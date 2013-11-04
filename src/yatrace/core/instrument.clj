(ns yatrace.core.instrument
  (:require [yatrace.core.decorate :as decorate])
  (:import [java.lang.instrument Instrumentation ClassFileTransformer]))


(defn- interface? [^Class k]
  (.isInterface k))

(defn- belong-yatrace? [^Class k]
  (-> (.getName k)
      (.startsWith "yatrace.")))
(defn- from-boot-class-loader? [^Class k]
  (nil? (.getClassLoader k)))

(defn get-candidates
  ([^Instrumentation inst]
     (->>  (.getAllLoadedClasses inst)
           (remove (some-fn interface? belong-yatrace? from-boot-class-loader?))))
  ([inst class-name & {package-pattern :package :or {package-pattern ".*"}}]
     (->> (get-candidates inst)
          (filter #(and (class-name (.getSimpleName ^Class %))
                        (re-find (re-pattern package-pattern) (.getName ( .getPackage ^Class %)))))))
  )

(defn class-trace-transformer [method-filter classes]
  (proxy [ClassFileTransformer] []
    (transform [loader name class-being-redefined
                protection-domain classfile-buffer]
      (if (classes class-being-redefined)
        (decorate/decorate classfile-buffer name method-filter)
        nil))))

(defn- transform-class [^Instrumentation inst classes]
  (.retransformClasses inst (into-array Class classes)))

(defn probe-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (doto inst
    (.addTransformer transformer true)
    (transform-class classes)
    (.removeTransformer transformer))
  )

(defn reset-class [^Instrumentation inst classes]
  (doto inst
      (transform-class classes)))
