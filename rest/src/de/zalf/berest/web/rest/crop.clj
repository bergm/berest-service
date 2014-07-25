(ns de.zalf.berest.web.rest.crop
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


(defn- db->crop
  [db id full-crop-url]
  (some->> (d/q '[:find ?crop-e
                  :in $ ?id
                  :where
                  [?crop-e :crop/id ?id]]
                db id)
           ffirst
           (d/entity db ,,,)
           (into {} ,,,)
           (#(assoc % :url full-crop-url) ,,,)))

(defn get-crop
  [id request]
  (let [db (db/current-db)
        full-url (req/request-url request)
        url-path (:uri request)
        crop (db->crop db id full-url)]
    (hp/html5
      (common/head (str "GET " url-path))
      (common/body
        nil #_(auth/get-identity request)

        (if crop
          [:div.container
           (temp/standard-header url-path :get)

           [:div.container
            (for [e (queries/get-ui-entities db :rest.ui/groups :crop)]
              (common/create-form-element db e {:value ((:rest.ui/describe-attribute e) crop)
                                                :disabled? true}))

            #_[:button.btn.btn-primary {:type :submit} (vocab :create-button)]]
           ]
          [:div.container.error
           [:p (str "Couldn't load crop with id: " id " from database.")]])))))


(defn get-crop-edn
  [id request]
  (let [db (db/current-db)]
    (db->crop db id (req/request-url request))))















