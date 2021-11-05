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

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class PrimitiveRef extends OperableRef {

	public PrimitiveRef(JExpression expression){
		super(expression);

		if(expression instanceof JVar){
			JVar variable = (JVar)expression;

			JType type = variable.type();
			if(!type.isPrimitive()){
				throw new IllegalArgumentException(type.fullName());
			}
		}
	}

	@Override
	public JExpression isMissing(){
		return JExpr.FALSE;
	}

	@Override
	public JExpression isNotMissing(){
		return JExpr.TRUE;
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
}