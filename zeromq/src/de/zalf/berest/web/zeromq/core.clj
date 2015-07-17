(ns de.zalf.berest.web.zeromq.core
  (refer-clojure :exclude [send])
  (require [zeromq.zmq :as zmq]
           [zeromq.device :as zmqd]
           #_[tailrecursion.cljson :as cljson]
           [de.zalf.berest.core.data :as data]
           [de.zalf.berest.core.api :as api]
           [de.zalf.berest.core.datomic :as db]
           #_[datomic.api :as d]
           #_[simple-time.core :as time]
           #_[clj-time.core :as ctc]
           #_[clj-time.format :as ctf]
           #_[clj-time.coerce :as ctcoe]
           #_[clojure-csv.core :as csv]
           [de.zalf.berest.core.core :as bc]
           #_[clojure.tools.logging :as log]
           [clojure.pprint :as pp]
           [cheshire.core :as json]))

#_(cljson/extends-protocol
  cljson/EncodeTagged
  clojure.lang.PersistentTreeMap
  (-encode [o] (into ["m"] (map cljson/encode (apply concat o)))))


(defn calculate-recommendation-from-remote-data
  [db {:keys [crop-id weather-data fcs-mm pwps-mm isms-mm ka5s lambdas layer-sizes slope dc-assertions]}]
  (binding [de.zalf.berest.core.core/*layer-sizes* (or layer-sizes (repeat 20 10))]
    (let [sorted-weather-map (into (sorted-map)
                                   (for [[doy precip evap] weather-data]
                                     [doy {:weather-data/precipitation precip
                                           :weather-data/evaporation evap}]))

          ;_ (println "sorted-weather-map: ")
          ;_ (pp/pprint sorted-weather-map)

          crop-template (data/db->crop-by-id (db/current-db) crop-id)

          plot** {:plot/ka5-soil-types ka5s

                  :plot/field-capacities fcs-mm
                  :plot/fc-pwp-unit :soil-moisture.unit/mm

                  :plot/permanent-wilting-points pwps-mm
                  :plot/pwp-unit :soil-moisture.unit/mm

                  :plot.annual/abs-day-of-initial-soil-moisture-measurement 90
                  :plot.annual/initial-soil-moistures isms-mm
                  :plot.annual/initial-sm-unit :soil-moisture.unit/mm

                  :lambdas lambdas

                  :plot.annual/crop-instances [{:crop.instance/template crop-template
                                                :crop.instance/dc-assertions (for [[abs-day dc] dc-assertions]
                                                                               {:assertion/abs-assert-dc-day abs-day
                                                                                :assertion/assert-dc dc})}]

                  :fallow (data/db->crop-by-name db 0 :cultivation-type 0 :usage 0)

                  :plot.annual/technology {:donation/step-size 5,
                                           :donation/opt 20,
                                           :donation/max 30,
                                           :donation/min 5,
                                           :technology/type :technology.type/sprinkler,
                                           :technology/sprinkle-loss-factor 0.2,
                                           :technology/cycle-days 1}

                  #_:plot/slope #_{:slope/key 1
                               :slope/description "eben"
                               :slope/symbol "NFT 01" }

                  ;:slope slope

                  :plot.annual/donations []

                  :plot/damage-compaction-area 0.0
                  :plot/damage-compaction-depth 300
                  :plot/irrigation-area 1.0
                  :plot/crop-area 1.2
                  :plot/groundwaterlevel 300

                  ;:plot.annual/year 1994
                  }

          ;_ (println "plot**: ")
          ;_ (pp/pprint plot**)

          inputs (bc/base-input-seq plot**
                                    sorted-weather-map
                                    []
                                    (-> plot** :plot.annual/technology :technology/type))
          ;_ (println "inputs:")
          ;_ (pp/pprint inputs)
          ]
      (bc/calc-recommendation
        6
        (int slope)
        (:plot.annual/technology plot**)
        inputs
        (:plot.annual/initial-soil-moistures plot**)))))


(defonce context (zmq/context))

(defn -main
  []
  (with-open [clients (doto (zmq/socket context :router)
                        (zmq/bind "tcp://*:6666"))
              workers (doto (zmq/socket context :dealer)
                        (zmq/bind "inproc://workers"))]
    (dotimes [i 3]
      (-> (Thread. #(with-open [receiver (doto (zmq/socket context :rep)
                                           (zmq/connect "inproc://workers"))]
                     (println "starting worker thread " i)
                     (while true
                       (let [string (zmq/receive-str receiver)
                             ;_ (println "received request str: " string)
                             clj (json/parse-string string)
                             ;_ (println "received request parsed: " (pr-str clj))
                             data (into {} (map (fn [[k v]] [(keyword k) v]) (second clj)))
                             _ (println "data: " (pr-str data))
                             date (:date data)
                             rec (calculate-recommendation-from-remote-data (db/current-db) data)
                             rec* (assoc rec :date date)
                             rec-str (json/generate-string rec*)
                             _ (println "sending response json-str: " rec-str)
                             ]
                         #_(Thread/sleep 1)
                         (zmq/send-str receiver rec-str)))))
          .start))
    (zmqd/proxy context clients workers)))

(-main)
