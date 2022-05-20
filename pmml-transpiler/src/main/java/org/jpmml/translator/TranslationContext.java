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
package org.jpmml.translator;

import java.lang.reflect.Constructor;
import java.lang.reflect.TypeVariable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import javax.xml.namespace.QName;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCase;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMods;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueMap;
import org.jpmml.model.PMMLException;
import org.jpmml.model.UnsupportedAttributeException;
import org.jpmml.translator.tree.TreeModelTranslator;

public class TranslationContext {

	private PMML pmml = null;

	private JCodeModel codeModel = null;

	private List<PMMLException> issues = new ArrayList<>();

	private Deque<JDefinedClass> owners = new ArrayDeque<>();

	private Deque<Scope> scopes = new ArrayDeque<>();

	private ArrayManager<QName> xmlNameManager = null;

	private Map<Model, TranslatedModel> translations = new IdentityHashMap<>();

	private Set<String> activeFieldNames = new LinkedHashSet<>();


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
		return this.owners.getFirst();
	}

	public JDefinedClass getOwner(Class<?> clazz){
		Deque<JDefinedClass> owners = getOwners();

		JClass superClazz = ref(clazz);

		for(Iterator<JDefinedClass> it = owners.iterator(); it.hasNext(); ){
			JDefinedClass owner = it.next();

			if(superClazz.isAssignableFrom(owner)){
				return owner;
			}
		}

		throw new IllegalArgumentException();
	}

	public Deque<JDefinedClass> getOwners(){
		return this.owners;
	}

	public void pushOwner(JDefinedClass owner){

		if(isSubclass(PMML.class, owner)){
			this.xmlNameManager = new ArrayManager<QName>(ref(QName.class), "xmlNames"){

				{
					initArrayVar(owner);
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
		JDefinedClass owner = this.owners.peekFirst();

		if(isSubclass(PMML.class, owner)){
			PMML pmml = getPMML();

			JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(PMML.class.getSimpleName(), pmml) + ".data", 0, this);

			QName[] xmlNames = this.xmlNameManager.getElements()
				.toArray(new QName[this.xmlNameManager.size()]);

			resourceInitializer.initQNames(this.xmlNameManager.getArrayVar(), xmlNames);
		}

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
		JDefinedClass owner = getOwner();

		JVar variable;

		// XXX
		if(("Arguments").equals(owner.name())){

			try {
				Constructor<JVar> constuctor = JVar.class.getDeclaredConstructor(JMods.class, JType.class, String.class, JExpression.class);
				if(!constuctor.isAccessible()){
					constuctor.setAccessible(true);
				}

				variable = constuctor.newInstance(null, owner, "this", null);
			} catch(ReflectiveOperationException roe){
				throw new RuntimeException(roe);
			}
		} else

		{
			variable = getVariable(Scope.VAR_ARGUMENTS);
		}

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

	public boolean isNonMissing(OperableRef operableRef){

		for(Scope scope : this.scopes){

			if(scope.isNonMissing(operableRef)){
				return true;
			}
		}

		return false;
	}

	public void markNonMissing(OperableRef operableRef){
		Scope scope = ensureOpenScope();

		scope.markNonMissing(operableRef);
	}

	public OperableRef ensureOperable(FieldInfo fieldInfo, Function<JMethod, Boolean> declareAsVariableFunction){
		Field<?> field = fieldInfo.getField();
		Encoder encoder = fieldInfo.getEncoder();

		DataType dataType = field.requireDataType();

		String variableName;

		if(encoder != null){
			FieldInfo finalFieldInfo = encoder.follow(fieldInfo);

			variableName = finalFieldInfo.getVariableName();
		} else

		{
			variableName = fieldInfo.getVariableName();
		}

		JExpression expression;

		JType type;

		try {
			JVar variable = getVariable(variableName);

			expression = variable;

			type = variable.type();
		} catch(IllegalArgumentException iae){
			JExpression[] initArgExprs = new JExpression[0];

			if(encoder instanceof TermFrequencyEncoder){
				TermFrequencyEncoder termFrequencyEncoder = (TermFrequencyEncoder)encoder;

				TreeModelTranslator.ensureTextIndexFields(fieldInfo, termFrequencyEncoder, this);
			} // End if

			if(encoder instanceof ArrayEncoder){
				ArrayEncoder arrayEncoder = (ArrayEncoder)encoder;

				initArgExprs = new JExpression[]{JExpr.lit(arrayEncoder.getIndex())};
			}

			ArgumentsRef argumentsRef = getArgumentsVariable();

			JMethod method = argumentsRef.getMethod(fieldInfo, this);

			expression = argumentsRef.invoke(method, initArgExprs);

			boolean declareAsVariable = declareAsVariableFunction.apply(method);
			if(declareAsVariable){
				expression = declare(method.type(), variableName, expression);
			}

			type = method.type();
		}

		if(encoder != null){
			return encoder.ref(expression);
		}

		switch(dataType){
			case STRING:
				return new StringRef(expression);
			case INTEGER:
			case FLOAT:
			case DOUBLE:
			case BOOLEAN:
				{
					if(type.isPrimitive()){
						return new PrimitiveRef(expression);
					}

					return new NumberRef(expression);
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

		return ref(ValueMap.class).narrow(Arrays.asList(ref(Object.class), numberTypeVar));
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

	public void _comment(String string){
		Scope scope = ensureOpenScope();

		JBlock block = scope.getBlock();

		block.directStatement("// " + string);
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

			if(resultMap.size() == 1){
				Map.Entry<?, V> entry = Iterables.getOnlyElement(resultMap.entrySet());

				JExpression condExpr = staticInvoke(Objects.class, "equals", valueExpr, PMMLObjectUtil.createExpression(entry.getKey(), this));

				block._return(JOp.cond(condExpr, PMMLObjectUtil.createExpression(entry.getValue(), this), PMMLObjectUtil.createExpression(defaultResult, this)));
			} else

			if(resultMap.size() > 1){
				JSwitch switchStatement = block._switch(valueExpr);

				JCase identityCase = null;

				Map<V, JCase> valueCases = new HashMap<>();

				Collection<? extends Map.Entry<?, V>> entries = resultMap.entrySet();
				for(Map.Entry<?, V> entry : entries){
					JCase valueCase = switchStatement._case(PMMLObjectUtil.createExpression(entry.getKey(), this));

					if(Objects.equals(entry.getKey(), entry.getValue())){

						if(identityCase != null){
							JSwitchUtil.chainCases(switchStatement, identityCase, valueCase);
						} else

						{
							JBlock caseBlock = valueCase.body();

							caseBlock._return(valueExpr);
						}

						identityCase = valueCase;
					} else

					{
						JCase prevValueCase = valueCases.get(entry.getValue());

						if(prevValueCase != null){
							JSwitchUtil.chainCases(switchStatement, prevValueCase, valueCase);
						} else

						{
							JBlock caseBlock = valueCase.body();

							caseBlock._return(PMMLObjectUtil.createExpression(entry.getValue(), this));
						}

						valueCases.put(entry.getValue(), valueCase);
					}
				}

				{
					JCase defaultCase = switchStatement._default();

					JBlock defaultBlock = defaultCase.body();

					defaultBlock._return(PMMLObjectUtil.createExpression(defaultResult, this));
				}
			} else

			{
				throw new IllegalArgumentException();
			}
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
		return this.scopes.getFirst();
	}

	public void pushScope(Scope scope){
		this.scopes.addFirst(scope);
	}

	public void popScope(){
		this.scopes.removeFirst();
	}

	public JExpression constantFieldName(String name){
		return constantFieldName(name, false);
	}

	public JExpression constantFieldName(String name, boolean markActive){

		if(name == null){
			return JExpr._null();
		} // End if

		if(markActive){
			this.activeFieldNames.add(name);
		}

		return JExpr.lit(name);
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

	public Map<Model, TranslatedModel> getTranslations(){
		return this.translations;
	}

	public void addTranslation(Model model, TranslatedModel translatedModel){
		this.translations.put(model, translatedModel);
	}

	public Set<String> getActiveFieldNames(){
		return this.activeFieldNames;
	}

	static
	private boolean isSubclass(Class<?> clazz, JDefinedClass owner){
		JClass superClazz = owner._extends();

		return (clazz.getName()).equals(superClazz.fullName());
	}
}