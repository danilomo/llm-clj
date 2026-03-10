(ns llm-clj.audio-integration-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.audio.openai :as audio]
            [llm-clj.audio.core :as audio-api]
            [clojure.string :as str]))

(defn has-api-key? []
  (boolean (System/getenv "OPENAI_API_KEY")))

(deftest ^:integration test-text-to-speech
  (when (has-api-key?)
    (let [provider (audio/create-provider {})
          result (audio-api/text-to-speech provider "Hello, this is a test."
                                           {:voice :alloy})]
      (is (bytes? result))
      (is (pos? (count result))))))

(deftest ^:integration test-text-to-speech-hd
  (when (has-api-key?)
    (let [provider (audio/create-provider {})
          result (audio-api/text-to-speech provider "High definition audio test."
                                           {:voice :nova
                                            :model "tts-1-hd"})]
      (is (bytes? result))
      (is (pos? (count result))))))

(deftest ^:integration test-text-to-speech-with-speed
  (when (has-api-key?)
    (let [provider (audio/create-provider {})
          result (audio-api/text-to-speech provider "Speed test."
                                           {:voice :echo
                                            :speed 1.5})]
      (is (bytes? result))
      (is (pos? (count result))))))

(deftest ^:integration test-speak-convenience
  (when (has-api-key?)
    (let [result (audio/speak "Hello from the speak function")]
      (is (bytes? result))
      (is (pos? (count result))))))

(deftest ^:integration test-speak-to-file
  (when (has-api-key?)
    (let [temp-file (java.io.File/createTempFile "test-audio" ".mp3")]
      (try
        (audio/speak-to-file "Testing speech" (.getPath temp-file))
        (is (.exists temp-file))
        (is (pos? (.length temp-file)))
        (finally
          (.delete temp-file))))))

(deftest ^:integration test-different-voices
  (when (has-api-key?)
    (let [provider (audio/create-provider {})]
      (doseq [voice [:alloy :echo :nova]]
        (let [result (audio-api/text-to-speech provider "Hi"
                                               {:voice voice})]
          (is (bytes? result) (str "Voice " voice " failed")))))))

(deftest ^:integration test-different-formats
  (when (has-api-key?)
    (let [provider (audio/create-provider {})]
      (doseq [format [:mp3 :opus :aac :flac]]
        (let [result (audio-api/text-to-speech provider "Format test"
                                               {:voice :alloy
                                                :response-format format})]
          (is (bytes? result) (str "Format " format " failed")))))))

;; Note: Transcription tests require sample audio files
;; which may not be available in CI environments

(deftest ^:integration test-transcribe-generated-speech
  (when (has-api-key?)
    (let [temp-file (java.io.File/createTempFile "test-audio" ".mp3")
          original-text "The quick brown fox jumps over the lazy dog"]
      (try
        ;; Generate speech
        (audio/speak-to-file original-text (.getPath temp-file)
                             {:voice :alloy})
        ;; Transcribe it back
        (let [result (audio/transcribe-file (.getPath temp-file))]
          (is (:text result))
          ;; Should contain at least some of the original words
          (is (or (str/includes?
                   (str/lower-case (:text result))
                   "quick")
                  (str/includes?
                   (str/lower-case (:text result))
                   "fox"))))
        (finally
          (.delete temp-file))))))

(deftest ^:integration test-transcribe-with-language
  (when (has-api-key?)
    (let [temp-file (java.io.File/createTempFile "test-audio" ".mp3")
          original-text "Hello world"]
      (try
        (audio/speak-to-file original-text (.getPath temp-file)
                             {:voice :alloy})
        (let [result (audio/transcribe-file (.getPath temp-file)
                                            {:language "en"})]
          (is (:text result)))
        (finally
          (.delete temp-file))))))

(deftest ^:integration test-transcribe-verbose-json
  (when (has-api-key?)
    (let [temp-file (java.io.File/createTempFile "test-audio" ".mp3")
          original-text "Testing verbose JSON response format"]
      (try
        (audio/speak-to-file original-text (.getPath temp-file)
                             {:voice :alloy})
        (let [provider (audio/create-provider {})
              result (audio-api/transcribe provider (.getPath temp-file)
                                           {:response-format :verbose_json})]
          (is (:text result))
          (is (:duration result)))
        (finally
          (.delete temp-file))))))

(deftest ^:integration test-translate
  (when (has-api-key?)
    (let [temp-file (java.io.File/createTempFile "test-audio" ".mp3")
          ;; Use English text for testing since we don't have non-English audio
          original-text "Hello this is a translation test"]
      (try
        (audio/speak-to-file original-text (.getPath temp-file)
                             {:voice :alloy})
        (let [provider (audio/create-provider {})
              result (audio-api/translate provider (.getPath temp-file) {})]
          (is (:text result)))
        (finally
          (.delete temp-file))))))
