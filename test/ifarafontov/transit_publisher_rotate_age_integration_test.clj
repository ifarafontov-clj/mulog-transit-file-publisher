(ns ifarafontov.transit-publisher-rotate-age-integration-test
  (:require [clojure.test :refer :all]
            [ifarafontov.test-commons :refer [test-dir
                                              create-test-dir
                                              read-all-logs
                                              start-publisher]]
            [com.brunobonacci.mulog :as mu]))


(use-fixtures :each create-test-dir)

(deftest rotate-age-test
  (let [stop (start-publisher {:rotate-age {:seconds 2}})]
    (try
      (mu/log :old :key "first")
      (Thread/sleep 2500)
      (mu/log :young :key "second")
      (Thread/sleep 500)
      (finally
        (stop)))

    (let [res (read-all-logs @test-dir)]
      (is (= 2 (count (.list @test-dir))))
      (is (every? pos-int? (map #(.length %) (.listFiles @test-dir))))
      (is (= [{:mulog/event-name :young, :key "second"}
              {:mulog/event-name :old, :key "first"}]
             (mapv #(select-keys % [:mulog/event-name :key])
                   res))))))