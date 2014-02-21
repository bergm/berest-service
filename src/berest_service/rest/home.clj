(ns berest-service.rest.home
  (:require [berest-service.berest.core :as bc]
            [berest-service.rest.common :as common]
            [berest-service.berest.datomic :as db]
            [datomic.api :as d]
            [io.pedestal.service.http.route :as route]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [geheimtur.util.auth :as auth]))

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:title {:lang/de ""
                   :lang/en "The BEREST REST Service provides 2 categories of resources
                   - one service to a users data in the database (data), including
                   useable representations of farms and plots and a simple way of creating
                   those resources and simple access to BEREST as a calculation/simulation
                   runtime for RPC like functionality.<br/><br/>
                   Note that this web app is the service. It is not an app built on the service,
                   nor a set of documentation pages about the service.
                   The URIs, query params, and POST data are the same ones you
                   will use when accessing the service programmatically.<br/><br/>
                   The embedded documentation is designed to assist you in using
                   the service API, but is not a reference nor tutorial for BEREST
                   itself. Please consult the BEREST documentation."}
           :farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :weather-stations {:lang/de "Wetter Stationen"
                              :lang/en "weather stations"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))



(defn home-layout
  [url]
  [:div.container
   [:h2 "Berest REST service"]
   [:p (vocab :title :lang/en)]
   [:hr]
   [:ul#berestElements
    [:li [:a {:href (str url "api/")} "API"]
     [:li [:a {:href (str url "data/")} "Data"]]
     ]]])

(defn get-home
  [{:keys [uri] :as request}]
  (->> (home-layout uri)
       (common/body (auth/get-identity request) ,,,)
       (hp/html5 (common/head "Berest REST service") ,,,)))



