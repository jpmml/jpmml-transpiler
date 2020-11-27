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

import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;

public class FieldInfo {

	private Field<?> field = null;

	private FieldInfo ref = null;

	private FunctionInvocation functionInvocation = null;

	private Integer count = null;

	private Encoder encoder = null;

	private String variableName = null;


	public FieldInfo(Field<?> field){
		this(field, null);
	}

	public FieldInfo(Field<?> field, Encoder encoder){
		setField(field);
		setEncoder(encoder);
	}

	public Field<?> getField(){
		return this.field;
	}

	private void setField(Field<?> field){
		this.field = Objects.requireNonNull(field);
	}

	public FieldInfo getRef(){
		return this.ref;
	}

	public void setRef(FieldInfo ref){
		this.ref = ref;
	}

	public FunctionInvocation getFunctionInvocation(){
		return this.functionInvocation;
	}

	public void setFunctionInvocation(FunctionInvocation functionInvocation){
		this.functionInvocation = functionInvocation;
	}

	public void updateCount(Integer count){

		if(count == null){
			return;
		} // End if

		if(this.count == null){
			this.count = count;
		} else

		{
			this.count += count;
		}
	}

	public Integer getCount(){
		return this.count;
	}

	public void setCount(Integer count){
		this.count = count;
	}

	public Encoder getEncoder(){
		return this.encoder;
	}

	public void setEncoder(Encoder encoder){
		this.encoder = encoder;
	}

	public String getVariableName(){

		if(this.variableName == null){
			this.variableName = createVariableName();
		}

		return this.variableName;
	}

	public void setVariableName(String varibaleName){
		this.variableName = varibaleName;
	}

	private String createVariableName(){
		Field<?> field = getField();
		FieldInfo ref = getRef();
		Encoder encoder = getEncoder();

		FieldName name = field.getName();

		while(ref != null){
			Field<?> refField = ref.getField();

			// XXX
			if(!(field.getOpType()).equals(refField.getOpType())){
				break;
			}

			name = refField.getName();

			ref = ref.getRef();
		}

		String result = IdentifierUtil.sanitize(name.getValue());

		if(encoder != null){
			result = (result + "2" + encoder.getName());
		}

		return result;
	}
}