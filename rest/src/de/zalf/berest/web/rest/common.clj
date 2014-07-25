(ns de.zalf.berest.web.rest.common
  (:require [clojure.string :as cs]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [hiccup.def :as hd]
            [hiccup.util :as hu]
            [datomic.api :as d]
            #_[de.zalf.berest.datomic :as db]
            [de.zalf.berest.core.queries :as queries]
            [de.zalf.berest.web.rest
             [util :as util]]
            [ring.util.response :as rur]
            #_[geheimtur.util.auth :as auth]))

(def ^:dynamic *lang* :lang/de)

(defn ns-attr->id [ns-keyword]
  (str (namespace ns-keyword) "_" (name ns-keyword)))

(defn id->ns-attr [id]
  (apply keyword (cs/split id #"_")))


(defn- merge-attrs
  "merge attributes map 'attrs with key value pairs in 'opt-attrs
  if according value in 'opt-attrs is not nil"
  [attrs & {:as opt-attrs}]
  (reduce (fn [m [k v]]
            (if v
              (assoc m k v)
              m))
          attrs opt-attrs))

;; html5 inputs

(defn input-field [type* ui-entity & [{:keys [value disabled?]}]]
  (let [id (-> ui-entity :db/ident ns-attr->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:input.form-control (merge-attrs {:type type*
                                         :id id
                                         :name id
                                         :placeholder ph}
                                        :value value
                                        :disabled (when disabled? "disabled"))]]]))

(defn double-field [ui-entity & [{:keys [value disabled?]}]]
  (let [id (-> ui-entity :db/ident ns-attr->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:input.form-control (merge-attrs {:type :number
                                         :step "any"
                                         :pattern "[0-9]+([,\\.][0-9]+)?"
                                         :id id
                                         :name id
                                         :placeholder ph}
                                        :value value
                                        :disabled (when disabled? "disabled"))]]]))

(defn select-field [ui-entity list-entries & [{:keys [value disabled?]}]]
  (let [id (-> ui-entity :db/ident ns-attr->id)
        label (-> ui-entity :rest.ui/label *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:select.form-control {:id id :name id}
       (for [e list-entries]
         [:option (merge-attrs {:value (:value e)}
                               :selected (when (and value (= value (:value e)))
                                           "selected"))
          (:label e)])]]]))

(defn textarea-field [ui-entity rows & [{:keys [value disabled?]}]]
  (let [id (-> ui-entity :db/ident ns-attr->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:textarea.form-control (merge-attrs {:id id
                                            :name id
                                            :placeholder ph
                                            :rows rows
                                            :value value}
                                           :value value
                                           :disabled (when disabled? "disabled"))]]]))


;; create rest ui from db entities





(defmulti create-form-element
          (fn [_db ui-entity & [{:keys [_value _disabled?] :as _opts}]]
            (select-keys ui-entity [:db/valueType
                                    :db/cardinality
                                    :rest.ui/type])))


;;simple string input field
(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one}
  [_ ui-entity & [opts]]
  (input-field :text ui-entity opts))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/email}
  [_ ui-entity & [opts]]
  (input-field :email ui-entity opts))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/multi-line-text}
  [_ ui-entity & [opts]]
  (textarea-field ui-entity 3 opts))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/many
                                :rest.ui/type :rest.ui.type/multi-line-text}
  [_ ui-entity & [opts]]
  (textarea-field ui-entity 3 opts))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/many}
  [_ ui-entity & [opts]]
  (input-field :text ui-entity opts))

;;date input field
(defmethod create-form-element {:db/valueType :db.type/instant
                                :db/cardinality :db.cardinality/one}
  [_ ui-entity & [opts]]
  (input-field :date ui-entity opts))

;;number input field
(defmethod create-form-element {:db/valueType :db.type/long
                                :db/cardinality :db.cardinality/one}
  [_ ui-entity & [opts]]
  (input-field :number ui-entity opts))

;;number input field
(defmethod create-form-element {:db/valueType :db.type/double
                                :db/cardinality :db.cardinality/one}
  [_ ui-entity & [opts]]
  (double-field ui-entity opts))

;;refs to composite fields (just one)
(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one}
  [db ui-entity & [opts]]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (queries/get-ui-entities db :rest.ui/groups ref-group)]
       (create-form-element db e)))])

;;might be multiple input fields (actually just doable by using javascript/clojurscript)
(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/many}
  [db ui-entity & [opts]]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (queries/get-ui-entities db :rest.ui/groups ref-group)]
       (create-form-element db e)))])

#_(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/enum-list}
  [db ui-entity & [opts]]
  (select-field ui-entity (map (fn [e] {:label (-> e :rest.ui/label *lang*)
                                        :value (:db/ident e)})
                               (queries/get-ui-entities db :rest.ui/list (:rest.ui/list ui-entity)))))


(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/enum-list}
  [db ui-entity & [opts]]
  (select-field ui-entity (map (fn [e]
                                 {:label (or (-> e :rest.ui/label *lang*) (:db/id e))
                                  :value (:db/id e)})
                               (queries/get-ui-entities db :rest.ui/list (:rest.ui/list-values ui-entity)))))

(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/many
                                :rest.ui/type :rest.ui.type/enum-list}
  [db ui-entity & [opts]]
  (select-field ui-entity (map (fn [e]
                                 {:label (or (-> e :rest.ui/label *lang*) (:db/id e))
                                  :value (:db/id e)})
                               (queries/get-ui-entities db :rest.ui/list (:rest.ui/list-values ui-entity)))))


