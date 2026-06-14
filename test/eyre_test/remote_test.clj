(ns eyre-test.remote-test
  (:require [clojure.test :refer :all]
            [eyre.facts :as facts]
            [clojure.pprint :as pprint]
            [eyre-test.docker :as docker]))

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

;; (deftest freebsd
;;   (let [opts {:root-password "root-access-please"
;;               :base-image "freebsd/freebsd-runtime:15.1"
;;               :ssh-port 9876}]

;;     (docker/cleanup opts)
;;     (docker/build opts)
;;     (docker/start opts)

;;     (is true)

;;     (docker/cleanup opts))
;;   )
