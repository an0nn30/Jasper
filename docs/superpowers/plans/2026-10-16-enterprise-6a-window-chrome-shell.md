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
`claude/enterprise-window-chrome`, cut from `main` at `deafed424` and holding only the two plan files. Run every command
from the worktree root, never in `~/projects/moray`. Line numbers below are from `deafed424`; plans 3b to 5b change the
files first, so every edit quotes the exact text it replaces (in the form it has after those plans) and the edit
scripts stop with a message when that text is not found the stated number of times. If a script stops, re-find the
quoted text (it moves, it does not change), adjust the anchor and re-run; never edit around a missing anchor, and record
any re-quoted anchor in the status banner. Plan 6b executes on the same branch after this plan.

## Before you start

Bring the branch up to `main` and check that every earlier plan is in it:

```bash
git rebase main
grep -c 'TRANSFER' jasper-sdk/src/main/java/dev/jasper/sdk/ui/StandardMenu.java                         # 3d: at least 1
grep -c 'record View(' jasper-app/src/main/java/dev/jasper/app/contributions/ToolbarEntry.java          # 3d: 1
grep -c 'OPEN_FILE' jasper-app/src/main/java/dev/jasper/app/commands/ActionId.java                      # 3e: at least 1
grep -c 'DOCK_RULE_HIT(6)' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java              # 4a: 1
grep -c 'GROUP_ROW_INDENT(14);' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java          # 4c: 1 (the enum's last role)
grep -c 'Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java                                # 4a: 1
ls jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicPromptFace.java   # 4b
ls jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java                          # 4b
ls jasper-app/src/main/java/dev/jasper/app/presentation/Projections.java jasper-app/src/main/java/dev/jasper/app/plugins/PluginSelection.java   # 5a
ls plugins/enterprise/src/main/resources/themes/enterprise.json plugins/enterprise/src/main/resources/presentation/enterprise.kit.toml   # 5b
ls jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseResources.java                       # 5b
grep -c 'Set<Landed> LANDED = Set.of();' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java   # 5a: 1
```

Each `grep -c` must print at least 1 and each `ls` must list its files; otherwise stop: this plan needs plans 3a to 5b on
`main`. If the last check prints 0 because a later plan (7 to 9) already landed its group, keep its constants in every
edit below that touches `LANDED` or `Landed`.

**Earlier plans' names.** This plan uses the names below, read from the earlier plans as written. Before Task 1 read
the merged code; where a name differs, change it in this plan's code blocks before executing (a renamed member is one
search and replace; a different shape means adapting the call without changing behaviour) and record it in the banner.

