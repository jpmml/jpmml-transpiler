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
package org.jpmml.transpiler;

import java.io.InputStream;

import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.model.PMMLUtil;
import org.jpmml.model.ResourceUtil;
import org.jpmml.model.visitors.PredicateInterner;
import org.junit.Test;

public class ConstantPredicateTest {

	@Test
	public void transpile() throws Exception {
		PMML xmlPmml;

		try(InputStream is = ResourceUtil.getStream(ConstantPredicateTest.class)){
			xmlPmml = PMMLUtil.unmarshal(is);
		}

		Visitor visitor = new PredicateInterner();
		visitor.applyTo(xmlPmml);

		Transpiler transpiler = new InMemoryTranspiler(null);

		PMML javaPmml = transpiler.transpile(xmlPmml);
	}
}