(ns llm-clj.api
  "Unified API for LLM interactions.

   This namespace provides a single `chat` function that transparently handles:
   - Simple completions
   - Tool calling with automatic execution
   - Structured outputs with Malli schemas
   - Combined tools + structured outputs

   This is the recommended entry point for most use cases."
  (:require [llm-clj.core :as core]
            [llm-clj.tools :as tools]
            [llm-clj.schema :as schema]
            [llm-clj.tracing.core :as tracing]
            [llm-clj.tracing.span :as span]
            [cheshire.core :as json]))

(defn- provider-type
  "Returns :openai or :anthropic based on the provider record type."
  [provider]
  (let [type-name (-> provider type .getSimpleName)]
    (cond
      (= type-name "OpenAIProvider") :openai
      (= type-name "AnthropicProvider") :anthropic
      :else (throw (ex-info (str "Unknown provider type: " type-name)
                            {:provider-type type-name})))))

(defn- create-schema-pseudo-tool
  "Creates a pseudo-tool definition for the response schema.
   Used for Anthropic which implements structured output via tool calling."
  [schema name description]
  {:name name
   :description (or description (str "Use this tool to output your final response in the " name " format"))
   :parameters schema
   :f nil
   :strict true})

(defn- is-schema-tool-call?
  "Returns true if the tool call is for the schema pseudo-tool."
  [tool-call schema-name]
  (= (get-in tool-call [:function :name]) schema-name))

(defn- execute-tool-call-safe
  "Executes a tool call, catching exceptions and returning error messages."
  [available-tools tool-call]
  (try
    (tools/execute-tool-call available-tools tool-call)
    (catch Exception e
      (let [tool-name (get-in tool-call [:function :name])]
        {:role :tool
         :tool-call-id (:id tool-call)
         :name tool-name
         :content (json/generate-string {:error (.getMessage e)})}))))

(defn- chat-openai-tools-and-schema
  "Handles OpenAI with both tools and structured output.
   OpenAI supports response_format alongside tools natively."
  [provider messages tools opts
   {:keys [response-schema schema-name schema-description validate?]}]
  (let [response-format (schema/structured-output-openai schema-name response-schema schema-description)
        all-opts (assoc opts :response-format response-format)
        response (tools/chat-with-tools provider messages tools all-opts)
        json-str (:content response)
        data (json/parse-string json-str true)]
    (if validate?
      (schema/validate response-schema data)
      data)))

