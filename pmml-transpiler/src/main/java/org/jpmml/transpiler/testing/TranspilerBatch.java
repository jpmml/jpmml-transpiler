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
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.writer.FileCodeWriter;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Visitor;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.evaluator.OutputFilters;
import org.jpmml.evaluator.PMMLTransformer;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.SimpleArchiveBatch;
import org.jpmml.model.JavaSerializer;
import org.jpmml.model.SerializationUtil;
import org.jpmml.translator.visitors.ModelTranslatorVisitorBattery;
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
	protected LoadingModelEvaluatorBuilder createLoadingModelEvaluatorBuilder(){
		LoadingModelEvaluatorBuilder evaluatorBuilder = super.createLoadingModelEvaluatorBuilder();

		evaluatorBuilder
			.setVisitors(new ModelTranslatorVisitorBattery())
			.setOutputFilter(OutputFilters.KEEP_FINAL_RESULTS);

		return evaluatorBuilder;
	}

	@Override
	protected List<PMMLTransformer<?>> getTransformers(){
		Transpiler transpiler = new InMemoryTranspiler(null){

			@Override
			public PMML transpile(PMML xmlPMML) throws IOException {
				PMML javaPMML = super.transpile(xmlPMML);

				validatePMML(javaPMML);

				return javaPMML;
			}

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

		return Collections.singletonList(new TranspilerTransformer(transpiler));
	}

	protected void validatePMML(PMML pmml) throws IOException {
		TranspilerBatchTest transpilerTest = getArchiveBatchTest();

		Visitor checker = transpilerTest.getChecker();
		if(checker != null){
			checker.applyTo(pmml);
		}

		Class<? extends PMML> pmmlClazz = pmml.getClass();

		ClassLoader clazzLoader = pmmlClazz.getClassLoader();

		JavaSerializer serializer = new JavaSerializer(clazzLoader);

		try {
			@SuppressWarnings("unused")
			PMML clonedPMML = SerializationUtil.clone(serializer, pmml);
		} catch(Exception e){
			throw new RuntimeException(e);
		}
	}
}