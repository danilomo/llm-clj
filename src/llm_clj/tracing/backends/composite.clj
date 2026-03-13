(ns llm-clj.tracing.backends.composite
  "Composite tracing backend that forwards to multiple backends.

   Use this when you want to send traces to multiple destinations,
   for example both stdout (for development) and JSON (for log aggregation).

   Example:
   (configure! {:enabled true
                :backend :composite
                :backends [:stdout :json]})"
  (:require [llm-clj.tracing.core :as core]
            [llm-clj.tracing.span :as span]
            [llm-clj.tracing.config :as config]
            ;; Import backends to ensure they're registered
            [llm-clj.tracing.backends.noop]
            [llm-clj.tracing.backends.stdout]
            [llm-clj.tracing.backends.json]))

;; Wrapper that stores multiple spans (one per backend)
(defrecord CompositeSpanWrapper [spans internal-span])

(defrecord CompositeTracer [tracers]
  core/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (let [internal-span (span/create-span span-name attributes parent-ctx)
          spans (mapv #(core/start-span % span-name attributes parent-ctx) tracers)]
      (->CompositeSpanWrapper spans internal-span)))

  (end-span [_ s]
    (doseq [[tracer span] (map vector tracers (:spans s))]
      (core/end-span tracer span))
    nil)

  (record-exception [_ s exception]
    (doseq [[tracer span] (map vector tracers (:spans s))]
      (core/record-exception tracer span exception))
    nil)

  (set-status [_ s status message]
    (doseq [[tracer span] (map vector tracers (:spans s))]
      (core/set-status tracer span status message))
    nil))

(defn- create-backend-instance
  "Creates a backend instance by keyword."
  [backend-key cfg]
  ;; We need to access the backend registry, but it's private in core
  ;; Instead, we'll require the backend namespaces and use their factory functions
  (case backend-key
    :noop ((requiring-resolve 'llm-clj.tracing.backends.noop/create-noop-tracer) cfg)
    :stdout ((requiring-resolve 'llm-clj.tracing.backends.stdout/create-stdout-tracer) cfg)
    :json ((requiring-resolve 'llm-clj.tracing.backends.json/create-json-tracer) cfg)
    :otel ((requiring-resolve 'llm-clj.tracing.backends.otel/create-otel-tracer) cfg)
    (throw (ex-info (str "Unknown backend type: " backend-key)
                    {:backend backend-key}))))

(defn create-composite-tracer
  "Creates a composite tracer that forwards to multiple backends.

   Config options:
   - :backends - Vector of backend keywords to use"
  [cfg]
  (let [backend-keys (or (:backends cfg)
                         (config/backends)
                         [:stdout])
        tracers (mapv #(create-backend-instance % cfg) backend-keys)]
    (->CompositeTracer tracers)))

;; Register this backend
(core/register-backend! :composite create-composite-tracer)
