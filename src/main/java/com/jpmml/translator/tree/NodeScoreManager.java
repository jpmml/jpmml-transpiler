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
package com.jpmml.translator.tree;

import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.JResourceInitializer;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;
import com.sun.codemodel.fmt.JBinaryFile;
import org.dmg.pmml.tree.Node;
import org.jpmml.evaluator.ResourceUtil;

public class NodeScoreManager extends ArrayManager<Number> implements ScoreFunction<Number> {

	public NodeScoreManager(JDefinedClass owner, JType componentType, String name){
		super(owner, componentType, name);
	}

	@Override
	public Number apply(Node node){
		Object score = node.getScore();

		return (Number)score;
	}

	@Override
	public JExpression createExpression(Number score){

		if(score instanceof Float){
			return JExpr.lit(score.floatValue());
		} else

		if(score instanceof Double){
			return JExpr.lit(score.doubleValue());
		}

		throw new IllegalArgumentException();
	}

	public void initResource(JBinaryFile binaryFile, JClass resourceUtilClazz, JResourceInitializer resourceInitializer){
		Collection<Number> elements = getElements();

		Number[] values = elements.stream()
			.toArray(Number[]::new);

		String method;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			if(values[0] instanceof Float){
				ResourceUtil.writeFloats(dataOutput, values);

				method = "readFloats";
			} else

			if(values[0] instanceof Double){
				ResourceUtil.writeDoubles(dataOutput, values);

				method = "readDoubles";
			} else

			{
				throw new IllegalArgumentException();
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		resourceInitializer.readNumbers(getArrayVar(), resourceUtilClazz.staticInvoke(method), JExpr.lit(values.length));
	}
}