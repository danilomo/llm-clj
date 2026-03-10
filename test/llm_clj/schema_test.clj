(ns llm-clj.schema-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.schema :refer [schema->json-schema structured-output-openai]]))

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
