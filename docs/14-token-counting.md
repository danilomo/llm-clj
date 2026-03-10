# Token Counting

Token counting helps you manage context windows, estimate costs, and optimize prompts before sending requests. llm-clj provides both exact token counting (via Anthropic's API) and estimation utilities for rough counts.

## Overview

| Feature | Description | Provider |
|---------|-------------|----------|
| **Exact Counting** | Precise token count via API | Anthropic |
| **Estimation** | Heuristic-based approximation | Any |
| **Context Windows** | Model-specific limits lookup | Any |
| **Fit Checking** | Verify messages fit in context | Any |
| **Truncation** | Auto-trim to fit context | Any |

## Why Token Counting Matters

### Use Cases

| Use Case | Description |
|----------|-------------|
| **Cost Estimation** | Know costs before sending requests |
| **Context Management** | Ensure prompts fit within limits |
| **Prompt Optimization** | Identify and trim verbose prompts |
| **Conversation Trimming** | Remove old messages to fit context |
| **Budget Enforcement** | Prevent unexpectedly large requests |

### Context Window Sizes

| Model | Context Window |
|-------|----------------|
| Claude 3 (all) | 200,000 tokens |
| Claude Sonnet 4 | 200,000 tokens |
| Claude Opus 4 | 200,000 tokens |
| GPT-4o | 128,000 tokens |
| GPT-4o-mini | 128,000 tokens |
| GPT-4-turbo | 128,000 tokens |
| GPT-4 | 8,192 tokens |
| GPT-3.5-turbo | 16,385 tokens |
| o1 | 200,000 tokens |
| o1-mini | 128,000 tokens |

## Exact Token Counting (Anthropic)

Anthropic provides an API endpoint that returns exact token counts without executing the request.

### Using the Anthropic Provider

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

;; Count tokens for a simple message
(anthropic/count-tokens nil
  [{:role :user :content "What is the capital of France?"}]
  {:model "claude-sonnet-4-20250514"})
;; => {:input-tokens 14}
```

### Using the Token Counting Module

```clojure
(require '[llm-clj.token-counting :as tc])

;; Count tokens (delegates to Anthropic API)
(tc/count-tokens-anthropic
  [{:role :user :content "Hello, how are you?"}]
  {:model "claude-sonnet-4-20250514"})
;; => {:input-tokens 12}
```

### With System Prompts

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

(anthropic/count-tokens nil
  [{:role :system :content "You are a helpful assistant who speaks like a pirate."}
   {:role :user :content "Hello!"}]
  {:model "claude-sonnet-4-20250514"})
;; => {:input-tokens 22}
```

### With Tools

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

(def tools
  [{:name "get_weather"
    :description "Get the current weather for a location"
    :input_schema {:type "object"
                   :properties {:location {:type "string"
                                           :description "City name"}}
                   :required ["location"]}}])

(anthropic/count-tokens nil
  [{:role :user :content "What's the weather in Paris?"}]
  {:model "claude-sonnet-4-20250514"
   :tools tools})
;; => {:input-tokens 85}
```

## REPL Examples

### Basic Token Counting

Copy and paste this entire block:

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])

;; Count tokens for various inputs
(def messages
  [{:role :user :content "Explain quantum computing in simple terms."}])

(def result (anthropic/count-tokens nil messages
              {:model "claude-sonnet-4-20250514"}))

(println "Input tokens:" (:input-tokens result))

;; Compare with a longer message
(def longer-messages
  [{:role :user :content "Explain quantum computing in simple terms.
                          Include examples of real-world applications,
                          discuss quantum supremacy, and explain
                          the difference between qubits and classical bits."}])

(def longer-result (anthropic/count-tokens nil longer-messages
                     {:model "claude-sonnet-4-20250514"}))

(println "Longer input tokens:" (:input-tokens longer-result))
```

### Pre-flight Cost Check

```clojure
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.core :as llm])

(defn safe-chat-completion
  "Sends a chat completion only if it fits within budget."
  [provider messages options & {:keys [max-input-tokens]
                                 :or {max-input-tokens 10000}}]
  (let [token-count (anthropic/count-tokens nil messages options)]
    (if (> (:input-tokens token-count) max-input-tokens)
      (throw (ex-info "Request exceeds token budget"
                      {:input-tokens (:input-tokens token-count)
                       :max-allowed max-input-tokens}))
      (do
        (println "Token count OK:" (:input-tokens token-count))
        (llm/chat-completion provider messages options)))))

;; Usage
(def provider (anthropic/create-provider {}))

(safe-chat-completion provider
  [{:role :user :content "Hello!"}]
  {:model "claude-sonnet-4-20250514"}
  :max-input-tokens 100)
```

## Token Estimation

For providers without a token counting API, or for quick estimates:

### Basic Estimation

```clojure
(require '[llm-clj.token-counting :as tc])

;; Estimate tokens from text (~4 characters per token)
(tc/estimate-tokens "Hello, world!")
;; => 4

(tc/estimate-tokens "The quick brown fox jumps over the lazy dog.")
;; => 12
```

### Message Estimation

```clojure
(require '[llm-clj.token-counting :as tc])

;; Estimate tokens for a message (includes role overhead)
(tc/estimate-message-tokens {:role :user :content "Hello, world!"})
;; => 8  (content tokens + 4 overhead)

;; Estimate entire conversation
(def conversation
  [{:role :system :content "You are a helpful assistant."}
   {:role :user :content "What is Clojure?"}
   {:role :assistant :content "Clojure is a functional programming language..."}
   {:role :user :content "Can you show me an example?"}])

(tc/estimate-conversation-tokens conversation)
;; => ~50 tokens (rough estimate)
```

### Multi-part Content Estimation

```clojure
(require '[llm-clj.token-counting :as tc])

;; Works with vision-style multi-part content
(def message-with-parts
  {:role :user
   :content [{:type :text :text "What is in this image?"}
             {:type :image :url "..."}]})

(tc/estimate-message-tokens message-with-parts)
;; => ~10 (estimates text parts only)
```

## Context Window Management

### Looking Up Context Windows

```clojure
(require '[llm-clj.token-counting :as tc])

;; Get context window for known models
(tc/get-context-window :claude-3-sonnet)
;; => 200000

(tc/get-context-window :gpt-4o)
;; => 128000

;; Works with model strings too
(tc/get-context-window "claude-sonnet-4-20250514")
;; => 200000

(tc/get-context-window "gpt-4-turbo-2024-04-09")
;; => 128000

;; Unknown models get conservative default
(tc/get-context-window "unknown-model")
;; => 8000
```

### Checking If Messages Fit

```clojure
(require '[llm-clj.token-counting :as tc])

(def messages
  [{:role :system :content "You are helpful."}
   {:role :user :content "Hello!"}])

;; Check against explicit limit
(tc/fits-context? messages {:max-tokens 200000})
;; => true

;; Reserve tokens for response (default: 4096)
(tc/fits-context? messages {:max-tokens 200000 :reserve 8000})
;; => true

;; Check against model's context window
(tc/fits-model-context? messages {:model "claude-sonnet-4-20250514"})
;; => true

(tc/fits-model-context? messages {:model "gpt-4" :reserve 2000})
;; => true
```

### Calculating Available Tokens

```clojure
(require '[llm-clj.token-counting :as tc])

(def messages
  [{:role :system :content "You are a coding assistant."}
   {:role :user :content "Write a function to sort a list."}])

;; How many tokens available for response?
(tc/available-tokens messages {:max-tokens 200000})
;; => ~199950
```

## Truncating Conversations

For long conversations that exceed the context window:

```clojure
(require '[llm-clj.token-counting :as tc])

;; Create a long conversation history
(def long-conversation
  (concat
    [{:role :system :content "You are a helpful assistant."}]
    (for [i (range 100)]
      {:role (if (even? i) :user :assistant)
       :content (str "Message number " i " with some content.")})))

;; Truncate to fit (keeps system message and recent messages)
(def truncated
  (tc/truncate-to-fit long-conversation
    {:max-tokens 8000
     :reserve 2000}))

(println "Original messages:" (count long-conversation))
(println "Truncated messages:" (count truncated))
(println "System message preserved:" (= :system (-> truncated first :role)))
```

### REPL Example: Smart Truncation

```clojure
(require '[llm-clj.token-counting :as tc])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])

(def provider (anthropic/create-provider {}))

(defn chat-with-auto-truncate
  "Maintains conversation history, auto-truncating when needed."
  [provider messages options]
  (let [model (or (:model options) "claude-sonnet-4-20250514")
        max-tokens (tc/get-context-window model)
        max-output (or (:max-tokens options) 4096)

        ;; Truncate if needed
        fitted-messages (tc/truncate-to-fit messages
                          {:max-tokens max-tokens
                           :reserve max-output})]

    (when (< (count fitted-messages) (count messages))
      (println "Truncated from" (count messages) "to" (count fitted-messages) "messages"))

    (llm/chat-completion provider fitted-messages options)))

;; Usage with growing conversation
(def conversation (atom [{:role :system :content "You are a helpful assistant."}]))

(defn chat! [user-message]
  (swap! conversation conj {:role :user :content user-message})
  (let [response (chat-with-auto-truncate provider @conversation
                   {:model "claude-sonnet-4-20250514" :max-tokens 1000})]
    (swap! conversation conj {:role :assistant :content (:content response)})
    (:content response)))

(chat! "Hello!")
(chat! "What's the weather like?")
;; ... continues, auto-truncating old messages as needed
```

## Complete Application: Token Budget Manager

```clojure
(require '[llm-clj.token-counting :as tc])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.core :as llm])

;; === Token Budget Manager ===

(defn create-budget-manager
  "Creates a token budget manager for tracking usage."
  [daily-limit]
  (atom {:daily-limit daily-limit
         :used 0
         :requests []}))

(defn record-usage!
  "Records token usage."
  [manager input-tokens output-tokens]
  (swap! manager (fn [state]
                   (-> state
                       (update :used + input-tokens output-tokens)
                       (update :requests conj
                               {:timestamp (System/currentTimeMillis)
                                :input input-tokens
                                :output output-tokens})))))

(defn budget-remaining
  "Returns remaining token budget."
  [manager]
  (- (:daily-limit @manager) (:used @manager)))

(defn within-budget?
  "Checks if estimated tokens are within budget."
  [manager estimated-tokens]
  (>= (budget-remaining manager) estimated-tokens))

(defn chat-with-budget
  "Sends request only if within budget."
  [provider messages options budget-manager]
  (let [;; Get exact token count
        token-count (anthropic/count-tokens nil messages options)
        input-tokens (:input-tokens token-count)
        estimated-output (or (:max-tokens options) 4096)
        estimated-total (+ input-tokens estimated-output)]

    (if-not (within-budget? budget-manager estimated-total)
      (throw (ex-info "Exceeds daily token budget"
                      {:estimated estimated-total
                       :remaining (budget-remaining budget-manager)}))
      (let [response (llm/chat-completion provider messages options)
            actual-output (get-in response [:usage :completion_tokens] estimated-output)]
        ;; Record actual usage
        (record-usage! budget-manager input-tokens actual-output)
        response))))

;; === Usage ===

(def provider (anthropic/create-provider {}))
(def budget (create-budget-manager 100000))  ; 100k tokens/day

;; Make requests with budget tracking
(try
  (let [response (chat-with-budget provider
                   [{:role :user :content "Hello!"}]
                   {:model "claude-sonnet-4-20250514" :max-tokens 100}
                   budget)]
    (println "Response:" (:content response))
    (println "Remaining budget:" (budget-remaining budget)))
  (catch Exception e
    (println "Error:" (ex-message e))))
```

## Complete Application: Prompt Optimizer

```clojure
(require '[llm-clj.token-counting :as tc])
(require '[llm-clj.providers.anthropic :as anthropic])

;; === Prompt Optimizer ===

(defn analyze-prompt-tokens
  "Analyzes token usage in a prompt."
  [messages options]
  (let [total (anthropic/count-tokens nil messages options)

        ;; Analyze each message
        individual (for [i (range (count messages))]
                     (let [single-msg [(nth messages i)]
                           count-result (anthropic/count-tokens nil single-msg options)]
                       {:index i
                        :role (-> messages (nth i) :role)
                        :tokens (:input-tokens count-result)
                        :preview (-> messages (nth i) :content (subs 0 (min 50 (count (-> messages (nth i) :content)))))}))

        ;; Find largest contributors
        sorted (sort-by :tokens > individual)]

    {:total-tokens (:input-tokens total)
     :by-message individual
     :largest (take 3 sorted)}))

(defn suggest-optimizations
  "Suggests ways to reduce token count."
  [analysis target-tokens]
  (let [current (:total-tokens analysis)
        reduction-needed (- current target-tokens)]
    (if (<= reduction-needed 0)
      {:status :ok :message "Already within target"}
      {:status :over-budget
       :current current
       :target target-tokens
       :reduction-needed reduction-needed
       :suggestions
       (cond-> []
         (> (count (:by-message analysis)) 3)
         (conj {:type :reduce-history
                :message "Consider removing older messages"})

         (some #(> (:tokens %) 1000) (:by-message analysis))
         (conj {:type :summarize
                :message "Summarize or shorten long messages"
                :candidates (filter #(> (:tokens %) 1000) (:by-message analysis))})

         (some #(= :system (:role %)) (:by-message analysis))
         (conj {:type :trim-system
                :message "Consider shortening system prompt"}))})))

;; === Usage ===

(def messages
  [{:role :system :content "You are a helpful assistant with expertise in programming.
                            You provide detailed explanations with examples.
                            Always be thorough and comprehensive."}
   {:role :user :content "Explain recursion."}
   {:role :assistant :content "Recursion is a programming technique where a function
                                calls itself to solve smaller instances of a problem...
                                (long explanation)"}
   {:role :user :content "Show me an example in Clojure."}])

(def analysis (analyze-prompt-tokens messages {:model "claude-sonnet-4-20250514"}))

(println "Total tokens:" (:total-tokens analysis))
(println "\nBy message:")
(doseq [msg (:by-message analysis)]
  (println (format "  [%d] %s: %d tokens - %s..."
                   (:index msg) (name (:role msg)) (:tokens msg) (:preview msg))))

(println "\nLargest contributors:")
(doseq [msg (:largest analysis)]
  (println (format "  %s: %d tokens" (name (:role msg)) (:tokens msg))))

(def suggestions (suggest-optimizations analysis 500))
(when (= :over-budget (:status suggestions))
  (println "\nOptimization suggestions:")
  (doseq [s (:suggestions suggestions)]
    (println "  -" (:message s))))
```

## Estimation vs Exact Counting

| Aspect | Estimation | Exact (Anthropic) |
|--------|------------|-------------------|
| Speed | Instant | API call required |
| Accuracy | ~80-90% | 100% |
| Cost | Free | Small API cost |
| Provider | Any | Anthropic only |
| Use Case | Quick checks | Pre-flight validation |

### When to Use Each

**Use Estimation for:**
- Quick UI feedback
- Rough budget calculations
- Non-critical checks
- OpenAI requests

**Use Exact Counting for:**
- Cost-sensitive applications
- Near-limit requests
- Billing accuracy
- Anthropic requests

## Options Reference

### count-tokens-anthropic Options

```clojure
{:model "claude-sonnet-4-20250514"  ; Required - model for tokenization
 :api-key "sk-..."                   ; Optional - uses env var if omitted
 :tools [...]                        ; Optional - include tool definitions
 :tool-choice {...}                  ; Optional - tool choice config
 :thinking {:enabled true            ; Optional - thinking mode
            :budget-tokens 10000}}
```

### fits-context? Options

```clojure
{:max-tokens 200000    ; Required - context window size
 :reserve 4096}        ; Optional - tokens to reserve for response
```

### fits-model-context? Options

```clojure
{:model "claude-sonnet-4-20250514"  ; Required - model name
 :reserve 4096}                      ; Optional - tokens to reserve
```

### truncate-to-fit Options

```clojure
{:max-tokens 200000    ; Required - context window size
 :reserve 4096}        ; Optional - tokens to reserve for response
```

## Error Handling

```clojure
(require '[llm-clj.token-counting :as tc])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.errors :as errors])

(defn safe-count-tokens [messages options]
  (try
    {:success true
     :result (anthropic/count-tokens nil messages options)}
    (catch Exception e
      (cond
        (errors/validation-error? e)
        {:success false
         :error :invalid-request
         :message (ex-message e)}

        (errors/authentication-error? e)
        {:success false
         :error :auth-failed}

        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        :else
        {:success false
         :error :unknown
         :message (ex-message e)}))))

;; Usage
(let [result (safe-count-tokens
               [{:role :user :content "Hello"}]
               {:model "claude-sonnet-4-20250514"})]
  (if (:success result)
    (println "Tokens:" (-> result :result :input-tokens))
    (println "Error:" (:error result))))
```

## Best Practices

1. **Pre-flight checks** - Count tokens before sending expensive requests
2. **Reserve for output** - Always reserve tokens for the response (4096+ recommended)
3. **Use estimation for UI** - Show approximate counts instantly, exact counts on submit
4. **Cache token counts** - Same messages = same tokens, avoid redundant API calls
5. **Monitor budgets** - Track token usage across requests
6. **Truncate smartly** - Keep system prompts and recent messages when trimming

## Limitations

- **Exact counting** - Only available for Anthropic
- **Estimation accuracy** - Varies by content type (code vs prose)
- **Image tokens** - Estimation doesn't account for image tokens
- **Tool tokens** - Tool definitions add significant tokens

## Next Steps

- [Error Handling](06-errors.md) - Handle token counting errors
- [Streaming](03-streaming.md) - Monitor tokens during streaming
- [Batch Processing](12-batch-processing.md) - Estimate batch costs
