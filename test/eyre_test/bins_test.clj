(ns eyre-test.bins-test
  (:require [clojure.test :refer :all]
            [eyre.shell :as shell]
            [eyre.bins :as bins]
            [eyre-test.shell-test :as shell-test]
            [eyre-test.config :as config]))

;; determine-paths should return a non-nil map for every host connection
(deftest determine-paths-test
  (doseq [host (config/select-hosts {:exclude #{}})]
    (let [exec (shell-test/make-executor-fn (config/host-ports host))
          paths (bins/determine-paths
                  {:exec exec
                   :shell (shell/determine-shell {:exec exec})})]
      (is (not (nil? paths))
          (str "determine-paths returned nil for " host))
      (is (map? paths)
          (str "determine-paths did not return a map for " host)))))

(defmacro same-paths-test [test-name pattern]
  `(deftest ~test-name
     (let [results# (into {}
                          (for [host# (config/select-hosts {:only #{~pattern}})]
                            (let [exec# (shell-test/make-executor-fn (config/host-ports host#))]
                              [host# (bins/determine-paths
                                       {:exec exec#
                                        :shell (shell/determine-shell {:exec exec#})})])))
           distinct-results# (set (vals results#))]
       (is (= 1 (count distinct-results#))
           (str "Expected all " ~(name pattern) " targets to detect the same paths, but got: "
                (into {} (for [[host# res#] results#] [host# res#])))))))

(same-paths-test alpine-paths-test :alpine*)
(same-paths-test ubuntu-paths-test :ubuntu*)
(same-paths-test fedora-paths-test :fedora*)
(same-paths-test debian-paths-test :debian*)
(same-paths-test archlinux-paths-test :archlinux*)
(same-paths-test amazonlinux-paths-test :amazonlinux*)
(same-paths-test rockylinux-paths-test :rockylinux*)
(same-paths-test oraclelinux-paths-test :oraclelinux*)
