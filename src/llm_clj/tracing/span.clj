(ns llm-clj.tracing.span
  "Span record and attribute definitions for tracing.

   This namespace provides:
   - Span record type for representing trace spans
   - Standard attribute name constants following semantic conventions
   - Helper functions for creating and manipulating spans")

;; Span record type
(defrecord Span [id
                 trace-id
                 parent-id
                 name
                 start-time
                 end-time
                 status
                 status-message
                 attributes
                 events
                 exception])

;; Standard attribute names following OpenTelemetry semantic conventions
;; where applicable, with llm-specific extensions

;; LLM-specific attributes
(def attr-llm-provider "llm.provider")
(def attr-llm-model "llm.model")
(def attr-llm-has-tools "llm.has_tools")
(def attr-llm-has-schema "llm.has_schema")
(def attr-llm-finish-reason "llm.finish_reason")
(def attr-llm-message-count "llm.message_count")

;; Token usage attributes
(def attr-usage-input-tokens "llm.usage.input_tokens")
(def attr-usage-output-tokens "llm.usage.output_tokens")
(def attr-usage-total-tokens "llm.usage.total_tokens")

;; Tool-specific attributes
(def attr-tool-name "tool.name")
(def attr-tool-arguments "tool.arguments")
(def attr-tool-result-length "tool.result_length")
(def attr-tool-success "tool.success")

;; Loop/iteration attributes
(def attr-iteration "iteration")
(def attr-max-iterations "max_iterations")

;; Error attributes
(def attr-error-type "error.type")
(def attr-error-message "error.message")

;; Status values
(def status-ok :ok)
(def status-error :error)
(def status-unset :unset)

(defn create-span
  "Creates a new Span record with the given name and optional attributes.

   Arguments:
   - name: String name for the span
   - attrs: Optional map of attributes
   - parent-ctx: Optional parent span context (map with :trace-id, :span-id)

   Returns a new Span record with generated IDs and start time."
  ([span-name]
   (create-span span-name {} nil))
  ([span-name attrs]
   (create-span span-name attrs nil))
  ([span-name attrs parent-ctx]
   (let [span-id (str (java.util.UUID/randomUUID))
         trace-id (or (:trace-id parent-ctx)
                      (str (java.util.UUID/randomUUID)))]
     (->Span span-id
             trace-id
             (:span-id parent-ctx)
             span-name
             (System/currentTimeMillis)
             nil
             status-unset
             nil
             (or attrs {})
             []
             nil))))

(defn end-span
  "Marks a span as ended with the current timestamp.

   Arguments:
   - span: The Span record to end

   Returns updated Span with end-time set."
  [span]
  (assoc span :end-time (System/currentTimeMillis)))

(defn set-span-status
  "Sets the status of a span.

   Arguments:
   - span: The Span record
   - status: One of :ok, :error, :unset
   - message: Optional status message (typically for errors)

   Returns updated Span."
  ([span status]
   (set-span-status span status nil))
  ([span status message]
   (assoc span
          :status status
          :status-message message)))

(defn set-span-attribute
  "Sets a single attribute on a span.

   Arguments:
   - span: The Span record
   - key: Attribute key (string)
   - value: Attribute value

   Returns updated Span."
  [span key value]
  (update span :attributes assoc key value))

(defn set-span-attributes
  "Sets multiple attributes on a span.

   Arguments:
   - span: The Span record
   - attrs: Map of attribute key-value pairs

   Returns updated Span."
  [span attrs]
  (update span :attributes merge attrs))

(defn record-span-exception
  "Records an exception on a span and sets error status.

   Arguments:
   - span: The Span record
   - exception: The exception that occurred

   Returns updated Span with exception recorded and error status set."
  [span exception]
  (-> span
      (assoc :exception exception)
      (set-span-status status-error (.getMessage exception))
      (set-span-attributes
       {attr-error-type (-> exception class .getName)
        attr-error-message (.getMessage exception)})))

(defn add-span-event
  "Adds an event to a span's event list.

   Arguments:
   - span: The Span record
   - event-name: Name of the event
   - attrs: Optional map of event attributes

   Returns updated Span with event added."
  ([span event-name]
   (add-span-event span event-name {}))
  ([span event-name attrs]
   (update span :events conj
           {:name event-name
            :timestamp (System/currentTimeMillis)
            :attributes attrs})))

(defn span-duration-ms
  "Returns the duration of a span in milliseconds.
   Returns nil if span is not yet ended."
  [span]
  (when (:end-time span)
    (- (:end-time span) (:start-time span))))

(defn span->context
  "Extracts context information from a span for propagation.

   Returns a map with :trace-id and :span-id for use as parent context."
  [span]
  {:trace-id (:trace-id span)
   :span-id (:id span)})

(defn span->map
  "Converts a Span record to a plain map for serialization."
  [span]
  (let [duration (span-duration-ms span)]
    (cond-> {:id (:id span)
             :trace-id (:trace-id span)
             :name (:name span)
             :start-time (:start-time span)
             :status (:status span)
             :attributes (:attributes span)}
      (:parent-id span) (assoc :parent-id (:parent-id span))
      (:end-time span) (assoc :end-time (:end-time span))
      duration (assoc :duration-ms duration)
      (:status-message span) (assoc :status-message (:status-message span))
      (seq (:events span)) (assoc :events (:events span))
      (:exception span) (assoc :exception-class (-> span :exception class .getName)
                               :exception-message (-> span :exception .getMessage)))))
