# Enterprise presentation 6a: the window shell Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under a native-titled look every terminal window, auxiliary window and dialog takes the operating system's
title bar, macOS menus move into each window (`apple.laf.useScreenMenuBar=false`), the classic menu composition (File,
Edit, View, Session, Transfer, plugin menus, Tools, Window, Help) and its 24 px menu row match the Workstation mocks,
NSAppearance follows the theme on every native-titled frame, the frame title follows the look's template, motion is off
whatever `ui.animations.enabled` says, and Compact keeps the in-window menu bar; the JUnit chrome renders of spec 16.1.4
start here, with the menu row compared against the Workstation and Dark Mode mocks.

**Architecture:** Plan 5a computed every projection but held the window-chrome group at the default projection
(`Presentation.LANDED` empty). This plan splits that group in two, lands `WINDOW_CHROME` (desktop flags, menus, shell,
motion, title) and leaves `WINDOW_CONTENT` (header, tabs, status, run item, dialogs) to plan 6b. The bootstrap applies
`DesktopFlags`; `dev.jasper.app.workspace.WorkspaceChrome.of` is the one reader of the `WorkspaceLook` variant enums and
picks the `MenuBarLayout` (`StandardMenuBar` or `ClassicMenuBar`); `dev.jasper.app.windows.ShellChrome.of` turns
`ShellLook` into the values `NativeShells` reads; `WindowLook` carries both to a terminal window; the application's one
animations supplier (`LookMotion`) folds `Motion` into `ui.animations.enabled`; `WindowTitle` fills the `TitleFormat`
template. The default look's composition, menus and pixels are unchanged.

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3 and AssertJ
3.27.7, Python 3.11+ standard library and Pillow for the edit scripts and measurements (always `python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 9.1, 9.2, 10.1, 10.2, 10.4, 10.9, 10.10, 16.1.4, 16.1.7, 16.2, 17 and row 6 of 19.1). Spec row 6 is
delivered as two plans: this one (6a, the shell: desktop flags, native titles, in-window and classic menus, NSAppearance
and captions, motion, the title template, Compact, the render harness) and
[6b](2026-10-16-enterprise-6b-window-chrome-content.md) (the classic header with toolbar views, ruled status cells and
the "▶ Run…" item, classic tabs, the classic in-window dialogs, the Workstation and Credential Vault comparisons). The
reference mocks are [`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (2× PNGs; the mock wins on
layout, DESIGN_LANGUAGE.md on intent; names, counts and hosts are fixtures).

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-6`, branch
`claude/enterprise-window-chrome`, cut from `main` at `b9178db45` (plans 3a to 3e, 4a and 5a) and holding the two plan
files. Run every command from the worktree root, never in `~/projects/moray`. Line numbers below are from `b9178db45`
(re-checked on 2026-10-16, "Execution notes"); every edit quotes the exact text it replaces and the edit scripts stop
with a message when that text is not found the stated number of times. If a script stops, re-find the
quoted text (it moves, it does not change), adjust the anchor and re-run; never edit around a missing anchor, and record
any re-quoted anchor in the status banner. Plan 6b executes on the same branch after this plan.

## Before you start

Do not rebase or merge: the branch already holds `main` at `b9178db45`. Check that the plans this one builds on are in it:

```bash
grep -c 'TRANSFER' jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java                         # 3d: at least 1
grep -c 'record View(' jasper-app/src/main/java/dev/jasper/app/contributions/ToolbarEntry.java          # 3d: 1
grep -c 'OPEN_FILE' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java                      # 3e: at least 1
grep -c 'DOCK_RULE(1), DOCK_RULE_HIT(6);' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java   # 4a: 1 (the enum's last roles)
grep -c 'Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java                                # 4a: 1
grep -c 'public static Kit enterprise()' jasper-ui/src/testFixtures/java/dev/jasper/ui/testsupport/KitFixtures.java   # 4a: 1
ls jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java   # 5a
grep -c 'Set<Landed> LANDED = Set.of();' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java   # 5a: 1
```

Each `grep -c` must print at least 1 and each `ls` must list its files; otherwise stop. If the last check prints 0
because a later plan (7 to 9) already landed its group, keep its constants in every edit below that touches `LANDED`
or `Landed`. Plans 4b, 4c and 5b are not on the branch, and this plan does not wait for them (Execution notes, Rulings
13 to 17): the chrome tests use plan 4a's Enterprise fixture kit and a fixture theme of spec 7.6's named roles
(`EnterpriseLook`, Task 4) instead of 5b's shipped kit, Plex and theme; Task 9's scan is a test of its own,
`ProjectionReadTest`, instead of an addition to 4b's `VariantReadTest`; Task 8 checks its pairs with `compare_all.py`
until 4c's `check_pairs.py` lands. Plans 4b and 8a run in parallel in other worktrees and also touch `DesignMetrics`,
`compact-roles.tsv` (8a) and their own scan tests (4b); this plan's additions there stay appended and textually local,
and are reconciled at merge.

**Earlier plans' names.** This plan uses the names below, read from the earlier plans as written. Before Task 1 read
the merged code; where a name differs, change it in this plan's code blocks before executing (a renamed member is one
search and replace; a different shape means adapting the call without changing behaviour) and record it in the banner.
Every name below was checked at `b9178db45`; the table says where a name is absent and what replaces it.

| This plan uses | Source |
|---|---|
| `Presentation(Optional<Provider>, Kit, ChromeSpec, boolean, KitParser.Faces, DefaultSource, Projections)`, `Presentation.Landed`, `LANDED`, `committedDesktop()`, `projections()`, `standard(Projections.Host)`, `fallback(Projections.Host)` | 5a Task 8 (checked; 5b's notices list is not on the branch and nothing here uses it) |
| `Projections(DesktopFlags desktop, WorkspaceLook workspace, ShellLook shell, PaletteLook palette, SettingsChrome settings, Composition composition, DialogMapping dialogs, Motion motion, TitleFormat title, DockLayoutKey dockLayout, AppPanels panels)`, `Projections.of(ChromeSpec, boolean, boolean, String, Host, Set<Landed>)`, `Projections.fallback(DesktopFlags, Host)`, `Projections.Host(boolean mac, boolean unifiedTitleSupported)` | 5a Task 8 (checked) |
| `DesktopFlags(boolean screenMenuBar, boolean nativeTitle)` with `STANDARD`; `WorkspaceLook(Menus, Header, Stripes, Tabs, Status, boolean runItem, FrameStyle)` and its nested enums; `ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance)`; `enum Motion { STANDARD, NONE }`; `TitleFormat(String template)`; `DialogMapping(Kind, boolean windowTitled)` | 5a Task 8 (checked) |
| `ChromeSpec(TitleBar, MenuBar, String windowTitle, ToolWindows, EditorTabs, StatusBar, PaletteKind, DialogKind, MotionKind)` with `DEFAULT`, `CLASSIC` (whose title is `{context}`) and the nested enums | 5a Task 3 (checked) |
| `ApplicationBootstrap.Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log, PluginSelection selection)`; `PluginSelection.presentation()`; `JasperApplication.presentation()` (set before the `ConfigurationController` is built) | 5a Task 9 (checked) |
| `ThemeController.startup(boolean dark, ThemeSelection selection, Kit kit, Consumer<InstallationFailure> fallingBack)`; `ThemeController.kit()` | 5a Tasks 10, 11 (checked) |
| `EnterpriseResources.ROOT`, `RESOURCES`, `facts()`, `theme()` | 5b Task 4: **not on the branch.** Task 4's `EnterpriseLook` takes 4a's `KitFixtures.enterprise()`, spec 5.1's chrome table and a checked-in fixture theme; `MockProfile` takes the repository root from `jasper.repoRoot` (Rulings 13, 14) |
| `KitFixtures.complement()`, `KitFixtures.enterprise()` (the complement and Enterprise fixture kits) | 4a Task 6 (checked) |
| `DesignMetrics.Role.GROUP_ROW_INDENT(14)` as the enum's last role | 4c Task 1: **not on the branch.** The enum ends with 4a's `DOCK_RULE(1), DOCK_RULE_HIT(6);`, and 4a's `NewRolesTest.theAddedRolesFollowEachOtherInOrder` already checks a run, not the tail (Rulings 15) |
| `VariantReadTest` and `StrategyIsolationTest.Scanned` | 4b Task 0: **not on the branch.** Task 9 creates `ProjectionReadTest` with its own constant-pool scan (Rulings 16) |
| `tools/visual/check_pairs.py`, `EnterpriseGalleryTest` | 4c: **not on the branch.** Task 8 runs `compare_all.py` (Rulings 17) |
| `ToolbarEntry.View`, `WindowChrome.windowOpened()`, `MenuTarget.Slot.SESSION`, `TRANSFER` | 3d Tasks 1 and 4 (checked) |
| `ActionId.OPEN_FILE` in File after New Window | 3e Task 2 (checked) |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task here runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. The headless gallery (`:jasper-ui:gallery`) and headless JUnit renders (`java.awt.headless=true`, which jasper-app's test task sets) may be run.
- Work only in the worktree above on `claude/enterprise-window-chrome`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. Every PNG and JSON the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base (Task 9 proves it); every reference pair in `tools/visual/pairs.json` and every calibration suite stays green unchanged. No existing render may change: under the default projection `WorkspaceChrome.STANDARD` builds today's menus in today's order, `TitleFormat("{context}")` gives today's title, `LookMotion(STANDARD)` passes the setting through, and `ShellChrome.standard()` reads `MacTitleBar.isSupported()` as today. The new metric roles (Task 3) are read only by classic chrome.
- Spec 9.1 table, exactly: `DesktopFlags.screenMenuBar` false when `menu_bar = "window"`, `nativeTitle` true when `title_bar = "native"`; `WorkspaceLook.menus` CLASSIC when `menu_bar = "window"`; `ShellLook.unifiedTitle` true only when `title_bar = "unified"` and `MacTitleBar.isSupported()`; `ShellLook.windowMenus` true when `menu_bar = "window"`; `ShellLook.followThemeAppearance` true when `title_bar = "native"`; `Motion` NONE when `motion = "none"`; `TitleFormat` is `window_title`.
- Spec 10.2: `StandardMenuBar` is File, Edit, View, Pane, Tab, Session, Transfer, Tools, plugin menus, Help; `ClassicMenuBar` is File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help, with Window holding the Pane items and the Tab items as two sections, then the tool-window toggles; Help holds About Jasper on every platform, macOS included under in-window menus; each built-in menu with no items of its own shows only while it has entries.
- Strategies at the owner (spec 3.3, 16.1.7): `WorkspaceChrome.of` is the only reader of the `WorkspaceLook` variant enums, `ShellChrome.of` of `ShellLook`, and `LookMotion` of `Motion`; `ProjectionReadTest` enforces it (Task 9). No other class branches on a look.
- The architecture edges of spec 9.2: `workspace`, `windows` and `application` → `presentation` (5a added `application`); `dev.jasper.app.platform` gains no edge (`TitleBarStyle` takes a boolean); `dev.jasper.app.dock` stays JDK-only (`verifyApplicationArchitecture`).
- Measured mock targets are halved 2× pixels (Verified facts, "Mock measurements"); a render compares at 2× against the mock with the stated tolerances. Colours are the Enterprise theme's roles; no colour literal enters production code (`ColourLiteralScanTest`).
- Javadoc doclint fails the build on errors: jasper-app runs `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` resolves; no raw `<`, `>`, `&` or `->` in a comment. New public types carry a class comment, record components an `@param`.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app, the SDK, the testkit or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches (Swing, AWT, FlatLaf or `dev.jasper.ui` imports, `new J…`-style names, the word appearance in any case) gets its row in the same commit, in the form `<path>,app.<package>,<Class>,nonvisual-helper,none,none,<evidence>,<disposition>` with a disposition beginning "New in Enterprise presentation Plan 6a Task N" ("Test/fixture ownership; New in …" for a test). A row has exactly eight comma-separated fields, so the disposition uses semicolons. Each task lists its rows; if `ThemeMigrationCoverageTest` names another file of the task, add its row in the same form.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; the em dash, ellipsis, ▶, ▼, × and arrows in copy are ordinary characters. The identifier word "hero" stays reserved for `Form.hero` (`PromptVocabularyTest`).

## Review Focus

1. A user on macOS turns Enterprise on and restarts: the first window has the operating system's title bar, the menus in
   a row inside the window (the screen menu bar shows only the application menu), Close Window still answers ⌘W in a
   plugin window, and About Jasper is in Help as well as in the application menu. Task 2:
   `DesktopFlagsTest.aNativeTitledLookTurnsTheScreenMenuBarOffBeforeAwt`; Task 5:
   `WindowShellTest.aNativeTitledWindowMountsItsMenusInTheWindow` and `AboutWindowsTest` (Help on macOS under window menus).
2. A plugin that registers a top-level menu after Help exists (a development plugin reloaded, or a plugin whose menu
   section is added late): under the classic layout Help still comes last, after Tools and Window; under the standard
   layout the bar keeps today's registration order. Task 4:
   `WindowChromeMenusTest.theClassicBarKeepsHelpLastWhateverTheRegistrationOrder` and
   `theStandardBarKeepsTodaysOrder`.
3. A remote pane that has not reported its directory yet (or a hosted editor document) under the template
   `Workstation — {session} — {directory}`: the empty token disappears with its separator, so the title never reads
   "Workstation — Mac — " or "Workstation —  — ~". Task 7: `WindowTitleTest.anEmptyTokenGoesWithItsSeparator` and
   `theTokensComeFromThePaneOrTheDocument`.
4. A user who switched animations on in an earlier session starts Enterprise: no tab, palette or overlay moves, and the
   setting is kept (turning Enterprise off brings the motion back); with the default look the setting still decides.
   Task 6: `LookMotionTest` (both cases) and `MotionReachTest.noMotionWinsOverTheSettingAndTheSettingStaysSaved`.
5. Compact view under Enterprise on macOS: the toolbar row goes but the menu row stays (with the screen menu bar off
   it is the only menu surface). Task 5: `WindowShellTest.compactUnderANativeTitleKeepsTheMenuRow`.

## Verified facts

At `b9178db45` (re-checked on 2026-10-16) unless marked.

1. `ApplicationBootstrap.prepareDesktop` calls `configureDesktopProperties()` and then
   `TitleBarStyle.choose(mac, nativeDecorations, compact).configure()` (`ApplicationBootstrap.java:100-104`);
   `configureDesktopProperties` sets `apple.laf.useScreenMenuBar` to `"true"` (`:126-132`); plan 5a Task 9 gives
   `prepareDesktop` its `Startup` with `selection()` and keeps both calls. `ApplicationBootstrapTest` calls the no-argument
   `configureDesktopProperties()` (`ApplicationBootstrapTest.java:64`, `:245`, `:253`).
2. `TitleBarStyle.choose(macOs, nativeDecorations, compact)` returns NATIVE for Compact, else MAC_UNIFIED on macOS, else
   NATIVE or CUSTOM by `nativeDecorations` (`TitleBarStyle.java:16-23`); `configure` sets FlatLaf's window decorations for
   CUSTOM only and leaves macOS alone (`:26-32`); `TitleBarStyleTest` covers both (`TitleBarStyleTest.java:10-26`).
3. Four call sites read `MacTitleBar.isSupported()`: `TerminalWindow.java:66` and `NativeShells.java:146,227,420`.
   `TerminalWindow` installs the title bar, falls back to `installCustomTitleBar`, and puts the menu bar on the unified
   bar or `frame.setJMenuBar` (`TerminalWindow.java:66-70`). A `MacTitleBar` is null when unsupported
   (`MacTitleBar.java:72-74`).
4. `WindowContent.installTitleBar(root, content, supported, nativeTitle)` mounts the content, moves the header into a
   unified bar, wires `onTitle` from `WindowHeaderContext` (`WindowContent.java:302-340`) and sets NSAppearance from the
   theme only for Compact on macOS (`:331-335`); `installCustomTitleBar` decorates Windows and Linux frames once FlatLaf
   decorates them (`:349-363`). `initializeViewMode(COMPACT)` removes the header (`:741`).
5. `NativeShells.frame` puts the minimal menu bar on the unified bar or `frame.setJMenuBar` (`NativeShells.java:146-148`)
   and subscribes to theme changes (`:160-164`); `dialog` installs chrome the same way (`:227`, `:241-245`); the minimal
   menu is New Window, Close Window (⌘W) and, off macOS, Quit (`:496-514`). JBR's API (`jbr-api` 1.9.0,
   `com.jetbrains.WindowDecorations`) offers only custom title bars (`setCustomTitleBar`, `CustomTitleBar`); it has no
   dark-caption call for a title bar the system draws (checked with `javap` on 2026-10-16).
6. `WindowChrome` builds File, Edit, View, Pane and Tab and adds them to `CollapsingMenuBar` in that order
   (`WindowChrome.java:79-99`); `rebuildContributedMenus` (`:320-361`) builds Session, Transfer and Tools fresh each pass and
   adds them, then every TOP_LEVEL section in registration order. The app's Help is a TOP_LEVEL section with
   key `app.help` added after plugins start (`JasperApplication.java:1045`), so today it is last. `AboutWindows.helpEntries(macOs)`
   leaves About out on macOS (`AboutWindows.java:70-80`).
7. `Contributions` keeps one VIEW section, `panelsMenu`, holding a "Tool Windows" submenu of every panel's toggle action
   (`Contributions.java:255-265`); `panels()` lists the panels and `PanelEntry.toggleActionId()` names each toggle
   (`Contributions.java:162`, `PanelEntry.java:37`).
8. `NativeStyles.menu` styles every menu and item with the BODY face and an accent selection with `text.onAccent`
   (`NativeStyles.java:146-156`). FlatLaf 3.7's `FlatMenuBarUI` styles `itemMargins`, `selectionInsets`, `selectionArc`,
   `hoverBackground`, `selectionBackground` and `selectionForeground` through `FlatLaf.style`
   (`FlatMenuBarUI.java:73-84`), and `FlatMenuItemBorder` gives top-level menus the bar's `itemMargins`
   (`FlatMenuItemBorder.java:39-49`); a hovered, closed top-level menu has no foreground style.
9. Animations: `ThemeController.configureAnimations(boolean)` and `animationsEnabled()` (`ThemeController.java:26-28`)
   feed `AppearanceBindings` (`:166`) and `PluginRuntime.animations` (`JasperApplication.java:957`);
   `ConfigurationController.configureThemes` sets it from the snapshot (`ConfigurationController.java:273`); windows take
   `AnimationSettings` through `WorkspaceConfiguration.apply` (`WorkspaceConfiguration.java:19`), which `WindowTabs`
   (`WindowTabs.java:81`) and `WindowCommandPalette` (`WindowCommandPalette.java:138`) read.
10. `WindowHeaderContext.from(pane, home, programTitle, command)` is today's label (`WindowHeaderContext.java:8-34`); a
    provider pane names its saved identity in `PaneSnapshot.shell()`, a remote one its OSC host in `remoteDirectory()`
    (`:15-21`); the selected content gives `title()`, `host()` and `location()` (`WindowContent.java:316-325`).
11. `DesktopTestSupport.content(...)` builds `WindowContent`s for tests (`DesktopTestSupport.java:53-79`);
    `MockUiPreview` mounts a window on a bare `JRootPane` through `installTitleBar` and prints it at 2× (`MockUiPreview.java:76-102`).
12. Plan 4a appends its roles after `HISTORY_CONTENT_GAP(2)`, ending the enum with `DOCK_RULE(1), DOCK_RULE_HIT(6);`
    (`DesignMetrics.java:136`), and their rows end `compact-roles.tsv` and `classic-roles.tsv` (`DOCK_RULE_HIT\t6`
    last). 4a's final `NewRolesTest.theAddedRolesFollowEachOtherInOrder` checks plan 4's roles as one run
    (`Collections.indexOfSubList`), not as the tail; `ClassicCatalogTest` freezes `classic-roles.tsv` whole and reads
    `NewRolesTest.ADDED`. Plan 4c (not merged) appends `GROUP_ROW_INDENT(14)` at the same place, and plan 8a (running in
    parallel) appends its roles there too.
13. Plans 4b, 4c and 5b are not merged: there is no `VariantReadTest`, `StrategyIsolationTest`, `KitFaces`,
    `tools/visual/check_pairs.py`, `EnterpriseGalleryTest`, `plugins/enterprise`, IBM Plex file or
    `EnterpriseResources`. 4a's `KitFixtures.enterprise()` is spec 16.1.3's Enterprise fixture kit: the classic base with
    the kit file's tokens, a 12 px body and the platform faces. `JasperTheme.parse(json, id)` reads a theme whose
    variants list spec 7.6's override keys beside the roles (`JasperTheme.java:135`, `DERIVED_OVERRIDES`).
14. The `workspace` and `windows` package-info files do not list `dev.jasper.app.presentation` (5a added `application`
    and `bootstrap` only). `verifyApplicationArchitecture` checks that the app's package graph is acyclic
    (`gradle/application-architecture.gradle.kts`), and `presentation` depends on neither package.
15. A window built with a `ThemeController` and still open when that controller closes and another theme installs
    (`ThemeTestSupport.controller`) refreshes its kit views through the closed binding: the EDT logs
    `IllegalStateException: Appearance binding is closed` (seen in the dry run of `theClassicMenuRowIsTheMocksRow` as
    first written). Closing the window first avoids it (`DesktopTestSupport.closeOwnersNow`, Task 4).

### Mock measurements

Measured with Pillow at the mocks' 2× pixels (`docs/design/enterprise/reference/01 Workstation/Workstation - Connected
(plugins in use).png` and `08 Dark Mode/Workstation Dark - Connected (plugins in use).png`, 2884 × 1804) and halved.
Window content starts at x = 2, y = 60 (2×): a 1 px outer border, then the native title bar.

| Measured | Light | Dark | Logical target | Owner |
|---|---|---|---|---|
| Native title bar | y 2-57 `#E1E3E6`, rule y 58-59 `#C4C7CC` | `#36393E`, `#2A2C30` | 28 + 1, drawn by the system | the OS |
| Menu row | y 60-107 `#ECEDEF` | `#3C3F44` | 24 tall on `surface.app` | `ClassicMenuBar` |
| Rule under the menu row | y 108-109 `#D3D6DA` | `#4A4D53` | 1 px `border.divider` | `ClassicMenuBar` |
| Menu label starts (ink, 2×) | 32, 107, 184, 272, 391, 516, 610, 734 | same | first at 15 from the content edge; 36 to 39 (2×) from one title's ink to the next, 19 | `MENU_BAR_SIDE` 5 + `MENU_BAR_ITEM_PADDING` 9 and the glyph's bearing |
| Menu label ink | `#1E1F22`, cap 74-92 (2×) | `#DFE1E5` | `text.primary`, 12 px UI face, cap top 7 below the row top | BODY |
| Toolbar row (plan 6b) | y 110-177 `#ECEDEF`, rule 178-179 `#C4C7CC` | `#3C3F44`, `#2A2C30` | 34 + 1 px `border.window` | 6b |

The Dark row's divider (`#4A4D53`) is lighter than its surface, which `border.divider`'s recipe does not give in Enterprise
Dark (`mix(border.window′, surface.card, 73 %)` ≈ `#2B2D31`); plan 6b makes `border.divider` overridable and sets it
(Rulings 9). Until 6b lands, the Dark menu-row test compares the row and the labels and records the rule as a measured
residual.

