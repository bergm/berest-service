(defproject berest-service "0.0.1-SNAPSHOT"
  :description "BEREST pedestal based web service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]
                 [io.pedestal/pedestal.service "0.2.2"]
                 [io.pedestal/pedestal.service-tools "0.2.2"]
                 #_[ring/ring-core "1.2.0"] ;pedestal dependency

                 ;; Remove this line and uncomment the next line to
                 ;; use Tomcat instead of Jetty:
                 [io.pedestal/pedestal.jetty "0.2.2"]
                 ;; [io.pedestal/pedestal.tomcat "0.2.1"]

                 #_[org.infinispan/infinispan-client-hotrod "6.0.0.CR1"]
                 [com.datomic/datomic-pro "0.8.4270"]
                 #_[com.datomic/datomic-free "0.8.4218"]

                 [geheimtur "0.1.1"]

                 [clj-time "0.6.0"]
                 [clojure-csv "2.0.1"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [com.taoensso/timbre "2.6.3"]
                 [egamble/let-else "1.0.6"]
                 [com.cemerick/friend "0.2.0"]
                 [org.clojars.pallix/analemma "1.0.0"]
                 [org.clojure/core.match "0.2.0"]
                 [com.keminglabs/c2 "0.2.3"]
                 [hiccup "1.0.4"]
                 [formative "0.3.2"]
                 [com.velisco/clj-ftp "0.3.0"]
                 [instaparse "1.2.13"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.namespace "0.2.4"]

                 ]

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "berest-service.server/run-dev"]}
  :repl-options  {:init-ns user
                  :init (try
                          (use 'io.pedestal.service-tools.dev)
                          (require 'berest-service.service)
                          ;; Nasty trick to get around being unable to reference non-clojure.core symbols in :init
                          (eval '(init berest-service.service/service #'berest-service.service/routes))
                          (catch Throwable t
                            (println "ERROR: There was a problem loading io.pedestal.service-tools.dev")
                            (clojure.stacktrace/print-stack-trace t)
                            (println)))
                  :welcome (println "Welcome to pedestal-service! Run (tools-help) to see a list of useful functions.")}
  :main ^{:skip-aot true} berest-service.server)












