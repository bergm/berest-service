(ns berest-service.rest.plot
  (:require [clojure.string :as cs]
            [io.pedestal.service.http :as http]
            [berest-service.berest.core :as bc]
            [berest-service.berest.datomic :as bd]
            [berest-service.berest.plot :as plot]
            [berest-service.rest.common :as rc]
            [berest-service.rest.queries :as rq]
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
          [element (or lang rc/*lang*)] "UNKNOWN element"))

(defn get-plot-ids [{:keys [path-params] :as request}]
  (let [{:keys [farm-id user-id]} path-params]
    (-> (plot/rest-plot-ids :edn user-id farm-id)
        http/edn-response)))


(defn- split-plot-id-format [plot-id-format]
  (-> plot-id-format
      (cs/split ,,, #"\.")
      (#(split-at (-> % count dec (max 1 ,,,)) %) ,,,)
      (#(map (partial cs/join ".") %) ,,,)))

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