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
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import com.jpmml.translator.ArrayManager;
import com.jpmml.translator.JResourceInitializer;
import com.sun.codemodel.JArray;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JType;
import com.sun.codemodel.fmt.JBinaryFile;
import org.dmg.pmml.ScoreDistribution;
import org.dmg.pmml.tree.Node;
import org.dmg.pmml.tree.PMMLAttributes;
import org.jpmml.evaluator.MissingAttributeException;
import org.jpmml.evaluator.ResourceUtil;
import org.jpmml.evaluator.Value;
import org.jpmml.evaluator.ValueFactory;
import org.jpmml.evaluator.ValueMap;
import org.jpmml.evaluator.ValueUtil;

abstract
public class NodeScoreDistributionManager<V extends Number> extends ArrayManager<List<Number>> implements ScoreFunction<List<Number>> {

	private String[] categories = null;


	public NodeScoreDistributionManager(JDefinedClass owner, JType componentType, String name, String[] categories){
		super(owner, componentType, name);

		setCategories(categories);
	}

	abstract
	public ValueFactory<V> getValueFactory();

	@Override
	public List<Number> apply(Node node){
		ValueFactory<V> valueFactory = getValueFactory();

		if(!node.hasScoreDistributions()){
			return null;
		}

		ValueMap<String, V> probabilityMap = new ValueMap<>();

		List<ScoreDistribution> scoreDistributions = node.getScoreDistributions();
		for(ScoreDistribution scoreDistribution : scoreDistributions){
			Number recordCount = scoreDistribution.getRecordCount();
			if(recordCount == null){
				throw new MissingAttributeException(node, PMMLAttributes.COMPLEXNODE_RECORDCOUNT);
			}

			String category = (String)scoreDistribution.getValue();
			Value<V> value = valueFactory.newValue(recordCount);

			probabilityMap.put(category, value);
		}

		ValueUtil.normalizeSimpleMax(probabilityMap.values());

		List<Number> result = new ArrayList<>();

		String[] categories = getCategories();
		for(int i = 0; i < categories.length; i++){
			String category = categories[i];
			Value<V> value = probabilityMap.get(category);
			if(value == null){
				value = valueFactory.newValue(0d);
			}

			result.add(value.getValue());
		}

		return result;
	}

	@Override
	public JExpression createExpression(List<Number> probabilities){
		JType componentType = getComponentType();

		JArray array = JExpr.newArray(componentType.elementType());

		for(Number probability : probabilities){
			JExpression elementExpr;

			if(probability instanceof Float){
				elementExpr = JExpr.lit(probability.floatValue());
			} else

			if(probability instanceof Double){
				elementExpr = JExpr.lit(probability.doubleValue());
			} else

			{
				throw new IllegalArgumentException();
			}

			array = array.add(elementExpr);
		}

		return array;
	}

	public void initResource(JBinaryFile binaryFile, JClass resourceUtilClazz, JResourceInitializer resourceInitializer){
		Collection<List<Number>> elements = getElements();

		Number[][] values = elements.stream()
			.map(element -> element.toArray(new Number[element.size()]))
			.toArray(Number[][]::new);

		String[] categories = getCategories();

		String method;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			if(values[0][0] instanceof Float){
				ResourceUtil.writeFloatArrays(dataOutput, values);

				method = "readFloatArrays";
			} else

			if(values[0][0] instanceof Double){
				ResourceUtil.writeDoubleArrays(dataOutput, values);

				method = "readDoubleArrays";
			} else

			{
				throw new IllegalArgumentException();
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		resourceInitializer.readNumberArrays(getArrayVar(), resourceUtilClazz.staticInvoke(method), JExpr.lit(values.length), JExpr.lit(categories.length));
	}

	public String[] getCategories(){
		return this.categories;
	}

	private void setCategories(String[] categories){
		this.categories = categories;
	}
}