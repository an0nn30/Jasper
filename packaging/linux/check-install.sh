#!/bin/sh
# Installs a Jasper .deb or .rpm, checks mandatory shared libraries, runs the bundled Java
# and removes the package again. CI runs this as root inside a disposable distribution container
# (see .github/workflows/linux-packages.yml); do not run it on a workstation.
set -eu
package=$1
runtime=${2:-missing}
case "$runtime" in missing|equipped) ;; *) echo "Expected missing or equipped runtime mode" >&2; exit 1 ;; esac
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
for name in portal gtk3 gtk4 qt5 qt6 kde5 kde6; do
    helper="$root/lib/app/desktop-colors/jasper-desktop-$name"
    test -x "$helper"
    if ldd "$helper" | grep -Ei 'libgtk|libadwaita'; then
        echo "Desktop helper links an optional toolkit: $helper" >&2
        exit 1
    fi
done
# Minimal install must not pull Qt/KF directly or through their optional-only dependency closure.
# All four files still ship; missing ELF runtimes must fail just that child's startup.
if [ "$runtime" = missing ]; then
for name in qt5 qt6 kde5 kde6; do
    helper="$root/lib/app/desktop-colors/jasper-desktop-$name"
    if ! ldd "$helper" | grep -q 'not found'; then
        echo "Minimal image unexpectedly supplies all optional runtimes: $helper" >&2
        exit 1
    fi
    if timeout 3 "$helper" </dev/null > /tmp/jasper-optional.out 2>/tmp/jasper-optional.err; then
        echo "Missing-runtime helper unexpectedly started: $helper" >&2
        exit 1
    fi
    test ! -s /tmp/jasper-optional.out
    grep -q 'shared libraries' /tmp/jasper-optional.err
done
rm -f /tmp/jasper-optional.out /tmp/jasper-optional.err
else
    for name in qt5 qt6 kde5 kde6; do
        if ldd "$root/lib/app/desktop-colors/jasper-desktop-$name" | grep -q 'not found'; then
            echo "Equipped runtime is missing optional libraries for $name" >&2
            exit 1
        fi
    done
fi
# Keep stdin open until the response arrives: EOF cancels pending extraction.
probe=$(mktemp -d)
trap 'rm -rf "$probe"' EXIT HUP INT TERM
mkfifo "$probe/input" "$probe/output"
sources="gtk3 gtk4"
if [ "$runtime" = equipped ]; then sources="$sources qt5 qt6 kde5 kde6"; fi
for name in $sources; do
    response=$(timeout 10 env HOME="$probe" XDG_CONFIG_HOME="$probe/config" XDG_CACHE_HOME="$probe/cache" sh -c '
        case "$3" in gtk*) unset DISPLAY WAYLAND_DISPLAY ;; esac
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
    ' sh "$root/lib/app/desktop-colors/jasper-desktop-$name" "$probe" "$name")
    printf '%s\n' "$response" | grep -q '"kind":"palette"'
    printf '%s\n' "$response" | grep -q '"generation":7'
done
rm -rf "$probe"
trap - EXIT HUP INT TERM

# The JDK's own libraries find libjvm and each other at run time, so ldd may not resolve those;
# anything else unresolved is a dependency the package forgot to declare.
bundled=$(find "$root" -name '*.so*' -exec basename {} \; | sort -u)
missing=$(find "$root" -type f \
    ! -path "$root/lib/app/desktop-colors/jasper-desktop-qt5" \
    ! -path "$root/lib/app/desktop-colors/jasper-desktop-qt6" \
    ! -path "$root/lib/app/desktop-colors/jasper-desktop-kde5" \
    ! -path "$root/lib/app/desktop-colors/jasper-desktop-kde6" \
    \( -name '*.so*' -o -perm -u+x \) -exec ldd {} \; 2>/dev/null \
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
