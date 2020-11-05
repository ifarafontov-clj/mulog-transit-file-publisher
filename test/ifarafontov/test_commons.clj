(ns ifarafontov.test-commons
  (:require
   [ifarafontov.transit-publisher :as tp]
   [com.brunobonacci.mulog :as mu]
   [cognitect.transit :as transit])
  (:import
   [java.nio.file Files]
   [java.time Instant]
   [java.nio.file.attribute FileAttribute]))

(def test-dir (atom nil))

(def inst-write-handler (transit/write-handler "inst" #(.toEpochMilli %)))
(def inst-read-handler (transit/read-handler #(Instant/ofEpochMilli %)))

(defn delete-dir
  [f]
  (when (.isDirectory f)
    (doseq [l (.listFiles f)]
      (delete-dir l)))
  (.delete f))

(defn create-test-dir [test]
  (try
    (reset! test-dir (.toFile (Files/createTempDirectory "tptest-" (into-array FileAttribute []))))
    (test)
    (finally
      (delete-dir @test-dir))))

(defn read-all-logs [dir]
  (->> dir
       (.listFiles)
       (map  #(.getAbsolutePath %))
       (sort)
       (map #(tp/read-all-transit {:file-name %
                                   :transit-handlers {"inst" inst-read-handler}}))
       (apply concat)))

(defn start-publisher [args-map]
  (mu/start-publisher!
   (merge 
    {:type :custom
     :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
     :dir-name (.getAbsolutePath @test-dir)}
    args-map)))
