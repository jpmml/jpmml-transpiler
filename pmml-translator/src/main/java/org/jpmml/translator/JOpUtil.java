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

import java.lang.reflect.Method;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JOp;

public class JOpUtil {

	private JOpUtil(){
	}

	static
	public boolean hasTopOp(JExpression expression){

		try {
			Method hasTopOpMethod = JOp.class.getDeclaredMethod("hasTopOp", JExpression.class);
			if(!hasTopOpMethod.isAccessible()){
				hasTopOpMethod.setAccessible(true);
			}

			return (Boolean)hasTopOpMethod.invoke(null, expression);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}
}