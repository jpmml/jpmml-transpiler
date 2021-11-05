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
package org.jpmml.translator.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.ComplexArray;
import org.dmg.pmml.DataType;
import org.dmg.pmml.False;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.TextIndex;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.PMMLAttributes;
import org.dmg.pmml.tree.PMMLElements;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.MissingAttributeException;
import org.jpmml.evaluator.MissingElementException;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.translator.ArrayManager;
import org.jpmml.translator.Encoder;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.FpPrimitiveEncoder;
import org.jpmml.translator.FunctionInvocation;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.JBinaryFileInitializer;
import org.jpmml.translator.JVarBuilder;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.OperableRef;
import org.jpmml.translator.OrdinalEncoder;
import org.jpmml.translator.PMMLObjectUtil;
import org.jpmml.translator.Scope;
import org.jpmml.translator.TermFrequencyEncoder;
import org.jpmml.translator.TextIndexUtil;
import org.jpmml.translator.TranslationContext;
import org.jpmml.translator.ValueFactoryRef;
import org.jpmml.translator.ValueMapBuilder;

public class TreeModelTranslator extends ModelTranslator<TreeModel> {

	public TreeModelTranslator(PMML pmml, TreeModel treeModel){
		super(pmml, treeModel);

		TreeModel.MissingValueStrategy missingValueStrategy = treeModel.getMissingValueStrategy();
		switch(missingValueStrategy){
			case NONE:
			case NULL_PREDICTION:
				break;
			default:
				throw new UnsupportedAttributeException(treeModel, missingValueStrategy);
		}

		TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
		switch(noTrueChildStrategy){
			case RETURN_LAST_PREDICTION:
			case RETURN_NULL_PREDICTION:
				break;
			default:
				throw new UnsupportedAttributeException(treeModel, noTrueChildStrategy);
		}

		Node root = treeModel.getNode();
		if(root == null){
			throw new MissingElementException(treeModel, PMMLElements.TREEMODEL_NODE);
		}

		Predicate predicate = root.getPredicate();
		if(!(predicate instanceof True)){
			throw new UnsupportedElementException(predicate);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		JDefinedClass owner = context.getOwner();

		NodeScoreManager scoreManager = new NodeScoreManager(context.ref(Number.class), IdentifierUtil.create("scores", node)){

			{
				initArrayVar(owner);
				initArray();
			}
		};

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(node));

		JMethod evaluateNodeMethod = createEvaluatorMethod(int.class, node, false, context);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			translateNode(treeModel, node, scoreManager, fieldInfos, context);
		} finally {
			context.popScope();
		}

		JMethod evaluateTreeModelMethod = createEvaluatorMethod(Number.class, treeModel, false, context);

		try {
			context.pushScope(new MethodScope(evaluateTreeModelMethod));

			JVar indexVar = context.declare(int.class, "index", createEvaluatorMethodInvocation(evaluateNodeMethod, context));

			context._returnIf(indexVar.eq(TreeModelTranslator.NULL_RESULT), JExpr._null());

			context._return(scoreManager.getComponent(indexVar));
		} finally {
			context.popScope();
		}

		return evaluateTreeModelMethod;
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		Object[] categories = getTargetCategories();

		JDefinedClass owner = context.getOwner();

		NodeScoreDistributionManager<?> scoreManager = new NodeScoreDistributionManager<Number>(context.ref(Number[].class), IdentifierUtil.create("scores", node), categories){

			private ValueFactory<Number> valueFactory = ModelTranslator.getValueFactory(treeModel);


			{
				initArrayVar(owner);
				initArray();
			}

			@Override
			public ValueFactory<Number> getValueFactory(){
				return this.valueFactory;
			}
		};

		Map<FieldName, FieldInfo> fieldInfos = getFieldInfos(Collections.singleton(node));

