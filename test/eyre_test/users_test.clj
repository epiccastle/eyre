(ns eyre-test.users-test
  (:require [clojure.string :as string]
            [clojure.test :refer :all]
            [eyre-test.config :as config]
            [eyre.shell :as shell]
            [eyre.users :as users]
            [eyre-test.shell-test :as shell-test]))

(deftest process-id-test
  (is (= {:gid {:id 1000 :name "crispin"}
          :uid {:id 1000 :name "crispin"}
          :groups [{:id 1000 :name "crispin"}
                   {:id 3 :name "sys"}
                   {:id 90 :name "network"}
                   {:id 98 :name "power"}]
          :group-ids #{1000 3 90 98}
          :group-names #{"crispin" "sys" "network" "power"}}
         (users/process-id "uid=1000(crispin) gid=1000(crispin) groups=1000(crispin),3(sys),90(network),98(power)"))))

(deftest gather-users-linux-test
  (let [mock-out "===id===\nuid=0(root) gid=0(root) groups=0(root)\n===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (users/gather-users {:exec exec-fn :shell {:type :bash}})]
    (is (= {:id 0 :name "root"} (:uid res)))
    (is (= {:id 0 :name "root"} (:gid res)))
    (is (= [{:id 0 :name "root"}] (:groups res)))
    (is (= #{0} (:group-ids res)))
    (is (= #{"root"} (:group-names res)))))

(deftest gather-users-macos-test
  (let [mock-out "===id===\nuid=0(root) gid=0(wheel) groups=0(wheel),1(daemon),3(sys)\n===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (users/gather-users {:exec exec-fn :shell {:type :zsh}})]
    (is (= {:id 0 :name "root"} (:uid res)))
    (is (= {:id 0 :name "wheel"} (:gid res)))
    (is (= [{:id 0 :name "wheel"} {:id 1 :name "daemon"} {:id 3 :name "sys"}]
           (:groups res)))
    (is (= #{0 1 3} (:group-ids res)))
    (is (= #{"wheel" "daemon" "sys"} (:group-names res)))))

(deftest gather-users-powershell-test
  (let [mock-out "===id===\n\"WINDOWS-TEST\\Administrator\",\"S-1-5-21-1234567890-1234567890-1234567890-500\"\n===groups===\n\"Administrators\",\"Group\",\"S-1-5-32-544\",\"Mandatory group, Enabled by default, Enabled group\"\n\"Users\",\"Group\",\"S-1-5-32-545\",\"Mandatory group, Enabled by default, Enabled group\"\n\"INTERACTIVE\",\"Well-known group\",\"S-1-5-4\",\"Mandatory group, Enabled by default, Enabled group\"\n===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (users/gather-users {:exec exec-fn :shell {:type :powershell}})]
    (is (= {:name "WINDOWS-TEST\\Administrator"
            :id "S-1-5-21-1234567890-1234567890-1234567890-500"}
           (:uid res)))
    (is (nil? (:gid res)))
    (is (= [{:name "Administrators" :id "S-1-5-32-544"}
            {:name "Users" :id "S-1-5-32-545"}
            {:name "INTERACTIVE" :id "S-1-5-4"}]
           (:groups res)))
    (is (= #{"S-1-5-32-544" "S-1-5-32-545" "S-1-5-4"}
           (:group-ids res)))
    (is (= #{"Administrators" "Users" "INTERACTIVE"}
           (:group-names res)))))

(deftest gather-users-cmd-test
  (let [mock-out "===id===\n\"WINDOWS-TEST\\Administrator\",\"S-1-5-21-1234567890-1234567890-1234567890-500\"\n===groups===\n\"Administrators\",\"Group\",\"S-1-5-32-544\",\"Mandatory group, Enabled by default, Enabled group\"\n\"Users\",\"Group\",\"S-1-5-32-545\",\"Mandatory group, Enabled by default, Enabled group\"\n===end==="
        exec-fn (fn [_script] {:exit 0 :out mock-out :err ""})
        res (users/gather-users {:exec exec-fn :shell {:type :cmd-exe}})]
    (is (= {:name "WINDOWS-TEST\\Administrator"
            :id "S-1-5-21-1234567890-1234567890-1234567890-500"}
           (:uid res)))
    (is (nil? (:gid res)))
    (is (= [{:name "Administrators" :id "S-1-5-32-544"}
            {:name "Users" :id "S-1-5-32-545"}]
           (:groups res)))
    (is (= #{"S-1-5-32-544" "S-1-5-32-545"}
           (:group-ids res)))
    (is (= #{"Administrators" "Users"}
           (:group-names res)))))

(deftest gather-users
  (doseq [host (config/select-hosts {:exclude #{}})]
    (prn host)
    (testing (str "host " host)
      (let [exec (shell-test/make-executor-fn (config/host-ports host))
            shell (shell/gather-shell {:exec exec})
            res (users/gather-users {:exec exec :shell shell})
            expected-name (or (:username (config/host-ports host)) "root")]
        (is (map? res))
        (is (contains? res :uid))
        (is (contains? res :gid))
        (is (contains? res :groups))
        (is (contains? res :group-ids))
        (is (contains? res :group-names))
        (is (map? (:uid res)))
        (is (some? (get-in res [:uid :id])))
        (is (contains? (:uid res) :name))
        (is (string/includes? (string/lower-case (or (get-in res [:uid :name]) ""))
                              (string/lower-case expected-name)))
        (when (:gid res)
          (is (map? (:gid res)))
          (is (some? (get-in res [:gid :id])))
          (is (contains? (:gid res) :name)))
        (is (vector? (:groups res)))
        (is (seq (:groups res)))
        (is (every? #(and (contains? % :id) (contains? % :name)) (:groups res)))
        (is (set? (:group-ids res)))
        (is (= (set (map :id (:groups res))) (:group-ids res)))
        (is (set? (:group-names res)))
        (is (= (set (map :name (:groups res))) (:group-names res)))))))
