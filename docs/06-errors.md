# Error Handling

llm-clj provides a structured error system that normalizes error handling across providers, making it easy to write robust applications that handle failures gracefully.

## Error Type Hierarchy

All API errors derive from a common hierarchy:

```
::api-error (base)
  ├── ::rate-limit-error (429)
  ├── ::authentication-error (401/403)
  ├── ::not-found-error (404)
  ├── ::server-error (5xx)
  └── ::timeout-error

::validation-error (client-side validation failures)
```

## Basic Error Handling

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])

(def provider (openai/create-provider {}))

(try
  (llm/chat-completion provider
    [{:role :user :content "Hello"}]
    {})
  (catch Exception e
    (cond
      (errors/rate-limited? e)
      (println "Rate limited! Retry after:" (errors/retry-after e) "seconds")

      (errors/authentication-error? e)
      (println "Check your API key!")

      (errors/server-error? e)
      (println "Server error, try again later")

      (errors/timeout? e)
      (println "Request timed out")

      (errors/api-error? e)
      (println "Other API error:" (ex-message e))

      :else
      (throw e))))
```

## Error Predicates

Use predicates to check error types:

| Predicate | Description |
|-----------|-------------|
| `(api-error? e)` | Any API error (base type) |
| `(rate-limited? e)` | HTTP 429 rate limit error |
| `(authentication-error? e)` | HTTP 401/403 auth error |
| `(server-error? e)` | HTTP 5xx server errors |
| `(timeout? e)` | Request timeout |
| `(validation-error? e)` | Client-side validation failure |
| `(retryable? e)` | True for rate-limit, server, or timeout errors |

## Error Data Extraction

Extract useful information from errors:

```clojure
(require '[llm-clj.errors :as errors])

(try
  (llm/chat-completion provider messages {})
  (catch Exception e
    (println "Error type:" (errors/error-type e))
    (println "Provider:" (errors/error-provider e))
    (println "HTTP status:" (errors/error-status e))

    ;; For rate limit errors
    (when (errors/rate-limited? e)
      (println "Retry after:" (errors/retry-after e) "seconds"))

    ;; Full error data
    (println "Error data:" (ex-data e))))
```

## REPL Examples

### Testing Error Handling

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])

;; Test with invalid API key
(def bad-provider (openai/create-provider {:api-key "invalid-key"}))

(try
  (llm/chat-completion bad-provider
    [{:role :user :content "Hello"}]
    {})
  (catch Exception e
    (println "Is auth error?" (errors/authentication-error? e))
    (println "Status:" (errors/error-status e))
    (println "Message:" (ex-message e))))

;; => Is auth error? true
;; => Status: 401
;; => Message: openai API error: Incorrect API key provided
```

### Implementing Retry Logic

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])

(def provider (openai/create-provider {}))

(defn with-retries
  "Executes f with exponential backoff for retryable errors."
  [f max-retries]
  (loop [attempt 0
         delay-ms 1000]
    (let [result (try
                   {:success true :value (f)}
                   (catch Exception e
                     (if (and (errors/retryable? e) (< attempt max-retries))
                       {:success false
                        :error e
                        :retry? true
                        :delay (or (when (errors/rate-limited? e)
                                     (* 1000 (errors/retry-after e)))
                                   delay-ms)}
                       {:success false
                        :error e
                        :retry? false})))]
      (cond
        (:success result)
        (:value result)

        (:retry? result)
        (do
          (println (str "Retry " (inc attempt) "/" max-retries
                        " after " (:delay result) "ms"))
          (Thread/sleep (:delay result))
          (recur (inc attempt) (* delay-ms 2)))

        :else
        (throw (:error result))))))

;; Usage
(with-retries
  #(llm/chat-completion provider
     [{:role :user :content "Hello"}]
     {})
  3)
```

### Comprehensive Error Handler

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])

(def provider (openai/create-provider {}))

(defn safe-completion
  "Wraps chat-completion with comprehensive error handling.
   Returns {:ok true :result ...} or {:ok false :error-type ... :message ...}"
  [provider messages opts]
  (try
    {:ok true
     :result (llm/chat-completion provider messages opts)}
    (catch Exception e
      (let [error-data (ex-data e)]
        {:ok false
         :error-type (cond
                       (errors/rate-limited? e) :rate-limited
                       (errors/authentication-error? e) :authentication
                       (errors/server-error? e) :server-error
                       (errors/timeout? e) :timeout
                       (errors/validation-error? e) :validation
                       (errors/api-error? e) :api-error
                       :else :unknown)
         :message (ex-message e)
         :status (errors/error-status e)
         :provider (errors/error-provider e)
         :retry-after (errors/retry-after e)
         :retryable? (errors/retryable? e)
         :raw-data error-data}))))

;; Usage
(let [result (safe-completion provider
               [{:role :user :content "Hello"}]
               {})]
  (if (:ok result)
    (println "Success:" (get-in result [:result :content]))
    (do
      (println "Failed:" (:error-type result))
      (println "Message:" (:message result))
      (when (:retryable? result)
        (println "This error is retryable")
        (when-let [delay (:retry-after result)]
          (println "Retry after:" delay "seconds"))))))
```

## Creating Custom Errors

