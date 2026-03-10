# llm-clj: Introduction

A unified Clojure library for interacting with Large Language Model APIs, providing a common abstraction over OpenAI and Anthropic.

## Philosophy

**llm-clj** follows a simple design principle: write your LLM code once, run it against any provider. The library normalizes the differences between OpenAI and Anthropic APIs while still allowing access to provider-specific features when needed.

Key design decisions:

- **Protocol-based abstraction**: A single `LLMProvider` protocol defines the core operations
- **Normalized messages**: A consistent message format that maps to both providers
- **Unified options**: Common options like `:temperature`, `:max-tokens`, `:tools` work identically across providers
- **Provider-specific escapes**: When you need provider-specific features, they're available without breaking the abstraction

## Installation

Add to your `project.clj`:

```clojure
[llm-clj "0.1.0-SNAPSHOT"]
```

Or `deps.edn`:

```clojure
{:deps {llm-clj/llm-clj {:local/root "/path/to/llm-clj"}}}
```

## Dependencies

The library uses:

- `core.async` for streaming support
- `clj-http` for HTTP requests
- `cheshire` for JSON processing
- `malli` for schema validation

## Quick Start

### 1. Set Up API Keys

Set environment variables for your providers:

```bash
export OPENAI_API_KEY="sk-..."
export ANTHROPIC_API_KEY="sk-ant-..."
```

### 2. Create a Provider

```clojure
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])

;; OpenAI provider (uses OPENAI_API_KEY env var)
(def openai-provider (openai/create-provider {}))

;; Anthropic provider (uses ANTHROPIC_API_KEY env var)
(def anthropic-provider (anthropic/create-provider {}))
```

### 3. Send a Message

```clojure
(require '[llm-clj.core :as llm])

;; Works identically with either provider
(llm/chat-completion openai-provider
  [{:role :user :content "Hello, how are you?"}]
  {:temperature 0.7})

;; => {:role :assistant
;;     :content "Hello! I'm doing well, thank you for asking..."
;;     :finish-reason :stop
;;     :usage {:prompt_tokens 12 :completion_tokens 28 :total_tokens 40}}
```

## Core Concepts

### Messages

Messages are maps with `:role` and `:content` keys:

```clojure
{:role :system :content "You are a helpful assistant."}
{:role :user :content "What is 2+2?"}
{:role :assistant :content "2+2 equals 4."}
```

Supported roles:
- `:system` - System instructions (extracted and handled specially by Anthropic)
- `:user` - User messages
- `:assistant` - Assistant responses
- `:tool` - Tool execution results

### Options

Common options work across all providers:

| Option | Type | Description |
|--------|------|-------------|
| `:model` | string | Model to use (e.g., "gpt-4o", "claude-sonnet-4-20250514") |
| `:temperature` | float | Sampling temperature (0.0-2.0) |
| `:max-tokens` | int | Maximum tokens to generate |
| `:top-p` | float | Nucleus sampling (0.0-1.0) |
| `:stop-sequences` | vector | Strings that stop generation |
| `:tools` | vector | Tool definitions |
| `:user-id` | string | User identifier for tracking |

### Responses

Normalized response format:

```clojure
{:role :assistant
 :content "Response text"
 :finish-reason :stop          ; :stop, :length, :tool_calls, etc.
 :usage {:prompt_tokens N
         :completion_tokens N
         :total_tokens N}
 :tool-calls [...]}            ; When tools are called
```

## Architecture Overview

```
                    +------------------+
                    |   LLMProvider    |
                    |    Protocol      |
                    +--------+---------+
                             |
              +--------------+--------------+
              |                             |
    +---------v---------+         +---------v---------+
    |  OpenAIProvider   |         | AnthropicProvider |
    +-------------------+         +-------------------+
              |                             |
              v                             v
        OpenAI API                   Anthropic API
```

