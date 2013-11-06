(ns yatrace.core.command
  (:require [clojure.string :as s]
            [yatrace.core.instrument :as instrument]
            [yatrace.core :as core]
            [yatrace.helper :as helper])
  (:use [clojure.pprint :only [ pprint]])
  (:import [java.lang.instrument Instrumentation]))

(defn- make-method-filter
  [class-method]
  (cond (string? class-method)
        (let [method  (second (s/split class-method #"\."))]
          (if (nil? method)
            (constantly true)
            (fn [cn mn]
              (#{method} mn))))
        :else
        (let [[_ method-filter] class-method]
          method-filter)))

(defn- make-class-filter
  [class-method]
  (cond
   (string? class-method)
   #{(first (s/split class-method #"\."))}
   :else
   (let [[class-filter _] class-method]
     class-filter)))

(defn- default-handler [evt]
  (pprint (helper/to-tree (:ctx evt))))

(defn trace [class-method & {:keys [package handler background], :or {package ".*" handler default-handler background false}}]
  (let [lock (Object.)]
    (locking lock 
      (let [method-filter (make-method-filter class-method)
            class-filter (make-class-filter class-method)
            classes (instrument/get-candidates core/instrumentation class-filter  :package package)
            transformer (instrument/class-trace-transformer method-filter class-filter)
            reset-task (doto
                           (Thread. #(locking lock
                                       (instrument/reset-class core/instrumentation transformer (instrument/get-candidates core/instrumentation class-filter :package package))))
                         (.setDaemon true)
                         (.start))
            transform-task (future
                             (instrument/probe-class core/instrumentation transformer classes))]
        @transform-task
        (core/reset-queue)
        (doseq [evt (repeatedly core/take-queue)]
          (do (println (format "%s event from %s@%H" (name (:type evt)) (class (:thread evt)) (.hashCode (:thread evt))))
              (handler evt)
              (when (instance? Throwable (get-in evt [:ctx :result-or-exception]))
                (.printStackTrace ^Throwable (get-in evt [:ctx :result-or-exception]) *out*))))))))
