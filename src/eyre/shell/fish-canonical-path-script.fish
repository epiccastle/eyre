set target (command -v $SHELL)
while test -L $target
    set link (readlink $target)
    if string match -q '/*' $link
        set target $link
    else
        set target (dirname $target)/$link
    end
end
cd (dirname $target); and echo (pwd -P)/(basename $target)

# Report the path of the currently running shell executable (this
# process), obtained independently of $SHELL. On Linux read from
# /proc/$fish_pid/exe; elsewhere fall back to a PATH lookup.
if test -L /proc/$fish_pid/exe
    readlink /proc/$fish_pid/exe
else
    command -v fish 2>/dev/null
end
true
