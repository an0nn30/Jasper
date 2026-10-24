# Enterprise presentation 15a: the look query Implementation Plan

> **Status (2026-10-19): executed on `claude/enterprise-look-query` (`2ad889b0b..23796aaa9`, then the Task 9 documentation commit).** Deviations from this plan's text: the execution rulings E1 to E15 below; in Task 9, the spec's 14.1 list ends its one remaining bullet with a full stop and 19.1's row 10 capitalises "The classic layout" at the start of its cell; 5.1's edit re-wraps only the edited sentence's two lines, keeping the paragraph's sentence-per-line breaks, and 9.1's single-reader sentence, already one long line, stays one line; Steps 3 and 5 write to a fixed scratchpad folder instead of `mktemp -d`; in Step 5 two agreements moved by 0.001, neither from a Task 2 Step 4 render: `enterprise-settings-13`, Settings › Editor showing Task 7's kit default "IBM Plex Mono" (0.723, now 0.722), and `composition-light-vault-manager`, whose status bar prints a fresh JUnit temporary path on every run (0.730, now 0.729); both are recorded in `docs/STATUS.md`; Task 2's `HostedPresentation.standard()` reads the default presentation (`Presentation.standard`), since only the listed classes may read `Kit.DEFAULT` (`StaticDefaultTest`). `./gradlew check --continue`: 11,567 tests, 0 failures, 0 errors, 25 skipped. The final review's fixes (`Plan 15a: final review fixes`) add two jasper-app tests, `PresentationReachTest.theApplicationHandsTheLooksPluginDefaultsToThePlugins` and `PluginSettingsPageTest.stagingThePluginsOwnDefaultUnderAKitDefaultSavesItAndResetReturnsToTheKits` (11,569 tests; only the touched classes were re-run), extend Task 5's unquoted-key message with `; quote a dotted setting key, such as "window.toolbar"`, drop the reader's `; configuration was not applied.` from a rejected look default's report, and correct four spec leftovers (decision 4, 5.9, 16.1 item 8 and 19.1 row 10).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** On 2026-10-19 the user asked: "Lets make sure that the changes we made were in the enterprise kit, and are exposed via plugin code and all plugins are up to date with these changes so that future plugins can also leverage look-specific settings when Enterprise is enabled." After the audit they approved: "Go ahead with plan 15 as proposed: SDK look query (and remove the Enterprise API), kit defaults for plugin settings, activation, classic icons everywhere, the search icon, versions, docs and the Sample plugin." This plan, the first of two, delivers the SDK look query (SDK 0.18.2), removes the Enterprise API, adds kit defaults for plugin settings, bumps the versions, updates the Sample plugin and documents all of it. Plan 15b (`2026-10-19-enterprise-15b-look-polish.md`) delivers activation, classic icons everywhere, the toolbar search icon and the identity guard.

