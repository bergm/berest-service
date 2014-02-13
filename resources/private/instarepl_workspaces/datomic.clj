(ns resources.private.instarepl-workspaces.datomic
  (:require clojure.set
            [crypto.password.scrypt :as pwd]
            [clojure.string :as cstr]
            [clojure.pprint :as pp]
            [clj-time.core :as ctc]
            [clj-time.coerce :as ctcoe]
            [clojure.java.io :as cjio]
            [clojure.tools.logging :as log]
            [datomic.api :as d :refer [q db]]
            [berest-service.berest.util :as bu]
            [berest-service.berest.datomic :as db]
            [berest-service.berest.helper :as bh :refer [|->]]))


#_(db/store-credentials (db/datomic-connection) "michael" "#zALf!" "Michael Berg" [:admin :guest :consultant :farmer])



(comment "install a few users into system"

  (let [db (db/current-db)]
    (db/store-credentials (db/datomic-connection) "michael" "#zALf!" "Michael Berg" [:admin :guest :consultant :farmer])
    (db/store-credentials (db/datomic-connection) "gunnar" "gUnNaR" "Gunnar Issbrücker" [:consultant])
    (db/store-credentials (db/datomic-connection) "guest" "guest" "John Doe" [:guest])
    (db/store-credentials (db/datomic-connection) "farmer" "fArMeR" "John Farmer" [:farmer]))


  )





(comment "insta repl code"
  (d/q '[:find ?e
         :in $
         :where
         [?e :user/id ?user-id]]
       (current-db) "michael")

  (->> (d/q '[:find ?e
              :in $
              :where
              [?e :user/id ?user-id]]
            (current-db) "michael")
       ffirst
       (d/entity (current-db) ,,,)
       d/touch)

  (->> (d/q '[:find ?e
            :in $
            :where
            [?e :db/ident :user/id]]
          (current-db))
     ffirst
     (d/entity (current-db))
     d/touch)


  (->> (d/q '[:find ?e
            :in $
            :where
            [?e :user/id]]
          (db/current-db))
     ffirst
     (d/entity (db/current-db))
     d/touch)


  )



