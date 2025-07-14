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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.google.common.base.Functions;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Streams;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.ComplexArray;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.False;
import org.dmg.pmml.Field;
import org.dmg.pmml.HasFieldReference;
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
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.TokenizedString;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.MissingAttributeException;
import org.jpmml.model.UnsupportedAttributeException;
import org.jpmml.model.UnsupportedElementException;
import org.jpmml.translator.ArrayFpPrimitiveEncoder;
import org.jpmml.translator.ArrayInfo;
import org.jpmml.translator.ArrayInfoMap;
import org.jpmml.translator.Encoder;
import org.jpmml.translator.FieldInfo;
import org.jpmml.translator.FieldInfoMap;
import org.jpmml.translator.FpPrimitiveEncoder;
import org.jpmml.translator.FunctionInvocation;
import org.jpmml.translator.IdentifierUtil;
import org.jpmml.translator.JBinaryFileInitializer;
import org.jpmml.translator.JIfStatement;
import org.jpmml.translator.JResourceInitializer;
import org.jpmml.translator.JVarBuilder;
import org.jpmml.translator.MethodScope;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.Modifiers;
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

		Node root = treeModel.requireNode();

		@SuppressWarnings("unused")
		True _true = root.requirePredicate(True.class);
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

		FieldInfoMap fieldInfos = getFieldInfos(Collections.singleton(node));

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

			context._returnIf(indexVar.eq(NodeScoreManager.RESULT_MISSING), JExpr._null());

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

		NodeScoreDistributionManager<?> scoreDistributionManager = new NodeScoreDistributionManager<Number>(context.ref(Number[].class), IdentifierUtil.create("scores", node), categories){

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

		FieldInfoMap fieldInfos = getFieldInfos(Collections.singleton(node));

		JMethod evaluateNodeMethod = createEvaluatorMethod(int.class, node, false, context);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			translateNode(treeModel, node, scoreDistributionManager, fieldInfos, context);
		} finally {
			context.popScope();
		}

		JMethod evaluateTreeModelMethod = createEvaluatorMethod(Classification.class, treeModel, true, context);

		try {
			context.pushScope(new MethodScope(evaluateTreeModelMethod));

			JVar indexVar = context.declare(int.class, "index", createEvaluatorMethodInvocation(evaluateNodeMethod, context));

			context._returnIf(indexVar.eq(NodeScoreDistributionManager.RESULT_MISSING), JExpr._null());

			JVar scoreVar = context.declare(Number[].class, "score", scoreDistributionManager.getComponent(indexVar));

			JVarBuilder valueMapBuilder = createScoreDistribution(categories, scoreVar, context);

			context._return(context._new(ProbabilityDistribution.class, valueMapBuilder));
		} finally {
			context.popScope();
		}

		return evaluateTreeModelMethod;
	}

	@Override
	public FieldInfoMap getFieldInfos(Set<? extends PMMLObject> bodyObjects){
		FieldInfoMap fieldInfos = super.getFieldInfos(bodyObjects);

		ArrayInfoMap arrayInfos = getArrayInfos();
		if(!arrayInfos.isEmpty()){
			declareArrayFields(arrayInfos.values());
		}

		fieldInfos = TreeModelTranslator.enhanceFieldInfos(bodyObjects, fieldInfos, arrayInfos);

		return fieldInfos;
	}

	static
	public <S> void translateNode(TreeModel treeModel, Node root, Scorer<S> scorer, FieldInfoMap fieldInfos, TranslationContext context){
		@SuppressWarnings("unused")
		True _true = root.requirePredicate(True.class);

		JBlock block = context.block();

		JIfStatement ifStatement = new JIfStatement(JExpr.TRUE);

		block.add(ifStatement);

		try {
			context.pushScope(new NodeScope(ifStatement));

			translateNode(treeModel, root, collectDependentNodes(root, Collections.emptyList()), null, scorer, fieldInfos, context);
		} finally {
			context.popScope();
		}
	}

	static
	public <S> void translateNode(TreeModel treeModel, Node node, List<Node> dependentNodes, Set<String> declarableNames, Scorer<S> scorer, FieldInfoMap fieldInfos, TranslationContext context){
		S score = scorer.prepare(node);

		NodeScope nodeScope = translatePredicate(treeModel, node, dependentNodes, scorer, fieldInfos, context);

		try {
			context.pushScope(nodeScope);

			if(node.hasNodes()){
				List<Node> children = node.getNodes();

				List<NodeGroup> nodeGroups = NodeGroupUtil.group(children);

				if(declarableNames == null){

					if(nodeGroups.size() > 0){
						Map<String, Long> leadingNameCounts = nodeGroups.stream()
							.map(nodeGroup -> {
								Predicate predicate = nodeGroup.getPredicate(0);

								if(predicate instanceof HasFieldReference){
									HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

									return hasFieldReference.requireField();
								}

								return null;
							})
							.filter(Objects::nonNull)
							.collect(Collectors.groupingBy(Functions.identity(), Collectors.counting()));

						declarableNames = (leadingNameCounts.entrySet()).stream()
							.filter(entry -> (entry.getValue() > 1L))
							.map(entry -> entry.getKey())
							.collect(Collectors.toCollection(LinkedHashSet::new));
					} else

					{
						declarableNames = Collections.emptySet();
					}
				}

				JIfStatement firstIfStatement = null;

				int offset = 0;

				for(int i = 0; i < nodeGroups.size(); i++){
					NodeGroup nodeGroup = nodeGroups.get(i);

					translateNodeGroup(treeModel, nodeGroup, nodeGroups, declarableNames, scorer, fieldInfos, context);

					JIfStatement ifStatement = (JIfStatement)nodeScope.chainContent(offset);

					if(i == 0){
						firstIfStatement = ifStatement;
					} // End if

					if(i < (nodeGroups.size() - 1)){
						context._comment("break");
					}

					offset = nodeScope.reInit();
				}

				TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
				switch(noTrueChildStrategy){
					case RETURN_NULL_PREDICTION:
						if(score != null){
							score = null;
						}
						break;
					case RETURN_LAST_PREDICTION:
						break;
					default:
						throw new UnsupportedAttributeException(treeModel, noTrueChildStrategy);
				}

				if(firstIfStatement == null || firstIfStatement.hasElse()){
					throw new IllegalStateException();
				}

				context.pushScope(new Scope(firstIfStatement._else()));

				try {
					scorer.yield(score, context);
				} finally {
					context.popScope();
				}
			} else

			{
				if(score == null){
					throw new MissingAttributeException(node, PMMLAttributes.COMPLEXNODE_SCORE);
				}

				scorer.yield(score, context);
			}
		} finally {
			context.popScope();
		}
	}

	static
	private <S> void translateNodeGroup(TreeModel treeModel, NodeGroup nodeGroup, List<NodeGroup> nodeGroups, Set<String> declarableNames, Scorer<S> scorer, FieldInfoMap fieldInfos, TranslationContext context){
		List<Node> nodes = nodeGroup;

		if(!declarableNames.isEmpty()){
			Iterator<String> nameIt = declarableNames.iterator();

			while(nameIt.hasNext()){
				String name = nameIt.next();

				if(usesField(nodes, name)){
					FieldInfo fieldInfo = fieldInfos.require(name);

					context.ensureOperable(fieldInfo, (method) -> true);

					nameIt.remove();
				}
			}
		}

		for(int i = 0; i < nodes.size(); i++){
			Node node = nodes.get(i);

			List<Node> dependentNodes = collectDependentNodes(node, nodes.subList(i + 1, nodes.size()));

			if(i == 0){
				dependentNodes = new ArrayList<>(dependentNodes);

				int index = nodeGroups.indexOf(nodeGroup);
				if(index < 0){
					throw new IllegalArgumentException();
				}

				for(int j = (index + 1); j < nodeGroups.size(); j++){
					NodeGroup siblingNodeGroup = nodeGroups.get(j);

					dependentNodes.addAll(siblingNodeGroup);
				}
			}

			translateNode(treeModel, node, dependentNodes, declarableNames, scorer, fieldInfos, context);
		}
	}

	static
	public <S> NodeScope translatePredicate(TreeModel treeModel, Node node, List<Node> dependentNodes, Scorer<S> scorer, FieldInfoMap fieldInfos, TranslationContext context){
		Predicate predicate = node.requirePredicate();

		OperableRef operableRef;

		JExpression valueExpr;

		if(predicate instanceof SimplePredicate){
			SimplePredicate simplePredicate = (SimplePredicate)predicate;

			operableRef = ensureOperable(simplePredicate, dependentNodes, fieldInfos, context);

			SimplePredicate.Operator operator = simplePredicate.requireOperator();
			switch(operator){
				case IS_MISSING:
					return createBranch(operableRef.isMissing(), context);
				case IS_NOT_MISSING:
					return createBranch(operableRef.isNotMissing(), context);
				default:
					break;
			}

			Object value = simplePredicate.requireValue();

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

			operableRef = ensureOperable(simpleSetPredicate, dependentNodes, fieldInfos, context);

			ComplexArray complexArray = (ComplexArray)simpleSetPredicate.requireArray();

			Collection<?> values = complexArray.getValue();

			SimpleSetPredicate.BooleanOperator booleanOperator = simpleSetPredicate.requireBooleanOperator();
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
			return createBranch(JExpr.TRUE, context);
		} else

		if(predicate instanceof False){
			return createBranch(JExpr.FALSE, context);
		} else

		{
			throw new UnsupportedElementException(predicate);
		}

		boolean isNonMissing = context.isNonMissing(operableRef);

		TreeModel.MissingValueStrategy missingValueStrategy = treeModel.getMissingValueStrategy();
		switch(missingValueStrategy){
			case NONE:
				{
					if(!isNonMissing){

						if(operableRef.requiresNotMissingCheck()){
							valueExpr = (operableRef.isNotMissing()).cand(valueExpr);
						}
					}

					NodeScope result = createBranch(valueExpr, context);

					if(!isNonMissing){
						// The mark applies to children only
						result.markNonMissing(operableRef);
					}

					return result;
				}
			case NULL_PREDICTION:
				{
					if(!isNonMissing){
						scorer.yieldIf(operableRef.isMissing(), null, context);

						// The mark applies to (subsequent-) siblings and children alike
						context.markNonMissing(operableRef);
					}

					return createBranch(valueExpr, context);
				}
			default:
				throw new UnsupportedAttributeException(treeModel, missingValueStrategy);
		}
	}

	static
	public FieldInfoMap enhanceFieldInfos(Set<? extends PMMLObject> bodyObjects, FieldInfoMap fieldInfos, ArrayInfoMap arrayInfos){
		CountingActiveFieldFinder countingActiveFieldFinder = new CountingActiveFieldFinder();
		DiscreteValueFinder discreteValueFinder = new DiscreteValueFinder();

		for(PMMLObject bodyObject : bodyObjects){
			Node node = (Node)bodyObject;

			countingActiveFieldFinder.applyTo(node);
			discreteValueFinder.applyTo(node);
		}

		Map<String, Set<Object>> discreteFieldValues = discreteValueFinder.getFieldValues();

		Map<ArrayInfo, List<DataField>> arrayInfoElements = new HashMap<>();

		Map<Field<?>, ArrayInfo> fieldArrayInfos = new HashMap<>();

		(arrayInfos.values()).stream()
			.forEach((arrayInfo) -> {
				List<Integer> indices = arrayInfo.getIndices();
				Map<Integer, DataField> dataFields = arrayInfo.getDataFields();

				int min = Collections.min(indices);
				int max = Collections.max(indices);

				List<DataField> elements = new ArrayList<>(max + 1);
				for(int i = 0; i <= max; i++){
					elements.add(dataFields.get(i));
				}

				arrayInfoElements.put(arrayInfo, elements);

				(dataFields.values()).stream()
					.forEach((dataField) -> fieldArrayInfos.put(dataField, arrayInfo));
			});

		ListMultimap<String, TokenizedString> tfTokens = ArrayListMultimap.create();

		Collection<? extends Map.Entry<String, FieldInfo>> entries = fieldInfos.entrySet();
		for(Map.Entry<String, FieldInfo> entry : entries){
			String name = entry.getKey();
			FieldInfo fieldInfo = entry.getValue();

			Field<?> field = fieldInfo.getField();

			DataType dataType = field.requireDataType();
			OpType opType = field.requireOpType();

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
								Encoder encoder = FpPrimitiveEncoder.create(fieldInfo, fieldArrayInfos);

								if(encoder instanceof ArrayFpPrimitiveEncoder){
									ArrayFpPrimitiveEncoder arrayFpPrimitiveEncoder = (ArrayFpPrimitiveEncoder)encoder;

									ArrayInfo arrayInfo = arrayFpPrimitiveEncoder.getArrayInfo();

									List<DataField> elements = arrayInfoElements.get(arrayInfo);

									arrayFpPrimitiveEncoder
										.setElements(elements);
								} else

								if(encoder instanceof TermFrequencyEncoder){
									TermFrequencyEncoder termFrequencyEncoder = (TermFrequencyEncoder)encoder;

									FunctionInvocation.Tf tf = termFrequencyEncoder.getTf(fieldInfo);

									List<TokenizedString> tokens = tfTokens.get(tf.getTextField());

									int index = tokens.indexOf(tf.getTermTokens());
									if(index < 0){
										index = tokens.size();

										tokens.add(tf.getTermTokens());
									}

									termFrequencyEncoder
										.setIndex(index)
										.setVocabulary(tokens);
								} else

								{
									// XXX
									if(dataType == DataType.INTEGER){
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
						if(values != null && !values.isEmpty()){
							Encoder encoder = OrdinalEncoder.create(fieldInfo, values);

							fieldInfo.setEncoder(encoder);
						}
					}
					break;
				default:
					break;
			}
		}

		return fieldInfos;
	}

	static
	public void ensureTextIndexFields(FieldInfo fieldInfo, TermFrequencyEncoder encoder, TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		FunctionInvocation.Tf tf = encoder.getTf(fieldInfo);

		TextIndex textIndex = tf.getTextIndex();
		String name = tf.getTextField();

		String textIndexName = IdentifierUtil.create("textIndex", textIndex, name);

		JFieldVar textIndexVar = (owner.fields()).get(textIndexName);
		if(textIndexVar == null){
			JResourceInitializer resourceInitializer = new JBinaryFileInitializer(IdentifierUtil.create(TextIndex.class.getSimpleName(), textIndex) + ".data", context);

			TextIndex localTextIndex = TextIndexUtil.toLocalTextIndex(textIndex, name);

			textIndexVar = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.ref(TextIndex.class), textIndexName, PMMLObjectUtil.createObject(localTextIndex, context));

			TokenizedString[] terms = (encoder.getVocabulary()).stream()
				.toArray(TokenizedString[]::new);

			JFieldVar termsVar = resourceInitializer.initTokenizedStringLists(IdentifierUtil.create("terms", textIndex, name), terms);
		}
	}

	static
	private OperableRef ensureOperable(HasFieldReference<?> hasFieldReference, List<Node> dependentNodes, FieldInfoMap fieldInfos, TranslationContext context){
		FieldInfo fieldInfo = fieldInfos.require(hasFieldReference);

		Function<JMethod, Boolean> function = new Function<JMethod, Boolean>(){

			@Override
			public Boolean apply(JMethod method){
				JType type = method.type();

				if(type.isReference()){
					return true;
				}

				return usesField(dependentNodes, hasFieldReference.requireField());
			}
		};

		return context.ensureOperable(fieldInfo, function);
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
	private NodeScope createBranch(JExpression testExpr, TranslationContext context){
		NodeScope scope = (NodeScope)context.ensureOpenScope();

		JBlock block = scope.getBlock();

		JIfStatement ifStatement = new JIfStatement(testExpr);

		block.add(ifStatement);

		return new NodeScope(ifStatement);
	}

	static
	private List<Node> collectDependentNodes(Node node, List<Node> siblings){

		if(node.hasNodes()){
			List<Node> children = node.getNodes();

			if(!siblings.isEmpty()){
				return Streams.concat(children.stream(), siblings.stream())
					.collect(Collectors.toList());
			}

			return children;
		}

		return siblings;
	}

	static
	private boolean usesField(Collection<Node> nodes, String fieldName){

		for(Node node : nodes){

			if(usesField(node, fieldName)){
				return true;
			}
		}

		return false;
	}

	static
	private boolean usesField(Node node, String fieldName){
		Predicate predicate = node.requirePredicate();

		if(predicate instanceof HasFieldReference){
			HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

			if(Objects.equals(hasFieldReference.requireField(), fieldName)){
				return true;
			}
		} // End if

		if(node.hasNodes()){
			return usesField(node.getNodes(), fieldName);
		}

		return false;
	}
}