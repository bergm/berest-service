(ns berest-service.rest.user
  (:require [berest-service.berest.core :as bc]
            [berest-service.berest.datomic :as bd]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.util :as util]
            #_[berest-service.service :as bs]
            [datomic.api :as d]
            #_[io.pedestal.service.http.route :as route]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]
            [geheimtur.util.auth :as auth]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:users {:lang/de "Nutzer"
                   :lang/en "users"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Nutzer angezeigt."
                  :lang/en "Here will be displayed all users
                  stored in the database."}
           :create {:lang/de "Neuen Nutzer erstellen:"
                    :lang/en "Create new user:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

(defn create-user-layout []
  [:div.container
   (for [e (queries/get-ui-entities :rest.ui/groups :user)]
     (common/create-form-element e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn get-user-entities
  [& [db-id]]
  (let [db (bd/current-db (or db-id bd/*db-id*))
        result (d/q '[:find ?e
                      :in $
                      :where
                      [?e :user/id]]
                    db)]
    (->> result
         (map first ,,,)
         (map (partial d/entity db) ,,,))))

(defn users-layout [url]
  [:div.container
   [:h3 (str "GET | POST " url)]

   [:div
    [:h4 (str (vocab :users) " (GET " url ")")]
    [:p (vocab :show)]
    [:hr]
    [:ul#users
     (for [ue (get-user-entities)]
       [:li [:a {:href (str (util/drop-path-segment url)
                            "/user/" (:user/id ue))}
             (or (:user/name ue) (:user/id ue))]])
     ]
    [:hr]
    [:h4 "application/edn"]
    [:code (pr-str (map :user/id (get-user-entities)))]
    [:hr]
    ]

   [:div
    [:h4 (str (vocab :create)" (POST " url ")")]
    [:form.form-horizontal {:role :form
                            :method :post
                            :action url}
     (create-user-layout)]
    ]])

(defn get-users
  [{:keys [url-for params] :as request}]
  (let [url (url-for ::get-users :app-name :rest) ]
    (->> (users-layout url)
         (common/body url (auth/get-identity request) ,,,)
         (hp/html5 (common/head (str "GET | POST " url)) ,,,)
         rur/response)))



(defn user-layout [url]
  [:div.container
   [:h3 (str "GET | POST " url)]

   [:div
    [:h4 (str (vocab :users) " (GET " url ")")]
    [:p (vocab :show)]
    [:hr]
    [:ul#users
     (for [ue (get-user-entities)]
       [:li [:a {:href (str url "/user/" (:user/id ue))} (or (:user/name ue) (:user/id ue))]])
     ]
    [:hr]
    [:h4 "application/edn"]
    [:code (pr-str (map :user/id (get-user-entities)))]
    [:hr]
    ]

   [:div
    [:h4 (str (vocab :create)" (POST " url ")")]
    [:form.form-horizontal {:role :form
                            :method :post
                            :action url}
     (create-user-layout)]
    ]])

(defn get-user
  [{:keys [url-for params] :as request}]
  (let [url (url-for ::get-user :app-name :rest) ]
    (->> (user-layout url)
         (common/body url (auth/get-identity request) ,,,)
         (hp/html5 (common/head (str "GET | POST " url)) ,,,)
         rur/response)))



