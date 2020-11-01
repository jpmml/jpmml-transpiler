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

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.Field;
import org.jpmml.evaluator.FieldValue;

public class OrdinalEncoder implements Encoder {

	private Map<Object, Integer> indexMap = new LinkedHashMap<>();

	private JMethod isSetMethod = null;


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
	public Integer encode(Object value){
		return this.indexMap.getOrDefault(value, 0);
	}

	@Override
	public OrdinalRef ref(JVar variable){
		return new OrdinalRef(variable, this);
	}

	@Override
	public JMethod createEncoderMethod(Field<?> field, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JMethod method = owner.method(ModelTranslator.MEMBER_PRIVATE, context._ref(int.class), IdentifierUtil.create("toOrdinal", field.getName()));

		JVar valueParam = method.param(FieldValue.class, "value");

		FieldValueRef fieldValueRef = new FieldValueRef(valueParam, field.getDataType());

		try {
			context.pushScope(new MethodScope(method));

			context._returnIf(valueParam.eq(JExpr._null()), OrdinalEncoder.MISSING_VALUE);

			context._return(fieldValueRef.asJavaValue(), this.indexMap, 0);
		} finally {
			context.popScope();
		}

		return method;
	}

	public JMethod ensureIsSetMethod(TranslationContext context){

		if(this.isSetMethod == null){
			this.isSetMethod = getOrCreateIsSetMethod(context);
		}

		return this.isSetMethod;
	}

	private JMethod getOrCreateIsSetMethod(TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JType intType = context._ref(int.class);

		JMethod isSetMethod = owner.getMethod("isSet", new JType[]{intType, intType});
		if(isSetMethod != null){
			return isSetMethod;
		}

		isSetMethod = owner.method(ModelTranslator.MEMBER_PRIVATE, boolean.class, "isSet");

		JVar bitSetParam = isSetMethod.param(intType, "bitSet");
		JVar indexParam = isSetMethod.param(intType, "index");

		JBlock block = isSetMethod.body();

		JBlock thenBlock = block._if((indexParam.gte(JExpr.lit(0))).cand(indexParam.lte(JExpr.lit(31))))._then();

		JVar maskVar = thenBlock.decl(intType, "mask", (JExpr.lit(1)).shl(indexParam));

		thenBlock._return((bitSetParam.band(maskVar)).eq(maskVar));

		block._return(JExpr.FALSE);

		return isSetMethod;
	}

	public static final JExpression MISSING_VALUE = JExpr.lit(-1);
}