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

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
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
import com.sun.codemodel.JAssignment;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.OpType;
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
import org.jpmml.evaluator.InvalidElementException;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.TextUtil;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.VoteDistribution;
import org.jpmml.evaluator.regression.RegressionModelUtil;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.FunctionInvocation;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.JBinaryFileInitializer;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.OperableRef;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.Scope;
import org.jpmml.translator.StringRef;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueBuilder;
import org.jpmml.translator.ValueMapBuilder;

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

		List<RegressionTable> regressionTables = regressionModel.getRegressionTables();
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

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

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

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(new HashSet<>(regressionTables));

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
	public ValueBuilder translateRegressionTable(RegressionTable regressionTable, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		ValueBuilder valueBuilder = new ValueBuilder(context)
			.declare(IdentifierUtil.create("result", regressionTable), context.getValueFactoryVariable().newValue());

		if(regressionTable.hasNumericPredictors()){
			List<NumericPredictor> numericPredictors = regressionTable.getNumericPredictors();

			ListMultimap<FieldName, FunctionInvocationPredictor> tfTerms = ArrayListMultimap.create();

			for(NumericPredictor numericPredictor : numericPredictors){
				FieldInfo fieldInfo = getFieldInfo(numericPredictor, fieldInfos);

				FunctionInvocation functionInvocation = fieldInfo.getFunctionInvocation();

				if((functionInvocation instanceof FunctionInvocation.Tf) || (functionInvocation instanceof FunctionInvocation.TfIdf)){
					FunctionInvocationPredictor tfTerm = new FunctionInvocationPredictor(numericPredictor, functionInvocation);

					FunctionInvocation.Tf tf = tfTerm.getTf();

					tfTerms.put(tf.getTextField(), tfTerm);

					continue;
				}

				Number coefficient = numericPredictor.getCoefficient();
				Integer exponent = numericPredictor.getExponent();

				OperableRef operableRef = context.ensureOperableVariable(fieldInfo);

				if(exponent != null && exponent.intValue() != 1){
					valueBuilder.update("add", coefficient, operableRef.getVariable(), exponent);
				} else

				{
					if(coefficient.doubleValue() != 1d){
						valueBuilder.update("add", coefficient, operableRef.getVariable());
					} else

					{
						valueBuilder.update("add", operableRef.getVariable());
					}
				}
			}

			addTermFrequencies(regressionTable, valueBuilder, Multimaps.asMap(tfTerms), fieldInfos, context);
		} // End if

		if(regressionTable.hasCategoricalPredictors()){
			Map<FieldName, List<CategoricalPredictor>> fieldCategoricalPredictors = regressionTable.getCategoricalPredictors().stream()
				.collect(Collectors.groupingBy(categoricalPredictor -> categoricalPredictor.getField(), Collectors.toList()));

			JBlock block = context.block();

			Collection<Map.Entry<FieldName, List<CategoricalPredictor>>> entries = fieldCategoricalPredictors.entrySet();
			for(Map.Entry<FieldName, List<CategoricalPredictor>> entry : entries){
				FieldInfo fieldInfo = getFieldInfo(entry.getKey(), fieldInfos);

				JMethod evaluateCategoryMethod = createEvaluatorMethod(Number.class, entry.getValue(), false, context);

				try {
					context.pushScope(new MethodScope(evaluateCategoryMethod));

					OperableRef operableRef = context.ensureOperableVariable(fieldInfo);

					Map<Object, Number> categoryValues = (entry.getValue()).stream()
						.collect(Collectors.toMap(CategoricalPredictor::getValue, CategoricalPredictor::getCoefficient));

					context._return(operableRef.getVariable(), categoryValues, null);
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

		Number intercept = regressionTable.getIntercept();
		if(intercept != null && intercept.doubleValue() != 0d){
			valueBuilder.update("add", intercept);
		}

		return valueBuilder;
	}

	static
	private void addTermFrequencies(RegressionTable regressionTable, ValueBuilder valueBuilder, Map<FieldName, List<FunctionInvocationPredictor>> tfTerms, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		JDefinedClass owner = context.getOwner();

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

		Function<FunctionInvocationPredictor, List<String>> termFunction = new Function<FunctionInvocationPredictor, List<String>>(){

			@Override
			public List<String> apply(FunctionInvocationPredictor tfTerm){
				FunctionInvocation.Tf tf = tfTerm.getTf();

				return tf.getTermTokens();
			}
		};

		Function<FunctionInvocationPredictor, Number> coefficientFunction = new Function<FunctionInvocationPredictor, Number>(){

			@Override
			public Number apply(FunctionInvocationPredictor tfTerm){
				NumericPredictor numericPredictor = tfTerm.numericPredictor;

				return numericPredictor.getCoefficient();
			}
		};

		Function<FunctionInvocationPredictor, Number> weightFunction = new Function<FunctionInvocationPredictor, Number>(){

			@Override
			public Number apply(FunctionInvocationPredictor tfTerm){
				FunctionInvocation.TfIdf tfIdf = tfTerm.getTfIdf();

				return tfIdf.getWeight();
			}
		};

		Collection<Map.Entry<FieldName, List<FunctionInvocationPredictor>>> entries = tfTerms.entrySet();
		for(Map.Entry<FieldName, List<FunctionInvocationPredictor>> entry : entries){
			FieldName name = entry.getKey();
			Collection<FunctionInvocationPredictor> predictors = entry.getValue();

			Set<TextIndex> textIndexes = predictors.stream()
				.map(textIndexFunction)
				.collect(Collectors.toSet());

			TextIndex textIndex = Iterables.getOnlyElement(textIndexes);

			TextIndex localTextIndex = new TextIndex(name, null)
				.setLocalTermWeights(textIndex.getLocalTermWeights())
				.setCaseSensitive(textIndex.isCaseSensitive())
				.setMaxLevenshteinDistance(textIndex.getMaxLevenshteinDistance())
				.setCountHits(textIndex.getCountHits())
				.setWordSeparatorCharacterRE(textIndex.getWordSeparatorCharacterRE())
				.setTokenize(textIndex.isTokenize());

			if(textIndex.hasTextIndexNormalizations()){
				(localTextIndex.getTextIndexNormalizations()).addAll(textIndex.getTextIndexNormalizations());
			}

			JFieldVar localTextIndexVar = owner.field(ModelTranslator.MEMBER_PRIVATE, context.ref(TextIndex.class), IdentifierUtil.create("textIndex", regressionTable), PMMLObjectUtil.createObject(localTextIndex, context));

			List<String>[] terms = predictors.stream()
				.map(termFunction)
				.toArray(List[]::new);

			JFieldVar termsVar = resourceInitializer.initStringLists(IdentifierUtil.create("terms", regressionTable), terms);

			JFieldVar termIndicesVar = owner.field(ModelTranslator.MEMBER_PRIVATE, context.ref(Map.class).narrow(Arrays.asList(context.ref(List.class).narrow(String.class), context.ref(Integer.class))), IdentifierUtil.create("termIndices", regressionTable), JExpr._new(context.ref(LinkedHashMap.class).narrow(Collections.emptyList())));

			JBlock init = owner.init();

			JForLoop termIndicesForLoop = init._for();

			JVar termIndicesLoopVar = termIndicesForLoop.init(context._ref(int.class), "i", JExpr.lit(0));
			termIndicesForLoop.test(termIndicesLoopVar.lt(JExpr.lit(terms.length)));
			termIndicesForLoop.update(termIndicesLoopVar.incr());

			JBlock termIndicesForBlock = termIndicesForLoop.body();

			termIndicesForBlock.add(termIndicesVar.invoke("put").arg(termsVar.invoke("get").arg(termIndicesLoopVar)).arg(termIndicesLoopVar));

			Number[] coefficients = predictors.stream()
				.map(coefficientFunction)
				.toArray(Number[]::new);

			JFieldVar coefficientsVar = resourceInitializer.initNumbers(IdentifierUtil.create("coefficients", regressionTable), MathContext.DOUBLE, coefficients);

			Number[] weights = predictors.stream()
				.map(weightFunction)
				.toArray(Number[]::new);

			JFieldVar weightsVar = resourceInitializer.initNumbers(IdentifierUtil.create("weights", regressionTable), MathContext.DOUBLE, weights);

			// XXX
			FieldInfo textFieldInfo = new FieldInfo(new DerivedField(localTextIndex.getTextField(), OpType.CATEGORICAL, DataType.STRING, null));

			StringRef textRef = (StringRef)context.ensureOperableVariable(textFieldInfo);

			JVar textVar = textRef.getVariable();

			if(localTextIndex.hasTextIndexNormalizations()){
				JInvocation textNormalizationInvocation = context.staticInvoke(TextUtil.class, "normalize", localTextIndexVar, textVar);

				context.add((JAssignment)textVar.assign(textNormalizationInvocation));
			}

			JInvocation textTokenizationInvocation = context.staticInvoke(TextUtil.class, "tokenize", localTextIndexVar, textVar);

			JVar textTokensVar = context.declare(context.ref(List.class).narrow(String.class), textVar.name() + "Tokens", textTokenizationInvocation);

			int maxLength = Arrays.stream(terms)
				.mapToInt(List::size)
				.max().orElseThrow(NoSuchElementException::new);

			JInvocation termFrequencyTableInvocation = context.staticInvoke(TextUtil.class, "termFrequencyTable", localTextIndexVar, textTokensVar, termIndicesVar.invoke("keySet"), JExpr.lit(maxLength));

			JVar termFrequencyTableVar = context.declare(context.ref(Map.class).narrow(Arrays.asList((JClass)textTokensVar.type(), context.ref(Integer.class))), textVar.name() + "FrequencyTable", termFrequencyTableInvocation);

			JVar entriesVar = context.declare(context.ref(Collection.class).narrow(context.ref(Map.Entry.class).narrow(((JClass)termFrequencyTableVar.type()).getTypeParameters())), "entries", termFrequencyTableVar.invoke("entrySet"));

			JBlock block = context.block();

			JForEach entriesForEach = block.forEach(((JClass)entriesVar.type()).getTypeParameters().get(0), "entry", entriesVar);

			try {
				context.pushScope(new Scope(entriesForEach.body()));

				JVar termVar = context.declare(context.ref(List.class).narrow(String.class), "term", entriesForEach.var().invoke("getKey"));
				JVar frequencyVar = context.declare(context.ref(Integer.class), "frequency", entriesForEach.var().invoke("getValue"));

				JVar indexVar = context.declare(context.ref(Integer.class), "termIndex", termIndicesVar.invoke("get").arg(termVar));

				JVar coefficientVar = context.declare(context.ref(Number.class), "coefficient", coefficientsVar.invoke("get").arg(indexVar));
				JVar weightVar = context.declare(context.ref(Number.class), "weight", weightsVar.invoke("get").arg(indexVar));

				valueBuilder.update("add", coefficientVar, weightVar, frequencyVar);
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
			FunctionInvocation functionInvocation = this.functionInvocation;

			if(functionInvocation instanceof FunctionInvocation.Tf){
				FunctionInvocation.Tf tf = (FunctionInvocation.Tf)functionInvocation;

				return tf;
			} else

			if(functionInvocation instanceof FunctionInvocation.TfIdf){
				FunctionInvocation.TfIdf tfIdf = (FunctionInvocation.TfIdf)functionInvocation;
				FunctionInvocation.Tf tf = tfIdf.getTf();

				return tf;
			}

			throw new IllegalArgumentException();
		}

		public FunctionInvocation.TfIdf getTfIdf(){
			FunctionInvocation functionInvocation = this.functionInvocation;

			if(functionInvocation instanceof FunctionInvocation.Tf){
				FunctionInvocation.Tf tf = (FunctionInvocation.Tf)functionInvocation;

				return new FunctionInvocation.TfIdf(){

					@Override
					public FunctionInvocation.Tf getTf(){
						return tf;
					}

					@Override
					public Number getWeight(){
						return 1;
					}
				};
			} else

			if(functionInvocation instanceof FunctionInvocation.TfIdf){
				FunctionInvocation.TfIdf tfIdf = (FunctionInvocation.TfIdf)functionInvocation;

				return tfIdf;
			}

			throw new IllegalArgumentException();
		}
	}
}