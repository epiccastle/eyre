(ns eyre.os
  (:require [clojure.string :as str]
            [eyre.utils :as utils :refer [embed]]))

;; detect the operating system the shell is running on. including kernel.

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
      (mac-codenames key))))

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

(defn- process-unix [{:strs [uname os-release lsb-release] :as sections}]
  (let [uname (utils/parse-kv-colon uname)
        kernel-name (:s uname)
        family (family-from-kernel-name kernel-name)
        base {:family  family
              :kernel  {:name    kernel-name
                        :release (:r uname)
                        :version (:v uname)}
              :machine (:m uname)}]
    (case family
      :linux
      (let [os-release (utils/parse-kv os-release)
            lsb-release (when lsb-release (utils/parse-kv lsb-release))
            pick (fn [k & ks] (some identity (map #(get % k) (cons os-release (cons lsb-release ks)))))]
        (assoc base :distro
               {:id          (some-> (pick :id :distributor_id) str/lower-case keyword)
                :name        (pick :name)
                :release     (pick :version_id :release)
                :codename (let [c (pick :version_codename :codename)]
                             (when-not (str/blank? c)
                               (keyword (str/lower-case c))))
                :description (pick :pretty_name :description)}))

      :darwin
      (let [sw (utils/parse-kv-colon (sections "sw-vers"))
            release (:productversion sw)]
        (assoc base :distro
               {:id       :macos
                :name     (:productname sw)
                :release  release
                :codename (guess-mac-codename release)
                :build    (:buildversion sw)}))

      base)))

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
  (let [ver (sections "ver")
        [_ version] (re-find #"[vV]ersion ([\d.]+)" ver)
        osinfo (utils/parse-kv (sections "osinfo"))
        arch (str/trim (sections "arch"))]
    {:family  :windows
     :kernel  {:name    "Windows"
               :release (or version (:version osinfo))}
     :machine (normalize-windows-arch arch)
     :distro  {:id      :windows
               :caption (:caption osinfo)
               :release (:version osinfo)
               :build   (:buildnumber osinfo)}}))

(defn gather-os
  "Gathers operating system, kernel and distribution facts from the
  host reachable via `exec`.

  ## Arguments

  Takes a map with:

  - `:exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`.
  - `:shell` - the shell map returned by `eyre.shell/gather-shell`;
    its `:type` selects which embedded collection script is run.
    Shells without a specific script fall back to the POSIX script.

  ## Returns

  A map with:

  - `:family` - keyword OS family derived from the kernel name:
    `:linux`, `:darwin`, `:freebsd`, `:netbsd`, `:openbsd`,
    `:dragonfly`, `:sunos`, `:aix` or `:windows`.
  - `:kernel` - a map `{:name :release :version}` with the kernel
    name (e.g. `\"Linux\"`), release (e.g. `\"6.12.91-1-MANJARO\"`)
    and full build/version string. On Windows only `:name` and
    `:release` are present.
  - `:machine` - hardware architecture string, e.g. `\"x86_64\"`.
    Windows `PROCESSOR_ARCHITECTURE` values are normalized to uname
    style, so `\"AMD64\"` becomes `\"x86_64\"` and `\"ARM64\"` becomes
    `\"aarch64\"`.
  - `:distro` - distribution/product details (see below); omitted for
    unix families other than `:linux` and `:darwin`.

  The `:distro` map is sourced from `/etc/os-release` and
  `lsb_release` on Linux, from `sw_vers` on macOS (with `:codename`
  guessed from the release number) and from `ver`/WMI on Windows:

  ```clojure
  ;; linux
  {:id :manjaro :name \"Manjaro Linux\" :release \"23.0\"
   :codename nil :description \"Manjaro Linux\"}

  ;; macos
  {:id :macos :name \"macOS\" :release \"14.4\"
   :codename :sonoma :build \"23E214\"}

  ;; windows
  {:id :windows :caption \"Microsoft Windows 11 Pro\"
   :release \"10.0.22631\" :build \"22631\"}
  ```

  ## Example

  ```clojure
  (os/gather-os {:exec local-exec :shell shell})
  ;; => {:family :linux
  ;;     :kernel {:name \"Linux\" :release \"6.12.91-1-MANJARO\"
  ;;              :version \"#1 SMP PREEMPT_DYNAMIC ...\"}
  ;;     :machine \"x86_64\"
  ;;     :distro {:id :manjaro :name \"Manjaro Linux\" ...}}
  ```"
  [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "os gathering script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (if (windows-shell-types shell-type)
        (process-windows sections)
        (process-unix sections)))))
