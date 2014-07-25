(ns de.zalf.berest.web.rest.data
  (:require [clojure.string :as cs]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [ring.util.request :as req]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.web.rest.queries :as queries]
            [de.zalf.berest.web.rest.template :as temp]))

;; page translations

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:crops {:lang/de "Fruchtarten"
                       :lang/en "crops"}
           :soils {:lang/de "Böden"
                   :lang/en "soils"}
           :users {:lang/de "Liste aller Nutzer (nur ADMIN Rolle)"
                   :lang/en "List of all users (only admin role)"}
           :user {:lang/de "Nutzer-Daten"
                  :lang/en "user-data"}
           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

;; page layouts

(defn data-layout
  [request]
  (let [url-path (:uri request)
        {user-id :user/id
         user-roles :user/roles} (-> request :session :identity)]
    [:div.container
     (temp/standard-header url-path)

     (when (:admin user-roles)
       [:div [:a {:href (str url-path "users/")} (vocab :users)]])
     (when user-id
       [:div [:a {:href (str url-path "users/" user-id "/")} (str (vocab :user) " " user-id)]])
     [:div [:a {:href (str url-path "crops/")} (vocab :crops)]]
     [:div [:a {:href (str url-path "soils/")} (vocab :soils)]]]))

(defn get-data
  [request]
  (common/standard-get request (data-layout request)))

(defn get-data-edn
  [request]
  (let [full-url (req/request-url request)]
    {:crops  {:url         (str full-url "data/crops/")
              :description "GET all available crops from this url"}
     :soils  {:url         (str full-url "data/soils/")
              :description "GET all available soils from this url"}}))


