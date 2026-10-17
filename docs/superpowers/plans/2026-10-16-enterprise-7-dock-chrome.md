# Enterprise presentation 7: dock chrome Implementation Plan

> **Status (2026-10-17): executed on `claude/enterprise-dock-chrome` (`947b3d9e1..deb7ea3c1`, then the documentation commit).** Deviations from this plan's text: plan 6's names are the merged 6a and 6b ones ("Plan 6's names", Execution notes R2), and the Enterprise kit and theme are 6a's fixtures until plan 5b, with the face-dependent probes printed (R4, R5); Task 9: a native header strip lays its actions' row out in a `GridBagLayout`, so every action is centred on the strip as Hide is (a flow layout put the row at the strip's top), and `HeaderStripTest` asserts it. `./gradlew check --continue`: 11,117 tests, 0 failures, 0 errors, 25 skipped.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Under a look whose kit file says `tool_windows = "stripes"` (Enterprise), each window docks its tool windows the
classic way and matches the Workstation, Dark Mode and Unlock Vault mocks: labelled side stripes whose labels start
with "N: " when the panel's toggle is bound to a digit, a bottom strip with count pills, flush frames divided by 1 px
rules with header strips (active and inactive, parenthesised counts, muted details, the header icons), drops onto the
bottom strip, a saved dock layout per look, and the Notifications panel titled "Event Log" at Bottom Left. The default
look stays byte-identical.

**Architecture:** `WorkspaceChrome.of` (plan 6) turns `WorkspaceLook.stripes` and `frames` into one value,
`DockChrome(labelledStripes, flushFrames)`, which builds the window's `StripeSet`: today's two `ToolStripe` rails
(`IconRails`) or two `LabelledStripe`s and a `BottomStrip` (`LabelledStripes`). All three stripes extend one abstract
`DockStripe`, the contract `WindowContent`, `WindowContributions` and `DockDrag` use. `ToolWindowArea`,
`ToolWindowHeader` and `DockDrag` read the window's kit instead of the installed one (spec 6.6) and take a `flush` flag
that paints 1 px `DOCK_RULE`s with `DOCK_RULE_HIT` drag areas, tracks the focused frame and draws header strips; borrowed
kit headers get the same strip through a host flag on `PanelHostHeader`. Colours come from `ChromePainter`'s dock
painters, each through a declared `(slot, against, guard)` pair. `UiState` keeps a non-default look's dock under
`[look."<id>"]`, keyed by the presentation's `DockLayoutKey`; `NotificationsFeature` takes `AppPanels`. The group lands
in `Presentation.LANDED` once every consumer exists (Task 12).

**Tech Stack:** Java 25 on JetBrains Runtime 25, Swing and FlatLaf 3.7, tomlj 1.1.1, Gradle 9.7 (Kotlin DSL), JUnit 6.1.3
and AssertJ 3.27.7, Python 3.11+ standard library and Pillow for the edit scripts and the mock measurements (always
`python3 -I`).

**Spec:** [`docs/superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md`](../specs/2026-10-15-jasper-enterprise-presentation-design.md)
(sections 1, 3, 5.2, 5.5, 6.6, 7.1, 7.3, 9.1, 9.2, 10.7, 13, 16.1.1 to 16.1.4, 16.1.7, 16.2, 17 and row 7 of 19.1). The
reference mocks are [`docs/design/enterprise/reference/`](../../design/enterprise/reference/) (2x PNGs; the mock wins on
layout, [DESIGN_LANGUAGE.md](../../design/enterprise/reference/DESIGN_LANGUAGE.md) on intent). Targets: `01 Workstation`
(Connected and Disconnected), `08 Dark Mode`, `07 Credential Vault/Credential Vault 1 - Unlock Vault (over
Workstation).png`.

**Workspace:** worktree `/Users/RQ7RQVF/projects/moray/.claude/worktrees/enterprise-7`, branch
`claude/enterprise-dock-chrome`, cut from `main` at `759b2b7d9` (the plan's commits sit on it). Run every command from
the worktree root, never in `~/projects/moray`. Line numbers below were re-anchored at `759b2b7d9` (Execution notes);
every edit script quotes the exact text it replaces and stops with a message when that text is not found the stated
number of times. If a script stops, re-find the quoted text (it moves, it does not change), adjust the anchor and re-run;
never edit around a missing anchor.

## Before you start

Plans 3a to 3e, 4a to 4c, 5a, 6a, 6b and 8a are on `main` at `759b2b7d9`, the branch's base. Plans 5b (the Enterprise
plugin, IBM Plex and the shipped theme) and 9a (Settings chrome) run in parallel in other worktrees: this plan depends on
neither, measures with 6a's Enterprise fixtures until 5b merges, and prints the probes that follow the UI face's glyphs
rather than asserting them (Execution notes R4, R5). Do not rebase, merge or push. Check that the base is what the plan
was refreshed against:

```bash
git merge-base --is-ancestor 759b2b7d9 HEAD && echo base-ok                                                 # base-ok
grep -c 'record Separator(' jasper-sdk/src/main/java/dev/jasper/sdk/ui/Nodes.java                         # 3b: 1
grep -c 'Kit CLASSIC' jasper-ui/src/main/java/dev/jasper/ui/kit/Kit.java                                  # 4a: 1
grep -c 'DOCK_RULE_HIT(6)' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java               # 4a: 1
grep -c 'DIALOG_FOOTER_VERTICAL(8);' jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java     # 6b, the enum's last role: 1
ls jasper-ui/src/main/java/dev/jasper/ui/swing/KitFaces.java jasper-ui/src/main/java/dev/jasper/ui/swing/ClassicTableFace.java  # 4b, 4c
grep -c 'public Kit kit()' jasper-app/src/main/java/dev/jasper/app/appearance/ThemeController.java        # 5a Task 10: 1
grep -c 'LANDED = Set.of(Landed.WINDOW_CHROME, Landed.WINDOW_CONTENT);' jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java  # 6a, 6b: 1
ls jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java \
  jasper-app/src/test/java/dev/jasper/app/testsupport/MockProfile.java jasper-app/src/test/resources/dev/jasper/app/testsupport/enterprise-fixture.json  # 6a
```

If a check prints 0 or a file is missing, stop and re-refresh the plan against the new base.

**Plan 6's names.** Plan 6 was delivered as 6a (`2026-10-16-enterprise-6a-window-chrome-shell.md`) and 6b
(`2026-10-16-enterprise-6b-window-chrome-content.md`); the names this plan uses were checked against their merged code
at `759b2b7d9` and its code blocks use them as they are (Execution notes R2):

| This plan uses | From |
|---|---|
| `public final class WorkspaceChrome` with `STANDARD`, `static WorkspaceChrome of(WorkspaceLook look, TitleFormat title)` and the private constructor `WorkspaceChrome(MenuBarLayout, TitleFormat, WorkspaceLook.Header, WorkspaceLook.Tabs, WorkspaceLook.Status, boolean runItem)`, which keeps the look's choices and makes each window's strategy from them (`header()`, `tabs(WindowContent)`, `statusBar(Action, BooleanSupplier)`), with `equals`/`hashCode` over them (6b Execution notes R29) | 6a Task 4, 6b Tasks 3 to 5 |
| `WindowContent(…, boolean macOs, WorkspaceChrome look)` and `WorkspaceChrome look()` | 6a Task 4 |
| `DesktopTestSupport.content(ShellLauncher, ThemeController, WorkspaceChrome look)`, `closeOwnersNow()` | 6a Task 4 |
| `dev.jasper.app.testsupport.EnterpriseLook` with `CHROME`, `kit()` (4a's fixture kit), `chrome()`, `theme()` (the fixture `JasperTheme`), `controller(boolean dark)` and `restoreDefault()`; no `registerFonts()` until 5b | 6a Task 4, 6a Execution notes R13 |
| `dev.jasper.app.testsupport.MockProfile` with `ROOT`, `REFERENCE`, `RENDERS`, `mock(String)`, `at(BufferedImage, int, int)`, `print`, `write`, `fonts(String, int, int, int)` | 6a Task 8, 6b Execution notes R36 |
| `ChromeRenderTest` with `record Case(String name, Supplier<ThemeController> themes, Projections projections)` and `check(Case, WindowContent, JRootPane, MacTitleBar)`, where "plans 6b to 9 add their surfaces' checks"; its cases use every landed group (`ALL`) | 6a Task 8, 6b Task 8 |
| `ProjectionReadTest.PROJECTION_READERS`, which already allows only `WorkspaceChrome` (and the presentation package) to read `WorkspaceLook.Stripes` and `FrameStyle` | 6a Execution notes R16 |
| `Presentation.LANDED` holding `Landed.WINDOW_CHROME` and `Landed.WINDOW_CONTENT` | 6a Task 1, 6b Task 8 |
| `WindowStatusBar()` (slash segments, no run item) and `WindowStatusBar(StatusCells, RunItem)`; 6b's `WindowPairs` and `WindowPairsTest` | 6b Task 5, Task 8 |

## Global Constraints

- Java 25 on the JetBrains Runtime 25 through the Gradle toolchain; run `./gradlew`, never a system `gradle`, and never `./gradlew --stop` (other sessions share the daemons).
- Never launch the GUI (`:jasper-app:run`, `:jasper-app:bench`) and never train the AOT cache (AGENTS.md); no task here runs `packageDist`, `trainAotCache` or `-Pjasper.aot=true`. `:jasper-ui:gallery` is headless (`java.awt.headless=true`) and may be run; the headless JUnit renders of this plan write PNGs under `jasper-app/build/` and open no window.
- Work only in the worktree above on `claude/enterprise-dock-chrome`; one commit per task; every commit message ends with the two lines `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>` separated by a blank line. Do not push or merge.
- Pixel rule: the default look does not change. Every file the headless gallery writes for Slate Cobalt Dark and Light is byte-identical to the base captured in Task 1 Step 1 (`jasper-ui/build/plan7-base/`), and so are the two default-look dock renders `DefaultDockPixelsTest` writes (`jasper-app/build/plan7-base/default-{dark,light}.png`); no `tools/visual/` pair of the default look changes. Tasks 3, 4, 8, 10 and 13 prove it. The only derived role that changes is Slate Cobalt Dark's `surface.header.active` (#232b3c to #2d3546, Rulings 16), which no default-look painter reads.
- Mock matching is a hard requirement: every surface this plan builds has a headless JUnit render under the Enterprise kit and theme compared with a crop of its mock (Light, Dark and the Unlock Vault mock's scrimmed window), with the tolerances stated in its task. A task is done only when its comparison passes; when it does not, adjust only the knobs the task names, re-run, and look at the images under `jasper-app/build/dock-renders/`. A floor may be lowered only when the ink positions already pass and the remaining difference is glyph rasterisation; then lower it to the measured value minus 0.01 and record the residual in the status banner and in `docs/STATUS.md`. Until plan 5b merges, the Enterprise kit and theme are plan 6a's fixtures (`EnterpriseLook`: 4a's fixture kit in the platform faces, `enterprise-fixture.json`), and every probe that follows the UI face's advance widths or glyphs (a text run's far edge, any position after a label, an agreeing share) is printed through `MockProfile.fonts` or `MockCrops.fontsFloor` instead of asserted, as 6a and 6b did (Execution notes R5); fills, rules, run counts, ink colours, thicknesses, the first ink a padding sets and the icons' centres stay asserted. The merge with plan 5b turns the printed probes into assertions within their stated tolerances and floors.
- Spec 3.3 and 16.1.7: `WorkspaceChrome.of` is the only class that reads `WorkspaceLook.Stripes` and `WorkspaceLook.FrameStyle`; every other class receives `DockChrome`'s booleans or a `StripeSet`. Faces and strategies never reference `Kit` or `ChromeSpec` beyond the `Kit` value they are built with.
- Spec 6.6: dock geometry reads the window's kit. `ToolWindowArea`, `ToolWindowHeader` and `DockDrag` keep no static metric reader; their constructors that take no kit (tests only) read the installed kit as before.
- `dev.jasper.app.dock` stays JDK-only: no Swing, and from `java.awt` only `Rectangle` and `Point` (`verifyApplicationArchitecture`).
- Colours come from the theme only (`ColourLiteralScanTest`); every colour the dock chrome paints is a theme role or a derived role painted through the pair its painter declares in `ChromePainter.DOCK_PAIRS` (spec 7.3).
- No interface without two real implementations (AGENTS.md): `StripeSet` has `IconRails` and `LabelledStripes`; `DockStripe` is an abstract class with three subclasses.
- Javadoc doclint fails the build on errors: jasper-app runs `-Xdoclint:all`, jasper-ui `-Xdoclint:all,-missing`; every `{@link}` must resolve and no comment may contain a raw `<`, `>`, `&` or `->`.
- `docs/design/theme-engine/migration.csv`: every new source under jasper-app, the SDK, the testkit or plugins that `ThemeMigrationCoverageTest.CANDIDATE` matches gets its row in the same commit. A new main source that paints is `standard-ui` with this plan's task number in both task columns; a test or fixture is `nonvisual-helper,none,none` with a disposition beginning "Test/fixture ownership; New in Enterprise plan 7 Task N". A row has exactly eight comma-separated fields, so a disposition uses semicolons. Each task's test run includes `ThemeMigrationCoverageTest`, which names any row still missing; add it in the same style.
- Documentation edits are the smallest that make each document accurate (AGENTS.md); `docs/superpowers/` history and earlier `docs/STATUS.md` entries are not rewritten.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in any source file; `…`, `−`, `×` and `—` in copy and comments are ordinary characters.
- Jasper is an MVP: no compatibility shims, aliases or migration code. The test-only conveniences this plan keeps (`ToolWindowArea(JComponent, DockState)`, `ToolWindowHeader(String, Supplier, Runnable)`, `DockDrag(…, ToolStripe, ToolStripe)`, `UiState.load(Path)`, `NotificationsFeature`'s three-argument constructor) mean the default look and exist for the existing tests.

## Review Focus

1. A user rebinds Toggle SFTP from ⌘⌥2 to ⌘⌥7, or to a key that is not a digit, in Keymap while a window is open: the
   stripe label reads "7: SFTP" or "SFTP" at once, without a restart, and a running drag is not left half-drawn. Task 7:
   `StripeNumbersWindowTest.aRebindRenumbersTheStripeAtOnce`.
2. A user who arranged panels under the default look turns Enterprise on, rearranges, and turns it off again: Enterprise
   starts from the registered defaults (Event Log at Bottom Left), keeps its own arrangement under its key, and the
   default look gets its earlier layout back exactly; a hand-edited `look` table with a bad entry falls back alone. Task
   11: `UiStateLookTest.eachLookKeepsItsOwnLayoutAndTheOthersSurviveASave` and `aBadLookEntryFallsBackAlone`.
3. Dragging a tool window onto the bottom strip between two labels, onto its empty right half, and onto a hidden bottom
   strip at the workspace's bottom edge: the window lands in Bottom Left at that index, in Bottom Right, and the strip
   appears with it. Task 6: `DockDragBottomStripTest` (three cases).
4. A 24 pt interface font, or a long title with an unread badge: the stripe thickens to the label's line, a label is
   never cut across its thickness, the pill stays inside its tile, labels never overlap, and the window's minimum size
   counts the bottom strip. Task 5: `LabelledStripeTest.aLargeFontThickensTheStripeAndNoLabelOverlaps`; Task 6:
   `BottomStripTest.theStripCountsInTheWindowsMinimumSize`.
5. A dark theme with near-equal surfaces (Slate Cobalt Dark, the Catppuccin flavours, the degenerate palettes): the
   rules stay visible, the focused header is lighter than the others in a dark theme and darker in a light one, and
   labels, details and pill text keep 4.5:1. Task 4:
   `PaintedContrastTest.theClassicDockPaintsEachPairAsItsGuardMakesItOverEveryPalette` and
   `HeaderSideTest.theActiveHeaderLeansTowardTheTextInEveryShippedVariant`; the painted text in Task 5's
   `LabelledStripeTest.eachLabelPaintsItsDeclaredPairOverEveryPalette` and Task 9's
   `HeaderStripTest.aFlushHeaderPaintsItsTextInItsDeclaredPairsOverEveryPalette`.

## Verified facts

At `deafed424` unless marked; facts about the earlier plans cite the plan. Re-anchored at `759b2b7d9` on 2026-10-17
(Execution notes): the line numbers below that moved are given at `759b2b7d9`; facts 1, 2, 5, 7, 11, 12 (but `KitView`),
13 and 17 had not moved.

1. `ToolStripe` is `final class ToolStripe extends JPanel` (`ToolStripe.java:53`); its `Button` record is
   `(id, title, icon, slot, selected, badge)` with a five-argument convenience (`:55-59`); the callbacks `onToggle`,
   `onMove`, `onHide` and `dragSource` are fields (`:69-73`); `render` keeps the windows whose `slot().stripe()` is its
   side (`:123`); `updateBadge` rebuilds the record with six arguments (`:182`); `beginDrag(String)` (`:239`),
   `dropGroups()` (`:287-293`), `setRailWidth` (`:228-232`) and `getMinimumSize` from the rail width (`:295`).
2. `DockDrag.threshold()` is static over `FlatLafInstaller.installedKit()` (`DockDrag.java:54-56`); the constructor takes
   two `ToolStripe`s (`:92`); `start` calls `stripe.beginDrag(dragging)` (`:213`); `areaAfter` widens by
   `getPreferredSize().width` and tests `slot.stripe() == stripe.side()` (`:257-270`); `standAtEdge` stands a hidden
   stripe over the area's side edge (`:326-333`); `ToolWindowArea.metrics()` is called statically (`:241`, `:278`).
   `DockDragTest` builds `new DockDrag(content, state, area, left, right)` (`DockDragTest.java:83`).
3. `ToolWindowArea.gutter()` and `surfaceArc()` are static over the installed kit (`ToolWindowArea.java:53-56`); `Host`
   is a static nested class whose constructor reads `gutter()` (`:59-78`) and whose `surface()` reads `surfaceArc()`
   (`:96-107`); `center` is a field initializer (`:230`); `DividerHandle` paints nothing (`:156`) and drags its leading
   edge to `position(event)` (`:201-205`); `mountHeader` gives a borrowed header the host actions "more" and "collapse"
   (`:397-399`) and its metadata through `hostHeader(state)` (`:425-429`); `header(id, title)` builds the native header
   (`:460-467`); `refreshGutters` (`:522-540`); `metrics()` is static with 6 px dividers (`:560`).
4. `ToolWindowHeader.logicalHeight()` is static over the installed kit (`ToolWindowHeader.java:31-33`); the hide button
   is `button("collapse", "Hide")` (`:44`); the constructor reads the installed kit's paddings (`:80-81`) and
   `NativeStyles.installed()` for the detail gap (`:88`); `getBackground` is `surface.panel` (`:100`); the connected dot
   paints through `ChromePainter.installed().connectedDot` (`:120`); preferred and minimum heights (`:157-159`).
5. `DockGeometry.Metrics.at(scale)` fixes 6 px dividers (`DockGeometry.java:39-40`); `DockDropResolver.Group(slot,
   bounds, buttons)` (`DockDropResolver.java:24-34`); `resolve` counts the buttons whose vertical middle is above the
   cursor (`:83`).
6. `WindowContent` makes the stripes in field initializers (`WindowContent.java:93`), the area, the body and the drag in
   its constructor (`:272-282`); `leftStripe()` and `rightStripe()` return `ToolStripe` (`:908-909`);
   `syncRailVisibility` (`:928-937`); `applyTheme` refreshes the stripes (`:1315-1316`); `setRailWidth` (`:1446-1454`);
   `rebind` re-applies accelerators through `contributed.applyAccelerators()` (`:623-632`); `setBindings` (`:617-620`).
   The window's chrome is `look()` (6a), whose `WorkspaceChrome` keeps the look's choices and makes each window's
   strategy from them, equal by those choices (6b Execution notes R29).
7. `WindowContributions` wires the stripes' callbacks (`WindowContributions.java:79-83`), restores sizes from
   `state.dock()` (`:189-197`), docks panels from `state.panel` (`:217`), saves through `state.panels`, `putPanel` and
   `setDock` (`:307-327`), renders the stripes from left and right lists (`:329-345`), updates badges (`:352-357`),
   refreshes action labels (`:377`) and resets the stripes on close (`:450-455`); `applyAccelerators` (`:110-112`).
   Nothing else in `src/main` reads `UiState`'s panels or dock.
8. `PanelEntry.toggleActionId()` names the action View › Tool Windows lists (`PanelEntry.java:36-37`);
   `KeyBindings.strokeFor(String)` (`KeyBindings.java:223-225`); `KeyBindings.withOverrides(macOs, overrides)` accepts
   extension ids (`:119-148`). Remote toggles Sessions with `HOSTS` (`cmd+alt+1`/`alt+1`) and SFTP with its `TOGGLE`
   (`cmd+alt+2`/`alt+2`) (`RemotePlugin.java:266`, `:270`; `SftpUi.java:57`).
9. `UiState` keeps one `panels` map and one `dock` (`UiState.java:105-109`); `parse` (`:133-158`) and `readDock`
   (`:161-176`); `panel`, `panels`, `putPanel`, `dock`, `setDock` (`:242-247`); `save` writes version 2 (`:264-285`), and
   `UiStateTest.roundTripsTheDockPanelsWindowsAndTheRail` pins its exact text (`UiStateTest.java:15-50`).
10. `JasperApplication` builds `NotificationsFeature` (`JasperApplication.java:321-322`) after `ThemeController.start`
    (`:304`) and loads `UiState.load(dirs.uiState())` in `startPlugins` (`:936`, the one call); `presentation()` (`:208`)
    is the presentation after start-up's fallback.
11. `NotificationsFeature` registers the panel as `addPanel(PANEL_ID, "Notifications", icon, Slot.RIGHT_TOP,
    Optional.of(SHOW_HISTORY), …)` (`NotificationsFeature.java:64-65`); `HistoryPanel`'s accessible name is
    "Notifications" (`HistoryPanel.java:81`); `NotificationsFeatureTest` builds the three-argument constructor
    (`NotificationsFeatureTest.java:42`).
12. `PanelHeaders.HeaderControl.refresh` writes the count as digits (`PanelHeaders.java:183-184`), the host detail in
    `text.secondary` (`:191-197`), an `EmptyBorder` from `FIELD_HORIZONTAL_PADDING` and
    `PANEL_HEADER_TRAILING_PADDING` (`:199-200`) and the inline layouts (`:202-203`); its dot is `status.success` held to
    3:1 on `surface.panel` (`:160-176`); `HeaderLayout.height` (`:294-308`) and `layoutContainer` (`:319-348`).
    `PanelHostHeader(detail, active, actions)` (`PanelHostHeader.java:18-26`); `KitView.setPanelHostActions` and
    `setPanelHostHeader` (`KitView.java:255`, `:261`). The header's component is a plain non-opaque `JPanel` (`:32-33`),
    so a border that fills its bounds paints behind its labels.
13. `ChromePainter` is constructible for any kit (`ChromePainter.java:22-25`); `connectedDot` holds the dot on
    `surface.panel` (`:233-240`); `measureText` and `prepareText` are public (`:44-46`).
14. `DerivedPalette.recipe` uses `SurfaceContrast.apart` for `surface.header`, `surface.header.active`,
    `surface.stripe`, `surface.tableHeader`, `surface.zebra` and `surface.footer` (`DerivedPalette.java:84-89`, `:98`);
    `apart` keeps a colour that is far enough on either side, else moves it toward black whenever `from`'s L* is at
    least the distance (`SurfaceContrast.java:123`). `DerivedColorTest.INPUTS` pins each recipe's inputs
    (`DerivedColorTest.java:27` for `surface.header.active`). `derived-roles.tsv` freezes Slate Cobalt Dark's header as
    `#2d3036` and its active header as `#232b3c`, darker than the inactive one (plan 2's STATUS hand-off); plan 5b left
    the direction rule to this plan (5b Rulings 8).
15. `IconRegistry` draws "remove" as `M 3 8 H 13` (`IconRegistry.java:352`), "more" (`:371`), "collapse" (`:373`).
16. `WorkspaceMetricsTest.formerStaticMetricsFollowTheInstalledKit` calls the three statics of facts 2 to 4
    (`WorkspaceMetricsTest.java:75-101`); `nativeAdaptersDoNotBorrowGenericKitRoles` forbids `ToolStripe.java` and
    `ToolWindowHeader.java` the generic roles `ICON_GAP`, `STATUS_DOT`, `FIELD_HORIZONTAL_PADDING` and others, and
    requires `ToolWindowHeader.java` to name its `TOOL_HEADER_*` roles (`:45-73`).
17. `ConfigSnapshot.MIN_RAIL_WIDTH` and `DEFAULT_RAIL_WIDTH` are 28 (`ConfigSnapshot.java:22`).
18. `ThemeMigrationCoverageTest` requires numeric task columns for `standard-ui` rows (`ThemeMigrationCoverageTest.java:53-56`).
19. `PaintedContrastTest.DEFAULT_PAIRS` declares the default look's eight pairs with samplers
    (`PaintedContrastTest.java:46-62`) over `KitFixtures.themes()` and `degenerate()` (`palettes()`, `:97-101`); its
    `hasColor(image, color)` scans only an image's right half (`:479-483`), where the off switch's knob sits.
20. Two tests type `owner.leftStripe()` and `owner.rightStripe()` as `ToolStripe`: `WindowPanelsTest.tips(ToolStripe)`
    (`WindowPanelsTest.java:51`) and the loop in `WindowDragTest.Fixture.button` (`WindowDragTest.java:73`).
21. Plan 4a Task 2 appends `STRIPE_LABEL_PADDING(8)`, `STRIPE_LABEL_GAP(1)`, `DOCK_RULE(1)`, `DOCK_RULE_HIT(6)` with
    the same value in both catalogs, then 4c's `GROUP_ROW_INDENT(14)`, 8a's thirty `RUN_WINDOW_*` roles and 6a/6b's
    thirty chrome roles, the enum ending `DIALOG_FOOTER_VERTICAL(8);` (re-anchored); 4a Task 3 writes the classic delta
    as `values.put(Role.X, v);` lines in `DesignMetrics.classic()` before `int[] table = declared();` and freezes
    `classic-roles.tsv`; `NewRolesTest.ADDED` lists the appended roles in order and finds them as one run anywhere
    (`indexOfSubList`), while `RunWindowRolesTest` and `ChromeRolesTest` check their own runs after `DOCK_RULE_HIT`. 4a's classic delta sets
    `NATIVE_TOOL_HEADER_HEIGHT` and `PANEL_HEADER` to 24, `WORKSPACE_GUTTER`, `WORKSPACE_SURFACE_ARC`, `DOCK_DROP_ARC` and
    `RAIL_BUTTON_ARC` to 0, `STATUS_DOT` and `TOOL_HEADER_ACTIVE_DOT` to 7 (4a Task 3). 4a Rulings 16 hands its
    three package-test repairs to plans 6 and 7: the `PanelHeaders` connected dot, `NotificationCardTest`'s count and
    the status bar's configuration text (`ConfigurationStatusTest`); plans 6a and 6b take none of them (6b's
    `WindowPairsTest` samples the classic ruled cells' warning and error text over every palette, not the default
    look's slash segments, which `ConfigurationStatusTest` checks in Slate Cobalt only).
22. Plan 5a Task 8: `Projections.of` takes the dock group (`stripes`, `FrameStyle`, `DockLayoutKey`, `AppPanels`) from
    the file only when `Presentation.LANDED` holds `DOCK_CHROME`; `WorkspaceLook(Menus, Header, Stripes, Tabs, Status,
    boolean runItem, FrameStyle)`; `AppPanels("Event Log", Slot.BOTTOM_LEFT)` under stripes; `DockLayoutKey(String)`.
    5a Task 9: `JasperApplication.presentation()`. 5a Task 10: `ThemeController.kit()`. 5a Rulings 3: converting the
    workspace's installed-kit readers belongs to plans 6 and 7.
23. Plan 5b (the Enterprise plugin, Plex, the shipped theme, `EnterpriseResources`) runs in parallel and is not on
    `main` (re-anchored). Plan 6a's `dev.jasper.app.testsupport.EnterpriseLook` stands in (6a Execution notes R13):
    `kit()` is 4a's `KitFixtures.enterprise()` (the classic base, a 12 px body, the platform faces), `chrome()` spec
    5.1's table, `theme()` the `JasperTheme` parsed from `testsupport/enterprise-fixture.json` (spec 7.6's named roles
    and 6b's chrome targets), `controller(boolean dark)` and `restoreDefault()`; there is no `registerFonts()`. Plan 6a's
    `MockProfile` gives `ROOT` (from `jasper.repoRoot`), `REFERENCE`, `RENDERS`, `mock`, `print`, `write` and
    `fonts(what, actual, expected, tolerance)`, which prints a probe that follows the UI face's advance widths until
    5b. The fixture theme gives Light `surface.control.selected` #D3D9E1, `surface.panel` #F7F7F8, `surface.header`
    #E1E3E6, `surface.header.active` #D9DFEA, `surface.stripe` #E4E6E9, `border.window` #C4C7CC and `border.divider`
    #D3D6DA; Dark #50545B, #35383D, #36393E, #3D4658, #383B40, #2A2C30 and #4A4D53.
24. `KitFixtures.context(theme, kit)` builds a `KitContext` over a fixture binding (`KitFixtures.java:329-333`);
    `KitFixtures.enterprise()` and `complement()` are plan 4a's fixture kits (`:94`, `:101`); `LayoutTestSupport.layoutTree` lays out a
    tree (`LayoutTestSupport.java:8-11`); jasper-app's test task sets `jasper.repoRoot` (`jasper-app/build.gradle.kts:86`).

## Mock measurements

Measured with Pillow (`python3 -I`) at the mocks' 2x pixels, halved to logical pixels. The Connected and Disconnected
Workstation mocks and the Dark Mode mock share one geometry; the Unlock Vault mock is the Disconnected window moved by
(64, 64) at 2x under a black 21 % scrim (white reads #C9CACC). Colours are the Enterprise theme's roles (spec 7.6); they
become theme roles, never literals. Coordinates in the last column are 2x pixels in the Connected mock.

| Surface | Measure | Light | Dark | Rule or role | Where (2x) |
|---|---|---|---|---|---|
| Side stripe | fill thickness | 22 | 22 | superseded by the 28 px floor of `window.rail_width` (spec 10.7) | x 2-45 |
| Side stripe | inner rule | 1, #C4C7CC | 1, #2A2C30 | `border.window`, `DOCK_RULE` | x 46-47 |
| Side stripe | fill | #E4E6E9 | #383B40 | `surface.stripe` | (40, 600) |
| Side stripe | open label's tile | #CFD4DC | #50545B | `surface.control.selected` (Light theme #D7D9DC: residual) | (4, 250) |
| Side stripe | first tile below the stripe's top | 4 | 4 | `RAIL_GAP` | y 188.5 |
| Side stripe | tile edge to text ink | 10 | 10 | `STRIPE_LABEL_PADDING` 9 plus the glyph's bearing | ink y 209 |
| Side stripe | between adjacent open tiles | 0 | 0 | `STRIPE_LABEL_GAP` 0 | y 309 |
| Side stripe | label | 12 px regular, cap 9, `text.primary` | #DFE1E5 | `LABEL` | |
| Side stripe | left ink runs (rel. stripe top) | 14.5-54.5, 75.5-150.5 | same | "1: SFTP", "2: File Servers", bottom to top | y 209-289, 331-481 |
| Side stripe | right ink runs | 14.5-60.5, 82-123.5 | same | "Sessions", "Tunnels", top to bottom | y 209-301, 344-427 |
| Side stripe | label line across the fill | centred | | baseline and ascent centred | |
| Bottom strip | fill height, top rule | 24, 1 | | the stripe thickness 28 (Rulings 4) | y 1708-1755, rule 1706-1707 |
| Bottom strip | first text ink from the window edge | 33 = 23 + 10 | | lead = the side stripes' thickness, then the padding | x 68 |
| Bottom strip | item dividers | 1, #D3D6DA | #4A4D53 | `border.divider`, one after each item | x 190, 334, 520 |
| Bottom strip | ink runs | 33-84, 105.5-155, 177.5-228, pill 233-249 | same | "Event Log", "Transfers", "Problems", pill | x 68-170, 213-311, 357-456, 468-499 |
| Bottom strip | text | cap top 8, baseline 17 of 24 | | centred | |
| Count pill | size, corner | 16 × 14, 7 | | `STRIPE_PILL_WIDTH`, `STRIPE_PILL_HEIGHT`, full round | y 1718-1745 |
| Count pill | text ink to pill | 6 | | `STRIPE_PILL_GAP` 5 plus the bearing | |
| Count pill | colours | #F2D79B, #6B4A00 | #6B5A30, #E3C27A | the deferred Problems panel's amber; this plan's tones are ACCENT and ERROR (spec 7.1) | |
| Header strip | height | 24 fill + 1 rule | | `tool_window_header` 24, `DOCK_RULE` | y 180-227, rule 228-229 |
| Header strip | active, inactive | #D9DFEA, #E1E3E6 | #3D4658, #36393E | `surface.header.active`, `surface.header` | (500, 200), (500, 960) |
| Header strip | title ink from the frame edge | 8.5 | | `FIELD_HORIZONTAL_PADDING` 8 (kit), `TOOL_HEADER_LEADING_PADDING` 8 (native) | x 65 |
| Header strip | title | 12/600, `text.primary` | #DFE1E5 | `PANEL_HEADER_TITLE`, `PANEL_TITLE` | |
| Header strip | title ink to the next ink | 8-9 | | `HEADER_STRIP_TITLE_GAP` 8, plus bearings | |
| Header strip | connected dot | 7, #2E9E4F | #4CB86A | `STATUS_DOT`, `TOOL_HEADER_ACTIVE_DOT` 7, `status.success` | x 138-151 |
| Header strip | dot to detail | 6 | | `HEADER_STRIP_DOT_GAP` 6 | |
| Header strip | detail "Mac" | #3A3D42 | #C4C8CE | `text.strong.secondary` | x 164-205 |
| Header strip | count "(2)" | 11 px, #5C6370 | #A0A5AD | `text.secondary`, `PANEL_HEADER_COUNT` | x 2264-2290 |
| Header strip | icons | centres 18 apart, last centre 15 from the right edge | | 16 px actions 2 apart, trailing padding 7 | "−" x 612-623 |
| Header strip | glyph ink | #4A4F57 | #B4B8BF | the kit's icon ink (residual, Rulings 15) | |
| Header strip | "−" stroke | 6 | | the "remove" glyph, 10 (residual, Rulings 14) | |
| Frames | rules between frames | 1, #C4C7CC | 1, #2A2C30 | `border.window`, `DOCK_RULE` | x 648, 2134; y 942, 1012 |
| Frames | panel body | #F7F7F8 | #35383D | `surface.panel` | (500, 250) |
| Frames | fixture layout | left 300, right 350; left split 381/762, right 416/762 | | fixtures, not defaults (Rulings 18) | |

Header ink in 2x pixels relative to each frame's left edge and header top: SFTP title 17-72, dot 90-103, "Mac" 116-157,
"⋯" 524-543, "−" 564-575, title cap rows 15-32, glyph rows 23-25; Sessions title 17-112, "(2)" 128-154, "+" 556-567,
"↙" 591-604, "⋯" 624-643, "−" 664-675; File Servers title 18-146, "(1)" 161-187; Tunnels title 16-102, "(1)" 117-143.

## Rulings

Decisions where the spec is silent or a literal reading cannot be built, taken on the user's behalf (2026-10-16). Each
is "what — why — cost if wrong". Record them in `docs/STATUS.md` (Task 14) and in this plan's status banner.

1. **One plan of 14 tasks** — spec row 7 is one runnable deliverable and fits in the split threshold; each task is a
   reviewable unit with its own test — cost if wrong: a longer review per task.
2. **Plan 6's names are assumptions** (the table above) — plan 6 was written in parallel; the refresh step re-anchors —
   cost if wrong: one search and replace per name.
3. **`DockChrome(labelledStripes, flushFrames)` is the one value `WorkspaceChrome.of` makes for the dock**, and
   `StripeSet` (sealed: `IconRails`, `LabelledStripes`) the stripes it builds; `DockStripe` is an abstract `JPanel` that
   `ToolStripe` (unchanged code), `LabelledStripe` and `BottomStrip` extend — spec 3.3 keeps the variant enums in one
   factory, and `WindowContent`, `WindowContributions` and `DockDrag` already share one stripe contract — cost if
   wrong: none; until Task 6 `StripeSet` has one implementation, the second arriving in the same plan.
4. **A stripe's thickness includes its 1 px rule and is `max(window.rail_width, the label's line + 2 × RAIL_EDGE +
   DOCK_RULE)`; the bottom strip has the same thickness** — spec 10.7 sets the side stripes' 28 px floor over the mocks'
   22, and one thickness for every stripe keeps the Panel rail width setting meaningful for all of them; the bottom
   strip is 4 px taller than the mock's 24 — cost if wrong: one expression in `StripeLabels.thickness`.
5. **Labels start `RAIL_GAP` (4) from the stripe's top, sit edge to edge (`STRIPE_LABEL_GAP` 0, from 4a's 1), the top
   group straight after the second, with `STRIPE_LABEL_PADDING` 9 (from 4a's 8)** — the mocks show adjacent open tiles
   with no seam and the text ink 10 from each tile edge; 9 plus Plex's bearing gives 10; Task 5's comparison is the
   arbiter and may move the padding by 1 — cost if wrong: one value and its two table rows.
6. **A closed label has no fill, a hovered one `surface.control.hover`, an open one `surface.control.selected`** —
   spec 10.7 names only the open tile; the mocks show only open side labels and closed bottom ones without a fill;
   Enterprise Light's open tile is #D7D9DC against the mock's #CFD4DC (Dark is exact) — cost if wrong: one role.
7. **Rail actions (the Sample plugin's) are labels after the second group, `RAIL_SECTION_GAP` below it, never
   selected** — the icon rail shows them there; a labelled stripe has no icons — cost if wrong: none.
8. **The bottom strip shows Bottom Left's labels from the left, starting at the side stripes' thickness, with a 1 px
   `border.divider` after each, and Bottom Right's from the right, mirrored; it paints a top rule only** — the mock's
   first text ink is 23 + 10 from the window edge and every item ends in a divider; the status bar below paints its own
   rule (plan 6) — cost if wrong: an inset.
9. **Every badge is a count pill, in the bottom strip and on the side stripes (turned with the text)**: 14 tall, at least
   16 wide, 5 padding, fully round, 5 after the text, the CHIP face; ACCENT paints `info.ground` with `accent`, ERROR
   `danger.ground` with `status.danger` — spec 7.1; every `PanelBadge` counts at least one; the mock's amber pill is the
   deferred Problems panel's — cost if wrong: one role per tone.
10. **A toggle's stroke numbers its label when its key is 0 to 9 and it holds Ctrl, Alt or Meta (Shift alone does not
    count)**; the label is "N: Title" and re-renders when the effective keymap changes; two panels bound to one digit
    both show it — spec 10.7's "a single digit with modifiers"; Shift with a digit types a symbol — cost if wrong: one
    predicate.
11. **Rules are `border.window` through the structural guard against `surface.app`**; under flush frames the dock's
    dividers are `DOCK_RULE` wide and their handles `DOCK_RULE_HIT` wide, centred on them, above the frames — spec 7.3
    names Latte's `border.window` on `surface.app` as a known repair, both Enterprise variants hold (1.43:1, 1.34:1) and
    keep the mocks' colours, and a pair against `surface.panel` would repaint Enterprise Dark's rules — cost if wrong:
    one pair.
12. **One header is active: the frame holding the permanent focus owner** — spec 10.7 says "while focused"; the mocks'
    two active headers (SFTP and Sessions) are a fixture; the render tests compare each active header in its own render
    — cost if wrong: none.
13. **A header's non-numeric detail is `text.strong.secondary` and a digits-only one (and a kit header's count) is
    parenthesised in `text.secondary`; the connected dot is held to 3:1 on the header's own surface** — spec 10.7's
    "muted" and "(2)", with the mock's "Mac" and "(2)" colours — cost if wrong: one role.
14. **The hide glyph is the existing "remove" (−)**: no new built-in icon — a new name would be SDK-visible vocabulary
    the testkit's `FakeKit` must mirror; the mock's stroke is 6 px against "remove"'s 10 — cost if wrong: a 4 px longer
    stroke.
15. **A header strip's title is followed 8 px later by its count, dot or detail, and the dot 6 px before its text**
    (`HEADER_STRIP_TITLE_GAP`, `HEADER_STRIP_DOT_GAP`, read only under flush frames, by the native and the kit header
    alike); **header actions are 16 px squares 2 apart under the classic catalog** (`PANEL_HEADER_ACTION` 16,
    `PANEL_HEADER_ACTION_VERTICAL` 0, `PANEL_HEADER_TRAILING_PADDING` 7, a new native `TOOL_HEADER_ACTION` 16,
    `TOOL_HEADER_LEADING_PADDING` 8, `TOOL_HEADER_TRAILING_PADDING` 7); under flush frames a kit header's descriptor
    actions and trailing groups are also 2 apart; glyph ink stays the kit's icon ink (the mock's is 30 % `text.primary`
    over `text.secondary`) — the mocks' 18 px pitch and 15 px last centre — cost if wrong: a few pixels, one value each.
16. **Derived surfaces lean toward the variant's text**: `SurfaceContrast.beyond` keeps a recipe's result at least its
    distance from `from` on the side of `text.primary`'s lightness (lighter in a dark variant, darker in a light one),
    falling back to the other side only when that side has no room; it replaces `apart` in the six recipes — plan 2's
    and 5b's hand-off, and both Enterprise mocks put the active header on that side; only Slate Cobalt Dark's
    `surface.header.active` moves (#232b3c to #2d3546) — cost if wrong: one rule.
17. **ui-state keeps a non-default look's dock under `[look."<id>"]` (`dock` and `panels`); `rail_visible`,
    `zen_mode`, windows, the palette place and the chooser stay global; a save writes every look's tables and the
    default look's keys exactly as before; a bad look entry falls back alone** — spec 10.7's example — cost if wrong:
    one table.
18. **A look's first dock uses `DockState`'s default sizes and each panel's registered default slot** — the mocks'
    300/350 px widths are a user's arrangement; plan 10 gives Remote and File Server their Enterprise anchors — cost if
    wrong: first-start widths.
19. **Event Log is the Notifications panel's title and `HistoryPanel`'s accessible name under the look; the action
    titles ("Notifications: Show History") and Settings' page stay** — spec 10.7 renames the panel only — cost if
    wrong: copy.
20. **The dock group lands in Task 12**, once every consumer exists — 5a Rulings 2 — cost if wrong: none.
21. **The mock comparisons crop surfaces, not the whole window**: side-stripe and bottom-strip crops align on the
    fill's centre across the thickness (28 against 22 and 24), headers and rules at their frame's edges; the Unlock
    Vault crops compare the render with the 21 % scrim laid over it (the scrim is plan 6's) — spec 16.1.4; the
    workspace above and around the dock is plan 6's and plan 10's — cost if wrong: crops move.
22. **The pixel proof for the default look** is the jasper-ui gallery plus `DefaultDockPixelsTest`'s two default-look
    dock renders, captured in Task 1 Step 1 and compared with `cmp` after Tasks 3, 4, 8, 10 and 13 — the gallery does
    not render the app's dock — cost if wrong: none.
23. **Hand-offs (4a Rulings 16), all three taken here**: the kit header's connected dot becomes a declared pair
    (`ChromePainter.DOCK_PAIRS` on the header surfaces) sampled by `PaintedContrastTest`; `NotificationCardTest`'s
    repeat count and `ConfigurationStatusTest`'s configuration text run their guards over every palette — the dot
    belongs to the header strips and the count to Event Log's rows; the configuration text is the status bar's, which
    4a assigns "to the window and bottom strip" and neither 6a nor 6b takes, so it would otherwise have no owner — cost
    if wrong: plan 6b's executor took it too, and the two loops overlap (keep one).
24. **A stripe never scrolls**: labels past its end are clipped by it; every window stays reachable from the Window
    menu, its shortcut, the palette and Move to — IntelliJ's stripes do not scroll either — cost if wrong: an overflow
    menu later.
25. **Under labelled stripes the side stripes have no foot group**: the bottom slots' windows show on the bottom strip,
    which takes their drops — spec 10.7 — cost if wrong: none.
26. **This plan writes spec 19.2's banner lines for the two specifications it implements** (Dock layout, Notifications)
    — no plan of the series owns the amendment lines, and each belongs with the change it records — cost if wrong: two
    lines another plan would also have written.

## File structure

- `jasper-app/src/main/java/dev/jasper/app/workspace/`: new `DockChrome.java` (the dock's look value), `DockStripe.java`
  (the stripe contract), `StripeSet.java` (the two sets), `StripeLabels.java` (label text, size and painting),
  `LabelledStripe.java` (a labelled side stripe), `BottomStrip.java` (the bottom strip), `StripeNumbers.java` (the
  digit of a toggle's stroke); changed `ToolStripe.java`, `ToolWindowArea.java`, `ToolWindowHeader.java`,
  `DockDrag.java`, `WindowContent.java`, `WindowContributions.java`, and plan 6's `WorkspaceChrome.java`.
- `jasper-app/src/main/java/dev/jasper/app/dock/`: `DockGeometry.java` (divider width), `DockDropResolver.java`
  (horizontal groups).
- `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java` (layout per look);
  `application/JasperApplication.java`, `application/NotificationsFeature.java`, `notificationui/HistoryPanel.java`;
  `presentation/Presentation.java` (landing).
- jasper-ui: `swing/ChromePainter.java` (dock painters and pairs), `swing/PanelHostHeader.java` (the frame flag),
  `swing/PanelHeaders.java` (the header strip), `theme/SurfaceContrast.java` and `theme/DerivedPalette.java` (the side
  rule), `metrics/DesignMetrics.java` (seven roles, the classic delta).
- Tests: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockCrops.java` (beside plan 6a's `MockProfile`), two methods on 6a's `EnterpriseLook`;
  `workspace/DefaultDockPixelsTest`, `DockMockTest`, `DockFixture`, `StripeSetTest`, `LabelledStripeTest`,
  `BottomStripTest`, `DockDragBottomStripTest`, `StripeNumbersTest`, `StripeNumbersWindowTest`, `FlushFramesTest`,
  `HeaderStripTest`, `DockChromeMatrixTest`, `EnterpriseDockWindowTest`; `dock/HorizontalDropTest`;
  `persistence/UiStateLookTest`; `application/EventLogTest`; jasper-ui `theme/HeaderSideTest`,
  `metrics/DockRolesTest`, `swing/PanelHeaderStripTest`; edits to `WorkspaceMetricsTest`, `DockDragTest`,
  `ToolWindowHeaderTest`, `ToolWindowAreaTest`, `WindowPanelsTest`, `WindowDragTest`, `PaintedContrastTest`,
  `NotificationCardTest`, `ConfigurationStatusTest`, `ChromeRenderTest`, `ProjectionsTest`, jasper-ui's
  `DerivedColorTest` and the frozen `compact-roles.tsv`, `classic-roles.tsv`, `derived-roles.tsv`.
- `tools/visual/pairs.json` (the dock pairs), documentation (Task 14).

---

### Task 1: The pixel base, the window's kit for the dock, and `DockChrome`

The pixel base for the whole plan is captured first, before any production change. Then the dock's three static metric
readers (spec 6.6: `ToolWindowArea.java:53,55`, `ToolWindowHeader.java:31`, `DockDrag.java:54`) become reads of the
window's kit, and `WorkspaceChrome.of` gains the dock's one look value, `DockChrome`. Nothing a user sees changes.

**Files:**
- Create: `jasper-app/src/test/java/dev/jasper/app/testsupport/MockCrops.java`, `jasper-app/src/test/java/dev/jasper/app/testsupport/MockCropsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/DefaultDockPixelsTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/DockChromeTest.java`,
  `jasper-app/src/main/java/dev/jasper/app/workspace/DockChrome.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java:53-56,59,230,254-260,460-464,560`,
  `ToolWindowHeader.java:31-33,56-63,80,88,116-120`, `DockDrag.java:3,53-56,178,241,278,311`, `WindowContent.java:272`,
  plan 6's `WorkspaceChrome.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java:85-96`, `DockDragTest.java:181,391,466`,
  `ToolWindowHeaderTest.java:130`, `ToolWindowAreaTest.java:72`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: `ThemeController.kit()` (5a Task 10); plan 6's `WorkspaceChrome` (the table above); `KitFixtures.kit(DesignMetrics)`.
- Produces: `record DockChrome(boolean labelledStripes, boolean flushFrames)` with `DockChrome.STANDARD`;
  `WorkspaceChrome.dock()`; `ToolWindowArea(JComponent, DockState, Kit kit, boolean flush)` with instance
  `surfaceArc()`, `dragThreshold()`, `metrics()`, `kit()`, `flush()`; `ToolWindowHeader(String, Supplier<JPopupMenu>,
  Runnable, Kit kit, boolean flush)` with instance `logicalHeight()`; the test support `MockCrops` (below, with
  `fontsFloor`, the printed agreeing share of Execution notes R5), used by every later task.

- [ ] **Step 1: Capture the pixel base**

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockCrops.java`:

```java
package dev.jasper.app.testsupport;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import javax.imageio.ImageIO;
import javax.swing.JComponent;

/**
 * Headless comparison of the dock chrome with the preserved Enterprise mocks (Enterprise presentation spec 16.1.4),
 * beside plan 6a's {@link MockProfile}, which compares by profile: 2x crops of the mocks and of renders at any scale, the
 * Unlock Vault mock's scrim, the share of agreeing pixels, and ink runs along either axis (a side stripe's labels run
 * down it). Test only.
 */
public final class MockCrops {
    /** Where the dock tests write their renders, mock crops and differences. */
    public static final Path OUT = MockProfile.ROOT.resolve("jasper-app/build/dock-renders");
    public static final String CONNECTED = "01 Workstation/Workstation - Connected (plugins in use).png";
    public static final String DISCONNECTED = "01 Workstation/Workstation - Disconnected.png";
    public static final String DARK = "08 Dark Mode/Workstation Dark - Connected (plugins in use).png";
    public static final String VAULT = "07 Credential Vault/Credential Vault 1 - Unlock Vault (over Workstation).png";
    /** The Unlock Vault mock's window lies this far into its image at 2x: Disconnected's (x, y) is its (x + 64, y + 64). */
    public static final int VAULT_OFFSET = 64;
    /** The Unlock Vault mock's scrim over the window: black at 21 % (spec 10.8). */
    public static final double VAULT_SCRIM = 0.21;
    private static final Map<String, BufferedImage> MOCKS = new ConcurrentHashMap<>();

    private MockCrops() { }

    /** A mock by its path under the reference folder, read once through plan 6a's {@link MockProfile}. */
    public static BufferedImage mock(String name) { return MOCKS.computeIfAbsent(name, MockProfile::mock); }

    /** An opaque copy of {@code image}'s rectangle. */
    public static BufferedImage crop(BufferedImage image, int x, int y, int width, int height) {
        var copy = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int row = 0; row < height; row++)
            for (int column = 0; column < width; column++) copy.setRGB(column, row, image.getRGB(x + column, y + row));
        return copy;
    }

    /** {@code component} laid out at {@code width} x {@code height} and printed at {@code scale}, opaque. EDT. */
    public static BufferedImage render(JComponent component, int width, int height, int scale) {
        component.setSize(width, height);
        for (int i = 0; i < 3; i++) LayoutTestSupport.layoutTree(component);
        var image = new BufferedImage(width * scale, height * scale, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        try { g.scale(scale, scale); component.printAll(g); } finally { g.dispose(); }
        return image;
    }

    /** {@code image} under black at {@code alpha}, as a modal scrim lays it. */
    public static BufferedImage scrim(BufferedImage image, double alpha) {
        var out = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) {
                Color c = new Color(image.getRGB(x, y));
                out.setRGB(x, y, new Color((int) Math.round(c.getRed() * (1 - alpha)), (int) Math.round(c.getGreen() * (1 - alpha)),
                    (int) Math.round(c.getBlue() * (1 - alpha))).getRGB());
            }
        return out;
    }

    public static Color at(BufferedImage image, int x, int y) { return new Color(image.getRGB(x, y)); }

    /**
     * An agreeing share that follows the UI face's glyphs: until plan 5b ships IBM Plex the fixture kit draws the platform
     * face, so it is printed as a residual, as {@link MockProfile#fonts} prints a face-width probe; the merge with plan 5b
     * asserts it at least {@code floor}.
     */
    public static void fontsFloor(String what, double actual, double floor) {
        System.out.printf("FONTS residual until plan 5b: %s %.3f, floor %.2f%n", what, actual, floor);
    }

    /** Whether every channel of {@code a} is within {@code tolerance} of {@code b}'s. */
    public static boolean near(Color a, Color b, int tolerance) {
        return Math.abs(a.getRed() - b.getRed()) <= tolerance && Math.abs(a.getGreen() - b.getGreen()) <= tolerance
            && Math.abs(a.getBlue() - b.getBlue()) <= tolerance;
    }

    /** The share of pixels whose every channel is within {@code tolerance}, over two images of one size. */
    public static double agreement(BufferedImage a, BufferedImage b, int tolerance) {
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight())
            throw new IllegalArgumentException("Sizes differ: " + a.getWidth() + "x" + a.getHeight() + " and " + b.getWidth() + "x" + b.getHeight());
        long agree = 0;
        for (int y = 0; y < a.getHeight(); y++)
            for (int x = 0; x < a.getWidth(); x++) if (near(at(a, x, y), at(b, x, y), tolerance)) agree++;
        return agree / (double) ((long) a.getWidth() * a.getHeight());
    }

    /**
     * One stretch of ink along an axis: positions {@code start} to {@code end} inclusive, and its darkest or most
     * different pixel.
     *
     * @param start the first position with ink
     * @param end the last position with ink
     * @param ink the pixel that differs most from the backgrounds
     */
    public record Run(int start, int end, Color ink) {
        /** Its extent in pixels. */
        public int length() { return end - start + 1; }
    }

    /**
     * The ink runs of {@code image} along x, or along y when {@code vertical}: a position has ink when any pixel across it
     * differs from every background by more than {@code threshold} (the sum of the channel differences); positions closer
     * than {@code gap} join one run.
     */
    public static List<Run> runs(BufferedImage image, boolean vertical, int threshold, int gap, Color... backgrounds) {
        int along = vertical ? image.getHeight() : image.getWidth(), across = vertical ? image.getWidth() : image.getHeight();
        var runs = new ArrayList<Run>();
        int start = -1, end = -1, best = -1;
        Color ink = null;
        for (int i = 0; i < along; i++) {
            Color strongest = null;
            int strength = -1;
            for (int j = 0; j < across; j++) {
                Color pixel = vertical ? at(image, j, i) : at(image, i, j);
                int distance = Integer.MAX_VALUE;
                for (Color background : backgrounds) distance = Math.min(distance, Math.abs(pixel.getRed() - background.getRed())
                    + Math.abs(pixel.getGreen() - background.getGreen()) + Math.abs(pixel.getBlue() - background.getBlue()));
                if (distance > threshold && distance > strength) { strength = distance; strongest = pixel; }
            }
            if (strongest == null) continue;
            if (start >= 0 && i - end > gap) { runs.add(new Run(start, end, ink)); start = -1; best = -1; }
            if (start < 0) start = i;
            end = i;
            if (strength > best) { best = strength; ink = strongest; }
        }
        if (start >= 0) runs.add(new Run(start, end, ink));
        return runs;
    }

    /** Writes the render, the mock crop and their difference (white where they agree within 24) under {@link #OUT}. */
    public static void write(String name, BufferedImage render, BufferedImage mock) {
        try {
            Files.createDirectories(OUT);
            ImageIO.write(render, "png", OUT.resolve(name + "-render.png").toFile());
            ImageIO.write(mock, "png", OUT.resolve(name + "-mock.png").toFile());
            if (render.getWidth() == mock.getWidth() && render.getHeight() == mock.getHeight()) {
                var diff = new BufferedImage(render.getWidth(), render.getHeight(), BufferedImage.TYPE_INT_RGB);
                for (int y = 0; y < diff.getHeight(); y++)
                    for (int x = 0; x < diff.getWidth(); x++) diff.setRGB(x, y, near(at(render, x, y), at(mock, x, y), 24) ? 0xFFFFFF : 0xD03A3A);
                ImageIO.write(diff, "png", OUT.resolve(name + "-diff.png").toFile());
            }
        } catch (IOException failure) { throw new UncheckedIOException(failure); }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/testsupport/MockCropsTest.java`:

```java
package dev.jasper.app.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.image.BufferedImage;
import org.junit.jupiter.api.Test;

/** The comparison helpers the dock tests rely on. */
class MockCropsTest {
    @Test void runsJoinCloseInkAndSplitAtAGap() {
        var image = new BufferedImage(40, 4, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < 40; x++) for (int y = 0; y < 4; y++) image.setRGB(x, y, 0xFFFFFF);
        for (int x : new int[] {3, 4, 6, 20, 21}) image.setRGB(x, 1, 0x1E1F22);
        var runs = MockCrops.runs(image, false, 60, 4, Color.WHITE);
        assertThat(runs).extracting(MockCrops.Run::start, MockCrops.Run::end)
            .containsExactly(org.assertj.core.groups.Tuple.tuple(3, 6), org.assertj.core.groups.Tuple.tuple(20, 21));
        assertThat(runs.getFirst().ink()).isEqualTo(new Color(0x1E1F22));
    }

    @Test void theScrimDarkensWhiteToTheVaultMocksGrey() {
        var white = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        white.setRGB(0, 0, 0xFFFFFF);
        assertThat(MockCrops.at(MockCrops.scrim(white, MockCrops.VAULT_SCRIM), 0, 0)).isEqualTo(new Color(0xC9, 0xC9, 0xC9));
    }

    @Test void theMocksAreTheirManifestSize() {
        assertThat(MockCrops.mock(MockCrops.CONNECTED).getWidth()).isEqualTo(2884);
        assertThat(MockCrops.mock(MockCrops.DARK).getHeight()).isEqualTo(1804);
        assertThat(MockCrops.mock(MockCrops.VAULT).getWidth()).isEqualTo(3012);
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DefaultDockPixelsTest.java` (it uses only APIs that exist at
the base, so it renders the base first):

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.nio.file.Files;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.imageio.ImageIO;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Enterprise presentation plan 7's pixel rule for the default look: the icon rails, a borrowed kit header and a native
 * header in today's frames, rendered at 2x for Slate Cobalt Dark and Light into {@code jasper-app/build/dock-renders/}.
 * The plan compares these files with the base it captured before its first change.
 */
@ExtendWith(EdtTestExtension.class)
class DefaultDockPixelsTest {
    @Test void theDefaultLookDockRendersForThePixelRule() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            for (boolean dark : new boolean[] {true, false}) {
                ThemeTestSupport.install(dark);
                var theme = ThemeTestSupport.generated(dark);
                try (var binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
                     var icons = new IconRegistry()) {
                    var context = KitContext.of(Kit.DEFAULT, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
                    var spec = new ViewSpec(new Nodes.Panel("hosts", ControlState.READY,
                        new Nodes.Header("hosts.header", ControlState.READY, "Hosts", OptionalInt.of(3), Optional.empty(), Optional.empty(), List.of()),
                        Optional.empty(), new Nodes.Text("hosts.body", ControlState.READY, "Body", Nodes.TextRole.BODY), Optional.empty()));
                    try (var view = new KitView(context, spec, event -> { })) {
                        var area = new ToolWindowArea(new JPanel(), new DockState());
                        DockTestSupport.show(area, Slot.LEFT_TOP, view, 260);
                        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("Notes"), 240);
                        area.setStripesVisible(true, true);
                        var left = new ToolStripe(Side.LEFT);
                        var right = new ToolStripe(Side.RIGHT);
                        var icon = AppIcons.semantic("folder");
                        left.render(List.of(new ToolStripe.Button(DockTestSupport.id(Slot.LEFT_TOP), "Hosts", icon, Slot.LEFT_TOP, true),
                            new ToolStripe.Button("test.files", "Files", icon, Slot.LEFT_BOTTOM, false)), List.of());
                        right.render(List.of(new ToolStripe.Button(DockTestSupport.id(Slot.RIGHT_TOP), "Notes", icon, Slot.RIGHT_TOP, true)), List.of());
                        var body = new JPanel(new BorderLayout());
                        body.add(left, BorderLayout.WEST);
                        body.add(area, BorderLayout.CENTER);
                        body.add(right, BorderLayout.EAST);
                        var image = MockCrops.render(body, 900, 420, 2);
                        Files.createDirectories(MockCrops.OUT);
                        ImageIO.write(image, "png", MockCrops.OUT.resolve("default-" + (dark ? "dark" : "light") + ".png").toFile());
                        assertThat(image.getWidth()).isEqualTo(1800);
                        area.close();
                    }
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
```

Run from the worktree root:

```bash
./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=dark && ./gradlew :jasper-ui:gallery -Pjasper.galleryTheme=light
rm -rf jasper-ui/build/plan7-base && mkdir -p jasper-ui/build/plan7-base
cp -R jasper-ui/build/gallery/dark-1x jasper-ui/build/gallery/light-1x jasper-ui/build/plan7-base/
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DefaultDockPixelsTest' --tests 'dev.jasper.app.testsupport.MockCropsTest'
rm -rf jasper-app/build/plan7-base && mkdir -p jasper-app/build/plan7-base
cp jasper-app/build/dock-renders/default-dark.png jasper-app/build/dock-renders/default-light.png jasper-app/build/plan7-base/
ls jasper-ui/build/plan7-base/dark-1x | wc -l; ls jasper-app/build/plan7-base
```

Expected: both gallery runs print "Rendered N fixtures"; the test run passes (2 classes); the listing prints the dark
gallery's file count and `default-dark.png default-light.png`. Keep both base directories until Task 14. The pixel check
used later is:

```bash
diff -r jasper-ui/build/plan7-base/dark-1x jasper-ui/build/gallery/dark-1x && diff -r jasper-ui/build/plan7-base/light-1x jasper-ui/build/gallery/light-1x \
  && cmp jasper-app/build/plan7-base/default-dark.png jasper-app/build/dock-renders/default-dark.png \
  && cmp jasper-app/build/plan7-base/default-light.png jasper-app/build/dock-renders/default-light.png && echo PIXELS-UNCHANGED
```

- [ ] **Step 2: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockChromeTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EdtTestExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 3.3 and 10.7: the workspace factory reads the dock's two points once, into one value. */
@ExtendWith(EdtTestExtension.class)
class DockChromeTest {
    /** The default window chrome with the classic dock: the combination the dock tests build windows with. */
    static final WorkspaceLook CLASSIC_DOCK = new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
        WorkspaceLook.Stripes.LABELLED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.FLUSH);

    /** A window chrome with the classic dock and the default window chrome. */
    static WorkspaceChrome classicDock() { return WorkspaceChrome.of(CLASSIC_DOCK, new dev.jasper.app.presentation.TitleFormat("{context}")); }

    @Test void theWorkspaceFactoryReadsTheDockGroupOnce() {
        assertThat(WorkspaceChrome.STANDARD.dock()).isEqualTo(DockChrome.STANDARD);
        assertThat(DockChrome.STANDARD).isEqualTo(new DockChrome(false, false));
        assertThat(WorkspaceChrome.of(CLASSIC_DOCK, new dev.jasper.app.presentation.TitleFormat("{context}")).dock()).isEqualTo(new DockChrome(true, true));
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java`, the three static reads become
instance reads, and a new test pins the window's own kit:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 1 step 2: the dock's metrics follow the window's kit."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceMetricsTest.java"); text = path.read_text(encoding="utf-8")
edits = [
    ('''                assertThat(ToolWindowHeader.logicalHeight()).isEqualTo(40);
                assertThat(new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { }).getPreferredSize().height)
                    .isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));
                assertThat(DockDrag.threshold()).isEqualTo(9);
                assertThat(ToolWindowArea.surfaceArc()).isZero();''',
     '''                var header = new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { });
                assertThat(header.logicalHeight()).isEqualTo(40);
                assertThat(header.getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));
                var area = new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState());
                assertThat(area.dragThreshold()).isEqualTo(9);
                assertThat(area.surfaceArc()).isZero();'''),
    ('''                assertThat(ToolWindowHeader.logicalHeight()).isEqualTo(32);
                assertThat(DockDrag.threshold()).isEqualTo(5);
                assertThat(ToolWindowArea.surfaceArc()).isEqualTo(16);''',
     '''                assertThat(new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { }).logicalHeight()).isEqualTo(32);
                var standard = new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState());
                assertThat(standard.dragThreshold()).isEqualTo(5);
                assertThat(standard.surfaceArc()).isEqualTo(16);'''),
    ('''    /** Enterprise presentation plan 1: geometry that static finals fixed at class load follows the installed kit. */''',
     '''    /** Enterprise presentation plan 7 (spec 6.6): a window's dock reads the kit it was built with, not the installed one. */
    @Test void aWindowsDockReadsItsOwnKit() throws Exception {
        javax.swing.SwingUtilities.invokeAndWait(() -> {
            dev.jasper.app.appearance.ThemeTestSupport.install(true);
            var kit = dev.jasper.ui.testsupport.KitFixtures.kit(dev.jasper.ui.kit.Kit.DEFAULT.metrics()
                .with(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT, 40).with(DesignMetrics.Role.DOCK_DRAG_THRESHOLD, 9)
                .with(DesignMetrics.Role.WORKSPACE_SURFACE_ARC, 0));
            var header = new ToolWindowHeader("Sessions", javax.swing.JPopupMenu::new, () -> { }, kit, false);
            assertThat(header.logicalHeight()).isEqualTo(40);
            assertThat(header.getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(40));
            var area = new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState(), kit, false);
            assertThat(area.dragThreshold()).isEqualTo(9);
            assertThat(area.surfaceArc()).isZero();
            assertThat(area.kit()).isSameAs(kit);
            assertThat(area.flush()).isFalse();
            assertThat(new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState()).surfaceArc())
                .as("the convenience reads the installed kit").isEqualTo(16);
        });
    }

    /** Enterprise presentation plan 1: geometry that static finals fixed at class load follows the installed kit. */''')]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"WorkspaceMetricsTest: expected 1 of {old[:70]!r}, found {text.count(old)}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

Three other tests read the removed statics (four reads in `DockDragTest`: the threshold at `:181` and `metrics()` in the
two guide checks at `:391` and `:466`, whose fixture's area `w.area` is the default look's); they read the installed kit,
which their fixtures build their dock over, or the fixture's area:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 1 step 2: the tests that read the dock's former statics."""
import pathlib
def edit(name, old, new, count=1):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old!r}, found {text.count(old)}")
    path.write_text(text.replace(old, new), encoding="utf-8")
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
KIT = "dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(dev.jasper.ui.metrics.DesignMetrics.Role.{})"
edit(T + "DockDragTest.java", "UIScale.scale(DockDrag.threshold()) + 1", "UIScale.scale(" + KIT.format("DOCK_DRAG_THRESHOLD") + ") + 1")
edit(T + "DockDragTest.java", "ToolWindowArea.metrics()", "w.area.metrics()", 2)
edit(T + "ToolWindowHeaderTest.java", "UIScale.scale(ToolWindowHeader.logicalHeight())", "UIScale.scale(header.logicalHeight())")
edit(T + "ToolWindowAreaTest.java", "int header = UIScale.scale(ToolWindowHeader.logicalHeight());",
     "int header = UIScale.scale(" + KIT.format("NATIVE_TOOL_HEADER_HEIGHT") + ");")
PY
```

If `ToolWindowHeaderTest`'s line names its header otherwise than `header`, use that variable. Once Step 4 has also
replaced `DockDrag`'s reads, no test or source may still call a removed static; this check then prints nothing:

```bash
grep -rn 'ToolWindowArea\.metrics()\|ToolWindowArea\.surfaceArc()\|ToolWindowArea\.gutter()\|ToolWindowHeader\.logicalHeight()\|DockDrag\.threshold()' jasper-app/src
```

Here, after Step 2, it prints only `DockDrag.java`'s two `ToolWindowArea.metrics()` reads; run it again at the end of
Step 4, where it must print nothing.

- [ ] **Step 3: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --tests 'dev.jasper.app.workspace.DockChromeTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `DockChrome`, `dock()`, `dragThreshold()`, `kit()`,
`flush()`, the five-argument `ToolWindowHeader` and the four-argument `ToolWindowArea`.

- [ ] **Step 4: Write `DockChrome` and read the window's kit**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/DockChrome.java`:

```java
package dev.jasper.app.workspace;

/**
 * How a window docks its tool windows under its look (spec 10.7), as {@link WorkspaceChrome#of} reads it from the
 * workspace projection: labelled stripes with the bottom strip or the icon rails, and flush frames with rules and
 * header strips or today's frames. Every other dock class receives these booleans, never the projection's enums.
 *
 * @param labelledStripes whether tool windows are reached by labelled stripes and the bottom strip
 * @param flushFrames whether frames are flush: divided by painted rules, with header strips
 */
record DockChrome(boolean labelledStripes, boolean flushFrames) {
    /** The default look's dock: icon rails and today's frames. */
    static final DockChrome STANDARD = new DockChrome(false, false);
}
```

Plan 6's `WorkspaceChrome` gains the value, built in `of` and given as `DockChrome.STANDARD` to `STANDARD`; its
equality, which plan 6a's `PresentationReachTest` compares `windowLook()` through (6b Execution notes R29), covers it:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 1 step 4: WorkspaceChrome reads the dock's two points once (spec 3.3)."""
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceChrome.java"); text = path.read_text(encoding="utf-8")
edits = [
    ("WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false);",
     "WorkspaceLook.Header.UNIFIED, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, DockChrome.STANDARD);"),
    ("                            WorkspaceLook.Status status, boolean runItem) {",
     "                            WorkspaceLook.Status status, boolean runItem, DockChrome dock) {\n        this.dock = Objects.requireNonNull(dock);"),
    ("return new WorkspaceChrome(menus, title, look.header(), look.tabs(), look.status(), look.runItem());",
     "return new WorkspaceChrome(menus, title, look.header(), look.tabs(), look.status(), look.runItem(),\n            new DockChrome(look.stripes() == WorkspaceLook.Stripes.LABELLED, look.frames() == WorkspaceLook.FrameStyle.FLUSH));"),
    ("    private final boolean runItem;", "    private final boolean runItem;\n    /** The dock's look: read here once from the look's stripes and frames (spec 3.3). */\n    private final DockChrome dock;"),
    ("    TitleFormat title() { return title; }",
     "    TitleFormat title() { return title; }\n    /** How this window docks its tool windows. */\n    DockChrome dock() { return dock; }"),
    ("&& tabs == chrome.tabs && status == chrome.status && runItem == chrome.runItem && title.equals(chrome.title);",
     "&& tabs == chrome.tabs && status == chrome.status && runItem == chrome.runItem && dock.equals(chrome.dock)\n            && title.equals(chrome.title);"),
    ("return Objects.hash(menus.getClass(), header, tabs, status, runItem, title); }",
     "return Objects.hash(menus.getClass(), header, tabs, status, runItem, dock, title); }")]
for old, new in edits:
    if text.count(old) != 1: raise SystemExit(f"WorkspaceChrome: expected 1 of {old!r}; re-find plan 6's text")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
PY
```

Then convert the dock's readers. Run from the worktree root:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 1 step 4: ToolWindowArea, ToolWindowHeader and DockDrag read the window's kit (spec 6.6)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "ToolWindowArea.java", [
    ('''    /** The installed kit's exterior gutter, in logical pixels. */
    private static int gutter() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.WORKSPACE_GUTTER); }
    /** The installed kit's workspace frame arc, in logical pixels. */
    static int surfaceArc() { return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC); }''',
     '''    /** The window's kit's exterior gutter, in logical pixels. */
    private int gutter() { return kit.metrics().value(DesignMetrics.Role.WORKSPACE_GUTTER); }
    /** The window's kit's workspace frame arc, in logical pixels. */
    int surfaceArc() { return kit.metrics().value(DesignMetrics.Role.WORKSPACE_SURFACE_ARC); }
    /** How far, in logical pixels, a press must move before it becomes a dock drag: the window's kit's threshold. */
    int dragThreshold() { return kit.metrics().value(DesignMetrics.Role.DOCK_DRAG_THRESHOLD); }
    /** The window's kit. */
    dev.jasper.ui.kit.Kit kit() { return kit; }
    /** Whether frames are flush: divided by painted rules, with header strips (spec 10.7). */
    boolean flush() { return flush; }''', 1),
    ('''    private static final class Host extends JPanel {''', '''    private final class Host extends JPanel {''', 1),
    ('''    private final DockState state;
    private final Host center = new Host(false);''',
     '''    private final DockState state;
    /** The window's kit: the frames' arc, gutter, rule and header sizes. */
    private final dev.jasper.ui.kit.Kit kit;
    /** Whether frames are flush (the classic look's frame style). */
    private final boolean flush;
    private final Host center;''', 1),
    ('''    ToolWindowArea(JComponent terminalColumn, DockState state) {
        super(null);
        this.state = Objects.requireNonNull(state, "state");
        setLayout(new DockLayout());''',
     '''    /**
     * An area of standard frames over the installed kit, as the default look's tests build it.
     *
     * @param terminalColumn the terminal column the frames dock around
     * @param state the window's dock state
     */
    ToolWindowArea(JComponent terminalColumn, DockState state) {
        this(terminalColumn, state, dev.jasper.ui.swing.FlatLafInstaller.installedKit(), false);
    }

    /**
     * @param terminalColumn the terminal column the frames dock around
     * @param state the window's dock state
     * @param kit the window's kit
     * @param flush whether frames are flush: divided by painted rules, with header strips (spec 10.7)
     */
    ToolWindowArea(JComponent terminalColumn, DockState state, dev.jasper.ui.kit.Kit kit, boolean flush) {
        super(null);
        this.state = Objects.requireNonNull(state, "state");
        this.kit = Objects.requireNonNull(kit, "kit");
        this.flush = flush;
        this.center = new Host(false);
        setLayout(new DockLayout());''', 1),
    ('''            () -> onHide.accept(id));
        header.presentation(''', '''            () -> onHide.accept(id), kit, flush);
        header.presentation(''', 1),
    ('''    static DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }''',
     '''    DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }''', 1)])
edit(W + "ToolWindowHeader.java", [
    ('''    /** The installed kit's header height, in logical pixels. */
    static int logicalHeight() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT);
    }''',
     '''    /** The window's kit's header height, in logical pixels, without a flush header's rule. */
    int logicalHeight() { return kit.metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT); }

    /** The window's kit. */
    private final dev.jasper.ui.kit.Kit kit;
    /** The window's kit's native styles and chrome painter. */
    private final NativeStyles styles;
    private final dev.jasper.ui.swing.ChromePainter painter;
    /** Whether this is a flush frame's header strip (spec 10.7). */
    private final boolean flush;''', 1),
    ('''    /**
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide) {
        super(new BorderLayout());
        this.menu = menu;''',
     '''    /**
     * A standard header over the installed kit, as the default look's tests build it.
     *
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide) {
        this(text, menu, onHide, dev.jasper.ui.swing.FlatLafInstaller.installedKit(), false);
    }

    /**
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     * @param kit the window's kit
     * @param flush whether this is a flush frame's header strip
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide, dev.jasper.ui.kit.Kit kit, boolean flush) {
        super(new BorderLayout());
        this.kit = java.util.Objects.requireNonNull(kit, "kit");
        this.styles = new NativeStyles(kit);
        this.painter = new dev.jasper.ui.swing.ChromePainter(kit);
        this.flush = flush;
        this.menu = menu;''', 1),
    ('''        var metrics = dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics();''', '''        var metrics = kit.metrics();''', 1),
    ('''                int x = titleWidth + NativeStyles.installed().metric(DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);''',
     '''                int x = titleWidth + styles.metric(DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);''', 1),
    ('''            @Override public int getIconWidth() { return NativeStyles.installed().metric(DesignMetrics.Role.TOOL_HEADER_ACTIVE_DOT); }''',
     '''            @Override public int getIconWidth() { return styles.metric(DesignMetrics.Role.TOOL_HEADER_ACTIVE_DOT); }''', 1),
    ('''                dev.jasper.ui.swing.ChromePainter.installed().connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));''',
     '''                painter.connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));''', 1)])
edit(W + "DockDrag.java", [
    ('''import dev.jasper.ui.metrics.DesignMetrics;

''', '', 1),
    ('''    /** How far, in logical pixels, a press must move before it becomes a drag: the installed kit's threshold. */
    static int threshold() {
        return dev.jasper.ui.swing.FlatLafInstaller.installedKit().metrics().value(DesignMetrics.Role.DOCK_DRAG_THRESHOLD);
    }

''', '', 1),
    ('''UIScale.scale(threshold())''', '''UIScale.scale(area.dragThreshold())''', 2),
    ('''ToolWindowArea.metrics()''', '''area.metrics()''', 2)])
edit(W + "WindowContent.java", [
    ('''        area = new ToolWindowArea(terminalColumn, dock);''',
     '''        area = new ToolWindowArea(terminalColumn, dock, themes.kit(), look().dock().flushFrames());''', 1)])
PY
```

The class comment of `DockDrag` mentions no `threshold()` method by name; `grep -n 'threshold()' jasper-app/src/main/java/dev/jasper/app/workspace/*.java`
must print nothing (the two reads are now `area.dragThreshold()`, which the case-sensitive pattern does not match). Then run Step 2's `grep` for the removed statics again: it must print nothing.

- [ ] **Step 5: Run the tests and the pixel check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --tests 'dev.jasper.app.workspace.DockChromeTest' --tests 'dev.jasper.app.workspace.DockDragTest' --tests 'dev.jasper.app.workspace.ToolStripe*' --tests 'dev.jasper.app.workspace.ToolWindow*' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.workspace.DefaultDockPixelsTest' --tests 'dev.jasper.app.testsupport.MockCropsTest' --tests 'dev.jasper.app.architecture.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS. `ThemeMigrationCoverageTest` names `MockCrops.java`, `MockCropsTest.java` and `DefaultDockPixelsTest.java`
if their rows are missing; add them (Step 6) and re-run. Then run Step 1's pixel check: it prints `PIXELS-UNCHANGED`
(the gallery is not re-rendered here: run the two gallery commands of Step 1 first).

- [ ] **Step 6: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
T = "jasper-app/src/test/java/dev/jasper/app/"
rows = [
    f"{T}testsupport/MockCrops.java,app.testsupport,MockCrops,nonvisual-helper,none,none,{T}testsupport/MockCropsTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 1; renders and mock crops for the dock comparisons",
    f"{T}testsupport/MockCropsTest.java,app.testsupport,MockCropsTest,nonvisual-helper,none,none,{T}testsupport/MockCropsTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 1; the comparison helpers",
    f"{T}workspace/DefaultDockPixelsTest.java,app.workspace,DefaultDockPixelsTest,nonvisual-helper,none,none,{T}workspace/DefaultDockPixelsTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 1; the default look's dock pixel base"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: the window's kit for the dock, and DockChrome

ToolWindowArea, ToolWindowHeader and DockDrag read the kit their window
was built with instead of the installed one (spec 6.6), and take the
flush-frame flag the classic dock will use. WorkspaceChrome.of reads the
dock's two points into one value, DockChrome. MockCrops and the default
look's dock render are the plan's comparison and pixel base.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 2: Horizontal drop geometry and rule-wide dividers

Spec 10.7: "`DockDrag` and the drop resolution gain horizontal group rectangles, as `Rectangle` math; the six slots are
unchanged". A group gains an axis, and the geometry's divider width becomes a metric, so flush frames can lay out 1 px
rules. The dock package stays JDK-only.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/DockDropResolver.java:17-34,63-84`, `DockGeometry.java:23-45`,
  `docs/design/theme-engine/migration.csv`
- Create: `jasper-app/src/test/java/dev/jasper/app/dock/HorizontalDropTest.java`

**Interfaces:**
- Consumes: nothing from other tasks.
- Produces: `DockDropResolver.Axis { VERTICAL, HORIZONTAL }`; `DockDropResolver.Group(Slot, Rectangle, List<Rectangle>, Axis)`
  with the three-argument form meaning `VERTICAL`; `DockGeometry.Metrics.at(double scale, int divider)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/HorizontalDropTest.java`:

```java
package dev.jasper.app.dock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.jasper.app.dock.DockDropResolver.Axis;
import dev.jasper.app.dock.DockDropResolver.Group;
import dev.jasper.app.dock.DockDropResolver.Target;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Spec 10.7: the bottom strip's groups lie across, and flush frames are divided by 1 px rules. */
class HorizontalDropTest {
    private static final Rectangle WORKSPACE = new Rectangle(28, 30, 1000, 600);
    /** The bottom strip under the workspace: Bottom Left's three labels, then Bottom Right's empty half. */
    private static final List<Group> STRIP = List.of(
        new Group(Slot.BOTTOM_LEFT, new Rectangle(0, 631, 528, 28), List.of(new Rectangle(28, 632, 80, 27),
            new Rectangle(109, 632, 70, 27), new Rectangle(180, 632, 90, 27)), Axis.HORIZONTAL),
        new Group(Slot.BOTTOM_RIGHT, new Rectangle(528, 631, 528, 28), List.of(), Axis.HORIZONTAL));

    @Test void aHorizontalGroupCountsTheLabelsLeftOfTheCursor() {
        assertThat(DockDropResolver.resolve(new Point(30, 640), STRIP, WORKSPACE)).hasValue(Target.stripe(Slot.BOTTOM_LEFT, 0));
        assertThat(DockDropResolver.resolve(new Point(150, 640), STRIP, WORKSPACE)).as("past the first two labels' middles, before the third's")
            .hasValue(Target.stripe(Slot.BOTTOM_LEFT, 2));
        assertThat(DockDropResolver.resolve(new Point(300, 640), STRIP, WORKSPACE)).hasValue(Target.stripe(Slot.BOTTOM_LEFT, 3));
        assertThat(DockDropResolver.resolve(new Point(900, 640), STRIP, WORKSPACE)).as("the empty right half")
            .hasValue(Target.stripe(Slot.BOTTOM_RIGHT, 0));
    }

    @Test void aVerticalGroupStillCountsByHeightAndIsTheDefault() {
        var group = new Group(Slot.LEFT_TOP, new Rectangle(0, 30, 28, 300), List.of(new Rectangle(0, 34, 27, 60), new Rectangle(0, 94, 27, 60)));
        assertThat(group.axis()).isEqualTo(Axis.VERTICAL);
        assertThat(DockDropResolver.resolve(new Point(10, 100), List.of(group), WORKSPACE)).hasValue(Target.stripe(Slot.LEFT_TOP, 1));
        assertThat(DockDropResolver.resolve(new Point(130, 10), List.of(group), WORKSPACE)).isEmpty();
    }

    @Test void rulesAreOnePixelDividersThatSnapAndScaleAsGapsDo() {
        var state = new DockState();
        state.add("left", Slot.LEFT_TOP); state.show("left");
        state.add("lower", Slot.LEFT_BOTTOM); state.show("lower");
        state.add("right", Slot.RIGHT_TOP); state.show("right");
        var layout = DockGeometry.compute(new Rectangle(0, 0, 1000, 763), state, DockGeometry.Metrics.at(1, 1));
        assertThat(layout.divider(DockGeometry.Handle.LEFT_WIDTH).orElseThrow().bounds()).isEqualTo(new Rectangle(260, 0, 1, 763));
        assertThat(layout.divider(DockGeometry.Handle.LEFT_RATIO).orElseThrow().bounds()).isEqualTo(new Rectangle(0, 381, 260, 1));
        assertThat(layout.slot(Slot.LEFT_BOTTOM).orElseThrow()).isEqualTo(new Rectangle(0, 382, 260, 381));
        assertThat(layout.terminal()).isEqualTo(new Rectangle(261, 0, 1000 - 260 - 260 - 2, 763));
        assertThat(DockGeometry.Metrics.at(2, 1).scaled(1)).isEqualTo(2);
        assertThat(DockGeometry.Metrics.at(1)).isEqualTo(DockGeometry.Metrics.at(1, 6));
        assertThatIllegalArgumentException().isThrownBy(() -> DockGeometry.Metrics.at(1, -1));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.HorizontalDropTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `Axis` and the two-argument `Metrics.at`.

- [ ] **Step 3: Write the axis and the divider width**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 2 step 3: horizontal groups and rule-wide dividers."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
D = "jasper-app/src/main/java/dev/jasper/app/dock/"
edit(D + "DockDropResolver.java", [
    ('''    /**
     * One stripe group as a drag sees it.
     *
     * @param slot the slot the group shows
     * @param bounds the part of the stripe that drops into this group
     * @param buttons the group's buttons top to bottom, without the dragged window's own
     */
    public record Group(Slot slot, Rectangle bounds, List<Rectangle> buttons) {
        public Group {
            Objects.requireNonNull(slot, "slot");
            bounds = new Rectangle(bounds);
            buttons = buttons.stream().map(Rectangle::new).toList();
        }
''',
     '''    /** How a group's buttons line up: down a side stripe, or across the bottom strip (spec 10.7). */
    public enum Axis { VERTICAL, HORIZONTAL }

    /**
     * One stripe group as a drag sees it.
     *
     * @param slot the slot the group shows
     * @param bounds the part of the stripe that drops into this group
     * @param buttons the group's buttons in order along its axis, without the dragged window's own
     * @param axis whether the buttons run down (a side stripe) or across (the bottom strip)
     */
    public record Group(Slot slot, Rectangle bounds, List<Rectangle> buttons, Axis axis) {
        public Group {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(axis, "axis");
            bounds = new Rectangle(bounds);
            buttons = buttons.stream().map(Rectangle::new).toList();
        }

        /** A group down a side stripe. */
        public Group(Slot slot, Rectangle bounds, List<Rectangle> buttons) { this(slot, bounds, buttons, Axis.VERTICAL); }
''', 1),
    ('''     * Over a stripe group: that group, at the number of its buttons whose middle is above the cursor; the stripes win''',
     '''     * Over a stripe group: that group, at the number of its buttons whose middle is above the cursor (left of it, across
     * the bottom strip); the stripes win''', 1),
    ('''            for (Rectangle button : group.buttons) if (button.y + button.height / 2.0 < cursor.y) index++;''',
     '''            for (Rectangle button : group.buttons)
                if (group.axis == Axis.VERTICAL ? button.y + button.height / 2.0 < cursor.y : button.x + button.width / 2.0 < cursor.x) index++;''', 1)])
edit(D + "DockGeometry.java", [
    ('''        public Metrics {
            if (!(scale > 0)) throw new IllegalArgumentException("The scale must be positive: " + scale);
        }

        /** Jasper's metrics at a UI scale: 6px dividers, 120px tool windows, a 200 x 120 px terminal column. */
        public static Metrics at(double scale) { return new Metrics(scale, 6, 120, 200, 120); }''',
     '''        public Metrics {
            if (!(scale > 0)) throw new IllegalArgumentException("The scale must be positive: " + scale);
            if (divider < 0) throw new IllegalArgumentException("The divider cannot be negative: " + divider);
        }

        /** Jasper's metrics at a UI scale: 6px dividers, 120px tool windows, a 200 x 120 px terminal column. */
        public static Metrics at(double scale) { return at(scale, 6); }

        /** The same with {@code divider}-pixel dividers: flush frames' 1 px rules (spec 10.7). */
        public static Metrics at(double scale, int divider) { return new Metrics(scale, divider, 120, 200, 120); }''', 1)])
PY
```

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*' --tests 'dev.jasper.app.workspace.DockDragTest' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS (`HorizontalDropTest` 3 tests; the existing dock tests unchanged; the dock package still imports only
`Rectangle` and `Point` from `java.awt`), except `ThemeMigrationCoverageTest`, which names `HorizontalDropTest.java`
(its `java.awt` imports match `CANDIDATE`) until Step 5 adds its row; re-run it after Step 5's script.

- [ ] **Step 5: Add the ledger row and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
T = "jasper-app/src/test/java/dev/jasper/app/dock/"
row = f"{T}HorizontalDropTest.java,app.dock,HorizontalDropTest,nonvisual-helper,none,none,{T}HorizontalDropTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 2; horizontal groups and rule-wide dividers"
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src/main/java/dev/jasper/app/dock jasper-app/src/test/java/dev/jasper/app/dock/HorizontalDropTest.java docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: horizontal drop groups and rule-wide dividers

A stripe group has an axis: the bottom strip's groups count the labels left
of the cursor (spec 10.7). The geometry's divider width is a metric, so flush
frames lay out 1 px rules; the default stays 6 px.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 3: One stripe contract and the window's stripe set

The window, its contributions and the dock drag use the stripes through one abstract `DockStripe`, and the window gets
them from `DockChrome.stripes(kit)` as a `StripeSet`. `ToolStripe`'s code does not change apart from its superclass,
the moved callback fields and a seventh `Button` component (the digit of Task 7), so the default look's rails paint
the same pixels. Until Task 6, `DockChrome.stripes` always gives the rails.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/DockStripe.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/StripeSet.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/StripeSetTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java:53-73,182,248`, `DockChrome.java`,
  `WindowContent.java:93,272-282,908-909,928-937,1315-1316,1450-1451`, `WindowContributions.java:79,87,329-357,377,439,450`,
  `DockDrag.java:61-63,72,92-96,207-295,326-345`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java:51`, `WindowDragTest.java:73`,
  `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `DockChrome`, `ToolWindowArea`; Task 2's `DockDropResolver.Group.axis()`.
- Produces: `abstract class DockStripe extends JPanel` with the fields `onToggle`, `onMove`, `onHide`, `dragSource` and
  the package-private abstract methods `side()`, `render(List<ToolStripe.Button>, List<Action>)`,
  `updateBadge(String, Optional<PanelBadge>)`, `refreshActions()`, `empty()`, `emptyWithout(String)`, `buttons()`,
  `setRailWidth(int)`, `beginDrag(String id, String label)`, `showGap(Slot, int)`, `endDrag()`, `dragging()`,
  `gapBounds()`, `dropGroups()`, `refreshTheme()`, and `int thickness()`; `sealed interface StripeSet` with `left()`,
  `right()`, `Optional<DockStripe> bottom()`, `DockStripe of(Slot)`, `List<DockStripe> all()` and the record
  `IconRails(ToolStripe left, ToolStripe right)`; `ToolStripe.Button(id, title, icon, slot, selected, badge, OptionalInt
  number)` with the five- and six-argument forms; `DockChrome.stripes(Kit)`; `WindowContent.stripes()`,
  `bottomStrip()`, `leftStripe()` and `rightStripe()` as `DockStripe`; `DockDrag(JComponent, DockState,
  ToolWindowArea, StripeSet)` and its field `Function<String, String> labels`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/StripeSetTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.ui.kit.Kit;
import java.util.ArrayDeque;
import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a window docks through one stripe set; the default look's set is today's two rails. */
@ExtendWith(EdtTestExtension.class)
class StripeSetTest {
    @Test void theRailsShowABottomSlotOnItsSidesRailAndHaveNoStrip() {
        ThemeTestSupport.install(true);
        var rails = new StripeSet.IconRails();
        assertThat(rails.of(Slot.LEFT_TOP)).isSameAs(rails.left());
        assertThat(rails.of(Slot.LEFT_BOTTOM)).isSameAs(rails.left());
        assertThat(rails.of(Slot.BOTTOM_LEFT)).isSameAs(rails.left());
        assertThat(rails.of(Slot.RIGHT_TOP)).isSameAs(rails.right());
        assertThat(rails.of(Slot.BOTTOM_RIGHT)).isSameAs(rails.right());
        assertThat(rails.bottom()).isEmpty();
        assertThat(rails.all()).containsExactly(rails.left(), rails.right());
        assertThat(rails.left().thickness()).isEqualTo(rails.left().getPreferredSize().width);
        assertThat(DockChrome.STANDARD.stripes(Kit.DEFAULT)).isInstanceOf(StripeSet.IconRails.class);
    }

    @Test void aButtonCarriesTheDigitOfItsToggleAndKeepsItThroughABadge() {
        ThemeTestSupport.install(true);
        var button = new ToolStripe.Button("p", "SFTP", null, Slot.LEFT_TOP, true, Optional.empty(), OptionalInt.of(2));
        assertThat(button.number()).hasValue(2);
        assertThat(new ToolStripe.Button("p", "SFTP", null, Slot.LEFT_TOP, true).number()).isEmpty();
        var rail = new ToolStripe(dev.jasper.app.dock.Side.LEFT);
        rail.render(java.util.List.of(button), java.util.List.of());
        rail.updateBadge("p", Optional.of(new dev.jasper.app.contributions.PanelBadge(1, dev.jasper.app.contributions.PanelBadge.Tone.ACCENT, "1 unread")));
        assertThat(rail.buttons()).hasSize(1);
    }

    @Test void aWindowDocksThroughItsStripeSet() {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()));
        try {
            assertThat(owner.stripes()).isInstanceOf(StripeSet.IconRails.class);
            assertThat(owner.leftStripe()).isSameAs(owner.stripes().left());
            assertThat(owner.rightStripe()).isSameAs(owner.stripes().right());
            assertThat(owner.bottomStrip()).isEmpty();
        } finally { owner.close(); }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.StripeSetTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `StripeSet`, `thickness()`, `stripes()`,
`bottomStrip()` and the seven-argument `ToolStripe.Button`.

- [ ] **Step 3: Write `DockStripe` and `StripeSet`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/DockStripe.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.awt.LayoutManager;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JPanel;

/**
 * One edge of a window's tool window stripes, as the window, its contributions and a dock drag use it: an icon rail
 * ({@link ToolStripe}), a labelled side stripe or the bottom strip (spec 10.7). Each shows the windows whose slot it
 * owns, toggles a window on click, offers its menu on right-click, tells a drag where its groups are and opens a gap
 * where a dragged window would drop. EDT only.
 */
abstract class DockStripe extends JPanel {
    /** Toggles the window with this id. */
    Consumer<String> onToggle = id -> { };
    /** Moves the window to a slot, from its menu. */
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    /** Hides the window, from its menu. */
    Consumer<String> onHide = id -> { };
    /** Hears of each window button a render creates, so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };

    DockStripe(LayoutManager layout) { super(layout); }

    /** The window edge the stripe stands on: left, right or, for the bottom strip, bottom. */
    abstract Side side();

    /** Shows the windows whose slot this stripe owns, in the order given, and the rail actions where it shows them. */
    abstract void render(List<ToolStripe.Button> toolWindows, List<Action> contributed);

    /** Changes a window's badge in place; a window this stripe does not show is ignored. */
    abstract void updateBadge(String id, Optional<PanelBadge> badge);

    /** Renames each action's button after its action's title. */
    abstract void refreshActions();

    /** True when the stripe shows neither tool windows nor actions. */
    abstract boolean empty();

    /** True when the window's button, if it has one here, is all this stripe shows. */
    abstract boolean emptyWithout(String id);

    /** Every button, in the stripe's order. */
    abstract List<AbstractButton> buttons();

    /** Sets {@code window.rail_width} in logical pixels; the caller validates the range. */
    abstract void setRailWidth(int width);

    /** Shows every group for a drag of {@code id}, whose stripe label is {@code label}, and lifts its button out. */
    abstract void beginDrag(String id, String label);

    /** Opens the gap at {@code index} in the slot's group when this stripe shows the slot, and closes it otherwise. */
    abstract void showGap(Slot slot, int index);

    /** Back to the normal stripe after a drag. */
    abstract void endDrag();

    abstract boolean dragging();

    /** The gap the last layout opened, in this stripe's coordinates. */
    abstract Optional<Rectangle> gapBounds();

    /** The stripe's groups as a drag resolves them, in this stripe's coordinates. */
    abstract List<DockDropResolver.Group> dropGroups();

    /** Repaints in the installed theme's colours. */
    abstract void refreshTheme();

    /** How deep the stripe stands from its window edge: a side stripe's width, the bottom strip's height. */
    int thickness() { return side() == Side.BOTTOM ? getPreferredSize().height : getPreferredSize().width; }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StripeSet.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * A window's tool window stripes (spec 10.7): the default look's two icon rails, or the classic look's two labelled
 * stripes and the bottom strip. {@link DockChrome#stripes} builds one per window.
 */
sealed interface StripeSet permits StripeSet.IconRails {
    /** The stripe on the window's left edge. */
    DockStripe left();

    /** The stripe on the window's right edge. */
    DockStripe right();

    /** The bottom strip, when the set has one. */
    Optional<DockStripe> bottom();

    /** The stripe that shows a window docked in {@code slot}. */
    DockStripe of(Slot slot);

    /** Every stripe: left, right, then the bottom strip. */
    default List<DockStripe> all() {
        var all = new ArrayList<DockStripe>(List.of(left(), right()));
        bottom().ifPresent(all::add);
        return List.copyOf(all);
    }

    /**
     * The default look's rails: a bottom slot's window shows in its side's foot group.
     *
     * @param left the left rail
     * @param right the right rail
     */
    record IconRails(ToolStripe left, ToolStripe right) implements StripeSet {
        /** Two new rails. */
        IconRails() { this(new ToolStripe(Side.LEFT), new ToolStripe(Side.RIGHT)); }

        @Override public Optional<DockStripe> bottom() { return Optional.empty(); }

        @Override public DockStripe of(Slot slot) { return slot.stripe() == Side.LEFT ? left : right; }
    }
}
```

Add `stripes` to `DockChrome.java`:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/DockChrome.java"); text = path.read_text(encoding="utf-8")
old = '''    static final DockChrome STANDARD = new DockChrome(false, false);
}'''
new = '''    static final DockChrome STANDARD = new DockChrome(false, false);

    /** A new set of stripes for one window over its kit: today's icon rails. */
    StripeSet stripes(dev.jasper.ui.kit.Kit kit) { return new StripeSet.IconRails(); }
}'''
if text.count(old) != 1: raise SystemExit("DockChrome: STANDARD moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

- [ ] **Step 4: Move `ToolStripe`, the window, its contributions and the drag onto the contract**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 3 step 4: one stripe contract (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "ToolStripe.java", [
    ('''final class ToolStripe extends JPanel {
    /** One tool window's button, with its unread badge if it has one. */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected, Optional<PanelBadge> badge) {
        Button(String id, String title, Icon icon, Slot slot, boolean selected) {
            this(id, title, icon, slot, selected, Optional.empty());
        }
    }''',
     '''final class ToolStripe extends DockStripe {
    /**
     * One tool window's button, with its unread badge if it has one and the digit its toggle's shortcut ends in, if any;
     * a rail shows no digit, a labelled stripe starts its label with it.
     */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected, Optional<PanelBadge> badge, java.util.OptionalInt number) {
        Button(String id, String title, Icon icon, Slot slot, boolean selected) {
            this(id, title, icon, slot, selected, Optional.empty(), java.util.OptionalInt.empty());
        }

        Button(String id, String title, Icon icon, Slot slot, boolean selected, Optional<PanelBadge> badge) {
            this(id, title, icon, slot, selected, badge, java.util.OptionalInt.empty());
        }
    }''', 1),
    ('''    Consumer<String> onToggle = id -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    /** Hears of each window button a render creates, so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };
''', '', 1),
    ('''        Button updated = new Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge);''',
     '''        Button updated = new Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge, window.number());''', 1),
    ('''    private JSeparator dragSeparator() {''',
     '''    /** A rail's gap is one button's step whatever the label. */
    @Override void beginDrag(String id, String label) { beginDrag(id); }

    private JSeparator dragSeparator() {''', 1)])
edit(W + "WindowContent.java", [
    ('''    private final ToolStripe leftStripe = new ToolStripe(Side.LEFT), rightStripe = new ToolStripe(Side.RIGHT);''',
     '''    /** The window's stripes: icon rails, or labelled stripes and the bottom strip (spec 10.7). */
    private final StripeSet stripes;
    private final DockStripe leftStripe, rightStripe;''', 1),
    ('''        area = new ToolWindowArea(terminalColumn, dock, themes.kit(), look().dock().flushFrames());''',
     '''        stripes = look().dock().stripes(themes.kit());
        leftStripe = stripes.left();
        rightStripe = stripes.right();
        area = new ToolWindowArea(terminalColumn, dock, themes.kit(), look().dock().flushFrames());''', 1),
    ('''        leftStripe.setVisible(false);
        rightStripe.setVisible(false);

        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        dockDrag = new DockDrag(this, dock, area, leftStripe, rightStripe);
        dockDrag.railsEnabled = this::railsEnabled;
        leftStripe.dragSource = dockDrag::install;
        rightStripe.dragSource = dockDrag::install;''',
     '''        for (DockStripe stripe : stripes.all()) stripe.setVisible(false);

        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        stripes.bottom().ifPresent(strip -> body.add(strip, BorderLayout.SOUTH));
        dockDrag = new DockDrag(this, dock, area, stripes);
        dockDrag.railsEnabled = this::railsEnabled;
        for (DockStripe stripe : stripes.all()) stripe.dragSource = dockDrag::install;''', 1),
    ('''    ToolStripe leftStripe() { return leftStripe; }
    ToolStripe rightStripe() { return rightStripe; }''',
     '''    DockStripe leftStripe() { return leftStripe; }
    DockStripe rightStripe() { return rightStripe; }
    /** The bottom strip of labelled stripes; empty under the icon rails. */
    java.util.Optional<DockStripe> bottomStrip() { return stripes.bottom(); }
    /** The window's stripes. */
    StripeSet stripes() { return stripes; }''', 1),
    ('''        boolean left = railsEnabled() && !leftStripe.empty(), right = railsEnabled() && !rightStripe.empty();
        area.setStripesVisible(left, right);
        if (leftStripe.isVisible() != left || rightStripe.isVisible() != right) {
            leftStripe.setVisible(left);
            rightStripe.setVisible(right);''',
     '''        boolean left = railsEnabled() && !leftStripe.empty(), right = railsEnabled() && !rightStripe.empty();
        boolean bottom = stripes.bottom().map(strip -> railsEnabled() && !strip.empty()).orElse(false);
        area.setStripesVisible(left, right);
        boolean bottomChanged = stripes.bottom().map(strip -> strip.isVisible() != bottom).orElse(false);
        if (leftStripe.isVisible() != left || rightStripe.isVisible() != right || bottomChanged) {
            leftStripe.setVisible(left);
            rightStripe.setVisible(right);
            stripes.bottom().ifPresent(strip -> strip.setVisible(bottom));''', 1),
    ('''            leftStripe.refreshTheme();
            rightStripe.refreshTheme();''', '''            for (DockStripe stripe : stripes.all()) stripe.refreshTheme();''', 1),
    ('''        leftStripe.setRailWidth(width);
        rightStripe.setRailWidth(width);''', '''        for (DockStripe stripe : stripes.all()) stripe.setRailWidth(width);''', 1)])
edit(W + "WindowContributions.java", [
    ('''        for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe())) {
            stripe.onToggle = this::togglePanel;''',
     '''        for (DockStripe stripe : owner.stripes().all()) {
            stripe.onToggle = this::togglePanel;''', 1),
    ('''        owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;''',
     '''        owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;
        owner.dockDrag().labels = id -> panels.containsKey(id) ? panels.get(id).entry.title() : "";''', 1),
    ('''        List<ToolStripe.Button> left = new ArrayList<>(), right = new ArrayList<>();
        for (Slot slot : Slot.values())
            for (String id : dock.ids(slot)) {
                PanelInstance panel = panels.get(id);
                if (panel == null) continue;
                var button = new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id),
                    panel.entry.badge());
                (slot.stripe() == Side.LEFT ? left : right).add(button);
            }
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        owner.leftStripe().render(left, railActions);
        owner.rightStripe().render(right, List.of());''',
     '''        List<ToolStripe.Button> windows = new ArrayList<>();
        for (Slot slot : Slot.values())
            for (String id : dock.ids(slot)) {
                PanelInstance panel = panels.get(id);
                if (panel == null) continue;
                windows.add(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id),
                    panel.entry.badge()));
            }
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        // Every stripe keeps the windows of the slots it shows.
        owner.leftStripe().render(windows, railActions);
        owner.rightStripe().render(windows, List.of());
        owner.bottomStrip().ifPresent(strip -> strip.render(windows, List.of()));''', 1),
    ('''            owner.leftStripe().updateBadge(panel.entry.id(), panel.entry.badge());
            owner.rightStripe().updateBadge(panel.entry.id(), panel.entry.badge());''',
     '''            for (DockStripe stripe : owner.stripes().all()) stripe.updateBadge(panel.entry.id(), panel.entry.badge());''', 1),
    ('''                else for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe())) stripe.refreshActions();''',
     '''                else for (DockStripe stripe : owner.stripes().all()) stripe.refreshActions();''', 1),
    ('''        owner.dockDrag().icons = id -> null;''', '''        owner.dockDrag().icons = id -> null;
        owner.dockDrag().labels = id -> "";''', 1),
    ('''        for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe())) {
            stripe.onToggle = id -> { };''',
     '''        for (DockStripe stripe : owner.stripes().all()) {
            stripe.onToggle = id -> { };''', 1)])
edit(W + "DockDrag.java", [
    ('''    private final List<ToolStripe> stripes;
    /** The stripes this drag shows its groups on: the visible ones and, while the rails may show, the hidden ones. */
    private final List<ToolStripe> targets = new ArrayList<>();''',
     '''    /** The window's stripes, which say which stripe shows a slot. */
    private final StripeSet set;
    private final List<DockStripe> stripes;
    /** The stripes this drag shows its groups on: the visible ones and, while the rails may show, the hidden ones. */
    private final List<DockStripe> targets = new ArrayList<>();''', 1),
    ('''    BooleanSupplier railsEnabled = () -> false;''',
     '''    BooleanSupplier railsEnabled = () -> false;
    /** The dragged window's stripe label, which a labelled stripe's gap is as long as. */
    Function<String, String> labels = id -> "";''', 1),
    ('''    DockDrag(JComponent root, DockState state, ToolWindowArea area, ToolStripe left, ToolStripe right) {
        this.root = root;
        this.state = state;
        this.area = area;
        this.stripes = List.of(left, right);''',
     '''    DockDrag(JComponent root, DockState state, ToolWindowArea area, ToolStripe left, ToolStripe right) {
        this(root, state, area, new StripeSet.IconRails(left, right));
    }

    /**
     * @param root the window content that holds the stripes and the area; drag coordinates are in its space
     * @param state the window's dock state
     * @param area the tool window area, which shares its parent with every stripe: a hidden stripe stands over its edge
     * @param stripes the window's stripes
     */
    DockDrag(JComponent root, DockState state, ToolWindowArea area, StripeSet stripes) {
        this.root = root;
        this.state = state;
        this.area = area;
        this.set = stripes;
        this.stripes = stripes.all();''', 1),
    ('''        for (ToolStripe stripe : stripes) {
            boolean showsAfter = slot.stripe() == stripe.side() || !stripe.emptyWithout(dragging);
            int change = (showsAfter ? 1 : 0) - (stripe.isVisible() ? 1 : 0);
            if (change == 0) continue;
            int width = change * stripe.getPreferredSize().width;
            if (stripe.side() == Side.LEFT) bounds.x += width;
            bounds.width -= width;
        }''',
     '''        for (DockStripe stripe : stripes) {
            boolean showsAfter = set.of(slot) == stripe || !stripe.emptyWithout(dragging);
            int change = (showsAfter ? 1 : 0) - (stripe.isVisible() ? 1 : 0);
            if (change == 0) continue;
            int size = change * stripe.thickness();
            switch (stripe.side()) {
                case LEFT -> { bounds.x += size; bounds.width -= size; }
                case RIGHT -> bounds.width -= size;
                case BOTTOM -> bounds.height -= size;
            }
        }''', 1),
    ('''    private void standAtEdge(ToolStripe stripe) {
        int width = stripe.getPreferredSize().width;
        int x = stripe.side() == Side.LEFT ? area.getX() : area.getX() + area.getWidth() - width;
        Rectangle place = new Rectangle(x, area.getY(), width, area.getHeight());''',
     '''    private void standAtEdge(DockStripe stripe) {
        int thickness = stripe.thickness();
        Rectangle place = switch (stripe.side()) {
            case LEFT -> new Rectangle(area.getX(), area.getY(), thickness, area.getHeight());
            case RIGHT -> new Rectangle(area.getX() + area.getWidth() - thickness, area.getY(), thickness, area.getHeight());
            case BOTTOM -> new Rectangle(0, area.getY() + area.getHeight() - thickness,
                area.getParent() == null ? area.getWidth() : area.getParent().getWidth(), thickness);
        };''', 1),
    ('''    private DockDropResolver.Group inRoot(ToolStripe stripe, DockDropResolver.Group group) {''',
     '''    private DockDropResolver.Group inRoot(DockStripe stripe, DockDropResolver.Group group) {''', 1),
    ('''        return new DockDropResolver.Group(group.slot(), SwingUtilities.convertRectangle(stripe, group.bounds(), root), buttons);''',
     '''        return new DockDropResolver.Group(group.slot(), SwingUtilities.convertRectangle(stripe, group.bounds(), root), buttons, group.axis());''', 1),
    ('''            stripe.beginDrag(dragging);''', '''            stripe.beginDrag(dragging, labels.apply(dragging));''', 1),
    ('''for (ToolStripe stripe''', '''for (DockStripe stripe''', 7)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java", [
    ('''    private static List<String> tips(ToolStripe stripe) {''', '''    private static List<String> tips(DockStripe stripe) {''', 1)])
edit("jasper-app/src/test/java/dev/jasper/app/workspace/WindowDragTest.java", [
    ('''for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe()))''',
     '''for (DockStripe stripe : List.of(owner.leftStripe(), owner.rightStripe()))''', 1)])
PY
```

The last `for (ToolStripe stripe` replacement counts the seven loops left after the areaAfter edit (in `start`, the two
`targets` loops of `update`, its `showGap` loop, its placeholder loop and the two loops of `finish`); if the count differs,
`grep -n 'ToolStripe' jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java` must show only the convenience
constructor once all loops read `DockStripe`.

- [ ] **Step 5: Run the tests and the pixel check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest' :jasper-app:verifyApplicationArchitecture`
Expected: PASS (`StripeSetTest` 3 tests, every existing workspace test unchanged). Then run Task 1 Step 1's two gallery
commands, `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DefaultDockPixelsTest'` and the pixel check:
it prints `PIXELS-UNCHANGED`.

- [ ] **Step 6: Add the ledger rows and commit**

`DockStripe.java` and `StripeSet.java` import Swing; add their rows and `StripeSetTest`'s:

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"; T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
rows = [
    f"{M}DockStripe.java,app.workspace,DockStripe,standard-ui,3,3,{T}StripeSetTest.java,New in Enterprise plan 7 Task 3; the stripe contract the window and the dock drag share; no painting of its own",
    f"{M}StripeSet.java,app.workspace,StripeSet,standard-ui,3,3,{T}StripeSetTest.java,New in Enterprise plan 7 Task 3; a window's icon rails or labelled stripes",
    f"{T}StripeSetTest.java,app.workspace,StripeSetTest,nonvisual-helper,none,none,{T}StripeSetTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 3; the stripe set and its routing"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: one stripe contract and the window's stripe set

ToolStripe extends DockStripe, the contract WindowContent,
WindowContributions and DockDrag now use; a window takes its stripes from
DockChrome as a StripeSet (today's two rails), routes a slot to the stripe
that shows it, and lays out and drops onto a bottom strip when the set has
one. A stripe button carries the digit of its toggle for labelled stripes.
The default look's pixels are unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 4: The dock's colours, catalog values and declared pairs

Everything the classic dock paints comes from `ChromePainter`'s dock painters, each colour through a pair listed in
`ChromePainter.DOCK_PAIRS` (spec 7.3, 16.1.1); `PaintedContrastTest` paints them over every palette. The derived
surfaces lean toward the variant's text (Rulings 16, plan 2's and 5b's hand-off), so a dark theme's focused header is
lighter than its other headers. The classic catalog gains the stripe, pill and header values the mocks measure
(Rulings 5, 9, 15). No default-look painter reads any of it.

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/theme/SurfaceContrast.java:109-122`, `DerivedPalette.java:84-89,98`,
  `jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv` (one row)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/metrics/DesignMetrics.java` (the `Role` enum's end, 4a's
  `STRIPE_LABEL_*` values, `classic()`), `jasper-ui/src/test/resources/dev/jasper/ui/metrics/compact-roles.tsv`,
  `classic-roles.tsv`, `jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java` (`SURFACE_HEADER_ACTIVE`'s inputs)
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/ChromePainter.java` (dock painters before `clip`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/theme/HeaderSideTest.java`, `jasper-ui/src/test/java/dev/jasper/ui/metrics/DockRolesTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java` (one test and two helpers)

**Interfaces:**
- Consumes: plan 2's `DerivedColor`, `DerivedPalette.of`, `PaintPair`, `SurfaceContrast.Guard`; plan 4a's roles.
- Produces: `SurfaceContrast.beyond(Color surface, Color from, double minimum, Color text)`; the roles
  `STRIPE_PILL_HEIGHT(14)`, `STRIPE_PILL_WIDTH(16)`, `STRIPE_PILL_PADDING(5)`, `STRIPE_PILL_GAP(5)`,
  `TOOL_HEADER_ACTION(16)`, `HEADER_STRIP_TITLE_GAP(8)` (a header strip's title to its next item) and
  `HEADER_STRIP_DOT_GAP(6)` (its connected dot to the detail) (same in both catalogs), `STRIPE_LABEL_PADDING` 9 and `STRIPE_LABEL_GAP` 0 in both, and the
  classic `TOOL_HEADER_LEADING_PADDING` 8, `TOOL_HEADER_TRAILING_PADDING` 7, `PANEL_HEADER_ACTION` 16,
  `PANEL_HEADER_ACTION_VERTICAL` 0, `PANEL_HEADER_TRAILING_PADDING` 7; `ChromePainter.DOCK_PAIRS` and the instance
  methods `dockRule(theme)`, `stripeSurface(theme)`, `stripeTile(theme, open, hover)`, `stripeLabel(theme, open)`,
  `stripDivider(theme)`, `countPill(String)` (its `Dimension`), `countPill(Graphics, x, y, text, theme, error)`,
  `headerSurface(theme, focused)`, `headerTitle(theme, focused)`, `headerDetail(theme, focused, count)`,
  `headerStrip(Graphics, width, height, theme, focused)`, `headerDot(Graphics, x, y, size, theme, focused)`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/theme/HeaderSideTest.java`:

```java
package dev.jasper.ui.theme;

import static dev.jasper.ui.theme.DerivedColor.SURFACE_HEADER;
import static dev.jasper.ui.theme.DerivedColor.SURFACE_HEADER_ACTIVE;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import org.junit.jupiter.api.Test;

/** Enterprise plan 7 (plan 2's hand-off): derived surfaces lean toward the variant's text. */
class HeaderSideTest {
    @Test void theActiveHeaderLeansTowardTheTextInEveryShippedVariant() {
        for (var theme : KitFixtures.themes()) {
            if (theme.derivedOverrides().containsKey(SURFACE_HEADER_ACTIVE)) continue;
            var derived = DerivedPalette.of(theme);
            Color header = derived.color(SURFACE_HEADER), active = derived.color(SURFACE_HEADER_ACTIVE);
            boolean lighter = ColorMath.lightness(theme.semantic().color(SemanticColor.TEXT_PRIMARY)) > ColorMath.lightness(header);
            double delta = ColorMath.lightness(active) - ColorMath.lightness(header);
            assertThat(lighter ? delta : -delta).as(theme.name() + (theme.dark() ? " Dark" : " Light")).isGreaterThanOrEqualTo(2 - 1e-9);
        }
    }

    @Test void slateCobaltDarksFocusedHeaderIsLighterThanItsOthers() {
        var derived = DerivedPalette.of(KitFixtures.dark());
        assertThat(ColorMath.hex(derived.color(SURFACE_HEADER_ACTIVE))).isEqualToIgnoringCase("#2d3546");
        assertThat(ColorMath.lightness(derived.color(SURFACE_HEADER_ACTIVE))).isGreaterThan(ColorMath.lightness(derived.color(SURFACE_HEADER)));
        assertThat(ColorMath.hex(DerivedPalette.of(KitFixtures.light()).color(SURFACE_HEADER_ACTIVE))).as("Light keeps its value")
            .isEqualToIgnoringCase("#d3dcea");
    }

    @Test void beyondKeepsAColourOnTheTextSideOrMovesItThere() {
        Color dark = new Color(0x23, 0x26, 0x2C), light = new Color(0xF2, 0xF4, 0xF7);
        Color already = new Color(0x40, 0x44, 0x4C);
        assertThat(SurfaceContrast.beyond(already, dark, 3, light)).as("far enough on the light side").isEqualTo(already);
        Color under = new Color(0x1C, 0x1F, 0x24);
        Color moved = SurfaceContrast.beyond(under, dark, 3, light);
        assertThat(ColorMath.lightness(moved) - ColorMath.lightness(dark)).as("moved past to the light side").isGreaterThanOrEqualTo(3);
        Color near = new Color(250, 250, 250);
        Color fallback = SurfaceContrast.beyond(near, near, 3, Color.WHITE);
        assertThat(ColorMath.lightness(near) - ColorMath.lightness(fallback)).as("no room on the text side: the other side").isGreaterThanOrEqualTo(3);
    }
}
```

Append to `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java`, after
`theOffSwitchWhichNoGuardRepairsIsPaintedAsAuthoredOverEveryPalette`, by this script:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 4 step 1: the classic dock's declared pairs over every palette."""
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java"); text = path.read_text(encoding="utf-8")
anchor = '''    private static boolean searchHint(GeneratedTheme theme, Color expected) {'''
if text.count(anchor) != 1: raise SystemExit("PaintedContrastTest: searchHint moved")
block = '''    /** Spec 10.7 and 16.1.2: the classic dock chrome paints each declared pair as its guard makes it, over every palette. */
    @Test void theClassicDockPaintsEachPairAsItsGuardMakesItOverEveryPalette() {
        var painter = new ChromePainter(Kit.CLASSIC);
        var pairs = ChromePainter.DOCK_PAIRS;
        var declared = List.of(
            new Declared("dock rule", pairs.get(0), (theme, expected) -> painter.dockRule(theme).equals(expected)
                && strip(painter, theme, false).getRGB(5, 24) == expected.getRGB() && strip(painter, theme, true).getRGB(5, 24) == expected.getRGB()),
            new Declared("stripe label", pairs.get(1), (theme, expected) -> painter.stripeLabel(theme, false).equals(expected)),
            new Declared("open stripe label", pairs.get(2), (theme, expected) -> painter.stripeLabel(theme, true).equals(expected)),
            new Declared("accent count pill", pairs.get(3), (theme, expected) -> pill(painter, theme, expected, false)),
            new Declared("error count pill", pairs.get(4), (theme, expected) -> pill(painter, theme, expected, true)),
            new Declared("header title", pairs.get(5), (theme, expected) -> painter.headerTitle(theme, false).equals(expected)),
            new Declared("focused header title", pairs.get(6), (theme, expected) -> painter.headerTitle(theme, true).equals(expected)),
            new Declared("header detail", pairs.get(7), (theme, expected) -> painter.headerDetail(theme, false, false).equals(expected)),
            new Declared("focused header detail", pairs.get(8), (theme, expected) -> painter.headerDetail(theme, true, false).equals(expected)),
            new Declared("header count", pairs.get(9), (theme, expected) -> painter.headerDetail(theme, false, true).equals(expected)),
            new Declared("focused header count", pairs.get(10), (theme, expected) -> painter.headerDetail(theme, true, true).equals(expected)),
            new Declared("header connected dot", pairs.get(11), (theme, expected) -> dot(painter, theme, false).getRGB(9, 9) == expected.getRGB()),
            new Declared("focused header connected dot", pairs.get(12), (theme, expected) -> dot(painter, theme, true).getRGB(9, 9) == expected.getRGB()));
        assertThat(declared).extracting(Declared::pair).as("every declared dock pair is sampled").containsExactlyElementsOf(pairs);
        for (var theme : palettes()) {
            assertThat(strip(painter, theme, true).getRGB(5, 5)).as(name(theme) + " focused strip").isEqualTo(painter.headerSurface(theme, true).getRGB());
            for (var each : declared) {
                String where = name(theme) + ", " + each.name();
                Color expected = SurfaceContrast.painted(theme, each.pair());
                assertThat(each.pair().guard().holds(expected, each.pair().against().color(theme))).as(where + " meets its guard").isTrue();
                assertThat(each.painter().paints(theme, expected)).as(where + " is painted " + ColorMath.hex(expected)).isTrue();
            }
        }
    }

    /** A 40 x 25 header strip: 24 px of surface over its 1 px rule. */
    private static BufferedImage strip(ChromePainter painter, GeneratedTheme theme, boolean focused) {
        var image = new BufferedImage(40, 25, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { painter.headerStrip(graphics, 40, 25, theme, focused); } finally { graphics.dispose(); }
        return image;
    }

    /** A count pill at four times its size, so its text has fully covered pixels. */
    private static boolean pill(ChromePainter painter, GeneratedTheme theme, Color expected, boolean error) {
        var image = new BufferedImage(160, 80, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { graphics.scale(4, 4); painter.countPill(graphics, 2, 2, "8", theme, error); } finally { graphics.dispose(); }
        return anywhere(image, expected);
    }

    /** Whether any pixel of {@code image} is exactly {@code color}; {@code hasColor} scans only the right half, the off switch's. */
    private static boolean anywhere(BufferedImage image, Color color) {
        for (int y = 0; y < image.getHeight(); y++)
            for (int x = 0; x < image.getWidth(); x++) if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    /** A 14 px connected dot at (2, 2). */
    private static BufferedImage dot(ChromePainter painter, GeneratedTheme theme, boolean focused) {
        var image = new BufferedImage(20, 20, BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { painter.headerDot(graphics, 2, 2, 14, theme, focused); } finally { graphics.dispose(); }
        return image;
    }

'''
path.write_text(text.replace(anchor, block + anchor), encoding="utf-8")
PY
```

Here the rule, the pills and the dots are sampled from painted pixels; the eight text pairs (the stripe labels and the
header titles, details and counts) are checked through the painter's accessors only, because the components that paint
them do not exist yet. Their painted pixels are sampled over every palette where those components arrive: Task 5's
`LabelledStripeTest.eachLabelPaintsItsDeclaredPairOverEveryPalette` (pairs 1 and 2) and Task 9's
`HeaderStripTest.aFlushHeaderPaintsItsTextInItsDeclaredPairsOverEveryPalette` (pairs 5 to 10), each at 4x inside the
label's bounds, so a look-and-feel or composition override of a label's foreground fails there.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.theme.HeaderSideTest' :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: FAIL; jasper-ui reports "cannot find symbol: method beyond" and jasper-app "cannot find symbol" for
`DOCK_PAIRS`, `dockRule`, `stripeLabel`, `countPill`, `headerTitle`, `headerDetail`, `headerStrip`, `headerDot`,
`headerSurface`.

- [ ] **Step 3: Write the side rule and the catalog values**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 4 step 3: surfaces lean toward the text; the dock's catalog values."""
import pathlib, re
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
U = "jasper-ui/src/main/java/dev/jasper/ui/"
edit(U + "theme/SurfaceContrast.java", [
    ('''    /** White or black, whichever contrasts more with {@code fill}. */''',
     '''    /**
     * {@code surface} kept at least {@code minimum} CIE L* beyond {@code from} on the side of {@code text}'s lightness
     * (lighter when the text is lighter than {@code from}, darker otherwise): unchanged when it already is; otherwise
     * moved toward white or black in 1% steps just until it is, or toward the other end when that side has no room. The
     * derived surfaces use it, so a dark variant's header strips stand lighter than its window surface.
     */
    public static Color beyond(Color surface, Color from, double minimum, Color text) {
        double target = ColorMath.lightness(from);
        boolean lighter = ColorMath.lightness(text) > target;
        if ((lighter ? ColorMath.lightness(surface) - target : target - ColorMath.lightness(surface)) >= minimum) return surface;
        for (Color end : lighter ? java.util.List.of(Color.WHITE, Color.BLACK) : java.util.List.of(Color.BLACK, Color.WHITE)) {
            boolean up = end.equals(Color.WHITE);
            for (int step = 1; step <= 100; step++) {
                Color candidate = ColorMath.mix(end, surface, step / 100.0);
                if ((up ? ColorMath.lightness(candidate) - target : target - ColorMath.lightness(candidate)) >= minimum) return candidate;
            }
        }
        return lighter ? Color.WHITE : Color.BLACK;
    }

    /** White or black, whichever contrasts more with {@code fill}. */''', 1)])
edit(U + "theme/DerivedPalette.java", [
    ('''            case SURFACE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .05), in.apply(SURFACE_APP), 3);
            case SURFACE_HEADER_ACTIVE -> SurfaceContrast.apart(mix(in, ACCENT, SURFACE_APP, .10),
                in.apply(DerivedColor.SURFACE_HEADER), 2);
            case SURFACE_STRIPE -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_APP, .035), in.apply(SURFACE_APP), 1.5);
            case SURFACE_TABLE_HEADER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .06), in.apply(SURFACE_CARD), 3);
            case SURFACE_ZEBRA -> closer(SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_CARD, .025), in.apply(SURFACE_CARD), 1.5),''',
     '''            case SURFACE_HEADER -> SurfaceContrast.beyond(mix(in, TEXT_PRIMARY, SURFACE_APP, .05), in.apply(SURFACE_APP), 3, in.apply(TEXT_PRIMARY));
            case SURFACE_HEADER_ACTIVE -> SurfaceContrast.beyond(mix(in, ACCENT, SURFACE_APP, .10),
                in.apply(DerivedColor.SURFACE_HEADER), 2, in.apply(TEXT_PRIMARY));
            case SURFACE_STRIPE -> SurfaceContrast.beyond(mix(in, TEXT_PRIMARY, SURFACE_APP, .035), in.apply(SURFACE_APP), 1.5, in.apply(TEXT_PRIMARY));
            case SURFACE_TABLE_HEADER -> SurfaceContrast.beyond(mix(in, TEXT_PRIMARY, SURFACE_CARD, .06), in.apply(SURFACE_CARD), 3, in.apply(TEXT_PRIMARY));
            case SURFACE_ZEBRA -> closer(SurfaceContrast.beyond(mix(in, TEXT_PRIMARY, SURFACE_CARD, .025), in.apply(SURFACE_CARD), 1.5, in.apply(TEXT_PRIMARY)),''', 1),
    ('''            case SURFACE_FOOTER -> SurfaceContrast.apart(mix(in, TEXT_PRIMARY, SURFACE_DIALOG, .03), in.apply(SURFACE_DIALOG), 2);''',
     '''            case SURFACE_FOOTER -> SurfaceContrast.beyond(mix(in, TEXT_PRIMARY, SURFACE_DIALOG, .03), in.apply(SURFACE_DIALOG), 2, in.apply(TEXT_PRIMARY));''', 1)])
edit("jasper-ui/src/test/resources/dev/jasper/ui/theme/derived-roles.tsv", [
    ('''slate-cobalt\tdark\tsurface.header.active\t#232b3c\n''', '''slate-cobalt\tdark\tsurface.header.active\t#2d3546\n''', 1)])
M = U + "metrics/DesignMetrics.java"
path = pathlib.Path(M); text = path.read_text(encoding="utf-8")
start = text.index("enum Role")
last = re.search(r"(\w+\(\d+\));", text[start:])
if last is None: raise SystemExit("DesignMetrics: the Role enum's last constant was not found")
end = start + last.end()
if last.group(1) != "DIALOG_FOOTER_VERTICAL(8)":
    raise SystemExit("DesignMetrics: the enum no longer ends with plan 6b's DIALOG_FOOTER_VERTICAL(8); re-find its last role")
text = text[:end - 1] + ''',
        // Enterprise plan 7: the count pill, the native header's actions and the header strips' gaps (Workstation mock);
        // only the classic dock chrome reads them.
        STRIPE_PILL_HEIGHT(14), STRIPE_PILL_WIDTH(16), STRIPE_PILL_PADDING(5), STRIPE_PILL_GAP(5), TOOL_HEADER_ACTION(16),
        HEADER_STRIP_TITLE_GAP(8), HEADER_STRIP_DOT_GAP(6);''' + text[end:]
for old, new in [("STRIPE_LABEL_PADDING(8), STRIPE_LABEL_GAP(1)", "STRIPE_LABEL_PADDING(9), STRIPE_LABEL_GAP(0)"),
                 ("        int[] table = declared();",
                  '''        // Enterprise plan 7: the header strips' paddings and 16 px actions, 18 apart (Workstation mock).
        values.put(Role.TOOL_HEADER_LEADING_PADDING, 8);
        values.put(Role.TOOL_HEADER_TRAILING_PADDING, 7);
        values.put(Role.PANEL_HEADER_ACTION, 16);
        values.put(Role.PANEL_HEADER_ACTION_VERTICAL, 0);
        values.put(Role.PANEL_HEADER_TRAILING_PADDING, 7);
        int[] table = declared();''')]:
    if text.count(old) != 1: raise SystemExit(f"DesignMetrics: expected 1 of {old!r}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
ADDED = [("STRIPE_PILL_HEIGHT", 14), ("STRIPE_PILL_WIDTH", 16), ("STRIPE_PILL_PADDING", 5), ("STRIPE_PILL_GAP", 5), ("TOOL_HEADER_ACTION", 16),
         ("HEADER_STRIP_TITLE_GAP", 8), ("HEADER_STRIP_DOT_GAP", 6)]
CLASSIC = {"TOOL_HEADER_LEADING_PADDING": 8, "TOOL_HEADER_TRAILING_PADDING": 7, "PANEL_HEADER_ACTION": 16,
           "PANEL_HEADER_ACTION_VERTICAL": 0, "PANEL_HEADER_TRAILING_PADDING": 7}
for table, extra in (("compact-roles.tsv", {}), ("classic-roles.tsv", CLASSIC)):
    path = pathlib.Path("jasper-ui/src/test/resources/dev/jasper/ui/metrics") / table
    text = path.read_text(encoding="utf-8")
    for role, value in {"STRIPE_LABEL_PADDING": 9, "STRIPE_LABEL_GAP": 0, **extra}.items():
        text, count = re.subn(rf"(?m)^{role}\t\d+$", f"{role}\t{value}", text)
        if count != 1: raise SystemExit(f"{table}: expected one {role} row, found {count}")
    path.write_text(text + "".join(f"{role}\t{value}\n" for role, value in ADDED), encoding="utf-8")
# Plan 2's recipe-input pin: surface.header.active now also reads text.primary (the side rule's text).
path = pathlib.Path("jasper-ui/src/test/java/dev/jasper/ui/theme/DerivedColorTest.java"); text = path.read_text(encoding="utf-8")
old = "inputs.put(SURFACE_HEADER_ACTIVE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_APP, SURFACE_HEADER));"
if text.count(old) != 1: raise SystemExit("DerivedColorTest: SURFACE_HEADER_ACTIVE's inputs moved")
path.write_text(text.replace(old, "inputs.put(SURFACE_HEADER_ACTIVE, Set.of(SemanticColor.ACCENT, SemanticColor.SURFACE_APP, SURFACE_HEADER,\n            SemanticColor.TEXT_PRIMARY));"), encoding="utf-8")
PY
```

The enum ends with plan 6b's `DIALOG_FOOTER_VERTICAL(8)` (the script stops if it does not). `NewRolesTest` finds
`ADDED` as one run anywhere in the enum (`indexOfSubList`, since the 6a/8a/4c merges), so it is not edited; the seven
roles get their own check in the style of plan 6a's `ChromeRolesTest`, which follows the chrome roles rather than
closing the enum, so plan 9a's roles (appended in parallel) merge after them without editing it. Create
`jasper-ui/src/test/java/dev/jasper/ui/metrics/DockRolesTest.java`:

```java
package dev.jasper.ui.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** Enterprise plan 7: the dock chrome's roles, measured on the Workstation mock, the same in both catalogs. */
class DockRolesTest {
    static final Map<String, Integer> ROLES = new LinkedHashMap<>();
    static {
        Object[][] rows = {{"STRIPE_PILL_HEIGHT", 14}, {"STRIPE_PILL_WIDTH", 16}, {"STRIPE_PILL_PADDING", 5}, {"STRIPE_PILL_GAP", 5},
            {"TOOL_HEADER_ACTION", 16}, {"HEADER_STRIP_TITLE_GAP", 8}, {"HEADER_STRIP_DOT_GAP", 6}};
        for (Object[] row : rows) ROLES.put((String) row[0], (Integer) row[1]);
    }

    @Test void theDockRolesFollowTheChromeRolesInOrderWithTheirMeasuredValuesInBothCatalogs() {
        var names = Arrays.stream(DesignMetrics.Role.values()).map(Enum::name).toList();
        int first = names.indexOf("STRIPE_PILL_HEIGHT");
        assertThat(first).as("appended after plan 6b's chrome roles").isGreaterThan(names.indexOf("DIALOG_FOOTER_VERTICAL"));
        assertThat(names.subList(first, first + ROLES.size())).containsExactlyElementsOf(ROLES.keySet());
        ROLES.forEach((name, value) -> {
            var role = DesignMetrics.Role.valueOf(name);
            assertThat(DesignMetrics.DEFAULT.value(role)).as("default " + name).isEqualTo(value);
            assertThat(DesignMetrics.CLASSIC.value(role)).as("classic " + name).isEqualTo(value);
        });
    }
}
```

The script also adds `text.primary` to `DerivedColorTest`'s pinned inputs of `surface.header.active` (plan 2's
recipe-input check). No plan 4b or 4c test pins the classic panel header's action size or paddings (checked at
`759b2b7d9`: jasper-ui's tests pass with the new catalog values).

- [ ] **Step 4: Write the dock painters**

In `jasper-ui/src/main/java/dev/jasper/ui/swing/ChromePainter.java`, insert before `    public String clip(String text,
FontMetrics metrics, int width) {`:

```java
    private static final PaintPair RULE = new PaintPair(SemanticColor.BORDER_WINDOW, SemanticColor.SURFACE_APP, SurfaceContrast.Guard.STRUCTURAL);
    private static final PaintPair LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_STRIPE, SurfaceContrast.Guard.TEXT);
    private static final PaintPair OPEN_LABEL = new PaintPair(SemanticColor.TEXT_PRIMARY, SemanticColor.SURFACE_CONTROL_SELECTED, SurfaceContrast.Guard.TEXT);
    private static final PaintPair ACCENT_PILL = new PaintPair(SemanticColor.ACCENT, DerivedColor.INFO_GROUND, SurfaceContrast.Guard.TEXT);
    private static final PaintPair ERROR_PILL = new PaintPair(SemanticColor.STATUS_DANGER, DerivedColor.DANGER_GROUND, SurfaceContrast.Guard.TEXT);
    private static final PaintPair TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT);
    private static final PaintPair FOCUSED_TITLE = new PaintPair(SemanticColor.TEXT_PRIMARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    private static final PaintPair DETAIL = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT);
    private static final PaintPair FOCUSED_DETAIL = new PaintPair(SemanticColor.TEXT_STRONG_SECONDARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    private static final PaintPair COUNT = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.TEXT);
    private static final PaintPair FOCUSED_COUNT = new PaintPair(SemanticColor.TEXT_SECONDARY, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.TEXT);
    private static final PaintPair DOT = new PaintPair(SemanticColor.STATUS_SUCCESS, DerivedColor.SURFACE_HEADER, SurfaceContrast.Guard.INDICATOR);
    private static final PaintPair FOCUSED_DOT = new PaintPair(SemanticColor.STATUS_SUCCESS, DerivedColor.SURFACE_HEADER_ACTIVE, SurfaceContrast.Guard.INDICATOR);

    /**
     * The pairs the classic dock chrome paints (spec 10.7 and 7.3), each through its guard, in this order: the 1 px rules
     * on the window surface; a label on the stripe and on an open label's tile; the accent and error count pills' text
     * on their grounds; and the header strips' title, detail, count and connected dot on the inactive, then the focused,
     * header surface.
     */
    public static final java.util.List<PaintPair> DOCK_PAIRS = java.util.List.of(RULE, LABEL, OPEN_LABEL, ACCENT_PILL, ERROR_PILL,
        TITLE, FOCUSED_TITLE, DETAIL, FOCUSED_DETAIL, COUNT, FOCUSED_COUNT, DOT, FOCUSED_DOT);

    /** The dock's 1 px rule: between frames, along a stripe's inner edge, under a header strip. */
    public Color dockRule(GeneratedTheme theme) { return SurfaceContrast.painted(theme, RULE); }

    /** A stripe's and the bottom strip's surface. */
    public Color stripeSurface(GeneratedTheme theme) { return DerivedColor.SURFACE_STRIPE.color(theme); }

    /** A label's tile: an open window's, a hovered closed one's, or null for none. */
    public Color stripeTile(GeneratedTheme theme, boolean open, boolean hover) {
        if (open) return theme.semantic().color(SemanticColor.SURFACE_CONTROL_SELECTED);
        return hover ? theme.semantic().color(SemanticColor.SURFACE_CONTROL_HOVER) : null;
    }

    /** A label's text on the stripe, or on an open label's tile. */
    public Color stripeLabel(GeneratedTheme theme, boolean open) { return SurfaceContrast.painted(theme, open ? OPEN_LABEL : LABEL); }

    /** The bottom strip's divider after each label. */
    public Color stripDivider(GeneratedTheme theme) { return DerivedColor.BORDER_DIVIDER.color(theme); }

    /** The size of a count pill showing {@code text}: the pill's height, at least its width or the text and its padding. */
    public Dimension countPill(String text) {
        int width = Math.max(metric(DesignMetrics.Role.STRIPE_PILL_WIDTH),
            measureText(font(Typography.Role.CHIP), text) + 2 * metric(DesignMetrics.Role.STRIPE_PILL_PADDING));
        return new Dimension(width, metric(DesignMetrics.Role.STRIPE_PILL_HEIGHT));
    }

    /**
     * A count pill with its top-left corner at ({@code x}, {@code y}) in the graphics' current transform: the tone's
     * ground (info for the accent tone, danger for the error tone), fully rounded, the text centred in the tone's colour.
     */
    public void countPill(Graphics graphics, int x, int y, String text, GeneratedTheme theme, boolean error) {
        Dimension size = countPill(text);
        var g = (Graphics2D) graphics.create();
        try {
            ThemePainting.antialias(g);
            g.setColor((error ? DerivedColor.DANGER_GROUND : DerivedColor.INFO_GROUND).color(theme));
            g.fill(new java.awt.geom.RoundRectangle2D.Double(x, y, size.width, size.height, size.height, size.height));
            Font font = font(Typography.Role.CHIP);
            g.setFont(font);
            g.setColor(SurfaceContrast.painted(theme, error ? ERROR_PILL : ACCENT_PILL));
            FontMetrics metrics = g.getFontMetrics(font);
            g.drawString(text, x + (size.width - measureText(font, text)) / 2f,
                y + (size.height - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent());
        } finally { g.dispose(); }
    }

    /** A header strip's surface: the focused window's, or the others'. */
    public Color headerSurface(GeneratedTheme theme, boolean focused) {
        return (focused ? DerivedColor.SURFACE_HEADER_ACTIVE : DerivedColor.SURFACE_HEADER).color(theme);
    }

    /** A header strip's title. */
    public Color headerTitle(GeneratedTheme theme, boolean focused) { return SurfaceContrast.painted(theme, focused ? FOCUSED_TITLE : TITLE); }

    /** A header strip's detail: muted, a count (a parenthesised number) more so. */
    public Color headerDetail(GeneratedTheme theme, boolean focused, boolean count) {
        return SurfaceContrast.painted(theme, count ? (focused ? FOCUSED_COUNT : COUNT) : (focused ? FOCUSED_DETAIL : DETAIL));
    }

    /** A header strip over {@code width} x {@code height}: its surface, with the 1 px rule along its bottom edge. */
    public void headerStrip(Graphics graphics, int width, int height, GeneratedTheme theme, boolean focused) {
        Graphics g = graphics.create();
        try {
            int rule = metric(DesignMetrics.Role.DOCK_RULE);
            g.setColor(headerSurface(theme, focused));
            g.fillRect(0, 0, width, Math.max(0, height - rule));
            g.setColor(dockRule(theme));
            g.fillRect(0, Math.max(0, height - rule), width, rule);
        } finally { g.dispose(); }
    }

    /** A header strip's connected dot: the success status held to the indicator floor on the strip's own surface. */
    public void headerDot(Graphics graphics, int x, int y, int size, GeneratedTheme theme, boolean focused) {
        var g = (Graphics2D) graphics.create();
        try {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setColor(SurfaceContrast.painted(theme, focused ? FOCUSED_DOT : DOT));
            g.fillOval(x, y, size, size);
        } finally { g.dispose(); }
    }

```

`ChromePainter` imports `java.awt.*`, `javax.swing.*`, `dev.jasper.ui.metrics.*` and `dev.jasper.ui.theme.*`, which
cover `Dimension`, `Font`, `FontMetrics`, `RenderingHints`, `Typography`, `PaintPair`, `DerivedColor` and
`SurfaceContrast`; `java.util.List` is written in full because `java.awt.List` is also imported.

- [ ] **Step 5: Run the tests and the pixel check**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests 'dev.jasper.app.appearance.PaintedContrastTest' --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest' --continue`
Expected: PASS: `HeaderSideTest` 3 tests; `DerivedRolesTableTest` with the one changed row; `DesignMetricsTest`,
`ClassicCatalogTest`, `NewRolesTest` (unchanged) and `DockRolesTest` with the new rows; `DerivedColorTest` with its
edited inputs; `PaintedContrastTest` with the new test over every palette.
If the derived-roles table test reports any row other than Slate Cobalt Dark's `surface.header.active`, stop: Rulings 16
said only that one moves; recompute with the rule and record the difference before going on. Then the pixel check
(Task 1 Step 1's gallery commands, `DefaultDockPixelsTest`, the `diff`/`cmp` line) prints `PIXELS-UNCHANGED`.

- [ ] **Step 6: Commit**

```bash
git add jasper-ui/src jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java
git commit -F - <<'EOF'
Dock chrome: the dock's colours, catalog values and declared pairs

ChromePainter paints the classic dock chrome (rules, stripe surface, label
tiles and text, count pills, header strips, details and connected dots),
each colour through a pair in DOCK_PAIRS that PaintedContrastTest paints
over every palette. Derived surfaces lean toward the variant's text, so a
dark theme's focused header is lighter than the others (Slate Cobalt Dark's
active header moves to #2d3546). The classic catalog gains the stripe, pill
and header values the Workstation mock measures. The default look's pixels
are unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 5: The labelled side stripe, compared with the mocks

Spec 10.7: "rotated text labels on `surface.stripe`, an open panel's label on `surface.control.selected` … Left-stripe
labels read bottom to top, right-stripe labels top to bottom". `StripeLabels` holds what the side stripes and the bottom
strip share; `LabelledStripe` lays out and paints a side stripe. The headless render under the Enterprise kit and theme
(6a's fixtures until plan 5b) is compared with the left and right stripes of the Workstation, Dark Mode and Unlock Vault
mocks.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/StripeLabels.java`, `jasper-app/src/main/java/dev/jasper/app/workspace/LabelledStripe.java`
- Modify: plan 6a's `jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java` (`theme`, `install`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/LabelledStripeTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`
- Modify: `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 3's `DockStripe`, `ToolStripe.Button`; Task 4's `ChromePainter` dock painters and roles; plan 6a's
  `EnterpriseLook` (`kit()`, `theme()`, `restoreDefault()`) and `MockProfile.fonts`; Task 1's `MockCrops`.
- Produces: `StripeLabels(Kit)` with `static String text(ToolStripe.Button)`, `font()`, `length(String,
  Optional<PanelBadge>)`, `thickness(int railWidth, Component)`, `paint(Graphics2D, String, Optional<PanelBadge>, int
  across, GeneratedTheme, boolean open)`, `painter()`, `styles()` and the nested `StripeLabels.Label` (a `JToggleButton`
  with `window`, `action`, `id()`, `text()`, `badge()`); `LabelledStripe(Side, Kit)`; the test support
  `EnterpriseLook.theme(boolean dark)` and `install(boolean dark)` beside plan 6a's `kit()`; `DockMockTest` with its
  `Variant` record (`LIGHT`, `DARK`, `VAULT`), which later tasks extend above its closing marker comment.

- [ ] **Step 1: Write the failing tests**

Add the theme's variants and their installation to plan 6a's
`jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java` (until plan 5b: 4a's Enterprise fixture kit in
the platform faces and 6a's fixture theme, `enterprise-fixture.json`; Execution notes R4):

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/testsupport/EnterpriseLook.java"); text = path.read_text(encoding="utf-8")
old = "    /** Installs Slate Cobalt under the default kit again, with today's fonts, for the tests that follow. EDT. */"
new = """    /** The fixture theme's Light or Dark variant. */
    public static dev.jasper.ui.theme.GeneratedTheme theme(boolean dark) { return theme().generated(dark); }

    /** Installs the theme's variant under the kit for a component render, without a controller. EDT. */
    public static void install(boolean dark) { dev.jasper.ui.swing.FlatLafInstaller.install(theme(dark), kit()); }

""" + old
if text.count(old) != 1: raise SystemExit("EnterpriseLook: restoreDefault moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/LabelledStripeTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.DerivedColor;
import java.awt.Color;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.JMenu;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a labelled side stripe's labels, order, numbers, reading direction, drag view and actions. */
@ExtendWith(EdtTestExtension.class)
class LabelledStripeTest {
    static ToolStripe.Button window(String id, String title, Slot slot, int number) {
        return new ToolStripe.Button(id, title, null, slot, true, Optional.empty(), number == 0 ? OptionalInt.empty() : OptionalInt.of(number));
    }

    private static LabelledStripe stripe(Side side) {
        EnterpriseLook.install(false);
        return new LabelledStripe(side, EnterpriseLook.kit());
    }

    private static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    @Test void itShowsItsSidesWindowsByGroupWithTheirNumbersEdgeToEdge() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("servers", "File Servers", Slot.LEFT_BOTTOM, 2), window("sftp", "SFTP", Slot.LEFT_TOP, 1),
                window("sessions", "Sessions", Slot.RIGHT_TOP, 0), window("log", "Event Log", Slot.BOTTOM_LEFT, 0)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 300, 1);
            assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP", "2: File Servers");
            Rectangle first = stripe.buttons().get(0).getBounds(), second = stripe.buttons().get(1).getBounds();
            assertThat(first.y).as("RAIL_GAP below the top").isEqualTo(metric(DesignMetrics.Role.RAIL_GAP));
            assertThat(second.y).as("edge to edge").isEqualTo(first.y + first.height);
            assertThat(first.width).as("the fill, without the rule").isEqualTo(stripe.getWidth() - metric(DesignMetrics.Role.DOCK_RULE));
            assertThat(stripe.buttons().get(0).isSelected()).isTrue();
            assertThat(stripe.getPreferredSize().width).as("the 28 px floor").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(28));
            assertThat(stripe.empty()).isFalse();
            assertThat(stripe.emptyWithout("sftp")).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aClickTogglesAndARightClickOffersMoveTo() throws Exception {
        try {
            var stripe = stripe(Side.RIGHT);
            var toggled = new ArrayList<String>();
            stripe.onToggle = toggled::add;
            stripe.render(List.of(window("sessions", "Sessions", Slot.RIGHT_TOP, 0)), List.of());
            stripe.buttons().getFirst().doClick(0);
            assertThat(toggled).containsExactly("sessions");
            var menu = stripe.buttons().getFirst().getComponentPopupMenu();
            assertThat(((JMenu) menu.getComponent(0)).getText()).isEqualTo("Move to");
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Spec 16.1.2: a closed and an open label paint their text in their declared pairs ({@code ChromePainter.DOCK_PAIRS}
     * 1 and 2), sampled from the 4x render inside the label's tile, over every palette.
     */
    @Test void eachLabelPaintsItsDeclaredPairOverEveryPalette() throws Exception {
        var palettes = new ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
        palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
        try {
            for (var theme : palettes) for (boolean open : List.of(false, true)) {
                dev.jasper.ui.swing.FlatLafInstaller.install(theme, EnterpriseLook.kit());
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit());
                stripe.render(List.of(new ToolStripe.Button("sftp", "SFTP", null, Slot.LEFT_TOP, open, Optional.empty(), OptionalInt.empty())), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 120, 4);
                Color expected = dev.jasper.ui.theme.SurfaceContrast.painted(theme, ChromePainter.DOCK_PAIRS.get(open ? 2 : 1));
                assertThat(inks(image, stripe.buttons().getFirst().getBounds(), 4, expected))
                    .as(theme.name() + (theme.dark() ? " Dark" : " Light") + (open ? ", open" : ", closed") + " label "
                        + dev.jasper.ui.theme.ColorMath.hex(expected)).isTrue();
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Whether {@code image}, rendered at {@code scale}, holds a pixel of exactly {@code color} inside {@code bounds}. */
    static boolean inks(BufferedImage image, Rectangle bounds, int scale, Color color) {
        for (int y = bounds.y * scale; y < Math.min(image.getHeight(), (bounds.y + bounds.height) * scale); y++)
            for (int x = bounds.x * scale; x < Math.min(image.getWidth(), (bounds.x + bounds.width) * scale); x++)
                if (image.getRGB(x, y) == color.getRGB()) return true;
        return false;
    }

    @Test void theLeftStripeReadsBottomToTopAndTheRightTopToBottom() throws Exception {
        try {
            var badge = Optional.of(new PanelBadge(3, PanelBadge.Tone.ACCENT, "3 unread"));
            Color ground = DerivedColor.INFO_GROUND.color(EnterpriseLook.theme(false));
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                stripe.render(List.of(new ToolStripe.Button("log", "Event Log", null, side == Side.LEFT ? Slot.LEFT_TOP : Slot.RIGHT_TOP,
                    false, badge, OptionalInt.empty())), List.of());
                var image = MockCrops.render(stripe, stripe.getPreferredSize().width, 200, 1);
                Rectangle label = stripe.buttons().getFirst().getBounds();
                int rows = 0, sum = 0;
                for (int y = label.y; y < label.y + label.height; y++)
                    for (int x = label.x; x < label.x + label.width; x++) if (image.getRGB(x, y) == ground.getRGB()) { rows++; sum += y; }
                assertThat(rows).as(side + ": the pill is painted").isPositive();
                int middle = label.y + label.height / 2;
                if (side == Side.LEFT) assertThat(sum / rows).as("the pill ends the label, read bottom to top").isLessThan(middle);
                else assertThat(sum / rows).as("the pill ends the label, read top to bottom").isGreaterThan(middle);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a 24 pt body thickens the stripe to its line; labels never overlap and keep their pills inside. */
    @Test void aLargeFontThickensTheStripeAndNoLabelOverlaps() throws Exception {
        try {
            EnterpriseLook.install(false);
            Kit kit = EnterpriseLook.kit();
            var big = new Kit(kit.id(), kit.metrics(), kit.type().withBody(24), kit.faces(), kit.controls(), kit.colors(), kit.uiKeys());
            var stripe = new LabelledStripe(Side.LEFT, big);
            var badge = Optional.of(new PanelBadge(12, PanelBadge.Tone.ERROR, "12 errors"));
            stripe.render(List.of(new ToolStripe.Button("a", "A rather long tool window title", null, Slot.LEFT_TOP, true, badge, OptionalInt.of(1)),
                window("b", "Second", Slot.LEFT_TOP, 0), window("c", "Third", Slot.LEFT_BOTTOM, 0)), List.of());
            int thickness = stripe.getPreferredSize().width;
            assertThat(thickness).as("thicker than the 28 px floor").isGreaterThan(com.formdev.flatlaf.util.UIScale.scale(28));
            MockCrops.render(stripe, thickness, 1200, 1);
            var bounds = stripe.buttons().stream().map(AbstractButton::getBounds).toList();
            for (int i = 1; i < bounds.size(); i++) assertThat(bounds.get(i).y).isGreaterThanOrEqualTo(bounds.get(i - 1).y + bounds.get(i - 1).height);
            var painter = new ChromePainter(big);
            var pill = painter.countPill("12");
            assertThat(bounds.getFirst().height).as("the label holds its text and its pill")
                .isGreaterThanOrEqualTo(pill.width + metric(DesignMetrics.Role.STRIPE_PILL_GAP));
            assertThat(bounds.getFirst().width).as("the pill fits across").isGreaterThanOrEqualTo(pill.height);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aDragShowsBothGroupsLiftsTheLabelAndOpensAGap() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1), window("servers", "File Servers", Slot.LEFT_TOP, 2)), List.of());
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            stripe.beginDrag("servers", "2: File Servers");
            var groups = stripe.dropGroups();
            assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
            assertThat(groups.get(0).buttons()).as("the lifted label is out").hasSize(1);
            assertThat(groups.get(1).buttons()).as("an empty group is drop space").isEmpty();
            assertThat(groups.get(1).bounds().height).isPositive();
            stripe.showGap(Slot.LEFT_BOTTOM, 0);
            Rectangle gap = stripe.gapBounds().orElseThrow();
            assertThat(gap.y).isGreaterThanOrEqualTo(groups.get(1).bounds().y);
            stripe.endDrag();
            assertThat(stripe.dragging()).isFalse();
            assertThat(stripe.gapBounds()).isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void railActionsFollowTheSecondGroupAndNeverStaySelected() throws Exception {
        try {
            var stripe = stripe(Side.LEFT);
            var ran = new ArrayList<String>();
            var action = new AbstractAction("About Sample") { @Override public void actionPerformed(ActionEvent event) { ran.add("about"); } };
            stripe.render(List.of(window("sftp", "SFTP", Slot.LEFT_TOP, 1)), List.of(action));
            MockCrops.render(stripe, stripe.getPreferredSize().width, 400, 1);
            var buttons = stripe.buttons();
            assertThat(buttons).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP", "About Sample");
            assertThat(buttons.get(1).getY()).isGreaterThanOrEqualTo(buttons.get(0).getY() + buttons.get(0).getHeight()
                + metric(DesignMetrics.Role.RAIL_SECTION_GAP));
            buttons.get(1).doClick(0);
            assertThat(ran).containsExactly("about");
            assertThat(buttons.get(1).isSelected()).isFalse();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theStripePaintsItsSurfaceAndItsRuleTowardTheWorkspace() throws Exception {
        try {
            var theme = EnterpriseLook.theme(false);
            var painter = new ChromePainter(EnterpriseLook.kit());
            for (Side side : List.of(Side.LEFT, Side.RIGHT)) {
                var stripe = stripe(side);
                stripe.render(List.of(), List.of());
                int width = stripe.getPreferredSize().width;
                BufferedImage image = MockCrops.render(stripe, width, 120, 1);
                int inner = side == Side.LEFT ? width - 1 : 0, outer = side == Side.LEFT ? 2 : width - 3;
                assertThat(new Color(image.getRGB(inner, 60))).as(side + " rule").isEqualTo(painter.dockRule(theme));
                assertThat(new Color(image.getRGB(outer, 60))).as(side + " surface").isEqualTo(painter.stripeSurface(theme));
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.app.testsupport.MockProfile;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock chrome: each surface plan 7 builds, rendered at 2x under the Enterprise kit and theme (plan
 * 6a's {@link EnterpriseLook} fixtures until plan 5b ships the plugin, Plex and the theme), against its crop of the
 * Workstation (Light), Dark Mode and Unlock Vault mocks. Probes that follow the UI face's glyphs are printed until then
 * ({@link MockProfile#fonts}). Renders, crops and their differences are written under
 * {@code jasper-app/build/dock-renders/}. Mock data (titles, numbers, counts) are fixtures.
 */
@ExtendWith(EdtTestExtension.class)
class DockMockTest {
    /**
     * One mock: its file, the theme variant the render uses, where the Workstation's geometry starts in it, and the scrim
     * over it.
     *
     * @param name a short name for the written files
     * @param mock the mock's path under the reference folder
     * @param dark whether the render uses Enterprise Dark
     * @param offset where the Workstation's (0, 0) lies in the mock, at 2x
     * @param scrim the black over the window, 0 for none
     */
    record Variant(String name, String mock, boolean dark, int offset, double scrim) {
        /** The mock's rectangle at the Workstation's 2x coordinates. */
        BufferedImage crop(int x, int y, int width, int height) { return MockCrops.crop(MockCrops.mock(mock), x + offset, y + offset, width, height); }

        /** A render as this mock shows it: under its scrim. */
        BufferedImage seen(BufferedImage render) { return scrim > 0 ? MockCrops.scrim(render, scrim) : render; }

        /** The open tile is Enterprise Light's #D3D9E1 (6a's fixture theme) against the mock's #CFD4DC (Rulings 6); Dark's is exact. */
        int tileTolerance() { return dark ? 3 : 10; }

        /**
         * How near a fill or rule must be: 3, or 5 under the Unlock Vault mock's scrim, which has a cool cast (white reads
         * #C9CACC, 2 to 3 bluer than black at 21 % gives; Execution notes R6).
         */
        int fillTolerance() { return scrim > 0 ? 5 : 3; }
    }

    static final Variant LIGHT = new Variant("light", MockCrops.CONNECTED, false, 0, 0);
    static final Variant DARK = new Variant("dark", MockCrops.DARK, true, 0, 0);
    static final Variant VAULT = new Variant("vault", MockCrops.VAULT, false, MockCrops.VAULT_OFFSET, MockCrops.VAULT_SCRIM);
    static final List<Variant> VARIANTS = List.of(LIGHT, DARK, VAULT);
    /** Agreement floors (spec 16.1.4): the share of pixels within 24 per channel of the mock. */
    static final double STRIPE_FLOOR = 0.92;

    @Test void theLeftStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.LEFT, EnterpriseLook.kit());
                stripe.render(List.of(LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 1),
                    LabelledStripeTest.window("servers", "File Servers", Slot.LEFT_BOTTOM, 2)), List.of());
                int thickness = stripe.getPreferredSize().width;
                assertThat(thickness).as("the 28 px floor of window.rail_width").isEqualTo(28);
                compareStripe(variant, "left-stripe", variant.seen(MockCrops.render(stripe, thickness, 165, 2)),
                    variant.crop(2, 180, 44, 330), thickness, Side.LEFT, List.of(29, 109, 151, 301));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    @Test void theRightStripeMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var stripe = new LabelledStripe(Side.RIGHT, EnterpriseLook.kit());
                stripe.render(List.of(LabelledStripeTest.window("sessions", "Sessions", Slot.RIGHT_TOP, 0),
                    LabelledStripeTest.window("tunnels", "Tunnels", Slot.RIGHT_BOTTOM, 0)), List.of());
                int thickness = stripe.getPreferredSize().width;
                compareStripe(variant, "right-stripe", variant.seen(MockCrops.render(stripe, thickness, 135, 2)),
                    variant.crop(2838, 180, 44, 270), thickness, Side.RIGHT, List.of(29, 121, 164, 247));
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /**
     * A side stripe against its mock crop (the mock's 22 px fill at 2x): the stripe and open-tile fills, one ink run per
     * label with the mock's ink, the first label's leading ink within 2 px, and (printed until plan 5b) the other run
     * edges and the share of agreeing pixels over the render's middle 44 columns of its fill, where the centred labels
     * line up with the mock's.
     */
    static void compareStripe(Variant variant, String name, BufferedImage render, BufferedImage mock, int thickness, Side side,
                              List<Integer> expectedRuns) {
        int rule = 2, fill = 2 * thickness - rule, fillStart = side == Side.LEFT ? 0 : rule;
        var window = MockCrops.crop(render, fillStart + (fill - mock.getWidth()) / 2, 0, mock.getWidth(), render.getHeight());
        MockCrops.write(variant.name() + "-" + name, MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight()), mock);
        String where = variant.name() + " " + name;
        Color stripeFill = MockCrops.at(mock, 2, 4), tile = MockCrops.at(mock, 2, 40);
        assertThat(MockCrops.near(MockCrops.at(window, 2, 4), stripeFill, variant.fillTolerance())).as(where + ": the stripe's surface").isTrue();
        assertThat(MockCrops.near(MockCrops.at(window, 2, 40), tile, variant.tileTolerance())).as(where + ": the open tile").isTrue();
        var theirs = MockCrops.runs(mock, true, 120, 20, stripeFill, tile);
        var mine = MockCrops.runs(window, true, 120, 20, stripeFill, tile);
        assertThat(theirs).as(where + ": the mock's labels").hasSize(expectedRuns.size() / 2);
        for (int i = 0; i < theirs.size(); i++) {
            assertThat(theirs.get(i).start()).as(where + " mock run " + i).isBetween(expectedRuns.get(2 * i) - 2, expectedRuns.get(2 * i) + 2);
            assertThat(theirs.get(i).end()).as(where + " mock run " + i).isBetween(expectedRuns.get(2 * i + 1) - 2, expectedRuns.get(2 * i + 1) + 2);
        }
        assertThat(mine).as(where + ": one ink run per label").hasSameSizeAs(theirs);
        // The first label's leading ink lies RAIL_GAP and STRIPE_LABEL_PADDING below the stripe's top whatever the face;
        // every later edge follows the labels' advance widths, and the agreeing share their glyphs: printed until plan
        // 5b's Plex (Execution notes R5), then asserted within 2 px and against STRIPE_FLOOR.
        assertThat(mine.getFirst().start()).as(where + " label 0 starts")
            .isBetween(theirs.getFirst().start() - 2, theirs.getFirst().start() + 2);
        for (int i = 0; i < theirs.size(); i++) {
            if (i > 0) MockProfile.fonts(where + " label " + i + " start", mine.get(i).start(), theirs.get(i).start(), 2);
            MockProfile.fonts(where + " label " + i + " end", mine.get(i).end(), theirs.get(i).end(), 2);
            assertThat(MockCrops.near(mine.get(i).ink(), theirs.get(i).ink(), 24)).as(where + " label " + i + " ink").isTrue();
        }
        MockCrops.fontsFloor(where + ": agreeing pixels",
            MockCrops.agreement(MockCrops.crop(window, 0, 0, mock.getWidth(), mock.getHeight()), mock, 24), STRIPE_FLOOR);
    }

    // Later tasks add their surfaces above this line.
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.LabelledStripeTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class LabelledStripe".

- [ ] **Step 3: Write `StripeLabels` and `LabelledStripe`**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StripeLabels.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.metrics.Typography;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.swing.NativeStyles;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiConsumer;
import javax.swing.Action;
import javax.swing.DefaultButtonModel;
import javax.swing.JToggleButton;

/**
 * What a labelled side stripe and the bottom strip share (spec 10.7): a label's text ("N: " and the title when the
 * window's toggle ends in a digit), its length along its stripe, every stripe's thickness, and the painting of a label
 * in text space: its padding, its text, then a count pill for a badge. EDT only.
 */
final class StripeLabels {
    private final ChromePainter painter;
    private final NativeStyles styles;

    StripeLabels(Kit kit) {
        Objects.requireNonNull(kit, "kit");
        this.painter = new ChromePainter(kit);
        this.styles = new NativeStyles(kit);
    }

    ChromePainter painter() { return painter; }

    NativeStyles styles() { return styles; }

    /** A window's label: "N: " and its title when its toggle ends in a digit, else its title. */
    static String text(ToolStripe.Button window) {
        return window.number().isPresent() ? window.number().getAsInt() + ": " + window.title() : window.title();
    }

    /** The labels' face: the kit's label role. */
    Font font() { return styles.font(Typography.Role.LABEL); }

    /** A label's length along its stripe: the padding at both ends and the text, then a badge's gap and pill. */
    int length(String text, Optional<PanelBadge> badge) {
        int length = 2 * styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING) + painter.measureText(font(), text);
        if (badge.isPresent())
            length += styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP) + painter.countPill(Integer.toString(badge.get().count())).width;
        return length;
    }

    /**
     * How deep every stripe stands, its 1 px rule included (Rulings 4): {@code window.rail_width}, or the label's line and
     * the rail's two edges when a large interface font needs more.
     */
    int thickness(int railWidth, Component reference) {
        int line = reference.getFontMetrics(font()).getHeight();
        return Math.max(UIScale.scale(railWidth),
            line + 2 * styles.metric(DesignMetrics.Role.RAIL_EDGE) + styles.metric(DesignMetrics.Role.DOCK_RULE));
    }

    /** Paints a label in text space, from its start, centred across {@code across}: its text, then its badge's pill. */
    void paint(Graphics2D g, String text, Optional<PanelBadge> badge, int across, GeneratedTheme theme, boolean open) {
        painter.prepareText(g);
        Font font = font();
        g.setFont(font);
        FontMetrics metrics = g.getFontMetrics(font);
        int x = styles.metric(DesignMetrics.Role.STRIPE_LABEL_PADDING);
        g.setColor(painter.stripeLabel(theme, open));
        g.drawString(text, (float) x, (across - metrics.getAscent() - metrics.getDescent()) / 2f + metrics.getAscent());
        if (badge.isEmpty()) return;
        String count = Integer.toString(badge.get().count());
        Dimension pill = painter.countPill(count);
        painter.countPill(g, x + painter.measureText(font, text) + styles.metric(DesignMetrics.Role.STRIPE_PILL_GAP),
            (across - pill.height) / 2, count, theme, badge.get().tone() == PanelBadge.Tone.ERROR);
    }

    /**
     * One label: a window's toggle or a rail action's button, which its stripe lays out and paints. An action's label has a
     * plain button model, so it never stays selected.
     */
    static final class Label extends JToggleButton {
        /** The window it shows, or null for an action; replaced when the window's badge changes. */
        ToolStripe.Button window;
        /** The action it runs, or null for a window. */
        final Action action;
        private final BiConsumer<Label, Graphics> painter;

        Label(ToolStripe.Button window, BiConsumer<Label, Graphics> painter) {
            this.window = Objects.requireNonNull(window, "window");
            this.action = null;
            this.painter = Objects.requireNonNull(painter, "painter");
            setUp();
        }

        Label(Action action, BiConsumer<Label, Graphics> painter) {
            this.window = null;
            this.action = Objects.requireNonNull(action, "action");
            this.painter = Objects.requireNonNull(painter, "painter");
            setModel(new DefaultButtonModel());
            setHideActionText(true);
            setAction(action);
            setUp();
        }

        private void setUp() {
            setOpaque(false);
            setBorderPainted(false);
            setContentAreaFilled(false);
            setFocusPainted(false);
            setFocusable(false);
            setRolloverEnabled(true);
        }

        /** The window's id, or null for an action. */
        String id() { return window == null ? null : window.id(); }

        /** What it reads. */
        String text() { return window != null ? StripeLabels.text(window) : String.valueOf(action.getValue(Action.NAME)); }

        /** The window's badge; an action has none. */
        Optional<PanelBadge> badge() { return window == null ? Optional.empty() : window.badge(); }

        @Override protected void paintComponent(Graphics graphics) { painter.accept(this, graphics); }
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/LabelledStripe.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.Action;

/**
 * One of the classic look's labelled tool window stripes (spec 10.7): each window as a label, "N: " first when its
 * toggle ends in a digit, read bottom to top on the left stripe and top to bottom on the right, on {@code surface.stripe},
 * an open window's label on {@code surface.control.selected}; the top group's labels from {@code RAIL_GAP} below the top,
 * then the second group's, then on the left the plugins' rail actions; a 1 px rule along the edge toward the workspace.
 * A badge ends its label with a count pill. A click toggles the window and a right-click opens its {@link ToolWindowMenu}.
 * During a dock drag both groups show (an empty group as the dragged label's drop space), the dragged label is lifted
 * out and its length stays at the end of its group, and a gap as long as it opens where it would drop. Labels past the
 * stripe's end are cut off by it. EDT only.
 */
final class LabelledStripe extends DockStripe {
    /** Where the last layout put everything; during a drag, also each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot.Group, List<Rectangle>> buttons = new EnumMap<>(Slot.Group.class);
        final EnumMap<Slot.Group, Rectangle> groups = new EnumMap<>(Slot.Group.class);
        Rectangle gap;
    }

    /** A side stripe's groups; the bottom slots' windows show on the bottom strip (Rulings 25). */
    private static final List<Slot.Group> GROUPS = List.of(Slot.Group.TOP, Slot.Group.SECOND);

    private final Side side;
    private final StripeLabels labels;
    private final EnumMap<Slot.Group, List<StripeLabels.Label>> groups = new EnumMap<>(Slot.Group.class);
    private final Map<String, StripeLabels.Label> byId = new LinkedHashMap<>();
    private final List<StripeLabels.Label> actions = new ArrayList<>();
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;
    private boolean dragging;
    private String lifted;
    private int liftedLength;
    private Slot.Group gapGroup;
    private int gapIndex;
    private Rectangle gap;

    LabelledStripe(Side side, Kit kit) {
        super(null);
        if (side == Side.BOTTOM) throw new IllegalArgumentException("A labelled stripe stands on the left or the right");
        this.side = side;
        this.labels = new StripeLabels(kit);
        setOpaque(true);
        getAccessibleContext().setAccessibleName(side == Side.LEFT ? "Left tool windows" : "Right tool windows");
        render(List.of(), List.of());
    }

    @Override Side side() { return side; }

    @Override void render(List<ToolStripe.Button> toolWindows, List<Action> contributed) {
        dragging = false; lifted = null; gapGroup = null; gap = null;
        removeAll();
        byId.clear();
        actions.clear();
        for (Slot.Group group : GROUPS) groups.put(group, new ArrayList<>());
        for (ToolStripe.Button window : toolWindows) {
            if (window.slot().side() != side) continue;
            var label = new StripeLabels.Label(window, this::paintLabel);
            label.setSelected(window.selected());
            label.addActionListener(event -> onToggle.accept(window.id()));
            label.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
                slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
            describe(label);
            groups.get(window.slot().group()).add(label);
            byId.put(window.id(), label);
            dragSource.accept(label, window.id());
        }
        for (Action action : contributed) {
            var label = new StripeLabels.Label(action, this::paintLabel);
            describe(label);
            actions.add(label);
        }
        for (Slot.Group group : GROUPS) groups.get(group).forEach(this::add);
        actions.forEach(this::add);
        revalidate();
        repaint();
    }

    /** The label's tooltip and accessible name and description: its text, its title, and its badge's words. */
    static void describe(StripeLabels.Label label) {
        String text = label.text();
        Optional<PanelBadge> badge = label.badge();
        label.setToolTipText(badge.map(mark -> text + " (" + mark.description() + ")").orElse(text));
        label.getAccessibleContext().setAccessibleName(label.window == null ? text : label.window.title());
        label.getAccessibleContext().setAccessibleDescription(badge.map(PanelBadge::description).orElse(null));
    }

    @Override void updateBadge(String id, Optional<PanelBadge> badge) {
        StripeLabels.Label label = byId.get(id);
        if (label == null) return;
        var window = label.window;
        label.window = new ToolStripe.Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge, window.number());
        describe(label);
        // A pill changes the label's length: lay out again; a drag's lifted label stays lifted.
        revalidate();
        doLayout();
        repaint();
    }

    @Override void refreshActions() {
        actions.forEach(LabelledStripe::describe);
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean empty() { return byId.isEmpty() && actions.isEmpty(); }

    @Override boolean emptyWithout(String id) { return actions.isEmpty() && byId.keySet().stream().allMatch(id::equals); }

    @Override List<AbstractButton> buttons() {
        var all = new ArrayList<AbstractButton>();
        for (Slot.Group group : GROUPS) all.addAll(groups.get(group));
        all.addAll(actions);
        return List.copyOf(all);
    }

    @Override void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate();
        repaint();
    }

    @Override void beginDrag(String id, String label) {
        dragging = true;
        lifted = id;
        StripeLabels.Label own = byId.get(id);
        liftedLength = own != null ? labels.length(own.text(), own.badge()) : labels.length(label, Optional.empty());
        gapGroup = null;
        doLayout();
        repaint();
    }

    @Override void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot.Group group = slot != null && slot.side() == side ? slot.group() : null;
        if (group == gapGroup && (group == null || index == gapIndex)) return;
        gapGroup = group;
        gapIndex = index;
        doLayout();
        repaint();
    }

    @Override void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapGroup = null; gap = null;
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean dragging() { return dragging; }

    @Override Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    @Override List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        var result = new ArrayList<DockDropResolver.Group>();
        for (Slot.Group group : GROUPS)
            result.add(new DockDropResolver.Group(Slot.on(side, group), placement.groups.get(group), placement.buttons.get(group)));
        return result;
    }

    @Override void refreshTheme() { repaint(); }

    @Override public Dimension getMinimumSize() {
        return new Dimension(labels.thickness(railWidth, this), labels.styles().metric(DesignMetrics.Role.RAIL_MIN_HEIGHT));
    }

    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
    }

    /** The two groups down from {@code RAIL_GAP}, then the actions; the drop groups meet after the top group. */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        var styles = labels.styles();
        int rule = styles.metric(DesignMetrics.Role.DOCK_RULE);
        int across = Math.max(0, getWidth() - rule), x = side == Side.LEFT ? 0 : rule;
        int between = styles.metric(DesignMetrics.Role.STRIPE_LABEL_GAP);
        int y = column(Slot.Group.TOP, styles.metric(DesignMetrics.Role.RAIL_GAP), x, across, between, withGap, placement);
        int split = y;
        y = column(Slot.Group.SECOND, y, x, across, between, withGap, placement);
        if (!actions.isEmpty()) {
            y += styles.metric(DesignMetrics.Role.RAIL_SECTION_GAP);
            for (StripeLabels.Label action : actions) {
                int length = labels.length(action.text(), Optional.empty());
                placement.bounds.put(action, new Rectangle(x, y, across, length));
                y += length + between;
            }
        }
        placement.groups.put(Slot.Group.TOP, new Rectangle(0, 0, getWidth(), split));
        placement.groups.put(Slot.Group.SECOND, new Rectangle(0, split, getWidth(), Math.max(0, getHeight() - split)));
        return placement;
    }

    /** The group's shown labels, the lifted one left out. */
    private List<StripeLabels.Label> shown(Slot.Group group) {
        return groups.get(group).stream().filter(label -> !dragging || !label.id().equals(lifted)).toList();
    }

    /** True while the dragged window's label belongs to this group. */
    private boolean liftedIn(Slot.Group group) {
        return dragging && groups.get(group).stream().anyMatch(label -> label.id().equals(lifted));
    }

    /** Lays a group out downward from y and returns where the next label starts. */
    private int column(Slot.Group group, int y, int x, int across, int between, boolean withGap, Placement placement) {
        List<StripeLabels.Label> shown = shown(group);
        boolean gapHere = withGap && dragging && gapGroup == group;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        var rects = new ArrayList<Rectangle>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) {
                placement.gap = new Rectangle(x, y, across, liftedLength);
                y += liftedLength + between;
            }
            if (i == shown.size()) break;
            var label = shown.get(i);
            var at = new Rectangle(x, y, across, labels.length(label.text(), label.badge()));
            placement.bounds.put(label, at);
            rects.add(at);
            y += at.height + between;
        }
        // The lifted label's length stays at the end of its group, and an empty group shows one label's drop space; a
        // gap in the group takes that place.
        if (dragging && !gapHere && (shown.isEmpty() || liftedIn(group))) y += liftedLength + between;
        placement.buttons.put(group, rects);
        return y;
    }

    @Override protected void paintComponent(Graphics graphics) {
        GeneratedTheme theme = ThemeZone.theme(this);
        var painter = labels.painter();
        graphics.setColor(painter.stripeSurface(theme));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE);
        graphics.setColor(painter.dockRule(theme));
        graphics.fillRect(side == Side.LEFT ? getWidth() - rule : 0, 0, rule, getHeight());
    }

    /** A label's tile, then its text and pill turned to read bottom to top (left) or top to bottom (right). */
    private void paintLabel(StripeLabels.Label label, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            GeneratedTheme theme = ThemeZone.theme(this);
            boolean open = label.isSelected();
            Color tile = labels.painter().stripeTile(theme, open, label.getModel().isRollover());
            if (tile != null) {
                g.setColor(tile);
                g.fillRect(0, 0, label.getWidth(), label.getHeight());
            }
            if (side == Side.LEFT) { g.translate(0, label.getHeight()); g.rotate(-Math.PI / 2); }
            else { g.translate(label.getWidth(), 0); g.rotate(Math.PI / 2); }
            labels.paint(g, label.text(), label.badge(), label.getWidth(), theme, open);
        } finally { g.dispose(); }
    }
}
```

- [ ] **Step 4: Run the tests; calibrate against the mocks**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.LabelledStripeTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: PASS: `LabelledStripeTest` 8 tests, `DockMockTest` 2 tests over three variants each.

If a `DockMockTest` assertion fails, open `jasper-app/build/dock-renders/<variant>-left-stripe-{render,mock,diff}.png`.
The knobs, in order: a label's first or last ink off by the same amount at both ends of every label is
`STRIPE_LABEL_PADDING` (`DesignMetrics.java` and both tables, Task 4's script shape); a gap between labels that grows
down the stripe is `STRIPE_LABEL_GAP`; all labels shifted together is `RAIL_GAP`'s read in `place`; text off across the
stripe is the baseline expression in `StripeLabels.paint`. Re-run after each change; never change a mock coordinate or
the expected runs, which are the measured mock (Mock measurements).

- [ ] **Step 5: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"; T = "jasper-app/src/test/java/dev/jasper/app/"
rows = [
    f"{M}StripeLabels.java,app.workspace,StripeLabels,standard-ui,5,5,{T}workspace/LabelledStripeTest.java,New in Enterprise plan 7 Task 5; label text; length; thickness and painting through ChromePainter's dock painters",
    f"{M}LabelledStripe.java,app.workspace,LabelledStripe,standard-ui,5,5,{T}workspace/LabelledStripeTest.java;{T}workspace/DockMockTest.java,New in Enterprise plan 7 Task 5; the classic look's labelled side stripe",
    f"{T}workspace/LabelledStripeTest.java,app.workspace,LabelledStripeTest,nonvisual-helper,none,none,{T}workspace/LabelledStripeTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 5; the labelled stripe",
    f"{T}workspace/DockMockTest.java,app.workspace,DockMockTest,nonvisual-helper,none,none,{T}workspace/DockMockTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 5; the dock chrome against the Enterprise mocks"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: the labelled side stripe

LabelledStripe shows a side's tool windows as labels ("N: " first when the
toggle ends in a digit), bottom to top on the left and top to bottom on the
right, open ones on their tile, badges as count pills, rail actions after
the second group, and a drag view with drop space and a gap. Its 2x render
under the Enterprise fixtures is compared with the Workstation, Dark Mode
and Unlock Vault mocks' stripes; the face-dependent probes print until 5b.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 6: The bottom strip, the labelled set, and drops onto the strip

Spec 10.7: "`LabelledStripes` adds a horizontal strip for the BOTTOM_LEFT and BOTTOM_RIGHT slots, with count pills for
badges that carry a number." `BottomStrip` lays out Bottom Left's labels from the side stripes' thickness and Bottom
Right's from the right (Rulings 8), each followed (or, on the right, preceded) by a divider; `StripeSet.LabelledStripes`
routes the bottom slots to it, and `DockChrome.stripes` builds it under labelled stripes. A drag drops onto it by its
horizontal groups (Task 2) and stands a hidden strip over the workspace's bottom edge (Task 3).

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/BottomStrip.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/BottomStripTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragBottomStripTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/StripeSet.java`, `DockChrome.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 3's `DockStripe`, `StripeSet`, `DockDrag(…, StripeSet)`; Task 5's `StripeLabels`,
  `LabelledStripe.describe`, `LabelledStripe`; Task 2's `DockDropResolver.Axis`; `DockChromeTest.CLASSIC_DOCK` (Task 1).
- Produces: `BottomStrip(Kit)`; `record StripeSet.LabelledStripes(LabelledStripe left, LabelledStripe right, BottomStrip
  bottomStrip)` with `LabelledStripes(Kit)`; `DockChrome.stripes(kit)` giving it when `labelledStripes`.

- [ ] **Step 1: Write the failing tests**

The window tests use plan 6a's `DesktopTestSupport.content(ShellLauncher, ThemeController, WorkspaceChrome look)`.

Create `jasper-app/src/test/java/dev/jasper/app/workspace/BottomStripTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.swing.ChromePainter;
import java.awt.Color;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: the bottom strip's labels, dividers, groups and place in the window. */
@ExtendWith(EdtTestExtension.class)
class BottomStripTest {
    @Test void bottomLeftStartsAtTheStripesThicknessAndBottomRightEndsAtItWithDividers() throws Exception {
        try {
            EnterpriseLook.install(false);
            var kit = EnterpriseLook.kit();
            var strip = new BottomStrip(kit);
            strip.render(List.of(LabelledStripeTest.window("log", "Event Log", Slot.BOTTOM_LEFT, 0),
                LabelledStripeTest.window("transfers", "Transfers", Slot.BOTTOM_LEFT, 3),
                LabelledStripeTest.window("terminal", "Terminal", Slot.BOTTOM_RIGHT, 0),
                LabelledStripeTest.window("sftp", "SFTP", Slot.LEFT_TOP, 2)), List.of());
            int thickness = strip.getPreferredSize().height;
            assertThat(thickness).as("as thick as the side stripes").isEqualTo(new LabelledStripe(Side.LEFT, kit).getPreferredSize().width);
            var image = MockCrops.render(strip, 900, thickness, 1);
            assertThat(strip.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log", "3: Transfers", "Terminal");
            Rectangle log = strip.buttons().get(0).getBounds(), transfers = strip.buttons().get(1).getBounds(), terminal = strip.buttons().get(2).getBounds();
            assertThat(log.x).as("aligned with the left stripe").isEqualTo(thickness);
            assertThat(log.y).as("under the top rule").isEqualTo(1);
            assertThat(transfers.x).as("after the divider").isEqualTo(log.x + log.width + 1);
            assertThat(terminal.x + terminal.width).as("aligned with the right stripe").isEqualTo(900 - thickness);
            var painter = new ChromePainter(kit);
            var theme = EnterpriseLook.theme(false);
            assertThat(new Color(image.getRGB(log.x + log.width, thickness / 2))).as("a divider after each label").isEqualTo(painter.stripDivider(theme));
            assertThat(new Color(image.getRGB(terminal.x - 1, thickness / 2))).as("a divider before each right label").isEqualTo(painter.stripDivider(theme));
            assertThat(new Color(image.getRGB(450, 0))).as("the top rule").isEqualTo(painter.dockRule(theme));
            assertThat(new Color(image.getRGB(5, thickness / 2))).as("the strip's surface").isEqualTo(painter.stripeSurface(theme));
            var groups = strip.dropGroups();
            assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
            assertThat(groups).extracting(DockDropResolver.Group::axis).containsOnly(DockDropResolver.Axis.HORIZONTAL);
            assertThat(groups.get(0).bounds().x + groups.get(0).bounds().width).isEqualTo(groups.get(1).bounds().x);
            assertThat(groups.get(0).buttons()).hasSize(2);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theLabelledSetShowsTheBottomSlotsOnTheStrip() throws Exception {
        try {
            EnterpriseLook.install(false);
            var set = new StripeSet.LabelledStripes(EnterpriseLook.kit());
            assertThat(set.of(Slot.LEFT_BOTTOM)).isSameAs(set.left());
            assertThat(set.of(Slot.RIGHT_TOP)).isSameAs(set.right());
            assertThat(set.of(Slot.BOTTOM_LEFT)).isSameAs(set.bottomStrip());
            assertThat(set.of(Slot.BOTTOM_RIGHT)).isSameAs(set.bottomStrip());
            assertThat(set.all()).containsExactly(set.left(), set.right(), set.bottomStrip());
            assertThat(new DockChrome(true, true).stripes(EnterpriseLook.kit())).isInstanceOf(StripeSet.LabelledStripes.class);
            assertThat(DockChrome.STANDARD.stripes(EnterpriseLook.kit())).isInstanceOf(StripeSet.IconRails.class);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Review Focus 4: a window under labelled stripes counts the bottom strip in its height and shows it with a bottom window. */
    @Test void theStripCountsInTheWindowsMinimumSize() throws Exception {
        var themes = ThemeTestSupport.controller(false);
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes,
            DockChromeTest.classicDock());
        try {
            var model = new Contributions();
            owner.connectContributions(model, UiState.inMemory());
            var strip = owner.bottomStrip().orElseThrow();
            assertThat(strip.isVisible()).as("an empty strip hides").isFalse();
            int before = owner.getMinimumSize().height;
            model.addPanel("dev.x.log", "Event Log", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("log"));
            assertThat(strip.isVisible()).isTrue();
            assertThat(strip.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log");
            assertThat(owner.leftStripe().buttons()).as("no foot group on the side stripes").isEmpty();
            assertThat(owner.getMinimumSize().height).isGreaterThanOrEqualTo(before + strip.getPreferredSize().height);
        } finally { owner.close(); }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragBottomStripTest.java`:

```java
package dev.jasper.app.workspace;

import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.LookAndFeel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Review Focus 3: dragging a tool window onto the bottom strip, a window built like WindowContent's under labelled stripes. */
class DockDragBottomStripTest {
    private CommandPaletteShortcutsTest.RecordingFocusManager keys;
    private KeyboardFocusManager previousKeys;
    private final List<Window> windows = new ArrayList<>();

    @BeforeEach void setUp() throws Exception {
        edt(() -> {
            EnterpriseLook.install(false);
            previousKeys = KeyboardFocusManager.getCurrentKeyboardFocusManager();
            keys = new CommandPaletteShortcutsTest.RecordingFocusManager();
            KeyboardFocusManager.setCurrentKeyboardFocusManager(keys);
        });
    }

    @AfterEach void tearDown() throws Exception {
        edt(() -> {
            try {
                for (Window window : windows) window.drag.cancel();
                assertThat(keys.dispatchers).isEmpty();
            } finally {
                KeyboardFocusManager.setCurrentKeyboardFocusManager(previousKeys);
                EnterpriseLook.restoreDefault();
            }
        });
    }

    /** a and b on the left stripe's top group, c and e in Bottom Left (or none), d on the right stripe. */
    private final class Window {
        final DockState state = new DockState();
        final StripeSet.LabelledStripes stripes = new StripeSet.LabelledStripes(EnterpriseLook.kit());
        final ToolWindowArea area = new ToolWindowArea(new JPanel(), state, EnterpriseLook.kit(), true);
        final JPanel content = new JPanel(new BorderLayout());
        final JRootPane rootPane = new JRootPane();
        final DockDrag drag = new DockDrag(content, state, area, stripes);

        Window(boolean bottomWindows) {
            var header = new JPanel(); header.setPreferredSize(new Dimension(10, 30));
            var body = new JPanel(new BorderLayout());
            body.add(stripes.left(), BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(stripes.right(), BorderLayout.EAST);
            body.add(stripes.bottomStrip(), BorderLayout.SOUTH);
            content.add(header, BorderLayout.NORTH); content.add(body, BorderLayout.CENTER);
            rootPane.setContentPane(content);
            for (DockStripe stripe : stripes.all()) stripe.dragSource = drag::install;
            area.dragSource = drag::install;
            drag.railsEnabled = () -> true;
            drag.labels = id -> id.toUpperCase();
            drag.onDrop = (id, target) -> {
                if (target.onStripe()) state.move(id, target.slot(), target.index().getAsInt());
                else state.moveAndShow(id, target.slot(), state.dropIndex(id, target.slot()));
            };
            for (String id : List.of("a", "b")) { state.add(id, Slot.LEFT_TOP); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            if (bottomWindows) for (String id : List.of("c", "e")) { state.add(id, Slot.BOTTOM_LEFT); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            state.add("d", Slot.RIGHT_TOP); area.setContent("d", "D", new JLabel("d"));
            state.addListener(change -> { drag.cancel(); area.sync(); render(); layout(); });
            render();
            rootPane.setSize(1200, 800);
            layout();
            windows.add(this);
        }

        void render() {
            var buttons = new ArrayList<ToolStripe.Button>();
            for (Slot slot : Slot.values())
                for (String id : state.ids(slot)) buttons.add(new ToolStripe.Button(id, id.toUpperCase(), null, slot, state.visible(id)));
            for (DockStripe stripe : stripes.all()) {
                stripe.render(buttons, List.of());
                stripe.setVisible(!stripe.empty());
            }
        }

        void layout() { LayoutTestSupport.layoutTree(rootPane); }

        AbstractButton label(DockStripe stripe, String id) {
            return stripe.buttons().stream().filter(button -> id.toUpperCase().equals(button.getToolTipText())).findFirst().orElseThrow();
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), content);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        void mouse(JComponent source, int id, Point inContent) {
            Point at = SwingUtilities.convertPoint(content, inContent, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** Presses on the label, moves past the threshold, then to {@code to}, and releases there. */
        void drop(AbstractButton source, Point to) {
            Point from = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, from);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(from.x + 20, from.y + 20));
            mouse(source, MouseEvent.MOUSE_DRAGGED, to);
            mouse(source, MouseEvent.MOUSE_RELEASED, to);
        }
    }

    @Test void aDropBetweenTwoBottomLabelsLandsBetweenThem() throws Exception {
        edt(() -> {
            var window = new Window(true);
            var strip = window.stripes.bottomStrip();
            Rectangle c = SwingUtilities.convertRectangle(strip, window.label(strip, "c").getBounds(), window.content);
            Rectangle e = SwingUtilities.convertRectangle(strip, window.label(strip, "e").getBounds(), window.content);
            window.drop(window.label(window.stripes.left(), "a"), new Point((c.x + c.width + e.x + e.width / 2) / 2 + 1, c.y + c.height / 2));
            assertThat(window.state.ids(Slot.BOTTOM_LEFT)).containsExactly("c", "a", "e");
            assertThat(window.state.ids(Slot.LEFT_TOP)).containsExactly("b");
        });
    }

    @Test void aDropOnTheEmptyRightHalfLandsInBottomRight() throws Exception {
        edt(() -> {
            var window = new Window(true);
            var strip = window.stripes.bottomStrip();
            Rectangle bounds = SwingUtilities.convertRectangle(strip.getParent(), strip.getBounds(), window.content);
            window.drop(window.label(window.stripes.left(), "b"), new Point(bounds.x + bounds.width - 120, bounds.y + bounds.height / 2));
            assertThat(window.state.ids(Slot.BOTTOM_RIGHT)).containsExactly("b");
        });
    }

    @Test void aHiddenStripTakesADropAtTheWorkspacesBottomEdgeAndShows() throws Exception {
        edt(() -> {
            var window = new Window(false);
            var strip = window.stripes.bottomStrip();
            assertThat(strip.isVisible()).as("no bottom window: the strip hides").isFalse();
            Rectangle area = SwingUtilities.convertRectangle(window.area.getParent(), window.area.getBounds(), window.content);
            int edge = area.y + area.height - strip.thickness() / 2;
            window.drop(window.label(window.stripes.left(), "a"), new Point(area.x + 200, edge));
            assertThat(window.state.ids(Slot.BOTTOM_LEFT)).containsExactly("a");
            assertThat(strip.isVisible()).as("the drop gives the strip a window").isTrue();
        });
    }
}
```

In `DockMockTest`, insert before `    // Later tasks add their surfaces above this line.`:

```java
    /**
     * The bottom strip's three closed labels, "Event Log", "Transfers" and "Problems" with a count pill (a fixture: the
     * mock's amber pill is the deferred Problems panel; this one is the error tone), against the mocks' bottom strip: the
     * strip is 28 thick against the mock's 24 and its labels start at the side stripes' 28 against 23 (Rulings 4 and 8),
     * so the render is cropped on its fill's middle rows and its ink is compared 10 px (2x) to the right of the mock's.
     * Asserted: the surface, three dividers in the mock's colour, the first label's leading ink and the pill's width;
     * the later edges, the pill's start and the agreeing share follow the labels' advance widths and glyphs and are
     * printed until plan 5b's Plex (Execution notes R5). The render is 330 wide, past the mock's 270, so the platform
     * face's wider labels still show their third divider.
     */
    @Test void theBottomStripMatchesTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var strip = new BottomStrip(EnterpriseLook.kit());
                var problems = new ToolStripe.Button("problems", "Problems", null, Slot.BOTTOM_LEFT, false,
                    java.util.Optional.of(new dev.jasper.app.contributions.PanelBadge(1, dev.jasper.app.contributions.PanelBadge.Tone.ERROR, "1 problem")),
                    java.util.OptionalInt.empty());
                strip.render(List.of(new ToolStripe.Button("log", "Event Log", null, Slot.BOTTOM_LEFT, false),
                    new ToolStripe.Button("transfers", "Transfers", null, Slot.BOTTOM_LEFT, false), problems), List.of());
                int thickness = strip.getPreferredSize().height, shift = 2 * (thickness - 23);
                var render = variant.seen(MockCrops.render(strip, 330, thickness, 2));
                var mock = variant.crop(2, 1708, 520, 48);
                // The render's fill (below its 2 px rule at 2x), its middle 48 rows; the mock crop is the mock's fill.
                int fill = 2 * thickness - 2, top = 2 + (fill - 48) / 2;
                var rows = MockCrops.crop(render, shift, top, 600, 48);
                MockCrops.write(variant.name() + "-bottom-strip", MockCrops.crop(rows, 0, 0, 520, 48), mock);
                String where = variant.name() + " bottom strip";
                Color surface = MockCrops.at(mock, 10, 4), divider = MockCrops.at(mock, 188, 24);
                assertThat(MockCrops.near(MockCrops.at(rows, 10, 4), surface, variant.fillTolerance())).as(where + ": its surface").isTrue();
                // The dividers, found as runs in the fill's top 6 rows (2x), where no text or pill has ink (adjacent
                // columns join: gap 1): the mock's are 2 px wide at 188, 332 and 518 of this crop. border.divider is the
                // fixture theme's #D3D6DA and #4A4D53 (6b's override), the mock's.
                var theirDividers = MockCrops.runs(variant.crop(2, 1708, 525, 6), false, 20, 1, surface);
                var myDividers = MockCrops.runs(MockCrops.crop(rows, 0, 0, 600, 6), false, 20, 1, surface);
                assertThat(theirDividers).as(where + ": the mock's dividers").hasSize(3);
                assertThat(myDividers).as(where + ": a divider after each label").hasSize(3);
                for (int i = 0; i < 3; i++) {
                    MockProfile.fonts(where + " divider " + i, myDividers.get(i).start(), theirDividers.get(i).start(), 2);
                    assertThat(myDividers.get(i).length()).as(where + ": divider " + i + " is 1 px").isEqualTo(theirDividers.get(i).length());
                    assertThat(MockCrops.near(myDividers.get(i).ink(), divider, 8)).as(where + ": divider " + i + "'s colour").isTrue();
                }
                // The pill: the columns holding its painted ground (the error tone's danger.ground, under the variant's
                // scrim), its round ends' antialiased columns aside; the mock's amber pill is found against the surface.
                var ground = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
                ground.setRGB(0, 0, dev.jasper.ui.theme.DerivedColor.DANGER_GROUND.color(EnterpriseLook.theme(variant.dark())).getRGB());
                int painted = variant.seen(ground).getRGB(0, 0), pillStart = -1, pillEnd = -1;
                for (int x = 0; x < rows.getWidth(); x++)
                    for (int y = 0; y < rows.getHeight(); y++)
                        if (rows.getRGB(x, y) == painted) { if (pillStart < 0) pillStart = x; pillEnd = x; break; }
                assertThat(pillStart).as(where + ": the pill is painted").isNotNegative();
                assertThat(pillEnd - pillStart + 1).as(where + ": the pill is 16 wide").isBetween(28, 34);
                var theirPill = MockCrops.runs(MockCrops.crop(mock, 462, 0, 40, 48), false, 30, 2, surface);
                assertThat(theirPill).as(where + ": the mock's pill").hasSize(1);
                MockProfile.fonts(where + " pill start", pillStart, 462 + theirPill.getFirst().start(), 3);
                var theirs = MockCrops.runs(MockCrops.crop(mock, 0, 0, 465, 48), false, 120, 20, surface, divider);
                var mine = MockCrops.runs(MockCrops.crop(rows, 0, 0, pillStart - 2, 48), false, 120, 20, surface, divider);
                assertThat(theirs).as(where + ": the mock's labels").hasSize(3);
                assertThat(mine).as(where + ": one ink run per label").hasSize(3);
                assertThat(mine.getFirst().start()).as(where + " label 0 starts")
                    .isBetween(theirs.getFirst().start() - 2, theirs.getFirst().start() + 2);
                for (int i = 0; i < 3; i++) {
                    if (i > 0) MockProfile.fonts(where + " label " + i + " start", mine.get(i).start(), theirs.get(i).start(), 2);
                    MockProfile.fonts(where + " label " + i + " end", mine.get(i).end(), theirs.get(i).end(), 2);
                }
                MockCrops.fontsFloor(where + ": agreeing pixels",
                    MockCrops.agreement(MockCrops.crop(rows, 0, 0, 460, 48), MockCrops.crop(mock, 0, 0, 460, 48), 24), STRIPE_FLOOR);
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.BottomStripTest' --tests 'dev.jasper.app.workspace.DockDragBottomStripTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class BottomStrip" and "LabelledStripes".

- [ ] **Step 3: Write `BottomStrip` and the labelled set**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/BottomStrip.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.ThemeZone;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.theme.GeneratedTheme;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.Action;

/**
 * The classic look's bottom strip (spec 10.7): the Bottom Left windows' labels from the side stripes' thickness rightward,
 * each followed by a 1 px {@code border.divider}, and the Bottom Right windows' labels ending at that thickness from the
 * right, each preceded by one; a 1 px rule along its top; as thick as the side stripes. Labels read across, an open
 * window's on its tile, a badge as a count pill. Its two groups meet midway between them and lie across, for the drag.
 * It shows no rail actions. EDT only.
 */
final class BottomStrip extends DockStripe {
    /** Where the last layout put everything: the labels, the dividers, each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot, List<Rectangle>> buttons = new EnumMap<>(Slot.class);
        final EnumMap<Slot, Rectangle> groups = new EnumMap<>(Slot.class);
        final List<Integer> dividers = new ArrayList<>();
        Rectangle gap;
    }

    /** One place along the strip: a label, the gap, or a dragged label's reserved space (neither label nor gap). */
    private record Item(StripeLabels.Label label, int length, boolean gap) { }

    private static final List<Slot> SLOTS = List.of(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);

    private final StripeLabels labels;
    private final EnumMap<Slot, List<StripeLabels.Label>> groups = new EnumMap<>(Slot.class);
    private final Map<String, StripeLabels.Label> byId = new LinkedHashMap<>();
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;
    private boolean dragging;
    private String lifted;
    private int liftedLength;
    private Slot gapSlot;
    private int gapIndex;
    private Rectangle gap;
    private List<Integer> dividers = List.of();

    BottomStrip(Kit kit) {
        super(null);
        this.labels = new StripeLabels(kit);
        setOpaque(true);
        getAccessibleContext().setAccessibleName("Bottom tool windows");
        render(List.of(), List.of());
    }

    @Override Side side() { return Side.BOTTOM; }

    @Override void render(List<ToolStripe.Button> toolWindows, List<Action> contributed) {
        dragging = false; lifted = null; gapSlot = null; gap = null;
        removeAll();
        byId.clear();
        for (Slot slot : SLOTS) groups.put(slot, new ArrayList<>());
        for (ToolStripe.Button window : toolWindows) {
            if (window.slot().side() != Side.BOTTOM) continue;
            var label = new StripeLabels.Label(window, this::paintLabel);
            label.setSelected(window.selected());
            label.addActionListener(event -> onToggle.accept(window.id()));
            label.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
                slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
            LabelledStripe.describe(label);
            groups.get(window.slot()).add(label);
            byId.put(window.id(), label);
            dragSource.accept(label, window.id());
        }
        for (Slot slot : SLOTS) groups.get(slot).forEach(this::add);
        revalidate();
        repaint();
    }

    @Override void updateBadge(String id, Optional<PanelBadge> badge) {
        StripeLabels.Label label = byId.get(id);
        if (label == null) return;
        var window = label.window;
        label.window = new ToolStripe.Button(window.id(), window.title(), window.icon(), window.slot(), window.selected(), badge, window.number());
        LabelledStripe.describe(label);
        revalidate();
        doLayout();
        repaint();
    }

    /** The strip shows no actions. */
    @Override void refreshActions() { }

    @Override boolean empty() { return byId.isEmpty(); }

    @Override boolean emptyWithout(String id) { return byId.keySet().stream().allMatch(id::equals); }

    @Override List<AbstractButton> buttons() {
        var all = new ArrayList<AbstractButton>();
        for (Slot slot : SLOTS) all.addAll(groups.get(slot));
        return List.copyOf(all);
    }

    @Override void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate();
        repaint();
    }

    @Override void beginDrag(String id, String label) {
        dragging = true;
        lifted = id;
        StripeLabels.Label own = byId.get(id);
        liftedLength = own != null ? labels.length(own.text(), own.badge()) : labels.length(label, Optional.empty());
        gapSlot = null;
        doLayout();
        repaint();
    }

    @Override void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot target = slot != null && slot.side() == Side.BOTTOM ? slot : null;
        if (target == gapSlot && (target == null || index == gapIndex)) return;
        gapSlot = target;
        gapIndex = index;
        doLayout();
        repaint();
    }

    @Override void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapSlot = null; gap = null;
        revalidate();
        doLayout();
        repaint();
    }

    @Override boolean dragging() { return dragging; }

    @Override Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    @Override List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        var result = new ArrayList<DockDropResolver.Group>();
        for (Slot slot : SLOTS)
            result.add(new DockDropResolver.Group(slot, placement.groups.get(slot), placement.buttons.get(slot), DockDropResolver.Axis.HORIZONTAL));
        return result;
    }

    @Override void refreshTheme() { repaint(); }

    @Override public Dimension getMinimumSize() { return new Dimension(0, labels.thickness(railWidth, this)); }

    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
        dividers = List.copyOf(placement.dividers);
    }

    /** A group's places in order: its shown labels with the gap among them, or the lifted label's reserved space at the end. */
    private List<Item> items(Slot slot, boolean withGap) {
        List<StripeLabels.Label> shown = groups.get(slot).stream().filter(label -> !dragging || !label.id().equals(lifted)).toList();
        boolean gapHere = withGap && dragging && gapSlot == slot;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        var items = new ArrayList<Item>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) items.add(new Item(null, liftedLength, true));
            if (i < shown.size()) items.add(new Item(shown.get(i), labels.length(shown.get(i).text(), shown.get(i).badge()), false));
        }
        boolean liftedHere = dragging && groups.get(slot).stream().anyMatch(label -> label.id().equals(lifted));
        if (dragging && !gapHere && (shown.isEmpty() || liftedHere)) items.add(new Item(null, liftedLength, false));
        return items;
    }

    /** Bottom Left from the lead rightward, Bottom Right ending at the lead from the right; the groups meet midway. */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE), across = Math.max(0, getHeight() - rule);
        int lead = labels.thickness(railWidth, this);
        int x = lead;
        var left = new ArrayList<Rectangle>();
        for (Item item : items(Slot.BOTTOM_LEFT, withGap)) {
            var at = new Rectangle(x, rule, item.length(), across);
            if (item.label() != null) { placement.bounds.put(item.label(), at); left.add(at); }
            if (item.gap()) placement.gap = at;
            if (item.label() != null || item.gap()) placement.dividers.add(x + item.length());
            x += item.length() + rule;
        }
        int leftEnd = x;
        var rightItems = items(Slot.BOTTOM_RIGHT, withGap);
        int total = rightItems.stream().mapToInt(item -> rule + item.length()).sum();
        int start = Math.max(leftEnd, getWidth() - lead - total);
        x = start;
        var right = new ArrayList<Rectangle>();
        for (Item item : rightItems) {
            if (item.label() != null || item.gap()) placement.dividers.add(x);
            x += rule;
            var at = new Rectangle(x, rule, item.length(), across);
            if (item.label() != null) { placement.bounds.put(item.label(), at); right.add(at); }
            if (item.gap()) placement.gap = at;
            x += item.length();
        }
        int boundary = Math.clamp((leftEnd + start) / 2, 0, Math.max(0, getWidth()));
        placement.buttons.put(Slot.BOTTOM_LEFT, left);
        placement.buttons.put(Slot.BOTTOM_RIGHT, right);
        placement.groups.put(Slot.BOTTOM_LEFT, new Rectangle(0, 0, boundary, getHeight()));
        placement.groups.put(Slot.BOTTOM_RIGHT, new Rectangle(boundary, 0, Math.max(0, getWidth() - boundary), getHeight()));
        return placement;
    }

    @Override protected void paintComponent(Graphics graphics) {
        GeneratedTheme theme = ThemeZone.theme(this);
        var painter = labels.painter();
        int rule = labels.styles().metric(DesignMetrics.Role.DOCK_RULE);
        graphics.setColor(painter.stripeSurface(theme));
        graphics.fillRect(0, 0, getWidth(), getHeight());
        graphics.setColor(painter.dockRule(theme));
        graphics.fillRect(0, 0, getWidth(), rule);
        graphics.setColor(painter.stripDivider(theme));
        for (int x : dividers) graphics.fillRect(x, rule, rule, Math.max(0, getHeight() - rule));
    }

    /** A label's tile, then its text and pill, reading across. */
    private void paintLabel(StripeLabels.Label label, Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            GeneratedTheme theme = ThemeZone.theme(this);
            boolean open = label.isSelected();
            Color tile = labels.painter().stripeTile(theme, open, label.getModel().isRollover());
            if (tile != null) {
                g.setColor(tile);
                g.fillRect(0, 0, label.getWidth(), label.getHeight());
            }
            labels.paint(g, label.text(), label.badge(), label.getHeight(), theme, open);
        } finally { g.dispose(); }
    }
}
```

Add the labelled set to `StripeSet.java` and build it in `DockChrome.java`:

```bash
python3 -I - <<'PY'
"""Plan 7 Task 6 step 3: the labelled stripe set."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "StripeSet.java", [
    ('''sealed interface StripeSet permits StripeSet.IconRails {''', '''sealed interface StripeSet permits StripeSet.IconRails, StripeSet.LabelledStripes {''', 1),
    ('''        @Override public DockStripe of(Slot slot) { return slot.stripe() == Side.LEFT ? left : right; }
    }''',
     '''        @Override public DockStripe of(Slot slot) { return slot.stripe() == Side.LEFT ? left : right; }
    }

    /**
     * The classic look's stripes: the side slots' windows on their side's labelled stripe, the bottom slots' on the
     * bottom strip.
     *
     * @param left the left labelled stripe
     * @param right the right labelled stripe
     * @param bottomStrip the bottom strip
     */
    record LabelledStripes(LabelledStripe left, LabelledStripe right, BottomStrip bottomStrip) implements StripeSet {
        /** Three new stripes over the window's kit. */
        LabelledStripes(dev.jasper.ui.kit.Kit kit) { this(new LabelledStripe(Side.LEFT, kit), new LabelledStripe(Side.RIGHT, kit), new BottomStrip(kit)); }

        @Override public Optional<DockStripe> bottom() { return Optional.of(bottomStrip); }

        @Override public DockStripe of(Slot slot) {
            return switch (slot.side()) {
                case LEFT -> left;
                case RIGHT -> right;
                case BOTTOM -> bottomStrip;
            };
        }
    }''', 1)])
edit(W + "DockChrome.java", [
    ('''    /** A new set of stripes for one window over its kit: today's icon rails. */
    StripeSet stripes(dev.jasper.ui.kit.Kit kit) { return new StripeSet.IconRails(); }''',
     '''    /** A new set of stripes for one window over its kit: the labelled stripes and the bottom strip, or the icon rails. */
    StripeSet stripes(dev.jasper.ui.kit.Kit kit) { return labelledStripes ? new StripeSet.LabelledStripes(kit) : new StripeSet.IconRails(); }''', 1)])
PY
```

- [ ] **Step 4: Run the tests; calibrate against the mocks**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.BottomStripTest' --tests 'dev.jasper.app.workspace.DockDragBottomStripTest' --tests 'dev.jasper.app.workspace.DockDragTest' --tests 'dev.jasper.app.workspace.StripeSetTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: PASS (`BottomStripTest` 3, `DockDragBottomStripTest` 3, `DockMockTest` 3 over three variants), with the
bottom strip's `FONTS residual until plan 5b` lines printed (Execution notes R5; measured at `759b2b7d9` in the platform
face: the dividers at 196, 344 and 532 for the mock's 188, 332 and 518, the pill from 483 for 466, agreeing pixels 0.856,
0.859 and 0.860). For an assertion failure open
`jasper-app/build/dock-renders/<variant>-bottom-strip-{render,mock,diff}.png`; the knobs are `STRIPE_LABEL_PADDING`
(the first label's leading ink: change it only together with Task 5's stripes, which share it), the pill's
`STRIPE_PILL_WIDTH` and `STRIPE_PILL_PADDING` (its width), and the baseline in `StripeLabels.paint` (text off
vertically). The printed edges become assertions at the merge with plan 5b, where `STRIPE_PILL_GAP` (text to pill) and
the label widths are the knobs; the gap after a pill, before its divider, is the label's trailing
`STRIPE_LABEL_PADDING`, as after text (`StripeLabels.length`), with no knob of its own.

- [ ] **Step 5: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"; T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
rows = [
    f"{M}BottomStrip.java,app.workspace,BottomStrip,standard-ui,6,6,{T}BottomStripTest.java;{T}DockMockTest.java,New in Enterprise plan 7 Task 6; the classic look's bottom strip",
    f"{T}BottomStripTest.java,app.workspace,BottomStripTest,nonvisual-helper,none,none,{T}BottomStripTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 6; the bottom strip",
    f"{T}DockDragBottomStripTest.java,app.workspace,DockDragBottomStripTest,nonvisual-helper,none,none,{T}DockDragBottomStripTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 6; drops onto the bottom strip"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: the bottom strip and drops onto it

BottomStrip shows the Bottom Left windows from the side stripes' thickness
and the Bottom Right windows from the right, with dividers, count pills and
two groups that lie across for the drag. The labelled stripe set routes the
bottom slots to it, and DockChrome builds it under labelled stripes. A drag
drops between its labels, onto its empty half, and onto it while it is
hidden. Its 2x render matches the three mocks' bottom strip.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 7: Stripe numbers from the live keymap

Spec 10.7: "A label starts with "N: " when the panel's toggle action is bound to a single digit with modifiers (⌘⌥1 on
macOS, Alt+1 elsewhere), read from the live keymap, so today's bindings give "1: Sessions", "2: SFTP" and "3:
Transfers"." The window's contributions read each panel's toggle stroke from the window's effective bindings, render the
digit into the stripe button, and render again when a rebind changes a digit (Rulings 10). The icon rails ignore it.

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/StripeNumbers.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/StripeNumbersTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/StripeNumbersWindowTest.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java:87,110-112,329-345`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 3's seven-argument `ToolStripe.Button` and `DockDrag.labels`; Task 5's `StripeLabels.text`;
  `PanelEntry.toggleActionId()`, `WindowContent.bindings()`, `KeyBindings.strokeFor(String)`.
- Produces: `StripeNumbers.of(Optional<KeyStroke>)` returning `OptionalInt`; stripe buttons carrying their digit;
  `DockDrag.labels` giving "N: Title".

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/StripeNumbersTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import javax.swing.KeyStroke;
import org.junit.jupiter.api.Test;

/** Spec 10.7 and Rulings 10: which toggle strokes number a stripe label. */
class StripeNumbersTest {
    private static Optional<KeyStroke> stroke(int code, int modifiers) { return Optional.of(KeyStroke.getKeyStroke(code, modifiers)); }

    @Test void aDigitWithCtrlAltOrMetaNumbersTheLabel() {
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, InputEvent.META_DOWN_MASK | InputEvent.ALT_DOWN_MASK))).hasValue(1);
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_2, InputEvent.ALT_DOWN_MASK))).hasValue(2);
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_0, InputEvent.CTRL_DOWN_MASK | InputEvent.SHIFT_DOWN_MASK))).hasValue(0);
    }

    @Test void anythingElseLeavesTheLabelUnnumbered() {
        assertThat(StripeNumbers.of(Optional.empty())).isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, 0))).as("a bare digit").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_1, InputEvent.SHIFT_DOWN_MASK))).as("Shift alone types a symbol").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_S, InputEvent.ALT_DOWN_MASK))).as("not a digit").isEmpty();
        assertThat(StripeNumbers.of(stroke(KeyEvent.VK_NUMPAD1, InputEvent.ALT_DOWN_MASK))).as("the keypad's digits do not count").isEmpty();
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/StripeNumbersWindowTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a window's labelled stripes number their labels from the window's live keymap. */
@ExtendWith(EdtTestExtension.class)
class StripeNumbersWindowTest {
    private static List<String> tips(DockStripe stripe) { return stripe.buttons().stream().map(AbstractButton::getToolTipText).toList(); }

    private static WindowContent window(Contributions model) {
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(false),
            DockChromeTest.classicDock());
        owner.connectContributions(model, UiState.inMemory());
        model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+2"), invocation -> { });
        model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
        model.addPanel("test.servers", "File Servers", new ImageIcon(), Slot.LEFT_BOTTOM, site -> new JLabel("servers"));
        return owner;
    }

    @Test void aPanelsLabelStartsWithTheDigitOfItsToggle() {
        var owner = window(new Contributions());
        try {
            assertThat(tips(owner.leftStripe())).containsExactly("2: SFTP", "File Servers");
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("2: SFTP");
        } finally { owner.close(); }
    }

    /** Review Focus 1. */
    @Test void aRebindRenumbersTheStripeAtOnce() {
        var owner = window(new Contributions());
        try {
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+7")));
            assertThat(tips(owner.leftStripe())).containsExactly("7: SFTP", "File Servers");
            assertThat(owner.dockDrag().active()).isFalse();
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+s")));
            assertThat(tips(owner.leftStripe())).containsExactly("SFTP", "File Servers");
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("SFTP");
        } finally { owner.close(); }
    }

    /** The default look: a rebind leaves the icon rails alone (no re-render, so a running drag is not cancelled). */
    @Test void aRebindUnderTheIconRailsLeavesTheRailsAlone() {
        var model = new Contributions();
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), ThemeTestSupport.controller(false),
            WorkspaceChrome.STANDARD);
        try {
            owner.connectContributions(model, UiState.inMemory());
            model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+2"), invocation -> { });
            model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
            var button = owner.leftStripe().buttons().getFirst();
            owner.setBindings(KeyBindings.withOverrides(false, Map.of("test.sftp.toggle", "alt+7")));
            assertThat(owner.leftStripe().buttons().getFirst()).as("the rail was not rebuilt").isSameAs(button);
            assertThat(owner.dockDrag().labels.apply("test.sftp")).isEqualTo("SFTP");
        } finally { owner.close(); }
    }
}
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.StripeNumbers*'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol: class StripeNumbers" (and, without it, the window test
would read "SFTP" for "2: SFTP").

- [ ] **Step 3: Write `StripeNumbers` and render the digits**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/StripeNumbers.java`:

```java
package dev.jasper.app.workspace;

import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.KeyStroke;

/**
 * The digit a panel's toggle shortcut gives its stripe label (spec 10.7), IntelliJ's "1: Project": a shortcut whose key
 * is 0 to 9, held with Ctrl, Alt or Meta. Shift alone does not count, as it types a symbol, and neither do the keypad's
 * digits.
 */
final class StripeNumbers {
    private static final int MODIFIERS = InputEvent.CTRL_DOWN_MASK | InputEvent.ALT_DOWN_MASK | InputEvent.META_DOWN_MASK;

    private StripeNumbers() { }

    /** The digit of {@code stroke}, or none. */
    static OptionalInt of(Optional<KeyStroke> stroke) {
        if (stroke.isEmpty()) return OptionalInt.empty();
        KeyStroke key = stroke.get();
        int code = key.getKeyCode();
        if (code < KeyEvent.VK_0 || code > KeyEvent.VK_9 || (key.getModifiers() & MODIFIERS) == 0) return OptionalInt.empty();
        return OptionalInt.of(code - KeyEvent.VK_0);
    }
}
```

```bash
python3 -I - <<'PY'
"""Plan 7 Task 7 step 3: the window's stripes carry the toggles' digits and follow rebinds."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java", [
    ('''        owner.dockDrag().labels = id -> panels.containsKey(id) ? panels.get(id).entry.title() : "";''',
     '''        owner.dockDrag().labels = this::stripeLabel;''', 1),
    ('''    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
    }''',
     '''    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
        // A rebind that changes a toggle's digit renames its labelled stripe's label at once (spec 10.7); under the
        // icon rails every number is empty, so this never re-renders them.
        if (!numbers().equals(numbers)) renderStripes();
    }

    /** The digits the stripes last showed, by panel id. */
    private Map<String, java.util.OptionalInt> numbers = Map.of();

    /** Each docked panel's stripe digit under the window's keymap now. */
    private Map<String, java.util.OptionalInt> numbers() {
        var current = new LinkedHashMap<String, java.util.OptionalInt>();
        for (PanelInstance panel : panels.values()) current.put(panel.entry.id(), number(panel));
        return current;
    }

    /**
     * The digit a panel's label shows: its toggle's under the window's keymap, on labelled stripes only. The icon rails
     * show no digit, so under them every number is empty and a rebind never re-renders (and so never cancels a drag).
     */
    private java.util.OptionalInt number(PanelInstance panel) {
        if (!(owner.stripes() instanceof StripeSet.LabelledStripes)) return java.util.OptionalInt.empty();
        return StripeNumbers.of(owner.bindings().strokeFor(panel.entry.toggleActionId()));
    }

    /** A panel's stripe label, as a drag's gap measures it. */
    private String stripeLabel(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null) return "";
        return StripeLabels.text(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), Slot.LEFT_TOP, false,
            panel.entry.badge(), number(panel)));
    }''', 1),
    ('''                windows.add(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id),
                    panel.entry.badge()));''',
     '''                windows.add(new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id),
                    panel.entry.badge(), number(panel)));''', 1),
    ('''        owner.bottomStrip().ifPresent(strip -> strip.render(windows, List.of()));''',
     '''        owner.bottomStrip().ifPresent(strip -> strip.render(windows, List.of()));
        numbers = numbers();''', 1)])
PY
```

`numbers` is assigned before the constructor's first `renderStripes` runs, as a field initializer; the private field and
the private method share a name on purpose (the field is what was shown, the method what the keymap says now).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.StripeNumbers*' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.workspace.WindowContributionsTest' --tests 'dev.jasper.app.workspace.ShortcutEditorIntegrationTest'`
Expected: PASS (`StripeNumbersTest` 2, `StripeNumbersWindowTest` 3; the rails' tests unchanged: under the rails every
number is empty, and a rebind does not re-render them).

- [ ] **Step 5: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
M = "jasper-app/src/main/java/dev/jasper/app/workspace/"; T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
rows = [
    f"{M}StripeNumbers.java,app.workspace,StripeNumbers,nonvisual-helper,none,none,{T}StripeNumbersTest.java,New in Enterprise plan 7 Task 7; the digit of a panel's toggle shortcut",
    f"{T}StripeNumbersTest.java,app.workspace,StripeNumbersTest,nonvisual-helper,none,none,{T}StripeNumbersTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 7; which strokes number a label",
    f"{T}StripeNumbersWindowTest.java,app.workspace,StripeNumbersWindowTest,nonvisual-helper,none,none,{T}StripeNumbersWindowTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 7; numbers follow the live keymap"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: stripe numbers from the live keymap

A panel whose toggle is bound to a digit with Ctrl, Alt or Meta reads
"N: Title" on a labelled stripe (spec 10.7); the window reads the digit
from its effective bindings and renders the stripes again when a rebind
changes one. The icon rails ignore it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 8: Flush frames: painted rules, drag areas and the focused frame

Spec 10.7: "`FrameStyle`: arc 0, gutter 0, a painted `DOCK_RULE` with a `DOCK_RULE_HIT` drag area, a header strip … on
`surface.header` (`surface.header.active` while focused)". The arc and gutter are already the window's kit's (Task 1;
0 in the classic catalog). Under flush frames the area lays its dividers out `DOCK_RULE` wide, paints them as rules,
grows each divider's handle to `DOCK_RULE_HIT` across, centred on its rule and above the frames, and tracks which frame
holds the permanent focus owner (Rulings 11, 12). Standard frames are unchanged.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java` (`Host`, `DividerHandle`,
  `DockLayout.layoutContainer`, the constructor, `sync`, `metrics`; new `paintComponent`, `addNotify`, `removeNotify`,
  `focusMoved`, `focusedSlot`), `ToolWindowHeader.java` (`setFocused`, `focused`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/FlushFramesTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `ToolWindowArea(…, Kit, boolean flush)`; Task 2's `DockGeometry.Metrics.at(scale, divider)`; Task
  4's `ChromePainter.dockRule`.
- Produces: `ToolWindowArea.focusMoved(Component)`, `Optional<Slot> focusedSlot()`; `ToolWindowHeader.setFocused(boolean)`,
  `boolean focused()`; the area's `Host.focusedHeader` hook, which Task 10 points at borrowed kit headers.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/FlushFramesTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.swing.ChromePainter;
import java.awt.Color;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: flush frames are divided by 1 px rules with 6 px drag areas, and the focused frame is known. */
@ExtendWith(EdtTestExtension.class)
class FlushFramesTest {
    private static ToolWindowArea area(boolean flush) {
        EnterpriseLook.install(false);
        var area = new ToolWindowArea(new JPanel(), new DockState(), EnterpriseLook.kit(), flush);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("top"), 300);
        DockTestSupport.show(area, Slot.LEFT_BOTTOM, new JLabel("bottom"), 300);
        DockTestSupport.show(area, Slot.RIGHT_TOP, new JLabel("right"), 350);
        return area;
    }

    private static Rectangle frame(ToolWindowArea area, Slot slot) { return area.content(slot).getParent().getBounds(); }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void flushFramesAreDividedByPaintedOnePixelRules() throws Exception {
        try {
            var area = area(true);
            var image = MockCrops.render(area, 1200, 763, 1);
            var rule = new ChromePainter(EnterpriseLook.kit()).dockRule(EnterpriseLook.theme(false));
            Rectangle top = frame(area, Slot.LEFT_TOP), bottom = frame(area, Slot.LEFT_BOTTOM);
            int x = top.x + top.width, y = top.y + top.height;
            assertThat(area.metrics().divider()).isEqualTo(1);
            assertThat(new Color(image.getRGB(x, 200))).as("the rule beside the left frames").isEqualTo(rule);
            assertThat(new Color(image.getRGB(x + 1, 200))).as("one pixel wide").isNotEqualTo(rule);
            assertThat(new Color(image.getRGB(100, y))).as("the rule between the left frames").isEqualTo(rule);
            assertThat(bottom.y).isEqualTo(y + 1);
            assertThat(top.x).as("no gutter in the classic catalog").isZero();
            JComponent handle = area.divider(DockGeometry.Handle.LEFT_WIDTH);
            assertThat(handle.getWidth()).as("a 6 px drag area").isEqualTo(6);
            assertThat(handle.getX()).isLessThan(x);
            assertThat(handle.getX() + handle.getWidth()).isGreaterThan(x + 1);
            assertThat(area.getComponentZOrder(handle)).as("above the frames").isLessThan(area.getComponentZOrder(area.content(Slot.LEFT_TOP).getParent()));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void draggingFromTheEdgeOfADragAreaMovesTheRuleByTheDrag() throws Exception {
        try {
            for (boolean flush : new boolean[] {true, false}) {
                var area = area(flush);
                MockCrops.render(area, 1200, 763, 1);
                JComponent handle = area.divider(DockGeometry.Handle.LEFT_WIDTH);
                mouse(handle, MouseEvent.MOUSE_PRESSED, 0, 100);
                mouse(handle, MouseEvent.MOUSE_DRAGGED, 40, 100);
                mouse(handle, MouseEvent.MOUSE_RELEASED, 40, 100);
                assertThat(area.state().size(Side.LEFT)).as(flush ? "flush" : "standard").isEqualTo(340);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void theFrameHoldingFocusIsTheFocusedOne() throws Exception {
        try {
            var area = area(true);
            MockCrops.render(area, 1200, 763, 1);
            area.focusMoved(area.content(Slot.LEFT_TOP));
            assertThat(area.focusedSlot()).hasValue(Slot.LEFT_TOP);
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isTrue();
            assertThat(area.headerOf(Slot.RIGHT_TOP).focused()).isFalse();
            area.focusMoved(area.content(Slot.RIGHT_TOP));
            assertThat(area.headerOf(Slot.LEFT_TOP).focused()).isFalse();
            assertThat(area.headerOf(Slot.RIGHT_TOP).focused()).isTrue();
            area.focusMoved(new JPanel());
            assertThat(area.focusedSlot()).as("focus in the terminal: no frame").isEmpty();
            var standard = area(false);
            standard.focusMoved(standard.content(Slot.LEFT_TOP));
            assertThat(standard.focusedSlot()).as("standard frames track no focus").isEmpty();
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void standardFramesKeepTheirSixPixelGapsAndPaintNoRule() throws Exception {
        try {
            var area = area(false);
            var image = MockCrops.render(area, 1200, 763, 1);
            Rectangle top = frame(area, Slot.LEFT_TOP), right = frame(area, Slot.RIGHT_TOP);
            assertThat(area.metrics().divider()).isEqualTo(6);
            assertThat(area.divider(DockGeometry.Handle.LEFT_WIDTH).getWidth()).isEqualTo(6);
            var app = EnterpriseLook.theme(false).semantic().color(dev.jasper.ui.theme.SemanticColor.SURFACE_APP);
            assertThat(new Color(image.getRGB(top.x + top.width + 3, 200))).as("the gap shows the window surface").isEqualTo(app);
            assertThat(right.x).as("a 6 px gap before the right frame").isGreaterThanOrEqualTo(top.x + top.width + 6);
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
```

In `DockMockTest`, insert before `    // Later tasks add their surfaces above this line.`:

```java
    /** The 1 px rules between flush frames against the mocks' rules at 2x: the colour and the two-pixel width. */
    @Test void theRulesBetweenFramesMatchTheMocks() throws Exception {
        for (Variant variant : VARIANTS) {
            try {
                EnterpriseLook.install(variant.dark());
                var area = new ToolWindowArea(new javax.swing.JPanel(), new dev.jasper.app.dock.DockState(), EnterpriseLook.kit(), true);
                DockTestSupport.show(area, Slot.LEFT_TOP, new javax.swing.JLabel("SFTP"), 300);
                DockTestSupport.show(area, Slot.LEFT_BOTTOM, new javax.swing.JLabel("File Servers"), 300);
                var render = variant.seen(MockCrops.render(area, 1000, 763, 2));
                var mock = variant.crop(0, 0, 2884, 1804);
                String where = variant.name() + " rules";
                // The mock's left rule is at x 648-649 and its rule between the left frames at y 942-943 (2x).
                Color theirs = MockCrops.at(mock, 648, 1000);
                assertThat(MockCrops.near(MockCrops.at(mock, 649, 1000), theirs, 0)).isTrue();
                assertThat(MockCrops.near(MockCrops.at(mock, 500, 942), theirs, 3)).isTrue();
                java.awt.Rectangle top = area.content(Slot.LEFT_TOP).getParent().getBounds();
                int x = 2 * (top.x + top.width), y = 2 * (top.y + top.height);
                assertThat(MockCrops.near(MockCrops.at(render, x, 600), theirs, variant.fillTolerance())).as(where + ": the colour").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 1, 600), theirs, variant.fillTolerance())).as(where + ": two pixels at 2x").isTrue();
                assertThat(MockCrops.near(MockCrops.at(render, x + 2, 600), theirs, variant.fillTolerance())).as(where + ": not three").isFalse();
                assertThat(MockCrops.near(MockCrops.at(render, 200, y), theirs, variant.fillTolerance())).as(where + ": between the left frames").isTrue();
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FlushFramesTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `focusMoved`, `focusedSlot` and `focused()`.

- [ ] **Step 3: Write the rules, the drag areas and the focus tracking**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 8 step 3: flush frames (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
W = "jasper-app/src/main/java/dev/jasper/app/workspace/"
edit(W + "ToolWindowArea.java", [
    ('''        /** Shows per-window header metadata on this frame's header, native or borrowed; inert once released. */
        private Consumer<dev.jasper.app.contributions.PanelHeaderState> header = state -> { };''',
     '''        /** Shows per-window header metadata on this frame's header, native or borrowed; inert once released. */
        private Consumer<dev.jasper.app.contributions.PanelHeaderState> header = state -> { };
        /** Whether this frame holds the focus owner (flush frames only). */
        private boolean focused;
        /** Shows the focus on this frame's header strip, native or borrowed; inert once released. */
        private Consumer<Boolean> focusedHeader = value -> { };

        void focused(boolean value) {
            if (focused == value) return;
            focused = value;
            focusedHeader.accept(value);
        }''', 1),
    ('''        /** Whether a drag event arrived since the press; a plain click must not rewrite a squeezed size. */
        private boolean moved;
        private int grab;''',
     '''        /** Whether a drag event arrived since the press; a plain click must not rewrite a squeezed size. */
        private boolean moved;
        /** Where in the handle the press was, less how far the divider's leading edge lies into the handle. */
        private int grab;''', 1),
    ('''                    dragging = placed;
                    moved = false;
                    grab = handle.vertical() ? event.getX() : event.getY();''',
     '''                    dragging = placed;
                    moved = false;
                    // A flush rule's drag area is wider than the rule: the rule's leading edge lies inside it.
                    int offset = handle.vertical() ? placed.bounds().x - getX() : placed.bounds().y - getY();
                    grab = (handle.vertical() ? event.getX() : event.getY()) - offset;''', 1),
    ('''                divider.setBounds(divider.placed == null ? new Rectangle() : divider.placed.bounds());''',
     '''                divider.setBounds(divider.placed == null ? new Rectangle() : hitArea(divider.placed));''', 1),
    ('''        for (DockGeometry.Handle handle : DockGeometry.Handle.values()) {
            var divider = new DividerHandle(handle);
            dividers.put(handle, divider);
            add(divider);
        }''',
     '''        for (DockGeometry.Handle handle : DockGeometry.Handle.values()) {
            var divider = new DividerHandle(handle);
            dividers.put(handle, divider);
            add(divider);
        }
        // A flush rule's drag area overlaps the frames beside it: the handles stay above them.
        if (flush) for (DividerHandle divider : dividers.values()) setComponentZOrder(divider, 0);''', 1),
    ('''        refreshGutters();
        revalidate(); repaint();
        onMinimumSizeChanged.run();
    }''',
     '''        refreshGutters();
        revalidate(); repaint();
        onMinimumSizeChanged.run();
        if (flush) focusMoved(java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().getPermanentFocusOwner());
    }

    /** The frame holding the permanent focus owner, under flush frames. */
    private Slot focused;
    /** Follows the permanent focus owner while the area is showing, under flush frames. */
    private final java.beans.PropertyChangeListener focusListener = event -> focusMoved(event.getNewValue() instanceof Component owner ? owner : null);

    @Override public void addNotify() {
        super.addNotify();
        if (flush) java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().addPropertyChangeListener("permanentFocusOwner", focusListener);
    }

    @Override public void removeNotify() {
        if (flush) java.awt.KeyboardFocusManager.getCurrentKeyboardFocusManager().removePropertyChangeListener("permanentFocusOwner", focusListener);
        super.removeNotify();
    }

    /** Spec 10.7: the frame holding {@code owner} shows its header strip as focused, the others not; standard frames ignore it. */
    void focusMoved(Component owner) {
        if (!flush) return;
        Slot now = null;
        for (var entry : hosts.entrySet())
            if (owner != null && SwingUtilities.isDescendingFrom(owner, entry.getValue())) now = entry.getKey();
        focused = now;
        for (var entry : hosts.entrySet()) entry.getValue().focused(entry.getKey() == focused);
    }

    /** The frame showing its header strip as focused, if any. */
    java.util.Optional<Slot> focusedSlot() { return java.util.Optional.ofNullable(focused); }

    /** A divider's drag area: the divider itself, or under flush frames {@code DOCK_RULE_HIT} across, centred on its rule. */
    private Rectangle hitArea(DockGeometry.Divider divider) {
        Rectangle bounds = divider.bounds();
        if (!flush) return bounds;
        int hit = UIScale.scale(kit.metrics().value(DesignMetrics.Role.DOCK_RULE_HIT));
        int grow = Math.max(0, hit - (divider.handle().vertical() ? bounds.width : bounds.height));
        if (divider.handle().vertical()) { bounds.x -= grow / 2; bounds.width += grow; }
        else { bounds.y -= grow / 2; bounds.height += grow; }
        return bounds;
    }

    /** Under flush frames, each laid out divider is a painted rule (spec 10.7). */
    @Override protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        if (!flush) return;
        graphics.setColor(new dev.jasper.ui.swing.ChromePainter(kit).dockRule(ThemeZone.theme(this)));
        for (DividerHandle divider : dividers.values()) {
            if (divider.placed == null) continue;
            Rectangle rule = divider.placed.bounds();
            graphics.fillRect(rule.x, rule.y, rule.width, rule.height);
        }
    }''', 1),
    ('''    DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }''',
     '''    DockGeometry.Metrics metrics() {
        double scale = UIScale.getUserScaleFactor();
        return flush ? DockGeometry.Metrics.at(scale, kit.metrics().value(DesignMetrics.Role.DOCK_RULE)) : DockGeometry.Metrics.at(scale);
    }''', 1),
    ('''                    ToolWindowHeader header = header(id, wanted.title());
                    host.add(header, BorderLayout.NORTH);
                    var surfaces = header.dragSurfaces();
                    for (JComponent surface : surfaces) dragSource.accept(surface, id);
                    host.header = header::presentation;
                    Host mounted = host;
                    host.releaseHeader = () -> { mounted.header = state -> { }; surfaces.forEach(releaseDragSource); };''',
     '''                    ToolWindowHeader header = header(id, wanted.title());
                    host.add(header, BorderLayout.NORTH);
                    var surfaces = header.dragSurfaces();
                    for (JComponent surface : surfaces) dragSource.accept(surface, id);
                    host.header = header::presentation;
                    host.focusedHeader = header::setFocused;
                    Host mounted = host;
                    host.releaseHeader = () -> { mounted.header = state -> { }; mounted.focusedHeader = value -> { }; surfaces.forEach(releaseDragSource); };''', 1)])
edit(W + "ToolWindowHeader.java", [
    ('''    void presentation(dev.jasper.app.contributions.PanelHeaderState state) {''',
     '''    /** Whether this header strip shows its frame as focused (flush frames, spec 10.7). */
    private boolean focused;

    /** Shows the frame as focused or not; only a flush header strip paints it. */
    void setFocused(boolean value) {
        if (focused == value) return;
        focused = value;
        repaint();
    }

    boolean focused() { return focused; }

    void presentation(dev.jasper.app.contributions.PanelHeaderState state) {''', 1)])
PY
```

Hosts are appended after the handles (`add(host)` in `sync`), so they lie below the handles in the z-order already;
only `center`, added first, needs the handles moved above it. The area's own imports already include `Graphics`,
`Rectangle`, `Component`, `SwingUtilities` and `UIScale`.

- [ ] **Step 4: Run the tests and the pixel check**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.FlushFramesTest' --tests 'dev.jasper.app.workspace.DockMockTest' --tests 'dev.jasper.app.workspace.ToolWindow*' --tests 'dev.jasper.app.workspace.DockDrag*' --tests 'dev.jasper.app.workspace.SidePanelRenderTest' --tests 'dev.jasper.app.workspace.WorkspaceGlassTest'`
Expected: PASS (`FlushFramesTest` 4, `DockMockTest` 4 over three variants). Then the pixel check (Task 1 Step 1's
`DefaultDockPixelsTest` and `cmp` lines; the gallery is untouched by this task): `PIXELS-UNCHANGED`.

- [ ] **Step 5: Add the ledger row and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
row = f"{T}FlushFramesTest.java,app.workspace,FlushFramesTest,nonvisual-helper,none,none,{T}FlushFramesTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 8; rules; drag areas and the focused frame"
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + row + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: flush frames with rules, drag areas and the focused frame

Under flush frames the area lays its dividers out as 1 px rules, paints
them in border.window (held on surface.app), grows each handle to a 6 px
drag area above the frames, and tracks the frame that holds the focus owner,
whose header strip shows as focused. Standard frames are unchanged.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 9: The native header strip, and the Workstation dock fixture

Spec 10.7: "a header strip of `tool_window_header` height on `surface.header` (`surface.header.active` while focused),
the hide glyph "−". A header detail is muted, and a digits-only detail is parenthesised ("Sessions (2)")." A native
`ToolWindowHeader` under flush frames paints that strip with its 1 px rule (Rulings 13 to 15). `DockFixture` builds the
Workstation mocks' dock (stripes, flush frames, the four panels in the mocks' places and sizes, the bottom labels) for
this and the later comparisons; here its native-header variant is compared with the mocks' four header strips.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java` (title and detail colours, the
  constructor, `presentation`, `getBackground`, sizes; new `paintComponent`)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/HeaderStripTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/DockFixture.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: Task 1's `ToolWindowHeader(…, Kit, boolean flush)`; Task 4's `ChromePainter.headerSurface`, `headerTitle`,
  `headerDetail`, `headerStrip`, `headerDot` and the roles `TOOL_HEADER_ACTION`, `HEADER_STRIP_TITLE_GAP`,
  `HEADER_STRIP_DOT_GAP`; Task 6's `StripeSet.LabelledStripes`; Task 8's `focused()`, `focusMoved`.
- Produces: `DockFixture(Kit kit, GeneratedTheme theme, boolean connected, boolean kitHeaders)` with `body`, `area`,
  `stripes`, `state`, `render(int scale)`, `focus(String id)`, `header(String id)` (a header's bounds in the body) and
  `close()`; `DockFixture.WIDTH`, `AREA_HEIGHT`, `LEFT`, `RIGHT`; `DockMockTest.compareHeader(Variant, String,
  BufferedImage, Rectangle, int mockX, int mockY, int mockWidth)` and `DockMockTest.LIGHT_DISCONNECTED`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/HeaderStripTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.swing.ChromePainter;
import dev.jasper.ui.theme.SemanticColor;
import java.awt.Color;
import java.util.List;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: a native tool window header under flush frames is a header strip. */
@ExtendWith(EdtTestExtension.class)
class HeaderStripTest {
    private static ToolWindowHeader header(boolean flush) {
        EnterpriseLook.install(false);
        return new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { }, EnterpriseLook.kit(), flush);
    }

    private static int metric(DesignMetrics.Role role) { return com.formdev.flatlaf.util.UIScale.scale(EnterpriseLook.kit().metrics().value(role)); }

    @Test void aFlushHeaderIsAStripOverItsRuleThatFollowsFocus() throws Exception {
        try {
            var header = header(true);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            assertThat(header.getPreferredSize().height).as("24 and its rule").isEqualTo(metric(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + metric(DesignMetrics.Role.DOCK_RULE));
            var image = MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            assertThat(new Color(image.getRGB(150, 5))).isEqualTo(painter.headerSurface(theme, false));
            assertThat(new Color(image.getRGB(150, image.getHeight() - 1))).as("the rule").isEqualTo(painter.dockRule(theme));
            header.setFocused(true);
            image = MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            assertThat(new Color(image.getRGB(150, 5))).as("focused").isEqualTo(painter.headerSurface(theme, true));
            assertThat(header.titleLabel().getForeground()).isEqualTo(painter.headerTitle(theme, true));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aFlushHeadersDetailIsMutedAndADigitsOnlyOneParenthesised() throws Exception {
        try {
            var header = header(true);
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            header.presentation(new PanelHeaderState("2", false, List.of()));
            var detail = (javax.swing.JLabel) header.dragSurfaces().get(2);
            assertThat(detail.getText()).isEqualTo("(2)");
            assertThat(detail.getForeground()).isEqualTo(painter.headerDetail(theme, false, true));
            header.presentation(new PanelHeaderState("Mac", true, List.of()));
            assertThat(detail.getText()).isEqualTo("Mac");
            assertThat(detail.getForeground()).isEqualTo(painter.headerDetail(theme, false, false));
            assertThat(detail.getIconTextGap()).isEqualTo(metric(DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
        } finally { EnterpriseLook.restoreDefault(); }
    }

    @Test void aFlushHeadersActionsAreSixteenPixelSquaresAndHideIsTheMinus() throws Exception {
        try {
            var header = header(true);
            header.presentation(new PanelHeaderState("", false, List.of(new PanelHeaderState.Action("Add", AppIcons.semantic("add"), () -> { }))));
            int side = metric(DesignMetrics.Role.TOOL_HEADER_ACTION);
            assertThat(header.optionsButton().getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(header.hideButton().getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(header.hideButton().getIcon()).isSameAs(AppIcons.semantic("remove"));
            MockCrops.render(header, 300, header.getPreferredSize().height, 1);
            var add = SwingFind.button(header, "Add");
            assertThat(add.getPreferredSize()).isEqualTo(new java.awt.Dimension(side, side));
            assertThat(((java.awt.FlowLayout) add.getParent().getLayout()).getHgap()).as("2 apart, 18 between centres").isEqualTo(2);
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * Spec 16.1.2: a flush header, focused and not, paints its title, a word detail and a digits-only detail in their
     * declared pairs ({@code ChromePainter.DOCK_PAIRS} 5 to 10), sampled from the 4x render inside each label, over every
     * palette.
     */
    @Test void aFlushHeaderPaintsItsTextInItsDeclaredPairsOverEveryPalette() throws Exception {
        var palettes = new java.util.ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
        palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
        var pairs = ChromePainter.DOCK_PAIRS;
        try {
            for (var theme : palettes) for (boolean focused : List.of(false, true)) {
                dev.jasper.ui.swing.FlatLafInstaller.install(theme, EnterpriseLook.kit());
                var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { }, EnterpriseLook.kit(), true);
                header.setFocused(focused);
                String where = theme.name() + (theme.dark() ? " Dark" : " Light") + (focused ? ", focused" : "");
                var detail = (javax.swing.JLabel) header.dragSurfaces().get(2);
                header.presentation(new PanelHeaderState("Mac", false, List.of()));
                var image = MockCrops.render(header, 300, header.getPreferredSize().height, 4);
                assertInk(image, header, header.titleLabel(), pairs.get(focused ? 6 : 5), theme, where + " title");
                assertInk(image, header, detail, pairs.get(focused ? 8 : 7), theme, where + " detail");
                header.presentation(new PanelHeaderState("2", false, List.of()));
                image = MockCrops.render(header, 300, header.getPreferredSize().height, 4);
                assertInk(image, header, detail, pairs.get(focused ? 10 : 9), theme, where + " count");
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Asserts that {@code label}'s bounds in {@code image}, a 4x render of {@code header}, hold the pair's painted colour. */
    private static void assertInk(java.awt.image.BufferedImage image, ToolWindowHeader header, javax.swing.JComponent label,
                                  dev.jasper.ui.theme.PaintPair pair, dev.jasper.ui.theme.GeneratedTheme theme, String what) {
        Color expected = dev.jasper.ui.theme.SurfaceContrast.painted(theme, pair);
        var bounds = javax.swing.SwingUtilities.convertRectangle(label.getParent(), label.getBounds(), header);
        assertThat(LabelledStripeTest.inks(image, bounds, 4, expected)).as(what + " " + dev.jasper.ui.theme.ColorMath.hex(expected)).isTrue();
    }

    @Test void aStandardHeaderIsUnchanged() throws Exception {
        try {
            ThemeTestSupport.install(true);
            var header = new ToolWindowHeader("Sessions", JPopupMenu::new, () -> { });
            assertThat(header.getBackground()).isEqualTo(ThemeTestSupport.color(true, SemanticColor.SURFACE_PANEL));
            assertThat(header.hideButton().getIcon()).isSameAs(AppIcons.semantic("collapse"));
            header.presentation(new PanelHeaderState("2", false, List.of()));
            assertThat(((javax.swing.JLabel) header.dragSurfaces().get(2)).getText()).isEqualTo("2");
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /** Finds a header's contributed button by its tooltip. */
    private static final class SwingFind {
        static javax.swing.JButton button(java.awt.Container root, String tip) {
            for (var child : root.getComponents()) {
                if (child instanceof javax.swing.JButton button && tip.equals(button.getToolTipText())) return button;
                if (child instanceof java.awt.Container nested) { var found = button(nested, tip); if (found != null) return found; }
            }
            return null;
        }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockFixture.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.PanelBadge;
import dev.jasper.app.contributions.PanelHeaderState;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.icons.IconRegistry;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.swing.KitContext;
import dev.jasper.ui.swing.KitView;
import dev.jasper.ui.theme.GeneratedTheme;
import dev.jasper.ui.theme.SemanticColor;
import dev.jasper.ui.theme.ThemeBinding;
import dev.jasper.ui.theme.ThemePair;
import dev.jasper.ui.theme.ThemeRoute;
import java.awt.BorderLayout;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

/**
 * The Workstation mocks' dock as a headless fixture (spec 16.1.4): labelled stripes, flush frames, and the four panels in
 * the mocks' places and sizes with the mocks' headers ("SFTP ● Mac", "File Servers (1)", "Sessions (2)", "Tunnels (1)"
 * when connected), with native headers or borrowed kit headers, and the bottom strip's three closed labels. The panels'
 * bodies are blank: their compositions are plan 10's. Titles, numbers and counts are fixtures. EDT only.
 */
final class DockFixture implements AutoCloseable {
    /** The mocks' window content width, the dock area's height, and the left and right frames' widths, in logical pixels. */
    static final int WIDTH = 1440, AREA_HEIGHT = 763, LEFT = 300, RIGHT = 350;
    /** The mocks' splits: the left frames 381 and 381 high, the right 416 and 346, around a 1 px rule. */
    static final double LEFT_RATIO = 0.5, RIGHT_RATIO = 416.0 / 762;

    /**
     * One panel of the mocks.
     *
     * @param id its id
     * @param title its title
     * @param slot where the mock docks it
     * @param number its stripe digit, 0 for none
     * @param count its header count, 0 for none
     * @param detail its header detail
     * @param connected whether its header shows the connected dot
     * @param actions the icon names of its own header actions
     */
    record Panel(String id, String title, Slot slot, int number, int count, String detail, boolean connected, List<String> actions) { }

    final DockState state = new DockState();
    final StripeSet.LabelledStripes stripes;
    final ToolWindowArea area;
    final JPanel body = new JPanel(new BorderLayout());
    private final boolean kitHeaders;
    private final Map<String, KitView> views = new LinkedHashMap<>();
    private final ThemeBinding binding;
    private final IconRegistry icons = new IconRegistry();
    private final List<Panel> panels;

    /** The mocks' panels; the Disconnected mock drops the SFTP detail and the File Servers and Tunnels counts. */
    static List<Panel> panels(boolean connected) {
        return List.of(
            new Panel("sftp", "SFTP", Slot.LEFT_TOP, 1, 0, connected ? "Mac" : "", connected, List.of()),
            new Panel("servers", "File Servers", Slot.LEFT_BOTTOM, 2, connected ? 1 : 0, "", false, List.of("add")),
            new Panel("sessions", "Sessions", Slot.RIGHT_TOP, 0, 2, "", false, List.of("add", "import")),
            new Panel("tunnels", "Tunnels", Slot.RIGHT_BOTTOM, 0, connected ? 1 : 0, "", false, List.of("add")));
    }

    DockFixture(Kit kit, GeneratedTheme theme, boolean connected, boolean kitHeaders) {
        this.kitHeaders = kitHeaders;
        this.panels = panels(connected);
        this.binding = new ThemeBinding(new ThemePair(theme, theme), failure -> { throw new AssertionError(failure); });
        var context = KitContext.of(kit, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
        stripes = new StripeSet.LabelledStripes(kit);
        var terminal = new JPanel();
        terminal.setBackground(theme.semantic().color(SemanticColor.SURFACE_TERMINAL));
        area = new ToolWindowArea(terminal, state, kit, true);
        body.add(stripes.left(), BorderLayout.WEST);
        body.add(area, BorderLayout.CENTER);
        body.add(stripes.right(), BorderLayout.EAST);
        body.add(stripes.bottomStrip(), BorderLayout.SOUTH);
        state.resize(Side.LEFT, LEFT);
        state.resize(Side.RIGHT, RIGHT);
        state.setRatio(Side.LEFT, LEFT_RATIO);
        state.setRatio(Side.RIGHT, RIGHT_RATIO);
        for (Panel panel : panels) {
            JComponent content;
            if (kitHeaders) {
                var view = new KitView(context, spec(panel), event -> { });
                views.put(panel.id(), view);
                content = view;
            } else {
                var plain = new JPanel();
                plain.setBackground(theme.semantic().color(SemanticColor.SURFACE_PANEL));
                content = plain;
            }
            area.setContent(panel.id(), panel.title(), content);
            state.add(panel.id(), panel.slot());
            state.show(panel.id());
            area.setHeader(panel.id(), header(panel));
        }
        for (String id : List.of("log", "transfers", "problems")) state.add(id, Slot.BOTTOM_LEFT);
        area.sync();
        var buttons = new ArrayList<ToolStripe.Button>();
        for (Panel panel : panels)
            buttons.add(new ToolStripe.Button(panel.id(), panel.title(), null, panel.slot(), true, Optional.empty(),
                panel.number() == 0 ? OptionalInt.empty() : OptionalInt.of(panel.number())));
        buttons.add(new ToolStripe.Button("log", "Event Log", null, Slot.BOTTOM_LEFT, false));
        buttons.add(new ToolStripe.Button("transfers", "Transfers", null, Slot.BOTTOM_LEFT, false));
        buttons.add(new ToolStripe.Button("problems", "Problems", null, Slot.BOTTOM_LEFT, false,
            Optional.of(new PanelBadge(1, PanelBadge.Tone.ERROR, "1 problem")), OptionalInt.empty()));
        for (DockStripe stripe : stripes.all()) stripe.render(buttons, List.of());
    }

    /**
     * A panel's borrowed kit header: the title and the count, as Remote's and File Server's panels build theirs (their own
     * actions reach the header through the host header, {@link #header(Panel)}).
     */
    private static ViewSpec spec(Panel panel) {
        var header = new Nodes.Header(panel.id() + ".header", ControlState.READY, panel.title(),
            panel.count() == 0 ? OptionalInt.empty() : OptionalInt.of(panel.count()), Optional.empty(), Optional.empty(), List.of());
        return new ViewSpec(new Nodes.Panel(panel.id(), ControlState.READY, header, Optional.empty(),
            new Nodes.Text(panel.id() + ".body", ControlState.READY, panel.title(), Nodes.TextRole.BODY), Optional.empty()));
    }

    /**
     * A panel's host header metadata: the detail, the dot and its own actions (as Remote's panels publish theirs through
     * {@code PanelHost.setHeader}), and, for a native header, the count as a digits-only detail.
     */
    private PanelHeaderState header(Panel panel) {
        String detail = !kitHeaders && panel.count() > 0 ? Integer.toString(panel.count()) : panel.detail();
        return new PanelHeaderState(detail, panel.connected(), panel.actions().stream()
            .map(icon -> new PanelHeaderState.Action(icon, AppIcons.semantic(icon), () -> { })).toList());
    }

    /** The body at the mocks' size: the dock area 763 high above the bottom strip. */
    BufferedImage render(int scale) {
        return MockCrops.render(body, WIDTH, AREA_HEIGHT + stripes.bottomStrip().getPreferredSize().height, scale);
    }

    /** Puts the focus owner in a panel, as clicking in it would. */
    void focus(String id) {
        Panel panel = panels.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
        area.focusMoved(area.content(panel.slot()));
    }

    /** A panel's header strip in the body's coordinates: its borrowed kit header or its native header. */
    Rectangle header(String id) {
        Panel panel = panels.stream().filter(each -> each.id().equals(id)).findFirst().orElseThrow();
        JComponent header = kitHeaders ? views.get(id).panelHeader(id) : area.headerOf(panel.slot());
        return SwingUtilities.convertRectangle(header.getParent(), header.getBounds(), body);
    }

    @Override public void close() {
        views.values().forEach(KitView::close);
        area.close();
        icons.close();
        binding.close();
    }
}
```

In `DockMockTest`, add the Disconnected variant after `VAULT`, and insert the comparison before
`    // Later tasks add their surfaces above this line.`:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java"); text = path.read_text(encoding="utf-8")
old = '''    static final List<Variant> VARIANTS = List.of(LIGHT, DARK, VAULT);'''
new = '''    static final List<Variant> VARIANTS = List.of(LIGHT, DARK, VAULT);
    /** The Disconnected Workstation mock (the Unlock Vault mock's window). */
    static final Variant LIGHT_DISCONNECTED = new Variant("light-disconnected", MockCrops.DISCONNECTED, false, 0, 0);
    /** Every mock with the connected or disconnected panels it shows. */
    static final List<Map.Entry<Variant, Boolean>> WORKSTATIONS = List.of(Map.entry(LIGHT, true), Map.entry(LIGHT_DISCONNECTED, false),
        Map.entry(DARK, true), Map.entry(VAULT, false));
    /** The header strips' agreement floor over their text, left of their icons. */
    static final double HEADER_FLOOR = 0.94;'''
if text.count(old) != 1: raise SystemExit("DockMockTest: VARIANTS moved")
text = text.replace(old, new).replace("import java.util.List;", "import java.util.List;\nimport java.util.Map;\nimport java.awt.Rectangle;", 1)
anchor = "    // Later tasks add their surfaces above this line."
block = '''    /** The four native header strips (flush frames) against the mocks', the focused one active in its own render. */
    @Test void nativeHeaderStripsMatchTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) compareHeaders(workstation.getKey(), workstation.getValue(), false);
    }

    /** One fixture per focused frame: SFTP's render for SFTP, File Servers and Tunnels; Sessions' for Sessions. */
    static void compareHeaders(Variant variant, boolean connected, boolean kitHeaders) throws Exception {
        try {
            EnterpriseLook.install(variant.dark());
            String kind = kitHeaders ? "kit" : "native";
            try (var fixture = new DockFixture(EnterpriseLook.kit(), EnterpriseLook.theme(variant.dark()), connected, kitHeaders)) {
                fixture.render(1);
                fixture.focus("sftp");
                var render = variant.seen(fixture.render(2));
                compareHeader(variant, kind + "-sftp-header", render, fixture.header("sftp"), 48, 180, 600);
                compareHeader(variant, kind + "-servers-header", render, fixture.header("servers"), 48, 944, 600);
                compareHeader(variant, kind + "-tunnels-header", render, fixture.header("tunnels"), 2136, 1014, 700);
                fixture.focus("sessions");
                render = variant.seen(fixture.render(2));
                compareHeader(variant, kind + "-sessions-header", render, fixture.header("sessions"), 2136, 180, 700);
            }
        } finally { EnterpriseLook.restoreDefault(); }
    }

    /**
     * A header strip against its mock crop (2x, its 24 px strip and its rule): the surface and the rule; the ink runs
     * along it, one per title, dot, detail and icon, the title's start within 2 px, each icon's centre within 2 (the icons
     * stand from the trailing edge, whatever the face) and each text run's ink; the other text edges (a text run's end
     * within 3, a later run's start within 2) and the agreeing pixels of the text, left of the icons, follow the title's
     * advance width and glyphs and are printed until plan 5b's Plex (Execution notes R5).
     */
    static void compareHeader(Variant variant, String name, BufferedImage render, Rectangle header, int mockX, int mockY, int mockWidth) {
        String where = variant.name() + " " + name;
        assertThat(2 * header.width).as(where + ": the frame's width").isEqualTo(mockWidth);
        assertThat(2 * header.height).as(where + ": 24 and the rule").isEqualTo(50);
        var mock = variant.crop(mockX, mockY, mockWidth, 50);
        var mine = MockCrops.crop(render, 2 * header.x, 2 * header.y, mockWidth, 50);
        MockCrops.write(variant.name() + "-" + name, mine, mock);
        Color surface = MockCrops.at(mock, 4, 4), rule = MockCrops.at(mock, 4, 49);
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 4), surface, variant.fillTolerance())).as(where + ": the strip").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 48), rule, variant.fillTolerance())).as(where + ": the rule").isTrue();
        assertThat(MockCrops.near(MockCrops.at(mine, 4, 49), rule, variant.fillTolerance())).as(where + ": the rule, 1 px").isTrue();
        var theirs = MockCrops.runs(MockCrops.crop(mock, 0, 0, mockWidth, 48), false, 60, 10, surface);
        var ours = MockCrops.runs(MockCrops.crop(mine, 0, 0, mockWidth, 48), false, 60, 10, surface);
        assertThat(ours).as(where + ": one run per title, dot, detail and icon").hasSameSizeAs(theirs);
        int firstIcon = mockWidth;
        for (int i = 0; i < theirs.size(); i++) {
            var mockRun = theirs.get(i);
            var run = ours.get(i);
            boolean icon = mockRun.start() > mockWidth * 0.6;
            if (icon) {
                firstIcon = Math.min(firstIcon, mockRun.start());
                assertThat((run.start() + run.end()) / 2.0).as(where + " icon " + i + " centre")
                    .isBetween((mockRun.start() + mockRun.end()) / 2.0 - 2, (mockRun.start() + mockRun.end()) / 2.0 + 2);
            } else {
                if (i == 0) assertThat(run.start()).as(where + " title starts").isBetween(mockRun.start() - 2, mockRun.start() + 2);
                else MockProfile.fonts(where + " run " + i + " start", run.start(), mockRun.start(), 2);
                MockProfile.fonts(where + " run " + i + " end", run.end(), mockRun.end(), 3);
                assertThat(MockCrops.near(run.ink(), mockRun.ink(), 24)).as(where + " run " + i + " ink").isTrue();
            }
        }
        int text = Math.max(8, firstIcon - 8);
        MockCrops.fontsFloor(where + ": agreeing pixels",
            MockCrops.agreement(MockCrops.crop(mine, 0, 0, text, 50), MockCrops.crop(mock, 0, 0, text, 50), 24), HEADER_FLOOR);
    }

'''
if text.count(anchor) != 1: raise SystemExit("DockMockTest: the closing marker moved")
path.write_text(text.replace(anchor, block + anchor), encoding="utf-8")
PY
```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.HeaderStripTest' --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: FAIL: `HeaderStripTest` reports the header 24 tall without its rule ("24 and its rule"), the detail "2" for
"(2)", the actions' size and the detail's colour; `DockMockTest.nativeHeaderStripsMatchTheMocks` fails on the strip's
height ("24 and the rule").

- [ ] **Step 3: Paint the native header strip**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 9 step 3: the native header strip (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java", [
    ('''            if (font != null) setFont(ThemeZone.font(Typography.Role.PANEL_TITLE));
        }
    };''',
     '''            if (font != null) setFont(ThemeZone.font(Typography.Role.PANEL_TITLE));
        }

        /** A header strip's title is held readable on the strip, focused or not. */
        @Override public java.awt.Color getForeground() {
            return flush && painter != null ? painter.headerTitle(ThemeZone.theme(this), focused) : super.getForeground();
        }
    };''', 1),
    ('''        @Override public java.awt.Color getForeground() { return ThemeZone.color(this, SemanticColor.TEXT_TERTIARY); }''',
     '''        @Override public java.awt.Color getForeground() {
            // A header strip's detail is muted, and a count (a parenthesised number) more so (spec 10.7).
            if (flush && painter != null) return painter.headerDetail(ThemeZone.theme(this), focused, presentation.detail().matches("\\\\d+"));
            return ThemeZone.color(this, SemanticColor.TEXT_TERTIARY);
        }''', 1),
    ('''        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, metrics.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING), 0, metrics.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));''',
     '''        setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(0, metrics.value(DesignMetrics.Role.TOOL_HEADER_LEADING_PADDING),
            flush ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 0, metrics.value(DesignMetrics.Role.TOOL_HEADER_TRAILING_PADDING)), null));''', 1),
    ('''                int x = titleWidth + styles.metric(DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);''',
     '''                int x = titleWidth + styles.metric(flush ? DesignMetrics.Role.HEADER_STRIP_TITLE_GAP : DesignMetrics.Role.TOOL_HEADER_DETAIL_GAP);''', 1),
    ('''        labels.setOpaque(false);
        labels.add(title); labels.add(detail);
        add(labels, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
    }''',
     '''        labels.setOpaque(false);
        labels.add(title); labels.add(detail);
        add(labels, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
        if (flush) {
            // A header strip's hide glyph is the minus, and its actions are 16 px squares 2 apart (Rulings 14 and 15).
            hide.setIcon(AppIcons.semantic("remove"));
            for (JButton button : java.util.List.of(options, hide)) actionSize(button);
            contributed.setLayout(new java.awt.FlowLayout(java.awt.FlowLayout.LEADING, 2, 0));
            detail.setIconTextGap(styles.metric(DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
        }
    }

    /** A header strip action's square. */
    private void actionSize(JButton button) {
        int side = styles.metric(DesignMetrics.Role.TOOL_HEADER_ACTION);
        var size = new Dimension(side, side);
        button.setPreferredSize(size);
        button.setMinimumSize(size);
        button.setMaximumSize(size);
    }''', 1),
    ('''    @Override public java.awt.Color getBackground() { return ThemeZone.color(this, SemanticColor.SURFACE_PANEL); }''',
     '''    @Override public java.awt.Color getBackground() {
        return flush && painter != null ? painter.headerSurface(ThemeZone.theme(this), focused) : ThemeZone.color(this, SemanticColor.SURFACE_PANEL);
    }

    /** A header strip paints its surface and the 1 px rule along its bottom (spec 10.7). */
    @Override protected void paintComponent(java.awt.Graphics graphics) {
        if (!flush) { super.paintComponent(graphics); return; }
        painter.headerStrip(graphics, getWidth(), getHeight(), ThemeZone.theme(this), focused);
    }''', 1),
    ('''                painter.connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));''',
     '''                if (flush) painter.headerDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this), focused);
                else painter.connectedDot(graphics, x, y, getIconWidth(), ThemeZone.theme(ToolWindowHeader.this));''', 1),
    ('''        detail.setText(state.detail());''',
     '''        detail.setText(flush && state.detail().matches("\\\\d+") ? "(" + state.detail() + ")" : state.detail());''', 1),
    ('''        @Override public void updateUI() { super.updateUI(); setFont(ThemeZone.font(Typography.Role.COUNT)); }''',
     '''        @Override public void updateUI() {
            super.updateUI();
            // A header strip's word detail ("Mac") is in the label face; a count keeps the count face (the mock).
            setFont(ThemeZone.font(flush && presentation != null && !presentation.detail().matches("\\\\d+") ? Typography.Role.LABEL : Typography.Role.COUNT));
        }''', 1),
    ('''        detail.setToolTipText(state.detail().isEmpty() ? null : state.detail());''',
     '''        detail.setToolTipText(state.detail().isEmpty() ? null : state.detail());
        if (flush) detail.setFont(ThemeZone.font(state.detail().matches("\\\\d+") ? Typography.Role.COUNT : Typography.Role.LABEL));''', 1),
    ('''                actionButtons.add(button); contributed.add(button);''',
     '''                if (flush) actionSize(button);
                actionButtons.add(button); contributed.add(button);''', 1),
    ('''    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(logicalHeight())); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(logicalHeight())); }''',
     '''    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(stripHeight())); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(stripHeight())); }

    /** The header's height in logical pixels: a flush strip adds its rule. */
    private int stripHeight() { return logicalHeight() + (flush ? kit.metrics().value(DesignMetrics.Role.DOCK_RULE) : 0); }''', 1)])
PY
```

`ToolWindowHeader` already imports `JButton`, `Dimension`, `AppIcons` and `FlatEmptyBorder`. The doubled backslashes in
the script write `"\\d+"` into the Java source, the regular expression for digits.

- [ ] **Step 4: Run the tests; calibrate against the mocks**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.HeaderStripTest' --tests 'dev.jasper.app.workspace.DockMockTest' --tests 'dev.jasper.app.workspace.ToolWindowHeaderTest' --tests 'dev.jasper.app.workspace.WorkspaceMetricsTest'`
Expected: PASS (`HeaderStripTest` 5, `DockMockTest` 5), with the native headers' `FONTS residual until plan 5b` lines
printed (measured at `759b2b7d9` in the platform face: the titles' ends 2 to 6 px past the mock's at 2x, the text's
agreeing pixels 0.931 to 0.976 against the 0.94 floor). For an assertion failure open
`jasper-app/build/dock-renders/<variant>-native-<panel>-header-{render,mock,diff}.png`; the knobs: the title's start is
`TOOL_HEADER_LEADING_PADDING` (classic, Task 4), the detail's start `HEADER_STRIP_TITLE_GAP`, the text after the dot
`HEADER_STRIP_DOT_GAP`, the icons' centres `TOOL_HEADER_TRAILING_PADDING` (all shift together) and `TOOL_HEADER_ACTION`
(their spacing). `WorkspaceMetricsTest.nativeAdaptersDoNotBorrowGenericKitRoles` must stay green: the header reads only
its own roles.

- [ ] **Step 5: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
T = "jasper-app/src/test/java/dev/jasper/app/workspace/"
rows = [
    f"{T}HeaderStripTest.java,app.workspace,HeaderStripTest,nonvisual-helper,none,none,{T}HeaderStripTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 9; the native header strip",
    f"{T}DockFixture.java,app.workspace,DockFixture,nonvisual-helper,none,none,{T}DockMockTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 9; the Workstation mocks' dock as a fixture"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: the native header strip

Under flush frames a native tool window header is a 24 px strip on
surface.header (surface.header.active while its frame has the focus) over a
1 px rule, with a muted detail, a parenthesised count, the dot held on the
strip, 16 px actions 2 apart and the minus for Hide. DockFixture builds the
Workstation mocks' dock; its native headers match the four header strips of
the Workstation, Dark Mode and Unlock Vault mocks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 10: The borrowed kit header strip, compared with the mocks

Spec 10.7: "Kit `PanelHeaders` receive it as a host flag." Every mock header is a plugin panel's header, which the dock
borrows from the kit view. `PanelHostHeader` gains its frame (`STANDARD`, `FLUSH`, `FLUSH_FOCUSED`); under a flush frame
`PanelHeaders` paints the same strip as the native header (a border that fills the header and draws the rule),
parenthesises its count and a digits-only detail, mutes them, puts the title gap and the dot gap of Task 4 between its
parts and spaces its actions 2 apart. The area sends the frame with every header change and every focus change, and
gives Hide the minus. This carries 4a's hand-off of the borrowed header's connected dot into a declared pair painted
over every palette (Rulings 23).

**Files:**
- Modify: `jasper-ui/src/main/java/dev/jasper/ui/swing/PanelHostHeader.java`, `jasper-ui/src/main/java/dev/jasper/ui/swing/PanelHeaders.java`
  (`HeaderControl.refresh`, `dot`, `HeaderLayout`; new `StripBorder`)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java` (`mountHeader`, `hostHeader`)
- Create: `jasper-ui/src/test/java/dev/jasper/ui/swing/PanelHeaderStripTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `FlushFramesTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/appearance/PaintedContrastTest.java`

**Interfaces:**
- Consumes: Task 4's `ChromePainter` header painters and roles; Task 8's `Host.focused` and `focusedHeader`; Task 9's
  `DockFixture` and `DockMockTest.compareHeaders`.
- Produces: `PanelHostHeader.Frame { STANDARD, FLUSH, FLUSH_FOCUSED }` and `PanelHostHeader(String, boolean,
  List<Action>, Frame)` with the three-argument form meaning `STANDARD`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-ui/src/test/java/dev/jasper/ui/swing/PanelHeaderStripTest.java`:

```java
package dev.jasper.ui.swing;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.model.ControlState;
import dev.jasper.ui.model.Nodes;
import dev.jasper.ui.model.ViewSpec;
import dev.jasper.ui.testsupport.KitFixtures;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import javax.swing.JLabel;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;

/** Spec 10.7: a borrowed kit panel header under a flush frame is a header strip. */
class PanelHeaderStripTest {
    private static ViewSpec spec() {
        return new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("panel.header", ControlState.READY, "Sessions", OptionalInt.of(2), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("panel.body", ControlState.READY, "Body", Nodes.TextRole.BODY), Optional.empty()));
    }

    private static BufferedImage paint(javax.swing.JComponent header) {
        header.setSize(300, header.getPreferredSize().height);
        header.doLayout();
        var image = new BufferedImage(300, header.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { header.paint(g); } finally { g.dispose(); }
        return image;
    }

    private static List<JLabel> labels(java.awt.Container root) {
        var labels = new java.util.ArrayList<JLabel>();
        for (var child : root.getComponents()) {
            if (child instanceof JLabel label && label.isVisible()) labels.add(label);
            if (child instanceof java.awt.Container nested) labels.addAll(labels(nested));
        }
        return labels;
    }

    @Test void aFlushHeaderIsAStripOverItsRuleWithAParenthesisedCount() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var theme = KitFixtures.light();
            var context = KitFixtures.context(theme, Kit.CLASSIC);
            var painter = new ChromePainter(Kit.CLASSIC);
            try (var view = new KitView(context, spec(), event -> { })) {
                var header = view.panelHeader("panel");
                view.setPanelHostHeader("panel", new PanelHostHeader("", false, List.of(), PanelHostHeader.Frame.FLUSH));
                int strip = Kit.CLASSIC.metrics().value(DesignMetrics.Role.PANEL_HEADER) + Kit.CLASSIC.metrics().value(DesignMetrics.Role.DOCK_RULE);
                assertThat(header.getPreferredSize().height).as("24 and the rule").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(strip));
                var image = paint(header);
                assertThat(new Color(image.getRGB(150, 3), true)).isEqualTo(painter.headerSurface(theme, false));
                assertThat(new Color(image.getRGB(150, image.getHeight() - 1), true)).as("the rule").isEqualTo(painter.dockRule(theme));
                assertThat(labels(header)).extracting(JLabel::getText).contains("Sessions", "(2)");
                view.setPanelHostHeader("panel", new PanelHostHeader("5", true, List.of(), PanelHostHeader.Frame.FLUSH_FOCUSED));
                image = paint(header);
                assertThat(new Color(image.getRGB(150, 3), true)).as("focused").isEqualTo(painter.headerSurface(theme, true));
                assertThat(labels(header)).extracting(JLabel::getText).contains("(5)");
                var title = labels(header).stream().filter(label -> label.getText().equals("Sessions")).findFirst().orElseThrow();
                assertThat(title.getForeground()).isEqualTo(painter.headerTitle(theme, true));
            }
        });
    }

    @Test void aStandardHeaderIsUnchanged() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            var context = KitFixtures.context(KitFixtures.dark());
            try (var view = new KitView(context, spec(), event -> { })) {
                var header = view.panelHeader("panel");
                view.setPanelHostHeader("panel", new PanelHostHeader("5", false, List.of()));
                assertThat(labels(header)).extracting(JLabel::getText).contains("2", "5").doesNotContain("(2)", "(5)");
                assertThat(new PanelHostHeader("", false, List.of()).frame()).isEqualTo(PanelHostHeader.Frame.STANDARD);
            }
        });
    }
}
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/FlushFramesTest.java`, add a test before the final `}` of the class:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/FlushFramesTest.java"); text = path.read_text(encoding="utf-8")
block = '''
    /** A borrowed kit header follows its frame's focus, and its Hide is the minus. */
    @Test void aBorrowedHeaderIsAStripThatFollowsFocus() throws Exception {
        EnterpriseLook.install(false);
        try (var fixture = new DockFixture(EnterpriseLook.kit(), EnterpriseLook.theme(false), true, true)) {
            var painter = new ChromePainter(EnterpriseLook.kit());
            var theme = EnterpriseLook.theme(false);
            fixture.focus("sessions");
            var image = fixture.render(1);
            var sessions = fixture.header("sessions");
            var sftp = fixture.header("sftp");
            assertThat(new Color(image.getRGB(sessions.x + 4, sessions.y + 4))).isEqualTo(painter.headerSurface(theme, true));
            assertThat(new Color(image.getRGB(sftp.x + 4, sftp.y + 4))).isEqualTo(painter.headerSurface(theme, false));
            fixture.focus("sftp");
            image = fixture.render(1);
            assertThat(new Color(image.getRGB(sessions.x + 4, sessions.y + 4))).isEqualTo(painter.headerSurface(theme, false));
            assertThat(new Color(image.getRGB(sftp.x + 4, sftp.y + 4))).isEqualTo(painter.headerSurface(theme, true));
            var hide = KitTestSupport.find(fixture.body, "host.hide", javax.swing.AbstractButton.class);
            assertThat(hide).isNotNull();
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
'''
if not text.rstrip().endswith("}"): raise SystemExit("FlushFramesTest: no closing brace")
text = text.rstrip()[:-1] + block
text = text.replace("import dev.jasper.app.testsupport.EdtTestExtension;", "import dev.jasper.app.testsupport.EdtTestExtension;\nimport dev.jasper.app.testsupport.KitTestSupport;", 1)
path.write_text(text, encoding="utf-8")
PY
```

In `PaintedContrastTest`, the borrowed header's connected dot joins the painted suite (4a Rulings 16), in both frames;
insert before `    private static boolean searchHint(GeneratedTheme theme, Color expected) {`:

```java
    /** 4a's hand-off: a borrowed kit header's connected dot is held to 3:1 on the surface it sits on, over every palette. */
    @Test void theBorrowedHeadersConnectedDotIsHeldOnItsSurfaceOverEveryPalette() throws Exception {
        var initial = ThemeTestSupport.generated(true);
        var original = UIManager.getLookAndFeel();
        var spec = new ViewSpec(new Nodes.Panel("panel", ControlState.READY,
            new Nodes.Header("panel.header", ControlState.READY, "Sessions", java.util.OptionalInt.empty(), Optional.empty(), Optional.empty(), List.of()),
            Optional.empty(), new Nodes.Text("panel.body", ControlState.READY, "Body", Nodes.TextRole.BODY), Optional.empty()));
        try (var binding = new ThemeBinding(new ThemePair(initial, initial), failure -> { throw new AssertionError(failure); });
             var icons = new IconRegistry()) {
            for (Kit kit : List.of(Kit.DEFAULT, Kit.CLASSIC)) {
                var context = KitContext.of(kit, binding, ThemeRoute.CHROME, icons, () -> false, action -> "");
                try (var view = new KitView(context, spec, event -> { })) {
                    for (var frame : PanelHostHeader.Frame.values()) {
                        view.setPanelHostHeader("panel", new PanelHostHeader("Mac", true, List.of(), frame));
                        var header = view.panelHeader("panel");
                        for (var theme : palettes()) {
                            FlatLafInstaller.install(theme, kit); binding.publish(new ThemePair(theme, theme)); SwingUtilities.updateComponentTreeUI(view);
                            view.setPanelHostHeader("panel", new PanelHostHeader("Mac", true, List.of(), frame));
                            header.setSize(400, header.getPreferredSize().height);
                            KitTestSupport.layout(header);
                            var pair = switch (frame) {
                                case STANDARD -> new PaintPair(STATUS_SUCCESS, SURFACE_PANEL, SurfaceContrast.Guard.INDICATOR);
                                case FLUSH -> ChromePainter.DOCK_PAIRS.get(11);
                                case FLUSH_FOCUSED -> ChromePainter.DOCK_PAIRS.get(12);
                            };
                            Color expected = SurfaceContrast.painted(theme, pair);
                            assertThat(anywhere(paint(header), expected)).as(name(theme) + " " + kit.id() + " " + frame + " dot " + ColorMath.hex(expected)).isTrue();
                        }
                    }
                }
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

```

`paint(JComponent)` is `PaintedContrastTest`'s helper (the off-switch test uses it) and `anywhere(BufferedImage, Color)`
Task 4's (the existing `hasColor` scans only an image's right half, and the dot sits after the title, on the left);
`KitTestSupport.layout(Component)` lays a tree out. In `DockMockTest`, insert before
`    // Later tasks add their surfaces above this line.`:

```java
    /** The four borrowed kit header strips against the mocks' (every mock header is a plugin panel's). */
    @Test void kitHeaderStripsMatchTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) compareHeaders(workstation.getKey(), workstation.getValue(), true);
    }

```

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-ui:test --tests 'dev.jasper.ui.swing.PanelHeaderStripTest' :jasper-app:test --tests 'dev.jasper.app.workspace.FlushFramesTest' --tests 'dev.jasper.app.workspace.DockMockTest' --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: FAIL; jasper-ui reports "cannot find symbol: class Frame" and the four-argument `PanelHostHeader`.

- [ ] **Step 3: Write the frame flag and the strip**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 10 step 3: a borrowed header's frame (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
S = "jasper-ui/src/main/java/dev/jasper/ui/swing/"
edit(S + "PanelHostHeader.java", [
    (''' * @param detail text after the title and count; empty to omit
 * @param active whether a connected status dot precedes the detail
 * @param actions icon-only actions, in order
 */
public record PanelHostHeader(String detail, boolean active, List<Action> actions) {
    /** No detail, no dot and no actions. */
    public static final PanelHostHeader EMPTY = new PanelHostHeader("", false, List.of());

    /** Creates an immutable description. */
    public PanelHostHeader {
        Objects.requireNonNull(detail, "detail");
        actions = List.copyOf(actions);
    }''',
     ''' * @param detail text after the title and count; empty to omit
 * @param active whether a connected status dot precedes the detail
 * @param actions icon-only actions, in order
 * @param frame how the host frames the header: as the view draws it, or as a flush frame's header strip
 */
public record PanelHostHeader(String detail, boolean active, List<Action> actions, Frame frame) {
    /** No detail, no dot and no actions. */
    public static final PanelHostHeader EMPTY = new PanelHostHeader("", false, List.of());

    /**
     * How the host frames a borrowed header. {@code FLUSH} and {@code FLUSH_FOCUSED} make it a header strip (Enterprise
     * presentation spec 10.7): the strip's surface over a 1 px rule, a parenthesised count and digits-only detail, a muted
     * detail and actions 2 px apart; focused while the frame holds the focus owner.
     */
    public enum Frame { STANDARD, FLUSH, FLUSH_FOCUSED }

    /** Creates an immutable description. */
    public PanelHostHeader {
        Objects.requireNonNull(detail, "detail");
        Objects.requireNonNull(frame, "frame");
        actions = List.copyOf(actions);
    }

    /** A description the view draws as it does without a host frame. */
    public PanelHostHeader(String detail, boolean active, List<Action> actions) { this(detail, active, actions, Frame.STANDARD); }''', 1)])
edit(S + "PanelHeaders.java", [
    ('''        /** The connected dot: the success status held to the indicator floor on the panel surface it sits on. */
        private Icon dot() {''',
     '''        /** Whether the host frames this header as a flush frame's strip (spec 10.7). */
        boolean flush() { return hostHeader.frame() != PanelHostHeader.Frame.STANDARD; }

        /** Whether that strip's frame holds the focus owner. */
        boolean focusedFrame() { return hostHeader.frame() == PanelHostHeader.Frame.FLUSH_FOCUSED; }

        /** The connected dot: the success status held to the indicator floor on the panel surface it sits on. */
        private Icon dot() {''', 1),
    ('''                    var g = (Graphics2D) graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        g.setColor(SurfaceContrast.on(context().theme().semantic(), SemanticColor.STATUS_SUCCESS,
                                SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));''',
     '''                    if (flush()) {
                        // On a header strip the dot is held on the strip's own surface (ChromePainter.DOCK_PAIRS).
                        new ChromePainter(context().kit()).headerDot(graphics, x, y, getIconWidth(), context().theme(), focusedFrame());
                        return;
                    }
                    var g = (Graphics2D) graphics.create();
                    try {
                        ThemePainting.antialias(g);
                        g.setColor(SurfaceContrast.on(context().theme().semantic(), SemanticColor.STATUS_SUCCESS,
                                SemanticColor.SURFACE_PANEL, SurfaceContrast.INDICATOR));''', 1),
    ('''            count.setText(h.count().isPresent() ? Integer.toString(h.count().getAsInt()) : "");''',
     '''            count.setText(h.count().isPresent() ? (flush() ? "(" + h.count().getAsInt() + ")" : Integer.toString(h.count().getAsInt())) : "");''', 1),
    ('''            Composition.style(
                    count, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_TERTIARY);''',
     '''            Composition.style(
                    count, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_TERTIARY);
            if (flush()) {
                var painter = new ChromePainter(context().kit());
                title.setForeground(painter.headerTitle(context().theme(), focusedFrame()));
                count.setForeground(painter.headerDetail(context().theme(), focusedFrame(), true));
            }''', 1),
    ('''            Composition.style(
                    detail, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_SECONDARY);''',
     '''            Composition.style(
                    detail, context(), Typography.Role.PANEL_HEADER_COUNT, SemanticColor.TEXT_SECONDARY);
            if (flush()) {
                // A strip's word detail ("Mac") is in the label face and muted; a digits-only detail is a count (spec 10.7).
                boolean digits = hostHeader.detail().matches("\\\\d+");
                if (digits) detail.setText("(" + hostHeader.detail() + ")");
                else Composition.style(detail, context(), Typography.Role.LABEL, SemanticColor.TEXT_SECONDARY);
                detail.setIconTextGap(ThemePainting.metric(context(), DesignMetrics.Role.HEADER_STRIP_DOT_GAP));
                detail.setForeground(new ChromePainter(context().kit()).headerDetail(context().theme(), focusedFrame(), digits));
            }''', 1),
    ('''            component.setBorder(new EmptyBorder(0, pad, 0, ThemePainting.metric(context(),DesignMetrics.Role.PANEL_HEADER_TRAILING_PADDING)));
            component.setLayout(new HeaderLayout(this));
            descriptor.setLayout(new Navigation.InlineLayout(context()));''',
     '''            int trailing = ThemePainting.metric(context(), DesignMetrics.Role.PANEL_HEADER_TRAILING_PADDING);
            component.setBorder(flush() ? new StripBorder(pad, ThemePainting.metric(context(), DesignMetrics.Role.DOCK_RULE), trailing, this)
                    : new EmptyBorder(0, pad, 0, trailing));
            component.setLayout(new HeaderLayout(this));
            descriptor.setLayout(flush() ? new Navigation.InlineLayout(context(), DesignMetrics.Role.PANEL_HEADER_ACTION_GAP)
                    : new Navigation.InlineLayout(context()));''', 1),
    ('''        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
        }''',
     '''        int gap() {
            return ThemePainting.metric(owner.context(), DesignMetrics.Role.ICON_GAP);
        }

        /** Between the trailing groups: the icon gap, or on a header strip the actions' 2 px. */
        int trailingGap() {
            return owner.flush() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_ACTION_GAP) : gap();
        }

        /** After the title and after the count: the icon gap, or on a header strip the title gap. */
        int textGap() {
            return owner.flush() ? ThemePainting.metric(owner.context(), DesignMetrics.Role.HEADER_STRIP_TITLE_GAP) : gap();
        }

        /** What lies under the content: the bottom gap, or a header strip's rule. */
        int bottom() {
            return ThemePainting.metric(owner.context(), owner.flush() ? DesignMetrics.Role.DOCK_RULE : DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP);
        }''', 1),
    ('''            int height = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER);
            int bottom = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP);''',
     '''            int bottom = bottom();
            // A header strip is PANEL_HEADER tall above its rule.
            int height = ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER) + (owner.flush() ? bottom : 0);''', 1),
    ('''                available = Math.max(0, available - allocated - gap());''',
     '''                available = Math.max(0, available - allocated - trailingGap());''', 1),
    ('''            int left = i.left, right = Math.max(left, p.getWidth() - i.right), g = gap();
            int contentHeight = Math.max(0, p.getHeight() - ThemePainting.metric(owner.context(), DesignMetrics.Role.PANEL_HEADER_BOTTOM_GAP));''',
     '''            int left = i.left, right = Math.max(left, p.getWidth() - i.right), g = trailingGap(), t = textGap();
            int contentHeight = Math.max(0, p.getHeight() - bottom());''', 1),
    ('''(cw > 0 ? g : 0)''', '''(cw > 0 ? t : 0)''', 1),
    ('''            left += tw + g;''', '''            left += tw + t;''', 1),
    ('''                left += cw + g;''', '''                left += cw + t;''', 1),
    ('''    static final class HeaderLayout implements Composition.WidthLayout {''',
     '''    /**
     * A borrowed header's strip under a flush frame (spec 10.7): it fills the header in the strip's surface and draws the
     * 1 px rule along its bottom, under the header's labels and actions; its insets keep the header's paddings and the
     * rule.
     */
    private static final class StripBorder extends EmptyBorder {
        private final HeaderControl owner;

        StripBorder(int left, int rule, int right, HeaderControl owner) {
            super(0, left, rule, right);
            this.owner = owner;
        }

        @Override public void paintBorder(Component c, Graphics g, int x, int y, int width, int height) {
            Graphics copy = g.create(x, y, width, height);
            try { new ChromePainter(owner.context().kit()).headerStrip(copy, width, height, owner.context().theme(), owner.focusedFrame()); }
            finally { copy.dispose(); }
        }

        @Override public boolean isBorderOpaque() { return true; }
    }

    static final class HeaderLayout implements Composition.WidthLayout {''', 1)])
edit("jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java", [
    ('''            new dev.jasper.ui.model.Nodes.Button("host.hide", ready, "", "collapse", ghost, "host.hide", "Hide")), event -> {''',
     '''            new dev.jasper.ui.model.Nodes.Button("host.hide", ready, "", flush ? "remove" : "collapse", ghost, "host.hide", "Hide")), event -> {''', 1),
    ('''        host.header = state -> { if (borrowed.getAsBoolean()) view.setPanelHostHeader(panel, hostHeader(state)); };
        host.header.accept(headers.getOrDefault(id, dev.jasper.app.contributions.PanelHeaderState.EMPTY));
        return () -> {
            host.header = state -> { };''',
     '''        // The last metadata is kept, so a focus change can send the header again with its frame.
        var shown = new java.util.concurrent.atomic.AtomicReference<>(headers.getOrDefault(id, dev.jasper.app.contributions.PanelHeaderState.EMPTY));
        host.header = state -> { shown.set(state); if (borrowed.getAsBoolean()) view.setPanelHostHeader(panel, hostHeader(state, frame(host))); };
        host.focusedHeader = value -> { if (borrowed.getAsBoolean()) view.setPanelHostHeader(panel, hostHeader(shown.get(), frame(host))); };
        host.header.accept(shown.get());
        return () -> {
            host.header = state -> { };
            host.focusedHeader = value -> { };''', 1),
    ('''    private static dev.jasper.ui.swing.PanelHostHeader hostHeader(dev.jasper.app.contributions.PanelHeaderState state) {
        return new dev.jasper.ui.swing.PanelHostHeader(state.detail(), state.active(), state.actions().stream()''',
     '''    /** How a borrowed header is framed: as the view draws it, or as a flush frame's strip, focused or not. */
    private dev.jasper.ui.swing.PanelHostHeader.Frame frame(Host host) {
        if (!flush) return dev.jasper.ui.swing.PanelHostHeader.Frame.STANDARD;
        return host.focused ? dev.jasper.ui.swing.PanelHostHeader.Frame.FLUSH_FOCUSED : dev.jasper.ui.swing.PanelHostHeader.Frame.FLUSH;
    }

    private static dev.jasper.ui.swing.PanelHostHeader hostHeader(dev.jasper.app.contributions.PanelHeaderState state,
                                                                  dev.jasper.ui.swing.PanelHostHeader.Frame frame) {
        return new dev.jasper.ui.swing.PanelHostHeader(state.detail(), state.active(), state.actions().stream()''', 1),
    ('''                ? new dev.jasper.ui.swing.PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList());''',
     '''                ? new dev.jasper.ui.swing.PanelHostHeader.Artwork(named.name(), named.pathData()) : null)).toList(), frame);''', 1)])
PY
```

`PanelHeaders` imports `java.awt.*`, `javax.swing.*`, `javax.swing.border.EmptyBorder` and the metrics, model and theme
packages, so `Component`, `Graphics`, `ChromePainter` (same package) and `DesignMetrics` resolve. A `Host`'s `focused`
field is read by the area's `frame(Host)`, which sits in the same outer class.

- [ ] **Step 4: Run the tests, calibrate, and run the pixel check**

Run: `./gradlew :jasper-ui:test :jasper-app:test --tests 'dev.jasper.app.workspace.FlushFramesTest' --tests 'dev.jasper.app.workspace.DockMockTest' --tests 'dev.jasper.app.workspace.ToolWindowKitPanelTest' --tests 'dev.jasper.app.appearance.PaintedContrastTest' --continue`
Expected: PASS (`PanelHeaderStripTest` 2, `PanelHostHeaderTest` unchanged, `FlushFramesTest` 5, `DockMockTest` 6), the
kit headers' `FONTS residual until plan 5b` lines printed (at `759b2b7d9` the titles end 3 to 6 px past the mock's at 2x
and the text agrees 0.933 to 0.975 against 0.94). For an assertion failure on a kit header open `jasper-app/build/dock-renders/<variant>-kit-<panel>-header-{render,mock,diff}.png`; the knobs:
the title's start is `FIELD_HORIZONTAL_PADDING`'s read in `refresh` (classic 8), the count's and detail's start
`HEADER_STRIP_TITLE_GAP`, the text after the dot `HEADER_STRIP_DOT_GAP`, the icons' centres
`PANEL_HEADER_TRAILING_PADDING` (all together) and `PANEL_HEADER_ACTION` with `PANEL_HEADER_ACTION_GAP` (their pitch).
Then the pixel check (Task 1 Step 1's gallery commands, `DefaultDockPixelsTest`, the `diff`/`cmp` line):
`PIXELS-UNCHANGED`. A standard frame sends `PanelHostHeader.Frame.STANDARD` and the "collapse" Hide, so the default look
draws what it drew.

- [ ] **Step 5: Commit**

```bash
git add jasper-ui/src jasper-app/src
git commit -F - <<'EOF'
Dock chrome: the borrowed kit header strip

PanelHostHeader carries how the host frames a borrowed header. Under a flush
frame the kit's panel header becomes the header strip: the strip's surface
over a 1 px rule, focused while its frame holds the focus owner, a
parenthesised count and digits-only detail, a muted word detail, the title
and dot gaps, actions 2 apart and the minus for Hide. Its connected dot is
painted through the declared pairs over every palette (4a's hand-off). The
kit headers match the four header strips of the Workstation, Dark Mode and
Unlock Vault mocks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 11: A saved dock layout per look

Spec 10.7: "The default look keeps today's ui-state keys unchanged. A non-default look saves its dock under its provider
id, so a first Enterprise start gets Enterprise's default anchors and a return to the default look finds the old
layout." `UiState` holds each look's dock (`dock` and `panels`) by key and reads and writes the one its look key names;
`[look."<id>"]` holds a non-default look's tables (Rulings 17). The application loads ui-state with the presentation's
`DockLayoutKey` (spec 9.1: "application → `UiState` (a plain string)"). `WindowContributions` is unchanged: it already
reads and writes only through `panel`, `panels`, `putPanel`, `dock` and `setDock`.

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java:105-131,133-158,160-204,242-247,264-285`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:936` (`startPlugins`)
- Create: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateLookTest.java`

**Interfaces:**
- Consumes: plan 5a's `JasperApplication.presentation()`, `Projections.dockLayout()`, `DockLayoutKey.key()`.
- Produces: `UiState.load(Path file, String look)`, `UiState.inMemory(String look)`, `String look()`; `load(Path)` and
  `inMemory()` mean the default look's key `""`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateLookTest.java`:

```java
package dev.jasper.app.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import dev.jasper.app.dock.Slot;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Spec 10.7 and Rulings 17: each look keeps its own dock in ui-state; the default look's keys do not change. */
class UiStateLookTest {
    static final String ENTERPRISE = "dev.jasper.enterprise";
    @TempDir Path dir;

    /** Review Focus 2. */
    @Test void eachLookKeepsItsOwnLayoutAndTheOthersSurviveASave() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        var standard = UiState.load(file);
        standard.putPanel("dev.x.hosts", new UiState.Panel(Slot.LEFT_TOP, 0, true));
        standard.setDock(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        standard.save();
        String before = Files.readString(file);

        var enterprise = UiState.load(file, ENTERPRISE);
        assertThat(enterprise.look()).isEqualTo(ENTERPRISE);
        assertThat(enterprise.panel("dev.x.hosts")).as("a first start under the look: the registered defaults").isEmpty();
        assertThat(enterprise.dock()).isEqualTo(UiState.Dock.DEFAULT);
        enterprise.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        enterprise.setDock(new UiState.Dock(260, 350, 200, 0.5, 0.55, 0.5));
        enterprise.setRailVisible(false);
        enterprise.save();
        assertThat(Files.readString(file)).contains("""

            [look."dev.jasper.enterprise".dock]
            left_width = 260
            right_width = 350
            bottom_height = 200
            left_ratio = 0.5
            right_ratio = 0.55
            bottom_ratio = 0.5

            [look."dev.jasper.enterprise".panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true
            """);

        var back = UiState.load(file);
        assertThat(back.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.LEFT_TOP, 0, true));
        assertThat(back.dock()).isEqualTo(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        assertThat(back.railVisible()).as("rail visibility is global").isFalse();
        back.setRailVisible(true);
        back.save();
        assertThat(Files.readString(file)).as("the default look's save keeps the other look's tables").contains("[look.\"dev.jasper.enterprise\".dock]");
        assertThat(Files.readString(file)).as("the default look's tables as it wrote them").contains(before.substring(before.indexOf("[dock]")));
        assertThat(UiState.load(file, ENTERPRISE).panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, true));
    }

    @Test void aBadLookEntryFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2

            [dock]
            left_width = 300

            [look."dev.jasper.enterprise".dock]
            left_width = 99999
            right_width = 400

            [look."dev.jasper.enterprise".panels."dev.x.bad"]
            slot = "NOWHERE"

            [look."dev.jasper.enterprise".panels."dev.x.good"]
            slot = "BOTTOM_LEFT"
            order = 2
            visible = false

            [look."Not An Id".panels."dev.x.good"]
            slot = "RIGHT_TOP"
            """);
        var enterprise = UiState.load(file, ENTERPRISE);
        assertThat(enterprise.dock().leftWidth()).as("an out-of-range width falls back alone").isEqualTo(UiState.Dock.DEFAULT.leftWidth());
        assertThat(enterprise.dock().rightWidth()).isEqualTo(400);
        assertThat(enterprise.panel("dev.x.bad")).isEmpty();
        assertThat(enterprise.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, 2, false));
        assertThat(UiState.load(file).dock().leftWidth()).isEqualTo(300);
        assertThat(UiState.load(file).panel("dev.x.good")).isEmpty();
    }

    @Test void aLookKeyIsAPluginIdOrEmpty() {
        assertThat(UiState.inMemory().look()).isEmpty();
        assertThat(UiState.inMemory(ENTERPRISE).look()).isEqualTo(ENTERPRISE);
        assertThatIllegalArgumentException().isThrownBy(() -> UiState.inMemory("Not An Id"));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.UiStateLookTest'`
Expected: FAIL; `compileTestJava` reports "cannot find symbol" for `load(Path, String)`, `look()` and `inMemory(String)`.

- [ ] **Step 3: Keep a dock per look**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 11 step 3: ui-state keeps each look's dock (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java", [
    ('''    private final Path file;
    private final Map<String, Panel> panels = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;
    private boolean zenMode;
    private Dock dock = Dock.DEFAULT;''',
     '''    /** One look's dock: where each panel docks, and the dock's sizes and ratios. */
    private static final class Layout {
        final Map<String, Panel> panels = new TreeMap<>();
        Dock dock = Dock.DEFAULT;
    }

    private final Path file;
    /** The look whose dock this state reads and writes: empty for the default look (spec 10.7). */
    private final String look;
    /** Each look's dock by its key, the default look's under the empty key. */
    private final Map<String, Layout> layouts = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;
    private boolean zenMode;''', 1),
    ('''    private UiState(Path file) { this.file = file; }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return new UiState(null); }

    public static UiState load(Path file) {
        UiState state = new UiState(file);''',
     '''    private UiState(Path file, String look) {
        this.file = file;
        this.look = Objects.requireNonNull(look, "look");
        if (!look.isEmpty() && !ID.matcher(look).matches()) throw new IllegalArgumentException("Not a look key: " + look);
        layout("");
    }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return inMemory(""); }

    /** The same under the look {@code look}: empty for the default look, else the providing plugin's id. */
    public static UiState inMemory(String look) { return new UiState(null, look); }

    /** The default look's state in {@code file}. */
    public static UiState load(Path file) { return load(file, ""); }

    /**
     * The state in {@code file} under the look {@code look}: empty for the default look, whose dock is the file's
     * {@code [dock]} and {@code [panels]}, else the providing plugin's id, whose dock is {@code [look."<id>"]}'s.
     */
    public static UiState load(Path file, String look) {
        UiState state = new UiState(file, look);''', 1),
    ('''            state.panels.clear(); state.windows.clear(); state.railVisible = true; state.dock = Dock.DEFAULT;''',
     '''            state.layouts.clear(); state.layout(""); state.windows.clear(); state.railVisible = true;''', 1),
    ('''        else if (Long.valueOf(2).equals(version)) readDock(toml);''', '''        else if (Long.valueOf(2).equals(version)) readLayouts(toml);''', 1),
    ('''    /** Version 2: a {@code [dock]} table and one entry per panel; a bad key or entry falls back alone. */
    private void readDock(TomlParseResult toml) {
        if (toml.get(List.of("dock")) instanceof TomlTable table)
            dock = new Dock(''',
     '''    /** Version 2: the default look's dock at the top, then each look's under {@code [look."<id>"]}; a bad one falls back alone. */
    private void readLayouts(TomlParseResult toml) {
        readDock(toml, layout(""));
        if (!(toml.get(List.of("look")) instanceof TomlTable looks)) return;
        for (String key : looks.keySet())
            if (ID.matcher(key).matches() && looks.get(List.of(key)) instanceof TomlTable entry) readDock(entry, layout(key));
    }

    /** One look's {@code dock} table and one entry per panel; a bad key or entry falls back alone. */
    private static void readDock(TomlTable toml, Layout into) {
        if (toml.get(List.of("dock")) instanceof TomlTable table)
            into.dock = new Dock(''', 1),
    ('''            panels.put(id, new Panel(slot.get(), order, Boolean.TRUE.equals(entry.get(List.of("visible")))));''',
     '''            into.panels.put(id, new Panel(slot.get(), order, Boolean.TRUE.equals(entry.get(List.of("visible")))));''', 1),
    ('''            panels.put(id, new Panel(Slot.of(side.get(), false), Panel.LAST, visible));''',
     '''            layout("").panels.put(id, new Panel(Slot.of(side.get(), false), Panel.LAST, visible));''', 1),
    ('''        dock = new Dock(migrated(Side.LEFT, shown, first, DockState.DEFAULT_SIDE), migrated(Side.RIGHT, shown, first, DockState.DEFAULT_SIDE),''',
     '''        layout("").dock = new Dock(migrated(Side.LEFT, shown, first, DockState.DEFAULT_SIDE), migrated(Side.RIGHT, shown, first, DockState.DEFAULT_SIDE),''', 1),
    ('''    public Optional<Panel> panel(String id) { return Optional.ofNullable(panels.get(id)); }
    /** Every remembered panel by id, sorted by id. */
    public Map<String, Panel> panels() { return Collections.unmodifiableMap(new TreeMap<>(panels)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) panels.put(id, Objects.requireNonNull(value)); }
    public Dock dock() { return dock; }
    public void setDock(Dock value) { dock = Objects.requireNonNull(value); }''',
     '''    /** A look's dock, made empty the first time it is asked for. */
    private Layout layout(String key) { return layouts.computeIfAbsent(key, ignored -> new Layout()); }

    /** The look whose dock this state reads and writes: empty for the default look. */
    public String look() { return look; }
    /** The look's remembered place of a panel. */
    public Optional<Panel> panel(String id) { return Optional.ofNullable(layout(look).panels.get(id)); }
    /** Every panel the look remembers, by id, sorted by id. */
    public Map<String, Panel> panels() { return Collections.unmodifiableMap(new TreeMap<>(layout(look).panels)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) layout(look).panels.put(id, Objects.requireNonNull(value)); }
    /** The look's dock sizes and ratios. */
    public Dock dock() { return layout(look).dock; }
    public void setDock(Dock value) { layout(look).dock = Objects.requireNonNull(value); }''', 1),
    ('''        text.append("\\n[dock]\\nleft_width = ").append(dock.leftWidth()).append("\\nright_width = ").append(dock.rightWidth())
            .append("\\nbottom_height = ").append(dock.bottomHeight()).append("\\nleft_ratio = ").append(dock.leftRatio())
            .append("\\nright_ratio = ").append(dock.rightRatio()).append("\\nbottom_ratio = ").append(dock.bottomRatio()).append('\\n');
        panels.forEach((id, panel) -> text.append("\\n[panels.\\"").append(id).append("\\"]\\nslot = \\"").append(panel.slot().name())
            .append("\\"\\norder = ").append(panel.order()).append("\\nvisible = ").append(panel.visible()).append('\\n'));''',
     '''        appendDock(text, "", layout(""));
        layouts.forEach((key, layout) -> { if (!key.isEmpty()) appendDock(text, "look.\\"" + key + "\\".", layout); });''', 1),
    ('''    private static String array(List<String> values) {''',
     '''    /** One look's {@code dock} table and panel entries, their table names under {@code prefix}. */
    private static void appendDock(StringBuilder text, String prefix, Layout layout) {
        Dock dock = layout.dock;
        text.append("\\n[").append(prefix).append("dock]\\nleft_width = ").append(dock.leftWidth()).append("\\nright_width = ").append(dock.rightWidth())
            .append("\\nbottom_height = ").append(dock.bottomHeight()).append("\\nleft_ratio = ").append(dock.leftRatio())
            .append("\\nright_ratio = ").append(dock.rightRatio()).append("\\nbottom_ratio = ").append(dock.bottomRatio()).append('\\n');
        layout.panels.forEach((id, panel) -> text.append("\\n[").append(prefix).append("panels.\\"").append(id).append("\\"]\\nslot = \\"")
            .append(panel.slot().name()).append("\\"\\norder = ").append(panel.order()).append("\\nvisible = ").append(panel.visible()).append('\\n'));
    }

    private static String array(List<String> values) {''', 1)])
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java"); text = path.read_text(encoding="utf-8")
old = "uiState = UiState.load(dirs.uiState());"
count = text.count(old)
if count < 1: raise SystemExit("JasperApplication: UiState.load moved")
path.write_text(text.replace(old, "uiState = UiState.load(dirs.uiState(), presentation().projections().dockLayout().key());"), encoding="utf-8")
print("JasperApplication: replaced", count)
PY
grep -nE '(^|[^.a-zA-Z_"])(panels\.[a-zA-Z]+\(|dock *= |\(panels\)|return dock;)|state\.(panels|dock)\b' jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java | grep -v 'Dock dock = '
```

The final `grep` looks only for reads and writes of the removed `panels` and `dock` fields (a bare `panels.put(`,
`panels.get(`, `panels.forEach(`, `new TreeMap<>(panels)`, `dock = `, `return dock;`, `state.panels`, `state.dock`) and
must print nothing. The lines that stay and are correct, which it does not match, are `Layout`'s two fields, the local
`Dock dock = layout.dock;` and its `dock.leftWidth()` reads in `appendDock`, every `layout(…).panels`, `layout(…).dock`,
`into.panels` and `into.dock`, the `List.of("dock")` and `List.of("panels")` keys, and the comments. The script prints how many `UiState.load` calls it changed: one per `startPlugins` overload plan 5a left
(Rulings 9 of 5a).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.workspace.WindowPanelsTest' --tests 'dev.jasper.app.workspace.WindowContributionsTest' --tests 'dev.jasper.app.application.*'`
Expected: PASS (`UiStateLookTest` 3; `UiStateTest` unchanged, byte for byte: the default look writes the same file).

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src
git commit -F - <<'EOF'
Dock chrome: a saved dock layout per look

ui-state keeps each look's dock and panel places: the default look's under
today's [dock] and [panels] keys, unchanged, and a look's under
[look."<id>"]. The application loads it with the presentation's dock layout
key, so a first start under a look uses the registered defaults and a
return to the default look finds its layout; a save keeps every look's
tables, and a bad entry falls back alone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 12: Event Log, and the dock group lands

Spec 10.7: "Under Enterprise, `AppPanels` gives the Notifications panel the title "Event Log" and the default slot
BOTTOM_LEFT; the default look keeps "Notifications" at RIGHT_TOP." `NotificationsFeature` takes the projection, and
`HistoryPanel` takes the title for screen readers (Rulings 19). With every consumer in place, `Presentation.LANDED` gains
`DOCK_CHROME` (Rulings 20), so a look whose kit says `tool_windows = "stripes"` gets labelled stripes, flush frames, its
own ui-state tables and Event Log. Two tests extend the chrome renders of spec 16.1.4 to the dock: a real window under
the Enterprise kit file's projection (`ChromeSpec.CLASSIC`, with 6a's fixture kit and theme until plan 5b), and every kit
under both dock styles. `NotificationCardTest`'s count repair and `ConfigurationStatusTest`'s
configuration text run over every palette (4a's hand-offs, Rulings 23).

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/NotificationsFeature.java:46-65`,
  `jasper-app/src/main/java/dev/jasper/app/notificationui/HistoryPanel.java:74-81`,
  `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java:321-322`,
  `jasper-app/src/main/java/dev/jasper/app/presentation/Presentation.java` (`LANDED`)
- Create: `jasper-app/src/test/java/dev/jasper/app/application/EventLogTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseDockWindowTest.java`, `jasper-app/src/test/java/dev/jasper/app/workspace/DockChromeMatrixTest.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`, plan 6a's
  `jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java` (`check`),
  `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java:76-99`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationStatusTest.java` (one test), `docs/design/theme-engine/migration.csv`

**Interfaces:**
- Consumes: plan 5a's `AppPanels`, `Projections.of`, `ChromeSpec.CLASSIC`, `Presentation.Landed`; plan 6's
  `WorkspaceChrome.of`; Tasks 1 to 11.
- Produces: `NotificationsFeature(NotificationCenter, Contributions, Supplier<KitContext>, AppPanels)`;
  `HistoryPanel(KitContext, NotificationCenter, PanelSite, boolean, String title)`; `Presentation.LANDED` holding
  `DOCK_CHROME` beside plan 6's `WINDOW_CHROME` and `WINDOW_CONTENT`.

- [ ] **Step 1: Write the failing tests**

Create `jasper-app/src/test/java/dev/jasper/app/application/EventLogTest.java`:

```java
package dev.jasper.app.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.notifications.Delivery;
import dev.jasper.app.notifications.NotificationCenter;
import dev.jasper.app.presentation.AppPanels;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.KitTestSupport;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Spec 10.7: under labelled stripes the Notifications panel is Event Log at Bottom Left; the default look keeps it. */
@ExtendWith(EdtTestExtension.class)
class EventLogTest {
    private static PanelEntry panel(Contributions contributions) {
        return contributions.panels().stream().filter(entry -> entry.id().equals(NotificationsFeature.PANEL_ID)).findFirst().orElseThrow();
    }

    @Test void underLabelledStripesTheNotificationsPanelIsTheEventLogAtBottomLeft() {
        var contributions = new Contributions();
        var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
        try (var feature = new NotificationsFeature(center, contributions, KitTestSupport::context, new AppPanels("Event Log", Slot.BOTTOM_LEFT))) {
            var entry = panel(contributions);
            assertThat(entry.title()).isEqualTo("Event Log");
            assertThat(entry.defaultSlot()).isEqualTo(Slot.BOTTOM_LEFT);
            assertThat(entry.toggleActionId()).isEqualTo(NotificationsFeature.SHOW_HISTORY);
            assertThat(contributions.action(NotificationsFeature.SHOW_HISTORY)).as("the action keeps its title")
                .hasValueSatisfying(action -> assertThat(action.title()).isEqualTo("Notifications: Show History"));
            var view = entry.factory().apply(new PanelSite(UUID.randomUUID(), () -> { }, () -> { }, () -> true));
            assertThat(view.getAccessibleContext().getAccessibleName()).isEqualTo("Event Log");
        }
    }

    @Test void theDefaultLookKeepsNotificationsAtRightTop() {
        var contributions = new Contributions();
        var center = new NotificationCenter(Runnable::run, Clock.systemUTC(), () -> Delivery.Presence.FOCUSED);
        try (var feature = new NotificationsFeature(center, contributions, KitTestSupport::context)) {
            assertThat(panel(contributions).title()).isEqualTo("Notifications");
            assertThat(panel(contributions).defaultSlot()).isEqualTo(Slot.RIGHT_TOP);
        }
    }
}
```

`NotificationCenter` takes a `Delivery.Presence` supplier and `PanelSite` its four-argument form, as
`NotificationsFeatureTest` builds them (checked at `759b2b7d9`).

Create `jasper-app/src/test/java/dev/jasper/app/workspace/EnterpriseDockWindowTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.ChromeSpec;
import dev.jasper.app.presentation.Presentation;
import dev.jasper.app.presentation.Projections;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.metrics.DesignMetrics;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import javax.imageio.ImageIO;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 10.7 and 16.1.4: a real window under the Enterprise kit file's projection ({@code ChromeSpec.CLASSIC} with the
 * dock group landed), its kit and its theme (plan 6a's {@link EnterpriseLook} fixtures until plan 5b ships them) docks
 * the classic way.
 */
@ExtendWith(EdtTestExtension.class)
class EnterpriseDockWindowTest {
    @Test void aWindowUnderTheShippedLookDocksTheClassicWay() throws Exception {
        var kit = EnterpriseLook.kit();
        ThemeController themes = EnterpriseLook.controller(false);
        var look = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", new Projections.Host(false, false), Presentation.LANDED);
        var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, WorkspaceChrome.of(look.workspace(), look.title()));
        try {
            var state = UiState.inMemory(look.dockLayout().key());
            var model = new Contributions();
            owner.connectContributions(model, state);
            model.addAction("test.sftp.toggle", "Toggle SFTP", null, List.of("sftp"), Optional.of("alt+1"), invocation -> { });
            model.addPanel("test.sftp", "SFTP", new ImageIcon(), Slot.LEFT_TOP, Optional.of("test.sftp.toggle"), site -> new JLabel("sftp"));
            model.addPanel("test.sessions", "Sessions", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("sessions"));
            // The application's Notifications panel takes the look's AppPanels (EventLogTest); a plain panel stands in here.
            model.addPanel("test.log", look.panels().notificationsTitle(), new ImageIcon(), look.panels().notificationsSlot(), site -> new JLabel("log"));
            owner.leftStripe().buttons().getFirst().doClick(0);

            assertThat(owner.stripes()).isInstanceOf(StripeSet.LabelledStripes.class);
            assertThat(owner.area().flush()).isTrue();
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("1: SFTP");
            assertThat(owner.bottomStrip().orElseThrow().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Event Log");
            assertThat(owner.bottomStrip().orElseThrow().isVisible()).isTrue();
            assertThat(state.look()).isEqualTo("dev.jasper.enterprise");
            assertThat(state.panel("test.sftp")).hasValueSatisfying(panel -> assertThat(panel.visible()).isTrue());
            var header = owner.area().headerOf(Slot.LEFT_TOP);
            int strip = kit.metrics().value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + kit.metrics().value(DesignMetrics.Role.DOCK_RULE);
            assertThat(header.getPreferredSize().height).isEqualTo(com.formdev.flatlaf.util.UIScale.scale(strip));
            var image = MockCrops.render(owner, 1442, 902, 2);
            Files.createDirectories(MockCrops.OUT);
            ImageIO.write(image, "png", MockCrops.OUT.resolve("enterprise-window-light.png").toFile());
        } finally {
            owner.close();
            themes.close();
            EnterpriseLook.restoreDefault();
        }
    }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockChromeMatrixTest.java`:

```java
package dev.jasper.app.workspace;

import static org.assertj.core.api.Assertions.assertThat;

import dev.jasper.app.appearance.ThemeController;
import dev.jasper.app.appearance.ThemeSelection;
import dev.jasper.app.config.TerminalSchemeChoice;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.presentation.WorkspaceLook;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.EnterpriseLook;
import dev.jasper.app.testsupport.MockCrops;
import dev.jasper.ui.kit.Kit;
import dev.jasper.ui.metrics.DesignMetrics;
import dev.jasper.ui.testsupport.KitFixtures;
import java.nio.file.Files;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.ImageIcon;
import javax.swing.JLabel;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Spec 16.1.4 for the dock: a window under the default, the Enterprise fixture and the complement kits, with icon rails
 * and today's frames or labelled stripes and flush frames, builds and docks; the structure holds (the stripe kind and
 * labels, the bottom strip, the dividers' width, no gutter where the catalog has none, the header strips' height), and
 * each render is written under {@code jasper-app/build/dock-renders/} for inspection.
 */
@ExtendWith(EdtTestExtension.class)
class DockChromeMatrixTest {
    static final WorkspaceLook RAILS = new WorkspaceLook(WorkspaceLook.Menus.STANDARD, WorkspaceLook.Header.UNIFIED,
        WorkspaceLook.Stripes.RAILS, WorkspaceLook.Tabs.EQUAL, WorkspaceLook.Status.SLASH, false, WorkspaceLook.FrameStyle.STANDARD);

    @Test void everyKitDocksUnderBothDockStyles() throws Exception {
        try {
            for (var kit : Map.of("default", Kit.DEFAULT, "enterprise", KitFixtures.enterprise(), "complement", KitFixtures.complement()).entrySet())
                for (var dock : List.of(RAILS, DockChromeTest.CLASSIC_DOCK)) {
                    boolean labelled = dock.stripes() == WorkspaceLook.Stripes.LABELLED;
                    String where = kit.getKey() + (labelled ? " labelled" : " rails");
                    ThemeController themes = ThemeController.startup(false, ThemeSelection.slateCobalt(TerminalSchemeChoice.MATCH), kit.getValue(),
                        failure -> { throw new AssertionError(failure); });
                    var owner = DesktopTestSupport.content(DesktopTestSupport.idleLauncher(new ArrayDeque<>()), themes, WorkspaceChrome.of(dock, new dev.jasper.app.presentation.TitleFormat("{context}")));
                    try {
                        var model = new Contributions();
                        owner.connectContributions(model, UiState.inMemory());
                        model.addPanel("test.left", "Left", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("left"));
                        model.addPanel("test.right", "Right", new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel("right"));
                        model.addPanel("test.log", "Log", new ImageIcon(), Slot.BOTTOM_LEFT, site -> new JLabel("log"));
                        owner.leftStripe().buttons().getFirst().doClick(0);
                        owner.rightStripe().buttons().getFirst().doClick(0);
                        var image = MockCrops.render(owner, 1200, 800, 2);
                        Files.createDirectories(MockCrops.OUT);
                        ImageIO.write(image, "png", MockCrops.OUT.resolve("matrix-" + where.replace(' ', '-') + ".png").toFile());
                        var metrics = kit.getValue().metrics();
                        assertThat(owner.stripes() instanceof StripeSet.LabelledStripes).as(where).isEqualTo(labelled);
                        assertThat(owner.bottomStrip().isPresent()).as(where + " bottom strip").isEqualTo(labelled);
                        assertThat(owner.area().metrics().divider()).as(where + " dividers")
                            .isEqualTo(labelled ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 6);
                        var frame = owner.area().content(Slot.LEFT_TOP).getParent();
                        if (metrics.value(DesignMetrics.Role.WORKSPACE_GUTTER) == 0)
                            assertThat(frame.getInsets()).as(where + " no gutter").isEqualTo(new java.awt.Insets(0, 0, 0, 0));
                        int header = metrics.value(DesignMetrics.Role.NATIVE_TOOL_HEADER_HEIGHT) + (labelled ? metrics.value(DesignMetrics.Role.DOCK_RULE) : 0);
                        assertThat(owner.area().headerOf(Slot.LEFT_TOP).getHeight()).as(where + " header").isEqualTo(com.formdev.flatlaf.util.UIScale.scale(header));
                        if (labelled) assertThat(owner.bottomStrip().orElseThrow().buttons()).as(where + " bottom labels").hasSize(1);
                        else assertThat(owner.leftStripe().buttons()).as(where + " the foot group on the rail").hasSize(2);
                    } finally { owner.close(); themes.close(); }
                }
        } finally { EnterpriseLook.restoreDefault(); }
    }
}
```

Add to `jasper-app/src/test/java/dev/jasper/app/presentation/ProjectionsTest.java`, before its last `}`:

```java
    /** Plan 7: the dock group has landed: the shipped kit's dock is labelled and flush, saved under its id, with Event Log. */
    @Test void theDockGroupHasLanded() {
        assertThat(Presentation.LANDED).contains(Presentation.Landed.WINDOW_CHROME, Presentation.Landed.WINDOW_CONTENT, Presentation.Landed.DOCK_CHROME);
        var shipped = Projections.of(ChromeSpec.CLASSIC, true, true, "dev.jasper.enterprise", MAC, Presentation.LANDED);
        assertThat(shipped.workspace().stripes()).isEqualTo(WorkspaceLook.Stripes.LABELLED);
        assertThat(shipped.workspace().frames()).isEqualTo(WorkspaceLook.FrameStyle.FLUSH);
        assertThat(shipped.dockLayout()).isEqualTo(new DockLayoutKey("dev.jasper.enterprise"));
        assertThat(shipped.panels()).isEqualTo(new AppPanels("Event Log", Slot.BOTTOM_LEFT));
        var standard = Projections.of(ChromeSpec.DEFAULT, false, false, "", MAC, Presentation.LANDED);
        assertThat(standard.workspace().stripes()).isEqualTo(WorkspaceLook.Stripes.RAILS);
        assertThat(standard.workspace().frames()).isEqualTo(WorkspaceLook.FrameStyle.STANDARD);
        assertThat(standard.dockLayout().key()).isEmpty();
        assertThat(standard.panels()).isEqualTo(new AppPanels("Notifications", Slot.RIGHT_TOP));
        assertThat(Presentation.standard(MAC).fallback(MAC).projections().dockLayout().key()).as("the fallback").isEmpty();
    }
```

The landing assertions plan 6 left in `ProjectionsTest` (`untilItsPlanLandsAChromeValueMapsToTheDefaultProjection`) need
no edit (checked at `759b2b7d9`): its check of `Presentation.LANDED` reads `.contains(WINDOW_CHROME, WINDOW_CONTENT)`,
which still holds, none of its assertions reads the dock group under `Presentation.LANDED`, and its
`EnumSet.of(WINDOW_CHROME)` case keeps `Stripes.RAILS` (it lands only the window group).

Plan 6a's `ChromeRenderTest.check` is where "plans 6b to 9 add their surfaces' checks"; it renders spec 16.1.4's cases
(the default, Enterprise, complement and fallback looks and the covering array's rows) with every group landed, so its
rows with `tool_windows = "stripes"` now dock the classic way. Add the dock's structure to it:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace/ChromeRenderTest.java"); text = path.read_text(encoding="utf-8")
old = "    static void check(Case look, WindowContent owner, JRootPane root, MacTitleBar bar) {\n"
new = old + """        // Plan 7 (spec 10.7): the dock the look names.
        boolean labelled = look.projections().workspace().stripes() == dev.jasper.app.presentation.WorkspaceLook.Stripes.LABELLED;
        assertThat(owner.stripes() instanceof StripeSet.LabelledStripes).as(look.name() + ": labelled stripes where the look asks for them").isEqualTo(labelled);
        assertThat(owner.bottomStrip().isPresent()).as(look.name() + ": the bottom strip").isEqualTo(labelled);
        assertThat(owner.area().flush()).as(look.name() + ": flush frames")
            .isEqualTo(look.projections().workspace().frames() == dev.jasper.app.presentation.WorkspaceLook.FrameStyle.FLUSH);
"""
if text.count(old) != 1: raise SystemExit("ChromeRenderTest: check moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

In `jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java`, the repeat count's repair runs over
every palette:

```bash
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/notificationui/NotificationCardTest.java"); text = path.read_text(encoding="utf-8")
old = '''                for (boolean dark : new boolean[]{true, false}) {
                    var theme = ThemeTestSupport.generated(dark); String name = dark ? "Dark" : "Light";'''
new = '''                // 4a's hand-off (Enterprise plan 7): the count's repair holds over the theme set and the degenerate palettes.
                var palettes = new java.util.ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
                palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
                for (var theme : palettes) {
                    String name = theme.name() + (theme.dark() ? " Dark" : " Light");'''
if text.count(old) != 1: raise SystemExit("NotificationCardTest: the theme loop moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

In `jasper-app/src/test/java/dev/jasper/app/workspace/ConfigurationStatusTest.java`, the configuration text's repair
runs over every palette too (4a's third hand-off); insert before
`    @Test void statusSurfaceFollowsChromeRatherThanTerminalPalette() throws Exception {`. If plan 6b already runs this
check over every palette, skip it and record that in the status banner.

```java
    /** 4a's hand-off (Enterprise plan 7): the configuration text's repair holds over the theme set and the degenerate palettes. */
    @Test void theConfigurationTextKeepsItsContrastOverEveryPalette() throws Exception {
        edt(() -> {
            var palettes = new java.util.ArrayList<>(dev.jasper.ui.testsupport.KitFixtures.themes());
            palettes.addAll(dev.jasper.ui.testsupport.KitFixtures.degenerate());
            Path file = Path.of("fixture", "settings.toml");
            try {
                for (var theme : palettes) {
                    dev.jasper.ui.swing.FlatLafInstaller.install(theme, dev.jasper.ui.kit.Kit.DEFAULT);
                    var status = new WindowStatusBar();
                    String name = theme.name() + (theme.dark() ? " Dark" : " Light");
                    for (var severity : ConfigDiagnostic.Severity.values()) {
                        status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(
                            new ConfigDiagnostic(severity, file, 1234567, 1, "window.tab_height", "Check this key")), file, true));
                        assertThat(contrast(status.configButton().getForeground(), status.getBackground()))
                            .as(name + ", " + severity.name()).isGreaterThanOrEqualTo(4.5);
                    }
                    status.setConfiguration(new ConfigService.State(ConfigSnapshot.defaults(), List.of(), file, true));
                    assertThat(contrast(status.configButton().getForeground(), status.getBackground()))
                        .as(name + ", no diagnostics").isGreaterThanOrEqualTo(4.5);
                }
            } finally { dev.jasper.app.testsupport.EnterpriseLook.restoreDefault(); }
        });
    }

```

`contrast(Color, Color)` is the test's own helper; `WindowStatusBar` reads the installed theme (`ThemeZone.theme(null)`)
and `setConfiguration` refreshes its colours.

- [ ] **Step 2: Run the tests to verify they fail**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.EventLogTest' --tests 'dev.jasper.app.presentation.ProjectionsTest' --tests 'dev.jasper.app.workspace.EnterpriseDockWindowTest' --tests 'dev.jasper.app.workspace.DockChromeMatrixTest' --tests 'dev.jasper.app.notificationui.NotificationCardTest' --tests 'dev.jasper.app.workspace.ConfigurationStatusTest' --continue`
Expected: FAIL; `EventLogTest` does not compile ("cannot find symbol" for the four-argument constructor), and
`ProjectionsTest.theDockGroupHasLanded` and `EnterpriseDockWindowTest` fail on `RAILS` for `LABELLED` (the group has not
landed). `DockChromeMatrixTest`, `NotificationCardTest` and `ConfigurationStatusTest` pass already (the repairs exist; the
tests widen them to every palette).

- [ ] **Step 3: Write Event Log and land the group**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 12 step 3: Event Log, and the dock group lands (spec 10.7)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new, count in edits:
        if text.count(old) != count: raise SystemExit(f"{name}: expected {count} of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
A = "jasper-app/src/main/java/dev/jasper/app/"
edit(A + "application/NotificationsFeature.java", [
    ('''    /** {@code kit} supplies the chrome kit context the cards and the Notifications panel are built with. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this.center = center;''',
     '''    /** {@code kit} supplies the chrome kit context the cards and the Notifications panel are built with: the default look's panel. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit) {
        this(center, contributions, kit, new dev.jasper.app.presentation.AppPanels("Notifications", Slot.RIGHT_TOP));
    }

    /** As the three-argument form, with the panel's title and default slot under the look: Event Log at Bottom Left under labelled stripes. */
    NotificationsFeature(NotificationCenter center, Contributions contributions, java.util.function.Supplier<dev.jasper.ui.swing.KitContext> kit,
                         dev.jasper.app.presentation.AppPanels panels) {
        this.center = center;''', 1),
    ('''        panel = contributions.addPanel(PANEL_ID, "Notifications", icon, Slot.RIGHT_TOP, Optional.of(SHOW_HISTORY),
            site -> new HistoryPanel(kit.get(), center, site, focused(site.windowId())));''',
     '''        panel = contributions.addPanel(PANEL_ID, panels.notificationsTitle(), icon, panels.notificationsSlot(), Optional.of(SHOW_HISTORY),
            site -> new HistoryPanel(kit.get(), center, site, focused(site.windowId()), panels.notificationsTitle()));''', 1)])
edit(A + "notificationui/HistoryPanel.java", [
    ('''    public HistoryPanel(KitContext kit, NotificationCenter center, PanelSite site, boolean focused) {
        super(new BorderLayout());''',
     '''    public HistoryPanel(KitContext kit, NotificationCenter center, PanelSite site, boolean focused) {
        this(kit, center, site, focused, "Notifications");
    }

    /**
     * As the four-argument form, named {@code title} for screen readers: Event Log under labelled stripes (spec 10.7).
     *
     * @param kit the kit the panel's views are built with
     * @param center the notification store
     * @param site the tool window site the panel lives in
     * @param focused whether the window is active
     * @param title the panel's title
     */
    public HistoryPanel(KitContext kit, NotificationCenter center, PanelSite site, boolean focused, String title) {
        super(new BorderLayout());''', 1),
    ('''        getAccessibleContext().setAccessibleName("Notifications");''', '''        getAccessibleContext().setAccessibleName(title);''', 1)])
edit(A + "application/JasperApplication.java", [
    ('''        notificationViews = new NotificationsFeature(notificationCenter, contributions,
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME));''',
     '''        notificationViews = new NotificationsFeature(notificationCenter, contributions,
            () -> themes.appearance().context(dev.jasper.ui.theme.ThemeRoute.CHROME), presentation().projections().panels());''', 1)])
import re
path = pathlib.Path(A + "presentation/Presentation.java"); text = path.read_text(encoding="utf-8")
landed = re.search(r"public static final Set<Landed> LANDED = Set\.of\(([^)]*)\);", text)
if landed is None or "DOCK_CHROME" in landed.group(1): raise SystemExit("Presentation: LANDED moved, or the dock group is there already")
groups = [group.strip() for group in landed.group(1).split(",") if group.strip()] + ["Landed.DOCK_CHROME"]
path.write_text(text[:landed.start(1)] + ", ".join(groups) + text[landed.end(1):], encoding="utf-8")
PY
```

`presentation()` is read after the theme controller's start-up, where plan 5a's fallback (4.7) may have replaced the
presentation; that value's `AppPanels` is "Notifications" at Right Top, which is what a fallback window must show. The
`LANDED` script appends `Landed.DOCK_CHROME` to whatever groups plan 6 left there; keep the comment above it true.

- [ ] **Step 4: Run the tests to verify they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.application.*' --tests 'dev.jasper.app.presentation.*' --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.notificationui.*' --tests 'dev.jasper.app.plugins.*' --tests 'dev.jasper.app.architecture.*' :jasper-app:verifyApplicationArchitecture --continue`
Expected: PASS: `EventLogTest` 2, `ProjectionsTest` with the new test, `ChromeRenderTest` with the dock checks,
`EnterpriseDockWindowTest` 1,
`DockChromeMatrixTest` 1, `NotificationCardTest` and `ConfigurationStatusTest` over every palette, `PresentationReachTest`
unchanged (`windowLook()` still equals `WindowLook.of(projections)`, `WorkspaceChrome`'s equality now covering
`DockChrome`). Plan 6a's `ProjectionReadTest` (spec 16.1.7) already lets only `WorkspaceChrome` read
`WorkspaceLook.Stripes` and `FrameStyle`, and still passes: `DockChrome` and every dock class receive booleans. Plan 5b's
`EnterpriseHomeTest` and `EnterpriseFirstWindowTest` are not on the branch; at the merge with 5b they run again (a first
window under the shipped look then docks with labelled stripes).

- [ ] **Step 5: Add the ledger rows and commit**

```bash
python3 -I - <<'PY'
import pathlib
ledger = pathlib.Path("docs/design/theme-engine/migration.csv")
T = "jasper-app/src/test/java/dev/jasper/app/"
rows = [
    f"{T}application/EventLogTest.java,app.application,EventLogTest,nonvisual-helper,none,none,{T}application/EventLogTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 12; Event Log under labelled stripes",
    f"{T}workspace/EnterpriseDockWindowTest.java,app.workspace,EnterpriseDockWindowTest,nonvisual-helper,none,none,{T}workspace/EnterpriseDockWindowTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 12; a window under the shipped look",
    f"{T}workspace/DockChromeMatrixTest.java,app.workspace,DockChromeMatrixTest,nonvisual-helper,none,none,{T}workspace/DockChromeMatrixTest.java,Test/fixture ownership; New in Enterprise plan 7 Task 12; every kit under both dock styles"]
text = ledger.read_text(encoding="utf-8")
ledger.write_text(text + ("" if text.endswith("\n") else "\n") + "\n".join(rows) + "\n", encoding="utf-8")
PY
./gradlew :jasper-app:test --tests 'dev.jasper.app.appearance.ThemeMigrationCoverageTest'
git add jasper-app/src docs/design/theme-engine/migration.csv
git commit -F - <<'EOF'
Dock chrome: Event Log, and the dock group lands

The Notifications panel takes its title and default slot from the look's
AppPanels: Event Log at Bottom Left under labelled stripes, Notifications
at Right Top otherwise. Presentation.LANDED gains DOCK_CHROME, so a look
whose kit asks for stripes docks with labelled stripes, flush frames and
its own ui-state tables. A real window under the Enterprise projection and
every kit under both dock styles are rendered and checked; the notification count's
and the status bar's configuration text's repairs run over every palette.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 13: The whole dock against the mocks, the visual pairs, and the pixel proof

The surfaces are compared one by one in Tasks 5 to 10; this task checks that the fixture's whole dock puts every frame,
header and strip where the mocks put them once the stripes' extra thickness is allowed for (5 px a side, Rulings 4),
writes the full renders for inspection, adds the opt-in `tools/visual/pairs.json` entries for every compared crop (spec
16.1.4: "Each of plans 5 to 10 adds `tools/visual/pairs.json` crops pairing its Enterprise renders with the preserved
mocks"), and proves the default look's pixels once more.

**Files:**
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java`, `tools/visual/pairs.json`, `tools/visual/README.md`

**Interfaces:**
- Consumes: Task 9's `DockFixture`; Task 5's `DockMockTest.Variant` and `WORKSTATIONS` (Task 9); the file names
  `MockCrops.write` gives the compared crops (`jasper-app/build/dock-renders/<variant>-<surface>-render.png`).
- Produces: `DockMockTest.theWholeDockIsArrangedAsTheMocks`; 25 `dock-*` pairs in `pairs.json`.

- [ ] **Step 1: Write the test**

In `DockMockTest`, insert before `    // Later tasks add their surfaces above this line.`:

```java
    /**
     * The whole dock against the mocks' arrangement (2x): the mock's dock begins at (2, 180), below the toolbar row's rule,
     * and the fixture's at (0, 0); left of the terminal everything lies 10 px further right (the left stripe is 28 thick
     * against 23), right of it 10 px further left, and the bottom strip starts where the mock's rule above it is.
     */
    @Test void theWholeDockIsArrangedAsTheMocks() throws Exception {
        for (var workstation : WORKSTATIONS) {
            Variant variant = workstation.getKey();
            try {
                EnterpriseLook.install(variant.dark());
                try (var fixture = new DockFixture(EnterpriseLook.kit(), EnterpriseLook.theme(variant.dark()), workstation.getValue(), true)) {
                    fixture.render(1);
                    fixture.focus("sftp");
                    var render = variant.seen(fixture.render(2));
                    MockCrops.write(variant.name() + "-dock", render, variant.crop(2, 180, 2880, 1578));
                    int left = 2 * (fixture.stripes.left().getWidth() - 23), right = -2 * (fixture.stripes.right().getWidth() - 23);
                    String where = variant.name() + " dock";
                    assertThat(left).as(where + ": the stripes' extra thickness").isEqualTo(10);
                    assertPlaced(where + " SFTP header", fixture.header("sftp"), 48 - 2 + left, 0, 600);
                    assertPlaced(where + " File Servers header", fixture.header("servers"), 48 - 2 + left, 944 - 180, 600);
                    assertPlaced(where + " Sessions header", fixture.header("sessions"), 2136 - 2 + right, 0, 700);
                    assertPlaced(where + " Tunnels header", fixture.header("tunnels"), 2136 - 2 + right, 1014 - 180, 700);
                    var strip = fixture.stripes.bottomStrip();
                    assertThat(2 * strip.getY()).as(where + ": the bottom strip's rule").isEqualTo(1706 - 180);
                    assertThat(strip.getWidth()).as(where + ": the bottom strip spans the window").isEqualTo(DockFixture.WIDTH);
                    var rule = MockCrops.at(variant.crop(648, 1000, 1, 1), 0, 0);
                    assertThat(MockCrops.near(MockCrops.at(render, 648 - 2 + left, 1000 - 180), rule, variant.fillTolerance())).as(where + ": the left rule").isTrue();
                    assertThat(MockCrops.near(MockCrops.at(render, 2134 - 2 + right, 1000 - 180), rule, variant.fillTolerance())).as(where + ": the right rule").isTrue();
                    assertThat(MockCrops.near(MockCrops.at(render, 500 - 2 + left, 942 - 180), rule, variant.fillTolerance())).as(where + ": the rule between the left frames").isTrue();
                }
            } finally { EnterpriseLook.restoreDefault(); }
        }
    }

    /** A header strip's place in the fixture at 2x against the mock's, and its width. */
    private static void assertPlaced(String where, Rectangle header, int x, int y, int width) {
        assertThat(2 * header.x).as(where + " x").isEqualTo(x);
        assertThat(2 * header.y).as(where + " y").isEqualTo(y);
        assertThat(2 * header.width).as(where + " width").isEqualTo(width);
    }

```

- [ ] **Step 2: Run the test**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DockMockTest'`
Expected: PASS (7 tests). This task adds no production code: the arrangement follows from Tasks 1 to 12; a failure here
is a fixture or layout defect in those tasks (look at `jasper-app/build/dock-renders/<variant>-dock-{render,mock}.png`),
fixed there, never by moving a mock coordinate.

- [ ] **Step 3: Add the visual pairs**

Each pair carries plan 4c's `min_agreement` and `colours`, so `check_pairs.py` checks it (Execution notes R17): the floor
is the edge agreement `compare_all.py` measured at `759b2b7d9` under 6a's fixtures, less 0.05, rounded down to 0.05, and
the colours are the theme roles each crop paints that the mock holds (the Unlock Vault mock's under the scrim).

```bash
python3 -I - <<'PY'
"""Plan 7 Task 13 step 3: the opt-in dock pairs (spec 16.1.4), checked by check_pairs.py."""
import json, pathlib
path = pathlib.Path("tools/visual/pairs.json")
pairs = json.loads(path.read_text(encoding="utf-8"))
if any(p["name"].startswith("dock-") for p in pairs): raise SystemExit("pairs.json already has dock pairs")
R = "docs/design/enterprise/reference/"
mocks = {"light": "01 Workstation/Workstation - Connected (plugins in use).png",
         "light-disconnected": "01 Workstation/Workstation - Disconnected.png",
         "dark": "08 Dark Mode/Workstation Dark - Connected (plugins in use).png",
         "vault": "07 Credential Vault/Credential Vault 1 - Unlock Vault (over Workstation).png"}
offset = {"light": 0, "light-disconnected": 0, "dark": 0, "vault": 64}
headers = {"sftp-header": (48, 180, 600, 50), "servers-header": (48, 944, 600, 50),
           "sessions-header": (2136, 180, 700, 50), "tunnels-header": (2136, 1014, 700, 50)}
surfaces = {"left-stripe": (2, 180, 44, 330), "right-stripe": (2838, 180, 44, 270), "bottom-strip": (2, 1708, 520, 48)}
# Measured edge agreement less 0.05, rounded down to 0.05 (Execution notes R17).
floors = {"kit-sftp-header": 0.85, "kit-servers-header": 0.85, "kit-sessions-header": 0.85, "kit-tunnels-header": 0.85,
          "left-stripe": 0.85, "right-stripe": 0.90, "bottom-strip": 0.80}
disconnected = {"kit-sftp-header": 0.90, "kit-servers-header": 0.90, "kit-sessions-header": 0.85, "kit-tunnels-header": 0.90}
# The roles each crop paints: a header's surface (focused SFTP and Sessions), its rule and title; a stripe's open tile,
# label and surface; the strip's surface, label and divider. Under the Unlock Vault scrim the scrimmed values.
light = {"focused": "#D9DFEA", "header": "#E1E3E6", "rule": "#C4C7CC", "text": "#1E1F22", "tile": "#D3D9E1",
         "stripe": "#E4E6E9", "divider": "#D3D6DA"}
dark = {"focused": "#3D4658", "header": "#36393E", "rule": "#2A2C30", "text": "#DFE1E5", "tile": "#50545B",
        "stripe": "#383B40", "divider": "#4A4D53"}
vault = {"focused": "#ABB0B9", "header": "#B2B3B6", "rule": "#9B9DA1", "text": "#18181B", "tile": "#A7ABB2",
         "stripe": "#B4B6B8", "divider": "#A7A9AC"}
roles = {"light": light, "light-disconnected": light, "dark": dark, "vault": vault}
def colours(variant, name):
    c = roles[variant]
    if name.endswith("-header"):
        return [c["focused" if name in ("kit-sftp-header", "kit-sessions-header") else "header"], c["rule"], c["text"]]
    if name == "bottom-strip": return [c["stripe"], c["text"], c["divider"]]
    # The Unlock Vault right stripe's label ink is lighter than its 2x2 blocks: tile and surface only.
    return [c["tile"], c["stripe"]] if (variant, name) == ("vault", "right-stripe") else [c["tile"], c["text"], c["stripe"]]
for variant, mock in mocks.items():
    o = offset[variant]
    entries = [(f"kit-{name}", crop) for name, crop in headers.items()]
    if variant != "light-disconnected": entries += list(surfaces.items())
    for name, (x, y, w, h) in entries:
        floor = (disconnected if variant in ("light-disconnected", "vault") and name in disconnected else floors)[name]
        pairs.append({"name": f"dock-{variant}-{name}", "mock": R + mock, "crop": [x + o, y + o, w, h],
                      "render": f"jasper-app/build/dock-renders/{variant}-{name}-render.png",
                      "min_agreement": floor, "colours": colours(variant, name)})
# ensure_ascii=False keeps the other pairs' "…" and the mocks' narrow no-break spaces as they are written.
path.write_text(json.dumps(pairs, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
print(sum(1 for p in pairs if p["name"].startswith("dock-")), "dock pairs")
PY
python3 -I - <<'PY'
import pathlib
path = pathlib.Path("tools/visual/README.md"); text = path.read_text(encoding="utf-8")
old = "(the classic look's `classic-*`, the `run-window-*` and the `chrome-*` pairs)"
new = "(the classic look's `classic-*`, the `run-window-*`, the `chrome-*` and the `dock-*` pairs)"
if text.count(old) != 1: raise SystemExit("tools/visual/README.md: check_pairs' line moved")
text = text.replace(old, new)
old = "Run with `python3 -I`; the tools need Pillow and NumPy."
new = ("The `dock-*` pairs compare the Enterprise dock chrome's crops that `DockMockTest` writes under\n"
       "`jasper-app/build/dock-renders/` (run `./gradlew :jasper-app:test --tests '*DockMockTest'` first).\n\n" + old)
if text.count(old) != 1: raise SystemExit("tools/visual/README.md moved")
path.write_text(text.replace(old, new), encoding="utf-8")
PY
```

Expected: the first script prints `25 dock pairs` (four header pairs for each of the four mocks, and the three stripe
and strip pairs for the three mocks whose stripes the tests compare). Then check that every render path exists and run
the check:

```bash
python3 -I - <<'PY'
import json, pathlib
missing = [p["render"] for p in json.load(open("tools/visual/pairs.json")) if p["name"].startswith("dock-") and not pathlib.Path(p["render"]).is_file()]
print("missing:", missing)
PY
python3 -I tools/visual/check_pairs.py jasper-app/build/dock-pairs dock-
```

Expected: `missing: []`; the dock run prints 25 `PASS` lines (measured at `759b2b7d9`: the kit headers 0.900 to 0.975,
the stripes 0.923 and 0.964, the strip 0.852 and 0.853) and writes one side-by-side, blend and report per dock pair
under `jasper-app/build/dock-pairs/`. Look at each side-by-side once; the JUnit comparisons are the gate.

- [ ] **Step 4: The pixel proof**

Run Task 1 Step 1's two gallery commands, `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DefaultDockPixelsTest'`
and its `diff`/`cmp` line.
Expected: `PIXELS-UNCHANGED`.

- [ ] **Step 5: Commit**

```bash
git add jasper-app/src/test/java/dev/jasper/app/workspace/DockMockTest.java tools/visual/pairs.json tools/visual/README.md
git commit -F - <<'EOF'
Dock chrome: the whole dock against the mocks, and the visual pairs

DockMockTest checks that the fixture's whole dock puts every header strip,
rule and the bottom strip where the Workstation, Dark Mode and Unlock Vault
mocks put them, allowing for the stripes' 28 px floor, and writes the full
renders. pairs.json gains the opt-in dock pairs for every compared crop.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

### Task 14: Documentation, STATUS and the whole build

Spec 17's documents that describe what this plan changed get the smallest accurate edit; the two specifications spec
19.2 amends for the dock (Dock layout and Notifications) gain their banner line; `docs/STATUS.md` gains this plan's entry
at the top, in the style of the plan 1 and plan 2 entries; and the whole build runs.

**Files:**
- Modify: `docs/app-architecture.md:250`, `docs/ui-architecture.md:247-248,269`, `docs/themes.md:135-137`,
  `docs/configuration.md:188,659`, `docs/notifications.md:34-35`
- Modify: `docs/superpowers/specs/2026-10-01-jasper-dock-layout-design.md:3-4`, `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md:3-11` (their status banners)
- Modify: `docs/STATUS.md` (a new entry at the top), this plan's file (its status banner)

**Interfaces:**
- Consumes: everything above; the result XML under `*/build/test-results/test/`.
- Produces: nothing for later tasks.

- [ ] **Step 1: Edit the documents**

```bash
python3 -I - <<'PY'
"""Plan 7 Task 14 step 1: the smallest accurate edits (spec 17)."""
import pathlib
def edit(name, edits):
    path = pathlib.Path(name); text = path.read_text(encoding="utf-8")
    for old, new in edits:
        if text.count(old) != 1: raise SystemExit(f"{name}: expected 1 of {old[:80]!r}, found {text.count(old)}")
        text = text.replace(old, new)
    path.write_text(text, encoding="utf-8")
edit("docs/app-architecture.md", [
    ('''Tool windows paint `surface.panel` (theme.md's tool-window body) through `ToolWindowSurface`, their headers included; the stripes stay chrome.''',
     '''Tool windows paint `surface.panel` (theme.md's tool-window body) through `ToolWindowSurface`, their headers included; the stripes stay chrome.
Under a look whose kit asks for stripes (`tool_windows = "stripes"`), `WorkspaceChrome.of` gives each window a
`DockChrome` with labelled stripes and flush frames: `StripeSet.LabelledStripes` puts two `LabelledStripe`s (labels
"N: Title" when the toggle's shortcut is a digit with a modifier) and a `BottomStrip` for the bottom slots in place of
the two `ToolStripe` rails, and `ToolWindowArea` divides the frames by painted 1 px rules with 6 px drag areas and gives
every frame a header strip, focused while it holds the focus owner. Dock geometry reads the window's kit.
`UiState` keeps such a look's dock under `[look."<provider id>"]`, and its Notifications panel is Event Log in the
bottom strip (`AppPanels`).''')])
edit("docs/ui-architecture.md", [
    ('''rules and action widths, status dot, rail badges, connected dot, split dividers, drop guides, find and title-bar rules,
palette shadow) over named roles;''',
     '''rules and action widths, status dot, rail badges, connected dot, split dividers, drop guides, find and title-bar rules,
palette shadow, and the classic dock's rules, stripe labels, count pills and header strips, whose colours it paints
through the pairs `ChromePainter.DOCK_PAIRS` declares) over named roles;'''),
    ('''releases them. Host integration only, not part of the plugin SDK (plugins reach it through `PanelHost.setHeader`).''',
     '''releases them. A host frame (`PanelHostHeader.Frame` `FLUSH` or `FLUSH_FOCUSED`) makes the header a flush frame's
header strip: the strip's surface over a 1 px rule, a parenthesised count, a muted detail and actions 2 px apart. Host
integration only, not part of the plugin SDK (plugins reach it through `PanelHost.setHeader`).''')])
edit("docs/themes.md", [
    ('''`border.divider`. Jasper otherwise computes them from the variant's roles; a listed one is used as given.''',
     '''`border.divider`. Jasper otherwise computes them from the variant's roles, the surfaces leaning toward the variant's
  text (lighter than the window in a dark variant, darker in a light one); a listed one is used as given.''')])
edit("docs/configuration.md", [
    ('''Integer 28–64 logical pixels; both tool window stripes and their buttons scale with it; out-of-range values use the nearest limit with a warning''',
     '''Integer 28–64 logical pixels; both tool window stripes and their buttons scale with it (under the Enterprise look, the least thickness of the labelled stripes and the bottom strip); out-of-range values use the nearest limit with a warning'''),
    ('''dock's sizes and split ratios, which panels are open, stripe visibility, main-window size/position and maximized''',
     '''dock's sizes and split ratios (each look apart: the Enterprise look keeps its own), which panels are open, stripe visibility, main-window size/position and maximized''')])
edit("docs/notifications.md", [
    ('''The Notifications button on the right stripe opens the panel, which starts in the upper half of the window's right
side;''',
     '''The Notifications button on the right stripe opens the panel, which starts in the upper half of the window's right
side (under the Enterprise look the panel is titled Event Log and starts in the bottom strip, on its left);''')])
banner = ("Amended on 2026-10-15 by the [Enterprise presentation design](2026-10-15-jasper-enterprise-presentation-design.md): "
          "{what}; everything else in this document stands.")
# Each banner is its own paragraph after the status paragraph, as the Snippets and Remote designs carry theirs.
edit("docs/superpowers/specs/2026-10-01-jasper-dock-layout-design.md", [
    ('''plan 2 (drag and drop) were both implemented on branch `claude/dock-layout` on 2026-10-02.
''',
     '''plan 2 (drag and drop) were both implemented on branch `claude/dock-layout` on 2026-10-02.

''' + banner.format(what="stripe, frame and bottom-strip presentation and saved layouts are per look; the model and the six slots are unchanged") + "\n")])
edit("docs/superpowers/specs/2026-10-02-jasper-notifications-design.md", [
    ('''(Settings)](../plans/2026-10-04-notifications-6-settings.md) is implemented on branch `claude/notifications-6`.
''',
     '''(Settings)](../plans/2026-10-04-notifications-6-settings.md) is implemented on branch `claude/notifications-6`.

''' + banner.format(what="under the Enterprise look the panel is titled Event Log, with default slot BOTTOM_LEFT") + "\n")])
PY
```

Both lines are spec 19.2's, word for word (its table's `<what>` column). The plan writes the banner's link only inside
this code block: `AppDocumentationTest` resolves every Markdown link outside code fences in `docs/`, this plan
included, against the plan's own folder (Execution notes R16).

- [ ] **Step 2: Run the whole build**

Run: `./gradlew check --continue`
Expected: BUILD SUCCESSFUL. Then count the results from the XML:

```bash
python3 -I - <<'PY'
import pathlib, xml.etree.ElementTree as ET
tests = failures = errors = skipped = 0
for path in pathlib.Path(".").glob("**/build/test-results/test/*.xml"):
    if ".claude" in path.parts: continue
    root = ET.parse(path).getroot()
    tests += int(root.get("tests", 0)); failures += int(root.get("failures", 0)); errors += int(root.get("errors", 0)); skipped += int(root.get("skipped", 0))
print(f"{tests} tests, {failures} failures, {errors} errors, {skipped} skipped")
PY
```

Expected: `N tests, 0 failures, 0 errors, M skipped`, N above plan 6's count by this plan's new tests. Write the line
into Step 3's entry.

- [ ] **Step 3: Write the STATUS entry and the status banner**

Insert at the top of `docs/STATUS.md`, after `# Jasper — Status and Handoff` and its blank line, an entry in the style of
the plan 1 and plan 2 entries (one paragraph; the numbers and the commit range filled in from this branch):

```text
**Enterprise presentation, plan 7: dock chrome — <date>** (`claude/enterprise-dock-chrome`, worktree
`.claude/worktrees/enterprise-7`, `<first>..<last>`, then this documentation commit;
[plan](superpowers/plans/2026-10-16-enterprise-7-dock-chrome.md),
[spec](superpowers/specs/2026-10-15-jasper-enterprise-presentation-design.md)). A look whose kit says
`tool_windows = "stripes"` (Enterprise) docks the classic way (spec 10.7): `WorkspaceChrome.of` reads the dock's two
points into `DockChrome`, which builds `StripeSet.LabelledStripes` (two `LabelledStripe`s, labels reading bottom to top
on the left and top to bottom on the right, "N: Title" when the toggle's live shortcut is a digit with Ctrl, Alt or
Meta, open labels on `surface.control.selected`, badges as count pills; and a `BottomStrip` for the bottom slots with
dividers) instead of the two `ToolStripe` rails, all through one `DockStripe` contract; the drop resolution gains
horizontal groups and a hidden bottom strip takes drops at the workspace's bottom edge; flush frames are divided by
painted 1 px `border.window` rules (held on `surface.app`) with 6 px drag areas, and every frame has a header strip
(native or borrowed through `PanelHostHeader.Frame`) on `surface.header`, `surface.header.active` while it holds the
focus owner, with a parenthesised count, a muted detail, 16 px actions 2 apart and the minus for Hide;
`ToolWindowArea`, `ToolWindowHeader` and `DockDrag` read the window's kit (spec 6.6). `UiState` keeps a non-default
look's dock under `[look."<id>"]` (the default look's file is unchanged), loaded with the presentation's `DockLayoutKey`;
the Notifications panel is Event Log at Bottom Left under the look (`AppPanels`); `Presentation.LANDED` holds
`DOCK_CHROME`. Derived surfaces lean toward the variant's text (`SurfaceContrast.beyond`): Slate Cobalt Dark's
`surface.header.active` is #2d3546, lighter than its other headers (plan 2's and 5b's hand-off); no default painter
reads it. The classic catalog gains the pill, header-action and header-gap values, `STRIPE_LABEL_PADDING` 9 and
`STRIPE_LABEL_GAP` 0. Mock matching: `DockMockTest` renders every surface at 2x under the Enterprise kit and theme
(plan 6a's `EnterpriseLook` fixtures until plan 5b) and compares it with its crop of the Workstation (Connected,
Disconnected), Dark Mode and Unlock Vault mocks (fills within 3, 5 under the Unlock Vault scrim, run counts and inks, the
first ink a padding sets and the icons' centres within 2 px at 2x), prints the probes that follow the UI face's glyphs
until plan 5b's Plex (`MockProfile.fonts`, `MockCrops.fontsFloor`: <the measured residuals>), and checks the whole
dock's arrangement; `tools/visual/pairs.json` gains 25 `dock-*` pairs that `check_pairs.py` passes (<agreements>). Residuals:
<the residuals the comparisons recorded, or "none beyond the plan's Rulings 6, 9, 14 and 15">. Zero pixel change for the
default look: the headless gallery's files for Slate Cobalt Dark and Light and `DefaultDockPixelsTest`'s two renders are
byte-identical to the base. Rulings (plan, "Rulings"): <one line each for 1 to 26, the substance in a few words>;
Execution notes (2026-10-17) R1 to R23 <one line each>.
Deviations from the plan's text: <each one, or none>. Hand-offs: plan 8 may read `DockStripe` for the Run window's corner
home; plan 10 sets Remote's and File Server's Enterprise anchors (Rulings 18); 4a Rulings 16's three package-test
repairs are all taken here (the kit header's connected dot as a declared pair, `NotificationCardTest`'s count and
`ConfigurationStatusTest`'s configuration text over every palette), so none is left open; at the merge with plan 5b,
Execution notes R19's items. `./gradlew check --continue`:
**N tests, 0 failures, 0 errors, M skipped**. GUI checks for the user (spec 16.2): stripe drag and drop including the
bottom strip, the labelled stripes' numbers after a Keymap change, the focused header strip, and Enterprise Dark's dock
against the Dark Mode mock. Not merged, not pushed.
```

Replace every `<…>` with the branch's facts before committing; none may remain (`grep -n '<first>\|<date>\|<the residuals\|<the measured\|<agreements>\|<one line\|<each one' docs/STATUS.md`
prints nothing). Add this plan's status banner under its title, in the style of the earlier plans' banners: "Status:
executed on 2026-10-…; <deviations from the plan's text, with the plan 6 name changes of 'Plan 6's names'>".

- [ ] **Step 4: Commit**

```bash
git add docs
git commit -F - <<'EOF'
Dock chrome: documentation and status

The architecture, UI architecture, themes, configuration and notifications
guides describe the classic dock, its painters and header strips, the side
rule of derived surfaces, the per-look dock in ui-state and Event Log; the
Dock layout and Notifications designs gain their amendment lines; STATUS
records the plan.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Self-review

- **Spec coverage (19.1 row 7, 10.7):** labelled stripes and their numbers — Tasks 5 and 7; the bottom strip and its
  pills — Task 6 (pills painted by Task 4's `countPill`); frames and header strips, active and inactive, with counts and
  icons — Tasks 8, 9 and 10; horizontal drop geometry — Tasks 2, 3 and 6; the dock layout per look (`DockLayoutKey`) —
  Task 11, with Enterprise's first-start slots from the registered defaults (Rulings 18); Event Log — Task 12; the chrome
  renders extended — Tasks 12 (`EnterpriseDockWindowTest`, `DockChromeMatrixTest`) and 13; spec 6.6's dock statics —
  Task 1; 16.1.1's count pills in the declared pairs — Task 4; 16.1.4's crops and pairs — Tasks 5, 6, 8, 9, 10 and 13;
  19.2's dock and notifications amendments — Task 14.
- **Mock targets:** 01 Workstation Connected and Disconnected, 08 Dark Mode and 07 Credential Vault 1 are compared for the
  stripes, the bottom strip, the rules, the native and kit header strips and the whole arrangement.
- **Pixel rule:** the default look's proof runs after Tasks 1, 3, 4, 8, 10 and 13.
- **Types:** `DockStripe`, `StripeSet`, `StripeLabels`, `DockChrome`, `ToolStripe.Button`'s seven components,
  `DockDropResolver.Axis`, `PanelHostHeader.Frame`, `UiState.load(Path, String)` and `NotificationsFeature`'s four
  arguments are used with the same names in every task that produces or consumes them.

## Execution notes (2026-10-17)

**Refresh.** Every anchor, quoted text, path, signature and line number this plan relies on was re-checked against the
worktree at `759b2b7d9` (`main`: plans 3a to 3e, 4a to 4c, 5a, 6a, 6b and 8a; not 5b or 9a, which run in parallel), on
which the plan's two commits sit. Method, without Gradle: a dry run in a scratch copy of the tree applied every step in
task order (each edit script with its stated counts, each created file, each insertion, each ledger row), compiled
jasper-terminal, the SDK, Buddy, the testkit, jasper-ui (main, test fixtures, tests) and jasper-app (main, tests) with
`javac` (JBR 25, the Gradle cache's jars) after each task, and ran the tests through the JUnit 6.1.3 launcher headless.
The plan as written failed in Tasks 1 (6a's planned `WorkspaceChrome` shape, 5b's `EnterpriseResources`), 4 (a
`NewRolesTest` probe, `DerivedColorTest`'s pinned inputs, a half-image sampler), 5, 6, 9 and 10 (the mock comparisons
under the platform face, a divider-run bug, the kit headers' actions), 8 (a missing import), 12 (`EventLogTest`'s
presence supplier) and 14 (an anchor 6b moved, and a link `AppDocumentationTest` refuses); after the edits below, the
blocks re-extracted from this file, replayed from `759b2b7d9` into a fresh tree, give the verified tree and every new
test passes: `MockCropsTest` 3, `DefaultDockPixelsTest` 1, `DockChromeTest` 1, `HorizontalDropTest` 3, `StripeSetTest`
3, `HeaderSideTest` 3, `DockRolesTest` 1, `LabelledStripeTest` 8, `BottomStripTest` 3, `DockDragBottomStripTest` 3,
`StripeNumbersTest` 2, `StripeNumbersWindowTest` 3, `FlushFramesTest` 5, `HeaderStripTest` 5, `PanelHeaderStripTest` 2,
`UiStateLookTest` 3, `EventLogTest` 2, `EnterpriseDockWindowTest` 1, `DockChromeMatrixTest` 1 and `DockMockTest` 7, with
`WorkspaceMetricsTest`, `PaintedContrastTest`, `ProjectionsTest` and `ConfigurationStatusTest` one test more each
(65 tests: 59 in jasper-app, 6 in jasper-ui). The whole jasper-app and jasper-ui suites fail, class by class, exactly
the tests the unchanged tree fails in that harness (pty sessions, staged plugins, packaged jars and build info, slf4j,
which need Gradle's task inputs): 3,326 jasper-app tests at Task 12 against the base's 3,268, the same 208 failing in
both. `DefaultDockPixelsTest`'s two renders are byte-identical to the base after Tasks 1, 3, 4, 8, 9, 10 and 13, and the
headless gallery (`KitGallery`, Slate Cobalt Dark and Light at 1×, 161 files a side) after Tasks 4, 10 and 13, the
tasks that touch jasper-ui; `javadoc -Xdoclint:all,-missing` over jasper-ui
main and `-Xdoclint:all` over jasper-app main report no error; the source-hygiene scan of the 62 touched Java files is
clean; the 23 new ledger rows have eight fields each and `ThemeMigrationCoverageTest` passes after every task;
`check_pairs.py` passes the 25 dock pairs; `AppDocumentationTest` passes with this file. The dry run did not run Gradle
(`check`, the architecture tasks, the packaged-jar tests such as `ProjectionReadTest`): the tasks still run their stated
commands.

Changes made to this plan's text: the Workspace paragraph, "Before you start" (no rebase; checks at `759b2b7d9`) and the
names table; the Global Constraints' mock-matching rule; facts 6, 8 to 10, 12, 14 to 16, 19 and 21 to 24 and the
facts' heading; the file structure; Task 1 (the `WorkspaceChrome` script, `MockCrops.OUT`, `MockCrops.fontsFloor`, the
`threshold()` grep); Task 4 (the Files lines, the enum-tail guard, `DockRolesTest`, `DerivedColorTest`, the pill
sampler, the imports sentence, the expected results); Task 5 (`EnterpriseLook`'s two methods, the Interfaces,
`DockMockTest`'s javadoc, imports and `compareStripe`, the commit message); Task 6 (the bottom-strip comparison and its
calibration text); Task 8 (`java.awt.Rectangle`); Task 9 (`compareHeader`, the expected results); Task 10 (the dot
sampler, `DockFixture`'s kit headers, the expected results); Task 12 (`EventLogTest`, `EnterpriseDockWindowTest`'s
javadoc, the `ProjectionsTest` and verification text, the commit message); Task 13 (the pairs' floors and colours,
`ensure_ascii`, `check_pairs.py`); Task 14 (the Files lines, the themes.md anchor, both spec banners scripted, the
STATUS template); `DockMockTest.Variant` (`fillTolerance`, the tile tolerance's comment) and the Files lines of Tasks 1,
3, 11 and 12.

### Pre-flight scan

Pairs of tasks that share a file or an interface:

| Tasks | Shared file or interface | Agreement | Ruling |
|---|---|---|---|
| 1 → 3 → 6 | `DockChrome.java`: created (1), `stripes(Kit)` (3), the labelled branch (6) | Each later anchor is the earlier task's output | R21 |
| 1 → 3, 12 | `WorkspaceChrome` (6a/6b's merged shape): `dock()`, its equality; `PresentationReachTest`'s `windowLook()` comparison (12) | Disagreed (6a's planned constructor); rewritten to the merged one, equality covering `DockChrome` | R2 |
| 1 → 3 → 8 → 9 → 10 | `ToolWindowArea.java`: the kit and flush constructor, `Host` an inner class (1); focus, rules, hit areas, `metrics()` (8); the borrowed header's frame (10) | Agree in task order; counts hold | R21 |
| 1 → 8 → 9 | `ToolWindowHeader.java`: kit, styles, painter, flush (1); `setFocused` (8); the strip (9) | Agree | R21 |
| 1 → 3 | `WindowContent.java`: Task 3's anchor is Task 1's `new ToolWindowArea(…, themes.kit(), look().dock().flushFrames())` | Agree | none |
| 1 → 3 → 6 | `DockDrag.java`: the threshold read (1), the stripe contract and `labels` (3), drops onto the strip (6) | Agree; seven `for (ToolStripe stripe` loops | none |
| 1 → 5 to 13 | `MockCrops` (`OUT`, `render`, `runs`, `write`, `fontsFloor`) | `OUT` read 5b's `EnterpriseResources.ROOT`; now 6a's `MockProfile.ROOT` | R4, R5 |
| 1 → 6, 7, 12 | `DockChromeTest.CLASSIC_DOCK`, `classicDock()` | Agree | none |
| 2 → 3, 5, 6, 8 | `DockDropResolver.Group.axis`, `Axis`, `DockGeometry.Metrics.at(scale, divider)` | Agree | none |
| 3 → 5, 6, 7, 9 | `DockStripe`, `StripeSet`, the seven-component `ToolStripe.Button`, `DockDrag.labels` | Agree | none |
| 3 → 7 | `WindowContributions.java`: Task 7's anchors (`labels`, the `windows` list, the bottom strip's render) are Task 3's output | Agree | none |
| 4 → 5, 6, 8, 9, 10 | `ChromePainter` dock painters, `DOCK_PAIRS` indices (1 and 2 in 5; 5 to 10 in 9; 11 and 12 in 10), the seven roles | Agree | none |
| 4 → 10 | `PaintedContrastTest`: Task 4's helpers before `searchHint`; Task 10 inserts at the same anchor and uses `anywhere` | `hasColor` scans an image's right half only; both samplers missed | R9 |
| 4 ↔ 9a (parallel) | `DesignMetrics`' enum tail, `compact-roles.tsv`, `classic-roles.tsv` | `DockRolesTest` follows the chrome roles rather than closing the enum | R8, R18 |
| 5 → 6, 8, 9, 10, 13 | `DockMockTest` (`Variant`, `VARIANTS`, the closing marker, `STRIPE_FLOOR`, `compareHeaders`) | Task 8 used `Rectangle` before Task 9 imports it; the Unlock Vault fills missed by 1 | R6, R10 |
| 5 → 6, 8, 9, 10, 12, 13 | `EnterpriseLook.theme(boolean)`, `install(boolean)` beside 6a's `kit()` | Task 5 anchored on 5b's `registerFonts()`, absent | R4 |
| 5 → 6, 9 | `StripeLabels`, `LabelledStripe.describe`, `LabelledStripeTest.window`, `inks` | Agree | none |
| 8 → 10 | `Host.focused`, `focusedHeader`, `ToolWindowArea.focusMoved` | Agree | none |
| 9 → 10, 13 | `DockFixture` (kit headers, `header(id)`, `focus`), `DockMockTest.compareHeaders` | The kit headers' actions as a header context stack made the strip 29 tall | R11 |
| 11 → 12 | `UiState.inMemory(String)`, `look()` | Agree | none |
| 12 ↔ 6b | `ConfigurationStatusTest` and 6b's `WindowPairsTest` | 6b samples the classic ruled cells only | R13 |
| 12 ↔ 6a | `ProjectionsTest`'s landing test, `ProjectionReadTest`, `ChromeRenderTest.check` | Agree; no edit to the landing test | R14 |
| 12 ↔ 9a (parallel) | `Presentation.LANDED` (9a adds `SETTINGS`), `ProjectionsTest`'s end, `ChromeRenderTest.check`'s top | Reconciled at merge | R18 |
| 13 ↔ 4c, 6a, 6b, 8a | `tools/visual/pairs.json`, `README.md`, `check_pairs.py` | The plan's pairs had no floors (`check_pairs.py` skips them) and its dump escaped the others' "…" | R17 |
| 14 → this file | `AppDocumentationTest` resolves links in `docs/`, this plan included | The plan as committed failed it (also at the base) | R16 |
| 1 to 13 | `docs/design/theme-engine/migration.csv`, append-only | Eight fields per row; every candidate has its row at its task's end | R18 |
| all ↔ 5b (later) | `EnterpriseLook`, the fixture theme, the printed probes, `derived-roles-enterprise.tsv` | 5b's merge items | R19 |

Each task against itself:

| Task | Agreement of its own text | Ruling |
|---|---|---|
| 1 | The `WorkspaceChrome` script quoted 6a's planned supplier constructor; `MockCrops.OUT` read 5b's `EnterpriseResources`; the `threshold()` grep promised two lines it cannot print | R2, R4, R15 |
| 2 | Agree; `HorizontalDropTest` 3 | none |
| 3 | Agree; `StripeSetTest` 3; the default look's pixels unchanged | none |
| 4 | The script probed `NewRolesTest`'s tail check, which checks a run since 6a (it then wrote nothing and `DockRolesTest` would close the enum); `DerivedColorTest` pins `surface.header.active`'s inputs; the accent pill was never sampled | R8, R9 |
| 5 | Anchored on 5b's `registerFonts()`; the second label's edges and the agreeing share follow the platform face's widths (0.821 to 0.847 against 0.92) | R4, R5 |
| 6 | The dividers' runs split each 2 px divider in two (gap 0); the third divider and the pill fell outside the 270 px render in the platform face; the error pill's ground is too near the stripe's surface for the 30 threshold | R5, R7 |
| 7 | Agree; `StripeNumbersTest` 2, `StripeNumbersWindowTest` 3 | none |
| 8 | `DockMockTest` had no `Rectangle` import yet; the Unlock Vault rule missed by 1 in blue | R6, R10 |
| 9 | The red step expected a colour failure (the strip's height fails first); the title ends and the agreeing share follow the face | R5, R15 |
| 10 | The kit headers grew to 29 px with a header context stack; the dot sampler scanned the right half | R9, R11 |
| 11 | Agree; `UiStateLookTest` 3; one `UiState.load` call | none |
| 12 | `NotificationCenter` takes a `Delivery.Presence` supplier; 5b's tests are absent; the landing test needs no edit | R12, R13, R14 |
| 13 | The pairs had no floors or colours for `check_pairs.py`; `json.dumps` rewrote ten unrelated lines | R17 |
| 14 | themes.md's anchor line now ends with `border.divider` (6b); the Notifications banner was a manual step whose prose link breaks `AppDocumentationTest`; the STATUS template named the shipped kit and Plex | R16, R19 |

### Rulings

The plan's own Rulings are 1 to 26; these are numbered R1 to R23 (Execution notes).

R1. **Refreshed at `759b2b7d9` by a dry run, not Gradle** — the orchestrator's instruction; the method above — cost if
wrong: none; each task still runs its stated commands.

R2. **Plan 6's names are the merged ones** (refines Rulings 2): `WorkspaceChrome` keeps the look's choices
(`Header`, `Tabs`, `Status`, `runItem`) and its private constructor takes them, so Task 1's script adds `DockChrome
dock` after `runItem`, `DockChrome.STANDARD` to `STANDARD`, `dock()` after `title()`, and `dock` to `equals` and
`hashCode` — 6a's `PresentationReachTest` compares `windowLook()` through that equality (6b R29) — cost if wrong: none.

R3. **No rebase; the base is checked** — the branch sits on `759b2b7d9` and the orchestrator forbids rebase and merge;
"Before you start" checks the merged plans' marks instead — cost if wrong: one re-refresh if `main` moves first.

R4. **Enterprise is plan 6a's fixture until plan 5b** (6a R13, 6b R19): `EnterpriseLook.theme(boolean)` is
`theme().generated(dark)` over `enterprise-fixture.json` and `install(boolean)` installs it under 4a's fixture kit, both
added before `restoreDefault()`; `MockCrops.OUT` resolves from `MockProfile.ROOT`; the fixture's Light open tile is
#D3D9E1 (5b's #D7D9DC; the mock's #CFD4DC), inside `tileTolerance` 10 — plan 5b is not merged — cost if wrong: at 5b's
merge three method bodies change (R19).

R5. **Probes that follow the UI face's glyphs are printed until plan 5b** (6a R26 and R29, 6b R36): through
`MockProfile.fonts` (a run edge after the first, a divider, the pill's start) and `MockCrops.fontsFloor` (an agreeing
share); asserted are the fills and rules, the run counts and inks, the thickness, the first ink a padding and the rail
gap set, the icons' centres, the pill's width and the divider colours. Measured at `759b2b7d9` in the platform face:
the stripes' second labels 1 to 6 px off at 2x and agreement 0.821 to 0.847 (floor 0.92); the bottom strip's dividers
8 to 14 px right of the mock's, the pill 17, agreement 0.856 to 0.860 (0.92); the native headers' titles end 2 to 6 px
past the mock's, agreement 0.931 to 0.976 (0.94); the kit headers 3 to 6 px, 0.933 to 0.975 — the fixture kit draws
the platform face where the mocks draw IBM Plex, and every failing probe was a text advance; asserting them now would
force knobs to fit the wrong face — cost if wrong: these go unchecked until 5b's merge turns them into assertions.

R6. **Under the Unlock Vault scrim a fill or rule compares within 5, else 3** (`Variant.fillTolerance()`): the mock's
scrim has a cool cast (white reads #C9CACC; its rule reads #9DA0A5 where black at 21 % gives #9B9DA1) — measured; the
render's scrim is the app's black — cost if wrong: a 2 ΔRGB looser check on four Vault fills.

R7. **The bottom strip's comparison**: dividers are runs with gap 1 (gap 0 split each 2 px divider in two); the render
is 330 px wide so the platform face's third divider and pill stay inside; the pill is found by its painted ground (the
error tone's `danger.ground`, under the variant's scrim), 28 to 34 px wide at 2x (30 measured, its round ends'
antialiased columns aside); the label runs end before the pill — measured; the 30 threshold missed the pale ground —
cost if wrong: none.

R8. **`DockRolesTest` is unconditional and follows the chrome roles** (6a R15's style): the seven roles are one run
after `DIALOG_FOOTER_VERTICAL` with the same values in both catalogs; `NewRolesTest` is not edited (it finds `ADDED` as
a run anywhere since the 6a, 8a and 4c merges); the script stops unless the enum ends with `DIALOG_FOOTER_VERTICAL(8)`;
`DerivedColorTest`'s `surface.header.active` inputs gain `text.primary` — a test that closes the enum would break when
plan 9a appends its roles; the side rule reads the text — cost if wrong: none.

R9. **`PaintedContrastTest` gains `anywhere(image, color)`** for the accent and error pills (Task 4) and the borrowed
header's dot (Task 10): `hasColor` scans only an image's right half, where the off switch's knob sits, and both lie on
the left (the pill test failed "Slate Cobalt Dark, accent count pill", the dot test "STANDARD dot") — cost if wrong:
none.

R10. **Task 8's rule comparison writes `java.awt.Rectangle`**: `DockMockTest` imports `Rectangle` only from Task 9 —
cost if wrong: none.

R11. **`DockFixture`'s kit headers take their actions through the host header** (`PanelHeaderState.Action`), as
Remote's Sessions and Tunnels and File Server's panels publish theirs through `PanelHost.setHeader`, not as a header
context stack: a context stack's kit buttons are `BUTTON_HEIGHT` tall, which made the strip 29 px ("24 and the rule":
58 for 50 at 2x); with host actions every kit header is 25 and its icons' centres land within 2 px of the mocks' — no
plugin puts actions in a header context — cost if wrong: a plugin header with context actions draws taller than the
strip (a candidate follow-up: clamp the strip, or size context buttons as header actions).

R12. **`EventLogTest` builds `NotificationCenter` with `() -> Delivery.Presence.FOCUSED`**, as `NotificationsFeatureTest`
does — the constructor takes a presence supplier — cost if wrong: none.

R13. **Task 12's `ConfigurationStatusTest` addition stays**: 6b's `WindowPairsTest` samples the classic ruled cells'
warning and error text over every palette, but the default look's slash segments' configuration text (4a Rulings 16's
third hand-off) is checked only in Slate Cobalt — cost if wrong: two loops overlap on no palette.

R14. **`ProjectionsTest`'s landing test is not edited, and `ProjectionReadTest` already covers the dock**: its
`.contains(WINDOW_CHROME, WINDOW_CONTENT)` still holds, none of its assertions reads the dock group under `LANDED`, and
6a's `PROJECTION_READERS` lets only `WorkspaceChrome` read `Stripes` and `FrameStyle` — checked — cost if wrong: none.

R15. **Text that described the wrong result is corrected**: the `threshold()` grep prints nothing (`dragThreshold()`
does not match it); Task 9's red step fails on the strip's height; `ChromePainter`'s imports include `javax.swing.*` and
`dev.jasper.ui.metrics.*`; the facts' and Files' line numbers are `759b2b7d9`'s — the scripts anchor on text — cost if
wrong: none.

R16. **The plan writes the amendment banners' link only inside code**: `AppDocumentationTest` resolves every Markdown
link outside code fences in `docs/`, this file included, against its folder, and the Notifications banner's prose link
failed it (at the base too: the plan as committed broke `check`); Task 14 now scripts both banners, each its own
paragraph after the status paragraph as the Snippets and Remote designs carry theirs (3e had the same failure) — cost
if wrong: none.

R17. **The dock pairs are checked with `check_pairs.py`** (4c's tool; 6b R42 used `compare_all.py` only because it was
absent): each pair carries `min_agreement`, the edge agreement measured at `759b2b7d9` less 0.05 rounded down to 0.05
(headers 0.85 or 0.90, stripes 0.85 and 0.90, the strip 0.80), and `colours`, the roles it paints that the mock holds;
`json.dumps(…, ensure_ascii=False)` keeps the other pairs' "…" and the mocks' narrow no-break spaces — measured: all 25
pass (0.852 to 0.975) — cost if wrong: under Plex a floor may be raised at 5b's merge.

R18. **Shared files with parallel plans are reconciled at merge, not here**: 5b (`EnterpriseLook`, whose bodies it
replaces while this plan's two methods stay appended before `restoreDefault()`; `migration.csv`; `docs/STATUS.md`;
`pairs.json`; `KitFixtures`, not edited here); 9a (`DesignMetrics`' enum tail and both role tables, where its roles
follow this plan's seven; `Presentation.LANDED`, which must hold `DOCK_CHROME` and `SETTINGS`; `ProjectionsTest`'s end;
`ChromeRenderTest.check`'s top; `migration.csv`; `docs/STATUS.md`). Keep every side's rows, roles, tests and paragraphs
— the orchestrator's instruction — cost if wrong: merge work only.

R19. **Plan 5b's merge with this branch**: `EnterpriseLook.theme(boolean)` reads the shipped theme and `install` the
shipped kit with Plex registered; R5's printed probes become assertions within their stated tolerances and floors
(retune `STRIPE_LABEL_PADDING`, `STRIPE_PILL_GAP` and the header gaps by the tasks' knobs if one misses); the open tile's
tolerance is re-measured against 5b's #D7D9DC; `SurfaceContrast.beyond` replaces `apart`, so every Enterprise derived
role 5b's theme does not author (`surface.tableHeader`, `surface.zebra`, `surface.footer` where not listed) is
recomputed and `derived-roles-enterprise.tsv` and `EnterpriseThemeTest` are checked against it; `HeaderSideTest` and the
every-palette loops then include Enterprise if `KitFixtures.themes()` does; 5b's `EnterpriseHomeTest` and
`EnterpriseFirstWindowTest` run under the landed dock — the fixtures stand in for 5b only — cost if wrong: the dock is
compared in the platform face until then.

R20. **The pixel rule held in the dry run**: the two default renders and the gallery's 161 files a side are
byte-identical to the base after every task that can move them; only Slate Cobalt Dark's `surface.header.active`
moves in `derived-roles.tsv` (Rulings 16 confirmed) — cost if wrong: none.

R21. **Order is binding; timing-sensitive tests may flake once**: the shared files are edited in task order and each
later anchor is an earlier task's output; the window tests and renders wait on the EDT while other plans' builds run,
so re-run such a test alone before treating it as a failure, and never run `./gradlew --stop` — cost if wrong: one
rerun.

R22. **The counts the tasks state are the dry run's**: Task 4's `HeaderSideTest` 3 and `DockRolesTest` 1; Task 5's
`LabelledStripeTest` 8 and `DockMockTest` 2; Task 6's 3, 3 and 3; Task 8's `FlushFramesTest` 4 and `DockMockTest` 4;
Task 9's 5 and 5; Task 10's `PanelHeaderStripTest` 2, `FlushFramesTest` 5 and `DockMockTest` 6; Task 13's 7 — cost if
wrong: none.

R23. **A plugin's kit header with context actions, and the label clipping past a stripe's end, are follow-ups, not
this plan's**: R11's taller strip and Rulings 24's clipped labels are recorded for the STATUS hand-offs — cost if
wrong: none.