		JMethod evaluateNodeMethod = createEvaluatorMethod(int.class, node, false, context);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			translateNode(treeModel, node, scoreManager, fieldInfos, context);
		} finally {
			context.popScope();
		}

		JMethod evaluateTreeModelMethod = createEvaluatorMethod(Classification.class, treeModel, true, context);

		try {
			context.pushScope(new MethodScope(evaluateTreeModelMethod));

			JVar indexVar = context.declare(int.class, "index", createEvaluatorMethodInvocation(evaluateNodeMethod, context));

			context._returnIf(indexVar.eq(TreeModelTranslator.NULL_RESULT), JExpr._null());

			JVar scoreVar = context.declare(Number[].class, "score", scoreManager.getComponent(indexVar));

			JVarBuilder valueMapBuilder = createScoreDistribution(categories, scoreVar, context);

			context._return(context._new(ProbabilityDistribution.class, valueMapBuilder));
		} finally {
			context.popScope();
		}

		return evaluateTreeModelMethod;
	}

	@Override
	public Map<FieldName, FieldInfo> getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		Map<FieldName, FieldInfo> fieldInfos = super.getFieldInfos(bodyObjects);

		fieldInfos = TreeModelTranslator.enhanceFieldInfos(bodyObjects, fieldInfos);

		return fieldInfos;
	}

	static
	public <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> void translateNode(TreeModel treeModel, Node root, ScoreManager scoreManager, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		S score = scoreManager.apply(root);
		Predicate predicate = root.getPredicate();

		if(!(predicate instanceof True)){
			throw new UnsupportedElementException(predicate);
		}

		translateNode(treeModel, null, root, scoreManager, fieldInfos, context);
	}

	static
	public <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> void translateNode(TreeModel treeModel, Node parentNode,  Node node, ScoreManager scoreManager, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		S score = scoreManager.apply(node);
		Predicate predicate = node.getPredicate();

		Scope nodeScope = translatePredicate(treeModel, predicate, fieldInfos, context);

		JExpression scoreExpr;

		if(node.hasNodes()){
			context.pushScope(nodeScope);

			try {
				List<Node> children = node.getNodes();

				children:
				for(Node child : children){
					Predicate childPredicate = child.getPredicate();

					if(childPredicate instanceof False){
						continue children;
					}

					translateNode(treeModel, node, child, scoreManager, fieldInfos, context);

					if(childPredicate instanceof True){
						return;
					}
				}
			} finally {
				context.popScope();
			}

			TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
			switch(noTrueChildStrategy){
				case RETURN_NULL_PREDICTION:
					scoreExpr = TreeModelTranslator.NULL_RESULT;
					break;
				case RETURN_LAST_PREDICTION:
					if(score == null){
						scoreExpr = TreeModelTranslator.NULL_RESULT;
					} else

					{
						int scoreIndex = scoreManager.getOrInsert(score);

						scoreExpr = JExpr.lit(scoreIndex);
					}
					break;
				default:
					throw new UnsupportedAttributeException(treeModel, noTrueChildStrategy);
			}
		} else

		{
			if(score == null){
				throw new MissingAttributeException(node, PMMLAttributes.COMPLEXNODE_SCORE);
			}

			int scoreIndex = scoreManager.getOrInsert(score);

			scoreExpr = JExpr.lit(scoreIndex);
		}

		JBlock nodeBlock = nodeScope.getBlock();

		nodeBlock._return(scoreExpr);
	}

	static
	public Scope translatePredicate(TreeModel treeModel, Predicate predicate, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		JBlock block = context.block();

		OperableRef operableRef;

		JExpression valueExpr;

		if(predicate instanceof SimplePredicate){
			SimplePredicate simplePredicate = (SimplePredicate)predicate;

			FieldInfo fieldInfo = getFieldInfo(simplePredicate, fieldInfos);

			operableRef = context.ensureOperableVariable(fieldInfo);

			SimplePredicate.Operator operator = simplePredicate.getOperator();
			switch(operator){
				case IS_MISSING:
					return createBranch(block, operableRef.isMissing());
				case IS_NOT_MISSING:
					return createBranch(block, operableRef.isNotMissing());
				default:
					break;
			}

			Object value = simplePredicate.getValue();

			switch(operator){
				case EQUAL:
					valueExpr = operableRef.equalTo(value, context);
					break;
				case NOT_EQUAL:
					valueExpr = operableRef.notEqualTo(value, context);
					break;
				case LESS_THAN:
					valueExpr = operableRef.lessThan(value, context);
					break;
				case LESS_OR_EQUAL:
					valueExpr = operableRef.lessOrEqual(value, context);
					break;
				case GREATER_OR_EQUAL:
					valueExpr = operableRef.greaterOrEqual(value, context);
					break;
				case GREATER_THAN:
					valueExpr = operableRef.greaterThan(value, context);
					break;
				default:
					throw new UnsupportedAttributeException(predicate, operator);
			}
		} else

		if(predicate instanceof SimpleSetPredicate){
			SimpleSetPredicate simpleSetPredicate = (SimpleSetPredicate)predicate;

			FieldInfo fieldInfo = getFieldInfo(simpleSetPredicate, fieldInfos);

			operableRef = context.ensureOperableVariable(fieldInfo);

			ComplexArray complexArray = (ComplexArray)simpleSetPredicate.getArray();

			Collection<?> values = complexArray.getValue();

			SimpleSetPredicate.BooleanOperator booleanOperator = simpleSetPredicate.getBooleanOperator();
			switch(booleanOperator){
				case IS_IN:
					valueExpr = operableRef.isIn(values, context);
					break;
				case IS_NOT_IN:
					valueExpr = operableRef.isNotIn(values, context);
					break;
				default:
					throw new UnsupportedAttributeException(predicate, booleanOperator);
			}
		} else

		if(predicate instanceof True){
			return createBranch(block, JExpr.TRUE);
		} else

		if(predicate instanceof False){
			return createBranch(block, JExpr.FALSE);
		} else

		{
			throw new UnsupportedElementException(predicate);
		}

		JVar variable = (JVar)operableRef.getExpression();

		TreeModel.MissingValueStrategy missingValueStrategy = treeModel.getMissingValueStrategy();
		switch(missingValueStrategy){
			case NONE:
				{
					boolean isNonMissing = context.isNonMissing(variable);

					if(!isNonMissing){
						JType type = operableRef.type();

						if(type.isReference()){
							valueExpr = (operableRef.isNotMissing()).cand(valueExpr);
						}
					}

					Scope result = createBranch(block, valueExpr);

					if(!isNonMissing){
						// The mark applies to children only
						result.markNonMissing(variable);
					}

					return result;
				}
			case NULL_PREDICTION:
				{
					if(!context.isNonMissing(variable)){
						context._returnIf(operableRef.isMissing(), TreeModelTranslator.NULL_RESULT);

						// The mark applies to (subsequent-) siblings and children alike
						context.markNonMissing(variable);
					}

					return createBranch(block, valueExpr);
				}
			default:
				throw new UnsupportedAttributeException(treeModel, missingValueStrategy);
		}
	}

	static
	public Map<FieldName, FieldInfo> enhanceFieldInfos(Set<? extends PMMLObject> bodyObjects, Map<FieldName, FieldInfo> fieldInfos){
		CountingActiveFieldFinder countingActiveFieldFinder = new CountingActiveFieldFinder();
		DiscreteValueFinder discreteValueFinder = new DiscreteValueFinder();

		for(PMMLObject bodyObject : bodyObjects){
			Node node = (Node)bodyObject;

			countingActiveFieldFinder.applyTo(node);
			discreteValueFinder.applyTo(node);
		}

		Map<FieldName, Set<Object>> discreteFieldValues = discreteValueFinder.getFieldValues();

		ListMultimap<FieldName, List<String>> tfTokens = ArrayListMultimap.create();

		Collection<? extends Map.Entry<FieldName, FieldInfo>> entries = fieldInfos.entrySet();
		for(Map.Entry<FieldName, FieldInfo> entry : entries){
			FieldName name = entry.getKey();
			FieldInfo fieldInfo = entry.getValue();

			Field<?> field = fieldInfo.getField();

			OpType opType = field.getOpType();
			DataType dataType = field.getDataType();

			fieldInfo.updateCount(countingActiveFieldFinder.getCount(name));

			switch(opType){
				case CONTINUOUS:
					{
						switch(dataType){
							// XXX
							case INTEGER:
								// Falls through
							case FLOAT:
							case DOUBLE:
								Encoder encoder = FpPrimitiveEncoder.create(fieldInfo);

								if(encoder instanceof TermFrequencyEncoder){
									TermFrequencyEncoder termFrequencyEncoder = (TermFrequencyEncoder)encoder;

									FunctionInvocation.Tf tf = termFrequencyEncoder.getTf(fieldInfo);

									List<List<String>> tokens = tfTokens.get(tf.getTextField());

									int index = tokens.indexOf(tf.getTermTokens());
									if(index < 0){
										index = tokens.size();

										tokens.add(tf.getTermTokens());
									}

									termFrequencyEncoder.setIndex(index);
								} else

								{
									// XXX
									if((DataType.INTEGER).equals(dataType)){
										encoder = null;
									}
								}

								fieldInfo.setEncoder(encoder);
								break;
							default:
								break;
						}
					}
					break;
				case CATEGORICAL:
					{
						Set<?> values = discreteFieldValues.get(name);
						if(values != null && values.size() > 0){
							Encoder encoder = OrdinalEncoder.create(fieldInfo, values);

							fieldInfo.setEncoder(encoder);
						}
					}
					break;
				default:
					break;
			}
		}

		for(Map.Entry<FieldName, FieldInfo> entry : entries){
			FieldName name = entry.getKey();
			FieldInfo fieldInfo = entry.getValue();

			Encoder encoder = fieldInfo.getEncoder();

			if(encoder instanceof TermFrequencyEncoder){
				TermFrequencyEncoder termFrequencyEncoder = (TermFrequencyEncoder)encoder;

				FunctionInvocation.Tf tf = termFrequencyEncoder.getTf(fieldInfo);

				termFrequencyEncoder.setVocabulary(tfTokens.get(tf.getTextField()));
			}
		}

		return fieldInfos;
	}

	static
	public void ensureTextIndexFields(FieldInfo fieldInfo, TermFrequencyEncoder encoder, TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		FunctionInvocation.Tf tf = encoder.getTf(fieldInfo);

		TextIndex textIndex = tf.getTextIndex();
		FieldName name = tf.getTextField();

		String textIndexName = IdentifierUtil.create("textIndex", textIndex, name);

		JFieldVar textIndexVar = (owner.fields()).get(textIndexName);
		if(textIndexVar == null){
			JBinaryFileInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(TextIndex.class.getSimpleName(), textIndex) + ".data", context);

			TextIndex localTextIndex = TextIndexUtil.toLocalTextIndex(textIndex, name);

			textIndexVar = owner.field(ModelTranslator.MEMBER_PRIVATE, context.ref(TextIndex.class), textIndexName, PMMLObjectUtil.createObject(localTextIndex, context));

			List<String>[] terms = (encoder.getVocabulary()).stream()
				.toArray(List[]::new);

			JFieldVar termsVar = resourceInitializer.initStringLists(IdentifierUtil.create("terms", textIndex, name), terms);
		}
	}

	static
	private ValueMapBuilder createScoreDistribution(Object[] categories, JVar scoreVar, TranslationContext context){
		ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
			.construct("values");

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		for(int i = 0; i < categories.length; i++){
			JExpression valueExpr = valueFactoryRef.newValue(scoreVar.component(JExpr.lit(i)));

			valueMapBuilder.update("put", categories[i], valueExpr);
		}

		return valueMapBuilder;
	}

	static
	private Scope createBranch(JBlock block, JExpression testExpr){
		JBlock thenBlock = block._if(testExpr)._then();

		return new Scope(thenBlock);
	}

	public static final JExpression NULL_RESULT = JExpr.lit(-1);
}