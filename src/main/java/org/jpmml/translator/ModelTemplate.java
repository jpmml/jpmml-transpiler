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
import java.util.Collection;
import java.util.List;

import com.google.common.collect.MoreCollectors;
import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMMLObject;
import org.jpmml.model.ReflectionUtil;

public class ModelTemplate extends Template {

	ModelTemplate(Class<? extends Model> clazz){
		super(clazz, getFields(clazz));
	}

	static
	private List<Field> getFields(Class<? extends Model> clazz){
		List<Field> fields = new ArrayList<>(ReflectionUtil.getFields(clazz));

		Field localTransformationsField = getField(fields, LocalTransformations.class);
		Field outputField = getField(fields, Output.class);

		fields.remove(localTransformationsField);
		fields.remove(outputField);

		fields.add(fields.size(), localTransformationsField);
		fields.add(fields.size(), outputField);

		return fields;
	}

	static
	private Field getField(Collection<Field> fields, Class<? extends PMMLObject> clazz){
		return fields.stream()
			.filter(field -> clazz.isAssignableFrom(field.getType()))
			.collect(MoreCollectors.onlyElement());
	}
}