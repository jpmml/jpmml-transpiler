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
package org.jpmml.translator;

import com.sun.codemodel.JMod;

public interface Modifiers {

	int PUBLIC_STATIC_FINAL = (JMod.PUBLIC | JMod.FINAL | JMod.STATIC);
	int PRIVATE_STATIC_FINAL = (JMod.PRIVATE | JMod.FINAL | JMod.STATIC);

	int PUBLIC_ABSTRACT = (JMod.PUBLIC | JMod.ABSTRACT);

	int PUBLIC_FINAL = (JMod.PUBLIC | JMod.FINAL);
	int PRIVATE_FINAL = (JMod.PRIVATE | JMod.FINAL);

	int PUBLIC = JMod.PUBLIC;
	int PRIVATE = JMod.PRIVATE;
}