PARTITIONS=DEFAULT
COMPONENTS="base debug"
BSDINSTALL_LOG="/tmp/install.log"

#!/bin/sh
sysrc ifconfig_DEFAULT=DHCP
sysrc sshd_enable=YES
sysrc -f /etc/ssh/sshd_config PermitRootLogin=yes
echo "root-access-please" | pw usermod root -h 0
shutdown -r now
