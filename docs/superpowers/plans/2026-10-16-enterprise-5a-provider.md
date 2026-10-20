# Enterprise presentation 5a: the presentation provider Implementation Plan

> **Status (2026-10-16): executed on `claude/enterprise-provider` (`d65dc99f8..934c33f1e`).** Deviations from this plan's text: (a) Task 5's review added `PluginStatus.NEEDS_LOOK` and a trial election: a provider that can load only beside another provider (a hard dependency on it, directly or through a library) is skipped with "requires another look's plugin; one look can be active at a time" and the next provider wins, and dependents of an invalid provider are dropped as unmet before the election; (b) Task 12's review made the application's selection and presentation fields volatile; (c) Task 13 puts the new AGENTS.md sentence after "`verifySdkArchitecture` and `verifyPluginArchitecture` enforce this.", so that sentence still refers to the SDK rule. From the spec: `PluginRuntime.presentation` takes only the `Kit`; spec 4.9's `DialogMapping` parameter is added beside it when the dialog consumer lands (Rulings 20).

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A plugin can provide Jasper's presentation from a declarative kit file: the descriptor keys and the
`presentation.provide` capability, bundled-but-off-by-default plugins with an explicit opt-in, one active provider chosen
inside the pure resolver from validated kit facts, safe mode dropping presentations, the attempt record that skips a
look that did not finish starting, the bootstrap pre-pass whose one `PluginSelection` feeds the configuration's kit
defaults and the plugin runtime, the `Presentation` value with every projection of spec 9.1 but Branding (plan 11) (chrome values held at the
default projection until their plans land), the kit reaching every `KitContext`, and the four-step fallback at start.

**Architecture:** A new package `dev.jasper.app.presentation` holds the pure parts: `ChromeSpec`, `KitParser` (strict
tomlj parsing into the jasper-ui `Kit` plus `ChromeSpec`, defaults and declared faces), `FontFile` (an AWT-free reader of a
font's name table), `PresentationFacts` (a provider's digest and first problem), `AttemptStore` and `PresentationAttempt`
(the `<home>/presentation-attempt.toml` record), the projection values and `Presentation`. `dev.jasper.app.plugins` gains
`PluginSelection`, the pre-pass that discovers plugins once, reads every declared presentation, resolves with the
new `Presentations` argument and parses the winner, and `PluginRuntime.start(selection, …)` consumes it without a second
scan. `ApplicationBootstrap.start` runs the pre-pass after the hand-off checks and before `new ConfigService(file, mac,
defaults)`, so the first snapshot carries the kit's defaults (`DefaultSource`, in `dev.jasper.app.config`). Nothing ships a
provider yet: plan 5b adds the Enterprise plugin, its fonts and theme.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3
and AssertJ 3.27.7, Python 3.11+ standard library for the mechanical edit scripts (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 4, 5.1 to 5.9 without the `[branding]` table, 9.1, 9.2, 15, 16.1.6 to 16.1.8 and row 5 of 19.1). The spec
row is delivered as two plans: this one (5a, the provider machinery, proven with fixture providers) and
[5b](2026-10-16-enterprise-5b-enterprise.md) (faces and fonts, the plugin theme tier, the Enterprise plugin, its theme,
conformance and the shipped-kit renders). Plans 1, 2, 3a, 3b, 3d and 4a are on `main` (`867940b55`) and in this branch;
3c, 3e, 4b and 4c are not, and this plan uses none of their APIs (see "Execution notes (2026-10-16)" at the end).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-5`, branch
`claude/enterprise-provider`, which holds `main` at `867940b55` plus the plan commits. Run every command from the
worktree root, never in `~/projects/moray`. Line numbers below were re-verified at `867940b55` on 2026-10-16; every edit
quotes the exact text it replaces, and the edit scripts stop with a message when that text is not found the stated number of times. If a
script stops, re-find the quoted text (it moves, it does not change), adjust the anchor, and re-run; never edit around a
missing anchor. Plan 5b executes on the same branch after this plan.

## Before you start

Do not rebase or merge: the branch already holds `main` at `867940b55`, and plans 3c, 3e and 4b, which finish in other
worktrees at the same time, are reconciled with this branch at merge. Check that the plans this one reads are in it:

```bash
git merge-base --is-ancestor 867940b55 HEAD && echo ok                                      # ok
grep -c 'VERSION = "0.18.0"' jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java          # 3a: 1
grep -c 'PRESENTATION_PROVIDE' jasper-sdk/src/main/java/dev/jasper/sdk/Capabilities.java      # 3a: at least 1
grep -c 'record Separator(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java              # 3b: 1
grep -c 'TRANSFER' jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java                # 3d: at least 1
grep -c 'Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java                       # 4: 1
ls jasper-ui/src/main/java/dev/jasper/ui/kit/ControlVariants.java jasper-ui/src/main/java/dev/jasper/ui/kit/ColorSlots.java \
   jasper-ui/src/main/java/dev/jasper/ui/metrics/MetricTokens.java                             # 4: all three exist
grep -c 'TypeScale CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/metrics/TypeScale.java       # 4: 1
grep -oE '(^|[ ,(])(ROW_HEIGHT|FIELD_RADIUS)\(' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java | wc -l   # 4a: 2
grep -c 'enterpriseTokens' jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java   # 4a: at least 1
```

If any check prints 0, something other than `ok`, or a missing file, stop. Plan 4 is delivered as 4a (the classic
catalog), 4b (the form faces) and 4c (the data faces). 5a reads only 4a's values, so it runs before 4b and 4c land;
5b's renders and "Turning Enterprise on gives classic controls" need 4b's and 4c's faces, which is 5b's entry condition.

**Plan 4's names.** Plan 4 was written at the same time as this plan. This plan uses the names below, which are spec
5.2, 5.4, 6.1 and 19.1's. On 2026-10-16 every row was checked against 4a's merged code at `867940b55` and matches it
(`UiKeySet` is `dev.jasper.ui.theme.UiKeySet`; the `ControlVariants` enums also implement `ControlVariants.Variant`,
whose `key()` is the lower-cased name), so no code block needed a rename. If a later merge changes one, a renamed member
is one search and replace per row; a member whose shape differs means adapting the code that uses it to the merged API
without changing behaviour. Record any change in this plan's status banner.

| This plan uses | Spec source |
|---|---|
| `new Kit(String id, DesignMetrics metrics, TypeScale type, FaceSet faces, ControlVariants controls, ColorSlots colors, UiKeySet uiKeys)`, `Kit.DEFAULT`, `Kit.CLASSIC`, accessors `controls()`, `colors()` | 6.1 |
| `ControlVariants` a record of 14 components in the order toggle, exclusive, tabs, group, formLabel, empty, table, tree, stepper, orderedList, buttons, tag, route, prompt; each a nested enum `ControlVariants.Toggle` … `ControlVariants.Prompt` whose constants are the 5.2 values upper-cased (`SWITCH`, `CHECKBOX`; `SEGMENTS`, `RADIOS`; `SEGMENTS`, `FOLDER`; `CARD`, `TITLED_BOX`; `PLAIN`, `COLON`; `TILE`, `TEXT`; `PLAIN`, `CLASSIC`; `PLAIN`, `CLASSIC`; `STEPPER`, `SPINNER`; `INLINE`, `SIDE_COLUMN`; `RAISED`, `CLASSIC`; `TINT`, `BOXED`; `TILES`, `EDGED`; `TILE`, `PLAIN`), default value first | 5.2, 6.1 |
| `ColorSlots(SemanticColor dialogSurface, SemanticColor navigationSurface)` | 5.7, 6.1 |
| `MetricTokens` (plan 4a Task 3): a final class with a private constructor; `MetricTokens.of(DesignMetrics catalog)` (each token's first role as the catalog holds it, so `CONTROL_HEIGHT` reads `FIELD_HEIGHT`), `int value(MetricTokens.Token)`, `MetricTokens with(Token, int)`, which throws `IllegalArgumentException("[metrics].<key>: <value> is outside <min>-<max>")`; the enum `MetricTokens.Token` (`CONTROL_HEIGHT` … `STATUS_DOT` in 5.4's order) with `key()`, `min()`, `max()` and `roles()`; `DesignMetrics.with(MetricTokens)`; the test fixture `KitFixtures.enterpriseTokens()` (spec 5.1's `[metrics]` over the classic base) | 5.4 |
| `DesignMetrics.Role.ROW_HEIGHT`, `DesignMetrics.Role.FIELD_RADIUS` | 5.4, 5.5 |
| `TypeScale.CLASSIC` (base 12) | 5.6 |
| `UiKeySet.CLASSIC` | 6.5 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task here runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run.
- Work only in the worktree above on `claude/enterprise-provider`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: no shipped plugin declares a presentation in this plan, so the default look is untouched: every file the headless gallery writes for Slate Cobalt Dark and Light (`./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark` and `=light`) is byte-identical to the base, and every reference pair and calibration suite stays green unchanged. Task 13 proves it.
- The descriptor keys are exactly spec 4.1's: `presentation` (string), `themes` (array of strings), `enabled_by_default` (boolean, default `true`); `presentation.provide` is legal only with `presentation`, and `presentation` only with `presentation.provide`. The consent text is "Change Jasper's window chrome and controls after a restart".
- The effective-enabled rule lives only in `PluginStateStore.effective(Entry entry, PluginCandidate candidate)`: enabled unless the entry says disabled, or there is no entry and the candidate is BUNDLED with `enabled_by_default = false`. The opt-in is the entry's `opt_in = true`, written only by enabling a BUNDLED off-by-default plugin and cleared by any write that disables; `consenting` writes it false.
- One active presentation: after admission, cycle removal and unmet-dependency removal, the candidates that declare a presentation and have no problem are ranked DEV, then BUNDLED, then USER, then by lowest id; losers are SKIPPED with "the <winner name> look is active"; a problem is SKIPPED "Presentation invalid: <message>"; a matching `pending` or `failed` attempt SKIPs the winner with "did not finish starting with this look last time"; safe mode makes every presentation-declaring plugin DISABLED "safe mode uses the standard look". Losers and the attempt skip post no plugin problem.
- Kit file limits and messages are spec 5.9's: at most 64 KiB; fonts `.ttf` or `.otf`, at most 2 MiB each; one message per failure naming the key, for example `[metrics].row_height: 40 is outside 18-28`. The `[branding]` table is refused as an unknown table until plan 11.
- The kit-default allowlist is exactly `ui.theme.light`, `ui.theme.dark`, `palette.visible_rows`, `window.toolbar_height`; precedence is user-saved, then kit default, then core default.
- `dev.jasper.app.presentation` depends only on `dev.jasper.app.config`, `dev.jasper.app.settings`, `dev.jasper.app.persistence`, `dev.jasper.app.dock`, `dev.jasper.ui.kit`, `dev.jasper.ui.metrics`, `dev.jasper.ui.theme`, the JDK and tomlj (spec 9.2); `ChromeSpec` is referenced only in that package. SDK types stay inside `dev.jasper.app.plugins` (AGENTS.md).
- Strategy construction and every projection are infallible: no constructor of `Projections`, `Presentation` or a projection value throws for any `ChromeSpec` the parser accepts (tested).
- Javadoc doclint fails the build on errors: jasper-app runs `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`. New public types carry a class comment, and record components an `@param`.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app, the SDK, the testkit or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches (any `import dev.jasper.ui…` or `import java.awt…`, the word `appearance` in any case, `new ArrayList` and similar constructor names ending in List, Table, Field, Panel and the like) gets its row in the same commit; each task lists its rows, and each task's test run includes `ThemeMigrationCoverageTest`, which names any row still missing. A new row uses the classification `nonvisual-helper`, `none` for both task columns, the file's own test (or `none-direct`) as evidence, and a disposition beginning "New in Enterprise plan 5a Task N" ("Test/fixture ownership; New in …" for a test). A row has exactly eight comma-separated fields, so a disposition uses semicolons, never commas.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; the em dash, ellipsis and arrows in copy are ordinary characters.

## Review Focus

1. A hand-edited `plugins.toml` that enables two presentation providers (a BUNDLED one and a `--plugin-dir` DEV one): the
   DEV one wins, the BUNDLED one is SKIPPED with "the <DEV name> look is active", no notice is posted for it, and the
   Plugins page's "after restart" prediction equals what the launch ran, so no restart banner appears. Task 5:
   `PresentationSelectionTest.twoEnabledProvidersElectOneByOriginThenIdAndLosersPostNoProblem`; Task 6:
   `PresentationSelectionTest.theCatalogPredictsTheLaunchIncludingAnAttemptSkip`.
2. A user turns a bundled off-by-default look on, then drops a newer zip of the same plugin into `plugins/` and later
   removes that copy: the opt-in survives every prune path, so the bundled look stays on; a copy installed through the
   Plugins page carries consent instead, and removing it leaves the bundled look off. Task 2:
   `PluginStateStoreOptInTest` (four cases).
3. Jasper crashes after the look's window was asked for but before it painted (a nonzero `System.exit`): the next start
   skips that look with one Warning and Try Again; a new build or a changed kit gets a fresh try; Try Again clears the
   record and restarts. Task 4: `AttemptStoreTest.theProcessHandleMarksItsLookAndKeepsAFailureOverALaterPaint` (the
   handle); Task 12: `PresentationAttemptTest` (`anExitThatIsNotOrderlyBeforeProofKeepsPending`,
   `tryAgainForgetsTheRecordAndRestarts`, `theFirstWindowsFirstPaintProvesTheLook`, `theRehearsalsPaintProvesTheLook`).
4. A kit file with a typo, an out-of-range token, a truncated or oversized file, deep nesting or `../` paths: the
   provider is SKIPPED with exactly one message naming the key, the default look applies, nothing escapes the pre-pass,
   and the plugin-problem notice appears once per digest, not at every start. Task 3: `KitParserTest` (negatives,
   `deepNestingIsOneProblem` and `fuzzedInputsOnlyEverProduceProblems`); Task 12:
   `PresentationAttemptTest.anInvalidKitIsAnnouncedOncePerDigest`.
5. A fresh home (no `config.toml`) under a provider whose kit sets defaults: the first snapshot already carries them, a
   saved value wins, Settings' Reset returns to the kit's value, and picking the core default while the kit overrides it
   saves an explicit value. Task 7: `DefaultsPrecedenceTest`.

## Verified facts

Re-verified at `867940b55` on 2026-10-16 (the line numbers are that commit's); facts about plans 3a to 4 cite the plan.

1. `DescriptorParser.CAPABILITIES` lists eleven capabilities (`DescriptorParser.java:15-17`) and `KEYS` fifteen keys
   (`:21-23`); `parse` rejects unknown keys (`:51`) and unknown capabilities (`:61-62`) and builds the descriptor at
   `:68-70`. Plan 3a adds `Capabilities.PRESENTATION_PROVIDE` to the SDK only (3a, Planned deviations 2).
2. `PluginDescriptor`'s canonical constructor has fifteen components and three conveniences (`PluginDescriptor.java:8-31`).
3. `PluginStateStore.Entry` is `(enabled, consented, remove)` with `DEFAULT = (true, {}, false)`
   (`PluginStateStore.java:34-37`); `read` (`:51-65`), `transact` (`:67-82`), `enabling` (`:85-91`), `consenting`
   (`:94-96`), `removing` (`:102-109`) and `render` (`:145-156`).
4. `PluginResolver.resolve(candidates, state, sdk, safeMode)` (`PluginResolver.java:21-58`): the safe-mode check
   (`:26-29`), admission (`:38-41`), cycles (`:43-45`), the unmet loop (`:46-56`); `admit` at `:65-77`.
5. `PluginCatalog.compute` resolves the next launch (`PluginCatalog.java:58-63`), sets `needsConsent` (`:83`) and the row's
   `enabled` as `entry == null || entry.enabled()` (`:94`) and `canToggle` (`:95`); `Disk` is `(candidates, pending,
   state)` (`:23-25`).
6. `PluginAdmin.disk()` scans the three origins and pending installs (`PluginAdmin.java:34-45`); `setEnabled` refuses an
   unreviewed USER plugin and writes `enabling` (`:51-58`); `consent` (`:61-67`); `remove` (`:74-92`).
7. `PluginInstaller.commit` writes `consenting` only with consent (`PluginInstaller.java:114-128`); `discardPending`
   removes the entry of an id with no installed folder (`:140-149`). `PluginMaintenance.removals` removes the entry after
   the plugin (`PluginMaintenance.java:126-140`); `consumeZips` commits without consent (`:108`).
8. `PluginRuntime.start(tables, dark)` discovers (`PluginRuntime.java:615-624`), prepares settings files for every
   candidate (`:625-628`), reads the state (`:632-638`), resolves (`:641`), records every non-superseded SKIPPED status as
   a NOT_STARTED problem (`:643-646`), builds the host and copies the pre-start values (`:647-655`), loads and starts in
   order (`:669-691`) and builds the catalog launch (`:692`). Pre-start setters throw once started (`:360-393`).
9. `PluginRuntime.Snapshot` is `(rows, restartNeeded, safeMode)` (`:188-191`); the banner reads "Restart Jasper to apply
   your changes." (`PluginManager.java:153-154`); `pluginmanager.Capabilities.describe` maps each capability
   (`Capabilities.java:7-23`).
10. `ApplicationBootstrap.prepareDesktop` opens `AppLog` (`ApplicationBootstrap.java:55`), runs `preparePlugins`
    (`:75`) and installs a shutdown hook that closes the log (`:59-66`); `start` makes the `ConfigService` after the
    hand-off checks (`:310-323`); `startDesktop` opens the first window (`:139-143`) or warms up (`:145-152`);
    `createApplication` builds the application (`:174-175`) and starts plugins (`:204`). `ApplicationBootstrapTest`
    calls the 4-argument `start` with lambda launches (`ApplicationBootstrapTest.java:72-104`), and the test task sets no
    `jasper.home`.
11. `JasperApplication`'s 7-argument constructor registers fonts, reads the library and selection and calls
    `ThemeController.startup(dark, selection, Kit.DEFAULT, …)` (`JasperApplication.java:236-253`); `preparePlugins`
    (`:833-836`); `startPlugins(codeLocation, developmentDirectory, safeMode, dirs)` (`:843`) builds the runtime and calls
    `plugins.start` (`:906`) and `PluginProblemNotices.announce` (`:907-908`); `quitNow` (`:1749-1753`); FIRST_PAINT is
    marked in `timeFirstTerminal` (`:1387-1392`); the rehearsal's stage paints with `Rehearsal.paint` (`:829`).
12. `ThemeController.startup` tries the selection, then Slate Cobalt Dark (`ThemeController.java:56-73`); the private
    constructor builds `AppearanceBindings` (`:100`); controllers made without a kit install `Kit.DEFAULT` (`:76-88`).
13. Plan 1 left the app reading the kit from the installed look and feel: `AppearanceBindings.context`
    (`AppearanceBindings.java:24-26`), `PresentationIcons.context` (`PresentationIcons.java:23-26`) and `HostedKit.create`
    (`HostedKit.java:56`) all call `FlatLafInstaller.installedKit()` (plan 1's STATUS entry, deviation 1 and the plan-5
    entry conditions).
14. `ConfigService(file, macOs[, worker, publisher])` reads with `ConfigSnapshot.defaults()` at `:118` and `:429` and
    parses through `ConfigLoader.parse(file, text, macOs)` at `:213`, `:306`, `:334` and `:396`;
    `ConfigLoader.parse` returns `ConfigSnapshot.defaults()` on a syntax error (`ConfigLoader.java:80`);
    `ConfigSnapshot.from(values, keybindings, plugins)` reads every key through `values.get` (`ConfigSnapshot.java:87-111`).
    `ConfigService.State` has eight components and a four-argument convenience (`ConfigService.java:48-60`).
15. `SettingsDraft.value` shows an unstaged key as `baseline.get` and a staged empty as `setting.defaultValue()`
    (`SettingsDraft.java:31-35`); `SchemaPage` compares with `row.setting.defaultValue()` in `edited` (`:465`) and
    `showValue` (`:511-512`); `AppearanceSelection` compares with core defaults (`:58-59`, `:155-159`); `SettingsDialog`
    builds the draft from `state.values()` (`:164`).
16. `StartupTrace.line` prints `Startup (<kind>, AOT cache <x>, CDS <y>):` (`StartupTrace.java:301-321`); four
    `StartupTraceTest` lines assert it.
17. `StaticDefaultTest.DEFAULT_KIT_READERS` is FlatLafInstaller, ThemeController and JasperApplication
    (`StaticDefaultTest.java:58-59`) and the test fails on any other `Kit.DEFAULT` reader. Plan 4a added `CLASSIC_KIT`
    (`:44-45`), the classic catalogs to `CATALOGS` (`:50-51`) and the pin that nothing reads `Kit.CLASSIC` (`:173`), which
    Task 3 lifts for `KitParser`.
18. `ThemeMigrationCoverageTest.CANDIDATE` matches any `import dev.jasper.ui` among others
    (`ThemeMigrationCoverageTest.java:17-18`), over jasper-app, the SDK, the testkit and plugins (`:62-77`).
19. JBR 25.0.4.1 (checked on 2026-10-16 with the IBM Plex 1.1.0 files): after `GraphicsEnvironment.registerFont`, a
    static face resolves by its full name (name ID 4: "IBM Plex Sans", "IBM Plex Sans Medm", "IBM Plex Sans SmBld",
    "IBM Plex Mono", "IBM Plex Mono Medm"); `Font.getFamily` returns name ID 1, which for the medium and semibold files is
    "IBM Plex Sans Medm" and "IBM Plex Sans SmBld", while the typographic family (ID 16) is "IBM Plex Sans"; deriving
    `TextAttribute.WEIGHT` from the regular face does not select another file. So faces are named by full name and the
    family check reads ID 16, falling back to ID 1.
20. `PluginJars.build` and `PluginJars.addResource` build fixture plugin jars (`jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java:33-91`).
21. Plan 3d leaves `PluginDescriptor`, `PluginResolver` and the state store untouched; plan 3d adds `HostedPlugin.iconHue`
    (3d, Rulings 8). Plan 4 adds the `Kit` components, `Kit.CLASSIC`, `ControlVariants`, `ColorSlots`, `MetricTokens`,
    `TypeScale.CLASSIC`, `UiKeySet.CLASSIC` and the roles `ROW_HEIGHT` and `FIELD_RADIUS` (spec 19.1 row 4; names in the
    table above).

## Rulings

Decisions where the spec is silent or a literal reading cannot be built. Record each in `docs/STATUS.md` (Task 13) and in
this plan's status banner.

1. **Two plans.** Spec row 5 is delivered as 5a (this plan) and 5b. 5a proves the machinery with fixture providers;
   5b adds the faces, the plugin theme tier, the Enterprise plugin, its theme, conformance and the renders. Cost if
   wrong: none; the order is the spec's.
2. **Projections are complete and gated.** Every 9.1 projection is a value in `dev.jasper.app.presentation`, computed
   from the parsed chrome by the 9.1 table, but `Presentation.LANDED` (empty in this plan) decides which groups take the
   file's values: `WINDOW_CHROME` (plan 6), `DOCK_CHROME` (7), `RUN_WINDOW` (8), `SETTINGS` (9). Until a group lands its
   fields are the default projection, as row 5 says; each later plan adds its group with its consumer. `DesktopFlags`
   is computed from the file but not applied (plan 6 applies it), `TitlePane.centerTitle` stays unconditional, and
   `Branding` is plan 11's. Cost if wrong: each later plan edits one constant.
3. **The interim installed-kit readers stay.** Plan 1's static chrome readers (`ChromePainter.installed()`,
   `NativeStyles.installed()`, `FlatLafInstaller.installedKit()` in the workspace) keep reading the installed kit,
   which is the presentation's kit after start-up because every install of the controller uses it (pinned by
   `PresentationReachTest`). The three `KitContext` construction sites and `PresentationIcons` take the presentation's
   kit (plan 1's entry conditions). Converting the chrome readers to the window's kit is plans 6 and 7's work with the
   chrome. Cost if wrong: a rollback during start-up can show off-EDT readers `Kit.DEFAULT` for one install.
4. **`PresentationFacts.read` takes a `Source`.** Spec 4.4's three-argument form cannot resolve kit-relative paths or
   name the declared theme ids that `[defaults]` is checked against, so `read(Source(id, kitPath, files, themePaths))`
   reads everything through one bounded jar reader, and the facts carry the parsed kit so the winner is not parsed twice.
   `Presentations` keys facts by `<origin>:<id>`, one per copy. Cost if wrong: a signature change.
5. **Descriptor paths.** A path is one or more `/`-separated segments of `[a-z0-9_.-]`, none `.` or `..`; the parser
   checks syntax (and `.json` plus a valid theme id for `themes`); existence is checked when the file is read: a missing
   kit file is "Presentation invalid: presentation: <path> is not in the plugin's jars", a missing theme file is reported
   like an invalid one. Cost if wrong: an earlier failure point.
6. **Order of the safe-mode checks.** Safe mode tests "declares a presentation" before "is a USER plugin", so a USER
   provider reads "safe mode uses the standard look". Hard dependents of a skipped provider are then skipped by the
   existing unmet rule, in a second unmet pass after the presentation pass. Cost if wrong: a different reason string.
7. **The attempt record's fields are optional.** An entry may carry only `announced` (an invalid kit's notice) or a state
   without `announced`; unknown or malformed content makes the whole file absent (logged). Cost if wrong: none.
8. **`enablingPresentation(id, optIn, others)`.** The store cannot see the candidate, so the opt-in is an argument;
   `consentingPresentation(id, capabilities, others)` is as spec 4.4. `turnedOff` names every provider turned off,
   joined with " and ". Cost if wrong: a parameter.
9. **The selection is fixed at construction.** `JasperApplication` takes the `PluginSelection` in its constructor
   (the theme library, kit and fonts need it before plugins start) and `startPlugins(AppDirs)` consumes it; spec 4.8's
   `startPlugins(PluginSelection, AppDirs)` becomes that pair. The old `startPlugins(codeLocation, dev, safeMode,
   dirs)` stays for tests and refuses a selection that names a presentation. Cost if wrong: one signature.
10. **Core-look conveniences.** Tests construct the resolver, the state entry, the catalog disk, the runtime start, the
    config service, loader, snapshot and state without kit data; those overloads stay and mean the core look
    (`Presentations.NONE`, `DefaultSource.CORE`). They are not user-facing compatibility shims. Cost if wrong: test churn.
11. **The pre-pass is injectable.** `ApplicationBootstrap.start(args, out, error, launch)`, which tests call, uses no
    plugins; `main` passes the production pre-pass. A test must never scan the developer's real home. Cost if wrong: none.
12. **Faces.** `FontFile` reads a font's full name (name ID 4) and typographic family (ID 16, else ID 1) without AWT
    (fact 19). In this plan a kit's `FaceSet` is its base's; plan 5b registers the files and names the faces. A font
    whose name table cannot be read is not a kit problem (it degrades in 5b); five weights cannot exceed 5.9's eight-file
    limit, so that limit has no separate check. Cost if wrong: a kit problem instead of a degraded face.
13. **Deep nesting.** tomlj accepts very deep dotted tables (`[a.a.a…b]` with 20,000 parts parses without error), so no
    walk of a parsed kit recurses: `KitParser` checks the top-level keys first and walks values with an explicit stack.
    A `StackOverflowError` anywhere in `KitParser.parse` (tomlj's own parser included) is "kit file: not valid TOML", and
    `PresentationFacts.read` turns one from any reader into the provider's problem, so nothing escapes the pre-pass. A
    `--safe-mode` start reads no presentation files at all. Cost if wrong: none.
14. **Colour literals** are `#rgb`, `#rrggbb`, `#rrggbbaa`, `0xrrggbb` and `rgb(`/`rgba(` anywhere in the file, named by
    key when inside a value and by line otherwise. **`window_title`** is at most 120 characters, not blank, with braces
    only around the three tokens. **`[type.ui]`** needs all three weights and `[type.data]` both. Cost if wrong: a stricter file.
15. **The classic base's chrome defaults** are 5.2's Enterprise column with `window_title = "{context}"`; its controls,
    metrics, type, faces and slots are `Kit.CLASSIC`'s. Cost if wrong: only kits that omit chrome keys differ.
16. **Fallback reporting.** When the kit is blamed (4.7), the provider is SKIPPED with "its look could not be installed;
    Jasper uses its standard look" and posts the ordinary NOT_STARTED problem; the attempt record says `failed`. The
    attempt-skip Warning is posted in the Plugins notification group. Cost if wrong: copy.
17. **The audit's four editors.** `PlatformFontSize`, `Editors.OptionalNumber` and `WorkspaceEditors.Opacity` compare
    with the effective default, as spec 5.8's table says, through `Editors.effectiveDefault(draft, setting)`, which is
    `draft.defaultValue(setting)` and the core default only for a page made without a draft (plugin settings pages);
    `NotificationGroupsEditor` has no setting-default comparison left to change (its one comparison is with a group's
    declared mode, and removing the table at its default goes through `SchemaPage`, which Task 7 moves to the effective
    default). `DefaultsPrecedenceTest` still pins that no allowlisted key reaches these editors. Cost if wrong: none.
18. **The trace line** reads `Startup (cold, AOT cache used, CDS on, look=default): …` and the trace file gains `"look"`.
    Cost if wrong: one string.
19. **Host facts.** `Projections` take `Host(boolean mac, boolean unifiedTitleSupported)`, computed by `PluginSelection` from
    `os.name` and FlatLaf's `SystemInfo.isMacFullWindowContentSupported` (no AWT), because `presentation` has no
    `platform` edge. Cost if wrong: none.
20. **The runtime's pre-start setter takes only the kit.** Spec 4.9 names `PluginRuntime.presentation(Kit kit,
    DialogMapping dialogs)`; in this plan it is `presentation(Kit kit)`, because nothing in the runtime consumes the
    dialog mapping yet (Rulings 2). The `DialogMapping` parameter is added beside the kit when the dialog consumer lands.
    Cost if wrong: one parameter.
21. **A failure sticks in the handle.** `PresentationAttempt.pending()` writes nothing once the handle recorded `failed`,
    and `exiting()` leaves a failed record alone (Task 4's file, changed in Task 12's review). Why: the bootstrap starts
    the plugins, where a provider that fails to load writes `failed`, and only then marks the first window `pending`;
    without the guard the window overwrote `failed`, an orderly quit then cleared the record, and every start retried the
    broken provider (spec 4.6). Cost if wrong: none; a look that fails at start is skipped next time, as the spec says.
22. **Try Again without a restart.** When `restart(RestartMode.SAME)` returns false (the command line is unknown), Try
    Again posts an Info "Restart Jasper to try the look again" after clearing the record. Why: otherwise the record is
    cleared and nothing happens. Cost if wrong: one notice's wording.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/presentation/` (new package): `package-info.java`; `ChromeSpec.java` (the nine
  chrome points); `FontFile.java` (name-table reader); `KitParser.java` (strict parser and validator); `PresentationFacts.java`
  (digest and problem of one provider); `AttemptStore.java` and `PresentationAttempt.java` (the attempt record);
  `Projections.java` (the projection values and the 9.1 table); `Presentation.java` (the one value a process runs with).
- `jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java` (new): kit defaults over the core defaults.
- `jasper-app/src/main/java/dev/jasper/app/plugins/`: `DescriptorParser`, `PluginDescriptor` (keys); `PluginStateStore`
  (opt-in, effective rule, one-provider transactions); `PluginResolver` and new `Presentations.java` (selection);
  `PluginStatus` (quiet reasons); `PluginCatalog`, `PluginAdmin`, `PluginInstaller`, `PluginMaintenance` (rows, opt-in,
  prune paths, prediction); new `PluginResources.java` (bounded jar reads) and new public `PluginSelection.java` (the
  pre-pass); `PluginRuntime`, `PluginHost`, `HostedContext`, `HostedKit`, `HostedKitView` (consume the selection, carry the
  kit).
- `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`: the pre-pass, the attempt writes.
- `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`: the presentation, the fallback, the
  attempt lifecycle and the Warning.
- `jasper-app/src/main/java/dev/jasper/app/appearance/`: `ThemeController` (kit-carrying constructor and the four steps),
  `AppearanceBindings` (a kit).
- `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java`: the source context's kit.
- `jasper-app/src/main/java/dev/jasper/app/settingsui/`: `SettingsDraft`, `SchemaPage`, `AppearanceSelection`,
  `SettingsDialog` (effective defaults); `jasper-app/src/main/java/dev/jasper/app/config/`: `ConfigService`,
  `ConfigLoader`, `ConfigSnapshot`.
- `jasper-app/src/main/java/dev/jasper/app/pluginmanager/`: `Capabilities` (consent text), `PluginManager` (banner).
- `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java`: `look=`.
- Tests (new): `presentation/KitParserTest`, `presentation/FontFileTest`, `presentation/PresentationFactsTest`,
  `presentation/AttemptStoreTest`, `presentation/ProjectionsTest`, `plugins/PluginStateStoreOptInTest`,
  `plugins/PresentationSelectionTest`, `plugins/PluginSelectionTest`, `config/DefaultsPrecedenceTest`,
  `appearance/ThemeControllerFallbackTest`, `application/PresentationReachTest`, `application/PresentationAttemptTest`;
  `testsupport/LookFixtures` (fixture providers on disk).

---

### Task 1: The descriptor keys and the capability

`plugin.toml` gains `presentation`, `themes` and `enabled_by_default` (spec 4.1), and `presentation.provide` joins the
host's capabilities with its key rule and consent text (4.2). Nothing reads the new values yet.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java:15-23,60-71`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java:8-45`
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java:19`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java`, `jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java`

**Interfaces:**
- Consumes: `dev.jasper.sdk.Capabilities.PRESENTATION_PROVIDE` (plan 3a).
- Produces: `PluginDescriptor.presentation()` (a jar path, `""` when none), `themes()` (jar paths, declared order),
  `enabledByDefault()`, `declaresPresentation()`, `themeIds()` (each theme file's name without `.json`);
  `DescriptorParser.PRESENTATION_PROVIDE`; `static boolean DescriptorParser.jarPath(String)`. The 15-argument
  `PluginDescriptor` constructor stays as a convenience with `("", List.of(), true)`.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 5a Task 1 step 1: descriptor tests for the presentation keys."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java", [
    ('''        assertThat(DescriptorParser.CAPABILITIES).isEqualTo(Set.of("terminal.observe", "terminal.selection",
            "terminal.inject", "terminal.open", "session.provide", "palette.contribute", "files.source", "network.listen",
            "clipboard.read", "terminal.environment", "editor.provide"));''',
     '''        assertThat(DescriptorParser.CAPABILITIES).as("every SDK capability, and nothing else")
            .isEqualTo(Set.copyOf(dev.jasper.sdk.Capabilities.ALL));''', 1),
    ('''    @Test void aNameThatShowsNothingIsRejected() {''',
     '''    private static final String LOOK = """
        id = "dev.example.look"
        name = "Look"
        version = "0.1.0"
        entry = "dev.example.look.LookPlugin"
        sdk = ">=0.18.0, <0.19"
        """;

    @Test void aPresentationProviderDeclaresItsKitItsThemesAndThatItIsOffByDefault() throws Exception {
        var descriptor = DescriptorParser.parse(LOOK + """
            capabilities = ["presentation.provide"]
            presentation = "presentation/look.kit.toml"
            themes = ["themes/look.json", "themes/look-contrast.json"]
            enabled_by_default = false
            """);
        assertThat(descriptor.presentation()).isEqualTo("presentation/look.kit.toml");
        assertThat(descriptor.declaresPresentation()).isTrue();
        assertThat(descriptor.themes()).containsExactly("themes/look.json", "themes/look-contrast.json");
        assertThat(descriptor.themeIds()).containsExactly("look", "look-contrast");
        assertThat(descriptor.enabledByDefault()).isFalse();
    }

    @Test void withoutTheKeysAPluginIsOnByDefaultAndProvidesNothing() throws Exception {
        var descriptor = DescriptorParser.parse(LOOK);
        assertThat(descriptor.presentation()).isEmpty();
        assertThat(descriptor.declaresPresentation()).isFalse();
        assertThat(descriptor.themes()).isEmpty();
        assertThat(descriptor.enabledByDefault()).isTrue();
        assertThat(DescriptorParser.parse(LOOK + "themes = [\\"themes/extra.json\\"]\\n").themeIds())
            .as("any plugin may ship themes, with no capability").containsExactly("extra");
    }

    @Test void thePresentationKeyAndItsCapabilityNeedEachOther() {
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "presentation = \\"look.kit.toml\\"\\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class)
            .hasMessage("presentation needs the presentation.provide capability");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "capabilities = [\\"presentation.provide\\"]\\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class)
            .hasMessage("capabilities: presentation.provide needs the presentation key");
    }

    @Test void pathsAreLowercaseJarPathsWithoutDotSegmentsOrALeadingSlash() {
        for (String bad : List.of("/look.kit.toml", "a/../look.kit.toml", "Look.kit.toml", "a//look.kit.toml", "a/./b.toml",
                "presentation/", "a b.toml", "..", "a\\\\b.toml"))
            assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "capabilities = [\\"presentation.provide\\"]\\npresentation = \\""
                    + bad.replace("\\\\", "\\\\\\\\") + "\\"\\n"))
                .as(bad).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageStartingWith("presentation: ");
        for (String bad : List.of("themes/Look.json", "themes/look.txt", "../look.json", "themes/look_.json", "themes/.json"))
            assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = [\\"" + bad + "\\"]\\n"))
                .as(bad).isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessageStartingWith("themes");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = [\\"a/look.json\\", \\"b/look.json\\"]\\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("themes names the theme look twice");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "themes = \\"themes/look.json\\"\\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("themes must be an array of strings");
        assertThatThrownBy(() -> DescriptorParser.parse(LOOK + "enabled_by_default = \\"no\\"\\n"))
            .isInstanceOf(DescriptorParser.InvalidDescriptor.class).hasMessage("enabled_by_default must be a boolean");
    }

    @Test void aNameThatShowsNothingIsRejected() {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java", [
    ('''        assertThat(Capabilities.describe("editor.provide")).isEqualTo("Open the files Jasper opens for editing (editor.provide)");''',
     '''        assertThat(Capabilities.describe("editor.provide")).isEqualTo("Open the files Jasper opens for editing (editor.provide)");
        assertThat(Capabilities.describe("presentation.provide"))
            .isEqualTo("Change Jasper's window chrome and controls after a restart (presentation.provide)");''', 1)])
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.DescriptorParserTest' --tests 'dev.jasper.app.pluginmanager.CapabilityDescriptionsTest'`
Expected: FAIL; `:jasper-app:compileTestJava` reports "cannot find symbol" for `presentation()`, `declaresPresentation()`,
`themes()`, `themeIds()` and `enabledByDefault()` in `DescriptorParserTest`.

- [ ] **Step 3: Parse the keys and add the consent text**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 1 step 3: presentation, themes and enabled_by_default."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java", [
    ('''        "clipboard.read", "terminal.environment", "editor.provide");''',
     '''        "clipboard.read", "terminal.environment", "editor.provide", "presentation.provide");
    /** The capability that goes with the {@code presentation} key, and only with it. */
    static final String PRESENTATION_PROVIDE = "presentation.provide";
    private static final java.util.regex.Pattern SEGMENT = java.util.regex.Pattern.compile("[a-z0-9_.-]+");''', 1),
    ('''        "subcommands");''', '''        "subcommands", "presentation", "themes", "enabled_by_default");''', 1),
    ('''        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id),
            icon(text(toml, "icon", false), id), homepage(text(toml, "homepage", false), id), iconHue(toml, id), iconSelectedInk(toml, id), subcommands(toml));''',
     '''        String presentation = text(toml, "presentation", false);
        if (!presentation.isEmpty()) path(presentation, "presentation");
        boolean provides = capabilities.contains(PRESENTATION_PROVIDE);
        if (!presentation.isEmpty() && !provides) throw new InvalidDescriptor("presentation needs the " + PRESENTATION_PROVIDE + " capability");
        if (provides && presentation.isEmpty()) throw new InvalidDescriptor("capabilities: " + PRESENTATION_PROVIDE + " needs the presentation key");
        return new PluginDescriptor(id, name, version, entry, sdk, text(toml, "description", false),
            text(toml, "vendor", false), capabilities, exports, requires(toml, id),
            icon(text(toml, "icon", false), id), homepage(text(toml, "homepage", false), id), iconHue(toml, id), iconSelectedInk(toml, id), subcommands(toml),
            presentation, themes(toml), flag(toml, "enabled_by_default", true));''', 1),
    ('''    private static List<PluginDescriptor.Subcommand> subcommands(TomlTable toml) throws InvalidDescriptor {''',
     '''    /**
     * A jar path as the descriptor and kit files name them: {@code /}-separated segments of lowercase letters, digits,
     * {@code _}, {@code .} and {@code -}, none of them {@code .} or {@code ..}, with no leading or trailing {@code /}.
     */
    static boolean jarPath(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")) return false;
        for (String segment : path.split("/", -1))
            if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }

    private static void path(String path, String key) throws InvalidDescriptor {
        if (!jarPath(path)) throw new InvalidDescriptor(key + ": " + path
            + " must be a jar path of lowercase letters, digits, /, _, . and -, without . or .. segments or a leading /");
    }

    /** Jasper theme files in the plugin's jars, each named after its theme's id. */
    private static List<String> themes(TomlTable toml) throws InvalidDescriptor {
        Object value = toml.get(List.of("themes"));
        if (value == null) return List.of();
        if (!(value instanceof TomlArray array)) throw new InvalidDescriptor("themes must be an array of strings");
        List<String> result = new ArrayList<>();
        Set<String> ids = new LinkedHashSet<>();
        for (int i = 0; i < array.size(); i++) {
            if (!(array.get(i) instanceof String path)) throw new InvalidDescriptor("themes must be an array of strings");
            path(path, "themes");
            String file = path.substring(path.lastIndexOf('/') + 1);
            String id = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : "";
            if (!dev.jasper.ui.theme.JasperTheme.validId(id))
                throw new InvalidDescriptor("themes: " + path + " must name <id>.json, the id lowercase letters, digits and dashes");
            if (!ids.add(id)) throw new InvalidDescriptor("themes names the theme " + id + " twice");
            result.add(path);
        }
        return result;
    }

    private static boolean flag(TomlTable toml, String key, boolean fallback) throws InvalidDescriptor {
        Object value = toml.get(List.of(key));
        if (value == null) return fallback;
        if (!(value instanceof Boolean flag)) throw new InvalidDescriptor(key + " must be a boolean");
        return flag;
    }

    private static List<PluginDescriptor.Subcommand> subcommands(TomlTable toml) throws InvalidDescriptor {''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java", [
    ('''/** A validated {@code plugin.toml}. */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                        dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands) {
''', '''/**
 * A validated {@code plugin.toml}. {@code presentation} is the jar path of the plugin's kit file ({@code ""} for none),
 * {@code themes} the jar paths of its Jasper theme files, and {@code enabledByDefault} false keeps a bundled plugin off
 * until the user turns it on.
 */
record PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                        String description, String vendor, Set<String> capabilities, Set<String> exports,
                        List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                        dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands,
                        String presentation, List<String> themes, boolean enabledByDefault) {
    /** The form that predates presentations: no kit, no themes, on by default. */
    PluginDescriptor(String id, String name, Version version, String entry, VersionRange sdk,
                     String description, String vendor, Set<String> capabilities, Set<String> exports,
                     List<Requirement> requires, String icon, String homepage, dev.jasper.ui.model.Nodes.Hue iconHue,
                     dev.jasper.ui.icons.IdentityArtwork.SelectedInk iconSelectedInk, List<Subcommand> subcommands) {
        this(id, name, version, entry, sdk, description, vendor, capabilities, exports, requires, icon, homepage, iconHue,
            iconSelectedInk, subcommands, "", List.of(), true);
    }
''', 1),
    ('''        subcommands = List.copyOf(subcommands);
    }
''', '''        subcommands = List.copyOf(subcommands);
        java.util.Objects.requireNonNull(presentation, "presentation");
        themes = List.copyOf(themes);
    }

    /** Whether the plugin provides a presentation. */
    boolean declaresPresentation() { return !presentation.isEmpty(); }

    /** Each declared theme's id: its file name without {@code .json}, in declared order. */
    List<String> themeIds() {
        return themes.stream().map(path -> path.substring(path.lastIndexOf('/') + 1, path.length() - ".json".length())).toList();
    }
''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java", [
    ('''            case "editor.provide" -> "Open the files Jasper opens for editing";''',
     '''            case "editor.provide" -> "Open the files Jasper opens for editing";
            case "presentation.provide" -> "Change Jasper's window chrome and controls after a restart";''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*'`
Expected: PASS, including the five new `DescriptorParserTest` cases and every existing plugin and manager test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins/DescriptorParser.java \
  jasper-app/src/main/java/dev/jasper/app/plugins/PluginDescriptor.java \
  jasper-app/src/main/java/dev/jasper/app/pluginmanager/Capabilities.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/DescriptorParserTest.java \
  jasper-app/src/test/java/dev/jasper/app/pluginmanager/CapabilityDescriptionsTest.java
git commit -F - <<'EOF'
Presentation provider: the descriptor keys and the capability

plugin.toml gains presentation (a jar path to a kit file), themes (Jasper
theme files) and enabled_by_default. presentation.provide joins the host's
capabilities, legal only with the presentation key and the key only with
it, and the consent text reads "Change Jasper's window chrome and controls
after a restart".

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Bundled but off by default, and the opt-in

The effective-enabled rule moves into `PluginStateStore.effective` (spec 4.3) and every reader uses it: admission, the
catalog row (so the card is unchecked and the details button reads "Enable") and `PluginAdmin.setEnabled`. An opt-in is
stored as `opt_in = true`, so it never equals `Entry.DEFAULT`, and the three prune paths keep it.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginStateStore.java:34-37,62,85-109,145-156`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginResolver.java:65-77`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java:80-95`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginAdmin.java:51-58`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginInstaller.java:140-149`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginMaintenance.java:126-140`
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreOptInTest.java`

**Interfaces:**
- Consumes: `PluginDescriptor.enabledByDefault()` (Task 1).
- Produces: `PluginStateStore.Entry(boolean enabled, Set<String> consented, boolean remove, boolean optIn)` with the
  3-argument convenience (`optIn` false) and `DEFAULT = (true, {}, false, false)`;
  `static boolean PluginStateStore.effective(Entry entryOrNull, PluginCandidate candidate)`;
  `static UnaryOperator<Map<String, Entry>> PluginStateStore.enabling(String id, boolean enabled, boolean optIn)`;
  reasons `"off by default"` (DISABLED) and `"not reviewed yet"` for a USER copy with an opt-in entry;
  `LookFixtures.plugin(Path root, String id, String descriptor, Map<String, String> resources)`,
  `LookFixtures.descriptor(String id, String name)`, `LookFixtures.provider(String kitPath)`, `LookFixtures.KIT`.

- [ ] **Step 1: Write the fixtures and the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java`:

```java
package dev.jasper.app.testsupport;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

/** Plugin folders on disk for presentation tests: one jar with a descriptor and text resources, and no classes. */
public final class LookFixtures {
    /** A kit file that names only its format and base. */
    public static final String KIT = "kit = 1\nbase = \"default\"\n";

    private LookFixtures() { }

    /** The required descriptor keys of a plugin on SDK 0.18. */
    public static String descriptor(String id, String name) {
        return "id = \"" + id + "\"\nname = \"" + name + "\"\nversion = \"0.1.0\"\nentry = \"" + id + ".Main\"\n"
            + "sdk = \">=0.18.0, <0.19\"\n";
    }

    /** The lines that make a descriptor a presentation provider with its kit at {@code kitPath}. */
    public static String provider(String kitPath) {
        return "capabilities = [\"presentation.provide\"]\npresentation = \"" + kitPath + "\"\n";
    }

    /** {@code <root>/<id>/jars/<id>.jar} holding {@code descriptor} as plugin.toml and each resource by its jar path. */
    public static Path plugin(Path root, String id, String descriptor, Map<String, String> resources) throws IOException {
        Path jars = root.resolve(id).resolve("jars");
        Path jar = PluginJars.build(jars, id + ".jar", descriptor, Map.of(), List.of());
        for (var resource : new java.util.TreeMap<>(resources).entrySet()) PluginJars.addResource(jar, resource.getKey(), resource.getValue());
        return root.resolve(id);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreOptInTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.3: a bundled plugin can be off by default, and the user's opt-in survives every prune path. */
class PluginStateStoreOptInTest {
    private static final String ID = "dev.example.look";
    private static final Version SDK = Version.parse("0.18.0");
    @TempDir Path home;

    private PluginStateStore store() { return new PluginStateStore(home.resolve("plugins.toml"), home.resolve("plugins.lock"), Duration.ofSeconds(2)); }

    private static PluginCandidate candidate(PluginCandidate.Origin origin, boolean enabledByDefault, Set<String> capabilities) {
        var descriptor = new PluginDescriptor(ID, "Look", Version.parse("0.1.0"), "x.Main", VersionRange.parse(">=0.18.0, <0.19"),
            "", "", capabilities, Set.of(), List.of(), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, List.of(), "", List.of(), enabledByDefault);
        return new PluginCandidate(descriptor, Path.of(ID), List.of(), origin);
    }

    private PluginAdmin admin(Path bundled) {
        var options = new PluginRuntime.Options(bundled, home.resolve("plugins"), null, false, home.resolve("plugins.toml"), home.resolve("plugins.lock"));
        return new PluginAdmin(options, SDK, () -> new PluginCatalog.Launch(List.of(), List.of(), Map.of(), false), id -> 0, Duration.ofSeconds(2));
    }

    @Test void theEffectiveRuleKeepsOnlyABundledOffByDefaultPluginWithoutAnEntryOff() {
        var off = candidate(PluginCandidate.Origin.BUNDLED, false, Set.of());
        assertThat(PluginStateStore.effective(null, off)).isFalse();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.BUNDLED, true, Set.of()))).isTrue();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.DEV, false, Set.of()))).as("DEV ignores it").isTrue();
        assertThat(PluginStateStore.effective(null, candidate(PluginCandidate.Origin.USER, false, Set.of()))).as("USER ignores it").isTrue();
        assertThat(PluginStateStore.effective(new PluginStateStore.Entry(true, Set.of(), false, true), off)).isTrue();
        assertThat(PluginStateStore.effective(new PluginStateStore.Entry(false, Set.of(), false), candidate(PluginCandidate.Origin.BUNDLED, true, Set.of()))).isFalse();
        var resolution = PluginResolver.resolve(List.of(off), Map.of(), SDK, false);
        assertThat(resolution.load()).isEmpty();
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.DISABLED);
            assertThat(status.reason()).isEqualTo("off by default");
        });
    }

    @Test void oneClickWritesTheOptInTheRowTurnsOnAndDisablingClearsIt() throws Exception {
        Path bundled = home.resolve("bundled");
        LookFixtures.plugin(bundled, ID, LookFixtures.descriptor(ID, "Look") + "enabled_by_default = false\n", Map.of());
        var admin = admin(bundled);
        var before = admin.snapshot().rows().stream().filter(row -> row.id().equals(ID)).findFirst().orElseThrow();
        assertThat(before.enabled()).as("unchecked, and the details button reads Enable").isFalse();
        assertThat(before.canToggle()).isTrue();
        var after = admin.setEnabled(ID, true);
        assertThat(Files.readString(home.resolve("plugins.toml"))).contains("[plugins.\"" + ID + "\"]\nenabled = true\nconsented = []\nremove = false\nopt_in = true\n");
        assertThat(after.rows().stream().filter(row -> row.id().equals(ID)).findFirst().orElseThrow().enabled()).isTrue();
        assertThat(after.restartNeeded()).as("the existing restart banner").isTrue();
        admin.setEnabled(ID, false);
        assertThat(store().read()).containsEntry(ID, new PluginStateStore.Entry(false, Set.of(), false, false));
    }

    @Test void theOptInSurvivesRemoveFalseDiscardPendingAndThePendingRemoval() throws Exception {
        var store = store();
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of(), false, true)); return state; });
        store.transact(PluginStateStore.removing(ID, false));
        assertThat(store.read()).as("remove(id, false) drops only DEFAULT entries").containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));

        Path user = home.resolve("plugins");
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), true, true)); return state; });
        PluginInstaller.discardPending(ID, user, store);
        assertThat(store.read()).as("discardPending keeps the opt-in and clears consent and the mark")
            .containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));

        LookFixtures.plugin(user, ID, LookFixtures.descriptor(ID, "Look"), Map.of());
        store.transact(state -> { state.put(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), true, true)); return state; });
        PluginMaintenance.apply(user, store, null, SDK);
        assertThat(Files.exists(user.resolve(ID).resolve("jars"))).as("the USER copy is removed").isFalse();
        assertThat(store.read()).containsEntry(ID, new PluginStateStore.Entry(true, Set.of(), false, true));
    }

    @Test void aUserCopyWithAnOptInEntryNeedsReviewAndConsentReplacesTheOptIn() throws Exception {
        var entry = new PluginStateStore.Entry(true, Set.of("terminal.observe"), false, true);
        var resolution = PluginResolver.resolve(List.of(candidate(PluginCandidate.Origin.USER, true, Set.of("terminal.observe"))),
            Map.of(ID, entry), SDK, false);
        assertThat(resolution.rejected()).singleElement().satisfies(status -> {
            assertThat(status.state()).isEqualTo(PluginStatus.State.NEEDS_CONSENT);
            assertThat(status.reason()).isEqualTo("not reviewed yet");
        });
        var state = new TreeMap<String, PluginStateStore.Entry>(Map.of(ID, entry));
        PluginStateStore.consenting(ID, Set.of("terminal.observe")).apply(state);
        assertThat(state).as("a Plugins-page install commits consent, which replaces the opt-in")
            .containsEntry(ID, new PluginStateStore.Entry(true, Set.of("terminal.observe"), false, false));
        var admin = admin(home.resolve("bundled"));
        LookFixtures.plugin(home.resolve("plugins"), ID, LookFixtures.descriptor(ID, "Look"), Map.of());
        store().transact(current -> { current.put(ID, entry); return current; });
        assertThatThrownBy(() -> admin.setEnabled(ID, false)).hasMessageContaining("Review Look");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginStateStoreOptInTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: method effective" and the four-argument `Entry`
constructor "cannot be applied".

- [ ] **Step 3: Implement the rule, the opt-in and the prune paths**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 2 step 3: the effective rule and the opt-in."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginStateStore.java", [
    ('''    record Entry(boolean enabled, Set<String> consented, boolean remove) {
        static final Entry DEFAULT = new Entry(true, Set.of(), false);
        Entry { consented = Set.copyOf(consented); }
    }''',
     '''    /**
     * One plugin's saved state. {@code optIn} records that the user turned on a bundled plugin that is off by default
     * ({@code opt_in = true}); only enabling such a plugin writes it, and any write that disables clears it.
     */
    record Entry(boolean enabled, Set<String> consented, boolean remove, boolean optIn) {
        static final Entry DEFAULT = new Entry(true, Set.of(), false, false);
        Entry { consented = Set.copyOf(consented); }
        Entry(boolean enabled, Set<String> consented, boolean remove) { this(enabled, consented, remove, false); }
    }

    /**
     * The one effective-enabled rule (spec 4.3): enabled unless the entry says disabled, or there is no entry and the
     * candidate is a bundled plugin whose descriptor says {@code enabled_by_default = false}.
     */
    static boolean effective(Entry entryOrNull, PluginCandidate candidate) {
        if (entryOrNull != null) return entryOrNull.enabled();
        return candidate.origin() != PluginCandidate.Origin.BUNDLED || candidate.descriptor().enabledByDefault();
    }''', 1),
    ('''            result.put(id, new Entry(flag(entry, "enabled", true, id), strings(entry, id), flag(entry, "remove", false, id)));''',
     '''            result.put(id, new Entry(flag(entry, "enabled", true, id), strings(entry, id), flag(entry, "remove", false, id),
                flag(entry, "opt_in", false, id)));''', 1),
    ('''    /** Enables or disables, keeping consent and a pending removal. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            state.put(id, new Entry(enabled, now.consented(), now.remove()));
            return state;
        };
    }''',
     '''    /** Enables or disables, keeping consent and a pending removal; disabling clears an opt-in. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled) { return enabling(id, enabled, false); }

    /** As {@link #enabling(String, boolean)}; {@code optIn} records that this enables a bundled plugin that is off by default. */
    static UnaryOperator<Map<String, Entry>> enabling(String id, boolean enabled, boolean optIn) {
        return state -> {
            Entry now = state.getOrDefault(id, Entry.DEFAULT);
            state.put(id, new Entry(enabled, now.consented(), now.remove(), enabled && (optIn || now.optIn())));
            return state;
        };
    }''', 1),
    ('''            Entry next = new Entry(now.enabled(), now.consented(), remove);''',
     '''            Entry next = new Entry(now.enabled(), now.consented(), remove, now.optIn());''', 1),
    ('''                .append("remove = ").append(entry.remove()).append('\\n');''',
     '''                .append("remove = ").append(entry.remove()).append('\\n');
            if (entry.optIn()) text.append("opt_in = true\\n");''', 1)])
edit(P + "PluginResolver.java", [
    ('''        if (entry != null && !entry.enabled()) return PluginStatus.of(candidate, PluginStatus.State.DISABLED, "disabled by the user");
        if (candidate.origin() != PluginCandidate.Origin.USER) return null;
        if (entry == null) return PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "not reviewed yet");''',
     '''        if (!PluginStateStore.effective(entry, candidate))
            return PluginStatus.of(candidate, PluginStatus.State.DISABLED, entry == null ? "off by default" : "disabled by the user");
        if (candidate.origin() != PluginCandidate.Origin.USER) return null;
        // Only bundled plugins are ever opted in: a user copy behind an opt-in entry has not been reviewed.
        if (entry == null || entry.optIn()) return PluginStatus.of(candidate, PluginStatus.State.NEEDS_CONSENT, "not reviewed yet");''', 1)])
edit(P + "PluginCatalog.java", [
    ('''            else if (entry != null) unconsented.removeAll(entry.consented());
            boolean needsConsent = user && !pendingRemoval && (entry == null || !unconsented.isEmpty());''',
     '''            else if (entry != null && !entry.optIn()) unconsented.removeAll(entry.consented());
            boolean reviewed = entry != null && !entry.optIn();
            boolean needsConsent = user && !pendingRemoval && (!reviewed || !unconsented.isEmpty());''', 1),
    ('''                entry == null || entry.enabled(), needsConsent,
                !pendingRemoval && !(user && entry == null), user, pendingRemoval, pendingInstall,''',
     '''                PluginStateStore.effective(entry, shown), needsConsent,
                !pendingRemoval && !(user && !reviewed), user, pendingRemoval, pendingInstall,''', 1)])
edit(P + "PluginAdmin.java", [
    ('''        if (subject.origin() == PluginCandidate.Origin.USER && !disk.state().containsKey(id))
            throw new IOException("Review " + subject.descriptor().name() + " before enabling or disabling it");
        store.transact(PluginStateStore.enabling(id, enabled));''',
     '''        PluginStateStore.Entry entry = disk.state().get(id);
        if (subject.origin() == PluginCandidate.Origin.USER && (entry == null || entry.optIn()))
            throw new IOException("Review " + subject.descriptor().name() + " before enabling or disabling it");
        boolean optIn = enabled && subject.origin() == PluginCandidate.Origin.BUNDLED && !subject.descriptor().enabledByDefault();
        store.transact(PluginStateStore.enabling(id, enabled, optIn));''', 1)])
edit(P + "PluginInstaller.java", [
    ('''                if (!Files.isDirectory(userDirectory.resolve(id))) state.remove(id);''',
     '''                if (!Files.isDirectory(userDirectory.resolve(id))) {
                    PluginStateStore.Entry entry = state.get(id);
                    // An opted-in bundled plugin stays on; only the install's consent and removal mark go.
                    if (entry != null && entry.optIn()) state.put(id, new PluginStateStore.Entry(true, java.util.Set.of(), false, true));
                    else state.remove(id);
                }''', 1)])
edit(P + "PluginMaintenance.java", [
    ('''                retire(userDirectory.resolve(id));
                state.remove(id);''',
     '''                retire(userDirectory.resolve(id));
                // Removing a user copy keeps the bundled plugin's opt-in.
                if (item.getValue().optIn()) state.put(id, new PluginStateStore.Entry(true, java.util.Set.of(), false, true));
                else state.remove(id);''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*'`
Expected: PASS: the four new cases and every existing resolver, catalog, admin, installer, maintenance, store and manager
test (an existing `plugins.toml` without `opt_in` renders byte-identically).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginStateStoreOptInTest.java
git commit -F - <<'EOF'
Presentation provider: bundled plugins off by default, and the opt-in

PluginStateStore.effective is the one effective-enabled rule: a bundled
plugin with enabled_by_default = false is off until the user turns it on.
Admission, the catalog row and PluginAdmin.setEnabled use it, so the card
is unchecked and the details button reads Enable. Turning such a plugin on
writes opt_in = true, which disabling clears and consent replaces; remove,
discardPending and the pending removal keep it, and a user copy behind an
opt-in entry still needs review.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The kit file: `ChromeSpec`, `FontFile`, `DefaultSource` and `KitParser`

The new package `dev.jasper.app.presentation` reads a kit file into the jasper-ui `Kit` (from its base catalog, tuned by
the public tokens, the type base, the control variants and the colour slots), a `ChromeSpec`, the kit's setting defaults
(`DefaultSource`, in `config`) and its declared face files, or refuses it with one message naming the key (spec 5.1 to
5.7 and 5.9 without `[branding]`, which stays an unknown table until plan 11).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/presentation/package-info.java`, `ChromeSpec.java`, `FontFile.java`, `KitParser.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/FontFileTest.java`, `KitParserTest.java`; `jasper-app/src/test/java/dev/jasper/app/config/DefaultSourceTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (two rows), `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java:37-38,44,59,173-174`

**Interfaces:**
- Consumes: plan 4's `Kit.CLASSIC`, the 7-component `Kit`, `ControlVariants`, `ColorSlots`, `MetricTokens`,
  `DesignMetrics.with(MetricTokens)`, `DesignMetrics.Role.ROW_HEIGHT` and `FIELD_RADIUS`, `TypeScale.CLASSIC`
  (names in "Plan 4's names"), and in the tests 4a's `KitFixtures.enterpriseTokens()`; `CoreSettings.UI_THEME_LIGHT`,
  `UI_THEME_DARK`, `PALETTE_VISIBLE_ROWS`,
  `WINDOW_TOOLBAR_HEIGHT`; `JasperTheme.builtIn(String)`, `JasperTheme.has(boolean)`.
- Produces: `public record ChromeSpec(TitleBar, MenuBar, String windowTitle, ToolWindows, EditorTabs, StatusBar,
  PaletteKind, DialogKind, MotionKind)` with `DEFAULT` and `CLASSIC`;
  `public record FontFile(String path, byte[] bytes, String fullName, String family)` with `static FontFile read(String,
  byte[])` and `named()`; `public record DefaultSource(Map<Setting<?>, Object> overrides)` with `ALLOWED`, `CORE`,
  `<T> T value(Setting<T>)`; `KitParser.parse(byte[] bytes, KitParser.Context context)` returning
  `KitParser.Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped,
  KitParser.Faces faces)` or throwing `KitParser.Invalid`; `KitParser.Context(String id, String directory,
  KitParser.Files files, Map<String, Optional<JasperTheme>> themes)`; `@FunctionalInterface KitParser.Files {
  Optional<byte[]> read(String path, int maxBytes); }`; `KitParser.Faces(Optional<UiFaces> ui, Optional<DataFaces> data)`
  with `NONE` and `files()`; `KitParser.UiFaces(String family, FontFile regular, FontFile medium, FontFile semibold)`,
  `KitParser.DataFaces(String family, FontFile regular, FontFile medium)`; `KitParser.MAX_KIT_BYTES` (64 KiB),
  `MAX_FONT_BYTES` (2 MiB); `static boolean KitParser.path(String)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/presentation/FontFileTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.6 and fact 19: faces are named by a file's full name and checked by its typographic family, without AWT. */
class FontFileTest {
    /** A TrueType file holding only a name table with these Windows English records (name ID to text). */
    static byte[] synthetic(Map<Integer, String> names) {
        try {
            var strings = new ByteArrayOutputStream();
            var records = new ByteArrayOutputStream();
            var record = new DataOutputStream(records);
            for (var name : names.entrySet()) {
                byte[] text = name.getValue().getBytes(StandardCharsets.UTF_16BE);
                record.writeShort(3); record.writeShort(1); record.writeShort(0x409); record.writeShort(name.getKey());
                record.writeShort(text.length); record.writeShort(strings.size());
                strings.write(text);
            }
            var table = new ByteArrayOutputStream();
            var head = new DataOutputStream(table);
            head.writeShort(0); head.writeShort(names.size()); head.writeShort(6 + 12 * names.size());
            head.write(records.toByteArray()); head.write(strings.toByteArray());
            var file = new ByteArrayOutputStream();
            var out = new DataOutputStream(file);
            out.writeInt(0x00010000); out.writeShort(1); out.writeShort(16); out.writeShort(0); out.writeShort(0);
            out.writeInt(0x6E616D65); out.writeInt(0); out.writeInt(12 + 16); out.writeInt(table.size());
            out.write(table.toByteArray());
            return file.toByteArray();
        } catch (IOException impossible) { throw new java.io.UncheckedIOException(impossible); }
    }

    /** A face as IBM Plex's static files name theirs: full name and legacy family per weight, one typographic family. */
    static byte[] face(String fullName, String typographicFamily) {
        var names = new LinkedHashMap<Integer, String>();
        names.put(1, fullName); names.put(4, fullName);
        if (!fullName.equals(typographicFamily)) names.put(16, typographicFamily);
        return synthetic(names);
    }

    @Test void theFullNameNamesTheFaceAndTheTypographicFamilyIsTheFamily() {
        var medium = FontFile.read("fonts/plex-medium.ttf", face("IBM Plex Sans Medm", "IBM Plex Sans"));
        assertThat(medium.fullName()).isEqualTo("IBM Plex Sans Medm");
        assertThat(medium.family()).as("name ID 16 over the legacy family").isEqualTo("IBM Plex Sans");
        assertThat(medium.named()).isTrue();
        var regular = FontFile.read("fonts/plex-regular.ttf", face("IBM Plex Sans", "IBM Plex Sans"));
        assertThat(regular.family()).as("without ID 16, the family is ID 1").isEqualTo("IBM Plex Sans");
    }

    @Test void aFileWithoutAReadableNameTableNamesNothing() {
        for (byte[] bytes : new byte[][] {new byte[0], new byte[] {0, 1, 0, 0, 0, 9}, "not a font at all".getBytes(StandardCharsets.UTF_8),
                java.util.Arrays.copyOf(face("Truncated", "Truncated"), 40)}) {
            var file = FontFile.read("fonts/x.ttf", bytes);
            assertThat(file.named()).isFalse();
            assertThat(file.fullName()).isEmpty();
            assertThat(file.family()).isEmpty();
        }
    }

    @Test void filesCompareByContentAndCopyTheirBytes() {
        byte[] bytes = face("A", "A");
        var one = FontFile.read("fonts/a.ttf", bytes);
        bytes[0] = 9;
        assertThat(one).isEqualTo(FontFile.read("fonts/a.ttf", face("A", "A")));
        one.bytes()[0] = 9;
        assertThat(one.bytes()[0]).isZero();
        assertThat(one.toString()).doesNotContain("[B@");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/config/DefaultSourceTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.8: a look defaults four settings, each value one its setting accepts. */
class DefaultSourceTest {
    @Test void theCoreSourceGivesTheCoreDefaultsAndAnOverrideGivesItsValue() {
        assertThat(DefaultSource.CORE.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(CoreSettings.PALETTE_VISIBLE_ROWS.defaultValue());
        var kit = new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 8, CoreSettings.UI_THEME_DARK, "catppuccin-mocha"));
        assertThat(kit.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        assertThat(kit.value(CoreSettings.UI_THEME_DARK)).isEqualTo("catppuccin-mocha");
        assertThat(kit.value(CoreSettings.UI_THEME_LIGHT)).isEqualTo(CoreSettings.UI_THEME_LIGHT.defaultValue());
        assertThat(DefaultSource.ALLOWED).containsExactlyInAnyOrder(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK,
            CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.WINDOW_TOOLBAR_HEIGHT);
    }

    @Test void anotherSettingOrAValueTheSettingRejectsIsRefused() {
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.UI_FONT_SIZE, 12f)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("ui.font.size");
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 40)))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("palette.visible_rows");
        assertThatThrownBy(() -> new DefaultSource(Map.of(CoreSettings.WINDOW_TOOLBAR_HEIGHT, "34")))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("window.toolbar_height");
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import org.junit.jupiter.api.Test;

/** Spec 5.1 to 5.9: the kit file, its values and every way it can be invalid. */
class KitParserTest {
    static final String PLEX_SANS = "IBM Plex Sans", PLEX_MONO = "IBM Plex Mono";
    /** Spec 5.1's shipped kit without [branding] (plan 11's). */
    static final String FULL = """
        kit = 1
        base = "classic"

        [chrome]
        title_bar = "native"
        menu_bar = "window"
        window_title = "Workstation — {session} — {directory}"
        tool_windows = "stripes"
        editor_tabs = "classic"
        status_bar = "cells"
        palette = "run_window"
        dialogs = "classic"
        motion = "none"

        [controls]
        toggle = "checkbox"
        exclusive = "radios"
        tabs = "folder"
        group = "titled_box"
        form_label = "colon"
        empty = "text"
        table = "classic"
        tree = "classic"
        stepper = "spinner"
        ordered_list = "side_column"
        buttons = "classic"
        tag = "boxed"
        route = "edged"
        prompt = "plain"

        [metrics]
        control_height = 22
        row_height = 22
        control_radius = 3
        field_radius = 2
        group_radius = 3
        focus_ring = 2
        tool_window_header = 24
        group_padding = 12
        dependent_indent = 20
        navigation_width = 230
        status_dot = 7

        [type]
        base = 12

        [type.ui]
        family = "IBM Plex Sans"
        regular = "fonts/ibm-plex-sans-regular.ttf"
        medium = "fonts/ibm-plex-sans-medium.ttf"
        semibold = "fonts/ibm-plex-sans-semibold.ttf"

        [type.data]
        family = "IBM Plex Mono"
        regular = "fonts/ibm-plex-mono-regular.ttf"
        medium = "fonts/ibm-plex-mono-medium.ttf"

        [colors]
        dialog_surface = "surface.app"
        navigation_surface = "surface.card"

        [defaults]
        "ui.theme.light" = "look"
        "ui.theme.dark" = "look"
        "palette.visible_rows" = 8
        "window.toolbar_height" = 34
        """;

    /** The fonts FULL names, under the kit's folder "presentation/". */
    static Map<String, byte[]> fonts() {
        var files = new HashMap<String, byte[]>();
        files.put("presentation/fonts/ibm-plex-sans-regular.ttf", FontFileTest.face(PLEX_SANS, PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-sans-medium.ttf", FontFileTest.face("IBM Plex Sans Medm", PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-sans-semibold.ttf", FontFileTest.face("IBM Plex Sans SmBld", PLEX_SANS));
        files.put("presentation/fonts/ibm-plex-mono-regular.ttf", FontFileTest.face(PLEX_MONO, PLEX_MONO));
        files.put("presentation/fonts/ibm-plex-mono-medium.ttf", FontFileTest.face("IBM Plex Mono Medm", PLEX_MONO));
        return files;
    }

    /** A theme with both variants, declared by the provider as "look". */
    static JasperTheme look() { return JasperTheme.bundled(); }

    static KitParser.Context context(Map<String, byte[]> files, Map<String, Optional<JasperTheme>> themes) {
        return new KitParser.Context("dev.example.look", "presentation/", (path, max) -> {
            byte[] bytes = files.get(path);
            return bytes == null ? Optional.empty() : Optional.of(java.util.Arrays.copyOf(bytes, Math.min(bytes.length, max + 1)));
        }, themes);
    }

    static KitParser.Parsed parse(String text) throws KitParser.Invalid {
        return KitParser.parse(text.getBytes(StandardCharsets.UTF_8), context(fonts(), Map.of("look", Optional.of(look()))));
    }

    static String problem(String text) {
        try { parse(text); throw new AssertionError("valid: " + text); }
        catch (KitParser.Invalid invalid) { return invalid.getMessage(); }
    }

    @Test void aMinimalKitIsItsBaseUnderTheProvidersId() throws Exception {
        var parsed = parse("kit = 1\nbase = \"default\"\n");
        assertThat(parsed.kit().id()).isEqualTo("dev.example.look");
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.DEFAULT.metrics());
        assertThat(parsed.kit().type()).isEqualTo(Kit.DEFAULT.type());
        assertThat(parsed.kit().faces()).isEqualTo(Kit.DEFAULT.faces());
        assertThat(parsed.kit().controls()).isEqualTo(Kit.DEFAULT.controls());
        assertThat(parsed.kit().colors()).isEqualTo(Kit.DEFAULT.colors());
        assertThat(parsed.kit().uiKeys()).isEqualTo(Kit.DEFAULT.uiKeys());
        assertThat(parsed.chrome()).isEqualTo(ChromeSpec.DEFAULT);
        assertThat(parsed.classicBase()).isFalse();
        assertThat(parsed.defaults()).isEqualTo(dev.jasper.app.config.DefaultSource.CORE);
        assertThat(parsed.faces()).isEqualTo(KitParser.Faces.NONE);
        assertThat(parsed.dropped()).isEmpty();
    }

    @Test void theClassicBaseStartsFromTheClassicCatalogAndChrome() throws Exception {
        var parsed = parse("kit = 1\nbase = \"classic\"\n");
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.CLASSIC.metrics());
        assertThat(parsed.kit().type()).isEqualTo(Kit.CLASSIC.type());
        assertThat(parsed.kit().controls()).isEqualTo(Kit.CLASSIC.controls());
        assertThat(parsed.kit().colors()).isEqualTo(Kit.CLASSIC.colors());
        assertThat(parsed.kit().uiKeys()).isEqualTo(Kit.CLASSIC.uiKeys());
        assertThat(parsed.chrome()).isEqualTo(ChromeSpec.CLASSIC);
        assertThat(parsed.chrome().windowTitle()).isEqualTo("{context}");
        assertThat(parsed.classicBase()).isTrue();
    }

    @Test void theShippedShapeParsesIntoEveryValueItNames() throws Exception {
        var parsed = parse(FULL);
        assertThat(parsed.chrome()).isEqualTo(new ChromeSpec(ChromeSpec.TitleBar.NATIVE, ChromeSpec.MenuBar.WINDOW,
            "Workstation — {session} — {directory}", ChromeSpec.ToolWindows.STRIPES, ChromeSpec.EditorTabs.CLASSIC,
            ChromeSpec.StatusBar.CELLS, ChromeSpec.PaletteKind.RUN_WINDOW, ChromeSpec.DialogKind.CLASSIC, ChromeSpec.MotionKind.NONE));
        assertThat(parsed.kit().controls()).isEqualTo(new ControlVariants(ControlVariants.Toggle.CHECKBOX, ControlVariants.Exclusive.RADIOS,
            ControlVariants.Tabs.FOLDER, ControlVariants.Group.TITLED_BOX, ControlVariants.FormLabel.COLON, ControlVariants.Empty.TEXT,
            ControlVariants.Table.CLASSIC, ControlVariants.Tree.CLASSIC, ControlVariants.Stepper.SPINNER, ControlVariants.OrderedList.SIDE_COLUMN,
            ControlVariants.Buttons.CLASSIC, ControlVariants.Tag.BOXED, ControlVariants.Route.EDGED, ControlVariants.Prompt.PLAIN));
        assertThat(parsed.kit().metrics()).isEqualTo(Kit.CLASSIC.metrics().with(KitFixtures.enterpriseTokens()));
        assertThat(parsed.kit().type()).isEqualTo(Kit.CLASSIC.type().withBody(12));
        assertThat(parsed.kit().colors().dialogSurface()).isEqualTo(SemanticColor.SURFACE_APP);
        assertThat(parsed.kit().colors().navigationSurface()).isEqualTo(SemanticColor.SURFACE_CARD);
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("look");
        assertThat(parsed.defaults().value(CoreSettings.UI_THEME_DARK)).isEqualTo("look");
        assertThat(parsed.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        assertThat(parsed.defaults().value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)).isEqualTo(34);
        var ui = parsed.faces().ui().orElseThrow();
        assertThat(ui.family()).isEqualTo(PLEX_SANS);
        assertThat(List.of(ui.regular().fullName(), ui.medium().fullName(), ui.semibold().fullName()))
            .containsExactly(PLEX_SANS, "IBM Plex Sans Medm", "IBM Plex Sans SmBld");
        assertThat(parsed.faces().data().orElseThrow().medium().family()).isEqualTo(PLEX_MONO);
        assertThat(parsed.faces().files()).extracting(FontFile::path).containsExactly("fonts/ibm-plex-sans-regular.ttf",
            "fonts/ibm-plex-sans-medium.ttf", "fonts/ibm-plex-sans-semibold.ttf", "fonts/ibm-plex-mono-regular.ttf", "fonts/ibm-plex-mono-medium.ttf");
    }

    /** Plan 4a's MetricTokens.of reads each token's first role, so a token left out keeps its base's value exactly. */
    @Test void aTokenLeftOutKeepsItsBasesValue() throws Exception {
        var metrics = parse("kit = 1\nbase = \"classic\"\n[metrics]\nrow_height = 22\n").kit().metrics();
        assertThat(metrics.value(DesignMetrics.Role.FIELD_HEIGHT)).as("control_height left out").isEqualTo(22);
        assertThat(metrics.value(DesignMetrics.Role.BUTTON_HEIGHT)).isEqualTo(24);
        assertThat(metrics).isEqualTo(Kit.CLASSIC.metrics());
        var tall = parse("kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 26\n").kit().metrics();
        assertThat(MetricTokens.of(tall).value(MetricTokens.Token.ROW_HEIGHT)).isEqualTo(26);
        assertThat(MetricTokens.of(tall).value(MetricTokens.Token.CONTROL_HEIGHT))
            .isEqualTo(MetricTokens.of(Kit.DEFAULT.metrics()).value(MetricTokens.Token.CONTROL_HEIGHT));
    }

    @Test void everyChromeAndControlPointTakesBothItsValues() throws Exception {
        assertThat(parse("kit = 1\nbase = \"default\"\n[chrome]\ntitle_bar = \"native\"\n").chrome().titleBar()).isEqualTo(ChromeSpec.TitleBar.NATIVE);
        assertThat(parse("kit = 1\nbase = \"classic\"\n[chrome]\ntitle_bar = \"unified\"\nmenu_bar = \"system\"\n").chrome().menuBar())
            .isEqualTo(ChromeSpec.MenuBar.SYSTEM);
        String[][] controls = {{"toggle", "switch", "checkbox"}, {"exclusive", "segments", "radios"}, {"tabs", "segments", "folder"},
            {"group", "card", "titled_box"}, {"form_label", "plain", "colon"}, {"empty", "tile", "text"}, {"table", "plain", "classic"},
            {"tree", "plain", "classic"}, {"stepper", "stepper", "spinner"}, {"ordered_list", "inline", "side_column"},
            {"buttons", "raised", "classic"}, {"tag", "tint", "boxed"}, {"route", "tiles", "edged"}, {"prompt", "tile", "plain"}};
        for (String[] point : controls)
            for (int value = 1; value <= 2; value++) {
                var kit = parse("kit = 1\nbase = \"" + (value == 1 ? "classic" : "default") + "\"\n[controls]\n" + point[0] + " = \"" + point[value] + "\"\n").kit();
                assertThat(kit.controls().toString().toLowerCase(java.util.Locale.ROOT)).as(point[0]).contains("=" + point[value]);
            }
    }

    @Test void aDefaultsThemeMayBeBuiltInOrDeclaredAndADeclaredThemeThatDidNotLoadIsDropped() throws Exception {
        var builtIn = parse("kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.light\" = \"catppuccin-latte\"\n");
        assertThat(builtIn.defaults().value(CoreSettings.UI_THEME_LIGHT)).isEqualTo("catppuccin-latte");
        var dropped = KitParser.parse("kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n\"palette.visible_rows\" = 3\n"
            .getBytes(StandardCharsets.UTF_8), context(fonts(), Map.of("look", Optional.empty())));
        assertThat(dropped.dropped()).containsExactly("ui.theme.dark");
        assertThat(dropped.defaults().value(CoreSettings.UI_THEME_DARK)).as("the core default applies").isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
        assertThat(dropped.defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(3);
    }

    @Test void everyInvalidKitGivesOneMessageNamingTheKey() {
        String head = "kit = 1\nbase = \"default\"\n";
        String ui = "[type.ui]\nfamily = \"F\"\nmedium = \"fonts/ibm-plex-sans-medium.ttf\"\nsemibold = \"fonts/ibm-plex-sans-semibold.ttf\"\n";
        var cases = new java.util.LinkedHashMap<String, String>();
        cases.put(head + "[branding]\nproduct = \"JasperOps\"\n", "[branding]: unknown table");
        cases.put(head + "colour = 1\n", "colour: unknown key");
        cases.put(head + "[chrome]\ntitle = \"x\"\n", "[chrome].title: unknown key");
        cases.put(head + "chrome = 1\n", "chrome: expected a table");
        cases.put(head + "[metrics]\nrow_height = \"22\"\n", "[metrics].row_height: expected an integer");
        cases.put(head + "[chrome]\ntitle_bar = \"glass\"\n", "[chrome].title_bar: \"glass\" is not one of unified, native");
        cases.put(head + "[controls]\ntoggle = 1\n", "[controls].toggle: expected a string");
        cases.put("kit = 2\nbase = \"default\"\n", "kit: 2 is not a format this Jasper reads; expected 1");
        cases.put("base = \"default\"\n", "kit: missing; a kit file begins with kit = 1");
        cases.put("kit = \"1\"\nbase = \"default\"\n", "kit: expected an integer");
        cases.put("kit = 1\n", "base: missing; use \"default\" or \"classic\"");
        cases.put("kit = 1\nbase = \"modern\"\n", "base: \"modern\" is not one of default, classic");
        cases.put(head + "[metrics]\nrow_height = 40\n", "[metrics].row_height: 40 is outside 18-28");
        cases.put(head + "[metrics]\ncontrol_height = 19\n", "[metrics].control_height: 19 is outside 20-28");
        cases.put(head + "[type]\nbase = 9\n", "[type].base: 9 is not a body size from 10 to 16");
        cases.put(head + "[type]\nbase = 12.5\n", "[type].base: expected an integer");
        cases.put(head + "[chrome]\nmenu_bar = \"window\"\n", "[chrome].menu_bar: \"window\" needs [chrome].title_bar = \"native\"");
        cases.put(head + "[defaults]\n\"ui.font.size\" = 12\n", "[defaults].\"ui.font.size\": not a setting a look may default; use "
            + "\"ui.theme.light\", \"ui.theme.dark\", \"palette.visible_rows\" or \"window.toolbar_height\"");
        cases.put(head + "[defaults]\n\"palette.visible_rows\" = 30\n", "[defaults].\"palette.visible_rows\": Use an integer from 1–20; using the default.");
        cases.put(head + "[defaults]\n\"window.toolbar_height\" = 70\n", "[defaults].\"window.toolbar_height\": Use an integer from 28–64; using 64.");
        cases.put(head + "[defaults]\n\"ui.theme.dark\" = \"nope\"\n",
            "[defaults].\"ui.theme.dark\": \"nope\" is neither a built-in theme nor one of this plugin's themes");
        cases.put(head + "[defaults]\n\"ui.theme.dark\" = \"catppuccin-latte\"\n", "[defaults].\"ui.theme.dark\": \"catppuccin-latte\" has no Dark variant");
        cases.put(head + ui + "regular = \"../ibm-plex-sans-regular.ttf\"\n",
            "[type.ui].regular: ../ibm-plex-sans-regular.ttf must be a lowercase path inside the kit's folder, without . or .. segments");
        cases.put(head + ui + "regular = \"fonts/missing.ttf\"\n", "[type.ui].regular: fonts/missing.ttf is not in the plugin's jars");
        cases.put(head + ui + "regular = \"fonts/face.woff2\"\n", "[type.ui].regular: fonts/face.woff2 is not a .ttf or .otf file");
        cases.put(head + ui, "[type.ui].regular: missing");
        cases.put(head + "[type.data]\nfamily = \" \"\nregular = \"fonts/ibm-plex-mono-regular.ttf\"\nmedium = \"fonts/ibm-plex-mono-medium.ttf\"\n",
            "[type.data].family: expected a family name of 1 to 64 characters");
        cases.put(head + "[chrome]\nwindow_title = \"{host} — {directory}\"\n",
            "[chrome].window_title: unknown token {host}; use {context}, {session} and {directory}");
        cases.put(head + "[chrome]\nwindow_title = \"Jasper {session\"\n",
            "[chrome].window_title: a brace that is not part of {context}, {session} or {directory}");
        cases.put(head + "[colors]\ndialog_surface = \"surface.card\"\n", "[colors].dialog_surface: \"surface.card\" is not one of surface.dialog, surface.app");
        cases.put(head + "[chrome]\nwindow_title = \"#1E1F22\"\n", "[chrome].window_title: \"#1E1F22\" looks like a colour; colours come from the theme");
        cases.put(head + "# the accent is #2F65CA\n", "kit file: line 3: \"#2F65CA\" looks like a colour; colours come from the theme");
        cases.put(head + "[chrome\n", null);
        for (var item : cases.entrySet()) {
            String message = problem(item.getKey());
            if (item.getValue() == null) assertThat(message).as(item.getKey()).startsWith("kit file: not valid TOML");
            else assertThat(message).as(item.getKey()).isEqualTo(item.getValue());
        }
    }

    @Test void filesOverTheirLimitsAreRefused() throws Exception {
        byte[] large = ("kit = 1\nbase = \"default\"\n#" + "x".repeat(KitParser.MAX_KIT_BYTES)).getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> KitParser.parse(large, context(fonts(), Map.of()))).hasMessage("kit file: larger than 64 KiB");
        var files = fonts();
        files.put("presentation/fonts/ibm-plex-sans-regular.ttf", new byte[KitParser.MAX_FONT_BYTES + 1]);
        assertThatThrownBy(() -> KitParser.parse(FULL.getBytes(StandardCharsets.UTF_8), context(files, Map.of("look", Optional.of(look())))))
            .hasMessage("[type.ui].regular: fonts/ibm-plex-sans-regular.ttf is larger than 2 MiB");
        assertThatThrownBy(() -> KitParser.parse(new byte[] {(byte) 0xC3, 0x28}, context(fonts(), Map.of()))).hasMessage("kit file: not UTF-8 text");
    }

    /** Rulings 13: tomlj accepts very deep dotted tables; the parser reports one problem and never recurses through them. */
    @Test void deepNestingIsOneProblem() {
        String head = "kit = 1\nbase = \"default\"\n";
        assertThat(problem(head + "[" + "a.".repeat(20_000) + "b]\n")).isEqualTo("[a]: unknown table");
        assertThat(problem(head + "[chrome." + "a.".repeat(20_000) + "b]\n")).isEqualTo("[chrome].a: unknown key");
        assertThat(problem(head + "a = " + "[".repeat(30_000))).startsWith("kit file: not valid TOML");
    }

    @Test void fuzzedInputsOnlyEverProduceProblems() {
        var inputs = new ArrayList<byte[]>();
        byte[] full = FULL.getBytes(StandardCharsets.UTF_8);
        for (int end = 0; end < full.length; end += 7) inputs.add(java.util.Arrays.copyOf(full, end));
        var random = new Random(5);
        for (int i = 0; i < 200; i++) { byte[] noise = new byte[random.nextInt(2048)]; random.nextBytes(noise); inputs.add(noise); }
        inputs.add(("kit = 1\nbase = \"default\"\na = " + "[".repeat(30_000)).getBytes(StandardCharsets.UTF_8));
        inputs.add(("kit = 1\nbase = \"default\"\n[" + "a.".repeat(20_000) + "b]\n").getBytes(StandardCharsets.UTF_8));
        inputs.add(FULL.replace("fonts/ibm-plex-sans-regular.ttf", "../../plugin.toml").getBytes(StandardCharsets.UTF_8));
        inputs.add(FULL.replace("fonts/ibm-plex-sans-regular.ttf", "/etc/passwd").getBytes(StandardCharsets.UTF_8));
        inputs.add(new byte[KitParser.MAX_KIT_BYTES * 2]);
        for (byte[] input : inputs) {
            try { KitParser.parse(input, context(fonts(), Map.of("look", Optional.of(look())))); }
            catch (KitParser.Invalid expected) { assertThat(expected.getMessage()).isNotBlank(); }
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.config.DefaultSourceTest'`
Expected: FAIL; `compileTestJava` reports "package dev.jasper.app.presentation does not exist" (or "cannot find symbol"
for `KitParser`, `FontFile`, `ChromeSpec`) and "cannot find symbol: class DefaultSource".

- [ ] **Step 3: Write the package, `ChromeSpec`, `FontFile` and `DefaultSource`**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/package-info.java`:

```java
/**
 * The presentation a process runs with, chosen once before Swing (spec 4 and 9): {@link dev.jasper.app.presentation.KitParser}
 * reads a provider's kit file into the jasper-ui kit and a {@link dev.jasper.app.presentation.ChromeSpec},
 * {@link dev.jasper.app.presentation.PresentationFacts} gives each provider's digest and first problem,
 * {@link dev.jasper.app.presentation.AttemptStore} keeps the attempt record, and
 * {@link dev.jasper.app.presentation.Presentation} hands each consumer its projection. Pure values, bounded reads and
 * locked read-modify-writes; no Swing. ChromeSpec is referenced only in this package.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.config, dev.jasper.app.settings, dev.jasper.app.persistence,
 * dev.jasper.app.dock, dev.jasper.ui.kit, dev.jasper.ui.metrics, dev.jasper.ui.theme.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.presentation;
```

Create `jasper-app/src/main/java/dev/jasper/app/presentation/ChromeSpec.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The nine chrome variant points of a kit file's {@code [chrome]} table (spec 5.2): eight two-valued points and the
 * window title template. Referenced only in this package; consumers receive projections ({@link Projections}).
 *
 * @param titleBar {@code title_bar}
 * @param menuBar {@code menu_bar}; {@code window} needs a native title bar
 * @param windowTitle {@code window_title}, a template over {@code {context}}, {@code {session}} and {@code {directory}}
 * @param toolWindows {@code tool_windows}
 * @param editorTabs {@code editor_tabs}
 * @param statusBar {@code status_bar}
 * @param palette {@code palette}
 * @param dialogs {@code dialogs}
 * @param motion {@code motion}
 */
public record ChromeSpec(TitleBar titleBar, MenuBar menuBar, String windowTitle, ToolWindows toolWindows, EditorTabs editorTabs,
                         StatusBar statusBar, PaletteKind palette, DialogKind dialogs, MotionKind motion) {
    /** The unified title bar, or the operating system's. */
    public enum TitleBar { UNIFIED, NATIVE }
    /** The screen menu bar on macOS, or a menu row in each window. */
    public enum MenuBar { SYSTEM, WINDOW }
    /** Icon rails, or labelled stripes with a bottom strip. */
    public enum ToolWindows { RAILS, STRIPES }
    /** Equal-width tabs, or content-sized classic tabs. */
    public enum EditorTabs { EQUAL, CLASSIC }
    /** Slash-separated segments, or ruled cells. */
    public enum StatusBar { SEGMENTS, CELLS }
    /** The anchored palette card, or the Run window. */
    public enum PaletteKind { ANCHORED, RUN_WINDOW }
    /** Dialogs as each requests, or overlay and attached-sheet dialogs drawn in the window. */
    public enum DialogKind { AS_REQUESTED, CLASSIC }
    /** Motion as the settings say, or none. */
    public enum MotionKind { STANDARD, NONE }

    /** The default look. */
    public static final ChromeSpec DEFAULT = new ChromeSpec(TitleBar.UNIFIED, MenuBar.SYSTEM, "{context}", ToolWindows.RAILS,
        EditorTabs.EQUAL, StatusBar.SEGMENTS, PaletteKind.ANCHORED, DialogKind.AS_REQUESTED, MotionKind.STANDARD);
    /** The classic base's points: spec 5.2's Enterprise column with the default title template. */
    public static final ChromeSpec CLASSIC = new ChromeSpec(TitleBar.NATIVE, MenuBar.WINDOW, "{context}", ToolWindows.STRIPES,
        EditorTabs.CLASSIC, StatusBar.CELLS, PaletteKind.RUN_WINDOW, DialogKind.CLASSIC, MotionKind.NONE);

    /** Every point is required, the title is not blank, and window menus need a native title bar (spec 5.3). */
    public ChromeSpec {
        Objects.requireNonNull(titleBar, "titleBar"); Objects.requireNonNull(menuBar, "menuBar");
        Objects.requireNonNull(windowTitle, "windowTitle"); Objects.requireNonNull(toolWindows, "toolWindows");
        Objects.requireNonNull(editorTabs, "editorTabs"); Objects.requireNonNull(statusBar, "statusBar");
        Objects.requireNonNull(palette, "palette"); Objects.requireNonNull(dialogs, "dialogs"); Objects.requireNonNull(motion, "motion");
        if (windowTitle.isBlank()) throw new IllegalArgumentException("Blank window title template");
        if (menuBar == MenuBar.WINDOW && titleBar != TitleBar.NATIVE)
            throw new IllegalArgumentException("menu_bar = window needs title_bar = native");
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/presentation/FontFile.java`:

```java
package dev.jasper.app.presentation;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Objects;

/**
 * A font file a kit names: its jar path (relative to the kit's folder), its bytes, its full name (name ID 4, by which a
 * registered static face resolves) and its typographic family (name ID 16, else ID 1), read from the name table without
 * AWT. A file whose name table cannot be read has empty names. Compared by content.
 *
 * @param path the path as the kit file names it
 * @param bytes the file's bytes
 * @param fullName the Windows English full name, or empty
 * @param family the Windows English typographic family, or empty
 */
public record FontFile(String path, byte[] bytes, String fullName, String family) {
    private static final int TRUE_TYPE = 0x00010000, OPEN_TYPE = 0x4F54544F, APPLE = 0x74727565, NAME = 0x6E616D65;

    /** Copies the bytes. */
    public FontFile {
        Objects.requireNonNull(path, "path"); Objects.requireNonNull(fullName, "fullName"); Objects.requireNonNull(family, "family");
        bytes = bytes.clone();
    }

    /** A copy of the bytes. */
    @Override public byte[] bytes() { return bytes.clone(); }

    /** Whether the name table gave a full name. */
    public boolean named() { return !fullName.isEmpty(); }

    /** {@code bytes} as the font at {@code path}; never throws. */
    public static FontFile read(String path, byte[] bytes) {
        try {
            var data = ByteBuffer.wrap(bytes);
            int version = data.getInt(0);
            if (version != TRUE_TYPE && version != OPEN_TYPE && version != APPLE) return new FontFile(path, bytes, "", "");
            int tables = Short.toUnsignedInt(data.getShort(4));
            for (int i = 0; i < tables; i++) {
                int record = 12 + 16 * i;
                if (data.getInt(record) != NAME) continue;
                int offset = data.getInt(record + 8);
                int count = Short.toUnsignedInt(data.getShort(offset + 2));
                int strings = offset + Short.toUnsignedInt(data.getShort(offset + 4));
                String family = "", full = "", typographic = "";
                for (int n = 0; n < count; n++) {
                    int at = offset + 6 + 12 * n;
                    int platform = Short.toUnsignedInt(data.getShort(at)), encoding = Short.toUnsignedInt(data.getShort(at + 2));
                    int language = Short.toUnsignedInt(data.getShort(at + 4)), id = Short.toUnsignedInt(data.getShort(at + 6));
                    int length = Short.toUnsignedInt(data.getShort(at + 8)), start = strings + Short.toUnsignedInt(data.getShort(at + 10));
                    if (platform != 3 || encoding != 1 || language != 0x409) continue;
                    if (start + length > bytes.length) throw new IndexOutOfBoundsException(start + length);
                    String value = new String(bytes, start, length, StandardCharsets.UTF_16BE);
                    switch (id) {
                        case 1 -> family = value;
                        case 4 -> full = value;
                        case 16 -> typographic = value;
                        default -> { }
                    }
                }
                return new FontFile(path, bytes, full, typographic.isEmpty() ? family : typographic);
            }
        } catch (IndexOutOfBoundsException malformed) {
            // Falls through: an unreadable name table names nothing.
        }
        return new FontFile(path, bytes, "", "");
    }

    @Override public boolean equals(Object other) {
        return other instanceof FontFile file && path.equals(file.path) && Arrays.equals(bytes, file.bytes)
            && fullName.equals(file.fullName) && family.equals(file.family);
    }

    @Override public int hashCode() { return Objects.hash(path, Arrays.hashCode(bytes), fullName, family); }

    @Override public String toString() { return "FontFile[" + path + ", " + bytes.length + " bytes, " + fullName + ", " + family + "]"; }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java`:

```java
package dev.jasper.app.config;

import dev.jasper.app.settings.Setting;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The defaults a look gives settings (spec 5.8): a key reads as the user's saved value, else this source's, else the
 * core default. Only {@link #ALLOWED} may be overridden, each with a value its setting accepts. Immutable.
 *
 * @param overrides each overridden setting's default
 */
public record DefaultSource(Map<Setting<?>, Object> overrides) {
    /** The settings a look may default: both theme picks, the palette's rows and the toolbar's height. */
    public static final Set<Setting<?>> ALLOWED = Set.of(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK,
        CoreSettings.PALETTE_VISIBLE_ROWS, CoreSettings.WINDOW_TOOLBAR_HEIGHT);
    /** No overrides: the core defaults, as the default look has them. */
    public static final DefaultSource CORE = new DefaultSource(Map.of());

    /** Copies the map; refuses a setting outside {@link #ALLOWED} or a value its setting rejects. */
    public DefaultSource {
        overrides = Map.copyOf(overrides);
        for (var entry : overrides.entrySet()) {
            if (!ALLOWED.contains(entry.getKey())) throw new IllegalArgumentException("Not a setting a look may default: " + entry.getKey().name());
            check(entry.getKey(), entry.getValue());
        }
    }

    /** {@code setting}'s default under this source. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        Object value = overrides.get(Objects.requireNonNull(setting, "setting"));
        return value == null ? setting.defaultValue() : (T) value;
    }

    @SuppressWarnings("unchecked")
    private static <T> void check(Setting<T> setting, Object value) {
        Objects.requireNonNull(value, setting.name());
        if (!setting.defaultValue().getClass().isInstance(value))
            throw new IllegalArgumentException(setting.name() + " is not a " + setting.defaultValue().getClass().getSimpleName() + ": " + value);
        setting.kind().check((T) value).ifPresent(problem -> { throw new IllegalArgumentException(setting.name() + ": " + problem); });
    }
}
```

- [ ] **Step 4: Write `KitParser`**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.config.DefaultSource;
import dev.jasper.app.settings.Reading;
import dev.jasper.app.settings.Setting;
import dev.jasper.ui.kit.ColorSlots;
import dev.jasper.ui.kit.ControlVariants;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.MetricTokens;
import dev.jasper.ui.metrics.TypeScale;
import dev.jasper.ui.theme.JasperTheme;
import dev.jasper.ui.theme.SemanticColor;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Reads a kit file (spec 5): TOML 1.0, strict and all or nothing, in the style of the plugin descriptor parser. A kit
 * names its format ({@code kit = 1}) and a host catalog ({@code base}), then optionally the chrome points, the control
 * points, the eleven public tokens, its type base and face files, two colour slots and four setting defaults. Every
 * failure is one {@link Invalid} naming the key. Pure: files arrive through {@link Files}.
 */
public final class KitParser {
    /** The largest kit file: 64 KiB. */
    public static final int MAX_KIT_BYTES = 64 * 1024;
    /** The largest font file: 2 MiB. */
    public static final int MAX_FONT_BYTES = 2 * 1024 * 1024;
    private static final Set<String> TOP = Set.of("kit", "base", "chrome", "controls", "metrics", "type", "colors", "defaults");
    private static final Pattern SEGMENT = Pattern.compile("[a-z0-9_.-]+");
    private static final Pattern COLOUR = Pattern.compile(
        "#(?:[0-9A-Fa-f]{8}|[0-9A-Fa-f]{6}|[0-9A-Fa-f]{3})(?![0-9A-Za-z])|\\b0[xX][0-9A-Fa-f]{6}\\b|\\brgba?\\(");
    private static final Pattern TOKEN = Pattern.compile("\\{([^{}]*)}");
    private static final Set<String> TITLE_TOKENS = Set.of("context", "session", "directory");
    private static final List<String> CHROME = List.of("title_bar", "menu_bar", "window_title", "tool_windows", "editor_tabs",
        "status_bar", "palette", "dialogs", "motion");
    private static final List<String> CONTROLS = List.of("toggle", "exclusive", "tabs", "group", "form_label", "empty", "table",
        "tree", "stepper", "ordered_list", "buttons", "tag", "route", "prompt");

    private static final Map<String, Setting<?>> DEFAULTS = defaultsByKey();

    /** A kit file is invalid; the message names the key, as in {@code [metrics].row_height: 40 is outside 18-28}. */
    public static final class Invalid extends Exception {
        private static final long serialVersionUID = 1L;
        Invalid(String message) { super(message); }
    }

    /** Reads a jar path: empty when absent, else at most {@code maxBytes + 1} bytes, so a caller sees a file that is too large. */
    @FunctionalInterface
    public interface Files {
        /**
         * @param path a jar path
         * @param maxBytes the most bytes the caller accepts
         * @return the file's first {@code maxBytes + 1} bytes, or empty
         */
        Optional<byte[]> read(String path, int maxBytes);
    }

    /**
     * What a kit file is read against.
     *
     * @param id the provider's id, which names the kit in diagnostics
     * @param directory the kit file's folder in the jar, {@code ""} or ending in {@code /}; kit paths resolve against it
     * @param files reads the provider's jars
     * @param themes each theme id the provider declares, with its theme, or empty when its file did not load
     */
    public record Context(String id, String directory, Files files, Map<String, Optional<JasperTheme>> themes) {
        /** Copies the themes. */
        public Context {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(directory, "directory"); Objects.requireNonNull(files, "files");
            themes = Map.copyOf(themes);
        }
    }

    /**
     * The UI face: one file per weight.
     *
     * @param family the declared family
     * @param regular weight 400
     * @param medium weight 500
     * @param semibold weight 600
     */
    public record UiFaces(String family, FontFile regular, FontFile medium, FontFile semibold) { }

    /**
     * The data face: one file per weight.
     *
     * @param family the declared family
     * @param regular weight 400
     * @param medium weight 500
     */
    public record DataFaces(String family, FontFile regular, FontFile medium) { }

    /**
     * The faces a kit names; a part left out keeps the base's faces.
     *
     * @param ui the UI face files
     * @param data the data face files
     */
    public record Faces(Optional<UiFaces> ui, Optional<DataFaces> data) {
        /** No face files. */
        public static final Faces NONE = new Faces(Optional.empty(), Optional.empty());

        /** Every named file in declared order: the UI face's regular, medium and semibold, then the data face's regular and medium. */
        public List<FontFile> files() {
            var files = new ArrayList<FontFile>();
            ui.ifPresent(face -> files.addAll(List.of(face.regular(), face.medium(), face.semibold())));
            data.ifPresent(face -> files.addAll(List.of(face.regular(), face.medium())));
            return List.copyOf(files);
        }
    }

    /**
     * A valid kit file.
     *
     * @param kit the jasper-ui kit
     * @param chrome the chrome points
     * @param classicBase whether the kit starts from the classic catalog
     * @param defaults the kit's setting defaults
     * @param dropped the {@code [defaults]} keys left out because the plugin theme they name did not load
     * @param faces the declared face files
     */
    public record Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped, Faces faces) {
        /** Copies the list. */
        public Parsed { dropped = List.copyOf(dropped); }
    }

    private record TypeAndFaces(TypeScale scale, Faces faces) { }
    private record Defaults(Map<Setting<?>, Object> values, List<String> dropped) { }

    private KitParser() { }

    /**
     * The kit in {@code bytes}.
     *
     * @param bytes the kit file
     * @param context what its paths and theme names refer to
     * @return the parsed kit
     * @throws Invalid with one message naming the key
     */
    public static Parsed parse(byte[] bytes, Context context) throws Invalid {
        Objects.requireNonNull(context, "context");
        try { return read(bytes, context); }
        catch (StackOverflowError deep) {
            // Rulings 13: a file too deep for a reader (tomlj's parser included) is not a kit file; nothing escapes the pre-pass.
            throw new Invalid("kit file: not valid TOML");
        }
    }

    private static Parsed read(byte[] bytes, Context context) throws Invalid {
        if (bytes.length > MAX_KIT_BYTES) throw new Invalid("kit file: larger than 64 KiB");
        String text = utf8(bytes);
        TomlParseResult toml;
        try { toml = Toml.parse(text); }
        catch (RuntimeException unreadable) { throw new Invalid("kit file: not valid TOML"); }
        if (toml.hasErrors()) throw new Invalid("kit file: not valid TOML: " + toml.errors().get(0).getMessage());
        // The top-level keys first, so an unknown table is refused before anything walks its contents (Rulings 13).
        for (String key : toml.keySet())
            if (!TOP.contains(key)) throw new Invalid(toml.get(List.of(key)) instanceof TomlTable ? "[" + key + "]: unknown table" : key + ": unknown key");
        noColours(toml);
        noColours(text);
        Object format = toml.get(List.of("kit"));
        if (format == null) throw new Invalid("kit: missing; a kit file begins with kit = 1");
        if (!(format instanceof Long number)) throw new Invalid("kit: expected an integer");
        if (number != 1) throw new Invalid("kit: " + number + " is not a format this Jasper reads; expected 1");
        Object baseValue = toml.get(List.of("base"));
        if (baseValue == null) throw new Invalid("base: missing; use \"default\" or \"classic\"");
        if (!(baseValue instanceof String base)) throw new Invalid("base: expected a string");
        boolean classic = switch (base) {
            case "default" -> false;
            case "classic" -> true;
            default -> throw new Invalid("base: \"" + base + "\" is not one of default, classic");
        };
        Kit start = classic ? Kit.CLASSIC : Kit.DEFAULT;
        ChromeSpec chrome = chrome(table(toml, "chrome"), classic ? ChromeSpec.CLASSIC : ChromeSpec.DEFAULT);
        ControlVariants controls = controls(table(toml, "controls"), start.controls());
        DesignMetrics metrics = metrics(table(toml, "metrics"), start.metrics());
        TypeAndFaces type = type(table(toml, "type"), start.type(), context);
        ColorSlots colors = colors(table(toml, "colors"), start.colors());
        Defaults defaults = defaults(table(toml, "defaults"), context);
        Kit kit = new Kit(context.id(), metrics, type.scale(), start.faces(), controls, colors, start.uiKeys());
        return new Parsed(kit, chrome, classic, new DefaultSource(defaults.values()), defaults.dropped(), type.faces());
    }

    /** A jar path inside the kit's folder: {@code /}-separated segments of {@code [a-z0-9_.-]}, none {@code .} or {@code ..}. */
    public static boolean path(String path) {
        if (path.isEmpty() || path.startsWith("/") || path.endsWith("/")) return false;
        for (String segment : path.split("/", -1))
            if (!SEGMENT.matcher(segment).matches() || segment.equals(".") || segment.equals("..")) return false;
        return true;
    }

    private static String utf8(byte[] bytes) throws Invalid {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return text.startsWith("﻿") ? text.substring(1) : text;
        } catch (CharacterCodingException malformed) { throw new Invalid("kit file: not UTF-8 text"); }
    }

    /** {@code [a.b].c}, {@code [defaults]."ui.theme.dark"} or a top-level {@code kit}. */
    private static String name(List<String> path) {
        String last = path.getLast().contains(".") ? "\"" + path.getLast() + "\"" : path.getLast();
        return path.size() == 1 ? last : "[" + String.join(".", path.subList(0, path.size() - 1)) + "]." + last;
    }

    /** One value on the walk: its parent and its key, so the path is built only for a message. */
    private record Walked(Object value, Walked parent, String key) {
        List<String> path() {
            var path = new java.util.ArrayDeque<String>();
            for (Walked at = this; at != null && at.key() != null; at = at.parent()) path.push(at.key());
            return List.copyOf(path);
        }
    }

    /** Every string value, however deeply nested, with an explicit stack (Rulings 13): a colour literal is named by its key. */
    private static void noColours(TomlTable root) throws Invalid {
        var pending = new java.util.ArrayDeque<Walked>();
        pending.push(new Walked(root, null, null));
        while (!pending.isEmpty()) {
            Walked item = pending.pop();
            if (item.value() instanceof TomlTable table) {
                for (String key : table.keySet()) pending.push(new Walked(table.get(List.of(key)), item, key));
            } else if (item.value() instanceof TomlArray array) {
                for (int i = 0; i < array.size(); i++) pending.push(new Walked(array.get(i), item.parent(), item.key()));
            } else if (item.value() instanceof String text) {
                Matcher match = COLOUR.matcher(text);
                if (match.find())
                    throw new Invalid(name(item.path()) + ": \"" + match.group() + "\" looks like a colour; colours come from the theme");
            }
        }
    }

    /** Comments are not values: a literal there is named by its line. */
    private static void noColours(String text) throws Invalid {
        String[] lines = text.split("\n", -1);
        for (int i = 0; i < lines.length; i++) {
            Matcher match = COLOUR.matcher(lines[i]);
            if (match.find()) throw new Invalid("kit file: line " + (i + 1) + ": \"" + match.group() + "\" looks like a colour; colours come from the theme");
        }
    }

    private static TomlTable table(TomlTable toml, String name) throws Invalid {
        Object value = toml.get(List.of(name));
        if (value == null) return null;
        if (!(value instanceof TomlTable table)) throw new Invalid(name + ": expected a table");
        return table;
    }

    private static void known(TomlTable table, String name, List<String> keys) throws Invalid {
        for (String key : table.keySet()) if (!keys.contains(key)) throw new Invalid("[" + name + "]." + key + ": unknown key");
    }

    private static String string(TomlTable table, String name, String key) throws Invalid {
        Object value = table.get(List.of(key));
        if (value == null) throw new Invalid("[" + name + "]." + key + ": missing");
        if (!(value instanceof String text)) throw new Invalid("[" + name + "]." + key + ": expected a string");
        return text;
    }

    private static <E extends Enum<E>> E point(TomlTable table, String name, String key, Class<E> type, E base) throws Invalid {
        if (table == null || table.get(List.of(key)) == null) return base;
        String text = string(table, name, key);
        for (E constant : type.getEnumConstants()) if (constant.name().toLowerCase(Locale.ROOT).equals(text)) return constant;
        throw new Invalid("[" + name + "]." + key + ": \"" + text + "\" is not one of " + Arrays.stream(type.getEnumConstants())
            .map(constant -> constant.name().toLowerCase(Locale.ROOT)).collect(Collectors.joining(", ")));
    }

    private static ChromeSpec chrome(TomlTable table, ChromeSpec base) throws Invalid {
        if (table == null) return base;
        known(table, "chrome", CHROME);
        var titleBar = point(table, "chrome", "title_bar", ChromeSpec.TitleBar.class, base.titleBar());
        var menuBar = point(table, "chrome", "menu_bar", ChromeSpec.MenuBar.class, base.menuBar());
        String title = table.get(List.of("window_title")) == null ? base.windowTitle() : windowTitle(string(table, "chrome", "window_title"));
        var toolWindows = point(table, "chrome", "tool_windows", ChromeSpec.ToolWindows.class, base.toolWindows());
        var editorTabs = point(table, "chrome", "editor_tabs", ChromeSpec.EditorTabs.class, base.editorTabs());
        var statusBar = point(table, "chrome", "status_bar", ChromeSpec.StatusBar.class, base.statusBar());
        var palette = point(table, "chrome", "palette", ChromeSpec.PaletteKind.class, base.palette());
        var dialogs = point(table, "chrome", "dialogs", ChromeSpec.DialogKind.class, base.dialogs());
        var motion = point(table, "chrome", "motion", ChromeSpec.MotionKind.class, base.motion());
        if (menuBar == ChromeSpec.MenuBar.WINDOW && titleBar != ChromeSpec.TitleBar.NATIVE)
            throw new Invalid("[chrome].menu_bar: \"window\" needs [chrome].title_bar = \"native\"");
        return new ChromeSpec(titleBar, menuBar, title, toolWindows, editorTabs, statusBar, palette, dialogs, motion);
    }

    private static String windowTitle(String text) throws Invalid {
        if (text.isBlank() || text.length() > 120) throw new Invalid("[chrome].window_title: expected 1 to 120 characters");
        Matcher token = TOKEN.matcher(text);
        while (token.find())
            if (!TITLE_TOKENS.contains(token.group(1)))
                throw new Invalid("[chrome].window_title: unknown token {" + token.group(1) + "}; use {context}, {session} and {directory}");
        String rest = TOKEN.matcher(text).replaceAll("");
        if (rest.indexOf('{') >= 0 || rest.indexOf('}') >= 0)
            throw new Invalid("[chrome].window_title: a brace that is not part of {context}, {session} or {directory}");
        return text;
    }

    private static ControlVariants controls(TomlTable table, ControlVariants base) throws Invalid {
        if (table == null) return base;
        known(table, "controls", CONTROLS);
        return new ControlVariants(
            point(table, "controls", "toggle", ControlVariants.Toggle.class, base.toggle()),
            point(table, "controls", "exclusive", ControlVariants.Exclusive.class, base.exclusive()),
            point(table, "controls", "tabs", ControlVariants.Tabs.class, base.tabs()),
            point(table, "controls", "group", ControlVariants.Group.class, base.group()),
            point(table, "controls", "form_label", ControlVariants.FormLabel.class, base.formLabel()),
            point(table, "controls", "empty", ControlVariants.Empty.class, base.empty()),
            point(table, "controls", "table", ControlVariants.Table.class, base.table()),
            point(table, "controls", "tree", ControlVariants.Tree.class, base.tree()),
            point(table, "controls", "stepper", ControlVariants.Stepper.class, base.stepper()),
            point(table, "controls", "ordered_list", ControlVariants.OrderedList.class, base.orderedList()),
            point(table, "controls", "buttons", ControlVariants.Buttons.class, base.buttons()),
            point(table, "controls", "tag", ControlVariants.Tag.class, base.tag()),
            point(table, "controls", "route", ControlVariants.Route.class, base.route()),
            point(table, "controls", "prompt", ControlVariants.Prompt.class, base.prompt()));
    }

    private static DesignMetrics metrics(TomlTable table, DesignMetrics base) throws Invalid {
        if (table == null) return base;
        known(table, "metrics", Arrays.stream(MetricTokens.Token.values()).map(MetricTokens.Token::key).toList());
        // The base catalog's own tokens: a key left out keeps the base's value (plan 4a's of reads each token's first role).
        var tokens = MetricTokens.of(base);
        for (var token : MetricTokens.Token.values()) {
            Object value = table.get(List.of(token.key()));
            if (value == null) continue;
            if (!(value instanceof Long number)) throw new Invalid("[metrics]." + token.key() + ": expected an integer");
            if (number != number.intValue())
                throw new Invalid("[metrics]." + token.key() + ": " + number + " is outside " + token.min() + "-" + token.max());
            // MetricTokens owns the ranges and the message, for example "[metrics].row_height: 40 is outside 18-28".
            try { tokens = tokens.with(token, number.intValue()); }
            catch (IllegalArgumentException outside) { throw new Invalid(outside.getMessage()); }
        }
        return base.with(tokens);
    }

    private static TypeAndFaces type(TomlTable table, TypeScale base, Context context) throws Invalid {
        if (table == null) return new TypeAndFaces(base, Faces.NONE);
        known(table, "type", List.of("base", "ui", "data"));
        TypeScale scale = base;
        Object body = table.get(List.of("base"));
        if (body != null) {
            if (!(body instanceof Long number)) throw new Invalid("[type].base: expected an integer");
            if (number < 10 || number > 16) throw new Invalid("[type].base: " + number + " is not a body size from 10 to 16");
            scale = base.withBody(number);
        }
        Optional<UiFaces> ui = Optional.empty();
        Object uiValue = table.get(List.of("ui"));
        if (uiValue != null) {
            if (!(uiValue instanceof TomlTable face)) throw new Invalid("[type].ui: expected a table");
            known(face, "type.ui", List.of("family", "regular", "medium", "semibold"));
            ui = Optional.of(new UiFaces(family(face, "type.ui"), font(face, "type.ui", "regular", context),
                font(face, "type.ui", "medium", context), font(face, "type.ui", "semibold", context)));
        }
        Optional<DataFaces> data = Optional.empty();
        Object dataValue = table.get(List.of("data"));
        if (dataValue != null) {
            if (!(dataValue instanceof TomlTable face)) throw new Invalid("[type].data: expected a table");
            known(face, "type.data", List.of("family", "regular", "medium"));
            data = Optional.of(new DataFaces(family(face, "type.data"), font(face, "type.data", "regular", context),
                font(face, "type.data", "medium", context)));
        }
        return new TypeAndFaces(scale, new Faces(ui, data));
    }

    private static String family(TomlTable table, String name) throws Invalid {
        String family = string(table, name, "family");
        if (family.isBlank() || family.length() > 64) throw new Invalid("[" + name + "].family: expected a family name of 1 to 64 characters");
        return family;
    }

    private static FontFile font(TomlTable table, String name, String weight, Context context) throws Invalid {
        String where = "[" + name + "]." + weight;
        String file = string(table, name, weight);
        if (!path(file)) throw new Invalid(where + ": " + file + " must be a lowercase path inside the kit's folder, without . or .. segments");
        if (!file.endsWith(".ttf") && !file.endsWith(".otf")) throw new Invalid(where + ": " + file + " is not a .ttf or .otf file");
        byte[] bytes = context.files().read(context.directory() + file, MAX_FONT_BYTES)
            .orElseThrow(() -> new Invalid(where + ": " + file + " is not in the plugin's jars"));
        if (bytes.length > MAX_FONT_BYTES) throw new Invalid(where + ": " + file + " is larger than 2 MiB");
        return FontFile.read(file, bytes);
    }

    private static ColorSlots colors(TomlTable table, ColorSlots base) throws Invalid {
        if (table == null) return base;
        known(table, "colors", List.of("dialog_surface", "navigation_surface"));
        return new ColorSlots(slot(table, "dialog_surface", List.of("surface.dialog", "surface.app"), base.dialogSurface()),
            slot(table, "navigation_surface", List.of("surface.sidebar", "surface.card"), base.navigationSurface()));
    }

    private static SemanticColor slot(TomlTable table, String key, List<String> allowed, SemanticColor base) throws Invalid {
        if (table.get(List.of(key)) == null) return base;
        String role = string(table, "colors", key);
        if (!allowed.contains(role)) throw new Invalid("[colors]." + key + ": \"" + role + "\" is not one of " + String.join(", ", allowed));
        return SemanticColor.of(role).orElseThrow();
    }

    private static Defaults defaults(TomlTable table, Context context) throws Invalid {
        if (table == null) return new Defaults(Map.of(), List.of());
        var values = new LinkedHashMap<Setting<?>, Object>();
        var dropped = new ArrayList<String>();
        for (String key : table.keySet()) {
            String where = "[defaults].\"" + key + "\"";
            Setting<?> setting = DEFAULTS.get(key);
            if (setting == null) throw new Invalid(where + ": not a setting a look may default; use "
                + DEFAULTS.keySet().stream().map(name -> "\"" + name + "\"").collect(Collectors.joining(", ")).replaceFirst(", ([^,]*)$", " or $1"));
            Reading<?> reading = read(setting, table.get(List.of(key)));
            if (!reading.issues().isEmpty()) throw new Invalid(where + ": " + reading.issues().getFirst().message());
            if (setting == CoreSettings.UI_THEME_LIGHT || setting == CoreSettings.UI_THEME_DARK) {
                boolean dark = setting == CoreSettings.UI_THEME_DARK;
                String id = (String) reading.value();
                Optional<JasperTheme> theme;
                var builtIn = JasperTheme.builtIn(id);
                if (builtIn.isPresent()) theme = builtIn;
                else if (context.themes().containsKey(id)) theme = context.themes().get(id);
                else throw new Invalid(where + ": \"" + id + "\" is neither a built-in theme nor one of this plugin's themes");
                if (theme.isEmpty()) { dropped.add(key); continue; }
                if (!theme.get().has(dark)) throw new Invalid(where + ": \"" + id + "\" has no " + (dark ? "Dark" : "Light") + " variant");
            }
            values.put(setting, reading.value());
        }
        return new Defaults(values, dropped);
    }

    private static <T> Reading<T> read(Setting<T> setting, Object raw) { return setting.kind().read(raw, setting.defaultValue()); }

    private static Map<String, Setting<?>> defaultsByKey() {
        var byKey = new LinkedHashMap<String, Setting<?>>();
        for (Setting<?> setting : List.of(CoreSettings.UI_THEME_LIGHT, CoreSettings.UI_THEME_DARK, CoreSettings.PALETTE_VISIBLE_ROWS,
                CoreSettings.WINDOW_TOOLBAR_HEIGHT)) byKey.put(String.join(".", setting.key()), setting);
        return java.util.Collections.unmodifiableMap(byKey);
    }
}
```

`KitParser` reads `Kit.DEFAULT` for the default base, so it joins `StaticDefaultTest`'s readers (the classes that
choose the start-up kit or fall back to it), and it is the one reader of `Kit.CLASSIC`, which plan 4a's rule (no reader
"until plan 5's kit parser names it") now names; a `Kit.CLASSIC` read is left out of the general "outside the catalog"
filter like a `Kit.DEFAULT` read:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java"); text = path.read_text(encoding="utf-8")
edits = [
    ('static final Set<String> DEFAULT_KIT_READERS = Set.of(',
     'static final Set<String> DEFAULT_KIT_READERS = Set.of("dev/jasper/app/presentation/KitParser", '),
    ("    /** The classic base; until plan 5's kit parser names it, no production class reads it. */",
     "    /** The classic base; only the kit parser, which starts a classic kit from it, reads it. */"),
    (" * FaceSet.PLATFORM, DesignMetrics.CLASSIC, TypeScale.CLASSIC or Kit.CLASSIC, only the classes that choose the start-up\n"
     " * kit or fall back to it read Kit.DEFAULT, and no static\n",
     " * FaceSet.PLATFORM, DesignMetrics.CLASSIC or TypeScale.CLASSIC, only the kit parser reads Kit.CLASSIC, only the classes\n"
     " * that choose the start-up kit or fall back to it read Kit.DEFAULT, and no static\n"),
    ('        assertThat(found.stream().filter(read -> read.endsWith(": " + CLASSIC_KIT))).as("readers of Kit.CLASSIC").isEmpty();\n',
     '        Set<String> classicReaders = found.stream().filter(read -> read.endsWith(": " + CLASSIC_KIT))\n'
     '            .map(read -> read.substring(0, read.indexOf(\'.\'))).collect(Collectors.toCollection(TreeSet::new));\n'
     '        assertThat(classicReaders).as("the classes that read Kit.CLASSIC").containsExactly("dev/jasper/app/presentation/KitParser");\n'),
    ('!read.endsWith(": " + DEFAULT_KIT)))',
     '!read.endsWith(": " + DEFAULT_KIT)\n                && !read.endsWith(": " + CLASSIC_KIT)))'),
]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"StaticDefaultTest: expected one {old!r} (plan 4a Task 6's text); re-find it")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

Add the two ledger rows at the end of `docs/design/theme-engine/migration.csv` (each one line):

```
jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java,app.presentation,KitParser,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java,New in Enterprise plan 5a Task 3; parses kit files into the jasper-ui kit and the chrome points; no Swing
jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java,app.presentation,KitParserTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 3; kit file values and every invalid kit
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.StaticDefaultTest'`
Expected: PASS: `FontFileTest` (3), `KitParserTest` (10), `DefaultSourceTest` (2), the config tests, the ledger test and
`StaticDefaultTest` (KitParser is the one `Kit.CLASSIC` reader and a `Kit.DEFAULT` reader).
If `KitParserTest.everyChromeAndControlPointTakesBothItsValues` fails on the `toString` check, plan 4's
`ControlVariants` names its components differently: compare the accessor of each point instead (keep the test's
meaning: each key takes each of its two values).

- [ ] **Step 6: Run the architecture checks and commit**

Run: `./gradlew :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL (the new package adds edges to `config`, `settings`, `dev.jasper.ui.kit`,
`dev.jasper.ui.metrics` and `dev.jasper.ui.theme` only, and no cycle).

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation jasper-app/src/main/java/dev/jasper/app/config/DefaultSource.java \
  jasper-app/src/test/java/dev/jasper/app/presentation jasper-app/src/test/java/dev/jasper/app/config/DefaultSourceTest.java \
  docs/design/theme-engine/migration.csv jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java
git commit -F - <<'EOF'
Presentation provider: the kit file

dev.jasper.app.presentation reads a kit file strictly: its format and base
catalog, the nine chrome points, the fourteen control points, the eleven
public tokens through MetricTokens, the type base and face files, the two
colour slots and the defaults of four settings (DefaultSource). Every
failure is one message naming the key; [branding] is an unknown table
until plan 11. FontFile reads a face's full name and typographic family
without AWT.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The attempt record

`<home>/presentation-attempt.toml` (spec 4.6) keeps, per provider, the look last tried (version, digest, build), whether
it finished starting (`pending`, `proven` or `failed`) and the digest whose problem was last announced (5.9). The store
is a locked read-modify-write with its own lock file; `PresentationAttempt` is the handle a process uses for its own look.
Nothing writes the record yet: Task 12 wires the writes.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/presentation/AttemptStore.java`, `PresentationAttempt.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/AttemptStoreTest.java`

**Interfaces:**
- Consumes: `TomlStateFile.readBounded(Path, int, String)`, `TomlStateFile.writeAtomically(Path, String, String)`
  (`dev.jasper.app.persistence`), `TomlLiterals.string(String)` (`dev.jasper.app.settings`).
- Produces: `AttemptStore(Path home)`, `Path file()`, `AttemptStore.Record read()` (never throws),
  `boolean update(String id, UnaryOperator<AttemptStore.Attempt> edit, Duration wait)` (never throws),
  `AttemptStore.EDT_WAIT` (250 ms) and `WAIT` (2 s); `enum AttemptStore.State { PENDING, PROVEN, FAILED }`;
  `AttemptStore.Attempt(String version, String digest, String build, Optional<State> state, String announced)` with
  `NONE`, `blocks(String digest, String build)`, `tried(version, digest, build, State)`, `cleared()`,
  `announcing(String digest)`; `AttemptStore.Record(Map<String, Attempt> providers)` with `EMPTY` and `of(String id)`;
  `PresentationAttempt(AttemptStore store, String id, String version, String digest, String build)` with `NONE`,
  `records()`, `id()`, `pending()`, `proven()`, `failed()`, `orderlyQuit()`, `exiting()`, `clear()`, and
  `static boolean PresentationAttempt.announce(AttemptStore store, String id, String digest)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/presentation/AttemptStoreTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.6: the record a look leaves when it starts, and how it is read back. */
class AttemptStoreTest {
    static final String ID = "dev.jasper.enterprise", DIGEST = "9f2c4e0b7a1d3c5e8f6a0b2d4c6e8f0a1b3d5c7e9f1a2b4c6d8e0f1a3b5c7d9e",
        BUILD = "/Applications/Jasper.app/Contents/app/jasper-app.jar@1760500000000";
    @TempDir Path home;

    @Test void aTriedLookIsWrittenAsTheSpecShowsAndReadBack() throws Exception {
        var store = new AttemptStore(home);
        assertThat(store.read()).isEqualTo(AttemptStore.Record.EMPTY);
        assertThat(store.update(ID, attempt -> attempt.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PENDING), AttemptStore.WAIT)).isTrue();
        assertThat(Files.readString(home.resolve("presentation-attempt.toml"))).isEqualTo("""
            version = 1

            [providers."dev.jasper.enterprise"]
            version = "0.1.0"
            digest = "9f2c4e0b7a1d3c5e8f6a0b2d4c6e8f0a1b3d5c7e9f1a2b4c6d8e0f1a3b5c7d9e"
            build = "/Applications/Jasper.app/Contents/app/jasper-app.jar@1760500000000"
            state = "pending"
            """);
        var attempt = store.read().of(ID);
        assertThat(attempt).isEqualTo(new AttemptStore.Attempt("0.1.0", DIGEST, BUILD, Optional.of(AttemptStore.State.PENDING), ""));
        assertThat(store.read().of("dev.other")).isEqualTo(AttemptStore.Attempt.NONE);
        store.update(ID, previous -> previous.announcing("abc"), AttemptStore.WAIT);
        assertThat(store.read().of(ID).announced()).isEqualTo("abc");
        store.update(ID, AttemptStore.Attempt::cleared, AttemptStore.WAIT);
        assertThat(store.read().of(ID).state()).isEmpty();
        store.update(ID, previous -> AttemptStore.Attempt.NONE, AttemptStore.WAIT);
        assertThat(Files.readString(home.resolve("presentation-attempt.toml"))).as("an empty attempt drops its table").isEqualTo("version = 1\n");
    }

    @Test void onlyAPendingOrFailedAttemptForTheSameLookAndBuildBlocks() {
        var pending = new AttemptStore.Attempt("0.1.0", DIGEST, BUILD, Optional.of(AttemptStore.State.PENDING), "");
        assertThat(pending.blocks(DIGEST, BUILD)).isTrue();
        assertThat(pending.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.FAILED).blocks(DIGEST, BUILD)).isTrue();
        assertThat(pending.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PROVEN).blocks(DIGEST, BUILD)).isFalse();
        assertThat(pending.blocks("another digest", BUILD)).as("a changed kit gets a fresh try").isFalse();
        assertThat(pending.blocks(DIGEST, "another build")).as("a new build gets a fresh try").isFalse();
        assertThat(pending.cleared().blocks(DIGEST, BUILD)).isFalse();
    }

    @Test void unreadableContentIsAbsent() throws Exception {
        var store = new AttemptStore(home);
        for (String text : java.util.List.of("version = 2\n", "not toml [", "version = 1\n[providers.\"a.b\"]\nstate = \"maybe\"\n",
                "version = 1\nproviders = 3\n", "version = 1\n[providers.\"a.b\"]\ndigest = 4\n")) {
            Files.writeString(home.resolve("presentation-attempt.toml"), text);
            assertThat(store.read()).as(text).isEqualTo(AttemptStore.Record.EMPTY);
        }
    }

    @Test void aBusyLockFailsTheWriteWithinItsWaitAndTheLaunchGoesOn() throws Exception {
        var store = new AttemptStore(home);
        try (var channel = FileChannel.open(home.resolve("presentation-attempt.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
             var held = channel.lock()) {
            long began = System.nanoTime();
            assertThat(store.update(ID, attempt -> attempt.tried("0.1.0", DIGEST, BUILD, AttemptStore.State.PENDING), Duration.ofMillis(100))).isFalse();
            assertThat(Duration.ofNanos(System.nanoTime() - began)).isLessThan(Duration.ofSeconds(2));
        }
        assertThat(store.read()).isEqualTo(AttemptStore.Record.EMPTY);
    }

    @Test void theProcessHandleMarksItsLookAndKeepsAFailureOverALaterPaint() {
        var store = new AttemptStore(home);
        var attempt = new PresentationAttempt(store, ID, "0.1.0", DIGEST, BUILD);
        attempt.pending();
        assertThat(store.read().of(ID).state()).contains(AttemptStore.State.PENDING);
        attempt.exiting();
        assertThat(store.read().of(ID).state()).as("an exit that was not orderly leaves pending").contains(AttemptStore.State.PENDING);
        attempt.orderlyQuit();
        attempt.exiting();
        assertThat(store.read().of(ID).state()).as("an orderly quit before proof clears pending").isEmpty();
        attempt.pending();
        attempt.proven();
        assertThat(store.read().of(ID).state()).contains(AttemptStore.State.PROVEN);
        var failing = new PresentationAttempt(store, ID, "0.1.0", DIGEST, BUILD);
        failing.pending();
        failing.failed();
        failing.proven();
        assertThat(store.read().of(ID).state()).as("a provider that failed stays failed when the window paints").contains(AttemptStore.State.FAILED);
        failing.clear();
        assertThat(store.read().of(ID).state()).as("Try Again").isEmpty();
        assertThat(PresentationAttempt.announce(store, ID, "d1")).isTrue();
        assertThat(PresentationAttempt.announce(store, ID, "d1")).as("once per digest").isFalse();
        assertThat(PresentationAttempt.announce(store, ID, "d2")).isTrue();
        PresentationAttempt.NONE.pending();
        PresentationAttempt.NONE.proven();
        assertThat(PresentationAttempt.NONE.records()).isFalse();
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.AttemptStoreTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class AttemptStore" and "class PresentationAttempt".

- [ ] **Step 3: Write the store and the handle**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/AttemptStore.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.app.persistence.TomlStateFile;
import dev.jasper.app.settings.TomlLiterals;
import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.UnaryOperator;
import org.tomlj.Toml;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * {@code <home>/presentation-attempt.toml} (spec 4.6): per provider, the look last tried (its version, digest and the
 * build that tried it), whether it finished starting, and the digest whose problem was last announced. More than one
 * Jasper process can write it, so every change is a locked read-modify-write under {@code presentation-attempt.lock}; a
 * busy lock or an unwritable file is logged and the write skipped. Content that cannot be read counts as absent. Any thread.
 */
public final class AttemptStore {
    /** Whether a tried look finished starting. */
    public enum State { PENDING, PROVEN, FAILED }

    /**
     * One provider's record; empty strings and an empty state mean "not recorded".
     *
     * @param version the provider's version when tried
     * @param digest the look's digest when tried
     * @param build the application's code location and modification time when tried
     * @param state whether it finished starting
     * @param announced the digest whose problem notice was last shown
     */
    public record Attempt(String version, String digest, String build, Optional<State> state, String announced) {
        /** Nothing recorded. */
        public static final Attempt NONE = new Attempt("", "", "", Optional.empty(), "");

        /** Every part is required. */
        public Attempt {
            Objects.requireNonNull(version, "version"); Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(build, "build");
            Objects.requireNonNull(state, "state"); Objects.requireNonNull(announced, "announced");
        }

        /** Whether this record skips the look {@code digest} of {@code build}: it is pending or failed for exactly it. */
        public boolean blocks(String digest, String build) {
            return state.filter(value -> value != State.PROVEN).isPresent() && this.digest.equals(digest) && this.build.equals(build);
        }

        /** This record for a look tried now. */
        public Attempt tried(String version, String digest, String build, State state) {
            return new Attempt(version, digest, build, Optional.of(state), announced);
        }

        /** This record without its state, so the next start tries afresh. */
        public Attempt cleared() { return new Attempt(version, digest, build, Optional.empty(), announced); }

        /** This record with {@code digest} announced. */
        public Attempt announcing(String digest) { return new Attempt(version, this.digest, build, state, digest); }

        private boolean empty() { return state.isEmpty() && announced.isEmpty(); }
    }

    /**
     * The whole record.
     *
     * @param providers each provider's attempt by id
     */
    public record Record(Map<String, Attempt> providers) {
        /** Nothing recorded. */
        public static final Record EMPTY = new Record(Map.of());

        /** Copies the map. */
        public Record { providers = Map.copyOf(providers); }

        /** {@code id}'s attempt, or {@link Attempt#NONE}. */
        public Attempt of(String id) { return providers.getOrDefault(id, Attempt.NONE); }
    }

    /** How long a write on the EDT waits for the lock. */
    public static final Duration EDT_WAIT = Duration.ofMillis(250);
    /** How long a write off the EDT waits for the lock. */
    public static final Duration WAIT = Duration.ofSeconds(2);
    private static final int MAX_BYTES = 64 * 1024;
    /** File locks are per process; this serializes writers inside one. */
    private static final ReentrantLock IN_PROCESS = new ReentrantLock();
    private static final System.Logger LOG = System.getLogger(AttemptStore.class.getName());
    private final Path file;
    private final Path lock;

    /** The record in {@code home}. */
    public AttemptStore(Path home) {
        file = home.resolve("presentation-attempt.toml");
        lock = home.resolve("presentation-attempt.lock");
    }

    /** The record's file. */
    public Path file() { return file; }

    /** The record now; lock-free, because the file is only replaced atomically. Never throws. */
    public Record read() {
        try { return parse(TomlStateFile.readBounded(file, MAX_BYTES, "Presentation attempts")); }
        catch (IOException | RuntimeException unreadable) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt record is unreadable; every look gets a fresh try", unreadable);
            return Record.EMPTY;
        }
    }

    /**
     * Applies {@code edit} to {@code id}'s attempt under the lock; an attempt that ends empty leaves the file. Never throws.
     *
     * @return false when the lock stayed busy for {@code wait} or the file could not be written (both logged)
     */
    public boolean update(String id, UnaryOperator<Attempt> edit, Duration wait) {
        long deadline = System.nanoTime() + wait.toNanos();
        boolean held;
        try { held = IN_PROCESS.tryLock(wait.toNanos(), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); held = false; }
        if (!held) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt lock is busy; this launch goes on without a record");
            return false;
        }
        try {
            Files.createDirectories(lock.toAbsolutePath().getParent());
            try (FileChannel channel = FileChannel.open(lock, StandardOpenOption.CREATE, StandardOpenOption.WRITE)) {
                FileLock locked = acquire(channel, deadline);
                if (locked == null) {
                    LOG.log(System.Logger.Level.WARNING, "The presentation attempt lock is busy; this launch goes on without a record");
                    return false;
                }
                try (locked) {
                    var next = new TreeMap<>(read().providers());
                    Attempt changed = Objects.requireNonNull(edit.apply(next.getOrDefault(id, Attempt.NONE)));
                    if (changed.empty()) next.remove(id); else next.put(id, changed);
                    TomlStateFile.writeAtomically(file, ".presentation-attempt-", render(next));
                    return true;
                }
            }
        } catch (IOException | RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The presentation attempt record could not be written; this launch goes on without it", failure);
            return false;
        } finally { IN_PROCESS.unlock(); }
    }

    private static FileLock acquire(FileChannel channel, long deadline) throws IOException {
        while (true) {
            try {
                FileLock held = channel.tryLock();
                if (held != null) return held;
            } catch (OverlappingFileLockException heldInThisProcess) {
                // Another channel in this JVM holds it: treated like another process.
            }
            if (System.nanoTime() >= deadline) return null;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return null; }
        }
    }

    private static Record parse(Optional<String> text) {
        if (text.isEmpty()) return Record.EMPTY;
        TomlParseResult toml = Toml.parse(text.get());
        if (toml.hasErrors()) throw new IllegalStateException("not valid TOML");
        if (!Long.valueOf(1).equals(toml.get(List.of("version")))) throw new IllegalStateException("not version 1");
        Object providers = toml.get(List.of("providers"));
        if (providers == null) return Record.EMPTY;
        if (!(providers instanceof TomlTable table)) throw new IllegalStateException("providers is not a table");
        var result = new TreeMap<String, Attempt>();
        for (String id : table.keySet()) {
            if (!(table.get(List.of(id)) instanceof TomlTable entry)) throw new IllegalStateException(id + " is not a table");
            String state = text(entry, "state");
            result.put(id, new Attempt(text(entry, "version"), text(entry, "digest"), text(entry, "build"),
                state.isEmpty() ? Optional.empty() : Optional.of(State.valueOf(state.toUpperCase(Locale.ROOT))), text(entry, "announced")));
        }
        return new Record(result);
    }

    private static String text(TomlTable table, String key) {
        Object value = table.get(List.of(key));
        if (value == null) return "";
        if (!(value instanceof String text)) throw new IllegalStateException(key + " is not a string");
        return text;
    }

    private static String render(Map<String, Attempt> providers) {
        var text = new StringBuilder("version = 1\n");
        for (var item : providers.entrySet()) {
            Attempt attempt = item.getValue();
            text.append("\n[providers.").append(TomlLiterals.string(item.getKey())).append("]\n");
            field(text, "version", attempt.version());
            field(text, "digest", attempt.digest());
            field(text, "build", attempt.build());
            attempt.state().ifPresent(state -> field(text, "state", state.name().toLowerCase(Locale.ROOT)));
            field(text, "announced", attempt.announced());
        }
        return text.toString();
    }

    private static void field(StringBuilder text, String key, String value) {
        if (!value.isEmpty()) text.append(key).append(" = ").append(TomlLiterals.string(value)).append('\n');
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/presentation/PresentationAttempt.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * The attempt record of the look this process runs (spec 4.6): {@link #pending} just before a window shows or a rehearsal
 * runs, {@link #proven} once it painted, {@link #failed} when the look or its provider failed, and an orderly quit before
 * proof clears the pending mark at exit. {@link #NONE} records nothing: the default look, or a look that was skipped.
 */
public final class PresentationAttempt {
    /** Records nothing. */
    public static final PresentationAttempt NONE = new PresentationAttempt(null, "", "", "", "");
    private final AttemptStore store;
    private final String id, version, digest, build;
    private final AtomicBoolean orderly = new AtomicBoolean(), proven = new AtomicBoolean(), failed = new AtomicBoolean();

    /**
     * @param store the record, or null for {@link #NONE}
     * @param id the provider's id
     * @param version the provider's version
     * @param digest the look's digest
     * @param build this application's build identity
     */
    public PresentationAttempt(AttemptStore store, String id, String version, String digest, String build) {
        this.store = store;
        this.id = Objects.requireNonNull(id, "id"); this.version = Objects.requireNonNull(version, "version");
        this.digest = Objects.requireNonNull(digest, "digest"); this.build = Objects.requireNonNull(build, "build");
    }

    /** Whether this handle writes a record. */
    public boolean records() { return store != null; }

    /** The provider's id; empty for {@link #NONE}. */
    public String id() { return id; }

    /** A window is about to show, or a rehearsal to run: pending. Runs on the EDT, bounded by {@link AttemptStore#EDT_WAIT}. */
    public void pending() {
        if (store != null) store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.PENDING), AttemptStore.EDT_WAIT);
    }

    /** The first window or the rehearsal painted: proven, unless this look already failed. Off the EDT; once. */
    public void proven() {
        if (store == null || failed.get() || !proven.compareAndSet(false, true)) return;
        store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.PROVEN), AttemptStore.WAIT);
    }

    /** The kit was blamed at start, or the provider failed to load or start: failed. */
    public void failed() {
        if (store == null) return;
        failed.set(true);
        store.update(id, attempt -> attempt.tried(version, digest, build, AttemptStore.State.FAILED), AttemptStore.EDT_WAIT);
    }

    /** The quit under way is orderly (Quit, or a programmatic {@code quitNow}). */
    public void orderlyQuit() { orderly.set(true); }

    /** The process is exiting (the shutdown hook): after an orderly quit before proof, the pending mark goes; otherwise it stays. */
    public void exiting() {
        if (store == null || !orderly.get() || proven.get()) return;
        store.update(id, attempt -> attempt.state().equals(Optional.of(AttemptStore.State.PENDING)) && attempt.digest().equals(digest)
            ? attempt.cleared() : attempt, AttemptStore.EDT_WAIT);
    }

    /** Try Again: forget whether the look finished starting, so the next start tries it afresh. Off the EDT. */
    public void clear() {
        if (store != null) store.update(id, AttemptStore.Attempt::cleared, AttemptStore.WAIT);
    }

    /**
     * Whether the problem of a look with {@code digest} is new for {@code id}, recording it as announced (spec 5.9's "once
     * per digest"). A record that cannot be written still answers true, so the notice is shown.
     */
    public static boolean announce(AttemptStore store, String id, String digest) {
        if (store.read().of(id).announced().equals(digest)) return false;
        store.update(id, attempt -> attempt.announcing(digest), AttemptStore.EDT_WAIT);
        return true;
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*'`
Expected: PASS (`AttemptStoreTest` 5 cases and the Task 3 tests).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation/AttemptStore.java \
  jasper-app/src/main/java/dev/jasper/app/presentation/PresentationAttempt.java \
  jasper-app/src/test/java/dev/jasper/app/presentation/AttemptStoreTest.java
git commit -F - <<'EOF'
Presentation provider: the attempt record

presentation-attempt.toml keeps, per provider, the look last tried, whether
it finished starting (pending, proven or failed) and the digest whose
problem was last announced, as locked read-modify-writes under its own lock
file. A busy lock or an unwritable file is logged and the launch goes on;
unreadable content counts as absent. PresentationAttempt is the handle a
process uses for its look; a failed look stays failed when its window
paints.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: One active presentation, chosen in the resolver

`PresentationFacts` gives each presentation-declaring candidate a digest (SHA-256 of the kit file, then each declared
theme file, then each font file, in declared order) and its first kit problem (spec 4.4). `PluginResolver.resolve` takes
them as a new `Presentations` argument and, after admission, cycle and unmet-dependency removal, elects one provider by
origin then id, skips the losers, skips an invalid kit, applies the attempt record and, under `--safe-mode`, disables
every provider (4.5). The quiet skips post no plugin problem. Until Task 9 the runtime reads the facts itself.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/presentation/PresentationFacts.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/Presentations.java`, `PluginResources.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row), `jasper-app/src/main/java/dev/jasper/app/plugins/PluginResolver.java:15-58`, `PluginStatus.java:5-16`,
  `PluginRuntime.java:640-646`, `jasper-app/src/main/java/dev/jasper/app/plugins/package-info.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/PresentationFactsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationSelectionTest.java`

**Interfaces:**
- Consumes: `KitParser` (Task 3), `AttemptStore.Record`, `Attempt.blocks` (Task 4), `PluginDescriptor.declaresPresentation()`,
  `presentation()`, `themes()` (Task 1).
- Produces: `public record PresentationFacts(String digest, Optional<String> problem, Optional<KitParser.Parsed> parsed)`
  with `MAX_THEME_BYTES` and `static PresentationFacts read(PresentationFacts.Source source)`;
  `PresentationFacts.Source(String id, String kitPath, KitParser.Files files, List<String> themePaths)`;
  `record Presentations(Map<String, PresentationFacts> facts, AttemptStore.Record attempts, String build)` with `NONE`,
  `static String key(PluginCandidate)`, `Optional<PresentationFacts> of(PluginCandidate)`,
  `static Presentations read(List<PluginCandidate> candidates, AttemptStore.Record attempts, String build)`;
  `static KitParser.Files PluginResources.of(PluginCandidate)`;
  `PluginResolver.resolve(candidates, state, sdk, safeMode, Presentations)` and `Resolution.presentation()`
  (`Optional<PluginCandidate>`); `PluginStatus.SAFE_MODE_LOOK`, `INVALID`, `ATTEMPT_SKIP`, `lookActive(String)`,
  `quiet()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/presentation/PresentationFactsTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 4.4: a provider's digest covers its kit, its themes and its fonts, in declared order, and names its first problem. */
class PresentationFactsTest {
    static KitParser.Files files(Map<String, byte[]> files) {
        return (path, max) -> Optional.ofNullable(files.get(path)).map(bytes -> java.util.Arrays.copyOf(bytes, Math.min(bytes.length, max + 1)));
    }

    @Test void theDigestCoversTheKitThenEachThemeThenEachFontInDeclaredOrder() throws Exception {
        var jar = new java.util.HashMap<>(KitParserTest.fonts());
        byte[] theme = jasperTheme();
        byte[] kit = KitParserTest.FULL.getBytes(StandardCharsets.UTF_8);
        jar.put("presentation/look.kit.toml", kit);
        jar.put("themes/look.json", theme);
        var facts = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", files(jar),
            List.of("themes/look.json")));
        assertThat(facts.problem()).isEmpty();
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(kit); digest.update(theme);
        for (String font : List.of("ibm-plex-sans-regular", "ibm-plex-sans-medium", "ibm-plex-sans-semibold", "ibm-plex-mono-regular", "ibm-plex-mono-medium"))
            digest.update(jar.get("presentation/fonts/" + font + ".ttf"));
        assertThat(facts.digest()).isEqualTo(HexFormat.of().formatHex(digest.digest()));
        assertThat(facts.parsed().orElseThrow().defaults().value(dev.jasper.app.config.CoreSettings.UI_THEME_DARK)).isEqualTo("look");
    }

    @Test void aMissingKitAnInvalidKitAndAThemeThatDoesNotLoadAreReported() {
        var missing = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", files(Map.of()), List.of()));
        assertThat(missing.problem()).contains("presentation: presentation/look.kit.toml is not in the plugin's jars");
        var invalid = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "look.kit.toml",
            files(Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 40\n".getBytes(StandardCharsets.UTF_8))), List.of()));
        assertThat(invalid.problem()).contains("[metrics].row_height: 40 is outside 18-28");
        assertThat(invalid.parsed()).isEmpty();
        var brokenTheme = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "look.kit.toml", files(Map.of(
            "look.kit.toml", "kit = 1\nbase = \"default\"\n[defaults]\n\"ui.theme.dark\" = \"look\"\n".getBytes(StandardCharsets.UTF_8),
            "themes/look.json", "{ not a theme".getBytes(StandardCharsets.UTF_8))), List.of("themes/look.json")));
        assertThat(brokenTheme.problem()).as("a theme that does not load is not a kit problem").isEmpty();
        assertThat(brokenTheme.parsed().orElseThrow().dropped()).containsExactly("ui.theme.dark");
    }

    /** A valid theme file with both variants: Slate Cobalt's own text. */
    static byte[] jasperTheme() throws java.io.IOException {
        try (var in = dev.jasper.ui.theme.JasperTheme.class.getResourceAsStream("/dev/jasper/ui/theme/slate-cobalt.json")) { return in.readAllBytes(); }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationSelectionTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.PresentationFacts;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 4.4 and 4.5: one presentation at a time, chosen inside the pure resolver. */
class PresentationSelectionTest {
    static final Version SDK = Version.parse("0.18.0");
    static final String BUILD = "/app/jasper-app.jar@1";

    static PluginCandidate provider(String id, PluginCandidate.Origin origin, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, "Look " + id, Version.parse("0.1.0"), "x.Main", VersionRange.parse(">=0.18.0, <0.19"),
            "", "", Set.of("presentation.provide"), Set.of(), List.of(requires), "", "", dev.jasper.ui.model.Nodes.Hue.NEUTRAL,
            dev.jasper.ui.icons.IdentityArtwork.SelectedInk.AUTHORED, List.of(), "look.kit.toml", List.of(), true);
        return new PluginCandidate(descriptor, Path.of(id), List.of(), origin);
    }

    static PluginCandidate plain(String id, PluginDescriptor.Requirement... requires) {
        var descriptor = new PluginDescriptor(id, id, Version.parse("1.0.0"), "x.Main", VersionRange.parse(">=0.18.0, <0.19"), "", "",
            Set.of(), Set.of(), List.of(requires), "", "");
        return new PluginCandidate(descriptor, Path.of(id), List.of(), PluginCandidate.Origin.BUNDLED);
    }

    static Presentations facts(Map<PluginCandidate, PresentationFacts> facts, AttemptStore.Record attempts) {
        var byKey = new java.util.HashMap<String, PresentationFacts>();
        facts.forEach((candidate, value) -> byKey.put(Presentations.key(candidate), value));
        return new Presentations(byKey, attempts, BUILD);
    }

    static PresentationFacts valid(String digest) { return new PresentationFacts(digest, Optional.empty(), Optional.empty()); }

    static Map<String, String> reasons(PluginResolver.Resolution resolution) {
        return resolution.rejected().stream().collect(java.util.stream.Collectors.toMap(PluginStatus::id, status -> status.state() + ": " + status.reason()));
    }

    @Test void aSingleValidProviderIsElectedAndLoads() {
        var look = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var resolution = PluginResolver.resolve(List.of(look, plain("a.other")), Map.of(), SDK, false,
            facts(Map.of(look, valid("d1")), AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).contains(look);
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("a.other", "b.look");
        assertThat(resolution.rejected()).isEmpty();
    }

    @Test void twoEnabledProvidersElectOneByOriginThenIdAndLosersPostNoProblem() {
        PluginCandidate bundledB = provider("b.look", PluginCandidate.Origin.BUNDLED), bundledA = provider("a.look", PluginCandidate.Origin.BUNDLED);
        PluginCandidate user = provider("0.look", PluginCandidate.Origin.USER), dev = provider("z.look", PluginCandidate.Origin.DEV);
        var reviewed = new PluginStateStore.Entry(true, Set.of("presentation.provide"), false);
        var all = facts(Map.of(bundledA, valid("a"), bundledB, valid("b"), user, valid("u"), dev, valid("z")), AttemptStore.Record.EMPTY);
        var resolution = PluginResolver.resolve(List.of(bundledA, bundledB, user, dev), Map.of("0.look", reviewed), SDK, false, all);
        assertThat(resolution.presentation()).contains(dev);
        assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "SKIPPED: the Look z.look look is active"),
            Map.entry("b.look", "SKIPPED: the Look z.look look is active"), Map.entry("0.look", "SKIPPED: the Look z.look look is active"));
        assertThat(resolution.rejected()).allSatisfy(status -> assertThat(status.quiet()).as(status.id()).isTrue());
        var withoutDev = PluginResolver.resolve(List.of(bundledB, user, bundledA), Map.of("0.look", reviewed), SDK, false, all);
        assertThat(withoutDev.presentation()).as("BUNDLED before USER, then the lowest id").contains(bundledA);
    }

    @Test void aProviderWithAProblemIsSkippedWithItsMessageAndDoesNotWin() {
        PluginCandidate broken = provider("a.look", PluginCandidate.Origin.DEV), good = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var resolution = PluginResolver.resolve(List.of(broken, good), Map.of(), SDK, false, facts(Map.of(
            broken, new PresentationFacts("x", Optional.of("[metrics].row_height: 40 is outside 18-28"), Optional.empty()), good, valid("g")),
            AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).contains(good);
        assertThat(reasons(resolution)).containsEntry("a.look", "SKIPPED: Presentation invalid: [metrics].row_height: 40 is outside 18-28");
        assertThat(resolution.rejected()).singleElement().satisfies(status -> assertThat(status.quiet()).isFalse());
        var unread = PluginResolver.resolve(List.of(good), Map.of(), SDK, false);
        assertThat(reasons(unread)).containsEntry("b.look", "SKIPPED: Presentation invalid: presentation: look.kit.toml could not be read");
    }

    @Test void safeModeDisablesEveryPresentationWhateverItsOrigin() {
        PluginCandidate bundled = provider("a.look", PluginCandidate.Origin.BUNDLED), dev = provider("b.look", PluginCandidate.Origin.DEV),
            user = provider("c.look", PluginCandidate.Origin.USER);
        var resolution = PluginResolver.resolve(List.of(bundled, dev, user, plain("d.other")), Map.of(), SDK, true,
            facts(Map.of(bundled, valid("a"), dev, valid("b"), user, valid("c")), AttemptStore.Record.EMPTY));
        assertThat(resolution.presentation()).isEmpty();
        assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "DISABLED: safe mode uses the standard look"),
            Map.entry("b.look", "DISABLED: safe mode uses the standard look"), Map.entry("c.look", "DISABLED: safe mode uses the standard look"));
        assertThat(resolution.load()).extracting(PluginCandidate::id).containsExactly("d.other");
    }

    @Test void aMatchingPendingOrFailedAttemptSkipsTheWinnerAndLeavesTheOthersLosers() {
        PluginCandidate winner = provider("a.look", PluginCandidate.Origin.BUNDLED), other = provider("b.look", PluginCandidate.Origin.BUNDLED);
        for (var state : List.of(AttemptStore.State.PENDING, AttemptStore.State.FAILED)) {
            var record = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "d1", BUILD, state)));
            var resolution = PluginResolver.resolve(List.of(winner, other), Map.of(), SDK, false,
                facts(Map.of(winner, valid("d1"), other, valid("d2")), record));
            assertThat(resolution.presentation()).as(state.name()).isEmpty();
            assertThat(reasons(resolution)).containsOnly(Map.entry("a.look", "SKIPPED: did not finish starting with this look last time"),
                Map.entry("b.look", "SKIPPED: the Look a.look look is active"));
            assertThat(resolution.rejected()).allSatisfy(status -> assertThat(status.quiet()).isTrue());
        }
        var proven = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "d1", BUILD, AttemptStore.State.PROVEN)));
        assertThat(PluginResolver.resolve(List.of(winner), Map.of(), SDK, false, facts(Map.of(winner, valid("d1")), proven)).presentation()).contains(winner);
        var changed = new AttemptStore.Record(Map.of("a.look", AttemptStore.Attempt.NONE.tried("0.1.0", "old", BUILD, AttemptStore.State.PENDING)));
        assertThat(PluginResolver.resolve(List.of(winner), Map.of(), SDK, false, facts(Map.of(winner, valid("d1")), changed)).presentation())
            .as("a changed kit gets a fresh try").contains(winner);
    }

    @Test void hardDependentsOfASkippedProviderAreSkippedAsUnmetAndOptionalOnesLoad() {
        PluginCandidate winner = provider("a.look", PluginCandidate.Origin.DEV), loser = provider("b.look", PluginCandidate.Origin.BUNDLED);
        var hard = plain("c.hard", new PluginDescriptor.Requirement("b.look", VersionRange.ANY, false));
        var optional = plain("d.soft", new PluginDescriptor.Requirement("b.look", VersionRange.ANY, true));
        var resolution = PluginResolver.resolve(List.of(winner, loser, hard, optional), Map.of(), SDK, false,
            facts(Map.of(winner, valid("a"), loser, valid("b")), AttemptStore.Record.EMPTY));
        assertThat(reasons(resolution)).containsEntry("c.hard", "SKIPPED: requires b.look, which is not available");
        assertThat(resolution.load()).extracting(PluginCandidate::id).contains("a.look", "d.soft").doesNotContain("b.look", "c.hard");
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.PresentationFactsTest' --tests 'dev.jasper.app.plugins.PresentationSelectionTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `PresentationFacts`, `Presentations`, the
five-argument `resolve`, `presentation()` and `quiet()`.

- [ ] **Step 3: Write `PresentationFacts`, `Presentations` and `PluginResources`**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/PresentationFacts.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.ui.theme.JasperTheme;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * What the resolver knows of one presentation provider (spec 4.4): the SHA-256 digest of its kit file, then each declared
 * theme file, then each font file, in declared order; its first problem; and, when valid, its parsed kit, so the winner is
 * not parsed twice.
 *
 * @param digest the look's identity, lower-case hex
 * @param problem the first kit validation failure, or empty
 * @param parsed the kit when it is valid
 */
public record PresentationFacts(String digest, Optional<String> problem, Optional<KitParser.Parsed> parsed) {
    /** The largest theme file: 1 MiB, as for imported themes. */
    public static final int MAX_THEME_BYTES = 1 << 20;

    /**
     * A provider's files.
     *
     * @param id the provider's id
     * @param kitPath the jar path of its kit file
     * @param files reads its jars
     * @param themePaths the jar paths of its theme files, in declared order
     */
    public record Source(String id, String kitPath, KitParser.Files files, List<String> themePaths) {
        /** Copies the list. */
        public Source {
            Objects.requireNonNull(id, "id"); Objects.requireNonNull(kitPath, "kitPath"); Objects.requireNonNull(files, "files");
            themePaths = List.copyOf(themePaths);
        }
    }

    /** Every part is required. */
    public PresentationFacts {
        Objects.requireNonNull(digest, "digest"); Objects.requireNonNull(problem, "problem"); Objects.requireNonNull(parsed, "parsed");
    }

    /** The facts of {@code source}; never throws. */
    public static PresentationFacts read(Source source) {
        MessageDigest digest = sha256();
        Optional<byte[]> kit = read(source.files(), source.kitPath(), KitParser.MAX_KIT_BYTES);
        kit.ifPresent(digest::update);
        var themes = new LinkedHashMap<String, Optional<JasperTheme>>();
        for (String path : source.themePaths()) {
            String file = path.substring(path.lastIndexOf('/') + 1);
            String id = file.endsWith(".json") ? file.substring(0, file.length() - ".json".length()) : file;
            Optional<byte[]> bytes = read(source.files(), path, MAX_THEME_BYTES);
            bytes.ifPresent(digest::update);
            themes.put(id, bytes.filter(content -> content.length <= MAX_THEME_BYTES).flatMap(content -> theme(content, id)));
        }
        if (kit.isEmpty())
            return new PresentationFacts(hex(digest), Optional.of("presentation: " + source.kitPath() + " is not in the plugin's jars"), Optional.empty());
        int slash = source.kitPath().lastIndexOf('/');
        String directory = slash < 0 ? "" : source.kitPath().substring(0, slash + 1);
        try {
            var parsed = KitParser.parse(kit.get(), new KitParser.Context(source.id(), directory, source.files(), themes));
            for (FontFile font : parsed.faces().files()) digest.update(font.bytes());
            return new PresentationFacts(hex(digest), Optional.empty(), Optional.of(parsed));
        } catch (KitParser.Invalid invalid) {
            return new PresentationFacts(hex(digest), Optional.of(invalid.getMessage()), Optional.empty());
        } catch (StackOverflowError deep) {
            // Rulings 13: a reader that ran out of stack must not stop start-up either.
            return new PresentationFacts(hex(digest), Optional.of("kit file: not valid TOML"), Optional.empty());
        } catch (RuntimeException defect) {
            // A defect in a reader must never stop start-up: the look is skipped with what went wrong.
            return new PresentationFacts(hex(digest), Optional.of("kit file: could not be read: " + defect), Optional.empty());
        }
    }

    private static Optional<byte[]> read(KitParser.Files files, String path, int max) {
        try { return files.read(path, max); }
        catch (RuntimeException unreadable) { return Optional.empty(); }
    }

    private static Optional<JasperTheme> theme(byte[] bytes, String id) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
            return Optional.of(JasperTheme.parse(text.startsWith("﻿") ? text.substring(1) : text, id));
        } catch (CharacterCodingException | RuntimeException | StackOverflowError invalid) { return Optional.empty(); }
    }

    private static MessageDigest sha256() {
        try { return MessageDigest.getInstance("SHA-256"); }
        catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private static String hex(MessageDigest digest) { return HexFormat.of().formatHex(digest.digest()); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/plugins/PluginResources.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.presentation.KitParser;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/** Reads files from a candidate's jars, bounded; runs no plugin code. Any thread. */
final class PluginResources {
    private static final System.Logger LOG = System.getLogger(PluginResources.class.getName());

    private PluginResources() { }

    /** The candidate's jars as a kit file reader: the first jar holding a path wins. */
    static KitParser.Files of(PluginCandidate candidate) {
        return (path, maxBytes) -> {
            for (Path jar : candidate.jars()) {
                try (var file = new JarFile(jar.toFile())) {
                    JarEntry entry = file.getJarEntry(path);
                    if (entry == null || entry.isDirectory()) continue;
                    try (var input = file.getInputStream(entry)) { return Optional.of(input.readNBytes(maxBytes + 1)); }
                } catch (IOException failure) {
                    LOG.log(System.Logger.Level.DEBUG, "Could not read " + path + " from " + jar, failure);
                }
            }
            return Optional.empty();
        };
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/plugins/Presentations.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.PresentationFacts;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * What the resolver needs to choose one presentation (spec 4.4): the facts of every candidate that declares one, by
 * {@link #key}, the attempt record, and this build's identity, so the launch and the Plugins page's prediction run the
 * same rule on the same data.
 */
record Presentations(Map<String, PresentationFacts> facts, AttemptStore.Record attempts, String build) {
    /** No presentation data: a provider without facts is skipped as unreadable. */
    static final Presentations NONE = new Presentations(Map.of(), AttemptStore.Record.EMPTY, "");

    Presentations {
        facts = Map.copyOf(facts);
        Objects.requireNonNull(attempts, "attempts"); Objects.requireNonNull(build, "build");
    }

    /** One copy of one plugin: its origin and id. */
    static String key(PluginCandidate candidate) { return candidate.origin() + ":" + candidate.id(); }

    Optional<PresentationFacts> of(PluginCandidate candidate) { return Optional.ofNullable(facts.get(key(candidate))); }

    /** Reads every presentation-declaring candidate's files; a later copy with the same key replaces an earlier one. */
    static Presentations read(List<PluginCandidate> candidates, AttemptStore.Record attempts, String build) {
        var facts = new HashMap<String, PresentationFacts>();
        for (PluginCandidate candidate : candidates) {
            if (!candidate.descriptor().declaresPresentation()) continue;
            facts.put(key(candidate), PresentationFacts.read(new PresentationFacts.Source(candidate.id(),
                candidate.descriptor().presentation(), PluginResources.of(candidate), candidate.descriptor().themes())));
        }
        return new Presentations(facts, attempts, build);
    }
}
```

- [ ] **Step 4: Elect in the resolver, name the reasons and keep the quiet ones quiet**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 5 step 4: selection in resolve, safe mode, quiet statuses."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginStatus.java", [
    ('''    static final String SUPERSEDED = "superseded by";
''', '''    static final String SUPERSEDED = "superseded by";
    /** Why safe mode disables a presentation provider. */
    static final String SAFE_MODE_LOOK = "safe mode uses the standard look";
    /** How the reason of a provider whose kit is invalid begins. */
    static final String INVALID = "Presentation invalid: ";
    /** Why the attempt record skips a look. */
    static final String ATTEMPT_SKIP = "did not finish starting with this look last time";
    private static final String LOOK_ACTIVE = " look is active";

    /** Why a provider that lost to {@code winner} is skipped. */
    static String lookActive(String winner) { return "the " + winner + LOOK_ACTIVE; }
''', 1),
    ('''    boolean superseded() { return state == State.SKIPPED && reason.startsWith(SUPERSEDED); }''',
     '''    boolean superseded() { return state == State.SKIPPED && reason.startsWith(SUPERSEDED); }

    /** Whether this status posts no plugin problem: a superseded copy, a provider that lost the look, or a look the attempt record skipped. */
    boolean quiet() {
        return superseded() || state == State.SKIPPED && (reason.startsWith("the ") && reason.endsWith(LOOK_ACTIVE) || reason.equals(ATTEMPT_SKIP));
    }''', 1)])
edit(P + "PluginResolver.java", [
    ('''    record Resolution(List<PluginCandidate> load, List<PluginStatus> rejected) {
        Resolution { load = List.copyOf(load); rejected = List.copyOf(rejected); }
    }''',
     '''    /** {@code presentation} is the elected presentation provider, which {@code load} holds. */
    record Resolution(List<PluginCandidate> load, List<PluginStatus> rejected, java.util.Optional<PluginCandidate> presentation) {
        Resolution { load = List.copyOf(load); rejected = List.copyOf(rejected); java.util.Objects.requireNonNull(presentation); }
        Resolution(List<PluginCandidate> load, List<PluginStatus> rejected) { this(load, rejected, java.util.Optional.empty()); }
    }''', 1),
    ('''    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode) {
        List<PluginStatus> rejected = new ArrayList<>();
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : candidates) {
            if (safeMode && candidate.origin() == PluginCandidate.Origin.USER) {''',
     '''    /** The core look's resolution: no presentation data, so a presentation provider is skipped as unreadable. */
    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode) {
        return resolve(candidates, state, sdk, safeMode, Presentations.NONE);
    }

    static Resolution resolve(List<PluginCandidate> candidates, Map<String, PluginStateStore.Entry> state,
                              Version sdk, boolean safeMode, Presentations presentations) {
        List<PluginStatus> rejected = new ArrayList<>();
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : candidates) {
            if (safeMode && candidate.descriptor().declaresPresentation()) {
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.DISABLED, PluginStatus.SAFE_MODE_LOOK));
                continue;
            }
            if (safeMode && candidate.origin() == PluginCandidate.Origin.USER) {''', 1),
    ('''        boolean changed = true;
        while (changed) {
            changed = false;
            for (PluginCandidate candidate : List.copyOf(chosen.values())) {
                String problem = unmet(candidate, chosen);
                if (problem == null) continue;
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, problem));
                changed = true;
            }
        }
        return new Resolution(order(chosen), rejected);
    }''',
     '''        dropUnmet(chosen, rejected);
        var presentation = elect(chosen, rejected, presentations);
        // A skipped provider's hard dependents are explained by the missing dependency, as any other's are.
        dropUnmet(chosen, rejected);
        return new Resolution(order(chosen), rejected, presentation);
    }

    private static void dropUnmet(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected) {
        boolean changed = true;
        while (changed) {
            changed = false;
            for (PluginCandidate candidate : List.copyOf(chosen.values())) {
                String problem = unmet(candidate, chosen);
                if (problem == null) continue;
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, problem));
                changed = true;
            }
        }
    }

    /**
     * Spec 4.4: of the loadable providers without a problem, DEV wins over BUNDLED over USER, then the lowest id; the
     * losers are skipped whole, and a pending or failed attempt for exactly the winner's look skips it too.
     */
    private static java.util.Optional<PluginCandidate> elect(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected,
                                                             Presentations presentations) {
        List<PluginCandidate> providers = new ArrayList<>();
        for (PluginCandidate candidate : List.copyOf(chosen.values())) {
            if (!candidate.descriptor().declaresPresentation()) continue;
            java.util.Optional<String> problem = presentations.of(candidate).map(dev.jasper.app.presentation.PresentationFacts::problem)
                .orElse(java.util.Optional.of("presentation: " + candidate.descriptor().presentation() + " could not be read"));
            if (problem.isPresent()) {
                chosen.remove(candidate.id());
                rejected.add(PluginStatus.of(candidate, PluginStatus.State.SKIPPED, PluginStatus.INVALID + problem.get()));
                continue;
            }
            providers.add(candidate);
        }
        if (providers.isEmpty()) return java.util.Optional.empty();
        providers.sort(Comparator.comparingInt((PluginCandidate candidate) -> rank(candidate.origin())).thenComparing(PluginCandidate::id));
        PluginCandidate winner = providers.getFirst();
        for (PluginCandidate loser : providers.subList(1, providers.size())) {
            chosen.remove(loser.id());
            rejected.add(PluginStatus.of(loser, PluginStatus.State.SKIPPED, PluginStatus.lookActive(winner.descriptor().name())));
        }
        String digest = presentations.of(winner).orElseThrow().digest();
        if (presentations.attempts().of(winner.id()).blocks(digest, presentations.build())) {
            chosen.remove(winner.id());
            rejected.add(PluginStatus.of(winner, PluginStatus.State.SKIPPED, PluginStatus.ATTEMPT_SKIP));
            return java.util.Optional.empty();
        }
        return java.util.Optional.of(winner);
    }

    private static int rank(PluginCandidate.Origin origin) {
        return switch (origin) { case DEV -> 0; case BUNDLED -> 1; case USER -> 2; };
    }''', 1)])
edit(P + "PluginRuntime.java", [
    ('''        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode());
        statuses.addAll(resolution.rejected());
        for (PluginStatus rejected : resolution.rejected())
            if (rejected.state() == PluginStatus.State.SKIPPED && !rejected.superseded())''',
     '''        var presentations = Presentations.read(candidates,
            new dev.jasper.app.presentation.AttemptStore(options.stateFile().toAbsolutePath().getParent()).read(), "");
        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode(), presentations);
        statuses.addAll(resolution.rejected());
        for (PluginStatus rejected : resolution.rejected())
            if (rejected.state() == PluginStatus.State.SKIPPED && !rejected.quiet())''', 1)])
PY
```

Add the ledger row of `PresentationFacts.java`, which imports `dev.jasper.ui.theme.JasperTheme`, at the end of
`docs/design/theme-engine/migration.csv` (one line):

```
jasper-app/src/main/java/dev/jasper/app/presentation/PresentationFacts.java,app.presentation,PresentationFacts,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/presentation/PresentationFactsTest.java,New in Enterprise plan 5a Task 5; a provider's digest and first kit problem; no Swing
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `PresentationFactsTest` (2), `PresentationSelectionTest` (6) and every existing plugin test
(the four-argument `resolve` still answers as before for plugins that declare no presentation).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation/PresentationFacts.java jasper-app/src/main/java/dev/jasper/app/plugins \
  jasper-app/src/test/java/dev/jasper/app/presentation/PresentationFactsTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PresentationSelectionTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Presentation provider: one active presentation

PresentationFacts digests a provider's kit, themes and fonts in declared
order and names its first kit problem. PluginResolver.resolve takes them as
Presentations and, after admission, cycles and unmet dependencies, elects
one provider: DEV, then BUNDLED, then USER, then the lowest id. Losers are
skipped with "the <winner> look is active", an invalid kit with
"Presentation invalid: <message>", and a pending or failed attempt for
exactly the winner's look skips it; safe mode disables every provider.
Losers and the attempt skip post no plugin problem.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: One look at a time on the Plugins page

Enabling or consenting to a presentation provider turns every other enabled provider off in the same locked
`plugins.toml` transaction (spec 4.4), the snapshot names what was turned off, and the restart banner says so. The
Plugins page's "after restart" prediction reads the same presentation facts and attempt record as the launch, so it
agrees with the launch, including after an attempt-record skip.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginStateStore.java` (two transactions)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginAdmin.java:34-67`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginCatalog.java:18-25,62`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java:188-191,692` (Snapshot, Launch)
- Modify: `jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java:153-154`
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationSelectionTest.java`, `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerAccess.java` (test access bridge)

**Interfaces:**
- Consumes: `Presentations.read`, the five-argument `resolve` (Task 5), `PluginStateStore.effective`, `enabling(id, enabled, optIn)` (Task 2).
- Produces: `PluginStateStore.enablingPresentation(String id, boolean optIn, Set<String> others)`,
  `consentingPresentation(String id, Set<String> capabilities, Set<String> others)`;
  `PluginRuntime.Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode, Optional<String> turnedOff)` with the
  3-argument convenience and `Snapshot withTurnedOff(Collection<String> names)`;
  `PluginCatalog.Launch(candidates, statuses, selected, safeMode, String build)` with the 4-argument convenience;
  `PluginCatalog.Disk(candidates, pending, state, Presentations presentations)` with the 3-argument convenience;
  `static String PluginManager.restartText(PluginRuntime.Snapshot)`.

- [ ] **Step 1: Write the failing tests**

Append to `PresentationSelectionTest` (before its final closing brace):

```java
    @org.junit.jupiter.api.io.TempDir Path home;

    private PluginAdmin admin(PluginCatalog.Launch launch) {
        var options = new PluginRuntime.Options(home.resolve("bundled"), home.resolve("plugins"), null, false,
            home.resolve("plugins.toml"), home.resolve("plugins.lock"));
        return new PluginAdmin(options, SDK, () -> launch, id -> 0, java.time.Duration.ofSeconds(2));
    }

    private static String look(String id, String name, boolean onByDefault) {
        return dev.jasper.app.testsupport.LookFixtures.descriptor(id, name) + dev.jasper.app.testsupport.LookFixtures.provider("look.kit.toml")
            + (onByDefault ? "" : "enabled_by_default = false\n");
    }

    @Test void enablingAProviderTurnsTheOtherEnabledProviderOffInOneTransaction() throws Exception {
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "a.look", look("a.look", "Alpha", true),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "b.look", look("b.look", "Beta", false),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        var snapshot = admin(new PluginCatalog.Launch(List.of(), List.of(), Map.of(), false)).setEnabled("b.look", true);
        var state = new PluginStateStore(home.resolve("plugins.toml"), home.resolve("plugins.lock"), java.time.Duration.ofSeconds(2)).read();
        assertThat(state).containsEntry("b.look", new PluginStateStore.Entry(true, Set.of(), false, true))
            .containsEntry("a.look", new PluginStateStore.Entry(false, Set.of(), false, false));
        assertThat(snapshot.turnedOff()).contains("Alpha");
        assertThat(snapshot.restartNeeded()).isTrue();
        assertThat(dev.jasper.app.pluginmanager.PluginManagerAccess.restartText(snapshot))
            .isEqualTo("Restart Jasper to apply your changes. Alpha was turned off: one look can be active at a time.");
        var plain = PluginStateStore.consentingPresentation("u.look", Set.of("presentation.provide"), Set.of("a.look", "b.look"))
            .apply(new java.util.TreeMap<>(state));
        assertThat(plain).containsEntry("u.look", new PluginStateStore.Entry(true, Set.of("presentation.provide"), false, false))
            .containsEntry("b.look", new PluginStateStore.Entry(false, Set.of(), false, false));
    }

    @Test void theCatalogPredictsTheLaunchIncludingAnAttemptSkip() throws Exception {
        dev.jasper.app.testsupport.LookFixtures.plugin(home.resolve("bundled"), "a.look", look("a.look", "Alpha", true),
            Map.of("look.kit.toml", dev.jasper.app.testsupport.LookFixtures.KIT));
        List<String> problems = new java.util.ArrayList<>();
        var candidates = PluginDiscovery.scan(home.resolve("bundled"), PluginCandidate.Origin.BUNDLED, problems);
        String digest = Presentations.read(candidates, AttemptStore.Record.EMPTY, BUILD).of(candidates.getFirst()).orElseThrow().digest();
        for (boolean skipped : new boolean[] {false, true}) {
            var store = new AttemptStore(home);
            store.update("a.look", attempt -> skipped ? attempt.tried("0.1.0", digest, BUILD, AttemptStore.State.PENDING) : AttemptStore.Attempt.NONE,
                AttemptStore.WAIT);
            var launched = PluginResolver.resolve(candidates, Map.of(), SDK, false, Presentations.read(candidates, store.read(), BUILD));
            var launch = new PluginCatalog.Launch(candidates, launched.rejected(), PluginCatalog.versions(launched.load()), false, BUILD);
            var snapshot = admin(launch).snapshot();
            assertThat(snapshot.restartNeeded()).as("skipped: " + skipped).isFalse();
            assertThat(launched.presentation().isPresent()).isEqualTo(!skipped);
        }
    }
```

Add to `jasper-app/src/test/java/dev/jasper/app/pluginmanager/` a test access bridge `PluginManagerAccess.java`:

```java
package dev.jasper.app.pluginmanager;

import dev.jasper.app.plugins.PluginRuntime;

/** Test access to the Plugins page's banner text from other packages' tests. */
public final class PluginManagerAccess {
    private PluginManagerAccess() { }

    /** The banner an idle, restart-needing snapshot shows. */
    public static String restartText(PluginRuntime.Snapshot snapshot) { return PluginManager.restartText(snapshot); }
}
```

and append to `PluginManagerTest` (before its final closing brace):

```java
    @org.junit.jupiter.api.Test void theRestartBannerNamesTheLooksTurnedOff() throws Exception {
        start(false, false); // the class's @AfterEach stops the runtime and closes the windows that start makes
        var plain = new dev.jasper.app.plugins.PluginRuntime.Snapshot(java.util.List.of(), true, false);
        org.assertj.core.api.Assertions.assertThat(PluginManager.restartText(plain)).isEqualTo("Restart Jasper to apply your changes.");
        org.assertj.core.api.Assertions.assertThat(PluginManager.restartText(plain.withTurnedOff(java.util.List.of("Alpha", "Beta"))))
            .isEqualTo("Restart Jasper to apply your changes. Alpha and Beta were turned off: one look can be active at a time.");
    }
```

Add the bridge's ledger row to `docs/design/theme-engine/migration.csv` only if `ThemeMigrationCoverageTest` reports it
(it imports no Swing or `dev.jasper.ui`, so it should not).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PresentationSelectionTest' --tests 'dev.jasper.app.pluginmanager.PluginManagerTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `turnedOff()`, `withTurnedOff`, `consentingPresentation`,
`restartText` and the five-argument `PluginCatalog.Launch`.

- [ ] **Step 3: Implement the transactions, the prediction and the banner**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 6 step 3: one provider at a time on the Plugins page."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginStateStore.java", [
    ('''    /** The user reviewed exactly these capabilities: that enables the plugin and cancels a pending removal. */''',
     '''    /** Enables the presentation provider {@code id} and disables every provider in {@code others}, in one transaction (spec 4.4). */
    static UnaryOperator<Map<String, Entry>> enablingPresentation(String id, boolean optIn, Set<String> others) {
        return state -> {
            enabling(id, true, optIn).apply(state);
            for (String other : others) enabling(other, false).apply(state);
            return state;
        };
    }

    /** Records consent to the presentation provider {@code id} and disables every provider in {@code others}, in one transaction. */
    static UnaryOperator<Map<String, Entry>> consentingPresentation(String id, Set<String> capabilities, Set<String> others) {
        return state -> {
            consenting(id, capabilities).apply(state);
            for (String other : others) enabling(other, false).apply(state);
            return state;
        };
    }

    /** The user reviewed exactly these capabilities: that enables the plugin and cancels a pending removal. */''', 1)])
edit(P + "PluginCatalog.java", [
    ('''    record Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode) {
        Launch { candidates = List.copyOf(candidates); statuses = List.copyOf(statuses); selected = Map.copyOf(selected); }
    }''',
     '''    /** {@code build} is the launch's build identity, which the attempt record is compared with. */
    record Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode, String build) {
        Launch { candidates = List.copyOf(candidates); statuses = List.copyOf(statuses); selected = Map.copyOf(selected); java.util.Objects.requireNonNull(build); }
        Launch(List<PluginCandidate> candidates, List<PluginStatus> statuses, Map<String, String> selected, boolean safeMode) {
            this(candidates, statuses, selected, safeMode, "");
        }
    }''', 1),
    ('''    record Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state) {
        Disk { candidates = List.copyOf(candidates); pending = Map.copyOf(pending); state = Map.copyOf(state); }
    }''',
     '''    /** {@code presentations} are the facts and attempt record the next launch would resolve with. */
    record Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state,
                Presentations presentations) {
        Disk { candidates = List.copyOf(candidates); pending = Map.copyOf(pending); state = Map.copyOf(state); java.util.Objects.requireNonNull(presentations); }
        Disk(List<PluginCandidate> candidates, Map<String, PluginCandidate> pending, Map<String, PluginStateStore.Entry> state) {
            this(candidates, pending, state, Presentations.NONE);
        }
    }''', 1),
    ('''        PluginResolver.Resolution resolution = PluginResolver.resolve(next, nextState, sdk, launch.safeMode());''',
     '''        PluginResolver.Resolution resolution = PluginResolver.resolve(next, nextState, sdk, launch.safeMode(), disk.presentations());''', 1)])
edit(P + "PluginAdmin.java", [
    ('''        for (PluginCandidate staged : PluginDiscovery.scan(options.userDirectory().resolve(PluginMaintenance.PENDING),
                PluginCandidate.Origin.USER, problems)) pending.put(staged.id(), staged);
        return new PluginCatalog.Disk(candidates, pending, store.read());''',
     '''        for (PluginCandidate staged : PluginDiscovery.scan(options.userDirectory().resolve(PluginMaintenance.PENDING),
                PluginCandidate.Origin.USER, problems)) pending.put(staged.id(), staged);
        var everything = new ArrayList<>(candidates);
        everything.addAll(pending.values());
        var attempts = new dev.jasper.app.presentation.AttemptStore(options.stateFile().toAbsolutePath().getParent()).read();
        return new PluginCatalog.Disk(candidates, pending, store.read(), Presentations.read(everything, attempts, launch.get().build()));''', 1),
    ('''        boolean optIn = enabled && subject.origin() == PluginCandidate.Origin.BUNDLED && !subject.descriptor().enabledByDefault();
        store.transact(PluginStateStore.enabling(id, enabled, optIn));
        return snapshot();''',
     '''        boolean optIn = enabled && subject.origin() == PluginCandidate.Origin.BUNDLED && !subject.descriptor().enabledByDefault();
        if (enabled && subject.descriptor().declaresPresentation()) {
            var others = otherProviders(disk, id);
            store.transact(PluginStateStore.enablingPresentation(id, optIn, others.keySet()));
            return snapshot().withTurnedOff(others.values());
        }
        store.transact(PluginStateStore.enabling(id, enabled, optIn));
        return snapshot();''', 1),
    ('''        requireReviewed(subject, reviewed);
        store.transact(PluginStateStore.consenting(id, subject.descriptor().capabilities()));
        return snapshot();
    }''',
     '''        requireReviewed(subject, reviewed);
        if (subject.descriptor().declaresPresentation()) {
            var others = otherProviders(disk(), id);
            store.transact(PluginStateStore.consentingPresentation(id, subject.descriptor().capabilities(), others.keySet()));
            return snapshot().withTurnedOff(others.values());
        }
        store.transact(PluginStateStore.consenting(id, subject.descriptor().capabilities()));
        return snapshot();
    }

    /**
     * The other presentation providers the next start would treat as enabled, by id with their names: effectively enabled
     * and, for a user copy, reviewed.
     */
    private static Map<String, String> otherProviders(PluginCatalog.Disk disk, String id) {
        var others = new TreeMap<String, String>();
        var all = new ArrayList<>(disk.candidates());
        all.addAll(disk.pending().values());
        for (PluginCandidate candidate : all) {
            if (candidate.id().equals(id) || !candidate.descriptor().declaresPresentation()) continue;
            PluginStateStore.Entry entry = disk.state().get(candidate.id());
            boolean unreviewed = candidate.origin() == PluginCandidate.Origin.USER && (entry == null || entry.optIn());
            if (PluginStateStore.effective(entry, candidate) && !unreviewed) others.putIfAbsent(candidate.id(), candidate.descriptor().name());
        }
        return others;
    }''', 1)])
edit(P + "PluginRuntime.java", [
    ('''    public record Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode) {
        /** Copies the rows. */
        public Snapshot { rows = List.copyOf(rows); }
    }''',
     '''    public record Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode, Optional<String> turnedOff) {
        /** Copies the rows. */
        public Snapshot { rows = List.copyOf(rows); Objects.requireNonNull(turnedOff, "turnedOff"); }

        /** A snapshot that turned no look off. */
        public Snapshot(List<Row> rows, boolean restartNeeded, boolean safeMode) { this(rows, restartNeeded, safeMode, Optional.empty()); }

        /** This snapshot naming the providers an enable turned off, joined with " and "; none leaves it unchanged. */
        public Snapshot withTurnedOff(java.util.Collection<String> names) {
            return names.isEmpty() ? this : new Snapshot(rows, restartNeeded, safeMode, Optional.of(String.join(" and ", names)));
        }
    }''', 1),
    ('''     * @param safeMode whether this process runs without user plugins
     */''',
     '''     * @param safeMode whether this process runs without user plugins
     * @param turnedOff the presentation providers the change just made turned off, for the banner; empty for none
     */''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java", [
    ('''                else if (snapshot.restartNeeded()) panel.banner("Restart Jasper to apply your changes.",''',
     '''                else if (snapshot.restartNeeded()) panel.banner(restartText(snapshot),''', 1),
    ('''    private void toggle(PluginRuntime.Row row) {''',
     '''    /** The restart banner's text, naming the looks an enable turned off (spec 4.4). */
    static String restartText(PluginRuntime.Snapshot snapshot) {
        return "Restart Jasper to apply your changes." + snapshot.turnedOff()
            .map(names -> " " + names + (names.contains(" and ") ? " were" : " was") + " turned off: one look can be active at a time.").orElse("");
    }

    private void toggle(PluginRuntime.Row row) {''', 1)])
PY
grep -n "public record Snapshot" -B4 jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java | head -8
```

The last command shows the Snapshot record's Javadoc; check that the `@param turnedOff` line now sits in it (the edit
anchors on the `safeMode` parameter line, which appears once, in that Javadoc).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.pluginmanager.*' :jasper-app:javadoc`
Expected: PASS, javadoc without errors.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/pluginmanager/PluginManager.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PresentationSelectionTest.java \
  jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerAccess.java \
  jasper-app/src/test/java/dev/jasper/app/pluginmanager/PluginManagerTest.java
git commit -F - <<'EOF'
Presentation provider: one look at a time on the Plugins page

Enabling or consenting to a presentation provider disables every other
enabled provider in the same locked plugins.toml transaction, and the
restart banner adds "<Other> was turned off: one look can be active at a
time." The "after restart" prediction resolves with the same presentation
facts, attempt record and build as the launch, so the two agree, also when
the attempt record skipped the look.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Kit defaults for settings

`ConfigService` takes a `DefaultSource` at construction (spec 5.8), so every snapshot, reload and syntax-error state,
and the first snapshot of a fresh home, reads an allowlisted key as user-saved, then kit default, then core default.
Settings' draft shows and resets to the effective default, and the audited comparisons use it.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/config/ConfigSnapshot.java:78-111`, `ConfigLoader.java:66-83`,
  `ConfigService.java:48-60,107-121,396-399,429-434`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java:61-62`
- Modify: `jasper-app/src/main/java/dev/jasper/app/settingsui/SettingsDraft.java:20-35,70-74`, `SchemaPage.java:465,511-512`,
  `AppearanceSelection.java:58-59,155-159`, `SettingsDialog.java:164`, `PlatformFontSize.java:12-25`,
  `Editors.java:196-199,500-503` (`OptionalNumber`), `WorkspaceEditors.java:15-34` (`Opacity`)
- Modify: `config.example.toml:13-15,62-63,134-137`, `docs/configuration.md:190,203,227-228`
- Test: `jasper-app/src/test/java/dev/jasper/app/config/DefaultsPrecedenceTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java`

**Interfaces:**
- Consumes: `DefaultSource` (Task 3).
- Produces: `ConfigSnapshot.defaults(DefaultSource)`, `ConfigSnapshot.from(SettingValues, DefaultSource, Map, Map)`;
  `ConfigLoader.parse(Path, String, boolean, DefaultSource)`; `ConfigService(Path, boolean, DefaultSource)`,
  `ConfigService(Path, boolean, ScheduledExecutorService, Consumer<Runnable>, DefaultSource)`, `DefaultSource
  ConfigService.defaults()`; `ConfigService.State` gains a ninth component `DefaultSource defaults` (the 8- and
  4-argument forms keep `DefaultSource.CORE`); `SettingsDraft(SettingValues, DefaultSource)` and
  `<T> T SettingsDraft.defaultValue(Setting<T>)`; package-private `static <T> T Editors.effectiveDefault(SettingsDraft,
  Setting<T>)`. The no-source overloads stay and mean the core look.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/config/DefaultsPrecedenceTest.java`:

```java
package dev.jasper.app.config;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.settings.SettingValues;
import dev.jasper.app.settingsui.SettingsDraft;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 5.8: user-saved, then kit default, then core default, from the first snapshot of a fresh home to Settings' Reset. */
class DefaultsPrecedenceTest {
    static final DefaultSource KIT = new DefaultSource(Map.of(CoreSettings.PALETTE_VISIBLE_ROWS, 8, CoreSettings.WINDOW_TOOLBAR_HEIGHT, 34,
        CoreSettings.UI_THEME_DARK, "catppuccin-mocha", CoreSettings.UI_THEME_LIGHT, "catppuccin-latte"));
    @TempDir Path home;

    private ConfigService service(Path file) {
        return new ConfigService(file, false, Executors.newSingleThreadScheduledExecutor(), Runnable::run, KIT);
    }

    @Test void aFreshHomesFirstSnapshotCarriesTheKitsDefaults() {
        try (var service = service(home.resolve("missing/config.toml"))) {
            var snapshot = service.initialState().snapshot();
            assertThat(snapshot.visibleRows()).isEqualTo(8);
            assertThat(snapshot.toolbarHeight()).isEqualTo(34);
            assertThat(snapshot.darkTheme()).isEqualTo("catppuccin-mocha");
            assertThat(snapshot.lightTheme()).isEqualTo("catppuccin-latte");
            assertThat(service.initialState().defaults()).isSameAs(KIT);
            assertThat(service.defaults()).isSameAs(KIT);
        }
    }

    @Test void aSavedValueWinsAndASyntaxErrorKeepsTheKitsDefaults() throws Exception {
        Path file = home.resolve("config.toml");
        Files.writeString(file, "[palette]\nvisible_rows = 3\n");
        try (var service = service(file)) {
            assertThat(service.initialState().snapshot().visibleRows()).isEqualTo(3);
            assertThat(service.initialState().snapshot().toolbarHeight()).as("unsaved: the kit's").isEqualTo(34);
        }
        Files.writeString(file, "[palette\n");
        try (var service = service(file)) { assertThat(service.initialState().snapshot().visibleRows()).isEqualTo(8); }
        assertThat(ConfigLoader.parse(file, "[palette\n", false, KIT).snapshot().darkTheme()).isEqualTo("catppuccin-mocha");
        assertThat(ConfigLoader.parse(file, "", false).snapshot().darkTheme()).as("the core look").isEqualTo(CoreSettings.UI_THEME_DARK.defaultValue());
        assertThat(ConfigSnapshot.defaults(DefaultSource.CORE)).isEqualTo(ConfigSnapshot.defaults());
    }

    @Test void theDraftShowsAndResetsToTheEffectiveDefault() {
        var draft = new SettingsDraft(SettingValues.defaults(CoreSettings.SCHEMA), KIT);
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).as("an unsaved allowlisted key").isEqualTo(8);
        assertThat(draft.defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(8);
        draft.stage(CoreSettings.PALETTE_VISIBLE_ROWS, Optional.of(12));
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(12);
        draft.stage(CoreSettings.PALETTE_VISIBLE_ROWS, Optional.empty());
        assertThat(draft.value(CoreSettings.PALETTE_VISIBLE_ROWS)).as("Reset gives the effective default").isEqualTo(8);
        assertThat(draft.defaultValue(CoreSettings.UI_FONT_SIZE)).as("outside the allowlist: the core default")
            .isEqualTo(CoreSettings.UI_FONT_SIZE.defaultValue());
        var saved = ConfigLoader.parse(home.resolve("config.toml"), "[palette]\nvisible_rows = 3\n", false, KIT).values();
        assertThat(new SettingsDraft(saved, KIT).value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(3);
        assertThat(new SettingsDraft(saved).defaultValue(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(CoreSettings.PALETTE_VISIBLE_ROWS.defaultValue());
    }

    /** Spec 5.8's audit: the editors that compare with a core default edit no allowlisted key, whose rows go through SchemaPage. */
    @Test void noAllowlistedKeyReachesACoreDefaultComparison() throws Exception {
        Path ui = Path.of(System.getProperty("jasper.repoRoot")).resolve("jasper-app/src/main/java/dev/jasper/app/settingsui");
        for (String editor : List.of("PlatformFontSize.java", "WorkspaceEditors.java", "NotificationGroupsEditor.java")) {
            String text = Files.readString(ui.resolve(editor));
            for (String key : List.of("UI_THEME_LIGHT", "UI_THEME_DARK", "PALETTE_VISIBLE_ROWS", "WINDOW_TOOLBAR_HEIGHT"))
                assertThat(text).as(editor + " " + key).doesNotContain(key);
        }
        String pages = Files.readString(ui.resolve("CorePages.java"));
        assertThat(pages).contains(".row(WINDOW_TOOLBAR_HEIGHT, Editors.stepper(\"px\"))", ".row(PALETTE_VISIBLE_ROWS, Editors.stepper(\"\"))",
            ".row(UI_THEME_DARK).shownWhen(false)", ".row(UI_THEME_LIGHT).shownWhen(false)");
        assertThat(Files.readString(ui.resolve("Editors.java"))).as("OptionalNumber is made only for Float settings")
            .contains("static final class OptionalNumber implements ValueEditor<Float>");
        // Spec 5.8's table: these editors compare with the effective default (equal to the core one outside the allowlist).
        assertThat(Files.readString(ui.resolve("Editors.java"))).contains("unset = effectiveDefault(draft, setting);");
        assertThat(Files.readString(ui.resolve("PlatformFontSize.java"))).contains("Editors.effectiveDefault(draft,UI_FONT_SIZE)")
            .doesNotContain("UI_FONT_SIZE.defaultValue()");
        assertThat(Files.readString(ui.resolve("WorkspaceEditors.java"))).doesNotContain("UI_WORKSPACE_OPACITY.defaultValue()");
    }
}
```

Append to `AppearanceSelectionTest` (before its final closing brace):

```java
    /** Spec 5.8: under a kit default, the untouched pick is the kit's; picking the core default saves it explicitly. */
    @Test void underAKitDefaultPickingTheCoreDefaultStagesAnExplicitValue() {
        var kit = new dev.jasper.app.config.DefaultSource(java.util.Map.of(CoreSettings.UI_THEME_DARK, "ocean"));
        var draft = new SettingsDraft(ConfigLoader.parse(Path.of("config.toml"), "", true).values(), kit);
        var editor = editor(draft);
        editor.show(AppearanceMode.SYSTEM);
        assertThat(pick(editor, 1).selectedId()).isEqualTo("ocean");
        var reset = (Nodes.Button) ((Nodes.Stack) ((Nodes.SettingRow) parts(editor).get(1)).control()).children().get(1);
        assertThat(reset.state().visible()).as("at the effective defaults").isFalse();
        assertThat(editor.companionValues()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.empty());
        assertThat(editor.event("ui.appearance", new UiEvent.Selected("ui.appearance:dark-theme", java.util.Set.of("slate-cobalt")))).isTrue();
        assertThat(editor.companionValues()).containsEntry(CoreSettings.UI_THEME_DARK, Optional.of("slate-cobalt"));
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.DefaultsPrecedenceTest' --tests 'dev.jasper.app.settingsui.AppearanceSelectionTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for the five-argument `ConfigService`, `defaults()` on
the service and the state, the four-argument `ConfigLoader.parse`, `ConfigSnapshot.defaults(DefaultSource)` and the
two-argument `SettingsDraft`.

- [ ] **Step 3: Thread the source through the configuration**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 7 step 3: DefaultSource in the configuration and in Settings."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
C = "jasper-app/src/main/java/dev/jasper/app/config/"
edit(C + "ConfigSnapshot.java", [
    ('''    /** The snapshot for loaded settings plus the keybindings and legacy plugin tables their own readers produced. */''',
     '''    /** The defaults under {@code source}: the core defaults with the look's defaults of its allowlisted keys (spec 5.8). */
    public static ConfigSnapshot defaults(DefaultSource source) {
        return defaults().toBuilder().darkTheme(source.value(CoreSettings.UI_THEME_DARK)).lightTheme(source.value(CoreSettings.UI_THEME_LIGHT))
            .visibleRows(source.value(CoreSettings.PALETTE_VISIBLE_ROWS)).toolbarHeight(source.value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)).build();
    }

    /** As {@link #from(SettingValues, Map, Map)}, an allowlisted key the file does not set reading as {@code defaults} gives it. */
    public static ConfigSnapshot from(SettingValues values, DefaultSource defaults, Map<String, String> keybindings,
                                      Map<String, Map<String, Object>> plugins) {
        return from(values, keybindings, plugins).toBuilder()
            .darkTheme(values.saved(CoreSettings.UI_THEME_DARK).orElseGet(() -> defaults.value(CoreSettings.UI_THEME_DARK)))
            .lightTheme(values.saved(CoreSettings.UI_THEME_LIGHT).orElseGet(() -> defaults.value(CoreSettings.UI_THEME_LIGHT)))
            .visibleRows(values.saved(CoreSettings.PALETTE_VISIBLE_ROWS).orElseGet(() -> defaults.value(CoreSettings.PALETTE_VISIBLE_ROWS)))
            .toolbarHeight(values.saved(CoreSettings.WINDOW_TOOLBAR_HEIGHT).orElseGet(() -> defaults.value(CoreSettings.WINDOW_TOOLBAR_HEIGHT)))
            .build();
    }

    /** The snapshot for loaded settings plus the keybindings and legacy plugin tables their own readers produced. */''', 1)])
edit(C + "ConfigLoader.java", [
    ('''    private ConfigLoader(Path file, String text, boolean macOs) {
        this.file = Objects.requireNonNull(file, "file");
        this.reader = new SchemaReader(CoreSettings.SCHEMA, text, (path, value) -> value);
        this.macOs = macOs;
    }

    public static Result parse(Path file, String text, boolean macOs) {
        return new ConfigLoader(file, text, macOs).parse();
    }''',
     '''    private final DefaultSource defaults;

    private ConfigLoader(Path file, String text, boolean macOs, DefaultSource defaults) {
        this.file = Objects.requireNonNull(file, "file");
        this.reader = new SchemaReader(CoreSettings.SCHEMA, text, (path, value) -> value);
        this.macOs = macOs;
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    /** The core look's reading of {@code text}. */
    public static Result parse(Path file, String text, boolean macOs) { return parse(file, text, macOs, DefaultSource.CORE); }

    /** {@code text} read with {@code defaults} for the allowlisted keys it does not set. */
    public static Result parse(Path file, String text, boolean macOs, DefaultSource defaults) {
        return new ConfigLoader(file, text, macOs, defaults).parse();
    }''', 1),
    ('''            return new Result(ConfigSnapshot.defaults(), diagnostics, true, read.values(), List.of(), List.of());
        return new Result(ConfigSnapshot.from(read.values(), keybindings, plugins), diagnostics, read.rejected(), read.values(),''',
     '''            return new Result(ConfigSnapshot.defaults(defaults), diagnostics, true, read.values(), List.of(), List.of());
        return new Result(ConfigSnapshot.from(read.values(), defaults, keybindings, plugins), diagnostics, read.rejected(), read.values(),''', 1)])
edit(C + "ConfigService.java", [
    ('''     * @param rejected whether the file could not be applied (syntax or type errors, or an unreadable file)
     */
    public record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                        List<LegacyKey> legacyKeys, List<List<String>> deadTables, SettingValues values, boolean rejected) {
        public State {
            Objects.requireNonNull(snapshot, "snapshot");''',
     '''     * @param rejected whether the file could not be applied (syntax or type errors, or an unreadable file)
     * @param defaults the look's defaults the snapshot was read with
     */
    public record State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                        List<LegacyKey> legacyKeys, List<List<String>> deadTables, SettingValues values, boolean rejected,
                        DefaultSource defaults) {
        public State {
            Objects.requireNonNull(defaults, "defaults");
            Objects.requireNonNull(snapshot, "snapshot");''', 1),
    ('''            this(snapshot, diagnostics, file, present, legacyKeys, deadTables, SettingValues.defaults(CoreSettings.SCHEMA), false);
        }''',
     '''            this(snapshot, diagnostics, file, present, legacyKeys, deadTables, SettingValues.defaults(CoreSettings.SCHEMA), false);
        }

        /** A state read under the core look's defaults. */
        public State(ConfigSnapshot snapshot, List<ConfigDiagnostic> diagnostics, Path file, boolean present,
                     List<LegacyKey> legacyKeys, List<List<String>> deadTables, SettingValues values, boolean rejected) {
            this(snapshot, diagnostics, file, present, legacyKeys, deadTables, values, rejected, DefaultSource.CORE);
        }''', 1),
    ('''    public ConfigService(Path file, boolean macOs) {
        this(file, macOs, newWorker(), SwingUtilities::invokeLater);
    }

    /** The supplied single-thread scheduled worker is owned by this service, including shutdown. */
    public ConfigService(Path file, boolean macOs, ScheduledExecutorService worker, Consumer<Runnable> publisher) {
        requireOffEdt();
        this.file = Objects.requireNonNull(file, "file");
        this.macOs = macOs;''',
     '''    public ConfigService(Path file, boolean macOs) {
        this(file, macOs, DefaultSource.CORE);
    }

    /** A service whose snapshots read the allowlisted keys the file does not set from {@code defaults} (spec 5.8). */
    public ConfigService(Path file, boolean macOs, DefaultSource defaults) {
        this(file, macOs, newWorker(), SwingUtilities::invokeLater, defaults);
    }

    /** The supplied single-thread scheduled worker is owned by this service, including shutdown. */
    public ConfigService(Path file, boolean macOs, ScheduledExecutorService worker, Consumer<Runnable> publisher) {
        this(file, macOs, worker, publisher, DefaultSource.CORE);
    }

    /** As the four-argument form, under {@code defaults}. */
    public ConfigService(Path file, boolean macOs, ScheduledExecutorService worker, Consumer<Runnable> publisher, DefaultSource defaults) {
        requireOffEdt();
        this.defaults = Objects.requireNonNull(defaults, "defaults");
        this.file = Objects.requireNonNull(file, "file");
        this.macOs = macOs;''', 1),
    ('''        lastConfig = readState(new State(ConfigSnapshot.defaults(), List.of(), file, false), true);''',
     '''        lastConfig = readState(empty(), true);''', 1),
    ('''    public State initialState() { return initialState; }''',
     '''    public State initialState() { return initialState; }
    /** The look's defaults every snapshot of this service reads with. */
    public DefaultSource defaults() { return defaults; }

    /** The state of a missing file: the defaults under this service's source. */
    private State empty() {
        return new State(ConfigSnapshot.defaults(defaults), List.of(), file, false, List.of(), List.of(),
            SettingValues.defaults(CoreSettings.SCHEMA), false, defaults);
    }''', 1),
    ('''            var parsed = ConfigLoader.parse(file, text, macOs);
            return parsed.rejected()
                ? new State(lastGood.snapshot(), parsed.diagnostics(), file, true, List.of(), List.of(), lastGood.values(), true)
                : new State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(), parsed.values(), false);''',
     '''            var parsed = ConfigLoader.parse(file, text, macOs, defaults);
            return parsed.rejected()
                ? new State(lastGood.snapshot(), parsed.diagnostics(), file, true, List.of(), List.of(), lastGood.values(), true, defaults)
                : new State(parsed.snapshot(), parsed.diagnostics(), file, true, parsed.legacyKeys(), parsed.deadTables(), parsed.values(), false, defaults);''', 1),
    ('''        fingerprint = MISSING;
        return new State(ConfigSnapshot.defaults(), List.of(), file, false);''',
     '''        fingerprint = MISSING;
        return empty();''', 1),
    ('''            ConfigDiagnostic.Severity.ERROR, file, 0, 0, "", message)), file, true, List.of(), List.of(), lastGood.values(), true);''',
     '''            ConfigDiagnostic.Severity.ERROR, file, 0, 0, "", message)), file, true, List.of(), List.of(), lastGood.values(), true, defaults);''', 1)])
text = pathlib.Path(C + "ConfigService.java").read_text(encoding="utf-8")
if text.count("ConfigLoader.parse(file, updated, macOs)") != 2 or text.count("ConfigLoader.parse(file, source, macOs)") != 1:
    raise SystemExit("ConfigService: the save paths' parse calls moved")
text = text.replace("ConfigLoader.parse(file, updated, macOs)", "ConfigLoader.parse(file, updated, macOs, defaults)")
text = text.replace("ConfigLoader.parse(file, source, macOs)", "ConfigLoader.parse(file, source, macOs, defaults)")
text = text.replace("    private final boolean macOs;\n", "    private final boolean macOs;\n    private final DefaultSource defaults;\n", 1)
pathlib.Path(C + "ConfigService.java").write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java", [
    ('''            state.values(), state.rejected());''', '''            state.values(), state.rejected(), state.defaults());''', 1)])
S = "jasper-app/src/main/java/dev/jasper/app/settingsui/"
edit(S + "SettingsDraft.java", [
    ('''    private SettingValues baseline;

    public SettingsDraft(SettingValues baseline) { this.baseline = Objects.requireNonNull(baseline, "baseline"); }''',
     '''    private SettingValues baseline;
    private final dev.jasper.app.config.DefaultSource defaults;

    /** A draft over {@code baseline} under the core look's defaults. */
    public SettingsDraft(SettingValues baseline) { this(baseline, dev.jasper.app.config.DefaultSource.CORE); }

    /** A draft over {@code baseline} whose defaults are {@code defaults}'s (spec 5.8). */
    public SettingsDraft(SettingValues baseline, dev.jasper.app.config.DefaultSource defaults) {
        this.baseline = Objects.requireNonNull(baseline, "baseline");
        this.defaults = Objects.requireNonNull(defaults, "defaults");
    }

    /** {@code setting}'s effective default: the look's, else the core default. */
    public <T> T defaultValue(Setting<T> setting) { return defaults.value(setting); }''', 1),
    ('''    /** The value the dialog shows: the pending edit, else the saved value, else the default. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        if (!pending.containsKey(setting)) return baseline.get(setting);
        Optional<T> staged = (Optional<T>) pending.get(setting);
        return staged.orElse(setting.defaultValue());
    }''',
     '''    /** The value the dialog shows: the pending edit, else the saved value, else the effective default. */
    @SuppressWarnings("unchecked")
    public <T> T value(Setting<T> setting) {
        if (!pending.containsKey(setting)) return baseline.saved(setting).orElseGet(() -> defaultValue(setting));
        Optional<T> staged = (Optional<T>) pending.get(setting);
        return staged.orElseGet(() -> defaultValue(setting));
    }''', 1)])
edit(S + "SchemaPage.java", [
    ('''        if (row.editor.removesAtDefault() && Objects.equals(value, row.setting.defaultValue())) {''',
     '''        if (row.editor.removesAtDefault() && Objects.equals(value, context.draft().defaultValue(row.setting))) {''', 1),
    ('''        row.resettable = !Objects.equals(value, row.setting.defaultValue()) || row.editor.relatedSettings().stream()
            .anyMatch(setting -> !Objects.equals(context.draft().value(setting), setting.defaultValue()));''',
     '''        row.resettable = !Objects.equals(value, context.draft().defaultValue(row.setting)) || row.editor.relatedSettings().stream()
            .anyMatch(setting -> !Objects.equals(context.draft().value(setting), context.draft().defaultValue(setting)));''', 1)])
edit(S + "AppearanceSelection.java", [
    ('''        boolean modified = value != UI_APPEARANCE.defaultValue() || !dark.equals(UI_THEME_DARK.defaultValue())
            || !light.equals(UI_THEME_LIGHT.defaultValue());''',
     '''        boolean modified = value != draft.defaultValue(UI_APPEARANCE) || !dark.equals(draft.defaultValue(UI_THEME_DARK))
            || !light.equals(draft.defaultValue(UI_THEME_LIGHT));''', 1),
    ('''        if (saved.map(pick::equals).orElse(pick.equals(setting.defaultValue()))) return saved;
        return pick.equals(setting.defaultValue()) ? Optional.empty() : Optional.of(pick);''',
     '''        String effective = draft.defaultValue(setting);
        if (saved.map(pick::equals).orElse(pick.equals(effective))) return saved;
        // Picking the core default while the look overrides it is a choice of its own, so it is saved.
        return pick.equals(effective) ? Optional.empty() : Optional.of(pick);''', 1)])
edit(S + "SettingsDialog.java", [
    ('''        draft = new SettingsDraft(state.values());''', '''        draft = new SettingsDraft(state.values(), state.defaults());''', 1)])
# Spec 5.8's audit: the optional-value editors compare with the effective default as well (Rulings 17).
edit(S + "Editors.java", [
    ('''        return new Factory<>((setting, context) -> new OptionalNumber(setting, label, step, unit), List.of());
    }''',
     '''        return new Factory<>((setting, context) -> new OptionalNumber(setting, label, step, unit, context.draft()), List.of());
    }

    /** {@code setting}'s effective default under {@code draft} (spec 5.8); the core default for a page made without a draft. */
    static <T> T effectiveDefault(SettingsDraft draft, Setting<T> setting) {
        return draft == null ? setting.defaultValue() : draft.defaultValue(setting);
    }''', 1),
    ('''        OptionalNumber(Setting<Float> setting, String label, double step, String unit) {''',
     '''        OptionalNumber(Setting<Float> setting, String label, double step, String unit, SettingsDraft draft) {''', 1),
    ('''            unset = setting.defaultValue();''', '''            unset = effectiveDefault(draft, setting);''', 1)])
edit(S + "PlatformFontSize.java", [
    ('''(setting,context)->new PlatformFontSize(),''', '''(setting,context)->new PlatformFontSize(context.draft()),''', 1),
    ('''    PlatformFontSize(){var kind=''', '''    private final SettingsDraft draft;
    PlatformFontSize(SettingsDraft draft){this.draft=draft;var kind=''', 1),
    ('''return inherited?UI_FONT_SIZE.defaultValue():number.value();''', '''return inherited?Editors.effectiveDefault(draft,UI_FONT_SIZE):number.value();''', 1),
    ('''inherited=value.equals(UI_FONT_SIZE.defaultValue());''', '''inherited=value.equals(Editors.effectiveDefault(draft,UI_FONT_SIZE));''', 1)])
edit(S + "WorkspaceEditors.java", [
    ('''UI_WORKSPACE_OPACITY.defaultValue()''', '''Editors.effectiveDefault(draft,UI_WORKSPACE_OPACITY)''', 4)])
edit("config.example.toml", [
    ('''# Range: 28-64 logical pixels; out-of-range values use the nearest limit.
toolbar_height = 30''', '''# Range: 28-64 logical pixels; out-of-range values use the nearest limit.
# The default depends on the active look.
toolbar_height = 30''', 1),
    ('''# Live. Result rows the palette list shows before it scrolls. Range: 1-20.
visible_rows = 5''', '''# Live. Result rows the palette list shows before it scrolls. Range: 1-20.
# The default depends on the active look.
visible_rows = 5''', 1),
    ('''A theme without that variant is reported and Slate Cobalt is used.
dark = "slate-cobalt"''', '''A theme without that variant is reported and Slate Cobalt is used.
# The default depends on the active look.
dark = "slate-cobalt"''', 1)])
edit("docs/configuration.md", [
    ('''buttons are the height minus 6 and icon-only buttons stay square; out-of-range values use the nearest limit with a warning | Live |''',
     '''buttons are the height minus 6 and icon-only buttons stay square; out-of-range values use the nearest limit with a warning; the default depends on the active look | Live |''', 1),
    ('''| `palette.visible_rows` | `5` | Integer 1–20; the old `palette.max_results` key is still read | Live |''',
     '''| `palette.visible_rows` | `5` | Integer 1–20; the old `palette.max_results` key is still read; the default depends on the active look | Live |''', 1),
    ('''| `ui.theme.dark` | `"slate-cobalt"` | A theme id: the theme the Dark appearance shows ([Themes](themes.md)) | Live |
| `ui.theme.light` | `"slate-cobalt"` | A theme id: the theme the Light appearance shows ([Themes](themes.md)) | Live |''',
     '''| `ui.theme.dark` | `"slate-cobalt"` | A theme id: the theme the Dark appearance shows ([Themes](themes.md)); the default depends on the active look | Live |
| `ui.theme.light` | `"slate-cobalt"` | A theme id: the theme the Light appearance shows ([Themes](themes.md)); the default depends on the active look | Live |''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.config.*' --tests 'dev.jasper.app.settingsui.*' --tests 'dev.jasper.app.application.*Config*'`
Expected: PASS: `DefaultsPrecedenceTest` (4), the new `AppearanceSelectionTest` case, `ConfigTemplateTest` (the example
file's comments) and every existing config, Settings and configuration-controller test.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/config jasper-app/src/main/java/dev/jasper/app/settingsui \
  jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java config.example.toml docs/configuration.md \
  jasper-app/src/test/java/dev/jasper/app/config/DefaultsPrecedenceTest.java \
  jasper-app/src/test/java/dev/jasper/app/settingsui/AppearanceSelectionTest.java
git commit -F - <<'EOF'
Presentation provider: kit defaults for four settings

ConfigService takes a DefaultSource at construction, so every snapshot,
reload, syntax-error state and a fresh home's first snapshot read
ui.theme.light, ui.theme.dark, palette.visible_rows and
window.toolbar_height as saved, then the look's default, then the core
default. Settings' draft shows and resets to the effective default,
SchemaPage, the Appearance picks and the optional-value editors compare
with it, and picking the core default under a look that overrides it
saves an explicit value.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: The projections and the `Presentation` value

`Presentation` is the one value a process runs with (spec 4.9): the provider (or none), the jasper-ui kit, the parsed
chrome, whether the base is classic, the declared faces, the kit defaults and every projection of spec 9.1, computed once
by the 9.1 table. A group of fields takes the file's chrome only once its plan has landed (`Presentation.LANDED`, empty
here; Rulings 2), so a parsed chrome point has no effect yet; `DesktopFlags` is computed from the file (plan 6 applies it).
`Presentation.fallback(Host)` is spec 4.7's explicit fallback value.

**Files:**
- Create in `jasper-app/src/main/java/dev/jasper/app/presentation/`: `DesktopFlags.java`, `WorkspaceLook.java`,
  `ShellLook.java`, `PaletteLook.java`, `SettingsChrome.java`, `Composition.java`, `DialogMapping.java`, `Motion.java`,
  `TitleFormat.java`, `DockLayoutKey.java`, `AppPanels.java`, `Projections.java`, `Presentation.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java` (readers), `docs/design/theme-engine/migration.csv` (three rows: `Presentation`, `ShellLook`, `ProjectionsTest`)
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`

**Interfaces:**
- Consumes: `ChromeSpec`, `KitParser.Parsed`, `KitParser.Faces` (Task 3), `DefaultSource`; `dev.jasper.app.dock.Slot`.
- Produces: `DesktopFlags(boolean screenMenuBar, boolean nativeTitle)` with `STANDARD = (true, false)`;
  `WorkspaceLook(Menus, Header, Stripes, Tabs, Status, boolean runItem, FrameStyle)` with nested enums `Menus { STANDARD,
  CLASSIC }`, `Header { UNIFIED, CLASSIC }`, `Stripes { RAILS, LABELLED }`, `Tabs { EQUAL, CLASSIC }`, `Status { SLASH,
  RULED }`, `FrameStyle { STANDARD, FLUSH }`; `ShellLook(boolean unifiedTitle, boolean windowMenus, boolean
  followThemeAppearance)`; `PaletteLook(View { CARD, RUN_WINDOW }, Home { TITLE_FIELD, CORNER })`;
  `SettingsChrome(boolean integratedTitle, boolean collapsibleNav, boolean inlineReset, Set<String> hiddenKeys, String
  fontRowCopy)`; `enum Composition { ROWS, CLASSIC }`; `DialogMapping(Kind { AS_REQUESTED, CLASSIC }, boolean
  windowTitled)`; `enum Motion { STANDARD, NONE }`; `TitleFormat(String template)`; `DockLayoutKey(String key)`;
  `AppPanels(String notificationsTitle, Slot notificationsSlot)`; `Projections(DesktopFlags desktop, WorkspaceLook
  workspace, ShellLook shell, PaletteLook palette, SettingsChrome settings, Composition composition, DialogMapping
  dialogs, Motion motion, TitleFormat title, DockLayoutKey dockLayout, AppPanels panels)` with `Projections.Host(boolean
  mac, boolean unifiedTitleSupported)`, `static Projections of(ChromeSpec, boolean classicBase, boolean namesUiFace,
  String providerId, Host, Set<Presentation.Landed>)` and `static Projections fallback(DesktopFlags committed, Host host)`;
  `Presentation(Optional<Presentation.Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase,
  KitParser.Faces faces, DefaultSource defaults, Projections projections)` with `Provider(String id, String name, String
  version, String digest)`, `enum Landed { WINDOW_CHROME, DOCK_CHROME, RUN_WINDOW, SETTINGS }`, `LANDED`,
  `static Presentation standard(Projections.Host)`, `static Presentation of(Provider, KitParser.Parsed, Projections.Host)`,
  `Presentation fallback(Projections.Host)`, `DesktopFlags committedDesktop()`, `String look()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import dev.jasper.app.dock.Slot;
import dev.jasper.ui.kit.Kit;
import java.util.EnumSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Spec 9.1: each consumer's projection, the default look's by the same rules, the fallback and the landing gate. */
class ProjectionsTest {
    static final Projections.Host MAC = new Projections.Host(true, true), LINUX = new Projections.Host(false, false);
    static final Set<Presentation.Landed> ALL = EnumSet.allOf(Presentation.Landed.class);

    @Test void theClassicLookMapsByTheSpecTableOnceEveryPlanLanded() {
        var classic = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, ALL);
        assertThat(classic.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(classic.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.CLASSIC,
            WorkspaceLook.Stripes.LABELLED, WorkspaceLook.Tabs.CLASSIC, WorkspaceLook.Status.RULED, true, WorkspaceLook.FrameStyle.FLUSH));
        assertThat(classic.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(classic.palette()).isEqualTo(new PaletteLook(PaletteLook.View.RUN_WINDOW, PaletteLook.Home.CORNER));
        assertThat(classic.settings()).isEqualTo(new SettingsChrome(false, true, false,
            Set.of("window.native_decorations", "ui.animations.enabled"), "Use the default font size"));
        assertThat(classic.composition()).isEqualTo(Composition.CLASSIC);
        assertThat(classic.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
        assertThat(classic.motion()).isEqualTo(Motion.NONE);
        assertThat(classic.title()).isEqualTo(new TitleFormat("{context}"));
        assertThat(classic.dockLayout()).isEqualTo(new DockLayoutKey("dev.jasper.enterprise"));
        assertThat(classic.panels()).isEqualTo(new AppPanels("Event Log", Slot.BOTTOM_LEFT));
    }

    @Test void theDefaultLookFollowsTheSameRules() {
        var mac = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, ALL);
        assertThat(mac.desktop()).isEqualTo(DesktopFlags.STANDARD);
        assertThat(mac.workspace()).isEqualTo(new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
            WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD));
        assertThat(mac.shell()).isEqualTo(new ShellLook(true, false, false));
        assertThat(mac.settings()).isEqualTo(new SettingsChrome(true, false, true, Set.of("window.native_decorations"), "Use platform font size"));
        assertThat(mac.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, false));
        assertThat(mac.dockLayout().key()).isEmpty();
        assertThat(mac.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        var linux = Projections.of(ChromeSpec.DEFAULT, false, false, "", LINUX, ALL);
        assertThat(linux.shell().unifiedTitle()).isFalse();
        assertThat(linux.settings().hiddenKeys()).isEmpty();
    }

    @Test void untilItsPlanLandsAChromeValueMapsToTheDefaultProjection() {
        assertThat(Presentation.LANDED).as("plans 6 to 9 add their groups").isEmpty();
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Presentation.LANDED);
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Presentation.LANDED);
        assertThat(shipped.desktop()).as("computed now, applied by plan 6").isEqualTo(new DesktopFlags(false, true));
        assertThat(new Projections(standard.desktop(), shipped.workspace(), shipped.shell(), shipped.palette(), shipped.settings(),
            shipped.composition(), shipped.dialogs(), shipped.motion(), shipped.title(), shipped.dockLayout(), shipped.panels())).isEqualTo(standard);
        var windowOnly = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.WINDOW_CHROME));
        assertThat(windowOnly.workspace().stripes()).as("the dock group has not landed").isEqualTo(WorkspaceLook.Stripes.RAILS);
        assertThat(windowOnly.workspace().menus()).isEqualTo(WorkspaceLook.Menus.CLASSIC);
    }

    @Test void theFallbackIsSpecFourSevensValue() {
        var fallback = Projections.fallback(new DesktopFlags(false, true), MAC);
        assertThat(fallback.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(fallback.workspace().header()).isEqualTo(WorkspaceLook.Header.UNIFIED);
        assertThat(fallback.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(fallback.settings()).isEqualTo(new SettingsChrome(false, false, true, Set.of("window.native_decorations"), "Use platform font size"));
        assertThat(fallback.composition()).isEqualTo(Composition.ROWS);
        assertThat(fallback.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, true));
        assertThat(fallback.motion()).isEqualTo(Motion.STANDARD);
        assertThat(fallback.dockLayout().key()).isEmpty();
        assertThat(fallback.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        assertThat(Projections.fallback(new DesktopFlags(true, true), LINUX).settings().hiddenKeys())
            .as("a native title hides the decorations switch on every platform").containsExactly("window.native_decorations");
        for (var host : new Projections.Host[] {MAC, LINUX})
            assertThat(Projections.fallback(DesktopFlags.STANDARD, host)).as("a unified title was committed: the default look's values")
                .isEqualTo(Projections.of(ChromeSpec.DEFAULT, false, false, "", host, Presentation.LANDED));
    }

    @Test void everyChromeSpecTheParserAcceptsBuildsEveryProjection() {
        int built = 0;
        for (var title : ChromeSpec.TitleBar.values()) for (var menu : ChromeSpec.MenuBar.values())
        for (var tools : ChromeSpec.ToolWindows.values()) for (var tabs : ChromeSpec.EditorTabs.values())
        for (var status : ChromeSpec.StatusBar.values()) for (var palette : ChromeSpec.PaletteKind.values())
        for (var dialogs : ChromeSpec.DialogKind.values()) for (var motion : ChromeSpec.MotionKind.values())
        for (String template : new String[] {"{context}", "Workstation — {session} — {directory}"}) {
            if (menu == ChromeSpec.MenuBar.WINDOW && title != ChromeSpec.TitleBar.NATIVE) continue;
            var chrome = new ChromeSpec(title, menu, template, tools, tabs, status, palette, dialogs, motion);
            for (var host : new Projections.Host[] {MAC, LINUX}) for (var landed : java.util.List.of(Presentation.LANDED, ALL))
                assertThatCode(() -> Projections.of(chrome, true, true, "p", host, landed)).doesNotThrowAnyException();
            built++;
        }
        assertThat(built).isEqualTo(3 * 2 * 2 * 2 * 2 * 2 * 2 * 2);
    }

    @Test void thePresentationOfAKitCarriesItAndFallsBackToTheDefaultKitKeepingItsDefaults() throws Exception {
        var standard = Presentation.standard(MAC);
        assertThat(standard.look()).isEqualTo("default");
        assertThat(standard.kit()).isSameAs(Kit.DEFAULT);
        assertThat(standard.provider()).isEmpty();
        var parsed = KitParserTest.parse(KitParserTest.FULL);
        var look = Presentation.of(new Presentation.Provider("dev.example.look", "Look", "0.1.0", "d1"), parsed, MAC);
        assertThat(look.look()).isEqualTo("dev.example.look");
        assertThat(look.kit()).isSameAs(parsed.kit());
        assertThat(look.defaults()).isSameAs(parsed.defaults());
        assertThat(look.faces()).isEqualTo(parsed.faces());
        assertThat(look.committedDesktop()).as("nothing is applied before plan 6").isEqualTo(DesktopFlags.STANDARD);
        var fallback = look.fallback(MAC);
        assertThat(fallback.kit()).isSameAs(Kit.DEFAULT);
        assertThat(fallback.provider()).isEmpty();
        assertThat(fallback.defaults()).as("the session keeps the defaults read at start").isSameAs(parsed.defaults());
        assertThat(fallback.projections()).isEqualTo(Projections.fallback(DesktopFlags.STANDARD, MAC));
        assertThat(fallback.look()).isEqualTo("default");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.ProjectionsTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `Projections`, `Presentation` and the projection types.

- [ ] **Step 3: Write the projection values**

Create each file in `jasper-app/src/main/java/dev/jasper/app/presentation/`:

`DesktopFlags.java`:

```java
package dev.jasper.app.presentation;

/**
 * What the bootstrap commits before AWT (spec 9.1): the macOS screen menu bar and the operating system's title bar.
 *
 * @param screenMenuBar whether macOS shows menus in the screen menu bar
 * @param nativeTitle whether windows take the operating system's title bar
 */
public record DesktopFlags(boolean screenMenuBar, boolean nativeTitle) {
    /** The default look's flags. */
    public static final DesktopFlags STANDARD = new DesktopFlags(true, false);
}
```

`WorkspaceLook.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The workspace's projection (spec 9.1), read by the workspace's chrome factory.
 *
 * @param menus the menu bar's composition
 * @param header the header's composition
 * @param stripes how tool windows are reached
 * @param tabs the editor tabs
 * @param status the status bar
 * @param runItem whether the status bar leads with "▶ Run…"
 * @param frames how tool-window frames are drawn
 */
public record WorkspaceLook(Menus menus, Header header, Stripes stripes, Tabs tabs, Status status, boolean runItem, FrameStyle frames) {
    /** The standard menus, or the classic ones with a Window menu. */
    public enum Menus { STANDARD, CLASSIC }
    /** The unified header, or the classic toolbar row. */
    public enum Header { UNIFIED, CLASSIC }
    /** Icon rails, or labelled stripes. */
    public enum Stripes { RAILS, LABELLED }
    /** Equal tabs, or classic tabs. */
    public enum Tabs { EQUAL, CLASSIC }
    /** Slash segments, or ruled cells. */
    public enum Status { SLASH, RULED }
    /** Today's frames, or flush frames with header strips. */
    public enum FrameStyle { STANDARD, FLUSH }

    /** Every part is required. */
    public WorkspaceLook {
        Objects.requireNonNull(menus); Objects.requireNonNull(header); Objects.requireNonNull(stripes);
        Objects.requireNonNull(tabs); Objects.requireNonNull(status); Objects.requireNonNull(frames);
    }
}
```

`ShellLook.java`:

```java
package dev.jasper.app.presentation;

/**
 * The window shells' projection (spec 9.1).
 *
 * @param unifiedTitle whether frames draw the unified macOS title bar
 * @param windowMenus whether each window shows its menus in a row of its own
 * @param followThemeAppearance whether a native-titled frame's appearance follows the theme's darkness
 */
public record ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance) { }
```

`PaletteLook.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The palette's projection (spec 9.1).
 *
 * @param view the palette card, or the Run window
 * @param home where the palette opens when it has no saved place
 */
public record PaletteLook(View view, Home home) {
    /** The card or the Run window. */
    public enum View { CARD, RUN_WINDOW }
    /** Under the title field, or in a corner. */
    public enum Home { TITLE_FIELD, CORNER }

    /** Both parts are required. */
    public PaletteLook { Objects.requireNonNull(view); Objects.requireNonNull(home); }
}
```

`SettingsChrome.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;
import java.util.Set;

/**
 * The Settings dialog's projection (spec 9.1 and 12.1).
 *
 * @param integratedTitle whether the dialog draws its title row in the window
 * @param collapsibleNav whether navigation sections collapse
 * @param inlineReset whether rows show their own Reset button
 * @param hiddenKeys settings the look implies, which Settings does not show
 * @param fontRowCopy the label of the interface font size's default switch
 */
public record SettingsChrome(boolean integratedTitle, boolean collapsibleNav, boolean inlineReset, Set<String> hiddenKeys, String fontRowCopy) {
    /** Copies the set. */
    public SettingsChrome { hiddenKeys = Set.copyOf(hiddenKeys); Objects.requireNonNull(fontRowCopy); }
}
```

`Composition.java`:

```java
package dev.jasper.app.presentation;

/** How the app's own pages compose (spec 9.1): today's rows, or the classic compositions. */
public enum Composition { ROWS, CLASSIC }
```

`DialogMapping.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * How plugin and app dialogs are placed (spec 9.1 and 10.8).
 *
 * @param kind as each dialog requests, or overlay and attached-sheet dialogs drawn in their window
 * @param windowTitled whether a native title bar already shows a full form's title
 */
public record DialogMapping(Kind kind, boolean windowTitled) {
    /** As requested, or classic. */
    public enum Kind { AS_REQUESTED, CLASSIC }

    /** The kind is required. */
    public DialogMapping { Objects.requireNonNull(kind); }
}
```

`Motion.java`:

```java
package dev.jasper.app.presentation;

/** Whether the look moves (spec 10.9): as the settings say, or never. */
public enum Motion { STANDARD, NONE }
```

`TitleFormat.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * The frame title template (spec 10.4).
 *
 * @param template a template over {@code {context}}, {@code {session}} and {@code {directory}}
 */
public record TitleFormat(String template) {
    /** The template is required. */
    public TitleFormat { Objects.requireNonNull(template); }
}
```

`DockLayoutKey.java`:

```java
package dev.jasper.app.presentation;

import java.util.Objects;

/**
 * Under which key ui-state saves the dock (spec 10.7): empty for the default look's keys, else the provider's id.
 *
 * @param key the key
 */
public record DockLayoutKey(String key) {
    /** The key is required. */
    public DockLayoutKey { Objects.requireNonNull(key); }
}
```

`AppPanels.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.app.dock.Slot;
import java.util.Objects;

/**
 * The app's own panels under the look (spec 10.7).
 *
 * @param notificationsTitle the Notifications panel's title
 * @param notificationsSlot its default slot
 */
public record AppPanels(String notificationsTitle, Slot notificationsSlot) {
    /** Both parts are required. */
    public AppPanels { Objects.requireNonNull(notificationsTitle); Objects.requireNonNull(notificationsSlot); }
}
```

- [ ] **Step 4: Write `Projections` and `Presentation`**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.app.dock.Slot;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

/**
 * Every consumer's projection of a presentation (spec 9.1), computed once by its table; the default look's values follow
 * from {@link ChromeSpec#DEFAULT} by the same rules. A group of fields takes the kit file's chrome only once the plan
 * that consumes it has landed ({@link Presentation#LANDED}); until then it is the default look's.
 *
 * @param desktop the bootstrap's flags, always computed from the file
 * @param workspace the workspace chrome
 * @param shell the window shells
 * @param palette the palette view and home
 * @param settings the Settings dialog
 * @param composition the app pages' composition
 * @param dialogs the dialog mapping
 * @param motion the motion policy
 * @param title the frame title template
 * @param dockLayout the ui-state key of the dock
 * @param panels the app's own panels
 */
public record Projections(DesktopFlags desktop, WorkspaceLook workspace, ShellLook shell, PaletteLook palette, SettingsChrome settings,
                          Composition composition, DialogMapping dialogs, Motion motion, TitleFormat title, DockLayoutKey dockLayout,
                          AppPanels panels) {
    /**
     * Facts of the host the 9.1 table reads.
     *
     * @param mac whether this is macOS
     * @param unifiedTitleSupported whether the unified macOS title bar is available
     */
    public record Host(boolean mac, boolean unifiedTitleSupported) { }

    /** Every projection is required. */
    public Projections {
        Objects.requireNonNull(desktop); Objects.requireNonNull(workspace); Objects.requireNonNull(shell); Objects.requireNonNull(palette);
        Objects.requireNonNull(settings); Objects.requireNonNull(composition); Objects.requireNonNull(dialogs); Objects.requireNonNull(motion);
        Objects.requireNonNull(title); Objects.requireNonNull(dockLayout); Objects.requireNonNull(panels);
    }

    /**
     * The 9.1 table.
     *
     * @param chrome the kit's chrome
     * @param classicBase whether the kit's base is classic
     * @param namesUiFace whether the kit names a UI face
     * @param providerId the provider's id, empty for the default look
     * @param host the host's facts
     * @param landed the groups whose plans have landed
     * @return the projections; never throws for a chrome the parser accepts
     */
    public static Projections of(ChromeSpec chrome, boolean classicBase, boolean namesUiFace, String providerId, Host host,
                                 Set<Presentation.Landed> landed) {
        ChromeSpec window = landed.contains(Presentation.Landed.WINDOW_CHROME) ? chrome : ChromeSpec.DEFAULT;
        ChromeSpec dock = landed.contains(Presentation.Landed.DOCK_CHROME) ? chrome : ChromeSpec.DEFAULT;
        ChromeSpec run = landed.contains(Presentation.Landed.RUN_WINDOW) ? chrome : ChromeSpec.DEFAULT;
        boolean settingsLanded = landed.contains(Presentation.Landed.SETTINGS);
        ChromeSpec page = settingsLanded ? chrome : ChromeSpec.DEFAULT;
        boolean classic = settingsLanded && classicBase, faces = settingsLanded && namesUiFace;
        boolean nativeTitle = window.titleBar() == ChromeSpec.TitleBar.NATIVE, windowMenus = window.menuBar() == ChromeSpec.MenuBar.WINDOW;
        boolean stripes = dock.toolWindows() == ChromeSpec.ToolWindows.STRIPES, runWindow = run.palette() == ChromeSpec.PaletteKind.RUN_WINDOW;
        var desktop = new DesktopFlags(chrome.menuBar() != ChromeSpec.MenuBar.WINDOW, chrome.titleBar() == ChromeSpec.TitleBar.NATIVE);
        var workspace = new WorkspaceLook(windowMenus ? WorkspaceLook.Menus.CLASSIC : WorkspaceLook.Menus.STANDARD,
            nativeTitle ? WorkspaceLook.Header.CLASSIC : WorkspaceLook.Header.UNIFIED,
            stripes ? WorkspaceLook.Stripes.LABELLED : WorkspaceLook.Stripes.RAILS,
            window.editorTabs() == ChromeSpec.EditorTabs.CLASSIC ? WorkspaceLook.Tabs.CLASSIC : WorkspaceLook.Tabs.EQUAL,
            window.statusBar() == ChromeSpec.StatusBar.CELLS ? WorkspaceLook.Status.RULED : WorkspaceLook.Status.SLASH,
            window.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,
            stripes ? WorkspaceLook.FrameStyle.FLUSH : WorkspaceLook.FrameStyle.STANDARD);
        var shell = new ShellLook(window.titleBar() == ChromeSpec.TitleBar.UNIFIED && host.unifiedTitleSupported(), windowMenus, nativeTitle);
        var palette = new PaletteLook(runWindow ? PaletteLook.View.RUN_WINDOW : PaletteLook.View.CARD,
            runWindow ? PaletteLook.Home.CORNER : PaletteLook.Home.TITLE_FIELD);
        var hidden = new LinkedHashSet<String>();
        if (page.titleBar() == ChromeSpec.TitleBar.NATIVE || host.mac()) hidden.add("window.native_decorations");
        if (page.motion() == ChromeSpec.MotionKind.NONE) hidden.add("ui.animations.enabled");
        var settings = new SettingsChrome(page.titleBar() != ChromeSpec.TitleBar.NATIVE, classic, !classic, hidden,
            faces ? "Use the default font size" : "Use platform font size");
        return new Projections(desktop, workspace, shell, palette, settings, classic ? Composition.CLASSIC : Composition.ROWS,
            new DialogMapping(window.dialogs() == ChromeSpec.DialogKind.CLASSIC ? DialogMapping.Kind.CLASSIC : DialogMapping.Kind.AS_REQUESTED, nativeTitle),
            window.motion() == ChromeSpec.MotionKind.NONE ? Motion.NONE : Motion.STANDARD, new TitleFormat(window.windowTitle()),
            new DockLayoutKey(landed.contains(Presentation.Landed.DOCK_CHROME) ? providerId : ""),
            stripes ? new AppPanels("Event Log", Slot.BOTTOM_LEFT) : new AppPanels("Notifications", Slot.RIGHT_TOP));
    }

    /**
     * Spec 4.7's fallback: the default kit's chrome under the title and menus {@code committed} before AWT. A committed native
     * title keeps the native-title values; a unified one gives the default look's values on {@code host}.
     */
    public static Projections fallback(DesktopFlags committed, Host host) {
        Objects.requireNonNull(committed, "committed"); Objects.requireNonNull(host, "host");
        boolean nativeTitle = committed.nativeTitle();
        Set<String> hidden = nativeTitle || host.mac() ? Set.of("window.native_decorations") : Set.of();
        return new Projections(committed,
            new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL,
                WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD),
            new ShellLook(!nativeTitle && host.unifiedTitleSupported(), !committed.screenMenuBar(), nativeTitle),
            new PaletteLook(PaletteLook.View.CARD, PaletteLook.Home.TITLE_FIELD),
            new SettingsChrome(!nativeTitle, false, true, hidden, "Use platform font size"), Composition.ROWS,
            new DialogMapping(DialogMapping.Kind.AS_REQUESTED, nativeTitle), Motion.STANDARD, new TitleFormat("{context}"), new DockLayoutKey(""),
            new AppPanels("Notifications", Slot.RIGHT_TOP));
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java`:

```java
package dev.jasper.app.presentation;

import dev.jasper.app.config.DefaultSource;
import dev.jasper.ui.kit.Kit;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * The one presentation a process runs with (spec 3.2 and 4.9), chosen on the bootstrap thread and replaced at most once,
 * by {@link #fallback(Projections.Host)}, before plugins start. Consumers receive its kit or a projection, never this value.
 *
 * @param provider the providing plugin, empty for the default look
 * @param kit the jasper-ui kit every context is made with
 * @param chrome the parsed chrome points
 * @param classicBase whether the kit's base is classic
 * @param faces the face files the kit names
 * @param defaults the kit's setting defaults
 * @param projections every consumer's projection
 */
public record Presentation(Optional<Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase, KitParser.Faces faces,
                           DefaultSource defaults, Projections projections) {
    /**
     * The plugin that provides a look.
     *
     * @param id its id
     * @param name its name, as notices say it
     * @param version its version
     * @param digest the look's digest
     */
    public record Provider(String id, String name, String version, String digest) {
        /** Every part is required. */
        public Provider { Objects.requireNonNull(id); Objects.requireNonNull(name); Objects.requireNonNull(version); Objects.requireNonNull(digest); }
    }

    /** The groups of projections whose plans consume them (spec 19.1 rows 6 to 9). */
    public enum Landed { WINDOW_CHROME, DOCK_CHROME, RUN_WINDOW, SETTINGS }

    /** The groups that have landed: none yet, so every chrome value maps to the default projection. */
    public static final Set<Landed> LANDED = Set.of();

    /** Every part is required. */
    public Presentation {
        Objects.requireNonNull(provider); Objects.requireNonNull(kit); Objects.requireNonNull(chrome); Objects.requireNonNull(faces);
        Objects.requireNonNull(defaults); Objects.requireNonNull(projections);
    }

    /** The default look. */
    public static Presentation standard(Projections.Host host) {
        return new Presentation(Optional.empty(), Kit.DEFAULT, ChromeSpec.DEFAULT, false, KitParser.Faces.NONE, DefaultSource.CORE,
            Projections.of(ChromeSpec.DEFAULT, false, false, "", host, LANDED));
    }

    /** {@code provider}'s look from its parsed kit. */
    public static Presentation of(Provider provider, KitParser.Parsed parsed, Projections.Host host) {
        return new Presentation(Optional.of(provider), parsed.kit(), parsed.chrome(), parsed.classicBase(), parsed.faces(), parsed.defaults(),
            Projections.of(parsed.chrome(), parsed.classicBase(), parsed.faces().ui().isPresent(), provider.id(), host, LANDED));
    }

    /** Spec 4.7: the default kit and chrome under the flags already committed on {@code host}, keeping the defaults read at start. */
    public Presentation fallback(Projections.Host host) {
        return new Presentation(Optional.empty(), Kit.DEFAULT, ChromeSpec.DEFAULT, false, KitParser.Faces.NONE, defaults,
            Projections.fallback(committedDesktop(), host));
    }

    /** The desktop flags the bootstrap applied: the look's once window chrome has landed, else the default look's. */
    public DesktopFlags committedDesktop() {
        return LANDED.contains(Landed.WINDOW_CHROME) ? projections.desktop() : DesktopFlags.STANDARD;
    }

    /** The provider's id, or {@code default}: how the start-up trace names the look. */
    public String look() { return provider.map(Provider::id).orElse("default"); }
}
```

Make `Presentation` a `Kit.DEFAULT` reader in `StaticDefaultTest`, and add the ledger rows of the three new files the ledger's pattern matches
(`Presentation.java` and `ProjectionsTest.java` import `dev.jasper.ui`; `ShellLook.java` names `followThemeAppearance`):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java"); text = path.read_text(encoding="utf-8")
old = 'static final Set<String> DEFAULT_KIT_READERS = Set.of('
if text.count(old) != 1: raise SystemExit("StaticDefaultTest: DEFAULT_KIT_READERS moved")
path.write_text(text.replace(old, old + '"dev/jasper/app/presentation/Presentation", '), encoding="utf-8")
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
rows = [
    "jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java,app.presentation,Presentation,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java,New in Enterprise plan 5a Task 8; the one presentation a process runs with; no Swing",
    "jasper-app/src/main/java/dev/jasper/app/presentation/ShellLook.java,app.presentation,ShellLook,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java,New in Enterprise plan 5a Task 8; the window shells' projection; no Swing",
    "jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java,app.presentation,ProjectionsTest,nonvisual-helper,none,none,"
    "jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 8; the 9.1 table and its gate"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.architecture.StaticDefaultTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS (`ProjectionsTest` 6 cases; `presentation` now also depends on `dev.jasper.app.dock`, and there is no cycle).
If `ThemeMigrationCoverageTest` names another new file of this task, add its row in the same style.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java \
  jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Presentation provider: the projections and the Presentation value

Presentation is the one value a process runs with: the provider, the kit,
the parsed chrome, the base, the face files, the kit defaults and every
spec 9.1 projection, computed once by its table; the default look follows
the same rules. A group of fields takes the file's chrome only once the
plan that consumes it lands (Presentation.LANDED, empty for now), so every
chrome value maps to the default projection; DesktopFlags is computed for
plan 6. Presentation.fallback(Host) is spec 4.7's value.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The bootstrap pre-pass and the one selection

`PluginSelection` (spec 4.8) discovers plugins once, reads every declared presentation and the attempt record,
resolves, and builds the winner's `Presentation`, on the bootstrap thread and with no AWT. `ApplicationBootstrap.start`
runs it (with the log and plugin maintenance, which move there from `prepareDesktop`) after the hand-off checks and
before `new ConfigService(file, mac, defaults)`. The application takes the selection at construction, starts the theme
under its kit, and `PluginRuntime.start(selection, …)` consumes it without a second scan. The trace reports
`look=<id>`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java:615-641,692`, `PluginResolver.java` (`without`),
  `PluginStatus.java` (`LOOK_FAILED`), `plugins/package-info.java:3-4`
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:41-84,104-125,162-204,297-331`, `bootstrap/package-info.java:3`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:225-253,833-906`, `application/package-info.java:3`
- Modify: `jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java:301-303` and `document`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java` (JasperApplication no longer reads `Kit.DEFAULT`), `docs/design/theme-engine/migration.csv` (two rows)
- Test: `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java`, `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java`

**Interfaces:**
- Consumes: `Presentations.read`, the five-argument `resolve` (Task 5), `AttemptStore`, `PresentationAttempt` (Task 4),
  `Presentation`, `Projections.Host` (Task 8), `ConfigService(Path, boolean, DefaultSource)` (Task 7).
- Produces: `public final class PluginSelection` with `static PluginSelection of(AppDirs dirs, Path codeLocation, String
  build, Path developmentDirectory, boolean safeMode)`, `static PluginSelection none()`, `Presentation presentation()`,
  `PresentationAttempt attempt()`, `Optional<PluginSelection.SkippedLook> skipped()` (`SkippedLook(String name,
  PresentationAttempt attempt)`), `String build()`, `PluginRuntime.Options options()`, `boolean none()` is
  `isNone()`, `PluginSelection blamed()`; package-private `scan(Options, String build, Version sdk, Projections.Host)`,
  `candidates()`, `problems()`, `resolution()`, `static Projections.Host host()`;
  `PluginRuntime.start(PluginSelection, Map<String, Map<String, Object>>, boolean)` (the old two-argument `start` scans
  for tests); `PluginResolver.without(Resolution, PluginCandidate, String reason)`; `PluginStatus.LOOK_FAILED`;
  `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path, Runnable, Path, Path, PluginSelection)`,
  `JasperApplication.presentation()`, `JasperApplication.startPlugins(AppDirs)`;
  `ApplicationBootstrap.Preparation` (`Prepared prepare(AppDirs, AppArguments)`, `NONE`), `ApplicationBootstrap.Prepared(AppLog log,
  PluginSelection bootstrapSelection)`, `ApplicationBootstrap.start(String[], PrintStream, PrintStream, Preparation, Consumer<Startup>)`,
  `Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log, PluginSelection selection)`;
  `StartupTrace.look(String)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.config.CoreSettings;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.8: one pre-pass before Swing; its selection feeds the configuration and the runtime, with no second scan. */
class PluginSelectionTest {
    static final String KIT = "kit = 1\nbase = \"default\"\n[defaults]\n\"palette.visible_rows\" = 7\n";
    @TempDir Path root;

    AppDirs dirs() { return new AppDirs(root.resolve("home"), root.resolve("home/config.toml"), root.resolve("home/logs")); }

    Path provider() throws Exception {
        return LookFixtures.plugin(root.resolve("dev"), "dev.example.look",
            LookFixtures.descriptor("dev.example.look", "Example") + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", KIT));
    }

    @Test void theWinnersPresentationCarriesItsKitDefaultsAndAttempt() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "/app/jasper-app.jar@1", provider(), false);
        assertThat(selection.presentation().look()).isEqualTo("dev.example.look");
        assertThat(selection.presentation().provider().orElseThrow().name()).isEqualTo("Example");
        assertThat(selection.presentation().defaults().value(CoreSettings.PALETTE_VISIBLE_ROWS)).isEqualTo(7);
        assertThat(selection.attempt().records()).isTrue();
        assertThat(selection.attempt().id()).isEqualTo("dev.example.look");
        assertThat(selection.build()).isEqualTo("/app/jasper-app.jar@1");
        assertThat(selection.candidates()).extracting(PluginCandidate::origin).containsExactly(PluginCandidate.Origin.DEV);
        assertThat(selection.skipped()).isEmpty();
    }

    @Test void safeModeAndNoProviderSelectTheStandardLook() throws Exception {
        var safe = PluginSelection.of(dirs(), null, "", provider(), true);
        assertThat(safe.presentation().look()).isEqualTo("default");
        assertThat(safe.attempt().records()).isFalse();
        assertThat(safe.resolution().rejected()).as("without reading its files, the provider is disabled for safe mode")
            .singleElement().satisfies(status -> {
                assertThat(status.state()).isEqualTo(PluginStatus.State.DISABLED);
                assertThat(status.reason()).isEqualTo(PluginStatus.SAFE_MODE_LOOK);
            });
        assertThat(PluginSelection.none().presentation().look()).isEqualTo("default");
        assertThat(PluginSelection.none().isNone()).isTrue();
        assertThat(PluginSelection.of(dirs(), null, "", null, false).presentation().provider()).isEmpty();
    }

    @Test void aSkippedLookIsNamedAndBlamingTheKitFallsBackKeepingTheDefaults() throws Exception {
        Path devPlugins = provider();
        var first = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        first.attempt().pending();
        var again = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        assertThat(again.presentation().look()).isEqualTo("default");
        assertThat(again.skipped()).hasValueSatisfying(skipped -> assertThat(skipped.name()).isEqualTo("Example"));
        again.skipped().orElseThrow().attempt().clear();
        var cleared = PluginSelection.of(dirs(), null, "B", devPlugins, false);
        assertThat(cleared.presentation().look()).as("Try Again").isEqualTo("dev.example.look");
        var blamed = cleared.blamed();
        assertThat(blamed.presentation().look()).isEqualTo("default");
        assertThat(blamed.presentation().kit()).isSameAs(dev.jasper.ui.kit.Kit.DEFAULT);
        assertThat(blamed.presentation().defaults()).isSameAs(cleared.presentation().defaults());
        assertThat(blamed.resolution().load()).extracting(PluginCandidate::id).doesNotContain("dev.example.look");
        assertThat(blamed.resolution().rejected()).anySatisfy(status -> assertThat(status.reason()).isEqualTo(PluginStatus.LOOK_FAILED));
        assertThat(blamed.attempt()).isSameAs(cleared.attempt());
    }

    @Test void theRuntimeStartsWhatTheSelectionChoseWithoutAnotherScan() throws Exception {
        Path dev = provider();
        var selection = PluginSelection.of(dirs(), null, "", dev, false);
        PluginMaintenance.deleteRecursively(dev);
        var runtime = new AtomicReference<PluginRuntime>();
        SwingUtilities.invokeAndWait(() -> {
            runtime.set(new PluginRuntime(selection.options(), new dev.jasper.app.notifications.ActivityNotifier(
                new dev.jasper.buddy.view.BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().start(selection, Map.of(), true);
        });
        assertThat(runtime.get().statusLines()).as("the deleted plugin is still the selection's").singleElement()
            .satisfies(line -> assertThat(line).contains("dev.example.look", "[DEV]"));
        var pending = new AtomicReference<List<java.util.concurrent.CompletableFuture<?>>>();
        SwingUtilities.invokeAndWait(() -> pending.set(runtime.get().stop()));
        java.util.concurrent.CompletableFuture.allOf(pending.get().toArray(java.util.concurrent.CompletableFuture[]::new)).get();
    }
}
```

The runtime is built as `BundledSamplePluginTest` builds one (`dev.jasper.app.notifications.ActivityNotifier`,
`dev.jasper.buddy.view.BuddyTestSupport`, `AppContractTest.headlessWindows()`).

Append to `ApplicationBootstrapTest` (before its final closing brace):

```java
    /** Spec 4.8: the pre-pass runs before the configuration is read, and its selection reaches the launch. */
    @Test void thePrePassFeedsTheConfigurationsDefaultsAndTheLaunch() throws Exception {
        Path config = directory.resolve("config.toml");
        Files.writeString(config, "");
        var selection = dev.jasper.app.plugins.PluginSelection.of(new AppDirs(directory, config, directory.resolve("logs")), null, "",
            dev.jasper.app.testsupport.LookFixtures.plugin(directory.resolve("dev"), "dev.example.look",
                dev.jasper.app.testsupport.LookFixtures.descriptor("dev.example.look", "Example")
                    + dev.jasper.app.testsupport.LookFixtures.provider("look.kit.toml"),
                java.util.Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[defaults]\n\"palette.visible_rows\" = 7\n")), false);
        var received = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err,
                (dirs, options) -> new ApplicationBootstrap.Prepared(null, selection), received::set)).isZero();
            assertThat(received.get().selection()).isSameAs(selection);
            assertThat(received.get().service().initialState().snapshot().visibleRows()).isEqualTo(7);
            assertThat(received.get().service().defaults()).isSameAs(selection.presentation().defaults());
        } finally { if (received.get() != null) received.get().service().close(); }
        var plain = new AtomicReference<ApplicationBootstrap.Startup>();
        try {
            assertThat(ApplicationBootstrap.start(new String[]{"--config", config.toString()}, System.out, System.err, plain::set)).isZero();
            assertThat(plain.get().selection().isNone()).as("the test entry scans no home").isTrue();
            assertThat(plain.get().log()).isNull();
        } finally { if (plain.get() != null) plain.get().service().close(); }
    }
```

In `StartupTraceTest`, every expected start-up line gains `, look=default` before `):` (the four lines quoted in fact
16), and one case names a look:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java"); text = path.read_text(encoding="utf-8")
count = 0
for old in ["CDS on): ", "CDS off): "]:
    count += text.count(old); text = text.replace(old, old.replace("): ", ", look=default): "))
if count != 4: raise SystemExit(f"StartupTraceTest: expected 4 start-up lines, found {count}")
path.write_text(text, encoding="utf-8")
PY
```

and append to `StartupTraceTest` (before its final closing brace):

```java
    @Test void theLineNamesTheLook() {
        var fixture = new Fixture();
        StartupTrace trace = fixture.trace();
        trace.look("dev.jasper.enterprise");
        fixture.at(20);
        trace.mark(StartupTrace.Milestone.MAIN_ENTERED);
        trace.settle();
        assertThat(fixture.log).containsExactly("Startup (cold, AOT cache used, CDS on, look=dev.jasper.enterprise): main entered 120 ms");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PluginSelectionTest' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest' --tests 'dev.jasper.app.startup.StartupTraceTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `PluginSelection`, `ApplicationBootstrap.Prepared`,
`Startup.selection()` and `StartupTrace.look`.

- [ ] **Step 3: Write `PluginSelection`**

Create `jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java`:

```java
package dev.jasper.app.plugins;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.PresentationAttempt;
import dev.jasper.app.presentation.Projections;
import dev.jasper.sdk.JasperSdk;
import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The bootstrap pre-pass (spec 4.8): plugins discovered once, every declared presentation and the attempt record read,
 * resolved, and the winner's presentation built, before Swing and before the configuration is read. This one selection
 * feeds the configuration's kit defaults, the application and {@link PluginRuntime#start(PluginSelection, Map, boolean)},
 * so a {@code plugins.toml} edited during start-up cannot load a plugin set that disagrees with the chrome already
 * committed. Immutable; touches no AWT; any thread.
 */
public final class PluginSelection {
    private static final System.Logger LOG = System.getLogger(PluginSelection.class.getName());

    /**
     * A look the attempt record skipped at this start.
     *
     * @param name its provider's name
     * @param attempt the handle whose {@link PresentationAttempt#clear()} is Try Again
     */
    public record SkippedLook(String name, PresentationAttempt attempt) {
        /** Both parts are required. */
        public SkippedLook { Objects.requireNonNull(name); Objects.requireNonNull(attempt); }
    }

    private final PluginRuntime.Options options;
    private final String build;
    private final List<PluginCandidate> candidates;
    private final List<String> problems;
    private final PluginResolver.Resolution resolution;
    private final Presentation presentation;
    private final PresentationAttempt attempt;
    private final Optional<SkippedLook> skipped;

    private PluginSelection(PluginRuntime.Options options, String build, List<PluginCandidate> candidates, List<String> problems,
                            PluginResolver.Resolution resolution, Presentation presentation, PresentationAttempt attempt,
                            Optional<SkippedLook> skipped) {
        this.options = options; this.build = build; this.candidates = List.copyOf(candidates); this.problems = List.copyOf(problems);
        this.resolution = resolution; this.presentation = presentation; this.attempt = attempt; this.skipped = skipped;
    }

    /**
     * The selection for this launch.
     *
     * @param dirs the home's folders
     * @param codeLocation the application's code location, which locates the bundled plugins; may be null
     * @param build the code location and its modification time, the identity the attempt record compares; may be empty
     * @param developmentDirectory the {@code --plugin-dir} folder, or null
     * @param safeMode whether this is a {@code --safe-mode} launch
     * @return the selection
     */
    public static PluginSelection of(AppDirs dirs, Path codeLocation, String build, Path developmentDirectory, boolean safeMode) {
        var options = new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeLocation), dirs.plugins(), developmentDirectory, safeMode,
            dirs.pluginState(), dirs.pluginLock());
        return scan(options, build, Version.parse(JasperSdk.VERSION), host());
    }

    /** No plugins and the default look: for an application made without the bootstrap. */
    public static PluginSelection none() {
        return new PluginSelection(null, "", List.of(), List.of(), new PluginResolver.Resolution(List.of(), List.of()),
            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty());
    }

    static PluginSelection scan(PluginRuntime.Options options, String build, Version sdk, Projections.Host host) {
        Objects.requireNonNull(options, "options"); Objects.requireNonNull(build, "build");
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);
        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), Duration.ofSeconds(2)).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
        var store = new AttemptStore(options.stateFile().toAbsolutePath().getParent());
        // Safe mode disables every presentation provider (spec 4.5), so it reads none of their files (Rulings 13).
        var attempts = store.read();
        var presentations = options.safeMode() ? new Presentations(Map.of(), attempts, build) : Presentations.read(candidates, attempts, build);
        var resolution = PluginResolver.resolve(candidates, state, sdk, options.safeMode(), presentations);
        Presentation presentation = Presentation.standard(host);
        PresentationAttempt attempt = PresentationAttempt.NONE;
        if (resolution.presentation().isPresent()) {
            PluginCandidate winner = resolution.presentation().get();
            var facts = presentations.of(winner).orElseThrow();
            var descriptor = winner.descriptor();
            presentation = Presentation.of(new Presentation.Provider(winner.id(), descriptor.name(), descriptor.version().toString(), facts.digest()),
                facts.parsed().orElseThrow(), host);
            attempt = new PresentationAttempt(store, winner.id(), descriptor.version().toString(), facts.digest(), build);
        }
        Optional<SkippedLook> skipped = Optional.empty();
        for (PluginStatus status : resolution.rejected()) {
            if (status.state() != PluginStatus.State.SKIPPED || !status.reason().equals(PluginStatus.ATTEMPT_SKIP)) continue;
            for (PluginCandidate candidate : candidates)
                if (candidate.id().equals(status.id()) && candidate.origin() == status.origin())
                    skipped = Optional.of(new SkippedLook(candidate.descriptor().name(), new PresentationAttempt(store, candidate.id(),
                        candidate.descriptor().version().toString(), presentations.of(candidate).orElseThrow().digest(), build)));
        }
        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped);
    }

    /** macOS, and whether its unified title bar is available, read without AWT. */
    static Projections.Host host() {
        boolean mac = System.getProperty("os.name", "").startsWith("Mac");
        return new Projections.Host(mac, mac && com.formdev.flatlaf.util.SystemInfo.isMacFullWindowContentSupported);
    }

    /** The presentation this launch runs. */
    public Presentation presentation() { return presentation; }

    /** The attempt record of that presentation's look; {@link PresentationAttempt#NONE} for the default look. */
    public PresentationAttempt attempt() { return attempt; }

    /** The look the attempt record skipped at this start, if one was. */
    public Optional<SkippedLook> skipped() { return skipped; }

    /** The build identity this selection compared the attempt record with. */
    public String build() { return build; }

    /**
     * Where plugins and their state live.
     *
     * @throws IllegalStateException for {@link #none()}
     */
    public PluginRuntime.Options options() {
        if (options == null) throw new IllegalStateException("No plugins were selected");
        return options;
    }

    /** Whether this is {@link #none()}. */
    public boolean isNone() { return options == null; }

    /**
     * Spec 4.7: this selection after the kit was blamed at start: the provider is skipped, so no service of it is
     * published, and the presentation is the fallback, which keeps the defaults read at start. The attempt is the same.
     */
    public PluginSelection blamed() {
        var provider = resolution.presentation();
        if (provider.isEmpty()) return this;
        return new PluginSelection(options, build, candidates, problems,
            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped);
    }

    List<PluginCandidate> candidates() { return candidates; }
    List<String> problems() { return problems; }
    PluginResolver.Resolution resolution() { return resolution; }
}
```

- [ ] **Step 4: Consume the selection in the runtime and add the resolver's `without`**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 9 step 4: the runtime starts the selection."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginStatus.java", [
    ('''    static final String ATTEMPT_SKIP = "did not finish starting with this look last time";''',
     '''    static final String ATTEMPT_SKIP = "did not finish starting with this look last time";
    /** Why a provider whose kit was blamed at start is skipped (spec 4.7). */
    static final String LOOK_FAILED = "its look could not be installed; Jasper uses its standard look";''', 1)])
edit(P + "PluginResolver.java", [
    ('''    private static void dropUnmet(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected) {''',
     '''    /** {@code resolution} without {@code dropped}, skipped for {@code reason}; its hard dependents are skipped as unmet. */
    static Resolution without(Resolution resolution, PluginCandidate dropped, String reason) {
        Map<String, PluginCandidate> chosen = new TreeMap<>();
        for (PluginCandidate candidate : resolution.load()) if (!candidate.id().equals(dropped.id())) chosen.put(candidate.id(), candidate);
        List<PluginStatus> rejected = new ArrayList<>(resolution.rejected());
        rejected.add(PluginStatus.of(dropped, PluginStatus.State.SKIPPED, reason));
        dropUnmet(chosen, rejected);
        return new Resolution(order(chosen), rejected, java.util.Optional.empty());
    }

    private static void dropUnmet(Map<String, PluginCandidate> chosen, List<PluginStatus> rejected) {''', 1)])
edit(P + "PluginRuntime.java", [
    ('''    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        this.dark = dark;
        long began = System.nanoTime();
        List<String> problems = new ArrayList<>();
        List<PluginCandidate> candidates = new ArrayList<>(
            PluginDiscovery.scan(options.bundledDirectory(), PluginCandidate.Origin.BUNDLED, problems));
        candidates.addAll(PluginDiscovery.scan(options.userDirectory(), PluginCandidate.Origin.USER, problems));
        if (options.developmentDirectory() != null)
            PluginDiscovery.single(options.developmentDirectory(), PluginCandidate.Origin.DEV, problems).ifPresent(candidates::add);''',
     '''    public void start(Map<String, Map<String, Object>> pluginTables, boolean dark) {
        start(PluginSelection.scan(options, "", Version.parse(JasperSdk.VERSION), PluginSelection.host()), pluginTables, dark);
    }

    /**
     * Loads and starts, in dependency order, the plugins {@code selection} chose; nothing is discovered again. Call once,
     * before the first window.
     *
     * @param selection the bootstrap's selection, made with this runtime's options
     * @param pluginTables the {@code [plugins."<id>"]} tables of the configuration, used only to seed a missing settings file
     * @param dark whether the current look is dark
     */
    public void start(PluginSelection selection, Map<String, Map<String, Object>> pluginTables, boolean dark) {
        if (host != null) throw new IllegalStateException("Plugins already started");
        this.selection = Objects.requireNonNull(selection, "selection");
        this.dark = dark;
        long began = System.nanoTime();
        List<String> problems = new ArrayList<>(selection.problems());
        List<PluginCandidate> candidates = new ArrayList<>(selection.candidates());''', 1),
    ('''        Map<String, PluginStateStore.Entry> state;
        try { state = new PluginStateStore(options.stateFile(), options.lockFile(), LOCK_WAIT).read(); }
        catch (IOException unreadable) {
            // Without readable consent, user plugins stay inert rather than running unreviewed.
            LOG.log(System.Logger.Level.WARNING, "Plugin state is unreadable; user plugins will need consent", unreadable);
            state = Map.of();
        }
''', '', 1),
    ('''        var presentations = Presentations.read(candidates,
            new dev.jasper.app.presentation.AttemptStore(options.stateFile().toAbsolutePath().getParent()).read(), "");
        var resolution = PluginResolver.resolve(candidates, state, Version.parse(JasperSdk.VERSION), options.safeMode(), presentations);''',
     '''        var resolution = selection.resolution();''', 1),
    ('''        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode());''',
     '''        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode(), selection.build());''', 1),
    ('''    private volatile PluginHost host;''', '''    private volatile PluginHost host;
    private PluginSelection selection = PluginSelection.none();''', 1)])
edit(P + "package-info.java", [
    ('''{@link dev.jasper.app.plugins.PluginRuntime}
 * is the only public type and is owned and closed by the application.''',
     '''{@link dev.jasper.app.plugins.PluginRuntime},
 * {@link dev.jasper.app.plugins.GlassSource} and {@link dev.jasper.app.plugins.PluginSelection} (the bootstrap pre-pass) are
 * the public types; the application owns and closes the runtime.''', 1),
    ('''dev.jasper.app.platform, dev.jasper.app.config,''', '''dev.jasper.app.platform, dev.jasper.app.presentation, dev.jasper.app.config,''', 1)])
PY
```

The runtime's `selection` field is read by Task 12 (the attempt record of a provider that fails to start).

- [ ] **Step 5: Run the pre-pass in the bootstrap and take the selection in the application**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 9 step 5: the bootstrap pre-pass and the application's selection."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
B = "jasper-app/src/main/java/dev/jasper/app/bootstrap/"
edit(B + "ApplicationBootstrap.java", [
    ('''        int result = start(args, System.out, System.err, ApplicationBootstrap::prepareDesktop);''',
     '''        int result = start(args, System.out, System.err, ApplicationBootstrap::prepare, ApplicationBootstrap::prepareDesktop);''', 1),
    ('''    record Startup(ConfigService service, AppArguments options) { }''',
     '''    /** What the boundary hands the desktop: the service, the arguments, the home, the log (null without the pre-pass) and the selection. */
    record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log,
                   dev.jasper.app.plugins.PluginSelection selection) { }

    /** What the pre-pass made: the log, open from here on, and the plugin selection. */
    record Prepared(AppLog log, dev.jasper.app.plugins.PluginSelection selection) { }

    /** The pre-pass {@link #start} runs after the hand-off checks and before the configuration is read (spec 4.8). */
    @FunctionalInterface interface Preparation {
        /** No log, no maintenance and no plugins: what tests of the boundary run. */
        Preparation NONE = (dirs, options) -> new Prepared(null, dev.jasper.app.plugins.PluginSelection.none());
        Prepared prepare(AppDirs dirs, AppArguments options);
    }

    /**
     * The production pre-pass: the Jasper log, pending plugin removals and installs, then the plugin selection, all on
     * the bootstrap thread and before AWT, so maintenance and selection log to the log file.
     */
    static Prepared prepare(AppDirs dirs, AppArguments options) {
        AppLog log = AppLog.open(dirs.logs());
        try {
            long began = System.nanoTime();
            JasperApplication.preparePlugins(dirs);
            Path source = HandoffSocket.codeLocation();
            String build = source == null ? "" : source + "@" + HandoffSocket.lastModified(source);
            var selection = dev.jasper.app.plugins.PluginSelection.of(dirs, source, build, options.pluginDir(), options.safeMode());
            StartupTrace.current().phase("plugin selection", began);
            return new Prepared(log, selection);
        } catch (RuntimeException | Error failure) { log.close(); throw failure; }
    }''', 1),
    ('''            AppDirs dirs = AppDirs.resolve(System.getProperty("os.name"), System.getenv(),
                Path.of(System.getProperty("user.home")), System.getProperty("jasper.home"));
            AppLog log = acquired.own(AppLog.open(dirs.logs()));''',
     '''            AppDirs dirs = startup.dirs();
            AppLog log = acquired.own(Objects.requireNonNull(startup.log(), "the desktop needs the pre-pass's log"));''', 1),
    ('''            dev.jasper.ui.icons.IconRegistry.prepareBuiltIns();
            JasperApplication.preparePlugins(dirs);''',
     '''            dev.jasper.ui.icons.IconRegistry.prepareBuiltIns();''', 1),
    ('''                    JasperApplication application = createApplication(service, history, dirs, options, commands);''',
     '''                    JasperApplication application = createApplication(service, history, dirs, options, commands, startup.selection());''', 1),
    ('''    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs,
                                                       AppArguments options, AtomicReference<CommandListener> commands) {''',
     '''    private static JasperApplication createApplication(ConfigService service, CommandHistory history, AppDirs dirs,
                                                       AppArguments options, AtomicReference<CommandListener> commands,
                                                       dev.jasper.app.plugins.PluginSelection selection) {''', 1),
    ('''            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), integrationDir, dirs.themes());''',
     '''            var application = new JasperApplication(service, null, history, dirs.buddyState(),
                () -> System.exit(0), integrationDir, dirs.themes(), selection);''', 1),
    ('''            application.startPlugins(HandoffSocket.codeLocation(), options.pluginDir(), options.safeMode(), dirs);''',
     '''            application.startPlugins(dirs);''', 1),
    ('''    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, Consumer<Startup> launch) {
        AppArguments options;''',
     '''    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, Consumer<Startup> launch) {
        return start(args, out, error, Preparation.NONE, launch);
    }
    static int start(String[] args, java.io.PrintStream out, java.io.PrintStream error, Preparation prepare, Consumer<Startup> launch) {
        AppArguments options;''', 1),
    ('''        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        long read = System.nanoTime();
        ConfigService service = acquired.own(new ConfigService(file, os.startsWith("Mac")));''',
     '''        Path file = options.configOverride() == null ? dirs.configFile() : options.configOverride();
        var acquired = new StartupResources();
        Prepared prepared = prepare.prepare(dirs, options);
        if (prepared.log() != null) acquired.own(prepared.log());
        StartupTrace.current().look(prepared.selection().presentation().look());
        long read = System.nanoTime();
        ConfigService service;
        try { service = acquired.own(new ConfigService(file, os.startsWith("Mac"), prepared.selection().presentation().defaults())); }
        catch (RuntimeException | Error failure) { acquired.rollback(failure); throw failure; }''', 1),
    ('''            launch.accept(new Startup(service, options));''',
     '''            launch.accept(new Startup(service, options, dirs, prepared.log(), prepared.selection()));''', 1)])
edit(B + "package-info.java", [
    ('''dev.jasper.app.launch, dev.jasper.app.platform, dev.jasper.app.residency,''',
     '''dev.jasper.app.launch, dev.jasper.app.platform, dev.jasper.app.plugins, dev.jasper.app.presentation, dev.jasper.app.residency,''', 1)])
A = "jasper-app/src/main/java/dev/jasper/app/application/"
edit(A + "JasperApplication.java", [
    ('''    /** As above; {@code themesFolder} holds the imported themes, or is null for the built-in theme alone. */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder) {
        dev.jasper.app.platform.BundledFonts.register();''',
     '''    /** As above; {@code themesFolder} holds the imported themes, or is null for the built-in theme alone. */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, themesFolder,
            dev.jasper.app.plugins.PluginSelection.none());
    }

    /**
     * As above, under {@code bootstrapSelection}: the bootstrap's plugin selection, whose presentation every surface of
     * this process receives (spec 4.9) and whose plugins {@link #startPlugins(AppDirs)} starts.
     */
    public JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection) {
        // Not named selection: the field changes during construction when the kit is blamed (spec 4.7), the parameter never.
        this.selection = Objects.requireNonNull(bootstrapSelection, "bootstrapSelection");
        presentation = this.selection.presentation();
        dev.jasper.app.platform.BundledFonts.register();''', 1),
    ('''        themes = ThemeController.startup(dark, selection, dev.jasper.ui.kit.Kit.DEFAULT,''',
     '''        themes = ThemeController.startup(dark, picks, presentation.kit(),''', 1),
    ('''        var selection = dev.jasper.app.appearance.ThemeSelection.of(library, startup.darkTheme(), startup.lightTheme(),''',
     '''        var picks = dev.jasper.app.appearance.ThemeSelection.of(library, startup.darkTheme(), startup.lightTheme(),''', 1),
    ('''    TerminalRegistry terminals() { return terminals; }
''', '''    TerminalRegistry terminals() { return terminals; }

    /** The plugin selection this process starts, fixed at construction. */
    private dev.jasper.app.plugins.PluginSelection selection;
    /** The presentation every surface of this process receives (spec 4.9). */
    private dev.jasper.app.presentation.Presentation presentation;

    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }
''', 1),
    ('''    /**
     * Starts plugins once, before the first window, so their contributions are in place when windows
     * appear. {@code codeLocation} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeLocation, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;''',
     '''    /**
     * Scans for plugins here and starts them: for an application made without the bootstrap's selection. A scan that
     * chooses a presentation is refused, because the look must be chosen before the application is made.
     * {@code codeLocation} locates bundled plugins beside the application jar and may be null;
     * {@code developmentDirectory} is the {@code --plugin-dir} value or null.
     */
    public void startPlugins(Path codeLocation, Path developmentDirectory, boolean safeMode, AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        if (!selection.isNone()) throw new IllegalStateException("This application starts the bootstrap's plugin selection");
        var scanned = dev.jasper.app.plugins.PluginSelection.of(dirs, codeLocation, "", developmentDirectory, safeMode);
        if (scanned.presentation().provider().isPresent())
            throw new IllegalStateException("A presentation provider needs the bootstrap's selection");
        selection = scanned;
        startPlugins(dirs);
    }

    /** Starts the selection's plugins once, before the first window, so their contributions are in place when windows appear. */
    public void startPlugins(AppDirs dirs) {
        if (plugins != null || quitting || stopped) return;
        if (selection.isNone()) selection = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "", null, false);''', 1),
    ('''        plugins = new PluginRuntime(new PluginRuntime.Options(PluginRuntime.bundledDirectory(codeLocation), dirs.plugins(),
            developmentDirectory, safeMode, dirs.pluginState(), dirs.pluginLock()), activityNotifier,''',
     '''        plugins = new PluginRuntime(selection.options(), activityNotifier,''', 1),
    ('''        plugins.start(configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome().dark());''',
     '''        plugins.start(selection, configuration == null ? Map.of() : configuration.snapshot().plugins(), themes.current().chrome().dark());''', 1)])
edit(A + "package-info.java", [
    ('''dev.jasper.app.platform, dev.jasper.app.pluginmanager, dev.jasper.app.plugins,''',
     '''dev.jasper.app.platform, dev.jasper.app.pluginmanager, dev.jasper.app.plugins, dev.jasper.app.presentation,''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java", [
    ('''        StringBuilder out = new StringBuilder("Startup (").append(kind()).append(", AOT cache ").append(status.aotCache())
            .append(", CDS ").append(status.cds()).append("):");''',
     '''        StringBuilder out = new StringBuilder("Startup (").append(kind()).append(", AOT cache ").append(status.aotCache())
            .append(", CDS ").append(status.cds()).append(", look=").append(look).append("):");''', 1),
    ('''        root.put("kind", kind());''', '''        root.put("kind", kind());
        root.put("look", look);''', 1),
    ('''    /** Records {@code milestone} now unless it was already recorded; settles once both settling milestones exist. */''',
     '''    private volatile String look = "default";

    /** The look this process runs: its provider's id, or {@code default} (spec 4.10). */
    public void look(String id) { look = Objects.requireNonNull(id, "id"); }

    /** Records {@code milestone} now unless it was already recorded; settles once both settling milestones exist. */''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java", [
    ('''        "dev/jasper/app/appearance/ThemeController", "dev/jasper/app/application/JasperApplication");''',
     '''        "dev/jasper/app/appearance/ThemeController");''', 1)])
PY
```

Check that nothing else in `JasperApplication` reads the removed `codeLocation`, `developmentDirectory` or `safeMode`
parameters of the old body (`grep -n "codeLocation\|developmentDirectory" jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java`
must show only the new convenience method's lines), and that `ApplicationBootstrap` imports `java.util.Objects`
(add the import if the compiler reports it). The StaticDefaultTest edit anchors on the set as Tasks 3 and 8 leave it
(plan 4a added no reader to it; its `Kit.CLASSIC` pin is the separate `CLASSIC_KIT` check Task 3 lifted): remove only
JasperApplication.

- [ ] **Step 6: Run the tests to verify they pass**

Add the two ledger rows at the end of `docs/design/theme-engine/migration.csv` first (each one line; `PluginSelection` uses `new ArrayList`
and `PluginSelectionTest` imports `javax.swing`):

```
jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java,app.plugins,PluginSelection,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java,New in Enterprise plan 5a Task 9; the bootstrap pre-pass that discovers plugins once and builds the one selection; no Swing
jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java,app.plugins,PluginSelectionTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 9; the pre-pass's selection; attempt skips and the blamed fallback
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.startup.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS: `PluginSelectionTest` (4), the new `ApplicationBootstrapTest` case, `StartupTraceTest` with the look,
every `JasperApplication*Test` (which call `startPlugins(null, dev, false, dirs)`), `StaticDefaultTest`, and the
architecture check with the new `bootstrap → plugins`, `bootstrap → presentation`, `plugins → presentation` and
`application → presentation` edges and no cycle; `ThemeMigrationCoverageTest` names no missing row.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/bootstrap \
  jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/startup/StartupTrace.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginSelectionTest.java jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java \
  jasper-app/src/test/java/dev/jasper/app/startup/StartupTraceTest.java jasper-app/src/test/java/dev/jasper/app/architecture/StaticDefaultTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Presentation provider: the bootstrap pre-pass and the one selection

PluginSelection discovers plugins once, reads every declared presentation
and the attempt record, resolves and builds the winner's Presentation on
the bootstrap thread, with no AWT. ApplicationBootstrap.start runs it, with
the log and plugin maintenance, after the hand-off checks and before the
ConfigService, which takes the look's defaults. The application starts
the theme under the selection's kit, and PluginRuntime.start consumes the
selection without a second scan. The start-up line reports look=<id>.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: The kit reaches every `KitContext`

Every `KitContext` this process makes carries the presentation's kit (spec 4.9 and 6.3): the theme controller holds the
kit it installs and gives it to `AppearanceBindings`, `PresentationIcons` keeps its source context's kit, and plugins'
`HostedKit` takes it through the new pre-start setter `PluginRuntime.presentation(Kit)`. Plan 1's entry conditions are
met here (Rulings 3): no `KitContext` is made from the installed look and feel any more.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java:56-104`, `AppearanceBindings.java:11-26`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java:23-26`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (setter and host), `PluginHost.java:98`,
  `HostedContext.java:91`, `HostedKit.java:29-56`, `HostedKitView.java:30`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`startPlugins`), `docs/design/theme-engine/migration.csv` (one row)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java`
  (a new case, and plan 1's `appContextsAreMadeOverTheInstalledKit`, whose `PresentationIcons` assertion becomes the new rule)

**Interfaces:**
- Consumes: `JasperApplication.presentation()` and the 8-argument constructor (Task 9).
- Produces: `ThemeController.kit()`; `AppearanceBindings(ThemePair initial, Kit kit, Consumer<Throwable> failures,
  BooleanSupplier animations)` (the 3-argument form reads the installed kit when a context is made);
  `PluginRuntime.presentation(Kit kit)` (throws once started); `HostedKit(String owner, BooleanSupplier open,
  Supplier<ThemePair> themes, Appearance appearance, Containment containment, BooleanSupplier animations,
  Function<String,String> shortcuts, Supplier<Kit> kit)`; `HostedKitView.context()` (package-private).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.testsupport.LookFixtures;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.Map;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.9 and 16.1.8: every KitContext construction site carries the presentation's kit, the fallback's included. */
class PresentationReachTest {
    @TempDir Path home;

    /** A provider whose kit differs from the default one in a metric every context exposes. */
    PluginSelection selection() throws Exception {
        Path dev = LookFixtures.plugin(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\ncontrol_height = 26\n"));
        return PluginSelection.of(new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")), null, "", dev, false);
    }

    @Test void theControllerInstallsAndHandsOutItsKitThroughEveryConfigure() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var kit = selection().presentation().kit();
        assertThat(kit).isNotEqualTo(Kit.DEFAULT);
        try {
            edt(() -> {
                try (var controller = ThemeController.startup(true, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
                        failure -> { throw new AssertionError(failure); })) {
                    assertThat(controller.kit()).isSameAs(kit);
                    assertThat(controller.appearance().context(ThemeRoute.CHROME).kit()).isSameAs(kit);
                    assertThat(FlatLafInstaller.installedKit()).isSameAs(kit);
                    controller.configure(false, TerminalSchemeChoice.MATCH);
                    assertThat(controller.appearance().context(ThemeRoute.WORKSPACE).kit()).isSameAs(kit);
                    assertThat(FlatLafInstaller.installedKit()).as("every install of the controller uses its kit").isSameAs(kit);
                    var icons = new dev.jasper.app.platform.PresentationIcons();
                    assertThat(icons.context(controller.appearance().context(ThemeRoute.CHROME)).kit()).isSameAs(kit);
                    icons.close();
                }
            });
        } finally { edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } }); }
    }

    @Test void theApplicationRunsTheSelectionsPresentation() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = selection();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                assertThat(application[0].presentation()).isSameAs(selection.presentation());
                assertThat(application[0].themes().kit()).isSameAs(selection.presentation().kit());
                assertThat(application[0].themes().appearance().context(ThemeRoute.CHROME).kit()).isSameAs(selection.presentation().kit());
            });
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
}
```

Append to `jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java` (before its final closing brace), and the
runtime's setter check:

```java
  @org.junit.jupiter.api.Test void aPluginsViewsAreMadeWithThePresentationsKit() throws Exception {
    var kit = new dev.jasper.ui.kit.Kit("reach", dev.jasper.ui.kit.Kit.DEFAULT.metrics().with(dev.jasper.ui.metrics.DesignMetrics.Role.BUTTON_HEIGHT, 26),
        dev.jasper.ui.kit.Kit.DEFAULT.type(), dev.jasper.ui.kit.Kit.DEFAULT.faces(), dev.jasper.ui.kit.Kit.DEFAULT.controls(),
        dev.jasper.ui.kit.Kit.DEFAULT.colors(), dev.jasper.ui.kit.Kit.DEFAULT.uiKeys());
    javax.swing.SwingUtilities.invokeAndWait(() -> {
      var hosted = new HostedKit("owner", () -> true, () -> dev.jasper.app.appearance.ThemeTestSupport.pair(false), null,
          new Containment(java.awt.EventQueue::isDispatchThread), () -> false, action -> "", () -> kit);
      var view = hosted.create(new dev.jasper.sdk.ui.ViewSpec(new dev.jasper.sdk.ui.Nodes.Text("t", dev.jasper.sdk.ui.ControlState.READY, "x",
          dev.jasper.sdk.ui.Nodes.TextRole.BODY)), event -> { });
      org.assertj.core.api.Assertions.assertThat(view.context().kit()).isSameAs(kit);
      view.close(); hosted.close();
    });
  }
```

(If `HostedKitTest`'s existing cases build their views from another `ViewSpec`, reuse that one; any single node will do.)

Plan 1's `HostedKitTest.appContextsAreMadeOverTheInstalledKit` asserts that `PresentationIcons` makes its context over
the installed kit ("the installed kit, not the source's"), which this task reverses: an icon context keeps its source
context's kit. Its `AppearanceBindings` (three-argument form) and `HostedKit` (seven-argument form) assertions still
hold, so only the icon assertion and the name change:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java"); text = path.read_text(encoding="utf-8")
edits = [
    (' @Test void appContextsAreMadeOverTheInstalledKit() throws Exception {',
     ' @Test void appContextsAreMadeOverTheInstalledKitAndIconContextsKeepTheirSources() throws Exception {'),
    ('''   assertThat(receiver.context(source).kit()).as("the installed kit, not the source's").isSameAs(tall);''',
     '''   assertThat(receiver.context(source).kit()).as("the source context's kit, not the installed one").isSameAs(source.kit());'''),
]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"HostedKitTest: expected one {old!r}; re-find it")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.PresentationReachTest' --tests 'dev.jasper.app.plugins.HostedKitTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `ThemeController.kit()`, the eight-argument
`HostedKit` constructor and `HostedKitView.context()`.

- [ ] **Step 3: Carry the kit**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 10 step 3: the presentation's kit reaches every KitContext."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
AP = "jasper-app/src/main/java/dev/jasper/app/appearance/"
edit(AP + "ThemeController.java", [
    ('''        Objects.requireNonNull(kit);
        return startup(dark, selection, theme -> ThemeManager.install(theme, kit), fallingBack);
    }''',
     '''        Objects.requireNonNull(kit);
        try { return new ThemeController(new Shown(dark, selection), kit, theme -> ThemeManager.install(theme, kit)); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), kit,
                theme -> ThemeManager.install(theme, kit));
        }
    }''', 1),
    ('''        try { return new ThemeController(new Shown(dark, selection), installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), installer);
        }''',
     '''        try { return new ThemeController(new Shown(dark, selection), Kit.DEFAULT, installer); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), Kit.DEFAULT, installer);
        }''', 1),
    ('''        this(new Shown(dark, ThemeSelection.slateCobalt(Objects.requireNonNull(scheme))), installer);''',
     '''        this(new Shown(dark, ThemeSelection.slateCobalt(Objects.requireNonNull(scheme))), Kit.DEFAULT, installer);''', 1),
    ('''        this(new Shown(dark, Objects.requireNonNull(selection)), ThemeController::installDefault);''',
     '''        this(new Shown(dark, Objects.requireNonNull(selection)), Kit.DEFAULT, ThemeController::installDefault);''', 1),
    ('''        this(new Shown(dark, Objects.requireNonNull(selection)), installer);
    }''', '''        this(new Shown(dark, Objects.requireNonNull(selection)), Kit.DEFAULT, installer);
    }''', 1),
    ('''    private ThemeController(Shown shown, Predicate<GeneratedTheme> installer) {
        requireEdt();
        this.installer = Objects.requireNonNull(installer);''',
     '''    private ThemeController(Shown shown, Kit kit, Predicate<GeneratedTheme> installer) {
        requireEdt();
        this.kit = Objects.requireNonNull(kit);
        this.installer = Objects.requireNonNull(installer);''', 1),
    ('''        appearance = new AppearanceBindings(current.pair(), ThemeController::reportFailure, () -> animationsEnabled);''',
     '''        appearance = new AppearanceBindings(current.pair(), kit, ThemeController::reportFailure, () -> animationsEnabled);''', 1),
    ('''    private final Predicate<GeneratedTheme> installer;''',
     '''    private final Predicate<GeneratedTheme> installer;
    /** The kit every install of this controller uses and every context it hands out carries. */
    private final Kit kit;''', 1),
    ('''    public ResolvedTheme current() { requireEdt(); return current; }''',
     '''    public ResolvedTheme current() { requireEdt(); return current; }
    /** The kit this controller installs under and its contexts carry. */
    public Kit kit() { return kit; }''', 1)])
edit(AP + "AppearanceBindings.java", [
    ('''    private final BooleanSupplier animations;
    private java.util.function.Function<String, String> shortcuts = action -> "";
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
    }''',
     '''    private final BooleanSupplier animations;
    private final java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit;
    private java.util.function.Function<String, String> shortcuts = action -> "";
    /** Bindings whose contexts carry the installed look and feel's kit, read when each context is made. */
    public AppearanceBindings(ThemePair initial, Consumer<Throwable> failures, BooleanSupplier animations) {
        this(initial, FlatLafInstaller::installedKit, failures, animations);
    }
    /** Bindings whose contexts carry {@code kit}, the presentation's. */
    public AppearanceBindings(ThemePair initial, dev.jasper.ui.kit.Kit kit, Consumer<Throwable> failures, BooleanSupplier animations) {
        this(initial, () -> kit, failures, animations);
        java.util.Objects.requireNonNull(kit);
    }
    private AppearanceBindings(ThemePair initial, java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit, Consumer<Throwable> failures,
                               BooleanSupplier animations) {
        binding = new ThemeBinding(initial, failures);
        this.animations = java.util.Objects.requireNonNull(animations);
        this.kit = kit;
    }''', 1),
    ('''    /** A context for {@code route} over the installed kit, read now: contexts are made after the start-up install. */
    public KitContext context(ThemeRoute route) {
        return KitContext.of(FlatLafInstaller.installedKit(), binding, route, icons, animations, action -> shortcuts.apply(action));
    }''',
     '''    /** A context for {@code route} over this binding's kit. */
    public KitContext context(ThemeRoute route) {
        return KitContext.of(kit.get(), binding, route, icons, animations, action -> shortcuts.apply(action));
    }''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java", [
    ('''        return KitContext.of(dev.jasper.ui.swing.FlatLafInstaller.installedKit(), source.binding(), source.route(), registry,''',
     '''        return KitContext.of(source.kit(), source.binding(), source.route(), registry,''', 1)])
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginHost.java", [
    ('''    java.util.function.BooleanSupplier animations = () -> true;''',
     '''    java.util.function.BooleanSupplier animations = () -> true;
    /** The kit plugins' views are made with: the presentation's, or the installed look and feel's for a runtime given none. */
    java.util.function.Supplier<dev.jasper.ui.kit.Kit> kit = dev.jasper.ui.swing.FlatLafInstaller::installedKit;''', 1)])
edit(P + "HostedContext.java", [
    ('''() -> host.animations.getAsBoolean(), action -> host.shortcuts.apply(action));''',
     '''() -> host.animations.getAsBoolean(), action -> host.shortcuts.apply(action), () -> host.kit.get());''', 1)])
edit(P + "HostedKit.java", [
    ('''    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment,BooleanSupplier animations,java.util.function.Function<String,String> shortcuts){this.owner=owner;this.open=open;this.themes=themes;this.appearance=appearance;this.containment=containment;this.animations=Objects.requireNonNull(animations);this.shortcuts=Objects.requireNonNull(shortcuts);}''',
     '''    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment,BooleanSupplier animations,java.util.function.Function<String,String> shortcuts){this(owner,open,themes,appearance,containment,animations,shortcuts,FlatLafInstaller::installedKit);}
    /** {@code kit} supplies the presentation's kit, which every view of the plugin is made with. */
    HostedKit(String owner,BooleanSupplier open,Supplier<ThemePair> themes,Appearance appearance,Containment containment,BooleanSupplier animations,java.util.function.Function<String,String> shortcuts,Supplier<dev.jasper.ui.kit.Kit> kit){this.owner=owner;this.open=open;this.themes=themes;this.appearance=appearance;this.containment=containment;this.animations=Objects.requireNonNull(animations);this.shortcuts=Objects.requireNonNull(shortcuts);this.kit=Objects.requireNonNull(kit);}
    private final Supplier<dev.jasper.ui.kit.Kit> kit;''', 1),
    ('''new HostedKitView(this,KitContext.of(FlatLafInstaller.installedKit(),binding,ThemeRoute.CHROME,icons,animations,shortcuts),spec,events)''',
     '''new HostedKitView(this,KitContext.of(kit.get(),binding,ThemeRoute.CHROME,icons,animations,shortcuts),spec,events)''', 1)])
edit(P + "HostedKitView.java", [
    (''' HostedKitView(HostedKit owner,KitContext context,ViewSpec spec,Consumer<UiEvent> events){this.owner=owner;this.events=Objects.requireNonNull(events);''',
     ''' private final KitContext context;
 /** The context the view was made with. */
 KitContext context(){return context;}
 HostedKitView(HostedKit owner,KitContext context,ViewSpec spec,Consumer<UiEvent> events){this.owner=owner;this.context=context;this.events=Objects.requireNonNull(events);''', 1)])
edit(P + "PluginRuntime.java", [
    ('''    /** Supplies the live application motion policy before plugin startup. */''',
     '''    private dev.jasper.ui.kit.Kit kit;
    /** Supplies the presentation's kit, which plugins' views are made with, before plugin startup (spec 4.9). */
    public void presentation(dev.jasper.ui.kit.Kit kit) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        this.kit = Objects.requireNonNull(kit);
    }
    /** Supplies the live application motion policy before plugin startup. */''', 1),
    ('''        created.animations = animations;''',
     '''        created.animations = animations;
        if (kit != null) { var chosen = kit; created.kit = () -> chosen; }''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ('''        plugins.animations(themes::animationsEnabled);''',
     '''        plugins.animations(themes::animationsEnabled);
        // The kit the controller installed: the presentation's, or the default one when the kit was blamed (spec 4.7).
        plugins.presentation(themes.kit());''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Add `PresentationReachTest.java`'s ledger row at the end of `docs/design/theme-engine/migration.csv` first (one line; it imports
`dev.jasper.ui` and `javax.swing`):

```
jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java,app.application,PresentationReachTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 10; the presentation's kit reaches every KitContext
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.architecture.StaticDefaultTest'`
Expected: PASS: `PresentationReachTest` (2), the new `HostedKitTest` case, the renamed
`appContextsAreMadeOverTheInstalledKitAndIconContextsKeepTheirSources` and every existing appearance, platform, plugin,
workspace and application test, and `ThemeMigrationCoverageTest` (in `appearance.*`) names no missing row.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance jasper-app/src/main/java/dev/jasper/app/platform/PresentationIcons.java \
  jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java jasper-app/src/test/java/dev/jasper/app/plugins/HostedKitTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Presentation provider: the kit reaches every KitContext

The theme controller holds the kit it installs and hands it to every
context through AppearanceBindings; PresentationIcons keeps its source
context's kit; plugins' views are made with the kit PluginRuntime receives
through the new pre-start setter presentation(Kit). No KitContext is made
from the installed look and feel any more.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 11: The fallback at start

`ThemeController.start` tries spec 4.7's four steps in order and says which installed: the selection under the chosen
kit; the selection under `Kit.DEFAULT` (the kit is blamed); Slate Cobalt Dark under the chosen kit (the theme was at
fault, as today); Slate Cobalt Dark under `Kit.DEFAULT` (blaming the kit), whose failure propagates. With the default kit
only the first and third exist. When the kit is blamed, the application switches to `selection.blamed()` (the provider
skipped, so no service of it is published; `Presentation.fallback(Host)`, keeping the kit defaults read at start) before
plugins start, and records `failed`. The switch assigns the field `this.selection`, never Task 9's constructor
parameter (`bootstrapSelection`), so `startPlugins(AppDirs)` starts the blamed selection; an application-level case in
`PresentationReachTest` proves it through a package-private constructor that takes the installers.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java:49-73`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (constructor, `pluginRuntime()`), `docs/design/theme-engine/migration.csv` (one row)
- Test: `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerFallbackTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java` (one case),
  `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java` (`loadable`)

**Interfaces:**
- Consumes: `ThemeController(Shown, Kit, Predicate)` (Task 10); `PluginSelection.blamed()`, `attempt()` (Task 9).
- Produces: `public enum ThemeController.Step { CHOSEN, DEFAULT_KIT, FALLBACK_THEME, FALLBACK_THEME_DEFAULT_KIT }` with
  `blamesKit()`; `public record ThemeController.Started(ThemeController controller, Step step)`;
  `public static Started ThemeController.start(boolean dark, ThemeSelection selection, Kit kit,
  Consumer<InstallationFailure> fallingBack)`, the public seam
  `start(boolean, ThemeSelection, Kit, Function<Kit, Predicate<GeneratedTheme>> installers, Consumer<InstallationFailure>)`
  and `public static final Function<Kit, Predicate<GeneratedTheme>> ThemeController.INSTALLERS` (the production
  installers); `startup(dark, selection, kit, fallingBack)` returns `start(…).controller()`; the package-private
  nine-argument `JasperApplication(…, PluginSelection bootstrapSelection, Function<Kit, Predicate<GeneratedTheme>>
  installers)`, which the public eight-argument constructor calls with `INSTALLERS`; package-private
  `PluginRuntime JasperApplication.pluginRuntime()`; `LookFixtures.loadable(Path root, String id, String descriptor,
  Map<String, String> resources)` (a provider whose `Main` starts and writes `started` into its data folder).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerFallbackTest.java`:

```java
package dev.jasper.app.appearance;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.JasperTheme;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.function.Predicate;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 4.7: the four steps of the fallback at start, and which of them blame the kit. */
class ThemeControllerFallbackTest {
    static final Kit CLASSIC = new Kit("classic-test", Kit.DEFAULT.metrics().with(DesignMetrics.Role.BUTTON_HEIGHT, 22), Kit.DEFAULT.type(),
        Kit.DEFAULT.faces(), Kit.DEFAULT.controls(), Kit.DEFAULT.colors(), Kit.DEFAULT.uiKeys());
    /** Light, so the fallback theme (Slate Cobalt Dark) is a different variant. */
    static final ThemeSelection LIGHT = ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH);

    record Run(ThemeController.Started started, List<ThemeController.InstallationFailure> failures) { }

    /** Starts with installers that fail where {@code fails} says (kit, theme), and otherwise install nothing. */
    static Run start(Kit kit, BiPredicate<Kit, GeneratedTheme> fails) throws Exception {
        var failures = new ArrayList<ThemeController.InstallationFailure>();
        var started = new ThemeController.Started[1];
        Function<Kit, Predicate<GeneratedTheme>> installers = chosen -> theme -> {
            if (fails.test(chosen, theme)) throw new IllegalStateException("cannot install " + theme.name() + " under " + chosen.id());
            return true;
        };
        SwingUtilities.invokeAndWait(() -> started[0] = ThemeController.start(false, LIGHT, kit, installers, failures::add));
        return new Run(started[0], failures);
    }

    @Test void theSelectionUnderTheChosenKitIsTheFirstStep() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> false);
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.CHOSEN);
        assertThat(run.started().step().blamesKit()).isFalse();
        assertThat(run.started().controller().kit()).isSameAs(CLASSIC);
        assertThat(run.failures()).isEmpty();
    }

    @Test void theSelectionUnderTheDefaultKitBlamesTheKit() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> kit == CLASSIC);
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.DEFAULT_KIT);
        assertThat(run.started().step().blamesKit()).isTrue();
        assertThat(run.started().controller().kit()).isSameAs(Kit.DEFAULT);
        SwingUtilities.invokeAndWait(() -> assertThat(run.started().controller().current().chrome().dark()).as("the user's Light").isFalse());
        assertThat(run.failures()).hasSize(1);
    }

    @Test void slateCobaltDarkUnderTheChosenKitBlamesTheTheme() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME);
        assertThat(run.started().step().blamesKit()).isFalse();
        assertThat(run.started().controller().kit()).isSameAs(CLASSIC);
        SwingUtilities.invokeAndWait(() -> assertThat(run.started().controller().current().chrome()).isSameAs(JasperTheme.bundled().generated(true)));
        assertThat(run.failures()).hasSize(2);
    }

    @Test void slateCobaltDarkUnderTheDefaultKitIsTheLastStepAndItsFailurePropagates() throws Exception {
        var run = start(CLASSIC, (kit, theme) -> kit == CLASSIC || !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME_DEFAULT_KIT);
        assertThat(run.started().step().blamesKit()).isTrue();
        assertThat(run.failures()).hasSize(3);
        assertThatThrownBy(() -> start(CLASSIC, (kit, theme) -> true)).hasRootCauseInstanceOf(IllegalStateException.class);
    }

    @Test void theDefaultKitHasOnlyTodaysTwoSteps() throws Exception {
        var run = start(Kit.DEFAULT, (kit, theme) -> !theme.dark());
        assertThat(run.started().step()).isEqualTo(ThemeController.Step.FALLBACK_THEME);
        assertThat(run.failures()).as("no default-kit retry of the same kit").hasSize(1);
        assertThatThrownBy(() -> start(Kit.DEFAULT, (kit, theme) -> true)).hasRootCauseInstanceOf(IllegalStateException.class);
    }
}
```

(`start` on the EDT through `invokeAndWait` wraps a propagated failure in an `InvocationTargetException`, whose root
cause is the installer's `IllegalStateException`.)

Add to `jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java` (before its final closing brace):

```java
    /**
     * As {@link #plugin}, with a {@code Main} class in package {@code id} (the descriptor's entry) whose start writes
     * {@code started} into the plugin's data folder: a provider that loads, so only its look decides its attempt.
     */
    public static Path loadable(Path root, String id, String descriptor, Map<String, String> resources) throws IOException {
        String main = "package " + id + ";\npublic final class Main implements dev.jasper.sdk.plugin.Plugin {\n"
            + "    @Override public void start(dev.jasper.sdk.plugin.PluginContext context) {\n"
            + "        try { java.nio.file.Files.writeString(context.dataDirectory().resolve(\"started\"), \"yes\"); }\n"
            + "        catch (java.io.IOException failure) { throw new java.io.UncheckedIOException(failure); }\n    }\n}\n";
        Path jar = PluginJars.build(root.resolve(id).resolve("jars"), id + ".jar", descriptor, Map.of(id + ".Main", main), List.of());
        for (var resource : new java.util.TreeMap<>(resources).entrySet()) PluginJars.addResource(jar, resource.getKey(), resource.getValue());
        return root.resolve(id);
    }
```

Append to `PresentationReachTest` (before its final closing brace):

```java
    /** Spec 4.7 and 16.1.8: a kit that cannot be installed runs the fallback value, and its provider never starts. */
    @Test void aKitThatCannotBeInstalledRunsTheFallbackValueAndSkipsItsProvider() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var dirs = new AppDirs(home, home.resolve("config.toml"), home.resolve("logs"));
        Path devPlugins = LookFixtures.loadable(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\ncontrol_height = 26\n"));
        var selection = PluginSelection.of(dirs, null, "B", devPlugins, false);
        var kit = selection.presentation().kit();
        assertThat(kit).isNotEqualTo(Kit.DEFAULT);
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null,
                    selection, chosen -> theme -> {
                        if (chosen == kit) throw new IllegalStateException("cannot install under " + chosen.id());
                        return ThemeController.INSTALLERS.apply(chosen).test(theme);
                    });
                assertThat(application[0].presentation()).as("the fallback value").isEqualTo(selection.blamed().presentation());
                assertThat(application[0].presentation().provider()).isEmpty();
                assertThat(application[0].themes().kit()).isSameAs(Kit.DEFAULT);
                assertThat(application[0].themes().appearance().context(ThemeRoute.CHROME).kit()).isSameAs(Kit.DEFAULT);
                application[0].startPlugins(dirs);
                assertThat(application[0].pluginRuntime().statusLines()).anySatisfy(line -> assertThat(line)
                    .contains("dev.example.look", "SKIPPED", "its look could not be installed; Jasper uses its standard look"));
            });
            assertThat(dirs.plugins().resolve("dev.example.look/data/started")).as("the provider never started, so it published no service")
                .doesNotExist();
            dev.jasper.app.workspace.DesktopTestSupport.until(() -> new dev.jasper.app.presentation.AttemptStore(home).read()
                .of("dev.example.look").state().equals(java.util.Optional.of(dev.jasper.app.presentation.AttemptStore.State.FAILED)));
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }
```

Without the field assignment (`this.selection = this.selection.blamed()`), the status line would show the provider
loaded and `started` would exist: this case is the guard on Task 9's parameter name.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeControllerFallbackTest' --tests 'dev.jasper.app.application.PresentationReachTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `ThemeController.Started`, `Step`, `start`,
`INSTALLERS`, the nine-argument `JasperApplication` constructor and `pluginRuntime()`.

- [ ] **Step 3: Write the four steps and use them at start**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 11 step 3: spec 4.7's fallback chain."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java", [
    ('''    public static ThemeController startup(boolean dark, ThemeSelection selection, Kit kit,
                                          java.util.function.Consumer<InstallationFailure> fallingBack) {
        Objects.requireNonNull(kit);
        try { return new ThemeController(new Shown(dark, selection), kit, theme -> ThemeManager.install(theme, kit)); }
        catch (InstallationFailure failure) {
            fallingBack.accept(failure);
            var cobalt = JasperTheme.bundled();
            return new ThemeController(new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme())), kit,
                theme -> ThemeManager.install(theme, kit));
        }
    }''',
     '''    public static ThemeController startup(boolean dark, ThemeSelection selection, Kit kit,
                                          java.util.function.Consumer<InstallationFailure> fallingBack) {
        return start(dark, selection, kit, fallingBack).controller();
    }

    /** Which of spec 4.7's attempts installed. */
    public enum Step {
        /** The selection under the chosen kit. */
        CHOSEN,
        /** The selection under the default kit: the chosen kit is blamed. */
        DEFAULT_KIT,
        /** Slate Cobalt Dark under the chosen kit: the theme was at fault. */
        FALLBACK_THEME,
        /** Slate Cobalt Dark under the default kit: the chosen kit is blamed too. */
        FALLBACK_THEME_DEFAULT_KIT;

        /** Whether this step means the chosen kit could not be installed. */
        public boolean blamesKit() { return this == DEFAULT_KIT || this == FALLBACK_THEME_DEFAULT_KIT; }
    }

    /**
     * A controller made at start and the step that installed it.
     *
     * @param controller the controller
     * @param step which attempt installed
     */
    public record Started(ThemeController controller, Step step) { }

    /**
     * Start-up under {@code kit} with spec 4.7's fallback: the selection under {@code kit}, then under the default kit,
     * then Slate Cobalt Dark under {@code kit}, then under the default kit; with the default kit only the first and third.
     * Each failure but the last is given to {@code fallingBack}; the last propagates. Every later install of the controller
     * uses the kit of the step that installed.
     */
    public static Started start(boolean dark, ThemeSelection selection, Kit kit, java.util.function.Consumer<InstallationFailure> fallingBack) {
        Objects.requireNonNull(kit);
        return start(dark, selection, kit, INSTALLERS, fallingBack);
    }

    /** The production installers: a theme installed into Swing under a kit. Applied on the EDT. */
    public static final java.util.function.Function<Kit, Predicate<GeneratedTheme>> INSTALLERS = chosen -> theme -> ThemeManager.install(theme, chosen);

    /**
     * {@link #start(boolean, ThemeSelection, Kit, java.util.function.Consumer)} with {@code installers}, which tests replace
     * to fail under a chosen kit (spec 4.7's fallback tests).
     */
    public static Started start(boolean dark, ThemeSelection selection, Kit kit,
                         java.util.function.Function<Kit, Predicate<GeneratedTheme>> installers,
                         java.util.function.Consumer<InstallationFailure> fallingBack) {
        record Attempt(Shown shown, Kit kit, Step step) { }
        var cobalt = JasperTheme.bundled();
        var chosen = new Shown(dark, selection);
        var fallback = new Shown(true, new ThemeSelection(selection.library(), cobalt, cobalt, selection.scheme()));
        boolean other = !kit.equals(Kit.DEFAULT);
        var attempts = new java.util.ArrayList<Attempt>();
        attempts.add(new Attempt(chosen, kit, Step.CHOSEN));
        if (other) attempts.add(new Attempt(chosen, Kit.DEFAULT, Step.DEFAULT_KIT));
        attempts.add(new Attempt(fallback, kit, Step.FALLBACK_THEME));
        if (other) attempts.add(new Attempt(fallback, Kit.DEFAULT, Step.FALLBACK_THEME_DEFAULT_KIT));
        for (int i = 0; ; i++) {
            var attempt = attempts.get(i);
            try { return new Started(new ThemeController(attempt.shown(), attempt.kit(), installers.apply(attempt.kit())), attempt.step()); }
            catch (InstallationFailure failure) {
                if (i == attempts.size() - 1) throw failure;
                fallingBack.accept(failure);
            }
        }
    }''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ('''        themes = ThemeController.startup(dark, picks, presentation.kit(),
            failure -> LOG.log(System.Logger.Level.WARNING, "Starting in Slate Cobalt Dark: " + failure.getMessage(), failure));''',
     '''        var started = ThemeController.start(dark, picks, presentation.kit(), installers,
            failure -> LOG.log(System.Logger.Level.WARNING, "Could not start with this theme and look; trying the next: " + failure.getMessage(), failure));
        themes = started.controller();
        // Spec 4.7: the kit was at fault. Every consumer gets the fallback before plugins start, and the provider is skipped.
        // The field, not the constructor's bootstrapSelection: startPlugins(AppDirs) starts this.selection.
        if (started.step().blamesKit()) {
            LOG.log(System.Logger.Level.WARNING, "The " + presentation.look() + " look could not be installed; Jasper uses its standard look");
            var attempt = this.selection.attempt();
            this.selection = this.selection.blamed();
            presentation = this.selection.presentation();
            Thread.ofVirtual().name("jasper-presentation-attempt").start(attempt::failed);
        }''', 1),
    ('''                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection) {
        // Not named selection: the field changes during construction when the kit is blamed (spec 4.7), the parameter never.''',
     '''                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection) {
        this(service, suppliedLauncher, history, buddyStateFile, terminate, shellIntegrationDir, themesFolder, bootstrapSelection,
            dev.jasper.app.appearance.ThemeController.INSTALLERS);
    }

    /** As above, with the installers that put a theme in place under a kit: the seam of spec 4.7's fallback tests. */
    JasperApplication(ConfigService service, ShellLauncher suppliedLauncher, CommandHistory history, Path buddyStateFile,
                      Runnable terminate, Path shellIntegrationDir, Path themesFolder, dev.jasper.app.plugins.PluginSelection bootstrapSelection,
                      java.util.function.Function<dev.jasper.ui.kit.Kit, java.util.function.Predicate<dev.jasper.ui.theme.GeneratedTheme>> installers) {
        // Not named selection: the field changes during construction when the kit is blamed (spec 4.7), the parameter never.''', 1),
    ('''    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }''',
     '''    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }

    /** The running plugins; null before {@link #startPlugins(AppDirs)}. Tests. */
    PluginRuntime pluginRuntime() { return plugins; }''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Add `ThemeControllerFallbackTest.java`'s ledger row at the end of `docs/design/theme-engine/migration.csv` first (one line; it imports
`dev.jasper.ui`):

```
jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerFallbackTest.java,app.appearance,ThemeControllerFallbackTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerFallbackTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 11; the four fallback steps at start
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.*' --tests 'dev.jasper.app.workspace.ThemeControllerTest' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.architecture.StaticDefaultTest'`
Expected: PASS: `ThemeControllerFallbackTest` (5), `PresentationReachTest` (3) and the existing controller and
application tests (`startup` behaves as before for the default kit); `ThemeMigrationCoverageTest` names no missing row.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/test/java/dev/jasper/app/appearance/ThemeControllerFallbackTest.java docs/design/theme-engine/migration.csv \
  jasper-app/src/test/java/dev/jasper/app/application/PresentationReachTest.java jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java
git commit -F - <<'EOF'
Presentation provider: the fallback at start

ThemeController.start tries the selection under the chosen kit, then under
the default kit, then Slate Cobalt Dark under the chosen kit, then under
the default kit, and says which step installed; with the default kit only
today's two steps exist and the last failure still propagates. When the
kit is blamed, the application switches to the fallback presentation and
skips the provider before plugins start, so it never starts, and the
attempt record says failed.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 12: The attempt lifecycle

The record is written where spec 4.6 says: `pending` just before the first window or a resident `--background`
rehearsal when not headless (never for `--no-window`, the background exit paths, a headless start or `quitNow`);
`proven` off the EDT at the first window's FIRST_PAINT or when the rehearsal's paint returns; an orderly quit before
proof clears `pending` in the shutdown hook; `failed` when the provider fails to load or start. A look the record skipped
posts one Warning with Try Again; an invalid kit's plugin-problem notice appears once per digest (spec 5.9).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`prepareDesktop`'s hook, `startDesktop`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`presentationAttempt`, `startPlugins`, `rehearsalWindow`, `quitNow`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java` (`start`), `PluginSelection.java` (`announceInvalid`), `docs/design/theme-engine/migration.csv` (one row)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/PresentationAttemptTest.java`, `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java`

**Interfaces:**
- Consumes: `PresentationAttempt` (Task 4), `PluginSelection.attempt()`, `skipped()` (Task 9), `PluginStatus.INVALID` (Task 5),
  `LookFixtures.loadable` (Task 11), the package-private `JasperApplication.trace(StartupTrace)`, `restartPlanner` and
  `spawner` seams.
- Produces: `JasperApplication.presentationAttempt()` (the selection's attempt while its look runs, else
  `PresentationAttempt.NONE`); package-private `void JasperApplication.rehearsalPainted()` (the rehearsal stage's
  post-paint hook); `static boolean ApplicationBootstrap.attemptBeforePresenting(AppArguments options, boolean
  resident, boolean headless)`; `boolean PluginSelection.announceInvalid(String id, PluginCandidate.Origin origin)`
  (package-private).

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/PresentationAttemptTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.notifications.NotificationAction;
import dev.jasper.app.notifications.Severity;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.presentation.AttemptStore;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.testsupport.LookFixtures;
import dev.jasper.app.workspace.DesktopTestSupport;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import javax.swing.LookAndFeel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 4.6: the attempt record across a start, a failed provider, a skipped look and an orderly quit. */
class PresentationAttemptTest {
    static final String ID = "dev.example.look";
    @TempDir Path home;

    AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    /** A provider with no classes: its look is chosen, and its plugin fails to load. */
    Path provider(String kit) throws Exception {
        return LookFixtures.plugin(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", kit));
    }

    /** A provider whose plugin loads and starts, so only the paint decides its attempt. */
    Path loadable() throws Exception {
        return LookFixtures.loadable(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", LookFixtures.KIT));
    }

    Optional<AttemptStore.State> state() { return new AttemptStore(home).read().of(ID).state(); }

    JasperApplication started(PluginSelection selection) throws Exception {
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
            application[0].startPlugins(dirs());
        });
        return application[0];
    }

    void quit(JasperApplication application, LookAndFeel original) throws Exception {
        edt(application::quitNow);
        edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
    }

    @Test void aProviderThatFailsToLoadRecordsFailedAndTheSessionKeepsItsLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", provider(LookFixtures.KIT), false);
        var application = started(selection);
        try {
            assertThat(new AttemptStore(home).read().of(ID).state()).contains(AttemptStore.State.FAILED);
            assertThat(application.presentation().look()).as("this session keeps the look, without the plugin").isEqualTo(ID);
            var next = PluginSelection.of(dirs(), null, "B", home.resolve("dev").resolve(ID), false);
            assertThat(next.skipped()).as("the next start skips it").isPresent();
        } finally { quit(application, original); }
    }

    @Test void aSkippedLookPostsOneWarningWithTryAgain() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider(LookFixtures.KIT);
        PluginSelection.of(dirs(), null, "B", dev, false).attempt().pending();
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        var application = started(selection);
        try {
            edt(() -> {
                var warnings = application.notificationCenter().history().stream().map(entry -> entry.request())
                    .filter(request -> request.title().equals("Jasper started with its standard look")).toList();
                assertThat(warnings).singleElement().satisfies(request -> {
                    assertThat(request.severity()).isEqualTo(Severity.WARNING);
                    assertThat(request.content()).isEqualTo("The last start with the Example look did not finish, so Jasper uses its standard look until you try again.");
                    assertThat(request.actions()).extracting(action -> action.label()).containsExactly("Try Again");
                });
                assertThat(application.notificationCenter().history()).as("the skipped look posts no plugin problem")
                    .noneMatch(entry -> entry.request().title().equals("Example was not started"));
                assertThat(application.presentationAttempt().records()).isFalse();
            });
        } finally { quit(application, original); }
    }

    @Test void anInvalidKitIsAnnouncedOncePerDigest() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider("kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 40\n");
        for (int start = 1; start <= 3; start++) {
            if (start == 3) LookFixtures.plugin(home.resolve("dev"), ID, LookFixtures.descriptor(ID, "Example") + LookFixtures.provider("look.kit.toml"),
                Map.of("look.kit.toml", "kit = 1\nbase = \"default\"\n[metrics]\nrow_height = 41\n"));
            var application = started(PluginSelection.of(dirs(), null, "B", dev, false));
            int shown = start;
            try {
                edt(() -> assertThat(application.notificationCenter().history().stream()
                        .filter(entry -> entry.request().title().equals("Example was not started")).count())
                    .as("start " + shown).isEqualTo(shown == 2 ? 0 : 1));
            } finally { quit(application, original); }
        }
    }

    @Test void theFirstWindowsFirstPaintProvesTheLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", loadable(), false);
        var trace = StartupTrace.detached();
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
            application[0].trace(trace);
            application[0].startPlugins(dirs());
            application[0].presentationAttempt().pending();
        });
        try {
            assertThat(state()).contains(AttemptStore.State.PENDING);
            trace.mark(StartupTrace.Milestone.FIRST_PAINT);
            DesktopTestSupport.until(() -> state().equals(Optional.of(AttemptStore.State.PROVEN)));
        } finally { quit(application[0], original); }
    }

    @Test void theRehearsalsPaintProvesTheLook() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        var selection = PluginSelection.of(dirs(), null, "B", loadable(), false);
        var application = started(selection);
        try {
            edt(() -> {
                application.presentationAttempt().pending();
                // What the rehearsal stage runs once Rehearsal.paint returned (a real paint needs a display).
                application.rehearsalPainted();
            });
            DesktopTestSupport.until(() -> state().equals(Optional.of(AttemptStore.State.PROVEN)));
        } finally { quit(application, original); }
    }

    @Test void tryAgainForgetsTheRecordAndRestarts() throws Exception {
        LookAndFeel original = UIManager.getLookAndFeel();
        Path dev = provider(LookFixtures.KIT);
        PluginSelection.of(dirs(), null, "B", dev, false).attempt().pending();
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        List<String> spawned = new CopyOnWriteArrayList<>();
        var terminated = new CountDownLatch(1);
        JasperApplication[] application = new JasperApplication[1];
        edt(() -> {
            application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, terminated::countDown,
                null, null, selection);
            application[0].restartPlanner = mode -> Optional.of(List.of("jasper", mode.name()));
            application[0].spawner = command -> spawned.add(String.join(" ", command));
            application[0].startPlugins(dirs());
        });
        try {
            NotificationAction[] tryAgain = new NotificationAction[1];
            edt(() -> tryAgain[0] = application[0].notificationCenter().history().stream().map(entry -> entry.request())
                .filter(request -> request.title().equals("Jasper started with its standard look")).findFirst().orElseThrow()
                .actions().getFirst());
            edt(() -> tryAgain[0].run().run());
            assertThat(terminated.await(10, TimeUnit.SECONDS)).as("Try Again restarts Jasper").isTrue();
            assertThat(spawned).containsExactly("jasper SAME");
            assertThat(state()).as("the record is forgotten").isEmpty();
            assertThat(PluginSelection.of(dirs(), null, "B", dev, false).skipped()).as("so the next start tries the look").isEmpty();
        } finally {
            edt(() -> { try { UIManager.setLookAndFeel(original); } catch (Exception failure) { throw new AssertionError(failure); } });
        }
    }

    @Test void anExitThatIsNotOrderlyBeforeProofKeepsPending() throws Exception {
        Path dev = provider(LookFixtures.KIT);
        var selection = PluginSelection.of(dirs(), null, "B", dev, false);
        selection.attempt().pending();
        // A crash or a nonzero System.exit runs the shutdown hook without an orderly quit.
        selection.attempt().exiting();
        assertThat(state()).contains(AttemptStore.State.PENDING);
        assertThat(PluginSelection.of(dirs(), null, "B", dev, false).skipped()).as("the next start skips the look").isPresent();
    }

    @Test void anOrderlyQuitBeforeProofClearsPendingAtExit() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "B", provider(LookFixtures.KIT), false);
        selection.attempt().pending();
        selection.attempt().orderlyQuit();
        selection.attempt().exiting();
        assertThat(new AttemptStore(home).read().of(ID).state()).isEmpty();
    }
}
```

Append to `ApplicationBootstrapTest` (before its final closing brace):

```java
    /** Spec 4.6: pending only on the paths about to show a window or rehearse. */
    @Test void onlyAWindowOrAResidentRehearsalRecordsAPendingLook() {
        var plain = new AppArguments(null, false, false, false, null, false, false, false);
        var background = new AppArguments(null, false, true, false, null, false, false, false);
        var noWindow = new AppArguments(null, false, false, false, null, false, false, true);
        assertThat(ApplicationBootstrap.attemptBeforePresenting(plain, false, false)).as("the first window").isTrue();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, true, false)).as("the resident rehearsal").isTrue();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, false, false)).as("a background exit (quitNow)").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(noWindow, true, false)).as("--no-window, even resident").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(plain, false, true)).as("headless").isFalse();
        assertThat(ApplicationBootstrap.attemptBeforePresenting(background, true, true)).isFalse();
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.PresentationAttemptTest' --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `presentationAttempt()`, `attemptBeforePresenting` and `rehearsalPainted()`.

- [ ] **Step 3: Write the record where spec 4.6 says**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 12 step 3: the attempt lifecycle."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
B = "jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java"
edit(B, [
    ('''            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try {
                    if (endpoint.get() != null) endpoint.get().close();''',
     '''            var attempt = startup.selection().attempt();
            Thread shutdown = Thread.ofPlatform().name("jasper-log-shutdown").unstarted(() -> {
                try {
                    // An orderly quit before the look proved itself clears its pending mark; any other exit leaves it (spec 4.6).
                    try { attempt.exiting(); } catch (RuntimeException unrecorded) { LOG.log(System.Logger.Level.WARNING, "Could not update the presentation attempt record", unrecorded); }
                    if (endpoint.get() != null) endpoint.get().close();''', 1),
    ('''                    if (!options.background()) {
                        long window = System.nanoTime();
                        application.newWindow();''',
     '''                    if (!options.background()) {
                        if (attemptBeforePresenting(options, application.resident(), GraphicsEnvironment.isHeadless()))
                            application.presentationAttempt().pending();
                        long window = System.nanoTime();
                        application.newWindow();''', 1),
    ('''                    } else {
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                    }
                });''',
     '''                    } else {
                        if (attemptBeforePresenting(options, application.resident(), GraphicsEnvironment.isHeadless()))
                            application.presentationAttempt().pending();
                        application.warmUp(() -> StartupTrace.current().mark(StartupTrace.Milestone.WARMED_UP));
                    }
                });''', 1),
    ('''    /** macOS reads these settings during toolkit/LAF initialization; never change them on reload. */''',
     '''    /**
     * Whether the launch is about to show a window or rehearse one, so the look's attempt is marked pending (spec 4.6):
     * the first window, or a resident {@code --background} warm-up, never headless and never {@code --no-window}.
     */
    static boolean attemptBeforePresenting(AppArguments options, boolean resident, boolean headless) {
        if (headless || options.noWindow()) return false;
        return !options.background() || resident;
    }

    /** macOS reads these settings during toolkit/LAF initialization; never change them on reload. */''', 1)])
text = pathlib.Path(B).read_text(encoding="utf-8")
if "import java.awt.GraphicsEnvironment;" not in text:
    text = text.replace("import java.nio.file.Path;\n", "import java.awt.GraphicsEnvironment;\nimport java.nio.file.Path;\n", 1)
    pathlib.Path(B).write_text(text, encoding="utf-8")
A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
edit(A, [
    ('''    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }''',
     '''    /** The presentation this process runs. EDT. */
    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }

    /** The attempt record of the look this process runs; none for the default look or one that fell back. Any thread. */
    public dev.jasper.app.presentation.PresentationAttempt presentationAttempt() {
        var current = presentation;
        return current.provider().isPresent() ? selection.attempt() : dev.jasper.app.presentation.PresentationAttempt.NONE;
    }''', 1),
    ('''        new PluginProblemNotices(notificationCenter, incidentNotices,
            () -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID)).announce(plugins.problems());''',
     '''        new PluginProblemNotices(notificationCenter, incidentNotices,
            () -> openSettings(null, dev.jasper.app.pluginmanager.PluginsPage.ID)).announce(plugins.problems());
        // Spec 4.6: a look the attempt record skipped says so once, and Try Again forgets the record and restarts.
        selection.skipped().ifPresent(skipped -> notificationCenter.post(NotificationRequest.of(IncidentNotices.PLUGINS.id(), Severity.WARNING,
                "Jasper started with its standard look")
            .withContent("The last start with the " + skipped.name() + " look did not finish, so Jasper uses its standard look until you try again.")
            .withAction(new dev.jasper.app.notifications.NotificationAction("Try Again", () -> Thread.ofVirtual().name("jasper-presentation-retry").start(() -> {
                skipped.attempt().clear();
                SwingUtilities.invokeLater(() -> restart(RestartMode.SAME));
            }), false))));
        // Spec 4.6: the first window's first paint proves the look, off the EDT.
        trace.when(StartupTrace.Milestone.FIRST_PAINT, task -> Thread.ofVirtual().name("jasper-presentation-proven").start(task),
            () -> presentationAttempt().proven());''', 1),
    ('''        return new Rehearsal.Stage(() -> Rehearsal.paint(window.nativeWindow()), close);''',
     '''        return new Rehearsal.Stage(() -> {
            Rehearsal.paint(window.nativeWindow());
            rehearsalPainted();
        }, close);''', 1),
    ('''    /** The attempt record of the look this process runs; none for the default look or one that fell back. Any thread. */''',
     '''    /** The rehearsal painted: the look proved itself (spec 4.6), recorded off the EDT. */
    void rehearsalPainted() {
        var attempt = presentationAttempt();
        Thread.ofVirtual().name("jasper-presentation-proven").start(attempt::proven);
    }

    /** The attempt record of the look this process runs; none for the default look or one that fell back. Any thread. */''', 1),
    ('''    public void quitNow() {
        quitting = true;''',
     '''    public void quitNow() {
        presentationAttempt().orderlyQuit();
        quitting = true;''', 1)])
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginRuntime.java", [
    ('''            if (rejected.state() == PluginStatus.State.SKIPPED && !rejected.quiet())''',
     '''            // Spec 5.9: an invalid kit's notice appears once per digest, not at every start.
            if (rejected.state() == PluginStatus.State.SKIPPED && !rejected.quiet()
                    && (!rejected.reason().startsWith(PluginStatus.INVALID) || selection.announceInvalid(rejected.id(), rejected.origin())))''', 1),
    ('''        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode(), selection.build());''',
     '''        // Spec 4.6: a provider that fails to load or start fails its look; this session keeps the chrome, without its services.
        resolution.presentation().ifPresent(provider -> {
            if (statuses.stream().anyMatch(status -> status.id().equals(provider.id()) && status.state() == PluginStatus.State.FAILED))
                selection.attempt().failed();
        });
        launch = new PluginCatalog.Launch(candidates, statuses, PluginCatalog.versions(resolution.load()), options.safeMode(), selection.build());''', 1)])
edit(P + "PluginSelection.java", [
    ('''    private final Optional<SkippedLook> skipped;

    private PluginSelection(''',
     '''    private final Optional<SkippedLook> skipped;
    private final Presentations presentations;
    private final AttemptStore store;

    private PluginSelection(''', 1),
    ('''                            Optional<SkippedLook> skipped) {
        this.options = options;''',
     '''                            Optional<SkippedLook> skipped, Presentations presentations, AttemptStore store) {
        this.presentations = presentations; this.store = store;
        this.options = options;''', 1),
    ('''            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty());''',
     '''            Presentation.standard(host()), PresentationAttempt.NONE, Optional.empty(), Presentations.NONE, null);''', 1),
    ('''        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped);''',
     '''        return new PluginSelection(options, build, candidates, problems, resolution, presentation, attempt, skipped, presentations, store);''', 1),
    ('''            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped);''',
     '''            PluginResolver.without(resolution, provider.get(), PluginStatus.LOOK_FAILED), presentation.fallback(host()), attempt, skipped,
            presentations, store);''', 1),
    ('''    List<PluginCandidate> candidates() { return candidates; }''',
     '''    /**
     * Whether the invalid kit of {@code id} from {@code origin} is news at this start: its digest was not announced before.
     * Records it as announced. Spec 5.9's "once per digest".
     */
    boolean announceInvalid(String id, PluginCandidate.Origin origin) {
        if (store == null) return true;
        for (PluginCandidate candidate : candidates)
            if (candidate.id().equals(id) && candidate.origin() == origin)
                return presentations.of(candidate).map(facts -> PresentationAttempt.announce(store, id, facts.digest())).orElse(true);
        return true;
    }

    List<PluginCandidate> candidates() { return candidates; }''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Add `PresentationAttemptTest.java`'s ledger row at the end of `docs/design/theme-engine/migration.csv` first (one line; it imports
`javax.swing`):

```
jasper-app/src/test/java/dev/jasper/app/application/PresentationAttemptTest.java,app.application,PresentationAttemptTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/PresentationAttemptTest.java,Test/fixture ownership; New in Enterprise plan 5a Task 12; the attempt lifecycle; proof; Try Again and the once-per-digest notice
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `PresentationAttemptTest` (8: the first paint and the rehearsal's paint prove the look, Try Again
clears the record and restarts, a not-orderly exit keeps `pending`, and the four cases above), the new bootstrap case and
every existing application, bootstrap and plugin test; `ThemeMigrationCoverageTest` names no missing row.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java \
  jasper-app/src/main/java/dev/jasper/app/plugins jasper-app/src/test/java/dev/jasper/app/application/PresentationAttemptTest.java \
  jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Presentation provider: the attempt lifecycle

The look's attempt is pending just before the first window or a resident
rehearsal when not headless, proven at the first window's first paint or
when the rehearsal painted, cleared at exit after an orderly quit before
proof, and failed when its provider fails to load or start. A look the
record skipped posts one Warning with Try Again, which forgets the record
and restarts; an invalid kit's notice appears once per digest.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 13: Documentation, STATUS and the whole build

The smallest accurate edits to the documents spec 17 names for this plan's part (the descriptor keys and the capability,
one active presentation, safe mode and the opt-in, the `presentation` package, the trace's `look=`, and AGENTS.md's
package list), a new `docs/STATUS.md` entry at the top in the style of the plan-1 and plan-2 entries, the pixel proof and
the full build with its counts.

**Files:**
- Modify: `docs/plugin-authoring.md:20-23,37` and the capability table (`:278`), `docs/sdk-architecture.md:29-36`,
  `docs/app-architecture.md:181` (a `presentation` row), `docs/diagnostics.md:33,40`, `AGENTS.md:24`, `docs/STATUS.md:1`
- Modify: this plan's status banner (first lines of this file)

**Interfaces:**
- Consumes: everything above.
- Produces: nothing new.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
"""Plan 5a Task 13 step 1: the smallest accurate documentation edits."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/plugin-authoring.md", [
    ('''clipboard.read, terminal.environment, editor.provide
exports = []''', '''clipboard.read, terminal.environment, editor.provide, presentation.provide
exports = []''', 1),
    ('''homepage = "https://example.dev/tool"  # optional, SDK 0.9.3: an http or https link on your plugin's page
''', '''homepage = "https://example.dev/tool"  # optional, SDK 0.9.3: an http or https link on your plugin's page
presentation = "presentation/look.kit.toml"  # optional, SDK 0.18.0: a kit file in your jars; needs presentation.provide
enabled_by_default = true          # optional, SDK 0.18.0: false keeps a bundled plugin off until the user turns it on
''', 1),
    ('''| `editor.provide` | `Platform.editor`: be the editor Jasper opens files with (SDK 0.17.1) |''',
     '''| `editor.provide` | `Platform.editor`: be the editor Jasper opens files with (SDK 0.17.1) |
| `presentation.provide` | nothing gated: with the `presentation` key, provide Jasper's look from your kit file, from the next start (SDK 0.18.0) |''', 1),
    ('''not an `http` or `https` address, are ignored: the plugin still loads, with the default icon and no link.
''', '''not an `http` or `https` address, are ignored: the plugin still loads, with the default icon and no link.

A presentation provider names its kit file with `presentation` and declares `presentation.provide`; the file's format is
section 5 of the [Enterprise presentation design](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md).
Jasper reads it before the first window, so a change applies after a restart, and one look is active at a time: a
`--plugin-dir` provider wins over a bundled one, which wins over an installed one, then the lowest id. A kit file that is
invalid skips the plugin with one message naming the key, a look that did not finish starting is skipped at the next
start until the user tries again, and `--safe-mode` uses the standard look. `enabled_by_default = false` keeps a bundled
plugin off until the user turns it on in Settings › Plugins; development and installed plugins ignore it.
''', 1)])
edit("docs/sdk-architecture.md", [
    ('''`JasperApplication.startPlugins` runs once on the EDT before the first window:''',
     '''`PluginSelection` discovers and resolves once on the bootstrap thread, before AWT and before the configuration is
read; `JasperApplication.startPlugins` then loads and starts its plugins once on the EDT before the first window:''', 1),
    ('''   id, checks the SDK range, applies `plugins.toml` (disabled, marked for removal, consent),
   removes dependency cycles and unmet hard dependencies, and orders dependencies first.''',
     '''   id, checks the SDK range, applies `plugins.toml` (disabled, marked for removal, consent, and the opt-in of a bundled
   plugin that is off by default), removes dependency cycles and unmet hard dependencies, elects one presentation
   provider (development, then bundled, then installed, then the lowest id; the others, an invalid kit and a look the
   attempt record skips are skipped; safe mode disables every provider), and orders dependencies first.''', 1)])
edit("docs/app-architecture.md", [
    ('''| `plugins` | The plugin runtime: descriptors,''',
     '''| `presentation` | The presentation a process runs with, chosen once before Swing: the kit file parser (`KitParser`, `ChromeSpec`, `FontFile`), each provider's `PresentationFacts`, the attempt record (`AttemptStore`, `PresentationAttempt`), the projections of spec 9.1 and `Presentation`. Values and bounded file stores; `PluginSelection` builds it and the application hands each consumer its kit or projection. |
| `plugins` | The plugin runtime: descriptors,''', 1)])
edit("docs/diagnostics.md", [
    ('''    Startup (cold, AOT cache used, CDS on): main entered 71 ms''', '''    Startup (cold, AOT cache used, CDS on, look=default): main entered 71 ms''', 1),
    ('''(`JASPER_AOT_CACHE_SKIP` is set).''', '''(`JASPER_AOT_CACHE_SKIP` is set). `look=` names the look: `default`, or the id of the plugin that provides it.''', 1)])
edit("AGENTS.md", [
    ('''SDK types appear in the app only inside `dev.jasper.app.plugins`.''',
     '''SDK types appear in the app only inside `dev.jasper.app.plugins`. `dev.jasper.app.presentation` holds the presentation a process runs with (the kit file parser, the attempt record, the projections); `PluginSelection` chooses it once on the bootstrap thread.''', 1)])
PY
```

- [ ] **Step 2: Prove the default look unchanged**

This plan changes no file under `jasper-ui`, so the headless gallery's pixels cannot move. Run:

```bash
git diff --stat main...HEAD -- jasper-ui
```

Expected: no output. (Three dots: `main` may have moved on with 3c, 3e or 4b, which change jasper-ui; only this branch's
own commits count.) (If an earlier step had to touch jasper-ui, run `./gradlew :jasper-ui:gallery
-Pjasper.galleryTheme=dark` and `=light` on this branch and on `main` in a scratch worktree, and compare every file
with `cmp`; record the result in the status banner.)

- [ ] **Step 3: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for report in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    root = ET.parse(report).getroot()
    tests += int(root.get("tests")); failures += int(root.get("failures")); errors += int(root.get("errors")); skipped += int(root.get("skipped"))
print(f"{tests:,} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Expected: 0 failures and 0 errors; note the four numbers for the entry below.

- [ ] **Step 4: Write the STATUS entry and the banner**

Insert at the top of `docs/STATUS.md`, right after the `# Jasper — Status and Handoff` heading and its blank line, one
paragraph (fill `<range>` with the Task 1 commit, `git log -1 --format=%h --fixed-strings --grep='Presentation provider: the descriptor keys and the capability'`, then `..` and `git log -1 --format=%h`; the plan commits before it are not part of the range, `<counts>` with Step 3's
line, and `<deviations>` with every deviation from this plan's text, or "none beyond the plan's Rulings"):

```markdown
**Enterprise presentation, plan 5a: the presentation provider — 2026-10-16** (`claude/enterprise-provider`, worktree
`.claude/worktrees/enterprise-5`, `<range>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-5a-provider.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). A plugin can provide Jasper's look from a
declarative kit file. `plugin.toml` gains `presentation`, `themes` and `enabled_by_default`, and `presentation.provide`
is a host capability (legal only with `presentation`). `PluginStateStore.effective` is the one effective-enabled rule: a
bundled plugin with `enabled_by_default = false` is off until the user turns it on, which writes `opt_in = true`; the
three prune paths keep the opt-in. `dev.jasper.app.presentation` holds `KitParser` (strict, one message naming the key;
`[branding]` stays an unknown table until plan 11), `FontFile` (a face's full name and typographic family without AWT),
`PresentationFacts` (digest and first problem), the attempt record (`presentation-attempt.toml`: pending, proven, failed,
announced), every spec 9.1 projection and `Presentation`. `PluginResolver.resolve` elects one provider (DEV, BUNDLED,
USER, then id; losers and the attempt skip post no problem; safe mode disables every provider), enabling or consenting
to one turns the others off in one transaction, and the Plugins page predicts with the same facts. `PluginSelection`
runs on the bootstrap thread before the `ConfigService`, which takes the kit's defaults for four keys (`DefaultSource`;
Settings shows and resets to the effective default); the application starts the theme under the selection's kit with
spec 4.7's four-step fallback, every `KitContext` carries the kit, and `PluginRuntime.start(selection, …)` scans
nothing again. The start-up line reads `look=<id>`. No plugin ships a presentation yet (plan 5b); the default look is
unchanged. Rulings (plan, "Rulings"): (1) row 5 is plans 5a and 5b; (2) the projections are complete but gated by
`Presentation.LANDED` (empty: plans 6 to 9 add their groups), and `DesktopFlags` is computed, not applied; (3) plan 1's
installed-kit chrome readers stay (the process installs one kit), the `KitContext` sites take the presentation's;
(4) `PresentationFacts.read` takes a `Source`; (5) descriptor path rule, existence checked when read; (6) safe mode's
presentation check comes first and dependents of a skipped provider are unmet; (7) attempt fields are optional; (8)
`enablingPresentation` takes the opt-in; (9) the application takes the selection at construction and
`startPlugins(AppDirs)` starts it; (10) core-look overloads stay for tests; (11) the bootstrap pre-pass is injectable;
(12) faces are named by the name table and registered in 5b; (13) deep nesting is invalid TOML; (14) colour-literal,
title and face rules; (15) the classic base's chrome is the Enterprise column; (16) a blamed kit posts the ordinary
problem; (17) the audited optional-value editors compare with the effective default through `Editors.effectiveDefault`; (18) `look=` in the trace's parenthesis; (19)
host facts without AWT; (20) `PluginRuntime.presentation` takes only the `Kit` (spec 4.9's `DialogMapping` parameter is
added beside it when the dialog consumer lands); (21) a failed attempt stays failed through a later window and an orderly
quit; (22) Try Again that cannot restart says to reopen Jasper. Deviations from the plan's text: <deviations>. Plan-5b entry conditions: register the fonts and
name the UI faces (`FaceSet`), the plugin theme tier and dormant ids, the Enterprise plugin. `./gradlew check
--continue`: **<counts>**. GUI checks for the user: none new (nothing visible changed). Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-16): executed on `claude/enterprise-provider` (`<range>`).** Deviations from this plan's text: <deviations>. From the spec: `PluginRuntime.presentation` takes only the `Kit`; spec 4.9's `DialogMapping` parameter is added beside it when the dialog consumer lands (Rulings 20).
```

- [ ] **Step 5: Commit**

```bash
git add docs AGENTS.md
git commit -F - <<'EOF'
Presentation provider: documentation and status

The plugin guide names the presentation and enabled_by_default keys and
presentation.provide; the SDK architecture describes the pre-pass, the
opt-in and the one active presentation; the app architecture lists the
presentation package; the diagnostics guide shows look=. STATUS records
plan 5a with its rulings and the full build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-16)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `279e23bea` (code identical to `main` at `867940b55`: plans 3a, 3b, 3d and 4a). Method: a dry run in a scratch
copy of the tree applied every step in order (each edit script with its stated counts, each created or appended file and
each ledger row); after every task, `javac` (JBR 25, the Gradle cache's jars, no Gradle) compiled jasper-app's main and
test sources with zero errors; after each Step 1 the compile failed with the symbols that task's Step 2 names. The plan's
new and touched test classes then ran headless through the JUnit 6.1.3 launcher at the end of every task (165 of 165 at
the end), and the wide suites (`plugins`, `pluginmanager`, `config`, `settingsui`, `startup`, `bootstrap`,
`presentation`, `architecture`, `application`, `appearance`, `platform`, `workspace.ThemeControllerTest`) failed exactly
the same tests as the unchanged tree (staged-plugin and jar-property tests that need Gradle's task inputs, nothing else).
`javadoc -Xdoclint:all` reports no error; `jdeps` shows exactly the new edges Tasks 3, 8 and 9 name and no cycle; the
source-hygiene scan is clean; every new ledger row has eight fields. Nothing under `jasper-ui` changes. None of the code
uses an API of plans 3c, 3e, 4b or 4c.

Changes made to this plan's text by the refresh: the header, Workspace and "Before you start" (no rebase; checks for 3a,
3b, 3d and 4a only; the `ROW_HEIGHT`/`FIELD_RADIUS` count is per name, not per line); "Plan 4's names" (checked, no
renames); facts 8, 11 and 17 and the Files line numbers of Tasks 3, 6, 9 and 13; Task 3 also edits
`StaticDefaultTest`'s class comment; Task 5's compound `var` declarations; Task 6's banner case starts the runtime and
its Files list names the bridge; the local `dev` renamed `devPlugins` in Tasks 9 and 11; Tasks 9 to 12 carry their
ledger rows as text; Task 9's note on `DEFAULT_KIT_READERS`; Task 12's Step 2 symbols; Task 13's diff, range and STATUS
item (17).

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 2, 5, 9 | `PluginDescriptor`: the 18-component constructor, `enabledByDefault()`, `declaresPresentation()`, `presentation()`, `themes()`, `themeIds()` | Consumers use the produced shapes; compiles after each task | 9 |
| 2 → 5 → 9 | `PluginResolver.java`: `admit` and `effective` (2), the five-argument `resolve` and the election (5), `without` (9) | Each later anchor is the earlier task's output; applied in order with its counts | 9 |
| 2 → 6 | `PluginStateStore`, `PluginAdmin`, `PluginCatalog`: `enabling(id, enabled, optIn)` and `effective` (2), then `enablingPresentation`, `consentingPresentation`, `Disk` with `Presentations` (6) | Agree | 9 |
| 2 → 11 | `LookFixtures`: `plugin`, `descriptor`, `provider`, `KIT` (2); `loadable` appended (11) | Agree | 9 |
| 3 → 5, 8, 9 | `KitParser.parse`, `Parsed`, `Faces`, `Files`, `Context`; `ChromeSpec` | Agree; `ChromeSpec` stays inside `presentation` | 9 |
| 3 → 7 | `DefaultSource` (`ALLOWED`, `CORE`, `value`) created in 3, threaded through the configuration in 7 | Agree | 9 |
| 3 → 8 → 9 | `StaticDefaultTest`: `KitParser` joins the readers and lifts 4a's `Kit.CLASSIC` pin (3), `Presentation` joins (8), `JasperApplication` leaves (9) | Agree; 4a added no reader to the set | 4, 9 |
| 3, 5, 8, 9, 10, 11, 12 | `docs/design/theme-engine/migration.csv`: append-only rows | Distinct paths, eight fields each; the coverage test passes at every task's end | 3 |
| 4 → 5, 9, 12 | `AttemptStore.Record`, `Attempt.blocks` (5); `PresentationAttempt` handle and `announce` (9, 12) | Agree | 9 |
| 5 → 6 | `PresentationSelectionTest` (6 appends two cases); `Presentations.read` and the five-argument `resolve` | Agree | 1, 9 |
| 5 → 6 → 9 → 10 → 12 | `PluginRuntime.java`: facts read in `start` (5, interim), `Snapshot`/`Launch` (6), `start(selection, …)` replacing 5's interim (9), `presentation(Kit)` (10), `failed` on load or start failure (12) | Agree; 9's anchors exist after 5 | 9 |
| 5 → 9 | `PluginStatus`: `SAFE_MODE_LOOK`, `INVALID`, `ATTEMPT_SKIP`, `lookActive`, `quiet` (5); `LOOK_FAILED` (9) | Agree | 9 |
| 6 → 9 | `PluginCatalog.Launch(…, build)` built by the runtime from the selection's build | Agree | 9 |
| 7 → 9 | `ConfigService(Path, boolean, DefaultSource)` used by the bootstrap | Agree | 9 |
| 8 → 9, 10, 11 | `Presentation` (`standard`, `of`, `fallback`, `look`), `Projections.Host` | Agree | 9 |
| 9 → 10 → 11 → 12 | `JasperApplication.java`: the selection constructor and `startPlugins(AppDirs)` (9), the kit to plugins (10), the installers constructor and `this.selection = this.selection.blamed()` (11), the attempt lifecycle (12) | Agree; 11's case guards 9's field/parameter split | 2, 9 |
| 9 → 12 | `ApplicationBootstrap.java`, `ApplicationBootstrapTest`, `PluginSelection.java` (`announceInvalid` added in 12) | Agree | 9 |
| 10 → 11 | `ThemeController` (kit constructor, then `start` and `Step`), `PresentationReachTest` (11 appends a case) | Agree | 2, 9 |
| 1, 9 → 13 | The keys and `presentation.provide` (1) and `look=` (9) as Task 13 documents them | Agree | 7, 8 |
| 7, 13 | `config.example.toml`, `docs/configuration.md` (7 only); 13 edits other documents | No overlap | none |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Files, Interfaces, tests, Step 2 symbols, commit list agree; 5 new `DescriptorParserTest` cases pass | none |
| 2 | Agree; 4 `PluginStateStoreOptInTest` cases pass | none |
| 3 | Agree after the class-comment edit; 3 + 10 + 2 cases as stated | 4 |
| 4 | Agree; 5 cases | none |
| 5 | Did not compile (compound `var`); fixed; 2 + 6 cases as stated | 1 |
| 6 | The banner case failed in teardown; fixed; the bridge needs no ledger row; the bridge added to Files | 2, 3 |
| 7 | Agree; 4 cases and the new `AppearanceSelectionTest` case pass | 10 |
| 8 | Agree; 6 cases; three ledger rows as stated | none |
| 9 | Did not compile (`dev` shadowed the package); fixed; ledger rows made explicit; `DEFAULT_KIT_READERS` note corrected | 1, 3 |
| 10 | Agree; ledger row made explicit; 2 cases | 3 |
| 11 | Did not compile (`dev` shadowed the package); fixed; ledger row made explicit; 5 + 3 cases | 1, 3 |
| 12 | Step 2 also reports `rehearsalPainted()`; ledger row made explicit; 8 cases | 3 |
| 13 | STATUS item (17) contradicted Rulings 17; `main..HEAD` and `git diff main` would count other plans' commits; doc line numbers moved; fixed | 5, 7, 8 |

### Rulings

1. **Compile fixes in test code.** Task 5's compound `var` declarations become `PluginCandidate …` (javac: "'var' is not
   allowed in a compound declaration"), and the locals named `dev` in Task 9's `PluginSelectionTest` and Task 11's
   `PresentationReachTest` case become `devPlugins`, because a local `dev` hides the package in `dev.jasper.ui.kit.Kit`
   and `dev.jasper.app.workspace.DesktopTestSupport`. Why: the tests did not compile as written. Cost if wrong: none; the
   meaning is unchanged.
2. **The banner case starts the runtime.** `PluginManagerTest`'s `@AfterEach` stops `runtime` and closes `windows`, so
   Task 6's `theRestartBannerNamesTheLooksTurnedOff` calls `start(false, false)` first instead of moving to a new class.
   Why: without it the case passes and its teardown throws a `NullPointerException`. Cost if wrong: milliseconds per run.
3. **Ledger rows are written out.** Tasks 9 to 12 add five named rows (`PluginSelection`, `PluginSelectionTest`,
   `PresentationReachTest`, `ThemeControllerFallbackTest`, `PresentationAttemptTest`) before their test runs; Task 6's
   bridge needs none. Why: the dry run's `ThemeMigrationCoverageTest` names exactly these, and the Global Constraints
   want each row in its file's commit. Cost if wrong: a disposition's wording.
4. **`StaticDefaultTest`'s class comment.** Task 3 also rewrites the sentence "nothing reads … Kit.CLASSIC". Why: after
   Task 3 `KitParser` reads it, and AGENTS.md documents only what the system does. Cost if wrong: none.
5. **Task 13 counts only this branch.** The jasper-ui check is `git diff --stat main...HEAD`, and the STATUS range starts
   at Task 1's commit. Why: `main` moves while 3c, 3e and 4b merge, and `main..HEAD` starts at the plan commits. Cost if
   wrong: a wrong range or a false pixel alarm.
6. **No rebase, no dependency on 3c, 3e, 4b or 4c.** The branch already holds `867940b55`; the pre-flight checks name
   3a, 3b, 3d and 4a only, and 4b and 4c are 5b's entry condition. Why: the dry run builds and passes without them, and
   the orchestrator forbids rebasing. Cost if wrong: none for 5a; 5b checks its own entry conditions.
7. **Line numbers.** Facts 8, 11 and 17 and the Files of Tasks 3, 6, 9 and 13 now give `867940b55`'s numbers; the other
   facts were checked and hold. Why: the Files numbers are guides, and every edit anchors on quoted text. Cost if wrong:
   none.
8. **STATUS item (17)** reads as Rulings 17 and Task 7's code do (the audited editors compare with the effective
   default through `Editors.effectiveDefault`). Why: the summary said the opposite of the plan's ruling. Cost if wrong:
   an inaccurate STATUS entry.
9. **Order is binding.** Every shared file above is edited in task order, and each later anchor is the earlier task's
   output, as the dry run shows. Execute the tasks in order, one commit each; a task run early stops at its anchor
   check by design. Cost if wrong: none when run in order.
10. **Shared files with the parallel plans are reconciled at merge, not here.** Task 7 edits `SchemaPage` (`edited`,
    `showValue`), `SettingsDraft`, `AppearanceSelection`, `SettingsDialog` and three editors; Task 6 edits the Plugins
    page's banner; Tasks 3 to 13 append to `migration.csv` and Task 13 prepends to `docs/STATUS.md`. Plan 3e changes
    `SchemaPage` (titled groups), settings labels and the Plugins page; 3c and 4b add ledger rows and STATUS entries.
    Keep this plan's anchors at `867940b55`; at merge keep both sides (5a's effective-default comparisons inside 3e's
    layout; all ledger rows; each STATUS paragraph). Cost if wrong: merge work only.
11. **Timing-sensitive tests.** `PresentationAttemptTest`, `PresentationReachTest` and the application tests wait on the
    EDT and on files (`DesktopTestSupport.until`); other plans' Gradle builds run at the same time, so one may flake once.
    Re-run such a test alone before treating it as a failure, and never run `./gradlew --stop`. Cost if wrong: one rerun.
