/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.codemodel.JCodeModelClassLoader;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.HasPMML;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.IntegrationTestBatch;
import org.jpmml.model.PMMLUtil;
import org.jpmml.model.SerializationUtil;

abstract
public class TranspilerTestBatch extends IntegrationTestBatch {

	public TranspilerTestBatch(String name, String dataset, Predicate<ResultField> predicate, Equivalence<Object> equivalence){
		super(name, dataset, predicate, equivalence);
	}

	@Override
	abstract
	public TranspilerTest getIntegrationTest();

	@Override
	public PMML getPMML() throws Exception {
		TranspilerTest transpilerTest = getIntegrationTest();

		PMML xmlPmml = super.getPMML();

		JCodeModel codeModel = TranspilerUtil.translate(xmlPmml, null);

		TranspilerUtil.compile(codeModel);

		ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

		PMML javaPmml = PMMLUtil.load(clazzLoader);

		Visitor checker = transpilerTest.getChecker();
		if(checker != null){
			checker.applyTo(javaPmml);
		}

		return javaPmml;
	}

	@Override
	protected void validateEvaluator(Evaluator evaluator) throws Exception {
		HasPMML hasPMML = (HasPMML)evaluator;

		PMML pmml = hasPMML.getPMML();

		Class<? extends PMML> pmmlClazz = pmml.getClass();

		ClassLoader clazzLoader = pmmlClazz.getClassLoader();

		SerializationUtil.clone(evaluator, clazzLoader);
	}
}