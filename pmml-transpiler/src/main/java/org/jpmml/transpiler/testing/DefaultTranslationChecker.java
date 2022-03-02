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
package org.jpmml.transpiler.testing;

import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.model.visitors.AbstractVisitor;

public class DefaultTranslationChecker extends AbstractVisitor {

	@Override
	public VisitorAction visit(MiningModel miningModel){
		PMMLObject parent = getParent();

		if(!(parent instanceof PMML)){
			throw new UntranslatedElementException(miningModel);
		}

		return super.visit(miningModel);
	}

	@Override
	public VisitorAction visit(RegressionModel regressionModel){
		throw new UntranslatedElementException(regressionModel);
	}

	@Override
	public VisitorAction visit(TreeModel treeModel){
		throw new UntranslatedElementException(treeModel);
	}
}