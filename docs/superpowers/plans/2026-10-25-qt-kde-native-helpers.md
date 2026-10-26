# Qt / KDE native helpers implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development. Execute tasks in order, with one implementation commit and independent task review per task, then whole-branch review. Steps use checkbox syntax.

**Goal:** Deliver four independently runnable, optional Qt/KDE palette helpers and prove their supported Linux build/runtime matrix before enabling them in Jasper.
**Architecture:** Extend the existing C wire protocol and add shared C++17 Qt process code. Compile Qt5, Qt6, KDE5 and KDE6 into separate executables; KDE helpers reuse process handling but extract complete KColorScheme snapshots. Keep the current Java source policy unchanged until the integration plan.
**Tech Stack:** C11, C++17, CMake, Qt 5.15 / Qt 6.2, KF5 5.92 / KF6 6.0 (Qt 6.5), Gradle, private Xvfb and D-Bus.
**Spec:** [Approved design](../specs/2026-10-25-qt-kde-desktop-colors-design.md).
**Status:** Draft for implementation review, 2026-10-25. No implementation started. This is deliverable 1 of 2; [application integration](2026-10-25-qt-kde-application-integration.md) follows its native gate. The compatibility investigation deliberately precedes the final baseline dependency manifest: record resolved versions, hashes and any necessary plan amendment at Task 1, rather than inventing build artifacts now.

## Global Constraints

- Runtime targets: Qt5 5.15, generic Qt6 6.2; KF5 5.92; KDE6 KF6 6.0 with Qt6 6.5. Prove them before UI integration.
- Packaged binaries keep the Ubuntu 22.04 glibc baseline, on x86_64 and aarch64; no bundled Qt runtime or platform-theme plugins and no mandatory Qt/KF package dependencies.
- One toolkit major per process. No Qt/KDE libraries in the JVM or GTK helpers. No mapped/activated windows, GUI acceptance, AOT training, desktop writes, shell launching, or session restoration.
- Fixed names: jasper-desktop-qt5, jasper-desktop-qt6, jasper-desktop-kde5, jasper-desktop-kde6. Installation directories remain lib/desktop-colors and lib/app/desktop-colors.
- Protocol v1 keeps its shape, 64 KiB message bound, 1024-code-point reasons and integer generations; add only source values qt5, qt6, kde5, kde6. Existing GTK vectors remain unchanged.
- Qt/KDE report actual polarity, even for opposite-polarity requests. Healthy unavailable/mismatched palettes remain observable. No inversion, forced style, named-scheme activation, or autosave.
- Only the seven existing pairs; Active group except Qt tooltip Inactive. Reject invalid/non-solid brushes and preserve RGBA. No Qt Accent dependency or new SDK/semantic roles.
- Interface only. Existing contrast/portal/terminal/editor behavior remains binding. Read-only observations never write user configuration.
- Use the Gradle wrapper and JBR25. No merge/push without authorization for this deliverable. Commit messages end with Co-Authored-By: Codex <noreply@openai.com>.

## Review Focus

1. A missing Qt/KF shared library or platform plugin must not make Jasper uninstallable or stop other helpers (Tasks 1, 4).
2. A current-only palette must reply with its observed polarity and stay alive across alternating requests (Tasks 2, 3).
3. QWidget initialization, events and EOF must work without mapping a window or leaking a helper (Tasks 2, 3).
4. Private build prefixes must not leak into runtime RPATH or silently raise glibc/libstdc++ requirements (Tasks 1, 4).
5. KDE Header availability and a generic Qt style must not be mistaken for a richer desktop palette (Tasks 1, 3).

## File map

- `packaging/desktop-colors/CMakeLists.txt`: explicit per-major C++ helper/test targets; Gradle remains the entry point.
- `packaging/desktop-colors/src/protocol.{h,c}`: shared C serializer/parser, including C++ linkage guards.
- `packaging/desktop-colors/src/qt_probe.{h,cpp}`: bounded stdin/event loop, snapshot ownership, Qt extraction and metadata.
- `packaging/desktop-colors/src/kde_probe.cpp`: KConfig/KColorScheme extraction, compiled separately for KF5/KF6.
- `packaging/desktop-colors/src/qt_main.cpp`: source-selected production entry point.
- `packaging/desktop-colors/test/qt_probe_test.cpp`, `kde_probe_test.cpp`, `qt_process_test.py`: extraction assertions and real-process protocol/lifetime tests.
- `packaging/desktop-colors/test/qt-vectors.jsonl`: additional wire fixtures, consumed by Java in deliverable 2.
- `packaging/desktop-colors/test/qt-fixtures/`: deterministic test-only Qt palette driver and KDE config fixtures, never shipped.
- `packaging/desktop-colors/toolchains/`: baseline build manifest/scripts; hashes and versions frozen by Task 1.
- `gradle/linux-desktop-colors.gradle`, `gradle/packaging.gradle`, `.github/workflows/{ci,linux-packages}.yml`: builds, verification and packaging.
- `docs/design/qt-kde-native-compatibility.md`: dated compatibility evidence and exact reproducer commands; not a user guide.

