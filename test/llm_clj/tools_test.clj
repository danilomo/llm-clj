(ns llm-clj.tools-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [llm-clj.tools :as tools]
            [llm-clj.config :as config]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]))

;; ---------------------------------------------------------------------------
;; Fixture
;; ---------------------------------------------------------------------------

(use-fixtures :once (fn [f] (config/load-env!) (f)))

;; ---------------------------------------------------------------------------
;; Sample tools
;; ---------------------------------------------------------------------------

(defn- eval-infix
  "Evaluates a simple infix math expression like '15 * 7' or '(* 15 7)'.
   Handles basic operators: + - * /"
  [expr]
  (let [expr (str/trim expr)]
    ;; Try infix pattern first: "num op num"
    (if-let [[_ a op b] (re-matches #"(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)" expr)]
      (let [a (Double/parseDouble a)
            b (Double/parseDouble b)
            op-fn (case op "+" + "-" - "*" * "/" /)]
        (long (op-fn a b)))
      ;; Fall back to Clojure prefix notation evaluation
      (eval (read-string expr)))))

(def calculator-tool
  (tools/define-tool
    "calculate"
    "Performs basic arithmetic. Returns the result of evaluating a mathematical expression."
    [:map [:expression :string]]
    (fn [{:keys [expression]}]
      {:result (eval-infix expression)})))

(def weather-tool
  (tools/define-tool
    "get_weather"
    "Gets the current weather for a given location"
    [:map [:location :string]]
    (fn [{:keys [location]}]
      {:location location
       :temperature 22
       :condition "sunny"})))

(def failing-tool
  (tools/define-tool
    "fail_always"
    "A tool that always throws an exception"
    [:map [:input :string]]
    (fn [_]
      (throw (ex-info "Intentional failure" {})))))

;; ---------------------------------------------------------------------------
;; Unit tests
;; ---------------------------------------------------------------------------

(deftest execute-tool-call-safe-catches-exceptions
  (testing "execute-tool-call-safe returns error message when tool throws"
    (let [tool-call {:id "test-123"
                     :function {:name "fail_always"
                                :arguments "{\"input\": \"test\"}"}}
          result (#'tools/execute-tool-call-safe [failing-tool] tool-call)]
      (is (= :tool (:role result)))
      (is (= "test-123" (:tool-call-id result)))
      (is (str/includes? (:content result) "error")))))

;; ---------------------------------------------------------------------------
;; Integration tests
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-with-tools-openai-test
  (testing "chat-with-tools executes tools and returns final response"
    (let [provider (openai/create-provider
                    {:api-key (config/get-env "OPENAI_API_KEY")
                     :model "gpt-4o-mini"})
          messages [{:role :user :content "What is 15 * 7? Use the calculate tool."}]
          result (tools/chat-with-tools provider messages [calculator-tool]
                                        {:max-tokens 100})]
      (is (map? result))
      (is (:content result))
      (is (or (str/includes? (:content result) "105")
              (str/includes? (str (:content result)) "105"))))))

(deftest ^:integration chat-with-tools-anthropic-test
  (testing "chat-with-tools works with Anthropic provider"
    (let [provider (anthropic/create-provider
                    {:api-key (config/get-env "ANTHROPIC_API_KEY")
                     :model "claude-haiku-4-5-20251001"})
          messages [{:role :user :content "What's the weather in Tokyo? Use the get_weather tool."}]
          result (tools/chat-with-tools provider messages [weather-tool]
                                        {:max-tokens 200})]
      (is (map? result))
      (is (:content result))
      (is (str/includes? (str/lower-case (str (:content result))) "tokyo")))))

(deftest ^:integration chat-with-tools-on-tool-call-callback-test
  (testing "on-tool-call callback is invoked for each tool call"
    (let [provider (openai/create-provider
                    {:api-key (config/get-env "OPENAI_API_KEY")
                     :model "gpt-4o-mini"})
          messages [{:role :user :content "What is 2 + 2? Use the calculate tool."}]
          callback-calls (atom [])
          result (tools/chat-with-tools provider messages [calculator-tool]
                                        {:max-tokens 100
                                         :on-tool-call (fn [tc res]
                                                         (swap! callback-calls conj {:tool-call tc :result res}))})]
      (is (map? result))
      (is (pos? (count @callback-calls)))
      (is (= "calculate" (get-in (first @callback-calls) [:tool-call :function :name]))))))

(deftest ^:integration chat-with-tools-handles-tool-errors-gracefully
  (testing "chat-with-tools continues when a tool throws an exception"
    (let [provider (openai/create-provider
                    {:api-key (config/get-env "OPENAI_API_KEY")
                     :model "gpt-4o-mini"})
          messages [{:role :user :content "Call the fail_always tool with input 'test'"}]
          result (tools/chat-with-tools provider messages [failing-tool]
                                        {:max-tokens 200})]
      ;; The model should receive the error and respond accordingly
      (is (map? result))
      (is (:content result)))))

(deftest ^:integration chat-with-tools-multi-tool-test
  (testing "chat-with-tools handles multiple tools in one request (docs example)"
    (let [provider (openai/create-provider
                    {:api-key (config/get-env "OPENAI_API_KEY")
                     :model "gpt-4o-mini"})
          messages [{:role :user :content "What's 15 * 7? And what's the weather in Tokyo?"}]
          result (tools/chat-with-tools provider messages
                                        [calculator-tool weather-tool]
                                        {:max-tokens 200})]
      (is (map? result))
      (is (:content result))
      ;; Should mention the calculation result
      (is (str/includes? (str (:content result)) "105"))
      ;; Should mention Tokyo
      (is (str/includes? (str/lower-case (str (:content result))) "tokyo")))))
