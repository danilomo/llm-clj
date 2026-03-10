(ns llm-clj.errors
  "Structured error types for LLM API interactions."
  (:require [cheshire.core :as json]))

;; Error type hierarchy
(derive ::rate-limit-error ::api-error)
(derive ::authentication-error ::api-error)
(derive ::not-found-error ::api-error)
(derive ::server-error ::api-error)
(derive ::timeout-error ::api-error)

(defn- parse-error-body
  "Attempts to parse error body as JSON, returns string if not JSON."
  [body]
  (if (string? body)
    (try
      (json/parse-string body true)
      (catch Exception _ body))
    body))

(defn- extract-error-message
  "Extracts error message from parsed body."
  [parsed-body]
  (or (get-in parsed-body [:error :message])
      (get parsed-body :message)
      (get-in parsed-body [:error :type])
      (str parsed-body)))

(defn- extract-retry-after
  "Extracts retry-after value from headers."
  [headers]
  (when-let [retry (or (get headers "retry-after")
                       (get headers "Retry-After"))]
    (try (Long/parseLong retry) (catch Exception _ nil))))

;; Error constructors

(defn api-error
  "Creates an API error from response data.
  Automatically determines specific error type based on status code."
  [provider status body & {:keys [headers]}]
  (let [parsed-body (parse-error-body body)
        message (extract-error-message parsed-body)
        error-type (cond
                     (= 429 status) ::rate-limit-error
                     (#{401 403} status) ::authentication-error
                     (= 404 status) ::not-found-error
                     (>= status 500) ::server-error
                     :else ::api-error)
        data (cond-> {:type error-type
                      :provider provider
                      :status status
                      :body parsed-body}
               (= 429 status) (assoc :retry-after (extract-retry-after headers)))]
    (ex-info (str (name provider) " API error: " message) data)))

(defn rate-limit-error
  "Creates a rate limit error with retry information."
  [provider retry-after]
  (ex-info (str (name provider) " rate limit exceeded")
           {:type ::rate-limit-error
            :provider provider
            :status 429
            :retry-after retry-after}))

(defn timeout-error
  "Creates a timeout error."
  [provider timeout-ms]
  (ex-info (str (name provider) " request timed out after " timeout-ms "ms")
           {:type ::timeout-error
            :provider provider
            :timeout-ms timeout-ms}))

(defn validation-error
  "Creates a validation error for invalid input."
  [message data]
  (ex-info message
           {:type ::validation-error
            :data data}))

;; Error predicates

(defn error-type
  "Returns the error type keyword from an exception, or nil if not an llm-clj error."
  [e]
  (when (instance? clojure.lang.ExceptionInfo e)
    (get (ex-data e) :type)))

(defn api-error?
  "Returns true if the exception is any API error."
  [e]
  (when-let [t (error-type e)]
    (isa? t ::api-error)))

(defn rate-limited?
  "Returns true if the exception is a rate limit error."
  [e]
  (= ::rate-limit-error (error-type e)))

(defn authentication-error?
  "Returns true if the exception is an authentication error."
  [e]
  (= ::authentication-error (error-type e)))

(defn timeout?
  "Returns true if the exception is a timeout error."
  [e]
  (= ::timeout-error (error-type e)))

(defn validation-error?
  "Returns true if the exception is a validation error."
  [e]
  (= ::validation-error (error-type e)))

(defn server-error?
  "Returns true if the exception is a server error (5xx)."
  [e]
  (= ::server-error (error-type e)))

(defn retryable?
  "Returns true if the error is retryable (rate limit or server error)."
  [e]
  (let [t (error-type e)]
    (or (= ::rate-limit-error t)
        (= ::server-error t)
        (= ::timeout-error t))))

;; Helper functions

(defn retry-after
  "Returns the retry-after value in seconds from a rate limit error, or nil."
  [e]
  (when (rate-limited? e)
    (get (ex-data e) :retry-after)))

(defn error-status
  "Returns the HTTP status code from an API error, or nil."
  [e]
  (get (ex-data e) :status))

(defn error-provider
  "Returns the provider name from an error, or nil."
  [e]
  (get (ex-data e) :provider))
