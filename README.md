# eyre

> The justices interpreted the King's laws; their judgments were entered in long eyre rolls. Bringing the King's law into the districts around the country had a role in creating conformity in legal decisions regardless of geography, and setting precedents for future cases. Thus, the courts of eyre were instrumental in creating English Common Law. The eyres were gradually replaced by assizes and general commissions of Oyer and Terminer in the latter half of the fourteenth century.

-- Jokinen, Anniina. "Justices in Eyre." Luminarium Encyclopedia.

**eyre** is a Clojure library for probing an operating system and user
environment for facts. It detects the shell, OS, hardware, users,
mounted filesystems, network configuration, and available binaries,
and works over any transport that can execute commands and return
`:exit`, `:out`, and `:err`.

## Features

- Supported shells:
    * bash
    * zsh
    * sh
    * dash
    * ksh
    * busybox/ash
    * fish
    * nushell
    * PowerShell
    * cmd.exe
- Supported Operating Systems:
    * Linux
    * FreeBSD
    * OpenBSD
    * macOS
    * Windows
- Every fact module accepts the same small contract: an executor
  function and a shell map.
- All per-shell collection scripts are embedded, so there are no
  files to install on the target.

## Installation

Add eyre to your `deps.edn`:

```clojure
{:deps {io.epiccastle/eyre {:mvn/version "LATEST"}}}
```

Or use a Git coordinate while developing:

```clojure
{:deps {io.epiccastle/eyre {:git/url "https://github.com/epiccastle/eyre"
                           :git/sha "..."}}}
```

## Usage

An executor is any function that takes a command string and returns a
map like `{:exit 0 :out "..." :err ""}`. The easiest one runs the
script locally with [babashka/process](https://github.com/babashka/process):

```clojure
(require '[babashka.process :as process]
         '[eyre.core :as eyre])

(defn local-exec [script]
  (process/shell {:cmd "bash"
                  :in script
                  :out :string
                  :err :string}))

(eyre/determine local-exec)
```

The executor can just as easily run over SSH. Each module uses only
`{:keys [exec shell]}`, so any transport returning `:exit`, `:out`,
and `:err` will work.

```clojure
(require '[clojuressh.core :as ssh]
         '[clojuressh.session :as session]
         '[eyre.core :as eyre])

(defn make-executor [{:keys [host username password port]}]
  (fn [command]
    (let [session (ssh/ssh host {:username username
                                 :password password
                                 :port     (or port 22)
                                 :strict-host-key-checking false})
          result  @(ssh/exec session command {:out :string
                                              :err :string})]
      (session/disconnect session)
      result)))

(def ssh-exec (make-executor {:host "remote.example.com"
                              :username "root"
                              :password "secret"
                              :port 22}))

(eyre/determine ssh-exec)
```

### Using individual modules

The same pieces are available separately if you do not need the full
report:

```clojure
(require '[eyre.shell :as shell]
         '[eyre.os :as os]
         '[eyre.users :as users])

(def shell (shell/determine-shell {:exec local-exec}))

(os/determine-os {:exec local-exec :shell shell})
(users/determine-users {:exec local-exec :shell shell})
```

## Output format

The complete map returned by `eyre.core/determine` is documented in
[`docs/output.md`](docs/output.md).

## Modules

| Namespace           | Fact domain                                |
|---------------------|--------------------------------------------|
| `eyre.shell`        | Shell type, version, and canonical path    |
| `eyre.os`           | Operating system, kernel, and distribution |
| `eyre.hardware`     | CPU, memory, disks, and virtualization     |
| `eyre.users`        | Current uid, gid, and group membership     |
| `eyre.filesystem`   | Mounted filesystems and security features  |
| `eyre.network`      | Hostname, interfaces, gateway, and DNS     |
| `eyre.bins`         | Resolved paths for common binaries         |

## Development

Run the tests with:

```bash
clojure -M:test
```

The test suite includes mock-based parser unit tests and integration
tests run against a set of local VMs and containers configured in
`test/eyre_test/config.clj`.

## License

Copyright © 2026 Crispin Wellington

Distributed under the Eclipse Public License version 2.0.
