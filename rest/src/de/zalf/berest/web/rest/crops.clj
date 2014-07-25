(ns de.zalf.berest.web.rest.crops
  (:require [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.core.datomic :as db]
            [de.zalf.berest.core.data :as data]
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
            [clojure.edn :as edn]
            [clojure.string :as str]))


(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:crops {:lang/de "Feldfrüchte"
                   :lang/en "crops"}
           :description {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Feldfrüchte angezeigt."
                         :lang/en "Here will be displayed all crops
                  stored in the database."}
           #_:create #_{:lang/de "Neuen Betrieb erstellen:"
                    :lang/en "Create new farm:"}
           #_:create-button #_{:lang/de "Erstellen"
                           :lang/en "Create"}
           }
          [element (or lang common/*lang*)] "UNKNOWN element"))


(defn create-crops-layout [db]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :crop)]
     (common/create-form-element db e))

   #_[:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn get-crops-edn*
  [db full-url]
  (map #(select-keys % [:crop/id :crop/name :crop/symbol :url])
       (data/db->all-crops db full-url)))

(defn get-crops-edn
  [request]
  (let [full-url (req/request-url request)
        db (db/current-db)]
    (get-crops-edn* db full-url)))

(defn crops-layout
  [db request]
  (let [full-url (req/request-url request)
        url-path (:uri request)
        crops (data/db->all-crops db full-url)]
    [:div.container
     (temp/standard-header url-path)

     (temp/standard-get-layout*
       {:url-path url-path
        :title (vocab :crops)
        :description (vocab :description)}
       "text/html" [:ul
                    (for [{url :url
                           crop-id :crop/id
                           name :crop/name
                           symbol :crop/symbol} (sort-by :crop/name crops)]
                      [:li [:a {:href url} (str "(" crop-id ") " symbol " | " name)]])]

       "application/edn" [:code {:style "white-space:pre-wrap"}
                          (pr-str (get-crops-edn* db full-url))])]))

(defn get-crops
  [request]
  (let [db (db/current-db)]
    (common/standard-get request
                         (crops-layout db request))))
















