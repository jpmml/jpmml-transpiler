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

import java.util.function.Predicate;

import com.google.common.base.Equivalence;
import org.dmg.pmml.Visitor;
import org.jpmml.evaluator.ResultField;
import org.jpmml.evaluator.testing.SimpleArchiveBatchTest;

public class TranspilerBatchTest extends SimpleArchiveBatchTest {

	private Visitor checker = null;


	public TranspilerBatchTest(Equivalence<Object> equivalence){
		this(equivalence, new DefaultTranslationChecker());
	}

	public TranspilerBatchTest(Equivalence<Object> equivalence, Visitor checker){
		super(equivalence);

		setChecker(checker);
	}

	@Override
	public TranspilerBatch createBatch(String algorithm, String dataset, Predicate<ResultField> columnFilter, Equivalence<Object> equivalence){
		TranspilerBatch result = new TranspilerBatch(algorithm, dataset, columnFilter, equivalence){

			@Override
			public TranspilerBatchTest getArchiveBatchTest(){
				return TranspilerBatchTest.this;
			}
		};

		return result;
	}

	public Visitor getChecker(){
		return this.checker;
	}

	public void setChecker(Visitor checker){
		this.checker = checker;
	}
}