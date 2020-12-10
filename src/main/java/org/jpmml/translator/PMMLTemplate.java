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

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;

import com.sun.codemodel.JInvocation;

import org.dmg.pmml.Model;
import org.dmg.pmml.PMML;
import org.dmg.pmml.PMMLElements;
import org.dmg.pmml.PMMLObject;
import org.dmg.pmml.Visitor;
import org.dmg.pmml.VisitorAction;
import org.dmg.pmml.mining.Segment;
import org.jpmml.converter.visitors.DataDictionaryCleaner;
import org.jpmml.converter.visitors.FunctionDictionaryCleaner;
import org.jpmml.converter.visitors.MiningSchemaCleaner;
import org.jpmml.converter.visitors.TransformationDictionaryCleaner;
import org.jpmml.model.ReflectionUtil;
import org.jpmml.model.visitors.AbstractVisitor;
import org.jpmml.model.visitors.VisitorBattery;

public class PMMLTemplate extends Template {

	PMMLTemplate(Class<? extends PMML> clazz){
		super(clazz, getFields(clazz));
	}

	@Override
	public JInvocation initializeObject(PMMLObject object, JInvocation invocation, TranslationContext context){
		PMML pmml = (PMML)object;

		List<Field> setterMethodFields = getSetterMethodFields();

		Field modelsField = PMMLElements.PMML_MODELS;

		List<Model> models = pmml.getModels();

		invocation = PMMLObjectUtil.addSetterMethod(modelsField, models, invocation, context);

		Map<Model, TranslatedModel> translations = context.getTranslations();
		if(!translations.isEmpty()){
			Visitor visitor = new AbstractVisitor(){

				@Override
				public VisitorAction visit(PMML pmml){

					if(pmml.hasModels()){
						List<Model> models = pmml.getModels();

						for(ListIterator<Model> it = models.listIterator(); it.hasNext(); ){
							it.set(filter(it.next()));
						}
					}

					return super.visit(pmml);
				}

				@Override
				public VisitorAction visit(Segment segment){
					segment.setModel(filter(segment.getModel()));

					return super.visit(segment);
				}

				private Model filter(Model model){
					TranslatedModel translatedModel = translations.get(model);
					if(translatedModel != null){
						return translatedModel;
					}

					return model;
				}
			};

			VisitorBattery visitorBattery = new VisitorBattery();
			visitorBattery.add(TransformationDictionaryCleaner.class);
			visitorBattery.add(DataDictionaryCleaner.class);
			visitorBattery.add(FunctionDictionaryCleaner.class);
			visitorBattery.add(MiningSchemaCleaner.class);

			visitor.applyTo(pmml);
			visitorBattery.applyTo(pmml);

			Collection<TranslatedModel> translatedModels = translations.values();
			for(TranslatedModel translatedModel : translatedModels){
				Model model = translatedModel.getModel();
				JWrappedExpression expression = translatedModel.getExpression();

				expression.setExpression(PMMLObjectUtil.initializeJavaModel(model, (JInvocation)expression.getExpression(), context));
			}
		}

		setterMethodFields.remove(modelsField);

		for(Field setterMethodField : setterMethodFields){
			Object value = ReflectionUtil.getFieldValue(setterMethodField, object);

			invocation = PMMLObjectUtil.addSetterMethod(setterMethodField, value, invocation, context);
		}

		return invocation;
	}

	static
	private List<Field> getFields(Class<? extends PMML> clazz){
		List<Field> fields = new ArrayList<>(ReflectionUtil.getFields(clazz));

		fields.remove(PMMLElements.PMML_TRANSFORMATIONDICTIONARY);
		fields.add(fields.size(), PMMLElements.PMML_TRANSFORMATIONDICTIONARY);

		return fields;
	}
}