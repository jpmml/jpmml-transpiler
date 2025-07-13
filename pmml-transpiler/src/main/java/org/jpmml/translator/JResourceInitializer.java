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

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.jpmml.evaluator.TokenizedString;

abstract
public class JResourceInitializer extends JClassInitializer {

	public JResourceInitializer(TranslationContext context){
		super(context);
	}

	abstract
	public void assign(JVar variable, JExpression expr);

	abstract
	public JInvocation initQNames(QName[] names);

	abstract
	public JInvocation initValues(JType type, Object[] values);

	abstract
	public JInvocation initTokenizedStringLists(TokenizedString[] tokenizedStrings);

	abstract
	public JInvocation initNumbers(JType type, Number[] values);

	abstract
	public JInvocation initNumbersList(JType type, List<Number[]> elements);

	abstract
	public JInvocation initNumberArraysList(JType type, List<Number[][]> elements, int length);

	abstract
	public JInvocation initNumbersMap(JType keyType, JType valueType, Map<?, Number> map);

	public JFieldVar initTokenizedStringLists(String name, TokenizedString[] tokenizedStrings){
		TranslationContext context = getContext();

		JFieldVar constant = createListConstant(name, context.ref(TokenizedString.class), context);

		assign(constant, initTokenizedStringLists(tokenizedStrings));

		return constant;
	}

	public JFieldVar initNumbers(String name, Number[] values){
		TranslationContext context = getContext();

		Class<?> valueClazz = getValueClass(Arrays.asList(values), Number.class);

		JClass type = context.ref(valueClazz);

		JFieldVar constant = createListConstant(name, type, context);

		assign(constant, initNumbers(type, values));

		return constant;
	}

	public JFieldVar initNumbersList(String name, List<Number[]> elements){
		TranslationContext context = getContext();

		Collection<Number> values = elements.stream()
			.flatMap(Arrays::stream)
			.collect(Collectors.toList());

		Class<?> valueClazz = getValueClass(values, Number.class);

		JClass type = (context.ref(valueClazz)).array();

		JFieldVar constant = createListConstant(name, type, context);

		assign(constant, initNumbersList(type, elements));

		return constant;
	}

	public JFieldVar initNumberArraysList(String name, List<Number[][]> elements, int length){
		TranslationContext context = getContext();

		Collection<Number> values = elements.stream()
			.flatMap(Arrays::stream)
			.flatMap(Arrays::stream)
			.collect(Collectors.toList());

		Class<?> valuesClazz = getValueClass(values, Number.class);

		JClass type = (context.ref(valuesClazz)).array().array();

		JFieldVar constant = createListConstant(name, type, context);

		assign(constant, initNumberArraysList(type, elements, length));

		return constant;
	}

	public JFieldVar initNumbersMap(String name, Map<?, Number> map){
		TranslationContext context = getContext();

		Collection<?> keys = map.keySet();
		Collection<Number> values = map.values();

		Class<?> keysClazz = getValueClass(keys);
		Class<?> valuesClazz = getValueClass(values, Number.class);

		JClass keyType = context.ref(keysClazz);
		JClass valueType = context.ref(valuesClazz);

		JFieldVar constant = createMapConstant(name, keyType, valueType, context);

		assign(constant, initNumbersMap(keyType, valueType, map));

		return constant;
	}

	static
	public boolean isExternalizable(Class<?> clazz){

		if(Objects.equals(clazz, String.class)){
			return true;
		} else

		if(Objects.equals(clazz, Integer.class) || Objects.equals(clazz, Float.class) || Objects.equals(clazz, Double.class)){
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
}