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

import java.util.List;

public class TermFrequencyEncoder extends FpPrimitiveEncoder implements ArrayEncoder {

	private int index = -1;

	private List<List<String>> vocabulary = null;


	public TermFrequencyEncoder(){
	}

	@Override
	public String getVariableName(FieldInfo fieldInfo){
		FunctionInvocation.Tf tf = getTf(fieldInfo);

		return IdentifierUtil.sanitize((tf.getTextField()).getValue()) + "2tf" + "$" + IdentifierUtil.sanitize((tf.getTerm()).replaceAll("\\s", "_"));
	}

	@Override
	public String getMemberName(FieldInfo fieldInfo){
		FunctionInvocation.Tf tf = getTf(fieldInfo);

		return IdentifierUtil.sanitize((tf.getTextField()).getValue()) + "2tf";
	}

	@Override
	public int getIndex(){
		return this.index;
	}

	public void setIndex(int index){
		this.index = index;
	}

	@Override
	public int getLength(){
		List<List<String>> vocabulary = getVocabulary();
		if(vocabulary == null){
			throw new IllegalStateException();
		}

		return vocabulary.size();
	}

	public List<List<String>> getVocabulary(){
		return this.vocabulary;
	}

	public void setVocabulary(List<List<String>> vocabulary){
		this.vocabulary = vocabulary;
	}

	public FunctionInvocation.Tf getTf(FieldInfo fieldInfo){
		FieldInfo finalFieldInfo = follow(fieldInfo);

		return (FunctionInvocation.Tf)finalFieldInfo.getFunctionInvocation();
	}
}