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

import java.util.List;
import java.util.Objects;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JStatement;

public class JIfStatement implements JStatement {

	private JExpression expression = null;

	private JBlock _then = new JBlock();

	private JBlock _else = null;


	public JIfStatement(JExpression expression){
		this.expression = Objects.requireNonNull(expression);
	}

	public JBlock _then(){
		return this._then;
	}

	public boolean hasElse(){
		return (this._else != null);
	}

	public JIfStatement _elseif(JIfStatement ifStatement){

		if(this._else == null){
			this._else = new JBlock(false, false);
		} else

		{
			throw new IllegalStateException();
		}

		this._else.add(ifStatement);

		return ifStatement;
	}

	public JBlock _else(){

		if(this._else == null){
			this._else = new JBlock();
		}

		return this._else;
	}

	@Override
	public void state(JFormatter formatter){

		if(Objects.equals(JExpr.TRUE, this.expression)){
			JBlockUtil.generateBody(this._then, formatter);

			return;
		} // End if

		if(Objects.equals(JExpr.FALSE, this.expression)){
			JBlockUtil.generateBody(this._else, formatter);

			return;
		} // End if

		if(JOpUtil.hasTopOp(this.expression)){
			formatter.p("if ").g(this.expression);
		} else

		{
			formatter.p("if (").g(this.expression).p(")");
		}

		formatter.g(this._then);

		if(this._else != null && !this._else.isEmpty()){
			formatter.p("else");
			formatter.g(this._else);

			List<?> elseObjects = this._else.getContents();
			if(elseObjects.size() == 1){
				Object elseObject = elseObjects.get(0);

				// Don't add an extra newline
				if(elseObject instanceof JIfStatement){
					return;
				}
			}
		}

		formatter.nl();
	}
}