(ns llm-clj.tracing.backends.noop
  "No-op tracing backend with zero overhead.

   This backend is used when tracing is disabled. All operations
   are no-ops that return minimal placeholder values, ensuring
   virtually no performance impact."
  (:require [llm-clj.tracing.core :as core]
            [llm-clj.tracing.span :as span]))

;; Minimal span placeholder for when tracing is disabled
;; but code still expects a span object
(def ^:private noop-span
  (span/->Span "noop" "noop" nil "noop" 0 nil :unset nil {} [] nil))

(defrecord NoopTracer []
  core/Tracer
  (start-span [_ _span-name _attributes _parent-ctx]
    noop-span)
  (end-span [_ _span]
    nil)
  (record-exception [_ _span _exception]
    nil)
  (set-status [_ _span _status _message]
    nil))

(defn create-noop-tracer
  "Creates a new NoopTracer instance.
   The config argument is ignored."
  [_config]
  (->NoopTracer))

;; Register this backend
(core/register-backend! :noop create-noop-tracer)
