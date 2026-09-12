# Plan 4a — saved settings and live reload

**Status:** All three implementation tasks, scoped reviews and final whole-branch review complete through `acd5ea3`. Fresh full check: 394 tests, 393 passed and one known font skip. Actual status renders inspected in both themes at normal/narrow widths. The final review’s Windows test portability finding is fixed and re-reviewed; no findings remain. Native and Windows execution remain unverified. The implementation remains on the feature branch pending integration.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task. Each task owns its implementation, TDD cycle, scoped commit and independent review.

**Goal:** Working saved settings, live reload, Settings/Reload controls and actionable config diagnostics.
**Architecture:** Immutable parsed snapshots → one background file service → an EDT application controller using existing WindowContent/ThemeController/KeyBindings. No terminal renderer changes.
**Tech Stack:** JBR25, Swing/FlatLaf3.7, Gradle, TomlJ1.1.1, JUnit/AssertJ.
**Spec:** docs/superpowers/specs/2026-09-11-moray-plan-4a-config-design.md

## Global Constraints
- UI/model operations stay on EDT; file I/O, parsing and Desktop/editor operations never run on EDT.
- No GUI/benchmark, real user config writes, remote push or merge by agents without existing authorization.
- Work on codex/plan-4-config with coauthor trailers. Terminal never depends on app, no public JediTerm types, no new interfaces without two real implementations, no plugins/SSH scope.
- Supported settings only: window.tab_height(38,28–72), window.toolbar(icons_and_labels/icons/hidden), window.status_bar(true), font.size(16,finite6–72), colors.theme(moray-dark/moray-light), keybindings(existing platform parser/defaults).
- Syntax/known-type errors retain last-good snapshot; unknown keys/actions warn; invalid values fall back to their defaults with error diagnostics. Invalid/colliding bindings default the whole mutually constrained binding map.
- Existing files are never overwritten; only explicit Settings creates a commented template. Runtime View changes remain temporary overrides; changing another file field must not reset them.
- Preserve sessions, selection/find, hidden/zoomed panes and manual font overrides unless the corresponding configured field changes. No terminal-module API or renderer edits for this slice.

### Task 1: Paths, arguments and immutable TOML validation
**Files:** Create AppDirs.java, AppArguments.java, ConfigSnapshot.java, ConfigDiagnostic.java, ConfigLoader.java and focused tests under moray-app/src/{main,test}/java/dev/moray/app; modify moray-app/build.gradle.kts for implementation("org.tomlj:tomlj:1.1.1").
**Produces these package-private contracts:**
```java
record AppDirs(Path root, Path configFile, Path themes, Path logs) {
    static AppDirs resolve(String osName, Map<String,String> env, Path home);
}
record AppArguments(Path configOverride, boolean help) {
    static AppArguments parse(String[] args, Path workingDirectory);
}
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
                      float fontSize, BuiltinTheme theme, Map<String,String> keybindings) {
    static ConfigSnapshot defaults();
    KeyBindings bindings(boolean macOs);
}
record ConfigDiagnostic(Severity severity, Path file, int line, int column, String key, String message) {
    enum Severity { WARNING, ERROR }
    String formatted();
}
final class ConfigLoader {
    record Result(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, boolean rejected) {}
    static Result parse(Path file, String text, boolean macOs);
}
```
Reject invalid direct snapshot construction; copy all maps/lists. Result.snapshot is candidate/defaults when rejected, and callers must retain prior snapshot on rejected=true. Diagnostics positions1-based, line0 only when unavailable. No raw arbitrary values in messages.
- [x] Write failing real-input tests for all platform path fallbacks/override normalization, help/unknown/missing/duplicate args; no directories created. Cases include relative XDG path and missing APPDATA.
```java
assertThat(AppDirs.resolve("Mac OS X", Map.of(), Path.of("/home/test")).configFile())
    .isEqualTo(Path.of("/home/test/.config/moray/config.toml"));
```
- [x] Write parser RED tests for all supported fields, quoted keys and keybinding brace/none swaps; precise syntax/type/unknown/value source lines including empty unknown tables and wrong-type known tables. Unknown action warns and ignores; collisions/invalidbinding map defaults witherror. Duplicatekey/malformedsyntax reject; invalid values fall back per key, other valid fields apply.
```java
var result = ConfigLoader.parse(Path.of("config.toml"), "[window]\ntab_height = 44\n[font]\nsize = 900", true);
assertThat(result.rejected()).isFalse();
assertThat(result.snapshot().tabHeight()).isEqualTo(44);
assertThat(result.snapshot().fontSize()).isEqualTo(16f);
assertThat(result.diagnostics().getFirst().line()).isEqualTo(4);
```
- [x] Add TomlJ and implement typed validation using Toml.parse(text), inputPositionOf(List<String>), table key-path APIs and explicit expected types. Known container tables checked before leaves; dynamic keybindings entries handled separately. Preserve quoted dotted unknown keys in diagnostics. Error messages describe required type/range/action without echoing values. Invalid binding whole-map fallback is distinct from fatal type rejection. Parse only; no I/O or Swing setup. Default bindings use actual existing platform engine.
- [x] Run focused tests RED/GREEN, app check and hygiene; commit Task1 files with coauthor and write report to plan scratch dir. Root owns STATUS, spec and plan.

