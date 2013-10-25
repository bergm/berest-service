(ns berest.web.views.climate
  (:require [berest.web.views.common :as common]
            #_[net.cgrand.enlive-html :as html]
            [berest.bcore :as bc]
            [noir [core :as nc]
                  [validation :as vali]]
            [hiccup.form :as hf]))

(nc/defpartial climate-station-layout [id]
  [:div "climate-station no " id]
  #_(if-let [plot (bc/db-read-plot id)]
    (let []
      [:h1 (str "Schlag: " id)]
        [:div#plotData 
          [:div#currentDCData 
            (str "DC: ")]])
    ([:div#error "Fehler: Konnte Klimastation mit Nummer: " id " nicht laden!"])))

(nc/defpartial climate-stations-layout []
  [:div "all climate-stations"])

(defn create-climate-station [cs-data]
  (:id cs-data))

(nc/defpartial new-climate-station-layout []
  (hf/form-to [:post "/climate-stations/new"]
    [:div 
      (hf/label "id" "Klimastationsnummer")
      (hf/text-field "id" "111")]
    [:div
      (hf/label "name" "Klimastationsname:")
      (hf/text-field "name" "")]
    #_(login-fields user)
    (hf/submit-button "Klimastations erstellen")))

