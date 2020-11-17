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

import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.jpmml.evaluator.FieldValue;

/**
 * @see FieldValue
 */
public class FieldValueRef extends JVarRef {

	private DataType dataType = null;


	public FieldValueRef(JVar variable, DataType dataType){
		super(variable);

		setDataType(dataType);
	}

	public JInvocation asJavaValue(){
		DataType dataType = getDataType();

		switch(dataType){
			case STRING:
				return asString();
			case INTEGER:
				return asInteger();
			case FLOAT:
				return asFloat();
			case DOUBLE:
				return asDouble();
			case BOOLEAN:
				return asBoolean();
			default:
				throw new IllegalArgumentException(dataType.toString());
		}
	}

	/**
	 * @see FieldValue#asString()
	 */
	public JInvocation asString(){
		return invoke("asString");
	}

	/**
	 * @see FieldValue#asNumber()
	 */
	public JInvocation asNumber(){
		return invoke("asNumber");
	}

	/**
	 * @see FieldValue#asInteger()
	 */
	public JInvocation asInteger(){
		return invoke("asInteger");
	}

	/**
	 * @see FieldValue#asFloat()
	 */
	public JInvocation asFloat(){
		return invoke("asFloat");
	}

	/**
	 * @see FieldValue#asDouble()
	 */
	public JInvocation asDouble(){
		return invoke("asDouble");
	}

	/**
	 * @see FieldValue#asBoolean()
	 */
	public JInvocation asBoolean(){
		return invoke("asBoolean");
	}

	public DataType getDataType(){
		return this.dataType;
	}

	private void setDataType(DataType dataType){
		this.dataType = Objects.requireNonNull(dataType);
	}
}