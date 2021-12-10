/*
 * Copyright (c) 2021 Villu Ruusmann
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

import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.Target;
import org.dmg.pmml.Targets;
import org.dmg.pmml.True;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.tree.ComplexNode;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.Value;
import org.jpmml.model.visitors.AbstractVisitor;
import org.jpmml.model.visitors.NodeFilterer;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.JCompoundAssignment;
import org.jpmml.translator.JExprUtil;
import org.jpmml.translator.JVarBuilder;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.PredicateKey;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueBuilder;
import org.jpmml.translator.tree.NodeGroup;
import org.jpmml.translator.tree.NodeGroupUtil;
import org.jpmml.translator.tree.Scorer;
import org.jpmml.translator.tree.TreeModelTranslator;

public class TreeModelBoosterTranslator extends MiningModelTranslator {

	public TreeModelBoosterTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		MiningFunction miningFunction = miningModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, miningFunction);
		}

		MathContext mathContext = miningModel.getMathContext();

		Segmentation segmentation = miningModel.getSegmentation();

		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.getMultipleModelMethod();
		switch(multipleModelMethod){
			case SUM:
				break;
			default:
				throw new UnsupportedAttributeException(segmentation, multipleModelMethod);
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

			TreeModel treeModel = (TreeModel)model;

			TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
			switch(noTrueChildStrategy){
				case RETURN_LAST_PREDICTION:
					break;
				default:
					throw new UnsupportedAttributeException(treeModel, noTrueChildStrategy);
			}

			TreeModel.MissingValueStrategy missingValueStrategy = treeModel.getMissingValueStrategy();
			switch(missingValueStrategy){
				case NONE:
					break;
				default:
					throw new UnsupportedAttributeException(treeModel, missingValueStrategy);
			}
		}

		AtomicInteger nodeCount = new AtomicInteger(0);

		Visitor nodeCounter = new AbstractVisitor(){

			@Override
			public VisitorAction visit(Node node){
				nodeCount.incrementAndGet();

				return super.visit(node);
			}
		};
		nodeCounter.applyTo(segmentation);

		if(nodeCount.get() > TreeModelBoosterTranslator.NODE_COUNT_LIMIT){
			throw new UnsupportedElementException(segmentation);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		PMML pmml = getPMML();
		MiningModel miningModel = getModel();

		TreeModel treeModel = transformModel(miningModel);

		MathContext mathContext = treeModel.getMathContext();

		Targets targets = treeModel.getTargets();

		Target target = Iterables.getOnlyElement(targets);

		ModelTranslator<?> modelTranslator = new TreeModelTranslator(pmml, treeModel);

		Node root = treeModel.getNode();

		Map<String, FieldInfo> fieldInfos = modelTranslator.getFieldInfos(Collections.singleton(root));

		JMethod evaluateNodeMethod = createEvaluatorMethod(Value.class, root, true, context);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			JVar resultVar;

			switch(mathContext){
				case FLOAT:
					// Use a double accumulator (instead of a float one) for improved numerical stability
					resultVar = context.declare(double.class, "result", JExprUtil.directNoPara(toFloatString(floatAsDouble(0f)) + "D"));
					break;
				case DOUBLE:
					resultVar = context.declare(double.class, "result", JExpr.lit(0d));
					break;
				default:
					throw new UnsupportedAttributeException(miningModel, mathContext);
			}

			Scorer<Number> scorer = new Scorer<Number>(){

				@Override
				public Number prepare(Node node){
					Object score = node.getScore();

					return (Number)score;
				}

				@Override
				public void yield(Number score, TranslationContext context){
					JBlock block = context.block();

					if(score != null && score.doubleValue() != 0d){
						block.add(createCompoundAssignment(resultVar, score));
					}
				}

				@Override
				public void yieldIf(JExpression expression, Number score, TranslationContext context){
					JBlock block = context.block();

					if(score != null && score.doubleValue() != 0d){
						JBlock thenBlock = block._if(expression)._then();

						thenBlock.add(createCompoundAssignment(resultVar, score));
					}
				}

				private JStatement createCompoundAssignment(JVar resultVar, Number value){

					switch(mathContext){
						case FLOAT:
							{
								double floatAsDoubleValue = floatAsDouble(value);

								return new JCompoundAssignment(resultVar, JExprUtil.directNoPara(toFloatString(Math.abs(floatAsDoubleValue)) + "D"), floatAsDoubleValue >= 0d ? "+=" : "-=");
							}
						case DOUBLE:
							{
								double doubleValue = value.doubleValue();

								return new JCompoundAssignment(resultVar, JExpr.lit(Math.abs(doubleValue)), doubleValue >= 0d ? "+=" : "-=");
							}
						default:
							throw new UnsupportedAttributeException(miningModel, mathContext);
					}
				}
			};

			TreeModelTranslator.translateNode(treeModel, root, scorer, fieldInfos, context);

			JVarBuilder valueBuilder = new ValueBuilder(context)
				.declare("resultValue", context.getValueFactoryVariable().newValue(resultVar));

			Number intercept = target.getRescaleConstant();

			switch(mathContext){
				case FLOAT:
					{
						double floatAsDoubleValue = floatAsDouble(intercept);

						valueBuilder.update("add", JExprUtil.directNoPara(toFloatString(floatAsDoubleValue) + "D"));
					}
					break;
				case DOUBLE:
					{
						double doubleValue = intercept.doubleValue();

						valueBuilder.update("add", JExpr.lit(doubleValue));
					}
					break;
				default:
					throw new UnsupportedAttributeException(miningModel, mathContext);
			}

			JVar resultValueVar = valueBuilder.getVariable();

			context._return(resultValueVar);
		} finally {
			context.popScope();
		}

		return evaluateNodeMethod;
	}

	static
	private TreeModel transformModel(MiningModel miningModel){
		TreeModel treeModel = transformSegmentation(miningModel);

		Segmentation segmentation = miningModel.getSegmentation();

		List<Segment> segments = segmentation.getSegments();
		if(!segments.isEmpty()){
			segments.clear();
		}

		Segment segment = new Segment()
			.setPredicate(True.INSTANCE)
			.setModel(treeModel);

		LocalTransformations localTransformations = treeModel.getLocalTransformations();
		if(localTransformations != null){
			miningModel.setLocalTransformations(localTransformations);

			treeModel.setLocalTransformations(null);
		}

		segments.add(segment);

		return treeModel;
	}

	static
	private TreeModel transformSegmentation(MiningModel miningModel){
		MathContext mathContext = miningModel.getMathContext();

		Segmentation segmentation = miningModel.getSegmentation();

		Number zero;

		switch(mathContext){
			case FLOAT:
				zero = floatAsDouble(0f);
				break;
			case DOUBLE:
				zero = 0d;
				break;
			default:
				throw new UnsupportedAttributeException(miningModel, mathContext);
		}

		Node root = new ComplexNode()
			.setScore(zero)
			.setPredicate(True.INSTANCE);

		Target target = new Target()
			.setRescaleConstant(zero);

		Targets targets = new Targets()
			.addTargets(target);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, new MiningSchema(), root)
			.setMathContext(mathContext)
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setMissingValueStrategy(TreeModel.MissingValueStrategy.NONE)
			.setTargets(targets);

		Visitor nodeFilterer = new NodeFilterer() {

			@Override
			public ComplexNode filter(Node node){

				if(node instanceof ComplexNode){
					ComplexNode complexNode = (ComplexNode)node;

					return complexNode;
				}

				return new ComplexNode(node);
			}
		};
		nodeFilterer.applyTo(segmentation);

		Visitor nodeExtender = new AbstractVisitor(){

			@Override
			public VisitorAction visit(Node node){
				PMMLObject parent = getParent();

				NodeGroupUtil.setParentId(node, System.identityHashCode(parent));

				return super.visit(node);
			}
		};
		nodeExtender.applyTo(segmentation);

		Visitor nodeScoreUpdater = new AbstractVisitor(){

			@Override
			public VisitorAction visit(TreeModel treeModel){
				Node root = treeModel.getNode();

				Number score = (Number)root.getScore();

				target.setRescaleConstant(add(mathContext, target.getRescaleConstant(), score));

				updateNodeScores(root, score);

				return super.visit(treeModel);
			}

			private void updateNodeScores(Node node, Number adjustment){
				Number score = (Number)node.getScore();

				node.setScore(subtract(mathContext, score, adjustment));

				if(node.hasNodes()){
					List<Node> children = node.getNodes();

					for(Node child : children){
						updateNodeScores(child, adjustment);
					}
				}
			}
		};
		nodeScoreUpdater.applyTo(segmentation);

		Visitor treeModelInitializer = new AbstractVisitor(){

			{
				MiningSchema miningSchema = miningModel.getMiningSchema();
				LocalTransformations localTransformations = miningModel.getLocalTransformations();

				if(miningSchema != null && miningSchema.hasMiningFields()){
					addMiningFields(miningSchema.getMiningFields());
				} // End if

				if(localTransformations != null && localTransformations.hasDerivedFields()){
					addDerivedFields(localTransformations.getDerivedFields());
				}
			}

			@Override
			public VisitorAction visit(TreeModel treeModel){
				Node root = treeModel.getNode();

				Predicate predicate = root.getPredicate();
				if(!(predicate instanceof True)){
					throw new IllegalArgumentException();
				} // End if

				if(root.hasNodes()){
					List<Node> children = root.getNodes();

					for(Node child : children){
						addNode(child);
					}

					Number score = (Number)root.getScore();
					if(score.doubleValue() != 0d){
						Node elseChild = new ComplexNode()
							.setScore(score)
							.setPredicate(True.INSTANCE);

						NodeGroupUtil.setParentId(elseChild, System.identityHashCode(root));

						addNode(elseChild);
					}
				}

				return super.visit(treeModel);
			}

			@Override
			public VisitorAction visit(LocalTransformations localTransformations){

				if(localTransformations.hasDerivedFields()){
					addDerivedFields(localTransformations.getDerivedFields());
				}

				return super.visit(localTransformations);
			}

			private void addMiningFields(List<MiningField> miningFields){
				MiningSchema miningSchema = treeModel.getMiningSchema();

				miningFields:
				for(MiningField miningField : miningFields){
					MiningField.UsageType usageType = miningField.getUsageType();

					switch(usageType){
						case ACTIVE:
							break;
						default:
							continue miningFields;
					}

					miningSchema.addMiningFields(miningField);
				}
			}

			private void addDerivedFields(List<DerivedField> derivedFields){
				LocalTransformations localTransformations = treeModel.getLocalTransformations();

				if(localTransformations == null){
					localTransformations = new LocalTransformations();

					treeModel.setLocalTransformations(localTransformations);
				}

				for(DerivedField derivedField : derivedFields){
					localTransformations.addDerivedFields(derivedField);
				}
			}

			private void addNode(Node node){
				Node root = treeModel.getNode();

				root.addNodes(node);
			}
		};
		treeModelInitializer.applyTo(segmentation);

		Visitor nodeGroupMerger = new AbstractVisitor(){

			@Override
			public VisitorAction visit(Node node){

				children:
				if(node.hasNodes()){
					List<Node> children = node.getNodes();

					if(children.size() == 1){
						break children;
					}

					List<NodeGroup> nodeGroups = NodeGroupUtil.group(children);
					if(nodeGroups.size() > 1){
						Map<List<PredicateKey>, NodeGroup> uniqueNodeGroups = null;

						for(NodeGroup nodeGroup : nodeGroups){

							// XXX
							if(!nodeGroup.isShallow()){
								continue;
							}

							if(uniqueNodeGroups == null){
								uniqueNodeGroups = new HashMap<>();
							}

							List<PredicateKey> key = createKey(nodeGroup);

							NodeGroup prevNodeGroup = uniqueNodeGroups.get(key);
							if(prevNodeGroup != null){
								merge(prevNodeGroup, nodeGroup);

								children.removeAll(nodeGroup);
							} else

							{
								uniqueNodeGroups.put(key, nodeGroup);
							}
						}
					}
				}

				return super.visit(node);
			}

			private List<PredicateKey> createKey(List<Node> nodes){
				return nodes.stream()
					.map(node -> new PredicateKey(node.getPredicate()))
					.collect(Collectors.toList());
			}

			private void merge(List<Node> leftNodes, List<Node> rightNodes){

				for(int i = 0; i < leftNodes.size(); i++){
					Node leftNode = leftNodes.get(i);
					Node rightNode = rightNodes.get(i);

					// XXX
					if(leftNode.hasNodes() || rightNode.hasNodes()){
						throw new IllegalArgumentException();
					}

					leftNode.setScore(add(mathContext, (Number)leftNode.getScore(), (Number)rightNode.getScore()));
				}
			}
		};
		nodeGroupMerger.applyTo(treeModel);

		return treeModel;
	}

	static
	private Number add(MathContext mathContext, Number left, Number right){

		switch(mathContext){
			case FLOAT:
				return (floatAsDouble(left) + floatAsDouble(right));
			case DOUBLE:
				return (left.doubleValue() + right.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	private Number subtract(MathContext mathContext, Number left, Number right){

		switch(mathContext){
			case FLOAT:
				return (floatAsDouble(left) - floatAsDouble(right));
			case DOUBLE:
				return (left.doubleValue() - right.doubleValue());
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	private double floatAsDouble(Number value){

		if(value instanceof Float){
			Float floatValue = (Float)value;

			return Double.parseDouble(floatValue.toString());
		} else

		if(value instanceof Double){
			Double doubleValue = (Double)value;

			return doubleValue.doubleValue();
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	private String toFloatString(Number value){
		DecimalFormat formatter = TreeModelBoosterTranslator.FORMAT_DECIMAL32;

		synchronized(formatter){
			return formatter.format(value);
		}
	}

	/**
	 * @see java.math.MathContext#DECIMAL32
	 */
	private static final DecimalFormat FORMAT_DECIMAL32;

	static {
		// Add one extra decimal place
		String pattern = "0.#######" + "E0";

		DecimalFormatSymbols symbols = new DecimalFormatSymbols();
		symbols.setDecimalSeparator('.');
		symbols.setMinusSign('-');
		symbols.setExponentSeparator("E");

		FORMAT_DECIMAL32 = new DecimalFormat(pattern, symbols);
		FORMAT_DECIMAL32.setRoundingMode(RoundingMode.HALF_EVEN);
	}

	public static final int NODE_COUNT_LIMIT = Integer.getInteger(TreeModelBoosterTranslator.class.getName() + "#" + "NODE_COUNT_LIMIT", 1000);
}