```clojure
(require '[llm-clj.errors :as errors])

;; Validation error (for client-side issues)
(throw (errors/validation-error
         "Invalid temperature value"
         {:temperature 2.5 :max 2.0}))

;; Rate limit error (simulated)
(throw (errors/rate-limit-error :openai 30))

;; Timeout error
(throw (errors/timeout-error :openai 30000))
```

## Error Handling in Streaming

Streaming errors are delivered through the channel:

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])
(require '[clojure.core.async :refer [<!!]])

(def provider (openai/create-provider {}))

(defn safe-stream [messages opts]
  (let [ch (llm/chat-completion-stream provider messages opts)]
    (loop [content ""]
      (if-let [event (<!! ch)]
        (case (:type event)
          :delta
          (recur (str content (:content event)))

          :complete
          {:ok true
           :content (:content event)
           :usage (:usage event)}

          :error
          (let [e (:error event)]
            {:ok false
             :error-type (cond
                           (errors/rate-limited? e) :rate-limited
                           (errors/authentication-error? e) :authentication
                           (errors/server-error? e) :server-error
                           :else :unknown)
             :message (ex-message e)
             :retryable? (errors/retryable? e)}))

        ;; Channel closed unexpectedly
        {:ok false
         :error-type :channel-closed
         :partial-content content}))))

;; Usage
(safe-stream [{:role :user :content "Hello"}] {})
```

## Provider-Specific Error Details

Errors include provider-specific information:

```clojure
(try
  (llm/chat-completion provider messages {})
  (catch Exception e
    (let [data (ex-data e)]
      (println "Provider:" (:provider data))  ; :openai or :anthropic
      (println "Status:" (:status data))      ; HTTP status code
      (println "Body:" (:body data)))))       ; Parsed response body
```

### OpenAI Error Body Structure

```clojure
{:error {:message "..." :type "..." :param "..." :code "..."}}
```

### Anthropic Error Body Structure

```clojure
{:error {:type "..." :message "..."}}
```

## Best Practices

### 1. Always Handle Rate Limits

```clojure
(defn with-rate-limit-handling [f]
  (try
    (f)
    (catch Exception e
      (if (errors/rate-limited? e)
        (let [delay (or (errors/retry-after e) 60)]
          (Thread/sleep (* 1000 delay))
          (f))
        (throw e)))))
```

### 2. Log Error Details

```clojure
(defn log-error [e]
  (let [data (ex-data e)]
    (println (format "[ERROR] Provider: %s, Status: %s, Type: %s"
                     (:provider data)
                     (:status data)
                     (:type data)))
    (println "Message:" (ex-message e))))
```

### 3. Distinguish Retryable Errors

```clojure
(defn handle-error [e]
  (if (errors/retryable? e)
    (do
      (println "Retryable error, will retry...")
      :retry)
    (do
      (println "Non-retryable error, failing...")
      :fail)))
```

### 4. Validate Before Calling

```clojure
(defn validated-completion [provider messages opts]
  (when (> (:temperature opts 0) 2.0)
    (throw (errors/validation-error
             "Temperature must be <= 2.0"
             {:temperature (:temperature opts)})))

  (when (empty? messages)
    (throw (errors/validation-error
             "Messages cannot be empty"
             {:messages messages})))

  (llm/chat-completion provider messages opts))
```

## Complete Error Handling Example

```clojure
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])
(require '[llm-clj.errors :as errors])

(def provider (openai/create-provider {}))

(defn robust-completion
  "Production-ready completion with full error handling."
  [provider messages opts & {:keys [max-retries timeout-ms]
                             :or {max-retries 3 timeout-ms 30000}}]
  (let [start-time (System/currentTimeMillis)]
    (loop [attempt 1]
      (let [elapsed (- (System/currentTimeMillis) start-time)]
        (when (> elapsed timeout-ms)
          (throw (errors/timeout-error :client timeout-ms)))

        (let [result
              (try
                {:status :success
                 :value (llm/chat-completion provider messages opts)}
                (catch Exception e
                  (cond
                    ;; Non-retryable errors
                    (errors/authentication-error? e)
                    {:status :auth-failed :error e}

                    (errors/validation-error? e)
                    {:status :validation-failed :error e}

                    ;; Retryable errors
                    (and (errors/retryable? e) (<= attempt max-retries))
                    {:status :retry
                     :error e
                     :delay (cond
                              (errors/rate-limited? e)
                              (* 1000 (or (errors/retry-after e) 60))

                              (errors/server-error? e)
                              (* 1000 (Math/pow 2 (dec attempt)))

                              :else 1000)}

                    ;; Max retries exceeded
                    :else
                    {:status :max-retries-exceeded :error e})))]

          (case (:status result)
            :success
            (:value result)

            :retry
            (do
              (println (format "Attempt %d/%d failed, retrying in %dms..."
                               attempt max-retries (long (:delay result))))
              (Thread/sleep (long (:delay result)))
              (recur (inc attempt)))

            ;; All failure cases
            (throw (:error result))))))))

;; Usage
(try
  (robust-completion provider
    [{:role :user :content "Hello!"}]
    {:temperature 0.7}
    :max-retries 3
    :timeout-ms 60000)
  (catch Exception e
    (println "Final failure:" (ex-message e))))
```

