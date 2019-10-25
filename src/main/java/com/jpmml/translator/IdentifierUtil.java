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

import org.dmg.pmml.FieldName;

public class IdentifierUtil {

	private IdentifierUtil(){
	}

	static
	public String create(FieldName name){
		String value = name.getValue();

		StringBuilder sb = new StringBuilder();

		char[] chars = value.toCharArray();

		for(int i = 0; i < chars.length; i++){
			char c = chars[i];

			if(sb.length() == 0){

				if(Character.isJavaIdentifierStart(c)){
					sb.append(Character.toLowerCase(c));
				}
			} else

			{
				if(Character.isJavaIdentifierPart(c)){
					sb.append(c);
				}
			}
		}

		return sb.toString();
	}

	static
	public String create(String prefix, Object object){
		return prefix + "$" + System.identityHashCode(object);
	}
}