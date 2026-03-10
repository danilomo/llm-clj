(ns llm-clj.schema
  (:require [malli.json-schema :as json-schema]
            [malli.core :as m]
            [clojure.walk :as walk]))

(defn- enforce-strict-object [schema-map]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (= "object" (:type x)))
       (assoc x :additionalProperties false)
       x))
   schema-map))

(defn schema->json-schema
  "Converts a Malli schema into a JSON Schema map suitable for LLM tools."
  [schema]
  (json-schema/transform schema))

(defn structured-output-openai
  "Creates the `response_format` configuration for OpenAI to enforce a specific schema."
  [name schema description]
  {:type "json_schema"
   :json_schema
   {:name name
    :description (or description (str "Output schema for " name))
    :schema (enforce-strict-object (schema->json-schema schema))
    :strict true}})

(defn structured-output-anthropic
  "Creates the `tools` and `tool_choice` configuration for Anthropic to enforce a specific schema."
  [name schema description]
  {:tools [{:name name
            :description (or description (str "Output schema for " name))
            :input_schema (enforce-strict-object (schema->json-schema schema))}]
   :tool-choice {:type "tool" :name name}})

(defn validate
  "Validates data against a schema, throwing an exception containing explanation if invalid."
  [schema data]
  (if (m/validate schema data)
    data
    (throw (ex-info "Validation failed"
                    {:errors (m/explain schema data)
                     :data data}))))
