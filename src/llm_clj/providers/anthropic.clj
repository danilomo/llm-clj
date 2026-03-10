(ns llm-clj.providers.anthropic
  (:require [llm-clj.core :as core]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [llm-clj.streaming :as streaming]
            [llm-clj.errors :as errors]
            [llm-clj.config :as config]
            [llm-clj.tools :as tools]
            [clojure.core.async :as async :refer [go]]))

(def ^:private default-api-url "https://api.anthropic.com/v1/messages")
(def ^:private count-tokens-url "https://api.anthropic.com/v1/messages/count_tokens")

(def ^:private beta-feature-versions
  "Maps beta feature keywords to their API version strings."
  {:prompt-caching "prompt-caching-2024-07-31"
   :pdfs "pdfs-2024-09-25"
   :token-counting "token-counting-2024-11-01"
   :message-batches "message-batches-2024-09-24"
   :computer-use "computer-use-2025-01-24"
   :code-execution "code-execution-2025-05-22"
   :mcp "mcp-client-2025-04-04"
   :interleaved-thinking "interleaved-thinking-2025-05-14"
   :files "files-2025-04-14"})

(defn- beta-features->header
  "Converts a vector of beta feature keywords to the anthropic-beta header value.
  Known features are mapped to their versioned strings, unknown features use their name."
  [features]
  (when (seq features)
    (->> features
         (map #(get beta-feature-versions % (name %)))
         (str/join ","))))

(defn- build-headers
  "Builds HTTP headers for Anthropic API requests.

  Parameters:
  - api-key: API key for authentication
  - beta-features: Optional vector of beta feature keywords
  - service-tier: Optional service tier keyword (:priority or :standard)"
  ([api-key] (build-headers api-key nil nil))
  ([api-key beta-features service-tier]
   (cond-> {"x-api-key" api-key
            "anthropic-version" "2023-06-01"
            "content-type" "application/json"}
     (seq beta-features) (assoc "anthropic-beta" (beta-features->header beta-features))
     (= service-tier :priority) (assoc "anthropic-service-tier" "priority"))))

;; Server tool types that should be passed through without transformation
(def ^:private server-tool-types
  #{"web_search_20250305" "code_execution_20250522" "bash_20250124"
    "text_editor_20250124" "computer_20250124" "mcp"})

(defn- server-tool?
  "Returns true if the tool is a server-side tool (has :type in server-tool-types)."
  [tool]
  (contains? server-tool-types (:type tool)))

(defn- pre-formatted-tool?
  "Returns true if the tool is already in Anthropic API format (has :input_schema key)."
  [tool]
  (contains? tool :input_schema))

(defn- format-tool
  "Formats a tool for the Anthropic API.
  Server tools are passed through unchanged.
  Pre-formatted tools (with :input_schema) are passed through unchanged.
  User-defined tools (created with define-tool) are formatted using tools/format-tool-anthropic."
  [tool]
  (cond
    (server-tool? tool) tool
    (pre-formatted-tool? tool) tool
    :else (tools/format-tool-anthropic tool)))

