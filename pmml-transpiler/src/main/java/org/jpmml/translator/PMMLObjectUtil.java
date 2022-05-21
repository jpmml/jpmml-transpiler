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
package org.jpmml.translator;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.XMLConstants;
import javax.xml.namespace.QName;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.ListMultimap;
import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JClassAlreadyExistsException;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldRef;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import jakarta.xml.bind.JAXBElement;
import org.dmg.pmml.DataDictionary;
import org.dmg.pmml.DefineFunction;
import org.dmg.pmml.Expression;
import org.dmg.pmml.False;
import org.dmg.pmml.HasDiscreteDomain;
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
import org.dmg.pmml.True;
import org.dmg.pmml.Value;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.jpmml.evaluator.java.JavaModel;
import org.jpmml.model.PMMLException;
import org.jpmml.model.ReflectionUtil;
import org.w3c.dom.Element;

public class PMMLObjectUtil {

	private PMMLObjectUtil(){
	}

	static
	public JDefinedClass createClass(PMMLObject object, String className, TranslationContext context){
		JCodeModel codeModel = context.getCodeModel();

		Class<?> clazz = object.getClass();

		JDefinedClass definedClazz;

		try {
			definedClazz = codeModel._class(className != null ? className : IdentifierUtil.create(clazz.getSimpleName(), object));
		} catch(JClassAlreadyExistsException jcaee){
			throw new IllegalArgumentException(jcaee);
		}

		definedClazz._extends(clazz);

		JMethod loaderMethod = definedClazz.method(Modifiers.PRIVATE_STATIC_FINAL, void.class, "ensureLoaded");

		JBlock init = definedClazz.init();

		// The loader method of a top level class is invoked from its static initializer
		init.add(definedClazz.staticInvoke(loaderMethod));

		return definedClazz;
	}

	static
	public JDefinedClass createMemberClass(int mods, String name, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JDefinedClass definedClazz;

		try {
			definedClazz = owner._class(mods, name);
		} catch(JClassAlreadyExistsException jcaee){
			throw new IllegalArgumentException(jcaee);
		}

		JMethod loaderMethod = definedClazz.method(Modifiers.PRIVATE_STATIC_FINAL, void.class, "ensureLoaded");

		JMethod ownerLoaderMethod = owner.getMethod("ensureLoaded", new JType[0]);

		JBlock block = ownerLoaderMethod.body();

		// The loader method of a member class is invoked from the loader method of its enclosing class
		block.add(definedClazz.staticInvoke(loaderMethod));

		return definedClazz;
	}

	static
	public JMethod createDefaultConstructor(PMMLObject object, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JMethod constructor = owner.constructor(Modifiers.PUBLIC);

		JBlock block = constructor.body();

		block.add(constructObject(object, JExpr.invoke("super"), context));
		block.add(initializeObject(object, null, context));

		return constructor;
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

		JMethod method = owner.method((owner.isAnonymous() ? Modifiers.PRIVATE_FINAL : Modifiers.PRIVATE_STATIC_FINAL), clazz, IdentifierUtil.create("build" + clazz.getSimpleName(), object));

		JBlock block = method.body();

		block._return(createObject(object, context));

		return method;
	}

	static
	public JMethod createArrayBuilderMethod(Class<?> clazz, List<?> objects, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JMethod method = owner.method((owner.isAnonymous() ? Modifiers.PRIVATE_FINAL : Modifiers.PRIVATE_STATIC_FINAL), context.ref(clazz).array(), IdentifierUtil.create("build" + clazz.getSimpleName() + "Array", objects));

		JBlock block = method.body();

		JVar resultVar = block.decl(context.ref(List.class).narrow(clazz), "result", context._new(ArrayList.class, objects.size()));

		for(int i = 0; i < objects.size(); i += PMMLObjectUtil.CHUNK_SIZE){
			List<?> chunk = objects.subList(i, Math.min(i + PMMLObjectUtil.CHUNK_SIZE, objects.size()));

			JMethod chunkMethod = owner.method((method.mods()).getValue(), method.type(), method.name() + "$" + i + "_" + (i + chunk.size()));

			try {
				context.pushScope(new MethodScope(chunkMethod));

				JArray array = JExpr.newArray(context.ref(clazz));

				for(Object object : chunk){
					array.add(createExpression(object, context));
				}

				context._return(array);
			} finally {
				context.popScope();
			}

			JInvocation invocation = context.staticInvoke(Collections.class, "addAll", resultVar, JExpr.invoke(chunkMethod));

			block.add(invocation);
		}

		block._return(resultVar.invoke("toArray").arg(JExpr.newArray(context.ref(clazz), resultVar.invoke("size"))));

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
		Template template = Template.getTemplate(object.getClass());

		return template.constructObject(object, invocation, context);
	}

	static
	public JInvocation initializeObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		Template template = Template.getTemplate(object.getClass());

