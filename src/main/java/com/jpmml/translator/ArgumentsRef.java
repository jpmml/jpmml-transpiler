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
		DataType dataType = field.getDataType();

		JMethod method = argumentsClazz.getMethod(IdentifierUtil.create("get", name), new JType[0]);
		if(method != null){
			return method;
		}

		JType type;

		switch(dataType){
			case STRING:
				type = context.ref(String.class);
				break;
			case INTEGER:
				type = context.ref(Integer.class);
				break;
			case FLOAT:
				type = context.ref(Float.class);
				break;
			case DOUBLE:
				type = context.ref(Double.class);
				break;
			case BOOLEAN:
				type = context.ref(Boolean.class);
				break;
			default:
				throw new UnsupportedAttributeException(field, dataType);
		}

		String prefix = (dataType.name()).toLowerCase();

		JMethod encoderMethod = null;

		if(encoder != null){
			prefix = (prefix + "2" + encoder.getName());

			try {
				context.pushOwner(argumentsClazz);

				encoderMethod = encoder.createEncoderMethod(type, name, context);
			} finally {
				context.popOwner();
			}

			type = encoderMethod.type();
		}

		JFieldVar flagFieldVar = argumentsClazz.field(JMod.PRIVATE, boolean.class, IdentifierUtil.create("_initialized", name), JExpr.lit(false));

		JFieldVar valueFieldVar = argumentsClazz.field(JMod.PRIVATE, type, IdentifierUtil.create(prefix, name));

		method = argumentsClazz.method(JMod.PUBLIC, type, IdentifierUtil.create("get", name));

		JBlock block = method.body();

		JBlock thenBlock = block._if(JExpr.refthis(flagFieldVar.name()).not())._then();

		JVar valueVar = thenBlock.decl(context.ref(FieldValue.class), "value", JExpr.refthis("context").invoke("evaluate").arg(context.constantFieldName(name)));

		FieldValueRef fieldValueRef = new FieldValueRef(valueVar);

		JExpression valueExpr;

		switch(dataType){
			case STRING:
				valueExpr = fieldValueRef.asString();
				break;
			case INTEGER:
				valueExpr = fieldValueRef.asInteger();
				break;
			case FLOAT:
				valueExpr = fieldValueRef.asFloat();
				break;
			case DOUBLE:
				valueExpr = fieldValueRef.asDouble();
				break;
			case BOOLEAN:
				valueExpr = fieldValueRef.asBoolean();
				break;
			default:
				throw new UnsupportedAttributeException(field, dataType);
		}

		valueExpr = JOp.cond(valueVar.ne(JExpr._null()), valueExpr, JExpr._null());

		if(encoder != null && encoderMethod != null){
			valueExpr = JExpr.invoke(encoderMethod).arg(valueExpr);
		}

		thenBlock.assign(JExpr.refthis(flagFieldVar.name()), JExpr.lit(true));
		thenBlock.assign(JExpr.refthis(valueFieldVar.name()), valueExpr);

		block._return(JExpr.refthis(valueFieldVar.name()));

		return method;
	}
}