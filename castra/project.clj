(defproject de.zalf.berest/berest-castra-service "0.0.1-SNAPSHOT"
  :description "BEREST CASTRA service"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[org.clojure/clojure "1.6.0"]

                 [de.zalf.berest/berest-core "0.0.1-SNAPSHOT"]

                 [tailrecursion/castra "2.1.1"]

                 [com.datomic/datomic-pro "0.9.4766.11"]

                 [clojurewerkz/quartzite "1.3.0"]

                 [ring "1.2.1"]
                 [ring-server "0.3.1"]
                 [fogus/ring-edn "0.2.0"]

                 [simple-time "0.1.1"]
                 [clj-time "0.6.0"]

                 [clojure-csv "2.0.1"]
                 [org.clojure/core.match "0.2.0"]
                 ]
  :repositories {"my.datomic.com" {:url "https://my.datomic.com/repo"
                                   :username "michael.berg@zalf.de"
                                   :password "dfe713b3-62f0-469d-8ac9-07d6b02b0175"}}

  :min-lein-version "2.0.0"

  :source-paths ["src"]
  :resource-paths ["resources"]

  :ring {:handler de.zalf.berest.web.castra.handler/castra-service
         :init de.zalf.berest.core.import.dwd-data/start-import-scheduler
         :destroy de.zalf.berest.core.import.dwd-data/stop-import-scheduler}

  ;:main ^{:skip-aot true} berest.core
  )












