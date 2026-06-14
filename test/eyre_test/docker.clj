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
      "docker build -t eyre/%s-base --build-arg root_password=%s --build-arg base_image=%s test"
      (tag-name opts)
      root-password
      base-image)
    "docker build failed"))

(defn cleanup [opts]
  (run! (format "docker container stop eyre-%s" (tag-name opts)))
  (run! (format "docker container rm eyre-%s" (tag-name opts)))
  nil)

(defn start [{:keys [ssh-port]
              :as opts}]
  (let [result (-> "docker run --name eyre-%s -d -p %d:22 eyre/%s-base"
                  (format (tag-name opts) ssh-port (tag-name opts))
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
