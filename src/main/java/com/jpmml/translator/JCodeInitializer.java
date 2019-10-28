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

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCodeModel;
import com.sun.codemodel.JDefinedClass;
import com.sun.codemodel.JExpr;
import com.sun.codemodel.JFieldVar;
import com.sun.codemodel.JFormatter;
import com.sun.codemodel.JInvocation;
import com.sun.codemodel.JMethod;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;

public class JCodeInitializer implements JStatement {

	private JDefinedClass owner = null;

	private JBlock block = null;


	public JCodeInitializer(JDefinedClass owner){
		setOwner(owner);

		this.block = new JBlock(false, false);

		JBlock init = owner.init();

		init.add(this);
	}

	@Override
	public void state(JFormatter formatter){
		formatter.g(this.block);
	}

	public JFieldVar initLambdas(String name, JType type, List<JMethod> methods){
		JDefinedClass owner = getOwner();

		JCodeModel codeModel = owner.owner();

		JFieldVar variable = owner.field(ModelTranslator.MEMBER_PRIVATE, (codeModel.ref(List.class)).narrow(type), name, JExpr._new(codeModel.ref(ArrayList.class)));

		JInvocation invocation = (codeModel.ref(Collections.class)).staticInvoke("addAll").arg(variable);

		for(JMethod method : methods){
			invocation = invocation.arg(JExpr.direct(owner.name() + "::" + method.name()));
		}

		this.block.add(invocation);

		return variable;
	}

	public JFieldVar initTargetCategories(String name, List<String> categories){
		JDefinedClass owner = getOwner();

		JCodeModel codeModel = owner.owner();

		JFieldVar variable = owner.field(ModelTranslator.MEMBER_PRIVATE, (codeModel.ref(List.class)).narrow(String.class), name, JExpr._new(codeModel.ref(ArrayList.class)));

		JInvocation invocation = (codeModel.ref(Collections.class)).staticInvoke("addAll").arg(variable);

		for(String category : categories){
			invocation = invocation.arg(JExpr.lit(category));
		}

		this.block.add(invocation);

		return variable;
	}

	public JDefinedClass getOwner(){
		return this.owner;
	}

	private void setOwner(JDefinedClass owner){
		this.owner = owner;
	}
}