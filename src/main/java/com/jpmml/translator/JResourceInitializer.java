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
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.fmt.JBinaryFile;
import org.dmg.pmml.MathContext;
import org.jpmml.evaluator.ResourceUtil;

public class JResourceInitializer implements JStatement {

	private JDefinedClass owner = null;

	private JBinaryFile binaryFile = null;

	private JVar dataInputVar = null;

	private JBlock tryBody = new JBlock();

	private JVar ioeVar = null;

	private JBlock catchBody = new JBlock();


	public JResourceInitializer(JDefinedClass owner, String name){
		setOwner(owner);

		JBinaryFile binaryFile = new JBinaryFile(name);

		setBinaryFile(binaryFile);

		(owner._package()).addResourceFile(binaryFile);

		JCodeModel codeModel = owner.owner();

		JBlock resourceStmt = new JBlock(false, false);

		JType dataInputStreamClazz = codeModel.ref(DataInputStream.class);

		JExpression isExpr = (JExpr.dotclass(owner)).invoke("getResourceAsStream").arg(name);

		this.dataInputVar = resourceStmt.decl(dataInputStreamClazz, "dataInput", (JExpr._new(dataInputStreamClazz)).arg(isExpr));

		JBlock catchStmt = new JBlock(false, false);

		this.ioeVar = catchStmt.decl(codeModel.ref(IOException.class), "ioe");

		this.catchBody._throw((JExpr._new(codeModel.ref(ExceptionInInitializerError.class))).arg(this.ioeVar));

		JBlock init = owner.init();

		init.add(this);
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

	public JFieldVar initNumbers(String name, MathContext mathContext, Number[] values){
		JDefinedClass owner = getOwner();
		JBinaryFile binaryFile = getBinaryFile();

		JCodeModel codeModel = owner.owner();

		JFieldVar variable = owner.field(ModelTranslator.MEMBER_PRIVATE, (codeModel.ref(List.class)).narrow(Number.class), name, JExpr._new(codeModel.ref(ArrayList.class)));

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			switch(mathContext){
				case FLOAT:
					ResourceUtil.writeFloats(dataOutput, values);
					break;
				case DOUBLE:
					ResourceUtil.writeDoubles(dataOutput, values);
					break;
				default:
					throw new IllegalArgumentException();
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JBlock block = this.tryBody;

		JInvocation invocation;

		switch(mathContext){
			case FLOAT:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readFloats");
				break;
			case DOUBLE:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readDoubles");
				break;
			default:
				throw new IllegalArgumentException();
		}

		invocation = invocation.arg(this.dataInputVar).arg(JExpr.lit(values.length));

		block.add(codeModel.ref(Collections.class).staticInvoke("addAll").arg(variable).arg(invocation));

		return variable;
	}

	public JFieldVar initNumbersList(String name, MathContext mathContext, List<Number[]> elements){
		JDefinedClass owner = getOwner();
		JBinaryFile binaryFile = getBinaryFile();

		JCodeModel codeModel = owner.owner();

		JFieldVar variable = owner.field(ModelTranslator.MEMBER_PRIVATE, (codeModel.ref(List.class)).narrow(Number[].class), name, JExpr._new(codeModel.ref(ArrayList.class)));

		JArray countArray = JExpr.newArray(codeModel.INT);

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			for(Number[] element : elements){

				switch(mathContext){
					case FLOAT:
						ResourceUtil.writeFloats(dataOutput, element);
						break;
					case DOUBLE:
						ResourceUtil.writeDoubles(dataOutput, element);
						break;
					default:
						throw new IllegalArgumentException();
				}

				countArray.add(JExpr.lit(element.length));
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JBlock block = this.tryBody;

		JVar countsVar = block.decl(codeModel.INT.array(), "counts", countArray);

		JForEach forEach = block.forEach(codeModel.INT, "count", countsVar);

		JInvocation invocation;

		switch(mathContext){
			case FLOAT:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readFloats");
				break;
			case DOUBLE:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readDoubles");
				break;
			default:
				throw new IllegalArgumentException();
		}

		invocation = invocation.arg(this.dataInputVar).arg(forEach.var());

		forEach.body().add((variable.invoke("add")).arg(invocation));

		return variable;
	}

	public JFieldVar initNumberArraysList(String name, MathContext mathContext, List<Number[][]> elements, int length){
		JDefinedClass owner = getOwner();
		JBinaryFile binaryFile = getBinaryFile();

		JCodeModel codeModel = owner.owner();

		JFieldVar variable = owner.field(ModelTranslator.MEMBER_PRIVATE, (codeModel.ref(List.class)).narrow(Number[][].class), name, JExpr._new(codeModel.ref(ArrayList.class)));

		JArray countArray = JExpr.newArray(codeModel.INT);

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			for(Number[][] element : elements){

				switch(mathContext){
					case FLOAT:
						ResourceUtil.writeFloatArrays(dataOutput, element);
						break;
					case DOUBLE:
						ResourceUtil.writeDoubleArrays(dataOutput, element);
						break;
					default:
						throw new IllegalArgumentException();
				}

				countArray.add(JExpr.lit(element.length));
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JBlock block = this.tryBody;

		JVar countsVar = block.decl(codeModel.INT.array(), "counts", countArray);

		JForEach forEach = block.forEach(codeModel.INT, "count", countsVar);

		JInvocation invocation;

		switch(mathContext){
			case FLOAT:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readFloatArrays");
				break;
			case DOUBLE:
				invocation = (codeModel.ref(ResourceUtil.class)).staticInvoke("readDoubleArrays");
				break;
			default:
				throw new IllegalArgumentException();
		}

		invocation = invocation.arg(this.dataInputVar).arg(forEach.var()).arg(JExpr.lit(length));

		forEach.body().add((variable.invoke("add")).arg(invocation));

		return variable;
	}

	public JDefinedClass getOwner(){
		return this.owner;
	}

	private void setOwner(JDefinedClass owner){
		this.owner = owner;
	}

	public JBinaryFile getBinaryFile(){
		return this.binaryFile;
	}

	private void setBinaryFile(JBinaryFile binaryFile){
		this.binaryFile = binaryFile;
	}
}