| This plan uses | Source |
|---|---|
| `Presentation(Optional<Provider>, Kit, ChromeSpec, boolean, KitParser.Faces, DefaultSource, Projections, List<String>)`, `Presentation.Landed`, `LANDED`, `committedDesktop()`, `projections()`, `standard(Projections.Host)`, `fallback(Projections.Host)` | 5a Task 8, 5b Task 1 |
| `Projections(DesktopFlags desktop, WorkspaceLook workspace, ShellLook shell, PaletteLook palette, SettingsChrome settings, Composition composition, DialogMapping dialogs, Motion motion, TitleFormat title, DockLayoutKey dockLayout, AppPanels panels)`, `Projections.of(ChromeSpec, boolean, boolean, String, Host, Set<Landed>)`, `Projections.fallback(DesktopFlags, Host)`, `Projections.Host(boolean mac, boolean unifiedTitleSupported)` | 5a Task 8 |
| `DesktopFlags(boolean screenMenuBar, boolean nativeTitle)` with `STANDARD`; `WorkspaceLook(Menus, Header, Stripes, Tabs, Status, boolean runItem, FrameStyle)` and its nested enums; `ShellLook(boolean unifiedTitle, boolean windowMenus, boolean followThemeAppearance)`; `enum Motion { STANDARD, NONE }`; `TitleFormat(String template)`; `DialogMapping(Kind, boolean windowTitled)` | 5a Task 8 |
| `ChromeSpec(TitleBar, MenuBar, String windowTitle, ToolWindows, EditorTabs, StatusBar, PaletteKind, DialogKind, MotionKind)` with `DEFAULT`, `CLASSIC` and the nested enums | 5a Task 3 |
| `ApplicationBootstrap.Startup(ConfigService service, AppArguments options, AppDirs dirs, AppLog log, PluginSelection selection)`; `PluginSelection.presentation()`; `JasperApplication.presentation()` | 5a Task 9 |
| `ThemeController.startup(boolean dark, ThemeSelection selection, Kit kit, Consumer<InstallationFailure> fallingBack)`; `ThemeController.kit()` | 5a Tasks 10, 11 |
| `EnterpriseResources.ROOT`, `RESOURCES`, `facts()` (`PresentationFacts.parsed()` an `Optional<KitParser.Parsed>`), `theme()` (a `JasperTheme`) | 5b Task 4 |
| `KitFixtures.complement()` (the complement fixture kit) | 4a Task 6 |
| `DesignMetrics.Role.GROUP_ROW_INDENT(14)` as the enum's last role, `NewRolesTest.ADDED` and `theAddedRolesCloseTheEnumInOrder` | 4c Task 1, 4a Task 2 |
| `VariantReadTest` (the class-file scan of the app and ui jars) and `StrategyIsolationTest.Scanned` | 4b Task 0 (its "isolation and variant-read tests") |
| `ToolbarEntry.View`, `WindowChrome.windowOpened()`, `MenuTarget.Slot.SESSION`, `TRANSFER` | 3d Tasks 1 and 4 |
| `ActionId.OPEN_FILE` in File after New Window | 3e Task 2 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task here runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. The headless gallery (`:jasper-ui:gallery`) and headless JUnit renders (`java.awt.headless=true`, which jasper-app's test task sets) may be run.
- Work only in the worktree above on `claude/enterprise-window-chrome`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look stays byte-identical. Every PNG and JSON the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base (Task 9 proves it); every reference pair in `tools/visual/pairs.json` and every calibration suite stays green unchanged. No existing render may change: under the default projection `WorkspaceChrome.STANDARD` builds today's menus in today's order, `TitleFormat("{context}")` gives today's title, `LookMotion(STANDARD)` passes the setting through, and `ShellChrome.standard()` reads `MacTitleBar.isSupported()` as today. The new metric roles (Task 3) are read only by classic chrome.
- Spec 9.1 table, exactly: `DesktopFlags.screenMenuBar` false when `menu_bar = "window"`, `nativeTitle` true when `title_bar = "native"`; `WorkspaceLook.menus` CLASSIC when `menu_bar = "window"`; `ShellLook.unifiedTitle` true only when `title_bar = "unified"` and `MacTitleBar.isSupported()`; `ShellLook.windowMenus` true when `menu_bar = "window"`; `ShellLook.followThemeAppearance` true when `title_bar = "native"`; `Motion` NONE when `motion = "none"`; `TitleFormat` is `window_title`.
- Spec 10.2: `StandardMenuBar` is File, Edit, View, Pane, Tab, Session, Transfer, Tools, plugin menus, Help; `ClassicMenuBar` is File, Edit, View, Session, Transfer, plugin menus, Tools, Window, Help, with Window holding the Pane items and the Tab items as two sections, then the tool-window toggles; Help holds About Jasper on every platform, macOS included under in-window menus; each built-in menu with no items of its own shows only while it has entries.
- Strategies at the owner (spec 3.3, 16.1.7): `WorkspaceChrome.of` is the only reader of the `WorkspaceLook` variant enums, `ShellChrome.of` of `ShellLook`, and `LookMotion` of `Motion`; `VariantReadTest` enforces it (Task 9). No other class branches on a look.
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

At `deafed424` unless marked; facts about plans 3b to 5b cite the plan.

1. `ApplicationBootstrap.prepareDesktop` calls `configureDesktopProperties()` and then
   `TitleBarStyle.choose(mac, nativeDecorations, compact).configure()` (`ApplicationBootstrap.java:67-72`);
   `configureDesktopProperties` sets `apple.laf.useScreenMenuBar` to `"true"` (`:87-92`); plan 5a Task 9 gives
   `prepareDesktop` its `Startup` with `selection()` and keeps both calls. `ApplicationBootstrapTest` calls the no-argument
   `configureDesktopProperties()` (`ApplicationBootstrapTest.java:64`, `:245`, `:253`).
2. `TitleBarStyle.choose(macOs, nativeDecorations, compact)` returns NATIVE for Compact, else MAC_UNIFIED on macOS, else
   NATIVE or CUSTOM by `nativeDecorations` (`TitleBarStyle.java:16-23`); `configure` sets FlatLaf's window decorations for
   CUSTOM only and leaves macOS alone (`:26-32`); `TitleBarStyleTest` covers both (`TitleBarStyleTest.java:10-26`).
3. Four call sites read `MacTitleBar.isSupported()`: `TerminalWindow.java:66` and `NativeShells.java:146,227,419`.
   `TerminalWindow` installs the title bar, falls back to `installCustomTitleBar`, and puts the menu bar on the unified
   bar or `frame.setJMenuBar` (`TerminalWindow.java:66-70`). A `MacTitleBar` is null when unsupported
   (`MacTitleBar.java:73-75`).
4. `WindowContent.installTitleBar(root, content, supported, nativeTitle)` mounts the content, moves the header into a
   unified bar, wires `onTitle` from `WindowHeaderContext` (`WindowContent.java:297-334`) and sets NSAppearance from the
   theme only for Compact on macOS (`:326-330`); `installCustomTitleBar` decorates Windows and Linux frames once FlatLaf
   decorates them (`:344-358`). `initializeViewMode(COMPACT)` removes the header (`:733-740`).
5. `NativeShells.frame` puts the minimal menu bar on the unified bar or `frame.setJMenuBar` (`NativeShells.java:146-148`)
   and subscribes to theme changes (`:160-164`); `dialog` installs chrome the same way (`:227`, `:241-245`); the minimal
   menu is New Window, Close Window (⌘W) and, off macOS, Quit (`:494-514`). JBR's API (`jbr-api` 1.9.0,
   `com.jetbrains.WindowDecorations`) offers only custom title bars (`setCustomTitleBar`, `CustomTitleBar`); it has no
   dark-caption call for a title bar the system draws (checked with `javap` on 2026-10-16).
6. `WindowChrome` builds File, Edit, View, Pane and Tab and adds them to `CollapsingMenuBar` in that order
   (`WindowChrome.java:66-91`); after 3d, `rebuildContributedMenus` builds Session, Transfer and Tools fresh each pass and
   adds them, then every TOP_LEVEL section in registration order (3d Task 1). The app's Help is a TOP_LEVEL section with
   key `app.help` added after plugins start (`JasperApplication.java:950`), so today it is last. `AboutWindows.helpEntries(macOs)`
   leaves About out on macOS (`AboutWindows.java:74-80`).
