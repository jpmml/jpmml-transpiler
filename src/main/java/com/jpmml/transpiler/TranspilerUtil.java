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

import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.jar.Manifest;

import com.jpmml.translator.PMMLObjectUtil;
import com.jpmml.translator.TranslationContext;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JPackage;
import org.dmg.pmml.PMML;
import org.jpmml.codemodel.ArchiverUtil;
import org.jpmml.codemodel.CompilerUtil;
import org.jpmml.codemodel.JServiceConfigurationFile;
import org.jpmml.codemodel.JarCodeWriter;
import org.jpmml.codemodel.MarkedCodeWriter;
import org.jpmml.evaluator.visitors.ValueOptimizer;
import org.jpmml.model.VisitorBattery;
import org.jpmml.model.visitors.NodeScoreOptimizer;
import org.jpmml.model.visitors.RowCleaner;

public class TranspilerUtil {

	private TranspilerUtil(){
	}

	static
	public JCodeModel translate(PMML pmml, String className){
		VisitorBattery visitorBattery = new VisitorBattery();
		visitorBattery.add(RowCleaner.class);
		visitorBattery.add(NodeScoreOptimizer.class);
		visitorBattery.add(ValueOptimizer.class);

		visitorBattery.applyTo(pmml);

		JCodeModel codeModel = new JCodeModel();

		TranslationContext context = new TranslationContext(pmml, codeModel);

		JClass transpiledPmmlClazz = PMMLObjectUtil.createClass(pmml, className, context);

		JPackage servicePackage = codeModel._package("META-INF/services");
		servicePackage.addResourceFile(new JServiceConfigurationFile(context.ref(PMML.class), Collections.<JClass>singletonList(transpiledPmmlClazz)));

		return codeModel;
	}

	static
	public void compile(JCodeModel codeModel) throws IOException {
		CompilerUtil.compile(codeModel);
	}

	static
	public void archive(JCodeModel codeModel, OutputStream os) throws IOException {
		Manifest manifest = ArchiverUtil.createManifest(TranspilerUtil.class);

		CodeWriter codeWriter = new MarkedCodeWriter(new JarCodeWriter(os, manifest), TranspilerUtil.HEADER);

		codeModel.build(codeWriter);
	}

	private static final String HEADER;

	static {
		String[] lines = {
			"/*",
			" * Copyright (c) 2019 Villu Ruusmann",
			" */"
		};
		String lineSeparator = System.getProperty("line.separator");

		StringBuilder sb = new StringBuilder();

		for(String line : lines){
			sb.append(line).append(lineSeparator);
		}

		HEADER = sb.toString();
	}
}