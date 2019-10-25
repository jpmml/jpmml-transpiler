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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JVar;

public class OrdinalRef extends OperableRef {

	private OrdinalEncoder encoder = null;


	public OrdinalRef(JVar variable, OrdinalEncoder encoder){
		super(variable);

		setEncoder(encoder);
	}

	@Override
	public JExpression isMissing(){
		JVar variable = getVariable();

		return variable.eq(OrdinalEncoder.MISSING_VALUE);
	}

	@Override
	public JExpression isNotMissing(){
		JVar variable = getVariable();

		return variable.ne(OrdinalEncoder.MISSING_VALUE);
	}

	@Override
	public JExpression equalTo(Object value, TranslationContext context){
		JVar variable = getVariable();
		OrdinalEncoder encoder = getEncoder();

		value = encoder.encode(value);

		return variable.eq(literal(value, context));
	}

	@Override
	public JExpression notEqualTo(Object value, TranslationContext context){
		JVar variable = getVariable();
		OrdinalEncoder encoder = getEncoder();

		value = encoder.encode(value);

		return (variable.ne(OrdinalEncoder.MISSING_VALUE)).cand(variable.ne(literal(value, context)));
	}

	@Override
	public JExpression isIn(Collection<?> values, TranslationContext context){
		JVar variable = getVariable();
		OrdinalEncoder encoder = getEncoder();

		List<Chunk> chunks = chunk(encoder, values);

		ensureIsSetMethod(encoder, chunks, context);

		Iterator<Chunk> it = chunks.iterator();

		JExpression result = (it.next()).isIn(variable);

		while(it.hasNext()){
			result = result.cor((it.next()).isIn(variable));
		}

		return result;
	}

	@Override
	public JExpression isNotIn(Collection<?> values, TranslationContext context){
		JVar variable = getVariable();
		OrdinalEncoder encoder = getEncoder();

		List<Chunk> chunks = chunk(encoder, values);

		ensureIsSetMethod(encoder, chunks, context);

		Iterator<Chunk> it = chunks.iterator();

		JExpression result = (variable.ne(OrdinalEncoder.MISSING_VALUE)).cand((it.next()).isNotIn(variable));

		while(it.hasNext()){
			result = result.cand((it.next()).isNotIn(variable));
		}

		return result;
	}

	public OrdinalEncoder getEncoder(){
		return this.encoder;
	}

	private void setEncoder(OrdinalEncoder encoder){
		this.encoder = encoder;
	}

	static
	public List<Chunk> chunk(OrdinalEncoder encoder, Collection<?> values){
		List<Chunk> result = new ArrayList<>();

		Iterator<Integer> it = values.stream()
			.map(encoder::encode)
			.distinct()
			.sorted()
			.iterator();

		Chunk chunk = null;

		while(it.hasNext()){
			Integer value = it.next();

			if(chunk == null){
				chunk = new Chunk();
				chunk.add(value);

				continue;
			} // End if

			Integer offsetValue = chunk.get(0);

			if((value - offsetValue) < 32){
				chunk.add(value);
			} else

			{
				result.add(chunk);

				chunk = new Chunk();
				chunk.add(value);
			}
		}

		if(chunk != null){
			result.add(chunk);
		}

		return result;
	}

	static
	private void ensureIsSetMethod(OrdinalEncoder encoder, Collection<Chunk> chunks, TranslationContext context){

		for(Chunk chunk : chunks){

			if(chunk.size() > 2 && !chunk.isDense()){
				encoder.ensureIsSetMethod(context);

				return;
			}
		}
	}

	static
	public class Chunk extends ArrayList<Integer> {

		public boolean isDense(){
			int size = size();

			if(size == 1){
				return true;
			} else

			if(size > 1){
				int firstValue = get(0);
				int lastValue = get(size - 1);

				return ((lastValue - firstValue) == (size - 1));
			}

			throw new IllegalStateException();
		}

		public JExpression isIn(JVar variable){
			int size = size();

			if(size == 1){
				return variable.eq(literal(0));
			} else

			if(size == 2){
				return (variable.eq(literal(0)).cor(variable.eq(literal(1))));
			} // End if

			if(isDense()){
				return (variable.gte(literal(0))).cand(variable.lte(literal(size - 1)));
			} else

			{
				return isSet(variable);
			}
		}

		public JExpression isNotIn(JVar variable){
			int size = size();

			if(size == 1){
				return variable.ne(literal(0));
			} else

			if(size == 2){
				return (variable.ne(literal(0))).cand(variable.ne(literal(1)));
			} // End if

			if(isDense()){
				return (variable.lt(literal(0))).cor(variable.gt(literal(size - 1)));
			} else

			{
				return (isSet(variable)).not();
			}
		}

		public JExpression isSet(JVar variable){
			int size = size();

			int firstValue = get(0);
			int lastValue = get(size - 1);

			Iterable<Integer> values = this;

			int bitSet = 0;

			for(int value : values){

				if(firstValue >= 1 && lastValue <= 32){
					bitSet |= (1 << (value - 1));
				} else

				{
					bitSet |= (1 << (value - firstValue));
				}
			}

			JExpression bitSetExpr = JExpr.lit(bitSet);

			JExpression indexExpr;

			if(firstValue >= 1 && lastValue <= 32){
				indexExpr = variable.minus(JExpr.lit(1));
			} else

			{
				indexExpr = variable.minus(JExpr.lit(firstValue));
			}

			return JExpr.invoke("isSet").arg(bitSetExpr).arg(indexExpr);
		}

		private JExpression literal(int index){
			return JExpr.lit(get(index));
		}
	}
}