(ns yatrace.core
  (:require [yatrace.core.instrument :as inst]
            [clojure.java.io :as io])
  (:use [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:import [java.lang.instrument Instrumentation ClassFileTransformer]
           [java.util.concurrent BlockingQueue LinkedBlockingQueue]
           )
  )

(declare ^Instrumentation instrumentation queue)
(declare server repl-stop)

(defmacro reset-signal-handler! [signal]
  (if (try (Class/forName "sun.misc.Signal")
          (catch Throwable e))
    `(try
       (sun.misc.Signal/handle
         (sun.misc.Signal. ~signal)
         sun.misc.SignalHandler/SIG_DFL)
       ; unrecognized signal - CONT on Windows, for instance
       (catch Throwable e#))
    `(println "Unable to set signal handlers."))
  )

(defn- start-watchdog
  [port-file]
  (let [trigger (io/file port-file)]
    (future (loop []
              (Thread/sleep 1000)
              (if (.exists trigger)
                (recur)
                (repl-stop))))))

(defn repl-start [port-file]
  (defonce server (start-server))
  (spit port-file (:port server))
  (start-watchdog port-file))

(defn repl-stop []
  (yatrace.Agent/reset)
  (reset-signal-handler! "INT")
  (swap! queue (constantly nil))
  (shutdown-agents)
  (stop-server server))

(defn agentmain [^String args ^Instrumentation instrumentation]
  (def instrumentation instrumentation)
  (repl-start args))

(defonce thread-context (atom {}))
(defonce queue (atom (LinkedBlockingQueue.)))
(defn take-queue []
  (.take ^BlockingQueue @queue))
(defn reset-queue []
  (swap! queue (constantly (LinkedBlockingQueue.))))

(defn- get-thread-context [^Thread t]
  (if-let [stack (@thread-context t)]
    stack
    []
    ))
(defn- push-invoke-context [thread ctx]
  (swap! thread-context update-in [thread] conj ctx)
  )

(defn- pop-invoke-context [thread]
  (let [ctx (-> thread
                get-thread-context
                peek)
        _ (swap! thread-context update-in [( Thread/currentThread)] pop)]
    ctx)
  )
(defn method-enter [thread stack-trace class-name method-name descriptor this-obj args]
  (let [ctx  {
              :class-name  class-name
              :method-name method-name
              :desc        descriptor
              :this-obj    this-obj
              :args        (seq args)
              :start       (System/currentTimeMillis)}]
    (push-invoke-context thread ctx)
    (.offer ^BlockingQueue @queue {:type :enter :thread thread :stack-trace stack-trace :ctx ctx})
    )
  )

(defn method-exit [thread stack-trace result-or-exception]
  (let [end-time (System/currentTimeMillis)
        ctx (pop-invoke-context thread)
        ctx' (merge ctx {
                         :result-or-exception result-or-exception
                         :end                 end-time
                         :duration            (- end-time (:start ctx))
                         })]
    (.offer ^BlockingQueue @queue {:type :exit :thread thread :stack-trace stack-trace :ctx ctx'})
    )
  )
