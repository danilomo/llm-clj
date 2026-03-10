(ns llm-clj.server-tools-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.server-tools :as st]))

(deftest test-web-search-basic
  (let [tool (st/web-search)]
    (is (= "web_search_20250305" (:type tool)))
    (is (= "web_search" (:name tool)))))

(deftest test-web-search-with-options
  (let [tool (st/web-search {:max-results 5
                             :allowed-domains ["example.com"]})]
    (is (= 5 (:max_results tool)))
    (is (= ["example.com"] (:allowed_domains tool)))))

(deftest test-web-search-with-blocked-domains
  (let [tool (st/web-search {:blocked-domains ["spam.com" "ads.net"]})]
    (is (= ["spam.com" "ads.net"] (:blocked_domains tool)))))

(deftest test-code-execution
  (let [tool (st/code-execution)]
    (is (= "code_execution_20250522" (:type tool)))
    (is (= "code_execution" (:name tool)))))

(deftest test-code-execution-with-timeout
  (let [tool (st/code-execution {:timeout 30})]
    (is (= 30 (:timeout tool)))))

(deftest test-bash-tool
  (let [tool (st/bash-tool)]
    (is (= "bash_20250124" (:type tool)))
    (is (= "bash" (:name tool)))))

(deftest test-bash-tool-with-restart
  (let [tool (st/bash-tool {:restart-on-failure true})]
    (is (true? (:restart_on_failure tool)))))

(deftest test-text-editor
  (let [tool (st/text-editor)]
    (is (= "text_editor_20250124" (:type tool)))
    (is (= "str_replace_editor" (:name tool)))))

(deftest test-computer
  (let [tool (st/computer {:display-width 1920 :display-height 1080})]
    (is (= "computer_20250124" (:type tool)))
    (is (= "computer" (:name tool)))
    (is (= 1920 (:display_width_px tool)))
    (is (= 1080 (:display_height_px tool)))))

(deftest test-computer-defaults
  (let [tool (st/computer)]
    (is (= 1024 (:display_width_px tool)))
    (is (= 768 (:display_height_px tool)))))

(deftest test-computer-with-display-number
  (let [tool (st/computer {:display-number 1})]
    (is (= 1 (:display_number tool)))))

(deftest test-mcp-server
  (let [tool (st/mcp-server {:name "test" :url "http://localhost:3000"})]
    (is (= "mcp" (:type tool)))
    (is (= "test" (:server_name tool)))
    (is (= "http://localhost:3000" (:server_url tool)))))

(deftest test-mcp-server-with-tools
  (let [tool (st/mcp-server {:name "test"
                             :url "http://localhost:3000"
                             :tools ["tool1" "tool2"]})]
    (is (= ["tool1" "tool2"] (:allowed_tools tool)))))

