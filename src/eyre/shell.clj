(ns eyre.shell
  (:require [clojure.string :as str]
            [eyre.utils :refer [embed newlines]]))

(def check-cmd-type-script (embed "shell/check-cmd-type-script.polyglot"))
(def ver-script "ver")
(def powershell-version-path-script (embed "shell/powershell-version-path-script.ps1"))
(def nushell-version-script (embed "shell/nushell-version-script.nu"))
(def bash-versions-script (embed "shell/bash-versions-script.sh"))
(def fish-canonical-path-script (embed "shell/fish-canonical-path-script.fish"))
(def default-canonical-path-script (embed "shell/default-canonical-path-script.sh"))
(def dash-version-script (embed "shell/dash-version-script.dash"))

(defn- process-version-line [version-line]
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

(defn- process-powershell [version-lines]
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

(defn gather-shell
  "Detects the shell running on the host reachable via `exec` and
  returns a map describing it.

  ## Arguments

  Takes a map with:

  - `:exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`.

  ## Returns

  A map with:

  - `:type` - keyword identifying the shell family: `:bash`, `:zsh`,
    `:sh`, `:dash`, `:ksh`, `:busybox`, `:fish`, `:nu`, `:powershell`
    or `:cmd-exe`.
  - `:version` - the shell version string, e.g. `\"5.2.15(1)-release\"`.
  - `:shell` - path of the shell as found on the system,
    e.g. `\"/bin/bash\"`.
  - `:canonical-path` - fully resolved path of the shell binary,
    e.g. `\"/usr/bin/bash\"`. (For `:cmd-exe` there is a `:path` key
    instead, holding the value of `%COMSPEC%`.)

  The returned map is the `:shell` input expected by the other fact
  gatherers (`eyre.os/gather-os`, `eyre.hardware/gather-hardware`,
  `eyre.users/gather-users`, `eyre.filesystem/gather-filesystem`,
  `eyre.network/gather-network` and `eyre.bins/gather-paths`), which
  use `:type` to select the appropriate embedded collection script.

  Detection runs small embedded probe scripts through `exec` and
  inspects their output, so nothing needs to be installed on the
  target host. Asserts if any probe script exits non-zero.

  ## Example

  ```clojure
  (shell/gather-shell {:exec local-exec})
  ;; => {:type :bash
  ;;     :version \"5.2.15(1)-release\"
  ;;     :shell \"/bin/bash\"
  ;;     :canonical-path \"/usr/bin/bash\"}
  ```"
  [{:keys [exec]}]
  (let [{:keys [exit out err]} (exec check-cmd-type-script)]
    (if (and (= 1 exit) (str/includes? err "variable not found") (str/includes? err "nu::parser::variable_not_found"))
      ;; nushell
      (let [{:keys [exit out err]} (exec nushell-version-script)]
        (assert (zero? exit) (str "nushell version gathering exited non zero: " exit " " err))
        (let [[version shell path] (str/split out newlines)]
          {:type :nu
           :version version
           :shell shell
           :canonical-path path}))

      ;; other
      (do
        (assert (zero? exit) (str "shell gathering script 1 exited non zero: " exit " " err))
        (let [[line-1 line-2] (str/split out newlines)
              first-guess (cond
                            (not= line-1 "%COMSPEC%") :cmd.exe
                            (= line-2 "powershell") :powershell
                            :else (keyword line-2))]
          (case first-guess
            :cmd.exe
            (let [{:keys [exit out err]} (exec ver-script)
                  _ (assert (zero? exit) (str "cmd.exe version gathering script exited non zero: " exit " " err))
                  version (second (re-find #"[vV]ersion ([\d.]+)" out))]
              {:type :cmd-exe
               :version version
               :shell line-1
               :path line-1})

            :powershell
            (let [{:keys [exit out err]} (exec powershell-version-path-script)
                  _ (assert (zero? exit) (str "powershell version gathering script exited non zero: " exit " " err))
                  [version path] (str/split out #"\r\npath:\r\n")
                  path (str/trim path)]
              {:type :powershell
               :version (process-powershell version)
               :shell path
               :canonical-path path})

            ;; bash like shell
            (let [{:keys [exit out err]} (exec bash-versions-script)
                  _ (assert (zero? exit) (str "shell gathering script 2 exited non zero: " exit " " err))
                  [versions shell] (str/split out newlines)
                  shell (second (str/split shell #"shell:"))
                  versions (process-version-line versions)
                  [shell-type shell-version] versions
                  {:keys [exit out err] :as res}
                    (exec
                      (case shell-type
                        :fish fish-canonical-path-script
                        ;; bash like shells
                        default-canonical-path-script))
                  _ (assert (zero? exit) (str "shell gathering script 3 exited non zero: " exit " " err))
                  [canonical-path] (str/split out newlines)
                  busybox? (str/ends-with? canonical-path "/busybox")
                  dash? (str/ends-with? canonical-path "/dash")]
              (cond
                busybox?
                (let [{:keys [exit out err]} (exec (str canonical-path " --help 2>&1 | head -1"))
                      _ (assert (zero? exit) (str "busybox version gathering script exited non zero: " exit " " err))
                      version (second (str/split out #"\s+"))]
                  {:type :busybox
                   :version version
                   :shell shell
                   :canonical-path canonical-path})

                dash?
                (let [{:keys [exit out err]} (exec dash-version-script)
                      _ (assert (zero? exit) (str "dash version gathering script exited non zero: " exit " " err))
                      version (-> out
                                  str/trim
                                  (str/split #"dash version:\s*")
                                  second)]
                  {:type :dash
                   :version version
                   :shell shell
                   :canonical-path canonical-path})

                :else
                {:type (or shell-type
                           (-> canonical-path
                               (str/split #"/")
                               last
                               keyword))
                 :version shell-version
                 :shell shell
                 :canonical-path canonical-path}))))))))
