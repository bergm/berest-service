(ns berest-service.rest.login
  (:require [berest.core :as bc]
            [berest.datomic :as db]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]
            #_[geheimtur.util.auth :as gua]))

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:username {:lang/de "Nutzername"
                      :lang/en "username"}
           :pwd {:lang/de "Passwort"
                 :lang/en "password"}}
          [element (or lang common/*lang*)] "UNKNOWN element"))

(defn login-form
  [return has-error]
  [:div {:class "col-lg-6 col-lg-offset-3"}
   (when has-error
     [:div {:class "alert alert-danger alert-dismissable"}
      [:button {:type "button" :class "close" :data-dismiss "alert" :aria-hidden "true"} "&times;"]
      "Wrong username and password combination."])
   [:form {:method "POST" :action (if return (str "/login?return=" return) "/login") :accept-charset "UTF-8"}
    [:fieldset
     [:legend "Sign in"]
     [:div {:class "form-group"}
      [:label {:for "username" :class "control-label hidden"} (vocab :username)]
      [:input {:type "text" :class "form-control" :id "username" :name "username" :placeholder (vocab :username) :autocomplete "off"}]]
     [:div {:class "form-group"}
      [:label {:for "password" :class "control-label hidden"} (vocab :pwd)]
      [:input {:type "password" :class "form-control" :id "password" :name "password" :placeholder (vocab :pwd)}]]
     [:div {:class "form-group"}
      [:button {:type "submit" :class "btn btn-default btn-block"} "Sign in"]]]]])

(defn get-login
  [{:keys [params] :as request}]
  (let [has-error (contains? params :error)]
    (->> (login-form (:return params) has-error)
         (common/body nil #_(gua/get-identity request) ,,,)
         (hp/html5 (common/head) ,,,))))



