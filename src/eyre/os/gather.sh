echo '===uname-os==='
for f in s r v m; do
  printf '%s:%s\n' "$f" "$(uname -$f)"
done
echo '===os-release==='
if [ -f /etc/os-release ]; then cat /etc/os-release; fi
echo '===lsb-release==='
if [ -f /etc/lsb-release ]; then cat /etc/lsb-release; fi
echo '===sw-vers==='
if command -v sw_vers >/dev/null 2>&1; then sw_vers; fi
