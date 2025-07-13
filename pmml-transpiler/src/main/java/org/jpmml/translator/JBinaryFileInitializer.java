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
import org.dmg.pmml.MathContext;
import org.jpmml.evaluator.ResourceUtil;
import org.jpmml.evaluator.TokenizedString;

public class JBinaryFileInitializer extends JResourceInitializer {

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

		JDefinedClass owner;

		try {
			owner = getResourceOwner(context);
		} catch(IllegalArgumentException iae){
			owner = context.getOwner();
		}

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

		JBlock init = owner.init();

		if(pos > -1){
			init.pos(pos);
		}

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
	public JInvocation initQNames(QName[] names){
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
	public JInvocation initValues(JType type, Object[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		String readMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			String typeName = type.fullName();
			switch(typeName){
				case "java.lang.String":
					ResourceUtil.writeStrings(dataOutput, castArray(values, new String[values.length]));
					readMethod = "readStrings";
					break;
				case "java.lang.Integer":
					ResourceUtil.writeIntegers(dataOutput, castArray(values, new Integer[values.length]));
					readMethod = "readIntegers";
					break;
				case "java.lang.Float":
					ResourceUtil.writeFloats(dataOutput, castArray(values, new Float[values.length]));
					readMethod = "readFloats";
					break;
				case "java.lang.Double":
					ResourceUtil.writeDoubles(dataOutput, castArray(values, new Double[values.length]));
					readMethod = "readDoubles";
					break;
				default:
					throw new IllegalArgumentException(typeName);
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		return context.staticInvoke(ResourceUtil.class, readMethod, this.dataInputVar, values.length);
	}

	@Override
	public JInvocation initTokenizedStringLists(TokenizedString[] tokenizedStrings){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeTokenizedStrings(dataOutput, tokenizedStrings);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readTokenizedStrings", this.dataInputVar, tokenizedStrings.length);

		return context.staticInvoke(Arrays.class, "asList").arg(invocation);
	}

	@Override
	public JInvocation initNumbers(MathContext mathContext, Number[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

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

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumbers(mathContext), this.dataInputVar, values.length);

		return context.staticInvoke(Arrays.class, "asList").arg(invocation);
	}

	@Override
	public JInvocation initNumbersList(MathContext mathContext, List<Number[]> elements){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JArray countArray = JExpr.newArray(context._ref(int.class));

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

		JMethod initMethod = ensureReadNumbersListMethod(readNumbers(mathContext), context);

		return JExpr.invoke(initMethod).arg(this.dataInputVar).arg(countArray);
	}

	@Override
	public JInvocation initNumberArraysList(MathContext mathContext, List<Number[][]> elements, int length){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JArray countArray = JExpr.newArray(context._ref(int.class));

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

		JMethod initMethod = ensureReadNumberArraysListMethod(readNumberArrays(mathContext), context);

		return JExpr.invoke(initMethod).arg(this.dataInputVar).arg(countArray).arg(JExpr.lit(length));
	}

	@Override
	public JInvocation initNumbersMap(Map<?, Number> map){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		Set<?> keys = map.keySet();
		Collection<Number> values = map.values();

		Class<?> keyClazz = getValueClass(keys);
		Class<?> valueClazz = getValueClass(values);

		String keyReadMethod;
		String valueReadMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			if(Objects.equals(keyClazz, String.class)){
				ResourceUtil.writeStrings(dataOutput, keys.toArray(new String[keys.size()]));
				keyReadMethod = "readStrings";
			} else

			if(Objects.equals(keyClazz, Integer.class)){
				ResourceUtil.writeIntegers(dataOutput, keys.toArray(new Integer[keys.size()]));
				keyReadMethod = "readIntegers";
			} else

			if(Objects.equals(keyClazz, Float.class)){
				ResourceUtil.writeFloats(dataOutput, keys.toArray(new Float[keys.size()]));
				keyReadMethod = "readFloats";
			} else

			if(Objects.equals(keyClazz, Double.class)){
				ResourceUtil.writeDoubles(dataOutput, keys.toArray(new Double[keys.size()]));
				keyReadMethod = "readDoubles";
			} else

			{
				throw new IllegalArgumentException();
			} // End if

			if(Objects.equals(valueClazz, Integer.class)){
				ResourceUtil.writeIntegers(dataOutput, values.toArray(new Integer[values.size()]));
				valueReadMethod = "readIntegers";
			} else

			if(Objects.equals(valueClazz, Float.class)){
				ResourceUtil.writeFloats(dataOutput, values.toArray(new Float[values.size()]));
				valueReadMethod = "readFloats";
			} else

			if(Objects.equals(valueClazz, Double.class)){
				ResourceUtil.writeDoubles(dataOutput, values.toArray(new Double[values.size()]));
				valueReadMethod = "readDoubles";
			} else

			{
				throw new IllegalArgumentException();
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JMethod initMethod = ensureReadNumbersMapMethod(keyReadMethod, valueReadMethod, context);

		return JExpr.invoke(initMethod).arg(this.dataInputVar).arg(JExpr.lit(map.size()));
	}

	public JBinaryFile getBinaryFile(){
		return this.binaryFile;
	}

	private void setBinaryFile(JBinaryFile binaryFile){
		this.binaryFile = binaryFile;
	}

	static
	private <E> E[] castArray(Object[] values, E[] newValues){
		return Arrays.asList(values)
			.toArray(newValues);
	}

	static
	private JMethod ensureReadNumbersListMethod(String readMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = readMethod + "List";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);
		JType intArrayClazz = intClazz.array();

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intArrayClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(List.class, context.ref(Number[].class)), name)
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
	private JMethod ensureReadNumberArraysListMethod(String readMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = readMethod + "List";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);
		JType intArrayClazz = intClazz.array();

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intArrayClazz, intClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(List.class, context.ref(Number[][].class)), name).
				_throws(IOException.class);

			JVar dataInputParam = method.param(DataInput.class, "dataInput");
			JVar countsParam = method.param(intArrayClazz, "counts");
			JVar lengthParam = method.param(intClazz, "length");

			JBlock block = method.body();

			JVar resultVar = block.decl(method.type(), "result", context._new(ArrayList.class));

			JForEach forEach = block.forEach(intClazz, "count", countsParam);

			JInvocation invocation = context.staticInvoke(ResourceUtil.class, readMethod, dataInputParam, forEach.var(), lengthParam);

			forEach.body().add((resultVar.invoke("add")).arg(invocation));

			block._return(resultVar);
		}

		return method;
	}

