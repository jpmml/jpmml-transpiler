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
import org.jpmml.model.PMMLUtil;

public class InMemoryTranspiler extends Transpiler {

	public InMemoryTranspiler(String className){
		super(className);
	}

	@Override
	public PMML transpile(PMML pmml) throws IOException {
		JCodeModel codeModel = compile(translate(pmml));

		ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

		return PMMLUtil.load(clazzLoader);
	}
}