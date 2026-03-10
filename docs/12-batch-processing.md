# Batch Processing

Batch processing APIs allow you to submit large volumes of requests for asynchronous processing at reduced costs. Both OpenAI and Anthropic offer batch APIs with significant price discounts.

## Overview

### Why Use Batch Processing?

| Benefit | Description |
|---------|-------------|
| **Cost Savings** | Up to 50% reduction compared to synchronous API calls |
| **Higher Throughput** | Process thousands of requests without rate limit concerns |
| **Reliability** | Automatic retries and guaranteed processing |
| **Simplicity** | Submit once, retrieve results when ready |

### When to Use Batches

- Processing large datasets (translations, classifications, summaries)
- Non-time-sensitive workloads
- Cost-sensitive applications
- Data pipelines and ETL processes

### Provider Comparison

| Feature | OpenAI | Anthropic |
|---------|--------|-----------|
| Completion Window | 24 hours | 24 hours |
| Cost Reduction | 50% | 50% |
| Max Requests | 50,000 per batch | 10,000 per batch |
| Input Format | JSONL file upload | Direct JSON array |

## The Batch Protocol

```clojure
(defprotocol BatchProvider
  (create-batch [this requests options])
  (get-batch [this batch-id])
  (cancel-batch [this batch-id])
  (list-batches [this options]))
```

## Request Format

Each request in a batch has:

```clojure
{:custom-id "unique-id"           ; Your identifier for tracking
 :messages [{:role :user          ; Same format as chat-completion
             :content "..."}]
 :params {:model "gpt-4o"         ; Model and other options
          :temperature 0.7
          :max-tokens 100}}
```

## OpenAI Batch Processing

### Creating a Provider

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])

(def provider (batch/create-provider {}))
```

### Creating a Batch

```clojure
(def requests
  [{:custom-id "translate-1"
    :messages [{:role :user :content "Translate to French: Hello"}]
    :params {:model "gpt-4o-mini" :max-tokens 50}}
   {:custom-id "translate-2"
    :messages [{:role :user :content "Translate to French: Goodbye"}]
    :params {:model "gpt-4o-mini" :max-tokens 50}}
   {:custom-id "translate-3"
    :messages [{:role :user :content "Translate to French: Thank you"}]
    :params {:model "gpt-4o-mini" :max-tokens 50}}])

(def batch-job (batch-core/create-batch provider requests {}))
;; => {:id "batch_abc123"
;;     :status :validating
;;     :created-at 1699000000
;;     :request-counts {:total 3 :completed 0 :failed 0}}
```

### Checking Batch Status

```clojure
(batch-core/get-batch provider "batch_abc123")
;; => {:id "batch_abc123"
;;     :status :in_progress
;;     :request-counts {:total 3 :completed 1 :failed 0}
;;     ...}
```

### Batch Statuses

| Status | Description |
|--------|-------------|
| `:validating` | Input file is being validated |
| `:in_progress` | Requests are being processed |
| `:completed` | All requests finished successfully |
| `:failed` | Batch failed (check error file) |
| `:expired` | Batch didn't complete within window |
| `:cancelled` | Batch was cancelled |

### Waiting for Completion

```clojure
;; Block until batch completes (with polling)
(def final-status
  (batch/wait-for-completion provider "batch_abc123"
    {:poll-interval-ms 30000    ; Check every 30 seconds
     :timeout-ms 3600000}))     ; Timeout after 1 hour

(println "Final status:" (:status final-status))
```

### Retrieving Results

```clojure
(def results (batch/get-results provider "batch_abc123"))
;; => {"translate-1" {:status 200 :body {:choices [...]} :error nil}
;;     "translate-2" {:status 200 :body {:choices [...]} :error nil}
;;     "translate-3" {:status 200 :body {:choices [...]} :error nil}}

;; Extract the actual responses
(doseq [[id result] results]
  (println id "=>" (get-in result [:body :choices 0 :message :content])))