The label row was re-measured on 2026-10-16 with `MockProfile.inks` (ΔE 25 from the row colour, columns 12 apart join):
the gaps between titles' ink are 39, 38, 37, 38, 36, 38 and 39 at 2×, which items padded 10 on each side cannot give
(2 × 10 plus the two glyphs' bearings is at least 41 at 2×). Side 5 and padding 9 put the first title's ink at 15 and,
under the platform face, every title start within 3 px (2×) of the mock and every width within 4 (Execution notes,
Rulings 18).

## Rulings

Decisions where the spec is silent or a literal reading cannot be built; record each in `docs/STATUS.md` (Task 9) and in
this plan's status banner.

1. **Spec row 6 is two plans** (6a shell, 6b content) — the row holds a dozen surfaces and the mock comparisons; one plan
   would pass 14 tasks — cost if wrong: one more STATUS entry.
2. **The window-chrome group splits in two.** `Presentation.Landed` gains `WINDOW_CONTENT` (header, tabs, status, run
   item, dialogs), which plan 6b lands; this plan lands `WINDOW_CHROME` (desktop flags, menus, shell, motion, title). A
   native-titled look with the content group still at the default projection is spec 9.1's fallback row ("UNIFIED, as a
   row under the native title"), so every intermediate build is coherent — cost if wrong: one enum constant.
3. **Help is the TOP_LEVEL section keyed `MenuTarget.HELP` (`app.help`).** `ClassicMenuBar` places it last;
   `StandardMenuBar` keeps today's registration order (Help is registered after plugins start, so it is last today) —
   cost if wrong: a plugin menu added later would sit after Help in the default look, as it does today.
4. **The classic Window menu takes the tool-window toggles and View loses its "Tool Windows" submenu** (spec 10.2 puts
   the toggles in Window; showing them twice adds nothing). `Contributions.toolWindowsSection()` identifies the section;
   PANE and TAB contributed sections join Window's two sections (spec 8.3) — cost if wrong: one condition.
5. **Menu-row metrics are new catalog roles with the same value in both catalogs** (`MENU_BAR_SIDE` 5,
   `MENU_BAR_ITEM_PADDING` 9, `MENU_BAR_HEIGHT` 24), read only by `ClassicMenuBar`, following plan 4a's Rulings 6; the
   vertical item margins are computed from the menu face's line height so the row is exactly 24 at any font — cost if
   wrong: one role value.
6. **An open classic menu's title is the accent with `text.onAccent`, square; a hovered closed title shows
   `surface.control.hover`.** FlatLaf colours only the background of a hovered closed title (fact 8), so "highlight solid
   blue with white text on hover" (DESIGN_LANGUAGE §5) is read as the open menu — cost if wrong: a UI-delegate subclass.
7. **About Jasper is in Help whenever the screen menu bar is off on macOS** and stays in the application menu too
   (macOS shows that menu even without a screen menu bar) — cost if wrong: one duplicate entry.
8. **Windows and Linux captions follow the operating system.** JBR exposes no dark-caption hint for a title bar the
   system draws (fact 5), so spec 10.1's documentation branch applies: `docs/app-architecture.md` records it. On macOS
   every native-titled frame and dialog follows the theme: terminal frames (`WindowContent.mount`); auxiliary frames and
   dialogs, the file chooser and its replace and new-folder sheets (`NativeShells.sheet`, which takes the appearance when
   it opens, as it lives for one question) through `ShellChrome.followTheme`; and a window's configuration diagnostics
   option pane through the window's own flag (the default look keeps `JOptionPane.showMessageDialog`) — cost if wrong:
   none; a later JBR call is one line in `ShellChrome`.
9. **The Dark divider.** Recorded as a residual here and fixed by plan 6b's override key (6b Rulings 3) — cost if wrong:
   the Dark menu row's rule is darker than the mock until 6b.
10. **`{session}` and `{directory}`.** A provider pane's session is its saved identity (`PaneSnapshot.shell()`), a remote
    one's its OSC host, a local one's "Local: <shell>"; a remote pane's directory is the reported path as it is (Jasper does
    not know the remote home), a local one's uses `~`; a hosted document gives its title and location. Separators are the
    literal text between two tokens; a dropped token takes the separator before it (after it for the first token), and a
    template whose tokens are all empty loses its trailing separator — cost if wrong: a different title string.
11. **Motion's one supplier is `ThemeController`'s flag**, which `LookMotion.allows(setting)` sets; `WindowTabs` and the
    palette now read it as well as their settings (both must allow), so every consumer reads the one policy — cost if
    wrong: none for the default look, where both are the setting.
12. **The render harness compares profiles, not edge maps.** `MockProfile` reads colour runs along a column or row and
    clusters of ink along a row in the mock and the 2× render and compares lengths, positions and colours within stated
    tolerances (±2 px at 2× for lengths and positions, ΔE76 ≤ 2.5 for theme colours); the opt-in `pairs.json` crops are
    added too (spec 16.1.4) — cost if wrong: tolerances retuned in one place.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/presentation/`: `Presentation.java` (`WINDOW_CONTENT`, `LANDED`), `Projections.java` (the split gate).
- `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java` (desktop flags); `platform/TitleBarStyle.java` (`nativeTitle`).
- `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` and `compact-roles.tsv`, `classic-roles.tsv` (the chrome roles of 6a and 6b).
- `jasper-app/src/main/java/dev/jasper/app/workspace/`: new `WorkspaceChrome.java`, `MenuBarLayout.java`, `StandardMenuBar.java`,
  `ClassicMenuBar.java`, `WindowLook.java`, `WindowTitle.java`; `WindowChrome.java` (menus through the layout),
  `WindowContent.java` (the look, `mount`, NSAppearance, the title), `TerminalWindow.java`, `WindowTabs.java`,
  `WindowCommandPalette.java` (motion), `package-info.java` (the `presentation` edge).
- `jasper-app/src/main/java/dev/jasper/app/windows/`: new `ShellChrome.java`; `NativeShells.java`; `package-info.java` (the `presentation` edge).
- `jasper-app/src/main/java/dev/jasper/app/contributions/`: `Contributions.java` (`toolWindowsSection`), `MenuTarget.java` (`HELP`).
- `jasper-app/src/main/java/dev/jasper/app/application/`: new `LookMotion.java`; `JasperApplication.java`, `ConfigurationController.java`, `AboutWindows.java`.
- Tests (new): `presentation/ProjectionsTest` (edited), `bootstrap/DesktopFlagsTest`, `workspace/WindowChromeMenusTest`,
  `workspace/WindowShellTest`, `windows/ShellChromeTest`, `application/LookMotionTest`, `application/MotionReachTest`,
  `workspace/WindowTitleTest`, `workspace/ChromeRenderTest`, `workspace/EnterpriseMenuRowMockTest`,
  `architecture/ProjectionReadTest`, and `jasper-ui`'s `metrics/ChromeRolesTest`; test support `testsupport/EnterpriseLook`,
  `testsupport/MockProfile`; resources `jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json`
  and `jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv`.

---

### Task 1: The window-chrome gate

`Presentation.Landed` gains `WINDOW_CONTENT` for plan 6b's header, tabs, status, run item and dialog fields, and
`WINDOW_CHROME` lands, so the desktop flags, menus, shell, motion and title template take the kit file's values from now
on (Rulings 2). Nothing consumes the new values yet; Tasks 2 to 7 add the consumers.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (the `Landed` enum and `LANDED`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java` (`of`)
- Test: `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`

**Interfaces:**
- Consumes: 5a Task 8's `Projections.of`, `Presentation.LANDED`, `committedDesktop()`.
- Produces: `Presentation.Landed.WINDOW_CONTENT`; `Presentation.LANDED = Set.of(Landed.WINDOW_CHROME)`; under
  `WINDOW_CHROME` alone a native-titled kit gives `WorkspaceLook.Header.UNIFIED`, `Tabs.EQUAL`, `Status.SLASH`,
  `runItem` false and `DialogMapping.Kind.AS_REQUESTED` (with `windowTitled` true), and `Menus.CLASSIC`, the shell, motion
  and title from the file; `committedDesktop()` returns the file's flags.

- [ ] **Step 1: Write the failing tests**

Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 6a Task 1 step 1: the split window-chrome gate in ProjectionsTest."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java"
text = pathlib.Path(T).read_text(encoding="utf-8")
start = text.index("    @Test void untilItsPlanLandsAChromeValueMapsToTheDefaultProjection() {")
end = text.index("    @Test void theFallbackIsSpecFourSevensValue() {")
pathlib.Path(T).write_text(text[:start] + '''    @Test void untilItsPlanLandsAChromeValueMapsToTheDefaultProjection() {
        assertThat(Presentation.LANDED).as("plan 6a lands the window chrome; 6b, 7, 8 and 9 add theirs")
            .contains(Presentation.Landed.WINDOW_CHROME).doesNotContain(Presentation.Landed.WINDOW_CONTENT);
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.WINDOW_CHROME));
        assertThat(shipped.desktop()).isEqualTo(new DesktopFlags(false, true));
        assertThat(shipped.workspace().menus()).isEqualTo(WorkspaceLook.Menus.CLASSIC);
        assertThat(shipped.shell()).isEqualTo(new ShellLook(false, true, true));
        assertThat(shipped.motion()).isEqualTo(Motion.NONE);
        assertThat(shipped.title()).isEqualTo(new TitleFormat("{context}"));
        assertThat(shipped.workspace().header()).as("spec 9.1's fallback row: the unified header under the native title")
            .isEqualTo(WorkspaceLook.Header.UNIFIED);
        assertThat(shipped.workspace().tabs()).isEqualTo(WorkspaceLook.Tabs.EQUAL);
        assertThat(shipped.workspace().status()).isEqualTo(WorkspaceLook.Status.SLASH);
        assertThat(shipped.workspace().runItem()).isFalse();
        assertThat(shipped.dialogs()).as("dialogs are window content; the native title still titles them")
            .isEqualTo(new DialogMapping(DialogMapping.Kind.AS_REQUESTED, true));
        var content = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC,
            EnumSet.of(Presentation.Landed.WINDOW_CHROME, Presentation.Landed.WINDOW_CONTENT));
        assertThat(content.workspace().header()).isEqualTo(WorkspaceLook.Header.CLASSIC);
        assertThat(content.workspace().tabs()).isEqualTo(WorkspaceLook.Tabs.CLASSIC);
        assertThat(content.workspace().status()).isEqualTo(WorkspaceLook.Status.RULED);
        assertThat(content.workspace().runItem()).isTrue();
        assertThat(content.dialogs()).isEqualTo(new DialogMapping(DialogMapping.Kind.CLASSIC, true));
        var none = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Set.of());
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Set.of());
        assertThat(none.desktop()).as("always computed from the file").isEqualTo(new DesktopFlags(false, true));
        assertThat(new Projections(standard.desktop(), none.workspace(), none.shell(), none.palette(), none.settings(),
            none.composition(), none.dialogs(), none.motion(), none.title(), none.dockLayout(), none.panels())).isEqualTo(standard);
        var windowOnly = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, EnumSet.of(Presentation.Landed.WINDOW_CHROME));
        assertThat(windowOnly.workspace().stripes()).as("the dock group has not landed").isEqualTo(WorkspaceLook.Stripes.RAILS);
    }

''' + text[end:], encoding="utf-8")
edit(T, [
    ('''        assertThat(look.committedDesktop()).as("nothing is applied before plan 6").isEqualTo(DesktopFlags.STANDARD);''',
     '''        assertThat(look.committedDesktop()).as("plan 6a applies the look's flags").isEqualTo(new DesktopFlags(false, true));''', 1),
    ('''        assertThat(fallback.projections()).isEqualTo(Projections.fallback(DesktopFlags.STANDARD, MAC));''',
     '''        assertThat(fallback.projections()).isEqualTo(Projections.fallback(new DesktopFlags(false, true), MAC));''', 1)])
PY
```

The second edit's anchors are 5a Task 8's `thePresentationOfAKitCarriesItAndFallsBackToTheDefaultKitKeepingItsDefaults`,
whose parsed kit (`KitParserTest.FULL`) lists `title_bar = "native"` and `menu_bar = "window"`; if that fixture names
other values, use the flags it gives.

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.ProjectionsTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … WINDOW_CONTENT".

- [ ] **Step 3: Split the gate and land the window chrome**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 1 step 3: WINDOW_CONTENT, and WINDOW_CHROME landed."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
P = "jasper-app/src/main/java/dev/jasper/app/presentation/"
edit(P + "Presentation.java", [
    ('''    /** The groups of projections whose plans consume them (spec 19.1 rows 6 to 9). */
    public enum Landed { WINDOW_CHROME, DOCK_CHROME, RUN_WINDOW, SETTINGS }''',
     '''    /**
     * The groups of projections whose plans consume them (spec 19.1 rows 6 to 9): the window shell (desktop flags,
     * menus, shell, motion, title), the window content (header, tabs, status, run item, dialogs), the dock, the Run window
     * and Settings.
     */
    public enum Landed { WINDOW_CHROME, WINDOW_CONTENT, DOCK_CHROME, RUN_WINDOW, SETTINGS }''', 1),
    ('''    /** The groups that have landed: none yet, so every chrome value maps to the default projection. */
    public static final Set<Landed> LANDED = Set.of();''',
     '''    /** The groups that have landed; every other group's values map to the default projection. */
    public static final Set<Landed> LANDED = Set.of(Landed.WINDOW_CHROME);''', 1)])
edit(P + "Projections.java", [
    ('''        ChromeSpec window = landed.contains(Presentation.Landed.WINDOW_CHROME) ? chrome : ChromeSpec.DEFAULT;''',
     '''        ChromeSpec window = landed.contains(Presentation.Landed.WINDOW_CHROME) ? chrome : ChromeSpec.DEFAULT;
        // The header, tabs, status, run item and dialogs land with the window content (plan 6b).
        ChromeSpec content = landed.contains(Presentation.Landed.WINDOW_CONTENT) ? window : ChromeSpec.DEFAULT;''', 1),
    ('''            nativeTitle ? WorkspaceLook.Header.CLASSIC : WorkspaceLook.Header.UNIFIED,''',
     '''            content.titleBar() == ChromeSpec.TitleBar.NATIVE ? WorkspaceLook.Header.CLASSIC : WorkspaceLook.Header.UNIFIED,''', 1),
    ('''            window.editorTabs() == ChromeSpec.EditorTabs.CLASSIC ? WorkspaceLook.Tabs.CLASSIC : WorkspaceLook.Tabs.EQUAL,
            window.statusBar() == ChromeSpec.StatusBar.CELLS ? WorkspaceLook.Status.RULED : WorkspaceLook.Status.SLASH,
            window.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,''',
     '''            content.editorTabs() == ChromeSpec.EditorTabs.CLASSIC ? WorkspaceLook.Tabs.CLASSIC : WorkspaceLook.Tabs.EQUAL,
            content.statusBar() == ChromeSpec.StatusBar.CELLS ? WorkspaceLook.Status.RULED : WorkspaceLook.Status.SLASH,
            content.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,''', 1),
    ('''            new DialogMapping(window.dialogs() == ChromeSpec.DialogKind.CLASSIC ? DialogMapping.Kind.CLASSIC : DialogMapping.Kind.AS_REQUESTED, nativeTitle),''',
     '''            new DialogMapping(content.dialogs() == ChromeSpec.DialogKind.CLASSIC ? DialogMapping.Kind.CLASSIC : DialogMapping.Kind.AS_REQUESTED, nativeTitle),''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.plugins.PluginSelectionTest' --tests 'dev.jasper.app.application.Presentation*'`
Expected: PASS. `ProjectionsTest`'s six cases pass (`theDefaultLookFollowsTheSameRules` and `theClassicLookMapsByTheSpecTable…`
use every group and are unchanged). A 5a or 5b test that asserted `committedDesktop()` is `DesktopFlags.STANDARD` for a
native-titled fixture fails here: change its expectation to the fixture's flags in the same commit and name it in the banner.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/presentation jasper-app/src/test/java/dev/jasper/app
git commit -F - <<'EOF'
Window chrome: the window-chrome gate

Presentation.Landed gains WINDOW_CONTENT for plan 6b's header, tabs,
status, run item and dialogs, and WINDOW_CHROME lands: the desktop
flags, menus, shell, motion and title template now take the kit file's
values. A native-titled look keeps spec 9.1's fallback header row until
the content group lands.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: The desktop flags

The bootstrap applies the committed `DesktopFlags` before AWT (spec 9.1, 10.1): `apple.laf.useScreenMenuBar` follows
`screenMenuBar`, and `TitleBarStyle.choose` gives NATIVE on every platform under `nativeTitle`.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:100-104,126-132`
- Modify: `jasper-app/src/main/java/dev/jasper/app/platform/TitleBarStyle.java:20-23`
- Create: `jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopFlagsTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/platform/TitleBarStyleTest.java` (one test)
- Modify: `docs/design/theme-engine/migration.csv` (one row: `DesktopFlagsTest` names `apple.awt.application.appearance`, which `CANDIDATE`'s `[Aa]ppearance` matches)

**Interfaces:**
- Consumes: Task 1's `Presentation.committedDesktop()`; 5a's `Startup.selection()`.
- Produces: `static void ApplicationBootstrap.configureDesktopProperties(DesktopFlags flags)` (the no-argument form
  stays, as `DesktopFlags.STANDARD`); `static TitleBarStyle TitleBarStyle.choose(boolean macOs, boolean nativeDecorations,
  boolean compact, boolean nativeTitle)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopFlagsTest.java`:

```java
package dev.jasper.app.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.DesktopFlags;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 10.1: the committed desktop flags reach the properties AWT reads, before any window. */
class DesktopFlagsTest {
    static final String MENU = "apple.laf.useScreenMenuBar";
    private final Map<String, String> saved = new HashMap<>();

    @BeforeEach void save() { for (String key : new String[] {MENU, "apple.awt.application.appearance", "apple.awt.enableTemplateImages"})
        saved.put(key, System.getProperty(key)); }
    @AfterEach void restore() { saved.forEach((key, value) -> { if (value == null) System.clearProperty(key); else System.setProperty(key, value); }); }

    @Test void aNativeTitledLookTurnsTheScreenMenuBarOffBeforeAwt() {
        ApplicationBootstrap.configureDesktopProperties(new DesktopFlags(false, true));
        assertThat(System.getProperty(MENU)).isEqualTo("false");
        assertThat(System.getProperty("apple.awt.application.appearance")).as("the rest is unchanged").isEqualTo("system");
        assertThat(System.getProperty("apple.awt.enableTemplateImages")).isEqualTo("true");
    }

    @Test void theDefaultLookKeepsTheScreenMenuBarAndOverridesAnInheritedSetting() {
        System.setProperty(MENU, "false");
        ApplicationBootstrap.configureDesktopProperties(DesktopFlags.STANDARD);
        assertThat(System.getProperty(MENU)).isEqualTo("true");
        System.setProperty(MENU, "false");
        ApplicationBootstrap.configureDesktopProperties();
        assertThat(System.getProperty(MENU)).as("the no-argument form is the default look's").isEqualTo("true");
    }
}
```

Then add to `TitleBarStyleTest`:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/platform/TitleBarStyleTest.java"); text = path.read_text(encoding="utf-8")
old = '''    @Test void macOsAlwaysUsesItsOwnTitleRow() {'''
new = '''    /** Spec 10.1: a native-titled look gives every platform the system title bar, in Normal and Compact alike. */
    @Test void aNativeTitledLookIsNativeEverywhere() {
        for (boolean macOs : new boolean[] {false, true})
            for (boolean nativeDecorations : new boolean[] {false, true})
                for (boolean compact : new boolean[] {false, true}) {
                    assertThat(TitleBarStyle.choose(macOs, nativeDecorations, compact, true)).isEqualTo(TitleBarStyle.NATIVE);
                    assertThat(TitleBarStyle.choose(macOs, nativeDecorations, compact, false))
                        .isEqualTo(TitleBarStyle.choose(macOs, nativeDecorations, compact));
                }
    }

    @Test void macOsAlwaysUsesItsOwnTitleRow() {'''
if text.count(old) != 1: raise SystemExit("TitleBarStyleTest anchor moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.DesktopFlagsTest' --tests 'dev.jasper.app.platform.TitleBarStyleTest'`
Expected: FAIL; `compileTestJava` reports "method configureDesktopProperties in class ApplicationBootstrap cannot be
applied to given types" and "no suitable method found for choose(boolean,boolean,boolean,boolean)".

