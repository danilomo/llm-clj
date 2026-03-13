# Structured Outputs

Structured outputs let you get validated Clojure data directly from LLM responses. Instead of parsing JSON strings and handling provider differences, you define a Malli schema and receive typed data.

## The Problem

Without structured outputs, getting typed data from LLMs requires multiple steps:

```clojure
;; Manual approach (tedious and provider-specific)
(require '[llm-clj.core :as llm])
(require '[llm-clj.schema :as schema])
(require '[cheshire.core :as json])

;; For OpenAI:
(let [response (llm/chat-completion openai-provider messages
                 {:response-format (schema/structured-output-openai "User" user-schema nil)})
      json-str (:content response)
      data (json/parse-string json-str true)]
  (schema/validate user-schema data))

;; For Anthropic (different code path!):
(let [response (llm/chat-completion anthropic-provider messages
                 (schema/structured-output-anthropic "User" user-schema nil))
      json-str (-> response :tool-calls first :function :arguments)
      data (json/parse-string json-str true)]
  (schema/validate user-schema data))
```

## The Solution: `chat-completion-structured`

The unified API handles everything automatically:

```clojure
(require '[llm-clj.schema :as schema])

;; Same code works for both providers!
(schema/chat-completion-structured provider messages user-schema)
;; => {:name "Alice" :age 28 :interests ["hiking" "reading"]}
```

## Quick Start

```clojure
(require '[llm-clj.schema :as schema])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

;; Define your schema using Malli
(def UserSchema
  [:map
   [:name :string]
   [:age :int]
   [:interests [:vector :string]]])

;; Get structured data with a single call
(def user
  (schema/chat-completion-structured provider
    [{:role :user :content "My name is Alice, I'm 28, and I love hiking and reading."}]
    UserSchema))

user
;; => {:name "Alice" :age 28 :interests ["hiking" "reading"]}
```

## How It Works

Under the hood, `chat-completion-structured`:

