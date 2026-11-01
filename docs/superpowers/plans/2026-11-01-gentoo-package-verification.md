# Gentoo source package verification — 2026-11-01

Implementation: `0f647d1928891d3fb069cfe40bafeb9382ad3fed`.
[Plan](2026-11-01-gentoo-package.md), [approved design](../specs/2026-11-01-gentoo-package-design.md),
[overlay guide](../../../packaging/gentoo/README.md).

Independent task and whole-branch reviews approved the implementation with no
actionable defects. Whole-branch review covered be557f600..6ac73905e. User graphical
acceptance remains pending. No GUI was launched.

## Offline build and selection

Portage fetched all 220 Manifest inputs successfully. The JCEF SDK archive's
SHA-512 matches `packaging/jcef/checksums.txt`. The Maven catalog contains 217
files: 107 jars, 87 POMs and 23 module files, totaling 97,058,593 bytes.

Clean builds used isolated Gradle state, the fetched local wrapper ZIP and staged
Maven repository, with `--offline`, `test`, `network-sandbox`, `sandbox`,
`usersandbox` and `userpriv` enabled. All-off compilation and headless package
verification passed; its staged image contains only the portal helper. All-on
unpack/prepare/configure/compile/test/install passed; its image contains exactly
portal, GTK 3, GTK 4, Qt 6 and KDE 6 helpers. Native launcher core verification
reports 152 checks with zero failures.

The first run exposed HotSpot's core-dump-filter write. The recipe now uses the
narrow `/proc/self/coredump_filter` prediction documented by Gentoo's Java policy.
The installed-image audit exposed two upstream JCEF `.:$ORIGIN` RPATHs. Sanitation
removes cwd lookup while preserving `$ORIGIN` and DT_RPATH semantics. Corrected
all-on installation emits no RPATH security warnings.

Host binary-cache signing policy rejected creation of a new unsigned local
binary package. Intermediate validation used ebuild phases; final installation
used `emerge --buildpkg=n`. Existing trust policy was preserved.

## Installed GTK configuration

Final GTK-only emerge exited zero and recorded `app-misc/jasper-workstation` in
world. Recorded USE: `arm64 elibc_glibc gtk kernel_linux`.

- Installed desktop helpers are exactly `jasper-desktop-portal` and `jasper-desktop-gtk3`.
- `qfile` owns `/usr/bin/jasper` and `/opt/jasper-workstation/bin/Jasper`; the command symlink is relative and correct.
- `jasper --help` and the private runtime's Java 25.0.4.1 version command pass.
- All 60 installed ELF files have resolved external libraries, accounting for libraries bundled by basename.
- Both sanitized JCEF RPATHs are `$ORIGIN`. Loading installed JCEF with `System.load` under the bundled runtime succeeds headlessly.
- `desktop-file-validate` exits zero; its multiple-main-category hint for `System;Network` is informational.

VM logs are retained under `~/.cache/jasper-build/`: `gentoo-build-all-off.log`,
`gentoo-audit-all-off.log`, `gentoo-build-all-on.log`, `gentoo-audit-all-on.log`
and `gentoo-install-gtk.log`.

## Focused regressions and limits

Ten Python regressions pass for collisions, metadata preservation, deterministic
output, unsafe inputs, checksums, catalog/ebuild parity and Manifest/JBR pins.
The real Gradle fixture passes root/child repository replacement and local
parent-POM resolution; retaining Maven Central makes it fail. The Linux ELF
fixture demonstrates malicious cwd library injection before sanitation and safe
private-library lookup afterward, while rejecting unexpected RPATHs.
Shell syntax checks and the full commit whitespace check pass.

The earlier full upstream check passed 13,843 tests with 60 skips; packaging-only
changes did not repeat that suite. Initial keywording remains glibc ARM64.
Graphical/session acceptance and other architectures need their own validation.
Final pkgcheck reports size guidance for the pinned FILESDIR catalog, long
generated URI lines and the Python 3.15 compatibility suggestion; the recipe
retains its tested/selected Python 3.11–3.14 range. It also reports
`NonsolvableDepsInDev` for glibc dependencies and `RequiredUseDefaults` /
`RequiredUseUnsatisfiableInDev` for `elibc_glibc` across three musl profiles.
Those profiles are intentionally unsupported and rejected by the private JBR
recipe. The glibc ARM64 profile resolves and installs successfully. Full QA log:
VM `~/.cache/jasper-build/gentoo-qa-final.log`.
