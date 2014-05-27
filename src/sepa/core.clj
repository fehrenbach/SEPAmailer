(ns sepa.core
  (:require [clojure.tools.cli :refer [parse-opts]])
  (:gen-class))

(defn -main [& args]
  (println "sepamailer"))

;; Guess mime type
;; http://docs.oracle.com/javase/7/docs/api/java/nio/file/spi/FileTypeDetector.html
