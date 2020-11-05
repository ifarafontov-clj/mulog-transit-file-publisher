(ns ifarafontov.test-commons
  (:require
   [ifarafontov.transit-publisher :as tp]
   [com.brunobonacci.mulog :as mu])
  (:import
   [java.nio.file Files]
   [java.nio.file.attribute FileAttribute]))

(def test-dir (atom nil))

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
       (map #(tp/read-all-transit {:file-name %}))
       (apply concat)))

(defn start-publisher [args-map]
  (mu/start-publisher!
   (merge 
    {:type :custom
     :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
     :dir-name (.getAbsolutePath @test-dir)}
    args-map)))
