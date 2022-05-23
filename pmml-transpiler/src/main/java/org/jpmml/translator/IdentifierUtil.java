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

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.dmg.pmml.PMMLObject;

public class IdentifierUtil {

	private IdentifierUtil(){
	}

	static
	public String sanitize(String name){
		name = sanitizeWhitespace(name);
		name = sanitizePunctuation(name);

		StringBuffer sb = new StringBuffer();

		for(int i = 0, max = name.length(); i < max; i++){
			char c = name.charAt(i);

			if(sb.length() == 0){

				if(Character.isJavaIdentifierStart(c)){
					sb.append(Character.toLowerCase(c));
				} else

				if(Character.isJavaIdentifierPart(c)){
					sb.append('_').append(c);
				}
			} else

			{
				if(Character.isJavaIdentifierPart(c)){
					sb.append(c);
				}
			}
		}

		return sb.toString();
	}

	static
	public String sanitizeWhitespace(String name){
		Pattern pattern = Pattern.compile("\\s+");

		Matcher matcher = pattern.matcher(name);

		return matcher.replaceAll("_");
	}

	static
	public String sanitizePunctuation(String name){
		Pattern pattern = Pattern.compile("\\-+");

		StringBuffer sb = new StringBuffer();

		Matcher matcher = pattern.matcher(name);

		while(matcher.find()){
			int start = matcher.start();
			int end = matcher.end();

			if((start > 0 && isLetterOrDigit(name, start - 1)) && (end < name.length() && isLetterOrDigit(name, end))){
				matcher.appendReplacement(sb, "_");
			} else

			{
				matcher.appendReplacement(sb, matcher.group(0));
			}
		}

		matcher.appendTail(sb);

		return sb.toString();
	}

	static
	private boolean isLetterOrDigit(String string, int index){
		char c = string.charAt(index);

		return Character.isLetterOrDigit(c);
	}

	static
	public String create(String prefix, String name){
		name = name.intern(); // XXX

		return prefix + "$" + System.identityHashCode(name);
	}

	static
	public String create(String prefix, PMMLObject object){
		return prefix + "$" + System.identityHashCode(object);
	}

	static
	public String create(String prefix, PMMLObject object, String name){
		return create(create(prefix, object), name);
	}

	static
	public String create(String prefix, List<?> objects){
		return prefix + "$" + System.identityHashCode(objects);
	}
}