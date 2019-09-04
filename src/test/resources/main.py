from lightgbm import LGBMClassifier, LGBMRegressor
from pandas import DataFrame
from sklearn.ensemble import AdaBoostRegressor, GradientBoostingClassifier, GradientBoostingRegressor, IsolationForest, RandomForestRegressor, VotingRegressor
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.preprocessing import LabelBinarizer, LabelEncoder
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor, ExtraTreeRegressor
from sklearn_pandas import DataFrameMapper
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.pipeline import PMMLPipeline
from xgboost.sklearn import XGBClassifier, XGBRegressor

import pandas
import sys

def load_csv(name):
	return pandas.read_csv("csv/" + name + ".csv", na_values = ["N/A", "NA"])

def split_csv(df):
	columns = df.columns.tolist()
	return (df[columns[: -1]], df[columns[-1]])

def store_csv(df, name):
	df.to_csv("csv/" + name + ".csv", index = False)

def store_pmml(pipeline, name):
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml")

datasets = "Audit,Iris,Auto"

if __name__ == "__main__":
	if len(sys.argv) > 1:
		datasets = sys.argv[1]

datasets = datasets.split(",")

#
# Binary classification
#

audit_df = load_csv("Audit")

audit_df["Adjusted"] = audit_df["Adjusted"].astype(int)

audit_X, audit_y = split_csv(audit_df)

def build_audit(classifier, name, **pmml_options):
	cat_columns = ["Employment", "Education", "Marital", "Occupation", "Gender"]
	cont_columns = ["Age", "Income", "Hours"]
	if isinstance(classifier, LGBMClassifier):
		cat_mappings = [([cat_column], [CategoricalDomain(), LabelEncoder()]) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [CategoricalDomain(), LabelBinarizer()]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], ContinuousDomain()) for cont_column in cont_columns]
	mapper = DataFrameMapper(cat_mappings + cont_mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", classifier)
	])
	if isinstance(classifier, LGBMClassifier):
		pipeline.fit(audit_X, audit_y, classifier__categorical_feature = [0, 1, 2, 3, 4])
	else:
		pipeline.fit(audit_X, audit_y)
	if isinstance(classifier, XGBClassifier):
		pipeline.verify(audit_X.sample(n = 3, random_state = 13), precision = 1e-5, zeroThreshold = 1e-5)
	else:
		pipeline.verify(audit_X.sample(n = 3, random_state = 13))
	pipeline.configure(**pmml_options)
	store_pmml(pipeline, name)
	adjusted = DataFrame(pipeline.predict(audit_X), columns = ["Adjusted"])
	adjusted_proba = DataFrame(pipeline.predict_proba(audit_X), columns = ["probability(0)", "probability(1)"])
	store_csv(pandas.concat((adjusted, adjusted_proba), axis = 1), name)

if "Audit" in datasets:
	build_audit(DecisionTreeClassifier(min_samples_leaf = 7, random_state = 13), "DecisionTreeAudit")
	build_audit(GradientBoostingClassifier(n_estimators = 71, random_state = 13), "GradientBoostingAudit")
	build_audit(LGBMClassifier(objective = "binary", n_estimators = 71, random_state = 13), "LightGBMAudit")
	build_audit(LogisticRegression(random_state = 13), "LogisticRegressionAudit")
	build_audit(XGBClassifier(objective = "binary:logistic", ntree_limit = 71, random_state = 13), "XGBoostAudit")

#
# Multi-class classification
#

iris_df = load_csv("Iris")

iris_X, iris_y = split_csv(iris_df)

def build_iris(classifier, name, **pmml_options):
	cont_columns = ["Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width"]
	cont_mappings = [([cont_column], ContinuousDomain()) for cont_column in cont_columns]
	mapper = DataFrameMapper(cont_mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", classifier)
	])
	pipeline.fit(iris_X, iris_y)
	if isinstance(classifier, XGBClassifier):
		pipeline.verify(iris_X.sample(n = 3, random_state = 13), precision = 1e-5, zeroThreshold = 1e-5)
	else:
		pipeline.verify(iris_X.sample(n = 3, random_state = 13))
	pipeline.configure(**pmml_options)
	store_pmml(pipeline, name)
	species = DataFrame(pipeline.predict(iris_X), columns = ["Species"])
	species_proba = DataFrame(pipeline.predict_proba(iris_X), columns = ["probability(setosa)", "probability(versicolor)", "probability(virginica)"])
	store_csv(pandas.concat((species, species_proba), axis = 1), name)

