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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import javax.xml.namespace.QName;

import com.google.common.collect.Iterables;
import com.sun.codemodel.JArray;
import com.sun.codemodel.JBlock;
import com.sun.codemodel.JClass;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JFieldVar;
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

	public void initQNames(JFieldVar field, QName[] names){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeQNames(dataOutput, names);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readQNames", this.dataInputVar, names.length);

		this.tryBody.assign(field, invocation);
	}

	public void initValues(JFieldVar field, Object[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		String readMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			JClass arrayType = (JClass)field.type();
			JClass arrayElementType = (JClass)arrayType.elementType();

			String typeName = arrayElementType.fullName();
			switch(typeName){
				case "java.lang.String":
					ResourceUtil.writeStrings(dataOutput, castArray(values, new String[values.length]));
					readMethod = "readStrings";
					break;
				case "java.lang.Double":
					ResourceUtil.writeDoubles(dataOutput, castArray(values, new Double[values.length]));
					readMethod = "readDoubles";
					break;
				case "java.lang.Float":
					ResourceUtil.writeFloats(dataOutput, castArray(values, new Float[values.length]));
					readMethod = "readFloats";
					break;
				default:
					throw new IllegalArgumentException(typeName);
			}
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readMethod, this.dataInputVar, values.length);

		this.tryBody.assign(field, invocation);
	}

	public JFieldVar initTokenizedStringLists(String name, TokenizedString[] tokenizedStrings){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createListConstant(name, context.ref(TokenizedString.class), context);

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			ResourceUtil.writeTokenizedStrings(dataOutput, tokenizedStrings);
		} catch(IOException ioe){
			throw new RuntimeException(ioe);
		}

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, "readTokenizedStrings", this.dataInputVar, tokenizedStrings.length);

		add(context.staticInvoke(Collections.class, "addAll", constant, invocation));

		return constant;
	}

	public JFieldVar initNumbers(String name, MathContext mathContext, Number[] values){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createListConstant(name, context.ref(Number.class), context);

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

		add(context.staticInvoke(Collections.class, "addAll", constant, invocation));

		return constant;
	}

	public JFieldVar initNumbersList(String name, MathContext mathContext, List<Number[]> elements){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createListConstant(name, context.ref(Number[].class), context);

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

		JMethod initMethod = createMethod(name, context)
			._throws(IOException.class);

		JVar dataInputParam = initMethod.param(DataInputStream.class, "dataInput");

		JBlock block = initMethod.body();

		JVar countsVar = block.decl(intType.array(), "counts", countArray);

		JForEach forEach = block.forEach(intType, "count", countsVar);

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumbers(mathContext), dataInputParam, forEach.var());

		forEach.body().add((constant.invoke("add")).arg(invocation));

		add(JExpr.invoke(initMethod).arg(this.dataInputVar));

		return constant;
	}

	public JFieldVar initNumberArraysList(String name, MathContext mathContext, List<Number[][]> elements, int length){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JFieldVar constant = createListConstant(name, context.ref(Number[][].class), context);

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

		JMethod initMethod = createMethod(name, context).
			_throws(IOException.class);

		JVar dataInputParam = initMethod.param(DataInputStream.class, "dataInput");

		JBlock block = initMethod.body();

		JVar countsVar = block.decl(intType.array(), "counts", countArray);

		JForEach forEach = block.forEach(intType, "count", countsVar);

		JInvocation invocation = context.staticInvoke(ResourceUtil.class, readNumberArrays(mathContext), dataInputParam, forEach.var(), length);

		forEach.body().add((constant.invoke("add")).arg(invocation));

		add(JExpr.invoke(initMethod).arg(this.dataInputVar));

		return constant;
	}

	public JFieldVar initNumbersMap(String name, Map<?, Number> map){
		TranslationContext context = getContext();
		JBinaryFile binaryFile = getBinaryFile();

		JDefinedClass owner = context.getOwner();

		JFieldVar constant = createMapConstant(name, context.ref(Object.class), context.ref(Number.class), context);

		String keyReadMethod;
		String valueReadMethod;

		try(OutputStream os = binaryFile.getDataStore()){
			DataOutput dataOutput = new DataOutputStream(os);

			Set<?> keys = map.keySet();

			Class<?> keyClazz = getValueClass(keys);

			if(Objects.equals(keyClazz, String.class)){
				ResourceUtil.writeStrings(dataOutput, keys.toArray(new String[keys.size()]));
				keyReadMethod = "readStrings";
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
			}

			Collection<Number> values = map.values();

			Class<?> valueClazz = getValueClass(values);

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

		JClass objectArrayClass = context.ref(Object[].class);
		JClass numberArrayClass = context.ref(Number[].class);

		JMethod putAllMethod = owner.getMethod("putAll", new JType[]{constant.type(), objectArrayClass, numberArrayClass});
		if(putAllMethod == null){
			putAllMethod = owner.method(Modifiers.PRIVATE_STATIC_FINAL, void.class, "putAll");

			JVar mapParam = putAllMethod.param(constant.type(), "map");

			JVar keysParam = putAllMethod.param(objectArrayClass, "keys");
			JVar valuesParam = putAllMethod.param(numberArrayClass, "values");

			JBlock block = putAllMethod.body();

			JForLoop forLoop = block._for();

			JVar loopVar = forLoop.init(context._ref(int.class), "i", JExpr.lit(0));
			forLoop.test(loopVar.lt(keysParam.ref("length")));
			forLoop.update(loopVar.incr());

			JBlock forBlock = forLoop.body();

			forBlock.add(JExpr.invoke(mapParam, "put").arg(keysParam.component(loopVar)).arg(valuesParam.component(loopVar)));
		}

		JInvocation keysInvocation = context.staticInvoke(ResourceUtil.class, keyReadMethod, this.dataInputVar, map.size());
		JInvocation valuesInvocation = context.staticInvoke(ResourceUtil.class, valueReadMethod, this.dataInputVar, map.size());

		add(JExpr.invoke(putAllMethod).arg(constant).arg(keysInvocation).arg(valuesInvocation));

		return constant;
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
	public boolean isExternalizable(Class<?> clazz){

		if(Objects.equals(clazz, String.class)){
			return true;
		} else

		if(Objects.equals(clazz, Float.class) || Objects.equals(clazz, Double.class)){
			return true;
		} else

		{
			return false;
		}
	}

	static
	public boolean isExternalizable(Collection<?> values){
		Class<?> valueClazz = getValueClass(values);

		return isExternalizable(valueClazz);
	}

	static
	public Class<?> getValueClass(Collection<?> values){
		Set<Class<?>> valueClazzes = values.stream()
			.map(value -> value.getClass())
			.collect(Collectors.toSet());

		if(valueClazzes.size() == 1){
			return Iterables.getOnlyElement(valueClazzes);
		}

		return Object.class;
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