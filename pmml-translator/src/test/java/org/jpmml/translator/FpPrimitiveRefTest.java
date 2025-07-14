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

import java.util.Arrays;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JVar;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class FpPrimitiveRefTest extends OperableRefTest {

	@Test
	public void generate(){
		JCodeModel codeModel = new JCodeModel();

		JBlock block = new JBlock();

		JVar variable = block.decl(codeModel.DOUBLE, "x");

		FpPrimitiveRef primitiveRef = new FpPrimitiveRef(variable);

		assertEquals("(x!=x)", generate(primitiveRef.isMissing()));
		assertEquals("(x==x)", generate(primitiveRef.isNotMissing()));

		assertEquals("(x==1.0D)", generate(primitiveRef.equalTo(1d, null)));
		assertEquals("((x==x)&&(x!=1.0D))", generate(primitiveRef.notEqualTo(1d, null)));

		assertEquals("((x==1.0D)||(x==2.0D))", generate(primitiveRef.isIn(Arrays.asList(2d, 1d), null)));
		assertEquals("((x==x)&&((x!=1.0D)&&(x!=2.0D)))", generate(primitiveRef.isNotIn(Arrays.asList(2d, 1d), null)));
	}
}