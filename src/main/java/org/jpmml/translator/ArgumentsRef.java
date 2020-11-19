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

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JMod;
import com.sun.codemodel.JOp;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.Field;
import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.UnsupportedAttributeException;

public class ArgumentsRef extends JVarRef {

	public ArgumentsRef(JVar variable){
		super(variable);
	}

	public JMethod getMethod(FieldInfo fieldInfo, TranslationContext context){
		JDefinedClass argumentsClazz = (JDefinedClass)type();

		Field<?> field = fieldInfo.getField();
		Encoder encoder = fieldInfo.getEncoder();

		FieldName name = field.getName();

		String stringName = fieldInfo.getVariableName();

		JMethod method = argumentsClazz.getMethod(stringName, new JType[0]);
		if(method != null){
			return method;
		}

		JMethod encoderMethod;

		if(encoder != null){

			try {
				context.pushOwner(argumentsClazz);

				encoderMethod = encoder.createEncoderMethod(field, context);
			} finally {
				context.popOwner();
			}
		} else

		{
			try {
				context.pushOwner(argumentsClazz);

				encoderMethod = ArgumentsRef.createEncoderMethod(field, context);
			} finally {
				context.popOwner();
			}
		}

		JType type = encoderMethod.type();

		method = argumentsClazz.method(JMod.PUBLIC, type, stringName);

		JBlock block = method.body();

		JExpression valueExpr = JExpr.invoke(encoderMethod).arg(context.constantFieldName(name));

		Integer count = fieldInfo.getCount();
		if(count != null && count > 1){
			JExpression initExpr;

			if(encoder != null){
				initExpr = encoder.createInitExpression(field, context);
			} else

			{
				initExpr = ArgumentsRef.createInitExpression(field, context);
			}

			JFieldVar fieldVar = argumentsClazz.field(JMod.PRIVATE, type, stringName, initExpr);

			JBlock thenBlock = block._if(JExpr.refthis(fieldVar.name()).eq(initExpr))._then();

			thenBlock.assign(JExpr.refthis(fieldVar.name()), valueExpr);

			block._return(JExpr.refthis(fieldVar.name()));
		} else

		{
			block._return(valueExpr);
		}

		return method;
	}

	static
	private JMethod createEncoderMethod(Field<?> field, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JType fieldNameClazz = context.ref(FieldName.class);

		DataType dataType = field.getDataType();

		String name;
		JType returnType;

		switch(dataType){
			case STRING:
				name = "toString";
				returnType = context.ref(String.class);
				break;
			case INTEGER:
				name = "toInteger";
				returnType = context.ref(Integer.class);
				break;
			case FLOAT:
				name = "toFloat";
				returnType = context.ref(Float.class);
				break;
			case DOUBLE:
				name = "toDouble";
				returnType = context.ref(Double.class);
				break;
			case BOOLEAN:
				name = "toBoolean";
				returnType = context.ref(Boolean.class);
				break;
			default:
				throw new UnsupportedAttributeException(field, dataType);
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

			context._return(JOp.cond(valueVar.ne(JExpr._null()), fieldValueRef.asJavaValue(), JExpr._null()));
		} finally {
			context.popScope();
		}

		return method;
	}

	static
	private JExpression createInitExpression(Field<?> field, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		DataType dataType = field.getDataType();

		String name;

		switch(dataType){
			case STRING:
				name = "INIT_STRING_VALUE";
				break;
			case INTEGER:
				name = "INIT_INTEGER_VALUE";
				break;
			case FLOAT:
				name = "INIT_FLOAT_VALUE";
				break;
			case DOUBLE:
				name = "INIT_DOUBLE_VALUE";
				break;
			case BOOLEAN:
				name = "INIT_BOOLEAN_VALUE";
				break;
			default:
				throw new IllegalArgumentException(dataType.toString());
		}

		JFieldVar constantVar = (owner.fields()).get(name);
		if(constantVar == null){
			JType type;
			JExpression initExpr;

			switch(dataType){
				case STRING:
					type = context.ref(String.class);
					initExpr = JExpr._new(type);
					break;
				case INTEGER:
					type = context.ref(Integer.class);
					initExpr = JExpr._new(type).arg(JExpr.lit(-999));
					break;
				case FLOAT:
					type = context.ref(Float.class);
					initExpr = JExpr._new(type).arg(JExpr.lit(-999f));
					break;
				case DOUBLE:
					type = context.ref(Double.class);
					initExpr = JExpr._new(type).arg(JExpr.lit(-999d));
					break;
				case BOOLEAN:
					type = context.ref(Boolean.class);
					initExpr = JExpr._new(type).arg(JExpr.lit(false));
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}

			constantVar = owner.field((JMod.PRIVATE | JMod.FINAL | JMod.STATIC), type, name, initExpr);
		}

		return owner.staticRef(constantVar);
	}
}