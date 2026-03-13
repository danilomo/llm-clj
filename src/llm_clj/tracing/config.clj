(ns llm-clj.tracing.config
  "Runtime configuration for the tracing system.

   Configuration can be set programmatically via `configure!` or
   through environment variables:

   - LLM_CLJ_TRACING_ENABLED: Enable/disable tracing (true/false)
   - LLM_CLJ_TRACING_BACKEND: Backend type (:noop :stdout :json :otel :composite)
   - LLM_CLJ_TRACING_SAMPLE_RATE: Sampling rate 0.0-1.0
   - LLM_CLJ_TRACING_CAPTURE_MESSAGES: Capture message content (privacy sensitive)
   - LLM_CLJ_TRACING_CAPTURE_TOOL_ARGS: Capture tool arguments (privacy sensitive)"
  (:require [clojure.string :as str]))

(defn- str->boolean
  "Parses a string to boolean, nil if not a valid boolean string."
  [s]
  (when s
    (case (str/lower-case s)
      ("true" "1" "yes") true
      ("false" "0" "no") false
      nil)))

(defn- str->double
  "Parses a string to double, nil if invalid."
  [s]
  (when s
    (try
      (Double/parseDouble s)
      (catch NumberFormatException _
        nil))))

(defn- str->keyword
  "Parses a string to keyword."
  [s]
  (when s
    (keyword (str/lower-case s))))

(defn- str->keywords
  "Parses a comma-separated string to a vector of keywords."
  [s]
  (when s
    (mapv (comp keyword str/trim str/lower-case)
          (str/split s #","))))

(defn- load-env-config
  "Loads configuration from environment variables."
  []
  (let [enabled (str->boolean (System/getenv "LLM_CLJ_TRACING_ENABLED"))
        backend (str->keyword (System/getenv "LLM_CLJ_TRACING_BACKEND"))
        backends (str->keywords (System/getenv "LLM_CLJ_TRACING_BACKENDS"))
        sample-rate (str->double (System/getenv "LLM_CLJ_TRACING_SAMPLE_RATE"))
        capture-messages (str->boolean (System/getenv "LLM_CLJ_TRACING_CAPTURE_MESSAGES"))
        capture-tool-args (str->boolean (System/getenv "LLM_CLJ_TRACING_CAPTURE_TOOL_ARGS"))]
    (cond-> {}
      (some? enabled) (assoc :enabled enabled)
      backend (assoc :backend backend)
      backends (assoc :backends backends)
      sample-rate (assoc :sample-rate sample-rate)
      (some? capture-messages) (assoc :capture-messages capture-messages)
      (some? capture-tool-args) (assoc :capture-tool-args capture-tool-args))))

;; Default configuration
(def default-config
  {:enabled false
   :backend :noop
   :backends []
   :sample-rate 1.0
   :capture-messages false   ;; Privacy: opt-in only
   :capture-tool-args false  ;; Privacy: opt-in only
   :otel-service-name "llm-clj"})

;; Global configuration atom
(defonce ^:private config-atom
  (atom (merge default-config (load-env-config))))

(defn get-config
  "Returns the current tracing configuration map."
  []
  @config-atom)

(defn configure!
  "Updates the tracing configuration.

   Arguments:
   - config-map: Map of configuration options to merge

   Options:
   - :enabled - Boolean to enable/disable tracing
   - :backend - Keyword for backend type (:noop :stdout :json :otel :composite)
   - :backends - Vector of backend keywords for :composite backend
   - :sample-rate - Double 0.0-1.0 for sampling probability
   - :capture-messages - Boolean to capture message content (privacy sensitive)
   - :capture-tool-args - Boolean to capture tool arguments (privacy sensitive)
   - :otel-service-name - String service name for OpenTelemetry

   Example:
   (configure! {:enabled true :backend :json :sample-rate 0.1})"
  [config-map]
  (swap! config-atom merge config-map))

(defn reset-config!
  "Resets configuration to defaults, then applies env vars."
  []
  (reset! config-atom (merge default-config (load-env-config))))

(defn enabled?
  "Returns true if tracing is enabled."
  []
  (:enabled @config-atom))

(defn backend
  "Returns the configured backend keyword."
  []
  (:backend @config-atom))

(defn backends
  "Returns the configured backends vector for composite backend."
  []
  (:backends @config-atom))

(defn sample-rate
  "Returns the configured sample rate."
  []
  (:sample-rate @config-atom))

(defn capture-messages?
  "Returns true if message content capture is enabled."
  []
  (:capture-messages @config-atom))

(defn capture-tool-args?
  "Returns true if tool argument capture is enabled."
  []
  (:capture-tool-args @config-atom))

(defn otel-service-name
  "Returns the configured OpenTelemetry service name."
  []
  (:otel-service-name @config-atom))

(defn should-sample?
  "Returns true if this trace should be sampled based on sample rate.
   Uses a random check against the configured sample rate."
  []
  (let [rate (sample-rate)]
    (or (>= rate 1.0)
        (< (rand) rate))))