- [ ] **Step 3: Apply the flags**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 2 step 3: the desktop flags reach the bootstrap and the title-bar style."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java", [
    ('''            configureDesktopProperties();
            // Before the look and feel and any window: a restart-only choice, like residency.
            dev.jasper.app.platform.TitleBarStyle.choose(System.getProperty("os.name").startsWith("Mac"),
                service.initialState().snapshot().nativeDecorations(),
                service.initialState().snapshot().viewMode() == dev.jasper.app.config.ViewMode.COMPACT).configure();''',
     '''            // The look's flags, committed before AWT (spec 9.1): the macOS menu bar and the system title bar.
            var desktop = startup.selection().presentation().committedDesktop();
            configureDesktopProperties(desktop);
            // Before the look and feel and any window: a restart-only choice, like residency.
            dev.jasper.app.platform.TitleBarStyle.choose(System.getProperty("os.name").startsWith("Mac"),
                service.initialState().snapshot().nativeDecorations(),
                service.initialState().snapshot().viewMode() == dev.jasper.app.config.ViewMode.COMPACT, desktop.nativeTitle()).configure();''', 1),
    ('''    /** macOS reads these settings during toolkit/LAF initialization; never change them on reload. */
    static void configureDesktopProperties() {
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.laf.useScreenMenuBar", "true");''',
     '''    /** The default look's desktop properties. */
    static void configureDesktopProperties() { configureDesktopProperties(dev.jasper.app.presentation.DesktopFlags.STANDARD); }

    /**
     * macOS reads these settings during toolkit/LAF initialization; never change them on reload. {@code flags} decide
     * whether menus use the screen menu bar or a row in each window (spec 10.1).
     */
    static void configureDesktopProperties(dev.jasper.app.presentation.DesktopFlags flags) {
        System.setProperty("apple.awt.application.appearance", "system");
        System.setProperty("apple.laf.useScreenMenuBar", Boolean.toString(flags.screenMenuBar()));''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/platform/TitleBarStyle.java", [
    ('''    /** Compact keeps the operating system title bar on every platform. */
    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations, boolean compact) {
        return compact ? NATIVE : choose(macOs, nativeDecorations);
    }''',
     '''    /** Compact keeps the operating system title bar on every platform. */
    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations, boolean compact) {
        return compact ? NATIVE : choose(macOs, nativeDecorations);
    }

    /** A look with a native title ({@code nativeTitle}) gives every platform the operating system title bar. */
    public static TitleBarStyle choose(boolean macOs, boolean nativeDecorations, boolean compact, boolean nativeTitle) {
        return nativeTitle ? NATIVE : choose(macOs, nativeDecorations, compact);
    }''', 1)])
PY
```

Add the ledger row:

```bash
python3 -I - <<'PY'
import pathlib
test = "jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopFlagsTest.java"
rows = [(test, "app.bootstrap", "DesktopFlagsTest", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 2; the desktop flags reach the AWT properties")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.bootstrap.*' --tests 'dev.jasper.app.platform.TitleBarStyleTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS (`DesktopFlagsTest` 2, `TitleBarStyleTest` 4, every existing `ApplicationBootstrapTest` case; `bootstrap →
presentation` is an allowed edge since 5a).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java jasper-app/src/main/java/dev/jasper/app/platform/TitleBarStyle.java \
  jasper-app/src/test/java/dev/jasper/app/bootstrap/DesktopFlagsTest.java jasper-app/src/test/java/dev/jasper/app/platform/TitleBarStyleTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the desktop flags

The bootstrap applies the look's committed desktop flags before AWT: the
macOS screen menu bar follows screenMenuBar, and a native-titled look gets
the system title bar on every platform. The default look's flags are
today's.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: The classic chrome metrics

The classic menu row, toolbar, tabs, status cells and dialogs of plans 6a and 6b are drawn from catalog roles measured on
the mocks. They are appended after plan 4a's roles (the enum ends with `DOCK_RULE_HIT(6)`) with the same value in
`DEFAULT` and `CLASSIC` (Rulings 5; 4a's Rulings 6): only classic chrome reads them, so the default look cannot move.
4a's `NewRolesTest.theAddedRolesFollowEachOtherInOrder` already checks plan 4's roles as one run in order wherever later
roles go, so it is unchanged, and `ChromeRolesTest` checks this plan's run the same way (after 4a's roles, one run in
order), so another plan's roles (4c's `GROUP_ROW_INDENT`, 8a's, 7's) may sit before or after it without changing either
test (Rulings 15).

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (enum tail)
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv` (rows appended)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/ChromeRolesTest.java`

**Interfaces:**
- Consumes: plan 4a's catalog (`DesignMetrics.DEFAULT`, `CLASSIC`, `value(Role)`) and its last role `DOCK_RULE_HIT`.
- Produces: `DesignMetrics.Role` constants, in this order: `MENU_BAR_SIDE(5)`, `MENU_BAR_ITEM_PADDING(9)`,
  `MENU_BAR_HEIGHT(24)`, `CHROME_BUTTON_PADDING(8)`, `CHROME_BUTTON_GAP(8)`, `CHROME_SEPARATOR_GAP(10)`,
  `CHROME_SEPARATOR_INSET(8)`, `CHROME_GLYPH(7)`, `CHROME_GLYPH_GAP(6)`, `CHROME_CARET(5)`, `CHROME_CARET_GAP(7)`,
  `CHROME_TRAILING_GAP(6)`, `TOOLBAR_SEARCH_WIDTH(300)`, `TOOLBAR_SEARCH_PADDING(8)`, `HINT_KEYCAP_HEIGHT(16)`,
  `HINT_KEYCAP_PADDING(4)`, `CLASSIC_TAB_LEADING(12)`, `CLASSIC_TAB_GAP(9)`, `CLASSIC_TAB_TRAILING(10)`,
  `CLASSIC_TAB_PLUS_INSET(11)`, `CLASSIC_TAB_MIN_LABEL(24)`, `STATUS_CELL_PADDING(10)`, `STATUS_CELL_DOT_GAP(7)`,
  `RUN_GLYPH_GAP(5)`, `DIALOG_RADIUS(4)`, `DIALOG_HEADER_HEIGHT(26)`, `DIALOG_HEADER_INSET(10)`, `DIALOG_SWATCH(8)`,
  `DIALOG_SWATCH_GAP(7)`, `DIALOG_FOOTER_VERTICAL(8)`. Each value is the halved 2× measurement in this plan's and plan
  6b's "Mock measurements".

- [ ] **Step 1: Write the failing test**

Create `jasper-ui/src/test/java/dev/jasper/ui/metrics/ChromeRolesTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Plans 6a and 6b: the classic chrome's roles, measured on the Enterprise mocks, the same in both catalogs. */
class ChromeRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        Object[][] rows = {{"MENU_BAR_SIDE", 5}, {"MENU_BAR_ITEM_PADDING", 9}, {"MENU_BAR_HEIGHT", 24}, {"CHROME_BUTTON_PADDING", 8},
            {"CHROME_BUTTON_GAP", 8}, {"CHROME_SEPARATOR_GAP", 10}, {"CHROME_SEPARATOR_INSET", 8}, {"CHROME_GLYPH", 7},
            {"CHROME_GLYPH_GAP", 6}, {"CHROME_CARET", 5}, {"CHROME_CARET_GAP", 7}, {"CHROME_TRAILING_GAP", 6},
            {"TOOLBAR_SEARCH_WIDTH", 300}, {"TOOLBAR_SEARCH_PADDING", 8}, {"HINT_KEYCAP_HEIGHT", 16}, {"HINT_KEYCAP_PADDING", 4},
            {"CLASSIC_TAB_LEADING", 12}, {"CLASSIC_TAB_GAP", 9}, {"CLASSIC_TAB_TRAILING", 10}, {"CLASSIC_TAB_PLUS_INSET", 11},
            {"CLASSIC_TAB_MIN_LABEL", 24}, {"STATUS_CELL_PADDING", 10}, {"STATUS_CELL_DOT_GAP", 7}, {"RUN_GLYPH_GAP", 5},
            {"DIALOG_RADIUS", 4}, {"DIALOG_HEADER_HEIGHT", 26}, {"DIALOG_HEADER_INSET", 10}, {"DIALOG_SWATCH", 8},
            {"DIALOG_SWATCH_GAP", 7}, {"DIALOG_FOOTER_VERTICAL", 8}};
        for (Object[] row : rows) ROLES.put((String) row[0], (Integer) row[1]);
    }

    @Test void theChromeRolesFollowPlanFoursRolesInOrderWithTheirMeasuredValuesInBothCatalogs() {
        var names = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf("MENU_BAR_SIDE");
        assertThat(first).as("appended after plan 4a's roles").isGreaterThan(names.indexOf("DOCK_RULE_HIT"));
        assertThat(names.subList(first, first + ROLES.size())).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as("default " + name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as("classic " + name).isEqualTo(value);
        });
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.ChromeRolesTest'`
Expected: FAIL: the first assertion reports that `-1` (no `MENU_BAR_SIDE` role yet) is not greater than
`DOCK_RULE_HIT`'s index.

- [ ] **Step 3: Add the roles and their rows**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 3 step 3: the classic chrome roles, appended after plan 4a's."""
import pathlib
ADDED = [("MENU_BAR_SIDE", 5), ("MENU_BAR_ITEM_PADDING", 9), ("MENU_BAR_HEIGHT", 24), ("CHROME_BUTTON_PADDING", 8),
    ("CHROME_BUTTON_GAP", 8), ("CHROME_SEPARATOR_GAP", 10), ("CHROME_SEPARATOR_INSET", 8), ("CHROME_GLYPH", 7),
    ("CHROME_GLYPH_GAP", 6), ("CHROME_CARET", 5), ("CHROME_CARET_GAP", 7), ("CHROME_TRAILING_GAP", 6),
    ("TOOLBAR_SEARCH_WIDTH", 300), ("TOOLBAR_SEARCH_PADDING", 8), ("HINT_KEYCAP_HEIGHT", 16), ("HINT_KEYCAP_PADDING", 4),
    ("CLASSIC_TAB_LEADING", 12), ("CLASSIC_TAB_GAP", 9), ("CLASSIC_TAB_TRAILING", 10), ("CLASSIC_TAB_PLUS_INSET", 11),
    ("CLASSIC_TAB_MIN_LABEL", 24), ("STATUS_CELL_PADDING", 10), ("STATUS_CELL_DOT_GAP", 7), ("RUN_GLYPH_GAP", 5),
    ("DIALOG_RADIUS", 4), ("DIALOG_HEADER_HEIGHT", 26), ("DIALOG_HEADER_INSET", 10), ("DIALOG_SWATCH", 8),
    ("DIALOG_SWATCH_GAP", 7), ("DIALOG_FOOTER_VERTICAL", 8)]
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"); text = path.read_text(encoding="utf-8")
old = "DOCK_RULE(1), DOCK_RULE_HIT(6);"
if text.count(old) != 1: raise SystemExit("DesignMetrics: the enum no longer ends with plan 4a's DOCK_RULE(1), DOCK_RULE_HIT(6);")
lines = [", ".join(f"{n}({v})" for n, v in ADDED[i:i + 6]) for i in range(0, len(ADDED), 6)]
new = ("DOCK_RULE(1), DOCK_RULE_HIT(6),\n"
       "        // Enterprise presentation plan 6: the classic chrome's menu row, toolbar, tabs, status cells and dialogs,\n"
       "        // measured on the Enterprise mocks and read only by classic chrome.\n"
       "        " + ",\n        ".join(lines) + ";")
path.write_text(text.replace(old, new), encoding="utf-8")
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/" + table); text = path.read_text(encoding="utf-8")
    if not text.endswith("DOCK_RULE_HIT\t6\n"): raise SystemExit(f"{table}: expected plan 4a's DOCK_RULE_HIT last")
    path.write_text(text + "".join(f"{n}\t{v}\n" for n, v in ADDED), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' :jasper-ui:javadoc`
Expected: PASS (`ChromeRolesTest`, `NewRolesTest` unchanged, `DesignMetricsTest.everyRoleHasItsCompactValue`, 4a's
`ClassicCatalogTest` frozen table, `MetricTokensTest`; 35 tests in the package in the dry run).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/test/resources/dev/jasper/ui/metrics \
  jasper-ui/src/test/java/dev/jasper/ui/metrics/ChromeRolesTest.java
git commit -F - <<'EOF'
Window chrome: the classic chrome metrics

Thirty catalog roles for the classic menu row, toolbar, tabs, status
cells and dialogs, measured on the Enterprise mocks and the same in both
catalogs, appended after plan 4a's roles and frozen in both tables. Only
classic chrome reads them.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The menu composition

`MenuBarLayout` has two implementations over the same contributions (spec 10.2): `StandardMenuBar` (today's order) and
`ClassicMenuBar` (File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help, with the Window menu and the
24 px classic row). `WorkspaceChrome.of` is the one reader of `WorkspaceLook.Menus` and picks the layout; `WindowContent`
receives a `WorkspaceChrome`, which the default constructors give as `WorkspaceChrome.STANDARD`.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java`, `MenuBarLayout.java`, `StandardMenuBar.java`, `ClassicMenuBar.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:91-97,320-361,363,488-489` (the built-in menus, `rebuildContributedMenus`, `menu`, `refreshTheme`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:100,221-223,712` (the field, the constructor, `chrome()`), `jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java` (the `presentation` edge, fact 14)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java:162` (`toolWindowsSection`), `contributions/MenuTarget.java:16` (`HELP`), `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:1045`
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java`, `jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java` (one factory and `closeOwnersNow`)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: 5a's `WorkspaceLook`, `TitleFormat`, `ChromeSpec`; 3d's `MenuTarget.Slot.SESSION`/`TRANSFER`; Task 3's
  `MENU_BAR_*` roles; 4a's `KitFixtures.enterprise()`.
- Produces: `public final class WorkspaceChrome` with `static final WorkspaceChrome STANDARD`, `static WorkspaceChrome
  of(WorkspaceLook look, TitleFormat title)`, package-private `MenuBarLayout menus()` and `TitleFormat title()`;
  `sealed interface MenuBarLayout permits StandardMenuBar, ClassicMenuBar` with `List<JMenu> fixed(BuiltIn)`,
  `void fresh(Map<MenuTarget.Slot, JMenu>, Supplier<JMenu> pane, Supplier<JMenu> tab)`, `boolean toolWindowsInView()`,
  `List<JMenu> contributed(Pass)`, `void style(JMenuBar)` and the records `BuiltIn(JMenu file, JMenu edit, JMenu view,
  JMenu pane, JMenu tab)`, `TopMenu(JMenu menu, boolean help)`, `Pass(Map<MenuTarget.Slot, JMenu> slots, List<TopMenu>
  topLevel, Function<String, JMenu> menu, List<JMenuItem> toolWindows)`; `WindowContent(ShellLauncher, Path, boolean,
  Consumer<Path>, Runnable, Runnable, ThemeController, KeyBindings, CommandHistory, boolean, WorkspaceChrome)` and
  `WorkspaceChrome look()`; `Contributions.toolWindowsSection()` (`Optional<MenuSection>`); `MenuTarget.HELP`
  (`"app.help"`); test support `EnterpriseLook.CHROME`, `kit()`, `chrome()`, `theme()`, `controller(boolean dark)`,
  `restoreDefault()` (plan 5b points them at the shipped kit, faces and theme); `DesktopTestSupport.content(ShellLauncher,
  ThemeController, WorkspaceChrome)` and `closeOwnersNow()`.

- [ ] **Step 1: Write the test support and the failing tests**

Plan 5b's shipped kit, Plex files and Enterprise theme are not on the branch (fact 13), so Enterprise runs here as 4a's
fixture kit, spec 5.1's chrome table and a fixture theme holding exactly spec 7.6's named roles and override keys over
Slate Cobalt (Rulings 13). Create `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java`:

```java
package dev.jasper.app.testsupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import dev.jasper.ui.theme.JasperTheme;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Enterprise as the chrome tests run it before plan 5b ships the plugin: plan 4a's Enterprise fixture kit (the classic
 * base with the kit file's tokens and 12 px body, in the platform faces), spec 5.1's chrome table, and a fixture theme
 * holding spec 7.6's named roles and override keys over Slate Cobalt (the roles the mocks show). Tests restore the
 * default look when they finish. Plan 5b replaces the kit, faces and theme with the shipped ones.
 */
public final class EnterpriseLook {
    /** Spec 5.1's {@code [chrome]} table, as the Enterprise kit file lists it. */
    public static final ChromeSpec CHROME = new ChromeSpec(ChromeSpec.TitleBar.NATIVE, ChromeSpec.MenuBar.WINDOW,
        "Workstation — {session} — {directory}", ChromeSpec.ToolWindows.STRIPES, ChromeSpec.EditorTabs.CLASSIC,
        ChromeSpec.StatusBar.CELLS, ChromeSpec.PaletteKind.RUN_WINDOW, ChromeSpec.DialogKind.CLASSIC, ChromeSpec.MotionKind.NONE);
    private static JasperTheme theme;

    private EnterpriseLook() { }

    /** Plan 4a's Enterprise fixture kit. */
    public static Kit kit() { return KitFixtures.enterprise(); }

    /** The Enterprise kit file's chrome points. */
    public static ChromeSpec chrome() { return CHROME; }

    /** The fixture theme: spec 7.6's named roles and overrides, Light and Dark, over Slate Cobalt's other roles. */
    public static synchronized JasperTheme theme() {
        if (theme != null) return theme;
        try (var in = EnterpriseLook.class.getResourceAsStream("enterprise-fixture.json")) {
            if (in == null) throw new AssertionError("enterprise-fixture.json is missing");
            theme = JasperTheme.parse(new String(in.readAllBytes(), StandardCharsets.UTF_8), "enterprise-fixture");
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
        return theme;
    }

    /** A controller showing the fixture theme's Light or Dark variant under the fixture kit. EDT. */
    public static ThemeController controller(boolean dark) {
        var shown = theme();
        return ThemeController.startup(dark, new ThemeSelection(ThemeLibrary.bundledOnly(), shown, shown, TerminalSchemeChoice.MATCH), kit(),
            failure -> { throw new AssertionError("the Enterprise fixture must install", failure); });
    }

    /** Installs Slate Cobalt under the default kit again, with today's fonts, for the tests that follow. EDT. */
    public static void restoreDefault() { ThemeTestSupport.controller(true).close(); }
}
```

Write the fixture theme from Slate Cobalt's file and spec 7.6's tables (a role the tables do not name keeps Slate
Cobalt's value; the menu row reads only named roles and `border.divider`, which derives from two of them):

```bash
python3 -I - <<'PY'
"""Plan 6a Task 4 step 1: the Enterprise fixture theme, spec 7.6's named roles over Slate Cobalt."""
import json, pathlib
base = json.loads(pathlib.Path("jasper-ui/src/main/resources/dev/jasper/ui/theme/slate-cobalt.json").read_text(encoding="utf-8"))
def roles(table):
    out = {}
    for keys, value in table:
        for key in keys.split(): out[key] = value
    return out
LIGHT = roles([("surface.app surface.dialog", "#ECEDEF"), ("surface.titlebar", "#E1E3E6"), ("surface.panel surface.sidebar", "#F7F7F8"),
    ("surface.card surface.input surface.terminal", "#FFFFFF"), ("accent selection.bg hue.blue", "#2F65CA"),
    ("text.onAccent text.onSelection", "#FFFFFF"), ("text.onAccent.secondary text.onSelection.secondary", "#DCE5F7"),
    ("status.warning", "#8A5A00"), ("hue.amber", "#B5651D"), ("border.window", "#C4C7CC"),
    ("border.control border.input button.secondary.border", "#B4B8BF"), ("text.primary", "#1E1F22"),
    ("text.strong.secondary", "#3A3D42"), ("text.secondary text.tertiary", "#5C6370"), ("text.placeholder", "#7A808A"),
    ("status.success hue.green", "#2E9E4F"), ("status.danger", "#D03A3A"), ("hue.purple", "#8A4FBF"),
    ("surface.header", "#E1E3E6"), ("surface.header.active", "#D9DFEA"), ("surface.stripe", "#E4E6E9"),
    ("surface.tableHeader", "#F0F1F3"), ("surface.zebra", "#F7F8FA"), ("surface.rowHover", "#EEF2FA"),
    ("warning.ground", "#FBF1DA"), ("warning.border", "#E6CF95")])
DARK = roles([("surface.app surface.dialog", "#3C3F44"), ("surface.titlebar", "#36393E"), ("surface.panel surface.sidebar", "#35383D"),
    ("surface.card surface.input surface.terminal", "#2E3135"), ("accent selection.bg hue.blue", "#3D74D6"),
    ("text.onAccent text.onSelection", "#FFFFFF"), ("button.secondary.bg", "#45484D"), ("surface.control.selected", "#50545B"),
    ("hue.amber", "#D08A4A"), ("border.window", "#2A2C30"), ("border.control border.input button.secondary.border", "#5A5E65"),
    ("text.primary", "#DFE1E5"), ("text.strong.secondary", "#C4C8CE"), ("text.secondary text.tertiary", "#A0A5AD"),
    ("text.placeholder", "#8E939B"), ("status.success hue.green", "#4CB86A"), ("status.warning", "#E3C27A"),
    ("surface.header", "#36393E"), ("surface.header.active", "#3D4658"), ("surface.stripe", "#383B40"),
    ("surface.tableHeader", "#393C41"), ("surface.zebra", "#313439"), ("warning.ground", "#4A4128"), ("warning.border", "#6B5A30")])
theme = dict(base)
theme["name"] = "Enterprise (fixture)"
theme["author"] = "Jasper tests"
theme["ui"] = {"dark": {**base["ui"]["dark"], **DARK}, "light": {**base["ui"]["light"], **LIGHT}}
theme["terminal"] = {key: base["terminal"][key] for key in ("dark", "light")}
path = pathlib.Path("jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json")
path.parent.mkdir(parents=True, exist_ok=True)
path.write_text(json.dumps(theme, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
PY
python3 -I -c "import json; d = json.load(open('jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json')); print(d['ui']['light']['surface.app'], d['ui']['dark']['surface.app'])"
```

Expected: `#ECEDEF #3C3F44`.

Add the window factory and `closeOwnersNow` to `DesktopTestSupport` (a test that closes its theme controller first
closes its windows, fact 15) and create the test:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java"); text = path.read_text(encoding="utf-8")
old = '''    public static void closeOwners() throws Exception {'''
new = '''    /** A window under {@code look}'s chrome, with this host's keymap. */
    public static WindowContent content(ShellLauncher launcher, ThemeController themes, WorkspaceChrome look) {
        boolean macOs = System.getProperty("os.name").startsWith("Mac");
        WindowContent owner = new WindowContent(launcher, HOME, false, path -> {}, () -> {}, () -> {}, themes,
            KeyBindings.defaults(macOs), new CommandHistory(), macOs, look);
        OWNERS.add(owner);
        return owner;
    }
    /** Closes the test windows now, on the EDT: before a test closes the theme controller its windows are bound to. */
    public static void closeOwnersNow() { OWNERS.forEach(WindowContent::close); OWNERS.clear(); }
    public static void closeOwners() throws Exception {'''
if text.count(old) != 1: raise SystemExit("DesktopTestSupport anchor moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import dev.jasper.ui.theme.DerivedColor;
import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JRootPane;
import javax.swing.border.CompoundBorder;
import javax.swing.border.MatteBorder;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.2: the standard and classic menu bars over the same contributions, and the classic menu row of the mocks. */
class WindowChromeMenusTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WorkspaceChrome classic() {
        return WorkspaceChrome.of(new WorkspaceLook(WorkspaceLook.Menus.CLASSIC, WorkspaceLook.Header.UNIFIED, WorkspaceLook.Stripes.RAILS,
            WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD), new TitleFormat("{context}"));
    }

    static List<String> titles(WindowContent owner) {
        return owner.chrome().menuBar().menus().stream().map(JMenu::getText).toList();
    }

    static JMenu menu(WindowContent owner, String title) {
        return owner.chrome().menuBar().menus().stream().filter(menu -> menu.getText().equals(title)).findFirst().orElseThrow();
    }

    static List<String> items(JMenu menu) {
        var labels = new ArrayList<String>();
        for (Component child : menu.getMenuComponents())
            labels.add(child instanceof JMenu sub ? "submenu:" + sub.getText() : child instanceof JMenuItem item ? item.getText() : "---");
        return labels;
    }

    /** A plugin's Session, Transfer and Tools entries and its own menu, then the app's Help; or Help first when asked. */
    static Contributions model(boolean helpFirst) {
        var model = new Contributions();
        for (String id : List.of("dev.x.connect", "dev.x.upload", "dev.x.tool", "dev.x.own", "dev.x.split", "dev.x.about"))
            model.addAction(id, id.substring(6), null, List.of(), Optional.empty(), invocation -> { });
        if (helpFirst) model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.SESSION)).set(List.of(new MenuEntry.Item("dev.x.connect")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TRANSFER)).set(List.of(new MenuEntry.Item("dev.x.upload")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.tool")));
        model.addMenuSection(MenuTarget.topLevel("dev.x.menu", "Own")).set(List.of(new MenuEntry.Item("dev.x.own")));
        if (!helpFirst) model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        return model;
    }

    @Test void theStandardBarKeepsTodaysOrder() throws Exception {
        edt(() -> {
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            owner.connectContributions(model(false));
            assertThat(titles(owner)).containsExactly("File", "Edit", "View", "Pane", "Tab", "Session", "Transfer", "Tools", "Own", "Help");
            var first = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            first.connectContributions(model(true));
            assertThat(titles(first)).as("registration order, as today").containsExactly("File", "Edit", "View", "Pane", "Tab", "Session", "Transfer", "Tools", "Help", "Own");
            assertThat(owner.chrome().menuBar().getClientProperty("FlatLaf.style")).as("the standard bar is not restyled").isNull();
        });
    }

    @Test void theClassicBarKeepsHelpLastWhateverTheRegistrationOrder() throws Exception {
        edt(() -> {
            for (boolean helpFirst : new boolean[] {false, true}) {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), classic());
                owner.connectContributions(model(helpFirst));
                assertThat(titles(owner)).as(helpFirst ? "help first" : "help last")
                    .containsExactly("File", "Edit", "View", "Session", "Transfer", "Own", "Tools", "Window", "Help");
            }
        });
    }

    @Test void theClassicWindowMenuHoldsPaneThenTabThenTheToolWindows() throws Exception {
        edt(() -> {
            var model = model(false);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel());
            model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.PANE)).set(List.of(new MenuEntry.Item("dev.x.split")));
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), classic());
            owner.connectContributions(model);
            var window = items(menu(owner, "Window"));
            assertThat(window.getFirst()).isEqualTo("Split Right");
            assertThat(window).as("Pane's items and the plugin's Pane section, Tab's items, then the toggles")
                .containsSubsequence("Split Right", "Next Prompt", "---", "split", "---", "Next Tab", "Previous Tab", "---", "Toggle Hosts");
            assertThat(window.getLast()).as("the tool-window toggles close the menu").isEqualTo("Toggle Hosts");
            assertThat(items(menu(owner, "View"))).as("the toggles moved to Window").doesNotContain("submenu:Tool Windows");
            var standard = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(true), WorkspaceChrome.STANDARD);
            standard.connectContributions(model);
            assertThat(items(menu(standard, "View"))).as("the default look keeps them in View").contains("submenu:Tool Windows");
        });
    }

    /** Mock measurements: a 24 px row on surface.app over a 1 px border.divider rule, the first title 15 from the edge. */
    @Test void theClassicMenuRowIsTheMocksRow() throws Exception {
        edt(() -> {
            var themes = EnterpriseLook.controller(false);
            try {
                var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, classic());
                owner.connectContributions(model(false));
                var root = new JRootPane();
                root.setJMenuBar(owner.menuBar());
                root.setContentPane(owner);
                root.setSize(1440, 600);
                LayoutTestSupport.layoutTree(root);
                var bar = owner.chrome().menuBar();
                assertThat(bar.getHeight()).as("24 and the rule").isEqualTo(25);
                assertThat(bar.getInsets()).isEqualTo(new Insets(0, 5, 1, 5));
                var rule = (MatteBorder) ((CompoundBorder) bar.getBorder()).getOutsideBorder();
                assertThat(rule.getMatteColor()).isEqualTo(DerivedColor.BORDER_DIVIDER.color(themes.current().chrome()));
                var file = bar.menus().getFirst();
                assertThat(file.getHeight()).isEqualTo(24);
                assertThat(file.getX() + file.getInsets().left).as("the first title starts 14 in, its ink at 15").isEqualTo(14);
                assertThat(file.getInsets().left).isEqualTo(9);
                assertThat(file.getFont()).as("the kit's body face").isEqualTo(dev.jasper.app.platform.ThemeZone.font(dev.jasper.ui.metrics.Typography.Role.BODY));
                assertThat(file.getFont().getSize()).as("the Enterprise kit's 12 px body").isEqualTo(12);
            } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

The Window menu labels are the actions' names ("Split Right", "Next Tab", "Next Prompt"); a panel registered without a
toggle id gets the action "Toggle <title>" (`Contributions.java:154-156`); the plugin action `dev.x.split` is labelled
"split" by the model fixture (`id.substring(6)`). The row's title font is the kit's BODY face at the fixture kit's
12 px (the platform face until 5b registers Plex).

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowChromeMenusTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … WorkspaceChrome" and "… HELP".

- [ ] **Step 3: Write the layouts and `WorkspaceChrome`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/MenuBarLayout.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.MenuTarget;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;

/**
 * How a window's menu bar composes its menus (Enterprise presentation spec 10.2): the same contributions in two orders.
 * The window builds every menu; a layout chooses which built-in menus the bar shows from the start, which menus a
 * contribution pass builds fresh, and the order of the menus each pass adds. EDT only.
 */
sealed interface MenuBarLayout permits StandardMenuBar, ClassicMenuBar {
    /**
     * The built-in menus, built once by the window.
     *
     * @param file File
     * @param edit Edit
     * @param view View
     * @param pane Pane
     * @param tab Tab
     */
    record BuiltIn(JMenu file, JMenu edit, JMenu view, JMenu pane, JMenu tab) { }

    /**
     * A contributed top-level menu of this pass.
     *
     * @param menu the menu
     * @param help whether it is the app's Help menu ({@link MenuTarget#HELP})
     */
    record TopMenu(JMenu menu, boolean help) { }

    /**
     * One contribution pass's menus.
     *
     * @param slots the built-in menus by slot, the fresh ones of this pass included, with their contributed sections
     * @param topLevel the contributed top-level menus in registration order
     * @param menu makes an empty menu that refreshes the window's actions when opened
     * @param toolWindows one item per tool window's toggle action, in registration order
     */
    record Pass(Map<MenuTarget.Slot, JMenu> slots, List<TopMenu> topLevel, Function<String, JMenu> menu, List<JMenuItem> toolWindows) {
        /** Copies the lists. */
        public Pass { topLevel = List.copyOf(topLevel); toolWindows = List.copyOf(toolWindows); Objects.requireNonNull(slots); Objects.requireNonNull(menu); }

        /** {@code slot}'s menu while it has entries. */
        Optional<JMenu> filled(MenuTarget.Slot slot) {
            JMenu menu = slots.get(slot);
            return menu != null && menu.getMenuComponentCount() > 0 ? Optional.of(menu) : Optional.empty();
        }
    }

    /** The built-in menus the bar shows from the start, in order. */
    List<JMenu> fixed(BuiltIn menus);

    /** Before a pass: puts fresh menus in the slots this layout rebuilds every pass. */
    void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab);

    /** Whether View keeps the app's "Tool Windows" section. */
    boolean toolWindowsInView();

    /** The menus a pass adds after the fixed ones, in order. */
    List<JMenu> contributed(Pass pass);

    /** Styles the bar from the installed theme and kit; called when the window is built and on every theme change. */
    void style(JMenuBar bar);
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StandardMenuBar.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.MenuTarget;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/** Today's menu bar: File, Edit, View, Pane, Tab, Session, Transfer, Tools, then top-level menus as registered. */
final class StandardMenuBar implements MenuBarLayout {
    @Override public List<JMenu> fixed(BuiltIn menus) { return List.of(menus.file(), menus.edit(), menus.view(), menus.pane(), menus.tab()); }

    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) { }

    @Override public boolean toolWindowsInView() { return true; }

    @Override public List<JMenu> contributed(Pass pass) {
        var menus = new ArrayList<JMenu>();
        for (MenuTarget.Slot slot : List.of(MenuTarget.Slot.SESSION, MenuTarget.Slot.TRANSFER, MenuTarget.Slot.TOOLS))
            pass.filled(slot).ifPresent(menus::add);
        for (TopMenu top : pass.topLevel()) menus.add(top.menu());
        return menus;
    }

    @Override public void style(JMenuBar bar) { }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ClassicMenuBar.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.FlatLafInstaller;
import dev.jasper.ui.theme.DerivedColor;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Component;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import javax.swing.BorderFactory;
import javax.swing.JMenu;
import javax.swing.JMenuBar;

/**
 * The classic menu bar (spec 10.2): File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help. Window holds
 * the Pane items, the Tab items and the tool-window toggles; Help comes last. The row is the mocks': 24 px of titles on
 * {@code surface.app} over a 1 px {@code border.divider} rule, an open title in the accent, square.
 */
final class ClassicMenuBar implements MenuBarLayout {
    @Override public List<JMenu> fixed(BuiltIn menus) { return List.of(menus.file(), menus.edit(), menus.view()); }

    @Override public void fresh(Map<MenuTarget.Slot, JMenu> slots, Supplier<JMenu> pane, Supplier<JMenu> tab) {
        // Window gathers Pane and Tab every pass, so they are built fresh with their sections, as Tools is.
        slots.put(MenuTarget.Slot.PANE, pane.get());
        slots.put(MenuTarget.Slot.TAB, tab.get());
    }

    @Override public boolean toolWindowsInView() { return false; }

    @Override public List<JMenu> contributed(Pass pass) {
        var menus = new ArrayList<JMenu>();
        pass.filled(MenuTarget.Slot.SESSION).ifPresent(menus::add);
        pass.filled(MenuTarget.Slot.TRANSFER).ifPresent(menus::add);
        for (TopMenu top : pass.topLevel()) if (!top.help()) menus.add(top.menu());
        pass.filled(MenuTarget.Slot.TOOLS).ifPresent(menus::add);
        menus.add(window(pass));
        for (TopMenu top : pass.topLevel()) if (top.help()) menus.add(top.menu());
        return menus;
    }

    /** Pane's items, a rule, Tab's items, then a rule and the tool-window toggles. */
    private static JMenu window(Pass pass) {
        JMenu window = pass.menu().apply("Window");
        move(pass.slots().get(MenuTarget.Slot.PANE), window);
        if (window.getMenuComponentCount() > 0) window.addSeparator();
        move(pass.slots().get(MenuTarget.Slot.TAB), window);
        if (!pass.toolWindows().isEmpty()) {
            window.addSeparator();
            pass.toolWindows().forEach(window::add);
        }
        return window;
    }

    private static void move(JMenu from, JMenu to) {
        if (from == null) return;
        for (Component child : from.getMenuComponents()) to.add(child);
    }

    @Override public void style(JMenuBar bar) {
        var theme = FlatLafInstaller.installedTheme();
        var metrics = FlatLafInstaller.installedKit().metrics();
        var palette = theme.semantic();
        // The item margins make the row exactly MENU_BAR_HEIGHT for the menu face's line, in logical pixels.
        int line = Math.round(bar.getFontMetrics(ThemeZone.font(Typography.Role.BODY)).getHeight() / UIScale.getUserScaleFactor());
        int height = metrics.value(DesignMetrics.Role.MENU_BAR_HEIGHT), padding = metrics.value(DesignMetrics.Role.MENU_BAR_ITEM_PADDING);
        int top = Math.max(0, (height - line) / 2), bottom = Math.max(0, height - line - top);
        bar.putClientProperty("FlatLaf.style", Map.of("itemMargins", new Insets(top, padding, bottom, padding),
            "selectionInsets", new Insets(0, 0, 0, 0), "selectionArc", 0,
            "hoverBackground", palette.color(SemanticColor.SURFACE_CONTROL_HOVER),
            "selectionBackground", palette.color(SemanticColor.ACCENT), "selectionForeground", palette.color(SemanticColor.TEXT_ONACCENT)));
        int side = UIScale.scale(metrics.value(DesignMetrics.Role.MENU_BAR_SIDE)), rule = UIScale.scale(metrics.value(DesignMetrics.Role.HAIRLINE));
        bar.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createMatteBorder(0, 0, rule, 0, DerivedColor.BORDER_DIVIDER.color(theme)),
            BorderFactory.createEmptyBorder(0, side, 0, side)));
        bar.setBackground(palette.color(SemanticColor.SURFACE_APP));
        bar.setOpaque(true);
        bar.revalidate(); bar.repaint();
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.presentation.WorkspaceLook;
import java.util.Objects;

/**
 * The workspace's chrome under the look (Enterprise presentation spec 3.3 and 9.1): the one reader of
 * {@link WorkspaceLook}'s variants, choosing each strategy once for every window it is given to. Everything else in the
 * workspace reads the strategies it holds.
 */
public final class WorkspaceChrome {
    /** The default look's chrome. */
    public static final WorkspaceChrome STANDARD = new WorkspaceChrome(new StandardMenuBar(), new TitleFormat("{context}"));

    private final MenuBarLayout menus;
    private final TitleFormat title;

    private WorkspaceChrome(MenuBarLayout menus, TitleFormat title) {
        this.menus = Objects.requireNonNull(menus);
        this.title = Objects.requireNonNull(title);
    }

    /**
     * The strategies for {@code look}.
     *
     * @param look the workspace's projection
     * @param title the frame title template
     * @return the chrome; never throws for a projection the presentation package builds
     */
    public static WorkspaceChrome of(WorkspaceLook look, TitleFormat title) {
        Objects.requireNonNull(look, "look");
        MenuBarLayout menus = switch (look.menus()) {
            case STANDARD -> new StandardMenuBar();
            case CLASSIC -> new ClassicMenuBar();
        };
        return new WorkspaceChrome(menus, title);
    }

    MenuBarLayout menus() { return menus; }
    TitleFormat title() { return title; }
}
```

- [ ] **Step 4: Compose the bar through the layout**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 4 step 4: WindowChrome composes through the layout; WindowContent carries the chrome."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowChrome.java", [
    ('''        JMenu pane = menu("Pane", ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
            ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN,
            ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT);
        JMenu tab = menu("Tab", ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
            ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
            ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9);
        menuBar.add(file); menuBar.add(edit); menuBar.add(view); menuBar.add(pane); menuBar.add(tab);''',
     '''        JMenu pane = paneMenu();
        JMenu tab = tabMenu();
        // The look's layout decides which built-in menus the bar shows from the start (spec 10.2).
        for (JMenu fixed : owner.look().menus().fixed(new MenuBarLayout.BuiltIn(file, edit, view, pane, tab))) menuBar.add(fixed);
        owner.look().menus().style(menuBar);''', 1),
    ('''    private JMenu menu(String label, ActionId... ids) {''',
     '''    private static final ActionId[] PANE_ITEMS = {ActionId.SPLIT_RIGHT, ActionId.SPLIT_DOWN, ActionId.FOCUS_PANE_LEFT,
        ActionId.FOCUS_PANE_RIGHT, ActionId.FOCUS_PANE_UP, ActionId.FOCUS_PANE_DOWN, ActionId.PREVIOUS_PROMPT, ActionId.NEXT_PROMPT};
    private static final ActionId[] TAB_ITEMS = {ActionId.NEXT_TAB, ActionId.PREVIOUS_TAB, ActionId.RENAME_TAB,
        ActionId.SELECT_TAB_1, ActionId.SELECT_TAB_2, ActionId.SELECT_TAB_3, ActionId.SELECT_TAB_4,
        ActionId.SELECT_TAB_5, ActionId.SELECT_TAB_6, ActionId.SELECT_TAB_7, ActionId.SELECT_TAB_8, ActionId.SELECT_TAB_9};
    private JMenu paneMenu() { return menu("Pane", PANE_ITEMS); }
    private JMenu tabMenu() { return menu("Tab", TAB_ITEMS); }

    /** One item per tool window whose toggle action this window has, in registration order. */
    private List<JMenuItem> toolWindowItems() {
        var items = new ArrayList<JMenuItem>();
        for (var panel : contributions.model().panels()) {
            Action action = contributions.action(panel.toggleActionId());
            if (action != null) items.add(new MenuBarItems.Item(action));
        }
        return items;
    }

    private JMenu menu(String label, ActionId... ids) {''', 1),
    ('''    void refreshTheme() {
        header.setBackground(ThemeZone.color(header, SemanticColor.SURFACE_APP));''',
     '''    void refreshTheme() {
        owner.look().menus().style(menuBar);
        header.setBackground(ThemeZone.color(header, SemanticColor.SURFACE_APP));''', 1)])
path = pathlib.Path(W + "WindowChrome.java"); text = path.read_text(encoding="utf-8")
start, end = text.index("    private void rebuildContributedMenus() {"), text.index("    private static final ActionId[] PANE_ITEMS")
path.write_text(text[:start] + '''    private void rebuildContributedMenus() {
        contributedSections.forEach((menu, added) -> added.forEach(menu::remove));
        contributedSections.clear();
        contributedMenus.forEach(menuBar::remove);
        contributedMenus.clear();
        MenuBarLayout layout = owner.look().menus();
        if (contributions != null) {
            var sections = contributions.model().menus();
            // Session, Transfer and Tools hold only plugin entries and, like a plugin's own menu, are built fresh on
            // every pass and added to the bar only while they have any: a stale one would keep an old look and feel,
            // and one JMenu would be re-inserted into the macOS screen menu bar.
            standardMenus.put(MenuTarget.Slot.SESSION, menu("Session"));
            standardMenus.put(MenuTarget.Slot.TRANSFER, menu("Transfer"));
            standardMenus.put(MenuTarget.Slot.TOOLS, menu("Tools"));
            // A layout that gathers Pane and Tab into another menu builds them fresh too.
            layout.fresh(standardMenus, this::paneMenu, this::tabMenu);
            var toolWindows = contributions.model().toolWindowsSection();
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.STANDARD) continue;
                if (!layout.toolWindowsInView() && toolWindows.filter(own -> own == section).isPresent()) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = standardMenus.get(MenuTarget.Slot.valueOf(section.target().key()));
                List<Component> added = contributedSections.computeIfAbsent(menu, key -> new ArrayList<>());
                // Session, Transfer and Tools have no items of their own, so a first section there needs no separator.
                if (menu.getMenuComponentCount() > 0) {
                    var separator = new JPopupMenu.Separator();
                    menu.add(separator); added.add(separator);
                }
                for (Component child : built) { menu.add(child); added.add(child); }
            }
            var topLevel = new ArrayList<MenuBarLayout.TopMenu>();
            for (MenuSection section : sections) {
                if (section.target().type() != MenuTarget.Type.TOP_LEVEL) continue;
                List<Component> built = build(section.entries(), MenuBarItems.Item::new);
                if (built.isEmpty()) continue;
                JMenu menu = observe(new JMenu(section.target().title()));
                built.forEach(menu::add);
                topLevel.add(new MenuBarLayout.TopMenu(menu, MenuTarget.HELP.equals(section.target().key())));
            }
            // The bar orders menus as they are added; the layout gives the order (spec 10.2).
            for (JMenu menu : layout.contributed(new MenuBarLayout.Pass(standardMenus, topLevel, label -> menu(label), toolWindowItems()))) {
                menuBar.add(menu); contributedMenus.add(menu);
            }
        }
        menuBar.revalidate(); menuBar.repaint();
    }

''' + text[end:], encoding="utf-8")
edit(W + "WindowContent.java", [
    ('''    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        super(new BorderLayout());''',
     '''    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs) {
        this(launcher, directory, detached, newWindow, quit, onEmpty, themes, bindings, history, macOs, WorkspaceChrome.STANDARD);
    }

    /** As above, under the look's workspace chrome (Enterprise presentation spec 9.1). */
    WindowContent(ShellLauncher launcher, Path directory, boolean detached, Consumer<Path> newWindow, Runnable quit, Runnable onEmpty,
                  ThemeController themes, KeyBindings bindings, CommandHistory history, boolean macOs, WorkspaceChrome look) {
        super(new BorderLayout());
        this.look = Objects.requireNonNull(look, "look");''', 1),
    ('''    private final WindowChrome chrome;
''', '''    private final WindowChrome chrome;
    /** The look's workspace strategies, fixed for this window's life. */
    private final WorkspaceChrome look;
''', 1),
    ('''    WindowChrome chrome() { return chrome; }''', '''    WindowChrome chrome() { return chrome; }
    WorkspaceChrome look() { return look; }''', 1)])
C = "jasper-app/src/main/java/dev/jasper/app/contributions/"
edit(C + "Contributions.java", [
    ('''    public List<PanelEntry> panels() { return List.copyOf(panels.values()); }''',
     '''    public List<PanelEntry> panels() { return List.copyOf(panels.values()); }

    /** The app's View section listing every panel's toggle ("Tool Windows"), while there are panels. */
    public Optional<MenuSection> toolWindowsSection() { return Optional.ofNullable(panelsMenu); }''', 1)])
edit(C + "MenuTarget.java", [
    ('''    /** The built-in menus that accept a contributed section. */''',
     '''    /** The top-level menu id of the app's Help menu, which a classic menu bar places last. */
    public static final String HELP = "app.help";

    /** The built-in menus that accept a contributed section. */''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java", [
    ('''contributions.addMenuSection(MenuTarget.topLevel("app.help", "Help"))''', '''contributions.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help"))''', 1)])
PY
```

`Contributions` already imports `java.util.Optional`. Add the `workspace → presentation` edge to the package-info
(spec 9.2; 5a did not add it, fact 14):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java"); text = path.read_text(encoding="utf-8")
old = "dev.jasper.app.platform, dev.jasper.app.settings, dev.jasper.app.terminals,"
if text.count(old) != 1: raise SystemExit("workspace package-info: the dependency list moved")
path.write_text(text.replace(old, "dev.jasper.app.platform, dev.jasper.app.presentation, dev.jasper.app.settings, dev.jasper.app.terminals,"), encoding="utf-8")
PY
```

Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
W, T = "jasper-app/src/main/java/dev/jasper/app/workspace/", "jasper-app/src/test/java/dev/jasper/app/"
test = T + "workspace/WindowChromeMenusTest.java"
rows = [(W + "MenuBarLayout.java", "app.workspace", "MenuBarLayout", test, "New in Enterprise presentation Plan 6a Task 4; how a window's menu bar orders the same menus"),
        (W + "StandardMenuBar.java", "app.workspace", "StandardMenuBar", test, "New in Enterprise presentation Plan 6a Task 4; today's menu order"),
        (W + "ClassicMenuBar.java", "app.workspace", "ClassicMenuBar", test, "New in Enterprise presentation Plan 6a Task 4; the classic order; Window menu and 24 px row"),
        (W + "WorkspaceChrome.java", "app.workspace", "WorkspaceChrome", test, "New in Enterprise presentation Plan 6a Task 4; the one reader of WorkspaceLook's variants"),
        (T + "testsupport/EnterpriseLook.java", "app.testsupport", "EnterpriseLook", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 4; the fixture kit; chrome and theme for chrome tests"),
        (test, "app.workspace", "WindowChromeMenusTest", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 4; both menu bars and the classic row")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.contributions.*' --tests 'dev.jasper.app.application.AboutWindowsTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.*' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `WindowChromeMenusTest` 4 cases; every existing `WindowChromeContributionsTest` case (including the bar
order of `menuRefreshDuringNativeMenuMutationDoesNotDuplicateTopLevelMenus`, which runs under `WorkspaceChrome.STANDARD`);
`StaticDefaultTest` (the classic bar reads the installed kit, not `DesignMetrics.DEFAULT`); no "Appearance binding is
closed" on the EDT.

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/contributions \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/workspace \
  jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java \
  jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the menu composition

MenuBarLayout orders the same contributions two ways. StandardMenuBar is
today's bar; ClassicMenuBar reads File, Edit, View, Session, Transfer,
plugin menus, Tools, Window and Help, with Window holding the Pane and Tab
items and the tool-window toggles, Help last whatever the registration
order, and the mocks' 24 px row over a border.divider rule. WorkspaceChrome
is the one reader of WorkspaceLook's variants; windows default to its
standard chrome. EnterpriseLook gives chrome tests the Enterprise fixture
kit, chrome and a theme of spec 7.6's named roles until plan 5b.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The window shell

A terminal window takes the look's title bar and menu placement through `WindowLook` (spec 10.1): the unified macOS bar
only where `ShellLook.unifiedTitle` says so, otherwise the system's title bar with the menus as a row in the window
(the screen menu bar shows them when the look keeps it), NSAppearance following the theme on a native-titled macOS
frame, and Compact keeping that row. `ShellChrome.of` gives `NativeShells` the same values for auxiliary windows,
dialogs and the file chooser; their minimal File menu becomes a row in the window under in-window menus, so Close
Window keeps ⌘W. About Jasper joins Help when the screen menu bar is off on macOS. The application hands every window,
the rehearsal's included, the look.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java`, `jasper-app/src/main/java/dev/jasper/app/windows/ShellChrome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java:56-70`, `WindowContent.java:139-140,302-335,1158` (the diagnostics dialog, `installTitleBar`, new `mount`, `theme()`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java:62,73-75,146-164,227-245,420-424,473-475` (the chooser's `sheet`), `windows/package-info.java` (the `presentation` edge, fact 14)
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`composeWindow`, `rehearsalWindow`, the shells, Help), `application/AboutWindows.java:70-80`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowShellTest.java`, `jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/AboutWindowsTest.java` (one test)
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: Task 4's `WorkspaceChrome`, `WindowContent(…, WorkspaceChrome)`; 5a's `Projections`, `ShellLook`, `JasperApplication.presentation()`.
- Produces: `public record WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance)` with
  `static WindowLook of(Projections)` and `static WindowLook standard()`; `TerminalWindow(WindowCallbacks, ShellLauncher,
  Path, boolean, ThemeController, ConfigSnapshot, CommandHistory, WindowLook)`; `static MacTitleBar
  WindowContent.mount(JRootPane, WindowContent, WindowLook, Consumer<String>)`; `static MacTitleBar
  WindowContent.installTitleBar(JRootPane, WindowContent, boolean supported, boolean followTheme, Consumer<String>)`;
  `public final class ShellChrome` with `static ShellChrome of(ShellLook)`, `static ShellChrome standard()`, package
  `static ShellChrome of(ShellLook, boolean macOs)`, `boolean unifiedTitle()`, `void followTheme(JRootPane, MacTitleBar, boolean dark)`;
  package-private `boolean WindowContent.followsThemeAppearance()` (the window's configuration diagnostics dialog follows
  the theme with it); `NativeShells.sheet` becomes an instance method so the chooser's sheets follow the theme;
  `NativeShells(ThemeController, UiState, Function<UUID, Window>, Runnable, Runnable, ShellChrome)`;
  `static boolean AboutWindows.aboutInApplicationMenu(boolean macOs, boolean screenMenuBar)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowShellTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.config.ViewMode;
import java.util.ArrayDeque;
import javax.swing.JRootPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Spec 10.1 and 10.10: a terminal frame's chrome under the look, through the one mount TerminalWindow uses. */
class WindowShellTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static WindowContent window(boolean macOs, WorkspaceChrome chrome) {
        var owner = new WindowContent(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), DesktopTestSupport.HOME, false, path -> {}, () -> {}, () -> {},
            ThemeTestSupport.controller(true), dev.jasper.app.config.KeyBindings.defaults(macOs), new dev.jasper.app.commands.CommandHistory(), macOs, chrome);
        DesktopTestSupport.track(owner);
        return owner;
    }

    @Test void aNativeTitledWindowMountsItsMenusInTheWindow() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            var root = new JRootPane();
            String[] title = {""};
            var bar = WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> title[0] = value);
            assertThat(bar).as("no unified bar").isNull();
            assertThat(root.getJMenuBar()).as("the menus are a row of this window").isSameAs(owner.menuBar());
            assertThat(owner.chrome().header().getParent()).as("the header is the row under them").isSameAs(owner);
            owner.update();
            assertThat(title[0]).as("the frame carries the title").isNotBlank();
        });
    }

    @Test void theDefaultLookKeepsItsTitleBarWhereTheHostSupportsIt() throws Exception {
        edt(() -> {
            var owner = window(true, WorkspaceChrome.STANDARD);
            var root = new JRootPane();
            try (var bar = WindowContent.mount(root, owner, new WindowLook(WorkspaceChrome.STANDARD, true, false), value -> { })) {
                assertThat(bar).isNotNull();
                assertThat(root.getJMenuBar()).as("MacTitleBar.setMenuBar keeps the menus on the root, the screen menu bar's source")
                    .isSameAs(owner.menuBar());
                assertThat(root.getClientProperty("apple.awt.windowAppearance")).as("the unified bar's own setLight, as today")
                    .isEqualTo(dev.jasper.ui.theme.TitleBarTheme.of(owner.theme().chrome()).dark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
            }
        });
    }

    @Test void aNativeTitleFollowsTheThemesAppearanceOnMacOs() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            var root = new JRootPane();
            WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { });
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            owner.onThemeChanged.accept(ThemeTestSupport.resolved(false));
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            assertThat(owner.followsThemeAppearance()).as("the window's diagnostics dialog follows with it").isTrue();
            var linux = window(false, WindowChromeMenusTest.classic());
            var other = new JRootPane();
            WindowContent.mount(other, linux, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { });
            assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("only macOS has NSAppearance").isNull();
            assertThat(linux.followsThemeAppearance()).isFalse();
        });
    }

    @Test void compactUnderANativeTitleKeepsTheMenuRow() throws Exception {
        edt(() -> {
            var owner = window(true, WindowChromeMenusTest.classic());
            owner.applyConfiguration(ConfigSnapshot.defaults().toBuilder().viewMode(ViewMode.COMPACT).build(), true);
            var root = new JRootPane();
            assertThat(WindowContent.mount(root, owner, new WindowLook(WindowChromeMenusTest.classic(), false, true), value -> { })).isNull();
            assertThat(owner.chrome().header().getParent()).as("Compact hides the toolbar row").isNull();
            assertThat(root.getJMenuBar()).as("and keeps the in-window menu bar").isSameAs(owner.menuBar());
        });
    }
}
```

`DesktopTestSupport.track(owner)` registers a window built elsewhere for `closeOwners`; add it:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java"); text = path.read_text(encoding="utf-8")
old = '''    public static void closeOwners() throws Exception {'''
new = '''    /** Closes {@code owner} with the other test windows. */
    public static WindowContent track(WindowContent owner) { OWNERS.add(owner); return owner; }
    public static void closeOwners() throws Exception {'''
if text.count(old) != 1: raise SystemExit("DesktopTestSupport anchor moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java`:

