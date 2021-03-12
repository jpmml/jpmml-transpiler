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

import java.io.File;
import java.io.IOException;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.writer.FileCodeWriter;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.HasPMML;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.IntegrationTestBatch;
import org.jpmml.model.SerializationUtil;

abstract
public class TranspilerTestBatch extends IntegrationTestBatch {

	private String explodedArchiveDir = System.getProperty(TranspilerTestBatch.class.getName() + "." + "explodedArchiveDir", null);


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

		Transpiler transpiler = new InMemoryTranspiler(null){

			@Override
			protected JCodeModel translate(PMML pmml) throws IOException {
				JCodeModel codeModel = super.translate(pmml);

				// Export sources and resources
				export(codeModel);

				return codeModel;
			}

			@Override
			protected JCodeModel compile(JCodeModel codeModel) throws IOException {
				codeModel = super.compile(codeModel);

				// Re-export sources and resources, export classes
				export(codeModel);

				return codeModel;
			}

			private void export(JCodeModel codeModel) throws IOException {

				if(TranspilerTestBatch.this.explodedArchiveDir != null){
					File explodedArchiveDir = new File(TranspilerTestBatch.this.explodedArchiveDir + "/" + getName() + getDataset());

					if(!explodedArchiveDir.exists()){
						boolean success = explodedArchiveDir.mkdirs();

						if(!success){
							throw new IOException();
						}
					}

					CodeWriter codeWriter = new FileCodeWriter(explodedArchiveDir);

					codeModel.build(codeWriter);
				}
			}
		};

		PMML javaPmml = transpiler.transpile(xmlPmml);

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