(ns llm-clj.api-integration-test
  "Integration tests for the unified chat API.

   Tests all four cases:
   1. Simple completion (no tools, no schema)
   2. Schema only (structured output)
   3. Tools only (tool calling)
   4. Both tools and schema"
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [llm-clj.api :as api]
            [llm-clj.tools :as tools]
            [llm-clj.config :as config]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]))

;; ---------------------------------------------------------------------------
;; Fixture
;; ---------------------------------------------------------------------------

(use-fixtures :once (fn [f] (config/load-env!) (f)))

;; ---------------------------------------------------------------------------
;; Provider factories
;; ---------------------------------------------------------------------------

(defn- openai-provider []
  (openai/create-provider
   {:api-key (config/get-env "OPENAI_API_KEY")
    :model "gpt-4o-mini"}))

(defn- anthropic-provider []
  (anthropic/create-provider
   {:api-key (config/get-env "ANTHROPIC_API_KEY")
    :model "claude-haiku-4-5-20251001"}))

;; ---------------------------------------------------------------------------
;; Sample tools
;; ---------------------------------------------------------------------------

(defn- eval-infix
  "Evaluates a simple infix math expression like '15 * 7'."
  [expr]
  (let [expr (str/trim expr)]
    (if-let [[_ a op b] (re-matches #"(-?\d+(?:\.\d+)?)\s*([+\-*/])\s*(-?\d+(?:\.\d+)?)" expr)]
      (let [a (Double/parseDouble a)
            b (Double/parseDouble b)
            op-fn (case op "+" + "-" - "*" * "/" /)]
        (long (op-fn a b)))
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

;; ---------------------------------------------------------------------------
;; Sample schemas
;; ---------------------------------------------------------------------------

(def PersonSchema
  [:map
   [:name :string]
   [:age :int]])

(def WeatherSummarySchema
  [:map
   [:location :string]
   [:temperature :int]
   [:description :string]])

;; ---------------------------------------------------------------------------
;; Case 1: Simple completion (no tools, no schema)
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-simple-openai-test
  (testing "Simple chat completion with OpenAI"
    (let [provider (openai-provider)
          messages [{:role :user :content "Say 'hello' and nothing else."}]
          result (api/chat provider messages {:max-tokens 20})]
      (is (map? result))
      (is (= :assistant (:role result)))
      (is (string? (:content result)))
      (is (str/includes? (str/lower-case (:content result)) "hello")))))

(deftest ^:integration chat-simple-anthropic-test
  (testing "Simple chat completion with Anthropic"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "Say 'hello' and nothing else."}]
          result (api/chat provider messages {:max-tokens 20})]
      (is (map? result))
      (is (= :assistant (:role result)))
      (is (string? (:content result)))
      (is (str/includes? (str/lower-case (:content result)) "hello")))))

;; ---------------------------------------------------------------------------
;; Case 2: Schema only (structured output)
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-schema-openai-test
  (testing "Structured output with OpenAI"
    (let [provider (openai-provider)
          messages [{:role :user :content "My name is Alice and I am 28 years old."}]
          result (api/chat provider messages {:response-schema PersonSchema
                                              :max-tokens 100})]
      (is (map? result))
      (is (= "Alice" (:name result)))
      (is (= 28 (:age result))))))

(deftest ^:integration chat-schema-anthropic-test
  (testing "Structured output with Anthropic"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "My name is Bob and I am 35 years old."}]
          result (api/chat provider messages {:response-schema PersonSchema
                                              :max-tokens 100})]
      (is (map? result))
      (is (= "Bob" (:name result)))
      (is (= 35 (:age result))))))

(deftest ^:integration chat-schema-custom-name-test
  (testing "Structured output with custom schema name"
    (let [provider (openai-provider)
          messages [{:role :user :content "Extract: John is 42 years old."}]
          result (api/chat provider messages {:response-schema PersonSchema
                                              :schema-name "PersonInfo"
                                              :schema-description "Information about a person"
                                              :max-tokens 100})]
      (is (map? result))
      (is (= "John" (:name result)))
      (is (= 42 (:age result))))))

(deftest ^:integration chat-schema-validation-disabled-test
  (testing "Structured output with validation disabled"
    (let [provider (openai-provider)
          messages [{:role :user :content "My name is Eve and I am 25."}]
          result (api/chat provider messages {:response-schema PersonSchema
                                              :validate? false
                                              :max-tokens 100})]
      (is (map? result))
      (is (contains? result :name))
      (is (contains? result :age)))))

