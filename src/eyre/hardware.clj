(ns eyre.hardware
  (:require [clojure.edn :as edn]
            [clojure.set]
            [clojure.string :as str]
            [eyre.utils :as utils :refer [embed newlines]]))

(def posix-gather-script (embed "hardware/gather.sh"))
(def fish-gather-script (embed "hardware/gather.fish"))
(def nu-gather-script (embed "hardware/gather.nu"))
(def powershell-gather-script (embed "hardware/gather.ps1"))
(def cmd-gather-script (embed "hardware/gather.cmd"))

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

(defn- normalize-arch [arch]
  (let [a (str/trim (str/lower-case arch))]
    (case a
      "amd64" "x86_64"
      "i386"  "i386"
      "i686"  "i386"
      "arm64" "aarch64"
      a)))

;;
;; POSIX / Unix parsing
;;

(defn- parse-sysctl [s]
  (->> (str/split s newlines)
       (keep (fn [line]
               (when-let [[_ k v] (or (re-find #"^([^:=]+)\s*:\s*(.*)$" line)
                                      (re-find #"^([^:=]+)\s*=\s*(.*)$" line))]
                 [(str/trim k) (str/trim v)])))
       (into {})))

(defn- parse-mac-swap [sysctl-a]
  (let [features (some (fn [line]
                         (when (str/starts-with? line "vm.swapusage")
                           line))
                         (str/split sysctl-a newlines))]
    (when-let [[_ amt unit] (and features (re-find #"(?i)total\s*=\s*(\d+\.?\d*)\s*([KMGT])" features))]
      (let [val (Double/parseDouble amt)
            mult (case (str/upper-case unit)
                   "K" 1024
                   "M" (* 1024 1024)
                   "G" (* 1024 1024 1024)
                   "T" (* 1024 1024 1024 1024)
                   1)]
        (long (* val mult))))))

(defn- bsd-flags [sysctl-map]
  (cond-> #{}
    (= (get sysctl-map "hw.instruction_sse") "1") (conj "sse")
    (= (get sysctl-map "hw.instruction_sse2") "1") (conj "sse2")
    (= (get sysctl-map "hw.instruction_sse3") "1") (conj "sse3")
    (= (get sysctl-map "hw.instruction_avx") "1") (conj "avx")
    (= (get sysctl-map "hw.instruction_avx2") "1") (conj "avx2")))

(defn- parse-cpu-sysctl [sysctl-map]
  (let [cores (some-> (or (get sysctl-map "hw.ncpu")
                          (get sysctl-map "hw.ncpuonline")
                          (get sysctl-map "hw.physicalcpu"))
                      edn/read-string)
        model (or (get sysctl-map "machdep.cpu.brand_string")
                  (get sysctl-map "hw.model")
                  (get sysctl-map "hw.machine"))
        features (or (get sysctl-map "machdep.cpu.features")
                     (get sysctl-map "machdep.cpu.extfeatures")
                     "")
        flags (->> (str/split features #"\s+")
                   (map str/lower-case)
                   (filter seq)
                   set)
        flags (clojure.set/union flags (bsd-flags sysctl-map))]
    {:model model
     :cores cores
     :flags flags}))

(defn- count-processors [cpuinfo]
  (let [matches (re-seq #"(?im)^processor\s*:" cpuinfo)]
    (when (seq matches)
      (count matches))))

(defn- parse-cpu-linux [cpuinfo]
  (when (seq (some-> cpuinfo str/trim))
    (let [processors (count-processors cpuinfo)
          model (some-> (re-find #"(?im)^model name\s*:\s*(.*)$" cpuinfo) second str/trim)
          flags (some-> (re-find #"(?im)^flags\s*:\s*(.*)$" cpuinfo) second str/trim (str/split #"\s+"))
          flags-set (if flags (set (map str/lower-case flags)) #{})]
      {:model model
       :cores processors
       :flags flags-set})))

(defn- parse-meminfo-linux [meminfo]
  (let [mem-kb (some-> (re-find #"(?im)^MemTotal:\s*(\d+)" meminfo) second Long/parseLong)
        swap-kb (some-> (re-find #"(?im)^SwapTotal:\s*(\d+)" meminfo) second Long/parseLong)]
    (when (or mem-kb swap-kb)
      {:total (if mem-kb (* mem-kb 1024) 0)
       :swap (if swap-kb (* swap-kb 1024) 0)})))

(defn- parse-lsblk [lsblk-str]
  (when (seq lsblk-str)
    (let [lines (str/split lsblk-str newlines)
          headers (some-> (first lines) str/trim (str/split #"\s+"))]
      (when-let [name-idx (and headers (.indexOf headers "NAME"))]
        (when (>= name-idx 0)
          (let [size-idx (.indexOf headers "SIZE")
                rota-idx (.indexOf headers "ROTA")
                type-idx (.indexOf headers "TYPE")
                tran-idx (.indexOf headers "TRAN")]
            (keep (fn [line]
                    (let [parts (str/split (str/trim line) #"\s+")]
                      (when (and (> (count parts) name-idx)
                                 (or (neg? type-idx) (= (get parts type-idx) "disk")))
                        (let [name (get parts name-idx)
                              size (when (>= size-idx 0) (some-> (get parts size-idx) Long/parseLong))
                              rota (when (>= rota-idx 0) (get parts rota-idx))
                              tran (when (>= tran-idx 0) (some-> (get parts tran-idx) str/lower-case))
                              type (cond
                                     (= tran "nvme") :nvme
                                     (str/includes? (str/lower-case name) "nvme") :nvme
                                     (= rota "0") :ssd
                                     :else :hdd)]
                          (when (and (seq name) size)
                            {:name name
                             :size size
                             :type type})))))
                  (rest lines))))))))

(defn- parse-sys-block [sys-block-str]
  (when (seq sys-block-str)
    (let [lines (str/split sys-block-str newlines)
          data (reduce (fn [acc line]
                         (if-let [[_ dev file val] (re-find #"^/sys/block/([^/]+)/(size|queue/rotational):(\d+)" line)]
                           (assoc-in acc [dev (keyword (str/replace file "/" "-"))] (Long/parseLong val))
                           acc))
                       {}
                       lines)]
      (for [[dev info] data
            :when (not (str/starts-with? dev "loop"))]
        (let [size (some-> (or (:size info) (get info (keyword "size"))) (* 512))
              rota (or (:queue-rotational info) (get info (keyword "queue-rotational")))
              type (cond
                     (str/includes? dev "nvme") :nvme
                     (= rota 0) :ssd
                     :else :hdd)]
          {:name dev
           :size (or size 0)
           :type type})))))

(defn- parse-diskutil-info [content]
  (let [kv (utils/parse-kv-colon content)
        whole? (= (get kv :whole) "Yes")
        dev (get kv (keyword "device identifier"))
        size-str (or (get kv (keyword "disk size")) (get kv (keyword "total size")))
        size (when size-str
               (or (some-> (re-find #"\((\d+)\s+Bytes\)" size-str) second Long/parseLong)
                   (some-> (re-find #"(\d+)\s+Bytes" size-str) second Long/parseLong)))
        media-name (get kv (keyword "media name") "")
        protocol (get kv :protocol "")
        solid-state? (or (= (get kv (keyword "solid state")) "Yes")
                         (str/includes? (str/lower-case (get kv (keyword "medium type") "")) "solid state"))
        type (cond
               (or (str/includes? (str/lower-case media-name) "nvme")
                   (str/includes? (str/lower-case protocol) "nvme")
                   (str/includes? (str/lower-case protocol) "pci")) :nvme
               solid-state? :ssd
               :else :hdd)]
    (when (and whole? dev size)
      {:name dev
       :size size
       :type type})))

(defn- parse-geom-disk [geom-str]
  (when (seq geom-str)
    (let [blocks (str/split geom-str #"(?m)^Geom name:\s*")]
      (keep (fn [block]
              (let [lines (str/split-lines block)
                    name (str/trim (first lines))
                    kv (utils/parse-kv-colon block)
                    mediasize-str (get kv :mediasize)
                    size (when mediasize-str
                           (some-> (re-find #"^(\d+)" mediasize-str) second Long/parseLong))
                    rotationrate (get kv :rotationrate)
                    descr (get kv :descr "")
                    type (cond
                           (str/includes? (str/lower-case name) "nvme") :nvme
                           (str/includes? (str/lower-case descr) "nvme") :nvme
                           (= rotationrate "0") :ssd
                           :else :hdd)]
                (when (and (seq name) size)
                  {:name name
                   :size size
                   :type type})))
            blocks))))

(defn- detect-virt-posix [{:strs [systemd-detect-virt sys-class-dmi kern-vm-guest cgroup dockerenv]} sysctl-map]
  (let [systemd-virt (some-> systemd-detect-virt str/trim str/lower-case)
        dmi (utils/parse-kv-colon (or sys-class-dmi ""))
        sys-vendor (some-> (get dmi :sys_vendor) str/lower-case)
        product-name (some-> (get dmi :product_name) str/lower-case)
        kern-vm-guest (some-> kern-vm-guest str/trim str/lower-case)
        cgroup (or cgroup "")
        dockerenv? (= "exists" (some-> dockerenv str/trim))
        sysctl-model (some-> (get sysctl-map "hw.model") str/lower-case)

        [is-virt? type]
        (cond
          (or dockerenv?
              (str/includes? cgroup "docker")
              (str/includes? cgroup "containerd")
              (= systemd-virt "docker"))
          [true :docker]

          (and (seq systemd-virt) (not= systemd-virt "none"))
          [true (keyword systemd-virt)]

          (and (seq kern-vm-guest) (not= kern-vm-guest "none"))
          [true (keyword kern-vm-guest)]

          (or (some-> sys-vendor (str/includes? "qemu"))
              (some-> product-name (str/includes? "qemu")))
          [true :qemu]

          (or (some-> sys-vendor (str/includes? "kvm"))
              (some-> product-name (str/includes? "kvm")))
          [true :kvm]

          (or (some-> sys-vendor (str/includes? "vmware"))
              (some-> product-name (str/includes? "vmware")))
          [true :vmware]

          (or (some-> sys-vendor (str/includes? "virtualbox"))
              (some-> product-name (str/includes? "virtualbox")))
          [true :virtualbox]

          (or (some-> sys-vendor (str/includes? "xen"))
              (some-> product-name (str/includes? "xen")))
          [true :xen]

          (or (some-> sys-vendor (str/includes? "amazon"))
              (some-> product-name (str/includes? "amazon")))
          [true :amazon]

          (or (some-> sys-vendor (str/includes? "microsoft"))
              (some-> product-name (str/includes? "hyper-v"))
              (some-> product-name (str/includes? "virtual machine")))
          [true :hyperv]

          (some-> sysctl-model (str/includes? "qemu"))
          [true :qemu]

          (some-> sysctl-model (str/includes? "virtualbox"))
          [true :virtualbox]

          (some-> sysctl-model (str/includes? "vmware"))
          [true :vmware]

          :else
          [false nil])]
    {:is-virtual? is-virt?
     :type type}))

(defn- process-unix [{:strs [sysctl-a cpuinfo uname meminfo lsblk sys-block geom-disk] :as sections}]
  (let [sysctl-map (parse-sysctl (or sysctl-a ""))
        cpu (or (parse-cpu-linux cpuinfo)
                (parse-cpu-sysctl sysctl-map))
        uname-arch (some-> uname str/trim str/lower-case)
        arch (normalize-arch (or (when (seq uname-arch) uname-arch)
                                 (some-> (get sysctl-map "hw.machine") str/lower-case)
                                 (when cpu (:architecture cpu))
                                 "x86_64"))
        cpu (assoc cpu :architecture arch)
        mem (or (parse-meminfo-linux (or meminfo ""))
                (let [total (some-> (or (get sysctl-map "hw.memsize")
                                        (get sysctl-map "hw.physmem")
                                        (get sysctl-map "hw.realmem"))
                                    Long/parseLong)]
                  {:total (or total 0)
                   :swap (or (parse-mac-swap sysctl-a) 0)}))
        disks (or (seq (parse-lsblk lsblk))
                  (seq (parse-sys-block sys-block))
                  (seq (keep (fn [[k v]]
                               (when (str/starts-with? k "diskutil-info-")
                                 (parse-diskutil-info v)))
                             sections))
                  (seq (parse-geom-disk geom-disk))
                  [])
        virt (detect-virt-posix sections sysctl-map)]
    {:cpu cpu
     :memory mem
     :disks (vec (sort-by :name disks))
     :virtualization virt}))

;;
;; Windows parsing
;;

(defn- parse-cpu-powershell [cpu-str]
  (when (seq cpu-str)
    (let [[name cores arch arch-code featureset] (str/split (str/trim cpu-str) #"\|")
          cores (some-> cores str/trim edn/read-string)
          arch (str/trim (or arch ""))
          normalized-arch (normalize-arch arch)
          featureset-int (try (Long/parseLong (str/trim (or featureset ""))) (catch Exception _ nil))
          flags (cond-> #{}
                  (and featureset-int (not= 0 (bit-and featureset-int (bit-shift-left 1 6)))) (conj "sse")
                  (and featureset-int (not= 0 (bit-and featureset-int (bit-shift-left 1 7)))) (conj "sse2")
                  (and featureset-int (not= 0 (bit-and featureset-int (bit-shift-left 1 8)))) (conj "sse3")
                  (and featureset-int (not= 0 (bit-and featureset-int (bit-shift-left 1 12)))) (conj "avx")
                  (and featureset-int (not= 0 (bit-and featureset-int (bit-shift-left 1 13)))) (conj "avx2"))
          flags (if (and (empty? flags) (= normalized-arch "x86_64")) #{"sse" "sse2"} flags)]
      {:model (str/trim (or name ""))
       :cores cores
       :architecture normalized-arch
       :flags flags})))

(defn- parse-memory-powershell [mem-str]
  (when (seq mem-str)
    (let [[total swap] (str/split (str/trim mem-str) #"\|")
          total (try (Long/parseLong (str/trim total)) (catch Exception _ 0))
          swap (try (Long/parseLong (str/trim swap)) (catch Exception _ 0))]
      {:total total
       :swap swap})))

(defn- parse-disks-powershell [disks-str]
  (when (seq disks-str)
    (->> (str/split disks-str newlines)
         (keep (fn [line]
                 (let [parts (str/split (str/trim line) #"\|")
                       [_ name size media-type] (map str/trim parts)]
                   (when (and (seq name) (seq size))
                     (let [size-bytes (try (Long/parseLong size) (catch Exception _ nil))
                           m-type (str/lower-case (or media-type ""))
                           type (cond
                                  (str/includes? m-type "ssd") :ssd
                                  (str/includes? m-type "unspecified") :ssd
                                  (str/includes? (str/lower-case name) "virtio") :ssd
                                  (str/includes? (str/lower-case name) "nvme") :nvme
                                  (str/includes? m-type "hdd") :hdd
                                  :else :hdd)]
                       (when (and name size-bytes)
                         {:name name
                          :size size-bytes
                          :type type}))))))
         vec)))

(defn- parse-virtualization-windows [virt-str]
  (if (seq virt-str)
    (let [[manuf model] (str/split (str/trim virt-str) #"\|")
          manuf (str/lower-case (or manuf ""))
          model (str/lower-case (or model ""))]
      (cond
        (or (str/includes? manuf "qemu") (str/includes? model "qemu"))
        {:is-virtual? true :type :qemu}

        (or (str/includes? manuf "vmware") (str/includes? model "vmware"))
        {:is-virtual? true :type :vmware}

        (or (str/includes? manuf "virtualbox") (str/includes? model "virtualbox"))
        {:is-virtual? true :type :virtualbox}

        (or (str/includes? manuf "xen") (str/includes? model "xen"))
        {:is-virtual? true :type :xen}

        (or (str/includes? manuf "microsoft") (str/includes? model "virtual machine"))
        {:is-virtual? true :type :hyperv}

        :else
        {:is-virtual? false :type nil}))
    {:is-virtual? false :type nil}))

(defn- parse-powershell [{:strs [cpu memory disks virtualization]}]
  (let [cpu (parse-cpu-powershell cpu)
        memory (parse-memory-powershell memory)
        disks (parse-disks-powershell disks)
        virt (parse-virtualization-windows virtualization)]
    {:cpu cpu
     :memory memory
     :disks (or disks [])
     :virtualization virt}))

(defn- parse-wmic-records [s]
  (assert s)
  (->> (str/split s newlines)
       (map str/trim)
       (filter seq)
       (reduce (fn [acc line]
                 (if-let [[_ k v] (re-find #"^([^=]+)=(.*)$" line)]
                   (let [k-kw (keyword (str/lower-case (str/trim k)))
                         v-str (str/trim v)
                         last-rec (last acc)]
                     (if (and last-rec (contains? last-rec k-kw))
                       (conj acc {k-kw v-str})
                       (if (seq acc)
                         (update acc (dec (count acc)) assoc k-kw v-str)
                         [{k-kw v-str}])))
                   acc))
               [])))

(defn- parse-cmd-cpu [{:strs [cpu]}]
  (let [kv (utils/parse-kv (or cpu ""))
        name (:name kv)
        cores (some-> (:numberofcores kv) edn/read-string)
        proc-arch (get kv (keyword "processor_architecture"))
        normalized-arch (normalize-arch proc-arch)
        flags (if (= normalized-arch "x86_64") #{"sse" "sse2"} #{})]
    {:model (or name "")
     :cores cores
     :architecture normalized-arch
     :flags flags}))

(defn- parse-cmd-memory [{:strs [memory]}]
  (let [kv (utils/parse-kv (or memory ""))
        total (try (Long/parseLong (or (:totalphysicalmemory kv) "0")) (catch Exception _ 0))
        swap-mb (try (Long/parseLong (or (:allocatedbasesize kv) "0")) (catch Exception _ 0))]
    {:total total
     :swap (* swap-mb 1024 1024)}))

(defn- parse-disks-cmd [disks-str]
  (assert disks-str)
  (keep (fn [rec]
          (let [name (:model rec)
                size-bytes (try (Long/parseLong (:size rec)) (catch Exception _ nil))
                iface (:interfacetype rec "")
                type (cond
                       (str/includes? (str/lower-case (or name "")) "nvme") :nvme
                       (str/includes? (str/lower-case iface) "nvme") :nvme
                       (str/includes? (str/lower-case (or name "")) "virtio") :ssd
                       :else :hdd)]
            (when (and name size-bytes)
              {:name name
               :size size-bytes
               :type type})))
        (parse-wmic-records disks-str)))

(defn- parse-virtualization-cmd [{:strs [virtualization]}]
  (let [kv (utils/parse-kv (or virtualization ""))
        manuf (str/lower-case (or (:manufacturer kv) ""))
        model (str/lower-case (or (:model kv) ""))]
    (cond
      (or (str/includes? manuf "qemu") (str/includes? model "qemu"))
      {:is-virtual? true :type :qemu}

      (or (str/includes? manuf "vmware") (str/includes? model "vmware"))
      {:is-virtual? true :type :vmware}

      (or (str/includes? manuf "virtualbox") (str/includes? model "virtualbox"))
      {:is-virtual? true :type :virtualbox}

      (or (str/includes? manuf "xen") (str/includes? model "xen"))
      {:is-virtual? true :type :xen}

      (or (str/includes? manuf "microsoft") (str/includes? model "virtual machine"))
      {:is-virtual? true :type :hyperv}

      :else
      {:is-virtual? false :type nil})))

(defn- process-cmd-exe [{:strs [disks] :as sections}]
  (let [cpu (parse-cmd-cpu sections)
        memory (parse-cmd-memory sections)
        disks (vec (or (parse-disks-cmd disks) []))
        virt (parse-virtualization-cmd sections)]
    {:cpu cpu
     :memory memory
     :disks disks
     :virtualization virt}))

(defn determine-hardware [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        script (or (get gather-scripts shell-type) posix-gather-script)
        {:keys [exit out err]} (exec script)]
    (assert (zero? exit) (str "hardware determination script exited non zero: " exit " " err))
    (let [sections (utils/parse-sections out)]
      (condp = shell-type
        :powershell (parse-powershell sections)
        :cmd-exe    (process-cmd-exe sections)
        (process-unix sections)))))
