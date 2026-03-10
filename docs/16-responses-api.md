# Responses API (Stateful Conversations)

OpenAI's Responses API (`/v1/responses`) provides server-side conversation state management and built-in tools. Unlike the Chat Completions API where you manage message history client-side, the Responses API stores conversation state on OpenAI's servers.

## Overview

| Feature | Chat Completions | Responses API |
|---------|------------------|---------------|
| State management | Client-side | Server-side |
| Multi-turn | Send full history | Use `previous-response-id` |
| Built-in tools | No | Yes (web search, code interpreter) |
| Message format | Messages array | Input string/items |
| Storage | Not stored | Optional storage |

## Why Use the Responses API?

| Use Case | Description |
|----------|-------------|
| **Simplified Multi-turn** | No need to manage conversation history |
| **Built-in Tools** | Web search, file search, code execution |
| **Server-side State** | Responses stored and retrievable |
| **Reduced Bandwidth** | Send only new input, not full history |
| **Audit Trail** | Retrieve past responses by ID |

## The Responses Protocol

```clojure
(defprotocol ResponsesProvider
  (create-response [this input options])
  (create-response-stream [this input options])
  (get-response [this response-id])
  (delete-response [this response-id]))
```

## Basic Usage

### Simple Response

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

;; Create provider
(def provider (resp/create-provider {}))

;; Simple text input
(responses/create-response provider "What is the capital of France?"
  {:model "gpt-4o"})
;; => {:id "resp_abc123"
;;     :status "completed"
;;     :output [...]
;;     :usage {:input-tokens 10 :output-tokens 15 :total-tokens 25}
;;     :model "gpt-4o"
;;     :created-at 1699000000}
```

### With System Instructions

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

(responses/create-response provider "Hello!"
  {:model "gpt-4o"
   :instructions "You are a helpful pirate. Respond in pirate speak."})
;; Output will be in pirate speak
```

## REPL Examples

### Basic Response

Copy and paste this entire block:

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Create a simple response
(def result (responses/create-response provider
              "Explain quantum computing in one sentence."
              {:model "gpt-4o"}))

(println "Response ID:" (:id result))
(println "Status:" (:status result))
(println "Output:" (:output result))
(println "Tokens used:" (:total-tokens (:usage result)))
```

### Multi-turn Conversation

The key feature of the Responses API - continuing conversations without resending history:

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; First turn - introduce yourself
(def r1 (responses/create-response provider
          "My name is Alice and I'm learning Clojure."
          {:model "gpt-4o"
           :store true}))  ; Store for multi-turn

(println "Turn 1 ID:" (:id r1))

;; Second turn - reference previous context
(def r2 (responses/create-response provider
          "What programming language am I learning?"
          {:model "gpt-4o"
           :previous-response-id (:id r1)}))

(println "Turn 2 output:" (:output r2))
;; The model remembers you said "Clojure"

;; Third turn - continue the conversation
(def r3 (responses/create-response provider
          "What's a good first project for a beginner?"
          {:model "gpt-4o"
           :previous-response-id (:id r2)}))

(println "Turn 3 output:" (:output r3))
;; Context from all previous turns is preserved
```

### Building a Conversation Helper

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

(defn create-conversation
  "Creates a new conversation with optional system instructions."
  [instructions]
  (atom {:provider provider
         :instructions instructions
         :last-response-id nil}))

(defn chat!
  "Sends a message and updates conversation state."
  [conversation message]
  (let [{:keys [provider instructions last-response-id]} @conversation
        response (responses/create-response provider message
                   (cond-> {:model "gpt-4o"
                            :store true}
                     instructions (assoc :instructions instructions)
                     last-response-id (assoc :previous-response-id last-response-id)))]
    (swap! conversation assoc :last-response-id (:id response))
    response))

(defn extract-text
  "Extracts text content from response output."
  [response]
  (->> (:output response)
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (clojure.string/join "")))

;; === Usage ===

(def conv (create-conversation "You are a helpful coding assistant."))

