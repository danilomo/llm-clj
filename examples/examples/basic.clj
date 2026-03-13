(ns examples.basic
  (:require [llm-clj.core :as core]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]))

;; Note: You must have OPENAI_API_KEY and ANTHROPIC_API_KEY set in your environment

(defn -main [& _args]
  (println "=== Basic Chat Example ===")

  (let [messages [{:role :system :content "You are a poetic assistant. Answer with a haiku."}
                  {:role :user :content "Describe the ocean."}]]

    (println "\n--- Calling OpenAI (GPT-4o) ---")
    (try
      (let [openai-prov (openai/create-provider {:model "gpt-4o"})
            res (core/chat-completion openai-prov messages {:max-tokens 50})]
        (println "Response:")
        (println (:content res))
        (println "(Tokens used:" (:usage res) ")"))
      (catch Exception _e (println "OpenAI failed (ensure OPENAI_API_KEY is set).")))

    (println "\n--- Calling Anthropic (Claude-3-haiku) ---")
    (try
      (let [anthropic-prov (anthropic/create-provider {:model "claude-3-haiku-20240307"})
            res (core/chat-completion anthropic-prov messages {:max-tokens 50})]
        (println "Response:")
        (println (:content res))
        (println "(Tokens used:" (:usage res) ")"))
      (catch Exception _e (println "Anthropic failed (ensure ANTHROPIC_API_KEY is set)."))))

  (shutdown-agents))

(-main)
