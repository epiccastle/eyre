(ns eyre-test.shell-test
  (:require [clojure.test :refer :all]
            [eyre-test.config :as config]
            [eyre.shell :as shell]
            [eyre-test.utils :as utils]
            [clojuressh.core :as ssh]
            [clojuressh.session :as session]))

(defn make-executor-fn [conf]
  (fn [command]
    (let [session (ssh/ssh "localhost" (merge
                                         {:username (:username conf "root")
                                          :password "root-access-please"
                                          :strict-host-key-checking false}
                                         conf))
          result @(ssh/exec session command {:out :string
                                             :err :string})]
      (session/disconnect session)
      result)))

(defn run-all [func & [selector]]
  (->> (config/select-hosts selector)
       sort
       (keep (fn [host]
               (let [conf (config/host-ports host)]
                 (prn host)
                 [host
                  (func {:exec (make-executor-fn conf)})])))
       (filter identity)
       (into {})))

#_ (run-all shell/gather-shell {:only #{:windows}})

(deftest windows-shell-tests
  (let [conf (config/host-ports :windows)
        executor (make-executor-fn conf)
        initial-shell (:type (shell/gather-shell {:exec executor}))]
    (when (= :powershell initial-shell)
      ;; switch system to cmd.exe
      (executor "New-ItemProperty -Path \"HKLM:\\SOFTWARE\\OpenSSH\" -Name DefaultShell -Value \"C:\\Windows\\System32\\cmd.exe\" -PropertyType String -Force"))
    (is (= (shell/gather-shell {:exec executor})
           {:type :cmd-exe,
            :version "10.0.20348.587",
            :login-shell "C:\\Windows\\system32\\cmd.exe",
            :path "C:\\Windows\\system32\\cmd.exe"}))
    ;; switch system to powershell
    (executor "reg add \"HKLM\\SOFTWARE\\OpenSSH\" /v DefaultShell /t REG_SZ /d \"C:\\Windows\\System32\\WindowsPowerShell\\v1.0\\powershell.exe\" /f")
    (is (= (shell/gather-shell {:exec executor})
           {:type :powershell
            :version "5.1.20348.558"
            :login-shell "c:\\windows\\system32\\windowspowershell\\v1.0\\powershell.exe"
            :canonical-path "c:\\windows\\system32\\windowspowershell\\v1.0\\powershell.exe"}))
    ;; revery to cmd.exe if needed
    (when (= :cmd.exe initial-shell)
      (executor "New-ItemProperty -Path \"HKLM:\\SOFTWARE\\OpenSSH\" -Name DefaultShell -Value \"C:\\Windows\\System32\\cmd.exe\" -PropertyType String -Force"))))

