(ns berest-service.berest.parse-crop-files
  #_(:refer-clojure :exclude [char])
  (:require [berest-service.berest.helper :as bh]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bd]
            [instaparse.core :as insta]
            [clojure.java.io :as cjio]))

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

#_(-> "private/crop-file-parser.txt"
                      cjio/resource
                      slurp)
(def p
  (insta/parser
   "
   line = <';'> <ws> crop-no <','> cult-type <','> usage <','> crop-code <','> crop-name ';' ws* token*
   crop-no = number
   cult-type = number
   usage = number
   crop-code = char char
   crop-name = token*
   <ws> = #'\\s+'
   <no-ws> = #'\\S'
   <token> = word | number | '/'
   word = letter+
   number = digit+
   <letter> = #'[a-zA-Z]'
   <digit> = #'[0-9]'
   <char> = #'\\w'

  "))


(p
 ";   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr")




(def as-and-bs
  (insta/parser
   "S = AB*
   AB = A B
     A = 'a'+
     B = 'b'+"))

(as-and-bs "aaaaabbbaaaabb")
