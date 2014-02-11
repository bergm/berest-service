(ns berest-service.rest.farm
  (:require [berest-service.berest.core :as bc]
            [berest-service.berest.datomic :as db]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.util :as util]
            [berest-service.rest.template :as temp]
            #_[berest-service.service :as bs]
            [datomic.api :as d]
            #_[io.pedestal.service.http.route :as route]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]
            [geheimtur.util.auth :as gua]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )

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


(defn create-farms-layout [db ]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :farm)]
     (common/create-form-element e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn farms-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :farms)
                              :description (vocab :show)
                              :get-id-fn :farm/id
                              :get-name-fn :farm/name
                              :entities (queries/get-entities db :farm/id)
                              :sub-entity-path "farm/"})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-farms-layout db)})])

(defn get-farms
  [{:keys [url-for params] :as request}]
  (let [db (db/current-db)]
    (common/standard-get ::get-farms
                         (partial farms-layout db)
                         request)))


(defn create-new-farm [req]
  (rur/response "post to create a new farm"))

(defn get-farm [req]
  (rur/response (str "Farm no: " (get-in req [:path-params :farm-id])
                     " and full req: " req)))

(defn update-farm [req]
  (rur/response (str "put to farm id: " (get-in req [:path-params :farm-id]))))















