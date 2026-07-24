(ns eyre.users
  (:require [clojure.string :as string]))

(defn process-id-name-substring [substring]
  (let [[_ id name] (re-matches #"(\d+)\(([\d\w_\.\-]+)\)" substring)]
    {:id (Integer/parseInt id)
     :name name}))

(defn process-id [id-out]
  (let [{:keys [gid uid groups]}
        (-> id-out string/trim (string/split #"\s+")
            (->> (take 3)
                 (map (fn [line]
                        (let [[type val] (string/split line #"=" 2)
                              vals (->> (string/split val #",")
                                        (mapv process-id-name-substring))]
                          [(keyword type) vals])))
                 (into {})))]
    {:gid (first gid)
     :uid (first uid)
     :groups groups
     :group-ids (into #{} (map :id groups))
     :group-names (into #{} (map :name groups))
     }))

#_ (process-id "uid=1000(crispin) gid=1000(crispin) groups=1000(crispin),3(sys),90(network),98(power),950(libvirt),960(docker),962(autologin),991(lp),992(kvm),994(input),996(audio),998(wheel)")

;; =>
#_ {:gid {:id 1000, :name "crispin"},
    :uid {:id 1000, :name "crispin"},
    :groups
    [{:id 1000, :name "crispin"}
     {:id 3, :name "sys"}
     {:id 90, :name "network"}
     {:id 98, :name "power"}
     {:id 950, :name "libvirt"}
     {:id 960, :name "docker"}
     {:id 962, :name "autologin"}
     {:id 991, :name "lp"}
     {:id 992, :name "kvm"}
     {:id 994, :name "input"}
     {:id 996, :name "audio"}
     {:id 998, :name "wheel"}],
    :group-ids #{950 998 992 994 1000 90 996 962 3 991 98 960},
    :group-names
    #{"lp"
      "libvirt"
      "wheel"
      "power"
      "network"
      "input"
      "docker"
      "audio"
      "kvm"
      "sys"
      "autologin"
      "crispin"}}
