(ns berest.web.views.welcome
  (:require [berest.web.views.common :as common]
            #_[noir.content.getting-started]
            #_[net.cgrand.enlive-html :as html])
  (:use [noir.core :only [defpage]]))

(defpage "/welcome" []
  (common/layout
    [:p "Welcome to Berest"]))

#_(html/deftemplate layout "berest/web/views/farm.html" [body]
                  [:div#wrapper] (html/content body))

#_(defpage "/farm/:id" {:keys [id]}
  (layout (str "the farm no " id " comes here")))

#_(defpage [:post "/login"] {:keys [username password]}
  (str "You tried to login as " username " with the password " password))

#_(defpage "/test" {:keys [arg1 arg2]}
  (str "You used arg1=" arg1 " and arg2=" arg2 " as arguments to the page."))
