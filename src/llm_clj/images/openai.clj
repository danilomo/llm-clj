(ns llm-clj.images.openai
  "OpenAI Images API implementation."
  (:require [llm-clj.images.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io])
  (:import [java.util Base64]))

(def ^:private generations-url "https://api.openai.com/v1/images/generations")
(def ^:private edits-url "https://api.openai.com/v1/images/edits")
(def ^:private variations-url "https://api.openai.com/v1/images/variations")

(def ^:private default-model "gpt-image-1")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)})

(defn- quality->api
  "Converts quality keyword to API value."
  [quality]
  (case quality
    :hd "hd"
    :high "high"
    :standard "standard"
    (name quality)))

(defn- build-generation-payload
  "Builds payload for image generation."
  [prompt options]
  (cond-> {:prompt prompt
           :model (or (:model options) default-model)}
    (:n options) (assoc :n (:n options))
    (:size options) (assoc :size (:size options))
    (:quality options) (assoc :quality (quality->api (:quality options)))
    (:style options) (assoc :style (name (:style options)))
    (:response-format options) (assoc :response_format (name (:response-format options)))
    (:user options) (assoc :user (:user options))))

(defn- parse-response
  "Parses the API response."
  [response]
  (let [body (-> response :body (json/parse-string true))]
    {:images (mapv (fn [item]
                     (cond-> {}
                       (:url item) (assoc :url (:url item))
                       (:b64_json item) (assoc :b64-json (:b64_json item))
                       (:revised_prompt item) (assoc :revised-prompt (:revised_prompt item))))
                   (:data body))
     :created (:created body)}))

(defn- file->multipart
  "Converts a file path to multipart form data."
  [path field-name]
  (let [file (io/file path)]
    {:name field-name
     :content file
     :filename (.getName file)}))

(defn- bytes->multipart
  "Converts bytes to multipart form data."
  [bytes field-name filename]
  {:name field-name
   :content bytes
   :filename filename})

(defn- image->multipart
  "Converts image input to multipart form data."
  [image field-name]
  (cond
    (string? image) (file->multipart image field-name)
    (bytes? image) (bytes->multipart image field-name "image.png")
    :else (throw (errors/validation-error
                  "Image must be file path or byte array"
                  {:image (type image)}))))

(defrecord OpenAIImageProvider [api-key]
  core/ImageProvider

  (generate-image [_ prompt options]
    (let [payload (build-generation-payload prompt options)
          response (http/post generations-url
                              {:headers (assoc (build-headers api-key)
                                               "Content-Type" "application/json")
                               :body (json/generate-string payload)
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (edit-image [_ image prompt options]
    (let [multipart (cond-> [(image->multipart image "image")
                             {:name "prompt" :content prompt}]
                      (:mask options) (conj (file->multipart (:mask options) "mask"))
                      (:model options) (conj {:name "model" :content (:model options)})
                      (:n options) (conj {:name "n" :content (str (:n options))})
                      (:size options) (conj {:name "size" :content (:size options)})
                      (:response-format options) (conj {:name "response_format"
                                                        :content (name (:response-format options))}))
          response (http/post edits-url
                              {:headers (build-headers api-key)
                               :multipart multipart
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (create-variation [_ image options]
    (let [multipart (cond-> [(image->multipart image "image")]
                      (:model options) (conj {:name "model" :content (:model options)})
                      (:n options) (conj {:name "n" :content (str (:n options))})
                      (:size options) (conj {:name "size" :content (:size options)})
                      (:response-format options) (conj {:name "response_format"
                                                        :content (name (:response-format options))}))
          response (http/post variations-url
                              {:headers (build-headers api-key)
                               :multipart multipart
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-response response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response)))))))

(defn create-provider
  "Creates an OpenAI Image provider.

  Options:
  - :api-key - OpenAI API key (optional, uses OPENAI_API_KEY env var)"
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :openai api-key)]
    (->OpenAIImageProvider key)))

;; Convenience functions

(defn generate
  "Convenience function to generate images without explicit provider.

  Usage:
  (generate \"A sunset over mountains\")
  (generate \"A cat\" {:size \"1024x1024\" :quality :hd})"
  ([prompt] (generate prompt {}))
  ([prompt options]
   (let [provider (create-provider (select-keys options [:api-key]))]
     (core/generate-image provider prompt (dissoc options :api-key)))))

(defn save-image
  "Downloads and saves an image from URL to a local file."
  [url output-path]
  (let [response (http/get url {:as :byte-array})]
    (io/copy (:body response) (io/file output-path))
    output-path))

(defn decode-b64-image
  "Decodes a base64 image to bytes."
  [b64-string]
  (.decode (Base64/getDecoder) b64-string))
