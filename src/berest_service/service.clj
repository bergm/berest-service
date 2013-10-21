(ns berest-service.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [ring.util.response :as ring-resp]
              [berest-service.berest
               [plot :as plot]
               [farm :as farm]]
              [clojure.string :as cs]))

(defn about-page
  [request]
  (ring-resp/response (format "Clojure %s" (clojure-version))))

(defn home-page
  [request]
  (ring-resp/response "Hello berest service!!!"))

#_(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]]]])

(defn get-rest-farm [req]
  {:status 200 :body (str "Farm no: " (get-in req [:path-params :farm-id]))})

(defn get-rest-plot-ids [req]
  (let [farm-id (get-in req [:path-params :farm-id])
        user-id "admin"]
    (-> (plot/rest-plot-ids :edn user-id farm-id)
        bootstrap/edn-response)))

(defn- split-plot-id-format [plot-id-format]
  (-> plot-id-format
      (cs/split ,,, #"\.")
      (#(split-at (-> % count dec (max 1 ,,,)) %) ,,,)
      (#(map (partial cs/join ".") %) ,,,)))

(defn get-rest-plot-id [req]
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
          ring-resp/response
          (ring-resp/content-type ,,, "text/csv"))
      (case (or format* format)
        "csv" (-> (plot/calc-plot :user-id user-id :farm-id farm-id :plot-id plot-id :data data)
                  ring-resp/response
                  (ring-resp/content-type ,,, "text/csv"))
        (ring-resp/not-found (str "Format '" format "' is not supported!"))))))


(defroutes routes
  [[:test
    ["/" {:get home-page} ^:interceptors [bootstrap/html-body]]]
   [:rest
    ["/rest/farms/:farm-id" {:get get-rest-farm}
     ["/plots"
      ["/" {:get get-rest-plot-ids}]
      ["/:plot-id-format" {:get get-rest-plot-id}]]]]])



;; You can use this fn or a per-request fn via io.pedestal.service.http.route/url-for
(def url-for (route/url-for-routes routes))

;; Consumed by test.server/create-server
(def service {:env :prod
              ;; You can bring your own non-default interceptors. Make
              ;; sure you include routing and set it up right for
              ;; dev-mode. If you do, many other keys for configuring
              ;; default interceptors will be ignored.
              ;; :bootstrap/interceptors []
              ::bootstrap/routes routes

              ;; Uncomment next line to enable CORS support, add
              ;; string(s) specifying scheme, host and port for
              ;; allowed source(s):
              ;;
              ;; "http://localhost:8080"
              ;;
              ;;::bootstrap/allowed-origins ["scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

(defn service-with [port]
  (assoc service ::bootstrap/port port))






