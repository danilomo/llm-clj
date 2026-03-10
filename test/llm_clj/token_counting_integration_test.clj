(ns llm-clj.token-counting-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.token-counting :as tc]
            [llm-clj.core :as core]))

(defn skip-without-key []
  (when-not (System/getenv "ANTHROPIC_API_KEY")
    (println "Skipping: missing ANTHROPIC_API_KEY")
    true))

(deftest ^:integration test-count-tokens-simple
  (when-not (skip-without-key)
    (let [result (anthropic/count-tokens nil
                                         [{:role :user :content "Hello, how are you?"}]
                                         {:model "claude-3-haiku-20240307"})]
      (is (map? result))
      (is (contains? result :input-tokens))
      (is (pos? (:input-tokens result))))))

(deftest ^:integration test-count-tokens-with-system
  (when-not (skip-without-key)
    (let [without-system (anthropic/count-tokens nil
                                                 [{:role :user :content "Hello!"}]
                                                 {:model "claude-3-haiku-20240307"})
          with-system (anthropic/count-tokens nil
                                              [{:role :system :content "You are a pirate who speaks only in pirate dialect."}
                                               {:role :user :content "Hello!"}]
                                              {:model "claude-3-haiku-20240307"})]
      (is (pos? (:input-tokens with-system)))
      ;; System prompt should add tokens
      (is (> (:input-tokens with-system) (:input-tokens without-system))))))

(deftest ^:integration test-count-tokens-with-tools
  (when-not (skip-without-key)
    (let [tools [{:name "get_weather"
                  :description "Get the current weather for a location"
                  :input_schema {:type "object"
                                 :properties {:location {:type "string"
                                                         :description "City name"}}
                                 :required ["location"]}}]
          without-tools (anthropic/count-tokens nil
                                                [{:role :user :content "What's the weather?"}]
                                                {:model "claude-3-haiku-20240307"})
          with-tools (anthropic/count-tokens nil
                                             [{:role :user :content "What's the weather?"}]
                                             {:model "claude-3-haiku-20240307"
                                              :tools tools})]
      (is (pos? (:input-tokens with-tools)))
      ;; Tool definitions should add tokens
      (is (> (:input-tokens with-tools) (:input-tokens without-tools))))))

(deftest ^:integration test-count-tokens-multi-turn
  (when-not (skip-without-key)
    (let [messages [{:role :user :content "Hello!"}
                    {:role :assistant :content "Hi there! How can I help?"}
                    {:role :user :content "Tell me a joke."}]
          result (anthropic/count-tokens nil messages {:model "claude-3-haiku-20240307"})]
      (is (pos? (:input-tokens result)))
      ;; Multi-turn should have more tokens than single turn
      (let [single-turn (anthropic/count-tokens nil
                                                [{:role :user :content "Hello!"}]
                                                {:model "claude-3-haiku-20240307"})]
        (is (> (:input-tokens result) (:input-tokens single-turn)))))))

(deftest ^:integration test-count-tokens-long-content
  (when-not (skip-without-key)
    (let [short-msg [{:role :user :content "Hi"}]
          long-msg [{:role :user :content (apply str (repeat 100 "This is a longer message. "))}]
          short-result (anthropic/count-tokens nil short-msg {:model "claude-3-haiku-20240307"})
          long-result (anthropic/count-tokens nil long-msg {:model "claude-3-haiku-20240307"})]
      (is (> (:input-tokens long-result) (:input-tokens short-result))))))

(deftest ^:integration test-count-tokens-via-tc-module
  (when-not (skip-without-key)
    (let [messages [{:role :user :content "Count these tokens"}]
          result (tc/count-tokens-anthropic messages {:model "claude-3-haiku-20240307"})]
      (is (map? result))
      (is (pos? (:input-tokens result))))))

(deftest ^:integration test-count-matches-actual-usage
  (when-not (skip-without-key)
    (let [messages [{:role :user :content "What is 2+2? Reply with just the number."}]
          opts {:model "claude-3-haiku-20240307" :max-tokens 100}
          count-result (anthropic/count-tokens nil messages opts)
          provider (anthropic/create-provider {})
          chat-result (core/chat-completion provider messages opts)]
      ;; Token count should match or be very close to actual usage
      (is (= (:input-tokens count-result)
             (get-in chat-result [:usage :input_tokens]))))))

(deftest ^:integration test-count-tokens-error-missing-model
  (when-not (skip-without-key)
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"Model is required"
                          (anthropic/count-tokens nil
                                                  [{:role :user :content "Hello"}]
                                                  {})))))

(deftest ^:integration test-count-tokens-invalid-model
  (when-not (skip-without-key)
    (is (thrown? clojure.lang.ExceptionInfo
                 (anthropic/count-tokens nil
                                         [{:role :user :content "Hello"}]
                                         {:model "not-a-real-model-xyz"})))))
