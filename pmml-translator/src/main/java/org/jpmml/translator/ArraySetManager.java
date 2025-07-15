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
package org.jpmml.translator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JType;

abstract
public class ArraySetManager<E> extends ArrayManager<E> {

	private Map<E, Integer> indices = new LinkedHashMap<>();


	public ArraySetManager(JType componentType, String name){
		super(componentType, name);
	}

	abstract
	public JExpression createExpression(E element);

	@Override
	public List<E> getElements(){
		List<E> elements = new ArrayList<>(this.indices.keySet());

		return elements;
	}

	@Override
	public boolean isEmpty(){
		return this.indices.isEmpty();
	}

	@Override
	public int size(){
		return this.indices.size();
	}

	@Override
	public void initArray(){
		super.initArray();

		JArray array = getArray();

		Collection<Map.Entry<E, Integer>> entries = this.indices.entrySet();
		for(Map.Entry<E, Integer> entry : entries){
			E element = entry.getKey();

			array.add(createExpression(element));
		}
	}

	public JExpression getComponent(int index){
		return getComponent(JExpr.lit(index));
	}

	public JExpression getComponent(JExpression indexExpr){
		JFieldVar arrayVar = getArrayVar();

		if(arrayVar == null){
			throw new IllegalStateException();
		}

		return arrayVar.component(indexExpr);
	}

	public int getOrInsert(E element){
		Integer index = this.indices.get(element);

		if(index == null){
			JArray array = getArray();

			if(array != null){
				array.add(createExpression(element));
			}

			index = this.indices.size();

			this.indices.put(element, index);
		}

		return index;
	}
}