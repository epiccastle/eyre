(ns eyre-test.network-test
  (:require [clojure.test :refer :all]
            [eyre.shell :as shell]
            [eyre.network :as network]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(into {}
      (for [host [:windows :macos :freebsd :ubuntu]]
        (let [exec (shell-test/make-executor-fn (shell-test/host-ports host))]
          [host (network/determine-network
                  {:exec exec
                   :shell (shell/determine-shell {:exec exec})})])))