7. `Contributions` keeps one VIEW section, `panelsMenu`, holding a "Tool Windows" submenu of every panel's toggle action
   (`Contributions.java:254-265`); `panels()` lists the panels and `PanelEntry.toggleActionId()` names each toggle
   (`Contributions.java:162`, `PanelEntry.java:37`).
8. `NativeStyles.menu` styles every menu and item with the BODY face and an accent selection with `text.onAccent`
   (`NativeStyles.java:146-156`). FlatLaf 3.7's `FlatMenuBarUI` styles `itemMargins`, `selectionInsets`, `selectionArc`,
   `hoverBackground`, `selectionBackground` and `selectionForeground` through `FlatLaf.style`
   (`FlatMenuBarUI.java:73-84`), and `FlatMenuItemBorder` gives top-level menus the bar's `itemMargins`
   (`FlatMenuItemBorder.java:39-49`); a hovered, closed top-level menu has no foreground style.
9. Animations: `ThemeController.configureAnimations(boolean)` and `animationsEnabled()` (`ThemeController.java:26-28`)
   feed `AppearanceBindings` (`:100`) and `PluginRuntime.animations` (`JasperApplication.java:879`);
   `ConfigurationController.configureThemes` sets it from the snapshot (`ConfigurationController.java:273`); windows take
   `AnimationSettings` through `WorkspaceConfiguration.apply` (`WorkspaceConfiguration.java:19`), which `WindowTabs`
   (`WindowTabs.java:81`) and `WindowCommandPalette` (`WindowCommandPalette.java:138`) read.
10. `WindowHeaderContext.from(pane, home, programTitle, command)` is today's label (`WindowHeaderContext.java:8-34`); a
    provider pane names its saved identity in `PaneSnapshot.shell()`, a remote one its OSC host in `remoteDirectory()`
    (`:15-21`); the selected content gives `title()`, `host()` and `location()` (`WindowContent.java:317`).
11. `DesktopTestSupport.content(...)` builds `WindowContent`s for tests (`DesktopTestSupport.java:47-75`);
    `MockUiPreview` mounts a window on a bare `JRootPane` through `installTitleBar` and prints it at 2× (`MockUiPreview.java:79-118`).
12. Plan 4a appends its roles after `HISTORY_CONTENT_GAP(2)`, ending the enum with `DOCK_RULE(1), DOCK_RULE_HIT(6);`, and
    appends their rows to `compact-roles.tsv`; `classic-roles.tsv` lists every role of `DesignMetrics.CLASSIC`
    (4a Tasks 2 and 3). Plan 4c Task 1 then appends `GROUP_ROW_INDENT(14);` after `DOCK_RULE_HIT(6),`, a
    `GROUP_ROW_INDENT\t14` row to both tables and the name to `NewRolesTest.ADDED`, so after 4c the enum and both tables
    end with `GROUP_ROW_INDENT`. 4a's `NewRolesTest.theAddedRolesCloseTheEnumInOrder` asserts that `ADDED` is the enum's
    tail, and `ClassicCatalogTest.theSpecsNamedValuesHold` reads `ADDED` (4a Task 2 step 1, Task 3).

### Mock measurements

Measured with Pillow at the mocks' 2× pixels (`docs/design/enterprise/reference/01 Workstation/Workstation - Connected
(plugins in use).png` and `08 Dark Mode/Workstation Dark - Connected (plugins in use).png`, 2884 × 1804) and halved.
Window content starts at x = 2, y = 60 (2×): a 1 px outer border, then the native title bar.

| Measured | Light | Dark | Logical target | Owner |
|---|---|---|---|---|
| Native title bar | y 2-57 `#E1E3E6`, rule y 58-59 `#C4C7CC` | `#36393E`, `#2A2C30` | 28 + 1, drawn by the system | the OS |
| Menu row | y 60-107 `#ECEDEF` | `#3C3F44` | 24 tall on `surface.app` | `ClassicMenuBar` |
| Rule under the menu row | y 108-109 `#D3D6DA` | `#4A4D53` | 1 px `border.divider` | `ClassicMenuBar` |
| Menu label starts (ink, 2×) | 32, 107, 183, 272, 391, 516, 609, 734 | same | first at 15 from the content edge, 20 between labels | `MENU_BAR_SIDE` 4 + `MENU_BAR_ITEM_PADDING` 10 |
| Menu label ink | `#1E1F22`, cap 74-92 (2×) | `#DFE1E5` | `text.primary`, 12 px UI face, cap top 7 below the row top | BODY |
| Toolbar row (plan 6b) | y 110-177 `#ECEDEF`, rule 178-179 `#C4C7CC` | `#3C3F44`, `#2A2C30` | 34 + 1 px `border.window` | 6b |

The Dark row's divider (`#4A4D53`) is lighter than its surface, which `border.divider`'s recipe does not give in Enterprise
Dark (`mix(border.window′, surface.card, 73 %)` ≈ `#2B2D31`); plan 6b makes `border.divider` overridable and sets it
(Rulings 9). Until 6b lands, the Dark menu-row test compares the row and the labels and records the rule as a measured
residual.

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
5. **Menu-row metrics are new catalog roles with the same value in both catalogs** (`MENU_BAR_SIDE` 4,
   `MENU_BAR_ITEM_PADDING` 10, `MENU_BAR_HEIGHT` 24), read only by `ClassicMenuBar`, following plan 4a's Rulings 6; the
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
  `WindowCommandPalette.java` (motion).
