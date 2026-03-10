(ns llm-clj.documents-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [llm-clj.documents :as docs]
            [llm-clj.providers.anthropic :as anthropic]))

(deftest test-pdf-url
  (let [block (docs/pdf-url "https://example.com/doc.pdf")]
    (is (= :document (:type block)))
    (is (= :url (get-in block [:source :type])))
    (is (= "https://example.com/doc.pdf" (get-in block [:source :url])))))

(deftest test-pdf-url-with-cache
  (let [block (docs/pdf-url "https://example.com/doc.pdf"
                            {:cache-control {:type "ephemeral"}})]
    (is (= {:type "ephemeral"} (:cache_control block)))))

(deftest test-pdf-base64
  (let [block (docs/pdf-base64 "JVBERi0...")]
    (is (= :document (:type block)))
    (is (= :base64 (get-in block [:source :type])))
    (is (= "application/pdf" (get-in block [:source :media_type])))
    (is (= "JVBERi0..." (get-in block [:source :data])))))

(deftest test-pdf-base64-with-cache
  (let [block (docs/pdf-base64 "JVBERi0..."
                               {:cache-control {:type "ephemeral"}})]
    (is (= {:type "ephemeral"} (:cache_control block)))))

(deftest test-text-block
  (let [block (docs/text "Hello")]
    (is (= :text (:type block)))
    (is (= "Hello" (:text block)))))

(deftest test-text-block-with-cache
  (let [block (docs/text "Hello" {:cache-control {:type "ephemeral"}})]
    (is (= :text (:type block)))
    (is (= "Hello" (:text block)))
    (is (= {:type "ephemeral"} (:cache_control block)))))

(deftest test-document-message
  (let [msg (docs/document-message
             [(docs/pdf-url "https://example.com/doc.pdf")
              (docs/text "Summarize")])]
    (is (= :user (:role msg)))
    (is (= 2 (count (:content msg))))
    (is (= :document (get-in msg [:content 0 :type])))
    (is (= :text (get-in msg [:content 1 :type])))))

(deftest test-pdf-file-not-found
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"not found"
                        (docs/pdf-file "/nonexistent/file.pdf"))))

(deftest test-pdf-file-wrong-extension
  ;; Create a temp file with wrong extension
  (let [temp (java.io.File/createTempFile "test" ".txt")]
    (try
      (is (thrown-with-msg? clojure.lang.ExceptionInfo
                            #"must be a PDF"
                            (docs/pdf-file (.getPath temp))))
      (finally
        (.delete temp)))))

(deftest test-pdf-file-success
  ;; Create a temp PDF file
  (let [temp (java.io.File/createTempFile "test" ".pdf")]
    (try
      ;; Write some content to simulate a PDF
      (spit temp "%PDF-1.4 test content")
      (let [block (docs/pdf-file (.getPath temp))]
        (is (= :document (:type block)))
        (is (= :base64 (get-in block [:source :type])))
        (is (= "application/pdf" (get-in block [:source :media_type])))
        (is (string? (get-in block [:source :data]))))
      (finally
        (.delete temp)))))

(deftest test-pdf-file-with-cache
  (let [temp (java.io.File/createTempFile "test" ".pdf")]
    (try
      (spit temp "%PDF-1.4 test content")
      (let [block (docs/pdf-file (.getPath temp) {:cache-control {:type "ephemeral"}})]
        (is (= {:type "ephemeral"} (:cache_control block))))
      (finally
        (.delete temp)))))

(deftest test-summarize-pdf-prompt-file
  (let [temp (java.io.File/createTempFile "test" ".pdf")]
    (try
      (spit temp "%PDF-1.4 test content")
      (let [msg (docs/summarize-pdf-prompt (.getPath temp))]
        (is (= :user (:role msg)))
        (is (= 2 (count (:content msg))))
        (is (= :document (get-in msg [:content 0 :type])))
        (is (= :text (get-in msg [:content 1 :type])))
        (is (str/includes? (get-in msg [:content 1 :text]) "summary")))
      (finally
        (.delete temp)))))

