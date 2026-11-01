#!/bin/sh
# Keep the pinned JCEF runtime's private-directory lookup, without searching cwd.
set -eu
runtime=$1/lib/runtime/lib
for name in libjcef.so jcef_helper; do
    binary=$runtime/$name
    rpath=$(patchelf --print-rpath "$binary")
    case "$rpath" in
        '.:$ORIGIN') patchelf --force-rpath --set-rpath '$ORIGIN' "$binary" ;;
        '$ORIGIN') ;;
        *) printf 'Unexpected JCEF RPATH in %s: %s\n' "$binary" "$rpath" >&2; exit 1 ;;
    esac
done
