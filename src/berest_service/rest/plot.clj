(ns berest-service.rest.plot
  (:require [clojure.string :as cs]
            [berest.core :as bc]
            [berest.datomic :as db]
            [berest.plot :as plot]
            [berest-service.rest.common :as common]
            [berest-service.rest.queries :as queries]
            [berest-service.rest.template :as temp]
            #_[berest-service.service :as bs]
            [datomic.api :as d]
            [ring.util.response :as rur]
            [hiccup.element :as he]
            [hiccup.def :as hd]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [clojure.edn :as edn]))

(comment "for instarepl"

  (require '[berest-service.service :as s])

  ::get-farms

  )

(defn vocab
  "translatable vocabulary for this page"
  [element & [lang]]
  (get-in {:farms {:lang/de "Betriebe"
                   :lang/en "farms"}
           :show {:lang/de "Hier werden alle in der Datenbank
                  gespeicherten Betriebe angezeigt."
                  :lang/en "Here will be displayed all farms
                  stored in the database."}
           :create {:lang/de "Neuen Betrieb erstellen:"
                    :lang/en "Create new farm:"}
           :create-button {:lang/de "Erstellen"
                           :lang/en "Create"}

           }
          [element (or lang common/*lang*)] "UNKNOWN element"))

(defn create-plots-layout [db]
  [:div.container
   (for [e (queries/get-ui-entities db :rest.ui/groups :plot)]
     (common/create-form-element db e))

   [:button.btn.btn-primary {:type :submit} (vocab :create-button)]])

(defn plots-layout [db url]
  [:div.container
   (temp/standard-get-post-h3 url)

   (temp/standard-get-layout {:url url
                              :get-title (vocab :farms)
                              :description (vocab :show)
                              :get-id-fn :farm/id
                              :get-name-fn :farm/name
                              :entities (queries/get-entities db :plot/id)
                              :sub-entity-path ["plots"]})

   (temp/standard-post-layout {:url url
                               :post-title (vocab :create)
                               :post-layout-fn (partial create-plots-layout db)})])

(defn get-plots
  [request]
  (let [db (db/current-db)]
    (common/standard-get (partial plots-layout db)
                         request)))


(defn get-plots-edn
  [user-id farm-id]
  (->> (d/q '[:find ?plot-id
              :in $ ?farm-id
              :where
              [?farm-e :farm/id ?farm-id]
              [?plot-e :farm/_plots ?farm-e]
              [?plot-e :plot/id ?plot-id]]
            (db/current-db user-id) farm-id)
       (map first ,,,)))



(defn get-plot-ids [{:keys [path-params] :as request}]
  (let [{:keys [farm-id user-id]} path-params]
    (-> (plot/rest-plot-ids :edn user-id farm-id)
        #_http/edn-response)))


(defn- split-plot-id-format [plot-id-format]
  (-> plot-id-format
      (cs/split ,,, #"\.")
      (#(split-at (-> % count dec (max 1 ,,,)) %) ,,,)
      (#(map (partial cs/join ".") %) ,,,)))

(defn get-plot [farm-id id data]
  (let [user-id "berest"]
    (plot/calc-plot :user-id user-id :farm-id farm-id :plot-id id :data data)))

(defn get-rest-plot [req]
  (let [{sim :sim
         format :format
         :as data} (get-in req [:query-params])
        {farm-id :farm-id
         plot-id-format :plot-id-format} (get-in req [:path-params])
        [plot-id format*] (split-plot-id-format plot-id-format)
        simulate? (= sim "true")
        user-id "berest"
        plot-id "zalf"]
    (if simulate?
      (-> (plot/simulate-plot :user-id user-id :farm-id farm-id :plot-id plot-id :data data)
          rur/response
          (rur/content-type ,,, "text/csv"))
      (case (or format* format)
        "csv" (-> (plot/calc-plot :user-id user-id :farm-id farm-id :plot-id plot-id :data data)
                  rur/response
                  (rur/content-type ,,, "text/csv"))
        (rur/not-found (str "Format '" format "' is not supported!"))))))

(defn create-plot
  [request]
  (str "post to create a new plot, full request: " request))

(defn update-plot
  [farm-id id request]
  (str "put to update an plot on farm (id: " farm-id ") with id " id ", full request: " request))

