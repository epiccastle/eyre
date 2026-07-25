(ns eyre.filesystem
  (:require [clojure.string :as str]
            [eyre.utils :as utils :refer [embed newlines]]))

;; Gather facts about a system's mounted filesystems, disk usage and
;; available filesystem security features.
;;
;; `gather-filesystem` takes a hashmap with
;; :exec (an executor function) and :shell (the detected shell map
;; from `eyre.shell/gather-shell`).
;;
;; The output structure is consistent across all platforms:
;;
;;   {:filesystems
;;    [{:device      "/dev/sda1"          ;; backing device / source
;;      :mount-point "/"                  ;; where it is mounted
;;      :type        "ext4"               ;; filesystem type (ntfs, apfs, ...)
;;      :options     "rw,relatime"        ;; mount options (nil on windows)
;;      :size        12345678             ;; total size in bytes (nil if unknown)
;;      :used        6789012              ;; used space in bytes (nil if unknown)
;;      :available   5555666              ;; free space in bytes (nil if unknown)
;;      :capacity    0.55}                ;; used fraction 0.0-1.0 (nil if unknown)
;;     ...]
;;    :features
;;    {:security
;;     {;; linux:
;;      :selinux   {:enabled true :mode :enforcing}
;;      :apparmor  {:enabled true :profiles 42}
;;      ;; macos:
;;      :sip       {:enabled true}
;;      ;; bsd:
;;      :securelevel {:level 1}
;;      ;; windows:
;;      :bitlocker {:enabled true}}}}}

(def ^:private posix-gather-script (embed "filesystem/gather.sh"))
(def ^:private fish-gather-script (embed "filesystem/gather.fish"))
(def ^:private nu-gather-script (embed "filesystem/gather.nu"))
(def ^:private powershell-gather-script (embed "filesystem/gather.ps1"))
(def ^:private cmd-gather-script (embed "filesystem/gather.cmd"))

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

;;
;; helpers
;;

(defn- parse-long-safe [s]
  (when (and s (seq (str/trim s)))
    (try (Long/parseLong (str/trim s))
         (catch Exception _ nil))))

(defn- ratio [used total]
  (if (and used total (pos? total))
    (double (/ used total))
    0.0))

(defn- assoc-some [m k v]
  (if (nil? v) m (assoc m k v)))

;;
;; mount parsing
;;

