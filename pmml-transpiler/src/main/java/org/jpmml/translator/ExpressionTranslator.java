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

import java.util.Objects;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import org.dmg.pmml.Expression;
import org.jpmml.evaluator.EvaluationContext;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.JavaExpression;
import org.jpmml.model.PMMLException;

abstract
public class ExpressionTranslator<E extends Expression> {

	private E expression = null;


	public ExpressionTranslator(E expression){
		setExpression(Objects.requireNonNull(expression));
	}

	abstract
	public void translateExpression(TranslationContext context);

	public JExpression translate(TranslationContext context){
		Expression expression = getExpression();

		JDefinedClass javaExpressionClazz = PMMLObjectUtil.createMemberClass(Modifiers.PUBLIC_STATIC_FINAL, IdentifierUtil.create(JavaExpression.class.getSimpleName(), expression), context);

		javaExpressionClazz._extends(JavaExpression.class);

		try {
			context.pushOwner(javaExpressionClazz);

			createEvaluateMethod(context);
		} catch(PMMLException pe){
			throw pe.ensureContext(expression);
		} finally {
			context.popOwner();
		}

		return context._new(javaExpressionClazz);
	}

	private JMethod createEvaluateMethod(TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JMethod method = owner.method(Modifiers.PUBLIC_FINAL, context.ref(FieldValue.class), "evaluate");
		method.annotate(Override.class);

		method.param(EvaluationContext.class, Scope.VAR_CONTEXT);

		try {
			context.pushScope(new MethodScope(method));

			translateExpression(context);
		} finally {
			context.popScope();
		}

		return method;
	}

	public E getExpression(){
		return this.expression;
	}

	private void setExpression(E expression){
		this.expression = expression;
	}
}