(println (extract-text (chat! conv "What is a closure in programming?")))
(println (extract-text (chat! conv "Can you show me an example in JavaScript?")))
(println (extract-text (chat! conv "How about in Clojure?")))
;; Each response builds on the previous context
```

## Built-in Tools

The Responses API includes powerful built-in tools:

| Tool | Description |
|------|-------------|
| `:web_search` | Search the web for current information |
| `:file_search` | Search through uploaded files |
| `:code_interpreter` | Execute Python code |

### Web Search

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Enable web search
(def result (responses/create-response provider
              "What are the latest developments in AI this week?"
              {:model "gpt-4o"
               :tools [:web_search]}))

(println "Output:" (:output result))
;; Response includes current information from the web
```

### Code Interpreter

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Enable code execution
(def result (responses/create-response provider
              "Calculate the first 20 Fibonacci numbers and show them as a chart."
              {:model "gpt-4o"
               :tools [:code_interpreter]}))

;; The model can execute Python code and return results
(println "Output:" (:output result))
```

### Multiple Tools

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Use multiple built-in tools
(def result (responses/create-response provider
              "Search for current Bitcoin price and create a simple analysis."
              {:model "gpt-4o"
               :tools [:web_search :code_interpreter]}))

(println "Output:" (:output result))
```

