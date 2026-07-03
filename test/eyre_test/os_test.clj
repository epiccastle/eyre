(ns eyre-test.os-test
  (:require [clojure.test :refer :all]
            [eyre.shell :as shell]
            [eyre.os :as os]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(deftest os-selection
  (is (=
        (into {}
              (for [host [:windows :macos :freebsd :ubuntu]]
                (let [exec (shell-test/make-executor-fn (shell-test/host-ports host))]
                  [host (os/determine-os
                          {:exec exec
                           :shell (shell/determine-shell {:exec exec})})])))
        {:windows
         {:family :windows,
          :kernel {:name "Windows", :release "10.0.20348.587"},
          :machine "x86_64",
          :distro
          {:id :windows,
           :caption "Microsoft Windows Server 2022 Standard Evaluation",
           :release "10.0.20348",
           :build "20348"}},
         :macos
         {:family :darwin,
          :kernel
          {:name "Darwin",
           :release "19.6.0",
           :version
           "Darwin Kernel Version 19.6.0: Thu Oct 29 22:56:45 PDT 2020; root:xnu-6153.141.2.2~1/RELEASE_X86_64"},
          :machine "x86_64",
          :distro
          {:id :macos,
           :name "Mac OS X",
           :release "10.15.7",
           :codename :catalina,
           :build "19H15"}},
         :freebsd
         {:family :freebsd,
          :kernel
          {:name "FreeBSD",
           :release "15.1-RELEASE",
           :version
           "FreeBSD 15.1-RELEASE releng/15.1-n283562-96841ea08dcf GENERIC"},
          :machine "amd64"},
         :ubuntu
         {:family :linux,
          :kernel
          {:name "Linux",
           :release "6.12.91-1-MANJARO",
           :version
           "#1 SMP PREEMPT_DYNAMIC Sun, 24 May 2026 05:07:21 +0000"},
          :machine "x86_64",
          :distro
          {:id :ubuntu,
           :name "Ubuntu",
           :release "24.04",
           :codename :noble,
           :description "Ubuntu 24.04.4 LTS"}}})))
