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

import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collections;
import java.util.List;

import javax.xml.namespace.QName;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.fmt.JBinaryFile;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.MathContext;
import org.jpmml.evaluator.ResourceUtil;

public class JBinaryFileInitializer extends JClassInitializer {

	private JBinaryFile binaryFile = null;

	private JVar dataInputVar = null;

	private JBlock tryBody = new JBlock();

	private JVar ioeVar = null;

	private JBlock catchBody = new JBlock();


	public JBinaryFileInitializer(String name, TranslationContext context){
		this(name, -1, context);
	}

	public JBinaryFileInitializer(String name, int pos, TranslationContext context){
		super(context);

		JBinaryFile binaryFile = new JBinaryFile(name);

		setBinaryFile(binaryFile);

		JDefinedClass owner = context.getOwner();

		JPackage _package = owner.getPackage();

		_package.addResourceFile(binaryFile);

		JBlock resourceStmt = new JBlock(false, false);

		JType dataInputStreamClazz = context.ref(DataInputStream.class);

		JExpression isExpr = (JExpr.dotclass(owner)).invoke("getResourceAsStream").arg(name);

		this.dataInputVar = resourceStmt.decl(dataInputStreamClazz, "dataInput", (JExpr._new(dataInputStreamClazz)).arg(isExpr));

		JBlock catchStmt = new JBlock(false, false);

		this.ioeVar = catchStmt.decl(context.ref(IOException.class), "ioe");

		this.catchBody._throw(context._new(ExceptionInInitializerError.class, this.ioeVar));

		JStatement tryWithResources = new JStatement(){

			@Override
			public void state(JFormatter formatter){
				formatter
					.p("try(")
					.b(JBinaryFileInitializer.this.dataInputVar)
					.p(")");

				formatter.g(JBinaryFileInitializer.this.tryBody);

				formatter
					.p("catch(")
					.b(JBinaryFileInitializer.this.ioeVar)
					.p(")");

				formatter.g(JBinaryFileInitializer.this.catchBody);

				formatter.nl();
			}
		};

		JBlock init = owner.init();

		if(pos > -1){
			init.pos(pos);
		}

		init.add(tryWithResources);
	}

	public void initFieldNames(JFieldVar field, FieldName[] names){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeFieldNames(dataOutput, names);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readFieldNames", this.dataInputVar, JExpr.lit(names.length));

		this.tryBody.assign(field, invocation);
	}

	public void initQNames(JFieldVar field, QName[] names){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeQNames(dataOutput, names);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readQNames", this.dataInputVar, JExpr.lit(names.length));

		this.tryBody.assign(field, invocation);
	}

	public JFieldVar initStringLists(String name, List<String>[] stringLists){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createConstant(name, context.ref(List.class).narrow(String.class), context);

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeStringLists(dataOutput, stringLists);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readStringLists", this.dataInputVar);

		this.tryBody.add(context.staticInvoke(Collections.class, "addAll", constant, invocation));

		return constant;
	}

	public JFieldVar initNumbers(String name, MathContext mathContext, Number[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createConstant(name, context.ref(Number.class), context);

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

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumbers(mathContext), this.dataInputVar, JExpr.lit(values.length));

		this.tryBody.add(context.staticInvoke(Collections.class, "addAll", constant, invocation));

		return constant;
	}

	public JFieldVar initNumbersList(String name, MathContext mathContext, List<Number[]> elements){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createConstant(name, context.ref(Number[].class), context);

		JType intType = context._ref(int.class);

		JArray countArray = JExpr.newArray(intType);

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

		JVar countsVar = block.decl(intType.array(), "counts", countArray);

		JForEach forEach = block.forEach(intType, "count", countsVar);

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumbers(mathContext), this.dataInputVar, forEach.var());

		forEach.body().add((constant.invoke("add")).arg(invocation));

		return constant;
	}

	public JFieldVar initNumberArraysList(String name, MathContext mathContext, List<Number[][]> elements, int length){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createConstant(name, context.ref(Number[][].class), context);

		JType intType = context._ref(int.class);

		JArray countArray = JExpr.newArray(intType);

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

		JVar countsVar = block.decl(intType.array(), "counts", countArray);

		JForEach forEach = block.forEach(intType, "count", countsVar);

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumberArrays(mathContext), this.dataInputVar, forEach.var(), JExpr.lit(length));

		forEach.body().add((constant.invoke("add")).arg(invocation));

		return constant;
	}

	public JBinaryFile getBinaryFile(){
		return this.binaryFile;
	}

	private void setBinaryFile(JBinaryFile binaryFile){
		this.binaryFile = binaryFile;
	}

	static
	private String readNumbers(MathContext mathContext){

		switch(mathContext){
			case FLOAT:
				return "readFloats";
			case DOUBLE:
				return "readDoubles";
			default:
				throw new IllegalArgumentException();
		}
	}

	static
	private String readNumberArrays(MathContext mathContext){

		switch(mathContext){
			case FLOAT:
				return "readFloatArrays";
			case DOUBLE:
				return "readDoubleArrays";
			default:
				throw new IllegalArgumentException();
		}
	}
}