(ns llm-clj.images-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.images.openai :as img]
            [llm-clj.images.core :as images]))

(defn has-api-key? []
  (boolean (System/getenv "OPENAI_API_KEY")))

(deftest ^:integration test-generate-image
  (when (has-api-key?)
    (let [provider (img/create-provider {})
          result (images/generate-image provider "A simple blue circle"
                                        {:model "dall-e-2"
                                         :size "256x256"
                                         :n 1})]
      (is (= 1 (count (:images result))))
      (is (string? (-> result :images first :url))))))

(deftest ^:integration test-generate-with-b64
  (when (has-api-key?)
    (let [provider (img/create-provider {})
          result (images/generate-image provider "A red square"
                                        {:model "dall-e-2"
                                         :size "256x256"
                                         :response-format :b64_json})]
      (is (-> result :images first :b64-json))
      (let [bytes (img/decode-b64-image (-> result :images first :b64-json))]
        (is (pos? (count bytes)))))))

(deftest ^:integration test-convenience-function
  (when (has-api-key?)
    (let [result (img/generate "A green triangle"
                               {:model "dall-e-2" :size "256x256"})]
      (is (:images result)))))

(deftest ^:integration test-save-image
  (when (has-api-key?)
    (let [result (img/generate "A yellow star"
                               {:model "dall-e-2" :size "256x256"})
          temp-file (java.io.File/createTempFile "test-image" ".png")]
      (try
        (img/save-image (-> result :images first :url) (.getPath temp-file))
        (is (.exists temp-file))
        (is (pos? (.length temp-file)))
        (finally
          (.delete temp-file))))))

(deftest ^:integration test-generate-with-timestamp
  (when (has-api-key?)
    (let [result (img/generate "A purple diamond"
                               {:model "dall-e-2" :size "256x256"})]
      (is (number? (:created result)))
      (is (pos? (:created result))))))
