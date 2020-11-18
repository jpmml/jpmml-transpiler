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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.dmg.pmml.CompoundPredicate;
import org.dmg.pmml.FieldName;
import org.dmg.pmml.HasFieldReference;
import org.dmg.pmml.Predicate;
import org.dmg.pmml.SimplePredicate;
import org.dmg.pmml.SimpleSetPredicate;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.tree.Node;
import org.jpmml.model.visitors.AbstractVisitor;

public class CountingActiveFieldFinder extends AbstractVisitor {

	private Map<FieldName, Integer> nameCounts = new LinkedHashMap<>();


	public void applyTo(Node node){
		super.applyTo(node);
	}

	@Override
	public VisitorAction visit(CompoundPredicate compoundPredicate){
		throw new UnsupportedOperationException();
	}

	@Override
	public VisitorAction visit(SimplePredicate simplePredicate){
		process(simplePredicate);

		return super.visit(simplePredicate);
	}

	@Override
	public VisitorAction visit(SimpleSetPredicate simpleSetPredicate){
		process(simpleSetPredicate);

		return super.visit(simpleSetPredicate);
	}

	public Set<FieldName> getFieldNames(){
		return Collections.unmodifiableSet(this.nameCounts.keySet());
	}

	public Integer getCount(FieldName name){
		return this.nameCounts.get(name);
	}

	public Map<FieldName, Integer> getFieldNameCounts(){
		return Collections.unmodifiableMap(this.nameCounts);
	}

	private <P extends Predicate & HasFieldReference<P>> void process(P predicate){
		FieldName name = predicate.getField();

		Integer count = this.nameCounts.get(name);

		this.nameCounts.put(name, count != null ? (count + 1) : 1);
	}
}