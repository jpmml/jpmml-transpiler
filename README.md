JPMML-Transpiler
================

Java Transpiler (Translator + Compiler) API for Predictive Model Markup Language (PMML).

# Features #

JPMML-Transpiler is a value add-on library to the [JPMML-Evaluator](https://github.com/jpmml/jpmml-evaluator) library platform.

JPMML-Transpiler traverses an `org.dmg.pmml.PMML` class model object, and "transpiles" dummy XML-backed objects into smart and optimized Java-backed objects for speedier execution:

* Expressions become `org.jpmml.evaluator.JavaExpression` subclasses.
* Models become `org.jpmml.evaluator.java.JavaModel` subclasses.
* Predicates become `org.jpmml.evaluator.JavaPredicate` subclasses.

# Prerequisites #

* JPMML-Evaluator 1.5.0 or newer.

# Installation #

### Release versions

The current release version is **1.1.0** (6 May, 2020):

JPMML-Transpiler library JAR files (together with accompanying Java source and Javadocs JAR files) are released via [Maven Central Repository](https://repo1.maven.org/maven2/org/jpmml/).

```xml
<dependency>
	<groupId>org.jpmml</groupId>
	<artifactId>jpmml-transpiler</artifactId>
	<version>1.1.0</version>
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

* `target/jpmml-transpiler-1.1-SNAPSHOT.jar` - the library JAR file.
* `target/jpmml-transpiler-executable-1.1-SNAPSHOT.jar` - the executable uber-JAR file (the library JAR file plus all its transitive dependencies).

# Usage #

### Transpiling XML-backed PMML objects to Java codemodel objects

Transpiling an XML-backed `org.dmg.pmml.PMML` object to an `com.sun.codemodel.JCodeModel` object:

```java
import com.sun.codemodel.JCodeModel;
import org.jpmml.model.PMMLUtil;
import org.jpmml.transpiler.TranspilerUtil;

PMML xmlPmml;

try(InputStream is = ...){
	xmlPmml = PMMLUtil.unmarshal(is);
}

// Set the fully-qualified name of the generated PMML subclass to `com.mycompany.MyModel`
JCodeModel codeModel = TranspilerUtil.transpile(xmlPmml, "com.mycompany.MyModel");
```

### Storing Java codemodel objects to PMML service provider JAR files

Storing the `JCodeModel` object to a PMML service provider Java archive:

```java
import org.jpmml.codemodel.ArchiverUtil;

File jarFile = new File(...);

try(OutputStream os = new FileOutputStream(jarFile)){
	ArchiverUtil.archive(codeModel, os);
}
```

### Loading Java-backed PMML objects from PMML service provider JAR files

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

### Building model evaluators

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

### Protocol

The effect of transpilation on memory consumption and execution speed can be estimated using the `org.jpmml.evaluator.EvaluationExample` command-line application (download the latest JPMML-Evaluator example executable uber-JAR file from the [JPMML-Evaluator releases](https://github.com/jpmml/jpmml-evaluator/releases) page).

For example, evaluating the `/src/test/resources/pmml/LightGBMAudit.pmml` model with the `/src/test/resources/csv/Audit.csv` input dataset.

Evaluating the model in interpreted mode:

```
$ java -jar pmml-evaluator-example-executable-1.5-SNAPSHOT.jar --model LightGBMAudit.pmml --input Audit.csv --output /dev/null --optimize --intern --loop 100

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

Transpiling the model:

```
$ java -jar target/jpmml-transpiler-executable-1.1-SNAPSHOT.jar --xml-input LightGBMAudit.pmml --jar-output LightGBMAudit.jar
```

Getting help:

```
$ java -jar target/jpmml-transpiler-executable-1.1-SNAPSHOT.jar --help
```

Evaluating the model in transpiled mode:

```
$ java -jar pmml-evaluator-example-executable-1.5-SNAPSHOT.jar --model LightGBMAudit.jar --input Audit.csv --output /dev/null --loop 1000

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

### Results

The project includes a [benchmarking script](https://github.com/jpmml/jpmml-transpiler/tree/master/src/test/resources/benchmark.sh), which executes a number Scikit-Learn, LightGBM and XGBoost models first in "interpreted mode" (JPMML-Evaluator alone) and then in "transpiled mode" (JPMML-Transpiler on top of JPMML-Evaluator).

The reported metric is the median batch prediction time. Absolute timings are rather meaningless. What matters is the ratio between the timings for interpreted mode and transpiled mode, which is called "speed-up factor" in the tables below.

All the benchmarked model PMML documents are available in the [PMML test resources directory](https://github.com/jpmml/jpmml-transpiler/tree/master/src/test/resources/pmml/).

##### Audit dataset

Binary classification using three continuous features and five categorical (string) features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeAudit.pmml | 9.50 | 6.96 | 1.4 |
| GradientBoostingAudit.pmml | 80.89 | 23.95 | 3.4 |
| LightGBMAudit.pmml | 268.27 | 21.77 | 12.3 |
| RandomForestAudit.pmml | 106.84 | 21.62 | 4.9 |
| XGBoostAudit.pmml | 73.44 | 13.86 | 5.3 |

##### Iris dataset

Multi-class classification using four continuous features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeIris.pmml | 0.48 | 0.32 | 1.5 |
| LightGBMIris.pmml | 4.57 | 0.40 | 11.4 |
| XGBoostIris.pmml | 2.65 | 0.38 | 7.0 |

##### Auto dataset

Regression using four continuous features and three categorical (integer) features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeAuto.pmml | 1.69 | 0.96 | 1.8 |
| GradientBoostingAuto.pmml | 7.01 | 1.38 | 5.1 |
| LightGBMAuto.pmml | 20.60 | 1.22 | 16.9 |
| RandomForestAuto.pmml | 13.44 | 2.35 | 5.7 |
| XGBoostAuto.pmml | 6.24 | 1.15 | 5.4 |

# Support #

Limited public support is available via the [JPMML mailing list](https://groups.google.com/forum/#!forum/jpmml).

# License #

JPMML-Transpiler is licensed under the terms and conditions of the [GNU Affero General Public License, Version 3.0](https://www.gnu.org/licenses/agpl-3.0.html).
For a quick summary of your rights ("Can") and obligations ("Cannot" and "Must") under AGPLv3, please refer to [TLDRLegal](https://tldrlegal.com/license/gnu-affero-general-public-license-v3-(agpl-3.0)).

If you would like to use JPMML-Transpiler in a proprietary software project, then it is possible to enter into a licensing agreement which makes JPMML-Transpiler available under the terms and conditions of the [BSD 3-Clause License](https://opensource.org/licenses/BSD-3-Clause) instead.
Please initiate the conversation by submitting the [Request for Quotation](https://openscoring.io/rfq/) web form, or sending an e-mail.

# Additional information #

JPMML-Transpiler is developed and maintained by Openscoring Ltd, Estonia.

Interested in using [Java PMML API](https://github.com/jpmml) software in your company? Please contact [info@openscoring.io](mailto:info@openscoring.io)
