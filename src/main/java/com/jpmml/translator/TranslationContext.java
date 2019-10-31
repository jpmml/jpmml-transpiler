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

import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.PMMLException;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueMap;

public class TranslationContext {

	private PMML pmml = null;

	private JCodeModel codeModel = null;

	private List<PMMLException> issues = new ArrayList<>();

	private Deque<JDefinedClass> owners = new ArrayDeque<>();

	private Deque<Scope> scopes = new ArrayDeque<>();

	private Map<PMMLObject, JExpression> representations = new LinkedHashMap<>();

	private ArrayManager<FieldName> fieldNameManager = null;

	private ArrayManager<QName> xmlNameManager = null;


	public TranslationContext(PMML pmml, JCodeModel codeModel){
		setPMML(pmml);
		setCodeModel(codeModel);
	}

	public JClass ref(Class<?> type){
		JCodeModel codeModel = getCodeModel();

		return codeModel.ref(type);
	}

	public JType _ref(Class<?> type){
		JCodeModel codeModel = getCodeModel();

		return codeModel._ref(type);
	}

	public JDefinedClass getOwner(){
		return this.owners.peekFirst();
	}

	public void pushOwner(JDefinedClass owner){

		if(this.fieldNameManager == null){
			this.fieldNameManager = new ArrayManager<FieldName>(ref(FieldName.class), "fieldNames"){

				{
					initArrayVar(owner);
					initArray();
				}

				@Override
				public JExpression createExpression(FieldName name){
					return staticInvoke(FieldName.class, "create", name.getValue());
				}
			};
		} // End if

		if(this.xmlNameManager == null){
			this.xmlNameManager = new ArrayManager<QName>(ref(QName.class), "xmlNames"){

				{
					initArrayVar(owner);
					initArray();
				}

				@Override
				public JExpression createExpression(QName name){
					return _new(QName.class, name.getNamespaceURI(), name.getLocalPart(), name.getPrefix());
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
			JVar variable = scope.getVariable(name);

			if(variable != null){
				return variable;
			}
		}

		throw new IllegalArgumentException(name);
	}

	public ArgumentsRef getArgumentsVariable(){
		JVar variable = getVariable(Scope.VAR_ARGUMENTS);

		return new ArgumentsRef(variable);
	}

	public EvaluationContextRef getContextVariable(){
		JVar variable = getVariable(Scope.VAR_CONTEXT);

		return new EvaluationContextRef(variable);
	}

	public ValueFactoryRef getValueFactoryVariable(){
		JVar variable = getVariable(Scope.VAR_VALUEFACTORY);

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

	public boolean isNonMissing(JVar variable){

		for(Scope scope : this.scopes){

			if(scope.isNonMissing(variable)){
				return true;
			}
		}

		return false;
	}

	public void markNonMissing(JVar variable){
		Scope scope = ensureOpenScope();

		scope.markNonMissing(variable);
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

	public JTypeVar getTypeVariable(String name){

		for(Scope scope : this.scopes){

			if(scope instanceof MethodScope){
				MethodScope methodScope = (MethodScope)scope;

				return methodScope.getTypeVariable(name);
			}
		}

		throw new IllegalArgumentException(name);
	}

	public JTypeVar getNumberTypeVariable(){
		return getTypeVariable(MethodScope.TYPEVAR_NUMBER);
	}

	public JClass getValueType(){
		JTypeVar numberTypeVar = getNumberTypeVariable();

		return ref(Value.class).narrow(numberTypeVar);
	}

	public JClass getValueMapType(){
		JTypeVar numberTypeVar = getNumberTypeVariable();

		return ref(ValueMap.class).narrow(Arrays.asList(ref(String.class), numberTypeVar));
	}

	public JVar declare(Class<?> type, String name, JExpression init){
		return declare(_ref(type), name, init);
	}

	public JVar declare(JType type, String name, JExpression init){
		Scope scope = ensureOpenScope();

		return scope.declare(type, name, init);
	}

	public void add(JStatement statement){
		Scope scope = ensureOpenScope();

		JBlock block = scope.getBlock();

		block.add(statement);
	}

	public void _returnIf(JExpression testExpr, JExpression resultExpr){
		Scope scope = ensureOpenScope();

		JBlock block = scope.getBlock();

		JBlock thenBlock = block._if(testExpr)._then();

		thenBlock._return(resultExpr);
	}

	public void _return(JExpression testExpr, JExpression trueResultExpr, JExpression falseResultExpr){
		Scope scope = ensureOpenScope();

		try {
			JBlock block = scope.getBlock();

			block._return(JOp.cond(testExpr, trueResultExpr, falseResultExpr));
		} finally {
			scope.close();
		}
	}

	public void _return(JExpression resultExpr){
		Scope scope = ensureOpenScope();

		try {
			JBlock block = scope.getBlock();

			block._return(resultExpr);
		} finally {
			scope.close();
		}
	}

	public <V> void _return(JExpression valueExpr, Map<?, V> resultMap, V defaultResult){
		Scope scope = ensureOpenScope();

		try {
			JBlock block = scope.getBlock();

			JSwitch switchBlock = block._switch(valueExpr);

			Collection<? extends Map.Entry<?, V>> entries = resultMap.entrySet();
			for(Map.Entry<?, V> entry : entries){
				JBlock caseBlock = switchBlock._case(PMMLObjectUtil.createExpression(entry.getKey(), this)).body();

				caseBlock._return(PMMLObjectUtil.createExpression(entry.getValue(), this));
			}

			JBlock defaultBlock = switchBlock._default().body();

			defaultBlock._return(PMMLObjectUtil.createExpression(defaultResult, this));
		} finally {
			scope.close();
		}
	}

	public JInvocation _new(Class<?> type, Object... args){
		TypeVariable<?>[] typeVariables = type.getTypeParameters();
		if(typeVariables.length > 0){
			return _new(ref(type).narrow(Collections.emptyList()), args);
		}

		return _new(ref(type), args);
	}

	public JInvocation _new(JClass type, Object... args){
		List<JClass> typeParameters = type.getTypeParameters();
		if(!typeParameters.isEmpty()){
			type = (type.erasure()).narrow(Collections.emptyList());
		}

		JInvocation invocation = JExpr._new(type);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, this));
		}

		return invocation;
	}

	public JInvocation invoke(JVar variable, String method, Object... args){
		return invoke((JExpression)variable, method, args);
	}

	public JInvocation invoke(JExpression variable, String method, Object... args){
		JInvocation invocation = variable.invoke(method);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, this));
		}

		return invocation;
	}

	public JInvocation staticInvoke(Class<?> type, String method, Object... args){
		return staticInvoke(ref(type), method, args);
	}

	public JInvocation staticInvoke(JClass type, String method, Object... args){
		JInvocation invocation = type.staticInvoke(method);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, this));
		}

		return invocation;
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

	public void addIssue(PMMLException issue){
		this.issues.add(issue);
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

	public List<PMMLException> getIssues(){
		return this.issues;
	}

	public JExpression getRepresentation(PMMLObject pmmlObject){
		return this.representations.get(pmmlObject);
	}

	public void putRepresentation(PMMLObject pmmlObject, JExpression reprExpr){
		this.representations.put(pmmlObject, reprExpr);
	}
}