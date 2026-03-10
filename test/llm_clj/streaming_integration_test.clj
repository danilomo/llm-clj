(ns llm-clj.streaming-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.core.async :as async :refer [<!!]]
            [llm-clj.core :as core]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]))

;; ---------------------------------------------------------------------------
;; .env loader
;; ---------------------------------------------------------------------------

(defn- load-env!
  "Reads key=value pairs from a .env file and sets them as system properties."
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [line (str/split-lines (slurp f))]
        (let [line (str/trim line)]
          (when (and (seq line) (not (str/starts-with? line "#")))
            (let [[k v] (str/split line #"=" 2)]
              (when (and k v)
                (System/setProperty (str/trim k) (str/trim v))))))))))

(defn- env
  [key]
  (or (System/getenv key) (System/getProperty key)))

(defn- load-dotenv! []
  (load-env! ".env")
  (load-env! (str (System/getProperty "user.dir") "/.env")))

;; ---------------------------------------------------------------------------
;; Provider factories
;; ---------------------------------------------------------------------------

(defn- openai-provider []
  (let [key (env "OPENAI_API_KEY")]
    (when-not key (throw (ex-info "OPENAI_API_KEY not found" {})))
    (openai/create-provider {:api-key key :model "gpt-4o-mini"})))

(defn- anthropic-provider []
  (let [key (env "ANTHROPIC_API_KEY")]
    (when-not key (throw (ex-info "ANTHROPIC_API_KEY not found" {})))
    (anthropic/create-provider {:api-key key :model "claude-3-haiku-20240307"})))

;; Fixture
(use-fixtures :once (fn [f] (load-dotenv!) (f)))

;; ---------------------------------------------------------------------------
;; Helper
;; ---------------------------------------------------------------------------

(defn- consume-stream [ch]
  (loop [events []]
    (let [event (<!! ch)]
      (if (or (nil? event) (= (:type event) :complete) (= (:type event) :error))
        (conj events event)
        (recur (conj events event))))))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest openai-streaming-test
  (testing "OpenAI streaming core.async channel"
    (let [provider (openai-provider)
          messages [{:role "user" :content "Count from 1 to 5, briefly."}]
          ch (core/chat-completion-stream provider messages {:max-tokens 50})
          events (consume-stream ch)
          complete-event (last events)
          deltas (filter #(= (:type %) :delta) events)]

      (is (seq deltas) "There should be at least one content delta chunk")
      (is (not-empty (:content complete-event)) "The complete event should have final content")
      (is (= :complete (:type complete-event)) "The last event should be :complete")
      (is (keyword? (:finish-reason complete-event)) "The finish reason should be parsed as keyword"))))

(deftest anthropic-streaming-test
  (testing "Anthropic streaming core.async channel"
    (let [provider (anthropic-provider)
          messages [{:role "user" :content "Count from 1 to 5, briefly."}]
          ch (core/chat-completion-stream provider messages {:max-tokens 50})
          events (consume-stream ch)
          complete-event (last events)
          deltas (filter #(= (:type %) :delta) events)]

      (is (seq deltas) "There should be at least one content delta chunk")
      (is (not-empty (:content complete-event)) "The complete event should have final content")
      (is (= :complete (:type complete-event)) "The last event should be :complete")
      (is (keyword? (:finish-reason complete-event)) "The finish reason should be parsed as keyword"))))
