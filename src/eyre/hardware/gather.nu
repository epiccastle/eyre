print '===uname-hardware==='
print (try { ^uname -m err> /dev/null | complete | get stdout } catch { "" })
print '===cpuinfo==='
print (try { open -r /proc/cpuinfo } catch { "" })
print '===sysctl-a==='
print (try { ^sysctl -a err> /dev/null | complete | get stdout } catch { "" })
print '===sys-class-dmi==='
print (try {
  let v = (if ("/sys/class/dmi/id/sys_vendor" | path exists) { open -r /sys/class/dmi/id/sys_vendor } else { "" })
  let p = (if ("/sys/class/dmi/id/product_name" | path exists) { open -r /sys/class/dmi/id/product_name } else { "" })
  $"sys_vendor: ($v)\nproduct_name: ($p)"
} catch { "" })
print '===meminfo==='
print (try { open -r /proc/meminfo } catch { "" })
print '===lsblk==='
print (try { ^lsblk -b -o NAME,SIZE,ROTA,TYPE,TRAN err> /dev/null | complete | get stdout } catch { "" })
print '===sys-block==='
print (try { ^grep -r "" /sys/block/*/size /sys/block/*/queue/rotational err> /dev/null | complete | get stdout } catch { "" })
print '===diskutil-list==='
print (try { ^diskutil list err> /dev/null | complete | get stdout } catch { "" })
try {
  let disks = (^diskutil list err> /dev/null | complete | get stdout | lines | find -r '^/dev/disk' | each { |it| $it | split row ' ' | first | str replace '/dev/' '' })
  $disks | each { |d|
    print $"===diskutil-info-($d)==="
    print (^diskutil info $d err> /dev/null | complete | get stdout)
  }
} catch { "" }
print '===systemd-detect-virt==='
print (try { ^systemd-detect-virt err> /dev/null | complete | get stdout } catch { "" })
print '===kern-vm-guest==='
print (try { ^sysctl -n kern.vm_guest err> /dev/null | complete | get stdout } catch { "" })
print '===cgroup==='
print (try { open -r /proc/1/cgroup } catch { "" })
print '===dockerenv==='
print (if ("/.dockerenv" | path exists) { "exists" } else { "" })
print '===geom-disk==='
print (try { ^geom disk list err> /dev/null | complete | get stdout } catch { "" })
