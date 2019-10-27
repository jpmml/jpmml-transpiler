/*
 * Copyright (c) 2017 Villu Ruusmann
 *
 * This file is part of JPMML-Transpiler
 *
 * JPMML-Transpiler is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * JPMML-Transpiler is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with JPMML-Transpiler.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.jpmml.translator;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.UnsupportedAttributeException;

public class TranslationContext {

	private PMML pmml = null;

	private JCodeModel codeModel = null;

	private Deque<JDefinedClass> owners = new ArrayDeque<>();

	private Deque<Scope> scopes = new ArrayDeque<>();

	private Map<PMMLObject, JExpression> representations = new LinkedHashMap<>();

	private ArrayManager<FieldName> fieldNameManager = null;

	private ArrayManager<QName> xmlNameManager = null;


	public TranslationContext(PMML pmml, JCodeModel codeModel){
		setPMML(pmml);
		setCodeModel(codeModel);
	}

	public JClass ref(Class<?> clazz){
		JCodeModel codeModel = getCodeModel();

		return codeModel.ref(clazz);
	}

	public JDefinedClass getOwner(){
		return this.owners.peekFirst();
	}

	public void pushOwner(JDefinedClass owner){

		if(this.fieldNameManager == null){
			this.fieldNameManager = new ArrayManager<FieldName>(owner, ref(FieldName.class), "fieldNames"){

				{
					initArray();
				}

				@Override
				public JExpression createExpression(FieldName name){
					JClass fieldNameClass = ref(FieldName.class);

					return fieldNameClass.staticInvoke("create").arg(name.getValue());
				}
			};
		} // End if

		if(this.xmlNameManager == null){
			this.xmlNameManager = new ArrayManager<QName>(owner, ref(QName.class), "xmlNames"){

				{
					initArray();
				}

				@Override
				public JExpression createExpression(QName name){
					JClass xmlNameClass = ref(QName.class);

					return JExpr._new(xmlNameClass).arg(name.getNamespaceURI()).arg(name.getLocalPart()).arg(name.getPrefix());
				}
			};
		}

		this.owners.addFirst(owner);
	}

	public void popOwner(){
		this.owners.removeFirst();
	}

	public JVar getVariable(String name){

		for(Scope scope : this.scopes){
			JVar variable = scope.get(name);

			if(variable != null){
				return variable;
			}
		}

		throw new IllegalArgumentException(name);
	}

	public ArgumentsRef getArgumentsVariable(){
		JVar variable = getVariable(Scope.NAME_ARGUMENTS);

		return new ArgumentsRef(variable);
	}

	public EvaluationContextRef getContextVariable(){
		JVar variable = getVariable(Scope.NAME_CONTEXT);

		return new EvaluationContextRef(variable);
	}

	public ValueFactoryRef getValueFactoryVariable(){
		JVar variable = getVariable(Scope.NAME_VALUEFACTORY);

		return new ValueFactoryRef(variable);
	}

	public FieldValueRef ensureFieldValueVariable(FieldInfo fieldInfo){
		Field<?> field = fieldInfo.getField();

		FieldName name = field.getName();

		String stringName = IdentifierUtil.create("value", name);

		JVar variable;

		try {
			variable = getVariable(stringName);
		} catch(IllegalArgumentException iae){
			variable = declare(FieldValue.class, stringName, getContextVariable().evaluate(constantFieldName(name)));
		}

		return new FieldValueRef(variable);
	}

	public OperableRef ensureOperableVariable(FieldInfo fieldInfo){
		Field<?> field = fieldInfo.getField();
		Encoder encoder = fieldInfo.getEncoder();

		FieldName name = field.getName();
		DataType dataType = field.getDataType();

		String stringName = fieldInfo.getVariableName();

		JVar variable;

		try {
			variable = getVariable(stringName);
		} catch(IllegalArgumentException iae){
			ArgumentsRef argumentsRef = getArgumentsVariable();

			JMethod method = argumentsRef.getMethod(fieldInfo, this);

			variable = declare(method.type(), stringName, argumentsRef.invoke(method));
		}

		if(encoder != null){
			return encoder.ref(variable);
		}

		switch(dataType){
			case STRING:
				return new StringRef(variable);
			case INTEGER:
			case FLOAT:
			case DOUBLE:
			case BOOLEAN:
				{
					JType type = variable.type();

					if(type.isPrimitive()){
						return new PrimitiveRef(variable);
					}

					return new NumberRef(variable);
				}
			default:
				throw new UnsupportedAttributeException(field, dataType);
		}
	}

	public JVar declare(Class<?> type, String name, JExpression init){
		JCodeModel codeModel = getCodeModel();

		return declare(codeModel._ref(type), name, init);
	}

	public JVar declare(JType type, String name, JExpression init){
		Scope scope = ensureOpenScope();

		return scope.declare(type, name, init);
	}

	public void add(JStatement statement){
		Scope scope = ensureOpenScope();

		scope.add(statement);
	}

	public void _return(JExpression expression){
		Scope scope = ensureOpenScope();

		try {
			scope._return(expression);
		} finally {
			scope.close();
		}
	}

	public JBlock block(){
		Scope scope = ensureOpenScope();

		return scope.getBlock();
	}

	public Scope ensureOpenScope(){
		Scope scope = getScope();

		return scope.ensureOpen();
	}

	public Scope getScope(){
		return this.scopes.peekFirst();
	}

	public void pushScope(Scope scope){
		this.scopes.addFirst(scope);
	}

	public void popScope(){
		this.scopes.removeFirst();
	}

	public JExpression constantFieldName(FieldName name){

		if(name == null){
			return JExpr._null();
		}

		ArrayManager<FieldName> fieldNameManager = this.fieldNameManager;

		int index = fieldNameManager.getOrInsert(name);

		return fieldNameManager.getComponent(index);
	}

	public JExpression constantXmlName(QName name){
		ArrayManager<QName> xmlNameManager = this.xmlNameManager;

		int index = xmlNameManager.getOrInsert(name);

		return xmlNameManager.getComponent(index);
	}

	public PMML getPMML(){
		return this.pmml;
	}

	private void setPMML(PMML pmml){
		this.pmml = pmml;
	}

	public JCodeModel getCodeModel(){
		return this.codeModel;
	}

	private void setCodeModel(JCodeModel codeModel){
		this.codeModel = codeModel;
	}

	public JExpression getRepresentation(PMMLObject pmmlObject){
		return this.representations.get(pmmlObject);
	}

	public void putRepresentation(PMMLObject pmmlObject, JExpression expression){
		this.representations.put(pmmlObject, expression);
	}
}