# Protocol #

The effect of transpilation on memory consumption and execution speed can be estimated using the `org.jpmml.evaluator.example.EvaluationExample` command-line application (download the latest JPMML-Evaluator example executable uber-JAR file from the [JPMML-Evaluator releases](https://github.com/jpmml/jpmml-evaluator/releases) page).

For example, evaluating the `LightGBMAudit.pmml` model with the `Audit.csv` input dataset.

Evaluating the model in interpreted mode:

```
$ java -jar pmml-evaluator-example-executable-1.6-SNAPSHOT.jar --model LightGBMAudit.pmml --input Audit.csv --output /dev/null --optimize --intern --loop 100

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
$ java -jar pmml-transpiler-example-executable-1.3-SNAPSHOT.jar --pmml-input LightGBMAudit.pmml --jar-output LightGBMAudit.jar
```

Getting help:

```
$ java -jar pmml-transpiler-example-executable-1.3-SNAPSHOT.jar --help
```

Evaluating the model in transpiled mode:

```
$ java -jar pmml-evaluator-example-executable-1.6-SNAPSHOT.jar --model LightGBMAudit.jar --input Audit.csv --output /dev/null --loop 1000

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

# Results #

The project includes a [benchmarking script](https://github.com/jpmml/jpmml-transpiler/tree/master/pmml-transpiler/src/test/resources/benchmark.sh), which executes a number Scikit-Learn, LightGBM and XGBoost models first in "interpreted mode" (JPMML-Evaluator alone) and then in "transpiled mode" (JPMML-Transpiler on top of JPMML-Evaluator).

The reported metric is the median batch prediction time. Absolute timings are rather meaningless. What matters is the ratio between the timings for interpreted mode and transpiled mode, which is called "speed-up factor" in the tables below.

All the benchmarked model PMML documents are available in the [PMML test resources directory](https://github.com/jpmml/jpmml-transpiler/tree/master/pmml-transpiler/src/test/resources/pmml/).

### Audit dataset

Binary classification using three continuous features and five categorical (string) features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeAudit.pmml | 9.50 | 6.96 | 1.4 |
| GradientBoostingAudit.pmml | 80.89 | 23.95 | 3.4 |
| LightGBMAudit.pmml | 268.27 | 21.77 | 12.3 |
| RandomForestAudit.pmml | 106.84 | 21.62 | 4.9 |
| XGBoostAudit.pmml | 73.44 | 13.86 | 5.3 |

### Iris dataset

Multi-class classification using four continuous features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeIris.pmml | 0.48 | 0.32 | 1.5 |
| LightGBMIris.pmml | 4.57 | 0.40 | 11.4 |
| XGBoostIris.pmml | 2.65 | 0.38 | 7.0 |

### Auto dataset

Regression using four continuous features and three categorical (integer) features.

| Model | Interpreted (ms) | Transpiled (ms) | Speed-up factor |
| ----- | ---------------- | --------------- | --------------- |
| DecisionTreeAuto.pmml | 1.69 | 0.96 | 1.8 |
| GradientBoostingAuto.pmml | 7.01 | 1.38 | 5.1 |
| LightGBMAuto.pmml | 20.60 | 1.22 | 16.9 |
| RandomForestAuto.pmml | 13.44 | 2.35 | 5.7 |
| XGBoostAuto.pmml | 6.24 | 1.15 | 5.4 | 
