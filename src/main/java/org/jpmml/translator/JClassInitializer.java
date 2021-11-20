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

import java.util.ArrayList;
import java.util.List;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import org.jpmml.evaluator.java.JavaModel;

abstract
class JClassInitializer {

	private TranslationContext context = null;


	JClassInitializer(TranslationContext context){
		setContext(context);
	}

	abstract
	public void add(JStatement statement);

	public TranslationContext getContext(){
		return this.context;
	}

	private void setContext(TranslationContext context){
		this.context = context;
	}

	static
	protected JMethod createMethod(String name, TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		JMethod method = owner.method(JMod.PRIVATE | JMod.STATIC, void.class, "init" + (name.substring(0, 1)).toUpperCase() + name.substring(1));

		return method;
	}

	static
	protected JFieldVar createConstant(String name, JType type, TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		JFieldVar constant = owner.field(Modifiers.MEMBER_PRIVATE, context.ref(List.class).narrow(type), name, context._new(ArrayList.class));

		return constant;
	}
}