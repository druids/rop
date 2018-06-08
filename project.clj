(defproject rop "0.3.0"
  :description "Yet another Railway Oriented Programming in Clojure"
  :url "https://github.com/druids/rop"
  :license {:name "MIT License"
            :url "http://opensource.org/licenses/MIT"}

  :dependencies [[funcool/cats "2.2.0"]]

  :profiles {:dev {:plugins [[lein-cloverage "1.0.10"]
                             [lein-kibit "0.1.6"]
                             [jonase/eastwood "0.2.5"]
                             [lein-eftest "0.5.2"]]
                   :dependencies [[org.clojure/clojure "1.9.0"]
                                  [funcool/struct "1.3.0"]]}})