```

## Anthropic Batch Processing

### Creating a Provider

```clojure
(require '[llm-clj.batch.anthropic :as batch])
(require '[llm-clj.batch.core :as batch-core])

(def provider (batch/create-provider {}))
```

### Creating a Batch

```clojure
(def requests
  [{:custom-id "summary-1"
    :messages [{:role :user :content "Summarize: The quick brown fox..."}]
    :params {:model "claude-3-haiku-20240307" :max-tokens 100}}
   {:custom-id "summary-2"
    :messages [{:role :user :content "Summarize: Lorem ipsum dolor..."}]
    :params {:model "claude-3-haiku-20240307" :max-tokens 100}}])

(def batch-job (batch-core/create-batch provider requests {}))
;; => {:id "batch_xyz789"
;;     :status :in-progress
;;     :created-at "2024-01-15T10:00:00Z"
;;     ...}
```

### Retrieving Results

```clojure
(def results (batch/get-results provider "batch_xyz789"))
;; => {"summary-1" {:result {...} :error nil}
;;     "summary-2" {:result {...} :error nil}}
```

## REPL Examples

### Complete OpenAI Batch Workflow

Copy and paste this entire block:

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])

(def provider (batch/create-provider {}))

;; Create batch requests
(def translation-requests
  (for [i (range 10)]
    {:custom-id (str "item-" i)
     :messages [{:role :system :content "You are a translator."}
                {:role :user :content (str "Translate to Spanish: Item number " i)}]
     :params {:model "gpt-4o-mini"
              :temperature 0.3
              :max-tokens 50}}))

;; Submit batch
(def job (batch-core/create-batch provider (vec translation-requests) {}))
(println "Batch created:" (:id job))
(println "Status:" (:status job))

;; Poll for status (in real use, you'd want longer intervals)
(defn check-status []
  (let [status (batch-core/get-batch provider (:id job))]
    (println "Status:" (:status status)
             "Completed:" (get-in status [:request-counts :completed])
             "/" (get-in status [:request-counts :total]))
    status))

(check-status)

;; When complete, get results
(when (= :completed (:status (check-status)))
  (let [results (batch/get-results provider (:id job))]
    (doseq [[id result] (sort-by key results)]
      (println id "=>" (get-in result [:body :choices 0 :message :content])))))
```

### Batch Translation Service

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])

(def provider (batch/create-provider {}))

(defn translate-batch
  "Translates a collection of texts to a target language using batch processing."
  [texts target-language]
  (let [requests (map-indexed
                   (fn [idx text]
                     {:custom-id (str "text-" idx)
                      :messages [{:role :system
                                  :content (str "Translate the following text to "
                                                target-language ". Only output the translation.")}
                                 {:role :user :content text}]
                      :params {:model "gpt-4o-mini"
                               :temperature 0.2
                               :max-tokens 500}})
                   texts)

        ;; Submit batch
        job (batch-core/create-batch provider (vec requests) {})
        _ (println "Submitted batch:" (:id job))

        ;; Wait for completion
        final (batch/wait-for-completion provider (:id job)
                {:poll-interval-ms 10000})]

    (if (= :completed (:status final))
      (let [results (batch/get-results provider (:id job))]
        (->> (range (count texts))
             (map (fn [i]
                    (let [result (get results (str "text-" i))]
                      (get-in result [:body :choices 0 :message :content]))))
             vec))
      (throw (ex-info "Batch failed" {:status final})))))

;; Usage
(def texts ["Hello, how are you?"
            "The weather is nice today."
            "I love programming in Clojure."])

(def translations (translate-batch texts "French"))
(doseq [[original translated] (map vector texts translations)]
  (println original "=>" translated))
```

### Batch Classification

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])

(def provider (batch/create-provider {}))

(defn classify-batch
  "Classifies texts into categories using batch processing."
  [texts categories]
  (let [category-str (clojure.string/join ", " categories)
        requests (map-indexed
                   (fn [idx text]
                     {:custom-id (str "classify-" idx)
                      :messages [{:role :system
                                  :content (str "Classify the following text into one of these categories: "
                                                category-str
                                                ". Respond with only the category name.")}
                                 {:role :user :content text}]
                      :params {:model "gpt-4o-mini"
                               :temperature 0
                               :max-tokens 20}})
                   texts)

        job (batch-core/create-batch provider (vec requests) {})
        final (batch/wait-for-completion provider (:id job)
                {:poll-interval-ms 10000})]

    (when (= :completed (:status final))
      (let [results (batch/get-results provider (:id job))]
        (->> (range (count texts))
             (map (fn [i]
                    {:text (nth texts i)
                     :category (-> (get results (str "classify-" i))
                                   (get-in [:body :choices 0 :message :content])
                                   clojure.string/trim)}))
             vec)))))

;; Usage
(def support-tickets
  ["My order hasn't arrived yet"
   "How do I reset my password?"
   "I want a refund for my purchase"
   "Great product, very satisfied!"
   "The app keeps crashing"])

(def categories ["shipping" "account" "refund" "feedback" "technical"])

(classify-batch support-tickets categories)
;; => [{:text "My order hasn't arrived yet" :category "shipping"}
;;     {:text "How do I reset my password?" :category "account"}
;;     ...]
```

## Managing Batches

### Listing Batches

```clojure
(batch-core/list-batches provider {:limit 10})
;; => {:batches [{:id "batch_1" :status :completed ...}
;;               {:id "batch_2" :status :in_progress ...}]
;;     :has-more true
;;     :first-id "batch_1"
;;     :last-id "batch_2"}

;; Pagination
(batch-core/list-batches provider {:limit 10 :after "batch_2"})
```

### Cancelling a Batch

```clojure
(batch-core/cancel-batch provider "batch_abc123")
;; => {:id "batch_abc123" :status :cancelling ...}
```

### Downloading Error Files (OpenAI)

```clojure
(let [status (batch-core/get-batch provider "batch_abc123")]
  (when (:error-file-id status)
    (let [errors (batch/download-file (:api-key provider) (:error-file-id status))]
      (println "Errors:" errors))))
```

## Options Reference

### Create Batch Options

