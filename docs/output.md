# `eyre.core/gather` output format

`eyre.core/gather` gathers facts about the system reachable through an
executor and returns a single hashmap with the following top-level keys:

| Key          | Type    | Description |
|--------------|---------|-------------|
| `:shell`      | hashmap | Detected shell information. |
| `:os`         | hashmap | Operating system identification. |
| `:hardware`   | hashmap | CPU, memory, disks and virtualization facts. |
| `:users`      | hashmap | User and group information for the executing user. |
| `:filesystem` | hashmap | Mounted filesystems and filesystem features. |
| `:network`    | hashmap | Hostname, network interfaces, routes and DNS. |
| `:paths`      | hashmap | Absolute paths of discovered binaries. |

---

## `:shell`

| Key                | Type    | Description |
|--------------------|---------|-------------|
| `:type`            | keyword | The detected shell family, e.g. `:bash`, `:zsh`, `:fish`, `:powershell`, `:cmd-exe`. |
| `:version`         | string  | Shell version string. |
| `:shell`           | string  | Path to the currently running shell executable (the process running the probes), obtained independently of `$SHELL`, e.g. `"/usr/bin/bash"`. |
| `:login-shell`     | string  | Path to the users login shell as found on the system `$SHELL`, e.g. `"/bin/bash"`. |
| `:canonical-path`  | string  | Canonical, fully resolved path to the login shell, e.g. `"/usr/bin/bash"`. |

---

## `:os`

| Key       | Type    | Description |
|-----------|---------|-------------|
| `:family` | keyword | Broad OS family, e.g. `:linux`, `:macos`, `:freebsd`, `:windows`. |
| `:kernel` | hashmap | Kernel details (see below). |
| `:machine`| string  | Hardware/machine architecture, e.g. `"x86_64"`. |
| `:distro` | hashmap | Linux distribution or OS product details (see below). |

### `:kernel`

| Key       | Type   | Description |
|-----------|--------|-------------|
| `:name`    | string | Kernel name, e.g. `"Linux"`. |
| `:release` | string | Kernel release, e.g. `"6.12.91-1-MANJARO"`. |
| `:version` | string | Full kernel version/build string. |

### `:distro`

| Key           | Type    | Description |
|---------------|---------|-------------|
| `:id`         | keyword | Distribution id, e.g. `:manjaro`, `:ubuntu`. |
| `:name`       | string  | Human-readable distribution name. |
| `:release`    | string? | Distribution release, or `nil` if unavailable. |
| `:codename`   | string? | Distribution codename, or `nil`. |
| `:description`| string  | Longer distribution description. |

A trailing `?` means the value may be `nil`.

---

## `:hardware`

| Key              | Type    | Description |
|------------------|---------|-------------|
| `:cpu`           | hashmap | CPU details. |
| `:memory`        | hashmap | System memory totals. |
| `:disks`         | vector  | List of detected block disks. |
| `:virtualization`| hashmap | Virtualization/container detection. |

### `:cpu`

| Key             | Type        | Description |
|-----------------|-------------|-------------|
| `:model`        | string      | CPU model name. |
| `:cores`        | integer     | Number of logical CPU cores. |
| `:flags`        | set<string> | CPU feature flags. |
| `:architecture` | string      | CPU architecture, e.g. `"x86_64"`. |

### `:memory`

| Key     | Type    | Description |
|---------|---------|-------------|
| `:total`| integer | Total RAM in bytes. |
| `:swap` | integer | Total swap space in bytes. |

### `:disks`

A vector of disk hashmaps:

| Key    | Type    | Description |
|--------|---------|-------------|
| `:name` | string  | Kernel device name, e.g. `"nvme0n1"`. |
| `:size` | integer | Device size in bytes. |
| `:type` | keyword | Class of disk, e.g. `:ssd`, `:nvme`, `:hdd`. |

### `:virtualization`

