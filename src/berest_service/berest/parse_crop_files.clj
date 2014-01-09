(ns berest-service.berest.parse-crop-files
  (:require [berest-service.berest.helper :as bh]
            [datomic.api :as d]
            [berest-service.berest.datomic :as bd]
            [instaparse.core :as insta]
            [clojure.java.io :as cjio]
            [clojure.pprint :as pp]
            [clojure.java.shell :as sh]
            [clojure.string :as cs]))
"
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
   (<empty-line*> | <ows>)
   "

   "
   crop-data = <empty-line*>
   (header-line |
   dc-2-rel-day |
   dc-2-name |
   dc-2-coverdegree |
   dc-2-extraction-depth |
   dc-2-transpiration |
   dc-2-quotient |
   dc-2-effectivity |
   dito* |
   <empty-line*>) <ows>
   "

(def crop-file-parser
  (insta/parser
   "
   crop-file = <empty-line*> <block-separator?> crop-block+

   crop-block = <empty-line*> (crop-data <empty-line*>)+ (<block-separator> | <ows-without-newline EOF>)

   <crop-data> = header-line | dc-2-rel-day | dc-2-name | dc-2-coverdegree | dc-2-extraction-depth |
   dc-2-transpiration | dc-2-quotient | dc-2-effectivity | dito

   (*
   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr
   *)
   header-line = <ows> crop-no <','> cult-type <','> (usage <','> crop-code <','>)? crop-name <ows> <rest-of-line>
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
   <ows 'DC' ows '=' ows> integer-values <rest-of-line>
   <ows> integer-values <rest-of-line>

   integer-values = (integer <ows> <','>? <ows>)+
   double-values = (double <ows> <','>? <ows>)+

   (*
   NameDC =   1 : Aussaat;
             10 : Aufgang;
             21 : Best.-beginn;
   *)
   dc-2-name = <ows 'NameDC' ows '=' ows> dc-2-name-pairs
   dc-2-name-pairs = dc-2-name-pair+
   <dc-2-name-pair> = <ows> integer <ows ':' ows> #'[^;]+' <rest-of-line>

   (*
   Bedeckungsgrad   =   15,   30,  115;                    Tag
                         0, 0.60, 0.80;                    Wert
   *)
   dc-2-coverdegree =
   <ows 'Bedeckungsgrad' ows '=' ows> integer-values <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Entnahmetiefe    =   10,  90;                           Tag
                         1,   6;                           Wert
   *)
   dc-2-extraction-depth =
   <ows 'Entnahmetiefe' ows '=' ows> integer-values <rest-of-line>
   <ows> integer-values <rest-of-line>

   (*
   Transpiration    =    1;                                Tag
                         1;                                Wert
   *)
   dc-2-transpiration =
   <ows 'Transpiration' ows '=' ows> integer-values <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Quotient(soll)   =    1;                                Tag
                         0;                                Wert
   *)
   dc-2-quotient =
   <ows 'Quotient(soll)' ows '=' ows> integer-values <rest-of-line>
   <ows> double-values <rest-of-line>

   (*
   Effektivitaet    =    1;                                Tag
                      0.17;                                Wert
   *)
   dc-2-effectivity =
   <ows 'Effektivitaet' ows '=' ows> integer-values <rest-of-line>
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
   block-separator = <ows ('* -' ws '*'?)+ ows-without-newline newline>

   rest-of-line = ';' #'[^\\n\\r]*' newline
   empty-line = newline | ows-without-newline newline
   newline = '\\r\\n' | '\\n'
   ows-without-newline = #'[^\\S\\n\\r]*'
   ows = #'\\s*'
   ws = #'\\s'
   word = #'[a-zA-Z0-9/.-]+'
   integer = #'[0-9]+'
   double = #'[0-9]+(?:\\.[0-9]*)?'
   EOF = #'\\Z'
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

  * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - *

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

  ")

#_(crop-file-parser test-text)

(def ps (insta/parses crop-file-parser test-text :total true))

(pp/pprint ps)

(count ps)

#_(pp/pprint (nth ps 0))



