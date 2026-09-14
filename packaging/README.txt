Jasper native package
====================

macOS: open the DMG and drag Jasper.app to Applications. The current development
build has only a local ad-hoc signature and has no final application icon. It is
not Developer ID signed or notarized, so macOS may require the usual confirmation
for software from an unidentified developer.

Windows: extract the complete Jasper folder from the ZIP, then run Jasper.exe.
Keep the app, runtime, and launcher files together. No external Java installation
is required on either platform because the matching JetBrains Runtime is bundled.

Jasper reads configuration from $HOME/.config/jasper on macOS and from
%APPDATA%\jasper on Windows. config.example.toml is included beside the program
as a reference and is not installed or copied into your configuration folder.
