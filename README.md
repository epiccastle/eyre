# eyre

[![Clojars Project](https://img.shields.io/clojars/v/io.epiccastle/eyre.svg)](https://clojars.org/io.epiccastle/eyre)

[![Babashka](https://raw.githubusercontent.com/babashka/babashka/master/logo/badge.svg)](https://github.com/babashka/babashka)

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
    * NetBSD
    * macOS
    * Windows
- Every fact module accepts the same small contract: an executor
  function and a shell map.
- All per-shell collection scripts are embedded, so there are no
  files to install on the target.
- No dependencies [`deps.edn`](deps.edn#L3).

## Quickstart

gather.clj:
```clojure
(ns gather
  (:require [babashka.process :as process]
            [clojure.pprint :as pprint]
            [eyre.core :as eyre]))

(defn local-exec [script]
  (process/shell {:in script
                  :out :string
                  :err :string}
                 "bash"))

(pprint/pprint
  (eyre/gather local-exec))

(shutdown-agents)
```

```bash
clojure -Sdeps '{:deps {io.epiccastle/eyre {:mvn/version "0.1.0"}}}' gather.clj
```
or run under [babashka](https://github.com/babashka/babashka#installation):

```bash
bb -Sdeps '{:deps {io.epiccastle/eyre {:mvn/version "0.1.0"}}}' gather.clj
```

## Installation

### tools.deps

```clojure
io.epiccastle/eyre {:mvn/version "0.1.0"}
```

### leiningen

```clojure
[io.epiccastle/eyre "0.1.0"]
```

## Usage

Full [documentation](https://epiccastle.io/eyre/0.1.0/).

An executor is any function that takes a command string and returns a
map like `{:exit 0 :out "..." :err ""}`. This one runs the
script locally with [babashka/process](https://github.com/babashka/process):

```clojure
(require '[babashka.process :as process]
         '[eyre.core :as eyre])

(defn local-exec [script]
  (process/shell {:in script
                  :out :string
                  :err :string}
                 "bash"))

(eyre/gather local-exec)
```

The executor can just as easily run over SSH. Any transport returning `:exit`, `:out`,
and `:err` will work. Here's an example using [clojuressh](https://github.com/epiccastle/clojuressh).

```clojure
(require '[clojuressh.core :as ssh]
         '[clojuressh.session :as session]
         '[eyre.core :as eyre])

(let [session (ssh/ssh "remotehost.com" {:username "remote-username"})
      exec (fn [script]
             @(ssh/exec session script {:out :string :err :string}))
      facts (eyre/gather exec)]
  (session/disconnect session)
  (:shell facts))
```

### Using individual modules

The same pieces are available separately if you do not need the full
report:

```clojure
(require '[eyre.shell :as shell]
         '[eyre.os :as os]
         '[eyre.users :as users])

(def shell (shell/gather-shell {:exec local-exec}))

(os/gather-os {:exec local-exec :shell shell})
(users/gather-users {:exec local-exec :shell shell})
```

## Output format

The complete map returned by `eyre.core/gather` is documented in
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

Read full test docs: [`docs/testing.md`](docs/testing.md).

Run the tests on babashka with:

```bash
make test-bb
```

On clojure with:

```bash
make test-clojure
```

Or both with:

```bash
make test
```

The test suite includes unit tests on parsing and integration
tests run against a set of local VMs and containers configured in
`test/eyre_test/config.clj`.

## Quote

> The justices interpreted the King's laws; their judgments were entered in long eyre rolls. Bringing the King's law into the districts around the country had a role in creating conformity in legal decisions regardless of geography, and setting precedents for future cases. Thus, the courts of eyre were instrumental in creating English Common Law. The eyres were gradually replaced by assizes and general commissions of Oyer and Terminer in the latter half of the fourteenth century.

-- Jokinen, Anniina. "Justices in Eyre." Luminarium Encyclopedia.

## License

Copyright © 2026 Crispin Wellington

Distributed under the Eclipse Public License version 2.0.
