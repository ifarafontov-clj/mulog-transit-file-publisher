(ns ifarafontov.transit-publisher-rotate-size-integration-test
  (:require [clojure.test :refer :all]
            [ifarafontov.test-commons :refer [test-dir
                                              create-test-dir
                                              read-all-logs
                                              start-publisher]]
            [com.brunobonacci.mulog :as mu]))


(use-fixtures :each create-test-dir)

(deftest rotate-size-test
  (let [stop  (start-publisher {:rotate-size {:kb 1}})
        kb2 (apply str (take 2048 (repeat "a")))]
    (try
      (mu/log :old :key "first" :msg kb2)
      (Thread/sleep 1000)
      (mu/log :young :key "second" :msg kb2)
      (Thread/sleep 1000)
      (finally
        (stop)))

    (let [res (read-all-logs @test-dir)]
      (is (= 3 (count (.list @test-dir))))
      (is (= [{:mulog/event-name :old, :key "first"}
              {:mulog/event-name :young, :key "second"}]
             (mapv #(select-keys % [:mulog/event-name :key])
                   res))))))