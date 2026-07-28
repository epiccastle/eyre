echo '===uname-hardware==='
uname -m 2>/dev/null
echo '===cpuinfo==='
cat /proc/cpuinfo 2>/dev/null
echo '===sysctl-a==='
sysctl -a 2>/dev/null
echo '===sys-class-dmi==='
if [ -f /sys/class/dmi/id/sys_vendor ]; then
  echo "sys_vendor: $(cat /sys/class/dmi/id/sys_vendor 2>/dev/null)"
  echo "product_name: $(cat /sys/class/dmi/id/product_name 2>/dev/null)"
fi
echo '===meminfo==='
cat /proc/meminfo 2>/dev/null
echo '===lsblk==='
lsblk -b -o NAME,SIZE,ROTA,TYPE,TRAN 2>/dev/null
echo '===sys-block==='
grep -r "" /sys/block/*/size /sys/block/*/queue/rotational 2>/dev/null
echo '===diskutil-list==='
diskutil list 2>/dev/null
if command -v diskutil >/dev/null 2>&1; then
  for d in $(diskutil list | awk '/^\/dev\/disk/ {print $1}' | sed 's|/dev/||'); do
    echo "===diskutil-info-$d==="
    diskutil info "$d" 2>/dev/null
  done
fi
echo '===systemd-detect-virt==='
systemd-detect-virt 2>/dev/null
echo '===kern-vm-guest==='
sysctl -n kern.vm_guest 2>/dev/null
echo '===cgroup==='
cat /proc/1/cgroup 2>/dev/null
echo '===dockerenv==='
if [ -f /.dockerenv ]; then echo "exists"; fi
echo '===geom-disk==='
if command -v geom >/dev/null 2>&1; then geom disk list; fi
true
