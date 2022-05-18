from lightgbm import LGBMClassifier, LGBMRegressor
from mlxtend.preprocessing import DenseTransformer
from pandas import DataFrame
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis
from sklearn.ensemble import AdaBoostRegressor, GradientBoostingClassifier, GradientBoostingRegressor, IsolationForest, RandomForestClassifier, RandomForestRegressor, VotingRegressor
from sklearn.feature_extraction.text import CountVectorizer, TfidfVectorizer
from sklearn.feature_selection import f_classif
from sklearn.feature_selection import SelectKBest
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.preprocessing import LabelBinarizer, LabelEncoder
from sklearn.svm import LinearSVC
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor, ExtraTreeClassifier, ExtraTreeRegressor
from sklearn_pandas import DataFrameMapper
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.feature_extraction.text import Matcher, Splitter
from sklearn2pmml.pipeline import PMMLPipeline
from sklearn2pmml.preprocessing import PMMLLabelBinarizer, PMMLLabelEncoder
from xgboost.sklearn import XGBClassifier, XGBRegressor

import numpy
import pandas
import sys

def load_csv(name, dtype = None):
	return pandas.read_csv("csv/" + name + ".csv", dtype = dtype, na_values = ["N/A", "NA"])

def split_csv(df):
	columns = df.columns.tolist()
	return (df[columns[: -1]], df[columns[-1]])

def store_csv(df, name):
	df.to_csv("csv/" + name + ".csv", index = False, na_rep = "N/A")

def sparsify(name):
	df = load_csv(name, dtype = str)
	df_X, df_y = split_csv(df)

	numpy.random.seed(13)
	df_X = df_X.mask(numpy.random.random(df_X.shape) < 0.25, other = None)

	df = pandas.concat((df_X, df_y), axis = 1)
	store_csv(df, name + "NA")

def store_pmml(pipeline, name):
	sklearn2pmml(pipeline, "pmml/" + name + ".pmml")

def cat_domain(name):
	return CategoricalDomain(invalid_value_treatment = "as_missing", with_data = False, with_statistics = False) if name.endswith("NA") else CategoricalDomain()

def cont_domain(name):
	return ContinuousDomain(invalid_value_treatment = "as_missing", with_data = False, with_statistics = False) if name.endswith("NA") else ContinuousDomain()

def label_binarizer(name):
	return PMMLLabelBinarizer() if name.endswith("NA") else LabelBinarizer()

def label_encoder(name):
	return PMMLLabelEncoder() if name.endswith("NA") else LabelEncoder()

datasets = "Audit,Sentiment,Iris,Auto"

if __name__ == "__main__":
	if len(sys.argv) > 1:
		datasets = sys.argv[1]

datasets = datasets.split(",")

#
# Binary classification
#

def load_audit(name):
	df = load_csv(name)
	df = df.where((pandas.notnull(df)), None)
	df["Adjusted"] = df["Adjusted"].astype(int)
	df["Age"] = df["Age"].astype(pandas.Int64Dtype() if name.endswith("NA") else int)
	df["Income"] = df["Income"].astype(float)
	df["Hours"] = df["Hours"].astype(float)
	return split_csv(df)

audit_X, audit_y = load_audit("Audit")

