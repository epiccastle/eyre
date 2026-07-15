(ns eyre-test.execute
  (:require [clojure.string :as str]
            [eyre-test.config :as eyre-test]
            [eyre.shell :as shell]
            [eyre-test.utils :as utils]
            [medley.core :as medley]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [clojuressh.user-info :as user-info]))

(defn test-ports
  []
  (->> eyre-test.config/host-ports
       (medley/map-vals #(utils/port-open? "localhost" % 1000))))

#_ (test-ports)

#_(def script "cat /etc/ssh/sshd_config | grep -v ^$ | grep -v ^#" #_(slurp "guess.sh"))
(def script
  #_"echo PS1;echo CMD1&&echo SH1#sh1"
  #_"echo CMD${0:-}1 2>nul||(echo|set /p=\"\" 2>nul)&(echo SH$0;exit)#'%~&echo;Write-Host PS1"
  #_"echo CMDEXE&&echo SH$0||powershell -NoProfile -Command \"Write-Output PWSH\""
  #_"echo %COMSPEC%\r\nWrite-Output powershell\r\necho $0\r\n"
  "echo $SHELL"
  )

(defn shell-all [script]
  (->>
    (for [[host conf] eyre-test.config/host-ports]
      (when (utils/port-open? "localhost" (:port conf) 1000)
        (prn host)
        [host (try
                (let [session (ssh/ssh "localhost" (merge
                                                     {:username "root"
                                                      :password "root-access-please"
                                                      :strict-host-key-checking false}
                                                     conf))
                      result @(ssh/exec session script {:out :string
                                                               :err :string})]
                  (session/disconnect session)
                  [:ok (:exit result) (:out result) (:err result)])
                (catch Exception e [:err (ex-message e)]))]))
    (filter identity)
    (into {})))

#_ (shell-all "ip -o addr")

(defn run-all [func]
  (->>
    (for [[host conf] eyre-test.config/host-ports]
      [host
       (func {:exec
              (fn [command]
                (when (utils/port-open? "localhost" (:port conf) 1000)
                  (prn host)
                  (try
                    (let [session (ssh/ssh "localhost" (merge
                                                         {:username (:username conf "root")
                                                          :password "root-access-please"
                                                          :strict-host-key-checking false}
                                                         conf))
                          result @(ssh/exec session command {:out :string
                                                             :err :string})]
                      (session/disconnect session)
                      result)
                    (catch Exception e [:err (ex-message e)]))))})])
    (filter identity)
    (into {})))

#_(doseq [[os result] (run-all determine-shell)]
  (println os)
  (println result)
  (println)
  )

#_(run-all shell/determine-shell)
