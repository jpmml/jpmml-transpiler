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
package com.jpmml.translator.tree;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.FieldInfo;
import com.jpmml.translator.FpPrimitiveEncoder;
import com.jpmml.translator.IdentifierUtil;
import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.OperableRef;
import com.jpmml.translator.OrdinalEncoder;
import com.jpmml.translator.Scope;
import com.jpmml.translator.TranslationContext;
import com.jpmml.translator.ValueFactoryRef;
import com.jpmml.translator.ValueMapBuilder;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
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
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.PMMLAttributes;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.MissingAttributeException;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.ValueFactory;

public class TreeModelTranslator extends ModelTranslator<TreeModel> {

	public TreeModelTranslator(PMML pmml, TreeModel treeModel){
		super(pmml, treeModel);

		TreeModel.MissingValueStrategy missingValueStrategy = treeModel.getMissingValueStrategy();
		switch(missingValueStrategy){
			case NONE:
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
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		JDefinedClass owner = context.getOwner();

		NodeScoreManager scoreManager = new NodeScoreManager(owner, context.ref(Number.class), IdentifierUtil.create("scores", node)){

			{
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

			context._return(scoreManager.getComponent(createEvaluatorMethodInvocation(evaluateNodeMethod, context)));
		} finally {
			context.popScope();
		}

		return evaluateTreeModelMethod;
	}

	@Override
	public JMethod translateClassifier(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		String[] categories = getTargetCategories();

		JDefinedClass owner = context.getOwner();

		NodeScoreDistributionManager<?> scoreManager = new NodeScoreDistributionManager<Number>(owner, context.ref(Number[].class), IdentifierUtil.create("scores", node), categories){

			private ValueFactory<Number> valueFactory = ModelTranslator.getValueFactory(treeModel);


			{
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

			JVar scoreVar = context.declare(Number[].class, "score", scoreManager.getComponent(createEvaluatorMethodInvocation(evaluateNodeMethod, context)));

			JVar valueMapVar = createScoreDistribution(categories, scoreVar, context);

			context._return(JExpr._new(context.ref(ProbabilityDistribution.class)).arg(valueMapVar));
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
	public <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> void translateNode(TreeModel treeModel, Node node, ScoreManager scoreManager, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){
		S score = scoreManager.apply(node);
		Predicate predicate = node.getPredicate();

		JExpression predicateExpr = translatePredicate(predicate, fieldInfos, context);

		JBlock block = context.block();

		JBlock ifBlock = block._if(predicateExpr)._then();

		int scoreIndex;

		if(node.hasNodes()){
			context.pushScope(new Scope(ifBlock));

			try {
				List<Node> children = node.getNodes();

				for(Node child : children){
					translateNode(treeModel, child, scoreManager, fieldInfos, context);
				}
			} finally {
				context.popScope();
			}

			TreeModel.NoTrueChildStrategy noTrueChildStrategy = treeModel.getNoTrueChildStrategy();
			switch(noTrueChildStrategy){
				case RETURN_NULL_PREDICTION:
					scoreIndex = -1;
					break;
				case RETURN_LAST_PREDICTION:
					if(score == null){
						scoreIndex = -1;
					} else

					{
						scoreIndex = scoreManager.getOrInsert(score);
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

			scoreIndex = scoreManager.getOrInsert(score);
		}

		ifBlock._return(JExpr.lit(scoreIndex));
	}

	static
	public JExpression translatePredicate(Predicate predicate, Map<FieldName, FieldInfo> fieldInfos, TranslationContext context){

		if(predicate instanceof SimplePredicate){
			SimplePredicate simplePredicate = (SimplePredicate)predicate;

			FieldInfo fieldInfo = getFieldInfo(simplePredicate, fieldInfos);

			OperableRef operableRef = context.ensureOperableVariable(fieldInfo);

			SimplePredicate.Operator operator = simplePredicate.getOperator();
			switch(operator){
				case IS_MISSING:
					return operableRef.isMissing();
				case IS_NOT_MISSING:
					return operableRef.isNotMissing();
				default:
					break;
			}

			Object value = simplePredicate.getValue();

			JExpression comparisonExpr;

			switch(operator){
				case EQUAL:
					comparisonExpr = operableRef.equalTo(value, context);
					break;
				case NOT_EQUAL:
					comparisonExpr = operableRef.notEqualTo(value, context);
					break;
				case LESS_THAN:
					comparisonExpr = operableRef.lessThan(value, context);
					break;
				case LESS_OR_EQUAL:
					comparisonExpr = operableRef.lessOrEqual(value, context);
					break;
				case GREATER_OR_EQUAL:
					comparisonExpr = operableRef.greaterOrEqual(value, context);
					break;
				case GREATER_THAN:
					comparisonExpr = operableRef.greaterThan(value, context);
					break;
				default:
					throw new UnsupportedAttributeException(predicate, operator);
			}

			JType type = operableRef.type();
			if(type.isReference()){
				comparisonExpr = (operableRef.isNotMissing()).cand(comparisonExpr);
			}

			return comparisonExpr;
		} else

		if(predicate instanceof SimpleSetPredicate){
			SimpleSetPredicate simpleSetPredicate = (SimpleSetPredicate)predicate;

			FieldInfo fieldInfo = getFieldInfo(simpleSetPredicate, fieldInfos);

			OperableRef operableRef = context.ensureOperableVariable(fieldInfo);

			ComplexArray complexArray = (ComplexArray)simpleSetPredicate.getArray();

			Collection<?> values = complexArray.getValue();

			JExpression setExpr;

			SimpleSetPredicate.BooleanOperator booleanOperator = simpleSetPredicate.getBooleanOperator();
			switch(booleanOperator){
				case IS_IN:
					setExpr = operableRef.isIn(values, context);
					break;
				case IS_NOT_IN:
					setExpr = operableRef.isNotIn(values, context);
					break;
				default:
					throw new UnsupportedAttributeException(predicate, booleanOperator);
			}

			JType type = operableRef.type();
			if(type.isReference()){
				setExpr = (operableRef.isNotMissing()).cand(setExpr);
			}

			return setExpr;
		} else

		if(predicate instanceof True){
			return JExpr.TRUE;
		} else

		if(predicate instanceof False){
			return JExpr.FALSE;
		} else

		{
			throw new UnsupportedElementException(predicate);
		}
	}

	static
	public JVar createScoreDistribution(String[] categories, JVar scoreVar, TranslationContext context){
		ValueMapBuilder valueMapBuilder = new ValueMapBuilder(context)
			.construct("values");

		ValueFactoryRef valueFactoryRef = context.getValueFactoryVariable();

		for(int i = 0; i < categories.length; i++){
			JExpression valueExpr = valueFactoryRef.newValue(scoreVar.component(JExpr.lit(i)));

			valueMapBuilder.update("put", categories[i], valueExpr);
		}

		return valueMapBuilder.getVariable();
	}

	static
	public Map<FieldName, FieldInfo> enhanceFieldInfos(Set<? extends PMMLObject> bodyObjects, Map<FieldName, FieldInfo> fieldInfos){
		PrimaryFieldReferenceFinder primaryFieldReferenceFinder = new PrimaryFieldReferenceFinder();
		DiscreteValueFinder discreteValueFinder = new DiscreteValueFinder();

		for(PMMLObject bodyObject : bodyObjects){
			Node node = (Node)bodyObject;

			primaryFieldReferenceFinder.applyTo(node);
			discreteValueFinder.applyTo(node);
		}

		Set<FieldName> primaryFieldNames = primaryFieldReferenceFinder.getFieldNames();
		Map<FieldName, Set<Object>> discreteFieldValues = discreteValueFinder.getFieldValues();

		Collection<? extends Map.Entry<FieldName, FieldInfo>> entries = fieldInfos.entrySet();
		for(Map.Entry<FieldName, FieldInfo> entry : entries){
			FieldName name = entry.getKey();
			FieldInfo fieldInfo = entry.getValue();

			Field<?> field = fieldInfo.getField();

			OpType opType = field.getOpType();
			DataType dataType = field.getDataType();

			fieldInfo.setPrimary(primaryFieldNames.contains(name));

			switch(opType){
				case CONTINUOUS:
					{
						switch(dataType){
							case FLOAT:
							case DOUBLE:
								fieldInfo.setEncoder(new FpPrimitiveEncoder());
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
							fieldInfo.setEncoder(new OrdinalEncoder(values));
						}
					}
					break;
				default:
					break;
			}
		}

		return fieldInfos;
	}
}