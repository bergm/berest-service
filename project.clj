(defproject berest-service "0.0.1-SNAPSHOT"
  :description "BEREST pedestal based web service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.5.1"]

                 #_[io.pedestal/pedestal.service "0.2.2"]
                 #_[io.pedestal/pedestal.service-tools "0.2.2"]
                 #_[ring/ring-core "1.2.0"] ;pedestal dependency
                 ;; Remove this line and uncomment the next line to
                 ;; use Tomcat instead of Jetty:
                 #_[io.pedestal/pedestal.jetty "0.2.2"]
                 ;; [io.pedestal/pedestal.tomcat "0.2.1"]

                 #_[org.infinispan/infinispan-client-hotrod "6.0.0.CR1"]
                 [com.datomic/datomic-pro "0.9.4556"]
                 #_[com.datomic/datomic-free "0.8.4218"]

                 #_[com.cemerick/friend "0.2.0"]
                 #_[geheimtur "0.1.1"]
                 [buddy "0.1.0-beta4"]
                 [crypto-password "0.1.1"]

                 [ring "1.2.1"]
                 [fogus/ring-edn "0.2.0"]

                 #_[compojure "1.1.6"]
                 [bidi "1.10.2"]
                 [liberator "0.11.0"]

                 [hiccup "1.0.4"]

                 [clj-time "0.6.0"]
                 [clojure-csv "2.0.1"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [com.taoensso/timbre "2.6.3"]
                 [egamble/let-else "1.0.6"]
                 [org.clojars.pallix/analemma "1.0.0"]
                 [org.clojure/core.match "0.2.0"]
                 [com.keminglabs/c2 "0.2.3"]
                 [formative "0.3.2"]
                 [com.velisco/clj-ftp "0.3.0"]
                 [instaparse "1.2.13"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [clojurewerkz/propertied "1.1.0"]

                 ]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  :source-paths ["src" "../berest/src"]

  :jelastic {:apihoster "app.jelastic.dogato.eu"
             ;:email "your@mail.com"
             ;:password "XXXXXXXX"
             :environment "berest-humanespaces"
             ; Optionals
             ; :context "mycontext"
             ; Custom filename can be set for example to match ring uberwar output
             ; :custom-filename (fn [proj]
             ;                    (str (:name proj) "-" (:version proj) "-STANDALONE"))
             }
  :immutant {:context-path "/"}

  :ring {:handler berest-service.handler/rest-service}

  :min-lein-version "2.0.0"
  :resource-paths ["config", "resources"]
  :aliases {"run-dev" ["trampoline" "run" "-m" "berest-service.server/run-dev"]}

  ;:main ^{:skip-aot true} berest-service.server
  )












