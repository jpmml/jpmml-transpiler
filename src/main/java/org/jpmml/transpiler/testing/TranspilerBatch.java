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
package org.jpmml.transpiler.testing;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.writer.FileCodeWriter;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.EvaluatorBuilder;
import org.jpmml.evaluator.FieldNameSet;
import org.jpmml.evaluator.FunctionNameStack;
import org.jpmml.evaluator.HasPMML;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.OutputFilters;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.SimpleArchiveBatch;
import org.jpmml.model.SerializationUtil;
import org.jpmml.translator.visitors.DefaultModelTranslatorBattery;
import org.jpmml.transpiler.InMemoryTranspiler;
import org.jpmml.transpiler.Transpiler;
import org.jpmml.transpiler.TranspilerTransformer;

abstract
public class TranspilerBatch extends SimpleArchiveBatch {

	private String explodedArchiveDir = System.getProperty(TranspilerBatch.class.getName() + "." + "explodedArchiveDir", null);


	public TranspilerBatch(String algorithm, String dataset, Predicate<ResultField> columnFilter, Equivalence<Object> equivalence){
		super(algorithm, dataset, columnFilter, equivalence);
	}

	@Override
	abstract
	public TranspilerBatchTest getArchiveBatchTest();

	@Override
	public Evaluator getEvaluator() throws Exception {
		Evaluator evaluator = super.getEvaluator();

		validateEvaluator(evaluator);

		return evaluator;
	}

	@Override
	public EvaluatorBuilder getEvaluatorBuilder() throws Exception {
		TranspilerBatchTest transpilerTest = getArchiveBatchTest();

		LoadingModelEvaluatorBuilder evaluatorBuilder = new LoadingModelEvaluatorBuilder();
		evaluatorBuilder.setVisitors(new DefaultModelTranslatorBattery());

		// XXX
		evaluatorBuilder.setDerivedFieldGuard(new FieldNameSet(8));
		evaluatorBuilder.setFunctionGuard(new FunctionNameStack(4));

		evaluatorBuilder.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS);

		try(InputStream is = open(getPmmlPath())){
			evaluatorBuilder.load(is);
		}

		PMML xmlPmml = evaluatorBuilder.getPMML();

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

				if(TranspilerBatch.this.explodedArchiveDir != null){
					File explodedArchiveDir = new File(TranspilerBatch.this.explodedArchiveDir + "/" + getAlgorithm() + getDataset());

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

		evaluatorBuilder.transform(new TranspilerTransformer(transpiler));

		PMML javaPmml = evaluatorBuilder.getPMML();

		Visitor checker = transpilerTest.getChecker();
		if(checker != null){
			checker.applyTo(javaPmml);
		}

		return evaluatorBuilder;
	}

	protected void validateEvaluator(Evaluator evaluator) throws Exception {
		HasPMML hasPMML = (HasPMML)evaluator;

		PMML pmml = hasPMML.getPMML();

		Class<? extends PMML> pmmlClazz = pmml.getClass();

		ClassLoader clazzLoader = pmmlClazz.getClassLoader();

		SerializationUtil.clone(evaluator, clazzLoader);
	}
}