**Architecture:**
- **SDK.** `dev.jasper.sdk.ui.Presentation` is a JDK-only record of facts about the look Jasper runs with: classic or not, the icon set, the tool windows, the title bar, where the menus are, the toolbar search, the UI and data faces, the body size and the toolbar height. `Appearance.presentation()` returns it. This is IntelliJ's `ExperimentalUI.isNewUI()` plus `JBUI.CurrentTheme`. The host builds it once per run in `dev.jasper.app.plugins.HostedPresentation`, from a new projection `PluginLook` (in `Projections`), the installed `Kit` and the look's `DefaultSource`. The testkit's `FakePluginHost.setPresentation` sets it, with `FakePresentations.STANDARD` and `CLASSIC` as ready values. A shared contract case runs on both hosts.
- **No Enterprise API.** Remote, File Server and Vault choose `Layout.CLASSIC` from `presentation.classic()`, so any classic look drives their compositions. `dev.jasper.enterprise.api.Workstation`, its export, the optional requirements, the compile-only dependencies, the `workstationPackages` gate rule and the three `FakeEnterprise` copies are removed. Enterprise keeps an entry point whose `start` does nothing.
- **Kit defaults for plugin settings.** A kit's `[defaults."<plugin id>"]` table holds raw TOML values keyed by that plugin's setting keys. `DefaultSource` carries them. When the plugin declares a setting, `DeclaredSettings` reads the kit's value with the setting's own kind, and uses it as the setting's default. A saved value still wins. A value the kind rejects, and a key the plugin never declares, are logged and ignored. Reset to Default returns to the kit's value. The testkit mirrors all of it. Enterprise's kit sets the Editor's `font_family` to its bundled IBM Plex Mono.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing, tomlj, Gradle (Kotlin DSL), JUnit 5 and AssertJ, `java.lang.classfile` for bytecode scans, Python 3 for `tools/visual` (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md).
- Read its header amendment lines and sections 1 (decisions 1, 4 and 19), 3 (principle 6), 4.1, 4.6, 4.7, 5.1, 5.8, 5.9, 8.4 to 8.6, 9.1, 9.2, 14.1 to 14.3, 15, 16.1, 17, 18 and 19.1.
- The [plugin SDK design](../specs/2026-09-21-jasper-plugin-sdk-design.md) section 5 ("Look and feel") is the original `Appearance` sketch; this plan adds one banner line to it.
- The user's request and approval of 2026-10-19, quoted in the Goal, are binding. They reverse spec 8.4's and 18's "an active-presentation query is not added".
- Audit (facts with file:line; `critic.md` wins where they disagree): `kit.md`, `surface.md`, `plugins.md` and `critic.md` under `/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/look-audit/`.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-15`, branch `claude/enterprise-look-query`, from `main` at `f78bb0385`. That commit holds Enterprise plans 1 to 14, the side stripes and the splash fix. Run every command from the worktree root. Line numbers below are from `f78bb0385`, re-verified against the worktree on 2026-10-19 (see "Execution notes" at the end, whose rulings bind where they refine a step). When an anchor has moved, re-find it; never edit around a missing anchor.

## Before you start (refresh)

Do not rebase, merge or push. Never run bare `git stash`, and never run `./gradlew --stop`.

```bash
git log --oneline -1                                                                         # f78bb0385 or a later plan-15a commit
grep -c 'public static final String VERSION = "0.18.1";' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java   # 1
grep -c 'public interface Workstation { }' plugins/enterprise/src/main/java/dev/jasper/enterprise/api/Workstation.java  # 1
grep -c 'workstationPackages' gradle/plugin-architecture.gradle.kts                          # 3
ls jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java 2>/dev/null | wc -l          # 0: Task 1 adds it
```

## Global Constraints

**Build and safety**
- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain. Always use `./gradlew`, never a system `gradle`, and never run `./gradlew --stop`: other sessions share the daemons.
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache. Headless JUnit renders and `:jasper-ui:gallery` may run.
- Work only in the worktree above, with one commit per task. Every commit message ends with a blank line, `🤖 Generated with Claude Code at The Home Depot`, a blank line, then `Co-Authored-By: Claude <noreply@anthropic.com>`. Do not push, merge or rebase. Never dispatch subagents from inside a task.

**The default look stays byte-identical.** Every file the headless gallery writes for Slate Cobalt Dark and Light (`dark-1x`, `light-1x`, 161 files each) must equal the Task 1 Step 0 baseline. So must jasper-app's 11 default-look renders (`ChromeRenderTest`'s default, complement and fallback chrome, palette and settings images, and `DefaultDockPixelsTest`'s `default-dark` and `default-light`). Task 9 proves both. Nothing in this plan paints; the risks are that a projection change reaches a strategy, or that a plugin composes differently under the default look. Both are tested.

**SDK and versions**
- `JasperSdk.VERSION` becomes `0.18.2`. The release is additive: one record and one `Appearance` method.
- Manifests that change in this plan move to `sdk = ">=0.18.2, <0.19"`. Remote, Vault, File Server and Sample read the query. Enterprise reads nothing, but its 0.3.0 kit uses a host feature that arrives with SDK 0.18.2: the `[defaults."<plugin id>"]` table here, and `[chrome] toolbar_search` in plan 15b. A 0.18.0 or 0.18.1 host would accept the plugin by its range and then refuse the whole kit as invalid (spec 5.9), and the range is a provider's only compatibility gate (Rulings 14).
- Versions: Enterprise `0.3.0`, Remote `0.15.0`, Vault `0.7.0`, File Server `0.6.0`, Sample `0.4.0`. Editor, History, Indexer and Snippets are unchanged.
- The testkit mirrors every host behaviour this plan adds. Each behaviour has a shared contract case that runs on both hosts (`FakeContractTest`, `AppContractTest`).
- MVP rule (memory "Jasper is an MVP: no compat shims"): the Enterprise API, its gate rule and its fakes are removed outright, with no deprecation, alias or migration note. One convenience is added on purpose: `DefaultSource(Map)` means "no plugin defaults", the value for every look but one (Rulings 15).

**Code rules**
- SDK signatures stay vendor-free and JDK-only (`verifySdkArchitecture`). App production code names SDK types only in `dev.jasper.app.plugins` (`verifyApplicationArchitecture`).
- Javadoc doclint runs on jasper-sdk, jasper-sdk-testkit and jasper-app (`-Xdoclint:all`; errors fail the build).
  - Every `{@link}` must resolve.
  - No comment may contain a raw `<`, `>`, `&` or `->`. Write `&lt;`, `&gt;` or words.
  - New public types and methods carry `@param`, `@return` and, in the SDK, `@since 0.18.2`.
- `docs/design/theme-engine/migration.csv`: `ThemeMigrationCoverageTest` scans `jasper-app/src`, `jasper-sdk/src`, `jasper-sdk-testkit/src` and every `plugins/*/src` (tests included).
  - A new or edited `.java` file that its `CANDIDATE` pattern matches needs a row in the same commit. The pattern matches an `import dev.jasper.sdk.ui`, the word `Appearance` or `appearance`, and more.
  - A row has exactly eight comma-free fields: `path,owner,Name,nonvisual-helper,none,none,<test path>,<disposition>`. The disposition begins `New in Enterprise plan 15a Task N;`.
  - A deleted file's row is deleted in the same commit, since the test requires every listed path to exist.
  - Run `./gradlew :jasper-app:test --tests '*ThemeMigrationCoverageTest'` in every task that adds, edits or deletes Java files.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in source.

**Tests and documents**
- Documentation edits are the smallest that make each document accurate (AGENTS.md "Documentation edits"). Delete removed content outright; never replace it with a negation.
- Earlier `docs/STATUS.md` entries and `docs/superpowers/` history are not rewritten, except the two "Not merged" lines Task 9 corrects (Rulings 24).
- `AppDocumentationTest` holds every `<!-- example:name -->` block in the guides equal to the code between `// example:name:start` and `:end` in `SamplePlugin.java` (and three test sources). A task that edits marked Sample code updates the guide's block in the same commit.

## Review Focus

1. **A kit default the declared kind rejects** (a string for an integer, an out-of-range number, an unknown choice token, an installed-font rule that refuses the family). The plugin must start, take its own default, and log one line naming the plugin, the key and the reason. The same holds for a key the plugin never declares.
   - Tests: Task 6 `DeclaredSettingsTest.aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported` and `aKitKeyThePluginNeverDeclaresIsReportedOnce`; contract `aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported`.
2. **A user's saved value equal to the plugin's own default, under a kit default that differs.** The saved value wins. In Settings, staging the plugin's own default under a kit default writes an explicit value, and Reset returns to the kit's.
   - Tests: Task 6 `PluginPanelPageTest.stagingThePluginsOwnDefaultUnderAKitDefaultSavesItAndResetReturnsToTheKits`; contract `aKitDefaultReplacesTheDeclaredDefaultAndASavedValueWins`.
3. **A look that fails to install at start** (the kit is blamed, spec 4.7). Plugins must read the fallback, the default look's chrome with the defaults read at start, so Remote, File Server and Vault compose their rows while the kit's plugin defaults still apply.
   - Test: Task 2 `HostedPresentationTest.theFallbackReportsTheDefaultLooksChromeWithTheDefaultsReadAtStart`.
4. **Enterprise installed but off, uninstalled, or a third-party look with a classic base.** No consumer may throw `NoClassDefFoundError`. The layout is ROWS without a classic look, and CLASSIC under any classic one.
   - Tests: Task 3 each plugin's `LayoutTest.aClassicPresentationIsClassicAndTheDefaultIsRows` and `RemotePluginTest.underTheDefaultLookSessionsStaysLeft`.
5. **`Appearance.presentation()` read off the UI thread** (Remote reads it on a background executor; a plugin may read it from a worker). It must be safe from any thread, the same value for every plugin, and unchanged across theme switches.
   - Test: contract `presentationIsTheHostsForEveryPluginOnAnyThreadAndThroughThemeChanges` (Task 1).

## Verified facts

At `f78bb0385`. Paths are under `jasper-app/src/main/java/dev/jasper/app/` unless they start with a module or `/`.

**SDK and testkit**
1. `JasperSdk.VERSION = "0.18.1"` (`jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`). It is pinned by `CapabilitiesTest.java:8`, `FilesValuesTest.java:54`, `PaletteValuesTest.java:107` and `KitBoundaryTest.java:5` (all under `jasper-sdk/src/test/java/dev/jasper/sdk/`), and by the rejection message `"needs SDK >=0.17.0, <0.18.0; this is SDK 0.18.1"` in `jasper-app/src/test/java/dev/jasper/app/plugins/KitCompatibilityTest.java:70`.
2. `Appearance` (`jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java:11-77`) gives colours, `variant()`, palettes, `workspaceOpacity` and change hooks. It has nothing about the presentation. It is implemented by `HostedUi.appearance()` (`plugins/HostedUi.java:362-404`), `FakePluginContext.appearance()` (`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginContext.java:239-271`) and one anonymous test double (`jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java:214`). `jasper-sdk/README.md:28-30` says the application and the testkit implement every interface but `Plugin`.
3. `dev.jasper.sdk.ui.DialogSpec.Presentation` is a nested enum (`DialogSpec.java:20`). No top-level `Presentation` exists in the SDK, the testkit or the plugins.
4. `Variant` reads "The application's current look" (`Variant.java:3`). `AppEvents.java:8` and `:16` say "The look changed". `PluginContext.java:120` promises "theme-aware icons". `Appearance` has had no icon method since SDK 0.15 (`docs/sdk-icons.md:41`).
5. `FakeKit.BUILT_INS` is a private set (`jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakeKit.java:21`). `new IconRegistry().names()` (`jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java:34`) is the host's set. The audit found the two equal (91 names), and no test pins it.
6. The shared contract harness is `ContractHarness` (`jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java`, `setSetting` at `:201`). The suite is `PluginContractTest` (same folder, 2,533 lines). The fake runs it in `jasper-sdk-testkit/src/test/java/dev/jasper/sdk/testing/FakeContractTest.java`; the app runs it in `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`, whose `setSetting` writes the settings file at `:184-196`.

**The host**
7. `PluginHost` holds the per-runtime values plugins see as mutable fields: `animations` (`plugins/PluginHost.java:98`), `kit` (`:100`), `shortcuts` and `clipboard`. `PluginRuntime` sets them when it makes the host (`plugins/PluginRuntime.java:683-688`). `PluginRuntime.presentation(Kit)` (`:389-393`) refuses after start ("Plugins have already started"). Its callers are `JasperApplication.java:989`, `PluginRuntimeTest.java:650,670` and `EnterpriseSettings.java:170` (test).
8. `HostedContext` makes the plugin's `HostedUi` (`plugins/HostedContext.java:85`) and its `HostedKit`, handing it `ui.appearance()` (`:91`). `appearance()` returns `ui.appearance()` (`:162`).
9. `PluginHost.start` makes each plugin's `DeclaredSettings` (`plugins/PluginHost.java:153`). `DeclaredSettings.declare` (`plugins/DeclaredSettings.java:59-73`) calls `SettingTranslation.translate(spec, this::guard)`, which builds the app `Setting` with the spec's own default (`plugins/SettingTranslation.java:29-53`). `complete()` (`:94-97`) closes declarations when `start` returns.
10. App `SettingKind.read(Object raw, T defaultValue)` (`settings/SettingKind.java:25`) reads tomlj values: `Long`, `Double`, `String`, `Boolean` and `TomlArray` for a string list (`:137`). `KitParser.read` uses it for the core defaults and strips the reader's "; using …" tail (`presentation/KitParser.java:494-497`).
11. Settings for a plugin:
    - A generated page compares with the app `Setting`'s default through the draft. Since the translated setting's default is the effective one, it needs no change.
    - A plugin's own page reaches `KitSettingRows.row`, which compares with the declaration's own default: `!sameValue(value, spec.defaultValue())` (`plugins/KitSettingRows.java:67`). `PluginPanelPage` makes the rows at `:54`, passing `settingSpec` (`:45`).
    - `PluginPanelPage.stage` drops a staged value equal to `app.defaultValue()` with nothing saved (`:185-188`), and `unset` stages `Optional.empty()` (`:192-199`).
    - The testkit's copies compare with `spec().defaultValue()`: `FakeSettingsPanelHost.java:60,67,198` and `KitSettingRows.java:51-52`. `FakeSetting` defaults from the spec (`FakeSetting.java:26,54,63`).

**Presentation and kit**
12. `Presentation` (`presentation/Presentation.java:22-23`) carries `kit`, `chrome`, `classicBase`, `faces`, `defaults` and `projections`. `fallback` keeps `defaults` (`:79-82`). Its javadoc says consumers receive "its kit or a projection, never this value" (`:10`).
13. `Projections` is an 11-component record (`presentation/Projections.java:24-26`). `of` is at `:84-119`, `standard` at `:127`, `fallback` at `:139-151`. `new Projections(` appears only at `:113` and `:143`. `Projections.of(` has 44 call sites across 12 files, so its signature must not change.
14. `ProjectionReadTest.PROJECTION_READERS` (`jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java:24-36`) restricts who may name each projection's variant enums. The `Composition` and `WorkspaceLook$Icons` readers are fixed lists, so `dev.jasper.app.plugins` may read neither.
15. `KitParser.defaults` (`presentation/KitParser.java:484-513`) refuses every `[defaults]` key outside the five core keys (`:489-492`, `:515-520`). `noColours` walks nested tables (`:243-260`). `DefaultSource(Map<Setting<?>, Object> overrides)` is the whole type (`config/DefaultSource.java:14-45`). `new DefaultSource(` appears in `KitParser.java:207`, `DefaultSourceTest` and `DefaultsPrecedenceTest`.
16. `Kit.faces()` is a `FaceSet` of registered face names. The parser sets each to the file's full name when its family is the declared one (`KitParser.java:396-414`). The shipped regular files' full names are `IBM Plex Sans` and `IBM Plex Mono` (`PresentationConformanceTest.java:94-95`). `FaceSet.PLATFORM` is `("", "", "", Font.MONOSPACED, Font.MONOSPACED)` (`jasper-ui/src/main/java/dev/jasper/ui/metrics/FaceSet.java:15`). `TypeScale.body()` is 13 for `DEFAULT` and 12 for the Enterprise kit (`[type].base = 12`).

**The Enterprise API and its consumers**
17. `public interface Workstation { }` (`plugins/enterprise/src/main/java/dev/jasper/enterprise/api/Workstation.java:4`) is published by `EnterprisePlugin.start` (`plugins/enterprise/src/main/java/dev/jasper/enterprise/EnterprisePlugin.java:16-18`). `plugin.toml` has `version = "0.2.0"` and `exports = ["dev.jasper.enterprise.api"]` (`:3`, `:13`). `DescriptorParser` requires `entry` (`plugins/DescriptorParser.java:60`), so a descriptor-only plugin is not possible.
18. Consumers:
    - Remote, Vault and File Server each have `workstation/Layout.java` with `of(Services)`.
      - Remote: `:23-26`, read at `RemotePlugin.java:157`.
      - Vault: `:24-27`, read at `VaultPlugin.java:110`.
      - File Server: `:24-27`, read at `FileServerPlugin.java:147`.
    - Their `plugin.toml` `requires` name `dev.jasper.enterprise` as optional. Remote `:13`, File Server `:13` and Vault `:12`.
    - Their `build.gradle.kts` hold `compileOnly(project(":jasper-plugin-enterprise"))` and `testImplementation(project(":jasper-plugin-enterprise"))`: Remote `:11`, `:25`; Vault `:9`, `:15`; File Server `:9`, `:23`.
19. `gradle/plugin-architecture.gradle.kts:10` lists `dev.jasper.enterprise.api` in three `declaredImports` entries. `:14-15` define `workstationPackages`, and `:52-54` check them.
20. Test fakes:
    - `FakeEnterprise` exists three times (`plugins/{remote,vault,fileserver}/src/test/java/dev/jasper/<plugin>/workstation/FakeEnterprise.java`).
    - It is used by each `LayoutTest`, `FileServerPluginTest.java:70`, `ClassicSessionsWiringTest.java:12,55,112`, `RemoteCompositionParityTest` (`plugins/remote/src/test/java/dev/jasper/remote/CompositionParityTest.java:290`), `RemotePluginTest.java:59,76` and `VaultPluginTest.java:55,66`.
    - `"dev.jasper.enterprise"` also sits in optional sets at `FileServerPluginTest.java:72`, `ClassicSessionsWiringTest.java:31` (`OPTIONAL`), `CompositionParityTest.java:292`, `RemotePluginTest.java:61,68,77` and `VaultPluginTest.java:57,69`.
    - In jasper-app, `StagedEnterprise` starts the staged jar (`jasper-app/src/test/java/dev/jasper/app/plugins/StagedEnterprise.java`). It is used by `PanelSurfaceFixture.java:61-63,86`, `StagedVaultHost.java:72-78` and `StagedEnterpriseTest`.
    - `EnterprisePluginTest` pins `start`'s three calls (`plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java:43-55`). `PresentationConformanceTest.java:56,59` pins version 0.2.0 and the export.
21. Remote's anchors and Transfers panel follow its `layout`: `RemotePlugin.java:183` and `:243`, `TunnelsUi`, `SftpUi`. `PanelSurfaceFixture.layout()` reads a plugin's `layout()` by reflection (`:116-120`).

**Docs**
22. Documentation to edit:
    - `docs/plugin-authoring.md:64-71` documents consuming `Workstation`.
    - Its in-repo recipe (`:741-746`) says a new plugin needs only `declaredImports`, which becomes true once `workstationPackages` is gone.
    - `:736` shows `dev.jasper.snippets-0.1.0.zip`; Snippets is 0.3.0.
    - The examples `plugin` (`:82-98`) and `pluginpanels` (`:227-270`) come from `SamplePlugin.java`.
    - AGENTS.md's architecture rules name the Enterprise API twice: in the Remote sentence and in the Enterprise sentence.
    - `docs/STATUS.md:99` (plan 14) and `:281` (plan 12) end "Not merged, not pushed.", but local `main` is `f78bb0385`, which holds both.
23. Version tests and tooling:
    - `VersionRangeTest.java:69-70` pins the bundled and Remote ranges.
    - `BundledSamplePluginTest.java:80` pins Sample `0.3.0`.
    - `docs/examples/external-plugin/build.gradle.kts:7` defaults `jasperSdkVersion` to `0.18.1`, and `VersionRangeTest.java:33-40` pins it to `JasperSdk.VERSION`.
    - `jasper-sdk/README.md:45` records 0.18.1 inline, with no section heading.

## Rulings

Each ruling is what was decided, why, and the cost if it is wrong.

1. **Two plans.** 15a covers points 1, 2, 3, 7 and 9 and their docs in 9 tasks; 15b covers points 4, 5, 6 and 10, the remaining docs and STATUS, in 6 tasks. Fifteen tasks exceed the dozen one plan holds. Cost: one more review cycle.
2. **`dev.jasper.sdk.ui.Presentation`, reached as `Appearance.presentation()`.** This is the design's name. `Appearance` already owns what a plugin reads about the look, and `Variant` keeps "dark or light". Cost: a reader may confuse the top-level type with the nested `DialogSpec.Presentation`. Every use of the nested enum is qualified today (fact 3).
3. **`Appearance.presentation()` is abstract.** No platform-free default value exists, and only the host and the testkit implement `Appearance` (fact 2). Cost: an outside test double implementing `Appearance` must add one method.
4. **The facts reported.** `classic` (the classic base), `icons`, `toolWindows`, `titleBar` (resolved for this platform), `menus`, `toolbarSearch`, `uiFace`, `dataFace`, `bodySize` and `toolbarHeight`. These are the design's list, and the host already projects each one. Cost: a fact a plugin needs later is an additive release.
5. **Values a user can change are reported as the look's, fixed for the run, with no change notification.** `toolbarHeight` is the look's default (`DefaultSource`), the faces and body size are the kit's, not `ui.font.*`, and `window.toolbar`'s mode is left out. The look is fixed per process (spec principle 2), plugins cannot read core settings by design, and no bundled plugin needs the effective values. Cost: a plugin that tracks the user's toolbar height or font waits for an additive effective-value API.
6. **`menus` is the look's placement on this platform in the Normal view with the platform's default decorations.** `window.native_decorations` on Windows and Linux, and Compact, are not reported. Mapping: macOS gives `SCREEN_MENU_BAR` when the committed flags keep the screen menu bar, else `WINDOW`; any other platform gives `WINDOW` under a native title (classic on Linux included), else `TITLE_BAR`. Cost: a user with native decorations reads `TITLE_BAR`; nothing reads it today.
7. **The fallback (spec 4.7) reports the default look's chrome with the defaults read at start.** That means the kit's toolbar height and plugin defaults are kept, as `ConfigService` keeps the core ones. Cost: after a failed Enterprise start the Editor still defaults to IBM Plex Mono, which is registered either way.
8. **A new projection `PluginLook` in `Projections`.** It is computed in `Projections.of` and `fallback` and read only by `HostedPresentation` (a new `ProjectionReadTest` row). The type facts come from the `Kit` and the toolbar height from the `DefaultSource`, which `plugins` already receives. This keeps spec 9.1's "each consumer receives its own projection" and the variant-read scans (fact 14) without changing `Projections.of`'s 45 call sites. Cost: one more record.
9. **`toolbarSearch` ships in the SDK now and reads `FIELD` for every look until plan 15b adds the kit point.** One SDK release covers plan 15, and `FIELD` is true until 15b. Cost: none.
10. **`Layout.CLASSIC` exactly when `presentation.classic()`, for compositions and anchors alike.** It is one switch, as today. The classic base is what brings classic controls, and `CompositionParityTest` keeps its two-layout shape. Cost: a classic-base look with rails gets Remote's right-hand Sessions and bottom Transfers on rails.
11. **The `dev.jasper.<plugin>.workstation` packages keep their names.** They name the Workstation composition, and renaming touches about 60 imports with no behaviour change. Their `package-info` files are rewritten. Cost: a reader may look for Enterprise there.
12. **Enterprise keeps `EnterprisePlugin`, whose `start` does nothing.** The descriptor requires `entry` (fact 17). Cost: none.
13. **Enterprise is `0.3.0` once for plan 15.** 15a bumps it; 15b's kit point stays within 0.3.0. This follows the user's "Enterprise 0.3.0 (plan 14's unbumped kit change plus this plan's)". Cost: if 15b merged much later, two looks would share 0.3.0.
14. **Consumers bump their minor version and declare `>=0.18.2`, and so does Enterprise.** Enterprise reads no SDK method, but its kit's `[defaults."<plugin id>"]` table (and plan 15b's `toolbar_search`) needs a 0.18.2 host; an older host would accept it by range and then refuse the kit. The Editor is not bumped: its code and manifest are unchanged, and its new look default is Enterprise's data. Cost: none.
15. **The format of kit defaults for plugin settings.**
    - A `[defaults]` entry whose value is a table is a plugin's table, keyed by the plugin's id. The id must match `[a-z][a-z0-9_.-]{0,127}`, contain a dot, and not be `jasper` or start `jasper.` (the SDK's rule, plus the dot, so a dotted core key written without quotes is refused rather than read as a plugin "ui").
    - Its keys are the plugin's setting keys as its settings file writes them: nested tables flatten with `.`, and each key matches `SettingSpec`'s key rule.
    - Values are kept raw (`String`, `Long`, `Double`, `Boolean`, `TomlArray`), since the kinds are known only when the plugin declares. The parser checks only the shape, and the declared kind's own reader checks the value, so a kit and a settings file are read alike.
    - `DefaultSource` gains `plugins`, with a one-argument convenience meaning none.
    - Cost: a kit author learns of a bad value only from the log.
16. **A rejected value or an undeclared key is logged at WARNING once, the plugin's own default applies, and the plugin starts.** The testkit's `lookDefaultProblems` mirrors the messages' fixed parts. A look must never break a plugin. Cost: the GUI shows nothing.
17. **Reset returns to the kit's default.** Staging the plugin's own default under a kit default saves an explicit value, as spec 5.8 rules for core keys. Cost: a user who expects Reset to give the plugin's default gets the look's.
18. **Enterprise's kit sets only `[defaults."dev.jasper.editor"] font_family = "IBM Plex Mono"`.** Enterprise bundles `presentation/fonts/ibm-plex-mono-regular.ttf`, the classic data face. No mock shows the editor, so its size stays 13. The Editor's rule accepts a family only if it is installed, and `LookFonts.register` registers Plex before plugins start. Cost: a user may expect 12 pt editor text; a failed registration falls back with one logged line.
19. **The Sample shows the query and a kit default.** Under labelled stripes the Sample panel starts in the bottom strip, else on the left. Its existing `demo_step_millis` shows a kit default, with a test and the guide's snippet; no new setting. Cost: none.
20. **The external Hello example gets only its `jasperSdkVersion` default, `0.18.2`.** It builds outside `check` from release jars, so code using the query could not be verified here, and the guide's compiled Sample snippet carries the pattern. Cost: authors copy from the guide.
21. **The `FakeKit.BUILT_INS` parity test is a jasper-app test that reads the private field reflectively.** No new testkit API is needed. Cost: a rename breaks it loudly.
22. **`dev.jasper.sdk.testing.FakePresentations` holds `STANDARD` and `CLASSIC`.** They replace the three `FakeEnterprise` copies. Cost: one new public testkit type.
23. **Migration rows.** Delete the rows of deleted files and add rows for new or newly matching ones (Global Constraints). Cost: none.
24. **Correct the two stale STATUS lines.** Plan 14's `:99` and plan 12's `:281` become "Merged to local `main` (`f78bb0385`), not pushed." The approved design lists them as stale. Cost: none.
25. **The docs are split by the plans' points.** 15a fixes the stale wording on the surfaces it touches (`Variant`, `AppEvents`, `PluginContext`, spec 4.1, the two STATUS lines). 15b fixes `StandardMenu`, `PanelSpec`, `Panels`, `Rail`, `sdk-icons.md` and the plugin-window paragraph. Cost: none.
26. **`PluginRuntime.presentation(Kit)` becomes `presentation(Kit, PluginLook, DefaultSource)`.** Its four callers are updated; there is no one-argument overload. Cost: none.
27. **One spec header line for plan 15.** 15a writes it; 15b adds its sections to the same line. Cost: none.

---

### Task 1: SDK 0.18.2 — `Presentation`, `Appearance.presentation()`, the testkit and the contract

**Files:**
- Create: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Presentation.java`
- Create: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PresentationValuesTest.java`
- Create: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePresentations.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/HostedPresentation.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/KitIconNamesParityTest.java`
- Modify:
  - `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Appearance.java`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java:6`
  - the five version pins of fact 1 (`KitCompatibilityTest.java:70` included)
  - `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java`
  - `FakePluginContext.java:239-271`
  - `ContractHarness.java`
  - `PluginContractTest.java`
  - `FakeContractTest.java`
  - `jasper-app/src/main/java/dev/jasper/app/plugins/PluginHost.java:98-100`
  - `HostedContext.java:85`
  - `HostedUi.java:362-404`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java`
  - `HostedKitTest.java:214`
  - `VersionRangeTest.java`
  - `docs/examples/external-plugin/build.gradle.kts:7`
  - `jasper-sdk/README.md`
  - `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Produces:

  ```java
  package dev.jasper.sdk.ui;
  /** ... @since 0.18.2 */
  public record Presentation(boolean classic, Icons icons, ToolWindows toolWindows, TitleBar titleBar, Menus menus,
                             ToolbarSearch toolbarSearch, java.util.Optional<String> uiFace, java.util.Optional<String> dataFace,
                             int bodySize, int toolbarHeight) {
      public enum Icons { NEW_UI, CLASSIC }
      public enum ToolWindows { RAILS, STRIPES }
      public enum TitleBar { UNIFIED, NATIVE, CLASSIC }
      public enum Menus { SCREEN_MENU_BAR, TITLE_BAR, WINDOW }
      public enum ToolbarSearch { FIELD, ICON }
  }
  ```

  - The compact constructor rejects a null part, a present face that is blank, a `bodySize` outside 6 to 96 and a `toolbarHeight` outside 28 to 64. The messages are `"bodySize: 5 is outside 6-96"` and `"toolbarHeight: 20 is outside 28-64"`.
  - Each component's javadoc says what it means. `classic`: the look starts from the classic catalog (classic controls, IntelliJ Classic's layout). `uiFace` and `dataFace`: the look's face for its regular weight, a name `java.awt.Font` accepts, or empty for the platform face (`Font.MONOSPACED` for data). `bodySize`: the look's body text size in points. `toolbarHeight`: the look's default toolbar height in pixels. Say for the last three that the user's own settings are not reported.
  - `Appearance`: `Presentation presentation();` with the javadoc "The look Jasper runs with, chosen at start: the same value for every plugin until Jasper restarts. Safe from any thread. @return the presentation @since 0.18.2".
  - `dev.jasper.sdk.testing.FakePresentations` (final, private constructor):
    - `public static final Presentation STANDARD = new Presentation(false, NEW_UI, RAILS, UNIFIED, TITLE_BAR, FIELD, Optional.empty(), Optional.empty(), 13, 30)`, the default look as on Windows and Linux.
    - `public static final Presentation CLASSIC = new Presentation(true, Icons.CLASSIC, STRIPES, NATIVE, WINDOW, FIELD, Optional.of("IBM Plex Sans"), Optional.of("IBM Plex Mono"), 12, 28)`, Enterprise's on Linux.
  - `FakePluginHost`:
    - `public void setPresentation(Presentation presentation)` throws `IllegalStateException("Plugins have already started")` once any plugin has started, and null is rejected;
    - `public Presentation presentation()`, which defaults to `FakePresentations.STANDARD`.
  - `ContractHarness`: `void setPresentation(dev.jasper.sdk.ui.Presentation presentation);`, called before any plugin starts.
  - App:
    - package-private `final class HostedPresentation` with `static dev.jasper.sdk.ui.Presentation standard()`. In this task it returns `FakePresentations.STANDARD`'s values, built in place, since jasper-app's main code cannot see the testkit; Task 2 replaces the body.
    - `PluginHost`: `volatile dev.jasper.sdk.ui.Presentation presentation = HostedPresentation.standard();`.
    - `HostedUi`: a field `java.util.function.Supplier<dev.jasper.sdk.ui.Presentation> presentation = HostedPresentation::standard` and `void presentation(java.util.function.Supplier<dev.jasper.sdk.ui.Presentation> source)`, which `HostedContext` calls right after making `ui` with `() -> host.presentation`. The `Appearance` reads the supplier on each call, so the order against `HostedKit`'s construction does not matter.

- [ ] **Step 0: Capture the baseline** (no commit)

Before any source change, render the default look and run the pairs from `f78bb0385`'s code. Task 9, and plan 15b's last task, compare against these files.

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15-baseline
git diff --quiet f78bb0385 HEAD -- . ':(exclude)docs' && echo "code is f78bb0385's"   # must print; otherwise stop
test -z "$(git status --porcelain)" && echo clean                                     # must print
rm -rf "$base" jasper-ui/build/gallery && mkdir -p "$base/app"
./gradlew :jasper-ui:gallery --rerun && cp -R jasper-ui/build/gallery/dark-1x "$base/gallery-dark-1x"
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light --rerun && cp -R jasper-ui/build/gallery/light-1x "$base/gallery-light-1x"
./gradlew :jasper-app:test --continue
for f in jasper-app/build/chrome-renders/chrome-default*.png jasper-app/build/chrome-renders/settings-default*.png \
         jasper-app/build/chrome-renders/chrome-complement*.png jasper-app/build/chrome-renders/settings-complement*.png \
         jasper-app/build/chrome-renders/chrome-fallback*.png jasper-app/build/chrome-renders/settings-fallback*.png \
         jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png; do cp "$f" "$base/app/"; done
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$base/pairs-out" > "$base/pairs.txt"; echo "exit $?"
ls "$base/gallery-dark-1x" | wc -l; ls "$base/gallery-light-1x" | wc -l; ls "$base/app" | wc -l; grep -c ': PASS ' "$base/pairs.txt"
```

