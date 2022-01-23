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

import org.jpmml.evaluator.testing.FloatEquivalence;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.junit.Test;

public class RegressionTest extends TranspilerBatchTest implements Algorithms, Datasets {

	public RegressionTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Test
	public void evaluateAdaBoostAuto() throws Exception {
		evaluate(ADA_BOOST, AUTO);
	}

	@Test
	public void evaluateDecisionTreeAuto() throws Exception {
		evaluate(DECISION_TREE, AUTO);
	}

	@Test
	public void evaluateGradientBoostingAuto() throws Exception {
		evaluate(GRADIENT_BOOSTING, AUTO);
	}

	@Test
	public void evaluateLightGBMAuto() throws Exception {
		evaluate(LIGHT_GBM, AUTO);
	}

	@Test
	public void evaluateLightGBMAutoNA() throws Exception {
		evaluate(LIGHT_GBM, AUTO_NA);
	}

	@Test
	public void evaluateLinearRegressionAuto() throws Exception {
		evaluate(LINEAR_REGRESSION, AUTO);
	}

	@Test
	public void evaluateRandomForestAuto() throws Exception {
		evaluate(RANDOM_FOREST, AUTO);
	}

	@Test
	public void evaluateVotingEnsembleAuto() throws Exception {
		evaluate(VOTING_ENSEMBLE, AUTO);
	}

	@Test
	public void evaluateXGBoostAuto() throws Exception {
		evaluate(XGBOOST, AUTO, new FloatEquivalence(8));
	}

	@Test
	public void evaluateXGBoostAutoNA() throws Exception {
		evaluate(XGBOOST, AUTO_NA, new FloatEquivalence(6));
	}
}