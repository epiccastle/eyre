print '===uname-os==='
print $"s: (try { ^uname -s err> /dev/null | complete | get stdout } catch { '' })"
print $"r: (try { ^uname -r err> /dev/null | complete | get stdout } catch { '' })"
print $"v: (try { ^uname -v err> /dev/null | complete | get stdout } catch { '' })"
print $"m: (try { ^uname -m err> /dev/null | complete | get stdout } catch { '' })"
print '===os-release==='
print (try { open -r /etc/os-release } catch { "" })
print '===lsb-release==='
print (try { open -r /etc/lsb-release } catch { "" })
print '===sw-vers==='
print (try { ^sw_vers err> /dev/null | complete | get stdout } catch { "" })
