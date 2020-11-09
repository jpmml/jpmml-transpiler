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
import org.jpmml.codemodel.JCodeModelClassLoader;
import org.jpmml.evaluator.PMMLTransformer;
import org.jpmml.model.PMMLUtil;

public class TranspilerTransformer implements PMMLTransformer<IOException> {

	private String className = null;


	public TranspilerTransformer(String className){
		setClassName(className);
	}

	@Override
	public PMML apply(PMML pmml) throws IOException {
		String className = getClassName();

		JCodeModel codeModel = TranspilerUtil.translate(pmml, className);

		TranspilerUtil.compile(codeModel);

		ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

		return PMMLUtil.load(clazzLoader);
	}

	public String getClassName(){
		return this.className;
	}

	private void setClassName(String className){
		this.className = className;
	}
}