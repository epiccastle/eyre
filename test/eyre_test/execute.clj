(ns eyre-test.execute
  (:require [clojure.string :as str]
            [eyre.shell :as shell]
            [eyre-test.utils :as utils]
            [medley.core :as medley]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [clojuressh.user-info :as user-info]))

(def host-ports
  {
   ;; qemu hosts
   ;;#_#_
   :windows {:port     22001
             :username "Administrator"}

   ;; openbsd doesnt boot, WARNING: / was not properly unmounted
   #_#_:openbsd 22002

   ;;#_#_#_#_#_#_
   :netbsd  {:port 22003}
   :freebsd {:port 22004}
   :macos   {:port 22005}

   ;; docker hosts
   ;;#_#_#_#_#_#_#_#_
   :alpine      {:port 22020}
   :alpine-fish {:port     22020
                 :username "fish"}
   :alpine-zsh  {:port     22020
                 :username "zsh"}
   :alpine-dash {:port     22020
                 :username "dash"}

   ;;#_#_#_#_#_#_#_#_#_#_
   :ubuntu      {:port 22021}
   :ubuntu-fish {:port     22021
                 :username "fish"}
   :ubuntu-zsh  {:port     22021
                 :username "zsh"}
   :ubuntu-dash {:port     22021
                 :username "dash"}
   :ubuntu-ksh {:port     22021
                :username "ksh"}

   ;;#_#_#_#_#_#_#_#_#_#_
   :debian      {:port 22022}
   :debian-fish {:port     22022
                 :username "fish"}
   :debian-zsh  {:port     22022
                 :username "zsh"}
   :debian-dash {:port     22022
                 :username "dash"}
   :debian-ksh {:port     22022
                :username "ksh"}

   ;;#_#_#_#_#_#_#_#_#_#_
   :fedora      {:port 22023}
   :fedora-fish {:port     22023
                 :username "fish"}
   :fedora-zsh  {:port     22023
                 :username "zsh"}
   :fedora-dash {:port     22023
                 :username "dash"}
   :fedora-ksh {:port     22023
                :username "ksh"}
   :fedora-nu {:port     22023
                :username "nu"}

   ;;#_#_#_#_#_#_#_#_#_#_#_#_
   :archlinux   {:port 22024}
   :archlinux-fish {:port     22024
                    :username "fish"}
   :archlinux-zsh  {:port     22024
                    :username "zsh"}
   :archlinux-dash {:port     22024
                    :username "dash"}
   :archlinux-ksh {:port     22024
                   :username "ksh"}
   :archlinux-nu {:port     22024
                   :username "nu"}

   ;;#_#_#_#_#_#_
   :amazonlinux {:port 22025}
   :amazonlinux-zsh  {:port     22025
                      :username "zsh"}
   :amazonlinux-ksh {:port     22025
                     :username "ksh"}

   ;;#_#_#_#_#_#_
   :rockylinux  {:port 22026}
   :rockylinux-zsh  {:port     22026
                     :username "zsh"}
   :rockylinux-ksh {:port     22026
                    :username "ksh"}

   ;;#_#_#_#_#_#_
   :oraclelinux {:port 22027}
   :oraclelinux-zsh  {:port     22027
                      :username "zsh"}
   :oraclelinux-ksh {:port     22027
                     :username "ksh"}
   })

(defn test-ports
  []
  (->> host-ports
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

(defn shell-all []
  (->>
    (for [[host conf] host-ports]
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

#_ (shell-all)

(defn run-all [func]
  (->>
    (for [[host conf] host-ports]
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