### Task 1: Prove baseline toolchains and public API availability

**Files:** Create `packaging/desktop-colors/test/qt_compatibility.cpp`, `packaging/desktop-colors/toolchains/build-baseline.sh`, `packaging/desktop-colors/toolchains/manifest.toml`, `docs/design/qt-kde-native-compatibility.md`; create the initial CMake file and add `verifyDesktopQtCompatibility` to `gradle/linux-desktop-colors.gradle`.
**Interfaces:** Gradle properties `jasper.qt5Prefix`, `jasper.qt6Prefix`, `jasper.kf5Prefix`, `jasper.kde6QtPrefix`, `jasper.kf6Prefix` are absolute development prefixes. Each target has its own CMake build directory/cache. Missing required build dependencies produce a named diagnostic, not an unnoticed skipped test. Runtime optionality does not imply silently omitting release binaries. Generic Qt6 uses qt6Prefix (6.2 floor); KDE6 uses kde6QtPrefix (6.5 floor) plus kf6Prefix. KDE5 uses qt5Prefix plus kf5Prefix. Do not let the KDE6 SDK accidentally raise the generic Qt6 target's floor. CMake options are `DC_QT_MAJOR=5|6`, `DC_PROBE_KIND=qt|kde` and `DC_BUILD_TESTS=ON|OFF`; output build directories are qt5, qt6, kde5 and kde6. Pin find_package requirements and CMAKE_PREFIX_PATH separately per target. Normal check/build never downloads or rebuilds dependency SDKs implicitly: it uses installed development packages or explicitly prepared prefixes.

- [ ] **Write the compile/runtime probe first.** Instantiate QApplication with quit-on-last-window disabled; extract Window/WindowText, Base/Text, Highlight/HighlightedText and Inactive ToolTip roles. For KDE builds also construct Window/View/Selection/Tooltip KColorScheme values from KSharedConfig::openConfig("kdeglobals") with globals enabled. Print compiled/runtime toolkit and framework versions, Header API availability and mapped-window count. Exercise a public palette event without showing widgets. The assertions are:

```cpp
QApplication app(argc, argv);
app.setQuitOnLastWindowClosed(false);
for (auto *window : QGuiApplication::allWindows()) if (window->isVisible()) return 2;
const auto palette = QApplication::palette();
if (!palette.color(QPalette::Active, QPalette::Window).isValid()) return 3;
if (!palette.color(QPalette::Active, QPalette::WindowText).isValid()) return 4;
if (QT_VERSION_MAJOR != qVersion()[0] - '0') return 5;
```

Use a test-only temporary config root; never point a writing fixture at the user's home. In tests, both pairs must survive readback with distinctive configured colors, and changing config followed by KConfig reparse must change the sample. Detect Header support by compiling the actual public API; record the exact guards needed by Task 3.

