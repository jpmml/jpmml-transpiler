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
package org.jpmml.transpiler.testing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import com.google.common.base.Equivalence;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.junit.Test;

public class VectorizationTest extends TranspilerBatchTest implements Algorithms, Datasets {

	public VectorizationTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Override
	public TranspilerBatch createBatch(String algorithm, String dataset, Predicate<ResultField> columnFilter, Equivalence<Object> equivalence){
		TranspilerBatch result = new TranspilerBatch(algorithm, dataset, columnFilter, equivalence){

			@Override
			public VectorizationTest getArchiveBatchTest(){
				return VectorizationTest.this;
			}

			@Override
			public List<Map<String, List<Number>>> getInput() throws IOException {
				List<? extends Map<String, ?>> input = super.getInput();

				return input.stream()
					.map(row -> collectCells(row, "x"))
					.collect(Collectors.toList());
			}

			private Map<String, List<Number>> collectCells(Map<String, ?> row, String name){
				List<Number> vector = new ArrayList<>();

				for(int i = 0; i < 256; i++){
					Object value = row.get(name + "_" + i);

					if(value == null){
						break;
					}

					vector.add(new Double(value.toString()));
				}

				return Collections.singletonMap(name, vector);
			}
		};

		return result;
	}

	@Test
	public void evaluateDecisionTreeIrisVec() throws Exception {
		evaluate(DECISION_TREE, IRIS_VEC);
	}

	@Test
	public void evaluateRandomForestIrisVec() throws Exception {
		evaluate(RANDOM_FOREST, IRIS_VEC);
	}
}