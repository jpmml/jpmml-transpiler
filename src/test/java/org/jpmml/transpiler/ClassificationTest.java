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

import org.dmg.pmml.FieldName;
import org.jpmml.evaluator.testing.FloatEquivalence;
import org.jpmml.evaluator.testing.PMMLEquivalence;
import org.junit.Test;

public class ClassificationTest extends TranspilerTest {

	public ClassificationTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Test
	public void evaluateDecisionTreeAudit() throws Exception {
		evaluate("DecisionTree", "Audit");
	}

	@Test
	public void evaluateGradientBoostingAudit() throws Exception {
		evaluate("GradientBoosting", "Audit");
	}

	@Test
	public void evaluateLightGBMAudit() throws Exception {
		evaluate("LightGBM", "Audit");
	}

	@Test
	public void evaluateLightGBMAuditNA() throws Exception {
		evaluate("LightGBM", "AuditNA");
	}

	@Test
	public void evaluateLogisticRegressionAudit() throws Exception {
		evaluate("LogisticRegression", "Audit");
	}

	@Test
	public void evaluateRandomForestAudit() throws Exception {
		evaluate("RandomForest", "Audit");
	}

	@Test
	public void evaluateXGBoostAudit() throws Exception {
		evaluate("XGBoost", "Audit", excludeFields(FieldName.create("probability(0)")), new FloatEquivalence(12));
	}

	@Test
	public void evaluateXGBoostAuditNA() throws Exception {
		evaluate("XGBoost", "AuditNA", excludeFields(FieldName.create("probability(0)")), new FloatEquivalence(8));
	}

	@Test
	public void evaluateLinearSVCSentiment() throws Exception {
		evaluate("LinearSVC", "Sentiment");
	}

	@Test
	public void evaluateLogisticRegressionSentiment() throws Exception {
		evaluate("LogisticRegression", "Sentiment");
	}

	@Test
	public void evaluateDecisionTreeIris() throws Exception {
		evaluate("DecisionTree", "Iris");
	}

	@Test
	public void evaluateGradientBoostingIris() throws Exception {
		evaluate("GradientBoosting", "Iris");
	}

	@Test
	public void evaluateLightGBMIris() throws Exception {
		evaluate("LightGBM", "Iris");
	}

	@Test
	public void evaluateLogisticRegressionIris() throws Exception {
		evaluate("LogisticRegression", "Iris");
	}

	@Test
	public void evaluateRandomForestIris() throws Exception {
		evaluate("RandomForest", "Iris");
	}

	@Test
	public void evaluateXGBoostIris() throws Exception {
		evaluate("XGBoost", "Iris", new FloatEquivalence(8));
	}
}