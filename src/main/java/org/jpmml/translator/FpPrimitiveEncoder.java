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

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.OpType;
import org.jpmml.evaluator.FieldValue;

public class FpPrimitiveEncoder implements Encoder {

	public FpPrimitiveEncoder(){
	}

	@Override
	public String getVariableName(FieldInfo fieldInfo){
		Field<?> field = fieldInfo.getField();

		FieldName name = field.getName();

		return IdentifierUtil.sanitize(name.getValue()) + "2fp";
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
	public OperableRef ref(JExpression expression){
		return new FpPrimitiveRef(expression);
	}

	@Override
	public FieldInfo follow(FieldInfo fieldInfo){
		FieldInfo result = fieldInfo;

		for(FieldInfo ref = fieldInfo.getRef(); ref != null; ref = ref.getRef()){
			Field<?> refField = ref.getField();

			if(!isCastable(refField)){
				break;
			}

			result = ref;
		}

		return result;
	}

	@Override
	public JMethod createEncoderMethod(FieldInfo fieldInfo, TranslationContext context){
		Field<?> field = fieldInfo.getField();

		DataType dataType = field.getDataType();

		String name;
		JPrimitiveType returnType;

		switch(dataType){
			case INTEGER:
				name = "Integer";
				returnType = (JPrimitiveType)context._ref(int.class);
				break;
			case FLOAT:
				name = "Float";
				returnType = (JPrimitiveType)context._ref(float.class);
				break;
			case DOUBLE:
				name = "Double";
				returnType = (JPrimitiveType)context._ref(double.class);
				break;
			default:
				throw new IllegalArgumentException(dataType.toString());
		}

		List<JPrimitiveType> castSequenceTypes = null;

		for(FieldInfo ref = fieldInfo.getRef(); ref != null; ref = ref.getRef()){
			Field<?> refField = ref.getField();

			if(!isCastable(refField)){
				break;
			}

			field = refField;

			dataType = field.getDataType();

			if(castSequenceTypes == null){
				castSequenceTypes = new ArrayList<>();
			}

			switch(dataType){
				case INTEGER:
					name += "Integer";
					castSequenceTypes.add((JPrimitiveType)context._ref(int.class));
					break;
				case FLOAT:
					name += "Float";
					castSequenceTypes.add((JPrimitiveType)context._ref(float.class));
					break;
				case DOUBLE:
					name += "Double";
					castSequenceTypes.add((JPrimitiveType)context._ref(double.class));
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}
		}

		name = ("to" + name + "Primitive");

		return createEncoderMethod(fieldInfo, returnType, name, castSequenceTypes, dataType, context);
	}

	public JMethod createEncoderMethod(FieldInfo fieldInfo, JPrimitiveType returnType, String name, List<JPrimitiveType> castSequenceTypes, DataType dataType, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JType fieldNameClazz = context.ref(FieldName.class);

		JMethod method = owner.getMethod(name, new JType[]{fieldNameClazz});
		if(method != null){
			return method;
		}

		method = owner.method(Modifiers.PRIVATE_FINAL, returnType, name);

		JVar nameParam = method.param(fieldNameClazz, "name");

		try {
			context.pushScope(new MethodScope(method));

			JVar valueVar = context.declare(FieldValue.class, "value", context.invoke(JExpr.refthis("context"), "evaluate", nameParam));

			FieldValueRef fieldValueRef = new FieldValueRef(valueVar, dataType);

			JExpression nanExpr = fpNanValue(returnType, context);
			JExpression javaValueExpr = fpJavaValue(fieldValueRef.asJavaPrimitiveValue(), returnType, castSequenceTypes, context);

			context._return(valueVar.eq(JExpr._null()), nanExpr, javaValueExpr);
		} finally {
			context.popScope();
		}

		return method;

	}

	@Override
	public JExpression createInitExpression(FieldInfo fieldInfo, TranslationContext context){
		Field<?> field = fieldInfo.getField();

		DataType dataType = field.getDataType();

		switch(dataType){
			case INTEGER:
				return FpPrimitiveEncoder.INIT_VALUE_DOUBLE;
			case FLOAT:
				return FpPrimitiveEncoder.INIT_VALUE_FLOAT;
			case DOUBLE:
				return FpPrimitiveEncoder.INIT_VALUE_DOUBLE;
			default:
				throw new IllegalArgumentException(dataType.toString());
		}
	}

	static
	public FpPrimitiveEncoder create(FieldInfo fieldInfo, Map<Field<?>, ArrayInfo> fieldArrayInfos){

		while(fieldInfo != null){
			Field<?> field = fieldInfo.getField();

			if(!isCastable(field)){
				break;
			}

			ArrayInfo arrayInfo = fieldArrayInfos.get(field);
			if(arrayInfo != null){
				Integer index = arrayInfo.getIndex((DataField)field);

				return new ArrayFpPrimitiveEncoder(arrayInfo)
					.setIndex(index);
			}

			FunctionInvocation functionInvocation = fieldInfo.getFunctionInvocation();
			if(functionInvocation != null){

				if(functionInvocation instanceof FunctionInvocation.Tf){
					return new TermFrequencyEncoder();
				}

				break;
			}

			fieldInfo = fieldInfo.getRef();
		}

		return new FpPrimitiveEncoder();
	}

	static
	protected boolean isCastable(Field<?> field){
		OpType opType = field.getOpType();
		switch(opType){
			case CONTINUOUS:
				break;
			default:
				return false;
		}

		DataType dataType = field.getDataType();
		switch(dataType){
			case INTEGER:
			case FLOAT:
			case DOUBLE:
				break;
			default:
				return false;
		}

		return true;
	}

	static
	protected JExpression fpNanValue(JPrimitiveType returnType, TranslationContext context){
		JCodeModel codeModel = context.getCodeModel();

		if((codeModel.FLOAT).equals(returnType)){
			return FpPrimitiveEncoder.NAN_VALUE_FLOAT;
		} else

		if((codeModel.DOUBLE).equals(returnType)){
			return FpPrimitiveEncoder.NAN_VALUE_DOUBLE;
		} else

		{
			throw new IllegalArgumentException();
		}
	}

	static
	protected JExpression fpJavaValue(JExpression javaValueExpr, JPrimitiveType returnType, List<JPrimitiveType> castSequenceTypes, TranslationContext context){

		if(castSequenceTypes != null){
			castSequenceTypes.add(0, returnType);
			castSequenceTypes.remove(castSequenceTypes.size() - 1);

			for(int i = (castSequenceTypes.size() - 1); i > -1; i--){
				javaValueExpr = JExpr.cast(castSequenceTypes.get(i), javaValueExpr);
			}
		}

		return javaValueExpr;
	}

	public static final JExpression INIT_VALUE_FLOAT = JExpr.lit(-999f);
	public static final JExpression INIT_VALUE_DOUBLE = JExpr.lit(-999d);

	public static final JExpression NAN_VALUE_FLOAT = JExpr.lit(Float.NaN);
	public static final JExpression NAN_VALUE_DOUBLE = JExpr.lit(Double.NaN);
}