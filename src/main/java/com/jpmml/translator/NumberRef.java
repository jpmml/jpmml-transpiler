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
package com.jpmml.translator;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JVar;

public class NumberRef extends ObjectRef {

	public NumberRef(JVar variable){
		super(variable);
	}

	@Override
	public JExpression equalTo(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.eq(valueExpr);
	}

	@Override
	public JExpression notEqualTo(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.ne(valueExpr);
	}

	@Override
	public JExpression lessThan(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.lt(valueExpr);
	}

	@Override
	public JExpression lessOrEqual(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.lte(valueExpr);
	}

	@Override
	public JExpression greaterOrEqual(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.gte(valueExpr);
	}

	@Override
	public JExpression greaterThan(JExpression valueExpr){
		JVar variable = getVariable();

		return variable.gt(valueExpr);
	}
}