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
package org.jpmml.translator.tree;

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;

public class ScorerUtil {

	private ScorerUtil(){
	}

	static
	public JExpression format(Number value){

		if(value instanceof Float){
			return JExpr.lit(value.floatValue());
		} else

		if(value instanceof Double){
			return JExpr.lit(value.doubleValue());
		}

		throw new IllegalArgumentException();
	}
}