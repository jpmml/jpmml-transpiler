JPMML-Transpiler
================

Java Transpiler (Translator + Compiler) API for Predictive Model Markup Language (PMML).

# Features #

JPMML-Transpiler traverses an `org.dmg.pmml.PMML` class model object, and "transpiles" XML-backed objects into executable Java-backed objects:

* Expressions become `org.jpmml.evaluator.JavaExpression` subclasses.
* Models become `org.jpmml.evaluator.java.JavaModel` subclasses.
* Predicates become `org.jpmml.evaluator.JavaPredicate` subclasses.

Transpilation results (Java source files plus Java bytecode files) are packaged into a "PMML service provider" JAR.

A PMML service provider JAR is simply a JAR file that contains a `/META-INF/services/org.dmg.pmml.PMML` service provider configuration file.

# Prerequisites #

* JPMML-Evaluator 1.4.13 or newer

# Installation #

JPMML-Transpiler is a proprietary product. The library JAR file is not, and will not be, available from public repositories such as the Maven Central repository.

The current version is **1.0.3** (5. November, 2019).

Obtaining and building JPMML-Transpiler locally using Apache Maven:

```
$ git clone https://github.com/vruusmann/jpmml-transpiler.git
$ cd jpmml-transpiler
$ git checkout ${version}
$ mvn clean install
```

The build produces two files:

* `target/jpmml-transpiler-${version}.jar` - the library JAR file.
* `target/jpmml-transpiler-executable-${version}.jar` - the executable uber-JAR file (the library JAR file plus all its transitive dependencies).

Adding JPMML-Transpiler to a project:

```xml
<dependency>
	<groupId>com.jpmml</groupId>
	<artifactId>jpmml-transpiler</artifactId>
	<version>${version}</version>
</dependency>
```

# Usage #

Transpiling an XML-backed `org.dmg.pmml.PMML` object to an `com.sun.codemodel.JCodeModel` object:

```java
import com.jpmml.transpiler.TranspilerUtil;
import com.sun.codemodel.JCodeModel;
import org.jpmml.model.PMMLUtil;

PMML xmlPmml;

try(InputStream is = ...){
	xmlPmml = PMMLUtil.unmarshal(is);
}

JCodeModel codeModel = TranspilerUtil.transpile(xmlPmml, null);
```

This `JCodeModel` object holds complete PMML service provider information.

If the PMML document is small and the application is short-lived, then it's possible to load a Java-backed `org.dmg.pmml.PMML` object directly from the `JCodeModel` object:

```java
import org.jpmml.codemodel.JCodeModelClassLoader;

ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

PMML javaPmml = PMMLUtil.load(clazzLoader);
```

However, if the PMML document is large (eg. some decision tree ensemble model) or the application is long-lived, then it's recommended to dump the `JCodeModel` object to a temporary JAR file in the local filesystem:

```java
import org.jpmml.codemodel.ArchiverUtil;

PMML javaPmml;

File tmpFile = File.createTempFile("pmml-", ".jar");

try {
	try(OutputStream os = new FileOutputStream(tmpFile)){
		ArchiverUtil.archive(codeModel, os);
	}

	javaPmml = PMMLUtil.load((tmpFile.toURI()).toURL());
} finally {
	tmpFile.delete();
}
```

A Java-backed `org.dmg.pmml.PMML` object typically does not benefit from applying extra optimizing or interning Visitors to it.

Building a model evaluator:

```java
import org.jpmml.evaluator.ModelEvaluatorBuilder;

Evaluator evaluator = new ModelEvaluatorBuilder(javaPmml)
	.build();
```

# Benchmarking #

The effect of transpilation on memory consumption and execution speed can be estimated using the `org.jpmml.evaluator.EvaluationExample` command-line application (download the latest JPMML-Evaluator example executable uber-JAR file from the [JPMML-Evaluator releases](https://github.com/jpmml/jpmml-evaluator/releases) page).

For example, evaluating the `/src/test/resources/pmml/LightGBMAudit.pmml` model with the `/src/test/resources/csv/Audit.csv` input dataset.

Evaluating the model in interpreted mode:

```
$ java -jar pmml-evaluator-example-executable-${version}.jar --model LightGBMAudit.pmml --input Audit.csv --output /dev/null --optimize --intern --loop 100

-- Timers ----------------------------------------------------------------------
main
             count = 1000
         mean rate = 3,52 calls/second
     1-minute rate = 3,52 calls/second
     5-minute rate = 3,16 calls/second
    15-minute rate = 2,85 calls/second
               min = 262,09 milliseconds
               max = 1042,44 milliseconds
              mean = 283,81 milliseconds
            stddev = 39,11 milliseconds
            median = 269,18 milliseconds
              75% <= 280,05 milliseconds
              95% <= 366,40 milliseconds
              98% <= 373,75 milliseconds
              99% <= 377,32 milliseconds
            99.9% <= 1041,82 milliseconds
```

Transpiling the model (download the latest JPMML-Transpiler executable uber-JAR file from the [JPMML-Transpiler releases](https://github.com/vruusmann/jpmml-transpiler/releases) page):

```
$ java -jar target/jpmml-transpiler-executable-${version}.jar --xml-input LightGBMAudit.pmml --jar-output LightGBMAudit.jar
```

Getting help:

```
$ java -jar target/jpmml-transpiler-executable-${version}.jar --help
```

Evaluating the model in transpiled mode:

```
$ java -jar pmml-evaluator-example-executable-${version}.jar --model LightGBMAudit.jar --input Audit.csv --output /dev/null --loop 1000

-- Timers ----------------------------------------------------------------------
main
             count = 1000
         mean rate = 34,34 calls/second
     1-minute rate = 20,98 calls/second
     5-minute rate = 15,59 calls/second
    15-minute rate = 14,54 calls/second
               min = 23,81 milliseconds
               max = 325,43 milliseconds
              mean = 29,05 milliseconds
            stddev = 15,82 milliseconds
            median = 24,06 milliseconds
              75% <= 25,48 milliseconds
              95% <= 60,53 milliseconds
              98% <= 72,75 milliseconds
              99% <= 91,66 milliseconds
            99.9% <= 325,24 milliseconds
```

In the current case, the transpilation has reduced the median evaluation time from 269 millis (for a batch of 1899 data records) to 24 millis, which is more than eleven times difference.

# License #

JPMML-Transpiler is dual-licensed under the [GNU Affero General Public License (AGPL) version 3.0](https://www.gnu.org/licenses/agpl-3.0.html), and a commercial license.

# Additional information #

JPMML-Transpiler is developed and maintained by Openscoring Ltd, Estonia.

Interested in using JPMML software in your application? Please contact [info@openscoring.io](mailto:info@openscoring.io)