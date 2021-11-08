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

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JStatement;

public class JBlockUtil {

	private JBlockUtil(){
	}

	static
	public void clear(JBlock block){
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
	public <T> T insert(JBlock block, T object){

		try {
			Method insertMethod = JBlock.class.getDeclaredMethod("insert", Object.class);
			if(!insertMethod.isAccessible()){
				insertMethod.setAccessible(true);
			}

			insertMethod.invoke(block, object);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}

		return object;
	}

	static
	public void insertAll(JBlock block, List<?> objects){

		for(Object object : objects){

			if(object instanceof JStatement){
				JStatement statement = (JStatement)object;

				block.add(statement);
			} else

			{
				JBlockUtil.insert(block, object);
			}
		}

	}

	static
	public void generateBody(JBlock block, JFormatter formatter){

		try {
			Method generateBodyMethod = JBlock.class.getDeclaredMethod("generateBody", JFormatter.class);
			if(!generateBodyMethod.isAccessible()){
				generateBodyMethod.setAccessible(true);
			}

			generateBodyMethod.invoke(block, formatter);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}
}