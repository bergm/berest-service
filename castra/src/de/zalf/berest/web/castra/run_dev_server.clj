(ns de.zalf.berest.web.castra.run-dev-server
  (require [ring.server.standalone :as ring-server]
           [de.zalf.berest.web.castra.handler :as handler]))

(ring-server/serve handler/castra-service {:port 3000
                                           :open-browser? false})
