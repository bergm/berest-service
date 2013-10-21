;; make in memory database
(use '[datomic.api :only [q db] :as d])
(require '[clj-time.core :as ctc]
         '[weberest.datomic :as bd]
         '[weberest.util :as bu]
				 '[weberest.test-data :as btd])

bd/datomic-base-uri

;get connection
(def con (bd/datomic-connection "berest"))

;get db value
(def ddb (bd/current-db "berest"))

(bd/apply-schema-to-db con)

#_(alter-var-root (var *db*) (constantly (db datomic-connection)))

;create db with schema
(bd/create-db "berest")

(btd/add-sugarbeet con)


;delete db
(bd/delete-db "berest") 






(q '[:find ?e
		 :in $
     :where
     [?e :db/ident :kv/dc]
     ]
   ddb)

;; find all reads corresponding to match-b
(q '[:find ?read ?doc
     :where
     [?read :db/doc ?doc]
     [?read :read/match ?match]
     [?match :db/doc "match-b"]]
   (db datomic-connection))
  
  

