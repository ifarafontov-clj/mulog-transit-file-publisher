(ns ifarafontov.transit-publisher
  (:require
   [clojure.java.io :as io]
   [cognitect.transit :as transit]
   [clojure.set :as set]
   [com.brunobonacci.mulog.buffer :as rb]
   [ifarafontov.NoopFlushOutputStream]
   [com.brunobonacci.mulog :as mu]
   [clojure.string :as str])
  (:import
   [java.nio.file Files CopyOption]
   [java.time.format DateTimeFormatter]
   [java.time LocalDateTime Instant ZoneId Duration]
   [com.brunobonacci.mulog.core Flake]
   [java.io File FileOutputStream BufferedInputStream EOFException]))

(set! *warn-on-reflection* true)

(def formatter (DateTimeFormatter/ofPattern "YYYYMMdd_hhmmss"))
(defn local-timestamp [now]
  (.format (LocalDateTime/ofInstant now
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

(defn file-stream-writer-created [^File file
                                  transit-format transit-handlers
                                  created-at]
  (let [stream (make-output-stream file)
        writer (transit/writer stream
                               transit-format
                               {:handlers transit-handlers})]
    (FSWC. file stream writer created-at)))

(defn rotate [^File file now file-name]
  (let [path (.toPath file)]
    (Files/move path (.resolveSibling path
                                      (str file-name "." (local-timestamp now)))
                (into-array CopyOption []))
    (File. (.getParentFile file) (str (.toEpochMilli now) "_" file-name))))

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

(defn read-all-transit [{:keys [file-name transit-format transit-handlers]
                         :or {transit-format :json
                              transit-handlers nil}}]

  (with-open [^BufferedInputStream in (io/make-input-stream
                                       (io/file file-name) {})]
    (let [reader (transit/reader
                  in
                  transit-format
                  {:handlers (merge {"flake" flake-read-handler} transit-handlers)})]
      (loop [res []]
        (if-let [entry (try
                         (transit/read reader)
                         ;;Both :json and :msgpack throw EOF
                         (catch RuntimeException re
                           (if (instance? EOFException (.getCause re))
                             nil
                             (throw re))))]
          (recur (conj res entry))
          res)))))

(deftype TransitRollingFilePublisher [file-name
                                      buffer
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
          items (into [] xf (rb/items buffer))
          _ (println "!! " (count items))
          now (Instant/now)]
      (doseq [item items]
        (transit/write writer item))
      (.realFlush stream)
      (when (rotate? file created-at now rotate-opts)
        (.close stream)
        (reset! fswc (file-stream-writer-created (rotate file now file-name)
                                                 transit-format
                                                 transit-handlers
                                                 now)))
      (rb/clear buffer)))


  java.io.Closeable
  (close [_]
    (let [^ifarafontov.NoopFlushOutputStream stream (:stream @fswc)]
      (.realFlush stream)
      (.close stream))))

(defn parse-created-at [file-name names now]
  (let [try-parse-long (fn [s] (try
                                 (Long/parseLong s)
                                 (catch NumberFormatException _ nil)))
        suffix (str "_" file-name)
        time-and-name (fn [^String log]
                        (when (.endsWith log suffix)
                          (when-let [ms (try-parse-long
                                         (subs log 0 (.lastIndexOf log suffix)))]
                            [(Instant/ofEpochMilli ms) log])))
        logs (->> names
                  (map time-and-name)
                  (filterv (complement nil?)))]
    (when (> (count logs) 1)
      (throw (AssertionError. (str "Do not know which file to use: "
                                   (str/join "," (map second logs))))))
    (if (empty? logs)
      [now (str (.toEpochMilli now) suffix)]
      (first logs))))


(defn transit-rolling-file-publisher
  [{:keys [dir-name file-name rotate-age rotate-size transit-format transit-handlers transform]
    :or {dir-name "."
         file-name "app.log.json"
         rotate-age nil
         rotate-size nil
         transit-format :json
         transit-handlers nil
         transform identity}}]

  {:pre [(-> (io/file dir-name)
             ((fn [^File f]
                (and (.isDirectory f) (.canWrite f)))))]}

  (let [log-dir  (io/file dir-name)
        now (Instant/now)
        rotate-opts (mapv (fn [[desc table]]
                            (when desc (descriptor->value desc table)))
                          [[rotate-age time-units] [rotate-size size-units]])
        [created-at log-name] (parse-created-at file-name (.list log-dir) now)
        log-file (let [f (File. log-dir log-name)]
                   (if (and
                        (.exists f)
                        (rotate? f created-at now rotate-opts))
                     (rotate f now file-name)
                     f))
        transit-handlers (merge {Flake flake-write-handler}
                                transit-handlers)]
    (TransitRollingFilePublisher.
     file-name
     (rb/agent-buffer 10000)
     (atom (file-stream-writer-created log-file
                                       transit-format
                                       transit-handlers
                                       created-at))
     (get-xf transform)
     rotate-opts
     transit-format
     transit-handlers)))

(defn -main
  [& args]

  (mu/start-publisher!
   {:type :custom
    :fqn-function "ifarafontov.transit-publisher/transit-rolling-file-publisher"
    :dir-name "logz/"
    :rotate-size {:mb 10}
    :transit-format :msgpack})
           ; :transit-format :msgpack
           ; :rotate-size {:mb 10}



  (mu/start-publisher! {:type :console})

  (mu/log :start
          :key 1
          :e :ev
          :t (str (Instant/now)))



;; 256MB
  (future
    (let [e (Exception. "Boom!")]
      (dotimes [n 1000000]
        (Thread/sleep 1)
        (mu/log :start
                :key n
                :ex (str (* 10000000 1000000000))
                :t (str (Instant/now))))))
  (count
   (read-all-transit {:file-name "logz/1603808240474_app.log.json"}))
                   ; :transit-format :msgpack



  (.list
   (.getParentFile (io/file "logz/app.log")))

  (.getName (io/file "logz1/app.log")))






