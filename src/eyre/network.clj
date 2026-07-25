(ns eyre.network
  (:require [clojure.edn :as edn]
            [clojure.string :as str]
            [eyre.network-parse :as network-parse]
            [eyre.utils :as utils :refer [embed newlines]]
            [medley.core :as medley]))

;; Gather facts about a system's network configuration.
;; `gather-network` takes a hashmap with
;; :exec (an executor function) and :shell (the detected shell map
;; from `eyre.shell/gather-shell`).
;;
;;   {:hostname      "host"
;;    :interfaces    {"eth0" {:name     "eth0"
;;                             :mac      "aa:bb:cc:dd:ee:ff"
;;                             :mtu      1500
;;                             :status   :up            ;; :up / :down / :unknown
;;                             :loopback? false
;;                             :ipv4     [{:address "10.0.0.5" :prefix 24}]
;;                             :ipv6     [{:address "fe80::1"   :prefix 64}]}}
;;    :default-gateway {:address "10.0.0.1" :interface "eth0"}
;;    :dns             {:nameservers ["8.8.8.8" "8.8.4.4"]
;;                      :search      ["example.com"]}}

(defn- interfaces->map
  "Converts a sequence of interface maps into a map keyed by interface
  name, ensuring each interface map contains a :name key.

  Interfaces with a missing or blank name default to `unknown`.  If there
  are multiple such interfaces, they are disambiguated as
  `unknown`, `unknown(2)`, `unknown(3)`, etc."
  [interfaces]
  (first
    (reduce (fn [[m counts] iface]
              (let [base (if (seq (:name iface)) (:name iface) "unknown")
                    n (get counts base 0)
                    key (if (zero? n) base (str base "(" (inc n) ")"))]
                [(assoc m key (if (contains? iface :name)
                                iface
                                (assoc iface :name key)))
                 (assoc counts base (inc n))]))
            [{} {}]
            interfaces)))

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

(defn- process-unix [sections]
  (let [{:strs [hostname proc-sys-kernel-hostname
                ip-addr ip-link ifconfig ip-route netstat-route
                resolv scutil-dns
                sys-class-net proc-net-route proc-net-fib-trie]} sections]
    {:hostname (str/trim (or hostname proc-sys-kernel-hostname))

     :interfaces
     (cond
       ;; iproute2
       ip-link
       (let [ip-links (network-parse/parse-ip-o-link ip-link)
             ip-addrs (network-parse/parse-ip-o-addr ip-addr)]
         (interfaces->map
           (for [[name link] (sort-by first ip-links)]
             (-> (merge {:ipv4 [] :ipv6 [] :loopback? false :status :unknown}
                        link
                        (select-keys (get ip-addrs name) [:ipv4 :ipv6]))
                 (assoc :name name)))))

       ;; ifconfig
       ifconfig
       (interfaces->map (network-parse/parse-ifconfig ifconfig))

       ;; proc fs
       (and sys-class-net proc-net-route proc-net-fib-trie)
       (interfaces->map
         (network-parse/parse-proc-network-info
           sys-class-net
           proc-net-route
           proc-net-fib-trie))

       :else
       {})

     :default-gateway
     (cond
       ip-route (network-parse/parse-ip-route-default ip-route)
       netstat-route (network-parse/parse-netstat-default-route netstat-route)
       proc-net-route (network-parse/parse-proc-net-default-route proc-net-route))

     :dns
     (cond
       resolv (network-parse/parse-resolv-conf resolv)
       scutil-dns (network-parse/parse-scutil-dns scutil-dns))}))

;;
;; windows parsing
;;

