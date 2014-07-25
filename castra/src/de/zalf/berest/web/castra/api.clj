(ns de.zalf.berest.web.castra.api
  (:require [tailrecursion.castra :refer [defrpc ex error *session*]]
            [de.zalf.berest.web.castra.rules :as rules]
            [de.zalf.berest.core.data :as data]
            [de.zalf.berest.core.util :as util]
            [de.zalf.berest.core.api :as api]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.climate.climate :as climate]
            [datomic.api :as d]
            [simple-time.core :as time]
            [clj-time.core :as ctc]
            [clj-time.format :as ctf]
            [clj-time.coerce :as ctcoe]
            [clojure-csv.core :as csv]
            [de.zalf.berest.core.core :as bc]))


;;; utility ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn call      [f & args]  (apply f args))
(defn apply-all [fns coll]  (mapv #(%1 %2) (cycle fns) coll))
(defn every-kv? [fn-map m]  (->> m (merge-with call fn-map) vals (every? identity)))
(defn map-kv    [kfn vfn m] (into (empty m) (map (fn [[k v]] [(kfn k) (vfn v)]) m)))
(defn map-k     [kfn m]     (map-kv kfn identity m))
(defn map-v     [vfn m]     (map-kv identity vfn m))

;;; internal ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

#_(defn new-message [db-val from conv text]
  {:from from, :conv conv, :text text})

#_(defn add-message [db-val from conv text]
  (let [cons* #(cons %2 (or %1 '()))]
    (update-in db-val [:messages conv] cons* (new-message db-val from conv text))))

(defn get-farms
  [db user-id]
  (map #(select-keys % [:farm/id :farm/name]) (data/db->a-users-farms db user-id)))

(defn get-plots
  [db farm-id]
  (map #(select-keys % [:plot/id :plot/name]) (data/db->a-farms-plots db farm-id)))



(def state-template
  {:language :lang/de

   :farms nil

   :weather-stations {}

   :full-selected-weather-stations {}

   :technology nil #_{:technology/cycle-days 1
                :technology/outlet-height 200
                :technology/sprinkle-loss-factor 0.4
                :technology/type :technology.type/drip ;:technology.type/sprinkler
                :donation/min 1
                :donation/max 30
                :donation/opt 20
                :donation/step-size 5}

   #_:plot #_{:plot/stt 6212
          :plot/slope 1
          :plot/field-capacities []
          :plot/fc-pwp-unit :soil-moisture.unit/volP
          :plot/permanent-wilting-points []
          :plot/ka5-soil-types []
          :plot/groundwaterlevel 300}

   :user-credentials nil})

(def static-state-template
  {:stts nil
   :slopes nil
   :substrate-groups nil
   :ka5-soil-types nil
   :crop->dcs nil})


;;; public ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- stem-cell-state
  [db {user-id :user/id :as cred}]
  (let [farms-with-plots
        (into {} (map (fn [{farm-id :farm/id
                            :as farm}]
                        [farm-id (assoc farm :plots (into {} (map (juxt :plot/id identity)
                                                                  (data/db->a-farms-plots db farm-id))))])
                      (data/db->a-users-farms db user-id)))]
    (assoc state-template :farms farms-with-plots
                          :weather-stations (data/db->a-users-weather-stations db user-id)
                          :user-credentials cred)))

(defn- static-stem-cell-state
  [db]
  (assoc static-state-template :stts (data/db->all-stts db)
                               :slopes (data/db->all-slopes db)
                               :substrate-groups (data/db->all-substrate-groups db)
                               :ka5-soil-types (data/db->all-ka5-soil-types db)
                               :crop->dcs (data/db->all-crop->dcs db)))

(defrpc get-berest-state
  [& [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (stem-cell-state db cred))))

(defrpc get-static-state
  "returns static state which usually won't change once it's on the client"
  [& [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (static-stem-cell-state db))))


(defrpc get-minimal-all-crops
  "returns the minimal version of all crops, a list of
  [{:crop/id :id
    :crop/name :name
    :crop/symbol :symbol}]
    currently"
  [& [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (map #(select-keys % [:crop/id :crop/name :crop/symbol])
           (data/db->min-all-crops db)))))




(defrpc get-state-with-full-selected-crops
  [selected-crop-ids & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred  (if user-id
                (db/credentials* db user-id pwd)
                (:user @*session*))

        crops (data/db->full-selected-crops db selected-crop-ids)]
    (when cred
      (assoc (stem-cell-state db cred)
        :full-selected-crops (into {} (map (fn [c] [(:crop/id c) c]) crops))))))


(defrpc get-weather-station-data
  [weather-station-id years & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (->> years
           (map (fn [year]
                  (let [data (:data (climate/weather-station-data db year weather-station-id))
                        data* (map #(select-keys % [:weather-data/date
                                                    :weather-data/global-radiation
                                                    :weather-data/average-temperature
                                                    :weather-data/precipitation
                                                    :weather-data/evaporation
                                                    :weather-data/prognosis-data?]) data)]
                    [year data*]))
             ,,,)
           (into {} ,,,)
           (#(assoc {} :weather-station-id weather-station-id
                       :data %) ,,,)))))

(defrpc get-crop-data
  [crop-id & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (first (data/db->full-selected-crops db [crop-id])))))


