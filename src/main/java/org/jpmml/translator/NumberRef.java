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

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JVar;

public class NumberRef extends ObjectRef {

	public NumberRef(JVar variable){
		super(variable);
	}

	@Override
	public JExpression equalTo(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.eq(literal(value, context));
	}

	@Override
	public JExpression notEqualTo(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.ne(literal(value, context));
	}

	@Override
	public JExpression lessThan(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.lt(literal(value, context));
	}

	@Override
	public JExpression lessOrEqual(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.lte(literal(value, context));
	}

	@Override
	public JExpression greaterOrEqual(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.gte(literal(value, context));
	}

	@Override
	public JExpression greaterThan(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.gt(literal(value, context));
	}
}