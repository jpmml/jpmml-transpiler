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

import java.util.Objects;

import com.sun.codemodel.JAssignmentTarget;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JExpressionImpl;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JStatement;

public class JCompoundAssignment extends JExpressionImpl implements JStatement {

	private JAssignmentTarget leftHandSide = null;

	private JExpression rightHandSide = null;

	private String operator = null;


	public JCompoundAssignment(JAssignmentTarget leftHandSide, JExpression rightHandSide, String operator){
		this.leftHandSide = Objects.requireNonNull(leftHandSide);
		this.rightHandSide = Objects.requireNonNull(rightHandSide);
		this.operator = Objects.requireNonNull(operator);

		switch(operator){
			case "+=":
			case "-=":
				break;
			default:
				throw new IllegalArgumentException(operator);
		}
	}

	@Override
	public void generate(JFormatter formatter){
		formatter.g(this.leftHandSide).p(this.operator).g(this.rightHandSide);
	}

	@Override
	public void state(JFormatter formatter){
		formatter.g(this).p(";");

		formatter.nl();
	}
}