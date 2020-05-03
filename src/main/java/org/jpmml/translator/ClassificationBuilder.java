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
package org.jpmml.translator;

import java.util.Arrays;

import com.sun.codemodel.JExpression;
import org.dmg.pmml.DataType;
import org.jpmml.evaluator.Classification;
import org.jpmml.evaluator.TargetUtil;

/**
 * @see Classification
 */
public class ClassificationBuilder extends JVarBuilder {

	public ClassificationBuilder(TranslationContext context){
		super(context);
	}

	public ClassificationBuilder declare(String name, JExpression init){
		TranslationContext context = getContext();

		return (ClassificationBuilder)declare(context.ref(Classification.class).narrow(Arrays.asList(context.ref(Object.class), context.getNumberTypeVariable())), name, init);
	}

	public ClassificationBuilder computeResult(DataType dataType){
		return (ClassificationBuilder)staticUpdate(TargetUtil.class, "computeResult", dataType);
	}
}