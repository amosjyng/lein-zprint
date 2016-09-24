(ns leiningen.zprint
    (:require
     [zprint.core :as zp :exclude [zprint]]
     [trptcolin.versioneer.core :as version]
     [me.raynes.fs :as fs])
    (:import [java.io File]))

(defn lein-zprint-about
  "Return version of this program."
  []
  (str "lein zprint " (version/get-version "lein-zprint" "lein-zprint")))

(defn zprint-about
  "Return version of zprint library program."
  []
  (str "zprint-" (version/get-version "zprint" "zprint")))

(defn vec-str-to-str
  "Take a vector of strings and concatenate them into one string with
  newlines between them."
  [vec-str]
  (apply str (interpose "\n" vec-str)))

;!zprint {:format :next :vector {:wrap? false}}

(def help-str
  (vec-str-to-str
    [(lein-zprint-about)
     (zprint-about)
     ""
     " lein zprint reformats Clojure source files from scratch using the"
     " zprint library, ignoring any line breaks or whitespace in the files."
     " It replaces the files by new ones that are completely reformatted."
     " The existing files are renamed by appending .old to the previous name."
     ""
     " To reformat one file in myproject:"
     ""
     "   lein zprint src/myproject/core.clj"
     ""
     " To reformat all of the Clojure source files in myproject:"
     ""
     "   lein zprint src/myproject/*.clj"
     ""
     " Make sure you run lein zprint with the current directory set to"
     " the directory containing your project.clj file."
     ""
     " To configure lein zprint, you can:"
     ""
     "   - create a $HOME/.zprintrc file containing a zprint options map"
     "   - define environment variables as described in the zprint doc"
     "   - add a :zprint {<options-map>} to project.clj"
     "   - place a number first in the arguments, which will become the width"
     "   - place an options map (surrounded by double quotes) first in the"
     "     arguments, which will be used to configure zprint (if there is a"
     "     number first, the map can be second in the arguments)"
     ""
     " You can place the token :explain anywhere you can place a file name"
     " and the current options will be output to standard out."
     ""
     " Within a file, you can control the function of the zprint formatter"
     " with lines that start with ;!zprint, and contain an options map."
     ""
     " ;!zprint <options>       perform a (set-options! <options>)"
     "                          Will use these options for the rest of the"
     "                          file (or until they are changed again)"
     ""
     " ;!zprint {:format :off}  turn off formatting"
     ""
     " ;!zprint {:format :on}   turn on formattting"
     ""
     " ;!zprint {:format :skip} do not format the next element that is"
     "                          not a comment and not whitespace"
     ""
     " ;!zprint {:format :next <options>}"
     "                          Format only the next non-comment non-whitespace"
     "                          element with the specified <options>"
     ""
     " You can type: lein zprint :help to get this text."
     ""]))

(defn zprint-one-file
  "Take a file name, possibly including a path, and zprint that one file."
  [project-options file-spec]
  (cond
    (= file-spec ":explain") (do (println (lein-zprint-about))
                                 (println (zprint-about))
                                 (zp/czprint nil :explain))
    (= file-spec ":about") (println (lein-zprint-about))
    (= file-spec ":help") (println help-str)
    :else
      (let [parent-path (fs/parent file-spec)
            tmp-name (fs/temp-name "zprint")
            tmp-file (str parent-path File/separator tmp-name)
            old-file (str file-spec ".old")]
        (println "Processing file:" file-spec)
        (try
          (zp/configure-all!)
          (zp/set-options! project-options ":zprint map in project.clj")
          (zp/zprint-file file-spec (fs/base-name file-spec) tmp-file)
          (when (:old? (zp/get-options))
            (fs/delete old-file)
            (fs/rename file-spec old-file))
          (fs/rename tmp-file file-spec)
          (catch
            Exception
            e
            (println
              (str "Unable to process file: "
                   file-spec
                   " because: "
                   e
                   " Leaving it unchanged!")))))))

(defn ^:no-project-needed zprint
  "Pretty-print all of the arguments that are not a map, replacing the
  existing file with the pretty printed one.  The old one is kept around
  with a .old extension.  If the arg is a map, it is considered an options
  map and subsequent files are pretty printed with those options."
  [project & args]
  (let [project-options (:zprint project)
        ; do the project options here, so we see if they work
        _ (when project-options
            (zp/set-options! project-options ":zprint map in project.clj"))
        arg1 (try (read-string (first args)) (catch Exception e nil))
        args (cond
               (map? arg1) (do (zp/set-options! arg1 "first arg to lein zprint")
                               (next args))
               (number? arg1)
                 (do
                   (zp/set-options! {:width arg1})
                   (if-let [arg2 (try (read-string (second args))
                                      (catch Exception e nil))]
                     (cond (map? arg2) (do (zp/set-options!
                                             arg2
                                             "second arg to lein zprint")
                                           (nnext args))
                           :else (next args))
                     (next args)))
               :else args)]
    (doseq [file-spec args] (zprint-one-file project-options file-spec))
    (flush)))
       