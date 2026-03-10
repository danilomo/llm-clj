(ns llm-clj.strict-tool-integration-test
  (:require [clojure.test :refer [deftest is testing use-fixtures]]
            [clojure.string :as str]
            [clojure.java.io :as io]
            [llm-clj.core :as core]
            [llm-clj.tools :as tools]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]))

;; ---------------------------------------------------------------------------
;; .env loader
;; ---------------------------------------------------------------------------

(defn- load-env! [path]
  (let [f (io/file path)]
    (when (.exists f)
      (doseq [line (str/split-lines (slurp f))]
        (let [line (str/trim line)]
          (when (and (seq line) (not (str/starts-with? line "#")))
            (let [[k v] (str/split line #"=" 2)]
              (when (and k v)
                (System/setProperty (str/trim k) (str/trim v))))))))))

(defn- env [key]
  (or (System/getenv key) (System/getProperty key)))

(defn- load-dotenv! []
  (load-env! ".env")
  (load-env! (str (System/getProperty "user.dir") "/.env")))

;; ---------------------------------------------------------------------------
;; Provider factories (cheapest models)
;; ---------------------------------------------------------------------------

(defn- openai-provider []
  (let [key (env "OPENAI_API_KEY")]
    (when-not key (throw (ex-info "OPENAI_API_KEY not found" {})))
    (openai/create-provider {:api-key key :model "gpt-4o-mini"})))

;; ---------------------------------------------------------------------------
;; Fixture
;; ---------------------------------------------------------------------------

(use-fixtures :once (fn [f] (load-dotenv!) (f)))

;; ---------------------------------------------------------------------------
;; Sample tool
;; ---------------------------------------------------------------------------

(def get-weather-fn
  (fn [{:keys [location]}]
    {:temperature 22 :condition "sunny" :location location}))

(def weather-tool
  (tools/define-tool
    "get_weather"
    "Gets the current weather for a given location"
    [:map [:location :string]]
    get-weather-fn))

(def strict-weather-tool
  (tools/define-tool
    "get_weather"
    "Gets the current weather for a given location"
    [:map [:location :string]]
    get-weather-fn
    {:strict true}))

;; ---------------------------------------------------------------------------
;; Unit tests for define-tool and format functions
;; ---------------------------------------------------------------------------

(deftest define-tool-defaults-strict-false-test
  (testing "define-tool without opts defaults :strict to false"
    (is (false? (:strict weather-tool)))))

(deftest define-tool-with-strict-true-test
  (testing "define-tool with {:strict true} stores :strict true"
    (is (true? (:strict strict-weather-tool)))))

(deftest format-tool-openai-without-strict-test
  (testing "format-tool-openai omits :strict key when strict is false"
    (let [formatted (tools/format-tool-openai weather-tool)]
      (is (not (contains? (:function formatted) :strict))
          "strict key should be absent when not enabled"))))

(deftest format-tool-openai-with-strict-test
  (testing "format-tool-openai includes :strict true when strict is enabled"
    (let [formatted (tools/format-tool-openai strict-weather-tool)]
      (is (true? (get-in formatted [:function :strict]))
          "strict key should be true in the function map"))))

(deftest format-tool-anthropic-without-strict-test
  (testing "format-tool-anthropic omits :strict key when strict is false"
    (let [formatted (tools/format-tool-anthropic weather-tool)]
      (is (not (contains? formatted :strict))
          "strict key should be absent when not enabled"))))

(deftest format-tool-anthropic-with-strict-test
  (testing "format-tool-anthropic includes :strict true when strict is enabled"
    (let [formatted (tools/format-tool-anthropic strict-weather-tool)]
      (is (true? (:strict formatted))
          "strict key should be true in the tool map"))))

;; ---------------------------------------------------------------------------
;; Integration tests: strict tool call roundtrip
;; ---------------------------------------------------------------------------

(deftest openai-strict-tool-call-test
  (testing "OpenAI: strict tool is called and result is returned correctly"
    (let [provider (openai-provider)
          messages [{:role :user :content "What's the weather in Paris?"}]
          result (core/chat-completion provider messages
                                       {:tools [(tools/format-tool-openai strict-weather-tool)]
                                        :tool-choice "auto"
                                        :max-tokens 100})]
      (is (map? result))
      ;; The model should have requested the tool call
      (is (= :tool_calls (:finish-reason result))
          (str "Expected :tool_calls finish reason, got: " (:finish-reason result)))
      (is (seq (:tool-calls result))
          "Response should include at least one tool call"))))

(deftest anthropic-strict-tool-call-test
  (testing "Anthropic: strict tool is called and result is returned correctly"
    ;; claude-haiku-4-5-20251001 is the cheapest model that supports strict tools
    (let [provider (anthropic/create-provider
                    {:api-key (env "ANTHROPIC_API_KEY")
                     :model "claude-haiku-4-5-20251001"})
          messages [{:role :user :content "What's the weather in Paris?"}]
          result (core/chat-completion provider messages
                                       {:tools [(tools/format-tool-anthropic strict-weather-tool)]
                                        :tool-choice {:type "auto"}
                                        :max-tokens 100})]
      (is (map? result))
      ;; Anthropic returns :tool_use when a tool call was made
      (is (= :tool_use (:finish-reason result))
          (str "Expected :tool_use finish reason, got: " (:finish-reason result)))
      (is (seq (:tool-calls result))
          "Response should include at least one tool call"))))
