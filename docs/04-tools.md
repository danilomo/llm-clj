# Tools and Function Calling

Tools allow LLMs to call functions you define, enabling them to perform actions, retrieve information, and interact with external systems.

## How Tools Work

1. You define tools with names, descriptions, and parameter schemas
2. You send these tools to the LLM along with your messages
3. The LLM may respond with a request to call one or more tools
4. You execute the tools and return results to the LLM
5. The LLM uses the results to form its final response

## Defining Tools

Use `define-tool` to create tool definitions:

```clojure
(require '[llm-clj.tools :as tools])

(def get-weather
  (tools/define-tool
    "get_weather"                                    ; name
    "Gets the current weather for a location"        ; description
    [:map                                            ; parameter schema (Malli)
     [:location :string]
     [:unit {:optional true} [:enum "celsius" "fahrenheit"]]]
    (fn [{:keys [location unit]}]                    ; implementation
      {:temperature 22
       :unit (or unit "celsius")
       :condition "sunny"
       :location location})))
```

### Tool Structure

A defined tool is a map:

```clojure
{:name "get_weather"
 :description "Gets the current weather for a location"
 :parameters [:map [:location :string] ...]
 :f #<function>
 :strict false}
```

### Parameter Schemas

Use Malli schemas to define parameters:

```clojure
;; Simple string parameter
[:map [:query :string]]

;; Multiple parameters with types
[:map
 [:city :string]
 [:country :string]
 [:include-forecast :boolean]]

;; With optional parameters
[:map
 [:required-param :string]
 [:optional-param {:optional true} :int]]

;; With enums
[:map
 [:format [:enum "json" "xml" "csv"]]]

;; Nested structures
[:map
 [:user [:map
         [:name :string]
         [:age :int]]]]
```

## Using Tools with Chat Completion

### Automatic Tool Execution (Recommended)

The easiest way to use tools is with `chat-with-tools`, which automatically handles the tool execution loop:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.tools :as tools])

(def provider (openai/create-provider {}))

;; Define tools
(def calculator
  (tools/define-tool
    "calculate"
    "Performs basic arithmetic calculations"
    [:map [:expression :string]]
    (fn [{:keys [expression]}]
      {:result (eval (read-string expression))})))

(def weather-tool
  (tools/define-tool
    "get_weather"
    "Gets current weather for a location"
    [:map [:location :string]]
    (fn [{:keys [location]}]
      {:location location :temperature 72 :conditions "sunny"})))

;; chat-with-tools automatically:
;; 1. Formats tools for the provider
;; 2. Executes any tool calls
;; 3. Continues until the model returns a final response
(def response
  (tools/chat-with-tools provider
    [{:role :user :content "What's 15 * 7? And what's the weather in Tokyo?"}]
    [calculator weather-tool]
    {:max-tokens 200}))

(:content response)
;; => "15 * 7 = 105. The weather in Tokyo is 72°F and sunny."
```

### chat-with-tools Options

```clojure
(tools/chat-with-tools provider messages tools
  {:max-iterations 10        ; Safety limit (default 10)
   :on-tool-call callback    ; Optional (fn [tool-call result]) for logging
   :max-tokens 500           ; Passed to chat-completion
   :temperature 0})          ; Passed to chat-completion
```

### Manual Tool Execution

For more control, you can manually handle tool calls:

```clojure
;; Send messages with tools
(def response
  (llm/chat-completion provider
    [{:role :user :content "What is 42 * 17?"}]
    {:tools [(tools/format-tool-openai calculator)]}))

;; Check if the model wants to call a tool
(when (:tool-calls response)
  (println "Tool calls:" (:tool-calls response)))
