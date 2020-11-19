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
import com.sun.codemodel.JMod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
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

		JType fieldNameClazz = context.ref(FieldName.class);

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

		JMethod method = owner.getMethod(name, new JType[]{fieldNameClazz});
		if(method != null){
			return method;
		}

		method = owner.method(JMod.PRIVATE, returnType, name);

		JVar nameParam = method.param(fieldNameClazz, "name");

		try {
			context.pushScope(new MethodScope(method));

			JVar valueVar = context.declare(FieldValue.class, "value", context.invoke(JExpr.refthis("context"), "evaluate", nameParam));

			FieldValueRef fieldValueRef = new FieldValueRef(valueVar, dataType);

			JExpression nanExpr;

			switch(dataType){
				case FLOAT:
					nanExpr = FpPrimitiveEncoder.NAN_VALUE_FLOAT;
					break;
				case DOUBLE:
					nanExpr = FpPrimitiveEncoder.NAN_VALUE_DOUBLE;
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}

			context._return(valueVar.eq(JExpr._null()), nanExpr, fieldValueRef.asJavaValue());
		} finally {
			context.popScope();
		}

		return method;
	}

	@Override
	public JExpression createInitExpression(Field<?> field, TranslationContext context){
		DataType dataType = field.getDataType();

		switch(dataType){
			case FLOAT:
				return FpPrimitiveEncoder.INIT_VALUE_FLOAT;
			case DOUBLE:
				return FpPrimitiveEncoder.INIT_VALUE_DOUBLE;
			default:
				throw new IllegalArgumentException(dataType.toString());
		}
	}

	public static final JExpression INIT_VALUE_FLOAT = JExpr.lit(-999f);
	public static final JExpression INIT_VALUE_DOUBLE = JExpr.lit(-999d);

	public static final JExpression NAN_VALUE_FLOAT = JExpr.lit(Float.NaN);
	public static final JExpression NAN_VALUE_DOUBLE = JExpr.lit(Double.NaN);
}