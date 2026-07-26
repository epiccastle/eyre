echo '===mount==='
mount 2>/dev/null
echo '===df==='
df -P -k 2>/dev/null
echo '===selinux==='
if command -v getenforce >/dev/null 2>&1; then
  getenforce 2>/dev/null
fi
if [ -f /sys/fs/selinux/enforce ]; then
  echo "enforce:$(cat /sys/fs/selinux/enforce 2>/dev/null)"
fi
echo '===apparmor==='
if [ -d /sys/kernel/security/apparmor ]; then
  echo "loaded:yes"
  if command -v aa-status >/dev/null 2>&1; then
    aa-status 2>/dev/null
  fi
fi
echo '===sip==='
if command -v csrutil >/dev/null 2>&1; then
  csrutil status 2>/dev/null
fi
echo '===securelevel==='
sysctl -n kern.securelevel 2>/dev/null
