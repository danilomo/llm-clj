(ns llm-clj.batch.openai
  "OpenAI Batch API implementation."
  (:require [llm-clj.batch.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str])
  (:import [java.io BufferedReader StringReader]))

(def ^:private batches-url "https://api.openai.com/v1/batches")
(def ^:private files-url "https://api.openai.com/v1/files")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)
   "Content-Type" "application/json"})

(defn- request->jsonl-line
  "Converts a request to a JSONL line for the batch file."
  [{:keys [custom-id messages params]}]
  (json/generate-string
   {:custom_id custom-id
    :method "POST"
    :url "/v1/chat/completions"
    :body (merge {:model (or (:model params) "gpt-4o")
                  :messages messages}
                 (dissoc params :model))}))

(defn- requests->jsonl
  "Converts requests to JSONL format."
  [requests]
  (->> requests
       (map request->jsonl-line)
       (str/join "\n")))

(defn- upload-batch-file
  "Uploads the JSONL file for batch processing."
  [api-key jsonl-content]
  (let [response (http/post files-url
                            {:headers {"Authorization" (str "Bearer " api-key)}
                             :multipart [{:name "purpose" :content "batch"}
                                         {:name "file"
                                          :content (.getBytes ^String jsonl-content)
                                          :filename "batch.jsonl"}]
                             :cookie-policy :standard
                             :throw-exceptions false})]
    (if (<= 200 (:status response) 299)
      (-> response :body (json/parse-string true) :id)
      (throw (errors/api-error :openai
                               (:status response)
                               (:body response))))))

(defn- parse-batch-status [body]
  (let [data (if (string? body) (json/parse-string body true) body)]
    {:id (:id data)
     :status (keyword (:status data))
     :created-at (:created_at data)
     :completed-at (:completed_at data)
     :request-counts {:total (get-in data [:request_counts :total])
                      :completed (get-in data [:request_counts :completed])
                      :failed (get-in data [:request_counts :failed])}
     :output-file-id (:output_file_id data)
     :error-file-id (:error_file_id data)
     :metadata (:metadata data)}))

(defn download-file
  "Downloads a file by ID and returns content as string."
  [api-key file-id]
  (let [url (str files-url "/" file-id "/content")
        response (http/get url
                           {:headers (build-headers api-key)
                            :cookie-policy :standard
                            :throw-exceptions false})]
    (if (<= 200 (:status response) 299)
      (:body response)
      (throw (errors/api-error :openai (:status response) (:body response))))))

(defn parse-results-jsonl
  "Parses JSONL results into a map keyed by custom-id."
  [jsonl-content]
  (with-open [reader (BufferedReader. (StringReader. jsonl-content))]
    (->> (line-seq reader)
         (map #(json/parse-string % true))
         (reduce (fn [acc line]
                   (assoc acc (:custom_id line)
                          {:status (get-in line [:response :status_code])
                           :body (get-in line [:response :body])
                           :error (:error line)}))
                 {}))))

(defrecord OpenAIBatchProvider [api-key]
  core/BatchProvider

  (create-batch [_ requests options]
    (let [jsonl (requests->jsonl requests)
          file-id (upload-batch-file api-key jsonl)
          payload (cond-> {:input_file_id file-id
                           :endpoint "/v1/chat/completions"
                           :completion_window (or (:completion-window options) "24h")}
                    (:metadata options) (assoc :metadata (:metadata options)))
          response (http/post batches-url
                              {:headers (build-headers api-key)
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-batch-status (:body response))
        (throw (errors/api-error :openai
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
        (throw (errors/api-error :openai
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
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response))))))

  (list-batches [_ options]
    (let [params (cond-> {}
                   (:limit options) (assoc :limit (:limit options))
                   (:after options) (assoc :after (:after options)))
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
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)))))))

(defn create-provider
  "Creates an OpenAI Batch provider."
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :openai api-key)]
    (->OpenAIBatchProvider key)))

;; High-level convenience functions

(defn get-results
  "Retrieves and parses batch results.
  Returns nil if batch not complete, map of custom-id->result if complete."
  [provider batch-id]
  (let [status (core/get-batch provider batch-id)]
    (when (= :completed (:status status))
      (let [content (download-file (:api-key provider) (:output-file-id status))]
        (parse-results-jsonl content)))))

(defn wait-for-completion
  "Polls batch status until complete or timeout.

  Options:
  - :poll-interval-ms - Polling interval (default: 60000)
  - :timeout-ms - Maximum wait time (default: 86400000 = 24h)

  Returns final batch status."
  [provider batch-id & [{:keys [poll-interval-ms timeout-ms]
                         :or {poll-interval-ms 60000
                              timeout-ms 86400000}}]]
  (let [start (System/currentTimeMillis)]
    (loop []
      (let [status (core/get-batch provider batch-id)
            elapsed (- (System/currentTimeMillis) start)]
        (cond
          (#{:completed :failed :expired :cancelled} (:status status))
          status

          (> elapsed timeout-ms)
          (throw (errors/timeout-error :openai timeout-ms))

          :else
          (do
            (Thread/sleep poll-interval-ms)
            (recur)))))))
