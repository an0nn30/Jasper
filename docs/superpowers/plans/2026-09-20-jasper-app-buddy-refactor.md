# Jasper app and Buddy refactor Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Jasper's application maintainable by feature ownership, extract a reusable Buddy library, and document how to develop both without conversation history.

**Architecture:** Keep `jasper-app` as the composition root over independent `jasper-terminal` and `jasper-buddy` libraries. Break existing dependency cycles with concrete owners, immutable values and narrow JDK callbacks before moving packages. Preserve existing algorithms and tests; introduce patterns only where they describe real ownership or construction.

**Tech Stack:** Java 25, JetBrains Runtime 25, Swing/AWT, Gradle wrapper, existing pinned FlatLaf/TOML/JBR dependencies, JUnit 6.1.3 and AssertJ 3.27.7. Buddy production uses JDK classes only.

**Spec:** [Approved app/Buddy design](../specs/2026-09-20-jasper-app-buddy-refactor-design.md).

**Status:** Merged into local `main` through `9b30dc2` on 2026-09-21; Tasks 1–12 complete; independent review and shutdown fixes verified, based on `c7b796b`; design commit `d33a669`. Native execution preference is preserved. Execution is tracked below and in the verification report. Each completed task must update its checkboxes and record deviations here and in `docs/STATUS.md`.

## Global Constraints

- Keep Java/JBR 25, Swing, Gradle wrapper and pinned dependencies.
- No new DI framework, event bus, plugin loader, JPMS conversion, emulator abstraction or external service.
- No new production interface without two real implementations; existing meaningful interfaces and JDK functional callbacks remain appropriate.
- `jasper-app` depends on both libraries; neither library depends on the app or the other library. No `jasper-common`.
- Keep `dev.jasper.app.Main` as the small stable launcher. App public visibility is not a supported SDK.
- App imports only supported terminal and Buddy APIs. No terminal `internalAccess()`, vendor types or library internal packages.
- Exactly five supported Buddy top-level types: `view.BuddyCompanion`, `notice.BuddyNotice`, `notice.BuddyNoticeId`, `config.BuddyOptions`, `config.BuddyPosition`.
- Buddy notice/presentation operations are EDT-only; construction creates no native window, timer or worker. `show()` creates presentation lazily and returns false when unavailable.
- Keep the 50-notice cap, replacement/acknowledgement ordering, orphan behavior, animation scheduling and position-file format.
- Keep saved-versus-session settings, command IDs/shortcuts, captured palette targets, shell launch settings, residency and shutdown behavior.
- No GUI, shell-starting desktop checks, native benchmarks, push or merge during unattended execution. Branch commits only, with `Co-Authored-By: Codex <noreply@openai.com>`.
- Headless checks use `./gradlew`; do not introduce native windows into `check`.
- Existing source bodies at `c7b796b` are the migration source. A method transfer means transplant its complete body, tests and comments, then change only the dependencies named here; it is not permission to replace the algorithm.

## Review Focus

1. Startup failure after partial acquisition: close every acquired resource in reverse order, retain the original failure and release global handlers/endpoint locks (Task 7).
2. A session arriving after pane close or application quit: close it exactly once and never attach it; cleanup must not block the EDT (Tasks 6–7).
3. A queued palette completion after dismissal, scope replacement or origin closure: prove the completion was queued, deliver it, and leave the replacement UI untouched (Task 5).
4. Configuration reload with temporary font/theme/toolbar choices: preserve unchanged overrides, apply changed live defaults and capture new-session-only values at launch (Tasks 2, 4, 6).
5. Buddy hide/disable/close and delayed producer callbacks: hiding retains notices without animation work; final close releases closures; late command completion cannot resurrect a closed pane (Tasks 8–10).

---

## File ownership and migration manifest

Paths below are relative to the repository. `A` means `jasper-app/src/main/java/dev/jasper/app`, `AT` means `jasper-app/src/test/java/dev/jasper/app`, `B` means `jasper-buddy/src/main/java/dev/jasper/buddy`, and `BT` means `jasper-buddy/src/test/java/dev/jasper/buddy`. A listed class names its `.java` file. During Tasks 2–7, existing classes stay in their flat package so each checkpoint compiles; Task 11 applies the final app manifest atomically. New owners can start in their final package when their dependencies permit it. Buddy moves atomically with its facade in Tasks 8–9.

| Package under dev.jasper.app | Existing classes / responsibility |
| --- | --- |
| root | Main: stable launcher delegating to bootstrap |
| bootstrap | AppArguments; startup/role/failure-cleanup portions extracted from Main |
| application | JasperApplication, ConfigurationController; application composition and window-to-feature wiring |
| workspace | TerminalWindow, WindowContent, WindowTabs, TerminalDeck, TerminalTab, TabState, TabMotion, SplitTree, SplitDividerBorder, TerminalPane, TerminalTitle, FindBar, InitialWindowSize, WindowChrome, WindowStatusBar; workspace-specific WindowCommands and WindowCommandPalette adapters |
| commands | ActionId, Command, CommandRegistry, CommandSearch; generic command identity, metadata, ranking and dispatch contracts |
| palette | CommandPalette, PaletteContext, PaletteTarget, PaletteResults, PaletteRow, PaletteStep, PaletteVerb, PaletteScope, ScopeRegistry, PaletteKeyRouter; presentation and scope lifecycle |
| palette.builtin | CommandsScope, ShellHistoryScope, SnippetsScope; adapters over actual data owners |
| config | ConfigSnapshot, ConfigDiagnostic, ConfigLoader, ConfigService, ConfigTemplate, FontConfig, TerminalConfig, KeyBindings, Appearance, ShellExitBehavior, ShellIntegrationMode; independent ToolbarMode and palette/history setting values |
| appearance | BuiltinTheme, ResolvedTheme, ThemeState, ThemeController; resolution and global look-and-feel installation |
| launch | DefaultShell, LaunchSettings, ShellLauncher, ShellIntegrationScripts |
| history | CommandHistory, CommandHistoryFile, HistoryShell, ShellHistoryEntry, ShellHistoryIndex, ShellHistoryParser, ShellHistorySnapshot, ShellHistorySource |
| snippets | Snippet, SnippetFile, SnippetStore |
| notifications | CommandNotice, CommandNotifier, BuddyVisibility; command attention policy and app-to-Buddy notice adaptation |
| residency | HandoffSocket, LaunchRequest; interprocess protocol and background endpoint ownership |
| platform | AppDirs, AppLog, AppIcons, ApplicationIcon, SystemFonts, MacTitleBar, NativeNotifier, ConfigEditor, LoginItem |
| persistence | TomlStateFile, BuddyStateFile; bounded file access and atomic writes |
| lifecycle | Subscription: extracted concrete cancellation handle currently nested in CommandRegistry |
| benchmark | Bench, MemoryBench, BenchmarkFixture, BenchmarkLifetime, BenchmarkMetrics, BenchmarkOptions, BenchmarkRendering, BenchmarkReport, BenchmarkRun |

| Package under dev.jasper.buddy | Types / responsibility |
| --- | --- |
| view | BuddyCompanion: supported concrete facade, AutoCloseable |
| notice | BuddyNotice with nested Kind/State; BuddyNoticeId: supported identity value |
| config | BuddyOptions and builder, BuddyPosition: supported immutable inputs |
| internal.model | BuddyDeck: bounded notice state, acknowledgement, orphaning and generation |
| internal.animation | BuddyAnimator, BuddyFrame, BubbleMotion, BubbleSpring |
| internal.presentation | BuddyWindow, BuddySprite, BuddyBubble, BuddyBubbleContent, BuddyBubblePanel, BuddyBubblePlacement, BuddyCard, BuddyColumnPanel, BuddyColumnPlacement, BuddyColumnWindow, BuddyDeckLayout, BuddyDeckPanel, BuddyDeckWindow, BuddyDragFrames, BuddyPlacement |

New files and their only responsibilities:

