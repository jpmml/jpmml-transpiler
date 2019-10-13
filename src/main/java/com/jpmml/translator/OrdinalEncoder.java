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
import java.util.Map;
import java.util.Set;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JSwitch;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.FieldName;
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
	public String getName(){
		return "ordinal";
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
	public ObjectRef ref(JVar variable){
		return new OrdinalRef(variable, this);
	}

	@Override
	public Integer encode(Object value){
		return this.indexMap.getOrDefault(value, 0);
	}

	@Override
	public JMethod createEncoderMethod(JType type, FieldName name, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JCodeModel codeModel = context.getCodeModel();

		JMethod encoderMethod = owner.method(JMod.PRIVATE, codeModel.INT, IdentifierUtil.create("encode", name));

		JVar valueParam = encoderMethod.param(type, "value");

		JBlock block = encoderMethod.body();

		JSwitch switchBlock = block._switch(valueParam);

		Collection<? extends Map.Entry<Object, Integer>> entries = this.indexMap.entrySet();
		for(Map.Entry<Object, Integer> entry : entries){
			switchBlock._case(PMMLObjectUtil.createExpression(entry.getKey(), context)).body()._return(JExpr.lit(entry.getValue()));
		}

		switchBlock._default().body()._return(JExpr.lit(0));

		return encoderMethod;
	}
}