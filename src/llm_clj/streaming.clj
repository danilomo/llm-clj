(ns llm-clj.streaming
  (:require [clojure.core.async :as async :refer [chan close!]]
            [clojure.string :as str]))

(defn parse-sse-line
  "Parses a single SSE line into [field value] or nil."
  [line]
  (when-not (str/blank? line)
    (let [colon-idx (str/index-of line ":")]
      (when colon-idx
        [(subs line 0 colon-idx)
         (str/triml (subs line (inc colon-idx)))]))))

(defn parse-sse-events
  "Parses a chunk of SSE data into a sequence of event maps.
  Each event map has :event and :data keys."
  [chunk]
  (let [lines (str/split-lines chunk)]
    (loop [lines lines
           current-event {}
           events []]
      (if (empty? lines)
        (if (seq current-event) (conj events current-event) events)
        (let [line (first lines)
              [field value] (parse-sse-line line)]
          (cond
            (str/blank? line)
            (recur (rest lines) {} (if (seq current-event) (conj events current-event) events))

            (= field "event")
            (recur (rest lines) (assoc current-event :event value) events)

            (= field "data")
            (recur (rest lines) (update current-event :data #(str % (when % "\n") value)) events)

            :else
            (recur (rest lines) current-event events)))))))

;; Event types returned by streaming:
;; {:type :delta :content "chunk of text"}
;; {:type :tool-call-delta :id "..." :name "..." :arguments "..."}
;; {:type :complete :content "full text" :usage {...} :finish-reason :stop}
;; {:type :error :error <exception>}

(defn create-stream-channel
  "Creates a channel for streaming responses.
  Returns {:channel ch :buffer buffer-atom}"
  []
  (let [ch (chan 100)]
    {:channel ch
     :buffer (atom "")}))

(defn emit-delta!
  "Emits a content delta event to the channel."
  [ch content]
  (async/put! ch {:type :delta :content content}))

(defn emit-complete!
  "Emits a completion event and closes the channel."
  [ch content usage finish-reason]
  (async/put! ch {:type :complete
                  :content content
                  :usage usage
                  :finish-reason finish-reason})
  (close! ch))

(defn emit-error!
  "Emits an error event and closes the channel."
  [ch error]
  (async/put! ch {:type :error :error error})
  (close! ch))
