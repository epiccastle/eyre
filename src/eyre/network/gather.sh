echo '===hostname==='
hostname 2>/dev/null
echo '===ip-addr==='
ip -o addr 2>/dev/null
echo '===ip-link==='
ip -o link 2>/dev/null
echo '===ip-route==='
ip route show default 2>/dev/null
echo '===ifconfig==='
ifconfig -a 2>/dev/null
echo '===netstat-route==='
netstat -rn 2>/dev/null
echo '===resolv==='
cat /etc/resolv.conf 2>/dev/null
echo '===scutil-dns==='
scutil --dns 2>/dev/null
echo '===ls-net==='
ls /sys/class/net/ 2>/dev/null
echo '===ubuntu-proc==='
# netdump.sh — dumps interface/link/route/address info as parseable records
# Format: RECORD_TYPE|field1|field2|...

# --- LINK layer info (per interface, from /sys) ---
for ifpath in /sys/class/net/*/; do
    iface=$(basename "$ifpath")
    mac=$(cat "$ifpath/address" 2>/dev/null)
    mtu=$(cat "$ifpath/mtu" 2>/dev/null)
    state=$(cat "$ifpath/operstate" 2>/dev/null)
    carrier=$(cat "$ifpath/carrier" 2>/dev/null)
    echo "LINK|${iface}|mac=${mac}|mtu=${mtu}|state=${state}|carrier=${carrier}"
done

# --- IPv4 routes/subnets per interface (from /proc/net/route, hex little-endian) ---
tail -n +2 /proc/net/route | awk '
function hexval(c,   digits) {
    digits = "0123456789abcdef"
    return index(digits, tolower(c)) - 1
}
function hexbyte2dec(h) {
    return hexval(substr(h,1,1)) * 16 + hexval(substr(h,2,1))
}
function hex2ip(h,   b1,b2,b3,b4) {
    b1 = hexbyte2dec(substr(h,7,2))
    b2 = hexbyte2dec(substr(h,5,2))
    b3 = hexbyte2dec(substr(h,3,2))
    b4 = hexbyte2dec(substr(h,1,2))
    return b1"."b2"."b3"."b4
}
{
    iface = $1
    dest  = hex2ip($2)
    mask  = hex2ip($8)
    print "ROUTE|" iface "|network=" dest "|netmask=" mask
}'

# --- IPv4 local host addresses (from /proc/net/fib_trie, LOCAL entries) ---
# Dedupe since Main: and Local: trees both list local addresses
awk '
/host LOCAL/ { print prev }
{ prev = $0 }
' /proc/net/fib_trie | sed -E 's/^[[:space:]]*[|+]--[[:space:]]*//' | sort -u | \
    awk '{ print "ADDR4|" $0 }'

# --- IPv6 addresses (from /proc/net/if_inet6 — already fixed-width fields) ---
if [ -r /proc/net/if_inet6 ]; then
    awk '{
        addr = $1
        prefixhex = $3
        # manual hex->dec, no strtonum
        n = 0
        for (i=1; i<=length(prefixhex); i++) {
            c = tolower(substr(prefixhex,i,1))
            v = index("0123456789abcdef", c) - 1
            n = n*16 + v
        }
        iface = $6
        formatted = ""
        for (i=1; i<=length(addr); i+=4) {
            formatted = formatted substr(addr,i,4) (i+4<=length(addr) ? ":" : "")
        }
        print "INET6|" iface "|" formatted "|" n
    }' /proc/net/if_inet6
fi
echo '===end==='
