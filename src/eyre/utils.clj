(ns eyre.utils
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [medley.core :as medley]))

(def newlines #"\r\n|\n\r|\r|\n")

(defmacro embed [path]
  (slurp (str "src/eyre/" path)))

(defn parse-sections
  "Splits raw gather output into a map of section-name -> joined string.
  Sections are delimited by `===name===` markers."
  [out]
  (let [re-header #"===(\S+)==="]
    (->> (str/split out newlines)
         (reduce (fn [{:keys [current] :as acc} line]
                   (if-let [[_ header] (re-matches re-header line)]
                     (-> acc
                         (assoc :current header)
                         (assoc-in [:sections header] []))
                     (update-in acc [:sections current] conj line)))
                 {})
         :sections
         (medley/map-vals #(str/join "\n" %)))))

#_ (parse-sections "===begin===
foo
bar
===section-2===
bing
===end===
===end2===")
