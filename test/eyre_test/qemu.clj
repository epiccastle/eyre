(ns eyre-test.qemu
  (:require [babashka.process :as process]
            [clojure.string :as string]
            [eyre-test.utils :as utils]))

(defn run [command error-message]
  (let [{:keys [exit err out]}
        (process/sh command)]
    (assert (zero? exit) (str error-message ": out:" out " err:" err))
    out))

(defn run! [command]
  (process/sh command))

(defn start [{:keys [qemu-bin image-path ssh-port serial-socket monitor-socket]
              :or {serial-socket "/tmp/qemu-serial.sock"
                   monitor-socket "/tmp/qemu-monitor.sock"}
              :as opts}]
  ;; Note: Assumes a virtio-net or similar setup that forwards guest port 22 to host ssh-port
  (let [cmd (format "%s -enable-kvm -m 1024 -drive file=%s,format=qcow2 -netdev user,id=net0,hostfwd=tcp::%d-:22 -device virtio-net-pci,netdev=net0 -vnc :0 -serial unix:%s,server,nowait -monitor unix:%s,server,nowait -daemonize"
                    qemu-bin image-path ssh-port serial-socket monitor-socket)]
    (println cmd)
    (run cmd "qemu start failed")
    true))

(defn stop [{:keys [monitor-socket]
             :or {monitor-socket "/tmp/qemu-monitor.sock"}}]
  ;; This sends a system_powerdown to the QEMU monitor
  (run! (format "echo 'system_powerdown' | socat - UNIX-CONNECT:%s" monitor-socket)))

(defn exec [opts command]
  (run (format "ssh -p %d -o StrictHostKeyChecking=no root@localhost '%s'" (:ssh-port opts) command)
       "ssh exec failed"))

(defn cp-to [opts local-src remote-dest]
  (run (format "scp -P %d -o StrictHostKeyChecking=no %s root@localhost:%s" (:ssh-port opts) local-src remote-dest)
       "scp to failed"))

(defn cp-from [opts remote-src local-dest]
  (run (format "scp -P %d -o StrictHostKeyChecking=no root@localhost:%s %s" (:ssh-port opts) remote-src local-dest)
       "scp from failed"))
