echo '===uname-os==='
for f in s r v m
    printf '%s:%s\n' $f (uname -$f)
end
echo '===os-release==='
if test -f /etc/os-release
    cat /etc/os-release
end
echo '===lsb-release==='
if test -f /etc/lsb-release
    cat /etc/lsb-release
end
echo '===sw-vers==='
if type -q sw_vers
    sw_vers
end
true
