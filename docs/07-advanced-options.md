# Advanced Options

This chapter covers advanced options that work across both OpenAI and Anthropic providers, including stop sequences, sampling parameters, user tracking, strict tool mode, and extended thinking/reasoning capabilities.

## Stop Sequences

Stop sequences are strings that cause the model to stop generating when encountered.

### Basic Usage

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Stop when "END" is encountered
(llm/chat-completion provider
  [{:role :user :content "List three colors, then write END"}]
  {:stop-sequences ["END"]})

;; Multiple stop sequences
(llm/chat-completion provider
  [{:role :user :content "Write a paragraph"}]
  {:stop-sequences ["END" "###" "\n\n"]})
```

### REPL Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Stop at first blank line
(def response
  (llm/chat-completion provider
    [{:role :user :content "Write a poem with two stanzas separated by a blank line."}]
    {:stop-sequences ["\n\n"]
     :temperature 0.8}))

(println (:content response))
;; Only the first stanza is returned
```

### Provider Mapping

- **OpenAI**: Maps to `stop` parameter
- **Anthropic**: Maps to `stop_sequences` parameter

## Top-p (Nucleus) Sampling

Top-p sampling limits the model to considering only the most probable tokens whose cumulative probability reaches `p`.

### Usage

```clojure
;; Use top-p sampling
(llm/chat-completion provider
  [{:role :user :content "Write a creative story opening"}]
  {:top-p 0.9})     ; Consider tokens up to 90% cumulative probability

;; Combine with temperature
(llm/chat-completion provider
  [{:role :user :content "Write a creative story opening"}]
  {:temperature 0.8
   :top-p 0.95})
```

### Values and Effects

| Value | Effect |
|-------|--------|
| 0.1 | Very focused, considers only top ~10% of probability mass |
| 0.5 | Moderately focused |
| 0.9 | Default-like, considers most likely tokens |
| 1.0 | No filtering (equivalent to not using top-p) |

### REPL Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Compare different top-p values
(defn test-top-p [p]
  (println (str "\n=== top-p: " p " ==="))
  (println (:content
             (llm/chat-completion provider
               [{:role :user :content "Complete this: The cat jumped over the"}]
               {:top-p p :max-tokens 10 :temperature 1.0}))))

(test-top-p 0.1)  ; Very constrained
(test-top-p 0.5)  ; Moderate
(test-top-p 0.95) ; Creative
```

## User/Metadata Tracking

Track users for analytics and abuse detection:

### Basic Usage

```clojure
(llm/chat-completion provider
  [{:role :user :content "Hello"}]
  {:user-id "user_12345"})
```

### Provider Mapping

- **OpenAI**: Maps to `user` parameter
- **Anthropic**: Maps to `metadata.user_id`

### REPL Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

(def openai-provider (openai/create-provider {}))
(def anthropic-provider (anthropic/create-provider {}))

;; Track user across both providers
(defn chat-with-user [provider user-id message]
  (llm/chat-completion provider
    [{:role :user :content message}]
    {:user-id user-id
     :temperature 0.7}))

(chat-with-user openai-provider "user_123" "What's the weather?")
(chat-with-user anthropic-provider "user_123" "What's the weather?")
```

### Use Cases

1. **Abuse detection**: Track problematic users
2. **Analytics**: Understand usage patterns
3. **Rate limiting**: Apply per-user limits
4. **Personalization**: Enable provider-side personalization (if available)

## Strict Tool Mode

Enable strict schema validation for more reliable tool calls:

### Defining Strict Tools

```clojure
(require '[llm-clj.tools :as tools])

;; Regular tool (strict: false by default)
(def regular-tool
  (tools/define-tool
    "get_weather"
    "Gets weather for a location"
    [:map [:location :string]]
    get-weather-fn))

;; Strict tool
(def strict-tool
  (tools/define-tool
    "get_weather"
    "Gets weather for a location"
    [:map [:location :string]]
    get-weather-fn
    {:strict true}))  ; Enable strict mode
```

