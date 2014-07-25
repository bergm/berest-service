(defproject de.zalf.berest/berest-castra-service "0.0.1-SNAPSHOT"
  :description "BEREST CASTRA service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [de.zalf.berest/berest-core "0.0.1-SNAPSHOT"]

                 [tailrecursion/castra "2.1.1"]

                 #_[org.infinispan/infinispan-client-hotrod "6.0.0.CR1"]
                 [com.datomic/datomic-pro "0.9.4766.11"]
                 #_[com.datomic/datomic-free "0.8.4218"]

                 #_[buddy "0.1.0-beta4"]
                 #_[crypto-password "0.1.1"]

                 [ring "1.2.1"]
                 [ring-server "0.3.1"]
                 [fogus/ring-edn "0.2.0"]

                 #_[clojurewerkz/quartzite "1.2.0"]
                 [org.immutant/immutant-jobs "1.1.3"]

                 #_[compojure "1.1.6"]
                 #_[bidi "1.10.2"]
                 #_[liberator "0.11.0"]

                 #_[hiccup "1.0.4"]

                 #_[prismatic/plumbing "0.2.2"]

                 [simple-time "0.1.1"]
                 [clj-time "0.6.0"]

                 [clojure-csv "2.0.1"]
                 #_[org.clojure/algo.generic "0.1.1"]
                 #_[org.clojure/math.numeric-tower "0.0.2"]
                 #_[com.taoensso/timbre "3.1.6"]
                 #_[egamble/let-else "1.0.6"]
                 [org.clojars.pallix/analemma "1.0.0"]
                 [org.clojure/core.match "0.2.0"]
                 #_[com.keminglabs/c2 "0.2.3"]
                 #_[formative "0.3.2"]
                 #_[com.velisco/clj-ftp "0.3.0"]
                 #_[instaparse "1.3.2"]
                 #_[org.clojure/tools.logging "0.2.6"]
                 #_[org.clojure/tools.namespace "0.2.4"]
                 #_[clojurewerkz/propertied "1.1.0"]
                 ]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  #_:profiles #_{:dev {:dependencies [[org.immutant/immutant-web "1.1.1"]
                                  [org.immutant/immutant-jobs "1.1.1"]]}}

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["resources"]

  #_:immutant #_{:context-path "/"}

  :ring {:handler de.zalf.berest.web.castra.handler/castra-service}

  ;:main ^{:skip-aot true} berest.core
  )












