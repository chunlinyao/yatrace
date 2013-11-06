(ns yatrace.core.instrument
  (:require [yatrace.core.decorate :as decorate]
            [clojure.string :as s])
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
  ([inst class-filter & {package-pattern :package :or {package-pattern ".*"}}]
     (->> (get-candidates inst)
          (filter #(and (class-filter (last (s/split (.getName ^Class %) #"\.")))
                        (re-find (re-pattern package-pattern) (.getName ( .getPackage ^Class %)))))))
  )

(defn class-trace-transformer [method-filter class-filter]
  (proxy [ClassFileTransformer] []
    (transform [loader name class-being-redefined
                protection-domain classfile-buffer]
      (if (class-filter (last (s/split (.getName ^Class class-being-redefined) #"\.")))
        (decorate/decorate classfile-buffer name method-filter)
        nil))))

(defn- transform-class [^Instrumentation inst classes]
  (doseq [klass classes]
    (.retransformClasses inst (into-array Class [klass]))))

(defn probe-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (doto inst
    (.addTransformer transformer true)
    (transform-class classes)))

(defn reset-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (doto inst
    (.removeTransformer transformer)
    (transform-class classes)))
