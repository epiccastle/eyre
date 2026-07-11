print '===hostname==='
print (try { ^hostname err> /dev/null | complete | get stdout } catch { "" })
print '===proc-sys-kernel-hostname==='
print (try { open /proc/sys/kernel/hostname } catch { "" })
print '===ip-addr==='
print (try { ^ip -o addr err> /dev/null | complete | get stdout } catch { "" })
print '===ip-link==='
print (try { ^ip -o link err> /dev/null | complete | get stdout } catch { "" })
print '===ip-route==='
print (try { ^ip route show default err> /dev/null | complete | get stdout } catch { "" })
print '===ifconfig==='
print (try { ^ifconfig -a err> /dev/null | complete | get stdout } catch { "" })
print '===netstat-route==='
print (try { ^netstat -rn err> /dev/null | complete | get stdout } catch { "" })
print '===resolv==='
print (try { if ("/etc/resolv.conf" | path exists) { open /etc/resolv.conf } } catch { "" })
print '===scutil-dns==='
print (try { ^scutil --dns err> /dev/null | complete | get stdout } catch { "" })
print '===sys-class-net==='
print (try { ^grep -r "" /sys/class/net/*/ err> /dev/null | complete | get stdout } catch { "" })
print '===proc-net-route==='
print (try { open /proc/net/route } catch { "" })
print '===proc-net-fib-trie==='
print (try { open /proc/net/fib_trie } catch { "" })
print '===end==='