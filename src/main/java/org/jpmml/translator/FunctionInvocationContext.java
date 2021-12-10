/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.HashMap;

import org.dmg.pmml.DefineFunction;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;

abstract
public class FunctionInvocationContext extends HashMap<String, Expression> {

	abstract
	public DefineFunction getDefineFunction(String name);

	public String resolve(String name){

		if(isEmpty()){
			return name;
		}

		FieldRef fieldRef = (FieldRef)get(name);

		return fieldRef.getField();
	}

	public Expression resolve(Expression expression){

		if(isEmpty()){
			return expression;
		} // End if

		if(expression instanceof FieldRef){
			FieldRef fieldRef = (FieldRef)expression;

			return get(fieldRef.getField());
		}

		return expression;
	}
}