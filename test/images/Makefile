PASSWORD := "root-access-please"

all: FreeBSD-15.1-RELEASE-amd64-ufs-base.qcow2 FreeBSD-15.1-RELEASE-amd64-zfs-base.qcow2 openbsd-base.qcow2

clean:
	-rm FreeBSD-15.1-RELEASE-amd64-ufs-base.qcow2 FreeBSD-15.1-RELEASE-amd64-zfs-base.qcow2

clean-downloads:
	-rm FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz

FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz:
	wget https://download.freebsd.org/releases/VM-IMAGES/15.1-RELEASE/amd64/Latest/FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz

FreeBSD-15.1-RELEASE-amd64-ufs-base.qcow2: FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz
	xz -dk FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz
	qemu-system-x86_64 -enable-kvm -m 1024 -drive file=FreeBSD-15.1-RELEASE-amd64-ufs.qcow2,format=qcow2 -netdev user,id=net0 -device virtio-net-pci,netdev=net0 -vnc :0 -serial unix:/tmp/freebsd-0.sock,server,nowait -daemonize
	sleep 30
	vncdo -s localhost:0 type "root"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "echo 'console=\"comconsole\"' > /boot/loader.conf"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "echo 'sshd_enable=\"YES\"' >> /etc/rc.conf"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "echo 'PermitRootLogin yes' >> /etc/ssh/sshd_config"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "passwd"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "${PASSWORD}"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "${PASSWORD}"
	vncdo -s localhost:0 key enter
	vncdo -s localhost:0 type "shutdown -p now"
	vncdo -s localhost:0 key enter
	mv FreeBSD-15.1-RELEASE-amd64-ufs.qcow2 FreeBSD-15.1-RELEASE-amd64-ufs-base.qcow2

FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz:
	wget https://download.freebsd.org/releases/VM-IMAGES/15.1-RELEASE/amd64/Latest/FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz

FreeBSD-15.1-RELEASE-amd64-zfs-base.qcow2: FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz
	xz -dk FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz
	qemu-system-x86_64 -enable-kvm -m 1024 -drive file=FreeBSD-15.1-RELEASE-amd64-zfs.qcow2,format=qcow2 -netdev user,id=net0  -device virtio-net-pci,netdev=net0 -vnc :1 -serial unix:/tmp/freebsd-1.sock,server,nowait -daemonize
	sleep 30
	vncdo -s localhost:1 type "root"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "echo 'console=\"comconsole\"' > /boot/loader.conf"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "echo 'sshd_enable=\"YES\"' >> /etc/rc.conf"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "echo 'PermitRootLogin yes' >> /etc/ssh/sshd_config"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "passwd"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "${PASSWORD}"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "${PASSWORD}"
	vncdo -s localhost:1 key enter
	vncdo -s localhost:1 type "shutdown -p now"
	vncdo -s localhost:1 key enter
	mv FreeBSD-15.1-RELEASE-amd64-zfs.qcow2 FreeBSD-15.1-RELEASE-amd64-zfs-base.qcow2

openbsd-min.qcow2:
	wget https://github.com/hcartiaux/openbsd-cloud-image/releases/download/v7.8_2025-10-22-09-25/openbsd-min.qcow2

downloads: FreeBSD-15.1-RELEASE-amd64-ufs.qcow2.xz FreeBSD-15.1-RELEASE-amd64-zfs.qcow2.xz openbsd-min.qcow2

openbsd-seed.iso: cloudinit/meta-data cloudinit/user-data
	genisoimage -output openbsd-seed.iso \
		-volid cidata \
		-joliet -rock \
		cloudinit/meta-data cloudinit/user-data

openbsd-base.qcow2: openbsd-min.qcow2 openbsd-seed.iso
	cp openbsd-min.qcow2 openbsd-base.qcow2
	qemu-system-x86_64 -enable-kvm -m 1024 -drive file=openbsd-base.qcow2,format=qcow2 -drive file=openbsd-seed.iso,format=raw,media=cdrom -netdev user,id=net0,hostfwd=tcp::9876-:22 -device virtio-net-pci,netdev=net0 -vnc :2 -serial unix:/tmp/qemu-0.sock,server,nowait -daemonize
	./setup-openbsd.sh

# socat - UNIX-CONNECT:/tmp/qemu-0.sock
# killall qemu-system-x86_64
