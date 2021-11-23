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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.dmg.pmml.tree.Node;

public class NodeGroupUtil {

	private NodeGroupUtil(){
	}

	static
	public List<NodeGroup> group(List<Node> nodes){
		Map<String, NodeGroup> nodeGroups = new LinkedHashMap<>();

		for(Node node : nodes){
			String parentId = getParentId(node);

			NodeGroup nodeGroup = nodeGroups.get(parentId);
			if(nodeGroup == null){
				nodeGroup = new NodeGroup(parentId);

				nodeGroups.put(parentId, nodeGroup);
			}

			nodeGroup.add(node);
		}

		return new ArrayList<>(nodeGroups.values());
	}

	static
	public String getParentId(Node node){
		return NodeUtil.getExtension(node, NodeGroup.EXTENSION_PARENT);
	}

	static
	public void setParentId(Node node, int parentId){
		NodeUtil.addExtension(node, NodeGroup.EXTENSION_PARENT, String.valueOf(parentId));
	}
}