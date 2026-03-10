(ns llm-clj.responses-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.responses.openai :as resp]))

(deftest test-format-input-string
  (is (= "hello" (#'resp/format-input "hello"))))

(deftest test-format-input-vector
  (is (= [{:type "text" :text "hi"}]
         (#'resp/format-input [{:type "text" :text "hi"}]))))

(deftest test-build-payload-basic
  (let [payload (#'resp/build-payload "test" {:model "gpt-4o"})]
    (is (= "test" (:input payload)))
    (is (= "gpt-4o" (:model payload)))))

(deftest test-build-payload-default-model
  (let [payload (#'resp/build-payload "test" {})]
    (is (= "gpt-4o" (:model payload)))))

(deftest test-build-payload-with-tools
  (let [payload (#'resp/build-payload "test" {:tools [:web_search]})]
    (is (= [{:type "web_search"}] (:tools payload)))))

(deftest test-build-payload-multi-turn
  (let [payload (#'resp/build-payload "test" {:previous-response-id "resp_123"})]
    (is (= "resp_123" (:previous_response_id payload)))))

(deftest test-build-payload-with-instructions
  (let [payload (#'resp/build-payload "test" {:instructions "Be helpful"})]
    (is (= "Be helpful" (:instructions payload)))))

(deftest test-build-payload-with-temperature
  (let [payload (#'resp/build-payload "test" {:temperature 0.7})]
    (is (= 0.7 (:temperature payload)))))

(deftest test-build-payload-with-max-output-tokens
  (let [payload (#'resp/build-payload "test" {:max-output-tokens 500})]
    (is (= 500 (:max_output_tokens payload)))))

(deftest test-build-payload-with-store
  (let [payload (#'resp/build-payload "test" {:store true})]
    (is (= true (:store payload)))))

(deftest test-format-tool-keyword
  (is (= {:type "web_search"} (#'resp/format-tool :web_search))))

(deftest test-format-tool-map
  (let [tool {:type "function" :function {:name "test"}}]
    (is (= tool (#'resp/format-tool tool)))))

(deftest test-format-tool-invalid
  (is (thrown? clojure.lang.ExceptionInfo
               (#'resp/format-tool "invalid"))))
