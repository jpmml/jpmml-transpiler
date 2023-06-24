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

import java.lang.reflect.Field;
import java.util.List;

import com.sun.codemodel.JBlock;
import com.sun.codemodel.JCase;
import com.sun.codemodel.JSwitch;

public class JSwitchUtil {

	private JSwitchUtil(){
	}

	static
	public void chainCases(JSwitch switchStatement, JCase firstCase, JCase secondCase){
		List<JCase> cases;

		try {
			Field casesField = JSwitch.class.getDeclaredField("cases");
			if(!casesField.isAccessible()){
				casesField.setAccessible(true);
			}

			cases = (List)casesField.get(switchStatement);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}

		int index = cases.indexOf(firstCase);
		if(index < 0){
			throw new IllegalArgumentException();
		} // End if

		if(cases.contains(secondCase)){
			cases.remove(secondCase);
		}

		cases.add(index + 1, secondCase);

		try {
			Field bodyField = JCase.class.getDeclaredField("body");
			if(!bodyField.isAccessible()){
				bodyField.setAccessible(true);
			}

			JBlock body = (JBlock)bodyField.get(firstCase);

			bodyField.set(firstCase, null);
			bodyField.set(secondCase, body);
		} catch(ReflectiveOperationException roe){
			throw new RuntimeException(roe);
		}
	}
}