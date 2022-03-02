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
import java.util.Objects;

import org.dmg.pmml.PMML;
import org.jpmml.evaluator.PMMLTransformer;

public class TranspilerTransformer implements PMMLTransformer<IOException> {

	private Transpiler transpiler = null;


	public TranspilerTransformer(Transpiler transpiler){
		setTranspiler(transpiler);
	}

	@Override
	public PMML apply(PMML pmml) throws IOException {
		Transpiler transpiler = getTranspiler();

		return transpiler.transpile(pmml);
	}

	public Transpiler getTranspiler(){
		return this.transpiler;
	}

	private void setTranspiler(Transpiler archiver){
		this.transpiler = Objects.requireNonNull(archiver);
	}
}