def build_audit(classifier, name, **pmml_options):
	if isinstance(classifier, LGBMClassifier):
		cat_columns = ["Age", "Employment", "Education", "Marital", "Occupation", "Gender", "Deductions"]
		cont_columns = ["Income", "Hours"]
	else:
		cat_columns = ["Employment", "Education", "Marital", "Occupation", "Gender", "Deductions"]
		cont_columns = ["Age", "Income", "Hours"]
	if name in ["LightGBMAuditNA", "XGBoostAuditNA"]:
		cat_columns.remove("Deductions")
	if isinstance(classifier, LGBMClassifier):
		cat_mappings = [([cat_column], [cat_domain(name), label_encoder(name)]) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [cat_domain(name), label_binarizer(name)]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], cont_domain(name)) for cont_column in cont_columns]
	mapper = DataFrameMapper(cat_mappings + cont_mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", classifier)
	])
	if isinstance(classifier, LGBMClassifier):
		pipeline.fit(audit_X, audit_y, classifier__categorical_feature = [0, 1, 2, 3, 4, 5])
	elif isinstance(classifier, XGBClassifier):
		if name == "XGBoostAuditNA":
			audit_X["Age"] = audit_X["Age"].astype(float)
		pipeline.fit(audit_X, audit_y)
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
	build_audit(DecisionTreeClassifier(min_samples_leaf = 7, random_state = 13), "DecisionTreeAudit", compact = False, flat = True)
	build_audit(GradientBoostingClassifier(n_estimators = 71, random_state = 13), "GradientBoostingAudit")
	build_audit(LGBMClassifier(objective = "binary", n_estimators = 71, random_state = 13), "LightGBMAudit")
	build_audit(LogisticRegression(multi_class = "ovr", solver = "liblinear", random_state = 13), "LogisticRegressionAudit")
	build_audit(RandomForestClassifier(n_estimators = 17, random_state = 13), "RandomForestAudit", compact = False, flat = False)
	build_audit(XGBClassifier(objective = "binary:logistic", ntree_limit = 71, random_state = 13), "XGBoostAudit")

sparsify("Audit")

audit_X, audit_y = load_audit("AuditNA")

if ("Audit" in datasets) or ("AuditNA" in datasets):
	build_audit(LGBMClassifier(objective = "binary", n_estimators = 71, random_state = 13), "LightGBMAuditNA")
	build_audit(XGBClassifier(objective = "binary:logistic", ntree_limit = 71, random_state = 13), "XGBoostAuditNA")

def load_sentiment(name):
	df = load_csv(name)
	return (df["Sentence"], df["Score"])

sentiment_X, sentiment_y = load_sentiment("Sentiment")

def build_sentiment(classifier, transformer, name, with_proba = True, **pmml_options):
	pipeline = PMMLPipeline([
		("transformer", transformer),
		("densifier", DenseTransformer()),
		("selector", SelectKBest(f_classif, k = 500)),
		("classifier", classifier)
	])
	pipeline.fit(sentiment_X, sentiment_y)
	pipeline.configure(**pmml_options)
	store_pmml(pipeline, name)
	score = DataFrame(pipeline.predict(sentiment_X), columns = ["Score"])
	if with_proba:
		score_proba = DataFrame(pipeline.predict_proba(sentiment_X), columns = ["probability(0)", "probability(1)"])
		score = pandas.concat((score, score_proba), axis = 1)
	store_csv(score, name)

if "Sentiment" in datasets:
	pmml_textindex_args = dict(analyzer = "word", preprocessor = None, strip_accents = None, dtype = numpy.float64)
	build_sentiment(LinearDiscriminantAnalysis(), TfidfVectorizer(tokenizer = Splitter(), ngram_range = (1, 3), norm = None, **pmml_textindex_args), "LinearDiscriminantAnalysisSentiment")
	build_sentiment(LinearSVC(random_state = 13), CountVectorizer(tokenizer = Splitter(), ngram_range = (1, 2), **pmml_textindex_args), "LinearSVCSentiment", with_proba = False)
	build_sentiment(LogisticRegression(multi_class = "ovr"), TfidfVectorizer(stop_words = "english", tokenizer = Matcher(), ngram_range = (1, 3), binary = True, norm = None, **pmml_textindex_args), "LogisticRegressionSentiment")
	build_sentiment(RandomForestClassifier(max_depth = 8, min_samples_leaf = 10, n_estimators = 31, random_state = 13), CountVectorizer(ngram_range = (1, 2), **pmml_textindex_args), "RandomForestSentiment")
	build_sentiment(XGBClassifier(objective = "binary:logistic", ntree_limit = 31, random_state = 13), CountVectorizer(tokenizer = Matcher(), **pmml_textindex_args), "XGBoostSentiment")

#
# Multi-class classification
#

def load_iris(name):
	df = load_csv(name)
	return split_csv(df)

