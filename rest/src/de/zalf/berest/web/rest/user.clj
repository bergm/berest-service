(ns de.zalf.berest.web.rest.user
  (:require [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.helper :as bh :refer [rcomp]]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.web.rest.queries :as queries]
            [de.zalf.berest.web.rest.util :as util]
            [de.zalf.berest.web.rest.template :as temp]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [ring.util.request :as req]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]))

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:user {:lang/de "Nutzer"
                   :lang/en "user"}
           :description {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Nutzer angezeigt."
                         :lang/en "Here will be displayed all users
                  stored in the database."}
           :create {:lang/de "Nutzerdaten aktualisieren:"
                    :lang/en "Update user data:"}
           :create-button {:lang/de "Aktualisieren"
                           :lang/en "Update"}
           :farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :weather-stations {:lang/de "Wetter Stationen"
                              :lang/en "weather stations"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))


(defn user-layout [db url]
  [:div.container
   (temp/standard-header url)

   #_(temp/standard-get-layout {:url url
                              :get-title (vocab :users)
                              :description (vocab :show)
                              :get-id-fn :user/id
                              :get-name-fn :user/name
                              :entities (queries/get-entities db :user/id)
                              :sub-entity-path "user/"})

   #_(temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-user-layout db)})])


(defn- db->user
  [db id full-user-url]
  (some->> (d/q '[:find ?user-e
                  :in $ ?id
                  :where
                  [?user-e :user/id ?id]]
                db id)
           ffirst
           (d/entity db ,,,)
           (into {} ,,,)
           (#(assoc % :url full-user-url) ,,,)))

(defn get-user
  [id request]
  (let [db (db/current-db)
        full-url (req/request-url request)
        url-path (:uri request)
        user (db->user db id full-url)]
    (hp/html5
      (common/head (str "GET " url-path))
      (common/body
        nil #_(auth/get-identity request)

        [:div.container
         (temp/standard-header url-path :get)

         [:hr]

         [:div [:a {:href (str url-path "farms/")} (vocab :farms)]]
         [:div [:a {:href (str url-path "weather-stations/")} (vocab :weather-stations)]]

         ]

        [:hr]

        (if user
          [:div.container
           (for [e (queries/get-ui-entities db :rest.ui/groups :user)]
             (common/create-form-element db e {:value ((:rest.ui/describe-attribute e) user)}))

           #_[:button.btn.btn-primary {:type :submit} (vocab :create-button)]]
          [:div.container.error
           [:p (str "Couldn't load user with id: " id " from database.")]])))))

(defn get-user-edn
  [id request]
  (let [db (db/current-db)]
    (db->user db id (req/request-url request))))

(defn create-user
  [request]

  )

(defn update-user
  [request]

  )

