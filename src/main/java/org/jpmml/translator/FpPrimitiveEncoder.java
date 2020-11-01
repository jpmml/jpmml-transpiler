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

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.jpmml.evaluator.FieldValue;

public class FpPrimitiveEncoder implements Encoder {

	public FpPrimitiveEncoder(){
	}

	@Override
	public String getName(){
		return "fp";
	}

	@Override
	public Object encode(Object value){

		// XXX: Assumes that Double.NaN can be "downcast" to Float.NaN
		if(value == null){
			return Double.NaN;
		}

		return value;
	}

	@Override
	public OperableRef ref(JVar variable){
		return new FpPrimitiveRef(variable);
	}

	@Override
	public JMethod createEncoderMethod(Field<?> field, TranslationContext context){
		JCodeModel codeModel = context.getCodeModel();

		JDefinedClass owner = context.getOwner();

		DataType dataType = field.getDataType();

		JType fieldValueClazz = context.ref(FieldValue.class);

		String name;
		JPrimitiveType returnType;

		switch(dataType){
			case FLOAT:
				name = "toFloatPrimitive";
				returnType = codeModel.FLOAT;
				break;
			case DOUBLE:
				name = "toDoublePrimitive";
				returnType = codeModel.DOUBLE;
				break;
			default:
				throw new IllegalArgumentException(dataType.toString());
		}

		JMethod method = owner.getMethod(name, new JType[]{fieldValueClazz});
		if(method != null){
			return method;
		}

		method = owner.method(ModelTranslator.MEMBER_PRIVATE, returnType, name);

		JVar valueParam = method.param(fieldValueClazz, "value");

		FieldValueRef fieldValueRef = new FieldValueRef(valueParam, dataType);

		try {
			context.pushScope(new MethodScope(method));

			JExpression nanExpr;

			switch(dataType){
				case FLOAT:
					nanExpr = JExpr.lit(Float.NaN);
					break;
				case DOUBLE:
					nanExpr = JExpr.lit(Double.NaN);
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}

			context._return(valueParam.eq(JExpr._null()), nanExpr, fieldValueRef.asJavaValue());
		} finally {
			context.popScope();
		}

		return method;
	}
}