### What Strict Mode Does

1. **Adds `additionalProperties: false`** to all object schemas
2. **Sets `strict: true`** flag on the tool
3. **Forces exact schema compliance** from the model

### REPL Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.tools :as tools])

(def provider (openai/create-provider {}))

;; Define a strict tool
(def create-user-tool
  (tools/define-tool
    "create_user"
    "Creates a new user account"
    [:map
     [:username :string]
     [:email :string]
     [:age :int]]
    (fn [{:keys [username email age]}]
      {:created true
       :username username
       :email email
       :age age})
    {:strict true}))

;; Check the formatted output
(tools/format-tool-openai create-user-tool)
;; => {:type "function"
;;     :function {:name "create_user"
;;                :description "Creates a new user account"
;;                :parameters {:type "object"
;;                             :properties {...}
;;                             :required ["username" "email" "age"]
;;                             :additionalProperties false}
;;                :strict true}}

;; Use with chat completion
(llm/chat-completion provider
  [{:role :user :content "Create a user named Alice, email alice@example.com, age 30"}]
  {:tools [(tools/format-tool-openai create-user-tool)]})
```

### When to Use Strict Mode

- **Use strict mode** when you need guaranteed schema compliance
- **Skip strict mode** when flexibility is needed (e.g., optional extra fields)

## Extended Thinking / Reasoning

Both OpenAI and Anthropic offer "thinking" or "reasoning" capabilities that let models work through complex problems.

### Unified Option Format

```clojure
:thinking {:enabled true
           :effort :medium        ; OpenAI: :low, :medium, :high
           :budget-tokens 10000}  ; Anthropic: token budget
```

### OpenAI Reasoning Models

OpenAI's reasoning models (o1, o3, o4-mini) use an effort level:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {:model "o1"}))

;; Enable reasoning with medium effort
(def response
  (llm/chat-completion provider
    [{:role :user :content "Solve this logic puzzle: ..."}]
    {:thinking {:enabled true
                :effort :medium}}))

;; Access thinking content if available
(when-let [thinking (:thinking response)]
  (println "Reasoning:" (:content thinking)))

(println "Answer:" (:content response))
```

### Anthropic Extended Thinking

Anthropic uses a token budget approach:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.anthropic :as anthropic])

(def provider (anthropic/create-provider {:model "claude-sonnet-4-20250514"}))

;; Enable extended thinking with budget
(def response
  (llm/chat-completion provider
    [{:role :user :content "Analyze this complex problem: ..."}]
    {:thinking {:enabled true
                :budget-tokens 15000}}))  ; Allow up to 15k tokens for thinking

;; The thinking content is available in the response
(when-let [thinking (:thinking response)]
  (println "Thinking process:" (:content thinking)))

(println "Final answer:" (:content response))
```

### REPL Example: Comparing Thinking Modes

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

;; Setup providers
(def openai-provider (openai/create-provider {:model "o1"}))
(def anthropic-provider (anthropic/create-provider {:model "claude-sonnet-4-20250514"}))

(def complex-problem
  "A farmer has a fox, a chicken, and a bag of grain.
   He needs to cross a river with a boat that can only carry him and one item at a time.
   If left alone, the fox will eat the chicken, and the chicken will eat the grain.
   How does he get everything across safely?")

;; OpenAI with reasoning
(defn solve-openai []
  (let [response (llm/chat-completion openai-provider
                   [{:role :user :content complex-problem}]
                   {:thinking {:enabled true :effort :high}})]
    (println "=== OpenAI Reasoning ===")
    (when-let [t (:thinking response)]
      (println "Thinking:" (:content t)))
    (println "Answer:" (:content response))))

;; Anthropic with extended thinking
(defn solve-anthropic []
  (let [response (llm/chat-completion anthropic-provider
                   [{:role :user :content complex-problem}]
                   {:thinking {:enabled true :budget-tokens 10000}})]
    (println "=== Anthropic Extended Thinking ===")
    (when-let [t (:thinking response)]
      (println "Thinking:" (:content t)))
    (println "Answer:" (:content response))))

;; Compare both
(solve-openai)
(solve-anthropic)
```

