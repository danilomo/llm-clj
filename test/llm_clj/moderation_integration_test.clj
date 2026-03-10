(ns llm-clj.moderation-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.moderation.openai :as mod]))

(defn skip-without-key []
  (when-not (System/getenv "OPENAI_API_KEY")
    (println "Skipping: missing OPENAI_API_KEY")
    true))

(deftest ^:integration test-safe-content
  (or (skip-without-key)
      (let [result (mod/moderate "Hello, how are you today?")]
        (is (false? (:flagged result)))
        (is (map? (:categories result)))
        (is (map? (:category-scores result))))))

(deftest ^:integration test-batch-moderation
  (or (skip-without-key)
      (let [result (mod/moderate ["Hello" "How are you?" "Nice weather"])]
        (is (= 3 (count (:results result))))
        (is (every? #(false? (:flagged %)) (:results result))))))

(deftest ^:integration test-category-scores-present
  (or (skip-without-key)
      (let [result (mod/moderate "A normal message")]
        (is (contains? (:category-scores result) :hate))
        (is (contains? (:category-scores result) :violence))
        (is (number? (:hate (:category-scores result)))))))

(deftest ^:integration test-moderate-messages
  (or (skip-without-key)
      (let [messages [{:role :user :content "Hello"}
                      {:role :assistant :content "Hi there!"}]
            result (mod/moderate-messages messages)]
        (is (= 2 (count result)))
        (is (every? :moderation result)))))
