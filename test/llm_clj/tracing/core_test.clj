(ns llm-clj.tracing.core-test
  (:require [clojure.test :refer [deftest testing is use-fixtures]]
            [llm-clj.tracing.core :as tracing]
            [llm-clj.tracing.config :as config]
            [llm-clj.tracing.span :as span]))

;; Test tracer that records all operations
(defrecord MockTracer [spans-atom]
  tracing/Tracer
  (start-span [_ span-name attributes parent-ctx]
    (let [s (span/create-span span-name attributes parent-ctx)]
      (swap! spans-atom conj {:event :start :span s})
      s))
  (end-span [_ s]
    (swap! spans-atom conj {:event :end :span-id (:id s)})
    nil)
  (record-exception [_ s exception]
    (swap! spans-atom conj {:event :exception :span-id (:id s) :exception exception})
    nil)
  (set-status [_ s status message]
    (swap! spans-atom conj {:event :status :span-id (:id s) :status status :message message})
    nil))

(defn create-mock-tracer []
  (->MockTracer (atom [])))

(defn get-events [tracer]
  @(:spans-atom tracer))

(defn reset-events! [tracer]
  (reset! (:spans-atom tracer) []))

;; Fixture to ensure tracing is enabled for tests
(defn with-tracing-enabled [f]
  (let [original-config (config/get-config)]
    (config/configure! {:enabled true :sample-rate 1.0})
    (try
      (f)
      (finally
        (config/reset-config!)
        (config/configure! original-config)))))

(use-fixtures :each with-tracing-enabled)

;; Tests

(deftest test-span-creation
  (testing "create-span generates valid span"
    (let [s (span/create-span "test.span" {:key "value"})]
      (is (string? (:id s)))
      (is (string? (:trace-id s)))
      (is (= "test.span" (:name s)))
      (is (= {:key "value"} (:attributes s)))
      (is (number? (:start-time s)))
      (is (nil? (:end-time s)))
      (is (= :unset (:status s))))))

(deftest test-span-with-parent-context
  (testing "create-span inherits trace-id from parent"
    (let [parent-ctx {:trace-id "parent-trace-123" :span-id "parent-span-456"}
          s (span/create-span "child.span" {} parent-ctx)]
      (is (= "parent-trace-123" (:trace-id s)))
      (is (= "parent-span-456" (:parent-id s)))
      (is (not= "parent-span-456" (:id s))))))

(deftest test-span-end
  (testing "end-span sets end-time"
    (let [s (span/create-span "test.span" {})
          ended (span/end-span s)]
      (is (number? (:end-time ended)))
      (is (>= (:end-time ended) (:start-time ended))))))

(deftest test-span-duration
  (testing "span-duration-ms calculates duration"
    (let [s (-> (span/create-span "test.span" {})
                (assoc :start-time 1000 :end-time 1500))]
      (is (= 500 (span/span-duration-ms s)))))

  (testing "span-duration-ms returns nil for unended span"
    (let [s (span/create-span "test.span" {})]
      (is (nil? (span/span-duration-ms s))))))

(deftest test-span-attributes
  (testing "set-span-attribute adds attribute"
    (let [s (-> (span/create-span "test.span" {})
                (span/set-span-attribute "key1" "value1"))]
      (is (= "value1" (get-in s [:attributes "key1"])))))

  (testing "set-span-attributes merges multiple attributes"
    (let [s (-> (span/create-span "test.span" {:existing "value"})
                (span/set-span-attributes {"new1" "val1" "new2" "val2"}))]
      (is (= {:existing "value" "new1" "val1" "new2" "val2"}
             (:attributes s))))))

(deftest test-span-exception
  (testing "record-span-exception sets error status"
    (let [ex (Exception. "Test error")
          s (-> (span/create-span "test.span" {})
                (span/record-span-exception ex))]
      (is (= :error (:status s)))
      (is (= "Test error" (:status-message s)))
      (is (= ex (:exception s)))
      (is (= "java.lang.Exception" (get-in s [:attributes span/attr-error-type])))
      (is (= "Test error" (get-in s [:attributes span/attr-error-message]))))))

(deftest test-span-events
  (testing "add-span-event adds event"
    (let [s (-> (span/create-span "test.span" {})
                (span/add-span-event "event1" {:data "value"}))]
      (is (= 1 (count (:events s))))
      (is (= "event1" (-> s :events first :name)))
      (is (= {:data "value"} (-> s :events first :attributes))))))

