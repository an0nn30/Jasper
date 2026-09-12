# Plan 4a — saved settings and live reload

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
- [ ] Write failing real-input tests for all platform path fallbacks/override normalization, help/unknown/missing/duplicate args; no directories created. Cases include relative XDG path and missing APPDATA.
```java
assertThat(AppDirs.resolve("Mac OS X", Map.of(), Path.of("/home/test")).configFile())
    .isEqualTo(Path.of("/home/test/.config/moray/config.toml"));
```
- [ ] Write parser RED tests for all supported fields, quoted keys and keybinding brace/none swaps; precise syntax/type/unknown/value source lines including empty unknown tables and wrong-type known tables. Unknown action warns and ignores; collisions/invalidbinding map defaults witherror. Duplicatekey/malformedsyntax reject; invalidvalues fall back perkey, validothersapply.
```java
var result = ConfigLoader.parse(Path.of("config.toml"), "[window]\ntab_height = 44\n[font]\nsize = 900", true);
assertThat(result.rejected()).isFalse();
assertThat(result.snapshot().tabHeight()).isEqualTo(44);
assertThat(result.snapshot().fontSize()).isEqualTo(16f);
assertThat(result.diagnostics().getFirst().line()).isEqualTo(4);
```
- [ ] Add TomlJ and implement typed validation using Toml.parse(text), inputPositionOf(List<String>), table key-path APIs and explicit expected types. Known container tables checked before leaves; dynamic keybindings entries handled separately. Preserve quoted dotted unknown keys in diagnostics. Error messages describe required type/range/action without echoing values. Invalidbinding wholemapfallback is distinct from fatal type rejection. Parse only; no I/O or Swing setup. Default bindings use actual existingplatformengine.
- [ ] Run focused tests RED/GREEN, app check and hygiene; commit Task1 files with coauthor and write report to plan scratch dir. Root owns STATUS/spec/plan.

