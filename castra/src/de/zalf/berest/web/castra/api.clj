(ns de.zalf.berest.web.castra.api
  (:require [tailrecursion.castra :refer [defrpc ex error *session*]]
            [tailrecursion.cljson :as cljson]
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
            [de.zalf.berest.core.core :as bc]
            [clojure.tools.logging :as log]))

(cljson/extends-protocol cljson/EncodeTagged
                         clojure.lang.PersistentTreeMap
                         (-encode [o] (into ["m"] (map cljson/encode (apply concat o)))))

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

   ;will only be available if user has role #admin
   :users nil

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
                          :users (when ((:user/roles cred) :user.role/admin)
                                   (data/db->all-users db))
                          :weather-stations (data/db->a-users-weather-stations db user-id)
                          :user-credentials cred)))

(defn- static-stem-cell-state
  [db {user-id :user/id :as cred}]
  (assoc static-state-template :stts (data/db->all-stts db)
                               :slopes (data/db->all-slopes db)
                               :substrate-groups (data/db->all-substrate-groups db)
                               :ka5-soil-types (data/db->all-ka5-soil-types db)
                               :crop->dcs (data/db->all-crop->dcs db)
                               :all-weather-stations (data/db->all-weather-stations db)
                               :minimal-all-crops (data/db->min-all-crops db user-id)))

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
      (static-stem-cell-state db cred))))


#_(defrpc get-minimal-all-crops
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
                        data* (map #(select-keys % [:db/id
                                                    :weather-data/date
                                                    :weather-data/global-radiation
                                                    :weather-data/average-temperature
                                                    :weather-data/precipitation
                                                    :weather-data/evaporation
                                                    :weather-data/prognosis-date]) data)
                        data** (filter (comp not :weather-data/prognosis-date) data*)]
                    [year data**]))
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

(defrpc create-new-user
  [new-user-id new-user-pwd & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (db/register-user (db/connection) new-user-id new-user-pwd new-user-id [:guest])
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new farm!")))))))

(defrpc set-new-password
  [pwd-user-id new-user-pwd & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (db/update-password (db/connection) pwd-user-id new-user-pwd)
        (catch Exception e
          (throw (ex error "Couldn't update password!")))))))

(defrpc update-user-roles
  [update-user-id new-roles & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (db/update-user-roles (db/connection) update-user-id new-roles)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't update user to new roles!")))))))

(defrpc add-user-weather-stations
  [update-user-id new-weather-station-ids & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (db/add-user-weather-stations (db/connection) update-user-id new-weather-station-ids)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't add new user weather-stations!")))))))

(defrpc remove-user-weather-stations
  [update-user-id weather-station-ids & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (db/remove-user-weather-stations (db/connection) update-user-id weather-station-ids)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't remove user weather-stations!")))))))

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

(defrpc create-new-local-user-weather-station
        [temp-weather-station-name & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/create-new-local-user-weather-station (db/connection) (:user/id cred) temp-weather-station-name)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new weather-station!")))))))



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

(defrpc create-new-plot-annual
  [plot-id new-year copy-data? copy-year & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-plot-annual (db/connection) (:user/id cred) plot-id new-year copy-data? copy-year)
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new plot annual!")))))))

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

(defrpc create-new-farm-contact
        [farm-id & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/create-new-farm-contact (db/connection) (:user/id cred) farm-id)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new farm contact!")))))))

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
                                                                       (:pwp :fc :sm :ism) (double value)
                                                                       :ka5 value))
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new fc, pwp or ka5 layer!")))))))

(defrpc create-new-donation
  [annual-plot-entity-id abs-start-day abs-end-day amount & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))]
    (when cred
      (try
        (data/create-new-donation (db/connection) (:user/id cred) annual-plot-entity-id
                                  (int abs-start-day) (int abs-end-day) (double amount))
        (stem-cell-state (db/current-db) cred)
        (catch Exception e
          (throw (ex error "Couldn't create new donation!")))))))

(defrpc create-new-soil-moisture
        [annual-plot-entity-id & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/create-new-soil-moisture (db/connection) (:user/id cred) annual-plot-entity-id)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new soil-moistures entity!")))))))

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
          (throw (ex error "Couldn't create new dc assertion!")))))))

(defrpc create-new-weather-data
        [id-attr id date tavg globrad evap precip prog-date & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              #_(println "(create-new-weather-data " id-attr " " id " " date " " tavg " " globrad " " evap
                       " " precip " " prog-date ")")
              (data/create-new-weather-data (db/connection) (:user/id cred)
                                            id-attr id date tavg globrad evap precip prog-date)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new weather-data!")))))))

(defrpc create-new-com-con
        [contact-entity-id com-con-id com-con-desc com-con-type & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/create-new-com-con (db/connection) (:user/id cred) contact-entity-id
                                       com-con-id com-con-desc com-con-type)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new communication connection!")))))))

(defrpc create-new-crop
        [temp-name & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/create-new-crop (db/connection) (:user/id cred) temp-name)
              (static-stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error "Couldn't create new crop!")))))))

(defrpc copy-crop
        [crop-id temp-name & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))]
          (when cred
            (try
              (data/copy-crop (db/connection) (:user/id cred) crop-id temp-name)
              (static-stem-cell-state (db/current-db) cred)
              (catch Exception e
                (throw (ex error (str "Couldn't copy crop with id: " crop-id "!"))))))))