(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/ref-list}
  [db ui-entity & [opts]]
  (let [id-attr (:rest.ui/list-values ui-entity)
        name-attr (keyword (namespace id-attr) "name")]
    (select-field ui-entity (map (fn [e]
                                   (let [id (id-attr e)
                                         label (or (name-attr e) id)]
                                     {:label label
                                      :value id}))
                                 (queries/get-ui-entities db id-attr)))))



#_(require '[berest-service.berest.datomic :as db])
#_(map create-form-element (db/current-db) (queries/get-ui-entities (db/current-db) :rest.ui/groups :user))



(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:page-name {:lang/de "BEREST Service"
                      :lang/en "BEREST service"}
           :signed-in-as {:lang/de "Eingeloggt als "
                          :lang/en "Signed in as "}
           :all-farms {:lang/de "Alle Betriebe"
                       :lang/en "all farms"}
           :dwd-weather-stations {:lang/de "DWD Wetterstationen"
                                  :lang/en "DWD weather stations"}}
          [element (or lang *lang*)] "UNKNOWN element"))



#_(hd/defhtml layout [title & content]
              (hp/html5 {:xml? true}
                        [:head
                         [:title title]
                         (hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")
                         [:style
                          "body { margin: 2em; }"
                          "textarea { width: 80%; height: 200px }"]]
                        [:body content]))

(defn head
  [& [title]]
  [:head
   (when (and title (not (empty? title))) [:title title])
   [:meta {:name "viewport"
           :content "width=device-width, initial-scale=1.0"}]
   [:link {:href "/css/auth-buttons.css" :media "screen" :rel "stylesheet" :type "text/css"}]
   [:link {:href "/css/bootstrap.min.css" :media "screen" :rel "stylesheet" :type "text/css"}]
   #_(hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")
   "<!--[if lt IE 9]>"
   [:script {:src "/js/html5shiv.js"}]
   [:script {:src "/js/respond.min.js"}]
   "<![endif]-->"])



(defn navbar
  [user]
  [:nav {:class "navbar navbar-default" :role "navigation"}
   [:div {:class "navbar-header"}
    [:button {:type "button" :class "navbar-toggle" :data-toggle "collapse" :data-target ".navbar-collapse"}
     [:span {:class "sr-only"} "Toggle navigation"]
     [:span {:class "icon-bar"}]
     [:span {:class "icon-bar"}]
     [:span {:class "icon-bar"}]]
    [:a {:class "navbar-brand" :href "/"} (vocab :page-name)]]
   [:div {:class "collapse navbar-collapse"}
    [:ul {:class "nav navbar-nav"}
     [:li [:a {:href "/home"} "Home"]]
     [:li [:a {:href ""} "other quick-link"]]]
    (when-not (nil? user)
      [:div {:class "navbar-right"}
       [:p {:class "navbar-text"}
        (vocab :signed-in-as) [:strong (:full-name user)]]
       [:a {:href "/logout" :class "btn btn-primary navbar-btn"}
        "Logout"]])]])

(defn body
  [user & content]
  [:body
   (navbar user)
   [:div {:class "container"}
    [:div {:class "row"}
     content]]
   [:script {:src "//code.jquery.com/jquery.js"}]
   [:script {:src "/js/bootstrap.min.js"}]])

(defn error-page
  [context]
  (->> [:div {:class "col-lg-8 col-lg-offset-2"}
        [:h2 (:title context)]
        [:p (:message context)]]
       (body (:user context) ,,,)
       (hp/html5 (head) ,,,)
       rur/response))

(defn unauthorized
  [request]
  (->> [:div {:class "col-lg-8 col-lg-offset-2"}
        [:h2 "Unauthorized"]
        [:p "It looks like there was a problem authenticating you, sir. Please try again."]]
       (body nil ,,,)
       (hp/html5 (head) ,,,)
       rur/response))


(defn standard-get
  [{:keys [uri params] :as request} layout]
  (hp/html5
    (head (str "GET | POST " uri))
    (body
      nil #_(auth/get-identity request)
      layout)))


(hd/defhtml layout [title & content]
            (hp/html5
             [:head
              (when-not (empty? title) [:title title])
              [:meta {:name "viewport"
                      :content "width=device-width, initial-scale=1.0"}]
              (hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")]
             [:body content]))



(comment
  "
  <!DOCTYPE html>
  <html>
  <head>
    <title>Bootstrap 101 Template</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Bootstrap -->
    <link href="css/bootstrap.min.css" rel="stylesheet">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
      <script src='https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js'></script>
      <script src='https://oss.maxcdn.com/libs/respond.js/1.3.0/respond.min.js'></script>
    <![endif]-->
  </head>
  <body>
    <h1>Hello, world!</h1>

    <!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
    <script src='https://code.jquery.com/jquery.js'></script>
    <!-- Include all compiled plugins (below), or include individual files as needed -->
    <script src="js/bootstrap.min.js"></script>
  </body>
 </html>
  ")

#_(hd/defhtml layout+js [& content]
  (hp/html5 {:xml? true}
    [:head
     [:title "Berest"]
     (hp/include-css "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.0/css/bootstrap-combined.min.css")
     #_(include-css "/css/reset.css")
     #_(include-js "/js/g.raphael-min.js")
     #_(include-js "/js/g.line-min.js")
     (hp/include-js "/cljs/main.js")
     [:style
      "body { margin: 2em; }"
      "textarea { width: 80%; height: 200px }"]]
    [:body
     content]))

#_(hd/defhtml layout-webfui [name]
         (hp/html5
          [:head
           [:title "webfui-client"]
           #_(hp/include-css (str "/css/" name ".css"))
           #_(he/javascript-tag "var CLOSURE_NO_DEPS = true;")
           (hp/include-js (str "/cljs/" name ".js"))]
          [:body]))




