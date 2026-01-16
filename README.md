JPMML-Transpiler [![Build Status](https://github.com/jpmml/jpmml-transpiler/workflows/maven/badge.svg)](https://github.com/jpmml/jpmml-transpiler/actions?query=workflow%3A%22maven%22)
================

Java Transpiler (Translator + Compiler) API for Predictive Model Markup Language (PMML).

# Table of Contents #

* [Features](#features)
* [Prerequisites](#prerequisites)
* [Installation](#installation)
* [Usage](#usage)
  * [TL;DR](#tldr)
  * [Deep dive](#deep-dive)
* [Benchmarking](#benchmarking)
* [Support](#support)
* [License](#license)
* [Additional information](#additional-information)

# Features #

JPMML-Transpiler is a value add-on library to the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library platform.

JPMML-Transpiler traverses an `org.dmg.pmml.PMML` class model object, and "transpiles" dummy XML-backed objects into smart and optimized Java-backed objects for speedier execution:

* Expressions become `org.jpmml.evaluator.JavaExpression` subclasses.
* Models become `org.jpmml.evaluator.java.JavaModel` subclasses.
* Predicates become `org.jpmml.evaluator.JavaPredicate` subclasses.

# Prerequisites #

* JPMML-Evaluator 1.7.5 or newer.

# Installation #

### Release versions

The current release version is **1.4.8** (16 January, 2026):

JPMML-Transpiler library JAR files (together with accompanying Java source and Javadocs JAR files) are released via [Maven Central Repository](https://repo1.maven.org/maven2/org/jpmml/).

```xml
<dependency>
	<groupId>org.jpmml</groupId>
	<artifactId>pmml-transpiler</artifactId>
	<version>1.4.8</version>
</dependency>
```

JPMML-Transpiler executable uber-JAR files are released via [GitHub](https://github.com/jpmml/jpmml-transpiler/releases).

### Snapshot versions

Clone the project. Enter the project root directory and build using [Apache Maven](https://maven.apache.org/):

```
$ git clone https://github.com/jpmml/jpmml-transpiler.git
$ cd jpmml-transpiler
$ mvn clean install
```

The build produces two files:

* `pmml-transpiler/target/pmml-transpiler-1.4-SNAPSHOT.jar` - the library JAR file.
* `pmml-transpiler-example/target/pmml-transpiler-example-executable-1.4-SNAPSHOT.jar` - the executable uber-JAR file (the library JAR file plus all its transitive dependencies).

# Usage #

### TL;DR

Building a model evaluator from a PMML file using the `org.jpmml.evaluator.LoadingModelEvaluatorBuilder` builder class.

The transpilation is attempted by invoking the `LoadingModelEvaluatorBuilder#transform(PMMLTransformer)` method (between the load and build stages) with a properly configured `org.jpmml.transpiler.TranspilerTransformer` argument:

```java
import org.jpmml.evaluator.Evaluator;
import org.jpmml.evaluator.LoadingModelEvaluatorBuilder;
import org.jpmml.transpiler.FileTranspiler
//import org.jpmml.transpiler.InMemoryTranspiler
import org.jpmml.transpiler.Transpiler
import org.jpmml.transpiler.TranspilerTransformer

File pmmlFile = ...;

LoadingModelEvaluatorBuilder evaluatorBuilder = new LoadingModelEvaluatorBuilder()
	.load(pmmlFile);

try {
	Transpiler transpiler = new FileTranspiler("com.mycompany.MyModel", new File(pmmlFile.getAbsolutePath() + ".jar"));
	//Transpiler transpiler = new InMemoryTranspiler("com.mycompany.MyModel");

	evaluatorBuilder = evaluatorBuilder.transform(new TranspilerTransformer(transpiler));
} catch(IOException ioe){
	ioe.printStackTrace(System.err);
	//throw ioe;
}

Evaluator evaluator = evaluatorBuilder.build();
```

The internal state of the model evaluator builder is only updated if the transpilation succeeds.

### Deep dive

Transpiling an XML-backed `org.dmg.pmml.PMML` object to an `com.sun.codemodel.JCodeModel` object:

```java
import com.sun.codemodel.JCodeModel;
import org.jpmml.model.PMMLUtil;
import org.jpmml.transpiler.TranspilerUtil;

PMML xmlPmml;

try(InputStream is = ...){
	xmlPmml = PMMLUtil.unmarshal(is);
}

// Generate Java source
// Set the fully-qualified name of the generated PMML subclass to `com.mycompany.MyModel`
JCodeModel codeModel = TranspilerUtil.translate(xmlPmml, "com.mycompany.MyModel");

// Compile Java source to Java bytecode
TranspilerUtil.compile(codeModel);
```

Storing the `JCodeModel` object to a PMML service provider Java archive:

```java
File jarFile = new File(...);

try(OutputStream os = new FileOutputStream(jarFile)){
	TranspilerUtil.archive(codeModel, os);
}
```

A PMML service provider Java archive is a Java ARchive (JAR) file that contains all transpilation results (Java source and bytecode files), plus a `/META-INF/services/org.dmg.pmml.PMML` service provider configuration file.

Such Java archives can be regarded as "model plug-ins" to a Java application.

Creating a `java.net.URLClassLoader` object to interact with the contents of a PMML service provider Java archive in a local filesystem:

```java
import java.net.URL;
import java.net.URLClassLoader;

File jarFile = ...;

URL[] classpath = {
	(jarFile.toURI()).toURL()
};

try(URLClassLoader clazzLoader = new URLClassLoader(classpath)){
	// Load and instantiate generated PMML subclass(es)
}
```

The generated `PMML` subclass can always be loaded and instantiated using [Java's service-provider loading facility](https://docs.oracle.com/javase/8/docs/api/java/util/ServiceLoader.html).

If the classpath contains exactly one PMML service provider Java archive, then it's possible to use the `org.jpmml.model.PMMLUtil#load(ClassLoader)` utility method:

```java
import org.dmg.pmml.PMML;
import org.jpmml.model.PMMLUtil;

PMML javaPmml = PMMLUtil.load(clazzLoader);
```

If the classpath contains more than one PMML service provider Java archives, then it becomes necessary to fall back to Java's standard APIs:

```java
import java.util.ServiceLoader;
import org.dmg.pmml.PMML;

ServiceLoader<PMML> pmmlServiceLoader = ServiceLoader.load(PMML.class, clazzLoader);
for(Iterator<PMML> pmmlIt = pmmlServiceLoader.iterator(); pmmlIt.hasNext(); ){
	PMML javaPmml = pmmlIt.next();
}
```

Alternatively, if the fully qualified name of the generated `PMML` subclass is known, then it's possible to load and instantiate it manually:

```java
Class<?> clazz = clazzLoader.loadClass("com.mycompany.MyModel");

Class<? extends PMML> pmmlClazz = clazz.asSubclass(PMML.class);

PMML javaPmml = pmmlClazz.newInstance();
```

The Java-backed `PMML` object is at its peak performance right from the start. There is no need to apply any optimizers or interners to it. The only noteworthy downside of the Java-backed object compared to the XML-backed object is the lack of SAX Locator information, which makes pinpointing evaluation exceptions to exact model source code location more difficult.

Building a model evaluator from a Java-backed `PMML` object:

```java
import org.jpmml.evaluator.ModelEvaluatorBuilder;

Evaluator evaluator = new ModelEvaluatorBuilder(javaPmml)
	.build();
```

Building a model evaluator from a PMML service provider Java archive:

```java
import org.jpmml.evaluator.ServiceLoadingModelEvaluatorBuilder;

File jarFile = ...;

Evaluator evaluator = new ServiceLoadingModelEvaluatorBuilder()
	.loadService((jarFile.toURI()).toURL())
	.build();
```

Java-backed model evaluators are functionally equivalent to XML-backed model evaluators.

# Benchmarking #

See [benchmarking.md](benchmarking.md)

# Support #

Limited public support is available via the [JPMML mailing list](https://groups.google.com/forum/#!forum/jpmml).

# License #

JPMML-Transpiler is licensed under the terms and conditions of the [GNU Affero General Public License, Version 3.0](https://www.gnu.org/licenses/agpl-3.0.html).
For a quick summary of your rights ("Can") and obligations ("Cannot" and "Must") under AGPLv3, please refer to [TLDRLegal](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-(agpl-3.0)).

If you would like to use JPMML-Transpiler in a proprietary software project, then it is possible to enter into a licensing agreement which makes JPMML-Transpiler available under the terms and conditions of the [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) instead.

# Additional information #

JPMML-Transpiler is developed and maintained by Openscoring Ltd, Estonia.

Interested in using [Java PMML API](https://github.com/jpmml) software in your company? Please contact [info@openscoring.io](mailto:info@openscoring.io)
