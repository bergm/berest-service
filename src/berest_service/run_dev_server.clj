(ns berest-service.run_dev_server
  (require [ring.server.standalone :as ring-server]
           [berest-service.handler :as handler]))

(ring-server/serve handler/rest-service {:port 3000
                                         :open-browser? false})
