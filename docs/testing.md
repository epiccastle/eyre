# Testing eyre

The test suite has two parts:

1. **Parser unit tests** in `test/eyre_test/*_test.clj`. These run locally with
   no extra dependencies.
2. **Integration tests** that run `eyre.core/gather` (and the individual
   modules) against real operating systems. These need the VMs described
   below.

This document explains how to build and run the OS test machines.

---

## Layout

```text
test/
├── systems/         # Packer/QEMU images for each OS family
│   ├── Makefile     # Orchestrates all subdirectories
│   ├── freebsd/
│   ├── macos/
│   ├── netbsd/
│   ├── openbsd/
│   └── windows/
└── eyre_test/
    ├── config.clj   # Maps host names to local SSH ports
    ├── shell_test.clj
    └── ...
```

- `test/systems` uses HashiCorp Packer to install operating systems from ISOs
  into QEMU images.
- `test/eyre_test/config.clj` maps those machines to `localhost` ports that
  the tests connect to over SSH.

---

## Prerequisites

For the Packer/QEMU systems you will need:

- `qemu-system-x86_64` with KVM enabled
- `packer`
- `curl`
- `vncviewer` (optional, useful for debugging stuck installs)

All commands below assume you run them from the project root.

---

## Packer/QEMU systems (`test/systems`)

Each subdirectory builds one operating system image from an install ISO.

Build everything:

```bash
cd test/systems
make
```

This builds the images for every system that has a `Makefile`.

Start every built VM in the background on the ports expected by
`test/eyre_test/config.clj`:

```bash
cd test/systems
make run
```

Stop all of them:

```bash
make kill
```

Wipe all build artifacts:

```bash
make clean-build
```

You can also run the top-level Makefile against a single subdirectory:

```bash
make -C test/systems/freebsd run
make -C test/systems/netbsd  kill
make -C test/systems/windows build
```

### Per-system notes

| System  | Packer template                     | Output image                              | Forwarded SSH port | Notes |
|---------|-------------------------------------|-------------------------------------------|--------------------|-------|
| FreeBSD | `test/systems/freebsd/freebsd.pkr.hcl` | `output-freebsd/freebsd.qcow2`            | 22004              | See `test/systems/freebsd/README.md` for ISO URL and VNC tips. |
| NetBSD  | `test/systems/netbsd/netbsd.pkr.hcl`  | `output-netbsd/netbsd.qcow2`              | 22003              | See `test/systems/netbsd/README.md`. The boot command is tuned for NetBSD 10.1 sysinst. |
| OpenBSD | `test/systems/openbsd/openbsd.pkr.hcl`| `output-openbsd/openbsd.qcow2`            | 22002              | There is currently a boot/fsck issue that keeps this image commented out in `config.clj`. |
| Windows | `test/systems/windows/windows-server-core.pkr.hcl` | `output-winserver-core/winserver-core.qcow2` | 22001   | Server Core image keeps the qcow2 small; see `test/systems/windows/README.md` for image-index advice. |
| macOS   | none                                | `test/systems/macos/macOS-Simple-KVM/*.qcow2` | 22005          | Manual setup; see `test/systems/macos/README.md`. |

### macOS

macOS is the odd one out: there is no Packer template. Use one of the
KVM projects referenced in `test/systems/macos/README.md` (e.g.
[macOS-Simple-KVM](https://github.com/foxlet/macOS-Simple-KVM)) to build the
disk images, then run:

```bash
cd test/systems/macos
make run
```

That simply executes `macOS-Simple-KVM/basic.sh`. Once the VM is up, enable
`sshd` and root login with the password `root-access-please` so it matches
the rest of the test environment.

---

## Connecting the tests to the VMs

`test/eyre_test/config.clj` maps host names to the local ports used by the
QEMU user-mode networking forwards above:

```clojure
:windows    {:port 22001 :username "Administrator"}
:openbsd    {:port 22002}
:netbsd     {:port 22003}
:freebsd    {:port 22004}
:macos      {:port 22005}
```

The integration tests build SSH executors from these maps in
`test/eyre_test/shell_test.clj`:

```clojure
(defn make-executor-fn [conf]
  (fn [command]
    (let [session (ssh/ssh "localhost"
                           (merge {:username "root"
                                   :password "root-access-please"
                                   :strict-host-key-checking false}
                                  conf))
          result @(ssh/exec session command
                            {:out :string :err :string})]
      (session/disconnect session)
      result)))
```

So once the VMs are running, the tests connect to `localhost:<port>` using
those credentials.

### If you change a port

Update `test/eyre_test/config.clj` to match the port you forward. For example,
if you run the FreeBSD image manually on a different port:

```clojure
:freebsd {:port 2222}
```

### Running only a subset

You can comment or uncomment hosts in `test/eyre_test/config.clj` with the
`#_` reader macro and the `selected-hosts` / `extra-exclude` variables.

To run only NetBSD, for instance, start just that VM and set `selected-hosts`
to `#{:netbsd}`, or pass an in-code selector when using the helper
functions.

---

## Running the tests

After starting the VMs you want:

```bash
clojure -M:test
```

This runs both the mock-based parser tests and the SSH-backed integration
tests for every host listed in `config.clj`.

---

## Quick reference

```bash
# Base images
make -C test/images

# Packer systems
make -C test/systems
make -C test/systems run
make -C test/systems kill

# Individual systems
make -C test/systems/freebsd build
make -C test/systems/freebsd run
make -C test/systems/netbsd  run
make -C test/systems/windows run
make -C test/systems/macos   run

# Tests
clojure -M:test
```
