(ns llm-clj.providers.openai
  (:require [llm-clj.core :as core]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [llm-clj.streaming :as streaming]
            [llm-clj.errors :as errors]
            [llm-clj.config :as config]
            [clojure.core.async :as async :refer [go]]))

(def ^:private default-api-url "https://api.openai.com/v1/chat/completions")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)
   "Content-Type" "application/json"})

(defn- parse-single-choice
  "Parses a single choice from OpenAI response."
  [choice _body]
  (let [msg (:message choice)]
    (cond-> {:role (keyword (:role msg))
             :content (:content msg)
             :finish-reason (keyword (:finish_reason choice))}
      (:tool_calls msg) (assoc :tool-calls (:tool_calls msg))
      ;; Extract reasoning/thinking content if present
      (:reasoning msg) (assoc :thinking {:content (:reasoning msg)})
      ;; Add logprobs if present
      (:logprobs choice) (assoc :logprobs (:logprobs choice)))))

(defn- parse-response
  "Parses the raw OpenAI response back into our sequence of normalized maps.
  When n>1, returns {:choices [...] :usage {...}}.
  When n=1, returns a single normalized response map with :usage."
  [response]
  (let [body (-> response :body (json/parse-string true))
        choices (:choices body)]
    (if (= 1 (count choices))
      ;; Single completion - return as before with usage at top level
      (assoc (parse-single-choice (first choices) body) :usage (:usage body))
      ;; Multiple completions - return vector of choices with shared usage
      {:choices (mapv #(parse-single-choice % body) choices)
       :usage (:usage body)})))

(defn- format-content-part
  "Formats a single content part for OpenAI's multi-part content format."
  [part]
  (case (:type part)
    :text {:type "text" :text (:text part)}
    :image-url {:type "image_url"
                :image_url {:url (:url part)
                            :detail (:detail part "auto")}}
    :image-base64 {:type "image_url"
                   :image_url {:url (str "data:" (:media-type part) ";base64," (:data part))
                               :detail (:detail part "auto")}}
    ;; Fallback for already-formatted content
    part))

(defn- format-message
  "Formats a normalized message for the OpenAI API format."
  [msg]
  (let [content (if (vector? (:content msg))
                  (mapv format-content-part (:content msg))
                  (:content msg))
        base (cond-> {:role (name (:role msg))}
               content (assoc :content content)
               (not content) (assoc :content nil)
               (:name msg) (assoc :name (:name msg)))]
    (cond-> base
      (:tool-calls msg) (assoc :tool_calls (:tool-calls msg))
      (:tool-call-id msg) (assoc :tool_call_id (:tool-call-id msg)))))

(defn- process-openai-stream-event
  "Processes a single OpenAI streaming event and returns normalized event or nil."
  [data-str]
  (when (and data-str (not= data-str "[DONE]"))
    (let [data (json/parse-string data-str true)
          choice (-> data :choices first)
          delta (:delta choice)
          finish-reason (:finish_reason choice)]
      (cond
        (:content delta)
        {:type :delta :content (:content delta)}

        finish-reason
        {:type :finish :finish-reason (keyword finish-reason) :usage (:usage data)}

        :else nil))))

(defn- stream-response [url headers payload]
  (let [{:keys [channel]} (streaming/create-stream-channel)
        content-buffer (atom "")]
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
              (doseq [line (line-seq reader)]
                (when (str/starts-with? line "data: ")
                  (let [data-str (subs line 6)
                        event (process-openai-stream-event data-str)]
                    (when event
                      (case (:type event)
                        :delta (do
                                 (swap! content-buffer str (:content event))
                                 (streaming/emit-delta! channel (:content event)))
                        :finish (streaming/emit-complete! channel
                                                          @content-buffer
                                                          (:usage event)
                                                          (:finish-reason event))
                        nil)))))
              ;; Ensure channel is closed
              (async/close! channel))
            (streaming/emit-error! channel
                                   (errors/api-error :openai
                                                     (:status response)
                                                     (:body response)
                                                     :headers (:headers response)))))
        (catch Exception e
          (streaming/emit-error! channel e))))
    channel))

