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
import java.util.Comparator;
import java.util.Iterator;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JVar;

public class OrdinalRef extends ObjectRef {

	private OrdinalEncoder encoder = null;


	public OrdinalRef(JVar variable, OrdinalEncoder encoder){
		super(variable);

		setEncoder(encoder);
	}

	@Override
	public JExpression equalTo(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.eq(literal(value, context));
	}

	@Override
	public JExpression notEqualTo(Object value, TranslationContext context){
		JVar variable = getVariable();

		return variable.ne(literal(value, context));
	}

	@Override
	public JExpression isIn(Collection<?> values, TranslationContext context){
		return super.isIn(values, context);
	}

	@Override
	public JExpression isNotIn(Collection<?> values, TranslationContext context){
		return super.isNotIn(values, context);
	}

	@Override
	protected JExpression literal(Object value, TranslationContext context){
		OrdinalEncoder encoder = getEncoder();

		value = encoder.encode(value);

		return super.literal(value, context);
	}

	@Override
	protected <E> Iterator<E> toIterator(Collection<E> values){
		OrdinalEncoder encoder = getEncoder();

		Comparator<E> comparator = new Comparator<E>(){

			@Override
			public int compare(E left, E right){
				return ((Comparable)encoder.encode(left)).compareTo((Comparable)encoder.encode(right));
			}
		};

		return values.stream()
			.sorted(comparator)
			.iterator();
	}

	public OrdinalEncoder getEncoder(){
		return this.encoder;
	}

	private void setEncoder(OrdinalEncoder encoder){
		this.encoder = encoder;
	}
}