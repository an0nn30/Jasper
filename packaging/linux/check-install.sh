#!/bin/sh
# Installs a Jasper .deb or .rpm, checks that every shared library in it resolves, runs the bundled Java
# and removes the package again. CI runs this as root inside a disposable distribution container
# (see .github/workflows/linux-packages.yml); do not run it on a workstation.
set -eu
package=$1
root=/opt/jasper

# Minimal images lack the applications and desktop-directories directories that xdg-desktop-menu needs,
# like headless systems do; the package's own install script must create them.
if [ -e /usr/share/desktop-directories ]; then
    echo "This image already has /usr/share/desktop-directories; the headless install path is not exercised"
fi

case "$package" in
    *.deb)
        export DEBIAN_FRONTEND=noninteractive
        apt-get update -qq
        apt-get install -y -qq "$package"
        remove() { apt-get remove -y -qq jasper >/dev/null; }
        ;;
    *.rpm)
        if command -v dnf >/dev/null; then
            dnf install -y -q findutils "$package"
            remove() { dnf remove -y -q jasper; }
        elif command -v zypper >/dev/null; then
            zypper --non-interactive --quiet install --allow-unsigned-rpm findutils "$package"
            remove() { zypper --non-interactive --quiet remove jasper; }
        else
            echo "No dnf or zypper in this image" >&2
            exit 1
        fi
        ;;
    *)
        echo "Not a .deb or .rpm: $package" >&2
        exit 1
        ;;
esac

test -x "$root/bin/Jasper"
test -x "$root/bin/jasper-watch"
test -x "$root/lib/bin/jasper"
"$root/lib/bin/jasper" --help | grep -q '^Usage: jasper'
"$root/lib/runtime/bin/java" -version
for name in portal gtk3 gtk4; do
    helper="$root/lib/app/desktop-colors/jasper-desktop-$name"
    test -x "$helper"
    if ldd "$helper" | grep -Ei 'libgtk|libadwaita'; then
        echo "Desktop helper links an optional toolkit: $helper" >&2
        exit 1
    fi
done
# Keep stdin open until the response arrives: EOF cancels pending extraction.
probe=$(mktemp -d)
trap 'rm -rf "$probe"' EXIT HUP INT TERM
mkfifo "$probe/input" "$probe/output"
for name in gtk3 gtk4; do
    response=$(timeout 10 env -u DISPLAY -u WAYLAND_DISPLAY sh -c '
        "$1" --protocol=1 <"$2/input" >"$2/output" &
        child=$!
        trap "kill $child 2>/dev/null || true" EXIT
        exec 3>"$2/input"
        exec 4<"$2/output"
        printf "%s\n" "{\"v\":1,\"kind\":\"sample\",\"generation\":7,\"appearance\":\"dark\"}" >&3
        while IFS= read -r response <&4; do
            case "$response" in *\"generation\":7*) break ;; esac
        done
        printf "%s\n" "$response"
        exec 3>&-
        wait "$child"
        trap - EXIT
    ' sh "$root/lib/app/desktop-colors/jasper-desktop-$name" "$probe")
    printf '%s\n' "$response" | grep -q '"kind":"palette"'
    printf '%s\n' "$response" | grep -q '"generation":7'
done
rm -rf "$probe"
trap - EXIT HUP INT TERM

# The JDK's own libraries find libjvm and each other at run time, so ldd may not resolve those;
# anything else unresolved is a dependency the package forgot to declare.
bundled=$(find "$root" -name '*.so*' -exec basename {} \; | sort -u)
missing=$(find "$root" -type f \( -name '*.so*' -o -perm -u+x \) -exec ldd {} \; 2>/dev/null \
    | awk '/not found/ { print $1 }' | sort -u | grep -vxF "$bundled" || true)
if [ -n "$missing" ]; then
    echo "Unresolved shared libraries after installing $package:" >&2
    echo "$missing" >&2
    exit 1
fi

# xdg-desktop-menu uses the first existing applications directory in $XDG_DATA_DIRS.
if ! grep -qs "$root/bin/Jasper" /usr/local/share/applications/*.desktop /usr/share/applications/*.desktop; then
    echo "No desktop menu entry for Jasper under /usr/local/share/applications or /usr/share/applications" >&2
    exit 1
fi

remove
if [ -e "$root/bin/Jasper" ]; then
    echo "Removing the package left $root/bin/Jasper behind" >&2
    exit 1
fi
if grep -qs "$root/bin/Jasper" /usr/local/share/applications/*.desktop /usr/share/applications/*.desktop; then
    echo "Removing the package left its desktop menu entry behind" >&2
    exit 1
fi
echo "Installed, checked and removed $package"
