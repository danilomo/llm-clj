# llm-clj Documentation

A comprehensive guide to using llm-clj, a unified Clojure library for OpenAI and Anthropic APIs.

## Tutorial Chapters

### Getting Started

1. **[Introduction](01-introduction.md)** - Overview, installation, and quick start
2. **[Basic Operations](02-basic-operations.md)** - Providers, messages, and responses

### Core Features

3. **[Streaming](03-streaming.md)** - Real-time streaming responses with core.async
4. **[Tools](04-tools.md)** - Function calling and tool execution
5. **[Vision](05-vision.md)** - Image understanding and analysis
6. **[Error Handling](06-errors.md)** - Structured error handling and retries

### Advanced Topics

7. **[Advanced Options](07-advanced-options.md)** - Stop sequences, sampling, thinking/reasoning

### Anthropic Features

8. **[Documents](08-documents.md)** - PDF support and document analysis
9. **[Server Tools](09-server-tools.md)** - Web search, code execution, computer use

### OpenAI Features

10. **[Embeddings](10-embeddings.md)** - Text embeddings and vector search
11. **[Images](11-images.md)** - DALL-E image generation
13. **[Audio](13-audio.md)** - Whisper transcription and TTS
15. **[Moderation](15-moderation.md)** - Content moderation
16. **[Responses API](16-responses-api.md)** - Stateful conversations

### Cross-Provider Features

12. **[Batch Processing](12-batch-processing.md)** - Async batch operations for both providers
14. **[Token Counting](14-token-counting.md)** - Token management and context windows

## Quick Reference

### Creating Providers

```clojure
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

(def openai (openai/create-provider {}))
(def anthropic (anthropic/create-provider {}))
```

### Basic Chat

```clojure
(require '[llm-clj.core :as llm])

(llm/chat-completion provider
  [{:role :user :content "Hello!"}]
  {:temperature 0.7})
```

### Streaming

```clojure
(require '[clojure.core.async :refer [<!!]])

(let [ch (llm/chat-completion-stream provider messages {})]
  (loop []
    (when-let [event (<!! ch)]
      (when (= :delta (:type event))
        (print (:content event)))
      (recur))))
```

### Tools

```clojure
(require '[llm-clj.tools :as tools])

(def my-tool
  (tools/define-tool "name" "description"
    [:map [:param :string]]
    (fn [{:keys [param]}] {:result param})))

(llm/chat-completion provider messages
  {:tools [(tools/format-tool-openai my-tool)]})
```

### Vision

```clojure
(require '[llm-clj.vision :as vision])

(llm/chat-completion provider
  [(vision/vision-message
     [(vision/text-content "What's in this image?")
      (vision/image-url "https://...")])]
  {})
```

## Environment Variables

| Variable | Provider | Description |
|----------|----------|-------------|
| `OPENAI_API_KEY` | OpenAI | API key for OpenAI |
| `ANTHROPIC_API_KEY` | Anthropic | API key for Anthropic |

## Common Options

| Option | Type | Description |
|--------|------|-------------|
| `:model` | string | Model identifier |
| `:temperature` | float | Randomness (0.0-2.0) |
| `:max-tokens` | int | Maximum output tokens |
| `:top-p` | float | Nucleus sampling |
| `:stop-sequences` | vector | Stop strings |
| `:tools` | vector | Tool definitions |
| `:user-id` | string | User tracking |
| `:thinking` | map | Enable reasoning/thinking |

## Response Format

```clojure
{:role :assistant
 :content "Response text"
 :finish-reason :stop
 :usage {:prompt_tokens N
         :completion_tokens N
         :total_tokens N}
 :tool-calls [...]}  ; When tools are invoked
```

## Error Handling

```clojure
(require '[llm-clj.errors :as errors])

(try
  (llm/chat-completion provider messages {})
  (catch Exception e
    (cond
      (errors/rate-limited? e) :retry-later
      (errors/authentication-error? e) :check-api-key
      (errors/retryable? e) :retry
      :else :fail)))
```

## Project Structure

```
llm-clj/
├── src/llm_clj/
│   ├── core.clj           ; LLMProvider protocol
│   ├── providers/
│   │   ├── openai.clj     ; OpenAI implementation
│   │   └── anthropic.clj  ; Anthropic implementation
│   ├── tools.clj          ; Tool definition and execution
│   ├── vision.clj         ; Image content helpers
│   ├── documents.clj      ; PDF/document support (Anthropic)
│   ├── server_tools.clj   ; Server-side tools (Anthropic)
│   ├── streaming.clj      ; Streaming utilities
│   ├── errors.clj         ; Error handling
│   ├── config.clj         ; Configuration
│   ├── schema.clj         ; Malli schema utilities
│   ├── token_counting.clj ; Token counting and estimation
│   ├── embeddings/        ; Embeddings API
│   │   ├── core.clj       ; EmbeddingProvider protocol
│   │   └── openai.clj     ; OpenAI implementation
│   ├── images/            ; Images API (DALL-E)
│   │   ├── core.clj       ; ImageProvider protocol
│   │   └── openai.clj     ; OpenAI implementation
│   ├── audio/             ; Audio API (Whisper, TTS)
│   │   ├── core.clj       ; AudioProvider protocol
│   │   └── openai.clj     ; OpenAI implementation
│   ├── moderation/        ; Content Moderation API
│   │   └── openai.clj     ; OpenAI implementation
│   ├── responses/         ; Responses API (Stateful)
│   │   ├── core.clj       ; ResponsesProvider protocol
│   │   └── openai.clj     ; OpenAI implementation
│   └── batch/             ; Batch Processing API
│       ├── core.clj       ; BatchProvider protocol
│       ├── openai.clj     ; OpenAI implementation
│       └── anthropic.clj  ; Anthropic implementation
├── docs/                   ; This documentation
├── specs/                  ; Implementation specifications
└── test/                   ; Test suite
```
