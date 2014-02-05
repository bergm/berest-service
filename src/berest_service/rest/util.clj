(ns berest-service.rest.util
  (:require [clojure.string :as cs]))

(defn drop-path-segment
  "return URL like string with one (n) path element(s) removed"
  [url-like & [n]]
  (as-> url-like x
        (cs/split x #"/")
        (drop-last (or n 1) x)
        (cs/join "/" x)))


