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

JPMML-Transpiler is a proprietary product.

The library JAR file is is not, and will not be, available from public repositories such as the Maven Central repository.

Obtaining and building JPMML-Transpiler locally using Apache Maven:

```
$ git checkout https://github.com/vruusmann/jpmml-transpiler.git
$ cd jpmml-transpiler
$ mvn clean install
```

The build produces two files:

* `target/jpmml-transpiler-1.0-SNAPSHOT.jar` - the library JAR file.
* `target/jpmml-transpiler-executable-1.0-SNAPSHOT.jar` - the executable uber-JAR file (the library JAR file plus all its transitive dependencies).

Adding JPMML-Transpiler to a project:

```xml
<dependency>
	<groupId>com.jpmml</groupId>
	<artifactId>jpmml-transpiler</artifactId>
	<version>1.0-SNAPSHOT</version>
</dependency>
```

# Usage #

Transpiling an XML-backed `org.dmg.pmml.PMML` object to an `com.sun.codemodel.JCodeModel` object:

```java
import com.jpmml.transpiler.TranspilerUtil;
import com.sun.codemodel.JCodeModel;
import org.jpmml.model.PMMLUtil;

PMML xmlPMML;

try(InputStream is = ...){
	xmlPmml = PMMLUtil.unmarshal(is);
}

JCodeModel codeModel = TranspilerUtil.transpile(xmlPmml);
```

This `JCodeModel` object holds complete PMML service provider information.

If the PMML document is small and the application is short-lived, then it's possible to load a Java-backed `org.dmg.pmml.PMML` object directly from the `JCodeModel` object:

```java
import org.jpmml.codemodel.JCodeModelClassLoader;

ClassLoader clazzLoader = new JCodeModelClassLoader(codeModel);

PMML javaPMML = PMMLUtil.load(clazzLoader);
```

However, if the PMML document is large (eg. some decision tree ensemble model) or the application is long-lived, then it's recommended to dump the `JCodeModel` object to a temporary JAR file in the local filesystem:

```java
import org.jpmml.codemodel.ArchiverUtil;

PMML javaPMML;

File tmpFile = File.createTempFile("pmml-", ".jar");

try {
	try(OutputStream os = new FileOutputStream(tmpFile)){
		ArchiverUtil.archive(codeModel, os);
	}

	javaPMML = PMMLUtil.load((tmpFile.toURI()).toURL());
} finally {
	tmpFile.delete();
}
```

A Java-backed `org.dmg.pmml.PMML` object typically does not benefit from applying extra optimizing or interning Visitors to it.

Building a model evaluator:

```java
import org.jpmml.evaluator.ModelEvaluatorBuilder;

Evaluator evaluator = new ModelEvaluatorBuilder(javaPMML)
	.build();
```

# Benchmarking #

The effect of transpilation on memory consumption and execution speed can be estimated using the `org.jpmml.evaluator.EvaluationExample` command-line application (download the latest executable uber-JAR file from [JPMML-Evaluator releases](https://github.com/jpmml/jpmml-evaluator/releases) page).

For example, evaluating the `/src/test/resources/pmml/XGBoostAudit.pmml` model with the `/src/test/resources/csv/Audit.csv` input dataset.

Evaluating the model in interpreted mode:

```
$ java -jar pmml-evaluator-example-executable-${version}.jar --model XGBoostAudit.pmml --input Audit.csv --output /dev/null --optimize --intern --loop 100

-- Timers ----------------------------------------------------------------------
main
             count = 1000
         mean rate = 10,05 calls/second
     1-minute rate = 9,53 calls/second
     5-minute rate = 7,98 calls/second
    15-minute rate = 7,49 calls/second
               min = 91,55 milliseconds
               max = 833,42 milliseconds
              mean = 99,48 milliseconds
            stddev = 29,66 milliseconds
            median = 93,09 milliseconds
              75% <= 96,19 milliseconds
              95% <= 131,04 milliseconds
              98% <= 153,78 milliseconds
              99% <= 180,36 milliseconds
            99.9% <= 833,03 milliseconds
```

Transpiling the model:

```
$ java -jar target/jpmml-transpiler-executable-1.0-SNAPSHOT.jar --pmml-input XGBoostAudit.pmml --jar-output XGBoostAudit.jar
```

Evaluating the model in transpiled mode:

```
$ java -jar pmml-evaluator-example-executable-${version}.jar --model XGBoostAudit.jar --input Audit.csv --output /dev/null --loop 1000

-- Timers ----------------------------------------------------------------------
main
             count = 1000
         mean rate = 56,87 calls/second
     1-minute rate = 48,34 calls/second
     5-minute rate = 46,50 calls/second
    15-minute rate = 46,17 calls/second
               min = 15,57 milliseconds
               max = 261,28 milliseconds
              mean = 17,51 milliseconds
            stddev = 9,64 milliseconds
            median = 15,86 milliseconds
              75% <= 16,11 milliseconds
              95% <= 20,31 milliseconds
              98% <= 31,16 milliseconds
              99% <= 48,06 milliseconds
            99.9% <= 261,13 milliseconds
```

In the current case, the transpilation has reduced the median evaluation time from 93 millis (for a batch of 1899 data records) to 16 millis, which is almost six times difference

# License #

JPMML-Transpiler is dual-licensed under the [GNU Affero General Public License (AGPL) version 3.0](https://www.gnu.org/licenses/agpl-3.0.html), and a commercial license.

# Additional information #

JPMML-Transpiler is developed and maintained by Openscoring Ltd, Estonia.

Interested in using JPMML software in your application? Please contact [info@openscoring.io](mailto:info@openscoring.io)