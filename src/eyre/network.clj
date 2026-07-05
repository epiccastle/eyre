(ns eyre.network
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [eyre.utils :as utils :refer [embed newlines]]
            [medley.core :as medley]))

;; Gather facts about a system's network configuration.
;; `determine-network` takes a hashmap with
;; :exec (an executor function) and :shell (the detected shell map
;; from `eyre.shell/determine-shell`).
;;
;;   {:hostname      "host"
;;    :interfaces    [{:name     "eth0"
;;                     :mac      "aa:bb:cc:dd:ee:ff"
;;                     :mtu      1500
;;                     :status   :up            ;; :up / :down / :unknown
;;                     :loopback false
;;                     :ipv4     [{:address "10.0.0.5" :prefix 24}]
;;                     :ipv6     [{:address "fe80::1"   :prefix 64}]}]
;;    :default-gateway {:address "10.0.0.1" :interface "eth0"}
;;    :dns             {:nameservers ["8.8.8.8" "8.8.4.4"]
;;                      :search      ["example.com"]}}

(def posix-gather-script (embed "network/gather.sh"))
(def fish-gather-script (embed "network/gather.fish"))
(def nu-gather-script (embed "network/gather.nu"))
(def powershell-gather-script (embed "network/gather.ps1"))
(def cmd-gather-script (embed "network/gather.cmd"))

(def ^:private gather-scripts
  {:bash       posix-gather-script
   :zsh        posix-gather-script
   :sh         posix-gather-script
   :dash       posix-gather-script
   :ksh        posix-gather-script
   :busybox    posix-gather-script
   :fish       fish-gather-script
   :nu         nu-gather-script
   :powershell powershell-gather-script
   :cmd-exe    cmd-gather-script})

;; ------------------------------------------------------------------
;; small helpers

(defn- blank? [s]
  (or (nil? s) (str/blank? s)))

(defn- present? [s]
  (not (blank? s)))


;; ------------------------------------------------------------------
;; `ip` (iproute2) parsing

(defn- join-ip-o-lines
  "ip -o output escapes continuation lines with a trailing backslash. Join
  those physical lines back into single logical lines."
  [s]
  (-> s
      (str/replace #"\\\s*\n\s*" " ")
      str/trim-newline))

(defn- parse-ip-o-addr
  "Parses `ip -o addr` output into a map of interface-name ->
  {:ipv4 [...] :ipv6 [...]}."
  [s]
  (when (present? s)
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
                 {}))))

#_(parse-ip-o-addr
    "1: lo    inet 127.0.0.1/8 scope host lo\\       valid_lft forever preferred_lft forever
1: lo    inet6 ::1/128 scope host \\       valid_lft forever preferred_lft forever
2: eth0    inet 172.17.0.2/16 brd 172.17.255.255 scope global eth0\\       valid_lft forever preferred_lft forever"
    )

(defn- parse-ip-o-link
  "Parses `ip -o link` output into a map of interface-name ->
  {:mac :mtu :status :loopback}."
  [s]
  (when (present? s)
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
         (into {}))))

#_ (parse-ip-o-link
     "1: lo: <LOOPBACK,UP,LOWER_UP> mtu 65536 qdisc noqueue state UNKNOWN qlen 1000\\    link/loopback 00:00:00:00:00:00 brd 00:00:00:00:00:00
2: eth0@if998: <BROADCAST,MULTICAST,UP,LOWER_UP,M-DOWN> mtu 1500 qdisc noqueue state UP \\    link/ether 6a:60:ce:b7:98:97 brd ff:ff:ff:ff:ff:ff"
     )

(defn- parse-ip-route-default
  "Parses `ip route show default` output, returns the first default
  route as {:address :interface}."
  [s]
  (when (present? s)
    (some (fn [line]
            (when-let [[_ gateway dev]
                       (re-matches #"\s*default\s+via\s+(\S+)\s+dev\s+(\S+).*" line)]
              {:address gateway :interface dev}))
          (str/split-lines s))))

;; ------------------------------------------------------------------
;; ifconfig parsing (macOS / BSD / linux net-tools fallback)

(defn- ifconfig-blocks
  "Splits ifconfig -a output into a list of [name header-line body-lines]."
  [s]
  (when (present? s)
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
            (recur (rest lines) cur-name cur-header (conj cur-body (str/trim line)) acc)))))))

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