- [ ] **Run and record the initial missing-capability failure.** `./gradlew :jasper-app:verifyDesktopQtCompatibility --no-daemon --console=plain` must fail with the specific absent SDK/prefix until the target prerequisites exist. Do not misreport unavailable infrastructure as an assertion RED.
- [ ] **Build isolated development prefixes on the authorized Linux VM or CI.** Use public upstream tarballs with version and SHA256 recorded in `manifest.toml`; build only dependency components needed by Qt Widgets/KF5 ConfigWidgets/KF6 ColorScheme. Record transitive dependencies, toolchain, CMake options and licenses. First establish generic Qt6 against 6.2 independently of the Qt6.5+ KF6 prefix. No elevated system install on the user's VM. Existing SSH authorization is `parallels@10.211.55.12`; reuse its dedicated test area, not unrelated projects.
- [ ] **Prove baseline packaging feasibility.** Compile/run the probe in Ubuntu22 userspace, inspect ELF NEEDED, version-info and RPATH/RUNPATH; Qt/KF may be unresolved in the minimal runtime, but baseline libc/libstdc++ requirements must be satisfied. In runtime-equipped environments, use their system Qt/KF/plugin set, not the private build prefix. Demonstrate Qt5/KF5, generic Qt6 floor, and Qt6/KF6. Older userspaces under PRoot do not count as booted desktop sessions; label evidence precisely.
- [ ] **Freeze the evidence and build recipe.** Fill the dated report with commands, source hashes, runtime paths, Header guards, plugin event behavior, and dependency scans. If any required floor or system-runtime compatibility fails, amend the spec/plan explicitly before dependent tasks; do not enable UI with silently reduced coverage.
- [ ] **Commit:** `build: establish Qt and KDE helper compatibility targets`, with required trailer. Review the build evidence as part of this task.

### Task 2: Implement generic Qt5/Qt6 helpers and v1 source serialization

**Files:** protocol.{h,c}, qt_probe.{h,cpp}, qt_main.cpp, CMakeLists.txt; qt_probe_test.cpp, qt_process_test.py, qt-vectors.jsonl and test-only Qt palette fixtures; gradle/linux-desktop-colors.gradle.
**Interfaces:** C enum appends `DC_QT5, DC_QT6, DC_KDE5, DC_KDE6`; `dc_write_palette` serializes exactly qt5/qt6/kde5/kde6 and rejects any other enum. Add extern-C guards around the public C function declarations. Preserve existing enum values and seven pairs. In C++ expose:

```cpp
bool dc_qt_pair(const QPalette &, QPalette::ColorGroup,
                QPalette::ColorRole, QPalette::ColorRole, dc_colors *);
void dc_qt_extract(const QPalette &, dc_palette *);
int dc_qt_run(dc_source source, QApplication &app);
int dc_qt_main(dc_source source, int argc, char **argv);
```

`dc_qt_extract` sets pairs and actual polarity, not generation or borrowed string ownership. `dc_qt_main` constructs QApplication and delegates to `dc_qt_run`; the run function owns metadata storage through each synchronous `dc_write_palette` call. A source dispatch invokes KDE extraction only in a binary built with its corresponding KF dependencies (Task 3).

- [ ] **Add extraction/protocol tests before implementation.** A QPalette fixture gives each role a different color. Assert every mapping from the design, Active versus Inactive tooltip behavior, Header/Sidebar absent, invalid/gradient/texture rejection, fractional alpha and entire-pair omission. Pin assertions such as:

```cpp
QPalette p;
p.setColor(QPalette::Active, QPalette::Window, QColor("#202020"));
p.setColor(QPalette::Active, QPalette::WindowText, QColor("#eeeeee"));
p.setColor(QPalette::Inactive, QPalette::ToolTipBase, QColor("#123456"));
p.setColor(QPalette::Inactive, QPalette::ToolTipText, QColor("#ffffff"));
dc_palette out{};
dc_qt_extract(p, &out);
QVERIFY(out.colors[DC_WINDOW].present);
QCOMPARE(out.appearance, DC_DARK);
QVERIFY(!out.colors[DC_HEADER].present);
QVERIFY(!out.colors[DC_SIDEBAR].present);
QCOMPARE(out.colors[DC_POPOVER].background.red, 0x12 / 255.0);
```

