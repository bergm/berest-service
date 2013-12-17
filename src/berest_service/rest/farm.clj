(ns berest-service.rest.farm
  (:require [berest-service.berest.core :as bc]
            [berest-service.berest.datomic :as bd]
            [berest-service.rest.common :as rc]
            [berest-service.rest.queries :as rq]
            #_[berest-service.service :as bs]
            [datomic.api :as d]
            [io.pedestal.service.http.route :as route]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Betriebe angezeigt."
                  :lang/en "Here will be displayed all farms
                  stored in the database."}
           :create {:lang/de "Neuen Betrieb erstellen:"
                    :lang/en "Create new farm:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang rc/*lang*)] "UNKNOWN element"))


(defn create-farms-layout []
  [:div.container
   (for [e (rq/get-ui-entities :rest.ui/groups :farm)]
     (rc/create-form-element e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

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
  (rc/layout (str "GET | POST " url)
             [:div.container
              [:h3 (str "GET | POST " url)]

              [:div
               [:h4 (str (vocab :farms) " (GET " url ")")]
               [:p (vocab :show)]
               [:hr]
               [:ul#farms
                (for [fe (get-farm-entities)]
                  [:li [:a {:href (str url "/" (:farm/id fe))} (or (:farm/name fe) (:farm/id fe))]])
                ]
               [:hr]
               [:h4 "application/edn"]
               [:code (pr-str (map :farm/id (get-farm-entities)))]
               [:hr]
               ]

              [:div
               [:h4 (str (vocab :create)" (POST " url ")")]
               [:form.form-horizontal {:role :form
                                       :method :post
                                       :action url}
                (create-farms-layout)]
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















