/*
 * Copyright (c) 2021 Villu Ruusmann
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

import org.dmg.pmml.False;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.True;
import org.jpmml.model.ReflectionUtil;

public class PredicateKey {

	private Predicate predicate = null;


	public PredicateKey(Predicate predicate){
		setPredicate(predicate);
	}

	@Override
	public int hashCode(){
		Predicate predicate = getPredicate();

		int result = Objects.hash(predicate.getClass());

		if(predicate instanceof HasFieldReference){
			HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

			result = (31 * result) + Objects.hashCode(hasFieldReference.getField());
		}

		return result;
	}

	@Override
	public boolean equals(Object object){

		if(object instanceof PredicateKey){
			PredicateKey that = (PredicateKey)object;

			return Objects.equals((this.getPredicate()).getClass(), (that.getPredicate()).getClass()) && ReflectionUtil.equals(this.getPredicate(), that.getPredicate());
		}

		return false;
	}

	public Predicate getPredicate(){
		return this.predicate;
	}

	private void setPredicate(Predicate predicate){
		this.predicate = filter(Objects.requireNonNull(predicate));
	}

	static
	private Predicate filter(Predicate predicate){

		if(predicate instanceof True){
			return True.INSTANCE;
		} else

		if(predicate instanceof False){
			return False.INSTANCE;
		}

		return predicate;
	}
}