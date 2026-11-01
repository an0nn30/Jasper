#!/bin/sh
# Linux ELF regression: a working-directory library must not override private runtime libraries.
set -eu
root=$(CDPATH= cd -- "$(dirname -- "$0")/../../.." && pwd)
fixture=$(mktemp -d "${TMPDIR:-/tmp}/jasper-rpath.XXXXXX")
trap 'rm -rf "$fixture"' EXIT HUP INT TERM
lib="$fixture/image/lib/runtime/lib"
mkdir -p "$lib" "$fixture/hostile"
printf '%s\n' 'int answer(void) { return 42; }' > "$fixture/library.c"
printf '%s\n' 'int answer(void) { return 7; }' > "$fixture/hostile.c"
printf '%s\n' 'extern int answer(void); int jcef(void) { return answer(); }' > "$fixture/jcef.c"
printf '%s\n' 'extern int jcef(void); int main(void) { return jcef() == 42 ? 0 : 1; }' > "$fixture/helper.c"
cc -shared -fPIC "$fixture/library.c" -o "$lib/libfixture.so"
cc -shared -fPIC "$fixture/hostile.c" -o "$fixture/hostile/libfixture.so"
cc -shared -fPIC "$fixture/jcef.c" -L"$lib" -lfixture -Wl,--disable-new-dtags '-Wl,-rpath,.:$ORIGIN' -o "$lib/libjcef.so"
cc "$fixture/helper.c" -L"$lib" -ljcef -Wl,--disable-new-dtags '-Wl,-rpath,.:$ORIGIN' -o "$lib/jcef_helper"
# Establish the vulnerability, with loader overrides absent.
unset LD_LIBRARY_PATH LD_PRELOAD
cd "$fixture/hostile"
if "$lib/jcef_helper"; then
    echo 'Fixture did not demonstrate current-directory injection' >&2
    exit 1
fi
sh "$root/packaging/gentoo/app-misc/jasper-workstation/files/sanitize-rpath.sh" "$fixture/image"
"$lib/jcef_helper"
for binary in libjcef.so jcef_helper; do
    test "$(patchelf --print-rpath "$lib/$binary")" = '$ORIGIN'
    readelf -d "$lib/$binary" | grep -F '(RPATH)' | grep -F '[$ORIGIN]' >/dev/null
    if readelf -d "$lib/$binary" | grep -F '(RUNPATH)' >/dev/null; then
        echo 'RPATH semantics unexpectedly changed' >&2
        exit 1
    fi
done
# An unexpected upstream path must require review rather than silent replacement.
patchelf --force-rpath --set-rpath '/unexpected:$ORIGIN' "$lib/libjcef.so"
if sh "$root/packaging/gentoo/app-misc/jasper-workstation/files/sanitize-rpath.sh" "$fixture/image" >"$fixture/rejected.log" 2>&1; then
    echo 'Unexpected RPATH was accepted' >&2
    exit 1
fi
grep -F 'Unexpected JCEF RPATH' "$fixture/rejected.log" >/dev/null
printf '%s\n' 'RPATH loader regression passed'
