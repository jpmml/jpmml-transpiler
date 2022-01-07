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
package org.jpmml.translator.mining;

import java.util.List;

import org.dmg.pmml.LocalTransformations;
import org.dmg.pmml.MiningField;
import org.dmg.pmml.MiningSchema;
import org.dmg.pmml.Model;
import org.dmg.pmml.Output;
import org.dmg.pmml.PMML;
import org.dmg.pmml.Targets;
import org.dmg.pmml.mining.MiningModel;
import org.dmg.pmml.mining.Segment;
import org.dmg.pmml.mining.Segmentation;
import org.jpmml.evaluator.InputFieldUtil;
import org.jpmml.evaluator.UnsupportedElementException;
import org.jpmml.translator.ModelTranslator;
import org.jpmml.translator.ModelTranslatorFactory;

abstract
public class MiningModelTranslator extends ModelTranslator<MiningModel> {

	public MiningModelTranslator(PMML pmml, MiningModel miningModel){
		super(pmml, miningModel);

		Segmentation segmentation = miningModel.requireSegmentation();

		@SuppressWarnings("unused")
		Segmentation.MultipleModelMethod multipleModelMethod = segmentation.requireMultipleModelMethod();

		@SuppressWarnings("unused")
		List<Segment> segments = segmentation.requireSegments();
	}

	public ModelTranslator<?> newModelTranslator(Model model){
		PMML pmml = getPMML();

		ModelTranslatorFactory modelTranslatorFactory = ModelTranslatorFactory.getInstance();

		return modelTranslatorFactory.newModelTranslator(pmml, model);
	}

	static
	public void checkMiningSchema(Model model){
		MiningSchema miningSchema = model.requireMiningSchema();

		if(miningSchema.hasMiningFields()){
			List<MiningField> miningFields = miningSchema.getMiningFields();

			for(MiningField miningField : miningFields){
				MiningField.UsageType usageType = miningField.getUsageType();

				switch(usageType){
					case ACTIVE:
						break;
					default:
						continue;
				}

				if(!InputFieldUtil.isDefault(null, miningField)){
					throw new UnsupportedElementException(miningField);
				}
			}
		}
	}

	static
	public void checkLocalTransformations(Model model){
		LocalTransformations localTransformations = model.getLocalTransformations();

		if(localTransformations != null && localTransformations.hasDerivedFields()){
			throw new UnsupportedElementException(localTransformations);
		}
	}

	static
	public void checkTargets(Model model){
		Targets targets = model.getTargets();

		if(targets != null && targets.hasTargets()){
			throw new UnsupportedElementException(targets);
		}
	}

	static
	public void checkOutput(Model model){
		Output output = model.getOutput();

		if(output != null && output.hasOutputFields()){
			throw new UnsupportedElementException(output);
		}
	}

	static
	public void pullUpDerivedFields(MiningModel parent, Model child){
		LocalTransformations parentLocalTransformations = parent.getLocalTransformations();
		LocalTransformations childLocalTransformations = child.getLocalTransformations();

		if(childLocalTransformations != null && childLocalTransformations.hasDerivedFields()){

			if(parentLocalTransformations == null){
				parentLocalTransformations = new LocalTransformations();

				parent.setLocalTransformations(parentLocalTransformations);
			}

			(parentLocalTransformations.getDerivedFields()).addAll(childLocalTransformations.getDerivedFields());

			// XXX
			child.setLocalTransformations(null);
		}
	}

	static
	public void pullUpOutputFields(MiningModel parent, Model child){
		Output parentOutput = parent.getOutput();
		Output childOutput = child.getOutput();

		if(childOutput != null && childOutput.hasOutputFields()){

			if(parentOutput == null){
				parentOutput = new Output();

				parent.setOutput(parentOutput);
			}

			(parentOutput.getOutputFields()).addAll(childOutput.getOutputFields());
		}
	}
}