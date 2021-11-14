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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JType;

abstract
public class ArrayManager<E> {

	private JType componentType = null;

	private String name = null;

	private JFieldVar arrayVar = null;

	private JArray array = null;

	private Map<E, Integer> indices = new LinkedHashMap<>();


	public ArrayManager(JType componentType, String name){
		setComponentType(componentType);
		setName(name);
	}

	abstract
	public JExpression createExpression(E element);

	public void initArrayVar(JDefinedClass owner){
		JType componentType = getComponentType();
		String name = getName();

		this.arrayVar = owner.field(Modifiers.MEMBER_PRIVATE, componentType.array(), name);
	}

	public void initArray(){
		JType componentType = getComponentType();

		if(this.arrayVar == null){
			throw new IllegalStateException();
		}

		this.array = JExpr.newArray(componentType);

		this.arrayVar.init(this.array);

		Collection<Map.Entry<E, Integer>> entries = this.indices.entrySet();
		for(Map.Entry<E, Integer> entry : entries){
			E element = entry.getKey();

			this.array.add(createExpression(element));
		}
	}

	public int size(){
		return this.indices.size();
	}

	public Collection<E> getElements(){
		return this.indices.keySet();
	}

	public int getOrInsert(E element){
		Integer index = this.indices.get(element);

		if(index == null){

			if(this.array != null){
				this.array.add(createExpression(element));
			}

			index = this.indices.size();

			this.indices.put(element, index);
		}

		return index;
	}

	public JExpression getComponent(int index){
		return getComponent(JExpr.lit(index));
	}

	public JExpression getComponent(JExpression indexExpr){

		if(this.arrayVar == null){
			throw new IllegalStateException();
		}

		return this.arrayVar.component(indexExpr);
	}

	public JType getComponentType(){
		return this.componentType;
	}

	private void setComponentType(JType componentType){
		this.componentType = Objects.requireNonNull(componentType);
	}

	public String getName(){
		return this.name;
	}

	private void setName(String name){
		this.name = Objects.requireNonNull(name);
	}

	public JFieldVar getArrayVar(){
		return this.arrayVar;
	}

	public JArray getArray(){
		return this.array;
	}
}