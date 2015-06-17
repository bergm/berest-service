(ns de.zalf.berest.web.zeromq.core
  (refer-clojure :exclude [send])
  (require [zeromq.zmq :as zmq]
           [zeromq.device :as zmqd]))

(defonce context (zmq/context))

(defn -main
  []
  (with-open [clients (doto (zmq/socket context :router)
                        (zmq/bind "tcp://*:5555"))
              workers (doto (zmq/socket context :dealer)
                        (zmq/bind "inproc://workers"))]
    (dotimes [i 5]
      (-> (Thread. #(with-open [receiver (doto (zmq/socket context :rep)
                                           (zmq/connect "inproc://workers"))]
                     (println "starting worker thread " i)
                     (while true
                       (let [string (zmq/receive-str receiver)]
                         (println (format "Received request: [%s]." string))
                         (Thread/sleep 1)
                         (zmq/send-str receiver "World!")))))
          .start))
    (zmqd/proxy context clients workers)))

(-main)
