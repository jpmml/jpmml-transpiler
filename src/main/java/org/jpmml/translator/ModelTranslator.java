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
package org.jpmml.translator;

import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DefineFunction;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.Expression;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Target;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.jpmml.evaluator.EvaluationContext;
import org.jpmml.evaluator.ModelManager;
import org.jpmml.evaluator.TargetField;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.evaluator.ValueFactoryFactory;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.visitors.ActiveFieldFinder;
import org.jpmml.model.visitors.FieldResolver;

abstract
public class ModelTranslator<M extends Model> extends ModelManager<M> {

	public ModelTranslator(PMML pmml, M model){
		super(pmml, model);

		MathContext mathContext = model.getMathContext();
		switch(mathContext){
			case FLOAT:
			case DOUBLE:
				break;
			default:
				throw new UnsupportedAttributeException(model, mathContext);
		}
	}

	public JExpression translate(TranslationContext context){
		M model = getModel();

		JDefinedClass javaModelClazz = PMMLObjectUtil.createMemberClass(ModelTranslator.MEMBER_PUBLIC, IdentifierUtil.create(JavaModel.class.getSimpleName(), model), context);

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

			if(!((evaluateMethod.type()).erasure()).equals(valueClazz)){
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

			context._return(context.staticInvoke(Collections.class, "singletonMap", targetField.getName(), valueVar.invoke("getValue")));
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

			ClassificationBuilder classificationBuilder = new ClassificationBuilder(context)
				.declare("classification", createEvaluatorMethodInvocation(evaluateMethod, context))
				.computeResult(targetField.getDataType());

			context._return(context.staticInvoke(Collections.class, "singletonMap", context.constantFieldName(targetField.getName()), classificationBuilder));
		} finally {
			context.popScope();
		}

		return evaluateClassificationMethod;
	}

	public Map<FieldName, FieldInfo> getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		PMML pmml = getPMML();
		M model = getModel();

