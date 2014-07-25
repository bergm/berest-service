(ns de.zalf.berest.web.rest.soil
  (:require [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.helper :as bh :refer [rcomp]]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.web.rest.queries :as queries]
            [de.zalf.berest.web.rest.util :as util]
            [de.zalf.berest.web.rest.template :as temp]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]))


(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Betriebe angezeigt."
                  :lang/en "Here will be displayed all farms
                  stored in the database."}
           :create {:lang/de "Neuen Betrieb erstellen:"
                    :lang/en "Create new farm:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))


(defn create-farms-layout [db]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :farm)]
     (common/create-form-element db e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn farms-layout [db url]
  [:div.container
   (temp/standard-header url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :farms)
                              :description (vocab :show)
                              :get-id-fn :farm/id
                              :get-name-fn :farm/name
                              :entities (db/query-entities db :farm/id)
                              :sub-entity-path ["farms"]})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-farms-layout db)})])

(defn get-crops-edn
  [user-id request]
  (let [db (db/current-db)]
    (->> (d/q '[:find ?farm-e
                :in $ ?user-id
                :where
                [?user-e :user/id ?user-id]
                [?user-e :user/farms ?farm-e]]
              db user-id)
         (map (rcomp first (partial d/entity db)),,,)
         (map #(select-keys % [:farm/id :farm/name]),,,))))

(defn get-crops
  [request]
  (let [db (db/current-db)]
    (common/standard-get (partial farms-layout db)
                         request)))


(defn create-farm
  [request]
  "post to create a new farm")

(defn get-farm
  [id request]
  (str "Farm no: " id " and full request: " request))

(defn update-farm
  [id request]
  (str "put to farm id: " id " and full request: " request))















