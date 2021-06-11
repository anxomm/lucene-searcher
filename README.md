# Lucene searcher utilities

## Introduction

This project provides with some useful classes to formulate queries and compare
different rankings over a Lucene index.

Tests have been carried out with Java 8, Lucene 8.1.1 and the
[NPL collection](http://ir.dcs.gla.ac.uk/resources/test_collections/npl/).

## Setup

In order to make the setup process easier, a _pom.xml_ was added, so importing
it as a Maven project should be enough.

## IndexNPL

Indexes the documents into a Lucene index. The options available are:

- -index \<path\>: path where the index will be created
- -openmode \<mode\>: the mode can be
  - create: create a whole new index
  - append: add documents to an existing index
  - create_or_append: create a new index if it does not exist, otherwise is the
  same as _append_

There are some variables specified in a _config.properties_ file, located at
_src/main/resources/_. The variables are:

- docs: top level folders or files to be indexed
- indexingmodel: language model to compare files with each other. Can be
  - jm \<lambda\>: Jelinek-Mercer smoothing model
  - dir \<mu\>: Dirichlet smooting model
  - tfidf: no smoothing

This class is a simple version of IndexFiles from [lucene-indexer](https://github.com/anxomm/lucene-indexer), so if more complex options are required just download the original class
from that other repository.

## SearchEvalNPL

Evaluates the queries from the NPL collection over the index created. The
queries and relevant documents are specified in the _config.properties_ file.

- -indexin \<path>\: path of the index
- -search \<model\>: must be the same as the one specified to index with IndexNPL
- -cut \<n\>: compute the results only till position _n_ of the ranking
- -metrica \<metric\>: which metric to compute. Can be
  - P: precision
  - R: recall
  - MAP: average precision
- -top \<m\>: show only the first _m_ documents from the ranking
- -queries \<range\>:
  - \<queryID\>: evaluate the query with id _queryID_
  - \<queryID1-queryID2\>: evaluate the queries in the range id _queryID1_-_queryID2_
  - all: evalute all the queries

## TrainingTestNPL

Finds the best smoothing parameter values for the language models, based on
a train test. Then is applied to a test set. The queries and relevant
documents are specified in the _config.properties_ file.

- -indexin \<path>\: path of the index
- -cut \<n\>: compute the results only till position _n_ of the ranking
- -metrica \<metric\>: which metric to compute. Can be
  - P: precision
  - R: recall
  - MAP: average precision
- -evaljm \<trainID1-trainID2\> \<testID1-testID2>: apply the Jelinek-Mercer
smoothing and train with the queries with id _trainID1_-_trainID2_. Then apply
the model with the best parameter value to the queries with id _testID1_-_testID2_
- -evaldir \<trainID1-trainID2\> \<testID1-testID2>: apply the Dirichlet
smoothing and train with the queries with id _trainID1_-_trainID2_. Then apply
the model with the best parameter value to the queries with id _testID1_-_testID2_
- -outfile \<path\>: file to save the results over the test set

-evaljm and -evaldir are **exclusive** options

## Compare

Computes a significance test over two models trained and tests by
TrainingTestNPL.

- -results \<results1\> \<results2\>: files obtained with TrainingTestNPL over
the same metric, training and test sets
- -test \<alpha\>: computes a [t-test](https://en.wikipedia.org/wiki/Student%27s_t-test)
with a significance level equal to _alpha_
- -wilcoxon \<alpha>: computes [Wilcoxon test](https://en.wikipedia.org/wiki/Wilcoxon_signed-rank_test)
with a significance level equal to _alpha_

-test and -wilcoxon are **exclusive** options.

## ManualRelevanceFeedbackNPL

Allows to do real relevance feedback. The queries and relevant documents
are specified in the _config.properties_ file.

- -indexin \<path>\: path of the index
- -cut \<n\>: compute the results only till position _n_ of the ranking
- -metrica \<metric\>: which metric to compute. Can be
  - P: precision
  - R: recall
  - MAP: average precision
- -retmodel \<model\>: the options are the same as in the _indexingmodel_ variable
- -query \<queryID\>: id of the query over the one relevance feedback is done

After the initial query is thrown, you will be able to expand or modify the query
and evaluate it over the same relevant documents and retrieval model of the
initial one.

---

## Execution

Each class can be executed like an ordinary .java, but can be executed with
Maven as well. To do so, run:

```
mvn package
mvn exec:java -Dexec.mainClass="<class_name>" -Dexec.args="<args>"
```

Notice that the first command will create _.jar_ files, so the files could also
be executed like

```
java -jar target/<class_name>-0.0.1-SNAPSHOT-jar-with-dependencies.jar <args>
```
