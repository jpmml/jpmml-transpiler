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
package com.jpmml.translator;

import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class JVarBuilder {

	private TranslationContext context = null;

	private JVar variable = null;


	public JVarBuilder(TranslationContext context){
		setContext(context);
	}

	public JVarBuilder construct(Class<?> type, String name, Object... args){
		TranslationContext context = getContext();

		return construct(context.ref(type), name, args);
	}

	public JVarBuilder construct(JType type, String name, Object... args){
		TranslationContext context = getContext();

		JInvocation invocation = JExpr._new(type);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, context));
		}

		return declare(type, name, invocation);
	}

	public JVarBuilder declare(Class<?> type, String name, JExpression init){
		TranslationContext context = getContext();

		return declare(context.ref(type), name, init);
	}

	public JVarBuilder declare(JType type, String name, JExpression init){
		TranslationContext context = getContext();

		JVar variable = context.declare(type, name, init);

		setVariable(variable);

		return this;
	}

	public JVarBuilder update(String method, Object... args){
		TranslationContext context = getContext();

		JVar variable = ensureVariable();

		JInvocation invocation = variable.invoke(method);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, context));
		}

		context.add(invocation);

		return this;
	}

	public JVarBuilder staticUpdate(Class<?> type, String method, Object... args){
		TranslationContext context = getContext();

		JVar variable = ensureVariable();

		JInvocation invocation = context.ref(type).staticInvoke(method);

		for(Object arg : args){
			invocation = invocation.arg(PMMLObjectUtil.createExpression(arg, context));
		}

		invocation = invocation.arg(variable);

		context.add(invocation);

		return this;
	}

	public JVar ensureVariable(){
		JVar variable = getVariable();
		if(variable == null){
			throw new IllegalStateException();
		}

		return variable;
	}

	public TranslationContext getContext(){
		return this.context;
	}

	private void setContext(TranslationContext context){
		this.context = context;
	}

	public JVar getVariable(){
		return this.variable;
	}

	private void setVariable(JVar variable){
		this.variable = variable;
	}
}