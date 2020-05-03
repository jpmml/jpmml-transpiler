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

import org.jpmml.evaluator.testing.FloatEquivalence;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.junit.Test;

public class RegressionTest extends TranspilerTest {

	public RegressionTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Test
	public void evaluateAdaBoostAuto() throws Exception {
		evaluate("AdaBoost", "Auto");
	}

	@Test
	public void evaluateDecisionTreeAuto() throws Exception {
		evaluate("DecisionTree", "Auto");
	}

	@Test
	public void evaluateGradientBoostingAuto() throws Exception {
		evaluate("GradientBoosting", "Auto");
	}

	@Test
	public void evaluateLightGBMAuto() throws Exception {
		evaluate("LightGBM", "Auto");
	}

	@Test
	public void evaluateLightGBMAutoNA() throws Exception {
		evaluate("LightGBM", "AutoNA");
	}

	@Test
	public void evaluateLinearRegressionAuto() throws Exception {
		evaluate("LinearRegression", "Auto");
	}

	@Test
	public void evaluateRandomForestAuto() throws Exception {
		evaluate("RandomForest", "Auto");
	}

	@Test
	public void evaluateVotingEnsembleAuto() throws Exception {
		evaluate("VotingEnsemble", "Auto");
	}

	@Test
	public void evaluateXGBoostAuto() throws Exception {
		evaluate("XGBoost", "Auto", new FloatEquivalence(1));
	}

	@Test
	public void evaluateXGBoostAutoNA() throws Exception {
		evaluate("XGBoost", "AutoNA", new FloatEquivalence(1));
	}
}