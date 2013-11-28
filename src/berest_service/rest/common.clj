(ns berest-service.rest.common
  (:require [hiccup
             [element :as he]
             [page :as hp]
             [def :as hd]]))

#_(hd/defhtml layout [title & content]
            (hp/html5 {:xml? true}
                      [:head
                       [:title title]
                       (hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")
                       [:style
                        "body { margin: 2em; }"
                        "textarea { width: 80%; height: 200px }"]]
                      [:body content]))

(hd/defhtml layout [title & content]
            (hp/html5
             [:head
              (when-not (empty? title) [:title title])
              [:meta {:name "viewport"
                      :content "width=device-width, initial-scale=1.0"}]
              (hp/include-css "//netdna.bootstrapcdn.com/bootstrap/3.0.2/css/bootstrap.min.css")]
             [:body content]))

(comment
  "
  <!DOCTYPE html>
  <html>
  <head>
    <title>Bootstrap 101 Template</title>
    <meta name="viewport" content="width=device-width, initial-scale=1.0">
    <!-- Bootstrap -->
    <link href="css/bootstrap.min.css" rel="stylesheet">

    <!-- HTML5 Shim and Respond.js IE8 support of HTML5 elements and media queries -->
    <!-- WARNING: Respond.js doesn't work if you view the page via file:// -->
    <!--[if lt IE 9]>
      <script src="https://oss.maxcdn.com/libs/html5shiv/3.7.0/html5shiv.js"></script>
      <script src="https://oss.maxcdn.com/libs/respond.js/1.3.0/respond.min.js"></script>
    <![endif]-->
  </head>
  <body>
    <h1>Hello, world!</h1>

    <!-- jQuery (necessary for Bootstrap's JavaScript plugins) -->
    <script src="https://code.jquery.com/jquery.js"></script>
    <!-- Include all compiled plugins (below), or include individual files as needed -->
    <script src="js/bootstrap.min.js"></script>
  </body>
 </html>
  ")

#_(hd/defhtml layout+js [& content]
  (hp/html5 {:xml? true}
    [:head
     [:title "Berest"]
     (hp/include-css "//netdna.bootstrapcdn.com/twitter-bootstrap/2.3.0/css/bootstrap-combined.min.css")
     #_(include-css "/css/reset.css")
     #_(include-js "/js/g.raphael-min.js")
     #_(include-js "/js/g.line-min.js")
     (hp/include-js "/cljs/main.js")
     [:style
      "body { margin: 2em; }"
      "textarea { width: 80%; height: 200px }"]]
    [:body
     content]))

#_(hd/defhtml layout-webfui [name]
         (hp/html5
          [:head
           [:title "webfui-client"]
           #_(hp/include-css (str "/css/" name ".css"))
           #_(he/javascript-tag "var CLOSURE_NO_DEPS = true;")
           (hp/include-js (str "/cljs/" name ".js"))]
          [:body]))

