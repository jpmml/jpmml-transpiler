/*
 * Copyright (c) 2021 Villu Ruusmann
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

import java.util.List;
import java.util.Objects;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataField;
import org.dmg.pmml.DataType;
import org.jpmml.evaluator.FieldValue;
import org.jpmml.evaluator.MissingFieldValueException;

public class ArrayFpPrimitiveEncoder extends FpPrimitiveEncoder implements ArrayEncoder {

	private ArrayInfo arrayInfo = null;

	private int index = -1;

	private List<DataField> elements = null;


	public ArrayFpPrimitiveEncoder(ArrayInfo arrayInfo){
		setArrayInfo(arrayInfo);
	}

	@Override
	public String getVariableName(FieldInfo fieldInfo){
		ArrayInfo arrayInfo = getArrayInfo();

		return IdentifierUtil.sanitize(arrayInfo.getName()) + "2fp" + "$" + String.valueOf(getIndex());
	}

	@Override
	public String getMemberName(FieldInfo fieldInfo){
		ArrayInfo arrayInfo = getArrayInfo();

		return IdentifierUtil.sanitize(arrayInfo.getName()) + "2fp";
	}

	@Override
	public JMethod createEncoderMethod(FieldInfo fieldInfo, JPrimitiveType returnType, String name, List<JPrimitiveType> castSequenceTypes, DataType dataType, TranslationContext context){
		JDefinedClass owner = context.getOwner();

		ArrayInfo arrayInfo = getArrayInfo();

		// XXX
		name = name + "$" + IdentifierUtil.sanitize(arrayInfo.getName());

		JMethod method = owner.getMethod(name, new JType[]{context._ref(int.class)});
		if(method != null){
			return method;
		}

		JFieldVar listField = owner.field(Modifiers.PRIVATE, context.genericRef(List.class, Number.class), IdentifierUtil.sanitize(arrayInfo.getName()));

		JMethod ensureListMethod = owner.method(Modifiers.PRIVATE_FINAL, listField.type(), IdentifierUtil.sanitize(arrayInfo.getName()));

		try {
			context.pushScope(new MethodScope(ensureListMethod));

			JExpression nameExpr = context.constantFieldName(arrayInfo.getName(), true);

			JBlock block = ensureListMethod.body();

			JBlock thenBlock = block._if(JExpr.refthis(listField.name()).eq(JExpr._null()))._then();

			JVar valueVar = thenBlock.decl(context.ref(FieldValue.class), "value", context.invoke(JExpr.refthis("context"), "evaluate", nameExpr));

			thenBlock._if(valueVar.eq(JExpr._null()))._then()._throw(context._new(MissingFieldValueException.class, nameExpr));

			thenBlock.assign(JExpr.refthis(listField.name()), JExpr.cast(listField.type(), valueVar.invoke("getValue")));

			block._return(JExpr.refthis(listField.name()));
		} finally {
			context.popScope();
		}

		method = owner.method(Modifiers.PRIVATE_FINAL, returnType, name);

		JVar indexParam = method.param(context._ref(int.class), "index");

		try {
			context.pushScope(new MethodScope(method));

			JVar listVar = context.declare(ensureListMethod.type(), listField.name(), JExpr.invoke(ensureListMethod));

			JVar numberVar = context.declare(Number.class, "number", listVar.invoke("get").arg(indexParam));

			JInvocation invocation;

			switch(dataType){
				case FLOAT:
					invocation = numberVar.invoke("floatValue");
					break;
				case DOUBLE:
					invocation = numberVar.invoke("doubleValue");
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}

			JExpression nanExpr = fpNanValue(returnType, context);
			JExpression javaValueExpr = fpJavaValue(invocation, returnType, castSequenceTypes, context);

			context._return(numberVar.eq(JExpr._null()), nanExpr, javaValueExpr);
		} finally {
			context.popScope();
		}

		return method;
	}

	public ArrayInfo getArrayInfo(){
		return this.arrayInfo;
	}

	private void setArrayInfo(ArrayInfo arrayInfo){
		this.arrayInfo = Objects.requireNonNull(arrayInfo);
	}

	@Override
	public int getIndex(){
		return this.index;
	}

	public ArrayFpPrimitiveEncoder setIndex(int index){
		this.index = index;

		return this;
	}

	@Override
	public int getLength(){
		List<DataField> elements = getElements();
		if(elements == null){
			throw new IllegalStateException();
		}

		return elements.size();
	}

	public List<DataField> getElements(){
		return this.elements;
	}

	public ArrayFpPrimitiveEncoder setElements(List<DataField> elements){
		this.elements = elements;

		return this;
	}
}