# Plan 4b — full terminal configuration implementation plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development task-by-task, with TDD and separate scoped reviews.

**Execution clarification:** Array validation points to the containing key’s exact source position; TomlJ element positions include preceding whitespace/comments.

**Goal:** Configurable typography, terminal behavior, session defaults and initial window size using the existing live reload system.
**Architecture:** Immutable expanded snapshots feed retained terminal views; immutable launch settings capture new-process defaults before worker dispatch. Shared font metrics drive every coordinate calculation.
**Tech Stack:** JBR25, Swing/FlatLaf, TomlJ1.1.1, Gradle/JUnit/AssertJ.
**Spec:** docs/superpowers/specs/2026-09-12-moray-plan-4b-terminal-config-design.md

## Global Constraints
- Work on codex/plan-4b-terminal-config, base ab672fa. Coauthor trailers; no direct main commits.
- No GUI/benchmark/editor, real audio, real user config writes, merge or push without authorization. Tests use temporary files, controlled sessions and injected boundaries.
- UI/live model work stays on EDT; file parsing, directory checks and process launches stay off EDT.
- Terminal never depends on app. No public JediTerm types; no new interfaces without two real implementations. Reuse existing shortcut/theme/session engines.
- App default font16, library default/reset14. Preserve current1.0 line metrics. Line height1–3, font6–72, dim0–1, columns5–500/default150, lines2–200/default45, scrollback0–1000000/default10000.
- Syntax/known-type errors reject; unknowns warn; invalid values default per field. Preserve positions and secret-free diagnostics, existing files and runtime per-field override policy.
- Existing sessions are retained. Shell/env/scrollback apply to new requests only, captured before dispatch. Window grid defaults affect new windows; no reload-induced repack.
- Custom theme files/system appearance, logging/env scrubbing, packaging, SSH/plugins and unrelated terminal hardening are out of scope.

### Task 1: Live terminal options, shared line metrics and bell lifecycle
**Files:** Modify moray-terminal/src/main/java/dev/moray/terminal/{TerminalOptions,FontSet,TerminalView}.java; create BellMode.java; focused tests in matching test package. Do not touch moray-app production files in this task.
**Produces exact public contracts:**
```java
public enum BellMode { VISUAL, SOUND, NONE }
public record TerminalOptions(String fontFamily, float fontSize, List<String> fallbackFonts,
 boolean ligatures, Palette palette, CursorStyle cursorStyle, boolean cursorBlink,
 OptionAsMeta optionAsMeta, int scrollback, boolean copyOnSelect, float lineHeight, BellMode bell) {
 // Retain the old ten-argument constructor, delegating with lineHeight=1f, BellMode.VISUAL.
 // defaults() retains font14 and all old defaults, with lineHeight1f and bellVISUAL.
}
// FontSet retains its four-argument constructor, delegating to:
public FontSet(String family, float size, List<String> fallbackFamilies, boolean ligatures, float lineHeight);
// TerminalView, EDT-owned:
public TerminalOptions options();
public void applyOptions(TerminalOptions next);
// Package-private test seam, default production Runnable is Toolkit.getDefaultToolkit()::beep:
void setBellSound(Runnable sound);
```
- [x] Write real RED tests for typography updates, option/meta/copy/cursor changes, no session replacement and unchanged state on equal options. Use FakeConnector/session/view helpers. Pin existing1.0 metrics and test1.5 metrics, hit testing/report coordinates and resized grid using actual FontSet, not duplicated renderer arithmetic. Retain program cursor override/RIS cases.
```java
var natural = new FontSet("JetBrains Mono", 16f, List.of(), true);
var spaced = new FontSet("JetBrains Mono", 16f, List.of(), true, 1.5f);
assertThat(spaced.cellWidth()).isEqualTo(natural.cellWidth());
assertThat(spaced.cellHeight()).isEqualTo((int)Math.ceil(natural.cellHeight() * 1.5));
assertThat(spaced.ascent()).isEqualTo(natural.ascent() + (spaced.cellHeight()-natural.cellHeight())/2);
```
- [x] Extend the immutable options record with defensive validation (non-null enums/palette, valid font/fallback strings, finite size/lineHeight ranges, scrollback range); preserve all old constructor call sites. Implement the FontSet overload with naturalHeight then scaled height/centered baseline as the spec defines. Keep existing cell width logic. No independent line-height fields in coordinate consumers.
- [x] Implement applyOptions: compare old/current typography; rebuild FontSet/painter only as needed, replace KeyEncoder when optionAsMeta changes, update configured cursor/copy/bell fields, and delegate palette updating through existing highlight/color behavior. Store coherent options after direct setFontSize/setPalette. Refit the same session only when metrics change; fire minimum-size change/revalidate/repaint as existing font controls do. Scrollback is creation-only and must not mutate the session. Do not clear selection/find for behavior-only changes.
```java
// Shared metric calculation inside FontSet after natural metrics are known:
cellHeight = Math.max(naturalHeight, (int)Math.ceil(naturalHeight * lineHeight));
ascent = naturalAscent + (cellHeight - naturalHeight) / 2;
```
- [x] Write RED bell tests with real BEL input and attached lightweight components. Verify visual paint changes and settles, SOUND calls injected Runnable, NONE ignores, bursts coalesce and detach/reattach rejects stale events. No real beep, native window or sleeps. A package-private deterministic timer completion helper is allowed if tests drive the actual callback.
- [x] Implement the existing session bell listener with an attachment-generation guard and AtomicBoolean coalescing before EDT publication. Visual overlay is foreground at15% opacity for150ms on one nonrepeating timer; repeated events restart it. Mode change/detach clears and stops it. Sound uses the injected callback, never from reader thread. Keep existing frame/blink lifecycle otherwise unchanged.
- [x] Run focused RED/GREEN tests then :moray-terminal:check and source hygiene; self-review and commit/report exact APIs and results. Root owns spec/plan/STATUS.

