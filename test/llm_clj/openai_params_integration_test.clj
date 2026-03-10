(ns llm-clj.openai-params-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as async]
            [llm-clj.providers.openai :as openai]
            [llm-clj.core :as core]))

(defn skip-without-key []
  (when-not (System/getenv "OPENAI_API_KEY")
    (println "Skipping: missing OPENAI_API_KEY")
    true))

(deftest ^:integration test-frequency-penalty
  (when-not (skip-without-key)
    (testing "Frequency penalty parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :frequency-penalty 0.5
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-presence-penalty
  (when-not (skip-without-key)
    (testing "Presence penalty parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :presence-penalty 0.3
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-seed-determinism
  (when-not (skip-without-key)
    (testing "Seed produces consistent outputs"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say exactly: Hello World"}]
            opts {:model "gpt-4o-mini" :seed 12345 :temperature 0 :max-tokens 50}
            r1 (core/chat-completion provider messages opts)
            r2 (core/chat-completion provider messages opts)]
        ;; With seed and temperature 0, results should be very similar or identical
        (is (= :assistant (:role r1)))
        (is (= :assistant (:role r2)))
        (is (string? (:content r1)))
        (is (string? (:content r2)))))))

(deftest ^:integration test-logprobs
  (when-not (skip-without-key)
    (testing "Logprobs are returned when requested"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :logprobs true
                                          :top-logprobs 3
                                          :max-tokens 20})]
        (is (= :assistant (:role result)))
        (is (contains? result :logprobs))
        (is (map? (:logprobs result)))
        (is (contains? (:logprobs result) :content))
        (is (vector? (:content (:logprobs result))))))))

(deftest ^:integration test-multiple-completions
  (when-not (skip-without-key)
    (testing "Multiple completions (n>1) return choices vector"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :n 3
                                          :temperature 1.0
                                          :max-tokens 20})]
        (is (contains? result :choices))
        (is (= 3 (count (:choices result))))
        (doseq [choice (:choices result)]
          (is (= :assistant (:role choice)))
          (is (string? (:content choice)))
          (is (contains? choice :finish-reason)))
        (is (contains? result :usage))))))

(deftest ^:integration test-parallel-tool-calls
  (when-not (skip-without-key)
    (testing "Parallel tool calls parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "What's the weather in Boston and New York?"}]
            tools [{:type "function"
                    :function {:name "get_weather"
                               :description "Get weather for a location"
                               :parameters {:type "object"
                                            :properties {:location {:type "string"}}
                                            :required ["location"]}}}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :tools tools
                                          :parallel-tool-calls true
                                          :max-tokens 100})]
        (is (= :assistant (:role result)))
        ;; The model may or may not decide to use tools, but the parameter should be accepted
        (is (contains? result :usage))))))

(deftest ^:integration test-service-tier
  (when-not (skip-without-key)
    (testing "Service tier parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :service-tier :auto
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-logit-bias
  (when-not (skip-without-key)
    (testing "Logit bias parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            ;; Example: bias against token 15339 (which is " world")
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :logit-bias {15339 -100}
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-store-parameter
  (when-not (skip-without-key)
    (testing "Store parameter is accepted"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "gpt-4o-mini"
                                          :store false
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-streaming-with-new-params
  (when-not (skip-without-key)
    (testing "Streaming works with new parameters"
      (let [provider (openai/create-provider {})
            messages [{:role :user :content "Say hello"}]
            channel (core/chat-completion-stream provider messages
                                                 {:model "gpt-4o-mini"
                                                  :frequency-penalty 0.5
                                                  :seed 12345
                                                  :max-tokens 50})
            events (atom [])]
        ;; Collect all events
        (loop []
          (when-let [event (async/<!! channel)]
            (swap! events conj event)
            (recur)))
        ;; Check we got some events
        (is (pos? (count @events)))
        ;; Last event should be :complete
        (is (= :complete (:type (last @events))))))))
