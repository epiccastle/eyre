(ns eyre.core
  (:require [eyre.bins :as bins]
            [eyre.filesystem :as filesystem]
            [eyre.hardware :as hardware]
            [eyre.network :as network]
            [eyre.os :as os]
            [eyre.shell :as shell]
            [eyre.users :as users]))

(defn gather
  "Gather all system facts from the host reachable via `exec`. Returns a
  map containing:

    :shell      - shell detection result from eyre.shell/gather-shell
    :os         - OS detection result from eyre.os/gather-os
    :hardware   - hardware facts from eyre.hardware/gather-hardware
    :users      - user facts from eyre.users/gather-users
    :filesystem - filesystem facts from eyre.filesystem/gather-filesystem
    :network    - network facts from eyre.network/gather-network
    :paths      - available binary paths from eyre.bins/gather-paths"
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
