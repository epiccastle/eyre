PARTITIONS=DEFAULT
COMPONENTS="base debug"
BSDINSTALL_LOG="/tmp/install.log"

#!/bin/sh
sysrc ifconfig_DEFAULT=DHCP
sysrc sshd_enable=YES
sed -i'' -E 's/^[[:blank:]]*#?[[:blank:]]*PermitRootLogin.*/PermitRootLogin yes/' /etc/ssh/sshd_config
sed -i'' -E 's/^[[:blank:]]*#?[[:blank:]]*PasswordAuthentication.*/PasswordAuthentication yes/' /etc/ssh/sshd_config
echo "root-access-please" | pw usermod root -h 0
shutdown -r now
