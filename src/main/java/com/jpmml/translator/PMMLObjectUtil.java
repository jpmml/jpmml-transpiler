/*
 * Copyright (c) 2017 Villu Ruusmann
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

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import javax.xml.bind.JAXBElement;
import javax.xml.namespace.QName;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.Header;
import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.ModelExplanation;
import org.dmg.pmml.ModelStats;
import org.dmg.pmml.ModelVerification;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.TransformationDictionary;
import org.jpmml.evaluator.PMMLException;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.ReflectionUtil;
import org.w3c.dom.Element;

public class PMMLObjectUtil {

	private PMMLObjectUtil(){
	}

	static
	public JDefinedClass createClass(PMML pmml, String className, TranslationContext context){
		JCodeModel codeModel = context.getCodeModel();

		Class<?> clazz = pmml.getClass();

		JDefinedClass owner;

		try {
			owner = codeModel._class(className != null ? className : IdentifierUtil.create(clazz.getSimpleName(), pmml));
		} catch(JClassAlreadyExistsException jcaee){
			throw new IllegalArgumentException(jcaee);
		}

		owner._extends(clazz);

		try {
			context.pushOwner(owner);

			JMethod constructor = owner.constructor(JMod.PUBLIC);

			JBlock block = constructor.body();

			block.add(constructObject(pmml, JExpr.invoke("super"), context));
			block.add(initializeObject(pmml, null, context));
		} finally {
			context.popOwner();
		}

		return owner;
	}

	static
	public JMethod createBuilderMethod(PMMLObject object, TranslationContext context){
		Class<? extends PMMLObject> clazz = object.getClass();

		while(clazz != null){
			String simpleName = clazz.getSimpleName();

			if(simpleName.startsWith("Rich")){
				clazz = (Class<? extends PMMLObject>)clazz.getSuperclass();

				continue;
			}

			break;
		}

		JDefinedClass owner = context.getOwner();

		JMethod method = owner.method((JMod.PRIVATE | JMod.FINAL | (owner.isAnonymous() ? 0 : JMod.STATIC)), clazz, IdentifierUtil.create("build" + clazz.getSimpleName(), object));

		JBlock block = method.body();

		block._return(createObject(object, context));

		return method;
	}

	static
	public JInvocation createObject(PMMLObject object, TranslationContext context){
		Class<? extends PMMLObject> clazz = object.getClass();

		JInvocation invocation = constructObject(object, context._new(clazz), context);

		invocation = initializeObject(object, invocation, context);

		return invocation;
	}

	static
	public JInvocation constructObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		Class<? extends PMMLObject> clazz = object.getClass();

		Template template = Template.getTemplate(clazz);

		List<Field> valueConstructorFields = template.getValueConstructorFields();
		for(Field valueConstructorField : valueConstructorFields){
			Object value = ReflectionUtil.getFieldValue(valueConstructorField, object);

			if(value == null){
				invocation.arg(JExpr._null());

				continue;
			} // End if

			if(value instanceof List){
				List<?> elements = (List<?>)value;

				JInvocation listInvocation = context.staticInvoke(Arrays.class, "asList");

				for(Object element : elements){
					listInvocation.arg(createExpression(element, context));
				}

				invocation.arg(listInvocation);
			} else

			{
				invocation.arg(createExpression(value, context));
			}
		}

		return invocation;
	}

	static
	public JInvocation initializeObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		Class<? extends PMMLObject> clazz = object.getClass();

		Template template = Template.getTemplate(clazz);

		List<Field> setterMethodFields = template.getSetterMethodFields();
		for(Field setterMethodField : setterMethodFields){
			Object value = ReflectionUtil.getFieldValue(setterMethodField, object);

			invocation = initialize(setterMethodField, value, invocation, context);
		}

		return invocation;
	}

	static
	public JInvocation initializeJavaModel(Model model, JInvocation invocation, TranslationContext context){
		Class<? extends Model> modelClazz = model.getClass();

		Template modelTemplate = Template.getTemplate(modelClazz);
		Template javaModelTemplate = Template.getTemplate(JavaModel.class);

		List<Field> javaModelInstanceFields = javaModelTemplate.getInstanceFields();
		for(Field javaModelInstanceField : javaModelInstanceFields){
			Field modelInstanceField = modelTemplate.getInstanceField(javaModelInstanceField.getName());

			if(modelInstanceField == null){
				continue;
			}

			Object value = ReflectionUtil.getFieldValue(modelInstanceField, model);

			invocation = initialize(javaModelInstanceField, value, invocation, context);
		}

		return invocation;
	}

	static
	public JExpression createExpression(Object value, TranslationContext context){

		if(value instanceof JVarRef){
			JVarRef variableRef = (JVarRef)value;

			return variableRef.getVariable();
		} else

		if(value instanceof JVarBuilder){
			JVarBuilder variableBuilder = (JVarBuilder)value;

			return variableBuilder.getVariable();
		}

		if(value instanceof JExpression){
			JExpression expression = (JExpression)value;

			return expression;
		} // End if

		if(value == null){
			return JExpr._null();
		}

		Class<?> clazz = value.getClass();

		if(ReflectionUtil.isPrimitiveWrapper(clazz)){

			if((Integer.class).equals(clazz)){
				return JExpr.lit((Integer)value);
			} else

			if((Float.class).equals(clazz)){
				return JExpr.lit((Float)value);
			} else

			if((Double.class).equals(clazz)){
				return JExpr.lit((Double)value);
			} else

			if((Boolean.class).equals(clazz)){
				return JExpr.lit((Boolean)value);
			}
		} else

		if((String.class).isAssignableFrom(clazz)){
			return JExpr.lit((String)value);
		} else

		if((Enum.class).isAssignableFrom(clazz)){
			Enum<?> enumValue = (Enum<?>)value;
			JClass enumClass = context.ref(clazz);

			return enumClass.staticRef(enumValue.name());
		} else

		if((FieldName.class).isAssignableFrom(clazz)){
			FieldName fieldName = (FieldName)value;

			return context.constantFieldName(fieldName);
		} else

		if((QName.class).isAssignableFrom(clazz)){
			QName xmlName = (QName)value;

			return context.constantXmlName(xmlName);
		} else

		if((PMMLObject.class).isAssignableFrom(clazz)){
			PMMLObject pmmlObject = (PMMLObject)value;

			JExpression representation = context.getRepresentation(pmmlObject);
			if(representation != null){
				return representation;
			} // End if

			// PMML-level elements
			if((pmmlObject instanceof Header) || (pmmlObject instanceof DataDictionary) || (pmmlObject instanceof TransformationDictionary)){
				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			// Model-level elements
			if((pmmlObject instanceof MiningSchema) || (pmmlObject instanceof LocalTransformations) || (pmmlObject instanceof Output) || (pmmlObject instanceof ModelStats) || (pmmlObject instanceof ModelExplanation) || (pmmlObject instanceof ModelVerification)){
				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			if(pmmlObject instanceof org.dmg.pmml.Field){
				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			if(pmmlObject instanceof Model){
				PMML pmml = context.getPMML();
				Model model = (Model)pmmlObject;

				ModelTranslatorFactory modelTranslatorFactory = ModelTranslatorFactory.newInstance();

				try {
					ModelTranslator<?> modelTranslator = modelTranslatorFactory.newModelTranslator(pmml, model);

					return modelTranslator.translate(context);
				} catch(PMMLException pe){
					context.addIssue(pe);
				}

				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			}

			return createObject(pmmlObject, context);
		} else

		if((JAXBElement.class).isAssignableFrom(clazz)){
			JAXBElement<?> jaxbElement = (JAXBElement<?>)value;

			Object jaxbValue = jaxbElement.getValue();

			return context._new(context.ref(JAXBElement.class).narrow(jaxbValue.getClass()), jaxbElement.getName(), JExpr.dotclass(context.ref(jaxbValue.getClass())), jaxbValue);
		} else

		if((Element.class).isAssignableFrom(clazz)){
			Element domElement = (Element)value;

			QName xmlName = new QName(domElement.getNamespaceURI(), domElement.getLocalName(), domElement.getPrefix());
			String stringValue = domElement.getTextContent();

			return createExpression(new JAXBElement<>(xmlName, String.class, stringValue), context);
		}

		throw new IllegalArgumentException(clazz.getName());
	}

	static
	private JInvocation initialize(Field setterMethodField, Object value, JInvocation invocation, TranslationContext context){

		if(value == null){
			return invocation;
		} // End if

		if(value instanceof List){
			List<?> elements = (List<?>)value;

			invocation = JExpr.invoke(invocation, formatSetterName("add", setterMethodField));

			for(Object element : elements){
				invocation.arg(createExpression(element, context));
			}
		} else

		{
			Class<?> valueClazz = value.getClass();

			if(valueClazz.isPrimitive() && ReflectionUtil.isDefaultValue(value)){
				return invocation;
			}

			invocation = JExpr.invoke(invocation, formatSetterName("set", setterMethodField));

			invocation.arg(createExpression(value, context));
		}

		return invocation;
	}

	static
	private String formatSetterName(String prefix, Field field){
		String name = field.getName();

		return prefix + (name.substring(0, 1)).toUpperCase() + name.substring(1);
	}
}