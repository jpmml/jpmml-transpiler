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

import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.FieldName;

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
	public JMethod createEncoderMethod(JType type, FieldName name, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		JPrimitiveType primitiveType = (JPrimitiveType)type.unboxify();

		JMethod encoderMethod = owner.method(ModelTranslator.MEMBER_PRIVATE, primitiveType, IdentifierUtil.create("toFloatingPointPrimitive", name));

		JVar valueParam = encoderMethod.param(type, "value");

		try {
			context.pushScope(new MethodScope(encoderMethod));

			JExpression nanExpr;

			switch(primitiveType.name()){
				case "float":
					nanExpr = JExpr.lit(Float.NaN);
					break;
				case "double":
					nanExpr = JExpr.lit(Double.NaN);
					break;
				default:
					throw new IllegalArgumentException(primitiveType.fullName());
			}

			context._return(valueParam.eq(JExpr._null()), nanExpr, valueParam);
		} finally {
			context.popScope();
		}

		return encoderMethod;
	}
}