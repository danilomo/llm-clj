(ns llm-clj.server-tools-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.server-tools :as st]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.core :as core]))

(defn has-api-key? []
  (boolean (System/getenv "ANTHROPIC_API_KEY")))

(deftest ^:integration test-web-search
  (if-not (has-api-key?)
    (println "Skipping test-web-search: missing ANTHROPIC_API_KEY")
    (let [provider (anthropic/create-provider {})
          result (core/chat-completion provider
                                       [{:role :user :content "What is the current weather in San Francisco?"}]
                                       {:model "claude-sonnet-4-20250514"
                                        :tools [(st/web-search)]
                                        :max-tokens 500})]
      (is (:content result)))))

(deftest ^:integration test-web-search-with-domain-restrictions
  (if-not (has-api-key?)
    (println "Skipping test-web-search-with-domain-restrictions: missing ANTHROPIC_API_KEY")
    (let [provider (anthropic/create-provider {})
          result (core/chat-completion provider
                                       [{:role :user :content "Find information about the Python programming language"}]
                                       {:model "claude-sonnet-4-20250514"
                                        :tools [(st/web-search {:allowed-domains ["python.org" "wikipedia.org"]})]
                                        :max-tokens 500})]
      (is (:content result)))))

(deftest ^:integration test-code-execution
  (if-not (has-api-key?)
    (println "Skipping test-code-execution: missing ANTHROPIC_API_KEY")
    (let [provider (anthropic/create-provider {})
          tools [(st/code-execution)]
          opts (st/with-required-betas tools {:model "claude-sonnet-4-20250514"
                                              :max-tokens 500})
          result (core/chat-completion provider
                                       [{:role :user :content "Calculate 2^100 using Python"}]
                                       (assoc opts :tools tools))]
      (is (:content result)))))

(deftest ^:integration test-multiple-server-tools
  (if-not (has-api-key?)
    (println "Skipping test-multiple-server-tools: missing ANTHROPIC_API_KEY")
    (let [provider (anthropic/create-provider {})
          tools [(st/web-search) (st/code-execution)]
          opts (st/with-required-betas tools {:model "claude-sonnet-4-20250514"
                                              :max-tokens 800})
          result (core/chat-completion provider
                                       [{:role :user :content "Search for today's stock price of AAPL and calculate what 100 shares would be worth"}]
                                       (assoc opts :tools tools))]
      (is (:content result)))))
