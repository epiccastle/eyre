(ns eyre-test.os-test
  (:require [clojure.test :refer :all]
            [eyre-test.config :as config]
            [eyre.shell :as shell]
            [eyre.os :as os]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [babashka.process :as process]))

;; the kernel running in the docker environments will report as the
;; kernel we are running as the host OS running these tests
(def kernel
  (:kernel
   (let [exec #(process/shell {:cmd "bash" :in % :out :string :err :string})]
     (os/gather-os
       {:exec exec
        :shell (shell/gather-shell {:exec exec})}))))

(deftest os-selection
  (is (=
        (into {}
              (for [host (config/select-hosts {:exclude #{}})]
                (let [exec (shell-test/make-executor-fn (config/host-ports host))]
                  (prn host)
                  [host (os/gather-os
                          {:exec exec
                           :shell (shell/gather-shell {:exec exec})})])))
        (config/filter-hashmap
          {:exclude #{}}
          {:alpine
           {:distro
            {:codename nil,
             :description "Alpine Linux v3.16",
             :id :alpine,
             :name "Alpine Linux",
             :release "3.16.2"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :alpine-dash
           {:distro
            {:codename nil,
             :description "Alpine Linux v3.16",
             :id :alpine,
             :name "Alpine Linux",
             :release "3.16.2"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :alpine-fish
           {:distro
            {:codename nil,
             :description "Alpine Linux v3.16",
             :id :alpine,
             :name "Alpine Linux",
             :release "3.16.2"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :alpine-zsh
           {:distro
            {:codename nil,
             :description "Alpine Linux v3.16",
             :id :alpine,
             :name "Alpine Linux",
             :release "3.16.2"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :amazonlinux
           {:distro
            {:codename nil,
             :description "Amazon Linux 2023.11.20260526",
             :id :amzn,
             :name "Amazon Linux",
             :release "2023"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :amazonlinux-ksh
           {:distro
            {:codename nil,
             :description "Amazon Linux 2023.11.20260526",
             :id :amzn,
             :name "Amazon Linux",
             :release "2023"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :amazonlinux-zsh
           {:distro
            {:codename nil,
             :description "Amazon Linux 2023.11.20260526",
             :id :amzn,
             :name "Amazon Linux",
             :release "2023"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :archlinux
           {:distro
            {:codename nil,
             :description "Arch Linux",
             :id :arch,
             :name "Arch Linux",
             :release "20260607.0.541780"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :archlinux-dash
           {:distro
            {:codename nil,
             :description "Arch Linux",
             :id :arch,
             :name "Arch Linux",
             :release "20260607.0.541780"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :archlinux-fish
           {:distro
            {:codename nil,
             :description "Arch Linux",
             :id :arch,
             :name "Arch Linux",
             :release "20260607.0.541780"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :archlinux-ksh
           {:distro
            {:codename nil,
             :description "Arch Linux",
             :id :arch,
             :name "Arch Linux",
             :release "20260607.0.541780"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :archlinux-nu
             {:family :linux,
              :kernel kernel,
              :machine "x86_64",
              :distro
              {:id :arch,
               :name "Arch Linux",
               :release "20260607.0.541780",
               :codename nil,
               :description "Arch Linux"}}
           :archlinux-zsh
           {:distro
            {:codename nil,
             :description "Arch Linux",
             :id :arch,
             :name "Arch Linux",
             :release "20260607.0.541780"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :debian
           {:distro
            {:codename :trixie,
             :description "Debian GNU/Linux 13 (trixie)",
             :id :debian,
             :name "Debian GNU/Linux",
             :release "13"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :debian-dash
           {:distro
            {:codename :trixie,
             :description "Debian GNU/Linux 13 (trixie)",
             :id :debian,
             :name "Debian GNU/Linux",
             :release "13"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :debian-fish
           {:distro
            {:codename :trixie,
             :description "Debian GNU/Linux 13 (trixie)",
             :id :debian,
             :name "Debian GNU/Linux",
             :release "13"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :debian-ksh
           {:distro
            {:codename :trixie,
             :description "Debian GNU/Linux 13 (trixie)",
             :id :debian,
             :name "Debian GNU/Linux",
             :release "13"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :debian-zsh
           {:distro
            {:codename :trixie,
             :description "Debian GNU/Linux 13 (trixie)",
             :id :debian,
             :name "Debian GNU/Linux",
             :release "13"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :fedora
           {:distro
            {:codename nil,
             :description "Fedora Linux 44 (Container Image)",
             :id :fedora,
             :name "Fedora Linux",
             :release "44"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :fedora-dash
           {:distro
            {:codename nil,
             :description "Fedora Linux 44 (Container Image)",
             :id :fedora,
             :name "Fedora Linux",
             :release "44"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :fedora-fish
           {:distro
            {:codename nil,
             :description "Fedora Linux 44 (Container Image)",
             :id :fedora,
             :name "Fedora Linux",
             :release "44"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :fedora-ksh
           {:distro
            {:codename nil,
             :description "Fedora Linux 44 (Container Image)",
             :id :fedora,
             :name "Fedora Linux",
             :release "44"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :fedora-nu
             {:family :linux,
              :kernel kernel,
              :machine "x86_64",
              :distro
              {:id :fedora,
               :name "Fedora Linux",
               :release "44",
               :codename nil,
               :description "Fedora Linux 44 (Container Image)"}}
           :fedora-zsh
           {:distro
            {:codename nil,
             :description "Fedora Linux 44 (Container Image)",
             :id :fedora,
             :name "Fedora Linux",
             :release "44"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :freebsd
           {:family :freebsd,
            :kernel
            {:name "FreeBSD",
             :release "15.1-RELEASE",
             :version
             "FreeBSD 15.1-RELEASE releng/15.1-n283562-96841ea08dcf GENERIC"},
            :machine "amd64"},
           :macos
           {:distro
            {:build "19H15",
             :codename :catalina,
             :id :macos,
             :name "Mac OS X",
             :release "10.15.7"},
            :family :darwin,
            :kernel
            {:name "Darwin",
             :release "19.6.0",
             :version
             "Darwin Kernel Version 19.6.0: Thu Oct 29 22:56:45 PDT 2020; root:xnu-6153.141.2.2~1/RELEASE_X86_64"},
            :machine "x86_64"},
           :netbsd
           {:family :netbsd,
            :kernel
            {:name "NetBSD",
             :release "10.1",
             :version
             "NetBSD 10.1 (GENERIC) #0: Mon Dec 16 13:08:11 UTC 2024  mkrepro@mkrepro.NetBSD.org:/usr/src/sys/arch/amd64/compile/GENERIC"},
            :machine "amd64"},
           :oraclelinux
           {:distro
            {:codename nil,
             :description "Oracle Linux Server 10.1",
             :id :ol,
             :name "Oracle Linux Server",
             :release "10.1"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :oraclelinux-ksh
           {:distro
            {:codename nil,
             :description "Oracle Linux Server 10.1",
             :id :ol,
             :name "Oracle Linux Server",
             :release "10.1"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :oraclelinux-zsh
           {:distro
            {:codename nil,
             :description "Oracle Linux Server 10.1",
             :id :ol,
             :name "Oracle Linux Server",
             :release "10.1"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :rockylinux
           {:distro
            {:codename nil,
             :description "Rocky Linux 9.3 (Blue Onyx)",
             :id :rocky,
             :name "Rocky Linux",
             :release "9.3"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :rockylinux-ksh
           {:distro
            {:codename nil,
             :description "Rocky Linux 9.3 (Blue Onyx)",
             :id :rocky,
             :name "Rocky Linux",
             :release "9.3"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :rockylinux-zsh
           {:distro
            {:codename nil,
             :description "Rocky Linux 9.3 (Blue Onyx)",
             :id :rocky,
             :name "Rocky Linux",
             :release "9.3"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :ubuntu
           {:distro
            {:codename :noble,
             :description "Ubuntu 24.04.4 LTS",
             :id :ubuntu,
             :name "Ubuntu",
             :release "24.04"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :ubuntu-dash
           {:distro
            {:codename :noble,
             :description "Ubuntu 24.04.4 LTS",
             :id :ubuntu,
             :name "Ubuntu",
             :release "24.04"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :ubuntu-fish
           {:distro
            {:codename :noble,
             :description "Ubuntu 24.04.4 LTS",
             :id :ubuntu,
             :name "Ubuntu",
             :release "24.04"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :ubuntu-ksh
           {:distro
            {:codename :noble,
             :description "Ubuntu 24.04.4 LTS",
             :id :ubuntu,
             :name "Ubuntu",
             :release "24.04"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :ubuntu-zsh
           {:distro
            {:codename :noble,
             :description "Ubuntu 24.04.4 LTS",
             :id :ubuntu,
             :name "Ubuntu",
             :release "24.04"},
            :family :linux,
            :kernel kernel,
            :machine "x86_64"},
           :windows
           {:distro
            {:build "20348",
             :caption "Microsoft Windows Server 2022 Standard Evaluation",
             :id :windows,
             :release "10.0.20348"},
            :family :windows,
            :kernel {:name "Windows", :release "10.0.20348.587"},
            :machine "x86_64"}}
          ))))

(defmacro distro-same-os-test [test-name pattern]
  `(deftest ~test-name
     (let [all-hosts# (config/select-hosts {:only #{~pattern}})]
       (when (seq all-hosts#)
         (let [results# (into {}
                              (for [host# all-hosts#]
                                (let [exec# (shell-test/make-executor-fn (config/host-ports host#))]
                                  (prn host#)
                                  [host# (os/gather-os
                                           {:exec exec#
                                            :shell (shell/gather-shell {:exec exec#})})])))
               distinct-results# (set (vals results#))]
           (is (= 1 (count distinct-results#))
               (str "Expected all " ~(name pattern) " targets to detect the same OS, but got: "
                    (into {} (for [[host# res#] results#] [host# res#])))))))))

(distro-same-os-test alpine-test :alpine*)
(distro-same-os-test ubuntu-test :ubuntu*)
(distro-same-os-test fedora-test :fedora*)
(distro-same-os-test debian-test :debian*)
(distro-same-os-test archlinux-test :archlinux*)
(distro-same-os-test amazonlinux-test :amazonlinux*)
(distro-same-os-test rockylinux-test :rockylinux*)
(distro-same-os-test oraclelinux-test :oraclelinux*)
