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
import org.jpmml.evaluator.java.JavaModel;

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
	public String getVariableName(FieldInfo fieldInfo){
		Field<?> field = fieldInfo.getField();

		return IdentifierUtil.sanitize(field.requireName()) + "2ordinal";
	}

	@Override
	public Integer encode(Object value){
		return this.indexMap.getOrDefault(value, 0);
	}

	@Override
	public OrdinalRef ref(JExpression expression){
		return new OrdinalRef(expression, this);
	}

	@Override
	public JMethod createEncoderMethod(FieldInfo fieldInfo, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		Field<?> field = fieldInfo.getField();

		JMethod method = owner.method(Modifiers.PRIVATE_FINAL, context._ref(int.class), IdentifierUtil.create("toOrdinal", field.requireName()));

		JVar nameParam = method.param(String.class, "name");

		try {
			context.pushScope(new MethodScope(method));

			JVar valueVar = context.declare(FieldValue.class, "value", context.invoke(JExpr.refthis("context"), "evaluate", nameParam));

			FieldValueRef fieldValueRef = new FieldValueRef(valueVar, field.getDataType());

			context._returnIf(valueVar.eq(JExpr._null()), OrdinalEncoder.MISSING_VALUE);

			JVar javaValueVar = context.declare(fieldValueRef.getJavaType(), "javaValue", fieldValueRef.asJavaValue());

			context._return(javaValueVar, this.indexMap, 0);
		} finally {
			context.popScope();
		}

		return method;
	}

	@Override
	public JExpression createInitExpression(FieldInfo fieldInfo, TranslationContext context){
		return OrdinalEncoder.INIT_VALUE;
	}

	public JMethod ensureIsSetMethod(TranslationContext context){

		if(this.isSetMethod == null){
			this.isSetMethod = getOrCreateIsSetMethod(context);
		}

		return this.isSetMethod;
	}

	private JMethod getOrCreateIsSetMethod(TranslationContext context){
		JDefinedClass owner = context.getOwner(JavaModel.class);

		JType intType = context._ref(int.class);

		JMethod isSetMethod = owner.getMethod("isSet", new JType[]{intType, intType});
		if(isSetMethod != null){
			return isSetMethod;
		}

		isSetMethod = owner.method(Modifiers.PRIVATE_STATIC_FINAL, boolean.class, "isSet");

		JVar bitSetParam = isSetMethod.param(intType, "bitSet");
		JVar indexParam = isSetMethod.param(intType, "index");

		JBlock block = isSetMethod.body();

		JBlock thenBlock = block._if((indexParam.gte(JExpr.lit(0))).cand(indexParam.lte(JExpr.lit(31))))._then();

		JVar maskVar = thenBlock.decl(intType, "mask", (JExpr.lit(1)).shl(indexParam));

		thenBlock._return((bitSetParam.band(maskVar)).eq(maskVar));

		block._return(JExpr.FALSE);

		return isSetMethod;
	}

	static
	public OrdinalEncoder create(FieldInfo fieldInfo, Set<?> values){
		return new OrdinalEncoder(values);
	}

	public static final JExpression INIT_VALUE = JExpr.lit(-999);
	public static final JExpression MISSING_VALUE = JExpr.lit(-1);
}