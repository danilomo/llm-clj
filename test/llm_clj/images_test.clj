(ns llm-clj.images-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.images.openai :as img]))

(deftest test-quality-conversion
  (is (= "hd" (#'img/quality->api :hd)))
  (is (= "high" (#'img/quality->api :high)))
  (is (= "standard" (#'img/quality->api :standard))))

(deftest test-build-generation-payload-basic
  (let [payload (#'img/build-generation-payload "test" {})]
    (is (= "test" (:prompt payload)))
    (is (= "gpt-image-1" (:model payload)))))

(deftest test-build-generation-payload-with-options
  (let [payload (#'img/build-generation-payload "test"
                                                {:model "dall-e-3"
                                                 :n 2
                                                 :size "1024x1024"
                                                 :quality :hd
                                                 :style :vivid})]
    (is (= "dall-e-3" (:model payload)))
    (is (= 2 (:n payload)))
    (is (= "1024x1024" (:size payload)))
    (is (= "hd" (:quality payload)))
    (is (= "vivid" (:style payload)))))

(deftest test-build-generation-payload-with-response-format
  (let [payload (#'img/build-generation-payload "test" {:response-format :b64_json})]
    (is (= "b64_json" (:response_format payload)))))

(deftest test-build-generation-payload-with-user
  (let [payload (#'img/build-generation-payload "test" {:user "user123"})]
    (is (= "user123" (:user payload)))))

(deftest test-decode-b64-image
  (let [original "Hello"
        encoded (.encodeToString (java.util.Base64/getEncoder) (.getBytes original))
        decoded (img/decode-b64-image encoded)]
    (is (= "Hello" (String. decoded)))))

(deftest test-decode-b64-image-binary
  (let [original (byte-array [0x89 0x50 0x4E 0x47])  ; PNG magic bytes
        encoded (.encodeToString (java.util.Base64/getEncoder) original)
        decoded (img/decode-b64-image encoded)]
    (is (= (seq original) (seq decoded)))))