### Task 2: Expanded immutable settings, positioned parsing and template
**Files:** Create moray-app/src/main/java/dev/moray/app/{FontConfig,TerminalConfig}.java. Modify ConfigSnapshot,ConfigLoader,ConfigTemplate and focused app tests. Reuse Task1 TerminalOptions/BellMode.
**Produces package-private contracts:**
```java
record FontConfig(String family, float size, List<String> fallback, boolean ligatures, float lineHeight) {
 static FontConfig defaults(); // JetBrains Mono,16,existing two fallbacks,true,1
 FontConfig withSize(float size);
}
record TerminalConfig(Shell shell, Map<String,String> env, int scrollback, OptionAsMeta optionAsMeta,
 CursorStyle cursorShape, boolean cursorBlink, float dimInactivePanes, boolean copyOnSelect, BellMode bell) {
 record Shell(String program,List<String> args) {}
 static TerminalConfig defaults(); // empty program/args/env,10000,LEFT,BLOCK,true,.3,false,VISUAL
}
record ConfigSnapshot(int tabHeight, WindowContent.ToolbarMode toolbar, boolean statusBar,
 FontConfig font, BuiltinTheme theme, Map<String,String> keybindings, int columns, int lines,
 TerminalConfig terminal) {
 // Preserve old six-argument constructor and these existing methods:
 static ConfigSnapshot defaults();
 float fontSize(); // font.size()
 KeyBindings bindings(boolean macOs);
 TerminalOptions viewOptions(float effectiveSize, Palette effectivePalette);
}
```
viewOptions maps FontConfig/TerminalConfig to Task1 constructor; supplied effective size/palette preserve runtime choices. The old constructor delegates to new defaults with FontConfig.withSize, columns150/lines45.
- [x] Write RED real TOML tests for every new setting/default/range, inline/nested tables, array element types, env map values, exact source positions, unknown/empty nested tables, quoted dotted keys and NUL input values. Test per-field fallback, type rejection, collection copies, old constructor compatibility, supported template uncommented defaults and existing binding round trips.
```java
var r = ConfigLoader.parse(Path.of("config.toml"), "[font]\nline_height=1.5\n[terminal]\nscrollback=321\n[terminal.shell]\nprogram='example-shell'\nargs=['--login', 'one argument']", true);
assertThat(r.rejected()).isFalse();
assertThat(r.snapshot().font().lineHeight()).isEqualTo(1.5f);
assertThat(r.snapshot().terminal().shell().args()).containsExactly("--login", "one argument");
```
- [x] Implement validated records and expand parser traversal for window/font/terminal and nested shell/cursor/env. Retain positions using List<String> paths, never split quoted path components. Known table/value/array-element types reject on mismatch; invalid scalar/list values default that field. An invalid env entry is omitted with ERROR while valid entries survive. Names must match [A-Za-z_][A-Za-z0-9_]*. Reserved TERM/COLORTERM warn and are omitted. Do not echo arguments/env values in diagnostics; unknown env names are data, not unknown config keys.
```java
// Snapshot conversion, with argument order matching Task1 exactly:
return new TerminalOptions(font.family(), effectiveSize, font.fallback(), font.ligatures(),
 effectivePalette, terminal.cursorShape(), terminal.cursorBlink(), terminal.optionAsMeta(),
 terminal.scrollback(), terminal.copyOnSelect(), font.lineHeight(), terminal.bell());
```
- [x] Expand the commented Settings template to every new supported setting with exact defaults/ranges and live versus new-pane/new-window behavior. Keep CREATE_NEW-only lifecycle unchanged. Empty program uses default command plus appended args; explicit program gets only configured args. Clarify missing-font fallback, forced TERM/COLORTERM and temporary runtime font/theme overrides. Extend real parse/round-trip tests rather than mirroring prose.
- [x] Run focused RED/GREEN and :moray-app:check/hygiene; commit/report. No service or UI integration changes in this task.

