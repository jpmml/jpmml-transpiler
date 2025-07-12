/*
 * Copyright (c) 2019 Villu Ruusmann
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
import java.util.Arrays;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;

public class JDirectInitializer extends JClassInitializer {

	public JDirectInitializer(TranslationContext context){
		super(context);
	}

	@Override
	public void add(JStatement statement){
		TranslationContext context = getContext();

		JDefinedClass owner = context.getOwner();

		JBlock init = owner.init();

		init.add(statement);
	}

	public JFieldVar initLambdas(String name, JClass type, List<JMethod> methods){
		TranslationContext context = getContext();

		JFieldVar constant = createListConstant(name, type, context);

		JExpression[] lambdas = methods.stream()
			.map(method -> JExpr.direct(formatLambda(method)))
			.toArray(JExpression[]::new);

		constant.init(context.staticInvoke(Arrays.class, "asList", lambdas));

		return constant;
	}

	public JFieldVar initTargetCategories(String name, List<?> categories){
		TranslationContext context = getContext();

		JFieldVar constant = createListConstant(name, context.ref(Object.class), context);

		JExpression[] literals = categories.stream()
			.map(category -> PMMLObjectUtil.createExpression(category, context))
			.toArray(JExpression[]::new);

		constant.init(context.staticInvoke(Arrays.class, "asList", literals));

		return constant;
	}

	static
	private String formatLambda(JMethod method){
		JDefinedClass outer;

		try {
			Field field = JMethod.class.getDeclaredField("outer");
			if(!field.isAccessible()){
				field.setAccessible(true);
			}

			outer = (JDefinedClass)field.get(method);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}

		return outer.name() + "::" + method.name();
	}
}