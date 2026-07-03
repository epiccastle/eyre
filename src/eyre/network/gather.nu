print '===hostname==='
hostname
print '===ip-addr==='
^ip -o addr err> /dev/null | default ""
print '===ip-link==='
^ip -o link err> /dev/null | default ""
print '===ip-route==='
^ip route show default err> /dev/null | default ""
print '===ifconfig==='
^ifconfig -a err> /dev/null | default ""
print '===netstat-route==='
^netstat -rn err> /dev/null | default ""
print '===resolv==='
if ("/etc/resolv.conf" | path exists) { cat /etc/resolv.conf }
print '===scutil-dns==='
^scutil --dns err> /dev/null | default ""
print '===end==='
