(ns llm-clj.audio-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.audio.openai :as audio]))

(deftest test-build-transcription-multipart-basic
  (let [multipart (#'audio/build-transcription-multipart "test.mp3" {})]
    (is (some #(= "model" (:name %)) multipart))
    (is (some #(= "file" (:name %)) multipart))))

(deftest test-build-transcription-multipart-with-options
  (let [multipart (#'audio/build-transcription-multipart "test.mp3"
                                                         {:language "en"
                                                          :prompt "Meeting transcript"
                                                          :response-format :verbose_json})]
    (is (some #(= "language" (:name %)) multipart))
    (is (some #(= "prompt" (:name %)) multipart))
    (is (some #(= "response_format" (:name %)) multipart))))

(deftest test-build-transcription-multipart-with-temperature
  (let [multipart (#'audio/build-transcription-multipart "test.mp3"
                                                         {:temperature 0.5})]
    (is (some #(and (= "temperature" (:name %))
                    (= "0.5" (:content %))) multipart))))

(deftest test-build-transcription-multipart-with-timestamp-granularities
  (let [multipart (#'audio/build-transcription-multipart "test.mp3"
                                                         {:timestamp-granularities [:word :segment]})]
    (is (some #(and (= "timestamp_granularities[]" (:name %))
                    (= "word,segment" (:content %))) multipart))))

(deftest test-parse-transcription-json
  (let [response {:body "{\"text\":\"Hello\",\"duration\":1.5}"}
        result (#'audio/parse-transcription-response response :json)]
    (is (= "Hello" (:text result)))
    (is (= 1.5 (:duration result)))))

(deftest test-parse-transcription-verbose-json
  (let [response {:body "{\"text\":\"Hello\",\"segments\":[{\"id\":0}],\"words\":[{\"word\":\"Hello\"}],\"language\":\"en\",\"duration\":2.0}"}
        result (#'audio/parse-transcription-response response :verbose_json)]
    (is (= "Hello" (:text result)))
    (is (= [{:id 0}] (:segments result)))
    (is (= [{:word "Hello"}] (:words result)))
    (is (= "en" (:language result)))
    (is (= 2.0 (:duration result)))))

(deftest test-parse-transcription-text
  (let [response {:body "Hello world"}
        result (#'audio/parse-transcription-response response :text)]
    (is (= "Hello world" (:text result)))))

(deftest test-parse-transcription-srt
  (let [response {:body "1\n00:00:00,000 --> 00:00:01,000\nHello"}
        result (#'audio/parse-transcription-response response :srt)]
    (is (= "1\n00:00:00,000 --> 00:00:01,000\nHello" (:text result)))))

(deftest test-parse-transcription-vtt
  (let [response {:body "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHello"}
        result (#'audio/parse-transcription-response response :vtt)]
    (is (= "WEBVTT\n\n00:00:00.000 --> 00:00:01.000\nHello" (:text result)))))

(deftest test-parse-transcription-nil-format
  (let [response {:body "{\"text\":\"Hello\"}"}
        result (#'audio/parse-transcription-response response nil)]
    (is (= "Hello" (:text result)))))

(deftest test-audio->multipart-string-path
  (let [result (#'audio/audio->multipart "path/to/audio.mp3")]
    (is (= "file" (:name result)))
    (is (= "audio.mp3" (:filename result)))))

(deftest test-audio->multipart-bytes
  (let [audio-bytes (byte-array [1 2 3 4])
        result (#'audio/audio->multipart audio-bytes)]
    (is (= "file" (:name result)))
    (is (= "audio.mp3" (:filename result)))
    (is (bytes? (:content result)))))

(deftest test-audio->multipart-invalid-input
  (is (thrown-with-msg? clojure.lang.ExceptionInfo
                        #"Audio must be file path or byte array"
                        (#'audio/audio->multipart 123))))
