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
package com.jpmml.translator.regression;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.jpmml.translator.FieldInfo;
import com.jpmml.translator.FieldValueRef;
import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.ObjectRef;
import com.jpmml.translator.PMMLObjectUtil;
import com.jpmml.translator.Scope;
import com.jpmml.translator.TranslationContext;
import com.jpmml.translator.ValueBuilder;
import com.jpmml.translator.ValueMapBuilder;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JVar;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.regression.CategoricalPredictor;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.PredictorTerm;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.InvalidElementException;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.TypeUtil;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.regression.RegressionModelUtil;

public class RegressionModelTranslator extends ModelTranslator<RegressionModel> {

	public RegressionModelTranslator(PMML pmml, RegressionModel regressionModel){
		super(pmml, regressionModel);

		MiningFunction miningFunction = regressionModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(regressionModel, miningFunction);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		RegressionModel regressionModel = getModel();

		List<RegressionTable> regressionTables = regressionModel.getRegressionTables();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

		RegressionTable regressionTable = Iterables.getOnlyElement(regressionTables);

		JMethod evaluateMethod = context.evaluatorMethod(JMod.PUBLIC, Value.class, regressionTable, true, true);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			ValueBuilder valueBuilder = translateRegressionTable(regressionTable, fieldInfos, context);

			computeValue(valueBuilder, regressionModel, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		RegressionModel regressionModel = getModel();

		List<RegressionTable> regressionTables = regressionModel.getRegressionTables();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

		JMethod evaluateListMethod = context.evaluatorMethod(JMod.PUBLIC, Classification.class, "evaluateRegressionTableList", true, true);

		try {
			context.pushScope(new MethodScope(evaluateListMethod));

			ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
				.construct("values");

			for(RegressionTable regressionTable : regressionTables){
				JMethod evaluateMethod = context.evaluatorMethod(JMod.PUBLIC, Value.class, regressionTable, true, true);

				try {
					context.pushScope(new MethodScope(evaluateMethod));

					ValueBuilder valueBuilder = translateRegressionTable(regressionTable, fieldInfos, context);

					context._return(valueBuilder.getVariable());
				} finally {
					context.popScope();
				}

				String targetCategory = TypeUtil.format(regressionTable.getTargetCategory());

				valueMapBuilder.update("put", targetCategory, createEvaluatorMethodInvocation(evaluateMethod, context));
			}

			computeClassification(valueMapBuilder, regressionModel, context);
		} finally {
			context.popScope();
		}

		return evaluateListMethod;
	}

	static
	public void computeValue(ValueBuilder valueBuilder, RegressionModel regressionModel, TranslationContext context){
		RegressionModel.NormalizationMethod normalizationMethod = regressionModel.getNormalizationMethod();

		switch(normalizationMethod){
			case NONE:
				break;
			default:
				valueBuilder.staticUpdate(RegressionModelUtil.class, "normalizeRegressionResult", normalizationMethod);
				break;
		}

		context._return(valueBuilder.getVariable());
	}

	static
	public void computeClassification(ValueMapBuilder valueMapBuilder, RegressionModel regressionModel, TranslationContext context){
		RegressionModel.NormalizationMethod normalizationMethod = regressionModel.getNormalizationMethod();
		List<RegressionTable> regressionTables = regressionModel.getRegressionTables();
		Output output = regressionModel.getOutput();

		if(regressionTables.size() == 2){
			valueMapBuilder.staticUpdate(RegressionModelUtil.class, "computeBinomialProbabilities", normalizationMethod);
		} else

		if(regressionTables.size() >= 2){
			valueMapBuilder.staticUpdate(RegressionModelUtil.class, "computeMultinomialProbabilities", normalizationMethod);
		} else

		{
			throw new InvalidElementException(regressionModel);
		}

		boolean probabilistic = false;

		if(output != null && output.hasOutputFields()){
			List<OutputField> outputFields = output.getOutputFields();

			List<OutputField> probabilityOutputFields = outputFields.stream()
				.filter(outputField -> {
					ResultFeature resultFeature = outputField.getResultFeature();

					switch(resultFeature){
						case PROBABILITY:
							return true;
						default:
							return false;
					}

				})
				.collect(Collectors.toList());

			probabilistic = (regressionTables.size() == probabilityOutputFields.size());
		}

		JVar valueMapVar = valueMapBuilder.getVariable();

		JExpression classificationExpr;

		if(probabilistic){
			classificationExpr = JExpr._new(context.ref(ProbabilityDistribution.class)).arg(valueMapVar);
		} else

		{
			classificationExpr = JExpr._new(context.ref(Classification.class)).arg(PMMLObjectUtil.createExpression(Classification.Type.VOTE, context)).arg(valueMapVar);
		}

		context._return(classificationExpr);
	}

	static
	public ValueBuilder translateRegressionTable(RegressionTable regressionTable, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		ValueBuilder valueBuilder = new ValueBuilder(context)
			.declare("result$" + System.identityHashCode(regressionTable), context.getValueFactoryVariable().newValue());

		if(regressionTable.hasNumericPredictors()){
			List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

			for(NumericPredictor numericPredictor : numericPredictors){
				FieldInfo fieldInfo = getFieldInfo(numericPredictor, fieldInfos);

				FieldValueRef fieldValueRef = context.ensureFieldValueVariable(fieldInfo);

				JInvocation invocation;

				Number coefficient = numericPredictor.getCoefficient();
				Integer exponent = numericPredictor.getExponent();

				if(exponent != null && exponent.intValue() != 1){
					valueBuilder.update("add", coefficient, fieldValueRef.asNumber(), exponent);
				} else

				{
					if(coefficient.doubleValue() != 1d){
						valueBuilder.update("add", coefficient, fieldValueRef.asNumber());
					} else

					{
						valueBuilder.update("add", fieldValueRef.asNumber());
					}
				}
			}
		} // End if

		if(regressionTable.hasCategoricalPredictors()){
			Map<FieldName, List<CategoricalPredictor>> fieldCategoricalPredictors = regressionTable.getCategoricalPredictors().stream()
				.collect(Collectors.groupingBy(categoricalPredictor -> categoricalPredictor.getField(), Collectors.toList()));

			Collection<Map.Entry<FieldName, List<CategoricalPredictor>>> entries = fieldCategoricalPredictors.entrySet();
			for(Map.Entry<FieldName, List<CategoricalPredictor>> entry : entries){
				FieldInfo fieldInfo = getFieldInfo(entry.getKey(), fieldInfos);

				FieldValueRef fieldValueRef = context.ensureFieldValueVariable(fieldInfo);

				JMethod evaluateCategoryMethod = context.evaluatorMethod(JMod.PRIVATE, Number.class, "evaluateField$" + System.identityHashCode(entry.getKey()), false, false);
				evaluateCategoryMethod.param(fieldValueRef.type(), fieldValueRef.name());

				try {
					context.pushScope(new MethodScope(evaluateCategoryMethod));

					translateField(fieldInfo, entry.getValue(), context);
				} finally {
					context.popScope();
				}

				JVar categoryValueVar = context.declare(Number.class, "categoryValue$" + System.identityHashCode(entry.getKey()), JExpr.invoke(evaluateCategoryMethod).arg(fieldValueRef.getVariable()));

				JBlock block = context.block();

				JBlock thenBlock = block._if(categoryValueVar.ne(JExpr._null()))._then();

				try {
					context.pushScope(new Scope(thenBlock));

					valueBuilder.update("add", categoryValueVar);
				} finally {
					context.popScope();
				}
			}
		} // End if

		if(regressionTable.hasPredictorTerms()){
			List<PredictorTerm> predictorTerms = regressionTable.getPredictorTerms();

			throw new UnsupportedElementException(Iterables.getFirst(predictorTerms, null));
		}

		Number intercept = regressionTable.getIntercept();
		if(intercept != null && intercept.doubleValue() != 0d){
			valueBuilder.update("add", intercept);
		}

		return valueBuilder;
	}

	static
	private void translateField(FieldInfo fieldInfo, List<CategoricalPredictor> categoricalPredictors, TranslationContext context){
		ObjectRef objectRef = context.ensureObjectVariable(fieldInfo, null);

		JBlock block = context.block();

		JSwitch switchBlock = block._switch(objectRef.getVariable());

		for(CategoricalPredictor categoricalPredictor : categoricalPredictors){
			switchBlock._case(PMMLObjectUtil.createExpression(categoricalPredictor.getValue(), context)).body()._return(PMMLObjectUtil.createExpression(categoricalPredictor.getCoefficient(), context));
		}

		switchBlock._default().body()._return(JExpr._null());
	}
}