### Task 2: Safe file lifecycle, generated template and background reload
**Files:** Create ConfigService.java, ConfigTemplate.java and ConfigServiceTest/ConfigTemplateTest; touch KeyBindings helper only if needed to share effective default string formatting. No WindowContent/GUI integration yet.
**Consumes:** Task1 contracts. **Produces:**
```java
final class ConfigService implements AutoCloseable {
    record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present) {}
    ConfigService(Path file, boolean macOs); // initial read off EDT, owns one background scheduled worker
    State initialState();
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
A package-private constructor may accept ScheduledExecutorService and Consumer<Runnable> publisher for deterministic lifecycle tests; do not add a custom interface. Service owns supplied worker and shuts it down onclose. State defensively copies diagnostics. Default constructor/initial read called only offEDT.
- [ ] Write filesystem RED tests with @TempDir: initial missing defaults/no creation, valid load, invalid file retainslastgood, recovery, delete defaults, same-mtime forced reload, unchanged poll no duplicatepublication, unreadable/oversized (>1MiB) rejection. Test callbackdelivery on EDT, openeroffEDT and close drops alreadyqueuedpublication. Use futures/latches/manualpublisher, no sleeps/timing guesses.
```java
Files.writeString(file, "[window]\ntab_height=44");
try (var service = new ConfigService(file, true)) {
    assertThat(service.initialState().snapshot().tabHeight()).isEqualTo(44);
    Files.writeString(file, "[window");
    assertThat(service.reload().get(5, TimeUnit.SECONDS).snapshot().tabHeight()).isEqualTo(44);
}
```
- [ ] Template RED tests: generatedtemplate parses clean on bothplatforms, comments document every supportedkey/default/livebehavior andeveryactionID, uncommented individual defaultkeybindings roundtrip actualeffectiveplatformdefaults (including nonMacAltcompatibility). Existingfile bytes survive repeated/concurrentensureExists; parentdirectoriescreatedonlywhenexplicitlyrequested; reader errors reported ratherthanoverwriting. Coveractualfunctions, notsource-text mirroring.
- [ ] Implement bounded UTF8 read via readNBytes(1MiB+1), immutableState and last-goodretention. Poll every1second on one scheduledworker; fingerprintmtime+size, forceReloadbypass; missingfiledefaults; parse/I/Oerrorspublishdiagnostics; changedstateonlypublication. Every queued EDT callbackchecksclosed. Initialstate available beforeUI; startsinglelistener once. No service-ownedUI or applicationlaunch. Requestsafterclose fail/ignore coherently withoutqueuedleaks.
- [ ] Implement documented template and CREATE_NEW only, catchFileAlreadyExists withoutrewriting. openSettings executesensureExists then injectedopener offEDT and reloadscreatedtemplate; exception completesfuture exceptionally soapplicationcanreport. Neveropen actualdesktop in tests. Runfocusedtests/appcheck/hygiene, commit/report.

### Task 3: Live application settings, actions and status diagnostics
**Files:** Create ConfigurationController.java, ConfigEditor.java and integrationtests. Modify Main.java, MorayApplication.java, TerminalWindow.java, WindowContent.java, WindowChrome.java, WindowStatusBar.java and themeproperties; README/docs/configuration.md. TerminalPane only if required for pending-configuredfont handling, no terminalmodulechanges.
**Consumes:** Task1/2 contracts. **Produces:** Actual runnablefile-backed app. Suggested coordinatorboundary:
```java
final class ConfigurationController implements AutoCloseable {
    ConfigurationController(ThemeController themes, ConfigService service); // EDT
    void register(WindowContent owner);
    void unregister(WindowContent owner);
    void accept(ConfigService.State state); // EDT
    public void close();
}
```
Controllerselectsinitialtheme once and changedconfiguredtheme thereafter, not on eachnewownerregistration. It ownsservice lifecycle. Existingownerconstructors staycompatible for fixtures; configactionsenabled onlywhenconnected. New WindowContent.bindings becomesreplaceable; store lastconfiguredsnapshot/fontresetdefault, callbacksforSettings/Reload/diagnostics/unregister. No duplicate shortcut engine.
- [ ] Write failing actualWindowContent/JRootPane tests withtemporaryConfigService: initialsnapshot, liveheight/toolbar/status/font/theme acrossmultipleowners/hiddenviews, no sessionreplacement, pendinglaunch inheritsnewconfiguredfont, fontresetusesconfiguredsize. Unrelatedfilefieldchangespreservemanualfont/height/theme/visibility; changedfieldreapplies; newownergetsconfigureddefaultswithoutresettingglobalmanualtheme. Useexistingcontrolledtestlaunchers, nocallingrealGUI.
- [ ] Write failing livebindingtests: oldrootstroke removed, newstroke dispatchesactualaction, none clearsaccelerator, nativefindfieldcopy/paste retainsediting, toolbarhint updated throughAction. Configinvalidsyntax updatesstatus whilelastgoodbehaviorretained; warnings/errorsclick opensselectableplaintextdetails withpath/line; rightlabel remainsboundedatnarrowwidth. Settings/Reload enableanddispatch; close unregistersandlatepublicationcannotmutateclosedowner.
```java
// After an actual file reload removing NEW_TAB:
assertThat(root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).get(oldStroke)).isNull();
assertThat(owner.action(ActionId.NEW_TAB).getValue(Action.ACCELERATOR_KEY)).isNull();
```
- [ ] Wire Main.parseargs offEDT, help/error exitswithoutUI, resolvepath+constructserviceinitialread beforeSwing invoke. ConstructMorayApplication withservice thenConfigurationController usingexistingThemeController. RegisterWindowContent beforeframebinding/show; new/pendingpanes configuredviaexistingonReady path. Closecontroller/service atapplicationshutdown; ownercloseunregisters. Preserveall existingconstructors/callers throughoverloads.
- [ ] Apply per-field differences only. Initialregister appliesheight/toolbar/status/font/bindings; futurefilefont changesapplyallviews andstoreddefault, newviews readlatestdefault; FONT_RESET usesconfigureddefault. setBindings removesoldrootmaps, clearsallactionaccelerators and reinstalls newset; handlers continue consultingcurrentengine. ThemeController ownsglobaltheme andlivepalette application. No shellrestart orterminalstateclearing.
- [ ] ConfigEditor defaultopen/edit thenreveal usesDesktop APIs on serviceworker; injectedConsumers forfailure/fallbacktests. No actualDesktop tests. Settings failures reportedthroughowner.onError onEDT. Status getsrealaccessibleconfigbutton (green/amber/red semanticcolors), label/pathtooltip andplainselectabledialog. Preserve existing shell/path/grid andbackground/minimumwidthcontracts; noHTML fromuntrustedstrings. Disablecallbacks afterclose.
- [ ] AddREADME/configurationguide withsupportedkeys, configpath/--config, settingscreation, reload/errorsemantics, current-windowoverridepolicy, examplesactualmac/nonMackeybindings andexplicitremainingPlan4scope. Manualchecksfornativeeditor/reload/shortcuts preserved rootownsSTATUS. Runcoveringtests, fullcheck/hygiene, commit/report.

## Root acceptance
- [ ] Independent taskreviews, finalwholebranchreview, fixesverified.
- [ ] Freshfullcheck, sourcehygiene/diffchecks; inspectactualheadlessstatusgeometry wherechanged.
- [ ] STATUS records mergedUI baseline, Plan4a delivery/remaining4b scope, allrulings andnativeacceptancepending.
