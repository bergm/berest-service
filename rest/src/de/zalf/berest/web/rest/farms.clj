(ns de.zalf.berest.web.rest.farms
  (:require [de.zalf.berest.core
             [core :as bc]
             [datomic :as db]
             [data :as data]
             [helper :as bh :refer [rcomp]]
             [queries :as queries]]
            [de.zalf.berest.web.rest
             [common :as common]
             [util :as util]
             [template :as temp]]
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
  (get-in {:farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :description {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Betriebe angezeigt."
                  :lang/en "Here will be displayed all farms
                  stored in the database."}
           :create {:lang/de "Neuen Betrieb erstellen:"
                    :lang/en "Create new farm:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

(defn get-farms-edn*
  [db user-id full-url]
  (map #(select-keys % [:farm/id :farm/name :url]) (data/db->a-users-farms db user-id full-url)))

(defn get-farms-edn
  [request]
  (let [full-url (req/request-url request)
        db (db/current-db)
        user-id (some-> request :session :identity :user/id)]
    (when user-id
      (get-farms-edn* db user-id full-url))))

(defn farms-layout
  [db request]
  (let [full-url (req/request-url request)
        url-path (:uri request)
        user-id (some-> request :session :identity :user/id)
        farms (data/db->a-users-farms db user-id full-url)]
    [:div.container
     (temp/standard-header url-path)

     [:hr]

     (temp/standard-get-layout*
       {:url url-path
        :title (vocab :farms)
        :description (vocab :description)}
       "text/html" [:ul
                    (for [{url :url
                           farm-id :farm/id
                           name :farm/name} (sort-by :farm/name farms)]
                      [:li [:a {:href url} (str "(" farm-id ") " name)]])]

       "application/edn" [:code {:style "white-space:pre-wrap"}
                          (pr-str (get-farms-edn* db full-url))])

     [:hr]

     (temp/standard-post-layout*
       {:url url-path
        :title (vocab :create)}

       [:div.container
        (for [e (queries/get-ui-entities db :rest.ui/groups :farm)]
          (common/create-form-element db e))

        [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])]))

(defn get-farms
  [request]
  (let [db (db/current-db)]
    (common/standard-get request (farms-layout db request))))















