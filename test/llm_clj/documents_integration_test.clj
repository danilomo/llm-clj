(ns llm-clj.documents-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [llm-clj.documents :as docs]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.core :as core]))

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
;; Provider factory
;; ---------------------------------------------------------------------------

(defn- anthropic-provider []
  (let [key (env "ANTHROPIC_API_KEY")]
    (when-not key (throw (ex-info "ANTHROPIC_API_KEY not found" {})))
    (anthropic/create-provider {:api-key key :model "claude-sonnet-4-20250514"})))

;; Fixture
(use-fixtures :once (fn [f] (load-dotenv!) (f)))

;; ---------------------------------------------------------------------------
;; Tests
;; ---------------------------------------------------------------------------

(deftest ^:integration test-pdf-url
  (testing "Anthropic: sending a PDF from URL correctly receives a response"
    (let [provider (anthropic-provider)
          ;; Use a publicly available PDF (hosted on GitHub Pages)
          pdf-url "https://pdfobject.com/pdf/sample.pdf"
          result (core/chat-completion provider
                                       [(docs/document-message
                                         [(docs/pdf-url pdf-url)
                                          (docs/text "What is this document about? Answer in one sentence.")])]
                                       {:model "claude-sonnet-4-20250514"
                                        :beta-features [:pdfs]
                                        :max-tokens 500})]
      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content")
      (is (pos? (count (:content result))) "Response content should be non-empty"))))

(deftest ^:integration test-pdf-url-auto-beta
  (testing "Anthropic: auto-detects documents and adds pdfs beta feature"
    (let [provider (anthropic-provider)
          pdf-url "https://pdfobject.com/pdf/sample.pdf"
          ;; Note: not explicitly passing :beta-features [:pdfs]
          result (core/chat-completion provider
                                       [(docs/document-message
                                         [(docs/pdf-url pdf-url)
                                          (docs/text "What is this document about? Answer in one sentence.")])]
                                       {:model "claude-sonnet-4-20250514"
                                        :max-tokens 500})]
      (is (map? result) "Response should be a map")
      (is (string? (:content result)) "Response should have string content"))))

;; Test with a local PDF file (if available)
(deftest ^:integration test-pdf-file
  (when-let [test-pdf (env "TEST_PDF_PATH")]
    (testing "Anthropic: sending a PDF from local file correctly receives a response"
      (let [provider (anthropic-provider)
            result (core/chat-completion provider
                                         [(docs/summarize-pdf-prompt test-pdf)]
                                         {:model "claude-sonnet-4-20250514"
                                          :beta-features [:pdfs]
                                          :max-tokens 500})]
        (is (map? result) "Response should be a map")
        (is (string? (:content result)) "Response should have string content")
        (is (pos? (count (:content result))) "Response content should be non-empty")))))
