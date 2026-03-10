(ns llm-clj.responses-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.responses.openai :as resp]
            [llm-clj.responses.core :as responses]
            [clojure.core.async :as async]))

(defn skip-without-key []
  (when-not (System/getenv "OPENAI_API_KEY")
    (println "Skipping: missing OPENAI_API_KEY")
    true))

(deftest ^:integration test-simple-response
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          result (responses/create-response provider "Say hello" {:model "gpt-4o"})]
      (is (:id result))
      (is (:output result))
      (is (:usage result)))))

(deftest ^:integration test-streaming-response
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          ch (responses/create-response-stream provider "Say hi" {:model "gpt-4o"})
          events (loop [acc []]
                   (if-let [e (async/<!! ch)]
                     (recur (conj acc e))
                     acc))]
      (is (some #(= :delta (:type %)) events))
      (is (some #(= :complete (:type %)) events)))))

(deftest ^:integration test-multi-turn
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          r1 (responses/create-response provider "Remember: the secret word is banana"
                                        {:model "gpt-4o" :store true})
          r2 (responses/create-response provider "What was the secret word?"
                                        {:model "gpt-4o" :previous-response-id (:id r1)})]
      (is (re-find #"(?i)banana" (str (:output r2)))))))

(deftest ^:integration test-with-instructions
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          result (responses/create-response provider "Hello"
                                            {:model "gpt-4o"
                                             :instructions "You are a helpful pirate. Respond in pirate speak."})]
      (is (:id result))
      (is (:output result)))))

(deftest ^:integration test-get-response
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          created (responses/create-response provider "Test" {:model "gpt-4o" :store true})
          retrieved (responses/get-response provider (:id created))]
      (is (= (:id created) (:id retrieved))))))

(deftest ^:integration test-delete-response
  (when-not (skip-without-key)
    (let [provider (resp/create-provider {})
          created (responses/create-response provider "Test" {:model "gpt-4o" :store true})
          deleted (responses/delete-response provider (:id created))]
      (is (true? deleted)))))
