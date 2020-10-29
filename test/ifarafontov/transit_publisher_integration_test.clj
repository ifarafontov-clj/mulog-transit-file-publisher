(ns ifarafontov.transit-publisher-integration-test
  (:require [clojure.test :refer :all]
            [ifarafontov.transit-publisher :as tp]
            [cognitect.transit :as transit]
            [ifarafontov.NoopFlushOutputStream]
            [com.brunobonacci.mulog :as mu]
            [clojure.java.io :as io]
            [com.brunobonacci.mulog.flakes :as fl])
  (:import [java.io ByteArrayOutputStream File ByteArrayInputStream]
           [java.time Instant]
           [java.nio.file Files]
           [java.nio.file.attribute FileAttribute]
           [com.brunobonacci.mulog.core Flake]))

(def test-dir (atom nil))

(defn delete-dir
  [f]
  (when (.isDirectory f)
    (doseq [l (.listFiles f)]
      (delete-dir l)))
  (.delete f))

(defn create-test-dir [test]
  (reset! test-dir (.toFile (Files/createTempDirectory "tptest-" (into-array FileAttribute []))))
  (test)
  (delete-dir @test-dir))

(defn read-all-logs [^File dir]
  (->> dir
       (.listFiles)
       (map  #(.getAbsolutePath %))
       (map #(tp/read-all-transit {:file-name %}))
       (apply concat)))

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

(deftest empty-folder-no-rotate-test
  (let [stop   (mu/start-publisher!
                {:type :custom
                 :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
                 :dir-name (.getAbsolutePath @test-dir)})]
    (try
      (mu/log :hello :key "value")
      (Thread/sleep 1500)
      (is (= 1 (count (.list @test-dir))))
      (let [res (read-all-logs @test-dir)]
        (is (= 1 (count res)))
        (is (= [{:mulog/event-name :hello :key "value"}]
               (mapv #(select-keys % [:mulog/event-name :key])
                     res))))
      (finally (stop)))))