(defn- parse-mount
  "Parses `mount` output into a list of filesystem maps. Handles both
  the linux format `device on /mount type fstype (options)` and the
  macos/bsd format `device on /mount (fstype, options...)`."
  [s]
  (when (seq s)
    (->> (str/split s newlines)
         (map str/trim)
         (filter seq)
         (keep (fn [line]
                 (or
                   ;; linux: device on mount-point type fstype (options)
                   (when-let [[_ device mp type opts]
                              (re-matches #"^(.+) on (.+) type (\S+) \((.*)\)$" line)]
                     {:device device :mount-point mp :type type :options opts})
                   ;; macos/bsd: device on mount-point (fstype, options...)
                   (when-let [[_ device mp content]
                              (re-matches #"^(.+) on (.+) \((.*)\)$" line)]
                     (let [[type & opts] (str/split content #",\s*")]
                       {:device device
                        :mount-point mp
                        :type (str/trim type)
                        :options (str/join "," (map str/trim opts))}))))))))

;;
;; df parsing
;;

(defn- parse-df
  "Parses `df -P -k` (POSIX 1K-block) output into a map of
  mount-point -> usage map. The `-P` flag keeps each filesystem on a
  single physical line."
  [s]
  (when (seq s)
    (->> (str/split s newlines)
         (map str/trim)
         (filter seq)
         rest
         (keep (fn [line]
                 (let [parts (str/split line #"\s+")]
                   ;; [filesystem blocks used available capacity mount-point...]
                   (when (>= (count parts) 6)
                     (let [device (nth parts 0)
                           blocks (parse-long-safe (nth parts 1))
                           used (parse-long-safe (nth parts 2))
                           available (parse-long-safe (nth parts 3))
                           mount-point (str/join " " (drop 5 parts))]
                       (when (and blocks used available)
                         [mount-point
                          {:device device
                           :mount-point mount-point
                           :size (* blocks 1024)
                           :used (* used 1024)
                           :available (* available 1024)
                           :capacity (ratio used blocks)}]))))))
         (into {}))))

;;
;; filesystem assembly
;;

(defn- merge-mount-and-df
  "Drives the filesystem list off `mount` entries (so every mounted
  filesystem appears) and enriches each with usage data from `df`
  (matched by mount point). Falls back to `df` only when `mount`
  produced nothing."
  [mounts df-map]
  (if (seq mounts)
    (for [mnt mounts]
      (let [mp (:mount-point mnt)
            df (get df-map mp)]
        (cond-> {:device (:device mnt)
                 :mount-point mp
                 :type (:type mnt)
                 :options (:options mnt)}
          df (assoc :size (:size df)
                    :used (:used df)
                    :available (:available df)
                    :capacity (:capacity df)))))
    (for [[_ df] df-map]
      (-> df
          (assoc :type nil :options nil)
          (select-keys [:device :mount-point :type :options
                        :size :used :available :capacity])))))

;;
;; security feature parsing (unix)
;;

(defn- parse-selinux [s]
  (when (seq s)
    (let [lines (map str/trim (str/split s newlines))
          status (some #(second (re-find #"(?i)^(enforcing|permissive|disabled)$" %)) lines)
          enforce (some #(second (re-find #"^enforce:(\d+)" %)) lines)]
      (when (or status enforce)
        (let [mode (when status (keyword (str/lower-case status)))
              enabled (if mode
                        (not= mode :disabled)
                        (= enforce "1"))]
          (cond-> {:enabled enabled}
                  mode (assoc :mode mode)))))))

(defn- parse-apparmor [s]
  (when (seq s)
    (let [loaded? (str/includes? s "loaded:yes")
          ;; aa-status line: "XX profiles are loaded."
          profiles (some #(some-> (re-find #"(\d+)\s+profiles are loaded" %)
                                  second parse-long-safe)
                         (str/split s newlines))]
      (when (or loaded? profiles)
        (cond-> {:enabled (or loaded? (some? profiles))}
                profiles (assoc :profiles profiles))))))

(defn- parse-sip [s]
  (when (seq s)
    (let [enabled? (str/includes? (str/lower-case s) "enabled")]
      {:enabled enabled?})))

(defn- parse-securelevel [s]
  (when-let [level (parse-long-safe s)]
    {:level level}))

;;
;; unix processing
;;

(defn- process-unix [{:strs [mount df selinux apparmor sip securelevel]}]
  (let [mounts (parse-mount (or mount ""))
        df-map (parse-df (or df ""))
        security (-> {}
                     (assoc-some :selinux (parse-selinux selinux))
                     (assoc-some :apparmor (parse-apparmor apparmor))
                     (assoc-some :sip (parse-sip sip))
                     (assoc-some :securelevel (parse-securelevel securelevel)))]
    {:filesystems (vec (merge-mount-and-df mounts df-map))
     :features {:security security}}))

;;
;; windows processing
;;

(defn- parse-volumes-powershell [s]
  (when (seq s)
    (->> (str/split s newlines)
         (map str/trim)
         (filter seq)
         (keep (fn [line]
                 (let [[device fstype size free drive-type] (str/split line #"\|")
                       drive-type (str/trim (or drive-type ""))
                       device (str/trim device)]
                   ;; 3 = local fixed disk, 4 = network, 2 = removable
                   (when (and (seq device)
                              (contains? #{"2" "3" "4"} drive-type))
                     (let [size (parse-long-safe size)
                           free (parse-long-safe free)
                           used (when (and size free) (- size free))]
                       {:device device
                        :mount-point (str device "\\")
                        :type (let [t (str/trim (or fstype ""))]
                                (when (seq t) t))
                        :options nil
                        :size size
                        :used used
                        :available free
                        :capacity (ratio used size)}))))))))

(defn- parse-features-powershell [s]
  (when (seq s)
    (let [protected? (some (fn [line]
                             (let [parts (str/split line #"\|")]
                               (when (>= (count parts) 2)
                                 (= "1" (str/trim (second parts))))))
                           (str/split s newlines))]
      (when (some? protected?)
        {:bitlocker {:enabled protected?}}))))

(defn- parse-powershell [{:strs [volumes features]}]
  (let [security (parse-features-powershell (or features ""))]
    {:filesystems (vec (or (parse-volumes-powershell volumes) []))
     :features {:security (or security {})}}))

(defn- parse-wmic-records
  "Parses wmic `get ... /value` output (key=value lines, blank line
  separated records) into a list of keyword->string maps."
  [s]
  (when (seq s)
    (->> (str/split s newlines)
         (map str/trim)
         (filter seq)
         (reduce (fn [acc line]
                   (if-let [[_ k v] (re-find #"^([^=]+)=(.*)$" line)]
                     (let [k-kw (keyword (str/lower-case (str/trim k)))
                           v-str (str/trim v)
                           last-rec (last acc)]
                       (if (and last-rec (contains? last-rec k-kw))
                         (conj acc {k-kw v-str})
                         (if (seq acc)
                           (update acc (dec (count acc)) assoc k-kw v-str)
                           [{k-kw v-str}])))
                     acc))
                 []))))

(defn- parse-volumes-cmd [s]
  (when (seq s)
    (keep (fn [rec]
            (let [device (:deviceid rec)
                  drive-type (:drivetype rec)]
              ;; 3 = local fixed disk, 4 = network, 2 = removable
              (when (and device (contains? #{"2" "3" "4"} drive-type))
                (let [size (parse-long-safe (:size rec))
                      free (parse-long-safe (:freespace rec))
                      used (when (and size free) (- size free))
                      fstype (:filesystem rec)]
                  {:device device
                   :mount-point (str device "\\")
                   :type (when (seq fstype) fstype)
                   :options nil
                   :size size
                   :used used
                   :available free
                   :capacity (ratio used size)}))))
          (parse-wmic-records s))))

(defn- process-cmd-exe [{:strs [volumes]}]
  {:filesystems (vec (or (parse-volumes-cmd volumes) []))
   :features {:security {}}})

(defn gather-filesystem
  "Gathers mounted filesystem, disk usage and filesystem security
  feature facts from the host reachable via `exec`.

  ## Arguments

  Takes a map with:

  - `:exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`.
  - `:shell` - the shell map returned by `eyre.shell/gather-shell`;
    its `:type` selects which embedded collection script is run.
    Shells without a specific script fall back to the POSIX script.

  ## Returns

  A map with:

  - `:filesystems` - a vector of mounted filesystem maps (see below).
  - `:features` - a map `{:security {...}}` of optional, platform
    specific security feature detections (see below).

  Each `:filesystems` entry has:

  - `:device` - backing device or mount source, e.g. `\"/dev/sda1\"`.
  - `:mount-point` - where it is mounted, e.g. `\"/\"`.
  - `:type` - filesystem type string, e.g. `\"ext4\"`, `\"apfs\"`,
    `\"ntfs\"`.
  - `:options` - mount options string, e.g. `\"rw,relatime\"` (`nil`
    on Windows).
  - `:size`, `:used`, `:available` - sizes in bytes (`nil` when
    unknown).
  - `:capacity` - used fraction as a double between 0.0 and 1.0
    (`nil` when unknown).

  On unix the list is driven by `mount` output enriched with usage
  data from `df -P -k`; on Windows fixed, removable and network
  volumes are enumerated via PowerShell or `wmic`.

  The `:features` `:security` map contains only the detections
  relevant to the platform:

  - `:selinux` - `{:enabled bool :mode :enforcing|:permissive}` (linux)
  - `:apparmor` - `{:enabled bool :profiles int}` (linux)
  - `:sip` - `{:enabled bool}` (macOS System Integrity Protection)
  - `:securelevel` - `{:level int}` (BSD)
  - `:bitlocker` - `{:enabled bool}` (Windows)

  ## Example

  ```clojure
  (filesystem/gather-filesystem {:exec local-exec :shell shell})
  ;; => {:filesystems [{:device \"/dev/sda1\" :mount-point \"/\"
  ;;                    :type \"ext4\" :options \"rw,relatime\"
  ;;                    :size 1099511627776 :used 549755813888
  ;;                    :available 549755813888 :capacity 0.5}]
  ;;     :features {:security {:apparmor {:enabled true :profiles 42}}}}
  ```"
  [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "filesystem gathering script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (condp = shell-type
        :powershell (parse-powershell sections)
        :cmd-exe    (process-cmd-exe sections)
        (process-unix sections)))))
