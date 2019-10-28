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

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Set;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JExpression;
import com.sun.codemodel.JStatement;
import com.sun.codemodel.JType;
import com.sun.codemodel.JVar;

public class Scope extends LinkedHashMap<String, JVar> {

	private JBlock block = null;

	private Set<String> nonMissingVariables = null;

	private boolean open = true;


	public Scope(JBlock block){
		setBlock(block);
	}

	public boolean isNonMissing(JVar variable){
		return (this.nonMissingVariables != null && this.nonMissingVariables.contains(variable.name()));
	}

	public void markNonMissing(JVar variable){

		if(this.nonMissingVariables == null){
			this.nonMissingVariables = new LinkedHashSet<>();
		}

		this.nonMissingVariables.add(variable.name());
	}

	public Scope ensureOpen(){
		boolean open = isOpen();

		if(!open){
			throw new IllegalStateException();
		}

		return this;
	}

	public Scope close(){
		setOpen(false);

		return this;
	}

	public JVar declare(JType type, String name, JExpression initializer){
		JBlock block = getBlock();

		JVar variable = block.decl(type, name, initializer);

		return declare(variable);
	}

	public JVar declare(JVar variable){
		put(variable.name(), variable);

		return variable;
	}

	public void add(JStatement statement){
		JBlock block = getBlock();

		block.add(statement);
	}

	public void _return(JExpression expression){
		JBlock block = getBlock();

		block._return(expression);
	}

	public JBlock getBlock(){
		return this.block;
	}

	private void setBlock(JBlock block){
		this.block = block;
	}

	public boolean isOpen(){
		return this.open;
	}

	private void setOpen(boolean open){
		this.open = open;
	}

	public static final String NAME_ARGUMENTS = "arguments";
	public static final String NAME_CONTEXT = "context";
	public static final String NAME_VALUEFACTORY = "valueFactory";
}