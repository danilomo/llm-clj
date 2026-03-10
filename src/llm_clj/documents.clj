(ns llm-clj.documents
  "Document and PDF helpers for LLM conversations.

  Usage:
  (require '[llm-clj.documents :as docs])

  ;; From URL
  (docs/pdf-url \"https://example.com/doc.pdf\")

  ;; From local file
  (docs/pdf-file \"/path/to/document.pdf\")

  ;; In a message
  {:role :user
   :content [(docs/pdf-file \"report.pdf\")
             (docs/text \"Summarize this document\")]}"
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [llm-clj.errors :as errors])
  (:import [java.util Base64]
           [java.nio.file Files]))

;; Document content block types

(defn pdf-url
  "Creates a document content block from a URL.

  Options:
  - :cache-control - Cache control settings for prompt caching
                     e.g., {:type \"ephemeral\"}

  Usage:
  (pdf-url \"https://example.com/doc.pdf\")
  (pdf-url \"https://example.com/doc.pdf\" {:cache-control {:type \"ephemeral\"}})"
  ([url] (pdf-url url {}))
  ([url {:keys [cache-control]}]
   (cond-> {:type :document
            :source {:type :url
                     :url url}}
     cache-control (assoc :cache_control cache-control))))

(defn pdf-base64
  "Creates a document content block from base64-encoded data.

  Usage:
  (pdf-base64 \"JVBERi0xLjQK...\")"
  ([base64-data] (pdf-base64 base64-data {}))
  ([base64-data {:keys [cache-control]}]
   (cond-> {:type :document
            :source {:type :base64
                     :media_type "application/pdf"
                     :data base64-data}}
     cache-control (assoc :cache_control cache-control))))

(defn pdf-file
  "Creates a document content block from a local file path.
  Reads the file and encodes it as base64.

  Usage:
  (pdf-file \"/path/to/document.pdf\")
  (pdf-file \"/path/to/document.pdf\" {:cache-control {:type \"ephemeral\"}})"
  ([file-path] (pdf-file file-path {}))
  ([file-path opts]
   (let [file (io/file file-path)]
     (when-not (.exists file)
       (throw (errors/validation-error
               "PDF file not found"
               {:path file-path})))
     (when-not (str/ends-with?
                (str/lower-case file-path) ".pdf")
       (throw (errors/validation-error
               "File must be a PDF"
               {:path file-path})))
     (let [bytes (Files/readAllBytes (.toPath file))
           base64 (.encodeToString (Base64/getEncoder) bytes)]
       (pdf-base64 base64 opts)))))

(defn text
  "Creates a text content block for multi-part messages.
  Use alongside document blocks.

  Usage:
  (text \"Please summarize this document\")"
  ([content] (text content {}))
  ([content {:keys [cache-control]}]
   (cond-> {:type :text
            :text content}
     cache-control (assoc :cache_control cache-control))))

;; Document message helpers

(defn document-message
  "Creates a user message containing documents and text.

  Usage:
  (document-message
    [(pdf-file \"report.pdf\")
     (text \"Summarize the key findings\")])"
  [content-parts]
  {:role :user
   :content (vec content-parts)})

(defn summarize-pdf-prompt
  "Creates a complete message for summarizing a PDF.

  Usage:
  (summarize-pdf-prompt \"/path/to/doc.pdf\")
  (summarize-pdf-prompt \"https://example.com/doc.pdf\" :url)"
  ([source] (summarize-pdf-prompt source :file))
  ([source source-type]
   (let [doc-block (case source-type
                     :file (pdf-file source)
                     :url (pdf-url source)
                     :base64 (pdf-base64 source))]
     (document-message
      [doc-block
       (text "Please provide a comprehensive summary of this document.")]))))

;; Format conversion for Anthropic API

(defn- format-document-source
  "Formats document source for Anthropic API."
  [source]
  (case (:type source)
    :url {:type "url" :url (:url source)}
    :base64 {:type "base64"
             :media_type (:media_type source)
             :data (:data source)}
    source))

(defn format-content-part
  "Formats a document content part for the Anthropic API.
  Used internally by the Anthropic provider."
  [part]
  (case (:type part)
    :document (cond-> {:type "document"
                       :source (format-document-source (:source part))}
                (:cache_control part) (assoc :cache_control (:cache_control part)))
    :text (cond-> {:type "text"
                   :text (:text part)}
            (:cache_control part) (assoc :cache_control (:cache_control part)))
    ;; Pass through already-formatted parts
    part))

;; Utility functions

(defn pdf-page-count
  "Attempts to get the page count from a PDF file.
  Returns nil if unable to determine.

  Note: This is a simple heuristic, not a full PDF parser."
  [file-path]
  (try
    (let [content (slurp file-path)]
      ;; Simple heuristic: count /Type /Page occurrences
      (count (re-seq #"/Type\s*/Page[^s]" content)))
    (catch Exception _ nil)))

(defn estimated-tokens
  "Provides a rough estimate of tokens for a PDF.
  Based on Anthropic's guidance: ~1500 tokens per page.

  Returns nil if page count cannot be determined."
  [file-path]
  (when-let [pages (pdf-page-count file-path)]
    (* pages 1500)))
