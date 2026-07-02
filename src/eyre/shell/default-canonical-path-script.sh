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
