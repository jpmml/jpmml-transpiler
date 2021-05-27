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

public class ClassificationTest extends TranspilerTest implements Algorithms, Datasets {

	public ClassificationTest(){
		super(new PMMLEquivalence(1e-13, 1e-13));
	}

	@Test
	public void evaluateDecisionTreeAudit() throws Exception {
		evaluate(DECISION_TREE, AUDIT);
	}

	@Test
	public void evaluateGradientBoostingAudit() throws Exception {
		evaluate(GRADIENT_BOOSTING, AUDIT);
	}

	@Test
	public void evaluateLightGBMAudit() throws Exception {
		evaluate(LIGHT_GBM, AUDIT);
	}

	@Test
	public void evaluateLightGBMAuditNA() throws Exception {
		evaluate(LIGHT_GBM, AUDIT_NA);
	}

	@Test
	public void evaluateLogisticRegressionAudit() throws Exception {
		evaluate(LOGISTIC_REGRESSION, AUDIT);
	}

	@Test
	public void evaluateRandomForestAudit() throws Exception {
		evaluate(RANDOM_FOREST, AUDIT);
	}

	@Test
	public void evaluateXGBoostAudit() throws Exception {
		evaluate(XGBOOST, AUDIT, excludeFields(AUDIT_PROBABILITY_FALSE), new FloatEquivalence(12));
	}

	@Test
	public void evaluateXGBoostAuditNA() throws Exception {
		evaluate(XGBOOST, AUDIT_NA, excludeFields(AUDIT_PROBABILITY_FALSE), new FloatEquivalence(8));
	}

	@Test
	public void evaluateLinearSVCSentiment() throws Exception {
		evaluate(LINEAR_SVC, SENTIMENT);
	}

	@Test
	public void evaluateLogisticRegressionSentiment() throws Exception {
		evaluate(LOGISTIC_REGRESSION, SENTIMENT);
	}

	@Test
	public void evaluateRandomForestSentiment() throws Exception {
		evaluate(RANDOM_FOREST, SENTIMENT);
	}

	@Test
	public void evaluateXGBoostSentiment() throws Exception {
		evaluate(XGBOOST, SENTIMENT, excludeFields(SENTIMENT_PROBABILITY_FALSE), new FloatEquivalence(8));
	}

	@Test
	public void evaluateDecisionTreeIris() throws Exception {
		evaluate(DECISION_TREE, IRIS);
	}

	@Test
	public void evaluateGradientBoostingIris() throws Exception {
		evaluate(GRADIENT_BOOSTING, IRIS);
	}

	@Test
	public void evaluateLightGBMIris() throws Exception {
		evaluate(LIGHT_GBM, IRIS);
	}

	@Test
	public void evaluateLogisticRegressionIris() throws Exception {
		evaluate(LOGISTIC_REGRESSION, IRIS);
	}

	@Test
	public void evaluateRandomForestIris() throws Exception {
		evaluate(RANDOM_FOREST, IRIS);
	}

	@Test
	public void evaluateXGBoostIris() throws Exception {
		evaluate(XGBOOST, IRIS, new FloatEquivalence(12));
	}
}