(ns llm-clj.config
  "Configuration management for LLM providers."
  (:require [clojure.java.io :as io]
            [clojure.string :as str]
            [llm-clj.errors :as errors]))

;; Default configurations
(def ^:private default-openai-config
  {:model "gpt-4o"
   :timeout-ms 120000
   :max-retries 3})

(def ^:private default-anthropic-config
  {:model "claude-sonnet-4-20250514"
   :timeout-ms 120000
   :max-retries 3
   :max-tokens 4096})

(def ^:private env-var-names
  {:openai "OPENAI_API_KEY"
   :anthropic "ANTHROPIC_API_KEY"})

;; Environment variable overrides (for .env file support)

(defonce ^:private env-overrides (atom {}))

(defn- parse-env-line
  "Parses a single line from a .env file.
  Supports formats: VAR=value, export VAR=value
  Returns [var-name value] or nil for comments/empty lines."
  [line]
  (let [trimmed (str/trim line)]
    (when (and (seq trimmed)
               (not (str/starts-with? trimmed "#")))
      (let [;; Remove 'export ' prefix if present
            cleaned (if (str/starts-with? trimmed "export ")
                      (subs trimmed 7)
                      trimmed)
            ;; Split on first '='
            idx (str/index-of cleaned "=")]
        (when idx
          (let [var-name (str/trim (subs cleaned 0 idx))
                value (str/trim (subs cleaned (inc idx)))
                ;; Remove surrounding quotes if present
                value (cond
                        (and (str/starts-with? value "\"")
                             (str/ends-with? value "\""))
                        (subs value 1 (dec (count value)))

                        (and (str/starts-with? value "'")
                             (str/ends-with? value "'"))
                        (subs value 1 (dec (count value)))

                        :else value)]
            [var-name value]))))))

(defn load-env!
  "Loads environment variables from a .env file.
  If no path is provided, looks for .env in the current directory.

  Supports formats:
  - VAR=value
  - export VAR=value
  - Quoted values: VAR=\"value\" or VAR='value'
  - Comments starting with #

  Returns a map of loaded variables, or nil if file doesn't exist."
  ([] (load-env! ".env"))
  ([path]
   (let [file (io/file path)]
     (when (.exists file)
       (let [lines (str/split-lines (slurp file))
             parsed (->> lines
                         (keep parse-env-line)
                         (into {}))]
         (swap! env-overrides merge parsed)
         parsed)))))

(defn set-env!
  "Manually sets an environment variable override.
  Useful for REPL usage without a .env file."
  [var-name value]
  (swap! env-overrides assoc var-name value))

(defn clear-env-overrides!
  "Clears all environment variable overrides."
  []
  (reset! env-overrides {}))

(defn get-env-overrides
  "Returns the current environment variable overrides (for debugging)."
  []
  @env-overrides)

;; Configuration resolution

(defn get-env
  "Gets an environment variable value.
  First checks overrides (from load-env! or set-env!), then System/getenv."
  [var-name]
  (or (get @env-overrides var-name)
      (System/getenv var-name)))

(defn resolve-api-key
  "Resolves API key from explicit value or environment variable.
  Provider should be :openai or :anthropic."
  [provider explicit-key]
  (or explicit-key
      (get-env (get env-var-names provider))
      (throw (errors/validation-error
              (str "Missing API key for " (name provider)
                   ". Set " (get env-var-names provider) " or pass :api-key")
              {:provider provider}))))

(defn default-config
  "Returns the default configuration for a provider."
  [provider]
  (case provider
    :openai default-openai-config
    :anthropic default-anthropic-config
    (throw (errors/validation-error
            (str "Unknown provider: " provider)
            {:provider provider}))))

(defn merge-config
  "Merges user config with defaults for a provider.
  Explicit values override defaults."
  [provider user-config]
  (merge (default-config provider) user-config))

;; Request options

(defn with-timeout
  "Adds timeout configuration to request options."
  [opts timeout-ms]
  (assoc opts :socket-timeout timeout-ms
         :connection-timeout timeout-ms))

;; Configuration builder

(defn build-provider-config
  "Builds a complete provider configuration from user options.

  Options:
  - :api-key - Explicit API key (optional, uses env var if not provided)
  - :base-url - Custom API endpoint (optional)
  - :model - Default model (optional)
  - :timeout-ms - Request timeout in milliseconds (optional)
  - :max-retries - Maximum retry attempts (optional)

  Returns a config map with all values resolved."
  [provider opts]
  (let [defaults (default-config provider)
        ;; Only include keys from opts that are non-nil
        user-opts (into {} (filter (fn [[_ v]] (some? v)) opts))
        merged (merge defaults user-opts)
        api-key (resolve-api-key provider (:api-key opts))]
    (assoc merged :api-key api-key)))

;; Validation

(defn validate-model
  "Validates that a model string is non-empty."
  [model]
  (when (or (nil? model) (empty? model))
    (throw (errors/validation-error "Model cannot be empty" {:model model})))
  model)

(defn validate-temperature
  "Validates temperature is in valid range [0, 2]."
  [temp]
  (when temp
    (when-not (<= 0 temp 2)
      (throw (errors/validation-error
              "Temperature must be between 0 and 2"
              {:temperature temp}))))
  temp)

(defn validate-max-tokens
  "Validates max-tokens is positive."
  [max-tokens]
  (when max-tokens
    (when-not (pos? max-tokens)
      (throw (errors/validation-error
              "max-tokens must be positive"
              {:max-tokens max-tokens}))))
  max-tokens)

(defn validate-options
  "Validates common options, returns options unchanged if valid."
  [opts]
  (validate-temperature (:temperature opts))
  (validate-max-tokens (:max-tokens opts))
  opts)

;; Convenience functions

(defn openai-config
  "Creates an OpenAI provider configuration."
  ([] (openai-config {}))
  ([opts] (build-provider-config :openai opts)))

(defn anthropic-config
  "Creates an Anthropic provider configuration."
  ([] (anthropic-config {}))
  ([opts] (build-provider-config :anthropic opts)))