(defn- parse-anthropic-response
  "Parses the raw Anthropic response back into our sequence of normalized maps."
  [response]
  (let [body (-> response :body (json/parse-string true))
        content-blocks (:content body)
        ;; Extract thinking blocks
        thinking-content (->> content-blocks
                              (filter #(= "thinking" (:type %)))
                              (map :thinking)
                              (str/join "\n"))
        text-content (->> content-blocks
                          (filter #(= "text" (:type %)))
                          (map :text)
                          (str/join "\n"))
        tool-calls (->> content-blocks
                        (filter #(= "tool_use" (:type %)))
                        (map (fn [block]
                               {:id (:id block)
                                :type "function"
                                :function {:name (:name block)
                                           :arguments (json/generate-string (:input block))}})))]
    (cond-> {:role :assistant
             :content text-content
             :finish-reason (keyword (:stop_reason body))
             :usage (:usage body)}
      (seq tool-calls) (assoc :tool-calls tool-calls)
      (not (str/blank? thinking-content)) (assoc :thinking {:content thinking-content}))))

(defn- format-document-source
  "Formats document source for Anthropic API."
  [source]
  (case (:type source)
    :url {:type "url" :url (:url source)}
    :base64 {:type "base64"
             :media_type (:media_type source)
             :data (:data source)}
    source))

(defn- detect-document-content
  "Detects if any message contains document content blocks.
  Returns true if documents are found, nil otherwise."
  [messages]
  (some (fn [msg]
          (when (vector? (:content msg))
            (some #(= :document (:type %)) (:content msg))))
        messages))

(defn- ensure-pdfs-beta
  "Ensures the :pdfs beta feature is included if documents are detected."
  [messages options]
  (if (and (detect-document-content messages)
           (not-any? #{:pdfs} (:beta-features options)))
    (update options :beta-features (fnil conj []) :pdfs)
    options))

(defn- format-content-part
  "Formats a single content part for Anthropic's multi-part content format."
  [part]
  (case (:type part)
    :text (cond-> {:type "text" :text (:text part)}
            (:cache_control part) (assoc :cache_control (:cache_control part)))
    :image-url {:type "image"
                :source {:type "url"
                         :url (:url part)}}
    :image-base64 {:type "image"
                   :source {:type "base64"
                            :media_type (:media-type part)
                            :data (:data part)}}
    ;; Document support for PDFs
    :document (cond-> {:type "document"
                       :source (format-document-source (:source part))}
                (:cache_control part) (assoc :cache_control (:cache_control part)))
    ;; Fallback for already-formatted content
    part))

(defn- format-message [msg]
  ;; Anthropic only supports 'user' and 'assistant' roles in the messages array.
  ;; System is passed globally at the top level.
  ;; Tool usage logic can get more complex, simplified here for the abstraction.
  (cond
    (= "tool" (name (:role msg)))
    {:role "user"
     :content [{:type "tool_result"
                :tool_use_id (:tool-call-id msg)
                :content (:content msg)}]}

    (= "assistant" (name (:role msg)))
    (cond-> {:role "assistant"
             :content (:content msg)}
      (:tool-calls msg)
      (assoc :content
             (vec (concat (when (seq (:content msg)) [{:type "text" :text (:content msg)}])
                          (map (fn [tc]
                                 {:type "tool_use"
                                  :id (:id tc)
                                  :name (get-in tc [:function :name])
                                  :input (json/parse-string (get-in tc [:function :arguments]) true)})
                               (:tool-calls msg))))))

    :else
    (let [content (if (vector? (:content msg))
                    (mapv format-content-part (:content msg))
                    (:content msg))]
      {:role (name (:role msg))
       :content content})))

(defn- process-anthropic-stream-event
  "Processes a single Anthropic streaming event."
  [event-type data-str]
  (when data-str
    (let [data (json/parse-string data-str true)]
      (case event-type
        "content_block_delta"
        (when (= "text_delta" (get-in data [:delta :type]))
          {:type :delta :content (get-in data [:delta :text])})

        "message_delta"
        {:type :message-delta
         :stop-reason (keyword (or (:stop_reason data) (get-in data [:delta :stop_reason])))
         :usage (:usage data)}

        "message_stop"
        {:type :message-stop}

        nil))))

(defn- stream-response [url headers payload]
  (let [{:keys [channel]} (streaming/create-stream-channel)
        content-buffer (atom "")
        last-usage (atom nil)
        last-stop-reason (atom nil)]
    (go
      (try
        (let [response (http/post url
                                  {:headers headers
                                   :body (json/generate-string (assoc payload :stream true))
                                   :as :stream
                                   :cookie-policy :standard
                                   :throw-exceptions false})]
          (if (<= 200 (:status response) 299)
            (with-open [reader (io/reader (:body response))]
              (let [lines (line-seq reader)]
                (loop [lines lines
                       current-event nil]
                  (if-let [line (first lines)]
                    (cond
                      (str/starts-with? line "event: ")
                      (recur (rest lines) (subs line 7))

                      (str/starts-with? line "data: ")
                      (let [data-str (subs line 6)
                            event (process-anthropic-stream-event current-event data-str)]
                        (when event
                          (case (:type event)
                            :delta (do
                                     (swap! content-buffer str (:content event))
                                     (streaming/emit-delta! channel (:content event)))
                            :message-delta (do
                                             (reset! last-usage (:usage event))
                                             (reset! last-stop-reason (:stop-reason event)))
                            :message-stop (streaming/emit-complete! channel
                                                                    @content-buffer
                                                                    @last-usage
                                                                    @last-stop-reason)
                            nil))
                        (recur (rest lines) nil))

                      :else
                      (recur (rest lines) current-event))
                    ;; End of lines
                    (async/close! channel)))))
            (streaming/emit-error! channel
                                   (errors/api-error :anthropic
                                                     (:status response)
                                                     (:body response)
                                                     :headers (:headers response)))))
        (catch Exception e
          (streaming/emit-error! channel e))))
    channel))

(defrecord AnthropicProvider [api-key base-url model]
  core/LLMProvider
  (chat-completion [_ messages options]
    (let [url (or base-url default-api-url)
          req-model (or (:model options) model "claude-3-haiku-20240307")
          ;; Auto-detect documents and add pdfs beta feature if needed
          options (ensure-pdfs-beta messages options)
          ;; Anthropic extracts the system prompt from the messages array
          [system-prompt other-msgs] (#'core/extract-system-prompt messages)

          payload (cond-> {:model req-model
                           :max_tokens (or (:max-tokens options) 4096)
                           :messages (map format-message (map core/normalize-message other-msgs))}
                    system-prompt (assoc :system system-prompt)
                    (:temperature options) (assoc :temperature (:temperature options))
                    (:tools options) (assoc :tools (mapv format-tool (:tools options)))
                    (:tool-choice options) (assoc :tool_choice (:tool-choice options))
                    (:stop-sequences options) (assoc :stop_sequences (:stop-sequences options))
                    (:top-p options) (assoc :top_p (:top-p options))
                    (:user-id options) (assoc :metadata {:user_id (:user-id options)})
                    ;; NEW: Add top-k parameter
                    (:top-k options) (assoc :top_k (:top-k options))
                    ;; Anthropic uses "thinking" parameter with type and budget_tokens
                    (and (:thinking options) (:enabled (:thinking options)))
                    (assoc :thinking {:type "enabled"
                                      :budget_tokens (get-in options [:thinking :budget-tokens] 10000)}))

          ;; Build headers with beta features and service tier
          headers (build-headers api-key
                                 (:beta-features options)
                                 (:service-tier options))

          response (http/post url
                              {:headers headers
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-anthropic-response response)
        (throw (errors/api-error :anthropic
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (chat-completion-stream [_ messages options]
    (let [url (or base-url default-api-url)
          req-model (or (:model options) model "claude-3-haiku-20240307")
          ;; Auto-detect documents and add pdfs beta feature if needed
          options (ensure-pdfs-beta messages options)
          [system-prompt other-msgs] (#'core/extract-system-prompt messages)
          payload (cond-> {:model req-model
                           :max_tokens (or (:max-tokens options) 4096)
                           :messages (map format-message (map core/normalize-message other-msgs))}
                    system-prompt (assoc :system system-prompt)
                    (:temperature options) (assoc :temperature (:temperature options))
                    (:top-p options) (assoc :top_p (:top-p options))
                    (:stop-sequences options) (assoc :stop_sequences (:stop-sequences options))
                    (:user-id options) (assoc :metadata {:user_id (:user-id options)})
                    (:tools options) (assoc :tools (mapv format-tool (:tools options)))
                    (:tool-choice options) (assoc :tool_choice (:tool-choice options))
                    ;; NEW: Add top-k parameter
                    (:top-k options) (assoc :top_k (:top-k options))
                    ;; Anthropic uses "thinking" parameter with type and budget_tokens
                    (and (:thinking options) (:enabled (:thinking options)))
                    (assoc :thinking {:type "enabled"
                                      :budget_tokens (get-in options [:thinking :budget-tokens] 10000)}))

          ;; Build headers with beta features and service tier
          headers (build-headers api-key
                                 (:beta-features options)
                                 (:service-tier options))]
      (stream-response url headers payload))));

(defn count-tokens
  "Counts tokens for a messages request without executing it.

  Returns the exact number of input tokens that would be used.

  Arguments:
  - api-key: Anthropic API key (or nil to use env var)
  - messages: Vector of message maps
  - options: Request options including:
    - :model - Model to use for tokenization (required)
    - :system - System prompt (optional)
    - :tools - Tools definition (optional)
    - :tool-choice - Tool choice (optional)
    - :thinking - Thinking configuration (optional)

  Returns:
  {:input-tokens N}

  Example:
  (count-tokens nil
    [{:role :user :content \"Hello, how are you?\"}]
    {:model \"claude-sonnet-4-20250514\"})
  ;; => {:input-tokens 12}"
  [api-key messages options]
  (let [key (or api-key (config/get-env "ANTHROPIC_API_KEY"))
        _ (when-not key
            (throw (errors/validation-error "Missing Anthropic API Key" {:provider :anthropic})))
        model (or (:model options)
                  (throw (errors/validation-error "Model is required for token counting"
                                                  {:options options})))
        [system-prompt other-msgs] (#'core/extract-system-prompt messages)

        payload (cond-> {:model model
                         :messages (map format-message (map core/normalize-message other-msgs))}
                  system-prompt (assoc :system system-prompt)
                  (:tools options) (assoc :tools (mapv format-tool (:tools options)))
                  (:tool-choice options) (assoc :tool_choice (:tool-choice options))
                  (and (:thinking options) (:enabled (:thinking options)))
                  (assoc :thinking {:type "enabled"
                                    :budget_tokens (get-in options [:thinking :budget-tokens] 10000)}))

        headers (build-headers key [:token-counting] nil)

        response (http/post count-tokens-url
                            {:headers headers
                             :body (json/generate-string payload)
                             :cookie-policy :standard
                             :throw-exceptions false})]
    (if (<= 200 (:status response) 299)
      (let [body (json/parse-string (:body response) true)]
        {:input-tokens (:input_tokens body)})
      (throw (errors/api-error :anthropic
                               (:status response)
                               (:body response)
                               :headers (:headers response))))))

(defn create-provider
  "Creates a new Anthropic provider instance.
  Options:
  - :api-key (optional, defaults to ANTHROPIC_API_KEY env var)
  - :model (optional default model)"
  [{:keys [_api-key _base-url _model] :as opts}]
  (let [cfg (config/build-provider-config :anthropic opts)]
    (->AnthropicProvider (:api-key cfg)
                         (:base-url cfg)
                         (:model cfg))))
