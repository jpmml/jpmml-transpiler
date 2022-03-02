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
package org.jpmml.translator.mining;

import java.util.List;
import java.util.Objects;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.evaluator.Classification;
import org.jpmml.model.InvalidElementException;
import org.jpmml.model.MissingElementException;
import org.jpmml.model.UnsupportedAttributeException;
import org.jpmml.model.UnsupportedElementException;
import org.jpmml.model.XPathUtil;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueFactoryRef;
import org.jpmml.translator.ValueMapBuilder;
import org.jpmml.translator.regression.RegressionModelTranslator;

public class ModelChainTranslator extends MiningModelTranslator {

	public ModelChainTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.requireMiningFunction();
		switch(miningFunction){
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();

		Segmentation segmentation = miningModel.requireSegmentation();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.requireMultipleModelMethod();
		switch(multipleModelMethod){
			case MODEL_CHAIN:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		List<Segment> segments = segmentation.requireSegments();

		List<Segment> regressorSegments = segments.subList(0, segments.size() - 1);
		for(Segment regressorSegment : regressorSegments){
			@SuppressWarnings("unused")
			True _true = regressorSegment.requirePredicate(True.class);
			Model model = regressorSegment.requireModel();

			MiningFunction modelMiningFunction = model.requireMiningFunction();
			switch(modelMiningFunction){
				case REGRESSION:
					break;
				default:
					throw new UnsupportedAttributeException(model, modelMiningFunction);
			}

			MathContext modelMathContext = model.getMathContext();
			if(!Objects.equals(mathContext, modelMathContext)){
				throw new UnsupportedAttributeException(model, modelMathContext);
			}

			checkMiningSchema(model);

			Output modelOutput = model.getOutput();
			if(modelOutput == null){
				throw new MissingElementException(MissingElementException.formatMessage(XPathUtil.formatElement(model.getClass()) + "/" + XPathUtil.formatElement(Output.class)), model);
			} // End if

			if(modelOutput.hasOutputFields()){
				List<OutputField> outputFields = modelOutput.getOutputFields();

				if(outputFields.size() != 1){
					throw new UnsupportedElementException(modelOutput);
				}

				OutputField outputField = Iterables.getOnlyElement(outputFields);

				ResultFeature resultFeature = outputField.getResultFeature();
				switch(resultFeature){
					case PREDICTED_VALUE:
						break;
					default:
						throw new UnsupportedAttributeException(outputField, resultFeature);
				}
			} else

			{
				throw new MissingElementException(modelOutput, org.dmg.pmml.PMMLElements.OUTPUT_OUTPUTFIELDS);
			}

			@SuppressWarnings("unused")
			ModelTranslator<?> modelTranslator = newModelTranslator(model);
		}

		{
			Segment classifierSegment = segments.get(segments.size() - 1);

			@SuppressWarnings("unused")
			True _true = classifierSegment.requirePredicate(True.class);
			RegressionModel regressionModel = classifierSegment.requireModel(RegressionModel.class);

			MiningFunction modelMiningFunction = regressionModel.requireMiningFunction();
			switch(modelMiningFunction){
				case CLASSIFICATION:
					break;
				default:
					throw new UnsupportedAttributeException(regressionModel, modelMiningFunction);
			}

			MathContext modelMathContext = regressionModel.getMathContext();
			if(!Objects.equals(mathContext, modelMathContext)){
				throw new UnsupportedAttributeException(regressionModel, modelMathContext);
			}

			checkMiningSchema(regressionModel);
			checkLocalTransformations(regressionModel);
			checkTargets(regressionModel);

			List<RegressionTable> regressionTables = regressionModel.getRegressionTables();
			for(RegressionTable regressionTable : regressionTables){

				if(regressionTable.hasNumericPredictors()){
					List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

					if(numericPredictors.size() > 1){
						throw new InvalidElementException(regressionTable);
					}
				} // End if

				if(regressionTable.hasCategoricalPredictors() || regressionTable.hasPredictorTerms()){
					throw new UnsupportedElementException(regressionTable);
				}
			}
		}
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.requireSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Classification.class, segmentation, true, context);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			translateSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	private void translateSegmentation(Segmentation segmentation, TranslationContext context){
		MiningModel miningModel = getModel();

		List<Segment> segments = segmentation.requireSegments();

		List<Segment> regressorSegments = segments.subList(0, segments.size() - 1);
		for(Segment regressorSegment : regressorSegments){
			Model model = regressorSegment.requireModel();

			Output modelOutput = model.getOutput();

			OutputField outputField = Iterables.getOnlyElement(modelOutput.getOutputFields());

			ModelTranslator<?> modelTranslator = newModelTranslator(model);

			JMethod evaluateMethod = modelTranslator.translateRegressor(context);

			JInvocation methodInvocation = createEvaluatorMethodInvocation(evaluateMethod, context);

			context.declare(context.getValueType(), IdentifierUtil.create("value", outputField.requireName()), methodInvocation);

			pullUpDerivedFields(miningModel, model);
		}

		ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
			.construct("values");

		{
			Segment classifierSegment = segments.get(segments.size() - 1);

			RegressionModel regressionModel = classifierSegment.requireModel(RegressionModel.class);

			List<RegressionTable> regressionTables = regressionModel.getRegressionTables();
			for(RegressionTable regressionTable : regressionTables){
				List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

				Number intercept = regressionTable.requireIntercept();

				JExpression valueExpr;

				NumericPredictor numericPredictor = Iterables.getFirst(numericPredictors, null);
				if(numericPredictor != null){
					valueExpr = context.getVariable(IdentifierUtil.create("value", numericPredictor.requireField()));

					Number coefficient = numericPredictor.requireCoefficient();
					if(coefficient.doubleValue() != 1d){
						valueExpr = context.invoke(valueExpr, "multiply", coefficient);
					} // End if

					if(intercept.doubleValue() != 0d){
						valueExpr = context.invoke(valueExpr, "add", intercept);
					}
				} else

				{
					ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

					if(intercept.doubleValue() != 0d){
						valueExpr = valueFactoryRef.newValue(PMMLObjectUtil.createExpression(intercept, context));
					} else

					{
						valueExpr = valueFactoryRef.newValue();
					}
				}

				valueMapBuilder.update("put", regressionTable.getTargetCategory(), valueExpr);
			}

			RegressionModelTranslator.computeClassification(valueMapBuilder, regressionModel, context);

			pullUpOutputFields(miningModel, regressionModel);
		}
	}
}