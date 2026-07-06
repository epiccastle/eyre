(ns eyre.network-parse
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [eyre.utils :as utils :refer [embed newlines]]))

;;
;; iproute2
;;

(defn join-ip-o-lines
  "ip -o output escapes continuation lines with a trailing backslash. Join
  those physical lines back into single logical lines."
  [s]
  (-> s
      (str/replace #"\\\s*\n\s*" " ")
      str/trim-newline))

(defn parse-ip-o-addr
  "Parses `ip -o addr` output into a map of interface-name ->
  {:ipv4 [...] :ipv6 [...]}."
  [s]
  (->> (str/split-lines (join-ip-o-lines s))
       (keep (fn [line]
               (when-let [[_ ifname family addr]
                          (re-find #"\s*\d+:\s+(\S+)\s+(inet6?)\s+(\S+)" line)]
                 (let [[ip prefix] (str/split addr #"/")
                       family (if (= family "inet") :ipv4 :ipv6)
                       prefix (when prefix (utils/parse-prefix prefix))]
                   [ifname {family [{:address ip :prefix prefix}]}]))))
       (reduce (fn [m [ifname entry]]
                 (merge-with into m {ifname entry}))
               {})))

#_(parse-ip-o-addr
    "1: lo    inet 127.0.0.1/8 scope host lo\\       valid_lft forever preferred_lft forever
1: lo    inet6 ::1/128 scope host \\       valid_lft forever preferred_lft forever
2: eth0    inet 172.17.0.2/16 brd 172.17.255.255 scope global eth0\\       valid_lft forever preferred_lft forever"
    )

(defn parse-ip-o-link
  "Parses `ip -o link` output into a map of interface-name ->
  {:mac :mtu :status :loopback}."
  [s]
  (->> (str/split-lines (join-ip-o-lines s))
       (keep (fn [line]
               (when-let [[_ ifname flags rest]
                          (re-matches #"\s*\d+:\s+([^:]+):\s+<([^>]*)>\s+(.*)" line)]
                 (let [flags-set (set (str/split flags #","))
                       [real-ifname peer-index] (str/split ifname #"@")
                       mtu (some-> (re-find #"mtu\s+(\d+)" rest) second)
                       state (some-> (re-find #"state\s+(\S+)" rest) second)
                       mac (some-> (re-find #"link/(\w+)\s+([0-9a-fA-F:]+)" rest)
                                   (get 2)
                                   utils/normalize-mac)
                       loopback (or (contains? flags-set "LOOPBACK")
                                    (= real-ifname "lo"))
                       link-info (cond-> {:mac mac
                                          :mtu (edn/read-string mtu)
                                          :status (utils/keywordize-status state)
                                          :loopback loopback}
                                         peer-index (assoc :peer-index peer-index))]
                   [real-ifname link-info]))))
       (into {})))

#_ (parse-ip-o-link
     "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000\\    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
2: eth0@if998: <BROADCAST,MULTICAST,UP,LOWER_UP,M-DOWN> mtu 1500 qdisc noqueue state UP \\    link/ether 6a:60:ce:b7:98:97 brd ff:ff:ff:ff:ff:ff"
     )

(defn parse-ip-route-default
  "Parses `ip route show default` output, returns the first default
  route as {:address :interface}."
  [s]
  (some (fn [line]
          (when-let [[_ gateway dev]
                     (re-matches #"\s*default\s+via\s+(\S+)\s+dev\s+(\S+).*" line)]
            {:address gateway :interface dev}))
        (str/split-lines s)))

;;
;; ifconfig parsing (macOS / BSD / linux net-tools fallback)
;;

(defn ifconfig-blocks
  "Splits ifconfig -a output into a list of [name header-line body-lines]."
  [s]
  (loop [lines (str/split-lines s)
         cur-name nil
         cur-header nil
         cur-body []
         acc []]
    (if (empty? lines)
      (if cur-name
        (conj acc [cur-name cur-header cur-body])
        acc)
      (let [line (first lines)]
        (if (re-matches #"\S.*" line)
          ;; new interface header
          (recur (rest lines)
                 (or (some-> (re-find #"^([^\s:]+)" line) second) "unknown")
                 line
                 []
                 (if cur-name
                   (conj acc [cur-name cur-header cur-body])
                   acc))
          (recur (rest lines) cur-name cur-header (conj cur-body (str/trim line)) acc))))))

#_(ifconfig-blocks
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
        nd6 options=21<PERFORMNUD,AUTO_LINKLOCAL>"
    )

(defn parse-angled-flags
  "Parses a token like `flags=1008843<UP,BROADCAST,RUNNING>` or
  `options=880028<VLAN_MTU,JUMBO_MTU>` returning
  `{:value 1008843 :flags #{:UP :BROADCAST :RUNNING}}` or nil."
  [s]
  (when-let [[_ value-str flag-str] (re-find #"=(\d+)<([^>]*)>" s)]
    {:value (edn/read-string value-str)
     :flags (if flag-str
              (->> (str/split flag-str #",")
                   (map keyword )
                   set)
              #{})}))

(defn parse-ifconfig-block [[name header body]]
  (let [flags (parse-angled-flags header)
        options (some->> body
                         (filter #(and (str/includes? % "options=")
                                       (not (str/includes? % "nd6"))))
                         (map parse-angled-flags)
                         (remove nil?)
                         first)
        nd6-options (some->> body
                              (filter #(str/includes? % "nd6 options="))
                              (map parse-angled-flags)
                              (remove nil?)
                         first)
        mtu (some-> (re-find #"mtu\s+(\d+)" header) second)
        loopback? (or (str/includes? header "LOOPBACK")
                     (= name "lo")
                     (= name "lo0"))
        mac (or (some-> #(re-find #"(?:ether|link/ether|HWaddr)\s+([0-9a-fA-F:]+)" %)
                        (some body)
                        second
                        utils/normalize-mac)
                (when loopback? "00:00:00:00:00:00"))
        status (let [status-line (some #(when (str/includes? % "status:") %) body)]
                 (if status-line
                   (utils/keywordize-status (str/trim (second (str/split status-line #":" 2))))
                   (if (str/includes? header "UP") :up :down)))
        addrs (->> body
                   (mapcat (fn [line]
                             (let [ipv4 (some-> (re-find #"inet\s+(?:addr:)?(\S+)(?:\s+netmask\s+(\S+))?" line)
                                                next)
                                   ipv6 (some-> (re-find #"inet6\s+([0-9a-fA-F:%]+)\s+(?:prefixlen\s+(\d+))?" line)
                                                next)
                                   entries (remove nil?
                                                   [(when ipv4
                                                      {:family :ipv4
                                                       :address (first ipv4)
                                                       :prefix (utils/parse-prefix (second ipv4))})
                                                    (when ipv6
                                                      {:family :ipv6
                                                       :address (str/replace (first ipv6) #"%.*" "")
                                                       :prefix (edn/read-string (second ipv6))})])]
                               entries)))
                   (group-by :family))]
    (-> {:ipv4 (mapv #(dissoc % :family) (:ipv4 addrs))
         :ipv6 (mapv #(dissoc % :family) (:ipv6 addrs))}
        (assoc :name name)
        (assoc :mac mac)
        (assoc :mtu (edn/read-string mtu))
        (assoc :status status)
        (assoc :loopback? loopback?)
        (assoc :flags flags)
        (assoc :options options)
        (assoc :nd6-options nd6-options))))

#_ (apply parse-ifconfig-block
     (second
       (ifconfig-blocks
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
        nd6 options=21<PERFORMNUD,AUTO_LINKLOCAL>"
         )))


(defn parse-ifconfig
  "Parses `ifconfig -a` output into a seq of interface maps."
  [s]
  (->> (ifconfig-blocks s)
       (map (fn [block]
              (parse-ifconfig-block block)))))

(defn ipv4-octets [^String ip]
  (mapv #(Integer/parseInt %) (str/split ip #"\.")))

(defn ipv4-in-subnet?
  "Returns true if `ip` belongs to `network`/`netmask`."
  [ip network netmask]
  (let [mask (ipv4-octets netmask)
        ip-b (ipv4-octets ip)
        net-b (ipv4-octets network)]
    (= (mapv bit-and ip-b mask)
       (mapv bit-and net-b mask))))

(defn- parse-ipv6
  "Parses an IPv6 address string into a vector of 8 integers (0-65535)."
  [s]
  (let [s (str/trim s)
        parts (str/split s #"::" -1)]
    (case (count parts)
      1 (let [groups (str/split s #":")]
          (when (not= (count groups) 8)
            (throw (ex-info "Invalid IPv6 address: expected 8 groups"
                             {:input s :groups groups})))
          (mapv #(Integer/parseInt % 16) groups))

      2 (let [[left right] parts
              left-groups  (if (str/blank? left)  [] (str/split left #":"))
              right-groups (if (str/blank? right) [] (str/split right #":"))
              missing (- 8 (+ (count left-groups) (count right-groups)))]
          (when (neg? missing)
            (throw (ex-info "Invalid IPv6 address: too many groups"
                             {:input s})))
          (mapv #(Integer/parseInt % 16)
                (concat left-groups (repeat missing "0") right-groups)))

      (throw (ex-info "Invalid IPv6 address: multiple '::'" {:input s})))))

(defn- zero-runs
  "Returns a seq of {:start i :len n} maps for each contiguous run of zero groups."
  [groups]
  (loop [i 0 runs [] cur nil]
    (cond
      (= i (count groups))
      (if cur (conj runs cur) runs)

      (zero? (nth groups i))
      (recur (inc i) runs (if cur (update cur :len inc) {:start i :len 1}))

      :else
      (recur (inc i) (if cur (conj runs cur) runs) nil))))

(defn- best-run
  "Finds the longest run of >=2 zero groups; leftmost wins ties. nil if none qualifies."
  [groups]
  (let [runs (filter #(>= (:len %) 2) (zero-runs groups))]
    (when (seq runs)
      ;; apply max-key keeps the first element seen among ties
      (apply max-key :len runs))))

(defn- fmt-groups [groups]
  (map #(format "%x" %) groups))

(defn compress-ipv6
  "Takes a string representation of an IPv6 address (full or already
   compressed) and returns its canonical, maximally-compressed form
   per RFC 5952 (lowercase hex, no leading zeros, longest zero-run
   collapsed to '::', leftmost run wins ties)."
  [s]
  (let [groups (parse-ipv6 s)
        run (best-run groups)]
    (if run
      (let [{:keys [start len]} run
            before (subvec groups 0 start)
            after  (subvec groups (+ start len))]
        (str (str/join ":" (fmt-groups before))
             "::"
             (str/join ":" (fmt-groups after))))
      (str/join ":" (fmt-groups groups)))))

(defn- hex-le->ipv4
  "Converts a little-endian hex string from /proc/net/route (e.g.
  `000011AC`) to dotted-decimal (`172.17.0.0`)."
  [h]
  (->> (re-seq #"[0-9a-fA-F]{2}" h)
       (map #(Integer/parseInt % 16))
       reverse
       (str/join ".")))

(defn- prefix->netmask
  "Converts a prefix length (0-32) to a dotted-decimal netmask."
  [prefix]
  (let [bits (str (str/join (repeat prefix "1"))
                  (str/join (repeat (- 32 prefix) "0")))]
    (str/join "."
              (for [i (range 0 32 8)]
                (Integer/parseInt (subs bits i (+ i 8)) 2)))))

(defn- parse-sys-class-net
  "Parses a concatenated dump of /sys/class/net/<iface>/* files into a
  list of interface maps with :name :mac :mtu :status :loopback?."
  [s]
  (let [entries (for [line (str/split-lines s)
                      :let [[_ iface file value]
                            (re-matches #"/sys/class/net/([^/]+)/([^:]+):(.*)" line)]
                      :when iface]
                  {:iface iface :file file :value value})
        by-iface (group-by :iface entries)]
    (for [[name recs] (sort-by first by-iface)]
      (let [m (into {} (for [{:keys [file value]} recs]
                         [(keyword file) value]))
            type (some-> (:type m) edn/read-string)]
        {:name      name
         :mac       (some-> (:address m) utils/normalize-mac)
         :mtu       (some-> (:mtu m) edn/read-string)
         :status    (some-> (:operstate m) utils/keywordize-status)
         :loopback? (or (= name "lo") (= type 772))
         :ipv4      []
         :ipv6      []}))))

(defn- parse-proc-net-route
  "Parses /proc/net/route into a list of route maps:
  {:iface :network :netmask :prefix :gateway}. Destination and mask are
  stored little-endian hex in the file and are decoded to dotted-decimal."
  [s]
  (->> (str/split-lines s)
       rest
       (map str/trim)
       (remove str/blank?)
       (keep (fn [line]
               (let [fields (str/split line #"\t+")]
                 (when (>= (count fields) 8)
                   (let [iface   (nth fields 0)
                         dest    (nth fields 1)
                         gw      (nth fields 2)
                         mask    (nth fields 7)
                         network (hex-le->ipv4 dest)
                         netmask (hex-le->ipv4 mask)]
                     {:iface   iface
                      :network network
                      :netmask netmask
                      :prefix  (utils/parse-prefix netmask)
                      :gateway (hex-le->ipv4 gw)})))))))

(defn- parse-proc-net-fib-trie
  "Parses /proc/net/fib_trie into a list of {:address :prefix :scope
  :type} entries. The Main and Local sections duplicate the same data
  so the result is deduplicated."
  [s]
  (let [re #"\|--\s+(\d+\.\d+\.\d+\.\d+)\s*\n\s+/(\d+)\s+(\S+)\s+(\S+)"
        entries (for [[_ addr prefix scope type] (re-seq re s)]
                  {:address addr
                   :prefix  (edn/read-string prefix)
                   :scope   scope
                   :type    type})]
    (distinct entries)))

(defn parse-proc-network-info
  "Parses raw /sys/class/net, /proc/net/route and /proc/net/fib_trie
  dumps into a seq of interface maps, mirroring `parse-ifconfig`.

  Local IPv4 addresses are the `host LOCAL` /32 leaf entries from
  fib_trie. Each is assigned to the interface whose subnet contains it
  (subnets come from fib_trie; the owning interface is resolved through
  /proc/net/route, with 127.0.0.0/8 inferred as the loopback). The
  reported prefix is the containing subnet's prefix length, matching
  `ifconfig`'s netmask reporting rather than the /32 host route. The
  IPv6 loopback address (::1/128) is added to loopback interfaces."
  [sys-class-net proc-net-route proc-net-fib-trie]
  (let [interfaces  (vec (parse-sys-class-net sys-class-net))
        lo-name     (some #(when (:loopback? %) (:name %)) interfaces)
        routes      (parse-proc-net-route proc-net-route)
        fib-entries (parse-proc-net-fib-trie proc-net-fib-trie)

        ;; actual assigned interface addresses: host-scoped LOCAL /32 leaves
        local-addrs (for [e fib-entries
                          :when (and (= (:type e) "LOCAL")
                                     (= (:scope e) "host")
                                     (= (:prefix e) 32))]
                      (:address e))

        ;; subnet routes (prefix < 32) define the on-link networks
        subnets (for [e fib-entries
                      :when (and (< (:prefix e) 32)
                                 (contains? #{"UNICAST" "LOCAL"} (:type e)))]
                  e)

        route-by-net (into {}
                           (for [r routes]
                             [[(:network r) (:prefix r)] (:iface r)]))

        subnet-infos (for [s subnets]
                       (let [net    (:address s)
                             prefix (:prefix s)
                             iface  (or (get route-by-net [net prefix])
                                        (when (str/starts-with? net "127.")
                                          lo-name))]
                         {:network net
                          :prefix  prefix
                          :netmask (prefix->netmask prefix)
                          :iface   iface}))

        addr-info (fn [addr]
                    (->> subnet-infos
                         (filter #(ipv4-in-subnet? addr
                                                   (:network %)
                                                   (:netmask %)))
                         (sort-by :prefix >)
                         first))

        assign-addr (fn [interfaces addr]
                      (let [{:keys [iface prefix]} (addr-info addr)
                            iface  (or iface
                                       (when (str/starts-with? addr "127.")
                                         lo-name))
                            prefix (or prefix
                                       (when (str/starts-with? addr "127.")
                                         8))]
                        (if iface
                          (mapv #(if (= (:name %) iface)
                                   (update % :ipv4 conj
                                           {:address addr :prefix prefix})
                                   %)
                                interfaces)
                          interfaces)))]
    (->> (reduce assign-addr interfaces local-addrs)
         (map (fn [i]
                (cond-> i
                        (:loopback? i)
                        (assoc :ipv6 [{:address "::1" :prefix 128}]))))
         (filter :name))))

(parse-proc-network-info
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
"
  )