```java
package dev.jasper.app.windows;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;
import org.junit.jupiter.api.Test;

/** Spec 9.1 and 10.1: the shells' values under each look. */
class ShellChromeTest {
    @Test void aNativeTitledLookFollowsTheThemeOnMacOsOnly() throws Exception {
        edt(() -> {
            var root = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, true);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameDarkAqua");
            ShellChrome.of(new ShellLook(false, true, true), true).followTheme(root, null, false);
            assertThat(root.getClientProperty("apple.awt.windowAppearance")).isEqualTo("NSAppearanceNameAqua");
            var other = new JRootPane();
            ShellChrome.of(new ShellLook(false, true, true), false).followTheme(other, null, true);
            ShellChrome.of(new ShellLook(false, false, false), true).followTheme(other, null, true);
            assertThat(other.getClientProperty("apple.awt.windowAppearance")).as("off macOS, or a look that does not follow").isNull();
        });
    }

    @Test void theTitleBarIsTheLooks() {
        assertThat(ShellChrome.of(new ShellLook(true, false, false), true).unifiedTitle()).isTrue();
        assertThat(ShellChrome.of(new ShellLook(false, true, true), true).unifiedTitle()).isFalse();
        assertThat(ShellChrome.standard().unifiedTitle()).isEqualTo(dev.jasper.app.platform.MacTitleBar.isSupported());
    }
}
```

