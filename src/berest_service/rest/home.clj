(ns berest-service.rest.home
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

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:title {:lang/de "Der Berest REST service.<br/>
                   Nachfolgend finden Sie eine Liste von Links zu den
                   eigentlichen Teilen des Service.<br/>
                   Sie sehen diese Seite auf diese Art und Weise, weil Sie
                   (oder Ihr Browser) den text/html Mediatyp angefordert haben.
                   Der application/edn Mediatyp ist ebenso für die gesamte API
                   untestützt und ist das bevorzugte Format für programmatische Nutzung.
                   Sie können Ihren bevorzugten Mediatyp über den Accept header wählen."
                   :lang/en "The Berest REST service.<br/>
                   Below you will find a list of links leading to the actual
                   service parts.</br>
                   You are seeing the page this way now because you
                   (or your browser) requested the text/html media type.
                   The application/edn media type is also supported throughout
                   the API, and is the preferred format for programmatic use.
                   You can choose your preferred media type using an Accept header."}
           :farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :weather-stations {:lang/de "Wetter Stationen"
                              :lang/en "weather stations"}

           }
          [element (or lang rc/*lang*)] "UNKNOWN element"))

(defn get-home [req]
  (rur/response
   (rc/layout "Berest REST service"
              [:div.container
               [:h2 "Berest REST service"]
               [:p (vocab :title)]
               [:hr]
               [:ul#berestElements
                [:li [:a {:href "farms"} (vocab :farms)]
                 [:li [:a {:href "weather-stations"} (vocab :weather-stations)]]
                 ]]
               ])))






