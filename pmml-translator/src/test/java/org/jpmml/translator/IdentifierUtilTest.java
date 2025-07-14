/*
 * Copyright (c) 2022 Villu Ruusmann
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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class IdentifierUtilTest {

	@Test
	public void sanitize(){
		assertEquals("x", IdentifierUtil.sanitize("X"));
		assertEquals("x1", IdentifierUtil.sanitize("X1"));

		assertEquals("x_1", IdentifierUtil.sanitize("X_1"));
		assertEquals("x_1", IdentifierUtil.sanitize("X 1"));
		assertEquals("x_A", IdentifierUtil.sanitize("X\t\tA"));

		assertEquals("x", IdentifierUtil.sanitize("-X"));
		assertEquals("x", IdentifierUtil.sanitize("X-"));
		assertEquals("x_1", IdentifierUtil.sanitize("X-1"));
		assertEquals("x_A", IdentifierUtil.sanitize("X--A"));
		assertEquals("x_1_A", IdentifierUtil.sanitize("X-1-A"));

		assertEquals("x1", IdentifierUtil.sanitize("X[1]"));
		assertEquals("x1", IdentifierUtil.sanitize("X[-1]"));

		assertEquals("_1", IdentifierUtil.sanitize("1"));
		assertEquals("_1", IdentifierUtil.sanitize("-1"));
		assertEquals("_1_1", IdentifierUtil.sanitize("1-1"));
		assertEquals("_1_X", IdentifierUtil.sanitize("1-X"));
	}
}