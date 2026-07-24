(ns eyre.core
  (:require [eyre.bins :as bins]
            [eyre.filesystem :as filesystem]
            [eyre.hardware :as hardware]
            [eyre.network :as network]
            [eyre.os :as os]
            [eyre.shell :as shell]
            [eyre.users :as users]))

(defn determine
  "Gather all system facts from the host reachable via `exec`. Returns a
  map containing:

    :shell      - shell detection result from eyre.shell/determine-shell
    :os         - OS detection result from eyre.os/determine-os
    :hardware   - hardware facts from eyre.hardware/determine-hardware
    :users      - user facts from eyre.users/determine-users
    :filesystem - filesystem facts from eyre.filesystem/determine-filesystem
    :network    - network facts from eyre.network/determine-network
    :paths      - available binary paths from eyre.bins/determine-paths"
  [exec]
  (let [shell (shell/determine-shell {:exec exec})
        ctx {:exec exec :shell shell}]
    {:shell      shell
     :os         (os/determine-os ctx)
     :hardware   (hardware/determine-hardware ctx)
     :users      (users/determine-users ctx)
     :filesystem (filesystem/determine-filesystem ctx)
     :network    (network/determine-network ctx)
     :paths      (bins/determine-paths ctx)}))