Expected:
- both prints, and `:jasper-app:test` BUILD SUCCESSFUL;
- every `cp` succeeds. If one reports a missing file, read the names the two tests write and copy those; never drop a default-look render;
- `exit 0`;
- `161`, `161`, `11` and `101`.

- [ ] **Step 1: Write the failing SDK value test**

`PresentationValuesTest`:
- `aPresentationKeepsItsFactsAndRefusesMissingOnes`: the `CLASSIC` values above round-trip through the accessors. `new Presentation(false, null, …)` throws `NullPointerException`. `Optional.of(" ")` as `uiFace` throws `IllegalArgumentException`.
- `theBodySizeAndToolbarHeightHaveTheirRanges`: 5 and 97 for `bodySize`, and 27 and 65 for `toolbarHeight`, throw `IllegalArgumentException` with the messages above. 6, 96, 28 and 64 are accepted.
- `theVersionIsZeroEighteenTwo`: `assertThat(JasperSdk.VERSION).isEqualTo("0.18.2")`.

Also change the four SDK pins of fact 1 to `"0.18.2"`. In `KitCompatibilityTest.aZeroSeventeenRangeIsSkippedBeforeItLoads` (`:70`), build the expected text from the constant: `"needs SDK >=0.17.0, <0.18.0; this is SDK " + JasperSdk.VERSION`.

Run: `./gradlew :jasper-sdk:test --tests '*PresentationValuesTest' --tests '*CapabilitiesTest'`
Expected: FAIL to compile (`Presentation` does not exist).

- [ ] **Step 2: Add `Presentation`, `Appearance.presentation()` and the version**

Add `Presentation.java` as in Interfaces and the abstract `Appearance.presentation()`. Set `JasperSdk.VERSION = "0.18.2"`.

Run: `./gradlew :jasper-sdk:test`
Expected: PASS.

- [ ] **Step 3: Write the failing contract case and the parity test**

In `PluginContractTest`, add after the appearance cases:

```java
/** SDK 0.18.2: the presentation is the host's, the same for every plugin and on any thread, and a theme change keeps it. */
@Test void presentationIsTheHostsForEveryPluginOnAnyThreadAndThroughThemeChanges() throws Exception {
    var classic = new Presentation(true, Presentation.Icons.CLASSIC, Presentation.ToolWindows.STRIPES, Presentation.TitleBar.NATIVE,
        Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD, Optional.of("IBM Plex Sans"), Optional.of("IBM Plex Mono"), 12, 28);
    h.setPresentation(classic);
    var first = new AtomicReference<PluginContext>(); var second = new AtomicReference<PluginContext>();
    h.start(new PluginInfo("test.lookone", "One", "1.0.0", Set.of()), Set.of(), Set.of(), first::set);
    h.start(new PluginInfo("test.looktwo", "Two", "1.0.0", Set.of()), Set.of(), Set.of(), second::set);
    assertThat(first.get().appearance().presentation()).isEqualTo(classic);
    assertThat(second.get().appearance().presentation()).isEqualTo(classic);
    var offThread = CompletableFuture.supplyAsync(() -> first.get().appearance().presentation()).get(5, TimeUnit.SECONDS);
    assertThat(offThread).isEqualTo(classic);
    h.setVariant(Variant.LIGHT); h.flush();
    assertThat(first.get().appearance().presentation()).isEqualTo(classic);
}
```

Place it right after `stoppingOrFailingRemovesEveryContributionAndAppearanceFollowsTheHost` (`:787-820`), before `aPanelMayNameOneOfThePluginsOwnActionsAsItsToggle` (`:822`). The suite's harness field is `h` (`PluginContractTest.java:124`); import `dev.jasper.sdk.ui.Presentation` (`CompletableFuture`, `TimeUnit` and `AtomicReference` are already imported). Test bodies run off the UI thread, so the `supplyAsync` read is another worker.

Implement `setPresentation` in `FakeContractTest`'s harness (`host.setPresentation(presentation)`) and in `AppContractTest`'s (`onEdt(() -> host.presentation = presentation)`).

Create `KitIconNamesParityTest.theTestkitsBuiltInIconNamesAreTheHosts`:
- read `dev.jasper.sdk.testing.FakeKit`'s private static `BUILT_INS` with `getDeclaredField` and `setAccessible(true)`;
- assert it equals `new dev.jasper.ui.icons.IconRegistry().names()`;
- close the registry if it is closeable.

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest'`
Expected: FAIL to compile (`setPresentation` and the fake's `presentation()` do not exist).

- [ ] **Step 4: Implement the testkit and the host side**

- `FakePresentations` as in Interfaces.
- `FakePluginHost.setPresentation` and `presentation()`. `FakePluginContext.appearance()` gains `@Override public Presentation presentation() { return host.presentation(); }`, which reads a volatile field.
- `HostedPresentation.standard()` as in Interfaces.
- `PluginHost.presentation`, `HostedUi.presentation(Supplier)`, its `Appearance.presentation()` (`return source.get();`, with no thread check), and the `HostedContext` call.
- `HostedKitTest`'s anonymous `Appearance` returns `HostedPresentation.standard()`.
- `migration.csv` rows: `Presentation.java` (owner `sdk.ui`), `PresentationValuesTest.java`, `FakePresentations.java` (owner `sdk.testing`), `HostedPresentation.java` and `KitIconNamesParityTest.java` (owner `app.plugins`), each `nonvisual-helper,none,none,<its test>`. Give rows only to the files the test reports, and copy the owner style of the neighbouring rows.

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest' && ./gradlew :jasper-app:test --tests '*AppContractTest' --tests '*KitIconNamesParityTest' --tests '*HostedKitTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 5: Versions and the README**

- `VersionRangeTest.pluginsBuiltForOlderMinorsAreIncompatibleWithZeroEighteen`: keep the `>=0.18.1` line, renamed "the former Remote range". Add `assertThat(VersionRange.parse(">=0.18.2, <0.19").contains(host)).as("the look query's range").isTrue();`. Task 3 only relabels this new line.
- `docs/examples/external-plugin/build.gradle.kts:7`: change `getOrElse("0.18.1")` to `getOrElse("0.18.2")`.
- `jasper-sdk/README.md`:
  - add `Presentation` to the `dev.jasper.sdk.ui` row of the package table;
  - add this section above "## Shell variables, the file editor and program tabs (SDK 0.17.1)":

    > ## The look Jasper runs with (SDK 0.18.2)
    >
    > `Appearance.presentation()` returns a `Presentation`: whether the look is classic, its icon set, how tool windows are reached, the title bar and where the menus are on this platform, how the toolbar offers Search Everywhere, the look's UI and data faces, its body size and its default toolbar height. It is chosen at start and is the same for every plugin until Jasper restarts; read it from any thread. A plugin that composes differently under a classic look branches on it once in `start`. The testkit's `FakePluginHost.setPresentation` sets it, and `FakePresentations.STANDARD` and `CLASSIC` are ready values. Plugins that read it declare `sdk = ">=0.18.2, <0.19"`.

Run: `./gradlew :jasper-app:test --tests '*VersionRangeTest' --tests '*KitCompatibilityTest' --tests '*AppDocumentationTest' && ./gradlew :jasper-sdk:check :jasper-sdk-testkit:check`
Expected: PASS (`check` includes doclint and `verifySdkRelease`).

- [ ] **Step 6: Commit**

```bash
git add -A jasper-sdk jasper-sdk-testkit jasper-app docs/examples/external-plugin/build.gradle.kts docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 1: SDK 0.18.2, Appearance.presentation() on both hosts

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 2: `PluginLook` and `HostedPresentation` — the host reports its look

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/presentation/PluginLook.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/HostedPresentationTest.java`
- Modify:
  - `presentation/Projections.java:24-26,84-151`
  - `plugins/HostedPresentation.java`
  - `plugins/PluginRuntime.java:389-394,683-688`
  - `application/JasperApplication.java:989`
  - `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`
  - `architecture/ProjectionReadTest.java:24-36`
  - `plugins/PluginRuntimeTest.java:650,670`
  - `settingsui/EnterpriseSettings.java:170`
  - `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: `dev.jasper.sdk.ui.Presentation` (Task 1).
- Produces:
  - `public record PluginLook(boolean classic, boolean classicIcons, boolean stripes, TitleBar titleBar, Menus menus, Search search)` in `dev.jasper.app.presentation`, with `public enum TitleBar { UNIFIED, NATIVE, CLASSIC }`, `public enum Menus { SCREEN_MENU_BAR, TITLE_BAR, WINDOW }` and `public enum Search { FIELD, ICON }`. Its javadoc: "What plugins learn about the presentation (spec 9.1), read only by the plugin runtime's adapter."
  - `Projections` gains a twelfth component, `PluginLook plugins`.
  - `static dev.jasper.sdk.ui.Presentation HostedPresentation.of(PluginLook look, dev.jasper.ui.kit.Kit kit, dev.jasper.app.config.DefaultSource defaults)`.
  - `standard()` becomes `of(Projections.standard(new Projections.Host(Projections.Platform.of(System.getProperty("os.name")), false)).plugins(), Kit.DEFAULT, DefaultSource.CORE)`.
  - `public void PluginRuntime.presentation(dev.jasper.ui.kit.Kit kit, PluginLook look, DefaultSource defaults)` replaces the one-argument method and keeps its "Plugins have already started" refusal. It rejects a null argument and stores the three values together (one nullable field holding all three, or three set at once). When the host is made, and only when `presentation(...)` was called, it sets `created.kit` as today and `created.presentation = HostedPresentation.of(look, kit, defaults)`; Task 6 adds `created.defaults` under the same condition. Otherwise the host keeps its own `HostedPresentation.standard()` (and, from Task 6, `DefaultSource.CORE`): most `PluginRuntimeTest` cases never call `presentation(...)`.

- [ ] **Step 1: Write the failing projection tests**

In `ProjectionsTest` (it has `MAC`, `WINDOWS` and `LINUX` hosts at `:12-13`; import `dev.jasper.app.testsupport.EnterpriseLook`, which the file names fully qualified today):

```java
@Test void thePluginLookIsTheLookOnEachPlatform() {
    var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC).plugins();
    assertThat(standard).isEqualTo(new PluginLook(false, false, false, PluginLook.TitleBar.UNIFIED, PluginLook.Menus.SCREEN_MENU_BAR, PluginLook.Search.FIELD));
    assertThat(Projections.of(ChromeSpec.DEFAULT, false, false, "", WINDOWS).plugins().menus()).isEqualTo(PluginLook.Menus.TITLE_BAR);
    assertThat(Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX).plugins().menus()).isEqualTo(PluginLook.Menus.TITLE_BAR);
    var classic = EnterpriseLook.CHROME;
    assertThat(Projections.of(classic, true, true, "dev.jasper.enterprise", MAC).plugins())
        .isEqualTo(new PluginLook(true, true, true, PluginLook.TitleBar.CLASSIC, PluginLook.Menus.SCREEN_MENU_BAR, PluginLook.Search.FIELD));
    assertThat(Projections.of(classic, true, true, "dev.jasper.enterprise", WINDOWS).plugins())
        .isEqualTo(new PluginLook(true, true, true, PluginLook.TitleBar.CLASSIC, PluginLook.Menus.TITLE_BAR, PluginLook.Search.FIELD));
    assertThat(Projections.of(classic, true, true, "dev.jasper.enterprise", LINUX).plugins())
        .isEqualTo(new PluginLook(true, true, true, PluginLook.TitleBar.NATIVE, PluginLook.Menus.WINDOW, PluginLook.Search.FIELD));
    var nativeWindowMenus = EnterpriseLook.titled(classic, ChromeSpec.TitleBar.NATIVE);
    assertThat(Projections.of(nativeWindowMenus, true, true, "dev.jasper.enterprise", MAC).plugins().menus()).isEqualTo(PluginLook.Menus.WINDOW);
}

