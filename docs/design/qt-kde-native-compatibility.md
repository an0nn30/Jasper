# Qt / KDE compatibility investigation — 2026-10-25

Status: native helpers and optional packaging implemented; aarch64 headless integration gate proven.
The complete release/desktop matrix remains pending. The user authorized
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
contain this enum. Compile checks passed with modern Fedora and pinned KF5.92/KF6.0
SDK headers. Theme support separately needs
`Colors:Header` evidence. No Header color is inferred from a Window fallback.

## Source preparation

[`manifest.toml`](../../packaging/desktop-colors/toolchains/manifest.toml) pins QtBase
5.15.2, 6.2.4 and 6.5.0, KF5 5.92.0 and KF6 6.0.0. Its 16 SHA256 values were read from
the official Qt/KDE `.sha256` URLs recorded per source on 2026-10-25 and verified against
the downloaded archives during baseline validation. KF5's required Qt build version is
5.15.2. KF6 ColorScheme requires Qt 6.5.0.

The opt-in recipe builds QtBase only, KF5's ConfigWidgets dependency chain, and KF6's
Config/GuiAddons/I18n/ColorScheme chain. It disables KAuth, designer/documentation/QML
and GuiAddons Wayland/X11 features that these extraction APIs do not need. Qt's XCB
platform and DBus remain enabled. Build-only prerequisites include gperf, Qt5 Linguist
tools/CMake metadata, Qt private headers for KI18n, and Qt5's xcb-util development module.
Extracted source license files and archive digests were inventoried; KColorScheme source
in both framework pins declares LGPL-2.0-or-later. Final packaged notices remain pending.