### Task 3: Captured launch defaults and initial window sizing
**Files:** Create moray-app/src/main/java/dev/moray/app/{LaunchSettings,InitialWindowSize}.java. Modify ShellLauncher,TerminalPane,MorayApplication,TerminalWindow,ConfigurationController and focused tests. Task4 owns full live view application.
**Produces package-private contracts:**
```java
record LaunchSettings(List<String> command, Map<String,String> environment,
 int columns, int lines, int scrollback) {
 static LaunchSettings resolve(ConfigSnapshot snapshot,String osName,Map<String,String> inherited,
   int windowColumns,int windowLines);
 String label();
}
// ShellLauncher becomes a focused class, retaining legacy constructor/label().
ShellLauncher(Executor executor, Function<Path,TerminalSession> start, String label);
ShellLauncher(Executor executor, Supplier<LaunchSettings> settings,
 BiFunction<Path,LaunchSettings,TerminalSession> start);
String launch(Path directory, BiConsumer<TerminalSession,Throwable> completion);
// ConfigurationController, EDT only, returns latest immutable state snapshot:
ConfigSnapshot snapshot();
final class InitialWindowSize {
 static Dimension terminalArea(ConfigSnapshot snapshot); // grid * shared metrics + 48px padding
 static Dimension fit(Dimension packed, Dimension minimum, Rectangle usableBounds);
}
```
- [x] Write RED tests that capture a requested launch into a queued executor, change settings, then execute; verify old command/args/env/scrollback/label remain captured, while the next request gets new values. Exercise exact argument boundaries, immutable environment overlay, default login shell append behavior, platform default resolution, forced TERM/COLORTERM, per-window frozen grid and capture/execute errors. Use standard injected BiFunction and controlled sessions, never user/login shells.
```java
var settings = LaunchSettings.resolve(snapshot, "Mac OS X", Map.of("SHELL","/bin/zsh", "KEEP","yes"), 150,45);
assertThat(settings.columns()).isEqualTo(150);
assertThat(settings.environment()).containsEntry("KEEP","yes");
// Test actual queued ShellLauncher: callback must reach EDT and use the captured value,
// not supplier.get() from inside its worker.
```
- [x] Implement LaunchSettings resolution with defensive copies. Default program uses DefaultShell.command(os,inherited) plus args; explicit program uses one executable plus exact args. Merge env without mutating inherited map, enforce TERM/COLORTERM. Safe label comes from captured executable; invalid path syntax must not throw while formatting an error label.
- [x] Implement configured ShellLauncher capture on request thread (production EDT), directory/process work on executor, EDT completion for capture/scheduling/launch failures. Return captured label, and store it in TerminalPane.start; existing constructor/callback call sites remain compatible. Cleanup of close-before-launch-completion remains unchanged.
- [x] MorayApplication creates a configured launcher per new window: capture that window's columns/lines once and supply current configuration.snapshot() for each later launch's session defaults. Actual worker calls TerminalSession.start(captured command/env/directory/grid/scrollback). No UI snapshot reads on worker. Legacy configuration-free construction uses defaults.
- [x] Add initial geometry RED tests with actual FontSet metrics and root/content sizing; prove lines/columns and typography affect initial area, that1.0 defaults are coherent, clamps respect minimum/screen constraints, and reload/pending completion do not repack windows. Implement terminalArea from fonts and48px padding. Set first pane preferred area before TerminalWindow.frame.pack; use InitialWindowSize.fit with usable monitor bounds afterward. In a too-small screen prefer the usable bounds over an impossible minimum. Preserve unconfigured fixture geometry.
```java
FontConfig f = snapshot.font();
FontSet fonts = new FontSet(f.family(), f.size(), f.fallback(), f.ligatures(), f.lineHeight());
return new Dimension(snapshot.columns()*fonts.cellWidth()+48,
 snapshot.lines()*fonts.cellHeight()+48);
```
- [x] Run focused tests and app check/hygiene; commit/report, including helper/API decisions. Root owns documentation status.