;; ---------------------------------------------------------------------------
;; Case 3: Tools only
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-tools-openai-test
  (testing "Tool calling with OpenAI via unified chat"
    (let [provider (openai-provider)
          messages [{:role :user :content "What is 15 * 7? Use the calculate tool."}]
          result (api/chat provider messages {:tools [calculator-tool]
                                              :max-tokens 100})]
      (is (map? result))
      (is (:content result))
      (is (str/includes? (str (:content result)) "105")))))

(deftest ^:integration chat-tools-anthropic-test
  (testing "Tool calling with Anthropic via unified chat"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "What's the weather in Tokyo? Use the get_weather tool."}]
          result (api/chat provider messages {:tools [weather-tool]
                                              :max-tokens 200})]
      (is (map? result))
      (is (:content result))
      (is (str/includes? (str/lower-case (str (:content result))) "tokyo")))))

(deftest ^:integration chat-tools-callback-test
  (testing "Tool calling with on-tool-call callback"
    (let [provider (openai-provider)
          messages [{:role :user :content "Calculate 8 + 4 using the calculate tool."}]
          callback-calls (atom [])
          result (api/chat provider messages
                           {:tools [calculator-tool]
                            :max-tokens 100
                            :on-tool-call (fn [tc res]
                                            (swap! callback-calls conj {:tool-call tc :result res}))})]
      (is (map? result))
      (is (pos? (count @callback-calls)))
      (is (= "calculate" (get-in (first @callback-calls) [:tool-call :function :name]))))))

(deftest ^:integration chat-tools-multiple-test
  (testing "Multiple tools available"
    (let [provider (openai-provider)
          messages [{:role :user :content "What's 15 * 7? Also what's the weather in Paris?"}]
          result (api/chat provider messages
                           {:tools [calculator-tool weather-tool]
                            :max-tokens 300})]
      (is (map? result))
      (is (:content result))
      (is (str/includes? (str (:content result)) "105"))
      (is (str/includes? (str/lower-case (str (:content result))) "paris")))))

;; ---------------------------------------------------------------------------
;; Case 4: Both tools and schema
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-tools-and-schema-openai-test
  (testing "Tools and structured output with OpenAI"
    (let [provider (openai-provider)
          messages [{:role :user :content "What's the weather in London? Use the tool and return structured data."}]
          result (api/chat provider messages
                           {:tools [weather-tool]
                            :response-schema WeatherSummarySchema
                            :max-tokens 200})]
      (is (map? result))
      (is (string? (:location result)))
      (is (str/includes? (str/lower-case (:location result)) "london"))
      (is (integer? (:temperature result)))
      (is (string? (:description result))))))

(deftest ^:integration chat-tools-and-schema-anthropic-test
  (testing "Tools and structured output with Anthropic"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "What's the weather in Tokyo? Use the tool and give me structured output."}]
          result (api/chat provider messages
                           {:tools [weather-tool]
                            :response-schema WeatherSummarySchema
                            :max-tokens 300})]
      (is (map? result))
      (is (string? (:location result)))
      (is (str/includes? (str/lower-case (:location result)) "tokyo"))
      (is (integer? (:temperature result)))
      (is (string? (:description result))))))

(deftest ^:integration chat-tools-and-schema-with-calculation-test
  (testing "Tools and schema with calculation tool"
    (let [provider (openai-provider)
          messages [{:role :user :content "Calculate 25 * 4 and tell me the person's name is 'Calculator Bot' with the age being the result."}]
          result (api/chat provider messages
                           {:tools [calculator-tool]
                            :response-schema PersonSchema
                            :max-tokens 200})]
      (is (map? result))
      (is (= "Calculator Bot" (:name result)))
      (is (= 100 (:age result))))))

;; ---------------------------------------------------------------------------
;; Edge cases
;; ---------------------------------------------------------------------------

(deftest ^:integration chat-with-system-message-test
  (testing "Chat with system message"
    (let [provider (openai-provider)
          messages [{:role :system :content "You always respond with exactly 'ACKNOWLEDGED'."}
                    {:role :user :content "Hello!"}]
          result (api/chat provider messages {:max-tokens 20})]
      (is (map? result))
      (is (str/includes? (str/upper-case (:content result)) "ACKNOWLEDGED")))))

(deftest ^:integration chat-empty-opts-test
  (testing "Chat with no options"
    (let [provider (openai-provider)
          messages [{:role :user :content "Reply with 'OK' only."}]
          result (api/chat provider messages)]
      (is (map? result))
      (is (string? (:content result))))))

(deftest ^:integration chat-two-arity-test
  (testing "Chat with two-arity call"
    (let [provider (anthropic-provider)
          messages [{:role :user :content "Say 'yes'."}]
          result (api/chat provider messages)]
      (is (map? result))
      (is (string? (:content result))))))
