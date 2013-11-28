(ns berest-service.rest.farm
  (:require [berest-service.berest.core :as bc]
            [berest-service.rest.common :as rc]
            [ring.util.response :as rur]
            [hiccup
             [element :as he]
             [def :as hd]
             [form :as hf]
             [page :as hp]]))

(defn get-farms [req]
  (let [url 1 #_((:url-for req) ::get-farms)]
    (rur/response
     (str "aaaaaablalbjalbj" url)
     #_(rc/layout ""
                [:div
                 [:h3 (str "GET | POST " url)]

                 [:div "list here all farms"]

                 [:div "create new farm"
                  (hf/form-to [:post url]
                              [:div
                               (hf/label "id" "Betriebsnummer")
                               (hf/text-field "id" "111")]
                              [:div
                               (hf/label "name" "Betriebsname:")
                               (hf/text-field "name" "")]
                              #_(login-fields user)
                              (hf/submit-button "Betrieb erstellen"))]

                 (str "and the footer of all farms")]))))

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







