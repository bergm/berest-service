(ns de.zalf.berest.web.rest.util
  (:require [clojure.string :as cs]))

(defn drop-path-segment
  "return URL like string with one (n) path element(s) removed"
  [url-like & [n]]
  (as-> url-like _
        (cs/split _ #"/")
        (drop-last (or n 1) _)
        (cs/join "/" _)))