@Test void theFallbacksPluginLookIsTheDefaultLooksUnderTheCommittedFlags() {
    assertThat(Projections.fallback(DesktopFlags.STANDARD, MAC).plugins()).isEqualTo(Projections.standard(MAC).plugins());
    assertThat(Projections.fallback(new DesktopFlags(false, true), LINUX).plugins())
        .isEqualTo(new PluginLook(false, false, false, PluginLook.TitleBar.NATIVE, PluginLook.Menus.WINDOW, PluginLook.Search.FIELD));
}
```

Add to `ProjectionReadTest.PROJECTION_READERS`:

```java
// Plan 15a (spec 9.1): what plugins learn of the look is read only by the plugin runtime's adapter.
Pattern.compile("dev/jasper/app/presentation/PluginLook\\$(?:TitleBar|Menus|Search)(?=[;$.]|$)"),
    type -> owns(type, "dev/jasper/app/plugins/HostedPresentation"),
```

In `onlyTheFactoriesReadTheLooksVariants`, add a liveness assertion that some class names `dev/jasper/app/presentation/PluginLook$Menus` and that `dev/jasper/app/plugins/HostedPresentation` is among them. In `aLookReadElsewhereIsReported`, add a `PluginLook$Menus` field to the probe and expect `hasSize(6)`.

Run: `./gradlew :jasper-app:test --tests '*ProjectionsTest' --tests '*ProjectionReadTest'`
Expected: FAIL to compile (`PluginLook` does not exist).

- [ ] **Step 2: Add `PluginLook` and compute it in `Projections`**

- In `of`: `new PluginLook(classicBase, chrome.icons() == ChromeSpec.Icons.CLASSIC, stripes, unified ? UNIFIED : classicTitle ? CLASSIC : NATIVE, host.mac() ? (screenMenuBar ? SCREEN_MENU_BAR : WINDOW) : (nativeTitle ? WINDOW : TITLE_BAR), PluginLook.Search.FIELD)`. The variables are `of`'s own (`:85-95`).
- In `fallback`: `new PluginLook(false, false, false, committed.nativeTitle() ? NATIVE : UNIFIED, host.mac() ? (committed.screenMenuBar() ? SCREEN_MENU_BAR : WINDOW) : (committed.nativeTitle() ? WINDOW : TITLE_BAR), FIELD)`.
- Add the component's `@param plugins what plugins learn of the look (plan 15a)` and its `requireNonNull`.

Run: `./gradlew :jasper-app:test --tests '*ProjectionsTest' --tests '*ProjectionReadTest'`
Expected: `ProjectionsTest` PASS; `ProjectionReadTest`'s liveness assertion still FAILS until Step 4.

- [ ] **Step 3: Write the failing adapter tests**

`HostedPresentationTest` (package `dev.jasper.app.plugins`):

```java
static final Projections.Host LINUX = new Projections.Host(Projections.Platform.LINUX, false);

@Test void theDefaultLookIsReportedWithThePlatformFacesAndTheCoreToolbarHeight() {
    var look = Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX).plugins();
    assertThat(HostedPresentation.of(look, Kit.DEFAULT, DefaultSource.CORE)).isEqualTo(new Presentation(false, Presentation.Icons.NEW_UI,
        Presentation.ToolWindows.RAILS, Presentation.TitleBar.UNIFIED, Presentation.Menus.TITLE_BAR, Presentation.ToolbarSearch.FIELD,
        Optional.empty(), Optional.empty(), 13, 30));
}

@Test void enterpriseIsReportedFromItsKitAndItsDefaults() {
    var parsed = EnterpriseResources.facts().parsed().orElseThrow();
    var look = Projections.of(parsed.chrome(), true, true, "dev.jasper.enterprise", LINUX).plugins();
    assertThat(HostedPresentation.of(look, parsed.kit(), parsed.defaults())).isEqualTo(new Presentation(true, Presentation.Icons.CLASSIC,
        Presentation.ToolWindows.STRIPES, Presentation.TitleBar.NATIVE, Presentation.Menus.WINDOW, Presentation.ToolbarSearch.FIELD,
        Optional.of("IBM Plex Sans"), Optional.of("IBM Plex Mono"), 12, 28));
}

@Test void theFallbackReportsTheDefaultLooksChromeWithTheDefaultsReadAtStart() {
    var parsed = EnterpriseResources.facts().parsed().orElseThrow();
    var provider = new dev.jasper.app.presentation.Presentation.Provider("dev.jasper.enterprise", "Enterprise", "0.3.0", "d");
    var fallback = dev.jasper.app.presentation.Presentation.of(provider, parsed, LINUX).fallback(LINUX);
    var reported = HostedPresentation.of(fallback.projections().plugins(), fallback.kit(), fallback.defaults());
    assertThat(reported.classic()).isFalse();
    assertThat(reported.icons()).isEqualTo(Presentation.Icons.NEW_UI);
    assertThat(reported.uiFace()).isEmpty();
    assertThat(reported.bodySize()).isEqualTo(13);
    assertThat(reported.toolbarHeight()).as("the kit's default, which the configuration keeps").isEqualTo(28);
}
```

In `PluginRuntimeTest`, add `aStartedPluginReadsThePresentationTheRuntimeWasGiven`, modelled on `pluginsViewsAreMadeWithThePresentationGivenBeforeTheyStart` (`:641`):
- build a development plugin with `PluginJars.build` whose `start` runs `System.setProperty("jasper.test.look", context.appearance().presentation().classic() + " " + context.appearance().presentation().toolWindows());`;
- before start, call `runtime.presentation(Kit.DEFAULT, Projections.of(EnterpriseLook.CHROME, true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.LINUX, false)).plugins(), DefaultSource.CORE)`;
- after start and `settle()`, assert `System.getProperty("jasper.test.look")` is `"true STRIPES"`, then clear the property in `finally`;
- assert the setter still refuses after start, with "Plugins have already started".

Run: `./gradlew :jasper-app:test --tests '*HostedPresentationTest' --tests '*PluginRuntimeTest'`
Expected: FAIL to compile (`HostedPresentation.of` and the three-argument `presentation` do not exist).

- [ ] **Step 4: Implement the adapter and the wiring**

- `HostedPresentation.of`:
  - map `look`'s enums by name: `Presentation.TitleBar.valueOf(look.titleBar().name())`, and likewise for the others;
  - `icons` from `classicIcons`, `toolWindows` from `stripes`;
  - `uiFace` is `kit.faces().uiFace(400)`, empty when blank;
  - `dataFace` is `kit.faces().dataRegular()`, empty when blank or equal to `java.awt.Font.MONOSPACED`;
  - `bodySize` is `Math.round(kit.type().body())`;
  - `toolbarHeight` is `defaults.value(dev.jasper.app.config.CoreSettings.WINDOW_TOOLBAR_HEIGHT)`.
- `PluginRuntime.presentation(Kit, PluginLook, DefaultSource)`: store all three together, then use them when making the host only if they were given (Interfaces). A runtime that was never given a presentation makes a host with `HostedPresentation.standard()`, so `PluginRuntimeTest`'s other cases run unchanged.
- `JasperApplication.java:989`: `plugins.presentation(themes.kit(), presentation.projections().plugins(), presentation.defaults());`. By then the field is the fallback if the kit was blamed (`:326-334`).
- `PluginRuntimeTest.java:650,670`: pass `Projections.standard(new Projections.Host(Projections.Platform.LINUX, false)).plugins()` and `DefaultSource.CORE` (the test has no host of its own).
- `EnterpriseSettings.java:170`: pass the test's own `projections.plugins()` (`:153`) and its local `defaults` (`:147`, the shipped kit's `EnterpriseResources.facts().parsed().orElseThrow().defaults()`), as `JasperApplication` gives them under Enterprise.
- `migration.csv`: a row for `HostedPresentationTest.java` if it matches.

Run: `./gradlew :jasper-app:test --tests '*HostedPresentationTest' --tests '*PluginRuntimeTest' --tests '*ProjectionReadTest' --tests '*ProjectionsTest' --tests '*ThemeMigrationCoverageTest' --tests '*SettingsPagesMockTest' --tests '*SettingsChromeMockTest'`
Expected: PASS. If an Enterprise Settings render changes because Remote's or Vault's page now composes classic, stop and record it for Task 9's STATUS entry as the user's 2026-10-19 direction; that is the truth under Enterprise. Never lower a floor.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 2: PluginLook and HostedPresentation report the look to plugins

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 3: Remote, Vault and File Server read the presentation

**Files:**
- Modify:
  - `plugins/{remote,vault,fileserver}/src/main/java/dev/jasper/<plugin>/workstation/Layout.java` and `package-info.java`;
  - `RemotePlugin.java:157`, `VaultPlugin.java:110` and `FileServerPlugin.java:147`;
  - the three `plugin.toml` files (version, `sdk`, `requires`) and the three `build.gradle.kts` files (fact 18);
  - `gradle/plugin-architecture.gradle.kts:10`.
- Delete: the three `workstation/FakeEnterprise.java`.
- Modify the tests: the three `workstation/LayoutTest.java`, `FileServerPluginTest.java:70,72`, `ClassicSessionsWiringTest.java:12,31,55,112`, Remote's `CompositionParityTest.java:290,292`, `RemotePluginTest.java:59,61,68,76,77` and `VaultPluginTest.java:55,57,66,69`.
- Modify the jasper-app fixtures: `PanelSurfaceFixture.java:33-63,86`, `StagedVaultHost.java:49-78,321` and `StagedEnterpriseTest.java`, renamed `StagedClassicLayoutTest.java`. Delete `StagedEnterprise.java`.
- Modify `docs/design/theme-engine/migration.csv` and `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java:69-70`.

**Interfaces:**
- Consumes: `Appearance.presentation()`, `FakePluginHost.setPresentation`, `FakePresentations.CLASSIC` and `STANDARD` (Task 1); `PluginHost.presentation` and `HostedPresentation.of` (Tasks 1 and 2).
- Produces: in each plugin, `public static Layout of(dev.jasper.sdk.ui.Presentation presentation)`, which returns `CLASSIC` exactly when `presentation.classic()`. The `of(Services)` overload is removed.

- [ ] **Step 1: Rewrite the layout tests**

In each `LayoutTest`, replace the three cases with:

```java
/** Plan 15a: any classic look composes the classic layout; the default look keeps the rows. */
@Test void aClassicPresentationIsClassicAndTheDefaultIsRows() {
    assertThat(Layout.of(FakePresentations.CLASSIC)).isEqualTo(Layout.CLASSIC);
    assertThat(Layout.of(FakePresentations.STANDARD)).isEqualTo(Layout.ROWS);
}

@Test void aStartedPluginReadsItsLayoutFromTheHostsPresentation() {
    try (var host = new FakePluginHost()) {
        host.setPresentation(FakePresentations.CLASSIC);
        var layout = new AtomicReference<Layout>();
        host.start(PROBE, Set.of(), Set.of(), context -> layout.set(Layout.of(context.appearance().presentation())));
        assertThat(layout.get()).isEqualTo(Layout.CLASSIC);
    }
}
```

In every other test from fact 20, delete `FakeEnterprise.start(host)` and call `host.setPresentation(FakePresentations.CLASSIC)` before the test's first `host.start`, a `FakeVault`'s included: `setPresentation` throws once any plugin has started (Task 1). `ClassicSessionsWiringTest.theComboAndTheTreeFollowTheActiveSshPaneWhileNothingIsChosen` (`:110-112`) starts `FakeVault` at `:111` before `FakeEnterprise.start` at `:112`, so its call moves above `:111`; every other site calls `FakeEnterprise.start` first and is replaced in place. Drop `FakeEnterprise.INFO.id()` and `"dev.jasper.enterprise"` from the optional sets, and drop the imports.

Add `RemotePluginTest.underTheDefaultLookSessionsStaysLeft`: a default `FakePluginHost`; `assertThat(host.panels()).contains("dev.jasper.remote.panel|Sessions|LEFT|dev.jasper.remote.hosts")`. `FakePluginHost.panels()` appends `|` and the toggle action id (`FakePluginHost.java:957-958`), and Sessions names `HOSTS` (`RemotePlugin.java:243-244`).

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-vault:test :jasper-plugin-fileserver:test`
Expected: FAIL to compile (`Layout.of(Presentation)` does not exist).

- [ ] **Step 2: Switch the three plugins**

- `Layout.of(Presentation)` as in Interfaces. The class javadoc becomes "Which composition <Plugin>'s views take (Enterprise presentation spec 14.3): {@link #CLASSIC} under a classic look ({@code Appearance.presentation().classic()}), else {@link #ROWS}."
- `package-info.java`: "<Plugin>'s choice of composition for the active presentation: {@link dev.jasper.<plugin>.workstation.Layout}."
- The three `start` call sites become `Layout.of(context.appearance().presentation())`.
- Manifests:
  - Remote `version = "0.15.0"`, `requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }, { id = "dev.jasper.editor", version = ">=0.2", optional = true }]`;
  - Vault `0.7.0` with no `requires` line;
  - File Server `0.6.0` with `requires = [{ id = "dev.jasper.vault", version = ">=0.2", optional = true }]`;
  - all three `sdk = ">=0.18.2, <0.19"`.
- `build.gradle.kts`: delete each `compileOnly` and `testImplementation` of `:jasper-plugin-enterprise`.
- `gradle/plugin-architecture.gradle.kts:10`: delete `"dev.jasper.enterprise.api"` from the three lists. Vault's entry becomes empty, so delete it.
- Delete the three `FakeEnterprise.java`.

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-vault:test :jasper-plugin-fileserver:test`
Expected: PASS.

- [ ] **Step 3: Move the staged fixtures off the Enterprise jar**

- `PanelSurfaceFixture`:
  - delete the `enterprise` loader field and its `StagedEnterprise.start` (`:61`);
  - in Enterprise mode, set `vault.host.presentation = HostedPresentation.of(Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(Projections.Platform.LINUX, false)).plugins(), EnterpriseLook.kit(), EnterpriseResources.facts().parsed().orElseThrow().defaults())` before the plugin starts;
  - the exports map is only `dev.jasper.vault.api`, and the optional sets lose `dev.jasper.enterprise`;
  - drop the loader's close (`:343`).
- `StagedVaultHost` does the same (`:49`, `:72-78`, `:321`). Put that one expression in a package-private test helper `static Presentation StagedVaultHost.enterprisePresentation()` and use it from both fixtures.
- Delete `StagedEnterprise.java`. Rename `StagedEnterpriseTest` to `StagedClassicLayoutTest`; its class javadoc becomes "Under the Enterprise presentation a staged consumer composes CLASSIC, and ROWS under the default look." and its body is unchanged.
- `migration.csv`: delete the `StagedEnterprise.java` and `StagedEnterpriseTest.java` rows (`:1421-1422`), and add a `StagedClassicLayoutTest.java` row copying the old test row's fields with the new path and name. Rows for the three `Layout.java` and `LayoutTest.java`, if the test now reports them, use owners `dev.jasper.remote`, `dev.jasper.vault` and `dev.jasper.fileserver`.
- `VersionRangeTest`: only relabel Task 1's `">=0.18.2, <0.19"` assertion to `.as("Enterprise's, Remote's, Vault's, File Server's and Sample's range")`. Leave the `>=0.18.1` line ("the former Remote range") as Task 1 left it, and add no other line.

Run: `./gradlew :jasper-app:test --tests '*StagedClassicLayoutTest' --tests '*ClassicCompositionRenderTest' --tests '*ClassicVaultRenderTest' --tests '*VersionRangeTest' --tests '*ThemeMigrationCoverageTest' && ./gradlew verifyPluginArchitecture`
Expected: PASS. `ClassicCompositionRenderTest`'s and `ClassicVaultRenderTest`'s mock checks are unchanged: the plugins compose CLASSIC as before.

- [ ] **Step 4: Commit**

```bash
git add -A plugins/remote plugins/vault plugins/fileserver gradle/plugin-architecture.gradle.kts jasper-app docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 3: Remote, Vault and File Server compose from the SDK presentation

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 4: Remove the Enterprise API

**Files:**
- Delete:
  - `plugins/enterprise/src/main/java/dev/jasper/enterprise/api/Workstation.java`
  - `plugins/enterprise/src/main/java/dev/jasper/enterprise/api/package-info.java`
