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
import java.util.function.Consumer;

import javax.xml.namespace.QName;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
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
import org.jpmml.evaluator.EvaluationContext;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.ValueFactory;

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

		this.fieldNameManager = new ArrayManager<FieldName>(ref(FieldName.class), "fieldNames"){

			@Override
			public JDefinedClass getOwner(){
				return TranslationContext.this.owners.peekLast();
			}

			@Override
			public JExpression createExpression(FieldName name){
				JClass fieldNameClass = ref(FieldName.class);

				return fieldNameClass.staticInvoke("create")
					.arg(name.getValue());
			}
		};

		this.xmlNameManager = new ArrayManager<QName>(ref(QName.class), "xmlNames"){

			@Override
			public JDefinedClass getOwner(){
				return TranslationContext.this.owners.peekLast();
			}

			@Override
			public JExpression createExpression(QName name){
				JClass xmlNameClass = ref(QName.class);

				return JExpr._new(xmlNameClass)
					.arg(name.getNamespaceURI()).arg(name.getLocalPart()).arg(name.getPrefix());
			}
		};
	}

	public JClass ref(Class<?> clazz){
		JCodeModel codeModel = getCodeModel();

		return codeModel.ref(clazz);
	}

	public JDefinedClass _class(PMMLObject object) throws JClassAlreadyExistsException {
		JCodeModel codeModel = getCodeModel();

		Class<? extends PMMLObject> clazz = object.getClass();

		JDefinedClass definedClazz = codeModel._class(clazz.getSimpleName() + "$" + System.identityHashCode(object));
		definedClazz._extends(clazz);

		return definedClazz;
	}

	public JDefinedClass anonymousClass(Class<?> clazz){
		JCodeModel codeModel = getCodeModel();

		return codeModel.anonymousClass(clazz);
	}

	public JMethod evaluatorMethod(int mods, Class<?> type, PMMLObject object, boolean withValueFactory, boolean withContext){
		Class<?> clazz = object.getClass();

		return evaluatorMethod(mods, type, "evaluate" + clazz.getSimpleName() + "$" + System.identityHashCode(object), withValueFactory, withContext);
	}

	public JMethod evaluatorMethod(int mods, Class<?> type, String name, boolean withValueFactory, boolean withContext){
		JCodeModel codeModel = getCodeModel();

		return evaluatorMethod(mods, codeModel._ref(type), name, withValueFactory, withContext);
	}

	public JMethod evaluatorMethod(int mods, JType type, String name, boolean withValueFactory, boolean withContext){
		JDefinedClass owner = getOwner();

		JMethod method = owner.method(mods, type, name);

		if(withValueFactory){
			method.param(ValueFactory.class, Scope.NAME_VALUEFACTORY);
		} // End if

		if(withContext){
			method.param(EvaluationContext.class, Scope.NAME_CONTEXT);
		}

		return method;
	}

	public JDefinedClass getOwner(){
		return this.owners.peekFirst();
	}

	public void pushOwner(JDefinedClass owner){
		this.owners.addFirst(owner);
	}

	public void popOwner(){
		this.owners.removeFirst();
	}

	public JVar getContextVariable(){
		return getVariable(Scope.NAME_CONTEXT);
	}

	public JVar getValueFactoryVariable(){
		return getVariable(Scope.NAME_VALUEFACTORY);
	}

	public JVar getFieldValueVariable(FieldName name){
		return getVariable(variableName("value", name));
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

	public JVar ensureValueVariable(Field<?> field, Consumer<JBlock> missingValueHandler){
		JCodeModel codeModel = getCodeModel();

		FieldName name = field.getName();
		DataType dataType = field.getDataType();

		String stringName = variableName((dataType.name()).toLowerCase(), name);

		try {
			return getVariable(stringName);
		} catch(IllegalArgumentException iae){
			JVar fieldValue = ensureFieldValueVariable(field);

			if(missingValueHandler != null){
				JBlock block = block();

				JBlock missingValueHandlerBlock = block._if(fieldValue.eq(JExpr._null()))._then();

				missingValueHandler.accept(missingValueHandlerBlock);
			}

			switch(dataType){
				case STRING:
					return declare(String.class, stringName, fieldValue.invoke("asString"));
				case INTEGER:
					return declare(codeModel.INT, stringName, fieldValue.invoke("asInteger"));
				case FLOAT:
					return declare(codeModel.FLOAT, stringName, fieldValue.invoke("asFloat"));
				case DOUBLE:
					return declare(codeModel.DOUBLE, stringName, fieldValue.invoke("asDouble"));
				case BOOLEAN:
					return declare(codeModel.BOOLEAN, stringName, fieldValue.invoke("asBoolean"));
				default:
					throw new UnsupportedAttributeException(field, dataType);
			}
		}
	}

	public JVar ensureFieldValueVariable(Field<?> field){
		FieldName name = field.getName();

		String stringName = variableName("value", name);

		try {
			return getVariable(stringName);
		} catch(IllegalArgumentException iae){
			return declare(FieldValue.class, stringName, getContextVariable().invoke("evaluate").arg(constantFieldName(name)));
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

		return this.fieldNameManager.getExpression(name);
	}

	public JExpression constantXmlName(QName name){
		return this.xmlNameManager.getExpression(name);
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

	static
	private String variableName(String prefix, FieldName name){
		return prefix + "$" + System.identityHashCode(name);
	}
}