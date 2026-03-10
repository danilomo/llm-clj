(ns llm-clj.vision
  (:require [clojure.java.io :as io]
            [clojure.string :as str])
  (:import [java.util Base64]))

(defn image-url
  "Creates an image content block from a URL.
  Options:
  - :detail - OpenAI only: 'auto', 'low', or 'high' (default: 'auto')"
  ([url] (image-url url {}))
  ([url {:keys [detail] :or {detail "auto"}}]
   {:type :image-url
    :url url
    :detail detail}))

(defn image-base64
  "Creates an image content block from base64-encoded data.
  media-type should be 'image/jpeg', 'image/png', 'image/gif', or 'image/webp'"
  [base64-data media-type]
  {:type :image-base64
   :data base64-data
   :media-type media-type})

(defn image-file
  "Creates an image content block from a local file path.
  Reads the file and encodes it as base64."
  [file-path]
  (let [file (io/file file-path)
        bytes (java.nio.file.Files/readAllBytes (.toPath file))
        base64 (.encodeToString (Base64/getEncoder) bytes)
        ext (-> file-path (str/split #"\.") last str/lower-case)
        media-type (case ext
                     "jpg" "image/jpeg"
                     "jpeg" "image/jpeg"
                     "png" "image/png"
                     "gif" "image/gif"
                     "webp" "image/webp"
                     (throw (ex-info "Unsupported image format" {:extension ext})))]
    (image-base64 base64 media-type)))

(defn text-content
  "Creates a text content block for multi-part messages."
  [text]
  {:type :text
   :text text})

(defn vision-message
  "Creates a user message with mixed text and image content.
  content-parts should be a vector of text-content and image-* blocks."
  [content-parts]
  {:role :user
   :content content-parts})
