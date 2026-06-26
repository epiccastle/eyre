ISO_URL := https://cdn.netbsd.org/pub/NetBSD/NetBSD-10.1/images/NetBSD-10.1-amd64.iso

.PHONY: build all clean clean-build

all: build

iso/NetBSD-10.1-amd64.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/NetBSD-10.1-amd64.iso" "$(ISO_URL)"

output-netbsd/netbsd.qcow2: iso/NetBSD-10.1-amd64.iso
	packer init netbsd.pkr.hcl
	packer build netbsd.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build netbsd.pkr.hcl 2>&1 | tee packer-build.log

build: output-netbsd/netbsd.qcow2

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-netbsd/