Extend native serializer tests for all new enum values, unknown enums, escaping and boundary sizes. Put new complete palette envelopes in qt-vectors.jsonl without editing existing GTK vector expectations. Add subprocess tests asserting request generation 9007199254740993 survives and malformed/oversized commands fail boundedly without partial output.
- [ ] **Observe RED.** Run new `:jasper-app:testDesktopQt` targets for Qt5 and Qt6 under private `xvfb-run`/`dbus-run-session`; record the missing implementation/assertion failure.
- [ ] **Implement mapping and event loop.** Window, View, Dialog, Popover and Selection use exactly the design's paired roles. Convert supported QColor values to finite sRGB components, reject non-SolidPattern brushes and invalid/out-of-range results, retain alpha; match existing Window polarity classification without changing contrast admission. `QSocketNotifier` reads bounded nonblocking stdin with newline framing through `dc_read_command`; handle multiple/fragmented commands, ping and EOF. Flush one complete snapshot per accepted sample. Quit promptly on EOF. Filter ApplicationPaletteChange, PaletteChange and StyleChange on the GUI thread; use one single-shot 100 ms timer for events. Reply immediately to sample commands, retaining their latest generation for later event snapshots. No QStyle::standardPalette, appearance setter or forced platform plugin in production. Record runtime Qt version and actual style class in existing implementation/version strings; no protocol schema expansion.
- [ ] **Exercise real process behavior.** A separate test-only `qt-fixtures/qt_fixture_main.cpp` creates QApplication, sets distinctive QPalette values through public APIs and calls dc_qt_run. It watches a temporary palette fixture file through QFileSystemWatcher and applies changed values via QApplication::setPalette, triggering genuine Qt palette events. The Python driver owns and edits that file. Production qt_main has no fixture environment keys or palette setters. This proves the event/protocol path; Task 1 and real desktop acceptance separately prove platform integration. Send alternating dark/light samples while its palette stays Light: same PID, same Light data, matching generations and timely replies. Trigger Dark, receive Dark without restart; ping; close stdin and require exit. Verify zero mapped windows and no config writes by directory diff. Run missing display/plugin and bounded stderr/error tests. Production output directories contain no test driver or fixture files.
- [ ] **Verify GREEN.** `./gradlew :jasper-app:testDesktopProtocol :jasper-app:testDesktopQt --no-daemon --console=plain` on each baseline Qt prefix. Existing GTK/portal tests also pass. Run C++ sanitizers in a supported native environment; label unsupported instrumentation honestly.
- [ ] **Commit:** `feat: add isolated Qt palette helpers`, with required trailer.

### Task 3: Implement KDE5/KDE6 extraction and live reconfiguration

**Files:** kde_probe.cpp, qt_probe.h, qt_main.cpp, CMakeLists.txt; kde_probe_test.cpp and qt-fixtures KDE configs; gradle/linux-desktop-colors.gradle; compatibility report.
**Interfaces:** `void dc_kde_extract(dc_palette *out)` is compiled separately against KF5 ConfigWidgets or KF6 ColorScheme. It reparses current system KConfig, creates Active KColorScheme sets, sets complete native pairs/actual polarity and never invokes a scheme manager. Metadata says KDE Frameworks major/version plus runtime Qt version. No cross-source pair completion.

- [ ] **Write tests with a temporary kdeglobals.** Give Window/View/Selection/Tooltip distinct foreground/background pairs. Assert colors match those values, Dialog matches Window, Sidebar is absent, and Header is present only when both compiled API and theme evidence support it. A missing Header remains absent rather than being guessed from Window. Write fixtures for custom scheme overrides, translucent ink, invalid colors, high-contrast black/white, and invalid/unavailable Window pair injection at the extraction boundary. Missing configuration keys alone are not evidence of an absent pair: KColorScheme may supply toolkit defaults, which the tests must distinguish from missing Header support. Reparse an edited fixture while the process remains alive and verify new pairs; assert no fixture writes by helper.
- [ ] **Observe RED** using new `./gradlew :jasper-app:testDesktopKde --no-daemon --console=plain` for each major.
- [ ] **Implement paired KColorScheme reads.** Use Task 1's proven public Header guards. Refresh KConfig at explicit samples and event samples, using the system config cascade and fresh KColorScheme objects. Use the shared Qt loop for stdin/events/EOF and immutable serialization. A valid current palette of the wrong polarity, or no usable pair, still produces a complete reply and leaves the process alive. Do not activate or autosave a named scheme; do not read stale KDE config in a generic Qt helper.
- [ ] **Verify GREEN and runtime isolation.** Run testDesktopQt, testDesktopKde and testDesktopProtocol against Qt5/KF5 and Qt6/KF6 runtime installations. Inspect `/proc/<pid>/maps` in tests: exactly one Qt major, no other major or GTK. Verify event-driven refresh plus explicit periodic-sample reparse, zero mapped windows, and timely EOF. Missing KF libraries must fail only the KDE executable; the generic Qt sibling still runs.
- [ ] **Commit:** `feat: add KDE color scheme probes`, with required trailer.

### Task 4: Stage all helpers and prove optional release dependencies

