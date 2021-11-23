/*
 * Copyright (c) 2021 Villu Ruusmann
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
package org.jpmml.translator.tree;

import java.util.ArrayList;
import java.util.List;

import org.dmg.pmml.Predicate;
import org.dmg.pmml.tree.Node;

public class NodeGroup extends ArrayList<Node> {

	private String parent = null;


	public NodeGroup(String parent){
		setParent(parent);
	}

	public boolean isShallow(){
		List<Node> nodes = this;

		for(Node node : nodes){

			if(node.hasNodes()){
				return false;
			}
		}

		return true;
	}

	public Predicate getPredicate(int index){
		Node node = get(index);

		return node.getPredicate();
	}

	public String getParent(){
		return this.parent;
	}

	private void setParent(String parent){
		this.parent = parent;
	}

	public static final String EXTENSION_PARENT = "parent";
}