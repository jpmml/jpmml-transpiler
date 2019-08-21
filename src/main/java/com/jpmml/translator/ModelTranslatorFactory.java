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

import java.util.Objects;

import com.jpmml.translator.mining.ModelChainTranslator;
import com.jpmml.translator.mining.TreeModelAggregatorTranslator;
import com.jpmml.translator.regression.RegressionModelTranslator;
import com.jpmml.translator.tree.TreeModelTranslator;
import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.PMMLElements;
import org.dmg.pmml.mining.Segmentation;
import org.dmg.pmml.regression.RegressionModel;
import org.dmg.pmml.tree.TreeModel;
import org.jpmml.evaluator.MissingElementException;
import org.jpmml.evaluator.UnsupportedElementException;

public class ModelTranslatorFactory {

	protected ModelTranslatorFactory(){
	}

	public ModelTranslator<?> newModelTranslator(PMML pmml, Model model){
		Objects.requireNonNull(pmml);
		Objects.requireNonNull(model);

		if(model instanceof MiningModel){
			MiningModel miningModel = (MiningModel)model;

			Segmentation segmentation = miningModel.getSegmentation();
			if(segmentation == null){
				throw new MissingElementException(miningModel, PMMLElements.MININGMODEL_SEGMENTATION);
			}

			Segmentation.MultipleModelMethod  multipleModelMethod = segmentation.getMultipleModelMethod();
			switch(multipleModelMethod){
				case MODEL_CHAIN:
					return new ModelChainTranslator(pmml, miningModel);
				default:
					return new TreeModelAggregatorTranslator(pmml, miningModel);
			}
		} else

		if(model instanceof RegressionModel){
			RegressionModel regressionModel = (RegressionModel)model;

			return new RegressionModelTranslator(pmml, regressionModel);
		} else

		if(model instanceof TreeModel){
			TreeModel treeModel = (TreeModel)model;

			return new TreeModelTranslator(pmml, treeModel);
		}

		throw new UnsupportedElementException(model);
	}

	static
	public ModelTranslatorFactory newInstance(){
		return new ModelTranslatorFactory();
	}
}