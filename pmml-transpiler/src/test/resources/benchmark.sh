#!/bin/bash

export JPMML_EVALUATOR_JAR=~/Workspace/jpmml-evaluator/pmml-evaluator-example/target/pmml-evaluator-example-executable-*.jar
export JPMML_TRANSPILER_JAR=~/Workspace/jpmml-transpiler/pmml-transpiler-example/target/pmml-transpiler-example-executable-*.jar

for dataset in "Audit" "Iris" "Auto"; do
	echo ${dataset}
	for model in $(ls pmml/*${dataset}.pmml); do
		echo ${model}
		java -jar ${JPMML_EVALUATOR_JAR} --model ${model} --input csv/${dataset}.csv --output /dev/null --optimize --intern --loop 10000 | grep "min =\|median ="
		java -jar ${JPMML_TRANSPILER_JAR} --xml-input ${model} --jar-output ${model}.jar
		java -jar ${JPMML_EVALUATOR_JAR} --model ${model}.jar --input csv/${dataset}.csv --output /dev/null --loop 10000 | grep "min =\|median ="
		rm ${model}.jar
	done
done