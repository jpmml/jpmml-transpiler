/*
 * Copyright (c) 2022 Villu Ruusmann
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
import java.util.List;
import java.util.Objects;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JType;

abstract
public class ArrayManager<E> {

	private JType componentType = null;

	private String name = null;

	private JFieldVar arrayVar = null;

	private JArray array = null;


	public ArrayManager(JType componentType, String name){
		setComponentType(componentType);
		setName(name);
	}

	abstract
	public List<E> getElements();

	public int size(){
		Collection<E> elements = getElements();

		return elements.size();
	}

	public void initArrayVar(JDefinedClass owner){
		JType componentType = getComponentType();
		String name = getName();

		this.arrayVar = owner.field(Modifiers.PRIVATE_STATIC_FINAL, componentType.array(), name);
	}

	public void initArray(){
		JType componentType = getComponentType();

		if(this.arrayVar == null){
			throw new IllegalStateException();
		}

		JArray array = JExpr.newArray(componentType);

		this.arrayVar.init(array);

		this.array = array;
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