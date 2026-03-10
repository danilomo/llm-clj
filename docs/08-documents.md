# Documents and PDF Support

Anthropic's Claude models can understand and analyze PDF documents directly. The `llm-clj.documents` namespace provides helpers for including PDFs in your conversations.

## Overview

Document support allows you to:
- Upload PDFs from local files or URLs
- Ask questions about document content
- Compare multiple documents
- Extract specific information
- Use prompt caching for large documents

**Note**: Document support is currently an Anthropic-only feature and requires the `:pdfs` beta feature (which is automatically enabled when documents are detected).

## Basic Concepts

### Document Content Blocks

Documents are included as content blocks within messages, similar to images:

```clojure
{:role :user
 :content [{:type :document :source {...}}
           {:type :text :text "Summarize this document"}]}
```

The `llm-clj.documents` namespace provides helpers to create these blocks easily.

### Beta Feature Requirement

PDF support requires the `:pdfs` beta feature. The library automatically detects documents in your messages and adds this feature, but you can also specify it explicitly:

```clojure
{:beta-features [:pdfs]}
```

## Creating Document Blocks

### From a URL

```clojure
(require '[llm-clj.documents :as docs])

;; Simple URL
(docs/pdf-url "https://example.com/report.pdf")

;; With prompt caching enabled
(docs/pdf-url "https://example.com/report.pdf"
              {:cache-control {:type "ephemeral"}})
```

### From a Local File

```clojure
;; From file path
(docs/pdf-file "/path/to/document.pdf")

;; With cache control
(docs/pdf-file "/path/to/document.pdf"
               {:cache-control {:type "ephemeral"}})
```

### From Base64 Data

```clojure
;; If you already have base64-encoded PDF data
(docs/pdf-base64 "JVBERi0xLjQK...")

;; With cache control
(docs/pdf-base64 base64-data {:cache-control {:type "ephemeral"}})
```

### Text Blocks

Use `text` to create text content alongside documents:

```clojure
(docs/text "Summarize the key findings")

;; With cache control
(docs/text "Summarize the key findings"
           {:cache-control {:type "ephemeral"}})
```

## Building Document Messages

### Using `document-message`

The `document-message` helper creates a complete user message:

```clojure
(docs/document-message
  [(docs/pdf-file "/path/to/report.pdf")
   (docs/text "What are the main conclusions?")])

;; => {:role :user
;;     :content [{:type :document :source {...}}
;;               {:type :text :text "What are the main conclusions?"}]}
```

### Using `summarize-pdf-prompt`

A convenience function for quick summarization:

```clojure
;; From file
(docs/summarize-pdf-prompt "/path/to/doc.pdf")

;; From URL
(docs/summarize-pdf-prompt "https://example.com/doc.pdf" :url)

;; From base64
(docs/summarize-pdf-prompt base64-data :base64)
```

## REPL Examples

### Summarizing a PDF

Copy and paste this entire block:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])

(def provider (anthropic/create-provider {}))

;; Summarize a PDF from URL
(defn summarize-pdf [pdf-url]
  (llm/chat-completion provider
    [(docs/document-message
       [(docs/pdf-url pdf-url)
        (docs/text "Please provide a concise summary of this document, highlighting the key points.")])]
    {:model "claude-sonnet-4-20250514"
     :max-tokens 1000}))

;; Example with a public PDF
(def result
  (summarize-pdf "https://www.w3.org/WAI/WCAG21/Techniques/pdf/img/table-word.pdf"))

(println (:content result))
```

### Asking Questions About a Document

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])

(def provider (anthropic/create-provider {}))

(defn ask-about-pdf [pdf-path question]
  (llm/chat-completion provider
    [(docs/document-message
       [(docs/pdf-file pdf-path)
        (docs/text question)])]
    {:model "claude-sonnet-4-20250514"
     :max-tokens 500}))

;; Usage
(ask-about-pdf "/path/to/contract.pdf"
               "What is the termination clause in this contract?")
```

### Comparing Two Documents

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])

(def provider (anthropic/create-provider {}))

(defn compare-documents [pdf1 pdf2 comparison-request]
  (llm/chat-completion provider
    [(docs/document-message
       [(docs/pdf-file pdf1)
        (docs/pdf-file pdf2)
        (docs/text comparison-request)])]
    {:model "claude-sonnet-4-20250514"
     :max-tokens 2000}))

;; Compare annual reports
(compare-documents
  "/path/to/report-2023.pdf"
  "/path/to/report-2024.pdf"
  "Compare these two annual reports. What are the key changes in revenue, strategy, and outlook?")
