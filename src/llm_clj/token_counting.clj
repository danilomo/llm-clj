(ns llm-clj.token-counting
  "Token counting utilities for LLM requests.

  Provides:
  - Anthropic's token counting API for exact counts
  - Estimation utilities for rough token counting
  - Context window management helpers"
  (:require [llm-clj.errors :as errors]
            [llm-clj.providers.anthropic :as anthropic]
            [clojure.string :as str]))

;; Exact token counting via Anthropic API

(defn count-tokens-anthropic
  "Counts tokens for an Anthropic messages request.

  Delegates to anthropic/count-tokens. See that function for full documentation.

  Returns {:input-tokens N}"
  [messages options]
  (anthropic/count-tokens (:api-key options) messages options))

;; Estimation utilities (for providers without token counting API)

(defn estimate-tokens
  "Rough estimation of tokens based on character count.
  Uses ~4 characters per token as a heuristic.

  This is an approximation and may vary by model and content."
  [text]
  (if (or (nil? text) (empty? (str text)))
    0
    (int (Math/ceil (/ (count (str text)) 4.0)))))

(defn estimate-message-tokens
  "Estimates tokens for a message, including role overhead."
  [message]
  (let [content (:content message)
        content-text (if (vector? content)
                       ;; Handle multi-part content
                       (->> content
                            (filter #(or (= :text (:type %))
                                         (string? %)))
                            (map #(if (string? %) % (:text %)))
                            (str/join " "))
                       (str content))
        content-tokens (estimate-tokens content-text)
        ;; Approximate overhead for role, formatting
        overhead 4]
    (+ content-tokens overhead)))

(defn estimate-conversation-tokens
  "Estimates total tokens for a conversation."
  [messages]
  (reduce + (map estimate-message-tokens messages)))

;; Context window management

(def context-windows
  "Known context window sizes for common models."
  {:claude-3-opus 200000
   :claude-3-sonnet 200000
   :claude-3-haiku 200000
   :claude-sonnet-4 200000
   :claude-opus-4 200000
   :gpt-4o 128000
   :gpt-4o-mini 128000
   :gpt-4-turbo 128000
   :gpt-4 8192
   :gpt-3.5-turbo 16385
   :o1 200000
   :o1-mini 128000})

(defn get-context-window
  "Returns the context window size for a model.
  Falls back to pattern matching on model name, then a conservative default."
  [model]
  (or (get context-windows (keyword model))
      ;; Try to extract from model name patterns
      (cond
        (re-find #"claude.*opus.*4" (str model)) 200000
        (re-find #"claude.*sonnet.*4" (str model)) 200000
        (re-find #"claude-3" (str model)) 200000
        (re-find #"claude" (str model)) 200000
        (re-find #"gpt-4o" (str model)) 128000
        (re-find #"gpt-4-turbo" (str model)) 128000
        (re-find #"gpt-4" (str model)) 8192
        (re-find #"gpt-3.5" (str model)) 16385
        (re-find #"o1-mini" (str model)) 128000
        (re-find #"o1" (str model)) 200000
        :else 8000))) ;; Conservative default

(defn fits-context?
  "Checks if messages fit within a context window.

  Options:
  - :max-tokens - Context window size (required)
  - :reserve - Tokens to reserve for response (default: 4096)"
  [messages {:keys [max-tokens reserve] :or {reserve 4096}}]
  (when-not max-tokens
    (throw (errors/validation-error "max-tokens is required" {:reserve reserve})))
  (let [estimated (estimate-conversation-tokens messages)]
    (<= estimated (- max-tokens reserve))))

(defn fits-model-context?
  "Checks if messages fit within a model's context window.

  Options:
  - :model - Model name to look up context window
  - :reserve - Tokens to reserve for response (default: 4096)"
  [messages {:keys [model reserve] :or {reserve 4096}}]
  (when-not model
    (throw (errors/validation-error "model is required" {:reserve reserve})))
  (let [max-tokens (get-context-window model)]
    (fits-context? messages {:max-tokens max-tokens :reserve reserve})))

(defn available-tokens
  "Calculates how many tokens are available for response given messages and context window.

  Options:
  - :max-tokens - Context window size (required)

  Returns the number of tokens available, or 0 if already exceeded."
  [messages {:keys [max-tokens]}]
  (when-not max-tokens
    (throw (errors/validation-error "max-tokens is required" {})))
  (let [estimated (estimate-conversation-tokens messages)
        available (- max-tokens estimated)]
    (max 0 available)))

(defn truncate-to-fit
  "Truncates messages from the beginning (oldest) to fit within context.
  Always keeps the system message and at least one user message.

  Options:
  - :max-tokens - Context window size (required)
  - :reserve - Tokens to reserve for response (default: 4096)

  Returns truncated message list, or original if already fits."
  [messages {:keys [max-tokens reserve] :or {reserve 4096}}]
  (when-not max-tokens
    (throw (errors/validation-error "max-tokens is required" {:reserve reserve})))
  (if (fits-context? messages {:max-tokens max-tokens :reserve reserve})
    messages
    ;; Need to truncate - keep system messages and trim from oldest
    (let [system-msgs (filter #(= :system (:role %)) messages)
          other-msgs (vec (remove #(= :system (:role %)) messages))
          available (- max-tokens reserve (estimate-conversation-tokens system-msgs))]
      (loop [msgs other-msgs]
        (if (or (<= (count msgs) 1)
                (<= (estimate-conversation-tokens msgs) available))
          (vec (concat system-msgs msgs))
          (recur (vec (rest msgs))))))))
