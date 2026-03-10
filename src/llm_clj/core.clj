(ns llm-clj.core
  (:require [clojure.string :as str]))

(defprotocol LLMProvider
  "Core protocol for Language Model providers."
  (chat-completion [this messages options]
    "Given a sequence of messages and options, returns a normalized response map.")
  (chat-completion-stream [this messages options]
    "Given a sequence of messages and options, returns a core.async channel
    that emits normalized streaming events:
    - {:type :delta :content \"...\"}
    - {:type :complete :content \"...\" :usage {...} :finish-reason :keyword}
    - {:type :error :error <exception>}"))

(defn normalize-message
  "Ensures a message map has the canonical keys.
  Common roles: :system, :user, :assistant, :tool"
  [{:keys [role content name tool-calls tool-call-id] :as _msg}]
  (cond-> {:role (keyword role)}
    content (assoc :content content)
    name (assoc :name name)
    tool-calls (assoc :tool-calls tool-calls)
    tool-call-id (assoc :tool-call-id tool-call-id)))

(defn extract-system-prompt
  "Extracts system prompts from a message sequence. 
  Returns a vector of `[system-prompt-string remaining-messages]`"
  [messages]
  (let [system-msgs (filter #(= :system (:role %)) messages)
        other-msgs (remove #(= :system (:role %)) messages)
        system-prompt (when (seq system-msgs)
                        (str/join "\n\n" (map :content system-msgs)))]
    [system-prompt other-msgs]))
