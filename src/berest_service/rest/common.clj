(ns berest-service.rest.common
  (:require [hiccup.element :as he]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [hiccup.def :as hd]
            [hiccup.util :as hu]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bd]))

(def ^:dynamic *lang* :lang/de)

;; html5 inputs

(defn- input-field
  "Creates a new <input> element."
  [type name value]
  [:input {:type  type
           :name  (hu/as-str name)
           :id    (hu/as-str name)
           :value value}])

(hd/defelem date-field
            "html5 date input"
            ([name] (date-field name nil))
            ([name value] (input-field "date" name value)))

(hd/defelem number-field
            "html5 number input"
            ([name] (number-field name nil))
            ([name value] (input-field "number" name value)))


;; create rest ui from db entities

(defn get-ui-entities [ui-group]
  (let [db (bd/current-db "berest")
        result (d/q '[:find ?ui-e
                      :in $ ?uig
                      :where
                      [?ui-e :rest.ui/groups ?uig]]
                    db ui-group)]
    (->> result
         (map first ,,,)
         (map (partial d/entity db) ,,,)
         (sort-by :rest.ui/order-no ,,,)
         #_(map d/touch ,,,))))


(defn- ns-key->id [ns-keyword]
  (str (namespace ns-keyword) "_" (name ns-keyword)))

(defmulti create-form-element #(vector (:db/valueType %) (:db/cardinality %)))

;;simple string input field
(defmethod create-form-element [:db.type/string :db.cardinality/one] [ui-entity]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.row
     [:label.col-md-3 {:name id} label]
     [:input.col-md-4 {:type :text :placeholder ph :id id :name id}]]))

(defmethod create-form-element [:db.type/string :db.cardinality/many] [ui-entity]
  (let [id (-> ui-entity :db/ident ns-key->id)
        label (-> ui-entity :rest.ui/label *lang*)
        ph (some-> ui-entity :rest.ui/placeholder *lang*)]
    [:div.row
     [:label.col-md-3 {:name id} label]
     [:input.col-md-4 {:type :text :placeholder ph :id id :name id}]]))

;;date input field
(defmethod create-form-element [:db.type/instant :db.cardinality/one] [ui-entity]
  [:div
   (hf/label (-> ui-entity :rest.ui/label :lang/de))
   (date-field (if-let [p (:rest.ui/placeholder ui-entity)]
                 {:rest.ui/placeholder (:lang/de p)}
                 {})
               (-> ui-entity :db/ident ns-key->id))])

;;number input field
(defmethod create-form-element [:db.type/long :db.cardinality/one] [ui-entity]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (number-field (if-let [p (:rest.ui/placeholder ui-entity)]
                   {:rest.ui/placeholder (:lang/de p)}
                   {})
                 (-> ui-entity :db/ident ns-key->id))])

;;refs to composite fields (just one)
(defmethod create-form-element [:db.type/ref :db.cardinality/one] [ui-entity]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (get-ui-entities ref-group)]
       (create-form-element e)))])

;;might be multiple input fields (actually just doable by using javascript/clojurscript)
(defmethod create-form-element [:db.type/ref :db.cardinality/many] [ui-entity]
  [:fieldset
   [:legend (-> ui-entity :rest.ui/label :lang/de)]
   (when-let [ref-group (:rest.ui/ref-group ui-entity)]
     (for [e (get-ui-entities ref-group)]
       (create-form-element e)))])






#_(first (map create-form-element (get-ui-entities :address)))



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



