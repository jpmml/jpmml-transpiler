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

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JInvocation;
import jakarta.xml.bind.annotation.XmlTransient;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.ReflectionUtil;
import org.jpmml.model.annotations.Property;
import org.jpmml.model.annotations.ValueConstructor;

public class Template {

	private Set<Field> suppressedValueConstructorFields = null;

	private List<Field> instanceFields = Collections.emptyList();

	private List<Field> valueConstructorFields = Collections.emptyList();

	private List<Field> setterMethodFields = Collections.emptyList();


	Template(Class<? extends PMMLObject> clazz){
		this(clazz, ReflectionUtil.getFields(clazz));
	}

	Template(Class<? extends PMMLObject> clazz, List<Field> instanceFields){
		this(clazz, instanceFields, Collections.emptySet());
	}

	Template(Class<? extends PMMLObject> clazz, List<Field> instanceFields, Set<Field> suppressedValueConstructorFields){
		this.suppressedValueConstructorFields = Objects.requireNonNull(suppressedValueConstructorFields);

		Map<String, Field> fields = new LinkedHashMap<>();

		for(Field instanceField : instanceFields){
			boolean xmlTransient = (instanceField.getAnnotation(XmlTransient.class) != null);

			if(xmlTransient){
				continue;
			}

			fields.put(instanceField.getName(), instanceField);
		}

		this.instanceFields = new ArrayList<>(fields.values());

		Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		List<Constructor<?>> valueConstructors = Arrays.stream(constructors)
			.filter(constructor -> constructor.getAnnotation(ValueConstructor.class) != null)
			.collect(Collectors.toList());

		if(!valueConstructors.isEmpty()){
			this.valueConstructorFields = new ArrayList<>();

			Constructor<?> valueConstructor = Iterables.getOnlyElement(valueConstructors);

			Annotation[][] parameterAnnotations = valueConstructor.getParameterAnnotations();
			for(int i = 0; i < parameterAnnotations.length; i++){
				Property property = (Property)parameterAnnotations[i][0];

				Field valueConstructorField = fields.get(property.value());
				if(!this.suppressedValueConstructorFields.contains(valueConstructorField)){
					fields.remove(property.value());
				}

				this.valueConstructorFields.add(valueConstructorField);
			}
		}

		this.setterMethodFields = new ArrayList<>(fields.values());
	}

	public JInvocation constructObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		List<Field> valueConstructorFields = getValueConstructorFields();

		for(Field valueConstructorField : valueConstructorFields){
			Object value;

			if(!this.suppressedValueConstructorFields.contains(valueConstructorField)){
				value = ReflectionUtil.getFieldValue(valueConstructorField, object);
			} else

			{
				value = null;
			}

			PMMLObjectUtil.addValueConstructorParam(valueConstructorField, value, invocation, context);
		}

		return invocation;
	}

	public JInvocation initializeObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		List<Field> setterMethodFields = getSetterMethodFields();

		for(Field setterMethodField : setterMethodFields){
			Object value = ReflectionUtil.getFieldValue(setterMethodField, object);

			invocation = PMMLObjectUtil.addSetterMethod(setterMethodField, value, invocation, context);
		}

		return invocation;
	}

	public Field getInstanceField(String name){
		List<Field> instanceFields = getInstanceFields();

		for(Field instanceField : instanceFields){

			if((name).equals(instanceField.getName())){
				return instanceField;
			}
		}

		return null;
	}

	public List<Field> getInstanceFields(){
		return this.instanceFields;
	}

	public List<Field> getValueConstructorFields(){
		return this.valueConstructorFields;
	}

	public List<Field> getSetterMethodFields(){
		return this.setterMethodFields;
	}

	static
	public Template getTemplate(Class<? extends PMMLObject> clazz){
		Template template = Template.templates.get(clazz);

		if(template == null){

			if((PMML.class).isAssignableFrom(clazz)){
				template = new PMMLTemplate(clazz.asSubclass(PMML.class));
			} else

			if((JavaModel.class).isAssignableFrom(clazz)){
				template = new JavaModelTemplate();
			} else

			if((Model.class).isAssignableFrom(clazz)){
				template = new ModelTemplate(clazz.asSubclass(Model.class));
			} else

			{
				template = new Template(clazz);
			}

			Template.templates.put(clazz, template);
		}

		return template;
	}

	private static final Map<Class<? extends PMMLObject>, Template> templates = new HashMap<>();
}