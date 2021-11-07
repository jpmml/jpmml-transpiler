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

import org.dmg.pmml.FieldName;

public interface Datasets {

	String AUDIT = "Audit";
	String AUDIT_NA = AUDIT + "NA";
	String AUTO = "Auto";
	String AUTO_NA = AUTO + "NA";
	String IRIS = "Iris";
	String SENTIMENT = "Sentiment";

	FieldName AUDIT_ADJUSTED = FieldName.create("Adjusted");
	FieldName AUDIT_PROBABILITY_TRUE = FieldName.create("probability(1)");
	FieldName AUDIT_PROBABILITY_FALSE = FieldName.create("probability(0)");

	FieldName SENTIMENT_PROBABILITY_TRUE = FieldName.create("probability(1)");
	FieldName SENTIMENT_PROBABILITY_FALSE = FieldName.create("probability(0)");

	FieldName SKLEARN_PREDICT_OUTLIER = FieldName.create("predict(outlier)");
}