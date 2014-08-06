(ns de.zalf.berest.web.castra.run-dev-server
  (require [ring.server.standalone :as ring-server]
           [de.zalf.berest.web.castra.handler :as handler]))

(ring-server/serve handler/castra-service {:port 3000
                                           :open-browser? false
                                           :auto-reload? true
                                           :reload-paths ["D:\\git-repos\\github\\bergm\\berest-core\\src"
                                                          "D:\\git-repos\\github\\bergm\\berest-service\\castra\\src"]})