/*
 * Copyright (c) 2017 Villu Ruusmann
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
package com.jpmml.transpiler;

import java.util.Collections;

import com.jpmml.translator.PMMLObjectUtil;
import com.jpmml.translator.TranslationContext;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import org.dmg.pmml.PMML;
import org.jpmml.codemodel.CompilerUtil;
import org.jpmml.codemodel.JServiceConfigurationFile;
import org.jpmml.evaluator.visitors.ValueOptimizer;
import org.jpmml.model.VisitorBattery;
import org.jpmml.model.visitors.NodeScoreOptimizer;
import org.jpmml.model.visitors.RowCleaner;

public class TranspilerUtil {

	private TranspilerUtil(){
	}

	static
	public JCodeModel transpile(PMML pmml) throws Exception {
		JCodeModel codeModel = new JCodeModel();

		JClass pmmlClazz = codeModel.ref(PMML.class);

		VisitorBattery visitorBattery = new VisitorBattery();
		visitorBattery.add(RowCleaner.class);
		visitorBattery.add(NodeScoreOptimizer.class);
		visitorBattery.add(ValueOptimizer.class);

		visitorBattery.applyTo(pmml);

		TranslationContext context = new TranslationContext(pmml, codeModel);

		JClass transpiledPmmlClazz = PMMLObjectUtil.createClass(pmml, context);

		JPackage servicePackage = codeModel._package("META-INF/services");
		servicePackage.addResourceFile(new JServiceConfigurationFile(pmmlClazz, Collections.<JClass>singletonList(transpiledPmmlClazz)));

		try {
			CompilerUtil.compile(codeModel);
		} catch(Exception e){
			e.printStackTrace(System.err);
		}

		return codeModel;
	}
}