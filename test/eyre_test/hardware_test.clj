(ns eyre-test.hardware-test
  (:require [clojure.test :refer :all]
            [eyre.hardware :as hardware]))

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
