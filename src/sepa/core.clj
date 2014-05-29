(ns sepa.core
  (:require [clojure.tools.cli :refer [parse-opts]]
            [clojure.java.io :refer [as-file reader]]
            [clojure.data.csv :refer [read-csv]]
            [clostache.parser :refer [render]]
            [postal.core :refer [send-message]])
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
   [nil "--smtp-server SERVER" "SMTP server to use."]
   [nil "--smtp-user USER" "Username for SMTP server."]
   ])

(defn missing-required-option [option]
  (println "Please pass the required option" option)
  (println "Usage:")
  (println (:summary (parse-opts cli-options))))

(defn parse-arguments [args]
  (let [{:keys [errors options summary]} (parse-opts args cli-options)]
    (when errors
      (doseq [error errors]
        (println error))
      (System/exit 1))
    (doseq [option [:template :csv :smtp-server :smtp-user]]
      (when-not (get options option)
        (println "Please pass the required option" (str "--" (name option)))
        (println "Usage:")
        (println summary)
        (System/exit 1)))
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
      [text-content {:type :attachment
                     :content-type (guess-mime-type attachment)
                     :content (as-file attachment)}]
      [text-content])))

(defn make-mail
  [{:keys [mail-from csv-mail-column mail-subject] :as options} data template]
  {:from mail-from
   :to (get data csv-mail-column)
   :subject mail-subject
   :body (mail-body options data template)})

(defn make-mails
  [options data template]
  (map #(make-mail options % template) data))

(defn read-password [msg]
  (apply str (.readPassword (. System console) msg (into-array Object []))))

(defn print-mail [mail]
  (println "Meta info:" (dissoc mail :body))
  (println "Body:" (:body mail)))

(def dont-ask-again (atom nil))

(defn send-this-mail?
  [mail]
  (if @dont-ask-again
    true
    (do
      (println "You are about to send the following email:")
      (print-mail mail)
      (println "Do you really want to send this email?
[Y]es/Yes to [a]ll/[N]o, not this one/Don't send this or any more mails and [q]uit.")
      (loop []
        (let [c (read-line)]
          (case c
            ("Y" "y") true
            ("N" "n") false
            ("A" "a") (reset! dont-ask-again true)
            ("Q" "q") (System/exit 0)
            (recur)))))))

(defn remove-mails-with-no-address
  [mails]
  (remove (fn [mail] (or (not (:to mail))
                         (= "" (:to mail))))
          mails))

(defn send-mails
  [{:keys [smtp-server smtp-user]} mails]
  (let [pass (read-password "Please provide your SMTP password:")]
    (doseq [mail mails]
      (when (send-this-mail? mail)
        (let [res (send-message {:host smtp-server
                                 :user smtp-user
                                 :pass pass
                                 :ssl true}
                                mail)]
          (when-not (= (:error res) :SUCCESS)
            (println "Something went wrong!")
            (println res)
            (println mail)
            (println smtp-server smtp-user)
            (System/exit 1))
          (println "Mail sent to" (:to mail)))))))

(defn -main [& args]
  (let [options (parse-arguments args)
        data (read-data options)
        template (read-template options)
        mails (remove-mails-with-no-address (make-mails options data template))]
    (when (empty? mails)
      (println "No mails to be sent. This is probably a mistake. Did you pass the correct --csv-mail-column option? How about the --csv-delimiter?")
      (println "Command line options:" options)
      (println "CSV was parsed to this:" data))
    (send-mails options mails)))
