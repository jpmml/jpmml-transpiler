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

import java.util.List;
import java.util.Map;

import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JStatement;
import org.jpmml.evaluator.JavaExpression;
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
	protected JFieldVar createListConstant(String name, JClass elementType, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		JFieldVar constant = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(List.class, elementType), name);

		return constant;
	}

	static
	protected JFieldVar createMapConstant(String name, JClass keyType, JClass valueType, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		JFieldVar constant = owner.field(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(Map.class, keyType, valueType), name);

		return constant;
	}

	static
	protected JDefinedClass getResourceOwner(TranslationContext context){

		try {
			return context.getOwner(JavaExpression.class);
		} catch(IllegalArgumentException iae){
			return context.getOwner(JavaModel.class);
		}
	}
}