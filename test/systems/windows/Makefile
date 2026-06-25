ISO_URL := https://software-static.download.prss.microsoft.com/sg/download/888969d5-f34g-4e03-ac9d-1f9786c66749/SERVER_EVAL_x64FRE_en-us.iso

.PHONY: build all

all: build

iso/SERVER_EVAL_x64FRE_en-us.iso:
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "iso/SERVER_EVAL_x64FRE_en-us.iso" "$ISO_URL"

output-winserver-core: iso/SERVER_EVAL_x64FRE_en-us.iso
	packer init windows-server-core.pkr.hcl
	packer build windows-server-core.pkr.hcl
	#debug version
	#PACKER_LOG=1 packer build windows-server-core.pkr.hcl 2>&1 | tee packer-build.log

build: output-winserver-core
