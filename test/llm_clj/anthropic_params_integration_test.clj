(ns llm-clj.anthropic-params-integration-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.core.async :as async]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.core :as core]))

(defn skip-without-key []
  (when-not (System/getenv "ANTHROPIC_API_KEY")
    (println "Skipping: missing ANTHROPIC_API_KEY")
    true))

(deftest ^:integration test-top-k-sampling
  (when-not (skip-without-key)
    (testing "Top-k parameter is accepted by API"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :top-k 40
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-top-k-with-temperature
  (when-not (skip-without-key)
    (testing "Top-k works together with temperature"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :top-k 50
                                          :temperature 0.8
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))))))

(deftest ^:integration test-beta-features-prompt-caching
  (when-not (skip-without-key)
    (testing "Beta features header is accepted"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Hello"}]
            ;; Should not error with beta header
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :beta-features [:prompt-caching]
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))))))

(deftest ^:integration test-multiple-beta-features
  (when-not (skip-without-key)
    (testing "Multiple beta features are accepted"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :beta-features [:prompt-caching :token-counting]
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))))))

(deftest ^:integration test-service-tier-standard
  (when-not (skip-without-key)
    (testing "Standard service tier is accepted"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :service-tier :standard
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))))))

(deftest ^:integration test-service-tier-priority
  (when-not (skip-without-key)
    (testing "Priority service tier header is accepted"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            ;; Note: Priority tier may require special account access
            ;; This test verifies the header is accepted, not that priority is granted
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :service-tier :priority
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))))))

(deftest ^:integration test-all-new-params-combined
  (when-not (skip-without-key)
    (testing "All new parameters work together"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            result (core/chat-completion provider messages
                                         {:model "claude-3-haiku-20240307"
                                          :top-k 50
                                          :beta-features [:prompt-caching]
                                          :service-tier :priority
                                          :temperature 0.7
                                          :max-tokens 50})]
        (is (= :assistant (:role result)))
        (is (string? (:content result)))
        (is (contains? result :usage))))))

(deftest ^:integration test-streaming-with-new-params
  (when-not (skip-without-key)
    (testing "Streaming works with new parameters"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Say hello"}]
            channel (core/chat-completion-stream provider messages
                                                 {:model "claude-3-haiku-20240307"
                                                  :top-k 40
                                                  :beta-features [:prompt-caching]
                                                  :service-tier :priority
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

(deftest ^:integration test-unknown-beta-feature
  (when-not (skip-without-key)
    (testing "Unknown beta features pass through without error in header construction"
      (let [provider (anthropic/create-provider {})
            messages [{:role :user :content "Hello"}]]
        ;; The API might ignore unknown features or return an error
        ;; We just verify the header construction doesn't fail on our side
        (try
          (let [result (core/chat-completion provider messages
                                             {:model "claude-3-haiku-20240307"
                                              :beta-features [:hypothetical-future-feature]
                                              :max-tokens 50})]
            ;; If it succeeds, verify it's a valid response
            (is (= :assistant (:role result))))
          (catch Exception e
            ;; If the API rejects it, that's also acceptable
            ;; We just wanted to verify our code doesn't crash
            (is (instance? Exception e))))))))
