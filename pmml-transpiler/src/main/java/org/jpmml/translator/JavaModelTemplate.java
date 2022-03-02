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
import java.util.List;

import com.sun.codemodel.JInvocation;

import org.dmg.pmml.Model;
import org.dmg.pmml.PMMLObject;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.ReflectionUtil;

public class JavaModelTemplate extends ModelTemplate {

	public JavaModelTemplate(){
		super(JavaModel.class);
	}

	@Override
	public JInvocation initializeObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		Model model = (Model)object;

		Template modelTemplate = Template.getTemplate(model.getClass());

		List<Field> instanceFields = getInstanceFields();
		for(Field instanceField : instanceFields){
			Field modelInstanceField = modelTemplate.getInstanceField(instanceField.getName());

			if(modelInstanceField == null){
				continue;
			}

			Object value = ReflectionUtil.getFieldValue(modelInstanceField, object);

			invocation = PMMLObjectUtil.addSetterMethod(instanceField, value, invocation, context);
		}

		return invocation;
	}
}