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

(defn parse-kv
  "Parses `key=value` lines into a keyword->string map. Surrounding
  double quotes are stripped from values."
  [content]
  (->> (str/split content newlines)
       (map str/trim)
       (map #(str/split % #"\s*=\s*" 2))
       (keep (fn [[k v]]
               (when v
                 [(keyword (str/lower-case k))
                  (if (and (str/starts-with? v "\"")
                           (str/ends-with? v "\""))
                    (edn/read-string v)
                    v)])))
       (into {})))

#_ (parse-kv "aaa=1 2 3
b-c-d = \"foo bar\"
extra line
bax-bing = bing = bong ")
