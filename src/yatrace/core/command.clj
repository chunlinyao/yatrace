(ns yatrace.core.command
  (:require [clojure.string :as s]
            [yatrace.core.instrument :as instrument]
            [yatrace.core :as core]
            [yatrace.helper :as helper])
  (:use [clojure.pprint :only [ pprint]])
  (:import [java.lang.instrument Instrumentation]))

(defn- get-method-filter [method]
  (if (nil? method)
    (constantly true)
    #{method})
  )

(defn- default-handler [evt]
  (pprint (helper/to-tree (:ctx evt))))

(defn trace [class-method & {:keys [package handler background], :or {package ".*" handler default-handler background false}}]
  (let [lock (Object.)]
    (locking lock 
      (let [method-filter (get-method-filter (second (s/split class-method #"\.")))
            class-name (first (s/split class-method #"\."))
            classes (instrument/get-candidates core/instrumentation #{class-name} :package package)
            reset-task (doto  (Thread. #(locking lock (do (doseq [klass classes] (instrument/reset-class core/instrumentation [klass]))
                                                         )))
                         (.setDaemon true)
                         (.start)
                         )
            transform-task (future (doseq [klass classes] (instrument/probe-class core/instrumentation (instrument/class-trace-transformer method-filter (set classes)) [ klass])))
            ]
        @transform-task
        (core/reset-queue)
        (doseq [evt (repeatedly core/take-queue)]
          (do (println (format "%s event from %s@%H" (name (:type evt)) (class (:thread evt)) (.hashCode (:thread evt))))
              (handler evt)
              (when (instance? Throwable (get-in evt [:ctx :result-or-exception]))
                (.printStackTrace ^Throwable (get-in evt [:ctx :result-or-exception]) *out*))))))))
