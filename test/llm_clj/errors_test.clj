(ns llm-clj.errors-test
  (:require [clojure.test :refer [deftest is testing]]
            [llm-clj.errors :as errors]))

(deftest test-api-error-creation
  (let [e (errors/api-error :openai 400 "{\"error\":{\"message\":\"bad request\"}}")]
    (is (errors/api-error? e))
    (is (= :openai (errors/error-provider e)))
    (is (= 400 (errors/error-status e)))))

(deftest test-rate-limit-detection
  (let [e (errors/api-error :anthropic 429 "{}" :headers {"retry-after" "30"})]
    (is (errors/rate-limited? e))
    (is (errors/retryable? e))
    (is (= 30 (errors/retry-after e)))))

(deftest test-authentication-error
  (let [e (errors/api-error :openai 401 "{\"error\":{\"message\":\"invalid api key\"}}")]
    (is (errors/authentication-error? e))
    (is (not (errors/retryable? e)))))

(deftest test-server-error
  (let [e (errors/api-error :openai 500 "Internal server error")]
    (is (errors/server-error? e))
    (is (errors/retryable? e))))

(deftest test-timeout-error
  (let [e (errors/timeout-error :openai 30000)]
    (is (errors/timeout? e))
    (is (errors/retryable? e))))

(deftest test-validation-error
  (let [e (errors/validation-error "Invalid model" {:model "bad-model"})]
    (is (errors/validation-error? e))
    (is (not (errors/api-error? e)))))

(deftest test-error-hierarchy
  ;; rate-limit-error is a type of api-error
  (let [e (errors/rate-limit-error :openai 30)]
    (is (errors/api-error? e))
    (is (errors/rate-limited? e))))

(deftest test-not-found-error
  (let [e (errors/api-error :openai 404 "{\"error\":{\"message\":\"model not found\"}}")]
    (is (errors/api-error? e))
    (is (= :llm-clj.errors/not-found-error (errors/error-type e)))
    (is (= 404 (errors/error-status e)))))

(deftest test-retry-after-case-insensitive
  (testing "lowercase retry-after header"
    (let [e (errors/api-error :anthropic 429 "{}" :headers {"retry-after" "60"})]
      (is (= 60 (errors/retry-after e)))))
  (testing "capitalized Retry-After header"
    (let [e (errors/api-error :anthropic 429 "{}" :headers {"Retry-After" "45"})]
      (is (= 45 (errors/retry-after e))))))

(deftest test-non-json-error-body
  (let [e (errors/api-error :openai 500 "Internal Server Error")]
    (is (errors/server-error? e))
    (is (string? (get (ex-data e) :body)))))

(deftest test-error-message-extraction
  (testing "OpenAI error format"
    (let [e (errors/api-error :openai 400 "{\"error\":{\"message\":\"Invalid request\"}}")]
      (is (= "openai API error: Invalid request" (ex-message e)))))
  (testing "Anthropic error format"
    (let [e (errors/api-error :anthropic 400 "{\"message\":\"Bad input\"}")]
      (is (= "anthropic API error: Bad input" (ex-message e)))))
  (testing "Plain string error"
    (let [e (errors/api-error :openai 500 "Server error")]
      (is (= "openai API error: Server error" (ex-message e))))))
