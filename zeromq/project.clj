(defproject
  de.zalf.berest/berest-zeromq-service "0.0.1"

  :description "BEREST ZeroMQ service"
  :url ""
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [de.zalf.berest/berest-core "0.0.3"]

                 [tailrecursion/cljson "1.0.7"]

                 [com.datomic/datomic-pro "0.9.5173" :exclusions [joda-time]]

                 [org.zeromq/jeromq "0.3.4"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]

                 [simple-time "0.1.1"]
                 [clj-time "0.9.0"]

                 [clojure-csv "2.0.1"]
                 [org.clojure/core.match "0.2.0"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["resources"]

  :profiles {:dev {:dependencies []
                   :resource-paths []}}
  
  :main de.zalf.berest.web.zeromq.core
  )












