if command -v greadlink >/dev/null 2>&1; then
  greadlink -f "$(command -v $SHELL)"
elif readlink -f / >/dev/null 2>&1; then
  readlink -f "$(command -v $SHELL)"
else
  # BSD readlink fallback: manually loop-resolve
  target="$(command -v $SHELL)"
  while [ -L "$target" ]; do
    link="$(readlink "$target")"
    case "$link" in
      /*) target="$link" ;;
      *) target="$(dirname "$target")/$link" ;;
    esac
  done
  cd -- "$(dirname -- "$target")" && echo "$(pwd -P)/$(basename -- "$target")"
fi

# Report the path of the currently running shell executable (this
# process), obtained independently of $SHELL. On Linux/BSD this is
# read from /proc/<pid>/exe; elsewhere it falls back to the shell's
# own $0 / PATH lookup.
if [ -L "/proc/$$/exe" ]; then
  readlink "/proc/$$/exe"
elif [ -L "/proc/$$/file" ]; then
  readlink "/proc/$$/file"
elif [ -L /proc/curproc/exe ]; then
  readlink /proc/curproc/exe
else
  case "$0" in
    */*) printf '%s\n' "$0" ;;
    *) command -v "${0#-}" 2>/dev/null ;;
  esac
fi

# Trailing no-op so zsh/ksh do not tail-exec the last readlink above
# (which would make /proc/$$/exe point at readlink instead of the shell).
true
