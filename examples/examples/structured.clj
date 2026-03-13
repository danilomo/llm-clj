(ns examples.structured
  (:require [llm-clj.core :as core]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.schema :as schema]
            [cheshire.core :as json]))

(def UserSchema
  [:map
   [:name :string]
   [:age :int]
   [:interests [:vector :string]]])

(defn -main [& _args]
  (println "=== Structured Output Example ===")

  (println "Schema definition in Malli:")
  (println UserSchema)

  (let [messages [{:role :system :content "You are a helpful assistant mapping user messages to JSON data."}
                  {:role :user :content "My name is Alice, I am 28 years old and I love hiking and reading."}]
        openai-prov (try (openai/create-provider {:model "gpt-4o"}) (catch Exception _ nil))
        anthropic-prov (try (anthropic/create-provider {:model "claude-3-haiku-20240307"}) (catch Exception _ nil))]

    ;; ========================================
    ;; NEW: Unified API (PydanticAI-style)
    ;; ========================================
    (println "\n========================================")
    (println "NEW: Unified chat-completion-structured API")
    (println "========================================")

    (when openai-prov
      (println "\n--- OpenAI with unified API ---")
      (let [result (schema/chat-completion-structured openai-prov messages UserSchema
                                                      {:name "User"
                                                       :description "Extracted user details"})]
        (println "Result:" result)))

    (when anthropic-prov
      (println "\n--- Anthropic with unified API ---")
      (let [result (schema/chat-completion-structured anthropic-prov messages UserSchema
                                                      {:name "User"
                                                       :description "Extracted user details"})]
        (println "Result:" result)))

    ;; ========================================
    ;; Legacy: Manual approach (still works)
    ;; ========================================
    (println "\n========================================")
    (println "Legacy: Manual low-level API")
    (println "========================================")

    (when openai-prov
      (println "\n--- Requesting strict JSON from OpenAI ---")
      (let [res (core/chat-completion openai-prov
                                      messages
                                      {:response-format (schema/structured-output-openai "User" UserSchema "Extracted user details")})
            raw-json (:content res)
            parsed (json/parse-string raw-json true)]

        (println "\nRaw Output:")
        (println raw-json)

        (println "\nValidated with Malli:")
        (println (schema/validate UserSchema parsed))))

    (when anthropic-prov
      (println "\n--- Requesting strict JSON from Anthropic (via Tool Calling) ---")
      (let [options (schema/structured-output-anthropic "User" UserSchema "Extracted user details")
            res (core/chat-completion anthropic-prov messages options)
            ;; Anthropic returns structured output as a tool call
            tool-call (first (:tool-calls res))
            raw-json (get-in tool-call [:function :arguments])
            parsed (json/parse-string raw-json true)]

        (println "\nRaw Output (inside tool call " (get-in tool-call [:function :name]) "):")
        (println raw-json)

        (println "\nValidated with Malli:")
        (println (schema/validate UserSchema parsed)))))

  (shutdown-agents))
