(ns eyre-test.qemu
  (:require [babashka.process :as process]
            [clojure.string :as string]
            [clojuressh.core :as ssh]
            [clojuressh.scp :as scp]
            [eyre-test.utils :as utils]))

(def ssh-opts {:host "localhost"
               :username "root"
               :strict-host-key-checking :no})

(defn- with-session [opts f]
  (let [session (ssh/ssh (assoc ssh-opts :port (:ssh-port opts)))]
    (try
      (f session)
      (finally (ssh/disconnect session)))))

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
  (with-session opts
    (fn [session]
      (let [{:keys [exit out err]} (ssh/exec session command)]
        (assert (zero? exit) (str "ssh exec failed: " command " out:" out " err:" err))
        out))))

(defn cp-to [opts local-src remote-dest]
  (with-session opts
    (fn [session]
      (scp/transfer-to session local-src remote-dest))))

(defn cp-from [opts remote-src local-dest]
  (with-session opts
    (fn [session]
      (scp/transfer-from session remote-src local-dest))))
