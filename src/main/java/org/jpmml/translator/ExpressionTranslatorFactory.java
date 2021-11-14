/*
 * Copyright (c) 2021 Villu Ruusmann
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

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.ListMultimap;
import org.dmg.pmml.Expression;
import org.jpmml.evaluator.InvalidMarkupException;
import org.jpmml.evaluator.PMMLException;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.UnsupportedMarkupException;

public class ExpressionTranslatorFactory {

	transient
	private ListMultimap<Class<? extends Expression>, Class<? extends ExpressionTranslator<?>>> expressionTranslatorClazzes = null;


	protected ExpressionTranslatorFactory(){
	}

	public ExpressionTranslator<?> newExpressionTranslator(Expression expression){

		try {
			List<? extends Class<? extends ExpressionTranslator<?>>> expressionTranslatorClazzes = getExpressionTranslatorClasses(expression.getClass());

			for(Class<? extends ExpressionTranslator<?>> expressionTranslatorClazz : expressionTranslatorClazzes){
				Constructor<?> constructor = findConstructor(expressionTranslatorClazz);

				try {
					return (ExpressionTranslator<?>)constructor.newInstance(expression);
				} catch(InvocationTargetException ite){
					Throwable cause = ite.getCause();

					if(cause instanceof PMMLException){

						// Invalid here, invalid everywhere
						if(cause instanceof InvalidMarkupException){
							// Ignored
						} else

						// Unsupported here, might be supported somewhere else
						if(cause instanceof UnsupportedMarkupException){
							continue;
						}

						throw (PMMLException)cause;
					}

					throw ite;
				}
			}
		} catch(ReflectiveOperationException | IOException e){
			throw new IllegalArgumentException(e);
		}

		throw new UnsupportedElementException(expression);
	}

	public List<Class<? extends ExpressionTranslator<?>>> getExpressionTranslatorClasses(Class<? extends Expression> expressionClazz) throws ClassNotFoundException, IOException {
		ListMultimap<Class<? extends Expression>, Class<? extends ExpressionTranslator<?>>> expressionTranslatorClazzes = getExpressionTranslatorClasses();

		while(expressionClazz != null){

			if(expressionTranslatorClazzes.containsKey(expressionClazz)){
				return expressionTranslatorClazzes.get(expressionClazz);
			}

			Class<?> expressionSuperClazz = expressionClazz.getSuperclass();

			if(!(Expression.class).isAssignableFrom(expressionSuperClazz)){
				break;
			}

			expressionClazz = expressionSuperClazz.asSubclass(Expression.class);
		}

		return Collections.emptyList();
	}

	public ListMultimap<Class<? extends Expression>, Class<? extends ExpressionTranslator<?>>> getExpressionTranslatorClasses() throws ClassNotFoundException, IOException {

		if(this.expressionTranslatorClazzes == null){
			this.expressionTranslatorClazzes = loadExpressionTranslatorClasses();
		}

		return this.expressionTranslatorClazzes;
	}

	static
	public ExpressionTranslatorFactory getInstance(){
		return ExpressionTranslatorFactory.INSTANCE;
	}

	static
	private ListMultimap<Class<? extends Expression>, Class<? extends ExpressionTranslator<?>>> loadExpressionTranslatorClasses() throws ClassNotFoundException, IOException {
		Thread thread = Thread.currentThread();

		ClassLoader clazzLoader = thread.getContextClassLoader();
		if(clazzLoader == null){
			clazzLoader = ClassLoader.getSystemClassLoader();
		}

		ListMultimap<Class<? extends Expression>, Class<? extends ExpressionTranslator<?>>> result = ArrayListMultimap.create();

		Enumeration<URL> urls = clazzLoader.getResources("META-INF/services/" + ExpressionTranslator.class.getName());

		while(urls.hasMoreElements()){
			URL url = urls.nextElement();

			try(InputStream is = url.openStream()){
				List<? extends Class<? extends ExpressionTranslator<?>>> expressionTranslatorClazzes = (List)loadServiceProviderClasses(is, clazzLoader, ExpressionTranslator.class);

				for(Class<? extends ExpressionTranslator<?>> expressionTranslatorClazz : expressionTranslatorClazzes){
					Class<? extends Expression> expressionClazz = findExpressionParameter(expressionTranslatorClazz);

					result.put(expressionClazz, expressionTranslatorClazz);
				}
			}
		}

		return result;
	}

	static
	private <S> List<Class<? extends S>> loadServiceProviderClasses(InputStream is, ClassLoader clazzLoader, Class<S> serviceClazz) throws ClassNotFoundException, IOException {
		List<Class<? extends S>> result = new ArrayList<>();

		BufferedReader reader = new BufferedReader(new InputStreamReader(is, "UTF-8"), 1024);

		while(true){
			String line = reader.readLine();

			if(line == null){
				break;
			}

			int hash = line.indexOf('#');
			if(hash > -1){
				line = line.substring(0, hash);
			}

			line = line.trim();

			if(line.isEmpty()){
				continue;
			}

			Class<?> serviceProviderClazz = Class.forName(line, false, clazzLoader);

			if(!(serviceClazz).isAssignableFrom(serviceProviderClazz)){
				throw new IllegalArgumentException(line);
			}

			result.add((Class)serviceProviderClazz);
		}

		reader.close();

		return result;
	}

	static
	private Constructor<?> findConstructor(Class<? extends ExpressionTranslator<?>> expressionTranslatorClazz) throws NoSuchMethodException {
		Constructor<?>[] constructors = expressionTranslatorClazz.getConstructors();

		for(Constructor<?> constructor : constructors){
			Class<?>[] parameterTypes = constructor.getParameterTypes();

			if(parameterTypes.length != 1){
				continue;
			} // End if

			if((Expression.class).isAssignableFrom(parameterTypes[0])){
				return constructor;
			}
		}

		throw new NoSuchMethodException();
	}

	static
	private Class<? extends Expression> findExpressionParameter(Class<? extends ExpressionTranslator<?>> expressionTranslatorClazz){
		Class<?> clazz = expressionTranslatorClazz;

		while(clazz != null){
			Class<?> superClazz = clazz.getSuperclass();

			if((ExpressionTranslator.class).equals(superClazz)){
				ParameterizedType parameterizedType = (ParameterizedType)clazz.getGenericSuperclass();

				Type[] arguments = parameterizedType.getActualTypeArguments();
				if(arguments.length != 1){
					throw new IllegalArgumentException(clazz.getName());
				}

				Class<?> argumentClazz = (Class<?>)arguments[0];

				return argumentClazz.asSubclass(Expression.class);
			}

			clazz = superClazz;
		}

		throw new IllegalArgumentException(expressionTranslatorClazz.getName());
	}

	private static final ExpressionTranslatorFactory INSTANCE = new ExpressionTranslatorFactory();
}