(deftest test-summarize-pdf-prompt-url
  (let [msg (docs/summarize-pdf-prompt "https://example.com/doc.pdf" :url)]
    (is (= :user (:role msg)))
    (is (= 2 (count (:content msg))))
    (is (= :document (get-in msg [:content 0 :type])))
    (is (= :url (get-in msg [:content 0 :source :type])))
    (is (= "https://example.com/doc.pdf" (get-in msg [:content 0 :source :url])))))

(deftest test-summarize-pdf-prompt-base64
  (let [msg (docs/summarize-pdf-prompt "JVBERi0..." :base64)]
    (is (= :user (:role msg)))
    (is (= 2 (count (:content msg))))
    (is (= :document (get-in msg [:content 0 :type])))
    (is (= :base64 (get-in msg [:content 0 :source :type])))))

(deftest test-format-content-part-document-url
  (let [block (docs/pdf-url "https://example.com/doc.pdf")
        formatted (docs/format-content-part block)]
    (is (= "document" (:type formatted)))
    (is (= "url" (get-in formatted [:source :type])))
    (is (= "https://example.com/doc.pdf" (get-in formatted [:source :url])))))

(deftest test-format-content-part-document-base64
  (let [block (docs/pdf-base64 "JVBERi0...")
        formatted (docs/format-content-part block)]
    (is (= "document" (:type formatted)))
    (is (= "base64" (get-in formatted [:source :type])))
    (is (= "application/pdf" (get-in formatted [:source :media_type])))
    (is (= "JVBERi0..." (get-in formatted [:source :data])))))

(deftest test-format-content-part-text
  (let [block (docs/text "Hello")
        formatted (docs/format-content-part block)]
    (is (= "text" (:type formatted)))
    (is (= "Hello" (:text formatted)))))

(deftest test-format-content-part-with-cache-control
  (let [block (docs/pdf-url "https://example.com/doc.pdf"
                            {:cache-control {:type "ephemeral"}})
        formatted (docs/format-content-part block)]
    (is (= {:type "ephemeral"} (:cache_control formatted)))))

(deftest test-format-content-part-passthrough
  ;; Already formatted parts should pass through unchanged
  (let [part {:type "image" :source {:type "base64" :data "..."}}
        formatted (docs/format-content-part part)]
    (is (= part formatted))))

;; Tests for auto-detection functions (using provider internals)
(deftest test-detect-document-content
  (let [detect-fn #'anthropic/detect-document-content]
    ;; Message with document content
    (is (true? (detect-fn [{:role :user
                            :content [(docs/pdf-url "https://example.com/doc.pdf")
                                      (docs/text "Summarize")]}])))
    ;; Message without document content
    (is (nil? (detect-fn [{:role :user :content "Hello"}])))
    ;; Message with only text blocks
    (is (nil? (detect-fn [{:role :user
                           :content [(docs/text "Hello")]}])))
    ;; Multiple messages, one with document
    (is (true? (detect-fn [{:role :user :content "Hello"}
                           {:role :assistant :content "Hi there!"}
                           {:role :user
                            :content [(docs/pdf-url "https://example.com/doc.pdf")
                                      (docs/text "Summarize")]}])))))

(deftest test-ensure-pdfs-beta
  (let [ensure-fn #'anthropic/ensure-pdfs-beta
        messages-with-doc [{:role :user
                            :content [(docs/pdf-url "https://example.com/doc.pdf")
                                      (docs/text "Summarize")]}]
        messages-without-doc [{:role :user :content "Hello"}]]
    ;; Should add :pdfs when documents present and not already included
    (let [result (ensure-fn messages-with-doc {})]
      (is (some #{:pdfs} (:beta-features result))))
    ;; Should not duplicate :pdfs if already present
    (let [result (ensure-fn messages-with-doc {:beta-features [:pdfs]})]
      (is (= 1 (count (filter #{:pdfs} (:beta-features result))))))
    ;; Should preserve existing beta features
    (let [result (ensure-fn messages-with-doc {:beta-features [:prompt-caching]})]
      (is (some #{:pdfs} (:beta-features result)))
      (is (some #{:prompt-caching} (:beta-features result))))
    ;; Should not add :pdfs when no documents
    (let [result (ensure-fn messages-without-doc {})]
      (is (nil? (:beta-features result))))))
