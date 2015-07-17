(defproject de.zalf.berest/berest-rest-service "0.0.1-SNAPSHOT"
  :description "BEREST REST service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 #_[berest-core/berest-core "0.0.7"]

                 #_[org.infinispan/infinispan-client-hotrod "6.0.0.CR1"]
                 [com.datomic/datomic-pro "0.9.5173" :exclusions [joda-time]]
                 #_[com.datomic/datomic-free "0.8.4218"]

                 [buddy "0.1.0-beta4"]
                 [crypto-password "0.1.1"]

                 [ring "1.2.1"]
                 [ring-server "0.3.1"]
                 [fogus/ring-edn "0.2.0"]

                 #_[compojure "1.1.6"]
                 [bidi "1.10.2"]
                 [liberator "0.11.0"]

                 [hiccup "1.0.4"]

                 #_[prismatic/plumbing "0.2.2"]

                 [simple-time "0.1.1"]
                 [clj-time "0.6.0"]

                 [clojure-csv "2.0.1"]
                 [org.clojure/algo.generic "0.1.1"]
                 [org.clojure/math.numeric-tower "0.0.2"]
                 [com.taoensso/timbre "3.1.6"]
                 #_[egamble/let-else "1.0.6"]
                 [org.clojars.pallix/analemma "1.0.0"]
                 [org.clojure/core.match "0.2.0"]
                 [com.keminglabs/c2 "0.2.3"]
                 [formative "0.3.2"]
                 [com.velisco/clj-ftp "0.3.0"]
                 [instaparse "1.3.2"]
                 [org.clojure/tools.logging "0.2.6"]
                 [org.clojure/tools.namespace "0.2.4"]
                 [clojurewerkz/propertied "1.1.0"]

                 ]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  :profiles {:dev {:dependencies [[org.immutant/immutant-web "1.1.1"]
                                  [org.immutant/immutant-jobs "1.1.1"]]}}

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["config" "resources"]

  :immutant {:context-path "/"}

  :ring {:handler berest.web.rest.handler/rest-service}

  ;:main ^{:skip-aot true} berest.core
  )












