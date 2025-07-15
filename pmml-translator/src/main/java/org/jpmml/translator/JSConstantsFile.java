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

import java.io.IOException;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.codemodel.JResourceFile;

public class JSConstantsFile extends JResourceFile {

	private Map<String, List<Object>> data = new LinkedHashMap<>();


	public JSConstantsFile(){
		super("constants.js");
	}

	@Override
	public void build(OutputStream os) throws IOException {
		Map<String, ?> data = getData();

		ObjectMapper objectMapper = new ObjectMapper();

		JsonFactory jsonFactory = objectMapper.getFactory();

		Writer writer = new OutputStreamWriter(os, StandardCharsets.UTF_8){

			@Override
			public void close() throws IOException {
			}
		};

		JsonGenerator jsonGenerator = jsonFactory.createGenerator(writer);

		Collection<? extends Map.Entry<String, ?>> entries = data.entrySet();
		for(Map.Entry<String, ?> entry : entries){
			String key = entry.getKey();
			Object value = entry.getValue();

			jsonGenerator.writeRaw("var " + key + " = ");
			jsonGenerator.writeObject(value);
			jsonGenerator.writeRaw(";");

			jsonGenerator.writeRaw('\n');
		}

		jsonGenerator.close();
	}

	public List<Object> get(String name){
		Map<String, List<Object>> data = getData();

		return data.get(name);
	}

	public void put(String name, List<Object> objects){
		Map<String, List<Object>> data = getData();

		data.put(name, objects);
	}

	public Map<String, List<Object>> getData(){
		return this.data;
	}
}