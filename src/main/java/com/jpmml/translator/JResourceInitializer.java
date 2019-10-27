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
package com.jpmml.translator;

import java.io.DataInputStream;
import java.io.IOException;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JResourceFile;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class JResourceInitializer implements JStatement {

	private JVar dataInputVar = null;

	private JBlock tryBody = new JBlock();

	private JVar ioeVar = null;

	private JBlock catchBody = new JBlock();


	public JResourceInitializer(JDefinedClass owner, JResourceFile resourceFile){
		JCodeModel codeModel = owner.owner();

		JBlock resourceStmt = new JBlock(false, false);

		JType dataInputStreamClazz = codeModel.ref(DataInputStream.class);

		JExpression isExpr = (JExpr.dotclass(owner)).invoke("getResourceAsStream").arg(resourceFile.name());

		this.dataInputVar = resourceStmt.decl(dataInputStreamClazz, "dataInput", (JExpr._new(dataInputStreamClazz)).arg(isExpr));

		JBlock catchStmt = new JBlock(false, false);

		JType ioExceptionClazz = codeModel.ref(IOException.class);

		this.ioeVar = catchStmt.decl(ioExceptionClazz, "ioe");

		JType exeptionInInitializerClazz = codeModel.ref(ExceptionInInitializerError.class);

		this.catchBody._throw((JExpr._new(exeptionInInitializerClazz)).arg(this.ioeVar));

		JBlock init = owner.init();

		init.add(this);
	}

	public void readNumbers(JVar arrayVar, JInvocation invocation, JExpression countExpr){
		this.tryBody.assign(arrayVar, invocation.arg(this.dataInputVar).arg(countExpr));
	}

	public void readNumberArrays(JVar arrayVar, JInvocation invocation, JExpression countExpr, JExpression lengthExpr){
		this.tryBody.assign(arrayVar, invocation.arg(this.dataInputVar).arg(countExpr).arg(lengthExpr));
	}

	@Override
	public void state(JFormatter formatter){
		formatter
			.p("try(")
			.b(this.dataInputVar)
			.p(")");

		formatter.g(this.tryBody);

		formatter
			.p("catch(")
			.b(this.ioeVar)
			.p(")");

		formatter.g(this.catchBody);

		formatter.nl();
	}
}