### Task 4: Live view application and complete user documentation
**Files:** Modify WindowContent,TerminalPane,ConfigurationController only as needed; integration tests, README/docs/configuration.md. Root owns STATUS/spec/plan/native checklist.
**Consumes:** ConfigSnapshot.viewOptions, font/terminal groups, TerminalView.applyOptions/options and configured launch/sizing paths.
**TerminalPane addition:** void setConfiguredDim(float amount); setActive/applyTheme must use stored value rather than hard-coded .3.
- [x] Write RED real ConfigService→WindowContent/JRootPane tests covering live typography/input/cursor/copy/bell/dim updates across windows, hidden/zoomed and pending views; stable session identity; native text editing and existing shortcut behavior. Verify unrelated setting changes preserve manual font size and global theme; changing font.family/line_height also preserves a manual size when font.size is unchanged; changing size/reset/new panes uses saved size. Verify focus/theme changes retain configured dimming.
```java
// For each retained ready pane when relevant saved fields change:
float size = previous == null || previous.fontSize()!=next.fontSize()
    ? next.fontSize() : pane.view().fontSize();
pane.view().applyOptions(next.viewOptions(size, pane.view().palette()));
pane.setConfiguredDim(next.terminal().dimInactivePanes());
```
- [x] Replace size-only application with grouped field comparisons. Do not apply options for unrelated chrome/binding/session-only changes. Save current font default for reset; configurePane uses the latest snapshot's live options with current shared theme when pending views become ready. Apply dimming to all panes, including pending. Keep launch settings untouched for existing sessions. Preserve palette/manual size explicitly when building options.
- [x] Complete behavioral coverage through actual view input/selection/BEL paths where the module tests supply safe seams; never add fake UI or inspect source strings. Check no stale callback mutates closed owners, no permanent timers introduced by bell detach, and current sizing/launch snapshots coexist with live changes.
- [x] Update README and configuration guide with full supported table/examples, ranges/defaults, live/new-window/new-pane semantics, exact shell arguments/env overlay, reserved variables, missing-font fallback, initial-screen/minimum constraints and remaining Plan4 scope. Preserve native acceptance links; no internal milestone labels in controls.
- [x] Run covering RED/GREEN tests, full ./gradlew check and hygiene, self-review and commit/report. Root performs final independent checks/review and handoff.

## Root acceptance
- [ ] Verify per-task reviews and final whole-branch review, resolving findings under the SDD fix rules.
- [x] Fresh full check, XML counts and source/diff hygiene; native GUI/audio/platform acceptance stays user-run.
- [ ] Record full Plan4b scope, remaining theme/packaging work, all rulings and actual verification in STATUS. Clean only this plan's scratch workspace after preserving its decisions.
