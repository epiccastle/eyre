(ns eyre.bins
  (:require [clojure.string :as str]
            [eyre.utils :as utils :refer [newlines]]))

;; the binaries whose paths we want to discover on a given host. this
;; list is the single source of truth; the per-shell detection scripts
;; below are generated from it so it never has to be duplicated across
;; the various shell dialects.

(def bins
  [
   "bash" "ksh" "zsh" "csh" "tcsh" "fish"
   "dash" "sh" "sash" "yash" "zsh"
   "stat" "ls" "id" "sudo" "lsattr"
   "file" "touch" "chacl" "chown" "chgrp" "chattr"
   "chmod" "cp" "cat" "cut" "printf" "find"
   "head" "tail" "sysctl" "true" "false"
   "date" "sed" "grep" "awk" "curl" "mkdir"
   "groupadd" "groupmod" "groupdel"
   "useradd" "usermod" "userdel"
   "wget" "git" "tar" "rsync" "bzip2" "aws"
   "bzcat" "bunzip2" "gzip" "gunzip" "zip"
   "unzip" "uname" "lsb_release"
   "md5sum" "md5"
   "sha1sum" "sha1"
   "sha224sum" "sha224"
   "sha256sum" "sha256"
   "sha384sum" "sha384"
   "sha512sum" "sha512"
   "scp" "service"
   "apt" "apt-get" "dpkg" "yum" "rpm" "pkg" "apt-key"
   "systemctl" "journalctl"
   ])

(defn- which-script-posix
  "bash/zsh/sh/dash/ksh/busybox -- `command -v` is a posix builtin so it
  is available even on minimal systems where the external `which` is
  not. missing binaries yield an empty substitution, producing a bare
  `<bin>:` line that the parser drops."
  []
  (apply str (map #(format "echo %s: $(command -v %s)\n" % %) bins)))

(defn- which-script-fish
  "fish -- uses fish's `(...)` command substitution."
  []
  (apply str (map #(format "echo %s: (command -v %s)\n" % %) bins)))

(defn- which-script-csh
  "csh/tcsh -- `command` is not a builtin here, so fall back to the
  external `which` with backtick substitution. csh aborts on a non-zero
  exit from a substitution, so we finish with `exit 0`."
  []
  (str (apply str (map #(format "echo %s: `which %s`\n" % %) bins))
       "\n"
       "exit 0"))

(defn- which-script-nu
  "nushell -- uses the builtin `which` which returns a table whose
  `path` column holds the resolved binary path. missing commands return
  an empty table, so the cell-path lookup is wrapped in a `try`."
  []
  (str "for b in [" (str/join " " (map #(str "\"" % "\"") bins)) "] {\n"
       "  print $\"($b): (try { (which $b).path.0 } catch { '' })\"\n"
       "}\n"))

(defn- which-script-powershell
  "powershell -- `Get-Command` resolves the binary, with `.Source`
  giving its full path. -ErrorAction SilentlyContinue keeps a missing
  command from aborting the loop."
  []
  (str "$bins = @(" (str/join "," (map #(str "'" % "'") bins)) ")\n"
       "foreach ($b in $bins) {\n"
       "  $c = Get-Command $b -ErrorAction SilentlyContinue\n"
       "  if ($c) { Write-Output \"$b: $($c.Source)\" } else { Write-Output \"$b: \" }\n"
       "}\n"))

(defn- which-script-cmd
  "cmd.exe -- `where` is a builtin that prints the full path of each
  match. `2>nul` suppresses its \"not found\" diagnostics; the `for /f`
  only echoes when a path is actually produced, so missing binaries are
  simply omitted."
  []
  (str "@echo off\n"
       "for %b in (" (str/join " " bins) ") do @(for /f \"delims=\" %p in ('where %b 2^>nul') do @echo %b: %p)\n"))

(defn make-which
  "Builds the shell-specific script that prints `<bin>: <path>` for
  every binary in `bins`. `shell-type` is the `:type` returned by
  `eyre.shell/determine-shell` (e.g. `:bash`, `:fish`, `:nu`,
  `:powershell`, `:cmd-exe`)."
  [shell-type]
  (case shell-type
    :fish        (which-script-fish)
    :nu          (which-script-nu)
    :powershell  (which-script-powershell)
    :cmd-exe     (which-script-cmd)
    #{:csh :tcsh} (which-script-csh)
    ;; any other posix-like shell
    (which-script-posix)))

(defn- process-paths
  "Parses `<bin>: <path>` lines into a keyword->string map. empty paths
  are dropped. the value is split on the first `:` only (with limit 2)
  so windows paths containing a drive-letter colon are preserved; the
  surrounding whitespace is trimmed rather than whitespace-split so that
  paths containing spaces are kept intact."
  [out]
  (->> (str/split out newlines)
       (map str/trim)
       (keep (fn [line]
               (when (seq line)
                 (let [[k v] (str/split line #":\s*" 2)]
                   (when (and k (seq v))
                     [(keyword k) (str/trim v)])))))
       (into {})))

(defn determine-paths
  "Runs the appropriate `which` script for the shell described by
  `shell` (as produced by `eyre.shell/determine-shell`) and returns a
  map of binary keyword -> resolved path for every binary in `bins`
  that is available on the host."
  [{:keys [exec shell]}]
  (let [shell-type (:type shell)
        {:keys [exit out err]} (exec (make-which shell-type))]
    (assert (zero? exit) (str "bins determination script exited non zero: " exit " " err))
    (process-paths out)))
