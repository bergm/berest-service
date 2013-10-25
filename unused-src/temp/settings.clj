(ns berest.web.views.settings
  (:require [berest.web.views.common :as common]
            #_[net.cgrand.enlive-html :as html]
            [berest.bcore :as bc]
            [noir [core :as nc]
                  [validation :as vali]]
            [hiccup.form :as hf]))

(nc/defpartial settings-layout []
  [:div "all settings"]
  #_(if-let [plot (bc/db-read-plot id)]
    (let []
      [:h1 (str "Schlag: " id)]
        [:div#plotData 
          [:div#currentDCData 
            (str "DC: ")]])
    ([:div#error "Fehler: Konnte Schlag mit Nummer: " id " nicht laden!"])))

(defn save-settings [settings]
  true) ;= success

(nc/defpartial new-settings-layout []
  (hf/form-to [:post "/farms/new"]
    [:div 
      (hf/label "id" "Betriebsnummer")
      (hf/text-field "id" "111")]
    [:div
      (hf/label "name" "Betriebsname:")
      (hf/text-field "name" "")]
    #_(login-fields user)
    (hf/submit-button "Betrieb erstellen")))



