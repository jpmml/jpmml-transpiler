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
package org.jpmml.transpiler.testing;

import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.junit.jupiter.api.Test;

public class AnomalyDetectionTest extends TranspilerBatchTest implements Algorithms, Datasets {

	public AnomalyDetectionTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Test
	public void evaluateIsolationForestAuto() throws Exception {
		evaluate(ISOLATION_FOREST, AUTO, excludeFields(SKLEARN_PREDICT_OUTLIER));
	}
}