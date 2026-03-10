(ns llm-clj.audio.openai
  "OpenAI Audio API implementation."
  (:require [llm-clj.audio.core :as core]
            [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.java.io :as io]
            [clojure.string :as str]))

(def ^:private transcriptions-url "https://api.openai.com/v1/audio/transcriptions")
(def ^:private translations-url "https://api.openai.com/v1/audio/translations")
(def ^:private speech-url "https://api.openai.com/v1/audio/speech")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)})

(defn- audio->multipart
  "Converts audio input to multipart form data."
  [audio]
  (cond
    (string? audio)
    (let [file (io/file audio)]
      {:name "file"
       :content file
       :filename (.getName file)})

    (bytes? audio)
    {:name "file"
     :content audio
     :filename "audio.mp3"}

    :else
    (throw (errors/validation-error
            "Audio must be file path or byte array"
            {:audio (type audio)}))))

(defn- build-transcription-multipart
  "Builds multipart form data for transcription."
  [audio options]
  (cond-> [(audio->multipart audio)
           {:name "model" :content (or (:model options) "whisper-1")}]
    (:language options)
    (conj {:name "language" :content (:language options)})

    (:prompt options)
    (conj {:name "prompt" :content (:prompt options)})

    (:response-format options)
    (conj {:name "response_format" :content (name (:response-format options))})

    (:temperature options)
    (conj {:name "temperature" :content (str (:temperature options))})

    (:timestamp-granularities options)
    (conj {:name "timestamp_granularities[]"
           :content (str/join "," (map name (:timestamp-granularities options)))})))

(defn- parse-transcription-response
  "Parses transcription response based on format."
  [response response-format]
  (let [body (:body response)]
    (case response-format
      (:srt :vtt :text) {:text body}
      (:verbose_json :json nil)
      (let [parsed (json/parse-string body true)]
        (cond-> {:text (:text parsed)}
          (:segments parsed) (assoc :segments (:segments parsed))
          (:words parsed) (assoc :words (:words parsed))
          (:language parsed) (assoc :language (:language parsed))
          (:duration parsed) (assoc :duration (:duration parsed))))
      {:text body})))

(defrecord OpenAIAudioProvider [api-key]
  core/AudioProvider

  (transcribe [_ audio options]
    (let [multipart (build-transcription-multipart audio options)
          response (http/post transcriptions-url
                              {:headers (build-headers api-key)
                               :multipart multipart
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-transcription-response response (:response-format options))
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (translate [_ audio options]
    (let [multipart (build-transcription-multipart audio (dissoc options :language))
          response (http/post translations-url
                              {:headers (build-headers api-key)
                               :multipart multipart
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (parse-transcription-response response (:response-format options))
        (throw (errors/api-error :openai
                                 (:status response)
                                 (:body response)
                                 :headers (:headers response))))))

  (text-to-speech [_ text options]
    (let [payload (cond-> {:model (or (:model options) "tts-1")
                           :voice (name (or (:voice options) :alloy))
                           :input text}
                    (:response-format options)
                    (assoc :response_format (name (:response-format options)))

                    (:speed options)
                    (assoc :speed (:speed options)))
          response (http/post speech-url
                              {:headers (assoc (build-headers api-key)
                                               "Content-Type" "application/json")
                               :body (json/generate-string payload)
                               :as :byte-array
                               :cookie-policy :standard
                               :throw-exceptions false})]
      (if (<= 200 (:status response) 299)
        (:body response)
        (throw (errors/api-error :openai
                                 (:status response)
                                 (String. ^bytes (:body response))
                                 :headers (:headers response)))))))

(defn create-provider
  "Creates an OpenAI Audio provider.

  Options:
  - :api-key - OpenAI API key (optional, uses OPENAI_API_KEY env var)"
  [{:keys [api-key] :as _opts}]
  (let [key (config/resolve-api-key :openai api-key)]
    (->OpenAIAudioProvider key)))

;; Convenience functions

(defn transcribe-file
  "Convenience function to transcribe an audio file.

  Usage:
  (transcribe-file \"/path/to/audio.mp3\")
  (transcribe-file \"/path/to/audio.mp3\" {:language \"en\"})"
  ([path] (transcribe-file path {}))
  ([path options]
   (let [provider (create-provider (select-keys options [:api-key]))]
     (core/transcribe provider path (dissoc options :api-key)))))

(defn speak
  "Convenience function for text-to-speech.

  Usage:
  (speak \"Hello world\")
  (speak \"Hello\" {:voice :nova :speed 1.2})"
  ([text] (speak text {}))
  ([text options]
   (let [provider (create-provider (select-keys options [:api-key]))]
     (core/text-to-speech provider text (dissoc options :api-key)))))

(defn speak-to-file
  "Generates speech and saves to a file.

  Usage:
  (speak-to-file \"Hello world\" \"/path/to/output.mp3\")"
  ([text output-path] (speak-to-file text output-path {}))
  ([text output-path options]
   (let [audio-bytes (speak text options)]
     (with-open [out (io/output-stream (io/file output-path))]
       (.write out ^bytes audio-bytes))
     output-path)))
