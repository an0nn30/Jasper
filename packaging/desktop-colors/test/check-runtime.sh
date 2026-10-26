#!/bin/sh
# Disposable container only: test baseline-built files, never rebuild against a newer compiler/SDK.
set -eu
if command -v apt-get >/dev/null; then
    export DEBIAN_FRONTEND=noninteractive
    apt-get update -qq
    apt-get install -y -qq --no-install-recommends cmake python3 xvfb xauth dbus locales fonts-dejavu-core libqt5widgets5 libqt5dbus5 libkf5configwidgets5 libqt6widgets6 libqt6dbus6 qt6-qpa-plugins libglib2.0-0
    targets="qt5 qt6 kde5"
    # Ubuntu22 supplies the Qt6.2 floor, not KF6/Qt6.5. Its KDE6 absence is a
    # legitimate platform capability skip; the Fedora all-four cell is mandatory.
    echo 'SKIP kde6: Ubuntu22 system runtime predates Qt6.5/KF6; minimum KDE6 runtime evidence remains a separate release requirement'
    runtime=optional
else
    dnf install -y -q cmake python3 xorg-x11-server-Xvfb xorg-x11-xauth dbus-daemon glibc-langpack-de dejavu-sans-fonts qt5-qtbase-gui qt6-qtbase-gui kf5-kconfigwidgets kf6-kcolorscheme glib2
    targets="qt5 qt6 kde5 kde6"
    runtime=required
fi
localedef -i de_DE -f UTF-8 de_DE.UTF-8
unset LD_LIBRARY_PATH QT_PLUGIN_PATH QT_QPA_PLATFORM_PLUGIN_PATH
python3 packaging/desktop-colors/test/verify-qt-package.py jasper-app/build/desktop-helpers --runtime "$runtime"
for target in $targets; do
    xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' dbus-run-session --config-file="$PWD/packaging/desktop-colors/test/themes/private-bus.conf" -- ctest --test-dir "jasper-app/build/desktop-colors/cmake/$target" --verbose --output-on-failure
done

if [ "$runtime" = required ]; then
    xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' dbus-run-session --config-file="$PWD/packaging/desktop-colors/test/themes/private-bus.conf" -- python3 packaging/desktop-colors/test/idle_helpers_test.py jasper-app/build/desktop-helpers
    package=$(ls jasper-dist/*.rpm)
    xvfb-run -a -s '-screen 0 1024x768x24 -nolisten tcp' dbus-run-session --config-file="$PWD/packaging/desktop-colors/test/themes/private-bus.conf" -- sh packaging/linux/check-install.sh "$package" equipped
fi
