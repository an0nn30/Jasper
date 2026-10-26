Jasper native package
====================

macOS: open the DMG and drag Jasper.app to Applications. Copy it with Finder or
ditto: a copy that resets file modification times, such as cp -R, still runs but
loses Jasper's start-up cache. Jasper is signed ad hoc only. It is not Developer
ID signed or notarized, so macOS may require the usual confirmation for software
from an unidentified developer. By default Jasper keeps running in the
background after Cmd+Q, which only closes its windows: choose Quit Jasper
Completely in its menu bar item before replacing Jasper.app with a newer version.

Windows setup: run Jasper-<version>-windows-<x64|aarch64>-setup.exe (aarch64 for
Windows on Arm) to install for your user, choose the folder and shortcuts, then
launch Jasper from the Start menu. By default Jasper keeps running after its last
window closes: choose Quit Jasper in its notification-area icon before installing
an upgrade. Uninstall through Windows Installed apps; your settings and plugin
data are preserved. Jasper is not Authenticode-signed, so Windows may warn about
an unknown publisher.

Windows portable: extract the complete Jasper folder from the ZIP, then run Jasper.exe.
Keep the app, runtime, and launcher files together.

Linux: install the package for your distribution and architecture, for example
"sudo apt install ./Jasper-<version>-linux-amd64.deb" or
"sudo dnf install ./Jasper-<version>-linux-x86_64.rpm" (arm64/aarch64 for ARM).
Jasper installs to /opt/jasper-workstation and adds a menu entry; the launcher is
/opt/jasper-workstation/bin/Jasper. Remove it with your package manager (package "jasper-workstation");
your settings and plugin data are preserved.

No external Java installation is required on any platform because the matching
JetBrains Runtime is bundled.

Jasper keeps its settings, plugins and logs in Jasper home: $HOME/.config/jasper
on macOS, $XDG_CONFIG_HOME/jasper (default $HOME/.config/jasper) on Linux and
%APPDATA%\jasper on Windows. A Jasper started with the JASPER_HOME environment
variable set uses that folder instead. Settings live in config.toml there;
config.example.toml, beside this file, is a reference and is not installed or
copied into Jasper home.

To give Java options, such as -Xmx4g for a larger heap, put one per line in
jasper.vmoptions in Jasper home, or use Settings > Appearance & Behavior >
System Settings > Java Virtual Machine. They apply at the next launch.

The jasper command: Settings > Appearance & Behavior > System Settings >
Command line > Install puts it on your PATH (macOS links /usr/local/bin/jasper,
Linux links ~/.local/bin/jasper, Windows adds Jasper's bin folder to your Path).
"jasper" opens a Jasper window, "jasper editor FILE" opens a file in Jasper's
Editor, and "jasper --help" lists the commands.
