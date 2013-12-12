(ns berest-service.rest.farm
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
            [hiccup.page :as hp]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )


#_(map rc/create-form-element (rc/get-ui-entities :rest.ui/groups :farm))

#_(rc/create-form-element (first (rc/get-ui-entities :rest.ui/groups :farm)))

(defn create-farms-layout []
  [:div.container
   (for [e (rc/get-ui-entities :rest.ui/groups :farm)]
     (rc/create-form-element e))

   (hf/submit-button "Betrieb erstellen")])

(defn get-farm-entities []
  (let [db (bd/current-db "berest")
        result (d/q '[:find ?farm-e
                      :in $
                      :where
                      [?farm-e :farm/id _]]
                    db)]
    (->> result
         (map first ,,,)
         (map (partial d/entity db) ,,,))))

(defn get-farms-layout [url]
  (rc/layout ""
             [:div.container
              [:h3 (str "GET | POST " url)]

              [:div
               [:h4 (str "Betriebe (GET " url ")")]
               [:p "Hier werden alle in der Datenbank gespeicherten Betriebe angezeigt."]
               [:hr]
               [:ul#farms
                (for [fe (get-farm-entities)]
                  [:li [:a {:href (str url "/" (:farm/id fe))} (or (:farm/name fe) (:farm/id fe))]])
                ]
               [:hr]
               [:h4 "application/edn"]
               [:code "blabla"]
               [:hr]
               ]

              [:div
               [:h4 (str "Neuen Betrieb erstellen: (POST " url ")")]
               [:form.form-horizontal {:role :form
                                       :method :post}

                (hf/form-to [:post url] (create-farms-layout))]
               ]]))

(defn get-farms [req]
  (let [url ((:url-for req) ::get-farms :app-name :rest)]
    (-> (get-farms-layout url)
        rur/response
        (rur/content-type ,,, "text/html"))))

(defn create-new-farm [req]
  (rur/response "post to create a new farm"))

(defn get-farm [req]
  (rur/response (str "Farm no: " (get-in req [:path-params :farm-id])
                     " and full req: " req)))

(defn update-farm [req]
  (rur/response (str "put to farm id: " (get-in req [:path-params :farm-id]))))


#_(defn farm-layout [user-id id]
  [:div "user-id" user-id "& farm no " id]
  #_(if-let [plot (bc/db-read-plot id)]
    (let []
      [:h1 (str "Schlag: " id)]
        [:div#plotData
          [:div#currentDCData
            (str "DC: ")]])
    ([:div#error "Fehler: Konnte Schlag mit Nummer: " id " nicht laden!"])))

#_(defn farms-layout [user-id]
  [:div "all farms"
   #_(he/javascript-tag "weberest.web.views.client.hello_world()")
   (hf/submit-button {:id "xxx"} "press for hello")
   #_(hp/include-js "/cljs/main.js")])

#_(defn create-farm [user-id farm-data]
  (:id farm-data))

#_(defn new-farm-layout [user-id]
  (hf/form-to [:post "/farms/new"]
    [:div
      (hf/label "id" "Betriebsnummer")
      (hf/text-field "id" "111")]
    [:div
      (hf/label "name" "Betriebsname:")
      (hf/text-field "name" "")]
    #_(login-fields user)
    (hf/submit-button "Betrieb erstellen")))















