(ns ifarafontov.transit-publisher-unit-test
  (:require [clojure.test :refer :all]
            [ifarafontov.transit-publisher :as tp]
            [cognitect.transit :as transit]
            [ifarafontov.NoopFlushOutputStream]
            [clojure.java.io :as io]
            [com.brunobonacci.mulog.flakes :as fl])
  (:import [java.io ByteArrayOutputStream File ByteArrayInputStream]
           [java.time Instant]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [com.brunobonacci.mulog.core Flake]))

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

(deftest throwable-keys-test
  (is (= #{} (tp/throwable-keys {:key "value" 1 2})))
  (is (= #{:t "ex"} (tp/throwable-keys {:k "V"
                                        :t (Exception. "Boom!")
                                        "s" "String-key"
                                        "ex" (Exception. "Bang!")}))))

(deftest convert-throwables-test
  (is (= {} (tp/convert-throwables {})))
  (is (= {:key "value"} (tp/convert-throwables {:key "value"})))
  (let [boom (Exception. "Boom!")
        bang (Exception. "Bang!")]
    (is (= {:key "value"
            :t (Throwable->map boom)
            "str-key" (Throwable->map bang)}
           (tp/convert-throwables {:key "value"
                                   :t  boom
                                   "str-key" bang})))))

(deftest transform-test
  (testing "Transducer test"
    (let [xf (tp/get-xf #(when-not (:dont-log (set (keys %))) %))
          e (Exception. "Boom!")
          res (into [] xf [[0 {:event :app-started :metric 42}]
                           [1 {:dont-log true}]
                           [2 {:event :ms-db :exception e}]
                           [3 {:event :trace :dont-log true}]])]

      (is (= [{:event :app-started, :metric 42}
              {:event :ms-db :exception (Throwable->map e)}]
             res)))))


(deftest flake-handlers-test
  (let [dest (ByteArrayOutputStream. 2048)
        w (transit/writer dest :json {:handlers {Flake tp/flake-write-handler}})
        f (fl/flake)
        _ (transit/write w f)
        in (ByteArrayInputStream. (.toByteArray dest))
        r (transit/reader in :json {:handlers {"flake" tp/flake-read-handler}})
        res (transit/read r)]
    (is (= f res))))

(deftest parse-created-at-test
  (let [now (Instant/ofEpochMilli (.toEpochMilli (Instant/now)))
        then (Instant/ofEpochMilli (.toEpochMilli (.minusMillis now 60000)))
        res (tp/parse-created-at "suffix"
                                 ["gar_bage" "123_qwe" "123_suffix_suffix"
                                  (str (.toEpochMilli now) "_suffix")]
                                 now)]
    (is (=  [now (str (.toEpochMilli now) "_suffix")] res))
    (is (= [now (str (.toEpochMilli now) "_suffix")]
           (tp/parse-created-at "suffix" [] now)))
    (is (thrown? AssertionError
                 (tp/parse-created-at "suffix"
                                      [(str (.toEpochMilli now) "_suffix")
                                       (str (.toEpochMilli then) "_suffix")]
                                      now)))))






