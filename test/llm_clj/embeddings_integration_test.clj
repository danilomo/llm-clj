(ns llm-clj.embeddings-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.embeddings.openai :as embed]
            [llm-clj.embeddings.core :as embeddings]))

(defn has-api-key? []
  (boolean (System/getenv "OPENAI_API_KEY")))

(deftest ^:integration test-single-embedding
  (when (has-api-key?)
    (let [provider (embed/create-provider {})
          result (embeddings/create-embedding provider "Hello world" {})]
      (is (= 1 (count (:embeddings result))))
      (is (vector? (first (:embeddings result))))
      (is (every? number? (first (:embeddings result)))))))

(deftest ^:integration test-batch-embeddings
  (when (has-api-key?)
    (let [provider (embed/create-provider {})
          result (embeddings/create-embedding provider
                                              ["one" "two" "three"]
                                              {})]
      (is (= 3 (count (:embeddings result)))))))

(deftest ^:integration test-custom-dimensions
  (when (has-api-key?)
    (let [provider (embed/create-provider {})
          result (embeddings/create-embedding provider "test"
                                              {:model "text-embedding-3-small"
                                               :dimensions 512})]
      (is (= 512 (count (first (:embeddings result))))))))

(deftest ^:integration test-semantic-similarity
  (when (has-api-key?)
    (let [result (embed/embed ["cat" "kitten" "automobile"])
          [cat kitten car] (:embeddings result)
          cat-kitten (embed/cosine-similarity cat kitten)
          cat-car (embed/cosine-similarity cat car)]
      ;; cat should be more similar to kitten than car
      (is (> cat-kitten cat-car)))))

(deftest ^:integration test-convenience-function
  (when (has-api-key?)
    (let [result (embed/embed "Hello")]
      (is (:embeddings result))
      (is (:usage result)))))

(deftest ^:integration test-usage-information
  (when (has-api-key?)
    (let [result (embed/embed "Hello world")]
      (is (pos? (get-in result [:usage :prompt-tokens])))
      (is (pos? (get-in result [:usage :total-tokens]))))))
