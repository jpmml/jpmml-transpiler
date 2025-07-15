/*
 * Copyright (c) 2025 Villu Ruusmann
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

public class JResourceInitializerFactory {

	protected JResourceInitializerFactory(){
	}

	public JResourceInitializer newResourceInitializer(String name, TranslationContext context){
		return new JBinaryFileInitializer(name, context);
	}

	static
	public JResourceInitializerFactory getInstance(){
		return JResourceInitializerFactory.INSTANCE;
	}

	private static final JResourceInitializerFactory INSTANCE = new JResourceInitializerFactory();
}