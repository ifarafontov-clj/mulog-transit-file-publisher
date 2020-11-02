(ns ifarafontov.transit-publisher-rotate-age-integration-test
  (:require [clojure.test :refer :all]
            [ifarafontov.transit-publisher :as tp]
            [ifarafontov.test-commons :refer [test-dir create-test-dir read-all-logs]]
            [cognitect.transit :as transit]
            [com.brunobonacci.mulog :as mu]
            [clojure.java.io :as io])
  (:import [java.io File]
           [java.time Instant]))


(use-fixtures :each create-test-dir)


(deftest rotate-age-test
  (let [_ (println (.getAbsolutePath @test-dir))
        stop   (mu/start-publisher!
                {:type :custom
                 :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
                 :dir-name (.getAbsolutePath @test-dir)
                 :rotate-age {:seconds 2}})]
    (try
      (mu/log :old :key "first")
      (Thread/sleep 2500)
      (mu/log :young :key "second")
      (Thread/sleep 500)
      (finally
        (stop)))

    (let [res (read-all-logs @test-dir)]
      (is (= 2 (count (.list @test-dir))))
      (is (= [{:mulog/event-name :young, :key "second"}
              {:mulog/event-name :old, :key "first"}]
             (mapv #(select-keys % [:mulog/event-name :key])
                   res))))))