(defrpc delete-crop
        [crop-db-id & [user-id pwd]]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))

              tx-data [[:db.fn/retractEntity crop-db-id]]]
          (when cred
            (try
              (d/transact (db/connection) tx-data)
              (static-stem-cell-state (db/current-db) cred)
              (catch Exception e
                (log/info "Couldn't delete crop entity! tx-data:\n" tx-data)
                (throw (ex error (str "Couldn't delete crop!"))))))))


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
          (log/info "Couldn't update entity! tx-data:\n" tx-data)
          (throw (ex error (str "Couldn't update entity!"))))))))

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
          (log/info "Couldn't retract entity! tx-data:\n" tx-data)
          (throw (ex error (str "Couldn't delete entity!"))))))))

(defrpc retract-db-value
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

              tx-data [[:db/retract entity-id (d/entid db attr) value*]]]
          (when cred
            (try
              (d/transact (db/connection) tx-data)
              (stem-cell-state (db/current-db) cred)
              (catch Exception e
                (log/info "Couldn't retract information on entity! tx-data:\n" tx-data)
                (throw (ex error (str "Couldn't delete information on entity!"))))))))



(defrpc update-crop-db-entity
        [crop-id entity-id attr value & {:keys [user-id pwd value-type]
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

              tx-data [[:db/add entity-id (d/entid db attr) value*]]
              ;_ (println "tx-data: " (pr-str tx-data))
              ]
          (when cred
            (try
              (d/transact (db/connection) tx-data)
              (first (data/db->full-selected-crops (db/current-db) [crop-id]))
              (catch Exception e
                (log/info "Couldn't update entity! tx-data:\n" tx-data)
                (throw (ex error (str "Couldn't update entity!"))))))))

(defrpc delete-crop-db-entity
        [crop-id entity-id?s & [user-id pwd]]
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
              (first (data/db->full-selected-crops (db/current-db) [crop-id]))
              (catch Exception e
                (log/info "Couldn't retract entity! tx-data:\n" tx-data)
                (throw (ex error (str "Couldn't retract entity! tx-data:\n" tx-data))))))))

(defrpc create-new-crop-kv-pair
        [crop-id crop-attr key-attr key-value value-attr value-value & {:keys [user-id pwd value-type]
                                                                        :or {value-type :identity}}]
        {:rpc/pre [(nil? user-id)
                   (rules/logged-in?)]}
        (let [db (db/current-db)

              cred (if user-id
                     (db/credentials* db user-id pwd)
                     (:user @*session*))

              value-value* (case value-type
                             :double (double value-value)
                             :int (int value-value)
                             value-value)
              ]
          (when cred
            (try
              (data/create-new-crop-kv-pair (db/connection) (:user/id cred) crop-id
                                            crop-attr key-attr key-value value-attr value-value*)
              (first (data/db->full-selected-crops (db/current-db) [crop-id]))
              (catch Exception e
                (throw (ex error (str "Couldn't create new key-value pair for crop with id: " crop-id "!"))))))))

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
                    {:donation/abs-start-day (util/date-to-doy day month year)
                     :donation/abs-end-day (util/date-to-doy day month year)
                     :donation/amount amount})
        {:keys [inputs soil-moistures]} (f db plot-id until-julian-day 6 year donations [])]
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
  [plot-id calculation-doy year & [user-id pwd]]
  {:rpc/pre [(nil? user-id)
             (rules/logged-in?)]}
  (let [db (db/current-db)

        cred (if user-id
               (db/credentials* db user-id pwd)
               (:user @*session*))

        prognosis-days 6

        donations (data/db->donations db plot-id year)
        ;_ (println "donations: " (pr-str donations))

        {:keys [inputs
                soil-moistures]
         :as res} (api/calculate-plot-from-db db plot-id calculation-doy prognosis-days year donations [])

        measured-soil-moistures (drop-last prognosis-days soil-moistures)
        ;prognosis-soil-moistures (take-last prognosis-days soil-moistures)

        ;_ (println "res: " (pr-str res))

        {slope :plot/slope
         annuals :plot/annuals
         :as plot} (data/db->plot db plot-id)
        annual-for-year (first (filter #(= year (:plot.annual/year %)) annuals))
        tech (:plot.annual/technology annual-for-year)

        recommendation (bc/calc-recommendation prognosis-days
                                               (:slope/key slope)
                                               tech
                                               (take-last prognosis-days inputs)
                                               (:soil-moistures (last measured-soil-moistures)))
        recommendation* (merge recommendation (bc/recommendation-states (:state recommendation)))
        ]
    (when cred
      {:recommendation recommendation*
       :soil-moistures soil-moistures
       :inputs (map #(select-keys % [:dc :abs-day :precipitation :evaporation
                                     :donation :profit-per-dt :avg-additional-yield-per-mm
                                     :qu-target :extraction-depth-cm :cover-degree :transpiration-factor
                                     :crop-id])
                    inputs)
       :crops (reduce (fn [m {:keys [crop]}]
                        (assoc m (:crop/id crop) crop))
                      {} inputs)})))

















