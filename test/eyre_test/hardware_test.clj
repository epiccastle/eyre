(ns eyre-test.hardware-test
  (:require [clojure.test :refer :all]
            [eyre.shell :as shell]
            [eyre.hardware :as hardware]
            [eyre-test.utils :as utils]
            [eyre-test.shell-test :as shell-test]
            [eyre-test.config :as config]
            [babashka.process :as process]))

(deftest determine-hardware-linux-test
  (let [mock-out "===uname===
x86_64
===cpuinfo===
processor       : 0
model name      : Intel(R) Core(TM) i7-8700 CPU @ 3.20GHz
flags           : fpu vme de sse sse2 sse3 avx avx2
===sys-class-dmi===
sys_vendor: QEMU
product_name: Standard PC (i440FX + PIIX, 1996)
===meminfo===
MemTotal:       16315264 kB
SwapTotal:       2097148 kB
===lsblk===
NAME SIZE ROTA TYPE TRAN
vda 34359738368 0 disk
vdb 137438953472 1 disk
===systemd-detect-virt===
qemu
===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (hardware/determine-hardware {:exec exec-fn :shell {:type :bash}})]
    (is (= "Intel(R) Core(TM) i7-8700 CPU @ 3.20GHz" (get-in res [:cpu :model])))
    (is (= 1 (get-in res [:cpu :cores])))
    (is (= "x86_64" (get-in res [:cpu :architecture])))
    (is (= #{"fpu" "vme" "de" "sse" "sse2" "sse3" "avx" "avx2"} (get-in res [:cpu :flags])))
    (is (= (* 16315264 1024) (get-in res [:memory :total])))
    (is (= (* 2097148 1024) (get-in res [:memory :swap])))
    (is (= [{:name "vda", :size 34359738368, :type :ssd}
            {:name "vdb", :size 137438953472, :type :hdd}]
           (:disks res)))
    (is (= {:is_virtual true :type :qemu} (:virtualization res)))))

(deftest determine-hardware-macos-test
  (let [mock-out "===sysctl-a===
machdep.cpu.brand_string: Intel(R) Core(TM) i5-7500 CPU @ 3.40GHz
hw.ncpu: 4
hw.model: VMware7,1
machdep.cpu.features: FPU VME DE SSE SSE2 AVX1.0
hw.memsize: 17179869184
vm.swapusage: total = 2048.00M  free = 1735.25M  active = 312.75M  (encrypted)
===diskutil-info-disk0===
Device Identifier:         disk0
Whole:                     Yes
Disk Size:                 500277792768 Bytes
Solid State:               Yes
Protocol:                  Solid State
===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (hardware/determine-hardware {:exec exec-fn :shell {:type :zsh}})]
    (is (= "Intel(R) Core(TM) i5-7500 CPU @ 3.40GHz" (get-in res [:cpu :model])))
    (is (= 4 (get-in res [:cpu :cores])))
    (is (= "x86_64" (get-in res [:cpu :architecture])))
    (is (= #{"fpu" "vme" "de" "sse" "sse2" "avx1.0"} (get-in res [:cpu :flags])))
    (is (= 17179869184 (get-in res [:memory :total])))
    (is (= (* 2048 1024 1024) (get-in res [:memory :swap])))
    (is (= [{:name "disk0", :size 500277792768, :type :ssd}] (:disks res)))
    (is (= {:is_virtual true :type :vmware} (:virtualization res)))))

(deftest determine-hardware-macos-empty-sections-test
  (let [mock-out "===uname===
x86_64
===cpuinfo===
===sysctl-a===
machdep.cpu.brand_string: Intel Core 2 Duo P9xxx (Penryn Class Core 2)
hw.ncpu: 4
hw.memsize: 6442450944
machdep.cpu.features: FPU VME DE PSE TSC MSR PAE MCE CX8 APIC SEP MTRR PGE MCA CMOV PAT PSE36 CLFSH MMX FXSR SSE SSE2 HTT SSE3 SSSE3 FMA CX16 SSE4.1 SSE4.2 x2APIC MOVBE AES VMM XSAVE OSXSAVE AVX1.0
vm.swapusage: total = 0.00M  used = 0.00M  free = 0.00M  (encrypted)
===sys-class-dmi===
===meminfo===
===lsblk===
===sys-block===
===diskutil-list===
===diskutil-info-disk0===
   Device Identifier:         disk0
   Device Node:               /dev/disk0
   Whole:                     Yes
   Part of Whole:             disk0
   Device / Media Name:       QEMU HARDDISK
   Disk Size:                 268.4 MB (268435456 Bytes) (exactly 524288 512-Byte-Units)
   Solid State:               No
===systemd-detect-virt===
===kern-vm-guest===
===cgroup===
===dockerenv===
===geom-disk===
===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (hardware/determine-hardware {:exec exec-fn :shell {:type :zsh}})]
    (is (= "Intel Core 2 Duo P9xxx (Penryn Class Core 2)" (get-in res [:cpu :model])))
    (is (= 4 (get-in res [:cpu :cores])))
    (is (= "x86_64" (get-in res [:cpu :architecture])))
    (is (= #{"fpu" "vme" "de" "pse" "tsc" "msr" "pae" "mce" "cx8" "apic" "sep" "mtrr" "pge" "mca" "cmov" "pat" "pse36" "clfsh" "mmx" "fxsr" "sse" "sse2" "htt" "sse3" "ssse3" "fma" "cx16" "sse4.1" "sse4.2" "x2apic" "movbe" "aes" "vmm" "xsave" "osxsave" "avx1.0"}
           (get-in res [:cpu :flags])))
    (is (= 6442450944 (get-in res [:memory :total])))
    (is (= 0 (get-in res [:memory :swap])))
    (is (= [{:name "disk0", :size 268435456, :type :hdd}] (:disks res)))))

(deftest determine-hardware-powershell-test
  (let [mock-out "===cpu===
Intel(R) Xeon(R) CPU E5-2673 v4 @ 2.30GHz|2|AMD64|9|12345
===memory===
17179869184|2147483648
===disks===
0|Red Hat VirtIO SCSI Disk Device|137438953472|SSD
===virtualization===
QEMU|Standard PC
===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (hardware/determine-hardware {:exec exec-fn :shell {:type :powershell}})]
    (is (= "Intel(R) Xeon(R) CPU E5-2673 v4 @ 2.30GHz" (get-in res [:cpu :model])))
    (is (= 2 (get-in res [:cpu :cores])))
    (is (= "x86_64" (get-in res [:cpu :architecture])))
    (is (= 17179869184 (get-in res [:memory :total])))
    (is (= 2147483648 (get-in res [:memory :swap])))
    (is (= [{:name "Red Hat VirtIO SCSI Disk Device", :size 137438953472, :type :ssd}] (:disks res)))
    (is (= {:is_virtual true :type :qemu} (:virtualization res)))))

(deftest determine-hardware-cmd-test
  (let [mock-out "===cpu===
Name=Intel(R) Xeon(R) CPU E5-2673 v4 @ 2.30GHz
NumberOfCores=2
PROCESSOR_ARCHITECTURE=AMD64
===memory===
TotalPhysicalMemory=17179869184
AllocatedBaseSize=2048
===disks===
Model=Red Hat VirtIO SCSI Disk Device
Size=137438953472
===virtualization===
Manufacturer=QEMU
===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (hardware/determine-hardware {:exec exec-fn :shell {:type :cmd-exe}})]
    (is (= "Intel(R) Xeon(R) CPU E5-2673 v4 @ 2.30GHz" (get-in res [:cpu :model])))
    (is (= 2 (get-in res [:cpu :cores])))
    (is (= "x86_64" (get-in res [:cpu :architecture])))
    (is (= 17179869184 (get-in res [:memory :total])))
    (is (= (* 2048 1024 1024) (get-in res [:memory :swap])))
    (is (= [{:name "Red Hat VirtIO SCSI Disk Device", :size 137438953472, :type :ssd}] (:disks res)))
    (is (= {:is_virtual true :type :qemu} (:virtualization res)))))

(def host-hardware
  (let [exec #(process/shell {:cmd "bash" :in % :out :string :err :string})]
    (hardware/determine-hardware
      {:exec exec
       :shell (shell/determine-shell {:exec exec})})))

(def host-cpu (:cpu host-hardware))
(def host-disks (:disks host-hardware))
(def host-memory (:memory host-hardware))

(def hardware-result
  {:alpine
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :alpine-dash
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :alpine-fish
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :alpine-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :amazonlinux
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :amazonlinux-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :amazonlinux-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux-dash
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux-fish
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux-nu
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :archlinux-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :debian
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :debian-dash
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :debian-fish
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :debian-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :debian-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora-dash
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora-fish
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora-nu
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :fedora-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :freebsd
   {:cpu
    {:architecture "x86_64",
     :cores 2,
     :flags #{"sse"},
     :model "AMD Ryzen 9 9950X 16-Core Processor"},
    :disks
    [{:name "cd0", :size 0, :type :hdd}
     {:name "vtbd0", :size 20971520000, :type :hdd}],
    :memory {:swap 0, :total 1033318400},
    :virtualization {:is_virtual true, :type :kvm}},
   :macos
   {:cpu
    {:architecture "x86_64",
     :cores 4,
     :flags
     #{"mca"
       "fxsr"
       "apic"
       "fma"
       "cmov"
       "pat"
       "sse3"
       "x2apic"
       "sep"
       "vmm"
       "mmx"
       "msr"
       "avx1.0"
       "sse"
       "cx8"
       "ssse3"
       "clfsh"
       "mce"
       "pge"
       "de"
       "sse2"
       "osxsave"
       "movbe"
       "pae"
       "pse"
       "aes"
       "tsc"
       "sse4.1"
       "htt"
       "sse4.2"
       "cx16"
       "fpu"
       "xsave"
       "pse36"
       "mtrr"
       "vme"},
     :model "Intel Core 2 Duo P9xxx (Penryn Class Core 2)"},
    :disks
    [{:name "disk0", :size 268435456, :type :hdd}
     {:name "disk1", :size 68719476736, :type :hdd}
     {:name "disk2", :size 68375502848, :type :hdd}],
    :memory {:swap 0, :total 6442450944},
    :virtualization {:is_virtual false, :type nil}},
   :netbsd
   {:cpu
    {:architecture "x86_64",
     :cores 2,
     :flags
     #{"mca"
       "fxsr"
       "avx512_bf16"
       "avx512_vpopcntdq"
       "apic"
       "vnmi"
       "f16c"
       "fma"
       "avx512_bitalg"
       "avx512vbmi"
       "cmov"
       "flushbyasid"
       "vmcb_clean"
       "ht"
       "pat"
       "xsaveerptr"
       "x2apic"
       "rdrand"
       "sep"
       "lm"
       "xgetbv1"
       "3dnowprefetch"
       "perfctr_core"
       "succor"
       "pausefilter"
       "mmxext"
       "avx512dq"
       "sse4_1"
       "erms"
       "wbnoinvd"
       "pclmulqdq"
       "smep"
       "nrip_save"
       "mmx"
       "osvw"
       "msr"
       "clflush"
       "virt_ssbd"
       "sse4a"
       "clflushopt"
       "movdir64b"
       "lbrv"
       "clzero"
       "avx512ifma"
       "adx"
       "bmi1"
       "sse"
       "avx512vl"
       "invpcid"
       "npt"
       "cx8"
       "svm"
       "cr8_legacy"
       "smap"
       "avx512f"
       "xsavec"
       "ssse3"
       "syscall"
       "arat"
       "mce"
       "pge"
       "de"
       "sse2"
       "v_vmsave_vmload"
       "pku"
       "sha_ni"
       "vgif"
       "movbe"
       "pae"
       "tsc_deadline_timer"
       "pse"
       "avx512bw"
       "pfthreshold"
       "aes"
       "tsc"
       "sse4_2"
       "vpclmulqdq"
       "vaes"
       "pni"
       "tsc_scale"
       "lahf_lm"
       "gfni"
       "umip"
       "cmp_legacy"
       "rdpid"
       "avx512_vnni"
       "movdiri"
       "abm"
       "cx16"
       "pdpe1gb"
       "fsgsbase"
       "fpu"
       "xsave"
       "fxsr_opt"
       "misalignsse"
       "rdtscp"
       "avx"
       "hypervisor"
       "xsaveopt"
       "tsc_adjust"
       "pse36"
       "bmi2"
       "avx512_vbmi2"
       "mtrr"
       "xsaves"
       "nx"
       "vme"
       "avx_vnni"
       "clwb"
       "avx512cd"
       "overflow_recov"
       "avx2"
       "popcnt"},
     :model "AMD Ryzen 9 9950X 16-Core Processor"},
    :disks [],
    :memory {:swap 4294963200, :total 1012408320},
    :virtualization {:is_virtual false, :type nil}},
   :oraclelinux
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :oraclelinux-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :oraclelinux-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :rockylinux
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :rockylinux-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :rockylinux-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :ubuntu
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :ubuntu-dash
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :ubuntu-fish
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :ubuntu-ksh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :ubuntu-zsh
   {:cpu host-cpu,
    :disks host-disks,
    :memory host-memory,
    :virtualization {:is_virtual true, :type :docker}},
   :windows
   {:cpu
    {:architecture "x86_64",
     :cores 2,
     :flags #{"sse3" "sse" "sse2" "avx" "avx2"},
     :model "AMD Ryzen 9 9950X 16-Core Processor"},
    :disks [{:name "QEMU HARDDISK", :size 20971520000, :type :ssd}],
    :memory {:swap 1207959552, :total 2146947072},
    :virtualization {:is_virtual true, :type :qemu}}})

(deftest determine-hardware
  (is (=
        (into {}
              (for [host (config/select-hosts {:exclude #{}})]
                (let [exec (shell-test/make-executor-fn (config/host-ports host))]
                  [host (hardware/determine-hardware
                          {:exec exec
                           :shell (shell/determine-shell {:exec exec})})])))
        (config/filter-hashmap
          {:exclude #{}}
          hardware-result))))
