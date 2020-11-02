(ns ifarafontov.test-commons
  (:require
   [ifarafontov.transit-publisher :as tp])
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
