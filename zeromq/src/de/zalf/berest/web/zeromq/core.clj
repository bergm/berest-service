(ns de.zalf.berest.web.zeromq.core
  (refer-clojure :exclude [send])
  (require [zeromq.zmq :as zmq]
           [zeromq.device :as zmqd]
           [tailrecursion.cljson :as cljson]
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
           [clojure.pprint :as pp]))

(cljson/extends-protocol
  cljson/EncodeTagged
  clojure.lang.PersistentTreeMap
  (-encode [o] (into ["m"] (map cljson/encode (apply concat o)))))


(defn calculate-from-remote-data*
  [db run-id crop-id {:keys [weather-data fcs-mm pwps-mm isms-mm ka5s lambdas layer-sizes slope dc-assertions]}]
  (binding [de.zalf.berest.core.core/*layer-sizes* (or layer-sizes (repeat 20 10))]
    (let [sorted-climate-data (into (sorted-map)
                                    (map (fn [[year years-data]]
                                           [year (into (sorted-map)
                                                       (for [[doy precip evap] years-data]
                                                         [doy {:weather-data/precipitation precip
                                                               :weather-data/evaporation evap}]))])
                                         weather-data))

          ;_ (println "sorted-climate-data: ")
          ;_ (pp/pprint sorted-climate-data)

          crop-template (data/db->crop-by-id (db/current-db) crop-id)

          ;plot (bc/deep-db->plot db #uuid "539ee6fc-762f-40ae-8c7d-7827ea61f709" 1994 #_"53a3f382-dae7-4fff-9d68-b3c7782fcae7" #_2014)

          plot** {:plot/ka5-soil-types ka5s

                  :plot/field-capacities fcs-mm
                  :plot/fc-pwp-unit :soil-moisture.unit/mm

                  :plot/permanent-wilting-points pwps-mm
                  :plot/pwp-unit :soil-moisture.unit/mm

                  :plot.annual/abs-day-of-initial-soil-moisture-measurement 90
                  :plot.annual/initial-soil-moistures isms-mm
                  :plot.annual/initial-sm-unit :soil-moisture.unit/mm

                  :lambdas lambdas

                  ;:plot.annual/crop-instances (:plot.annual/crop-instances plot)

                  :plot.annual/crop-instances [{:crop.instance/template crop-template
                                                ;:crop.instance/name "dummy name"
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

                  :plot/slope {:slope/key 1
                               :slope/description "eben"
                               :slope/symbol "NFT 01" }

                  :slope slope

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

          res (map (fn [[year sorted-weather-map]]
                     #_(println "calculating year: " year)
                     [year (let [inputs (bc/create-input-seq plot**
                                                             sorted-weather-map
                                                             365
                                                             []
                                                             (-> plot** :plot.annual/technology :technology/type))]
                             #_(println "inputs:")
                             #_(pp/pprint inputs)
                             (bc/calculate-sum-donations-by-auto-donations
                               inputs (:plot.annual/initial-soil-moistures plot**)
                               (int slope) #_(-> plot** :plot/slope :slope/key)
                               (:plot.annual/technology plot**)
                               5))])
                   sorted-climate-data)
          ;_ (println "res: " res)
          _ (println "calculated run-id: " run-id)
          ]
      {:run-id run-id
       :result (into {} res)}
      #_(mapv second res))))

(defn calculate-from-remote-data
  [run-id crop-id data]
  (let [db (db/current-db)]
    (calculate-from-remote-data* db run-id crop-id data)))









(defonce context (zmq/context))

(defn -main
  []
  (with-open [clients (doto (zmq/socket context :router)
                        (zmq/bind "tcp://*:5555"))
              workers (doto (zmq/socket context :dealer)
                        (zmq/bind "inproc://workers"))]
    (dotimes [i 3]
      (-> (Thread. #(with-open [receiver (doto (zmq/socket context :rep)
                                           (zmq/connect "inproc://workers"))]
                     (println "starting worker thread " i)
                     (while true
                       (let [string (zmq/receive-str receiver)
                             clj (cljson/cljson->clj string)]
                         (println "received request: " (pr-str clj))
                         (Thread/sleep 1)
                         (zmq/send-str receiver (str i " success"))))))
          .start))
    (zmqd/proxy context clients workers)))

(-main)
