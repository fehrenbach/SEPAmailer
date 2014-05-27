(defproject sepamailer "0.0.1-SNAPSHOT"
  :dependencies [[org.clojure/clojure "1.6.0"]
                 [org.clojure/tools.cli "0.3.1"]
                 [com.draines/postal "1.11.1"]
                 [de.ubercode.clostache/clostache "1.4.0"]]
  :main sepa.core
  :aot [sepa.core]
  :plugins [[cider/cider-nrepl "0.7.0-SNAPSHOT"]])