| Final file | Responsibility |
| --- | --- |
| A/config/ToolbarMode.java | Toolbar value independent of Swing owners |
| A/config/PaletteSettings.java | Palette limit validation and default |
| A/config/HistorySettings.java | Immutable history enabled/trivial-command defaults |
| A/lifecycle/Subscription.java | Once-only removal of a registered listener |
| A/workspace/WorkspaceActivity.java | Typed command/pane activity records, no feature policy |
| A/workspace/WindowCallbacks.java | Native window lifecycle callbacks |
| A/workspace/WorkspaceActions.java | Existing action table, dispatch and enablement |
| A/workspace/WorkspaceConfiguration.java | Saved-setting replay and temporary overrides |
| A/palette/PaletteController.java | Scope/query/step state and stale-completion rejection |
| A/application/SessionLaunchCoordinator.java | Session admission, ownership and pending exits |
| A/application/ApplicationShutdown.java | Once-only bounded off-EDT termination |
| A/bootstrap/StartupResources.java | Acquisition rollback and ownership transfer |
| A/bootstrap/ApplicationBootstrap.java | Startup roles and composition formerly in Main |
| A/application/BuddyIntegration.java | Application visibility, activity and persistence wiring |
| B/config/BuddyOptions.java | Immutable presentation configuration with builder/toBuilder |
| B/config/BuddyPosition.java | Immutable screen coordinates |
| B/notice/BuddyNoticeId.java | Source-qualified stable identity |
| B/view/BuddyCompanion.java | Supported EDT facade over model and lazy presentation |
| gradle/application-architecture.gradle.kts | App and Buddy bytecode boundaries and DAG checks |
| docs/app-architecture.md | Ownership, dependencies, threading, startup and shutdown |
| docs/app-maintenance.md | Executable feature recipes and setting propagation checklist |
| docs/app-refactor-verification.md | Exact commands, test counts, review findings and manual gaps |
| jasper-app/README.md; jasper-buddy/README.md | New contributor and embedding entry points |

Every final production package also receives `package-info.java`. Every production type receives responsibility/thread/ownership documentation; supported Buddy members document arguments, failure and close behavior. Avoid comments that merely repeat syntax.

Tests move beside their owner. Integration tests that join features belong to `application` or `workspace`; pure provider tests stay beside providers. Shared test-only fixtures move to `AT/testsupport`, with explicit public fixture methods only where required. `dev.jasper.terminal.view.TerminalKeyTestSupport` remains test-only. No test source or preview class enters a production jar.

## Task 1: Capture a reproducible baseline and migration ledger

**Files:** Create `docs/app-refactor-verification.md`; modify this plan and `docs/STATUS.md`. Read `build.gradle.kts`, `jasper-app/build.gradle.kts`, `settings.gradle.kts`, `gradle/packaging.gradle`, `A/Main.java`, and all resources under `jasper-app/src/main/resources`.

**Interfaces:** Consumes the existing Gradle `check` and `verifyTerminalArchitecture` tasks. Produces a recorded baseline of tests, resources, launcher names and Git revision; no production API.

- [x] Run the baseline without opening a GUI:

```bash
./gradlew verifyTerminalArchitecture check --rerun-tasks
```

- [x] Record XML totals and inventory with these commands. Do not copy the historical 1,083 total if the checkout differs.

```python
from pathlib import Path
import xml.etree.ElementTree as ET
for module in ('jasper-app', 'jasper-terminal'):
    suites = [ET.parse(p).getroot() for p in Path(module, 'build/test-results/test').glob('TEST-*.xml')]
    print(module, {k: sum(int(s.get(k, 0)) for s in suites)
                   for k in ('tests', 'failures', 'errors', 'skipped')})
for p in sorted(Path('jasper-app/src/main/resources').rglob('*')):
    if p.is_file(): print(p)
```

```bash
rg -n 'dev\.jasper\.app\.|getResource|registerCustomDefaultsSource' jasper-app gradle packaging README.md
```

- [x] Write the verification ledger with headings `Baseline`, `Task checkpoints`, `Architecture`, `Packaged resources`, `Documentation`, `Independent review`, `Manual acceptance`. Under baseline record actual commands, revision, results and expected skips. Under manual acceptance state desktop, native performance and Windows runtime checks are pending user execution.
- [x] Commit only the documentation files with message `docs: record app refactor baseline` and the required trailer.

## Task 2: Make settings and subscriptions independent values

**Files:** Create `A/config/{ToolbarMode,PaletteSettings,HistorySettings}.java`, `A/lifecycle/Subscription.java`; modify `A/{ConfigSnapshot,ConfigLoader,ConfigTemplate,WindowContent,PaletteContext,ShellHistoryScope,CommandSearch,CommandRegistry,ScopeRegistry,PaletteScope,CommandHistory,ShellHistoryIndex,SnippetStore}.java` and all consumers found by the searches below. Create `AT/config/SettingsValuesTest.java`, `AT/lifecycle/SubscriptionTest.java`; retain all existing config/search tests.

**Interfaces:** Produces `PaletteSettings(int maxResults)`, constants `DEFAULT_MAX_RESULTS=5`, `MIN_MAX_RESULTS=1`, `MAX_MAX_RESULTS=20`; `HistorySettings(boolean enabled,List<String> trivialCommands)`, `defaults()`; `ToolbarMode`; `Subscription(Runnable)` and `void close()`. `ConfigSnapshot` retains its 15-component canonical constructor and accessor names at this checkpoint.

- [x] Write value tests (JUnit/AssertJ imports) before adding the classes:

```java
@Test void historyDefensivelyCopiesAndPreservesAnExplicitEmptyList() {
    var input = new java.util.ArrayList<>(java.util.List.of("ls"));
    var value = new HistorySettings(true, input);
    input.clear();
    assertThat(value.trivialCommands()).containsExactly("ls");
    assertThat(new HistorySettings(true, java.util.List.of()).trivialCommands()).isEmpty();
}
@Test void paletteBoundsIncludeBothEndpoints() {
    assertThat(new PaletteSettings(1).maxResults()).isEqualTo(1);
    assertThat(new PaletteSettings(20).maxResults()).isEqualTo(20);
    assertThatThrownBy(() -> new PaletteSettings(0)).isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> new PaletteSettings(21)).isInstanceOf(IllegalArgumentException.class);
}
```

```java
@Test void reentrantCloseRemovesOnlyOnce() {
    var calls = new java.util.concurrent.atomic.AtomicInteger();
    var holder = new Subscription[1];
    holder[0] = new Subscription(() -> { calls.incrementAndGet(); holder[0].close(); });
    holder[0].close(); holder[0].close();
    assertThat(calls).hasValue(1);
}
```

- [x] Run `./gradlew :jasper-app:test --tests '*SettingsValuesTest' --tests '*SubscriptionTest'`; expect missing-class compilation failures.
- [x] Add these complete leaf implementations (one public type per named file with its package and imports):

```java
public enum ToolbarMode { ICONS_AND_LABELS, ICONS, HIDDEN }
public record PaletteSettings(int maxResults) {
    public static final int DEFAULT_MAX_RESULTS = 5;
    public static final int MIN_MAX_RESULTS = 1;
    public static final int MAX_MAX_RESULTS = 20;
    public PaletteSettings {
        if (maxResults < MIN_MAX_RESULTS || maxResults > MAX_MAX_RESULTS)
            throw new IllegalArgumentException("Max results must be 1–20.");
    }
}
public record HistorySettings(boolean enabled, java.util.List<String> trivialCommands) {
    public HistorySettings { trivialCommands = java.util.List.copyOf(trivialCommands); }
    public static HistorySettings defaults() {
        return new HistorySettings(true, java.util.List.of("exit", "clear", "ls", "ll", "la", "cd", "pwd", "c", "q", "logout"));
    }
}
public final class Subscription implements AutoCloseable {
    private Runnable removal;
    public Subscription(Runnable removal) { this.removal = java.util.Objects.requireNonNull(removal); }
    @Override public void close() {
        if (removal == null) return;
        Runnable once = removal;
        removal = null;
        once.run();
    }
}
```

`Subscription` is caller-thread-confined: the owner retains its EDT checks. Do not silently make callbacks concurrent.

- [x] Migrate references using this exhaustive search, then delete the nested enum/subscription and obsolete constructors:

```bash
rg -n 'WindowContent.ToolbarMode|CommandRegistry.Subscription|DEFAULT_TRIVIAL|PaletteContext.(MIN_MAX_RESULTS|MAX_MAX_RESULTS|DEFAULT_MAX_RESULTS)|CommandSearch.find' jasper-app/src
```

`ConfigSnapshot` validates `new PaletteSettings(maxResults)` and uses `HistorySettings.defaults().trivialCommands()`. Replace its `BuiltinTheme` constructors at call sites with `theme.appearance()` and `FontConfig.defaults().withSize(size)`. Keep convenience constructors only when they depend entirely on config-owned values. Move `PaletteContext` bounds/default references to config values; remove provider-dependent defaults. Delete the three-argument `CommandSearch.find`; pass an explicit limit at every caller (5 for callers that previously used its default). Preserve the four-argument ranking implementation exactly.
- [x] Add a ConfigSnapshot builder/toBuilder, preserving all 15 canonical fields. Keep `defaults()` constructing the canonical record directly to avoid builder recursion. Place this code inside ConfigSnapshot; retain its existing Map/List imports. All validation still runs in the canonical constructor.

