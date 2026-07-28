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
true
