(ns llm-clj.batch.anthropic
  "Anthropic Message Batches API implementation."
  (:require [llm-clj.batch.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private batches-url "https://api.anthropic.com/v1/messages/batches")

(defn- build-headers [api-key]
  {"x-api-key" api-key
   "anthropic-version" "2023-06-01"
   "anthropic-beta" "message-batches-2024-09-24"
   "content-type" "application/json"})

(defn- format-request
  "Formats a single request for the batch."
  [{:keys [custom-id messages params]}]
  {:custom_id custom-id
   :params (merge {:model (or (:model params) "claude-3-haiku-20240307")
                   :max_tokens (or (:max-tokens params) 4096)
                   :messages messages}
                  (dissoc params :model :max-tokens))})

(defn- parse-batch-status [body]
  (let [data (if (string? body) (json/parse-string body true) body)]
    {:id (:id data)
     :status (keyword (str/replace (or (:processing_status data) "unknown") "_" "-"))
     :created-at (:created_at data)
     :ended-at (:ended_at data)
     :request-counts {:total (get-in data [:request_counts :processing])
                      :completed (get-in data [:request_counts :succeeded])
                      :failed (+ (get-in data [:request_counts :errored] 0)
                                 (get-in data [:request_counts :canceled] 0))}
     :results-url (:results_url data)}))

(defrecord AnthropicBatchProvider [api-key]
  core/BatchProvider

  (create-batch [_ requests _options]
    (let [payload {:requests (mapv format-request requests)}
          response (http/post batches-url
                              {:headers (build-headers api-key)
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-batch-status (:body response))
        (throw (errors/api-error :anthropic
                                 (:status response)
                                 (:body response))))))

  (get-batch [_ batch-id]
    (let [url (str batches-url "/" batch-id)
          response (http/get url
                             {:headers (build-headers api-key)
                              :cookie-policy :standard
                              :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-batch-status (:body response))
        (throw (errors/api-error :anthropic
                                 (:status response)
                                 (:body response))))))

  (cancel-batch [_ batch-id]
    (let [url (str batches-url "/" batch-id "/cancel")
          response (http/post url
                              {:headers (build-headers api-key)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-batch-status (:body response))
        (throw (errors/api-error :anthropic
                                 (:status response)
                                 (:body response))))))

  (list-batches [_ options]
    (let [params (cond-> {}
                   (:limit options) (assoc :limit (:limit options))
                   (:after options) (assoc :after_id (:after options)))
          response (http/get batches-url
                             {:headers (build-headers api-key)
                              :query-params params
                              :cookie-policy :standard
                              :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (let [body (json/parse-string (:body response) true)]
          {:batches (mapv parse-batch-status (:data body))
           :has-more (:has_more body)
           :first-id (:first_id body)
           :last-id (:last_id body)})
        (throw (errors/api-error :anthropic
                                 (:status response)
                                 (:body response)))))))

(defn create-provider
  "Creates an Anthropic Batch provider."
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :anthropic api-key)]
    (->AnthropicBatchProvider key)))

(defn get-results
  "Retrieves batch results from results URL."
  [provider batch-id]
  (let [status (core/get-batch provider batch-id)]
    (when (and (= :ended (:status status)) (:results-url status))
      (let [response (http/get (:results-url status)
                               {:headers {"x-api-key" (:api-key provider)}
                                :cookie-policy :standard
                                :throw-exceptions false})]
        (when (<= 200 (:status response) 299)
          (->> (str/split-lines (:body response))
               (map #(json/parse-string % true))
               (reduce (fn [acc item]
                         (assoc acc (:custom_id item)
                                {:result (:result item)
                                 :error (:error item)}))
                       {})))))))