```

### Complete Manual Tool Execution Loop

For cases where you need full control over the tool execution process (custom error handling, logging, etc.), you can implement the loop manually:

```clojure
(defn manual-tool-loop [provider messages my-tools]
  (let [formatted-tools (mapv tools/format-tool-openai my-tools)
        response (llm/chat-completion provider messages {:tools formatted-tools})]
    (if-not (:tool-calls response)
      response
      (let [tool-results (mapv #(tools/execute-tool-call my-tools %) (:tool-calls response))
            new-messages (vec (concat
                                messages
                                [{:role :assistant
                                  :content (:content response)
                                  :tool-calls (:tool-calls response)}]
                                tool-results))]
        ;; Recursively continue with tool results
        (recur provider new-messages my-tools)))))
```

**Note:** For most use cases, prefer `tools/chat-with-tools` which handles this automatically with built-in safety limits and error handling.

## REPL Example: Complete Tool Flow

Copy and paste this entire block:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.tools :as tools])
(require '[cheshire.core :as json])

(def provider (openai/create-provider {}))

;; === Define Tools ===

(def search-tool
  (tools/define-tool
    "search_knowledge_base"
    "Searches the knowledge base for information"
    [:map [:query :string]]
    (fn [{:keys [query]}]
      ;; Simulated search results
      {:results [{:title "Clojure Documentation"
                  :snippet "Clojure is a dynamic, functional programming language..."}
                 {:title "Getting Started Guide"
                  :snippet "To begin with Clojure, first install Java..."}]
       :total 2})))

(def create-task-tool
  (tools/define-tool
    "create_task"
    "Creates a new task in the task management system"
    [:map
     [:title :string]
     [:description {:optional true} :string]
     [:priority [:enum "low" "medium" "high"]]]
    (fn [{:keys [title description priority]}]
      {:success true
       :task-id (str "TASK-" (rand-int 10000))
       :title title
       :priority priority})))

(def available-tools [search-tool create-task-tool])

;; === Tool Execution Loop ===

(defn execute-tools [response tools]
  (mapv #(tools/execute-tool-call tools %) (:tool-calls response)))

(defn chat-loop [initial-message]
  (let [formatted-tools (mapv tools/format-tool-openai available-tools)]
    (loop [messages [{:role :user :content initial-message}]
           iterations 0]
      (when (< iterations 5)  ; Safety limit
        (let [response (llm/chat-completion provider messages {:tools formatted-tools})]

          (println "\n--- Iteration" iterations "---")
          (println "Response content:" (:content response))
          (println "Tool calls:" (count (:tool-calls response)))

          (if (:tool-calls response)
            (let [;; Execute tools
                  tool-results (execute-tools response available-tools)
                  _ (println "Tool results:" tool-results)

                  ;; Build new messages
                  assistant-msg {:role :assistant
                                 :content (:content response)
                                 :tool-calls (:tool-calls response)}
                  new-messages (vec (concat messages [assistant-msg] tool-results))]

              ;; Continue with tool results
              (recur new-messages (inc iterations)))

            ;; No tool calls, return final response
            response))))))

;; === Test It ===

(chat-loop "Search for information about Clojure and then create a high-priority task to learn it.")

;; Expected flow:
;; 1. Model calls search_knowledge_base
;; 2. We return search results
;; 3. Model calls create_task
;; 4. We return task creation confirmation
;; 5. Model provides final response summarizing actions
```

## Provider-Specific Formatting

### OpenAI Format

```clojure
(tools/format-tool-openai weather-tool)
;; =>
;; {:type "function"
;;  :function {:name "get_weather"
;;             :description "Gets current weather for a location"
;;             :parameters {:type "object"
;;                          :properties {:location {:type "string"}}
;;                          :required ["location"]}}}
```

### Anthropic Format

```clojure
(tools/format-tool-anthropic weather-tool)
;; =>
;; {:name "get_weather"
;;  :description "Gets current weather for a location"
;;  :input_schema {:type "object"
;;                 :properties {:location {:type "string"}}
;;                 :required ["location"]}}
```

## Strict Tool Mode

Enable strict schema validation for more reliable tool calls:

```clojure
(def strict-tool
  (tools/define-tool
    "get_user"
    "Gets user information by ID"
    [:map [:user-id :string]]
    get-user-fn
    {:strict true}))  ; Enable strict mode

;; When formatted, strict tools include validation flags
(tools/format-tool-openai strict-tool)
;; => {:type "function"
;;     :function {:name "get_user"
;;                :description "Gets user information by ID"
;;                :parameters {:type "object"
;;                             :properties {:user-id {:type "string"}}
;;                             :required ["user-id"]
;;                             :additionalProperties false}  ; Added by strict mode
;;                :strict true}}                              ; Strict flag
```

## Tool Choice

Control when tools are used:

```clojure
;; Let model decide
{:tools formatted-tools :tool-choice "auto"}

;; Force tool use
{:tools formatted-tools :tool-choice "required"}

;; Force specific tool (OpenAI)
{:tools formatted-tools :tool-choice {:type "function" :function {:name "get_weather"}}}

;; Disable tools for this call
{:tools formatted-tools :tool-choice "none"}
```

## Tools with Anthropic

Anthropic uses the same pattern with slightly different formatting:

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

(def provider (anthropic/create-provider {}))

(def response
  (llm/chat-completion provider
    [{:role :user :content "What's the weather in Paris?"}]
    {:tools [(tools/format-tool-anthropic weather-tool)]}))

;; Tool calls are normalized to the same format
(:tool-calls response)
;; => [{:id "toolu_123"
;;      :type "function"
;;      :function {:name "get_weather"
;;                 :arguments "{\"location\":\"Paris\"}"}}]
```

## Error Handling in Tools

```clojure
(def safe-tool
  (tools/define-tool
    "risky_operation"
    "Does something that might fail"
    [:map [:input :string]]
    (fn [{:keys [input]}]
      (try
        ;; Your logic here
        {:success true :result (process input)}
        (catch Exception e
          {:success false :error (.getMessage e)})))))

;; Always return valid JSON from tool functions
;; The LLM can handle error messages gracefully
```

## Tool Result Message Format

When returning tool results, use this format:

```clojure
{:role :tool
 :tool-call-id "call_abc123"  ; Must match the tool call ID
 :name "get_weather"           ; Tool name
 :content "{...}"              ; JSON string result
}
```

The `execute-tool-call` function handles this automatically.

## Complete Multi-Tool Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.tools :as tools])

(def provider (openai/create-provider {}))

;; Database simulation
(def db (atom {:users {"u1" {:name "Alice" :email "alice@example.com"}
                       "u2" {:name "Bob" :email "bob@example.com"}}
               :orders []}))

;; Define multiple tools
(def get-user-tool
  (tools/define-tool
    "get_user"
    "Gets user information by ID"
    [:map [:user-id :string]]
    (fn [{:keys [user-id]}]
      (if-let [user (get-in @db [:users user-id])]
        {:found true :user user}
        {:found false :error "User not found"}))))

(def create-order-tool
  (tools/define-tool
    "create_order"
    "Creates an order for a user"
    [:map
     [:user-id :string]
     [:product :string]
     [:quantity :int]]
    (fn [{:keys [user-id product quantity]}]
      (let [order {:id (str "ORD-" (rand-int 10000))
                   :user-id user-id
                   :product product
                   :quantity quantity}]
        (swap! db update :orders conj order)
        {:success true :order order}))))

(def list-orders-tool
  (tools/define-tool
    "list_orders"
    "Lists all orders, optionally filtered by user"
    [:map [:user-id {:optional true} :string]]
    (fn [{:keys [user-id]}]
      (let [orders (:orders @db)
            filtered (if user-id
                       (filter #(= user-id (:user-id %)) orders)
                       orders)]
        {:orders filtered :count (count filtered)}))))

(def all-tools [get-user-tool create-order-tool list-orders-tool])

;; Interactive session
(defn agent-chat [user-input]
  (let [formatted-tools (mapv tools/format-tool-openai all-tools)]
    (loop [messages [{:role :system
                      :content "You are a helpful assistant that can manage users and orders."}
                     {:role :user :content user-input}]
           max-iterations 5]
      (when (pos? max-iterations)
        (let [response (llm/chat-completion provider messages
                                            {:tools formatted-tools
                                             :temperature 0})]
          (if (:tool-calls response)
            (let [results (mapv #(tools/execute-tool-call all-tools %) (:tool-calls response))
                  new-messages (vec (concat messages
                                            [{:role :assistant
                                              :content (:content response)
                                              :tool-calls (:tool-calls response)}]
                                            results))]
              (recur new-messages (dec max-iterations)))
            (:content response)))))))

;; Test
(agent-chat "Look up user u1 and create an order for 3 widgets for them")
;; => "I found Alice (alice@example.com) and created order ORD-4521 for 3 widgets."
```

