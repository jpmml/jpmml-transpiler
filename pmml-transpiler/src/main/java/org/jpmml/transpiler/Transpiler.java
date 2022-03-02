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
package org.jpmml.transpiler;

import java.io.IOException;

import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;

abstract
public class Transpiler {

	private String className = null;


	public Transpiler(String className){
		setClassName(className);
	}

	abstract
	public PMML transpile(PMML pmml) throws IOException;

	@SuppressWarnings(
		value = {"unused"}
	)
	protected JCodeModel translate(PMML pmml) throws IOException {
		String className = getClassName();

		JCodeModel codeModel = TranspilerUtil.translate(pmml, className);

		return codeModel;
	}

	protected JCodeModel compile(JCodeModel codeModel) throws IOException {
		TranspilerUtil.compile(codeModel);

		return codeModel;
	}

	public String getClassName(){
		return this.className;
	}

	private void setClassName(String className){
		this.className = className;
	}
}