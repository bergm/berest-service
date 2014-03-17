(ns berest-service.rest.weather-station
  (:require [berest.core :as bc]
            [berest.datomic :as db]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.util :as util]
            [berest-service.rest.template :as temp]
            [datomic.api :as d]
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
          [element (or lang common/*lang*)] "UNKNOWN element"))




(defn create-wstation-layout [db]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :weather-station)]
     (common/create-form-element db e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])


(defn weather-stations-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :wstations)
                              :description (vocab :show)
                              :get-id-fn :weather-station/id
                              :get-name-fn :weather-station/name
                              :entities (queries/get-entities db :weather-station/id)
                              :sub-entity-path ["weather-stations"]})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-wstation-layout db)})])


(defn get-weather-stations
  [request]
  (let [db (db/current-db)]
    (common/standard-get (partial weather-stations-layout db)
                         request)))


(defn create-weather-station
  [{:keys [form-params] :as request}]
  (let [db (db/current-db)
        form-data (into {} (map (fn [[k v]]
                                  (let [attr (common/id->ns-attr k)]
                                    [attr (queries/string->value db attr v)]))
                                form-params))
        transaction-data (assoc form-data :db/id (d/tempid :db.part/user))]
    #_(try
        (d/transact (db/datomic-connection) transaction-data)
        (catch Exception e
          (log/info "Couldn't store weather station data to datomic! data:
                    [\n" transaction-data "\n]")))

    (rur/response (pr-str transaction-data))

    ))




(defn weather-station-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :wstations)
                              :description (vocab :show)
                              :get-id-fn :weather-station/id
                              :get-name-fn :weather-station/name
                              :entities (queries/get-entities db :weather-station/id)
                              :sub-entity-path "weather-station/"})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-wstation-layout db)})])


(defn get-weather-station
  [id request]
  (let [db (db/current-db)]
    (str "get weather station with id: " id ", full request: " request)
    #_(common/standard-get (partial weather-station-layout db)
                         request)))

(defn update-weather-station
  [id request]
  (str "put to update weather station with id: " id ", full request: " request)

  #_(let [db (db/current-db)
        form-data (into {} (map (fn [[k v]]
                                  (let [attr (common/id->ns-attr k)]
                                    [attr (queries/string->value db attr v)]))
                                form-params))
        transaction-data (assoc form-data :db/id (d/tempid :db.part/user))]
    #_(try
      (d/transact (db/datomic-connection) transaction-data)
      (catch Exception e
        (log/info "Couldn't store weather station data to datomic! data:
                  [\n" transaction-data "\n]")))

    (rur/response (pr-str transaction-data))

    ))


