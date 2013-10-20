(ns yatrace.core.instrument
  (:require [yatrace.core.decorate :as decorate])
  (:import [java.lang.instrument Instrumentation ClassFileTransformer]))

(defn class-trace-transformer [method-filter classes]
  (proxy [ClassFileTransformer] []
    (transform [loader name class-being-redefined
                protection-domain classfile-buffer]
      (if (classes class-being-redefined)
        (decorate/decorate classfile-buffer, name, (partial method-filter class-being-redefined))
        nil))))

(defn- transform-class [^Instrumentation inst classes]
  (.retransformClasses inst (into-array Class classes)))

(defn probe-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (doto inst
    (.addTransformer transformer true)
    (transform-class classes))
  )

(defn reset-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (doto inst
      (.removeTransformer transformer)
      (transform-class classes)))
