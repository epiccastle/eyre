(ns eyre-test.network-test
  (:require [clojure.test :refer :all]
            [eyre-test.config :as config]
            [eyre.shell :as shell]
            [eyre.network :as network]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(deftest network-test
  (is (=
        #_{:windows
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
            '({:mtu 16384,
              :name "lo0",
              :nd6-options {:value 201, :flags #{:PERFORMNUD :DAD}},
              :status :up,
              :ipv6 [{:address "::1", :prefix 128}],
              :ipv4 [{:address "127.0.0.1", :prefix 8}],
              :options
              {:value 1203, :flags #{:RXCSUM :SW_TIMESTAMP :TXCSUM :TXSTATUS}},
              :flags {:value 8049, :flags #{:RUNNING :MULTICAST :LOOPBACK :UP}},
              :loopback? true,
              :mac "00:00:00:00:00:00"}
             {:mtu 0,
              :name "EHC253",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 0, :flags #{}},
              :loopback? false,
              :mac nil}
             {:mtu 1280,
              :name "gif0",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 8010, :flags #{:POINTOPOINT :MULTICAST}},
              :loopback? false,
              :mac nil}
             {:mtu 0,
              :name "UHC29",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 0, :flags #{}},
              :loopback? false,
              :mac nil}
             {:mtu 1280,
              :name "stf0",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 0, :flags #{}},
              :loopback? false,
              :mac nil}
             {:mtu 0,
              :name "UHC93",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 0, :flags #{}},
              :loopback? false,
              :mac nil}
             {:mtu 0,
              :name "UHC61",
              :nd6-options nil,
              :status :down,
              :ipv6 [],
              :ipv4 [],
              :options nil,
              :flags {:value 0, :flags #{}},
              :loopback? false,
              :mac nil}
             {:mtu 1500,
              :name "en0",
              :nd6-options {:value 201, :flags #{:PERFORMNUD :DAD}},
              :status :up,
              :ipv6
              [{:address "fec0::14e9:2943:cac0:7d4e", :prefix 64}
               {:address "fec0::5cc2:73a1:4342:494e", :prefix 64}
               {:address "fec0::acd9:ccf2:b8d2:7719", :prefix 64}
               {:address "fec0::f58c:9398:e289:fbcf", :prefix 64}],
              :ipv4 [{:address "10.0.2.15", :prefix 24}],
              :options nil,
              :flags
              {:value 8863,
               :flags #{:BROADCAST :RUNNING :SIMPLEX :SMART :MULTICAST :UP}},
              :loopback? false,
              :mac "52:54:00:c9:18:27"}),
            :default-gateway {:gateway "10.0.2.2", :iface "en0"},
            :dns {:nameservers ["10.0.2.3"], :search []}},
           :freebsd
           {:hostname "",
            :interfaces
            '({:mtu 1500,
              :name "vtnet0",
              :nd6-options
              {:value 29, :flags #{:AUTO_LINKLOCAL :PERFORMNUD :IFDISABLED}},
              :status :up,
              :ipv6 [],
              :ipv4 [{:address "10.0.2.15", :prefix 24}],
              :options
              {:value 880028,
               :flags #{:LINKSTATE :HWSTATS :JUMBO_MTU :VLAN_MTU}},
              :flags
              {:value 1008843,
               :flags #{:BROADCAST :RUNNING :SIMPLEX :LOWER_UP :MULTICAST :UP}},
              :loopback? false,
              :mac "52:54:00:12:34:56"}
             {:mtu 16384,
              :name "lo0",
              :nd6-options {:value 21, :flags #{:AUTO_LINKLOCAL :PERFORMNUD}},
              :status :up,
              :ipv6 [{:address "::1", :prefix 128}],
              :ipv4 [{:address "127.0.0.1", :prefix 8}],
              :options
              {:value 680003,
               :flags #{:LINKSTATE :RXCSUM :RXCSUM_IPV6 :TXCSUM_IPV6 :TXCSUM}},
              :flags
              {:value 1008049,
               :flags #{:RUNNING :LOWER_UP :MULTICAST :LOOPBACK :UP}},
              :loopback? true,
              :mac "00:00:00:00:00:00"}),
            :default-gateway {:gateway "10.0.2.2", :iface "vtnet0"},
            :dns {:nameservers ["10.0.2.3"], :search []}},
           :ubuntu
           {:hostname "a3603551e18c",
            :interfaces [],
            :default-gateway nil,
            :dns {:nameservers ["192.168.92.2"], :search ["plume"]}}}

        (into {}
              (for [host
                    #_[:archlinux-fish :archlinux-nu]
                    #_[:windows :macos :freebsd :ubuntu]
                    #_[:oraclelinux]
                    [:windows]
                    #_(keys config/host-ports)]
                (let [exec (shell-test/make-executor-fn (config/host-ports host))]
                  (prn host)
                  [host (network/gather-network
                          {:exec exec
                           :shell (shell/gather-shell {:exec exec})})])))

        )))

#_ (into {}
         (for [host [:ubuntu]]
           (let [exec (shell-test/make-executor-fn (config/host-ports host))]
             [host (network/gather-network
                     {:exec exec
                      :shell (shell/gather-shell {:exec exec})})])))


#_
(def executor (shell-test/make-executor-fn (config/host-ports :windows)))
#_
(executor "New-ItemProperty -Path \"HKLM:\\SOFTWARE\\OpenSSH\" -Name DefaultShell -Value \"C:\\Windows\\System32\\cmd.exe\" -PropertyType String -Force")
