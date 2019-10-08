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

import java.util.Collection;
import java.util.Iterator;

import com.sun.codemodel.JExpression;
import com.sun.codemodel.JVar;

abstract
public class ObjectRef extends JVarRef {

	public ObjectRef(JVar variable){
		super(variable);
	}

	public JExpression equalTo(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression notEqualTo(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression lessThan(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression lessOrEqual(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression greaterOrEqual(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression greaterThan(JExpression valueExpr){
		throw new UnsupportedOperationException();
	}

	public JExpression isIn(Collection<JExpression> valueExprs){
		Iterator<JExpression> it = valueExprs.iterator();

		JExpression result = equalTo(it.next());

		while(it.hasNext()){
			result = result.cor(equalTo(it.next()));
		}

		return result;
	}

	public JExpression isNotIn(Collection<JExpression> valueExprs){
		Iterator<JExpression> it = valueExprs.iterator();

		JExpression result = notEqualTo(it.next());

		while(it.hasNext()){
			result = result.cand(notEqualTo(it.next()));
		}

		return result;
	}
}