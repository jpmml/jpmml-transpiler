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
package org.jpmml.translator.tree;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.VisitorAction;
import org.jpmml.model.visitors.AbstractVisitor;

public class PrimaryFieldReferenceFinder extends AbstractVisitor {

	private Set<FieldName> names = new LinkedHashSet<>();


	@Override
	public VisitorAction visit(Predicate predicate){

		if(predicate instanceof HasFieldReference){
			HasFieldReference<?> hasFieldReference = (HasFieldReference<?>)predicate;

			FieldName name = hasFieldReference.getField();

			process(name);

			return VisitorAction.TERMINATE;
		}

		return super.visit(predicate);
	}

	public Set<FieldName> getFieldNames(){
		return Collections.unmodifiableSet(this.names);
	}

	private void process(FieldName name){
		this.names.add(name);
	}
}