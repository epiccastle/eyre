#!/bin/bash
expect << 'EOF'
set timeout 120
spawn socat - UNIX-CONNECT:/tmp/qemu-0.sock
expect "login:"
send "root\r"
expect ":"
send "root-access-please\r"
expect "# "
send "shutdown -p now\r"
expect eof
EOF
