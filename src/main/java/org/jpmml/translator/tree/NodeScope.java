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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import com.sun.codemodel.JBlock;
import org.jpmml.translator.JBlockUtil;
import org.jpmml.translator.JIfStatement;
import org.jpmml.translator.Scope;

public class NodeScope extends Scope {

	public NodeScope(JIfStatement ifStatement){
		super(ifStatement._then());
	}

	Object chainContent(){
		JBlock block = getBlock();

		List<?> objects = block.getContents();
		if(objects.size() == 0){
			throw new IllegalStateException();
		} else

		if(objects.size() == 1){
			return objects.get(0);
		}

		Object result = objects.get(objects.size() - 1);

		List<?> chainedObjects = chainContent(objects);

		JBlockUtil.clear(block);
		JBlockUtil.insertAll(block, chainedObjects);

		return result;
	}

	static
	List<Object> chainContent(List<?> objects){
		List<Object> result = new ArrayList<>();

		JIfStatement prevIfStatement = null;

		Iterator<?> objectIt = objects.iterator();

		while((prevIfStatement == null) && objectIt.hasNext()){
			Object object = objectIt.next();

			if(object instanceof JIfStatement){
				JIfStatement ifStatement = (JIfStatement)object;

				result.add(ifStatement);

				prevIfStatement = ifStatement;
			} else

			{
				result.add(object);
			}
		} // End while

		while(objectIt.hasNext()){
			Object object = objectIt.next();

			if(object instanceof JIfStatement){
				JIfStatement ifStatement = (JIfStatement)object;

				if(prevIfStatement.hasElse()){
					JBlock elseBlock = prevIfStatement._else();

					elseBlock.add(ifStatement);

					prevIfStatement = ifStatement;
				} else

				{
					prevIfStatement = prevIfStatement._elseif(ifStatement);
				}
			} else

			{
				JBlock elseBlock = prevIfStatement._else();

				JBlockUtil.insert(elseBlock, object);
			}
		}

		return result;
	}
}