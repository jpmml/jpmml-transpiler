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
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.PMMLObjectUtil;
import com.jpmml.translator.Scope;
import com.jpmml.translator.TranslationContext;
import com.jpmml.translator.ValueMapBuilder;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JVar;
import org.dmg.pmml.ComplexArray;
import org.dmg.pmml.DataType;
import org.dmg.pmml.False;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.ProbabilityDistribution;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.ValueFactory;

public class TreeModelTranslator extends ModelTranslator<TreeModel> {

	public TreeModelTranslator(PMML pmml, TreeModel treeModel){
		super(pmml, treeModel);
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		NodeScoreManager scoreManager = new NodeScoreManager("scores$" + System.identityHashCode(node), context);

		Map<FieldName, Field<?>> activeFields = getActiveFields(Collections.singleton(node));

		JMethod evaluateNodeMethod = context.evaluatorMethod(JMod.PUBLIC, int.class, node, false, true);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			translateNode(node, scoreManager, activeFields, context);
		} finally {
			context.popScope();
		}

		JMethod evaluateTreeModelMethod = context.evaluatorMethod(JMod.PUBLIC, Number.class, treeModel, false, true);

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

		NodeScoreDistributionManager<?> scoreManager = new NodeScoreDistributionManager<Number>("scores$" + System.identityHashCode(node), categories, context){

			private ValueFactory<Number> valueFactory = ModelTranslator.getValueFactory(treeModel);


			@Override
			public ValueFactory<Number> getValueFactory(){
				return this.valueFactory;
			}
		};

		Map<FieldName, Field<?>> activeFields = getActiveFields(Collections.singleton(node));

		JMethod evaluateNodeMethod = context.evaluatorMethod(JMod.PUBLIC, int.class, node, false, true);

		try {
			context.pushScope(new MethodScope(evaluateNodeMethod));

			translateNode(node, scoreManager, activeFields, context);
		} finally {
			context.popScope();
		}

		JMethod evaluateTreeModelMethod = context.evaluatorMethod(JMod.PUBLIC, Classification.class, treeModel, true, true);

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

	static
	public <S, ScoreManager extends ArrayManager<S> & ScoreFunction<S>> void translateNode(Node node, ScoreManager scoreManager, Map<FieldName, Field<?>> activeFields, TranslationContext context){
		Predicate predicate = node.getPredicate();

		JExpression predicateExpr = translatePredicate(predicate, activeFields, context);

		JBlock block = context.block();

		JBlock ifBlock = block._if(predicateExpr)._then();

		if(node.hasNodes()){
			context.pushScope(new Scope(ifBlock));

			try {
				List<Node> children = node.getNodes();

				for(Node child : children){
					translateNode(child, scoreManager, activeFields, context);
				}
			} finally {
				context.popScope();
			}
		}

		S score = scoreManager.apply(node);
		if(score == null){
			return;
		}

		int scoreIndex = scoreManager.getOrInsert(score);

		ifBlock._return(JExpr.lit(scoreIndex));
	}

	static
	public JExpression translatePredicate(Predicate predicate, Map<FieldName, Field<?>> activeFields, TranslationContext context){

		if(predicate instanceof SimplePredicate){
			SimplePredicate simplePredicate = (SimplePredicate)predicate;

			Field<?> field = getField(simplePredicate, activeFields);

			JVar fieldValueVar = context.ensureFieldValueVariable(field);

			SimplePredicate.Operator operator = simplePredicate.getOperator();
			switch(operator){
				case IS_MISSING:
					return fieldValueVar.eq(JExpr._null());
				case IS_NOT_MISSING:
					return fieldValueVar.ne(JExpr._null());
				default:
					break;
			}

			JVar valueVar = context.ensureValueVariable(field, null);

			Object value = simplePredicate.getValue();

			JExpression valueLitExpr = PMMLObjectUtil.createExpression(value, context);

			switch(operator){
				case EQUAL:
					return translateEqualsCheck(field, valueVar, valueLitExpr);
				case NOT_EQUAL:
					return translateNotEqualCheck(field, valueVar, valueLitExpr);
				case LESS_THAN:
					return valueVar.lt(valueLitExpr);
				case LESS_OR_EQUAL:
					return valueVar.lte(valueLitExpr);
				case GREATER_OR_EQUAL:
					return valueVar.gte(valueLitExpr);
				case GREATER_THAN:
					return valueVar.gt(valueLitExpr);
				default:
					throw new UnsupportedAttributeException(predicate, operator);
			}
		} else

		if(predicate instanceof SimpleSetPredicate){
			SimpleSetPredicate simpleSetPredicate = (SimpleSetPredicate)predicate;

			Field<?> field = getField(simpleSetPredicate, activeFields);

			JVar valueVar = context.ensureValueVariable(field, null);

			ComplexArray complexArray = (ComplexArray)simpleSetPredicate.getArray();

			Collection<?> values = complexArray.getValue();

			Iterator<?> valueIt = values.iterator();

			JExpression predicateExpr = null;

			SimpleSetPredicate.BooleanOperator booleanOperator = simpleSetPredicate.getBooleanOperator();
			switch(booleanOperator){
				case IS_IN:
					do {
						Object value = valueIt.next();

						JExpression valueLitExpr = PMMLObjectUtil.createExpression(value, context);

						JExpression checkExpr = translateEqualsCheck(field, valueVar, valueLitExpr);

						predicateExpr = (predicateExpr != null ? predicateExpr.cor(checkExpr) : checkExpr);
					} while(valueIt.hasNext());
					break;
				case IS_NOT_IN:
					do {
						Object value = valueIt.next();

						JExpression valueLitExpr = PMMLObjectUtil.createExpression(value, context);

						JExpression checkNotExpr = translateNotEqualCheck(field, valueVar, valueLitExpr);

						predicateExpr = (predicateExpr != null ? predicateExpr.cand(checkNotExpr) : checkNotExpr);
					} while(valueIt.hasNext());
					break;
				default:
					throw new UnsupportedAttributeException(predicate, booleanOperator);
			}

			return predicateExpr;
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

		for(int i = 0; i < categories.length; i++){
			JExpression valueExpr = context.getValueFactoryVariable().invoke("newValue").arg(scoreVar.component(JExpr.lit(i)));

			valueMapBuilder.update("put", categories[i], valueExpr);
		}

		return valueMapBuilder.getVariable();
	}

	static
	private JExpression translateEqualsCheck(Field<?> field, JVar valueVar, JExpression valueLitExpr){
		DataType dataType = field.getDataType();

		switch(dataType){
			case STRING:
				return valueLitExpr.invoke("equals").arg(valueVar);
			case INTEGER:
			case FLOAT:
			case DOUBLE:
			case BOOLEAN:
				return valueVar.eq(valueLitExpr);
			default:
				throw new UnsupportedAttributeException(field, dataType);
		}
	}

	static
	private JExpression translateNotEqualCheck(Field<?> field, JVar valueVar, JExpression valueLitExpr){
		DataType dataType = field.getDataType();

		switch(dataType){
			case STRING:
				return (valueLitExpr.invoke("equals").arg(valueVar)).not();
			case INTEGER:
			case FLOAT:
			case DOUBLE:
			case BOOLEAN:
				return valueVar.ne(valueLitExpr);
			default:
				throw new UnsupportedAttributeException(field, dataType);
		}
	}
}