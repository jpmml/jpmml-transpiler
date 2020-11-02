/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLElements;
import org.jpmml.model.ReflectionUtil;

public class PMMLTemplate extends Template {

	PMMLTemplate(Class<? extends PMML> clazz){
		super(clazz, getFields(clazz));
	}

	static
	private List<Field> getFields(Class<? extends PMML> clazz){
		List<Field> fields = new ArrayList<>(ReflectionUtil.getFields(clazz));

		fields.remove(PMMLElements.PMML_TRANSFORMATIONDICTIONARY);
		fields.add(fields.size(), PMMLElements.PMML_TRANSFORMATIONDICTIONARY);

		return fields;
	}
}