(deftest test-with-span-macro
  (testing "with-span executes body and returns result"
    (let [mock (create-mock-tracer)]
      (tracing/with-tracer [mock]
        (let [result (tracing/with-span [s "test.operation" {:key "value"}]
                       (+ 1 2))]
          (is (= 3 result))
          (let [events (get-events mock)]
            (is (= 3 (count events)))
            (is (= :start (:event (first events))))
            (is (= "test.operation" (-> events first :span :name)))
            (is (= {:key "value"} (-> events first :span :attributes)))
            (is (= :status (:event (second events))))
            (is (= :ok (:status (second events))))
            (is (= :end (:event (nth events 2))))))))))

(deftest test-with-span-exception-handling
  (testing "with-span records exception and re-throws"
    (let [mock (create-mock-tracer)]
      (tracing/with-tracer [mock]
        (is (thrown? Exception
                     (tracing/with-span [s "failing.operation" {}]
                       (throw (Exception. "Test failure")))))
        (let [events (get-events mock)]
          (is (some #(= :exception (:event %)) events))
          (is (some #(and (= :status (:event %)) (= :error (:status %))) events))
          (is (some #(= :end (:event %)) events)))))))

(deftest test-nested-spans
  (testing "nested spans share trace-id"
    (let [mock (create-mock-tracer)]
      (tracing/with-tracer [mock]
        (tracing/with-span [outer "outer.span" {}]
          (tracing/with-span [inner "inner.span" {}]
            "nested"))
        (let [events (get-events mock)
              starts (filter #(= :start (:event %)) events)
              outer-span (-> starts first :span)
              inner-span (-> starts second :span)]
          (is (= 2 (count starts)))
          (is (= (:trace-id outer-span) (:trace-id inner-span)))
          (is (= (:id outer-span) (:parent-id inner-span))))))))

(deftest test-with-trace-macro
  (testing "with-trace creates new root trace"
    (let [mock (create-mock-tracer)]
      (tracing/with-tracer [mock]
        (tracing/with-trace [t "root.trace" {:agent "test"}]
          "result")
        (let [events (get-events mock)
              start-event (first (filter #(= :start (:event %)) events))]
          (is (nil? (-> start-event :span :parent-id))))))))

(deftest test-tracing-disabled
  (testing "with-span passes through when disabled"
    (config/configure! {:enabled false})
    (let [mock (create-mock-tracer)
          result (tracing/with-tracer [mock]
                   (tracing/with-span [s "test.span" {}]
                     42))]
      (is (= 42 result))
      (is (empty? (get-events mock))))))

(deftest test-config-functions
  (testing "configure! updates config"
    (config/configure! {:enabled true :backend :json :sample-rate 0.5})
    (is (true? (config/enabled?)))
    (is (= :json (config/backend)))
    (is (= 0.5 (config/sample-rate))))

  (testing "reset-config! restores defaults"
    (config/reset-config!)
    (is (false? (config/enabled?)))
    (is (= :noop (config/backend)))
    (is (= 1.0 (config/sample-rate)))))

(deftest test-privacy-defaults
  (testing "capture-messages is off by default"
    (config/reset-config!)
    (is (false? (config/capture-messages?))))

  (testing "capture-tool-args is off by default"
    (config/reset-config!)
    (is (false? (config/capture-tool-args?)))))

(deftest test-span-to-map
  (testing "span->map converts span to plain map"
    (let [s (-> (span/create-span "test.span" {:key "value"})
                (assoc :end-time (+ (System/currentTimeMillis) 100))
                (span/set-span-status :ok nil))
          m (span/span->map s)]
      (is (map? m))
      (is (= "test.span" (:name m)))
      (is (= {:key "value"} (:attributes m)))
      (is (contains? m :duration-ms)))))

(deftest test-add-attribute-during-span
  (testing "add-attribute modifies current span"
    (let [mock (create-mock-tracer)]
      (config/configure! {:enabled true})
      (tracing/with-tracer [mock]
        (tracing/with-span [_ "test.span" {}]
          (tracing/add-attribute "dynamic.key" "dynamic.value")
          (tracing/add-attributes {"key1" "val1" "key2" "val2"}))))))

(deftest test-sampling
  (testing "should-sample? respects sample rate"
    (config/configure! {:sample-rate 1.0})
    (is (every? identity (repeatedly 10 config/should-sample?)))

    (config/configure! {:sample-rate 0.0})
    (is (every? false? (repeatedly 10 config/should-sample?)))))
