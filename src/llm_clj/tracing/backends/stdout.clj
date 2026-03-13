(ns llm-clj.tracing.backends.stdout
  "Stdout tracing backend for development and debugging.

   Prints human-readable trace information to stdout, useful for
   development and quick debugging. For production use, prefer
   the JSON backend."
  (:require [llm-clj.tracing.core :as core]
            [llm-clj.tracing.span :as span]))

(defn- format-timestamp
  "Formats a timestamp in ISO-8601 format."
  [timestamp]
  (let [instant (java.time.Instant/ofEpochMilli timestamp)
        formatter java.time.format.DateTimeFormatter/ISO_INSTANT]
    (.format formatter instant)))

(defn- format-attributes
  "Formats attributes map for display."
  [attrs]
  (when (seq attrs)
    (str " " (pr-str attrs))))

(defn- print-span-start
  "Prints span start information."
  [s]
  (println (str "[TRACE] "
                (format-timestamp (:start-time s))
                " START "
                (:name s)
                " [trace=" (subs (:trace-id s) 0 8) "..."
                " span=" (subs (:id s) 0 8) "..."
                (when (:parent-id s)
                  (str " parent=" (subs (:parent-id s) 0 8) "..."))
                "]"
                (format-attributes (:attributes s)))))

(defn- print-span-end
  "Prints span end information."
  [s]
  (let [duration (span/span-duration-ms s)]
    (println (str "[TRACE] "
                  (format-timestamp (:end-time s))
                  " END "
                  (:name s)
                  " [span=" (subs (:id s) 0 8) "..."
                  " status=" (name (:status s))
                  " duration=" duration "ms]"
                  (format-attributes (:attributes s))))))

(defn- print-exception
  "Prints exception information."
  [s ex]
  (println (str "[TRACE] "
                (format-timestamp (System/currentTimeMillis))
                " ERROR "
                (:name s)
                " [span=" (subs (:id s) 0 8) "..."
                "] "
                (-> ex class .getName)
                ": "
                (.getMessage ex))))

;; Mutable span storage for tracking span state
;; Using an atom with a map of span-id -> span
(defonce ^:private span-store (atom {}))

(defrecord StdoutTracer []
  core/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (let [s (span/create-span span-name attributes parent-ctx)]
      (swap! span-store assoc (:id s) s)
      (print-span-start s)
      s))

  (end-span [_ s]
    (let [updated-span (-> (get @span-store (:id s) s)
                           span/end-span)]
      (swap! span-store dissoc (:id s))
      (print-span-end updated-span)
      nil))

  (record-exception [_ s exception]
    (let [updated-span (span/record-span-exception s exception)]
      (swap! span-store assoc (:id s) updated-span)
      (print-exception s exception)
      nil))

  (set-status [_ s status message]
    (let [updated-span (span/set-span-status s status message)]
      (swap! span-store assoc (:id s) updated-span)
      nil)))

(defn create-stdout-tracer
  "Creates a new StdoutTracer instance."
  [_config]
  (->StdoutTracer))

;; Register this backend
(core/register-backend! :stdout create-stdout-tracer)
