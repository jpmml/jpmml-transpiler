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
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class JVarRef {

	private JVar variable = null;


	public JVarRef(JVar variable){
		setVariable(variable);
	}

	public JType type(){
		JVar variable = getVariable();

		return variable.type();
	}

	public String name(){
		JVar variable = getVariable();

		return variable.name();
	}

	public JInvocation invoke(JMethod method, JExpression... argExprs){
		return invoke(method.name(), argExprs);
	}

	public JInvocation invoke(String method, JExpression... argExprs){
		JVar variable = getVariable();

		JInvocation result = variable.invoke(method);

		for(JExpression argExpr : argExprs){
			result = result.arg(argExpr);
		}

		return result;
	}

	public JVar getVariable(){
		return this.variable;
	}

	private void setVariable(JVar variable){
		this.variable = variable;
	}
}