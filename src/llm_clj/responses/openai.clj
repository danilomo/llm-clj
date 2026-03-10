(ns llm-clj.responses.openai
  "OpenAI Responses API implementation."
  (:require [llm-clj.responses.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [llm-clj.streaming :as streaming]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]
            [clojure.core.async :as async :refer [go]]))

(def ^:private responses-url "https://api.openai.com/v1/responses")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)
   "Content-Type" "application/json"})

(defn- format-input
  "Formats input for the API.
  String input becomes a simple user message.
  Vector input is passed through as items."
  [input]
  (if (string? input)
    input
    (mapv identity input)))

(defn- format-tool
  "Formats a tool configuration."
  [tool]
  (cond
    ;; Built-in tools
    (keyword? tool)
    {:type (name tool)}

    ;; Already formatted
    (map? tool)
    tool

    :else
    (throw (errors/validation-error "Invalid tool format" {:tool tool}))))

(defn- build-payload
  "Builds the request payload from input and options."
  [input options]
  (let [formatted-input (format-input input)]
    (cond-> {:model (or (:model options) "gpt-4o")
             :input formatted-input}
      (:instructions options) (assoc :instructions (:instructions options))
      (:previous-response-id options) (assoc :previous_response_id (:previous-response-id options))
      (:tools options) (assoc :tools (mapv format-tool (:tools options)))
      (:tool-choice options) (assoc :tool_choice (:tool-choice options))
      (:temperature options) (assoc :temperature (:temperature options))
      (:max-output-tokens options) (assoc :max_output_tokens (:max-output-tokens options))
      (:top-p options) (assoc :top_p (:top-p options))
      (:store options) (assoc :store (:store options)))))

(defn- parse-response
  "Parses the API response into a normalized map."
  [response]
  (let [body (-> response :body (json/parse-string true))]
    {:id (:id body)
     :status (:status body)
     :output (:output body)
     :usage (when-let [u (:usage body)]
              {:input-tokens (:input_tokens u)
               :output-tokens (:output_tokens u)
               :total-tokens (:total_tokens u)})
     :model (:model body)
     :created-at (:created_at body)}))

(defn- process-stream-event
  "Processes a single streaming event."
  [event-type data-str]
  (when data-str
    (let [data (json/parse-string data-str true)]
      (case event-type
        "response.output_text.delta"
        {:type :delta :content (:delta data)}

        "response.completed"
        {:type :complete
         :response (parse-response {:body (json/generate-string data)})}

        "error"
        {:type :error :error (errors/api-error :openai nil (:error data))}

        nil))))

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
              (let [lines (line-seq reader)]
                (loop [lines lines
                       current-event nil]
                  (if-let [line (first lines)]
                    (cond
                      (str/starts-with? line "event: ")
                      (recur (rest lines) (subs line 7))

                      (str/starts-with? line "data: ")
                      (let [data-str (subs line 6)
                            event (process-stream-event current-event data-str)]
                        (when event
                          (case (:type event)
                            :delta (do
                                     (swap! content-buffer str (:content event))
                                     (streaming/emit-delta! channel (:content event)))
                            :complete (streaming/emit-complete! channel
                                                                @content-buffer
                                                                (:usage (:response event))
                                                                :stop)
                            :error (streaming/emit-error! channel (:error event))
                            nil))
                        (recur (rest lines) nil))

                      :else
                      (recur (rest lines) current-event))
                    (async/close! channel)))))
            (streaming/emit-error! channel
                                   (errors/api-error :openai
                                                     (:status response)
                                                     (:body response)
                                                     :headers (:headers response)))))
        (catch Exception e
          (streaming/emit-error! channel e))))
    channel))

(defrecord OpenAIResponsesProvider [api-key]
  core/ResponsesProvider

  (create-response [_ input options]
    (let [payload (build-payload input options)
          response (http/post responses-url
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

  (create-response-stream [_ input options]
    (let [payload (build-payload input options)]
      (stream-response responses-url (build-headers api-key) payload)))

  (get-response [_ response-id]
    (let [url (str responses-url "/" response-id)
          response (http/get url
                             {:headers (build-headers api-key)
                              :cookie-policy :standard
                              :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (delete-response [_ response-id]
    (let [url (str responses-url "/" response-id)
          response (http/delete url
                                {:headers (build-headers api-key)
                                 :cookie-policy :standard
                                 :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        true
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response)))))))

(defn create-provider
  "Creates an OpenAI Responses API provider.

  Options:
  - :api-key - OpenAI API key (optional, uses OPENAI_API_KEY env var)"
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :openai api-key)]
    (->OpenAIResponsesProvider key)))
