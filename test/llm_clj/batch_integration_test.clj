(ns llm-clj.batch-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.batch.core :as batch]
            [llm-clj.batch.openai :as batch-openai]
            [llm-clj.batch.anthropic :as batch-anthropic]))

(defn skip-without-api-key [key-env-var]
  (when-not (System/getenv key-env-var)
    (println "Skipping: missing" key-env-var)
    true))

;; OpenAI Integration Tests

(deftest ^:integration test-openai-create-batch
  (when-not (skip-without-api-key "OPENAI_API_KEY")
    (let [provider (batch-openai/create-provider {})
          requests [{:custom-id "test-1"
                     :messages [{:role "user" :content "Say hello"}]
                     :params {:model "gpt-4o-mini"}}]
          result (batch/create-batch provider requests {})]
      (is (string? (:id result)))
      (is (keyword? (:status result)))
      (is (some? (:created-at result)))
      (is (map? (:request-counts result)))

      ;; Clean up - cancel the batch since we don't want to wait 24h
      (try
        (batch/cancel-batch provider (:id result))
        (catch Exception _ nil)))))

(deftest ^:integration test-openai-list-batches
  (when-not (skip-without-api-key "OPENAI_API_KEY")
    (let [provider (batch-openai/create-provider {})
          result (batch/list-batches provider {:limit 5})]
      (is (vector? (:batches result)))
      (is (boolean? (:has-more result))))))

(deftest ^:integration test-openai-get-batch-not-found
  (when-not (skip-without-api-key "OPENAI_API_KEY")
    (let [provider (batch-openai/create-provider {})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (batch/get-batch provider "batch_nonexistent"))))))

;; Anthropic Integration Tests

(deftest ^:integration test-anthropic-create-batch
  (when-not (skip-without-api-key "ANTHROPIC_API_KEY")
    (let [provider (batch-anthropic/create-provider {})
          requests [{:custom-id "test-1"
                     :messages [{:role "user" :content "Say hello"}]
                     :params {:model "claude-3-haiku-20240307"}}]
          result (batch/create-batch provider requests {})]
      (is (string? (:id result)))
      (is (keyword? (:status result)))
      (is (some? (:created-at result)))
      (is (map? (:request-counts result)))

      ;; Clean up - cancel the batch
      (try
        (batch/cancel-batch provider (:id result))
        (catch Exception _ nil)))))

(deftest ^:integration test-anthropic-list-batches
  (when-not (skip-without-api-key "ANTHROPIC_API_KEY")
    (let [provider (batch-anthropic/create-provider {})
          result (batch/list-batches provider {:limit 5})]
      (is (vector? (:batches result)))
      (is (boolean? (:has-more result))))))

(deftest ^:integration test-anthropic-get-batch-not-found
  (when-not (skip-without-api-key "ANTHROPIC_API_KEY")
    (let [provider (batch-anthropic/create-provider {})]
      (is (thrown? clojure.lang.ExceptionInfo
                   (batch/get-batch provider "msgbatch_nonexistent"))))))
