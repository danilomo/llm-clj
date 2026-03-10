# Content Moderation

OpenAI's Moderation API checks text content against usage policies, detecting potentially harmful content across multiple categories. llm-clj provides a simple interface for content moderation with helpful utility functions.

## Overview

The Moderation API classifies text for:

| Category | Description |
|----------|-------------|
| **hate** | Content expressing hatred toward a group |
| **hate-threatening** | Hateful content with violence/harm |
| **harassment** | Content that harasses individuals |
| **harassment-threatening** | Harassment with violence/harm |
| **self-harm** | Content promoting self-harm |
| **self-harm-intent** | Expressing intent to self-harm |
| **self-harm-instructions** | Instructions for self-harm |
| **sexual** | Sexually explicit content |
| **sexual-minors** | Sexual content involving minors |
| **violence** | Content depicting violence |
| **violence-graphic** | Graphic violence depictions |
| **illicit** | Content about illegal activities |
| **illicit-violent** | Illegal activities with violence |

## Why Use Moderation?

| Use Case | Description |
|----------|-------------|
| **Input Validation** | Screen user inputs before processing |
| **Output Filtering** | Check LLM responses before showing |
| **Community Safety** | Moderate user-generated content |
| **Policy Compliance** | Ensure content meets platform policies |
| **Cost Savings** | Reject problematic content before LLM calls |

## Basic Usage

### Single Text Moderation

```clojure
(require '[llm-clj.moderation.openai :as mod])

;; Moderate a single piece of text
(mod/moderate "Hello, how are you today?")
;; => {:flagged false
;;     :categories {:hate false :violence false ...}
;;     :category-scores {:hate 0.00001 :violence 0.00002 ...}
;;     :model "omni-moderation-latest"
;;     :id "modr-..."}
```

### Checking the Result

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def result (mod/moderate "some user input"))

;; Check if content is flagged
(mod/flagged? result)
;; => true or false

;; Get which categories were flagged
(mod/flagged-categories result)
;; => #{:violence :harassment}

