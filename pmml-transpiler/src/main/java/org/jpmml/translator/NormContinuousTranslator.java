/*
 * Copyright (c) 2022 Villu Ruusmann
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

import java.util.List;
import java.util.stream.Collectors;

import com.google.common.collect.Range;
import com.google.common.collect.RangeMap;
import com.google.common.collect.TreeRangeMap;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.LinearNorm;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.NormContinuous;
import org.dmg.pmml.OpType;
import org.dmg.pmml.OutlierTreatmentMethod;
import org.dmg.pmml.PMML;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.FieldValueUtil;
import org.jpmml.model.InvalidElementException;
import org.jpmml.model.UnsupportedAttributeException;

public class NormContinuousTranslator extends ExpressionTranslator<NormContinuous> {

	public NormContinuousTranslator(NormContinuous normContinuous){
		super(normContinuous);

		OutlierTreatmentMethod outlierTreatment = normContinuous.getOutliers();
		switch(outlierTreatment){
			case AS_MISSING_VALUES:
			case AS_EXTREME_VALUES:
				break;
			default:
				throw new UnsupportedAttributeException(normContinuous, outlierTreatment);
		}
	}

	@Override
	public void translateExpression(TranslationContext context){
		JDefinedClass owner = context.getOwner();

		NormContinuous normContinuous = getExpression();

		OutlierTreatmentMethod outlierTreatment = normContinuous.getOutliers();

		List<LinearNorm> linearNorms = normContinuous.requireLinearNorms();
		if(linearNorms.size() < 2){
			throw new InvalidElementException(normContinuous);
		}

		JDefinedClass normContinuousFuncInterface = ensureNormContinuousFuncInterface(context);

		JMethod createNormContinuousFuncMethod = ensureCreateNormContinuousFuncMethod(normContinuousFuncInterface, context);

		JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(NormContinuous.class.getSimpleName(), normContinuous) + ".data", context);

		List<Number[]> linearNormValues = linearNorms.stream()
			.map(linearNorm -> new Number[]{linearNorm.requireOrig(), linearNorm.requireNorm()})
			.collect(Collectors.toList());

		JFieldVar linearNormsVar = resourceInitializer.initNumbersList("linearNorms", MathContext.DOUBLE, linearNormValues);

		JVar rangeMapVar = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.ref(RangeMap.class).narrow(context.ref(Double.class), normContinuousFuncInterface), "rangeMap", context.staticInvoke(TreeRangeMap.class, "create"));

		JBlock init = owner.init();

		JForLoop forLoop = init._for();

		JVar loopVar = forLoop.init(context._ref(int.class), "i", JExpr.lit(1));
		forLoop.test(loopVar.lt(JExpr.lit(linearNormValues.size())));
		forLoop.update(loopVar.incr());

		JBlock forBlock = forLoop.body();

		JPrimitiveType doubleType = (JPrimitiveType)context._ref(double.class);

		JVar prevLinearNormVar = forBlock.decl(context.ref(Number[].class), "prevLinearNorm", linearNormsVar.invoke("get").arg(loopVar.minus(JExpr.lit(1))));
		JVar prevOrigVar = forBlock.decl(doubleType, "prevOrig", JExpr.component(prevLinearNormVar, JExpr.lit(0)).invoke("doubleValue"));
		JVar prevNormVar = forBlock.decl(doubleType, "prevNorm", JExpr.component(prevLinearNormVar, JExpr.lit(1)).invoke("doubleValue"));

		JVar linearNormVar = forBlock.decl(context.ref(Number[].class), "linearNorm", linearNormsVar.invoke("get").arg(loopVar));
		JVar origVar = forBlock.decl(doubleType, "orig", JExpr.component(linearNormVar, JExpr.lit(0)).invoke("doubleValue"));
		JVar normVar = forBlock.decl(doubleType, "norm", JExpr.component(linearNormVar, JExpr.lit(1)).invoke("doubleValue"));

		forBlock.add(rangeMapVar.invoke("put").arg(context.staticInvoke(Range.class, "closed", prevOrigVar, origVar)).arg(JExpr.invoke(createNormContinuousFuncMethod).arg(prevOrigVar).arg(prevNormVar).arg(origVar.minus(prevOrigVar)).arg(normVar.minus(prevNormVar))));

		JVar valueVar = context.declare(FieldValue.class, "value", (context.getContextVariable()).evaluate(PMMLObjectUtil.createExpression(normContinuous.requireField(), context)));

		JMethod normContinuousMethod = owner.method(Modifiers.PUBLIC_FINAL, Number.class, "normContinuous");

		JVar valueParam = normContinuousMethod.param(FieldValue.class, "value");

		try {
			context.pushScope(new MethodScope(normContinuousMethod));

			FieldValueRef fieldValueRef = new FieldValueRef(valueParam, DataType.DOUBLE);

			Number mapMissingTo = normContinuous.getMapMissingTo();

			context._returnIf(valueParam.eq(JExpr._null()), PMMLObjectUtil.createExpression(mapMissingTo, context));

			JVar javaValueVar = context.declare(fieldValueRef.getJavaType(), "javaValue", fieldValueRef.asJavaValue());

			LinearNorm firstLinearNorm = linearNorms.get(0);
			LinearNorm lastLinearNorm = linearNorms.get(linearNorms.size() - 1);

			JExpression checkLowExpr = javaValueVar.lt(PMMLObjectUtil.createExpression(firstLinearNorm.requireOrig(), context));
			JExpression checkHighExpr = javaValueVar.gt(PMMLObjectUtil.createExpression(lastLinearNorm.requireOrig(), context));

			switch(outlierTreatment){
				case AS_MISSING_VALUES:
					context._returnIf(checkLowExpr.bor(checkHighExpr), JExpr._null());
					break;
				case AS_EXTREME_VALUES:
					context._returnIf(checkLowExpr, PMMLObjectUtil.createExpression(firstLinearNorm.requireNorm(), context));
					context._returnIf(checkHighExpr, PMMLObjectUtil.createExpression(lastLinearNorm.requireNorm(), context));
					break;
				default:
					throw new UnsupportedAttributeException(normContinuous, outlierTreatment);
			}

			JVar normContinuousFuncVar = context.declare(normContinuousFuncInterface, "normContinuousFunction", rangeMapVar.invoke("get").arg(javaValueVar));

			context._return(normContinuousFuncVar.invoke("apply").arg(javaValueVar));
		} finally {
			context.popScope();
		}

		JInvocation invocation = context.staticInvoke(FieldValueUtil.class, "create", PMMLObjectUtil.createExpression(OpType.CONTINUOUS, context), PMMLObjectUtil.createExpression(DataType.DOUBLE, context), JExpr.invoke(normContinuousMethod).arg(valueVar));

		context._return(invocation);
	}

	private JDefinedClass ensureNormContinuousFuncInterface(TranslationContext context){
		JDefinedClass owner = context.getOwner(PMML.class);

		JDefinedClass definedClazz = JCodeModelUtil.getNestedClass(owner, "NormContinuousFunction");
		if(definedClazz != null){
			return definedClazz;
		}

		try {
			definedClazz = owner._interface("NormContinuousFunction");
		} catch(JClassAlreadyExistsException jcaee){
			throw new IllegalArgumentException(jcaee);
		}

		definedClazz.annotate(FunctionalInterface.class);

		JMethod method = definedClazz.method(Modifiers.PUBLIC_ABSTRACT, double.class, "apply");

		method.param(double.class, "x");

		return definedClazz;
	}

	private JMethod ensureCreateNormContinuousFuncMethod(JDefinedClass normContinuousFuncInterface, TranslationContext context){
		JCodeModel codeModel = context.getCodeModel();

		JDefinedClass owner = context.getOwner(PMML.class);

		JType doubleType = context._ref(double.class);

		JMethod method = owner.getMethod("createNormContinuousFunction", new JType[]{doubleType, doubleType, doubleType, doubleType});
		if(method != null){
			return method;
		}

		method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, normContinuousFuncInterface, "createNormContinuousFunction");

		JVar origParam = method.param(doubleType, "orig");
		JVar normParam = method.param(doubleType, "norm");
		JVar origRangeParam = method.param(doubleType, "origRange");
		JVar normRangeParam = method.param(doubleType, "normRange");

		try {
			context.pushScope(new MethodScope(method));

			JDefinedClass normContinuousFuncClazz = codeModel.anonymousClass(normContinuousFuncInterface);

			JMethod applyMethod = normContinuousFuncClazz.method(Modifiers.PUBLIC, double.class, "apply");
			applyMethod.annotate(Override.class);

			JVar xParam = applyMethod.param(double.class, "x");

			JBlock block = applyMethod.body();

			// "norm + (x - orig) / origRange * normRange"
			block._return(normParam.plus((xParam.minus(origParam)).div(origRangeParam).mul(normRangeParam)));

			context._return(JExpr._new(normContinuousFuncClazz));
		} finally {
			context.popScope();
		}

		return method;
	}
}