(ns llm-clj.stop-sequences-integration-test
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
  "Reads key=value pairs from a .env file and sets them as system properties
   so that (System/getenv ...) picks them up via the provider defaults."
  [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [line (str/split-lines (slurp f))]
        (let [line (str/trim line)]
          (when (and (seq line) (not (str/starts-with? line "#")))
            (let [[k v] (str/split line #"=" 2)]
              (when (and k v)
                ;; System/setProperty is visible to System/getenv on most JVMs
                ;; but we use an internal trick to inject into the env map.
                ;; Instead, store as system properties and adjust providers to
                ;; fall back to them, OR just export them here via reflection.
                (System/setProperty (str/trim k) (str/trim v))))))))))

(defn- env
  "Returns the value for key from env vars or system properties."
  [key]
  (or (System/getenv key) (System/getProperty key)))

(defn- load-dotenv! []
  (load-env! ".env")
  (load-env! (str (System/getProperty "user.dir") "/.env")))

;; ---------------------------------------------------------------------------
;; Provider factories that read from system properties as fallback
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

(deftest openai-stop-sequences-test
  (testing "OpenAI: model stops before emitting the stop sequence"
    (let [provider (openai-provider)
          messages [{:role :user
                     :content "Count from 1 to 10, one number per line."}]
          stop-seqs ["5"]
          result (core/chat-completion provider messages {:stop-sequences stop-seqs
                                                          :max-tokens 100})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")

      ;; The model should not have produced "5" in the output because it's a stop token
      (is (not (str/includes? (:content result) "5"))
          (str "Content should not contain stop sequence '5', got: " (:content result)))

      ;; Finish reason should reflect the stop sequence was hit
      (is (= :stop (:finish-reason result))
          (str "Expected finish-reason :stop, got: " (:finish-reason result))))))

(deftest anthropic-stop-sequences-test
  (testing "Anthropic: model stops before emitting the stop sequence"
    (let [provider (anthropic-provider)
          messages [{:role :user
                     :content "Count from 1 to 10, one number per line."}]
          stop-seqs ["5"]
          result (core/chat-completion provider messages {:stop-sequences stop-seqs
                                                          :max-tokens 100})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")

      ;; The model should not have produced "5" in the output
      (is (not (str/includes? (:content result) "5"))
          (str "Content should not contain stop sequence '5', got: " (:content result)))

      ;; Anthropic uses :end_turn or :stop_sequence as finish reason
      (is (= :stop_sequence (:finish-reason result))
          (str "Expected finish-reason :stop_sequence, got: " (:finish-reason result))))))

(deftest openai-multiple-stop-sequences-test
  (testing "OpenAI: respects the first stop sequence encountered among multiple"
    (let [provider (openai-provider)
          ;; Ask model to write lines ending with 4, 5, 6... stop before it writes "6"
          messages [{:role :user
                     :content "List the numbers 1 through 10, one per line, nothing else."}]
          stop-seqs ["6" "STOP"]
          result (core/chat-completion provider messages {:stop-sequences stop-seqs
                                                          :max-tokens 100})]

      (is (map? result))
      (is (string? (:content result)))

      ;; Model should not have emitted "6" or anything after it
      (is (not (str/includes? (:content result) "6"))
          (str "Content should not contain '6', got: " (:content result))))))

(deftest anthropic-multiple-stop-sequences-test
  (testing "Anthropic: respects the first stop sequence encountered among multiple"
    (let [provider (anthropic-provider)
          messages [{:role :user
                     :content "List the numbers 1 through 10, one per line, nothing else."}]
          stop-seqs ["6" "STOP"]
          result (core/chat-completion provider messages {:stop-sequences stop-seqs
                                                          :max-tokens 100})]

      (is (map? result))
      (is (string? (:content result)))

      (is (not (str/includes? (:content result) "6"))
          (str "Content should not contain '6', got: " (:content result))))))