- Modify:
  - `plugins/enterprise/src/main/java/dev/jasper/enterprise/EnterprisePlugin.java`
  - `plugins/enterprise/src/main/java/dev/jasper/enterprise/package-info.java`
  - `plugins/enterprise/src/main/resources/plugin.toml:3,13`
  - `plugins/enterprise/build.gradle.kts:1`
  - `plugins/enterprise/src/test/java/dev/jasper/enterprise/EnterprisePluginTest.java`
  - `gradle/plugin-architecture.gradle.kts:13-15,52-54`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:56,59`
  - `AGENTS.md`
  - `docs/design/theme-engine/migration.csv:1351` (the `EnterprisePlugin.java` row's disposition)

**Interfaces:**
- Consumes: Task 3 removed every consumer.
- Produces: `EnterprisePlugin.start(PluginContext)` with an empty body.

- [ ] **Step 1: Write the failing tests**

- `EnterprisePluginTest` keeps only `startDoesNothing`: parse `EnterprisePlugin.class` as now, collect its `start` method's invoke and throw instructions, and `assertThat(steps).isEmpty()`. The javadoc: "Spec 4.6: start cannot throw, so a failed Enterprise start is never the plugin's own doing; the look is data, so start does nothing." Delete the two `Workstation` cases and `CONSUMER`. `ENTERPRISE`'s version becomes `"0.3.0"`.
- `PresentationConformanceTest.theDescriptorIsSpecFourOnes`: `Version.parse("0.3.0")`, `assertThat(descriptor.exports()).as("no API (plan 15a)").isEmpty()` and `assertThat(descriptor.sdk()).as("the kit's plugin defaults need SDK 0.18.2 (Rulings 14)").isEqualTo(VersionRange.parse(">=0.18.2, <0.19"))`. If `VersionRange` has no value equality, compare `toString()` or test `contains` on 0.18.1 (false) and 0.18.2 (true).

Run: `./gradlew :jasper-plugin-enterprise:test && ./gradlew :jasper-app:test --tests '*PresentationConformanceTest'`
Expected: FAIL (`start` still calls `services().publish`; the version is 0.2.0, the export is still listed and the range is `>=0.18.0`).

- [ ] **Step 2: Remove the API**

- `EnterprisePlugin`: delete the import. The class javadoc becomes "The Enterprise look. Its kit file, fonts and theme are data the host reads from the descriptor before the first window; its start does nothing, so it cannot fail." and `start`'s becomes "Does nothing: the look is the descriptor's data."
- `package-info.java`: "The Enterprise plugin's entry point. The look itself is the descriptor's presentation, fonts and theme: data the host reads."
- `plugin.toml`: `version = "0.3.0"`, `sdk = ">=0.18.2, <0.19"`; delete the `exports` line.
- `build.gradle.kts:1`'s comment: "// The Enterprise look: its kit file, fonts and theme are data the host reads; its code does nothing."
- Delete `api/Workstation.java` and `api/package-info.java`.
- `gradle/plugin-architecture.gradle.kts`: delete `:13-15` (the comment and `workstationPackages`) and `:52-54` (the `dev.jasper.enterprise.api` check).
- AGENTS.md:
  - In the Remote sentence, delete " and the Enterprise API (named only in `dev.jasper.remote.workstation`)".
  - Replace the Enterprise sentences "Enterprise is bundled too … (`verifyPluginArchitecture`)." with: "Enterprise is bundled too (`plugins/enterprise`, off by default): its look is descriptor data (its kit file, the IBM Plex fonts and the Enterprise theme), and its code does nothing. Plugins read the active look through the SDK (`Appearance.presentation()`)."

Run: `./gradlew :jasper-plugin-enterprise:test verifyPluginArchitecture && ./gradlew :jasper-app:test --tests '*PresentationConformanceTest' --tests '*AppDocumentationTest' --tests '*ThemeMigrationCoverageTest' --tests '*PresentationAttemptTest'`
Expected: PASS. `git grep -n 'enterprise\.api\|Workstation\.class\|workstationPackages' -- ':!docs/superpowers' ':!docs/STATUS.md'` prints only `docs/plugin-authoring.md` lines, which Task 9 rewrites.

- [ ] **Step 3: Commit**

```bash
git add -A plugins/enterprise gradle/plugin-architecture.gradle.kts jasper-app AGENTS.md docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 4: remove the Enterprise API; Enterprise 0.3.0 is data

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 5: The kit file's plugin defaults

**Files:**
- Modify:
  - `jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java`
  - `presentation/KitParser.java:36-41,207,484-513`
  - `jasper-app/src/test/java/dev/jasper/app/config/DefaultSourceTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java`

**Interfaces:**
- Produces:
  - `public record DefaultSource(Map<Setting<?>, Object> overrides, Map<String, Map<String, Object>> plugins)`. The compact constructor deep-copies `plugins` (`Map.copyOf` of each table, with no null keys or values) and keeps every existing check on `overrides`.
  - `public DefaultSource(Map<Setting<?>, Object> overrides)` means no plugin defaults (Rulings 15).
  - `public Map<String, Object> plugin(String pluginId)`: that plugin's table of raw TOML values by dotted setting key, empty when the kit has none.
  - `CORE` is `new DefaultSource(Map.of(), Map.of())`.

- [ ] **Step 1: Write the failing tests**

`DefaultSourceTest.pluginTablesAreCopiedAndAbsentOnesAreEmpty`: a source built with `Map.of("dev.x.tool", Map.of("size", 12L))` gives `plugin("dev.x.tool")` equal to `Map.of("size", 12L)` and `plugin("dev.other")` empty. Mutating the caller's maps after construction changes nothing; build them as `HashMap`s to show it. `CORE.plugin("dev.x.tool")` is empty.

`KitParserTest`:

```java
@Test void aPluginsTableGivesItsSettingsDefaultsAsRawValues() throws Exception {
    var parsed = parse("kit = 1\nbase = \"default\"\n[defaults]\n\"palette.visible_rows\" = 8\n[defaults.\"dev.x.tool\"]\nfont_family = \"Mono\"\n"
        + "size = 12\nratio = 1.5\nwrap = true\nsftp.parallel = 3\nnames = [\"a\", \"b\"]\n");
    var table = parsed.defaults().plugin("dev.x.tool");
    assertThat(table).containsOnlyKeys("font_family", "size", "ratio", "wrap", "sftp.parallel", "names");
    assertThat(table.get("font_family")).isEqualTo("Mono");
    assertThat(table.get("size")).isEqualTo(12L);
    assertThat(table.get("ratio")).isEqualTo(1.5);
    assertThat(table.get("wrap")).isEqualTo(true);
    assertThat(table.get("sftp.parallel")).as("a nested key is the dotted setting key").isEqualTo(3L);
    assertThat(table.get("names")).isInstanceOf(org.tomlj.TomlArray.class);
    assertThat(parsed.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
}
```

In `everyInvalidKitGivesOneMessageNamingTheKey`, add to `cases`:

