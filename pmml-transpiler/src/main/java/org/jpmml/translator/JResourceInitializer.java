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

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.MathContext;
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
	public JFieldVar initTokenizedStringLists(String name, TokenizedString[] tokenizedStrings);

	abstract
	public JFieldVar initNumbers(String name, MathContext mathContext, Number[] values);

	abstract
	public JFieldVar initNumbersList(String name, MathContext mathContext, List<Number[]> elements);

	abstract
	public JFieldVar initNumberArraysList(String name, MathContext mathContext, List<Number[][]> elements, int length);

	abstract
	public JFieldVar initNumbersMap(String name, Map<?, Number> map);

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
		Set<Class<?>> valueClazzes = values.stream()
			.map(value -> value.getClass())
			.collect(Collectors.toSet());

		if(valueClazzes.size() == 1){
			return Iterables.getOnlyElement(valueClazzes);
		}

		return Object.class;
	}
}