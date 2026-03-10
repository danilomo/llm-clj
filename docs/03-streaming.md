# Streaming Responses

Streaming allows you to receive LLM responses in real-time as they're generated, rather than waiting for the complete response. This is essential for responsive user interfaces and long-running generations.

## How Streaming Works

llm-clj uses `core.async` channels to deliver streaming events. When you call `chat-completion-stream`, you get back a channel that emits events as the response is generated.

### Event Types

```clojure
;; Content chunk - emitted as text is generated
{:type :delta :content "chunk of text"}

;; Completion - emitted when generation finishes
{:type :complete
 :content "full accumulated text"
 :usage {:prompt_tokens N :completion_tokens N}
 :finish-reason :stop}

;; Error - emitted if something goes wrong
{:type :error :error <exception>}
```

## Basic Streaming

### Simple Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.core.async :as async :refer [<!! go-loop]])

(def provider (openai/create-provider {}))

;; Get a streaming channel
(def ch (llm/chat-completion-stream provider
          [{:role :user :content "Write a haiku about Clojure."}]
          {}))

;; Consume events from the channel
(go-loop []
  (when-let [event (<! ch)]
    (case (:type event)
      :delta (print (:content event))  ; Print each chunk
      :complete (println "\n[Done]")
      :error (println "Error:" (:error event)))
    (recur)))
```

### Blocking Consumption

For REPL experimentation, use blocking takes:

```clojure
(require '[clojure.core.async :refer [<!!]])

(def ch (llm/chat-completion-stream provider
          [{:role :user :content "Count from 1 to 5."}]
          {}))

;; Blocking loop - good for REPL
(loop []
  (when-let [event (<!! ch)]
    (case (:type event)
      :delta (do (print (:content event)) (flush))
      :complete (println "\n--- Complete ---")
      :error (println "Error:" (:error event)))
    (when (not= :complete (:type event))
      (recur))))
```

## REPL Examples

### Print Streaming Response

Copy and paste this entire block:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.core.async :refer [<!!]])

(def provider (openai/create-provider {}))

(defn stream-print [messages opts]
  (let [ch (llm/chat-completion-stream provider messages opts)]
    (loop []
      (when-let [event (<!! ch)]
        (case (:type event)
          :delta (do (print (:content event)) (flush))
          :complete (do
                      (println)
                      (println "---")
                      (println "Finish reason:" (:finish-reason event))
                      (println "Usage:" (:usage event)))
          :error (println "Error:" (:error event)))
        (when (not= :complete (:type event))
          (recur))))))

;; Try it
(stream-print [{:role :user :content "Explain monads in simple terms."}]
              {:temperature 0.7 :max-tokens 200})
```

### Collect Full Response

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.core.async :refer [<!!]])

(def provider (openai/create-provider {}))