(defrecord OpenAIProvider [api-key base-url model]
  core/LLMProvider
  (chat-completion [_ messages options]
    (let [url (or base-url default-api-url)
          req-model (or (:model options) model "gpt-4o")
          payload (cond-> {:model req-model
                           :messages (map (comp format-message core/normalize-message) messages)}
                    (:temperature options) (assoc :temperature (:temperature options))
                    (:max-tokens options) (assoc :max_tokens (:max-tokens options))
                    (:response-format options) (assoc :response_format (:response-format options))
                    (:tools options) (assoc :tools (:tools options))
                    (:tool-choice options) (assoc :tool_choice (:tool-choice options))
                    (:stop-sequences options) (assoc :stop (:stop-sequences options))
                    (:top-p options) (assoc :top_p (:top-p options))
                    (:user-id options) (assoc :user (:user-id options))
                    ;; OpenAI reasoning models use "reasoning" parameter
                    (and (:thinking options) (:enabled (:thinking options)))
                    (assoc :reasoning {:effort (name (get-in options [:thinking :effort] :medium))})
                    ;; New parameters from spec
                    (:frequency-penalty options) (assoc :frequency_penalty (:frequency-penalty options))
                    (:presence-penalty options) (assoc :presence_penalty (:presence-penalty options))
                    (:logit-bias options) (assoc :logit_bias (:logit-bias options))
                    (:logprobs options) (assoc :logprobs (:logprobs options))
                    (:top-logprobs options) (assoc :top_logprobs (:top-logprobs options))
                    (:n options) (assoc :n (:n options))
                    (:seed options) (assoc :seed (:seed options))
                    (:parallel-tool-calls options) (assoc :parallel_tool_calls (:parallel-tool-calls options))
                    (:service-tier options) (assoc :service_tier (name (:service-tier options)))
                    (:store options) (assoc :store (:store options))
                    (:prediction options) (assoc :prediction (:prediction options)))
          response (http/post url
                              {:headers (build-headers api-key)
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (chat-completion-stream [_ messages options]
    (let [url (or base-url default-api-url)
          req-model (or (:model options) model "gpt-4o")
          payload (cond-> {:model req-model
                           :messages (map (comp format-message core/normalize-message) messages)}
                    (:temperature options) (assoc :temperature (:temperature options))
                    (:max-tokens options) (assoc :max_tokens (:max-tokens options))
                    (:top-p options) (assoc :top_p (:top-p options))
                    (:stop-sequences options) (assoc :stop (:stop-sequences options))
                    (:user-id options) (assoc :user (:user-id options))
                    (:tools options) (assoc :tools (:tools options))
                    (:tool-choice options) (assoc :tool_choice (:tool-choice options))
                    ;; OpenAI reasoning models use "reasoning" parameter
                    (and (:thinking options) (:enabled (:thinking options)))
                    (assoc :reasoning {:effort (name (get-in options [:thinking :effort] :medium))})
                    ;; New parameters from spec (excluding :n which is not supported in streaming)
                    (:frequency-penalty options) (assoc :frequency_penalty (:frequency-penalty options))
                    (:presence-penalty options) (assoc :presence_penalty (:presence-penalty options))
                    (:logit-bias options) (assoc :logit_bias (:logit-bias options))
                    (:logprobs options) (assoc :logprobs (:logprobs options))
                    (:top-logprobs options) (assoc :top_logprobs (:top-logprobs options))
                    (:seed options) (assoc :seed (:seed options))
                    (:parallel-tool-calls options) (assoc :parallel_tool_calls (:parallel-tool-calls options))
                    (:service-tier options) (assoc :service_tier (name (:service-tier options)))
                    (:store options) (assoc :store (:store options))
                    (:prediction options) (assoc :prediction (:prediction options)))]
      (stream-response url (build-headers api-key) payload))));

(defn create-provider
  "Creates a new OpenAI provider instance.
  Options:
  - :api-key (optional, defaults to OPENAI_API_KEY env var)
  - :base-url (optional, for explicitly setting custom endpoints like vLLM)
  - :model (optional default model)"
  [{:keys [_api-key _base-url _model] :as opts}]
  (let [cfg (config/build-provider-config :openai opts)]
    (->OpenAIProvider (:api-key cfg)
                      (:base-url cfg)
                      (:model cfg))))
