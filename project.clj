(defproject yatrace "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [org.clojure/tools.nrepl "0.2.3"]
                 [reply "0.2.1" :exclusions [com.cemerick/drawbridge]]
                 ]
  :manifest {"Premain-Class" "yatrace.Agent"
             "Agent-Class" "yatrace.Agent"}
  :java-source-paths [ "srcj"]
  :global-vars {*warn-on-reflection* true}
  :repl-options {:init-ns yatrace.core}
  :main yatrace.Client)
