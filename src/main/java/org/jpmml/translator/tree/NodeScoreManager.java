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

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;
import org.dmg.pmml.tree.Node;
import org.jpmml.translator.ArrayManager;

public class NodeScoreManager extends ArrayManager<Number> implements ScoreFunction<Number> {

	public NodeScoreManager(JType componentType, String name){
		super(componentType, name);
	}

	@Override
	public Number apply(Node node){
		Object score = node.getScore();

		return (Number)score;
	}

	@Override
	public JExpression createExpression(Number score){

		if(score instanceof Float){
			return JExpr.lit(score.floatValue());
		} else

		if(score instanceof Double){
			return JExpr.lit(score.doubleValue());
		}

		throw new IllegalArgumentException();
	}

	public Number[] getValues(){
		Collection<Number> elements = getElements();

		Number[] result = elements.stream()
			.toArray(Number[]::new);

		return result;
	}
}