(ns sepa.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :refer [as-file reader]]
            [clojure.data.csv :refer [read-csv]]
            [clostache.parser :refer [render]])
;  (:import [org.apache.commons.validator.routines.EmailValidator])
  (:import [java.nio.file.spi.FileTypeDetector])
  (:gen-class))

(comment (defn email?
           "Returns true if the email address is valid, otherwise false."
           [address]
           (.isValid (org.apache.commons.validator.routines.EmailValidator/getInstance)
                     address)))

(defn file-exists? [path]
  (.exists (as-file path)))

(def cli-options
  [[nil "--template TEMPLATEFILE" "Template file (mustache syntax)"
    :validate [file-exists? "Template file must be the name of an existing file."]]
   [nil "--csv CSVFILE" "CSV file"
    :validate [file-exists? "CSV file must be the name of an existing file."]]
   [nil "--csv-delimiter DELIMITER" "Character used to separate columns, defaults to \";\"."
    :default \;
    :parse-fn (fn [s] (if-not (= 1 (count s))
                        (throw (Exception. (str "CSV delimiter must be a single character, was: " s)))
                        (first s)))]
   [nil "--csv-mail-column COLUMNNAME" "Column in the CSV file that contains the e-mail address, defaults to \"EMail\"."
    :default :EMail
    :parse-fn keyword]
   [nil "--mail-from FROMADDRESS" "Email address to use in from field, defaults to '\"Universitätschor Marburg e.V. - Finanzen\" <finanzen@unichor-marburg.de>'."
    :default "\"Universitätschor Marburg e.V. - Finanzen\" <finanzen@unichor-marburg.de>"
    ;;    :validate [email? "Must be a well-formed email address, see the default for an example."]
    ]
   [nil "--mail-subject SUBJECT" "Subject line for e-mails, defaults to \"SEPA\"."
    :default "SEPA"]
   [nil "--attachment ATTACHMENTFILE" "File to attach (the same to all messages)."
    :validate [file-exists? "Attachment file must be the name of an existing file, if provided at all."]]
   ])

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

(defn rows-to-maps
  [names rows]
  (map (fn [row]
         (into {} (map-indexed (fn [i cell-content]
                                 [(get names i) cell-content])
                               row)))
       rows))

(defn read-data
  "Read CSV file into a sequence of maps.
Input format: CSV with first row being names of columns (valid clojure keyword syntax).
Output format: Rows from the second, as a sequence of maps from the name of the column (converted to a keyword) to its value."
  [{:keys [csv csv-delimiter]}]
  (with-open [csv-file (reader csv)]
    (let [[first-row & data-rows] (read-csv csv-file :separator csv-delimiter)
          column-names (into {} (map-indexed (fn [i column-name] [i (keyword column-name)]) first-row))]
      ;; Not sure about with-open and lazyness of data.csv. Just force all rows.
      (doall (rows-to-maps column-names data-rows)))))

(defn read-template
  [{:keys [template]}]
  ;; TODO maybe pass encoding (slurp template :encoding "encoding-here")
  (slurp template))

(defn guess-mime-type [file-path]
  (java.nio.file.Files/probeContentType (.toPath (as-file file-path))))

(defn mail-body [{:keys [attachment]} data template]
  (let [text-content {:type "text/plain; charset=utf-8"
                      :content (render template data)}]
    (if attachment
      [text-content {:type "attachment"
                     :content-type (guess-mime-type attachment)
                     :content (as-file attachment)}]
      text-content)))

(defn make-mail
  [{:keys [mail-from csv-mail-column mail-subject] :as options} data template]
  {:from mail-from
   :to (get data csv-mail-column)
   :subject mail-subject
   :body (mail-body options data template)})

(defn make-mails
  [options data template]
  (map #(make-mail options % template) data))

(defn -main [& args]
  (let [options (parse-arguments args)
        data (read-data options)
        template (read-template options)
        mails (make-mails options data template)]
;    (send-mails mails options)
    (println options)
    (doseq [mail mails]
      (println mail))))
