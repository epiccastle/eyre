nu --version
print $env.SHELL

# Try to resolve the real path of $SHELL
let shell_path = (which $env.SHELL | get path.0? | default $env.SHELL)

let resolved = if (which greadlink | is-not-empty) {
    # Use greadlink -f if available (macOS with coreutils)
    ^greadlink -f $shell_path
} else {
    # Use Nu's built-in path canonicalize (resolves symlinks like readlink -f)
    try {
        $shell_path | path expand --strict
    } catch {
        # BSD fallback: manually loop-resolve symlinks
        mut target = $shell_path
        loop {
            let meta = (ls -la $target | get 0)
            if $meta.type != "symlink" { break }

            let link = (readlink $target)  # or: ^readlink $target
            $target = if ($link | str starts-with "/") {
                $link
            } else {
                ($target | path dirname) | path join $link
            }
        }
        $target
    }
}

print $resolved