### Task 2: Safe file lifecycle, generated template and background reload
**Files:** Create ConfigService.java, ConfigTemplate.java and ConfigServiceTest/ConfigTemplateTest; touch KeyBindings helper only if needed to share effective default string formatting. No WindowContent or GUI integration yet.
**Consumes:** Task1 contracts. **Produces:**
```java
final class ConfigService implements AutoCloseable {
    record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {}
    ConfigService(Path file, boolean macOs); // initial read off EDT, owns one background scheduled worker
    State initialState();
    boolean macOs(); // stable parse platform, also used when applying bindings
    void start(Consumer<State> listener); // one listener, published on EDT by default
    CompletableFuture<State> reload(); // forced read, serialized with polling
    CompletableFuture<Path> openSettings(Consumer<Path> opener); // background create-if-absent then open
    public void close();
}
final class ConfigTemplate {
    static String text(boolean macOs);
    static void ensureExists(Path file, boolean macOs) throws IOException;
}
```
A package-private constructor may accept ScheduledExecutorService and Consumer<Runnable> publisher for deterministic lifecycle tests; do not add a custom interface. Service owns supplied worker and shuts it down on close. State defensively copies diagnostics. Default constructor/initial read called only off EDT.
- [x] Write filesystem RED tests with @TempDir: initial missing defaults/no creation, valid load, invalid file retainslast-good, recovery, delete defaults, same-mtime forced reload, unchanged poll with no duplicate publication, unreadable/oversized (>1MiB) rejection. Test callback delivery on EDT, opener off EDT and close drops already queued publication. Use futures/latches/manual publisher, no sleeps/timing guesses.
```java
Files.writeString(file, "[window]\ntab_height=44");
try (var service = new ConfigService(file, true)) {
    assertThat(service.initialState().snapshot().tabHeight()).isEqualTo(44);
    Files.writeString(file, "[window");
    assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(44);
}
```
- [x] Template RED tests: generated template parses clean on both platforms, comments document every supported key, default and live behavior andevery action ID, uncommented individual default keybindings roundtrip actual effective platform defaults (including nonMacAltcompatibility). Existing file bytes survive repeated/concurrent ensureExists; parent directories created only when explicitly requested; reader errors reported rather than overwriting. Cover actual functions, notsource-text mirroring.
- [x] Implement bounded UTF8 read via readNBytes(1MiB+1), immutable State and last-good retention. Poll every 1 second on one scheduled worker; fingerprint mtime + size, forced reload bypass; missing file defaults; parse/I/O errors publish diagnostics; publish changed state only. Every queued EDT callback checks closed. Initial state available before UI; start a single listener once. No service-owned UI or application launch. Requests after close fail/ignore coherently without queued leaks.
- [x] Implement documented template and CREATE_NEW only, catch FileAlreadyExists without rewriting. openSettings executes ensureExists then the injected opener off EDT and reloads the created template; an exception completes the future exceptionally so the application can report it. Never open the actual desktop in tests. Run focused tests, app check and hygiene, commit/report.