- `jasper-app/src/main/java/dev/jasper/app/windows/`: new `ShellChrome.java`; `NativeShells.java`.
- `jasper-app/src/main/java/dev/jasper/app/contributions/`: `Contributions.java` (`toolWindowsSection`), `MenuTarget.java` (`HELP`).
- `jasper-app/src/main/java/dev/jasper/app/application/`: new `LookMotion.java`; `JasperApplication.java`, `ConfigurationController.java`, `AboutWindows.java`.
- Tests (new): `presentation/ProjectionsTest` (edited), `bootstrap/DesktopFlagsTest`, `workspace/WindowChromeMenusTest`,
  `workspace/WindowShellTest`, `windows/ShellChromeTest`, `application/LookMotionTest`, `application/MotionReachTest`,
  `workspace/WindowTitleTest`, `workspace/ChromeRenderTest`, `workspace/EnterpriseMenuRowMockTest`;
  test support `testsupport/EnterpriseLook`, `testsupport/MockProfile`; resource
  `jasper-app/src/test/resources/dev/jasper/app/workspace/chrome-covering-array.tsv`.

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
- Modify: `jasper-app/src/main/java/dev/jasper/app/bootstrap/ApplicationBootstrap.java:67-72,86-92`
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
the mocks. They are appended after plan 4's roles (the last is 4c's `GROUP_ROW_INDENT`) with the same value in `DEFAULT`
and `CLASSIC` (Rulings 5; 4a's Rulings 6): only classic chrome reads them, so the default look cannot move. 4a's
`NewRolesTest.theAddedRolesCloseTheEnumInOrder` asserts that plan 4's roles end the enum, which these roles would break;
it becomes `theAddedRolesFollowEachOtherInOrder` (plan 4's roles stay one run in their order, wherever later roles go),
and `ChromeRolesTest` checks this plan's run the same way, so a later plan appending roles changes neither test (plan
7 Task 4's script sees that `NewRolesTest` no longer checks the tail and writes its own `DockRolesTest`).

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (enum tail)
- Modify: `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`, `classic-roles.tsv` (rows appended)
- Modify: `jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java` (the tail assertion becomes a run assertion)
- Test: `jasper-ui/src/test/java/dev/jasper/ui/metrics/ChromeRolesTest.java`

**Interfaces:**
- Consumes: plan 4a's catalog (`DesignMetrics.DEFAULT`, `CLASSIC`, `value(Role)`) and `NewRolesTest`; 4c's `GROUP_ROW_INDENT`.
- Produces: `DesignMetrics.Role` constants, in this order: `MENU_BAR_SIDE(4)`, `MENU_BAR_ITEM_PADDING(10)`,
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
        Object[][] rows = {{"MENU_BAR_SIDE", 4}, {"MENU_BAR_ITEM_PADDING", 10}, {"MENU_BAR_HEIGHT", 24}, {"CHROME_BUTTON_PADDING", 8},
            {"CHROME_BUTTON_GAP", 8}, {"CHROME_SEPARATOR_GAP", 10}, {"CHROME_SEPARATOR_INSET", 8}, {"CHROME_GLYPH", 7},
            {"CHROME_GLYPH_GAP", 6}, {"CHROME_CARET", 5}, {"CHROME_CARET_GAP", 7}, {"CHROME_TRAILING_GAP", 6},
            {"TOOLBAR_SEARCH_WIDTH", 300}, {"TOOLBAR_SEARCH_PADDING", 8}, {"HINT_KEYCAP_HEIGHT", 16}, {"HINT_KEYCAP_PADDING", 4},
            {"CLASSIC_TAB_LEADING", 12}, {"CLASSIC_TAB_GAP", 9}, {"CLASSIC_TAB_TRAILING", 10}, {"CLASSIC_TAB_PLUS_INSET", 11},
            {"CLASSIC_TAB_MIN_LABEL", 24}, {"STATUS_CELL_PADDING", 10}, {"STATUS_CELL_DOT_GAP", 7}, {"RUN_GLYPH_GAP", 5},
            {"DIALOG_RADIUS", 4}, {"DIALOG_HEADER_HEIGHT", 26}, {"DIALOG_HEADER_INSET", 10}, {"DIALOG_SWATCH", 8},
            {"DIALOG_SWATCH_GAP", 7}, {"DIALOG_FOOTER_VERTICAL", 8}};
        for (Object[] row : rows) ROLES.put((String) row[0], (Integer) row[1]);
    }

    @Test void theChromeRolesFollowPlanFoursRolesWithTheirMeasuredValuesInBothCatalogs() {
        var names = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf("MENU_BAR_SIDE");
        assertThat(first).as("appended after plan 4's roles, the last of which is 4c's").isEqualTo(names.indexOf("GROUP_ROW_INDENT") + 1);
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
Expected: FAIL: the first assertion reports that `-1` (no `MENU_BAR_SIDE` role yet) is not the index after `GROUP_ROW_INDENT`.

Then make 4a's tail assertion a run assertion, so it holds once this plan's roles follow plan 4's:

```bash
python3 -I - <<'PY'
"""Plan 6a Task 3 step 2: plan 4's roles stay one run in order; they no longer end the enum."""
import pathlib
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java"); text = path.read_text(encoding="utf-8")
old = '''    @Test void theAddedRolesCloseTheEnumInOrder() {
        var roles = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        assertThat(roles.subList(roles.size() - ADDED.size(), roles.size())).containsExactlyElementsOf(ADDED);
    }'''
new = '''    /** Plan 4's roles are one run in order; plan 6's chrome roles follow them (ChromeRolesTest). */
    @Test void theAddedRolesFollowEachOtherInOrder() {
        var roles = java.util.Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = roles.indexOf(ADDED.getFirst());
        assertThat(first).as("plan 4's first role").isPositive();
        assertThat(roles.subList(first, first + ADDED.size())).containsExactlyElementsOf(ADDED);
    }'''
if text.count(old) != 1: raise SystemExit("NewRolesTest: theAddedRolesCloseTheEnumInOrder not found once")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

This edit passes on its own (plan 4's roles are still the tail); `ChromeRolesTest` is the failing test.

- [ ] **Step 3: Add the roles and their rows**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 3 step 3: the classic chrome roles, appended after plan 4a's."""
import pathlib
ADDED = [("MENU_BAR_SIDE", 4), ("MENU_BAR_ITEM_PADDING", 10), ("MENU_BAR_HEIGHT", 24), ("CHROME_BUTTON_PADDING", 8),
    ("CHROME_BUTTON_GAP", 8), ("CHROME_SEPARATOR_GAP", 10), ("CHROME_SEPARATOR_INSET", 8), ("CHROME_GLYPH", 7),
    ("CHROME_GLYPH_GAP", 6), ("CHROME_CARET", 5), ("CHROME_CARET_GAP", 7), ("CHROME_TRAILING_GAP", 6),
    ("TOOLBAR_SEARCH_WIDTH", 300), ("TOOLBAR_SEARCH_PADDING", 8), ("HINT_KEYCAP_HEIGHT", 16), ("HINT_KEYCAP_PADDING", 4),
    ("CLASSIC_TAB_LEADING", 12), ("CLASSIC_TAB_GAP", 9), ("CLASSIC_TAB_TRAILING", 10), ("CLASSIC_TAB_PLUS_INSET", 11),
    ("CLASSIC_TAB_MIN_LABEL", 24), ("STATUS_CELL_PADDING", 10), ("STATUS_CELL_DOT_GAP", 7), ("RUN_GLYPH_GAP", 5),
    ("DIALOG_RADIUS", 4), ("DIALOG_HEADER_HEIGHT", 26), ("DIALOG_HEADER_INSET", 10), ("DIALOG_SWATCH", 8),
    ("DIALOG_SWATCH_GAP", 7), ("DIALOG_FOOTER_VERTICAL", 8)]
path = pathlib.Path("jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java"); text = path.read_text(encoding="utf-8")
old = "GROUP_ROW_INDENT(14);"
if text.count(old) != 1: raise SystemExit("DesignMetrics: the enum no longer ends with plan 4c's GROUP_ROW_INDENT(14);")
lines = [", ".join(f"{n}({v})" for n, v in ADDED[i:i + 6]) for i in range(0, len(ADDED), 6)]
new = ("GROUP_ROW_INDENT(14),\n"
       "        // Enterprise presentation plan 6: the classic chrome's menu row, toolbar, tabs, status cells and dialogs,\n"
       "        // measured on the Enterprise mocks and read only by classic chrome.\n"
       "        " + ",\n        ".join(lines) + ";")
path.write_text(text.replace(old, new), encoding="utf-8")
for table in ("compact-roles.tsv", "classic-roles.tsv"):
    path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics/" + table); text = path.read_text(encoding="utf-8")
    if not text.endswith("GROUP_ROW_INDENT\t14\n"): raise SystemExit(f"{table}: expected plan 4c's GROUP_ROW_INDENT last")
    path.write_text(text + "".join(f"{n}\t{v}\n" for n, v in ADDED), encoding="utf-8")
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.metrics.*' :jasper-ui:javadoc`
Expected: PASS (`ChromeRolesTest`, `NewRolesTest` with `theAddedRolesFollowEachOtherInOrder`,
`DesignMetricsTest.everyRoleHasItsCompactValue`, 4a's `ClassicCatalogTest` frozen table, `MetricTokensTest`).

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java jasper-ui/src/test/resources/dev/jasper/ui/metrics \
  jasper-ui/src/test/java/dev/jasper/ui/metrics/ChromeRolesTest.java jasper-ui/src/test/java/dev/jasper/ui/metrics/NewRolesTest.java
git commit -F - <<'EOF'
Window chrome: the classic chrome metrics

Thirty catalog roles for the classic menu row, toolbar, tabs, status
cells and dialogs, measured on the Enterprise mocks and the same in both
catalogs, appended after plan 4's roles and frozen in both tables. Only
classic chrome reads them. NewRolesTest checks plan 4's roles as one run
in order rather than as the enum's tail.

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
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowChrome.java:66-91,246-283,410-418` (the built-in menus, `rebuildContributedMenus`, `refreshTheme`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:232-236` (the constructor), `jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java` (the `presentation` edge, if 5a did not add it)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java:162` (`toolWindowsSection`), `contributions/MenuTarget.java` (`HELP`), `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:950`
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DesktopTestSupport.java` (one factory)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowChromeMenusTest.java`
- Modify: `docs/design/theme-engine/migration.csv` (rows below)

**Interfaces:**
- Consumes: 5a's `WorkspaceLook`, `TitleFormat`; 3d's `MenuTarget.Slot.SESSION`/`TRANSFER`; Task 3's `MENU_BAR_*` roles;
  5b's `EnterpriseResources`.
- Produces: `public final class WorkspaceChrome` with `static final WorkspaceChrome STANDARD`, `static WorkspaceChrome
  of(WorkspaceLook look, TitleFormat title)`, package-private `MenuBarLayout menus()` and `TitleFormat title()`;
  `sealed interface MenuBarLayout permits StandardMenuBar, ClassicMenuBar` with `List<JMenu> fixed(BuiltIn)`,
  `void fresh(Map<MenuTarget.Slot, JMenu>, Supplier<JMenu> pane, Supplier<JMenu> tab)`, `boolean toolWindowsInView()`,
  `List<JMenu> contributed(Pass)`, `void style(JMenuBar)` and the records `BuiltIn(JMenu file, JMenu edit, JMenu view,
  JMenu pane, JMenu tab)`, `TopMenu(JMenu menu, boolean help)`, `Pass(Map<MenuTarget.Slot, JMenu> slots, List<TopMenu>
  topLevel, Function<String, JMenu> menu, List<JMenuItem> toolWindows)`; `WindowContent(ShellLauncher, Path, boolean,
  Consumer<Path>, Runnable, Runnable, ThemeController, KeyBindings, CommandHistory, boolean, WorkspaceChrome)` and
  `WorkspaceChrome look()`; `Contributions.toolWindowsSection()` (`Optional<MenuSection>`); `MenuTarget.HELP`
  (`"app.help"`); test support `EnterpriseLook.kit()`, `registerFonts()`, `controller(boolean dark)`,
  `restoreDefault()`; `DesktopTestSupport.content(ShellLauncher, ThemeController, WorkspaceChrome)`.

- [ ] **Step 1: Write the test support and the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java`:

```java
package dev.jasper.app.testsupport;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeLibrary;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.ui.kit.Kit;
import java.awt.Font;
import java.awt.FontFormatException;
import java.awt.GraphicsEnvironment;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Enterprise as a process runs it, for chrome tests: the shipped kit file through the production parser, its IBM Plex
 * files registered once, and its theme installed under its kit. Tests restore the default look when they finish.
 */
public final class EnterpriseLook {
    private static boolean registered;

    private EnterpriseLook() { }

    /** The shipped kit, with its fonts registered. */
    public static synchronized Kit kit() {
        registerFonts();
        return EnterpriseResources.facts().parsed().orElseThrow(() -> new AssertionError("the shipped kit must parse")).kit();
    }

    /** The shipped kit's chrome points. */
    public static ChromeSpec chrome() {
        return EnterpriseResources.facts().parsed().orElseThrow(() -> new AssertionError("the shipped kit must parse")).chrome();
    }

    /** Registers the plugin's Plex files with AWT, once per JVM. */
    public static synchronized void registerFonts() {
        if (registered) return;
        try (var files = Files.list(EnterpriseResources.RESOURCES.resolve("presentation/fonts"))) {
            for (Path file : files.filter(path -> path.toString().endsWith(".ttf")).sorted().toList())
                GraphicsEnvironment.getLocalGraphicsEnvironment().registerFont(Font.createFont(Font.TRUETYPE_FONT, file.toFile()));
        } catch (IOException | FontFormatException failure) { throw new AssertionError("the Plex files must register", failure); }
        registered = true;
    }

    /** A controller showing Enterprise Light or Dark under the shipped kit; it installs both. EDT. */
    public static ThemeController controller(boolean dark) {
        var theme = EnterpriseResources.theme();
        return ThemeController.startup(dark, new ThemeSelection(ThemeLibrary.bundledOnly(), theme, theme, TerminalSchemeChoice.MATCH), kit(),
            failure -> { throw new AssertionError("Enterprise must install", failure); });
    }

    /** Installs Slate Cobalt under the default kit again, with today's fonts, for the tests that follow. EDT. */
    public static void restoreDefault() { ThemeTestSupport.controller(true).close(); }
}
```

Add the window factory to `DesktopTestSupport` and create the test:

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
                assertThat(bar.getInsets()).isEqualTo(new Insets(0, 4, 1, 4));
                var rule = (MatteBorder) ((CompoundBorder) bar.getBorder()).getOutsideBorder();
                assertThat(rule.getMatteColor()).isEqualTo(DerivedColor.BORDER_DIVIDER.color(themes.current().chrome()));
                var file = bar.menus().getFirst();
                assertThat(file.getHeight()).isEqualTo(24);
                assertThat(file.getX() + file.getInsets().left).as("the first title starts 14 in, its ink at 15").isEqualTo(14);
                assertThat(file.getInsets().left).isEqualTo(10);
                assertThat(file.getFont().getFontName(java.util.Locale.ROOT)).isEqualTo("IBM Plex Sans");
            } finally { themes.close(); EnterpriseLook.restoreDefault(); }
        });
    }
}
```

The Window menu labels are the actions' names (`ActionId.java:14,23,39`: "Split Right", "Next Tab", "Next Prompt"); a
panel registered without a toggle id gets the action "Toggle <title>" (`Contributions.java:154-156`); the plugin action
`dev.x.split` is labelled "split" by the model fixture (`id.substring(6)`).

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
grep -n 'import java.util.Optional' jasper-app/src/main/java/dev/jasper/app/contributions/Contributions.java
grep -n 'dev.jasper.app.presentation' jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java
```

