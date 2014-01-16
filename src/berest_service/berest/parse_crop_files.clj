(ns berest-service.berest.parse-crop-files
  (:require [datomic.api :as d]
            [berest-service.berest.datomic :as bd]
            [instaparse.core :as insta]
            [clojure.java.io :as cjio]
            [clojure.pprint :as pp]
            [clojure.string :as cs]))

(def crop-block-parser
  (insta/parser
   "
   crop-block = <empty-line*> (crop-data <empty-line*>)+ <ows-without-newline EOF>

   <crop-data> = header-line | dc-2-rel-day | dc-2-name | dc-2-coverdegree | dc-2-extraction-depth |
   dc-2-transpiration | dc-2-quotient | dc-2-effectivity | dito

   (*
   * - * - * - * - * - * - * - * - * - * - * - * - * - * - * - * -
   block-separator = <ows ('*' ows '-' ows '*'?)+ ows-without-newline newline>
   *)

   (*
   0101,7,0,WW,Winterweizen/AJ;      Aussaatjahr
   *)
   (*header-line = <ows> crop-no <','> cult-type <','> (usage <','> crop-code <','>)? crop-name <ows> <rest-of-line>*)
   header-line = <ows> crop-no <','> cult-type <','> usage <','> crop-code <','> crop-name <ows> <rest-of-line>
   crop-no = #'[0-9]+'
   cult-type = integer
   usage = integer
   crop-code = #'[^,]*'
   crop-name = #'[^;]*'

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
   <ows 'Quotient' ows '(soll)' ows '=' ows> integer-values <rest-of-line>
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

   rest-of-line = ';' #'[^\\n\\r]*' (newline | EOF)
   empty-line = newline | ws-without-newline newline
   newline = '\\r\\n' | '\\n'
   ows-without-newline = #'[^\\S\\n\\r]*'
   ws-without-newline = #'[^\\S\\n\\r]+'
   ows = #'\\s*'
   ws = #'\\s+'
   word = #'[a-zA-Z0-9/.-]+'
   integer = #'[0-9]+'
   double = #'[0-9]+(?:\\.[0-9]*)?'
   SOF = #'\\A'
   EOF = #'\\Z'
   "))


#_(def test-text
  "
0000,0,0,BRACHE,Brache;

DC =    1;  Code
        1;  Tag

NameDC =  1: Brache;


Bedeckungsgrad =   1;          Tag
                   0;          Wert

Entnahmetiefe  =   1;          Tag
                   1;          Wert

Transpiration  =   1;          Tag
                   1;          Wert

Quotient(soll) =   1;          Tag
                   0;          Wert

Effektivitaet  =   1;          Tag
                   0;          Wert

")

#_(crop-block-parser test-text)

(comment
(def ps (insta/parses crop-block-parser test-text
                      ;:total true
                      ))
(pp/pprint ps)
(count ps)
(pp/pprint (nth ps 0))
)

(defn transform-crop-block [block]
  (let [trans {:double #(Double/parseDouble %)
               :integer #(Integer/parseInt %)
               :double-values vector
               :integer-values vector
               :word identity

               :crop-no identity
               :cult-type identity
               :usage identity
               :crop-code cs/trim
               :crop-name cs/trim

               :header-line (fn [crop-no cult-type & [usage-or-crop-name crop-code crop-name]]
                              [:crop {:db/id (bd/new-entity-id)
                                      :crop/id (str crop-no "/" cult-type (when usage-or-crop-name "/") usage-or-crop-name)
                                      :crop/number (Integer/parseInt crop-no)
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
                                  data-map))))}]
    (insta/transform trans block)))

#_(-> test-text
    crop-block-parser
    transform-crop-block
    pp/pprint)


(comment
  (def crops (slurp (str "C:/Users/michael/development/GitHub/berest-service/resources/private/crops/issbruecker/BBFASTD1.TXT")))
  (def crop-blocks (cs/split crops #"\s*\*\s?-\s?\*\s?\-[^\r\n]*"))
  (pp/pprint (take 2 crop-blocks))
)

(defn parse-and-transform-crop-files [crop-files]
  (for [cf crop-files
        crop-block (-> cf
                       cjio/resource
                       slurp
                       (cs/split ,,, #"\s*\*\s?-\s?\*\s?\-[^\r\n]*")
                       (#(filter (fn [b] (-> b cs/trim empty? not)) %) ,,,))]
    (-> crop-block
        crop-block-parser
        transform-crop-block)))


(defn import-bbfastdx-crop-files-into-datomic [datomic-connection]
  (let [cfs (for [i (range 1 (inc 5))]
              (str "private/crops/issbruecker/BBFASTD" i ".txt"))
        transaction-data (parse-and-transform-crop-files cfs)]
    (d/transact datomic-connection transaction-data)))



#_(->> (parse-crop-files)
     #_(filter #(-> % second map?) ,,,)
     pp/pprint)









