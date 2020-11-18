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

import com.google.common.collect.Iterators;
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

		String stringName = fieldInfo.getVariableName();

		JMethod method = argumentsClazz.getMethod(stringName, new JType[0]);
		if(method != null){
			return method;
		}

		JMethod constructor = Iterators.getOnlyElement(argumentsClazz.constructors());

		JBlock constructorBody = constructor.body();

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

		JMethod encoderMethod = null;

		if(encoder != null){

			try {
				context.pushOwner(argumentsClazz);

				encoderMethod = encoder.createEncoderMethod(field, context);
			} finally {
				context.popOwner();
			}

			type = encoderMethod.type();
		}

		method = argumentsClazz.method(JMod.PUBLIC, type, stringName);

		JBlock methodBody = method.body();

		JBlock initializerBlock;

		Integer count = fieldInfo.getCount();
		if(count != null && count > 1){
			JFieldVar fieldFlagVar = argumentsClazz.field(JMod.PRIVATE, boolean.class, "_" + stringName, JExpr.FALSE);
			JFieldVar fieldVar = argumentsClazz.field(JMod.PRIVATE, type, stringName);

			JBlock thenBlock = methodBody._if(JExpr.refthis(fieldFlagVar.name()).not())._then();

			thenBlock.assign(JExpr.refthis(fieldFlagVar.name()), JExpr.TRUE);

			initializerBlock = thenBlock;
		} else

		{
			initializerBlock = methodBody;
		}

		JVar valueVar = initializerBlock.decl(context.ref(FieldValue.class), IdentifierUtil.create("value", name), context.invoke(JExpr.refthis("context"), "evaluate", name));

		JExpression valueExpr;

		if(encoder != null && encoderMethod != null){
			valueExpr = JExpr.invoke(encoderMethod).arg(valueVar);
		} else

		{
			FieldValueRef fieldValueRef = new FieldValueRef(valueVar, dataType);

			valueExpr = JOp.cond(valueVar.ne(JExpr._null()), fieldValueRef.asJavaValue(), JExpr._null());
		} // End if

		if(count != null && count > 1){
			JFieldVar fieldVar = (argumentsClazz.fields()).get(stringName);

			initializerBlock.assign(JExpr.refthis(fieldVar.name()), valueExpr);

			methodBody._return(JExpr.refthis(fieldVar.name()));
		} else

		{
			methodBody._return(valueExpr);
		}

		return method;
	}
}