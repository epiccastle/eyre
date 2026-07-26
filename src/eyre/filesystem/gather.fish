echo '===mount==='
mount 2>/dev/null
echo '===df==='
df -P -k 2>/dev/null
echo '===selinux==='
if type -q getenforce
  getenforce 2>/dev/null
end
if test -f /sys/fs/selinux/enforce
  echo "enforce:"(cat /sys/fs/selinux/enforce 2>/dev/null)
end
echo '===apparmor==='
if test -d /sys/kernel/security/apparmor
  echo "loaded:yes"
  if type -q aa-status
    aa-status 2>/dev/null
  end
end
echo '===sip==='
if type -q csrutil
  csrutil status 2>/dev/null
end
echo '===securelevel==='
sysctl -n kern.securelevel 2>/dev/null
