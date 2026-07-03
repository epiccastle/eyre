(ns eyre.os
  (:require [clojure.string :as str]
            [eyre.utils :refer [embed]]))

;; detect the operating system the shell is running on. including kernel.

;; a function `determine-os` that takes a hashmap as an argument.
;; that hashmap has a key :exec that contains an executer function like
;; eyre.shell/determine-shell
;; the argument hashmap also has a key :shell that contains the
;; detected shell from `determine-shell`

(def posix-gather-script (embed "os/gather.sh"))
(def fish-gather-script (embed "os/gather.fish"))
(def nu-gather-script (embed "os/gather.nu"))
(def powershell-gather-script (embed "os/gather.ps1"))
(def cmd-gather-script (embed "os/gather.cmd"))

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

(defn- parse-sections
  "Splits raw gather output into a map of section-name -> joined string.
  Sections are delimited by `===name===` markers."
  [out]
  (->> (str/split out #"\r?\n")
       (reduce (fn [{:keys [current sections] :as acc} line]
                 (if-let [[_ name] (re-matches #"===(\S+)===" line)]
                   (-> acc
                       (assoc :current name)
                       (assoc-in [:sections name] []))
                   (if current
                     (update-in acc [:sections current] conj line)
                     acc)))
               {:current nil :sections {}})
       :sections
       (reduce-kv (fn [m k v]
                    (assoc m k (str/trim (str/join "\n" v))))
                  {})))

(defn- parse-kv
  "Parses `key=value` lines into a keyword->string map. Surrounding
  double quotes are stripped from values."
  [content]
  (->> (str/split-lines content)
       (map str/trim)
       (filter #(and (seq %) (str/includes? % "=")))
       (map #(str/split % #"=" 2))
       (filter #(= 2 (count %)))
       (map (fn [[k v]]
              [(keyword (str/lower-case k))
               (-> v
                   (str/replace #"^\"" "")
                   (str/replace #"\"$" "")
                   str/trim)]))
       (into {})))

(defn- parse-kv-colon
  "Parses `key: value` lines into a keyword->string map."
  [content]
  (->> (str/split-lines content)
       (map str/trim)
       (filter #(str/includes? % ":"))
       (map #(str/split % #":" 2))
       (filter #(= 2 (count %)))
       (map (fn [[k v]] [(keyword (str/lower-case k)) (str/trim v)]))
       (into {})))

(defn- parse-uname-section
  "Parses the `===uname===` section (lines like `s:Linux`) into a
  string map keyed by the uname flag letter."
  [uname]
  (->> (str/split-lines uname)
       (map str/trim)
       (filter seq)
       (map #(str/split % #":" 2))
       (filter #(= 2 (count %)))
       (into {})))

(def ^:private mac-codenames
  {"10.0"  :cheetah
   "10.1"  :puma
   "10.2"  :jaguar
   "10.3"  :panther
   "10.4"  :tiger
   "10.5"  :leopard
   "10.6"  :snow-leopard
   "10.7"  :lion
   "10.8"  :mountain-lion
   "10.9"  :mavericks
   "10.10" :yosemite
   "10.11" :el-capitan
   "10.12" :sierra
   "10.13" :high-sierra
   "10.14" :mojave
   "10.15" :catalina
   "11"    :big-sur
   "12"    :monterey
   "13"    :ventura
   "14"    :sonoma
   "15"    :sequoia})

(defn- guess-mac-codename [version]
  (when (seq version)
    (let [[_ key] (re-matches #"(10\.\d+|\d+)\..*" version)]
      (get mac-codenames key))))

(defn- family-from-kernel-name [name]
  (case name
    "Linux"     :linux
    "Darwin"    :darwin
    "FreeBSD"   :freebsd
    "NetBSD"    :netbsd
    "OpenBSD"   :openbsd
    "DragonFly" :dragonfly
    "SunOS"     :sunos
    "AIX"       :aix
    (keyword (str/lower-case name))))

(defn- process-unix [sections]
  (let [uname (parse-uname-section (get sections "uname"))
        kernel-name (get uname "s")
        family (family-from-kernel-name kernel-name)
        base {:family  family
              :kernel  {:name    kernel-name
                        :release (get uname "r")
                        :version (get uname "v")}
              :machine (get uname "m")}]
    (cond
      (= family :linux)
      (let [osr (parse-kv (get sections "os-release"))
            lsb (parse-kv (get sections "lsb-release"))
            pick (fn [k & ks] (some identity (map #(get % k) (cons osr (cons lsb ks)))))]
        (assoc base :distro
               {:id          (some-> (pick :id :distributor_id) str/lower-case keyword)
                :name        (pick :name)
                :release     (pick :version_id :release)
                :codename    (some-> (pick :version_codename :codename) str/lower-case keyword)
                :description (pick :pretty_name :description)}))

      (= family :darwin)
      (let [sw (parse-kv-colon (get sections "sw-vers"))
            release (:productversion sw)]
        (assoc base :distro
               {:id       :macos
                :name     (:productname sw)
                :release  release
                :codename (guess-mac-codename release)
                :build    (:buildversion sw)}))

      :else base)))

(defn- normalize-windows-arch
  "Maps Windows PROCESSOR_ARCHITECTURE values to the uname -m naming
  used on posix systems (x86_64, aarch64, i386)."
  [arch]
  (case arch
    "AMD64" "x86_64"
    "ARM64" "aarch64"
    "IA64"  "ia64"
    "x86"   "i386"
    (str/lower-case arch)))

(defn- process-windows [sections]
  (let [ver (get sections "ver")
        [_ vstr] (re-find #"[vV]ersion ([\d.]+)" ver)
        osinfo (parse-kv (get sections "osinfo"))
        arch (str/trim (get sections "arch"))]
    {:family  :windows
     :kernel  {:name    "Windows"
               :release (or vstr (:version osinfo))}
     :machine (normalize-windows-arch arch)
     :distro  {:id      :windows
               :caption (:caption osinfo)
               :release (:version osinfo)
               :build   (:buildnumber osinfo)}}))

(defn determine-os [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (get gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "os determination script exited non zero: " exit " " err))
    (let [sections (parse-sections out)]
      (if (windows-shell-types shell-type)
        (process-windows sections)
        (process-unix sections)))))
