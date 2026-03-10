# llm-clj

A unified Clojure library for LLM providers. Write once, run on OpenAI or Anthropic.

## Features

- **Unified Protocol** - Single `LLMProvider` interface works across providers
- **Streaming** - Async streaming via core.async channels
- **Tool Calling** - Define tools with Malli schemas, automatic execution loop
- **Vision** - Multi-modal messages with images (URL, base64, file)
- **Documents** - PDF support for Anthropic Claude
- **Embeddings** - Text embeddings with OpenAI
- **Audio** - Whisper transcription and text-to-speech
- **Images** - DALL-E image generation
- **Batch Processing** - Async batch APIs for both providers
- **Structured Outputs** - JSON schema enforcement via Malli
- **Error Handling** - Rich error hierarchy with retry support

## Installation

Add to `project.clj`:

```clojure
[llm-clj "0.1.0"]
```

## Quick Start

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

;; Create a provider
(def provider (openai/create-provider {:api-key (System/getenv "OPENAI_API_KEY")}))
;; or
(def provider (anthropic/create-provider {:api-key (System/getenv "ANTHROPIC_API_KEY")}))

;; Chat completion
(llm/chat-completion provider
  [{:role :user :content "Hello!"}]
  {:model "gpt-4o" :temperature 0.7})
;; => {:content "Hello! How can I help you today?" :role :assistant :usage {...}}
```

## Streaming

```clojure
(require '[clojure.core.async :refer [<!!]])

(let [ch (llm/chat-completion-stream provider messages {:model "gpt-4o"})]
  (loop []
    (when-let [event (<!! ch)]
      (case (:type event)
        :delta    (print (:content event))
        :complete (println "\n[Done]")
        :error    (println "Error:" (:error event)))
      (when-not (#{:complete :error} (:type event))
        (recur)))))
```

## Tool Calling

```clojure
(require '[llm-clj.tools :as tools])

(def weather-tool
  (tools/define-tool "get_weather"
    "Get current weather for a location"
    [:map [:location :string] [:unit {:optional true} [:enum "celsius" "fahrenheit"]]]
    (fn [{:keys [location unit]}]
      {:temperature 22 :unit (or unit "celsius") :location location})))

;; Automatic tool execution loop
(tools/chat-with-tools provider
  [{:role :user :content "What's the weather in Tokyo?"}]
  [weather-tool]
  {:model "gpt-4o"})
```

## Vision

```clojure
(require '[llm-clj.vision :as vision])

(llm/chat-completion provider
  [(vision/vision-message
     [(vision/text-content "What's in this image?")
      (vision/image-url "https://example.com/image.jpg")])]
  {:model "gpt-4o"})

;; Or from a local file
(vision/image-file "photo.png")
```

## PDFs (Anthropic)

```clojure
(require '[llm-clj.documents :as docs])

(llm/chat-completion provider
  [(docs/document-message
     [(docs/pdf-file "report.pdf")
      (docs/text "Summarize the key findings")])]
  {:model "claude-sonnet-4-20250514"})
```

## Embeddings

```clojure
(require '[llm-clj.embeddings.core :as embeddings])
(require '[llm-clj.embeddings.openai :as openai-emb])

(def emb-provider (openai-emb/create-provider {}))

(embeddings/embed emb-provider "Hello, world!")
;; => [0.0123 -0.0456 ...] (1536 dimensions)

(embeddings/embed-batch emb-provider ["text1" "text2" "text3"])
```

## Error Handling

```clojure
(require '[llm-clj.errors :as errors])

(try
  (llm/chat-completion provider messages opts)
  (catch Exception e
    (cond
      (errors/rate-limited? e)
      (do (Thread/sleep (* 1000 (errors/retry-after e)))
          (retry))

      (errors/authentication-error? e)
      (throw (ex-info "Invalid API key" {}))

      (errors/retryable? e)
      (retry-with-backoff))))
```

## Supported Models

**OpenAI**: gpt-4o, gpt-4o-mini, gpt-4-turbo, o1, o1-mini, o3-mini

**Anthropic**: claude-opus-4-20250514, claude-sonnet-4-20250514, claude-3-5-haiku-20241022

## Configuration

Set environment variables:

```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."
```

Or pass directly:

```clojure
(openai/create-provider {:api-key "sk-..." :default-model "gpt-4o"})
```

## Development

```bash
make test      # Run all tests
make lint      # Run linters (clj-kondo, cljfmt, kibit)
make format    # Auto-format code
lein repl      # Start REPL
```

## Documentation

See the [docs](docs/) directory for detailed guides on each feature.

## License

MIT
