(ns yatrace.core
  (:require [clojure.string :as s]
            )
  (:use [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:import [java.lang.instrument Instrumentation ClassFileTransformer]
           )
  )

(declare ^Instrumentation instrumentation)
(declare server)

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


(defn repl-start [port-file]
  (defonce server (start-server))
  (spit port-file (:port server)))

(defn repl-stop []
  (yatrace.Agent/reset)
  (reset-signal-handler! "INT")
  (shutdown-agents)
  (stop-server server))

(defn agentmain [^String args ^Instrumentation instrumentation]
  (def instrumentation instrumentation)
  (repl-start args))
