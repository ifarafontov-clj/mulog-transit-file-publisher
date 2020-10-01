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

(defrecord FSWC [^File file
                 ^ifarafontov.NoopFlushOutputStream stream
                 ^cognitect.transit.Writer writer
                 ^Instant created-at])

(defn file-stream-writer-created [file transit-format]
  (let [stream (make-output-stream file)
        writer (transit/writer stream transit-format)]
    (FSWC. file stream writer (creation-time file))))

(defn rotate [^File file]
  (let [path (.toPath file)
        old-name (.getCanonicalPath file)]
    (Files/move path (.resolveSibling path
                                      (str (.getName file) "." (local-timestamp)))
                (into-array CopyOption []))
    (io/file old-name)))

(defn throwable-keys [m]
  (reduce (fn [acc [k v]]
            (if (instance? Throwable v) (conj acc k) acc))
          #{} m))

(defn convert-throwables [m]
  (let [ks (throwable-keys m)]
    (reduce (fn [acc k] (update-in acc [k] Throwable->map))
            m ks)))

(defn get-xf [transform]
  (comp
   (map second)
   (map transform)
   (filter (complement nil?))
   (map convert-throwables)))

(comment
  (deftype TransitRollingFilePublisher [atom-file-stream-writer-created buffer transform]
    com.brunobonacci.mulog.publisher.PPublisher
    (agent-buffer [_]
      buffer)


    (publish-delay [_]
      1000)


    (publish [_ buffer]


      (let [[file stream writer created-at] @atom-file-stream-writer-created])

    ;; items are pairs [offset <item>]


      (doseq [item (transform (map second (rb/items buffer)))]
        (.write filewriter ^String (str (ut/edn-str item) \newline)))
      (.flush filewriter)
      (rb/clear buffer))


    java.io.Closeable
    (close [_]
      (.flush filewriter)
      (.close filewriter))))

(defn transit-rolling-file-publisher1
  [{:keys [file rotate-age rotate-size transit-format transform]
    :or {file "./app.log.json"
         rotate-age nil
         rotate-size nil
         transit-format :json
         transform identity}}]

  {:pre [(-> (io/file file)
             (.getParentFile)
             (#(and (.isDirectory %) (.canWrite %))))]}

  (let [rotate-opts (mapv (fn [[desc table]]
                            (when desc (descriptor->value desc table)))
                          [[rotate-age time-units] [rotate-size size-units]])
        current-file (io/file file)
        log-file (if (rotate? current-file (creation-time current-file)
                              (Instant/now) rotate-opts)
                   (rotate current-file) current-file)
        fswc (atom (file-stream-writer-created file transit-format))]))

(defn -main
  "I don't do a whole lot ... yet."
  [& args]
  (file-stream-writer-created "new.json" :json))