(deftest test-mcp-server-validation
  (is (thrown? clojure.lang.ExceptionInfo
               (st/mcp-server {:name "test"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (st/mcp-server {:url "http://localhost:3000"})))
  (is (thrown? clojure.lang.ExceptionInfo
               (st/mcp-server {}))))

(deftest test-server-tool?
  (is (true? (st/server-tool? (st/web-search))))
  (is (true? (st/server-tool? (st/code-execution))))
  (is (true? (st/server-tool? (st/bash-tool))))
  (is (true? (st/server-tool? (st/text-editor))))
  (is (true? (st/server-tool? (st/computer))))
  (is (true? (st/server-tool? (st/mcp-server {:name "test" :url "http://localhost"}))))
  (is (false? (st/server-tool? {:type "function" :name "my-tool"}))))

(deftest test-required-beta-features
  (is (= #{} (st/required-beta-features [(st/web-search)])))
  (is (= #{} (st/required-beta-features [(st/code-execution)])))
  (is (= #{:computer-use} (st/required-beta-features [(st/bash-tool)])))
  (is (= #{:computer-use} (st/required-beta-features [(st/text-editor)])))
  (is (= #{:computer-use} (st/required-beta-features [(st/computer)])))
  (is (= #{:mcp} (st/required-beta-features [(st/mcp-server {:name "test" :url "http://localhost"})])))
  (is (= #{:computer-use} (st/required-beta-features [(st/bash-tool) (st/computer)])))
  (is (= #{:computer-use}
         (st/required-beta-features [(st/code-execution) (st/bash-tool)]))))

(deftest test-validate-beta-features-pass
  (is (true? (st/validate-beta-features [(st/code-execution)] [])))
  (is (true? (st/validate-beta-features [(st/web-search)] [])))
  (is (true? (st/validate-beta-features [(st/bash-tool) (st/computer)] [:computer-use])))
  (is (true? (st/validate-beta-features [(st/code-execution) (st/bash-tool)]
                                        [:computer-use]))))

(deftest test-validate-beta-features-fail
  (is (thrown? clojure.lang.ExceptionInfo
               (st/validate-beta-features [(st/bash-tool)] [])))
  (is (thrown? clojure.lang.ExceptionInfo
               (st/validate-beta-features [(st/code-execution) (st/bash-tool)] []))))

(deftest test-with-required-betas
  (let [opts (st/with-required-betas [(st/code-execution)] {})]
    (is (empty? (:beta-features opts))))
  (let [opts (st/with-required-betas [(st/bash-tool) (st/computer)] {})]
    (is (contains? (set (:beta-features opts)) :computer-use)))
  (let [opts (st/with-required-betas [(st/web-search)] {})]
    (is (empty? (:beta-features opts)))))

(deftest test-with-required-betas-preserves-existing
  (let [opts (st/with-required-betas [(st/bash-tool)]
               {:beta-features [:prompt-caching]})]
    (is (contains? (set (:beta-features opts)) :computer-use))
    (is (contains? (set (:beta-features opts)) :prompt-caching))))

(deftest test-web-search-result?
  (is (true? (st/web-search-result? {:type "web_search_tool_result"})))
  (is (false? (st/web-search-result? {:type "text"})))
  (is (false? (st/web-search-result? {:type "tool_use"}))))

(deftest test-code-execution-result?
  (is (true? (st/code-execution-result? {:type "code_execution_tool_result"})))
  (is (false? (st/code-execution-result? {:type "text"}))))

(deftest test-extract-web-search-results
  (let [content-blocks [{:type "text" :text "Here are the results"}
                        {:type "web_search_tool_result"
                         :results [{:title "Result 1" :url "http://example.com/1"}
                                   {:title "Result 2" :url "http://example.com/2"}]}
                        {:type "text" :text "Based on these results..."}]]
    (is (= [{:title "Result 1" :url "http://example.com/1"}
            {:title "Result 2" :url "http://example.com/2"}]
           (vec (st/extract-web-search-results content-blocks))))))

(deftest test-extract-web-search-results-multiple-blocks
  (let [content-blocks [{:type "web_search_tool_result"
                         :results [{:title "Result 1" :url "http://example.com/1"}]}
                        {:type "web_search_tool_result"
                         :results [{:title "Result 2" :url "http://example.com/2"}]}]]
    (is (= 2 (count (st/extract-web-search-results content-blocks))))))

(deftest test-extract-code-output
  (let [content-blocks [{:type "text" :text "Running code..."}
                        {:type "code_execution_tool_result"
                         :output "Hello, World!"}]]
    (is (= "Hello, World!" (st/extract-code-output content-blocks)))))

(deftest test-extract-code-output-multiple
  (let [content-blocks [{:type "code_execution_tool_result"
                         :output "Line 1"}
                        {:type "code_execution_tool_result"
                         :output "Line 2"}]]
    (is (= "Line 1\nLine 2" (st/extract-code-output content-blocks)))))

(deftest test-extract-code-output-empty
  (let [content-blocks [{:type "text" :text "No code executed"}]]
    (is (= "" (st/extract-code-output content-blocks)))))
