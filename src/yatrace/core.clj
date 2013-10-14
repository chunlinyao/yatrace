(ns yatrace.core
  (:require [clojure.string :as s]
            [clojure.java.io :as io]
            [clojure.tools.nrepl :as repl]
            [clojure.tools.nrepl.ack :as ack]
            [reply.main :as reply])
  (:use [clojure.tools.nrepl.server :only (start-server stop-server)])
  (:import [java.net URL URLClassLoader]
           [java.io File]
           [java.lang.instrument Instrumentation ClassFileTransformer]
           [clojure.lang Reflector]
           )
  )

(declare ^Instrumentation instrumentation)
(declare server)

(defn repl-start [port-file]
  (defonce server (start-server))
  (spit port-file (:port server)))

(defn repl-stop []
  (stop-server server)
  (shutdown-agents))

(defn agentmain [^String args ^Instrumentation instrumentation]
  (def instrumentation instrumentation)
  (repl-start args))

(defn ^String classname [c]
  (cond (instance? Class c)
        (.getName ^Class c)
        (string? c) c
        :else
        (classname (type c))))

(defn class-as-resource [cn]
  (let [cn (classname cn)]
    (io/resource (str (s/replace cn "." "/") ".class"))))

(defn ^File agent-jar []
  (let [^URL f (class-as-resource yatrace.Main)]
    (if (= "file" (.getProtocol f))
      (some (fn [^File f] (when (re-find #"yatrace-.*.jar" (.getName f)) f)) (file-seq (io/file "target")))
      (io/file (URL. (first (s/split (.getFile f) #"!")))))))

(defonce ^URLClassLoader tools-jar-loader
  (URLClassLoader. (into-array URL [(.toURL (io/file (System/getProperty "java.home")
                                                     "../lib/tools.jar"))])))
(defn vm-class []
  (.loadClass tools-jar-loader "com.sun.tools.attach.VirtualMachine"))
(defn vm [pid]
  (let [vm-class (vm-class)]
    (.invoke (.getMethod vm-class "attach"
                         (into-array Class [String])) nil (object-array [pid]))))

(defn list-vms []
  (let [vm-class (vm-class)
        vms (-> vm-class (.getMethod "list" (make-array Class 0)) (.invoke nil (make-array Object 0)))]
    (doseq [m vms]
      (println (.id m) (.displayName m)))
    ))

(defn repl-client [port-file]
  (if-let [port (slurp port-file)]
    (reply/launch {:attach port})
    (println "Can not connect to target vm")))

(defn attach-agent [pid]
  (let [vm (vm pid)
        agent (.getAbsolutePath (agent-jar))
        port-file (.getAbsolutePath (doto ( File/createTempFile "yatrace" ".port")
                                      (.deleteOnExit)
                                      (.createNewFile)))]
    (Reflector/invokeInstanceMember "loadAgent" vm (object-array [agent (str agent "\n" port-file)]))
    (Reflector/invokeNoArgInstanceMember vm "detach")
    (repl-client port-file)))

(defn javamain
  [[pid & argv]]
  (if pid
    (attach-agent pid)
    (list-vms))
  (shutdown-agents))