		MiningSchema miningSchema = model.getMiningSchema();
		Output output = model.getOutput();

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
						if((previousField != null) && (previousField != field)){
							throw new IllegalArgumentException(name.getValue());
						}
					}

					// XXX
					return VisitorAction.SKIP;
				}

				return super.visit(object);
			}
		};
		fieldResolver.applyTo(pmml);

		Set<FieldName> names = ActiveFieldFinder.getFieldNames(bodyObjects.toArray(new PMMLObject[bodyObjects.size()]));

		Map<FieldName, FieldInfo> result = new LinkedHashMap<>();
		for(FieldName name : names){
			Field<?> field = bodyFields.get(name);

			FieldInfo fieldInfo = new FieldInfo(field);

			result.put(name, fieldInfo);
		}

		FunctionInvocationContext context = new FunctionInvocationContext(){

			@Override
			public DefineFunction getDefineFunction(String name){
				return ModelTranslator.this.getDefineFunction(name);
			}
		};

		Collection<FieldInfo> fieldInfos = new ArrayList<>(result.values());
		for(FieldInfo fieldInfo : fieldInfos){
			enhanceFieldInfo(fieldInfo, miningSchema, bodyFields, result, context);
		}

		return result;
	}

	public Object[] getTargetCategories(){
		TargetField targetField = getTargetField();

		List<?> categories = targetField.getCategories();

		return categories.toArray(new Object[categories.size()]);
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

		JMethod method = owner.method(JMod.PUBLIC, context.ref(Map.class).narrow(Arrays.asList(context.ref(FieldName.class), context.ref(Object.class).wildcard())), name);
		method.annotate(Override.class);

		JTypeVar numberTypeVar = method.generify(MethodScope.TYPEVAR_NUMBER, Number.class);

		method.param(context.ref(ValueFactory.class).narrow(numberTypeVar), Scope.VAR_VALUEFACTORY);
		method.param(EvaluationContext.class, Scope.VAR_CONTEXT);

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

		JMethod method = owner.method(ModelTranslator.MEMBER_PRIVATE, type, name);

		if(withValueFactory){
			JTypeVar numberTypeVar = method.generify(MethodScope.TYPEVAR_NUMBER, Number.class);

			TypeVariable<?>[] typeVariables = type.getTypeParameters();
			if(typeVariables.length == 1){
				method.type(context.ref(type).narrow(numberTypeVar));
			} else

			if(typeVariables.length == 2){
				method.type(context.ref(type).narrow(context.ref(Object.class), numberTypeVar));
			}

			method.param(context.ref(ValueFactory.class).narrow(numberTypeVar), Scope.VAR_VALUEFACTORY);
		}

		method.param(ensureArgumentsType(context), Scope.VAR_ARGUMENTS);

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
				case Scope.VAR_ARGUMENTS:
					try {
						arg = (context.getArgumentsVariable()).getVariable();
					} catch(IllegalArgumentException iae){
						arg = JExpr._new(ensureArgumentsType(context)).arg((context.getContextVariable()).getVariable());
					}
					break;
				case Scope.VAR_CONTEXT:
					arg = (context.getContextVariable()).getVariable();
					break;
				case Scope.VAR_VALUEFACTORY:
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
	public JDefinedClass ensureArgumentsType(TranslationContext context){
		JDefinedClass owner = context.getOwner();

		for(Iterator<JDefinedClass> it = owner.classes(); it.hasNext(); ){
			JDefinedClass clazz = it.next();

			if(("Arguments").equals(clazz.name())){
				return clazz;
			}
		}

		JDefinedClass argumentsClazz = PMMLObjectUtil.createMemberClass(ModelTranslator.MEMBER_PUBLIC, "Arguments", context);

		JFieldVar contextVar = argumentsClazz.field(JMod.PRIVATE, EvaluationContext.class, "context");

		JMethod constructor = argumentsClazz.constructor(JMod.PUBLIC);

		JVar contextParam = constructor.param(EvaluationContext.class, "context");

		JBlock block = constructor.body();

		block.assign(JExpr.refthis(contextVar.name()), contextParam);

		return argumentsClazz;
	}

	static
	private void enhanceFieldInfo(FieldInfo fieldInfo, MiningSchema miningSchema, Map<FieldName, Field<?>> bodyFields, Map<FieldName, FieldInfo> fieldInfos, FunctionInvocationContext context){
		Field<?> field = fieldInfo.getField();

		if(field instanceof DerivedField){
			DerivedField derivedField = (DerivedField)field;

			Expression expression = derivedField.getExpression();

			FunctionInvocation functionInvocation = FunctionInvocationUtil.match(expression, context);

			if(functionInvocation instanceof FunctionInvocation.Ref){
				FunctionInvocation.Ref ref = (FunctionInvocation.Ref)functionInvocation;

				FieldName name = ref.getField();

				FieldInfo refFieldInfo = fieldInfos.get(name);
				if(refFieldInfo == null){
					Field<?> refField = bodyFields.get(name);

					refFieldInfo = new FieldInfo(refField);

					fieldInfos.put(name, refFieldInfo);

					// XXX
					if(refField instanceof DataField){
						ensureActive(refField, miningSchema);
					}

					enhanceFieldInfo(refFieldInfo, miningSchema, bodyFields, fieldInfos, context);
				}

				fieldInfo.setRef(refFieldInfo);

				functionInvocation = null;
			}

			fieldInfo.setFunctionInvocation(functionInvocation);
		}
	}

	static
	private void ensureActive(Field<?> field, MiningSchema miningSchema){
		FieldName name = field.getName();

		if(miningSchema.hasMiningFields()){
			List<MiningField> miningFields = miningSchema.getMiningFields();

			for(MiningField miningField : miningFields){

				if((miningField.getName()).equals(name)){
					return;
				}
			}
		}

		MiningField miningField = new MiningField(name);

		miningSchema.addMiningFields(miningField);
	}

	public static final int MEMBER_PUBLIC = (JMod.PUBLIC | JMod.FINAL | JMod.STATIC);
	public static final int MEMBER_PRIVATE = (JMod.PRIVATE | JMod.FINAL | JMod.STATIC);
}