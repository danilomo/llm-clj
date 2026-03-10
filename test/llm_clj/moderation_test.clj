(ns llm-clj.moderation-test
  (:require [clojure.test :refer [deftest is]]
            [llm-clj.moderation.openai :as mod]))

(deftest test-category-key-conversion
  (is (= :hate (#'mod/category-key->keyword "hate")))
  (is (= :hate-threatening (#'mod/category-key->keyword "hate/threatening")))
  (is (= :self-harm-intent (#'mod/category-key->keyword "self-harm/intent"))))

(deftest test-transform-categories
  (let [input {:hate false :violence true "hate/threatening" false}
        result (#'mod/transform-categories input)]
    (is (false? (:hate result)))
    (is (true? (:violence result)))
    (is (false? (:hate-threatening result)))))

(deftest test-flagged?
  (is (true? (mod/flagged? {:flagged true})))
  (is (false? (mod/flagged? {:flagged false}))))

(deftest test-any-flagged?
  (is (true? (mod/any-flagged? {:results [{:flagged false} {:flagged true}]})))
  (is (false? (mod/any-flagged? {:results [{:flagged false} {:flagged false}]}))))

(deftest test-flagged-categories
  (let [result {:categories {:hate false :violence true :harassment true}}]
    (is (= #{:violence :harassment} (mod/flagged-categories result)))))

(deftest test-high-score-categories
  (let [result {:category-scores {:hate 0.1 :violence 0.8 :harassment 0.4}}]
    (is (= {:violence 0.8 :harassment 0.4} (mod/high-score-categories result 0.3)))
    (is (= {:violence 0.8} (mod/high-score-categories result 0.5)))))

(deftest test-category-score
  (let [result {:category-scores {:violence 0.85}}]
    (is (= 0.85 (mod/category-score result :violence)))
    (is (nil? (mod/category-score result :hate)))))