```

### Multi-turn Document Conversation

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])

(def provider (anthropic/create-provider {}))

(defn document-conversation [pdf-path]
  (let [history (atom [])]

    ;; First message includes the document
    (defn start-conversation! [initial-question]
      (reset! history
              [(docs/document-message
                 [(docs/pdf-file pdf-path {:cache-control {:type "ephemeral"}})
                  (docs/text initial-question)])])
      (let [response (llm/chat-completion provider @history
                       {:model "claude-sonnet-4-20250514"
                        :max-tokens 1000
                        :beta-features [:pdfs :prompt-caching]})]
        (swap! history conj {:role :assistant :content (:content response)})
        (:content response)))

    ;; Follow-up questions (document is cached)
    (defn ask! [question]
      (swap! history conj {:role :user :content question})
      (let [response (llm/chat-completion provider @history
                       {:model "claude-sonnet-4-20250514"
                        :max-tokens 1000
                        :beta-features [:pdfs :prompt-caching]})]
        (swap! history conj {:role :assistant :content (:content response)})
        (:content response)))

    {:start start-conversation!
     :ask ask!
     :history (fn [] @history)}))

;; Usage
(def conv (document-conversation "/path/to/research-paper.pdf"))

((:start conv) "What is the main thesis of this paper?")
;; => "The main thesis is..."

((:ask conv) "What methodology did they use?")
;; => "The researchers employed..."

((:ask conv) "What are the limitations mentioned?")
;; => "The paper acknowledges several limitations..."
```

## Prompt Caching for Large Documents

When working with large PDFs or asking multiple questions about the same document, use prompt caching to improve performance and reduce costs:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])

(def provider (anthropic/create-provider {}))

;; First request - document is cached
(def response1
  (llm/chat-completion provider
    [(docs/document-message
       [(docs/pdf-file "/path/to/large-doc.pdf"
                       {:cache-control {:type "ephemeral"}})
        (docs/text "What is the executive summary?")])]
    {:model "claude-sonnet-4-20250514"
     :beta-features [:pdfs :prompt-caching]
     :max-tokens 500}))

;; Second request - uses cached document (faster & cheaper)
(def response2
  (llm/chat-completion provider
    [(docs/document-message
       [(docs/pdf-file "/path/to/large-doc.pdf"
                       {:cache-control {:type "ephemeral"}})
        (docs/text "What are the financial projections?")])]
    {:model "claude-sonnet-4-20250514"
     :beta-features [:pdfs :prompt-caching]
     :max-tokens 500}))