| Input (a whole kit; `head` is the test's `kit = 1` and `base` lines) | Message |
|---|---|
| `"[defaults.ui]\ntheme = 1\n"` (as a whole kit: `head + "[defaults.ui]\ntheme = 1\n"`) | `[defaults]."ui": a plugin's table is named by its id, such as "dev.jasper.editor"` |
| `head + "[defaults.\"jasper.core\"]\nx = 1\n"` | `[defaults]."jasper.core": not a plugin id` |
| `head + "[defaults.\"dev.x.tool\"]\n\"bad key\" = 1\n"` | `[defaults."dev.x.tool"]."bad key": not a setting key` |
| `head + "[defaults.\"dev.x.tool\"]\nwhen = 1979-05-27\n"` | `[defaults."dev.x.tool"].when: expected a string, number, boolean or array` |
| `head + "[defaults.\"dev.x.tool\"]\nhue = \"#FF0000\"\n"` | `[defaults."dev.x.tool"].hue: "#FF0000" looks like a colour; colours come from the theme` (the colour scan's message, through the extended `name(List)`) |

For the first two rows the message keeps `defaults()`'s own `[defaults]."<key>"` form (`KitParser.java:488`). For the last three, `KitParser.name(List)` (`:227-230`) names the path; it is extended (execution Ruling E5) to quote every segment that is not a TOML bare key, so it gives exactly the strings in the table.

Run: `./gradlew :jasper-app:test --tests '*DefaultSourceTest' --tests '*KitParserTest'`
Expected: FAIL (no `plugin` method, and a table value is "not a setting a look may default").

- [ ] **Step 2: Implement**

- `DefaultSource` as in Interfaces. Its javadoc gains: "{@code plugins} holds a look's defaults for plugins' declared settings (spec 5.8), raw TOML values that the plugin's runtime reads with each setting's own kind when the plugin declares it."
- In `KitParser.defaults`: an entry whose value is a `TomlTable` is a plugin table.
  - Check its id as in Rulings 15 (a key with no dot, then the id rule).
  - Walk it with an explicit stack, as `noColours` does, joining keys with `.`.
  - Refuse a key outside `[A-Za-z0-9_-]+(\.[A-Za-z0-9_-]+)*` and a value that is not `String`, `Long`, `Double`, `Boolean` or `TomlArray`.
  - Keep the value as tomlj gives it.
  - The `Defaults` record gains `Map<String, Map<String, Object>> plugins`, and `:207` passes it to the two-argument `DefaultSource`.
  - Every other entry keeps today's core handling.
- The class javadoc's "five setting defaults" becomes "five setting defaults, defaults for other plugins' settings".

Run: `./gradlew :jasper-app:test --tests '*DefaultSourceTest' --tests '*KitParserTest' --tests '*DefaultsPrecedenceTest' --tests '*PresentationConformanceTest'`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java \
        jasper-app/src/test/java/dev/jasper/app/config/DefaultSourceTest.java jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java
git commit -m "Plan 15a Task 5: a kit's [defaults.\"<plugin id>\"] tables

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 6: Plugins declare settings over the look's defaults, on both hosts

**Files:**
- Modify (host):
  - `plugins/PluginHost.java:98-100,153`
  - `plugins/PluginRuntime.java:683-688`
  - `plugins/DeclaredSettings.java:52-97`
  - `plugins/SettingTranslation.java:29-53`
  - `plugins/DeclaredSetting.java`
  - `plugins/KitSettingRows.java:30-67`
  - `plugins/PluginPanelPage.java:45-54`
- Modify (testkit):
  - `FakePluginHost.java`
  - `FakePluginSettings.java:36-48`
  - `FakeSetting.java:22-70`
  - `FakeSettingsPanelHost.java:60,67,198`
  - `KitSettingRows.java:51-52`
- Modify (contract and tests):
  - `ContractHarness.java`
  - `PluginContractTest.java`
  - `FakeContractTest.java`
  - `AppContractTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/DeclaredSettingsTest.java`
  - `PluginPanelPageTest.java`

**Interfaces:**
- Consumes: `DefaultSource.plugin(String)` (Task 5); `PluginRuntime.presentation(Kit, PluginLook, DefaultSource)` (Task 2).
- Produces:
  - `PluginHost`:
    - `volatile DefaultSource defaults = DefaultSource.CORE;`
    - `Consumer<String> lookDefaultProblem = message -> LOG.log(System.Logger.Level.WARNING, message);`
    - `PluginRuntime` sets `created.defaults = defaults` beside `created.presentation`, only when `presentation(...)` was called (Task 2).
  - `DeclaredSettings(String pluginId, PluginSettingsFiles files, Containment containment, Consumer<Runnable> ui, Consumer<ConfigDiagnostic> report, Runnable written, Map<String, Object> lookDefaults, Consumer<String> problems)`. `PluginHost.start` passes `defaults.plugin(id)` and `lookDefaultProblem`.
  - `static Translated SettingTranslation.translate(SettingSpec<?> spec, UnaryOperator<Predicate<String>> guard, Optional<Object> lookDefault, Consumer<String> problem)`:
    - when `lookDefault` is present, read it with the translated kind: `kind.read(raw, defaultValue)`;
    - with no issues, the value read is the app `Setting`'s default;
    - otherwise pass the reader's first issue message to `problem` (the caller adds the plugin id and key) and keep the spec's default.
  - `DeclaredSetting.defaultValue()` returns `T`, the effective default in SDK terms: `sdkValue(appSetting().defaultValue())`.
  - The host `KitSettingRows` gains a constructor parameter `Function<Setting<?>, Object> defaults` (the effective default in SDK terms), used at `:67` instead of `spec.defaultValue()`. `PluginPanelPage` passes `setting -> declared.handle(setting).defaultValue()`.
  - Exact problem texts:
    - a rejected value: `<plugin id>: the look's default for <key> is not used: <reading issue without its "; using …" tail>`. Strip the tail with `replaceFirst("; using [^;]*\\.$", ".")`, as the kit parser does;
    - an undeclared key: `<plugin id>: the look gives a default for <key>, which the plugin does not declare`. It is reported once per key, in `complete()`, in the table's key order sorted alphabetically.
  - Testkit:
    - `public void FakePluginHost.setKitDefault(String pluginId, String key, Object rawValue)` takes the fake's raw types: `Long`, `Double`, `String`, `Boolean`, `List<String>`. It throws `IllegalStateException("Plugins have already started")` after a start;
    - `public List<String> FakePluginHost.lookDefaultProblems(String pluginId)`.
    - `FakeSetting` takes the effective default at construction (the kit value when `convert` and `check` accept it, else the spec's, with the problem added). It exposes `T defaultValue()` and uses it at `:26,54,63`.
    - `FakeSettingsPanelHost` and the testkit's `KitSettingRows` use `defaultValue()` in place of `spec().defaultValue()`.
    - The fake reports undeclared keys when its declarations close, with the same texts. Its reading issue may word the reason differently; only the prefix is contract.
  - `ContractHarness`: `void setKitDefault(String pluginId, String key, Object rawValue);` and `List<String> lookDefaultProblems(String pluginId);`.
    - The app harness parses `"v = " + literal(rawValue)` with tomlj, the file's `literal` helper, to get the raw value.
    - It adds the value to a `DefaultSource` it assigns to `host.defaults` before the plugin starts, keeping earlier entries.
    - It sets `host.lookDefaultProblem` once to a list it reads back.

- [ ] **Step 1: Write the failing contract cases**

In `PluginContractTest`, near the declared-settings cases (`:2125`):

```java
/** Plan 15a (spec 5.8): saved, then the look's, then the plugin's own default. */
@Test void aKitDefaultReplacesTheDeclaredDefaultAndASavedValueWins() {
    h.setKitDefault("test.kitdefault", "size", 15L);
    var size = new AtomicReference<Setting<Integer>>();
    h.start(new PluginInfo("test.kitdefault", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context ->
        size.set(context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size"))));
    assertThat(size.get().get()).isEqualTo(15);
    h.setSetting("test.kitdefault", "size", 13L);
    assertThat(size.get().get()).as("a saved value equal to the plugin's own default still wins").isEqualTo(13);
    assertThat(h.lookDefaultProblems("test.kitdefault")).isEmpty();
}

@Test void aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported() {
    h.setKitDefault("test.kitbad", "size", 99L);
    h.setKitDefault("test.kitbad", "mode", "sideways");
    var size = new AtomicReference<Setting<Integer>>(); var mode = new AtomicReference<Setting<String>>();
    h.start(new PluginInfo("test.kitbad", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context -> {
        size.set(context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
        mode.set(context.settings().declare(SettingSpec.of("mode", SettingKind.choice(List.of(Choice.of("up", "Up", "up"), Choice.of("down", "Down", "down"))), "up").label("Mode")));
    });
    assertThat(h.active("test.kitbad")).isTrue();
    assertThat(size.get().get()).isEqualTo(13);
    assertThat(mode.get().get()).isEqualTo("up");
    assertThat(h.lookDefaultProblems("test.kitbad")).hasSize(2).allSatisfy(line -> assertThat(line).startsWith("test.kitbad: the look's default for "))
        .anySatisfy(line -> assertThat(line).startsWith("test.kitbad: the look's default for size is not used: "));
}

@Test void aKitDefaultForAnUndeclaredKeyIsReportedAndAnotherPluginsTableIsIgnored() {
    h.setKitDefault("test.kitextra", "missing", true);
    h.setKitDefault("test.notrunning", "size", 9L);
    h.start(new PluginInfo("test.kitextra", "Kit", "1.0.0", Set.of()), Set.of(), Set.of(), context ->
        context.settings().declare(SettingSpec.of("size", SettingKind.integer(8, 48), 13).label("Size")));
    assertThat(h.lookDefaultProblems("test.kitextra"))
        .containsExactly("test.kitextra: the look gives a default for missing, which the plugin does not declare");
    assertThat(h.lookDefaultProblems("test.notrunning")).isEmpty();
}
```

`Choice.of(token, displayName, value)` and `SettingKind.choice(List)` are the SDK's (`Choice.java:30`, `SettingKind.java:73`).

Run: `./gradlew :jasper-sdk-testkit:test --tests '*FakeContractTest'`
Expected: FAIL to compile (`setKitDefault` does not exist).

- [ ] **Step 2: Implement the testkit side**

Implement as in Interfaces.

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: PASS (the fake's own tests included).

- [ ] **Step 3: Write the failing host tests**

`DeclaredSettingsTest`. Set `host.defaults` and a collecting `host.lookDefaultProblem` before `start`.
- `aLooksKitDefaultReplacesTheDeclaredDefaultAndASavedValueStillWins`: a kit `font_family = "IBM Plex Mono"` with `SettingKind.text()` reads it. A file holding `font_family = "Menlo"` reads Menlo.
- `aKitDefaultTheKindRejectsLeavesThePluginsDefaultAndIsReported`: `size = "big"` for `integer(8, 48)` reads 13, and the problem starts `test.declared: the look's default for size is not used: `. A text rule that refuses the value behaves the same, and its rule runs contained.
- `aKitKeyThePluginNeverDeclaresIsReportedOnce`: after `complete()` and two `reload()`s, the list holds the one line.
- `aStringListKitDefaultIsReadAsTheFileWouldBe`: `names = ["a", "b"]` (a `TomlArray` from a parsed kit snippet) for `SettingKind.stringList()` reads `List.of("a", "b")`.

`PluginPanelPageTest.stagingThePluginsOwnDefaultUnderAKitDefaultSavesItAndResetReturnsToTheKits`, under a kit default of 15 for an integer setting whose own default is 13:
- staging 13 through `SettingsPanelHost.stage` makes the draft save an explicit `13`;
- the row is resettable;
- the row's Reset stages `Optional.empty()`;
- after Apply, the setting reads 15;
- the row is no longer resettable.

Drive it as the file's existing page tests do.

Run: `./gradlew :jasper-app:test --tests '*DeclaredSettingsTest' --tests '*PluginPanelPageTest'`
Expected: FAIL to compile.

- [ ] **Step 4: Implement the host side**

Implement as in Interfaces. `DeclaredSettings.declare` passes `Optional.ofNullable(lookDefaults.get(spec.key()))` and a problem consumer that prefixes `pluginId + ": "`. `complete()` reports undeclared keys before `reload(true)`.

Run: `./gradlew :jasper-app:test --tests '*DeclaredSettingsTest' --tests '*PluginPanelPageTest' --tests '*AppContractTest' --tests '*PluginSettingsPageTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add -A jasper-app jasper-sdk-testkit docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 6: the look's defaults for plugin settings, on both hosts

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 7: Enterprise defaults the Editor's font

**Files:**
- Modify:
  - `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml:68-73`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java:24-50,69-87`
  - `plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/plugins/EnterpriseEditorDefaultTest.java`

**Interfaces:**
- Consumes: Tasks 5 and 6.

- [ ] **Step 1: Write the failing tests**

- `PresentationConformanceTest`: add `"defaults/dev.jasper.editor/font_family"` to `KEYS`. In `theKitListsEveryKeyAndParsesWithoutAProblem` add `assertThat(parsed.defaults().plugin("dev.jasper.editor")).as("the Editor in the data face (plan 15a)").containsExactly(java.util.Map.entry("font_family", "IBM Plex Mono"));`.
- `EnterpriseEditorDefaultTest.underEnterpriseTheEditorsFontIsPlexMono`, modelled on `DeclaredSettingsTest`'s `host(...)` set-up:
  - `host.defaults = EnterpriseResources.facts().parsed().orElseThrow().defaults()`;
  - start a probe with id `dev.jasper.editor` that declares `SettingSpec.of("font_family", SettingKind.text(), "Monospaced").label("Font")`;
  - assert it reads `IBM Plex Mono` and that the problem list is empty.

  This proves the table reaches the Editor's key.
- `EditorSettingsTest.aLooksFontFamilyIsTheDefaultOnlyWhenItIsInstalled`, with the file's `start(host, installed)` helper and the real `EditorSettings.Declared`. The Editor accepts a family only if it is installed (`EditorSettings.java:61-62`):
  - a `FakePluginHost` with `setKitDefault("dev.jasper.editor", "font_family", "IBM Plex Mono")` and installed `Set.of("IBM Plex Mono", "Menlo")` reads `IBM Plex Mono` from `current().fontFamily()`, and `lookDefaultProblems(ID)` is empty;
  - a second host with the same kit default and installed `Set.of("Menlo")` reads the fallback `Menlo`, and `lookDefaultProblems(ID)` holds one line starting `dev.jasper.editor: the look's default for font_family is not used: `.

  This is a test-only change in the Editor: its code and manifest stay as they are (Rulings 14).

Run: `./gradlew :jasper-app:test --tests '*PresentationConformanceTest' --tests '*EnterpriseEditorDefaultTest' && ./gradlew :jasper-plugin-editor:test --tests '*EditorSettingsTest'`
Expected: the two jasper-app tests FAIL (the kit has no such table). `EditorSettingsTest` PASSES, since Task 6 made the fake read kit defaults; keep it as the proof of the Editor's rule.

- [ ] **Step 2: Add the table**

After `"window.toolbar" = "icons"` in `enterprise.kit.toml`, add:

```toml

[defaults."dev.jasper.editor"]
font_family = "IBM Plex Mono"
```

Add the new test's `migration.csv` row if it is reported.

Run: `./gradlew :jasper-app:test --tests '*PresentationConformanceTest' --tests '*EnterpriseEditorDefaultTest' --tests '*EnterpriseFixtureTest' --tests '*EnterpriseFirstWindowTest' --tests '*EnterpriseHomeTest' --tests '*ThemeMigrationCoverageTest' && ./gradlew :jasper-plugin-editor:test`
Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml plugins/editor/src/test/java/dev/jasper/editor/ui/EditorSettingsTest.java jasper-app/src/test docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 7: Enterprise's kit sets the Editor's font to IBM Plex Mono

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 8: The Sample plugin shows the query and a look default

**Files:**
- Modify:
  - `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java:66-67,109-113`
  - `plugins/sample/src/main/resources/plugin.toml:3,5`
  - `plugins/sample/src/test/java/dev/jasper/sample/SamplePluginTest.java`
  - `jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java:80`
  - `docs/plugin-authoring.md`: the `example:plugin` and `example:pluginpanels` blocks, and a new `example:pluginlook` block

**Interfaces:**
- Consumes: `Appearance.presentation()`, `FakePresentations.CLASSIC`, `FakePluginHost.setKitDefault` and `lookDefaultProblems`.

- [ ] **Step 1: Write the failing tests**

In `SamplePluginTest`:

```java
@Test void underLabelledStripesTheSamplePanelStartsInTheBottomStrip() throws Exception {
    javax.swing.SwingUtilities.invokeAndWait(() -> { try (var host = new FakePluginHost()) {
        host.setPresentation(FakePresentations.CLASSIC);
        host.setSetting("dev.jasper.sample", "demo_ui", true);
        start(host, INFO, Set.of(), Set.of(), new SamplePlugin());
        assertThat(host.panels()).containsExactly("dev.jasper.sample.panel|Sample|BOTTOM");
    } });
}

@Test void aLooksKitDefaultSetsTheStepDelayAndASavedValueStillWins() {
    try (var host = new FakePluginHost()) {
        host.setKitDefault("dev.jasper.sample", "demo_step_millis", 150L);
        var settings = new java.util.concurrent.atomic.AtomicReference<SampleSettings>();
        start(host, INFO, Set.of(), Set.of(), context -> settings.set(new SampleSettings(context.settings())));
        assertThat(settings.get().stepMillis.get()).isEqualTo(150);
        host.setSetting("dev.jasper.sample", "demo_step_millis", 40L);
        assertThat(settings.get().stepMillis.get()).isEqualTo(40);
        assertThat(host.lookDefaultProblems("dev.jasper.sample")).isEmpty();
    }
}
```

`theDemoUiAddsAPanelARailButtonAndAWindow` keeps `|LEFT`: the default fake is `STANDARD`. `BundledSamplePluginTest:80` expects `"0.4.0"`.

Run: `./gradlew :jasper-plugin-sample:test`
Expected: `underLabelledStripes…` FAILS (`|LEFT`); the kit-default case PASSES, since Task 6 made it work. Keep it as the Sample's demonstration.

- [ ] **Step 2: Use the query in the Sample**

- In `installPanelAndWindow`, `new PanelSpec("dev.jasper.sample.panel", "Sample", FLASK, Anchor.LEFT)` becomes `new PanelSpec("dev.jasper.sample.panel", "Sample", FLASK, panelAnchor(context))`.
- After `installPanelAndWindow`'s `// example:pluginpanels:end`, add (exact text; the guide shows it):

  ```java
      // example:pluginlook:start
      /** IntelliJ's classic layout keeps a tool window like this one in the bottom strip: under labelled stripes it starts there. */
      private static Anchor panelAnchor(PluginContext context) {
          var look = context.appearance().presentation();
          return look.toolWindows() == Presentation.ToolWindows.STRIPES ? Anchor.BOTTOM : Anchor.LEFT;
      }
      // example:pluginlook:end
  ```

- Import `dev.jasper.sdk.ui.Presentation`.
- In `start`, `"The look is now " + event.variant()` becomes `"The theme is now " + event.variant()` (Rulings 25).
- `plugin.toml`: `version = "0.4.0"`, `sdk = ">=0.18.2, <0.19"`.

- [ ] **Step 3: Update the guide's compiled examples**

In `docs/plugin-authoring.md`:
- replace the `example:plugin` block's log line and the `example:pluginpanels` block's `PanelSpec` line with the new code, character for character;
- after the `example:pluginpanels` block's closing fence and the paragraph that follows it, add:

  ````markdown
  A plugin that composes differently under a classic look reads `context.appearance().presentation()` once in `start`
  (SDK 0.18.2): the Sample opens its panel in the bottom strip under labelled stripes.

  <!-- example:pluginlook -->
  ```java
  /** IntelliJ's classic layout keeps a tool window like this one in the bottom strip: under labelled stripes it starts there. */
  private static Anchor panelAnchor(PluginContext context) {
      var look = context.appearance().presentation();
      return look.toolWindows() == Presentation.ToolWindows.STRIPES ? Anchor.BOTTOM : Anchor.LEFT;
  }
  ```
  ````

Run: `./gradlew :jasper-plugin-sample:test && ./gradlew :jasper-app:test --tests '*AppDocumentationTest' --tests '*BundledSamplePluginTest' --tests '*ThemeMigrationCoverageTest'`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add plugins/sample jasper-app/src/test/java/dev/jasper/app/plugins/BundledSamplePluginTest.java docs/plugin-authoring.md docs/design/theme-engine/migration.csv
git commit -m "Plan 15a Task 8: the Sample reads the presentation and shows a look default (0.4.0)

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

### Task 9: Documentation, STATUS and the whole build

**Files:**
- Modify:
  - `docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`
  - `docs/superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md:4`
  - `docs/plugin-authoring.md:64-71,644-675,736`
  - `docs/sdk-architecture.md:429-433,462-472`
  - `docs/plugin-ui.md:233-238`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/Variant.java:3`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/events/AppEvents.java:8,16`
  - `jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java:119-121`
  - `docs/STATUS.md`
  - this plan's status banner

- [ ] **Step 1: Amend the spec** (smallest accurate edits; quote the old text and re-wrap at 120 columns with the paragraph's indent)

- **Header:** after the plan 14 line (`:22`), add one line:

  > Amended on 2026-10-19 at the user's direction (plan 15): 1 (decisions 1, 4 and 19), 3 (principle 6), 4.1, 4.6, 4.7, 5.1, 5.8, 5.9, 8.4, 8.7, 9.1, 14.1 to 14.3, 15, 16.1, 17, 18 and 19.1. Plugins read the active presentation through the SDK (`Appearance.presentation()`, SDK 0.18.2), so any classic look drives Remote's, File Server's and Vault's classic compositions, and the Enterprise API is removed; a kit may default other plugins' settings (`[defaults."<plugin id>"]`), and Enterprise's sets the Editor's font to IBM Plex Mono.
- **Decision 1 (`:60`):** delete ", and exports `dev.jasper.enterprise.api`".
- **Decision 4 (`:65-66`):** "discovering Enterprise through its exported API (a presence-only `Workstation` service and an optional requirement)" becomes "reading the active presentation through the SDK (`Appearance.presentation()`; since 2026-10-19, plan 15)".
- **Decision 19 (`:101`):** "Vault consumes `Workstation` for a classic manager (14.8)" becomes "Vault composes a classic manager under a classic look (14.8)".
- **Principle 6 (`:268`):** "plugins on whether `Workstation` is published" becomes "plugins on whether the SDK's presentation is classic".
- **4.1:** in the descriptor block, `version = "0.2.0"` becomes `"0.3.0"` and `sdk = ">=0.18.0, <0.19"` (`:288`) becomes `">=0.18.2, <0.19"`. The `description` becomes the shipped one (`plugins/enterprise/src/main/resources/plugin.toml:6`). Delete the `exports` line.
- **4.6 (`:421-423`):** "In that last case the current session keeps the classic chrome with no `Workstation`: Remote and File Server compose their default layouts, which is a reachable, tested state. `EnterprisePlugin.start` only publishes `Workstation`, and a test pins that it cannot throw." becomes "In that last case the current session keeps the classic chrome, and plugins read its classic presentation. `EnterprisePlugin.start` does nothing, and a test pins that it cannot throw."
- **4.7 (`:445`):** "so no `Workstation` is published" becomes "so plugins read the fallback's presentation".
- **5.1:** in the listing, after `"window.toolbar" = "icons"` add an empty line, `[defaults."dev.jasper.editor"]` and `font_family = "IBM Plex Mono"`. In "the tables `chrome`, … and `defaults`", append to the sentence: "; `defaults` may also hold one table per plugin (5.8)".
- **5.8:** append the paragraph:

  > **Plugin settings** (the user, 2026-10-19; plan 15). A kit may default any plugin's declared settings in a table `[defaults."<plugin id>"]`, keyed as that plugin's settings file writes its keys (`sftp.max_parallel_files = 4` nests, as in the file). The parser checks only the shape: an id with at least one dot outside the reserved `jasper` namespace, setting keys, and string, integer, float, boolean or array values. The plugin's runtime reads each value with the setting's own kind when the plugin declares it (`DeclaredSettings`), so the precedence is user-saved, then the kit's, then the plugin's own default, and Reset to Default returns to the kit's. A value the kind rejects and a key the plugin does not declare are logged once and ignored, and the plugin's own default applies; a table for a plugin that is not running is ignored. Enterprise's kit sets the Editor's `font_family` to IBM Plex Mono, its data face. The fallback (4.7) keeps them, as it keeps the core defaults.
- **5.9:** after "- a `[defaults]` key outside the allowlist, or a value its setting rejects;" add "- a `[defaults]` table not named by a plugin id with a dot, a key in it that is not a setting key, or a value in it that is not a string, number, boolean or array;".
- **8.4 (`:1111-1112`):** the text breaks at the hyphen ("Tabs nodes, an active-" then "presentation query, `SettingSpec.dependsOn`"); delete "an active-presentation query, " across that break and re-wrap.
- **8.7:** add after 8.6:

  > ### 8.7 SDK 0.18.2
  >
  > An additive release (the user, 2026-10-19; plan 15): `Appearance.presentation()` returns `dev.jasper.sdk.ui.Presentation`, a record of the look Jasper runs with: `classic` (the classic base), `icons` (`NEW_UI`, `CLASSIC`), `toolWindows` (`RAILS`, `STRIPES`), `titleBar` (`UNIFIED`, `NATIVE`, `CLASSIC`, resolved for the platform), `menus` (`SCREEN_MENU_BAR`, `TITLE_BAR`, `WINDOW`), `toolbarSearch` (`FIELD`, `ICON`), the look's `uiFace` and `dataFace` (a regular face's name, empty for the platform's), its `bodySize` and its default `toolbarHeight`. It is IntelliJ's `ExperimentalUI.isNewUI()` and `JBUI.CurrentTheme` for Jasper: chosen at start, the same for every plugin and safe from any thread; values a user setting changes are reported as the look's. The host builds it in `dev.jasper.app.plugins.HostedPresentation` from the `PluginLook` projection (9.1), the kit and the look's defaults; the testkit's `FakePluginHost.setPresentation` and `FakePresentations` mirror it. Remote, File Server, Vault and Sample declare `sdk = ">=0.18.2, <0.19"`, and so does Enterprise: its kit's `[defaults."<plugin id>"]` table (5.8) needs a 0.18.2 host, and an older one would refuse the kit (5.9).
- **9.1:** in the projections table, add the row `` | `PluginLook` | `classic`, `classicIcons`, `stripes`, `titleBar`, `menus`, `search` | plugins: `HostedPresentation` (the SDK's `Presentation`, 8.7) | ``. In the field table add `` | `PluginLook` | `classic` when `base = "classic"`; `classicIcons` and `stripes` with `icons = "classic"` and `tool_windows = "stripes"`; `titleBar` the title as resolved for the platform; `menus` the screen menu bar on macOS while the committed flags keep it, else in the window under a native title and in the title bar otherwise; `search` FIELD | the default look's under the committed flags | ``. In the sentence listing single readers, add "`PluginLook`'s enums only by `HostedPresentation`".
- **14.1:** "`dev.jasper.enterprise.EnterprisePlugin`, whose `start()` only calls `context.services().publish(Workstation.class, new Workstation() { })`;" becomes "`dev.jasper.enterprise.EnterprisePlugin`, whose `start()` does nothing;". Delete the bullet "- `dev.jasper.enterprise.api.Workstation` and its `package-info`.". Delete "and imports no plugin API, and a gate check keeps it so: cycle detection counts optional edges" up to the sentence's end, so it reads "The plugin has no requirements."
- **14.2:** retitle it "### 14.2 The presentation query" and replace its body with:

  > Plugins learn the look from the SDK (8.7), not from a plugin's API: `Appearance.presentation().classic()` is the equivalent of IntelliJ's `ExperimentalUI.isNewUI()` being false. The toolbar control is generic SDK (`ToolbarItem.View`), the menus are standard menus, and anchors and compositions belong to their owners.
- **14.3:** replace the bullet list with: "Remote, File Server and Vault read `Layout.of(context.appearance().presentation())` once in `start`: `Layout.CLASSIC` under a classic look, else `Layout.ROWS`. Each `Layout` lives in the plugin's `workstation` package. Tests set the testkit's `FakePresentations.CLASSIC`."
- **15 (`:1732`):** "this session keeps the classic chrome without `Workstation`" becomes "this session keeps the classic chrome and its presentation".
- **16.1:**
  - In item 7, delete ", the Enterprise confinement check (14.3)".
  - In item 8, after the `DefaultsPrecedenceTest` bullet add "- `DeclaredSettingsTest` and the contract: a kit's plugin default replaces the declared default, a saved value wins, a rejected value or an undeclared key is reported and ignored, and Reset returns to the kit's.".
  - In item 9, after "`presentation.provide`;" add " the presentation (8.7); the look's plugin defaults;".
- **17:** in the `plugin-authoring.md` bullet, "and consuming `Workstation`" becomes "the presentation query and the look's plugin defaults". In the `AGENTS.md` bullet, delete " and the confinement of `dev.jasper.enterprise.api`".
- **18 (`:1875`):** delete "an SDK active-presentation query or traits; " and re-wrap.
- **19.1:**
  - "Fourteen plans" becomes "Fifteen plans", and "1 to 14 in sequence" becomes "1 to 15 in sequence".
  - Row 5: "`dev.jasper.enterprise.api.Workstation` and its publication in `EnterprisePlugin.start`" becomes "`EnterprisePlugin`".
  - Row 10: "`Workstation` consumption" becomes "the classic layout".
  - Add the row:

    > | 15 | `look-query` | The user's 2026-10-19 direction, in two plans: 15a `look-query` (SDK 0.18.2's `Appearance.presentation()` and `PluginLook`, the Enterprise API removed, kit defaults for plugin settings, Enterprise 0.3.0 with the Editor in IBM Plex Mono, the consumers' and Sample's versions); 15b `look-polish` (activation by a panel's own toggle action, classic icons in tool-window headers and toolbar views, the toolbar search icon, the identity guard) |

Check:

```bash
spec=docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md
grep -n 'enterprise\.api\|Workstation()\|Workstation\.class\|interface Workstation\|Fourteen plans\|an active-presentation query' "$spec"   # prints nothing
python3 -I - "$spec" <<'PY'
import re, sys
text = re.sub(r"\s+", " ", open(sys.argv[1], encoding="utf-8").read())
for phrase in ("SDK active-presentation query", "an active- presentation query", "no `Workstation`", "consumes `Workstation`"):
    print(text.count(phrase), "(want 0)", phrase)
for phrase in ("Amended on 2026-10-19 at the user's direction (plan 15)", "### 8.7 SDK 0.18.2", "**Plugin settings** (the user, 2026-10-19; plan 15)",
               "### 14.2 The presentation query", "`PluginLook`", "Fifteen plans", "| 15 | `look-query` |", '[defaults."dev.jasper.editor"]'):
    print(text.count(phrase), "(want at least 1)", phrase)
PY
```

Expected: no `grep` output, four `0` lines, and every other count at least `1`.

In the plugin SDK design, after the status line (`:4`), add this line (fenced here because a relative link in a plan fails `AppDocumentationTest`):

```markdown
**Amended 2026-10-19** by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md) (plan 15): `Appearance.presentation()` (SDK 0.18.2) reports the active presentation; everything else in this document stands.
```

- [ ] **Step 2: The guides and the stale javadoc**

- `docs/plugin-authoring.md`:
  - **Replace `:64-71`** ("The bundled Enterprise plugin … which `verifyPluginArchitecture` checks.") with:

    > The bundled Enterprise plugin (`plugins/enterprise`, off by default) is a worked presentation provider: its look is the kit file, IBM Plex fonts and theme its descriptor names, and its code does nothing. A kit's `[defaults]` may also hold one table per plugin, `[defaults."<plugin id>"]`, keyed as that plugin's settings file writes its keys: each value becomes the setting's default when the plugin declares it, a saved value still wins, and Reset returns to it. A value the setting's kind rejects, or a key the plugin does not declare, is logged and ignored. Enterprise's gives the Editor IBM Plex Mono.
    >
    > Any plugin can ask which look is running: `context.appearance().presentation()` (SDK 0.18.2) says whether it is classic, its icon set, how tool windows are reached, the title bar and where the menus are, how the toolbar offers Search Everywhere, its UI and data faces, body size and default toolbar height. Branch on it once in `start` (Remote, File Server and Vault compose their classic layouts when `classic()` is true), and test both with `FakePluginHost.setPresentation(FakePresentations.CLASSIC)`. Declare `sdk = ">=0.18.2, <0.19"`.
  - **"Declared settings" (`:657-659`):** after "Declaring after `start` returns throws." insert "Under a look whose kit defaults the key, the default is the look's (see above); `FakePluginHost.setKitDefault` and `lookDefaultProblems` test it."
  - **`:736`:** `dev.jasper.snippets-0.1.0.zip` becomes `dev.jasper.snippets-0.3.0.zip`.
- `docs/sdk-architecture.md`:
  - After the `Appearance.editorPalette()` paragraph (`:429-433`), add: "SDK 0.18.2 adds `Appearance.presentation()`: `HostedPresentation` builds one `Presentation` per run from the `PluginLook` projection, the installed kit and the look's `DefaultSource`, and `PluginRuntime.presentation(Kit, PluginLook, DefaultSource)` hands all three to the host before plugins start. It is an immutable value read from any thread; `FakePluginHost.setPresentation` mirrors it, and the contract case `presentationIsTheHostsForEveryPluginOnAnyThreadAndThroughThemeChanges` runs on both."
  - In "Declared settings" (`:462-472`), after "reports rejected values and undeclared keys against the file." add: "A look's `[defaults."<plugin id>"]` values become each declared setting's default, read with the setting's kind when it is declared; a rejected value and an undeclared key are logged (`PluginHost.lookDefaultProblem`) and ignored, and `FakePluginHost.setKitDefault` and `lookDefaultProblems` mirror it."
- `docs/plugin-ui.md`, the "Colors for specialized content" paragraph (`:233-238`): append "Specialized content that needs the look's faces reads them from `appearance().presentation()` (`uiFace`, `dataFace`, `bodySize`; SDK 0.18.2)."
- `Variant.java:3`: "/** The application's current variant, dark or light: plugins pick colors and icons that suit it. */".
- `AppEvents.java:8`: "/** The theme changed; read {@link ThemeChanged#variant()}. */". `:16`: " * The application's theme changed.".
- `PluginContext.java:120`: " * The application's colours, variant, presentation and their change notifications.".

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest' --tests '*VersionRangeTest' && ./gradlew :jasper-sdk:javadoc`
Expected: PASS.

- [ ] **Step 3: Prove the default look unchanged**

```bash
base=/private/tmp/claude-501/-Users-RQ7RQVF-projects-moray/819d407c-0391-424d-8db0-7177b2d3e0e0/scratchpad/plan15-baseline
out=$(mktemp -d)
ls "$base/gallery-dark-1x" "$base/gallery-light-1x" "$base/app" > /dev/null || echo "NO BASELINE"
rm -rf jasper-ui/build/gallery
./gradlew :jasper-ui:gallery --rerun && cp -R jasper-ui/build/gallery/dark-1x "$out/gallery-dark-1x"
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light --rerun && cp -R jasper-ui/build/gallery/light-1x "$out/gallery-light-1x"
for theme in dark light; do diff -r "$base/gallery-$theme-1x" "$out/gallery-$theme-1x" && echo "$theme: identical ($(ls "$out/gallery-$theme-1x" | wc -l) files)"; done
./gradlew :jasper-app:test --tests '*ChromeRenderTest' --tests '*DefaultDockPixelsTest' --rerun
mkdir -p "$out/app"
for f in jasper-app/build/chrome-renders/chrome-default*.png jasper-app/build/chrome-renders/settings-default*.png \
         jasper-app/build/chrome-renders/chrome-complement*.png jasper-app/build/chrome-renders/settings-complement*.png \
         jasper-app/build/chrome-renders/chrome-fallback*.png jasper-app/build/chrome-renders/settings-fallback*.png \
         jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png; do cp "$f" "$out/app/"; done
diff -r "$base/app" "$out/app" && echo "jasper-app default look: identical ($(ls "$out/app" | wc -l) files)"
echo "$out"
```

Expected: `dark: identical (161 files)`, `light: identical (161 files)` and `jasper-app default look: identical (11 files)`, with no `diff` output. If the baseline is missing, make one from `f78bb0385` in a detached worktree (`git worktree add --detach "$TMPDIR/plan15-main" f78bb0385`), run Step 0's commands there with `./gradlew -p`, compare, then `git worktree remove`. Never compare against renders made after Task 1.

- [ ] **Step 4: Run the whole build and count**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Count from the XML:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
t = f = e = s = 0
for p in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    r = ET.parse(p).getroot(); t += int(r.get("tests")); f += int(r.get("failures")); e += int(r.get("errors")); s += int(r.get("skipped"))
print(f"{t} tests, {f} failures, {e} errors, {s} skipped")
PY
```

Expected: 0 failures and 0 errors. Record the counts; the base is 11,541 tests and 25 skipped.

- [ ] **Step 5: Render the Enterprise gallery and check the pairs**

```bash
for theme in light dark; do ./gradlew :jasper-ui:gallery -Pjasper.galleryKit=enterprise -Pjasper.galleryTheme=$theme -Pjasper.galleryScale=2; done
python3 -I tools/visual/check_pairs.py "$(mktemp -d)" > "$out/pairs.txt"; echo "exit $?"
diff <(sed 's/ agreement=.*//' "$base/pairs.txt") <(sed 's/ agreement=.*//' "$out/pairs.txt") && echo "same verdicts"
diff "$base/pairs.txt" "$out/pairs.txt" && echo "same agreements"   # or only the lines Task 2 Step 4 recorded
```

Expected: `exit 0`, `same verdicts` (101 of 101 PASS) and `same agreements`, except for pair lines whose crops hold an Enterprise Settings render that Task 2 Step 4 recorded as changed (a plugin page now composing classic under Enterprise). Each such line may differ in its agreement; list it in the STATUS entry as the user's 2026-10-19 direction, with both values. Every other line must be identical; if one differs, find the cause before going on.

- [ ] **Step 6: Write `docs/STATUS.md`'s new top entry, correct the two stale lines, and write this plan's banner**

- At `:99` and `:281`, "Not merged, not pushed." becomes "Merged to local `main` (`f78bb0385`), not pushed." (Rulings 24.)
- The new entry goes above plan 14's and starts as below. The template stays fenced here, because inline links in a plan's template fail `AppDocumentationTest`.

```markdown
**Enterprise presentation, plan 15a: the look query at the user's direction — 2026-10-19** (`claude/enterprise-look-query`, worktree `.claude/worktrees/enterprise-15`, from `main` at `f78bb0385`; [plan](superpowers/plans/2026-10-19-enterprise-15a-look-query.md), [spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md))
```

Then, in the series' style, give:
- the request and the approval, quoted;
- what they became: SDK 0.18.2's `Appearance.presentation()` and its facts; `PluginLook` and `HostedPresentation`; the Enterprise API removed (Enterprise 0.3.0, no exports, `start` does nothing); Enterprise, Remote 0.15.0, Vault 0.7.0, File Server 0.6.0 and Sample 0.4.0 at `>=0.18.2`; `[defaults."<plugin id>"]` with its precedence, warnings and Reset; Enterprise's Editor default; the Sample's bottom-strip panel and step-delay demonstration; the testkit's `setPresentation`, `FakePresentations`, `setKitDefault` and `lookDefaultProblems`;
- the rulings (this plan's 27, one clause each);
- any execution rulings;
- any Enterprise render that changed in Task 2 Step 4, as the user's 2026-10-19 direction;
- the check counts, "101 of 101" pairs with the same agreements, the byte-identical gallery and the 11 identical default-look renders;
- what is left for plan 15b, and for later: the splash's fixed colours and "Workstation…" step text for other branded looks; the external THD plugin's port to SDK 0.18;
- **GUI checks for the user**, in this order:
  1. Under Enterprise, the Editor opens files in IBM Plex Mono, and Settings › Editor shows "IBM Plex Mono" with Reset to Default hidden. Choosing another font shows Reset, and Reset returns to IBM Plex Mono.
  2. With Enterprise off, the Editor keeps its own default font.
  3. Under Enterprise, Remote's Sessions on the right, Transfers in the bottom strip, File Servers' table and the Vault manager's classic layout, as before.
  4. With Enterprise off, all three show their rows.
  5. With `demo_ui = true`, the Sample panel starts in the bottom strip under Enterprise and on the left without it.
  6. Settings › Plugins shows Enterprise 0.3.0, Remote 0.15.0, Vault 0.7.0, File Server 0.6.0 and Sample 0.4.0.
- the merge state: "Not merged, not pushed."

Then this plan's status banner, under the title: "> **Status (2026-10-19): executed on `claude/enterprise-look-query` (`<first>..<last>`, then the Task 9 documentation commit).** Deviations from this plan's text: …" (none, or each one).

Run: `./gradlew :jasper-app:test --tests '*AppDocumentationTest'`
Expected: PASS.

- [ ] **Step 7: Commit**

```bash
git add docs AGENTS.md jasper-sdk/src/main/java/dev/jasper/sdk/Variant.java jasper-sdk/src/main/java/dev/jasper/sdk/events/AppEvents.java \
        jasper-sdk/src/main/java/dev/jasper/sdk/plugin/PluginContext.java
git commit -m "Docs: Enterprise plan 15a, the look query, at the user's direction

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>"
```

---

## Execution notes (2026-10-19)

Refresh before execution, on `claude/enterprise-look-query` at `95c60c27a`. Local `main` is still `f78bb0385`: nothing has merged since plans 15a and 15b were written, and the branch differs from it only by the two plan files. The refresh checks print `1`, `1`, `3` and `0`. Every fact, path, quoted text and signature above was re-read against the worktree. The few that moved are corrected in place:
- `Appearance.java:11-77`, `PluginRuntime.java:389-393`, `DeclaredSettings.java:59-73`, `PluginPanelPage.java:185-188` and `:192-199`;
- `Projections.of(` has 44 call sites in 12 files;
- the extra `"dev.jasper.enterprise"` optional sets of fact 20;
- `SamplePlugin.java:66-67,109-113`, and the spec's plan 14 header line at `:22`;
- spec 8.4's hyphen-broken "active-presentation query", and the Task 5 table header.

The rulings below refine the steps; where one of them and a step's text differ, the ruling wins.

### Tasks, in order

1. SDK 0.18.2: `Presentation`, `Appearance.presentation()`, the testkit and the contract.
2. `PluginLook` and `HostedPresentation`: the host reports its look.
3. Remote, Vault and File Server read the presentation.
4. Remove the Enterprise API.
5. The kit file's plugin defaults.
6. Plugins declare settings over the look's defaults, on both hosts.
7. Enterprise defaults the Editor's font.
8. The Sample plugin shows the query and a look default.
9. Documentation, STATUS and the whole build.

### Pre-flight scan

| Scope | Shared file or interface | Agrees? | Ruling |
|---|---|---|---|
| Tasks 1 and 2 | `HostedPresentation.java` (Task 1 creates `standard()` with literal values; Task 2 adds `of` and rewrites `standard()`); `PluginHost.presentation`, which Task 2's `PluginRuntime` sets | Yes. On macOS Task 2's `standard()` reports `SCREEN_MENU_BAR`, where `FakePresentations.STANDARD` says `TITLE_BAR`; no contract case compares the default value | E1 |
| Tasks 1 and 3 | `FakePresentations`, `FakePluginHost.setPresentation`; `VersionRangeTest`'s `>=0.18.2` line (Task 1 adds it, Task 3 relabels it) | Yes | none |
| Tasks 1 and 6 | `FakePluginHost` (`setPresentation`, `setKitDefault`, both refusing after a start); `ContractHarness`, `PluginContractTest`, `FakeContractTest`, `AppContractTest` | Yes, if the two setters share one notion of "started" | E2 |
| Tasks 1 and 8 | `Appearance.presentation()`, `FakePresentations.CLASSIC` in `SamplePluginTest` | Yes | none |
| Tasks 1 and 9 | `jasper-sdk/README.md` (Task 1's section), `docs/sdk-architecture.md` (Task 9 names Task 1's contract case `presentationIsTheHostsForEveryPluginOnAnyThreadAndThroughThemeChanges`) | Yes: the name matches | E3 |
| Tasks 2 and 3 | `HostedPresentation.of`, `Projections.of(...).plugins()` in `StagedVaultHost.enterprisePresentation()` | Yes | E4 |
| Tasks 2 and 6 | `PluginRuntime.presentation(Kit, PluginLook, DefaultSource)`; `created.defaults` set only when it was called | Yes | none |
| Tasks 2 and 9 | spec 9.1's `PluginLook` row: `classic`, `classicIcons`, `stripes`, `titleBar`, `menus`, `search` | Yes: Task 2's record components | none |
| Tasks 3 and 4 | `gradle/plugin-architecture.gradle.kts` (Task 3 line 10; Task 4 lines 13-15 and 52-54); Task 4 needs Task 3's consumers gone | Yes. Task 4's Files list said 51-54, its step 52-54 | E6 |
| Tasks 3 and 8 | `VersionRangeTest`'s label names Sample's range before Task 8 moves Sample to it | Yes: the assertion only checks that the range holds the host | none |
| Tasks 4 and 7 | `PresentationConformanceTest` (Task 4 `theDescriptorIsSpecFourOnes`; Task 7 `KEYS` and `theKitListsEveryKeyAndParsesWithoutAProblem`) | Yes: separate members | none |
| Tasks 4 and 9 | `docs/plugin-authoring.md:64-71`, which Task 4's grep expects to remain and Task 9 rewrites; `AGENTS.md` (Task 4 only) | Yes | none |
| Tasks 5 and 6 | `DefaultSource.plugin(String)`, `DefaultSource.CORE` | Yes | none |
| Tasks 5 and 7 | the `[defaults."dev.jasper.editor"]` table the parser accepts | Yes | none |
| Tasks 6 and 7 | `PluginHost.defaults`, `setKitDefault`, `lookDefaultProblems`, the problem texts' fixed prefixes | Yes | none |
| Tasks 6 and 8 | `setKitDefault` and `lookDefaultProblems` in `SamplePluginTest` | Yes | none |
| Tasks 8 and 9 | `docs/plugin-authoring.md`: Task 8's insertion moves Task 9's anchors (`:657-659`, `:736`) down | Yes, found by text | E7 |
| Tasks 1, 2, 3, 4, 6, 7 and 8 | `docs/design/theme-engine/migration.csv` | Yes: each task adds or deletes its own rows | E8 |
| 15a and 15b | `PluginLook.Search` (`FIELD`, `ICON`) and Task 2's `ProjectionReadTest` pattern over `PluginLook`'s three enums; `Presentation.ToolbarSearch`; `FakePresentations.CLASSIC`'s javadoc "Enterprise's on Linux"; `HostedPresentationTest.enterpriseIsReportedFromItsKitAndItsDefaults`; the spec header line "Amended on 2026-10-19 at the user's direction (plan 15): …"; `plan15-baseline` and Task 9 Steps 3 and 4 | Yes: kept as written | E14 |
| Task 1 alone | Step 0 needs a clean tree and code equal to `f78bb0385`'s; Step 5 leaves the README's inline "Bundled manifests require" sentence at 0.18.1 | Clean after this commit. The README sentence needed a ruling | E3, E9 |
| Task 2 alone | Step 4 says "stop and record" for a changed Enterprise Settings render | Ambiguous | E10 |
| Task 3 alone | `PanelSurfaceFixture` starts the Vault inside `new StagedVaultHost(...)` before it can set a presentation; the `LayoutTest` class javadoc still says "while Workstation is published" | Needed rulings | E4, E11 |
| Task 4 alone | `EnterprisePluginTest`'s `ENTERPRISE` is unused once only `startDoesNothing` stays; migration row 1351 says "publishes Workstation only" | Needed rulings | E6 |
| Task 5 alone | The message table's header said "after `head + [defaults]`" while every row is a whole kit; `name(List)` prints `[defaults.dev.x.tool].bad key`, not the table's text | Header fixed in place; `name` needed a ruling | E5 |
| Task 6 alone | `PluginHost` has no `LOG`; `PluginContractTest` does not import `Choice`; the app harness collects every plugin's problems in one list | Needed rulings | E12 |
| Task 7 alone | self-consistent | Yes | none |
| Task 8 alone | Step 3 says the new text follows "the paragraph that follows" the `example:pluginpanels` block; a bullet list follows it | Needed a ruling | E13 |
| Task 9 alone | Files listed `docs/plugin-authoring.md:744`, which no step edits (fact 22: the recipe becomes true); STATUS `:304` (side stripes) also ends "Not merged, not pushed." | `:744` dropped in place; `:304` needed a ruling | E7, E15 |

### Rulings

- **E1. The app's default presentation is per platform; the fake's `STANDARD` is the Windows and Linux one.** On macOS, `HostedPresentation.standard()` reports `SCREEN_MENU_BAR`, because it is the look on that platform. Tests that need a fixed default use `FakePresentations.STANDARD`, and no contract case compares the two hosts' defaults. Why: the app reports the facts of the platform it runs on, and the testkit needs one platform-free value. Cost if wrong: a later contract case that compares defaults fails on macOS only. It would then set the presentation explicitly.
- **E2. One "started" flag in `FakePluginHost`.** It is set by the first `start` and never cleared, even when plugins stop. Both `setPresentation` and `setKitDefault` throw `IllegalStateException("Plugins have already started")` once it is set. The `contexts` map drops stopped plugins, so it cannot serve as the flag. Why: `PluginRuntime`'s setters refuse after start, whatever has stopped since. Cost if wrong: a fake test that sets a presentation after stopping every plugin gets refused. Such a test would use a new host.
- **E3. The README's inline version sentence also names 0.18.2.** In `jasper-sdk/README.md:45`, "Bundled manifests require `sdk = ">=0.18.0, <0.19"`;" gains, right after it, "0.18.2 adds `Appearance.presentation()` (Enterprise, Remote, Vault, File Server and Sample require it);". This goes in Task 1's commit, since Task 1 adds the section. Why: the sentence lists each 0.18 patch and which plugins require it, and leaving it out would make it inaccurate (AGENTS.md "Documentation edits"). Cost if wrong: one clause to drop.
- **E4. Staged fixtures set the host's presentation before the consumer starts, and keep today's Vault composition.**
  - `StagedVaultHost` (Enterprise mode) assigns `host.presentation = enterprisePresentation()` right after `host = AppContractTest.host(...)`, before the Vault starts. The Vault then composes CLASSIC, as `ClassicVaultRenderTest` expects.
  - `PanelSurfaceFixture` builds its `StagedVaultHost` with the three-argument constructor, which is not Enterprise mode, and that Vault has already started ROWS. So the fixture assigns `vault.host.presentation = StagedVaultHost.enterprisePresentation()` where `StagedEnterprise.start` stood (`:61`), before Remote or File Server starts. That Vault stays ROWS, as today.
  - Why: the mock renders stay as they are, and each consumer reads the layout once in `start`. Cost if wrong: none for the renders; a later fixture that wants a classic Vault beside Remote passes `true` to `StagedVaultHost`.
- **E5. `KitParser.name(List)` quotes every segment that is not a TOML bare key (`[A-Za-z0-9_-]+`), in the table prefix and the last key alike.**
  - Messages at the plugin-id level keep `defaults()`'s existing `where` form, `[defaults]."<key>"`. This gives rows 1 and 2 as written.
  - Messages inside a plugin table come from the extended `name(List)`, as `[defaults."dev.x.tool"]."bad key"` and `[defaults."dev.x.tool"].when`.
  - The colour scan, which already uses `name`, then gives `[defaults."dev.x.tool"].hue: "#FF0000" looks like a colour; colours come from the theme`.
  - The `Defaults` record (`KitParser.java:153`, now `values, dropped`) gains `plugins` as its third component.
  - Why: today's `name` would print `[defaults.dev.x.tool].bad key`, which names a different TOML path. No existing path has a non-bare segment except dotted last keys, and those are already quoted, so every existing message is unchanged; `KitParserTest` proves it. Cost if wrong: one helper to revert, and the three messages to restate.
- **E6. In Task 4, keep `gradle/plugin-architecture.gradle.kts:51`, delete `ENTERPRISE`, and correct row 1351.**
  - Line 51 (`val target = ...`) stays: line 55 uses it. Only `:13-15` and `:52-54` are deleted.
  - `EnterprisePluginTest` keeps only `startDoesNothing`. `ENTERPRISE` and `CONSUMER` go, with the imports that only they used. So "`ENTERPRISE`'s version becomes `0.3.0`" is moot: the 0.3.0 pin lives in `PresentationConformanceTest`.
  - In `migration.csv:1351`, "publishes Workstation only" becomes "its start does nothing (plan 15a Task 4)", committed with Task 4.
  - Why: an unused constant is noise (MVP rule), and the inventory must describe the code. Cost if wrong: none.
- **E7. Task 9 finds its `docs/plugin-authoring.md` anchors by quoted text, not by line.** These are "Declaring after `start` returns throws." and `dev.jasper.snippets-0.1.0.zip`, since Task 8 inserts about 15 lines above them. The paragraph at `:64-71` sits above Task 8's insertion, so its lines hold. Why: plan rule "never edit around a missing anchor". Cost if wrong: none.
- **E8. Migration rows copy their neighbours' owners.** These are `sdk` for `jasper-sdk` files, `sdk-testkit` for testkit files (main, test and testFixtures alike), `app.plugins` and `app.presentation` for the app, and `dev.jasper.remote`, `dev.jasper.vault`, `dev.jasper.fileserver` and `dev.jasper.sample` for the plugins. This replaces Task 1's `sdk.ui` and `sdk.testing`.
  - Rows go only to files that `ThemeMigrationCoverageTest` reports. Each `workstation/Layout.java` will be reported once it imports `dev.jasper.sdk.ui.Presentation`. A file that names the SDK type fully qualified and never "appearance" is not reported.
  - Neither `Workstation.java`, `api/package-info.java`, `FakeEnterprise.java`, `Layout.java` nor `LayoutTest.java` has a row today, so deleting the first three needs no row change.
  - Why: the inventory's existing style (`PanelHeader.java` is `sdk`, `FakePluginContext.java` is `sdk-testkit`). Cost if wrong: none, since the test checks paths and fields, not the owner's spelling.
- **E9. If `verifySdkRelease` reports stale 0.18.1 jars after the bump, run `./gradlew sdkRelease --rerun`, then repeat Step 5's command.** Why: STATUS records that a jar left in `build/sdk-release/` fails the check, and `--rerun` recovers. Cost if wrong: none.
- **E10. "Stop and record" in Task 2 Step 4 means: finish the step, record the render, and carry on.** If an Enterprise Settings render moves because Remote's or Vault's page now composes CLASSIC, write down the test, the probe and both values for Task 9's STATUS entry. Do not pause the task, and never lower a floor or a threshold. If a floor fails, find the cause; do not loosen it. Why: under Enterprise the classic page is the user's 2026-10-19 direction, not a regression. Cost if wrong: one render to explain in review.
- **E11. Each `LayoutTest`'s class javadoc becomes "Plan 15a: <Plugin> composes its classic layout exactly under a classic presentation, else its rows."** Drop the imports only the old cases used (`Services`, `Optional`, `Function`), and add `FakePresentations`. Why: the old javadoc describes the removed API. Cost if wrong: none.
- **E12. Task 6 host and harness details.**
  - `PluginHost` gains `private static final System.Logger LOG = System.getLogger(PluginHost.class.getName());` for `lookDefaultProblem`. The class has no logger today.
  - `PluginContractTest` imports `dev.jasper.sdk.settings.Choice`.
  - The app harness collects into one list. `lookDefaultProblems(pluginId)` returns the lines that start with `pluginId + ": "`, in order.
  - `DeclaredSettings.complete()` reports undeclared keys before `reload(true)`, sorted by key. `reload` reports nothing new.
  - The contract cases go after `declaredSettingsReadTheirFileAndABadValueFallsBackWithOneReport` (`:2124`).
  - Why: these complete the stated interfaces without changing them. Cost if wrong: none.
- **E13. The guide's new `example:pluginlook` text goes after the bullet list that follows the `example:pluginpanels` block, just before "## Terminals and capabilities".** Why: no paragraph follows the block, and the bullets are notes on the block's code, so they must stay attached to it. Cost if wrong: a paragraph moves.
- **E14. Keep every interface 15b relies on exactly as written.**
  - the `PluginLook` component order, with `search` last;
  - the enum names `TitleBar`, `Menus` and `Search`, and the `ProjectionReadTest` pattern that names them;
  - `Presentation`'s component order, with `toolbarSearch` sixth;
  - the test name `HostedPresentationTest.enterpriseIsReportedFromItsKitAndItsDefaults`;
  - `FakePresentations.CLASSIC`'s javadoc wording "Enterprise's on Linux";
  - the spec header line's opening words;
  - the `plan15-baseline` path, and Task 9 Steps 3 and 4 unchanged.

  Why: 15b runs next on this branch and edits these by name. Cost if wrong: a 15b anchor goes missing.
- **E15. Only the two STATUS lines of Rulings 24 are corrected.** `:304` (side stripes) also ends "Not merged, not pushed.", but its commits `095e220b9` and `4b55dbfb7` are not ancestors of `f78bb0385` (the series was rebased), and the approved proposal named only plan 12 and plan 14. Task 9's STATUS entry mentions it in one clause for the user to confirm. Why: the plan's constraint not to rewrite history entries, and a fact that cannot be shown from the graph. Cost if wrong: one stale history line remains until the user rules.

A timing-sensitive test may fail once (`StagedTransferLifecycleTest`, or the `@TempDir` clean-up in `StartupStepsApplicationTest`). Run it again on its own before treating it as a failure, and record any re-run in STATUS.
