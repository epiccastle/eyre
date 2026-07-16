print '===mount==='
print (try { ^mount err> /dev/null | complete | get stdout } catch { "" })
print '===df==='
print (try { ^df -P -k err> /dev/null | complete | get stdout } catch { "" })
print '===selinux==='
print (try { ^getenforce err> /dev/null | complete | get stdout } catch { "" })
print (try { if ("/sys/fs/selinux/enforce" | path exists) { $"enforce: (open /sys/fs/selinux/enforce)" } } catch { "" })
print '===apparmor==='
print (try { if ("/sys/kernel/security/apparmor" | path exists) { "loaded:yes" } } catch { "" })
print (try { ^aa-status err> /dev/null | complete | get stdout } catch { "" })
print '===sip==='
print (try { ^csrutil status err> /dev/null | complete | get stdout } catch { "" })
print '===securelevel==='
print (try { ^sysctl -n kern.securelevel err> /dev/null | complete | get stdout } catch { "" })
print '===end==='