| Key          | Type    | Description |
|--------------|---------|-------------|
| `:is-virtual?`| boolean | Whether the system appears to be virtualized. |
| `:type`      | keyword?| Detected virtualization/container type, e.g. `:kvm`, `:vmware`, `:docker`, or `nil`. |

---

## `:users`

Information about the user running the executor.

| Key            | Type   | Description |
|----------------|--------|-------------|
| `:gid`         | hashmap| Primary group: `{:id integer :name string}`. |
| `:uid`         | hashmap| User identity: `{:id integer :name string}`. |
| `:groups`      | vector | Group memberships as `{:id integer :name string}` maps. |
| `:group-ids`   | set    | Set of group ids (integers). |
| `:group-names` | set    | Set of group names (strings). |

---

## `:filesystem`

| Key            | Type   | Description |
|----------------|--------|-------------|
| `:filesystems` | vector | Mounted filesystems. |
| `:features`    | hashmap| Optional security/filesystem feature flags. |

### `:filesystems` entry

| Key            | Type    | Description |
|----------------|---------|-------------|
| `:device`      | string  | Device or source for the mount. |
| `:mount-point` | string  | Path where the filesystem is mounted. |
| `:type`        | string  | Filesystem type, e.g. `"ext4"`, `"tmpfs"`. |
| `:options`     | string  | Mount options string. |
| `:size`        | integer?| Total size in bytes, if available. |
| `:used`        | integer?| Used bytes, if available. |
| `:available`   | integer?| Available bytes, if available. |
| `:capacity`    | double? | Used fraction (0.0–1.0), if available. |

### `:features`

This submap contains optional, OS-specific feature detections. The exact keys
vary by platform; for example there may be a `:security` section with an
`:apparmor` map such as `{:enabled true}`.

---

## `:network`

| Key               | Type    | Description |
|-------------------|---------|-------------|
| `:hostname`       | string  | System hostname. |
| `:interfaces`     | hashmap | Network interfaces keyed by interface name. |
| `:default-gateway`| hashmap?| Default IPv4/IPv6 gateway, or `nil`. |
| `:dns`            | hashmap | DNS configuration. |

### `:interfaces` entry

The key is the interface name (string, e.g. `"eno1"`, `"docker0"`, `"lo"`).
The value is a hashmap that always contains `:name` with the same value.

| Key            | Type    | Description |
|----------------|---------|-------------|
| `:name`        | string  | Interface name (matches the map key). |
| `:mac`         | string? | Normalized MAC address, or `nil`. |
| `:mtu`         | integer?| Maximum transmission unit, or `nil`. |
| `:status`      | keyword | `:up`, `:down` or `:unknown`. |
| `:loopback?`   | boolean | `true` if this is the loopback interface. |
| `:ipv4`        | vector  | IPv4 addresses as `{:address string :prefix integer}`. |
| `:ipv6`        | vector  | IPv6 addresses as `{:address string :prefix integer}`. |
| `:peer-index`  | string? | For virtual-ethernet pairs, the peer interface index, e.g. `"if2"`. |

### `:default-gateway`

| Key         | Type   | Description |
|-------------|--------|-------------|
| `:address`  | string | Gateway IP address. |
| `:interface`| string | Name of the outgoing interface. |

### `:dns`

| Key            | Type   | Description |
|----------------|--------|-------------|
| `:nameservers` | vector | IP addresses of configured nameservers (strings). |
| `:search`      | vector | DNS search suffixes (strings). |

---

## `:paths`

A hashmap of discovered executable paths. The keys are binary names as
keywords and the values are absolute filesystem paths as strings.

| Key              | Type   | Description |
|------------------|--------|-------------|
| `:bash`          | string | Example: `"/usr/bin/bash"`. |
| `:cat`           | string | Example: `"/usr/bin/cat"`. |
| `:<binary-name>` | string | One key per discovered binary. |

Only binaries found on the system are present. The exact set of keys depends
on what is installed and visible in the target environment.
