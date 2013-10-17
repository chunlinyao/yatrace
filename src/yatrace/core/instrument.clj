(ns yatrace.core.instrument
  (:import [java.lang.instrument Instrumentation ClassFileTransformer]))

(defn- decorate [& args]
  nil)

(defn class-trace-transformer [method-filter classes]
  (proxy [ClassFileTransformer] []
    (transform [loader name class-being-redefined
                protection-domain classfile-buffer]
      (if (classes class-being-redefined)
        (decorate classfile-buffer, name, (partial method-filter class-being-redefined))
        nil))))

(defn- transform-class [^Instrumentation inst classes]
  (.retransformClasses (into-array Class classes)))

(defn probe-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (-> inst
      (.addTransformer transformer true)
      (transform-class inst classes))
  )

(defn reset-class [^Instrumentation inst ^ClassFileTransformer transformer classes]
  (-> inst
      (.removeTransformer transformer)
      (transform-class inst classes)))
