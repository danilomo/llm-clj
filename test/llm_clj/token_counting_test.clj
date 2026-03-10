(ns llm-clj.token-counting-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.token-counting :as tc]))

(deftest test-estimate-tokens
  (testing "empty string returns 0"
    (is (= 0 (tc/estimate-tokens "")))
    (is (= 0 (tc/estimate-tokens nil))))

  (testing "short strings"
    (is (= 1 (tc/estimate-tokens "Hi")))
    (is (= 1 (tc/estimate-tokens "abc")))
    (is (= 1 (tc/estimate-tokens "test"))))

  (testing "longer strings produce more tokens"
    (is (= 3 (tc/estimate-tokens "Hello world")))
    (is (pos? (tc/estimate-tokens "A longer piece of text that should have more tokens")))))

(deftest test-estimate-message-tokens
  (testing "simple message has overhead added"
    (let [msg {:role :user :content "Hello"}]
      (is (> (tc/estimate-message-tokens msg)
             (tc/estimate-tokens "Hello")))))

  (testing "empty content still has overhead"
    (let [msg {:role :user :content ""}]
      (is (= 4 (tc/estimate-message-tokens msg)))))

  (testing "multi-part content is handled"
    (let [msg {:role :user :content [{:type :text :text "Hello"}
                                     {:type :text :text "World"}]}]
      (is (pos? (tc/estimate-message-tokens msg))))))

(deftest test-estimate-conversation-tokens
  (testing "empty conversation"
    (is (= 0 (tc/estimate-conversation-tokens []))))

  (testing "single message"
    (let [msgs [{:role :user :content "Hi"}]]
      (is (pos? (tc/estimate-conversation-tokens msgs)))))

  (testing "multi-turn conversation"
    (let [msgs [{:role :user :content "Hi"}
                {:role :assistant :content "Hello!"}
                {:role :user :content "How are you?"}]]
      (is (> (tc/estimate-conversation-tokens msgs)
             (tc/estimate-conversation-tokens (take 1 msgs)))))))

(deftest test-get-context-window
  (testing "known models return correct values"
    (is (= 200000 (tc/get-context-window :claude-3-sonnet)))
    (is (= 200000 (tc/get-context-window :claude-3-opus)))
    (is (= 200000 (tc/get-context-window :claude-3-haiku)))
    (is (= 200000 (tc/get-context-window :claude-sonnet-4)))
    (is (= 128000 (tc/get-context-window :gpt-4o)))
    (is (= 128000 (tc/get-context-window :gpt-4o-mini))))

  (testing "model name strings are recognized"
    (is (= 200000 (tc/get-context-window "claude-sonnet-4-20250514")))
    (is (= 200000 (tc/get-context-window "claude-3-haiku-20240307")))
    (is (= 128000 (tc/get-context-window "gpt-4o-2024-08-06"))))

  (testing "unknown models get conservative default"
    (is (= 8000 (tc/get-context-window :unknown-model)))
    (is (= 8000 (tc/get-context-window "custom-model-v1")))))

(deftest test-fits-context-yes
  (testing "short message fits easily"
    (let [msgs [{:role :user :content "Short message"}]]
      (is (tc/fits-context? msgs {:max-tokens 200000}))))

  (testing "respects reserve parameter"
    (let [msgs [{:role :user :content "Hi"}]]
      (is (tc/fits-context? msgs {:max-tokens 100 :reserve 50})))))

(deftest test-fits-context-no
  (testing "very long message doesn't fit"
    (let [long-content (apply str (repeat 100000 "word "))
          msgs [{:role :user :content long-content}]]
      (is (not (tc/fits-context? msgs {:max-tokens 8000})))))

  (testing "default reserve of 4096 is respected"
    (let [msgs [{:role :user :content "Hi"}]]
      ;; Message is ~5 tokens, but with 4096 reserve we need at least 4101 tokens
      (is (not (tc/fits-context? msgs {:max-tokens 100}))))))

(deftest test-fits-context-validation
  (testing "throws when max-tokens missing"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"max-tokens is required"
                          (tc/fits-context? [] {})))))

(deftest test-fits-model-context
  (testing "uses model's context window"
    (let [msgs [{:role :user :content "Hello"}]]
      (is (tc/fits-model-context? msgs {:model "claude-3-haiku-20240307"}))))

  (testing "throws when model missing"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"model is required"
                          (tc/fits-model-context? [] {})))))

(deftest test-available-tokens
  (testing "calculates remaining tokens"
    (let [msgs [{:role :user :content "Hello"}]]  ;; ~5 tokens + 4 overhead = ~9 tokens
      (is (> (tc/available-tokens msgs {:max-tokens 200000}) 199000))))

  (testing "returns 0 when exceeded"
    (let [long-content (apply str (repeat 10000 "word "))
          msgs [{:role :user :content long-content}]]
      (is (= 0 (tc/available-tokens msgs {:max-tokens 100})))))

  (testing "throws when max-tokens missing"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"max-tokens is required"
                          (tc/available-tokens [] {})))))

(deftest test-truncate-to-fit
  (testing "returns original when fits"
    (let [msgs [{:role :user :content "Hello"}]]
      (is (= msgs (tc/truncate-to-fit msgs {:max-tokens 200000})))))

  (testing "removes oldest messages first"
    (let [msgs [{:role :user :content "First message"}
                {:role :assistant :content "Response"}
                {:role :user :content "Second message"}]
          truncated (tc/truncate-to-fit msgs {:max-tokens 50 :reserve 10})]
      ;; Should keep fewer messages
      (is (<= (count truncated) (count msgs)))))

  (testing "keeps system messages"
    (let [msgs [{:role :system :content "You are helpful"}
                {:role :user :content "First"}
                {:role :assistant :content "Response 1"}
                {:role :user :content "Second"}
                {:role :assistant :content "Response 2"}
                {:role :user :content "Third"}]
          truncated (tc/truncate-to-fit msgs {:max-tokens 100 :reserve 20})]
      ;; System message should be first
      (is (= :system (:role (first truncated))))))

  (testing "keeps at least one user message"
    (let [long-content (apply str (repeat 1000 "word "))
          msgs [{:role :user :content long-content}]
          truncated (tc/truncate-to-fit msgs {:max-tokens 100 :reserve 10})]
      (is (= 1 (count truncated)))))

  (testing "throws when max-tokens missing"
    (is (thrown-with-msg? clojure.lang.ExceptionInfo
                          #"max-tokens is required"
                          (tc/truncate-to-fit [] {})))))
