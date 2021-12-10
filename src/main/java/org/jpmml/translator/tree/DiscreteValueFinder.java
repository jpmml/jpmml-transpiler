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
package org.jpmml.translator.tree;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.dmg.pmml.ComplexArray;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.VisitorAction;
import org.jpmml.model.visitors.AbstractVisitor;

public class DiscreteValueFinder extends AbstractVisitor {

	private Map<String, Set<Object>> fieldValues = new LinkedHashMap<>();


	@Override
	public VisitorAction visit(SimplePredicate simplePredicate){
		String fieldName = simplePredicate.getField();
		SimplePredicate.Operator operator = simplePredicate.getOperator();
		Object value = simplePredicate.getValue();

		switch(operator){
			case EQUAL:
			case NOT_EQUAL:
				addValue(fieldName, value);
				break;
			default:
				break;
		}

		return super.visit(simplePredicate);
	}

	@Override
	public VisitorAction visit(SimpleSetPredicate simpleSetPredicate){
		String fieldName = simpleSetPredicate.getField();
		SimpleSetPredicate.BooleanOperator booleanOperator = simpleSetPredicate.getBooleanOperator();
		ComplexArray array = (ComplexArray)simpleSetPredicate.getArray();

		Collection<?> values = array.getValue();

		switch(booleanOperator){
			case IS_IN:
			case IS_NOT_IN:
				for(Object value : values){
					addValue(fieldName, value);
				}
				break;
			default:
				break;
		}

		return super.visit(simpleSetPredicate);
	}

	public void addValue(String fieldName, Object value){
		Map<String, Set<Object>> fieldValues = getFieldValues();

		Set<Object> values = fieldValues.get(fieldName);
		if(values == null){
			values = new LinkedHashSet<>();

			fieldValues.put(fieldName, values);
		}

		values.add(value);
	}

	public Map<String, Set<Object>> getFieldValues(){
		return this.fieldValues;
	}
}