## All Options Reference

Here's a complete reference of all unified options:

```clojure
{;; Model Selection
 :model "gpt-4o"                 ; Model identifier

 ;; Generation Parameters
 :temperature 0.7                ; Randomness (0.0-2.0)
 :max-tokens 4096                ; Maximum output tokens
 :top-p 0.9                      ; Nucleus sampling (0.0-1.0)
 :stop-sequences ["END" "###"]   ; Stop generation strings

 ;; User Tracking
 :user-id "user_123"             ; User identifier for tracking

 ;; Tool Use
 :tools [...]                    ; Vector of formatted tools
 :tool-choice "auto"             ; "auto", "required", "none", or specific

 ;; Structured Output (OpenAI)
 :response-format {:type "json_object"}

 ;; Extended Thinking/Reasoning
 :thinking {:enabled true
            :effort :medium        ; OpenAI: :low/:medium/:high
            :budget-tokens 10000}  ; Anthropic: token budget
}
```

## Provider-Specific Options

### OpenAI-Only Options

```clojure
{:frequency-penalty 0.5      ; Penalize repeated tokens (-2.0 to 2.0)
 :presence-penalty 0.5       ; Penalize tokens already used (-2.0 to 2.0)
 :logit-bias {50256 -100}    ; Adjust token probabilities
 :logprobs true              ; Return log probabilities
 :top-logprobs 5             ; Number of top tokens to return
 :n 3                        ; Generate multiple completions
 :seed 42                    ; Deterministic generation
 :parallel-tool-calls true   ; Allow parallel tool execution
 :service-tier :default      ; :default or :priority
 :store true                 ; Store conversation (for Responses API)
 :prediction {...}}          ; Prediction hints
```

### Anthropic-Only Options

```clojure
{:top-k 40                   ; Top-k sampling (unique to Anthropic)
 :beta-features [:pdfs       ; Enable beta features
                 :code-execution
                 :computer-use]
 :service-tier :priority}    ; :standard or :priority
```

## Complete Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[llm-clj.tools :as tools])

;; Create providers
(def openai-provider (openai/create-provider {}))
(def anthropic-provider (anthropic/create-provider {}))

;; Define a strict tool
(def analyze-tool
  (tools/define-tool
    "analyze_text"
    "Analyzes text and returns structured insights"
    [:map
     [:sentiment [:enum "positive" "negative" "neutral"]]
     [:topics [:vector :string]]
     [:key-points [:vector :string]]]
    (fn [analysis]
      {:recorded true :analysis analysis})
    {:strict true}))

;; Create a sophisticated analysis function
(defn deep-analyze [provider text]
  (let [response (llm/chat-completion provider
                   [{:role :system
                     :content "You are an expert analyst. Analyze text thoroughly then record your findings."}
                    {:role :user
                     :content (str "Analyze this text:\n\n" text)}]
                   {:temperature 0.3
                    :max-tokens 2000
                    :top-p 0.9
                    :stop-sequences ["[END ANALYSIS]"]
                    :user-id "analyst_system"
                    :tools [(tools/format-tool-openai analyze-tool)]
                    :thinking {:enabled true
                               :effort :medium
                               :budget-tokens 5000}})]

    {:thinking (get-in response [:thinking :content])
     :analysis (when (:tool-calls response)
                 (-> response :tool-calls first
                     (get-in [:function :arguments])
                     (cheshire.core/parse-string true)))
     :content (:content response)
     :usage (:usage response)}))

;; Use it
(deep-analyze openai-provider
  "Climate change continues to accelerate, with 2024 breaking temperature records globally.
   Scientists warn that immediate action is needed to prevent catastrophic outcomes.")
```