1. **Detects the provider type** (OpenAI or Anthropic)
2. **Configures the appropriate mechanism**:
   - OpenAI: Uses `response_format` with `json_schema` type
   - Anthropic: Uses tool calling as a workaround (Anthropic doesn't have native structured outputs)
3. **Makes the API call** via `chat-completion`
4. **Extracts the JSON** from the provider-specific response location
5. **Parses and validates** against your Malli schema
6. **Returns validated Clojure data**

## API Reference

### `chat-completion-structured`

```clojure
(schema/chat-completion-structured provider messages schema)
(schema/chat-completion-structured provider messages schema opts)
```

**Arguments:**

| Argument | Type | Description |
|----------|------|-------------|
| `provider` | Provider | An OpenAI or Anthropic provider instance |
| `messages` | Vector | Message maps (same as `chat-completion`) |
| `schema` | Malli Schema | The expected output structure |
| `opts` | Map (optional) | Options (see below) |

**Options:**

| Option | Type | Default | Description |
|--------|------|---------|-------------|
| `:name` | String | `"Response"` | Schema name sent to the API |
| `:description` | String | Auto-generated | Description of the expected output |
| `:validate?` | Boolean | `true` | Whether to validate against schema |
| Other keys | Various | - | Passed through to `chat-completion` |

**Returns:** Validated Clojure data matching the schema.

**Throws:** `ExceptionInfo` with `:errors` key if validation fails.

## Schema Examples

### Simple Types

```clojure
;; Boolean decision
(def DecisionSchema
  [:map
   [:approved :boolean]
   [:reason :string]])

(schema/chat-completion-structured provider
  [{:role :user :content "Should we approve this request? The user has good history."}]
  DecisionSchema)
;; => {:approved true :reason "User has demonstrated reliable history"}
```

### Enums and Options

```clojure
(def SentimentSchema
  [:map
   [:sentiment [:enum "positive" "negative" "neutral"]]
   [:confidence :double]
   [:key-phrases [:vector :string]]])

(schema/chat-completion-structured provider
  [{:role :user :content "Analyze: 'I love this product!'"}]
  SentimentSchema)
;; => {:sentiment "positive" :confidence 0.95 :key-phrases ["love" "product"]}
```

### Nested Structures

```clojure
(def OrderSchema
  [:map
   [:customer [:map
               [:name :string]
               [:email :string]]]
   [:items [:vector [:map
                     [:product :string]
                     [:quantity :int]
                     [:price :double]]]]
   [:total :double]])

(schema/chat-completion-structured provider
  [{:role :system :content "Extract order details from the conversation."}
   {:role :user :content "Hi, I'm John (john@example.com). I'd like 2 widgets at $9.99 each."}]
  OrderSchema)
;; => {:customer {:name "John" :email "john@example.com"}
;;     :items [{:product "widget" :quantity 2 :price 9.99}]
;;     :total 19.98}
```

### Optional Fields

```clojure
(def ProfileSchema
  [:map
   [:name :string]
   [:age :int]
   [:bio {:optional true} :string]
   [:website {:optional true} :string]])
```

## Passing Additional Options

Options are passed through to `chat-completion`:

```clojure
(schema/chat-completion-structured provider messages schema
  {:name "ExtractedData"
   :description "Structured data extracted from user input"
   :validate? true
   ;; These are passed to chat-completion:
   :temperature 0
   :max-tokens 500
   :model "gpt-4o"})
```

## Disabling Validation

For cases where you want to skip Malli validation (e.g., the LLM might return slightly non-conforming data that you want to handle yourself):

```clojure
(schema/chat-completion-structured provider messages schema
  {:validate? false})
;; Returns raw parsed JSON, may not match schema exactly
```

## Error Handling

### Validation Errors

When the LLM returns data that doesn't match your schema:

```clojure
(try
  (schema/chat-completion-structured provider messages schema)
  (catch clojure.lang.ExceptionInfo e
    (let [data (ex-data e)]
      (println "Validation failed:")
      (println "Errors:" (:errors data))
      (println "Raw data:" (:data data)))))
```

### API Errors

Standard API errors (rate limits, auth, etc.) propagate as usual:

```clojure
(require '[llm-clj.errors :as errors])

(try
  (schema/chat-completion-structured provider messages schema)
  (catch clojure.lang.ExceptionInfo e
    (cond
      (errors/rate-limited? e) :retry-later
      (errors/authentication-error? e) :check-api-key
      :else (throw e))))
```

## Provider-Specific Behavior

### OpenAI

OpenAI uses native structured outputs via `response_format`:

```clojure
;; Internally becomes:
{:response-format {:type "json_schema"
                   :json_schema {:name "Response"
                                 :schema {...}
                                 :strict true}}}
```

The response content is the JSON string directly.

### Anthropic

Anthropic doesn't have native structured outputs, so the library uses tool calling as a workaround:

```clojure
;; Internally becomes:
{:tools [{:name "Response"
          :description "..."
          :input_schema {...}}]
 :tool-choice {:type "tool" :name "Response"}}
```

The response is extracted from the tool call arguments.

## Complete REPL Example

```clojure
;; === Setup ===
(require '[llm-clj.schema :as schema])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

(def openai-provider (openai/create-provider {}))
(def anthropic-provider (anthropic/create-provider {}))

;; === Define Schemas ===

(def PersonSchema
  [:map
   [:name :string]
   [:age :int]
   [:occupation :string]])

(def BookReviewSchema
  [:map
   [:title :string]
   [:author :string]
   [:rating [:int {:min 1 :max 5}]]
   [:summary :string]
   [:recommended :boolean]])

;; === Extract Structured Data ===

;; Works identically with both providers
(defn extract-person [provider text]
  (schema/chat-completion-structured provider
    [{:role :user :content (str "Extract person info: " text)}]
    PersonSchema
    {:temperature 0}))

(extract-person openai-provider
  "Dr. Sarah Chen is a 42-year-old neuroscientist.")
;; => {:name "Dr. Sarah Chen" :age 42 :occupation "neuroscientist"}

(extract-person anthropic-provider
  "Meet Bob, a 35-year-old software engineer.")
;; => {:name "Bob" :age 35 :occupation "software engineer"}

;; === Book Review Extraction ===

(schema/chat-completion-structured openai-provider
  [{:role :system :content "Extract structured book review data."}
   {:role :user :content "Just finished 'Clean Code' by Robert Martin.
                          Solid 4/5 - great principles for writing maintainable code.
                          Every developer should read this!"}]
  BookReviewSchema)
;; => {:title "Clean Code"
;;     :author "Robert Martin"
;;     :rating 4
;;     :summary "Great principles for writing maintainable code"
;;     :recommended true}

;; === With Custom Options ===

(schema/chat-completion-structured anthropic-provider
  [{:role :user :content "Parse: Alice, age 30, engineer"}]
  PersonSchema
  {:name "PersonExtractor"
   :description "Extracts person details from text"
   :model "claude-sonnet-4-20250514"
   :max-tokens 200})
```

## Low-Level API

For cases where you need more control, the underlying functions are available:

### `structured-output-openai`

Creates the `response_format` configuration:

```clojure
(schema/structured-output-openai "SchemaName" schema "Description")
;; => {:type "json_schema"
;;     :json_schema {:name "SchemaName"
;;                   :description "Description"
;;                   :schema {...}
;;                   :strict true}}
```

### `structured-output-anthropic`

Creates the tools/tool_choice configuration:

```clojure
(schema/structured-output-anthropic "SchemaName" schema "Description")
;; => {:tools [{:name "SchemaName"
;;              :description "Description"
;;              :input_schema {...}}]
;;     :tool-choice {:type "tool" :name "SchemaName"}}
```

### `extract-structured-content`

Extracts JSON from provider responses:

```clojure
(schema/extract-structured-content :openai response)   ; Gets :content
(schema/extract-structured-content :anthropic response) ; Gets tool call args
```

### `validate`

Validates data against a schema:

```clojure
(schema/validate PersonSchema {:name "Alice" :age 30 :occupation "dev"})
;; => {:name "Alice" :age 30 :occupation "dev"}

(schema/validate PersonSchema {:name "Alice"}) ; Missing fields
;; Throws ExceptionInfo with :errors
```

## Best Practices

1. **Use descriptive schema names** - Helps the LLM understand the expected format
2. **Set temperature to 0** - For consistent, deterministic extraction
3. **Provide examples in system prompt** - For complex schemas
4. **Handle validation errors gracefully** - LLMs occasionally produce edge cases
5. **Use `:description` for complex fields** - Via Malli's properties

## Next Steps

- [Vision](05-vision.md) - Combine structured outputs with image analysis
- [Tools](04-tools.md) - Full tool calling for complex workflows
- [Error Handling](06-errors.md) - Robust error handling patterns
