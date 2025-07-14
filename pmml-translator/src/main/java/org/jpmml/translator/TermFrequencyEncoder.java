/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPrimitiveType;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.TextIndex;
import org.jpmml.evaluator.TokenizedString;

public class TermFrequencyEncoder extends FpPrimitiveEncoder implements ArrayEncoder {

	private int index = -1;

	private List<TokenizedString> vocabulary = null;


	public TermFrequencyEncoder(){
	}

	@Override
	public String getVariableName(FieldInfo fieldInfo){
		FunctionInvocation.Tf tf = getTf(fieldInfo);

		return IdentifierUtil.sanitize(tf.getTextField()) + "2tf" + "$" + String.valueOf(getIndex());
	}

	@Override
	public String getMemberName(FieldInfo fieldInfo){
		FunctionInvocation.Tf tf = getTf(fieldInfo);

		return IdentifierUtil.sanitize(tf.getTextField()) + "2tf";
	}

	@Override
	public JMethod createEncoderMethod(FieldInfo fieldInfo, JPrimitiveType returnType, String name, List<JPrimitiveType> castSequenceTypes, DataType dataType, TranslationContext context){
		// JavaModel$Arguments
		JDefinedClass owner = context.getOwner();

		FunctionInvocation.Tf tf = getTf(fieldInfo);

		name = IdentifierUtil.create(name, tf.getTextField());

		JMethod method = owner.getMethod(name, new JType[]{context._ref(int.class)});
		if(method != null){
			return method;
		}

		// JavaModel
		JDefinedClass ownerOwner = (JDefinedClass)owner.parentContainer();

		Map<String, JFieldVar> fields = ownerOwner.fields();

		JFieldVar textIndexVar = fields.get(IdentifierUtil.create("textIndex", tf.getTextIndex(), tf.getTextField()));
		JFieldVar termsVar = fields.get(IdentifierUtil.create("terms", tf.getTextIndex(), tf.getTextField()));

		JFieldVar termFrequencyTableVar = owner.field(Modifiers.PRIVATE, context.genericRef(Map.class, TokenizedString.class, Integer.class), IdentifierUtil.create("termFrequencyTable", tf.getTextField()));

		JMethod frequencyTableMethod = owner.method(Modifiers.PRIVATE_FINAL, termFrequencyTableVar.type(), termFrequencyTableVar.name());

		try {
			context.pushScope(new MethodScope(frequencyTableMethod));

			JBlock block = frequencyTableMethod.body();

			JBlock thenBlock = block._if(termFrequencyTableVar.eq(JExpr._null()))._then();

			try {
				context.pushScope(new Scope(thenBlock));

				TextIndex localTextIndex = TextIndexUtil.toLocalTextIndex(tf.getTextIndex(), tf.getTextField());

				int maxLength = getVocabulary().stream()
					.mapToInt(TokenizedString::size)
					.max().orElseThrow(NoSuchElementException::new);

				TextIndexUtil.computeTermFrequencyTable(termFrequencyTableVar, localTextIndex, textIndexVar, context._new(HashSet.class, termsVar), maxLength, context);
			} finally {
				context.popScope();
			}

			block._return(termFrequencyTableVar);
		} finally {
			context.popScope();
		}

		method = owner.method(Modifiers.PRIVATE_FINAL, returnType, name);

		JVar indexParam = method.param(context._ref(int.class), "index");

		try {
			context.pushScope(new MethodScope(method));

			JVar frequencyVar = context.declare(Integer.class, "frequency", JExpr.invoke(frequencyTableMethod).invoke("get").arg(termsVar.invoke("get").arg(indexParam)));

			JExpression nanExpr = JExpr.lit(0);
			JExpression javaValueExpr;

			switch(dataType){
				case INTEGER:
					javaValueExpr = frequencyVar.invoke("intValue");
					break;
				case FLOAT:
					javaValueExpr = frequencyVar.invoke("floatValue");
					break;
				case DOUBLE:
					javaValueExpr = frequencyVar.invoke("doubleValue");
					break;
				default:
					throw new IllegalArgumentException(dataType.toString());
			}

			javaValueExpr = fpJavaValue(javaValueExpr, returnType, castSequenceTypes, context);

			context._return(frequencyVar.eq(JExpr._null()), nanExpr, javaValueExpr);
		} finally {
			context.popScope();
		}

		return method;
	}

	@Override
	public int getIndex(){
		return this.index;
	}

	public TermFrequencyEncoder setIndex(int index){
		this.index = index;

		return this;
	}

	@Override
	public int getLength(){
		List<TokenizedString> vocabulary = getVocabulary();
		if(vocabulary == null){
			throw new IllegalStateException();
		}

		return vocabulary.size();
	}

	public List<TokenizedString> getVocabulary(){
		return this.vocabulary;
	}

	public TermFrequencyEncoder setVocabulary(List<TokenizedString> vocabulary){
		this.vocabulary = vocabulary;

		return this;
	}

	public FunctionInvocation.Tf getTf(FieldInfo fieldInfo){
		FieldInfo finalFieldInfo = follow(fieldInfo);

		return (FunctionInvocation.Tf)finalFieldInfo.getFunctionInvocation();
	}
}