```java
static Builder builder() { return new Builder(defaults()); }
Builder toBuilder() { return new Builder(this); }
static final class Builder {
    private int tabHeight;
    private ToolbarMode toolbar;
    private boolean statusBar;
    private FontConfig font;
    private Appearance variant;
    private Map<String, String> keybindings;
    private int columns;
    private int lines;
    private TerminalConfig terminal;
    private boolean buddyEnabled;
    private boolean historyEnabled;
    private int maxResults;
    private List<String> trivialCommands;
    private int longCommandSeconds;
    private boolean backgroundEnabled;
    private Builder(ConfigSnapshot source) {
        tabHeight = source.tabHeight();
        toolbar = source.toolbar();
        statusBar = source.statusBar();
        font = source.font();
        variant = source.variant();
        keybindings = source.keybindings();
        columns = source.columns();
        lines = source.lines();
        terminal = source.terminal();
        buddyEnabled = source.buddyEnabled();
        historyEnabled = source.historyEnabled();
        maxResults = source.maxResults();
        trivialCommands = source.trivialCommands();
        longCommandSeconds = source.longCommandSeconds();
        backgroundEnabled = source.backgroundEnabled();
    }
    Builder tabHeight(int value) { tabHeight = value; return this; }
    Builder toolbar(ToolbarMode value) { toolbar = value; return this; }
    Builder statusBar(boolean value) { statusBar = value; return this; }
    Builder font(FontConfig value) { font = value; return this; }
    Builder variant(Appearance value) { variant = value; return this; }
    Builder keybindings(Map<String, String> value) { keybindings = Map.copyOf(value); return this; }
    Builder columns(int value) { columns = value; return this; }
    Builder lines(int value) { lines = value; return this; }
    Builder terminal(TerminalConfig value) { terminal = value; return this; }
    Builder buddyEnabled(boolean value) { buddyEnabled = value; return this; }
    Builder historyEnabled(boolean value) { historyEnabled = value; return this; }
    Builder maxResults(int value) { maxResults = value; return this; }
    Builder trivialCommands(List<String> value) { trivialCommands = List.copyOf(value); return this; }
    Builder longCommandSeconds(int value) { longCommandSeconds = value; return this; }
    Builder backgroundEnabled(boolean value) { backgroundEnabled = value; return this; }
    ConfigSnapshot build() {
        return new ConfigSnapshot(tabHeight, toolbar, statusBar, font, variant, keybindings, columns, lines, terminal, buddyEnabled, historyEnabled, maxResults, trivialCommands, longCommandSeconds, backgroundEnabled);
    }
}
```

Add the following test to ConfigLoaderTest (same package as ConfigSnapshot), then migrate positional reconstruction in application/config tests and implementation to toBuilder where it modifies an existing snapshot:

```java
@Test void snapshotBuilderPreservesEveryUnchangedComponent() {
    var original = ConfigSnapshot.defaults().toBuilder().columns(101).lines(37)
        .historyEnabled(false).buddyEnabled(false).longCommandSeconds(23)
        .maxResults(17).trivialCommands(List.of("status")).backgroundEnabled(true).build();
    assertThat(original.toBuilder().build()).isEqualTo(original);
    var changed = original.toBuilder().toolbar(ToolbarMode.HIDDEN).build();
    assertThat(changed.toBuilder().toolbar(original.toolbar()).build()).isEqualTo(original);
    assertThatThrownBy(() -> original.toBuilder().maxResults(21).build())
        .isInstanceOf(IllegalArgumentException.class);
}
```

- [x] Run `./gradlew :jasper-app:test`; commit `refactor: isolate settings and lifecycle values` with trailer.

## Task 3: Replace reverse ownership with subscriptions and window callbacks

**Files:** Modify `A/{ThemeController,TerminalWindow,WindowContent,JasperApplication,ConfigurationController,MacTitleBar,Main,TerminalTitle}.java`; create `A/WindowCallbacks.java` (moves to workspace in Task 11). Update `AT/{ThemeControllerTest,ApplicationActionsTest,WindowChromeTest,WindowChromeHintTest,InitialWindowSizeTest,MainTest}.java` and create `AT/WindowCallbacksTest.java`.

**Interfaces:** `ThemeController.subscribe(BiConsumer<ResolvedTheme,Boolean>) -> Subscription`; workspace-owned `WindowCallbacks(Consumer<Path> newWindow,Runnable quit,Consumer<TerminalWindow> activated,Consumer<TerminalWindow> closed,Consumer<WindowCallbacks.State> stateChanged)`; nested `State(TerminalWindow window,boolean showing,boolean iconified)`. `MacTitleBar.install(JRootPane,JComponent,JComponent,IntSupplier,Runnable,boolean) -> MacTitleBar`; `setTitle(String,boolean)`, `setLight(boolean)`, `refreshHeight()`, `setActive(boolean)`, `attach(JFrame)`, `close()`.

- [x] Replace ThemeController's existing owner-registration test with callback registration while preserving all install/rollback tests. Add this test on the EDT using existing `DesktopTestSupport.edt`:

```java
@Test void closingThemeSubscriptionStopsFurtherDelivery() throws Exception {
    DesktopTestSupport.edt(() -> {
        var changes = new java.util.ArrayList<ResolvedTheme>();
        var themes = new ThemeController(theme -> true);
        var registration = themes.subscribe((theme, delegates) -> changes.add(theme));
        assertThat(changes).hasSize(1);
        registration.close(); registration.close();
        themes.select(BuiltinTheme.LIGHT);
        assertThat(changes).hasSize(1);
    });
}
```

- [x] Run `./gradlew :jasper-app:test --tests '*ThemeControllerTest'`; expect missing `subscribe`.
- [x] Replace `Set<WindowContent> owners` by an ordered set of `BiConsumer<ResolvedTheme,Boolean>`. Replace the apply-loop target with `listener.accept(next, chromeChanged)`. Implement registration with rollback if initial delivery fails:

```java
Subscription subscribe(java.util.function.BiConsumer<ResolvedTheme, Boolean> listener) {
    requireEdt(); java.util.Objects.requireNonNull(listener);
    if (!listeners.add(listener)) return new Subscription(() -> {});
    try { listener.accept(current(), true); }
    catch (RuntimeException | Error failure) { listeners.remove(listener); throw failure; }
    return new Subscription(() -> { requireEdt(); listeners.remove(listener); });
}
```

WindowContent owns and closes its returned subscription. ConfigurationController remains an application coordinator over config, theme and workspace. Remove all ThemeController imports/references to WindowContent.

- [x] Add the callback record with compact-constructor null checks on all five components. Replace TerminalWindow's `JasperApplication` field with the record. Its event handlers call `callbacks.activated().accept(this)`, `callbacks.closed().accept(this)` and `callbacks.stateChanged().accept(new WindowCallbacks.State(this, showing, iconified))`. Application constructs it from its existing methods. Move configuration registration out of TerminalWindow into JasperApplication immediately after window construction and before showing; pass the initial snapshot for sizing. Do not move ConfigurationController into workspace.

- [x] Decouple MacTitleBar: transplant the existing geometry/painting/native methods; replace stored `WindowContent` with supplied `JComponent tabs`, `IntSupplier tabHeight`, and `Runnable minimumSizeChanged`. Use `tabs.getMinimumSize()/setBounds`, `tabHeight.getAsInt()` and `minimumSizeChanged.run()`. Workspace wires title/theme/height notifications and calls `windowTabs().setActive` itself. `setTitle` updates the label and single-tab visibility; `setLight` sets the existing Aqua/DarkAqua root property then refreshes colors. `install` sets the root content, creates the wrapper only when supported, and never registers workspace callbacks. Move the exact `Main.windowTitle` body into `TerminalTitle.windowTitle` and update callers/tests. Close detaches native/listener resources; workspace clears its callbacks when it closes.

- [x] Run `./gradlew :jasper-app:test`; verify `rg 'WindowContent|JasperApplication|BuiltinTheme|Main\.' A/MacTitleBar.java` has no matches (expand `A` to its defined path). Commit `refactor: separate theme and platform ownership` with trailer.

## Task 4: Give workspace actions, settings and activity explicit owners

**Files:** Create `A/{WorkspaceActions,WorkspaceConfiguration,WorkspaceActivity}.java`; modify `A/{WindowContent,TerminalPane,TerminalWindow,JasperApplication,WindowCommands,ConfigurationController}.java`; update `AT/{ApplicationActionsTest,ConfigurationControllerTest,ConfigurationStatusTest,WindowCommandsTest,TerminalTitleIntegrationTest}.java`; create `AT/WorkspaceActivityTest.java`.

**Interfaces:** package-private `WorkspaceActions(WindowContent)`, `Action action(ActionId)`, `void invoke(ActionId)`, `void update()`, `boolean updating()`; package-private `WorkspaceConfiguration(WindowContent)`, `void apply(ConfigSnapshot,boolean)`, `ConfigSnapshot snapshot()`, `float configuredFontSize()`; public workspace value container `WorkspaceActivity` with nested records below. `WindowContent.activity(Consumer<WorkspaceActivity.Event>) -> Subscription`. Existing `WindowContent` action/settings entry points delegate, retaining callers during migration.