if "Iris" in datasets:
	build_iris(DecisionTreeClassifier(min_samples_leaf = 5, random_state = 13), "DecisionTreeIris")
	build_iris(GradientBoostingClassifier(n_estimators = 11, random_state = 13), "GradientBoostingIris")
	build_iris(LGBMClassifier(objective = "multiclass", n_estimators = 11, random_state = 13), "LightGBMIris")
	build_iris(LogisticRegression(random_state = 13), "LogisticRegressionIris")
	build_iris(XGBClassifier(objective = "multi:softprob", n_estimators = 11, random_state = 13), "XGBoostIris")

#
# Regression
#

auto_df = load_csv("Auto")

auto_df["model_year"] = auto_df["model_year"].astype(int)
auto_df["origin"] = auto_df["origin"].astype(int)

auto_X, auto_y = split_csv(auto_df)

def build_auto(regressor, name, **pmml_options):
	cat_columns = ["cylinders", "model_year", "origin"]
	cont_columns = ["displacement", "horsepower", "weight", "acceleration"]
	if isinstance(regressor, LGBMRegressor):
		cat_mappings = [([cat_column], [CategoricalDomain(), LabelEncoder()]) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [CategoricalDomain(), LabelBinarizer()]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], [ContinuousDomain()]) for cont_column in cont_columns]
	mapper = DataFrameMapper(cat_mappings + cont_mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("regressor", regressor)
	])
	if isinstance(regressor, LGBMRegressor):
		pipeline.fit(auto_X, auto_y, regressor__categorical_feature = [0, 1, 2])
	elif isinstance(regressor, IsolationForest):
		pipeline.fit(auto_X)
	else:
		pipeline.fit(auto_X, auto_y)
	if isinstance(regressor, XGBRegressor):
		pipeline.verify(auto_X.sample(n = 3, random_state = 13), precision = 1e-5, zeroThreshold = 1e-5)
	else:
		pipeline.verify(auto_X.sample(n = 3, random_state = 13))
	pipeline.configure(**pmml_options)
	store_pmml(pipeline, name)
	if isinstance(regressor, IsolationForest):
		decision_function = DataFrame(pipeline.decision_function(auto_X), columns = ["decisionFunction"])
		outlier = DataFrame(pipeline.predict(auto_X), columns = ["outlier"])
		outlier['outlier'] = outlier['outlier'].apply(lambda x: str(bool(x == -1)).lower())
		store_csv(pandas.concat((decision_function, outlier), axis = 1), name)
	else:
		mpg = DataFrame(pipeline.predict(auto_X), columns = ["mpg"])
		store_csv(mpg, name)

if "Auto" in datasets:
	build_auto(AdaBoostRegressor(n_estimators = 31, random_state = 13), "AdaBoostAuto")
	build_auto(DecisionTreeRegressor(random_state = 13), "DecisionTreeAuto")
	build_auto(GradientBoostingRegressor(n_estimators = 31, random_state = 13), "GradientBoostingAuto")
	build_auto(IsolationForest(n_estimators = 31, random_state = 13), "IsolationForestAuto")
	build_auto(LGBMRegressor(objective = "regression", n_estimators = 31, random_state = 13), "LightGBMAuto")
	build_auto(LinearRegression(), "LinearRegressionAuto")
	build_auto(RandomForestRegressor(n_estimators = 17, random_state = 13), "RandomForestAuto")
	build_auto(VotingRegressor(estimators = [("major", DecisionTreeRegressor(max_depth = 8, random_state = 13)), ("minor", ExtraTreeRegressor(max_depth = 5, random_state = 13))], weights = [0.7, 0.3]), "VotingEnsembleAuto")
	build_auto(XGBRegressor(objective = "reg:linear", n_estimators = 31, random_state = 13), "XGBoostAuto")