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
