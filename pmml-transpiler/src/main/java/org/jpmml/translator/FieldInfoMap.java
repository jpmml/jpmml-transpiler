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

import java.util.LinkedHashMap;

import org.dmg.pmml.Field;
import org.dmg.pmml.HasFieldReference;

public class FieldInfoMap extends LinkedHashMap<String, FieldInfo> {

	public FieldInfo require(HasFieldReference<?> hasFieldReference){
		return require(hasFieldReference.requireField());
	}

	public FieldInfo require(String name){
		FieldInfo fieldInfo = get(name);
		if(fieldInfo == null){
			throw new IllegalArgumentException();
		}

		return fieldInfo;
	}

	public FieldInfo create(Field<?> field){
		FieldInfo fieldInfo = new FieldInfo(field);

		put(field.requireName(), fieldInfo);

		return fieldInfo;
	}
}