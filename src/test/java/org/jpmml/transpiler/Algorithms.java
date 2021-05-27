/*
 * Copyright (c) 2021 Villu Ruusmann
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

public interface Algorithms {

	String ADA_BOOST = "AdaBoost";
	String DECISION_TREE = "DecisionTree";
	String GRADIENT_BOOSTING = "GradientBoosting";
	String ISOLATION_FOREST = "IsolationForest";
	String LIGHT_GBM = "LightGBM";
	String LINEAR_REGRESSION = "LinearRegression";
	String LINEAR_SVC = "LinearSVC";
	String LOGISTIC_REGRESSION = "LogisticRegression";
	String RANDOM_FOREST = "RandomForest";
	String VOTING_ENSEMBLE = "VotingEnsemble";
	String XGBOOST = "XGBoost";
}