```

## Token Estimation

Large PDFs can consume many tokens. Use these utilities to estimate usage:

```clojure
(require '[llm-clj.documents :as docs])

;; Get page count (heuristic)
(docs/pdf-page-count "/path/to/document.pdf")
;; => 15

;; Estimate tokens (~1500 per page)
(docs/estimated-tokens "/path/to/document.pdf")
;; => 22500

;; Check before sending
(defn check-pdf-size [pdf-path max-tokens]
  (if-let [estimated (docs/estimated-tokens pdf-path)]
    (if (> estimated max-tokens)
      (throw (ex-info "PDF too large"
                      {:estimated-tokens estimated
                       :max-tokens max-tokens}))
      {:ok true :estimated-tokens estimated})
    {:ok true :estimated-tokens :unknown}))

(check-pdf-size "/path/to/document.pdf" 100000)
```

## Error Handling

The library provides specific error handling for document operations:

```clojure
(require '[llm-clj.documents :as docs])
(require '[llm-clj.errors :as errors])

;; File not found
(try
  (docs/pdf-file "/nonexistent/file.pdf")
  (catch Exception e
    (when (errors/validation-error? e)
      (println "File not found:" (:path (ex-data e))))))

;; Wrong file type
(try
  (docs/pdf-file "/path/to/document.txt")  ; Not a PDF
  (catch Exception e
    (when (errors/validation-error? e)
      (println "Must be a PDF file"))))
```

## Document Analysis Application

Here's a complete example of a document analysis application:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])
(require '[llm-clj.tools :as tools])
(require '[llm-clj.errors :as errors])
(require '[cheshire.core :as json])

(def provider (anthropic/create-provider {}))

;; Tool to extract structured information
(def extract-info-tool
  (tools/define-tool
    "extract_document_info"
    "Extracts structured information from the document"
    [:map
     [:title :string]
     [:author {:optional true} :string]
     [:date {:optional true} :string]
     [:document-type [:enum "report" "contract" "article" "other"]]
     [:key-topics [:vector :string]]
     [:summary :string]]
    (fn [info]
      (println "Extracted:" info)
      {:recorded true})
    {:strict true}))

(defn analyze-document [pdf-source & {:keys [source-type]
                                       :or {source-type :file}}]
  ;; Validate and create document block
  (let [doc-block (case source-type
                    :file (do
                            (when-let [tokens (docs/estimated-tokens pdf-source)]
                              (println "Estimated tokens:" tokens))
                            (docs/pdf-file pdf-source))
                    :url (docs/pdf-url pdf-source)
                    :base64 (docs/pdf-base64 pdf-source))]

    ;; Send to Claude for analysis
    (let [response (llm/chat-completion provider
                     [{:role :system
                       :content "You are a document analyst. Analyze documents thoroughly and extract structured information using the provided tool."}
                      (docs/document-message
                        [doc-block
                         (docs/text "Analyze this document. Extract the title, author (if available), date (if available), document type, key topics, and provide a brief summary. Use the extract_document_info tool to record your findings.")])]
                     {:model "claude-sonnet-4-20250514"
                      :max-tokens 2000
                      :tools [(tools/format-tool-anthropic extract-info-tool)]})]

      ;; Process tool call if present
      (if-let [tool-calls (:tool-calls response)]
        (let [tool-call (first tool-calls)
              extracted (json/parse-string
                          (get-in tool-call [:function :arguments])
                          true)]
          {:success true
           :analysis extracted
           :raw-content (:content response)})
        {:success true
         :analysis nil
         :raw-content (:content response)}))))

;; Usage
(analyze-document "/path/to/report.pdf")
;; => {:success true
;;     :analysis {:title "Q4 2024 Financial Report"
;;                :author "Finance Department"
;;                :date "January 2025"
;;                :document-type "report"
;;                :key-topics ["revenue" "expenses" "projections"]
;;                :summary "Quarterly financial report showing..."}
;;     :raw-content "..."}

;; From URL
(analyze-document "https://example.com/whitepaper.pdf"
                  :source-type :url)
```

## Batch Document Processing

Process multiple documents efficiently:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.documents :as docs])
(require '[clojure.java.io :as io])

(def provider (anthropic/create-provider {}))

(defn process-pdf-batch [pdf-dir output-fn]
  (let [pdf-files (->> (io/file pdf-dir)
                       (.listFiles)
                       (filter #(.endsWith (.getName %) ".pdf")))]
    (doseq [pdf-file pdf-files]
      (println "Processing:" (.getName pdf-file))
      (try
        (let [response (llm/chat-completion provider
                         [(docs/summarize-pdf-prompt (.getPath pdf-file))]
                         {:model "claude-sonnet-4-20250514"
                          :max-tokens 500})]
          (output-fn {:file (.getName pdf-file)
                      :summary (:content response)
                      :status :success}))
        (catch Exception e
          (output-fn {:file (.getName pdf-file)
                      :error (ex-message e)
                      :status :error}))))))

;; Usage
(def results (atom []))

(process-pdf-batch "/path/to/pdf/folder"
                   #(swap! results conj %))

;; Review results
@results
```

## Content Block Structure Reference

### Document Block (Internal)

```clojure
{:type :document
 :source {:type :url :url "https://..."}
 ;; or
 :source {:type :base64
          :media_type "application/pdf"
          :data "JVBERi0..."}
 :cache_control {:type "ephemeral"}}  ; optional
```

### Text Block (Internal)

```clojure
{:type :text
 :text "Your question here"
 :cache_control {:type "ephemeral"}}  ; optional
```

### Formatted for API (Internal)

The library automatically converts to Anthropic's API format:

```clojure
;; URL-based document
{:type "document"
 :source {:type "url" :url "https://..."}}

;; Base64 document
{:type "document"
 :source {:type "base64"
          :media_type "application/pdf"
          :data "JVBERi0..."}}
```

## Best Practices

1. **Use prompt caching** for large documents or multi-turn conversations
2. **Estimate tokens** before sending large PDFs to avoid context limit issues
3. **Handle errors** gracefully - files may be missing or corrupt
4. **Use specific questions** rather than open-ended "analyze this" prompts
5. **Consider document size** - very large PDFs may need to be split
6. **Validate file types** - the library checks for .pdf extension

## Limitations

- **Anthropic only**: PDF support is not available with OpenAI (use their Assistants API instead)
- **Beta feature**: May have changing behavior
- **Token costs**: PDFs can consume significant tokens (~1500 per page)
- **Page count heuristic**: The `pdf-page-count` function is approximate

