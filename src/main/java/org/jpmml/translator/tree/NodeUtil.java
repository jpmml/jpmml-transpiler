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

import java.util.List;

import org.dmg.pmml.Extension;
import org.dmg.pmml.tree.Node;

public class NodeUtil {

	private NodeUtil(){
	}

	static
	public String getExtension(Node node, String name){

		if(node.hasExtensions()){
			List<Extension> extensions = node.getExtensions();

			for(Extension extension : extensions){

				if((name).equals(extension.getName())){
					return extension.getValue();
				}
			}
		}

		return null;
	}

	static
	public void addExtension(Node node, String name, String value){
		Extension extension = new Extension()
			.setName(name)
			.setValue(value);

		node.addExtensions(extension);
	}
}