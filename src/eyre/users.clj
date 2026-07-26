(ns eyre.users
  (:require [clojure.string :as string]
            [eyre.utils :as utils :refer [embed newlines]]))

(def ^:private posix-gather-script (embed "users/gather.sh"))
(def ^:private fish-gather-script (embed "users/gather.fish"))
(def ^:private nu-gather-script (embed "users/gather.nu"))
(def ^:private powershell-gather-script (embed "users/gather.ps1"))
(def ^:private cmd-gather-script (embed "users/gather.cmd"))

(def ^:private gather-scripts
  {:bash       posix-gather-script
   :zsh        posix-gather-script
   :sh         posix-gather-script
   :dash       posix-gather-script
   :ksh        posix-gather-script
   :busybox    posix-gather-script
   :fish       fish-gather-script
   :nu         nu-gather-script
   :powershell powershell-gather-script
   :cmd-exe    cmd-gather-script})

(defn gather-script
  "Returns the embedded collection script for `shell-type`. Falls back
  to the POSIX script when no specific script is available."
  [shell-type]
  (or (gather-scripts shell-type) posix-gather-script))

(def ^:private windows-shell-types #{:powershell :cmd-exe})

(defn- process-id-name-substring [substring]
  (let [[_ id name] (re-matches #"(\d+)\(([\d\w_\.\-]+)\)" substring)]
    {:id (Integer/parseInt id)
     :name name}))

(defn- process-id [id-out]
  (let [{:keys [gid uid groups]}
        (-> id-out string/trim (string/split #"\s+")
            (->> (take 3)
                 (map (fn [line]
                        (let [[type val] (string/split line #"=" 2)
                              vals (->> (string/split val #",")
                                        (mapv process-id-name-substring))]
                          [(keyword type) vals])))
                 (into {})))]
    {:gid (first gid)
     :uid (first uid)
     :groups groups
     :group-ids (into #{} (map :id groups))
     :group-names (into #{} (map :name groups))
     }))

(defn- parse-csv-line
  "Parse a simple CSV line with quoted fields."
  [line]
  (when line
    (loop [chars (seq line)
           in-quote? false
           current ""
           fields []]
      (if (seq chars)
        (let [c (first chars)
              cs (rest chars)]
          (cond
            (= c \")
            (recur cs (not in-quote?) current fields)
            (and (= c \,) (not in-quote?))
            (recur cs false "" (conj fields current))
            :else
            (recur cs in-quote? (str current c) fields)))
        (conj fields current)))))

(defn- windows-sid? [s]
  (boolean (re-matches #"S-1-\d+.*" s)))

(defn- parse-windows-id [id-section]
  (let [[name id] (parse-csv-line (string/trim (or id-section "")))]
    {:name (string/trim (or name ""))
     :id (string/trim (or id ""))}))

(defn- parse-windows-groups [groups-section]
  (->> (string/split groups-section newlines)
       (map string/trim)
       (filter seq)
       (map parse-csv-line)
       (keep (fn [[group-name _type sid _attrs]]
               (when (and (seq group-name) (windows-sid? sid))
                 {:name (string/trim group-name)
                  :id (string/trim sid)})))
       vec))

(defn- process-windows [{:strs [id groups]}]
  (let [uid (parse-windows-id id)
        groups (parse-windows-groups groups)]
    {:uid uid
     :gid nil
     :groups groups
     :group-ids (into #{} (map :id groups))
     :group-names (into #{} (map :name groups))}))

(defn process-users
  "Processes parsed users collection sections into a users facts map."
  [shell-type sections]
  (if (windows-shell-types shell-type)
    (process-windows sections)
    (process-id (sections "id"))))

(defn gather-users
  "Gathers identity and group membership facts for the user the
  executor runs as on the host reachable via `exec`.

  ## Arguments

  Takes a map with:

  - `:exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`.
  - `:shell` - the shell map returned by `eyre.shell/gather-shell`;
    its `:type` selects which embedded collection script is run.
    Shells without a specific script fall back to the POSIX script.

  ## Returns

  A map with:

  - `:uid` - `{:id :name}` for the executing user.
  - `:gid` - `{:id :name}` for the user's primary group, or `nil` on
    Windows.
  - `:groups` - vector of `{:id :name}` maps, one per group the user
    belongs to (including the primary group).
  - `:group-ids` - set of the `:id` values from `:groups`.
  - `:group-names` - set of the `:name` values from `:groups`.

  On unix (data from `id`) ids are integers and names are strings:

  ```clojure
  {:uid {:id 1000 :name \"crispin\"}
   :gid {:id 1000 :name \"crispin\"}
   :groups [{:id 1000 :name \"crispin\"} {:id 998 :name \"wheel\"}]
   :group-ids #{1000 998}
   :group-names #{\"crispin\" \"wheel\"}}
  ```

  On Windows (`:powershell` / `:cmd-exe` shells) there is no uid/gid
  concept; instead `:uid` and each `:groups` entry carry the account
  name and its SID string, and `:gid` is `nil`:

  ```clojure
  {:uid {:name \"HOST\\\\user\" :id \"S-1-5-21-...\"}
   :gid nil
   :groups [{:name \"Administrators\" :id \"S-1-5-32-544\"} ...]
   :group-ids #{\"S-1-5-32-544\" ...}
   :group-names #{\"Administrators\" ...}}
  ```"
  [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (gather-script shell-type)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "users gathering script exited non zero: " exit " " err))
    (process-users shell-type (utils/parse-sections out))))