```java
public final class WorkspaceActivity {
    private WorkspaceActivity() {}
    public sealed interface Event permits Started, Finished, TitleChanged, PaneState {}
    public record Origin(boolean anyWindowActive, boolean ownWindowActive,
                         boolean ownTabSelected, boolean ownPaneFocused) {}
    public record Started(Object id, String command, java.util.function.LongSupplier elapsedNanos,
                          Runnable activate, boolean watched) implements Event {}
    public record Finished(Object id, String command, java.util.OptionalInt exitStatus,
                           java.time.Duration duration, Origin origin, Runnable activate) implements Event {}
    public record TitleChanged(Object id, String title) implements Event {}
    public enum State { OPENED, FOCUSED, BLURRED, CLOSED }
    public record PaneState(Object id, State state) implements Event {}
}
```

This is a closed local value family, not a plugin event bus or strategy interface. Remove `CommandStartedSink` and `CommandFinishedSink`. Each pane allocates a private final `Object activityId = new Object()`; callback actions are separate from the ID, so retaining an ID cannot retain a pane. Validate nonnull event fields in compact constructors, including activation for started/finished events.

- [x] Add identity and no-policy tests:

```java
@Test void activityIdentityDoesNotContainThePaneOrFeaturePolicy() {
    Object id = new Object();
    var event = new WorkspaceActivity.TitleChanged(id, "build");
    assertThat(event.id()).isSameAs(id);
    assertThat(event.title()).isEqualTo("build");
    assertThat(WorkspaceActivity.TitleChanged.class.getRecordComponents())
        .extracting(java.lang.reflect.RecordComponent::getType)
        .containsExactly(Object.class, String.class);
}
```

- [x] Run `./gradlew :jasper-app:test --tests '*WorkspaceActivityTest'`; expect missing type.
- [x] Transplant WindowContent's action-map construction, `invoke`, `updateActions`, and `updatingActions` flag into WorkspaceActions. Keep command registry/window-command registration in WindowContent; actions obtain the current tab/pane through WindowContent's existing accessors. Action callbacks capture the owner, not a tab from construction. WindowContent delegates exactly:

```java
void invoke(ActionId id) { workspaceActions.invoke(id); }
Action action(ActionId id) { return workspaceActions.action(id); }
void updateActions() { workspaceActions.update(); }
boolean updatingActions() { return workspaceActions.updating(); }
```

- [x] Transplant `configured`, `configuredFontSize`, `applyConfiguration` and `liveBehaviorChanged` into WorkspaceConfiguration. Change direct tab iteration to owner tab access; use narrow existing methods for toolbar/status/bindings/history/palette. Preserve the `previous == null || savedValueChanged` conditions; never replay all defaults indiscriminately. Route new-pane options through `workspaceConfiguration.snapshot()` and saved font size. Use `TerminalOptions.toBuilder()` where rebuilding an existing options value so all unrelated fields survive.
- [x] Implement local activity subscription with the same ordered-list/snapshot-iteration/removal idiom as CommandRegistry. On subscription, replay OPENED for each existing pane before returning the registration. Emit OPENED for each later pane before any command event. On pane close emit CLOSED before releasing callbacks; detach listeners on workspace close. Application translates the records into existing notifier calls, including conversion of `WorkspaceActivity.Origin` into `CommandNotice.Origin`. Workspace imports neither notifications nor Buddy.
- [x] Extend the existing configuration reload integration tests using their snapshot/pane fixtures: zoom a live pane, reload an identical snapshot, assert zoom preserved; change font default, assert live font updates; change only shell/grid/scrollback, assert no session replacement and next launch sees captured new settings. Retain existing temporary theme/toolbar/status tests. Run the full app tests after each extraction, then commit `refactor: extract workspace behavior owners` with trailer.

## Task 5: Extract palette state from window presentation

**Files:** Create `A/PaletteController.java`; modify `A/{WindowCommandPalette,PaletteTarget,PaletteContext,CommandPalette}.java`; update `AT/WindowCommandPaletteTest.java`, `AT/PaletteScopesTest.java`; create `AT/PaletteControllerTest.java`.

**Interfaces:** `PaletteController(ScopeRegistry,boolean macOs,Runnable layout,Runnable dismissed,Function<String,String> shortcut,BooleanSupplier batching,Runnable updateActions,Consumer<String> reportError,Consumer<String> reopen)`; `boolean open(String,PaletteTarget,BooleanSupplier originValid)`; `void dismiss()`, `close()`, `refresh()`, `refreshIfChanged()`, `setMaxResults(int)`, `setTrivialCommands(List<String>)`, `enterPressed(int)`, `escape()`, `executeNumber(int)`, `moveSelection(int)`, `openPicker()`; `boolean isOpen()`, `pickerOpen()`, `stepOpen()`, `tabPressed(boolean)`; `String activeScopeId()`. Retain presentation methods on CommandPalette. WindowCommandPalette creates `PaletteTarget` from its captured pane using the exact removed `PaletteTarget.of` body.

- [x] In the existing step tests, capture the `Consumer<PaletteStep.Result>` passed to the fake scope's completion. Add three cases that actually invoke completion after: dismiss/reopen, removal/replacement of scope, origin pane closure. Assert a replacement query, selection and error label remain unchanged, and no target paste/run occurs. Queue the delivery explicitly with `SwingUtilities.invokeLater` and drain the EDT before asserting; do not merely close a never-completed step.
Use this concrete queued case in PaletteScopesTest, reusing its existing owner/install/FakeScope fixtures:

```java
@Test void queuedOldCompletionCannotDismissReopenedPalette() throws Exception {
    var ownerRef = new java.util.concurrent.atomic.AtomicReference<WindowContent>();
    var delivered = new java.util.concurrent.atomic.AtomicBoolean();
    try {
        edt(() -> {
            var content = owner(true); ownerRef.set(content); install(content);
            var fake = new FakeScope(); fake.deferCompletion = true;
            content.scopes().register(fake);
            var palette = content.commandPalette();
            palette.open(fake.id()); palette.enterPressed(2); palette.enterPressed(0);
            assertThat(fake.pending).isNotNull();
            var oldCompletion = fake.pending;
            javax.swing.SwingUtilities.invokeLater(() -> {
                delivered.set(true);
                oldCompletion.accept(PaletteStep.Result.reopen(PaletteScope.COMMANDS_ID, "new_tab"));
            });
            palette.dismiss(); palette.open(fake.id());
            palette.component().queryField().setText("beta");
        });
        edt(() -> {
            assertThat(delivered).isTrue();
            var palette = ownerRef.get().commandPalette();
            assertThat(palette.isOpen()).isTrue();
            assertThat(palette.activeScopeId()).isEqualTo("test.fake");
            assertThat(palette.component().queryField().getText()).isEqualTo("beta");
        });
    } finally { edt(() -> { if (ownerRef.get() != null) ownerRef.get().close(); }); }
}
```

- [x] Run `./gradlew :jasper-app:test --tests '*WindowCommandPaletteTest' --tests '*PaletteScopesTest'`; preserve the baseline tests even if existing stale protection passes. Add a same-step-object reuse case; the old identity-only guard is insufficient to distinguish two opening generations.
- [x] Move these complete state-machine methods from WindowCommandPalette into PaletteController: `activate`, settings setters/getters, picker/step state accessors, `enterPressed`, `executeNumber`, `moveSelection`, `escape`, `tabPressed`, `showStep`, `closeStep`, `completeStep`, `queryChanged`, `changed`, `refreshIfChanged`, `refresh`, `rebuild`, `pickerResults`, `matchesScope` and scope matching helpers. Move their corresponding state/listener fields. Replace owner accesses with the constructor callbacks; preserve search/ranking/selection semantics.
- [x] Add a monotonically increasing generation; increment on open, dismiss, close, scope replacement, step cancellation and invalidated origin. Complete through this guard before mutating `completing` or card state:

```java
long submittedGeneration = generation;
PaletteStep submittedStep = step;
current.complete().accept(palette.stepValues(), result -> {
    Runnable apply = () -> {
        if (closed || !open || generation != submittedGeneration
                || step != submittedStep || !originValid.getAsBoolean()
                || !scopes.contains(active)) return;
        completing = false;
        acceptStepResult(result);
    };
    if (javax.swing.SwingUtilities.isEventDispatchThread()) apply.run();
    else javax.swing.SwingUtilities.invokeLater(apply);
});
```

`acceptStepResult(PaletteStep.Result)` is the original completeStep callback body from its error branch through reopening/selecting, excluding the old `completing=false` and identity guard. Its reopen goes through a new generation using the captured target only while originValid is true. Capture synchronous completion errors under the same generation guard before displaying them.

