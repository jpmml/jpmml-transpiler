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
package org.jpmml.translator.tree;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JConditional;
import com.sun.codemodel.JVar;
import org.jpmml.translator.Scope;

public class NodeScope extends Scope {

	public NodeScope(JConditional conditional){
		super(conditional._then());
	}

	void chainContent(){
		JBlock block = getBlock();

		List<Object> objects = new ArrayList<>(block.getContents());

		clear(block);

		JConditional prevConditional = null;

		Iterator<Object> objectIt = objects.iterator();

		if(objectIt.hasNext()){
			Object object = objectIt.next();

			if(object instanceof JConditional){
				JConditional conditional = (JConditional)object;

				block.add(conditional);

				prevConditional = conditional;
			} else

			if(object instanceof JVar){
				JVar variable = (JVar)object;

				insert(block, variable);
			} else

			{
				throw new IllegalStateException();
			}
		} // End if

		if((prevConditional == null) && objectIt.hasNext()){
			JConditional conditional = (JConditional)objectIt.next();

			block.add(conditional);

			prevConditional = conditional;
		}

		while(objectIt.hasNext()){
			Object object = objectIt.next();

			JBlock elseBlock = prevConditional._else();

			if(object instanceof JConditional){
				JConditional conditional = (JConditional)object;

				if(elseBlock.isEmpty()){
					flatten(elseBlock);
				}

				elseBlock.add(conditional);

				prevConditional = conditional;
			} else

			if(object instanceof JVar){
				JVar variable = (JVar)object;

				insert(elseBlock, variable);
			} else

			{
				throw new IllegalStateException();
			}
		}
	}

	static
	private void clear(JBlock block){
		List<?> content;

		try {
			Field contentField = JBlock.class.getDeclaredField("content");
			if(!contentField.isAccessible()){
				contentField.setAccessible(true);
			}

			content = (List<?>)contentField.get(block);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}

		content.clear();

		block.pos(content.size());
	}

	static
	private void insert(JBlock block, Object object){

		try {
			Method insertMethod = JBlock.class.getDeclaredMethod("insert", Object.class);
			if(!insertMethod.isAccessible()){
				insertMethod.setAccessible(true);
			}

			insertMethod.invoke(block, object);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}

	static
	private void flatten(JBlock block){

		try {
			Field bracesRequiredField = JBlock.class.getDeclaredField("bracesRequired");
			if(!bracesRequiredField.isAccessible()){
				bracesRequiredField.setAccessible(true);
			}

			bracesRequiredField.set(block, false);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		} // End try

		try {
			Field indentRequiredField = JBlock.class.getDeclaredField("indentRequired");
			if(!indentRequiredField.isAccessible()){
				indentRequiredField.setAccessible(true);
			}

			indentRequiredField.set(block, false);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}
}