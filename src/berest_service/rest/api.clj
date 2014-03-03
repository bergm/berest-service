(ns berest-service.rest.api
  (:require [clojure.string :as cs]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.pprint :as pp]
            [clojure.edn :as edn]
            [clojure.tools.logging :as log]
            [berest-service.berest.core :as bc]
            [berest-service.berest.plot :as plot]
            [berest-service.berest.datomic :as db]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.template :as temp]))

;; page translations

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

;; page layouts

(defn api-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   #_[:div
    [:h4 (str (vocab :farms) " (GET " url ")")]
    [:p (vocab :show)]
    [:hr]
    [:ul#farms
     (for [fe #_(get-farm-entities)]
       [:li [:a {:href (str url "/" (:farm/id fe))} (or (:farm/name fe) (:farm/id fe))]])
     ]
    [:hr]
    [:h4 "application/edn"]
    [:code (pr-str (map :farm/id #_(get-farm-entities)))]
    [:hr]
    ]

   #_[:div
    [:h4 (str (vocab :create)" (POST " url ")")]
    [:form.form-horizontal {:role :form
                            :method :post
                            :action url}
     #_(create-farms-layout)]
    ]])

(defn get-api
  [request]
  (let [db (db/current-db)]
    (common/standard-get (partial api-layout db)
                         request)))

;; api functions

(defn- split-plot-id-format [plot-id-format]
  (-> plot-id-format
      (cs/split ,,, #"\.")
      (#(split-at (-> % count dec (max 1 ,,,)) %) ,,,)
      (#(map (partial cs/join ".") %) ,,,)))

(defn simulate
  [{:keys [query-params path-params] :as request}]
  (let [{format :format :as data} query-params
        {farm-id :farm-id
         plot-id-format :plot-id-format} path-params
        [plot-id format*] (split-plot-id-format plot-id-format)
        plot-id "zalf"]
    (-> (plot/simulate-plot :user-id "guest" :farm-id farm-id :plot-id plot-id :data data)
        rur/response
        (rur/content-type ,,, "text/csv"))))

(defn calculate
  [{:keys [query-params path-params] :as request}]
  (let [{format :format :as data} query-params
        {farm-id :farm-id
         plot-id-format :plot-id-format} path-params
        [plot-id format*] (split-plot-id-format plot-id-format)
        plot-id "zalf"]
    (case (or format* format)
      "csv" (-> (plot/calc-plot :user-id "guest" :farm-id farm-id :plot-id plot-id :data data)
                rur/response
                (rur/content-type ,,, "text/csv"))
      (rur/not-found (str "Format '" format "' is not supported!")))))






