Moray native package
====================

macOS: open the DMG and drag Moray.app to Applications. The current development
build has only a local ad-hoc signature and has no final application icon. It is
not Developer ID signed or notarized, so macOS may require the usual confirmation
for software from an unidentified developer.

Windows: extract the complete Moray folder from the ZIP, then run Moray.exe.
Keep the app, runtime, and launcher files together. No external Java installation
is required on either platform because the matching JetBrains Runtime is bundled.

Moray reads configuration from $HOME/.config/moray on macOS and from
%APPDATA%\moray on Windows. config.example.toml is included beside the program
as a reference and is not installed or copied into your configuration folder.
