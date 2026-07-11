(ns eyre.utils
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [medley.core :as medley]))

(def newlines #"\r\n|\n\r|\r|\n")

(defmacro embed [path]
  (slurp (str "src/eyre/" path)))

(defn rejoin-lines [lines]
  (let [interleaved (concat (interpose "\n" lines) ["\n"])
        c (* 2 (quot (count interleaved) 2))]
    (str/join "" (take c interleaved))))

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
         (medley/map-vals #(let [data (rejoin-lines %)]
                             (when (seq data)
                               data))))))

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

(defn parse-kv-colon
  "Parses `key: value` lines into a keyword->string map."
  [content]
  (->> (str/split content newlines)
       (map str/trim)
       (map #(str/split % #"\s*:\s*" 2))
       (keep (fn [[k v]]
               (when v
                 [(keyword (str/lower-case k)) (str/trim v)])))
       (into {})))

#_ (parse-kv-colon
     "
key: value
key 2 : value number 2
extra line
key3 : foo
"
     )

;;
;; network utils
;;

(defn keywordize-status [status]
  (let [status (str/lower-case status)]
    (cond
      (#{"up" "active" "connected"} status) :up
      (#{"down" "inactive" "disconnected"} status) :down
      :else :unknown)))

(defn normalize-mac
  "Normalizes a mac address to colon-separated lower case. Accepts
  colon, dash, or no separators."
  [mac]
  (-> mac
      str/trim
      (str/replace #"[^0-9a-fA-F]" "")
      str/lower-case
      (->> (re-seq #"[0-9a-fA-F]{2}")
           (str/join ":"))))

(defn parse-prefix
  "Parses a netmask like `255.255.255.0` or `0xffffff00` into a prefix
  length. If already a number string returns the int."
  [p]
  (cond
    (re-matches #"\d+" p)
    (edn/read-string p)

    (str/includes? p ".")
    (let [bits (->> (str/split p #"\.")
                    (map #(Integer/parseInt %))
                    (map #(Integer/toString % 2))
                    (apply str))]
      (count (filter #{\1} bits)))

    (str/starts-with? p "0x")
    (let [hex (subs p 2)]
      (when (re-matches #"[0-9a-fA-F]+" hex)
        (.bitCount (BigInteger. hex 16))))

    :else nil))
