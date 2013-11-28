(ns berest-service.berest.master-data
	(:require [datomic.api :as d]
						[clj-time.core :as ctc]
						[berest-service.berest
						 [datomic :as bd]
						 [core :as bc]
						 [util :as bu]
						 [helper :as bh]]))

(def master-data
  [
   ;farm Agrargenossenschaft Bad Dürrenberg eG
   {
    :db/id (bd/new-entity-id)

    :farm/id "irgendeine Fördernummer"

    :farm/name "Agrargenossenschaft Bad Dürrenberg eG"

    :farm/addresses [{
                      :address/street "Siedlungsstraße 17a"
                      :address/postal-code "06231"
                      :address/city "Bad Dürrenberg"
                      :address/city-part ""
                      :address/municipality "Saalekreis"
                      :address/state "Sachsen-Anhalt"
                      :address/state-short ""
                      :address/country "Bundesrepublik Deutschland"
                      :address/country-short "DE"
                      }]

    :farm/com-connections [{
                            :com-connection/id "+49-(0)3462/80337"
                            :com-connection/type :com-con.type/mobile-phone
                            :com-connection/description "Telefon-Zentrale"
                            }
                           {
                            :com-connection/id "info@ag-badduerrenberg.de"
                            :com-connection/type :com-con.type/email
                            :com-connection/description "Email-Zentrale"
                            }]

    :farm/contacts [{
                     :person/salutation "Herr"
                     :person/first-name "Bernd"
                     :person/last-name "Ulrich"
                     :person/com-connections [{
                                               :com-connection/id "+49170555555"
                                               :com-connection/type :com-con.type/mobile-phone
                                               }
                                              {
                                               :com-connection/id "bernd.ulrich@ag-badduerrenberg.de"
                                               :com-connection/type :com-con.type/email
                                               }]
                     :person/roles ["Vorstand"]
                     :person/is-main-contact true
                     :person/notes ["hinweis 1"
                                    "information über irgendwas 2"]
                     }

                    {
                     :person/salutation "Herr"
                     :person/first-name "Ralf"
                     :person/last-name "Heller"
                     :person/com-connections [{
                                               :com-connection/id "+4917066666666"
                                               :com-connection/type :com-con.type/fixed-phone
                                               }
                                              {
                                               :com-connection/id "ralf.heller@ag-badduerrenberg.de"
                                               :com-connection/type :com-con.type/email
                                               }]
                     :person/roles ["Werkstatt"]
                     :person/notes ["hinweis 1"
                                    "information über irgendwas 2"]
                     }]

    :farm/notes ["hinweis 1" "hinweis 2" "hinweis 2"]

    :farm/plots [(bd/temp-entity-id -101)
                 (bd/temp-entity-id -102)]

    :farm/weather-station (bd/temp-entity-id -1001)
    }


   ;weather-stations

   {:db/id (bd/temp-entity-id -1001)
    :weather-station/id "leipzig-schkeuditz"
    :weather-station/name "Leipzig-Schkeuditz"
    :weather-station/geo-coord {:geo-coord/latitude 51.444
                                :geo-coord/longitude 12.30}}

   ;plots

   {:db/id (bd/temp-entity-id -101)

    :plot/id "123243235342"

    :plot/number "112-0"

    :plot/description "Wasserloch"

    :plot/short-description ""

    :plot/notes ["bemerkung 1" "bemerkung 2"]

    :plot/crop-area 68.0

    :plot/irrigation-area 45.3

    :plot/stt 153 ;D5c

    :plot/slope 1

    :plot/groundwater-level 300

    :plot/damage-compaction-depth 300

    :plot/damage-compaction-area 0.0

    :plot/field-capacities (bd/create-inline-entities :soil/upper-boundary-depth :soil/field-capacity
                                                      [30 17.5, 60 15.5, 90 15.7, 120 15.7, 200 12.5])

    :plot/fc-unit :soil-moisture.unit/volP

    :plot/permanent-wilting-points (bd/create-inline-entities :soil/upper-boundary-depth :soil/permanent-wilting-point
                                                              [30 3.5, 60 3.0, 90 3.7, 120 3.7, 200 3.0])

    :plot/pwp-unit :soil-moisture.unit/volP

    ;:plot/annuals [plot.annual] :ref

    ;:plot/weather-station [weather-station] :ref

    ;:plot/weather-data * [weather-data] :ref

    ;:plot/location-coords * [geo-coord] :ref

    :plot/az-glz 55

    :plot/nft "03"

    :plot/bse "LL"

    :plot/sse "p-sö/g-(k)el"

    :plot/bwb "mittel"

    ;:plot/irrigation-well :ref

    }

   {:db/id (bd/temp-entity-id -102)}

   ])

  ;add 0110/1/0 WR data
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


;add plot zalf test plot
#_(defn add-zalf-test-plot [datomic-connection]
  (let [db (d/db datomic-connection)

				fcs (bd/create-entities :soil/upper-boundary-depth :soil/field-capacity
                                 [30 17.5, 60 15.5, 90 15.7, 120 15.7, 200 12.5])

        pwps (bd/create-entities :soil/upper-boundary-depth :soil/permanent-wilting-point
                                 [30 3.5, 60 3.0, 90 3.7, 120 3.7, 200 3.0])

        plot {:db/id (bd/new-entity-id)
              :plot/number "zalf"
              :plot/crop-area 1.0
              :plot/irrigation-area 1.0
              :plot/stt 6212
              :plot/slope 1
              :plot/field-capacities (bd/get-entity-ids fcs)
              :plot/fc-unit :soil-moisture.unit/volP
              :plot/permanent-wilting-points (bd/get-entity-ids pwps)
              :plot/pwp-unit :soil-moisture.unit/volP
              :plot/groundwaterlevel 300
              :plot/damage-compaction-depth 300
              :plot/damage-compaction-area 0.0}

				{:keys [db-after tempids] :as tx} (->> [fcs pwps plot]
																							 flatten
																							 (d/transact datomic-connection ,,,)
																							 .get)

        plot-e-id (d/resolve-tempid db-after tempids (:db/id plot))

				_ (add-zalf-1993 datomic-connection plot-e-id)
        _ (add-zalf-1994 datomic-connection plot-e-id)
        _ (add-zalf-1995 datomic-connection plot-e-id)
        _ (add-zalf-1996 datomic-connection plot-e-id)
        _ (add-zalf-1997 datomic-connection plot-e-id)
        _ (add-zalf-1998 datomic-connection plot-e-id)]
    true))


#_(defn install-test-data [datomic-connection]
	(bh/juxt* add-sugarbeet
						add-maize
						add-potato
						add-winter-rye
						add-winter-barley
						add-winter-wheat
						add-fallow
						datomic-connection)
	(add-zalf-test-plot datomic-connection))


