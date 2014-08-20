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
            [tailrecursion.castra.handler :refer [castra]]
            [de.zalf.berest.core.import.dwd-data :as dwd]))

(def server (atom nil))

(def castra-service
  (-> (castra 'de.zalf.berest.web.castra.api)
      (wrap-session {:store (cookie-store {:key "a 16-byte secret"})})
      #_(wrap-file "resources/public")
      (wrap-resource "public")
      (wrap-not-modified)
      (wrap-content-type)
      #_(wrap-file-info)))

(defn app [port public-path]
  (-> (castra 'de.zalf.berest.web.castra.api)
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
