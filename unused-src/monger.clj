(ns berest.monger
  (:require [clojure.math.numeric-tower :as nt])
  (:require [monger.collection :as mc])
  (:require [monger.operators :as mo])
  (:require [monger core util])
  (:require clojure.set)
  (:require [incanter.core :as ic])
  (:require [clojure.string :as cstr])
  (:require [clojure.pprint :as pp])
  (:require [clj-time.core :as ctc])
  )

(comment ;; localhost, default port
(monger.core/connect!)

;; given host, given port
;(monger.core/connect! { :host "db.megacorp.internal" :port 7878 })
(monger.core/set-db! (monger.core/get-db "berest"))
)


(comment
  (defn mongo-read-soil-moisture [snru day]
    (let [sms (mc/find-maps "feuchtes" {:snru snru, :tag day})]
      (first (map #(dissoc ) sms))))
  )


(comment
  ;; localhost, default port
  (monger.core/connect!)
  
  ;; given host, given port
  ;(monger.core/connect! { :host "db.megacorp.internal" :port 7878 })
  
  (monger.core/set-db! (monger.core/get-db "berest"))
  
  ;; with a query, as Clojure maps
  (mc/find-maps "schlags" {:schlagnummer "0400"})
  )