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

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.DataOutput;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import javax.xml.namespace.QName;

import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JForEach;
import com.sun.codemodel.JForLoop;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JPackage;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;
import com.sun.codemodel.fmt.JBinaryFile;
import org.jpmml.evaluator.ResourceUtil;
import org.jpmml.evaluator.TokenizedString;

public class JBinaryFileInitializer extends JResourceInitializer {

	private JBinaryFile binaryFile = null;

	private JVar dataInputVar = null;

	private JBlock tryBody = new JBlock();

	private JVar ioeVar = null;

	private JBlock catchBody = new JBlock();


	public JBinaryFileInitializer(String name, TranslationContext context){
		super(context);

		// XXX
		name = name + ".data";

		JDefinedClass owner;

		try {
			owner = getResourceOwner(context);
		} catch(IllegalArgumentException iae){
			owner = context.getOwner();
		}

		JBlock init = owner.init();

		JBinaryFile binaryFile = new JBinaryFile(name);

		setBinaryFile(binaryFile);

		JPackage _package = owner.getPackage();

		_package.addResourceFile(binaryFile);

		JBlock resourceStmt = new JBlock(false, false);

		JClass dataInputStreamClazz = context.ref(DataInputStream.class);

		JExpression isExpr = (JExpr.dotclass(owner)).invoke("getResourceAsStream").arg(name);

		this.dataInputVar = resourceStmt.decl(dataInputStreamClazz, "dataInput", context._new(dataInputStreamClazz, isExpr));

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

		init.add(tryWithResources);
	}

	@Override
	public void add(JStatement statement){
		this.tryBody.add(statement);
	}

	@Override
	public void assign(JVar variable, JExpression expr){
		this.tryBody.assign(variable, expr);
	}

	@Override
	public JInvocation initQNameArray(QName[] names){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeQNames(dataOutput, names);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		return context.staticInvoke(ResourceUtil.class, "readQNames", this.dataInputVar, names.length);
	}

	@Override
	public JInvocation initTokenizedStringArray(TokenizedString[] tokenizedStrings){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeTokenizedStrings(dataOutput, tokenizedStrings);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		return context.staticInvoke(ResourceUtil.class, "readTokenizedStrings", this.dataInputVar, tokenizedStrings.length);
	}

	@Override
	public JInvocation initObjectArray(JType type, Object[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		String readMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			readMethod = recordValues(dataOutput, type, values);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		return context.staticInvoke(ResourceUtil.class, readMethod, this.dataInputVar, values.length);
	}

	@Override
	public JInvocation initNumberArrayList(JType type, List<Number[]> elements){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		String readMethod = null;

		JArray countArray = JExpr.newArray(context._ref(int.class));

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			for(Number[] element : elements){
				readMethod = recordValues(dataOutput, type.elementType(), element);

				countArray.add(JExpr.lit(element.length));
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JMethod method = ensureReadNumberArrayListMethod(type, readMethod, context);

		return JExpr.invoke(method).arg(this.dataInputVar).arg(countArray);
	}

	@Override
	public JInvocation initNumberMatrixList(JType type, List<Number[][]> elements, int length){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		String readArraysMethod = null;

		JArray countArray = JExpr.newArray(context._ref(int.class));

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			for(Number[][] element : elements){
				readArraysMethod = recordArrayValues(dataOutput, type.elementType(), element);

				countArray.add(JExpr.lit(element.length));
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JMethod method = ensureReadNumberMatrixListMethod(type, readArraysMethod, context);

		return JExpr.invoke(method).arg(this.dataInputVar).arg(countArray).arg(JExpr.lit(length));
	}

	@Override
	public JInvocation initNumberMap(JType keyType, JType valueType, Map<?, Number> map){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		Set<?> keys = map.keySet();
		Collection<Number> values = map.values();

		Class<?> keysClazz = getValueClass(keys);
		Class<?> valuesClazz = getValueClass(values, Number.class);

		String keyReadMethod;
		String valueReadMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			keyReadMethod = recordValues(dataOutput, keyType, toArray(keys, keysClazz));
			valueReadMethod = recordValues(dataOutput, valueType, toArray(values, valuesClazz));
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JMethod method = ensureReadNumberMapMethod(keyType, valueType, keyReadMethod, valueReadMethod, context);

		return JExpr.invoke(method).arg(this.dataInputVar).arg(JExpr.lit(map.size()));
	}

	private String recordValues(DataOutput dataOutput, JType type, Object[] values) throws IOException {
		String typeName = type.fullName();

		switch(typeName){
			case "java.lang.String":
				ResourceUtil.writeStrings(dataOutput, castArray(values, new String[values.length]));
				return "readStrings";
			case "java.lang.Integer":
				ResourceUtil.writeIntegers(dataOutput, castArray(values, new Integer[values.length]));
				return "readIntegers";
			case "java.lang.Float":
				ResourceUtil.writeFloats(dataOutput, castArray(values, new Float[values.length]));
				return "readFloats";
			case "java.lang.Double":
				ResourceUtil.writeDoubles(dataOutput, castArray(values, new Double[values.length]));
				return "readDoubles";
			default:
				throw new IllegalArgumentException(typeName);
		}
	}

	private String recordArrayValues(DataOutput dataOutput, JType type, Number[][] values) throws IOException {
		String typeName = type.fullName();

		switch(typeName){
			case "java.lang.Float[]":
				ResourceUtil.writeFloatArrays(dataOutput, values);
				return "readFloatArrays";
			case "java.lang.Double[]":
				ResourceUtil.writeDoubleArrays(dataOutput, values);
				return "readDoubleArrays";
			default:
				throw new IllegalArgumentException(typeName);
		}
	}

	public JBinaryFile getBinaryFile(){
		return this.binaryFile;
	}

	private void setBinaryFile(JBinaryFile binaryFile){
		this.binaryFile = Objects.requireNonNull(binaryFile);
	}

	static
	private <E> E[] toArray(Collection<?> values, Class<? extends E> clazz){
		return values.toArray(size -> (E[])Array.newInstance(clazz, size));
	}

	static
	private <E> E[] castArray(Object[] values, E[] newValues){
		return Arrays.asList(values)
			.toArray(newValues);
	}

	static
	private JMethod ensureReadNumberArrayListMethod(JType type, String readMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = toSingular(readMethod) + "ArrayList";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);
		JType intArrayClazz = intClazz.array();

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intArrayClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(List.class, type), name)
				._throws(IOException.class);

			JVar dataInputParam = method.param(dataInputClazz, "dataInput");
			JVar countsParam = method.param(intArrayClazz, "counts");

			JBlock block = method.body();

			JVar resultVar = block.decl(method.type(), "result", context._new(ArrayList.class));

			JForEach forEach = block.forEach(intClazz, "count", countsParam);

			JInvocation invocation = context.staticInvoke(ResourceUtil.class, readMethod, dataInputParam, forEach.var());

			forEach.body().add((resultVar.invoke("add")).arg(invocation));

			block._return(resultVar);
		}

		return method;
	}

