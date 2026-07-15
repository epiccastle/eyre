(ns eyre-test.network-test
  (:require [clojure.test :refer :all]
            [eyre-test.config :as eyre-test]
            [eyre.shell :as shell]
            [eyre.network :as network]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(deftest network-test
  (is (=
        (into {}
              (for [host
                    #_[:archlinux-fish :archlinux-nu]
                    #_[:windows :macos :freebsd :ubuntu]
                    #_[:oraclelinux]
                    [:windows]
                    #_(keys eyre-test.config/host-ports)]
                (let [exec (shell-test/make-executor-fn (eyre-test.config/host-ports host))]
                  (prn host)
                  [host (network/determine-network
                          {:exec exec
                           :shell (shell/determine-shell {:exec exec})})])))
        {:windows
         {:hostname "WINTEST",
          :interfaces
          '({:name "Ethernet",
            :ipv4 [{:address "10.0.2.15", :prefix 24}],
            :ipv6
            [{:address "fec0::84ec:5be3:8c22:c04f%1", :prefix 64}
             {:address "fe80::84ec:5be3:8c22:c04f%6", :prefix 64}],
            :loopback false,
            :status :up,
            :mtu nil,
            :mac "52:54:00:12:34:56"}
           {:name "Loopback Pseudo-Interface 1",
            :ipv4 [{:address "127.0.0.1", :prefix 8}],
            :ipv6 [{:address "::1", :prefix 128}],
            :loopback true,
            :status :up,
            :mtu 4294967295}),
          :default-gateway {:address "10.0.2.2", :interface "Ethernet"},
          :dns {:nameservers [], :search []}},
         :macos
         {:hostname "iMac.local",
          :interfaces
          '({:ipv4 [{:address "127.0.0.1", :prefix 8}],
            :ipv6 [{:address "::1", :prefix 128}],
            :name "lo0",
            :mac "00:00:00:00:00:00",
            :mtu 16384,
            :status :up,
            :loopback true}
           {:ipv4 [],
            :ipv6 [],
            :name "EHC253",
            :mac nil,
            :mtu 0,
            :status :down,
            :loopback false}
           {:ipv4 [],
            :ipv6 [],
            :name "gif0",
            :mac nil,
            :mtu 1280,
            :status :down,
            :loopback false}
           {:ipv4 [],
            :ipv6 [],
            :name "UHC29",
            :mac nil,
            :mtu 0,
            :status :down,
            :loopback false}
           {:ipv4 [],
            :ipv6 [],
            :name "stf0",
            :mac nil,
            :mtu 1280,
            :status :down,
            :loopback false}
           {:ipv4 [],
            :ipv6 [],
            :name "UHC93",
            :mac nil,
            :mtu 0,
            :status :down,
            :loopback false}
           {:ipv4 [],
            :ipv6 [],
            :name "UHC61",
            :mac nil,
            :mtu 0,
            :status :down,
            :loopback false}
           {:ipv4 [{:address "10.0.2.15", :prefix 24}],
            :ipv6
            [{:address "fec0::14e9:2943:cac0:7d4e", :prefix 64}
             {:address "fec0::5cc2:73a1:4342:494e", :prefix 64}
             {:address "fec0::acd9:ccf2:b8d2:7719", :prefix 64}
             {:address "fec0::f58c:9398:e289:fbcf", :prefix 64}],
            :name "en0",
            :mac "52:54:00:c9:18:27",
            :mtu 1500,
            :status :up,
            :loopback false}),
          :default-gateway {:address "10.0.2.2", :interface "en0"},
          :dns {:nameservers ["10.0.2.3"], :search []}},
         :freebsd
         {:hostname "",
          :interfaces
          '({:ipv4 [{:address "10.0.2.15", :prefix 24}],
            :ipv6 [],
            :name "vtnet0",
            :mac "52:54:00:12:34:56",
            :mtu 1500,
            :status :up,
            :loopback false}
           {:ipv4 [{:address "127.0.0.1", :prefix 8}],
            :ipv6 [{:address "::1", :prefix 128}],
            :name "lo0",
            :mac "00:00:00:00:00:00",
            :mtu 16384,
            :status :up,
            :loopback true}),
          :default-gateway {:address "10.0.2.2", :interface "vtnet0"},
          :dns {:nameservers ["10.0.2.3"], :search []}},
         :ubuntu
         {:hostname "a3603551e18c",
          :interfaces [],
          :default-gateway nil,
          :dns {:nameservers ["192.168.92.2"], :search ["plume"]}}}
        )))

#_ (into {}
         (for [host [:ubuntu]]
           (let [exec (shell-test/make-executor-fn (eyre-test.config/host-ports host))]
             [host (network/determine-network
                     {:exec exec
                      :shell (shell/determine-shell {:exec exec})})])))


#_
(def executor (shell-test/make-executor-fn (eyre-test.config/host-ports :windows)))
#_
(executor "New-ItemProperty -Path \"HKLM:\\SOFTWARE\\OpenSSH\" -Name DefaultShell -Value \"C:\\Windows\\System32\\cmd.exe\" -PropertyType String -Force")