(defrpc create-new-farm
  [temp-farm-name & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-farm (db/connection) (:user/id cred) temp-farm-name)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new farm!")))))))

(defrpc create-new-plot
  [farm-id & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-plot (db/connection) (:user/id cred) farm-id)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new plot!")))))))

(defrpc create-new-farm-address
  [farm-id & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-farm-address (db/connection) (:user/id cred) farm-id)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new farm address!")))))))

(defrpc create-new-soil-data-layer
  [id-attr id depth type value & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-soil-data-layer (db/connection) (:user/id cred)
                                         id-attr id (int depth) type (case type
                                                                       [:pwp :fc :sm] (double value)
                                                                       :ka5 value))
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new fc, pwp or ka5 layer!")))))))

(defrpc create-new-donation
  [annual-plot-entity-id abs-day amount & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-donation (db/connection) (:user/id cred) annual-plot-entity-id (int abs-day) (double amount))
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new donation!")))))))

(defrpc create-new-crop-instance
  [annual-plot-entity-id crop-template-id & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-crop-instance (db/connection) (:user/id cred) annual-plot-entity-id crop-template-id)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new crop instance!")))))))

(defrpc create-new-dc-assertion
  [crop-instance-entity-id abs-dc-day dc #_at-abs-day & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-dc-assertion (db/connection) (:user/id cred) crop-instance-entity-id
                                      (int abs-dc-day) (int dc) #_(int at-abs-day))
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new crop intance!")))))))

(defrpc update-db-entity
  [entity-id attr value & {:keys [user-id pwd value-type]
                           :or {value-type :identity}}]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))

        value* (case value-type
                 :double (double value)
                 :int (int value)
                 value)

        tx-data [[:db/add entity-id (d/entid db attr) value*]]]
    (when cred
      (try
        (d/transact (db/connection) tx-data)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error (str "Couldn't update entity! tx-data:\n" tx-data))))))))

(defrpc delete-db-entity
  [entity-id?s & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))

        entity-ids (if (sequential? entity-id?s) entity-id?s [entity-id?s])

        tx-data (for [e-id entity-ids]
                  [:db.fn/retractEntity e-id])]
    (when cred
      (try
        (d/transact (db/connection) tx-data)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error (str "Couldn't retract entity! tx-data:\n" tx-data))))))))

(defrpc set-substrate-group-fcs-and-pwps
  [plot-id substrate-group-key & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/set-substrate-group-fcs-and-pwps db (db/connection) (:user/id cred) plot-id substrate-group-key)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't set/copy substrate group's fcs/pws!")))))))


#_(defrpc update-weather-station
  [weather-station-id name lat lng & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))

        (d/q '[:find ?ws-e #_?year
               :in $ ?ws-id
               :where
               [?-e :user/id ?user-id]
               [?user-e :user/weather-stations ?ws-e]]
             db weather-station-id)

        ]
    (when cred
      (d/transact (db/connection)
                  [(when name [:db/add [:weather-station/id weather-station-id] :weather-station/name name])
                   (when lat [:db/add [:weather-station/id weather-station-id] :weather-station/g name])
                   ]))))


(defrpc login
  [user-id pwd]
  {:rpc/pre [(rules/login! user-id pwd)]}
  (get-berest-state))

(defrpc logout
  []
  {:rpc/pre [(rules/logout!)]}
  nil)

(defn calc-or-sim-csv
  [f plot-id until-date donations]
  (let [db (db/current-db)
        ud (ctcoe/from-date until-date)
        until-julian-day (.getDayOfYear ud)
        year (ctc/year ud)
        donations (for [{:keys [day month amount]} donations]
                    {:donation/abs-day (util/date-to-doy day month year)
                     :donation/amount amount})
        {:keys [inputs soil-moistures]} (f db plot-id until-julian-day year donations [])]
    (->> soil-moistures
         (api/create-csv-output inputs ,,,)
         (#(csv/write-csv % :delimiter \tab) ,,,))))

(defrpc simulate-csv
  [plot-id until-date donations]
  {:rpc/pre [(rules/logged-in?)]}
  (calc-or-sim-csv api/simulate-plot-from-db plot-id until-date donations))

(defrpc calculate-csv
  [plot-id until-date donations]
  {:rpc/pre [(rules/logged-in?)]}
  (calc-or-sim-csv api/calculate-plot-from-db plot-id until-date donations))


(defrpc calculate-from-db
  [plot-id until-abs-day year & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))

        donations (data/db->donations db plot-id year)

        {:keys [inputs inputs-7
                soil-moistures soil-moistures-7
                prognosis]} (api/calculate-plot-from-db db plot-id until-abs-day year donations [])

        {slope :plot/slope
         annuals :plot/annuals
         :as plot} (data/db->plot db plot-id)
        annual-for-year (first (filter #(= year (:plot.annual/year %)) annuals))
        tech (:plot.annual/technology annual-for-year)

        recommendation (bc/calc-recommendation 5
                                               (:slope/key slope)
                                               (:plot.annual/technology annual-for-year)
                                               (take-last 5 inputs)
                                               (:soil-moistures (last soil-moistures-7)))
        recommendation* (merge recommendation (bc/recommendation-states (:state recommendation)))
        ]
    (when cred
      {:recommendation recommendation*
       :soil-moistures soil-moistures
       :inputs (map #(select-keys % [:abs-day :precipitation :evaporation :donation :qu-target])
                    inputs)})))

















