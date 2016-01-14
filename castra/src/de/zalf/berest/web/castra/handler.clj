(ns de.zalf.berest.web.castra.handler
  (:require [ring.adapter.jetty :refer [run-jetty]]
            [ring.middleware.resource :refer [wrap-resource]]
            [ring.middleware.session :refer [wrap-session]]
            [ring.middleware.session.cookie :refer [cookie-store]]
            [ring.middleware.file :refer [wrap-file]]
            [ring.middleware.file-info :refer [wrap-file-info]]
            [ring.middleware.content-type :refer [wrap-content-type]]
            [ring.middleware.not-modified :refer [wrap-not-modified]]
            [ring.middleware.resource :refer [wrap-resource]]
            [castra.middleware :as cm]
            [castra.core :as cc]
            [compojure.core :as c]
            [compojure.route :as route]
            [de.zalf.berest.core.import.dwd-data :as dwd]
            [ring.util.response :as ring-resp]
            [ring.middleware.cors :as cors :refer [wrap-cors]]
            )
  (:import
    [java.util.regex Pattern]
    [java.io ByteArrayInputStream ByteArrayOutputStream]))

(def server (atom nil))

#_(defn wrap-access-control-allow-*
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (-> response
          (ring-resp/header ,,, "Access-Control-Allow-Origin" "*")
          #_(ring-resp/header ,,, "Access-Control-Allow-Headers" "origin, x-auth-token, x-csrf-token, content-type, accept")
          #_(ring-resp/header ,,, "X-Dev-Mode" "true")
          #_(#(do (println %) %))))))

(defn wrap-access-control-allow-*
  [handler]
  (fn [request]
    (when-let [response (handler request)]
      (-> response
          (ring-resp/header ,,, "Access-Control-Allow-Credentials" "true")
          #_(ring-resp/header ,,, "Access-Control-Allow-Headers" "origin, x-auth-token, x-csrf-token, content-type, accept")
          #_(ring-resp/header ,,, "X-Dev-Mode" "true")
          #_(#(do (println %) %))))))

(defn print**
  [handler]
  (fn [request]
    (println "request: " (pr-str request))
    (let [response (handler request)]
      (println "response: " (pr-str response))
      response)))

;; hat tip to io.aviso/pretty
(def ^:const ^:private ansi-pattern (Pattern/compile "\\e\\[.*?m"))
(defn ^String strip-ansi
  "Removes ANSI codes from a string, returning just the raw text."
  [string]
  (clojure.string/replace string ansi-pattern ""))

(defn- ex->clj [e]
  (let [e (if (cc/ex? e) e (cc/dfl-ex e))]
    {:message (.getMessage e)
     :data    (ex-data e)
     :stack   (strip-ansi
                (with-out-str
                  (try (clojure.stacktrace/print-cause-trace e)
                       (catch Throwable x
                         (try (clojure.stacktrace/print-stack-trace e)
                              (catch Throwable x
                                (printf "No stack trace: %s" (.getMessage x))))))))}))


(defn- csrf! []
  (when-not (get-in cc/*request* [:headers "x-castra-csrf"])
    (throw (ex-info "Invalid CSRF token" {}))))

(defn- do-rpc [vars [f & args]]
  (let [bad!  #(throw (ex-info "RPC endpoint not found" {:endpoint (symbol f)}))
        fun   (or (resolve (symbol f)) (bad!))]
    (when-not (contains? vars fun) (bad!))
    (apply fun args)))


(defn- select-vars [nsname & {:keys [only exclude]}]
  (let [to-var    #(resolve (symbol (str nsname) (str %)))
        to-vars   #(->> % (map to-var) (keep identity) set)
        var-pubs  #(do (require %) (vals (ns-publics %)))
        vars      (->> nsname var-pubs set)
        only      (if (seq only) (to-vars only) vars)
        exclude   (if (seq exclude) (to-vars exclude) #{})]
    (-> vars (clojure.set/intersection only) (clojure.set/difference exclude))))

(defn wrap-castra [handler & [opts & more :as namespaces]]
  (let [{:keys [body-keys] :as opts} (when (map? opts) opts)
        nses (if opts more namespaces)
        head {"X-Castra-Tunnel" "transit"}
        seq* #(or (try (seq %) (catch Throwable e)) [%])
        vars (fn [] (->> nses (map seq*) (mapcat #(apply select-vars %)) set))]
    (fn [req]
      (if-not (= :post (:request-method req))
        (handler req)
        (binding [*print-meta*    true
                  cc/*pre*           true
                  cc/*request*       req
                  cc/*session*       (atom (:session req))
                  cc/*validate-only* (= "true" (get-in req [:headers "x-castra-validate-only"]))]
          (let [_ (println "namespaces: " namespaces "nses: " nses " var: " (pr-str vars))
                h (cm/headers req head {"Content-Type" "application/json"})
                f #(do (csrf!) (do-rpc (vars) (cm/expression body-keys req)))
                d (try (cm/response body-keys req {:ok (f)})
                       (catch Throwable e
                         (cm/response body-keys req {:error (ex->clj e)})))]
            {:status 200, :headers h, :body d, :session @cc/*session*}))))))

(c/defroutes
  app-routes
  (c/GET "/" req (ring-resp/content-type (ring-resp/resource-response "index.html") "text/html"))
  (route/resources "/" {:root ""}))

(def castra-service
  (-> app-routes
      (cm/wrap-castra ,,, 'de.zalf.berest.web.castra.api)
      #_(cm/wrap-castra-session ,,, "a 16-byte secret")
      (wrap-session ,,, {:store (cookie-store {:key "a 16-byte secret"})})
      #_(wrap-file "resources/public")
      (wrap-resource ,,, "public")
      (wrap-resource ,,, "website")
      (wrap-cors ,,, :access-control-allow-origin [#".*"]
                     :access-control-allow-methods [:post])
      wrap-access-control-allow-*
      print**
      wrap-not-modified
      wrap-content-type))

(defn app [port public-path]
  (-> app-routes
      (cm/wrap-castra ,,, 'de.zalf.berest.web.castra.api)
      #_(cm/wrap-castra-session ,,, "a 16-byte secret")
      (wrap-session {:store (cookie-store {:key "a 16-byte secret"})})
      (wrap-file public-path)
      (wrap-file-info)
      (run-jetty {:join? false
                  :port port})))

(defn start-server
  "Start castra demo server (port 33333)."
  [port public-path]
  (swap! server #(or % (app port public-path))))

(defn run-task
  [port public-path]
  (.mkdirs (java.io.File. public-path))
  (start-server port public-path)
  (fn [continue]
    (fn [event]
      (continue event))))

(defn -main
  [& args])
