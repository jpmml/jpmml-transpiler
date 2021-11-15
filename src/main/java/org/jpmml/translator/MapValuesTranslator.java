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
package org.jpmml.translator;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldColumnPair;
import org.dmg.pmml.InlineTable;
import org.dmg.pmml.MapValues;
import org.dmg.pmml.OpType;
import org.dmg.pmml.PMMLAttributes;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.FieldValueUtil;
import org.jpmml.evaluator.InlineTableUtil;
import org.jpmml.evaluator.InvalidAttributeException;
import org.jpmml.evaluator.InvalidElementException;
import org.jpmml.evaluator.TypeUtil;
import org.jpmml.evaluator.UnsupportedElementException;

public class MapValuesTranslator extends ExpressionTranslator<MapValues> {

	public MapValuesTranslator(MapValues mapValues){
		super(mapValues);

		List<FieldColumnPair> fieldColumnPairs = mapValues.getFieldColumnPairs();
		if(fieldColumnPairs.size() != 1){
			throw new UnsupportedElementException(mapValues);
		}
	}

	@Override
	public void translateExpression(TranslationContext context){
		JDefinedClass owner = context.getOwner();

		MapValues mapValues = getExpression();

		InlineTable inlineTable = InlineTableUtil.getInlineTable(mapValues);

		List<FieldColumnPair> fieldColumnPairs = mapValues.getFieldColumnPairs();

		FieldColumnPair fieldColumnPair = Iterables.getOnlyElement(fieldColumnPairs);

		Map<Object, Object> mapping = parseInlineTable(inlineTable, fieldColumnPair.getColumn(), mapValues.getOutputColumn());

		DataType inputDataType = TypeUtil.getDataType(mapping.keySet());

		DataType outputDataType = mapValues.getDataType();
		if(outputDataType == null){
			outputDataType = TypeUtil.getDataType(mapping.values());
		}

		JVar valueVar = context.declare(FieldValue.class, "value", (context.getContextVariable()).evaluate(PMMLObjectUtil.createExpression(fieldColumnPair.getField(), context)));

		JMethod mapValueMethod = owner.method(JMod.PUBLIC, Object.class, "mapValues");

		JVar valueParam = mapValueMethod.param(context.ref(FieldValue.class), "value");

		try {
			context.pushScope(new MethodScope(mapValueMethod));

			FieldValueRef fieldValueRef = new FieldValueRef(valueParam, inputDataType);

			Object mapMissingTo = mapValues.getMapMissingTo();

			context._returnIf(valueParam.eq(JExpr._null()), PMMLObjectUtil.createExpression(mapMissingTo, context));

			Object defaultValue = mapValues.getDefaultValue();

			switch(inputDataType){
				case BOOLEAN:
					{
						if(defaultValue != null){
							throw new InvalidAttributeException(mapValues, PMMLAttributes.MAPVALUES_DEFAULTVALUE, defaultValue);
						}

						Object trueValue = mapping.get(Boolean.TRUE);
						Object falseValue = mapping.get(Boolean.FALSE);

						context._return(JOp.cond(fieldValueRef.asJavaValue(), PMMLObjectUtil.createExpression(trueValue, context), PMMLObjectUtil.createExpression(falseValue, context)));
					}
					break;
				default:
					{
						JVar javaValueVar = context.declare(fieldValueRef.getJavaType(), "javaValue", fieldValueRef.asJavaValue());

						context._return(javaValueVar, mapping, defaultValue);
					}
					break;
			}
		} finally {
			context.popScope();
		}

		JInvocation invocation = context.staticInvoke(FieldValueUtil.class, "create", PMMLObjectUtil.createExpression(outputDataType, context), PMMLObjectUtil.createExpression(OpType.CATEGORICAL, context), JExpr.invoke(mapValueMethod).arg(valueVar));

		context._return(invocation);
	}

	static
	private Map<Object, Object> parseInlineTable(InlineTable inlineTable, String inputColumn, String outputColumn){
		Table<Integer, String, Object> content = InlineTableUtil.getContent(inlineTable);

		Map<Object, Object> result = new LinkedHashMap<>();

		Set<Integer> rows = content.rowKeySet();
		for(Integer row : rows){
			Object inputValue = content.get(row, inputColumn);
			Object outputValue = content.get(row, outputColumn);

			if(inputValue == null || outputValue == null){
				throw new InvalidElementException(inlineTable);
			}

			result.put(inputValue, outputValue);
		}

		return result;
	}
}