(ns berest-service.berest.parse-crop-files
  (:require [berest-service.berest.helper :as bh]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bd]
            [instaparse.core :as insta]
            [clojure.java.io :as cjio]
            [clojure.pprint :as pp]))




(def crop-file-parser
  (insta/parser
   "
   crop-file = crop-data+

   crop-data =
   <empty-line*>
   header-line
   <empty-line+>
   dc-2-rel-day
   <empty-line+>
   dc-2-name
   <empty-line+>
   dc-2-coverdegree
   <empty-line+>
   dc-2-extraction-depth
   <empty-line+>
   dc-2-transpiration
   <empty-line+>
   dc-2-quotient
   <empty-line+>
   dc-2-effectivity
   <empty-line+>
   dito*
   <empty-line+>
   <block-separator>
   <empty-line*>

   (*
   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr
   *)
   header-line = <ows> crop-no <','> cult-type <','> (usage <','> crop-code <','>)? crop-name <rest-of-line>
   crop-no = integer
   cult-type = integer
   usage = integer
   crop-code = word
   crop-name = word

   (*
   DC =    1,  10, 21;  Code
            1,  15, 72;  Tag
   *)
   dc-2-rel-day =
   <ows 'DC' ows '=' ows> dc-codes <rest-of-line>
   <ows> rel-days <rest-of-line>

   dc-codes = (integer <ows> <','>? <ows>)+
   rel-days = (integer <ows> <','>? <ows>)+
   integer-values = (integer <ows> <','>? <ows>)+
   double-values = (double <ows> <','>? <ows>)+

   (*
   NameDC =   1 : Aussaat;
             10 : Aufgang;
             21 : Best.-beginn;
   *)
   dc-2-name = <ows 'NameDC' ows '=' ows> dc-2-name-pair+
   <dc-2-name-pair> = <ows ';'? ows> integer <ows ':' ows> #'[^;]+' <rest-of-line>

   (*
   Bedeckungsgrad   =   15,   30,  115;                    Tag
                         0, 0.60, 0.80;                    Wert
   *)
   dc-2-coverdegree =
   <ows 'Bedeckungsgrad' ows '=' ows> dc-codes <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Entnahmetiefe    =   10,  90;                           Tag
                         1,   6;                           Wert
   *)
   dc-2-extraction-depth =
   <ows 'Entnahmetiefe' ows '=' ows> dc-codes <rest-of-line>
   <ows> integer-values <rest-of-line>

   (*
   Transpiration    =    1;                                Tag
                         1;                                Wert
   *)
   dc-2-transpiration =
   <ows 'Transpiration' ows '=' ows> dc-codes <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Quotient(soll)   =    1;                                Tag
                         0;                                Wert
   *)
   dc-2-quotient =
   <ows 'Quotient(soll)' ows '=' ows> dc-codes <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Effektivitaet    =    1;                                Tag
                      0.17;                                Wert
   *)
   dc-2-effectivity =
   <ows 'Effektivitaet' ows '=' ows> dc-codes <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   0101,7,2,dito.,;
   0101,7,3,dito.,;
   0101,7,9,dito.,;
   *)
   dito = <ows> crop-no <','> cult-type <','> usage <',' ows 'dito.' ows ',' rest-of-line>

   (*
   * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -
   *)
   block-separator = <ows ('* -' ws?)+ ows newline>

   rest-of-line = ';' #'.*' newline
   empty-line = ows | newline
   newline = '\\r\\n' | '\\n'  (*#'\\r\\n' | #'\\n'*)
   ows = #'\\s*'
   ws = #'\\s'
   word = #'[a-zA-Z0-9/.-]+'
   integer = #'[0-9]+'
   double = #'[0-9]+(?:\\.[0-9]*)?'
   "))

(def test-text
  "

  0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr

  DC =    1,  10, 21;  Code
          1,  15, 72;  Tag

 NameDC =   1 : Aussaat;
           10 : Aufgang;
           21 : Best.-beginn;



 Bedeckungsgrad   =   15,   30,  115;                    Tag
                       0, 0.60, 0.80;                    Wert

 Entnahmetiefe    =   10,  90;                           Tag
                       1,   6;                           Wert

 Transpiration    =    1;                                Tag
                       1;                                Wert

 Quotient(soll)   =    1;                                Tag
                       0;                                Wert

 Effektivitaet    =    1;                                Tag
                    0.17;                                Wert


  0101,7,2,dito.,;
  0101,7,3,dito.,;
  0101,7,9,dito.,;

  * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -

  ")


(insta/parse crop-file-parser test-text :optimize :memory)







#_(crop-file-parser
 "
 0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr

 DC =    1,  10, 21;  Code
         1,  15, 72;  Tag

 NameDC =   1 : Aussaat;
           10 : Aufgang;
           21 : Best.-beginn;



 Bedeckungsgrad   =   15,   30,  115;                    Tag
                       0, 0.60, 0.80;                    Wert

 Entnahmetiefe    =   10,  90;                           Tag
                       1,   6;                           Wert

 Transpiration    =    1;                                Tag
                       1;                                Wert

 Quotient(soll)   =    1;                                Tag
                       0;                                Wert

 Effektivitaet    =    1;                                Tag
                    0.17;                                Wert


 0101,7,2,dito.,;
 0101,7,3,dito.,;
 0101,7,9,dito.,;
 * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -


0101,1,Winterweizen;      Aussaatjahr

DC =    1,  10, 21;  Code
        1,  15, 72;  Tag

NameDC =   1 : Aussaat;
          10 : Aufgang;
          21 : 31. Dez.;



Bedeckungsgrad   =   15,   30,  115;                    Tag
                      0, 0.60, 0.80;                    Wert

Entnahmetiefe    =   10,  90;                           Tag
                      1,   6;                           Wert

Transpiration    =    1;                                Tag
                      1;                                Wert

Quotient(soll)   =    1;                                Tag
                      0;                                Wert

Effektivitaet    =    1;                                Tag
                   0.17;                                Wert

* - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -

 ")


(def crops (slurp (str "C:/Users/michael/development/GitHub/berest-service/resources/private/crops/full-version-with-dito-short-name-and-usage/BBFASTD1.TXT")))



(defn parse-Bbfastdx []
  (doseq [i (range 1 2)]
    (-> (str "private/crops/full-version-with-dito-short-name-and-usage/BBFASTD" i ".TXT")
        cjio/resource
        slurp
        crop-file-parser)))

#_(parse-Bbfastdx)




