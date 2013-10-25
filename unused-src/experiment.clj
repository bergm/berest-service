(ns berest.experiment
  #_(:use clojure.test
        clojure.pprint ; Amotoen doesn't require this, just the sample code below
        com.lithinos.amotoen.grammars.csv)
  (:refer-clojure :exclude [char])
  (:use [the.parsatron])
  (:require [berest.helper :as h]))

#_(deftest use-amotoen
  (pprint (to-clj "a,b,c")))

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
  (let [eol+not-chars (apply conj eol-tokens not-chars)]
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
                (times no-of-digits (digit)))]
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

(defparser eol []
  (either (char \newline) (char \return)))

;example
;   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr
(defparser id-line []
  (let->> [number (parse-int 4) 
           _ (char \,)
           cultivation-type (parse-int 1)
           _ (char \,)
           usage (parse-int 1)
           _ (char\,)
           symbol (word-with)
           _ (char \,)
           name (word-with \/)
           _ (char \;)
           description (word-with "/")
           _ (many (eol))]
          (always {:crop/number number
                   :crop/cultivation-type cultivation-type 
                   :crop/usage usage 
                   :crop/symbol symbol 
                   :crop/name name 
                   :crop/description description})))

(defparser dito-line []
  (let->> [number (parse-int 4) 
           _ (char \,)
           cultivation-type (parse-int 1)
           _ (char \,)
           usage (parse-int 1)
           _ (char\,)
           _ (literal "dito.")
           _ (char \,)
           _ (char \;)
           _ (ws*) 
           _ (many (eol))]
          (always {:crop/number number
                   :crop/cultivation-type cultivation-type
                   :crop/usage usage
                   :dito true})))

(defparser eol-comment []
  (many (either (eol) (any-char))))

(defparser ws-comma []
  (either (char \space) (char \,)))

(defparser parse-item [item-parser]
  (let->> [i (item-parser)
           _ (many (ws-comma))]
          (always i)))

(defparser parse-item-list [item-parser]
  (many (parse-item item-parser)))

;for example
;   DC =    1,  10, 21;  Code
;           1,  15, 72;  Tag
(defparser horizontal-map [name key-parser value-parser]
  (let->> [;first line
           _ (ws*)
           _ (literal name)
           _ (ws*)
           _ (char \=)
           _ (ws*)
           keys (parse-item-list key-parser)
           _ (char \;)
           _ (not-eol*) 
           _ (eol)
           
           ;second line
           _ (ws*)
           values (parse-item-list value-parser)
           _ (not-eol*)
           _ (many (eol))]
          (always (interleave keys values))))

;example
;   NameDC =   1 : Aussaat;
;             10 : Aufgang;
;             21 : Best.-beginn;
(defparser vmap-line [key-parser value-parser]
  (let->> [_ (ws*)
           key (key-parser)
           _ (ws*)
           _ (char \:)
           _ (ws*)
           value (value-parser)
           _ (not-eol*)
           _ (many (eol))]
          (always [key value])))

(defparser vertical-map [name key-parser value-parser]
  (let->> [;first line
           _ (ws*)
           _ (literal name)
           _ (ws*)
           _ (char \=)
           _ (ws*)
           kv1 (vmap-line key-parser value-parser) 

           ;second + x lines
           kv-pairs (many (vmap-line key-parser value-parser))]
          (always (flatten (cons kv1 kv-pairs)))))

(defparser empty-line []
  (nxt (ws*) (eol)))

(defparser empty-line* []
  (many (empty-line)))

(defparser separator-line []
  (literal "* - * - ")
  (many1 (either (literal "*") (literal "* - ")))
  (not-eol*)
  (many (eol))
  (always true))
 