(defn- parse-angled-flags
  "Parses a token like `flags=1008843<UP,BROADCAST,RUNNING>` or
  `options=880028<VLAN_MTU,JUMBO_MTU>` returning
  `{:value 1008843 :flags #{:UP :BROADCAST :RUNNING}}` or nil."
  [s]
  (when-let [[_ value-str flag-str] (re-find #"=(\d+)<([^>]*)>" s)]
    {:value (Integer/parseUnsignedInt value-str 10)
     :flags (if (present? flag-str)
              (->> (str/split flag-str #",")
                   (map keyword )
                   set)
              #{})}))

(defn- parse-ifconfig-block [name header body]
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


(defn- parse-ifconfig
  "Parses `ifconfig -a` output into a seq of interface maps."
  [s]
  (when (present? s)
    (->> (ifconfig-blocks s)
         (map (fn [[name header body]]
                (parse-ifconfig-block name header body)))
         (filter :name))))

(defn- ipv4-octets [^String ip]
  (mapv #(Integer/parseInt %) (str/split ip #"\.")))

(defn- ipv4-in-subnet?
  "Returns true if `ip` belongs to `network`/`netmask`."
  [ip network netmask]
  (let [mask (ipv4-octets netmask)
        ip-b (ipv4-octets ip)
        net-b (ipv4-octets network)]
    (= (mapv bit-and ip-b mask)
       (mapv bit-and net-b mask))))

(defn- compress-ipv6
  "Compresses an expanded IPv6 address (e.g. `0000:...:0001`) to its
  canonical short form (e.g. `::1`)."
  [addr]
  (let [groups (->> (str/split addr #":")
                     (mapv #(format "%x" (BigInteger. ^String % 16))))
        ;; find the longest run of "0" groups to collapse into "::"
        runs (loop [i 0 acc [] cur-start nil cur-len 0]
               (if (= i (count groups))
                 (if (pos? cur-len)
                   (conj acc [cur-start cur-len])
                   acc)
                 (let [g (groups i)
                       zero? (= g "0")]
                   (cond
                     (and zero? (nil? cur-start)) (recur (inc i) acc i 1)
                     zero?                     (recur (inc i) acc cur-start (inc cur-len))
                     :else                     (recur (inc i)
                                                  (if (pos? cur-len)
                                                    (conj acc [cur-start cur-len])
                                                    acc)
                                                  nil 0)))))
        long-runs (filter #(>= (second %) 2) runs)
        longest (when (seq long-runs)
                  (apply max-key second long-runs))]
    (if longest
      (let [[start len] longest
            before (subvec groups 0 start)
            after  (subvec groups (+ start len) (count groups))]
        (str (str/join ":" before)
             "::"
             (str/join ":" after)))
      (str/join ":" groups))))

(defn- parse-ubuntu-proc
  "Parses the `===ubuntu-proc===` pipe-delimited output (produced by
  network/gather.sh) into a seq of interface maps, mirroring the shape
  returned by `parse-ifconfig`.

  Record types handled:
    LINK|<iface>|mac=<mac>|mtu=<mtu>|state=<state>|carrier=<carrier>
    ROUTE|<iface>|network=<dest>|netmask=<mask>
    ADDR4|<ip>                       ;; local IPv4, interface unbound
    INET6|<iface>|<addr>|<prefix>"
  [proc-data]
  (when (present? proc-data)
    (let [lines (->> (str/split-lines proc-data)
                     (map str/trim)
                     (remove str/blank?))
          records (for [line lines]
                    (str/split line #"\|"))
          links (for [r records :when (= (first r) "LINK")]
                  (let [name (second r)
                        m (utils/parse-kv (str/join "\n" (nnext r)))]
                    {:name       name
                     :mac        (some-> (:mac m) utils/normalize-mac)
                     :mtu        (some-> (:mtu m) edn/read-string)
                     :status     (some-> (:state m) utils/keywordize-status)
                     :loopback?  (or (= name "lo") (= name "lo0"))
                     :routes     []
                     :ipv4       []
                     :ipv6       []}))
          attach-route (fn [ifs r]
                         (let [[_ iface & kvs] r
                               m (utils/parse-kv (str/join "\n" kvs))
                               route {:network (:network m) :netmask (:netmask m)}]
                           (mapv #(if (= (:name %) iface)
                                    (update % :routes conj route)
                                    %)
                                 ifs)))
          links (reduce (fn [ifs r]
                          (if (= (first r) "ROUTE")
                            (attach-route ifs r)
                            ifs))
                        links records)
          ;; INET6 entries attach directly to their named interface.
          links (reduce (fn [ifs r]
                          (if (not= (first r) "INET6")
                            ifs
                            (let [[_ iface addr prefix] r]
                              (mapv #(if (= (:name %) iface)
                                       (update % :ipv6 conj
                                               {:address (compress-ipv6 addr)
                                                :prefix (edn/read-string prefix)})
                                       %)
                                    ifs))))
                        links records)
          ;; ADDR4 entries are interface-unbound; assign each to the interface
          ;; whose route subnet contains it, falling back to loopback for 127.x.
          links (reduce (fn [ifs r]
                          (if (not= (first r) "ADDR4")
                            ifs
                            (let [addr (second r)
                                  lo?  (str/starts-with? addr "127.")
                                  matching-route (fn [i]
                                                   (->> (:routes i)
                                                        (filter #(ipv4-in-subnet? addr
                                                                                   (:network %)
                                                                                   (:netmask %)))
                                                        (sort-by (fn [r]
                                                                   (utils/parse-prefix (:netmask r)))
                                                                 >)
                                                        first))
                                  in-routes? (fn [i] (some? (matching-route i)))
                                  iface (or (some #(when (and (not lo?) (in-routes? %)) %) ifs)
                                            (some #(when (and lo? (:loopback? %)) %) ifs))]
                              (if iface
                                (let [prefix (some-> (matching-route iface)
                                                     :netmask
                                                     utils/parse-prefix)]
                                  (mapv #(if (= (:name %) (:name iface))
                                           (update % :ipv4 conj (cond-> {:address addr}
                                                                        prefix (assoc :prefix prefix)))
                                           %)
                                        ifs))
                                ifs))))
                        links records)]
      (->> links
           (map #(dissoc % :routes))
           (filter :name)))))

#_ (parse-ubuntu-proc "
LINK|eth0|mac=52:0a:b9:5e:8a:1d|mtu=1500|state=up|carrier=1
LINK|lo|mac=00:00:00:00:00:00|mtu=65536|state=unknown|carrier=1
ROUTE|eth0|network=0.0.0.0|netmask=0.0.0.0
ROUTE|eth0|network=172.17.0.0|netmask=255.255.0.0
ADDR4|127.0.0.0
ADDR4|127.0.0.1
ADDR4|172.17.0.3
INET6|lo|0000:0000:0000:0000:0000:0000:0000:0001|128
")

;; =>
#_ ({:name "eth0",
  :mac "52:0a:b9:5e:8a:1d",
  :mtu 1500,
  :status :up,
  :loopback? false,
  :ipv4 [{:address "172.17.0.3" :prefix 16}],
  :ipv6 []}
 {:name "lo",
  :mac "00:00:00:00:00:00",
  :mtu 65536,
  :status :unknown,
  :loopback? true,
  :ipv4 [{:address "127.0.0.0" :prefix 8} {:address "127.0.0.1" :prefix 8}],
  :ipv6 [{:address "::1", :prefix 128}]})

(defn- hex-le->ipv4
  "Converts a little-endian hex string from /proc/net/route (e.g.
  `000011AC`) to dotted-decimal (`172.17.0.0`)."
  [h]
  (when (present? h)
    (->> (re-seq #"[0-9a-fA-F]{2}" h)
         (map #(Integer/parseInt % 16))
         reverse
         (str/join "."))))

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
  (when (present? s)
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
           :ipv6      []})))))

(defn- parse-proc-net-route
  "Parses /proc/net/route into a list of route maps:
  {:iface :network :netmask :prefix :gateway}. Destination and mask are
  stored little-endian hex in the file and are decoded to dotted-decimal."
  [s]
  (when (present? s)
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
                        :gateway (hex-le->ipv4 gw)}))))))))

(defn- parse-proc-net-fib-trie
  "Parses /proc/net/fib_trie into a list of {:address :prefix :scope
  :type} entries. The Main and Local sections duplicate the same data
  so the result is deduplicated."
  [s]
  (when (present? s)
    (let [re #"\|--\s+(\d+\.\d+\.\d+\.\d+)\s*\n\s+/(\d+)\s+(\S+)\s+(\S+)"
          entries (for [[_ addr prefix scope type] (re-seq re s)]
                    {:address addr
                     :prefix  (edn/read-string prefix)
                     :scope   scope
                     :type    type})]
      (distinct entries))))

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
  (when (present? sys-class-net)
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
           (filter :name)))))

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





