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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.ProbabilityAggregator;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueAggregator;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.translator.AggregatorBuilder;
import org.jpmml.translator.ArrayManager;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.JBinaryFileInitializer;
import org.jpmml.translator.JDirectInitializer;
import org.jpmml.translator.JVarBuilder;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.Scope;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueBuilder;
import org.jpmml.translator.ValueFactoryRef;
import org.jpmml.translator.tree.NodeScoreDistributionManager;
import org.jpmml.translator.tree.NodeScoreManager;
import org.jpmml.translator.tree.ScoreFunction;
import org.jpmml.translator.tree.TreeModelTranslator;

public class TreeModelAggregatorTranslator extends MiningModelTranslator {

	public TreeModelAggregatorTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
			case CLASSIFICATION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();

		Segmentation segmentation = miningModel.getSegmentation();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		switch(multipleModelMethod){
			case SUM:
			case WEIGHTED_SUM:
			case AVERAGE:
			case WEIGHTED_AVERAGE:
			case MEDIAN:
			case WEIGHTED_MEDIAN:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		Segmentation.MissingPredictionTreatment missingPredictionTreatment = segmentation.getMissingPredictionTreatment();
		switch(missingPredictionTreatment){
			case RETURN_MISSING:
			case CONTINUE:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, missingPredictionTreatment);
		}

		List<Segment> segments = segmentation.getSegments();
		for(Segment segment : segments){
			Predicate predicate = segment.getPredicate();
			Model model = segment.getModel();

			if(!(predicate instanceof True)){
				throw new UnsupportedElementException(predicate);
			} // End if

			if(!(model instanceof TreeModel)){
				throw new UnsupportedElementException(model);
			} // End if

			if(!(mathContext).equals(model.getMathContext())){
				throw new UnsupportedAttributeException(model, model.getMathContext());
			}

			checkMiningSchema(model);
			checkTargets(model);
			checkOutput(model);

			@SuppressWarnings("unused")
			ModelTranslator<?> modelTranslator = newModelTranslator(model);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.getSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Value.class, segmentation, true, context);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			translateValueAggregatorSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		MiningModel miningModel = getModel();

		Segmentation segmentation = miningModel.getSegmentation();

		JMethod evaluateMethod = createEvaluatorMethod(Classification.class, segmentation, true, context);

