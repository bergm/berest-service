(ns berest-service.rest.user
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
            [geheimtur.util.auth :as auth]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:users {:lang/de "Nutzer"
                   :lang/en "users"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Nutzer angezeigt."
                  :lang/en "Here will be displayed all users
                  stored in the database."}
           :create {:lang/de "Neuen Nutzer erstellen:"
                    :lang/en "Create new user:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

(defn create-user-layout [db]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :user)]
     (common/create-form-element db e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn users-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :users)
                              :description (vocab :show)
                              :get-id-fn :user/id
                              :get-name-fn :user/name
                              :entities (queries/get-entities db :user/id)
                              :sub-entity-path ["users"]})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-user-layout db)})])


(defn get-users
  [{:keys [url-for params] :as request}]
  (let [db (db/current-db)]
    (common/standard-get ::get-users
                         (partial users-layout db)
                         request)))


(defn user-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :users)
                              :description (vocab :show)
                              :get-id-fn :user/id
                              :get-name-fn :user/name
                              :entities (queries/get-entities db :user/id)
                              :sub-entity-path "user/"})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-user-layout db)})])

(defn get-user
  [{:keys [url-for params] :as request}]
  (let [db (db/current-db)]
    (common/standard-get ::get-user
                         (partial user-layout db)
                         request)))

