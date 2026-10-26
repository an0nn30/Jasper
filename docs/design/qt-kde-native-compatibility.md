# Qt / KDE compatibility investigation — 2026-10-25

Status: preparation implemented; native compatibility gate **pending**. The user authorized
local work while Fedora updates. SSH became available as `dustin@10.211.55.13` during
preparation; the parent prepared a disposable rootless Fedora 44 aarch64 Podman container
with the development prerequisites and ran all four probes. Ubuntu VM use is excluded. Ubuntu 22.04
remains the packaged ELF userspace baseline; containers/PRoot on Fedora are baseline
userspaces, not booted desktop acceptance.

## Prepared targets

| CMake build | Public SDK minimum | Gradle prefix properties |
| --- | --- | --- |
| `qt5` | Qt Widgets 5.15 | `jasper.qt5Prefix` |
| `qt6` | Qt Widgets 6.2 | `jasper.qt6Prefix` |
| `kde5` | Qt Widgets 5.15, KF5 ConfigWidgets/Config 5.92 | `jasper.qt5Prefix`, `jasper.kf5Prefix` |
| `kde6` | Qt Widgets 6.5, KF6 ColorScheme/Config 6.0 | `jasper.kde6QtPrefix`, `jasper.kf6Prefix` |

Build directories are `jasper-app/build/desktop-colors/cmake/<target>`. Each configuration
gets only its target's prefixes, clears cached Qt/KF discovery, ignores CMake package
registries/environment search paths, and disables RPATH. Omitted prefixes permit installed
development packages; they do not prove a floor when those packages are newer.

The probe prints compiled/runtime Qt versions, compiled KF version, configured pairs,
public palette-event counts, Header availability, visible-window count and Linux Qt/KF
runtime mapping paths. KDE public generated version headers describe the build SDK;
no public runtime framework version query was found. `kf.runtime` therefore explicitly
reports unknown. Runtime paths must accompany the built-against version in evidence.

The temporary `kdeglobals` fixture supplies distinctive Window/View/Selection/Tooltip
background/foreground values and changes Window before a KConfig reparse. HOME and XDG
config/cache/data locations point into the temporary root before QApplication starts;
FullConfig keeps KConfig's global cascade enabled inside that isolated root. The probe
never shows or activates a widget. Public QApplication palette mutation checks event
delivery in the test process; real KDE/qt5ct/qt6ct theme change delivery remains untested.

