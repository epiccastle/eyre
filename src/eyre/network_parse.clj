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
                       [_ mtu] (re-find #"mtu\s+(\d+)" rest)
                       [_ state] (re-find #"state\s+(\S+)" rest)
                       [_ mac] (re-find #"link/\w+\s+([0-9a-fA-F:]+)" rest)
                       mac (utils/normalize-mac mac)
                       loopback (or (contains? flags-set "LOOPBACK")
                                    (= real-ifname "lo"))
                       link-info (cond-> {:mac mac
                                          :mtu (edn/read-string mtu)
                                          :status (utils/keywordize-status state)
                                          :loopback loopback}
                                         peer-index (assoc :peer-index peer-index))]
                   [real-ifname link-info]))))
       (into {})))

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
;; ifconfig
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
        options (->> body
                     (filter #(and (str/includes? % "options=")
                                   (not (str/includes? % "nd6"))))
                     (map parse-angled-flags)
                     first)
        nd6-options (->> body
                         (filter #(str/includes? % "nd6 options="))
                         (map parse-angled-flags)
                         first)
        [_ mtu] (re-find #"mtu\s+(\d+)" header)
        loopback? (or (str/includes? header "LOOPBACK")
                      (= name "lo")
                      (= name "lo0"))
        mac (or (some->> body
                         (some #(re-find #"(?:ether|link/ether|HWaddr)\s+([0-9a-fA-F:]+)" %))
                         second
                         utils/normalize-mac)
                (when loopback? "00:00:00:00:00:00"))
        status-line (->> body (filter #(str/includes? % "status:")) first)
        status (if status-line
                 (-> status-line
                     (str/split #":" 2)
                     second
                     str/trim
                     utils/keywordize-status)
                 (if (re-find #"\WUP\W" header) :up :down))
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
    {:ipv4 (mapv #(dissoc % :family) (:ipv4 addrs))
     :ipv6 (mapv #(dissoc % :family) (:ipv6 addrs))
     :name name
     :mac mac
     :mtu (edn/read-string mtu)
     :status status
     :loopback? loopback?
     :flags flags
     :options options
     :nd6-options nd6-options}))

(defn parse-ifconfig
  "Parses `ifconfig -a` output into a seq of interface maps."
  [s]
  (->> (ifconfig-blocks s)
       (map (fn [block]
              (parse-ifconfig-block block)))))

;;
;; /proc and /sys kernel network structure parsing
;;

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
            type (-> m :type edn/read-string)]
        {:name      name
         :mac       (-> m :address utils/normalize-mac)
         :mtu       (-> m :mtu edn/read-string)
         :status    (-> m :operstate utils/keywordize-status)
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
               (let [[iface dest gw _ _ _ _ mask] (str/split line #"\t+")
                     network (hex-le->ipv4 dest)
                     netmask (hex-le->ipv4 mask)]
                 {:iface   iface
                  :network network
                  :netmask netmask
                  :prefix  (utils/parse-prefix netmask)
                  :gateway (hex-le->ipv4 gw)})))))

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
        local-addrs (->> fib-entries
                         (filter (fn [e]
                                   (and (= (:type e) "LOCAL")
                                        (= (:scope e) "host")
                                        (= (:prefix e) 32))))
                         (map :address))

        ;; subnet routes (prefix < 32) define the on-link networks
        subnets (->> fib-entries
                     (filter (fn [e]
                               (and (< (:prefix e) 32)
                                   (contains? #{"UNICAST" "LOCAL"} (:type e))))))

        route-by-net (->> routes
                          (map (fn [r]
                                 [[(:network r) (:prefix r)] (:iface r)]))
                          (into {}))

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
                         (filter (fn [{:keys [network netmask]}]
                                   (ipv4-in-subnet? addr network netmask)))
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
         (mapv (fn [i]
                 (if (:loopback? i)
                   (assoc i :ipv6 [{:address "::1" :prefix 128}])
                   i))))))

;;
;; netstat -rn default route
;;

(def route-re #"^(?:0\.0\.0\.0|default)\s+(\S+).*\s(\S+)\s*$")

(defn parse-netstat-default-route
  "Parses `netstat -rn` output for the default route. Returns
  hashmap with :address and :interface"
  [s]
  (some (fn [line]
          (when-let [[_ gateway iface] (re-find route-re line)]
            {:gateway gateway :interface iface}))
        (str/split-lines s)))

;;
;; resolv.conf
;;

(defn parse-resolv-conf
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

;;
;; scutil --dns (macos)
;;

(defn parse-scutil-dns
  "Parses `scutil --dns` output, returning the union of nameservers and
  search domains across resolvers."
  [s]
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
               {:nameservers [] :search []})))
