(ns llm-clj.moderation.openai
  "OpenAI Content Moderation API implementation.

  Usage:
  (require '[llm-clj.moderation.openai :as mod])

  (mod/moderate \"content to check\")
  ;; => {:flagged false :categories {...} :category-scores {...}}"
  (:require [llm-clj.config :as config]
            [llm-clj.errors :as errors]
            [clj-http.client :as http]
            [cheshire.core :as json]
            [clojure.string :as str]))

(def ^:private moderation-url "https://api.openai.com/v1/moderations")

(def ^:private default-model "omni-moderation-latest")

(defn- build-headers [api-key]
  {"Authorization" (str "Bearer " api-key)
   "Content-Type" "application/json"})

(defn- category-key->keyword
  "Converts API category names to Clojure keywords.
  'hate/threatening' -> :hate-threatening"
  [s]
  (-> s
      (str/replace "/" "-")
      (str/replace "_" "-")
      keyword))

(defn- transform-categories
  "Transforms category map keys to keywords."
  [categories]
  (into {}
        (map (fn [[k v]] [(category-key->keyword (name k)) v]))
        categories))

(defn- parse-response
  "Parses the moderation API response."
  [response]
  (let [body (-> response :body (json/parse-string true))
        result (-> body :results first)]
    {:flagged (:flagged result)
     :categories (transform-categories (:categories result))
     :category-scores (transform-categories (:category_scores result))
     :model (:model body)
     :id (:id body)}))

(defn- parse-multi-response
  "Parses response for multiple inputs."
  [response]
  (let [body (-> response :body (json/parse-string true))]
    {:results (mapv (fn [result]
                      {:flagged (:flagged result)
                       :categories (transform-categories (:categories result))
                       :category-scores (transform-categories (:category_scores result))})
                    (:results body))
     :model (:model body)
     :id (:id body)}))

(defn moderate
  "Checks content against OpenAI's moderation policies.

  content - String or vector of strings to moderate

  Options:
  - :api-key - OpenAI API key (optional, uses OPENAI_API_KEY env var)
  - :model - Moderation model (default: omni-moderation-latest)

  Returns for single input:
  {:flagged boolean
   :categories {:hate false :violence true ...}
   :category-scores {:hate 0.001 :violence 0.85 ...}
   :model \"...\"
   :id \"...\"}

  Returns for multiple inputs:
  {:results [{:flagged ... :categories ... :category-scores ...} ...]
   :model \"...\"
   :id \"...\"}"
  ([content] (moderate content {}))
  ([content options]
   (let [api-key (config/resolve-api-key :openai (:api-key options))
         payload {:model (or (:model options) default-model)
                  :input content}
         response (http/post moderation-url
                             {:headers (build-headers api-key)
                              :body (json/generate-string payload)
                              :cookie-policy :standard
                              :throw-exceptions false})]
     (if (<= 200 (:status response) 299)
       (if (string? content)
         (parse-response response)
         (parse-multi-response response))
       (throw (errors/api-error :openai
                                (:status response)
                                (:body response)
                                :headers (:headers response)))))))

;; Convenience predicates

(defn flagged?
  "Returns true if the moderation result is flagged."
  [result]
  (:flagged result))

(defn any-flagged?
  "Returns true if any result in a batch is flagged."
  [batch-result]
  (boolean (some :flagged (:results batch-result))))

(defn flagged-categories
  "Returns a set of categories that are flagged (true).

  Usage:
  (flagged-categories result)
  ;; => #{:violence :harassment}"
  [result]
  (->> (:categories result)
       (filter (fn [[_ v]] v))
       (map first)
       set))

(defn high-score-categories
  "Returns categories with scores above the threshold.

  Usage:
  (high-score-categories result 0.5)
  ;; => {:violence 0.85}"
  [result threshold]
  (->> (:category-scores result)
       (filter (fn [[_ v]] (> v threshold)))
       (into {})))

(defn category-score
  "Gets the score for a specific category.

  Usage:
  (category-score result :violence)
  ;; => 0.85"
  [result category]
  (get (:category-scores result) category))

;; Batch moderation helpers

(defn moderate-messages
  "Moderates a sequence of chat messages.
  Returns results indexed by message position.

  Usage:
  (moderate-messages messages)
  ;; => [{:role :user :content \"...\" :moderation {...}} ...]"
  [messages & [options]]
  (let [contents (map :content messages)
        results (moderate (vec contents) options)]
    (mapv (fn [msg result]
            (assoc msg :moderation result))
          messages
          (:results results))))

(defn filter-safe-messages
  "Filters messages to only include those that pass moderation.

  Usage:
  (filter-safe-messages messages)"
  [messages & [options]]
  (->> (moderate-messages messages options)
       (filter (fn [msg] (not (:flagged (:moderation msg)))))
       (mapv #(dissoc % :moderation))))
