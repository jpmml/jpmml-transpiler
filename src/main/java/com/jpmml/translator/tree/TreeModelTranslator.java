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

import com.jpmml.translator.MethodScope;
import com.jpmml.translator.ModelTranslator;
import com.jpmml.translator.PMMLObjectUtil;
import com.jpmml.translator.Scope;
import com.jpmml.translator.TranslationContext;
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
import org.dmg.pmml.MathContext;
import org.dmg.pmml.MiningFunction;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.True;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.UnsupportedAttributeException;
import org.jpmml.evaluator.UnsupportedElementException;

public class TreeModelTranslator extends ModelTranslator<TreeModel> {

	public TreeModelTranslator(PMML pmml, TreeModel treeModel){
		super(pmml, treeModel);

		MiningFunction miningFunction = treeModel.getMiningFunction();
		switch(miningFunction){
			case REGRESSION:
				break;
			default:
				throw new UnsupportedAttributeException(treeModel, miningFunction);
		}
	}

	@Override
	public JMethod translateRegressor(TranslationContext context){
		TreeModel treeModel = getModel();

		Node node = treeModel.getNode();

		Map<FieldName, Field<?>> activeFields = getActiveFields(Collections.singleton(node));

		JMethod evaluateMethod = context.evaluatorMethod(JMod.PUBLIC, getResultType(), node, false, true);

		try {
			context.pushScope(new MethodScope(evaluateMethod));

			translateNode(node, activeFields, context);
		} finally {
			context.popScope();
		}

		return evaluateMethod;
	}

	public Class<?> getResultType(){
		TreeModel treeModel = getModel();

		MathContext mathContext = treeModel.getMathContext();
		switch(mathContext){
			case FLOAT:
				return float.class;
			case DOUBLE:
				return double.class;
			default:
				throw new UnsupportedAttributeException(treeModel, mathContext);
		}
	}

	static
	public void translateNode(Node node, Map<FieldName, Field<?>> activeFields, TranslationContext context){
		Predicate predicate = node.getPredicate();

		JExpression predicateExpr = translatePredicate(predicate, activeFields, context);

		JBlock block = context.block();

		JBlock ifBlock = block._if(predicateExpr)._then();

		if(node.hasNodes()){
			context.pushScope(new Scope(ifBlock));

			try {
				List<Node> children = node.getNodes();

				for(Node child : children){
					translateNode(child, activeFields, context);
				}
			} finally {
				context.popScope();
			}
		}

		Object score = node.getScore();
		if(score == null){
			return;
		}

		JExpression scoreLitExpr = PMMLObjectUtil.createExpression(score, context);

		ifBlock._return(scoreLitExpr);
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