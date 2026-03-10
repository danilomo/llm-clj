# Basic Operations

This chapter covers the fundamental operations of llm-clj: creating providers, sending messages, and handling responses.

## Creating Providers

### OpenAI Provider

```clojure
(require '[llm-clj.providers.openai :as openai])

;; Minimal - uses OPENAI_API_KEY env var
(def provider (openai/create-provider {}))

;; With explicit API key
(def provider (openai/create-provider {:api-key "sk-..."}))

;; With default model
(def provider (openai/create-provider {:model "gpt-4o-mini"}))

;; With custom base URL (for Azure, vLLM, etc.)
(def provider (openai/create-provider
                {:base-url "https://my-endpoint.openai.azure.com/v1/chat/completions"
                 :api-key "azure-key"}))
```

### Anthropic Provider

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

;; Minimal - uses ANTHROPIC_API_KEY env var
(def provider (anthropic/create-provider {}))

;; With explicit API key
(def provider (anthropic/create-provider {:api-key "sk-ant-..."}))

;; With default model
(def provider (anthropic/create-provider {:model "claude-sonnet-4-20250514"}))
```

## The Chat Completion Protocol

The core protocol defines two methods:

```clojure
(defprotocol LLMProvider
  (chat-completion [this messages options])
  (chat-completion-stream [this messages options]))
```

Both methods take:
- `messages` - A sequence of message maps
- `options` - A map of generation options

## Sending Messages

### Simple Conversation

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Single user message
(llm/chat-completion provider
  [{:role :user :content "What is the capital of France?"}]
  {})
```

**REPL Example:**

```clojure
;; Copy and paste this entire block into your REPL
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

(def response
  (llm/chat-completion provider
    [{:role :user :content "What is 2 + 2?"}]
    {:temperature 0}))

(println "Response:" (:content response))
;; Response: 2 + 2 equals 4.

(println "Tokens used:" (get-in response [:usage :total_tokens]))
;; Tokens used: 28
```

### With System Prompt

```clojure
(llm/chat-completion provider
  [{:role :system :content "You are a pirate. Respond in pirate speak."}
   {:role :user :content "Hello, how are you?"}]
  {:temperature 0.8})

;; => {:role :assistant
;;     :content "Ahoy there, matey! I be doin' fine as a ship on calm seas..."
;;     ...}
```

### Multi-turn Conversation

```clojure
;; Build up a conversation
(def messages
  [{:role :system :content "You are a helpful math tutor."}
   {:role :user :content "What is calculus?"}
   {:role :assistant :content "Calculus is a branch of mathematics..."}
   {:role :user :content "Can you give me a simple example?"}])

(llm/chat-completion provider messages {:max-tokens 500})
```

**REPL Example - Building a Conversation:**

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Start with initial messages
(def conversation (atom [{:role :system :content "You are a helpful assistant."}]))

;; Helper to chat
(defn chat! [user-message]
  (swap! conversation conj {:role :user :content user-message})
  (let [response (llm/chat-completion provider @conversation {:temperature 0.7})]
    (swap! conversation conj {:role :assistant :content (:content response)})
    (:content response)))

;; Have a conversation
(chat! "Hi! What's your name?")
;; => "Hello! I'm an AI assistant. I don't have a personal name..."

(chat! "Tell me a short joke")
;; => "Why don't scientists trust atoms? Because they make up everything!"

;; See full conversation
@conversation
```

## Generation Options

### Temperature

Controls randomness. Lower = more deterministic, higher = more creative.

```clojure
;; Very deterministic
(llm/chat-completion provider messages {:temperature 0})

;; Balanced
(llm/chat-completion provider messages {:temperature 0.7})

;; Creative
(llm/chat-completion provider messages {:temperature 1.5})
```

### Max Tokens

Limits response length:

```clojure
;; Short response
(llm/chat-completion provider messages {:max-tokens 50})

;; Long response
(llm/chat-completion provider messages {:max-tokens 4096})
```

### Model Selection

Override the default model per-request:

```clojure
;; Use a specific model
(llm/chat-completion provider messages {:model "gpt-4o-mini"})

;; Anthropic
(llm/chat-completion anthropic-provider messages {:model "claude-sonnet-4-20250514"})
```

## Understanding Responses

### Response Structure

```clojure
{:role :assistant           ; Always :assistant for completions
 :content "Response text"   ; The generated text
 :finish-reason :stop       ; Why generation stopped
 :usage {:prompt_tokens 15
         :completion_tokens 42
         :total_tokens 57}
 :tool-calls [...]}         ; Present when tools are called
```

### Finish Reasons

| Reason | Meaning |
|--------|---------|
| `:stop` | Natural completion |
| `:length` | Hit max_tokens limit |
| `:tool_calls` / `:tool_use` | Model wants to call a tool |
| `:content_filter` | Content filtered by provider |

### Token Usage

Track costs and limits:

```clojure
(let [response (llm/chat-completion provider messages {})]
  (println "Input tokens:" (get-in response [:usage :prompt_tokens]))
  (println "Output tokens:" (get-in response [:usage :completion_tokens]))
  (println "Total:" (get-in response [:usage :total_tokens])))
```

## Provider Differences

While the API is unified, some behaviors differ:

### System Messages

- **OpenAI**: System messages included in the messages array
- **Anthropic**: System messages extracted and sent as a separate `system` parameter

This is handled automatically - just use `{:role :system}` messages.

### Default Models

- **OpenAI**: `gpt-4o` (if not specified)
- **Anthropic**: `claude-3-haiku-20240307` (if not specified)

### Max Tokens

- **OpenAI**: Optional (has internal defaults)
- **Anthropic**: Required (library defaults to 4096)

## Complete REPL Example

Copy this entire block for a working example:

```clojure
;; === Setup ===
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

;; Create providers (ensure API keys are set as env vars)
(def openai-provider (openai/create-provider {}))
(def anthropic-provider (anthropic/create-provider {}))

;; === Basic Usage ===

;; Simple query
(def response
  (llm/chat-completion openai-provider
    [{:role :user :content "Explain recursion in one sentence."}]
    {:temperature 0.3}))

(println (:content response))

;; === Compare Providers ===

(defn ask-both [question]
  (let [messages [{:role :user :content question}]
        opts {:temperature 0.7 :max-tokens 100}]
    {:openai (-> (llm/chat-completion openai-provider messages opts) :content)
     :anthropic (-> (llm/chat-completion anthropic-provider messages opts) :content)}))

(ask-both "What is functional programming?")

;; === With System Prompt ===

(def code-review
  (llm/chat-completion openai-provider
    [{:role :system :content "You are a code reviewer. Be concise and specific."}
     {:role :user :content "Review this: (defn add [a b] (+ a b))"}]
    {:temperature 0.3}))

(println (:content code-review))

;; === Track Usage ===

(defn completion-with-stats [provider messages opts]
  (let [response (llm/chat-completion provider messages opts)]
    (println "Response:" (:content response))
    (println "Finish reason:" (:finish-reason response))
    (println "Usage:" (:usage response))
    response))

(completion-with-stats openai-provider
  [{:role :user :content "Hello!"}]
  {})
```

