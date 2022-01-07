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

import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.List;
import java.util.Objects;

import org.dmg.pmml.Expression;
import org.jpmml.evaluator.ServiceFactory;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.evaluator.UnsupportedMarkupException;
import org.jpmml.model.InvalidMarkupException;
import org.jpmml.model.PMMLException;

public class ExpressionTranslatorFactory extends ServiceFactory<Expression, ExpressionTranslator<?>> {

	protected ExpressionTranslatorFactory(){
		super(Expression.class, (Class)ExpressionTranslator.class);
	}

	public ExpressionTranslator<?> newExpressionTranslator(Expression expression){
		Objects.requireNonNull(expression);

		try {
			List<? extends Class<? extends ExpressionTranslator<?>>> expressionTranslatorClazzes = getServiceProviderClasses(expression.getClass());

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

	static
	public ExpressionTranslatorFactory getInstance(){
		return ExpressionTranslatorFactory.INSTANCE;
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

	private static final ExpressionTranslatorFactory INSTANCE = new ExpressionTranslatorFactory();
}