(defn stream-collect [messages opts]
  "Streams a response, collecting all chunks into a final result."
  (let [ch (llm/chat-completion-stream provider messages opts)
        chunks (atom [])]
    (loop []
      (when-let [event (<!! ch)]
        (case (:type event)
          :delta (swap! chunks conj (:content event))
          :complete {:content (apply str @chunks)
                     :finish-reason (:finish-reason event)
                     :usage (:usage event)}
          :error {:error (:error event)})
        (if (#{:complete :error} (:type event))
          (case (:type event)
            :complete {:content (apply str @chunks)
                       :finish-reason (:finish-reason event)
                       :usage (:usage event)}
            :error {:error (:error event)})
          (recur))))))

;; Usage
(def result (stream-collect
              [{:role :user :content "What is 2+2?"}]
              {:temperature 0}))

(:content result)  ;; => "2+2 equals 4."
```

### Streaming with Timeout

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.core.async :as async :refer [<!! timeout alt!!]])

(def provider (openai/create-provider {}))

(defn stream-with-timeout [messages opts timeout-ms]
  (let [ch (llm/chat-completion-stream provider messages opts)
        deadline (async/timeout timeout-ms)]
    (loop [content ""]
      (let [[event port] (async/alts!! [ch deadline])]
        (cond
          (= port deadline)
          {:error :timeout :partial-content content}

          (nil? event)
          {:error :channel-closed :partial-content content}

          (= :delta (:type event))
          (do
            (print (:content event))
            (flush)
            (recur (str content (:content event))))

          (= :complete (:type event))
          {:content (:content event)
           :finish-reason (:finish-reason event)
           :usage (:usage event)}

          (= :error (:type event))
          {:error (:error event)})))))

;; 10 second timeout
(stream-with-timeout
  [{:role :user :content "Write a short poem."}]
  {:max-tokens 100}
  10000)
```

## Streaming with Both Providers

Both OpenAI and Anthropic support streaming with the same interface:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.providers.anthropic :as anthropic])
(require '[clojure.core.async :refer [<!!]])

(def openai-provider (openai/create-provider {}))
(def anthropic-provider (anthropic/create-provider {}))

(defn stream-from [provider name messages]
  (println (str "=== " name " ==="))
  (let [ch (llm/chat-completion-stream provider messages {:max-tokens 100})]
    (loop []
      (when-let [event (<!! ch)]
        (case (:type event)
          :delta (do (print (:content event)) (flush))
          :complete (println "\n")
          :error (println "Error:" (:error event)))
        (when (not= :complete (:type event))
          (recur))))))

(def messages [{:role :user :content "Say hello in 3 languages."}])

(stream-from openai-provider "OpenAI" messages)
(stream-from anthropic-provider "Anthropic" messages)
```

## Streaming Options

All standard options work with streaming:

```clojure
(llm/chat-completion-stream provider messages
  {:model "gpt-4o"
   :temperature 0.7
   :max-tokens 500
   :top-p 0.9
   :stop-sequences ["END"]
   :user-id "user_123"})
```

## Building a Chat Interface

Here's a complete example of a streaming chat interface:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[clojure.core.async :refer [<!!]])

(def provider (openai/create-provider {}))

(defn streaming-chat []
  (let [history (atom [{:role :system
                        :content "You are a helpful assistant. Be concise."}])]

    (println "Streaming Chat (type 'quit' to exit)")
    (println "=========================================")

    (loop []
      (print "\nYou: ")
      (flush)
      (let [input (read-line)]
        (when (and input (not= input "quit"))
          ;; Add user message
          (swap! history conj {:role :user :content input})

          ;; Stream response
          (print "\nAssistant: ")
          (flush)
          (let [ch (llm/chat-completion-stream provider @history {:temperature 0.7})
                response-content (atom "")]
            (loop []
              (when-let [event (<!! ch)]
                (case (:type event)
                  :delta (do
                           (swap! response-content str (:content event))
                           (print (:content event))
                           (flush))
                  :complete (do
                              (println)
                              ;; Add assistant response to history
                              (swap! history conj {:role :assistant
                                                   :content @response-content}))
                  :error (println "\nError:" (:error event)))
                (when (not= :complete (:type event))
                  (recur)))))
          (recur))))))

;; Run the chat
;; (streaming-chat)
```

## Error Handling in Streams

Errors are delivered through the channel as events:

```clojure
(require '[llm-clj.errors :as errors])

(defn safe-stream [provider messages opts]
  (let [ch (llm/chat-completion-stream provider messages opts)]
    (loop [content ""]
      (when-let [event (<!! ch)]
        (case (:type event)
          :delta
          (recur (str content (:content event)))

          :complete
          {:success true
           :content (:content event)
           :usage (:usage event)}

          :error
          (let [e (:error event)]
            (cond
              (errors/rate-limited? e)
              {:success false
               :error :rate-limited
               :retry-after (errors/retry-after e)}

              (errors/authentication-error? e)
              {:success false
               :error :auth-failed}

              :else
              {:success false
               :error :unknown
               :exception e})))))))

;; Usage
(safe-stream provider
  [{:role :user :content "Hello"}]
  {})
```

## Streaming Infrastructure

The `llm-clj.streaming` namespace provides low-level utilities:

```clojure
(require '[llm-clj.streaming :as streaming])

;; Parse SSE lines
(streaming/parse-sse-line "data: {\"text\": \"hello\"}")
;; => ["data" "{\"text\": \"hello\"}"]

;; Parse multiple SSE events from a chunk
(streaming/parse-sse-events "event: message\ndata: {\"text\": \"hi\"}\n\n")
;; => [{:event "message" :data "{\"text\": \"hi\"}"}]

;; Create a stream channel (used internally)
(streaming/create-stream-channel)
;; => {:channel #<chan> :buffer #<atom "">}
```