## Streaming Responses

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])
(require '[clojure.core.async :as async])

(def provider (resp/create-provider {}))

;; Stream a response
(let [ch (responses/create-response-stream provider
           "Write a short poem about functional programming."
           {:model "gpt-4o"})]
  (loop []
    (when-let [event (async/<!! ch)]
      (case (:type event)
        :delta (print (:content event))
        :complete (println "\n\nDone! Tokens:" (:total-tokens (:usage event)))
        :error (println "Error:" (:error event)))
      (recur))))
```

### Streaming with Multi-turn

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])
(require '[clojure.core.async :as async])

(def provider (resp/create-provider {}))

;; First turn (non-streaming to get ID)
(def r1 (responses/create-response provider
          "Let's discuss the history of Lisp."
          {:model "gpt-4o" :store true}))

;; Second turn with streaming
(let [ch (responses/create-response-stream provider
           "What was special about Lisp machines?"
           {:model "gpt-4o"
            :previous-response-id (:id r1)})]
  (loop []
    (when-let [event (async/<!! ch)]
      (when (= :delta (:type event))
        (print (:content event)))
      (recur)))
  (println))
```

## Managing Stored Responses

### Retrieve a Response

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Create and store a response
(def r1 (responses/create-response provider "Remember: the password is banana123"
          {:model "gpt-4o" :store true}))

(println "Created response:" (:id r1))

;; Later, retrieve it
(def retrieved (responses/get-response provider (:id r1)))

(println "Retrieved:" (:output retrieved))
```

### Delete a Response

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

(def provider (resp/create-provider {}))

;; Create a stored response
(def r1 (responses/create-response provider "Sensitive information here"
          {:model "gpt-4o" :store true}))

;; Delete it when no longer needed
(responses/delete-response provider (:id r1))
;; => true

;; Attempting to retrieve will now fail
(try
  (responses/get-response provider (:id r1))
  (catch Exception e
    (println "Response deleted successfully")))
```

## Response Structure

### Output Format

```clojure
{:id "resp_abc123"
 :status "completed"
 :output [{:type "message"
           :role "assistant"
           :content [{:type "output_text"
                      :text "The response text..."}]}]
 :usage {:input-tokens 10
         :output-tokens 25
         :total-tokens 35}
 :model "gpt-4o"
 :created-at 1699000000}
```

### Extracting Text Content

```clojure
(defn extract-text
  "Extracts all text content from a response."
  [response]
  (->> (:output response)
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (clojure.string/join "")))

;; Usage
(def result (responses/create-response provider "Hello" {:model "gpt-4o"}))
(println (extract-text result))
```

## Options Reference

```clojure
{:model "gpt-4o"                      ; Model to use
 :instructions "..."                   ; System instructions
 :previous-response-id "resp_123"      ; Continue conversation
 :tools [:web_search :code_interpreter] ; Built-in tools
 :tool-choice "auto"                   ; Tool selection strategy
 :temperature 0.7                      ; Sampling temperature
 :max-output-tokens 4096               ; Max response tokens
 :top-p 1.0                            ; Nucleus sampling
 :store true}                          ; Store response for retrieval
```

### Tool Choice Options

| Value | Description |
|-------|-------------|
| `"auto"` | Model decides when to use tools |
| `"required"` | Model must use at least one tool |
| `"none"` | Disable tool use for this request |

## Complete Application: Research Assistant

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

;; === Research Assistant with Web Search ===

(def provider (resp/create-provider {}))

(defn create-research-session
  "Creates a research session with web search enabled."
  [topic]
  (let [instructions (str "You are a research assistant helping to investigate: " topic
                          ". Use web search to find current, accurate information. "
                          "Cite your sources when possible.")]
    (atom {:topic topic
           :instructions instructions
           :history []
           :last-id nil})))

(defn research!
  "Asks a research question in the session."
  [session question]
  (let [{:keys [instructions last-id]} @session
        response (responses/create-response provider question
                   (cond-> {:model "gpt-4o"
                            :instructions instructions
                            :tools [:web_search]
                            :store true}
                     last-id (assoc :previous-response-id last-id)))
        text (extract-text response)]
    (swap! session (fn [s]
                     (-> s
                         (assoc :last-id (:id response))
                         (update :history conj {:question question
                                                :answer text
                                                :id (:id response)}))))
    text))

(defn get-history
  "Returns the research session history."
  [session]
  (:history @session))

(defn extract-text [response]
  (->> (:output response)
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (clojure.string/join "")))

;; === Usage ===

(def session (create-research-session "Quantum Computing Advances 2024"))

(println (research! session "What are the latest quantum computing breakthroughs?"))
(println (research! session "Which companies are leading in this field?"))
(println (research! session "What are the practical applications being developed?"))

;; Review history
(doseq [{:keys [question answer]} (get-history session)]
  (println "\nQ:" question)
  (println "A:" (subs answer 0 (min 200 (count answer))) "..."))
```

## Complete Application: Code Tutor

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])

;; === Interactive Code Tutor ===

(def provider (resp/create-provider {}))

(defn create-tutor-session
  "Creates a coding tutor session."
  [language]
  (atom {:language language
         :instructions (str "You are an expert " language " tutor. "
                           "Explain concepts clearly with examples. "
                           "Use code_interpreter to demonstrate and verify code. "
                           "Ask follow-up questions to check understanding.")
         :last-id nil}))

(defn ask-tutor!
  "Asks the tutor a question or requests an exercise."
  [session question]
  (let [{:keys [instructions last-id]} @session
        response (responses/create-response provider question
                   (cond-> {:model "gpt-4o"
                            :instructions instructions
                            :tools [:code_interpreter]
                            :store true}
                     last-id (assoc :previous-response-id last-id)))]
    (swap! session assoc :last-id (:id response))
    (extract-text response)))

(defn extract-text [response]
  (->> (:output response)
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (clojure.string/join "")))

;; === Usage ===

(def python-tutor (create-tutor-session "Python"))

(println (ask-tutor! python-tutor "Explain list comprehensions with examples."))
(println (ask-tutor! python-tutor "Show me a more complex example with filtering."))
(println (ask-tutor! python-tutor "Give me a practice exercise."))
(println (ask-tutor! python-tutor "Here's my solution: [x*2 for x in range(10) if x % 2 == 0]"))
```

## Complete Application: Persistent Chat Bot

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])
(require '[clojure.edn :as edn])

;; === Persistent Chat Bot ===

(def provider (resp/create-provider {}))

(defn save-session!
  "Saves session state to a file."
  [session file-path]
  (spit file-path (pr-str @session)))

(defn load-session
  "Loads session state from a file."
  [file-path]
  (atom (edn/read-string (slurp file-path))))

(defn create-bot
  "Creates a new chat bot with personality."
  [name personality]
  (atom {:name name
         :personality personality
         :instructions (str "You are " name ". " personality)
         :conversation-ids []
         :last-id nil}))

(defn chat-with-bot!
  "Sends a message to the bot."
  [bot message]
  (let [{:keys [instructions last-id]} @bot
        response (responses/create-response provider message
                   (cond-> {:model "gpt-4o"
                            :instructions instructions
                            :store true}
                     last-id (assoc :previous-response-id last-id)))]
    (swap! bot (fn [b]
                 (-> b
                     (assoc :last-id (:id response))
                     (update :conversation-ids conj (:id response)))))
    (extract-text response)))

(defn reset-conversation!
  "Resets the conversation while keeping the bot personality."
  [bot]
  (swap! bot assoc :last-id nil :conversation-ids []))

(defn extract-text [response]
  (->> (:output response)
       (filter #(= "message" (:type %)))
       (mapcat :content)
       (filter #(= "output_text" (:type %)))
       (map :text)
       (clojure.string/join "")))

;; === Usage ===

(def assistant (create-bot "Ada"
                 "A friendly AI assistant who loves explaining technology
                  in simple terms and uses occasional humor."))

(println (chat-with-bot! assistant "Hi! Who are you?"))
(println (chat-with-bot! assistant "What's the difference between AI and ML?"))
(println (chat-with-bot! assistant "Can you give me a simple analogy?"))

;; Save session for later
(save-session! assistant "/tmp/ada-session.edn")

;; Later, restore session
;; (def restored-assistant (load-session "/tmp/ada-session.edn"))
;; (println (chat-with-bot! restored-assistant "What were we talking about?"))
```

## Error Handling

```clojure
(require '[llm-clj.responses.core :as responses])
(require '[llm-clj.responses.openai :as resp])
(require '[llm-clj.errors :as errors])

(def provider (resp/create-provider {}))

(defn safe-create-response [input options]
  (try
    {:success true
     :result (responses/create-response provider input options)}
    (catch Exception e
      (cond
        (errors/authentication-error? e)
        {:success false
         :error :auth-failed
         :message "Check your API key"}

        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        (errors/validation-error? e)
        {:success false
         :error :invalid-request
         :message (ex-message e)}

        :else
        {:success false
         :error :unknown
         :message (ex-message e)}))))

;; Usage
(let [result (safe-create-response "Hello" {:model "gpt-4o"})]
  (if (:success result)
    (println "Response:" (extract-text (:result result)))
    (println "Error:" (:error result) "-" (:message result))))
```

## Responses API vs Chat Completions

### When to Use Responses API

- Multi-turn conversations without managing history
- Need built-in web search or code execution
- Want server-side conversation storage
- Building apps with conversation audit trails

### When to Use Chat Completions

- Full control over message history
- Custom tool implementations
- Cross-provider compatibility (Anthropic)
- Simpler single-turn requests

### Migration Example

```clojure
;; Chat Completions approach
(def messages (atom [{:role :system :content "You are helpful."}]))

(defn chat-completions-turn [provider user-message]
  (swap! messages conj {:role :user :content user-message})
  (let [response (llm/chat-completion provider @messages {})]
    (swap! messages conj {:role :assistant :content (:content response)})
    (:content response)))

;; Responses API approach (simpler!)
(def last-response-id (atom nil))

(defn responses-turn [provider user-message]
  (let [response (responses/create-response provider user-message
                   (cond-> {:model "gpt-4o"
                            :instructions "You are helpful."
                            :store true}
                     @last-response-id (assoc :previous-response-id @last-response-id)))]
    (reset! last-response-id (:id response))
    (extract-text response)))
```

## Best Practices

1. **Use `:store true`** for multi-turn conversations
2. **Clean up stored responses** when conversations end
3. **Handle streaming for long responses** to improve UX
4. **Combine tools thoughtfully** - web search + code interpreter is powerful
5. **Set appropriate instructions** to guide the model's behavior
6. **Track response IDs** for debugging and audit trails

## Limitations

- **OpenAI only** - Not available for Anthropic
- **Storage limits** - Stored responses may expire
- **Tool availability** - Some tools may have usage limits
- **Response format** - Different from Chat Completions responses

