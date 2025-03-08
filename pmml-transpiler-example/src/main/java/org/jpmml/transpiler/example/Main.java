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
package org.jpmml.transpiler.example;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import com.beust.jcommander.DefaultUsageFormatter;
import com.beust.jcommander.IUsageFormatter;
import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.sun.codemodel.JCodeModel;
import org.dmg.pmml.PMML;
import org.jpmml.model.PMMLUtil;
import org.jpmml.transpiler.TranspilerUtil;

public class Main {

	@Parameter (
		names = {"--input", "--pmml-input", "--xml-input"},
		description = "PMML XML input file",
		required = true,
		order = 1
	)
	private File input = null;

	@Parameter (
		names = {"--output", "--jar-output"},
		description = "PMML service provider JAR output file",
		order = 2
	)
	private File output = null;

	@Parameter (
		names = {"--class-name"},
		description = "The fully qualified name of the transpiled PMML class",
		required = false,
		order = 3
	)
	private String className = null;

	@Parameter (
		names = {"--help"},
		description = "Show the list of configuration options and exit",
		help = true,
		order = Integer.MAX_VALUE
	)
	private boolean help = false;


	static
	public void main(String... args) throws Exception {
		Main main = new Main();

		JCommander commander = new JCommander(main);
		commander.setProgramName(Main.class.getName());

		IUsageFormatter usageFormatter = new DefaultUsageFormatter(commander);

		try {
			commander.parse(args);
		} catch(ParameterException pe){
			StringBuilder sb = new StringBuilder();

			sb.append(pe.toString());
			sb.append("\n");

			usageFormatter.usage(sb);

			System.err.println(sb.toString());

			System.exit(-1);
		}

		if(main.help){
			StringBuilder sb = new StringBuilder();

			usageFormatter.usage(sb);

			System.out.println(sb.toString());

			System.exit(0);
		}

		main.run();
	}

	public void run() throws Exception {
		File input = getInput();
		File output = getOutput();

		if(output == null){
			output = new File(input.getAbsolutePath() + ".jar");
		} else

		if(output.isDirectory()){
			output = new File(output, input.getName() + ".jar");
		}

		String className = getClassName();

		JCodeModel codeModel;

		try(InputStream is = new FileInputStream(input)){
			PMML pmml = PMMLUtil.unmarshal(is);

			codeModel = TranspilerUtil.translate(pmml, className);
		}

		try {
			TranspilerUtil.compile(codeModel);
		// Inform the end user about the compilation exception, and keep going
		} catch(IOException ioe){
			ioe.printStackTrace(System.err);
		}

		try(OutputStream os = new FileOutputStream(output)){
			TranspilerUtil.archive(codeModel, os);
		}
	}

	public File getInput(){
		return this.input;
	}

	public void setInput(File input){
		this.input = input;
	}

	public File getOutput(){
		return this.output;
	}

	public void setOutput(File output){
		this.output = output;
	}

	public String getClassName(){
		return this.className;
	}

	public void setClassName(String className){
		this.className = className;
	}
}