		try {
			 context.pushScope(new MethodScope(evaluateMethod));

			 translateProbabilityAggregatorSegmentation(segmentation, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	@Override
	public Map<FieldName, FieldInfo> getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		Segmentation segmentation = (Segmentation)Iterables.getOnlyElement(bodyObjects);

		Set<Node> nodes = new LinkedHashSet<>();

		List<Segment> segments = segmentation.getSegments();
		for(Segment segment : segments){
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			nodes.add(node);
		}

		Map<FieldName, FieldInfo> fieldInfos = super.getFieldInfos(nodes);

		fieldInfos = TreeModelTranslator.enhanceFieldInfos(nodes, fieldInfos);

		return fieldInfos;
	}

	private void translateValueAggregatorSegmentation(Segmentation segmentation, TranslationContext context){
		MiningModel miningModel = getModel();

		MathContext mathContext = miningModel.getMathContext();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		List<Segment> segments = segmentation.getSegments();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(segmentation));

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		AggregatorBuilder aggregatorBuilder = new AggregatorBuilder(context);

		switch(multipleModelMethod){
			case SUM:
			case AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.UnivariateStatistic.class, "aggregator", valueFactoryRef);
				break;
			case MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.Median.class, "aggregator", valueFactoryRef, segments.size());
				break;
			case WEIGHTED_SUM:
			case WEIGHTED_AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.WeightedUnivariateStatistic.class, "aggregator", valueFactoryRef);
				break;
			case WEIGHTED_MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.WeightedMedian.class, "aggregator", valueFactoryRef, segments.size());
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		List<NodeScoreManager> scoreManagers = new ArrayList<>();

		List<Number> weights = null;

		List<JMethod> methods = new ArrayList<>();

		for(Segment segment : segments){
			True _true = (True)segment.getPredicate();
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			NodeScoreManager scoreManager = new NodeScoreManager(context.ref(Number.class), IdentifierUtil.create("scores", node));

			scoreManagers.add(scoreManager);

			switch(multipleModelMethod){
				case SUM:
				case AVERAGE:
				case MEDIAN:
					break;
				case WEIGHTED_SUM:
				case WEIGHTED_AVERAGE:
				case WEIGHTED_MEDIAN:
					{
						if(weights == null){
							weights = new ArrayList<>();
						}

						weights.add(segment.getWeight());
					}
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}

			JMethod method = createEvaluatorMethod(treeModel, node, scoreManager, fieldInfos, context);

			methods.add(method);
		}

		JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(Segmentation.class.getSimpleName(), segmentation) + ".data", context);

		List<Number[]> scoreValues = scoreManagers.stream()
			.map(scoreManager -> scoreManager.getValues())
			.collect(Collectors.toList());

		JFieldVar scoresVar = resourceInitializer.initNumbersList(IdentifierUtil.create("scores", segmentation), mathContext, scoreValues);

		JFieldVar weightsVar = null;

		if(weights != null){
			Number[] weightValues = weights.toArray(new Number[weights.size()]);

			weightsVar = resourceInitializer.initNumbers(IdentifierUtil.create("weights", segmentation), mathContext, weightValues);
		}

		JDirectInitializer codeInitializer = new JDirectInitializer(context);

		JFieldVar methodsVar = codeInitializer.initLambdas(IdentifierUtil.create("methods", segmentation), (context.ref(ToIntFunction.class)).narrow(ensureArgumentsType(context)), methods);

		JBlock block = context.block();

		try {
			JForLoop forLoop = block._for();

			JVar loopVar = forLoop.init(context._ref(int.class), "i", JExpr.lit(0));
			forLoop.test(loopVar.lt(JExpr.lit(segments.size())));
			forLoop.update(loopVar.incr());

			JBlock forBlock = forLoop.body();

			context.pushScope(new Scope(forBlock));

			JVar indexExpr = context.declare(int.class, "index", (methodsVar.invoke("get").arg(loopVar)).invoke("applyAsInt").arg((context.getArgumentsVariable()).getVariable()));

			context._returnIf(indexExpr.eq(TreeModelTranslator.NULL_RESULT), JExpr._null());

			JExpression scoreExpr = (scoresVar.invoke("get").arg(loopVar)).component(indexExpr);

			switch(multipleModelMethod){
				case SUM:
				case AVERAGE:
				case MEDIAN:
					aggregatorBuilder.update("add", scoreExpr);
					break;
				case WEIGHTED_SUM:
				case WEIGHTED_AVERAGE:
				case WEIGHTED_MEDIAN:
					JExpression weightExpr = weightsVar.invoke("get").arg(loopVar);

					aggregatorBuilder.update("add", scoreExpr, weightExpr);
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		} finally {
			context.popScope();
		}

		JVar aggregatorVar = aggregatorBuilder.getVariable();

		JInvocation valueInit;

		switch(multipleModelMethod){
			case SUM:
				valueInit = aggregatorVar.invoke("sum");
				break;
			case WEIGHTED_SUM:
				valueInit = aggregatorVar.invoke("weightedSum");
				break;
			case AVERAGE:
				valueInit = aggregatorVar.invoke("average");
				break;
			case WEIGHTED_AVERAGE:
				valueInit = aggregatorVar.invoke("weightedAverage");
				break;
			case MEDIAN:
				valueInit = aggregatorVar.invoke("median");
				break;
			case WEIGHTED_MEDIAN:
				valueInit = aggregatorVar.invoke("weightedMedian");
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		JVarBuilder resultBuilder = new ValueBuilder(context)
			.declare(context.getValueType(), "result", valueInit);

		context._return(resultBuilder.getVariable());
	}

	private void translateProbabilityAggregatorSegmentation(Segmentation segmentation, TranslationContext context){
		MiningModel miningModel = getModel();

		MathContext mathContext = miningModel.getMathContext();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		List<Segment> segments = segmentation.getSegments();

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(segmentation));

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		AggregatorBuilder aggregatorBuilder = new AggregatorBuilder(context);

		switch(multipleModelMethod){
			case AVERAGE:
				aggregatorBuilder.construct(ProbabilityAggregator.Average.class, "aggregator", valueFactoryRef);
				break;
			case WEIGHTED_AVERAGE:
				aggregatorBuilder.construct(ProbabilityAggregator.WeightedAverage.class, "aggregator", valueFactoryRef);
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		Object[] categories = getTargetCategories();

		List<NodeScoreDistributionManager<?>> scoreManagers = new ArrayList<>();

		List<Number> weights = null;

		List<JMethod> methods = new ArrayList<>();

		for(Segment segment : segments){
			True _true = (True)segment.getPredicate();
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			NodeScoreDistributionManager<?> scoreManager = new NodeScoreDistributionManager<Number>(context.ref(Number[].class), IdentifierUtil.create("scores", node), categories){

				private ValueFactory<Number> valueFactory = ModelTranslator.getValueFactory(treeModel);


				@Override
				public ValueFactory<Number> getValueFactory(){
					return this.valueFactory;
				}
			};

			scoreManagers.add(scoreManager);

			switch(multipleModelMethod){
				case AVERAGE:
					break;
				case WEIGHTED_AVERAGE:
					{
						if(weights == null){
							weights = new ArrayList<>();
						}

						weights.add(segment.getWeight());
					}
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}

			JMethod method = createEvaluatorMethod(treeModel, node, scoreManager, fieldInfos, context);

			methods.add(method);
		}

		JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(Segmentation.class.getSimpleName(), segmentation) + ".data", context);

		List<Number[][]> scoreValues = scoreManagers.stream()
			.map(scoreManager -> scoreManager.getValues())
			.collect(Collectors.toList());

		JFieldVar scoresVar = resourceInitializer.initNumberArraysList(IdentifierUtil.create("scores", segmentation), mathContext, scoreValues, categories.length);

		JFieldVar weightsVar = null;

		if(weights != null){
			Number[] weightValues = weights.toArray(new Number[weights.size()]);

			weightsVar = resourceInitializer.initNumbers(IdentifierUtil.create("weights", segmentation), mathContext, weightValues);
		}

		JDirectInitializer codeInitializer = new JDirectInitializer(context);

		JFieldVar methodsVar = codeInitializer.initLambdas(IdentifierUtil.create("methods", segmentation), (context.ref(ToIntFunction.class)).narrow(ensureArgumentsType(context)), methods);

		JFieldVar categoriesVar = codeInitializer.initTargetCategories("targetCategories", Arrays.asList(categories));

		aggregatorBuilder.update("init", categoriesVar);

		JBlock block = context.block();

		try {
			JForLoop forLoop = block._for();

			JVar loopVar = forLoop.init(context._ref(int.class), "i", JExpr.lit(0));
			forLoop.test(loopVar.lt(JExpr.lit(segments.size())));
			forLoop.update(loopVar.incr());

			JBlock forBlock = forLoop.body();

			context.pushScope(new Scope(forBlock));

			JVar indexExpr = context.declare(int.class, "index", (methodsVar.invoke("get").arg(loopVar)).invoke("applyAsInt").arg((context.getArgumentsVariable()).getVariable()));

			context._returnIf(indexExpr.eq(TreeModelTranslator.NULL_RESULT), JExpr._null());

			JExpression scoreExpr = (scoresVar.invoke("get").arg(loopVar)).component(indexExpr);

			switch(multipleModelMethod){
				case AVERAGE:
					aggregatorBuilder.update("add", scoreExpr);
					break;
				case WEIGHTED_AVERAGE:
					JExpression weightExpr = weightsVar.invoke("get").arg(loopVar);

					aggregatorBuilder.update("add", scoreExpr, weightExpr);
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
		} finally {
			context.popScope();
		}

		JVar aggregatorVar = aggregatorBuilder.getVariable();

		JInvocation valueMapInit;

		switch(multipleModelMethod){
			case AVERAGE:
				valueMapInit = aggregatorVar.invoke("averageMap");
				break;
			case WEIGHTED_AVERAGE:
				valueMapInit = aggregatorVar.invoke("weightedAverageMap");
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		context._return(context._new(ProbabilityDistribution.class, valueMapInit));
	}

	private <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> JMethod createEvaluatorMethod(TreeModel treeModel, Node node, ScoreManager scoreManager, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		JMethod method = createEvaluatorMethod(int.class, node, false, context);

		try {
			context.pushScope(new MethodScope(method));

			TreeModelTranslator.translateNode(treeModel, node, scoreManager, fieldInfos, context);
		} finally {
			context.popScope();
		}

		return method;
	}
}