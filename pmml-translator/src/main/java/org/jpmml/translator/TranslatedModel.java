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

import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import org.dmg.pmml.ForwardingModel;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.jpmml.model.visitors.HasActiveFields;

public class TranslatedModel extends ForwardingModel implements HasActiveFields {

	private JWrappedExpression expression = null;

	private Set<String> activeFields = Collections.emptySet();


	public TranslatedModel(Model model){
		super(model);
	}

	public JWrappedExpression getExpression(){
		return this.expression;
	}

	public TranslatedModel setExpression(JWrappedExpression expression){
		this.expression = expression;

		return this;
	}

	@Override
	public Set<String> getActiveFields(){
		return this.activeFields;
	}

	public TranslatedModel setActiveFields(Set<String> activeFields){
		this.activeFields = Objects.requireNonNull(activeFields);

		return this;
	}

	@Override
	public VisitorAction accept(Visitor visitor){
		VisitorAction status = visitor.visit(this);

		if(status == VisitorAction.CONTINUE){
			visitor.pushParent(this);

			status = PMMLObject.traverse(visitor, getMiningSchema(), getOutput(), getModelStats(), getModelExplanation(), getTargets(), getLocalTransformations(), getModelVerification());

			visitor.popParent();
		} // End if

		if(status == VisitorAction.TERMINATE){
			return VisitorAction.TERMINATE;
		}

		return status;
	}
}