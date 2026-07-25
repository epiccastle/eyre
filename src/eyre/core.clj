(ns eyre.core
  (:require [eyre.bins :as bins]
            [eyre.filesystem :as filesystem]
            [eyre.hardware :as hardware]
            [eyre.network :as network]
            [eyre.os :as os]
            [eyre.shell :as shell]
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

  The shell is detected first; its result is threaded through as the
  `:shell` argument of every module so each one can select the
  appropriate embedded collection script. See the individual gather
  functions for the shape of each entry.

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
        ctx {:exec exec :shell shell}]
    {:shell      shell
     :os         (os/gather-os ctx)
     :hardware   (hardware/gather-hardware ctx)
     :users      (users/gather-users ctx)
     :filesystem (filesystem/gather-filesystem ctx)
     :network    (network/gather-network ctx)
     :paths      (bins/gather-paths ctx)}))
