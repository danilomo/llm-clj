(ns llm-clj.openai-params-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.providers.openai :as openai]
            [cheshire.core :as json]))

(deftest test-parse-single-completion
  (testing "Parses single completion response correctly"
    (let [response {:body (json/generate-string
                           {:choices [{:message {:role "assistant"
                                                 :content "Hello!"}
                                       :finish_reason "stop"}]
                            :usage {:prompt_tokens 10
                                    :completion_tokens 5
                                    :total_tokens 15}})}
          result (#'openai/parse-response response)]
      (is (= :assistant (:role result)))
      (is (= "Hello!" (:content result)))
      (is (= :stop (:finish-reason result)))
      (is (= {:prompt_tokens 10 :completion_tokens 5 :total_tokens 15} (:usage result))))))

(deftest test-parse-multiple-completions
  (testing "Parses multiple completions (n>1) into :choices vector"
    (let [response {:body (json/generate-string
                           {:choices [{:message {:role "assistant"
                                                 :content "Response 1"}
                                       :finish_reason "stop"}
                                      {:message {:role "assistant"
                                                 :content "Response 2"}
                                       :finish_reason "stop"}
                                      {:message {:role "assistant"
                                                 :content "Response 3"}
                                       :finish_reason "stop"}]
                            :usage {:prompt_tokens 10
                                    :completion_tokens 15
                                    :total_tokens 25}})}
          result (#'openai/parse-response response)]
      (is (contains? result :choices))
      (is (= 3 (count (:choices result))))
      (is (= "Response 1" (:content (first (:choices result)))))
      (is (= "Response 2" (:content (second (:choices result)))))
      (is (= "Response 3" (:content (nth (:choices result) 2))))
      (is (= {:prompt_tokens 10 :completion_tokens 15 :total_tokens 25} (:usage result))))))

(deftest test-parse-logprobs
  (testing "Parses logprobs when present in response"
    (let [logprobs-data {:content [{:token "Hello"
                                    :logprob -0.1
                                    :bytes [72 101 108 108 111]
                                    :top_logprobs [{:token "Hello" :logprob -0.1}
                                                   {:token "Hi" :logprob -2.3}]}]}
          response {:body (json/generate-string
                           {:choices [{:message {:role "assistant"
                                                 :content "Hello!"}
                                       :finish_reason "stop"
                                       :logprobs logprobs-data}]
                            :usage {:prompt_tokens 10
                                    :completion_tokens 5
                                    :total_tokens 15}})}
          result (#'openai/parse-response response)]
      (is (contains? result :logprobs))
      (is (= logprobs-data (:logprobs result))))))

(deftest test-parse-tool-calls
  (testing "Parses tool calls correctly"
    (let [tool-calls [{:id "call_abc123"
                       :type "function"
                       :function {:name "get_weather"
                                  :arguments "{\"location\":\"Boston\"}"}}]
          response {:body (json/generate-string
                           {:choices [{:message {:role "assistant"
                                                 :content nil
                                                 :tool_calls tool-calls}
                                       :finish_reason "tool_calls"}]
                            :usage {:prompt_tokens 10
                                    :completion_tokens 5
                                    :total_tokens 15}})}
          result (#'openai/parse-response response)]
      (is (contains? result :tool-calls))
      (is (= tool-calls (:tool-calls result)))
      (is (= :tool_calls (:finish-reason result))))))

(deftest test-parse-reasoning
  (testing "Parses reasoning/thinking content"
    (let [response {:body (json/generate-string
                           {:choices [{:message {:role "assistant"
                                                 :content "Answer"
                                                 :reasoning "Let me think..."}
                                       :finish_reason "stop"}]
                            :usage {:prompt_tokens 10
                                    :completion_tokens 5
                                    :total_tokens 15}})}
          result (#'openai/parse-response response)]
      (is (contains? result :thinking))
      (is (= {:content "Let me think..."} (:thinking result))))))

(deftest test-format-message-with-tool-calls
  (testing "Formats messages with tool calls correctly"
    (let [msg {:role :assistant
               :content nil
               :tool-calls [{:id "call_123"
                             :type "function"
                             :function {:name "test"
                                        :arguments "{}"}}]}
          formatted (#'openai/format-message msg)]
      (is (= "assistant" (:role formatted)))
      (is (= nil (:content formatted)))
      (is (= (:tool-calls msg) (:tool_calls formatted))))))

(deftest test-format-message-with-tool-result
  (testing "Formats tool result messages correctly"
    (let [msg {:role :tool
               :tool-call-id "call_123"
               :content "result"}
          formatted (#'openai/format-message msg)]
      (is (= "tool" (:role formatted)))
      (is (= "call_123" (:tool_call_id formatted)))
      (is (= "result" (:content formatted))))))

(deftest test-format-message-with-name
  (testing "Formats messages with name field correctly"
    (let [msg {:role :user
               :name "John"
               :content "Hello"}
          formatted (#'openai/format-message msg)]
      (is (= "user" (:role formatted)))
      (is (= "John" (:name formatted)))
      (is (= "Hello" (:content formatted))))))