Expected: the first grep prints the import (add `import java.util.Optional;` if it prints nothing); the second prints
the line 5a added (`workspace → presentation`, spec 9.2); if it prints nothing, add `dev.jasper.app.presentation` to the
package-info's allowed outgoing dependencies in the same sentence style.

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
        (T + "testsupport/EnterpriseLook.java", "app.testsupport", "EnterpriseLook", test, "Test/fixture ownership; New in Enterprise presentation Plan 6a Task 4; the shipped kit; fonts and theme for chrome tests"),
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
`StaticDefaultTest` (the classic bar reads the installed kit, not `DesignMetrics.DEFAULT`).

- [ ] **Step 6: Commit**

```bash
git add jasper-app/src/main/java/dev/jasper/app/workspace jasper-app/src/main/java/dev/jasper/app/contributions \
  jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java jasper-app/src/test/java/dev/jasper/app/workspace \
  jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Window chrome: the menu composition

MenuBarLayout orders the same contributions two ways. StandardMenuBar is
today's bar; ClassicMenuBar reads File, Edit, View, Session, Transfer,
plugin menus, Tools, Window and Help, with Window holding the Pane and Tab
items and the tool-window toggles, Help last whatever the registration
order, and the mocks' 24 px row over a border.divider rule. WorkspaceChrome
is the one reader of WorkspaceLook's variants; windows default to its
standard chrome.

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
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/TerminalWindow.java:47-70`, `WindowContent.java:139-140,297-334` (the diagnostics dialog, `installTitleBar`, new `mount`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/windows/NativeShells.java:73-78,141-171,207-277,419-423,472-476` (the chooser's `sheet`), `windows/package-info.java` (the `presentation` edge, if 5a did not add it)
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
grep -n 'dev.jasper.app.presentation' jasper-app/src/main/java/dev/jasper/app/windows/package-info.java
```

Expected: the grep prints the edge 5a added (`windows → presentation`, spec 9.2); if it prints nothing, add
`dev.jasper.app.presentation` to the allowed dependencies there. `TerminalWindow` still imports `MacTitleBar` for its
field; if the compiler reports `SystemInfo` or `MacTitleBar` imports unused, leave them (they are used).

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
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java:311-320` (the `onTitle` wiring)
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
- Consumes: Tasks 4 to 7; `EnterpriseLook`; 4a's `KitFixtures.complement()`; 5a's `Projections`, `ChromeSpec`.
- Produces: `public final class MockProfile` with `REFERENCE`, `RENDERS`, `record Run(int start, int length, Color
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
 * along a column or a row, and clusters of ink along a row. Colours compare as CIE76 ΔE in L*a*b* (D65). Fonts, colours
 * and geometry come from the shipped look, so a profile that differs is a chrome defect or a recorded residual.
 */
public final class MockProfile {
    /** The preserved Enterprise mocks. */
    public static final Path REFERENCE = EnterpriseResources.ROOT.resolve("docs/design/enterprise/reference");
    /** Where chrome renders are written for inspection and the opt-in pairs. */
    public static final Path RENDERS = EnterpriseResources.ROOT.resolve("jasper-app/build/chrome-renders");

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
                    themes.close();
                    EnterpriseLook.restoreDefault();
                }
            });
            DesktopTestSupport.closeOwners();
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
 * Spec 16.1.4 against the mocks: the Enterprise menu row, rendered at 2× under the shipped kit, Plex and the Enterprise
 * theme, compared with the Workstation (Light) and Dark Mode mocks by profile ("Mock measurements"; Rulings 12).
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
        } finally { themes.close(); EnterpriseLook.restoreDefault(); }
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

