(ns examples.tracing-agent
  "Example demonstrating tracing in an agentic application.

   This example simulates a research agent that:
   1. Plans the research approach
   2. Executes multiple tool calls to gather information
   3. Synthesizes the results into a final answer

   All LLM calls are traced under a single parent trace, producing
   structured JSON output that can be ingested by log aggregation systems.

   Run with: lein run -m examples.tracing-agent

   The output will be multi-line JSON (JSONL format) showing:
   - span_start events when operations begin
   - span_end events when operations complete
   - Nested spans sharing the same trace_id
   - Duration and status information"
  (:require [llm-clj.api :as api]
            [llm-clj.providers.openai :as openai]
            [llm-clj.providers.anthropic :as anthropic]
            [llm-clj.tools :as tools]
            [llm-clj.tracing.core :as tracing]
            [llm-clj.tracing.config :as tracing-config]
            [llm-clj.tracing.span :as span]
            ;; Load backends so they register themselves
            [llm-clj.tracing.backends.noop]
            [llm-clj.tracing.backends.stdout]
            [llm-clj.tracing.backends.json]))

;; =============================================================================
;; Tool Definitions
;; =============================================================================

(defn search-web [{:keys [query]}]
  ;; Simulated web search
  (Thread/sleep 100) ; Simulate network latency
  {:results [{:title "Climate Change Overview"
              :snippet "Global temperatures have risen by 1.1C since pre-industrial times..."}
             {:title "Recent Climate Data"
              :snippet "2023 was the warmest year on record..."}]})

(defn search-database [{:keys [query category]}]
  ;; Simulated database search
  (Thread/sleep 50)
  {:records [{:id 1 :data "Historical temperature records show..."}
             {:id 2 :data "CO2 levels have increased by 50% since 1850..."}]})

(defn calculate [{:keys [expression]}]
  ;; Simple calculator
  (try
    {:result (eval (read-string expression))}
    (catch Exception e
      {:error (.getMessage e)})))

(def web-search-tool
  (tools/define-tool
    "search_web"
    "Search the web for current information on a topic"
    [:map
     [:query {:description "The search query"} :string]]
    search-web))

(def database-tool
  (tools/define-tool
    "search_database"
    "Search the internal knowledge database"
    [:map
     [:query {:description "The search query"} :string]
     [:category {:description "Category to search in" :optional true} :string]]
    search-database))

(def calculator-tool
  (tools/define-tool
    "calculate"
    "Perform mathematical calculations"
    [:map
     [:expression {:description "A mathematical expression to evaluate"} :string]]
    calculate))

;; =============================================================================
;; Mock Provider for Demo (when no API keys available)
;; =============================================================================

