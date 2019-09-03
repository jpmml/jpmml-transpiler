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
public class ArrayManager<E> {

	private JArray array = null;

	private JFieldVar arrayVar = null;

	private Map<E, Integer> indices = new LinkedHashMap<>();


	public ArrayManager(JDefinedClass owner, JType type, String name){
		JArray array = JExpr.newArray(type);

		this.array = array;
		this.arrayVar = owner.field(JMod.PRIVATE | (owner.isAnonymous() ? 0 : JMod.STATIC), type.array(), name, array);
	}

	abstract
	public JExpression createExpression(E element);

	public int getOrInsert(E element){
		Integer index = this.indices.get(element);

		if(index == null){
			this.array.add(createExpression(element));

			index = this.indices.size();

			this.indices.put(element, index);
		}

		return index;
	}

	public JExpression getComponent(int index){
		return getComponent(JExpr.lit(index));
	}

	public JExpression getComponent(JExpression indexExpr){
		return this.arrayVar.component(indexExpr);
	}
}