**Files:** gradle/linux-desktop-colors.gradle, gradle/packaging.gradle, .github/workflows/ci.yml, .github/workflows/linux-packages.yml, packaging/desktop-colors/README.md; `jasper-app/src/main/java/dev/jasper/app/platform/DesktopColors.java` helperNames only, its DesktopHelperDirectoryTest; new `packaging/desktop-colors/test/verify-qt-package.py`; compatibility report and docs/STATUS.md.
**Interfaces:** Add `compileDesktopQtHelpers`, `testDesktopQt`, `testDesktopKde`, `verifyDesktopQtLinkage`, `verifyDesktopQtCompatibility` and aggregate `checkDesktopQtNative`. Existing compileDesktopHelpers/stageDesktopHelpers/verifyDesktopHelpers/verifyPackage include all seven production executables. Build prefixes from Task 1 select per-target configurations. App source modes are still GTK-only in this deliverable.

- [ ] **Add failing package assertions.** Assert executable mode, exact helper set (no fixture files), ELF machine, glibc and libstdc++ floor, no build-prefix RPATH/RUNPATH, intended Qt/KF NEEDED major, and absence of forbidden toolkit deps on portal/GTK helpers. Inspect actual DEB Depends/Pre-Depends and RPM Requires: adding these four executables must not add Qt/KF or their optional-only transitive libraries as mandatory app requirements.
- [ ] **Run RED** against the current staging/packaging tasks; new helper files and dependency policy must fail before the wiring is added.
- [ ] **Wire staging and verification.** Append all four names after existing helper names, retaining existing indexes until deliverable 2 removes positional dispatch. Compile helpers against the appropriate baseline prefixes. Build all four for release; missing build capabilities fail with actionable diagnostics. On non-Linux, tasks skip without querying CMake/Qt. Keep strict unresolved-library checks for existing mandatory executables; optional helpers get explicit build-equipped and missing-runtime checks, not a blanket ignored ldd result.
- [ ] **Fix package dependency scanning by exact executable path.** `rpmLibraryRequirements` currently scans every ELF; exclude only the four optional helper paths from mandatory dependency inference. Apply the equivalent exclusion to DEB dependency generation. Do not strip Qt/KF names globally, because another mandatory binary could legitimately need them. Preserve baseline runtime dependencies of the JVM/launcher/portal/GTK helpers. Examine generated metadata and test actual installation in a minimal image with no Qt/KF.
- [ ] **Add CI matrix and release gate.** Use pinned toolchain manifests/caches keyed by architecture, hashes and build flags. Both x86_64/aarch64 release jobs use baseline-compatible artifacts. Native tests cover Qt5/KF5, Qt6 floor and Qt6/KF6 with private display/bus and fonts; keep existing GTK jobs, worker caps and artifacts intact. Record a legitimate platform skip separately from a mandatory matrix failure. Cache population may be a separate build job, with a timeout based on the measured Task 1 build duration rather than the existing 30-minute packaging timeout; no unverified downloaded helper executables. Run the exact release artifact against both its minimum runtime and newer system runtimes, so a newer-build accidental symbol dependency cannot masquerade as floor support.
- [ ] **Run GREEN.** `./gradlew :jasper-app:checkDesktopQtNative :jasper-app:installDist :jasper-app:packageApp :jasper-app:verifyPackage --no-daemon --console=plain` on Linux. Inspect/install DEB/RPM artifacts in minimal and runtime-equipped distro containers. Verify packaged helper samples directly without launching Jasper GUI. Run Mac/Windows packaging verification for absence of Linux helpers. Capture memory/CPU of idle helpers over multiple 30-second sample boundaries, process counts and no repeated startups; this headless helper measurement is not a Jasper GUI benchmark.
- [ ] **Complete native gate and commit.** Record target versions, runtime linkage, counts, commands and exact limitations in the compatibility report. All four helpers must have baseline native evidence before proceeding to deliverable 2 Task 3 (source/UI activation). Commit `build: package optional Qt and KDE desktop helpers` with required trailer. Run independent whole-branch review; no automatic merge or push.

## Completion and handoff

This deliverable is complete when all seven helpers stage correctly, each new helper has reproducible native tests,
and package installation does not require Qt/KF. Mark every unrun architecture/desktop case explicitly; do not call
the complete compatibility matrix proven from the existing Ubuntu VM alone. Continue with the application plan only
after the relevant native gate passes. GUI acceptance belongs to the user and must not be silently marked complete.
