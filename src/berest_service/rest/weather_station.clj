(ns berest-service.rest.weather-station
  (:require [berest-service.berest.core :as bc]
            [berest-service.rest.common :as rc]
            [berest-service.berest.datomic :as bd]
            #_[berest-service.service :as bs]
            [datomic.api :as d]
            [io.pedestal.service.http.route :as route]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]))

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:wstations {:lang/de "Wetterstationen"
                       :lang/en "weather stations"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Wetterstationen angezeigt."
                  :lang/en "Here will be displayed all weather stations
                  stored in the database."}
           :create {:lang/de "Neue Wetterstation erstellen:"
                    :lang/en "Create new weather station:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang rc/*lang*)] "UNKNOWN element"))

(defn get-wstation-entities []
  (let [db (bd/current-db "berest")
        result (d/q '[:find ?ws-e
                      :in $
                      :where
                      [?ws-e :weather-station/id]]
                    db)]
    (->> result
         (map first ,,,)
         (map (partial d/entity db) ,,,))))


(defn create-wstation-layout []
  [:div.container
   (for [e (rc/get-ui-entities :rest.ui/groups :weather-station)]
     (rc/create-form-element e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])


(defn get-wstations-layout [url]
  (rc/layout (str "GET | POST " url)
              [:div.container
               [:h3 (str "GET | POST " url)]

               [:div
                [:h4 (str (vocab :wstations) " (GET " url ")")]
                [:p (vocab :show)]
                [:hr]
                [:ul#farms
                 (for [wse (get-wstation-entities)]
                   [:li [:a {:href (str url "/" (:weather-station/id wse))}
                         (or (:weather-station/name wse) (:weather-station/id wse))]])
                 ]
                [:hr]
                [:h4 "application/edn"]
                [:code (pr-str (map :weather-station/id (get-wstation-entities)))]
                [:hr]]

               [:div
                [:h4 (str (vocab :create) " (POST " url ")")]
                [:form.form-horizontal {:role :form
                                        :method :post
                                        :action url}

                 (create-wstation-layout)]
                ]]))


(namespace :blab/aa)

(defn get-weather-stations [req]
  (let [url ((:url-for req) ::get-weather-stations :app-name :rest)]
    (rur/response (get-wstations-layout url))))

(defn parse-value [attr value]
  (let [db (bd/current-db "berest")
        result (d/q '[:find ?a
                      :in $ ?group
                      :where
                      [?ws-e :rest.ui/groups :weather-station]
                      [?ws-e ?a :db.type/string]]
                    db (keyword (namespace attr)))
        string-attrs (into #{} (map first result))]
    (if (string-attrs attr)
      value
      (edn/read-string value))))


(parse-value :geo-coord/lat "11.2")

(defn create-weather-station [req]
  (let [form-data (:form-params req)
        form-data* (into {} (map (fn [[k v]]
                                   (let [ns-k (rc/id->ns-key k)]
                                     [ns-k (parse-value ns-k v)]))
                                 form-data))
        transaction-data (assoc form-data* :db/id (d/tempid :db.part/user))]
    #_(try
      (d/transact (bd/datomic-connection "berest") transaction-data)
      (catch Exception e
        (log/info "Couldn't store weather station data to datomic! data:
                  [\n" transaction-data "\n]")))

    (rur/response (pr-str transaction-data))
    #_(d/transact (datomic-connection "berest") rui*)
    ))




