(ns sepa.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :refer [as-file]])
  (:gen-class))

(defn file-exists? [path]
  (.exists (as-file path)))

(def cli-options
  [[nil "--template TEMPLATEFILE" "Template file (mustache syntax)"
    :validate [file-exists? "Template file must be the name of an existing file."]]
   [nil "--csv CSVFILE" "CSV file"
    :validate [file-exists? "CSV file must be the name of an existing file."]]])

(defn parse-arguments [args]
  (let [{:keys [errors options]} (parse-opts args cli-options)]
    (when errors
      (doseq [error errors]
        (println error))
      (System/exit 1))
    (when-not (:template options)
      (println "Please pass in the name of a template to use.")
      (System/exit 2))
    (when-not (:csv options)
      (println "Please pass in the name of a CSV file to use.")
      (System/exit 3))
    options))

(defn -main [& args]
  (let [options (parse-arguments args)
;        data (read-data options)
;        template (read-template options)
;        mails (make-mails options data template)
        ]
;    (send-mails mails options)
    options))

;; Guess mime type
;; http://docs.oracle.com/javase/7/docs/api/java/nio/file/spi/FileTypeDetector.html
