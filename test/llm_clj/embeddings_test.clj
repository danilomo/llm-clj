(ns llm-clj.embeddings-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.embeddings.openai :as embed]))

(deftest test-normalize-input-string
  (is (= ["hello"] (#'embed/normalize-input "hello"))))

(deftest test-normalize-input-vector
  (is (= ["a" "b"] (#'embed/normalize-input ["a" "b"]))))

(deftest test-build-payload-basic
  (let [payload (#'embed/build-payload "test" {})]
    (is (= ["test"] (:input payload)))
    (is (= "text-embedding-3-small" (:model payload)))))

(deftest test-build-payload-with-options
  (let [payload (#'embed/build-payload "test"
                                       {:model "text-embedding-3-large"
                                        :dimensions 512
                                        :user "user123"})]
    (is (= "text-embedding-3-large" (:model payload)))
    (is (= 512 (:dimensions payload)))
    (is (= "user123" (:user payload)))))

(deftest test-build-payload-with-encoding-format
  (let [payload (#'embed/build-payload "test" {:encoding-format :base64})]
    (is (= "base64" (:encoding_format payload)))))

(deftest test-cosine-similarity
  (is (= 1.0 (embed/cosine-similarity [1 0 0] [1 0 0])))
  (is (< (Math/abs (- 0.0 (embed/cosine-similarity [1 0 0] [0 1 0]))) 0.001))
  (is (< (Math/abs (- -1.0 (embed/cosine-similarity [1 0 0] [-1 0 0]))) 0.001)))

(deftest test-cosine-similarity-normalized-vectors
  (let [v1 [0.5 0.5 0.5 0.5]
        v2 [0.5 0.5 0.5 0.5]]
    (is (< (Math/abs (- 1.0 (embed/cosine-similarity v1 v2))) 0.001))))

(deftest test-cosine-similarity-orthogonal
  (let [v1 [1.0 0.0]
        v2 [0.0 1.0]]
    (is (< (Math/abs (embed/cosine-similarity v1 v2)) 0.001))))