- [x] Leave root/layer installation, Overlay, positioning, swallowed pointer release, captured pane/tab and prior focus in WindowCommandPalette. Its `dismissed` callback performs the original focus restoration and clears pane/tab references. Controller clears target/context closures on dismiss/close. Preserve palette key routing and IME composition behavior.
- [x] Run full app tests; verify PaletteController and PaletteTarget reference no workspace/history/snippets/application classes. Commit `refactor: isolate palette interaction state` with trailer.

## Task 6: Own session launch admission and bounded shutdown

**Files:** Create `A/{SessionLaunchCoordinator,ApplicationShutdown}.java`; modify `A/JasperApplication.java`, `A/TerminalPane.java`; retain `A/ShellLauncher.java` launch capture behavior. Create `AT/{SessionLaunchCoordinatorTest,ApplicationShutdownTest}.java`; update `AT/{ShellLauncherTest,ApplicationActionsTest,JasperApplicationResidencyTest}.java`.

**Interfaces:** `SessionLaunchCoordinator(ExecutorService)`, `TerminalSession track(TerminalSession)`, `List<CompletableFuture<?>> pendingExits()`, `void close()`; `ApplicationShutdown(Runnable terminate)`, `void await(List<CompletableFuture<?>> pending)`. Both concrete owners are application-internal. `ShellLauncher` continues to receive an Executor, settings Supplier and `(Path,LaunchSettings)->TerminalSession` factory; it never depends on application.

- [x] Add deterministic tests using existing fake connector/session fixtures: block factory return on a latch, close pane and coordinator, release factory, drain EDT, assert the returned session closes and no pane onReady fires. Also launch before close and assert tracked exit futures remain in the shutdown snapshot until completion. Do not use real shell processes for these cases.
- [x] Write a shutdown test with a completed future, an `AtomicInteger` terminate callback and latch; assert exactly one invocation even when `await` is called twice, and assert `SwingUtilities.isEventDispatchThread()` is false in terminate. A separate unfinished future test uses a package-private injected `Duration` constructor set to 10 ms and awaits termination with a bounded latch; production constructor uses exactly 2 seconds.
- [x] Run focused tests; missing owners are expected RED. Transplant the launch executor and session set from JasperApplication into SessionLaunchCoordinator. Synchronize admission/close/snapshot with a private lock. `track` immediately closes and returns a late session if stopped; otherwise records it and removes it on exit. Close stops admission before `executor.shutdown()`; already-admitted sessions remain pane-owned and tracked until exit (execution ruling preserves the existing shutdown test). Pane completion retains its existing closed check and closes late results before attachment.

```java
public TerminalSession track(TerminalSession session) {
    java.util.Objects.requireNonNull(session);
    synchronized (lock) {
        if (!closed) {
            sessions.add(session);
            session.exitFuture().whenComplete((code, failure) -> {
                synchronized (lock) { sessions.remove(session); }
            });
            return session;
        }
    }
    session.close();
    return session;
}
```

- [x] Extract the current allOf/orTimeout/exit-thread block into ApplicationShutdown. Keep the once gate on EDT and preserve the queued shutdown boundary so Quit can still be recorded by command history. Use this termination core:

```java
void await(java.util.List<java.util.concurrent.CompletableFuture<?>> pending) {
    if (started) return;
    started = true;
    java.util.concurrent.CompletableFuture.allOf(
        pending.toArray(java.util.concurrent.CompletableFuture[]::new))
        .orTimeout(grace.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
        .whenComplete((ignored, failure) -> Thread.ofPlatform().name("jasper-exit")
            .start(terminate));
}
```

Retain elapsed-time logging from existing shutdown around the completion block. JasperApplication closes feature owners, history/config and desktop handlers before passing history.closedFuture plus coordinator.pendingExits. Off-EDT waiting never blocks Swing. Remove redundant launch/session/shutdown fields from JasperApplication.
- [x] Run `./gradlew :jasper-app:test`; commit `refactor: make launch and shutdown ownership explicit` with trailer.

## Task 7: Extract startup composition with explicit rollback

**Files:** Create `A/bootstrap/StartupResources.java`, `A/ApplicationBootstrap.java`; modify `A/Main.java`; move bootstrap assertions from `AT/MainTest.java` into `AT/ApplicationBootstrapTest.java`; create `AT/bootstrap/StartupResourcesTest.java`.

**Interfaces:** `StartupResources.own(T extends AutoCloseable)->T`, `release(AutoCloseable)`, `transfer()`, `close()`, `rollback(Throwable)`; `ApplicationBootstrap.main(String[])`, existing `start(String[],PrintStream,PrintStream,BiConsumer<ConfigService,AppArguments>)->int`, role/handoff helpers unchanged except ownership. `Main.main(String[])` delegates only.

- [x] Write the rollback test before the owner:

```java
@Test void rollbackIsReverseOrderedOnceOnlyAndPreservesOriginalFailure() {
    var order = new java.util.ArrayList<String>();
    var resources = new StartupResources();
    resources.own((AutoCloseable) () -> order.add("first"));
    resources.own((AutoCloseable) () -> { order.add("second"); throw new IllegalStateException("cleanup"); });
    var original = new IllegalArgumentException("startup");
    resources.rollback(original); resources.close();
    assertThat(order).containsExactly("second", "first");
    assertThat(original.getSuppressed()).extracting(Throwable::getMessage).containsExactly("cleanup");
}
```

- [x] Run the focused test for RED. Add this complete rollback implementation, documenting caller thread ownership:

```java
public final class StartupResources implements AutoCloseable {
    private final java.util.ArrayDeque<AutoCloseable> resources = new java.util.ArrayDeque<>();
    private boolean finished;
    public <T extends AutoCloseable> T own(T resource) {
        if (finished) throw new IllegalStateException("Startup ownership is finished");
        resources.addFirst(java.util.Objects.requireNonNull(resource));
        return resource;
    }
    public void release(AutoCloseable resource) {
        if (finished) throw new IllegalStateException("Startup ownership is finished");
        if (!resources.removeIf(candidate -> candidate == resource))
            throw new IllegalArgumentException("Resource is not owned here");
    }
    public void transfer() { finished = true; resources.clear(); }
    public void rollback(Throwable failure) {
        java.util.Objects.requireNonNull(failure);
        if (finished) return;
        finished = true;
        while (!resources.isEmpty()) {
            try { resources.removeFirst().close(); }
            catch (Throwable cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
        }
    }
    @Override public void close() {
        var failure = new IllegalStateException("Startup resource cleanup failed");
        rollback(failure);
        if (failure.getSuppressed().length != 0) throw failure;
    }
}
```

- [x] Move the remaining Main implementation into ApplicationBootstrap, preserving start's early argument validation and AF_UNIX handoff before any Swing/toolkit/font operation. Rename `Main.class` synchronization to ApplicationBootstrap.class. The only Main body is:

```java
public final class Main {
    private Main() {}
    public static void main(String[] args) {
        dev.jasper.app.bootstrap.ApplicationBootstrap.main(args);
    }
}
```

Until Task 11, qualify ApplicationBootstrap in its current flat package instead. No temporary delegating business methods remain on Main.
- [x] Replace cleanup branches with explicit acquisition registration immediately after successful acquisition. AppLog/exception handler/hook have process-lifetime ownership after startup; history/config move to JasperApplication on successful construction; endpoint remains owned by a process-lifetime close callback and the shutdown hook. Register `removeShutdownHook` as a rollback action before transferring successful startup. Use separate startup scopes for pre-EDT setup and EDT composition; never share an unsynchronized mutable ledger across threads. Transfer only after the receiving owner is wired. Preserve asynchronous log cleanup and original startup diagnostics, standalone isolation, background bind failure exit and code-source stale refusal.
- [x] Extend startup tests to inject failures at history acquired, application construction, endpoint bound and first window creation boundaries using package-private JDK callback constructor seams on ApplicationBootstrap. Assert reverse cleanup, endpoint can bind again, restored default exception handler, zero created windows for handoff/background failure, and exactly one termination. Keep start tests that inspect configuration and nonzero exit codes. Run full app tests; commit `refactor: isolate bootstrap and startup rollback` with trailer.

## Task 8: Establish Buddy values and remove application dependencies

**Files:** Create `jasper-buddy/build.gradle.kts`, `B/config/{BuddyOptions,BuddyPosition}.java`, `B/notice/BuddyNoticeId.java`; move `A/BuddyNotice.java` to `B/notice/BuddyNotice.java`. Modify `settings.gradle.kts`, `jasper-app/build.gradle.kts`, and all existing Buddy consumers. Create `BT/config/BuddyOptionsTest.java`, `BT/notice/BuddyNoticeIdTest.java`; move `AT/BuddyNoticeTest.java` to `BT/notice/BuddyNoticeTest.java`.

