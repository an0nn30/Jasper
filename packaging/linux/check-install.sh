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
test -x "$root/lib/bin/jasper"
"$root/lib/bin/jasper" --help | grep -q '^Usage: jasper'
"$root/lib/runtime/bin/java" -version

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
