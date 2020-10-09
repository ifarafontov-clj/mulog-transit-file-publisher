(ns ifarafontov.transit-publisher
  (:require
   [clojure.java.io :as io]
   [cognitect.transit :as transit]
   [clojure.set :as set]
   [com.brunobonacci.mulog.buffer :as rb]
   [com.brunobonacci.mulog.utils :as ut]
   [ifarafontov.NoopFlushOutputStream]
   [com.brunobonacci.mulog :as mu])
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

(defn file-stream-writer-created [^File file transit-format transit-handlers]
  (let [stream (make-output-stream file)
        writer (transit/writer stream
                               transit-format
                               {:handlers (merge {Flake flake-write-handler}
                                                 transit-handlers)})]
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



(deftype TransitRollingFilePublisher [buffer
                                      fswc
                                      xf
                                      rotate-opts
                                      transit-format
                                      transit-handlers]
  com.brunobonacci.mulog.publisher.PPublisher
  (agent-buffer [_]
    buffer)


  (publish-delay [_]
    500)


  (publish [_ buffer]
    (let [{:keys [file
                  ^ifarafontov.NoopFlushOutputStream stream
                  writer
                  created-at]} @fswc
          items (into [] xf (rb/items buffer))]
      (doseq [item items]
        (transit/write writer item))
      (.realFlush stream)
      (when (rotate? file created-at (Instant/now) rotate-opts)
        (reset! fswc (file-stream-writer-created (rotate file)
                                                 transit-format
                                                 transit-handlers)))
      (rb/clear buffer)))


  java.io.Closeable
  (close [_]
    (let [^ifarafontov.NoopFlushOutputStream stream (:stream @fswc)]
      (.realFlush stream)
      (.close stream))))

(defn transit-rolling-file-publisher
  [{:keys [file-name rotate-age rotate-size transit-format transit-handlers transform]
    :or {file-name "./app.log.json"
         rotate-age nil
         rotate-size nil
         transit-format :json
         transit-handlers nil
         transform identity}}]

  {:pre [(-> (io/file file-name)
             (.getParentFile)
             ((fn [^File f]
                (and (.isDirectory f) (.canWrite f)))))]}

  (let [rotate-opts (mapv (fn [[desc table]]
                            (when desc (descriptor->value desc table)))
                          [[rotate-age time-units] [rotate-size size-units]])
        current-file (io/file file-name)
        log-file (if (and
                      (.exists current-file)
                      (rotate? current-file (creation-time current-file)
                               (Instant/now) rotate-opts))
                   (rotate current-file) current-file)]
    (TransitRollingFilePublisher.
     (rb/agent-buffer 10000)
     (atom (file-stream-writer-created log-file transit-format transit-handlers))
     (get-xf transform)
     rotate-opts
     transit-format
     transit-handlers)))

(defn -main
  [& args]

  
  (mu/start-publisher!
   {:type :custom
    :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
    :file-name "logz/app.log"
    })
  
  
  (let [e (Exception. "Boom!")]
    (time 
     (dotimes [_ 100000]
       (mu/log :start :key 123 :exc e :time (Instant/now))
       ))
    )
  
  
  
  
  
  

  
  )


