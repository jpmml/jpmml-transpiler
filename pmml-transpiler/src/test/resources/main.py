from sklearn2pmml.sklearn_pandas import patch_sklearn

patch_sklearn()

from lightgbm import LGBMClassifier, LGBMRegressor
from mlxtend.preprocessing import DenseTransformer
from pandas import DataFrame, Series
from sklearn.compose import ColumnTransformer
from sklearn.discriminant_analysis import LinearDiscriminantAnalysis
from sklearn.ensemble import AdaBoostRegressor, GradientBoostingClassifier, GradientBoostingRegressor, IsolationForest, RandomForestClassifier, RandomForestRegressor, VotingRegressor
from sklearn.feature_extraction.text import CountVectorizer, TfidfVectorizer
from sklearn.feature_selection import f_classif
from sklearn.feature_selection import SelectKBest
from sklearn.isotonic import IsotonicRegression
from sklearn.linear_model import LinearRegression, LogisticRegression
from sklearn.multiclass import OneVsRestClassifier
from sklearn.pipeline import make_pipeline
from sklearn.preprocessing import LabelEncoder, OneHotEncoder
from sklearn.svm import LinearSVC
from sklearn.tree import DecisionTreeClassifier, DecisionTreeRegressor, ExtraTreeClassifier, ExtraTreeRegressor
from sklearn_pandas import DataFrameMapper
from sklearn2pmml import sklearn2pmml
from sklearn2pmml.decoration import CategoricalDomain, ContinuousDomain
from sklearn2pmml.ensemble import SelectFirstClassifier, SelectFirstRegressor
from sklearn2pmml.feature_extraction.text import Matcher, Splitter
from sklearn2pmml.pipeline import PMMLPipeline
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

def cat_domain(name, **kwargs):
	return CategoricalDomain(invalid_value_treatment = "as_missing", with_data = False, with_statistics = False, **kwargs) if name.endswith("NA") else CategoricalDomain(with_statistics = True, **kwargs)

def cont_domain(name, **kwargs):
	return ContinuousDomain(invalid_value_treatment = "as_missing", with_data = False, with_statistics = False, **kwargs) if name.endswith("NA") else ContinuousDomain(with_statistics = True, **kwargs)

def make_column_dropper(drop_cols):
	return ColumnTransformer([
		("drop", "drop", drop_cols)
	], remainder = "passthrough")

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
	df["Deductions"] = df["Deductions"].astype(pandas.BooleanDtype() if name.endswith("NA") else bool)
	df["Income"] = df["Income"].astype(float)
	df["Hours"] = df["Hours"].astype(float)
	return split_csv(df)

audit_X, audit_y = load_audit("Audit")

# XXX
audit_X = audit_X.drop(["Deductions"], axis = 1)

def build_audit(classifier, name, **pmml_options):
	cat_columns = ["Employment", "Education", "Marital", "Occupation", "Gender", "Deductions"]
	cont_columns = ["Age", "Income", "Hours"]
	# XXX
	if "Deductions" not in audit_X.columns:
		cat_columns.remove("Deductions")
	#if isinstance(classifier, (LGBMClassifier, XGBClassifier)):
	#	cat_columns.insert(0, "Age")
	#	cont_columns.remove("Age")
	if isinstance(classifier, (LGBMClassifier, XGBClassifier)):
		cat_mappings = [([cat_column], cat_domain(name, dtype = "category")) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [cat_domain(name), OneHotEncoder()]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], cont_domain(name)) for cont_column in cont_columns]
	mappings = cat_mappings + cont_mappings
	if isinstance(classifier, (LGBMClassifier, XGBClassifier)):
		mapper = DataFrameMapper(mappings, input_df = True, df_out = True)
	elif isinstance(classifier, SelectFirstClassifier):
		mapper = DataFrameMapper(mappings + [(["Employment"], None)], df_out = True)
	else:
		mapper = DataFrameMapper(mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", classifier)
	])
	pipeline.fit(audit_X, audit_y)
	if isinstance(classifier, SelectFirstClassifier):
		pass
	elif isinstance(classifier, XGBClassifier):
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
	build_audit(OneVsRestClassifier(LogisticRegression(solver = "liblinear", random_state = 13)), "LogisticRegressionAudit")
	build_audit(RandomForestClassifier(n_estimators = 17, random_state = 13), "RandomForestAudit", compact = False, flat = False)
	build_audit(SelectFirstClassifier([("private", make_pipeline(make_column_dropper([-1]), LogisticRegression(random_state = 13)), "X['Employment'] in ['Consultant', 'Private', 'SelfEmp']"), ("public", make_pipeline(make_column_dropper([-1]), LogisticRegression(random_state = 13)), "X['Employment'] in ['PSFederal', 'PSLocal', 'PSState', 'Volunteer']")]), "SelectFirstAudit")
	build_audit(XGBClassifier(objective = "binary:logistic", n_estimators = 71, enable_categorical = True, random_state = 13), "XGBoostAudit", compact = True, input_float = True)

