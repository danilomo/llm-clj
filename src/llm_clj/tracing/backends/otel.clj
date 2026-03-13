(ns llm-clj.tracing.backends.otel
  "OpenTelemetry tracing backend.

   This backend integrates with the OpenTelemetry SDK to export traces
   to any OTEL-compatible backend (Jaeger, Zipkin, OTLP collectors, etc.).

   Requires optional dependencies - add the :otel profile to your project:

   :profiles {:otel {:dependencies [[io.opentelemetry/opentelemetry-api \"1.40.0\"]
                                    [io.opentelemetry/opentelemetry-sdk \"1.40.0\"]]}}

   If OTEL dependencies are not available, this backend will fall back to noop."
  (:require [llm-clj.tracing.core :as core]
            [llm-clj.tracing.span :as span]
            [llm-clj.tracing.config :as config]))

;; Check if OTEL classes are available
(def ^:private otel-available?
  (try
    (Class/forName "io.opentelemetry.api.trace.Tracer")
    true
    (catch ClassNotFoundException _
      false)))

(defmacro ^:private when-otel
  "Executes body only if OTEL is available."
  [& body]
  (if otel-available?
    `(do ~@body)
    nil))

;; OTEL interop helpers - only defined if OTEL is available
(when-otel
 (import '[io.opentelemetry.api GlobalOpenTelemetry]
         '[io.opentelemetry.api.trace Tracer Span SpanKind StatusCode]
         '[io.opentelemetry.api.common Attributes AttributeKey]
         '[io.opentelemetry.context Context]))

(defn- status-keyword->otel
  "Converts our status keyword to OTEL StatusCode."
  [status]
  (when-otel
   (case status
     :ok StatusCode/OK
     :error StatusCode/ERROR
     :unset StatusCode/UNSET
     StatusCode/UNSET)))

(defn- set-otel-attributes
  "Sets attributes on an OTEL span builder or span."
  [builder attrs]
  (when-otel
   (reduce-kv
    (fn [b k v]
      (cond
        (string? v) (.setAttribute b (AttributeKey/stringKey (name k)) v)
        (number? v) (if (integer? v)
                      (.setAttribute b (AttributeKey/longKey (name k)) (long v))
                      (.setAttribute b (AttributeKey/doubleKey (name k)) (double v)))
        (boolean? v) (.setAttribute b (AttributeKey/booleanKey (name k)) v)
        :else (.setAttribute b (AttributeKey/stringKey (name k)) (str v))))
    builder
    attrs)))

;; Wrapper for OTEL span that implements our span interface
(defrecord OtelSpanWrapper [otel-span internal-span]
  ;; This record wraps both the OTEL span and our internal span
  ;; for context propagation
  )

(defrecord OtelTracer [^io.opentelemetry.api.trace.Tracer tracer]
  core/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (when-otel
     (let [internal-span (span/create-span span-name attributes parent-ctx)
           builder (-> tracer
                       (.spanBuilder span-name)
                       (.setSpanKind SpanKind/INTERNAL))
           builder (set-otel-attributes builder attributes)
           otel-span (.startSpan builder)]
       (->OtelSpanWrapper otel-span internal-span))))

  (end-span [_ s]
    (when-otel
     (when-let [otel-span (:otel-span s)]
       (.end otel-span)))
    nil)

  (record-exception [_ s exception]
    (when-otel
     (when-let [otel-span (:otel-span s)]
       (.recordException otel-span exception)))
    nil)

  (set-status [_ s status message]
    (when-otel
     (when-let [otel-span (:otel-span s)]
       (let [otel-status (status-keyword->otel status)]
         (if message
           (.setStatus otel-span otel-status message)
           (.setStatus otel-span otel-status)))))
    nil))

;; Fallback noop tracer for when OTEL is not available
(defrecord OtelNoopTracer []
  core/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (span/create-span span-name attributes parent-ctx))
  (end-span [_ _span]
    nil)
  (record-exception [_ _span _exception]
    nil)
  (set-status [_ _span _status _message]
    nil))

(defn create-otel-tracer
  "Creates an OpenTelemetry tracer.

   If OTEL dependencies are not available, returns a noop tracer.

   Config options:
   - :otel-service-name - Service name for OTEL (default: \"llm-clj\")"
  [cfg]
  (if otel-available?
    (when-otel
     (let [service-name (or (:otel-service-name cfg)
                            (config/otel-service-name)
                            "llm-clj")
           tracer (-> (GlobalOpenTelemetry/get)
                      (.getTracer service-name))]
       (->OtelTracer tracer)))
    (->OtelNoopTracer)))

;; Register this backend
(core/register-backend! :otel create-otel-tracer)
