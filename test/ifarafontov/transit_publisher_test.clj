(ns ifarafontov.transit-publisher-test
  (:require [clojure.test :refer :all]
            [ifarafontov.transit-publisher :as tp]
            [cognitect.transit :as transit]
            [ifarafontov.NoopFlushOutputStream])
  (:import [java.io ByteArrayOutputStream File]
           [java.time Instant]))

(deftest noop-flush-test
  (testing "A flush() call has no effect, realFlush() call flushes bytes to destination"
    (let [dest (ByteArrayOutputStream. 1024)
          stream (ifarafontov.NoopFlushOutputStream. dest)
          writer (transit/writer stream :json)]
      (transit/write writer 42)
      (.flush stream)
      (is (zero? (.size dest)))
      (.realFlush stream)
      (is ((complement zero?) (.size dest))))))

(deftest units-conversion-test
  (testing "Units conversion works"
    (is (= 7260000 (tp/descriptor->value {:minutes 1 :hours 2} tp/time-units))))
  (testing "Units conversion throws with wrong arguments"
    (is (thrown? AssertionError (tp/descriptor->value {:km 1 :cm 5} tp/time-units)))
    (is (thrown? AssertionError (tp/descriptor->value {} tp/time-units)))
    (is (thrown? AssertionError (tp/descriptor->value {:minutes 0} tp/time-units)))))

(deftest rotate-conditions-test
  (testing "Age rotation"
    (let [now (Instant/now)
          minute-ago (.minusMillis now (* 60 1000))]
      (is (true? (tp/too-old? minute-ago now 1000)))
      (is (false? (tp/too-old? minute-ago now (* 2 60 1000))))))
  (testing "Size rotation"
    (is (false? (tp/too-big? 1 2)))
    (is (true? (tp/too-big? 2 1))))
  (testing "Rotate?"
    (let [file (proxy [File] ["non/existent"]
                 (length [] 42))
          minute-ago (.minusMillis (Instant/now) (* 60 1000))
          two-minutes (* 2 60 1000)
          dont-rotate [[nil nil]
                       [two-minutes nil]
                       [nil 43]
                       [two-minutes 43]]
          ten-seconds (* 10 1000)
          do-rotate [[ten-seconds nil]
                     [nil 41]
                     [ten-seconds 41]]
          rotate? (partial tp/rotate? file minute-ago (Instant/now))
          f (comp boolean rotate?)]
      (is (every? false? (map f dont-rotate)))
      (is (every? true? (map f do-rotate))))))






