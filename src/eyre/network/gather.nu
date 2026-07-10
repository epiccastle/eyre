print '===hostname==='
try { ^hostname err> /dev/null | complete | get stdout } catch { "" }
print '===ip-addr==='
try { ^ip -o addr err> /dev/null | complete | get stdout } catch { "" }
print '===ip-link==='
try { ^ip -o link err> /dev/null | complete | get stdout } catch { "" }
print '===ip-route==='
try { ^ip route show default err> /dev/null | complete | get stdout } catch { "" }
print '===ifconfig==='
try { ^ifconfig -a err> /dev/null | complete | get stdout } catch { "" }
print '===netstat-route==='
try { ^netstat -rn err> /dev/null | complete | get stdout } catch { "" }
print '===resolv==='
try { if ("/etc/resolv.conf" | path exists) { open /etc/resolv.conf } } catch { "" }
print '===scutil-dns==='
try { ^scutil --dns err> /dev/null | complete | get stdout } catch { "" }
print '===sys-class-net==='
try { ^grep -r "" /sys/class/net/*/ err> /dev/null | complete | get stdout } catch { "" }
print '===proc-net-route==='
try { open /proc/net/route } catch { "" }
print '===proc-net-fib-trie==='
try { open /proc/net/fib_trie } catch { "" }
print '===end==='