To see the harness fail first (TDD for the comparison itself), set `MENU_BAR_ITEM_PADDING`'s value in `ChromeRolesTest`
and `DesignMetrics` to 8 temporarily: `theMenuRowMatchesTheMock` must fail with "title 1 starts" off by 8 px (two
paddings at 2×); restore 10. If a comparison fails with the real values, open the two PNGs beside the mock crop
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

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-ui:test --tests 'dev.jasper.ui.gallery.EnterpriseGalleryTest'`
Then: `python3 -I tools/visual/check_pairs.py build/chrome-compare chrome-menu-row`
Expected: BUILD SUCCESSFUL (`EnterpriseGalleryTest` reads only `classic-*` pairs, so the new pairs leave it alone); the
check prints `chrome-menu-row-light: PASS …` and `chrome-menu-row-dark: PASS …`. Record both agreements for the STATUS
entry; a FAIL with a missing colour is a defect to fix as in Step 2, a low agreement from the crop's content (the mock has
no plugin menu text differences) is a floor to lower to the measured value rounded down to 0.05, recorded in the banner.

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

`VariantReadTest` gains spec 16.1.7's projection readers for this plan's points (`WorkspaceLook`'s variants only in
`WorkspaceChrome`, `Motion` only in `LookMotion`, each besides the presentation package); the documents spec 17 names
for this plan get their smallest edits; the View modes specification gets its spec 19.2 banner line; `docs/STATUS.md`
gets this plan's entry; the default look is proven unchanged and the whole build runs.

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java` (two tests)
- Modify: `docs/app-architecture.md:161,172` (the presentation row and the title-bar paragraph at `:210-219`), `docs/superpowers/specs/2026-10-03-jasper-view-modes-design.md:3`, `docs/STATUS.md:1`, this plan's first lines

