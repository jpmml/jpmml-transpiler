package com.jpmml.translator;

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
import java.util.stream.Collectors;

import com.google.common.collect.Iterables;
import org.dmg.pmml.PMMLObject;
import org.jpmml.model.ReflectionUtil;
import org.jpmml.model.annotations.Property;
import org.jpmml.model.annotations.ValueConstructor;

public class Template {

	private List<Field> instanceFields = Collections.emptyList();

	private List<Field> valueConstructorFields = Collections.emptyList();

	private List<Field> setterMethodFields = Collections.emptyList();


	Template(Class<? extends PMMLObject> clazz){
		Map<String, Field> fields = new LinkedHashMap<>();

		List<Field> instanceFields = ReflectionUtil.getFields(clazz);
		for(Field instanceField : instanceFields){

			// XXX: Nullify instead?
			if(("locator").equals(instanceField.getName())){
				continue;
			}

			fields.put(instanceField.getName(), instanceField);
		}

		this.instanceFields = new ArrayList<>(fields.values());

		Constructor<?>[] constructors = clazz.getDeclaredConstructors();

		List<Constructor<?>> valueConstructors = Arrays.stream(constructors)
			.filter(constructor -> constructor.getAnnotation(ValueConstructor.class) != null)
			.collect(Collectors.toList());

		if(valueConstructors.size() > 0){
			this.valueConstructorFields = new ArrayList<>();

			Constructor<?> valueConstructor = Iterables.getOnlyElement(valueConstructors);

			Annotation[][] parameterAnnotations = valueConstructor.getParameterAnnotations();
			for(int i = 0; i < parameterAnnotations.length; i++){
				Property property = (Property)parameterAnnotations[i][0];

				Field valueConstructorField = fields.remove(property.value());

				this.valueConstructorFields.add(valueConstructorField);
			}
		}

		this.setterMethodFields = new ArrayList<>(fields.values());
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
			template = new Template(clazz);

			Template.templates.put(clazz, template);
		}

		return template;
	}

	private static final Map<Class<? extends PMMLObject>, Template> templates = new HashMap<>();
}