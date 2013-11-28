(ns berest-service.berest.datomic
  (:require clojure.set

            [clojure.string :as cstr]
            [clojure.pprint :as pp]
            [clj-time.core :as ctc]
            [clojure.java.io :as cjio]
            [datomic.api :as d
             :refer [q db]]
            [berest-service.berest
             [util :as bu]
             [helper :as bh
              :refer [|->]]]))

(defn datomic-connection-string [base-uri db-id]
  (str base-uri db-id))

(def free-local-base-uri "datomic:free://localhost:4334/")
(def free-local-connection-string (partial datomic-connection-string free-local-base-uri))

(def free-azure-base-uri "datomic:free://humane-spaces.cloudapp.net:4334/")
(def free-azure-connection-string (partial datomic-connection-string free-azure-base-uri))

(def infinispan-local-datomic-base-uri "datomic:inf://localhost:11222/")
(def infinispan-local-connection-string (partial datomic-connection-string infinispan-local-datomic-base-uri))

(def dynamodb-base-uri "datomic:ddb://eu-west-1/berest-datomic-store/%s?aws_access_key_id=AKIAIKDIFN2XPB7ZE3SA&aws_secret_key=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")
(defn dynamodb-connection-string [db-id]
  (format dynamodb-base-uri db-id))

(def datomic-connection-string dynamodb-connection-string)

(defn datomic-connection [db-id]
  (->> db-id
       datomic-connection-string
       d/connect))

(defn current-db [db-id]
  (some->> (datomic-connection db-id)
           d/db))

(def berest-datomic-schemas ["private/db/berest-meta-schema.dtm"
                             "private/db/berest-schema.dtm"])

(defn apply-schemas-to-db [datomic-connection & schemas]
  (->> (or schemas berest-datomic-schemas)
       (map (|-> cjio/resource slurp read-string) ,,,)
       (map (partial d/transact datomic-connection) ,,,)
			 dorun))

(defn create-db [db-id & [uri]]
  (let [uri* (datomic-connection-string db-id)]
    (when (d/create-database uri*)
			(apply-schemas-to-db (d/connect uri*))
			db-id)))

(defn delete-db [db-id]
  (->> db-id
       datomic-connection-string
       d/delete-database))

(defn new-entity-ids [] (repeatedly #(d/tempid :db.part/user)))
(defn new-entity-id [] (first (new-entity-ids)))
(defn temp-entity-id [value] (d/tempid :db.part/user value))

(defn create-entities
  ([key value kvs]
    (map (fn [id [k v]] {:db/id id, key k, value v}) (new-entity-ids) (apply array-map kvs)))
  ([ks-to-vss]
    (map #(assoc (zipmap (keys ks-to-vss) %) :db/id (new-entity-id))
         (apply map vector (vals ks-to-vss)))))

(defn create-inline-entities
  ([key value kvs]
   (map (fn [[k v]] {key k, value v}) (apply array-map kvs)))
  ([ks-to-vss]
   (map #(zipmap (keys ks-to-vss) %) (apply map vector (vals ks-to-vss)))))

(defn get-entity-ids [entities] (map :db/id entities))
(defn get-entity-id [entity] (:db/id entity))

(defn get-entities [db entity-ids]
  (map (partial d/entity db) entity-ids)
  #_(map (partial datomic.db/get-entity db) entity-ids))
(defn get-entity [db entity-id]
  (first (get-entities db [entity-id])))

(defn create-map-from-entity-ids
  [key value entity-ids]
  (->> entity-ids
    get-entities
    (map (juxt key value) ,,,)
    (into (sorted-map) ,,,)))

(defn create-map-from-entities
  [key value entities]
  (->> entities
    (map (juxt key value) ,,,)
    (into (sorted-map) ,,,)))

(defn query-for-db-id [db relation value]
  (->> (q '[:find ?db-id
            :in $ ?r ?v
            :where
            [?db-id ?r ?v]]
         db, relation, value)
    (map first)))

(defn unique-query-for-db-id [db relation value]
  (first (query-for-db-id db relation value)))


(defn create-dc-assertion*
  "Create a dc assertion for given year 'in-year' to define that at abs-dc-day
  the dc-state was 'dc'. Optionally a at-abs-day can be given when the
  dc state had been told the system, else abs-dc-day will be assumed."
  [in-year abs-dc-day dc & [at-abs-day]]
  {:db/id (new-entity-id)
   :assertion/at-abs-day (or at-abs-day abs-dc-day)
   :assertion/assert-dc dc
   :assertion/abs-assert-dc-day abs-dc-day})

(defn create-dc-assertion
  "Create a dc assertion for given year 'in-year' to define that at '[day month]'
  the dc-state was 'dc'. Optionally a '[at-day at-month]' can be given when the
  dc state had been told the system, else '[day month]' will be assumed"
  [in-year [day month] dc & [[at-day at-month :as at]]]
  (let [abs-dc-day (bu/date-to-doy day month in-year)
        at-abs-day (if (not-any? nil? (or at [nil]))
                       (bu/date-to-doy at-day at-month in-year)
                       abs-dc-day)]
       (create-dc-assertion* in-year abs-dc-day dc at-abs-day)))

(defn create-dc-assertions
  "create multiple assertions at one"
  [in-year assertions]
  (map #(apply create-dc-assertion in-year %) assertions))


(defn create-irrigation-donation*
	"Create datomic map for an irrigation donation given an start-abs-day and optionally
  an end-abs-day (else this will be the same as start-abs-day) and the irrigation-donation in [mm]"
	[start-abs-day donation-mm & [end-abs-day]]
  {:db/id (new-entity-id)
   :irrigation/abs-start-day start-abs-day
   :irrigation/abs-end-day (or end-abs-day start-abs-day)
   :irrigation/amount donation-mm})

(defn create-irrigation-donation
  "create datomic map for an irrigation donation"
  [in-year [start-day start-month] donation-mm & [[end-day end-month :as end-date]]]
  (let [start-abs-day (bu/date-to-doy start-day start-month in-year)
        end-abs-day (if (not-any? nil? (or end-date [nil]))
                       (bu/date-to-doy end-day end-month in-year)
                       start-abs-day)]
       (create-irrigation-donation* start-abs-day donation-mm end-abs-day)))

(defn create-irrigation-donations
  "Create multiple irrigation donation datomic maps at once"
  [in-year donations]
  (map #(apply create-irrigation-donation in-year %) donations))



