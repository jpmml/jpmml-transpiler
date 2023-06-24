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

import java.util.Map;

import com.sun.codemodel.JAssignment;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JVar;
import org.dmg.pmml.DataType;
import org.dmg.pmml.DerivedField;
import org.dmg.pmml.OpType;
import org.dmg.pmml.TextIndex;
import org.jpmml.evaluator.TextUtil;
import org.jpmml.evaluator.TokenizedString;

public class TextIndexUtil {

	private TextIndexUtil(){
	}

	static
	public TextIndex toLocalTextIndex(TextIndex textIndex, String name){
		String wordRE = textIndex.getWordRE();
		String wordSeparatorCharacterRE = textIndex.getWordSeparatorCharacterRE();

		if(wordRE != null){
			wordSeparatorCharacterRE = null;
		}

		TextIndex localTextIndex = new TextIndex(name, null)
			.setLocalTermWeights(textIndex.getLocalTermWeights())
			.setCaseSensitive(textIndex.isCaseSensitive())
			.setMaxLevenshteinDistance(textIndex.getMaxLevenshteinDistance())
			.setCountHits(textIndex.getCountHits())
			.setWordRE(wordRE)
			.setWordSeparatorCharacterRE(wordSeparatorCharacterRE)
			.setTokenize(textIndex.isTokenize());

		if(textIndex.hasTextIndexNormalizations()){
			(localTextIndex.getTextIndexNormalizations()).addAll(textIndex.getTextIndexNormalizations());
		}

		return localTextIndex;
	}

	static
	public JExpression computeTermFrequencyTable(JVar assignmentTargetVar, TextIndex textIndex, JExpression textIndexExpr, JExpression vocabularyExpr, int maxLength, TranslationContext context){
		// XXX
		FieldInfo textFieldInfo = new FieldInfo(new DerivedField(textIndex.requireTextField(), OpType.CATEGORICAL, DataType.STRING, null));

		StringRef textRef = (StringRef)context.ensureOperable(textFieldInfo, (method) -> true);

		JVar textVar = (JVar)textRef.getExpression();

		if(textIndex.hasTextIndexNormalizations()){
			JInvocation textNormalizationInvocation = context.staticInvoke(TextUtil.class, "normalize", textIndexExpr, textVar);

			context.add((JAssignment)textVar.assign(textNormalizationInvocation));
		}

		JInvocation textTokenizationInvocation = context.staticInvoke(TextUtil.class, "tokenize", textIndexExpr, textVar);

		JVar textTokensVar = context.declare(context.ref(TokenizedString.class), textVar.name() + "Tokens", textTokenizationInvocation);

		JInvocation termFrequencyTableInvocation = context.staticInvoke(TextUtil.class, "termFrequencyTable", textIndexExpr, textTokensVar, vocabularyExpr, maxLength);

		if(assignmentTargetVar != null){
			context.add((JAssignment)assignmentTargetVar.assign(termFrequencyTableInvocation));

			return termFrequencyTableInvocation;
		}

		JVar termFrequencyTableVar = context.declare(context.genericRef(Map.class, TokenizedString.class, Integer.class), textVar.name() + "FrequencyTable", termFrequencyTableInvocation);

		return termFrequencyTableVar;
	}

	static
	public FunctionInvocation.Tf asTf(FunctionInvocation functionInvocation){

		if(functionInvocation instanceof FunctionInvocation.Tf){
			FunctionInvocation.Tf tf = (FunctionInvocation.Tf)functionInvocation;

			return tf;
		} else

		if(functionInvocation instanceof FunctionInvocation.TfIdf){
			FunctionInvocation.TfIdf tfIdf = (FunctionInvocation.TfIdf)functionInvocation;
			FunctionInvocation.Tf tf = tfIdf.getTf();

			return tf;
		}

		throw new IllegalArgumentException();
	}

	static
	public FunctionInvocation.TfIdf asTfIdf(FunctionInvocation functionInvocation){

		if(functionInvocation instanceof FunctionInvocation.Tf){
			FunctionInvocation.Tf tf = (FunctionInvocation.Tf)functionInvocation;

			return new FunctionInvocation.TfIdf(){

				@Override
				public FunctionInvocation.Tf getTf(){
					return tf;
				}

				@Override
				public Number getWeight(){
					return 1;
				}
			};
		} else

		if(functionInvocation instanceof FunctionInvocation.TfIdf){
			FunctionInvocation.TfIdf tfIdf = (FunctionInvocation.TfIdf)functionInvocation;

			return tfIdf;
		}

		throw new IllegalArgumentException();
	}
}