;; Get the score for a specific category
(mod/category-score result :violence)
;; => 0.85
```

### Batch Moderation

```clojure
(require '[llm-clj.moderation.openai :as mod])

;; Moderate multiple texts at once
(def results (mod/moderate ["message 1" "message 2" "message 3"]))
;; => {:results [{:flagged false ...} {:flagged true ...} {:flagged false ...}]
;;     :model "omni-moderation-latest"
;;     :id "modr-..."}

;; Check if any in the batch are flagged
(mod/any-flagged? results)
;; => true
```

## REPL Examples

### Basic Content Check

Copy and paste this entire block:

```clojure
(require '[llm-clj.moderation.openai :as mod])

;; Check safe content
(def safe-result (mod/moderate "Hello! How can I help you today?"))

(println "Flagged:" (:flagged safe-result))
(println "Categories flagged:" (mod/flagged-categories safe-result))

;; Look at the category scores
(println "\nCategory scores:")
(doseq [[category score] (sort-by val > (:category-scores safe-result))]
  (println (format "  %s: %.6f" (name category) score)))
```

### Finding High-Risk Categories

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def result (mod/moderate "Some content to check"))

;; Get categories with scores above a threshold
(def high-risk (mod/high-score-categories result 0.3))

(if (empty? high-risk)
  (println "No high-risk categories detected")
  (do
    (println "High-risk categories:")
    (doseq [[category score] high-risk]
      (println (format "  %s: %.2f" (name category) score)))))
```

### Pre-screening User Input

```clojure
(require '[llm-clj.moderation.openai :as mod])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

(def provider (openai/create-provider {}))

(defn safe-chat
  "Only processes user input if it passes moderation."
  [user-input]
  (let [mod-result (mod/moderate user-input)]
    (if (mod/flagged? mod-result)
      {:error "Content violates usage policies"
       :categories (mod/flagged-categories mod-result)}
      (let [response (llm/chat-completion provider
                       [{:role :user :content user-input}]
                       {:model "gpt-4o-mini" :max-tokens 500})]
        {:success true
         :response (:content response)}))))

;; Usage
(safe-chat "What's the weather like?")
;; => {:success true :response "I don't have access to weather data..."}
```

### Batch Processing User Messages

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def user-messages
  [{:id 1 :text "Hello there!"}
   {:id 2 :text "How do I learn programming?"}
   {:id 3 :text "What's a good recipe for pasta?"}])

;; Moderate all messages in one API call
(def results (mod/moderate (mapv :text user-messages)))

;; Pair results with original messages
(def annotated
  (mapv (fn [msg result]
          (assoc msg
                 :flagged (:flagged result)
                 :moderation result))
        user-messages
        (:results results)))

;; Filter to only safe messages
(def safe-messages (filter #(not (:flagged %)) annotated))

(println "Safe messages:" (count safe-messages) "/" (count user-messages))
(doseq [msg safe-messages]
  (println "  -" (:text msg)))
```

## Moderating Chat Conversations

### Add Moderation to Messages

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def conversation
  [{:role :user :content "Hello!"}
   {:role :assistant :content "Hi there! How can I help?"}
   {:role :user :content "Tell me about programming."}])

;; Add moderation results to each message
(def moderated (mod/moderate-messages conversation))

;; Check results
(doseq [msg moderated]
  (println (format "[%s] %s - flagged: %s"
                   (name (:role msg))
                   (subs (:content msg) 0 (min 30 (count (:content msg))))
                   (:flagged (:moderation msg)))))
```

### Filter to Safe Messages Only

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def messages
  [{:role :user :content "Safe message here"}
   {:role :user :content "Another safe message"}
   {:role :user :content "Yet another message"}])

;; Get only messages that pass moderation
(def safe-messages (mod/filter-safe-messages messages))

(println "Kept" (count safe-messages) "of" (count messages) "messages")
```

## Category Scores

Each category returns a score between 0 and 1:

| Score Range | Interpretation |
|-------------|----------------|
| 0.00 - 0.20 | Very unlikely to violate |
| 0.20 - 0.50 | Low risk |
| 0.50 - 0.80 | Moderate risk |
| 0.80 - 1.00 | High risk, likely flagged |

### Working with Scores

```clojure
(require '[llm-clj.moderation.openai :as mod])

(def result (mod/moderate "Some content"))

;; Get specific category score
(let [violence-score (mod/category-score result :violence)]
  (cond
    (nil? violence-score) (println "Category not found")
    (< violence-score 0.2) (println "Safe")
    (< violence-score 0.5) (println "Low risk")
    (< violence-score 0.8) (println "Moderate risk - review")
    :else (println "High risk - reject")))

;; Get all categories above a threshold
(mod/high-score-categories result 0.5)
;; => {:violence 0.72 :harassment 0.55}
```

## Response Structure

### Single Input Response

```clojure
{:flagged false                      ; Overall flag
 :categories {:hate false            ; Boolean for each category
              :hate-threatening false
              :harassment false
              :harassment-threatening false
              :self-harm false
              :self-harm-intent false
              :self-harm-instructions false
              :sexual false
              :sexual-minors false
              :violence false
              :violence-graphic false
              :illicit false
              :illicit-violent false}
 :category-scores {:hate 0.00001     ; Score (0-1) for each
                   :violence 0.00002
                   ...}
 :model "omni-moderation-latest"
 :id "modr-abc123"}
```

### Batch Input Response

```clojure
{:results [{:flagged false :categories {...} :category-scores {...}}
           {:flagged true :categories {...} :category-scores {...}}
           {:flagged false :categories {...} :category-scores {...}}]
 :model "omni-moderation-latest"
 :id "modr-abc123"}
```

## Options Reference

```clojure
{:api-key "sk-..."                    ; Optional, uses env var
 :model "omni-moderation-latest"}     ; Moderation model
```

### Available Models

| Model | Description |
|-------|-------------|
| `omni-moderation-latest` | Latest model, recommended (default) |
| `text-moderation-latest` | Text-only moderation |
| `text-moderation-stable` | Stable text moderation |

## Complete Application: Content Moderation System

```clojure
(require '[llm-clj.moderation.openai :as mod])
(require '[llm-clj.core :as llm])
(require '[llm-clj.providers.openai :as openai])

;; === Content Moderation System ===

(def provider (openai/create-provider {}))

(def moderation-config
  {:strict-categories #{:hate :violence :sexual-minors :self-harm}
   :score-threshold 0.7
   :log-flagged true})

(defn analyze-content
  "Analyzes content and returns detailed moderation info."
  [content]
  (let [result (mod/moderate content)
        flagged-cats (mod/flagged-categories result)
        high-scores (mod/high-score-categories result 0.5)
        strict-violation (some flagged-cats (:strict-categories moderation-config))]
    {:content (subs content 0 (min 100 (count content)))
     :flagged (:flagged result)
     :strict-violation strict-violation
     :flagged-categories flagged-cats
     :high-score-categories high-scores
     :action (cond
               strict-violation :reject
               (:flagged result) :review
               (seq high-scores) :caution
               :else :allow)}))

(defn moderate-and-respond
  "Moderates user input and generates response if safe."
  [user-input system-prompt]
  (let [analysis (analyze-content user-input)]
    (case (:action analysis)
      :reject
      {:status :rejected
       :reason "Content violates strict policies"
       :categories (:flagged-categories analysis)}

      :review
      {:status :pending-review
       :reason "Content flagged for review"
       :categories (:flagged-categories analysis)}

      :caution
      (do
        (when (:log-flagged moderation-config)
          (println "CAUTION:" (:high-score-categories analysis)))
        (let [response (llm/chat-completion provider
                         [{:role :system :content system-prompt}
                          {:role :user :content user-input}]
                         {:model "gpt-4o-mini" :max-tokens 500})]
          {:status :allowed-with-caution
           :response (:content response)
           :warnings (:high-score-categories analysis)}))

      :allow
      (let [response (llm/chat-completion provider
                       [{:role :system :content system-prompt}
                        {:role :user :content user-input}]
                       {:model "gpt-4o-mini" :max-tokens 500})]
        {:status :allowed
         :response (:content response)}))))

;; === Usage ===

(def system-prompt "You are a helpful assistant.")

(let [result (moderate-and-respond "What's the capital of France?" system-prompt)]
  (case (:status result)
    :allowed (println "Response:" (:response result))
    :allowed-with-caution (do
                            (println "Response:" (:response result))
                            (println "Warnings:" (:warnings result)))
    :pending-review (println "Pending review:" (:reason result))
    :rejected (println "Rejected:" (:reason result))))
```

## Complete Application: Community Forum Moderator

```clojure
(require '[llm-clj.moderation.openai :as mod])

;; === Forum Post Moderator ===

(defn create-moderator
  "Creates a moderation function with configurable thresholds."
  [config]
  (let [{:keys [auto-reject-threshold
                review-threshold
                allowed-categories]
         :or {auto-reject-threshold 0.9
              review-threshold 0.5
              allowed-categories #{}}} config]

    (fn [post]
      (let [result (mod/moderate (:content post))
            scores (:category-scores result)

            ;; Check for auto-reject
            reject-categories (->> scores
                                   (filter (fn [[cat score]]
                                             (and (>= score auto-reject-threshold)
                                                  (not (allowed-categories cat)))))
                                   (map first)
                                   set)

            ;; Check for review
            review-categories (->> scores
                                   (filter (fn [[cat score]]
                                             (and (>= score review-threshold)
                                                  (< score auto-reject-threshold)
                                                  (not (allowed-categories cat)))))
                                   (map first)
                                   set)]

        (cond
          (seq reject-categories)
          {:post-id (:id post)
           :action :auto-reject
           :reason reject-categories
           :scores (select-keys scores reject-categories)}

          (seq review-categories)
          {:post-id (:id post)
           :action :queue-for-review
           :reason review-categories
           :scores (select-keys scores review-categories)}

          :else
          {:post-id (:id post)
           :action :approve
           :scores scores})))))

;; === Usage ===

(def moderator (create-moderator
                 {:auto-reject-threshold 0.85
                  :review-threshold 0.4}))

;; Moderate forum posts
(def posts
  [{:id 1 :author "user1" :content "Great tutorial on Clojure!"}
   {:id 2 :author "user2" :content "I have a question about macros."}
   {:id 3 :author "user3" :content "Thanks for the help everyone!"}])

(doseq [post posts]
  (let [result (moderator post)]
    (println (format "Post %d: %s" (:post-id result) (name (:action result))))))
```

## Complete Application: Real-time Chat Moderator

```clojure
(require '[llm-clj.moderation.openai :as mod])

;; === Real-time Chat Moderation ===

(defn create-chat-moderator
  "Creates a stateful chat moderator that tracks user violations."
  []
  (let [user-violations (atom {})]

    {:moderate
     (fn [user-id message]
       (let [result (mod/moderate message)
             violations (get @user-violations user-id 0)]
         (when (:flagged result)
           (swap! user-violations update user-id (fnil inc 0)))

         (cond
           ;; User has too many violations
           (>= violations 3)
           {:action :user-banned
            :message "User has been banned due to repeated violations"}

           ;; Current message flagged
           (:flagged result)
           {:action :message-blocked
            :violations (inc violations)
            :categories (mod/flagged-categories result)
            :warning (if (>= (inc violations) 2)
                       "Final warning before ban"
                       "Content blocked")}

           ;; Message OK
           :else
           {:action :allowed
            :message message})))

     :get-violations
     (fn [user-id]
       (get @user-violations user-id 0))

     :reset-violations
     (fn [user-id]
       (swap! user-violations dissoc user-id))

     :ban-user
     (fn [user-id]
       (swap! user-violations assoc user-id 999))}))

;; === Usage ===

(def chat-mod (create-chat-moderator))

;; Simulate chat messages
(println ((:moderate chat-mod) "user123" "Hello everyone!"))
;; => {:action :allowed :message "Hello everyone!"}

;; Check violations
(println "Violations:" ((:get-violations chat-mod) "user123"))
;; => 0
```

## Error Handling

```clojure
(require '[llm-clj.moderation.openai :as mod])
(require '[llm-clj.errors :as errors])

(defn safe-moderate [content]
  (try
    {:success true
     :result (mod/moderate content)}
    (catch Exception e
      (cond
        (errors/authentication-error? e)
        {:success false
         :error :auth-failed
         :message "Check your API key"}

        (errors/rate-limited? e)
        {:success false
         :error :rate-limited
         :retry-after (errors/retry-after e)}

        (errors/validation-error? e)
        {:success false
         :error :invalid-input
         :message (ex-message e)}

        :else
        {:success false
         :error :unknown
         :message (ex-message e)}))))

;; Usage
(let [result (safe-moderate "some content")]
  (if (:success result)
    (if (mod/flagged? (:result result))
      (println "Content flagged!")
      (println "Content OK"))
    (println "Error:" (:error result))))
```

## Best Practices

1. **Moderate before LLM calls** - Save costs by rejecting problematic content early
2. **Use batch moderation** - Process multiple texts in one API call
3. **Set appropriate thresholds** - Adjust based on your use case
4. **Log flagged content** - Track patterns for policy refinement
5. **Handle edge cases** - Consider false positives in your workflow
6. **Combine with output moderation** - Check LLM responses too

## Limitations

- **Text only** - Does not analyze images (use Vision API separately)
- **English-focused** - Best accuracy for English content
- **Context-limited** - Each text analyzed independently
- **False positives** - May flag benign content discussing sensitive topics

## Cost Considerations

The Moderation API is:
- **Free** for most use cases (check OpenAI pricing)
- **Fast** compared to LLM calls
- **Efficient** for batch processing

Use moderation liberally to protect your application and users.