(defrecord MockProvider []
  llm-clj.core/LLMProvider
  (chat-completion [_ messages opts]
    (Thread/sleep 200) ; Simulate API latency
    (let [last-msg (last messages)
          has-tools? (seq (:tools opts))]
      (cond
        ;; If this is the first message with tools, request tool calls
        (and has-tools?
             (= :user (:role last-msg))
             (re-find #"climate|weather|temperature" (str (:content last-msg))))
        {:role :assistant
         :content nil
         :tool-calls [{:id "call_1"
                       :type :function
                       :function {:name "search_web"
                                  :arguments "{\"query\": \"climate change latest data 2024\"}"}}
                      {:id "call_2"
                       :type :function
                       :function {:name "search_database"
                                  :arguments "{\"query\": \"historical temperature\", \"category\": \"climate\"}"}}]
         :usage {:input-tokens 150 :output-tokens 50 :total-tokens 200}
         :finish-reason :tool_calls}

        ;; If we have tool results, synthesize answer
        (some #(= :tool (:role %)) messages)
        {:role :assistant
         :content "Based on my research, I found that global temperatures have risen by 1.1C since pre-industrial times. 2023 was recorded as the warmest year on record. Historical data shows CO2 levels have increased by 50% since 1850, which correlates with the observed temperature increase."
         :usage {:input-tokens 300 :output-tokens 80 :total-tokens 380}
         :finish-reason :stop}

        ;; Default response
        :else
        {:role :assistant
         :content "I can help you research that topic. What specific information are you looking for?"
         :usage {:input-tokens 50 :output-tokens 20 :total-tokens 70}
         :finish-reason :stop})))

  (chat-completion-stream [this messages opts]
    (throw (ex-info "Streaming not implemented in mock" {}))))

(defn create-mock-provider []
  (->MockProvider))

;; =============================================================================
;; Agent Implementation
;; =============================================================================

(defn research-agent
  "A research agent that plans, gathers information, and synthesizes results.

   This function demonstrates how tracing works in an agentic application:
   - The entire agent turn is wrapped in a parent trace
   - Each LLM call and tool execution creates child spans
   - All spans share the same trace_id for correlation"
  [provider query]
  (println "\n" (apply str (repeat 60 "=")) "\n")
  (println "Research Agent - Processing query:" query)
  (println (apply str (repeat 60 "=")) "\n")

  ;; Wrap the entire agent operation in a trace
  (tracing/with-trace [trace "agent.research_turn"
                       {"agent.name" "research-agent"
                        "agent.query" query
                        "agent.tools_available" 3}]

    ;; Step 1: Initial planning/understanding phase
    (println "\n[Agent] Step 1: Understanding the query...")
    (tracing/with-span [_ "agent.plan"
                        {"agent.step" "planning"}]
      (Thread/sleep 50)) ; Simulate thinking time

    ;; Step 2: Execute the main chat with tools
    (println "[Agent] Step 2: Gathering information via tools...")
    (let [messages [{:role :system
                     :content "You are a research assistant. Use the available tools to gather information and provide comprehensive answers."}
                    {:role :user
                     :content query}]
          result (api/chat provider messages
                           {:tools [web-search-tool database-tool calculator-tool]
                            :max-iterations 3})]

      ;; Step 3: Log the final synthesis
      (println "[Agent] Step 3: Synthesizing final response...")
      (tracing/with-span [_ "agent.synthesize"
                          {"agent.step" "synthesis"
                           "response.length" (count (:content result ""))}]
        (Thread/sleep 30))

      (println "\n[Agent] Final Response:")
      (println (:content result))
      result)))

;; =============================================================================
;; Main Entry Point
;; =============================================================================

(defn -main [& args]
  (println "\n")
  (println "============================================================")
  (println "  Tracing Agent Example - Multi-line JSON Output")
  (println "============================================================")
  (println "\nThis example demonstrates distributed tracing in an agentic")
  (println "application. All LLM calls and tool executions are traced")
  (println "under a single parent trace for correlation.\n")

  ;; Configure tracing with JSON backend
  (tracing/configure! {:enabled true
                       :backend :json
                       :sample-rate 1.0
                       :capture-tool-args true})

  (println "Tracing configured:")
  (println "  - Backend: JSON (multi-line JSONL format)")
  (println "  - Sample rate: 100%")
  (println "  - Tool args capture: enabled")
  (println "\n--- JSON Trace Output Begins Below ---\n")

  ;; Try to use a real provider, fall back to mock
  (let [provider (or (try
                       (when (System/getenv "OPENAI_API_KEY")
                         (println "Using OpenAI provider")
                         (openai/create-provider {:model "gpt-4o-mini"}))
                       (catch Exception _ nil))
                     (try
                       (when (System/getenv "ANTHROPIC_API_KEY")
                         (println "Using Anthropic provider")
                         (anthropic/create-provider {:model "claude-3-haiku-20240307"}))
                       (catch Exception _ nil))
                     (do
                       (println "No API keys found - using mock provider for demo")
                       (create-mock-provider)))]

    ;; Run the research agent
    (research-agent provider "What are the latest findings on climate change and global temperature trends?"))

  (println "\n--- JSON Trace Output Ends ---\n")

  (println "============================================================")
  (println "  Trace Analysis")
  (println "============================================================")
  (println "\nThe JSON output above shows:")
  (println "  1. span_start events when operations begin")
  (println "  2. span_end events with duration and status")
  (println "  3. All spans share the same trace_id")
  (println "  4. Child spans reference their parent_id")
  (println "\nYou can pipe this output to jq for pretty printing:")
  (println "  lein run -m examples.tracing-agent 2>&1 | grep '{\"' | jq .")
  (println)

  (shutdown-agents))

;; =============================================================================
;; Alternative: Run with stdout backend for human-readable output
;; =============================================================================

(defn run-with-stdout-tracing []
  (println "\n=== Running with STDOUT tracing (human-readable) ===\n")

  (tracing/configure! {:enabled true
                       :backend :stdout
                       :sample-rate 1.0})

  (let [provider (create-mock-provider)]
    (research-agent provider "Explain quantum computing basics")))

;; Uncomment to run with stdout tracing instead:
;; (run-with-stdout-tracing)
