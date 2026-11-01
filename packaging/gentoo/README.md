# Gentoo source overlay

`app-misc/jasper-workstation-1.0.0_p20261101` builds source commit
`be557f600d984232cc9060c8488b563286b3ee28` with Gradle 9.7.0 and the JCEF
JetBrains Runtime SDK 25.0.4.1-b635.75. The initial keyword is `~arm64` on glibc systems; the upstream private JBR requires glibc.
The installed image removes the current-directory search entry from two upstream JCEF ELF RPATHs while retaining their private `$ORIGIN` lookup.
The package installs its image and private runtime in `/opt/jasper-workstation`,
the `jasper` command in `/usr/bin`, and a desktop entry and icon. It leaves
system Java selection and user configuration to their existing owners.

## Install

Keep the checkout at a stable, root-readable path. Read the
[PolyForm Noncommercial](licenses/PolyForm-Noncommercial-1.0.0),
[JogAmp](licenses/Jasper-JogAmp) and [Nerd Fonts](licenses/Jasper-Nerd-Fonts)
license texts before accepting those three package-specific entries below.

On a system with no existing `repos.conf`, create `/etc/portage/repos.conf/` and
write `gentoo.conf` with the existing Gentoo checkout location:

```ini
[DEFAULT]
main-repo = gentoo
[gentoo]
location = /var/db/repos/gentoo
sync-type = rsync
sync-uri = rsync://rsync.gentoo.org/gentoo-portage
```

Keep existing repository configuration when already present. Register the overlay as root:

```sh
mkdir -p /etc/portage/repos.conf
cat > /etc/portage/repos.conf/jasper-local.conf <<EOF_REPO
[jasper-local]
location = /path/to/Jasper/packaging/gentoo
masters = gentoo
auto-sync = no
EOF_REPO
mkdir -p /etc/portage/package.accept_keywords /etc/portage/package.license /etc/portage/package.use
printf '%s\n' 'app-misc/jasper-workstation ~arm64' > /etc/portage/package.accept_keywords/jasper
printf '%s\n' 'app-misc/jasper-workstation PolyForm-Noncommercial-1.0.0 Jasper-JogAmp Jasper-Nerd-Fonts' > /etc/portage/package.license/jasper
printf '%s\n' 'app-misc/jasper-workstation gtk -gtk4 -qt6 -kde' > /etc/portage/package.use/jasper
FEATURES=test emerge --ask --buildpkg=n app-misc/jasper-workstation
```

Jasper uses PolyForm Noncommercial 1.0.0. The package preserves third-party
license notices in its application jars and the private runtime's `legal` tree.
The overlay carries the compound JogAmp and Nerd Fonts notices as Gentoo license
entries. If your license policy rejects other declared licenses, review and
accept those individually. Binary redistribution is restricted in this recipe;
installation builds Jasper from source while using pinned upstream Java/native
dependencies and the private JBR.

All integration flags default off:

| USE | Native helper | Additional dependencies |
|---|---|---|
| `gtk` | GTK 3 | GTK 3.24 or newer |
| `gtk4` | GTK 4 | GTK 4.10 or newer and libadwaita |
| `qt6` | Qt 6 | Qt 6 Widgets |
| `kde` | KDE Frameworks 6 | Qt 6, KConfig and KColorScheme; requires `qt6` |

Enabled integrations are required at build time. Disabled integrations are omitted
even when the SDK is installed; Qt 5 and KDE Frameworks 5 are explicitly off. The
GIO portal helper and core JBR/X11/Chromium runtime libraries remain dependencies.
GTK is the verified installed configuration on the initial XFCE ARM64 VM; the
[verification record](../../docs/superpowers/plans/2026-11-01-gentoo-package-verification.md)
records the headless acceptance evidence and remaining graphical checks.

`FEATURES=test` runs the upstream headless `verifyPackage` task: it checks the
packaged jars/runtime/selected helpers and runs the native launcher suites.
It does not launch the application. After installing, these checks are headless:

