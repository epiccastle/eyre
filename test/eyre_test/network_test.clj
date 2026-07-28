(ns eyre-test.network-test
  (:require [clojure.test :refer :all]
            [eyre-test.config :as config]
            [eyre.shell :as shell]
            [eyre.network :as network]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(def network-results
  {:freebsd
   {:hostname "",
    :interfaces
    {"vtnet0"
     {:mtu 1500,
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
       :flags
       #{:BROADCAST :RUNNING :SIMPLEX :LOWER_UP :MULTICAST :UP}},
      :loopback? false,
      :mac "52:54:00:12:34:56"},
     "lo0"
     {:mtu 16384,
      :name "lo0",
      :nd6-options {:value 21, :flags #{:AUTO_LINKLOCAL :PERFORMNUD}},
      :status :up,
      :ipv6
      [{:address "::1", :prefix 128} {:address "fe80::1", :prefix 64}],
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :options
      {:value 680003,
       :flags #{:LINKSTATE :RXCSUM :RXCSUM_IPV6 :TXCSUM_IPV6 :TXCSUM}},
      :flags
      {:value 1008049,
       :flags #{:RUNNING :LOWER_UP :MULTICAST :LOOPBACK :UP}},
      :loopback? true,
      :mac "00:00:00:00:00:00"}},
    :default-gateway {:gateway "10.0.2.2", :interface "vtnet0"},
    :dns {:nameservers ["10.0.2.3"], :search []}},
   :alpine-zsh
   {:hostname "28e971df93a4",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.2", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "56:4c:86:1e:01:b5",
      :mtu 1500,
      :peer-index "if37",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :amazonlinux-ksh
   {:hostname "a9ec7992a58e",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ba:af:fd:4e:c5:84",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.7", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :macos
   {:hostname "iMac.local",
    :interfaces
    {"lo0"
     {:mtu 16384,
      :name "lo0",
      :nd6-options {:value 201, :flags #{:PERFORMNUD :DAD}},
      :status :up,
      :ipv6
      [{:address "::1", :prefix 128} {:address "fe80::1", :prefix 64}],
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :options
      {:value 1203, :flags #{:RXCSUM :SW_TIMESTAMP :TXCSUM :TXSTATUS}},
      :flags
      {:value 8049, :flags #{:RUNNING :MULTICAST :LOOPBACK :UP}},
      :loopback? true,
      :mac "00:00:00:00:00:00"},
     "gif0"
     {:mtu 1280,
      :name "gif0",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 8010, :flags #{:POINTOPOINT :MULTICAST}},
      :loopback? false,
      :mac nil},
     "UHC61"
     {:mtu 0,
      :name "UHC61",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 0, :flags #{}},
      :loopback? false,
      :mac nil},
     "stf0"
     {:mtu 1280,
      :name "stf0",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 0, :flags #{}},
      :loopback? false,
      :mac nil},
     "UHC29"
     {:mtu 0,
      :name "UHC29",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 0, :flags #{}},
      :loopback? false,
      :mac nil},
     "EHC253"
     {:mtu 0,
      :name "EHC253",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 0, :flags #{}},
      :loopback? false,
      :mac nil},
     "UHC93"
     {:mtu 0,
      :name "UHC93",
      :nd6-options nil,
      :status :down,
      :ipv6 [],
      :ipv4 [],
      :options nil,
      :flags {:value 0, :flags #{}},
      :loopback? false,
      :mac nil},
     "en0"
     {:mtu 1500,
      :name "en0",
      :nd6-options {:value 201, :flags #{:PERFORMNUD :DAD}},
      :status :up,
      :ipv6
      [{:address "fe80::1c36:9d2b:7b58:839d", :prefix 64}
       {:address "fec0::14e9:2943:cac0:7d4e", :prefix 64}
       {:address "fec0::416:636f:c86a:e7e0", :prefix 64}],
      :ipv4 [{:address "10.0.2.15", :prefix 24}],
      :options nil,
      :flags
      {:value 8863,
       :flags #{:BROADCAST :RUNNING :SIMPLEX :SMART :MULTICAST :UP}},
      :loopback? false,
      :mac "52:54:00:c9:18:27"}},
    :default-gateway {:gateway "10.0.2.2", :interface "en0"},
    :dns {:nameservers ["10.0.2.3"], :search []}},
   :windows
   {:hostname "WINTEST",
    :interfaces
    {"Ethernet"
     {:name "Ethernet",
      :ipv4 [{:address "10.0.2.15", :prefix 24}],
      :ipv6
      [{:address "fec0::f107:b797:1279:92c5%1", :prefix 64}
       {:address "fe80::f107:b797:1279:92c5%5", :prefix 64}],
      :loopback? false,
      :status :up,
      :mtu nil,
      :mac "52:54:00:12:34:56"},
     "Loopback Pseudo-Interface 1"
     {:name "Loopback Pseudo-Interface 1",
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :up,
      :mtu 4294967295}},
    :default-gateway {:address "10.0.2.2", :interface "Ethernet"},
    :dns {:nameservers ["10.0.2.3"], :search []}},
   :debian-fish
   {:hostname "2fd4a44d269d",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "36:64:f7:cb:c5:42",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.4", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :rockylinux
   {:hostname "7e66fc3c7532",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "d6:56:73:93:7d:57",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.8", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux-ksh
   {:hostname "e90efd4e35d3",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux-dash
   {:hostname "e90efd4e35d3",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux-zsh
   {:hostname "e90efd4e35d3",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :alpine-dash
   {:hostname "28e971df93a4",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.2", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "56:4c:86:1e:01:b5",
      :mtu 1500,
      :peer-index "if37",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :debian-ksh
   {:hostname "2fd4a44d269d",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "36:64:f7:cb:c5:42",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.4", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora-zsh
   {:hostname "0ce7dfc1211a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "f2:61:fd:1d:5c:37",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.5", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :amazonlinux-zsh
   {:hostname "a9ec7992a58e",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ba:af:fd:4e:c5:84",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.7", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora
   {:hostname "0ce7dfc1211a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "f2:61:fd:1d:5c:37",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.5", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux-nu
   {:hostname "",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :ubuntu-fish
   {:hostname "12a9351f84b5",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ea:1a:21:48:95:e2",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.3", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora-ksh
   {:hostname "0ce7dfc1211a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "f2:61:fd:1d:5c:37",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.5", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :debian-dash
   {:hostname "2fd4a44d269d",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "36:64:f7:cb:c5:42",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.4", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :oraclelinux-ksh
   {:hostname "bdf38b91267a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "e2:b3:f0:51:06:a4",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.9", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :alpine
   {:hostname "28e971df93a4",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.2", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "56:4c:86:1e:01:b5",
      :mtu 1500,
      :peer-index "if37",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :amazonlinux
   {:hostname "a9ec7992a58e",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ba:af:fd:4e:c5:84",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.7", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux-fish
   {:hostname "e90efd4e35d3",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora-nu
   {:hostname "",
    :interfaces {},
    :default-gateway nil,
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :ubuntu-ksh
   {:hostname "12a9351f84b5",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ea:1a:21:48:95:e2",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.3", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :rockylinux-ksh
   {:hostname "7e66fc3c7532",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "d6:56:73:93:7d:57",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.8", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :oraclelinux-zsh
   {:hostname "bdf38b91267a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "e2:b3:f0:51:06:a4",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.9", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :archlinux
   {:hostname "e90efd4e35d3",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.6", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "12:19:c9:f4:95:fd",
      :mtu 1500,
      :peer-index "if41",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :netbsd
   {:hostname "netbsd.localdomain",
    :interfaces
    {"vioif0"
     {:mtu 1500,
      :name "vioif0",
      :nd6-options nil,
      :status :up,
      :ipv6
      [{:address "fe80::5054:ff:fe12:3456", :prefix 64}
       {:address "fec0::f85f:423c:2d51:522d", :prefix 64}],
      :ipv4 [{:address "10.0.2.15", :prefix 24}],
      :options nil,
      :flags nil,
      :loopback? false,
      :mac nil},
     "lo0"
     {:mtu 33624,
      :name "lo0",
      :nd6-options nil,
      :status :up,
      :ipv6
      [{:address "::1", :prefix 128} {:address "fe80::1", :prefix 64}],
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :options nil,
      :flags nil,
      :loopback? true,
      :mac "00:00:00:00:00:00"}},
    :default-gateway {:gateway "10.0.2.2", :interface "vioif0"},
    :dns {:nameservers ["10.0.2.3"], :search []}},
   :debian
   {:hostname "2fd4a44d269d",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "36:64:f7:cb:c5:42",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.4", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :rockylinux-zsh
   {:hostname "7e66fc3c7532",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "d6:56:73:93:7d:57",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.8", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora-dash
   {:hostname "0ce7dfc1211a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "f2:61:fd:1d:5c:37",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.5", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :debian-zsh
   {:hostname "2fd4a44d269d",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "36:64:f7:cb:c5:42",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.4", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :fedora-fish
   {:hostname "0ce7dfc1211a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "f2:61:fd:1d:5c:37",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.5", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :ubuntu-zsh
   {:hostname "12a9351f84b5",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ea:1a:21:48:95:e2",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.3", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :ubuntu-dash
   {:hostname "12a9351f84b5",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ea:1a:21:48:95:e2",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.3", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :alpine-fish
   {:hostname "28e971df93a4",
    :interfaces
    {"eth0"
     {:ipv4 [{:address "172.17.0.2", :prefix 16}],
      :ipv6 [],
      :loopback? false,
      :status :up,
      :mac "56:4c:86:1e:01:b5",
      :mtu 1500,
      :peer-index "if37",
      :name "eth0"},
     "lo"
     {:ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}],
      :loopback? true,
      :status :unknown,
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :name "lo"}},
    :default-gateway {:address "172.17.0.1", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :ubuntu
   {:hostname "12a9351f84b5",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "ea:1a:21:48:95:e2",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.3", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}},
   :oraclelinux
   {:hostname "bdf38b91267a",
    :interfaces
    {"eth0"
     {:name "eth0",
      :mac "e2:b3:f0:51:06:a4",
      :mtu 1500,
      :status :up,
      :loopback? false,
      :ipv4 [{:address "172.17.0.9", :prefix 16}],
      :ipv6 []},
     "lo"
     {:name "lo",
      :mac "00:00:00:00:00:00",
      :mtu 65536,
      :status :unknown,
      :loopback? true,
      :ipv4 [{:address "127.0.0.1", :prefix 8}],
      :ipv6 [{:address "::1", :prefix 128}]}},
    :default-gateway {:network "0.0.0.0", :interface "eth0"},
    :dns {:nameservers ["192.168.92.2"], :search ["plume"]}}})

(deftest network-test
  (is (=
        (into {}
              (for [host (config/select-hosts {:exclude #{}})]
                (let [exec (shell-test/make-executor-fn (config/host-ports host))]
                  (prn host)
                  [host (network/gather-network
                          {:exec exec
                           :shell (shell/gather-shell {:exec exec})})])))
        (config/filter-hashmap
          {:exclude #{}}
          network-results))))
