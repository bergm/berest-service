(ns berest-service.rest.common
  (:require [clojure.string :as cs]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [hiccup.def :as hd]
            [hiccup.util :as hu]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bd]))

(def ^:dynamic *lang* :lang/de)

(defn ns-key->id [ns-keyword]
  (str (namespace ns-keyword) "_" (name ns-keyword)))

(defn id->ns-key [id]
  (apply keyword (cs/split id #"_")))

#_(id->ns-key "geo-coord_longitude")


;; html5 inputs

(defn input-field [type* ui-entity]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:input.form-control {:type type*
                            :id id :name id
                            :placeholder ph}]]]))

(defn double-field [ui-entity]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:input.form-control {:type :number
                            :step "any"
                            :pattern "[0-9]+([,\\.][0-9]+)?"
                            :id id :name id
                            :placeholder ph}]]]))

(defn select-field [ui-entity list-entries]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:select.form-control {:id id :name id}
       (for [e list-entries]
         [:option {:value (:value e)} (:label e)])]]]))

(defn textarea-field [ui-entity rows]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.form-group
     [:label.col-sm-3.control-label {:for id} label]
     [:div.col-sm-9
      [:textarea.form-control {:id id :name id
                               :placeholder ph
                               :rows rows}]]]))


;; create rest ui from db entities

(defn get-ui-entities [attr & [value]]
  (let [db (bd/current-db "berest")
        result (if value
                 (d/q '[:find ?ui-e
                        :in $ ?attr ?value
                        :where
                        [?ui-e ?attr ?value]]
                      db attr value)
                 (d/q '[:find ?ui-e
                        :in $ ?attr
                        :where
                        [?ui-e ?attr]]
                      db attr))]
    (->> result
         (map first ,,,)
         (map (partial d/entity db) ,,,)
         (sort-by :rest.ui/order-no ,,,)
         #_(map d/touch ,,,))))



(defmulti create-form-element
  #(select-keys %1 [:db/valueType
                    :db/cardinality
                    :rest.ui/type]))


;;simple string input field
(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one}
  [ui-entity]
  (input-field :text ui-entity))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/email}
  [ui-entity]
  (input-field :email ui-entity))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/multi-line-text}
  [ui-entity]
  (textarea-field ui-entity 3))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/many
                                :rest.ui/type :rest.ui.type/multi-line-text}
  [ui-entity]
  (textarea-field ui-entity 3))

(defmethod create-form-element {:db/valueType :db.type/string
                                :db/cardinality :db.cardinality/many}
  [ui-entity]
  (input-field :text ui-entity))

;;date input field
(defmethod create-form-element {:db/valueType :db.type/instant
                                :db/cardinality :db.cardinality/one}
  [ui-entity]
  (input-field :date ui-entity))

;;number input field
(defmethod create-form-element {:db/valueType :db.type/long
                                :db/cardinality :db.cardinality/one}
  [ui-entity]
  (input-field :number ui-entity))

;;number input field
(defmethod create-form-element {:db/valueType :db.type/double
                                :db/cardinality :db.cardinality/one}
  [ui-entity]
  (double-field ui-entity))

;;refs to composite fields (just one)
(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one} [ui-entity]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (get-ui-entities :rest.ui/groups ref-group)]
       (create-form-element e)))])

;;might be multiple input fields (actually just doable by using javascript/clojurscript)
(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/many} [ui-entity]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (get-ui-entities :rest.ui/groups ref-group)]
       (create-form-element e)))])

(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/enum-list} [ui-entity]
  (select-field ui-entity (map (fn [e] {:label (-> e :rest.ui/label *lang*)
                                        :value (:db/ident e)})
                               (get-ui-entities :rest.ui/list (:rest.ui/list ui-entity)))))

(defmethod create-form-element {:db/valueType :db.type/ref
                                :db/cardinality :db.cardinality/one
                                :rest.ui/type :rest.ui.type/ref-list} [ui-entity]
  (let [id-attr (:rest.ui/list ui-entity)
        id-attr-ns (namespace id-attr)]
    (select-field ui-entity (map (fn [e]
                                   (let [id (id-attr e)
                                         name-attr (keyword id-attr-ns "name")
                                         label (or (name-attr e) id)]
                                     {:label label
                                      :value id}))
                                 (get-ui-entities (:rest.ui/list ui-entity))))))


#_(first (map create-form-element (get-ui-entities :rest.ui/groups :address)))



   #_(hd/defhtml layout [title & content]
            (hp/html5 {:xml? true}
                      [:head
                       [:title title]
                       (hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")
                       [:style
                        "body { margin: 2em; }"
                        "textarea { width: 80%; height: 200px }"]]
                      [:body content]))

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