```sh
qfile /usr/bin/jasper /opt/jasper-workstation/bin/Jasper
jasper --help
/opt/jasper-workstation/lib/runtime/bin/java -version
find /opt/jasper-workstation/lib/app/desktop-colors -maxdepth 1 -type f -printf '%f\n' | sort
```

The graphical checks in [native acceptance](../../docs/packaging.md#native-acceptance-checklist)
remain a user step. To remove the package, run `emerge --ask --depclean
app-misc/jasper-workstation`; user settings remain in the user's Jasper home.

## Offline inputs and updates

Portage fetches the source snapshot, wrapper ZIP, JBR SDK archive and every locked
Maven file before compiling. Only the source and JBR archives are unpacked by
`src_unpack`; the wrapper extracts its local Gradle ZIP. Maven jars, parent POMs
and Gradle module metadata are staged byte-for-byte into a local Maven repository,
with group-qualified paths and collision-resistant distfile names. An init script
replaces project repositories with that repository. The wrapper runs `--offline`
with a new `${T}/gradle-home`, no build cache and automatic toolchain provisioning
disabled. Network sandboxing remains enabled. Packaging support comes from
`FILESDIR`, because the pinned source snapshot predates this overlay.

To refresh dependencies, first complete the relevant upstream build using the
updated source, JBR and Gradle wrapper. Use a dedicated Gradle home for this step
to avoid collecting dependencies from unrelated projects. Run:

```sh
files=packaging/gentoo/app-misc/jasper-workstation/files
python3 "$files/maven.py" inventory "$GRADLE_USER_HOME/caches/modules-2/files-2.1" /tmp/jasper-maven-inventory.json
python3 "$files/maven.py" lock /tmp/jasper-maven-inventory.json "$files/maven-lock.json"
python3 "$files/maven.py" uris "$files/maven-lock.json" > /tmp/jasper-maven-uris.txt
```

Replace the ebuild's Maven `SRC_URI` lines with `/tmp/jasper-maven-uris.txt`.
The inventory preserves cached POM parents/BOMs and `.module` files and excludes
only Gradle's own tooling source ZIP. Review the catalog diff and upstream source
URLs. The focused Gradle fixture uses the configured `JAVA_HOME` and the wrapper; it verifies repository replacement for root and child projects and local parent-POM resolution.
The generator derives canonical Maven Central URLs, except
`org.jetbrains.jediterm`, which uses JetBrains' IntelliJ dependency repository.
Other repositories require an explicit generator update and review.

The overlay maintains one source recipe with its matching catalog. For a source-version update, rename the ebuild to its new version, update
`JASPER_COMMIT` to the full source SHA and update `-PjasperVersion` if the
application version changes. Keep Gradle/JBR pins aligned with that source's
wrapper and `packaging/jcef/checksums.txt`. Update license texts/declarations and
runtime dependencies whenever the bundled inputs change. Then, on Gentoo:

```sh
ebuild packaging/gentoo/app-misc/jasper-workstation/jasper-workstation-1.0.0_p20261101.ebuild manifest
python3 -m unittest discover -s packaging/gentoo/tests -v
sh packaging/gentoo/tests/test-offline-init.sh
sh packaging/gentoo/tests/test-rpath.sh
FEATURES=test USE='-gtk -gtk4 -qt6 -kde' ebuild packaging/gentoo/app-misc/jasper-workstation/jasper-workstation-1.0.0_p20261101.ebuild clean unpack prepare configure compile test install
FEATURES=test USE='gtk -gtk4 -qt6 -kde' emerge --oneshot --buildpkg=n app-misc/jasper-workstation
```

Portage's Manifest pins fetched bytes independently of the SHA-256/size checks
used by Maven staging. The all-off phase check installs only into Portage's temporary image; the GTK emerge installs the final package. `--buildpkg=n` leaves the host's existing binary-package signature policy intact.
A clean all-off build followed by a GTK build verifies
that no developer Gradle cache or installed optional SDK hides missing inputs
or helper-selection mistakes. Check `qt6 kde` dependency resolution and helper
selection when those SDKs are available; graphical acceptance and other
architectures need their own validation.