Header detection compiles `KColorScheme(QPalette::Active, KColorScheme::Header)` with the
selected imported libraries and defines `DC_HAVE_KCOLORSCHEME_HEADER=0|1`. The pinned
[KF5 header](https://invent.kde.org/frameworks/kconfigwidgets/-/blob/v5.92.0/src/kcolorscheme.h)
and [KF6 header](https://invent.kde.org/frameworks/kcolorscheme/-/blob/v6.0.0/src/kcolorscheme.h)
contain this enum. Modern Fedora KF5/KF6 compile checks passed; floor-version checks remain
pending. Theme support separately needs
`Colors:Header` evidence. No Header color is inferred from a Window fallback.

## Source preparation

[`manifest.toml`](../../packaging/desktop-colors/toolchains/manifest.toml) pins QtBase
5.15.2, 6.2.4 and 6.5.0, KF5 5.92.0 and KF6 6.0.0. Its 16 SHA256 values were read from
the official Qt/KDE `.sha256` URLs recorded per source on 2026-10-25; no archive download,
source compilation or artifact digest is claimed. KF5's required Qt build version is
5.15.2. KF6 ColorScheme requires Qt 6.5.0.

The opt-in recipe builds QtBase only, KF5's ConfigWidgets dependency chain, and KF6's
Config/GuiAddons/I18n/ColorScheme chain. It disables KAuth, designer/documentation/QML
and GuiAddons Wayland/X11 features that these extraction APIs do not need. Qt's XCB
platform and DBus remain enabled. This dependency reduction and the full system header
prerequisite list need Linux configure/build confirmation. Source license files are
identified in the manifest; per-component license review and shipped notices remain
pending extraction and packaging. No dependency is shipped by this preparation change.

On an authorized baseline Linux userspace, choose a dedicated private area:

```sh
bash packaging/desktop-colors/toolchains/build-baseline.sh --plan qt6 /absolute/qt-compat
bash packaging/desktop-colors/toolchains/build-baseline.sh --build qt6 /absolute/qt-compat
bash packaging/desktop-colors/toolchains/build-baseline.sh --build qt5 /absolute/qt-compat
bash packaging/desktop-colors/toolchains/build-baseline.sh --build kde5 /absolute/qt-compat
bash packaging/desktop-colors/toolchains/build-baseline.sh --build kde6 /absolute/qt-compat
./gradlew :jasper-app:verifyDesktopQtCompatibility --no-daemon --console=plain \
  -Pjasper.qt5Prefix=/absolute/qt-compat/prefixes/qt5 \
  -Pjasper.qt6Prefix=/absolute/qt-compat/prefixes/qt6 \
  -Pjasper.kf5Prefix=/absolute/qt-compat/prefixes/kf5 \
  -Pjasper.kde6QtPrefix=/absolute/qt-compat/prefixes/kde6-qt \
  -Pjasper.kf6Prefix=/absolute/qt-compat/prefixes/kf6
```

`--plan` writes nothing. `--build` is Linux-only, downloads and verifies sources before
safe extraction, installs only into that area, and records configure/build/toolchain
commands in `logs/`. It requires Python >=3.11.8 and the existing build prerequisites;
normal Gradle tasks never call it. Run generic Qt6 first. Runtime verification clears
`LD_LIBRARY_PATH` and Qt plugin overrides so installed system runtimes/plugins supply
the integration; private SDKs must not substitute for minimum/newer runtime tests.

For each built target, retain these commands' full output with compiler version,
architecture, userspace identity, source manifest and probe output:

```sh
readelf -d jasper-app/build/desktop-colors/cmake/qt6/qt-compatibility
readelf --version-info jasper-app/build/desktop-colors/cmake/qt6/qt-compatibility
ldd jasper-app/build/desktop-colors/cmake/qt6/qt-compatibility
```

Repeat for `qt5`, `kde5`, `kde6` on x86_64/aarch64 and minimum/newer system runtimes.
Inspect NEEDED, RPATH/RUNPATH, GLIBC/GLIBCXX requirements and actual mapped paths;
unresolved optional Qt/KF in minimal userspaces is distinct from a baseline ABI failure.

## Local evidence and remaining gate

On the macOS development host with AppleClang 21, CMake and Python 3.14.7:

- `python3 packaging/desktop-colors/test/toolchains_test.py`: 5 tests passed, including
  independent Qt6/KDE6 plans, KDE5 dependency order, invalid argument handling, corrupt
  archive rejection, and all three Qt floor diagnostics. Initial runs failed against
  absent preparer/CMake files; these are tooling contract failures, not native assertion RED.
- `bash -n packaging/desktop-colors/toolchains/build-baseline.sh`: passed.
- `./gradlew :jasper-app:verifyDesktopQtCompatibility --no-daemon --console=plain`:
  successful build-script configuration, task SKIPPED on non-Linux as designed. The initial
  sandboxed attempt failed opening Gradle's cache lock; the authorized retry produced this skip.
- `cmake -S packaging/desktop-colors -B /private/tmp/jasper-qt-compatibility-missing-qt5
  -DDC_QT_MAJOR=5 -DDC_PROBE_KIND=qt -DDC_BUILD_TESTS=ON`: exit 1, named
  `Missing desktop-colors SDK: Qt5 Widgets >= 5.15`. This is missing infrastructure.

## Modern Fedora system-runtime evidence

The parent built probe source from `213739400` with GNU C++ 16.2.1 and ran verbose CTest
under private Xvfb/D-Bus in the disposable Fedora 44 aarch64 container. This is headless
container evidence, not booted desktop/theme-engine acceptance.

The parent created `jasper-qt-tests` from `docker.io/library/fedora:44`, installed CMake,
Ninja, GCC C++, Qt5/Qt6 development packages, KF5 ConfigWidgets, KF6 ColorScheme/Config
development packages, Python, binutils, Xvfb and dbus-daemon inside it, and copied source
to `/work/packaging/desktop-colors`. After `ssh dustin@10.211.55.13` and
`podman exec -it jasper-qt-tests bash`, the reproducer is:

```sh
mkdir -p /tmp/runtime-root
chmod 700 /tmp/runtime-root
export XDG_RUNTIME_DIR=/tmp/runtime-root
for target in qt5 qt6 kde5 kde6; do
  major=${target: -1}
  kind=${target%?}
  cmake -S /work/packaging/desktop-colors -B /work/build/$target -G Ninja \
    -DDC_QT_MAJOR=$major -DDC_PROBE_KIND=$kind -DDC_BUILD_TESTS=ON
  cmake --build /work/build/$target -j2
  xvfb-run -a dbus-run-session -- ctest --test-dir /work/build/$target --verbose
done
```

| Target | Compiled/runtime Qt | Built-against KF | CTest | Application/widget palette events | Visible windows |
| --- | --- | --- | --- | --- | --- |
| Qt5 | 5.15.18 / 5.15.18 | — | 1 passed | 4 / 2 | 0 |
| Qt6 | 6.11.2 / 6.11.2 | — | 1 passed | 4 / 1 | 0 |
| KDE5 | 5.15.18 / 5.15.18 | 5.116.0 | 1 passed | 4 / 2 | 0 |
| KDE6 | 6.11.2 / 6.11.2 | 6.31.0 | 1 passed | 4 / 1 | 0 |

Both KDE Header constructor compile checks succeeded; both fixtures report Header theme
absent. Both KDE fixtures read back all four distinctive pairs and reparse the changed
Window background (`#ff0b0c0d` to `#ff333435`). Mapped Qt/KF library paths are under
`/usr/lib64/`, including `libQt5Core.so.5.15.18`, `libQt6Core.so.6.11.2`,
`libKF5ConfigWidgets.so.5.116.0` and `libKF6ColorScheme.so.6.31.0`; runtime KF version
remains explicitly unknown through public APIs.

Raw logs: `/private/tmp/jasper-fedora-compatibility.log` (Qt5/Qt6/KDE5 plus initial missing
KF6 Config development package), `/private/tmp/jasper-fedora-kde6.log` (KDE6 successful
rerun after installing that package inside the container). The first log includes D-Bus
portal activation/teardown warnings despite all three probe tests passing. These runs do
not establish clean desktop integration or the complete release gate. The Gradle task now
uses `ctest --verbose --output-on-failure` so successful version/event/Header/mapping
evidence is visible in its captured output, as it is in these manually verbose runs.

Pinned private SDK preparation, minimum-version runtime tests, baseline ELF requirements,
external mapped-window/config-write audit, real theme-engine events and x86_64 evidence
remain pending. Task 1 and the native gate remain open; source/UI activation must wait
for their evidence.

## KDE helper extraction evidence — 2026-10-25

Task 3's production helpers and deterministic fixtures ran in the same Fedora 44
aarch64 container under private Xvfb/D-Bus. Qt5 5.15.18 / KF5 5.116.0 and Qt6
6.11.2 / KF6 6.31.0 each pass all three compatibility/extraction/process CTests:
39 KDE extraction assertions and 87 process assertions per major. Generic Qt5/Qt6
regression suites each pass 27 extraction and 79 process assertions. A separate
KDE5 build with the Header compile guard disabled passes 39 extraction assertions.
All four targets also pass all three CTests with GCC AddressSanitizer and
UndefinedBehaviorSanitizer (`detect_leaks=0`, `halt_on_error=1`); third-party toolkit
leak detection is excluded. Native protocol verification passes 450 checks.

The KDE tests cover configuration cascade/user overrides, paired Header evidence,
translucent ink, native defaults for malformed/missing config, invalid-brush
injection, three public event types, explicit and real 30-second reparses, healthy
opposite/unknown observations and recovery, exact generations, prompt EOF, no
top-level Show/activation events during the loop, and unchanged fixture config.
`/proc/<pid>/maps` verifies one Qt major, matching KF and no GTK. An isolated
executable copy with a missing KF SONAME fails startup while its generic Qt sibling
remains usable, maps no KF, and ignores changed KDE configuration.

Build with `DC_PROBE_KIND=kde` and `DC_QT_MAJOR=5|6`, `--parallel 2`, then run
verbose CTest with `test/themes/private-bus.conf` as above. Task 3's source/builds
and logs are under `/work/task3/` in `jasper-qt-tests`. macOS Gradle
`testDesktopProtocol testDesktopQt testDesktopKde` configures successfully and skips
native execution. The container uses direct CMake/compiler verification because
it has no JBR or complete checkout. This evidence retains the floor/ABI and real
desktop acceptance gates above; KF metadata reports its built-against version and
Qt's runtime version.
