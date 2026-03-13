(ns llm-clj.tracing.core
  "Core tracing protocol and context management.

   This namespace provides:
   - Tracer protocol for backend implementations
   - Dynamic vars for context propagation
   - with-span and with-trace macros for instrumentation

   Usage:
   (require '[llm-clj.tracing.core :as tracing])

   ;; Enable tracing with stdout backend
   (tracing/configure! {:enabled true :backend :stdout})

   ;; Instrument code
   (tracing/with-span [s \"llm.completion\" {:model \"gpt-4\"}]
     (do-completion ...))"
  (:require [llm-clj.tracing.span :as span]
            [llm-clj.tracing.config :as config]
            [clojure.core.async :as async]))

;; Tracer protocol - implemented by each backend
(defprotocol Tracer
  "Protocol for tracing backends."
  (start-span [this span-name attributes parent-ctx]
    "Starts a new span with the given name and attributes.
     parent-ctx is a map with :trace-id and :span-id from parent span, or nil.
     Returns a span object.")
  (end-span [this span]
    "Ends a span, recording its completion.")
  (record-exception [this span exception]
    "Records an exception on a span.")
  (set-status [this span status message]
    "Sets the status of a span. Status is :ok, :error, or :unset."))

;; Dynamic vars for context propagation
(def ^:dynamic *trace-context*
  "Current span context for propagation.
   Contains :trace-id and :span-id of the active span."
  nil)

(def ^:dynamic *tracer*
  "Active tracer instance. Can be overridden with binding."
  nil)

(def ^:dynamic *current-span*
  "Current active span object."
  nil)

;; Backend registry and management
(defonce ^:private backend-registry (atom {}))

(defn register-backend!
  "Registers a backend factory function.

   Arguments:
   - backend-key: Keyword identifying the backend (e.g., :stdout, :json)
   - factory-fn: Function that takes config map and returns a Tracer instance"
  [backend-key factory-fn]
  (swap! backend-registry assoc backend-key factory-fn))

(defn- create-backend
  "Creates a backend instance from the registry.
   Returns nil if backend not found."
  [backend-key config]
  (when-let [factory (@backend-registry backend-key)]
    (factory config)))

;; Lazy tracer initialization
(defonce ^:private active-tracer (atom nil))

(defn- ensure-tracer
  "Ensures a tracer is available, creating one if needed.
   Uses *tracer* if bound, otherwise uses/creates the global tracer."
  []
  (or *tracer*
      @active-tracer
      (let [cfg (config/get-config)
            backend-key (:backend cfg)
            tracer (create-backend backend-key cfg)]
        (reset! active-tracer tracer)
        tracer)))

(defn set-tracer!
  "Sets the global tracer instance.
   Useful for testing or custom tracer injection."
  [tracer]
  (reset! active-tracer tracer))

(defn reset-tracer!
  "Resets the global tracer, forcing re-creation on next use."
  []
  (reset! active-tracer nil))

;; Public API functions

(defn current-context
  "Returns the current trace context for propagation.
   Returns nil if no active span."
  []
  *trace-context*)

(defn current-span
  "Returns the current active span, or nil if none."
  []
  *current-span*)

(defn add-attribute
  "Adds an attribute to the current span.
   No-op if no active span."
  [key value]
  (when-let [s *current-span*]
    (span/set-span-attribute s key value)))

(defn add-attributes
  "Adds multiple attributes to the current span.
   No-op if no active span."
  [attrs]
  (when-let [s *current-span*]
    (span/set-span-attributes s attrs)))

(defn add-event
  "Adds an event to the current span.
   No-op if no active span."
  ([event-name]
   (add-event event-name {}))
  ([event-name attrs]
   (when-let [s *current-span*]
     (span/add-span-event s event-name attrs))))

;; Core span execution

(defn execute-with-span*
  "Internal implementation of span execution.
   Handles span lifecycle and exception recording."
  [tracer span-name attributes parent-ctx body-fn]
  (if-not (and (config/enabled?) (config/should-sample?))
    ;; Tracing disabled or not sampled - just run body
    (body-fn)
    ;; Tracing enabled and sampled
    (let [tracer (or tracer (ensure-tracer))]
      (if-not tracer
        ;; No tracer available - run body without tracing
        (body-fn)
        ;; Execute with tracing
        (let [s (start-span tracer span-name attributes parent-ctx)
              ctx (span/span->context s)]
          (try
            (binding [*trace-context* ctx
                      *current-span* s
                      *tracer* tracer]
              (let [result (body-fn)]
                (set-status tracer s span/status-ok nil)
                (end-span tracer s)
                result))
            (catch Exception e
              (record-exception tracer s e)
              (set-status tracer s span/status-error (.getMessage e))
              (end-span tracer s)
              (throw e))))))))

(defmacro with-span
  "Executes body within a new span, creating a child of the current context.

   Arguments:
   - binding: A vector [span-var span-name attributes?]
     - span-var: Symbol to bind the span to (can be _ if not needed)
     - span-name: String name for the span
     - attributes: Optional map of span attributes
   - body: Forms to execute within the span

   The span automatically:
   - Inherits trace context from parent spans
   - Records exceptions and sets error status on failure
   - Sets success status and ends span on completion

   Example:
   (with-span [s \"llm.completion\" {:model \"gpt-4\"}]
     (do-completion provider messages opts))"
  [[span-var span-name & [attributes]] & body]
  `(execute-with-span*
    nil
    ~span-name
    ~(or attributes {})
    *trace-context*
    (fn []
      (let [~span-var *current-span*]
        ~@body))))

(defmacro with-trace
  "Executes body within a new root trace span.

   Similar to with-span but starts a new trace (no parent context).
   Use this for top-level operations in agentic libraries.

   Arguments:
   - binding: A vector [span-var span-name attributes?]
   - body: Forms to execute

   Example:
   (with-trace [t \"agent.turn\" {:agent \"researcher\"}]
     (api/chat provider messages opts))"
  [[span-var span-name & [attributes]] & body]
  `(execute-with-span*
    nil
    ~span-name
    ~(or attributes {})
    nil  ;; No parent context - start new trace
    (fn []
      (let [~span-var *current-span*]
        ~@body))))

(defmacro with-tracer
  "Executes body with a specific tracer bound.

   Useful for testing or using a custom tracer.

   Example:
   (with-tracer [my-test-tracer]
     (with-span [s \"test.span\" {}]
       (do-something)))"
  [[tracer] & body]
  `(binding [*tracer* ~tracer]
     ~@body))

;; Streaming support

(defn wrap-stream-channel
  "Wraps a streaming channel to record span events.

   Takes a core.async channel and returns a new channel that:
   - Passes through all values
   - Records span events for deltas (if enabled)
   - Ends the span on :complete or :error events

   Arguments:
   - tracer: The tracer to use
   - span: The span to record events on
   - ch: The source channel
   - opts: Options map:
     - :record-deltas? - Record an event for each delta (default: false)

   Returns a new channel with the same values."
  [tracer s ch {:keys [record-deltas?] :or {record-deltas? false}}]
  (let [out-ch (async/chan)]
    (async/go-loop []
      (if-let [v (async/<! ch)]
        (do
          (when record-deltas?
            (when (= :delta (:type v))
              (add-event "stream.delta" {:content-length (count (:content v ""))})))
          (case (:type v)
            :complete
            (do
              (when-let [usage (:usage v)]
                (add-attributes
                 {span/attr-usage-input-tokens (:input-tokens usage)
                  span/attr-usage-output-tokens (:output-tokens usage)
                  span/attr-usage-total-tokens (:total-tokens usage)}))
              (add-attribute span/attr-llm-finish-reason (:finish-reason v))
              (set-status tracer s span/status-ok nil)
              (end-span tracer s))
            :error
            (do
              (when-let [e (:error v)]
                (record-exception tracer s e))
              (set-status tracer s span/status-error (str (:error v)))
              (end-span tracer s))
            nil)
          (async/>! out-ch v)
          (when-not (#{:complete :error} (:type v))
            (recur)))
        (async/close! out-ch)))
    out-ch))

(defmacro with-streaming-span
  "Creates a span for streaming operations.

   Unlike with-span, this returns the channel immediately and the span
   is ended asynchronously when the stream completes.

   Arguments:
   - binding: [span-var span-name attributes?]
   - ch-expr: Expression that returns the streaming channel
   - opts: Options for wrap-stream-channel

   Returns the wrapped channel.

   Example:
   (with-streaming-span [s \"llm.stream\" {:model \"gpt-4\"}]
     (core/chat-completion-stream provider messages opts)
     {:record-deltas? true})"
  [[span-var span-name & [attributes]] ch-expr & [opts]]
  `(if-not (and (config/enabled?) (config/should-sample?))
     ~ch-expr
     (let [tracer# (ensure-tracer)]
       (if-not tracer#
         ~ch-expr
         (let [s# (start-span tracer# ~span-name ~(or attributes {}) *trace-context*)
               ~span-var s#
               ch# ~ch-expr]
           (wrap-stream-channel tracer# s# ch# ~(or opts {})))))))

;; Re-export configure! for convenience
(defn configure!
  "Configures the tracing system. See llm-clj.tracing.config/configure! for details."
  [config-map]
  (config/configure! config-map)
  ;; Reset tracer so it picks up new config
  (reset-tracer!))
