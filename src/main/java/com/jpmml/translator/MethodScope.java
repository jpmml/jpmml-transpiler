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
package com.jpmml.translator;

import java.util.List;

import com.sun.codemodel.JMethod;
import com.sun.codemodel.JVar;

public class MethodScope extends Scope {

	private JMethod method = null;


	public MethodScope(JMethod method){
		super(method.body());

		setMethod(method);

		List<JVar> params = method.params();
		for(JVar param : params){
			declare(param);
		}

		boolean hasVarArgs = method.hasVarArgs();
		if(hasVarArgs){
			JVar varParam = method.listVarParam();

			declare(varParam);
		}
	}

	public JMethod getMethod(){
		return this.method;
	}

	private void setMethod(JMethod method){
		this.method = method;
	}
}