(ns berest-service.rest.weather-station
  (:require [berest-service.berest.core :as bc]
            [berest-service.rest.common :as common]
            [berest-service.berest.datomic :as bd]
            [berest-service.rest.queries :as rq]
            [berest-service.rest.util :as util]
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
            [clojure.tools.logging :as log]
            [geheimtur.util.auth :as gua]))

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




(defn create-wstation-layout []
  [:div.container
   (for [e (rq/get-ui-entities :rest.ui/groups :weather-station)]
     (common/create-form-element e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])


(defn weather-stations-layout [url]
  (let [wses (rq/get-entities :weather-station/id)]
    [:div.container
     [:h3 (str "GET | POST " url)]

     [:div
      [:h4 (str (vocab :wstations) " (GET " url ")")]
      [:p (vocab :show)]
      [:hr]
      [:ul#farms
       (for [wse wses]
         [:li [:a {:href (str (util/drop-path-segment url)
                              "/weather-station/" (:weather-station/id wse))}
               (or (:weather-station/name wse) (:weather-station/id wse))]])]
      [:hr]
      [:h4 "application/edn"]
      [:code (pr-str (map :weather-station/id wses))]
      [:hr]]

     [:div
      [:h4 (str (vocab :create) " (POST " url ")")]
      [:form.form-horizontal {:role :form
                              :method :post
                              :action url}

       (create-wstation-layout)]
      ]]))


(defn get-weather-stations
  [{:keys [url-for params] :as request}]
  (let [url (url-for ::get-weather-stations :app-name :rest)]
    (->> (weather-stations-layout url)
         (common/body url (gua/get-identity request) ,,,)
         (hp/html5 (common/head (str "GET | POST " url)) ,,,)
         rur/response)))


(defn create-weather-station [req]
  (let [form-data (:form-params req)
        form-data* (into {} (map (fn [[k v]]
                                   (let [attr (common/id->ns-attr k)]
                                     [attr (rq/string->value attr v)]))
                                 form-data))
        transaction-data (assoc form-data* :db/id (d/tempid :db.part/user))]
    #_(try
      (d/transact (bd/datomic-connection) transaction-data)
      (catch Exception e
        (log/info "Couldn't store weather station data to datomic! data:
                  [\n" transaction-data "\n]")))

    (rur/response (pr-str transaction-data))

    ))




(defn weather-station-layout [url]
  (let [wses (rq/get-entities :weather-station/id)]
    [:div.container
     [:h3 (str "GET | POST " url)]

     [:div
      [:h4 (str (vocab :wstations) " (GET " url ")")]
      [:p (vocab :show)]
      [:hr]
      [:ul#farms
       (for [wse wses]
         [:li [:a {:href (str url "/" (:weather-station/id wse))}
               (or (:weather-station/name wse) (:weather-station/id wse))]])]
      [:hr]
      [:h4 "application/edn"]
      [:code (pr-str (map :weather-station/id wses))]
      [:hr]]

     [:div
      [:h4 (str (vocab :create) " (POST " url ")")]
      [:form.form-horizontal {:role :form
                              :method :post
                              :action url}

       (create-wstation-layout)]
      ]]))


(defn get-weather-station
  [{:keys [url-for params] :as request}]
  (let [url (url-for ::get-weather-stations :app-name :rest)]
    (->> (weather-station-layout url)
         (common/body url (gua/get-identity request) ,,,)
         (hp/html5 (common/head (str "GET | POST " url)) ,,,)
         rur/response)))


(defn update-weather-station [req]
  (let [form-data (:form-params req)
        form-data* (into {} (map (fn [[k v]]
                                   (let [attr (common/id->ns-attr k)]
                                     [attr (rq/string->value attr v)]))
                                 form-data))
        transaction-data (assoc form-data* :db/id (d/tempid :db.part/user))]
    #_(try
      (d/transact (bd/datomic-connection) transaction-data)
      (catch Exception e
        (log/info "Couldn't store weather station data to datomic! data:
                  [\n" transaction-data "\n]")))

    (rur/response (pr-str transaction-data))

    ))


