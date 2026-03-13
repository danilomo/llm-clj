(ns llm-clj.tools
  (:require [llm-clj.schema :as schema]
            [llm-clj.core :as core]
            [llm-clj.tracing.core :as tracing]
            [llm-clj.tracing.span :as span]
            [llm-clj.tracing.config :as tracing-config]
            [clojure.walk :as walk]
            [cheshire.core :as json]))

(defn define-tool
  "Defines a tool for use by LLMs.
  Takes:
  - `name`: string identifier
  - `description`: string explaining what the tool does
  - `parameters`: a Malli schema defining the input arguments
  - `f`: a Clojure function `(fn [args] ...)` that executes the tool
  - `opts`: (optional) map with :strict key for schema validation"
  ([name description parameters f]
   (define-tool name description parameters f {}))
  ([name description parameters f opts]
   {:name name
    :description description
    :parameters parameters
    :f f
    :strict (:strict opts false)}))

(defn- enforce-additional-properties
  "Adds additionalProperties:false to every object node in a JSON Schema map.
  Required by OpenAI when strict mode is enabled."
  [schema-map]
  (walk/postwalk
   (fn [x]
     (if (and (map? x) (= "object" (:type x)))
       (assoc x :additionalProperties false)
       x))
   schema-map))

(defn format-tool-openai
  "Formats a defined tool into the OpenAI tools structure.
  When :strict is true, additionalProperties:false is added to all object nodes
  as required by the OpenAI API."
  [{:keys [name description parameters strict]}]
  (let [json-params (cond-> (schema/schema->json-schema parameters)
                      strict enforce-additional-properties)]
    {:type "function"
     :function (cond-> {:name name
                        :description description
                        :parameters json-params}
                 strict (assoc :strict true))}))

(defn format-tool-anthropic
  "Formats a defined tool into the Anthropic tools structure."
  [{:keys [name description parameters strict]}]
  (let [json-params (cond-> (schema/schema->json-schema parameters)
                      strict enforce-additional-properties)]
    (cond-> {:name name
             :description description
             :input_schema json-params}
      strict (assoc :strict true))))

(defn execute-tool-call
  "Given a list of available tools (as created by `define-tool`) and an llm `tool-call`,
  finds the requested tool, executes it, and returns a normalized message for the history."
  [available-tools tool-call]
  (let [tool-name (get-in tool-call [:function :name])
        args-json (get-in tool-call [:function :arguments])
        args (json/parse-string args-json true)
        tool (first (filter #(= tool-name (:name %)) available-tools))
        ;; Build attributes, optionally including args based on config
        base-attrs {span/attr-tool-name tool-name}
        attrs (if (tracing-config/capture-tool-args?)
                (assoc base-attrs span/attr-tool-arguments args-json)
                base-attrs)]
    (tracing/with-span [_ "llm.tool" attrs]
      (if tool
        (let [result ((:f tool) args)
              content (if (string? result) result (json/generate-string result))]
          (tracing/add-attributes {span/attr-tool-success true
                                   span/attr-tool-result-length (count content)})
          {:role :tool
           :tool-call-id (:id tool-call)
           :name tool-name
           :content content})
        (do
          (tracing/add-attributes {span/attr-tool-success false})
          {:role :tool
           :tool-call-id (:id tool-call)
           :name tool-name
           :content (str "Error: Tool " tool-name " not found.")})))))

(defn- execute-tool-call-safe
  "Like execute-tool-call but catches exceptions and returns error messages
   to the LLM instead of throwing."
  [available-tools tool-call]
  (try
    (execute-tool-call available-tools tool-call)
    (catch Exception e
      (let [tool-name (get-in tool-call [:function :name])]
        {:role :tool
         :tool-call-id (:id tool-call)
         :name tool-name
         :content (json/generate-string {:error (.getMessage e)})}))))

(defn- provider-type
  "Returns :openai or :anthropic based on the provider record type."
  [provider]
  (let [type-name (-> provider type .getSimpleName)]
    (cond
      (= type-name "OpenAIProvider") :openai
      (= type-name "AnthropicProvider") :anthropic
      :else (throw (ex-info (str "Unknown provider type: " type-name)
                            {:provider-type type-name})))))

(defn- format-tool-for-provider
  "Formats a tool definition for the given provider."
  [provider tool]
  (case (provider-type provider)
    :openai (format-tool-openai tool)
    :anthropic (format-tool-anthropic tool)))

(defn chat-with-tools
  "Performs chat completion with automatic tool execution.

   Calls chat-completion and automatically executes any tool calls requested
   by the model, continuing until the model returns a final response without
   tool calls.

   Arguments:
   - provider: An LLM provider (OpenAI or Anthropic)
   - messages: Initial message sequence
   - tools: Vector of tool definitions (as created by define-tool)
   - opts: Options map passed to chat-completion, plus:
     - :max-iterations - Safety limit for tool call loops (default 10)
     - :on-tool-call - Optional callback (fn [tool-call result]) for logging

   Returns the final response map from chat-completion (without tool-calls)."
  ([provider messages tools]
   (chat-with-tools provider messages tools {}))
  ([provider messages tools opts]
   (let [{:keys [max-iterations on-tool-call]
          :or {max-iterations 10}} opts
         chat-opts (dissoc opts :max-iterations :on-tool-call)
         formatted-tools (mapv #(format-tool-for-provider provider %) tools)]
     (loop [msgs (vec messages)
            iter 0]
       (if (>= iter max-iterations)
         (throw (ex-info "Max tool iterations exceeded"
                         {:max-iterations max-iterations
                          :iterations iter}))
         (let [response (tracing/with-span [_ "llm.completion"
                                            {span/attr-iteration iter
                                             span/attr-max-iterations max-iterations}]
                          (let [resp (core/chat-completion provider msgs
                                                           (assoc chat-opts :tools formatted-tools))]
                            ;; Add usage info to span
                            (when-let [usage (:usage resp)]
                              (tracing/add-attributes
                               {span/attr-usage-input-tokens (:input-tokens usage)
                                span/attr-usage-output-tokens (:output-tokens usage)
                                span/attr-usage-total-tokens (:total-tokens usage)}))
                            (tracing/add-attribute span/attr-llm-finish-reason
                                                   (name (or (:finish-reason resp) :unknown)))
                            resp))]
           (if-not (seq (:tool-calls response))
             response
             (let [tool-results (mapv #(execute-tool-call-safe tools %) (:tool-calls response))
                   _ (when on-tool-call
                       (doseq [[tc result] (map vector (:tool-calls response) tool-results)]
                         (on-tool-call tc result)))
                   assistant-msg {:role :assistant
                                  :content (:content response)
                                  :tool-calls (:tool-calls response)}
                   new-msgs (into msgs (cons assistant-msg tool-results))]
               (recur new-msgs (inc iter))))))))))
