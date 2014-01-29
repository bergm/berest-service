(ns berest-service.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :as interceptor :refer [defon-response]]
              [io.pedestal.service.http.ring-middlewares :as ring-middlewares]
              [ring.util.response :as rur]
              [ring.middleware.session.cookie :as cookie]
              [berest-service.berest.plot :as plot]
              [berest-service.berest.farm :as farm]
              [berest-service.rest.farm :as rfarm]
              [berest-service.rest.home :as rhome]
              [berest-service.rest.login :as rlogin]
              [berest-service.rest.common :as rcommon]
              [berest-service.rest.weather-station :as rwstation]
              [clojure.string :as cs]
              [geheimtur.interceptor :as gi]
              [geheimtur.impl.form-based :as gif]))

(def users {;"user" {:name "user" :password "zALf" :roles #{:user} :full-name "Bobby Briggs"}
            "admin" {:name "admin" :password "#zALf!" :roles #{:admin} :full-name "Michael Berg"}})

(defn credentials
  [username password]
  (when-let [identity (get users username)]
    (when (= password (:password identity))
      (dissoc identity :password))))


(defn about-page
  [request]
  (rur/response (format "Clojure %s" (clojure-version))))

(defn home-page
  [request]
  (rur/response "Hello berest service!!!!!!!"))

#_(defroutes routes
  [[["/" {:get home-page}
     ;; Set default interceptors for /about and any other paths under /
     ^:interceptors [(body-params/body-params) bootstrap/html-body]
     ["/about" {:get about-page}]]]])


(defn get-rest-plots [req]
  (let [farm-id (get-in req [:path-params :farm-id])
        user-id "admin"]
    (-> (plot/rest-plot-ids :edn user-id farm-id)
        bootstrap/edn-response)))


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

(defon-response access-forbidden-interceptor
  [response]
  (if (or
       (= 401 (:status response))
       (= 403 (:status response)))
    (->
     (rcommon/error-page {:title "Access Forbidden" :message (:body response)})
     (rur/content-type "text/html;charset=UTF-8"))
    response))

(defon-response not-found-interceptor
  [response]
  (if-not (rur/response? response)
    (-> (rcommon/error-page {:title   "Not Found"
                             :message "We are sorry, but the page you are looking for does not exist."})
        (rur/content-type "text/html;charset=UTF-8"))
    response))

(def login-post-handler
  (gif/default-login-handler {:credential-fn credentials}))

(interceptor/definterceptor
 session-interceptor
 (ring-middlewares/session {:cookie-name "SID"
                            :store (cookie/cookie-store)}))

(defroutes routes
  [#_[:home
    ["/" {:get home-page} ^:interceptors [bootstrap/html-body]]]
   [:rest
    ["/" ^:interceptors [(body-params/body-params)
                         bootstrap/html-body
                         access-forbidden-interceptor (gi/interactive {})
                         #_(gi/http-basic "BEREST REST-Service" credentials)
                         session-interceptor]
     {:get rhome/get-home}
     ["/login" {:get rlogin/login-page
                :post login-post-handler}]
     ["/logout" {:get gif/default-logout-handler}]
     ["/unauthorized" {:get rcommon/unauthorized}]
     ["/farms"
      ^:interceptors [(gi/guard :roles #{:admin} :silent? false)]
      {:get rfarm/get-farms
       :post rfarm/create-new-farm}
      ["/:farm-id" {:get rfarm/get-farm
                    :put rfarm/update-farm}
       ["/plots" {:get get-rest-plots}
        ["/:plot-id-format" {:get get-rest-plot}]]]]
     ["/weather-stations"
      ^:interceptors [(gi/guard :roles #{:admin} :silent? false)]
      {:get rwstation/get-weather-stations
       :post rwstation/create-weather-station}]]]])


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
              ;;::bootstrap/allowed-origins ["http://berest-humanespaces.rhcloud.com" #_"scheme://host:port"]

              ;; Root for resource interceptor that is available by default.
              ::bootstrap/resource-path "/public"

              ::bootstrap/not-found-interceptor not-found-interceptor

              ;; Either :jetty or :tomcat (see comments in project.clj
              ;; to enable Tomcat)
              ;;::bootstrap/host "localhost"
              ::bootstrap/type :jetty
              ::bootstrap/port 8080})

(defn service-with [port]
  (assoc service ::bootstrap/port port))



