(defparser parse-section []
  (let->> [id (id-line)
           _  (empty-line*)
           dc (horizontal-map "DC" parse-int parse-int)
           _ (empty-line*)
           name (vertical-map "NameDC" parse-int (partial word-without \;))
           _ (empty-line*)
           cover-degree (horizontal-map "Bedeckungsgrad" parse-int parse-double)
           _ (empty-line*)
           extraction-depth (horizontal-map "Entnahmetiefe" parse-int parse-int)
           _ (empty-line*)
           transpiration (horizontal-map "Transpiration" parse-int parse-double)
           _ (empty-line*)
           quotient (horizontal-map "Quotient(soll)" parse-int parse-double)
           _ (empty-line*)
           effectivity (horizontal-map "Effektivität" parse-int parse-double)
           _ (empty-line*)
           
           _ (separator-line)
           ]
          (let [dc-to-day (create-entities :kv/dc :kv/rel-dc-day dc)
                dc-to-name (create-entities :kv/dc :kv/name name)
                rel-day-to-cover-degree (create-entities :kv/rel-dc-day :kv/cover-degree cover-degree)
                rel-day-to-extraction-depth (create-entities :kv/rel-dc-day :kv/extraction-depth 
                                                             extraction-depth) 
                rel-day-to-transpiration-factor (create-entities :kv/rel-dc-day :kv/transpiration-factor 
                                                                 transpiration) 
                rel-day-to-quotient (create-entities :kv/rel-dc-day :kv/quotient-aet-pet
                                                     quotient)
                crop (assoc id 
                            (:crop/id (str (:crop/number id) "/" 
                                           (:crop/cultivation-type id) "/"
                                           (:crop/usage id)))
                            :crop/dc-to-rel-dc-day (get-entity-ids dc-to-day) 
                            :crop/dc-to-developmental-state-name (get-entity-ids dc-to-name)
                            :crop/rel-dc-day-to-cover-degree (get-entity-ids rel-day-to-cover-degree) 
                            :crop/rel-dc-day-to-extraction-depth (get-entity-ids rel-day-to-extraction-depth)
                            :crop/rel-dc-day-to-transpiration-factor (get-entity-ids rel-day-to-transpiration-factor)
                            :crop/rel-dc-day-to-quotient-aet-pet (get-entity-ids rel-day-to-quotient)
                            :crop/effectivity-quotient (-> effectivity first second))]
            (always ((d/transact datomic-connection
              ;print
              (flatten [dc-to-day
                        dc-to-name
                        rel-day-to-cover-degree
                        rel-day-to-extraction-depth
                        rel-day-to-transpiration-factor
                        rel-day-to-quotient
                        crop]))))
            )))

(defparser parse-file []
  (let->> [
           
           
           
           ])
  
  
  )






(comment
  
  (defparser ws []
    (either (char \space) (char \,)))
  
  (defparser tok [token-parser]
    (many (ws))
    token-parser) 
  
  (defparser array-item []
    (let->> [digit-chars (many1 (digit))]
            (always (read-string (apply str digit-chars)))))
  
  (defparser arr []
    (between (char LPAREN) (char RPAREN)
             (many (tok (array-item)))))  
  
  (run (arr) "()")
  (run (arr) "(1)")
  (run (arr) "(1, 2, 3)")
  (run (arr) "( 1, 2, 3)")
  
  (run (arr) "(1, 2, 3, )") ;; => RuntimeException
  
  (defparser arr []
    (between (char LPAREN) (tok (char RPAREN))
             (many (attempt (tok (array-item))))))
  
  
  
  (defparser instruction []
    (choice (char \>)
            (char \<)
            (char \+)
            (char \-)
            (char \.)
            (char \,)
            (between (char \[) (char \]) (many (instruction)))))
  
  (defparser bf []
    (many (instruction))
    (eof))
  
  (run (bf) ",>++++++[<-------->-],[<+>-]<.")
  
  (defparser ben-string []
    (let->> [length (integer)]
            (>> (char \:)
                (times length (any-char)))))
  
  (run (ben-string) "4:spam") ;; => [\s \p \a \m]
  
  )