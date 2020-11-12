(ns ifarafontov.transit-publisher-no-rotate-integration-test
  (:require [clojure.test :refer :all]
            [ifarafontov.transit-publisher :as tp]
            [ifarafontov.test-commons :refer [test-dir
                                              create-test-dir
                                              read-all-logs
                                              start-publisher
                                              inst-write-handler]]
            [cognitect.transit :as transit]
            [com.brunobonacci.mulog :as mu]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.time Instant]))


(use-fixtures :each create-test-dir)

(deftest rotate-test
  (testing "(rotate ..) renames current log file and creates a new one"
    (let [now (Instant/now)
          suffix "app.log.json"
          file (File. @test-dir (str (.toEpochMilli now) "_" suffix))
          _ (spit file "it's all good, man!")
          new-file (tp/rotate file now suffix)
          _ (spit new-file "More good news")
          expected-rotated-name (str suffix "." (tp/local-timestamp now))
          expected-current-name (str (.toEpochMilli now) "_" suffix)]

      (is (= 2 (count (.list @test-dir))))
      (is (= "More good news" (slurp (File. @test-dir expected-current-name))))
      (is (= "it's all good, man!" (slurp (File. @test-dir expected-rotated-name)))))))

(deftest empty-folder-test
  (let [stop   (start-publisher {:transform #(assoc % :extra "extra")
                                 :transit-handlers
                                 {Instant inst-write-handler}})
        now (Instant/ofEpochMilli (.toEpochMilli (Instant/now)))]
    (try
      (mu/log :hello :key "value" :inst now)
      (Thread/sleep 1500)
      (let [res (read-all-logs @test-dir)]
        (is (= 1 (count (.list @test-dir))))
        (is (= [{:mulog/event-name :hello :key "value" :inst now :extra "extra"}]
               (mapv #(select-keys % [:mulog/event-name :key :inst :extra])
                     res))))
      (finally
        (stop)))))

(deftest append-to-existing-test
  (let [fs (io/make-output-stream
            (File. @test-dir (str (.toEpochMilli (Instant/now)) "_" "app.log.json")) {})
        _ (and (transit/write (transit/writer fs :json)
                              {:mulog/event-name :start :key "started"})
               (.close fs))
        stop (start-publisher {})]
    (try  (mu/log :continue :key "continued")
          (Thread/sleep 1500)
          (let [res (read-all-logs @test-dir)]
            (is (= 1 (count (.list @test-dir))))
            (is (= [{:mulog/event-name :start, :key "started"}
                    {:mulog/event-name :continue, :key "continued"}]
                   (mapv #(select-keys % [:mulog/event-name :key])
                         res))))
          (finally (stop)))))