**Interfaces:**
- Consumes: everything above.
- Produces: `VariantReadTest.PROJECTION_READERS` (a map from a variant pattern to the classes that may read it), which
  plans 6b to 9 extend.

- [ ] **Step 1: Write the failing scan tests**

```bash
python3 -I - <<'PY'
"""Plan 6a Task 9 step 1: the projection readers of spec 16.1.7."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java"); text = path.read_text(encoding="utf-8")
old = '''    @Test void aReadElsewhereIsReported() throws Exception {'''
new = '''    /**
     * Spec 16.1.7 for the window chrome: each look projection's variants are read only by the factory or supplier that
     * owns them, besides the presentation package that computes them. Plans 6b to 9 add their points.
     */
    static final java.util.Map<Pattern, java.util.function.Predicate<String>> PROJECTION_READERS = java.util.Map.of(
        Pattern.compile("dev/jasper/app/presentation/WorkspaceLook\\\\$(?:Menus|Header|Tabs|Status)(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/workspace/WorkspaceChrome")
                || type.startsWith("dev/jasper/app/workspace/WorkspaceChrome$"),
        Pattern.compile("dev/jasper/app/presentation/Motion(?=[;$.]|$)"),
            type -> type.startsWith("dev/jasper/app/presentation/") || type.equals("dev/jasper/app/application/LookMotion"));

    /** The classes in {@code files} that read a projection's variants without being allowed to, as "class reads pattern". */
    static List<String> projectionReaders(List<byte[]> files) {
        var found = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            PROJECTION_READERS.forEach((pattern, allowed) -> {
                if (!allowed.test(scanned.name()) && scanned.strings().stream().anyMatch(s -> pattern.matcher(s).find()))
                    found.add(scanned.name() + " reads " + pattern.pattern());
            });
        }
        return found;
    }

    @Test void onlyTheFactoriesReadTheLooksVariants() throws Exception {
        var files = jars();
        var live = new ArrayList<String>();
        for (var bytes : files) {
            var scanned = StrategyIsolationTest.Scanned.of(bytes);
            if (scanned.strings().stream().anyMatch(s -> s.contains("dev/jasper/app/presentation/WorkspaceLook$Menus"))) live.add(scanned.name());
        }
        assertThat(live).as("the scan is live").anyMatch(name -> name.startsWith("dev/jasper/app/workspace/WorkspaceChrome"));
        assertThat(projectionReaders(files)).isEmpty();
    }

    @Test void aLookReadElsewhereIsReported() throws Exception {
        byte[] probe = ClassFile.of().build(ClassDesc.of("dev.jasper.app.workspace.WindowChromeProbe"), builder -> builder
            .withField("menus", ClassDesc.of("dev.jasper.app.presentation.WorkspaceLook$Menus"), ClassFile.ACC_PRIVATE)
            .withField("motion", ClassDesc.of("dev.jasper.app.presentation.Motion"), ClassFile.ACC_PRIVATE));
        assertThat(projectionReaders(List.of(probe))).hasSize(2);
    }

    @Test void aReadElsewhereIsReported() throws Exception {'''
if text.count(old) != 1: raise SystemExit("VariantReadTest: aReadElsewhereIsReported moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

The Java pattern string is `"dev/jasper/app/presentation/WorkspaceLook\\$(?:Menus|Header|Tabs|Status)(?=[;$.]|$)"`
(the Python string doubles its backslashes again). Check `VariantReadTest` imports `java.util.ArrayList`, `List`,
`ClassFile` and `ClassDesc` (4b's file does).

- [ ] **Step 2: Run the scan**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.architecture.VariantReadTest'`
Expected: PASS (4 cases). To see it fail, add `private dev.jasper.app.presentation.Motion probe;` to `WindowContent`
temporarily: `onlyTheFactoriesReadTheLooksVariants` names `dev/jasper/app/workspace/WindowContent`; remove it again.

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

