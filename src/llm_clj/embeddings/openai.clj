(ns llm-clj.embeddings.openai
  "OpenAI Embeddings API implementation."
  (:require [llm-clj.embeddings.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]))

(def ^:private embeddings-url "https://api.openai.com/v1/embeddings")

(def ^:private default-model "text-embedding-3-small")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)
   "Content-Type" "application/json"})

(defn- normalize-input
  "Ensures input is a vector of strings."
  [input]
  (if (string? input)
    [input]
    (vec input)))

(defn- build-payload
  "Builds the request payload."
  [input options]
  (cond-> {:model (or (:model options) default-model)
           :input (normalize-input input)}
    (:dimensions options) (assoc :dimensions (:dimensions options))
    (:encoding-format options) (assoc :encoding_format (name (:encoding-format options)))
    (:user options) (assoc :user (:user options))))

(defn- parse-response
  "Parses the API response."
  [response]
  (let [body (-> response :body (json/parse-string true))
        embeddings (->> (:data body)
                        (sort-by :index)
                        (mapv :embedding))]
    {:embeddings embeddings
     :model (:model body)
     :usage {:prompt-tokens (get-in body [:usage :prompt_tokens])
             :total-tokens (get-in body [:usage :total_tokens])}}))

(defrecord OpenAIEmbeddingProvider [api-key]
  core/EmbeddingProvider

  (create-embedding [_ input options]
    (let [payload (build-payload input options)
          response (http/post embeddings-url
                              {:headers (build-headers api-key)
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response)))))))

(defn create-provider
  "Creates an OpenAI Embedding provider.

  Options:
  - :api-key - OpenAI API key (optional, uses OPENAI_API_KEY env var)"
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :openai api-key)]
    (->OpenAIEmbeddingProvider key)))

;; Convenience functions

(defn embed
  "Convenience function to create embeddings without explicit provider.

  Usage:
  (embed \"text to embed\")
  (embed [\"text1\" \"text2\"] {:model \"text-embedding-3-large\"})
  (embed \"text\" {:api-key \"sk-...\" :dimensions 512})"
  ([input] (embed input {}))
  ([input options]
   (let [provider (create-provider (select-keys options [:api-key]))]
     (core/create-embedding provider input (dissoc options :api-key)))))

(defn cosine-similarity
  "Calculates cosine similarity between two embedding vectors."
  [v1 v2]
  (let [dot-product (reduce + (map * v1 v2))
        magnitude1 (Math/sqrt (reduce + (map #(* % %) v1)))
        magnitude2 (Math/sqrt (reduce + (map #(* % %) v2)))]
    (/ dot-product (* magnitude1 magnitude2))))
