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
package org.jpmml.translator;

import com.google.common.math.DoubleMath;
import com.sun.codemodel.JExpression;
import org.dmg.pmml.DataType;
import org.jpmml.evaluator.TypeUtil;

public class NumberRef extends ObjectRef {

	public NumberRef(JExpression expression){
		super(expression);
	}

	@Override
	public JExpression equalTo(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.eq(literal(value, context));
	}

	@Override
	public JExpression notEqualTo(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.ne(literal(value, context));
	}

	@Override
	public JExpression lessThan(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.lt(literal(value, context));
	}

	@Override
	public JExpression lessOrEqual(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.lte(literal(value, context));
	}

	@Override
	public JExpression greaterOrEqual(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.gte(literal(value, context));
	}

	@Override
	public JExpression greaterThan(Object value, TranslationContext context){
		JExpression expression = getExpression();

		return expression.gt(literal(value, context));
	}

	@Override
	public JExpression literal(Object value, TranslationContext context){
		JExpression expression = getExpression();

		if(value instanceof String){
			String string = (String)value;

			try {
				value = TypeUtil.parseOrCast(DataType.DOUBLE, string);

				if(DoubleMath.isMathematicalInteger((Double)value)){
					value = TypeUtil.parseOrCast(DataType.INTEGER, value);
				}
			} catch(IllegalArgumentException iae){
				// Ignored
			}
		}

		return super.literal(value, context);
	}
}