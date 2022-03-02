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

import java.util.Objects;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;

public class JExpressionRef {

	private JExpression expression = null;


	public JExpressionRef(JExpression expression){
		setExpression(expression);
	}

	public JInvocation invoke(JMethod method, JExpression... argExprs){
		return invoke(method.name(), argExprs);
	}

	public JInvocation invoke(String method, JExpression... argExprs){
		JExpression expression = getExpression();

		JInvocation result = expression.invoke(method);

		for(JExpression argExpr : argExprs){
			result = result.arg(argExpr);
		}

		return result;
	}

	public JExpression getExpression(){
		return this.expression;
	}

	private void setExpression(JExpression expression){
		this.expression = Objects.requireNonNull(expression);
	}
}