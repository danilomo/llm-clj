(ns examples.tools
  (:require [llm-clj.core :as core]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.tools :as tools]))

(defn get-weather [{:keys [location unit]}]
  (println "[Tool Execution] Fetching weather for" location "in unit" unit)
  ;; Dummy implementation
  {:temperature (if (= unit "celsius") 22 72)
   :condition "Sunny"})

(def weather-tool
  (tools/define-tool
    "get_weather"
    "Get the current weather in a given location"
    [:map
     [:location {:description "The city and state, e.g. San Francisco, CA"} :string]
     [:unit {:description "The unit of temperature to return", :optional true} [:enum "celsius" "fahrenheit"]]]
    get-weather))

(defn run-tools-example [prov config-fn]
  (let [messages [{:role :user :content "What's the weather like in Paris?"}]
        tool-config (map config-fn [weather-tool])]
    (when prov
      (println "\nUser asks:" (-> messages first :content))
      (println "\n1. Sending request with tools...")
      (let [res1 (core/chat-completion prov messages {:tools tool-config})]
        (if-let [tool-calls (seq (:tool-calls res1))]
          (do
            (println "\nModel responded with tool calls:" tool-calls)
            ;; Execute tools and build next message history
            (let [tool-responses (map (partial tools/execute-tool-call [weather-tool]) tool-calls)
                  next-messages (concat messages [res1] tool-responses)]
              (println "\n2. Tool executed. Sending result back to model...")
              (let [res2 (core/chat-completion prov next-messages {:tools tool-config})]
                (println "\nFinal response:" (:content res2)))))
          (println "Model did not choose to use tools. Response:" (:content res1)))))))

(defn -main [& _args]
  (println "=== Tool Calling Example ===")

  (println "\n--- Testing OpenAI ---")
  (let [openai-prov (try (openai/create-provider {:model "gpt-4o"}) (catch Exception _ nil))]
    (run-tools-example openai-prov tools/format-tool-openai))

  (println "\n--- Testing Anthropic ---")
  (let [anthropic-prov (try (anthropic/create-provider {:model "claude-3-haiku-20240307"}) (catch Exception _ nil))]
    (run-tools-example anthropic-prov tools/format-tool-anthropic))

  (shutdown-agents))
