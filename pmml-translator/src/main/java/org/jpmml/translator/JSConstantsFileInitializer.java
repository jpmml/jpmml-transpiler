/*
 * Copyright (c) 2025 Villu Ruusmann
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import javax.xml.namespace.QName;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import org.jpmml.evaluator.TokenizedString;

public class JSConstantsFileInitializer extends JResourceInitializer {

	private List<Object> objects = null;

	private JVar dataLoaderVar = null;


	public JSConstantsFileInitializer(String name, TranslationContext context){
		super(context);

		JDefinedClass owner;

		try {
			owner = getResourceOwner(context);
		} catch(IllegalArgumentException iae){
			owner = context.getOwner();
		}

		JSConstantsFile constantsFile = JSConstantsFileInitializer.ensureJSConstantsFile(context);

		this.objects = new ArrayList<>();

		constantsFile.put(name, this.objects);

		JBlock resourceStmt = new JBlock(false, false);

		JClass dataLoaderClazz = context.ref("org.jpmml.teavm.DataLoader");

		this.dataLoaderVar = resourceStmt.decl(dataLoaderClazz, "dataLoader", context._new(dataLoaderClazz, name));

		JBlock init = owner.init();

		JStatement tryWithResources = createTryWithResources(this.dataLoaderVar);

		init.add(tryWithResources);
	}

	@Override
	public JInvocation initQNameArray(QName[] names){
		String[][] stringMatrices = Arrays.stream(names)
			.map(name -> new String[]{name.getNamespaceURI(), name.getLocalPart(), name.getPrefix()})
			.toArray(String[][]::new);

		this.objects.add(stringMatrices);

		return this.dataLoaderVar.invoke("readQNames");
	}

	@Override
	public JInvocation initTokenizedStringArray(TokenizedString[] tokenizedStrings){
		String[][] stringMatrices = Arrays.stream(tokenizedStrings)
			.map(tokenizedString -> tokenizedString.getTokens())
			.toArray(String[][]::new);

		this.objects.add(stringMatrices);

		return this.dataLoaderVar.invoke("readTokenizedStrings");
	}

	@Override
	public JInvocation initObjectArray(JType type, Object[] values){
		this.objects.add(values);

		return this.dataLoaderVar.invoke("read" + (type.elementType()).name() + "s");
	}

	@Override
	public JInvocation initNumberArrayList(JType type, List<Number[]> values){
		this.objects.add(values);

		return this.dataLoaderVar.invoke("read" + (type.elementType()).name() + "ArrayList");
	}

	@Override
	public JInvocation initNumberMatrixList(JType type, List<Number[][]> values, int length){
		this.objects.add(values);

		return this.dataLoaderVar.invoke("read" + ((type.elementType()).elementType()).name() + "MatrixList");
	}

	@Override
	public JInvocation initNumberMap(JType keyType, JType valueType, Map<?, Number> map){
		this.objects.add(map);

		return this.dataLoaderVar.invoke("read" + keyType.name() + valueType.name() + "Map");
	}

	static
	public JSConstantsFile ensureJSConstantsFile(TranslationContext context){

		if(JSConstantsFileInitializer.FILE == null){
			JSConstantsFileInitializer.FILE = new JSConstantsFile();

			JCodeModel codeModel = context.getCodeModel();

			JPackage _package = codeModel.rootPackage();

			_package.addResourceFile(JSConstantsFileInitializer.FILE);
		}

		return JSConstantsFileInitializer.FILE;
	}

	private static JSConstantsFile FILE = null;
}