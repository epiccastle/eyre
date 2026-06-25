#!/bin/sh
# FreeBSD installation script for Packer.
# Fetched over HTTP and run from the bsdinstall Shell.
#
# Strategy: let bsdinstall script handle partitioning and distribution
# extraction, then configure the installed system (SSH, root password, etc.)
# while the target is still mounted at /mnt.

set -e

echo "==> Starting FreeBSD installation"

# ── networking ────────────────────────────────────────────────────────────────
ifconfig vtnet0 up 2>/dev/null || true
dhclient vtnet0 2>/dev/null || true

# ── write bsdinstall config and run it ────────────────────────────────────────
cat > /tmp/bsdinstall.cfg <<'EOF'
DISTRIBUTIONS="base.txz kernel.txz"
PARTITIONS="vtbd0"
nonInteractive="YES"
BSDINSTALL_SKIP_HOSTNAME="YES"
BSDINSTALL_SKIP_UPDATE="YES"
BSDINSTALL_SKIP_PKG="YES"
BSDINSTALL_SKIP_SERVICES="YES"
BSDINSTALL_SKIP_HARDENING="YES"
BSDINSTALL_SKIP_FINALVERIFICATION="YES"
EOF

bsdinstall script /tmp/bsdinstall.cfg

# ── post-install: bsdinstall leaves the target mounted at /mnt ────────────────

# /etc/rc.conf — networking, SSH, basic services
cat > /mnt/etc/rc.conf <<EOF
hostname="freebsd-packer"
ifconfig_vtnet0="DHCP"
sshd_enable="YES"
dumpdev="AUTO"
EOF

# /etc/resolv.conf — DNS resolution
cat > /mnt/etc/resolv.conf <<EOF
nameserver 8.8.8.8
nameserver 8.8.4.4
EOF

# Root password
echo 'freebsd' | chroot /mnt pw usermod root -h 0

# SSH: allow root login with password (lab image)
sed -i '' \
    -e 's/^#*PermitRootLogin.*/PermitRootLogin yes/' \
    -e 's/^#*PasswordAuthentication.*/PasswordAuthentication yes/' \
    /mnt/etc/ssh/sshd_config

# Generate SSH host keys now so sshd can start on first boot
chroot /mnt ssh-keygen -A

# Flag for Packer provisioner
touch /mnt/root/ssh-ready

# ── done — unmount and reboot into the installed system ───────────────────────
echo "==> Installation complete. Rebooting from disk..."
umount /mnt
sync
reboot
