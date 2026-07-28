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

# Path of the currently running nu executable (this process),
# obtained independently of $SHELL. On Linux /proc/self/exe is a symlink
# to the executable of the running process (nu), so resolving it gives
# the real nu binary. We try nushell's built-in path canonicalization
# first, then external `readlink -f`, and only fall back to the
# resolved $SHELL path as a last resort (e.g. on systems without
# /proc such as BSD/macOS).
let running = (
    try {
        # Linux: /proc/self/exe symlinks to this process's executable.
        try { "/proc/self/exe" | path expand --strict } catch {
            # Fallback: external readlink -f (Linux readlink supports -f).
            if (which readlink | is-not-empty) {
                ^readlink -f /proc/self/exe
            } else { null }
        }
    } catch { null }
) | default $resolved
print $running
