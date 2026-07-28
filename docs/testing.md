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
- `eyre_test/docker.clj` builds, starts and stops a suite of linux distributions
  with multiple shells each for running tests against.
- `test/eyre_test/config.clj`: Global control of which hosts tests are run againt.
  Maps those machines to `localhost` ports that the tests connect to over SSH.

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

1. Build everything. This takes ages!

```bash
cd test/systems
make
```

This builds the images for every qemu system: freebsd, netbsd, openbsd and windows.

2. Start every built VM in the background on the ports expected by
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
## Docker systems (`eyre_test/docker.clj`)

Start all the docker container images:

```bash
make start-all-docker
```

Stop all the docker container images:

```bash
make stop-all-docker
```

Clean up all the docker container images:

```bash
make cleanup-all-docker
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

## Running the tests

After starting the VMs and docker images you want to do the following from the
project root:

```bash
make test
```

This runs all tests. A full run across all configured test machines would result in:

```
Ran 75 tests containing 1006 assertions.
0 failures, 0 errors.
```

### Running only a subset

You can comment or uncomment hosts in `test/eyre_test/config.clj` with the
`#_` reader macro and the `selected-hosts` / `extra-exclude` variables.

To run only NetBSD, for instance, start just that VM and set `selected-hosts`
to `#{:netbsd}`.

You can exclude sets of machines you don't have running. For example, if you have
the qemu machines running, but dont have the docker instance, exclude the docker
instances from tests with

```clojure
(def extra-exclude #{
                     ;; exclude all the docker hosts
                     :alpine-* :ubuntu* :debian*
                     :fedora* :archlinux* :amazonlinux*
                     :rockylinux* :oraclelinux*
                     })
```

Or the reverse, only running docker, no qemu vms

```clojure
(def extra-exclude #{
                     ;; exclude the qemu vms
                     :windows :freebsd :macos :netbsd
                     })
```