```clojure
{:completion-window "24h"     ; OpenAI: completion time window
 :metadata {:project "x"}}    ; OpenAI: arbitrary metadata
```

### List Batches Options

```clojure
{:limit 20                    ; Max results per page
 :after "batch_xyz"}          ; Pagination cursor
```

### Wait for Completion Options

```clojure
{:poll-interval-ms 60000      ; How often to check status (default: 1 min)
 :timeout-ms 86400000}        ; Max wait time (default: 24 hours)
```

## Response Structure

### Batch Status

```clojure
{:id "batch_abc123"
 :status :completed
 :created-at 1699000000
 :completed-at 1699003600
 :request-counts {:total 100
                  :completed 98
                  :failed 2}
 :output-file-id "file_xyz"    ; OpenAI: results file
 :error-file-id "file_err"     ; OpenAI: errors file
 :results-url "https://..."    ; Anthropic: results URL
 :metadata {:project "x"}}
```

### Result Format (OpenAI)

```clojure
{"custom-id" {:status 200
              :body {:id "chatcmpl-..."
                     :choices [{:message {:content "..."}}]
                     :usage {...}}
              :error nil}}
```

### Result Format (Anthropic)

```clojure
{"custom-id" {:result {:type "message"
                       :content [{:type "text" :text "..."}]}
              :error nil}}
```

## Error Handling

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])
(require '[llm-clj.errors :as errors])

(defn safe-batch-process [provider requests]
  (try
    (let [job (batch-core/create-batch provider requests {})
          final (batch/wait-for-completion provider (:id job)
                  {:timeout-ms 7200000})]  ; 2 hour timeout

      (case (:status final)
        :completed
        {:success true
         :results (batch/get-results provider (:id job))}

        :failed
        {:success false
         :error :batch-failed
         :failed-count (get-in final [:request-counts :failed])}

        :expired
        {:success false
         :error :batch-expired}

        :cancelled
        {:success false
         :error :batch-cancelled}))

    (catch Exception e
      (cond
        (errors/timeout? e)
        {:success false :error :polling-timeout}

        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        :else
        {:success false :error :unknown :exception e}))))
```

## Complete Application: Bulk Data Processing

```clojure
(require '[llm-clj.batch.openai :as batch])
(require '[llm-clj.batch.core :as batch-core])

;; === Bulk Data Processing Pipeline ===

(def provider (batch/create-provider {}))

(defn process-in-batches
  "Processes data items in batches, handling large volumes efficiently."
  [items process-fn batch-size]
  (let [batches (partition-all batch-size items)
        results (atom {})]

    (doseq [[batch-idx batch-items] (map-indexed vector batches)]
      (println (str "Processing batch " (inc batch-idx) "/" (count batches)))

      ;; Create requests for this batch
      (let [requests (map-indexed
                       (fn [idx item]
                         (let [custom-id (str "batch-" batch-idx "-item-" idx)]
                           (assoc (process-fn item) :custom-id custom-id)))
                       batch-items)

            ;; Submit batch
            job (batch-core/create-batch provider (vec requests) {})
            _ (println "  Submitted:" (:id job))

            ;; Wait for completion
            final (batch/wait-for-completion provider (:id job)
                    {:poll-interval-ms 30000})]

        (when (= :completed (:status final))
          (let [batch-results (batch/get-results provider (:id job))]
            (swap! results merge batch-results)
            (println "  Completed:" (count batch-results) "items")))))

    @results))

;; Example: Summarize articles
(defn article->request [article]
  {:messages [{:role :system
               :content "Summarize this article in 2-3 sentences."}
              {:role :user
               :content (:content article)}]
   :params {:model "gpt-4o-mini"
            :max-tokens 150
            :temperature 0.3}})

;; Usage
(def articles
  [{:id 1 :content "Long article about AI..."}
   {:id 2 :content "Long article about climate..."}
   ;; ... many more articles
   ])

(def summaries
  (process-in-batches articles article->request 100))

;; Process results
(doseq [article articles]
  (let [result-key (str "batch-0-item-" (dec (:id article)))
        summary (get-in summaries [result-key :body :choices 0 :message :content])]
    (println "Article" (:id article) ":" summary)))
```

## Best Practices

1. **Use meaningful custom IDs** - Makes it easy to match results to inputs
2. **Handle partial failures** - Check individual result statuses
3. **Set appropriate timeouts** - Don't wait forever; have fallback plans
4. **Monitor batch progress** - Log completion percentages
5. **Batch similar requests** - Same model/params for efficiency
6. **Consider batch size** - Balance between API limits and manageability

## Limitations

- **24-hour window**: Batches must complete within 24 hours
- **No streaming**: Results are only available after completion
- **Model support**: Not all models support batch processing
- **OpenAI file limits**: 100MB max file size, 50,000 requests per batch
- **Anthropic limits**: 10,000 requests per batch

## Next Steps

- [Token Counting](13-token-counting.md) - Estimate costs before batching
- [Error Handling](06-errors.md) - Handle batch errors gracefully
- [Embeddings](10-embeddings.md) - Batch embedding generation
