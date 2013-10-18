(ns berest-service.berest.parse-crop-files
  (:refer-clojure :exclude [char])
  (:use [the.parsatron])
  (:require [berest-service.berest.helper :as h]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bdt]))

;this should be parsed

;   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr

;   DC =    1,  10, 21;  Code
;           1,  15, 72;  Tag

;   NameDC =   1 : Aussaat;
;             10 : Aufgang;
;             21 : Best.-beginn;



;   Bedeckungsgrad   =   15,   30,  115;                    Tag
;                         0, 0.60, 0.80;                    Wert

;   Entnahmetiefe    =   10,  90;                           Tag
;                         1,   6;                           Wert

;   Transpiration    =    1;                                Tag
;                         1;                                Wert

;   Quotient(soll)   =    1;                                Tag
;                         0;                                Wert

;   Effektivitaet    =    1;                                Tag
;                      0.17;                                Wert


;   0101,7,2,dito.,;
;   0101,7,3,dito.,;
;   0101,7,9,dito.,;
;   * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -

(def eol-tokens #{\newline \return})
(def not-eol-tokens (complement eol-tokens))

(defn not-eol
  "Consume any character, but eol"
  []
  (token #(and (not-eol-tokens %) (char? %))))

(defn not-x
  "Consume any character, but given ones"
  [& not-chars]
  (let [eol+not-chars (if (empty? not-chars)
                        eol-tokens
                        (apply conj eol-tokens not-chars))]
    (token #(and ((complement eol+not-chars) %) (char? %)))))

(defparser not-eol* []
  (many (not-eol)))

(defparser ws []
  (char \space))

(defparser ws* []
  (many (ws)))

(defparser ws+ []
  (many1 (ws)))

(defparser tok [token-parser]
  (many (ws))
  token-parser)

(defparser literal [lit]
  (let->> [ok? (reduce nxt (map char lit))]
          (if ok?
            (always lit)
            (never))))

(defparser digits+ []
  (many1 (digit)))

(defparser parse-double []
  (let->> [ds (many1 (either (digit) (char \.)))]
          (always (->> ds
                    (apply str)
                    (Double/parseDouble)))))

(defparser parse-int [& no-of-digits]
  (let->> [ds (if (empty? no-of-digits)
                (digits+)
                (times (first no-of-digits) (digit)))]
          (always (->> ds
                    (apply str)
                    (Integer/parseInt)))))

(defparser letters+ []
  (many1 (letter)))

(defparser letters-with+ [& with-chars]
  (many1 (choice (letter)
                 (apply choice (map char with-chars)))))

(defparser letters-or-digits-with+ [& with-chars]
  (many1 (choice (letter)
                 (digit)
                 (apply choice (map char with-chars)))))

(defparser word-with [& with-chars]
  (let->> [ls (apply letters-or-digits-with+ with-chars)]
          (always (apply str ls))))

(defparser word-without [& without-chars]
  (let->> [ls (many1 (apply not-x without-chars))]
          (always (apply str ls))))

(defparser eol-marker []
  (either (char \newline) (char \return)))

(defparser eol []
  (let->> [eolm (eol-marker)]
          (either (eol-marker) (always :eol))))

#_(defparser comment->eol []
  (many (let->> [tok (either (lookahead (eol)) (any-char))]
                (if (= tok :eol)
                  (never)
                  (always tok)))))

;example
;old variant ;0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr
;0701,1,Kartoffeln;    Reifegruppe 1
(defparser id-line []
  (let->> [number (>> (ws*) (parse-int 4))
           cultivation-type (>> (char \,) (parse-int 1))
           #_usage #_(>> (char \,) (parse-int 1))
           #_symbol #_(>> (char \,) (word-with))
           name (>> (char \,) (word-with \/ \space \- \. \+))
           ;description (>> (char \;) (word-without))
           description (>> (char \;) (not-eol*))
           _ (many (eol))]
          (always {:crop/number number
                   :crop/cultivation-type cultivation-type
                   :crop/usage 0 #_usage
                   :crop/symbol name #_symbol
                   :crop/name name
                   :crop/description description})))

#_(defparser dito-line []
  (let->> [number (>> (ws*) (parse-int 4))
           cultivation-type (>> (char \,) (parse-int 1))
           usage (>> (char \,) (parse-int 1))
           _ (>> (literal ",dito.,;") (ws*) (many (eol)))]
          (always {:crop/number number
                   :crop/cultivation-type cultivation-type
                   :crop/usage usage})))

(defparser eol-comment []
  (many (either (eol) (any-char))))

(defparser ws-comma []
  (either (char \space) (char \,)))

(defparser parse-item [item-parser]
  (let->> [i (item-parser)
           _ (many (ws-comma))]
          (always i)))

(defparser parse-item-list [item-parser]
  (many1 (parse-item item-parser)))

;for example
;   DC =    1,  10, 21;  Code
;           1,  15, 72;  Tag
(defparser horizontal-map [name key-parser value-parser]
  (let->> [;first line
           keys (>> (ws*) (literal name) (ws*) (char \=) (ws*)
                    (parse-item-list key-parser))
           values (>> (char \;) (not-eol*) (eol)
                      ;second line
                      (ws*) (parse-item-list value-parser))
           _ (>> (not-eol*) (many (eol)))]
          ;(println name ": " (interleave keys values))
          (always (interleave keys values))))

;example
;   NameDC =   1 : Aussaat;
;             10 : Aufgang;
;             21 : Best.-beginn;
(defparser vmap-line [key-parser value-parser]
  (let->> [key (>> (ws*) (key-parser))
           value (>> (ws*) (char \:) (ws*) (value-parser))
           _ (>> (not-eol*) (eol))]
          (always [key value])))

(defparser vertical-map [name key-parser value-parser]
  (let->> [;first line
           kv1 (>> (ws*) (literal name) (ws*) (char \=) (ws*)
                   (vmap-line key-parser value-parser))
           ;second + x lines
           kv-pairs (many (vmap-line key-parser value-parser))]
          (always (flatten (cons kv1 kv-pairs)))))

(defparser empty-line []
  (>> (ws*) (eol)))

(defparser empty-line* []
  (many (empty-line)))

(defparser separator-line []
  (literal "* - * - ")
  (many1 (either (literal "*") (literal "* - ")))
  (not-eol*)
  (many (eol))
  (always true))

(defparser parse-section []
  (let->>
    [id (>> (empty-line*) (id-line))
     dc (>> (empty-line*) (horizontal-map "DC" parse-int parse-int))
     name (>> (empty-line*) (vertical-map "NameDC" parse-int (partial word-without \;)))
     cover-degree (>> (empty-line*) (horizontal-map "Bedeckungsgrad" parse-int parse-double))
     extraction-depth (>> (empty-line*) (horizontal-map "Entnahmetiefe" parse-int parse-int))
     transpiration (>> (empty-line*) (horizontal-map "Transpiration" parse-int parse-double))
     quotient (>> (empty-line*) (horizontal-map "Quotient(soll)" parse-int parse-double))
     effectivity (>> (empty-line*) (horizontal-map "Effektivitaet" parse-int parse-double))
     #_ditos #_(many (dito-line))
     _ (>> (empty-line*) (separator-line))]
    (let [dc-to-day (bdt/create-entities :kv/dc :kv/rel-dc-day dc)
          dc-to-name (bdt/create-entities :kv/dc :kv/name name)
          rel-day-to-cover-degree (bdt/create-entities :kv/rel-dc-day :kv/cover-degree
                                                       cover-degree)
          rel-day-to-extraction-depth (bdt/create-entities :kv/rel-dc-day :kv/extraction-depth
                                                           extraction-depth)
          rel-day-to-transpiration-factor (bdt/create-entities :kv/rel-dc-day :kv/transpiration-factor
                                                               transpiration)
          rel-day-to-quotient (bdt/create-entities :kv/rel-dc-day :kv/quotient-aet-pet
                                                   quotient)
          to-4-digits (fn [s] (if (= (count (str s)) 4) s (str 0 s)))
          crop (assoc id
                      :db/id (bdt/new-entity-id)
                      :crop/id (str (to-4-digits (:crop/number id)) "/"
                                    (:crop/cultivation-type id) "/"
                                    (:crop/usage id))
                      :crop/dc-to-rel-dc-day (bdt/get-entity-ids dc-to-day)
                      :crop/dc-to-developmental-state-name (bdt/get-entity-ids dc-to-name)
                      :crop/rel-dc-day-to-cover-degree (bdt/get-entity-ids rel-day-to-cover-degree)
                      :crop/rel-dc-day-to-extraction-depth (bdt/get-entity-ids rel-day-to-extraction-depth)
                      :crop/rel-dc-day-to-transpiration-factor (bdt/get-entity-ids rel-day-to-transpiration-factor)
                      :crop/rel-dc-day-to-quotient-aet-pet (bdt/get-entity-ids rel-day-to-quotient)
                      :crop/effectivity-quotient (-> effectivity second))
          #_dito-crops #_(map #(-> crop
                             (merge ,,, %)
                             (assoc ,,, :db/id (bdt/new-entity-id)
                                        :crop/id (str (to-4-digits (:crop/number %)) "/"
                                                      (:crop/cultivation-type %) "/"
                                                      (:crop/usage %))))
                          ditos)]
      ;return as result of this parser a function which can be called to insert everything into datomic
      (always (fn [datomic-connection]
                (;d/transact datomic-connection
                  print
                  (flatten [dc-to-day
                            dc-to-name
                            rel-day-to-cover-degree
                            rel-day-to-extraction-depth
                            rel-day-to-transpiration-factor
                            rel-day-to-quotient
                            crop
                            #_dito-crops])))))))

(defparser parse-file []
  (many1 (parse-section)))

(defn parse-files [files]
  (->> files
    (map #(do
            (println "parsing " %)
            (run (parse-file) (slurp %)))
         ,,,)
    flatten
    flatten))

(defn parse-Bbfastdx []
  (->> (for [i (range 1 8)]
         (str "C:/Users/michael/development/irrigation/berest-test/fastd/Bbfastd" i ".txt"))
    parse-files))


