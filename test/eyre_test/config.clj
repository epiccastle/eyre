(ns eyre-test.config
  (:require [babashka.fs :as fs]))

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

(def selected-hosts
  #{:alpine
    :alpine-dash
    :alpine-fish
    :alpine-zsh
    :amazonlinux
    :amazonlinux-ksh
    :amazonlinux-zsh
    :archlinux
    :archlinux-dash
    :archlinux-fish
    :archlinux-ksh
    :archlinux-nu
    :archlinux-zsh
    :debian
    :debian-dash
    :debian-fish
    :debian-ksh
    :debian-zsh
    :fedora
    :fedora-dash
    :fedora-fish
    :fedora-ksh
    :fedora-nu
    :fedora-zsh
    :freebsd
    :macos
    :netbsd
    :oraclelinux
    :oraclelinux-ksh
    :oraclelinux-zsh
    :rockylinux
    :rockylinux-ksh
    :rockylinux-zsh
    :ubuntu
    :ubuntu-dash
    :ubuntu-fish
    :ubuntu-ksh
    :ubuntu-zsh
    :windows})

(defn select-hosts [pattern]
  (let [pattern-str (name pattern)]
    (set (filter #(fs/match (name %) pattern-str) selected-hosts))))
