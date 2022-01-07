/*
 * Copyright (c) 2020 Villu Ruusmann
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

import java.util.List;

import org.dmg.pmml.Apply;
import org.dmg.pmml.Constant;
import org.dmg.pmml.DefineFunction;
import org.dmg.pmml.Expression;
import org.dmg.pmml.FieldRef;
import org.dmg.pmml.PMMLFunctions;
import org.dmg.pmml.ParameterField;
import org.dmg.pmml.TextIndex;

public class FunctionInvocationUtil {

	private FunctionInvocationUtil(){
	}

	static
	public FunctionInvocation match(Expression expression, FunctionInvocationContext context){

		if(expression instanceof FieldRef){
			return matchFieldRef((FieldRef)expression, context);
		} else

		if(expression instanceof TextIndex){
			return matchTextIndex((TextIndex)expression, context);
		} else

		if(expression instanceof Apply){
			return matchApply((Apply)expression, context);
		}

		return null;
	}

	static
	public FunctionInvocation matchFieldRef(FieldRef fieldRef, FunctionInvocationContext context){
		return new FunctionInvocation.Ref(){

			@Override
			public String getField(){
				return fieldRef.requireField();
			}
		};
	}

	static
	public FunctionInvocation matchTextIndex(TextIndex textIndex, FunctionInvocationContext context){
		return new FunctionInvocation.Tf(){

			@Override
			public TextIndex getTextIndex(){
				return textIndex;
			}

			@Override
			public String getTextField(){
				TextIndex textIndex = getTextIndex();

				return context.resolve(textIndex.requireTextField());
			}

			@Override
			public String getTerm(){
				TextIndex textIndex = getTextIndex();

				Constant constant = (Constant)context.resolve(textIndex.requireExpression());

				return (String)constant.getValue();
			}
		};
	}

	static
	public FunctionInvocation matchApply(Apply apply, FunctionInvocationContext context){
		String function = apply.requireFunction();
		List<Expression> expressions = apply.getExpressions();

		if((PMMLFunctions.MULTIPLY).equals(function) && expressions.size() == 2){
			FunctionInvocation functionInvocation = match(expressions.get(0), context);

			if(functionInvocation instanceof FunctionInvocation.Tf){
				FunctionInvocation.Tf tf = (FunctionInvocation.Tf)functionInvocation;

				return new FunctionInvocation.TfIdf(){

					@Override
					public FunctionInvocation.Tf getTf(){
						return tf;
					}

					@Override
					public Number getWeight(){
						Constant constant = (Constant)context.resolve(expressions.get(1));

						return (Number)constant.getValue();
					}
				};
			}
		} else

		{
			DefineFunction defineFunction = context.getDefineFunction(function);

			if(defineFunction != null){
				Expression expression = defineFunction.requireExpression();

				FunctionInvocationContext defineFunctionContext = new FunctionInvocationContext(){

					@Override
					public DefineFunction getDefineFunction(String name){
						return context.getDefineFunction(name);
					}
				};

				if(defineFunction.hasParameterFields()){
					List<ParameterField> parameterFields = defineFunction.getParameterFields();

					for(int i = 0; i < parameterFields.size(); i++){
						defineFunctionContext.put((parameterFields.get(i)).requireName(), expressions.get(i));
					}
				}

				return match(expression, defineFunctionContext);
			}
		}

		return null;
	}
}