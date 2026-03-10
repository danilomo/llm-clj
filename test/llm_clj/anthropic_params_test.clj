(ns llm-clj.anthropic-params-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as str]
            [llm-clj.providers.anthropic :as anthropic]))

(deftest test-beta-features-header-single
  (testing "Single beta feature maps to version string"
    (is (= "prompt-caching-2024-07-31"
           (#'anthropic/beta-features->header [:prompt-caching])))))

(deftest test-beta-features-header-multiple
  (testing "Multiple beta features are comma-separated"
    (is (= "prompt-caching-2024-07-31,pdfs-2024-09-25"
           (#'anthropic/beta-features->header [:prompt-caching :pdfs])))))

(deftest test-beta-features-header-all-known
  (testing "All known beta features map correctly"
    (let [result (#'anthropic/beta-features->header
                  [:prompt-caching :pdfs :token-counting :message-batches
                   :computer-use :code-execution :mcp :interleaved-thinking :files])]
      (is (clojure.string/includes? result "prompt-caching-2024-07-31"))
      (is (clojure.string/includes? result "pdfs-2024-09-25"))
      (is (clojure.string/includes? result "token-counting-2024-11-01"))
      (is (clojure.string/includes? result "message-batches-2024-09-24"))
      (is (clojure.string/includes? result "computer-use-2025-01-24"))
      (is (clojure.string/includes? result "code-execution-2025-05-22"))
      (is (clojure.string/includes? result "mcp-client-2025-04-04"))
      (is (clojure.string/includes? result "interleaved-thinking-2025-05-14"))
      (is (clojure.string/includes? result "files-2025-04-14")))))

(deftest test-beta-features-header-unknown
  (testing "Unknown beta features pass through as-is"
    (is (= "new-feature"
           (#'anthropic/beta-features->header [:new-feature])))))

(deftest test-beta-features-header-mixed
  (testing "Mixed known and unknown beta features"
    (let [result (#'anthropic/beta-features->header [:prompt-caching :unknown-feature])]
      (is (clojure.string/includes? result "prompt-caching-2024-07-31"))
      (is (clojure.string/includes? result "unknown-feature")))))

(deftest test-beta-features-header-empty
  (testing "Empty vector returns nil"
    (is (nil? (#'anthropic/beta-features->header [])))))

(deftest test-beta-features-header-nil
  (testing "Nil returns nil"
    (is (nil? (#'anthropic/beta-features->header nil)))))

(deftest test-build-headers-basic
  (testing "Basic headers without optional parameters"
    (let [headers (#'anthropic/build-headers "test-key")]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "2023-06-01" (get headers "anthropic-version")))
      (is (= "application/json" (get headers "content-type")))
      (is (nil? (get headers "anthropic-beta")))
      (is (nil? (get headers "anthropic-service-tier"))))))

(deftest test-build-headers-with-beta-features
  (testing "Headers include anthropic-beta when beta features provided"
    (let [headers (#'anthropic/build-headers "test-key" [:pdfs] nil)]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "pdfs-2024-09-25" (get headers "anthropic-beta")))
      (is (nil? (get headers "anthropic-service-tier"))))))

(deftest test-build-headers-with-multiple-beta-features
  (testing "Headers include multiple beta features"
    (let [headers (#'anthropic/build-headers "test-key" [:prompt-caching :pdfs] nil)]
      (is (= "prompt-caching-2024-07-31,pdfs-2024-09-25" (get headers "anthropic-beta"))))))

(deftest test-build-headers-with-priority-service-tier
  (testing "Headers include service tier for :priority"
    (let [headers (#'anthropic/build-headers "test-key" nil :priority)]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "priority" (get headers "anthropic-service-tier")))
      (is (nil? (get headers "anthropic-beta"))))))

(deftest test-build-headers-with-standard-service-tier
  (testing "Headers do not include service tier for :standard"
    (let [headers (#'anthropic/build-headers "test-key" nil :standard)]
      (is (= "test-key" (get headers "x-api-key")))
      (is (nil? (get headers "anthropic-service-tier"))))))

(deftest test-build-headers-with-both-beta-and-service-tier
  (testing "Headers include both beta features and service tier"
    (let [headers (#'anthropic/build-headers "test-key" [:pdfs :token-counting] :priority)]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "pdfs-2024-09-25,token-counting-2024-11-01" (get headers "anthropic-beta")))
      (is (= "priority" (get headers "anthropic-service-tier"))))))

(deftest test-build-headers-arity-1
  (testing "Single-arity build-headers works correctly"
    (let [headers (#'anthropic/build-headers "test-key")]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "2023-06-01" (get headers "anthropic-version")))
      (is (nil? (get headers "anthropic-beta")))
      (is (nil? (get headers "anthropic-service-tier"))))))

(deftest test-build-headers-arity-3
  (testing "Three-arity build-headers works correctly"
    (let [headers (#'anthropic/build-headers "test-key" [:pdfs] :priority)]
      (is (= "test-key" (get headers "x-api-key")))
      (is (= "pdfs-2024-09-25" (get headers "anthropic-beta")))
      (is (= "priority" (get headers "anthropic-service-tier"))))))
