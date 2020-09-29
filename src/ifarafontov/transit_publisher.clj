(ns ifarafontov.transit-publisher
  (:require
   [clojure.java.io :as io]
   [cognitect.transit :as transit]
   [clojure.set :as set]
   [com.brunobonacci.mulog.buffer :as rb]
   [com.brunobonacci.mulog.utils :as ut]
   [com.brunobonacci.mulog.publisher :refer [PPublisher]]
   [ifarafontov.NoopFlushOutputStream])
  (:import
   [java.nio.file Files CopyOption]
   [java.time.format DateTimeFormatter]
   [java.time LocalDateTime Instant ZoneId Duration]
   [com.brunobonacci.mulog.core Flake]
   [java.io File FileOutputStream]))

(set! *warn-on-reflection* true)

(def formatter (DateTimeFormatter/ofPattern "YYYYMMdd_hhmmss"))
(defn local-timestamp []
  (.format (LocalDateTime/ofInstant (Instant/now)
                                    (ZoneId/systemDefault)) formatter))

(def flake-write-handler (transit/write-handler "flake" #(str %)))
(def flake-read-handler (transit/read-handler #(Flake/parseFlake %)))

(defn too-old? [^Instant created-at ^Instant now max-age-ms]
  (> (.toMillis (Duration/between created-at now)) max-age-ms))

(defn too-big? [size-bs max-size-bs]
  (> size-bs max-size-bs))

(defn rotate? [^File file ^Instant created-at ^Instant now rotate-opts]
  (let [[max-age-ms max-size-bs] rotate-opts]
    (or (when max-age-ms (too-old? created-at now  max-age-ms))
        (when max-size-bs (too-big? (.length file) max-size-bs)))))

(defn creation-time [^File file]
  (.toInstant (.creationTime
               (java.nio.file.Files/readAttributes
                (.toPath file)
                java.nio.file.attribute.BasicFileAttributes
                (into-array java.nio.file.LinkOption [])))))

(def time-units (let [seconds 1000
                      minutes (* 60 seconds)
                      hours (* 60 minutes)
                      days (* 24 hours)
                      weeks (* 7 days)]
                  {:seconds seconds
                   :minutes minutes
                   :hours hours
                   :days days
                   :weeks weeks}))

(def size-units (let [kb 1024
                      mb (* 1024 kb)
                      gb (* 1024 mb)]
                  {:kb kb
                   :mb mb
                   :gb gb}))

(defn descriptor->value [descriptor units-table]
  {:pre [(map? descriptor)
         (not-empty descriptor)
         (set/subset? (set (keys descriptor))
                      (set (keys units-table)))
         (every? pos-int? (vals descriptor))]}
  (reduce (fn [acc [unit v]] (+ acc (* (unit units-table) v)))
          0 (seq descriptor)))

(defn make-output-stream [^File file]
  (ifarafontov.NoopFlushOutputStream. (FileOutputStream. file true)))

(defrecord FSW [^File file
                ^ifarafontov.NoopFlushOutputStream stream
                ^cognitect.transit.Writer writer])

(defn file-stream-writer [file-name transit-format]
  (let [log-file (io/file file-name)
        stream (make-output-stream log-file)
        writer (transit/writer stream transit-format)]
    (FSW. log-file stream writer)))

(defn rotate [^File file]
  (let [path (.toPath file)
        old-name (.getCanonicalPath file)]
    (Files/move path (.resolveSibling path
                                      (str (.getName file) "." (local-timestamp)))
                (into-array CopyOption []))
    (io/file old-name)))

(defn -main
  "I don't do a whole lot ... yet."
  [& args])


