# Bundled Cygwin verification — 2026-10-20

Branch: `codex/cygwin-probes`. Approved implementation uses prebuilt POSIX PTY relays; ordinary installation requires the JDK and bundled artifacts, without PowerShell, Python, GnuPG, WiX or a native compiler. WiX remains a build dependency for installer generation.

## Seed and distribution

The checked-in lock contains 114 packages, 91 corresponding package source archives and 333 verified artifacts (510,489,120 bytes). Installed size is 831,247,849 bytes. The retained signed metadata matches the pinned packages. Setup 2.937 tagged sources, COPYING and provenance, relay sources/binaries/notices, and package notices are included. `seed-licenses.json` records upstream declarations; `_autorebase` has no explicit declaration and its notice includes the supplied scripts and README.

Seed SHA-512: `8093925a9b40473df99b261dd8a233a177c7f138f442ca77afde67a5c53a6cf6eaad72ea6e10e5a64b0dd8e22988ef7f9e0809e1a8bba3ead8e8b349c0b5152c`.

Default Java-only acquisition/staging passed, including an actual missing pinned archive download. The corresponding-source ZIP contains all 210 required source/notice entries. Release verification binds native and interactive acceptance to this seed and fails closed for missing evidence.

## Automated checks

Focused checks pass: 29 manifest assertions, 35 portable lifecycle assertions, 30 user-install assertions and packaging graph/support checks. Earlier production launch probes pass on native ARM64 and x64-emulated JBR, including twenty immediate resize/input cycles. The earlier full headless check has three pre-existing editor failures: ThemeMigrationCoverageTest, EditorDocumentPreservationTest and SettingsPagesMockTest. Independent implementation and distribution reviews completed; reported fixes have regression coverage.

Windows VM: Windows 11 ARM64, JBR 25.0.4.1 b583.48. The real seed provisioned successfully offline. Restricted-PATH `packageWindowsUser` passes with only JBR and Windows system directories. Native installer acceptance exposed WiX 3 MAX_PATH limits; installer generation now stages a short temporary app image. Provisioning diagnostics persist under the owned environment for installer failures. MSI also reproduced denied module inspection of protected installer processes: provisioning now skips process inspection only while the root is empty, retaining it for partial retries and package changes. An insufficient-space MSI attempt failed and rolled back the application as intended.

Native Windows user-installer regressions pass all 38 assertions, including deep seed-path copy/inspection and read-only launcher update/rollback/removal cases. The real Gradle install exposed Win32 MAX_PATH in the junction-safety check; it now uses extended-length drive/UNC paths. Owned app cleanup clears DOS read-only flags without following links, fixing removal of jpackage launchers. Fresh offline `installWindowsUser` successfully installed both Jasper and Cygwin with only JBR and Windows system directories on PATH.

The final disconnected MSI run passed all seven assertions: fresh provisioning, adding `hello` through the production supervisor, repair preservation, upgrade preservation, uninstall preservation, application removal and missing-seed rejection. Reports are retained in the VM under `C:/Users/dustin/vmtask/cygwin-complete-20261020/msi-acceptance-07/`.

Actual disconnected Gradle lifecycle passes fresh installation, update, uninstall and reinstall. Full snapshots match all 8,275 root entries, including the added `hello` package, `.bashrc`, `/etc` customization, a Cygwin symlink and a Git repository. The harness uses Cygwin `readlink` for WSL-format links that the JDK cannot interpret. Fresh-install evidence is in `user-acceptance-fresh.log`; final comparisons are in `user-acceptance.log`, under the same VM task directory. The VM network was restored afterward. Native ARM64 evidence is recorded in `validation.json`; exported logs are retained locally in `jasper-app/build/cygwin/native-acceptance/`. Final `verifyCygwinDistribution` rejects the still-missing interactive evidence as expected.

## Outstanding acceptance

Native x64 Windows remains outstanding; x64 emulation is not native x64 evidence. Interactive app validation remains outstanding on both architectures: Cygwin selection in tabs/splits/settings, Manage Cygwin packages in Settings/palette, interactive Setup changes, Ctrl+C/resize/Unicode, native programs, prompt navigation and cwd inheritance. `validation.json` retains these release gates.

Integration into current local `main` passed the full parallel headless check with Python 3.13: 11,878 tests, zero failures/errors and 29 skips (1,106 XML reports), plus native, architecture, packaging and Cygwin standalone checks. The earlier branch-baseline editor failures are superseded by this green combined result. Native x64 and interactive-app release gates remain outstanding.
