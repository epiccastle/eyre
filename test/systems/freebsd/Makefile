ISO_URL := https://download.freebsd.org/releases/ISO-IMAGES/15.1/FreeBSD-15.1-RELEASE-amd64-disc1.iso

.PHONY: build all clean clean-build

all: build

iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso" "$(ISO_URL)"

output-freebsd: iso/FreeBSD-15.1-RELEASE-amd64-disc1.iso
	packer init freebsd.pkr.hcl
	packer build freebsd.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build freebsd.pkr.hcl 2>&1 | tee packer-build.log

build: output-freebsd

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-freebsd/
