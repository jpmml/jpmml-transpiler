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
package org.jpmml.translator.regression;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Multimaps;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Output;
import org.dmg.pmml.OutputField;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ResultFeature;
import org.dmg.pmml.TextIndex;
import org.dmg.pmml.regression.CategoricalPredictor;
import org.dmg.pmml.regression.NumericPredictor;
import org.dmg.pmml.regression.PredictorTerm;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.regression.RegressionTable;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.TokenizedString;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.VoteDistribution;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.evaluator.regression.RegressionModelUtil;
import org.jpmml.model.InvalidElementException;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.FunctionInvocation;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.JBinaryFileInitializer;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.Modifiers;
import org.jpmml.translator.OperableRef;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.Scope;
import org.jpmml.translator.TextIndexUtil;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueBuilder;
import org.jpmml.translator.ValueMapBuilder;

public class RegressionModelTranslator extends ModelTranslator<RegressionModel> {

	public RegressionModelTranslator(PMML pmml, RegressionModel regressionModel){
		super(pmml, regressionModel);

		MiningFunction miningFunction = regressionModel.requireMiningFunction();
		switch(miningFunction){
			case REGRESSION:
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(regressionModel, miningFunction);
		}

		List<RegressionTable> regressionTables = regressionModel.requireRegressionTables();
		for(RegressionTable regressionTable : regressionTables){

			if(regressionTable.hasPredictorTerms()){
				List<PredictorTerm> predictorTerms = regressionTable.getPredictorTerms();

				throw new UnsupportedElementException(Iterables.getFirst(predictorTerms, null));
			}
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		RegressionModel regressionModel = getModel();

		List<RegressionTable> regressionTables = regressionModel.getRegressionTables();

		Map<String, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

		RegressionTable regressionTable = Iterables.getOnlyElement(regressionTables);

		JMethod evaluateMethod = createEvaluatorMethod(Value.class, regressionTable, true, context);

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

		Map<String, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

		JMethod evaluateListMethod = createEvaluatorMethod(Classification.class, regressionTables, true, context);

		try {
			context.pushScope(new MethodScope(evaluateListMethod));

			ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
				.construct("values");

			for(RegressionTable regressionTable : regressionTables){
				JMethod evaluateMethod = createEvaluatorMethod(Value.class, regressionTable, true, context);

				try {
					context.pushScope(new MethodScope(evaluateMethod));

					ValueBuilder valueBuilder = translateRegressionTable(regressionTable, fieldInfos, context);

					context._return(valueBuilder.getVariable());
				} finally {
					context.popScope();
				}

				valueMapBuilder.update("put", regressionTable.getTargetCategory(), createEvaluatorMethodInvocation(evaluateMethod, context));
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

			switch(normalizationMethod){
				case NONE:
				case LOGIT:
				case PROBIT:
				case CLOGLOG:
				case LOGLOG:
				case CAUCHIT:
					valueMapBuilder.staticUpdate(RegressionModelUtil.class, "computeBinomialProbabilities", normalizationMethod);
					break;
				case SIMPLEMAX:
				case SOFTMAX:
					valueMapBuilder.staticUpdate(RegressionModelUtil.class, "computeMultinomialProbabilities", normalizationMethod);
					break;
				default:
					throw new InvalidElementException(regressionModel);
			}
		} else

		if(regressionTables.size() > 2){

			switch(normalizationMethod){
				case NONE:
				case SIMPLEMAX:
				case SOFTMAX:
					valueMapBuilder.staticUpdate(RegressionModelUtil.class, "computeMultinomialProbabilities", normalizationMethod);
					break;
				default:
					throw new InvalidElementException(regressionModel);
			}
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

		JExpression classificationExpr;

		if(probabilistic){
			classificationExpr = context._new(ProbabilityDistribution.class, valueMapBuilder);
		} else

		{
			classificationExpr = context._new(VoteDistribution.class, valueMapBuilder);
		}

		context._return(classificationExpr);
	}

	static
	public ValueBuilder translateRegressionTable(RegressionTable regressionTable, Map<String, FieldInfo> fieldInfos, TranslationContext context){
		ValueBuilder valueBuilder = new ValueBuilder(context)
			.declare(IdentifierUtil.create("result", regressionTable), context.getValueFactoryVariable().newValue());

		if(regressionTable.hasNumericPredictors()){
			List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

			ListMultimap<String, FunctionInvocationPredictor> tfTerms = ArrayListMultimap.create();

			for(NumericPredictor numericPredictor : numericPredictors){
				FieldInfo fieldInfo = getFieldInfo(numericPredictor, fieldInfos);

				FunctionInvocation functionInvocation = fieldInfo.getFunctionInvocation();

				if((functionInvocation instanceof FunctionInvocation.Tf) || (functionInvocation instanceof FunctionInvocation.TfIdf)){
					FunctionInvocationPredictor tfTerm = new FunctionInvocationPredictor(numericPredictor, functionInvocation);

					FunctionInvocation.Tf tf = tfTerm.getTf();

					tfTerms.put(tf.getTextField(), tfTerm);

					continue;
				}

				Number coefficient = numericPredictor.requireCoefficient();
				Integer exponent = numericPredictor.getExponent();

				OperableRef operableRef = context.ensureOperable(fieldInfo, (method) -> true);

				if(exponent != null && exponent.intValue() != 1){
					valueBuilder.update("add", coefficient, operableRef.getExpression(), exponent);
				} else

				{
					if(coefficient.doubleValue() != 1d){
						valueBuilder.update("add", coefficient, operableRef.getExpression());
					} else

					{
						valueBuilder.update("add", operableRef.getExpression());
					}
				}
			}

			addTermFrequencies(regressionTable, valueBuilder, Multimaps.asMap(tfTerms), fieldInfos, context);
		} // End if

		if(regressionTable.hasCategoricalPredictors()){
			Map<String, List<CategoricalPredictor>> fieldCategoricalPredictors = regressionTable.getCategoricalPredictors().stream()
				.collect(Collectors.groupingBy(categoricalPredictor -> categoricalPredictor.requireField(), Collectors.toList()));

			JBlock block = context.block();

			Collection<Map.Entry<String, List<CategoricalPredictor>>> entries = fieldCategoricalPredictors.entrySet();
			for(Map.Entry<String, List<CategoricalPredictor>> entry : entries){
				FieldInfo fieldInfo = getFieldInfo(entry.getKey(), fieldInfos);

				JMethod evaluateCategoryMethod = createEvaluatorMethod(Number.class, entry.getValue(), false, context);

				try {
					context.pushScope(new MethodScope(evaluateCategoryMethod));

					OperableRef operableRef = context.ensureOperable(fieldInfo, (method) -> true);

					Map<Object, Number> categoryValues = (entry.getValue()).stream()
						.collect(Collectors.toMap(CategoricalPredictor::requireValue, CategoricalPredictor::requireCoefficient));

					context._return(operableRef.getExpression(), categoryValues, null);
				} finally {
					context.popScope();
				}

				JVar categoryValueVar = context.declare(Number.class, IdentifierUtil.create("lookup", entry.getKey()), createEvaluatorMethodInvocation(evaluateCategoryMethod, context));

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

		Number intercept = regressionTable.requireIntercept();
		if(intercept.doubleValue() != 0d){
			valueBuilder.update("add", intercept);
		}

		return valueBuilder;
	}

	static
	private void addTermFrequencies(RegressionTable regressionTable, ValueBuilder valueBuilder, Map<String, List<FunctionInvocationPredictor>> tfTerms, Map<String, FieldInfo> fieldInfos, TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		if(tfTerms.isEmpty()){
			return;
		}

		JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(RegressionTable.class.getSimpleName(), regressionTable) + ".data", context);

		Function<FunctionInvocationPredictor, TextIndex> textIndexFunction = new Function<FunctionInvocationPredictor, TextIndex>(){

			@Override
			public TextIndex apply(FunctionInvocationPredictor tfTerm){
				FunctionInvocation.Tf tf = tfTerm.getTf();

				return tf.getTextIndex();
			}
		};

		Function<FunctionInvocationPredictor, TokenizedString> termFunction = new Function<FunctionInvocationPredictor, TokenizedString>(){

			@Override
			public TokenizedString apply(FunctionInvocationPredictor tfTerm){
				FunctionInvocation.Tf tf = tfTerm.getTf();

				return tf.getTermTokens();
			}
		};

		Function<FunctionInvocationPredictor, Number> coefficientFunction = new Function<FunctionInvocationPredictor, Number>(){

			@Override
			public Number apply(FunctionInvocationPredictor tfTerm){
				NumericPredictor numericPredictor = tfTerm.numericPredictor;

				return numericPredictor.requireCoefficient();
			}
		};

		Function<FunctionInvocationPredictor, Number> weightFunction = new Function<FunctionInvocationPredictor, Number>(){

			@Override
			public Number apply(FunctionInvocationPredictor tfTerm){
				FunctionInvocation.TfIdf tfIdf = tfTerm.getTfIdf();

				return tfIdf.getWeight();
			}
		};

		Collection<Map.Entry<String, List<FunctionInvocationPredictor>>> entries = tfTerms.entrySet();
		for(Map.Entry<String, List<FunctionInvocationPredictor>> entry : entries){
			String name = entry.getKey();
			Collection<FunctionInvocationPredictor> predictors = entry.getValue();

			Set<TextIndex> textIndexes = predictors.stream()
				.map(textIndexFunction)
				.collect(Collectors.toSet());

			TextIndex textIndex = Iterables.getOnlyElement(textIndexes);

			TextIndex localTextIndex = TextIndexUtil.toLocalTextIndex(textIndex, name);

			JFieldVar textIndexVar = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.ref(TextIndex.class), IdentifierUtil.create("textIndex", regressionTable, name), PMMLObjectUtil.createObject(localTextIndex, context));

			TokenizedString[] terms = predictors.stream()
				.map(termFunction)
				.toArray(TokenizedString[]::new);

			JFieldVar termsVar = resourceInitializer.initTokenizedStringLists(IdentifierUtil.create("terms", regressionTable, name), terms);

			JFieldVar termIndicesVar = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.ref(Map.class).narrow(Arrays.asList(context.ref(TokenizedString.class), context.ref(Integer.class))), IdentifierUtil.create("termIndices", regressionTable, name), context._new(LinkedHashMap.class));

			JForLoop termIndicesForLoop = new JForLoop();

			JVar termIndicesLoopVar = termIndicesForLoop.init(context._ref(int.class), "i", JExpr.lit(0));
			termIndicesForLoop.test(termIndicesLoopVar.lt(JExpr.lit(terms.length)));
			termIndicesForLoop.update(termIndicesLoopVar.incr());

			JBlock termIndicesForBlock = termIndicesForLoop.body();

			termIndicesForBlock.add(termIndicesVar.invoke("put").arg(termsVar.invoke("get").arg(termIndicesLoopVar)).arg(termIndicesLoopVar));

			resourceInitializer.add(termIndicesForLoop);

			Number[] coefficients = predictors.stream()
				.map(coefficientFunction)
				.toArray(Number[]::new);

			JFieldVar coefficientsVar = resourceInitializer.initNumbers(IdentifierUtil.create("coefficients", regressionTable, name), MathContext.DOUBLE, coefficients);

			Number[] weights = predictors.stream()
				.map(weightFunction)
				.toArray(Number[]::new);

			JFieldVar weightsVar = null;

			if((Arrays.stream(weights)).anyMatch(weight -> (weights != null && weight.doubleValue() != 1d))){
				weightsVar = resourceInitializer.initNumbers(IdentifierUtil.create("weights", regressionTable, name), MathContext.DOUBLE, weights);
			}

			int maxLength = Arrays.stream(terms)
				.mapToInt(TokenizedString::size)
				.max().orElseThrow(NoSuchElementException::new);

			JVar termFrequencyTableVar = (JVar)TextIndexUtil.computeTermFrequencyTable(null, localTextIndex, textIndexVar, termIndicesVar.invoke("keySet"), maxLength, context);

			JVar entriesVar = context.declare(context.ref(Collection.class).narrow(context.ref(Map.Entry.class).narrow(((JClass)termFrequencyTableVar.type()).getTypeParameters())), "entries", termFrequencyTableVar.invoke("entrySet"));

			JBlock block = context.block();

			JForEach entriesForEach = block.forEach(((JClass)entriesVar.type()).getTypeParameters().get(0), "entry", entriesVar);

			try {
				context.pushScope(new Scope(entriesForEach.body()));

				JVar termVar = context.declare(context.ref(TokenizedString.class), "term", entriesForEach.var().invoke("getKey"));
				JVar frequencyVar = context.declare(context.ref(Integer.class), "frequency", entriesForEach.var().invoke("getValue"));

				JVar indexVar = context.declare(context.ref(Integer.class), "termIndex", termIndicesVar.invoke("get").arg(termVar));

				JVar coefficientVar = context.declare(context.ref(Number.class), "coefficient", coefficientsVar.invoke("get").arg(indexVar));

				List<JVar> factorVars = new ArrayList<>();
				factorVars.add(coefficientVar);

				if(weightsVar != null){
					JVar weightVar = context.declare(context.ref(Number.class), "weight", weightsVar.invoke("get").arg(indexVar));

					factorVars.add(weightVar);
				}

				TextIndex.LocalTermWeights localTermWeights = textIndex.getLocalTermWeights();
				switch(localTermWeights){
					case BINARY:
						break;
					case TERM_FREQUENCY:
						factorVars.add(frequencyVar);
						break;
					case LOGARITHMIC:
						JVar logFrequencyVar = context.declare(context.ref(Double.class), "logFrequency", context.staticInvoke(Math.class, "log10", JExpr.lit(1).plus(frequencyVar)));
						factorVars.add(logFrequencyVar);
						break;
					default:
						throw new UnsupportedAttributeException(localTextIndex, localTermWeights);
				}

				valueBuilder.update("add", factorVars.toArray(new JVar[factorVars.size()]));
			} finally {
				context.popScope();
			}
		}
	}

	static
	private class FunctionInvocationPredictor {

		private NumericPredictor numericPredictor = null;

		private FunctionInvocation functionInvocation = null;


		private FunctionInvocationPredictor(NumericPredictor numericPredictor, FunctionInvocation functionInvocation){
			this.numericPredictor = numericPredictor;
			this.functionInvocation = functionInvocation;
		}

		public FunctionInvocation.Tf getTf(){
			return TextIndexUtil.asTf(this.functionInvocation);
		}

		public FunctionInvocation.TfIdf getTfIdf(){
			return TextIndexUtil.asTfIdf(this.functionInvocation);
		}
	}
}