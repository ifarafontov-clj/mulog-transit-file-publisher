(ns ifarafontov.NoopFlushOutputStream
  (:gen-class
   :extends java.io.BufferedOutputStream
   :methods [[realFlush [] void]]
   :exposes-methods {flush superFlush})
  (:import
   [java.io BufferedOutputStream]))

;; The sole purpose of this class is to make flush() call a no-op
;; because Transit writer calls flush() after each write(), 
;; effectively disabling buffering. See 
;; https://github.com/cognitect/transit-java/blob/master/src/main/java/com/cognitect/transit/impl/WriterFactory.java </br>
;; getJsonInstance() method.


; no-op
(defn -flush [this])

(defn -realFlush [this]
  (.superFlush this))