(defn- chat-anthropic-tools-and-schema
  "Handles Anthropic with both tools and structured output.
   Anthropic uses tool calling for structured output, so we add the schema
   as a pseudo-tool and handle it specially in the execution loop."
  [provider messages tools opts
   {:keys [response-schema schema-name schema-description validate?]}]
  (let [{:keys [max-iterations on-tool-call]
         :or {max-iterations 10}} opts
        chat-opts (dissoc opts :max-iterations :on-tool-call :tools
                          :response-schema :schema-name :schema-description :validate?)

        ;; Create pseudo-tool for schema output
        schema-tool (create-schema-pseudo-tool response-schema schema-name schema-description)
        all-tools (conj (vec tools) schema-tool)
        formatted-tools (mapv tools/format-tool-anthropic all-tools)]

    (loop [msgs (vec messages)
           iter 0]
      (if (>= iter max-iterations)
        (throw (ex-info "Max tool iterations exceeded"
                        {:max-iterations max-iterations
                         :iterations iter}))
        (let [response (core/chat-completion provider msgs
                                             (assoc chat-opts :tools formatted-tools))]
          (if-not (seq (:tool-calls response))
            ;; No tool calls - model wants to respond with text
            ;; Force the schema tool call for structured output
            (let [force-opts (merge chat-opts
                                    (schema/structured-output-anthropic
                                     schema-name response-schema schema-description))
                  force-response (core/chat-completion provider msgs force-opts)
                  json-str (-> force-response :tool-calls first :function :arguments)
                  data (json/parse-string json-str true)]
              (if validate?
                (schema/validate response-schema data)
                data))

            ;; Tool calls present - check for schema tool
            (let [schema-call (first (filter #(is-schema-tool-call? % schema-name)
                                             (:tool-calls response)))
                  real-tool-calls (remove #(is-schema-tool-call? % schema-name)
                                          (:tool-calls response))]
              (if schema-call
                ;; Schema tool called - extract and return structured data
                (let [json-str (get-in schema-call [:function :arguments])
                      data (json/parse-string json-str true)]
                  (if validate?
                    (schema/validate response-schema data)
                    data))

                ;; Only real tools called - execute and continue
                (let [tool-results (mapv #(execute-tool-call-safe tools %) real-tool-calls)
                      _ (when on-tool-call
                          (doseq [[tc result] (map vector real-tool-calls tool-results)]
                            (on-tool-call tc result)))
                      assistant-msg {:role :assistant
                                     :content (:content response)
                                     :tool-calls (:tool-calls response)}
                      new-msgs (into msgs (cons assistant-msg tool-results))]
                  (recur new-msgs (inc iter)))))))))))

(defn chat
  "Unified chat function that handles tools and structured outputs transparently.

   This is the recommended entry point for most LLM interactions. It automatically
   handles the complexity of combining tools and structured outputs across
   different providers (OpenAI and Anthropic).

   Arguments:
   - provider: An LLM provider instance (OpenAI or Anthropic)
   - messages: Vector of message maps
   - opts: Optional map with:
     - :tools - Vector of tool definitions (from define-tool)
     - :response-schema - Malli schema for structured output
     - :schema-name - Name for the schema (default: \"Response\")
     - :schema-description - Description for the schema
     - :validate? - Validate structured output (default: true)
     - :max-iterations - Max tool call rounds (default: 10)
     - :on-tool-call - Callback (fn [tool-call result]) for logging
     - Other options passed to chat-completion (model, temperature, etc.)

   Returns:
   - If response-schema is provided: validated Clojure data matching the schema
   - Otherwise: standard response map from chat-completion

   Examples:

   ;; Simple completion
   (chat provider [{:role :user :content \"Hello\"}])
   ;; => {:role :assistant :content \"Hi there!\" :usage {...}}

   ;; With tools
   (chat provider messages {:tools [weather-tool]})
   ;; => {:role :assistant :content \"The weather in Tokyo is...\" :usage {...}}

   ;; With structured output
   (chat provider messages {:response-schema PersonSchema})
   ;; => {:name \"Alice\" :age 30}

   ;; Both tools and structured output
   (chat provider messages {:tools [search-tool]
                            :response-schema SummarySchema})
   ;; => {:title \"...\" :summary \"...\" :sources [...]}"
  ([provider messages]
   (chat provider messages {}))
  ([provider messages opts]
   (let [{:keys [tools response-schema schema-name schema-description validate?]
          :or {schema-name "Response" validate? true}} opts
         has-tools? (boolean (seq tools))
         has-schema? (some? response-schema)
         ptype (provider-type provider)]
     (tracing/with-span [_ "llm.chat"
                         {span/attr-llm-provider (name ptype)
                          span/attr-llm-model (:model opts)
                          span/attr-llm-has-tools has-tools?
                          span/attr-llm-has-schema has-schema?
                          span/attr-llm-message-count (count messages)}]
       (let [;; Clean opts for pass-through
             passthrough-opts (dissoc opts :tools :response-schema :schema-name
                                      :schema-description :validate?)]
         (cond
           ;; Case 1: Neither tools nor schema - simple completion
           (and (not has-tools?) (not has-schema?))
           (core/chat-completion provider messages passthrough-opts)

           ;; Case 2: Schema only - use chat-completion-structured
           (and (not has-tools?) has-schema?)
           (schema/chat-completion-structured provider messages response-schema
                                              (assoc passthrough-opts
                                                     :name schema-name
                                                     :description schema-description
                                                     :validate? validate?))

           ;; Case 3: Tools only - use chat-with-tools
           (and has-tools? (not has-schema?))
           (tools/chat-with-tools provider messages tools opts)

           ;; Case 4: Both tools and schema - provider-specific handling
           :else
           (let [schema-opts {:response-schema response-schema
                              :schema-name schema-name
                              :schema-description schema-description
                              :validate? validate?}]
             (case ptype
               :openai (chat-openai-tools-and-schema provider messages tools opts schema-opts)
               :anthropic (chat-anthropic-tools-and-schema provider messages tools opts schema-opts)))))))))
