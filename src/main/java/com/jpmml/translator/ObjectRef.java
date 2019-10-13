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
import com.sun.codemodel.JVar;

abstract
public class ObjectRef extends JVarRef {

	public ObjectRef(JVar variable){
		super(variable);
	}

	public JExpression equalTo(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression notEqualTo(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression lessThan(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression lessOrEqual(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression greaterOrEqual(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression greaterThan(Object value, TranslationContext context){
		throw new UnsupportedOperationException();
	}

	public JExpression isIn(Collection<?> values, TranslationContext context){
		Iterator<?> it = toIterator(values);

		JExpression result = equalTo(it.next(), context);

		while(it.hasNext()){
			result = result.cor(equalTo(it.next(), context));
		}

		return result;
	}

	public JExpression isNotIn(Collection<?> values, TranslationContext context){
		Iterator<?> it = toIterator(values);

		JExpression result = notEqualTo(it.next(), context);

		while(it.hasNext()){
			result = result.cand(notEqualTo(it.next(), context));
		}

		return result;
	}

	protected JExpression literal(Object value, TranslationContext context){
		return PMMLObjectUtil.createExpression(value, context);
	}

	protected <E> Iterator<E> toIterator(Collection<E> values){
		return values.stream()
			.sorted()
			.iterator();
	}
}