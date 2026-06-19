(ns eyre-test.remote-test
  (:require [clojure.test :refer :all]
            [eyre.facts :as facts]
            [clojure.pprint :as pprint]
            [eyre-test.docker :as docker]
            [eyre-test.qemu :as qemu]
            [eyre-test.utils :as utils]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [eyre-test.bcrypt :as bcrypt]))


(deftest openbsd
  (let [opts {:root-password "root-access-please"
              :qemu-bin "qemu-system-x86_64"
              :image-path "test/images/openbsd-base.qcow2"
              :ssh-port 9876}]
    (println "starting...")
    (qemu/start opts)
    (utils/wait-for-ssh! "localhost" (:ssh-port opts))
    (let [session (ssh/ssh "localhost"
                           {:port (:ssh-port opts)
                            :username "root"
                            :password (:root-password opts)
                            :strict-host-key-checking false})]
      (-> (ssh/exec session "uname -a" {:out :string})
          deref
          :out
          println)

      (println "shutdown...")
      (ssh/exec session "shutdown -p now")
      (session/disconnect session))
    (utils/wait-for-file-missing "/tmp/qemu-serial.sock")
    (println "done")

    )
  )
#_(deftest freebsd
  (let [opts {:root-password "root-access-please"
              :base-image "freebsd/freebsd-runtime:15.1"
              :ssh-port 9876}]
    (qemu/start {:qemu-bin "qemu-system-x86_64"
                 :image-path "test/FreeBSD-15.1-RELEASE-amd64-zfs.qcow2"
                 :serial-socket "/tmp/freebsd.sock"
                 :ssh-port 9876})
    #_(println "!!" (bcrypt/hashpw "root-access-please" (bcrypt/gensalt)))




    )
  )

#_
(deftest alpine
  (let [opts {:root-password "root-access-please"
              :base-image "alpine:3.16.2"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest ubuntu
  (let [opts {:root-password "root-access-please"
              :base-image "ubuntu:24.04"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )


#_
(deftest debian
  (let [opts {:root-password "root-access-please"
              :base-image "debian:stable"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest fedora
  (let [opts {:root-password "root-access-please"
              :base-image "fedora:44"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest arch
  (let [opts {:root-password "root-access-please"
              :base-image "archlinux:latest"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest amazonlinux
  (let [opts {:root-password "root-access-please"
              :base-image "amazonlinux:2023"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest rockylinux
  (let [opts {:root-password "root-access-please"
              :base-image "rockylinux:9"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )

#_
(deftest oraclelinux
  (let [opts {:root-password "root-access-please"
              :base-image "oraclelinux:10"
              :ssh-port 9876}]

    (docker/cleanup opts)
    (docker/build opts)
    (docker/start opts)

    (is true)

    (docker/cleanup opts))
  )
