(ns eyre.shell
  (:require [clojure.string :as str]
            [eyre.utils :refer [embed]]
            [eyre-test.utils :as utils]
            [medley.core :as medley]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [clojuressh.user-info :as user-info]))

(def newlines #"\r\n|\n\r|\r|\n")

(def check-cmd-type-script (embed "shell/check-cmd-type-script.polyglot"))

(def ver-script "ver")

(def powershell-version-path-script (embed "shell/powershell-version-path-script.ps1"))

(def nushell-version-script (embed "shell/nushell-version-script.nu"))

(def bash-versions-script "echo \"B:$BASH_VERSION:Z:$ZSH_VERSION:F:$FISH_VERSION:K:$KSH_VERSION\"
echo shell:$SHELL
")

(def fish-canonical-path-script "set target (command -v $SHELL)
while test -L $target
    set link (readlink $target)
    if string match -q '/*' $link
        set target $link
    else
        set target (dirname $target)/$link
    end
end
cd (dirname $target); and echo (pwd -P)/(basename $target)")

(def default-canonical-path-script " if command -v greadlink >/dev/null 2>&1; then
  greadlink -f \"$(command -v $SHELL)\"
elif readlink -f / >/dev/null 2>&1; then
  readlink -f \"$(command -v $SHELL)\"
else
  # BSD readlink fallback: manually loop-resolve
  target=\"$(command -v $SHELL)\"
  while [ -L \"$target\" ]; do
    link=\"$(readlink \"$target\")\"
    case \"$link\" in
      /*) target=\"$link\" ;;
      *) target=\"$(dirname \"$target\")/$link\" ;;
    esac
  done
  cd -- \"$(dirname -- \"$target\")\" && echo \"$(pwd -P)/$(basename -- \"$target\")\"
fi
")

(def dash-version-script "#!/bin/dash

# Try to determine the version of dash currently running,
# using multiple methods across different Linux distros.

DASH_VERSION=\"\"

# 1. dpkg (Debian, Ubuntu, Mint, etc.)
if [ -z \"$DASH_VERSION\" ] && command -v dpkg > /dev/null 2>&1; then
    DASH_VERSION=$(dpkg -l dash 2>/dev/null | awk '/^ii/ { print $3 }')
fi

# 2. rpm (Fedora, RHEL, CentOS, AlmaLinux, Rocky, etc.)
if [ -z \"$DASH_VERSION\" ] && command -v rpm > /dev/null 2>&1; then
    DASH_VERSION=$(rpm -q --queryformat '%{VERSION}-%{RELEASE}\\n' dash 2>/dev/null)
    # rpm exits 0 even on \"not installed\", so check output
    echo \"$DASH_VERSION\" | grep -qi 'not installed' && DASH_VERSION=\"\"
fi

# 3. pacman (Arch, Manjaro, EndeavourOS, etc.)
if [ -z \"$DASH_VERSION\" ] && command -v pacman > /dev/null 2>&1; then
    DASH_VERSION=$(pacman -Qi dash 2>/dev/null | awk -F': ' '/^Version/ { print $2 }')
fi

# 4. apk (Alpine Linux)
if [ -z \"$DASH_VERSION\" ] && command -v apk > /dev/null 2>&1; then
    DASH_VERSION=$(apk info dash 2>/dev/null | head -1 | sed 's/dash-//')
fi

# 5. zypper / rpm fallback (openSUSE, SLES)
if [ -z \"$DASH_VERSION\" ] && command -v zypper > /dev/null 2>&1; then
    DASH_VERSION=$(zypper info dash 2>/dev/null | awk -F': ' '/^Version/ { print $2 }')
fi

# 6. xbps-query (Void Linux)
if [ -z \"$DASH_VERSION\" ] && command -v xbps-query > /dev/null 2>&1; then
    DASH_VERSION=$(xbps-query dash 2>/dev/null | awk -F': ' '/^pkgver/ { sub(/dash-/, \"\", $2); print $2 }')
fi

# 7. portage / qatom (Gentoo)
if [ -z \"$DASH_VERSION\" ] && command -v qatom > /dev/null 2>&1; then
    DASH_VERSION=$(qatom -F '%{PV}' app-shells/dash 2>/dev/null)
fi

# 8. /etc/os-release + binary --version as a last resort
#    (dash itself doesn't support --version, but some distros patch it)
if [ -z \"$DASH_VERSION\" ]; then
    DASH_VERSION=$(dash --version 2>&1 | head -1)
    echo \"$DASH_VERSION\" | grep -qi 'unknown\\|illegal\\|invalid' && DASH_VERSION=\"\"
fi

# 9. Parse /proc/version or uname as absolute last resort
if [ -z \"$DASH_VERSION\" ]; then
    DASH_BIN=$(command -v dash)
    if [ -n \"$DASH_BIN\" ]; then
        # Try strings on the binary to extract a version-like pattern
        DASH_VERSION=$(strings \"$DASH_BIN\" 2>/dev/null \\
            | grep -E '^[0-9]+\\.[0-9]+(\\.[0-9]+)*$' \\
            | head -1)
    fi
fi

# Report
if [ -n \"$DASH_VERSION\" ]; then
    echo \"dash version: $DASH_VERSION\"
else
    echo \"Could not determine dash version on this system.\" >&2
    exit 1
fi
")

(defn process-version-line [version-line]
  (prn version-line)
  (-> version-line
      (str/split #":")
      (->> (partition 2)
           (keep (fn [[k v]]
                  (when (pos? (count v))
                    [({"B" :bash
                       "Z" :zsh
                       "F" :fish
                       "K" :ksh} k)
                     (str/trim v)]))))
      first))

#_ (process-version-line "B:3.2.57(1)-release:Z::F::K:")
#_ (process-version-line "B::Z::F::K:")
#_ (process-version-line "B::Z::F:3.7.0:K:")

(defn process-powershell [version-lines]
  (let [[header _ version] (-> version-lines
                               str/trim
                               (str/split newlines))
        header (-> header
                   str/trim
                   (str/split #"\s+")
                   (->> (map str/lower-case)
                        (map keyword)))
        version-parts (-> version
                          str/trim
                          (str/split #"\s+"))
        version (->> (map vector header version-parts)
                     (into {}))]
    (str (:major version) "." (:minor version) "." (:build version) "." (:revision version))))

#_ (process-powershell "\r\nMajor  Minor  Build  Revision\r\n-----  -----  -----  --------\r\n5      1      20348  558     \r\n\r\n\r\n")

(defn determine-shell [{:keys [exec]}]
  (let [{:keys [exit out err]} (exec check-cmd-type-script)]
    (if (and (= 1 exit) (str/includes? err "variable not found") (str/includes? err "nu::parser::variable_not_found"))
      ;; nushell
      (let [{:keys [exit out err]} (exec nushell-version-script)]
        (assert (zero? exit) (str "nushell version determination exited non zero: " exit " " err))
        (let [[version shell path] (str/split out newlines)]
          {:type :nu
           :version version
           :shell shell
           :canonical-path path}))

      ;; other
      (do
        (assert (zero? exit) (str "shell determination script 1 exited non zero: " exit " " err))
        (let [[line-1 line-2] (str/split out newlines)
              first-guess (cond
                            (not= line-1 "%COMSPEC%") :cmd.exe
                            (= line-2 "powershell") :powershell
                            :else (keyword line-2))]
          (case first-guess
            :cmd.exe
            (let [{:keys [exit out err]} (exec ver-script)
                  _ (assert (zero? exit) (str "cmd.exe version determination script exited non zero: " exit " " err))
                  version (second (re-find #"[vV]ersion ([\d.]+)" out))]
              {:type :cmd-exe
               :version version
               :shell line-1
               :path line-1})

            :powershell
            (let [{:keys [exit out err]} (exec powershell-version-path-script)
                  _ (assert (zero? exit) (str "powershell version determination script exited non zero: " exit " " err))
                  [version path] (str/split out #"\r\npath:\r\n")
                  path (str/trim path)]
              {:type :powershell
               :version (process-powershell version)
               :shell path
               :canonical-path path})

            ;; bash like shell
            (let [{:keys [exit out err]} (exec bash-versions-script)
                  _ (assert (zero? exit) (str "shell determination script 2 exited non zero: " exit " " err))
                  [versions shell] (str/split out newlines)
                  shell (second (str/split shell #"shell:"))
                  versions (process-version-line versions)
                  [shell-type shell-version] versions
                  {:keys [exit out err]}
                    (exec
                      (case shell-type
                        :fish fish-canonical-path-script
                        ;; bash like shells
                        default-canonical-path-script))
                  _ (assert (zero? exit) (str "shell determination script 3 exited non zero: " exit " " err))
                  [sh-readline] (str/split out newlines)
                  busybox? (str/ends-with? sh-readline "/busybox")
                  dash? (str/ends-with? sh-readline "/dash")]
              (cond
                busybox?
                (let [{:keys [exit out err]} (exec (str sh-readline " --help 2>&1 | head -1"))
                      _ (assert (zero? exit) (str "busybox version determination script exited non zero: " exit " " err))
                      version (second (str/split out #"\s+"))]
                  {:type :busybox
                   :version version
                   :shell shell
                   :canonical-path sh-readline})

                dash?
                (let [{:keys [exit out err]} (exec dash-version-script)
                      _ (assert (zero? exit) (str "dash version determination script exited non zero: " exit " " err))
                      version (-> out
                                  str/trim
                                  (str/split #"dash version:\s*")
                                  second)]
                  {:type :dash
                   :version version
                   :shell shell
                   :canonical-path sh-readline})

                :else
                {:type (or shell-type
                           (-> sh-readline
                               (str/split #"/")
                               last
                               keyword))
                 :version shell-version
                 :shell shell
                 :canonical-path sh-readline}))))))))
