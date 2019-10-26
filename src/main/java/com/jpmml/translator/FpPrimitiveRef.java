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
package com.jpmml.translator;

import java.util.Collection;
import java.util.Iterator;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class FpPrimitiveRef extends PrimitiveRef {

	public FpPrimitiveRef(JVar variable){
		super(variable);

		JType type = variable.type();
		switch(type.name()){
			case "float":
			case "double":
				break;
			default:
				throw new IllegalArgumentException(type.fullName());
		}
	}

	@Override
	public JExpression isMissing(){
		JVar variable = getVariable();

		return (variable).ne(variable);
	}

	@Override
	public JExpression isNotMissing(){
		JVar variable = getVariable();

		return (variable).eq(variable);
	}

	@Override
	public JExpression notEqualTo(Object value, TranslationContext context){
		return (isNotMissing()).cand(super.notEqualTo(value, context));
	}

	@Override
	public JExpression isNotIn(Collection<?> values, TranslationContext context){
		Iterator<?> it = values.stream()
			.sorted()
			.iterator();

		JExpression result = super.notEqualTo(it.next(), context);

		while(it.hasNext()){
			result = result.cand(super.notEqualTo(it.next(), context));
		}

		return (isNotMissing()).cand(result);
	}
}