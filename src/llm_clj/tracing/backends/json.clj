(ns llm-clj.tracing.backends.json
  "Structured JSON logging backend for production.

   Outputs one JSON object per line (JSONL format) for each span event,
   suitable for log aggregation systems like Elasticsearch, Datadog, etc.

   Events:
   - span_start: When a span begins
   - span_end: When a span completes
   - span_error: When an exception is recorded"
  (:require [llm-clj.tracing.core :as core]
            [llm-clj.tracing.span :as span]
            [cheshire.core :as json]))

(defn- format-timestamp
  "Formats a timestamp in ISO-8601 format."
  [timestamp]
  (let [instant (java.time.Instant/ofEpochMilli timestamp)
        formatter java.time.format.DateTimeFormatter/ISO_INSTANT]
    (.format formatter instant)))

(defn- emit-json
  "Emits a JSON line to stdout."
  [data]
  (println (json/generate-string data)))

(defn- span-base-data
  "Returns base data for a span event."
  [s]
  {:trace_id (:trace-id s)
   :span_id (:id s)
   :span_name (:name s)})

(defn- emit-span-start
  "Emits a span start event."
  [s]
  (emit-json
   (merge (span-base-data s)
          {:event "span_start"
           :timestamp (format-timestamp (:start-time s))
           :parent_id (:parent-id s)
           :attributes (:attributes s)})))

(defn- emit-span-end
  "Emits a span end event."
  [s]
  (let [duration (span/span-duration-ms s)]
    (emit-json
     (merge (span-base-data s)
            {:event "span_end"
             :timestamp (format-timestamp (:end-time s))
             :status (name (:status s))
             :status_message (:status-message s)
             :duration_ms duration
             :attributes (:attributes s)}))))

(defn- emit-exception
  "Emits an exception event."
  [s ex]
  (emit-json
   (merge (span-base-data s)
          {:event "span_error"
           :timestamp (format-timestamp (System/currentTimeMillis))
           :exception_class (-> ex class .getName)
           :exception_message (.getMessage ex)})))

;; Mutable span storage for tracking span state
(defonce ^:private span-store (atom {}))

(defrecord JsonTracer []
  core/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (let [s (span/create-span span-name attributes parent-ctx)]
      (swap! span-store assoc (:id s) s)
      (emit-span-start s)
      s))

  (end-span [_ s]
    (let [updated-span (-> (get @span-store (:id s) s)
                           span/end-span)]
      (swap! span-store dissoc (:id s))
      (emit-span-end updated-span)
      nil))

  (record-exception [_ s exception]
    (let [updated-span (span/record-span-exception s exception)]
      (swap! span-store assoc (:id s) updated-span)
      (emit-exception s exception)
      nil))

  (set-status [_ s status message]
    (let [updated-span (span/set-span-status s status message)]
      (swap! span-store assoc (:id s) updated-span)
      nil)))

(defn create-json-tracer
  "Creates a new JsonTracer instance."
  [_config]
  (->JsonTracer))

;; Register this backend
(core/register-backend! :json create-json-tracer)