### Task 3: Live application settings, actions and status diagnostics
**Files:** Create ConfigurationController.java, ConfigEditor.java and integration tests. Modify Main.java, MorayApplication.java, TerminalWindow.java, WindowContent.java, WindowChrome.java, WindowStatusBar.java and theme properties; README/docs/configuration.md. TerminalPane only if required to apply configured fonts to pending launches; no terminal-module changes.
**Consumes:** Task1/2 contracts. **Produces:** Actual runnable file-backed app. Suggested coordinator boundary:
```java
final class ConfigurationController implements AutoCloseable {
    ConfigurationController(ThemeController themes, ConfigService service); // EDT
    void register(WindowContent owner);
    void unregister(WindowContent owner);
    void accept(ConfigService.State state); // EDT
    public void close();
}
```
Controller uses service.macOs() whenever it materializes snapshot bindings; the parse and application platforms must match. Select the initial theme once and update it when the configured theme changes. Registering a new owner must preserve the current global theme. The controller owns the service lifecycle. Existing owner constructors remain compatible with fixtures; configuration actions are enabled only when connected. Make WindowContent.bindings replaceable, store the configured snapshot and font reset default, and provide callbacks for Settings, Reload, diagnostics and unregister. Keep the existing shortcut engine.
- [x] Write failing tests using actual WindowContent/JRootPane instances and a temporary ConfigService. Cover initial settings, live height/toolbar/status/font/theme across multiple owners and hidden views, retained sessions, pending launches inheriting the latest configured font, and reset using the configured size. Unrelated file changes preserve manual font/height/theme/visibility; changing a field reapplies it. New owners get saved defaults without resetting a manually selected global theme. Use controlled launchers; never launch the GUI.
- [x] Write failing live-binding tests: remove old root strokes, dispatch actual actions through new strokes, clear accelerators with none, preserve native find-field copy/paste, and update toolbar hints through Action. Invalid syntax updates status while retaining working settings. Clicking warning/error status exposes selectable plain-text details with path/line; right-side content stays bounded at narrow widths. Settings/Reload enable and dispatch. Closing unregisters the owner and blocks late updates.
```java
// After an actual file reload removing NEW_TAB:
assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(oldStroke)).isNull();
assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isNull();
```
- [x] Parse Main arguments off EDT; help/errors exit without UI. Resolve the path and construct the service before invoking Swing. Construct MorayApplication with the service and create ConfigurationController using the existing ThemeController. Register WindowContent before binding/showing the frame. Configure new/pending panes through the existing onReady path. Close the controller/service at application shutdown, unregister each closed owner, and preserve existing constructors/callers through overloads.
- [x] Apply per-field differences only. Initial registration applies height/toolbar/status/font/bindings. File font changes update every view and stored default; new views read that latest default, and FONT_RESET uses it. setBindings removes old root maps, clears all action accelerators and installs the replacement; handlers consult the current engine. ThemeController owns global theme and live palette application. Do not restart shells or clear terminal state.
- [x] ConfigEditor uses Desktop edit/open then reveal on the service worker, with injected Consumers for failure/fallback tests. Do not invoke the actual desktop in tests. Report Settings failures through owner.onError on EDT. Add an accessible config button to status, with green/amber/red semantic colors, a path tooltip and a selectable plain-text diagnostic dialog. Preserve shell/path/grid metadata, background and minimum-width contracts; disable HTML rendering for untrusted strings. Clear callbacks on close.
- [x] Add README/configuration documentation covering supported keys, paths/--config, Settings creation, reload/error semantics, temporary runtime overrides, actual macOS/non-macOS shortcut examples and remaining Plan 4 scope. Preserve the native checklist; root owns STATUS. Run covering tests, full check and source hygiene; commit and report.

## Root acceptance
- [x] Independent task reviews, final whole-branch review and verified fixes.
- [x] Fresh full check, source hygiene and diff checks; inspect actual headless status geometry where changed.
- [x] STATUS records the merged UI baseline, Plan 4a delivery, remaining Plan 4 scope, all rulings and pending native acceptance.