(def trans {:double #(Double/parseDouble %)
            :integer #(Integer/parseInt %)
            :double-values vector
            :integer-values vector
            :word identity

            :crop-no identity
            :cult-type identity
            :usage identity
            :crop-code identity
            :crop-name identity

            :header-line (fn [crop-no cult-type & [usage-or-crop-name crop-code crop-name]]
                           [:crop {:db/id (bd/new-entity-id)
                            :crop/id (str crop-no "/" cult-type (when usage-or-crop-name "/") usage-or-crop-name)
                            :crop/number crop-no
                            :crop/cultivation-type cult-type
                            :crop/usage usage-or-crop-name
                            :crop/name crop-name
                            :crop/symbol (or crop-code crop-name)}])

            :dc-2-rel-day (fn [dcs days]
                            [:dc-2-rel-day (bd/create-entities :kv/dc :kv/rel-dc-day
                                                                (interleave dcs days))])

            :dc-2-coverdegree (fn [dcs cds]
                                [:dc-2-coverdegree (bd/create-entities :kv/rel-dc-day :kv/cover-degree
                                                                       (interleave dcs cds))])

            :dc-2-name-pairs vector
            :dc-2-name (fn [pairs]
                         [:dc-2-name (bd/create-entities :kv/dc :kv/name pairs)])

            :dc-2-extraction-depth (fn [dcs cds]
                                     [:dc-2-extraction-depth (bd/create-entities :kv/rel-dc-day :kv/extraction-depth
                                                                                 (interleave dcs cds))])

            :dc-2-transpiration (fn [dcs cds]
                                  [:dc-2-transpiration-factor (bd/create-entities :kv/rel-dc-day :kv/transpiration-factor
                                                                                  (interleave dcs cds))])

            :dc-2-quotient (fn [dcs cds]
                             [:dc-2-quotient (bd/create-entities :kv/rel-dc-day :kv/quotient-aet-pet
                                                                 (interleave dcs cds))])

            :dc-2-effectivity (fn [dcs cds]
                                [:effectivity (first cds)])

            :dito (fn [& args]
                    [:dito args])

            :crop-block (fn [& crop-data]
                         (flatten
                          (for [[k data-map] crop-data
                                :when (not (#{:dito :effectivity} k))]
                            (if (= k :crop)
                              (let [cd (into {} crop-data)]
                                (assoc data-map
                                  :crop/dc-to-rel-dc-days (bd/get-entity-ids (:dc-2-rel-day cd))
                                 :crop/dc-to-developmental-state-names (bd/get-entity-ids (:dc-2-name cd))
                                 :crop/rel-dc-day-to-cover-degrees (bd/get-entity-ids (:dc-2-coverdegree cd))
                                  :crop/rel-dc-day-to-extraction-depths (bd/get-entity-ids (:dc-2-extraction-depth cd))
                                  :crop/rel-dc-day-to-transpiration-factors (bd/get-entity-ids (:dc-2-transpiration cd))
                                  :crop/rel-dc-day-to-quotient-aet-pets (bd/get-entity-ids (:dc-2-quotient cd))
                                  :crop/effectivity-quotient (:effectivity cd)))
                              data-map))))


            })

(insta/transform trans (crop-file-parser test-text))



#_(defn add-winter-rye [datomic-connection]
  (let [dc-to-day (bd/create-entities :kv/dc :kv/rel-dc-day
                                      [21 60, 31 110, 51 140, 61 155, 75 170, 92 200])
        dc-to-name (bd/create-entities :kv/dc :kv/name
                                       [21 "Best.-beginn", 31 "Schossbeginn", 51 "Aehrenschieben", 61 "Bluete",
                                        75 "Milchreife", 92 "Todreife"])
        rel-day-to-cover-degree (bd/create-entities :kv/rel-dc-day :kv/cover-degree
                                                    [90 1.0])
        rel-day-to-extraction-depth (bd/create-entities :kv/rel-dc-day :kv/extraction-depth
                                                        [90 60, 120 90, 150 110, 170 130])
        rel-day-to-transpiration-factor (bd/create-entities :kv/rel-dc-day :kv/transpiration-factor
                                                            [100 1.0, 110 1.3, 190 1.3, 200 1.0, 210 0.1])
        rel-day-to-quotient (bd/create-entities :kv/rel-dc-day :kv/quotient-aet-pet
                                                [80 0.0, 90 0.2, 110 0.8, 170 0.8, 180 0.6, 200 0.0])

        crop {:db/id (bd/new-entity-id)
              :crop/id "0110/1/0"
              :crop/number 110
              :crop/cultivation-type 1
              :crop/usage 0
              :crop/name "Winterroggen/EJ"
              :crop/symbol "WR"
              :crop/dc-to-rel-dc-days (bd/get-entity-ids dc-to-day)
              :crop/dc-to-developmental-state-names (bd/get-entity-ids dc-to-name)
              :crop/rel-dc-day-to-cover-degrees (bd/get-entity-ids rel-day-to-cover-degree)
              :crop/rel-dc-day-to-extraction-depths (bd/get-entity-ids rel-day-to-extraction-depth)
              :crop/rel-dc-day-to-transpiration-factors (bd/get-entity-ids rel-day-to-transpiration-factor)
              :crop/rel-dc-day-to-quotient-aet-pets (bd/get-entity-ids rel-day-to-quotient)
              :crop/effectivity-quotient 0.17}]

    (d/transact datomic-connection
                ;print
                (flatten [dc-to-day
                          dc-to-name
                          rel-day-to-cover-degree
                          rel-day-to-extraction-depth
                          rel-day-to-transpiration-factor
                          rel-day-to-quotient
                          crop]))))


(def crops (slurp (str "C:/Users/michael/development/GitHub/berest-service/resources/private/crops/full-version-with-dito-short-name-and-usage/BBFASTD1.TXT")))

(crop-file-parser crops)

#_(cs/split crops #"\s*\*\s-\s\*\s\-[^\r\n]*")


#_(insta/parse crop-file-parser crops :optimize :memory)

(defn parse-Bbfastdx []
  (doseq [i (range 1 2)]
    (-> (str "private/crops/full-version-with-dito-short-name-and-usage/BBFASTD" i ".TXT")
        cjio/resource
        slurp
        crop-file-parser)))

#_(parse-Bbfastdx)









