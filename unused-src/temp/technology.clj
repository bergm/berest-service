(ns berest.web.views.technology
  (:require [berest.web.views.common :as common]
            #_[net.cgrand.enlive-html :as html]
            [berest.bcore :as bc]
            [noir [core :as nc]
                  [validation :as vali]]
            [hiccup.form :as hf]))

(nc/defpartial technology-layout [id]
  [:div "technology no " id]
  #_(if-let [plot (bc/db-read-plot id)]
    (let []
      [:h1 (str "Schlag: " id)]
        [:div#plotData 
          [:div#currentDCData 
            (str "DC: ")]])
    ([:div#error "Fehler: Konnte Schlag mit Nummer: " id " nicht laden!"])))

(nc/defpartial technologies-layout []
  [:div "all technologies"])

(defn create-technology [tech-data]
  (:id tech-data))

(nc/defpartial new-technology-layout []
  (hf/form-to [:post "/technologies/new"]
    [:div 
      (hf/label "id" "Technologienummer")
      (hf/text-field "id" "111")]
    [:div
      (hf/label "name" "Technologiename:")
      (hf/text-field "name" "")]
    #_(login-fields user)
    (hf/submit-button "Technologie erstellen")))


