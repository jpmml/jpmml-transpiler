/*
 * Copyright (c) 2019 Villu Ruusmann
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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Target;
import org.dmg.pmml.Targets;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.EvaluationContext;
import org.jpmml.evaluator.HasModel;
import org.jpmml.evaluator.HasPMML;
import org.jpmml.evaluator.IndexableUtil;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.TargetUtil;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.evaluator.ValueFactoryFactory;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.visitors.FieldReferenceFinder;
import org.jpmml.model.visitors.FieldResolver;

abstract
public class ModelTranslator<M extends Model> implements HasPMML, HasModel<M> {

	private PMML pmml = null;

	private M model = null;

	private TargetField targetField = null;


	public ModelTranslator(PMML pmml, M model){
		setPMML(Objects.requireNonNull(pmml));
		setModel(Objects.requireNonNull(model));
	}

	public JExpression translate(TranslationContext context){
		M model = getModel();

		JDefinedClass owner = context.getOwner();

		JDefinedClass javaModelClazz;

		try {
			javaModelClazz = owner._class(ModelTranslator.MEMBER_PUBLIC, IdentifierUtil.create(JavaModel.class.getSimpleName(), model));
		} catch(JClassAlreadyExistsException jcaee){
			throw new RuntimeException(jcaee);
		}

		javaModelClazz._extends(JavaModel.class);

		try {
			context.pushOwner(javaModelClazz);

			createEvaluateMethod(context);
		} finally {
			context.popOwner();
		}

		JInvocation invocation = JExpr._new(javaModelClazz);

		invocation = PMMLObjectUtil.initializeJavaModel(model, invocation, context);

		return invocation;
	}

	public void createEvaluateMethod(TranslationContext context){
		M model = getModel();

		MiningFunction miningFunction = model.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
				{
					JMethod regressorMethod = translateRegressor(context);

					createEvaluateRegressionMethod(regressorMethod, context);
				}
				break;
			case CLASSIFICATION:
				{
					JMethod classifierMethod = translateClassifier(context);

					createEvaluateClassificationMethod(classifierMethod, context);
				}
				break;
			default:
				throw new UnsupportedAttributeException(model, miningFunction);
		}
	}

	public JMethod translateRegressor(TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JMethod translateClassifier(TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JMethod createEvaluateRegressionMethod(JMethod evaluateMethod, TranslationContext context){
		M model = getModel();

		TargetField targetField = getTargetField();

		JMethod evaluateRegressionMethod = createEvaluatorMethod("evaluateRegression", context);

		try {
			context.pushScope(new MethodScope(evaluateRegressionMethod));

			JInvocation methodInvocation = createEvaluatorMethodInvocation(evaluateMethod, context);

			JType valueClazz = context.ref(Value.class);

			if(!(evaluateMethod.type()).equals(valueClazz)){
				methodInvocation = context.getValueFactoryVariable().newValue(methodInvocation);
			}

			ValueBuilder valueBuilder = new ValueBuilder(context)
				.declare("value", methodInvocation);

			Target target = targetField.getTarget();
			if(target != null){
				translateRegressorTarget(target, valueBuilder);

				// XXX
				model.setTargets(null);
			}

			JVar valueVar = valueBuilder.getVariable();

			context._return(context.ref(Collections.class).staticInvoke("singletonMap").arg(context.constantFieldName(targetField.getName())).arg(valueVar.invoke("getValue")));
		} finally {
			context.popScope();
		}

		return evaluateRegressionMethod;
	}

	public JMethod createEvaluateClassificationMethod(JMethod evaluateMethod, TranslationContext context){
		M model = getModel();

		TargetField targetField = getTargetField();

		JMethod evaluateClassificationMethod = createEvaluatorMethod("evaluateClassification", context);

		try {
			context.pushScope(new MethodScope(evaluateClassificationMethod));

			JVarBuilder classificationBuilder = new JVarBuilder(context)
				.declare(Classification.class, "classification", createEvaluatorMethodInvocation(evaluateMethod, context))
				.staticUpdate(TargetUtil.class, "computeResult", targetField.getDataType());

			JVar classificationVar = classificationBuilder.getVariable();

			context._return(context.ref(Collections.class).staticInvoke("singletonMap").arg(context.constantFieldName(targetField.getName())).arg(classificationVar));
		} finally {
			context.popScope();
		}

		return evaluateClassificationMethod;
	}

	public Map<FieldName, FieldInfo> getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		PMML pmml = getPMML();
		M model = getModel();

		Map<FieldName, Field<?>> bodyFields = new HashMap<>();

		Visitor fieldResolver = new FieldResolver(){

			@Override
			public VisitorAction visit(PMMLObject object){

				if(bodyObjects.contains(object)){
					Model parent = (Model)getParent();

					Collection<Field<?>> fields = getFields();

					for(Field<?> field : fields){
						FieldName name = field.getName();

						Field<?> previousField = bodyFields.put(name, field);
						if(previousField != null){
							throw new IllegalArgumentException(name.getValue());
						}
					}

					return VisitorAction.TERMINATE;
				}

				return super.visit(object);
			}
		};
		fieldResolver.applyTo(pmml);

		FieldReferenceFinder fieldReferenceFinder = new FieldReferenceFinder();
		for(PMMLObject bodyObject : bodyObjects){
			fieldReferenceFinder.applyTo(bodyObject);
		}

		Set<FieldName> names = fieldReferenceFinder.getFieldNames();

		Map<FieldName, FieldInfo> result = new LinkedHashMap<>();

		for(FieldName name : names){
			Field<?> field = bodyFields.get(name);

			FieldInfo fieldInfo = new FieldInfo(field);

			result.put(name, fieldInfo);
		}

		return result;
	}

	public String[] getTargetCategories(){
		TargetField targetField = getTargetField();

		List<String> categories = targetField.getCategories();

		return categories.toArray(new String[categories.size()]);
	}

	public TargetField getTargetField(){

		if(this.targetField == null){
			this.targetField = createTargetField();
		}

		return this.targetField;
	}

	private TargetField createTargetField(){
		PMML pmml = getPMML();
		M model = getModel();

		MiningField targetMiningField = null;

		MiningSchema miningSchema = model.getMiningSchema();
		if(miningSchema != null && miningSchema.hasMiningFields()){
			List<MiningField> miningFields = miningSchema.getMiningFields();

			List<MiningField> targetMiningFields = miningFields.stream()
				.filter(miningField -> {
					MiningField.UsageType usageType = miningField.getUsageType();

					switch(usageType){
						case PREDICTED:
						case TARGET:
							return true;
						default:
							return false;
					}
				})
				.collect(Collectors.toList());

			if(targetMiningFields.size() > 0){
				targetMiningField = Iterables.getOnlyElement(targetMiningFields);
			}
		}

		FieldName name = null;

		if(targetMiningField != null){
			name = targetMiningField.getName();
		}

		DataField targetDataField = null;

		if(name != null){
			DataDictionary dataDictionary = pmml.getDataDictionary();

			if(dataDictionary != null && dataDictionary.hasDataFields()){
				List<DataField> dataFields = dataDictionary.getDataFields();

				targetDataField = IndexableUtil.findIndexable(dataFields, name);
			}
		} else

		{
			MiningFunction miningFunction = model.getMiningFunction();

			switch(miningFunction){
				case REGRESSION:
					MathContext mathContext = model.getMathContext();

					switch(mathContext){
						case FLOAT:
							targetDataField = new DataField(name, OpType.CONTINUOUS, DataType.FLOAT);
							break;
						case DOUBLE:
							targetDataField = new DataField(name, OpType.CONTINUOUS, DataType.DOUBLE);
							break;
						default:
							throw new UnsupportedAttributeException(model, mathContext);
					}
					break;
				case CLASSIFICATION:
					targetDataField = new DataField(name, OpType.CATEGORICAL, DataType.STRING);
					break;
				default:
					throw new UnsupportedAttributeException(model, miningFunction);
			}
		}

		Target targetTarget = null;

		Targets targets = model.getTargets();
		if(targets != null && targets.hasTargets()){
			List<Target> _targets = targets.getTargets();

			targetTarget = IndexableUtil.findIndexable(_targets, name, true);
		}

		return new TargetField(targetDataField, targetMiningField, targetTarget);
	}

	@Override
	public PMML getPMML(){
		return this.pmml;
	}

	private void setPMML(PMML pmml){
		this.pmml = pmml;
	}

	@Override
	public M getModel(){
		return this.model;
	}

	private void setModel(M model){
		this.model = model;
	}

	static
	public <V extends Number> ValueFactory<V> getValueFactory(Model model){
		MathContext mathContext = model.getMathContext();

		switch(mathContext){
			case FLOAT:
			case DOUBLE:
				ValueFactoryFactory valueFactoryFactory = ValueFactoryFactory.newInstance();

				// XXX
				return (ValueFactory)valueFactoryFactory.newValueFactory(mathContext);
			default:
				throw new UnsupportedAttributeException(model, mathContext);
		}
	}

	static
	public FieldInfo getFieldInfo(HasFieldReference<?> hasFieldReference, Map<FieldName, FieldInfo> fieldInfos){
		return getFieldInfo(hasFieldReference.getField(), fieldInfos);
	}

	static
	public FieldInfo getFieldInfo(FieldName name, Map<FieldName, FieldInfo> fieldInfos){
		FieldInfo fieldInfo = fieldInfos.get(name);
		if(fieldInfo == null){
			throw new IllegalArgumentException(name.getValue());
		}

		return fieldInfo;
	}

	static
	private void translateRegressorTarget(Target target, ValueBuilder valueBuilder){
		Number rescaleFactor = target.getRescaleFactor();

		if(rescaleFactor != null && rescaleFactor.doubleValue() != 1d){
			valueBuilder.update("multiply", rescaleFactor);
		}

		Number rescaleConstant = target.getRescaleConstant();
		if(rescaleConstant != null && rescaleConstant.doubleValue() != 0d){
			valueBuilder.update("add", rescaleConstant);
		}

		Target.CastInteger castInteger = target.getCastInteger();
		if(castInteger != null){
			throw new UnsupportedAttributeException(target, castInteger);
		}
	}

	static
	public JMethod createEvaluatorMethod(String name, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JMethod method = owner.method(JMod.PUBLIC, Map.class, name);
		method.annotate(Override.class);

		method.param(ValueFactory.class, Scope.NAME_VALUEFACTORY);
		method.param(EvaluationContext.class, Scope.NAME_CONTEXT);

		return method;
	}

	static
	public JMethod createEvaluatorMethod(Class<?> type, PMMLObject object, boolean withValueFactory, TranslationContext context){
		return createEvaluatorMethod(type, IdentifierUtil.create("evaluate" + (object.getClass()).getSimpleName(), object), withValueFactory, context);
	}

	static
	public JMethod createEvaluatorMethod(Class<?> type, List<? extends PMMLObject> objects, boolean withValueFactory, TranslationContext context){
		Object object = Iterables.getFirst(objects, null);

		return createEvaluatorMethod(type, IdentifierUtil.create("evaluate" + (object.getClass()).getSimpleName() + "List", object), withValueFactory, context);
	}

	static
	private JMethod createEvaluatorMethod(Class<?> type, String name, boolean withValueFactory, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JType argumentsType = ensureArgumentsType(owner);

		JMethod method = owner.method(ModelTranslator.MEMBER_PRIVATE, type, name);

		if(withValueFactory){
			method.param(ValueFactory.class, Scope.NAME_VALUEFACTORY);
		}

		method.param(argumentsType, Scope.NAME_ARGUMENTS);

		return method;
	}

	static
	public JInvocation createEvaluatorMethodInvocation(JMethod method, TranslationContext context){
		JInvocation invocation = JExpr.invoke(method);

		List<JVar> params = method.params();
		for(JVar param : params){
			String name = param.name();

			JExpression arg;

			switch(name){
				case Scope.NAME_ARGUMENTS:
					try {
						arg = (context.getArgumentsVariable()).getVariable();
					} catch(IllegalArgumentException iae){
						JDefinedClass owner = context.getOwner();

						arg = JExpr._new(ensureArgumentsType(owner)).arg((context.getContextVariable()).getVariable());
					}
					break;
				case Scope.NAME_CONTEXT:
					arg = (context.getContextVariable()).getVariable();
					break;
				case Scope.NAME_VALUEFACTORY:
					arg = (context.getValueFactoryVariable()).getVariable();
					break;
				default:
					throw new IllegalArgumentException(name);
			}

			invocation = invocation.arg(arg);
		}

		return invocation;
	}

	static
	private JDefinedClass ensureArgumentsType(JDefinedClass owner){

		for(Iterator<JDefinedClass> it = owner.classes(); it.hasNext(); ){
			JDefinedClass clazz = it.next();

			if(("Arguments").equals(clazz.name())){
				return clazz;
			}
		}

		JDefinedClass argumentsClazz;

		try {
			argumentsClazz = owner._class(ModelTranslator.MEMBER_PUBLIC, "Arguments");
		} catch(JClassAlreadyExistsException jcaee){
			throw new RuntimeException(jcaee);
		}

		JFieldVar contextVar = argumentsClazz.field(JMod.PRIVATE, EvaluationContext.class, "context");

		JMethod constructor = argumentsClazz.constructor(JMod.PUBLIC);

		JVar contextParam = constructor.param(EvaluationContext.class, "context");

		JBlock block = constructor.body();

		block.assign(JExpr.refthis(contextVar.name()), contextParam);

		return argumentsClazz;
	}

	public static final int MEMBER_PUBLIC = (JMod.PUBLIC | JMod.FINAL | JMod.STATIC);
	public static final int MEMBER_PRIVATE = (JMod.PRIVATE | JMod.FINAL | JMod.STATIC);
}