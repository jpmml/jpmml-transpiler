/*
 * Copyright (c) 2018 Villu Ruusmann
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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.sun.codemodel.JMethod;
import com.sun.codemodel.JTypeVar;
import com.sun.codemodel.JVar;

public class MethodScope extends Scope {

	private JMethod method = null;

	private Map<String, JTypeVar> typeVariables = null;


	public MethodScope(JMethod method){
		super(method.body());

		setMethod(method);

		List<JVar> params = method.params();
		for(JVar param : params){
			putVariable(param);
		}

		boolean hasVarArgs = method.hasVarArgs();
		if(hasVarArgs){
			JVar varParam = method.listVarParam();

			putVariable(varParam);
		}

		JTypeVar[] typeParams = method.typeParams();
		for(JTypeVar typeParam : typeParams){
			putTypeVariable(typeParam);
		}
	}

	public JTypeVar getTypeVariable(String name){

		if(this.typeVariables == null){
			return null;
		}

		return this.typeVariables.get(name);
	}

	public void putTypeVariable(JTypeVar typeParam){

		if(this.typeVariables == null){
			this.typeVariables = new LinkedHashMap<>();
		}

		this.typeVariables.put(typeParam.name(), typeParam);
	}

	public JMethod getMethod(){
		return this.method;
	}

	private void setMethod(JMethod method){
		this.method = method;
	}

	public static final String TYPEVAR_NUMBER = "V";
}