(defproject
  de.zalf.berest/berest-castra-service "0.1.0"

  :description "BEREST CASTRA service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.7.0"]

                 [de.zalf.berest/berest-core "0.1.0"]

                 [compojure "1.4.0"]
                 [hoplon/castra "3.0.0-alpha3"]

                 [com.datomic/datomic-pro "0.9.5344" :exclusions [joda-time]]
                 [com.amazonaws/aws-java-sdk-dynamodb "1.9.39" :exclusions [joda-time]]

                 [clojurewerkz/quartzite "2.0.0"]

                 [org.zeromq/jeromq "0.3.5"]
                 [org.zeromq/cljzmq "0.1.4" :exclusions [org.zeromq/jzmq]]

                 [ring "1.4.0"]
                 [ring-server "0.4.0"]
                 [jumblerg/ring.middleware.cors "1.0.1"]
                 #_[ring-cors "0.1.7"]
                 [fogus/ring-edn "0.3.0"]

                 [simple-time "0.2.1"]
                 [clj-time "0.11.0"]

                 [clojure-csv "2.0.1"]
                 [org.clojure/core.match "0.2.2"]]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["resources"
                   #_"../../berest-hoplon-website"]

  :profiles {:dev {:dependencies []
                   :resource-paths ["../../berest-hoplon-website"]}}

  :ring {:handler de.zalf.berest.web.castra.handler/castra-service
         ;:init de.zalf.berest.core.import.dwd-data/start-import-scheduler
         ;:destroy de.zalf.berest.core.import.dwd-data/stop-import-scheduler
         }

  :main de.zalf.berest.web.castra.run-dev-server
  )












