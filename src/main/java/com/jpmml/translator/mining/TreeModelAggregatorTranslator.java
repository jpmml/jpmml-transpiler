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
package com.jpmml.translator.mining;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.ObjectBuilder;
import com.jpmml.translator.TranslationContext;
import com.jpmml.translator.ValueBuilder;
import com.jpmml.translator.tree.NodeScoreManager;
import com.jpmml.translator.tree.TreeModelTranslator;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.True;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueAggregator;

public class TreeModelAggregatorTranslator extends MiningModelTranslator {

	public TreeModelAggregatorTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();
		switch(mathContext){
			case FLOAT:
			case DOUBLE:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, mathContext);
		}

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
			}

			checkMiningSchema(model);
			checkLocalTransformations(model);
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

		JMethod evaluateMethod = context.evaluatorMethod(JMod.PUBLIC, Value.class, segmentation, true, true);

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

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		List<Segment> segments = segmentation.getSegments();

		JVar valueFactoryVar = context.getValueFactoryVariable();

		JExpression newSimpleVectorExpr = valueFactoryVar.invoke("newVector").arg(JExpr.lit(0));
		JExpression newComplexVectorExpr = valueFactoryVar.invoke("newVector").arg(JExpr.lit(segments.size()));

		ObjectBuilder aggregatorBuilder = new ObjectBuilder(context);

		switch(multipleModelMethod){
			case SUM:
			case AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.class, "aggregator", newSimpleVectorExpr);
				break;
			case MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.class, "aggregator", newComplexVectorExpr);
				break;
			case WEIGHTED_SUM:
			case WEIGHTED_AVERAGE:
				aggregatorBuilder.construct(ValueAggregator.class, "aggregator", newSimpleVectorExpr, newSimpleVectorExpr, newSimpleVectorExpr);
				break;
			case WEIGHTED_MEDIAN:
				aggregatorBuilder.construct(ValueAggregator.class, "aggregator", newComplexVectorExpr, newComplexVectorExpr);
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
		}

		for(Segment segment : segments){
			True _true = (True)segment.getPredicate();
			TreeModel treeModel = (TreeModel)segment.getModel();

			Node node = treeModel.getNode();

			TreeModelTranslator treeModelTranslator = (TreeModelTranslator)newModelTranslator(treeModel);

			NodeScoreManager nodeScoreManager = new NodeScoreManager("scores$" + System.identityHashCode(node), context);

			Map<FieldName, Field<?>> activeFields = treeModelTranslator.getActiveFields(Collections.singleton(node));

			JMethod evaluateMethod = context.evaluatorMethod(JMod.PUBLIC, int.class, node, false, false);

			JInvocation evaluateInvocation = JExpr.invoke(evaluateMethod);

			Collection<? extends Map.Entry<FieldName, Field<?>>> entries = activeFields.entrySet();
			for(Map.Entry<FieldName, Field<?>> entry : entries){
				Field<?> field = entry.getValue();

				JVar valueVar = context.ensureValueVariable(field, null);

				evaluateMethod.param(valueVar.type(), valueVar.name());

				evaluateInvocation = evaluateInvocation.arg(valueVar);
			}

			try {
				context.pushScope(new MethodScope(evaluateMethod));

				TreeModelTranslator.translateNode(node, nodeScoreManager, activeFields, context);
			} finally {
				context.popScope();
			}

			JExpression valueExpr = nodeScoreManager.getComponent(evaluateInvocation);

			switch(multipleModelMethod){
				case SUM:
				case AVERAGE:
				case MEDIAN:
					aggregatorBuilder.update("add", valueExpr);
					break;
				case WEIGHTED_SUM:
				case WEIGHTED_AVERAGE:
				case WEIGHTED_MEDIAN:
					aggregatorBuilder.update("add", valueExpr, segment.getWeight());
					break;
				default:
					throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
			}
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

		ObjectBuilder resultBuilder = new ValueBuilder(context)
			.declare(Value.class, "result", valueInit);

		context._return(resultBuilder.getVariable());
	}
}