	static
	private JMethod ensureReadNumbersMapMethod(String keyReadMethod, String valueReadMethod, TranslationContext context){
		JDefinedClass owner = getResourceOwner(context);

		String name = keyReadMethod + valueReadMethod.replace("read", "") + "Map";

		JType dataInputClazz = context.ref(DataInput.class);
		JType intClazz = context._ref(int.class);

		JMethod method = owner.getMethod(name, new JType[]{dataInputClazz, intClazz});
		if(method == null){
			method = owner.method(Modifiers.PRIVATE_STATIC_FINAL, context.genericRef(Map.class, context.ref(Object.class), context.ref(Number.class)), name)
				._throws(IOException.class);

			JVar dataInputParam = method.param(DataInput.class, "dataInput");
			JVar sizeParam = method.param(intClazz, "size");

			JBlock block = method.body();

			JVar resultVar = block.decl(method.type(), "result", context._new(LinkedHashMap.class));

			JInvocation keysInvocation = context.staticInvoke(ResourceUtil.class, keyReadMethod, dataInputParam, sizeParam);
			JInvocation valuesInvocation = context.staticInvoke(ResourceUtil.class, valueReadMethod, dataInputParam, sizeParam);

			JVar keysVar = block.decl(context.ref(Object.class).array(), "keys", keysInvocation);
			JVar valuesVar = block.decl(context.ref(Number.class).array(), "values", valuesInvocation);

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