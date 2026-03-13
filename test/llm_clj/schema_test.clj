(ns llm-clj.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.schema :refer [schema->json-schema structured-output-openai
                                    extract-structured-content]]))

(deftest test-schema->json-schema
  (testing "Converts malli schema to json schema map"
    (let [schema [:map [:name :string] [:age :int]]
          json-schema (schema->json-schema schema)]
      (is (= "object" (:type json-schema)))
      (is (= "string" (get-in json-schema [:properties :name :type])))
      (is (= "integer" (get-in json-schema [:properties :age :type]))))))

(deftest test-structured-output-openai
  (testing "Wraps JSON Schema for OpenAI"
    (let [schema [:map [:result :boolean]]
          wrapped (structured-output-openai "MyResult" schema "The result output")]
      (is (= "json_schema" (:type wrapped)))
      (is (= "MyResult" (get-in wrapped [:json_schema :name])))
      (is (= true (get-in wrapped [:json_schema :strict]))))))

(deftest test-extract-structured-content
  (testing "OpenAI: extracts content directly from response"
    (let [response {:role :assistant
                    :content "{\"name\":\"Alice\",\"age\":28}"
                    :finish-reason :stop}]
      (is (= "{\"name\":\"Alice\",\"age\":28}"
             (extract-structured-content :openai response)))))

  (testing "Anthropic: extracts from tool call arguments"
    (let [response {:role :assistant
                    :content ""
                    :tool-calls [{:id "call_123"
                                  :type "function"
                                  :function {:name "Response"
                                             :arguments "{\"name\":\"Bob\",\"age\":35}"}}]
                    :finish-reason :tool_use}]
      (is (= "{\"name\":\"Bob\",\"age\":35}"
             (extract-structured-content :anthropic response))))))