**Interfaces:** Values are defined below. All BuddyOptions accessors use record-style names. `BuddyOptions.builder(Font)` requires the resolved font; `toBuilder()` preserves every field. `BuddyNotice` becomes `record BuddyNotice(BuddyNoticeId id,Kind kind,String title,State state,Supplier<String> detail,Runnable activate)` with the existing nested enums and predicates.

**Planning refinement:** BuddyCard currently calls `FlatLaf.isLafDark()`. Add explicit `boolean dark` to BuddyOptions (default true, matching Jasper's default); the application supplies its resolved chrome mode on every theme change. This closes an actual vendor dependency without copying FlatLaf detection into the library. It is a value-only extension of the approved appearance contract.

- [x] Add value tests before implementing the API:

```java
@Test void optionsCopyPreservesAllFields() {
    var font = new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13);
    var positions = new java.util.ArrayList<BuddyPosition>();
    Runnable activate = () -> {};
    Runnable toggle = () -> {};
    var original = BuddyOptions.builder(font).dark(false)
        .initialPosition(new BuddyPosition(-100, 42)).positionChanged(positions::add)
        .activateHost(activate).toggleRequested(toggle).build();
    var changed = original.toBuilder().primaryFont(font.deriveFont(15f)).build();
    assertThat(changed.dark()).isFalse();
    assertThat(changed.initialPosition()).contains(new BuddyPosition(-100, 42));
    assertThat(changed.activateHost()).isSameAs(activate);
    assertThat(changed.toggleRequested()).isSameAs(toggle);
    changed.positionChanged().accept(new BuddyPosition(1, 2));
    assertThat(positions).containsExactly(new BuddyPosition(1, 2));
}
@Test void sourceQualifiesNoticeIdentity() {
    Object key = new Object();
    assertThat(new BuddyNoticeId("terminal", key)).isEqualTo(new BuddyNoticeId("terminal", key));
    assertThat(new BuddyNoticeId("transfer", key)).isNotEqualTo(new BuddyNoticeId("terminal", key));
    assertThatThrownBy(() -> new BuddyNoticeId(" ", key)).isInstanceOf(IllegalArgumentException.class);
}
```

- [x] Include `jasper-buddy` in settings, with an empty dependency block and root-inherited Java/test conventions. Add `implementation(project(":jasper-buddy"))` to the app. Run the focused Buddy tests for RED, then add these complete values:

```java
public record BuddyPosition(int x, int y) {}
public record BuddyNoticeId(String source, Object key) {
    public BuddyNoticeId {
        java.util.Objects.requireNonNull(source, "source");
        java.util.Objects.requireNonNull(key, "key");
        if (source.isBlank()) throw new IllegalArgumentException("A notice source must not be blank");
    }
}
```

```java
public final class BuddyOptions {
    private final java.awt.Font primaryFont;
    private final boolean dark;
    private final BuddyPosition initialPosition;
    private final java.util.function.Consumer<BuddyPosition> positionChanged;
    private final Runnable activateHost, toggleRequested;
    private BuddyOptions(Builder b) {
        primaryFont = java.util.Objects.requireNonNull(b.primaryFont, "primaryFont");
        dark = b.dark; initialPosition = b.initialPosition;
        positionChanged = java.util.Objects.requireNonNull(b.positionChanged, "positionChanged");
        activateHost = java.util.Objects.requireNonNull(b.activateHost, "activateHost");
        toggleRequested = java.util.Objects.requireNonNull(b.toggleRequested, "toggleRequested");
    }
    public static Builder builder(java.awt.Font font) { return new Builder(font); }
    public Builder toBuilder() {
        return new Builder(primaryFont).dark(dark).initialPosition(initialPosition)
            .positionChanged(positionChanged).activateHost(activateHost).toggleRequested(toggleRequested);
    }
    public java.awt.Font primaryFont() { return primaryFont; }
    public boolean dark() { return dark; }
    public java.util.Optional<BuddyPosition> initialPosition() { return java.util.Optional.ofNullable(initialPosition); }
    public java.util.function.Consumer<BuddyPosition> positionChanged() { return positionChanged; }
    public Runnable activateHost() { return activateHost; }
    public Runnable toggleRequested() { return toggleRequested; }
    public static final class Builder {
        private java.awt.Font primaryFont;
        private boolean dark = true;
        private BuddyPosition initialPosition;
        private java.util.function.Consumer<BuddyPosition> positionChanged = position -> {};
        private Runnable activateHost = () -> {}, toggleRequested = () -> {};
        private Builder(java.awt.Font font) { primaryFont = java.util.Objects.requireNonNull(font, "primaryFont"); }
        public Builder primaryFont(java.awt.Font value) { primaryFont = java.util.Objects.requireNonNull(value); return this; }
        public Builder dark(boolean value) { dark = value; return this; }
        public Builder initialPosition(BuddyPosition value) { initialPosition = value; return this; }
        public Builder positionChanged(java.util.function.Consumer<BuddyPosition> value) {
            positionChanged = java.util.Objects.requireNonNull(value); return this;
        }
        public Builder activateHost(Runnable value) { activateHost = java.util.Objects.requireNonNull(value); return this; }
        public Builder toggleRequested(Runnable value) { toggleRequested = java.util.Objects.requireNonNull(value); return this; }
        public BuddyOptions build() { return new BuddyOptions(this); }
    }
}
```

- [x] Change BuddyNotice's identity components to `BuddyNoticeId id`, validate nonnull ID and preserve existing kind/state/title/detail checks. Keep `live`, `wantsAttention`, `orphaned` predicates; use `id.equals(otherId)` for identity. Replace all constructions and BuddyDeck's private Id record with the supported ID. This checkpoint may still have the model/presentation in the app; none is supported Buddy API.
- [x] Run `./gradlew check`; commit `refactor: define Buddy configuration and notice values` with trailer.

## Task 9: Move Buddy model/presentation behind its lazy facade

**Files:** Move all 20 remaining Buddy implementation classes to the exact B/internal destinations in the manifest; create `B/view/BuddyCompanion.java`. Move their owner tests and Buddy-only preview/measurement fixtures into corresponding BT packages. Move `jasper-app/src/main/resources/dev/jasper/app/buddy/jasper-buddy.png` to `jasper-buddy/src/main/resources/dev/jasper/buddy/internal/presentation/jasper-buddy.png`. Modify all presentation constructors/callers and `jasper-app/build.gradle.kts` preview tasks. Create `BT/view/BuddyCompanionTest.java`.

**Interfaces:** `BuddyWindow.create(BuddyOptions,BuddyDeck) -> BuddyWindow` (nullable when unavailable), `applyOptions(BuddyOptions)`, existing `show/hide/greet/poke/setWorking/refreshDeck/dispose`; no state file input. `BuddyCompanion` constructor and facade below are supported. BuddyDeck mutators take BuddyNoticeId; snapshot methods remain internal. Keep internal classes public only where Java package access requires it.

- [x] Test on EDT that construction/post/hide/clear/close are headless-safe; close twice; call every facade mutation after close and assert no native creation or callback invocation. Off-EDT construction and every operation must fail with IllegalStateException. Null constructor options must fail before acquiring resources. Place state assertions in internal model tests and a package-private facade constructor accepting a supplied BuddyDeck; it must not appear in public signatures.
- [x] Run Buddy tests for RED. Move sources/resources and transplant model behavior. Change sprite load to `BuddySprite.class.getResourceAsStream("jasper-buddy.png")`; missing/null streams follow the existing unavailable diagnostic. Tests use a JarFile/resource check later, not a source-tree fallback.
- [x] Remove BuddyWindow's Path/read/write dependencies. First realization reads `options.initialPosition()`, converts to Point, and passes through existing monitor clamping; drag-end converts Point to BuddyPosition and invokes the **current** options callback once. Later applyOptions must not read initialPosition again. Menu listeners invoke current options callbacks rather than capturing construction callbacks.
- [x] Thread current appearance through owned presentation instances; final BuddyDeckWindow/ColumnWindow/Bubble constructors take BuddyOptions with their existing model/callback inputs and expose applyOptions(BuddyOptions). Replace `SystemFonts.system(style,size)` with `options.primaryFont().deriveFont(style,size)` and `FlatLaf.isLafDark()` with `options.dark()`. BuddyCard becomes an instance owned by each panel or accepts explicit font/dark parameters on drawing/measurement methods; retain its static geometry/material caches keyed by dark mode. Bubble/menu fonts use the same resolved font. `applyOptions` updates drawer/column/bubble children, invalidates font-dependent sizes, revalidates and repaints. No global static host font/callback state.
- [x] Implement the facade using this complete ownership core (imports refer only to supported values and internal owners):

```java
public final class BuddyCompanion implements AutoCloseable {
    private final BuddyDeck deck;
    private BuddyOptions options;
    private BuddyWindow window;
    private boolean closed, working;
    public BuddyCompanion(BuddyOptions options) { this(options, new BuddyDeck()); }
    BuddyCompanion(BuddyOptions options, BuddyDeck deck) {
        requireEdt();
        this.options = java.util.Objects.requireNonNull(options, "options");
        this.deck = java.util.Objects.requireNonNull(deck, "deck");
    }
    private static void requireEdt() {
        if (!javax.swing.SwingUtilities.isEventDispatchThread())
            throw new IllegalStateException("Buddy operations require the EDT");
    }
    private void refresh() { if (window != null) window.refreshDeck(); }
    public void post(BuddyNotice notice) {
        requireEdt(); if (closed) return; deck.post(java.util.Objects.requireNonNull(notice)); refresh();
    }
    public void updateTitle(BuddyNoticeId id, String title) {
        requireEdt(); if (closed) return; deck.updateTitle(id, title); refresh();
    }
    public void acknowledge(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.acknowledge(id); refresh();
    }
    public void dismiss(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.dismiss(id); refresh();
    }
    public void orphan(BuddyNoticeId id) {
        requireEdt(); if (closed) return; deck.orphan(id); refresh();
    }
    public void orphan(BuddyNoticeId id, String finalDetail) {
        requireEdt(); if (closed) return; deck.orphan(id, finalDetail); refresh();
    }
    public void clear() { requireEdt(); if (closed) return; deck.clear(); refresh(); }
    public void setWorking(boolean value) {
        requireEdt(); if (closed) return; working = value;
        if (window != null) window.setWorking(value);
    }
    public boolean show() {
        requireEdt(); if (closed) return false;
        if (window == null) window = BuddyWindow.create(options, deck);
        if (window == null) return false;
        window.setWorking(working); window.show(); return true;
    }
    public void hide() { requireEdt(); if (!closed && window != null) window.hide(); }
    public void greet() { requireEdt(); if (!closed && window != null) window.greet(); }
    public void poke() { requireEdt(); if (!closed && window != null) window.poke(); }
    public void applyOptions(BuddyOptions value) {
        requireEdt(); if (closed) return; options = java.util.Objects.requireNonNull(value);
        if (window != null) window.applyOptions(options);
    }
    @Override public void close() {
        requireEdt(); if (closed) return; closed = true;
        try { if (window != null) window.dispose(); }
        finally { window = null; options = null; deck.clear(); }
    }
}
```

BuddyDeck construction is pure/no resources; null validation precedes all presentation acquisition. Add nonnull-ID checks and nonblank title validation in the model's public-to-internal mutators. Preserve no-op results for unknown IDs. All supported mutations return void; only show returns boolean.
- [x] Keep the existing timer/animation pure tests and expand hide/dispose assertions in internal presentation tests: stopped timer, hidden dependent windows, removed UIManager listener, no outstanding drag frame, cleared host callbacks after disposal. Native creation is checked only by existing headless seams; user runs actual window acceptance later.
- [x] Migrate both JasperApplication and CommandNotifier from BuddyDeck/BuddyWindow to BuddyCompanion now so the entire app compiles without importing the moved model or presentation. JasperApplication temporarily retains its integration policy until Task 10; replace create/attachDeck/refreshDeck/dispose calls with constructor/show/model facade operations/close and convert state-file/font inputs into BuddyOptions. Remove onDeckChanged callback and redundant refresh calls; companion owns refresh. Port notifier tests to controlled facade/model tests **without** exposing public model inspection or importing Buddy internals into app production. Test producer emissions through package-private Consumer callbacks if needed; production binds them to facade methods.
- [x] Run `./gradlew check`; commit `refactor: extract Buddy library and facade` with trailer.

## Task 10: Centralize app Buddy policy and close producer lifetimes

**Files:** Create `A/BuddyIntegration.java`; modify `A/{JasperApplication,CommandNotifier,BuddyVisibility,BuddyStateFile}.java`. Keep BuddyVisibility in notifications and BuddyStateFile in persistence. Update `AT/{CommandNotifierTest,BuddyVisibilityTest,BuddyStateFileTest,JasperApplicationResidencyTest}.java`; create `AT/BuddyIntegrationTest.java`.

**Interfaces:** `BuddyIntegration(Path stateFile,Font font,boolean dark,Runnable activateHost,Runnable toggleRequested)`; `BuddyCompanion companion()`, `void configured(boolean)`, `void toggle()`, `boolean enabled()`, `void window(Object,boolean showing,boolean iconified)`, `void removeWindow(Object)`, `void appearance(Font,boolean)`, `void activity()`, `void greet()`, `void close()`. Application composes CommandNotifier with companion and OS notification callback. CommandNotifier adds `opened(Object key)` and `close()`; all producer events reject inactive keys.

- [x] Add notifier tests that call `opened`, start a long command, save its scheduled Runnable, close the producer, then execute the saved Runnable and deliver a finished event. Assert no new post, no working state and no OS notification. Repeat for close before start, close after completion and notifier-wide shutdown. A test must invoke saved work after close, not only verify the cancellation callback was called.
- [x] Add BuddyIntegration tests using a package-private constructor that accepts `BuddyCompanion`, `BuddyVisibility`, `Consumer<BuddyPosition>` and `BooleanSupplier show` for headless control. Assert unavailable show called once despite repeated state events; hiding preserves the companion model; position writes occur only on drag-end callback; appearance changes retain initial position without applying it again; close removes the global key listener once.
- [x] Run focused tests for RED. Move JasperApplication's Buddy fields, lazy availability, syncBuddy, toggle, greet, key watch and poke throttling into BuddyIntegration. Keep exactly one companion model from construction even before first show. Resolve the system font in the app and pass it with resolved dark mode. Read BuddyStateFile before first show; retain its strict format and synchronous drag-end write behavior. Native exception handling still disables the presentation and logs the existing message.
- [x] Reject events after producer closure without retaining an unbounded tombstone set:

```java
private final java.util.Set<Object> activeProducers = new java.util.HashSet<>();
private boolean disposed;
void opened(Object key) {
    if (!disposed) activeProducers.add(java.util.Objects.requireNonNull(key));
}
private boolean accepts(Object key) { return !disposed && activeProducers.contains(key); }
```

Translate workspace OPENED into `opened` before any started event; registration replays existing panes and later panes emit their own OPENED. Put `if (!accepts(key)) return;` at the beginning of started/finished/titleChanged/looked/hidden paths and inside delayed callbacks. In closed, remove the key **before** cancellation/orphaning; retain the existing final duration/outcome rules. In notifier close, mark disposed, cancel every in-flight timer, clear active producers and replace/release retained callback closures. App closes notifier before companion. No post-shutdown callback can reactivate the companion.
- [x] Replace notification use of `TerminalTitle.singleLine` with its existing two-replacement pure body inside CommandNotifier; do not create a common module. Application translates workspace events and registers producer identities; workspace never imports CommandNotice/Buddy.
- [x] Run `./gradlew check`; commit `refactor: isolate Buddy application integration` with trailer.

## Task 11: Apply final packages and enforce architecture from bytecode

**Files:** Move every existing app production type to the manifest destination, new owners to their listed paths, and tests to owner/testsupport packages. Modify `build.gradle.kts`, `jasper-app/build.gradle.kts`, `jasper-buddy/build.gradle.kts`, `gradle/packaging.gradle` if it names relocated benchmarks/previews; create `gradle/application-architecture.gradle.kts`. Create `AT/architecture/PackagedResourcesTest.java`, `BT/architecture/BuddyApiTest.java`. Update `AGENTS.md` module map and link to this approved amendment.

**Interfaces:** Gradle task `verifyApplicationArchitecture`, wired into app and Buddy check; supported Buddy allowlist exactly five top-level types plus their nested types. Root launcher remains `dev.jasper.app.Main`; benchmark entry points become `dev.jasper.app.benchmark.Bench` and `.MemoryBench`.

- [x] Add architecture checks before moves. Expand the existing root terminal checker pattern into the new script: use the JBR toolchain's jdeps/javap, compiled production class directories and complete runtime classpaths. Record class edges with `-verbose:class -filter:none --multi-release 25`. These predicates define the required checks:

```kotlin
val buddySupported = setOf(
    "dev.jasper.buddy.view.BuddyCompanion",
    "dev.jasper.buddy.notice.BuddyNotice",
    "dev.jasper.buddy.notice.BuddyNoticeId",
    "dev.jasper.buddy.config.BuddyOptions",
    "dev.jasper.buddy.config.BuddyPosition"
)
fun topLevel(name: String) = name.substringBefore('$')
fun packageOf(name: String) = name.substringBeforeLast('.')
fun assertAcyclic(graph: Map<String, Set<String>>) {
    val active = mutableSetOf<String>()
    val done = mutableSetOf<String>()
    fun visit(node: String) {
        if (node in done) return
        check(active.add(node)) { "Package cycle at $node through $active" }
        graph[node].orEmpty().forEach { visit(it) }
        active.remove(node); done.add(node)
    }
    graph.keys.forEach { visit(it) }
}
```

For each app→Buddy edge require `topLevel(target) in buddySupported`. For Buddy edges forbid app, terminal and third-party prefixes; allow Java/JDK dependencies. Check the full app package DAG and Buddy package DAG, excluding same-package edges only. Ban root app production types except Main. Run `javap -public -s` on all supported Buddy types and nested types, verify all referenced types are JDK or allowlisted Buddy, then `javap -c -p` on app to catch fully qualified/internalAccess bytecode references. Preserve existing terminal rules. No cycle allowlist or ignored violation list.

- [x] Run `./gradlew verifyApplicationArchitecture`; expect flat-root and vendor/Buddy boundary failures before the migration. Do not relax the rule to pass the old layout.
- [x] Apply the manifest moves, package declarations and explicit imports. Promote only cross-package contracts and enum/record constructors actually consumed elsewhere. Keep WorkspaceActions/WorkspaceConfiguration and presentation-private helpers package-private. Move tests that assert internals alongside their owner; cross-feature tests use supported collaboration methods, not reflective field access.

```python
# For each reviewed manifest entry, move one source while preserving its body.
from pathlib import Path

def move_java(source, target, package):
    source, target = Path(source), Path(target)
    text = source.read_text()
    old = text.splitlines()[0]
    assert old.startswith('package ') and old.endswith(';'), source
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(text.replace(old, 'package ' + package + ';', 1))
    source.unlink()
```

Use this helper against the exhaustive class manifest, not a filename-prefix guess. Review imports after each package group. The atomic task may temporarily fail compilation while moves are in progress; its commit must be fully runnable.
- [x] Update preview task mainClass names to each test owner's actual package. Buddy-only previews execute against Buddy's test runtime; app integration previews remain app-owned and use supported facade/value contracts. Their optional execution remains outside check. Keep FlatLaf defaults at `dev/jasper/app/themes` and shell integration resource paths unchanged unless a test proves all callers migrated. Benchmark launchers use the new benchmark package. jpackage continues using Main and the application runtime classpath, now including Buddy.
- [x] Test resources from actual jars, not `src/main/resources`. Make resource tests depend on both `jar` tasks and pass their paths via Gradle system properties. In the test open `JarFile` and assert exact entries:

```java
try (var jar = new java.util.jar.JarFile(System.getProperty("jasper.buddyJar"))) {
    var entry = jar.getJarEntry("dev/jasper/buddy/internal/presentation/jasper-buddy.png");
    assertThat(entry).isNotNull();
    try (var stream = jar.getInputStream(entry)) {
        assertThat(javax.imageio.ImageIO.read(stream)).isNotNull();
    }
}
```

Also assert the app jar contains every FlatLaf and shell-integration entry recorded in Task 1, excludes old Buddy sprite/classes and all test fixtures, and both library jars lack app classes. Check installDist contains both library jars. Do not run the produced desktop launcher.
- [x] Run `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist`; commit `refactor: organize app packages and enforce boundaries` with trailer.

## Task 12: Deliver onboarding, maintenance recipes and final verification

**Files:** Create `jasper-app/README.md`, `jasper-buddy/README.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`, all missing production `package-info.java`; update root `README.md`, `AGENTS.md`, `docs/STATUS.md`, this plan and `docs/app-refactor-verification.md`. Create `AT/documentation/AppExamplesTest.java`, `BT/documentation/BuddyExamplesTest.java`, and link/documentation validation beside existing terminal documentation checks.

**Interfaces:** New contributors start at module READMEs. Examples compile against the same production signatures as consumers. Buddy Javadoc includes only supported packages; internal docs remain in source. App Javadoc/package docs describe collaboration, not a promised external API.

- [x] Write executable Buddy embedding example as a test (run its body through `SwingUtilities.invokeAndWait`):

```java
var options = BuddyOptions.builder(new java.awt.Font("Dialog", java.awt.Font.PLAIN, 13))
    .dark(true).activateHost(() -> {}).toggleRequested(() -> {}).build();
try (var buddy = new BuddyCompanion(options)) {
    var id = new BuddyNoticeId("example", new Object());
    buddy.post(new BuddyNotice(id, BuddyNotice.Kind.TASK, "Build", BuddyNotice.State.DONE,
        () -> "Finished", () -> {}));
    buddy.acknowledge(id);
    buddy.hide();
    buddy.applyOptions(options.toBuilder().dark(false).build());
}
```

Explain that real applications call show on EDT; this headless test intentionally exercises construction/model/lifecycle only. An actual host font, callbacks, visibility policy and persistence belong to the embedding application.
- [x] Write application onboarding covering clone/toolchain, `./gradlew check`, entry point, launch flow, resource ownership, where test fixtures live, no-GUI agent rule, expected environment skips, package graph and review workflow. Include a startup/shutdown sequence diagram naming Bootstrap, StartupResources, JasperApplication, SessionLaunchCoordinator, Workspace and BuddyIntegration.
- [x] Write maintenance recipes with a file map, minimal compiling example and exact test command for each: new workspace command/shortcut; new palette scope; new saved/live/session-only setting; history/snippet provider behavior; new Buddy notice producer; platform integration; session-lifecycle change. The setting recipe explicitly lists canonical snapshot validation, builder/toBuilder, config loader/template/example, workspace apply comparison, launch capture and docs/tests. The producer recipe explicitly pairs opened/closed and cancellation, opaque IDs, nonblocking suppliers, orphaning and shutdown.
- [x] Explain the selected patterns: command registry for real actions; immutable builders for preservation; concrete coordinator for lifecycle; facade around Buddy; callbacks for host requests. State that future plugins may target these boundaries but no plugin ABI/loading/persistence permission model is promised by this refactor.
- [x] Add link validation of relative Markdown references against actual files, and compile copied recipe examples in test sources. Wire Buddy Javadoc doclint and architecture checks into check. Document every production package's allowed outgoing dependencies, thread confinement and cleanup owner. Resolve broken Javadoc links caused by package moves; do not disable doclint to hide them.
- [x] Run the final headless verification and record actual results:

```bash
./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist --rerun-tasks
```

Use Task 1's XML counting script extended with `jasper-buddy`. Run the AGENTS.md source-character hygiene scan across all three production source roots. Review `git diff --check` and resource contents. Retain manual desktop/Windows/performance gaps explicitly; headless success is not visual/native acceptance.
- [x] Perform a fresh-reader exercise: starting only with the app README, locate the owner and test for adding a command, changing a live font default and posting/orphaning a Buddy notice. Record any navigation gaps and fix the docs. Request one independent whole-branch review under the preserved native workflow; fix material findings and rerun affected checks. Do not merge/push automatically: the earlier main merge authorization covered the terminal refactor.
- [x] Commit `docs: onboard app and Buddy contributors` with trailer. Present runnable results, verified counts, final review findings and manual acceptance instructions to the user.

## Completion criteria and execution record

The deliverable is complete only when every manifest class and test has one owner, no legacy flat production classes remain except Main, all dependency checks pass without exemptions, Buddy can be consumed without app/terminal/vendor classes, existing behavior tests and targeted lifetime tests pass, and onboarding/recipes compile and resolve links. A package move alone does not satisfy the owner extractions.

Keep one commit per task; preserve all user changes outside scope. A task that needs a signature change must update dependent steps, this plan's interface blocks and the spec when contractual behavior changes before proceeding. Record any additional baseline tests or platform findings in the verification ledger.

| Task | Status | Validation / deviations |
| --- | --- | --- |
| 1–11 | Complete | Runnable task commits; exact checks and rulings in verification report |
| 12 | Complete | Guides/examples/doclint, independent review and fresh-reader exercise pass; two shutdown fixes verified RED→GREEN; final 1,127-test run passes |

## Planning self-review

- Spec coverage: package manifest, new owners, Buddy facade/lifecycle, policy/persistence separation, build/resource migration, tests, docs and future SDK limits each have an owning task.
- Dependency refinement: MacTitleBar receives Swing components and callbacks; `Main.windowTitle` moves to workspace. BuddyOptions carries explicit dark mode because the current painter calls FlatLaf. Neither requires a new shared module.
- Signature consistency: settings defaults live in config, Subscription in lifecycle; activity IDs are opaque; Buddy ID mutators return void; only show returns boolean; supported allowlist has five top-level types.
- Review Focus: startup Task 7; late launches Tasks 6–7; palette generations Task 5; saved/live settings Tasks 2/4/6; Buddy lifetime and stale producers Tasks 8–10.
- Implementation follows the recorded task checkpoints; the verification report records corrected transfer details and rulings. Final review completed; endpoint cleanup moved off EDT and accepted launch/late-child cleanup joined the bounded shutdown wait. Detailed findings and verification are in the report.