Then add one sentence to the `presentation` row of `docs/app-architecture.md`'s package table (5a's row begins
"| `presentation` | The presentation a process runs with"): after its first sentence, insert "The window chrome's
projections are read by `WorkspaceChrome.of`, `ShellChrome.of` and the application's `LookMotion`." Run
`./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'` and expect PASS (links resolve).

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

Expected: `dark: identical` and `light: identical`. Then run the reference pairs and the calibration suites as they
are: `./gradlew :jasper-app:test --tests '*Calibration*' --tests '*Reference*'` must pass unchanged.

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
Task 8's `check_pairs.py` lines, and `<deviations>` with every deviation from this plan's text, or "none beyond the plan's
Rulings"):

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
supplier is the theme controller's flag; (12) mock comparison by profile. Deviations from the plan's text: <deviations>.
Plan-6b entry conditions: the `WINDOW_CONTENT` group, `border.divider` overridable, the header, tabs, status cells and
dialogs. `./gradlew check --continue`: **<counts>**. GUI checks for the user (spec 16.2): with Enterprise on, the macOS
in-window menu bar and native title (⌘W in a plugin window, About in Help), Windows and Linux NATIVE with the caption
following the system setting, Compact keeping the menu row, nothing moving with animations on. Not merged, not pushed.
```

Replace this plan's first heading line with the same heading followed by a status banner:

```markdown
> **Status (2026-10-16): executed on `claude/enterprise-window-chrome` (`<range>`).** Deviations from this plan's text: <deviations>.
```

- [ ] **Step 7: Commit**

```bash
git add docs jasper-app/src/test/java/dev/jasper/app/architecture/VariantReadTest.java
git commit -F - <<'EOF'
Window chrome: the shell's documentation, scan and status

VariantReadTest keeps WorkspaceLook's variants in WorkspaceChrome and
Motion in LookMotion. The app architecture describes the native-titled
shell, the menu layouts and the title template; the View modes design
gains its amendment line. STATUS records plan 6a with its rulings, the
mock comparison and the full build's counts.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```
