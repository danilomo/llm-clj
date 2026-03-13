(ns llm-clj.schema
  (:require [malli.json-schema :as json-schema]
            [malli.core :as m]
            [clojure.walk :as walk]
            [cheshire.core :as json]
            [llm-clj.core :as core]))

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

(defn- provider-type
  "Determines the provider type for a given provider instance.
  Uses type name matching to avoid circular dependencies with provider namespaces."
  [provider]
  (let [type-name (-> provider type .getName)]
    (cond
      (= type-name "llm_clj.providers.openai.OpenAIProvider") :openai
      (= type-name "llm_clj.providers.anthropic.AnthropicProvider") :anthropic
      :else (throw (ex-info "Unknown provider type" {:provider (type provider)
                                                     :type-name type-name})))))

(defn extract-structured-content
  "Extracts the JSON string from a provider response based on provider type.
  - OpenAI: Returns the content directly (structured output in response)
  - Anthropic: Extracts from tool call arguments (structured via tool calling)"
  [provider-type response]
  (case provider-type
    :openai (:content response)
    :anthropic (-> response :tool-calls first :function :arguments)))

(defn chat-completion-structured
  "High-level API for structured outputs that works across providers.

  Given a provider, messages, and a Malli schema, returns validated Clojure data.
  Automatically handles the different mechanisms used by each provider:
  - OpenAI: Uses response_format with json_schema
  - Anthropic: Uses tool calling workaround

  Arguments:
  - provider: An LLM provider instance (OpenAI or Anthropic)
  - messages: Vector of message maps
  - schema: A Malli schema defining the expected output structure
  - opts: Optional map with:
    - :name - Schema name (default: \"Response\")
    - :description - Schema description (optional)
    - :validate? - Whether to validate against schema (default: true)
    - Any other options are passed through to chat-completion

  Returns validated Clojure data matching the schema.

  Example:
  (chat-completion-structured provider
    [{:role :user :content \"My name is Alice, I'm 28\"}]
    [:map [:name :string] [:age :int]])
  ;; => {:name \"Alice\" :age 28}"
  [provider messages schema & [{:keys [name description validate?]
                                :or {name "Response" validate? true}
                                :as opts}]]
  (let [ptype (provider-type provider)
        ;; Remove our custom keys before passing to chat-completion
        passthrough-opts (dissoc opts :name :description :validate?)
        provider-opts (case ptype
                        :openai {:response-format (structured-output-openai name schema description)}
                        :anthropic (structured-output-anthropic name schema description))
        ;; Merge passthrough opts with provider-specific opts
        all-opts (merge passthrough-opts provider-opts)
        response (core/chat-completion provider messages all-opts)
        json-str (extract-structured-content ptype response)
        data (json/parse-string json-str true)]
    (if validate?
      (validate schema data)
      data)))
