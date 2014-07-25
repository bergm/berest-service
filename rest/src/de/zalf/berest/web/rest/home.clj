(ns de.zalf.berest.web.rest.home
  (:require [de.zalf.berest.core.core :as bc]
            [de.zalf.berest.web.rest.common :as common]
            [de.zalf.berest.core.datomic :as db]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [ring.util.request :as req]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            #_[geheimtur.util.auth :as auth]))

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


           }
          [element (or lang common/*lang*)] "UNKNOWN element"))



(defn home-layout
  [url-path]
  [:div.container
   [:h2 "Berest REST service"]
   [:p (vocab :title :lang/en)]
   [:hr]
   [:ul#berestElements
    [:li [:a {:href (str url-path "login")} "Login"]]
    [:li [:a {:href (str url-path "logout")} "Logout"]]
    [:li [:a {:href (str url-path "api/")} "API"]]
    [:li [:a {:href (str url-path "auth-api/")} "Authenticated API"]]
    [:li [:a {:href (str url-path "data/")} "Data"]]]])

(defn get-home
  [{url-path :uri :as request}]
  (hp/html5
    (common/head "Berest REST service")
    (common/body nil #_(auth/get-identity request)
                 (home-layout url-path))))

(defn get-home-edn
  [request]
  (let [full-url (req/request-url request)]
    {:login  {:url (str full-url "login")
              :description "POST to this service login url for authentication to BEREST REST service."}
     :logout {:url (str full-url "logout")
              :description "POST to this service logout url to logout of BEREST REST service."}
     :api {:url (str full-url "api/")
           :description "Unauthenticated API resources of BEREST REST service."}
     :auth-api {:url (str full-url "auth-api/")
                :description "Authenticated API resources of BEREST REST service. POST to service-login/ required first."}
     :data   {:url (str full-url "data/")
              :description "GET data resources of BEREST REST service."}}))


