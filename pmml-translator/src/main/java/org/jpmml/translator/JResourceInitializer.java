/*
 * Copyright (c) 2025 Villu Ruusmann
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

import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.jpmml.evaluator.TokenizedString;

abstract
public class JResourceInitializer extends JClassInitializer {

	private JBlock tryBody = new JBlock();

	private JVar ioeVar = null;

	private JBlock catchBody = new JBlock();


	public JResourceInitializer(TranslationContext context){
		super(context);
	}

	abstract
	public JInvocation initQNameArray(QName[] names);

	abstract
	public JInvocation initTokenizedStringArray(TokenizedString[] tokenizedStrings);

	abstract
	public JInvocation initObjectArray(JType type, Object[] values);

	abstract
	public JInvocation initNumberArrayList(JType type, List<Number[]> elements);

	abstract
	public JInvocation initNumberMatrixList(JType type, List<Number[][]> elements, int length);

	abstract
	public JInvocation initNumberMap(JType keyType, JType valueType, Map<?, Number> map);

	@Override
	public void add(JStatement statement){
		this.tryBody.add(statement);
	}

	public void assign(JVar variable, JExpression expr){
		this.tryBody.assign(variable, expr);
	}

	public JStatement createTryWithResources(JVar resourceVar){
		TranslationContext context = getContext();

		JBlock catchStmt = new JBlock(false, false);

		this.ioeVar = catchStmt.decl(context.ref(IOException.class), "ioe");

		this.catchBody._throw(context._new(RuntimeException.class, this.ioeVar));

		JStatement tryWithResources = new JStatement(){

			@Override
			public void state(JFormatter formatter){
				formatter
					.p("try(")
					.b(resourceVar)
					.p(")");

				formatter.g(JResourceInitializer.this.tryBody);

				formatter
					.p("catch(")
					.b(JResourceInitializer.this.ioeVar)
					.p(")");

				formatter.g(JResourceInitializer.this.catchBody);

				formatter.nl();
			}
		};

		return tryWithResources;
	}

	public JFieldVar initTokenizedStringArray(String name, TokenizedString[] tokenizedStrings){
		TranslationContext context = getContext();

		JClass tokenizedStringClazz = context.ref(TokenizedString.class);

		JFieldVar constant = createConstant(name, tokenizedStringClazz.array(), context);

		assign(constant, initTokenizedStringArray(tokenizedStrings));

		return constant;
	}

	public JFieldVar initNumberList(String name, Class<? extends Number> clazz, List<? extends Number> values){
		TranslationContext context = getContext();

		JClass type = context.ref(clazz);

		JFieldVar constant = createListConstant(name, type, context);

		JInvocation invocation = initObjectArray(type, toNumbers(clazz, values.toArray(new Number[values.size()])));

		invocation = context.staticInvoke(Arrays.class, "asList", invocation);

		assign(constant, invocation);

		return constant;
	}

	public JFieldVar initNumberArrayList(String name, Class<? extends Number> clazz, List<Number[]> elements){
		TranslationContext context = getContext();

		JClass type = (context.ref(clazz)).array();

		JFieldVar constant = createListConstant(name, type, context);

		elements = elements.stream()
			.map(element -> toNumbers(clazz, element))
			.collect(Collectors.toList());

		assign(constant, initNumberArrayList(type, elements));

		return constant;
	}

	public JFieldVar initNumberMatrixList(String name, Class<? extends Number> clazz, List<Number[][]> elements, int length){
		TranslationContext context = getContext();

		JClass type = (context.ref(clazz)).array().array();

		JFieldVar constant = createListConstant(name, type, context);

		elements = elements.stream()
			.map(element -> {
				return Arrays.stream(element)
					.map(row -> toNumbers(clazz, row))
					.toArray(Number[][]::new);
			})
			.collect(Collectors.toList());

		assign(constant, initNumberMatrixList(type, elements, length));

		return constant;
	}

	public JFieldVar initNumberMap(String name, Class<? extends Number> valueClazz, Map<?, ? extends Number> map){
		TranslationContext context = getContext();

		Collection<?> keys = map.keySet();

		Class<?> keysClazz = getValueClass(keys);

		JClass keyType = context.ref(keysClazz);
		JClass valueType = context.ref(valueClazz);

		JFieldVar constant = createMapConstant(name, keyType, valueType, context);

		Map<Object, Number> numberMap = new LinkedHashMap<>();

		Collection<? extends Map.Entry<?, ? extends Number>> entries = map.entrySet();
		for(Map.Entry<?, ? extends Number> entry : entries){
			numberMap.put(entry.getKey(), toNumber(valueClazz, entry.getValue()));
		}

		assign(constant, initNumberMap(keyType, valueType, numberMap));

		return constant;
	}

	public JFieldVar initNumberMap(String name, Map<?, Number> map){
		TranslationContext context = getContext();

		Collection<?> keys = map.keySet();
		Collection<Number> values = map.values();

		Class<?> keysClazz = getValueClass(keys);
		Class<?> valuesClazz = getValueClass(values, Number.class);

		JClass keyType = context.ref(keysClazz);
		JClass valueType = context.ref(valuesClazz);

		JFieldVar constant = createMapConstant(name, keyType, valueType, context);

		assign(constant, initNumberMap(keyType, valueType, map));

		return constant;
	}

	static
	public boolean isExternalizable(Class<?> clazz){

		if(Objects.equals(String.class, clazz)){
			return true;
		} else

		if(Objects.equals(Integer.class, clazz) || Objects.equals(Float.class, clazz) || Objects.equals(Double.class, clazz)){
			return true;
		} else

		{
			return false;
		}
	}

	static
	public boolean isExternalizable(Collection<?> values){
		Class<?> valueClazz = getValueClass(values);

		return isExternalizable(valueClazz);
	}

	static
	public Class<?> getValueClass(Collection<?> values){
		return getValueClass(values, Object.class);
	}

	static
	public Class<?> getValueClass(Collection<?> values, Class<?> defaultClazz){
		Set<Class<?>> valueClazzes = values.stream()
			.map(value -> value.getClass())
			.collect(Collectors.toSet());

		if(valueClazzes.size() == 1){
			return Iterables.getOnlyElement(valueClazzes);
		}

		return defaultClazz;
	}

	static
	private Number[] toNumbers(Class<? extends Number> clazz, Number[] values){
		return Arrays.stream(values)
			.map(value -> toNumber(clazz, value))
			.toArray(Number[]::new);
	}

	static
	private Number toNumber(Class<? extends Number> clazz, Number value){

		if(Objects.equals(Integer.class, clazz)){
			return value.intValue();
		} else

		if(Objects.equals(Float.class, clazz)){
			return value.floatValue();
		} else

		if(Objects.equals(Double.class, clazz)){
			return value.doubleValue();
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	protected String toSingular(String string){

		if(string.endsWith("s")){
			string = string.substring(0, string.length() - "s".length());
		}

		return string;
	}
}