iris_X, iris_y = load_iris("Iris")

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
	build_iris(DecisionTreeClassifier(min_samples_leaf = 5, random_state = 13), "DecisionTreeIris", compact = False, flat = True)
	build_iris(GradientBoostingClassifier(n_estimators = 11, random_state = 13), "GradientBoostingIris")
	build_iris(LGBMClassifier(objective = "multiclass", solver = "lbfgs", n_estimators = 11, random_state = 13), "LightGBMIris")
	build_iris(LogisticRegression(multi_class = "multinomial", random_state = 13), "LogisticRegressionIris")
	build_iris(RandomForestClassifier(n_estimators = 5, random_state = 13), "RandomForestIris", compact = False, flat = False)
	build_iris(XGBClassifier(objective = "multi:softprob", n_estimators = 11, use_label_encoder = True, random_state = 13), "XGBoostIris")

iris_X, iris_y = load_iris("IrisVec")

def build_iris_vec(classifier, name):
	pipeline = PMMLPipeline([
		("classifier", classifier)
	])
	pipeline.fit(iris_X, iris_y)
	store_pmml(pipeline, name)
	species = DataFrame(pipeline.predict(iris_X), columns = ["Species"])
	species_proba = DataFrame(pipeline.predict_proba(iris_X), columns = ["probability(setosa)", "probability(versicolor)", "probability(virginica)"])
	store_csv(pandas.concat((species, species_proba), axis = 1), name)

if "Iris" in datasets:
	build_iris_vec(DecisionTreeClassifier(min_samples_leaf = 5, random_state = 13), "DecisionTreeIrisVec")
	build_iris_vec(RandomForestClassifier(n_estimators = 7, max_depth = 3, random_state = 13), "RandomForestIrisVec")

#
# Regression
#

def load_auto(name):
	df = load_csv(name)
	df = df.where((pandas.notnull(df)), None)
	df["cylinders"] = df["cylinders"].astype(pandas.Int64Dtype() if name.endswith("NA") else int)
	df["horsepower"] = df["horsepower"].astype(float)
	df["model_year"] = df["model_year"].astype(pandas.Int64Dtype() if name.endswith("NA") else int)
	df["mpg"] = df["mpg"].astype(float)
	df["origin"] = df["origin"].astype(pandas.Int64Dtype() if name.endswith("NA") else int)
	df["weight"] = df["weight"].astype(float)
	return split_csv(df)

auto_X, auto_y = load_auto("Auto")

def build_auto(regressor, name, **pmml_options):
	cat_columns = ["cylinders", "model_year", "origin"]
	cont_columns = ["displacement", "horsepower", "weight", "acceleration"]
	if isinstance(regressor, LGBMRegressor):
		cat_mappings = [([cat_column], [cat_domain(name), label_encoder(name)]) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [cat_domain(name), label_binarizer(name)]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], [cont_domain(name)]) for cont_column in cont_columns]
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
	build_auto(DecisionTreeRegressor(random_state = 13), "DecisionTreeAuto", compact = False, flat = True)
	build_auto(GradientBoostingRegressor(n_estimators = 31, random_state = 13), "GradientBoostingAuto")
	build_auto(IsolationForest(n_estimators = 31, random_state = 13), "IsolationForestAuto")
	build_auto(LGBMRegressor(objective = "regression", n_estimators = 31, random_state = 13), "LightGBMAuto")
	build_auto(LinearRegression(), "LinearRegressionAuto")
	build_auto(RandomForestRegressor(n_estimators = 17, random_state = 13), "RandomForestAuto", compact = False, flat = False)
	build_auto(VotingRegressor(estimators = [("major", DecisionTreeRegressor(max_depth = 8, random_state = 13)), ("minor", ExtraTreeRegressor(max_depth = 5, random_state = 13))], weights = [0.7, 0.3]), "VotingEnsembleAuto")
	build_auto(XGBRegressor(objective = "reg:squarederror", n_estimators = 31, random_state = 13), "XGBoostAuto")

sparsify("Auto")

auto_X, auto_y = load_auto("AutoNA")

if ("Auto" in datasets) or ("AutoNA" in datasets):
	build_auto(LGBMRegressor(objective = "regression", n_estimators = 31, random_state = 13), "LightGBMAutoNA")
	build_auto(XGBRegressor(objective = "reg:squarederror", n_estimators = 31, random_state = 13), "XGBoostAutoNA")