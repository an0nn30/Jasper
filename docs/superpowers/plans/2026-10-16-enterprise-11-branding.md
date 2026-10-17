# Enterprise presentation 11: branding Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A look with `[branding]` brands Jasper's start-up and windows: the kit file's `[branding]` table (product,
icons, splash art) is parsed and validated into a `Branding` value, the Enterprise plugin ships the reference icon set
and splash art, the look's icons become every window's icon list and the taskbar and Dock icon (in packaged macOS
launches too, from the moment the splash opens), and a start that will open a window shows the Boot Splash, drawn as the
reference frames give it, with real start-up steps and no motion, closed when the first window shows or start-up fails.

**Architecture:** `KitParser` reads `[branding]` with bounded jar reads and checks the files without decoding them
(PNG header sizes, and jasper-ui's receiving SVG rules through a new XML-only `EncodedArtwork.checkSvg`), so the
bootstrap pre-pass still touches no AWT; `Branding` holds the bytes and travels in `Presentation`. Once the desktop
properties and title-bar style are committed, the bootstrap hands the icons to `ApplicationIcon` and, on a start that
will open a window, decodes the splash's files off the EDT into a `SplashPainter` (new package `dev.jasper.app.splash`)
and posts its `Splash` window ahead of the composition. A JDK-only `StartupSteps` (in `dev.jasper.app.startup`) is the
step model: the bootstrap, `PluginRuntime` (one name per plugin before it loads) and `JasperApplication` (the first
window) report to it, and it paints each step at once because the event thread is busy starting Jasper.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and Java2D, jsvg 2.0.0 (inside jasper-ui only), ImageIO, tomlj
1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ 3.27.7, Python 3.11+ standard library for the edit scripts and
checks (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1 (decision 19), 3, 5.1's `[branding]` table, 5.9's branding rules, 9.1's `Branding` row, 10.9, 10.11, 16.1.4,
16.2, 18 and row 11 of 19.1). Reference mocks: [`docs/design/enterprise/reference/`](../../design/enterprise/reference/)
folders `09 App Icon` and `10 Boot Splash` (the frames are 1x, 720 × 440; `extracted/template.html` is the markup).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-11`, branch
`claude/enterprise-branding`, cut from `main` at `4dba6ae2a` (the plan was written at `deafed424` and refreshed on
2026-10-17; see "Execution notes (2026-10-17)" at the end). Run every command from the worktree root, never in
`~/projects/moray`. Plans 3a to 3e, 4a to 4c, 5a, 5b, 6a, 6b and 8a are on `main` (5a, 5b, 6a and 6b are required);
plans 7 and 9a run in parallel in other worktrees and 8b, 9b, 10a and 10b come later; none of them is required, and
the files this plan shares with them are reconciled when the orchestrator merges.
Line numbers below are from `4dba6ae2a`; every edit quotes the exact text it replaces, and
the edit scripts stop with a message when that text is not found the stated number of times. If a script stops,
re-find the quoted text (it moves, it does not change), adjust the anchor, and re-run; never edit around a missing
anchor. Names this plan takes from plans 5a and 5b are cited by task below.

## Before you start (refresh)

Do not rebase, merge or push (the orchestrator merges). Check that every plan this one builds on is on the branch:

```bash
grep -cE '^\*\*Enterprise presentation, plan (5a|5b|6a|6b):' docs/STATUS.md                 # 4
ls jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java \
   jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java \
   jasper-app/src/main/java/dev/jasper/app/presentation/PresentationFacts.java \
   jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java \
   jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java \
   jasper-app/src/test/java/dev/jasper/app/testsupport/LookFixtures.java \
   jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java \
   plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml                     # all exist
grep -c 'droppedDefaults' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java   # 5b: at least 1
grep -c '\[branding\]: unknown table' jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java  # 5a: 1
grep -c 'Branding' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java          # 0: this plan adds it
grep -c 'record Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log' \
   jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java                    # 5a: 1
grep -cE '^\*\*Enterprise presentation, plan (7|8a|8b|9a|9b|10a|10b):' docs/STATUS.md      # 1 (8a) at 4dba6ae2a
```

If one of the first eight checks fails, stop: this plan needs plans 5a, 5b, 6a and 6b on the branch. The last check
only reports which later plans are in: none of them is needed (plans 7 and 9a run in parallel and touch the dock and
Settings packages). Then, for each task, read the code its scripts anchor on (`git log --oneline 4dba6ae2a..HEAD --
<file>` shows what moved) before running them. Record any anchor or name you had to adapt in this plan's status banner
(Task 8).

**Names from plans 5a and 5b this plan uses** (adapt a code block if the merged code differs, keeping its behaviour):

| Name | Source |
|---|---|
| `KitParser.parse(byte[], KitParser.Context)`, `Context(String id, String directory, Files files, Map<String, Optional<JasperTheme>> themes)`, `Files.read(String path, int maxBytes)`, `Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped, Faces faces)`, `Invalid`, `path(String)`, `MAX_FONT_BYTES`, the private helpers `table`, `known`, `string`, `defaults` | 5a Task 3 |
| `KitParser.Faces(Optional<UiFaces> ui, Optional<DataFaces> data)`, `UiFaces(String family, FontFile regular, FontFile medium, FontFile semibold)`, `DataFaces(String family, FontFile regular, FontFile medium)`, `FontFile.bytes()` | 5a Task 3 |
| `PresentationFacts.read(PresentationFacts.Source)`, `Source(String id, String kitPath, KitParser.Files files, List<String> themePaths)`, `digest()`, `problem()`, `parsed()` | 5a Task 5 |
| `Presentation(Optional<Provider> provider, Kit kit, ChromeSpec chrome, boolean classicBase, KitParser.Faces faces, DefaultSource defaults, Projections projections, List<String> droppedDefaults)` with `standard(Projections.Host)`, `of(Provider, KitParser.Parsed, Projections.Host)`, `fallback(Projections.Host)`; `Presentation.Provider(String id, String name, String version, String digest)` | 5a Task 8, 5b Task 1 |
| `PluginSelection.of(AppDirs, Path codeLocation, String build, Path developmentDirectory, boolean safeMode)`, `none()`, `presentation()`, `options()`, `isNone()`, the private field `resolution` (`PluginResolver.Resolution.load()`) | 5a Task 9 |
| `PluginRuntime.start(PluginSelection, Map, boolean)`, its plugin load loop | 5a Task 9 |
| `ApplicationBootstrap.Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log, PluginSelection selection)`, `createApplication(…, PluginSelection selection)`, `prepareDesktop`'s `AppDirs dirs = startup.dirs();`, `startDesktop`'s `attemptBeforePresenting` lines | 5a Tasks 9 and 12 |
| `JasperApplication(ConfigService, ShellLauncher, CommandHistory, Path, Runnable, Path, Path, PluginSelection)`, `presentation()`, `startPlugins(AppDirs)`, `plugins.start(selection, …)` | 5a Task 9 |
| `LookFixtures.plugin(Path root, String id, String descriptor, Map<String, String> resources)`, `descriptor(id, name)`, `provider(kitPath)`, `KIT`; `KitParserTest.context(Map<String, byte[]>, Map<String, Optional<JasperTheme>>)`; `PresentationFactsTest.files(Map<String, byte[]>)`; `ProjectionsTest.MAC` | 5a Tasks 2, 3, 5, 8 |
| `EnterpriseResources.ROOT`, `RESOURCES`, `facts()`; `PresentationConformanceTest.KEYS`; the shipped `enterprise.kit.toml` without `[branding]` | 5b Tasks 3 and 4 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. Headless JUnit renders and `:jasper-ui:gallery` (headless) may be run. The splash window itself is a GUI check for the user (spec 16.2).
- Work only in the worktree above on `claude/enterprise-branding`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. No default-look painter changes: `SvgPreflight` is a move of `DecodedArtwork`'s private checks, `EncodedArtwork.raster` is new and read only by the splash, About's copyright line is the same string through a constant, and `ApplicationIcon.images()` returns Jasper's sets unless a branded look supplied icons. Every file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to `main`'s, and every reference pair and calibration suite stays green unchanged (Task 8 proves it). The only new images are this plan's renders under `jasper-app/build/enterprise-splash/`.
- `[branding]` is exactly spec 5.1's three keys: `product` (1 to 32 characters, not blank), `icons` (a folder holding `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`, `icon-128.png`, `icon-256.png` and `icon-1024.png`, each a PNG of its stated size and at most 512 KiB) and `splash_art` (an SVG of at most 256 KiB). A kit without the table is `Branding.NONE`: Jasper's icon and no splash. Every failure is one message naming the key, in `KitParser`'s style.
- The splash is spec 10.11 and the Boot Splash frames exactly: 720 × 440 with a 1 px #8A8F96 border over a vertical gradient from #17346F to #102552; the art across the top 250 px above a 1 px #8A8F96 rule; at content (40, 278) the 64 px icon, 16 px to its right the product name (UI face, 30 px semibold, #FFFFFF, tracking −0.4 px) above the version line 5 px below (data face, 12 px, #C7D6F2); 40 px from the sides and 30 px above the bottom the step (12 px, #C7D6F2, ellipsised) at the left and About's copyright line (11 px, #9FB3DA) at the right; a 6 px bar along the bottom (#0B1A3C track under a 1 px #2A4580 rule; fill gradient #5A8BE0, #2F65CA at 60 %, #1F4C9E). The same in Light and Dark; no fade, no easing.
- The steps are spec 10.11's: "Starting Java runtime…", "Reading configuration…", "Loading plugin: <name>" for each plugin as it starts, "Restoring workspace…" and "Starting <product> Workstation…"; the fill is the step's number over the step count. "Unlocking credential vault…" and "Indexing hosts…" are not steps (spec 18); the product name appears nowhere but the splash (spec 18).
- The splash shows only on a start that will open a window: not a handed-off launch, not `--background`, not `--no-window`, not `--safe-mode`, not headless. It closes when the first window shows or start-up fails. A splash that cannot be drawn or opened never stops start-up.
- Mock matching is a hard requirement (the user: "make sure our design system/kit matches the mocks perfectly when enabled"): Task 6's `BootSplashMockTest` compares the render with all ten frames at the tolerances stated there. A failing check is fixed by changing the drawing (the remedies listed in Task 6), never by loosening a tolerance; record any measured constant you change in the plan's status banner.
- `dev.jasper.app.presentation` stays free of Swing and AWT; it gains one edge, `dev.jasper.ui.icons` (`EncodedArtwork.checkSvg`, XML only). `dev.jasper.app.platform` keeps no outgoing Jasper dependency; `dev.jasper.app.startup` stays JDK-only. The new `dev.jasper.app.splash` depends on `dev.jasper.app.startup` and `dev.jasper.ui.icons` only. jsvg stays inside jasper-ui.
- Javadoc doclint fails the build on errors: jasper-app runs `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`. New public types carry a class comment, and record components an `@param`.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches gets its row in the same commit (each task lists its rows; the test names any still missing). A row has exactly eight comma-separated fields (`path,owner,surface,classification,migration_task,composition_task,existing_test,disposition`); a new row uses `nonvisual-helper` (or `custom-content` for a painter), `none` for both task columns, the file's own test as evidence and a disposition beginning "New in Enterprise plan 11 Task N" ("Test/fixture ownership; New in …" for a test); a disposition uses semicolons, never commas.
- `ColourLiteralScanTest` stays green: the splash's colours live only in `dev.jasper.app.splash.SplashColors`, which Task 6 exempts by path; no other production file gains a colour literal.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; the ellipsis, the middle dot and × in copy are ordinary characters.

## Review Focus

1. **Start-up fails after the splash was posted** (the first window's composition throws, or `prepareDesktop` fails
   after posting it): the splash closes, so no orphan window outlives the failed start, and a later step does nothing.
   Task 7: `ApplicationBootstrapTest.theStepsEndWhetherTheStartSucceedsOrFails` (the composition) and
   `aFailedStartClosesAPostedSplashOnceAndPostsNothingWithoutOne` (both catches, through `abandon`); Task 5:
   `StartupStepsTest.aFailingSplashEndsTheStepsWithoutStoppingStartUp`.
2. **A damaged install**: an icon whose PNG header is right but whose data is truncated, or a splash face that does not
   decode: start-up goes on; the icon is left out of the icon list (all of them broken means Jasper's icons), and the
   face draws in the platform face. Task 4: `ApplicationIconTest.aBrandedLooksIconsStandInForJaspersAndAFileThatDoesNotDecodeIsLeftOut`;
   Task 6: `SplashPainterTest.undrawableFilesAreRefusedAndAnUndecodableFaceFallsBack`.
3. **A look blamed at start** (spec 4.7, the kit fails to install): the fallback presentation has `Branding.NONE`, so
   the splash closes at once and windows take Jasper's icons again. Task 2:
   `KitBrandingTest.thePresentationCarriesTheBrandingAndTheFallbackDropsIt`; Task 5:
   `StartupStepsApplicationTest.aLookWithoutBrandingEndsTheStepsAtOnce`; Task 4 (`useLook(List.of())`).
4. **A plugin with a very long name**: its step is ellipsised before the copyright line, keeping the 24 px gap; it never
   overlaps it. Task 6: `SplashPainterTest.aLongStepIsEllipsisedBeforeTheCopyrightWithItsGap`.
5. **A `--background` resident start under Enterprise** that later opens a window from the Dock or a hand-off: no
   splash, but that window and the Dock carry the look's icons, also in a packaged macOS launch. Task 7:
   `ApplicationBootstrapTest.aBrandedLookGivesTheApplicationItsIconsEvenWithoutASplash`; Task 4:
   `ApplicationIconTest.aLooksIconIsSetOnTheTaskbarEvenInAPackagedMacLaunch`.

## Verified facts

At `4dba6ae2a` (re-checked 2026-10-17) unless marked.

1. `ApplicationIcon.images()` picks the macOS, Windows or Linux set (`ApplicationIcon.java:20-22`);
   `installTaskbarIcon` returns early for a packaged macOS launch (`usesBundleIcon`, `:36`, `:48-50`) and sets
   `images().getLast()` (`:41`); the sets are loaded lazily (`:68-79`). The file is unchanged since `deafed424`.
   `platform` has no outgoing Jasper dependency (`platform/package-info.java`).
2. Every window icon list reads `ApplicationIcon.images()` at construction: `TerminalWindow.java:76`,
   `NativeShells.java:302,412,623,672` (plan 6a's classic dialogs and frames are built there too; no other production
   `setIconImages` exists); About draws the largest image (`AboutView.java:190`); the Windows tray reads
   `images("windows")` explicitly (`ResidentTray.java:246`). `createApplication` calls `installTaskbarIcon` first
   (`ApplicationBootstrap.java:233`).
3. Plan 5a moved the pre-pass into `start`: `prepare` opens the log and runs `PluginSelection.of` on the bootstrap
   thread (`ApplicationBootstrap.java:66-77`), `start` reads the configuration before AWT (`:394-398`) and hands
   `Startup(service, options, dirs, log, selection)` (`:49-50`) to `prepareDesktop`; a handed-off launch returns before
   anything else (`:380`). `prepareDesktop` (`:78-111`) commits the desktop properties and the title-bar style through
   `applyDesktop` (`:100`; plan 6a, `:131-139`), prewarms the built-in icons (`:101-102`, the comment "Off the EDT and
   before the first window: an optional prewarm of the built-in icons.") and posts `startDesktop` (`:103`); its catch
   rolls back and logs (`:107-110`). `startDesktop` (`:165-224`) exits a non-resident `--background` start
   (`:175-180`), composes (`:181-218`), serves `--no-window` (`:196-199`), marks the attempt `pending` and opens the
   first window (`:200-207`) and catches start-up failures (`:219-224`). The `--background`, `--no-window` and
   safe-mode flags are `AppArguments.background()`, `noWindow()` and `safeMode()` (`AppArguments.java:7-8`).
   `attemptBeforePresenting` and its javadoc ("Whether the launch is about to show a window or rehearse one, …") sit at
   `:113-120`, just above `applyDesktop`.
4. Tests are headless: the root build sets `java.awt.headless=true` for every test task (`build.gradle.kts:31`); no
   test can create a `JWindow` or `JFrame`, and no test drives `prepareDesktop` (the bootstrap tests use the test entry
   `start(args, out, error, launch)`), so no existing test reaches the new splash code. Tests run sequentially in one
   JVM per module (no JUnit parallel configuration), so `ApplicationIcon`'s static look icons must be reset by every
   test that sets them.
5. `JasperApplication.composeWindow` (`JasperApplication.java:467`) adds, lays out and shows the window on one line
   (`:519`: `windows.add(window); window.restoreLayout(uiState); window.show();`) and marks `WINDOW_SHOWN` after it
   (`:531`, the only `        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);`); the constructor sets the presentation
   from the selection (`:292`) and replaces it with the blamed selection's fallback when the kit is blamed
   (`:316-317`); `presentation()` is at `:210`; `startPlugins(AppDirs)` builds the runtime (`:957`) and calls
   `plugins.start(selection, …)` (`:1001`, the only line starting with `plugins.start(selection,`).
6. `PluginRuntime`'s pre-start setters throw once started (`PluginRuntime.java:368-407`, the `glass` field at `:400`,
   `host` at `:418`); `Objects` and `Consumer` are imported (`:30`, `:41`); the start loop over `resolution.load()`
   loads then starts each candidate (`:692-713`), naming a candidate as `candidate.descriptor().name()`.
   `PluginSelection.isNone()` is at `PluginSelection.java:154` and the `resolution` field at `:43`.
7. `StartupTrace.mark` (`StartupTrace.java:158`) runs waiting actions on the marking thread; `phase(name, start)`
   (`:176`) accumulates a named phase. `dev.jasper.app.startup`'s package comment says "Every type is safe from any
   thread; nothing here blocks the EDT." and allows no other Jasper package.
8. `BuildInfo.versionLine()` is "1.2.0" or "1.2.0 + 14 commits" (`BuildInfo.java:112-116`) and `shortCommit()` the
   first eight characters (`:109`); `UNKNOWN`'s comment "A build nothing is known about." is at `:45`; About shows the
   version line (`AboutView.java:165`), the location "branch @ shortCommit" (`:93`) and the literal
   "Copyright 2026 Dustin McKenna-Watts" (`:172`, asserted by `AboutViewTest.java:71`).
9. `EncodedArtwork` refuses empty or larger than 256 KiB data (`EncodedArtwork.java:16`), decodes once (`:21-25`) and
   returns its data at `:27`; `DecodedArtwork` runs its private XML preflight (`DecodedArtwork.java:42`, `:111-144`: no
   doctype, processing instruction, script, foreign object or animation, no event or `base` attribute, fragment-only
   `href` and `url(…)`, no escaped or `@` CSS), then loads with jsvg denying every external resource (`:43-45`); neither
   file changed since `deafed424`. jsvg is jasper-ui's `implementation` dependency (`jasper-ui/build.gradle.kts:8`) and
   only `compileOnly` in jasper-app (`jasper-app/build.gradle.kts:20`). The Boot Splash's `extracted/art.svg` (15,536
   bytes, `viewBox="0 0 718 250"`) passes every preflight rule (checked with Python on 2026-10-16: elements `svg`,
   `metadata`, `manifest`, `defs`, `linearGradient`, `radialGradient`, `stop`, `rect`, `g`, `line`, `polyline`,
   `circle`; no `href`; every `url(…)` a fragment); its SHA-256 is unchanged (2026-10-17).
10. `ColourLiteralScanTest` scans every production Java source and text resource (not `.svg` or `.png`) under
    jasper-ui, jasper-app and plugins, and skips only `THEMES`: the built-in theme files and, since plan 5b, every
    in-repo plugin theme (`ColourLiteralScanTest.java:14-19`); the skip is `if (THEMES.contains(file)) continue;`
    (`:68`), and `STRING_HEX` is declared once (`:42`).
11. `ThemeMigrationCoverageTest.CANDIDATE` matches `import java.awt…`, `import javax.swing…`, `import dev.jasper.ui…`,
    `UIManager`, the word appearance in any case, `new J…` and `new …List` among others
    (`ThemeMigrationCoverageTest.java:17-18`); `BuildInfo.java`, `BuildInfoTest.java`, `Branding.java`,
    `BrandingTest.java`, `KitBrandingTest.java`, `StartupSteps.java` and `splash/package-info.java` match nothing and
    need no row; every modified file that matches (`ApplicationIconTest`, `ApplicationBootstrapTest`,
    `PresentationConformanceTest`, `ApplicationBootstrap`, `PluginSelection`, `PluginRuntime`, `PluginJars`) has its row.
12. `PluginJars.addResource(Path, String, String)` adds one text entry (`PluginJars.java:81-91`, indented eight spaces);
    `LookFixtures.plugin` writes text resources only and returns `<root>/<id>` (`LookFixtures.java:27-32`).
13. `tools/visual/pairs.json` is a list of 66 `{name, mock, crop, render, …}` entries written with two-space indentation
    (`json.dumps(…, indent=2, ensure_ascii=False)` plus a newline reproduces it byte for byte); `tools/visual/README.md:13-16`
    says how `compare_all.py` runs them, its bullet ending "the staged render evidence tests, `EnterpriseKitRenderTest`
    and the\n  workspace preview)." (plan 5b added `EnterpriseKitRenderTest`).
14. The reference icon files are byte-identical across the macOS iconset, the hicolor tree and the Windows PNG folder
    for each size they share (SHA-256, 2026-10-16), and every one is RGBA of its stated size (table under "Measured
    targets"). `09 App Icon/README.md` asks for 16 to 256 px in window icon lists and the 1024 px master for the Dock.
15. `extracted/template.html` lays the splash out as: a 720 × 440 `border-box` with a 1 px border, the art `img` 718 × 250
    in a 250 px box whose 1 px bottom border is the rule, the icon row at content (40, 278) with `gap:16px`, the text
    column `gap:5px` (name `line-height:1`), the bottom row at `left:40px; right:40px; bottom:30px` with
    `justify-content:space-between; gap:24px`, and the bar `height:6px; border-top:1px` at the bottom; its step list
    and the fade, scale and `width .35s ease-out` transitions are the animated mock's (not adopted, spec 10.11).

## Measured targets

Measured with Python and PIL on 2026-10-16 from `docs/design/enterprise/reference/10 Boot Splash/` (1x frames, so
pixel distances are not halved). Coordinates are window pixels: the 1 px border makes the mock's content position
(x, y) window pixel (x + 1, y + 1). Ink boxes are inclusive and use the thresholds named (mean of R, G and B above it).

| What | Target (window pixels) | How it was measured |
|---|---|---|
| Border | every pixel of rows 0 and 439 and columns 0 and 719 is #8A8F96 | exact in all ten frames |
| Content gradient | rows 1 to 438, #17346F at the top to #102552 at the bottom, evaluated at pixel centres (row 252 is #132B5D) | column 5, rows 252 to 431; over the five text-free regions of the test the mock differs from the gradient by a mean of 1.0 (0.85 in frame 10) and at most 3 |
| Art | x 1 to 718, y 1 to 250 (718 × 250) | the first non-gradient row after it is the rule |
| Rule under the art | row 251, x 1 to 718, #8A8F96 | exact |
| Icon | the 64 px PNG drawn at (41, 279); its ink box (48, 286)–(97, 337) | compositing `icon-64.png` over the gradient at (41, 279) matches with mean difference 2.6/2.3/3.1 (R/G/B); one pixel off in any direction gives 11 to 14 |
| Product name | text x 121, baseline 312; ink (122, 291)–(265, 317) at threshold 140 | "JasperOps"; ink bottom of the non-descending letters is row 311 |
| Version line | text x 121, baseline 333; ink (122, 323)–(271, 332) at threshold 100 | "1.0.0 · Build a3f9c21" |
| Step | text x 41, baseline 405; ink left 42, bottom 407 at threshold 100 | per frame below |
| Copyright line | right edge x 679, baseline 406; ink (568, 398)–(678, 407) at threshold 90 | "CopyRight Notice Here" |
| Gap between step and copyright | 24 px | template `gap:24px` |
| Bar rule | row 432, #2A4580 (the mock's palette gives #29457F to 3 levels off) | x 360, all frames |
| Track | rows 433 to 438, #0B1A3C (the mock within 1) | right of the fill |
| Fill | x 1 to `round(fraction × 718)`, rows 433 to 438; frame 10's rows read #5486DD, #4878D0, #3F71CD, #3066CA, #295CBA, #2251A7 (the gradient at pixel centres gives #5486DD, #487BD7, #3C71D1, #3066CB, #295CBA, #2251A7) | frames 01, 04, 05, 06, 08, 09 and 10 end exactly at `round(p × 718)` for the mock's 6, 36, 46, 56, 76, 88 and 100 %; 02, 03 and 07 caught the fill mid-transition at 96, 135 and 407 px |
| Frame noise | between frames, identical regions differ by a mean of 1.6 (art), 0.14 (gradient), 1.5 (icon row) and 1.9 (copyright), 99th percentile 7, 1, 12 and 15 | the frames are palette-quantised screenshots |

| Frame | Step text (fixture) | Fill fraction | Step ink box (threshold 100) |
|---|---|---|---|
| 01 | Starting Java runtime… | 0.06 | (42, 395)–(164, 407) |
| 02 | Reading configuration… | 96 / 718 | (42, 395)–(166, 407) |
| 03 | Unlocking credential vault… | 135 / 718 | (42, 395)–(189, 407) |
| 04 | Loading plugin: File Server | 0.36 | (42, 395)–(182, 407) |
| 05 | Loading plugin: Tunnels | 0.46 | (42, 395)–(166, 407) |
| 06 | Loading plugin: Credential Vault | 0.56 | (42, 395)–(210, 407) |
| 07 | Loading plugin: Run Dialog | 407 / 718 | (42, 395)–(182, 407) |
| 08 | Indexing hosts… | 0.76 | (42, 395)–(128, 407) |
| 09 | Restoring workspace… | 0.88 | (42, 396)–(161, 407) |
| 10 | Starting JasperOps Workstation… | 1.0 | (42, 396)–(220, 407) |

The icon files (`09 App Icon`, SHA-256 of each, all RGBA, each of its stated size):

| Shipped as | Taken from | Pixels | Bytes | SHA-256 |
|---|---|---|---|---|
| `icon-16.png` | `macOS/AppIcon.iconset/icon_16x16.png` | 16 × 16 | 661 | `6015bbb01ff649f3930a9d5e5c533fd57b66de175d38b7c9a3702da52012d466` |
| `icon-32.png` | `macOS/AppIcon.iconset/icon_32x32.png` | 32 × 32 | 1,407 | `1b1dc37d8716b1e13e9adcf9c25c9bc6923fcaa33251d2496ece5b685e5bd728` |
| `icon-48.png` | `Linux/hicolor/48x48/apps/jasper.png` | 48 × 48 | 2,252 | `2eb2a9b07b94face0457a2a42d3b537356d1da747be4d639cd267d858af05f61` |
| `icon-64.png` | `macOS/AppIcon.iconset/icon_32x32-2x.png` | 64 × 64 | 3,101 | `d232b1e0d07cadff8c54f45698efee1916559328c29b102b2d3d9a45a9953436` |
| `icon-128.png` | `macOS/AppIcon.iconset/icon_128x128.png` | 128 × 128 | 6,702 | `2e9b967d0515870ce1e88511a7070d38c86c95d000b128980ebd45e4dcfd15dc` |
| `icon-256.png` | `macOS/AppIcon.iconset/icon_256x256.png` | 256 × 256 | 17,905 | `4940e4fef8195b3210c47b6fdbe56e192c9a56322f58b98847aaf08566e4a643` |
| `icon-1024.png` | `macOS/AppIcon.iconset/icon_512x512-2x.png` | 1024 × 1024 | 165,216 | `fecc2f8b5eaeb9a9ab471d281aaee736a16bfe623e056c419752ff3a050a66d6` |
| `splash/art.svg` | `10 Boot Splash/extracted/art.svg` | 718 × 250 | 15,536 | `eda029835c14f871aabd9674eecfa39d5840a40e70278963cda61756b499d4c1` |

## Rulings

Decisions where the spec is silent or a literal reading cannot be built. Record each in `docs/STATUS.md` (Task 8).

1. **One plan.** Row 11 fits eight tasks, so it is not split. Cost if wrong: none.
2. **`Branding` holds bytes, checked without decoding.** The pre-pass touches no AWT (spec 4.8), so `KitParser` reads a
   PNG's size from its header chunk and checks the art with jasper-ui's receiving SVG rules on the XML alone; the
   bootstrap and `ApplicationIcon` decode after the desktop properties are committed. A file that passes and then
   does not decode degrades (Review Focus 2). Cost if wrong: a damaged file found one step later.
3. **`presentation` gains the edge `dev.jasper.ui.icons`.** Spec 9.2 lists `presentation`'s edges without it; the
   art's check must be the one the splash's decode applies, so jasper-ui exposes `EncodedArtwork.checkSvg` (XML only,
   the rules moved unchanged from `DecodedArtwork` into `SvgPreflight`) and `EncodedArtwork.raster`. Cost if wrong: one
   edge and two public methods.
4. **All three keys are required when `[branding]` is present**; the icon folder must hold exactly the seven sizes;
   the art is fitted to 718 × 250 by its view box, and its aspect is not validated (spec 5.9 lists only "an SVG of at
   most 256 KiB"). Cost if wrong: a stricter or looser file.
5. **The look's digest covers the branding files** after the fonts (the icons from the smallest, then the art), so a
   changed icon gets a fresh try (spec 4.6). Cost if wrong: one loop.
6. **`Branding` is a component of `Presentation`**, as `DefaultSource` is, not of `Projections`; the default look and
   the fallback have `Branding.NONE`. Cost if wrong: a field moves.
7. **The look's icons reach every branded launch**, not only a splashed one, so a window a `--background` resident
   opens later carries them. Window icon lists take all seven sizes (Jasper's own macOS and Linux lists include their
   1024 px image too; AWT picks the nearest); the Dock and taskbar take the largest. About's icon follows
   `ApplicationIcon.images()` and so shows the look's icon; the Windows notification-area icon and the macOS menu-bar
   glyph keep Jasper's. Cost if wrong: About's icon.
8. **A look that falls back at start** (spec 4.7) has `Branding.NONE` (spec 9.1): the splash closes at once, new
   windows take Jasper's icons and the Dock gets Jasper's largest image back. Cost if wrong: a rare mismatched icon.
9. **The splash opens after the desktop properties and the title-bar style are committed**, at the start of
   `prepareDesktop`'s desktop work, not inside `start` where `PluginSelection` runs: AWT must not start before them
   (spec 4.8). Its files are decoded on the bootstrap thread and its window posted to the EDT ahead of `startDesktop`;
   each step paints at once (through the window's own graphics) because the event thread is then busy composing. Cost if wrong: the
   splash appears a few milliseconds later.
10. **Step timing.** "Starting Java runtime…" is step 1 as the splash opens (the toolkit and the event thread start);
    "Reading configuration…" covers the application's construction, where the configuration, themes and keymap are
    applied (the file itself is read before AWT may start); each "Loading plugin: <name>" comes before that plugin
    loads, also for one that then fails to load; "Restoring workspace…" comes as the first window's composition begins
    and "Starting <product> Workstation…" just before it shows. The count is four plus the selected plugins; steps only
    move forward, and a call that would not advance is ignored. Cost if wrong: copy and order.
11. **The splash's faces are created from the kit's face files** (`Font.createFont`), not registered: the application
    registers them later (plan 5b). A missing face draws in the platform face (bold for the product name). Cost if
    wrong: none.
12. **The version line** is `BuildInfo.buildLine()`: the version line and "Build" with the eight-character short commit,
    as About shows them ("1.2.0 + 14 commits · Build 8ba67ed3"); either part is left out when unknown, and a build with
    neither reads "Development build". About's copyright line becomes one constant, `BuildInfo.COPYRIGHT`, that both
    show. Cost if wrong: copy.
13. **The splash's colours are fixed** (spec 10.11, the same in Light and Dark) and live in one class,
    `SplashColors`, which `ColourLiteralScanTest` exempts by path, as it exempts the theme files. Cost if wrong: one
    exemption.
14. **Geometry is in window pixels, measured from the frames**, and the baselines are fixed integers rather than
    computed from font metrics, because Java and Chrome compute a "normal" line height differently; the step is
    ellipsised to the room left of the copyright line minus 24 px; the product name is clipped at the copyright line's
    right edge (32 characters at 30 px fit in most cases). Cost if wrong: constants.
15. **The mock comparison uses tolerances**: the frames are palette-quantised (Measured targets), so flat colours of
    the render are exact and the mock's within 3, regions compare by mean difference, text by its ink box, and the fill
    by frame 10's rows; frames 02, 03 and 07 caught the fill mid-transition, so their fractions are the measured 96,
    135 and 407 px. The mock's data (version, copyright, steps, fill) are fixtures. Cost if wrong: retuned tolerances.
16. **The splash window** is a plain `java.awt.Window`, not a `JWindow`: a `JWindow`'s root pane calls `updateUI`,
    which initializes the platform look and feel (Aqua on macOS, Metal elsewhere) on the EDT before `ThemeController`
    installs FlatLaf, a second look and feel on every branded cold start's critical path. It paints through `paint` and,
    for each step, through `getGraphics()` and `Toolkit.sync()`, and it carries the look's icons (`setIconImages`, from
    `ApplicationIcon.images()` after `useLook`), so an X11 or Windows taskbar entry for it shows the look's icon (spec
    10.11, "from the splash on"). It is unfocusable, centred on the default screen device's bounds, with the system's
    window shadow; a failure to open it is logged and start-up goes on. Cost if wrong: placement, or a `JWindow` and
    its look-and-feel start-up cost back.
17. **The visual pairs are 1x** (`tools/visual/pairs.json`), because the frames are. Cost if wrong: none.
18. **The splash's text is compared with the frames on macOS only** (`BootSplashMockTest.everyFramesTextMatchesItsMock`,
    `@EnabledOnOs(OS.MAC)`), as plan 9a Rulings 17 rules for its mocks: the frames were drawn by Chrome on macOS, and
    Linux and Windows rasterize IBM Plex differently, so a 1 px ink drift there could be fixed only by a tolerance. The
    border, rules, gradient, art, icon, bar, fill and track are compared on every OS (`everyFrameMatchesItsMock`).
    Cost if wrong: a Linux- or Windows-only text drift is seen only in the GUI check.

## File structure

- `jasper-ui/src/main/java/dev/jasper/ui/icons/`: new `SvgPreflight.java` (the receiving SVG rules, moved);
  `DecodedArtwork.java` (calls it; `raster`); `EncodedArtwork.java` (`raster`, `checkSvg`).
- `jasper-app/src/main/java/dev/jasper/app/presentation/`: new `Branding.java`; `KitParser.java` (`[branding]`),
  `PresentationFacts.java` (digest), `Presentation.java` (component), `package-info.java` (edge).
- `plugins/enterprise/src/main/resources/presentation/`: `icons/icon-16.png` to `icon-1024.png`, `splash/art.svg`,
  `enterprise.kit.toml` (`[branding]`).
- `jasper-app/src/main/java/dev/jasper/app/platform/`: `ApplicationIcon.java` (look icons), `BuildInfo.java`
  (`buildLine`, `COPYRIGHT`); `about/AboutView.java` (the constant).
- `jasper-app/src/main/java/dev/jasper/app/startup/StartupSteps.java` (new); `plugins/PluginRuntime.java`
  (`starting`), `plugins/PluginSelection.java` (`loading`), `application/JasperApplication.java` (`startupSteps`).
- `jasper-app/src/main/java/dev/jasper/app/splash/` (new package): `package-info.java`, `SplashColors.java`,
  `SplashPainter.java`, `Splash.java`.
- `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` and `package-info.java`.
- Tests (new): `jasper-ui/.../icons/EncodedArtworkRasterTest`, `presentation/BrandingTest`, `presentation/KitBrandingTest`,
  `testsupport/BrandingFixtures`, `startup/StartupStepsTest`, `plugins/PluginStartingStepsTest`,
  `application/StartupStepsApplicationTest`, `splash/SplashPainterTest`, `splash/BootSplashMockTest`.

---

### Task 1: Artwork at a caller's size, and the SVG rules without drawing (jasper-ui)

jasper-ui owns jsvg, so the splash's art is drawn there: `EncodedArtwork.raster(width, height)` draws the artwork into
a new image of that size, and `EncodedArtwork.checkSvg(bytes)` applies the receiving rules to the XML alone, for the
kit parser, which must not touch AWT. The rules move unchanged from `DecodedArtwork` into `SvgPreflight`, the one copy.

**Files:**
- Create: `jasper-ui/src/main/java/dev/jasper/ui/icons/SvgPreflight.java`
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/icons/DecodedArtwork.java:11-16,20,42,111-144`, `EncodedArtwork.java:27`, `jasper-ui/build.gradle.kts:26`
- Test: `jasper-ui/src/test/java/dev/jasper/ui/icons/EncodedArtworkRasterTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces: `public BufferedImage EncodedArtwork.raster(int width, int height)` (1 to 8192 a side, a new ARGB image,
  throws `IllegalArgumentException`); `public static void EncodedArtwork.checkSvg(byte[] data)` (throws
  `IllegalArgumentException` whose message names the rule, for example "Active SVG denied", "Nonlocal resource
  denied", "Nonlocal paint resource denied", "Not SVG", "Artwork must contain 1..262144 bytes"); package-private
  `SvgPreflight.check(byte[]) throws Exception` and `DecodedArtwork.raster(int, int)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/icons/EncodedArtworkRasterTest.java`:

```java
package dev.jasper.ui.icons;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** Enterprise plan 11: artwork drawn at a caller's size, and the receiving SVG rules checked without drawing. */
class EncodedArtworkRasterTest {
    static final Path BOOT_ART = Path.of(System.getProperty("jasper.repoRoot"),
        "docs/design/enterprise/reference/10 Boot Splash/extracted/art.svg");

    static byte[] svg(String body) {
        return ("<svg xmlns='http://www.w3.org/2000/svg' width='20' height='10' viewBox='0 0 20 10'>" + body + "</svg>")
            .getBytes(StandardCharsets.UTF_8);
    }

    @Test void aVectorIsDrawnAtTheRequestedSizeAndAPngIsScaled() throws Exception {
        var vector = new EncodedArtwork(EncodedArtwork.Format.SVG,
            svg("<rect width='10' height='10' fill='#ff0000'/><rect x='10' width='10' height='10' fill='#0000ff'/>"));
        BufferedImage drawn = vector.raster(40, 20);
        assertThat(drawn.getWidth()).isEqualTo(40);
        assertThat(drawn.getHeight()).isEqualTo(20);
        assertThat(drawn.getRGB(5, 10)).isEqualTo(0xffff0000);
        assertThat(drawn.getRGB(35, 10)).isEqualTo(0xff0000ff);
        assertThat(vector.raster(40, 20)).as("a new image each time").isNotSameAs(drawn);
        var source = new BufferedImage(20, 10, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < 10; y++) for (int x = 0; x < 20; x++) source.setRGB(x, y, x < 10 ? 0xffff0000 : 0xff0000ff);
        var bytes = new ByteArrayOutputStream();
        ImageIO.write(source, "png", bytes);
        var scaled = new EncodedArtwork(EncodedArtwork.Format.PNG, bytes.toByteArray()).raster(40, 20);
        assertThat(scaled.getRGB(5, 10)).isEqualTo(0xffff0000);
        assertThat(scaled.getRGB(35, 10)).isEqualTo(0xff0000ff);
        assertThatThrownBy(() -> vector.raster(0, 10)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> vector.raster(8193, 10)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void theBootSplashArtPassesTheRulesAndDrawsOpaqueAtItsSize() throws Exception {
        byte[] art = Files.readAllBytes(BOOT_ART);
        EncodedArtwork.checkSvg(art);
        var image = new EncodedArtwork(EncodedArtwork.Format.SVG, art).raster(718, 250);
        for (int[] point : new int[][] {{0, 0}, {717, 0}, {0, 249}, {717, 249}, {359, 125}})
            assertThat(image.getRGB(point[0], point[1]) >>> 24).as("alpha at %s,%s", point[0], point[1]).isEqualTo(255);
    }

    @Test void checkSvgAppliesTheReceivingRulesWithoutDrawing() {
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<script>alert(1)</script>")))
            .isInstanceOf(IllegalArgumentException.class).hasMessage("Active SVG denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<image href='https://example.com/a.png' width='1' height='1'/>")))
            .hasMessage("Nonlocal resource denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(svg("<rect width='1' height='1' fill='url(https://example.com/#p)'/>")))
            .hasMessage("Nonlocal paint resource denied");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg("<html/>".getBytes(StandardCharsets.UTF_8))).hasMessage("Not SVG");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg("not xml at all".getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(new byte[256 * 1024 + 1])).hasMessage("Artwork must contain 1..262144 bytes");
        assertThatThrownBy(() -> EncodedArtwork.checkSvg(("<!DOCTYPE svg [<!ENTITY e 'x'>]>"
            + new String(svg("<rect width='1' height='1'/>"), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8)))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.icons.EncodedArtworkRasterTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `raster` and `checkSvg`.

- [ ] **Step 3: Move the rules into `SvgPreflight`**

Create `jasper-ui/src/main/java/dev/jasper/ui/icons/SvgPreflight.java` (the bodies are `DecodedArtwork`'s, unchanged):

```java
package dev.jasper.ui.icons;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.regex.Pattern;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Node;

/**
 * The receiving rules for SVG artwork, applied to the XML alone, without decoding, drawing or AWT: an {@code svg} root,
 * no doctype or external entities, no processing instruction, no script, foreign object or animation, no event or
 * {@code base} attribute, fragment-only {@code href} and {@code url(…)} references, and no escaped or {@code @} CSS.
 */
final class SvgPreflight {
    private static final Pattern URL = Pattern.compile("url\\s*\\(([^)]*)\\)", Pattern.CASE_INSENSITIVE);

    private SvgPreflight() { }

    /** Throws when {@code data} breaks a rule; the message names it. */
    static void check(byte[] data) throws Exception {
        var factory = DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
        factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        factory.setXIncludeAware(false);
        factory.setExpandEntityReferences(false);
        var builder = factory.newDocumentBuilder();
        builder.setErrorHandler(new org.xml.sax.helpers.DefaultHandler() {
            @Override public void fatalError(org.xml.sax.SAXParseException e) throws org.xml.sax.SAXException { throw e; }
        });
        var document = builder.parse(new ByteArrayInputStream(data));
        if (!"svg".equals(document.getDocumentElement().getLocalName())) throw new IOException("Not SVG");
        inspect(document, 0);
    }

    private static void inspect(Node node, int depth) throws IOException {
        if (depth > 128) throw new IOException("Artwork nesting exceeds limit");
        if (node.getNodeType() == Node.PROCESSING_INSTRUCTION_NODE) throw new IOException("Processing instruction denied");
        String name = node.getLocalName();
        if (name != null && java.util.Set.of("script", "foreignobject", "animate", "animatetransform", "set").contains(name.toLowerCase(Locale.ROOT)))
            throw new IOException("Active SVG denied");
        var attributes = node.getAttributes();
        if (attributes != null) for (int i = 0; i < attributes.getLength(); i++) {
            var attribute = attributes.item(i);
            String key = attribute.getLocalName();
            String value = attribute.getNodeValue().trim();
            if (key != null && (key.toLowerCase(Locale.ROOT).startsWith("on") || key.equals("base")))
                throw new IOException("Active SVG attribute denied");
            if ("href".equals(key) && !value.matches("#[A-Za-z_][A-Za-z0-9_.:-]*")) throw new IOException("Nonlocal resource denied");
            css(value);
        }
        if ("style".equals(name)) css(node.getTextContent());
        for (Node child = node.getFirstChild(); child != null; child = child.getNextSibling()) inspect(child, depth + 1);
    }

    private static void css(String value) throws IOException {
        if (value.indexOf('\\') >= 0 || value.indexOf('@') >= 0) throw new IOException("External or escaped CSS denied");
        var urls = URL.matcher(value);
        while (urls.find())
            if (!urls.group(1).trim().replace("\"", "").replace("'", "").matches("#[A-Za-z_][A-Za-z0-9_.:-]*"))
                throw new IOException("Nonlocal paint resource denied");
    }
}
```

- [ ] **Step 4: Call it from `DecodedArtwork`, add `raster`, and expose both on `EncodedArtwork`**

```bash
python3 -I - <<'PY'
"""Plan 11 Task 1: SvgPreflight is the one copy of the SVG rules; DecodedArtwork rasters on request."""
import pathlib
p = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/icons/DecodedArtwork.java")
text = p.read_text(encoding="utf-8")
def once(old, new):
    global text
    if text.count(old) != 1: raise SystemExit(f"DecodedArtwork: expected one {old!r}, found {text.count(old)}")
    text = text.replace(old, new)
once("                preflight(data);\n", "                SvgPreflight.check(data);\n")
once(r'''    private static final Pattern URL=Pattern.compile("url\\s*\\(([^)]*)\\)",Pattern.CASE_INSENSITIVE);
''', "")
for line in ["import java.util.Locale;\n", "import java.util.regex.Pattern;\n", "import javax.xml.XMLConstants;\n",
             "import javax.xml.parsers.DocumentBuilderFactory;\n", "import org.w3c.dom.Node;\n"]:
    once(line, "")
start = text.index("    private static void preflight(byte[] data)throws Exception {")
end = text.rindex("\n}")
if text.count("private static void css(String value)") != 1: raise SystemExit("DecodedArtwork: css moved; re-find the preflight block")
raster = '''    /** A new image of the artwork at {@code width} by {@code height}: the vector drawn to fit, or the PNG scaled; never cached. */
    BufferedImage raster(int width, int height) {
        if (width < 1 || height < 1 || width > 8192 || height > 8192)
            throw new IllegalArgumentException("A raster needs 1 to 8192 pixels a side, not " + width + " by " + height);
        var result = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        var g = result.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            if (vector != null) vector.render(null, g, new ViewBox(0, 0, width, height));
            else {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                g.drawImage(image, 0, 0, width, height, null);
            }
        } finally { g.dispose(); }
        return result;
    }'''
text = text[:start] + raster + text[end:]
p.write_text(text, encoding="utf-8")

e = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/icons/EncodedArtwork.java")
text = e.read_text(encoding="utf-8")
old = "    public byte[] data(){return data.clone();}\n"
if text.count(old) != 1: raise SystemExit("EncodedArtwork: data() moved")
text = text.replace(old, old + '''
    /**
     * The artwork drawn into a new {@code width} by {@code height} ARGB image, decoding it first if needed: an SVG is
     * fitted by its view box, a PNG scaled. For a caller that paints the same artwork at one size many times, such as a
     * splash; never cached.
     *
     * @param width the image's width in pixels, 1 to 8192
     * @param height the image's height in pixels, 1 to 8192
     * @return a new image
     * @throws IllegalArgumentException when the artwork is invalid or the size is out of range
     */
    public java.awt.image.BufferedImage raster(int width, int height) { return decoded().raster(width, height); }

    /**
     * Checks SVG bytes against the receiving rules without decoding or drawing them, so with no AWT: for a reader that
     * must refuse artwork before the desktop starts.
     *
     * @param data the SVG file
     * @throws IllegalArgumentException whose message names the rule the file breaks
     */
    public static void checkSvg(byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length == 0 || data.length > 256 * 1024) throw new IllegalArgumentException("Artwork must contain 1..262144 bytes");
        try { SvgPreflight.check(data); }
        catch (Exception refused) {
            String reason = refused.getMessage();
            throw new IllegalArgumentException(reason == null || reason.isBlank() ? refused.getClass().getSimpleName() : reason, refused);
        }
    }
''')
e.write_text(text, encoding="utf-8")

g = pathlib.Path("jasper-ui/build.gradle.kts")
text = g.read_text(encoding="utf-8")
old = '''    inputs.dir(rootProject.file("tools/themes/testdata/intellij/loose")).withPathSensitivity(PathSensitivity.RELATIVE)\n'''
if text.count(old) != 1: raise SystemExit("jasper-ui/build.gradle.kts: the loose fixtures input moved")
text = text.replace(old, old + '''    // EncodedArtworkRasterTest checks the Boot Splash art (Enterprise plan 11).
    inputs.file(rootProject.file("docs/design/enterprise/reference/10 Boot Splash/extracted/art.svg")).withPathSensitivity(PathSensitivity.RELATIVE)
''')
g.write_text(text, encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.icons.*' :jasper-ui:javadoc`
Expected: PASS: `EncodedArtworkRasterTest` (3) and the existing icon tests unchanged (`ReceivingArtworkTest`,
`BuiltInArtworkTest`, `IdentityArtworkTest`, `IconRegistryTest`), which prove the moved rules behave as before;
javadoc BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/icons jasper-ui/src/test/java/dev/jasper/ui/icons/EncodedArtworkRasterTest.java jasper-ui/build.gradle.kts
git commit -F - <<'EOF'
Branding: artwork at a caller's size and the SVG rules without drawing

EncodedArtwork.raster draws artwork into a new image of a given size (the
splash's art), and EncodedArtwork.checkSvg applies the receiving SVG
rules to the XML alone, with no AWT, for the kit parser. The rules move
unchanged from DecodedArtwork into SvgPreflight, their one copy.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The `[branding]` table and the `Branding` value

`KitParser` reads `[branding]` (spec 5.1 and 5.9) into `Branding`, a value of the files' bytes; the look's digest
covers them; `Presentation` carries the branding, and the default look and the fallback have `Branding.NONE`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/presentation/Branding.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/BrandingFixtures.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/KitParser.java`, `PresentationFacts.java`, `Presentation.java`, `package-info.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java:81-91`, `jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/BrandingTest.java`, `KitBrandingTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row: `BrandingFixtures`)

**Interfaces:**
- Consumes: Task 1's `EncodedArtwork.checkSvg`; plan 5a's `KitParser`, `PresentationFacts`, `Presentation` (table above).
- Produces: `public final class Branding` with `SIZES` (`List.of(16, 32, 48, 64, 128, 256, 1024)`), `NONE`,
  `static Branding of(String product, Map<Integer, byte[]> icons, byte[] splashArt)`, `boolean present()`,
  `String product()`, `byte[] icon(int size)`, `List<byte[]> iconFiles()` (smallest first), `byte[] splashArt()`,
  `List<byte[]> files()` (icons, then art); `KitParser.Parsed.branding()`; `KitParser.MAX_ICON_BYTES` (512 KiB),
  `MAX_ART_BYTES` (256 KiB); `Presentation.branding()`; test support `BrandingFixtures.TABLE`, `SIZES`,
  `png(int width, int height)`, `png(int width, int height, int argb)`, `svg()`, `files(String directory)`,
  `plugin(Path root, String id, String name)`; `PluginJars.addResource(Path jar, String name, byte[] bytes)`.

- [ ] **Step 1: Write the test support**

Add a binary overload to `PluginJars` (the text one delegates to it):

```bash
python3 -I - <<'PY'
import pathlib
p = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/testsupport/PluginJars.java")
text = p.read_text(encoding="utf-8")
edits = [
    ('''        public static void addResource(Path jar, String name, String text) throws IOException {
            Path rewritten''',
     '''        public static void addResource(Path jar, String name, String text) throws IOException {
            addResource(jar, name, text.getBytes(StandardCharsets.UTF_8));
        }

        /** As above, for a binary resource such as an icon. */
        public static void addResource(Path jar, String name, byte[] bytes) throws IOException {
            Path rewritten'''),
    ("out.write(text.getBytes(StandardCharsets.UTF_8)); out.closeEntry();", "out.write(bytes); out.closeEntry();"),
]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"PluginJars: expected one {old!r}")
    text = text.replace(old, new)
p.write_text(text, encoding="utf-8")
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/BrandingFixtures.java`:

```java
package dev.jasper.app.testsupport;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.imageio.ImageIO;

/** A branded look's files for tests (Enterprise plan 11): solid PNG icons of each size and a flat SVG art. */
public final class BrandingFixtures {
    /** Spec 5.1's [branding] table, with the folders {@link #files} writes. */
    public static final String TABLE = "[branding]\nproduct = \"JasperOps\"\nicons = \"icons\"\nsplash_art = \"splash/art.svg\"\n";
    /** The seven icon sizes. */
    public static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256, 1024);

    private BrandingFixtures() { }

    /** A solid blue PNG of the given size; the same bytes on every call. */
    public static byte[] png(int width, int height) { return png(width, height, 0xFF2F65CA); }

    /** A solid PNG of the given size and colour; the same bytes on every call. */
    public static byte[] png(int width, int height, int argb) {
        var image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < height; y++) for (int x = 0; x < width; x++) image.setRGB(x, y, argb);
        var bytes = new ByteArrayOutputStream();
        try { ImageIO.write(image, "png", bytes); }
        catch (IOException impossible) { throw new UncheckedIOException(impossible); }
        return bytes.toByteArray();
    }

    /** A 718 by 250 SVG of one flat colour. */
    public static byte[] svg() {
        return ("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"718\" height=\"250\" viewBox=\"0 0 718 250\">"
            + "<rect width=\"718\" height=\"250\" fill=\"#1D4596\"/></svg>").getBytes(StandardCharsets.UTF_8);
    }

    /** Every icon and the art under {@code directory} (empty or ending in a slash), by jar path. */
    public static Map<String, byte[]> files(String directory) {
        var files = new LinkedHashMap<String, byte[]>();
        for (int size : SIZES) files.put(directory + "icons/icon-" + size + ".png", png(size, size));
        files.put(directory + "splash/art.svg", svg());
        return files;
    }

    /** A DEV presentation provider at {@code <root>/<id>} whose default-base kit has {@link #TABLE} and its files. */
    public static Path plugin(Path root, String id, String name) throws IOException {
        Path folder = LookFixtures.plugin(root, id, LookFixtures.descriptor(id, name) + LookFixtures.provider("look.kit.toml"),
            Map.of("look.kit.toml", LookFixtures.KIT + TABLE));
        Path jar = folder.resolve("jars").resolve(id + ".jar");
        for (var file : new TreeMap<>(files("")).entrySet()) PluginJars.addResource(jar, file.getKey(), file.getValue());
        return folder;
    }
}
```

Add its ledger row at the end of `docs/design/theme-engine/migration.csv` (one line):

```
jasper-app/src/test/java/dev/jasper/app/testsupport/BrandingFixtures.java,app.testsupport,BrandingFixtures,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/presentation/KitBrandingTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 2; a branded look's generated PNG icons and SVG art
```

- [ ] **Step 2: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/presentation/BrandingTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.testsupport.BrandingFixtures;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.Test;

/** Spec 5.1, 9.1 and 10.11: a look's branding is its product name, seven icons and splash art, held as bytes. */
class BrandingTest {
    static Map<Integer, byte[]> icons() {
        var icons = new TreeMap<Integer, byte[]>();
        for (int size : Branding.SIZES) icons.put(size, BrandingFixtures.png(size, size));
        return icons;
    }

    @Test void noBrandingIsJaspersIconAndNoSplash() {
        assertThat(Branding.NONE.present()).isFalse();
        assertThat(Branding.NONE.product()).isEmpty();
        assertThat(Branding.NONE.iconFiles()).isEmpty();
        assertThat(Branding.NONE.files()).isEmpty();
        assertThat(Branding.NONE.splashArt()).isEmpty();
        assertThatThrownBy(() -> Branding.NONE.icon(16)).isInstanceOf(IllegalArgumentException.class);
        assertThat(Branding.NONE).hasToString("Branding.NONE");
    }

    @Test void aBrandingHoldsCopiesInSizeOrderAndComparesByContent() {
        var icons = icons();
        byte[] art = BrandingFixtures.svg();
        var branding = Branding.of("JasperOps", icons, art);
        assertThat(branding.present()).isTrue();
        assertThat(branding.product()).isEqualTo("JasperOps");
        assertThat(branding.iconFiles()).hasSize(7);
        assertThat(branding.iconFiles().getFirst()).isEqualTo(BrandingFixtures.png(16, 16));
        assertThat(branding.iconFiles().getLast()).isEqualTo(BrandingFixtures.png(1024, 1024));
        assertThat(branding.files()).hasSize(8);
        assertThat(branding.files().getLast()).isEqualTo(BrandingFixtures.svg());
        icons.get(16)[0] = 9;
        art[0] = 9;
        assertThat(branding.icon(16)).as("a copy of what it was given").isEqualTo(BrandingFixtures.png(16, 16));
        assertThat(branding.splashArt()).isEqualTo(BrandingFixtures.svg());
        branding.icon(16)[0] = 1;
        assertThat(branding.icon(16)).as("a copy each time").isEqualTo(BrandingFixtures.png(16, 16));
        var same = Branding.of("JasperOps", icons(), BrandingFixtures.svg());
        assertThat(branding).isEqualTo(same).hasSameHashCodeAs(same);
        assertThat(branding).isNotEqualTo(Branding.of("Other", icons(), BrandingFixtures.svg()));
        assertThat(branding.toString()).contains("JasperOps").doesNotContain("[B@");
    }

    @Test void aPartMissingIsRefused() {
        assertThatThrownBy(() -> Branding.of(" ", icons(), BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Branding.of("x".repeat(33), icons(), BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        var missing = icons();
        missing.remove(48);
        assertThatThrownBy(() -> Branding.of("JasperOps", missing, BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        var extra = icons();
        extra.put(512, BrandingFixtures.png(512, 512));
        assertThatThrownBy(() -> Branding.of("JasperOps", extra, BrandingFixtures.svg())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Branding.of("JasperOps", icons(), new byte[0])).isInstanceOf(IllegalArgumentException.class);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/presentation/KitBrandingTest.java`:

```java
package dev.jasper.app.presentation;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.testsupport.BrandingFixtures;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Spec 5.1 and 5.9: the [branding] table, every way it can be invalid, the digest and the presentation's branding. */
class KitBrandingTest {
    static final String HEAD = "kit = 1\nbase = \"default\"\n";

    /** The fixture branding under the kit's folder, "presentation/". */
    static Map<String, byte[]> files() { return new HashMap<>(BrandingFixtures.files("presentation/")); }

    static KitParser.Parsed parse(String text, Map<String, byte[]> files) throws KitParser.Invalid {
        return KitParser.parse(text.getBytes(StandardCharsets.UTF_8), KitParserTest.context(files, Map.of()));
    }

    static String problem(String text, Map<String, byte[]> files) {
        try { parse(text, files); throw new AssertionError("valid: " + text); }
        catch (KitParser.Invalid invalid) { return invalid.getMessage(); }
    }

    static Map<String, byte[]> with(String path, byte[] bytes) {
        var files = files();
        if (bytes == null) files.remove(path); else files.put(path, bytes);
        return files;
    }

    @Test void aKitWithoutBrandingKeepsJaspersIconAndShowsNoSplash() throws Exception {
        assertThat(parse(HEAD, files()).branding()).isSameAs(Branding.NONE);
    }

    @Test void theBrandingTableGivesTheProductTheIconsBySizeAndTheArt() throws Exception {
        var files = files();
        var branding = parse(HEAD + BrandingFixtures.TABLE, files).branding();
        assertThat(branding.present()).isTrue();
        assertThat(branding.product()).isEqualTo("JasperOps");
        for (int size : Branding.SIZES) assertThat(branding.icon(size)).isEqualTo(files.get("presentation/icons/icon-" + size + ".png"));
        assertThat(branding.splashArt()).isEqualTo(files.get("presentation/splash/art.svg"));
    }

    /** One invalid branding: the kit text, the jar's files and the one message it gives. */
    record Case(String kit, Map<String, byte[]> files, String message) { }

    @Test void everyInvalidBrandingGivesOneMessageNamingTheKey() {
        String table = BrandingFixtures.TABLE;
        String art = "presentation/splash/art.svg", icon48 = "presentation/icons/icon-48.png";
        var cases = List.of(
            new Case(HEAD + table + "logo = \"x\"\n", files(), "[branding].logo: unknown key"),
            new Case(HEAD + "branding = 1\n", files(), "branding: expected a table"),
            new Case(HEAD + "[branding]\nproduct = \"JasperOps\"\n", files(), "[branding].icons: missing"),
            new Case(HEAD + table.replace("product = \"JasperOps\"\n", ""), files(), "[branding].product: missing"),
            new Case(HEAD + table.replace("\"JasperOps\"", "7"), files(), "[branding].product: expected a string"),
            new Case(HEAD + table.replace("JasperOps", " "), files(), "[branding].product: expected a name of 1 to 32 characters"),
            new Case(HEAD + table.replace("JasperOps", "x".repeat(33)), files(), "[branding].product: expected a name of 1 to 32 characters"),
            new Case(HEAD + table.replace("icons = \"icons\"", "icons = \"../icons\""), files(),
                "[branding].icons: ../icons must be a lowercase path inside the kit's folder, without . or .. segments"),
            new Case(HEAD + table, with(icon48, null), "[branding].icons: icons/icon-48.png is not in the plugin's jars"),
            new Case(HEAD + table, with(icon48, BrandingFixtures.png(64, 64)), "[branding].icons: icons/icon-48.png is 64 × 64 pixels, not 48 × 48"),
            new Case(HEAD + table, with(icon48, "GIF89a not a png".getBytes(StandardCharsets.UTF_8)), "[branding].icons: icons/icon-48.png is not a PNG file"),
            new Case(HEAD + table, with("presentation/icons/icon-1024.png", new byte[KitParser.MAX_ICON_BYTES + 1]),
                "[branding].icons: icons/icon-1024.png is larger than 512 KiB"),
            new Case(HEAD + table.replace("splash/art.svg", "splash/art.png"), files(), "[branding].splash_art: splash/art.png is not an .svg file"),
            new Case(HEAD + table.replace("splash/art.svg", "splash/missing.svg"), files(),
                "[branding].splash_art: splash/missing.svg is not in the plugin's jars"),
            new Case(HEAD + table, with(art, new byte[KitParser.MAX_ART_BYTES + 1]), "[branding].splash_art: splash/art.svg is larger than 256 KiB"),
            new Case(HEAD + table, with(art, "<html/>".getBytes(StandardCharsets.UTF_8)),
                "[branding].splash_art: splash/art.svg is not an SVG Jasper can draw: Not SVG"),
            new Case(HEAD + table, with(art, "<svg xmlns=\"http://www.w3.org/2000/svg\"><script/></svg>".getBytes(StandardCharsets.UTF_8)),
                "[branding].splash_art: splash/art.svg is not an SVG Jasper can draw: Active SVG denied"));
        for (Case item : cases) assertThat(problem(item.kit(), item.files())).as(item.message()).isEqualTo(item.message());
    }

    /** Rulings 5: a changed branding file is a changed look, so it gets a fresh try. */
    @Test void theDigestCoversTheBrandingFilesAfterTheKit() throws Exception {
        var jar = files();
        byte[] kit = (HEAD + BrandingFixtures.TABLE).getBytes(StandardCharsets.UTF_8);
        jar.put("presentation/look.kit.toml", kit);
        var source = new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml", PresentationFactsTest.files(jar), List.of());
        var facts = PresentationFacts.read(source);
        assertThat(facts.problem()).isEmpty();
        var digest = MessageDigest.getInstance("SHA-256");
        digest.update(kit);
        for (int size : Branding.SIZES) digest.update(jar.get("presentation/icons/icon-" + size + ".png"));
        digest.update(jar.get("presentation/splash/art.svg"));
        assertThat(facts.digest()).isEqualTo(HexFormat.of().formatHex(digest.digest()));
        jar.put("presentation/icons/icon-16.png", BrandingFixtures.png(16, 16, 0xFF000000));
        var changed = PresentationFacts.read(new PresentationFacts.Source("dev.example.look", "presentation/look.kit.toml",
            PresentationFactsTest.files(jar), List.of()));
        assertThat(changed.digest()).as("a changed icon").isNotEqualTo(facts.digest());
    }

    /** Spec 9.1 and 4.7: the presentation carries its kit's branding; the default look and the fallback have none. */
    @Test void thePresentationCarriesTheBrandingAndTheFallbackDropsIt() throws Exception {
        var parsed = parse(HEAD + BrandingFixtures.TABLE, files());
        var look = Presentation.of(new Presentation.Provider("dev.example.look", "Example", "0.1.0", "d"), parsed, ProjectionsTest.MAC);
        assertThat(look.branding()).isEqualTo(parsed.branding());
        assertThat(look.fallback(ProjectionsTest.MAC).branding()).isSameAs(Branding.NONE);
        assertThat(Presentation.standard(ProjectionsTest.MAC).branding()).isSameAs(Branding.NONE);
    }
}
```

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.BrandingTest' --tests 'dev.jasper.app.presentation.KitBrandingTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class Branding" and "cannot find symbol: method branding()".

- [ ] **Step 4: Write `Branding`**

Create `jasper-app/src/main/java/dev/jasper/app/presentation/Branding.java`:

```java
package dev.jasper.app.presentation;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.stream.Stream;

/**
 * A look's branding (spec 5.1, 9.1 and 10.11): the product name its splash shows, its application icons by pixel size
 * and its splash art. {@link #NONE} is a look without {@code [branding]}, which keeps Jasper's icon and shows no splash;
 * the default look and the fallback presentation have it. Holds the files' bytes, which {@link KitParser} checks without
 * decoding them, so the bootstrap pre-pass touches no AWT; the splash and the application icon decode them. Immutable;
 * compared by content.
 */
public final class Branding {
    /** The icon sizes a branded look ships, in pixels, smallest first. */
    public static final List<Integer> SIZES = List.of(16, 32, 48, 64, 128, 256, 1024);
    /** No branding: Jasper's icon and no splash. */
    public static final Branding NONE = new Branding("", new TreeMap<>(), new byte[0]);

    private final String product;
    private final TreeMap<Integer, byte[]> icons;
    private final byte[] splashArt;

    private Branding(String product, TreeMap<Integer, byte[]> icons, byte[] splashArt) {
        this.product = product;
        this.icons = icons;
        this.splashArt = splashArt;
    }

    /**
     * A look's branding.
     *
     * @param product the name the splash shows, 1 to 32 characters
     * @param icons one PNG file for each of {@link #SIZES}
     * @param splashArt the splash art's SVG file
     * @return the branding, holding copies
     * @throws IllegalArgumentException when a part is missing
     */
    public static Branding of(String product, Map<Integer, byte[]> icons, byte[] splashArt) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(icons, "icons");
        Objects.requireNonNull(splashArt, "splashArt");
        if (product.isBlank() || product.codePointCount(0, product.length()) > 32)
            throw new IllegalArgumentException("A product name has 1 to 32 characters: " + product);
        if (!new TreeSet<>(icons.keySet()).equals(new TreeSet<>(SIZES)))
            throw new IllegalArgumentException("Branding needs one icon of each size " + SIZES + ", not " + new TreeSet<>(icons.keySet()));
        if (splashArt.length == 0) throw new IllegalArgumentException("Branding needs its splash art");
        var copies = new TreeMap<Integer, byte[]>();
        icons.forEach((size, file) -> copies.put(size, Objects.requireNonNull(file, "icon " + size).clone()));
        return new Branding(product, copies, splashArt.clone());
    }

    /** Whether the look is branded. */
    public boolean present() { return !product.isEmpty(); }

    /** The name the splash shows; empty for {@link #NONE}. */
    public String product() { return product; }

    /**
     * The PNG file of one icon size.
     *
     * @param size one of {@link #SIZES}
     * @return a copy of the file
     * @throws IllegalArgumentException for {@link #NONE} or a size the look does not ship
     */
    public byte[] icon(int size) {
        byte[] file = icons.get(size);
        if (file == null) throw new IllegalArgumentException("No " + size + " px icon" + (present() ? "" : ": the look has no branding"));
        return file.clone();
    }

    /** Every icon's PNG file, smallest first; empty for {@link #NONE}. */
    public List<byte[]> iconFiles() { return icons.values().stream().map(byte[]::clone).toList(); }

    /** The splash art's SVG file; empty for {@link #NONE}. */
    public byte[] splashArt() { return splashArt.clone(); }

    /** The files in the order the look's digest reads them: the icons smallest first, then the splash art. */
    public List<byte[]> files() {
        return present() ? Stream.concat(iconFiles().stream(), Stream.of(splashArt())).toList() : List.of();
    }

    @Override public boolean equals(Object other) {
        if (!(other instanceof Branding branding) || !product.equals(branding.product) || !Arrays.equals(splashArt, branding.splashArt)
            || !icons.keySet().equals(branding.icons.keySet())) return false;
        for (var icon : icons.entrySet()) if (!Arrays.equals(icon.getValue(), branding.icons.get(icon.getKey()))) return false;
        return true;
    }

    @Override public int hashCode() {
        int hash = Objects.hash(product, Arrays.hashCode(splashArt));
        for (var icon : icons.entrySet()) hash = 31 * hash + 31 * icon.getKey() + Arrays.hashCode(icon.getValue());
        return hash;
    }

    @Override public String toString() {
        return present() ? "Branding[" + product + ", icons " + icons.keySet() + ", art " + splashArt.length + " bytes]" : "Branding.NONE";
    }
}
```

- [ ] **Step 5: Parse `[branding]`, digest it and carry it**

```bash
python3 -I - <<'PY'
"""Plan 11 Task 2: KitParser reads [branding]; the digest covers it; Presentation carries it."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/presentation/"
edit(P + "KitParser.java", [
    ('''two colour slots and four setting defaults. Every''', '''two colour slots, four setting defaults and its branding. Every''', 1),
    ('''    public static final int MAX_FONT_BYTES = 2 * 1024 * 1024;\n''',
     '''    public static final int MAX_FONT_BYTES = 2 * 1024 * 1024;
    /** The largest branding icon: 512 KiB. */
    public static final int MAX_ICON_BYTES = 512 * 1024;
    /** The largest splash art: 256 KiB. */
    public static final int MAX_ART_BYTES = 256 * 1024;\n''', 1),
    ('''Set.of("kit", "base", "chrome", "controls", "metrics", "type", "colors", "defaults");''',
     '''Set.of("kit", "base", "chrome", "controls", "metrics", "type", "colors", "defaults", "branding");''', 1),
    ('''     * @param faces the declared face files
     */
    public record Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped, Faces faces) {
        /** Copies the list. */
        public Parsed { dropped = List.copyOf(dropped); }''',
     '''     * @param faces the declared face files
     * @param branding the look's branding; {@link Branding#NONE} without a {@code [branding]} table
     */
    public record Parsed(Kit kit, ChromeSpec chrome, boolean classicBase, DefaultSource defaults, List<String> dropped, Faces faces,
                         Branding branding) {
        /** Copies the list. */
        public Parsed { dropped = List.copyOf(dropped); Objects.requireNonNull(branding, "branding"); }''', 1),
    ('''        Defaults defaults = defaults(table(toml, "defaults"), context);\n''',
     '''        Defaults defaults = defaults(table(toml, "defaults"), context);
        Branding branding = branding(table(toml, "branding"), context);\n''', 1),
    ('''defaults.dropped(), type.faces());''', '''defaults.dropped(), type.faces(), branding);''', 1),
    ('''    private static Defaults defaults(TomlTable table, Context context) throws Invalid {''',
     '''    /** Spec 5.1 and 5.9: the product name, the seven icons and the splash art, all required when the table is present. */
    private static Branding branding(TomlTable table, Context context) throws Invalid {
        if (table == null) return Branding.NONE;
        known(table, "branding", List.of("product", "icons", "splash_art"));
        String product = string(table, "branding", "product");
        if (product.isBlank() || product.codePointCount(0, product.length()) > 32)
            throw new Invalid("[branding].product: expected a name of 1 to 32 characters");
        String folder = string(table, "branding", "icons");
        if (!path(folder))
            throw new Invalid("[branding].icons: " + folder + " must be a lowercase path inside the kit's folder, without . or .. segments");
        var icons = new LinkedHashMap<Integer, byte[]>();
        for (int size : Branding.SIZES) {
            String file = folder + "/icon-" + size + ".png";
            byte[] bytes = context.files().read(context.directory() + file, MAX_ICON_BYTES)
                .orElseThrow(() -> new Invalid("[branding].icons: " + file + " is not in the plugin's jars"));
            if (bytes.length > MAX_ICON_BYTES) throw new Invalid("[branding].icons: " + file + " is larger than 512 KiB");
            int[] pixels = pngSize(bytes);
            if (pixels.length == 0) throw new Invalid("[branding].icons: " + file + " is not a PNG file");
            if (pixels[0] != size || pixels[1] != size)
                throw new Invalid("[branding].icons: " + file + " is " + pixels[0] + " × " + pixels[1] + " pixels, not " + size + " × " + size);
            icons.put(size, bytes);
        }
        String art = string(table, "branding", "splash_art");
        String where = "[branding].splash_art: " + art;
        if (!path(art)) throw new Invalid(where + " must be a lowercase path inside the kit's folder, without . or .. segments");
        if (!art.endsWith(".svg")) throw new Invalid(where + " is not an .svg file");
        byte[] svg = context.files().read(context.directory() + art, MAX_ART_BYTES)
            .orElseThrow(() -> new Invalid(where + " is not in the plugin's jars"));
        if (svg.length > MAX_ART_BYTES) throw new Invalid(where + " is larger than 256 KiB");
        // The receiving rules the splash's decode applies, on the XML alone: the pre-pass touches no AWT (Rulings 2 and 3).
        try { dev.jasper.ui.icons.EncodedArtwork.checkSvg(svg); }
        catch (IllegalArgumentException refused) { throw new Invalid(where + " is not an SVG Jasper can draw: " + refused.getMessage()); }
        return Branding.of(product, icons, svg);
    }

    /** A PNG's width and height from its header chunk, or an empty array when the bytes are not a PNG. */
    private static int[] pngSize(byte[] bytes) {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', '\\r', '\\n', 0x1A, '\\n'};
        if (bytes.length < 24 || !Arrays.equals(Arrays.copyOf(bytes, 8), signature)) return new int[0];
        if (bytes[12] != 'I' || bytes[13] != 'H' || bytes[14] != 'D' || bytes[15] != 'R') return new int[0];
        var header = ByteBuffer.wrap(bytes);
        return new int[] {header.getInt(16), header.getInt(20)};
    }

    private static Defaults defaults(TomlTable table, Context context) throws Invalid {''', 1)])
edit(P + "PresentationFacts.java", [
    (''' * theme file, then each font file, in declared order; its first problem; and, when valid, its parsed kit, so the winner is''',
     ''' * theme file, then each font file, in declared order, then its branding files (the icons from the smallest, then the
 * splash art); its first problem; and, when valid, its parsed kit, so the winner is''', 1),
    ('''            for (FontFile font : parsed.faces().files()) digest.update(font.bytes());\n''',
     '''            for (FontFile font : parsed.faces().files()) digest.update(font.bytes());
            for (byte[] file : parsed.branding().files()) digest.update(file);\n''', 1)])
edit(P + "package-info.java", [
    (''' * dev.jasper.app.dock, dev.jasper.ui.kit, dev.jasper.ui.metrics, dev.jasper.ui.theme.''',
     ''' * dev.jasper.app.dock, dev.jasper.ui.kit, dev.jasper.ui.metrics, dev.jasper.ui.theme, dev.jasper.ui.icons (the
 * splash art's check, XML only).''', 1)])

# Presentation: one more component, set by each factory.
path = pathlib.Path(P + "Presentation.java"); text = path.read_text(encoding="utf-8")
text, n = re.subn(r"(public record Presentation\([^)]*?)\) \{", r"\1, Branding branding) {", text, count=1)
if n != 1: raise SystemExit("Presentation: the record header moved")
old = " */\npublic record Presentation("
if text.count(old) != 1: raise SystemExit("Presentation: the class comment's end moved")
text = text.replace(old, " * @param branding the look's branding (spec 10.11); {@link Branding#NONE} for the default look and the fallback\n" + old)
old = "    public Presentation {\n"
if text.count(old) != 1: raise SystemExit("Presentation: the compact constructor moved")
text = text.replace(old, old + "        Objects.requireNonNull(branding, \"branding\");\n")
def argument(text, signature, value):
    if text.count(signature) != 1: raise SystemExit(f"Presentation: expected one {signature!r}")
    start = text.index("return new Presentation(", text.index(signature))
    end = text.index(");", start)
    return text[:end] + ", " + value + text[end:]
text = argument(text, "public static Presentation standard(", "Branding.NONE")
text = argument(text, "public static Presentation of(", "parsed.branding()")
text = argument(text, "public Presentation fallback(", "Branding.NONE")
path.write_text(text, encoding="utf-8")

edit("jasper-app/src/test/java/dev/jasper/app/presentation/KitParserTest.java", [
    (r'''        cases.put(head + "[branding]\nproduct = \"JasperOps\"\n", "[branding]: unknown table");''',
     r'''        cases.put(head + "[branding]\nproduct = \"JasperOps\"\n", "[branding].icons: missing");''', 1),
    ('''(plan 11's)''', '''(KitBrandingTest covers it)''', 1)])
PY
```

Then find every other construction of the two records and give it the new last argument:

```bash
grep -rn "new Presentation(\|new KitParser.Parsed(\|new Parsed(" jasper-app/src --include='*.java'
```

Every hit outside `Presentation.java` and `KitParser.java` (a test that builds one directly) gains `, Branding.NONE`
(or `, dev.jasper.app.presentation.Branding.NONE` outside the package) as its last argument.

- [ ] **Step 6: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `BrandingTest` (3), `KitBrandingTest` (5), `KitParserTest` with its `[branding].icons: missing` case,
`PresentationFactsTest` (the plan-5a digest test is unchanged: its kit has no branding), `ProjectionsTest`,
`PresentationConformanceTest` (the shipped kit has no `[branding]` yet, so its branding is `NONE` and its key list
holds), the ledger test, and the architecture check with the new `presentation → dev.jasper.ui.icons` edge and no cycle.
If `ThemeMigrationCoverageTest` names another new file of this task, add its row in the Global Constraints' form.

- [ ] **Step 7: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation jasper-app/src/test/java/dev/jasper/app/presentation \
  jasper-app/src/test/java/dev/jasper/app/testsupport docs/design/theme-engine/migration.csv
git add -u jasper-app/src/test
git commit -F - <<'EOF'
Branding: the [branding] table and the Branding value

KitParser reads spec 5.1's [branding] table into Branding: the product
name, the seven icons by size and the splash art, as bytes. It checks
each icon's PNG header size and the art with the receiving SVG rules on
the XML alone, so the pre-pass still touches no AWT, and names the key
in every failure. The look's digest covers the branding files, and
Presentation carries the branding; the default look and the fallback
have Branding.NONE.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The Enterprise branding files

The Enterprise plugin ships the reference icon set and splash art the user supplied, byte for byte, and its kit file
gains spec 5.1's `[branding]` table. The conformance test pins the files to the reference copies and their sizes.

**Files:**
- Create: `plugins/enterprise/src/main/resources/presentation/icons/icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`, `icon-128.png`, `icon-256.png`, `icon-1024.png`; `plugins/enterprise/src/main/resources/presentation/splash/art.svg`
- Modify: `plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml`
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java` (`KEYS`, one test). `jasper-app/build.gradle.kts` needs no edit: its
  `tasks.test` already declares `docs/design/enterprise/reference` (all ten folders) and `plugins/enterprise/src/main/resources` as inputs (`:103`, `:108`).

**Interfaces:**
- Consumes: Task 2's `Branding`, `KitParser.Parsed.branding()`; plan 5b's `EnterpriseResources`, `PresentationConformanceTest.KEYS`.
- Produces: the shipped branding: product "JasperOps", icons at `presentation/icons/`, art at `presentation/splash/art.svg`.

- [ ] **Step 1: Write the failing test**

```bash
python3 -I - <<'PY'
import pathlib
p = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java")
text = p.read_text(encoding="utf-8")
edits = [
    ('''    /** Every key of spec 5.1's kit file except [branding], which plan 11 adds. */''', '''    /** Every key of spec 5.1's kit file. */'''),
    ('''"defaults/ui.theme.light", "defaults/ui.theme.dark", "defaults/palette.visible_rows", "defaults/window.toolbar_height");''',
     '''"defaults/ui.theme.light", "defaults/ui.theme.dark", "defaults/palette.visible_rows", "defaults/window.toolbar_height",
        "branding/product", "branding/icons", "branding/splash_art");'''),
    ('''    /** The staged jar carries the same kit: the host reads it with the same digest and no problem. */''',
     '''    /** Spec 5.1, 5.9 and 14.1: the branding is the reference artwork the user supplied, each icon a PNG of its size. */
    @Test void theBrandingIsTheReferenceArtwork() throws Exception {
        var branding = EnterpriseResources.facts().parsed().orElseThrow().branding();
        assertThat(branding.product()).isEqualTo("JasperOps");
        Path reference = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference");
        var sources = new java.util.LinkedHashMap<Integer, String>();
        sources.put(16, "09 App Icon/macOS/AppIcon.iconset/icon_16x16.png");
        sources.put(32, "09 App Icon/macOS/AppIcon.iconset/icon_32x32.png");
        sources.put(48, "09 App Icon/Linux/hicolor/48x48/apps/jasper.png");
        sources.put(64, "09 App Icon/macOS/AppIcon.iconset/icon_32x32-2x.png");
        sources.put(128, "09 App Icon/macOS/AppIcon.iconset/icon_128x128.png");
        sources.put(256, "09 App Icon/macOS/AppIcon.iconset/icon_256x256.png");
        sources.put(1024, "09 App Icon/macOS/AppIcon.iconset/icon_512x512-2x.png");
        assertThat(List.copyOf(sources.keySet())).isEqualTo(dev.jasper.app.presentation.Branding.SIZES);
        for (var source : sources.entrySet()) {
            int size = source.getKey();
            byte[] shipped = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/icons/icon-" + size + ".png"));
            assertThat(shipped).as("icon-%s.png is the reference file", size).isEqualTo(Files.readAllBytes(reference.resolve(source.getValue())));
            assertThat(branding.icon(size)).isEqualTo(shipped);
            assertThat(shipped.length).isLessThanOrEqualTo(512 * 1024);
            var image = javax.imageio.ImageIO.read(new ByteArrayInputStream(shipped));
            assertThat(image.getWidth()).as("icon-%s.png width", size).isEqualTo(size);
            assertThat(image.getHeight()).as("icon-%s.png height", size).isEqualTo(size);
            assertThat(image.getColorModel().hasAlpha()).as("icon-%s.png alpha", size).isTrue();
        }
        byte[] art = Files.readAllBytes(EnterpriseResources.RESOURCES.resolve("presentation/splash/art.svg"));
        assertThat(art).as("the splash art is the reference file").isEqualTo(Files.readAllBytes(reference.resolve("10 Boot Splash/extracted/art.svg")));
        assertThat(branding.splashArt()).isEqualTo(art);
        assertThat(art.length).isLessThanOrEqualTo(256 * 1024);
    }

    /** The staged jar carries the same kit: the host reads it with the same digest and no problem. */'''),
]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"PresentationConformanceTest: expected one {old!r} (plan 5b Task 4's text); re-find it")
    text = text.replace(old, new)
p.write_text(text, encoding="utf-8")
PY
```

(`List`, `Files`, `Path` and `ByteArrayInputStream` are imported by plan 5b's test.)

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PresentationConformanceTest'`
Expected: FAIL: `theKitListsEveryKeyAndParsesWithoutAProblem` (the three branding keys are missing) and
`theBrandingIsTheReferenceArtwork` (`NoSuchFileException` for `presentation/icons/icon-16.png`, or the product is empty).

- [ ] **Step 3: Copy the reference files and verify each size**

```bash
src="docs/design/enterprise/reference/09 App Icon"
dst=plugins/enterprise/src/main/resources/presentation
mkdir -p "$dst/icons" "$dst/splash"
cp "$src/macOS/AppIcon.iconset/icon_16x16.png" "$dst/icons/icon-16.png"
cp "$src/macOS/AppIcon.iconset/icon_32x32.png" "$dst/icons/icon-32.png"
cp "$src/Linux/hicolor/48x48/apps/jasper.png" "$dst/icons/icon-48.png"
cp "$src/macOS/AppIcon.iconset/icon_32x32-2x.png" "$dst/icons/icon-64.png"
cp "$src/macOS/AppIcon.iconset/icon_128x128.png" "$dst/icons/icon-128.png"
cp "$src/macOS/AppIcon.iconset/icon_256x256.png" "$dst/icons/icon-256.png"
cp "$src/macOS/AppIcon.iconset/icon_512x512-2x.png" "$dst/icons/icon-1024.png"
cp "docs/design/enterprise/reference/10 Boot Splash/extracted/art.svg" "$dst/splash/art.svg"
python3 -I - <<'PY'
import hashlib, pathlib, struct
root = pathlib.Path("plugins/enterprise/src/main/resources/presentation")
sums = {
    "icons/icon-16.png": "6015bbb01ff649f3930a9d5e5c533fd57b66de175d38b7c9a3702da52012d466",
    "icons/icon-32.png": "1b1dc37d8716b1e13e9adcf9c25c9bc6923fcaa33251d2496ece5b685e5bd728",
    "icons/icon-48.png": "2eb2a9b07b94face0457a2a42d3b537356d1da747be4d639cd267d858af05f61",
    "icons/icon-64.png": "d232b1e0d07cadff8c54f45698efee1916559328c29b102b2d3d9a45a9953436",
    "icons/icon-128.png": "2e9b967d0515870ce1e88511a7070d38c86c95d000b128980ebd45e4dcfd15dc",
    "icons/icon-256.png": "4940e4fef8195b3210c47b6fdbe56e192c9a56322f58b98847aaf08566e4a643",
    "icons/icon-1024.png": "fecc2f8b5eaeb9a9ab471d281aaee736a16bfe623e056c419752ff3a050a66d6",
    "splash/art.svg": "eda029835c14f871aabd9674eecfa39d5840a40e70278963cda61756b499d4c1",
}
for name, expected in sums.items():
    data = (root / name).read_bytes()
    if hashlib.sha256(data).hexdigest() != expected: raise SystemExit(f"{name}: not the reference file")
    if name.endswith(".png"):
        size = int(name.split("-")[1].split(".")[0])
        if data[:8] != b"\x89PNG\r\n\x1a\n" or data[12:16] != b"IHDR": raise SystemExit(f"{name}: not a PNG")
        width, height = struct.unpack(">II", data[16:24])
        color_type = data[25]
        if (width, height) != (size, size) or color_type != 6: raise SystemExit(f"{name}: {width} x {height}, colour type {color_type}")
        if len(data) > 512 * 1024: raise SystemExit(f"{name}: over 512 KiB")
        print(f"{name}: {width} x {height} RGBA, {len(data)} bytes")
    else:
        if len(data) > 256 * 1024: raise SystemExit(f"{name}: over 256 KiB")
        print(f"{name}: {len(data)} bytes")
PY
```

Expected: eight lines, each icon `N x N RGBA` with the byte counts of the Measured targets table, and
`splash/art.svg: 15536 bytes`; no `SystemExit`.

- [ ] **Step 4: Add `[branding]` to the shipped kit**

```bash
python3 -I - <<'PY'
import pathlib
p = pathlib.Path("plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml")
text = p.read_text(encoding="utf-8")
old = '''navigation_surface = "surface.card"\n\n[defaults]\n'''
if text.count(old) != 1: raise SystemExit("enterprise.kit.toml: the [colors] and [defaults] tables moved")
p.write_text(text.replace(old, '''navigation_surface = "surface.card"

[branding]
product = "JasperOps"
icons = "icons"
splash_art = "splash/art.svg"

[defaults]
'''), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.plugins.PresentationConformanceTest' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.*'`
Expected: PASS, including `theStagedPluginIsTheSourcesAndOffByDefault` (the staged jar now carries the branding files
and has the sources' digest), `EnterpriseFixtureTest` and `EnterpriseKitRenderTest` unchanged, and
`ColourLiteralScanTest` (the kit file has no colour; `.png` and `.svg` are not scanned).

- [ ] **Step 6: Commit**

```bash
git add plugins/enterprise/src/main/resources/presentation jasper-app/src/test/java/dev/jasper/app/plugins/PresentationConformanceTest.java
git commit -F - <<'EOF'
Branding: the Enterprise icons and splash art

The Enterprise plugin ships the reference icon set (16 to 1024 px, taken
from the macOS iconset and, for 48 px, the hicolor tree) and the Boot
Splash art byte for byte, and its kit file gains spec 5.1's [branding]
table. The conformance test pins each file to its reference copy and
each icon to its stated size.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The look's application icon, the version line and the copyright

`ApplicationIcon` takes a branded look's icons: they stand in for Jasper's in every new window's icon list, and the
largest becomes the taskbar and Dock icon, in packaged macOS launches too. `BuildInfo` gains the splash's version line
and the one copyright constant About and the splash share.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/ApplicationIcon.java`, `BuildInfo.java:45,118`, `jasper-app/src/main/java/dev/jasper/app/about/AboutView.java:172`
- Test: `jasper-app/src/test/java/dev/jasper/app/platform/ApplicationIconTest.java`, `BuildInfoTest.java`

**Interfaces:**
- Consumes: nothing new (`platform` keeps no Jasper dependency: the icons arrive as PNG bytes).
- Produces: `public static void ApplicationIcon.useLook(List<byte[]> files)`, `public static boolean ApplicationIcon.branded()`,
  package-private `static boolean setsTaskbarIcon(boolean macos, String launcherPath, boolean lookIcons)`;
  `public String BuildInfo.buildLine()`, `public static final String BuildInfo.COPYRIGHT`.

- [ ] **Step 1: Write the failing tests**

Append to `ApplicationIconTest` (before its final closing brace):

```java
    /** Spec 10.11: a branded look's icons stand in for Jasper's; one that does not decode is left out (Review Focus 2). */
    @Test void aBrandedLooksIconsStandInForJaspersAndAFileThatDoesNotDecodeIsLeftOut() throws Exception {
        byte[] truncated = {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1A, '\n', 0, 0, 0, 13, 'I', 'H', 'D', 'R', 0, 0, 0, 48, 0, 0, 0, 48};
        try {
            ApplicationIcon.useLook(java.util.List.of(png(16), truncated, png(32)));
            assertThat(ApplicationIcon.branded()).isTrue();
            assertThat(ApplicationIcon.images()).extracting(image -> image.getWidth(null)).containsExactly(16, 32);
            var first = ApplicationIcon.images();
            ApplicationIcon.useLook(java.util.List.of(png(16), truncated, png(32)));
            assertThat(ApplicationIcon.images()).as("the same files change nothing").isSameAs(first);
            ApplicationIcon.useLook(java.util.List.of(truncated));
            assertThat(ApplicationIcon.branded()).as("nothing decoded: Jasper's icons stay").isFalse();
            assertThat(ApplicationIcon.images()).extracting(image -> image.getWidth(null)).contains(16, 32, 256);
            ApplicationIcon.useLook(java.util.List.of(png(16)));
            ApplicationIcon.useLook(java.util.List.of());
            assertThat(ApplicationIcon.branded()).as("an empty list is Jasper's icons again").isFalse();
            ApplicationIcon.installTaskbarIcon(); // No native calls in headless test mode.
        } finally {
            ApplicationIcon.useLook(java.util.List.of());
        }
    }

    /** Spec 10.11: a look's icon reaches the Dock in a packaged macOS launch too, and Jasper's goes back over it. */
    @Test void aLooksIconIsSetOnTheTaskbarEvenInAPackagedMacLaunch() {
        String packaged = "/Applications/Jasper.app/Contents/MacOS/Jasper";
        assertThat(ApplicationIcon.setsTaskbarIcon(true, packaged, true)).isTrue();
        assertThat(ApplicationIcon.setsTaskbarIcon(true, packaged, false)).isFalse();
        assertThat(ApplicationIcon.setsTaskbarIcon(true, null, false)).isTrue();
        assertThat(ApplicationIcon.setsTaskbarIcon(false, "/opt/jasper/bin/Jasper", false)).isTrue();
    }

    private static byte[] png(int size) throws java.io.IOException {
        var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        var bytes = new java.io.ByteArrayOutputStream();
        ImageIO.write(image, "png", bytes);
        return bytes.toByteArray();
    }
```

Append to `BuildInfoTest` (before its final closing brace):

```java
    /** Spec 10.11: the splash's version line is the version and the short commit, as About shows them. */
    @Test void theSplashLineIsTheVersionAndTheShortCommitAsAboutShowsThem() {
        assertThat(BuildInfo.of(facts("headTags", "v1.2.0")).buildLine()).isEqualTo("1.2.0 · Build 8ba67ed3");
        assertThat(BuildInfo.of(facts("describe", "v1.2.0-14-g8ba67ed3")).buildLine()).isEqualTo("1.2.0 + 14 commits · Build 8ba67ed3");
        assertThat(BuildInfo.of(facts()).buildLine()).as("no version").isEqualTo("Build 8ba67ed3");
        assertThat(new BuildInfo(false, java.util.Optional.of("1.2.0"), 0, "", "", false, java.util.Optional.empty()).buildLine())
            .as("no commit").isEqualTo("1.2.0");
        assertThat(BuildInfo.UNKNOWN.buildLine()).isEqualTo("Development build");
        assertThat(BuildInfo.COPYRIGHT).isEqualTo("Copyright 2026 Dustin McKenna-Watts");
    }
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.ApplicationIconTest' --tests 'dev.jasper.app.platform.BuildInfoTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `useLook`, `branded`, `setsTaskbarIcon`, `buildLine`
and `COPYRIGHT`.

- [ ] **Step 3: Give `ApplicationIcon` the look's icons**

Replace `jasper-app/src/main/java/dev/jasper/app/platform/ApplicationIcon.java` with the file below (the file is
unchanged from `deafed424` to `4dba6ae2a`; if `git log --oneline 4dba6ae2a..HEAD --
jasper-app/src/main/java/dev/jasper/app/platform/ApplicationIcon.java` lists a commit, keep that commit's change in the
new text):

```java
package dev.jasper.app.platform;

import com.formdev.flatlaf.util.SystemInfo;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Taskbar;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.imageio.ImageIO;

/**
 * Native-sized PNG representations of the same artwork used by jpackage, or a branded look's own icons (spec 10.11),
 * which then stand in for Jasper's in every new window's icon list and on the taskbar and Dock.
 */
public final class ApplicationIcon {
    private static final System.Logger LOG = System.getLogger(ApplicationIcon.class.getName());

    /** A look's icons: its PNG files as given, and the images that decoded. */
    private record Look(List<byte[]> files, List<Image> images) { }

    private static volatile Look look = new Look(List.of(), List.of());
    /** Whether a look's icon was ever put on the taskbar, so that Jasper's goes back over it. */
    private static volatile boolean taskbarBranded;

    private ApplicationIcon() {}

    /** Returns a branded look's icons when it supplied them, else the icon sizes and framing of the current desktop. */
    public static List<Image> images() {
        List<Image> branded = look.images();
        if (!branded.isEmpty()) return branded;
        return images(SystemInfo.isMacOS ? "macos" : SystemInfo.isWindows ? "windows" : "linux");
    }

    static List<Image> images(String platform) {
        return switch (platform) {
            case "macos" -> Mac.IMAGES;
            case "windows" -> Windows.IMAGES;
            case "linux" -> Linux.IMAGES;
            default -> throw new IllegalArgumentException("Unknown icon platform: " + platform);
        };
    }

    /**
     * Makes a branded look's icons the application's (spec 10.11): its PNG files, smallest first, become every new
     * window's icon list and, through {@link #installTaskbarIcon()}, the taskbar and Dock icon, in packaged macOS
     * launches too. An empty list gives Jasper's own icons back. A file that does not decode is left out and logged; when
     * none decodes, Jasper's icons stay. Giving the current files again changes nothing. Any thread, before the windows
     * it should reach.
     *
     * @param files the look's icon files, smallest first
     */
    public static void useLook(List<byte[]> files) {
        Objects.requireNonNull(files, "files");
        if (same(files, look.files())) return;
        var images = new ArrayList<Image>(files.size());
        var copies = new ArrayList<byte[]>(files.size());
        for (byte[] file : files) {
            copies.add(file.clone());
            try {
                Image image = ImageIO.read(new ByteArrayInputStream(file));
                if (image != null) images.add(image);
                else LOG.log(System.Logger.Level.WARNING, "A look icon is not an image Jasper can read; it is left out");
            } catch (IOException | RuntimeException unreadable) {
                LOG.log(System.Logger.Level.WARNING, "A look icon could not be read; it is left out", unreadable);
            }
        }
        look = new Look(List.copyOf(copies), List.copyOf(images));
    }

    /** Whether a branded look's icons are the application's. */
    public static boolean branded() { return !look.images().isEmpty(); }

    /** Also gives development launches the correct Dock icon, and every launch a branded look's; call on the EDT. */
    public static void installTaskbarIcon() {
        boolean branded = branded();
        // Keep the system-rendered catalog icon throughout a packaged Mac launch, unless a look's icon is or was shown.
        if (!setsTaskbarIcon(SystemInfo.isMacOS, System.getProperty("jpackage.app-path"), branded || taskbarBranded)) return;
        if (GraphicsEnvironment.isHeadless() || !Taskbar.isTaskbarSupported()) return;
        try {
            Taskbar taskbar = Taskbar.getTaskbar();
            if (taskbar.isSupported(Taskbar.Feature.ICON_IMAGE)) {
                taskbar.setIconImage(images().getLast());
                if (branded) taskbarBranded = true;
            }
        } catch (UnsupportedOperationException | SecurityException failure) {
            LOG.log(System.Logger.Level.WARNING, "Desktop does not allow setting the application icon", failure);
        }
    }

    static boolean usesBundleIcon(boolean macos, String launcherPath) {
        return macos && launcherPath != null && !launcherPath.isBlank();
    }

    /**
     * Whether Jasper sets the taskbar icon: always for a look's icons (or to put Jasper's back over one), else unless a
     * packaged Mac launch keeps its bundle icon.
     */
    static boolean setsTaskbarIcon(boolean macos, String launcherPath, boolean lookIcons) {
        return lookIcons || !usesBundleIcon(macos, launcherPath);
    }

    private static boolean same(List<byte[]> files, List<byte[]> current) {
        if (files.size() != current.size()) return false;
        for (int i = 0; i < files.size(); i++) if (!Arrays.equals(files.get(i), current.get(i))) return false;
        return true;
    }

    private static List<Image> load(String platform, int... sizes) {
        var images = new ArrayList<Image>(sizes.length);
        for (int size : sizes) {
            String name = "/dev/jasper/app/icons/app/" + platform + "/icon-" + size + ".png";
            try (var input = ApplicationIcon.class.getResourceAsStream(name)) {
                if (input == null) throw new IllegalStateException("Missing application icon: " + name);
                var image = ImageIO.read(input);
                if (image == null) throw new IllegalStateException("Invalid application icon: " + name);
                images.add(image);
            } catch (IOException failure) {
                throw new UncheckedIOException("Cannot read application icon: " + name, failure);
            }
        }
        return List.copyOf(images);
    }

    private static final class Mac {
        static final List<Image> IMAGES = load("macos", 16, 32, 64, 128, 256, 512, 1024);
    }

    private static final class Windows {
        static final List<Image> IMAGES = load("windows", 16, 20, 24, 30, 32, 36, 40, 48,
            60, 64, 72, 80, 96, 128, 256);
    }

    private static final class Linux {
        static final List<Image> IMAGES = load("linux", 16, 24, 32, 48, 64, 96, 128, 256, 512, 1024);
    }
}
```

- [ ] **Step 4: Add the version line and the copyright constant**

```bash
python3 -I - <<'PY'
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/platform/BuildInfo.java", [
    ('''    /** A build nothing is known about. */''',
     '''    /** Jasper's copyright line, as About and a branded look's splash show it. */
    public static final String COPYRIGHT = "Copyright 2026 Dustin McKenna-Watts";

    /** A build nothing is known about. */''', 1),
    ('''    /** "main @ 8ba67ed3, modified"; the hash alone when detached; "commit unknown" when nothing is known. */''',
     '''    /**
     * A branded look's splash line (spec 10.11): the version line and the short commit as About shows them,
     * "1.2.0 + 14 commits · Build 8ba67ed3"; the version line alone when the commit is unknown, "Build 8ba67ed3" when no
     * version is known, and "Development build" when neither is.
     */
    public String buildLine() {
        String line = versionLine();
        if (!known()) return line.isEmpty() ? "Development build" : line;
        return (line.isEmpty() ? "" : line + " · ") + "Build " + shortCommit();
    }

    /** "main @ 8ba67ed3, modified"; the hash alone when detached; "commit unknown" when nothing is known. */''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/about/AboutView.java", [
    ('''facts.add(text(COPYRIGHT, "Copyright 2026 Dustin McKenna-Watts", Nodes.TextRole.CAPTION));''',
     '''facts.add(text(COPYRIGHT, BuildInfo.COPYRIGHT, Nodes.TextRole.CAPTION));''', 1)])
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.about.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `ApplicationIconTest` (two new cases and the existing ones, which keep reading Jasper's sets),
`BuildInfoTest` (one new case), `AboutViewTest` (the same copyright text), `ResidentTrayTest`.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/platform jasper-app/src/main/java/dev/jasper/app/about/AboutView.java \
  jasper-app/src/test/java/dev/jasper/app/platform/ApplicationIconTest.java jasper-app/src/test/java/dev/jasper/app/platform/BuildInfoTest.java
git commit -F - <<'EOF'
Branding: the look's application icon, the version line and the copyright

ApplicationIcon.useLook makes a branded look's icons the application's:
they stand in for Jasper's in every new window's icon list, and the
largest becomes the taskbar and Dock icon, in packaged macOS launches
too; an icon that does not decode is left out. BuildInfo.buildLine is
the splash's version line, and BuildInfo.COPYRIGHT the one copyright
line About and the splash show.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The start-up steps

`StartupSteps` (JDK-only, in `dev.jasper.app.startup`) is the step model of spec 10.11. The runtime names each selected
plugin just before it loads, the application reports the first window's composition and showing, and a presentation
without branding ends the steps at once.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/startup/StartupSteps.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/startup/package-info.java:2-4`, `jasper-app/src/main/java/dev/jasper/app/plugins/PluginRuntime.java:400,692`, `PluginSelection.java` (`loading`), `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (field, setter, `startPlugins`, `composeWindow`)
- Test: `jasper-app/src/test/java/dev/jasper/app/startup/StartupStepsTest.java`, `jasper-app/src/test/java/dev/jasper/app/plugins/PluginStartingStepsTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/StartupStepsApplicationTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (three rows)

**Interfaces:**
- Consumes: Task 2's `Presentation.branding()`, `BrandingFixtures.plugin`; plan 5a's `PluginSelection`, `PluginRuntime.start(selection, …)`, `JasperApplication(…, PluginSelection)`, `startPlugins(AppDirs)`.
- Produces: `public final class StartupSteps` with `RUNTIME`, `CONFIGURATION`, `PLUGIN` ("Loading plugin: "), `WORKSPACE`,
  `NONE`, `static StartupSteps of(String product, int plugins, Consumer<Step> show, Runnable close)`, `Step current()`,
  `boolean active()`, `configuration()`, `plugin(String name)`, `workspace()`, `workstation()`, `done()`; `public record
  StartupSteps.Step(String text, int index, int count)` with `double fraction()`; `public void
  PluginRuntime.starting(Consumer<String> plugin)`; `public List<String> PluginSelection.loading()`; `public void
  JasperApplication.startupSteps(StartupSteps steps)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/startup/StartupStepsTest.java`:

```java
package dev.jasper.app.startup;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

/** Spec 10.11: the splash's steps follow start-up as it happens; the fill is the step's number over the count. */
class StartupStepsTest {
    @Test void theStepsFollowStartUpAndFillIndexOverCount() {
        var shown = new ArrayList<StartupSteps.Step>();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", 2, shown::add, closed::incrementAndGet);
        assertThat(steps.current()).isEqualTo(new StartupSteps.Step("Starting Java runtime…", 1, 6));
        assertThat(steps.active()).isTrue();
        steps.configuration();
        steps.plugin("File Server");
        steps.plugin("Remote");
        steps.workspace();
        steps.workstation();
        assertThat(shown).containsExactly(new StartupSteps.Step("Reading configuration…", 2, 6),
            new StartupSteps.Step("Loading plugin: File Server", 3, 6), new StartupSteps.Step("Loading plugin: Remote", 4, 6),
            new StartupSteps.Step("Restoring workspace…", 5, 6), new StartupSteps.Step("Starting JasperOps Workstation…", 6, 6));
        assertThat(shown.getFirst().fraction()).isEqualTo(2 / 6.0);
        assertThat(shown.getLast().fraction()).isEqualTo(1.0);
        assertThat(closed).hasValue(0);
        steps.done();
        steps.done();
        assertThat(closed).as("closed once").hasValue(1);
        assertThat(steps.active()).isFalse();
        steps.plugin("Late");
        steps.workstation();
        assertThat(shown).as("nothing after done").hasSize(5);
    }

    @Test void stepsOnlyMoveForward() {
        var shown = new ArrayList<StartupSteps.Step>();
        var steps = StartupSteps.of("JasperOps", 1, shown::add, () -> { });
        steps.plugin("A");
        steps.plugin("B");
        assertThat(shown).as("a plugin beyond the count is ignored").containsExactly(new StartupSteps.Step("Loading plugin: A", 3, 5));
        steps.workspace();
        steps.configuration();
        steps.workstation();
        assertThat(shown).extracting(StartupSteps.Step::index).containsExactly(3, 4, 5);
        var none = StartupSteps.of("JasperOps", 0, shown::add, () -> { });
        none.workspace();
        assertThat(shown.getLast()).isEqualTo(new StartupSteps.Step("Restoring workspace…", 3, 4));
    }

    @Test void noStepsDoNothing() {
        assertThat(StartupSteps.NONE.active()).isFalse();
        StartupSteps.NONE.configuration();
        StartupSteps.NONE.plugin("A");
        StartupSteps.NONE.workspace();
        StartupSteps.NONE.workstation();
        StartupSteps.NONE.done();
        assertThat(StartupSteps.NONE.active()).isFalse();
    }

    /** Review Focus 1: a splash that cannot show a step closes, and start-up goes on. */
    @Test void aFailingSplashEndsTheStepsWithoutStoppingStartUp() {
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", 1, step -> { throw new IllegalStateException("cannot paint"); }, closed::incrementAndGet);
        steps.configuration();
        assertThat(steps.active()).isFalse();
        assertThat(closed).hasValue(1);
        steps.plugin("A");
        var throwing = StartupSteps.of("JasperOps", 0, step -> { }, () -> { throw new IllegalStateException("cannot close"); });
        throwing.done();
        assertThat(throwing.active()).isFalse();
    }

    @Test void aSplashNamesItsProductAndCountsPlugins() {
        assertThatThrownBy(() -> StartupSteps.of(" ", 0, step -> { }, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> StartupSteps.of("JasperOps", -1, step -> { }, () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartupSteps.Step("x", 0, 4)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new StartupSteps.Step("x", 5, 4)).isInstanceOf(IllegalArgumentException.class);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/plugins/PluginStartingStepsTest.java`:

```java
package dev.jasper.app.plugins;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.11: the runtime names each plugin of the selection just before it loads, for the splash's steps. */
class PluginStartingStepsTest {
    @TempDir Path root;

    @Test void theRuntimeNamesEachSelectedPluginBeforeItLoads() throws Exception {
        Path dev = LookFixtures.plugin(root.resolve("dev"), "dev.example.tool", LookFixtures.descriptor("dev.example.tool", "Example Tool"), Map.of());
        var dirs = new AppDirs(root.resolve("home"), root.resolve("home/config.toml"), root.resolve("home/logs"));
        var selection = PluginSelection.of(dirs, null, "", dev, false);
        assertThat(selection.loading()).containsExactly("Example Tool");
        var named = new ArrayList<String>();
        var runtime = new AtomicReference<PluginRuntime>();
        SwingUtilities.invokeAndWait(() -> {
            runtime.set(new PluginRuntime(selection.options(), new dev.jasper.app.notifications.ActivityNotifier(
                new dev.jasper.buddy.view.BuddyTestSupport().companion(), () -> { }), PluginRuntime.SettingsReports.IGNORED,
                new dev.jasper.app.contributions.Contributions(), AppContractTest.headlessWindows(), new dev.jasper.app.terminals.TerminalRegistry()));
            runtime.get().starting(named::add);
            runtime.get().start(selection, Map.of(), true);
            assertThatThrownBy(() -> runtime.get().starting(name -> { })).isInstanceOf(IllegalStateException.class);
        });
        assertThat(named).as("named before loading, although it then fails to load: it has no classes").containsExactly("Example Tool");
        var pending = new AtomicReference<List<CompletableFuture<?>>>();
        SwingUtilities.invokeAndWait(() -> pending.set(runtime.get().stop()));
        CompletableFuture.allOf(pending.get().toArray(CompletableFuture[]::new)).get();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/StartupStepsApplicationTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static dev.jasper.app.workspace.DesktopTestSupport.launcher;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.commands.CommandHistory;
import dev.jasper.app.platform.AppDirs;
import dev.jasper.app.plugins.PluginSelection;
import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.testsupport.BrandingFixtures;
import dev.jasper.app.testsupport.LookFixtures;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.11: the application reports each plugin to a branded look's splash; a look without branding ends the steps. */
class StartupStepsApplicationTest {
    @TempDir Path home;

    AppDirs dirs() { return new AppDirs(home, home.resolve("config.toml"), home.resolve("logs")); }

    @Test void aBrandedLookReportsEachPluginAsItStarts() throws Exception {
        var selection = PluginSelection.of(dirs(), null, "", BrandingFixtures.plugin(home.resolve("dev"), "dev.example.look", "Example"), false);
        assertThat(selection.presentation().branding().present()).isTrue();
        var shown = new ArrayList<StartupSteps.Step>();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", selection.loading().size(), shown::add, closed::incrementAndGet);
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startupSteps(steps);
                application[0].startPlugins(dirs());
            });
            assertThat(shown).containsExactly(new StartupSteps.Step("Loading plugin: Example", 3, 5));
            assertThat(closed).hasValue(0);
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            UIManager.setLookAndFeel(original);
        }
    }

    /** Review Focus 3: the presentation of a look that fell back at start has no branding, so its splash closes. */
    @Test void aLookWithoutBrandingEndsTheStepsAtOnce() throws Exception {
        Path dev = LookFixtures.plugin(home.resolve("dev"), "dev.example.look", LookFixtures.descriptor("dev.example.look", "Example")
            + LookFixtures.provider("look.kit.toml"), Map.of("look.kit.toml", LookFixtures.KIT));
        var selection = PluginSelection.of(dirs(), null, "", dev, false);
        assertThat(selection.presentation().branding().present()).isFalse();
        var closed = new AtomicInteger();
        var steps = StartupSteps.of("JasperOps", selection.loading().size(), step -> { }, closed::incrementAndGet);
        var original = UIManager.getLookAndFeel();
        JasperApplication[] application = new JasperApplication[1];
        try {
            edt(() -> {
                application[0] = new JasperApplication(null, launcher(new ArrayDeque<>()), new CommandHistory(), null, () -> { }, null, null, selection);
                application[0].startupSteps(steps);
            });
            assertThat(closed).hasValue(1);
            assertThat(steps.active()).isFalse();
        } finally {
            edt(() -> { if (application[0] != null) application[0].quitNow(); });
            UIManager.setLookAndFeel(original);
        }
    }
}
```

Add the three ledger rows at the end of `docs/design/theme-engine/migration.csv` (each one line):

```
jasper-app/src/test/java/dev/jasper/app/startup/StartupStepsTest.java,app.startup,StartupStepsTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/startup/StartupStepsTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 5; the splash's steps; matched only by collection construction
jasper-app/src/test/java/dev/jasper/app/plugins/PluginStartingStepsTest.java,app.plugins,PluginStartingStepsTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/plugins/PluginStartingStepsTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 5; the runtime names each plugin before it loads
jasper-app/src/test/java/dev/jasper/app/application/StartupStepsApplicationTest.java,app.application,StartupStepsApplicationTest,nonvisual-helper,none,none,jasper-app/src/test/java/dev/jasper/app/application/StartupStepsApplicationTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 5; the application reports plugins to a branded splash
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.StartupStepsTest' --tests 'dev.jasper.app.plugins.PluginStartingStepsTest' --tests 'dev.jasper.app.application.StartupStepsApplicationTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `StartupSteps`, `loading`, `starting` and `startupSteps`.

- [ ] **Step 3: Write `StartupSteps`**

Create `jasper-app/src/main/java/dev/jasper/app/startup/StartupSteps.java`:

```java
package dev.jasper.app.startup;

import java.util.Objects;
import java.util.function.Consumer;

/**
 * The steps a branded look's splash shows while Jasper starts (spec 10.11), as start-up reaches them:
 * {@value #RUNTIME} as the splash opens, {@value #CONFIGURATION} while the application is made, "Loading plugin: "
 * with each selected plugin's name just before it loads, {@value #WORKSPACE} as the first window is composed, and
 * "Starting" with the product name and "Workstation…" just before it shows. The count, four plus the selected plugins,
 * is known once the plugins are selected, and step i of n fills i / n. Steps only move forward: a call that would not
 * advance is ignored, and after {@link #done()} nothing shows. A splash that cannot show a step closes, and start-up
 * goes on. EDT.
 */
public final class StartupSteps {
    private static final System.Logger LOG = System.getLogger(StartupSteps.class.getName());

    /** The first step, shown as the splash opens. */
    public static final String RUNTIME = "Starting Java runtime…";
    /** While the application applies its configuration, themes and keymap. */
    public static final String CONFIGURATION = "Reading configuration…";
    /** Before each plugin loads, followed by its name. */
    public static final String PLUGIN = "Loading plugin: ";
    /** While the first window is composed. */
    public static final String WORKSPACE = "Restoring workspace…";

    /**
     * One step.
     *
     * @param text what the splash says
     * @param index the step's number, from 1
     * @param count how many steps this start has
     */
    public record Step(String text, int index, int count) {
        /** The text is required and the number within the count. */
        public Step {
            Objects.requireNonNull(text, "text");
            if (count < 1 || index < 1 || index > count) throw new IllegalArgumentException("Step " + index + " of " + count);
        }

        /** How far the bar is filled: the step's number over the count. */
        public double fraction() { return (double) index / count; }
    }

    /** No splash: every call does nothing. */
    public static final StartupSteps NONE = new StartupSteps("Jasper", 0, step -> { }, () -> { }, true);

    private final String product;
    private final int plugins;
    private final Consumer<Step> show;
    private final Runnable close;
    private Step current;
    private int loaded;
    private boolean done;

    private StartupSteps(String product, int plugins, Consumer<Step> show, Runnable close, boolean done) {
        this.product = product;
        this.plugins = plugins;
        this.show = show;
        this.close = close;
        this.done = done;
        current = new Step(RUNTIME, 1, count());
    }

    /**
     * The steps of a start that shows a splash, on the first one.
     *
     * @param product the look's product name, as the last step says it
     * @param plugins how many plugins the selection starts
     * @param show shows each step after the first; EDT
     * @param close closes the splash, once; EDT
     * @return the steps
     */
    public static StartupSteps of(String product, int plugins, Consumer<Step> show, Runnable close) {
        Objects.requireNonNull(product, "product");
        Objects.requireNonNull(show, "show");
        Objects.requireNonNull(close, "close");
        if (product.isBlank()) throw new IllegalArgumentException("A splash names its product");
        if (plugins < 0) throw new IllegalArgumentException("Negative plugin count: " + plugins);
        return new StartupSteps(product, plugins, show, close, false);
    }

    /** The step the splash shows now: the first until start-up reaches another. */
    public Step current() { return current; }

    /** Whether the splash is still showing these steps. */
    public boolean active() { return !done; }

    /** The application is being made. */
    public void configuration() { advance(2, CONFIGURATION); }

    /**
     * A selected plugin is about to load.
     *
     * @param name the plugin's name
     */
    public void plugin(String name) {
        Objects.requireNonNull(name, "name");
        if (done || loaded >= plugins) return;
        loaded++;
        advance(2 + loaded, PLUGIN + name);
    }

    /** The first window is being composed. */
    public void workspace() { advance(count() - 1, WORKSPACE); }

    /** The first window is about to show. */
    public void workstation() { advance(count(), "Starting " + product + " Workstation…"); }

    /** Start-up showed its first window or failed: the splash closes, once, and later calls do nothing. */
    public void done() {
        if (done) return;
        done = true;
        try { close.run(); }
        catch (RuntimeException failure) { LOG.log(System.Logger.Level.WARNING, "The splash could not be closed", failure); }
    }

    private int count() { return 4 + plugins; }

    private void advance(int index, String text) {
        if (done || index <= current.index()) return;
        current = new Step(text, index, count());
        try { show.accept(current); }
        catch (RuntimeException failure) {
            // A splash never stops start-up: one that cannot show a step closes.
            LOG.log(System.Logger.Level.WARNING, "The splash could not show a step; closing it", failure);
            done();
        }
    }
}
```

The package comment says every type is safe from any thread; `StartupSteps` is EDT-only, so qualify it (the smallest
accurate edit):

```bash
python3 -I - <<'PY'
import pathlib
p = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/startup/package-info.java")
text = p.read_text(encoding="utf-8")
old = (" * durations, per-window traces, one log line once start-up settles and an optional JSON file for the start-up\n"
       " * benchmark. Every type is safe from any thread; nothing here blocks the EDT.\n")
if text.count(old) != 1: raise SystemExit("startup/package-info.java: the thread sentence moved")
p.write_text(text.replace(old,
    " * durations, per-window traces, one log line once start-up settles and an optional JSON file for the start-up\n"
    " * benchmark, and the steps a branded look's splash shows. Every type except StartupSteps (EDT only) is safe from any\n"
    " * thread; nothing here blocks the EDT.\n"), encoding="utf-8")
PY
```

- [ ] **Step 4: Report from the runtime and the application**

```bash
python3 -I - <<'PY'
"""Plan 11 Task 5: the runtime names each plugin before it loads; the application reports the first window."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/plugins/"
edit(P + "PluginRuntime.java", [
    ('''    private GlassSource glass = GlassSource.NONE;\n''',
     '''    private Consumer<String> starting = plugin -> { };

    /**
     * Hears each selected plugin's name just before it loads, in start order: the splash's steps (spec 10.11). Before
     * plugin startup.
     *
     * @param plugin receives the plugin's name
     */
    public void starting(Consumer<String> plugin) {
        if (host != null) throw new IllegalStateException("Plugins have already started");
        starting = Objects.requireNonNull(plugin, "plugin");
    }

    private GlassSource glass = GlassSource.NONE;\n''', 1),
    ('''        for (PluginCandidate candidate : resolution.load()) {
            PluginLoader.Loaded unit;\n''',
     '''        for (PluginCandidate candidate : resolution.load()) {
            starting.accept(candidate.descriptor().name());
            PluginLoader.Loaded unit;\n''', 1)])
edit(P + "PluginSelection.java", [
    ('''    public boolean isNone() { return options == null; }\n''',
     '''    public boolean isNone() { return options == null; }

    /** The names of the plugins this selection starts, in start order: the splash's plugin steps (spec 10.11). */
    public List<String> loading() { return resolution.load().stream().map(candidate -> candidate.descriptor().name()).toList(); }\n''', 1)])

A = "jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"
path = pathlib.Path(A); text = path.read_text(encoding="utf-8")
old = "    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }\n"
if text.count(old) != 1: raise SystemExit("JasperApplication: presentation() moved (plan 5a Task 9)")
text = text.replace(old, old + '''
    /** The steps a branded look's splash shows (spec 10.11); none until the bootstrap hands them over. EDT. */
    private dev.jasper.app.startup.StartupSteps startupSteps = dev.jasper.app.startup.StartupSteps.NONE;

    /**
     * The steps this application reports to a branded look's splash (spec 10.11): each plugin as it starts, then the
     * first window as it is composed and shown. A presentation without branding, as a look that fell back at start has
     * (spec 4.7), ends them at once. Before {@link #startPlugins(AppDirs)}. EDT.
     *
     * @param steps the bootstrap's steps
     */
    public void startupSteps(dev.jasper.app.startup.StartupSteps steps) {
        startupSteps = java.util.Objects.requireNonNull(steps, "steps");
        if (!presentation.branding().present()) steps.done();
    }
''')
text, n = re.subn(r"^(\s*)plugins\.start\(selection,", r"\1plugins.starting(startupSteps::plugin);\n\1plugins.start(selection,", text, count=1, flags=re.M)
if n != 1: raise SystemExit("JasperApplication: plugins.start(selection, ...) moved (plan 5a Task 9)")
start = text.index("    private TerminalWindow composeWindow(")
show = text.index("window.show();", start)
text = text[:show] + "startupSteps.workstation(); " + text[show:]
old = "        trace.mark(StartupTrace.Milestone.WINDOW_SHOWN);\n"
if text.count(old) != 1: raise SystemExit("JasperApplication: the WINDOW_SHOWN mark moved")
text = text.replace(old, old + "        startupSteps.done();\n")
path.write_text(text, encoding="utf-8")
PY
```

Check the result reads as intended:
`grep -n "startupSteps" jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` shows the field,
the setter, `plugins.starting(startupSteps::plugin);` just before `plugins.start(selection, …)`,
`startupSteps.workstation(); window.show();` inside `composeWindow`, and `startupSteps.done();` right after the
`WINDOW_SHOWN` mark.

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.startup.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `StartupStepsTest` (5), `PluginStartingStepsTest` (1), `StartupStepsApplicationTest` (2), every existing
plugin and application test, the ledger test and the architecture check (no new edge: `plugins` and `application`
already depend on `startup`).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/startup/StartupSteps.java jasper-app/src/main/java/dev/jasper/app/startup/package-info.java \
  jasper-app/src/main/java/dev/jasper/app/plugins \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/startup/StartupStepsTest.java \
  jasper-app/src/test/java/dev/jasper/app/plugins/PluginStartingStepsTest.java jasper-app/src/test/java/dev/jasper/app/application/StartupStepsApplicationTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Branding: the start-up steps

StartupSteps is spec 10.11's step model: Starting Java runtime, Reading
configuration, Loading plugin: <name> for each selected plugin, Restoring
workspace and Starting <product> Workstation, filled as step over count,
forward only, closed once. The runtime names each plugin just before it
loads, the application reports the first window's composition and
showing, and a presentation without branding ends the steps at once.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The splash, matched to the Boot Splash frames

The new package `dev.jasper.app.splash`: `SplashPainter` draws the splash exactly as the frames and the template give it
(Measured targets), `Splash` is its window, and `SplashColors` holds its fixed colours. `BootSplashMockTest` renders the
shipped Enterprise branding and compares it with all ten frames.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/splash/package-info.java`, `SplashColors.java`, `SplashPainter.java`, `Splash.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/splash/SplashPainterTest.java`, `BootSplashMockTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java:42,68`, `tools/visual/pairs.json`, `tools/visual/README.md:16`, `docs/design/theme-engine/migration.csv` (five rows)

**Interfaces:**
- Consumes: Task 1's `EncodedArtwork.raster`; Task 2's `Branding`, `BrandingFixtures`; Task 5's `StartupSteps`; plan 5b's
  `EnterpriseResources.facts()` and the Plex faces.
- Produces: `public final class SplashPainter` with `WIDTH` (720), `HEIGHT` (440), `public record SplashPainter.Content(String
  product, String version, String copyright, byte[] icon64, byte[] icon128, byte[] art, Optional<byte[]> nameFace,
  Optional<byte[]> textFace, Optional<byte[]> versionFace)`, `static SplashPainter of(Content content, int scale)`
  (throws `IllegalArgumentException` when an icon or the art cannot be drawn), `void paint(Graphics2D g, String step,
  double fraction)`, package-private `fillWidth(double)`, `fit(Font, FontRenderContext, String, float)`,
  `advance(Font, FontRenderContext, String)`, `displayable(Font, String, String, int)`, `GAP`; `public final class Splash` with `Splash(SplashPainter, List<Image> icons)`
  (a plain `java.awt.Window`, carrying `icons`),
  `void open(StartupSteps.Step first)`, `void show(StartupSteps.Step step)`, `void close()` (EDT), package-private
  `static Point centred(Rectangle screen)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/splash/SplashPainterTest.java`:

```java
package dev.jasper.app.splash;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.testsupport.BrandingFixtures;
import java.awt.Color;
import java.awt.Font;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.font.FontRenderContext;
import java.awt.image.BufferedImage;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import javax.swing.UIManager;
import javax.swing.plaf.FontUIResource;
import org.junit.jupiter.api.Test;

/** Spec 10.11: the splash's fill, its step text, its scale, its theme independence and its files. */
class SplashPainterTest {
    static SplashPainter.Content content(byte[] icon64, byte[] art, Optional<byte[]> nameFace) {
        return new SplashPainter.Content("JasperOps", "1.2.0 · Build 8ba67ed3", "Copyright 2026 Dustin McKenna-Watts", icon64,
            BrandingFixtures.png(128, 128), art, nameFace, Optional.empty(), Optional.empty());
    }

    static SplashPainter.Content content() { return content(BrandingFixtures.png(64, 64), BrandingFixtures.svg(), Optional.empty()); }

    static BufferedImage render(SplashPainter painter, String step, double fraction, int scale) {
        var image = new BufferedImage(SplashPainter.WIDTH * scale, SplashPainter.HEIGHT * scale, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.scale(scale, scale); painter.paint(g, step, fraction); }
        finally { g.dispose(); }
        return image;
    }

    static int[] pixels(BufferedImage image) { return image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()); }

    @Test void theFillIsTheFractionOfTheBarWithoutEasing() {
        assertThat(SplashPainter.fillWidth(0.06)).isEqualTo(43);
        assertThat(SplashPainter.fillWidth(0.36)).isEqualTo(258);
        assertThat(SplashPainter.fillWidth(0.76)).isEqualTo(546);
        assertThat(SplashPainter.fillWidth(0.88)).isEqualTo(632);
        assertThat(SplashPainter.fillWidth(1.0)).isEqualTo(718);
        assertThat(SplashPainter.fillWidth(2.0)).isEqualTo(718);
        assertThat(SplashPainter.fillWidth(0)).isZero();
        assertThat(SplashPainter.fillWidth(-1)).isZero();
        assertThat(SplashPainter.fillWidth(Double.NaN)).isZero();
        var image = render(SplashPainter.of(content(), 1), StartupSteps.RUNTIME, 0.5, 1);
        int track = SplashColors.TRACK.getRGB() & 0xffffff;
        assertThat(image.getRGB(359, 435) & 0xffffff).as("the fill's last column").isNotEqualTo(track);
        assertThat(image.getRGB(360, 435) & 0xffffff).as("the track after it").isEqualTo(track);
    }

    /** Review Focus 4: a step too long for its room ends with an ellipsis and keeps the 24 px gap to the copyright line. */
    @Test void aLongStepIsEllipsisedBeforeTheCopyrightWithItsGap() {
        var painter = SplashPainter.of(content(), 1);
        String step = StartupSteps.PLUGIN + "A plugin whose name goes on " + "and on ".repeat(40);
        var empty = render(painter, "", 0.4, 1);
        var full = render(painter, step, 0.4, 1);
        int copyrightLeft = 719;
        for (int y = 390; y < 412; y++) for (int x = 35; x < 719; x++) {
            int rgb = empty.getRGB(x, y);
            if ((((rgb >> 16) & 255) + ((rgb >> 8) & 255) + (rgb & 255)) / 3 > 90) copyrightLeft = Math.min(copyrightLeft, x);
        }
        assertThat(copyrightLeft).as("the copyright line's ink").isBetween(400, 700);
        // Two pixels of slack for side bearings: the gap is measured between advances, this between inks.
        for (int y = 390; y < 412; y++) for (int x = copyrightLeft - SplashPainter.GAP + 3; x < 719; x++)
            assertThat(full.getRGB(x, y)).as("nothing of the step at %s,%s", x, y).isEqualTo(empty.getRGB(x, y));
        var font = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        var frc = new FontRenderContext(null, true, true);
        String fitted = SplashPainter.fit(font, frc, step, 200f);
        assertThat(fitted).endsWith("…").startsWith(StartupSteps.PLUGIN);
        assertThat(SplashPainter.advance(font, frc, fitted)).isLessThanOrEqualTo(200f);
        assertThat(SplashPainter.fit(font, frc, StartupSteps.CONFIGURATION, 400f)).isEqualTo(StartupSteps.CONFIGURATION);
    }

    @Test void theArtFollowsTheDeviceScale() {
        var painter = SplashPainter.of(content(), 2);
        var one = render(painter, StartupSteps.RUNTIME, 0.1, 1);
        var two = render(painter, StartupSteps.RUNTIME, 0.1, 2);
        assertThat(two.getWidth()).isEqualTo(1440);
        assertThat(two.getRGB(0, 0)).isEqualTo(SplashColors.BORDER.getRGB());
        assertThat(two.getRGB(1, 1)).as("a 1 px border is 2 device pixels").isEqualTo(SplashColors.BORDER.getRGB());
        assertThat(two.getRGB(2, 300)).isNotEqualTo(SplashColors.BORDER.getRGB());
        // The fixture art is one flat colour, so its 2x raster is that colour wherever the 1x raster is.
        assertThat(two.getRGB(720, 200)).isEqualTo(one.getRGB(360, 100));
    }

    /** Spec 10.11: the same in Light and Dark; the splash reads no theme and no UIManager key. */
    @Test void theSplashIgnoresTheTheme() {
        var painter = SplashPainter.of(content(), 1);
        int[] plain = pixels(render(painter, StartupSteps.WORKSPACE, 0.9, 1));
        Object foreground = UIManager.get("Label.foreground"), font = UIManager.get("defaultFont");
        try {
            UIManager.put("Label.foreground", Color.MAGENTA);
            UIManager.put("defaultFont", new FontUIResource(Font.SERIF, Font.ITALIC, 30));
            assertThat(pixels(render(painter, StartupSteps.WORKSPACE, 0.9, 1))).isEqualTo(plain);
        } finally {
            UIManager.put("Label.foreground", foreground);
            UIManager.put("defaultFont", font);
        }
    }

    /** Review Focus 2: a file that cannot be drawn refuses the splash at once; a face that does not decode falls back. */
    @Test void undrawableFilesAreRefusedAndAnUndecodableFaceFallsBack() {
        byte[] notPng = "not a png".getBytes(StandardCharsets.UTF_8);
        assertThatThrownBy(() -> SplashPainter.of(content(notPng, BrandingFixtures.svg(), Optional.empty()), 1))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("64 px icon");
        assertThatThrownBy(() -> SplashPainter.of(content(BrandingFixtures.png(64, 64), "<html/>".getBytes(StandardCharsets.UTF_8),
            Optional.empty()), 1)).isInstanceOf(IllegalArgumentException.class);
        assertThatCode(() -> SplashPainter.of(content(BrandingFixtures.png(64, 64), BrandingFixtures.svg(), Optional.of(new byte[] {1, 2, 3})), 1))
            .doesNotThrowAnyException();
    }

    /** Execution notes (2026-10-17) ruling 8: a text the kit's face cannot draw takes the platform face at its size. */
    @Test void aTextTheFaceCannotDrawTakesThePlatformFace() throws Exception {
        byte[] plex = java.nio.file.Files.readAllBytes(dev.jasper.app.testsupport.EnterpriseResources.RESOURCES
            .resolve("presentation/fonts/ibm-plex-sans-regular.ttf"));
        Font face = Font.createFont(Font.TRUETYPE_FONT, new java.io.ByteArrayInputStream(plex)).deriveFont(12f);
        assertThat(SplashPainter.displayable(face, StartupSteps.PLUGIN + "Tunnels…", Font.SANS_SERIF, Font.PLAIN)).isSameAs(face);
        Font fallback = SplashPainter.displayable(face, StartupSteps.PLUGIN + "\u65E5\u672C", Font.SANS_SERIF, Font.PLAIN);
        assertThat(fallback.getFamily()).isNotEqualTo(face.getFamily());
        assertThat(fallback.getSize2D()).isEqualTo(12f);
    }

    @Test void theSplashIsCentredOnTheMainScreenAndOneThatCannotOpenStaysClosed() {
        assertThat(Splash.centred(new Rectangle(0, 0, 1440, 900))).isEqualTo(new Point(360, 230));
        assertThat(Splash.centred(new Rectangle(1440, -200, 1920, 1080))).isEqualTo(new Point(2040, 120));
        assertThatThrownBy(() -> new Splash(SplashPainter.of(content(), 1), null)).isInstanceOf(NullPointerException.class);
        var splash = new Splash(SplashPainter.of(content(), 1), java.util.List.of(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB)));
        // Headless: the window cannot be made; that is logged and the splash stays closed.
        assertThatCode(() -> {
            splash.open(new StartupSteps.Step(StartupSteps.RUNTIME, 1, 4));
            splash.show(new StartupSteps.Step(StartupSteps.CONFIGURATION, 2, 4));
            splash.close();
            splash.close();
        }).doesNotThrowAnyException();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/splash/BootSplashMockTest.java`:

```java
package dev.jasper.app.splash;

import dev.jasper.app.presentation.KitParser;
import dev.jasper.app.testsupport.EnterpriseResources;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import org.assertj.core.api.SoftAssertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;

/**
 * Spec 10.11 and 16.1.4: the splash under the shipped Enterprise branding, at 1x, against the ten Boot Splash frames
 * (1x, 720 by 440). The frames are palette-quantised screenshots of the animated mock (the plan's Measured targets), so
 * the render's flat colours are exact and the mock's within 3, regions compare by mean difference, text by its ink box,
 * and the fill by frame 10's rows. The version, the copyright, the steps and their fill are the mock's fixtures. The
 * frames were drawn on macOS and Linux and Windows rasterize IBM Plex differently, so the text is compared on macOS only
 * (plan Rulings 18); every other probe runs on every OS. Renders and difference images are written to
 * jasper-app/build/enterprise-splash/ for inspection.
 */
class BootSplashMockTest {
    static final Path FRAMES_DIRECTORY = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference/10 Boot Splash");
    static final Path OUT = EnterpriseResources.ROOT.resolve("jasper-app/build/enterprise-splash");
    static final String VERSION = "1.0.0 · Build a3f9c21", COPYRIGHT = "CopyRight Notice Here";

    /** An inclusive ink box. */
    record Box(int left, int top, int right, int bottom) { }

    /** One frame: its id, its file, its step, the fill it caught, and its step's measured ink box. */
    record Frame(String id, String file, String step, double fraction, Box stepInk) { }

    static final List<Frame> FRAMES = List.of(
        new Frame("01", "Boot Splash 01 - Starting Java runtime.png", "Starting Java runtime…", 0.06, new Box(42, 395, 164, 407)),
        new Frame("02", "Boot Splash 02 - Reading configuration.png", "Reading configuration…", 96 / 718.0, new Box(42, 395, 166, 407)),
        new Frame("03", "Boot Splash 03 - Unlocking credential vault.png", "Unlocking credential vault…", 135 / 718.0, new Box(42, 395, 189, 407)),
        new Frame("04", "Boot Splash 04 - Loading plugin File Server.png", "Loading plugin: File Server", 0.36, new Box(42, 395, 182, 407)),
        new Frame("05", "Boot Splash 05 - Loading plugin Tunnels.png", "Loading plugin: Tunnels", 0.46, new Box(42, 395, 166, 407)),
        new Frame("06", "Boot Splash 06 - Loading plugin Credential Vault.png", "Loading plugin: Credential Vault", 0.56, new Box(42, 395, 210, 407)),
        new Frame("07", "Boot Splash 07 - Loading plugin Run Dialog.png", "Loading plugin: Run Dialog", 407 / 718.0, new Box(42, 395, 182, 407)),
        new Frame("08", "Boot Splash 08 - Indexing hosts.png", "Indexing hosts…", 0.76, new Box(42, 395, 128, 407)),
        new Frame("09", "Boot Splash 09 - Restoring workspace.png", "Restoring workspace…", 0.88, new Box(42, 396, 161, 407)),
        new Frame("10", "Boot Splash 10 - Starting Workstation.png", "Starting JasperOps Workstation…", 1.0, new Box(42, 396, 220, 407)));

    static final Rectangle ART = new Rectangle(1, 1, 718, 250), ICON = new Rectangle(41, 279, 64, 64);
    /** Where only the gradient shows. */
    static final List<Rectangle> GROUND = List.of(new Rectangle(1, 252, 718, 24), new Rectangle(1, 276, 39, 70),
        new Rectangle(330, 276, 389, 70), new Rectangle(1, 346, 718, 42), new Rectangle(1, 412, 718, 20));
    static final Rectangle NAME = new Rectangle(110, 280, 490, 42), VERSION_LINE = new Rectangle(110, 319, 490, 26),
        STEP = new Rectangle(30, 385, 490, 35), COPYRIGHT_LINE = new Rectangle(520, 385, 180, 35);
    static final Box NAME_INK = new Box(122, 291, 265, 317), VERSION_INK = new Box(122, 323, 271, 332),
        COPYRIGHT_INK = new Box(568, 398, 678, 407);

    static SplashPainter.Content content(KitParser.Parsed parsed) {
        var branding = parsed.branding();
        var ui = parsed.faces().ui().orElseThrow();
        var data = parsed.faces().data().orElseThrow();
        return new SplashPainter.Content(branding.product(), VERSION, COPYRIGHT, branding.icon(64), branding.icon(128), branding.splashArt(),
            Optional.of(ui.semibold().bytes()), Optional.of(ui.regular().bytes()), Optional.of(data.regular().bytes()));
    }

    static BufferedImage render(SplashPainter painter, String step, double fraction) {
        var image = new BufferedImage(SplashPainter.WIDTH, SplashPainter.HEIGHT, BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { painter.paint(g, step, fraction); }
        finally { g.dispose(); }
        return image;
    }

    static BufferedImage mock(String file) throws Exception {
        BufferedImage source = ImageIO.read(FRAMES_DIRECTORY.resolve(file).toFile());
        var image = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);
        var g = image.createGraphics();
        try { g.drawImage(source, 0, 0, null); }
        finally { g.dispose(); }
        return image;
    }

    static int channel(int rgb, int c) { return (rgb >> (16 - 8 * c)) & 255; }

    /** The largest of the three channels' mean absolute differences over {@code region}. */
    static double mean(BufferedImage a, BufferedImage b, Rectangle region) {
        long[] sums = new long[3];
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                for (int c = 0; c < 3; c++) sums[c] += Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c));
        long area = (long) region.width * region.height;
        return Math.max(sums[0], Math.max(sums[1], sums[2])) / (double) area;
    }

    /** The largest absolute channel difference over {@code region}. */
    static int largest(BufferedImage a, BufferedImage b, Rectangle region) {
        int largest = 0;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++)
                for (int c = 0; c < 3; c++) largest = Math.max(largest, Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c)));
        return largest;
    }

    static int distance(int rgb, Color color) {
        int largest = 0;
        for (int c = 0; c < 3; c++) largest = Math.max(largest, Math.abs(channel(rgb, c) - channel(color.getRGB(), c)));
        return largest;
    }

    /** The inclusive box of the pixels in {@code region} whose mean of R, G and B is above {@code threshold}. */
    static Box ink(BufferedImage image, Rectangle region, int threshold) {
        int left = Integer.MAX_VALUE, top = Integer.MAX_VALUE, right = -1, bottom = -1;
        for (int y = region.y; y < region.y + region.height; y++)
            for (int x = region.x; x < region.x + region.width; x++) {
                int rgb = image.getRGB(x, y);
                if ((channel(rgb, 0) + channel(rgb, 1) + channel(rgb, 2)) / 3.0 > threshold) {
                    left = Math.min(left, x); top = Math.min(top, y); right = Math.max(right, x); bottom = Math.max(bottom, y);
                }
            }
        return right < 0 ? new Box(-1, -1, -1, -1) : new Box(left, top, right, bottom);
    }

    /** The last column of the fill on row 435 (the track's blue is 60, the fill's above 120); 0 for none. */
    static int fillEnd(BufferedImage image) {
        int end = 0;
        for (int x = 1; x < 719; x++) if (channel(image.getRGB(x, 435), 2) > 120) end = x;
        return end;
    }

    static BufferedImage difference(BufferedImage a, BufferedImage b) {
        var image = new BufferedImage(a.getWidth(), a.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) {
                int rgb = 0;
                for (int c = 0; c < 3; c++) rgb = rgb << 8 | Math.min(255, 4 * Math.abs(channel(a.getRGB(x, y), c) - channel(b.getRGB(x, y), c)));
                image.setRGB(x, y, rgb);
            }
        return image;
    }

    static void ink(SoftAssertions soft, String what, Box render, Box mock, Box measured, int left, int top, int right, int bottom) {
        soft.assertThat(mock).as(what + ": the mock's ink is the measured one").isEqualTo(measured);
        soft.assertThat(render.left()).as(what + " left").isBetween(measured.left() - left, measured.left() + left);
        soft.assertThat(render.top()).as(what + " top").isBetween(measured.top() - top, measured.top() + top);
        soft.assertThat(render.right()).as(what + " right").isBetween(measured.right() - right, measured.right() + right);
        soft.assertThat(render.bottom()).as(what + " bottom").isBetween(measured.bottom() - bottom, measured.bottom() + bottom);
    }

    static SplashPainter painter() throws Exception {
        return SplashPainter.of(content(EnterpriseResources.facts().parsed().orElseThrow()), 1);
    }

    /** The border, rule, gradient, art, icon and bar of every frame: no text, so the same on every OS. */
    @Test void everyFrameMatchesItsMock() throws Exception {
        var painter = painter();
        Files.createDirectories(OUT);
        BufferedImage settled = mock(FRAMES.getLast().file());
        var soft = new SoftAssertions();
        for (Frame frame : FRAMES) {
            BufferedImage mock = mock(frame.file());
            BufferedImage render = render(painter, frame.step(), frame.fraction());
            ImageIO.write(render, "png", OUT.resolve(frame.id() + ".png").toFile());
            ImageIO.write(difference(render, mock), "png", OUT.resolve(frame.id() + "-diff.png").toFile());
            String at = "frame " + frame.id() + ": ";
            int border = 0, mockBorder = 0, rule = 0;
            for (int x = 0; x < 720; x++) for (int y : new int[] {0, 439}) {
                if (distance(render.getRGB(x, y), SplashColors.BORDER) != 0) border++;
                if (distance(mock.getRGB(x, y), SplashColors.BORDER) != 0) mockBorder++;
            }
            for (int y = 0; y < 440; y++) for (int x : new int[] {0, 719}) {
                if (distance(render.getRGB(x, y), SplashColors.BORDER) != 0) border++;
                if (distance(mock.getRGB(x, y), SplashColors.BORDER) != 0) mockBorder++;
            }
            for (int x = 1; x < 719; x++) if (distance(render.getRGB(x, 251), SplashColors.BORDER) != 0) rule++;
            soft.assertThat(border).as(at + "border pixels that are not the border colour").isZero();
            soft.assertThat(mockBorder).as(at + "the mock's border pixels that are not the border colour").isZero();
            soft.assertThat(rule).as(at + "rule pixels under the art that are not the border colour").isZero();
            soft.assertThat(mean(render, mock, ART)).as(at + "art").isLessThanOrEqualTo(6.0);
            for (Rectangle ground : GROUND) {
                soft.assertThat(mean(render, mock, ground)).as(at + "gradient " + ground).isLessThanOrEqualTo(2.0);
                soft.assertThat(largest(render, mock, ground)).as(at + "gradient " + ground + " at worst").isLessThanOrEqualTo(6);
            }
            soft.assertThat(mean(render, mock, ICON)).as(at + "icon").isLessThanOrEqualTo(4.0);
            soft.assertThat(distance(render.getRGB(360, 432), SplashColors.TRACK_RULE)).as(at + "the bar's rule").isZero();
            soft.assertThat(distance(mock.getRGB(360, 432), SplashColors.TRACK_RULE)).as(at + "the mock's bar rule").isLessThanOrEqualTo(3);
            int mockEnd = fillEnd(mock);
            soft.assertThat(fillEnd(render)).as(at + "the fill's end").isBetween(mockEnd - 1, mockEnd + 1);
            for (int y = 433; y < 439; y++)
                soft.assertThat(distance(render.getRGB(20, y), new Color(settled.getRGB(20, y)))).as(at + "fill row " + y).isLessThanOrEqualTo(10);
            int track = mockEnd + 6;
            if (track < 718) for (int y = 433; y < 439; y++) {
                soft.assertThat(distance(render.getRGB(track, y), SplashColors.TRACK)).as(at + "track row " + y).isZero();
                soft.assertThat(distance(mock.getRGB(track, y), SplashColors.TRACK)).as(at + "the mock's track row " + y).isLessThanOrEqualTo(2);
            }
        }
        soft.assertAll();
    }

    /** The product name, version line, step and copyright line of every frame: macOS only (plan Rulings 18). */
    @Test @EnabledOnOs(OS.MAC) void everyFramesTextMatchesItsMock() throws Exception {
        var painter = painter();
        var soft = new SoftAssertions();
        for (Frame frame : FRAMES) {
            BufferedImage mock = mock(frame.file());
            BufferedImage render = render(painter, frame.step(), frame.fraction());
            String at = "frame " + frame.id() + ": ";
            ink(soft, at + "product name", ink(render, NAME, 140), ink(mock, NAME, 140), NAME_INK, 1, 1, 2, 1);
            ink(soft, at + "version line", ink(render, VERSION_LINE, 100), ink(mock, VERSION_LINE, 100), VERSION_INK, 1, 1, 2, 1);
            ink(soft, at + "step", ink(render, STEP, 100), ink(mock, STEP, 100), frame.stepInk(), 1, 1, 2, 1);
            ink(soft, at + "copyright line", ink(render, COPYRIGHT_LINE, 90), ink(mock, COPYRIGHT_LINE, 90), COPYRIGHT_INK, 2, 1, 1, 1);
            for (Rectangle text : List.of(NAME, VERSION_LINE, STEP, COPYRIGHT_LINE))
                soft.assertThat(mean(render, mock, text)).as(at + "text " + text).isLessThanOrEqualTo(8.0);
        }
        soft.assertAll();
    }
}
```

Add the five ledger rows at the end of `docs/design/theme-engine/migration.csv` (each one line):

```
jasper-app/src/main/java/dev/jasper/app/splash/SplashColors.java,app.splash,SplashColors,custom-content,none,none,jasper-app/src/test/java/dev/jasper/app/splash/BootSplashMockTest.java,New in Enterprise plan 11 Task 6; the boot splash's fixed colours (spec 10.11); the one source ColourLiteralScanTest exempts for them
jasper-app/src/main/java/dev/jasper/app/splash/SplashPainter.java,app.splash,SplashPainter,custom-content,none,none,jasper-app/src/test/java/dev/jasper/app/splash/BootSplashMockTest.java,New in Enterprise plan 11 Task 6; draws the boot splash from a look's files; reads no theme
jasper-app/src/main/java/dev/jasper/app/splash/Splash.java,app.splash,Splash,custom-content,none,none,jasper-app/src/test/java/dev/jasper/app/splash/SplashPainterTest.java,New in Enterprise plan 11 Task 6; the boot splash's undecorated unfocusable window; painted at once at each step
jasper-app/src/test/java/dev/jasper/app/splash/SplashPainterTest.java,app.splash,SplashPainterTest,custom-content,none,none,jasper-app/src/test/java/dev/jasper/app/splash/SplashPainterTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 6; fill; ellipsis; scale; theme independence; files
jasper-app/src/test/java/dev/jasper/app/splash/BootSplashMockTest.java,app.splash,BootSplashMockTest,custom-content,none,none,jasper-app/src/test/java/dev/jasper/app/splash/BootSplashMockTest.java,Test/fixture ownership; New in Enterprise plan 11 Task 6; the splash against the ten Boot Splash frames
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.splash.*'`
Expected: FAIL; `compileTestJava` reports "package dev.jasper.app.splash does not exist" or "cannot find symbol" for
`SplashPainter`, `Splash` and `SplashColors`.

- [ ] **Step 3: Write the package, the colours and the painter**

Create `jasper-app/src/main/java/dev/jasper/app/splash/package-info.java`:

```java
/**
 * The boot splash of a branded look (spec 10.11): {@link dev.jasper.app.splash.SplashPainter} draws it from the look's
 * files as the Boot Splash frames give it, the same in every theme, and {@link dev.jasper.app.splash.Splash} is its
 * undecorated, unfocusable window, painted at once at each step because the event thread is busy starting Jasper. The
 * bootstrap owns the splash and closes it through the start-up steps.
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.startup, dev.jasper.ui.icons.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.splash;
```

Create `jasper-app/src/main/java/dev/jasper/app/splash/SplashColors.java`:

```java
package dev.jasper.app.splash;

import java.awt.Color;

/**
 * The boot splash's colours (spec 10.11). They belong to the splash artwork, the same under every theme and in Light and
 * Dark, so they live here rather than in a theme; ColourLiteralScanTest exempts this one file for them.
 */
final class SplashColors {
    /** The 1 px window border and the rule under the art. */
    static final Color BORDER = new Color(0x8A8F96);
    /** The content gradient's top. */
    static final Color TOP = new Color(0x17346F);
    /** The content gradient's bottom. */
    static final Color BOTTOM = new Color(0x102552);
    /** The product name. */
    static final Color PRODUCT = new Color(0xFFFFFF);
    /** The version line and the step. */
    static final Color TEXT = new Color(0xC7D6F2);
    /** The copyright line. */
    static final Color COPYRIGHT = new Color(0x9FB3DA);
    /** The bar's track. */
    static final Color TRACK = new Color(0x0B1A3C);
    /** The 1 px rule above the track. */
    static final Color TRACK_RULE = new Color(0x2A4580);
    /** The fill gradient's top, its stop at 60 % and its bottom. */
    static final Color FILL_TOP = new Color(0x5A8BE0), FILL_MIDDLE = new Color(0x2F65CA), FILL_BOTTOM = new Color(0x1F4C9E);

    private SplashColors() { }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/splash/SplashPainter.java`:

```java
package dev.jasper.app.splash;

import dev.jasper.ui.icons.EncodedArtwork;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.font.FontRenderContext;
import java.awt.font.TextAttribute;
import java.awt.image.BaseMultiResolutionImage;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;

/**
 * Draws the boot splash (spec 10.11) as the Boot Splash frames and the animated mock's markup give it, in window pixels:
 * a 720 by 440 window whose 1 px border surrounds a 718 by 438 content area at (1, 1), so a content position (x, y) of
 * the mock is window pixel (x + 1, y + 1) here. The positions are the frames' (the plan's Measured targets). The same in
 * every theme: it reads no theme and no UIManager key. {@link #of} decodes the files once; painting draws the art from a
 * raster made once per device scale.
 */
public final class SplashPainter {
    /** The window's width in logical pixels. */
    public static final int WIDTH = 720;
    /** The window's height in logical pixels. */
    public static final int HEIGHT = 440;
    static final int CONTENT_X = 1, CONTENT_Y = 1, CONTENT_WIDTH = 718, CONTENT_HEIGHT = 438;
    static final int ART_HEIGHT = 250, RULE_Y = 251;
    static final int ICON_X = 41, ICON_Y = 279, ICON_SIZE = 64;
    static final int TEXT_X = 121, NAME_BASELINE = 312, VERSION_BASELINE = 333;
    static final int STEP_X = 41, STEP_BASELINE = 405, COPYRIGHT_RIGHT = 679, COPYRIGHT_BASELINE = 406, GAP = 24;
    static final int BAR_RULE_Y = 432, BAR_Y = 433, BAR_HEIGHT = 6;
    static final float NAME_SIZE = 30f, NAME_TRACKING = -0.4f, VERSION_SIZE = 12f, STEP_SIZE = 12f, COPYRIGHT_SIZE = 11f;
    static final int MAX_SCALE = 4;
    private static final System.Logger LOG = System.getLogger(SplashPainter.class.getName());

    /**
     * What the splash draws.
     *
     * @param product the product name
     * @param version the version line
     * @param copyright the copyright line
     * @param icon64 the 64 px icon's PNG file
     * @param icon128 the 128 px icon's PNG file, for a 2x screen
     * @param art the splash art's SVG file
     * @param nameFace the UI face's semibold file, for the product name; empty for the platform face
     * @param textFace the UI face's regular file, for the step and the copyright line; empty for the platform face
     * @param versionFace the data face's regular file, for the version line; empty for the platform monospaced face
     */
    public record Content(String product, String version, String copyright, byte[] icon64, byte[] icon128, byte[] art,
                          Optional<byte[]> nameFace, Optional<byte[]> textFace, Optional<byte[]> versionFace) {
        /** Every part is required. */
        public Content {
            Objects.requireNonNull(product, "product"); Objects.requireNonNull(version, "version");
            Objects.requireNonNull(copyright, "copyright"); Objects.requireNonNull(icon64, "icon64");
            Objects.requireNonNull(icon128, "icon128"); Objects.requireNonNull(art, "art");
            Objects.requireNonNull(nameFace, "nameFace"); Objects.requireNonNull(textFace, "textFace");
            Objects.requireNonNull(versionFace, "versionFace");
        }
    }

    private final String product, version, copyright;
    private final Image icon;
    private final EncodedArtwork art;
    private final Font name, versionFont, stepFont, copyrightFont;
    private final Map<Integer, BufferedImage> rasters = new ConcurrentHashMap<>();

    private SplashPainter(String product, String version, String copyright, Image icon, EncodedArtwork art, Font name,
                          Font versionFont, Font stepFont, Font copyrightFont) {
        this.product = product; this.version = version; this.copyright = copyright; this.icon = icon; this.art = art;
        this.name = name; this.versionFont = versionFont; this.stepFont = stepFont; this.copyrightFont = copyrightFont;
    }

    /**
     * Decodes {@code content} once and draws the art for {@code scale}, so the first paint decodes nothing. Any thread.
     *
     * @param content what the splash draws
     * @param scale the main screen's device scale, 1 to 4
     * @return the painter
     * @throws IllegalArgumentException when an icon or the art cannot be drawn
     */
    public static SplashPainter of(Content content, int scale) {
        Objects.requireNonNull(content, "content");
        Image icon = new BaseMultiResolutionImage(png(content.icon64(), ICON_SIZE), png(content.icon128(), 2 * ICON_SIZE));
        var art = new EncodedArtwork(EncodedArtwork.Format.SVG, content.art());
        art.prepare();
        Font semibold = face(content.nameFace(), Font.SANS_SERIF, Font.BOLD);
        Font regular = face(content.textFace(), Font.SANS_SERIF, Font.PLAIN);
        Font data = face(content.versionFace(), Font.MONOSPACED, Font.PLAIN);
        var painter = new SplashPainter(content.product(), content.version(), content.copyright(), icon, art,
            semibold.deriveFont(Map.of(TextAttribute.SIZE, NAME_SIZE, TextAttribute.TRACKING, NAME_TRACKING / NAME_SIZE)),
            data.deriveFont(VERSION_SIZE), regular.deriveFont(STEP_SIZE), regular.deriveFont(COPYRIGHT_SIZE));
        painter.raster(Math.clamp(scale, 1, MAX_SCALE));
        return painter;
    }

    /**
     * Paints the whole splash at the graphics' origin, in logical pixels, at the graphics' device scale.
     *
     * @param target where to paint; its state is left as it was
     * @param step the step's text, ellipsised to the room before the copyright line
     * @param fraction how far the bar is filled, 0 to 1
     */
    public void paint(Graphics2D target, String step, double fraction) {
        Objects.requireNonNull(step, "step");
        Graphics2D g = (Graphics2D) target.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setPaint(new LinearGradientPaint(0, CONTENT_Y, 0, CONTENT_Y + CONTENT_HEIGHT, new float[] {0f, 1f},
                new Color[] {SplashColors.TOP, SplashColors.BOTTOM}));
            g.fillRect(CONTENT_X, CONTENT_Y, CONTENT_WIDTH, CONTENT_HEIGHT);
            g.drawImage(raster(scale(g)), CONTENT_X, CONTENT_Y, CONTENT_WIDTH, ART_HEIGHT, null);
            g.setColor(SplashColors.BORDER);
            g.fillRect(CONTENT_X, RULE_Y, CONTENT_WIDTH, 1);
            g.drawImage(icon, ICON_X, ICON_Y, ICON_SIZE, ICON_SIZE, null);
            FontRenderContext frc = g.getFontRenderContext();
            Shape clip = g.getClip();
            g.clipRect(TEXT_X, 0, COPYRIGHT_RIGHT - TEXT_X, HEIGHT);
            text(g, displayable(name, product, Font.SANS_SERIF, Font.BOLD), SplashColors.PRODUCT, product, TEXT_X, NAME_BASELINE);
            text(g, versionFont, SplashColors.TEXT, version, TEXT_X, VERSION_BASELINE);
            g.setClip(clip);
            float copyrightX = COPYRIGHT_RIGHT - advance(copyrightFont, frc, copyright);
            text(g, copyrightFont, SplashColors.COPYRIGHT, copyright, copyrightX, COPYRIGHT_BASELINE);
            Font stepFace = displayable(stepFont, step, Font.SANS_SERIF, Font.PLAIN);
            text(g, stepFace, SplashColors.TEXT, fit(stepFace, frc, step, copyrightX - GAP - STEP_X), STEP_X, STEP_BASELINE);
            g.setColor(SplashColors.TRACK_RULE);
            g.fillRect(CONTENT_X, BAR_RULE_Y, CONTENT_WIDTH, 1);
            g.setColor(SplashColors.TRACK);
            g.fillRect(CONTENT_X, BAR_Y, CONTENT_WIDTH, BAR_HEIGHT);
            int fill = fillWidth(fraction);
            if (fill > 0) {
                g.setPaint(new LinearGradientPaint(0, BAR_Y, 0, BAR_Y + BAR_HEIGHT, new float[] {0f, 0.6f, 1f},
                    new Color[] {SplashColors.FILL_TOP, SplashColors.FILL_MIDDLE, SplashColors.FILL_BOTTOM}));
                g.fillRect(CONTENT_X, BAR_Y, fill, BAR_HEIGHT);
            }
            g.setColor(SplashColors.BORDER);
            g.fillRect(0, 0, WIDTH, 1);
            g.fillRect(0, HEIGHT - 1, WIDTH, 1);
            g.fillRect(0, 0, 1, HEIGHT);
            g.fillRect(WIDTH - 1, 0, 1, HEIGHT);
        } finally {
            g.dispose();
        }
    }

    /** The fill's width in pixels: the fraction of the 718 px bar, rounded; no easing. */
    static int fillWidth(double fraction) {
        if (!(fraction > 0)) return 0;
        return (int) Math.round(Math.min(1, fraction) * CONTENT_WIDTH);
    }

    /** {@code text}, shortened with an ellipsis to fit {@code room} pixels. */
    static String fit(Font font, FontRenderContext frc, String text, float room) {
        if (advance(font, frc, text) <= room) return text;
        for (int end = text.length(); end > 0; end = text.offsetByCodePoints(end, -1)) {
            String shortened = text.substring(0, end).stripTrailing() + "…";
            if (advance(font, frc, shortened) <= room) return shortened;
        }
        return "";
    }

    /**
     * {@code font}, or the platform's {@code family} at {@code style} and {@code font}'s size when {@code font} lacks a
     * glyph of {@code text} (a plugin or product name in another script), as the application's own text does under a
     * kit face (plan 5b).
     */
    static Font displayable(Font font, String text, String family, int style) {
        if (font.canDisplayUpTo(text) == -1) return font;
        return new Font(family, style, 1).deriveFont(font.getSize2D());
    }

    /** {@code text}'s advance in logical pixels. */
    static float advance(Font font, FontRenderContext frc, String text) {
        return text.isEmpty() ? 0f : (float) font.getStringBounds(text, frc).getWidth();
    }

    private static void text(Graphics2D g, Font font, Color color, String text, float x, float baseline) {
        if (text.isEmpty()) return;
        g.setFont(font);
        g.setColor(color);
        g.drawString(text, x, baseline);
    }

    private static int scale(Graphics2D g) {
        return Math.clamp((long) Math.ceil(g.getTransform().getScaleX() - 1e-6), 1, MAX_SCALE);
    }

    private BufferedImage raster(int scale) {
        return rasters.computeIfAbsent(scale, factor -> art.raster(CONTENT_WIDTH * factor, ART_HEIGHT * factor));
    }

    private static BufferedImage png(byte[] file, int size) {
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(file));
            if (image != null) return image;
        } catch (IOException | RuntimeException unreadable) {
            throw new IllegalArgumentException("The splash's " + size + " px icon does not decode", unreadable);
        }
        throw new IllegalArgumentException("The splash's " + size + " px icon does not decode");
    }

    private static Font face(Optional<byte[]> file, String fallbackFamily, int fallbackStyle) {
        if (file.isPresent()) {
            try { return Font.createFont(Font.TRUETYPE_FONT, new ByteArrayInputStream(file.get())); }
            catch (FontFormatException | IOException | RuntimeException undecodable) {
                LOG.log(System.Logger.Level.WARNING, "A splash face does not decode; the platform face draws its text", undecodable);
            }
        }
        return new Font(fallbackFamily, fallbackStyle, 1);
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/splash/Splash.java`:

```java
package dev.jasper.app.splash;

import dev.jasper.app.startup.StartupSteps;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Toolkit;
import java.awt.Window;
import java.util.List;
import java.util.Objects;

/**
 * The boot splash's window (spec 10.11): undecorated, unfocusable and centred on the main screen, carrying the look's
 * window icons. It appears and closes at once, with no motion, and paints each step at once through the window's own
 * graphics, because the event thread is busy starting Jasper and would not get to a queued repaint. It is a plain AWT
 * window, not a Swing one, so opening it initializes no look and feel ahead of the application's. A window that cannot
 * be made is logged and the splash stays closed; start-up goes on. EDT.
 */
public final class Splash {
    private static final System.Logger LOG = System.getLogger(Splash.class.getName());
    private final SplashPainter painter;
    private final List<Image> icons;
    private Window window;
    private StartupSteps.Step step;
    private boolean closed;

    /**
     * A splash that draws with {@code painter}; nothing is shown until {@link #open}.
     *
     * @param painter the decoded splash
     * @param icons the window's icons, the look's (spec 10.11), so a taskbar or window switcher shows them from the
     *     splash on
     */
    public Splash(SplashPainter painter, List<Image> icons) {
        this.painter = Objects.requireNonNull(painter, "painter");
        this.icons = List.copyOf(icons);
    }

    /**
     * Shows the splash on its first step. Does nothing once closed or open.
     *
     * @param first the step to show
     */
    public void open(StartupSteps.Step first) {
        if (closed || window != null) return;
        step = Objects.requireNonNull(first, "first");
        try {
            window = new Window((Frame) null) {
                @Override public void paint(Graphics g) { draw(g); }
                // The whole window is painted each time, so nothing is cleared first.
                @Override public void update(Graphics g) { draw(g); }
            };
            window.setFocusableWindowState(false);
            window.setIconImages(icons);
            window.setSize(SplashPainter.WIDTH, SplashPainter.HEIGHT);
            window.setLocation(centred(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getBounds()));
            window.setVisible(true);
            paintNow();
        } catch (RuntimeException failure) {
            LOG.log(System.Logger.Level.WARNING, "The splash could not be shown", failure);
            close();
        }
    }

    /**
     * Shows {@code next} at once.
     *
     * @param next the step start-up reached
     */
    public void show(StartupSteps.Step next) {
        step = Objects.requireNonNull(next, "next");
        if (window != null) paintNow();
    }

    /** Closes the splash at once; idempotent. */
    public void close() {
        closed = true;
        if (window == null) return;
        window.setVisible(false);
        window.dispose();
        window = null;
    }

    private void draw(Graphics g) {
        StartupSteps.Step shown = step;
        if (shown != null) painter.paint((Graphics2D) g, shown.text(), shown.fraction());
    }

    private void paintNow() {
        Graphics g = window.getGraphics();
        if (g == null) return;
        try { draw(g); }
        finally { g.dispose(); }
        Toolkit.getDefaultToolkit().sync();
    }

    /** Where a splash centred on {@code screen} goes. */
    static Point centred(Rectangle screen) {
        return new Point(screen.x + (screen.width - SplashPainter.WIDTH) / 2, screen.y + (screen.height - SplashPainter.HEIGHT) / 2);
    }
}
```

- [ ] **Step 4: Exempt the splash's colours and pair the renders with the frames**

```bash
python3 -I - <<'PY'
import json, pathlib
p = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java")
text = p.read_text(encoding="utf-8")
anchor = "    static final Pattern STRING_HEX"
if text.count(anchor) != 1: raise SystemExit("ColourLiteralScanTest: STRING_HEX moved")
text = text.replace(anchor, '''    /** Spec 10.11 fixes the boot splash's colours for every theme; this one source holds them. */
    static final Set<Path> FIXED = Set.of(ROOT.resolve("jasper-app/src/main/java/dev/jasper/app/splash/SplashColors.java"));
''' + anchor)
old = "if (THEMES.contains(file)"
if text.count(old) != 1: raise SystemExit("ColourLiteralScanTest: the theme-file skip moved (plan 5b's THEMES already holds the plugin themes; keep its condition)")
text = text.replace(old, "if (FIXED.contains(file) || THEMES.contains(file)")
p.write_text(text, encoding="utf-8")

pairs = pathlib.Path("tools/visual/pairs.json")
data = json.loads(pairs.read_text(encoding="utf-8"))
frames = {"01": "Boot Splash 01 - Starting Java runtime.png", "04": "Boot Splash 04 - Loading plugin File Server.png",
          "10": "Boot Splash 10 - Starting Workstation.png"}
for frame, file in frames.items():
    data.append({"name": f"enterprise-splash-{frame}", "mock": f"docs/design/enterprise/reference/10 Boot Splash/{file}",
                 "crop": [0, 0, 720, 440], "render": f"jasper-app/build/enterprise-splash/{frame}.png"})
pairs.write_text(json.dumps(data, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")

readme = pathlib.Path("tools/visual/README.md")
text = readme.read_text(encoding="utf-8")
old = "  workspace preview).\n"
if text.count(old) != 1: raise SystemExit("tools/visual/README.md: the compare_all bullet moved")
readme.write_text(text.replace(old, old + "- The Boot Splash frames are 1x (720 × 440), so their pairs (`enterprise-splash-*`) take the 1x renders\n"
    "  `BootSplashMockTest` writes under `jasper-app/build/enterprise-splash/`.\n"), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests and iterate until the render matches**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.splash.*' --tests 'dev.jasper.app.architecture.ColourLiteralScanTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `SplashPainterTest` (7) and `BootSplashMockTest` (2: `everyFrameMatchesItsMock` and, on macOS,
`everyFramesTextMatchesItsMock`; all ten frames, every soft assertion), `ColourLiteralScanTest` with `SplashColors`
exempt, and the ledger test. Run this step on the Mac: elsewhere the text test is skipped and proves nothing.

If a `BootSplashMockTest` assertion fails, open `jasper-app/build/enterprise-splash/NN-diff.png` beside `NN.png` and the
frame, find the cause and change the drawing, never a tolerance:

| Failing check | Remedy, in this order |
|---|---|
| `art` mean above 6 | in `DecodedArtwork.raster` add `KEY_STROKE_CONTROL` `VALUE_STROKE_PURE` and `KEY_RENDERING` `VALUE_RENDER_QUALITY`; then draw the art at twice the scale and let `drawImage` reduce it (`raster(2 * scale)` in `SplashPainter.paint`) |
| an ink box off by a constant number of pixels | move that text's baseline or x constant in `SplashPainter` by the measured offset (the frames are the authority) |
| the product name's right edge off by more than 2 | check that the face is IBM Plex Sans SemiBold (`Content.nameFace`), `TRACKING` is −0.4 / 30 and `KEY_FRACTIONALMETRICS` is on |
| a gradient region above its mean | check the gradient's end points (rows 1 and 439) and that nothing else paints there |
| `icon` above 4 | check the icon's origin (41, 279) and that the 64 px variant is drawn at 1x |
| the fill's end or rows | check `fillWidth` rounding and the gradient's 0, 0.6 and 1 stops over rows 433 to 439 |

Record every constant you change in the plan's status banner and in the Task 8 STATUS entry. Then also run the opt-in
comparison for a visual check: `python3 -I tools/visual/compare_all.py "$(mktemp -d)" enterprise-splash` and inspect its
side-by-side images.

- [ ] **Step 6: Run the architecture check and commit**

Run: `./gradlew :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: BUILD SUCCESSFUL (`splash` depends on `startup` and `dev.jasper.ui.icons`; no cycle).

```bash
git add jasper-app/src/main/java/dev/jasper/app/splash jasper-app/src/test/java/dev/jasper/app/splash \
  jasper-app/src/test/java/dev/jasper/app/architecture/ColourLiteralScanTest.java tools/visual docs/design/theme-engine/migration.csv
git add -u jasper-ui/src/main/java/dev/jasper/ui/icons
git commit -F - <<'EOF'
Branding: the splash, matched to the Boot Splash frames

dev.jasper.app.splash draws the boot splash exactly as the frames and the
mock's markup give it: the gradient, the art drawn by jsvg through
jasper-ui, the 64 px icon, the product name and version line, the step
and About's copyright line, and the 6 px bar filled as step over count,
with no motion, the same in every theme. Splash is its undecorated,
unfocusable window, painted at once at each step. BootSplashMockTest
compares the render with all ten frames; SplashColors holds the fixed
colours and is exempt from the colour scan.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The splash and the icon at start-up

The bootstrap brands the start: right after the desktop properties and the title-bar style are committed, a branded
look's icons become the application's, and on a start that will open a window the splash's files are decoded and its
window is posted ahead of the composition with the Dock or taskbar icon. The steps follow start-up and end whatever
happens; a look that falls back at start gives Jasper's icons back.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (`prepareDesktop`, `startDesktop`, `createApplication`, five new package-private static methods and the private `screenScale`), `bootstrap/package-info.java:3`
- Test: `jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java`

**Interfaces:**
- Consumes: Task 2's `Branding`, `Presentation.branding()`, `BrandingFixtures.plugin`; Task 4's `ApplicationIcon.useLook`,
  `branded`, `installTaskbarIcon`, `BuildInfo.buildLine`, `COPYRIGHT`; Task 5's `StartupSteps`, `PluginSelection.loading()`,
  `JasperApplication.startupSteps`; Task 6's `SplashPainter`, `Splash`; plan 5a's `Startup`, `PluginSelection`.
- Produces: package-private `static StartupSteps ApplicationBootstrap.openSplash(Startup startup, boolean headless)`,
  `static boolean splashWanted(AppArguments options, boolean headless, boolean branded)`,
  `static SplashPainter.Content splashContent(Presentation presentation, BuildInfo build)`,
  `static void presenting(StartupSteps steps, Runnable start)`, `static void abandon(StartupSteps steps, Executor edt)`.

- [ ] **Step 1: Write the failing tests**

Append to `ApplicationBootstrapTest` (before its final closing brace):

```java
    /** Spec 10.11: only a branded start that will open a window shows the splash. */
    @Test void onlyABrandedStartThatOpensAWindowShowsTheSplash() {
        var plain = AppArguments.parse(new String[0], directory);
        assertThat(ApplicationBootstrap.splashWanted(plain, false, true)).isTrue();
        assertThat(ApplicationBootstrap.splashWanted(plain, false, false)).as("no branding").isFalse();
        assertThat(ApplicationBootstrap.splashWanted(plain, true, true)).as("headless").isFalse();
        for (String flag : java.util.List.of("--background", "--no-window", "--safe-mode"))
            assertThat(ApplicationBootstrap.splashWanted(AppArguments.parse(new String[] {flag}, directory), false, true)).as(flag).isFalse();
    }

    /** Review Focus 5: the look's icons reach every branded launch, the splash only one that opens a window. */
    @Test void aBrandedLookGivesTheApplicationItsIconsEvenWithoutASplash() throws Exception {
        var dirs = new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs"));
        var branded = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "",
            dev.jasper.app.testsupport.BrandingFixtures.plugin(directory.resolve("dev"), "dev.example.look", "Example"), false);
        try {
            var background = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[] {"--background"}, directory), dirs, null, branded);
            assertThat(ApplicationBootstrap.openSplash(background, false)).isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            assertThat(dev.jasper.app.platform.ApplicationIcon.branded()).isTrue();
            assertThat(dev.jasper.app.platform.ApplicationIcon.images()).extracting(image -> image.getWidth(null))
                .containsExactly(16, 32, 48, 64, 128, 256, 1024);
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
            var windowed = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[0], directory), dirs, null, branded);
            assertThat(ApplicationBootstrap.openSplash(windowed, true)).as("headless: no splash").isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
            var standard = new ApplicationBootstrap.Startup(null, AppArguments.parse(new String[0], directory), dirs, null,
                dev.jasper.app.plugins.PluginSelection.none());
            assertThat(ApplicationBootstrap.openSplash(standard, false)).isSameAs(dev.jasper.app.startup.StartupSteps.NONE);
            assertThat(dev.jasper.app.platform.ApplicationIcon.branded()).as("the default look keeps Jasper's icons").isFalse();
        } finally {
            dev.jasper.app.platform.ApplicationIcon.useLook(java.util.List.of());
        }
    }

    /** Spec 10.11: the splash shows the look's files, the version line and About's copyright. */
    @Test void theSplashContentIsTheLooksBrandingWithAboutsVersionAndCopyright() throws Exception {
        var dirs = new AppDirs(directory, directory.resolve("config.toml"), directory.resolve("logs"));
        var branded = dev.jasper.app.plugins.PluginSelection.of(dirs, null, "",
            dev.jasper.app.testsupport.BrandingFixtures.plugin(directory.resolve("dev"), "dev.example.look", "Example"), false);
        var build = dev.jasper.app.platform.BuildInfo.of(java.util.Map.of("commit", "8ba67ed3" + "0".repeat(32), "describe", "v1.2.0-14-g8ba67ed3"));
        var content = ApplicationBootstrap.splashContent(branded.presentation(), build);
        assertThat(content.product()).isEqualTo("JasperOps");
        assertThat(content.version()).isEqualTo("1.2.0 + 14 commits · Build 8ba67ed3");
        assertThat(content.copyright()).isEqualTo(dev.jasper.app.platform.BuildInfo.COPYRIGHT);
        assertThat(content.icon64()).isEqualTo(branded.presentation().branding().icon(64));
        assertThat(content.icon128()).isEqualTo(branded.presentation().branding().icon(128));
        assertThat(content.art()).isEqualTo(branded.presentation().branding().splashArt());
        assertThat(content.nameFace()).as("the fixture kit names no faces").isEmpty();
        assertThat(content.versionFace()).isEmpty();
    }

    /** Review Focus 1: a splash never outlives the start that opened it. */
    @Test void theStepsEndWhetherTheStartSucceedsOrFails() {
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var steps = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        ApplicationBootstrap.presenting(steps, () -> { });
        assertThat(closed).hasValue(1);
        var failing = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        assertThatThrownBy(() -> ApplicationBootstrap.presenting(failing, () -> { throw new IllegalStateException("composition failed"); }))
            .hasMessage("composition failed");
        assertThat(closed).hasValue(2);
    }

    /** Review Focus 1: a failed start closes a posted splash once, on the EDT; without a splash nothing is posted. */
    @Test void aFailedStartClosesAPostedSplashOnceAndPostsNothingWithoutOne() {
        var closed = new java.util.concurrent.atomic.AtomicInteger();
        var posted = new java.util.ArrayList<Runnable>();
        var steps = dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet);
        ApplicationBootstrap.abandon(steps, posted::add);
        assertThat(closed).as("closed on the EDT, not on the failing thread").hasValue(0);
        assertThat(posted).hasSize(1);
        posted.forEach(Runnable::run);
        assertThat(closed).hasValue(1);
        ApplicationBootstrap.abandon(steps, posted::add);
        assertThat(posted).as("an ended splash posts nothing").hasSize(1);
        ApplicationBootstrap.abandon(dev.jasper.app.startup.StartupSteps.NONE, posted::add);
        assertThat(posted).as("no splash: nothing is posted, so AWT stays unstarted").hasSize(1);
        ApplicationBootstrap.abandon(dev.jasper.app.startup.StartupSteps.of("JasperOps", 0, step -> { }, closed::incrementAndGet), Runnable::run);
        assertThat(closed).as("startDesktop's catch, already on the EDT").hasValue(2);
    }
```

(`AppDirs`, `AppArguments`, `assertThat` and `assertThatThrownBy` are already imported by the test; add
`import static org.assertj.core.api.Assertions.assertThatThrownBy;` if the compiler reports it.)

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.ApplicationBootstrapTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `splashWanted`, `openSplash`, `splashContent`,
`presenting` and `abandon`.

- [ ] **Step 3: Wire the splash and the icon into the bootstrap**

```bash
python3 -I - <<'PY'
"""Plan 11 Task 7: the bootstrap brands the start."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
B = "jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java"
edit(B, [
    ('''import dev.jasper.app.startup.StartupTrace;\n''',
     '''import dev.jasper.app.startup.StartupSteps;
import dev.jasper.app.startup.StartupTrace;
import dev.jasper.app.splash.Splash;
import dev.jasper.app.splash.SplashPainter;
import dev.jasper.app.platform.BuildInfo;
import dev.jasper.app.presentation.Branding;
import dev.jasper.app.presentation.Presentation;\n''', 1),
    # prepareDesktop: open the splash once AWT may start; close it if start-up fails after posting it.
    ('''        acquired.own(service);
        try {
            AppDirs dirs = startup.dirs();\n''',
     '''        acquired.own(service);
        var opened = new AtomicReference<StartupSteps>(StartupSteps.NONE);
        try {
            AppDirs dirs = startup.dirs();\n''', 1),
    ('''            // Off the EDT and before the first window: an optional prewarm of the built-in icons.\n''',
     '''            // Spec 10.11, as soon as AWT may start: the look's icons and, on a start that will open a window, its splash.
            opened.set(openSplash(startup, GraphicsEnvironment.isHeadless()));
            // Off the EDT and before the first window: an optional prewarm of the built-in icons.\n''', 1),
    ('''            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, endpoint, commands));\n''',
     '''            StartupSteps steps = opened.get();
            SwingUtilities.invokeLater(() -> startDesktop(startup, dirs, log, exceptions, shutdown, endpoint, commands, steps));\n''', 1),
    ('''            acquired.rollback(failure);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);\n''',
     '''            acquired.rollback(failure);
            // A splash already posted opens first, then closes: start-up failed (spec 10.11). Nothing without one.
            abandon(opened.get(), SwingUtilities::invokeLater);
            LOG.log(System.Logger.Level.ERROR, "Application startup failed", failure);\n''', 1),
    # startDesktop: the steps follow the composition and end whatever happens.
    ('''                                     AtomicReference<CommandListener> commands) {
        ConfigService service = startup.service(); AppArguments options = startup.options();''',
     '''                                     AtomicReference<CommandListener> commands, StartupSteps steps) {
        ConfigService service = startup.service(); AppArguments options = startup.options();''', 1),
    ('''            compose(service, () -> new CommandHistory(dirs.commandHistory()),''',
     '''            presenting(steps, () -> compose(service, () -> new CommandHistory(dirs.commandHistory()),''', 1),
    ('''createApplication(service, history, dirs, options, commands, startup.selection());''',
     '''createApplication(service, history, dirs, options, commands, startup.selection(), steps);''', 1),
    ('''                        long window = System.nanoTime();
                        application.newWindow();''',
     '''                        steps.workspace();
                        long window = System.nanoTime();
                        application.newWindow();''', 1),
    ('''                });
        } catch (RuntimeException | Error failure) {
            // Also covers failure before composition took ownership (for example initial role resolution).\n''',
     '''                }));
        } catch (RuntimeException | Error failure) {
            abandon(steps, Runnable::run);
            // Also covers failure before composition took ownership (for example initial role resolution).\n''', 1),
    # createApplication: the configuration step, then the look's icons as the application runs them.
    ('''                                                       dev.jasper.app.plugins.PluginSelection selection) {''',
     '''                                                       dev.jasper.app.plugins.PluginSelection selection, StartupSteps steps) {''', 1),
    ('''            var application = new JasperApplication(service, null, history, dirs.buddyState(),\n''',
     '''            steps.configuration();
            var application = new JasperApplication(service, null, history, dirs.buddyState(),\n''', 1),
    ('''                () -> System.exit(0), integrationDir, dirs.themes(), selection);\n''',
     '''                () -> System.exit(0), integrationDir, dirs.themes(), selection);
            // A look that fell back at start runs without its branding (spec 4.7 and 9.1): Jasper's icons, and no splash.
            ApplicationIcon.useLook(application.presentation().branding().iconFiles());
            ApplicationIcon.installTaskbarIcon();
            application.startupSteps(steps);\n''', 1),
    # Before plan 5a Task 12's attemptBeforePresenting javadoc (plan 6a rewrites the configureDesktopProperties one).
    ('''    /**
     * Whether the launch is about to show a window or rehearse one, so the look's attempt is marked pending (spec 4.6):''',
     '''    /**
     * Spec 10.11, once the desktop properties and the title-bar style are committed: a branded look's icons become the
     * application's on every launch (so a window a resident opens later carries them too), and on a start that will
     * open a window the splash's files are decoded here, off the EDT, and its window is posted ahead of the composition,
     * with the Dock or taskbar icon. Returns the steps start-up reports to the splash, or {@link StartupSteps#NONE}. A
     * splash whose files cannot be drawn is skipped and logged; start-up goes on.
     */
    static StartupSteps openSplash(Startup startup, boolean headless) {
        Presentation presentation = startup.selection().presentation();
        Branding branding = presentation.branding();
        if (!branding.present()) return StartupSteps.NONE;
        long began = System.nanoTime();
        ApplicationIcon.useLook(branding.iconFiles());
        if (!splashWanted(startup.options(), headless, true)) return StartupSteps.NONE;
        SplashPainter painter;
        try { painter = SplashPainter.of(splashContent(presentation, BuildInfo.current()), screenScale()); }
        catch (RuntimeException undrawable) {
            LOG.log(System.Logger.Level.WARNING, "The " + branding.product() + " splash cannot be drawn; starting without it", undrawable);
            return StartupSteps.NONE;
        }
        var splash = new Splash(painter, ApplicationIcon.images());
        var steps = StartupSteps.of(branding.product(), startup.selection().loading().size(), splash::show, splash::close);
        SwingUtilities.invokeLater(() -> {
            ApplicationIcon.installTaskbarIcon();
            splash.open(steps.current());
        });
        StartupTrace.current().phase("splash", began);
        return steps;
    }

    /**
     * Whether this start shows a branded look's splash: one that will open a window, so not headless,
     * {@code --background}, {@code --no-window} or {@code --safe-mode} (a handed-off launch never gets here).
     */
    static boolean splashWanted(AppArguments options, boolean headless, boolean branded) {
        return branded && !headless && !options.background() && !options.noWindow() && !options.safeMode();
    }

    /** What the splash draws: the look's branding and faces, the version line and About's copyright line (spec 10.11). */
    static SplashPainter.Content splashContent(Presentation presentation, BuildInfo build) {
        Branding branding = presentation.branding();
        var faces = presentation.faces();
        return new SplashPainter.Content(branding.product(), build.buildLine(), BuildInfo.COPYRIGHT, branding.icon(64), branding.icon(128),
            branding.splashArt(), faces.ui().map(ui -> ui.semibold().bytes()), faces.ui().map(ui -> ui.regular().bytes()),
            faces.data().map(data -> data.regular().bytes()));
    }

    /**
     * Ends the steps of a start that failed (spec 10.11) through {@code edt}, the event thread: a splash already posted
     * opens first, then closes. Without a splash nothing is posted, so a failure before anything touched AWT does not
     * start the toolkit. {@code active()} only ever turns false, so a stale read off the EDT at worst posts a second,
     * harmless {@code done()}.
     */
    static void abandon(StartupSteps steps, java.util.concurrent.Executor edt) {
        if (steps.active()) edt.execute(steps::done);
    }

    /** Runs {@code start}, then ends {@code steps} whatever happened: a splash never outlives the start that opened it. EDT. */
    static void presenting(StartupSteps steps, Runnable start) {
        try { start.run(); }
        finally { steps.done(); }
    }

    /** The main screen's device scale, for the splash art's first raster; 1 without a screen. */
    private static int screenScale() {
        try {
            return (int) Math.ceil(GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice()
                .getDefaultConfiguration().getDefaultTransform().getScaleX() - 1e-6);
        } catch (RuntimeException | Error noScreen) {
            return 1;
        }
    }

    /**
     * Whether the launch is about to show a window or rehearse one, so the look's attempt is marked pending (spec 4.6):''', 1)])
text = pathlib.Path(B).read_text(encoding="utf-8")
if "import java.awt.GraphicsEnvironment;" not in text:
    text = text.replace("import java.nio.file.Path;\n", "import java.awt.GraphicsEnvironment;\nimport java.nio.file.Path;\n", 1)
    pathlib.Path(B).write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/bootstrap/package-info.java", [
    ('''dev.jasper.app.snippets, dev.jasper.app.startup,''', '''dev.jasper.app.snippets, dev.jasper.app.splash, dev.jasper.app.startup,''', 1)])
PY
```

Check that the composition reads as intended: `grep -n "presenting(steps\|steps\.\|StartupSteps steps" jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java`
shows `opened`'s two uses in `prepareDesktop`, `presenting(steps, () -> compose(` closed by `}));`, `steps.workspace();`
before the first window, `abandon(steps, Runnable::run);` first in `startDesktop`'s catch, `abandon(opened.get(),
SwingUtilities::invokeLater);` in `prepareDesktop`'s, and `steps.configuration();` before the application is made.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.platform.*' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: the five new `ApplicationBootstrapTest` cases and every existing bootstrap, rollback, application and
platform test (they run the test entry `start(args, out, error, launch)`, whose selection is `PluginSelection.none()`,
so nothing is branded); the architecture check with the new `bootstrap → splash` edge and no cycle.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap jasper-app/src/test/java/dev/jasper/app/bootstrap/ApplicationBootstrapTest.java
git commit -F - <<'EOF'
Branding: the splash and the icon at start-up

Once the desktop properties and title-bar style are committed, a branded
look's icons become the application's on every launch, and on a start
that will open a window (not --background, --no-window, safe mode or
headless) the splash's files are decoded off the EDT and its window is
posted ahead of the composition with the Dock or taskbar icon. The
steps follow start-up and end whatever happens; a look that falls back
at start gives Jasper's icons back. The start-up trace times the splash.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Documentation, STATUS and the whole build

**Files:**
- Modify: `docs/app-architecture.md` (the `splash` row, the `startup` and `presentation` rows), the guide that documents the kit file (found below), `docs/STATUS.md`, this plan's status banner

- [ ] **Step 1: Edit the documentation**

Do not rebase or merge: plans 7 and 9a run in parallel, and the orchestrator reconciles `docs/app-architecture.md`,
`docs/STATUS.md`, `migration.csv` and `pairs.json` when it merges.

```bash
python3 -I - <<'PY'
import pathlib, re
p = pathlib.Path("docs/app-architecture.md")
text = p.read_text(encoding="utf-8")
def once(old, new):
    global text
    if text.count(old) != 1: raise SystemExit(f"app-architecture.md: expected one {old!r}")
    text = text.replace(old, new)
once("| `residency` |", "| `splash` | The boot splash of a branded look: `SplashPainter` draws it from the look's files, the same in every theme, "
     "and `Splash` is its undecorated window. The bootstrap decodes the files off the EDT, opens the window on it and closes it "
     "through `StartupSteps` when the first window shows or start-up fails. |\n| `residency` |")
once("| `startup` | Start-up timing (`StartupTrace`, `WindowTrace`, `StartupTraceFile`);",
     "| `startup` | Start-up timing (`StartupTrace`, `WindowTrace`, `StartupTraceFile`) and the steps a branded look's splash shows (`StartupSteps`);")
once("bootstrap, application, plugins and benchmark. Safe from any thread;",
     "bootstrap, application, plugins and benchmark. Safe from any thread except `StartupSteps` (EDT only);")
row = re.search(r"^\| `presentation` \|.*\|$", text, flags=re.M)
if not row: raise SystemExit("app-architecture.md: no presentation row (plan 5a Task 13 adds it); re-find it")
line = row.group(0)
text = text.replace(line, line[:-2].rstrip() + " `Branding` holds a look's product name, icons and splash art as bytes; "
    "the bootstrap and `ApplicationIcon` decode them. |", 1)
p.write_text(text, encoding="utf-8")
PY
grep -rln '\[defaults\]' docs/*.md
grep -rn 'branding\|plan 11' docs/*.md AGENTS.md jasper-app/README.md jasper-sdk/README.md jasper-ui/README.md
```

The first `grep` names the guide that documents the kit file's tables: `docs/plugin-authoring.md`. Its provider
paragraph says the kit file's format is spec section 5 ", except that its `[branding]` table is not accepted yet." (line
44 at `4dba6ae2a`). Delete that clause, so the sentence ends at the spec link, and add the paragraph below right after
that provider paragraph (the one ending "development and installed plugins ignore it."):

```markdown
`[branding]` (optional) brands the look's start-up and windows: `product` is the name its splash shows (1 to 32
characters), `icons` a folder in the kit's folder holding `icon-16.png`, `icon-32.png`, `icon-48.png`, `icon-64.png`,
`icon-128.png`, `icon-256.png` and `icon-1024.png` (each a PNG of that size, at most 512 KiB), and `splash_art` a
718 × 250 SVG of at most 256 KiB that follows the plugin artwork rules (no scripts, animation or outside resources).
All three keys are required. A look with it replaces Jasper's icon in its windows and on the taskbar and Dock, and
shows a splash on a start that opens a window; a look without it keeps Jasper's icon and shows none.
```

Fix every other hit of the second `grep` that says branding is not supported yet (delete the statement, AGENTS.md's
documentation rules); leave mentions that are already accurate (at `4dba6ae2a` the other hits, in `docs/README.md`,
`docs/app-maintenance.md` and `docs/new-ui-icons-verification.md`, are about Moray's rebranding or the packaged
application branding and stay).

- [ ] **Step 2: Prove the default look unchanged**

This plan touches jasper-ui only in `dev.jasper.ui.icons` (a move of private checks and two new methods). Compare the
headless gallery with `main`'s:

```bash
base=$(mktemp -d)
# main is checked out in ~/projects/moray, so the comparison tree is detached at the commit this branch sits on.
git worktree add --detach "$base/main" "$(git merge-base HEAD main)"
for theme in dark light; do
  ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme && rm -rf "$base/branch-$theme" && cp -R jasper-ui/build/gallery "$base/branch-$theme"
  (cd "$base/main" && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=$theme) && rm -rf "$base/main-$theme" && cp -R "$base/main/jasper-ui/build/gallery" "$base/main-$theme"
  diff -r "$base/main-$theme" "$base/branch-$theme" && echo "$theme: identical"
done
git worktree remove --force "$base/main"
```

Expected: `dark: identical` and `light: identical`, and no `diff` output.

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

Expected: 0 failures and 0 errors; note the four numbers for the entry below. Run the source-hygiene check of AGENTS.md
over the new and changed sources (`jasper-app/src`, `jasper-ui/src`, `plugins`); expected: no output.

- [ ] **Step 4: Write the STATUS entry and the banner**

Insert at the top of `docs/STATUS.md`, right after the `# Jasper — Status and Handoff` heading and its blank line, one
paragraph. Fill `<range>` with Task 1's commit (`git log --format=%h -1 --grep='^Branding: artwork at a caller'`)..`git log -1 --format=%h`, `<counts>` with
Step 3's line, and `<deviations>` with every deviation from this plan's text (adapted anchors and names, any constant
Task 6 changed with its measured value), or "none beyond the plan's Rulings":

```markdown
**Enterprise presentation, plan 11: branding — 2026-10-17** (`claude/enterprise-branding`, worktree
`.claude/worktrees/enterprise-11`, `<range>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-11-branding.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). A look with `[branding]` brands Jasper's
start-up and windows. `KitParser` reads the table (`product`, `icons`, `splash_art`) into `Branding`, the files' bytes,
checking each icon's PNG header size and the art with jasper-ui's receiving SVG rules on the XML alone
(`EncodedArtwork.checkSvg`, the rules moved unchanged into `SvgPreflight`), so the pre-pass touches no AWT; the look's
digest covers the files and `Presentation` carries the branding (`Branding.NONE` for the default look and the
fallback). The Enterprise plugin ships the reference icon set (16 to 1024 px) and the Boot Splash art byte for byte,
pinned by the conformance test. `ApplicationIcon.useLook` makes a branded look's icons every new window's icon list and
the taskbar and Dock icon, in packaged macOS launches too. Once the desktop properties and title-bar style are
committed, a start that will open a window (not `--background`, `--no-window`, safe mode or headless) decodes the
splash off the EDT and posts its window (`dev.jasper.app.splash`: `SplashPainter`, `Splash`, `SplashColors`) with the
Dock icon; `StartupSteps` shows "Starting Java runtime…", "Reading configuration…", "Loading plugin: <name>" per
plugin, "Restoring workspace…" and "Starting JasperOps Workstation…", filled as step over count with no motion, and
the splash closes when the first window shows or start-up fails. `BootSplashMockTest` compares the render with all ten
frames (renders under `jasper-app/build/enterprise-splash/`, three pairs in `tools/visual/pairs.json`). The default
look is unchanged (the headless gallery is byte-identical to `main`'s). Rulings (plan, "Rulings"): (1) one plan; (2)
bytes, checked without decoding; (3) `presentation → dev.jasper.ui.icons` for the art's check; (4) all three keys
required, the art's aspect unchecked; (5) the digest covers the branding; (6) `Branding` is a `Presentation`
component; (7) icons on every branded launch, About's icon follows, the tray and menu-bar glyph keep Jasper's; (8) a
look that falls back gives Jasper's icons back and closes the splash; (9) the splash opens after the desktop
properties, not at the selection; (10) step timing and count; (11) faces from the kit's files, not registered; (12)
`BuildInfo.buildLine` and `BuildInfo.COPYRIGHT`; (13) fixed colours in `SplashColors`, exempt from the scan; (14)
measured window-pixel geometry and baselines; (15) the comparison's tolerances; (16) a plain AWT window with the
look's icons, centred on the main screen; (17) 1x pairs; (18) the text compared with the frames on macOS only.
Deviations from the plan's text: <deviations>. `./gradlew check --continue`: **<counts>**. GUI
checks for the user (spec 16.2): on a cold start with Enterprise on, the splash appears centred, unfocused, with the
JasperOps art, icon, version line, steps and bar, crisp on a Retina screen, and closes as the first window shows; the
Dock icon (packaged and development builds) and the window icons on Windows and Linux are the Enterprise icon from the
splash on; no splash with `--background` (the resident's later windows still carry the icon), `--no-window` or
`--safe-mode`; a start that fails after the splash opened leaves no splash behind; the default look shows no splash and
Jasper's icon. Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-17): executed on `claude/enterprise-branding` (`<range>`).** Deviations from this plan's text: <deviations>.
```

- [ ] **Step 5: Commit**

```bash
git add docs AGENTS.md
git commit -F - <<'EOF'
Branding: documentation and status

The plugin guide documents the kit file's [branding] table; the app
architecture lists the splash package, StartupSteps and Branding. STATUS
records plan 11 with its rulings, the GUI checks for the user and the
full build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-17)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `4dba6ae2a` (`main`: plans 3a to 3e, 4a to 4c, 5a, 5b, 6a, 6b and 8a; not 7 or 9a, which run in parallel,
nor 8b, 9b, 10a or 10b). Method, without Gradle: a dry run in a scratch copy of the tree (`git archive HEAD`) applied
every edit script in order with its stated counts (all twelve held), Task 3's copies (the eight SHA-256 sums and sizes
held) and every created, replaced and appended Java file; compiled the main sources of jasper-sdk, jasper-terminal,
jasper-ui, jasper-buddy and jasper-app, and the jasper-app, jasper-ui icon and test-fixture sources, with `javac` (JBR
25, the Gradle cache's jars); and ran tests headless through the JUnit 6.1.3 launcher on this Mac (`jasper.repoRoot` set
to the copy). Every new test passes: `EncodedArtworkRasterTest` (3), `BrandingTest` (3), `KitBrandingTest` (5),
`StartupStepsTest` (5), `PluginStartingStepsTest` (1), `StartupStepsApplicationTest` (2), the two new
`ApplicationIconTest` cases, the new `BuildInfoTest` case, `SplashPainterTest` (7, with ruling 8's case),
`BootSplashMockTest` (2: `everyFrameMatchesItsMock` and, on macOS, `everyFramesTextMatchesItsMock`, all ten frames
within the stated tolerances, so no Task 6 remedy is needed at `4dba6ae2a`), the five new `ApplicationBootstrapTest`
cases and `PresentationConformanceTest.theBrandingIsTheReferenceArtwork`; `KitParserTest`, `PresentationFactsTest`,
`ProjectionsTest`, `ReceivingArtworkTest`, `BuiltInArtworkTest`, `IdentityArtworkTest`, `IconRegistryTest`,
`EnterpriseFixtureTest`, `EnterpriseKitRenderTest`, `PresentationReachTest`, `LookNoticeTest`, `PresentationAttemptTest`,
`AboutViewTest` and `ColourLiteralScanTest` pass. The only failures are the environment's: `BuildInfoTest
.theBuildWritesTheResource` (Gradle's build-info resource), `PresentationConformanceTest
.theStagedPluginIsTheSourcesAndOffByDefault` (Gradle's staged plugins), and `ThemeMigrationCoverageTest`, which named
exactly the nine files whose ledger rows Tasks 2, 5 and 6 add (the dry run did not write the rows). `javadoc
-Xdoclint:all` over the changed jasper-app packages and `-Xdoclint:all,-missing` over `dev.jasper.ui.icons` report no
error; the source-hygiene scan finds nothing. Not checked here (Gradle only): `verifyApplicationArchitecture`, the
headless gallery's byte comparison (Task 8 step 2), `stagePlugins` and `check`.

Changes made to this plan's text: the Workspace paragraph and "Before you start" (no rebase, the `4dba6ae2a` base, the
later-plans check); Verified facts 1 to 13; the Files lines of Tasks 1 to 6; Task 3 (no `jasper-app/build.gradle.kts`
edit, ruling 3); Task 4 (the `ApplicationIcon` history note); Task 6 (the README anchor, ruling 4; `displayable` and its
test, ruling 8; the count 7); Task 7 (the splash anchor, ruling 2); Task 8 (step 1 without a rebase, the
plugin-authoring edit, ruling 9; the dates and the range, ruling 10).

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 2 | `EncodedArtwork.checkSvg`, called by `KitParser.branding` on the XML alone | Agree; messages match `KitBrandingTest` | none |
| 1 → 6 | `EncodedArtwork.raster` (the splash's art); Task 6's remedies may edit `DecodedArtwork.raster`, and its commit stages `jasper-ui/.../icons` with `-u` | Agree | none |
| 2 → 3 | `Branding`, `Parsed.branding()`, `MAX_ICON_BYTES`, `MAX_ART_BYTES`; Task 2 expects `PresentationConformanceTest` green because the shipped kit has no `[branding]` until Task 3 | Agree | none |
| 2 → 5, 7 | `BrandingFixtures.plugin`, `Presentation.branding()`, `PluginSelection.presentation()` | Agree | none |
| 2 → 6 | `BrandingFixtures.png`, `svg`; `Branding.icon`, `splashArt` | Agree | none |
| 2, 5, 6 | `docs/design/theme-engine/migration.csv`, appended rows (1 + 3 + 5) | Exactly the nine files the coverage test names | 11 |
| 3 → 6 | The shipped icons, art and IBM Plex faces `BootSplashMockTest` reads through `EnterpriseResources`; the reference folders as test inputs | Agree; Task 3's input edit is redundant | 3 |
| 4 → 7 | `ApplicationIcon.useLook`, `branded`, `installTaskbarIcon`, `images`; `BuildInfo.buildLine`, `COPYRIGHT` | Agree | 13 |
| 5 → 6 | `StartupSteps.Step`, `fraction()`, `RUNTIME`, `CONFIGURATION`, `PLUGIN`, `WORKSPACE` | Agree | none |
| 5 → 7 | `StartupSteps.of`, `NONE`, `current`, `active`, `configuration`, `workspace`, `done`; `PluginSelection.loading()`; `JasperApplication.startupSteps` | Agree: the bootstrap reports configuration and workspace, the application plugins, workstation and done | 14 |
| 6 → 7 | `SplashPainter.of`, `Content`; `Splash(painter, icons)`, `open`, `show`, `close` (as `Consumer<Step>` and `Runnable`) | Agree | none |
| 5 ↔ 7 | `JasperApplication.java` (Task 5) and `ApplicationBootstrap.java` (Task 7) | Disjoint files | none |
| 6 → 8 | `tools/visual/README.md`, `pairs.json` (6); the docs and STATUS (8) | Agree | 4 |
| 2 → 8 | The plugin guide's "not accepted yet" clause | The sentence is a clause, and no `[defaults]` paragraph exists | 9 |
| 7 ↔ 5a, 6a (merged) | `prepareDesktop`: the selection moved into `start`; `applyDesktop` commits the desktop properties and the title-bar style | The quoted "Before any plugin is discovered" comment is gone | 2 |
| 3, 6 ↔ 5b (merged) | `enterprise.kit.toml` (`[colors]` then `[defaults]`), `KEYS`' comment, `EnterpriseResources`, `ColourLiteralScanTest.THEMES`, the README's compare_all bullet | Anchors hold except the README bullet; `jasper-app/build.gradle.kts` already declares the reference folder | 3, 4 |
| 1 to 8 ↔ 7, 9a (parallel) | `migration.csv`, `docs/STATUS.md`, `pairs.json`, `tools/visual/README.md`, `docs/app-architecture.md`, `JasperApplication.java`, `Presentation.java`, `enterprise.kit.toml`, `KitParserTest` | Textually local, appended edits | 11, 12 |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Agrees; anchors unchanged since `deafed424`; 3 cases and the icon suites pass | none |
| 2 | Agrees; 3 + 5 cases, `KitParserTest`'s new message and `PresentationFactsTest` pass; no other construction of `Presentation` or `Parsed` exists | none |
| 3 | The build input edit duplicated an existing input; otherwise agrees; the conformance case passes | 3 |
| 4 | Agrees; `ApplicationIcon`, `BuildInfo` and `AboutView` unchanged since `deafed424` | 13 |
| 5 | Agrees; one `@TempDir` deletion race seen once under load | 7 |
| 6 | The README anchor had moved; a step or product in a script IBM Plex lacks drew boxes; the mock comparison passes all ten frames | 4, 8 |
| 7 | The `prepareDesktop` anchor had moved | 2 |
| 8 | Step 1 rebased; the guide's anchor and the STATUS dates and range needed updating | 1, 9, 10 |

### Rulings

1. **No rebase, merge or push.** "Before you start" and Task 8 step 1 no longer run `git rebase main`; the branch sits
   on `4dba6ae2a`, and plans 7 and 9a, which run in parallel, are merged by the orchestrator. — Why: the orchestrator
   forbids rebasing, and no later plan is required. — Cost if wrong: none; a newer `main` comes in at the merge.
2. **The splash opens right after `applyDesktop`.** Plan 5a moved `PluginSelection` into `start`'s pre-pass, so the
   comment Task 7 anchored on is gone; `openSplash` now goes before the built-in icon prewarm ("Off the EDT and before
   the first window: an optional prewarm of the built-in icons."), just after `applyDesktop` commits the desktop
   properties and the title-bar style. — Why: Rulings 9 (after those, as early as possible), and the splash's window is
   posted before the prewarm runs. — Cost if wrong: the splash appears a few milliseconds later or earlier.
3. **Task 3 adds no test input.** `jasper-app/build.gradle.kts`' `tasks.test` already declares
   `docs/design/enterprise/reference` (all ten folders, `:108`) and `plugins/enterprise/src/main/resources` (`:103`).
   — Why: the two new inputs would only overlap the existing one. — Cost if wrong: if line 108 is ever removed, the
   conformance and mock tests could stay UP-TO-DATE after a reference file changes.
4. **The README bullet goes after "  workspace preview).".** Plan 5b rewrote `compare_all.py`'s bullet; that line is
   unique. — Why: same place, current text. — Cost if wrong: none.
5. **Line numbers are `4dba6ae2a`'s.** Verified facts and Files lines are refreshed; besides rulings 2 and 4 no quoted
   text changed, and `ApplicationIcon`, `EncodedArtwork`, `DecodedArtwork`, `BuildInfo` and `AboutView` are unchanged
   since `deafed424`. — Why: the scripts anchor on text, the numbers only orient. — Cost if wrong: none.
6. **The dry run stands in for the Gradle red-green steps only as evidence.** Each task still runs its stated Gradle
   commands. — Why: Gradle-only checks (architecture, gallery, staged plugins) were not run here. — Cost if wrong: none.
7. **`StartupStepsApplicationTest`'s `@TempDir` race.** Once, under load, its folder could not be deleted after
   `quitNow` (a late write, the shape `PresentationAttemptTest` shares); alone it passed three runs in a row. Per the
   machine note, re-run it alone before treating a failure as real; a failure that repeats alone is a defect to debug
   (superpowers:systematic-debugging), never to skip. — Why: the assertions passed every time. — Cost if wrong: a
   flaky test reaches `check`.
8. **The splash's product name and step take the platform face for a glyph the kit's face lacks.**
   `SplashPainter.displayable(font, text, family, style)` returns the face, or the platform's family at the same size
   when `canDisplayUpTo` finds a missing glyph; `paint` uses it for the product name (bold fallback) and the step (fit
   and drawn with the same font). `SplashPainterTest.aTextTheFaceCannotDrawTakesThePlatformFace` covers it. — Why: a
   plugin name in another script would draw boxes under IBM Plex; plan 5b's ruling 26 gives the application's own text
   the same fallback. — Cost if wrong: one helper; the frames are unaffected (all their text is in Plex, and the mock
   test passes with it).
9. **The plugin guide loses its clause and gains the paragraph.** `docs/plugin-authoring.md:44` says the kit format is
   spec section 5 ", except that its `[branding]` table is not accepted yet."; Task 8 deletes that clause and adds the
   plan's `[branding]` paragraph right after the provider paragraph (no paragraph describes `[defaults]`; it is
   mentioned in the themes paragraph). — Why: AGENTS.md's documentation rules (delete, never negate), and the provider
   paragraph is where the kit file is introduced. — Cost if wrong: the paragraph's placement.
10. **The STATUS entry and banner are dated 2026-10-17, and the range starts at Task 1's commit.** The plan's own
    commits are on the branch, so `main..HEAD | tail -1` would name the plan commit. — Why: the entry records the
    execution. — Cost if wrong: none.
11. **Shared files with plans 7 and 9a stay textually local.** Ledger rows and `pairs.json` entries are appended, the
    STATUS paragraph goes at the top, the README gains one bullet, `docs/app-architecture.md` gains one row and two
    phrases, `JasperApplication.java` gains four local insertions, `Presentation.java` changes its record header,
    compact constructor and three factories, `enterprise.kit.toml` gains a table between `[colors]` and `[defaults]`,
    and `KitParserTest` changes one case and one comment. If plan 7 or 9a lands first and an anchor moved, the script
    stops: re-find it. — Why: the orchestrator reconciles at the merge. — Cost if wrong: a merge conflict there.
12. **`Presentation.LANDED` is untouched.** `Branding` is a component, not a projection group (Rulings 6); plans 7 and
    9a add their groups to `LANDED` independently. — Why: no overlap. — Cost if wrong: none.
13. **`createApplication` keeps both `installTaskbarIcon` calls.** The existing first call stays; the plan's second
    one, after `useLook(application.presentation().branding().iconFiles())`, puts Jasper's icon back after a fallback
    (spec 4.7). On a default look's development launch it sets the same icon twice. — Why: harmless, and the fallback
    needs it. — Cost if wrong: one redundant native call per development launch.
14. **The blamed path is covered through the presentation the application holds.** `startupSteps` reads
    `presentation` after construction, which is the blamed selection's fallback (`JasperApplication.java:316-317`);
    `StartupStepsApplicationTest.aLookWithoutBrandingEndsTheStepsAtOnce` covers a presentation without branding and
    `KitBrandingTest` the fallback's `Branding.NONE`, so no test forces a kit to fail installing. — Why: the seam
    (`installers`) exists but a branded install failure adds nothing those two do not prove. — Cost if wrong: a
    regression in the blamed path is caught only by review or the GUI check.
