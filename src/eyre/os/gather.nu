print '===uname-os==='
print $"s: (^uname -s)"
print $"r: (^uname -r)"
print $"v: (^uname -v)"
print $"m: (^uname -m)"
print '===os-release==='
if ("/etc/os-release" | path exists) { cat /etc/os-release }
print '===lsb-release==='
if ("/etc/lsb-release" | path exists) { cat /etc/lsb-release }
print '===sw-vers==='
if (which sw_vers | is-not-empty) { sw_vers }