Add the About case to `AboutWindowsTest`:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/application/AboutWindowsTest.java"); text = path.read_text(encoding="utf-8")
old = '''    @Test void helpListsTheVmOptionsActionsAfterKeyboardShortcutsAndAboutLastExceptOnMacOs() {'''
new = '''    /** Spec 10.2: About is in Help on every platform once the menus are rows in the window, macOS included. */
    @Test void aboutIsInTheApplicationMenuOnlyWhileMacOsShowsTheScreenMenuBar() {
        assertThat(AboutWindows.aboutInApplicationMenu(true, true)).isTrue();
        assertThat(AboutWindows.aboutInApplicationMenu(true, false)).isFalse();
        assertThat(AboutWindows.aboutInApplicationMenu(false, true)).isFalse();
        assertThat(AboutWindows.helpEntries(AboutWindows.aboutInApplicationMenu(true, false))).contains(new MenuEntry.Item("app.about"));
    }

    @Test void helpListsTheVmOptionsActionsAfterKeyboardShortcutsAndAboutLastExceptOnMacOs() {'''
if text.count(old) != 1: raise SystemExit("AboutWindowsTest anchor moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowShellTest' --tests 'dev.jasper.app.windows.ShellChromeTest' --tests 'dev.jasper.app.application.AboutWindowsTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `WindowLook`, `WindowContent.mount`, `ShellChrome` and
`aboutInApplicationMenu`.

- [ ] **Step 3: Write `WindowLook` and `ShellChrome`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.Projections;
import java.util.Objects;

/**
 * What a terminal window is made of under the look (Enterprise presentation spec 9.1): the workspace's chrome and the
 * shell's title facts.
 *
 * @param chrome the workspace strategies
 * @param unifiedTitle whether the frame draws the unified macOS title bar
 * @param followThemeAppearance whether a frame with the system's title bar keeps its macOS appearance in step with the theme
 */
public record WindowLook(WorkspaceChrome chrome, boolean unifiedTitle, boolean followThemeAppearance) {
    /** The chrome is required. */
    public WindowLook { Objects.requireNonNull(chrome, "chrome"); }

    /** The look of {@code projections}. */
    public static WindowLook of(Projections projections) {
        return new WindowLook(WorkspaceChrome.of(projections.workspace(), projections.title()), projections.shell().unifiedTitle(),
            projections.shell().followThemeAppearance());
    }

    /** The default look on this host. */
    public static WindowLook standard() { return new WindowLook(WorkspaceChrome.STANDARD, MacTitleBar.isSupported(), false); }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/windows/ShellChrome.java`:

```java
package dev.jasper.app.windows;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ShellLook;
import javax.swing.JRootPane;

/**
 * The window shells' chrome under the look (Enterprise presentation spec 9.1 and 10.1): the one reader of
 * {@link ShellLook}. It says whether auxiliary frames, dialogs and the file chooser draw the unified macOS title bar,
 * and keeps a frame that has the system's title bar in step with the theme's darkness on macOS. On Windows and Linux the
 * system title bar follows the operating system's light or dark setting: JBR has no hint for a title bar it does not
 * draw. EDT only.
 */
public final class ShellChrome {
    private final boolean unifiedTitle, followTheme, macOs;

    private ShellChrome(boolean unifiedTitle, boolean followTheme, boolean macOs) {
        this.unifiedTitle = unifiedTitle; this.followTheme = followTheme; this.macOs = macOs;
    }

    /** The shells of {@code look} on this host. */
    public static ShellChrome of(ShellLook look) { return of(look, SystemInfo.isMacOS); }

    /** The shells of {@code look} as on macOS or not; for tests. */
    static ShellChrome of(ShellLook look, boolean macOs) { return new ShellChrome(look.unifiedTitle(), look.followThemeAppearance(), macOs); }

    /** The default look's shells on this host. */
    public static ShellChrome standard() { return new ShellChrome(MacTitleBar.isSupported(), false, SystemInfo.isMacOS); }

    /** Whether frames and dialogs draw the unified macOS title bar. */
    public boolean unifiedTitle() { return unifiedTitle; }

    /**
     * Gives {@code root}'s frame the theme's macOS appearance when this look follows the theme and the frame has the
     * system's title bar ({@code bar} is null); otherwise does nothing.
     */
    void followTheme(JRootPane root, MacTitleBar bar, boolean dark) {
        if (!followTheme || !macOs || bar != null) return;
        root.putClientProperty("apple.awt.windowAppearance", dark ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
    }
}
```

- [ ] **Step 4: Mount the windows, the shells and the application through them**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 5 step 4: TerminalWindow, WindowContent.mount, NativeShells, JasperApplication and AboutWindows."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "TerminalWindow.java", [
    ('''    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, boolean detached,
                   ThemeController themes, ConfigSnapshot initial, CommandHistory history) {
        this.callbacks = callbacks;''',
     '''    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, boolean detached,
                   ThemeController themes, ConfigSnapshot initial, CommandHistory history) {
        this(callbacks, launcher, directory, detached, themes, initial, history, WindowLook.standard());
    }

    /** As above, under {@code look}: its workspace chrome and its title bar (Enterprise presentation spec 9.1 and 10.1). */
    public TerminalWindow(WindowCallbacks callbacks, ShellLauncher launcher, Path directory, boolean detached,
                   ThemeController themes, ConfigSnapshot initial, CommandHistory history, WindowLook look) {
        this.callbacks = callbacks;''', 1),
    ('''            KeyBindings.defaults(SystemInfo.isMacOS), history, SystemInfo.isMacOS);''',
     '''            KeyBindings.defaults(SystemInfo.isMacOS), history, SystemInfo.isMacOS, look.chrome());''', 1),
    ('''        titleBar = WindowContent.installTitleBar(frame.getRootPane(), content, MacTitleBar.isSupported(), frame::setTitle);
        if (titleBar == null) WindowContent.installCustomTitleBar(frame.getRootPane(), content);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        if (titleBar != null) titleBar.setMenuBar(content.menuBar());
        else frame.setJMenuBar(content.menuBar());''',
     '''        titleBar = WindowContent.mount(frame.getRootPane(), content, look, frame::setTitle);
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);''', 1)])
edit(W + "WindowContent.java", [
    ('''    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        MacTitleBar bar;''',
     '''    /**
     * Mounts {@code content} in a terminal frame's {@code root} under {@code look} (Enterprise presentation spec 10.1): the
     * unified macOS bar hosting the header, or the system's title bar with the header as a row in the window. Either way
     * the menu bar is the root's (the unified bar's {@code setMenuBar} puts it there too); on macOS the screen menu bar
     * shows it while the look keeps that on, and otherwise it is a row in the window. TerminalWindow and the chrome
     * renders share this path. Returns the unified bar, or null.
     */
    static MacTitleBar mount(JRootPane root, WindowContent content, WindowLook look, Consumer<String> nativeTitle) {
        MacTitleBar bar = installTitleBar(root, content, look.unifiedTitle(), look.followThemeAppearance(), nativeTitle);
        if (bar == null) installCustomTitleBar(root, content);
        if (bar != null) bar.setMenuBar(content.menuBar()); else root.setJMenuBar(content.menuBar());
        return bar;
    }

    /** Workspace adapter: wires title/theme values to the platform-only title bar. */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported,
                                      Consumer<String> nativeTitle) {
        return installTitleBar(root, content, supported, false, nativeTitle);
    }

    /**
     * As above; with {@code followTheme} a frame that has the system's title bar on macOS takes the theme's appearance,
     * as Compact does (spec 10.1).
     */
    static MacTitleBar installTitleBar(JRootPane root, WindowContent content, boolean supported, boolean followTheme,
                                      Consumer<String> nativeTitle) {
        MacTitleBar bar;''', 1),
    ('''        if (content.viewMode == ViewMode.COMPACT && content.macOs) {''',
     '''        // The window's own dialogs (the configuration diagnostics) follow with it under a native-titled look.
        content.followsThemeAppearance = bar == null && followTheme && content.macOs;
        if ((content.viewMode == ViewMode.COMPACT || bar == null && followTheme) && content.macOs) {''', 1),
    ('''    Consumer<JComponent> showConfigDiagnostics = control -> JOptionPane.showMessageDialog(
        this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);''',
     '''    /** Whether this window's macOS appearance follows the theme (a native-titled look, spec 10.1); its dialogs follow too. */
    private boolean followsThemeAppearance;
    Consumer<JComponent> showConfigDiagnostics = control -> {
        if (!followsThemeAppearance) {
            JOptionPane.showMessageDialog(this, control, "Configuration", JOptionPane.PLAIN_MESSAGE);
            return;
        }
        // As showMessageDialog does, with the frame's appearance on the option pane's dialog.
        var dialog = new JOptionPane(control, JOptionPane.PLAIN_MESSAGE).createDialog(this, "Configuration");
        dialog.getRootPane().putClientProperty("apple.awt.windowAppearance",
            theme().chrome().dark() ? "NSAppearanceNameDarkAqua" : "NSAppearanceNameAqua");
        dialog.setVisible(true);
        dialog.dispose();
    };''', 1),
    ('''    ResolvedTheme theme() { return themes.current(); }''',
     '''    ResolvedTheme theme() { return themes.current(); }
    boolean followsThemeAppearance() { return followsThemeAppearance; }''', 1)])
N = "jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java"
edit(N, [
    ('''    private final Map<AuxiliarySurface, Window> natives = new HashMap<>();''',
     '''    private final Map<AuxiliarySurface, Window> natives = new HashMap<>();
    private final ShellChrome shell;''', 1),
    ('''    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit) {
        this.themes = Objects.requireNonNull(themes); this.state = Objects.requireNonNull(state);''',
     '''    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit) {
        this(themes, state, terminalWindows, newWindow, quit, ShellChrome.standard());
    }

    /**
     * As above, under the look's shells (Enterprise presentation spec 10.1).
     *
     * @param themes the application's look
     * @param state remembered window bounds
     * @param terminalWindows finds a terminal window's native window by id, or returns null
     * @param newWindow opens a terminal window
     * @param quit quits the application
     * @param shell the look's title bar and appearance
     */
    public NativeShells(ThemeController themes, UiState state, Function<UUID, Window> terminalWindows,
                        Runnable newWindow, Runnable quit, ShellChrome shell) {
        this.shell = Objects.requireNonNull(shell);
        this.themes = Objects.requireNonNull(themes); this.state = Objects.requireNonNull(state);''', 1),
    ('''            resources.layout = installChrome(frame.getRootPane(), surface, MacTitleBar.isSupported());
            MacTitleBar bar = resources.layout.bar();
            if (bar != null) bar.setMenuBar(menuBar(surface)); else frame.setJMenuBar(menuBar(surface));''',
     '''            resources.layout = installChrome(frame.getRootPane(), surface, shell.unifiedTitle());
            MacTitleBar bar = resources.layout.bar();
            // Under in-window menus (spec 10.1) the minimal File menu is a row of this frame, so Close Window keeps its key.
            if (bar != null) bar.setMenuBar(menuBar(surface)); else frame.setJMenuBar(menuBar(surface));
            shell.followTheme(frame.getRootPane(), bar, themes.current().chrome().dark());''', 1),
    ('''                if (bar != null) bar.setLight(!resolved.chrome().dark());
                resources.layout.body().changed();''',
     '''                if (bar != null) bar.setLight(!resolved.chrome().dark());
                shell.followTheme(frame.getRootPane(), bar, resolved.chrome().dark());
                resources.layout.body().changed();''', 1),
    ('''            resources.layout = installChrome(dialog.getRootPane(), surface, MacTitleBar.isSupported());
            MacTitleBar bar = resources.layout.bar();''',
     '''            resources.layout = installChrome(dialog.getRootPane(), surface, shell.unifiedTitle());
            MacTitleBar bar = resources.layout.bar();
            shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());''', 1),
    ('''                if (bar != null) bar.setLight(!resolved.chrome().dark());
                if (resources.attachment != null) resources.attachment.changed();''',
     '''                if (bar != null) bar.setLight(!resolved.chrome().dark());
                shell.followTheme(dialog.getRootPane(), bar, resolved.chrome().dark());
                if (resources.attachment != null) resources.attachment.changed();''', 1),
    ('''        MacTitleBar bar = MacTitleBar.install(dialog.getRootPane(), panel, () -> { }, MacTitleBar.isSupported());
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
        });''',
     '''        MacTitleBar bar = MacTitleBar.install(dialog.getRootPane(), panel, () -> { }, shell.unifiedTitle());
        shell.followTheme(dialog.getRootPane(), bar, themes.current().chrome().dark());
        Subscription theme = themes.subscribe((resolved, chromeChanged) -> {
            if (chromeChanged) SwingUtilities.updateComponentTreeUI(dialog);
            if (bar != null) bar.setLight(!resolved.chrome().dark());
            shell.followTheme(dialog.getRootPane(), bar, resolved.chrome().dark());
        });''', 1),
    # The chooser's replace and new-folder sheets are native-titled dialogs too (spec 10.1); each lives for one
    # question, so it takes the appearance when it opens.
    ('''    private static ChooserPrompts.Modal sheet(Window owner, String title) {
        var window = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
        window.setIconImages(ApplicationIcon.images());''',
     '''    private ChooserPrompts.Modal sheet(Window owner, String title) {
        var window = new JDialog(owner, title, Dialog.ModalityType.DOCUMENT_MODAL);
        shell.followTheme(window.getRootPane(), null, themes.current().chrome().dark());
        window.setIconImages(ApplicationIcon.images());''', 1)])
A = "jasper-app/src/main/java/dev/jasper/app/application/"
edit(A + "JasperApplication.java", [
    ('''                launcher, directory, detached, themes, configuration == null ? null : windowConfiguration(), history);''',
     '''                launcher, directory, detached, themes, configuration == null ? null : windowConfiguration(), history, windowLook());''', 1),
    ('''        TerminalWindow window = new TerminalWindow(Rehearsal.CALLBACKS, Rehearsal.launcher(feed), null, false, themes, snapshot,
            new CommandHistory());''',
     '''        TerminalWindow window = new TerminalWindow(Rehearsal.CALLBACKS, Rehearsal.launcher(feed), null, false, themes, snapshot,
            new CommandHistory(), windowLook());''', 1),
    ('''        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(), this::quit);''',
     '''        shells = new NativeShells(themes, uiState, this::nativeWindow,
            () -> newWindow(), this::quit, dev.jasper.app.windows.ShellChrome.of(presentation.projections().shell()));''', 1),
    ('''.set(AboutWindows.helpEntries(macOs));''',
     '''.set(AboutWindows.helpEntries(AboutWindows.aboutInApplicationMenu(macOs, presentation.committedDesktop().screenMenuBar())));''', 1),
    ('''    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }''',
     '''    public dev.jasper.app.presentation.Presentation presentation() { return presentation; }

    /** What every terminal window of this process is made of: the presentation's workspace chrome and title bar. */
    private dev.jasper.app.workspace.WindowLook windowLook() { return dev.jasper.app.workspace.WindowLook.of(presentation.projections()); }''', 1)])
edit(A + "AboutWindows.java", [
    ('''    /**
     * Help's entries: Keyboard Shortcuts; Change Memory Settings… and Edit Custom VM Options… in a section of their own;
     * then About Jasper except on macOS, where About is in the application menu.
     */
    static List<MenuEntry> helpEntries(boolean macOs) {''',
     '''    /** Whether About Jasper lives in the application menu instead of Help: on macOS while it shows the screen menu bar. */
    static boolean aboutInApplicationMenu(boolean macOs, boolean screenMenuBar) { return macOs && screenMenuBar; }

    /**
     * Help's entries: Keyboard Shortcuts; Change Memory Settings… and Edit Custom VM Options… in a section of their own;
     * then About Jasper unless {@code macOs} says it is in the application menu ({@link #aboutInApplicationMenu}).
     */
    static List<MenuEntry> helpEntries(boolean macOs) {''', 1)])
PY
```

Add the `windows → presentation` edge (spec 9.2; 5a did not add it, fact 14):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/windows/package-info.java"); text = path.read_text(encoding="utf-8")
old = "dev.jasper.app.persistence, dev.jasper.app.platform.\n"
if text.count(old) != 1: raise SystemExit("windows package-info: the dependency list moved")
path.write_text(text.replace(old, "dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.presentation.\n"), encoding="utf-8")
PY
```

`TerminalWindow` still imports `MacTitleBar` for its field and `SystemInfo` for the platform; both stay used.

Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
rows = [("jasper-app/src/main/java/dev/jasper/app/workspace/WindowLook.java", "app.workspace", "WindowLook",
         "jasper-app/src/test/java/dev/jasper/app/workspace/WindowShellTest.java", "New in Enterprise presentation Plan 6a Task 5; a terminal window's chrome and title facts"),
        ("jasper-app/src/main/java/dev/jasper/app/windows/ShellChrome.java", "app.windows", "ShellChrome",
         "jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java", "New in Enterprise presentation Plan 6a Task 5; the one reader of ShellLook; NSAppearance"),
        ("jasper-app/src/test/java/dev/jasper/app/workspace/WindowShellTest.java", "app.workspace", "WindowShellTest",
         "jasper-app/src/test/java/dev/jasper/app/workspace/WindowShellTest.java", "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 5; the frame mount under each look"),
        ("jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java", "app.windows", "ShellChromeTest",
         "jasper-app/src/test/java/dev/jasper/app/windows/ShellChromeTest.java", "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 5; the shells' values")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 5: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.windows.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' --tests 'dev.jasper.app.architecture.*' :jasper-app:verifyApplicationArchitecture :jasper-app:javadoc`
Expected: PASS: `WindowShellTest` 4, `ShellChromeTest` 2, `AboutWindowsTest` with the new case; every existing
`MacTitleBarTest`, `NativeShellsChromeTest`, `RehearsalTest` and `JasperApplication*Test` case (the standard projection
gives `ShellChrome.standard()`'s values, `WindowLook.standard()`'s on the default look, and About where it was).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/windows \
  jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/test/java/dev/jasper/app docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the window shell

A terminal window takes the look's title bar through WindowLook: the
unified macOS bar only where the look asks for it, otherwise the system
title bar with the menus as a row in the window; a native-titled macOS
frame follows the theme's appearance, and Compact keeps the menu row.
ShellChrome gives auxiliary windows, dialogs, the file chooser and its
sheets the same values, and a window's diagnostics dialog follows it.
About Jasper joins Help when the screen menu bar is off. Every
window, the rehearsal's included, gets the look.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: Motion

`Motion.NONE` turns off the tab-strip motion, the palette reveal and the overlay fade whatever `ui.animations.enabled`
says (spec 10.9). The application's one animations supplier is `LookMotion`, the only reader of `Motion`: the
configuration controller sets `ThemeController`'s flag to `motion == STANDARD && setting`, which `AppearanceBindings`
(every `KitContext.motionPolicy()`), `PluginRuntime.animations` and now the window's tabs and palette read (Rulings 11).
The saved setting is untouched.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/application/LookMotion.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/ConfigurationController.java:105-110,273`, `JasperApplication.java` (the controller)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (`lookAnimates`), `WindowTabs.java:81`, `WindowCommandPalette.java:138`
- Test: `jasper-app/src/test/java/dev/jasper/app/application/LookMotionTest.java`, `jasper-app/src/test/java/dev/jasper/app/application/MotionReachTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row: `MotionReachTest` imports `dev.jasper.ui.theme` and `SystemAppearance`)

**Interfaces:**
- Consumes: 5a's `Motion`, `Projections.motion()`.
- Produces: `record LookMotion(Motion motion)` (package-private, application) with `STANDARD`, `static LookMotion
  of(Projections)` and `boolean allows(boolean setting)`; `ConfigurationController(ThemeController, ConfigService,
  Consumer<Path>, SystemAppearance.Observation, Path, LookMotion)`; package-private `boolean WindowContent.lookAnimates()`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/LookMotionTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.Motion;
import org.junit.jupiter.api.Test;

/** Spec 10.9: the look's motion and the setting, folded once. */
class LookMotionTest {
    @Test void aStillLookNeverMoves() {
        assertThat(new LookMotion(Motion.NONE).allows(true)).isFalse();
        assertThat(new LookMotion(Motion.NONE).allows(false)).isFalse();
    }

    @Test void theDefaultLookLeavesItToTheSetting() {
        assertThat(LookMotion.STANDARD.allows(true)).isTrue();
        assertThat(LookMotion.STANDARD.allows(false)).isFalse();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/application/MotionReachTest.java`:

```java
package dev.jasper.app.application;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.ConfigService;
import dev.jasper.app.platform.SystemAppearance;
import dev.jasper.app.presentation.Motion;
import dev.jasper.ui.theme.ThemeRoute;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.9: the one supplier reaches the kit contexts, and a saved setting survives a still look. */
class MotionReachTest {
    @TempDir Path dir;

    boolean[] run(Motion motion) throws Exception {
        Path file = dir.resolve(motion + ".toml");
        Files.writeString(file, "[ui.animations]\nenabled = true\n");
        var worker = Executors.newSingleThreadScheduledExecutor();
        boolean[] seen = new boolean[3];
        try (var service = new ConfigService(file, true, worker, SwingUtilities::invokeLater)) {
            edt(() -> {
                var themes = ThemeTestSupport.controller(true, theme -> true);
                var controller = new ConfigurationController(themes, service, path -> { },
                    SystemAppearance.Observation.unavailable("none in tests"), null, new LookMotion(motion));
                seen[0] = themes.animationsEnabled();
                seen[1] = themes.appearance().context(ThemeRoute.CHROME).motionPolicy().enabled();
                seen[2] = controller.snapshot().animations().enabled();
                controller.close(); themes.close();
            });
        } finally { worker.shutdownNow(); }
        return seen;
    }

    @Test void noMotionWinsOverTheSettingAndTheSettingStaysSaved() throws Exception {
        assertThat(run(Motion.NONE)).containsExactly(false, false, true);
        assertThat(run(Motion.STANDARD)).as("the default look moves as the setting says").containsExactly(true, true, true);
    }
}
```

If `ui.animations.enabled` is spelled otherwise in `config.example.toml` (the key `CoreSettings.UI_ANIMATIONS_ENABLED`
names it), use that spelling in the file the test writes.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.LookMotionTest' --tests 'dev.jasper.app.application.MotionReachTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … LookMotion".

- [ ] **Step 3: The supplier and its consumers**

Create `jasper-app/src/main/java/dev/jasper/app/application/LookMotion.java`:

```java
package dev.jasper.app.application;

import dev.jasper.app.presentation.Motion;
import dev.jasper.app.presentation.Projections;
import java.util.Objects;

/**
 * The application's one animations supplier (Enterprise presentation spec 10.9): motion runs only when the look moves
 * and the user's setting allows it. The only reader of {@link Motion}; the setting itself is never changed.
 *
 * @param motion the look's motion
 */
record LookMotion(Motion motion) {
    /** The default look: the setting decides. */
    static final LookMotion STANDARD = new LookMotion(Motion.STANDARD);

    /** The motion is required. */
    LookMotion { Objects.requireNonNull(motion, "motion"); }

    /** The look of {@code projections}. */
    static LookMotion of(Projections projections) { return new LookMotion(projections.motion()); }

    /** Whether anything may move, given the user's {@code setting}. */
    boolean allows(boolean setting) { return motion == Motion.STANDARD && setting; }
}
```

```bash
python3 -I - <<'PY'
"""Plan 6a Task 6 step 3: the configuration controller folds the look's motion; windows read the one flag."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
A = "jasper-app/src/main/java/dev/jasper/app/application/"
edit(A + "ConfigurationController.java", [
    ('''    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor,
                            dev.jasper.app.platform.SystemAppearance.Observation initialAppearance, Path themesFolder) {
        this.systemAppearance = Objects.requireNonNull(initialAppearance);''',
     '''    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor,
                            dev.jasper.app.platform.SystemAppearance.Observation initialAppearance, Path themesFolder) {
        this(themes, service, editor, initialAppearance, themesFolder, LookMotion.STANDARD);
    }

    /** The look's motion, folded into the animations setting (spec 10.9). */
    private final LookMotion motion;

    /** As above, under the look's {@code motion}: the animations flag is the setting only while the look moves. */
    ConfigurationController(ThemeController themes, ConfigService service, Consumer<Path> editor,
                            dev.jasper.app.platform.SystemAppearance.Observation initialAppearance, Path themesFolder, LookMotion motion) {
        this.motion = Objects.requireNonNull(motion, "motion");
        this.systemAppearance = Objects.requireNonNull(initialAppearance);''', 1),
    ('''        themes.configureAnimations(snapshot.animations().enabled());''',
     '''        themes.configureAnimations(motion.allows(snapshot.animations().enabled()));''', 1)])
edit(A + "JasperApplication.java", [
    ('''        configuration = service == null ? null : new ConfigurationController(themes, service, this::openForEditing,
            systemAppearance.current(), themesFolder);''',
     '''        configuration = service == null ? null : new ConfigurationController(themes, service, this::openForEditing,
            systemAppearance.current(), themesFolder, LookMotion.of(presentation.projections()));
        // Without a configuration the setting is its default, on; the look still decides (spec 10.9).
        if (configuration == null) themes.configureAnimations(LookMotion.of(presentation.projections()).allows(true));''', 1)])
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "WindowContent.java", [
    ('''    WorkspaceChrome look() { return look; }''',
     '''    WorkspaceChrome look() { return look; }
    /** The application's one animations flag: the look's motion folded into the setting (spec 10.9). EDT. */
    boolean lookAnimates() { return themes.animationsEnabled(); }''', 1)])
edit(W + "WindowTabs.java", [
    ('''            if (order.size() <= 1 || !added && !removed || !animations.enabled() || layoutWidth <= 0) millis = 0;''',
     '''            if (order.size() <= 1 || !added && !removed || !animations.enabled() || !owner.lookAnimates() || layoutWidth <= 0) millis = 0;''', 1)])
edit(W + "WindowCommandPalette.java", [
    ('''        stopReveal();
        if (!animations.enabled()) return;''',
     '''        stopReveal();
        if (!animations.enabled() || !owner.lookAnimates()) return;''', 1)])
PY
```

`LookMotion` and `LookMotionTest` name no Swing, UI or appearance type, so they need no ledger row; `MotionReachTest`
imports `dev.jasper.ui.theme` and `SystemAppearance`, so it gets its row:

```bash
python3 -I - <<'PY'
import pathlib
test = "jasper-app/src/test/java/dev/jasper/app/application/MotionReachTest.java"
rows = [(test, "app.application", "MotionReachTest", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 6; the one motion supplier reaches the kit contexts")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.workspace.WindowTabs*' --tests 'dev.jasper.app.workspace.WindowCommandPalette*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `LookMotionTest` 2, `MotionReachTest` 1, every existing tab-motion and palette-reveal test (their
controllers keep the flag on, so their settings still decide).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/application jasper-app/src/main/java/dev/jasper/app/workspace \
  jasper-app/src/test/java/dev/jasper/app/application/LookMotionTest.java jasper-app/src/test/java/dev/jasper/app/application/MotionReachTest.java \
  docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: motion

LookMotion is the application's one animations supplier and the only
reader of Motion: the configuration controller sets the theme
controller's flag to the look's motion folded into ui.animations.enabled.
Kit contexts, plugins, the tab strip and the palette reveal all read that
flag, so a still look never moves while the saved setting stays as it was.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: The window title

The frame title follows the look's `TitleFormat` (spec 10.4): `{context}` is today's label, `{session}` the active
pane's session or a hosted document's title, `{directory}` its directory with `~` for home or the document's location;
a token with no value goes with its separator (Rulings 10). The title search field keeps today's context. The frame
title still starts as "Jasper".

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:316-325` (the `onTitle` wiring)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowTitleTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (one row: `WindowTitle`'s `new ArrayList` matches `CANDIDATE`'s `new …List`)

**Interfaces:**
- Consumes: Task 4's `WorkspaceChrome.title()`; 5a's `TitleFormat`.
- Produces: `final class WindowTitle` with `static String format(TitleFormat, String context, String session, String
  directory)`, `static String session(PaneSnapshot)` and `static String directory(PaneSnapshot, Path home)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowTitleTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.terminals.PaneSnapshot;
import dev.jasper.app.terminals.RemoteLocation;
import java.nio.file.Path;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

/** Spec 10.4: the frame title from the look's template, as the Workstation mocks show it. */
class WindowTitleTest {
    static final TitleFormat WORKSTATION = new TitleFormat("Workstation — {session} — {directory}");
    static final Path HOME = Path.of(System.getProperty("user.home"));

    static PaneSnapshot pane(Optional<Path> directory, Optional<String> provider, Optional<RemoteLocation> remote, String shell) {
        return new PaneSnapshot("", directory, 80, 24, true, PaneSnapshot.State.RUNNING, OptionalInt.empty(), provider, remote, shell);
    }

    @Test void theDefaultTemplateIsTodaysLabel() {
        assertThat(WindowTitle.format(new TitleFormat("{context}"), "dustin@~", "Local: zsh", "~")).isEqualTo("dustin@~");
    }

    @Test void theWorkstationTemplateReadsAsTheMocks() {
        assertThat(WindowTitle.format(WORKSTATION, "x", "Mac", "~")).isEqualTo("Workstation — Mac — ~");
        assertThat(WindowTitle.format(WORKSTATION, "x", "Local: zsh", "~")).isEqualTo("Workstation — Local: zsh — ~");
    }

    @Test void anEmptyTokenGoesWithItsSeparator() {
        assertThat(WindowTitle.format(WORKSTATION, "x", "Mac", "")).isEqualTo("Workstation — Mac");
        assertThat(WindowTitle.format(WORKSTATION, "x", "", "~")).isEqualTo("Workstation — ~");
        assertThat(WindowTitle.format(WORKSTATION, "x", "  ", "")).isEqualTo("Workstation");
        assertThat(WindowTitle.format(new TitleFormat("{session} | {directory}"), "x", "", "~/src")).isEqualTo("~/src");
    }

    @Test void theTokensComeFromThePaneOrTheDocument() {
        var local = pane(Optional.of(HOME.resolve("src")), Optional.empty(), Optional.empty(), "zsh");
        assertThat(WindowTitle.session(local)).isEqualTo("Local: zsh");
        assertThat(WindowTitle.directory(local, HOME)).isEqualTo("~/src");
        assertThat(WindowTitle.directory(pane(Optional.of(HOME), Optional.empty(), Optional.empty(), "zsh"), HOME)).isEqualTo("~");
        var provided = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.empty(), "Mac");
        assertThat(WindowTitle.session(provided)).as("a saved identity names the session").isEqualTo("Mac");
        assertThat(WindowTitle.directory(provided, HOME)).as("no reported directory yet").isEmpty();
        var reported = pane(Optional.empty(), Optional.of("dev.jasper.remote"), Optional.of(new RemoteLocation("mbp", "/srv/www")), "Mac");
        assertThat(WindowTitle.directory(reported, HOME)).isEqualTo("/srv/www");
        var osc = pane(Optional.empty(), Optional.empty(), Optional.of(new RemoteLocation("build-01", "/tmp")), "zsh");
        assertThat(WindowTitle.session(osc)).as("an OSC host names a remote pane").isEqualTo("build-01");
        assertThat(WindowTitle.session(null)).isEmpty();
        assertThat(WindowTitle.directory(null, HOME)).isEmpty();
        assertThat(WindowTitle.session(pane(Optional.empty(), Optional.empty(), Optional.empty(), "bad\u0007name"))).isEqualTo("Local: bad name");
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WindowTitleTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol … WindowTitle".

- [ ] **Step 3: Write `WindowTitle` and use it**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.presentation.TitleFormat;
import dev.jasper.app.terminals.PaneSnapshot;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The frame title from the look's template (Enterprise presentation spec 10.4). The text between two tokens separates
 * them: a token with no value goes with the separator before it, or after it when it comes first, and a template whose
 * tokens are all empty keeps its leading text without a trailing separator.
 */
final class WindowTitle {
    private static final Pattern TOKEN = Pattern.compile("\\{(context|session|directory)\\}");
    /** Spaces and the separators a template may end its leading text with. */
    private static final Pattern TRAILING = Pattern.compile("[\\s—–·:|-]+$");

    private WindowTitle() { }

    /** {@code format} with its tokens filled in. */
    static String format(TitleFormat format, String context, String session, String directory) {
        var values = Map.of("context", Objects.requireNonNull(context), "session", Objects.requireNonNull(session),
            "directory", Objects.requireNonNull(directory));
        String template = format.template();
        var literals = new ArrayList<String>();
        var tokens = new ArrayList<String>();
        Matcher matcher = TOKEN.matcher(template);
        int at = 0;
        while (matcher.find()) {
            literals.add(template.substring(at, matcher.start()));
            tokens.add(values.get(matcher.group(1)).strip());
            at = matcher.end();
        }
        literals.add(template.substring(at));
        // literals: the leading text, then the separator before each later token, then the trailing text.
        var out = new StringBuilder(literals.getFirst());
        boolean any = false;
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).isEmpty()) continue;
            if (any) out.append(literals.get(i));
            out.append(tokens.get(i));
            any = true;
        }
        if (!any) return TRAILING.matcher(literals.getFirst()).replaceAll("") + literals.getLast();
        return out.append(literals.getLast()).toString();
    }

    /** {@code {session}}: a provided session's saved identity, a remote pane's host, else "Local: <shell>". */
    static String session(PaneSnapshot pane) {
        if (pane == null) return "";
        if (pane.providerId().isPresent()) return clean(pane.shell());
        if (pane.remoteDirectory().isPresent()) return clean(pane.remoteDirectory().get().host());
        String shell = clean(pane.shell());
        return shell.isEmpty() ? "Local" : "Local: " + shell;
    }

    /** {@code {directory}}: a remote pane's reported path as it is, a local pane's with {@code ~} for {@code home}. */
    static String directory(PaneSnapshot pane, Path home) {
        if (pane == null) return "";
        if (pane.providerId().isPresent() || pane.remoteDirectory().isPresent())
            return pane.remoteDirectory().map(location -> clean(location.path())).orElse("");
        Path directory = pane.workingDirectory().orElse(home).toAbsolutePath().normalize();
        Path absoluteHome = home.toAbsolutePath().normalize();
        String path = directory.equals(absoluteHome) ? "~" : directory.startsWith(absoluteHome)
            ? "~/" + absoluteHome.relativize(directory).toString().replace('\\', '/') : directory.toString();
        return clean(path);
    }

    private static String clean(String text) {
        var result = new StringBuilder();
        text.codePoints().forEach(point -> result.appendCodePoint(Character.isISOControl(point) ? ' ' : point));
        return result.toString().strip();
    }
}
```

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java"); text = path.read_text(encoding="utf-8")
old = '''            nativeTitle.accept(context.label());'''
new = '''            // The frame title follows the look's template (spec 10.4); the title field keeps the context.
            var snapshot = pane == null ? null : pane.snapshot();
            Path home = Path.of(System.getProperty("user.home"));
            nativeTitle.accept(WindowTitle.format(content.look().title(), context.label(),
                document != null ? document.title() : WindowTitle.session(snapshot),
                document != null ? document.location() : WindowTitle.directory(snapshot, home)));'''
if text.count(old) != 1: raise SystemExit("WindowContent: the native title line moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

Add the ledger row (`WindowTitleTest` names no candidate type):

```bash
python3 -I - <<'PY'
import pathlib
rows = [("jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java", "app.workspace", "WindowTitle",
         "jasper-app/src/test/java/dev/jasper/app/workspace/WindowTitleTest.java", "New in Enterprise presentation Plan 6a Task 7; the frame title from the look's template")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS: `WindowTitleTest` 4; every existing title and header test (under `{context}` the frame title is the
label as before).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowTitle.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/WindowTitleTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the window title

WindowTitle fills the look's title template: {context} is today's label,
{session} the active pane's saved identity, remote host or Local: <shell>
(or a document's title), {directory} its directory with ~ for home (or a
document's location). A token with no value goes with its separator. The
default template keeps today's title.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: The chrome renders

Spec 16.1.4's chrome renders start here, as JUnit and headless: a window under the default, Enterprise and complement
kits, the fallback combination (native title, window menus, default kit) and the eight rows of a checked-in strength-2
covering array over the nine chrome points, each mounted through `WindowContent.mount` and printed at 2× into
`jasper-app/build/chrome-renders/`, asserting structure (the content meets the menu row flush, no gutter above the
header, the composition the look names, Help last). `MockProfile` compares Enterprise renders with the Workstation and
Dark Mode mocks by profile (Rulings 12), and this task compares the menu row; plan 6b adds the toolbar, tabs, status
and dialogs. `tools/visual/pairs.json` gains the opt-in crops.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java`
- Create: `jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv`
- Modify: `tools/visual/pairs.json` (two entries appended), `tools/visual/README.md` (one sentence), `jasper-app/build.gradle.kts` (`tasks.test` inputs), `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: Tasks 4 to 7; Task 4's `EnterpriseLook` and `DesktopTestSupport.closeOwnersNow()`; 4a's `KitFixtures.complement()`; 5a's `Projections`, `ChromeSpec`.
- Produces: `public final class MockProfile` with `ROOT`, `REFERENCE`, `RENDERS`, `record Run(int start, int length, Color
  color)`, `record Ink(int start, int end, int top, int bottom)`, `static BufferedImage mock(String)`, `static List<Run>
  column(BufferedImage, int x, int from, int to)`, `static List<Run> row(BufferedImage, int y, int from, int to)`,
  `static List<Run> solid(List<Run>, int minimum)`, `static List<Ink> inks(BufferedImage, Rectangle, int gap, double
  threshold)`, `static double deltaE(Color, Color)`, `static Color at(BufferedImage, int, int)`, `static BufferedImage
  print(JComponent, int width, int height)`, `static Path write(BufferedImage, String name)`;
  `ChromeRenderTest.cases()` and `ChromeRenderTest.Case(String name, Supplier<ThemeController> themes, Projections
  projections)` for plan 6b; `EnterpriseMenuRowMockTest.render(boolean dark)` (a 2× print of a 1440 × 871 Enterprise
  window, the mock's content area) and `EnterpriseMenuRowMockTest.workstation()` (the mocks' menus as contributions).

- [ ] **Step 1: Write the comparison support, the covering array and the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java`:

```java
package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Compares a 2× render with a 2× reference mock by profile (Enterprise presentation plans 6 to 10): runs of one colour
 * along a column or a row, and clusters of ink along a row. Colours compare as CIE76 ΔE in L*a*b* (D65). A profile that
 * differs is a chrome defect or a recorded residual.
 */
public final class MockProfile {
    /** The repository root, as the test task names it. */
    public static final Path ROOT = Path.of(System.getProperty("jasper.repoRoot", "..")).toAbsolutePath().normalize();
    /** The preserved Enterprise mocks. */
    public static final Path REFERENCE = ROOT.resolve("docs/design/enterprise/reference");
    /** Where chrome renders are written for inspection and the opt-in pairs. */
    public static final Path RENDERS = ROOT.resolve("jasper-app/build/chrome-renders");

    private MockProfile() { }

    /**
     * Pixels of one colour in a row or a column.
     *
     * @param start the first pixel
     * @param length how many
     * @param color the first pixel's colour
     */
    public record Run(int start, int length, Color color) { }

    /**
     * Adjacent columns holding ink (a pixel unlike the region's background).
     *
     * @param start the first column
     * @param end one past the last column
     * @param top the first inked row
     * @param bottom one past the last inked row
     */
    public record Ink(int start, int end, int top, int bottom) {
        /** The cluster's width. */
        public int width() { return end - start; }
    }

    /** The mock at {@code relative} under {@link #REFERENCE}. */
    public static BufferedImage mock(String relative) {
        try { return ImageIO.read(REFERENCE.resolve(relative).toFile()); }
        catch (IOException failure) { throw new UncheckedIOException(failure); }
    }

    /** The opaque colour at {@code (x, y)}. */
    public static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y) & 0xFFFFFF); }

    /** Runs down column {@code x} from {@code from} (inclusive) to {@code to} (exclusive); a pixel within ΔE 1 joins its run. */
    public static List<Run> column(BufferedImage image, int x, int from, int to) { return runs(image, x, from, to, true); }

    /** Runs along row {@code y} from {@code from} to {@code to}. */
    public static List<Run> row(BufferedImage image, int y, int from, int to) { return runs(image, y, from, to, false); }

    private static List<Run> runs(BufferedImage image, int fixed, int from, int to, boolean vertical) {
        var runs = new ArrayList<Run>();
        int start = from;
        Color first = vertical ? at(image, fixed, from) : at(image, from, fixed);
        for (int i = from + 1; i <= to; i++) {
            Color next = i == to ? null : vertical ? at(image, fixed, i) : at(image, i, fixed);
            if (next != null && deltaE(next, first) <= 1) continue;
            runs.add(new Run(start, i - start, first));
            start = i; first = next;
        }
        return runs;
    }

    /** The runs at least {@code minimum} long: antialiased edges and text drop out. */
    public static List<Run> solid(List<Run> runs, int minimum) { return runs.stream().filter(run -> run.length() >= minimum).toList(); }

    /**
     * The ink clusters in {@code region}: a column holds ink where a pixel is more than {@code threshold} ΔE from the
     * region's top-left pixel, and columns at most {@code gap} apart join a cluster.
     */
    public static List<Ink> inks(BufferedImage image, Rectangle region, int gap, double threshold) {
        Color background = at(image, region.x, region.y);
        var inks = new ArrayList<Ink>();
        int start = -1, last = -1, top = Integer.MAX_VALUE, bottom = -1;
        for (int x = region.x; x < region.x + region.width; x++) {
            int columnTop = -1, columnBottom = -1;
            for (int y = region.y; y < region.y + region.height; y++)
                if (deltaE(at(image, x, y), background) > threshold) { if (columnTop < 0) columnTop = y; columnBottom = y + 1; }
            if (columnTop < 0) continue;
            if (start >= 0 && x - last > gap) { inks.add(new Ink(start, last + 1, top, bottom)); start = -1; top = Integer.MAX_VALUE; bottom = -1; }
            if (start < 0) start = x;
            last = x; top = Math.min(top, columnTop); bottom = Math.max(bottom, columnBottom);
        }
        if (start >= 0) inks.add(new Ink(start, last + 1, top, bottom));
        return inks;
    }

    /** CIE76 ΔE between two opaque sRGB colours. */
    public static double deltaE(Color a, Color b) {
        double[] p = lab(a), q = lab(b);
        return Math.sqrt((p[0] - q[0]) * (p[0] - q[0]) + (p[1] - q[1]) * (p[1] - q[1]) + (p[2] - q[2]) * (p[2] - q[2]));
    }

    private static double[] lab(Color color) {
        double[] rgb = {color.getRed() / 255.0, color.getGreen() / 255.0, color.getBlue() / 255.0};
        for (int i = 0; i < 3; i++) rgb[i] = rgb[i] <= .04045 ? rgb[i] / 12.92 : Math.pow((rgb[i] + .055) / 1.055, 2.4);
        double x = (.4124 * rgb[0] + .3576 * rgb[1] + .1805 * rgb[2]) / .95047, y = .2126 * rgb[0] + .7152 * rgb[1] + .0722 * rgb[2],
            z = (.0193 * rgb[0] + .1192 * rgb[1] + .9505 * rgb[2]) / 1.08883;
        double fx = f(x), fy = f(y), fz = f(z);
        return new double[] {116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz)};
    }

    private static double f(double t) { return t > .008856 ? Math.cbrt(t) : 7.787 * t + 16 / 116.0; }

    /** {@code root} laid out at {@code width} × {@code height} logical pixels and printed at 2×. EDT. */
    public static BufferedImage print(JComponent root, int width, int height) {
        root.setSize(width, height);
        LayoutTestSupport.layoutTree(root);
        LayoutTestSupport.layoutTree(root);
        var image = new BufferedImage(width * 2, height * 2, BufferedImage.TYPE_INT_RGB);
        var graphics = image.createGraphics();
        try { graphics.scale(2, 2); root.printAll(graphics); } finally { graphics.dispose(); }
        return image;
    }

    /** Writes {@code image} as {@code <name>.png} under {@link #RENDERS}. */
    public static Path write(BufferedImage image, String name) {
        try {
            Files.createDirectories(RENDERS);
            Path file = RENDERS.resolve(name + ".png");
            ImageIO.write(image, "png", file.toFile());
            return file;
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

Create `jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv` (Rulings 12: row 0 the
default look; every pair of the nine points' values appears in some row, except `menu_bar = window` with
`title_bar = unified`, which spec 5.3 refuses):

```text
# title_bar	menu_bar	window_title	tool_windows	editor_tabs	status_bar	palette	dialogs	motion
unified	system	{context}	rails	equal	segments	anchored	as_requested	standard
native	system	Workstation — {session} — {directory}	stripes	classic	cells	run_window	classic	none
native	window	{context}	stripes	equal	segments	anchored	classic	none
native	window	Workstation — {session} — {directory}	rails	classic	cells	run_window	as_requested	standard
unified	system	{context}	stripes	classic	segments	run_window	as_requested	none
unified	system	Workstation — {session} — {directory}	stripes	equal	cells	anchored	classic	standard
native	system	{context}	rails	equal	cells	run_window	classic	none
native	window	Workstation — {session} — {directory}	stripes	classic	segments	anchored	classic	standard
```

The columns are separated by one tab character each (the file has no other tabs); write it with a script so the tabs are real:

```bash
python3 -I - <<'PY'
import pathlib
W = "Workstation — {session} — {directory}"
rows = ["unified system {context} rails equal segments anchored as_requested standard",
        f"native system {W} stripes classic cells run_window classic none",
        "native window {context} stripes equal segments anchored classic none",
        f"native window {W} rails classic cells run_window as_requested standard",
        "unified system {context} stripes classic segments run_window as_requested none",
        f"unified system {W} stripes equal cells anchored classic standard",
        "native system {context} rails equal cells run_window classic none",
        f"native window {W} stripes classic segments anchored classic standard"]
def cells(row):
    parts = row.split(" ")
    if "Workstation" in parts:
        i = parts.index("Workstation"); parts[i:i + 5] = [" ".join(parts[i:i + 5])]
    return parts
out = "# title_bar\tmenu_bar\twindow_title\ttool_windows\teditor_tabs\tstatus_bar\tpalette\tdialogs\tmotion\n"
out += "".join("\t".join(cells(row)) + "\n" for row in rows)
path = pathlib.Path("jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv")
path.parent.mkdir(parents=True, exist_ok=True); path.write_text(out, encoding="utf-8")
assert all(len(line.split("\t")) == 9 for line in out.splitlines()), "nine columns per line"
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import com.formdev.flatlaf.util.SystemInfo;
import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.platform.MacTitleBar;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.DesktopFlags;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.testsupport.KitFixtures;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/**
 * Spec 16.1.4, as JUnit and headless: a window's chrome under the default, Enterprise and complement kits, the fallback
 * combination and a strength-2 covering array of the nine chrome points, mounted as TerminalWindow mounts it and printed
 * at 2× into jasper-app/build/chrome-renders/. Plans 6b to 9 extend {@link #check}.
 */
class ChromeRenderTest {
    static final Projections.Host HOST = new Projections.Host(SystemInfo.isMacOS, MacTitleBar.isSupported());
    static final Set<Presentation.Landed> ALL = EnumSet.allOf(Presentation.Landed.class);
    static final String[] KEYS = {"title_bar", "menu_bar", "window_title", "tool_windows", "editor_tabs", "status_bar", "palette", "dialogs", "motion"};

    /**
     * One rendered look.
     *
     * @param name the image's name
     * @param themes makes the theme controller (and so the kit) the window runs under
     * @param projections the look's projections
     */
    record Case(String name, Supplier<ThemeController> themes, Projections projections) { }

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    static ThemeController slateCobalt(Kit kit) {
        return ThemeController.startup(false, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit,
            failure -> { throw new AssertionError("Slate Cobalt must install", failure); });
    }

    /** The covering array's rows, as chrome specs. */
    static List<ChromeSpec> covering() {
        try (InputStream in = ChromeRenderTest.class.getResourceAsStream("chrome-covering-array.tsv")) {
            var rows = new ArrayList<ChromeSpec>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] v = line.split("\t");
                rows.add(new ChromeSpec(ChromeSpec.TitleBar.valueOf(v[0].toUpperCase()), ChromeSpec.MenuBar.valueOf(v[1].toUpperCase()), v[2],
                    ChromeSpec.ToolWindows.valueOf(v[3].toUpperCase()), ChromeSpec.EditorTabs.valueOf(v[4].toUpperCase()),
                    ChromeSpec.StatusBar.valueOf(v[5].toUpperCase()), ChromeSpec.PaletteKind.valueOf(v[6].toUpperCase()),
                    ChromeSpec.DialogKind.valueOf(v[7].toUpperCase()), ChromeSpec.MotionKind.valueOf(v[8].toUpperCase())));
            }
            return rows;
        } catch (IOException failure) { throw new java.io.UncheckedIOException(failure); }
    }

    /** Every case of spec 16.1.4 this plan renders. */
    static List<Case> cases() {
        var cases = new ArrayList<Case>();
        cases.add(new Case("default", () -> slateCobalt(Kit.DEFAULT), Projections.of(ChromeSpec.DEFAULT, false, false, "", HOST, ALL)));
        cases.add(new Case("enterprise", () -> EnterpriseLook.controller(false),
            Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", HOST, ALL)));
        cases.add(new Case("complement", () -> slateCobalt(KitFixtures.complement()), Projections.of(ChromeSpec.DEFAULT, true, true, "dev.example", HOST, ALL)));
        cases.add(new Case("fallback", () -> slateCobalt(Kit.DEFAULT), Projections.fallback(new DesktopFlags(false, true), HOST)));
        var rows = covering();
        for (int i = 0; i < rows.size(); i++)
            cases.add(new Case("covering-" + i, () -> EnterpriseLook.controller(false), Projections.of(rows.get(i), true, true, "dev.jasper.enterprise", HOST, ALL)));
        return cases;
    }

    /** The structure every look keeps; plans 6b to 9 add their surfaces' checks here. */
    static void check(Case look, WindowContent owner, JRootPane root, MacTitleBar bar) {
        var window = WindowLook.of(look.projections());
        assertThat(bar != null).as(look.name() + ": the unified bar where the look asks for it").isEqualTo(window.unifiedTitle());
        if (bar == null) {
            assertThat(root.getJMenuBar()).as(look.name() + ": the menus are a row of the window").isSameAs(owner.menuBar());
            int contentTop = SwingUtilities.convertPoint(owner, 0, 0, root).y;
            assertThat(contentTop).as(look.name() + ": the content meets the menu row flush").isEqualTo(root.getJMenuBar().getHeight());
            var header = owner.chrome().header();
            if (header.getParent() == owner) assertThat(header.getY()).as(look.name() + ": no gutter above the header").isZero();
        }
        var titles = WindowChromeMenusTest.titles(owner);
        if (look.projections().workspace().menus() == WorkspaceLook.Menus.CLASSIC)
            assertThat(titles).as(look.name()).contains("Window").doesNotContain("Pane", "Tab");
        else assertThat(titles).as(look.name()).contains("Pane", "Tab").doesNotContain("Window");
        assertThat(titles.getLast()).as(look.name() + ": Help is last").isEqualTo("Help");
    }

    @Test void everyLookBuildsItsChromeFlush() throws Exception {
        for (Case look : cases()) {
            edt(() -> {
                ThemeController themes = look.themes().get();
                MacTitleBar bar = null;
                try {
                    var window = WindowLook.of(look.projections());
                    var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, window.chrome());
                    owner.connectContributions(WindowChromeMenusTest.model(false));
                    var root = new JRootPane();
                    bar = WindowContent.mount(root, owner, window, title -> { });
                    MockProfile.write(MockProfile.print(root, 1200, 700), "chrome-" + look.name());
                    check(look, owner, root, bar);
                } finally {
                    if (bar != null) bar.close();
                    DesktopTestSupport.closeOwnersNow();
                    themes.close();
                    EnterpriseLook.restoreDefault();
                }
            });
        }
    }

    @Test void theCoveringArrayHasStrengthTwoOverTheChromePoints() {
        var rows = covering();
        assertThat(rows).hasSize(8);
        assertThat(rows.getFirst()).as("row 0 is the default look").isEqualTo(ChromeSpec.DEFAULT);
        var seen = new HashSet<String>();
        for (var row : rows) {
            boolean[] values = flags(row);
            for (int i = 0; i < KEYS.length; i++) for (int j = i + 1; j < KEYS.length; j++) seen.add(i + ":" + j + ":" + values[i] + ":" + values[j]);
        }
        for (int i = 0; i < KEYS.length; i++) for (int j = i + 1; j < KEYS.length; j++) for (boolean a : new boolean[] {false, true}) for (boolean b : new boolean[] {false, true}) {
            if (i == 0 && j == 1 && !a && b) continue; // spec 5.3: window menus need a native title
            assertThat(seen).as(KEYS[i] + "=" + a + " with " + KEYS[j] + "=" + b).contains(i + ":" + j + ":" + a + ":" + b);
        }
    }

    /** A row's points as "is the Enterprise value". */
    static boolean[] flags(ChromeSpec row) {
        return new boolean[] {row.titleBar() == ChromeSpec.TitleBar.NATIVE, row.menuBar() == ChromeSpec.MenuBar.WINDOW,
            !row.windowTitle().equals("{context}"), row.toolWindows() == ChromeSpec.ToolWindows.STRIPES, row.editorTabs() == ChromeSpec.EditorTabs.CLASSIC,
            row.statusBar() == ChromeSpec.StatusBar.CELLS, row.palette() == ChromeSpec.PaletteKind.RUN_WINDOW,
            row.dialogs() == ChromeSpec.DialogKind.CLASSIC, row.motion() == ChromeSpec.MotionKind.NONE};
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.MenuEntry;
import dev.jasper.app.contributions.MenuTarget;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayDeque;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import javax.swing.JRootPane;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Spec 16.1.4 against the mocks: the Enterprise menu row, rendered at 2× under the Enterprise fixture kit and theme
 * ({@link EnterpriseLook}), compared with the Workstation (Light) and Dark Mode mocks by profile ("Mock measurements";
 * Rulings 12).
 */
class EnterpriseMenuRowMockTest {
    static final String LIGHT = "01 Workstation/Workstation - Connected (plugins in use).png";
    static final String DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    /** Where the window's content starts in the 2× mocks: past the outer border, the native title bar and its rule. */
    static final int X = 2, Y = 60;
    /** The mocks' content area in logical pixels. */
    static final int WIDTH = 1440, HEIGHT = 871;

    @org.junit.jupiter.api.AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** The mocks' menus: a plugin's Session, Transfer and Tools entries, and the app's Help. */
    static Contributions workstation() {
        var model = new Contributions();
        for (String id : List.of("dev.x.connect", "dev.x.upload", "dev.x.tool", "dev.x.about"))
            model.addAction(id, id.substring(6), null, List.of(), Optional.empty(), invocation -> { });
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.SESSION)).set(List.of(new MenuEntry.Item("dev.x.connect")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TRANSFER)).set(List.of(new MenuEntry.Item("dev.x.upload")));
        model.addMenuSection(MenuTarget.standard(MenuTarget.Slot.TOOLS)).set(List.of(new MenuEntry.Item("dev.x.tool")));
        model.addMenuSection(MenuTarget.topLevel(MenuTarget.HELP, "Help")).set(List.of(new MenuEntry.Item("dev.x.about")));
        return model;
    }

    /** The Enterprise window as the mocks show its content, printed at 2× and written for inspection. EDT. */
    static BufferedImage render(boolean dark, java.util.function.Consumer<WindowContent> fixture) {
        var themes = EnterpriseLook.controller(dark);
        try {
            var projections = Projections.of(EnterpriseLook.chrome(), true, true, "dev.jasper.enterprise", new Projections.Host(true, false),
                EnumSet.allOf(Presentation.Landed.class));
            var look = WindowLook.of(projections);
            var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, look.chrome());
            owner.connectContributions(workstation());
            fixture.accept(owner);
            var root = new JRootPane();
            WindowContent.mount(root, owner, look, title -> { });
            var image = MockProfile.print(root, WIDTH, HEIGHT);
            MockProfile.write(image, "enterprise-" + (dark ? "dark" : "light") + "-window");
            return image;
        } finally { DesktopTestSupport.closeOwnersNow(); themes.close(); EnterpriseLook.restoreDefault(); }
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void theMenuRowMatchesTheMock(boolean dark) throws Exception {
        var mock = MockProfile.mock(dark ? DARK : LIGHT);
        BufferedImage[] render = new BufferedImage[1];
        edt(() -> render[0] = render(dark, owner -> { }));
        String what = dark ? "dark" : "light";
        var mockRuns = MockProfile.solid(MockProfile.column(mock, 2000, Y, Y + 52), 2);
        var renderRuns = MockProfile.solid(MockProfile.column(render[0], 2000 - X, 0, 52), 2);
        // The 24 px row (48 at 2x) on surface.app, then its 1 px rule.
        assertThat(renderRuns.get(0).length()).as(what + " row").isCloseTo(mockRuns.get(0).length(), within(2));
        assertThat(MockProfile.deltaE(renderRuns.get(0).color(), mockRuns.get(0).color())).as(what + " row colour").isLessThanOrEqualTo(2.5);
        assertThat(renderRuns.get(1).length()).as(what + " rule").isCloseTo(mockRuns.get(1).length(), within(1));
        if (!dark) assertThat(MockProfile.deltaE(renderRuns.get(1).color(), mockRuns.get(1).color())).as("light rule colour").isLessThanOrEqualTo(2.5);
        // Plan 6a Rulings 9: Enterprise Dark's border.divider is darker than the mock's rule until plan 6b's override.
        var mockInks = MockProfile.inks(mock, new Rectangle(X + 2, Y + 2, 900, 44), 12, 25);
        var renderInks = MockProfile.inks(render[0], new Rectangle(2, 2, 900, 44), 12, 25);
        assertThat(mockInks).as("the mock's eight menu titles").hasSize(8);
        assertThat(renderInks).as(what + ": File, Edit, View, Session, Transfer, Tools, Window, Help").hasSize(8);
        for (int i = 0; i < 8; i++) {
            var expected = mockInks.get(i); var actual = renderInks.get(i);
            assertThat(actual.start() + X).as(what + " title " + i + " starts").isCloseTo(expected.start(), within(4));
            assertThat(actual.width()).as(what + " title " + i + " width").isCloseTo(expected.width(), within(4));
            assertThat(actual.top() + Y).as(what + " title " + i + " top").isCloseTo(expected.top(), within(3));
        }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail, then pass**

The harness tests run against Tasks 4 to 7, which already exist, so they do not fail for a missing symbol. Run them:

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ChromeRenderTest' --tests 'dev.jasper.app.workspace.EnterpriseMenuRowMockTest'`
Expected: PASS: `ChromeRenderTest` 2, `EnterpriseMenuRowMockTest` 2, and twelve `chrome-*.png` plus
`enterprise-light-window.png` and `enterprise-dark-window.png` under `jasper-app/build/chrome-renders/`.

To see the harness fail first (TDD for the comparison itself), set `MENU_BAR_ITEM_PADDING`'s value in `ChromeRolesTest`,
`DesignMetrics` and both tables to 7 temporarily: `theMenuRowMatchesTheMock` must fail with "title 1 starts" off by
12 px (three paddings of 2 px at 2×); restore 9. In the dry run (Execution notes) every title started within 3 px and
measured within 4 px of the mock in both variants, and the tops matched exactly. If a comparison fails with the real values, open the two PNGs beside the mock crop
(`python3 -I tools/visual/region.py "<mock>" jasper-app/build/chrome-renders/enterprise-light-window.png
build/menu-row --crop 2,60,2880,1742 --region 0,0,1000,60`) and fix the classic bar's metrics in `ClassicMenuBar`
or the role values (Task 3) until it passes; record any value you change in the banner and in "Mock measurements".

- [ ] **Step 3: Add the opt-in pairs and the test inputs**

```bash
python3 -I - <<'PY'
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json"); pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith("chrome-menu-row") for p in pairs): raise SystemExit("pairs.json already has the menu-row pairs")
R, G = "docs/design/enterprise/reference/", "jasper-app/build/chrome-renders/"
pairs += [
    {"name": "chrome-menu-row-light", "mock": R + "01 Workstation/Workstation - Connected (plugins in use).png", "crop": [2, 60, 2880, 50],
     "render": G + "enterprise-light-window.png", "render_crop": [0, 0, 2880, 50], "min_agreement": 0.5, "colours": ["#ECEDEF", "#D3D6DA", "#1E1F22"]},
    {"name": "chrome-menu-row-dark", "mock": R + "08 Dark Mode/Workstation Dark - Connected (plugins in use).png", "crop": [2, 60, 2880, 50],
     "render": G + "enterprise-dark-window.png", "render_crop": [0, 0, 2880, 50], "min_agreement": 0.5, "colours": ["#3C3F44", "#DFE1E5"]}]
path.write_text(json.dumps(pairs, indent=2) + "\n", encoding="utf-8")
readme = pathlib.Path("tools/visual/README.md"); text = readme.read_text(encoding="utf-8")
text += ("\nThe `chrome-*` pairs compare the Enterprise chrome renders that `ChromeRenderTest` and the `Enterprise*MockTest`\n"
         "classes write under `jasper-app/build/chrome-renders/` (run `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*Mock*'`).\n")
readme.write_text(text, encoding="utf-8")
PY
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/build.gradle.kts"); text = path.read_text(encoding="utf-8")
old = '''    inputs.file(rootProject.file("docs/design/theme-engine/migration.csv")).withPathSensitivity(PathSensitivity.RELATIVE)'''
new = old + '''
    inputs.dir(rootProject.file("docs/design/enterprise/reference")).withPathSensitivity(PathSensitivity.RELATIVE)'''
if text.count(old) != 1: raise SystemExit("build.gradle.kts: the migration.csv input moved")
if "docs/design/enterprise/reference" in text: raise SystemExit("build.gradle.kts already declares the reference mocks (plan 5b?); skip this edit")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

If the second script stops because an earlier plan already declared the reference folder as a test input, nothing
else is needed. Add the ledger rows:

```bash
python3 -I - <<'PY'
import pathlib
T = "jasper-app/src/test/java/dev/jasper/app/"
rows = [(T + "testsupport/MockProfile.java", "app.testsupport", "MockProfile", T + "workspace/EnterpriseMenuRowMockTest.java",
         "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 8; profile comparison of 2x renders with the mocks"),
        (T + "workspace/ChromeRenderTest.java", "app.workspace", "ChromeRenderTest", T + "workspace/ChromeRenderTest.java",
         "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 8; spec 16.1.4 chrome renders and the covering array"),
        (T + "workspace/EnterpriseMenuRowMockTest.java", "app.workspace", "EnterpriseMenuRowMockTest", T + "workspace/EnterpriseMenuRowMockTest.java",
         "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 8; the menu row against the Workstation and Dark Mode mocks")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests and the opt-in check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Then: `python3 -I tools/visual/compare_all.py build/chrome-compare chrome-menu-row`
Expected: BUILD SUCCESSFUL; the comparison prints `chrome-menu-row-light: best_offset_render_px dx=0 dy=0
edge_agreement=…` and the same for `chrome-menu-row-dark` (dry run: 0.988 and 0.732, the Dark rule being Rulings 9's
residual). Record both agreements for the STATUS entry. `tools/visual/check_pairs.py` and `EnterpriseGalleryTest`
arrive with plan 4c (fact 13): the two pairs already carry the `min_agreement` and `colours` keys it reads, which
`compare_all.py` ignores, and they are checked with it when 4c is merged (Rulings 17); a FAIL then with a missing
colour is a defect to fix as in Step 2, and a low agreement from the crop's content is a floor to lower to the measured
value rounded down to 0.05, recorded in STATUS.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java \
  jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseMenuRowMockTest.java jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv \
  tools/visual/pairs.json tools/visual/README.md jasper-app/build.gradle.kts docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the chrome renders

Spec 16.1.4's chrome renders as JUnit: a window under the default,
Enterprise and complement kits, the fallback combination and a
strength-2 covering array of the nine chrome points, mounted as
TerminalWindow mounts it and printed at 2x under build/chrome-renders,
asserting a flush menu row, no gutter and the look's composition.
MockProfile compares the Enterprise menu row with the Workstation and
Dark Mode mocks by colour runs and title positions; pairs.json gains the
opt-in crops.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The variant-read scan, documentation, STATUS and the whole build

`ProjectionReadTest` holds spec 16.1.7's projection readers for this plan's points (`WorkspaceLook`'s variants only in
`WorkspaceChrome`, `Motion` only in `LookMotion`, each besides the presentation package), with a constant-pool scan of
its own because plan 4b's `VariantReadTest` is not on the branch (Rulings 16); the documents spec 17 names
for this plan get their smallest edits; the View modes specification gets its spec 19.2 banner line; `docs/STATUS.md`
gets this plan's entry; the default look is proven unchanged and the whole build runs.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java`
- Modify: `docs/app-architecture.md:181,220` (the presentation row and the title-bar paragraph), `docs/superpowers/specs/2026-10-03-jasper-view-modes-design.md:3`, `docs/STATUS.md:1`, this plan's first lines, `docs/design/theme-engine/migration.csv` (one row: the scan's `new ArrayList` matches `CANDIDATE`)

**Interfaces:**
- Consumes: everything above.
- Produces: `ProjectionReadTest.PROJECTION_READERS` (a map from a variant pattern to the classes that may read it), which
  plans 6b to 9 extend.

- [ ] **Step 1: Write the scan tests**

Create `jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java`:

```java
package dev.jasper.app.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.classfile.ClassFile;
import java.lang.classfile.constantpool.Utf8Entry;
import java.lang.constant.ClassDesc;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

/**
 * Enterprise presentation spec 9.1 and 16.1.7 for the window chrome: each look projection's variants are read only by
 * the factory or supplier that owns them, besides the presentation package that computes them. Plans 6b to 9 add their
 * points to {@link #PROJECTION_READERS}.
 */
class ProjectionReadTest {
    /** A variant pattern over internal names, and the classes that may read it. */
    static final Map<Pattern, Predicate<String>> PROJECTION_READERS = Map.of(
        Pattern.compile("dev/jasper/app/presentation/WorkspaceLook\\$(?:Menus|Header|Tabs|Status)(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/workspace/WorkspaceChrome")
                || type.startsWith("dev/jasper/app/workspace/WorkspaceChrome$"),
        Pattern.compile("dev/jasper/app/presentation/Motion(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/application/LookMotion"));

    /**
     * A class's internal name and every string in its constant pool.
     *
     * @param name the internal name
     * @param strings the constant pool's strings
     */
    record Scanned(String name, Set<String> strings) {
        static Scanned of(byte[] bytes) {
            var model = ClassFile.of().parse(bytes);
            var strings = new HashSet<String>();
            for (var entry : model.constantPool()) if (entry instanceof Utf8Entry utf8) strings.add(utf8.stringValue());
            return new Scanned(model.thisClass().asInternalName(), strings);
        }
    }

    /** The classes in {@code files} that read a projection's variants without being allowed to, as "class reads pattern". */
    static List<String> projectionReaders(List<byte[]> files) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = Scanned.of(bytes);
            PROJECTION_READERS.forEach((pattern, allowed) -> {
                if (!allowed.test(scanned.name()) && scanned.strings().stream().anyMatch(s -> pattern.matcher(s).find()))
                    found.add(scanned.name() + " reads " + pattern.pattern());
            });
        }
        return found;
    }

    @Test void onlyTheFactoriesReadTheLooksVariants() throws Exception {
        var files = StaticDefaultTest.classFiles(Path.of(System.getProperty("jasper.appJar")), 400);
        var live = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = Scanned.of(bytes);
            if (scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/WorkspaceLook$Menus"))) live.add(scanned.name());
        }
        assertThat(live).as("the scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/workspace/WorkspaceChrome"));
        assertThat(projectionReaders(files)).isEmpty();
    }

    @Test void aLookReadElsewhereIsReported() {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.WindowChromeProbe"), builder -> builder
            .withField("menus", ClassDesc.of("dev.jasper.app.presentation.WorkspaceLook$Menus"), ClassFile.ACC_PRIVATE)
            .withField("motion", ClassDesc.of("dev.jasper.app.presentation.Motion"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(2);
    }
}
```

Add its ledger row:

```bash
python3 -I - <<'PY'
import pathlib
test = "jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java"
rows = [(test, "app.architecture", "ProjectionReadTest", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 9; the window chrome's projection variants are read only by their factories")]
ledger = pathlib.Path("docs/design/theme-engine/migration.csv"); text = ledger.read_text(encoding="utf-8")
text += "" if text.endswith("\n") else "\n"
ledger.write_text(text + "".join(f"{p},{m},{c},nonvisual-helper,none,none,{e},{d}\n" for p, m, c, e, d in rows), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the scan**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.ProjectionReadTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'`
Expected: PASS (`ProjectionReadTest` 2 cases). The scan reads the production jar, so it passes as soon as it is written;
to see it fail, add `private dev.jasper.app.presentation.Motion probe;` to `WindowContent` temporarily:
`onlyTheFactoriesReadTheLooksVariants` names `dev/jasper/app/workspace/WindowContent`; remove it again.

- [ ] **Step 3: Edit the documents and the spec banner**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 9 step 3: the smallest accurate documentation edits and spec 19.2's View modes line."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [
    ('''the system title bar stays and the header is the top row of `WindowContent`.''',
     '''the system title bar stays and the header is the top row of `WindowContent`. A look with a native title (the
Enterprise presentation) gives every platform the system title bar: the bootstrap turns macOS's screen menu bar off, so
each window's menus are a row in the window, a native-titled macOS frame's appearance follows the theme, and on Windows
and Linux the system title bar follows the operating system's light or dark setting (JBR has no hint for a title bar it
does not draw). `WorkspaceChrome.of` (workspace) and `ShellChrome.of` (windows) read the look's projections and choose
the strategies, among them `MenuBarLayout`: `StandardMenuBar`, or `ClassicMenuBar` (File, Edit, View, Session,
Transfer, plugin menus, Tools, Window, Help). `WindowTitle` fills the look's title template.''', 1)])
banner = ("Amended on 2026-10-15 by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md): "
          "under a native-titled look Compact hides the toolbar row and keeps the in-window menu bar; everything else in this document stands.")
spec = pathlib.Path("docs/superpowers/specs/2026-10-03-jasper-view-modes-design.md"); text = spec.read_text(encoding="utf-8")
anchor = "Status: Adversarial follow-up (2026-10-04)"
if text.count(anchor) != 1 or banner in text: raise SystemExit("view modes spec: banner anchor moved or already amended")
end = text.index("\n\n", text.index(anchor))
spec.write_text(text[:end] + "\n\n" + banner + text[end:], encoding="utf-8")
PY
```

Then add one sentence to the `presentation` row of `docs/app-architecture.md`'s package table, after its first
sentence:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("docs/app-architecture.md"); text = path.read_text(encoding="utf-8")
old = "the attempt record (`AttemptStore`, `PresentationAttempt`), the projections of spec 9.1 and `Presentation`."
if text.count(old) != 1: raise SystemExit("app-architecture: the presentation row's first sentence moved")
path.write_text(text.replace(old, old + " The window chrome's projections are read by `WorkspaceChrome.of`, `ShellChrome.of` and the application's `LookMotion`."), encoding="utf-8")
PY
```

Run `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'` and expect PASS (links resolve; 12 cases in
the dry run).

- [ ] **Step 4: Prove the default look unchanged**

This plan's only jasper-ui change is the appended roles (Task 3). Render the headless gallery here and at the base and
compare every file:

```bash
B=$(mktemp -d) && git worktree add --detach "$B/base" "$(git merge-base HEAD main)"
for theme in dark light; do
  (cd "$B/base" && ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/base-$theme")
  ./gradlew -q :jasper-ui:gallery -Pjasper.galleryTheme=$theme && cp -R jasper-ui/build/gallery/$theme-1x "$B/new-$theme"
  diff -r "$B/base-$theme" "$B/new-$theme" && echo "$theme: identical"
done
git worktree remove --force "$B/base"
```

Expected: `dark: identical` and `light: identical` (the dry run rendered 322 files per side, identical). Then run the
reference pairs and the calibration suites as they are: `./gradlew :jasper-app:test --tests '*Calibration*' --tests '*Reference*'` must pass unchanged.

- [ ] **Step 5: Run the whole build**

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

Expected: 0 failures and 0 errors; note the four numbers.

- [ ] **Step 6: Write the STATUS entry and the banner**

Insert at the top of `docs/STATUS.md`, right after the `# Jasper — Status and Handoff` heading and its blank line, one
paragraph (fill `<range>` with the first and last commit of this plan, `<counts>` with Step 5's line, `<agreements>` with
Task 8's `compare_all.py` lines, and `<deviations>` with every deviation from this plan's text, or "none beyond the plan's
Rulings", plus the Execution notes' rulings that changed the work):

```markdown
**Enterprise presentation, plan 6a: the window shell — 2026-10-16** (`claude/enterprise-window-chrome`, worktree
`.claude/worktrees/enterprise-6`, `<range>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-6a-window-chrome-shell.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). The window shell of spec 10.1, 10.2, 10.4,
10.9 and 10.10 lands: `Presentation.LANDED` holds `WINDOW_CHROME`, and `WINDOW_CONTENT` (header, tabs, status, run item,
dialogs) waits for plan 6b. The bootstrap applies `DesktopFlags` (macOS `apple.laf.useScreenMenuBar=false` under in-window
menus; `TitleBarStyle.NATIVE` on every platform under a native title). `WorkspaceChrome.of` reads the workspace
projection and picks `StandardMenuBar` (today's) or `ClassicMenuBar` (File, Edit, View, Session, Transfer, plugin menus,
Tools, Window, Help; Window holds Pane, Tab and the tool-window toggles; the mocks' 24 px row over a `border.divider`
rule). `WindowLook` carries the chrome and the title facts to every terminal window and the rehearsal's, mounted through
`WindowContent.mount`; `ShellChrome.of` gives auxiliary windows, dialogs and the file chooser the same title bar, and a
native-titled macOS frame follows the theme's appearance. About Jasper joins Help when the screen menu bar is off.
`LookMotion` folds `Motion` into `ui.animations.enabled` once; kit contexts, plugins, the tab strip and the palette read
it. `WindowTitle` fills the title template. Thirty classic chrome roles (Task 3) serve plans 6a and 6b. The chrome renders
of spec 16.1.4 run as JUnit (`ChromeRenderTest`: default, Enterprise, complement, fallback and an eight-row covering array);
`EnterpriseMenuRowMockTest` compares the menu row with the Workstation and Dark Mode mocks (opt-in pairs: <agreements>).
Rulings (plan, "Rulings"): (1) row 6 is plans 6a and 6b; (2) the window-chrome group splits, `WINDOW_CONTENT` for 6b;
(3) Help is `MenuTarget.HELP`, last in the classic bar; (4) the classic Window menu takes the tool-window toggles from
View; (5) the menu-row roles are the same in both catalogs and the item margins come from the face's line height;
(6) an open classic title is the accent, a hovered closed one `surface.control.hover`; (7) About is in Help whenever the
screen menu bar is off; (8) Windows and Linux captions follow the operating system, and on macOS every native-titled frame and dialog follows the theme (the chooser's sheets and the diagnostics dialog included); (9) Enterprise Dark's menu-row rule is
darker than the mock until plan 6b's `border.divider` override; (10) the title tokens and separators; (11) the one motion
supplier is the theme controller's flag; (12) mock comparison by profile; Execution notes: (13) Enterprise runs in the
chrome tests as 4a's fixture kit, spec 5.1's chrome and a fixture theme of spec 7.6's named roles until plan 5b; (16) the
scan is `ProjectionReadTest`; (17) the pairs are checked with `compare_all.py` until 4c's `check_pairs.py`; (18) the
menu row is 5 + 9, re-measured on the mocks. Deviations from the plan's text: <deviations>.
Plan-6b entry conditions: the `WINDOW_CONTENT` group, `border.divider` overridable, the header, tabs, status cells and
dialogs; 6b extends `ProjectionReadTest.PROJECTION_READERS` and uses `EnterpriseLook` and `closeOwnersNow`. At the merge
with plan 5b, `EnterpriseLook` takes the shipped kit, faces and theme, `enterprise-fixture.json` goes, and
`EnterpriseMenuRowMockTest` runs again under Plex. `./gradlew check --continue`: **<counts>**. GUI checks for the user (spec 16.2): with Enterprise on, the macOS
in-window menu bar and native title (⌘W in a plugin window, About in Help), Windows and Linux NATIVE with the caption
following the system setting, Compact keeping the menu row, nothing moving with animations on. Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-16): executed on `claude/enterprise-window-chrome` (`<range>`).** Deviations from this plan's text: <deviations>.
```

- [ ] **Step 7: Commit**

```bash
git add docs jasper-app/src/test/java/dev/jasper/app/architecture/ProjectionReadTest.java
git commit -F - <<'EOF'
Window chrome: the shell's documentation, scan and status

ProjectionReadTest keeps WorkspaceLook's variants in WorkspaceChrome and
Motion in LookMotion. The app architecture describes the native-titled
shell, the menu layouts and the title template; the View modes design
gains its amendment line. STATUS records plan 6a with its rulings, the
mock comparison and the full build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

## Execution notes (2026-10-16)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `33af58203` (the two plan commits on `main` at `b9178db45`: plans 3a to 3e, 4a and 5a; not 4b, 4c or 5b).
Method, without Gradle: a dry run in a scratch copy of the tree applied every step in order (each edit script with its
stated counts, each created file, each ledger row), compiled jasper-ui and jasper-app main, test fixtures and tests with
`javac` (JBR 25, the Gradle cache's jars) after each task, and ran the tests through the JUnit 6.1.3 launcher headless.
The new and touched tests pass at the end of Tasks 3 to 9 (62 jasper-app tests and the 35 of `dev.jasper.ui.metrics` at
the end). The wide suites (`application`, `appearance`, `architecture`, `bootstrap`, `contributions`, `documentation`,
`platform`, `plugins`, `presentation`, `windows`, `workspace`) fail exactly the tests the unchanged tree fails (staged
plugins, packaged jars and build info, which need Gradle's task inputs, and `dockedPluginHeadersAreSingle…`).
`javadoc -Xdoclint:all` over jasper-app exits 0; the source-hygiene scan is clean (2,435 files); every new ledger row has
eight fields and `ThemeMigrationCoverageTest` passes; the headless gallery (`KitGallery`, dark and light at 1×) is
byte-identical to the unchanged tree's, 322 files a side; `compare_all.py` gives the menu-row pairs 0.988 (Light) and
0.732 (Dark, Rulings 9's rule). After the edits below, the blocks re-extracted from this file reproduce the verified
tree.

Changes made to this plan's text: the Workspace paragraph and "Before you start" (no rebase; checks for 3d, 3e, 4a and
5a only; what stands in for 4b, 4c and 5b); the names table (each name checked, absences marked); facts 1, 3 to 6, 9 to
12 re-anchored and facts 13 to 15 added; the menu-label row of "Mock measurements" and a re-measurement note; Rulings 5's
values; the file structure; Task 3 (no `NewRolesTest` edit, `ChromeRolesTest` after `DOCK_RULE_HIT`, values 5 and 9);
Task 4 (`EnterpriseLook` on fixtures, the fixture theme, `closeOwnersNow`, the row test's insets, font and teardown, the
workspace edge, the Files lines); Task 5 (the windows edge, the Files lines); Task 7's Files line; Task 8 (`MockProfile`
from `jasper.repoRoot`, teardown order, the TDD note, `compare_all.py`); Task 9 (`ProjectionReadTest`, its ledger row,
the presentation-row script, the Files lines, the STATUS template's rulings and entry conditions).

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 2, 5 | `Presentation.LANDED` and `committedDesktop()`: the bootstrap's flags (2), About in Help (5) | Agree: with `WINDOW_CHROME` landed the committed flags are the file's | 24 |
| 1 → 4, 5, 8 (→ 6b) | `Projections.of` under `WINDOW_CHROME`; `WINDOW_CONTENT` left for 6b | Agree; the content fields stay at spec 9.1's fallback row | 24 |
| 3 → 4, 8 | `MENU_BAR_SIDE`, `MENU_BAR_ITEM_PADDING`, `MENU_BAR_HEIGHT`: read by `ClassicMenuBar`, asserted by the row test (4) and the mock test (8) | Disagreed with the mocks at 4 and 10 (title 2 off by 6 px, Help by 30); 5 and 9 in Task 3's test, script and tables and in Task 4's assertions | 18 |
| 3 ↔ 4c, 8a (not merged; parallel) | `DesignMetrics`' last line, `compact-roles.tsv`, `classic-roles.tsv`, `NewRolesTest` | This plan appends after `DOCK_RULE_HIT(6)` and leaves `NewRolesTest` alone; the others append at the same place | 15, 23 |
| 4 → 5 → 6 → 7 | `WindowContent.java`: field, constructor and `look()` (4); `mount`, `installTitleBar`, the diagnostics dialog, `followsThemeAppearance` (5); `lookAnimates` after `look()` (6); the `nativeTitle.accept` line inside `installTitleBar` (7) | Each later anchor is the earlier task's output or text it leaves; counts hold in order | 24 |
| 4 → 5 | `DesktopTestSupport.java`: `content(…, WorkspaceChrome)` and `closeOwnersNow` (4), `track` (5), both before `closeOwners()` | The anchor stays unique after Task 4 | 19, 24 |
| 4 → 5 → 6 | `JasperApplication.java`: the Help key (4) and Help's entries (5) on one line, the windows, rehearsal, shells and `windowLook` (5), the configuration controller (6) | Agree; disjoint spans | 24 |
| 4 → 5, 8 | `WindowChromeMenusTest.classic()`, `titles()`, `model(boolean)` | Agree | none |
| 4 → 8 (→ 6b) | `EnterpriseLook` (`CHROME`, `kit`, `chrome`, `theme`, `controller`, `restoreDefault`), `closeOwnersNow` | Agree after the rulings | 13, 19, 20 |
| 4, 6 → 9 | `WorkspaceChrome` and `LookMotion` as the only readers; `ProjectionReadTest` | Agree: the scan finds no other reader | 16 |
| 4, 5 | `workspace` and `windows` package-info files | The edges were conditional; 5a added neither; now explicit | 21 |
| 5 → 8 | `WindowContent.mount`, `WindowLook.of`, `ShellChrome` | Agree | none |
| 2, 4 to 9 | `docs/design/theme-engine/migration.csv`, append-only | Eight fields; at each task's end every candidate file it creates has its row (`LookMotion`, `LookMotionTest`, `WindowTitleTest` need none) | none |
| 8 ↔ 4c (not merged) | `tools/visual/pairs.json`, `README.md`; `check_pairs.py` and `EnterpriseGalleryTest` absent | Append; checked with `compare_all.py` | 17, 23 |
| 8 → 6b | `MockProfile`, `ChromeRenderTest.cases`/`Case`/`check`, `EnterpriseMenuRowMockTest.render`/`workstation` | Agree; `MockProfile` gains `ROOT` | 14 |
| 9 ↔ 4b (not merged; parallel) | `VariantReadTest`, `StrategyIsolationTest.Scanned` absent | A separate `ProjectionReadTest` | 16 |
| 9 → 6b | `PROJECTION_READERS` | 6b's text names `VariantReadTest.PROJECTION_READERS`; it is `ProjectionReadTest.PROJECTION_READERS` | 16 |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | Agree; `ProjectionsTest` 6 cases; `KitParserTest.FULL` lists `title_bar = "native"` and `menu_bar = "window"` as the note says; no other test asserted `DesktopFlags.STANDARD` for a native-titled kit | none |
| 2 | Agree; Files line numbers moved; `DesktopFlagsTest` 2, `TitleBarStyleTest` 4 | 22 |
| 3 | The intro, Files, Step 2 and the commit named a `NewRolesTest` edit whose anchor 4a already replaced, and 4c's `GROUP_ROW_INDENT`; the menu-row values failed the mock | 15, 18 |
| 4 | `EnterpriseLook` read 5b's `EnterpriseResources`; the row test asserted IBM Plex and logged "Appearance binding is closed"; the package edge was conditional | 13, 19, 20, 21 |
| 5 | Agree; the windows edge was conditional; `WindowShellTest` 4, `ShellChromeTest` 2, `AboutWindowsTest` + 1 | 21 |
| 6 | Agree; `LookMotionTest` 2, `MotionReachTest` 1; the file writes `[ui.animations]` `enabled`, as `config.example.toml` spells it | none |
| 7 | Agree; `WindowTitleTest` 4; Files line moved | 22 |
| 8 | `MockProfile` read `EnterpriseResources.ROOT`; Step 4 named 4c's `check_pairs.py` and `EnterpriseGalleryTest`; the TDD note's numbers followed padding 10; teardown closed the controller before the windows; `ChromeRenderTest` 2 and `EnterpriseMenuRowMockTest` 2 pass after the rulings, twelve `chrome-*.png` and two `enterprise-*-window.png` written | 14, 17, 18, 19 |
| 9 | `VariantReadTest` (4b) is absent; the presentation-row sentence was a manual step; Files line numbers moved; the STATUS template named `check_pairs.py` | 16, 22 |

### Rulings

The plan's own Rulings are 1 to 12; these continue the numbering.

13. **Enterprise in the chrome tests is a fixture until plan 5b.** `EnterpriseLook` gives 4a's `KitFixtures.enterprise()`
    (the classic base, the kit file's tokens, a 12 px body, the platform faces), spec 5.1's `[chrome]` table as
    `EnterpriseLook.CHROME`, and a checked-in theme (`testsupport/enterprise-fixture.json`, written by Task 4's script)
    holding exactly spec 7.6's named roles and override keys, Light and Dark, over Slate Cobalt's other roles. Why: 5b's
    kit, Plex files and theme are not on the branch and the orchestrator says the tests use 5a's and 4a's fixtures, while
    mock matching needs the mocks' colours, which are spec 7.6's named values (the menu row reads only named roles and
    `border.divider`, derived from two of them). Cost if wrong: at the merge with 5b, three method bodies change and one
    resource goes; a role 5b derives (an unnamed one) is not compared here.
14. **`MockProfile` finds the repository from `jasper.repoRoot`**, which jasper-app's test task already sets (as
    `KitFixtures.themes()` does), and exposes it as `ROOT`. Why: `EnterpriseResources.ROOT` is 5b's. Cost if wrong: none.
15. **The chrome roles follow 4a's last role.** The Task 3 script anchors on `DOCK_RULE(1), DOCK_RULE_HIT(6);` and on
    tables ending `DOCK_RULE_HIT\t6`; `ChromeRolesTest` asserts that `MENU_BAR_SIDE` comes after `DOCK_RULE_HIT` and that
    the thirty roles are one run in order; `NewRolesTest` already asserts a run (`indexOfSubList`) and is not edited. Why:
    4c's `GROUP_ROW_INDENT` is not merged, and 8a appends its roles at the same place in parallel. Cost if wrong: at a
    merge with 4c or 8a, one enum line and the two tables' tails take every side's roles in one order; neither test
    changes. If 6a reaches `main` before 4c executes, 4c's own anchor (`DOCK_RULE_HIT(6);`) must be re-found.
16. **The variant-read scan is `ProjectionReadTest`**, a new architecture test with its own constant-pool `Scanned`
    record and `StaticDefaultTest.classFiles`, holding `PROJECTION_READERS`; it is not an edit to 4b's `VariantReadTest`.
    Why: 4b's file does not exist here and 4b creates and edits it in parallel, so a separate file merges without
    conflict. Cost if wrong: two small scanners until someone folds them; plan 6b's refresh renames its
    `VariantReadTest.PROJECTION_READERS` references.
17. **The opt-in pairs are checked with `compare_all.py`.** Task 8 appends both pairs with `min_agreement` and
    `colours` (which `compare_all.py` ignores) and records `compare_all.py`'s agreements; `EnterpriseGalleryTest` is not
    run. Why: 4c creates `check_pairs.py` and `EnterpriseGalleryTest`. Cost if wrong: the PASS/FAIL and colour check of
    these two pairs runs at the merge with 4c, where a floor may need lowering.
18. **The classic menu row is side 5 and item padding 9** (was 4 and 10), in Task 3's roles, test and tables, Task 4's
    insets assertions (`Insets(0, 5, 1, 5)`, a title's left inset 9), "Mock measurements" and Rulings 5. Why: measured
    on the mocks, the ink gaps between titles are 36 to 39 at 2× (19 logical), which a 2 × 10 padding cannot give; at
    4 and 10 the dry run failed "title 2 starts" by 6 px, drifting to 30 px at Help; at 5 and 9 the first title's ink is
    still at 15 (the title box still starts 14 in) and every title starts within 3 px and measures within 4 px of the mock
    in Light and Dark. Cost if wrong: under Plex (5b) a title may drift a pixel or two; a retune is two role values and
    their rows.
19. **Tests close their windows before their theme controller.** `DesktopTestSupport.closeOwnersNow()` (EDT) runs first in
    the `finally` of `theClassicMenuRowIsTheMocksRow`, `ChromeRenderTest.everyLookBuildsItsChromeFlush` and
    `EnterpriseMenuRowMockTest.render`. Why: fact 15; as written, the row test passed while the EDT logged fourteen
    "Appearance binding is closed" exceptions. Cost if wrong: none.
20. **The row test asserts the kit's BODY face at 12 px**, not the family "IBM Plex Sans". Why: no Plex until 5b, and
    the fixture kit draws the platform face. Cost if wrong: 5b adds the family assertion back.
21. **The `workspace → presentation` (Task 4) and `windows → presentation` (Task 5) package-info edges are explicit
    edits.** Why: fact 14; the plan made them conditional on 5a, which did not add them. Cost if wrong: none; the graph
    stays acyclic.
22. **Line numbers re-anchored.** Facts and Files lines give `b9178db45`'s numbers. Every other quoted anchor of the
    plan's scripts matched its stated count unchanged. Why: the Files numbers are guides; the scripts anchor on text.
    Cost if wrong: none.
23. **Shared files with parallel or unmerged plans are reconciled at merge, not here.** 8a: `DesignMetrics`,
    `NewRolesTest` (this plan does not edit it), `compact-roles.tsv`; 4b: `VariantReadTest` (not edited here); 4c:
    `DesignMetrics`, the tables, `pairs.json`, `tools/visual/README.md`; every plan: `migration.csv` and `docs/STATUS.md`.
    Keep every side's rows, roles, pairs and paragraphs. Cost if wrong: merge work only.
24. **Order is binding.** The shared files above are edited in task order and each later anchor is the earlier task's
    output, as the dry run shows; execute the tasks in order, one commit each. Cost if wrong: none when run in order.
25. **Timing-sensitive tests.** `MotionReachTest` (a configuration service on a worker), the chrome renders and the
    window tests wait on the EDT; other plans' Gradle builds run at the same time, so one may flake once. Re-run such a
    test alone before treating it as a failure, and never run `./gradlew --stop`. Cost if wrong: one rerun.
26. **Plan 5b's merge with this branch** switches `EnterpriseLook` to `EnterpriseResources` (the shipped kit with Plex
    registered, its chrome, the Enterprise theme), deletes `enterprise-fixture.json` and its script's output, restores
    Rulings 20's family assertion and runs `WindowChromeMenusTest`, `ChromeRenderTest` and `EnterpriseMenuRowMockTest`
    again; a title that drifts past 4 px is retuned by Rulings 18's measurement. Why: the fixture stands in for 5b only.
    Cost if wrong: the mock comparison stays on the platform face.
