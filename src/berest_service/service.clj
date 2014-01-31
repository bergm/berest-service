(ns berest-service.service
    (:require [io.pedestal.service.http :as bootstrap]
              [io.pedestal.service.http.route :as route]
              [io.pedestal.service.http.body-params :as body-params]
              [io.pedestal.service.http.route.definition :refer [defroutes]]
              [io.pedestal.service.interceptor :as interceptor :refer [defon-response]]
              [io.pedestal.service.http.ring-middlewares :as ring-middlewares]
              [ring.util.response :as rur]
              [ring.middleware.session.cookie :as cookie]
              [berest-service.rest.farm :as farm]
              [berest-service.rest.home :as home]
              [berest-service.rest.login :as login]
              [berest-service.rest.common :as common]
              [berest-service.rest.weather-station :as wstation]
              [berest-service.rest.api :as api]
              [berest-service.rest.plot :as plot]
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


(defon-response access-forbidden-interceptor
  [response]
  (if (or
       (= 401 (:status response))
       (= 403 (:status response)))
    (->
     (common/error-page {:title "Access Forbidden" :message (:body response)})
     (rur/content-type "text/html;charset=UTF-8"))
    response))

(defon-response not-found-interceptor
  [response]
  (if-not (rur/response? response)
    (-> (common/error-page {:title   "Not Found"
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
                         access-forbidden-interceptor
                         (gi/interactive {})
                         session-interceptor]
     {:get home/get-home}
     ["/login" {:get login/login-page
                :post login-post-handler}]
     ["/logout" {:get gif/default-logout-handler}]
     ["/unauthorized" {:get common/unauthorized}]
     ["/api" {:get api/get-api}
      ["/simulate" {:get api/simulate}]
      ["/calculate" {:get api/calculate}]]
     ["/data"
      ["/:user-id"
       ^:interceptors [(gi/guard :roles #{:admin} :silent? false)]
       {:get home/get-user-home}
       ["/farms"
        {:get farm/get-farms
         :post farm/create-new-farm}]
       ["/:farm-id" {:get farm/get-farm
                     :put farm/update-farm}
        ["/plots" {:get plot/get-plot-ids}]
        ["/:plot-id-format" {:get plot/get-rest-plot}]]
       ["/weather-stations"
        ^:interceptors [(gi/guard :roles #{:admin} :silent? false)]
        {:get wstation/get-weather-stations
         :post wstation/create-weather-station}]]]]]])


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



















