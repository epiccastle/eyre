ISO_URL := https://cdn.openbsd.org/pub/OpenBSD/7.9/amd64/install79.iso
ISO_FILE := iso/install79.iso

.PHONY: build all clean clean-build

all: build

$(ISO_FILE):
	mkdir -p iso
	curl -fL --retry 3 --retry-delay 5 -o "$(ISO_FILE)" "$(ISO_URL)"

build: $(ISO_FILE)
	# Add packer build command placeholder if needed
	echo "Built for openbsd"

clean:
	-rm -f packer-build.log

clean-build:
	-rm -rf output-openbsd/
