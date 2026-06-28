(ns eyre-test.docker
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

(defn tag-name [opts]
  (-> (:base-image opts)
      (string/replace #":" "_")))

(defn build [{:keys [root-password
                     base-image]
              :as opts}]
  (run
    (format
      "docker build -t eyre/%s-base --build-arg root_password=%s --build-arg base_image=%s test/docker"
      (tag-name opts)
      root-password
      base-image)
    "docker build failed"))

(defn cleanup [opts]
  (run! (format "docker container stop eyre-%s" (tag-name opts)))
  (run! (format "docker container rm eyre-%s" (tag-name opts)))
  nil)

(defn start [{:keys [ssh-port vnc-port]
              :as opts}]
  (let [result (-> (format "docker run --name eyre-%s -d -p %d:22%s eyre/%s-base"
                           (tag-name opts)
                           ssh-port
                           (if vnc-port (format " -p %d:5900" vnc-port) "")
                           (tag-name opts))
                   (run "docker run failed")
                   string/trim)]
    (utils/wait-for-port! "localhost" ssh-port)
    result))

(defn stop [opts]
  (run! (format "docker container stop eyre-%s" (tag-name opts))))

(defn exec [opts command]
  (run
    (str (format "docker exec eyre-%s " (tag-name opts)) command)
    "docker exec failed"))

(defn exec! [opts command]
  (run!
    (str (format "docker exec eyre-%s " (tag-name opts)) command)))

(defn cp-to [opts local-src remote-dest]
  (run
    (format "docker cp \"%s\" \"eyre-%s:%s\"" local-src (tag-name opts) remote-dest)
    "docker cp failed"))

(defn cp-from [opts remote-src local-dest]
  (run
    (format "docker cp \"eyre-%s:%s\" \"%s\"" (tag-name opts) remote-src local-dest)
    "docker cp failed"))

(defn put-file [opts contents remote-dest]
  (process/sh
    ["docker" "exec" (format "eyre-" (tag-name opts)) "ash" "-c"
     (format "echo '%s' > '%s'"
             contents
             remote-dest)]))

(defn put-dir
  "transfer a complete local directory to the docker container"
  [opts src-dir src-path dest-path]
  (process/sh "rm /tmp/eyre-tarball.tgz")
  (process/sh (format "tar -cvz -C '%s' -f /tmp/eyre-tarball.tgz '%s'" src-dir src-path))
  (exec opts "rm -f /tmp/eyre-tarball.tgz")
  (cp-to opts "/tmp/eyre-tarball.tgz" "/tmp/eyre-tarball.tgz")
  (exec
    opts
    (format "tar -xv -f /tmp/eyre-tarball.tgz -C '%s'"
            dest-path)))

(defn md5 [opts path]
  (-> (exec opts (format "md5sum '%s'" path))
      (string/split #" ")
      first))

(defn get-container-ip
  [opts]
  (-> (format "docker inspect -f '{{range.NetworkSettings.Networks}}{{.IPAddress}}{{end}}' eyre-%s" opts)
      process/sh
      :out
      string/trim))

(def ^:private docker-instances
  [{:root-password "root-access-please"
    :base-image "alpine:3.16.2"
    :ssh-port 22020
    :vnc-display 20
    :vnc-port 5920}
   {:root-password "root-access-please"
    :base-image "ubuntu:24.04"
    :ssh-port 22021
    :vnc-display 21
    :vnc-port 5921}
   {:root-password "root-access-please"
    :base-image "debian:stable"
    :ssh-port 22022
    :vnc-display 22
    :vnc-port 5922}
   {:root-password "root-access-please"
    :base-image "fedora:44"
    :ssh-port 22023
    :vnc-display 23
    :vnc-port 5923}
   {:root-password "root-access-please"
    :base-image "archlinux:latest"
    :ssh-port 22024
    :vnc-display 24
    :vnc-port 5924}
   {:root-password "root-access-please"
    :base-image "amazonlinux:2023"
    :ssh-port 22025
    :vnc-display 25
    :vnc-port 5925}
   {:root-password "root-access-please"
    :base-image "rockylinux:9"
    :ssh-port 22026
    :vnc-display 26
    :vnc-port 5926}
   {:root-password "root-access-please"
    :base-image "oraclelinux:10"
    :ssh-port 22027
    :vnc-display 27
    :vnc-port 5927}])

(defn start-all-docker
  "Starts all docker test instances.  SSH ports begin at 22020,
   VNC display numbers at :20 (TCP 5920).  Returns a vector of option
   maps for the running containers."
  []
  (doseq [opts docker-instances]
    (cleanup opts)
    (build opts))
  (doseq [opts docker-instances]
    (start opts))
  docker-instances)

#_ (start-all-docker)
