(ns berest-service.berest.plot
  (:require [hiccup [element :as he]
             [form :as hf]
             [page :as hp]]
            #_[net.cgrand.enlive-html :as html]
            [berest-service.berest
             [core :as bc]
             [datomic :as bd]
             [util :as bu]
             [helper :as bh
              :refer [args-21->12 args-231->123 --< --<* |-> |* |*kw]]]
            [c2.scale :as scale]
            [analemma
             [charts :as acharts]
             [svg :as asvg]]
            [clojure
             [edn :as edn]
             [pprint :as pp]
             [walk :as cw]
             [string :as cs]]
            [clojure.math.numeric-tower :as nt]
            [clj-time.format :as ctf]
            [datomic.api :as d]
            [formative
             [core :as f]
             [parse :as fp]]
            [clojure-csv.core :as csv]
            [clojure.core.match :as ccm
             :refer [match]]
            [let-else :as le
             :refer [let?]]))


(defn style [m]
  (reduce (fn [s [k v]]
            (str s (name k) ": "
              (match [v]
                [[val unit]] (str val (name unit))

                [n :guard number?]
                (if (#{:height :width :top :left :bottom :right} k)
                  (str n "px")
                  n)

                [val] (name val))
              ";"))
          "" m))

(defn seq->vec [analemma-forms]
  (cw/prewalk #(if (seq? %1) (into [] %1) %1) analemma-forms))

(defn y-axis [{:keys [height width
              	      ymin ymax
            		      grid-lines
            		      axis-font-family axis-font-size
            		      axis-number-format
                      side] :or {side :left}}]
  (let [grid-y-space (/ height grid-lines)]
    (for [i (range 1 (inc grid-lines)) :when (even? i)]
      (-> (asvg/text  { :x (match side
                            :left 0
                            :right width)
                        :y (- height (* i grid-y-space))}
  	                  (format axis-number-format
          		                (asvg/translate-value (* i grid-y-space)
          			  	                                0 height ymin ymax)))
        (asvg/style :fill (asvg/rgb 150 150 150)
      	            :font-family axis-font-family
                    :font-size axis-font-size
                    :text-anchor (match side :left :end :right :start)
                    :alignment-baseline :middle)))))

(defn xy-plot [& options]
  (let [props (merge acharts/default-chart-props (apply hash-map options))
        [l-ymin l-ymax] (:y-range-left props)
        [r-ymin r-ymax] (:y-range-right props)]
    {:properties props
     :svg (-> (asvg/group (acharts/chart-background props))
            (asvg/translate (:x props) (:y props))
    	      (concat
    	        (acharts/x-grid props)
    	        (acharts/y-grid props)
    	        (acharts/x-axis props)
    	        (if (and l-ymin l-ymax)
                (y-axis (merge props {:side :left
                                      :ymin l-ymin
                                      :ymax l-ymax}))
                [])
              (if (and r-ymin r-ymax)
                (y-axis (merge props {:side :right
                                      :ymin r-ymin
                                      :ymax r-ymax}))
                [])))}))

(defn point-label [id x y text options]
  [:text (merge {:id id :x x :y y
                :label-font-family "Verdana"
                :label-font-size "55px"
                :visibility :visible
                :fill (asvg/rgb 100 100 150)} options)
          text])

(defn polyline [xs ys & options]
  (let [points (clojure.string/join " " (map #(str %1 "," %2) xs ys))]
    [:polyline (merge {:points points} (apply hash-map options))]))

(defn line [x1 y1 x2 y2 & options]
  [:line (merge {:x1 x1 :y1 y1 :x2 x2 :y2 y2} (apply hash-map options))])

(defn add-points [xs ys display-xs display-ys options]
  (map (fn [x y dx dy]
         [:circle (merge { :cx x, :cy y, :r 2
                           :data-x dx :data-y dy}
                         options)])
        xs ys display-xs display-ys))

(defn insert-into [chart hi-elements]
  (assoc chart :svg (concat (:svg chart) hi-elements)))

(defn add-margins [low high & {:keys [percentage] :or {percentage 10}}]
  (let [abs #(if (< 0 %1) %1 (- %1))
        range (- high low)
        fraction (* range (/ percentage 100))]
  [(- low (abs fraction)) (+ high (abs fraction))]))

#_(defn graph-javascript []
  (he/javascript-tag
    " function enlargeCircle(evt, id, label, unit){
        var ct = evt.currentTarget;
        ct.prevSize = ct.r.baseVal.value;
        ct.r.baseVal.value = ct.prevSize * 3;

        var vd = document.getElementById('valueDisplay_'+id);
        vd.setAttribute('x', ct.getAttribute('cx'));
        vd.setAttribute('y', ct.getAttribute('cy'));
        vd.style.visibility = 'visible';

        vd.setAttribute('fill', ct.getAttribute('fill'));
        vd.textContent =  'Tag: '+ct.getAttribute('data-x')
                          +' | '+label+': '+ct.getAttribute('data-y')+unit;
      };
      function shrinkCircle(evt, id){
        var ct = evt.currentTarget;
        ct.r.baseVal.value = ct.prevSize;

        var vd = document.getElementById('valueDisplay_'+id);
        vd.setAttribute('style', 'visibility:hidden');
      };"))

(def default-graph-properties
  { :min-day 80
    :x-margin {:left 40 :right 40}
    :y-margin {:top 30 :bottom 30}
    :height 200
    :plot-area-margin-percentage 5
    :curve-properties { :color :black
                        :color-f nil
                        :label ""
                        :unit ""
                        :f identity
                        :type :line
                        :assoc-y :left
                        :assoc-x :bottom}})

(defn graph [id data & {:keys [ xmin xmax
                                x-margin y-margin
                                height
                                margin-percentage
                                default-props
                                force-const-lines-display
                                align-zero]
                        :or { xmin (:min-day default-graph-properties)
                              x-margin (:x-margin default-graph-properties)
                              y-margin (:y-margin default-graph-properties)
                              height (:height default-graph-properties)
                              margin-percentage (:plot-area-margin-percentage default-graph-properties)
                              default-props (:curve-properties default-graph-properties)
                              force-const-lines-display false
                              align-zero false}}]
  (let [;get x and y values of const lines into for x/y/min/max calculations
        ;if forced, else these should only appear if other data being displayed
        ;use that range
        {:keys [hs vs]} (if force-const-lines-display
                          (reduce (fn [ {:keys [hs vs] :as acc}
                                        {:keys [const horizontal vertical]}]
                                    (cond
                                      (and const horizontal)
                                      { :hs (conj hs horizontal)
                                        :vs vs}

                                      (and const vertical)
                                      { :hs hs
                                        :vs (conj vs vertical)}

                                      :else
                                      acc))
                                  {:hs [] :vs []} data)
                          {:hs [] :vs []})

        data* (map (|* merge default-props) data)

        dayss (map :days data*)
        max-days (apply max (apply concat vs dayss))
        xmax (or xmax (* (+ (quot max-days 10) 1) 10))
        [xmin* xmax*] (add-margins xmin xmax :percentage margin-percentage)
        ;width is being constructed dynamically to use browser scrolling and
        ;zooming in svg graphs
        width (* 9 (int (- xmax xmin)))

        m|m (fn [min|max l] (when (seq l) (apply min|max l)))
        calc-y-min-max (fn [side]
                          (some->> data*
                            (filter #(= (:assoc-y %1) side) ,,,)
                            (map :values ,,,)
                            (apply concat hs ,,,)
                            (#(when (seq %) %) ,,,)
                            (--<* (|* m|m min) (|* m|m max) ,,,)
                            (apply add-margins ,,,)))

        colors (map :color data*)
        y-min-max* {:left (calc-y-min-max :left)
                    :right (calc-y-min-max :right)}

        ;align two sided graphs to a zero line if possible
        y-min-max (if (and  align-zero
                            (some (|* > 0) (:left y-min-max*))
                            (not-any? nil? (--<* :left :right y-min-max*)))
                    (let [[[yll ylh] [yrl yrh]]
                          (--<* :left :right y-min-max*)

                          left-ratio (/ ylh yll)
                          upper-factor (Math/abs (/ (* yrl left-ratio)
                                                    yrh))
                          yrh+ (- (* upper-factor yrh) yrh)]
                      { :left [yll ylh]
                        :right [yrl (+ yrh yrh+)]})
                    y-min-max*)

        ;create chart which is used as background
        chart (xy-plot  :width width :height height
                        :x (:left x-margin) :y (:top y-margin)
                        :xmin xmin :xmax xmax
                        :y-range-left (:left y-min-max)
                        :y-range-right (:right y-min-max)
                       :grid-lines (/ (- xmax xmin) 10))

        scale-x (scale/linear :domain [xmin xmax] :range [0. width])
        scale-y { :left (scale/linear :domain (:left y-min-max)
                                      :range [height 0.])
                  :right (scale/linear  :domain (:right y-min-max)
                                        :range [height 0.])}

        ;transform data to include possible const lines vertically and horizontally
        data** (map (fn [{:keys [const horizontal vertical assoc-y] :as m}]
                      (merge  default-props m
                              (when (and const (or horizontal vertical))
                                (merge {:no-points true}
                                  (cond
                                    horizontal {:days [xmin xmax]
                                                :assoc-x :bottom
                                                :values [horizontal horizontal]}
                                    vertical {:days [vertical vertical]
                                              :assoc-y assoc-y
                                              :values (assoc-y y-min-max)})))))
                    data)]
    [:div#bars {:style (style {:background-color :gray
                               :height (+ height (+ (:top y-margin)
                                                    (:bottom y-margin)))
                               :width (+ width (+ (:left x-margin)
                                                  (:right x-margin)))})}

      [:svg
        (-> chart

          ;make analemma seqs to vectors
          seq->vec

          :svg

          (into ,,,
           (for [{:keys [days values color color-f
                         type assoc-y label unit f no-points]} data**
                 :let [days* (map scale-x days)
                       scale-y* (assoc-y scale-y)
                       zero (scale-y* 0)]]
                [:g {:data-label label :data-unit unit}
                 (match type
                        :line (polyline days* (map scale-y* values)
                                        :stroke color :fill :none)
                        :bar (map (fn [day* value]
                                    (let [value* (scale-y* value)]
                                         (line day* zero day* value*
                                               :stroke (if color-f
                                                           (color-f value)
                                                           color)
                                               :fill :none)))
                                  days* values))
                 (when-not no-points
                           (add-points (map scale-x days)
                                       (map (assoc-y scale-y) values)
                                       days (map f values)
                                       {:stroke color :fill color}))])))]]))

(defn- plot-content [user-id id until]
  (let? [db (bd/current-db user-id)
         :else [:div#error "Fehler: Konnte keine Verbindung zur Datenbank herstellen!"]

         plot (bc/db-read-plot db id 2012)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " id " nicht laden!"]

         weathers (get bc/weather-map 1993)

         irrigation-donations-map (bc/read-irrigation-donations db
                                                                (:plot/number plot)
                                                                (:plot/irrigation-area plot))

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weathers
                                      :irrigation-donations irrigation-donations-map
                                      :until-abs-day (+ until 7)
                                      :irrigation-mode :spinkle-losses)
         inputs-7 (drop-last 7 inputs)
         prognosis-inputs (take-last 7 inputs)
         days (range (-> inputs first :abs-day) (+ until 7 1))

         sms-7* (bc/calc-soil-moistures* inputs-7 (:plot/initial-soil-moistures plot))

         no-of-layers (-> sms-7*
                        first
                        :soil-moistures
                        count)

         sms-layers (for [i (range no-of-layers)]
                      (map  (|->  :soil-moistures
                                  (|* args-21->12 nth i)
                                  (|*kw bu/round :digits 5))
                            (rest sms-7*)))

         sms-days (map :abs-day (rest sms-7*))

         ;{soil-moistures-7 :soil-moistures
         ;:as sms-7} (last sms-7*)
         #_(calc-soil-moistures inputs-7 (:plot/initial-soil-moistures plot))]

    #_(clojure.string/join "<br>" (map #(-> % :soil-moistures first) sms-7*))
    #_(clojure.string/join "<br>" sms-7*)
    #_(clojure.string/join "<br>" inputs)

    [:div

     [:div#baseData
      [:span#plotId "Nr.: " (:plot/number plot)] " "
      [:span#plotArea "Fläche: " (:plot/crop-area plot)] " "
      [:span#plotIrrArea "beregnete Fläche: " (:plot/irrigation-area plot)]
      #_(print-str plot)]

        [:div#graphs
          [:span  [:span {:style "color:green"} "AET/PET Soll"] " / "
                  [:span {:style "color:goldenrod"} "AET/PET"] " / "
                  [:span
                    [:span {:style "color:blue"} "N"] "-"
                    [:span {:style "color:red"} "V"] " [mm]"] " / "
                  [:span {:style "color:darkblue"} "Gabe [mm]"]]
          (graph :d2Plot [
                          {:const true
                             :horizontal 0
                             :assoc-y :left
                             :color :black}

                          {:days (->> inputs
                                      (filter (|-> :qu-target
                                                   (|* < 0))
                                              ,,,)
                                      (map :abs-day ,,,))
                           :values (->> inputs
                                        (map :qu-target ,,,)
                                        (filter (|* < 0) ,,,)
                                        (map  (|->  (|* * 100)
                                                    (|*kw bu/round :digits 1))
                                              ,,,))
                            :color :green
                            :assoc-y :left
                            :label "AET/PET S."
                            :unit "%"}

                          { :days (->> (rest sms-7*)
                                       (filter (|-> :aet7pet
                                                    (|* < 0))
                                               ,,,)
                                       (map :abs-day ,,,))
                            :values (->> (rest sms-7*)
                                         (map :aet7pet ,,,)
                                         (filter (|* < 0) ,,,)
                                         (map  (|->  (|* * 100)
                                                     (|*kw bu/round :digits 1))
                                               ,,,))
                            :color :goldenrod
                            :assoc-y :left
                            :label "AET/PET"
                            :unit "%"}

                          { :days days
                            :values (map  (|->  (--< :precipitation :evaporation)
                                                (|* apply -)
                                                (|*kw bu/round :digits 1))
                                          inputs)
                            :color-f #(if (< % 0) :red :blue)
                            :type :bar
                            :assoc-y :right
                            :label "N-V"
                            :unit "mm"}

                          { :days days
                            :values (->> inputs
                                      (map :irrigation-amount ,,,)
                                      (filter (|* < 0) ,,,)
                                      (map  (|*kw bu/round :digits 1)
                                            ,,,))
                            :color :darkblue
                            :type :bar
                            :assoc-y :right
                            :label "Gabe" :unit "mm"}
                            ]
                  :align-zero true)]

      [:div
          [:span  [:span {:style "color:green"} "AET/PET Soll"] " / "
                  [:span {:style "color:goldenrod"} "AET/PET"] " / "
                  [:span
                    [:span {:style "color:blue"} "N"] "-"
                    [:span {:style "color:red"} "V"] " [mm]"] " / "
                  [:span {:style "color:darkblue"} "Gabe [mm]"]]
          (graph :d1Plot [
                          { :const true
                            :horizontal 0
                            :assoc-y :left
                            :color :black}

                          { :days (->> (rest sms-7*)
                                    (filter (|->  :aet7pet
                                                  (|* < 0))
                                            ,,,)
                                    (map :abs-day ,,,))
                            :values (->> (rest sms-7*)
                                      (map :aet7pet ,,,)
                                      (filter (|* < 0) ,,,)
                                      (map  (|->  (|* * 100)
                                                  (|*kw bu/round :digits 1))
                                            ,,,))
                            :color :goldenrod
                            :assoc-y :left
                            :label "AET/PET"
                            :unit "%"
                          }

                          { :days days
                            :values (map  :precipitation  inputs)
                            :color-f #(if (< % 0) :red :blue)
                            :type :bar
                            :assoc-y :right
                            :label "N-V"}

                          ]
                  :align-zero true
                  :force-const-lines-display true)]


       [:div
          [:span "DC Stadium"]
          (graph :d1Plot [{:days days
                           :values (map :rel-dc-day inputs)
                           :color :black
                           :label "DC"}])]

        [:div
          [:span  [:span {:style "color:blue"} "Niederschlag (N) [mm]"] " / "
                  [:span {:style "color:red"} "Verdunstung (V) [mm]"] " / "
                  [:span {:style "color:black"} "N-V [mm]"]]
          (graph :d2Plot [{ :const true
                            :horizontal 0
                            :color :black}
                          { :days days
                            :values (map :precipitation inputs)
                            :color :blue
                            :label "Nied." :unit "mm"}
                          { :days days
                            :values (map :evaporation inputs)
                            :color :red
                            :label "Verd." :unit "mm"}
                          { :days days
                            :values (map  (|->  (--< :precipitation :evaporation)
                                                (partial apply -))
                                          inputs)
                            :color :black
                            :type :bar
                            :label "P-V" :unit "mm"}])]

        [:div
          [:span [:span {:style "color:darkkhaki"} "Transpirationsfaktor [0-2]"]] " / "
          [:span [:span {:style "color:green"} "Bedeckungsgrad [0-100%]"]] " / "
          [:span [:span {:style "color:blue"} "Durchwurzelungstiefe [0-2m]"]] " / "
          [:span [:span {:style "color:brown"} "Soll AET/PET [0-1]"]]
          (graph :d3Plot [{ :const true
                            :horizontal 0
                            :color :black}
                          { :const true
                            :horizontal 1
                            :color :black}
                          { :days days
                            :values (map :transpiration-factor inputs)
                            :color :darkkhaki
                            :label "Trans."}
                          { :days days
                            :values (map (|-> :rounded-extraction-depth-cm
                                              (|* bh/swap / 100)) inputs)
                            :color :blue
                            :label "D.wurz.tiefe" :unit "m"}
                          { :days days
                            :values (map :cover-degree inputs)
                            :color :green
                            :label "Bed.grad" :unit "%" :f (|* * 100)}
                          { :days days
                            :values (map :qu-target inputs)
                            :color :brown
                            :label "S.AET/PET"}])]
        #_[:div
          [:span [:span {:style "color:blue"} "Bodenfeuchteschichten"]]
          (concat
            (map-indexed  (fn [i sms]
                            (graph  (keyword (str "d4Plot" i))
                                    [ { :const true
                                        :horizontal 0
                                        :color :black}
                                      { :days sms-days
                                      :values sms
                                      :color :blue
                                      :unit "mm"
                                      :label (str "Bf. Schicht-" (inc i))}]
                                    :force-const-lines-display true
                                    :y-margin (cond
                                                (= i 0)
                                                { :top (-> default-graph-properties
                                                        :y-margin
                                                        :top)
                                                  :bottom 0}

                                                (= (inc i) no-of-layers)
                                                { :top 0
                                                  :bottom (-> default-graph-properties
                                                            :y-margin
                                                            :bottom)}

                                                :else
                                                { :top 0 :bottom 0})))
                          sms-layers))


          #_(graph :d4Plot (map-indexed (fn [i sms]
                                        { :days sms-days
                                          :values sms
                                          :color :blue
                                          :label (str "Bf. Schicht-" (inc i))})
                                      sms-layers))]


        [:div#weatherTable
          [:table
            [:tr
              [:th "Datum"]
              [:th "Niederschlag [mm]"]
              [:th "Verdunstung [mm]"]
              [:th "Regengabe [mm]"]]
            (for [{:keys [abs-day
                          irrigation-amount
                          precipitation
                          evaporation]} (reverse inputs)]
              [:tr
                [:td (ctf/unparse (ctf/formatter "dd.MM.YYYY") (bu/doy-to-date abs-day))]
                [:td precipitation]
                [:td evaporation]
                [:td irrigation-amount]])]]

        ]))

(defn plot-layout [user-id farm-id id until]
  [:div
   [:h1 (str "user-id: " user-id " farm-id: " farm-id " Schlag: " id)]
   #_[:svg {:height 200} [:rect#bla {:x 0 :y 0 :width 100 :height 100 :style "fill:red"}]]
   #_(he/javascript-tag "weberest.web.views.client.call_transform_rect();")
   (plot-content user-id id until)
   (he/javascript-tag "weberest.web.views.client.setup_value_display_svg_listeners();")])

(def default-weather-data
     (->> (get bc/weather-map 1993)
          (map (|-> second
                    (--< :doy :precipitation :evaporation)
                    (|* map str)) ,,,)
          (cons ["Tag im Jahr" "Niederschlag" "Verdunstung"] ,,,)
          (#(csv/write-csv % :delimiter ";") ,,,)))

(defn test-plot-form [db]
     {:enctype "multipart/form-data"
      :values {:plot-id "0400"
               ;:weather-data default-weather-data
               :irrigation-data (str "Tag im Jahr;Menge"
                                     \newline "100;0"
                                     \newline "110;0")
              :dc-state-data (str "Tag im Jahr;DC-Code"
                                  \newline "100;1"
                                  \newline "110;20")}
      :validations [[:integer :until-julian-day]]
      :fields [{:name :h1 :type :heading :text "Schlag auswählen ..."}
               {:name :plot-id :type :select
                :options (for [id (bc/available-plot-ids db)] [id id])
                :label "Schlag"}
               {:name :h1 :type :heading :text "Rechnen bis ..."}
               {:name :until-julian-day :type :select
                :options (for [d (range 1 366)] [d d])
                :first-option 250
                :label "lfd. Tag im Jahr"}
               {:name :xxx :type :html :html [:div {:style "font-weight: bold"} "oder optional über Datum ..."]}
               {:name :until-day :type :select
                :options (for [d (range 1 32)] [d d])
                :placeholder ""
                :label "Tag"}
               {:name :until-month :type :select
                :options (for [m (range 4 13)] [m m])
                :placeholder ""
                :label "Monat"}
               {:name :h1 :type :heading :text "Klima wählen ..."}
               {:name :weather-year :type :select
                :options (for [y (range 1993 1999)] [y y])
                :label "Wetterdaten (Star2/Müncheberg) für Jahr"}
               #_{:name :weather-data :type :textarea :label "Wetterdaten"}
               {:name :h1 :type :heading :text "Beregnungsgaben angeben ..."}
               {:name :irrigation-data :type :textarea :label "Beregnungswasser [mm]"}
               #_{:name :dc-state-data :type :textarea :label "Beobachtete DC-Stadien"}
               {:name :csv-delimiter :type :select
                :options [{:value ";" :label ";"}
                          {:value "\t" :label "Tabulator"}]
                :label "Trennzeichen für Textboxen mit CSV-Format Text"}]})

(defn test-plot-layout [user-id farm-id]
  (f/render-form (assoc (test-plot-form (bd/current-db user-id))
                        :method :post
                        :action (str "/farms/" farm-id "/plots/test.csv"))))

(defn parse-weather-data [weather-data delim]
  (let [wd (rest (csv/parse-csv weather-data
                                :delimiter delim))
           create-map (if (-> wd first count (= ,,, 3))
                       (fn [doy precip evap]
                         {:doy (Integer/parseInt doy)
                          :precipitation (Float/parseFloat precip)
                          :evaporation (Float/parseFloat evap)
                          :prognosis? false})
                       (fn [doy precip globrad tavg]
                         {:doy (Integer/parseInt doy)
                          :precipitation (Float/parseFloat precip)
                          :evaporation (bc/potential-evaporation-turc-wendling
                                        (Float/parseFloat globrad)
                                        (Float/parseFloat tavg))
                          :prognosis? false}))]
       (reduce (fn [sm data-line]
                 (let [m (apply create-map data-line)]
                      (assoc sm (:doy m) m)))
               (sorted-map) wd)))

(defn parse-irrigation-data* [year irrigation-data]
  (for [[day month amount] irrigation-data]
       {:irrigation/abs-day (bu/date-to-doy day month year)
        :irrigation/amount amount}))

(defn parse-irrigation-data [irrigation-data delim]
  (for [[abs-day amount] (rest (csv/parse-csv irrigation-data
                                              :delimiter delim))]
       {:irrigation/abs-day (Integer/parseInt abs-day)
        :irrigation/amount (Float/parseFloat amount)}))

(defn parse-dc-state-data [dc-state-data delim]
  (for [[abs-day dc] (rest (csv/parse-csv dc-state-data
                                              :delimiter delim))]
       {:abs-day (Integer/parseInt abs-day)
        :dc (Integer/parseInt dc)}))

;right now user-id as used as name for the database the user uses
;this should be changed if web-app db is used and holds configuration data for users
(defn calc-test-plot
  [user-id farm-id {:keys [plot-id until-julian-day
                           until-day until-month
                           weather-year
                           weather-data irrigation-data
                           #_dc-state-data
                           ]
                          [csv-delimiter] :csv-delimiter
                          :as test-data}]
  (let? [db (bd/current-db user-id)
         :else [:div#error "Fehler: Konnte keine Verbindung zur Datenbank herstellen!"]

         plot (bc/db-read-plot db plot-id 2012)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " plot-id " nicht laden!"]

         weather-year* (Integer/parseInt weather-year)
         weathers (get bc/weather-map weather-year*)
         #_(if (cs/blank? weather-data)
               bc/weather-map
               (parse-weather-data weather-data csv-delimiter))

         irrigation-donations
         (if (cs/blank? irrigation-data)
             (bc/read-irrigation-donations db (:plot/number plot)
                                           (:plot/irrigation-area plot))
             (parse-irrigation-data irrigation-data csv-delimiter))

         #_dc-assertions
         #_(->> dc-state-data
              (#(parse-dc-state-data % \;) ,,,)
                                     (map (fn [{:keys [abs-day dc]}]
                                            (bd/create-dc-assertion* weather-year* abs-day dc))
                                          ,,,))
         ;plot* (update-in plot [])


         until-julian-day* (if (not-any? empty? [until-day until-month])
                               (bu/date-to-doy (Integer/parseInt until-day)
                                               (Integer/parseInt until-month)
                                               weather-year*)
                               (Integer/parseInt until-julian-day))

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weathers
                                      :irrigation-mode irrigation-donations
                                      :until-abs-day (+ until-julian-day* 7)
                                      :irrigation-mode :sprinkle-losses)
         inputs-7 (drop-last 7 inputs)

         ;xxx (map (|-> (--< :abs-day :irrigation-amount) str) inputs-7)
         ;_ (println xxx)

         prognosis-inputs (take-last 7 inputs)
         days (range (-> inputs first :abs-day) (+ until-julian-day* 7 1))

         sms-7* (bc/calc-soil-moistures* inputs-7 (:plot.annual/initial-soil-moistures plot))
         {soil-moistures-7 :soil-moistures
         :as sms-7} (last sms-7*)
         #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

         prognosis* (bc/calc-soil-moisture-prognosis* 7 prognosis-inputs soil-moistures-7)
         prognosis (last prognosis*)
         #_(bc/calc-soil-moisture-prognosis 7 prognosis-inputs soil-moistures-7)

         #_no-of-layers
         #_(-> sms-7*
             first
             :soil-moistures
             count)

          #_sms-layers
          #_(for [i (range no-of-layers)]
                 (map (|-> :soil-moistures
                           (|* args-21->12 nth i)
                           (|*kw bu/round :digits 5))
                      (rest sms-7*)))

        ;sms-days (map :abs-day (rest sms-7*))
        ]

        ;use rest on sms-7* etc. to skip the initial value prepended by reductions
        ;which doesn't fit to the input list
        (csv/write-csv (bc/create-csv-output inputs (concat (rest sms-7*) (rest prognosis*)))
                       :delimiter ";")))


(defn calc-plot
  [& {:keys [user-id farm-id plot-id]
      {:keys [until-day until-month
              weather-year
              irrigation-data
              #_dc-state-data]} :data
      :as all}]
  (let? [db (bd/current-db user-id)
         :else [:div#error "Fehler: Konnte keine Verbindung zur Datenbank herstellen!"]

         weather-year* (Integer/parseInt weather-year)
         weathers (get bc/weather-map weather-year*)

         plot (bc/db-read-plot db plot-id weather-year*)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " plot-id " nicht laden!"]

         irrigation-donations (for [[day month amount] (edn/read-string irrigation-data)]
                                {:irrigation/abs-day (bu/date-to-doy day month weather-year*)
                                 :irrigation/amount amount})

         #_dc-assertions
         #_(->> dc-state-data
              (#(parse-dc-state-data % \;) ,,,)
                                     (map (fn [{:keys [abs-day dc]}]
                                            (bd/create-dc-assertion* weather-year* abs-day dc))
                                          ,,,))
         ;plot* (update-in plot [])


         until-julian-day (bu/date-to-doy (Integer/parseInt until-day)
                                          (Integer/parseInt until-month)
                                          weather-year*)

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weathers
                                      :irrigation-donations irrigation-donations
                                      :until-abs-day (+ until-julian-day 7)
                                      :irrigation-mode :sprinkle-losses)
         inputs-7 (drop-last 7 inputs)

         ;xxx (map (|-> (--< :abs-day :irrigation-amount) str) inputs-7)
         ;_ (println xxx)

         prognosis-inputs (take-last 7 inputs)
         days (range (-> inputs first :abs-day) (+ until-julian-day 7 1))

         sms-7* (bc/calc-soil-moistures* inputs-7 (:plot.annual/initial-soil-moistures plot))
         {soil-moistures-7 :soil-moistures
         :as sms-7} (last sms-7*)
         #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

         prognosis* (bc/calc-soil-moisture-prognosis* 7 prognosis-inputs soil-moistures-7)
         prognosis (last prognosis*)
         #_(bc/calc-soil-moisture-prognosis 7 prognosis-inputs soil-moistures-7)

         #_no-of-layers
         #_(-> sms-7*
             first
             :soil-moistures
             count)

          #_sms-layers
          #_(for [i (range no-of-layers)]
                 (map (|-> :soil-moistures
                           (|* args-21->12 nth i)
                           (|*kw bu/round :digits 5))
                      (rest sms-7*)))

        ;sms-days (map :abs-day (rest sms-7*))
        ]

        ;use rest on sms-7* etc. to skip the initial value prepended by reductions
        ;which doesn't fit to the input list
        (csv/write-csv (bc/create-csv-output inputs (concat (rest sms-7*) (rest prognosis*)))
                       :delimiter ";")))

(defn simulate-plot
  [& {:keys [user-id farm-id plot-id]
      {:keys [until-day until-month
              weather-year
              #_dc-state-data]} :data
      :as all}]
  (let? [db (bd/current-db user-id)
         :else [:div#error "Fehler: Konnte keine Verbindung zur Datenbank herstellen!"]

         weather-year* (Integer/parseInt weather-year)
         weathers (get bc/weather-map weather-year*)

         plot (bc/db-read-plot db plot-id weather-year*)
         :else [:div#error "Fehler: Konnte Schlag mit Nummer: " plot-id " nicht laden!"]

         until-julian-day (bu/date-to-doy (Integer/parseInt until-day)
                                          (Integer/parseInt until-month)
                                          weather-year*)

         inputs (bc/create-input-seq| :plot plot
                                      :sorted-weather-map weathers
                                      :until-abs-day until-julian-day #_(+ until-julian-day 7)
                                      :irrigation-mode :sprinkle-losses)

         ;xxx (map (|-> (--< :abs-day :irrigation-amount) str) inputs)
         ;_ (println xxx)

         days (range (-> inputs first :abs-day) (+ until-julian-day 1))

         sms* (bc/calculate-soil-moistures-by-auto-donations* inputs (:plot.annual/initial-soil-moistures plot)
                                                              (:plot/slope plot) (:plot.annual/technology plot) 5)
         {soil-moistures :soil-moistures
          :as sms} (last sms*)
         #_(bc/calc-soil-moistures inputs-7 (:plot.annual/initial-soil-moistures plot))

         ;_ (map pp/pprint sms*)
         ]

        ;use rest on sms-7* etc. to skip the initial value prepended by reductions
        ;which doesn't fit to the input list
        (csv/write-csv (bc/create-csv-output inputs (rest sms*)) :delimiter ";")))

(defn plots-layout [user-id farm-id]
  [:div "user-id: " user-id " all plots in farm " farm-id])

(defn create-plot [user-id farm-id plot-data]
  (:id plot-data))

(defn new-plot-layout [user-id farm-id]
  (hf/form-to [:post (str "/farms/" farm-id "/plots/new")]
    [:div
      (hf/label "id" "Schlagnummer")
      (hf/text-field "id" "1234")]
    [:div
      (hf/label "number" "Schlagnummer:")
      (hf/text-field "number" "")]
    (hf/submit-button "Schlag erstellen")))

(defn rest-plot-ids [format user-id farm-id]
  #_(let [db (some->> user-id
                 (str bd/datomic-base-uri ,,,)
                 d/connect
                 d/db)

        (d/q '[:find ?plot-e-id
               :in $ ?farm-id
               :where [?plot-e-id :plot/number ?plot-no]]

                           (d/db datomic-connection) plot-no)



        ]


    )

  (case format
    :edn ["zalf"]
    :json ["zalf"]))
