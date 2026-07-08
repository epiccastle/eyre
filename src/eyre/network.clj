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