sparsify("Audit")

audit_X, audit_y = load_audit("AuditNA")

# XXX
audit_X = audit_X.drop(["Deductions"], axis = 1)

if ("Audit" in datasets) or ("AuditNA" in datasets):
	build_audit(LGBMClassifier(objective = "binary", n_estimators = 71, random_state = 13), "LightGBMAuditNA")
	build_audit(XGBClassifier(objective = "binary:logistic", n_estimators = 71, enable_categorical = True, random_state = 13), "XGBoostAuditNA", compact = True, input_float = True)

def load_sentiment(name):
	df = load_csv(name)
	return (df["Sentence"], df["Score"])

sentiment_X, sentiment_y = load_sentiment("Sentiment")

def build_sentiment(classifier, transformer, name, with_proba = True, **pmml_options):
	pipeline = PMMLPipeline([
		("transformer", transformer),
		("selector", SelectKBest(f_classif, k = 500)),
		("densifier", DenseTransformer()),
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
	build_sentiment(OneVsRestClassifier(LogisticRegression()), TfidfVectorizer(stop_words = "english", tokenizer = Matcher(), ngram_range = (1, 3), binary = True, norm = None, **pmml_textindex_args), "LogisticRegressionSentiment")
	build_sentiment(RandomForestClassifier(max_depth = 8, min_samples_leaf = 10, n_estimators = 31, random_state = 13), CountVectorizer(ngram_range = (1, 2), **pmml_textindex_args), "RandomForestSentiment")
	build_sentiment(XGBClassifier(objective = "binary:logistic", n_estimators = 31, random_state = 13), CountVectorizer(tokenizer = Matcher(), **pmml_textindex_args), "XGBoostSentiment")

#
# Multi-class classification
#

def load_iris(name):
	df = load_csv(name)
	return split_csv(df)

iris_X, iris_y = load_iris("Iris")

def build_iris(classifier, name, **pmml_options):
	cont_columns = ["Sepal.Length", "Sepal.Width", "Petal.Length", "Petal.Width"]
	cont_mappings = [([cont_column], cont_domain(name)) for cont_column in cont_columns]
	mapper = DataFrameMapper(cont_mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("classifier", classifier)
	])
	if isinstance(classifier, XGBClassifier):
		classifier._le = LabelEncoder()
		iris_y_le = Series(classifier._le.fit_transform(iris_y), name = "Species")
	else:
		iris_y_le = iris_y
	pipeline.fit(iris_X, iris_y_le)
	if isinstance(classifier, XGBClassifier):
		pipeline.verify(iris_X.sample(n = 3, random_state = 13), precision = 1e-5, zeroThreshold = 1e-5)
	else:
		pipeline.verify(iris_X.sample(n = 3, random_state = 13))
	pipeline.configure(**pmml_options)
	store_pmml(pipeline, name)
	species = DataFrame(pipeline.predict(iris_X), columns = ["Species"])
	if isinstance(classifier, XGBClassifier):
		species["Species"] = classifier._le.inverse_transform(species["Species"])
	species_proba = DataFrame(pipeline.predict_proba(iris_X), columns = ["probability(setosa)", "probability(versicolor)", "probability(virginica)"])
	store_csv(pandas.concat((species, species_proba), axis = 1), name)

if "Iris" in datasets:
	build_iris(DecisionTreeClassifier(min_samples_leaf = 5, random_state = 13), "DecisionTreeIris", compact = False, flat = True)
	build_iris(GradientBoostingClassifier(n_estimators = 11, random_state = 13), "GradientBoostingIris")
	build_iris(LGBMClassifier(objective = "multiclass", n_estimators = 11, random_state = 13), "LightGBMIris")
	build_iris(LogisticRegression(solver = "lbfgs", random_state = 13), "LogisticRegressionIris")
	build_iris(RandomForestClassifier(n_estimators = 5, random_state = 13), "RandomForestIris", compact = False, flat = False)
	build_iris(XGBClassifier(objective = "multi:softprob", n_estimators = 11, random_state = 13), "XGBoostIris", input_float = True)

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
	if isinstance(regressor, IsotonicRegression):
		cat_columns = []
		cont_columns = ["acceleration"]
	else:
		cat_columns = ["cylinders", "model_year", "origin"]
		cont_columns = ["displacement", "horsepower", "weight", "acceleration"]
	if isinstance(regressor, (LGBMRegressor, XGBRegressor)):
		cat_mappings = [([cat_column], cat_domain(name, dtype = "category")) for cat_column in cat_columns]
	else:
		cat_mappings = [([cat_column], [cat_domain(name), OneHotEncoder()]) for cat_column in cat_columns]
	cont_mappings = [([cont_column], [cont_domain(name)]) for cont_column in cont_columns]
	mappings = cat_mappings + cont_mappings
	if isinstance(regressor, (LGBMRegressor, XGBRegressor)):
		mapper = DataFrameMapper(mappings, input_df = True, df_out = True)
	elif isinstance(regressor, SelectFirstRegressor):
		mapper = DataFrameMapper(mappings + [(["cylinders"], None)], df_out = True)
	else:
		mapper = DataFrameMapper(mappings)
	pipeline = PMMLPipeline([
		("mapper", mapper),
		("regressor", regressor)
	])
	if isinstance(regressor, LGBMRegressor):
		pipeline.fit(auto_X, auto_y)
	elif isinstance(regressor, IsolationForest):
		pipeline.fit(auto_X)
	else:
		pipeline.fit(auto_X, auto_y)
	if isinstance(regressor, SelectFirstRegressor):
		pass
	elif isinstance(regressor, XGBRegressor):
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
	build_auto(IsotonicRegression(out_of_bounds = "clip"), "IsotonicRegressionAuto")
	build_auto(LinearRegression(), "LinearRegressionAuto")
	build_auto(RandomForestRegressor(n_estimators = 17, random_state = 13), "RandomForestAuto", compact = False, flat = False)
	build_auto(SelectFirstRegressor([("small", make_pipeline(make_column_dropper([-1]), LinearRegression()), "X['cylinders'] in [3, 4, 5]"), ("big", make_pipeline(make_column_dropper([-1]), LinearRegression()), "X['cylinders'] in [6, 8]")]), "SelectFirstAuto")
	build_auto(VotingRegressor(estimators = [("major", DecisionTreeRegressor(max_depth = 8, random_state = 13)), ("minor", ExtraTreeRegressor(max_depth = 5, random_state = 13))], weights = [0.7, 0.3]), "VotingEnsembleAuto")
	build_auto(XGBRegressor(objective = "reg:squarederror", n_estimators = 31, enable_categorical = True, random_state = 13), "XGBoostAuto", compact = True, input_float = True)

sparsify("Auto")

auto_X, auto_y = load_auto("AutoNA")

# XXX
auto_X["cylinders"] = auto_X["cylinders"].astype(pandas.StringDtype()).astype(object)
auto_X["model_year"] = auto_X["model_year"].astype(pandas.StringDtype()).astype(object)
auto_X["origin"] = auto_X["origin"].astype(pandas.StringDtype()).astype(object)

if ("Auto" in datasets) or ("AutoNA" in datasets):
	build_auto(LGBMRegressor(objective = "regression", n_estimators = 31, random_state = 13), "LightGBMAutoNA")
	build_auto(XGBRegressor(objective = "reg:squarederror", n_estimators = 31, enable_categorical = True, random_state = 13), "XGBoostAutoNA", compact = True, input_float = True)
