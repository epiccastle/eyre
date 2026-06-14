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

(defn start [{:keys [qemu-bin image-path ssh-port]
              :as opts}]
  ;; Note: Assumes a virtio-net or similar setup that forwards guest port 22 to host ssh-port
  (let [cmd (format "%s -enable-kvm -m 1024 -drive file=%s,format=qcow2 -netdev user,id=net0,hostfwd=tcp::%d-:22 -device virtio-net-pci,netdev=net0 -vnc :0 -daemonize"
                    qemu-bin image-path ssh-port)]
    (run cmd "qemu start failed")
    (utils/wait-for-port! "localhost" ssh-port)
    true))

(defn stop [opts]
  ;; This sends a system_powerdown to the QEMU monitor
  (run! (format "echo 'system_powerdown' | socat - UNIX-CONNECT:/tmp/qemu-monitor-%s" (:name opts))))

(defn exec [opts command]
  (run (format "ssh -p %d -o StrictHostKeyChecking=no root@localhost '%s'" (:ssh-port opts) command)
       "ssh exec failed"))

(defn cp-to [opts local-src remote-dest]
  (run (format "scp -P %d -o StrictHostKeyChecking=no %s root@localhost:%s" (:ssh-port opts) local-src remote-dest)
       "scp to failed"))

(defn cp-from [opts remote-src local-dest]
  (run (format "scp -P %d -o StrictHostKeyChecking=no root@localhost:%s %s" (:ssh-port opts) remote-src local-dest)
       "scp from failed"))
