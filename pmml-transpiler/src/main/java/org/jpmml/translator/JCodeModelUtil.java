/*
 * Copyright (c) 2022 Villu Ruusmann
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

import java.util.Iterator;

import com.sun.codemodel.JDefinedClass;

public class JCodeModelUtil {

	private JCodeModelUtil(){
	}

	static
	public JDefinedClass getNestedClass(JDefinedClass clazz, String name){

		for(Iterator<JDefinedClass> it = clazz.classes(); it.hasNext(); ){
			JDefinedClass nestedClazz = it.next();

			if((name).equals(nestedClazz.name())){
				return nestedClazz;
			}
		}

		return null;
	}
}