(deftest gather-shell-test
  (is (=
        (run-all shell/gather-shell {:exclude #{:windows}})
        (config/filter-hashmap
          {:exclude #{:windows}}
          {:alpine
           {:type :busybox,
            :version "v1.35.0",
            :login-shell "/bin/ash",
            :canonical-path "/bin/busybox"}
           :alpine-dash
           {:type :dash,
            :version "0.5.11.5-r1 description:",
            :login-shell "/usr/bin/dash",
            :canonical-path "/usr/bin/dash"}
           :alpine-fish
           {:type :fish,
            :version "3.4.1",
            :login-shell "/usr/bin/fish",
            :canonical-path "/usr/bin/fish"}
           :alpine-zsh
           {:type :zsh,
            :version "5.8.1",
            :login-shell "/bin/zsh",
            :canonical-path "/bin/zsh"}
           :amazonlinux
           {:type :bash,
            :version "5.2.15(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :amazonlinux-ksh
           {:type :ksh,
            :version "Version AJM 93u+ 2012-08-01",
            :login-shell "/usr/bin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :amazonlinux-zsh
           {:type :zsh,
            :version "5.9",
            :login-shell "/usr/bin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :archlinux
           {:type :bash,
            :version "5.3.15(1)-release",
            :login-shell "/usr/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :archlinux-dash
           {:type :dash,
            :version "0.5.13.4-1",
            :login-shell "/usr/sbin/dash",
            :canonical-path "/usr/bin/dash"}
           :archlinux-fish
           {:type :fish,
            :version "4.7.1",
            :login-shell "/usr/sbin/fish",
            :canonical-path "/usr/bin/fish"}
           :archlinux-ksh
           {:type :ksh,
            :version "Version A 2020.0.0",
            :login-shell "/usr/sbin/ksh",
            :canonical-path "/usr/bin/ksh"}
           :archlinux-nu
           {:type :nu,
            :version "0.113.1",
            :login-shell "/usr/sbin/nu",
            :canonical-path "/usr/bin/nu"}
           :archlinux-zsh
           {:type :zsh,
            :version "5.9.1",
            :login-shell "/usr/sbin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :debian
           {:type :bash,
            :version "5.2.37(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :debian-dash
           {:type :dash,
            :version "0.5.12-12",
            :login-shell "/usr/bin/dash",
            :canonical-path "/usr/bin/dash"}
           :debian-fish
           {:type :fish,
            :version "4.0.2",
            :login-shell "/usr/bin/fish",
            :canonical-path "/usr/bin/fish"}
           :debian-ksh
           {:type :ksh,
            :version "Version AJM 93u+m/1.0.10 2024-08-01",
            :login-shell "/usr/bin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :debian-zsh
           {:type :zsh,
            :version "5.9",
            :login-shell "/usr/bin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :fedora
           {:type :bash,
            :version "5.3.9(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :fedora-dash
           {:type :dash,
            :version "0.5.13.1-3.fc44",
            :login-shell "/usr/sbin/dash",
            :canonical-path "/usr/bin/dash"}
           :fedora-fish
           {:type :fish,
            :version "4.6.0",
            :login-shell "/usr/sbin/fish",
            :canonical-path "/usr/bin/fish"}
           :fedora-ksh
           {:type :ksh,
            :version "Version AJM 93u+m/1.0.10 2024-08-01",
            :login-shell "/usr/sbin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :fedora-nu
           {:type :nu,
            :version "0.99.1",
            :login-shell "/usr/sbin/nu",
            :canonical-path "/usr/bin/nu"}
           :fedora-zsh
           {:type :zsh,
            :version "5.9",
            :login-shell "/usr/sbin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :freebsd
           {:type :sh,
            :version nil,
            :login-shell "/bin/sh",
            :canonical-path "/bin/sh"}
           :macos
           {:type :bash,
            :version "3.2.57(1)-release",
            :login-shell "/bin/sh",
            :canonical-path "/bin/sh"}
           :netbsd
           {:type :sh,
            :version nil,
            :login-shell "/bin/sh",
            :canonical-path "/bin/sh"}
           :oraclelinux
           {:type :bash,
            :version "5.2.26(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :oraclelinux-ksh
           {:type :ksh,
            :version "Version AJM 93u+m/1.0.10 2024-08-01",
            :login-shell "/usr/bin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :oraclelinux-zsh
           {:type :zsh,
            :version "5.9",
            :login-shell "/usr/bin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :rockylinux
           {:type :bash,
            :version "5.1.8(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :rockylinux-ksh
           {:type :ksh,
            :version "Version AJM 93u+m/1.0.6 2023-06-13",
            :login-shell "/usr/bin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :rockylinux-zsh
           {:type :zsh,
            :version "5.8",
            :login-shell "/usr/bin/zsh",
            :canonical-path "/usr/bin/zsh"}
           :ubuntu
           {:type :bash,
            :version "5.2.21(1)-release",
            :login-shell "/bin/bash",
            :canonical-path "/usr/bin/bash"}
           :ubuntu-dash
           {:type :dash,
            :version "0.5.12-6ubuntu5",
            :login-shell "/usr/bin/dash",
            :canonical-path "/usr/bin/dash"}
           :ubuntu-fish
           {:type :fish,
            :version "3.7.0",
            :login-shell "/usr/bin/fish",
            :canonical-path "/usr/bin/fish"}
           :ubuntu-ksh
           {:type :ksh,
            :version "Version AJM 93u+m/1.0.8 2024-01-01",
            :login-shell "/usr/bin/ksh",
            :canonical-path "/usr/bin/ksh93"}
           :ubuntu-zsh
           {:type :zsh,
            :version "5.9",
            :login-shell "/usr/bin/zsh",
            :canonical-path "/usr/bin/zsh"}}))))
