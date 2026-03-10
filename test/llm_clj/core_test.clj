(ns llm-clj.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.core :refer [normalize-message]]))

(deftest test-normalize-message
  (testing "Normalizes string roles to keywords"
    (is (= {:role :user, :content "Hello"}
           (normalize-message {:role "user" :content "Hello"}))))

  (testing "Preserves tool call arguments"
    (is (= {:role :assistant
            :tool-calls [{:id "call_123" :type "function" :function {:name "my_tool" :arguments "{}"}}]}
           (normalize-message {:role "assistant"
                               :tool-calls [{:id "call_123" :type "function" :function {:name "my_tool" :arguments "{}"}}]})))))

(deftest test-extract-system-prompt
  (testing "Extracts system prompts and joins them"
    (let [msgs [{:role :system :content "You are a helpful assistant."}
                {:role :user :content "Hi!"}
                {:role :system :content "Always be kind."}]]
      (is (= ["You are a helpful assistant.\n\nAlways be kind."
              [{:role :user :content "Hi!"}]]
             (#'llm-clj.core/extract-system-prompt msgs))))))