	static
	private JMethod ensureReadNumberMatrixListMethod(JType type, String readArraysMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = readArraysMethod.replace("Arrays", "Matrix") + "List";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);
		JType intArrayClazz = intClazz.array();

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intArrayClazz, intClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(List.class, type), name).
				_throws(IOException.class);

			JVar dataInputParam = method.param(DataInput.class, "dataInput");
			JVar countsParam = method.param(intArrayClazz, "counts");
			JVar lengthParam = method.param(intClazz, "length");

			JBlock block = method.body();

			JVar resultVar = block.decl(method.type(), "result", context._new(ArrayList.class));

			JForEach forEach = block.forEach(intClazz, "count", countsParam);

			JInvocation invocation = context.staticInvoke(ResourceUtil.class, readArraysMethod, dataInputParam, forEach.var(), lengthParam);

			forEach.body().add((resultVar.invoke("add")).arg(invocation));

			block._return(resultVar);
		}

		return method;
	}

	static
	private JMethod ensureReadNumberMapMethod(JType keyType, JType valueType, String keyReadMethod, String valueReadMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = toSingular(keyReadMethod) + toSingular(valueReadMethod.replace("read", "")) + "Map";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(Map.class, keyType, valueType), name)
				._throws(IOException.class);

			JVar dataInputParam = method.param(DataInput.class, "dataInput");
			JVar sizeParam = method.param(intClazz, "size");

			JBlock block = method.body();

			JVar resultVar = block.decl(method.type(), "result", context._new(LinkedHashMap.class));

			JInvocation keysInvocation = context.staticInvoke(ResourceUtil.class, keyReadMethod, dataInputParam, sizeParam);
			JInvocation valuesInvocation = context.staticInvoke(ResourceUtil.class, valueReadMethod, dataInputParam, sizeParam);

			JVar keysVar = block.decl(keyType.array(), "keys", keysInvocation);
			JVar valuesVar = block.decl(valueType.array(), "values", valuesInvocation);

			JForLoop forLoop = block._for();

			JVar loopVar = forLoop.init(intClazz, "i", JExpr.lit(0));
			forLoop.test(loopVar.lt(sizeParam));
			forLoop.update(loopVar.incr());

			JBlock forBlock = forLoop.body();

			forBlock.add(JExpr.invoke(resultVar, "put").arg(keysVar.component(loopVar)).arg(valuesVar.component(loopVar)));

			block._return(resultVar);
		}

		return method;
	}
}