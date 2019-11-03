/*
 * Copyright (c) 2017 Villu Ruusmann
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
package com.jpmml.transpiler;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.jar.Manifest;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sun.codemodel.CodeWriter;
import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;
import org.jpmml.codemodel.ArchiverUtil;
import org.jpmml.codemodel.MarkedCodeWriter;
import org.jpmml.codemodel.JarCodeWriter;
import org.jpmml.model.PMMLUtil;

public class Main {

	@Parameter (
		names = {"--help"},
		description = "Show the list of configuration options and exit",
		help = true
	)
	private boolean help = false;

	@Parameter (
		names = {"--input", "--xml-input"},
		description = "PMML XML input file",
		required = true
	)
	private File input = null;

	@Parameter (
		names = {"--class-name"},
		description = "The fully qualified name of the transpiled PMML class",
		required = false
	)
	private String fullName = null;

	@Parameter (
		names = {"--output", "--jar-output"},
		description = "PMML service provider JAR output file",
		required = true
	)
	private File output = null;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			commander.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		if(main.help){
			StringBuilder sb = new StringBuilder();

			commander.usage(sb);

			System.out.println(sb.toString());

			System.exit(0);
		}

		main.run();
	}

	public void run() throws Exception {
		File input = getInput();
		String fullName = getFullName();
		File output = getOutput();

		PMML pmml;

		try(InputStream is = new FileInputStream(input)){
			pmml = PMMLUtil.unmarshal(is);
		}

		JCodeModel codeModel = TranspilerUtil.transpile(pmml, fullName);

		Manifest manifest = ArchiverUtil.createManifest(TranspilerUtil.class);

		try(OutputStream os = new FileOutputStream(output)){
			CodeWriter codeWriter = new MarkedCodeWriter(new JarCodeWriter(os, manifest), Main.HEADER);

			codeModel.build(codeWriter);
		}
	}

	public File getInput(){
		return this.input;
	}

	public void setInput(File input){
		this.input = input;
	}

	public String getFullName(){
		return this.fullName;
	}

	public void setFullName(String fullName){
		this.fullName = fullName;
	}

	public File getOutput(){
		return this.output;
	}

	public void setOutput(File output){
		this.output = output;
	}

	private static final String HEADER;

	static {
		String[] lines = {
			"/*",
			" * Copyright (c) 2019 Villu Ruusmann",
			" */"
		};
		String lineSeparator = System.getProperty("line.separator");

		StringBuilder sb = new StringBuilder();

		for(String line : lines){
			sb.append(line).append(lineSeparator);
		}

		HEADER = sb.toString();
	}
}