The recipe pins three build-only patches separately from unchanged tarball hashes:
Qt5's include-only GCC11 fixes from upstream commits
[0afad46](https://code.qt.io/cgit/qt/qtbase.git/commit/?id=0afad46bb71b106780dd641de768a81d9f6c6a51)
and [813a928](https://code.qt.io/cgit/qt/qtbase.git/commit/?id=813a928c7c3cf98670b6043149880ed5c955efb9),
the KConfig6 QML-off [upstream fix](https://invent.kde.org/frameworks/kconfig/-/commit/e372f1ddbab0a39373da5025d2866dfeba217bdf),
and an analogous local KI18n6 build-system guard. KF6 skips unused framework translation
compilation with ECM's supported `KF_SKIP_PO_PROCESSING=ON`; KF5 still needs Linguist.
Qt6 explicitly sets `INPUT_opengl=no`, and Qt5 rechecks configure tests after prerequisite
changes. Each patch has exact original/result file hashes and a patch hash; changed or
partially patched sources fail before mutation, while exact reruns are idempotent.

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

- `python3 packaging/desktop-colors/test/toolchains_test.py`: 12 tests passed, including
  independent Qt6/KDE6 plans, KDE5 dependency order, invalid argument handling, corrupt
  archive/patch rejection, patch idempotence/partial-state handling, named preflight
  failures, target SDK paths under a root containing `qt5`, and all three Qt floor diagnostics. Initial runs failed against
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

## Ubuntu22 baseline SDK and floor evidence

The disposable `jasper-qt-baseline` container on Fedora runs Ubuntu22 aarch64 userspace,
GNU11.4, CMake3.22.1 and glibc2.35. Isolated CPython3.13.17 runs the preparation script.
Pinned QtBase5.15.2/6.2.4/6.5.0 and both framework dependency chains built and installed
after the documented corrections; the initial KF5 build used system Qt5.15.3. The final corrected
script was checked against pristine archive members for every patch and all four
preflights. Its exact KDE5 recipe subsequently completed against private Qt5.15.2 with
all eight pinned KF5 components; a clean cold end-to-end rebuild remains pending. Source/build/diagnostic logs are preserved in `/private/tmp/jasper-qt-baseline-sdk-logs.tgz`,
`/private/tmp/jasper-kf5-baseline-sdk-logs.tgz` and `/private/tmp/jasper-qt5-baseline-build-recheck.log`.

| Probe build SDK | Runtime tested | Result |
| --- | --- | --- |
| System Qt5.15.3 | System Qt5.15.3 | Pass |
| Private Qt6.2.4 | System Qt6.2.4 and Fedora Qt6.11.2 | Same binary passes both |
| Private KF5.92 / system Qt5.15.3 | System KF5.92 / Qt5.15.3 and Fedora KF5.116 / Qt5.15.18 | Same binary passes both |
| Private KF6.0 / Qt6.5.0 | Private SDK runtime smoke, then Fedora system KF6.31 / Qt6.11.2 | Same binary passes both; minimum installed system runtime pending |

All report zero visible windows; both pinned KDE Header APIs compile and distinctive
pair/reparse assertions pass. Probe executables have no RPATH/RUNPATH, highest direct
GLIBC2.34 and CXXABI1.3.9. Task2 source `98a1b1d4` also passes all three CTests on each
system floor Qt5.15.3/Qt6.2.4: 27 extraction and 79 process checks per major, including
the real 30-second refresh. Those production helpers require at most GLIBC2.34,
GLIBCXX3.4.26 and CXXABI1.3.9 and resolve system libraries without RPATH/RUNPATH.
Exact logs: `/private/tmp/jasper-task2-baseline-floor-tests.log`,
`/private/tmp/jasper-final-recipe-patch-validation.log`, and
`/private/tmp/jasper-baseline-artifacts-newer-system-runtimes.log`.

The minimum KDE6 separately installed runtime, x86_64 matrix and real theme-engine/desktop
acceptance remain pending. The later packaging proof below completes the aarch64
headless native integration gate; source/UI choices belong to the application deliverable.

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


## Seven-helper packaging and aarch64 integration gate — 2026-10-25

The final Gradle gate `checkDesktopQtNative installDist packageApp verifyPackage packageDeb packageRpm`
passes in Ubuntu22 aarch64 userspace with JBR25, GNU11.4 and the five pinned SDK prefixes.
All four independently configured targets pass three CTests each: Qt5/Qt6 each 27 extraction
and 79 process checks; KDE5/KDE6 each 39 extraction and 110 process checks. KDE process
checks now compare configuration after every observation, including startup, against the
latest harness-authored bytes. Existing no-Header evidence remains 39 passing extraction checks.

The aggregate run registers private Qt6.5/KF6 SDK library paths with ldconfig only inside the
disposable build container; it is explicitly a private-SDK runtime case. Separate minimum
installed Qt5.15.3/KF5.92 and Qt6.2.4 fixture runs precede it. The exact package-build
artifacts also pass all twelve CTests on Fedora44 system Qt5.15.18/Qt6.11.2 and
KF5.116/KF6.31, with identical production/staged SHA256 values before and after tests.
No private library or plugin override supplies the Fedora result. Every production helper meets the Ubuntu22
ELF floor; the four new helpers require at most GLIBC2.34, GLIBCXX3.4.26 and CXXABI1.3.9,
and have no RPATH/RUNPATH. `verify-qt-package.py` additionally rejects GLIBC_PRIVATE and
GLIBC_ABI_DT_RELR requirements.

Staging and both package formats contain exactly the seven executable helpers, with 0755
modes and matching aarch64 ELF headers. JBR's automatic package scan includes executable
helpers and appends custom dependencies. Packaging therefore overrides only its dependency
placeholders with an independent mandatory ELF scan excluding the four exact optional helper
paths. Every other ELF retains its dependencies, including a hypothetical mandatory binary
with a Qt requirement. Actual DEB Depends/Pre-Depends and RPM Requires match that scan;
Qt/KF and optional-only transitive dependencies are absent. Linux policy tests pass both the
exact-path test and a real ELF regression retaining a Qt-named dependency on another binary.
Strict scanning first caught missing build-container libXtst/libasound; CI now installs both.

The generated arm64 DEB installs, checks CLI/Java and GTK fallback, and removes successfully
in minimal Ubuntu22. The aarch64 RPM does the same in minimal Fedora44. Both retain four
optional helper files whose missing libraries cause isolated loader failures; mandatory
libraries resolve. No Qt/KF runtime is installed to satisfy Jasper. A runtime-equipped Fedora RPM install also
checks all four installed Qt/KDE helpers with generation-matched samples on private
Xvfb/D-Bus, then removes successfully. RPM removal emits an inherited nonfatal JBR
transaction-lock diagnostic in both minimal and equipped cases; removal exits zero.
Package SHA256 values:

| Package | SHA256 |
| --- | --- |
| Jasper-1.0.0-linux-arm64.deb | `185f7253fcb3ea88b605f4053cf02bbd494bf1094ed93299fcd03c6dc6f50656` |
| Jasper-1.0.0-linux-aarch64.rpm | `bed21bb1206b62c05086e8b9261903d3641a53be3024f11d4d3356a2bda6b25d` |

The exact staged baseline helpers run concurrently on Fedora system runtimes across two
real 30-second boundaries. Four stable PIDs, no children/repeated startups, zero CPU tick
deltas in both intervals, unchanged configuration and timely EOF are observed. Resident
memory is Qt5 29,272 kB, Qt6 29,744 kB, KDE5 30,788 kB and KDE6 31,864 kB.
Headless macOS package verification passes and excludes the Linux helper directory;
Windows execution remains unavailable. The full macOS headless check passes 13,041
JUnit tests with zero failures/errors and 30 skips, plus 72 theme-tool tests. Its initial
invocation supplied a nonexistent Python path; repeating with installed Python3.13.15
passes. Existing JavaDoc/Unsafe diagnostics remain in the successful log.

CI now separates the source-pinned SDK preparation/cache job (180-minute cold-build cap,
two compiler workers) from the existing 30-minute package job. Cache keys include architecture
and all recipe/manifest/patch hashes, which include build flags. x86_64/aarch64 packages and
matching native fixtures are built on Ubuntu22. Runtime cells execute those exact artifacts
on Ubuntu22 and Fedora44 installed system libraries rather than recompiling with a newer
compiler. Ubuntu22's unsupported KDE6 case is an explicit capability skip; Fedora's all-four
cell is mandatory. Existing GTK jobs, Gradle worker caps and package artifacts are retained.
These workflow changes are prepared and syntax-checked, not dispatched from this branch.

This evidence satisfies the spec's deterministic headless native gate before application
integration. It does not establish a minimum separately installed Qt6.5/KF6.0 runtime,
x86_64 execution, a complete cold recipe rebuild or Plasma5/Plasma6 Wayland/X11 and non-KDE
qt5ct/qt6ct acceptance. Those release/desktop cases remain open; GUI checks require the user.
The app source policy remains GTK-only here. No GUI, AOT training, benchmark, merge or push.

Logs: `/private/tmp/jasper-final-baseline-helper-tests.log`,
`/private/tmp/jasper-final-kde6-newer-system-tests.log`,
`/private/tmp/jasper-task4-final-linux-gate.log`, `/private/tmp/jasper-task4-final-abi.log`,
`/private/tmp/jasper-task4-linux-policy-tests.log`, `/private/tmp/jasper-task4-final-package-metadata.log`,
`/private/tmp/jasper-task4-final-minimal-deb.log`, `/private/tmp/jasper-task4-final-minimal-rpm.log`,
`/private/tmp/jasper-task4-idle-helpers.log`, `/private/tmp/jasper-task4-mac-package.log`,
`/private/tmp/jasper-native4-package-runtime-tests.log`,
`/private/tmp/jasper-native4-package-runtime-hashes.log`,
`/private/tmp/jasper-task4-final-equipped-rpm.log`, `/private/tmp/jasper-task4-mac-check-final.log`.
