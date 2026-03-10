(ns llm-clj.vision-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [llm-clj.core :as core]
            [llm-clj.vision :as vision]
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
;; Dummy image definition
;; ---------------------------------------------------------------------------
;; A small 1x1 transparent PNG pixel base64 encoded
(def tiny-png-b64 "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==")
(def tiny-png-media-type "image/png")

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest openai-vision-message-test
  (testing "OpenAI: sending a vision message correctly receives a response"
    (let [provider (openai-provider)
          messages [(vision/vision-message
                     [(vision/text-content "What do you see in this image? Just give a very short answer.")
                      (vision/image-base64 tiny-png-b64 tiny-png-media-type)])]
          result (core/chat-completion provider messages {:max-tokens 50})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (pos? (count (:content result))) "Response content should be non-empty")
      (is (keyword? (:finish-reason result)) "Response should have a finish-reason keyword"))))

(deftest anthropic-vision-message-test
  (testing "Anthropic: sending a vision message correctly receives a response"
    (let [provider (anthropic-provider)
          messages [(vision/vision-message
                     [(vision/text-content "What do you see in this image? Just give a very short answer.")
                      (vision/image-base64 tiny-png-b64 tiny-png-media-type)])]
          result (core/chat-completion provider messages {:max-tokens 50})]

      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (pos? (count (:content result))) "Response content should be non-empty")
      (is (keyword? (:finish-reason result)) "Response should have a finish-reason keyword"))))