		return template.initializeObject(object, invocation, context);
	}

	static
	public JInvocation initializeJavaModel(Model model, JInvocation invocation, TranslationContext context){
		JavaModelTemplate javaModelTemplate = (JavaModelTemplate)Template.getTemplate(JavaModel.class);

		return javaModelTemplate.initializeObject(model, invocation, context);
	}

	static
	public JExpression createExpression(Object value, TranslationContext context){

		if(value instanceof JExpressionRef){
			JExpressionRef variableRef = (JExpressionRef)value;

			return variableRef.getExpression();
		} else

		if(value instanceof JVarBuilder){
			JVarBuilder variableBuilder = (JVarBuilder)value;

			return variableBuilder.getVariable();
		} // End if

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

		if((QName.class).isAssignableFrom(clazz)){
			QName xmlName = (QName)value;

			return context.constantXmlName(xmlName);
		} else

		if((PMMLObject.class).isAssignableFrom(clazz)){
			PMMLObject pmmlObject = (PMMLObject)value;

			if((True.INSTANCE).equals(pmmlObject)){
				JClass trueClass = context.ref(True.class);

				return trueClass.staticRef("INSTANCE");
			} else

			if((False.INSTANCE).equals(pmmlObject)){
				JClass falseClass = context.ref(False.class);

				return falseClass.staticRef("INSTANCE");
			}

			Class<?> pmmlObjectClazz = pmmlObject.getClass();
			if(pmmlObjectClazz.isAnonymousClass()){
				throw new IllegalArgumentException();
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
				org.dmg.pmml.Field field = (org.dmg.pmml.Field)pmmlObject;

				ListMultimap<Value.Property, Object> discreteValues = ArrayListMultimap.create();

				List<Value> suppressedPmmlValues = null;

				discreteValues:
				if(field instanceof HasDiscreteDomain){
					HasDiscreteDomain<?> hasDiscreteDomain = (HasDiscreteDomain<?>)field;

					if(hasDiscreteDomain.hasValues()){
						List<Value> pmmlValues = hasDiscreteDomain.getValues();

						for(Value pmmlValue : pmmlValues){
							String displayName = pmmlValue.getDisplayValue();

							if(displayName != null || pmmlValue.hasExtensions()){
								discreteValues.clear();

								break discreteValues;
							}

							discreteValues.put(pmmlValue.getProperty(), pmmlValue.requireValue());
						}

						suppressedPmmlValues = new ArrayList<>(pmmlValues);

						ensureEditableValues((org.dmg.pmml.Field & HasDiscreteDomain)field).clear();
					}
				}

				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				if(field instanceof HasDiscreteDomain){
					HasDiscreteDomain<?> hasDiscreteDomain = (HasDiscreteDomain<?>)field;

					if(suppressedPmmlValues != null){
						ensureEditableValues((org.dmg.pmml.Field & HasDiscreteDomain)field).addAll(suppressedPmmlValues);
					}

					JBlock body = builderMethod.body();

					JStatement _return;

					Field returnExprField;

					JInvocation invocation;

					try {
						Class<? extends JStatement> returnClazz = (Class)Class.forName("com.sun.codemodel.JReturn");

						_return = returnClazz.cast(Iterables.getLast(body.getContents()));

						returnExprField = returnClazz.getDeclaredField("expr");
						if(!returnExprField.isAccessible()){
							returnExprField.setAccessible(true);
						}

						invocation = (JInvocation)returnExprField.get(_return);
					} catch(ReflectiveOperationException roe){
						throw new IllegalArgumentException(roe);
					}

					Collection<Map.Entry<Value.Property, Collection<Object>>> entries = (discreteValues.asMap()).entrySet();
					for(Map.Entry<Value.Property, Collection<Object>> entry : entries){
						Value.Property property = entry.getKey();
						List<?> values = (List<?>)entry.getValue();

						if(values != null && !values.isEmpty()){
							Set<Class<?>> valueClazzes = values.stream()
								.map(argument -> argument.getClass())
								.collect(Collectors.toSet());

							Class<?> valueClazz;

							if(valueClazzes.size() == 1){
								valueClazz = Iterables.getOnlyElement(valueClazzes);
							} else

							{
								valueClazz = Object.class;
							}

							invocation = invocation.invoke("addValues").arg(createExpression(property, context));

							if((values.size() > 2) && (Objects.equals(valueClazz, String.class) || Objects.equals(valueClazz, Double.class) || Objects.equals(valueClazz, Float.class))){
								JFieldRef valuesFieldRef = context.constantValues((Class)valueClazz, IdentifierUtil.create((property.name()).toLowerCase(), field), values);

								invocation = invocation.arg(JExpr.cast(context.ref(Object[].class), valuesFieldRef));
							} else

							{
								invocation = initializeArray(valueClazz, values, invocation, context);
							}
						}
					}

					try {
						returnExprField.set(_return, invocation);
					} catch(ReflectiveOperationException roe){
						throw new IllegalArgumentException(roe);
					}
				}

				return JExpr.invoke(builderMethod);
			} else

			if(pmmlObject instanceof DefineFunction){
				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			if((pmmlObject instanceof Segmentation) || (pmmlObject instanceof Segment)){
				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			if(pmmlObject instanceof Model){
				PMML pmml = context.getPMML();
				Model model = (Model)pmmlObject;

				ModelTranslatorFactory modelTranslatorFactory = ModelTranslatorFactory.getInstance();

				try {
					ModelTranslator<?> modelTranslator = modelTranslatorFactory.newModelTranslator(pmml, model);

					return modelTranslator.translate(context);
				} catch(PMMLException pe){
					context.addIssue(pe);
				}

				JMethod builderMethod = createBuilderMethod(pmmlObject, context);

				return JExpr.invoke(builderMethod);
			} else

			if(pmmlObject instanceof Expression){
				Expression expression = (Expression)pmmlObject;

				ExpressionTranslatorFactory expressionTranslatorFactory = ExpressionTranslatorFactory.getInstance();

				try {
					ExpressionTranslator<?> expressionTranslator = expressionTranslatorFactory.newExpressionTranslator(expression);

					return expressionTranslator.translate(context);
				} catch(PMMLException pe){
					context.addIssue(pe);
				}

				return createObject(pmmlObject, context);
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

			String namespaceURI = domElement.getNamespaceURI();
			if(namespaceURI == null){
				namespaceURI = XMLConstants.NULL_NS_URI;
			}

			String localName = domElement.getLocalName();
			if(localName == null){
				localName = "";
			}

			String prefix = domElement.getPrefix();
			if(prefix == null){
				prefix = XMLConstants.DEFAULT_NS_PREFIX;
			}

			QName xmlName = new QName(namespaceURI, localName, prefix);
			String stringValue = domElement.getTextContent();

			return createExpression(new JAXBElement<>(xmlName, String.class, stringValue), context);
		}

		throw new IllegalArgumentException(clazz.getName());
	}

	static
	public void addValueConstructorParam(Field valueConstructorField, Object value, JInvocation invocation, TranslationContext context){

		if(value == null){
			invocation.arg(JExpr._null());

			return;
		} // End if

		if(value instanceof List){
			List<?> elements = (List<?>)value;

			JInvocation listInvocation = context.staticInvoke(Arrays.class, "asList");

			initializeArray(valueConstructorField, elements, listInvocation, context);

			invocation.arg(listInvocation);
		} else

		{
			invocation.arg(createExpression(value, context));
		}
	}

	static
	public JInvocation addSetterMethod(Field setterMethodField, Object value, JInvocation invocation, TranslationContext context){

		if(value == null){
			return invocation;
		} // End if

		if(value instanceof List){
			List<?> elements = (List<?>)value;

			if(elements.isEmpty()){
				return invocation;
			}

			invocation = JExpr.invoke(invocation, formatSetterName("add", setterMethodField));

			initializeArray(setterMethodField, elements, invocation, context);
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
	private JInvocation initializeArray(Field field, List<?> elements, JInvocation invocation, TranslationContext context){
		Class<?> listClazz = field.getType();

		if(!(List.class).equals(listClazz)){
			throw new IllegalArgumentException();
		}

		ParameterizedType listType = (ParameterizedType)field.getGenericType();

		Type listElementType = listType.getActualTypeArguments()[0];

		return initializeArray((Class)listElementType, elements, invocation, context);
	}

	static
	private JInvocation initializeArray(Class<?> listElementType, List<?> elements, JInvocation invocation, TranslationContext context){

		if(elements.size() <= PMMLObjectUtil.CHUNK_SIZE){

			for(Object element : elements){
				invocation.arg(createExpression(element, context));
			}
		} else

		{
			JMethod method = createArrayBuilderMethod((Class)listElementType, elements, context);

			invocation.arg(JExpr.invoke(method));
		}

		return invocation;
	}

	static
	private String formatSetterName(String prefix, Field field){
		String name = field.getName();

		return prefix + (name.substring(0, 1)).toUpperCase() + name.substring(1);
	}

	static
	private <F extends org.dmg.pmml.Field<F> & HasDiscreteDomain<F>> List<Value> ensureEditableValues(F field){
		Field valuesField = ReflectionUtil.getField(field.getClass(), "values");

		List<Value> values = ReflectionUtil.getFieldValue(valuesField, field);

		if(!(values instanceof ArrayList)){
			values = new ArrayList<>(values);

			ReflectionUtil.setFieldValue(valuesField, field, values);
		}

		return values;
	}

	private static final int CHUNK_SIZE = 256;
}