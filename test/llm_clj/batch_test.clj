(ns llm-clj.batch-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as str]
            [llm-clj.batch.openai :as batch-openai]
            [llm-clj.batch.anthropic :as batch-anthropic]
            [cheshire.core :as json]))

;; OpenAI tests

(deftest test-request->jsonl-line
  (let [req {:custom-id "test-1"
             :messages [{:role :user :content "Hello"}]
             :params {:model "gpt-4o"}}
        line (#'batch-openai/request->jsonl-line req)
        parsed (json/parse-string line true)]
    (is (= "test-1" (:custom_id parsed)))
    (is (= "POST" (:method parsed)))
    (is (= "/v1/chat/completions" (:url parsed)))
    (is (= "gpt-4o" (get-in parsed [:body :model])))))

(deftest test-request->jsonl-line-default-model
  (let [req {:custom-id "test-2"
             :messages [{:role :user :content "Hello"}]
             :params {}}
        line (#'batch-openai/request->jsonl-line req)
        parsed (json/parse-string line true)]
    (is (= "gpt-4o" (get-in parsed [:body :model])))))

(deftest test-request->jsonl-line-with-extra-params
  (let [req {:custom-id "test-3"
             :messages [{:role :user :content "Hello"}]
             :params {:model "gpt-4o-mini" :temperature 0.7 :max_tokens 100}}
        line (#'batch-openai/request->jsonl-line req)
        parsed (json/parse-string line true)]
    (is (= "gpt-4o-mini" (get-in parsed [:body :model])))
    (is (= 0.7 (get-in parsed [:body :temperature])))
    (is (= 100 (get-in parsed [:body :max_tokens])))))

(deftest test-requests->jsonl
  (let [reqs [{:custom-id "1" :messages [] :params {}}
              {:custom-id "2" :messages [] :params {}}]
        jsonl (#'batch-openai/requests->jsonl reqs)
        lines (str/split-lines jsonl)]
    (is (= 2 (count lines)))
    (is (= "1" (:custom_id (json/parse-string (first lines) true))))
    (is (= "2" (:custom_id (json/parse-string (second lines) true))))))

(deftest test-parse-results-jsonl
  (let [jsonl "{\"custom_id\":\"req-1\",\"response\":{\"status_code\":200,\"body\":{\"content\":\"Hello\"}}}"
        results (batch-openai/parse-results-jsonl jsonl)]
    (is (contains? results "req-1"))
    (is (= 200 (get-in results ["req-1" :status])))
    (is (= {:content "Hello"} (get-in results ["req-1" :body])))))

(deftest test-parse-results-jsonl-multiple
  (let [jsonl (str "{\"custom_id\":\"req-1\",\"response\":{\"status_code\":200,\"body\":{}}}\n"
                   "{\"custom_id\":\"req-2\",\"response\":{\"status_code\":200,\"body\":{}}}")
        results (batch-openai/parse-results-jsonl jsonl)]
    (is (= 2 (count results)))
    (is (contains? results "req-1"))
    (is (contains? results "req-2"))))

(deftest test-parse-results-jsonl-with-error
  (let [jsonl "{\"custom_id\":\"req-1\",\"error\":{\"code\":\"invalid_request\",\"message\":\"Bad request\"}}"
        results (batch-openai/parse-results-jsonl jsonl)]
    (is (contains? results "req-1"))
    (is (= {:code "invalid_request" :message "Bad request"} (get-in results ["req-1" :error])))))

(deftest test-parse-batch-status
  (let [body {:id "batch_abc123"
              :status "completed"
              :created_at 1234567890
              :completed_at 1234567900
              :request_counts {:total 10 :completed 9 :failed 1}
              :output_file_id "file_out"
              :error_file_id "file_err"
              :metadata {:key "value"}}
        status (#'batch-openai/parse-batch-status body)]
    (is (= "batch_abc123" (:id status)))
    (is (= :completed (:status status)))
    (is (= 1234567890 (:created-at status)))
    (is (= 1234567900 (:completed-at status)))
    (is (= {:total 10 :completed 9 :failed 1} (:request-counts status)))
    (is (= "file_out" (:output-file-id status)))
    (is (= "file_err" (:error-file-id status)))
    (is (= {:key "value"} (:metadata status)))))

(deftest test-parse-batch-status-from-json-string
  (let [body (json/generate-string {:id "batch_xyz" :status "in_progress"})
        status (#'batch-openai/parse-batch-status body)]
    (is (= "batch_xyz" (:id status)))
    (is (= :in_progress (:status status)))))

;; Anthropic tests

(deftest test-anthropic-format-request
  (let [req {:custom-id "test-1"
             :messages [{:role :user :content "Hello"}]
             :params {:model "claude-3-opus-20240229"}}
        formatted (#'batch-anthropic/format-request req)]
    (is (= "test-1" (:custom_id formatted)))
    (is (= "claude-3-opus-20240229" (get-in formatted [:params :model])))
    (is (= 4096 (get-in formatted [:params :max_tokens])))
    (is (= [{:role :user :content "Hello"}] (get-in formatted [:params :messages])))))

(deftest test-anthropic-format-request-default-model
  (let [req {:custom-id "test-2"
             :messages [{:role :user :content "Hello"}]
             :params {}}
        formatted (#'batch-anthropic/format-request req)]
    (is (= "claude-3-haiku-20240307" (get-in formatted [:params :model])))))

(deftest test-anthropic-format-request-with-max-tokens
  (let [req {:custom-id "test-3"
             :messages [{:role :user :content "Hello"}]
             :params {:max-tokens 1000}}
        formatted (#'batch-anthropic/format-request req)]
    (is (= 1000 (get-in formatted [:params :max_tokens])))))

(deftest test-anthropic-parse-batch-status
  (let [body {:id "msgbatch_abc123"
              :processing_status "ended"
              :created_at "2024-01-01T00:00:00Z"
              :ended_at "2024-01-01T01:00:00Z"
              :request_counts {:processing 0 :succeeded 9 :errored 1 :canceled 0}
              :results_url "https://api.anthropic.com/results"}
        status (#'batch-anthropic/parse-batch-status body)]
    (is (= "msgbatch_abc123" (:id status)))
    (is (= :ended (:status status)))
    (is (= "2024-01-01T00:00:00Z" (:created-at status)))
    (is (= "2024-01-01T01:00:00Z" (:ended-at status)))
    (is (= {:total 0 :completed 9 :failed 1} (:request-counts status)))
    (is (= "https://api.anthropic.com/results" (:results-url status)))))

(deftest test-anthropic-parse-batch-status-in-progress
  (let [body {:id "msgbatch_xyz"
              :processing_status "in_progress"
              :request_counts {:processing 5 :succeeded 3 :errored 0 :canceled 0}}
        status (#'batch-anthropic/parse-batch-status body)]
    (is (= :in-progress (:status status)))
    (is (= 5 (get-in status [:request-counts :total])))
    (is (= 3 (get-in status [:request-counts :completed])))))

(deftest test-anthropic-parse-batch-status-from-json-string
  (let [body (json/generate-string {:id "msgbatch_test" :processing_status "ended"})
        status (#'batch-anthropic/parse-batch-status body)]
    (is (= "msgbatch_test" (:id status)))
    (is (= :ended (:status status)))))