(defn- parse-powershell [{:strs [hostname ipinterface adapter
                                 addresses route dns] :as _sections}]
  (let [interfaces (->> (network-parse/parse-pipe-rows ipinterface)
                        (map (fn [[iname _idx _family state mtu]]
                               [iname {:name iname
                                       :status (utils/keywordize-status state)
                                       :mtu (edn/read-string mtu)
                                       :ipv4 []
                                       :ipv6 []
                                       :loopback? false}]))
                        (into {}))
        adapters (->> (network-parse/parse-pipe-rows adapter)
                      (map (fn [[aname mac status mtu]]
                             [aname {:mac (utils/normalize-mac mac)
                                     :status (utils/keywordize-status status)
                                     :mtu (edn/read-string mtu)}]))
                      (into {}))
        addresses (->> (network-parse/parse-pipe-rows addresses)
                       (keep (fn [[iname ip family prefix]]
                               [iname {:family (if (= family "IPv6") :ipv6 :ipv4)
                                       :address ip
                                       :prefix (edn/read-string prefix)}]))
                       (reduce (fn [m [iname a]]
                                 (update-in m [iname (:family a)] (fnil conj [])
                                            (select-keys a [:address :prefix])))
                               {}))
        route (->> (network-parse/parse-pipe-rows route)
                   (filter #(>= (count %) 2))
                   first)
        nameservers (->> (network-parse/parse-pipe-rows dns)
                         (filter #(and (>= (count %) 3)
                                       ;; AddressFamily is a .NET enum printed
                                       ;; as a number: 2 = IPv4, 23 = IPv6.
                                       (= (second %) "2")
                                       (seq (nth % 2))))
                         (map #(str/split (nth % 2) #","))
                         first)
        all-names (set (concat
                         (keys interfaces)
                         (keys adapters)
                         (keys addresses)))]
    {:hostname (str/trim hostname)
     :interfaces
     (interfaces->map
       (for [name (sort all-names)]
         (let [addrs (addresses name)
               loopback? (or (str/includes? (str/lower-case name) "loopback")
                             (some #(str/starts-with? (:address %) "127.")
                                   (:ipv4 addrs)))]
           (-> (merge {:name name :ipv4 [] :ipv6 [] :loopback? false :status :unknown}
                      (interfaces name)
                      (adapters name))
               (assoc :loopback? (boolean loopback?))

               (cond->
                 (seq addrs)
                 (assoc :ipv4 (vec (:ipv4 addrs []))
                        :ipv6 (vec (:ipv6 addrs []))))))))
     :default-gateway (when route
                        {:address (second route)
                         :interface (first route)})
     :dns {:nameservers (or nameservers [])
           :search []}}))

(defn- parse-ipconfig
  "Parses `ipconfig /all` (cmd-exe) into a partial structure:
  {:hostname ... :interfaces {...} :dns ...} extracting per-interface
  mac, ipv4, ipv6, default-gateway and dns."
  [s]
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
                      {:nameservers [] :search [dns-suffix]})]
          {:hostname hostname
           :interfaces (interfaces->map
                         (for [i ifs]
                           (-> i
                               (dissoc :default-gateway :dns)
                               (assoc :status (or (:status i) :unknown)))))
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
                                        :search [dns-suffix]}))
                   acc)))))))



(defn- parse-route-print-default
  "Parses `route print 0.0.0.0` for the default route (0.0.0.0 gateway)."
  [s]
  (some (fn [line]
          (when-let [[_ gw _mask iface]
                     (re-matches #"\s*0\.0\.0\.0\s+(\S+)\s+(\S+)\s+(\S+)\s+\S+" line)]
            (when (not= gw "0.0.0.0")
              {:address gw :interface iface})))
        (str/split-lines s)))

(defn- process-cmd-exe [{:strs [netsh-interface-ipv4-show-interfaces
                                netsh-interface-ipv4-show-config
                                ipconfig route-print hostname]}]
  (let [intfs (network-parse/parse-netsh-ipv4-show-interfaces netsh-interface-ipv4-show-interfaces)
        configs (network-parse/parse-netsh-ipv4-show-config netsh-interface-ipv4-show-config)
        cfg-by-name (->> configs
                         (map (juxt :name identity))
                         (into {}))
        interfaces (for [{:keys [iname _index mtu status]} intfs]
                     (let [c (get cfg-by-name iname)]
                       {:name iname
                        :mtu mtu
                        :status status
                        :ipv4 (:ipv4 c [])
                        :ipv6 []
                        :loopback? (str/includes? (str/lower-case iname) "loopback")}))
        parsed (parse-ipconfig ipconfig)]
    (assoc parsed
           :hostname (or (:hostname parsed) (str/trim hostname))
           :interfaces (interfaces->map interfaces)
           :default-gateway (or (:default-gateway parsed)
                            (parse-route-print-default route-print)))))

(defn gather-network [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (get gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "network gathering script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (condp = shell-type
        :powershell (parse-powershell sections)
        :cmd-exe    (process-cmd-exe sections)
        (process-unix sections)))))
