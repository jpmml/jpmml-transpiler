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

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.OpType;

public class OrdinalEncoder implements Encoder {

	private Map<Object, Integer> indexMap = new LinkedHashMap<>();


	public OrdinalEncoder(Set<?> values){
		int index = 1;

		for(Object value : values){
			this.indexMap.put(value, index);

			index++;
		}
	}

	@Override
	public DataType getDataType(){
		return DataType.INTEGER;
	}

	@Override
	public OpType getOpType(){
		return OpType.CATEGORICAL;
	}

	@Override
	public Integer encode(Object value){
		return this.indexMap.getOrDefault(value, 0);
	}

	@Override
	public void createEncoderBody(JMethod method, TranslationContext context){
		List<JVar> params = method.params();

		JVar param = params.get(0);

		JBlock block = method.body();

		JSwitch switchBlock = block._switch(param);

		Collection<? extends Map.Entry<Object, Integer>> entries = this.indexMap.entrySet();
		for(Map.Entry<Object, Integer> entry : entries){
			switchBlock._case(PMMLObjectUtil.createExpression(entry.getKey(), context)).body()._return(JExpr.lit(entry.getValue()));
		}

		switchBlock._default().body()._return(JExpr.lit(0));
	}
}