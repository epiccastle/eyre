(ns eyre.users
  (:require [clojure.string :as string]
            [eyre.utils :as utils :refer [embed newlines]]))

(def posix-gather-script (embed "users/gather.sh"))
(def fish-gather-script (embed "users/gather.fish"))
(def nu-gather-script (embed "users/gather.nu"))
(def powershell-gather-script (embed "users/gather.ps1"))
(def cmd-gather-script (embed "users/gather.cmd"))

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

(def ^:private windows-shell-types #{:powershell :cmd-exe})

(defn process-id-name-substring [substring]
  (let [[_ id name] (re-matches #"(\d+)\(([\d\w_\.\-]+)\)" substring)]
    {:id (Integer/parseInt id)
     :name name}))

(defn process-id [id-out]
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

(defn determine-users [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "users determination script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (if (windows-shell-types shell-type)
        (process-windows sections)
        (process-id (sections "id"))))))

#_ (process-id "uid=1000(crispin) gid=1000(crispin) groups=1000(crispin),3(sys),90(network),98(power),950(libvirt),960(docker),962(autologin),991(lp),992(kvm),994(input),996(audio),998(wheel)")

;; =>
#_ {:gid {:id 1000, :name "crispin"},
    :uid {:id 1000, :name "crispin"},
    :groups
    [{:id 1000, :name "crispin"}
     {:id 3, :name "sys"}
     {:id 90, :name "network"}
     {:id 98, :name "power"}
     {:id 950, :name "libvirt"}
     {:id 960, :name "docker"}
     {:id 962, :name "autologin"}
     {:id 991, :name "lp"}
     {:id 992, :name "kvm"}
     {:id 994, :name "input"}
     {:id 996, :name "audio"}
     {:id 998, :name "wheel"}],
    :group-ids #{950 998 992 994 1000 90 996 962 3 991 98 960},
    :group-names
    #{"lp"
      "libvirt"
      "wheel"
      "power"
      "network"
      "input"
      "docker"
      "audio"
      "kvm"
      "sys"
      "autologin"
      "crispin"}}
