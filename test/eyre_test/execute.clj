(ns eyre-test.execute
  (:require [clojure.string :as str]
            [eyre-test.utils :as utils]
            [medley.core :as medley]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]
            [clojuressh.user-info :as user-info]))

(def host-ports
  {
   ;; qemu hosts
   :windows     {:port 22001
                 :username "Administrator"}

   ;; openbsd doesnt boot, WARNING: / was not properly unmounted
   #_#_:openbsd     22002

   :netbsd      {:port 22003}
   :freebsd     {:port 22004}
   :macos       {:port 22005}

   ;; docker hosts
   :alpine      {:port 22020}
   :ubuntu      {:port 22021}
   :debian      {:port 22022}
   :fedora      {:port 22023}
   :archlinux   {:port 22024}
   :amazonlinux {:port 22025}
   :rockylinux  {:port 22026}
   :oraclelinux {:port 22027}})

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

#_(clojuressh.agent/set-debug-fn
 (fn [_level message]
   (binding [*out* *err*]
     (println message))))

(defn process-ps [[header line]]
  (let [command-column-index (-> header
                                 str/trim
                                 (str/split #"\s+")
                                 (->> (map str/lower-case)
                                      (keep-indexed (fn [n title]
                                                      (when (#{"cmd" "command"} title)
                                                        n))))
                                 first)]
    (-> line
        str/trim
        (str/split #"\s+")
        (nth command-column-index))))

#_ (process-ps '("  PID TTY           TIME CMD"
                 "  908 ??         0:00.00 sh -c echo B:$BASH_VERSION:Z:$ZSH_VERSION:F:$FISH_VERSION:K:$KSH_VERSION\\012echo ps:\\012ps -p $$ || echo no-ps\\012echo sh:\\012if command -v greadlink >/dev/null 2>&1; then\\012  greadlink -f \"$(command -v sh)\"\\012elif readlink -f / >/dev/null 2>&1; then\\012  readlink -f \"$(command -v sh)\"\\012else\\012  # BSD readlink fallback: manually loop-resolve\\012  target=\"$(command -v sh)\"\\012  while [ -L \"$target\" ]; do\\012    link=\"$(readlink \"$target\")\"\\012    case \"$link\" in\\012      /*) target=\"$link\" ;;\\012      *) target=\"$(dirname \"$target\")/$link\" ;;\\012    esac\\012  done\\012  cd -- \"$(dirname -- \"$target\")\" && echo \"$(pwd -P)/$(basename -- \"$target\")\"\\012fi\\012"))
#_ (process-ps '(" PID TT  STAT    TIME COMMAND"
   "4529  -  Ss   0:00.00 sh -c echo B:$BASH_VERSION:Z:$ZSH_VERSION:F:$FISH_VERSION:K:$KSH_VERSION\\necho ps:\\nps -p $$ || echo no-ps\\necho sh:\\nif command -v greadlink >/dev/null 2>&1; then\\n  greadlink -f \"$(command -v sh)\"\\nelif readlink -f / >/dev/null 2>&1; then\\n  readlink -f \"$(command -v sh)\"\\nelse\\n  # BSD readlink fallback: manually loop-resolve\\n  target=\"$(command -v sh)\"\\n  while [ -L \"$target\" ]; do\\n    link=\"$(readlink \"$target\")\"\\n    case \"$link\" in\\n      /*) target=\"$link\" ;;\\n      *) target=\"$(dirname \"$target\")/$link\" ;;\\n    esac\\n  done\\n  cd -- \"$(dirname -- \"$target\")\" && echo \"$(pwd -P)/$(basename -- \"$target\")\"\\nfi\\n"))

(defn process-version-line [version-line]
  (-> version-line
      (str/split #":")
      (->> (partition 2)
           (keep (fn [[k v]]
                  (when (pos? (count v))
                    [({"B" :bash
                       "Z" :zfs
                       "F" :fish
                       "K" :ksh} k)
                     (str/trim v)]))))
      first))


#_ (process-version-line "B:3.2.57(1)-release:Z::F::K:")
#_ (process-version-line "B::Z::F::K:")

(defn process-powershell [version-lines]
  (let [[header _ version] (-> version-lines
                               str/trim
                               (str/split #"\r\n|\n\r|\r|\n"))
        header (-> header
                   str/trim
                   (str/split #"\s+")
                   (->> (map str/lower-case)
                        (map keyword)))
        version-parts (-> version
                          str/trim
                          (str/split #"\s+"))
        version (->> (map vector header version-parts)
                     (into {}))
        ]
    (str (:major version) "." (:minor version) "." (:build version) "." (:revision version))))

#_ (process-powershell "\r\nMajor  Minor  Build  Revision\r\n-----  -----  -----  --------\r\n5      1      20348  558     \r\n\r\n\r\n")

(defn determine-shell [{:keys [exec]}]
  (let [{:keys [exit out err]} (exec "echo %COMSPEC%\r\nWrite-Output powershell\r\necho $0\r\n")]
    (assert (zero? exit) (str "shell determination script 1 exited non zero: " exit " " err))
    (let [[line-1 line-2 & remain] (str/split out #"\r\n|\n\r|\r|\n")
          first-guess (cond
                        (not= line-1 "%COMSPEC%") :cmd.exe
                        (= line-2 "powershell") :powershell
                        :else (keyword line-2))]
      (case first-guess
        :cmd.exe
        first-guess

        :powershell
        (let [{:keys [exit out err]} (exec "echo $PSVersionTable.PSVersion")]
          [:powershell (process-powershell out)])

        ;; bash like shell
        (let [{:keys [exit out err]} (exec "echo B:$BASH_VERSION;Z:$ZSH_VERSION:F:$FISH_VERSION:K:$KSH_VERSION
echo shell:$SHELL
echo ps:
ps -p $$ || echo no-ps
echo sh:
if command -v greadlink >/dev/null 2>&1; then
  greadlink -f \"$(command -v $SHELL)\"
elif readlink -f / >/dev/null 2>&1; then
  readlink -f \"$(command -v $SHELL)\"
else
  # BSD readlink fallback: manually loop-resolve
  target=\"$(command -v $SHELL)\"
  while [ -L \"$target\" ]; do
    link=\"$(readlink \"$target\")\"
    case \"$link\" in
      /*) target=\"$link\" ;;
      *) target=\"$(dirname \"$target\")/$link\" ;;
    esac
  done
  cd -- \"$(dirname -- \"$target\")\" && echo \"$(pwd -P)/$(basename -- \"$target\")\"
fi
")]
          (assert (zero? exit) (str "shell determination script 2 exited non zero: " exit " " err))
          (let [[[versions shell] _ ps _ [sh-readline]] (->> (str/split out #"\r\n|\n\r|\r|\n")
                                                       (partition-by #{"ps:" "sh:"})
                                                       )
                shell (second (str/split shell #"shell:"))
                ps (if (= ["no-ps"] ps)
                     nil
                     (process-ps ps))
                versions (process-version-line versions)]
            [shell versions ps sh-readline]))))))

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
                                                         {:username "root"
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

#_(run-all determine-shell)
