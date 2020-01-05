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

import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JVar;
import org.jpmml.evaluator.FieldValue;

/**
 * @see FieldValue
 */
public class FieldValueRef extends JVarRef {

	public FieldValueRef(JVar variable){
		super(variable);
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
}