(ns eyre.bins
  (:require [clojure.string :as str]
            [eyre.utils :as utils :refer [newlines]]))

;; the binaries whose paths we want to discover on a given host. this
;; list is the single source of truth; the per-shell detection scripts
;; below are generated from it so it never has to be duplicated across
;; the various shell dialects.

(def ^{:doc "All the binaries searched for by default by `gather-paths`"} bins
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
  not. For shell builtins (e.g. `true`, `false`, `printf`) `command -v`
  returns the bare name rather than a path; in that case we search PATH
  directory-by-directory to find the external binary. the PATH search
  uses `tr` + `while read` rather than IFS word-splitting so it works
  in zsh (which does not split unquoted variables by default)."
  [bins]
  (str "PATH=\"/usr/local/sbin:/usr/sbin:/sbin:$PATH\"\n"
       "export PATH\n"
       "for b in " (str/join " " bins) "; do\n"
       "  p=$(command -v \"$b\" 2>/dev/null)\n"
       "  case \"$p\" in /*) ;; *) p=$(echo \"$PATH\" | tr ':' '\\n' | while read d; do [ -x \"$d/$b\" ] && { echo \"$d/$b\"; break; }; done) ;; esac\n"
       "  echo \"$b: $p\"\n"
       "done\n"))

(defn- which-script-fish
  "fish -- uses fish's `(...)` command substitution. sbin directories
  are prepended to PATH so that system binaries (e.g. `sysctl`,
  `useradd`) are found even when the shell's default PATH omits them."
  [bins]
  (str "set -x PATH /usr/local/sbin /usr/sbin /sbin $PATH\n"
       (apply str (map #(format "echo %s: (command -v %s)\n" % %) bins))))

(defn- which-script-csh
  "csh/tcsh -- `command` is not a builtin here, so fall back to the
  external `which` with backtick substitution. csh aborts on a non-zero
  exit from a substitution, so we finish with `exit 0`."
  [bins]
  (str (apply str (map #(format "echo %s: `which %s`\n" % %) bins))
       "\n"
       "exit 0"))

(defn- which-script-nu
  "nushell -- nushell's `which` builtin returns only the first match
  (the builtin, not the external binary), so we search PATH directly to
  find the real binary path. `$env.PATH` gives the search directories;
  we check each candidate path with `ls` (wrapped in `try` since `ls`
  errors on non-existent paths)."
  [bins]
  (str "let search_dirs = [ /usr/local/sbin /usr/sbin /sbin ] ++ $env.PATH\n"
       "for b in [" (str/join " " (map #(str "\"" % "\"") bins)) "] {\n"
       "  let paths = ($search_dirs | each { |dir| $\"($dir)/($b)\" } | where { |f| (try { (ls $f | is-not-empty) } catch { false }) })\n"
       "  print $\"($b): (if ($paths | is-empty) { '' } else { $paths | first })\"\n"
       "}\n"))

(defn- which-script-powershell
  "powershell -- `Get-Command` resolves the binary, with `.Source`
  giving its full path. -ErrorAction SilentlyContinue keeps a missing
  command from aborting the loop."
  [bins]
  (str "$bins = @(" (str/join "," (map #(str "'" % "'") bins)) ")\n"
       "foreach ($b in $bins) {\n"
       "  $c = Get-Command $b -ErrorAction SilentlyContinue\n"
       "  if ($c) { Write-Output \"${b}: $($c.Source)\" } else { Write-Output \"${b}: \" }\n"
       "}\n"))

(defn- which-script-cmd
  "cmd.exe -- `where` is a builtin that prints the full path of each
  match. `2>nul` suppresses its \"not found\" diagnostics; the `for /f`
  only echoes when a path is actually produced, so missing binaries are
  simply omitted."
  [bins]
  (str "@echo off\n"
       "for %b in (" (str/join " " bins) ") do @(for /f \"delims=\" %p in ('where %b 2^>nul') do @echo %b: %p)\n"))

(defn- make-which
  "Builds the shell-specific script that prints `<bin>: <path>` for
  every binary in `bins`. `shell-type` is the `:type` returned by
  `eyre.shell/gather-shell` (e.g. `:bash`, `:fish`, `:nu`,
  `:powershell`, `:cmd-exe`)."
  [shell-type bins]
  (case shell-type
    :fish        (which-script-fish bins)
    :nu          (which-script-nu bins)
    :powershell  (which-script-powershell bins)
    :cmd-exe     (which-script-cmd bins)
    #{:csh :tcsh} (which-script-csh bins)
    ;; any other posix-like shell
    (which-script-posix bins)))

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

(defn gather-paths
  "Discovers the absolute paths of common binaries on the host
  reachable via `exec`.

  ## Arguments

  Takes a map with:

  - `:exec` - an executor function that runs a script string on the
    target host and returns `{:exit int :out string :err string}`.
  - `:shell` - the shell map returned by `eyre.shell/gather-shell`;
    its `:type` selects which generated lookup script is run (see
    `make-which`).
  - `:bins` - optional list of binaries to search for. If absent uses
    this namespace's `bin` var.

  ## Returns

  A map of binary name keyword -> absolute path string for every
  binary in `eyre.bins/bins` that was found on the host, e.g.

  ```clojure
  {:bash \"/usr/bin/bash\"
   :git \"/usr/bin/git\"
   :systemctl \"/usr/bin/systemctl\"}
  ```

  Binaries that are not installed (or not on the searched PATH) are
  simply absent from the map, so the exact key set varies between
  hosts. The probed binary list covers shells, core file/text
  utilities, hash and checksum tools, user/group administration
  tools, downloaders, and common package/service managers.

  The lookup prepends `/usr/local/sbin`, `/usr/sbin` and `/sbin` to
  the search PATH (on posix shells) so system binaries are found even
  when the login shell's default PATH omits them. Shell builtins that
  have no external binary (e.g. `true` in some shells) may still be
  resolved by searching PATH directory-by-directory; those with no
  on-disk counterpart are omitted.

  ## Example

  ```clojure
  (bins/gather-paths {:exec local-exec :shell shell})
  ;; => {:bash \"/usr/bin/bash\" :cat \"/usr/bin/cat\" ...}
  ```"
  [{:keys [exec shell] :as args}]
  (let [shell-type (:type shell)
        {:keys [exit out err]} (exec (make-which shell-type (args :bins bins)))]
    (assert (zero? exit) (str "bins gathering script exited non zero: " exit " " err))
    (process-paths out)))
