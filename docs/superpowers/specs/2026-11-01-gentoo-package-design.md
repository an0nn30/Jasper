# Gentoo source package

Approved by Dustin on 2026-11-01: source build with optional USE flags.

Provide a repository-local Gentoo overlay for `app-misc/jasper-workstation`, initially keyworded `~arm64`. Build the merged source snapshot be557f600 with Gradle and the pinned JCEF JBR. Portage fetches checksum-pinned source, build tools and Maven dependencies before compilation; compilation uses an isolated cache and offline Gradle. Install the app image and private runtime at `/opt/jasper-workstation`, a `jasper` command, desktop entry and icon.

Expose `gtk`, `gtk4`, `qt6`, and `kde`, default off; KDE requires Qt6. Map enabled flags to required Gradle integrations and disabled flags to off. Qt5/KDE5 remain off because the current Gentoo tree lacks their SDKs. Core GLib/X11/runtime libraries remain dependencies; disabling GTK/Qt does not promise removal of those distinct libraries.

Preserve upstream licenses and declare package license requirements. Do not bypass Portage network sandboxing, launch the GUI, train AOT caches, or change system Java. Keep the running development checkout and user settings intact. Install the tested GTK-enabled package on this XFCE VM. Document overlay setup, license acceptance, USE flags, dependency lock refresh, and source-version updates.

Acceptance: a clean offline Portage build, installed package ownership and launchers, packaged helper selection for all-off and GTK configurations, and package verification. Qt6/KDE dependencies and selection must be checked; unsupported architectures remain unkeyworded. Independent review precedes completion. User performs final graphical acceptance.
