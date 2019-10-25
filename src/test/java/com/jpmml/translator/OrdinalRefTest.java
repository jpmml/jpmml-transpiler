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
package com.jpmml.translator;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JVar;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class OrdinalRefTest extends OperableRefTest {

	@Test
	public void chunk(){
		OrdinalEncoder encoder = new OrdinalEncoder(OrdinalRefTest.values);

		assertEquals(Collections.emptyList(), OrdinalRef.chunk(encoder, Collections.emptyList()));

		assertEquals(Arrays.asList(Arrays.asList(1, 2, 3, 4, 16)), OrdinalRef.chunk(encoder, Arrays.asList(0d, 1d, 2d, 1d, 3d, 0d, 15d)));
		assertEquals(Arrays.asList(Arrays.asList(1, 32), Arrays.asList(33, 64), Arrays.asList(65, 96), Arrays.asList(97, 128), Arrays.asList(129)), OrdinalRef.chunk(encoder, Arrays.asList(0d, 31d, 32d, 63d, 64d, 95d, 96d, 127d, 128d)));
	}

	@Test
	public void firstChunk(){
		OrdinalRef.Chunk chunk = new OrdinalRef.Chunk();

		try {
			chunk.isDense();

			fail();
		} catch(IllegalStateException ise){
			// Ignored
		}

		JBlock block = new JBlock();

		JVar variable = block.decl(null, "x");

		chunk.add(2);

		assertEquals(Arrays.asList(2), chunk);
		assertTrue(chunk.isDense());

		assertEquals("(x==2)", generate(chunk.isIn(variable)));
		assertEquals("(x!=2)", generate(chunk.isNotIn(variable)));

		chunk.add(4);

		assertEquals(Arrays.asList(2, 4), chunk);
		assertFalse(chunk.isDense());

		assertEquals("((x==2)||(x==4))", generate(chunk.isIn(variable)));
		assertEquals("((x!=2)&&(x!=4))", generate(chunk.isNotIn(variable)));

		chunk.add(1, 3);

		assertEquals(Arrays.asList(2, 3, 4), chunk);
		assertTrue(chunk.isDense());

		assertEquals("((x>=2)&&(x<=4))", generate(chunk.isIn(variable)));
		assertEquals("((x<2)||(x>4))", generate(chunk.isNotIn(variable)));

		chunk.add(7);
		chunk.add(32);

		int bitSet = 0b1110;

		bitSet |= (1 << (7 - 1));
		bitSet |= (1 << (32 - 1));

		assertEquals(5, Integer.bitCount(bitSet));

		assertEquals(Arrays.asList(2, 3, 4, 7, 32), chunk);
		assertFalse(chunk.isDense());

		assertEquals("isSet(" + Integer.toString(bitSet) + ",(x-1))", generate(chunk.isIn(variable)));
		assertEquals("(!isSet(" + Integer.toString(bitSet) + ",(x-1)))", generate(chunk.isNotIn(variable)));
	}

	@Test
	public void secondChunk(){
		OrdinalRef.Chunk chunk = new OrdinalRef.Chunk();

		JBlock block = new JBlock();

		JVar variable = block.decl(null, "x");

		chunk.addAll(Arrays.asList(35, 36, 37, 40));

		int bitSet = 0;

		bitSet |= (1 << (35 - 35));
		bitSet |= (1 << (36 - 35));
		bitSet |= (1 << (37 - 35));
		bitSet |= (1 << (40 - 35));

		assertEquals(0b100111, bitSet);

		assertEquals(Arrays.asList(35, 36, 37, 40), chunk);
		assertFalse(chunk.isDense());

		assertEquals("isSet(" + Integer.toString(bitSet) + ",(x-35))", generate(chunk.isIn(variable)));
		assertEquals("(!isSet(" + Integer.toString(bitSet) + ",(x-35)))", generate(chunk.isNotIn(variable)));
	}

	private static final Set<Double> values = new LinkedHashSet<>();

	static {
		for(int i = 0; i <= 255; i++){
			OrdinalRefTest.values.add((double)i);
		}
	}
}