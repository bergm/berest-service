(ns de.zalf.berest.web.rest.template
  (:require [clojure.string :as str]
            [hiccup.element :as he]
            [hiccup.form :as hf]
            [hiccup.page :as hp]
            [hiccup.def :as hd]
            [hiccup.util :as hu]
            [datomic.api :as d]
            [de.zalf.berest.core.datomic :as bd]
            [de.zalf.berest.web.rest.queries :as rq]
            [de.zalf.berest.web.rest.util :as util]
            [ring.util.response :as rur]
            [de.zalf.berest.web.rest.common :as common]))


(defn url->link-segments
  [url-like & [{:keys [base sub-path-accessible?]
               :or   {sub-path-accessible? (constantly true)}}]]
  (when url-like
    (let [dir? (= (last url-like) \/)
          url-like* (str/split url-like #"/")
          urls (for [i (range 1 (inc (count url-like*)))]
                 (split-at i url-like*))]
      (as-> urls _
            (map (fn [[fst _]]
                   (let [url (str/join "/" fst)
                         url* (str (if (empty? url) (or base "") url) "/")
                         display (str (last fst) "/")]
                     (if (sub-path-accessible? url*)
                       [:a {:href url*} display]
                       [:span display])))
                 _)
            (drop-last _ )
            (concat _ [(str (last url-like*) (if dir? "/" ""))])))))

#_(url->link-segments "/data/plot/aaaa/")

(defn standard-header
  [url-path & get-post]
  (let [get-post* (or get-post [:get :post])
        get-post-str (->> get-post*
                          (map name ,,,)
                          (map #(.toUpperCase %) ,,,)
                          (str/join " | " ,,,))]
    [:h2 (str get-post-str " ")
     (for [segment (url->link-segments url-path)]
       segment)]))

(defn standard-get-layout*
  [{:keys [url-path title description]} & media-type-to-content]
  [:div
   [:h3 (str title " (GET " url-path ")")]
   [:p description]
   [:hr]
   (for [[media-type content] (partition 2 media-type-to-content)]
     [:div
      [:h4 "media-type: " media-type]
      content
      [:hr]])])

(defn standard-get-layout [{:keys [url-path
                                   get-title description
                                   get-id-fn get-name-fn
                                   entities sub-entity-path
                                   leaf-sub-entities?]}]
  [:div
   [:h3 (str get-title " (GET " url-path ")")]
   [:h4 "media-type: text/html"]
   [:p description]
   [:hr]
   [:ul#farms
    (for [e entities]
      [:li [:a {:href (str (util/drop-path-segment url-path) "/"
                           (str/join "/" sub-entity-path) "/"
                           (get-id-fn e) (if leaf-sub-entities? "" "/"))}
            (or (get-name-fn e) (get-id-fn e))]])]
   [:hr]
   [:h4 "media-type: application/edn"]
   [:code (pr-str (map get-id-fn entities))]
   [:hr]])


(defn standard-post-layout*
  [{:keys [url title]} & layout]
  [:div
   [:h3 (str title " (POST " url ")")]
   [:form.form-horizontal {:role :form
                           :method :post
                           :action url}
    layout]])

(defn standard-post-layout [{:keys [url
                                    post-title post-layout-fn]}]
  [:div
   [:h3 (str post-title " (POST " url ")")]
   [:form.form-horizontal {:role :form
                           :method :post
                           :action url}
    (post-layout-fn)]])
