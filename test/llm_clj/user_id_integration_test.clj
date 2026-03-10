(ns llm-clj.user-id-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [clojure.java.io :as io]
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
  "Returns the value for key from env vars or system properties."
  [key]
  (or (System/getenv key) (System/getProperty key)))

(defn- load-dotenv! []
  (load-env! ".env")
  (load-env! (str (System/getProperty "user.dir") "/.env")))

;; ---------------------------------------------------------------------------
;; Provider factories (cheapest models)
;; ---------------------------------------------------------------------------

(defn- openai-provider []
  (let [key (env "OPENAI_API_KEY")]
    (when-not key (throw (ex-info "OPENAI_API_KEY not found" {})))
    (openai/create-provider {:api-key key :model "gpt-4o-mini"})))

(defn- anthropic-provider []
  (let [key (env "ANTHROPIC_API_KEY")]
    (when-not key (throw (ex-info "ANTHROPIC_API_KEY not found" {})))
    (anthropic/create-provider {:api-key key :model "claude-3-haiku-20240307"})))

;; ---------------------------------------------------------------------------
;; Fixture: load .env before the test suite runs
;; ---------------------------------------------------------------------------

(use-fixtures :once (fn [f] (load-dotenv!) (f)))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest openai-user-id-basic-test
  (testing "OpenAI: :user-id is accepted and a valid response is returned"
    (let [provider (openai-provider)
          messages [{:role :user :content "Say hello in one short sentence."}]
          result (core/chat-completion provider messages {:user-id "user_12345" :max-tokens 50})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (pos? (count (:content result))) "Response content should be non-empty")
      (is (keyword? (:finish-reason result)) "Response should have a finish-reason keyword"))))

(deftest openai-user-id-combined-options-test
  (testing "OpenAI: :user-id works alongside other sampling options"
    (let [provider (openai-provider)
          messages [{:role :user :content "What is 3 + 3? Reply with just the number."}]
          result (core/chat-completion provider messages {:user-id "user_abc"
                                                          :temperature 0.5
                                                          :max-tokens 10})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (str/includes? (:content result) "6")
          (str "Expected '6' in response, got: " (:content result))))))

(deftest anthropic-user-id-basic-test
  (testing "Anthropic: :user-id is accepted and a valid response is returned"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "Say hello in one short sentence."}]
          result (core/chat-completion provider messages {:user-id "user_12345" :max-tokens 50})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (pos? (count (:content result))) "Response content should be non-empty")
      (is (keyword? (:finish-reason result)) "Response should have a finish-reason keyword"))))

(deftest anthropic-user-id-combined-options-test
  (testing "Anthropic: :user-id works alongside other sampling options"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "What is 3 + 3? Reply with just the number."}]
          result (core/chat-completion provider messages {:user-id "user_abc"
                                                          :temperature 0.5
                                                          :max-tokens 10})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (str/includes? (:content result) "6")
          (str "Expected '6' in response, got: " (:content result))))))
