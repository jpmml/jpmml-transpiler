/*
 * Copyright (c) 2017 Villu Ruusmann
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

import java.util.LinkedHashMap;
import java.util.Map;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JType;

abstract
class ArrayManager<E> {

	private JType type = null;

	private String name = null;

	private Map<JDefinedClass, ArrayDeclaration> arrayDeclarations = new LinkedHashMap<>();


	ArrayManager(JType type, String name){
		setType(type);
		setName(name);
	}

	abstract
	public JDefinedClass getOwner();

	abstract
	public JExpression createExpression(E element);

	public JExpression getExpression(E element){
		JDefinedClass owner = getOwner();

		ArrayDeclaration arrayDeclaration = this.arrayDeclarations.get(owner);
		if(arrayDeclaration == null){
			arrayDeclaration = new ArrayDeclaration(owner);

			this.arrayDeclarations.put(owner, arrayDeclaration);
		}

		return arrayDeclaration.getExpression(element);
	}

	public JType getType(){
		return this.type;
	}

	private void setType(JType type){
		this.type = type;
	}

	public String getName(){
		return this.name;
	}

	private void setName(String name){
		this.name = name;
	}

	private class ArrayDeclaration {

		private JArray array = null;

		private JFieldVar arrayVar = null;

		private Map<E, JExpression> expressions = new LinkedHashMap<>();


		private ArrayDeclaration(JDefinedClass owner){
			JType type = getType();
			String name = getName();

			this.array = JExpr.newArray(type);

			this.arrayVar = owner.field(JMod.PRIVATE | (owner.isAnonymous() ? 0 : JMod.STATIC), type.array(), name, this.array);
		}

		public JExpression getExpression(E element){
			JExpression expression = this.expressions.get(element);

			if(expression == null){
				this.array.add(createExpression(element));

				int size = this.expressions.size();

				expression = this.arrayVar.component(JExpr.lit(size));

				this.expressions.put(element, expression);
			}

			return expression;
		}
	}
}