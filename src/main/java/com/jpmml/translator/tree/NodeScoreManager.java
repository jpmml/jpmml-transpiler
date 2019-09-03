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
package com.jpmml.translator.tree;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.TranslationContext;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;

public class NodeScoreManager extends ArrayManager<Number> {

	public NodeScoreManager(String name, TranslationContext context){
		super(context.getOwner(), context.ref(Number.class), name);
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
}