(ns eyre.core
  (:require [clojure.string :as str]
            [eyre.bins :as bins]
            [eyre.filesystem :as filesystem]
            [eyre.hardware :as hardware]
            [eyre.network :as network]
            [eyre.os :as os]
            [eyre.shell :as shell]
            [eyre.utils :as utils]
            [eyre.users :as users]))

(defn gather
  "Gathers all system facts from the host reachable via `exec` and
  returns them as a single map. This is the library's main entry
  point.

  ## Arguments

  - `exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`. Any
    transport works: local shell, SSH, containers, etc.

  ## Returns

  A map containing:

  - `:shell` - shell detection result from `eyre.shell/gather-shell`
  - `:os` - OS detection result from `eyre.os/gather-os`
  - `:hardware` - hardware facts from `eyre.hardware/gather-hardware`
  - `:users` - user facts from `eyre.users/gather-users`
  - `:filesystem` - filesystem facts from
    `eyre.filesystem/gather-filesystem`
  - `:network` - network facts from `eyre.network/gather-network`
  - `:paths` - available binary paths from `eyre.bins/gather-paths`

  The shell is detected first; its result is used to select the
  appropriate embedded collection script for each module. The
  scripts from all modules are concatenated and executed once, and
  the parsed output is passed to each module's processor function.

  ## Example

  ```clojure
  (require '[babashka.process :as process]
           '[eyre.core :as eyre])

  (defn local-exec [script]
    (process/shell {:cmd \"bash\" :in script
                    :out :string :err :string}))

  (eyre/gather local-exec)
  ;; => {:shell {:type :bash ...} :os {:family :linux ...} ...}
  ```"
  [exec]
  (let [shell (shell/gather-shell {:exec exec})
        shell-type (:type shell)
        script-parts [(os/gather-script shell-type)
                      (hardware/gather-script shell-type)
                      (network/gather-script shell-type)
                      (users/gather-script shell-type)
                      (filesystem/gather-script shell-type)]
        script (if (= :cmd-exe shell-type)
                 (str/join "&\n" script-parts)
                 (str/join "\n" script-parts))
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "combined gather script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      {:shell      shell
       :os         (os/process-os shell-type sections)
       :hardware   (hardware/process-hardware shell-type sections)
       :users      (users/process-users shell-type sections)
       :filesystem (filesystem/process-filesystem shell-type sections)
       :network    (network/process-network shell-type sections)
       :paths      (bins/gather-paths {:exec exec :shell shell})})))
