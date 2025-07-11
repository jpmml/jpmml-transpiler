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
package org.jpmml.translator;

import java.util.Objects;
import java.util.Set;

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.ResultFeature;
import org.jpmml.evaluator.ModelManagerFactory;
import org.jpmml.model.UnsupportedElementException;

public class ModelTranslatorFactory extends ModelManagerFactory<ModelTranslator<?>> {

	protected ModelTranslatorFactory(){
		super((Class)ModelTranslator.class);
	}

	public ModelTranslator<?> newModelTranslator(PMML pmml, Model model){
		return newModelManager(pmml, model);
	}

	public ModelTranslator<?> newModelTranslator(PMML pmml, Model model, Set<ResultFeature> extraResultFeature){
		return newModelManager(pmml, model, extraResultFeature);
	}

	@Override
	public ModelTranslator<?> newModelManager(PMML pmml, Model model, Set<ResultFeature> extraResultFeatures){
		Objects.requireNonNull(pmml);
		Objects.requireNonNull(model);

		if(!ModelTranslatorFactory.ENABLED){
			throw new UnsupportedElementException(model);
		}

		return super.newModelManager(pmml, model, extraResultFeatures);
	}

	static
	public ModelTranslatorFactory getInstance(){
		return ModelTranslatorFactory.INSTANCE;
	}

	private static final boolean ENABLED = Boolean.parseBoolean(System.getProperty(ModelTranslatorFactory.class.getName() + "#ENABLED", "true"));

	private static final ModelTranslatorFactory INSTANCE = new ModelTranslatorFactory();
}