;; ------------------------------------------------------------------
;; netstat -rn default route parsing (fallback for non-iproute2)

(def route-re #"^(?:0\.0\.0\.0|default)\s+(\S+).*\s(\S+)\s*$")

(defn- parse-netstat-default-route
  "Parses `netstat -rn` output for the default route. Returns
  {:address :interface}."
  [s]
  (some (fn [line]
          (when-let [[_ gateway iface] (re-find route-re line)]
            {:gateway gateway :iface iface}))
        (str/split-lines s)))

;; linux
#_(parse-netstat-default-route
  "Kernel IP routing table
Destination     Gateway         Genmask         Flags   MSS Window  irtt Iface
0.0.0.0         192.168.92.1    0.0.0.0         UG        0 0          0 eno1
172.17.0.0      0.0.0.0         255.255.0.0     U         0 0          0 docker0
192.168.92.0    0.0.0.0         255.255.255.0   U         0 0          0 eno1"
  )

;; freebsd
#_(parse-netstat-default-route
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
ff02::/16                         link#2                        URS             lo0"
  )

;; ------------------------------------------------------------------
;; resolv.conf parsing

(defn- parse-resolv-conf
  "Parses /etc/resolv.conf into {:nameservers [...] :search [...]}."
  [s]
  (->> (str/split-lines s)
       (map str/trim)
       (remove #(str/starts-with? % "#"))
       (remove #(str/starts-with? % ";"))
       (reduce (fn [{:keys [nameservers search] :as acc} line]
                 (cond
                   (str/starts-with? line "nameserver")
                   (let [v (str/trim (subs line (count "nameserver")))]
                     (if (seq v)
                       (update acc :nameservers conj v)
                       acc))

                   (str/starts-with? line "search")
                   (let [v (str/split (str/trim (subs line (count "search"))) #"\s+")]
                     (assoc acc :search v))

                   (str/starts-with? line "domain")
                   (let [v (str/trim (subs line (count "domain")))]
                     (assoc acc :search [v]))

                   :else acc))
               {:nameservers [] :search []})))

#_ (parse-resolv-conf
     "# A comment
domain overridden.com
search mydomain.com sub.mydomain.com
nameserver 192.168.12.2
nameserver 192.168.12.3
options timeout:2
"
     )

;; ------------------------------------------------------------------
;; scutil --dns parsing (macOS)

(defn- parse-scutil-dns
  "Parses `scutil --dns` output, returning the union of nameservers and
  search domains across resolvers."
  [s]
  (when (present? s)
    (->> (str/split-lines s)
         (reduce (fn [{:keys [nameservers search] :as acc} line]
                   (cond
                     (str/includes? line "nameserver[")
                     (if-let [v (some-> (re-find #":\s*(\S+)" line) second)]
                       (update acc :nameservers conj v)
                       acc)

                     (str/includes? line "search domain[")
                     (if-let [v (some-> (re-find #":\s*(\S+)" line) second)]
                       (update acc :search conj v)
                       acc)

                     :else acc))
                 {:nameservers [] :search []}))))

;; ------------------------------------------------------------------
;; assembling the posix result

(defn- process-unix [sections]
  (let [hostname (str/trim (get sections "hostname"))
        ip-addrs (parse-ip-o-addr (get sections "ip-addr"))
        ip-links (parse-ip-o-link (get sections "ip-link"))
        ;; if ifconfig is present we use it both for link info and
        ;; addresses, only when ip -o data is missing
        ifcfg (parse-ifconfig (get sections "ifconfig"))]
    (cond
      (and (seq ip-links) (seq ip-addrs))
      ;; iproute2 path
      {:hostname hostname
       :interfaces
       (for [[name link] (sort-by first ip-links)]
         (merge {:ipv4 [] :ipv6 [] :loopback false :status :unknown}
                link
                (select-keys (get ip-addrs name) [:ipv4 :ipv6])))
       :default-gateway (or (parse-ip-route-default (get sections "ip-route"))
                            (parse-netstat-default-route (get sections "netstat-route")))
       :dns (or (parse-resolv-conf (get sections "resolv"))
                (parse-scutil-dns (get sections "scutil-dns")))}

      (seq ifcfg)
      ;; ifconfig fallback (macOS, BSD, linux without iproute2)
      {:hostname hostname
       :interfaces (seq ifcfg)
       :default-gateway (parse-netstat-default-route (get sections "netstat-route"))
       :dns (or (parse-resolv-conf (get sections "resolv"))
                (parse-scutil-dns (get sections "scutil-dns")))}

      :else
      {:hostname hostname
       :interfaces []
       :default-gateway nil
       :dns (or (parse-resolv-conf (get sections "resolv"))
                (parse-scutil-dns (get sections "scutil-dns")))})))

;; ------------------------------------------------------------------
;; windows parsing

(defn- parse-pipe-rows
  "Splits content into seqs of fields split on `|`. Skips blank lines."
  [s]
  (when (present? s)
    (->> (str/split-lines s)
         (map str/trim)
         (filter seq)
         (map #(str/split % #"\|" -1))
         (filter seq))))

(defn- parse-powershell [sections]
  (let [hostname (str/trim (get sections "hostname"))
        interfaces (->> (parse-pipe-rows (get sections "ipinterface"))
                        (map (fn [[alias _idx family state mtu]]
                               (when (present? alias)
                                 [alias {:name alias
                                         :status (utils/keywordize-status state)
                                         :mtu (edn/read-string mtu)
                                         :ipv4 []
                                         :ipv6 []
                                         :loopback false}])))
                        (filter first)
                        (into {}))
        adapters (->> (parse-pipe-rows (get sections "adapter"))
                       (map (fn [[name mac status mtu]]
                              (when (present? name)
                                [name {:mac (utils/normalize-mac mac)
                                       :status (utils/keywordize-status status)
                                       :mtu (edn/read-string mtu)}])))
                       (filter first)
                       (into {}))
        addresses (->> (parse-pipe-rows (get sections "addresses"))
                       (keep (fn [[alias ip family prefix]]
                               (when (and (present? alias) (present? ip))
                                 [alias {:family (if (= family "IPv6") :ipv6 :ipv4)
                                         :address ip
                                         :prefix (edn/read-string prefix)}])))
                       (reduce (fn [m [alias a]]
                                 (update-in m [alias (:family a)] (fnil conj [])
                                            (select-keys a [:address :prefix])))
                               {}))
        route (->> (parse-pipe-rows (get sections "route"))
                   (filter #(and (>= (count %) 2) (present? (second %))))
                   first)
        dns-rows (parse-pipe-rows (get sections "dns"))
        nameservers (->> dns-rows
                         (filter #(and (>= (count %) 3)
                                       (= (second %) "IPv4")
                                       (present? (nth % 2))))
                         first
                         (#(when % (str/split (nth % 2) #","))))
        all-names (set (concat (keys interfaces)
                                (keys adapters)
                                (keys addresses)))]
    {:hostname hostname
     :interfaces
     (for [name (sort all-names)]
       (let [base (merge {:name name :ipv4 [] :ipv6 [] :loopback false :status :unknown}
                         (get interfaces name)
                         (get adapters name))
             addrs (get addresses name)
             base (if (seq addrs)
                    (-> base
                        (assoc :ipv4 (vec (:ipv4 addrs [])))
                        (assoc :ipv6 (vec (:ipv6 addrs []))))
                    base)
             loopback? (or (str/includes? (str/lower-case name) "loopback")
                           (some #(str/starts-with? (:address %) "127.")
                                 (:ipv4 base)))]
         (assoc base :loopback (boolean loopback?))))
     :default-gateway (when route
                        {:address (second route)
                         :interface (first route)})
     :dns {:nameservers (or nameservers [])
           :search []}}))

(defn- parse-ipconfig
  "Parses `ipconfig /all` (cmd-exe) into a partial structure:
  {:hostname ... :interfaces [...] :dns ...} extracting per-interface
  mac, ipv4, ipv6, default-gateway and dns."
  [s]
  (when (present? s)
    (let [lines (map str/trim (str/split-lines s))
          hostname (some #(some-> (re-find #"(?i)Host Name[^:]*:\s*(\S+)" %) second) lines)
          dns-suffix (some #(some-> (re-find #"(?i)Primary DNS Suffix[^:]*:\s*(\S+)" %) second) lines)
          bare-ip? #(re-matches #"[0-9a-fA-F:.]+" %)]
      (loop [lines lines
             cur nil
             acc []]
        (cond
          (empty? lines)
          (let [acc (if cur (conj acc cur) acc)
                ifs (reverse (filter :name acc))
                gw-iface (some #(when (:default-gateway %) %) ifs)
                dns (or (some :dns ifs)
                        (when (present? dns-suffix)
                          {:nameservers [] :search [dns-suffix]}))]
            {:hostname hostname
             :interfaces (for [i ifs]
                           (-> i
                               (dissoc :default-gateway :dns)
                               (assoc :status (or (:status i) :unknown))))
             :default-gateway (when gw-iface
                                {:address (get-in gw-iface [:default-gateway :address])
                                 :interface (:name gw-iface)})
             :dns dns})

          :else
          (let [line (first lines)]
            (cond
              (re-matches #"(?i).*adapter\s+.+?:.*" line)
              (let [name (second (re-find #"(?i)adapter\s+(.+?):" line))]
                (recur (rest lines)
                       {:name name :ipv4 [] :ipv6 []}
                       (if cur (conj acc cur) acc)))

              (nil? cur)
              (recur (rest lines) nil acc)

              ;; continuation of a DNS Servers list: a bare ip address
              (and (:dns cur) (bare-ip? line))
              (recur (rest lines)
                     (update-in cur [:dns :nameservers] conj line)
                     acc)

              :else
              (recur (rest lines)
                     (cond-> cur
                             (and (not (:mac cur))
                                  (re-matches #"(?i).*Physical Address.*:\s*([\w-]+)" line))
                             (assoc :mac (utils/normalize-mac (second (re-find #"(?i):\s*([\w-]+)" line))))

                             (re-matches #"(?i).*IPv4 Address.*:\s*(\S+)" line)
                             (assoc :ipv4 [{:address (str/replace (second (re-find #"(?i):\s*(\S+)" line)) #"\(.*" "")
                                            :prefix 24}])

                             (re-matches #"(?i).*IPv6 Address.*:\s*(\S+)" line)
                             (assoc :ipv6 [{:address (str/replace (second (re-find #"(?i):\s*(\S+)" line)) #"\(.*" "")
                                            :prefix 64}])

                             (re-matches #"(?i).*Default Gateway.*:\s*(\S+)" line)
                             (assoc :default-gateway {:address (second (re-find #"(?i):\s*(\S+)" line))})

                             (re-matches #"(?i).*DNS Servers.*:\s*(\S+)" line)
                             (assoc :dns {:nameservers [(second (re-find #"(?i):\s*(\S+)" line))]
                                          :search (if (present? dns-suffix) [dns-suffix] [])}))
                     acc))))))))



(defn- parse-route-print-default
  "Parses `route print 0.0.0.0` for the default route (0.0.0.0 gateway)."
  [s]
  (when (present? s)
    (some (fn [line]
            (when-let [[_ gw _mask iface]
                       (re-matches #"\s*0\.0\.0\.0\s+(\S+)\s+(\S+)\s+(\S+)\s+\S+" line)]
              (when (not= gw "0.0.0.0")
                {:address gw :interface iface})))
          (str/split-lines s))))

(defn- process-cmd-exe [sections]
  (let [parsed (or (parse-ipconfig (get sections "ipconfig"))
                   {:hostname nil :interfaces [] :default-gateway nil :dns nil})]
    (-> parsed
        (assoc :hostname (or (:hostname parsed)
                            (str/trim (get sections "hostname"))))
        (assoc :default-gateway (or (:default-gateway parsed)
                                    (parse-route-print-default
                                     (get sections "route-print")))))))

;; ------------------------------------------------------------------
;; entry point

(defn determine-network [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (get gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (println out)
    (assert (zero? exit) (str "network determination script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (condp = shell-type
        :powershell (parse-powershell sections)
        :cmd-exe    (process-cmd-exe sections)
        (process-unix sections)))))
