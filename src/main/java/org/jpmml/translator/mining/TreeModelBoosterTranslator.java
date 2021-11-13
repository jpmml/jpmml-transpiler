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

import java.util.Collections;
import java.util.List;
import java.util.Map;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
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
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.TranslationContext;
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
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		PMML pmml = getPMML();
		MiningModel miningModel = getModel();

		TreeModel treeModel = transformModel(miningModel);

		MathContext mathContext = treeModel.getMathContext();

		ModelTranslator<?> modelTranslator = new TreeModelTranslator(pmml, treeModel);

		Node root = treeModel.getNode();

		Map<FieldName, FieldInfo> fieldInfos = modelTranslator.getFieldInfos(Collections.singleton(root));

		JMethod evaluateNodeMethod = createEvaluatorMethod(Value.class, root, true, context);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			JVar resultVar;

			switch(mathContext){
				case FLOAT:
					resultVar = context.declare(float.class, "result", JExpr.lit(0f));
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
						block.assignPlus(resultVar, formatScore(score));
					}
				}

				@Override
				public void yieldIf(JExpression expression, Number score, TranslationContext context){
					JBlock block = context.block();

					JBlock thenBlock = block._if(expression)._then();

					if(score != null && score.doubleValue() != 0d){
						thenBlock.assignPlus(resultVar, formatScore(score));
					}
				}

				private JExpression formatScore(Number score){
					return PMMLObjectUtil.createExpression(score, context);
				}
			};

			TreeModelTranslator.translateNode(treeModel, root, scorer, fieldInfos, context);

			context._return((context.getValueFactoryVariable()).newValue(resultVar));
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
		Segmentation segmentation = miningModel.getSegmentation();

		Node root = new ComplexNode()
			.setScore(0)
			.setPredicate(True.INSTANCE);

		TreeModel treeModel = new TreeModel(MiningFunction.REGRESSION, new MiningSchema(), root)
			.setMathContext(miningModel.getMathContext()) // XXX
			.setNoTrueChildStrategy(TreeModel.NoTrueChildStrategy.RETURN_LAST_PREDICTION)
			.setMissingValueStrategy(TreeModel.MissingValueStrategy.NONE);

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

				TreeModelTranslator.addParentExtension(node, System.identityHashCode(parent));

				return super.visit(node);
			}
		};
		nodeExtender.applyTo(segmentation);

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

						TreeModelTranslator.addParentExtension(elseChild, System.identityHashCode(root));

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

		return treeModel;
	}
}