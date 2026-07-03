echo '===hostname==='
hostname 2>/dev/null
echo '===ip-addr==='
ip -o addr 2>/dev/null
echo '===ip-link==='
ip -o link 2>/dev/null
echo '===ip-route==='
ip route show default 2>/dev/null
echo '===ifconfig==='
ifconfig -a 2>/dev/null
echo '===netstat-route==='
netstat -rn 2>/dev/null
echo '===resolv==='
cat /etc/resolv.conf 2>/dev/null
echo '===scutil-dns==='
scutil --dns 2>/dev/null
echo '===end==='
