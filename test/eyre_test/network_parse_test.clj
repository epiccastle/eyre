(ns eyre-test.network-parse-test
  (:require [clojure.test :refer :all]
            [eyre.shell :as shell]
            [eyre.network-parse :as network-parse]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

;;
;; iproute2
;;

(deftest join-ip-o-lines
  (is (= "line 1 line 2
line 3"
         (network-parse/join-ip-o-lines "line 1\\
line 2
line 3"))))

(deftest parse-ip-o-addr
  (is (= {"lo" {:ipv4 [{:address "127.0.0.1"
                        :prefix 8}]
                :ipv6 [{:address "::1"
                        :prefix 128}]}
          "eth0" {:ipv4 [{:address "172.17.0.2"
                          :prefix 16}]}}

         (network-parse/parse-ip-o-addr
           "1: lo    inet 127.0.0.1/8 scope host lo\\       valid_lft forever preferred_lft forever
1: lo    inet6 ::1/128 scope host \\       valid_lft forever preferred_lft forever
2: eth0    inet 172.17.0.2/16 brd 172.17.255.255 scope global eth0\\       valid_lft forever preferred_lft forever"))))

(deftest parse-ip-o-link
  (is (= {"lo" {:mac "00:00:00:00:00:00"
                :mtu 65536
                :status :unknown
                :loopback true}
          "eth0" {:mac "6a:60:ce:b7:98:97"
                  :mtu 1500
                  :status :up
                  :loopback false
                  :peer-index "if998"}}

        (network-parse/parse-ip-o-link
     "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000\\    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
2: eth0@if998: <BROADCAST,MULTICAST,UP,LOWER_UP,M-DOWN> mtu 1500 qdisc noqueue state UP \\    link/ether 6a:60:ce:b7:98:97 brd ff:ff:ff:ff:ff:ff"
     ))))

(deftest parse-ip-route-default
  (is (= {:address "192.168.1.1"
          :interface "eno1"}
         (network-parse/parse-ip-route-default
           "default via 192.168.1.1 dev eno1 proto dhcp src 192.168.1.240 metric 100"))))

;;
;; ifconfig
;;

(deftest ifconfig-blocks
  (is (= [["vtnet0"
           "vtnet0: flags=1008843<UP,BROADCAST,RUNNING,SIMPLEX,MULTICAST,LOWER_UP> metric 0 mtu 1500"
           ["options=880028<VLAN_MTU,JUMBO_MTU,LINKSTATE,HWSTATS>"
            "ether 52:54:00:12:34:56"
            "inet 10.0.2.15 netmask 0xffffff00 broadcast 10.0.2.255"
            "media: Ethernet autoselect (10Gbase-T <full-duplex>)"
            "status: active"
            "nd6 options=29<PERFORMNUD,IFDISABLED,AUTO_LINKLOCAL>"]]
          ["lo0"
           "lo0: flags=1008049<UP,LOOPBACK,RUNNING,MULTICAST,LOWER_UP> metric 0 mtu 16384"
           ["options=680003<RXCSUM,TXCSUM,LINKSTATE,RXCSUM_IPV6,TXCSUM_IPV6>"
            "inet 127.0.0.1 netmask 0xff000000"
            "inet6 ::1 prefixlen 128"
            "inet6 fe80::1%lo0 prefixlen 64 scopeid 0x2"
            "groups: lo"
            "nd6 options=21<PERFORMNUD,AUTO_LINKLOCAL>"]]]

         (network-parse/ifconfig-blocks
"vtnet0: flags=1008843<UP,BROADCAST,RUNNING,SIMPLEX,MULTICAST,LOWER_UP> metric 0 mtu 1500
        options=880028<VLAN_MTU,JUMBO_MTU,LINKSTATE,HWSTATS>
        ether 52:54:00:12:34:56
        inet 10.0.2.15 netmask 0xffffff00 broadcast 10.0.2.255
        media: Ethernet autoselect (10Gbase-T <full-duplex>)
        status: active
        nd6 options=29<PERFORMNUD,IFDISABLED,AUTO_LINKLOCAL>
lo0: flags=1008049<UP,LOOPBACK,RUNNING,MULTICAST,LOWER_UP> metric 0 mtu 16384
        options=680003<RXCSUM,TXCSUM,LINKSTATE,RXCSUM_IPV6,TXCSUM_IPV6>
        inet 127.0.0.1 netmask 0xff000000
        inet6 ::1 prefixlen 128
        inet6 fe80::1%lo0 prefixlen 64 scopeid 0x2
        groups: lo
        nd6 options=21<PERFORMNUD,AUTO_LINKLOCAL>"))))

(deftest parse-angled-flags
  (is (= {:value 1008843
          :flags #{:BROADCAST :RUNNING :UP}}
         (network-parse/parse-angled-flags "flags=1008843<UP,BROADCAST,RUNNING>")))
  (is (= {:value 880028
          :flags #{:JUMBO_MTU :VLAN_MTU}}
         (network-parse/parse-angled-flags "options=880028<VLAN_MTU,JUMBO_MTU>"))))

(deftest parse-ifconfig-block
  (is (= {:mtu 1500
          :name "vtnet0"
          :nd6-options {:value 29
                        :flags #{:AUTO_LINKLOCAL :PERFORMNUD :IFDISABLED}}
          :status :up
          :ipv6 []
          :ipv4 [{:address "10.0.2.15", :prefix 24}]
          :options {:value 880028
                    :flags #{:LINKSTATE :HWSTATS :JUMBO_MTU :VLAN_MTU}}
          :flags {:value 1008843
                  :flags #{:BROADCAST :RUNNING :SIMPLEX :LOWER_UP :MULTICAST :UP}}
          :loopback? false, :mac "52:54:00:12:34:56"}

         (network-parse/parse-ifconfig-block
           ["vtnet0"
            "vtnet0: flags=1008843<UP,BROADCAST,RUNNING,SIMPLEX,MULTICAST,LOWER_UP> metric 0 mtu 1500"
            ["options=880028<VLAN_MTU,JUMBO_MTU,LINKSTATE,HWSTATS>"
             "ether 52:54:00:12:34:56"
             "inet 10.0.2.15 netmask 0xffffff00 broadcast 10.0.2.255"
             "media: Ethernet autoselect (10Gbase-T <full-duplex>)"
             "status: active"
             "nd6 options=29<PERFORMNUD,IFDISABLED,AUTO_LINKLOCAL>"]])))
  (is (=
        {:mtu 16384
         :name "lo0"
         :nd6-options {:value 21
                       :flags #{:AUTO_LINKLOCAL :PERFORMNUD}}
         :status :up
         :ipv6 [{:address "::1"
                 :prefix 128}]
         :ipv4 [{:address "127.0.0.1"
                 :prefix 8}]
         :options {:value 680003
                   :flags #{:LINKSTATE :RXCSUM :RXCSUM_IPV6 :TXCSUM_IPV6 :TXCSUM}}
         :flags {:value 1008049
                 :flags #{:RUNNING :LOWER_UP :MULTICAST :LOOPBACK :UP}}
         :loopback? true
         :mac "00:00:00:00:00:00"}

        (network-parse/parse-ifconfig-block
          ["lo0"
           "lo0: flags=1008049<UP,LOOPBACK,RUNNING,MULTICAST,LOWER_UP> metric 0 mtu 16384"
           ["options=680003<RXCSUM,TXCSUM,LINKSTATE,RXCSUM_IPV6,TXCSUM_IPV6>"
            "inet 127.0.0.1 netmask 0xff000000"
            "inet6 ::1 prefixlen 128"
            "inet6 fe80::1%lo0 prefixlen 64 scopeid 0x2"
            "groups: lo"
            "nd6 options=21<PERFORMNUD,AUTO_LINKLOCAL>"]]))))

(deftest ipv4-octets
  (is (= [192 168 0 1] (network-parse/ipv4-octets "192.168.0.1"))))

(deftest ipv4-in-subnet?
  (is (network-parse/ipv4-in-subnet? "192.168.0.1" "192.168.0.0" "255.255.255.0"))
  (is (not (network-parse/ipv4-in-subnet? "192.168.1.1" "192.168.0.0" "255.255.255.0")))
  (is (network-parse/ipv4-in-subnet? "192.168.1.1" "192.168.0.0" "255.255.254.0"))
  (is (not (network-parse/ipv4-in-subnet? "192.168.2.1" "192.168.0.0" "255.255.254.0")))
  (is (network-parse/ipv4-in-subnet? "192.168.192.1" "192.168.128.0" "255.255.128.0"))
  (is (not (network-parse/ipv4-in-subnet? "192.168.64.1" "192.168.128.0" "255.255.128.0")))
  (is (network-parse/ipv4-in-subnet? "192.168.192.1" "192.168.128.0" "255.255.128.0"))
  (is (network-parse/ipv4-in-subnet? "192.168.192.1" "10.0.1.1" "0.0.0.0"))
  (is (network-parse/ipv4-in-subnet? "192.168.0.1" "192.168.0.1" "255.255.255.255"))
  (is (not (network-parse/ipv4-in-subnet? "192.168.0.1" "192.168.0.2" "255.255.255.255"))))

(deftest compress-ipv6
  (is (= "fe80::9452:d6ff:fea7:9c3c"
         (network-parse/compress-ipv6 "fe80::9452:d6ff:fea7:9c3c")))
  (is (= "::9452:d6ff:fea7:9c3c"
         (network-parse/compress-ipv6 "0000:0000:0000:0000:9452:d6ff:fea7:9c3c")))
  (is (= "::1234:0:9452:d6ff:fea7:9c3c"
         (network-parse/compress-ipv6 "0000:0000:1234:0000:9452:d6ff:fea7:9c3c")))
  (is (= "0:1234::9452:d6ff:fea7:9c3c"
         (network-parse/compress-ipv6 "0000:1234:0000:0000:9452:d6ff:fea7:9c3c")))
  (is (= "0:1234:0:1234:0:d6ff::"
        (network-parse/compress-ipv6 "0000:1234:0000:1234:0000:d6ff:0000:0000"))))

(deftest parse-proc-network-info
  (is (= [{:name "eth0"
           :mac "52:0a:b9:5e:8a:1d"
           :mtu 1500
           :status :up
           :loopback? false
           :ipv4 [{:address "172.17.0.3"
                   :prefix 16}]
           :ipv6 []}
          {:name "lo"
           :mac "00:00:00:00:00:00"
           :mtu 65536
           :status :unknown
           :loopback? true
           :ipv4 [{:address "127.0.0.1"
                   :prefix 8}]
           :ipv6 [{:address "::1"
                   :prefix 128}]}]

         (network-parse/parse-proc-network-info
           ;; sys-class-net
           "/sys/class/net/eth0/uevent:INTERFACE=eth0
/sys/class/net/eth0/uevent:IFINDEX=2
/sys/class/net/eth0/carrier_changes:2
/sys/class/net/eth0/testing:0
/sys/class/net/eth0/carrier:1
/sys/class/net/eth0/dev_id:0x0
/sys/class/net/eth0/carrier_down_count:1
/sys/class/net/eth0/proto_down:0
/sys/class/net/eth0/address:52:0a:b9:5e:8a:1d
/sys/class/net/eth0/operstate:up
/sys/class/net/eth0/link_mode:0
/sys/class/net/eth0/dormant:0
/sys/class/net/eth0/statistics/tx_errors:0
/sys/class/net/eth0/statistics/rx_length_errors:0
/sys/class/net/eth0/statistics/rx_packets:57765
/sys/class/net/eth0/statistics/tx_carrier_errors:0
/sys/class/net/eth0/statistics/tx_dropped:0
/sys/class/net/eth0/statistics/rx_missed_errors:0
/sys/class/net/eth0/statistics/rx_over_errors:0
/sys/class/net/eth0/statistics/tx_aborted_errors:0
/sys/class/net/eth0/statistics/rx_crc_errors:0
/sys/class/net/eth0/statistics/rx_frame_errors:0
/sys/class/net/eth0/statistics/rx_nohandler:0
/sys/class/net/eth0/statistics/tx_fifo_errors:0
/sys/class/net/eth0/statistics/multicast:0
/sys/class/net/eth0/statistics/tx_packets:47824
/sys/class/net/eth0/statistics/tx_window_errors:0
/sys/class/net/eth0/statistics/rx_bytes:8160751
/sys/class/net/eth0/statistics/collisions:0
/sys/class/net/eth0/statistics/rx_dropped:0
/sys/class/net/eth0/statistics/tx_bytes:7552857
/sys/class/net/eth0/statistics/tx_heartbeat_errors:0
/sys/class/net/eth0/statistics/rx_fifo_errors:0
/sys/class/net/eth0/statistics/rx_errors:0
/sys/class/net/eth0/statistics/tx_compressed:0
/sys/class/net/eth0/statistics/rx_compressed:0
/sys/class/net/eth0/mtu:1500
/sys/class/net/eth0/gro_flush_timeout:0
/sys/class/net/eth0/power/runtime_active_time:0
/sys/class/net/eth0/power/runtime_status:unsupported
/sys/class/net/eth0/power/runtime_suspended_time:0
/sys/class/net/eth0/power/control:auto
/sys/class/net/eth0/carrier_up_count:1
/sys/class/net/eth0/speed:10000
/sys/class/net/eth0/netdev_group:0
/sys/class/net/eth0/napi_defer_hard_irqs:0
/sys/class/net/eth0/ifindex:2
/sys/class/net/eth0/broadcast:ff:ff:ff:ff:ff:ff
/sys/class/net/eth0/type:1
/sys/class/net/eth0/dev_port:0
/sys/class/net/eth0/queues/tx-0/tx_maxrate:0
/sys/class/net/eth0/queues/tx-0/xps_cpus:00000000
/sys/class/net/eth0/queues/tx-0/tx_timeout:0
/sys/class/net/eth0/queues/tx-0/xps_rxqs:00000000
/sys/class/net/eth0/queues/tx-0/traffic_class:0
/sys/class/net/eth0/queues/rx-0/rps_flow_cnt:0
/sys/class/net/eth0/queues/rx-0/rps_cpus:00000000
/sys/class/net/eth0/name_assign_type:4
/sys/class/net/eth0/duplex:full
/sys/class/net/eth0/addr_assign_type:3
/sys/class/net/eth0/addr_len:6
/sys/class/net/eth0/threaded:0
/sys/class/net/eth0/tx_queue_len:0
/sys/class/net/eth0/iflink:999
/sys/class/net/eth0/flags:0x1003
/sys/class/net/lo/uevent:INTERFACE=lo
/sys/class/net/lo/uevent:IFINDEX=1
/sys/class/net/lo/carrier_changes:0
/sys/class/net/lo/testing:0
/sys/class/net/lo/carrier:1
/sys/class/net/lo/dev_id:0x0
/sys/class/net/lo/carrier_down_count:0
/sys/class/net/lo/proto_down:0
/sys/class/net/lo/address:00:00:00:00:00:00
/sys/class/net/lo/operstate:unknown
/sys/class/net/lo/link_mode:0
/sys/class/net/lo/dormant:0
/sys/class/net/lo/statistics/tx_errors:0
/sys/class/net/lo/statistics/rx_length_errors:0
/sys/class/net/lo/statistics/rx_packets:0
/sys/class/net/lo/statistics/tx_carrier_errors:0
/sys/class/net/lo/statistics/tx_dropped:0
/sys/class/net/lo/statistics/rx_missed_errors:0
/sys/class/net/lo/statistics/rx_over_errors:0
/sys/class/net/lo/statistics/tx_aborted_errors:0
/sys/class/net/lo/statistics/rx_crc_errors:0
/sys/class/net/lo/statistics/rx_frame_errors:0
/sys/class/net/lo/statistics/rx_nohandler:0
/sys/class/net/lo/statistics/tx_fifo_errors:0
/sys/class/net/lo/statistics/multicast:0
/sys/class/net/lo/statistics/tx_packets:0
/sys/class/net/lo/statistics/tx_window_errors:0
/sys/class/net/lo/statistics/rx_bytes:0
/sys/class/net/lo/statistics/collisions:0
/sys/class/net/lo/statistics/rx_dropped:0
/sys/class/net/lo/statistics/tx_bytes:0
/sys/class/net/lo/statistics/tx_heartbeat_errors:0
/sys/class/net/lo/statistics/rx_fifo_errors:0
/sys/class/net/lo/statistics/rx_errors:0
/sys/class/net/lo/statistics/tx_compressed:0
/sys/class/net/lo/statistics/rx_compressed:0
/sys/class/net/lo/mtu:65536
/sys/class/net/lo/gro_flush_timeout:0
/sys/class/net/lo/power/runtime_active_time:0
/sys/class/net/lo/power/runtime_status:unsupported
/sys/class/net/lo/power/runtime_suspended_time:0
/sys/class/net/lo/power/control:auto
/sys/class/net/lo/carrier_up_count:0
/sys/class/net/lo/netdev_group:0
/sys/class/net/lo/napi_defer_hard_irqs:0
/sys/class/net/lo/ifindex:1
/sys/class/net/lo/broadcast:00:00:00:00:00:00
/sys/class/net/lo/type:772
/sys/class/net/lo/dev_port:0
/sys/class/net/lo/queues/tx-0/tx_maxrate:0
/sys/class/net/lo/queues/tx-0/tx_timeout:0
/sys/class/net/lo/queues/tx-0/xps_rxqs:0
/sys/class/net/lo/queues/rx-0/rps_flow_cnt:0
/sys/class/net/lo/queues/rx-0/rps_cpus:00000000
/sys/class/net/lo/name_assign_type:2
/sys/class/net/lo/addr_assign_type:0
/sys/class/net/lo/addr_len:6
/sys/class/net/lo/threaded:0
/sys/class/net/lo/tx_queue_len:1000
/sys/class/net/lo/iflink:1
/sys/class/net/lo/flags:0x9"

           ;; proc-net-route
           "Iface	Destination	Gateway         Flags	RefCnt	Use	Metric	Mask		MTU	Window	IRTT
eth0	00000000	010011AC	0003	0	0	0	00000000	0	0	0
eth0	000011AC	00000000	0001	0	0	0	0000FFFF	0	0	0
"

           ;; proc-net-fib-trie
           "Main:
  +-- 0.0.0.0/0 3 0 5
     |-- 0.0.0.0
        /0 universe UNICAST
     +-- 127.0.0.0/8 2 0 2
        +-- 127.0.0.0/31 1 0 0
           |-- 127.0.0.0
              /8 host LOCAL
           |-- 127.0.0.1
              /32 host LOCAL
        |-- 127.255.255.255
           /32 link BROADCAST
     +-- 172.17.0.0/16 2 0 2
        +-- 172.17.0.0/30 2 0 2
           |-- 172.17.0.0
              /16 link UNICAST
           |-- 172.17.0.3
              /32 host LOCAL
        |-- 172.17.255.255
           /32 link BROADCAST
Local:
  +-- 0.0.0.0/0 3 0 5
     |-- 0.0.0.0
        /0 universe UNICAST
     +-- 127.0.0.0/8 2 0 2
        +-- 127.0.0.0/31 1 0 0
           |-- 127.0.0.0
              /8 host LOCAL
           |-- 127.0.0.1
              /32 host LOCAL
        |-- 127.255.255.255
           /32 link BROADCAST
     +-- 172.17.0.0/16 2 0 2
        +-- 172.17.0.0/30 2 0 2
           |-- 172.17.0.0
              /16 link UNICAST
           |-- 172.17.0.3
              /32 host LOCAL
        |-- 172.17.255.255
           /32 link BROADCAST
")

         )))

(deftest parse-netstat-default-route
  (testing "linux"
    (is (= {:gateway "192.168.12.1"
            :interface "eno1"}
           (network-parse/parse-netstat-default-route
             "Kernel IP routing table
Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface
0.0.0.0         192.168.12.1    0.0.0.0         UG        0 0          0 eno1
172.17.0.0      0.0.0.0         255.255.0.0     U         0 0          0 docker0
192.168.12.0    0.0.0.0         255.255.255.0   U         0 0          0 eno1"))))

  (testing "freebsd"
    (is (= {:gateway "10.0.2.2"
            :interface "vtnet0"}
           (network-parse/parse-netstat-default-route
             "Routing tables

Internet:
Destination        Gateway            Flags         Netif Expire
default            10.0.2.2           UGS          vtnet0
10.0.2.0/24        link#1             U            vtnet0
10.0.2.15          link#2             UHS             lo0
127.0.0.1          link#2             UH              lo0

Internet6:
Destination                       Gateway                       Flags         Netif Expire
::/96                             link#2                        URS             lo0
::1                               link#2                        UHS             lo0
::ffff:0.0.0.0/96                 link#2                        URS             lo0
fe80::%lo0/10                     link#2                        URS             lo0
fe80::%lo0/64                     link#2                        U               lo0
fe80::1%lo0                       link#2                        UHS             lo0
ff02::/16                         link#2                        URS             lo0")))))

(deftest parse-resolv-conf
  (is (= {:nameservers ["192.168.12.2" "192.168.12.3"]
          :search ["mydomain.com" "sub.mydomain.com"]}
         (network-parse/parse-resolv-conf
           "# A comment
domain overridden.com
search mydomain.com sub.mydomain.com
nameserver 192.168.12.2
nameserver 192.168.12.3
options timeout:2
"))))

(deftest parse-scutil-dns
  (is (= {:nameservers ["10.0.2.3" "10.0.2.3"]
          :search []}
         (network-parse/parse-scutil-dns
           ;; TODO: capture a scutil --dns output with a search domain for test
           "DNS configuration

resolver #1
  nameserver[0] : 10.0.2.3
  if_index : 8 (en0)
  flags    : Request A records, Request AAAA records
  reach    : 0x00020002 (Reachable,Directly Reachable Address)

resolver #2
  domain   : local
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 300000

resolver #3
  domain   : 254.169.in-addr.arpa
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 300200

resolver #4
  domain   : 8.e.f.ip6.arpa
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 300400

resolver #5
  domain   : 9.e.f.ip6.arpa
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 300600

resolver #6
  domain   : a.e.f.ip6.arpa
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 300800

resolver #7
  domain   : b.e.f.ip6.arpa
  options  : mdns
  timeout  : 5
  flags    : Request A records, Request AAAA records
  reach    : 0x00000000 (Not Reachable)
  order    : 301000

DNS configuration (for scoped queries)

resolver #1
  nameserver[0] : 10.0.2.3
  if_index : 8 (en0)
  flags    : Scoped, Request A records, Request AAAA records
  reach    : 0x00020002 (Reachable,Directly Reachable Address)"
           ))))

(deftest parse-pipe-rows
  (is (= [["Ethernet" "2" "10.0.2.3"]
          ["Ethernet" "23" ""]
          ["Loopback Pseudo-Interface 1" "2" ""]
          ["Loopback Pseudo-Interface 1" "23" "fec0:0:0:ffff::1,fec0:0:0:ffff::2,fec0:0:0:ffff::3"]]
         (network-parse/parse-pipe-rows
           "Ethernet|2|10.0.2.3\nEthernet|23|\nLoopback Pseudo-Interface 1|2|\nLoopback Pseudo-Interface 1|23|fec0:0:0:ffff::1,fec0:0:0:ffff::2,fec0:0:0:ffff::3\n")))
  (is (= [["Ethernet" "fec0::84ec:5be3:8c22:c04f%1" "IPv6" "64"]
          ["Ethernet" "fe80::84ec:5be3:8c22:c04f%6" "IPv6" "64"]
          ["Loopback Pseudo-Interface 1" "::1" "IPv6" "128"]
          ["Ethernet" "10.0.2.15" "IPv4" "24"]
          ["Loopback Pseudo-Interface 1" "127.0.0.1" "IPv4" "8"]]
         (network-parse/parse-pipe-rows
           "Ethernet|fec0::84ec:5be3:8c22:c04f%1|IPv6|64\nEthernet|fe80::84ec:5be3:8c22:c04f%6|IPv6|64\nLoopback Pseudo-Interface 1|::1|IPv6|128\nEthernet|10.0.2.15|IPv4|24\nLoopback Pseudo-Interface 1|127.0.0.1|IPv4|8\n"))))
