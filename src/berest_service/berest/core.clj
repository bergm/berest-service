(ns berest-service.berest.core
  (:require [clojure.math.numeric-tower :as nt]
            [clojure.java.io :as cjio]
            clojure.set
            [clojure.string :as str]
            [clojure.pprint :as pp]
            [clj-time
             [core :as ctc]
             [format :as ctf]]
            [berest-service.berest
             [datomic :as bd]
             [util :as bu]]
            [clojure-csv.core :as csv]
            [datomic.api :as d]

            [berest-service.berest.helper :as bh
             :refer [defnk defnk- |* |-> |<- --< --<*]]
            [let-else :as le
             :refer [let?]]
            [clojure.algo.generic.functor :as cagf
             :refer [fmap]]
            [taoensso.timbre :as timbre
             :refer [trace debug info warn error fatal spy]]

            #_[clojure.tools.macro :as ctm]
            #_[clojure.algo.generic.arithmetic :as caga]
            #_[incanter.core :as ic]))

(timbre/set-level! :info)
(timbre/set-config! [:timestamp-pattern] ""#_"yyyy-MMM-dd HH:mm:ss ZZ")
(timbre/set-config! [:timestamp-locale] (java.util.Locale/GERMAN))
(timbre/set-config! [:prefix-fn] (fn [{:keys [level timestamp hostname ns]}]
                                   (str #_timestamp #_" " #_hostname #_" " (-> level name str/upper-case)
                                        " [" ns "]")))

;(frinj-init!)

(def ^{:dynamic true :berest/unit :cm} *layer-sizes* (flatten [5 5 (repeat 19 10)]))
#_(def ^{:dynamic true :berest/unit :cm} *layer-sizes* [10 20 30 40 50])
(defn ^{:berest/unit :cm} layer-depths [] (reductions + *layer-sizes*))
(defn no-of-soil-layers [] (count *layer-sizes*))
(defn ^{:berest/unit :cm} max-soil-depth [] (last (layer-depths)))

(defn pFK->mm7x
  "fc [mm/x] -> [mm/x]"
  [fc percent-value]
  (* 1/100 percent-value fc))

(defn pNFK->mm7x
  "fc, pwp [mm/x] -> [mm/x]"
  [fc pwp percent-value]
  (+ pwp (* 1/100 percent-value (- fc pwp))))

(defn mm7x->pNFK
  "fc, pwp, mm-value [mm/x] -> [% NFK]"
  [fc pwp sm-value]
  (/ (- sm-value pwp) (* (- fc pwp) 100)))

(defn volp->mm7dm "value [volp] -> [mm/dm] -> " [value] value)

(defn mm7dm->volp "value [mm/dm] -> [volp]" [value] value)

(defn mm7dm->mm7cm "value [mm/dm] -> [mm/cm]" [value] (/ value 10))

(defn mm7cm->mm7dm "value [mm/cm] -> [mm/dm]" [value] (* value 10))

(def fout (ref []))
(def out (ref ""))

(defn append-out [out append-func value]
  (dosync
   (alter out append-func value)))

(defn nFC [fc pwp] (- fc pwp))

(defn potential-evaporation-turc-wendling
  [globrad-Jpcm2 tavg & {:keys [fk] :or {fk 1}}]
  (/ (* (+ globrad-Jpcm2 (* 93 fk)) (+ tavg 22)) (* 150 (+ tavg 123))))

(defn climatic-water-balance-turc-wendling
  [precip-mm globrad-Jpcm2 tavg & {:keys [fk] :or {fk 1}}]
  (- precip-mm (potential-evaporation-turc-wendling globrad-Jpcm2 tavg :fk fk)))

(defn sorted-unzip-map
  "split a map into it's keys and values by sorted keys, return [[keys][values]]"
  [map]
  ((--< keys vals) (into (sorted-map) map)))

(defn expand-layers
  "(expand-fn value expanded-depth)
  (last-values-fn expanded-values last-value) ... returns function which takes like expand-fn
  [value depth] but knows how to treat 'values' below last depths until max-depth,
  the default being to simply repeat the last-value from 'values' parameter"
  [max-depth [depths values] & {:keys [expand-fn last-values-fn]
                                :or {expand-fn (fn [value depth] value)
                                     last-values-fn (fn [expanded-values last-value]
                                                      (fn [value depth] last-value))}}]
  {:pre [(= (count depths) (count values))]}
  (let [;create pairs of upper and lower bounds
        bounds (partition 2 1 (into (sorted-set) (flatten [0 (take-while #(<= % max-depth) depths) max-depth])))

        ;create sizes out of the list of bounds
        depths* (map (fn [[l r]] (- r l)) bounds)

        ;associate the according values to the depths just created, but only until the depth available in depths-list
        expanded-values (mapcat #(repeat %1 (expand-fn %2 %1)) depths* values)]

    ;now add potentially values filling up to max-depths, the function last-values-fn
    ;can examine the stuff done so far and get the last value
    (println "expanded-values: " (count expanded-values) " max-depth: " max-depth " last depths*: " (last depths*)
             " last expanded-values: " (last expanded-values))
    (if (< (count expanded-values) max-depth)
      (concat expanded-values
              (repeat (last depths*)
                      ((last-values-fn expanded-values (last values)) (last values) (last depths*))))
      expanded-values)))

(defn aggregate-layers
  "aggregate layers according to given layer-sizes and reduce function reduce-fn"
  [reduce-fn layer-sizes layer-data]
  (->> (loop [res []
              sizes layer-sizes
              data layer-data]
         (if (not (seq sizes))
           (if (seq data)
             (lazy-cat res [data])
             res)
           (let [size (first sizes)]
             (recur (lazy-cat res [(take size data)]) (rest sizes) (drop size data)))))
       (map (partial reduce reduce-fn) ,,,)))

(defn adjacent-kv-pairs [map key]
  {:lower (->> map
               (filter #(< (first %) key) ,,,)
               (into (sorted-map) ,,,)
               last)
   :upper (->> map
               (filter #(> (first %) key) ,,,)
               (into (sorted-map) ,,,)
               first)})

(defn interpolated-value
  "interpolate between the keys in map m for the given value key
  or if an exact match, just return the matched value in m
  m can also contain a map as values, in which case the values of that map
  are assumed to be numbers and treated as multiple results"
  [m key]
  (if (empty? m)
    nil
    (if-let [v (find m key)]
      (second v)
      ;split in lower and upper parts, lower parts
      ;will again include = to circumvent that clojure will
      ;compare for equality 20.0 = 20 => false (equivalence 20.0 == 20 => true),
      ;but the find above will use equality and thus won't match integer and
      ;floating point values
      (let [m-lower (->> m
                         (filter #(<= (first %) key) ,,,)
                         (into (sorted-map) ,,,))
            m-upper (->> m
                         (filter #(> (first %) key) ,,,)
                         (into (sorted-map) ,,,))]
        (cond
         (empty? m-lower) (-> m-upper first second)
         (empty? m-upper) (-> m-lower last second)
         :else (let [[lowerKey, lowerValue] (last m-lower)
                     [upperKey, upperValue] (first m-upper)
                     normalized-key (/ (- key lowerKey)
                                       (- upperKey lowerKey))]
                 (if (every? map? [lowerValue upperValue])
                   ;the values are actually maps
                   (into (empty lowerValue)
                         (map (fn [[lk lv] [uk uv]]
                                [lk (+ lv (* normalized-key
                                             (- uv lv)))])
                              lowerValue upperValue))
                   ;the values are normal scalars
                   (+ lowerValue (* normalized-key
                                    (- upperValue lowerValue))))))))))

(defn remove-namespace-from-keyword
  "remove the namespace from the given keyword"
  [kw]
  (-> kw name keyword))

(defmulti remove-namespace-1
  "remove namespace keywords collection just 1 level deep"
  class)

(defmethod remove-namespace-1
  clojure.lang.IPersistentMap [m]
  (reduce (fn [m [key value]]
            (if (keyword? key)
              (-> m
                  (assoc ,,, (remove-namespace-from-keyword key) value)
                  (dissoc ,,, key))
              m))
          m m))

(defn remove-namespace-from-keywords
  "remove namespace from keywords in collection deeply"
  [collection]
  (clojure.walk/prewalk #(if (keyword? %)
                           (remove-namespace-from-keyword %)
                           %)
                        collection))

(defn abs-pre-calculation-day [plot] (dec (:plot/abs-calculation-day plot)))

(defn crop-id [crop] (str (:plot/number crop) "/" (:plot/cultivation-type crop) "/" (:plot/usage crop)))

(defn resulting-damage-compaction-depth-cm
  [plot]
  (if (> (:plot/damage-compaction-area plot) (* 0.5 (:plot/crop-area plot)))
    (:plot/damage-compaction-depth plot) 300))

#_(defn awc-percent
    "available water capacity in percent (nFK Prozent)"
    [from-layer to-layer soil-moisture fc pwp]
    (let [[sum-wc sum-awc]
          (reduce (fn [[sum-wc sum-awc] i]
                    (let [[smi fci pwpi] ((--< soil-moisture fc pwp) i)]
                      [(+ sum-wc (- smi pwpi))
                       (+ sum-awc (nFC fci pwpi))]))
                  [0. 0.] (range 0 (count soil-moisture)))]
      (max 0. (round (* 100.0 (/ sum-wc sum-awc))))))

(defn create-soil-moistures
  "create some interesting soil-moistures, function had been used in berest"
  [max-soil-depth layer-depths soil-moisture fc pwp]
  (let [;create expanded layers for all 3 inputs (1 cm layers)
        [sm fc pwp] (map #(expand-layers max-soil-depth [layer-depths %] :expand-fn /)
                         [soil-moisture fc pwp])

        ;the two functions to calculate and sum a vector of pairs of values
        ;for water content and available water content
        sum-wc-fn (partial reduce #(+ %1 (apply - %2)) 0.)
        sum-awc-fn (partial reduce #(+ %1 (apply nFC %2)) 0.)

        ;the actual function calulating the percentage for a requested layer range
        f (fn [from to]
            (let [create-pairs (partial map vector)
                  sum-wc (-> (create-pairs sm pwp)
                             vec
                             (subvec ,,, from to)
                             sum-wc-fn)
                  sum-awc (-> (create-pairs fc pwp)
                              vec
                              (subvec ,,, from to)
                              sum-awc-fn)]
              (max 0. (Math/round (* 100.0 (/ sum-wc sum-awc))))))]
    {:pNFK_0-30cm (f 0 30)
     :pNFK_30-60cm (f 30 60)
     :pNFK_0-60cm (f 0 60)}))

(defn lambda-correction
  "return the lambda correction factor for the given day of year,
  assuming right now the given day of year is without leap years"
  {:doc/origin "The expression part in the let is taken from:
   W. Mirschel et al./Ecological Modelling 81 (1995) 53-69, equation (13)
   while the doy* binding is taken from the BOWET source code (the last part can be taken from
   as well:
   Pascal code:
   IF(jahr[l] MOD 4 = 0) THEN BEGIN
   IF(tag > 305) THEN t:= tag - 305 ELSE t:= tag + 61;
   END;
   IF(jahr[l] MOD 4 > 0) THEN BEGIN
   IF(tag > 304) THEN t:= tag - 304 ELSE t:= tag + 61;
   END;
   {Die letzten 6 Anweisungen nur zur Anpassung an Programm verdtur2}
   fakt:=1 + 0.77 * SIN(0.01571 * (t - 166));"}
  [doy]
  (let [doy* (if (> doy 304)
               (- doy 304)
               (+ doy 61))]
    (+ 1 (* 0.77 (Math/sin (* 0.01571 (- doy* 166)))))))

(defn lambda-without-correction
  "create lambda without correction layer structure"
  [resulting-damage-compaction-depth-cm stt fcs-cm]
  (let [[first-30-cm below-30-cm] (map (partial apply array-map)
                                       #_[[fk-in-volP*10 lambda*100]]
                                       [[100 115, 140 68, 200 50, 240 33, 260 33,
                                         300 25, 312 25, 327 17, 356 15, 1000 15]
                                        [90 115, 120 68, 180 50, 220 33, 250 33,
                                         280 25, 344 25, 364 17, 373 15, 1000 15]])
        cm->dm #(/ % 10)
        mm7cm->volp (|-> mm7cm->mm7dm mm7dm->volp)]
    (->> fcs-cm
         ; create cm uncorrected lambda layers
         (map-indexed
          (fn [layer-no fc]
            (/ (cond
                (< resulting-damage-compaction-depth-cm (inc layer-no)) 1
                (<= 1121 stt 1323) 100
                (<= 713 stt 723) 5
                :else (->> (if (< layer-no 30)
                             first-30-cm
                             below-30-cm)
                           (drop-while (fn [[fc*10 _]] (>= (* (mm7cm->volp fc) 10) fc*10)) ,,,)
                           first ;first element in rest of list
                           second)) ;get lambda from pair
               100))
          ,,,)
         ; aggregate layers via choosing smallest lambda value in resulting layer
         (aggregate-layers min *layer-sizes* ,,,)
         ; divide by resulting layer-size squared in dm (to use the above dm values)
         (map #(/ %2 (* (cm->dm %) (cm->dm %))) *layer-sizes*) ,,,)))

(defn lambda
  "get layers with lambda values at a given day of year"
  [lambda-without-correction doy]
  (map (partial * (lambda-correction doy)) lambda-without-correction))

#_(defrecord Donation [day amount])

(defn donations-at [donations day]
  (reduce
   (fn [acc d]
     (if (= day (:irrigation/abs-day d))
       (+ acc (:irrigation/amount d))
       acc))
   0 donations))

(comment
  (clojure.walk/prewalk-replace {:irrigation/abs-start-day :start
                                 :irrigation/abs-end-day :end
                                 :irrigation/area :area
                                 :irrigation/amount :amount}))

(defn db-read-irrigation-donations [db plot-no]
  (->> (d/q '[:find ?donation-e
              :in $ ?plot-no
              :where
              [?plot-e :plot/number ?plot-no]
              [?plot-e :plot/irrigation-water-donations ?donation-e]]
            db plot-no)
       (map #(-> %
                 first
                 (bd/get-entity ,,,))
            ,,,)))

(defn db-create-irrigation-donation
  [datomic-connection plot-no abs-start-day abs-end-day area amount]
  (let [plot-id (ffirst (d/q '[:find ?plot :in $ ?plot-no
                               :where [?plot :plot/number ?plot-no]]
                             (d/db datomic-connection) plot-no))
        donation {:db/id (bd/new-entity-id),
                  :irrigation/abs-start-day abs-start-day
                  :irrigation/abs-end-day abs-end-day
                  :irrigation/area area
                  :irrigation/amount amount}
        plot {:db/id plot-id
              :plot/irrigation-water-donations (bd/get-entity-id donation)}]
    (d/transact datomic-connection (flatten [donation plot]))))

(defn read-irrigation-donations [db plot-no irrigation-area]
  (->> (db-read-irrigation-donations db plot-no)
       (filter #(and (>= (:irrigation/abs-end-day %) (:irrigation/abs-start-day %))
                     (> (:irrigation/area %) (* 0.5 irrigation-area)))
               ,,,)
       (map #(assoc % :irrigation/abs-day (/ (+ (:irrigation/abs-start-day %)
                                                (:irrigation/abs-end-day %))
                                             2))
            ,,,)))

(comment
  (def ds (list (:abs-day 1 :amount 20) (:abs-day 2 :amount 10) (:abs-day 1 :amount 30)))
  (println (donations-at ds 2))
  )

(def climate-data
  (-> "private/climate-data-muencheberg-zalf-1992-to-1998.csv"
      #_"private/climate-data-muencheberg-1993-to-1998.csv"
      cjio/resource
      slurp
      (csv/parse-csv :delimiter \;)))

(def weather-map
  (reduce (fn [sm [day month year tavg precip globrad]]
            (let [y (Integer/parseInt year)
                  doy (bu/date-to-doy (Integer/parseInt day)
                                      (Integer/parseInt month)
                                      y)
                  sm* (if (find sm y)
                        sm
                        (assoc sm y (sorted-map)))]
              (assoc-in sm* [y doy]
                        (let [gr (Float/parseFloat globrad)
                              ta (Float/parseFloat tavg)]
                          {:doy doy
                           :tavg ta
                           :globrad gr
                           :evaporation (potential-evaporation-turc-wendling gr ta)
                           :precipitation (Float/parseFloat precip)
                           :prognosis? false}))))
          (sorted-map) (rest climate-data)))

(defn longterm-evap-precip [doy]
  (let [longterm-average-evaporation-values
        [#_"01.04." 1.1, 1.2, 1.2, 1.2, 1.3, 1.3, 1.3, 1.4, 1.4, 1.4,
         #_"11.04." 1.4, 1.5, 1.5, 1.5, 1.6, 1.6, 1.6, 1.7, 1.7, 1.7,
         #_"21.04." 1.8, 1.8, 1.8, 1.9, 1.9, 1.9, 2.0, 2.0, 2.0, 2.1,
         #_"01.05." 2.1, 2.1, 2.2, 2.2, 2.2, 2.3, 2.3, 2.3, 2.4, 2.4,
         #_"11.05." 2.4, 2.5, 2.5, 2.5, 2.6, 2.6, 2.6, 2.7, 2.7, 2.7,
         #_"21.05." 2.7, 2.8, 2.8, 2.8, 2.9, 2.9, 2.9, 2.9, 3.0, 3.0, 3.0,
         #_"01.06." 3.0, 3.1, 3.1, 3.1, 3.2, 3.2, 3.2, 3.2, 3.3, 3.3,
         #_"11.06." 3.3, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4,
         #_"21.06." 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4, 3.4,
         #_"01.07." 3.4, 3.4, 3.3, 3.3, 3.3, 3.3, 3.3, 3.3, 3.3, 3.3,
         #_"11.07." 3.3, 3.3, 3.3, 3.3, 3.3, 3.3, 3.3, 3.2, 3.2, 3.2,
         #_"21.07." 3.2, 3.2, 3.2, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.1, 3.0,
         #_"01.08." 3.0, 3.0, 3.0, 3.0, 3.0, 2.9, 2.9, 2.9, 2.9, 2.9,
         #_"11.08." 2.9, 2.9, 2.8, 2.8, 2.8, 2.8, 2.7, 2.7, 2.7, 2.7,
         #_"21.08." 2.6, 2.6, 2.6, 2.6, 2.5, 2.5, 2.4, 2.4, 2.4, 2.4, 2.4,
         #_"01.09." 2.3, 2.3, 2.3, 2.2, 2.2, 2.2, 2.2, 2.1, 2.1, 2.1,
         #_"11.09." 2.0, 2.0, 2.0, 2.0, 1.9, 1.9, 1.9, 1.8, 1.8, 1.8,
         #_"21.09." 1.7, 1.7, 1.6, 1.6, 1.6, 1.5, 1.5, 1.5, 1.4, 1.4,
         #_"01.10." 1.4, 1.3, 1.3, 1.3, 1.2, 1.2, 1.2, 1.1, 1.1, 1.0,
         #_"11.10." 1.0, 1.0, 0.9, 0.9, 0.9, 0.7, 0.6, 0.7, 0.5, 0.8,
         #_"21.10." 1.0, 1.0, 0.9, 0.9, 0.9, 0.7, 0.6, 0.7, 0.5, 0.8]
        longterm-average-precipitation-values
        [#_"01.04." 1.0, 1.7, 0.6, 0.5, 1.1, 0.9, 0.9, 0.9, 1.9, 1.5,
         #_"11.04." 1.1, 0.8, 1.2, 1.5, 2.2, 0.9, 1.4, 1.1, 2.0, 0.9,
         #_"21.04." 0.7, 1.3, 0.9, 0.9, 0.4, 0.6, 0.9, 1.0, 2.0, 1.6,
         #_"01.05." 1.3, 1.1, 2.0, 1.5, 1.6, 1.8, 1.9, 1.3, 1.0, 1.3,
         #_"11.05." 4.2, 0.6, 1.6, 1.5, 1.3, 0.6, 0.9, 1.9, 1.4, 4.6,
         #_"21.05." 1.0, 0.9, 0.4, 0.9, 2.7, 1.0, 3.6, 2.8, 0.7, 2.2, 2.3,
         #_"01.06." 1.1, 2.2, 0.6, 1.3, 1.0, 0.8, 0.7, 2.7, 4.4, 3.5,
         #_"11.06." 2.0, 6.0, 1.3, 1.0, 1.8, 1.9, 1.5, 1.0, 3.3, 1.5,
         #_"21.06." 1.9, 2.8, 0.7, 0.6, 3.6, 2.4, 4.1, 3.3, 3.5, 1.9,
         #_"01.07." 1.6, 1.5, 1.9, 3.0, 3.4, 1.9, 1.1, 0.9, 2.5, 1.2,
         #_"11.07." 1.3, 2.2, 1.5, 1.0, 2.5, 2.0, 1.9, 3.4, 1.1, 4.3,
         #_"21.07." 3.6, 3.7, 3.6, 1.5, 0.9, 1.4, 2.1, 1.0, 1.4, 1.2, 0.9,
         #_"01.08." 0.8, 0.5, 2.4, 1.7, 1.0, 1.3, 0.8, 1.7, 1.9, 1.3,
         #_"11.08." 2.0, 1.7, 1.4, 0.3, 2.3, 1.7, 2.8, 1.1, 1.1, 3.1,
         #_"21.08." 1.6, 2.9, 1.2, 1.4, 2.6, 1.4, 2.4, 3.2, 4.0, 1.6, 0.6,
         #_"01.09." 2.1, 0.5, 0.3, 1.0, 1.4, 1.6, 3.1, 1.8, 2.6, 2.3,
         #_"11.09." 2.9, 1.0, 1.2, 1.9, 0.6, 2.0, 1.8, 1.1, 0.7, 1.2,
         #_"21.09." 0.6, 1.5, 0.6, 2.3, 1.2, 0.9, 0.6, 2.2, 2.3, 1.0,
         #_"01.10." 0.6, 0.8, 2.5, 0.4, 0.7, 0.5, 2.1, 0.5, 1.1, 2.4,
         #_"11.10." 0.8, 0.2, 0.9, 1.6, 1.0, 2.5, 1.7, 1.6, 1.5, 1.0,
         #_"21.10." 2.2, 2.9, 1.8, 1.4, 1.2, 0.6, 1.3, 2.0, 0.4, 1.9]]
    (if (and (< 90 doy) (<= doy (+ 90 213)))
      (let [index (- doy 90 1)]
        {:tavg -9999
         :globrad -9999
         :evaporation (nth longterm-average-evaporation-values index),
         :precipitation (nth longterm-average-precipitation-values index)})
      {:tavg -9999
       :globrad -9999
       :evaporation -9999
       :precipitation -9999})))

(defn weather-at [m doy]
  (if-let [v (find m doy)]
    (second v)
    (assoc (longterm-evap-precip doy) :prognosis? true)))

;type CodeEinheit = | PFK | PNFK | Volp | MM
;:PFK :PNFK :Volp :MM

;type IrrigationMode = | SprinkleLosses | NoSprinkleLosses
;:Irrigation-mode :Sprinkle-losses :No-sprinkle-losses

(defnk user-input-fc-or-pwp-to-cm-layers
  "convert the user input field capacity or permanent wilting point to cm layers
  e.g. {30 17.7, 60 13.7, 150 15.7}
  user-input-soil-data [volP (= mm/dm) | pFK | pNFK | mm] -> [mm/cm]"
  [user-input-soil-data :unit :volP]
  (let [[expand-layers*
         x->mm7cm*] (case unit
                      (:volP :pFK :pNFK) [(|* expand-layers (max-soil-depth))
                                          (|-> volp->mm7dm mm7dm->mm7cm)]
                      :mm [#(expand-layers (max-soil-depth) %
                                           :expand-fn /
                                           :last-values-fn (fn [expanded-values last-value]
                                                             (fn [value depth] (last expanded-values))))
                           identity])]
    (->> user-input-soil-data
         sorted-unzip-map
         expand-layers*
         (map x->mm7cm* ,,,))))

(defn user-input-soil-moisture-to-cm-layers
  "convert the user input soil-moisture into the internally defined layer structure
  e.g. {30 3.4, 60 2.9, 150 3.8} :pFK
  fc, pwp [mm/cm], soil-moisture-unit [pFK | pNFK | volP | mm] -> [mm/cm]"
  [fcs-cm pwps-cm soil-moisture-unit user-input-soil-moistures]
  (let [[expand-layers*
         x->mm7cm*] (case soil-moisture-unit
                      (:volP :pFK :pNFK) [(|* expand-layers (max-soil-depth))
                                          (soil-moisture-unit {:pFK #(pFK->mm7x %1 %3)
                                                               :pNFK pNFK->mm7x
                                                               :volP #(-> %3
                                                                          volp->mm7dm
                                                                          mm7dm->mm7cm)})]
                      :mm [#(expand-layers (max-soil-depth) %
                                           :expand-fn /
                                           :last-values-fn (fn [expanded-values last-value]
                                                             (fn [value depth] (last expanded-values))))
                           (fn [_ _ sm] sm)])]
    (->> user-input-soil-moistures
         sorted-unzip-map
         expand-layers*
         (map x->mm7cm* fcs-cm pwps-cm ,,,))))

(defn db-crop-data-map->crop
  "transform the crop data from datomic to crop map"
  [db-crop-data-map]
  (reduce (fn [m [relation-kw key-kw value-kw]]
            (assoc m relation-kw
              (bd/create-map-from-entities key-kw value-kw (relation-kw m))
              #_(bd/create-map-from-entity-ids key-kw value-kw (relation-kw m))))
          db-crop-data-map
          [[:crop/dc-to-rel-dc-days :kv/dc :kv/rel-dc-day]
           [:crop/dc-to-developmental-state-names :kv/dc :kv/name]
           [:crop/rel-dc-day-to-cover-degrees :kv/rel-dc-day :kv/cover-degree]
           [:crop/rel-dc-day-to-extraction-depths :kv/rel-dc-day :kv/extraction-depth]
           [:crop/rel-dc-day-to-transpiration-factors :kv/rel-dc-day :kv/transpiration-factor]
           [:crop/rel-dc-day-to-quotient-aet-pets :kv/rel-dc-day :kv/quotient-aet-pet]]))

(defn db-create-crop
  "create crop from give db crop-id"
  [db crop-id]
  (db-crop-data-map->crop (into {} (bd/get-entity db crop-id))))

(defn db-read-crop
  "read a crop from the database by the given number and optional cultivation type and usage"
  [db number & {:keys [cultivation-type usage] :or {cultivation-type 1 usage 0}}]
  (let [crop-id (ffirst (d/q '[:find ?crop-id
                               :in $ ?no ?ct ?u
                               :where
                               [?crop-id :crop/number ?no]
                               [?crop-id :crop/cultivation-type ?ct]
                               [?crop-id :crop/usage ?u]]
                             db number cultivation-type usage))]
    (db-create-crop db crop-id)))

(defn entity->map
  [db entity]
  (letfn [(copy [value] (if (= datomic.query.EntityMap (type value))
                          (entity->map db (bd/get-entity db (:db/id value)))
                          value))]
    (into {} (for [[key value?s] entity]
               [key (if (set? value?s)
                      (map copy value?s)
                      (copy value?s))]))))

(defn available-plot-ids
  "get all plot ids available in the given 'db'"
  [db]
  (map first (d/q '[:find ?plot-no :in $ :where [? :plot/number ?plot-no]] db)))

(defn db-read-plot
  "read a plot with id 'plot-id' completely given the db-value 'db' with associated data from year 'year'"
  [db plot-id year]
  (let? [[plot-e-id yearly-values-e-id]
         (first (d/q '[:find ?plot-e-id ?yv-e-id
                       :in $ ?plot-id ?year
                       :where
                       [?plot-e-id :plot/number ?plot-id]
                       [?plot-e-id :plot/yearly-values ?yv-e-id]
                       [?yv-e-id :plot/year ?year]]
                     db plot-id year))
         :else nil

         [plot plot-yv] (map (partial bd/get-entity db) [plot-e-id yearly-values-e-id])

         fcs-cm (->> (:plot/field-capacities plot)
                     (bd/create-map-from-entities :soil/upper-boundary-depth
                                                  :soil/field-capacity
                                                  ,,,)
                     user-input-fc-or-pwp-to-cm-layers)
         fcs (->> fcs-cm
                  (aggregate-layers + *layer-sizes* ,,,))

         pwps-cm (->> (:plot/permanent-wilting-points plot)
                      (bd/create-map-from-entities :soil/upper-boundary-depth
                                                   :soil/permanent-wilting-point
                                                   ,,,)
                      user-input-fc-or-pwp-to-cm-layers)
         pwps (->> pwps-cm
                   (aggregate-layers + *layer-sizes* ,,,))

         sms (->> (:plot/initial-soil-moistures plot-yv)
                  (bd/create-map-from-entities :soil/upper-boundary-depth
                                               :soil/soil-moisture
                                               ,,,)
                  (user-input-soil-moisture-to-cm-layers
                   fcs-cm pwps-cm (->> (:plot/initial-sm-unit plot-yv)
                                       remove-namespace-from-keyword))
                  (aggregate-layers + *layer-sizes* ,,,))

         lwc (lambda-without-correction
              (resulting-damage-compaction-depth-cm plot)
              (:plot/stt plot)
              fcs-cm)

         ;read a fallow "crop" to be used with this plot
         fallow (db-read-crop db 0 :cultivation-type 1 :usage 0)

         plot-yv* (->> plot-yv
                       (entity->map db ,,,)
                       (clojure.walk/postwalk (fn [item]
                                                (if (vector? item)
                                                  (let [[kw db-crop-data-map] item]
                                                    (if (= kw :crop-instance/template)
                                                      [kw (db-crop-data-map->crop db-crop-data-map)]
                                                      item))
                                                  item))
                                              ,,,))]

        (-> (entity->map db plot)
            (dissoc ,,, :db/id :plot/yearly-values)
            (merge ,,, (dissoc plot-yv* :db/id))
            (assoc ,,,
              :plot/initial-soil-moistures sms
              :plot/field-capacities fcs
              :plot/permanent-wilting-points pwps
              :lambda-without-correction lwc
              :fallow fallow))))

(defn db-store-initial-soil-moisture
  [datomic-connection plot-no depths soil-moistures units]
  (let [plot-id (ffirst (d/q '[:find ?plot :in $ ?plot-no
                               :where [?plot :plot/number ?plot-no]]
                             (d/db datomic-connection) plot-no))
        entities (bd/create-entities {:soil/upper-boundary-depth depths
                                      :soil/soil-moisture soil-moistures
                                      :soil/soil-moisture-unit units})
        plot {:db/id plot-id
              :plot/user-soil-data (bd/get-entity-ids entities)}]
    (d/transact datomic-connection (flatten [entities plot]))))


(defn glugla
  "Calculates the excess water in a soil-layer given the initial water content, the
  infiltration into that layer and the lambda value for the layer, by default
  for one day, but optionally for a time slice of dti days:
  ni* [mm] = Ni* = Ni [mm/d] - Vi [mm/d] = precipitation - evaporation
  dti [d] = delta ti = length of time slice i [d]
  wia [mm] = WiA = water content at the start of time slice i
  ni* [mm] = Ni* = Ni [mm/d] - Vi [mm/d] = precipitation - evaporation
  lai [1/(mm*d)] = lambda i = parameter of the water movement in the soil during time slice i
  wie [mm] = WiE = water content at the end of time slice i = final excess water"
  [wia ni* lai & {dti :dti :or {dti 1}}]

  {:doc/origin "Gerhard Glugla, Albrecht-Thaer-Archiv, Arbeiten aus den Gebieten Bodenkunde,
   Pflanzenernährung, Düngung, Acker- und Pflanzenbau, 1969, 13. Band, Akademie-Verlag Berlin,
   Berechnungsverfahren zur Ermittlung des aktuellen Wassergehalts und Gravitationswasserabflusses
   im Boden, Seite 374, Abb. 2"}

  (cond
   ;more water will fit into the current layer (below infiltration barrier)
   (neg? wia) (let [wie (+ (* ni* dti) wia)]
                (if (and (>= ni* 0.) (pos? wie))
                  (let [dsti1 (- (/ wia ni*))
                        ais (- (Math/exp (* -2 (Math/sqrt (* lai ni*)) (- dti dsti1))))]
                    (/ (* (Math/sqrt (/ ni* lai)) (+ 1 ais))
                       (- 1 ais)))
                  wie))
   ;current layer already above infiltration barrier
   :else (cond
          (zero? ni*) (/ wia (+ 1 (* lai wia dti)))
          ;Entzug
          (neg? ni*)(let [n7l (Math/sqrt (/ (- ni*) lai))
                          l*n (Math/sqrt (* (- ni*) lai))
                          dti1 (/ (Math/atan (/ wia n7l))
                                  l*n)]
                      (if (> dti1 dti)
                        (let [bi (Math/tan (* l*n dti))]
                          (/ (* n7l (- wia (* n7l bi)))
                             (+ n7l (* wia bi))))
                        (* ni* (- dti dti1))))
          ;(pos? ni*) // kein Entzug
          :else (let [n7l (Math/sqrt (/ ni* lai))
                      l*n (Math/sqrt (* lai ni*))
                      ai (* (/ (- wia n7l)
                               (+ wia n7l))
                            (Math/exp (* -2 l*n dti)))]
                  (/ (* n7l (+ 1 ai))
                     (- 1 ai))))))

(defn glugla*
  "same as glugla, but immediately calculates also the infiltration into the next layer"
  [initial-excess-water infiltration-from-layer-above lambda & {dti :delta-t-i :or {dti 1}}]
  (let [few (glugla initial-excess-water infiltration-from-layer-above lambda :dti dti)]
    {:final-excess-water few
     :infiltration-into-next-layer (+ initial-excess-water infiltration-from-layer-above (- few))}))

(defn interception [precipitation evaporation irrigation transpiration-factor irrigation-mode]
  (let [null5 0.5
        null2 0.2
        tf (max 1 transpiration-factor)

        ;Berechnung fuer natuerlichen Regen
        tin (+ null5 (* (- evaporation 1) tf null2))
        [interception-precipitation, pet] (if (> precipitation 0)
                                            [tin, (- evaporation (* tin null5))]
                                            [0, evaporation])

        ;Berechnung fuer Zusatzregen/Spruehverluste
        [interception-irrigation,
         sprinkle-loss,
         pet*] (if (> irrigation 1)
                 (let [[ii, sl]
                       (condp = irrigation-mode
                         :sprinkle-losses [(* 0.6 tin (+ 1 (* irrigation 0.05))),
                                           (* (+ 1 (* (- evaporation 2.5) null2)) null2 irrigation)]
                         :no-sprinkle-losses [0, 0])]
                   (if (> precipitation 0)
                     [ii, sl, (- evaporation (* (+ ii interception-precipitation) null5))]
                     [ii, sl, (- evaporation (* ii 0.75))]))
                 [0, 0, pet])]
    {:pet (max 0 pet*),
     :effective-precipitation (- precipitation interception-precipitation),
     :effective-irrigation (- irrigation interception-irrigation sprinkle-loss),
     :effective-irrigation-uncovered (- irrigation sprinkle-loss)}))

(defn uncovered-water-abstraction-fraction
  "Get fraction of water-abstraction on uncovered soil for given layer i when using maximal m equal sized layers
  the function will use by default the curvature parameter z with value 0.05 which fits best the value for the
  first two layers in the original BEREST table [:0-10cm 0.625, :10-20cm 0.3, :30-60cm 0.075].
  The third layer (30-60cm) isn't matched exactly, but it's the layer with the smallest water-abstraction
  and very close (0.08145 vs 0.075), but the integral over the all the layers is nevertheless 1 and maybe
  the original values have been choosen to get rounded but close to the functions results values."
  [m i & {:keys [z] :or {z 0.05}}]
  {:doc/origin "R. Koitzsch, Zeitschrift für Meteorologie Band 27 Heft 5,
   Schätzung der Bodenfeuchte mit einem Mehrschichtenmodell, equation (9)"}
  (/ (- (* (+ z 1) (Math/log (/ (+ (* m z) i)
                                (+ (* m z) i -1))))
        (/ 1 m))
     (- (* (+ z 1) (Math/log (/ (+ z 1)
                                z)))
        1)))

(defn covered-water-abstraction-fraction
  "Get fraction of water-abstraction on plant covered soil for given layer i when using maximal n equal sized layers."
  [n i]
  {:doc/origin "R. Koitzsch, Zeitschrift für Meteorologie Band 27 Heft 5,
   Schätzung der Bodenfeuchte mit einem Mehrschichtenmodell, equation (6)"}
  (/ (- 2 (/ (- (* 2 i) 1)
             n))
     n))

(defn complement-layers [no-of-layers with-value incomplete-layers]
  (take no-of-layers (concat incomplete-layers (repeat with-value))))

(defn f1-koitzsch
  "returns a sequence of soil-depth-cm layers with water-abstraction values for covered or uncovered case
  for the given maximum depth in cm"
  [max-depth-cm covered?]
  (let [f (if covered?
            covered-water-abstraction-fraction
            uncovered-water-abstraction-fraction)
        upper-values (for [i (range 1 (inc max-depth-cm))]
                       (f max-depth-cm i))]
    (complement-layers (max-soil-depth) 0 upper-values)))

(defn gi-koitzsch
  "calculate water abstraction for a given maximum extraction depth and the give
  reduction factors
  note: code still contains the uncovered case, but that code has actually moved
  one level up to the caller of gi-koitzsch, because the extraction depth doesn't make
  much sense for uncovered soil and has been originally defined to be 60cm, which is
  constant throughout the program, whereas in the uncovered case the there is a maximum
  rooting (extraction) depth which can be fully used, but will only be so, if
  given the reduction factors applied to the layers gives the the largest abstraction, else
  shallower extraction depth will be used"
  [extraction-depth-cm reduction-factors & {:keys [covered?] :or {covered true}}]
  (let [;we search within the layers above and equal to extraction-depth-cm
        search-layer-depths (take-while (partial >= extraction-depth-cm) (layer-depths))
        ;create a list of [f1-koitzsch-result] for every possible layer depth in use
        ffss (for [max-depth search-layer-depths]
              (->> (f1-koitzsch max-depth true)
                   (aggregate-layers + *layer-sizes* ,,,)))
        ;calculate the gj and store the sum of all layers to find out the maximum later
        gis (map (fn [depth-cm ffs]
                   (let [gj (bu/dot-mult reduction-factors ffs)
                         rij (bu/sum gj)]
                     {:depth-cm depth-cm, :rij rij, :gj gj})) search-layer-depths ffss)
        ;sort gis according to ascending rij and then descending depth-cm
        gis-sorted (sort-by identity
                            (fn [{l-rij :rij, l-depth :depth-cm} {r-rij :rij, r-depth :depth-cm}]
                              (if (= l-rij r-rij) (> l-depth r-depth) (> l-rij r-rij)))
                            gis)]
    ;just use values only below 50cm, take the first one and extract the gj and complement with zeros below extraction depth
    (->> gis-sorted
         first
         :gj
         (complement-layers (no-of-soil-layers) 0 ,,,))))

(defn capillary-rise-barrier
  "calculate capillary rise barrier for a layer with size x-dm based on the field capacity of a x-dm layer"
  [fc-xdm layer-size-dm]
  (let [fc-1dm (/ fc-xdm layer-size-dm)]
    (* (+ fc-1dm 40.667 (- (* fc-1dm 0.408)))
       layer-size-dm)))

(defn infiltration-barrier [fk pwp abs-current-day layer-depth-cm]
  (let [barriers (if (<= layer-depth-cm 30)
                   (sorted-map 80 11, 140 7)
                   (sorted-map 100 10, 200 8))
        vs (* (interpolated-value barriers abs-current-day) 0.1)
        vs* (let [pwfk (+ (/ pwp fk) -1 vs)]
              (if (and (< vs 1.) (pos? pwfk))
                (+ vs (/ (* (- 1 vs) pwfk 0.95)
                         (- 0.66 0.3)))
                vs))]
    (+ (* (nFC fk pwp) vs*) pwp)))

(defn pwp4p [fc pwp] (- pwp (* 0.04 (nFC fc pwp))))

(defn uncovered-reduction-factors [fcs pwps soil-moistures]
  (map (fn [fc pwp sm]
         (let [pwp4p* (pwp4p fc pwp)
               r (if (> sm pwp4p*)
                   (min (/ (- sm pwp4p*) (- fc pwp4p*)) 1)
                   0)]
           (* r r)))
       fcs pwps soil-moistures))

(defn covered-reduction-factors [extraction-depth-cm fcs pwps soil-moisture pet]
  (map (fn [layer-size-cm depth-cm fc pwp sm]
         (let [pwp4p* (pwp4p fc pwp)]
           (if (and (<= depth-cm extraction-depth-cm) (< sm fc))
             (let [nfc (nFC fc pwp)
                   fc-dm (/ fc layer-size-cm 10)
                   xsm (if (or (< fc-dm 10) (> fc-dm 46))
                         (+ (* nfc 0.81) pwp)
                         (+ (* (+ 67.77
                                  (* 3.14 fc-dm)
                                  (* -0.2806 fc-dm fc-dm)
                                  (* 0.008131 fc-dm fc-dm fc-dm)
                                  (* -0.0000735 fc-dm fc-dm fc-dm fc-dm))
                               nfc 0.01)
                            pwp))
                   rk (if (> pet 3)
                        (* (- fc xsm) 0.0666)
                        (* (- xsm pwp) 0.3333))
                   sm-crit (+ xsm (* (- pet 3) rk))]
               (if (< sm sm-crit)
                 (let [pwp12p (- pwp (* 0.12 nfc))]
                   (cond
                    (< pwp sm) (/ (- sm pwp12p)
                                  (- sm-crit pwp12p))
                    (< pwp4p* sm) (let [sreb 3] ; { sreb=(pwp-pw7)/(pwp-pw9)=3.0 }
                                    (* (/ (- sm pwp4p*)
                                          (- sm-crit pwp12p))
                                       sreb))
                    :else 0))
                 1))
             1)))
       *layer-sizes* (layer-depths) fcs pwps soil-moisture))

(defn sm-1-day [extraction-depth-cm cover-degree pet abs-current-day
                fcs pwps lambdas soil-moistures soil-moisture-prognosis?
                evaporation ivd groundwater-level daily-precipitation-and-irrigation]
  (let [sms+surface-water (concat [(+ daily-precipitation-and-irrigation (first soil-moistures))]
                                  (rest soil-moistures))

        ;for at least partly uncovered ground
        water-abstractions (if (>= cover-degree 99/100)
                             (repeat (no-of-soil-layers) 0)
                             ;calculate same as gi-koitzsch for a depth of 60cm but uncovered soil
                             (->> (f1-koitzsch 60 false)
                                  (aggregate-layers + *layer-sizes* ,,,)
                                  (bu/s-mult evaporation
                                             (uncovered-reduction-factors fcs pwps sms+surface-water)
                                             ,,,)))

        ;for at least partly covered ground
        [aet, water-abstractions*]
        (if (<= cover-degree 1/100)
          [0, water-abstractions]
          (let [extraction-depth-cm* (if soil-moisture-prognosis?
                                       (min extraction-depth-cm 60)
                                       extraction-depth-cm)
                rfs (if (> cover-degree 1/100)
                      (covered-reduction-factors extraction-depth-cm fcs pwps sms+surface-water pet)
                      (repeat (no-of-soil-layers) 1))
                gi (gi-koitzsch extraction-depth-cm* rfs)]
            [(bu/sum (bu/s-mult pet gi)),
             (bu/dot-add (bu/s-mult (- 1 cover-degree) water-abstractions)
                         (bu/s-mult (* pet cover-degree) gi))]))

        {groundwater-infiltration :infiltration-into-next-layer
         sms* :soil-moistures}
        (->> soil-moistures
             ;combine a few more needed inputs to infiltration calculation
             (map vector lambdas water-abstractions* *layer-sizes* (layer-depths) fcs pwps ,,,)
             ;calculate the infiltration top down layer by layer, transporting excess water down
             ;and building up the soil layers as we go down
             (reduce (fn [{infiltration-from-prev-layer :infiltration-into-next-layer
                           sms :soil-moistures}
                          [lambda water-abstraction layer-size-cm depth-cm fc pwp sm]]
                       (let [cr-barrier (capillary-rise-barrier fc (/ layer-size-cm 10))]
                         (if (<= depth-cm groundwater-level)
                           (let [;above that barrier the water will start to infiltrate to the next layer
                                 inf-barrier (infiltration-barrier fc pwp abs-current-day depth-cm)

                                 ;in the next steps we basically care just about the excess water
                                 ;not about the layers full water content, so we calculate
                                 ;just the difference (positive or negative) to the infiltration barrier
                                 ;(in case of the first layer, possibly including todays precipitation)
                                 initial-excess-water (- sm inf-barrier)

                                 net-infiltration-from-above-layer (- infiltration-from-prev-layer water-abstraction)

                                 ;the excess water after calculation of the infiltration to the next layer
                                 {:keys [final-excess-water
                                         infiltration-into-next-layer]}
                                 (glugla* initial-excess-water net-infiltration-from-above-layer lambda)

                                 ;add the (positive/negative) excess water back to the infiltration barrier
                                 ;to obtain the actual water content now in this layer
                                 ;(after water could infiltrate to the next layer)
                                 sm* (max (+ inf-barrier final-excess-water) (pwp4p fc pwp))]
                             {:infiltration-into-next-layer infiltration-into-next-layer,
                              :soil-moistures (conj sms sm*)})
                           {:infiltration-into-next-layer 0,
                            :soil-moistures cr-barrier})))
                     {:infiltration-into-next-layer daily-precipitation-and-irrigation
                      :soil-moistures []}
                     ,,,))

        ;calculate the capillary rise if applicable
        [_ soil-moistures*] (->> sms*
                                 ;combine soil-moistures with fc and *layer-sizes* for reduce function below
                                 (map vector fcs *layer-sizes* ,,,)
                                 ;reverse, to go from bottom to top layer
                                 reverse
                                 ;transport excess water from lower layers to top layers
                                 (reduce (fn [[excess-water sms] [fc layer-size-cm sm]]
                                           (let [cr-barrier (capillary-rise-barrier fc (/ layer-size-cm 10))
                                                 sm* (+ sm excess-water)
                                                 excess-water* (max 0 (- sm* cr-barrier))]
                                             [excess-water* (cons (- sm* excess-water*) sms)]))
                                         [0 '()]
                                         ,,,))]
    {:aet aet
     :soil-moistures soil-moistures*
     :groundwater-infiltration groundwater-infiltration}))

(defn calc-soil-moisture [{:keys [qu-sum-deficits qu-sum-targets soil-moistures]
                           :as result-accumulator}
                          {:keys [abs-day rel-dc-day crop irrigation-amount
                                  evaporation precipitation irrigation-mode
                                  cover-degree qu-target rounded-extraction-depth-cm
                                  transpiration-factor
                                  fcs pwps lambdas groundwaterlevel-cm damage-compaction-depth-cm
                                  sm-prognosis?]
                           :as input}]
  (let [{:keys [pet
                effective-precipitation
                effective-irrigation
                effective-irrigation-uncovered]}
        (interception precipitation evaporation irrigation-amount
                      transpiration-factor irrigation-mode)

        pet* (if (< cover-degree 1/1000)
               0
               (* pet transpiration-factor))

        daily-precipitation-and-irrigation
        (+ (* (+ effective-precipitation effective-irrigation) cover-degree)
           (* (+ precipitation effective-irrigation-uncovered) (- 1 cover-degree)))

        {aet :aet
         soil-moistures* :soil-moistures
         groundwater-infiltration :groundwater-infiltration}
        (sm-1-day rounded-extraction-depth-cm cover-degree pet*
                  abs-day fcs pwps lambdas soil-moistures
                  sm-prognosis? evaporation damage-compaction-depth-cm
                  groundwaterlevel-cm daily-precipitation-and-irrigation)

        aet7pet (cond
                 (< cover-degree 1/1000) 0
                 (> pet* 1/100) (/ aet pet*)
                 :else 1)]

    {:abs-day abs-day
     :rel-dc-day rel-dc-day
     :irrigation-amount irrigation-amount
     :effective-precipitation effective-precipitation
     :effective-irrigation effective-irrigation
     :effective-irrigation-uncovered effective-irrigation-uncovered
     :aet aet
     :pet pet*
     :aet7pet aet7pet
     :qu-target qu-target
     :qu-deficit (if (< aet7pet qu-target)
                   (- qu-target aet7pet)
                   0)
     :qu-sum-deficits (if (< aet7pet qu-target)
                        (+ qu-sum-deficits (- qu-target aet7pet))
                        qu-sum-deficits)
     :qu-sum-targets (+ qu-sum-targets qu-target)
     :soil-moistures soil-moistures*
     :groundwater-infiltration groundwater-infiltration}))

(defn dc-to-abs+rel-dc-day-from-crop-instance-dc-assertions
  "create a dc to abs+rel-dc-day map from the data given with crop-instance
  (dc-assertions and the crop template, thus the dc-to-rel-dc-day curve)"
  [crop-instance]
  (let? [dc-assertions* (->> crop-instance
                             :crop-instance/dc-assertions
                             (sort-by :assertion/at-abs-day ,,,))
         :is-not empty?
         ;_ (println dc-assertions*)

         sorted-dc-to-rel-dc-days (->> crop-instance
                                       :crop-instance/template
                                       :crop/dc-to-rel-dc-days
                                       (into (sorted-map) ,,,))
         ;_ (println sorted-dc-to-rel-dc-days)

         {dc* :assertion/assert-dc
          abs-dc-day* :assertion/abs-assert-dc-day} (first dc-assertions*)
         :else nil
         ;_ (println "dc-to-rel-dc-days: " dc-to-rel-dc-days \newline)

         rel-dc-day* (interpolated-value sorted-dc-to-rel-dc-days dc*)
         sorted-dc-to-rel-dc-days* (assoc sorted-dc-to-rel-dc-days dc* rel-dc-day*)

         sorted-initial-dc-to-abs+rel-dc-day
         (fmap (fn [rel-dc-day]
                 {:abs-dc-day (+ abs-dc-day* (- rel-dc-day rel-dc-day*))
                  :rel-dc-day rel-dc-day})
               sorted-dc-to-rel-dc-days*)
         ;_ (println "initial: ")
         ;_ (pp/pprint sorted-initial-dc-to-abs+rel-dc-day)
         ]

        (reduce (fn [sorted-dc-to-days {dc* :assertion/assert-dc
                                        abs-dc-day* :assertion/abs-assert-dc-day}]
                  (let [;get a new days pair, either from map (should be the only case) or interpolate new
                        {:keys [abs-dc-day rel-dc-day]} (fmap (|-> bu/round int)
                                                              (or (get sorted-dc-to-days dc*)
                                                                  (interpolated-value sorted-dc-to-days dc*)))
                        ;_ (println "abs-dc-day: " abs-dc-day " rel-dc-day: " rel-dc-day)

                        ;that many days the assertion shifts the reported dc state
                        new-delta (- abs-dc-day* abs-dc-day)
                        ;_ (println "dc*: " dc* " abs-dc-day*: " abs-dc-day* " new-delta: " new-delta)

                        ;directly store the asserted dc state, because that's what the user reported
                        sorted-dc-to-days* (assoc sorted-dc-to-days dc* {:abs-dc-day abs-dc-day*
                                                                         :rel-dc-day (+ rel-dc-day new-delta)})

                        ;apply the new delta to all future dc-to-days pairs, because the future
                        ;(regarding the asserted abs-day of an dc state) represents just average possible
                        ;time ranges for the dcs
                        ;also if a past dc (regarding the assert dc*) has due to the new delta
                        ;a larger abs-dc-day, this mapping will be removed
                        sorted-dc-to-days** (->> sorted-dc-to-days*
                                                 (map (fn [[dc {:keys [abs-dc-day rel-dc-day]} :as key-value]]
                                                        ;move everyting after assert dc* by new-delta days
                                                        (if (> dc dc*)
                                                          [dc {:abs-dc-day (+ abs-dc-day new-delta)
                                                               :rel-dc-day (+ rel-dc-day new-delta)}]
                                                          ;if there's an assertion before that has due to new-delta
                                                          ;a larger abs-dc-day than the assert abs-dc-day*
                                                          ;remove (skip) this assertion
                                                          (when (<= abs-dc-day abs-dc-day*)
                                                            key-value)))
                                                      ,,,)
                                                 (into (sorted-map) ,,,))
                        ;_ (println "sorted-dc-to-days**")
                        ;_ (pp/pprint sorted-dc-to-days**)
                        ]
                    sorted-dc-to-days**))
                sorted-initial-dc-to-abs+rel-dc-day (rest dc-assertions*))))

(defn dc-to-abs+rel-dc-day-from-plot-dc-assertions
  "create a dc to abs-dc-day map for all crops in sequence of dc-assertions"
  [crop-instances]
  (->> crop-instances
       (map (fn [crop-instance]
              [crop-instance
               (dc-to-abs+rel-dc-day-from-crop-instance-dc-assertions crop-instance)])
            ,,,)
       (into {} ,,,)))

(defn- index-localized-crop-instance-curves-by-abs-dc-day
  "transform result to map like form indexed by abs-dc-day for sorting and processing"
  [localized-crop-instance-curves]
  (map (fn [[crop-instance dc-map]]
         (map (fn [[dc {:keys [abs-dc-day rel-dc-day]}]]
                [abs-dc-day {:dc dc
                             :rel-dc-day rel-dc-day
                             :crop-instance crop-instance}])
              dc-map))
       localized-crop-instance-curves))

(defn- merge-abs-dc-day-to-crop-data-maps
  "merge all crops/abs-dc-days as pre-stage to get final map with correct order in year"
  [abs-dc-day-to-crop-data]
  (reduce (fn [m crop-map]
            (into m
                  (for [[abs-dc-day data-map] crop-map]
                    (assoc m abs-dc-day
                      (if-let [val (get m abs-dc-day)]
                        (if (vector? val)
                          (conj val data-map)
                          [val data-map])
                        data-map)))))
          (sorted-map) abs-dc-day-to-crop-data))

(defn- calculate-final-abs-dc-to-crop-data-map
  "calculate order given a list of merged abs-dc-day-to-crop-data"
  [merged-abs-dc-day-to-crop-data-maps]
  (->> merged-abs-dc-day-to-crop-data-maps
       (reduce (fn [{:keys [m last-crop-instance crop-canceled?]}
                    [abs-dc-day data-map?s]]
                 (let [v? (vector? data-map?s)
                       lci (if last-crop-instance
                             last-crop-instance
                             ;if is vector simply take first crop-instance, other decision could be implemented
                             (:crop-instance ((if v? first identity) data-map?s)))]
                   (if v?
                     ;we got a cancelation of the current crop, but have to decide which
                     ;to choose now
                     (->> data-map?s
                          ;ignore the data belong to last crop instance
                          (filter #(not= lci (:crop-instance %)) ,,,)
                          ;for now simply take first from list (other decision could be implemented)
                          first
                          ;create mapping to data for current abs-dc-day
                          (assoc m abs-dc-day ,,,)
                          ;create accumulator for next reduce call
                          (#(hash-map :m %
                                      :last-crop-instance (:crop-instance %)
                                      :crop-canceled? true) ,,,))
                     ;ignore crop/crop-data if its from previous crop
                     ;and only if crop has been canceled before by other crop
                     {:m (if (or (= lci (:crop-instance data-map?s))
                                 (not crop-canceled?))
                           (assoc m abs-dc-day data-map?s)
                           m)
                      :last-crop-instance lci
                      :crop-canceled? crop-canceled?})))
               {:m (sorted-map)
                :last-crop-instance nil
                :crop-canceled? false} ,,,)
       :m))

(defn abs-dc-day->crop-instance
  "given an abs-dc-day return the crop-instance being used and
  the rel-dc-day for this crop-instance:
  assumes that
  a) dc = 1 means seeding, if there's no dc = 1, then is a winter crop, except
  if the crop has a previous crop, then a dc > 1 means the crop stop breaks the previous crop
  b) there will always be a harvesting (= last dc) step, thus after this step there
  is always fallow unless another crop follows with a dc > 1 (see a)"
  [fallow abs-dc-day-to-crop-instance-data abs-dc-day]
  (let [fallow* {:dc 0
                 :rel-dc-day 1
                 :crop fallow}

        ;first-abs-dc-day (ffirst abs-dc-day-to-crop-instance-data)
        ]
    (if-let [{dc :dc
              rel-dc-day :rel-dc-day
              {crop :crop-instance/template} :crop-instance}
             (get abs-dc-day-to-crop-instance-data abs-dc-day)]
      {:dc dc
       :rel-dc-day rel-dc-day
       :crop crop}
      (let [{[l-abs-dc-day
              {l-dc :dc
               l-rel-dc-day :rel-dc-day
               {l-crop :crop-instance/template} :crop-instance
               :as lower}] :lower
             [u-abs-dc-day
              {u-dc :dc
               u-rel-dc-day :rel-dc-day
               {u-crop :crop-instance/template} :crop-instance
               :as upper}] :upper}
            (adjacent-kv-pairs abs-dc-day-to-crop-instance-data abs-dc-day)]
        (cond
         ;before summer crop, just fallow, but before winter crop = winter crop
         (and (not lower) upper) (if (> u-dc 1)
                                   {:dc u-dc
                                    :rel-dc-day u-rel-dc-day
                                    :crop u-crop}
                                   fallow*)
         ;after last crop just fallow
         (and lower (not upper)) fallow*
         (and lower upper) (if
                             ;if next=prev crop, just interpolate
                             (or (= l-crop u-crop)
                                 ;if next crop is a different crop and next crop has
                                 ;a dc > 1, thus already after seeding
                                 ;keep on doing the previous crop, until the next crop
                                 ;comes, because the next crop will stop break the previous
                                 ;crop
                                 (and (not= l-crop u-crop)
                                      (> u-dc 1)))
                             {:dc l-dc
                              :rel-dc-day (+ l-rel-dc-day (- abs-dc-day l-abs-dc-day))
                              :crop l-crop}
                             ;if next crop is a different crop, fallow if
                             ;the next crop's dc is 1 => there new crop will start
                             fallow*)
         :else nil)))))

#_(deftest test-abs-dc-day->crop-instance
    (is (= )))

(defnk base-input-seq
  "create a input sequence for soil-moisture calculations
  - takes into account dc assertions which are available in plot map
  - lazy sequence as long as weather is available"
  [plot sorted-weather-map irrigation-donations irrigation-mode]
  (let [abs-dc-day-to-crop-instance-data
        (->> (:plot/crop-instances plot)
             dc-to-abs+rel-dc-day-from-plot-dc-assertions
             index-localized-crop-instance-curves-by-abs-dc-day
             merge-abs-dc-day-to-crop-data-maps
             calculate-final-abs-dc-to-crop-data-map)]
    (for [abs-day (range 1 (-> sorted-weather-map rseq ffirst inc))
          :let [weather (sorted-weather-map abs-day)]
          :while weather]
      (let [{:keys [dc rel-dc-day crop]}
            (abs-dc-day->crop-instance (:fallow plot) abs-dc-day-to-crop-instance-data abs-day)

            prev-day-cover-degree (interpolated-value (:crop/rel-dc-day-to-cover-degrees crop)
                                                      (dec rel-dc-day))

            cover-degree (interpolated-value (:crop/rel-dc-day-to-cover-degrees crop) rel-dc-day)]

        {:dc dc
         :abs-day abs-day
         :rel-dc-day rel-dc-day
         :crop crop
         :irrigation-amount (donations-at irrigation-donations abs-day)
         :tavg (bu/round (:tavg weather) :digits 1)
         :globrad (bu/round (:globrad weather) :digits 1)
         :evaporation (bu/round (:evaporation weather) :digits 1)
         :precipitation (bu/round (:precipitation weather) :digits 1)
         :irrigation-mode irrigation-mode
         :cover-degree cover-degree
         :qu-target (bu/round
                     (if (< prev-day-cover-degree 1/100)
                       0
                       (interpolated-value (:crop/rel-dc-day-to-quotient-aet-pets crop) rel-dc-day))
                     :digits 3)
         :rounded-extraction-depth-cm (->> (if (<= cover-degree 1/1000)
                                             0
                                             (interpolated-value (:crop/rel-dc-day-to-extraction-depths crop) rel-dc-day))
                                           (+ 1 ,,,)
                                           (bh/swap / 10 ,,,)
                                           nt/round
                                           (* 10 ,,,))
         :transpiration-factor (interpolated-value (:crop/rel-dc-day-to-transpiration-factors crop) rel-dc-day)
         :fcs (:plot/field-capacities plot)
         :pwps (:plot/permanent-wilting-points plot)
         :lambdas (lambda (:lambda-without-correction plot) abs-day)
         :groundwaterlevel-cm (:plot/groundwaterlevel plot)
         :damage-compaction-depth-cm (resulting-damage-compaction-depth-cm plot)
         :sm-prognosis? false}))))

(defnk create-input-seq
  "create the input sequence for all the other functions"
  [plot sorted-weather-map until-abs-day :irrigation-donations [] :irrigation-mode :sprinkle-losses]
  (->> (base-input-seq plot
                       sorted-weather-map
                       irrigation-donations
                       irrigation-mode)
       (drop (dec (:plot/abs-day-of-initial-soil-moisture-measurement plot)) ,,,)
       (take-while #(<= (:abs-day %) until-abs-day) ,,,)))

(defnk calc-soil-moistures*
  "calculate the soil-moistures for the given inputs and initial soil-moisture returning
  all intermediate steps, unless red-fn is defined to be reduce"
  [inputs initial-soil-moistures :red-fn reductions]
  (red-fn calc-soil-moisture
          {:qu-sum-deficits 0
           :qu-sum-targets 0
           :soil-moistures initial-soil-moistures}
          inputs))

(defnk calc-soil-moistures
  "calculate the soil-moistures for the given inputs and initial soil-moisture"
  [inputs initial-soil-moistures]
  (calc-soil-moistures* inputs initial-soil-moistures :red-fn reduce))

(defn average-prognosis-result
  "averages the results returned from calc-soil-moisture for prognosis calculation"
  [no-of-days {:keys [qu-sum-deficits qu-sum-targets soil-moistures] :as m}]
  {:input m
   :qu-avg-current (/ (- qu-sum-targets qu-sum-deficits)
                      no-of-days)
   :qu-avg-target (/ qu-sum-targets
                     no-of-days)
   :soil-moistures soil-moistures})

(defnk- calc-soil-moisture-prognosis**
  "the common part of the prognosis calculations, no matter if done by reduce or reductions"
  [prognosis-days inputs soil-moistures :red-fn reductions]
  (->> inputs
       ;if we got more input, take just the prognosis days
       (take prognosis-days ,,,)
       ;turn on prognosis mode
       (map #(assoc % :sm-prognosis? true) ,,,)
       ;calc soil-moistures (either with intermediate values or just last result)
       (red-fn calc-soil-moisture
               {:qu-sum-deficits 0
                :qu-sum-targets 0
                :soil-moistures soil-moistures}
               ,,,)))

(defnk calc-soil-moisture-prognosis*
  "calculate the soil-moisture prognosis but returning a list of intermediate results"
  [prognosis-days inputs soil-moistures]
  (->> (calc-soil-moisture-prognosis** prognosis-days inputs soil-moistures :red-fn reductions)
       ;drop first initial value from the reductions function
       rest
       ;calculate averages of result(s)
       (map-indexed (fn [i v] (average-prognosis-result (inc i) v)) ,,,)))

(defnk calc-soil-moisture-prognosis
  "calculate the soil-moisture prognosis in prognosis-days using inputs and the given soil-moisture"
  [prognosis-days inputs soil-moistures]
  (->> (calc-soil-moisture-prognosis** prognosis-days inputs soil-moistures :red-fn reduce)
       ;average the returned single result
       (average-prognosis-result prognosis-days ,,,)))

(def recommendation-actions #{:dont-irrigate :irrigate :check-again})

(def recommendation-states
  {:outside-of-irrigation-period {:action :dont-irrigate
                                  :text-de "Entw/Zeitr"}
   :check-back-soon {:action :check-again
                     :no-of-days 4
                     :text-de "in ca 4 Tg"}
   :increase-technological-max-donation {:action :dont-irrigate
                                         :text-de "S.K. erh."}
   :optimal-donation {:action :irrigate
                      :text-de "Gabe opt."}
   :optimal-soil-moisture {:action :dont-irrigate
                           :text-de "Bf opt."}
   :high-soil-moisture {:action :dont-irrigate
                        :text-de "Bf hoch"}
   :below-technological-minimum {:action :dont-irrigate
                                 :text-de "Tech.min"}})

(defn calc-donation-amount
  "calculate the irrigation amount to be given and the according recommendation text"
  [qu-prognosis-target
   {:keys [min-boundary-donation opt-boundary-donation max-boundary-donation
           step-size cycle-days]}
   inputs soil-moistures]
  (let [input (first inputs)

        ;look that many days into the future
        ;should be as many days as cycle days to the technological requirements
        ;but at least 4 days to reflect a valid prognosis time frame
        ;(general prognosis time frame is 5 days)
        irrigation-prognosis-days (max 4 (min cycle-days 14))

        ;a reduced quotient depending on crop specifics to have a lower border
        ;because water stress doesn't impact evey crop the same at the
        ;standard (per crop) target quotient
        qu-eff (- qu-prognosis-target (-> input :crop :crop/effectivity-quotient))

        ;function to calculate the soil-moistures by applying donation amount x at the first day
        ;of a period of length irrigation-prognosis-days
        calc-sms-with-donation (fn [irrigation-amount]
                                 (->> inputs
                                      (take irrigation-prognosis-days ,,,)
                                      ;set irrigation amount just for first day (of all the irrigation days)
                                      ((fn [[f-input & rest-input]] (cons (assoc f-input :irrigation-amount irrigation-amount) rest-input)) ,,,)
                                      ;set sm-prognosis true for all inputs
                                      (map #(assoc % :sm-prognosis? true) ,,,)
                                      ;calculate soil-moisture for the irrigation days
                                      (reduce calc-soil-moisture
                                              {:qu-sum-deficits 0
                                               :qu-sum-targets 0
                                               :soil-moistures soil-moistures}
                                              ,,,)
                                      ;get the average of the irrigation days
                                      (average-prognosis-result irrigation-prognosis-days ,,,)))

        ;calculate soil-moisture in given future time without any irrigation as base value
        {qu-0-current :qu-avg-current
         qu-0-target :qu-avg-target
         :as qu-avg} (calc-sms-with-donation 0)

        _ (debug "irr-days: " irrigation-prognosis-days " qu-eff: " qu-eff " qu-0-current: " qu-0-current " qu-0-target: " qu-0-target)

        ]
    (if (< qu-0-current qu-0-target)
      ;without irrigation we're below the target curve
      (if (< qu-eff qu-0-current)
        ;but we're above the effective curve, thus try again in about 4 days
        {:state :check-back-soon}
        ;nope, we've got to irrigate
        (let [make-donation #(hash-map :irrigation/abs-day (:abs-day input) #_(inc (:abs-day input))
                                       :irrigation/amount %)

              qu-target*-fn (fn [qu-target]
                              ;(/ (+ qu-eff qu-target) 2)
                              (/ (+ 1 qu-target) 2))

              _ (debug "qu-target*: " (qu-target*-fn qu-prognosis-target))

              calc-qus (|-> calc-sms-with-donation ;calc soil-moisture with a certain donation amount
                            ;extract qu-current and qu-target from result
                            (--< :qu-avg-current :qu-avg-target)
                            ;calculate qu-target* for the second element
                            (--< first (|-> second qu-target*-fn)))

              donations-- (take-while #(<= min-boundary-donation %)
                                      (iterate #(- % step-size) opt-boundary-donation))
              donations++ (take-while #(<= % max-boundary-donation)
                                      (iterate #(+ % step-size) opt-boundary-donation))

              ;depending on qu-current vs qu-target* when applying optimal donation we got from boundary conditions,
              ;choose branch to increase/or decrease amount
              [branch exit-condition] (let [[qu-current qu-target*] (calc-qus opt-boundary-donation)
                                            _ (debug "qu-current: " qu-current " qu-target*: " qu-target*)]
                                        (if (> qu-current qu-target*)
                                          [donations-- <] ;opt-- branch: exit if current <= target*
                                          [donations++ >])) ;opt++ branch: exit if current >= target*

              branch-qus (map (fn [donation] [donation (calc-qus donation)]) branch)
              ;drop all elements from branch which don't satisify condition
              branch-qus* (drop-while (|-> second
                                           ;we have to drop elements from branch until exit condition is true
                                           ;thus the complement of the exit-condition will be applied
                                           #(apply (complement exit-condition) %))
                                      branch-qus)

              [final-donation [final-qu-current final-qu-target]]
              (if (empty? branch-qus*)
                ;for some reason (e.g. technology or slope or soil doesn't allow more) the
                ;donation couldn't be increased until the qu-current gets larger than qu-target*,
                ;thus take the highest possible value
                (last branch-qus)
                ;take the first donation which got us higer qu-current than qu-target*
                (first branch-qus*))

              _ (debug "bqus: " branch-qus " bqus*: " branch-qus* " fd: " final-donation " queff:" qu-eff)
              ]
          {:donation final-donation
           ;final-donation = max-donation works because final-donation actually is taken
           ;from the donations+ seq and thus at maximum max-donation
           :state (if (and (= final-donation max-boundary-donation) (< final-qu-current qu-eff))
                    :increase-technological-max-donation
                    :optimal-donation)}))
      ;without irrigation we're above target curve, thus everything is fine
      {:state :optimal-soil-moisture})))

(defn calc-donation-boundaries
  "calculate the irrigation-water donation data given a soil-moisture and
  the technological restrictions"
  [forecast-days slope {opt-donation-technology :technology/opt-donation
                        donation-step-size :technology/donation-step-size
                        max-donation-technology :technology/max-donation
                        min-donation-technology :technology/min-donation}
   inputs soil-moistures]
  (let [{:keys [abs-day fcs pwps]} (first inputs)

        [max-donation-soil-30
         max-donation-soil-60] (->> (map vector (layer-depths) fcs pwps soil-moistures)
                                    ;create ["depths equal and below 30cm" , "rest"]
                                    (split-with #(<= (first %) 30) ,,,)
                                    (--<* first
                                          (|-> second
                                               ;finally get 30cm < depth <= 60cm
                                               (partial take-while #(<= (first %) 60)))
                                          ,,,)
                                    ;calc difference of inf-barrier and soil-moisture
                                    ;and sum up layers
                                    ;for both parts
                                    (map #(->> %
                                               (reduce (fn [sum [depth-cm fc pwp sm]]
                                                         (+ sum
                                                            (- (infiltration-barrier fc pwp abs-day depth-cm) sm)))
                                                       0 ,,,)
                                               double
                                               Math/round)
                                         ,,,))

        ;it's a little bit unclear, if that is supposed to check whether
        ;just soil-60 is negative and thus don't add it,
        ;but on the other side if soil-30 and -60 are negative, it will
        ;be added as well
        ;the question is, if that can happen at all, actually only if the
        ;infiltration barrier can get smaller than the soil-moisture in the layer
        ;for now I keep it that way, but should be fixed
        max-donation-soil+60 (+ max-donation-soil-30
                                (if (pos? (* max-donation-soil-30 max-donation-soil-60))
                                  max-donation-soil-60
                                  0))

        max-donation-soil+60+weather (->> inputs
                                          (reduce (fn [sum {:keys [evaporation precipitation]}]
                                                    (+ sum (- evaporation precipitation)))
                                                  max-donation-soil+60
                                                  ,,,)
                                          Math/round
                                          (max 0 ,,,))

        max-donation-slope (condp #(% %2) slope
                             #{1 2} 50
                             #{3} 40
                             #{4 5} 30
                             #{6} 20)

        max-possible-donation (min max-donation-technology max-donation-slope max-donation-soil+60+weather)



        ;get lowest maximal donation supported by technology
        max-possible-and-supported-donation (* donation-step-size (quot max-possible-donation donation-step-size))

        opt-donation (if (< max-possible-and-supported-donation opt-donation-technology)
                       (->> opt-donation-technology
                            (iterate #(- % donation-step-size) ,,,)
                            (drop-while #(< max-possible-and-supported-donation %) ,,,)
                            first)
                       opt-donation-technology)

        _ (debug [max-donation-technology max-donation-slope max-donation-soil+60+weather max-possible-and-supported-donation opt-donation])

        high-soil-moisture? (< max-donation-soil+60+weather 15)]

    {:high-soil-moisture? high-soil-moisture?
     :min-boundary-donation (if high-soil-moisture? 0 min-donation-technology)
     :opt-boundary-donation opt-donation
     :max-boundary-donation max-possible-and-supported-donation}))

(defn calc-recommendation
  "calculate the recommendation text and recommendation donation amount for the given input values"
  [prognosis-days slope technology inputs soil-moistures]
  (let [;get the boundaries depending on current soil-moisture, used technology and slope
        {:keys [high-soil-moisture?
                max-boundary-donation
                min-boundary-donation
                opt-boundary-donation]
         :as boundaries} (calc-donation-boundaries prognosis-days slope
                                                   technology inputs soil-moistures)

        technology+boundaries (-> boundaries
                                  (dissoc ,,, :high-soil-moisture?)
                                  (assoc ,,,
                                    :cycle-days (:technology/cycle-days technology)
                                    :step-size (:technology/donation-step-size technology)))

        {qu-prognosis-avg-current :qu-avg-current
         qu-prognosis-avg-target :qu-avg-target}
        (calc-soil-moisture-prognosis prognosis-days inputs soil-moistures)]
    (cond
     (< qu-prognosis-avg-current qu-prognosis-avg-target)
     (cond

      high-soil-moisture?
      {:state :high-soil-moisture}

      (>= max-boundary-donation min-boundary-donation)
      (do
        (debug "qu-prognosis-avg-current: " qu-prognosis-avg-current " < qu-prognosis-avg-target: " qu-prognosis-avg-target )
        (debug technology+boundaries)
        (calc-donation-amount qu-prognosis-avg-target technology+boundaries inputs soil-moistures)
        )

      :else
      {:state :below-technological-minimum})

     (< qu-prognosis-avg-target 1/10)
     {:state :outside-of-irrigation-period}

     :else
     {:state :optimal-soil-moisture})))

(defnk calculate-soil-moistures-by-auto-donations**
  "calculate soil-moistures but apply automatically the recommended donations"
  [inputs initial-soil-moistures slope technology prognosis-days :red-fn reduce]
  (red-fn (fn [{:keys [days-to-go current-sm]} [f-input :as prognosis-inputs]]
            (let [{:keys [days-to-go* donation]}
                  (if (zero? days-to-go)
                    (let [_ (debug "abs-day: " (:abs-day f-input))
                          {:keys [state donation] :as rec}
                          (calc-recommendation (count prognosis-inputs) slope technology
                                               prognosis-inputs (:soil-moistures current-sm))
                          _ (debug "state: " state " recommended donation: " donation)
                          ]
                      (case state
                        (:optimal-soil-moisture
                         :outside-of-irrigation-period
                         :below-technological-minimum
                         :high-soil-moisture) {:days-to-go* 1
                                               :donation 0}

                        (:check-back-soon) {:days-to-go* (-> recommendation-states
                                                             :check-back-soon
                                                             :no-of-days
                                                             #_inc)
                                            :donation 0}

                        (:increase-technological-max-donation
                         :optimal-donation) {:days-to-go* (:technology/cycle-days technology) #_prognosis-days
                                             :donation donation}))
                    {:days-to-go* days-to-go
                     :donation 0})

                  _ (debug "days-to-go*: " (dec days-to-go*) " final donation to apply: " donation)
                  ]
              {:days-to-go (dec days-to-go*)
               :current-sm (calc-soil-moisture current-sm (assoc f-input :irrigation-amount donation))}))
          {:days-to-go 0
           :current-sm {:qu-sum-deficits 0
                        :qu-sum-targets 0
                        :soil-moistures initial-soil-moistures}}
          (partition-all prognosis-days 1 inputs)))

(defnk calculate-soil-moistures-by-auto-donations
  [inputs initial-soil-moistures slope technology prognosis-days]
  (->> (calculate-soil-moistures-by-auto-donations** inputs initial-soil-moistures slope technology
                                                     prognosis-days :red-fn reduce)
       :current-sm))

(defnk calculate-soil-moistures-by-auto-donations*
  "calculate soil-moistures but apply automatically the recommended donations"
  [inputs initial-soil-moistures slope technology prognosis-days]
  (->> (calculate-soil-moistures-by-auto-donations** inputs initial-soil-moistures slope technology
                                                     prognosis-days :red-fn reductions)
       rest
       (map :current-sm ,,,)))

(defnk calc-soil-moistures*
  "calculate the soil-moistures for the given inputs and initial soil-moisture returning
  all intermediate steps, unless red-fn is defined to be reduce"
  [inputs initial-soil-moistures :red-fn reductions]
  (red-fn calc-soil-moisture
          {:qu-sum-deficits 0
           :qu-sum-targets 0
           :soil-moistures initial-soil-moistures}
          inputs))

(defnk calc-soil-moistures
  "calculate the soil-moistures for the given inputs and initial soil-moisture"
  [inputs initial-soil-moistures]
  (calc-soil-moistures* inputs initial-soil-moistures :red-fn reduce))


(defn create-csv-output [inputs full-reductions-results]
  (let [header-line ["doy"
                     "date"
                     "rel DC day"
                     "precip [mm]"
                     "tavg [°]"
                     "globrad [Jpcm2]"
                     "evap [mm]"
                     "irrWater [mm]"
                     "pet [mm]"
                     "aet [mm]"
                     "aet/pet"
                     "aet/pet soll"
                     "infil 200cm [mm]"
                     "sm 0-30cm [mm]"
                     "sm 30-60cm [mm]"
                     "sm 60-90cm [mm]"
                     "sm 5cm [mm]"
                     "sm 10cm [mm]"
                     "sm 20cm [mm]"
                     "sm 30cm [mm]"
                     "sm 40cm [mm]"
                     "sm 50cm [mm]"
                     "sm 60cm [mm]"
                     "sm 70cm [mm]"
                     "sm 80cm [mm]"
                     "sm 90cm [mm]"
                     "sm 100cm [mm]"
                     "sm 110cm [mm]"
                     "sm 120cm [mm]"
                     "sm 130cm [mm]"
                     "sm 140cm [mm]"
                     "sm 150cm [mm]"
                     "sm 160cm [mm]"
                     "sm 170cm [mm]"
                     "sm 180cm [mm]"
                     "sm 190cm [mm]"
                     "sm 200cm [mm]"
                     "sm 0-10cm [mm]"
                     "sm 10-20cm [mm]"
                     "sm 30-60cm [mm]"
                     "sm 60-100cm [mm]"
                     "sm 100-150cm [mm]"
                     "effective-precipitation [mm]"
                     "effective-irrigation [mm]"
                     "effective-irrigation-uncovered [mm]"
                     "cover-degree [%]"
                     "dc"
                     "rounded-extraction-depth [cm]"]

        body-lines (map (fn [input rres]
                          #_(println rres)
                          (map str [(:abs-day input)
                                    (ctf/unparse (ctf/formatter "dd.MM.")
                                                 (bu/doy-to-date (:abs-day input)))
                                    (:rel-dc-day input)
                                    (:precipitation input)
                                    (:tavg input)
                                    (:globrad input)
                                    (- (:evaporation input))
                                    (:irrigation-amount rres #_input)
                                    (:pet rres)
                                    (:aet rres)
                                    (:aet7pet rres)
                                    (:qu-target rres)
                                    (:groundwater-infiltration rres)
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 0 4))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 4 7))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 7 10))
                                    (nth (:soil-moistures rres) 0)
                                    (nth (:soil-moistures rres) 1)
                                    (nth (:soil-moistures rres) 2)
                                    (nth (:soil-moistures rres) 3)
                                    (nth (:soil-moistures rres) 4)
                                    (nth (:soil-moistures rres) 5)
                                    (nth (:soil-moistures rres) 6)
                                    (nth (:soil-moistures rres) 7)
                                    (nth (:soil-moistures rres) 8)
                                    (nth (:soil-moistures rres) 9)
                                    (nth (:soil-moistures rres) 10)
                                    (nth (:soil-moistures rres) 11)
                                    (nth (:soil-moistures rres) 12)
                                    (nth (:soil-moistures rres) 13)
                                    (nth (:soil-moistures rres) 14)
                                    (nth (:soil-moistures rres) 15)
                                    (nth (:soil-moistures rres) 16)
                                    (nth (:soil-moistures rres) 17)
                                    (nth (:soil-moistures rres) 18)
                                    (nth (:soil-moistures rres) 19)
                                    (nth (:soil-moistures rres) 20)
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 0 2))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 2 4))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 4 7))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 7 11))
                                    (bu/sum (subvec (vec (:soil-moistures rres)) 11 16))
                                    (:effective-precipitation rres)
                                    (:effective-irrigation rres)
                                    (:effective-irrigation-uncovered rres)
                                    (* (:cover-degree input) 100)
                                    (:dc input)
                                    (:rounded-extraction-depth-cm input)]))
                        inputs full-reductions-results)]
    (cons header-line body-lines)))

(defn run [db plot sorted-weather-map irrigation-donations-map until-abs-day irrigation-mode]
  (append-out out str (str (-> plot :plot/number str (subs ,,, 0 3)) "-"
                           (-> plot :plot/number str (subs ,,, 3 4)) " "
                           (-> plot :assertion/dc-assertions first :assertion/crop crop-id) " "
                           (-> plot :assertion/dc-assertions first :assertion/crop :symbol) "      "))

  (let [inputs (create-input-seq| :plot plot
                                  :sorted-weather-map sorted-weather-map
                                  :until-abs-day (+ until-abs-day 7)
                                  :irrigation-donations irrigation-donations-map
                                  :irrigation-mode irrigation-mode)
        _ (println "inputs:" \newline "----------------------")
        _ (pp/pprint inputs)
        _ (println "----------------------")

        inputs-7 (drop-last 7 inputs)
        prognosis-inputs (take-last 7 inputs)

        sms-7* (calc-soil-moistures* inputs-7 (:plot/initial-soil-moistures plot))
        _ (println "soil-moistures-7:" \newline "----------------------")
        _ (pp/pprint sms-7*)
        _ (println "----------------------")
        {soil-moistures-7 :soil-moistures
         :as sms-7} (last sms-7*)
        #_(calc-soil-moistures inputs-7 (:plot/initial-soil-moistures plot))

        prognosis* (calc-soil-moisture-prognosis* 7 prognosis-inputs soil-moistures-7)
        _ (println "prognosis:" \newline "----------------------")
        _ (pp/pprint prognosis*)
        _ (println "----------------------")
        prognosis (last prognosis*)
        #_(calc-soil-moisture-prognosis 7 prognosis-inputs soil-moistures-7)

        {:keys [recommendation-text recommended-donation-amount]
         :as rec} (calc-recommendation (:plot/slope plot) (:plot/technology plot)
                                       prognosis-inputs soil-moistures-7)
        _ (println "recommendation:" \newline "----------------------")
        _ (pp/pprint rec)
        _ (println "----------------------")
        ]
    (spit "out.csv" (csv/write-csv (create-csv-output inputs (concat sms-7* prognosis*))))))

(defn -main
  "main function for commandline use"
  [& args]

  (let [db (->> "berest"
                (str bd/datomic-base-uri ,,,)
                d/connect
                d/db)
        plot (db-read-plot db "0400" 2012)
        weather weather-map
        irrigation-donations-map (read-irrigation-donations db
                                                            (:plot/number plot)
                                                            (:plot/irrigation-area plot))
        weather+prognosis weather
        irrigation-mode :sprinkle-losses]
    (run plot weather irrigation-donations-map (bu/date-to-doy 29 5) irrigation-mode)))

