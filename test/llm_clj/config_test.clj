(ns llm-clj.config-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.config :as config]))

(deftest test-default-config
  (is (= "gpt-4o" (:model (config/default-config :openai))))
  (is (= 4096 (:max-tokens (config/default-config :anthropic)))))

(deftest test-merge-config
  (let [merged (config/merge-config :openai {:model "gpt-4o-mini"})]
    (is (= "gpt-4o-mini" (:model merged)))
    (is (= 120000 (:timeout-ms merged))))) ;; default preserved

(deftest test-resolve-api-key-explicit
  (is (= "my-key" (config/resolve-api-key :openai "my-key"))))

(deftest test-resolve-api-key-from-env
  (with-redefs [config/get-env (fn [k] (when (= k "OPENAI_API_KEY") "env-key"))]
    (is (= "env-key" (config/resolve-api-key :openai nil)))))

(deftest test-resolve-api-key-missing
  (with-redefs [config/get-env (constantly nil)]
    (is (thrown? clojure.lang.ExceptionInfo
                 (config/resolve-api-key :openai nil)))))

(deftest test-validate-temperature
  (is (= 0.5 (config/validate-temperature 0.5)))
  (is (nil? (config/validate-temperature nil)))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-temperature 2.5))))

(deftest test-validate-max-tokens
  (is (= 100 (config/validate-max-tokens 100)))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-max-tokens 0)))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-max-tokens -1))))

(deftest test-validate-options
  (is (= {:temperature 0.7} (config/validate-options {:temperature 0.7})))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-options {:temperature 5}))))

(deftest test-unknown-provider
  (is (thrown? clojure.lang.ExceptionInfo
               (config/default-config :unknown-provider))))

(deftest test-with-timeout
  (let [opts (config/with-timeout {} 5000)]
    (is (= 5000 (:socket-timeout opts)))
    (is (= 5000 (:connection-timeout opts)))))

(deftest test-openai-config
  (with-redefs [config/get-env (fn [k] (when (= k "OPENAI_API_KEY") "test-key"))]
    (let [cfg (config/openai-config)]
      (is (= "test-key" (:api-key cfg)))
      (is (= "gpt-4o" (:model cfg)))
      (is (nil? (:base-url cfg))))))

(deftest test-anthropic-config
  (with-redefs [config/get-env (fn [k] (when (= k "ANTHROPIC_API_KEY") "test-key"))]
    (let [cfg (config/anthropic-config {:model "claude-opus-4-5-20251101"})]
      (is (= "test-key" (:api-key cfg)))
      (is (= "claude-opus-4-5-20251101" (:model cfg)))
      (is (= 4096 (:max-tokens cfg))))))

(deftest test-validate-model
  (is (= "gpt-4o" (config/validate-model "gpt-4o")))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-model nil)))
  (is (thrown? clojure.lang.ExceptionInfo
               (config/validate-model ""))))

(deftest test-explicit-base-url
  (with-redefs [config/get-env (fn [k] (when (= k "OPENAI_API_KEY") "test-key"))]
    (let [cfg (config/openai-config {:base-url "https://custom.endpoint.com"})]
      (is (= "https://custom.endpoint.com" (:base-url cfg))))))
