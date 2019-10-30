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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JType;

abstract
class JClassInitializer {

	private TranslationContext context = null;


	JClassInitializer(TranslationContext context){
		setContext(context);
	}

	public TranslationContext getContext(){
		return this.context;
	}

	private void setContext(TranslationContext context){
		this.context = context;
	}

	static
	protected JFieldVar createConstant(String name, JType type, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JFieldVar constant = owner.field(ModelTranslator.MEMBER_PRIVATE, (context.ref(List.class)).narrow(type), name, JExpr._new((context.ref(ArrayList.class)).narrow(Collections.emptyList())));

		return constant;
	}
}