# Jasper — Status and Handoff

## Current state — 2026-10-03

- **View-modes branch updated from local main `ad1eb65a`** at the user's request. The merge retains workspace glass, window-layout restoration and picker fixes alongside Normal/Compact modes. Configuration snapshots preserve both view mode and glass through loading, builder copies and both prior constructor signatures; schema count is 38. Full merged `./gradlew check` passed: **8,460 tests, 0 failures/errors, 6 skipped**, all 88 tasks successful (33 executed, 55 up-to-date). Updated main's Appearance section-list expectations to include View mode; independent merge review and scoped test review approved. Source hygiene checked 1,767 Java files; diff checks passed. No GUI launched or push performed.

- **Appearance view modes: SDD complete, native acceptance remains user-run** on `codex/appearance-view-modes`, based on `43894416`, in `/Users/dustin/.codex/worktrees/appearance-view-modes/moray`. User requested Normal (unchanged) and Compact (native macOS title, no toolbar, existing palette floating). [Design](superpowers/specs/2026-10-03-jasper-view-modes-design.md), [SDD plan](superpowers/plans/2026-10-03-jasper-view-modes.md). Restart-required is the announced implementation default; the optional preference question is unanswered. Plan text specifies existing-method changes by exact contracts/tests rather than copying large classes. Tasks 1 and 2 are implemented and independently approved; whole-branch code review approved with no critical or important findings. Its one minor docs finding, a stale reference to a nonexistent Task 3 in the manual checklist, is corrected. Compact captures startup mode, uses native workspace chrome, suppresses the complete header through reloads/menu actions/plugin contributions, and uses the existing floating palette fallback. Focused Task 2 tests: **97 tests, 0 failures, 0 errors, 0 skipped**; existing compiler warnings only. Final controller `./gradlew check` on `713b53b5`: **8,373 tests, 0 failures, 0 errors, 6 skipped**, 85 tasks (9 executed, 76 up-to-date), architecture/doc checks included. Source hygiene covered 1,747 Java files; diff checks passed. The controller inspected dark/default-font and light/18pt headless Appearance renders: the row and restart notice fit. `settingsPreview` was intentionally stopped after those renders with SIGTERM (exit 143) because it enumerates all bundled themes; full preview-matrix completion is not claimed. [Native manual checks](superpowers/plans/2026-10-03-jasper-view-modes-manual-check.md) remain user-run. Task 2 deviations: `TerminalWindow` and `WindowCommandPalette` needed no edits; their existing menu/root ownership and fallback geometry satisfy the contract. Without a config service, the existing null initial window config and sizing are preserved (controller clarification). User explicitly authorized implementation with SDD; no GUI, merge or push authorized.

- **Window layout restoration fix** (2026-10-03; `codex/restore-window-layout`, based on `main` at `f450112b`):
  main terminal frames previously never saved or restored their bounds; dock persistence intentionally used the
  last layout edit rather than the last window closed. Each window now snapshots its tool-panel slots/order,
  visibility, dock sizes/ratios and its own rail visibility before teardown. `ui-state.toml` version 2 also stores
  `[main_window]` normal pixel bounds and maximized axes. The app restores these after configuration and panel
  attachment, before showing the frame. Minimized state is not restored; unavailable display space is fitted to a
  current usable display, while intentional spans across adjacent displays remain exact. Auxiliary windows and
  file-chooser state are preserved. The original dock spec is amended to the user's last-closed-wins requirement.
  This is a bounded bug-fix follow-up with inline TDD and independent final review, not a re-execution of the
  historical dock plans. Regressions cover close order, all six open slots with exact terminal-area restoration,
  rail ownership, persistence across reload, maximized/minimized normal bounds, removed monitors, spanning displays,
  and malformed main-window data. Independent review's spanning-display finding was fixed with a failing/passing
  regression and approved in re-review. Final `./gradlew check --continue`: **8,439 tests, 0 failures, 0 errors, 6 skipped**; architecture gates and
  native headless checks passed. Changed Java source hygiene and `git diff --check` passed. No GUI launched, merge
  or push performed.
  User-run acceptance: resize/reposition a window, open/move/resize panels, quit/relaunch; repeat with two differently
  arranged windows closed in both orders, and with maximization/minimization and multiple displays. Native macOS
  fullscreen is distinct from AWT maximization and is not restored by this change. Terminal sessions/editor
  documents are not resurrected by this layout state.

- **Notifications plan 3: SDK notifications, reviewed on branch `claude/notifications-3`; awaiting the user's GUI
  checks and merge decision** (2026-10-03; plan `docs/superpowers/plans/2026-10-03-notifications-3-sdk.md`,
  [guide](notifications.md#plugin-notifications)): SDK 0.14.0 adds `dev.jasper.sdk.notifications`
  (`PluginContext.notifications()`, groups, specs with actions, a default action, an icon, a window and a cause) and
  removes `Notices`; every bundled plugin declares `>=0.14.0, <0.15` (Editor `>=0.14.1` since the workspace-glass merge). `plugins.HostedNotifications` posts into the
  center with the plugin id as source and contained links; a cause becomes the plugin's incident with Show more…
  (`IncidentNotices.pluginNotice`); a plugin's notifications expire when it stops. `PluginHost` posts succeeded and
  failed activities in `<plugin-id>.activities`. After launch, `PluginProblemNotices` posts each load failure, skipped
  plugin and unreadable plugin directory in Plugin problems with Open Plugins. The model now strips bidi controls,
  treats invisible-only text as blank, fits poster icons to 16 px and bounds group names. Snippets, Editor, File
  Server, Vault and Remote post titled notifications with their causes; Remote declares `dev.jasper.remote.tunnels`.
  Spec interpretations: see the plan's list (1 to 17). `./gradlew check --continue`: **8,333 tests, 0 failures, 0
  errors, 6 skipped** (39 new, after the final-review fix; one run in between failed only the known
  `TerminalAppIntegrationTest` "reflow" row-reset flake, which passed in three isolated reruns and in the full rerun),
  all four architecture gates and doclint included; the source hygiene check and `git diff --check c02673cd` are
  clean. Final review: one important finding fixed (a plugin whose manifest `name` showed nothing stopped Jasper from
  launching; `DescriptorParser` now rejects such a name, making it an unreadable-plugin problem); seven minor findings
  are deferred in the plan's status banner. No GUI launched. Pushed to `origin` on 2026-10-03 with the dock polish
  round, at the user's choice. GUI checks for the user: a plugin error card with Show
  more… opening the Errors window; a long File Server transfer (one that runs past the activity threshold), whose
  outcome is posted in "File Server activities" as "Sending <file>" or "Receiving <file>" (an interrupted one as an
  error); a plugin directory without `plugin.toml` in the user plugins folder, reported at launch with Open Plugins.

- **Merged `main` into `claude/dock-layout`** (2026-10-03; `main` at `63e3b95c`, merge base `cc0429f5`): main's
  features that touch the dock now use it. Unread badges are on the stripes: `ToolStripe.Button` carries the
  `PanelBadge`, a badged button shows a `BadgedIcon` and names the badge in its tooltip and accessible description, and
  a badge change updates its button in place (`Contributions.Kind.BADGES`, `ToolStripe.updateBadge`) and so never
  cancels a drag; the drag ghost keeps the plain icon. The
  Notifications panel docks in Right Top (`Slot.RIGHT_TOP`, where `PanelRegion.RIGHT` was), and its in-content
  "Notifications" heading is gone because the tool window header carries the title (Clear All stays). The notification
  card layer sits in the bottom-right corner of `ToolWindowArea` (between the stripes, above the status bar, over any
  docked tool window), on `PALETTE_LAYER` under the dock drag's `DRAG_LAYER` guides. `NotificationsFeature`'s
  `dev.jasper.app.dock` import is listed in the `application` package-info. Docs: this file keeps both sides' entries;
  the notifications guide and app maintenance describe the stripe button, its badge and the area corner. New tests:
  `WindowPanelsTest.aBadgedPanelShowsADotAndDescribesItsCount` (on the right stripe),
  `WindowDragTest.aBadgedButtonStillDragsAndLiftsOutAndItsGhostIsThePlainIcon`,
  `WindowCornerLayerTest.theLayerStaysBetweenTheStripesOverDockedWindowsAndUnderADockDrag` (its two older cases now
  follow the area) and `HistoryPanelTest.theToolWindowHeaderCarriesTheTitle`; for the in-place badge update,
  `ToolStripeTest`, two `WindowDragTest` cases (a badge set mid-drag, in this window and in another) and
  `BadgedIconTest.theSizeEqualsTheBaseIconsSize` (a `BadgedIcon` is its base icon's size, so a badge change needs no
  relayout). `./gradlew check --continue`: **8,200 tests, 0 failures, 0 errors, 6 skipped**, all four architecture
  gates and doclint included; main's `ExFilesTest` fix (`185a5f8e`) makes it pass on this Mac, and the Remote
  `ListenerTest` flake did not fail. Open: the notifications GUI checks now look at the Notifications button on the
  right stripe; one more GUI check: start a tool window drag, then trigger a notification (for example a config
  error), and the stripe button gains its dot while the drag continues. No GUI launched. Fast-forwarded to `main` and
  pushed to `origin` on 2026-10-03 at the user's choice, together with the notifications plan 1 merge below.

- **Dock layout plan 2: drag and drop** (branch `claude/dock-layout`,
  [plan](superpowers/plans/2026-10-02-jasper-dock-layout-plan-2-drag-and-drop.md)): tool windows drag by their stripe
  buttons and headers. `DockDropResolver` (pure) resolves a stripe group and position or a cell of the workspace's 3 × 2
  grid; `DockGeometry.landing` previews the landing on a copy of the state; during a drag both stripes show all three
  groups, lift the dragged button and open a gap; `DockDropOverlay` on `DRAG_LAYER` paints the highlight, the
  placeholder and the ghost; `DockDrag` owns the gesture (5px threshold, Esc cancels, a resize resolves again). A stripe
  drop moves and keeps visibility; a workspace drop moves and shows (`DockState.moveAndShow`). Any dock change, View ›
  Rail, or the window closing or being deactivated cancels a drag. Deviations from the plan text: before execution
  (`728167c4`) the expected test counts and Task 6's `close()` anchor were aligned with plan 1 as executed; Task 4,
  `DockDropOverlayTest` is isolated from the installed theme (it saves and restores the look-and-feel-level colours,
  clears `DragAndDrop.areaBackground`, `DragAndDrop.borderColor` and `Component.accentColor` at both the developer and
  look-and-feel level, and installs `Theme.DARK` first in `theThemesOwnDragColoursWin`); Task 5, `DockDrag` button rules
  (a non-left press during a drag cancels it, only a left release drops, popup-trigger presses are ignored, and a left
  press during a drag means the release was lost, so the drag is cancelled and the new press starts over; `DockDragTest`
  grew by 4 cases, and a lint fix was made); Task 6, the drag returns home and the source group keeps its step
  (`DockDrag` targets the lifted stripe button's home, its old square, slot and index, until the cursor first leaves
  that square grown by the drag threshold and one more pixel on every side, and a resize forgets it; during a drag the
  lifted button's group keeps its step at the group's end and the gap takes it when the target is in that group, so
  later groups do not slide up; three `ToolStripeDragTest` values moved one step; final counts `DockDragTest` 17 cases,
  `ToolStripeDragTest` 8); Tasks 1, 2 and 3 none in code; final review: a press near a stripe button's edge that moves
  just past the drag threshold and back stays home (`DockDrag` leaves home only past the square grown by the threshold
  and one more pixel on every side; new tests `aWobbleOutOfTheLastRowAndBackStaysHome`,
  `aWobbleOutOfTheLastColumnAndBackStaysHome` and `oneMorePixelOutLeavesHomeAndComingBackIsResolvedFromTheStripe`); a
  drag ends when its window is deactivated (a modal dialog opening, another application taking focus), through
  `WindowContent.setActive(false)`, which `TerminalWindow`'s `windowDeactivated` already calls, rather than through a
  window listener `DockDrag` adds and removes, since a headless test cannot build a `Window`; for the same reason its
  Esc dispatcher claims only key events from its own window as told by the root pane, not by
  `SwingUtilities.getWindowAncestor` (new tests `theWindowDeactivatingMidDragCancelsItAndNothingDrops` and
  `anEscInAnotherWindowIsLeftToItAndTheDragGoesOn`); View › Rail cancels a running drag (new test
  `turningTheRailOnOrOffMidDragCancelsIt`); and test cleanup (`DockDragTest` records the Esc dispatchers with its own
  focus manager, cancels every fixture's drag after each test and checks none is left; `DockDropOverlayTest` saves and
  restores on the EDT, the look and feel first, and fails when it cannot reinstall it); final counts `DockDragTest` 21
  cases, `WindowDragTest` 8. Planning decisions are listed in the plan. `./gradlew check --continue`: 8,044 tests, 1
  failure, 0 errors, 6 skipped; the one failure is `ExFilesTest.bufferSelectsATabByNumberOrName` (Editor), which fails
  on this Mac only (its `java.io.tmpdir` contains "zz") and predates the branch, and
  `ListenerTest.acceptsOnLocalhostUntilClosed` (Remote) is flaky under full-suite load (it did not fail in this run and
  passes when run alone). Open: the GUI checks are pending; any dock change or stripe render cancels a running drag
  (an action's enable or title change now refreshes the stripe in place, so only adding or removing an action or a
  rail placement, or a dock or panel change, re-renders it; follow-up pushed to main on 2026-10-03; GUI check: lock the
  vault, or otherwise change an action, while dragging a tool window, and the drag carries on); on stripes shorter than about 200px the
  foot can clip by one step during a drag; and the overlay now repaints only what changed on each drag event (each
  changed guide's old and new bounds, nothing when none changed, no longer the whole window and every terminal under
  it), and the placeholder's fill sits inside its border like the highlight's, so the two no longer stack; GUI check:
  the ghost should not lag while dragging on a large HiDPI window; if the ghost still lags over a terminal, the next
  lever is in `jasper-terminal`: `TerminalView.paintComponent` takes a full snapshot and paints every row whatever its
  clip, so a terminal under a small dirty rectangle still pays the whole snapshot. No GUI launched; GUI checks for the
  user are the plan's last section.
  Polish round (2026-10-03, pushed to `main` with notifications plans 2 and 3 at the user's choice): the overlay repaints only what changed, the placeholder's
  border no longer stacks over its fill, and the placeholder's fill is concentric with its border (its corner
  diameter is `arc - 1`, where the border's inner edge lies); `DockState.dropIndex` is the one drop-index rule shared
  by the preview and the drop. 8 new tests pin behaviour that was already there: `HostedUiTest` maps all six anchor and
  split combinations to their slots (`everyAnchorAndSplitCombinationMapsToItsOwnSlot`); `UiStateTest` gains 7 cases: a
  version 1 side takes its size from the visible panel, else from the first panel (2), the dock's sizes and ratios
  exactly at their limits are accepted and one step outside each falls back alone (2), and a scalar where the `dock`
  or `panels` table belongs, or where a panel entry does, loses only that part (3). `./gradlew check --continue`:
  **8,314 tests, 0 failures, 0 errors, 6 skipped** (8,306 before).
- **Dock layout plan 1: six slots** (branch `claude/dock-layout`,
  [plan](superpowers/plans/2026-10-02-jasper-dock-layout-plan-1-six-slots.md),
  [spec](superpowers/specs/2026-10-01-jasper-dock-layout-design.md)): IntelliJ-style tool windows apart from dragging.
  New JDK-only package `dev.jasper.app.dock` (`Slot`, `Side`, `DockState`, pure `DockGeometry`), enforced by
  `verifyApplicationArchitecture`. `ToolWindowArea` replaces `WorkspaceRegions` (one rounded frame with a
  `ToolWindowHeader` per showing slot, a full-width bottom area, its own 6px dividers that preview a drag and save on
  release); two `ToolStripe`s replace `WindowRail`; six-slot Move to menus on stripe buttons and headers.
  `ui-state.toml` version 2 (`[dock]` sizes and ratios, per-panel slot, order and visibility; per-entry fallback;
  version 1 migrates on read). SDK 0.13.0 adds `PanelSpec.withSplit()`; all seven manifests require `>=0.13.0, <0.14`;
  Remote 0.11.0 opens SFTP under Sessions on a fresh state or after Move to › Left Bottom once (an upgraded state that
  already places SFTP keeps it in Left Top, since a saved place wins). Sessions, Tunnels and File Servers no longer
  repeat their title. Deviations from the plan text: Task 2, `DockGeometry` keeps each ratio divider's drag range
  inside `DockState`'s 0.1–0.9 ratio limits (new test `aWideSplitsDividerRangeStaysWithinTheRatioLimits`); Task 5,
  `ToolWindowHeader`'s padding is a compound border around `FlatEmptyBorder` so it survives theme switches, and
  `ToolWindowMenu`'s six Move to items share a `ButtonGroup`; Task 7, a divider press and release without a drag
  between them leaves the state alone (new test `aClickOnASqueezedDividerChangesNothing`); Task 8, `docked()` returns
  focus to the terminal between the hidden and the shown notifications as `hidePanel` did before, a throwing panel
  factory is logged at WARNING, `WindowPanelsTest` has 18 cases rather than 16, the close test checks the right
  stripe, and a `ToolWindowHeader` comment names `ToolWindowArea.Host` instead of `WorkspaceRegions.Host`; Tasks 1, 3,
  4, 6 and 9 none; final review: `WindowContributions.close()` no longer saves, because every dock change is saved as
  it happens and saving again on close overwrote other windows' later changes (new test
  `closingAWindowKeepsTheChangesAnotherWindowSaved`); `DockGeometry` reserves above the bottom area the larger of the
  terminal's minimum height and two tool window minimums plus a divider when the left or the right side shows both
  halves, in the squeeze and in the bottom divider's range (new tests
  `theBottomAreaGrowsOnlyUntilASplitSidesHalvesReachTheirMinimum`,
  `aShortWindowSqueezesTheBottomAreaBeforeASplitSidesHalves` and `theSplitSidesMinimumScalesWithTheUi`); each panel
  instance keeps a `notified` flag and hears a visibility only while it is still true and not yet told (new tests
  `aFactoryThatHidesItsOwnWindowHearsNothing` and `aWindowThatShowsItselfAgainWhenReplacedHearsOnlyWhatIsTrue`, so
  `WindowPanelsTest` now has 21 cases); a bad `[windows]` entry is skipped alone instead of resetting the whole file
  (new test `aBadWindowEntryFallsBackAlone`; the bad-window case of
  `invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults` now checks that the rest of the file survives); and the
  SFTP wording above and in the plan's GUI checks. Planning decisions are listed in the plan.
  `./gradlew check --continue`: 7,994 tests, 1 failure, 0 errors, 6 skipped; the one failure is
  `ExFilesTest.bufferSelectsATabByNumberOrName` (Editor), which fails on this Mac only (its `java.io.tmpdir` contains
  "zz") and predates the branch, and `ListenerTest.acceptsOnLocalhostUntilClosed` (Remote) is flaky under full-suite
  load (it did not fail in this run and passes alone). The window's minimum height now counts a split side's two
  halves (one shared rule, `DockGeometry.splitMinimum`; follow-up pushed to main on 2026-10-03). No GUI launched. GUI
  checks for the user: the plan's section
  "GUI checks for the user". Plan 2 (drag and drop) was executed on top of it; see the entry above.

- **No FlatLaf SEVERE at start-up with Nord** (branch `fix/theme-lazy-border`, user report 2026-10-02): Nord sets
  `TableHeader.cellBorder` to a colour. Resolving workspace values asked the private, never-installed FlatLaf defaults
  `get(key) == null` for every explicit colour key, which built FlatLaf's lazy `FlatTableHeaderBorder`; its constructor
  reads `TableHeader.cellMargins` from the installed look, missing before any theme is installed, and FlatLaf logged
  the NPE. `FlatLafThemeData.defines` checks the table without building values (then `get` for keys FlatLaf serves
  elsewhere); WorkspaceValues and ThemeManager.install use it. Full check **7,967/0/0/6**; merged into local main
  2026-10-02 (fast-forward), not pushed. Separate, not fixed here:
  Darcula and four other converted themes keep IntelliJ named-colour values such as `"panel"`, which FlatLaf logs as
  "Failed to parse" (`TitlePane.background`, `NewUiOnboarding.Dialog.background`).

- **Theme dropdowns search as you type** (branch `claude/theme-search`, worktree `.claude/worktrees/settings-appearance`,
  design approved in chat 2026-10-02): both Appearance dropdowns (`Editors.ThemePick`) are a `settingsui.SearchCombo`.
  Typing in the open or focused dropdown shows an IntelliJ-style search bar at the top of the list and keeps the
  names holding every word; Same as UI theme stays pinned; "No matching themes" is an unpickable row. Up/Down/Enter/
  Escape/Backspace/Home/End work while searching, Enter picks without reaching OK (and picks nothing when nothing
  matched), Space opens the list, and the combo never picks by first letter, even after a theme change rebuilds its
  look and feel (an independent review found that and the Home/End leak; both fixed with tests). Rows are padded as
  FlatLaf pads its own combo popup (the user saw text outside the highlight; fixed in `a1d3022f`). Full check
  **7,966/0/0/6**; merged into local main 2026-10-02 (fast-forward), not pushed. Not done: grouped sections (not
  requested), GUI check (hover/click, the × button, typing on a closed dropdown), push.

- **Settings stays in front; Appearance gets IntelliJ-style theme dropdowns** (branch `claude/settings-appearance`,
  worktree `.claude/worktrees/settings-appearance`, design approved in chat 2026-10-02):
  - **On top:** macOS raised the window Settings blocks in front of it. Every modal dialog now floats above Jasper's
    windows while Jasper is active (`windows.FloatWhileActive`) and drops to the normal level while another
    application is. Blocking is unchanged: only the opening window.
  - **Appearance:** Theme and Workspace scheme dropdowns (`Editors.ThemePick`), with a gear beside Theme whose only
    item is Import theme… (`themesui.ThemeImport`). They are staged until Apply/OK and re-read the catalog on refresh;
    a missing saved name shows as "(not found)". Font & Colors links to Appearance; View › Themes… opens it.
    "Workspace theme" is "Workspace scheme" in the UI and docs; the key `ui.theme.workspace` is unchanged.
  - **Removed:** the Themes page, its pickers, preview, sample terminal and syntax sample, Duplicate, Remove, Open
    themes folder and About, and the now-unused `ThemeDuplicate` and `ThemeFiles` duplicate/removal operations.
    The notices still ship (now checked by `PackagedResourcesTest`). The theme-system spec §7 carries a superseded banner.
  - Merged into local main 2026-10-02 after merging main (`c4211cf1`) into the branch; not pushed. Merged-tree
    `./gradlew check`: **7,954/0/0/6**. Not done: GUI check (Settings over its window and other windows, switching apps, the
    dropdowns, the gear and an import), push.

- **Built-in theme catalog no longer stalls start-up** (branch `claude/theme-catalog-edt`, user request in chat
  2026-10-02): building the application parsed all 284 bundled theme files on the EDT (`ThemeCatalog`'s built-in
  snapshot), about 1.5 s cold, nearly all of it tomlj reading the converted themes' `[ui]` and `[icons]` tables (96% of
  the 9.4 MB). This is the start-up side of the cold-JVM cost behind the contended-endpoint test below, which now
  warms up outside its window; before that warm-up landed, this change alone made the test pass cold (5 of 5). A
  bundled theme's name, darkness and palette now come from its file up to the first `[ui]` or `[icons]` table
  (`ThemeTomlWriter` writes them last); `Theme.Bundled` parses the whole file on its first `document()` call, once, and
  bundled themes are equal by file. The native scan and the importer match bundled `extends` parents by name before
  reading their documents. A cold catalog load takes about 250 ms; parsing in parallel was measured and rejected
  (1.7x). `BuiltInThemesTest` checks every bundled theme against its whole file. Full check after rebasing on `e0e61461`: **7,975/0/0/6**. Not done: GUI
  check, push.

- **`:b` name test no longer depends on the temp folder** (merged into main as `f1c1acd3` and pushed, 2026-10-02):
  `ExFilesTest.bufferSelectsATabByNumberOrName` failed on the dev Mac, whose `java.io.tmpdir` holds "zz", because `:b`
  matches whole paths, as Vim 9.1 does (checked with `/usr/bin/vim`). The test's buffers now live under a fixed `/work`
  folder. Open: `ExFiles.match` tries exact, ends-with, starts-with, contains and stops at the first level with any
  match; Vim tries contains, starts-with, ends-with, exact and stops at the first with exactly one. With buffers `xab`
  and `abab`, Vim's `:b ab` selects `abab` where Jasper reports E93.

- **Workspace glass, plans 1 (foundation) and 2 (editor)** (branch `claude/workspace-glass`, user request 2026-10-02):
  2026-10-03 (user request): the terminal tab strip now always shows the workspace background instead of the theme's
  `tab_bar` colour (called a bug by the user) and, being non-opaque over the centre host, is the same glass as the
  terminal in a translucent window; pills, hover, close buttons and the list button stay solid. GUI check: the strip
  reads as one surface with the terminal, opaque and glass, in Clear Dark and a theme with a distinct tab bar.
  [spec](superpowers/specs/2026-10-02-jasper-workspace-glass-design.md),
  [plan 1](superpowers/plans/2026-10-02-workspace-glass-1-foundation.md),
  [plan 2](superpowers/plans/2026-10-02-workspace-glass-2-editor.md). Themes gain `[workspace] opacity/blur`; Settings
  › Appearance › Workspace overrides them (`ui.workspace.opacity` in percent, `ui.workspace.blur`
  `"theme"`/`"on"`/`"off"`); bundled Clear Dark (0.95, blur on) and Clear Light (0.93, blur on) are ported from
  Terminal.app (catalog 286). On macOS, `WindowGlass` makes the window translucent through two JDK internals (new
  launcher flags) and blurs behind it through `libjasper-glass.dylib`; terminal panes paint their default background
  with alpha inside a cleared surface, and any failure keeps the window opaque; the whole path is macOS-only (rows hidden in Settings, no `WindowGlass`, no launcher flags, `GlassSource.NONE` elsewhere; the keys are silently ignored). Deviations from the spec: ancestors stay
  opaque and the workspace host clears a hole (second spike); opacity is `TerminalView.setBackgroundOpacity` only (no
  `TerminalOptions` field), and it takes a `float`; `WindowGlass` follows the root pane. Rulings made during execution:
  `ClearThemesTest` runs under `EdtTestExtension`; the Workspace section kept its own "Choose workspace theme…" button (superseded by the 2026-10-03 merge: no button; the theme rows now sit in Appearance)
  (Terminal › Font & Colors has one too). The final review's fix wave makes a window translucent again after a UI-theme
  switch's deferred opaque frame background (restoring the newer background later), fills the whole terminal surface
  with one glass fill instead of clearing it (the lower-arc strip was a hole), and finds the blurred `NSWindow` through
  any layer ancestor. Typing lag and blank live resizes with glass on came from Java2D's Metal pipeline rendering
  antialiased translucent `Src` fills in software (`fc127ca9`: plain rectangular fills, opaque chrome corners). Blur
  then moved from the private CoreGraphics radius, which blurred the square corners outside the rounded window, to a
  public `NSVisualEffectView` masked to the window's corners: on/off, `hudWindow` for dark workspace themes and a light
  `underWindowBackground` for light ones; theme `blur` is a boolean and the CoreGraphics path is gone (spec amendment
  2026-10-02). `GlassSettings`' blur component is `blurChoice` (a `WorkspaceBlur`), so `blur()` can stay the
  `Optional<Boolean>` that `GlassLevels.resolve` reads. Review follow-ups: the corner-mask observer is a
  `JasperCornerObserver` the effect view keeps alive, so its lifetime follows the view (and the window); the Blur row
  shows "Use theme's value" for `"theme"`.
  Plan 2 (editor glass) is done: SDK 0.14.1 adds `Appearance.workspaceOpacity(Component)` (default 1.0; `onWorkspaceChanged`
  also reports it, including a component entering or leaving a translucent window), answered by the app's `GlassSource`
  and `WindowContent.setGlassListener`; Editor 0.7.0 paints its text and gutter backgrounds once each in a `GlassSurface`
  and makes the area, viewports and gutter non-opaque (the current-line band takes the opacity's alpha; selection, caret,
  find/Vim bars, banner and loading state stay solid). Deviation: the spec's no-argument `workspaceOpacity()` became
  `workspaceOpacity(Component)` because opacity is per window and content moves between windows. Rulings: the gutter
  border is cleared in glass mode (it was an opaque stripe); `EditorTabTest` primes RSyntaxTextArea 3.6.0 font metrics by
  reflection (headless, no peer). Final-review fix wave: `workspaceOpacity` answers the window's opacity only inside the
  workspace centre (`WindowContent.workspaceOpacity`: the deck holding terminal and hosted content tabs) and 1.0 for
  chrome, the rail and side/bottom panels; the applied opacity is kept as the double it arrived as (0.8 reads back as
  0.8); the glass listener runs after `mountContent`'s try; gutter children reattached by RSyntaxTextArea (Vim
  `:set nonumber`/`number`) take the gutter's opacity through a once-per-gutter container listener.
  `./gradlew check --continue`: 8,043 tests, 0 errors, 6 skipped across all modules (jasper-app 1,815, jasper-terminal
  394, jasper-buddy 163, jasper-sdk 44, jasper-sdk-testkit 85, plugins 5,542); its one failure was the known flake
  `TerminalAppIntegrationTest.rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation` `[3 reflow]`
  (jasper-terminal is untouched by plan 2), which passed an isolated rerun and the rerun of the check (BUILD
  SUCCESSFUL): a timing flake under load, not investigated further. GUI checks (user-run) for plan 2: an editor tab
  in Clear Dark and Clear Light, a split of terminal and editor tabs, moving an editor tab to a separate window and back,
  opacity changes live, and the current-line and selection look; scroll bars stay solid in a glass editor (by design),
  so check a long file looks acceptable; Vim `:set nonumber` then `:set number` in a glass window (digits do not smear);
  a plugin tool panel (e.g. a side panel) in a glass window stays opaque. Plan 1 GUI checks: Clear Dark beside Terminal.app's, the Clear Dark
  (`hudWindow`) versus Clear Light (light `underWindowBackground`) material, rounded corners without slivers and square
  full-screen corners after entering and leaving full screen, live opacity/blur changes, splits, stripes hidden,
  native fullscreen, switching back to an opaque theme, switch the UI theme live from Jasper Dark to Clear Dark, a
  packaged build loading `Contents/app/libjasper-glass.dylib`, and Settings rows hidden (no Workspace header) off macOS.
  Merged `origin/main` (`ffce7483`: dock layout, notifications, theme rows in Appearance, SDK 0.13.0) on 2026-10-03:
  SDK 0.13.1 (Editor needs `>=0.13.1, <0.14`), the glass fill ported from the deleted `WorkspaceRegions.Host` to
  `ToolWindowArea`'s centre host (`setGlass`), and Opacity/Blur in an Appearance › Workspace section after main's theme
  rows, with no header off macOS (`SchemaPage` now hides a section whose rows are all hidden). `./gradlew check
  --continue`: 8,288 tests, 0 failures, 0 errors, 6 skipped.
  A second merge of `origin/main` (`43894416`: SDK 0.14.0, Notices replaced by notifications, dock polish) moved
  `workspaceOpacity` to SDK 0.14.1 (Editor 0.7.0 needs `>=0.14.1, <0.15`); `PluginHost.Environment` carries both
  main's failure sink and the `GlassSource`. `./gradlew check --continue`: 8,429 tests, 0 failures, 0 errors, 6 skipped.
- **Contended-endpoint rollback test: cold-JVM warm-up, not a lock bug** (branch `claude/suspicious-sinoussi-327047`,
  user request 2026-10-02): `ApplicationBootstrapRollbackTest.contendedEndpointCannotBlockEdtOrBoundedTermination[1]`
  failed every isolated run. EDT stack sampling showed the first composition in a JVM taking 1.6 s on the EDT
  (about 900 classes, mostly `Theme`'s built-in TOML parse through tomlj/ANTLR, plus FlatLaf/font setup), the same with
  or without the child-held lock, with zero samples in `FileChannel.lock`; later compositions take under 10 ms with
  the lock held. The cold case also never exercised the contended path: the child released the lock before shutdown
  ran. The test now composes and quits once uncontended before the lock child starts; the 1 s budget is unchanged.
  A temporary mutation (shutdown actions run on the EDT) still fails both cases. 10 isolated class runs and 5
  method-only runs pass. `./gradlew check --continue`: **7,971 tests, 1 failure, 6 skips**. The failure is a separate,
  pre-existing, machine-dependent assertion, `ExFilesTest.bufferSelectsATabByNumberOrName` (`:b zz` expects no match,
  but this Mac's temp folder `/var/folders/d0/…psfzzxc…/T/` contains "zz"); it passes with a `java.io.tmpdir` without
  "zz", and was previously hidden because `:jasper-app:test` failed first. It was fixed separately on main
  (`185a5f8e`). Merged into main (`5729a684`) and pushed 2026-10-02.

- **Palette scope pills removed** (branch `palette-remove-scope-hints`, user request in chat 2026-10-02): with no
  chip, the pills along the palette's bottom edge (`> commands`, `history >`, …) overlapped the selected row's verbs
  and hid its detail. They are gone, so the hint bar always shows the selected row's detail and verbs. Typed prefixes,
  scope shortcuts and the query-field chip are unchanged. `WindowContent.scopeShortcut` became `paletteShortcut`,
  because the title search field is its only remaining caller. Merged into main 2026-10-02 and pushed with `5729a684`.
  Not done: GUI check.

- **Notifications plan 1: core, reviewed and merged to local `main`; awaiting the user's GUI checks** (2026-10-02; branch `claude/notifications`, spec
  `docs/superpowers/specs/2026-10-02-jasper-notifications-design.md`, plan
  `docs/superpowers/plans/2026-10-02-notifications-1-core.md`, [guide](notifications.md)): an EDT `NotificationCenter` (in-memory history of 500,
  60 s coalescing, read/expired state, waiting cards), per-window card stacks in the content corner, the built-in
  Notifications rail panel with an unread badge, and the `jasper.general` / `jasper.config` producers. Window errors
  and info, plugin `Notices` and configuration problems no longer open dialogs. Recorded interpretations are in the
  plan's "Spec interpretations" section; the six deviations made during execution are in its status banner. `./gradlew check`: **8,076 tests, 0 failures, 0 errors, 6 skips**
  (all four architecture gates, doclint and compiled examples included; source hygiene over the 58 changed Java files
  and `git diff --check` clean). On this Mac `plugins/editor`'s `ExFilesTest.bufferSelectsATabByNumberOrName` fails when
  the temp folder's path contains `zz` (as `$TMPDIR` does here); main already fixes it (`185a5f8e`), the branch base does
  not, so the check was run with `java.io.tmpdir` pointed at a short path without `zz` (some tests also depend on the temp path's length and text); merging main removes the need. No GUI was launched
  and nothing was pushed. GUI checks for the user:
  - cards in light and dark themes, their shadow, and the badge dot on a normal and a selected/hovered rail button;
  - several windows: a card appears in the window that raised it; clicks beside a card reach the terminal; Dismiss Cards;
  - the Notifications panel: New/Earlier, Clear All, row ✕, theme switch while open;
  - auto-hide pausing while the window is in the background and while hovering a card (including while its More menu is open);
  - a broken `config.toml` at startup shows a sticky Configuration card with Show Problems / Open config.toml;
  - a broken shell profile at startup leaves the window open with the failure in the pane and an error card; at cold
    start this also checks the final-review fix for a card raised before the first window's activation (the card must
    show once the window is active, not only sit in history);
  - busy terminal output (for example `yes` or a large `cat`) scrolling under a sticky card stays smooth, and the
    card's shadow looks as before on a Retina display.

  Final whole-branch review fixes, all applied on the branch (`d2bd488f`, `08a2c9ff`, `498e4d61`): a card raised
  before a new window's first activation waits instead of landing in history only; a coalesced repeat that shows its
  card no longer stays waiting for focus; each card's shadow is rendered once and cached instead of twelve rings on
  every repaint. `./gradlew check` after the fixes (same short temp path): **8,084 tests, 0 failures, 0 errors, 6
  skips** (8 new tests; the 8,076 above is the count before them).

  Merged to local `main` on 2026-10-02 with the user's agreement: `main` (`8a428f0b`) was merged into the branch first
  (`438f6a99`; one trivial conflict in `WindowCommandPalette`, keeping main's argument list without `scopeShortcut` and
  this branch's titled "Command failed" error), then `main` was fast-forwarded. `./gradlew cleanTest check --continue`
  on the merged tree, with no temp-path workaround: **8,079 tests, 0 failures, 0 errors, 6 skips**. One earlier run of
  it failed `FtpEngineTest.tlsWithoutRequireAllowsBoth` with "425 Can't open passive connection"; the File Server
  suite then passed three isolated re-runs (160/160) and the next full run, so it is a load-dependent passive-port
  flake, not a regression (the branch does not touch File Server). Pushed to `origin` on 2026-10-03 with the dock
  layout, at the user's choice; the GUI checks are still the user's.

- **Notifications plan 2: incidents and the Errors window, merged to local `main` (`c02673cd`); awaiting the user's
  GUI checks** (2026-10-02 to 2026-10-03; plan
  `docs/superpowers/plans/2026-10-02-notifications-2-incidents.md`, [guide](notifications.md#the-errors-window)):
  uncaught exceptions on any thread, contained plugin failures, failed shell launches and failing notification
  actions are in-memory incidents (at most 100, coalesced by source, class and root-cause frames, traces taken on the
  catching thread and capped at 256 KiB). Each is announced by one ERROR notification in `jasper.errors`,
  `jasper.plugins` or, for a shell, `jasper.general`, with Disable Plugin for plugins and Show more… last; a repeat
  updates it at most once a second (failures waiting for the EDT are merged first, and the count catches up), with a
  card at most once a minute. The Errors window lists them with Copy, Disable Plugin, Open Log Folder and Clear. The
  log is unchanged. Recorded interpretations are in the plan's "Spec interpretations" section; the execution
  deviations are in its status banner: each shell launch failure is announced in its own window (the repeat rules
  cover Jasper and plugin incidents only), the center's 60 s coalescing still ignores the target, no package-info
  files changed beyond `notifications` and `notificationui`, and the final review's fixes (the once-a-second update
  with the merge, Show more… last, Open Log Folder opening the folder). Every task passed its review, and the final
  whole-branch review's findings were fixed (`5aa67e75`, `ac407bbb`). Findings deferred to plans 3, 4 and 5, and the
  smaller ones left open, are listed under "Deferred findings" in the plan's status banner. `./gradlew check`:
  **8,161 tests, 0 failures, 0 errors, 6 skips** (all four architecture gates, doclint and compiled examples
  included; source hygiene over the 34 changed Java files and `git diff --check` clean). No GUI was launched. Pushed
  to `origin` on 2026-10-03 with the dock polish round, at the user's choice. GUI checks for the user:
  - an uncaught exception (for example from a plugin under development) shows an "Unexpected error in Jasper" or
    plugin card; Show more… opens the Errors window at it, in light and dark themes;
  - the Errors window: list selection with the mouse and the arrow keys, the monospaced trace scrolls, Copy pastes a
    report, Open Log Folder opens the folder, Clear empties the list and disables the cleared notifications' links;
  - a plugin that keeps failing shows one card, counts up `×N`, and shows its card again no sooner than a minute later;
  - with the Notifications panel open, a plugin failing in a tight loop keeps Jasper responsive and its card counts up;
  - Disable Plugin posts "… will be disabled when Jasper restarts", and the Plugins page agrees after a restart;
  - a broken shell profile shows "Could not start the terminal" with Show more…, and the window's only pane stays
    open showing the failure;
  - Notifications: Show Errors from the palette; the window remembers its size and position.

- **Theme system Plan 5 complete and independently reviewed; awaiting integration** (2026-10-01):
  [Themes page and live reload](superpowers/plans/2026-10-01-theme-system-5-themes-page.md) is written and self-reviewed
  against local merge `cc0429f5`, on `codex/theme-system-plan5` in the reused `.worktrees/theme-system` worktree.
  Six tasks cover serialized theme polling, safe Duplicate/Remove, searchable staged pickers, local two-zone preview,
  owner-scoped import/file dialogs and final navigation/acceptance. Selection stays staged; confirmed file actions
  happen immediately. Plan 4's stale help text is included in Task 6. Preserve the user's inline execution preference
  after approval. Task 1 now reloads native changes independently of config, including equal-metadata parent edits and descendant refresh, with unchanged-object reuse. Task 2 adds inherited commented copies and frozen removal intents; stale bytes, identities and symlinks are refused. Task 3 adds independent searchable staged pickers, literal labels, effective swatches and retained missing selections/diagnostics. Task 4 adds isolated two-zone previews with effective syntax styles, canned output and disposal. Task 5 wires immediate import/copy/removal, owner-bound dialogs, notices and folder actions, with worker validation and lifetime guards. Task 6 registers Settings > Themes, native catalog menus and staged/immediate workflows, closes the outdated help text, and preserves legacy shortcut IDs. Full check passes **7,972/0/0/6** (1m28s); retired dropdown tests are replaced by page/menu integration coverage. All four architecture gates, packaged resources, doclint and 1,661-source hygiene pass. Independent review of `cc0429f5..fb74ca16` found no Critical/Important issues; exact-head full check repeats **7,972/0/0/6** (1m27s). Deferred minors: no Themes tree modified marker, and the original design spec status banner is stale. Native visual/focus/menu and real Trash/folder checks remain user-run; the pre-existing bounded startup wait is retained. No second review or fix pass was needed. The existing bounded startup EDT wait is retained (plan correction); new runtime work remains asynchronous. Task 4 also narrows the packaged-resource fixture guard to allow only the new production ThemePreview; other Preview classes remain forbidden. No GUI or push.

- **Theme system Plan 4 complete, independently reviewed and merged locally** (2026-10-01):
  [conversion, vendoring and migration](superpowers/plans/2026-10-01-theme-system-4-conversion-migration.md)
  completed all seven tasks on `codex/theme-system-plans`, now merged; the worktree is reused for Plan 5. Full `./gradlew check`
  passes **7,930 tests, zero failures/errors, six skips**, including all four architecture gates, packaged resources,
  JavaDoc and compiled documentation examples. Java hygiene scans 1,637 source files cleanly; authored diff whitespace passes.
  The lower final test count reflects retired store/runtime-legacy tests; converter/security/installation parity and
  native fallback/reload coverage remain. Local merge `cc0429f5` includes the hidden-rail framing fix and passes a fresh `./gradlew check`: **7,931 tests, zero failures/errors, six skips** (1m33s; `/tmp/moray-plan4-main-check.log`). No GUI or push.
  The catalog contains **284 native themes from 12 sources/413 hashed inputs**; exact pins, licenses, exclusions and
  renames are in `tools/themes/sources.json`, SOURCE.md and THIRD-PARTY-THEMES.md. Largest theme: 97,426 bytes;
  256 KiB cap unchanged. `[icons]` is now a documented native format table. All generation/testing is offline.
  Safe import/file services preserve originals, freeze decisions, allocate batch names and reject stale replacements.
  Migration journals intent/output/movement, stages all output before moving local parents, preserves failed originals
  and persistent warnings, and rewrites only theme config keys before ConfigService construction. Config intent prevents
  repeated chained renames after interruption; comments and numbered backups are retained. Runtime reads only native
  TOML. Both old stores are removed; Appearance, Font & Colors and View choices remain operational.
  Recorded choices include unknown-parent fallback warnings; verified inverse token mappings/FlatLaf post-JSON
  precedence; explicit classic Darcula scheme and One Dark Modern packaging associations; Frappe → Frappé alias;
  excluded extra Islands variants; publication/marker cleanup failures do not negate already committed success.
  Task 7 also fixes positive-line/zero-column theme diagnostics that interrupted malformed-native reloads.
  Final independent review found four Important issues; one inline TDD fix pass resolves all four. Native parent
  references follow renamed legacy parents through journaled comment-preserving edits with original backups and
  crash/stale-file checks (explicit exception to the plan's no-native-rewrite rule). Native imports resolve declared
  names in local filename order before bundles, with bounded sibling scans. The journal accepts 16×64 providers
  and validates serialized intent before publication; migration hashes honor format limits before/during reads.
  The 18 added regression cases include 66-provider ZIP recovery and sparse oversized inputs. Final full check:
  **7,930/0/0/6**, all architecture gates and 1,637-source hygiene pass; authored diff whitespace clean.
  Deferred minor: CoreSettings theme help still mentions runtime theme plugins; update with Plan 5 Import UI.
  No second review or desktop visual acceptance. User-only acceptance checklist:
  [themes](themes.md#native-acceptance-for-this-upgrade-user-run). **Next: Plan 5**, the Themes page, staged preview,
  Import/Duplicate/Open/Remove UI and live file reload on the completed APIs.

- **Hidden-rail terminal framing** (2026-10-01, user visually accepted): the terminal/tab column
  now fills its available workspace with square corners whenever the rail is hidden or empty. Showing
  the rail restores the rounded frame, exterior spacing and lower corner spacer; plugin panel hosts
  keep their existing treatment. Updated the maintenance recipe and prior size expectations. A new
  headless light/dark render check covers hide/show/hide, flush bounds, corner pixels and edge input;
  existing rounded-frame repaint checks now explicitly show the rail. Independent review approved;
  `./gradlew check` passed: **7,898 tests, zero failures/errors, six skips** (app 1,678).
  Changed-Java source hygiene and `git diff --check` pass. The user approved the appearance and
  authorized merging to `main` and pushing. No agent-launched GUI or benchmark. Check log: `/tmp/rail-edge-check.log`.
s

- **Theme system Plan 3 complete, independently reviewed and merged to local `main`**
  (2026-10-01, not pushed): [plugin pane restyler](superpowers/plans/2026-10-01-theme-system-3-plugin-panes.md)
  now styles mounted center controls with one chrome LAF, preserves plugin styles/direct colors, tracks dynamic
  children and combo dropdowns, and restores state on match/move/close/plugin stop. All five tasks independently reviewed;
  the final whole-branch review's five findings are fixed in `863ca6b9` and independently approved.
  All seven plugins audited: Remote/File Server/Vault/Editor
  custom renderers and rules use mounted-owner palettes; Vim error/bell styles remain plugin-owned; syntax and
  configured yank colors stay unchanged. [Authoring audit](plugin-authoring.md#hosted-pane-control-styling) records
  precedence, exemptions, cleanup and progress text limitations (all three audited bundled bars draw no text).
  Recorded deviations: narrow public `setUI` stack detection plus silent UIResource default semantics; source-owned
  hierarchy observation of excluded associated headers/editors; failed mount/registry rollback and live unmounted
  document retry. Actual Vim and host precedence are tested separately to keep module dependencies unchanged.
  Final serialized `./gradlew check` passed **7,897 tests, zero failures/errors, six skips** (app 1,677),
  including all four architecture gates; Java hygiene (1,636 files) and `git diff --check 5435069c` pass.
  XML counts use explicit top-level module/plugin test roots only. Evidence: `/tmp/p3-final-check.log`,
  `/tmp/p3-final-counts.log`, `/tmp/p3-final-hygiene.log`. Task 5's seven RED regressions and 83 focused GREEN
  tests are retained in the plan; the final fix wave passed 99 focused tests after ownership/accessor/diagnostic REDs.
  Task 5 note-style churn is fixed (`bd4fd9cd`). The final fix wave adds ownership cancellation for synchronous
  generated writes, accessible typed color fallback for nonpublic plugin subclasses, correctly keyed warnings and
  scoped diagnostic assertions, and the corrected three-site progress inventory. Focused independent re-review found no remaining findings or new breakage. User-only native visual checklist is in
  [themes](themes.md). Local fast-forward integration was verified with a fresh `./gradlew check` from `main`: 7,897 tests,
  zero failures/errors, six skips (3m42s; `/tmp/moray-plan3-main-check.log`). No GUI, benchmark or push performed.
  The worktree is retained for reuse. Next: Plan 4 conversion, vendoring and migration; Plan 5 adds the Themes page and live reload.

- **Theme workspace startup regression fixed:** the first native GUI attempt after Plan 2 found that GitHub Dark
  omits the optional IntelliJ `ToolWindow.background` key. The new workspace color lookup threw while creating
  `WindowContent`, and startup rollback immediately exited. Tab strips now fall back to chrome `Panel.background`,
  preserving an authored ToolWindow color and independent workspace `TAB_BAR` tokens. A headless real-window-content
  regression reproduced the logged stack before the fix and verifies absent/present keys, workspace switching and
  retained-tab painting. Independent review approved; full `./gradlew check`: **7,771 tests, zero failures/errors,
  six skips** (app 1,562). No GUI launched; user retry remains the native acceptance check. This correction is included
  in the local Plan 2 integration; no push.

- **Theme system Plan 2 workspace delivery** (branch `codex/theme-system-plans`, 2026-10-01):
  [assessment and requirement coverage](superpowers/plans/2026-10-01-theme-system-remaining-work.md) audits main
  `5fc76c7d` against the full theme design. Plan 2 is implemented, headless-verified and independently reviewed, then merged to local `main` on 2026-10-01;
  user GUI acceptance remains pending. The four sequential deliverables are:
  [2: workspace zone/SDK/Editor](superpowers/plans/2026-10-01-theme-system-2-workspace.md),
  [3: plugin pane styling](superpowers/plans/2026-10-01-theme-system-3-plugin-panes.md),
  [4: conversion/vendoring/migration](superpowers/plans/2026-10-01-theme-system-4-conversion-migration.md),
  [5: Themes page/live reload](superpowers/plans/2026-10-01-theme-system-5-page-reload.md).
  `ui.theme.workspace` independently selects a complete native or in-memory converted Alacritty workspace, with
  `match` sharing the exact generated chrome object. Terminal panes, tabs, dividers, find controls and retained icon
  artwork follow that zone; menus, sidebars and dialogs remain chrome. Editor follows workspace syntax and same-dark
  changes without replacing its documents or undo state. SDK 0.12.0 exposes component-aware `WorkspacePalette`,
  syntax roles/styles, editor palette and workspace events; all seven bundled manifests require it. Only the chrome
  installs FlatLaf. Native effective workspace tokens from complete IntelliJ themes use a private, isolated LAF
  materialization, with all 48 tokens inverted and authored terminal colors, syntax, identity and icons preserved.
  One EDT-owned generated cache prunes obsolete catalog entries and retains active choices. A failed chrome install
  publishes neither slot. Missing chrome uses the safe built-in for its last applied darkness (dark at startup);
  missing workspace temporarily matches chrome while keeping the requested name saved for repair.
  Legacy `terminal.colors` and `ui.theme.terminal` are read aliases only: `light`/`dark` convert to Jasper names only
  from those paths, while canonical names stay literal and win regardless of file order. Saving removes the aliases.
  Legacy dark now uses native Jasper Dark (`#191c20`) instead of the retired `#282a36` palette. The menu identity
  moved to `WorkspaceThemeChoice` because the former adapter misidentified native Light; cost if wrong: revert that
  menu refactor, but the ambiguous selection could not remain. Fake palette reads accept the creating test thread
  and Swing EDT, rejecting other workers; cost if wrong: narrow testkit threading and adapt Editor tests, while the
  production host remains UI-thread confined. Fake workspace setters publish snapshots with queued notifications;
  getters expose the last flushed values, making each callback coherent; cost if wrong: clients depending on
  pre-flush reads must flush or the fake scheduling contract needs revision. Native SDK publication uses Color/Boolean
  UI defaults and an app.plugins-only adapter; Editor maps all 16 syntax roles. Task-level deviations and checks are
  in the [Plan 2 banner](superpowers/plans/2026-10-01-theme-system-2-workspace.md). Serialized `./gradlew check`
  passed: **7,769 tests, 0 failures/errors, 6 skipped** (app 1,560; Editor 4,783). All architecture gates passed;
  `git diff --check` and the Java control/private-use/surrogate scan were clean.
  Final review fix: selected native/Alacritty name collisions keep native precedence and now warn with the shadowed
  source and chosen native theme. Exact-case and mixed-case collisions are covered, with a warning-free non-collision
  control. This fulfills remaining-work decision 6 in the transitional Plan 2 delivery. The whole-branch review
  (`5fc76c7d..ec030a72`) found this omission; the fix wave `1a389653` and whitespace cleanup `1b1cb027`
  passed scoped independent re-review (`ec030a72..1b1cb027`) with no actionable findings. The bundled-source
  collision warning has no dedicated test; the source fallback was reviewed, and exact/mixed-case user-source
  collisions plus the non-collision control are tested. Local integration was verified with a fresh `./gradlew check`
  from main: 7,769 tests, zero failures/errors and six skips (3m 47s). No GUI, benchmark or push was performed.
  **GUI checklist for the user, not run by the agent:** Jasper Light chrome with Jasper Dark and an Alacritty
  workspace; tabs, find, dividers and icons; switch between dark workspaces with Editor open and unsaved edits;
  match; multiple windows; chrome-colored menus, sidebars and dialogs; old IntelliJ Themes and Alacritty Themes
  pages. Those old pages remain through Plan 3 and are removed in Plan 4. Next is Plan 3 generic plugin pane
  styling; Plans 4–5 (conversion/migration and Themes page/live reload) remain future work.

- **Theme system plan 1: format and generator** (branch `claude/theme-system`, spec
  `docs/superpowers/specs/2026-10-01-jasper-theme-system-design.md`, plan
  `docs/superpowers/plans/2026-10-01-theme-system-1-format-generator.md`): themes are Jasper TOML files generated into
  FlatLaf keys (`ThemeToml`, `ThemeResolver`, `ThemeGenerator`, `UiTemplate`). Jasper Dark is native
  (`jasper-dark.toml`, parity with the retired JSON), Jasper Light is new, IntelliJ Light and Darcula are converted
  bundled themes, installed IntelliJ plugins are converted in memory, and `.toml` files in the `themes` folder are
  themes (problems carry their line). Deviations (plan banner): 48 tokens; new `ui_complete` key; no contrast floor
  for borders; Jasper Light keeps the One Light terminal palette; the converter stays in `appearance` until plan 4;
  `Theme.LIGHT` became `Theme.INTELLIJ_LIGHT`, whose terminal palette now comes from its own scheme; spec 2.3's
  missing-theme fallback by the slot's last-known `dark` was deferred to Plan 2 and is now implemented as described
  above; derivation values differ from spec 3's sketch
  (`surface_raised` 6 %, `surface_sunken` 12 % toward black or white, `tab_active` = accent 18 % over the surface,
  `sidebar` = `surface_sunken`; the full table is in `ThemeGenerator`'s JavaDoc). Converted IntelliJ themes keep their
  named colours as `ColorPalette.*` keys (accent included), pinned by a parity test against the theme file. The
  merge review fixes author-specific FlatLaf adjustments (Dracula and Material parity), derived foreground contrast
  against overridden fills, and invalid parent candidates hiding valid themes of the same name. Cycles remain errors even when a
  built-in fallback exists, while same-name overrides extend only lower-priority layers. The named-colour
  suffix concern (`Icon`, `.border`, `.rowHeight`) did not reproduce in installed-colour parity tests: skipped colours
  are restored by the existing installation path. These review fixes are recorded as plan deviations below the banner.
  Independent review complete; merged and pushed to main at `5fc76c7d`. `./gradlew check` passed after integrating current main: 7,720 tests,
  0 failures/errors, 6 skipped (app 1,522). Source hygiene and diff checks passed.
  Plan 2 now implements the deferred workspace fallback above; see the current entry. GUI checks for the user: Jasper Dark unchanged; Jasper Light, IntelliJ Light and Darcula across
  the window, Settings and dialogs; a hand-written theme file in `themes/` (including a broken one's warning).

- **Remote tunnels plan 7b-2: tunnels you run by hand** (branch `claude/tunnels-7b2`, plan
  `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b2-tunnels-by-hand.md`, executed 2026-10-01, user guide in
  `docs/remote.md`): Remote 0.10.0 (`sdk >=0.11.0`) adds saved tunnels (local, remote, dynamic SOCKS5) in
  `data/tunnels.toml` and quick forwards, run by hand from the new Tunnels rail panel, the Tunnels palette scope (Start
  or stop, Open in browser, Edit tunnel), the SSH menu (Toggle Tunnels, New Tunnel..., Forward a Port..., Stop All
  Tunnels) and the terminal context menu (Forward a Port...), with live counters, Open in Browser, Copy Address and a
  "N tunnels" status item. Package `dev.jasper.remote.tunnels`: an own engine (`Listener` binds before the lease,
  `TunnelManager` leases the shared session and forwards each connection over its own direct-tcpip channel, `Pump`
  counts bytes, `Socks5`), a remote tunnel through MINA's remote forwarding to a loopback relay;
  `dev.jasper.remote.ui.tunnels` holds the UI. A lost session fails the tunnel (7b-3 reconnects). The store gives an entry
  without an `id` a stable one derived from its name (as File Server's does since 80a6fe4d), and refuses saves while the
  file has entries it skipped, so a save never deletes them; the final review's fix wave also made the error row read
  "tunnels.toml has errors: <reason>" once, show names as plain text, wrap the empty-state message, fail a tunnel whose
  lease throws, keep the per-session ordering chain alive after an Error and bracket IPv6 addresses in row text.
  Deviations from the spec: `*` is sent to the server as `0.0.0.0`; no `RemoteRelay` class (the relay is a `Listener`
  on `127.0.0.1:0`); the palette scope has no aliases; the rail icon is `BundledIcon.ACTIONS_SWAP_PANELS` and the
  failing status icon `BundledIcon.STATUS_WARNING`, so `RemotePluginTest`'s semantic-icon check admits that one
  catalog icon; `Pump` uses `java.net` sockets; local `*` binds one dual-stack wildcard socket rather than separate
  `0.0.0.0` and `::` sockets; the shared SSH client now accepts server-initiated `forwarded-tcpip` channels, only to a
  loopback target (needed for remote tunnels; MINA's client rejected them all by default); remote forward requests and
  cancels on one session run in the order they were issued, and a second remote tunnel asking for a port another
  tunnel on the same session holds is refused locally (MINA cancels by port on any failed request); palette verbs
  re-resolve the tunnel by id; `BundledSamplePluginTest` and `RemoteShortcutConfigTest` expectations count the new
  panel, toggle and status item; Remote is 0.10.0 because main already shipped 0.9.0. Follow-ups: Toggle Tunnels is
  unbound (⌥⌘4 / Alt+4 would continue the approved panel series; needs the user's approval); the Tunnels panel's row
  operations are list keys, not yet Keymap panel actions like the Sessions panel's; half-close through a remote tunnel
  is untested against OpenSSH, and MINA's client cannot carry a target-first half-close (later client-to-target bytes
  are lost); stopping a remote tunnel while its port request is still in flight and restarting it at once on the same
  port can be refused once; "Allow other computers" and the exposure warning follow the checkbox, not a typed `*`,
  `0.0.0.0` or LAN address (do early in 7b-3); `HostStore` has the same two store defects (id-less entries get a new
  id on every read, skipped entries are dropped by the next save), a small separate task.
  `./gradlew check` green after merging main (File Server FS-2): app 1471/0/1 (also after `:jasper-app:test --rerun`),
  terminal 391/0/4, Editor 4782/0/0, Remote 382/0/0, Vault 140/0/1, Buddy 163/0/0, History 41/0/0, Snippets 11/0/0,
  Sample 12/0/0, File Server 158/0/0, SDK 39/0/0, testkit 79/0/0 (7,669 tests in all, 0 failures, 6 skipped). GUI
  checks pending with the user (`docs/remote.md`, Native acceptance for tunnels). Merged to local main 2026-10-01 (not
  pushed).
- **File Server FS-2: FTP/FTPS and TLS** (branch `claude/file-server-fs2`, plan
  `docs/superpowers/plans/2026-09-30-file-server-fs2.md`): File Server 0.2.0 serves FTP with explicit FTPS (Apache
  FtpServer 1.2.1) and HTTPS, on one self-signed EC certificate in `data/tls/` (BouncyCastle; fingerprint on the server
  card, Show Fingerprints… with Regenerate). Require TLS refuses `USER`/`PASS` before `AUTH TLS` (530) and data
  commands without `PROT P` (521). FTP paths stop `..` at the share root; uploads go through `ShareRoot.upload` and
  commit only on a 226 reply; a non-zero offset is accepted only at the file's end. Logins share `LoginThrottle` (a
  waiting address is refused unchecked). New setting `passive_ports` (the spec's `passive-ports`, renamed to match
  the plugin's other keys). Serve Current Folder over FTP (session password). FS-1 leftovers fixed: `HttpEngine`
  binds after its setup, replaced files keep their POSIX permissions, two event-ordering test races, and the panel's
  empty hint wraps with the divider hidden until a server is selected. Deviations from the plan text (listed in the
  plan's banner): the panel's note text area also guards against a degenerate hidden layout; `HttpEngine.start` reads
  the TLS context before creating any server (no leaked thread or channel on a failed TLS start); `FtpEngine` reports
  the innermost bind `IOException`. Full `./gradlew check` green (2026-10-01, after the final-review fixes): File Server 158 tests, whole repository
  7,584 tests (jasper-app 1,471), 0 failures, 6 skipped. GUI checks pending: FileZilla/Cyberduck over FTP and explicit
  FTPS, a browser over HTTPS (certificate warning, fingerprint match), Require TLS refusing a plain client, the
  editor's TLS and passive-port controls (switching the protocol in the editor drops Use TLS and Require TLS), Show
  Fingerprints and Regenerate, the empty panel's hint.
  Plan decisions not listed above: active mode (`PORT`/`EPRT`) stays allowed with FtpServer's active and passive IP
  checks on, so data connections only go to and come from the client's own address; plain FTP gets the warning "Plain
  FTP sends the password unencrypted"; Show Fingerprints reuses `ConfirmPanel`, with Regenerate; FTP listings show dot
  files, as HTTP's do. Final-review fixes: `TlsMaterial` creates its folder and files owner-only from the start and no
  longer ignores an I/O error from that; the files FtpServer's `MD5` and `MMD5` read now end as complete downloads
  (replies 251 and 252); `ShareFile` compares by path, so `RMD` refuses the working directory; the editor's Passive
  ports row sits below the port hint; `HttpEngine` says "HTTPS" in its create-failure message.
  **Deferred (FS-2):** the login throttle's check-then-act race across parallel sessions (shared with HTTP); a half-present
  TLS pair (only `server.p12` or only `password`) is silently recreated, so the fingerprint can change; after
  Regenerate the card shows the new fingerprint while running TLS servers present the old one until restarted; an FTP
  cancel closes only the file stream; an abandoned `APPE` keeps the bytes it appended; FtpServer logs a WARN per failed
  login; `ShareView.finish` does file I/O while holding the session's monitor.
- **Keymap defaults K1: contexts and defaults** (branch `claude/keymap-defaults`, spec
  `docs/superpowers/specs/2026-09-30-keymap-defaults-design.md`, plan `docs/superpowers/plans/2026-09-30-keymap-defaults-k1.md`):
  actions carry a context (Global, Terminal, a plugin's tabs, a panel, a window). The most specific applicable action
  takes a key; a key with no applicable action reaches the focused component (the shell, in a terminal pane), and so
  does a disabled action's key outside terminal panes. Built-ins have explicit macOS and Linux/Windows defaults; plugin
  cmd+shift defaults get Alt outside macOS. Settings › Keymap has a Where column. Menu items show their shortcuts, but
  only the root input map takes keys, so a key it declines never fires a menu item (Linux Ctrl+Shift+V in a text field
  no longer pastes into the shell). Linux Ctrl+Tab / Ctrl+Shift+Tab switch tabs before focus traversal can take them,
  from a text area, a table or a field too.
  SDK 0.11.0 (with File Server's `network.listen` and `Windows.onQuitting`): `ShortcutContext`, `ActionSpec.withContext` / `withDefaultBindings`, `FakePluginHost.actionSpec`;
  bundled plugins declare `sdk = ">=0.11.0, <0.12"`; Remote 0.9.0, Vault 0.4.0. **Moved defaults** (macOS / Linux and
  Windows): Command Palette ⇧⌘P / Ctrl+Shift+P (was ⌘K / Ctrl+K); Clear Scrollback ⌘K, terminal only (was ⇧⌘K);
  Close Pane ⌘W / Ctrl+Shift+W, closing the tab with its last pane (was ⇧⌘W / Ctrl+Alt+Shift+W); Close Tab ⌥⌘W /
  Ctrl+Alt+Shift+W (was ⌘W / Ctrl+Shift+W); Rename Tab ⌘I / Ctrl+Shift+I (was F2); Reload Config unbound (was ⇧⌘R /
  Ctrl+Alt+Shift+R); Linux Next/Previous Tab Ctrl+Tab / Ctrl+Shift+Tab (was Ctrl+Shift+] / [); Linux Quit
  Ctrl+Alt+Shift+Q (was Ctrl+Shift+Q); SSH Sessions ⌥⌘1 / Alt+1 (was ⇧⌘S / Ctrl+Shift+S); new: SFTP ⌥⌘2 / Alt+2,
  Transfers ⌥⌘3 / Alt+3, Open SFTP Here ⇧⌘O / Ctrl+Alt+Shift+O, Lock Vault ⇧⌘L / Ctrl+Alt+Shift+L; Open Vault
  ⌘\ / Ctrl+Shift+\ (was F8). Deviations
  (plan banner): the SDK type is `ShortcutContext` (`ActionContext` was taken); Find/Copy/Paste stay Global until K3;
  palette hiding of owned-context actions, the testkit's context invocation and DEFAULT_DROPPED, and the Keymap's
  "overrides … here" wait for K3/K4; the docs sweep is K4 (it also owns the stale F8 mentions left in
  `docs/credential-vault.md` and `docs/command-palette.md`, and the old palette example in `docs/configuration.md`;
  Vault's own no-vault notice no longer names F8); menu items show accelerators but never fire them from the keyboard
  (Deviation 6); in a terminal pane a disabled action's key does nothing instead of reaching the shell, because X11 and
  Windows would deliver it as a control character, such as ^C from Ctrl+Shift+C with no selection (Deviation 9).
  **Upgrade note:** a user `[keybindings]` entry on a stroke that is now another action's default (⌘K, ⌘I, ⇧⌘P, ⌘W,
  ⌥⌘W, Ctrl+Tab, Ctrl+Alt+Shift+Q, …) collides, and the loader falls back to the default keybindings, as for any
  collision.
  `./gradlew check` green after merging main: app 1471/0/1, terminal 391/0/4, Editor 4782/0/0, Remote 297/0/0,
  Vault 140/0/1, Buddy 163/0/0, History 41/0/0, Snippets 11/0/0, Sample 12/0/0, File Server 114/0/0, SDK 39/0/0,
  testkit 79/0/0. Merged into local main 2026-09-30 (not pushed). GUI checks pending with
  the user: ⇧⌘P / Ctrl+Shift+P opens the palette; ⌘K clears in a terminal and does nothing in the SSH Sessions panel's
  search; ⌘W closes a split pane, then the tab; on Linux Ctrl+K and F2 reach the shell, Ctrl+Tab switches tabs from a
  terminal, an Editor tab or a panel field, Ctrl+Shift+C with no selection in a terminal does nothing, and
  Ctrl+Shift+V in a panel field pastes into the field only; ⌥⌘1 / Alt+1 toggles SSH Sessions; the Keymap shows the
  Where column; menus still show each shortcut, a click still runs the item, and every menu shortcut still works from
  a terminal.
- **File Server FS-1: foundation and HTTP** (branch `claude/file-server-fs1`, spec
  `docs/superpowers/specs/2026-09-30-jasper-file-server-design.md`, plan
  `docs/superpowers/plans/2026-09-30-file-server-fs1.md`, user guide `docs/file-server.md`): SDK 0.11.0 adds the
  `network.listen` capability and `Windows.onQuitting` quit guards (app `QuitGuards`, testkit fake and contract tests);
  every bundled plugin declares `sdk = ">=0.11.0, <0.12"`. New bundled plugin File Server (`plugins/fileserver`,
  `dev.jasper.fileserver` 0.1.0): File Servers rail panel, editor, status item, palette scope, Serve Current Folder over
  HTTP, quit confirmation, autostart (waiting for a locked Vault), Vault or session logins, `ShareRoot` jail, login
  throttle, and an HTTP engine on the JDK server (listings, ranges, Basic auth, uploads and deletes on writable shares,
  Host and Origin checks). Deviations from the spec: user doc in `docs/file-server.md`; setting keys
  `confirm_quit`/`activity_entries`; the privileged-port suggestion is port + 8000; Host/Origin checks added; palette
  titles in house style with a scope for per-server Start/Stop; saving a temporary server adopts it. Execution
  deviations (all listed in the plan's status banner): `ResolvedLogin.isAnonymous()`; `ShareRoot` also refuses
  unrepresentable names and C1 control characters; the login throttle answers early or locked-out attempts with 429 and
  `Retry-After` instead of sleeping, and the Host check runs before credentials; the listing sends
  `Referrer-Policy: same-origin`; transfer ids are unique per JVM; the port-clash check uses the definition a running
  engine uses; FAILED from RUNNING is not implemented (engines have no fatal-runtime channel); the panel is
  master/detail; an Interface bind advertises one URL; the status item toggles the panel; labels set `html.disable`.
  Open follow-up: `shutdown()` stops engines in parallel under one 5 s deadline, so `Plugin.stop()` can block up to 5 s,
  against the SDK's "must not block". `./gradlew check` green: jasper-app 1,439 tests (0 failures, 1 skipped),
  jasper-terminal 391 (0, 4 skipped), jasper-buddy 163, jasper-sdk 35, jasper-sdk-testkit 74, editor 4,782,
  fileserver 114, history 41, remote 297, sample 12, snippets 11, vault 139 (0, 1 skipped). Earlier runs saw flaky
  `RemotePluginTest.endingAPanesSessionClearsTheSftpView`, `TransferCoordinatorTest.resumedScannedJob...` and
  `TerminalAppIntegrationTest.rowResetRejects...`; none failed in the final run. Final-review fixes: a `servers.toml` entry without an id gets a stable id derived from
  its name, and saves are refused while the file has skipped entries; uploads go to a hidden temporary file and replace the target atomically only when
  complete; activity refreshes coalesce into one UI task; activity-log text from clients has control characters
  replaced; a cancelled download is not logged as an error; a failed `HttpEngine.start` releases its pool (FS-2 also binds only after the setup succeeds).
  Merged into local main 2026-09-30 (not pushed) on top of SDK 0.10.2 (connection activities, `Platform.browse`), which
  0.11.0 now includes; every bundled plugin declares `sdk = ">=0.11.0, <0.12"`, so the Remote tunnels plans (7b) must use
  that range. GUI checks pending with the
  user: New File Server and start it; open the URL from a phone on the LAN with All networks (macOS firewall and Local
  Network prompts); upload two files and delete one from the listing page in a real browser (Chrome, Safari, Firefox)
  on a writable server; a wrong password then an immediate retry shows a short 429 wait; a Vault account login with a
  locked Vault and Start With Jasper; Serve Current Folder from a terminal; the quit confirmation; Reveal Folder and
  Copy Address.

- **Remote tunnels plan 7b-1: SDK 0.10.2 connection activities** (branch `claude/tunnels-7b1`, plan
  `docs/superpowers/plans/2026-09-30-jasper-remote-plan-7b1-connection-activities.md`, executed inline 2026-09-30):
  `Activities.connection` returns a `ConnectionHandle` (`up`, `degraded`, `down`, `close`) publishing
  `ConnectionEvent`s on `Activities.CONNECTIONS` (states in order, detail-only reports coalesced, one `CLOSED`, closed
  when the plugin stops); `Platform.browse(URI)` opens http/https addresses through the new `platform.Browser`; Buddy
  shows connections as connection cards, and a card whose `activateActionId` names the source plugin's own action
  runs it in the last active window and is acknowledged (task cards too). Testkit: `connectionLog()`, `browsed()`;
  contract +3 cases. No deviations from the plan. `./gradlew check` green: app 1439/0/1, terminal 391/0/4,
  Editor 4782/0/0, Remote 297/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0, Snippets 11/0/0, Sample 12/0/0,
  SDK 36/0/0, testkit 75/0/0. Seen once during Task 1's first full check and not reproduced in six reruns (four
  alone, two whole-module): `RemotePluginTest.endingAPanesSessionClearsTheSftpView` read the SFTP panel's folder
  ("/") right after its rows emptied; a timing flake unrelated to this plan. Merged to main at f7b9cd76; plan 7b-2 builds on it.
- **Remote tunnels design (7b)** (branch `claude/tunnels-spec`, spec
  `docs/superpowers/specs/2026-09-30-jasper-remote-tunnels-design.md`, brainstormed and approved section by section
  2026-09-30; written spec approved by the user the same day): tunnels with their own lifecycle (never tied to terminals)
  through a saved host, local / remote / dynamic SOCKS5, Manually or When Jasper starts, quick unsaved forwards, a
  Tunnels rail panel, an own forwarding engine over MINA channels (live counters; reconnect with per-host backoff
  keeps the local port bound), unattended connections that never prompt (new `Connections` unattended lease, Vault
  0.4.0 `credentialIfAllowed`), and Buddy connection cards for trouble only through SDK 0.10.2 connection activities
  (plus `Platform.browse` and card activation). Replaces the tunnels outline in the Remote design's section 9. Three
  plans: 7b-1 SDK (executed, entry above), 7b-2 tunnels run by hand, 7b-3 tunnels that stay up.
- **Terminal colors follow the UI theme** (branch `claude/terminal-follow-theme`, bounded change approved in chat
  2026-09-30, no spec): Font & Colors' Terminal colors row is a checkbox, "Terminal colors follow the UI theme" (the
  value `match`), over the dropdown of separate choices (Light, Dark, every terminal theme, More Terminal Themes…),
  which is off while the box is ticked; clearing it returns to the last separate choice (this dialog's, else the saved
  one, else Dark). No new setting. `./gradlew check` green: app 1429/0/1, other modules unchanged. Merged into local main
  2026-09-30 (not pushed). GUI check pending: tick and clear the box, the dropdown greys out, Apply switches the terminals.
- **Alacritty terminal themes** (branch `claude/alacritty-themes`, spec
  `docs/superpowers/specs/2026-09-30-alacritty-terminal-themes-design.md`, plan
  `docs/superpowers/plans/2026-09-30-alacritty-terminal-themes.md`): `terminal.colors` accepts a terminal theme's name;
  Jasper bundles the alacritty-theme collection (177 themes, commit ab88d5a8) and reads `.toml` files in
  `terminal-themes/`; Settings › Themes › Alacritty Themes lists them with a colour strip and a sample terminal drawn by
  the real renderer, with Use, Install from Disk…, Reveal Folder and Remove…; Font & Colors' dropdown lists them and ends
  in More Terminal Themes…. New package `terminalthemes`. Deviations from the plan: `TerminalColors` declares its
  reserved-names list before the constants; `ThemeController.configure(Theme)` and `configure(Theme, UiFontConfig)` keep
  a named terminal palette; the Alacritty page shows the selected file's problems in red; the sample terminal keeps its
  preferred (60x10) size; the page test uses a non-bundled theme and a separate test shows a user file shadowing bundled
  "Tokyo Night" until removed; failed rescans and reveal failures are reported. Final-review fixes: deeply nested
  theme files (a tomlj StackOverflowError) are reported instead of stopping start-up, unreadable files are retried,
  the page's problems text no longer takes the spare height, View › Terminal Colors shows no check for a named theme,
  the two start-up scans are guarded separately, and the configuration/themes/architecture docs were corrected.
  `./gradlew check` green: jasper-app 1,428 tests (0 failures, 1 skipped), jasper-terminal 391 (0, 4 skipped), jasper-buddy 163 (0, 0), jasper-sdk 34 (0, 0), jasper-sdk-testkit 72 (0, 0), editor 4,782 (0, 0), history 41, remote 297, sample 12, snippets 11, vault 139 (0, 1 skipped). Merged
  into local main 2026-09-30 (not pushed). GUI checks pending with the user: the dropdown and More Terminal Themes…; the sample in the configured font;
  Use then Apply recolours open terminals; install a downloaded theme and remove it; a theme without cursor or selection
  colours; switching back to Match the theme.
- **IntelliJ themes from disk and the Marketplace** (branch `claude/theme-marketplace`, spec
  `docs/superpowers/specs/2026-09-30-intellij-theme-marketplace-design.md`, plan
  `docs/superpowers/plans/2026-09-30-intellij-theme-marketplace.md`): Settings gains a Themes group whose IntelliJ Themes
  page has Marketplace (search, details, Install) and Installed (Use, Update, Uninstall, Install from Disk…, Reveal
  Folder) tabs; the Appearance dropdown ends in Get More Themes… (Manage Themes… when `themes.marketplace.enabled` is
  false). New package `themestore` is Jasper's first network client. Deviations from the plan: the Marketplace client
  refuses a first request to any host other than the base and uses shutdownNow on close; the installer replaces files
  atomically (disk installs go through `.downloads`), writes provenance before deleting old versions, accepts only
  .jar/.zip and only uninstalls .jar/.zip; ThemeStore calls the rescan hook on the EDT, retries failed icon fetches and
  clears leftover downloads once per app run; the description pane uses an allowlist HTML sanitiser (basic formatting
  tags, only a[href]; links open in the browser, http/https only); the Marketplace tab loads the installed list when
  shown; details failures show an inline Retry and install/update failures offer Retry in the banner; the package-info
  `{@link}` names ThemeStore and the ThemeCombo renderer calls `ThemeCombo.isAction(value)`. Final-review fixes: arrow
  and page keys no longer navigate from the dropdown's action entry; `#installed`/`#marketplace` reveal the tab before
  the page is selected so only that tab loads; update checks run only on the Installed tab; the sanitiser caps input at
  256 KiB and escapes href quotes once; old-version deletes stay inside the folder (.jar/.zip only); unexpected
  failures show a generic message and are logged; the details header shows the icon; the threaded ThemeStore test is
  deterministic. `./gradlew check` green:
  app 1393/0/1, terminal 391/0/4, Editor 4781/0/0, Remote 297/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0,
  Snippets 11/0/0, Sample 12/0/0, SDK 34/0/0, testkit 72/0/0. Merged into local main 2026-09-30 (not pushed; merged-tree check green, app 1396/0/1, Editor 4782/0/0). Follow-up fixed on `claude/theme-combo-enter`: Enter on the highlighted Get More Themes…/Manage Themes… row of the open theme list now navigates (the editor field binds Enter; an editable combo's own Enter keeps the typed text). GUI checks pending with the user:
  Get More Themes… from the dropdown; search "dracula", install, Use and Apply; an Islands-based theme; Material Theme
  UI refused cleanly; Install from Disk…; Uninstall of the active theme; the offline checkbox; arrowing through the theme dropdown; the page in dark and
  light themes.
- **File chooser plan C: Save As anywhere** (branch `claude/file-chooser-c`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-c.md`): Editor 0.6.1's Save As uses Jasper's chooser in Save mode,
  starting in the document's folder (local or remote) with its name filled in, offering local places and every file
  source with a resolver; a local document can be saved to a remote host, a remote one to this computer, and a new
  remote file is created by its first write. An untitled tab's Save As starts where the focused terminal is. Directory
  mode's status line names the folder Choose will pick. No API change. `./gradlew check` green: app 1349/0/1, terminal 391/0/4, Editor 4782/0/0, Remote 297/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0, Snippets 11/0/0, Sample 12/0/0, SDK 34/0/0, testkit 72/0/0. Not merged.
  GUI checks pending with the user: Save As a local file onto a host and back; Save As over an existing remote file
  asks to replace it; Directory mode (for example SFTP's "Download selected files to") shows the Choose target.
- **File chooser plan B2: Remote hosts and remote Open in Editor** (branch `claude/file-chooser-b2`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-b2.md`): Remote 0.8.0 registers its saved hosts as a file source
  (`dev.jasper.remote.chooser.RemoteHosts`, `files.source`): each host is a root with a connected dot, clicking it
  resolves the logins (Vault prompts as usual) and browses over SFTP with links followed, and an SSH pane is located on
  its host at the shell's folder (or the login's home). Editor 0.6.0 adds `LocationResolver` /
  `Editor.locationDocuments`; Open in Editor… (Cmd/Ctrl+O) now always shows Jasper's chooser, offering sources with a
  resolver and opening their files through it, starting in the active document's folder (local or remote) or where the
  focused pane is. Remote's resolver builds `RemoteDocument`s. Removed: `RemoteFilePicker`, `RemoteOpening`, File ›
  Open Remote File… and `SftpPanel.pickMode`. SDK 0.10.1: `FileLocation.Sourced.HOME`. `./gradlew check` green:
  app 1348/0/1, terminal 391/0/4, Editor 4781/0/0, Remote 297/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0,
  Snippets 11/0/0, Sample 12/0/0, SDK 34/0/0, testkit 72/0/0. Not merged. GUI checks pending with the user: Cmd/Ctrl+O in an SSH pane opens on that host at the shell's
  folder; clicking a disconnected host connects (Vault prompt, Cancel, Retry); opening a remote file; Cmd/Ctrl+O from a
  remote Editor tab starts in its folder; pinning a remote folder.
- **File chooser plan B1: SDK file sources** (branch `claude/file-chooser-b1`, plan
  `docs/superpowers/plans/2026-09-30-file-chooser-b1.md`): SDK 0.10.0 adds `dev.jasper.sdk.files` (`FileSources`,
  `FileSource`, `FileBrowser`, `FileLocation`, `FileChoice`, `FileEntry`, `FileRoot`, `FileSourceSpec`), the
  `files.source` capability and `Windows.choose(FileChoice)`; all bundled plugins declare `sdk = ">=0.10.0, <0.11"`.
  The chooser browses `Location`s through app-internal `Browser`s and `ChooserSource`s; plugin sources appear as
  navigation sections with a connected dot, connect on click (Cancel connection, Retry on failure) and list in
  256-entry batches; `Windows.choose` starts where a named source locates the focused pane. No bundled plugin
  registers a source yet (Remote does in plan B2), so the app looks as after plan A. `./gradlew check` green: app 1348/0/1, terminal 391/0/4, Editor 4778/0/0, Remote 306/0/0, Vault 139/0/1,
  Buddy 163/0/0, History 41/0/0, Snippets 11/0/0, Sample 12/0/0, SDK 34/0/0, testkit 72/0/0.
  Not merged. GUI checks: none new beyond plan A's (re-check the local chooser).
- **Jasper file chooser, plan A: local** (branch `claude/file-chooser`, spec
  `docs/superpowers/specs/2026-09-30-file-chooser-design.md`, plan `docs/superpowers/plans/2026-09-30-file-chooser-a.md`,
  executed inline 2026-09-30): every native file picker (`FileDialog`, `JFileChooser`) is replaced by Jasper's
  Explorer-style chooser (`dev.jasper.app.files`): navigation pane (Terminal folder, home, Desktop, Downloads, Pinned,
  Recent), back/forward/up, breadcrumb path bar with typed paths (Cmd/Ctrl+L), search filter, sortable list, preview
  pane toggle, dotfile toggle (Cmd/Ctrl+Shift+.), typed paths in the name field, Save with replace confirmation and
  New folder. Choosers start at the owner's focused-pane folder when the caller gives none; Open in Editor… no longer
  overrides that in a terminal pane. No SDK change (moved to plan B; deviations listed in the plan). `./gradlew check`
  green: app 1338/0/1, terminal 391/0/4, Editor 4778/0/0, Remote 306/0/0, Vault 139/0/1, Buddy 163/0/0, History
  41/0/0, Snippets 11/0/0, Sample 12/0/0, SDK 31/0/0, testkit 69/0/0. Not merged. GUI checks pending with the user:
  Cmd/Ctrl+O from a local pane opens at its folder quickly (target about 100 ms); Save As in the Editor; Settings file
  pickers; Install Plugin (.zip only); SFTP upload/download folder pickers; dark and light themes; long paths in the
  path bar. Cmd/Ctrl+O in an SSH pane still shows the old remote picker until plan B.
- **Opening remote files from Cmd/Ctrl+O and File › Open Remote File…** (branch `claude/remote-open-file`, spec
  `docs/superpowers/specs/2026-09-29-remote-open-file-design.md`, plan
  `docs/superpowers/plans/2026-09-29-remote-open-file.md`, executed inline 2026-09-29): Open in Editor… asks openers
  before the local chooser (Editor 0.5.0: `OpenRequest`, `Editor.opener`); the Editor tab's hardcoded Cmd/Ctrl+O is
  gone, so the Keymap's binding for Open in Editor… applies in terminal panes and Editor tabs. Remote 0.7.0 answers
  for connected SSH panes and remote Editor tabs with a modal Open Remote File picker (host dropdown, navigation,
  multi-select, up to 20 files, links followed), also on File and SSH › Open Remote File… (no default shortcut); the
  SFTP panel's open-in-Editor logic moved to `EditorOpening`, shared with the picker. `./gradlew check` green: app
  1294/0/1, terminal 391/0/4, Editor 4776/0/0, Remote 299/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0,
  Snippets 11/0/0, Sample 12/0/0, SDK 31/0/0, testkit 69/0/0 (tests/failures/skipped). Not merged. GUI checks pending
  with the user: Cmd/Ctrl+O in an SSH pane, a remote Editor tab and a local pane; rebinding Open in Editor… in the
  Keymap moves all three; File › Open Remote File… with and without an SSH pane focused; on Linux/Windows, the
  Keymap's cmd+o is Ctrl+Shift+O, which now applies in Editor tabs too (their old in-tab key was Ctrl+O). Superseded by the file chooser (plan B2): the picker and Open Remote File… are gone.
- **IntelliJ parent themes and schemes** (branch `claude/intellij-parent-themes`, bounded change approved in chat
  2026-09-29, no spec): every theme and editor scheme IntelliJ bundles at the pinned intellij-community commit is
  vendored under `themes/intellij/` (New UI `expUI/`, `islands/`, `Light.xml`), so plugin themes built on
  `ExperimentalDark`, `Islands Dark`, `Islands Light` etc. and schemes naming `Dark`, `Light`, `Islands Dark` etc.
  resolve without falling back to Darcula/IntelliJ or Jasper's palette. Parents only: the picker still offers only
  Jasper Dark and IntelliJ Light. Licensing: the files are Apache 2.0; the repo's root LICENSE.txt became JetBrains'
  "Open-Source Build Terms" in June 2026, which cover their distributed builds (recorded in `SOURCE.md`).
  `./gradlew check` green: app 1294/0/1, others unchanged. Merged into local main (380ef131) on 2026-09-29; not
  pushed. GUI check pending with the user: install an
  Islands-based theme plugin and a Dracula plugin and compare their chrome and terminal with IntelliJ.
- **Recent files in the command palette** (branch `claude/editor-recent-files`, spec
  `docs/superpowers/specs/2026-09-29-editor-recent-files-design.md`, plan
  `docs/superpowers/plans/2026-09-29-editor-recent-files.md`, executed inline 2026-09-29): the Editor keeps the local
  and remote files it opened in `plugins/dev.jasper.editor/data/recent-files.toml` (newest first, at most 50,
  forgotten after `recent_files_days`, default 30; 0 keeps none) and offers them in the "Recent files" palette scope
  (Open, Remove from recent files; action Recent Files…). Editor 0.4.0 adds `Editor.reopener` and now declares
  `palette.contribute` (a deviation: the plan missed that registering a scope needs it); Remote 0.6.2 registers
  `remote:` and reopens on the saved hosts. `./gradlew check` green: app 1290/0/1, terminal 391/0/4, Editor 4770/0/0,
  Remote 289/0/0, Vault 139/0/1, Buddy 163/0/0, History 41/0/0, Snippets 11/0/0, Sample 12/0/0, SDK 31/0/0,
  testkit 69/0/0 (tests/failures/skipped). Not merged. GUI checks pending with the user: open a local and an SFTP
  file, restart, reopen both from Recent files (the SFTP one prompts for sign-in when needed); deleting the host dims
  its row; `recent_files_days = 0` empties the scope.
- **Editor Vim mode, plan V4d** (branch `claude/vim-v4d`, plan
  `docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4d.md`, written 2026-09-29): sneak, the last of spec 8's extensions (`s`, `S`, `z`,
  `Z`, `;`, `,`, vertical scope, the one-character `<Plug>Sneak_f` forms; no label mode), on motions that read their
  keys first (`VimCommands.motionWithKeys`). Every file in the plan was compiled and run against Vim 9.1 with the
  pinned sneak before the plan was written: 2211 corpus cases (47 new), with and without a final line break.
  - Executed subagent-driven on 2026-09-29 (c18ceb96, 8f030022; final-review fixes f731d93e: the sneak highlight ends
    when the text changes, scripted `F`/`T` leave sneak's `;` alone, `g:sneak#f_reset` and `t_reset`); `./gradlew check`
    7,157 tests green.
  - Merged into local main (fast-forward, after merging main: 7,178 tests green) on 2026-09-29; not pushed.
  - Not done: GUI checks 110–114 of the [checklist](editor-verification.md) (user-run); push.
- **Settings fixes and the Plugins page** (`claude/settings-polish`, 2026-09-29; spec
  `docs/superpowers/specs/2026-09-29-jasper-plugins-page-design.md`, plan
  `docs/superpowers/plans/2026-09-29-jasper-plugins-page.md`):
  - Settings fixes: the theme picker enables Apply again, since every dialog replays the theme when it opens, which
    gave the editable combo a new editor field. Narrow pages no longer squash lists, tables and fields: descriptions
    wrap, editors keep their size, and a page too narrow for its editors scrolls sideways.
  - Settings › Plugins is IntelliJ-style: cards grouped Development, User-installed and Bundled, with icons, badges and
    enable checkboxes, a search field and a gear menu (Install from Zip…), and a details pane with the icon, vendor,
    homepage link, Enable/Disable or Review… with a ▾ menu, and Overview and Details tabs.
  - `plugin.toml` gains optional `icon` and `homepage` (SDK 0.9.3); every bundled plugin ships an icon and requires
    `>=0.9.3, <0.10`.
  - `./gradlew check`: 6,860 tests, 0 failures, 6 skipped.
  - GUI checks pending with the user:
    - the theme picker enabling Apply;
    - Terminal › Shell and Tools › Shell History at a narrow width;
    - cards and groups;
    - toggling from a card, and review from a checkbox;
    - the ▾ and right-click menus;
    - search;
    - the homepage link;
    - icons in dark and light.
  - Deviations: see the plan's status banner. Merged into local `main` on 2026-09-29 after merging main (Vim V4c,
    palette recents, terminal surface) into the branch; the manifest keys moved to SDK 0.9.3 there. `./gradlew check`
    on the merged tree: 7,070 tests, 0 failures, 0 errors, 6 skipped. Not pushed.
- **Editor Vim mode, plan V4c** (branch `claude/vim-v4c`, plan
  `docs/superpowers/plans/2026-09-29-jasper-editor-vim-v4c.md`, written 2026-09-29): matchit, ported from Vim 9.1's matchit.vim (`%`, `g%`,
  `[%`, `]%`, Visual `a%`; `set matchit`, `packadd matchit`), with the languages' `b:match_*` from Vim's filetype
  plugins (`matchit-languages.tsv`); look-arounds and columns in Vim patterns (`\@<=`, `\%23c`), Vim script's string
  functions (`VimStrings`) and searchpair(). The corpus gains `filetype:` (Vim loads the filetype plugin and syntax).
  Every file in the plan was compiled and run against Vim 9.1 before the plan was written: 2164 corpus cases
  (98 new), with and without a final line break.
  - Executed inline on 2026-09-29 (9f85bd53..4ce02518): every step's expected result matched; `./gradlew check`
    7,031 tests green.
  - Merged into local main (fast-forward, after merging main twice: 7,050 tests green) on 2026-09-29; not pushed.
  - Not done: GUI checks 104–109 of the [checklist](editor-verification.md) (user-run); plan V4d
    (sneak); push.
- **Palette recents and scope hints** (branch `claude/palette-recents`, bounded change agreed in chat 2026-09-29):
  the empty palette shows the last two rows run (Recently used), then rows ranked by frecency (Most used), topped up
  with scopes' own suggestions to ten; the scope prefixes are pills along the bottom edge (the mode list is gone).
  Uses (scope, row, title, count, last) are saved in `command-history.toml` (version 2; version 1 still reads), at
  most 50. SDK 0.9.2 adds `ScopeSpec.withInRecents`; History opts out (`sdk = ">=0.9.2, <0.10"`) and scopes out of
  All are out too. Each hint pill shows the key that selects its scope (`>` for Commands, else the scope's own
  shortcut). Merged into local main 2026-09-29; not pushed. Not done: GUI check of the pills and lists, push.
  The pills were removed on 2026-10-02 (see above).
- **Title-bar command centre, plan B** (branch `claude/titlebar-plan-b`, plan
  `docs/superpowers/plans/2026-09-29-jasper-titlebar-command-center-b.md`):
  - **Centring:** the search field is always centred on the window, and the sides give way first.
  - **Palette:** the card is one colour.
  - **Windows and Linux:** get FlatLaf's title bar with the app icon, menus (folding to "…" then ☰), field,
    toolbar and window buttons; `window.native_decorations` (restart) keeps the system title bar.
  - Executed inline 2026-09-29 (6b7e0334..536ff95c); `./gradlew check` 6,535 tests green (6 skipped).
  - Deviations: listed in the plan's banner.
  - Merged into local main 2026-09-29; not pushed.
  - Not done: GUI checks on Windows and Linux (Snap Layouts, double-click maximise, dragging the empty title
    bar and toolbar gaps, both menu folds, F10), the macOS look after the centring change, push.
- **Editor Vim mode, plan V4b** (branch `claude/vim-v4b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4b.md`, written 2026-09-28): the extensions paragraph-motion, textobj-entire,
  textobj-indent, argtextobj and highlightedyank, switched on by IdeaVim's names (`:set` takes hyphens) or `Plug`
  lines. Their text objects select in Visual mode as the plugins do and the pending operator acts on that area
  (`Script.select`); highlightedyank draws a third kind of highlight after a yank. The corpus gains `let:`, pins that
  need pins (textobj-user) and `plugin/**`. Every file in the plan was compiled and run against Vim 9.1 with the pinned
  plugins before the plan was written: 2066 corpus cases (160 new), with and without a final line break.
  - Executed inline on 2026-09-29 (fd2bca5b..ba1aa09d): every step's expected result matched; `./gradlew check`
    6,809 tests green.
  - Merged into local main (fast-forward, after merging main: 6,827 tests green) on 2026-09-29; not pushed.
  - Not done: GUI checks 97–103 of the [checklist](editor-verification.md) (user-run); plan V4c
    (matchit, sneak); push.
- **Title-bar command centre, plan A** (branch `claude/titlebar-command-center`, spec
  `docs/superpowers/specs/2026-09-28-jasper-titlebar-command-center-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-titlebar-command-center-a.md`):
  - **Header:** every window's header centres a VS Code-style search field showing the working
    context and packs the toolbar right behind a ⋮ overflow; a hidden toolbar keeps the header row.
  - **Palette:** drops from the field; its tabs are replaced by scope chips (`>`, `history >`, …) and
    a mode list (Commands first); Tab no longer switches scopes; Backspace in an empty query removes the chip.
  - Executed inline 2026-09-28 (21664347..8474e9cf); `./gradlew check` 6,520 tests green (6 skipped).
  - Deviations: test-only, listed in the plan's banner.
  - Not done: GUI checks (field and palette look in light and dark, the drop, native dragging
    around the field and not over the open card), plan B (Windows/Linux custom title bar), merge
    and push.
- **Editor Vim mode: files without a final line break** (branch `fix/vim-final-break`, merged into local main
  2026-09-28, not pushed): the corpus runs every case a second time on its text without a final line break, which
  found empty last lines lost by `x`, `s`, `D`, `cc`, `S`, `o` and `==` (the case 093245f7 fixed for `<CR>`/`o`),
  the empty text swallowing a put or opened line, and the cursor after undo there. `Buffer` keeps an empty last line
  in every edit; Visual put over every line removes Vim's dummy line (ML_EMPTY); `empty-lines.case` (26 cases) was
  recorded from Vim. `./gradlew check` 6,503 tests green.
- **Merged `origin/main` (WSL shell profiles, ConPTY, SDK 0.9.1, Remote WSL group) into local main** (2026-09-28)
  after the Vim V3b/V4a work and two Vim fixes (branch `fix/vim-search-highlight`): `<Esc>` in Normal mode with
  nothing pending hides the 'hlsearch' matches like `:noh` (a divergence, in `docs/vim-divergences.md`), and `<CR>` or
  `o` on the last line of a file without a final newline opens the new line instead of returning to the line's start
  (`Buffer.setLine`/`insertLines` add the final newline an empty last line needs). Only this file conflicted.
- **Editor Vim mode, plan V4a** (branch `claude/vim-v4a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v4a.md`, written 2026-09-28): the extension framework (`set {name}` and `Plug` lines switch
  one on, with its plugin's default mappings over `<Plug>` commands, hasmapto() and `g:…_no_mappings`) and ports of
  surround, commentary ('commentstring' from the tab's language), exchange and ReplaceWithRegister, which follow
  their plugins' scripts on the engine's commands (`Script`, `Machine.nested`; surround's getchar() and input() through
  `Machine.readKey` and `readLine`). The corpus's `ext:` cases are recorded against the plugins and tpope/vim-repeat at
  the commits `plugins/editor/vim-oracle.properties` pins. Also `g'` and `` g` ``, and the `'[` mark after a put of
  several lines (fixed). Every file in the plan was compiled and run against Vim 9.1 with the pinned plugins before the
  plan was written: 1880 corpus cases (138 new).
  - Executed inline on 2026-09-28 (bf4dab73..ee1ab3fa): every step's expected result matched; `./gradlew check`
    4,505 tests green.
  - Merged into local main (fast-forward) on 2026-09-28; not pushed.
  - Not done: GUI checks 89–96 of the [checklist](editor-verification.md) (user-run); plan V4b
    (argtextobj, textobj-entire, textobj-indent, matchit, paragraph-motion, highlightedyank, sneak); push.
- **Editor Vim mode, plan V3b** (branch `claude/vim-v3b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3b.md`, written 2026-09-28): the vimrc (`plugins/dev.jasper.editor/data/vimrc`), read the
  first time Vim turns on and on Reload vimrc, with comments, continuation, `source` (10 deep), `if has('ide')`, and
  skipped lines counted in a notice and listed in `:messages`; `:let` for `mapleader`, registers, options and
  variables; `:sethandler`; `<Action>(id)`, `:action` and `:actionlist` over the Editor's commands; the options vimrcs
  set; `:syntax` and `:filetype` accepted. Every file in the plan was compiled and run against Vim 9.1 before the plan
  was written: 1742 corpus cases (28 new in `vimrc.case`), with files and the Editor's commands in `VimrcTest` and
  `EditorWindowTest`.
  - Executed 2026-09-28 (inline, one commit per task); every task's tests and re-recording matched the plan.
  - `./gradlew check`: 4,359 tests, 0 failed, 0 errors, 5 skipped.
  - Merged into local `main` on 2026-09-28 as a fast-forward (main had not moved); the check above is the merged
    tree's. Not pushed.
  - Not done: GUI checks 80–88 of the [checklist](editor-verification.md) (user-run); plan V4
    (extensions); push.
- **Merged main (plugin settings C1-C3, Vim V1c-V3a) into `worktree-wsl-hosts`** (2026-09-28). Main's SDK 0.9.0
  plus this branch's additive profile API ship as SDK 0.9.1 (`JasperSdk.VERSION`, `@since 0.9.1`, version tests);
  Remote is 0.6.0 with `sdk = ">=0.9.1, <0.10"` (main's declared settings and the WSL group); the other bundled
  plugins keep main's `>=0.9.0, <0.10`. `PluginHost.Environment` takes main's settings files and report clearing plus
  `PluginRuntime.ShellProfiles`; `CorePages.pages` gains an overload with both the detected shells and the plugin
  pages under Tools, which `JasperApplication` uses. `./gradlew check --continue` on Windows: 4367 tests, 234 skipped,
  13 failed (app 1221/141/3, terminal 391/19/1, Editor 2005/13/3, Remote 284/54/6, Vault 139/7/0, Buddy 163/0/0,
  History 41/0/0, SDK 31/0/0, SDK testkit 69/0/0, Sample 12/0/0, Snippets 11/0/0): the known list plus four that
  also fail on `main` on Windows (listed in `docs/windows-startup-verification.md`). Not pushed.
- **WSL B: SDK shell profiles and the Remote WSL group** (`worktree-wsl-hosts`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-wsl-b-remote.md`): SDK 0.9.1 (written as 0.8.6; renumbered when main's SDK 0.9.0 was merged) adds `ShellProfileInfo`,
  `Terminals.profiles()`/`onProfilesChanged` (default methods, `terminal.open`), `LocalSpec.profile` and
  `OpenRequest.localProfile`; the app feeds plan A's `ShellProfileCatalog` to plugins through
  `PluginRuntime.ShellProfiles` and refuses unknown ids (empty result, warning); the testkit scripts profiles
  (`setShellProfiles`) and the contract suite covers known, unknown and default. Remote 0.6.0 (`sdk >=0.9.1, <0.10`; 0.5.0 before the merge)
  titles its panel Sessions and shows a read-only WSL group first (badges, session dots, Open in new tab/split,
  its own `wsl_collapsed` flag), lists distributions first in the Sessions dropdown (no separator: the SDK dropdown has
  none) and as `WSL` rows in `>ssh`. `./gradlew check --continue` on Windows: 3479 tests, 234
  skipped, 8 failed, all on the known list in `docs/windows-startup-verification.md` (app 1187/141 skipped/2 failed,
  terminal 391/19/0, Editor 1172/13/0, Remote 278/54/6, Vault 139/7/0, Buddy 163/0/0, History 40/0/0, SDK 27/0/0,
  SDK testkit 60/0/0, Sample 11/0/0, Snippets 11/0/0); hygiene scan clean. Native WSL acceptance (docs/remote.md items 11 to 15, also in
  `docs/windows-startup-verification.md`) and the macOS/Linux check pending with the user. Deviations from the
  plan: none in code. Plan A's names matched the plan's assumptions (`ShellProfile.Kind`, `ShellProfileCatalog`,
  `OpenSpec.Local(Optional<Path>, Optional<String>)`, `JasperApplication.shellProfiles`); `knownProfile` compares
  ids exactly although plan A launches `wsl:` ids case-insensitively (plugins receive canonical ids from
  `profiles()`); Tasks 5 and 6 left raw non-ASCII literals in `HostsPanel`, `RemoteScope` and `HostsPanelTest`,
  rewritten as Java escapes in Task 7; Task 8 adds native item 15 (Sessions dropdown and `>ssh`) to
  `docs/remote.md`, names the ↻ "Detect shells again" button in item 13, and copies the WSL-group checks into
  `docs/windows-startup-verification.md`. Review fixes after Task 8: the WSL group's collapsed state is its own
  `wsl_collapsed` flag in `panel-state.toml` (no longer the `wsl` entry a host group of that name shared); `>ssh`
  matches distributions on names only and keeps up to half the result limit for hosts; `onProfilesChanged`'s
  Javadoc and docs say when it fires (first detection, then changed results; Custom edits may not be reported); the
  panel reads "Search sessions…" and, beside a WSL group with no hosts, "No SSH hosts yet — …". Remote tests
  283/54/6 (known six). Merged with main afterwards (entry above: SDK 0.9.1, Remote 0.6.0). Not pushed; not merged into main.
- **WSL shell profiles, plan A** (branch `worktree-wsl-hosts`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-wsl-a-profiles.md`, spec
  `docs/superpowers/specs/2026-09-28-jasper-wsl-shell-profiles-design.md`): Windows sessions run on ConPTY, so OSC
  titles and working-directory reports pass through. `terminal.shell.profile` chooses the shell new tabs start:
  PowerShell, PowerShell 7, Command Prompt, each detected WSL 1/WSL 2 distribution (registry, `wsl.exe -l -v`
  fallback, Docker's hidden), the login shell on macOS/Linux, or Custom (`program`/`args`, which an empty profile
  still selects when they are set). Settings › Terminal › Shell has a Profile dropdown; Program and Arguments apply
  to Custom only. WSL tabs run `wsl.exe -d <name> --cd <dir>` from the home folder, forward Jasper's variables
  through `WSLENV`, and report Windows paths (`SessionLaunchOptions.localPathMapper`). Splits start their pane's
  profile. An unavailable profile starts the platform default with a status-bar warning. Automatic shell
  integration does not reach WSL (manual line documented). `./gradlew check --continue` on Windows: 3458 tests,
  237 skipped, 8 failed, no failures outside the known list (re-verified and shortened in
  `docs/windows-startup-verification.md`: `ShellIntegrationSessionTest`/`ShellCommandTrackerTest` now pass, and
  `SessionLaunchCoordinatorTest` is enabled on Windows because ConPTY passes its OSC 2 readiness title);
  `ShellProfilesTest.anExecutionAliasCountsAsExistingThoughItCannotBeFollowed` is skipped on Windows (symlink
  privilege) and runs on macOS/Linux. macOS/Linux check pending with the user. Native acceptance checklist in
  `docs/windows-startup-verification.md`. Deviations from the plan: Task 10's `SchemaPage.showValue` changes a
  row's enablement only when its `enabledWhen` condition changes (a `shownEnabled` flag), so the line-height
  spinner stays disabled while "use the platform" is checked; review fixes after Task 10: a WSL launch in the
  Windows home passes `--cd ~`, `wsl:NAME` ids match detected distributions case-insensitively
  (`ShellProfiles.sameId`, also in the Settings dropdown), every spelling of `WSLENV` merges into one entry,
  `WslDistributions.detect` asks `wsl.exe -l -v` only when `reg.exe` gives no answer (exit 1, a missing Lxss key,
  means "no WSL"), PowerShell 7 detection uses `NOFOLLOW_LINKS` and also finds the Store alias under
  `%LOCALAPPDATA%\Microsoft\WindowsApps`, the `SessionLaunchOptions` javadoc says the path mapper compares by
  identity, and non-ASCII literals are Java escapes. Second review round after Task 11: a WSL/`pwsh` profile
  trusted before startup detection completes waits for it off the EDT (at most 12 s) and falls back to the platform
  default with a warning if missing, and detection completing reports a missing configured profile; only a pane
  nobody asked a directory for gets `--cd ~` (`Optional<Path>` through `ShellLauncher.launch`/`LaunchSettings.at`,
  `JasperApplication.newWindow()`), so a split of a WSL pane in `/mnt/c/Users/NAME` stays there;
  `sessionDefaults` reports no profile warning for SSH panes; the Settings dropdown keeps one "(not detected)"
  entry and ignores re-picking the shown entry; a supplied launcher skips detection; `WslDistributionsTest` runs
  `java.home`'s java. Plan B (Remote panel WSL group, SDK 0.9.1) is implemented on the same branch (entry above).
  Not merged.
- **Editor Vim mode, plan V3a** (branch `claude/vim-v3a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v3a.md`, written 2026-09-28): macros (`q`, `@`, `@@`, `{n}@`, `@:`, `@.`,
  `recording @q` in the Vim bar) and the mapping commands typed on the command line (`:map :nmap :vmap :xmap :omap
  :imap :cmap :map!`, their `noremap`, `unmap` and `mapclear` forms, and the listings), through a per-tab typeahead
  ported from Vim's getchar.c: recursion and E223 ('maxmapdepth'), 'timeout' and 'timeoutlen' on a Swing timer,
  `<nowait>`, `<unique>`, `<Plug>`, `<Nop>`, `<Leader>`; a failing command ends a mapping or macro, a mapping's changes
  undo as one step, and Ctrl+C stops one that runs on. Every file in the plan was compiled and run against Vim 9.1
  before the plan was written: 1714 corpus cases (133 new in `macros.case` and `mappings.case`, 2 divergent).
  - Executed 2026-09-28 (inline, one commit per task); every task's tests and re-recording matched the plan.
  - `./gradlew check`: 4,311 tests, 0 failed, 0 errors, 5 skipped.
  - Merged into local `main` on 2026-09-28 after merging main (plugin settings C3) into the branch; `./gradlew check`
    on the merged tree: 4,311 tests, 0 failures, 0 errors, 5 skipped. Not pushed.
  - Not done: GUI checks 71–79 of the [checklist](editor-verification.md) (user-run); plan V3b
    (the vimrc, `sethandler` lines, `let mapleader`, `<Action>`); V4; push.
- **Plugin settings C3** (`claude/plugin-settings-c3`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c3.md`): SDK 0.9.0. Editor, Shell History, Remote and
  Credential Vault declare their settings and get generated pages under Settings › Tools; Snippets declares none.
  `PluginConfig`, `PluginContext.config()`, the testkit's `setConfig` and `reports()`, and the application's
  `PluginSettings` view are gone; every bundled plugin requires `>=0.9.0, <0.10`. Remote's `[shortcuts]` table is
  gone: its actions keep Cmd+Shift+S and Cmd+Shift+H as default bindings, rebindable in Keymap, and old lines warn
  as unknown keys. The Editor's Vim toggle saves through its setting. History's empty list means no trivial
  commands. Values the plugins used to clamp now fall back to their defaults with a report. `./gradlew check`: 4,117 tests,
  0 failures, 5 skipped. GUI checks pending with the user: each plugin's page under Tools; the Editor's Vim toggle
  and its page's Vim mode; a Remote file with an old `[shortcuts]` table; Keymap rebinding Toggle SSH Hosts.
  Still deferred from C2:
  - a pending-changed callback for plugin controls
  - per-page dedupe of warnings from a throwing `modified()`/`keywords()`
  - the stub's Open in Editor failure going to a notice while Settings is modal
  - section-heading carry-over for unsectioned settings
  - the discard count per plugin page

  Deviations: see the plan's status banner. Merged into local `main` on 2026-09-28 after merging main (Vim V2b)
  into the branch, which needed `EditorWindow`'s `IOException` import back for V2b's new catch; `./gradlew check` on the
  merged tree: 4,153 tests, 0 failures, 0 errors, 5 skipped. Not pushed.
- **Editor Vim mode, plan V2b** (branch `claude/vim-v2b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2b.md`, written 2026-09-28): the Editor's tabs as Vim's buffers — `:w :wq :x :exi :sav :wa :xa :wqa :q :qa
  :quita :e :ene :r :ls :buffers :files :b :bn :bN :bp :tabn :tabN :tabp :tabnew :tabe`, `ZZ`, `ZQ`, `gt`, `gT`,
  through the Editor's save, conflict and close paths (`VimHost.Documents`, implemented by `EditorWindow`); `<Tab>`
  completion of commands, options, paths and tab names; 'number' and 'relativenumber' on the gutter; uppercase marks
  that select their tab. Every file in the plan was compiled and run before the plan was written: 1582 corpus
  cases against Vim 9.1 (17 new), and the tab commands in `ExFilesTest` and `EditorWindowTest`.
  - Executed 2026-09-28 (inline, one commit per task); every task's tests and re-recording matched the plan.
  - `./gradlew check`: 4,135 tests, 0 failed, 0 errors, 5 skipped.
  - Merged into local `main` on 2026-09-28 after merging main (plugin settings C2) into the branch; `./gradlew check`
    on the merged tree: 4,153 tests, 0 failures, 0 errors, 5 skipped. Not pushed.
  - Not done: GUI checks 61–70 of the [checklist](editor-verification.md) (user-run); plans V3 and V4; push.
- **Plugin settings C2** (`claude/plugin-settings-c2`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c2.md`): Settings has a page under Tools for each running
  plugin with settings, generated from its declarations (`PluginSettingsPage`) or built by the plugin through the new
  SDK `SettingsPanel` and `SettingsPanelHost` (`PluginPanelPage`); plugins that are enabled but not running get a stub
  with Open in Editor. Apply writes the plugin's own file. The Plugins page's Open Settings goes to a running plugin's
  page. The Sample plugin declares its settings and has its own page. Previews: `settingsPreview` renders 44 images.
  The preview did not render correctly on the first run: the final review found plugin-built pages stretched (the
  Sample page's slider about 430 px below its checkbox), fixed in the final fix wave and checked in the re-rendered
  preview. Final review fixes: F1 one page per plugin id (no stub for a superseded copy or a running plugin, no
  duplicate stubs); F2 a rejected plugin file blocks only its own page's edits, and plugin-built pages show the
  rejection message too; F3 plugin-built pages stay at the top and their standard rows keep their height; F4 closing a
  plugin-built page without a successful Apply calls `SettingsPanel.reset()` before `dispose()`; minors: staging the
  default of an unset key stages "unset", a failure without a message shows its type, `SettingsPanelHost.changed()`
  Javadoc, and docs. `./gradlew check`: 3,666 tests, 0 failures, 5 skipped. GUI checks pending with the user: change
  a plugin setting and Apply; the Sample page's slider; Open Settings from the Plugins page; a plugin in safe mode
  (stub page). Deferred to C3: a pending-changed callback for plugin controls; per-page dedupe of warnings from a
  throwing `modified()`/`keywords()`; contract cases for `page()`; the stub's Open in Editor failure going to a notice
  while Settings is modal; section-heading carry-over for unsectioned settings; the discard count per plugin page; the
  Sample `settings.toml` "reads this file live" wording. Deviations: see the plan's status banner. Merged into local `main` on 2026-09-28
  after merging main (Vim V2a) into the branch; `./gradlew check` on the merged tree: 4,117 tests, 0 failures,
  0 errors, 5 skipped. Not pushed.
- **Editor Vim mode, plan V2a** (branch `claude/vim-v2a`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v2a.md`, written 2026-09-28): the Ex command line — ranges (`;`, searches, marks, folds, `*`), `|`, Vim's 600
  command names for abbreviations (E319 for those Jasper lacks), `:s` with every flag including confirmation, `:&`,
  `:&&`, `:~`, `&`, `g&`, `:g`/`:g!`/`:v`, `:d :y :> :< :pu :j :m :t :co :norm :sort :left :right :center`,
  `:p :P :l :nu :# :=`, `:k :mark :delm :marks :jumps :changes :reg :di`, `:u :red :noh`, `:set`/`:setlocal`/
  `:setglobal` with do_set's syntax, the output panel (Vim's hit-enter prompt), questions that pause a command,
  'report' messages, 'hlsearch' on and 'gdefault'. The corpus compares output (`output: yes`). Every file in the plan
  was compiled and run against Vim 9.1 before the plan was written: 1565 corpus cases
  (1560 recorded, 5 divergent).
  - Executed 2026-09-28 (inline, one commit per task); every task's tests and re-recording matched the plan.
  - `./gradlew check`: 4,068 tests, 0 failed, 0 errors, 5 skipped.
  - Merged into local `main` on 2026-09-28 after merging main (plugin settings C1) into the branch; `./gradlew check`
    on the merged tree: 4,099 tests, 0 failures, 0 errors, 5 skipped. Not pushed.
  - Not done: GUI checks 51–60 of the [checklist](editor-verification.md) (user-run); plans V2b, V3 and V4; push.
- **Plugin settings C1** (`claude/plugin-settings`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-plugin-settings-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-plugin-settings-c1.md`): SDK `dev.jasper.sdk.settings` (declarations,
  kinds, typed handles) and `PluginContext.settings()`; the application reads each plugin's declared settings with its
  own schema (`SchemaReader`, moved out of `ConfigLoader`), reports bad values and unknown keys against the file,
  runs listeners on the UI thread and saves through a comment-preserving atomic write; the testkit fake and the
  contract suite cover the same behaviour. `PluginConfig` stays until C3; Settings pages come in C2.
  `./gradlew check`: 3,450 tests, 0 failures, 0 errors, 5 skipped. Executed inline with checkpoints after Tasks 3 and 5. Deviations: see the plan's status banner.
  Merged into local `main` on 2026-09-28 after merging main (Linux packages, Vim V1c) into the branch; `./gradlew check`
  on the merged tree: 3,648 tests, 0 failures, 0 errors, 5 skipped. Not pushed.

- **Editor Vim mode, plan V1c** (branch `claude/vim-v1c`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1c.md`, written 2026-09-28): the view — scrolling
  (`<C-e> <C-y> <C-d> <C-u> <C-f> <C-b>`, page keys, `zt z<CR> zz z. zb z- z+ z^`), `H M L`, the screen-line motions
  (`gj gk g0 g^ gm g$`), 'scrolloff', 'sidescroll' and 'sidescrolloff', and the fold commands on the Editor's code
  folds (`zo zO zc zC za zA zv zR zM zj zk [z ]z`) with closed folds counted as one line and 'foldopen'. A port of
  Vim's move.c (`View`) kept in step with the text area's viewport (`ViewportSync`). Every file in the plan was
  compiled and run against Vim 9.1 before the plan was written: 1,125 corpus cases (1,122 recorded, 3 divergent).
  - Executed 2026-09-28 (subagent-driven: three task commits plus the final review's fix commit).
  - Final review fixes: (1) while the command line reads a pattern, the view is not updated after each key;
    'incsearch' scrolls the view to the match from the view as it was when the line opened (the caret stays), and
    `<Esc>` or `<CR>` puts that view back before the usual update, as Vim's old_viewstate does; (2) `EditorTab.refresh`
    no longer turns word wrapping back on while Vim is on, and because RSyntaxTextArea always wraps at words,
    `VimTextArea` installs a wrapped view that honours `setWrapStyleWord(false)` and breaks at any character, so the
    screen lines are Vim's; `ViewportSync` counts the view's columns without the text area's side insets; (3) with Vim
    on, the text area is taller by the viewport's height less one line, so the view may scroll past the end of the
    text (`G` `zt`, `<C-f>` at the end, `<C-e>`) and the screen shows the view's first line; (4) `viewScrolled` keeps
    an enclosing command's busy flag and does not notify mid-command; (5) a viewport resize that changes the view's
    size scrolls the view to the cursor at once; (6) an unused local in `View.scrollCursorTop` went. Six
    `ViewportSyncTest` cases and one `EditorTabTest` case cover them.
  - `./gradlew check`: 3,599 tests, 0 failed, 0 errors, 5 skipped.
  - Merged into local `main` on 2026-09-28 after merging main (settings dialog B2) into the branch; not pushed.
  - Not done: GUI checks 41–50 of the [checklist](editor-verification.md) (user-run); plans V2, V3 and V4; push.

- **Settings dialog B2** (`claude/settings-dialog-b2`, 2026-09-28; plan
  `docs/superpowers/plans/2026-09-28-jasper-settings-dialog-b2.md`): Keymap and Plugins are pages of Settings.
  Keymap stages shortcut edits (Set) and saves them in one write on Apply; shortcuts and sections that differ from
  their defaults are blue. Plugins hosts the plugin list, with its consent and confirm dialogs over Settings. Help ›
  Keyboard Shortcuts… and File › Manage Plugins… open those pages; their old windows are gone. The View menu saves
  toolbar mode, the status bar and terminal colours; View › Window Settings… replaces the four size dialogs; the
  session overrides in `WorkspaceConfiguration` and `ThemeState` are removed. Previews: `settingsPreview` renders 36
  images, inspected against the IntelliJ reference. `./gradlew check`: 2,925 tests, 0 failures, 0 errors, 5 skipped.
  Executed inline with checkpoints after Tasks 3 and 5. GUI checks pending with the user: record a shortcut, Set,
  Apply and use it; the plugin restart banner and an install's consent dialog over Settings; View choices in two
  windows; a View choice with a broken config.toml; Window Settings… Deviations: see the plan's status banner.
  Merged into local `main` on 2026-09-28 after merging main (Vim V1b) into the branch; `./gradlew check` on the
  merged tree: 3,419 tests, 0 failures, 0 errors, 5 skipped. Not pushed.

- **Linux .deb and .rpm packages** (branch `claude/linux-packages`, 2026-09-28, no plan; the user chose native
  Linux builds plus CI over cross-building from macOS): on Linux, `:jasper-app:packageDist` runs `packageDeb` and
  `packageRpm` (jpackage from the verified app image, installed to `/opt/jasper` with a menu entry), for x64 and
  aarch64 hosts. `verifyPackage` gained the Linux image layout, the ELF machine of the launcher and bundled Java, and
  the icon. The `.rpm` requires libraries by soname, read with `readelf`, because jpackage's lookup needs an RPM
  database. Each package is unpacked and compared byte for byte with the image before it is published. The new
  `linux-packages` workflow builds on `ubuntu-22.04` and `ubuntu-22.04-arm`, then installs every package into
  Debian 12, Ubuntu 24.04, Fedora and openSUSE Tumbleweed containers (`packaging/linux/check-install.sh`). See
  [Linux packages](packaging.md#linux-packages). Locally: `./gradlew check` (3,410 tests, 0 failures, 5 skipped)
  and macOS `verifyPackage`. Pushed with the user's approval; the
  [fifth `linux-packages` run](https://github.com/an0nn30/Jasper/actions/runs/36449030992) passed all ten jobs:
  both packages on both architectures, and install/library/menu/removal checks on Debian 12, Ubuntu 24.04, Fedora
  and openSUSE Tumbleweed for x64 and aarch64 (`.deb` ~97 MB, `.rpm` ~119 MB). Earlier runs found and fixed
  jpackage replacing the menu icon with its generic Java icon (now supplied through `--resource-dir`) and the
  install-check's container setup. At the user's request the install scripts are now generated from the JBR's
  own templates with the menu registration made safe on headless systems (it creates the two directories
  `xdg-desktop-menu` needs and never fails the install or removal); the install check no longer prepares them.
  Open: the regular `ci` workflow is red for unrelated tests already failing on `main`; Linux
  desktop acceptance (checklist in `packaging.md`) is the user's. Final run
  [36450141658](https://github.com/an0nn30/Jasper/actions/runs/36450141658) green on all ten jobs; merged into local
  `main` on 2026-09-28 at the user's request (the branch is on origin; `main` not pushed).

- **Editor Vim mode, plan V1b** (branch `claude/vim-v1b`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1b.md`, written 2026-09-28): Visual mode (charwise,
  linewise, blockwise, with block `I`/`A`/`c`, `p` over an area, `gv` and `.` on an area of the same size), `/` and `?`
  with offsets and `;`, `n N * # g* g#`, the Vim regex translator (`dev.jasper.editor.vim.regex`), 'incsearch' and
  'hlsearch' highlights, marks, the jumplist and change list, `gi`, `<C-a>`/`<C-x>` (and `g<C-a>`), `gq`/`gw`,
  `<C-g>`, `ga`, blockwise registers and blockwise forced motions. Scrolling, the screen-line motions and folds moved
  to plan V1c (spec 13). Every file in the plan was compiled and run against Vim 9.1 before the plan was written: 946
  corpus cases (943 recorded, 3 divergent).
  - Executed inline on 2026-09-28 (five task commits plus one fix: a marks case typed four backticks, which broke
    `AppDocumentationTest`'s fence stripping inside the plan; it now types G, two backticks, 0, two backticks, with
    Vim's result unchanged). `./gradlew check`: 3,372 tests, 0 failed, 5 skipped.
  - Merged into local `main` on 2026-09-28 after merging main (settings dialog B1) into the branch; not pushed.
  - Not done: GUI checks 31–40 of the [checklist](editor-verification.md) (user-run); plans V1c, V2, V3 and V4; push.

- **Settings dialog B1** (`claude/settings-dialog`, 2026-09-28; spec
  `docs/superpowers/specs/2026-09-28-jasper-settings-dialog-design.md`, plan
  `docs/superpowers/plans/2026-09-28-jasper-settings-dialog-b1.md`): Cmd+, and the macOS Settings… item open
  an IntelliJ-style modal Settings dialog (`dev.jasper.app.settingsui`): search, category tree, breadcrumb with
  back/forward, schema-generated pages for all 33 core settings with Reset and inline validation,
  Apply/OK/Cancel with a discard prompt, and banners for old keys and rejected files. `ConfigService.State`
  carries the loaded values and a `rejected` flag; `AuxiliaryWindows.modalDialog` remembers the dialog's bounds.
  Keymap and Plugins are launcher pages until plan B2. Previews: `./gradlew :jasper-app:settingsPreview`
  (24 renders, dark/light, default and 18 pt), inspected against the user's IntelliJ reference; four layout
  defects found there were fixed. `./gradlew check`: 2,916 tests, 0 failures, 0 errors, 5 skipped. Executed
  inline with checkpoints after Tasks 4 and 7. GUI checks pending with the user (open via Cmd+, and the
  application menu, Apply a change, Cancel with changes, banners, remembered bounds, theme switch while open).
  Deviations: see the plan's status banner. Merged to local main (3cb249e5); not pushed.

- **Editor Vim mode, plan V1a** (branch `claude/vim-mode`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-vim-v1a.md`, spec
  `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, 2026-09-27): a headless Vim engine in
  `dev.jasper.editor.vim` on every tab's `VimTextArea`: Normal, Insert and Replace modes, counts, motions, text
  objects, operators, registers (with `"+` and `"*`), undo in Vim's steps, and `.`. The handler rule gives Vim only
  the keys it defines; ⌘ keys stay with the Editor. The Vim bar shows the mode, pending keys and messages. The
  `vim` setting, the Vim status item and Toggle Vim Mode switch it (the toggle writes the settings file).
  - Fidelity: 469 corpus cases in `plugins/editor/src/test/resources/vim/corpus`, recorded from Vim 9.1 by
    `./gradlew :jasper-plugin-editor:recordVimCorpus` (not part of `check`) and replayed by `VimCorpusTest`; every
    command in `VimCommands` has a case. Two `=` cases are divergent; `docs/vim-divergences.md` lists every
    difference and `docs/editor-vim-reference.md` is generated from the command table.
  - Deviations from the spec are in the plan's header and the spec's amended sections 3.8, 4.1 and 13.
  - Review fixes beyond the plan's code: the undo manager keeps Vim's grouping after a reload (discardAllEdits), an
    undo from outside Vim during an insert reopens the insert's undo step, and a failing Insert-mode key is not
    recorded (so a count or `.` does not replay it, and `<Esc>` always leaves Insert mode); three corpus cases cover
    the last point.
  - Final review fixes: (1) outside Insert and Replace mode a key Vim declines never runs one of RSyntaxTextArea's
    editing actions (only Editor commands, registered with `VimTextArea.allowInNormalMode`, run from the text area's
    own map) and rings the bell, while ancestor and window bindings (the app's shortcuts, the hosted Close Tab)
    still fire; (2) a
    failing Insert-mode key rings the bell and the insert goes on, so `.` still ends in Normal mode; (3) no redo
    while an insert's undo step has edits, and undo/redo reopen the step in a `finally`; (4) `SettingsFile` keeps
    the file's own line breaks, and the save-failure dialog names the exception; (5) `"+`/`"*` text has `\r\n` and
    `\r` turned into `\n`; (6) `Buffer` reads the document's line elements instead of re-splitting the text after
    every edit; (7) the deferred caret clamp checks the editor is still attached, and committed IME text is dropped
    outside Insert mode. Two corpus cases cover (2).
  - `./gradlew check` after merging `main` (settings foundation): 2,878 tests, 2,873 passed, 0 failed, 5 skipped. One earlier run failed `TerminalAppIntegrationTest`'s "reflow" row-reset search case once; it passed in three isolated reruns and in the full rerun (no terminal code changed on this branch).
  - Merged into local `main` on 2026-09-28; not pushed.
  - Not done: GUI checks 21–30 of the [checklist](editor-verification.md) (user-run); plans V1b, V2, V3 and V4; push.
- **Settings foundation** (`claude/settings-foundation`, 2026-09-27; spec
  `docs/superpowers/specs/2026-09-27-jasper-settings-foundation-design.md`, plan
  `docs/superpowers/plans/2026-09-27-jasper-settings-foundation.md`): `config.toml` is described by one
  schema (`dev.jasper.app.settings`, `config.CoreSettings`); the loader, the generated template and the
  example/guide tests read it. Version 2 keys: `[terminal.font]`, `terminal.colors`,
  `system.run_in_background`; version 1 keys load with "Moved to" warnings. `TomlDocument` edits TOML in
  place, keeping comments, and refuses any edit that would not land exactly; `ConfigService.save` and
  shortcut saves share one verified transaction. Migrate Config (palette and diagnostics button) rewrites
  old keys after backing the file up to `config.toml.v1.bak`. Plugin settings problems name the plugin's
  own file and line, survive app reloads and clear when that file reloads; the settings poller no longer
  reports from its own thread. `./gradlew check`: 2,336 tests, 0 failures, 0 errors, 5 skipped. Executed
  inline (user choice) with checkpoints after Tasks 4, 7 and 12; no GUI launched. Deviations: see the
  plan's status banner. Next: sub-project B (Settings dialog), then C (SDK settings API). Not pushed; not merged.

- **Windows tray icon and installer build script** (`codex/windows-build-script-and-resident`,
  2026-09-27): user-approved amendment to the background-residency spec. A resident Windows
  process shows a notification-area icon with Open Jasper (also double-click) and Quit Jasper;
  Quit is the normal quit path with plugin guards. `platform.ResidentTray` is a no-op when
  headless, outside Windows or when unsupported; `JasperApplication.residentIndicator` injects it.
  Removed when residency ends or at shutdown. `packaging\windows\Build-Installer.ps1` finds an x64
  JBR SDK 25 and WiX 3 and builds the setup EXE (or ZIP) on Windows ARM64; verified building
  the installer and rejecting an ARM64 SDK. On Windows: `ResidentIndicatorTest` (6),
  `ResidentTrayTest` (3), `ApplicationBootstrapTest` (13, one existing skip) and
  `verifyApplicationArchitecture` pass. `JasperApplicationResidencyTest` stays disabled on
  Windows (no recorded reason). Full `check` not run. Manual, user-run: the icon appears
  when resident, Open/double-click reveals a window, Quit exits, no icon when not resident.
  Not merged or pushed.
- **App-hosted Editor tabs** (`codex/plugin-workspace-tabs`, 2026-09-27):
  Editor files now use the main workspace's tab strip, rounded content frame, commands,
  status and breadcrumbs. The tab menu moves live content to/from a separate app-owned
  workspace, preserving undo and unsaved edits. SDK 0.8.5 supplies hosted content handles,
  asynchronous close guards and semantic editor palettes; Editor 0.2.0 and Remote 0.4.1
  require it. SFTP passes the originating workspace. Native inline implementation follows
  the approved hosted-workspace-tabs spec/plan. Rulings: reuse the whole WindowContent for
  shell-free detached windows; use stable session keys across Save As; defer individual
  close/move commands during batch approval. Independent review identified status actions
  targeting another host's selected document and new documents bypassing an in-progress
  native close; both have RED→GREEN regressions. Final `check :jasper-app:packageDist`
  passed: 2,281 tests, zero failures/errors, five skips; app image verified and DMG rebuilt.
  Dark/light and narrow/overflow headless previews inspected. No native GUI launched.
  Merged into main and pushed with the Windows setup wizard. No remaining confirmed review
  findings. The full workspace is shared instead of extracting another host; model/SDK and
  workspace wiring were developed together before separate commits to verify ownership.
- **Windows setup wizard** (branch `codex/windows-setup-wizard`, 2026-09-27): approved
  bounded packaging extension adds `:jasper-app:packageInstaller`, producing
  `Jasper-<version>-windows-x64-setup.exe` alongside the existing ZIP. Uses the verified
  JBR image, per-user installation, directory/shortcut dialogs, a stable upgrade UUID,
  and standard Windows uninstall. User data outside the installation folder is preserved.
  Built successfully with JBR SDK 25 x64 and WiX 3.14.1 on Windows ARM64 via emulation.
  MSI metadata verification passes; negative checks reject wrong version/upgrade UUID
  and machine-wide scope. The EXE's x64 architecture and all 20 official icon frames
  were verified. Installer + ZIP builds and targeted packaged-resource/terminal tests
  passed (12 tests: six passed, six Unix-only skips). Independent review found no issues.
  Updated from `origin/main` at `90c2e574` in merge `7ce646f9`: preserved both status
  entries at the sole conflict and applied main's LF checkout policy to existing files.
  Rebuilt and verified the setup EXE and ZIP; packaged assets now have the pinned LF bytes.
  Targeted resource, terminal integration, and startup verification passed (42 tests:
  34 passed, eight skipped). Full `check --continue` also ran: seven tests require this
  account to have Windows symlink privileges (listed below), and one reflow-search timeout
  passed when its full test class was rerun. Initial checksum failures disappeared after
  LF normalization. This is not a green full-suite claim. Remaining privilege failures:
  `ShellIntegrationScriptsTest.installReplacesASymlinkRatherThanWritingThroughIt`,
  `FileEndpointTest.localContract`, `FileEndpointTest.rejectsIntermediateLinksWithoutTouchingTheirReferents`,
  `SftpEndpointTest.sftpV3ContractAndSubsystemCloseKeepsSharedSessionAlive`,
  `TransferCoordinatorTest.folderTransferPreservesEmptyDirectoriesLinksAndUsesBoundedQueue`,
  `KnownHostsTest.ownDanglingSymlinkIsNotAnEmptyTrustFile`, and
  `FileOperationTest.recursiveDeleteNeverFollowsLinksAndTransferReservationsBlockIt`.
  Refreshed again from `origin/main` at `da81da80` in merge `d0c0551e`. Restored 14
  manifest-pinned `build/` icons omitted by the global build-directory ignore rule;
  a source-only exception now keeps them tracked. Replaced the startup-focus test's
  Unix shell fixture with an idle attached terminal session. Focused resource, icon,
  startup, tab and terminal tests pass: 38 tests, 32 passed, six Unix-only skips.
  Setup EXE and ZIP rebuilt successfully; MSI verification and all 14 restored asset
  hashes in the packaged JAR pass. Independent review found no issues. Full `check`
  was not rerun for this refresh; the earlier privilege limitations above still apply.
  Resolved the subsequent pull of `origin/main` at `39b8a7e3`: kept both status
  histories and the shared source-icon ignore exception. Retained Windows startup-focus
  coverage because the branch's attached-session fixture needs no Unix shell.
  Nine focused resource/icon/focus tests passed with no skips; Windows setup EXE and
  ZIP rebuilt and package/MSI verification passed. Full `check` was not rerun.
  Setup remains unsigned. Interactive install/upgrade/uninstall acceptance is
  user-run; no installer or GUI was launched. [Build and acceptance](packaging.md#windows-setup-wizard).
  Updated from local main at `8883f670` (hosted Editor tabs); sole conflict was this
  file, both entries kept. Merged into main and pushed to `origin` on 2026-09-27.

- **Local/remote integration and missing icon assets** (2026-09-27): merged
  `origin/main` at `2d59d455` with the local keyboard-panel changes, retaining both
  sets of status notes. Integration checks exposed 14 missing vendored SVGs under
  `icons/intellij/build/`: the broad `build/` ignore rule had excluded that source
  category from Git. Restored the exact files from the UI worktree, verified every
  SHA-256 against `assets.tsv`, and added a source-only ignore exception.
  Full `./gradlew check` passed: 2,266 tests, zero failures/errors, five skips.

- **Thin shortcut disclosure chevrons** (`codex/keyboard-shortcuts-ui`, 2026-09-27):
  the shortcut tree now uses outlined chevrons instead of filled triangles: right when
  collapsed and down when expanded. Only the arrow shape is overridden; the active
  theme still supplies its colors. Existing shortcut tests passed and dark/light
  headless previews were inspected.

- **Expandable keyboard shortcut sections** (`codex/keyboard-shortcuts-ui`, 2026-09-27):
  replaced the flat four-column table with collapsible section/plugin groups, vendored
  folder icons and right-aligned bindings. Panel, tree, viewport and editor share the
  active theme's panel surface; selected rows retain the theme's rounded treatment.
  Search temporarily opens matching sections and restores manual disclosure state when
  cleared. Live refresh preserves selection by action ID; Enter edits actions or toggles
  groups, and expand/collapse-all controls support browsing. Context remains in tooltips
  and accessible descriptions; recording, conflict resolution and read-only keys retain
  their existing behavior. Headless previews cover dark/light and larger UI fonts.
  Regression checks cover disclosure, search, refresh, rendering and full-width row
  clicks; independent review findings are fixed and the final review is clean.
  `check :jasper-app:packageDist` passed: 2,072 tests, zero failures/errors, five skips;
  package verification passed and the DMG was rebuilt. An initial unrelated asynchronous
  terminal-search timeout passed in isolation and in the final full suite. No GUI launched.

- **Missing `build/` icons** (branch `claude/fix-build-icons`, 2026-09-27): the New UI catalog lists 7 icons
  (14 SVGs) under `icons/intellij/build/`, which the root `.gitignore`'s `build/` rule kept out of Git, so
  `IntellijIconsTest` and `HostedUiTest` failed on every clean checkout (CI red since `6bd1132d`). The 14 files
  were fetched from `intellij-community` at the pinned commit and match `assets.tsv`'s SHA-256; `.gitignore`
  now excepts that folder. `./gradlew check`: 2,262 tests, 2,257 passed, 0 failed, 5 skipped.

- **Editor work merged with `origin/main`** (branch `claude/sync-origin`, 2026-09-27): local `main` (Editor
  plans A and B) and `origin/main` (28 commits: New UI icon catalog as SDK 0.8.2, remote-directory hints as
  SDK 0.8.3, workspace chrome, shortcut editor) had both numbered new SDK APIs 0.8.2. The Editor's additions
  (`PluginWindow.onCloseCommand`, quit guards, `Windows.chooseSaveFile`) are now SDK **0.8.4**; the Editor
  declares `sdk = ">=0.8.4, <0.9"`, Remote 0.4.0 `>=0.8.3`. Conflicts: Remote's manifest and `RemotePlugin`
  (both sides kept), SDK version tests, SDK docs and this file.

- **Editor plugin, plan B** (branch `claude/editor-plugin-b`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-plugin-b.md`): Remote 0.4.0 optionally requires the Editor.
  - Double-click or Enter in the SFTP browser opens a file, or a link to a file, in the Editor; **Open in Editor** opens up to 20 selected files.
  - Saving follows the write contract over SFTP (`dev.jasper.remote.edit.SafeWrite`):
    - an owner-only sibling created with its mode through a hand-built `SSH_FXP_OPEN`;
    - posix-rename, and writing in place otherwise;
    - read-only files refused.
  - Editor operations run one at a time per connection on one SFTP channel (`EditChannels`).
  - The spec's sections 4.4, 6.1, 6.2 and 6.4 carry the Plan B amendments.
  - Deviations from the task text, from the task reviews: `SafeWrite` falls back to writing in place when the target's permissions are unknown (−1) or when the sibling create is refused with `AccessDeniedException` (other create failures propagate); `EditChannels` settles a channel before failing its queued jobs, so work submitted from a failure callback is not lost; `plugins/remote/build.gradle.kts`'s first two comment lines were replaced, since the plan's replacement already carried the second line's text; a late link lookup in the SFTP panel navigates or reports only while the panel still shows the same host and folder, and Open in Editor still opens the selection's regular files when a link fails to resolve, naming the reason; Step 4's `docs/README.md` instruction was rephrased so no inline code in the plan holds Markdown link syntax, which `AppDocumentationTest` misread as a broken link from `docs/superpowers/plans/`.
  - Final review fix wave: a failed save over SFTP no longer leaves its hidden sibling (`SafeWrite` marks one it could not remove with `SafeWrite.SiblingLeft`, and `RemoteDocument` queues a never-prompting removal), a sibling created readable by others is narrowed to `0600` before any byte, and the review's minor text, test and documentation items are in.
  - Follow-up (final review): a save costs about 55 sequential SFTP round trips (every endpoint call re-checks each ancestor folder; two channel opens per save; content uploaded twice without posix-rename) — measure with checklist item 20, then consider resolved-path calls without the ancestor walk, checking posix-rename up front, and a short channel linger (a §6.3 amendment).
  - `./gradlew check` (2026-09-27): **BUILD SUCCESSFUL**. Totals across `*/build/test-results/test/` and `plugins/*/build/test-results/test/`: 2,196 tests, 2,191 passed, 0 failed, 5 skipped, 0 errors.
  - Not done: GUI acceptance (items 12–20 of the [checklist](editor-verification.md), user-run) and push. Merged into `main` locally on 2026-09-27.

- **Editor plugin, plan A** (branch `claude/editor-plugin`, plan
  `docs/superpowers/plans/2026-09-27-jasper-editor-plugin-a.md`, spec
  `docs/superpowers/specs/2026-09-27-jasper-editor-plugin-design.md`): SDK 0.8.4 (numbered 0.8.2 on the
  branch; renumbered when merged with `origin/main`, whose 0.8.2 and 0.8.3 are the New UI icon catalog and
  remote-directory hints) adds
  `PluginWindow.onCloseCommand`, quit and restart asking plugin windows' closing guards, and
  `Windows.chooseSaveFile`. The bundled Editor (`plugins/editor`, RSyntaxTextArea 3.6.0) opens, edits
  and safely saves local files in tabs in one window, with a TOML lexer, conflict checks and outside-change
  polling. Planning decisions and deviations are in the plan's header. Not done: plan B (SFTP editing
  through Remote), GUI acceptance ([checklist](editor-verification.md), user-run) and push. Merged into
  `main` locally on 2026-09-27 after `origin/main`'s three-platform CI work.

- **Terminal-first startup focus** (`codex/intellij-new-icons`, 2026-09-27):
  removed the SSH panel's deferred focus request on every hierarchy showing event,
  which raced the window's startup terminal focus. Explicit panel visibility callbacks
  request host-list focus only when already mounted in a showing window; restoring a
  panel before window display only selects its first host. The native frame now uses
  the workspace's active-terminal default focus policy. A pending shell has a focusable
  pane placeholder, replaced by the terminal view when ready; the placeholder then
  leaves keyboard traversal. The existing palette launch-focus guard remains intact.
  Startup regression failed before the fix and now covers pending/ready terminals,
  active-tab changes and disposal with a visible tool-panel field. Focused palette and
  remote tests passed; independent review is clean. `check :jasper-app:packageDist`
  passed: 2,068 tests, zero failures/errors, five skips; package verification passed and
  DMG rebuilt. Native GUI focus checks remain manual; no GUI launched.

- **Scrollable workspace tabs with edge fades** (`codex/intellij-new-icons`, 2026-09-27):
  replaced whole-tab wheel jumps with precise distance scrolling in a clipped viewport.
  Tabs retain their widths and can be partially visible at either edge; wheel/trackpad
  gestures over labels, close controls and the strip work without changing selection.
  A 16px fade uses the active `ToolWindow.background` only where content overflows;
  the tab-list control stays fixed outside the viewport. Selecting a tab (including
  the current tab through the list), resizing and growing visible titles reveal it.
  Child repaint origins preserve fades on hover. Drag drops outside the viewport are
  ignored, including over the dropdown; scrolled reorder/close keep the same model.
  Regression tests reproduced fractional-input and clipped-drag failures before fixes;
  focused tests and dark/light headless previews pass. Independent review finding fixed
  and re-reviewed clean. `check :jasper-app:packageDist` passed: 2,067 tests, zero
  failures/errors, five skips; package verification passed and DMG rebuilt. No GUI launched.

- **Title-bar vertical alignment** (`codex/intellij-new-icons`, 2026-09-27):
  removed the redundant 6px workspace top gutter beneath a visible toolbar/native
  header. Controls were already centered within the row; the extra chrome below made
  them appear too high. Terminal and side-panel surfaces now begin at the header edge.
  Hidden macOS actions retain the visible context row; hiding an ordinary toolbar
  restores the top gutter. Panel parents and splitter sizes are preserved.
  Regression tests reproduced the 6px error before the fix and now cover dark/light,
  28/30/64px headers, side panels and toolbar visibility. Headless previews inspected;
  independent review is clean. `check :jasper-app:packageDist` passed: 2,063 tests,
  zero failures/errors, five skips; package verification passed and DMG rebuilt.
  No GUI launched.

- **Command palette scope spacing** (`codex/intellij-new-icons`, 2026-09-27):
  inset the scope strip by 4 logical pixels on all sides so selected scope highlights
  clear the rounded palette frame. Scope click targets remain 30px high and preferred
  palette height includes the extra padding. Existing active-theme colors are retained.
  Geometry/pixel regression failed before the change and passes afterward; dark/light
  headless previews inspected and independent review is clean.
  `check :jasper-app:packageDist` passed: 2,061 tests, zero failures/errors,
  five skips; package verification passed and DMG rebuilt. No GUI launched.

- **SSH group folder icons** (`codex/intellij-new-icons`, 2026-09-27):
  replaced the text disclosure triangles with vendored `nodes/folder` for collapsed
  groups and `general/open` for expanded groups. Remote supplies icons through the SDK
  appearance catalog, retaining light/dark variants. Search-revealed groups show the open
  icon without changing saved collapse state; group labels, counts and actions remain.
  Regression failed before the change and now verifies both states plus search reveal.
  Independent review is clean. `check :jasper-app:packageDist` passed: 2,061 tests,
  zero failures/errors, five skips; package verification passed and DMG rebuilt.
  No GUI launched.

- **Terminal corner repaint repair** (`codex/intellij-new-icons`, 2026-09-27):
  all four outer terminal corners retain the shared 8px radius. The top corners could
  appear square because Swing skipped the rounded host background for dirty rectangles
  entirely covered by the opaque tab/terminal layout container. Made that layout-only
  container non-opaque so the host always paints behind clipped descendants. Actual
  terminal/tab surfaces, geometry and theme colors are unchanged. A partial-paint
  regression failed before the fix and now verifies all four corners in dark/light;
  full headless previews inspected and independent review is clean.
  `check :jasper-app:packageDist` passed: 2,060 tests, zero failures/errors, five skips.
  Package verification passed and the DMG was rebuilt. No GUI launched.

- **Command-aware header and short tabs** (`codex/intellij-new-icons`, 2026-09-27):
  the user approved full `user@path : command` headers and short command/program tab
  labels. SSH headers use saved host identity and remote path instead of local metadata.
  OSC program titles override the complete automatic header/tab wording; manually named
  tabs still win. Display overrides reset on command boundaries and reconnects, while
  notification-title tracking is preserved. Native window metadata follows the header.
  The title reserves 280–480px when available, with 24px before toolbar controls; narrow
  windows retain clipping, full literal tooltips and action overflow. Headless dark/light
  previews inspected. Test-first checks cover command text, foreground fallback, OSC and
  tmux titles, SSH paths, pane/tab focus, spacing and literal HTML-looking tooltips.
  Independent review completed and its tooltip finding fixed. `check :jasper-app:packageDist`
  passed: 2,059 tests, zero failures/errors, five skips. Package verification passed and
  the DMG was rebuilt. No GUI launched.

- **UI branch refreshed from main** (`codex/intellij-new-icons`, 2026-09-27):
  merged `origin/main` at `617ad248`, bringing in the editable keyboard-shortcuts
  window (no new plugin in the incoming commits). Preserved both status histories
  when resolving the only merge conflict. The editor inherits the branch's rounded
  inputs and active theme; replaced its square etched frame with an 8px rounded,
  theme-colored outline that refreshes on theme changes. Existing shortcut behavior
  is unchanged. Dark/light headless previews at default and 18-point typography
  inspected; independent integration review completed. `check :jasper-app:packageDist`
  passed: 2,059 tests, zero failures/errors, five skips. Package signature/payload
  verification passed and the DMG was rebuilt. No GUI launched or branch pushed.

- **Centered status-bar spacing** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  removed the extra 6px exterior workspace gutter above a visible status bar so its
  icons and text sit evenly between the workspace surfaces and window bottom. Hiding
  the bar restores the gutter; plugin parents, panel sizes and the terminal's rounded
  lower inset are preserved. Regression reproduced the offset before the fix and
  verifies icon geometry in dark/light themes at 20/24/48px bar heights, panel edges,
  and visibility changes. Headless previews inspected; independent review is clean.
  `check :jasper-app:packageDist` passed: 2,044 tests, zero failures/errors, five skips.
  Rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Centered rail spacing** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  rail icons were centered within their controls, but an extra 6px workspace gutter
  made their right-side spacing wider. Visible rails now meet adjacent panel/terminal
  surfaces without that duplicate gutter; hidden or empty rails restore the exterior
  gutter. Borders update in place, retaining plugin parents, panel sizes and divider
  positions. Regression first reproduced a 34px visual column for a 28px rail, then
  verified even icon spacing at 28/36/64px and aligned terminal/bottom-panel edges.
  Headless preview inspected; independent review is clean. `check :jasper-app:packageDist`
  passed: 2,043 tests, zero failures/errors, five skips. Rebuilt DMG and package
  signature/payload verification passed. No GUI launched.

- **Rounded terminal bottom corners** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  the terminal/tab frame now rounds all four corners with the same 8px radius as tool
  panels. A radius-sized lower inset keeps terminal cells and the cursor outside the
  clipping curve; it continues the resolved terminal background, including independent
  terminal-color changes. Workspace minimums include the inset, while panel gutters,
  dividers and saved panel sizes retain their behavior. Headless dark/light previews
  inspected; regression coverage checks both lower corners, an intact last grid row,
  palette changes and dynamic minimums. Updated layout expectations for the 8px inset.
  Independent review is clean. An initial full run lost generated build output mid-test;
  clean test recompilation and `check :jasper-app:packageDist` then passed: 2,042 tests,
  zero failures/errors, five skips. Rebuilt DMG and package signature/payload verification
  passed. No GUI launched.

- **Rounded selected workspace tabs** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  replaced the terminal-colored tab and underline with an inset 4px rounded selection
  and 1px outline, matching the user's IntelliJ reference. Fill, text, border and icon
  variants follow the active UI theme independently of terminal colors. Jasper Dark's
  muted blue fill and border live in the scheme JSON; other themes derive outline
  defaults from their existing active/inactive tab accent keys, preserving explicit
  overrides. Hover uses the same rounded geometry; layout and hit targets are unchanged.
  Failing-before/passing-after coverage includes rounded corners, insets, active/inactive
  outline, live themes and independent terminal palettes. Dark/light headless previews
  inspected; independent review is clean. `check :jasper-app:packageDist` passed:
  2,041 tests, zero failures/errors, five skips; rebuilt DMG and package signature/payload
  verification passed. No GUI launched.

- **Configurable unified title-bar height** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  `window.toolbar_height` and View → Toolbar height now size the entire macOS header,
  removing its fixed 42px floor. Default is 30px, with bounds of 28–64 logical pixels;
  older values below 28 clamp with a config warning. Native JBR geometry and window
  minimums update live, even with hidden toolbar controls. Standalone dialog titles
  remain 28px. Existing height, config, dialog and full-window geometry tests reproduced
  the old floor, then passed with the fix. Updated the separate tab-height test
  to expect the new default too. Headless workspace preview inspected; independent
  review found no issues.
  `check :jasper-app:packageDist` passed: 2,041 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Local and SSH status breadcrumbs** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  status paths now render as theme-aware folder/host segments with chevrons. Narrow
  windows collapse ancestors while retaining the current folder; tooltips and accessible
  text keep the full path. Remote hosts appear once, and an unknown remote path never
  falls back to the local home. Remote reuses SFTP's read-only folder follower for the
  active SSH pane, including when SFTP is closed, respecting host follow settings and
  existing OS support. SDK 0.8.3 adds an additive provider-directory hint: display only,
  actual OSC 7 wins, reconnect clears it, and stale/closed attempts cannot overwrite it.
  Headless local/remote/narrow light/dark previews inspected. Independent review caught
  a Windows-invalid display fixture; production now uses typed snapshots only. A
  failing large-font test led to content-aware status height; re-review is clean.
  `check :jasper-app:packageDist` passed: 2,041 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Fluid workspace surfaces** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  removed rail, workspace-header, toolbar, tab-strip and status-bar boundary rules;
  selected-tab underlines remain. The terminal/tab column now has rounded upper
  shoulders alongside the rounded plugin panel; its bottom stays rectangular to
  preserve terminal cells. Shared chrome forms 6px exterior gutters and plain 6px
  draggable panel gaps, with no divider stroke or grip. Internal terminal split
  indicators remain unchanged. Tab-strip and frame colors read the active theme.
  Independent review caught a center-frame minimum-size regression; a failing-before
  test reproduced it and the center now inherits the terminal's dynamic minimum.
  Headless light/dark workspace previews inspected; re-review is clean. Updated old
  edge-to-edge size and border assertions for the new gutters and continuous surfaces.
  `check :jasper-app:packageDist` passed: 2,035 tests, zero failures/errors, five skips;
  rebuilt DMG and signature/payload verification passed. No GUI launched.

- **Tool-panel contrast and original rail icons** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  removed the rail's tab-accent selection override and alternate selected-icon variant;
  rail buttons now retain their original theme-aware icons and the active theme's
  toolbar selection color. Jasper Dark defines `toolWindowBackground` (`#191c20`) for
  rounded tool panels and matching list/tree/table content, giving stronger contrast
  against the chrome. All color values live in the scheme; UI code reads active theme
  keys. Existing light-theme colors and explicit plugin overrides remain intact.
  Headless dark/light previews inspected. Independent review caught one stale color
  assertion, now updated. All checks pass: 2,033 tests, zero failures/errors, five skips.
  Packaging retried successfully after a transient `.DS_Store` cleanup conflict;
  rebuilt DMG and package signature/payload verification pass. No GUI launched.

- **Rounded selections and highlights** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  shared theme defaults give list/tree/table, combo popup and menu selections a 4px
  radius and horizontal inset. Standard list/table padding keeps content inside the
  highlight. `AppListUI` extends FlatLaf's selection painting to composite plugin rows
  (including SSH hosts and Vault), preserving renderer layout and custom colors.
  Palette selected/hovered rows and scope highlights share the geometry. Light/dark
  headless previews inspected; regression coverage includes live theme changes,
  composite content, panel colors and palette hover. Independent review found no issues.
  `check :jasper-app:packageDist` passed: 2,033 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Window-centered command palette** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  the screenshot showed the palette shifted into the terminal column when a tool panel
  was open. Placement now uses the whole workspace's window bounds, including panels
  and rail, for centering, available width and the stable top anchor. Native header
  controls remain outside the placement area. A failing-before/passing-after regression
  covers asymmetric panels, divider sizes, window resizing and both native-header modes;
  existing scope-height and small-window tests pass. Independent review found no issues.
  `check :jasper-app:packageDist` passed: 2,032 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Rounded plugin tool panels** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  shared left/right/bottom region hosts provide 8px rounded surfaces and 6px chrome
  gutters. Plugin content clips to the outline, including partial repaints, while
  explicit plugin colors stay intact. Saved sizes still measure the outer region;
  hiding, replacing and moving panels retain the existing lifecycle. Selected rail
  controls use the theme accent and matching host icon variants. Dark/light headless
  previews and all three region positions checked. A live-theme render test exposed
  removal of the gutter border; an app-owned border wrapper preserves it across LAF
  changes, with a passing regression. Independent re-review is clean.
  `check :jasper-app:packageDist` passed: 2,031 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Softer, roomier inputs** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  text, password and formatted fields use 4px corner radii and 5px vertical / 8px
  horizontal padding through shared theme defaults, including plugin forms. Removed
  conflicting dark-only geometry; existing font sizes and theme colors are retained.
  Palette search/form rows grow by 4px to accommodate the larger controls. Dark/light
  headless input previews inspected; independent review found no issues.
  `check :jasper-app:packageDist` passed: 2,030 tests, zero failures/errors, five skips;
  rebuilt DMG and package signature/payload verification passed. No GUI launched.

- **Rounded command palette** (follow-up on `codex/intellij-new-icons`, 2026-09-27):
  matched the user's IntelliJ reference with 8px outer corner radii and the existing
  theme border. Header/footer children clip within that outline, including partial
  repaints; transparent corners use the corresponding rounded hit area. Insets,
  keyboard behavior and placement are unchanged. Dark/light headless renders inspected;
  independent review found no issues. `check :jasper-app:packageDist` passed: 2,030 tests,
  zero failures/errors, five skips. Rebuilt DMG and package signature/payload checks pass.
  No GUI launched.

- **Unified workspace header and graphite chrome** (branch `codex/intellij-new-icons`, 2026-09-27):
  continued in the user-selected icon worktree. macOS now combines native controls,
  focused directory/host context and toolbar in a 42px default header, with tabs below.
  Program titles still reach native window metadata; the visible context follows the
  focused pane, retaining a provider's saved host/session label before remote OSC 7 arrives.
  Whole toolbar controls move into a live ellipsis menu by stable priority, retaining
  actions, shortcuts, toggles and dropdown grouping. Settings has one header home.
  Jasper Dark uses graphite surfaces, silver foregrounds, a faint header highlight and
  ice-blue tab accents. Selected tabs match the resolved terminal palette; authored host
  icon variants and foregrounds remain readable with independent terminal colors.
  Upstream icon files are unchanged. The optional angled tab shoulder is deferred.
  The two notification fixes were carried forward as `a824e2a4` and `8deb2293`.
  Inline implementation follows the [approved design](superpowers/specs/2026-09-27-jasper-unified-header-design.md).
  Independent review caught mixed-palette tab contrast; regressions reproduced it, the
  fix passes, and re-review is clean. `./gradlew check :jasper-app:packageDist
  :jasper-app:mockUiPreview` passed: **2,030 tests, zero failures/errors, five skips**,
  plus native notification/packaging checks. The mounted DMG contains the current app
  JAR and passes strict deep signature verification. Headless local/remote, narrow/wide,
  dark/light previews inspected; Java source hygiene and whitespace checks pass.
  Native dragging, traffic lights, fullscreen and final presentation remain user-run
  acceptance; no GUI launched. Work retained on this branch, not merged or pushed.

- **IntelliJ New UI icons** (branch `codex/intellij-new-icons`, 2026-09-27): isolated in
  `/Users/dustin/.codex/worktrees/intellij-new-icons/moray`. Vendored the complete platform
  and Terminal `expui` collections at `60b8fbadc2f08ef18a6ecab0c138a849e14f13fb`: 1,744 SVGs,
  885 catalog choices, upstream bytes, licenses/notices and SHA-256 provenance retained.
  AppIcons/NamedIcons own artwork, caching, light/dark selection and proportional 16px sizing.
  SDK 0.8.2 exposes `Appearance.icon(BundledIcon)` and adds 13 semantic names (47 total);
  Vault and Remote now require 0.8.2 and use host icons instead of private painters.
  Title bar/layout and application branding are unchanged. The sample's custom-SVG example
  remains supported. Deviation: KEY retains the central Tabler key because this upstream set
  has no key symbol; artwork/catalog/SDK changes form one integration commit.
  Independent review found and fixed an Escape binding regression and a differing-dimensions
  light/dark fitting issue, both with failing-before/passing-after tests; follow-up review clean.
  `./gradlew check :jasper-app:installDist` passed: 2,009 tests, zero failures/errors, five skips.
  Distribution membership, all asset hashes and Java source hygiene verified. Headless comparison:
  `python3 tools/icons/render-preview.py`. [Details](new-ui-icons-verification.md).
  Native visual acceptance is user-run; not merged or pushed.
- **Completed notification command** (follow-up on `codex/mac-notification-icon`, 2026-09-27):
  the user reported `sleep 12` completing as `~`. Completion reused the live terminal
  title, which can contain a shell's directory. `CommandNotifier.finished` now uses
  the captured command from the completion event, including arguments, for both the
  native notice and completed Buddy card. Running-title updates remain live. Unit
  regressions and a headless real-PTY burst reproduce the old `~` result and pass
  with `sleep 12`; failure wording and multiline commands are covered too.
  This supersedes the earlier final-program-title rule. `check` passed: 2,008 Java
  tests, zero failures/errors, five skips, plus the native harness. `packageDist`
  passed; the mounted DMG contains the current app JAR and passes strict deep signature
  verification. Independent review found no issues. No GUI launched. Not merged or pushed.

- **macOS notification icon** (branch `codex/mac-notification-icon`, 2026-09-27):
  the screenshot's icon belongs to AppleScript: `NativeNotifier` launched `osascript`,
  so the sender was not Jasper. Packaged macOS now posts through an in-process JNI
  UserNotifications bridge, using `dev.jasper.app` and its bundle icon. The first
  eligible notice requests Jasper's notification permission; denial is respected.
  The existing threshold/visibility policy stays in Java; the native delegate permits
  an eligible hidden-pane notice while Jasper is foreground. UTF-16 text stays data.
  Packaging compiles/signs the bridge and verifies payload bytes, architecture and
  signatures. `check` includes a macOS fake-center harness and real headless JNI load;
  other platforms skip native compilation. Gradle/IDE launches send no native notices
  because they have no Jasper bundle identity (the old AppleScript behavior is superseded).
  The original sender-attribution regression failed before the fix. `./gradlew check`
  passed: 2,006 Java tests, zero failures/errors, five skips, plus the native harness.
  `packageDist` and strict package signature checks passed. Independent review found
  no actionable issues. Native icon/permission acceptance remains user-run; no GUI
  launched. Not merged or pushed.
  See [maintenance and acceptance](app-maintenance.md#macos-notification-identity).
- **Editable keyboard shortcuts** (branch `codex/shortcut-editor`, worktree
  `/Users/dustin/.codex/worktrees/shortcut-editor/moray`, 2026-09-27): approved bounded
  in-chat design, implemented inline with test-first coverage; no plan document.
  Clicking a shortcut or Unassigned opens an inline recorder with Save, Cancel,
  Remove and Restore default. Conflicts require explicit reassignment. App/plugin
  bindings save to the active `config.toml`, preserve unrelated text and comments,
  support symlink targets, and publish live to all windows. Contextual keys remain
  read-only. Commands already registered `app.shortcuts`; its search terms now
  include keymap and editing/settings aliases. Independent final review is complete;
  the symlink test skips platforms without symlink privileges, and search/edit recorders
  are mutually exclusive. The preview task now uses the theme ID in filenames (the
  existing theme-object filename exceeded filesystem limits after the theme refactor).
  `./gradlew check :jasper-app:installDist :jasper-app:shortcutHelpPreview` passed:
  2,017 tests, zero failures/errors, five environment-specific skips. Architecture,
  source hygiene and diff checks passed; light/dark previews at default and 18-point
  typography were inspected. Native GUI acceptance is user-run.
  Integrated into `main` on 2026-09-27 with user authorization to publish to
  `origin/main`. Merged-result `./gradlew check :jasper-app:installDist` passed with
  the same 2,017 tests and five skips. The worktree is retained.
  See [shortcut guide](keyboard-shortcuts.md).

- **Three-platform CI** (branch `fix/ci-cross-platform`, 2026-09-27): `main` had been red on Linux
  and Windows since 2026-09-13 (last green `1db31b1b`). The logs showed no assertion text and
  Gradle stopped at the first failing module, so most failures were hidden. CI now prints full
  failures, runs `check --continue`, fails a test after 5 min and a job after 30.
  - **Production fixes:** `HandoffSocket.close()` could mistake its own dying listener for a
    successor and leave its files (Linux); an SFTP write's `close()` turned the real error into
    "Self-suppression not permitted"; ssh `Include` globs threw on Windows; Windows refusals now
    read "Connection refused"; multi-line snippets no longer get CRLF on Windows (tomlj writes the
    platform separator); `.gitattributes` checks text out with LF.
  - **Tests:** no longer assume the host keymap, POSIX paths, or Debian's nounset-unsafe
    `/etc/bash.bashrc`. POSIX-only features (FIFOs, POSIX modes, the SFTP browser's `/` paths,
    OSC through a real PTY) are `@DisabledOnOs(WINDOWS)` or assert Windows' own behaviour.
  - **Known Windows limits, pinned by tests:** resuming a paused transfer asks Restart or Skip,
    because NTFS gives Java no file identity (user's choice to keep, 2026-09-27); SFTP downloads
    record a metadata warning (no POSIX modes). Sessions run on winpty, pty4j's default, which
    does not pass OSC through, so titles and shell integration probably do not work on Windows
    (ConPTY is the follow-up).
  - **Verified:** all three CI legs green at `8dd24bda` (after merging `main`); local `check` 2,002
    tests, 0 failures, 5 skipped. `main`'s Windows PowerShell PTY test first ran in CI here: a cold
    PowerShell start outlasted `Await`'s 10 s, so it now allows 30 s and reports the screen.
  - **Merged `main` (`71f96ed8`):** the transfer-scanner starvation this branch also found and fixed
    is fixed on `main` too (entry below); the merge keeps `main`'s version, which also keeps copy
    admission in rotating order. This branch fixes the Windows failures CI shows, which covers the
    startup entry's list except six symlink tests: they pass on CI's administrator runner but fail
    for a Windows account without the symlink privilege (open).
  - **Not done:** merge to `main`.

- **Windows terminal startup** (branch `codex/fix-windows-terminal-startup`, 2026-09-27):
  launching through `cmd.exe` supplies hidden environment entries such as `=C:`. The
  session options validator rejected these before PowerShell could start. Validation now
  follows the JDK's Windows rule (a leading `=` is allowed; embedded `=` and NUL remain
  invalid), retaining the existing rules on other platforms. Unit and real headless
  PowerShell PTY regressions failed before the fix and pass afterward. Independent review
  found no actionable issues. The full Windows check exposed broader failures and a
  stalled Remote test; see [verification details](windows-startup-verification.md).
  User confirmed the fix and authorized integration/publication on 2026-09-27.
  Merged into `main`; the broader Windows test findings remain open.

- **macOS Local Network identity collision** (branch `codex/mac-network-identity`, 2026-09-27):
  a user-retried Remote LAN connection produced `Local network denied by preference for Moray
  (dev.moray.app)` in macOS `nehelper`. Installed Jasper, historical Moray and JBR's generic
  jpackage launcher shared `LC_UUID` `25BA8067-AB3A-327B-8BE9-0C3162B915F5`; the usage description
  was already present and the bundle signature valid. Packaging now derives an app-specific
  launcher UUID from the bundle identifier and immutable SDK launcher before final signing.
  `verifyPackage` checks it; portable binary regressions run under `check`. This supersedes the
  earlier assumption below that a usage string and an ad-hoc signature were sufficient.
  `./gradlew check` passed: 1,997 tests, zero failures/errors, three skips, plus the portable UUID
  regression task. `packageDist` and strict signature verification passed; injecting the old UUID
  made `verifyPackage` fail as intended, and restoring the executable passed. The mounted DMG
  retains the corrected UUID and has a valid signature. Independent review found no issues.
  Native prompt/LAN acceptance remains user-run; Apple-issued signing remains separate release
  work (no valid identity on this Mac). The user authorized merging to `main` and pushing to
  `origin` on 2026-09-27. See [verification](mac-local-network-verification.md).

- **Original Steep application icon** (branch `codex/jasper-steep-icon`):
  user-selected full-height 68-degree tab and Frost chevron replace Ember in
  native packages and runtime assets. The exact SVG master and Inkscape generator
  live in `packaging/icons`; `build/Jasper-icon-set.zip` contains macOS
  ICNS/Retina assets, Windows 20-frame ICO with 24 PNG sizes, and Linux 18 sizes
  plus scalable SVG. Work is isolated in the managed `jasper-steep-icon` worktree.
  Follow-up: the user requested a full-canvas macOS variant with the steep tab
  drawn into its top edge after rejecting an added surround. Icon Composer
  supplies `Assets.car`; the ICNS/runtime PNGs contain the matching flattened
  artwork. Packaged Mac launches retain the native Dock icon instead of replacing
  it after Java starts. Windows/Linux retain the free-standing silhouette.
  Full check/package passed: 1,997 tests, zero failures/errors, three skips.
  Mounted DMG catalog/ICNS bytes and strict signature passed; native rendering
  confirmed the edge-tab artwork. Independent review found no remaining issues.
  See [verification](../packaging/icons/VERIFICATION.md) for final checks.
  No desktop GUI launched. The user approved merging to main and pushing.

- **Duplicate contributed menus on macOS** (branch `codex/fix-duplicate-plugin-menus`,
  2026-09-26): investigation of the installed app's logs confirmed older user Remote/Vault
  copies were skipped in favor of bundled versions, not loaded twice. `WindowChrome` could
  re-enter its menu rebuild while JBR dispatched a callback during a native menu mutation,
  duplicating SSH/Help on insertion or throwing during removal. Menu rendering now finishes
  the active pass before processing nested refresh requests against the latest model.
  Headless regressions reproduce both failures before the fix and pass afterward, including
  a menu-content change during insertion. Independent review found no actionable issues.
  `./gradlew check :jasper-app:packageDist` passed: 1,985 tests, zero failures/errors, three
  skips before the SDK 0.8.1 update; the macOS DMG was rebuilt and the package image verified.
  Native packaged-app acceptance remains user-run; no GUI was launched. Main integration
  and publication are authorized.
- **Transfer scanner starvation** (same branch, 2026-09-26): the menu integration check
  exposed an intermittent Remote stall: queued reservation retries could repeatedly take
  the scanner before the destination's current owner could finalize and release it.
  User-authorized follow-up prioritizes reservation holders for scanner work while retaining
  rotating copy admission, including already-scanned jobs resumed without a reservation.
  The same-destination regression failed in two of five repetitions before the fix; a new
  gated-copy regression caught and prevented starvation of an unrelated resumed job.
  Both fixes passed independent review and an isolated `./gradlew check` after SDK 0.8.1:
  1,994 tests, zero failures/errors, three skips. Concurrent icon work was excluded from
  the verification checkout and these commits. Main integration and publication authorized.
- **Full-height rail** (branch `worktree-rail-full-height`, 2026-09-25): the tab strip now sits
  over the terminal deck inside `WorkspaceRegions`' center, so the rail and the left/right panels
  run from the toolbar to the status bar and the bottom panel sits under the terminal only.
  `WindowPanelsTest` pins the geometry. `./gradlew check` passed: 1,912 tests, 0 failures, 3
  skipped. Not done: visual acceptance (user-run), merge and push.
- **One layout** (branch `claude/theme-engine`, plan `docs/superpowers/plans/2026-09-25-jasper-single-layout.md`):
  the retro style, Metal, the GNOME/Tango/OldGNOME rasters and `ui.theme.style` are gone; every
  window uses the IntelliJ-style chrome. SDK 0.8.0 removes `OldGnomeIcon` and
  `Appearance.icon(String, OldGnomeIcon)`; bundled plugins require `>=0.8.0, <0.9`. Plan
  deviation: the title-row constant is `MacTitleBar.TITLE_HEIGHT` (`HEIGHT` hid
  `ImageObserver.HEIGHT`). Next: the theme engine with IntelliJ-format themes and IntelliJ
  Light (part 2). Not merged, not pushed.
- **Theme engine** (branch `claude/theme-engine`, plan
  `docs/superpowers/plans/2026-09-25-jasper-theme-engine.md`):
  - **Themes:** every theme is an IntelliJ-format `.theme.json` installed by `ThemeManager`. Light
    is classic IntelliJ Light, vendored from intellij-community at `f7377708`. Dark is
    `jasper-dark.theme.json`, keeping today's colours.
  - **Keys:** `ChromeKeys` derives the `Jasper.*` chrome keys a theme leaves unset.
  - **Surfaces:** side panels paint `ToolWindow.background`, and the status bar paints `StatusBar.*`
    under a 1-px rule.
  - **Removed:** the FlatLaf properties overrides, `BrandedButtonUI` and `BuiltinTheme`.
  - **Planning decisions:** listed in the plan header. They cover the rail staying chrome, contrast
    floors for derived text, FlatLaf form-control heights, and Jasper Dark menus using the list
    selection.
  - **Spike and deviations:** IntelliJ Light and Jasper Dark matched on the first run, so the
    dark-parent fallback was not needed. The plan's status lists the deviations.
  - **Accepted Jasper Dark differences:** FlatLaf builds IntelliJ dark themes on its Darcula base.
    `jasper-dark.theme.json` pins the 52 drifted values Jasper draws. The rest are:
    - ruled: menu selection, form heights, `Jasper.palette*`;
    - set by FlatLaf after the theme: the spinner and editable combo follow `TextField.background`,
      and toggle and tab underline colours come from `{*-dark}`;
    - not drawn: AWT system colours, internal frames, sliders, hidden tab and scroll-bar buttons,
      progress text, and FlatLaf-only window borders.

    The plan's status has the full list.
  - **Not done:** visual acceptance of Light and Dark (user-run), merge and push.
- **Search Everywhere palette** (branch `claude/theme-engine`, plan
  `docs/superpowers/plans/2026-09-25-jasper-search-everywhere.md`):
  - **Tabs:** All first (Cmd/Ctrl+K), then one tab per scope.
  - **All tab:** groups each participating scope's matches, capped per scope, with a "More in…"
    row. SDK 0.8.0 adds `ScopeSpec.withInAll`; Vault opts out.
  - **Rows:** one line, with a hint bar showing the other verbs. Tab and Shift+Tab switch tabs.
  - **Colours** come from the theme's `SearchEverywhere.*`, `List.*`, `Popup.borderColor`,
    `Popup.dropShadowColor` and `Component.linkColor`.
  - **Removed:** the `>` picker, scope aliases, the Cmd+1–5 row shortcuts and the `Jasper.palette*`
    keys.
  - **Deviations:** View → Command Palette opens All too; a step from All closes with its scope;
    the tag drops before the detail; All caps at 200 rows; the shortcut reference and config
    template drop Cmd+1–5. Details in the plan's status banner.
  - **Planning decisions:** listed in the plan header.
  - **Not done:** visual acceptance (user-run), merge and push.
- **IntelliJ theme import, plan 3a** (branch `claude/theme-import`, plan
  `docs/superpowers/plans/2026-09-25-jasper-theme-import.md`):
  - **Choosing:** `ui.theme.name` names a built-in or installed theme. `ui.theme.variant`, the View → Appearance
    Light and Dark items and `view.appearance.*` are removed; the View submenu is Terminal Colors.
  - **Installing:** JetBrains Marketplace theme plugins (`.jar`, `.zip`) in `<home>/themes/`, read in
    place and rescanned with each configuration read.
  - **Terminal:** under `match`, a theme's editor-scheme console colours, with IntelliJ's vendored
    Default and Darcula schemes as parents.
  - **Problems** are configuration warnings; none blocks start-up.
  - **Planning decisions and deviations:** listed in the plan header (status line), including the final review's hardening.
  - **Follow-up done:** one 256 MiB inflation budget per archive read now covers plain `.jar` reads too.
  - **Not done:** plan 3b (the bundled set), visual acceptance (user-run), merge and push.

### IntelliJ-style chrome and follow-ups — merged 2026-09-25

- **IntelliJ-style modern chrome** (branch `claude/intellij-chrome`, plan
  `docs/superpowers/plans/2026-09-24-jasper-intellij-chrome.md`): title-only title row, IntelliJ
  toolbar, editor tabs and find bar with vendored IntelliJ icons; SDK 0.7.6 adds
  SPLIT/ZOOM/TERMINAL/SERVER, draws plugin SVGs as authored and uses `SessionSpec.icon` as the tab
  icon. Planning decisions (tab height setting kept, toolbar default mode kept, no toolbar
  overflow menu, tab animation removed) are listed in the plan. Visual acceptance in modern
  light, modern dark and retro is user-run. Merged into `main` on 2026-09-25; not pushed.
  Rulings: the vendored icon `LICENSE.txt` under `icons/intellij/` is the canonical Apache-2.0
  text (the upstream root `LICENSE.txt` in the source tree is JetBrains' IDE build terms, not the
  artwork's license); one `RetroChromeTest` assertion that pinned "no Settings in modern" was
  narrowed to match the new modern chrome, with all other retro assertions unchanged.
  `./gradlew check` passed: 1,771 tests, 0 failures, 0 errors, 3 skipped.
- **Terminal colours independent of the UI** (same branch, plan
  `docs/superpowers/plans/2026-09-25-jasper-terminal-colors.md`): `ui.theme.terminal =
  "match" | "light" | "dark"` and View → Appearance Terminal choices; live, retro ignores it, SDK
  unchanged. Visual acceptance of the six light/dark combinations and retro is user-run.
- **Font line height below 1.0:** `font.line_height` accepts 0.5–3.0; below 1.0 rows are tighter
  than the font and glyphs may overlap neighbouring rows (supersedes the 2026-09-12 ruling that kept
  the minimum at 1 to avoid overlap).
- **Tab height:** `window.tab_height` is 20–72 logical pixels, default 30; out-of-range values clamp
  to the nearest limit with a warning instead of falling back to the default.
- **Window defaults (2026-09-26, user's choice):** new windows open at 100 × 35 cells (was 150 × 45,
  which superseded the parent spec's grid ruling below); the rail defaults to its 28 px minimum and the
  status bar to 24 px; the toolbar stays 30 px. `buddy.enabled` now defaults to false (Jasper is opt-in).
- **View → Tools:** the app's per-panel toggles submenu is now titled Tools (was Panels). Remote no
  longer adds its own View items (SFTP panel, SSH Hosts), which duplicated those toggles. SDK 0.8.1
  adds `PanelSpec.toggleActionId`: a panel may name one of its plugin's already registered actions as
  its toggle, and the app then lists that action in Tools and registers no `<panel id>.toggle`.
  Remote names its renamed actions (Toggle SSH hosts, with ⇧⌘S, and Toggle SFTP), so each panel has
  one palette entry and Tools shows the shortcuts; Remote requires `>=0.8.1`.
- **Rail width:** `window.rail_width` is 28–64 logical pixels, default 28, clamped the same way.
  Rail buttons stay square at width − 6, separators at width − 14. View → Rail width… and the
  `view.rail_width` command set a session-only width, like Tab height….
- **Toolbar and status bar height:** `window.toolbar_height` (24–64, default 30) and
  `window.status_bar_height` (20–48, default 24) clamp the same way and have matching session-only
  View dialogs and `view.toolbar_height` / `view.status_bar_height` commands. Toolbar buttons are the
  height − 6 (icon-only buttons square; labelled buttons keep their side padding), separators the
  height − 14. The status bar is exactly its configured height unless the status font's line is taller,
  so a large UI font never clips it. Hiding either bar keeps its height. The four size dialogs share
  `WindowChrome.editSize`.
- **SDK version on merge:** `main` had already claimed SDK 0.7.5 for the Remote SFTP icons (FILE,
  LINK, UPLOAD, DOWNLOAD, UP, NEW_FOLDER, PAUSE, RESUME), so the chrome names moved to 0.7.6 and
  Remote requires `>=0.7.6`.

### Transfer strip — 2026-09-24

The bottom Transfers panel (two paged tables, thirteen buttons) is replaced by a compact strip at
the bottom of the SFTP sidebar with one contextual action per transfer, and uploads, downloads and
host-to-host copies ask once before copying onto existing items
([amendment](superpowers/specs/2026-09-24-jasper-remote-transfer-strip-design.md),
[plan](superpowers/plans/2026-09-24-jasper-remote-transfer-strip.md)). The queue, durability and
recovery are unchanged; the "Replace / Skip existing" choice is stored in the job's existing
file/folder policies. A finish whose only issues are skipped items fades like a clean one.
The final review added two queue operations: **Retry failed** (`TransferCoordinator.retryFailed`)
re-reads each failed item's source on the scan lane and resets it with the same per-entry
`TransferStore.reset` that Restart uses, leaving skipped items skipped; **Resolve…** finds the
first entry needing a decision with `firstAttention`, however far into the job it is.
GUI acceptance is item 11 of [remote-7c-verification](remote-7c-verification.md).

### SFTP directory follow probe — 2026-09-24

Follow terminal folder only followed OSC 7, which stock bash and zsh do not send over SSH. The
[directory probe amendment](superpowers/specs/2026-09-24-jasper-remote-directory-probe-design.md)
([plan](superpowers/plans/2026-09-24-jasper-remote-directory-probe.md)) adds a fixed read-only
probe on a separate exec channel for Linux and macOS hosts; each followed pane has its own SSH
connection so the probe can tell shells apart (macOS no longer shows other processes'
environments). Automated tests cover the script against real local processes, dedicated leases,
the follower policy and the plugin wiring. The GUI acceptance steps in
[remote-7c-verification](remote-7c-verification.md) are the user's.

Final review fix wave (2026-09-24): `DirectoryFollower.request` (an explicit ask — focus, panel
shown, follow turned back on) now resets the last-delivered path before probing, so its result
always delivers even when unchanged; this closes a gap where re-enabling Follow after a manual
sidebar navigation could leave the sidebar stuck on the manually chosen folder. Enter-triggered
results are still delivered only when they differ from the last delivered path. The probe script
now prints a `jasper-cwd` marker field before the directory so remote-user startup-file output
cannot be mistaken for the path; `DirectoryProbe.parse` takes the field after the last marker.
`DirectoryProbe.read` now takes one deadline shared between channel-open and waitFor instead of
applying the 2 s timeout to each.

### SFTP phase 1 — implemented, reviewed and verified

The [SFTP design](superpowers/specs/2026-09-23-jasper-remote-sftp-design.md) is approved
on `codex/remote-sftp` in `/Users/dustin/.codex/worktrees/remote-sftp/moray`, from main
`1fcf9a8d`. Conversation-approved scope: one remote file-browser sidebar; local/remote
and remote/remote copies; a Buddy-independent Transfers panel and status-bar progress;
bounded large-file/folder handling; persistent pause/resume with unfinished jobs restored
paused; explicit conflicts, temporary-file publication and recoverable errors. It remains
inside `dev.jasper.remote`, depends on SSH 7a, and does not require tunnels 7b.

The implementation adds generic SDK progress/chooser APIs and a plugin-owned SQLite queue.
The user's icon clarification is recorded in section 9.3: all SFTP artwork uses SDK
`Appearance.icon(IconName)`; missing semantic names and Tabler/OldGNOME2 assets belong in
the app/SDK, with both skin mappings tested. Remote adds no icon resources or custom painting.
It was self-reviewed for lifecycle, recovery, bounded work and architectural consistency.
The user requested a detailed [implementation plan](superpowers/plans/2026-09-23-jasper-remote-plan-7c-sftp.md),
an independent adversarial plan review, then native implementation. That sequence is authorized;
the latest native execution choice supersedes the earlier per-task-agent preference. Baseline
`./gradlew check` passed: 1,765 tests, 1,762 passed, three expected skips, no failures/errors.
The adversarial review found eight important contract/recovery issues; the plan now includes
explicit resolutions and regression tests. Native implementation has completed semantic icons, status progress, and owner-based pickers;
session leases, endpoint identity and prompt ownership changes also pass their regression suites.
Bounded local/SFTP endpoints also pass: explicit ACK/force barriers, safe publication, links,
short reads, sparse offsets, cancellation, and SFTP through ProxyJump with a surviving shell.
Durable SQLite storage passes reopen, checkpoint, 100k-entry pagination, cross-process locking,
killed-writer recovery and corrupt/newer-schema preservation tests. The transfer engine now passes local/SFTP/relay copies, recursive folders and links, short pause
checkpoints and restart validation, blocked-write pause, seven killed-process publication boundaries,
and a >4-GiB/100k-entry probe under a 64-MiB heap. The browser/controller, disk-backed directory pages, destination picker and cancellable
file operations are implemented with native controls and SDK icons. Plugin wiring and Transfers management are integrated; real staged/HostedContext tests pass
shutdown during read, checkpoint and publication, with paused recovery and lock reacquisition.
The final `./gradlew check :jasper-app:installDist` gate passed after the review fixes
(1,845 tests: 1,842 passed, three expected skips, zero failures/errors). The independent
review found two data-integrity issues and six control/recovery issues; all are fixed with
regressions. This includes Unicode subtree handling, destination aliases, publication decisions,
credential cancellation, bounded remaining-conflict policies, frozen scan frontiers, explicit
link navigation and persistent deletion results. Distribution contents and SDK floor are
verified; Remote bundles no icons. No deferred review minors. See
[verification and native acceptance](remote-7c-verification.md).
Nothing merged or pushed.

### Latest completed work

Configuration documentation follow-up: the example and settings reference now include
explicit `ui.theme.style = "modern"` and `ui.font.family = "system"`, with optional
UI sizing, portable fallback, full-restart behavior and retro's retained-but-ignored
variant documented. The generated Settings template matches this guidance. Removed
obsolete History keys from the app-config overview; their plugin settings remain
documented separately. Existing config loader/template tests pass, including example
defaults under macOS and non-macOS parsing. No runtime behavior changed.

The user approved merging all `codex/retro-metal` work through `adfc37cb` into
`main` and pushing `main` to `origin`. The conflict-free merge preserves the newer
keyboard-shortcut reference and includes Metal retro chrome, bundled OldGNOME2
icons, portable typography, skin-aware SDK icons, Vault/Remote icon migration and
self-contained Vault-backed SSH import. The Codex-managed feature worktree and its
development data are retained.

Merged-result verification: `./gradlew check :jasper-app:installDist` passed in
1m51s with **1,765 tests: 1,762 passed, three expected skips, zero failures/errors**.
Architecture guards, source hygiene and diff checks passed; the main-checkout
application distribution was rebuilt. Native GUI and real-host acceptance remain
user-run. Earlier integration/publication notes below describe their historical
milestones.

The approved keyboard shortcut reference is integrated into local `main` through
`5f374e04` from `codex/keyboard-shortcuts`. The Codex-managed worktree is retained at
`/Users/dustin/.codex/worktrees/keyboard-shortcuts/moray`.
**Help → Keyboard Shortcuts…** opens one reusable window with grouped app/plugin rows,
text and exact-key capture search, effective overridden bindings, conflict explanations,
contextual keys and live updates. [Guide and native acceptance](keyboard-shortcuts.md).
No SDK changes. Third-party private UI listeners are not discoverable, and OS-reserved
shortcuts remain OS-controlled.

Bounded design approved in chat; native inline implementation and independent review.
The review's held-key/focus-loss finding is fixed and re-reviewed. Tests cover catalog
completeness, overrides/conflicts, contextual/plugin removal, capture/tails, singleton
reuse/live refresh, native-recorder cleanup and larger UI typography. Headless dark/light
previews at default/18-point typography were inspected; no native GUI was launched.
`./gradlew check :jasper-app:installDist :jasper-app:shortcutHelpPreview` passed:
**1,652 tests: 1,649 passed, three expected skips, zero failures/errors**. All architecture
checks passed; the worktree distribution was rebuilt. After the user-approved fast-forward
merge, `./gradlew check :jasper-app:installDist` passed again on local `main` with the same
counts and rebuilt the main-checkout distribution. GUI acceptance remains user-run.
Nothing was pushed; the Codex-managed worktree and branch are retained.


Remote plan 7a and its follow-ups are integrated into local `main` from
`codex/remote-7a` through `22b0fd29`: SSH sessions and navigation, connection
overlays, configurable UI typography and Remote shortcuts, keyboard-ready hosts
sidebar, and terminal resize coalescing. The merge preserves `main`'s title-colored
status bar and Vault placement under File instead of the rail (`c1eb3858`); their
existing tests now assert that behavior. Nothing was pushed. The Codex-managed
worktree is retained, including its development home and saved plugin data.

Merged-result verification: `./gradlew check :jasper-app:installDist` passed with
**1,644 tests: 1,641 passed, three expected skips, zero failures/errors**. All
architecture checks passed and the application distribution was rebuilt. GUI
acceptance remains user-run.

Both architecture refactors are merged into local `main`: terminal through
`c7b796b`, application/Buddy through `9b30dc2`. The completed app-refactor branch
and worktree were removed. Those merge operations did not push to origin.

The three modules are `jasper-app` (product composition), `jasper-terminal`
(terminal library) and `jasper-buddy` (JDK-only companion library). Start at the
[documentation index](README.md) for each module's onboarding, architecture and
maintenance guides. Plugin SDK plan 1 (core and runtime) is implemented and merged into
local `main`, as is plan 2 (actions and chrome placements). Plan 3a (rail, panels and plugin
windows) is merged too; the spec's plan 3 was split into 3a and 3b. Plan 3b (Plugins manager,
install and restart) is merged too. The spec's plan 4 is split into 4a (terminal API: observe,
inject, open) and 4b (plugin-provided sessions); 4a and 4b are merged too. Working-directory provenance moved from 4b to a small plan 4c, which is
merged too. With it the plugin SDK plans are complete. The palette-plugins design
(`superpowers/specs/2026-09-21-jasper-palette-plugins-design.md`) is approved; plan 5a (the
contribution surface) is merged at `3eb143a`, plan 5b (the History and Snippets plugins) is
merged at `0550bad`. The plugin-home design (`superpowers/specs/2026-09-22-jasper-plugin-home-design.md`)
is merged at `17c0476`. The Credential Vault design
(`superpowers/specs/2026-09-22-jasper-vault-design.md`) has plan 6a (core and API) integrated into local `main` at `c8141b6`; plan 6b (UI)
is being continued in `.worktrees/vault-6b` on `claude/vault-6b`, then the SSH plugin spec. A development launch keeps its own home under
`jasper-app/build/dev-home` (`jasper.home`, merged at `8d5566e`).

### Packaged SSH failures — 2026-09-24

The packaged app failed every Remote SSH connection with `ExceptionInInitializerError`;
`./gradlew :jasper-app:run` connected. Cause, reproduced with the bundled runtime and the
image's own `Jasper.cfg` classpath: jpackage had put every bundled plugin jar (sshd,
BouncyCastle, slf4j-jdk14, …) on the application classpath. MINA looks up its
`SecurityProviderRegistrar` through the thread context class loader, got the application
loader's copy, and the cast to the plugin's copy threw `ClassCastException` inside
`KeyPairProvider.<clinit>`. `packageApp` now strips `plugins/` entries from `Jasper.cfg` and
re-signs once after every bundle edit (the vtool step no longer signs); `verifyPackage`
rejects plugin classpath entries. With the fixed classpath, the same reproduction completes
key exchange and authentication.

A LAN host (192.168.1.120) failed with "No route to host" even from `gradle run`, while
`ssh` reached it. From one shell, `nc`, Apple's `python3` and a Microsoft-signed JDK 17
connected, but the ad-hoc-signed bundled `java` got `NoRouteToHostException` over the same
en0 route. Cause: macOS Local Network privacy, which keys the grant to the code signature.
`packageApp` adds `NSLocalNetworkUsageDescription` (verified), and Remote now says "No route to
host <host>. On macOS, allow Jasper under System Settings > Privacy & Security > Local Network."
instead of MINA's raw future text (`FailuresTest`). Ad-hoc signatures change with each build,
so the grant may not persist across rebuilds until a stable signing identity exists. The
`gradle run` JBR `java` needs its own grant. Native confirmation of the prompt is a user GUI
check; see [packaging](packaging.md#macos-local-network-access).

### Self-contained SSH import — implemented and verified

The approved [design](superpowers/specs/2026-09-23-jasper-vault-backed-ssh-import-design.md)
and seven-task [plan](superpowers/plans/2026-09-23-jasper-vault-backed-ssh-import.md)
are implemented natively on `codex/retro-metal`. Vault/Remote 0.2 import referenced private keys
and their passphrases into encrypted managed storage, grant only the requesting plugin, and save
ordered Vault identities after persistence. Re-import **Update** repairs Agent hosts while
preserving UUID, group and favorite. Conflicting host edits force Refresh; durable keys remain
available for retry. Legacy manual Agent/password/path-based authentication is retained.

Real Vault-form tests pass in both icon modes: two aliases deduplicate an encrypted key, repair
an Agent host, restart with source files deleted, unlock and authenticate against loopback with
no agent. Staged isolated-loader tests exercise the exported API, independent key parsing and
1x/2x Metal/FlatLaf forms. These exposed and fixed setup-dialog ordering, independent setup
waiter cancellation, BC provider isolation and Swing UI-delegate lookup from plugin components.

Plan rulings: an independently authored v1 fixture replaces an old-code-generated fixture;
transaction inputs are consumed rather than retained; one state owner combines request/review
and passphrase prompts; the restart test accumulates the testkit's draining output. The existing
plain EC PEM generator produces a form MINA rejects; supported encrypted OpenSSH ECDSA is tested,
and the unrelated generator format is deferred. No production key/Vault was read or migrated.
`./gradlew check :jasper-app:installDist` passed with **1,757 tests: 1,754 passed, three expected skips, zero failures/errors**. Architecture guards, source hygiene and staged dependencies passed. Independent final review is complete: a stale legacy editor could persist a duplicate managed UUID and prevent unlock; stale saves and duplicate snapshots are now rejected before persistence. Held-read cancellation and overlapping durable-import regressions verify secret wiping and deduplication, including deliberate mutation checks. No minor findings remain. The [plan review record](superpowers/plans/2026-09-23-jasper-vault-backed-ssh-import.md#final-review-record-and-execution-rulings) preserves every execution ruling and its cost. Windows ACL handling was inspected but not executed on Windows; full OpenSSH semantics remain outside the documented import subset. GUI and real-host acceptance remain
user-run; re-import and check **Update** on the affected hosts when testing this distribution.

### Remote empty-agent diagnosis — 2026-09-23

Investigated the user's imported-host authentication failure on `codex/retro-metal`.
The merge changed Remote's icon selection only; its SSH authentication code matched main.
Saved hosts in the previous SSH worktree use Vault, while the fresh imports here use Agent;
the current system agent reported no identities. Development homes and Vault files are
separate. No private keys were read, credentials migrated, or real remote connection opened.

Remote now checks for usable agent identities before TCP/host-key prompts and explains how
to recover, instead of reporting generic authentication rejection. The probe uses the auth
timeout and connection cancellation ownership; retry queries the agent again. A regression
failed with the user's original message, then passed after the fix, including successful
loopback SSH authentication after adding a generated key to the test agent. Existing Vault
and agent connection tests pass. Independent review found no actionable issues. The guide
explains Vault mapping, agent loading, and separate development homes. Choosing/configuring
a credential for the user's current saved hosts remains user-dependent.

Verification: `./gradlew check :jasper-app:installDist` passed: 1,712 tests,
1,709 passed, three expected skips, zero failures/errors. Distribution rebuilt;
source hygiene and `git diff --check` passed. No native GUI launch or push.

### Retro Metal appearance — 2026-09-22

Implemented on `codex/retro-metal` in `/Users/dustin/.codex/worktrees/retro-metal-plan/moray`,
based on `ac02f94`, following the approved [design](superpowers/specs/2026-09-22-jasper-retro-metal-design.md)
and [plan](superpowers/plans/2026-09-22-jasper-retro-metal.md). The user chose native inline
execution with one independent final review. Headless verification and the independent review fix pass are complete.
See [configuration](configuration.md) to enable it.

Retro uses Metal/Ocean, light controls, black high-contrast terminal colors and bundled GNOME
icons. The startup style stays fixed; reload derives a restart notice while sessions and
live settings continue normally. Plugins report LIGHT chrome and preserve artwork and SDK
contracts. User screenshot feedback added flat compact toolbar buttons with 24-pixel icons,
icon-only tab close controls and unboxed status actions; forms retain Metal defaults.
The user confirmed GPL2+ for OldGNOME2; assets include the license, honest origin notice and
22-file hash manifest. No GUI launch, merge, push or edits to the original checkout's dirty files.

Execution adjustments: invalid config types retain existing whole-snapshot rejection; unknown
strings default per-field. EDT cleanup/parameterized tests explicitly marshal LAF changes.
Compacting toolbar labels invalidates Swing's cached layout requirements. Real palette scope
tests cover submission/cancellation in both styles. Vault's own SDK-only test module renders
Metal forms without an app dependency; host tests render forms in all three appearances.
Visual QA also corrected a low-contrast palette accent alias and terminal placeholder foreground; regression tests failed before both fixes. Native OS title bars, modality, menu/focus and Retina acceptance remain user-run.

Verification: `verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture
verifyPluginArchitecture check :jasper-app:installDist` passed: 1,578 tests, 1,575 passed,
three expected skips, zero failures/errors. Inspected 1x/2x workspace, palette and Vault renders;
modern light/dark host forms remain intact. Installed `jasper-app.jar` contains all 22 PNGs,
matching hashes, license and notice. Source hygiene and `git diff --check` passed. No native
window was launched. The plan's user-run OS acceptance checklist remains pending.

Independent review found two issues, both fixed with failing-then-passing regressions: very
long tab titles now elide within 240 logical pixels, preserving complete tooltip/accessibility
text and a reachable close icon under overflow; GNOME icons use JDK multi-resolution ImageIcon
support so Metal renders visibly disabled icons, including icon-only fallback controls.
The full check/distribution gate passed again after these fixes. No deferred minor findings.
The reviewer set native OS/physical Retina behavior aside; those checks remain user-run.
Branch and worktree are preserved, with no merge or push.

### Retro toolbar icon refinement — 2026-09-23

After native screenshot feedback that the OldGNOME2 toolbar symbols were too faint,
replaced the five core toolbar glyphs with clearer classic Tango choices: blue plus
(New Tab), blue monitor (New Window), paired windows (Split), blue expansion arrows
(Zoom), and a blue-lens magnifier (Find). The rest of the original collection remains.
Flat 24-pixel toolbar geometry, icon-only closes and disabled-state behavior are unchanged.

Ten unmodified Tango 0.8.90 PNGs (16/32-pixel sources) are bundled with upstream COPYING,
AUTHORS, source URL and hash manifest. The upstream release places these icons in the
public domain. JDK multi-resolution ImageIcons retain stock Metal disabled rendering;
32-pixel sources are rendered to 24 logical pixels for toolbar use. No runtime downloads.
Targeted icon/resource, disabled-state, toolbar/tab tests and installDist passed. Inspected
the enabled toolbar preview and workspace render. No native GUI launch, merge or push.

### Retro toolbar captions — 2026-09-23

Following the user's MobaXTerm reference, retro toolbar icons are now 32 logical pixels
with centered labels underneath and flat buttons. Standard Metal font/disabled behavior
remain intact. Built-in controls and contributed buttons/dropdowns use the same caption
placement; plugin-owned artwork remains unchanged. Icon-only mode is shorter, hidden mode
still hides the bar, and narrow windows compact labels then restore them when widened.
Modern toolbar presentation and tab-close controls are unchanged. No new assets/dependencies.

Verified targeted toolbar actions/modes, minimum-width layout and widening, plugin toolbar
contributions, 16/24/32-pixel disabled-icon rendering, and the rebuilt distribution. Inspected
actual toolbar renders at 1x/2x, headlessly without starting any shells or native windows.

User size refinement: reduced toolbar icons from 32 to 28 logical pixels, retaining captions
below them and flat styling. The bundled 32-pixel source artwork is unchanged.

### SDK icons for both skins — 2026-09-23

Implemented SDK 0.7.2 on `codex/retro-metal` per the approved [design](superpowers/specs/2026-09-23-jasper-sdk-skin-icons-design.md)
and [native implementation plan](superpowers/plans/2026-09-23-jasper-sdk-skin-icons.md).
`Appearance.icon(modernSvgResourcePath, OldGnomeIcon)` selects modern SVG or one of 22
explicit OldGNOME2 choices. Host toolbars use independent 28px retro variants; shared
menu/palette/plugin-content icons remain 16px. Existing icon calls and custom icons keep
their behavior. Sample and Vault's existing SDK icon calls opt in, with SDK >=0.7.2 manifests.
The fake host supports pre-start style configuration and inspectable icon selections.

Bundled 55 unmodified originals with GPL2+ notice/license and source hashes. See the
[catalog and examples](sdk-icons.md). Source images lacking larger variants are enlarged;
the original rectangular keyring image is centered with its proportions preserved. A
shared-placement regression also exposed missing Swing SMALL_ICON metadata; contributed
menus now display their supplied compact icons. These execution adjustments are recorded
in the plan; the user's direct execution request superseded another plan approval checkpoint.

Full architecture guards, check and installDist passed: 1,591 tests, 1,588 passed, three
expected skips, zero failures/errors. Installed app jar contains all 55 verified PNG hashes
and license/notice; inspected catalog and modern/retro headless toolbar previews at 1x/2x,
including disabled Metal icons. Independent review of `4234a95..ffa18ed` found no issues
and independently verified XML counts and installed assets. Native OS/physical Retina
acceptance stays user-run; attribution retains the user's explicit GPL2+ declaration.
No GUI, login shell, merge or push. Branch/worktree preserved; no deferred findings.

### Semantic SDK icons and Vault — 2026-09-23

Updated `codex/retro-metal` from local main `c1eb385` in merge `20f3564`, preserving its
Vault File-menu placement/no rail entry and modern title-colored status background; retro
continues using Metal's light panel background. Full-suite integration fixtures now verify
these main changes while retaining status contrast checks.

The user's semantic API clarification supersedes requiring plugins to choose both skins:
SDK 0.7.3 now provides `Appearance.icon(IconName.LOCK)` / `UNLOCK` and 20 other meanings.
Jasper owns all modern/retro artwork and uses its own resource loader. Existing custom SVG
and OldGnomeIcon overloads remain compatible. The fake exposes FakeNamedIcon(name, retro).
Vault's status/menu icons and actual manager lock/unlock controls use semantic names only;
its minimum SDK is 0.7.3. Original modern lock/unlock SVGs are copied byte-for-byte into the
host; manager buttons now share those standard shapes, including an unlock glyph.

See [implementation plan](superpowers/plans/2026-09-23-jasper-semantic-icons.md) and
[API guide with headless Vault preview](sdk-icons.md). Added 22 modern SVGs with exact
source/hash manifest and MIT license; missing existing choices came from a pinned official
Tabler revision. Existing OldGNOME2 catalog supplies retro images. Package verification
confirmed 22 modern SVG and 55 retro PNG hashes plus notices. All architecture guards,
check and installDist passed: 1,596 tests, 1,593 passed, three expected skips, zero failures/errors.
Independent final review of `8ad055b..9750043` found no issues and independently confirmed
XML totals, installed modern hashes and original Vault artwork identity. Native GUI/login
shell not launched; physical desktop acceptance remains user-run. Branch/worktree retained;
local main is an ancestor, no merge back or push, no deferred findings.

### Retro toolbar spacing refinement — 2026-09-23

User screenshot feedback requested a tighter MobaXTerm-like toolbar. Reduced retro button
padding from 8 to 5 logical pixels per side and from 4 to 3 vertically. Captions now use
regular weight, one point smaller than the Metal button font, with a 3-pixel icon/text gap.
Icons remain 28px; built-in and contributed buttons share the style. Modern toolbar styling
is unchanged. Verified existing toolbar modes, minimum width, label restoration, contributed
controls and headless previews; distribution rebuilt without launching the GUI.

### Retro in-window menu bar — 2026-09-23

Per user request, desktop bootstrap now sets `apple.laf.useScreenMenuBar=false` for the
captured retro startup style and true for modern, before Swing/toolkit initialization.
Existing terminal and auxiliary window JMenuBars stay attached to their frames, so retro
renders them inside the windows on macOS. Full process restart is required; config reload
does not change this process-wide selection. Startup configuration regression verifies both
styles override inherited JVM values. Headless bootstrap/chrome/window checks and installDist
passed; native macOS acceptance remains user-run. No GUI launch or push.

### Remote plan 7a (SSH) — 2026-09-22

Implemented in the native worktree `/Users/dustin/.codex/worktrees/remote-7a/moray` on
`codex/remote-7a`, now merged into local `main` (not pushed). The [7a plan](superpowers/plans/2026-09-22-jasper-remote-plan-7a-ssh.md)
implements the approved [Remote design](superpowers/specs/2026-09-22-jasper-remote-design.md): saved hosts,
config import, host-key trust, Vault/agent authentication, shared sessions, ProxyJump, hosts panel,
editor, palette, actions and status. [Guide and native acceptance](remote.md). Tunnels (7b) and SFTP (7c) follow.

Native inline execution, one commit per task, followed by one independent review and a tested fix commit.
`./gradlew check :jasper-app:installDist` passed: **1,604 tests, 1,601 passed, three expected skips,
zero failures/errors**. All review findings are addressed; no deferred minors.
[Execution and review record](remote-7a-verification.md).
Plan corrections so far: current-main base preserves the shell revert; native branch/worktree and accurate
Codex attribution replace example Claude names. Fixed the store queue's completion type, strict trust
validation before host filtering, MINA connection-context target lookup and wrapped DNS errors.
Added lifecycle regressions and owned session references/cancellation for Vault, prompts, jump dependencies
and natural shell exit; callbacks return to the UI executor. Test fixtures use fresh credentials,
BC Ed25519 keys, short Unix socket paths, complete resize replies, correct fake activation and test-only UI
access bridges. These corrections preserve the intended behavior. The planned split/collapse/credential
ordering/fake-Vault deviations are recorded in the spec. GUI acceptance remains user-run.
The review fix pass composes queued host mutations, protects broken external edits, fixes optional
Vault loading, accepts multiple legitimate keys per trust file while rejecting conflicts/revocation,
bounds/cancels agent I/O, preserves OpenSSH first-value/quoted/commented imports, and reports
mutation failures after editor closure. SDK 0.7.2 adds `Panels.toggle` (app and testkit) for lazy
per-window Hosts action toggling; Remote requires it. Enter, star clicks and stale selection are fixed.

### Remote SSH card/dialog follow-up — 2026-09-22

User-approved adjustments on the same `codex/remote-7a` worktree: renameable default group
(persisted without rewriting hosts), larger searchable host cards with cached detected OS and IP,
active-session badges, double-click/Enter to focus the most recently used running pane, and a
Cancel/Retry progress dialog before tab/split creation (also used on reconnect). Explicit Connect
still creates another session. The host editor shows the renamed default group as a placeholder.

Metadata runs only over a user-established authenticated session, via bounded separate exec
channels. No proactive passwordless connections, no commands injected into the user's shell.
Unknown/unsupported OS discovery leaves the terminal usable. Results are cached against the
actual authenticated endpoint; direct peer IPs are shown when useful, never a jump forward's IP.

One independent review found accessibility and endpoint-cache association issues plus invisible
keyboard selection on group headers; all are addressed with regressions. Headless FlatLaf renders
at 360px and 240px sidebar widths and the connection dialog were inspected. Native acceptance
remains user-run. `./gradlew check :jasper-app:installDist` passed: **1,617 tests, 1,614 passed,
three expected skips, zero failures/errors**; Remote **69/69**. No merge or push.

### Remote host-list styling revision — 2026-09-22

At the user's request, replaced the rounded host cards with flat, indented tree-style rows.
Host labels are two points larger than the base list font, with eight points of vertical padding.
Search, favorites, group renaming, session counts and last-session activation remain. OS/IP details
are shown in the selected-host area and tooltips and remain searchable/accessibly named.
The separately requested connection overlay is still a design draft pending approval/Cancel choice.
`./gradlew check :jasper-app:installDist` passed: 1,617 tests, 1,614 passed, three expected skips,
zero failures/errors. Headless renders at 360px and 240px were inspected.

### Remote list density refinement — 2026-09-22

The user found the padded tree rows too loose. Reduced row padding from eight to three points
vertically and the font increase from two points to one. Group headers use the base bold font
with tighter spacing; an active-session dot (plus a count for multiple sessions) replaces the
wordy row status. Selected-host details now align flush left with their action controls.
Interaction, accessible status, search and connection behavior are unchanged.
`./gradlew check :jasper-app:installDist` passed: 1,617 tests, 1,614 passed, three expected skips,
zero failures/errors. Headless renders using Jasper’s actual dark theme at 360px and 240px were inspected.

### App-wide UI typography — 2026-09-23

User-approved `ui.font.family` / `ui.font.size` settings update chrome and plugin controls live,
independently of terminal `[font]`. Omitted settings preserve platform defaults; sizes accept 8–32.
Relative headings, palette/status labels, tabs and titles follow updates. Remote headings refresh
in existing panels and progress content grows to fit. Missing families fall back to the platform.
`./gradlew :jasper-app:test :jasper-plugin-remote:test` passed, followed by the complete check below.
Large and fractional sizes use exact point values; live palette rows and secondary sections resize
with the UI font, and host-star geometry follows its label metrics.

### Centered connection overlay — 2026-09-23

Completed the approved [overlay plan](superpowers/plans/2026-09-22-jasper-connection-overlay.md)
natively in this worktree. SDK 0.7.3 adds `Windows.overlay(OverlaySpec)` with app/testkit ownership
parity. Remote progress stays centered inside its owner with an explicit Cancel button, Retry/Close
on failure and no Escape/outside dismissal. Duplicate requests focus the existing attempt. Native
trust/Vault prompts stay usable, and owner/plugin closure cancels pending work. Bounds, scrolling,
focus restoration and keyboard/native-menu containment have headless regression coverage.

One independent review found five issues (native menu bypass, cached palette geometry, content
revalidation, border-induced scrollbars and scaled font offsets). All are fixed and re-reviewed;
no findings remain. Follow-up fractional-font verification also caught and fixed FlatLaf’s button
styling rounding. Native GUI acceptance remains user-run; app-themed headless renders at default
and 18-point UI size were inspected. No GUI was launched.

`./gradlew check :jasper-app:installDist` passed: **1,634 tests, 1,631 passed, three expected skips,
zero failures/errors**; Remote **70/70**. Distribution rebuilt with the updated app, SDK and plugins.
No merge or push.

### Overlay scrollbar correction — 2026-09-23

The user reported scrollbars in native connection progress and explicitly rejected any scrolling
fallback. Removed the host's JScrollPane completely: a plain bordered panel now sizes to its
content and lays it out directly. This supersedes the overlay plan's constrained-window scrolling
fallback. No SDK or Remote connection lifecycle changes.

`./gradlew :jasper-app:test --tests '*WindowOverlay*' :jasper-app:installDist` passed: six overlay
layout/focus tests, zero failures. The regression verifies no host scroll widgets even in a
constrained root, fitted content bounds, and natural relayout. An actual app-themed headless
ConnectionPanel probe confirmed that all visible labels/buttons fit at 12, 14, 18, 24 and 32 points
in progress and failure states. Default/18-point previews inspected. Distribution rebuilt; no native
GUI launched, merge or push.

### Remote Sessions toolbar — 2026-09-23

User-approved bounded follow-up, implemented natively in the same worktree. Remote contributes
**Sessions** through the existing SDK dropdown, listing saved hosts alphabetically by name with
their addresses. Selection focuses the last used running session or starts a connection in the
invoking window. Host additions, edits, imports and removals refresh the menu; invocation resolves
the current host record. **Manage Sessions...** shows the Hosts panel without hiding it if already
visible, and remains available with no hosts. Chrome and popup use the existing app typography.

Tests first failed on the missing toolbar/actions, then passed. The bundled-app integration test
now verifies both the sample button and the new Sessions dropdown. Independent read-only review
found no actionable issues. `./gradlew check :jasper-app:installDist` passed: **1,636 tests,
1,633 passed, three expected skips, zero failures/errors**; Remote **71/71**. Distribution rebuilt.
No GUI launched; native appearance remains user acceptance. No merge or push.

### Remote View menu entry — 2026-09-23

Registered the existing SSH Hosts toggle action under **View > SSH Hosts**, so it shows or hides
the panel in the invoking window. The existing **Cmd+Shift+H** (Ctrl+Shift+H elsewhere) binding
already opens the palette directly in the SSH scope; no additional shortcut or action is needed.
`./gradlew :jasper-plugin-remote:test --tests '*RemotePluginTest' :jasper-app:installDist` passed:
ten plugin integration tests, zero failures. Distribution rebuilt; no GUI launched, merge or push.

### Terminal resize and local startup follow-up — 2026-09-23

Reproduced prompt fragmentation headlessly with a clean zsh (`-dfi`, no user startup files): a
burst of immediate grid changes interleaves prompt redraws with later reflows. `TerminalView`
now coalesces layout resizes for 120 ms, then changes the emulator and shell together. Zero-area
layout no longer collapses the grid. Pending changes cancel on removal and refit on reattachment;
explicit session resizes and font changes retain their immediate behavior. View-driven clean-zsh
probes at burst and 10 ms resize intervals showed one intact prompt after narrowing/widening.

Three regressions failed before the fix and pass afterward. Independent review found no production
issues; strengthened its identified detach-cancellation test gap. `./gradlew check :jasper-app:installDist`
passed: **1,639 tests, 1,636 passed, three expected skips, zero failures/errors**. App rebuilt.
No native GUI launched, merge or push.

The local-only prompt startup delay was traced separately to a user shell startup block appending
Homebrew initialization on every launch. After explicit permission, backed up both user zsh files,
removed 191 duplicate profile commands and stopped further appending; other configuration is
preserved. Isolated profile evaluation measured 1.848 s before and 0.014 s after (not whole shell
startup). Both files pass zsh syntax checks. No user configuration is tracked in this repository.

### Remote configurable navigation shortcuts — 2026-09-23

Implemented the user-approved `[shortcuts]` table in `dev.jasper.remote.toml`: `toggle_panel`
defaults to `cmd+shift+s`, `open_palette` to `cmd+shift+h`. Missing values use defaults; blank
strings disable the plugin preference. Reloads replace only changed action registrations under
stable ids, refreshing shortcuts in existing windows while preserving menu/status placements.
App-level `[keybindings]` overrides retain priority. No SDK extension or plugin restart required.

Two real staged-plugin/TOML integration tests failed first, then passed: defaults, live changes,
disable/reset, unrelated-settings stability, menu preservation, override precedence, and invalid
or colliding bindings. Independent review confirmed action/window behavior and caught an
inaccurate logging promise, now removed: automatic plugin reload leaves invalid/colliding bindings
unbound but does not necessarily log the resolution problem until an app reload/start.

`./gradlew check :jasper-app:installDist` passed: **1,641 tests, 1,638 passed, three expected skips,
zero failures/errors**. Rebuilt distribution again after the settings-template comment correction.
No GUI launched, merge or push.

### Remote sidebar keyboard entry — 2026-09-23

The user clarified this request targets the SSH sidebar, not the scoped command palette. Showing
the sidebar now highlights the first visible host (skipping headings/errors) and requests list
focus after mounting, so arrows and Enter work immediately. A hide before focus delivery cancels
the request. Late host loading/filter results select the first available host; ordinary refresh
preserves the selected host or folder. With all folders collapsed, the first folder is selected
so Enter can expand it, without changing saved collapse state on opening.

Independent review caught folder selection jumping to an unrelated host after collapse; fixed
with a failing-then-passing collapse/expand regression. Selection-on-show, reopening, Down/Enter,
late loading and refresh/filter cases are covered too. `./gradlew :jasper-plugin-remote:test
:jasper-app:installDist` passed: **74 Remote tests, zero failures**. Distribution rebuilt; native
focus acceptance remains user-run. No GUI launched, merge or push.

### macOS default shell refresh reverted — 2026-09-22

At the user's request, reverted `dddfc57` and its integration note `238769f`:
per-launch macOS account lookup caused noticeable shell-start latency in native use.
Shell selection again uses inherited `SHELL`, with the existing platform fallbacks;
there is no per-launch `dscl` query. Shell integration and launch scheduling are
restored to their previous behavior. The account reports `/bin/zsh`, while the
running apps inherited `/opt/homebrew/bin/bash`; the user plans to refresh the login
environment by rebooting. No system settings changed and no GUI launched.

Verification: code and tests match `75dd8a3` exactly; only this status note differs.
`./gradlew check :jasper-app:installDist -q` passed on retry: 1,546 tests,
1,543 passed and three expected skips. The first run hit an unchanged test race in
`AttachedSessionTest.shellIntegrationWorksButNeverReportsALocalDirectory` at line 74:
streaming a synchronized list during concurrent writes throws
`ConcurrentModificationException`. No terminal code/test changes were made; that
intermittent test issue remains deferred. The revert is integrated into local `main`;
no push or replacement of the running installed application.

### Plugin home — 2026-09-22

Merged at `17c0476` (not pushed). The work implements the
[plugin-home plan](superpowers/plans/2026-09-22-jasper-plugin-home-plan.md) of the
[plugin-home design](superpowers/specs/2026-09-22-jasper-plugin-home-design.md): everything about a
plugin lives under `<home>/plugins/<id>/` (`jars/` for an installed plugin, `<id>.toml` settings,
`data/`), for bundled and development plugins too; `plugin-data/` is gone and migrated once, as are
flat `plugins/<id>/*.jar` layouts. A zip dropped into `plugins/` is staged at the next launch without
consent (an unusable one becomes `.rejected`); an update replaces `jars/` only; Remove… takes the
whole folder after a confirmation. Each plugin's settings file is the only source of its settings:
seeded from an old `[plugins."<id>"]` table (kept for that and reported as moved), else the jar's
`settings.toml` (History and the sample ship one), else a header; `PluginSettingsFiles` polls the
files once a second and a broken file keeps its last good values, reported under `plugins.<id>`.
The SDK's `PluginConfig` gained `file()` (0.7.0); the testkit mirrors the layout. The manager's
rows carry the three paths; its list has a context menu (the row's actions, then Open Settings, Open
Plugin Folder, Open Data Folder) whose openers go through `ConfigEditor`.

Scope decisions, as recorded at the top of the plan: `start(tables, dark)` and
`configurationChanged(tables)` keep their signatures (tables seed only); `plugins.toml` and
`plugins.lock` stay files beside `config.toml`; a drop-in zip records no consent; folders are
prepared in `PluginRuntime.start`; settings files are polled, not watched; a parse failure's report
is sticky until the next configuration snapshot (`ConfigurationController.report` keeps reports until
then); `ConfirmView` is its own small view; the fake's data layout is `<root>/<id>/data`.

Deviations from the plan text: `PluginDiscovery.scan` skips jar-less folders quietly (bundled and
development plugins' settings-and-data folders) rather than reporting them, an addition the plan's
migration test needed; `PluginSettings`' nested `View` also implements `file()`; the plugin-tables
tests and `PluginInstallerTest` were extended for the moved warning and the `jars/` path; the
manager test seeds `plugins.toml` as text because `PluginStateStore` is package-private. No
behaviour deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,433 tests: 1,431 passed, two expected environment skips, no failures or
errors** (app 766, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10, History plugin
40, Snippets plugin 11). Native acceptance (the five steps at the end of the plan) is pending and is
the user's.

### Credential Vault plan 6a — 2026-09-22

Integrated into local `main` at `c8141b6` (not pushed): the bundled `dev.jasper.vault` plugin's
core and API from the [vault design](superpowers/specs/2026-09-22-jasper-vault-design.md) per the
[6a plan](superpowers/plans/2026-09-22-jasper-vault-plan-6a-core-and-api.md). A device-bound
Argon2id/AES-256-GCM file with a binary plaintext that never becomes a `String`; the device secret in
the platform keychain tool with a 0600-file fallback; a lock manager with inactivity auto-lock; an
OpenSSH key generator; `VaultApi` published per consumer with once/always/deny grants stored in the
vault, one prompt per request kind, and cancellation that withdraws a request. UI so far: the create,
unlock, grant and picker dialogs, `Open Vault...` (F8) and `Lock Vault`. Plan 6b adds the manager
window, status item, rail action, key generator dialog and the Vault palette scope.

### Vault layout and TermLab theme — 2026-09-22

The manager lock action now uses an outlined padlock with a rounded body and centered
keyhole, matching the rail icon's visual language. Verified with a headless manager render,
the manager panel tests and `:jasper-app:installDist`.

Latest follow-up in `.worktrees/vault-6b`: the user's reference screenshots and local TermLab
source supersede the earlier split-pane manager and button-style iterations. The manager now has
one searchable, type-filtered list with two-line names/details and trailing type labels; compact
icons provide add/edit/delete/copy-public-key/lock. The + menu includes import and generation;
More includes grants and master-password changes. Double-click/Enter edits, palette navigation
reveals filtered-out entries, and lock clears the list and dialogs. Cancel/Okay close the manager;
individual editor saves remain immediate. Device storage source, auto-lock and file path stay in
the footer. Secret notes are displayed only in their editor, not a retained manager preview.

`TermLab/core/resources/themes/TermLabDark.theme.json`, `TermLabLight.theme.json` and its
`plugins/vault/.../VaultDialog.java` supplied the palette and row layout. Ordinary Swing buttons
use 24-pixel minimum height, 72-pixel minimum text-button width, 12-point labels, gray secondary
fills and blue default buttons. Editors align actions right and attach/release their default
button with the form lifetime. The terminal toolbar keeps its independent geometry. The dark
secondary button text is slightly lighter (#A7AEBB instead of #A0A7B4), preserving Jasper's existing
4.5:1 contrast guard; disabled text keeps its tested defaults. No JetBrains platform dependency,
SDK change, credential-type addition or vault format change. [Authoring guidance](plugin-authoring.md#consistent-buttons-flexible-layouts).

`./gradlew check :jasper-app:installDist -q` passed: **1,546 tests, 1,543 passed, three expected
skips, no failures/errors**. Independent review found two P2 issues (visible security status and
row accessible names), both fixed. Tests cover filtering, selection, palette reveal, menu actions,
lock cleanup, default-button lifetime and theme state. Headless dark/light manager, empty-state,
login and generator renders inspected; native appearance remains user acceptance. Not merged or pushed.

### Native file browsing — 2026-09-22

Follow-up to vault 6b in `.worktrees/vault-6b`: all editable key-path fields now have inline
**Browse...** buttons (SSH private/public files and login private keys). The native chooser is
owned by the editor dialog, starts from the current path, and preserves text on cancellation.
Closing or locking the vault while choosing discards any late result. Manual entry stays available.

SDK 0.7.1 adds `WindowSurface.chooseFile(title, initialPath)` for windows and dialogs; the vault
requires that version. The app owns native selection and disposal; the fake host queues responses.
The existing plugin ZIP picker shares the same native implementation. This is an additive extension
to the 6b plan; no vault format or consumer API change. `./gradlew check :jasper-app:installDist -q`
passed: **1,542 tests, 1,539 passed, three expected skips, no failures/errors**. Independent
review found only Windows test path assumptions, corrected before the final check. Headless
form rendering and source hygiene passed; native chooser acceptance remains user-run.

### Credential Vault plan 6b continuation — 2026-09-22

Implemented in `.worktrees/vault-6b` on `claude/vault-6b`, from `c8141b6`; not merged or pushed.
The resumed handoff held an uncommitted draft with only palette/status tasks. The completed
[6b plan](superpowers/plans/2026-09-22-jasper-vault-plan-6b-ui.md) now covers the palette,
self-clearing clipboard, padlock/rail, persisted manager operations, account/key/note editors,
master-password change, singleton manager, grant revocation and key generation with an optional
login account. [User guide and native acceptance](credential-vault.md).

**Resolved user decision:** the user chose passphrase encryption on 2026-09-22. The generator
now accepts and confirms an optional passphrase. Nonempty values encrypt every supported algorithm
in OpenSSH v1 format using bcrypt_pbkdf (24 rounds, fresh 16-byte salt) and AES-256-CTR; empty
fields retain the existing unencrypted output. An optional generated login stores its own
passphrase copy in the encrypted vault; standalone key records retain only paths/metadata.
Owned request, UTF-8, private-key encoding and derived-key arrays are cleared. No new dependency,
production process invocation, SDK/consumer API change or vault storage-format change.
Native acceptance and the opt-in real-keychain test remain user-run.

Execution: Task 1 used a subagent and independent reviewer, with one RED/GREEN fix round for
clipboard failure preventing shutdown locking. Tasks 2 onward ran inline; final whole-branch
review found two password-change issues; both are fixed in `a06dcb8` with RED/GREEN
regressions. Failed rekey writes retain the old key; queued saves use the resulting key. Closing
before commit cancels; after commit starts, the form says closing will not cancel and reports
write failures even after closure. Each task has its own commit and failing-before-passing tests. Baseline
`check` was 1,483 tests (1,480 passed, three expected skips). Verification on `a06dcb8`: `./gradlew check :jasper-app:installDist -q` passed with
**1,526 tests: 1,523 passed, three expected skips, no failures/errors**. Vault: 93 tests,
92 passed and the opt-in keychain test skipped. The installed vault jar contains the manager,
generator, scope and icons beside its BouncyCastle dependency. Source hygiene and diff checks pass.
Initial full checks caught eager clipboard access at plugin startup and old sample-only rail/status
assertions; a RED/GREEN default-start regression and updated bundled-plugin expectations cover them.

Deviations/rulings: both username and password copies expire per the spec; locked palette rows
remain enabled for Enter; the inherited branch/worktree was retained. A small 6a prerequisite
(`737f90c`) rejects/zeroes late create/unlock results after lock, including stop; prompt cancellation
also invalidates in-flight derivation. Wipeable Swing documents clear editable buffers and avoid
secret Strings in persistence; Swing's required Content.getString is the explicit UI boundary.
Generation failures remove created files. Headless dark-theme renders prompted GridBag forms,
readable kind labels and hiding the unused note area. No SDK, consumer API or vault storage-format change.
The independent review found no other actionable issues. Native behavior and a fresh audit of
unchanged 6a cryptography/platform storage remain outside this headless UI review; the two new
UI-reachable core failures were fixed. No deferred minor findings. The key-generation decision is implemented; native acceptance remains user-run; this branch and its execution ledger are retained.

Passphrase-extension verification: all five encrypted algorithms are independently readable by
`ssh-keygen -y` with the exact synthetic Unicode/whitespace passphrase and match their generated
public keys. BouncyCastle rejects absent/wrong passphrases. Tests also cover confirmation, UI/request
wiping, stored account ownership after lock/reopen, rejection, failed-save cleanup and lock-time
cleanup. Final `./gradlew check :jasper-app:installDist -q` passed with **1,534 tests: 1,531
passed, three expected skips, zero failures/errors** (vault: 101 total, 100 passed, one opt-in
keychain skip). The scoped independent review found no actionable issues. The generator was
rendered headlessly and inspected; source hygiene, diff checks and installed encrypted-encoder
packaging passed. The design decision is fully implemented; no merge/push or native launch.

### Vault native acceptance: shared chrome corrections — 2026-09-22

The user's native screenshots exposed three app-provider issues during vault acceptance. An
opaque unused tab panel covered auxiliary title bars outside the traffic-light region; title-only
mode now hides it. SDK dialogs already use `NativeShells`, but that path had skipped the app title
bar; frames and dialogs now share title setup, and JBR's Frame/Dialog overloads provide the same
custom chrome. Dialog ownership/modality, close guards, dynamic titles, activation, theme updates
and disposal remain app-owned. The SDK contract already supplies this surface; no API extension
was needed. This also fixes the Plugins manager and all other app-provided auxiliary surfaces.

The toolbar and rail now use `Jasper.titleBackground` in light and dark themes. Settings and Reload
config were removed from the toolbar; Settings remains in the rail, and both commands remain in
menus/palette. Regression tests reproduce the title-bar pixel boundary, verify both auxiliary
surface kinds, and cover theme colors, toolbar contents and plugin contributions. Full-check
failures identified two older screenshot/layout expectations, updated to the new color and five
buttons. Headless dark/light renders were inspected. Final `./gradlew check :jasper-app:installDist -q`
passed with **1,536 tests: 1,533 passed, three expected skips, no failures/errors**. The independent
review found no production-code regression; its two stale-test findings are resolved. Source
hygiene and diff checks passed. Native macOS rendering/dragging/modality remain a user-run visual
check; no GUI, merge or push was performed. Changes remain on `claude/vault-6b`.


Toolbar spacing follow-up: reduced the row from 53 to 42 logical pixels, leaving the 30-pixel
buttons and horizontal spacing intact. This gives 6 pixels of top/bottom padding and 11 more pixels
of terminal height. Updated existing geometry/pixel expectations; 15 relevant layout/chrome tests
and `:jasper-app:installDist` passed. No full-suite rerun for this isolated spacing change.


### macOS 26 window controls in the packaged app — 2026-09-22

The user's screenshots showed Jasper's stoplights in the pre-macOS-26 flat style while TermLab
(Tauri) had the new controls. Cause, proven by a spike with a plain Swing frame and no shell: AppKit
draws the macOS 26 controls only for a process whose main executable records a macOS 26 or later SDK
in its `LC_BUILD_VERSION`; JBR 25.0.4.1's `java` and jpackage's launcher stub both record SDK 13.3.
Rewriting that load command on a copy of `java` with `xcrun vtool -set-build-version macos 11.0 27.0
-replace` and re-signing gave the new controls with no other change. The jpackage launcher is
self-contained (links only Cocoa, libc++ and libSystem; parses `Jasper.cfg` itself), so no C stub is
needed. `packageApp` now runs that rewrite on `Jasper.app/Contents/MacOS/Jasper` after jpackage and
re-signs the bundle ad hoc with preserved metadata; `verifyPackage` asserts the launcher records the
installed SDK and warns below 26. Without `xcrun` both steps log and skip. `./gradlew :jasper-app:run`
keeps the legacy controls because it runs the stock `java`. See [packaging](packaging.md#macos-26-window-controls).
Branch `claude/termlab-tabs`, worktree `.worktrees/termlab-tabs`.

The wider design this came out of, TermLab-style tabs under native title bars on every platform
(`superpowers/specs/2026-09-22-jasper-termlab-tabs-native-titlebar-design.md`), is approved but
deferred: the user chose the launcher fix alone once it was clear the controls do not depend on the
tab rework. The spec's banner records the narrowing; no plan was written for sections 3 to 5.

### Terminal dark palette — 2026-09-22

`Palette.jasperDark()` now carries the TermLab Dark palette ported from the conch project's
xterm.js theme (Dracula-derived: background `#282a36`, foreground `#f8f8f2`, selection `#44475a`).
Only the terminal palette changed; the dark FlatLaf chrome keeps its own values, and
`TabbedPane.selectedBackground` in `FlatDarkLaf.properties` is pinned to the new terminal
background so selected tabs still match the terminal. Branch `claude/termlab-dark-palette`.

### Palette plugins plan 5b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-22 at `0550bad` (not pushed). The work implements
[plan 5b](superpowers/plans/2026-09-21-jasper-palette-plugins-plan-5b-history-snippets-plugins.md):
the History and Snippets palette scopes are now two bundled plugins. `plugins/snippets`
(`dev.jasper.snippets`, bundling tomlj) carries the snippet value, `snippets.toml` format and store
and the Snippets scope, publishes `dev.jasper.snippets.api.SnippetService`, and moves a pre-plugin
`<home>/snippets.toml` into its data directory once. `plugins/history` (`dev.jasper.history`) carries
the five shell-history parsers, the polling index, discovery and the History scope; it captures live
commands from `COMMAND_FINISHED` with `PaneInfo.shell`, reads `trivial_commands` and
`deprioritize_trivial` from `[plugins."dev.jasper.history"]`, and shows "Save as snippet…" only when
the service is present (`requires … optional = true`, the Vault/SSH shape). The application lost
`palette.builtin.{ShellHistoryScope,SnippetsScope}`, the `snippets` and shell-history code,
`HistorySettings`, `ConfigSnapshot.historyEnabled/trivialCommands`, `ActionId.HISTORY_PALETTE/SNIPPETS_PALETTE`
and every wiring arm (3,473 lines); `CommandsScope` moved into `palette`, `CommandHistory` into
`commands`, `PaletteContext` lost `trivialCommands` and `PaletteTarget` lost `shellName`.
`ConfigLoader` reports `[palette.scopes.history]` as moved. See
[command palette](command-palette.md), [configuration](configuration.md#shell-history) and
[plugin authoring](plugin-authoring.md#palette-scopes).

Scope decisions, as recorded at the top of the plan:

1. The legacy snippets file is found relative to the plugin's data directory (the home is its
   grandparent), not passed through configuration.
2. The Snippets plugin bundles tomlj; `stagePlugins` copies each plugin's runtime classpath.
3. `ConfigSnapshot` loses `historyEnabled` and `trivialCommands`; the old table is reported as moved.
4. The two scope tests were rewritten against `FakePluginHost`; the rest moved with their code.
5. `CommandPalettePreview` lost its History and Snippets scenarios.
6. "Edit file" goes through `platform().openInEditor`.
7. `KeyBindings` keeps only `COMMAND_PALETTE` and `CLEAR_SCROLLBACK` special.
8. `PluginConfig.stringList` cannot tell absent from empty, so `deprioritize_trivial = false` replaces
   `trivial_commands = []`.

Deviations from the plan text: `FakePluginHost(Path dataRoot)` was added to the testkit (the plan
anticipated it); the View menu separator index moved with the two removed items;
`CommandHistoryFile` became public for one app test; `config.example.toml` gained the plugin's
table in place of `[palette.scopes.history]`; the plan's test for the Snippets error row needed a
TOML syntax error rather than a missing key, and its migration test uses a consumer plugin to reach
the service (a plugin cannot `find` its own publication). No behaviour deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,420 tests: 1,418 passed, two expected environment skips, no failures or
errors** (app 753, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10, History plugin
40, Snippets plugin 11). The installed image's `lib/plugins/` holds all three bundled plugins, the
Snippets one with tomlj and its antlr runtime. Native acceptance (the five steps at the end of the
plan) is pending and is the user's.

### Palette plugins plan 5a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `3eb143a` (not pushed). The work implements
[plan 5a](superpowers/plans/2026-09-21-jasper-palette-plugins-plan-5a-contribution-surface.md) of
the [palette-plugins design](superpowers/specs/2026-09-21-jasper-palette-plugins-design.md): a plugin
can contribute a command-palette scope, open the palette, see a pane's shell label, show an error
notice and open a file in the user's editor. The SDK is 0.6.0: new package `dev.jasper.sdk.palette`
(`Palette`, `PaletteScope`, `ScopeSpec`, `PaletteVerb`, `PaletteRow`, `PaletteResults`,
`PaletteStep`, `PaletteQuery`), `ui.Notices`, `ui.Platform`, `PaneInfo.shell`, capability
`palette.contribute`, and `PluginContext.palette()/notices()/platform()`. The app's `Contributions`
model carries scopes and palette requests; `WindowContributions` registers every contributed scope in
its window and routes requests; `PaletteKeyRouter` treats a contributed scope's shortcut like a
built-in one while the palette is open; `plugins.HostedPalette` adapts a scope with contained calls
and hands the plugin its own rows back; `PaneSnapshot.shell` feeds `PaneInfo.shell`. The testkit
gained `FakePalette` and nine host accessors; the contract suite is 35 cases. The sample plugin
contributes a "Greetings" scope behind `demo_scope`. The application's own History and Snippets
scopes are untouched. See [SDK architecture](sdk-architecture.md#palette-scopes) and
[plugin authoring](plugin-authoring.md#palette-scopes).

Scope decisions, as recorded at the top of the plan:

1. `PaneInfo.shell` and `PaneSnapshot.shell` are last components, so existing constructor calls
   grew by one appended argument.
2. `PaletteTarget` carries the window and pane ids; a windowless target (fixtures only) yields no
   rows from a contributed scope.
3. A contributed scope's shortcut is intercepted only while the palette is open; closed, the plugin's
   own action handler runs and calls `Palette.open`.
4. The adapter keeps the plugin's row as the app row's token.
5. `Palette.open` on the showing scope dismisses and applies neither query nor row.
6. `Notices.error` goes to the last active window's `onError` (a dialog today), or the log;
   `Platform.openInEditor` runs the app's `ConfigEditor` on the plugin's executor and reports
   failure as a notice.
7. The contract's `addTerminalPane` keeps its signature; both harnesses create `zsh` panes.

Deviations from the plan text: three existing tests pinned the capability list
(`TerminalValuesTest`, `DescriptorParserTest`) or built a `PaneInfo` the plan's grep missed
(`FakeContractTest`); each expectation was extended. The SDK's one-line Javadocs were rewritten as
multi-line comments to satisfy doclint. No code deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,435 tests: 1,433 passed, two expected environment skips, no failures or
errors** (app 819, Buddy 163, terminal 377, SDK 21, testkit 45, sample plugin 10). Native acceptance
(the five steps at the end of the plan, driven by the sample plugin's `demo_scope`) is pending and is
the user's.

### Official Ember application icon — 2026-09-21

Implemented on `codex/ember-app-icon` in a dedicated worktree; local integration
was authorized by the user. No push was performed. The approved Ember pack (faceted white turtle,
warm gradient, no prompt) is the official icon for all platforms. macOS ICNS,
Windows ICO, all runtime PNGs, and Linux PNG/hicolor assets use that artwork.
Linux windows now select their own resource set and portable distributions carry
the hicolor hierarchy. Existing native installer support remains macOS/Windows.

The approved raster source and a reproducible Core Graphics generator replace the
old SVG app-icon pipeline. Buddy retains its silver character reference and sprites.
See [the icon guide](../packaging/icons/README.md) for provenance, sizes and generation.
Verification: `./gradlew check :jasper-app:installDist :jasper-app:packageDist`
passed after integrating main's development-home change with **1,437 tests: 1,435
passed, two expected skips, no failures or errors** (app 821, Buddy 163, terminal
377, SDK 21, testkit 45, sample plugin 10). The macOS
app/DMG passed native verification, including exact ICNS bytes in the bundle.
All runtime PNGs and ICNS/ICO containers match the approved Ember preview pack
byte-for-byte. All ten Linux hicolor sizes match their portable-distribution
copies. Windows and Linux native desktop checks remain unrun. No Jasper GUI or
benchmark was launched.

### Plugin SDK plan 4c — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `9fbdc49` (not pushed). The work implements
[plan 4c](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4c-directory-provenance.md): a
directory reported under another host name is no longer treated as a local path.
`internal.shell.DirectoryProvenance` classifies every OSC 7 report; `ShellCommandTracker` keeps one
of local and remote and clears the other; `jasper-terminal` gained `session.RemoteDirectory` (the
only addition to the app-facing allowlist), `TerminalSession.remoteDirectory()`,
`TerminalSessionListener.remoteDirectoryChanged` and `SessionLaunchOptions.localHostNames`. The app
resolves the machine's names once, off the EDT, in `launch.LocalHostNames`, carries the remote
directory through `terminals.RemoteLocation`, `PaneSnapshot` and `TerminalEvent`, and shows
`host:path` in the status bar. SDK `PaneInfo.remoteDirectory`, `CWD_CHANGED` and `COMMAND_FINISHED`
now carry it; the SDK is 0.5.1; the testkit gained `remoteCwdChanged`. See
[terminal architecture](terminal-architecture.md#working-directory-provenance).

Scope decisions, as recorded at the top of the plan:

1. A report with no host, or with `localhost`, is local in a local session even when the machine's
   names could not be resolved; the spec's "unresolved names mean remote" applies to reports that
   name a host.
2. The local names are what `hostname` prints plus the `HOSTNAME` and `COMPUTERNAME` variables,
   which is what the bundled integration scripts report. `InetAddress.getLocalHost()` is not used.
3. `commandExecuted` keeps its signature; the app reads `session.remoteDirectory()` inside the
   callback on the reader thread.
4. An attached session now reports remote directories; plan 4b's suppression wrapper is gone.
5. The status bar shows `host:path` for a remote directory; tab titles keep falling back to the
   local directory.
6. SDK `RemoteDirectory` accepts an empty host (the program named none); the sample's range
   `>=0.5, <0.6` is unchanged.
7. A stronger locality signal, such as a per-session token from Jasper's own integration, stays
   deferred, as in the spec.

This changes local sessions: a local pane in which the user runs `ssh` by hand reports no local
directory while the remote shell reports its own, so New Tab and Split start where the local shell
was. The classification is best effort: OSC 7 is unauthenticated text.

Deviation from the plan text: Task 0's separate baseline `check` was skipped, because the branch
starts at the main commit verified minutes earlier. No code deviations.

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,415 tests: 1,413 passed, two expected environment skips, no failures or
errors** (app 810, Buddy 163, terminal 377, SDK 18, testkit 38, sample plugin 9). The contract
suite is 30 cases, run against both the testkit and the application. Native acceptance (the five
steps at the end of the plan) is pending and is the user's: step 5 matters most, because a machine
whose `hostname` differs from what its shell reports would see local directories classified as
remote.

### Plugin SDK plan 4b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `5f21a0e` (not pushed); the merged
result passed the same verification. The work implements
[plan 4b](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4b-plugin-sessions.md): a plugin opens
a pane whose session it provides. `jasper-terminal` gained `TerminalSession.attach(AttachedConnection,
GridSize, scrollback)`, the only addition to the app-facing allowlist, over a JDK-only
`internal.transport.AttachedTransport` (one writer thread behind a 4 MiB queue, whole-write
rejection reported through `TerminalSessionListener.inputDropped`, coalesced and ordered resizes, a
two-second drain after exit, first failure wins, `close` exactly once) and a thin
`internal.emulation.AttachedConnector`, so attached output runs through the same shell-integration
chain as a PTY. The app gained `terminals.{OpenSpec, SessionRequest, SessionAttempt}`, pending and
disconnected pane states with Reconnect, `plugins.CleanupWorker` and `HostedSessions`; the SDK
gained `SessionSpec`, `PendingSession`, `TerminalConnection`, `ExitPolicy` and
`OpenRequest.session` behind `session.provide`, and is 0.5.0; the testkit gained `FakeSessions`;
the bundled sample provides a loopback echo session. The local PTY path is unchanged. See
[terminal architecture](terminal-architecture.md#attached-sessions) and
[SDK architecture](sdk-architecture.md#provided-sessions).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,403 tests: 1,401 passed, two expected environment skips, no failures or
errors** (app 806, Buddy 163, terminal 370, SDK 18, testkit 37, sample plugin 9). The contract
suite grew from 23 to 29 cases and passes for the testkit fake and for the application. The new
threading tests (`AttachedTransportTest`, `AttachedSessionTest`, `SessionPaneTest`,
`AppContractTest`) were each rerun three to five times without a failure. Native acceptance (the
eight-step checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: working-directory provenance (classifying OSC 7 reports by
host, `RemoteDirectory`, the machine's local names in `SessionLaunchOptions`) moved to a small
plan 4c, and 4b implements the one rule it needs, that an attached session never reports a local
working directory; an attached session writes no exit line into the buffer; an unknown exit status
or a transport failure is an exceptionally completed `exitFuture()`; Reconnect starts a fresh
session and view in the same pane and does not carry scrollback over; cancelling closes the pane
only when no session was ever shown in it, and otherwise returns to the disconnected bar; the
attempt's grid is the window's configured initial grid; `SessionSpec.icon` is accepted and unused;
a user's split of a provided pane opens a local shell at home; and the SDK version is 0.5.0.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch starts at the commit local `main` had just been verified at. `AttachedTransportTest`'s cut-off case accepts end of stream as well as an `IOException`: the fixture's `close` ends the pipe cleanly, and both are valid ways for the blocked read to end. `AttachedSessionTest` feeds shell-integration marks in the form the module's own tests use (BEL terminators, the command line ended by CR LF, a following prompt) instead of the ST-terminated sequence in the plan. In `TerminalPane` the `noticeTransient` field is declared before the timer whose initializer reads it; the plan's order was an illegal forward reference. `FakePluginHost.terminalPanes()` was added in Task 6 with the other drivers rather than in Task 8. `HostedTerminals` got a small `capability(OpenRequest)` helper for the audit line, and `plugins/package-info.java` lists `dev.jasper.terminal.session`. The terminal module's allowlist is documented in `jasper-terminal/README.md`'s package table; `docs/terminal-architecture.md` gained the "Attached sessions" section before "Threads and failures".

### Plugin SDK plan 4a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `2eee9c7` (not pushed); the merged
result passed the same verification. The work implements
[plan 4a](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-4a-terminal-api.md): plugins find the
active window, tab and pane through `context.terminals()`, read pane metadata, follow sixteen
`TerminalEvents` topics, type and paste into a pane, read the selection, and open local tabs and
splits, each behind the capability the user consented to (`MissingCapabilityException` otherwise,
with an audit log that never contains content). New app package `dev.jasper.app.terminals`
(`TerminalRegistry`: pull-based window, tab and pane entries, id-only events, the derived active
pane); `workspace.WindowTerminals`; `plugins.CapabilityGate`, `HostedTerminals` and
`TerminalBridge`; the testkit's `FakeWorkspace` and `FakeTerminals`. The SDK is 0.4.0 and the
bundled sample declares `terminal.observe` and `terminal.inject`. `jasper-terminal` is untouched.
See [SDK architecture](sdk-architecture.md#terminal-api).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,359 tests: 1,357 passed, two expected environment skips, no failures or
errors** (app 787, Buddy 163, terminal 355, SDK 16, testkit 30, sample plugin 8). The contract
suite grew from 17 to 23 cases and passes for the testkit fake and for the application. Native
acceptance (the seven-step checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: the spec's plan 4 is split into 4a (this) and 4b
(`TerminalSession.attach` with the app-owned writer, drain and exactly-once `close`,
`PendingSession`, pending and disconnected pane states, Reconnect, the cleanup worker,
`OpenRequest.session`, and working-directory provenance; the only part that touches
`jasper-terminal`); event payloads carry ids rather than handles, because a handle is bound to the
plugin that obtained it; `openTab` and `split` return `Optional<PaneHandle>`; `LocalSpec` carries
only a working directory; `terminal.observe` gates `info()`, `foregroundJob()`, `TabHandle.title()`
and every `jasper.terminal.*` topic, while identity, structure and visible navigation need no
capability; injection, selection reads, opens and a plugin's first terminal subscription are
audited at INFO, metadata queries are not; the 4b shapes (`remoteDirectory`, `SessionKind.PLUGIN`,
`CONNECTING`) are already in the SDK and unused; `foregroundJob()` completes on a worker thread;
and the SDK version is 0.4.0.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch starts at the commit local `main` was fast-forwarded to, and that merged result had just passed the full verification. `MissingCapabilityException`'s two private fields carry Javadoc comments, because doclint warns about uncommented fields of a serializable class. `TerminalBridgeTest` builds `new Containment(() -> true)`; the plan assumed a no-argument constructor and said to follow `EventBusTest` otherwise. `AppContractTest` needed `import java.util.UUID`, which the plan's harness code assumed. The testkit's `openRequests()` also lists `front|<window id>` lines for `toFront()`, documented in its Javadoc; both contract harnesses filter them out, so the shared format is unchanged. Task 6's second harness block repeated what Task 4 had already put into `AppContractTest.newHarness()` and was not applied twice. Every compiled example in `docs/plugin-authoring.md` was re-copied from the sample source, not only the new one: `start()` gained the `demo_terminal` line, so the `plugin` example changed too.

### Plugin SDK plan 3b — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `4cf8b3a` (not pushed); the merged
result passed the same verification. The work implements
[plan 3b](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-3b-manager-install-restart.md):
the application-owned Plugins manager (File → Manage Plugins…, action `plugins.manage`) that lists
every plugin with state, reason, capabilities, requirements and error count; enables and disables
plugins; reviews capabilities and records consent; installs a plugin from a zip and removes one;
and restarts Jasper to apply the change, including safe mode's Restart Normally, which retires a
resident process first. New packages: `dev.jasper.app.pluginmanager` and the leaf
`dev.jasper.app.restart`; new runtime classes `PluginMaintenance`, `PluginInstaller`,
`PluginCatalog` and `PluginAdmin`; `PluginRuntime` gained the public records `Row`, `Snapshot`,
`Inspection` and `Outcome`. The SDK is unchanged at 0.3.0. See
[SDK architecture](sdk-architecture.md#plugins-manager-install-and-restart).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,326 tests: 1,324 passed, two expected environment skips, no failures or
errors** (app 768, Buddy 163, terminal 355, SDK 12, testkit 22, sample plugin 6).
`RestartCommandTest.theRunningJvmReportsItsOwnCommandLine` passes on the development Mac under
JBR 25: `ProcessHandle.info()` reports the process's own command and arguments, so Restart Now
can replay them. Native acceptance (the eleven-step checklist at the end of the plan) is pending:
agents do not launch the GUI, and no test spawns a Jasper process.

Scope decisions, recorded in the plan: the quit path has no running-session confirmation today,
so restart and retire call `quit()` as it is; installs are always staged in
`plugins/.pending/<id>/` and applied, like removals, by launch maintenance inside the state lock;
that maintenance runs on the main thread in the bootstrap, before the desktop starts; the manager
is app UI over `PluginRuntime`'s public records, and `PluginRuntime` stays the only public
top-level type of its package; `retire` is handoff protocol 1 with a sixth field, so an ordinary
open request is byte-identical and the stale-build upgrade path is untouched; waiting for the
resident means waiting until nothing answers on the socket, 30 seconds, cancelable; a replacement
spawned while process cleanup has not finished gets `--standalone`; a zip whose plugin needs
another SDK is rejected at install; an entry in `plugins.toml` means "reviewed", so an unreviewed
plugin offers only Review and Remove; zips hold jars at the root or in one folder, within fixed
limits; the manager does not open by itself in safe mode; `plugins.manage` is contributed by the
application before plugins start and has no default shortcut; and the sample plugin is unchanged.

Deviations from the plan text: Task 0's separate baseline `check` was not run: the branch started from the merged `main` that had just passed the full verification, with documentation-only changes on top. The new `LaunchRequestTest` case is `@DisabledOnOs(WINDOWS)`, as Task 5 allowed, because it asserts the exact encoding of a Unix path. In `docs/plugin-authoring.md` the obsolete passage that told users to consent by hand in `plugins.toml` was replaced by a pointer to File → Manage Plugins…, rather than kept above the new section. No production or test code differs from the plan text.

### Plugin SDK plan 3a — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 at `88fe4a0` (not pushed); the merged
result passed the same verification. The work implements
[plan 3a](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-3a-rail-panels-windows.md):
plugin panels in left, right and bottom regions toggled from a single left rail, rail action
buttons, and application-built plugin windows and dialogs, with panel placement, rail
visibility and window bounds remembered in `ui-state.toml`. The SDK is 0.3.0. New app packages
and types: `dev.jasper.app.windows` (`AuxiliarySurface`, `AuxiliaryWindows`, `NativeShells`),
`workspace.WorkspaceRegions` and `workspace.WindowRail`, `persistence.UiState`, and panels and
rail actions in the `contributions` model. See
[SDK architecture](sdk-architecture.md#panels-the-rail-and-plugin-windows).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist --rerun-tasks`
passed with **1,277 tests: 1,275 passed, two expected environment skips, no failures or
errors** (app 719, Buddy 163, terminal 355, SDK 12, testkit 22, sample plugin 6). The contract
suite grew from 14 to 17 cases and passes for the testkit fake and for the application.
Native acceptance (the checklist at the end of the plan) is pending: agents do not launch the GUI.

Scope decisions, recorded in the plan: the spec's plan 3 is split into 3a (this) and 3b (the
Plugins manager, zip install and consent, the restart banner, the retire request and "Restart
normally"); `WindowOwner` is an ordinary interface in `dev.jasper.sdk` because a sealed
interface cannot permit subtypes in other packages of the unnamed module; `onClosing` and
`onClosed` return a `Subscription`; regions have no header or close button; rail visibility
is app state in `ui-state.toml`, not a `config.toml` key; quit closes plugin windows without
consulting their closing guards, closing by hand consults them; `MacTitleBar` needed no new
mode; and the SDK version is 0.3.0 with the sample's range `>=0.3, <0.4`. The process now
stays alive while a plugin window is open, even with no terminal window and residency off.

Deviations from the plan text: `PanelAndWindowValuesTest` needed `import dev.jasper.sdk.WindowOwner`, which the plan's test omitted. `AuxiliaryWindowsTest`'s singleton case uses distinct ids for its singleton and non-singleton windows: a singleton request returns any open window with that id, so the plan's shared id made the expected count wrong. The headless split-pane fallback in Task 4 was not needed; exact pixel sizes pass headlessly. Test code reaches the headless `AuxiliaryWindows` through a new `AppContractTest.headlessWindows()` helper instead of repeating the construction, because `PluginRuntimeTest` has a parameter named `dev` that shadows the `dev.jasper` package in a qualified name. `JasperApplicationPluginsTest.bindingProblemsSeparateUnknownUserIdsFromDroppedPluginDefaults` now awaits termination after `quit`: shutdown writes `ui-state.toml` into the temporary home, which raced JUnit's deletion of that directory.

### Plugin SDK plan 2 — 2026-09-21

Merged into local `main` by fast-forward on 2026-09-21 (not pushed); the merged result passed
the same verification. The work implements
[plan 2](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-2-actions-chrome.md): plugin
actions that are palette commands with rebindable shortcuts, and their placements in the
toolbar, menu bar, terminal context menu and status bar, all drawn by the application.
`KeyBindings` is keyed by action id; the new app-native `dev.jasper.app.contributions` model
keeps SDK types confined to `dev.jasper.app.plugins`; the SDK is 0.2.0. See
[SDK architecture](sdk-architecture.md#chrome-contributions).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
passed with **1,246 tests: 1,244 passed, two expected environment skips, no failures or
errors** (app 695, Buddy 163, terminal 355, SDK 10, testkit 18, sample plugin 5). The
contract suite grew from 9 to 14 cases and passes for the testkit fake and for the application.

Scope decisions, recorded in the plan: window and pane handles carry identity only until plan
4; the sample plugin's UI is off unless `demo_ui = true`; status items are global; a plugin's
dropped default shortcut is logged rather than shown as a configuration warning. Deviations
from the plan text: an invalid shortcut for a namespaced (plugin) action id is a per-entry
warning instead of an error that resets every keybinding, because the plugin may not even be
installed (a pre-existing loader test exposed this); empty contributed status rows collapse at
the origin and rows are clamped inside the bar, because the first layout broke the status
bar's bounded-layout contract at very narrow widths; and `:jasper-app:test` now takes only
`plugins/sample/src` as an input, fixing a plan-1 wiring bug in which the whole `plugins/`
tree, including the sample's build outputs, was declared and Gradle rejected builds that ran
both projects' tasks.

Known limit: keybinding diagnostics are evaluated when plugins start and at each configuration
reload; an action registered later is rebound in windows at once but not re-evaluated for
diagnostics until the next reload. Native acceptance is pending and user-run; the checklist is
at the end of the plan.

### Plugin SDK plan 1 — 2026-09-21

Merged into local `main` by fast-forward through `13b9646` on 2026-09-21 (not pushed); the
merged result passed the same verification. The work implements
[plan 1](superpowers/plans/2026-09-21-jasper-plugin-sdk-plan-1-core-runtime.md) of the
[plugin SDK design](superpowers/specs/2026-09-21-jasper-plugin-sdk-design.md): the JDK-only
`jasper-sdk`, `jasper-sdk-testkit` with a contract suite that both the fake and the
application pass, the `dev.jasper.app.plugins` runtime, standalone `--safe-mode`,
`--plugin-dir` and `--standalone` launches, an EDT-independent exit deadline, the activity
bridge to Buddy and a bundled sample plugin. Start at [SDK architecture](sdk-architecture.md).

Verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture verifySdkArchitecture verifyPluginArchitecture check :jasper-app:installDist`
passed with **1,207 tests: 1,205 passed, two expected environment skips, no failures or
errors** (app 667, Buddy 163, terminal 355, SDK 8, testkit 11, sample plugin 3). Each new
architecture guard was shown to fail on a deliberate violation before being restored.

Deviations from the spec, all recorded in the plan: `PluginContext` carries only plan-1
accessors; the cleanup worker and `MissingCapabilityException` wait for plan 4; the runtime
is one package; activities reach Buddy without a threshold and do not drive its working
animation; the per-consumer service overload is `publishPerConsumer` (two `publish` overloads
are ambiguous for functional service interfaces). Deviations from the plan text: two tests
were corrected (`PluginTablesTest` expects the loader's quoted diagnostic keys;
`ConfigurationReportTest` builds `ConfigService` off the EDT, as production must), one racing
assertion in `PluginHostTest` now waits on a latch, and `installDist` also carries bundled
plugins in `lib/plugins`, which the plan had omitted.

Known flake, not caused by this branch: `TerminalAppIntegrationTest.rowResetRejectsACompletedSearchAlreadyQueuedAheadOfReconciliation`
(`"reflow"` case) fails about one full `check --rerun-tasks` in eight under parallel load, at
the same rate on the baseline code (`claude/plugin-sdk-design`, identical to `main` apart from
documents) as here; it passed six of six isolated reruns. Its final `findAsync` callback asserts
inside the callback, so a wrong count and a slow search both surface as the same five-second
latch timeout. `jasper-terminal` is untouched by this branch. Every other test was green in all
20 full runs made while investigating.

Open items: the sample plugin ships in the application image and should leave it when the
Vault plugin arrives. Until plan 3's Plugins manager, a user plugin can only be consented by
editing `plugins.toml`. Native acceptance is pending and user-run; the checklist is at the end
of the plan. Native packaging (`packageApp`/`verifyPackage`) was changed to bundle and verify
plugins but was not run.

The approved [terminal amendment](superpowers/specs/2026-09-20-jasper-terminal-refactor-design.md)
and [app/Buddy amendment](superpowers/specs/2026-09-20-jasper-app-buddy-refactor-design.md)
extend the original Phase 1 design. Their plans and verification reports preserve
execution decisions: [terminal](terminal-refactor-verification.md),
[app/Buddy](app-refactor-verification.md).

Merged-result verification: `./gradlew verifyTerminalArchitecture verifyApplicationArchitecture check :jasper-app:installDist`
passed with **1,127 tests: 1,125 passed, two expected environment skips, no failures
or errors** (app 609, Buddy 163, terminal 355). Architecture guards, Javadoc doclint,
compiled guide examples and packaged-resource checks passed. Javadoc missing-tag
warnings are nonfatal; headless checks do not establish native acceptance.

Independent reviews are complete. The terminal queued-search/reset defect and
the app endpoint-lock/accepted-launch shutdown defects were fixed with regression
tests. The two terminal documentation follow-ups (option reconstruction sites and
plan completion markers) are addressed by the [documentation audit](documentation-audit.md). App review
left no deferred minors.

## Open acceptance work

- User-run desktop behavior: window placement, physical keyboard/clipboard,
  fonts/IME, Buddy native windows, shell exits and residency.
- Windows native packaging and desktop acceptance.
- Fresh native throughput/RSS measurements on the refactored code; the dated
  [readiness measurements](terminal-readiness.md) apply only to their recorded revisions.
- The two-week daily-use trial has not started.

Use the [packaging checklist](packaging.md#native-acceptance-checklist) and
[benchmark protocol](benchmarks.md). Agents must not launch native windows or
benchmarks without the user's applicable authorization.

## Historical implementation notes

The remainder is an archive of earlier handoffs, not a current task queue.
Branch names, paths, package layouts, test counts, approvals and “next steps”
below describe their dated checkpoints. They do not override the current state
above or the maintained guides. In particular, older custom-theme/Follow System
features and flat app/Buddy packages have since been replaced. Historical artifacts
absent from this checkout are identified as such rather than linked as available.

**Background residency (2026-09-17):** On `claude/background-daemon` (from main `1f1afeb`, with `main` `b56d098` since merged in), Jasper gains an
opt-in `[background] enabled` setting (default `false`) that keeps the process running with no
windows after the last one closes, so the next launch reveals a window from an already-warm JVM
instead of paying a full cold start — JVM, AWT toolkit, FlatLaf, font resolution, theme,
shell-integration extraction, shell-history discovery. **Quit still exits completely**; that is
the off switch. A resident Jasper holds no shell, no PTY and no child process — sessions have
already exited through the normal pane-close path before the last window goes.

*Roles and handoff.* Startup now resolves one of four roles. **Launcher** (default) attempts an
`AF_UNIX` handoff before any AWT/Swing initialization and exits 0 if a resident process accepts.
**Resident** (`--background`, the form written into the login item) runs a full warm-up pass —
install FlatLaf and realize the toolkit, resolve the font family and fallbacks, resolve the
theme, extract the shell-integration scripts, refresh the shell-history index — binds the socket,
and opens no window; if the bind fails, it logs a warning and exits instead of opening a window
at login, which the plan's pre-flight scan caught as a real conflict (the spec's "log and
continue as an ordinary app" is written for a foreground launch, not an unattended one). **App**
(today's path) additionally binds the socket and, with the setting on, stays resident when its
last window closes; a reopen listener (`AppReopenedListener`/`APP_EVENT_REOPENED`, confirmed
present on JBR 25) is registered unconditionally beside the existing quit handler, so a Dock
click on a running, windowless Jasper raises a window through the same `openOrRaise` path the
socket uses, with no new process created. **Standalone** (`--config <path>`) is today's path and
nothing else — see below. The endpoint is a `daemon` subdirectory of the per-user config root
(`AppDirs.daemonDir/daemonSocket/daemonToken/daemonLock`), bound under a `FileLock` that makes
stale-socket recovery (check, connect, unlink, bind) atomic across concurrent launches, gated by
an owner-only token, and carrying the launcher's own code-source path and modification time so a
resident process from an older build replies `refused stale`, unbinds, and exits only if it has
no windows open — an upgrade is never served a window built from stale code and never kills a
running terminal.

*Login item.* Enabling the setting also plans a per-platform login item through `LoginItem.plan`,
a pure resolver in the style of `AppDirs.resolve`/`LaunchSettings.resolve`: macOS gets
`~/Library/LaunchAgents/dev.jasper.background.plist` (`RunAtLoad` true, `KeepAlive` false, no
`launchctl` call), Windows gets a `Jasper` value under
`HKCU\Software\Microsoft\Windows\CurrentVersion\Run` via `reg add`/`reg delete`. Only a packaged
install (detected through the `jpackage.app-path` system property) registers; a `./gradlew run`
session stays resident but logs a warning and skips the login item. The login item is reconciled
on every config load, immediately; residency itself is decided once at startup and is never
renegotiated while the process runs.

*A `--config` launch is fully standalone — a gap the review found in the spec, not just the
code.* The design originally said only that a `--config` launch never hands off, because the
resident process holds a different configuration; it did not say such a launch must not *bind*.
Since the endpoint's path comes from the real `AppDirs` rather than the override, `jasper
--config other.toml` with `background.enabled = true` inside `other.toml` would have bound the
shared endpoint, and a later plain `jasper` would have handed off to it and received a window
built from `other.toml` — exactly the invariant refusing the handoff exists to protect. Both the
spec and the code now make a `--config` launch fully standalone: no handoff, no bind, never
resident, login item untouched, regardless of what `other.toml` sets.

**Deviations from the plan.** Two of the plan's code blocks did not compile as written. Task 3's
wire-format code declared both `static LaunchRequest decode(String line)` and a
same-erasure `private static Path decode(String encoded)`, so the private helper is
`decodePath` instead. Task 4's `codeSource()` catch list read `URISyntaxException |
InvalidPathException | RuntimeException`, which javac rejects because `InvalidPathException` is a
`RuntimeException` subclass listed beside it; it was dropped from the catch list with no behavior
change, since `RuntimeException` already covers it. Several tests initially proved nothing:
mutation testing during Task 6's review found `openOrRaise`'s shutdown guard could be deleted
with the suite still green, because `newWindow`'s own guard covers every headlessly reachable
case, so the test was renamed to `openOrRaiseDoesNotThrowOnceTheApplicationHasQuit` with a
comment on exactly what it does and does not cover, and the raise-during-shutdown branch moved to
the manual checklist below instead; the warm-up test did not fail when `shellHistory.refresh()`
was removed until the implementer also stopped the shell-history index's poll timer that
`onChanged` starts; Task 4's first fix round separately found two tests that asserted a refusal
they reached for the wrong reason. The handoff's read deadline had to be split into two constants
after a regression test proved flaky: fixing a wedged accept thread (a silent client) first set
the server's read deadline equal to the launcher's own 2000ms reply budget, and a test
pitting a stalled peer against a concurrent handoff then raced those two clocks toward *different*
outcomes, passing 3 times in 10; `serve` now reads with its own 500ms `READ_TIMEOUT_MILLIS`
and the launcher's wait uses a separate 2000ms `HANDOFF_TIMEOUT_MILLIS`, and the regression
test is 10/10, confirmed to fail deterministically without the fix. The login item's live-reload
test needed its trigger fixed, not its assertion: `ConfigService.start()` queues its own initial
publish, so constructing `JasperApplication` and reconciling the login item in the same EDT turn
let that queued duplicate land as a second event on the freshly installed listener; the test now
spans two EDT turns with an intervening pump, matching the existing `ConfigurationControllerTest`
pattern, and no assertion changed.

Accepted without new coverage: `LoginItem.run()`'s 10-second timeout and `destroyForcibly` path
(the only commands it ever runs are `reg add`/`reg delete`, which do not hang in practice, and a
10-second test was judged not worth adding to a suite this size); `shutdown()` clears the quit
handler but never removes the constructor's reopen listener (inert, since the JVM is exiting
anyway, but asymmetric). Separately worth knowing before searching for it: `config.example.toml`'s
`[background]` table round-trips through
`ConfigTemplateTest.repositoryExampleIsCompleteAndParsesAsBuiltInDefaultsOnBothPlatforms`, not
`ExpandedConfigTest`, which never reads the example file at all.

`main` gained a `[notifications]` config table and a substantially rewritten `JasperApplication`
(NativeNotifier, BuddyDeck, CommandNotifier, new listener wiring) after this branch started;
expect mechanical conflicts in `ConfigLoader.FIELDS`'s root set, `ConfigSnapshot`'s record
components, `config.example.toml`/`ConfigTemplate`'s table insertion, and `JasperApplication`'s
listener/constructor wiring at merge time. Not acted on here; merging is a separate step with the
user's say-so.

**Next step:** each of tasks 1–7 is individually reviewed clean (task 4 after three fix rounds,
tasks 3 and 5–7 after one each, tasks 1–2 with none needed), but the whole-branch review the plan
calls for after this task has not run yet. The plan asks it to pay particular attention to the
accept loop's error handling (the only non-daemon thread; a thrown error there would silently end
residency), whether anything in `shutdown()` is now reachable twice or not at all on the residency
path, and the stale-build handoff, which is the one path that deliberately closes the endpoint
from inside a request.

Fresh `./gradlew check --rerun-tasks`: jasper-app 568 tests, 0 failures, 1 skip
(`ShellIntegrationScriptTest.fishReWrapsAPromptDefinedAfterTheIntegrationLoaded`, fish not
installed on this machine); jasper-terminal 316 tests, 0 failures, 1 skip
(`FontSetTest.fallsBackWhenPrimaryCannotDisplay`) — 884 tests, 0 failures, 2 skips overall,
unchanged from the end of Task 7 since this task adds no code or tests. A second, plain
`./gradlew check` run after the documentation edits matched exactly. [Design
spec](superpowers/specs/2026-09-17-jasper-background-daemon-design.md),
[plan](superpowers/plans/2026-09-17-jasper-background-daemon.md), [configuration
guide](configuration.md#background-residency), [packaging
checklist](packaging.md#native-acceptance-checklist).

**Still user-run.** Nothing about this feature's actual purpose has been verified, and no GUI was
launched at any point during this work:

- Cold-versus-warm launch timing — the number this whole feature is judged on.
- The resident process's idle memory (`ps -o rss= -p <pid>`).
- Login start on macOS (LaunchAgent, log out/in) and on Windows (`Run` value, sign out/in), both
  from a real packaged install.
- `AF_UNIX` sockets on Windows at all.
- The macOS Dock icon persisting with zero windows, and a Dock click producing a window
  noticeably faster than a cold start.
- Quit really exiting completely and the next launch being cold.
- Survival across sleep/wake and a monitor change while resident.
- The plist/`Run` value being removed the moment `enabled = false` is saved, without restarting
  Jasper.
- A handoff request or a Dock click arriving while Jasper is quitting not raising a window that
  is being disposed — the `openOrRaise` guard is inspected and unit-tested for the reachable
  branch, but the disposing-window branch itself needs a live window and cannot be exercised
  headlessly.

No GUI, merge or push.

**Buddy drag performance and presentation coordination (2026-09-17):** The user
reported dropped frames and tearing while dragging. Pointer events previously
moved the buddy immediately, rebuilt/revalidated the notification column, queried
monitor geometry, and resized/showed its native window while an independent column
timer also moved/repainted it. Dragging now consumes the latest pointer position
on one coalescing 16ms EDT clock, moving buddy and column in the same callback. The
column's independent timer is suspended during dragging and resumes afterward;
release flushes the last position, and hide/dispose cancel pending moves. A paused
pointer still advances spring/shimmer frames. Geometry-only moves retain notice
motion and use the monitor snapshot captured at drag start; native size/location/
visibility setters run only when changed.

Static translucent capsule material (fill, outline and clipped outer shadow) is
cached in bounded 2x premultiplied images. Immutable deck snapshots are cached
until mutation, and settled column shimmer invalidates only the subtext region.
The actual 2x three-card headless benchmark improved from median **0.983ms / p95
1.251ms / p99 1.454ms** to **0.731ms / 0.896ms / 0.981ms**. Before/after RGBA renders
differed in only 12 pixels, by at most one channel value. This measures Java2D
paint CPU cost, not native dragging FPS or WindowServer presentation. Separate
native windows still cannot promise atomic/vsynced presentation; desktop tearing
acceptance remains user-run, with no unverified claim that it is eliminated.

`./gradlew check`: **1,004 tests, 1,002 passed, two existing skips, no failures/errors**
(app 682/681/1; terminal 322/321/1). New drag-clock tests cover pointer bursts,
paused-pointer animation, final-position flushing and cancellation. Existing buddy
geometry, material, title/state, hit-testing and motion regressions pass. Source
hygiene and diff checks pass. Reproduce CPU measurements with
`:jasper-app:buddyPerformanceMeasurement` (headless; no native window or shell).
No GUI, commit or push.


**Buddy shimmer and fluid screen placement (2026-09-17):** The additional
14:35:58 recording was extracted into all 482 original frames with ffmpeg, with
ffprobe timestamps. On the same notification worktree, RUNNING subtext now has a
soft left-to-right glyph highlight, shared by column and drawer, with steady
completed/waiting/orphaned text. Running surfaces request 16ms frames; timers stop
when hidden/disposed and return to a one-second cadence for other live details.

Dragging follows the buddy directly until its anchor-relative placement changes.
The column switches sides around the screen midpoint with an eight-point dead
band and an approximately reference-fitted damped spring (8.6/s decay, 8 rad/s,
800ms settle). Reversals preserve position and velocity; internal stack direction
also interpolates. Every intermediate window position is clamped to the usable
monitor, including offset/negative-origin screens. Initial showing snaps placement
without altering the separate capsule arrival animation.

`./gradlew check`: **1,002 tests, 1,000 passed, two existing skips, no failures or
errors** (app 680/679/1; terminal 322/321/1). Eight new tests cover highlight
movement, steady completed text, repaint state, continuous/reversed placement,
midpoint hysteresis and screen bounds. Actual headless light/dark drag/shimmer
previews are in `jasper-app/build/reports/buddy-motion/{dark,light}.mp4`, generated
by `:jasper-app:buddyMotionPreview` plus ffmpeg. Native desktop acceptance remains
user-run under AGENTS.md. No GUI, commit or push. The shimmer's band/timing is
visual tuning, not a claimed exact measurement from the subtle reference shader.
[Measurements and reproduction](design/buddy-notification-reference.md#running-subtext-and-dragging-follow-up-2026-09-17).


**iTerm-style tabs and title correction (2026-09-17):** Continuing on
`claude/command-notifications` in `.claude/worktrees/practical-shamir-1252c5`.
The user's three screenshots supersede the fixed 160-point tabs, duplicate
right-hand window title and sliding underline. A single session now hides the
strip and uses a centered window title; multiple tabs stretch across the macOS
title bar with centered system-font text, thin dividers, hover close buttons,
actual shortcut labels and a fixed right-edge add button. The 38-point default,
native stoplights/gestures, tab ordering and overflow navigation remain.

Automatic tab/native titles now combine the OSC title (or directory) with the
foreground job: `~ (-zsh)`, `~ (sleep)`, `Reviewing files (tmux)`. A small Unix PTY
metadata query reads the foreground process group, then Java process metadata,
every 500ms off the EDT. It needs no shell integration or helper subprocess and
stops polling on exit/disposal. Unsupported process metadata falls back to the
configured program. Manual names remain overrides. Buddy titles remain the
program's text without the job suffix, with the captured command as fallback;
finished-notification ordering and the matched bubble motion remain unchanged.
The existing system-font resolver is shared as `SystemFonts`.

The title wrapper was also present in iTerm: tmux's default `set-titles-string`
added the session/window number and quoted pane title. With explicit user
permission, `~/.tmux.conf` now forwards custom pane titles and shortens its default
hostname/user@hostname:path shell title to `~` or the directory basename. The
running server received only this option; no plugin/config reload. Original
saved as `~/.tmux.conf.jasper-backup-20260917-145844`. The running shell evaluates
to `~`, while custom task titles stay intact.

Validation: `./gradlew check` passes **994 tests: 992 passed, two existing skips,
zero failures/errors** (app 672/671/1, terminal 322/321/1). Real PTY tests cover
foreground job changes, background jobs, login shells, real tmux forwarding,
OSC 0/1/2, manual naming and completed-notice snapshots. Headless title-bar
renders cover single/multiple/narrow layouts in both themes; source hygiene and
diff checks pass. One pre-existing dangling Javadoc warning remains in
`LaunchSettingsTest`. Native desktop acceptance remains user-run under AGENTS.md;
no GUI, commit or push. [Reference and reproduction](design/iterm-title-bar-reference.md).



**Program titles in tabs and buddy notices (2026-09-17):** Continuing in
`.claude/worktrees/practical-shamir-1252c5` on `claude/command-notifications`.
The user requested using the title set by the command/application for the tab
and bubble. OSC 0/1/2 reception and automatic tab/window titles already existed;
the missing connection was from title events to notices, and tabs had no command
fallback when no OSC title was supplied. The running local tmux server was also
observed with `set-titles off`, which prevents its pane titles reaching Jasper.
Optional session-scoped forwarding settings are now documented; the user's tmux
configuration is unchanged.

`TerminalPane` keeps ordered EDT snapshots of title/start/end events. Reading
`session.title()` later would race with a subsequent prompt title, so a real PTY
test holds Swing until the full title/finish/prompt burst has been parsed and
checks the completed notice still names the command's last task. Automatic tabs,
the native window and live bubbles use the program title, then running-command
fallback; idle automatic tabs still fall back to the directory. Manual tab names
remain explicit overrides. Finished notices/native notifications retain the last
title from that execution.

A title update changes a notice in place, preserving order, acknowledgement and
arrival state, and cannot resurrect a dismissed notice. Full titles are retained;
renderers fit them to available width, with code-point-safe binary truncation for
the capsule instead of repeatedly shortening a potentially long title one
character at a time. No terminal-module API or shell script changes were needed.

Validation: eight new tests cover OSC 0/1/2 through the actual PTY/parser/tab/window/
bubble path, command fallback and title clearing, preserved manual names,
completion ordering, per-pane identity, pre-threshold titles, dismissal and long
Unicode titles. `./gradlew check`: app 674 (673 passed, one fish skip), terminal
320 (319 passed, one existing font skip): **994 total, 992 passed, two skipped,
zero failures/errors**. Source hygiene and diff checks pass. Native GUI remains
user-run. [Title behavior and tmux settings](configuration.md#automatic-tab-and-notification-titles).

**Buddy notification reference correction (2026-09-17):** On
`claude/command-notifications` in `.claude/worktrees/practical-shamir-1252c5`, the
user supplied light/dark recordings and asked to replace the notification styling
and animation. ffmpeg extracted every original frame and ffprobe supplied its
variable-rate timestamp. The replacement uses 330 × 56 logical-pixel capsules,
13-point macOS system text, measured light/dark fills, partial transparency and a
cached soft outer shadow. Thought-tail dots, card overlap and scale/hover growth
are removed. The capsule descends 32 logical pixels with a fitted 600ms spring;
the actual headless render tracks the recorded arrival with 0.95 physical-pixel
RMS error and a maximum two-pixel difference.

Motion is keyed by notice identity: status updates stay in place and interrupted
stack moves preserve the current position. Live detail text ticks once a second
after motion settles; theme changes repaint visible capsules. Drawer dismissal
now refreshes the column, and input ignores transparent rounded corners. Existing
notice routing, three-visible limit and drawer history remain. The reference's
stop-task control has no corresponding Jasper cancellation API; existing open
and dismiss actions are retained rather than adding a misleading button.

`./gradlew check` passes: app 666 tests (665 passed, one fish skip), terminal 320
(319 passed, one existing font skip): **986 total, 984 passed, two skipped, zero
failures/errors**. `:jasper-app:buddyNotificationPreview` renders actual components
without a window or shell. Light/dark frames, the visual comparison and a
reference-left/Jasper-right motion MP4 are in
`jasper-app/build/reports/buddy-notifications/`. Source hygiene and diff checks
pass. Native desktop acceptance remains user-run, especially transparency over
a textured background: a flat video does not uniquely identify source alpha or
native blur. [Measurements, evidence limits and reproduction](design/buddy-notification-reference.md).
The thought-column spec and plan banners record this user-requested deviation.

**Finished-command notifications (2026-09-16):** On
`claude/command-notifications` (from main `1f1afeb`), a command that ran past a
threshold and finished in a tab you were not looking at now tells you, through
the desk buddy — who also gains the status bubble that was built and deferred.

*Duration.* `commandExecuted` carried no elapsed time, so `TerminalSession` now
stamps a monotonic clock at the command-start mark and subtracts at the end; the
clock is injectable so tests drive it without sleeping. The first cut used
`commandStartedAt == 0` as a "never started" sentinel — the same mistake as
history ranking treating timestamp 0 as "older than everything" — and the test
clock, which legitimately starts at zero, caught it. No sentinel is needed.

*The rule.* `CommandNotice.shouldNotify` is pure over three booleans the app
already tracks, so all eight combinations are tested without a window. It
notifies for another tab of the focused window, and for anything while Jasper is
in the background. Deliberately quiet: the tab you are looking at, and a command
in a different Jasper window while another Jasper window has focus.

*Sprites.* Three frames appended — `TYPE_A`, `TYPE_B`, `TYPE_REST` — so existing
column indices are untouched; the strip is 840x48. The plan said to check the
`.ase` master had not been hand-edited, because `generate.py` warns that
rerunning it destroys hand edits. It had four commits rather than the expected
one, so the check earned its place: `generate.py` changed in all four and
rerunning it reproduces the committed PNG byte for byte. The script has always
been the source of truth and the README now says so. The first laptop was drawn
large and centred and read as a buddy behind a monitor, with shell, belly and
glasses all hidden; rendering at 8x showed it at once, and it is now compact and
sits in his lap.

*Animation and routing.* `BuddyAnimator` gains a `WORKING` mode that returns
before the tuck and sleep deadlines, so a working buddy never falls asleep —
pinned by ninety seconds of ticks.

*Two surfaces.* Live notices rise in a **thought column above his head**;
everything that happened lives in a **drawer** he opens on a single click.
Position is a claim about lifetime, and a permanent stack of finished cards
beside him was a record pretending to be a status display.

`BuddyDeck` holds `BuddyNotice`s keyed by `(source, key)` and knows nothing
about panes: the terminal is one producer and passes the pane as its key,
which is what makes "one card per tab/pane/window" true today without the
deck learning why. `Kind` splits **task** (begins and ends) from
**connection** (up until it is not), because a tunnel never completes and a
model built only around completion could not express one; `State.fits(Kind)`
makes an impossible notice unconstructible. `detail` is a `Supplier<String>`
so a running card ticks without being re-posted and a future transfer can
report bytes through the same field.

A bubble appears the moment its pane stops being watched — `focusLost` covers
another pane, another tab, another window and Jasper itself going to the
background — with `long_command_seconds` as the fallback for a command
running in the pane you are still watching.

The surfaces run their own repaint timers. They previously had none: the
buddy's timer repaints only his sprite canvas, so the column painted a
single frame per event at elapsed zero — scale 0.6, and opacity 0 because it
rode the same spring curve — and the bubble was invisible until an unrelated
repaint happened. Opacity now fades over the first 40% of the arrival rather
than riding the spring, and the timers stop as soon as `animating()` goes
false, so a settled column costs nothing. Bubbles overlap by six pixels so
the column reads as a stack, and the ones above a new arrival spring up over
its place and overshoot once before settling.

Column membership is `live() || !acknowledged`. Acknowledgement is deck
state, not notice state, because it is a fact about the reader. Focusing a
pane acknowledges it; a command finishing in the focused pane is
acknowledged on the spot and never appears; closing a pane acknowledges as
well as orphans, since its pane can never be focused again. `live()`
excludes orphans — a pane closed mid-command is left `RUNNING` but nothing
is still happening, and without that it sat above his head all session.

`BuddyCard` paints one card for both surfaces. `BuddyDeckLayout` and
`BubbleMotion` stay pure, so the column offsets, the flip, the scroll clamp,
the hit tests and the bounce curve are all tested headlessly. Rendering the
column under the sprite is what showed the tail was invisible at 7px on a
dark desktop and trailed off his centre line; no assertion would have.

`NEEDS_INPUT` is defined and rendered but set by nothing yet: OSC 133 `A`
means "at a prompt, ready for input", so for the shell it is the same event
as a finished command. Catching a blocked **sub-process** (`sudo`, `[y/N]`)
needs the pty's foreground process group and is its own work. Connection
producers, and grouping healthy connections into one "2 tunnels up" bubble,
are deliberately deferred until something posts one rather than shipping
untested machinery for a caller that does not exist.

`TerminalSession.Listener.commandStarted` is new. Nothing previously reached
the app at the C mark, so `CommandNotifier.passedThreshold` had no
production caller at all and the typing animation could never have run.

Notification widened from the tab to the pane: only the pane you were typing
in is quiet. `CommandNotifier.Channel` was deleted rather than given a second
implementation. `NativeNotifier` passes the command line to `osascript` as
argv rather than inside an interpolated AppleScript string, which a test
asserts with a command containing quotes and a newline.

*tmux.* The scripts wrap every OSC in tmux's passthrough sequence when `$TMUX`
is set, and turn on `allow-passthrough` for their own pane. Measured against tmux
3.5a: without the wrapper **none** of the marks reach the outer terminal, so the
whole feature was silently dead in a pane while history — which is read from the
shell's history file, not the marks — kept working and hid it. With the wrapper
all four marks, the OSC 1341 command payload and OSC 7 arrive. The option is set
pane-scoped so a user's global tmux configuration is untouched.

*Setting.* `notifications.long_command_seconds`, default 10, live, 0 disables.
**It needs shell integration**: the duration comes from the OSC 133 marks, so a
shell without them produces no notifications and no cards at all, which the
configuration guide states plainly rather than guessing a duration.

Fresh `./gradlew check --rerun-tasks`: jasper-app 554 tests,
553 passed and one fish skip;
jasper-terminal 318 tests,
317 passed and one existing
font skip. Total 872 tests, 870 passed, 2 skipped,
zero failures/errors. [Configuration
guide](configuration.md#finished-command-notifications), [design
spec](superpowers/specs/2026-09-16-jasper-finished-command-notifications-design.md),
[plan](superpowers/plans/2026-09-16-jasper-command-notifications.md). Still
user-run: a long command in a background tab on the real desktop, watching the
typing animation and the bubble, and the same with the buddy disabled. No GUI,
merge or push.

**History ranking, freshness and tmux (2026-09-16):** On
`claude/history-scope-ranking` (from main `18fd513`), three confirmed defects in
the shipped History scope and one preference, each measured against the dev
machine before anything was changed.

*Ranking.* `ShellHistorySnapshot` sorted on `timestamp`, which is `0` when
unknown, so `0` meant both "unknown" and "older than everything". bash writes no
timestamps unless `HISTTIMEFORMAT` is set — measured here: 10,004 timestamped
zsh entries against 500 untimestamped bash ones, and `HISTTIMEFORMAT` set
nowhere — so no bash command could ever rank however recently it ran. An entry
without a timestamp now ranks just before its source file was last written,
stepping back a second per entry from the end. `build` takes
`Source(entries, modified)` instead of a bare list.

*Freshness.* The index had exactly two refresh triggers: the first window, and
the History scope being activated. A one-second Swing timer now calls the
existing `refresh()`, started on the first listener and stopped on the last, so
a window with no palette pays nothing. `refresh()` already coalesces and
`readSource` already returns on unchanged size and mtime, so a quiet tick is one
stat per source — pinned by `aQuietTickCostsNoRead`.

*tmux.* `inject` matched only `zsh`, `bash` and `fish`, so a user whose
configured shell is tmux got no integration at all. Measured: a pane of a tmux
server Jasper started *does* inherit `ZDOTDIR` and `JASPER_SHELL_INTEGRATION`,
but tmux replaces `TERM_PROGRAM` with `tmux` and every script guarded on that.
Jasper now exports `JASPER_TERMINAL=1`, which tmux leaves alone, and `inject`
gains a tmux arm applying the environment mechanism for the basename of
`$SHELL`. Driving a real tmux 3.5a then exposed a second problem, introduced by
the previous feature: tmux runs its `default-command` as `$SHELL -c ...`, so
under `set -g default-command ${SHELL}` — which this machine's `~/.tmux.conf`
sets — the interactive shell is the child of a non-interactive one, and the
`.zshenv` fix that stopped a non-interactive zsh leaking `ZDOTDIR` stranded it.
`JASPER_INTEGRATION_LOADED` separates the two cases. Verified against real tmux
with that configuration: the integration loads and `__jasper_precmd` is defined.

*Trivial de-ranking.* `palette.scopes.history.trivial_commands` (live) partitions rather than
scores: the listed commands keep their order among themselves and follow
everything else, and are still listed and searchable. It defaults to `exit`,
`clear`, `ls`, `ll`, `la`, `cd`, `pwd`, `c`, `q` and `logout`. Only a short
command whose first word is listed counts, so `cd deep/path && ./gradlew build`
is real work and `clearcache` is not `clear`. Setting the key replaces the
default list; an empty list turns de-ranking off. It shipped first as a boolean
over a built-in set and became a list on request — the ranking code did not
change, only what it is handed. Entries are single words (one containing
whitespace could never match and is rejected), lower-cased and de-duplicated.
Both History-scope settings then moved from a top-level `[history]` table to
`[palette.scopes.history]`, so a future scope adds a table beside it rather than
another top-level one; `palette.max_results` stays put because it applies to
every scope rather than one. The old `[history]` table is not accepted as an
alias — it is reported as unknown — which is safe because no config in use set
it.

Deviations from the plan: the flag rides on `PaletteContext` beside
`maxResults`, not on a config snapshot the scope never receives, so the
partition lives in `ShellHistoryScope` and `ShellHistorySnapshot` stays pure
merged data; `ShellIntegrationEndToEndTest`-style prompt-row coverage was not
extended, since `promptRows()` is package-private in `jasper-terminal`. Three
existing tests asserted orders that only held under the old sentinel or the old
ranking and were updated, each with a comment saying why: two index fixtures
stamped zsh entries at epoch 100–500 while the bash file's mtime was the wall
clock, and one scope test pinned a row position that `ls` being de-ranked moves.

**Known gap:** bash inside tmux still gets nothing, because bash's mechanism is
a `--rcfile` argument tmux never passes; documented with the manual `source`
line. Attaching to a tmux server started before Jasper gets nothing either.

Fresh `./gradlew check --rerun-tasks`: jasper-app 523 tests, 522 passed and one
fish skip; jasper-terminal 316 tests, 315 passed and one existing font skip.
Total 839 tests, 837 passed, two skipped, zero failures/errors; after merging
`main`, which had brought in the palette input-row anchor and its three tests,
842 tests, 840 passed, two skipped, zero failures. [Configuration
guide](configuration.md#shell-history), [palette
guide](command-palette.md#shell-history), [design
spec](superpowers/specs/2026-09-16-jasper-history-ranking-design.md),
[plan](superpowers/plans/2026-09-16-jasper-history-ranking.md). Still user-run:
the History palette inside tmux on the real desktop, confirming a just-run
command appears within about a second and that bash entries interleave sensibly.
Merged to `main` and pushed. No GUI.

**Palette input-row anchor (2026-09-16):** On `claude/command-palette-y-axis`
(from `18fd513`), the palette card is anchored by its **top** edge, one-fifth of
the way down the terminal area, instead of by its vertical center one-third
down. The old anchor subtracted half the card height from the top, so every
change in result count — and therefore every scope, since Commands, History and
Snippets return different numbers of rows — put the text box at a different
height; the user reported the jump from screenshots. Only the small-window
clearance clamp may still move the top edge, and only when the card would not
otherwise fit. One-fifth keeps the default Commands card within a few pixels of
its 2026-09-13 position. `WindowCommandPalette.positioned` carries the change;
three tests cover it (a pure geometry check across four card heights, the
clamped small-window case, and a real scope switch plus two row-count changes
asserting the card's `y` never moves while its height does) and all three fail
against the old formula. `./gradlew check`: 821 tests, 0 failures, 2 pre-existing
skips (`FontSetTest.fallsBackWhenPrimaryCannotDisplay`, and the fish script test
that needs fish installed). The binding spec, the scopes spec, the palette plan
and `docs/design/command-palette/README.md` record the new anchor, and the
24-image render matrix plus the 2× UI-scale artifact were regenerated headlessly
with `commandPalettePreview`. Native GUI confirmation remains user-run.

**Shell integration scripts (2026-09-16):** On `claude/shell-integration`
(from main `27999fe`), Jasper ships and auto-loads its own zsh, bash and fish
integration scripts instead of relying on the user's own shell configuration.
`ShellIntegrationScripts.install` extracts `jasper.zsh`/`jasper.bash`/
`jasper.fish` plus their per-shell wrapper files (zsh `ZDOTDIR` wrappers,
`bash/rc.bash`, the fish vendor snippet) to `shell-integration` under the
application directory, writing only files whose content changed so upgrades
replace stale copies in place. Each script emits OSC 7 on directory change,
OSC 133 A/B/C/D with the exit status in D, and the exact command line just
before C on Jasper's own OSC 1341 channel; it guards on an interactive shell,
`TERM_PROGRAM=Jasper` and an unset `JASPER_INTEGRATION_LOADED`, so a nested
shell gets no marks unless it sources the script itself.
`terminal.shell_integration` (`"auto"`/`"manual"`/`"off"`, default `"auto"`,
new panes only) controls whether `LaunchSettings` injects the script for a
launch whose resolved program is exactly `zsh`, `bash` or `fish` (zsh via
`ZDOTDIR`, bash via `--rcfile` with `-l`/`--login` emulated through
`JASPER_LOGIN_SHELL`, fish via `XDG_DATA_DIRS`) or only exports
`JASPER_SHELL_INTEGRATION`; `TERM_PROGRAM=Jasper` is always set and a
`[terminal.env]` override wins. `TerminalSession` learns the `cmd` payload
(base64-decoded, preferred over the screen read) and a
`shellIntegrationDetected()` flag that goes true at the first A mark; the
status bar shows a filled dot after the shell name once detected and a hollow
one otherwise, with matching tooltips. `Main` extracts the scripts and passes
the resulting directory into `JasperApplication` and `ShellLauncher` before the
first window opens.

**Whole-branch review and its fixes (2026-09-16):** Three reviewers covered the
scripts, the launch/extraction Java and the receiving side plus tests and docs.
No Critical findings; everything below was fixed on the branch under
[plan](superpowers/plans/2026-09-16-jasper-shell-integration-review-fixes.md),
each with a test that fails without the fix.

*Scripts.* fish wrapped the prompt once at load with no marker check, and fish
sources `vendor_conf.d` before `config.fish`, so starship, Tide or any
hand-written `fish_prompt` replaced the wrapper for the session and the B mark
was lost — the wrap now happens in the `fish_prompt` event with a marker check,
like zsh and bash, and the invented `prompt_pwd` fallback is gone. `set -u`
printed "unbound variable" on every new pane; a readonly `PS1` or
`PROMPT_COMMAND` printed an error every prompt and aborted the hook; a command
containing the substring `__jasper_` was dropped entirely; `printf`, `base64`
and `tr` were callable as user functions. `rc.bash` sourced both
`/etc/bash.bashrc` and `/etc/bashrc`; `.zshenv` left `ZDOTDIR` pointing at the
wrapper directory for a top-level non-interactive zsh.

*Launch and extraction.* `JASPER_*` is now scrubbed from the inherited
environment — the scripts export `JASPER_INTEGRATION_LOADED` and return early on
it, so a Jasper launched from an integrated pane (including `gradlew run`, the
way this branch gets tested by hand) had integration silently off in every
pane. bash injection is skipped when the user passed `--norc`, `--rcfile`,
`--init-file` or any form of `-c`, because bash would ignore Jasper's rc file
while `-l` had already been stripped, leaving a login shell with neither login
emulation nor an rc file; `-l` is recognised in a short cluster and left alone
past `--`; `--noprofile` no longer sets `JASPER_LOGIN_SHELL`; `XDG_DATA_DIRS`
no longer accumulates. Extraction writes through a staged file and an atomic
move, never through a symlink, owner-only.

*Receiving side.* A repeated A flushed the cycle with no status, so fish 4 —
the one shell with no automated coverage — lost the marker for every command.
The `cmd` text survived D, RIS and the alternate-screen switch. The payload had
no bound while `ShellHistoryParser` caps disk lines at 16 KiB. Invalid UTF-8
was substituted to U+FFFD and reported as the command. Detection now notifies
listeners: the A mark draws nothing, so the status dot previously updated only
when the following prompt happened to trigger a repaint — it worked by luck,
and the new `ConfiguredTerminalBehaviorTest` case fails without the fix. This
one was not in any reviewer's report; writing the wiring test found it.

*Behaviour change to review.* `HISTCONTROL=ignorespace` (or `ignoreboth`) is now
honoured: a space-hidden command sends neither the `cmd` payload nor C, so it
reaches neither shell history nor Jasper's. The reviewer raised this as a
spec-level privacy question rather than a bug, and the previous behaviour was
pinned by a passing test; that test is now inverted. Revert the `HISTCONTROL`
case in `jasper.bash` if the original behaviour was intended.

*Deliberately not done.* The status bar now draws a running/stopped dot and an
integration dot side by side ("● zsh ○"), and `Segment.doLayout` clips the
integration dot first at narrow widths. Both are visual judgements that cannot
be verified headlessly and would churn the design render fixtures on a guess —
left for the user's desktop pass.

Deviations from the plan text: extraction runs on the **EDT** in `Main` before
the first window is created (a few small file writes, measured at ~9 ms cold),
not on a configuration worker as the spec proposed — the earlier STATUS wording
said "main thread", which was wrong. The script line budget is now per-shell:
`jasper.bash` is 109 lines against zsh's 57 and fish's 55, because only bash
needs the `DEBUG`-trap plumbing, both `PROMPT_COMMAND` forms, the readonly
guards and the `HISTCONTROL` check. Known limitations, all disclosed in the
[configuration guide](configuration.md#shell-integration): a bash `DEBUG` trap
the user installs after the first prompt displaces Jasper's; the chained user
trap sees `$?`/`$_`/`BASH_COMMAND` from Jasper's wrapper; `jasper.fish` is still
untested on the dev Mac because fish is not installed, though it now has a test
that reports as a skip rather than being absent. [Configuration
guide](configuration.md#shell-integration), [design
spec](superpowers/specs/2026-09-16-jasper-shell-integration-design.md),
[plan](superpowers/plans/2026-09-16-jasper-shell-integration.md). A **second whole-branch review** then found three regressions these fixes had
introduced, all since fixed with their own tests: honouring `HISTCONTROL`
suppressed repeated commands under `ignoreboth` (Ubuntu's stock value) and every
command when history was disabled, and tightening the `A` arm moved
`commandStartRow = -1` and the payload clear behind the row check, reopening the
stale-capture leak the same round had closed — only the flush belongs there. It
also found the five wrapper files had never been made unset-safe (round one
fixed only the three main scripts, so under `set -u` Jasper silently skipped the
user's own `~/.bashrc`/`~/.zshrc`), two bare `printf` calls left in the bash
DEBUG trap, `-o`/`-O` option arguments ending the bash option scan early, and a
staged extraction path that could itself be a symlink.

A direct check of the `HISTCONTROL` matrix against real bash afterwards caught
a hole none of the tests had: a space-hidden command typed as the **first**
command of a session leaves history empty, which the "history is switched off"
carve-out then read as permission to report it — the exact case of opening a
terminal and typing ` export TOKEN=…`. Suppression now keys on whether history
is enabled (`[[ -o history ]]` and `HISTSIZE`), not on whether it has entries.
The eight-row matrix (hidden/visible × ignorespace/ignoreboth/ignoredups/none ×
history on/off) is verified against `/bin/bash`, and the new test fails if the
condition is reverted. Fresh `./gradlew check --rerun-tasks` on the branch: jasper-app
503 tests, 502 passed and one fish skip; jasper-terminal 313 tests, 312 passed
and one existing font skip — 816 tests, 814 passed, two skipped. After merging
`main` (which had brought in the cursor-shape fix and its three tests): 819
tests, 817 passed, two skipped, zero failures/errors. Still user-run: each
shell (zsh, bash, and fish if installed) on the real macOS desktop with the
user's own dotfiles and prompt theme, confirming marks, the status-bar dot and
that nested shells stay quiet; plus a look at the two adjacent status-bar dots
and at the status bar at a narrow window width. Merged to `main` and pushed.
No GUI.

**Configured cursor shape under tmux (2026-09-16):** On
`claude/shell-cursor-shape-bug-9981da`, `terminal.cursor.shape` was ignored for
anyone whose shell sends a terminal query. Only `CSI ... SP q` is DECSCUSR, but
jediterm-core 3.76 reads *every* CSI ending in `q` as one — measured:
`CSI > q` (XTVERSION), `CSI Ps q` (DECLL) and `CSI ? q` all arrive as
`BLINK_BLOCK`, indistinguishable from an application asking for a block cursor.
tmux sends XTVERSION when it opens a session, so `display.cursorShape()` was
non-null within milliseconds of launch and `CursorStyle.effective` returned the
program's shape forever after; the config never lost a value, it was outranked.
`ShellIntegrationFilter` now parses CSI properly (parameter bytes `0x30–0x3F`,
intermediates `0x20–0x2F`, final `0x40–0x7E`) and **drops** a `q`-final sequence
that carries no space intermediate, which also retires the `CSI_ZERO`/`CSI_SPACE`
states. JediTerm answers none of these queries (verified: no reply bytes), so
nothing is lost. Real DECSCUSR is untouched: `CSI 0 SP q` / `CSI SP q` still pass
through and append `cursor-reset`, `CSI 5 SP q` still sets a beam, and a query
arriving afterwards no longer discards it. Verified end to end against the user's
own `config.toml` and real tmux: shape stays null, effective style `UNDERLINE`.
`./gradlew check`: 773 tests, zero failures, one existing font skip. Jasper does
not reply to XTVERSION — deliberate, matching the previous behaviour; worth
revisiting only if a program needs the answer. Merged to `main` and pushed; the
branch is deleted. No GUI.

**Palette snippets (2026-09-16):** On `claude/snippets` in `.worktrees/snippets` (from
main `b9efd41`), the command palette gains a third scope, `SnippetsScope` (id
`jasper.snippets`, bookmark icon, aliases `snip`/`snippets`), backed by an
application-wide `SnippetStore` over `snippets.toml` (one serial worker, EDT
snapshots, append-only writes, so hand edits and comments survive). Snippets are
named commands with `{{placeholder}}` tokens (`\{{` for a literal); Paste and
Paste-and-run on a snippet with placeholders open a fill-in step — one field per
placeholder in first-appearance order, Tab/Shift+Tab wrap, values remembered per
placeholder for the process — and Shift+Enter is "Edit file" (creates
`snippets.toml` with its header if missing, then opens it in the OS editor).
From History, Shift+Enter is "Save as snippet…": a one-field name step prefilled
with the command's first word and argument, appending to the store and, on
success, reopening Snippets with the new row selected; a duplicate name or a
write failure keeps the step open with an error message. Cmd+J/Ctrl+Shift+J
always opens Snippets (a prior override collides with the new `snippets_palette`
default exactly as `history_palette` does). Deviations from the plan text,
recorded in the plan and spec status banners: scopes expose steps through
`PaletteScope.step(row, verb, context)`, consulted before `execute`, rather than
`execute` itself returning a step as the spec's prose suggested; `available`
gained a verb parameter so History refuses Paste on a dead pane but still allows
Save; the controller ignores Enter while a step's asynchronous `complete()` is
pending (a `completing` guard) so two quick presses cannot double-submit; the
History duplicate-name message names the *existing* snippet's own canonical
name, not the typed one; `WindowContent` now assigns its snippet store
before building the History scope, fixing a constructor-order bug in passing;
Snippets' paste verbs (`available`) require a live target while Edit file does
not, and the error row is likewise available only for Edit file; and reopening
the palette after a save sets the query field to the new snippet's name (a new
`PaletteStep.Result.reopen(scopeId, rowId, query)` overload) so the row is
still visible under `palette.max_results` even when it sorts last in the file.

Task 6 extended the [headless render matrix](design/command-palette/README.md)
with three new states — the Snippets list, the `Deploy` fill-in step and the
History save-name step — rendered from a six-snippet fixture file (two of them
with placeholders) written into a temporary directory, all inspected in dark and
light. [Guide](command-palette.md#snippets),
[design spec](superpowers/specs/2026-09-15-jasper-snippets-design.md),
[plan](superpowers/plans/2026-09-15-jasper-snippets.md). Fresh
`./gradlew check --rerun-tasks`: jasper-app 464 tests passed; jasper-terminal 303
tests, 302 passed and one existing font skip. Total 767 tests, 766 passed, one
skipped, zero failures/errors. Still user-run: Cmd+J on a real macOS desktop,
editing `snippets.toml` in the real OS editor, and the fill-in step with an input
method active. No GUI, merge or push.

**Palette visible rows (2026-09-29):** supersedes the result cap below. The five rows were meant as
the list's height, not a cap on matches: `palette.visible_rows` (1–20, default 5, live; the old
`palette.max_results` key is an alias that Migrate Config renames) sets how many result rows the
list shows before it scrolls, with headers above them adding their own height. Scopes now get
`PaletteResults.MAX_ROWS` (200) as their limit, so a scope's tab and the All tab list every match;
All still shares 200 rows across its sections and offers "More in <Scope>…" only for a scope that
budget cuts short. SDK unchanged apart from javadoc (`PaletteQuery.maxResults` stays "at most this
many").

**Palette result cap (2026-09-15):** `palette.max_results` (1–20, default 5, live) is now the
hard cap every scope returns: `PaletteContext` carries it to `CommandsScope` (through a
`CommandSearch.find` limit) and `ShellHistoryScope` (which no longer holds fifty rows and
scrolls twelve), the card sizes to exactly that many rows, and `PaletteScope.preferredRows()`
is gone. Cmd/Ctrl+1–5 still number the first five. Tests cover parsing, the range, both
scopes and the live change on an open palette. History renders regenerated.

**Palette scopes and shell history (2026-09-15):** On `claude/palette-scopes` in
`.worktrees/palette-scopes` (from main `86352b9`), the command palette is now
scope-based: one `PaletteScope` contract with two implementations, `CommandsScope`
(wraps the existing registry/search/three-recents behaviour unchanged) and
`ShellHistoryScope` (substring search over an application-wide `ShellHistoryIndex`
fed by zsh/bash/fish/nushell/PowerShell history files and OSC 133 B/C live
capture; nushell is looked up under `$XDG_CONFIG_HOME` or `~/.config` and, on macOS,
also under `~/Library/Application Support/nushell`). Cmd+K/Ctrl+K
always opens Commands, Cmd+R/Ctrl+Shift+R always opens History (a prior user override
already bound to that shortcut now collides with the `history_palette` default and reverts
all keybinding overrides to defaults until the user sets `history_palette = "none"` or
rebinds it); a scope's own shortcut dismisses it while already active, even from the
picker. Typing `>` at the start of an empty query opens an in-card scope picker
(Tab/Enter commits, Escape or deleting the `>` reverts); a visible chip shows the
active scope and doubles as a picker button. History rows show a shell tag,
optional working-directory detail line and, on Enter, paste the command into the
focused pane; Cmd+Enter pastes and runs. `history.enabled` (live) registers or
removes the scope entirely. Deviations from the plan text: the picker state is
derived statelessly from the query text (`query.startsWith(">")`) rather than
tracked as a transition, since `JTextField.setText` fires remove+insert and a
transition-based check cannot see a `>`-prefixed value the whole way through;
`PaletteKeyRouter` claims a key only when its target scope is registered, so a
disabled History shortcut is consumed but not held; `ShellHistoryParser.consumed`
tracks raw byte offsets so a dangling zsh/PowerShell continuation is re-read
later and a trailing fish block is re-parsed on the next tail read; clipped
16 MiB tail reads trim forward to the next newline; history rows use the logical
`Font.MONOSPACED` face rather than the pane's own terminal font, so the palette
needs no font plumbing from the terminal view; `CommandCapture` and its test
fixture use JediTerm's `CharUtils.DWC`/`CharBuffer` directly, confined to
`jasper-terminal`; `ShellHistoryScope` hides the shell tag on a row when only one
shell contributed to the snapshot, rather than always showing the shell name as
the tag as the spec described. Task 11 extended the [headless render matrix](design/command-palette/README.md)
with three new states (History recent/query, scope picker) and added
`ShellHistorySearchMeasurement`, a substring-ranking benchmark over a synthetic
50,000-entry snapshot (medians only, no CI threshold; [report](design/command-palette/history-search-measurement.md)).
[Guide](command-palette.md), [design spec](superpowers/specs/2026-09-15-jasper-palette-scopes-design.md),
[plan](superpowers/plans/2026-09-15-jasper-palette-scopes.md). Fresh
`./gradlew check --rerun-tasks` after the final-review fix wave executed all eight
tasks: jasper-app 438 tests passed; jasper-terminal 303 tests, 302 passed and one
existing font skip. Total 741 tests, 740 passed, one skipped, zero failures/errors.
A 2026-09-15 follow-up (`claude/history-hardening`) then made the rewrite
fingerprint always cover the last 64 file bytes before the offset (kept across a
zero-progress read) and added the nushell candidates above. Still user-run: Cmd+R on a real
macOS desktop, input-method composition with the chip present, chip rendering on
the native title-bar theme, and paste/paste-and-run into a real zsh with
bracketed paste enabled. No GUI, merge or push.

**Fast quit (2026-09-14):** On `claude/fast-quit` in `.worktrees/fast-quit` (from main `0c6463a`), the JVM no longer lingers after the last window closes. Nothing called `System.exit`, so exit relied on AWT's auto-shutdown, which waits a full quiet second after the last peer is disposed (JBR 25 `AWTAutoShutdown.SAFETY_TIMEOUT`; measured 1.0 s headlessly) before the JVM tears down. `JasperApplication.shutdown()` now waits, bounded at two seconds, for the command-history file and the exit of every shell it started (`track`), then runs an injected terminator off the EDT; `Main` passes `System.exit(0)`, tests pass a no-op. The bounded pty force-kill fallback is preserved because closed shells' `exitFuture`s are awaited. One INFO line, `Shutdown finished in N ms`, records the timeline in the app log. New headless `JasperApplicationShutdownTest`; `./gradlew check` passed. Desktop check is user-run: type `exit` (with `on_exit = "close_on_success"`) or close the last window and the Dock icon should vanish at once.

**Two modern theme variants (2026-09-14):** On `claude/modern-restore`, the purple theme, the OS-following appearance mode (and its jSystemThemeDetector dependency), custom Alacritty-style palette files and the `[colors]` table are removed. `ui.theme.variant` (`"dark"` default, or `"light"`) selects the bundled FlatLaf Dark + Jasper Dark or FlatLaf Light + Jasper Light pairing; View → Appearance keeps Light/Dark as a temporary override that clears when the saved variant changes; Reload config retries a failed chrome installation. No Java built-in look and feel is used or selectable. `ThemeState` is now `(saved, override)`, `ConfigService.State` carries no palette, `ConfigurationController` has no appearance source, and `AppDirs` has no themes directory. Legacy `[colors]` settings warn as unknown and are ignored ([migration](rebranding.md), [configuration](configuration.md#theme)). `./gradlew check` after the rebase onto `b779c08` with the desk buddy carried across: 656 tests, 655 passed, one known font skip. Headless previews regenerated: `design/mock-ui-dark.png`, `design/mock-ui-light.png`, and the 16-image [command palette matrix](design/command-palette/README.md). Not done: GUI check (user-run), merge, push. The classic vanilla Swing look (`d09b82e`/`5bc288f`) is still to be added back later as a selectable option.

**Modern Jasper restored (2026-09-14):** On `claude/modern-restore` in `.worktrees/modern`, the user chose the modern FlatLaf app as Jasper's baseline again. This commit puts the tree of the rebrand snapshot `e365e8a` (Jasper rebrand + silver turtle icon on the pre-vanilla FlatLaf chrome: native macOS title bar with tabs, Tabler toolbar, coordinated dark/light/purple themes, custom TOML palettes, System appearance) back on top of main. The branch was then rebased onto `b779c08` (main after the desk-buddy merge), re-applying the desk buddy's small hooks in the config loader, snapshot, template, controller, application, terminal window, window chrome and commands onto the modern files; the buddy's own classes, sprite, tests and docs came across unchanged. Main `5bc288f` had merged the rebrand with the vanilla Swing snapshot `d09b82e`. Nothing from that merge besides the vanilla UI and its documentation was dropped; the merge's edits to `rebranding.md` and the spec's visual amendment were vanilla-only. The vanilla Swing implementation stays in history at `d09b82e` and `5bc288f` (standard controls, OS title bar, GNOME 2 icons, `ui.laf` built-in look and feel selection, headless previews under `docs/design/vanilla-swing/`), and a FlatLaf-light/dark `ui.laf` addition to that version is on `claude/flatlaf` at `618f157`. Direction: keep the modern theme as the default and later add the classic vanilla Swing appearance back as a selectable option rather than a replacement. `./gradlew check` on the restored tree before the theme change: 662 tests, 661 passed, one known font skip. No GUI, merge or push.

**Desk buddy (2026-09-14):** On `claude/desk-buddy` in `.worktrees/desk-buddy` (from main `5bc288f`), the pixel-art Jasper desk buddy is implemented: a translucent always-on-top utility window that blinks while idle, greets on hover, drags with a remembered position (`buddy.toml`), raises the last active terminal on double-click, and shows only while a terminal window is open and not minimized. `buddy.enabled` (live) plus View → Show Jasper / palette / right-click toggle him. Sprite pipeline: `packaging/buddy/jasper-buddy.ase` (LibreSprite master) → committed PNG strip; the bootstrap `generate.py` drew the first frames. Deviations from the plan text are recorded in the plan's Deviations log (shared `TomlStateFile` helper, hook placement before `configuration.register`, `syncBuddy` refresh/fault isolation, silent headless path). Approved follow-up (2026-09-14, commits on the same branch): left alone he now sits at 20 s, tucks into his shell at 60 s and sleeps with rising Zs (six new frames, strip 588 × 48), hovering plays a fixed one-second greeting and restarts the boredom clock (waking him first if he is tucked or asleep), postures are absolute deadlines so a lid-close tick resolves in O(1), `hoverExited` is gone and a double-click rather than a single click raises the last active terminal. `./gradlew check` passed: 629 tests, 628 passed, one existing font skip. Right-click now opens a reusable translucent bubble beside him reading Hide Jasper (`BuddyBubbleContent`, `BuddyBubblePanel`, `BuddyBubblePlacement`, `BuddyBubble`) instead of a `JPopupMenu`, on `claude/buddy-bubble` in `.worktrees/buddy-bubble` from main `e9f921c` with `./gradlew check` at 675 tests, 674 passed and the one existing font skip; status messages themselves are still deferred, though the component and its `above` placement are ready for them. Every appearance now opens with a two-second spawn — he fades in over half a second while three new sparkle overlay frames cycle around him (strip 714 × 48), then waves for a second before standing, with hover ignored until it ends — on `claude/buddy-spawn` in `.worktrees/buddy-spawn` from main `0c6463a` with `./gradlew check` at 682 tests, 681 passed and the one existing font skip. He now also notices the terminal itself: activating a Jasper window greets him (`BuddyAnimator.greet`, which `hoverEntered` now delegates to) and typing in one keeps him awake without a wave (`poke`, via one throttled toolkit key listener the application installs with the buddy and removes on shutdown), on `claude/buddy-attention` in `.worktrees/buddy-attention` from main `d11b817` with `./gradlew check` at 690 tests, 689 passed and the one existing font skip; terminal *output* still does not count as activity. Desktop acceptance is user-run: [checklist](superpowers/plans/2026-09-14-jasper-desk-buddy-manual-check.md). [Design](superpowers/specs/2026-09-14-jasper-desk-buddy-design.md), [plan](superpowers/plans/2026-09-14-jasper-desk-buddy.md). No GUI, merge or push.

**Full Jasper rebrand (2026-09-14):** On `codex/jasper-silver-icon`, the application, Java packages (`dev.jasper.app` / `dev.jasper.terminal`), module directories, Gradle tasks/properties, classes, resources, built-in theme IDs, shell-integration markers, logs/config paths, packaging names/identifier, icon filenames, tools, and documentation now use Jasper. Repository directory and origin remain `moray` / `https://github.com/an0nn30/moray.git`. The first forced check found one hard-coded URL endpoint in `WordBoundariesTest` that needed 26 → 27 for the longer name; corrected it and the subsequent `./gradlew check :jasper-app:packageDist` passed. Both module suites executed across these runs: 662 tests, 661 passed, one known font skip. Native verification confirms `Jasper.app`, `dev.jasper.app`, `Jasper.icns`, Jasper-only application JAR names, and a rebuilt `Jasper-1.0.0-macos-aarch64.dmg`. Production JARs contain no legacy package/resource names. [Migration notes](rebranding.md) describe the new settings locations and theme selectors; existing user data is untouched. Historical benchmark manifests retain actual old artifact names/hashes. Windows native packaging and live GUI acceptance remain unrun. Existing unrelated edits were retained through the file moves. No commit, merge, push, Git repository rename, or GUI launch.

**Jasper silver app icon (2026-09-14):** On `codex/jasper-silver-icon`, the user-selected Silver Desk Buddy turtle replaces Eclipse in the editable master, platform SVGs, macOS ICNS, Windows ICO, and all Swing/Dock PNG sizes. The master matches the approved silver study exactly; Windows uses tighter corners and a larger footprint. The generator and [icon guide](../packaging/icons/README.md) now describe Jasper. `./gradlew check :jasper-app:packageDist` passed: XML totals 662 tests, 661 passed, one existing font skip; terminal checks were up to date. Mac app package verification passed and the DMG rebuilt. All 15 ICO frames match their runtime PNGs byte-for-byte. Native Windows packaging and live desktop appearance remain unverified. Application names/identifiers remain Jasper for this icon-only change. Existing unrelated working changes were preserved; no GUI, commit, merge, or push.

**Rail, vault and SSH planning (2026-09-13):** On `codex/rail-vault-design`, the user requested the rail and working SSH connections, with a credential vault first. The `2026-09-13-jasper-vault-product-design.md` (not present in this checkout) consolidates approved conversation decisions: dedicated manager, separate logins and imported encrypted keys, explicit Unlock with optional OS-stored remembered access (seven days, configurable), and inactivity locking (15 minutes, configurable/disabled). Auto-lock retains valid remembered access; explicit Lock clears it. Existing SSH sessions survive either lock. The user-supplied UI mocks (the historical credential-manager folder is not present in this checkout) now govern manager and Add credential layout: navigation sidebar, upper table, lower editor and explicit Save/Revert. Product-spec review and technical design precede implementation plans. This updates sequencing without declaring the Phase 1 trial complete. Existing split-divider working changes are preserved and unrelated. No implementation, GUI, merge or push for this planning work.

**Split divider visibility (2026-09-13):** On `codex/visible-split-dividers`, terminal splits now have a continuous 2px separator inside an 8px resize target, with brighter grips and hover/pressed backgrounds. Purple, classic dark, and light each supply a contrasting separator color. The rendered-pixel regression failed before the change and passes afterward across both orientations and theme replacement; existing nested split, ratio, and zoom tests pass. `./gradlew check` passed: 662 tests, 661 passed, one existing font skip (terminal tests up to date). Headless nested-pane previews are reproducible with `./gradlew :jasper-app:mockUiPreview --args='/absolute/output --splits'`; purple and light renders were visually inspected. The preview explicitly paints the real AWT divider because peerless Swing printing skips it. No native GUI, merge, or push for this follow-up.

**Command palette integration (2026-09-13):** The user approved merging the palette, including the higher placement in `c4f3335`, into `main` and pushing to `origin`. The conflict-free merged result passed `./gradlew check --rerun-tasks` in 18 seconds with all eight tasks executed: 661 tests, 660 passed, one existing font skip, zero failures/errors. Continue from `/Users/dustin/projects/moray` on `main`; the completed feature worktree and branch are being cleaned up after publication. [Guide](command-palette.md), [previews and verification](design/command-palette/README.md). Native desktop acceptance remains user-run; no GUI or packaging was run during integration. The dated development notes below are historical.

**Palette placement follow-up (2026-09-13):** Moved the card upward at the user's request: its center targets one-third of the way down the terminal area, retaining horizontal centering and small-window clearance clamps. The updated position assertion failed before the change; all 15 palette integration tests pass afterward. Regenerated the 24-image theme/state matrix and separate 2× UI-scale artifact; representative normal/narrow renders were inspected. This is a focused follow-up to the full verification below; native GUI checks remain user-run.

**Command palette implementation (2026-09-12):** Complete and independently reviewed on `codex/command-palette-design` in `.worktrees/command-palette`, branched from main `1db31b1`; the main checkout remains unchanged. Cmd/Ctrl+K opens the centered themed palette, Cmd/Ctrl+1–5 runs numbered results, and three shared recents persist across restarts. Commands register through window-owned Swing actions without a plugin framework. All six task gates and final whole-branch/scoped review are complete through runtime fix `28a7ff0`. Final review corrected selected-result visibility after query/reorder changes in short scrolled cards; two actual-viewport regressions reproduced the defect before the fix. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 661 tests, 660 passed, one existing font skip, zero failures/errors. Actual purple/classic/light renders and separate 2× UI scaling were inspected; matching-only medians were 18.3–93.9 µs over 1,000 indexed commands. Branch-wide diff checks and source hygiene passed after temporary-report cleanup. [Guide and extension example](command-palette.md), [visuals, measurements and review record](design/command-palette/README.md), [plan](superpowers/plans/2026-09-12-jasper-command-palette.md), [native acceptance checklist](superpowers/plans/2026-09-12-jasper-command-palette-manual-check.md). Native focus, IME, accessibility, Windows desktop and real restart acceptance remain user-run. No GUI, packaging, merge or push.

**Default purple theme (2026-09-12):** `jasper-dark-purple` now supplies the default app chrome and terminal colors, including the dark half of Follow System. Explicit `jasper-dark` remains classic; `jasper-light` is unchanged. Custom files keep their existing fixed classic palette inheritance. Source defaults, generated config template, root example and documentation are updated. `./gradlew check` executed both modules: 585 tests, 584 passed and one existing font skip. Actual purple/classic/light Swing + ANSI previews were rendered headlessly and inspected; independent review found no actionable issues. The Mac app/DMG was rebuilt and package verification passed with the official Eclipse icon. The user has approved merging the completed icon/theme work into `main` and publishing it to `origin`. No live settings, GUI or installation changes were performed. Historical palette/render folder `design/jasper-dark-purple/` is not present in this checkout.

**Official app icon (2026-09-12):** The user-approved brighter Eclipse design is integrated into native packaging and runtime windows. Native macOS ICNS and Windows ICO inputs, runtime window/Dock PNGs and a reproducible SVG export script are in place. macOS uses one 100px margin on the 1024px canvas and a rounded squircle; Windows uses tighter corners and a 32px margin. `./gradlew check :jasper-app:packageDist` passed: 580 tests in result XML, 579 passed and one existing skip (unchanged terminal checks up to date). Native Mac image/DMG, bundle icon bytes, strict signature and all icon containers were verified; independent review found no issues. Live Mac Dock/Cmd+Tab and Windows desktop/native packaging acceptance remain pending. The 444 rejected/exploratory design files were moved out of the repo to Trash after verifying the official master and all archived hashes; this is explicitly authorized cleanup and supersedes the earlier preservation note below. Main integration and publication are authorized; no GUI launch or installation performed. [Official icon assets and evidence](../packaging/icons/README.md).

**Current readiness work (2026-09-12):** Logging, terminal hardening and repeated packaged measurements are integrated locally on `main` after user approval. Continue from `/Users/dustin/projects/moray`. Task and whole-branch/scoped reviews are complete. Published `cc3f957` passed [three-platform CI](https://github.com/an0nn30/moray/actions/runs/34724932663). Merged verification exposed one incomplete-input test wait, corrected by test-only `7481114`; the fresh merged check passed 576 tests (575 passed, one known skip), all eight tasks executed. All 312 pre-existing untracked icon-option files were preserved byte-for-byte with unchanged permissions. The URL-test correction and integration bookkeeping remain local; main has not been pushed. The verified DMG and 896-file benchmark archive were preserved under `build/readiness-2026-09-12/` before cleaning up the completed feature worktree. Runtime code is unchanged from measured `8d84e7d`; the full memory comparison remains attributed to `10cc444`. [Readiness handoff, measurements and remaining native/trial gates](terminal-readiness.md). The two-week trial has not started.

The following dated milestone notes describe earlier verification; the readiness handoff above is the current state.

**Plan 4d implementation:** The approved macOS and Windows packaging work is fully reviewed, verified and integrated locally on `main` through `0162293`, including direct README build/DMG instructions. Continue from `/Users/dustin/projects/moray`; the merged feature worktree is being cleaned up. [Build commands and native checklist](packaging.md), [design](superpowers/specs/2026-09-12-jasper-plan-4d-packaging-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4d-packaging.md). Gradle creates and verifies a native image with JBR 25, then a macOS DMG or portable Windows ZIP. Native macOS metadata, runtime, dependency payloads, strict bundle seal and DMG integrity were verified, including alternate version and paths with spaces. Windows/unsupported-host execution and desktop acceptance remain unrun. The initial package version is `1.0.0` because macOS jpackage rejects a zero major version; this remains a development build. No generated bundle metadata is changed to satisfy verification: vendor and architecture are read from the bundled runtime and native files. The launcher now removes inherited terminal identity variables before explicit configuration overlays and supplies absent/blank macOS LANG with `en_US.UTF-8`. The example and configuration documentation are updated. `origin` is configured at `https://github.com/an0nn30/moray.git`; no push has been performed. Broader app logging remains pending.

**As of:** 2026-09-12. Plan 4d and the README build instructions are integrated locally on `main`. Fresh merged `./gradlew build :jasper-app:packageDist --rerun-tasks` executed all 16 tasks in 55 seconds: 508 tests, 507 passed, one known font skip, no failures/errors. Native image verification and DMG integrity checks passed. The main checkout was clean before integration. The current DMG is `jasper-app/build/packaging/dist/Jasper-1.0.0-macos-aarch64.dmg` in the main checkout. Windows execution, native UI, benchmark and daily-use acceptance remain open.

**Shell-exit follow-up:** `terminal.on_exit` now supports `keep_open` (the default), `close_on_success` and `close`. Policy reloads apply to future exits in existing and pending panes without changing view options; retained output stays open. Closure follows the existing pane → empty tab → empty window lifecycle and preserves siblings. This bounded follow-up uses implementer TDD/self-review plus independent root review, with no new architecture or historical-plan rewrite. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 463 tests, 462 passed, one known font skip, no failures/errors. Source hygiene and diff checks passed. Independent review approved with no findings; merged main reproduced the full verification. The merged branch/worktree are cleaned up; [native shell-exit checks](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md#shell-exit-follow-up) remain user-run. At that milestone, remaining Plan 4 work was custom themes/system appearance, logging/launcher cleanup and packaging; current Plan 4c progress is recorded below.

**Compact padding/config example follow-up:** The terminal pane now uses a 4px inset on every side, superseding the old screenshot-matched 24px inset. Initial window sizing includes the same 8px total per dimension. The root [copyable example](../config.example.toml) contains all supported non-shortcut defaults and optional commented platform-specific shortcut examples. Independent review approved with no findings. Headless layout and actual-file parse coverage passed; [dark/light renders](design/compact-padding-comparison.md) were visually inspected. Native visual sizing remains user-run.

**Plan 4b work:** Full terminal configuration is integrated on `main`. It was developed on `codex/plan-4b-terminal-config`, based on `ab672fa`; the merged worktree and branch are cleaned up after verification. All four task reviews approved through `9d8b9b4`; final whole-branch review approved after the documentation-only correction `7ab277b`, with no open findings. Fresh `./gradlew check --rerun-tasks` executed all eight tasks: 442 tests, 441 passed, one known font skip, no failures/errors or test output. Both modules passed source hygiene and the branch passed diff checks. Native acceptance remains user-run. See [design](superpowers/specs/2026-09-12-jasper-plan-4b-terminal-config-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4b-terminal-config.md) and [native checklist](superpowers/plans/2026-09-12-jasper-plan-4b-manual-check.md).

**Plan 4a progress:** Saved settings and live reload are integrated on `main`, with all task and final reviews complete through `acd5ea3`. Fresh full verification: 394 tests, 393 passed and one known font skip, no failures/errors. Actual status renders passed visual inspection in dark/light at normal/narrow widths. Final whole-branch review approved after correcting Windows-specific test fixtures; no findings remain open. Native and Windows execution remain unverified. See [configuration usage](configuration.md), [status renders](design/config-status-comparison.md) and [native acceptance](superpowers/plans/2026-09-11-jasper-plan-4a-manual-check.md).

**Current follow-up:** Default title/tab height is now 38px, configurable from 28–72 through View → Tab height… for the current window. Both Swing and native height use the same value; reset restores 38, cancellation preserves the previous value. View height changes remain temporary; `window.tab_height` supplies the saved default and applies live when edited. Opening and closing tabs and the selected underline animate with a shared 180ms eased settle; selection/focus/launch remain immediate and timers stop when idle/hidden/disposed. The existing shortcut engine now provides Cmd/Ctrl+1–9 and Cmd/Ctrl+{ / } (Shift+brackets), with plain Ctrl for tab navigation outside macOS. Unrelated shortcut behavior is retained. [Design](superpowers/specs/2026-09-11-jasper-tab-motion-design.md), [plan](superpowers/plans/2026-09-11-jasper-tab-motion.md), [native checks](superpowers/plans/2026-09-11-jasper-tab-motion-manual-check.md).

**Active revision:** The user's exact mock supersedes the earlier separate title and two-tone toolbar choices. Implemented title-bar tabs, horizontal neutral toolbar, and seamless terminal/status surface. See the [design](superpowers/specs/2026-09-11-jasper-mock-ui-design.md), [implementation plan](superpowers/plans/2026-09-11-jasper-mock-ui.md), [original reference](design/mock-ui-reference.png), and [measured comparison](design/mock-ui-comparison.md). The current compact height supersedes the original mock geometry; measured colors are retained. Font metrics, real grid dimensions, native window appearance and motion still require visual acceptance.

Read [AGENTS.md](../AGENTS.md) for repository rules, [README.md](../README.md) for running, and the [Phase 1 design](superpowers/specs/2026-09-10-jasper-phase-1-terminal-design.md) for binding product requirements. Normal GUI acceptance is user-run. The user explicitly authorized controlled readiness benchmarks; game/VM prerequisites were checked before every run.

## Plan 4c implementation — custom themes and system appearance

Implemented on `codex/plan-4c-themes` and integrated locally on main after user approval. The completed feature worktree and branch are being cleaned up after successful merged verification. All four task reviews passed; whole-branch review findings were resolved in `3c3af48`, and scoped re-review approved with no new findings. See [design](superpowers/specs/2026-09-12-jasper-plan-4c-themes-design.md), [implementation plan](superpowers/plans/2026-09-12-jasper-plan-4c-themes.md), [configuration](configuration.md), [headless comparison](design/plan-4c-themes/comparison.md) and [native checklist](superpowers/plans/2026-09-12-jasper-plan-4c-manual-check.md). The main checkout was clean at integration; the updated example is now available at the repository root.

Resolved themes separate chrome from custom terminal/padding/status colors. Follow System, explicit choices, accepted config palettes and source warnings now reach retained/hidden/zoomed/pending/new views. Palette-only updates preserve delegates and sessions; configuration refresh preserves custom status colors. Main alone creates the deferred production source, and controller close removes its listener and guards late callbacks. Headless source tests use synthetic bindings only.

Task 4 deviations: remember latest OS reading outside the committed reducer so failed LAF installation preserves effective theme/menu while Follow System can retry without a second event (controller-approved). Native title callback stays at the existing `MacTitleBar.install` ownership boundary and extracts resolved chrome. Pending panes receive palette backgrounds before attachment. Status palette-derived readability also applies when a custom palette equals the opposite built-in palette. Installation-specific exceptions keep arbitrary application callback errors outside the theme failure-reporting catch, proven by a focused RED/GREEN regression. The existing Task 1 configuration bridge is removed. The detector-only JNA exclusion is retained because pty4j supplies both JNA 5.14.0 modules.

Fresh `./gradlew check --rerun-tasks` passed with all eight tasks executed: 502 tests, 501 passed, one known `FontSetTest.fallsBackWhenPrimaryCannotDisplay()` skip, zero failures/errors. Both modules passed source hygiene (152 Java files) and `git diff --check` passed. Native detector/GUI/physical screens/benchmark remain unrun; all native checklist items are unchecked. Logging/launcher cleanup and packaging remain pending. Final review correction `3c3af48` makes explicit Reload retry unchanged desired theme state through the existing delivery guards, makes service-test timestamps deterministic, and adds the combined malformed-main-config/theme-poll regression. Both actual OS and saved-config failure paths recover through the real Reload action. Root final verification ran all eight tasks in 13 seconds: 506 tests, 505 passed, one known font skip, zero failures/errors; scoped re-review approved all three findings. No review findings remain open.

## 1. Goal and delivery phases

Jasper is a Java Swing terminal workstation with a MobaXterm-style layout, built to replace `~/projects/conch` as the daily terminal. Product phases: terminal → SSH session management → credential vault → SFTP → tunnels → plugins and editor. Reuse tested non-UI Java from `~/projects/termlab-bundle` in later phases. Do not redesign the terminal foundation before the Phase 1 switch-over test.

| Plan | State |
|---|---|
| 1 — Terminal core | Complete on main (`6daa61f`..`d0ccbfb`) |
| 2 — Terminal completeness | Complete on main (`196e24d`..`0ce4add`) |
| 3 — App chrome | Implemented and reviewed on main; all automated checks passed; native acceptance pending |
| 3.5 — macOS chrome, themes and toolbar | Integrated on main and fully reviewed; native acceptance pending |
| Screenshot UI revision | Implemented and fully reviewed; compact/motion follow-up implemented and fully reviewed; native acceptance pending; integrated on main |
| 4a — Saved settings and live reload | Integrated on main and fully reviewed; native acceptance pending |
| 4b — Full terminal configuration | Integrated on main and fully reviewed; native acceptance pending |
| 4c — Custom themes/system appearance | Integrated on main and fully reviewed; native acceptance pending |
| 4d — Native packaging/launcher environment | Integrated locally on main and fully reviewed; native acceptance pending |
| Remaining Plan 4 | App logging integrated locally on main and reviewed |
| Terminal hardening | Implemented, reviewed, measured and integrated locally on main; CI passed; native acceptance/daily-use gate pending |

Plan 3 design: [application design](superpowers/specs/2026-09-11-jasper-plan-3-app-chrome-design.md). Execution: [implementation plan](superpowers/plans/2026-09-11-jasper-plan-3-app-chrome.md). The per-plan scratch workspace was removed after final review; this handoff, completed plan checkboxes and git history preserve the record. Do not restart completed plans: Plans 4a–4d are integrated on main. Logging and hardening are now integrated locally on main. Next are native acceptance and then the daily-use gate; authorized publication and three-platform CI are complete.

Plan 3.5 was developed on `codex/plan-3-5-chrome-themes` and merged locally. That milestone is available in `/Users/dustin/projects/moray` on `main`; Plan 4a is also integrated there. [Title/theme design](superpowers/specs/2026-09-11-jasper-plan-3-5-titlebar-themes-design.md) and [execution plan](superpowers/plans/2026-09-11-jasper-plan-3-5-titlebar-themes-implementation.md) cover the first runnable deliverable. [Toolbar study](design/plan-3-5-toolbar-study.html) is an illustrative discussion aid; the user selected B (fuller, two-tone colored icons), now included as Task 4.

Previous milestone: [Plan 3.5 — macOS chrome, themes and toolbar](superpowers/plans/2026-09-11-jasper-plan-3-5-chrome-and-themes.md). The user has tried Plan 3 during development and reports that it looks good overall; this is qualitative feedback, not a claim that every native checklist item or benchmark was completed.

## 2. Implemented behavior

The existing terminal retains ligatures, fallback fonts, wide characters, colors, cursor modes, Option input, mouse reporting, scrollback, selection, clipboard, bracketed paste, prompt jumps, OSC integration and clickable links.

Plan 3 adds:

- Multiple independently owned windows and tabs; close buttons, middle-click close, drag reorder, F2 rename with shell-title override retention.
- Pure split-tree model; nested right/down splits, directional focus, divider ratios, zoom/restore without restarting shells.
- Shared action catalog and keybinding parser; application shortcuts before terminal encoding; native editing in find fields retained.
- FlatLaf menus, seven-button toolbar with Tabler outline SVGs, view visibility controls, light/dark chrome, focused shell/directory/dimensions status, and inactive-pane dimming.
- Async find bar with debounce, case/regex options, next/previous, count and retained error state, Escape/close, and stale-result rejection.
- Local context menus that preserve terminal-program mouse ownership.
- Per-pane font controls, screen-preserving history clear, working-directory inheritance with launch-time validation.
- Background shell launch with close-before-completion cleanup; native Quit routes through app cleanup.

Plan 4a now reads saved TOML settings, polls for changes, enables Settings/Reload and exposes positioned diagnostics through status. Configured height, toolbar/status visibility, font size, built-in theme and shortcuts apply live across owners. Unrelated reloads preserve temporary View/font overrides; Settings creates a template only when absent. The integrated UI baseline on main predates this configuration behavior.

Plan 4b adds live font family/fallback/ligatures/line height, Option-as-Meta, cursor defaults, copy-on-select, configurable inactive dimming and visual/sound/none bells. Retained and pending views use current live settings without restarting sessions; per-field manual size/theme overrides survive unrelated edits. Shell executable/arguments/environment and scrollback are captured for each new request. New windows use configured rows/columns and shared font metrics, constrained to the usable display. The expanded TOML template and [configuration guide](configuration.md) document all fields, validation and lifecycle rules.

The earlier Plan 3.5 implementation (visual geometry superseded below) includes:

- Live dark/light terminal palettes and coordinated FlatLaf themes (`09e382e`, `6bdd298`). Existing hidden/zoomed panes, delayed launches and new windows use the selected theme while retaining sessions, fonts, selection/find state and split ratios. Explicit terminal truecolor remains unchanged.
- A slim macOS title surface (`7833692`) using the decorated frame and supported public properties. Native traffic lights and window behavior remain owned by macOS; separate tabs and toolbar sit beneath the title. Other platforms retain their existing decorations.
- The user's selected toolbar B (`116709b`): 28-pixel two-tone Tabler icons, distinct action colors in both themes, labels and visibility modes retained. Settings/Reload remain muted while disabled.

Actual headless Swing previews: [dark](design/plan-3-5-titlebar-dark.png) and [light](design/plan-3-5-titlebar-light.png). Native traffic lights are absent from these renders. Native acceptance remains user-run; Plan 4 retains configuration, custom theme files, persistence, automatic system appearance and packaging.

The screenshot revision now supplies:

- A single 54px title/tab row with native traffic-light space, persistent tab controls, overflow navigation and a clipped trailing title. Public JBR title-height integration retains native decoration with a fallback.
- A 53px horizontal toolbar using 16px neutral Tabler outlines, inline labels, highlighted New tab, separators and right-aligned Settings/Reload. Existing actions, menus and visibility modes remain functional.
- A 30px status bar that shares the terminal background, live shell/path/grid metadata and running dot; the current 4px terminal padding supersedes the old 24px mock geometry, and the app default/reset font size remains 16.
- Measured dark surface and visible ANSI accent colors, with equivalent light geometry. Actual headless previews: [dark](design/mock-ui-dark.png), [light](design/mock-ui-light.png). Reproduce with `./gradlew :jasper-app:mockUiPreview`.

## 3. Fixes included during Plan 3

- `openingLink` capture clears on new press and focus loss; a missed release cannot swallow a later gesture.
- Alternate-screen transitions invalidate selections, matches, pending find and viewport row references.
- A session row epoch reconciles history/alternate/reflow resets missed during temporary view detachment.
- Search keeps one bounded per-view executor through reparenting; its thread retires after idle. New requests supersede queued work; cancellation cannot multiply workers on repeated zoom/reparent.
- Expired prompt rows are physically pruned during history eviction.
- PTY close returns promptly to the EDT, sends Unix hangup, applies a bounded force fallback, and closes acquired streams. A non-daemon cleanup worker survives final-window disposal long enough to perform fallback. A real headless child ignoring HUP/TERM is covered on Unix.
- Find navigation requested during debounce/inflight search is retained; invalid regex errors persist until the query changes. Reparenting clears canceled search state.
- Copy action enablement checks selection presence in constant time without extracting text or taking the buffer lock.
- Font and nested-split minimum dimensions propagate to the native frame; PTY, view and emulator agree on the pinned JediTerm minimum of 5 columns × 2 rows.
- Appearance menus synchronize their selection with the active global theme when opened, including changes made from sibling windows.
- Long directory/status text and tab names cannot inflate native minimum width; full tab titles remain in tooltips.
- Global shortcuts are installed on the root pane, with native text-field editing retained.
- F13–F24 override parsing uses Java's separate high-function-key range; actual F13/F24 strokes are regression-tested.

## 4. Verification still required

1. User-run [compact tab/motion checklist](superpowers/plans/2026-09-11-jasper-tab-motion-manual-check.md), then [screenshot UI checklist](superpowers/plans/2026-09-11-jasper-mock-ui-manual-check.md): native controls, title-tab hit testing, dragging/double-click, fullscreen, scaling and exact visual comparison in real windows. The earlier Plan 3.5 appearance checklist is superseded by this reference. Also retain the [Plan 3 acceptance checklist](superpowers/plans/2026-09-11-jasper-plan-3-manual-check.md): native windows/menu/Quit, split/zoom/focus/dividers, tab gestures, find, clipboard, links, mouse reporting, directory inheritance and chrome readability. Headless tests do not establish these native visual results.
2. Packaged readiness benchmarking is recorded in the [measurement report](benchmarks/2026-09-12-terminal-readiness.md). Final runtime `8d84e7d` passed three full throughput runs at 36.91–37.28 MB/s. The 35 MB/s floor passes; 45 MB/s target remains unmet. These controlled fixtures do not replace normal native acceptance.
3. [three-platform CI run](https://github.com/an0nn30/moray/actions/runs/34724855954) passed macOS, Ubuntu and Windows at `c793925`. Windows ConPTY/packaged desktop and Linux desktop/font behavior still require native acceptance. The branch was published and then merged locally; main has not been pushed.
4. After Plan 4, satisfy the Phase 1 manual checklist and two-week Jasper-only trial. Record every reason to reopen conch and fix the blockers before SSH begins.

Known skip: `FontSetTest.fallsBackWhenPrimaryCannotDisplay` skips on this Mac because its probe finds no differentiating glyph. Nerd Font-specific fallback tests run with a suitable installed font and skip on systems lacking it.

## 5. Remaining terminal hardening

The integrated readiness work resolves the carried-over gesture ownership, bounded logical-line traversal, idle frame/blink work, pure mouse-report snapshots, visible search scanning, EDT browser dispatch, multi-notch wheel and selection integrity findings. See the [handoff](terminal-readiness.md) for evidence. Remaining limitations:

- Search does not span physical soft-wrapped rows. Java regex cancellation remains best effort; a pathological running regex can delay the next request in that pane, although queued work and worker allocation are bounded.
- Width reflow or history erase clears row-based selection/matches/prompts and returns to live. This is intentional with JediTerm's row model.
- Strikethrough is unsupported by jediterm-core 3.76 TextStyle.
- macOS font fallback uses JBR/system cascading for CJK/emoji; explicit fallback chiefly affects missing Nerd Font glyphs.

Readiness added astral/edge/overlapping search, bounded wrapped continuation, button ownership and multi-notch regressions. Retained coverage opportunities include large-history indicator geometry, empty-scrollback viewport, copy-on-select, alternate-screen wheel/arrows, paste-to-live, Shift+PageDown and match colors. Add behavioral regressions when working in these areas, not tests that mirror source constants.

## 6. Native acceptance and remaining Plan 4 work

The screenshot revision supersedes Plan 3.5 geometry and artwork. User visual/native acceptance remains open; the user approved integration and starting Plan 4. The integrated UI baseline runs from `/Users/dustin/projects/moray` with `./gradlew :jasper-app:run`. That checkout now includes Plan 4a live reload and Plan 4b full terminal configuration. No complete pixel-identity claim: native frame controls/focus/font rasterization are not headless-verifiable, and the existing terminal renderer computes 91 × 35 cells versus the mock's 100 × 40 at the measured content size. See the comparison report for exact prompt ink bounds. Built-in palettes and live theme application have moved forward from Plan 4.

Plans 4a–4d supply saved settings, full terminal configuration, custom themes/system appearance, launcher cleanup and native packaging. The integrated readiness work adds logging and the recorded interaction/performance hardening. [Measurements](benchmarks/2026-09-12-terminal-readiness.md) report baseline/final memory, allocation, GC, throughput, EDT delay, cleanup and diagnostic limits. The controlled Java fixture uses argument lists on both platforms; Windows execution remains unverified.

Remaining opportunities: logical-pixel font-cell rounding, cosmetic ShellIntegrationFilter hold-limit prefix counting, and deeper retention/dependency/font-cache profiling if real usage warrants it. No speculative heap or backend change is part of this slice. Three-platform CI passed; normal native acceptance remains necessary before the two-week trial.

## 7. Decisions and implementation deviations

- Keep JediTerm, own renderer, two one-way modules, no emulator abstraction/plugin API.
- Option+Left/Right remains ESC[1;3D / ESC[1;3C, not ESC b / ESC f.
- OSC8 schemes remain http/https/ftp/mailto; detected text may include file URLs. Refused OSC8 targets do not fall through to text detection.
- macOS Command-click links takes precedence over program mouse reports; Shift provides local mouse behavior; horizontal Shift-wheel on macOS is ignored.
- The user's approved Phase 1 direction and Plan 3 go-ahead were used for execution; a supplemental written design records concrete app contracts.
- On Linux/Windows, cmd already means Ctrl+Shift. Defaults explicitly written cmd+shift add Alt to avoid collisions; user overrides parse literally and collisions are errors. macOS defaults are unchanged.
- Settings/reload and saved configuration were deferred from Plan 3 to Plan 4a and are now implemented. Plan 3.5 supplied coordinated built-in themes and macOS title-bar work; automatic system appearance remains a later Plan 4 deliverable.
- Four direct multi-pane carryovers were included; the remaining hardening list above is follow-up scope.
- Native acceptance is user-run per newly merged AGENTS.md. No agent GUI launch or benchmark followed discovery of that rule.
- The user selected the local `~/projects/tabler-icons` repository. Seven outline assets and full MIT license are bundled; SOURCE.txt records the source commit and theme-color adaptation.

## 8. Workflow and historical Plan 3 verification

Superpowers spec/plan → task implementer with TDD → separate task review → scoped fix/re-review → final branch review. Reviewed commits: models `a75d5fa`; terminal hooks `56b251d`; lifecycle fixes `73f105c`; desktop `acfc79f`; desktop review fixes `6fac903`/`dcc0b00`; final minor fixes `dda35e2`. Task reviews and final whole-branch review approved. Final fixes synchronize appearance menu state and make the forced-close test wait for installed signal traps. A mutation check confirmed that removing force fallback makes the latter test fail; production cleanup was restored before verification.

Root final verification on `dda35e2`: all 8 Gradle tasks executed successfully in 8 seconds; 277 tests, zero failures/errors, one expected font skip. Source-hygiene and `git diff --check` passed. No compiler warnings. Final reviewer compared the exact default key catalog with the parent spec and found it aligned; selected shortcut regressions for live overrides belong in Plan 4, without duplicating source constants in tests.

Plan 3 was merged locally and its feature worktree/branch removed after merged checks passed (277 tests, zero failures/errors, one expected skip). Plan 3.5 is now integrated on main; continue native acceptance from `/Users/dustin/projects/moray`, retaining the daily-use gate.

## 9. Execution rulings preserved from the completed Plan 3 ledger

These are historical Plan 3 scope decisions. The user's subsequent Plan 3.5 amendment above changes the sequencing of built-in theme work.

- The existing approved Phase 1 design plus the user's Plan 3 go-ahead authorizes execution; use a supplemental execution design instead of restarting approval — avoids repeating settled layout decisions — costs rework if the detailed choices differ from user intent.
- Non-mac defaults containing explicit cmd+shift add Alt because cmd already means Ctrl+Shift — prevents duplicate destructive shortcuts — costs non-mac shortcut familiarity; macOS unchanged.
- Settings/reload remain visibly disabled and status says Built-in defaults until Plan 4 — no configuration subsystem in Plan 3 — costs waiting for settings until the next planned milestone.
- Light/dark chrome is selectable in Plan 3; automatic system appearance remains Plan 4 with config — keeps scope aligned — costs manual appearance selection temporarily.
- Fold four immediate STATUS carryovers into Task 2 (lost link gesture, alternate-buffer invalidation, prompt pruning, child termination), retain remaining performance/selection findings for follow-up — these directly affect reliable multiple panes — costs additional Task 2 work and later hardening before Phase 1 acceptance.
- GUI smoke and final benchmark are user-run under newly added AGENTS.md — respects desktop-operation restriction — costs leaving visual/performance acceptance pending after headless completion.
- Keep disabled Settings/Reload tooltips user-facing, without internal Plan 4 numbers — developer instructions exclude implementation details from product flows and implementation already explains unavailable config — costs losing an internal milestone reference in tooltips, which remains in docs. Corrected ambiguous plan phrase before re-review.

## 10. Plan 3.5 execution rulings

- The user's go-ahead authorizes implementation of the requested title/theme direction with reviewable initial colors — avoids repeated approval of the already requested milestone — costs visual rework if the initial palette differs from intent.
- Start with slim custom title surface and separate tabs unless the pending optional layout answer changes it — limits geometry changes before toolbar discussion — costs a later layout revision if tabs should be integrated.
- Deliver title/themes as 3.5a and discuss toolbar artwork as 3.5b before implementing that selection — honors the explicit request to discuss toolbar appearance — costs leaving existing icons during the first deliverable.
- Reuse supported decorated-frame macOS full-content properties and FlatLaf bounds rather than add JBR API/native dependencies — verified in pinned source and retains native behavior — costs revisiting integration if native user checks reveal a platform limitation.

The third ruling was superseded when the user selected toolbar B: Task 4 delivers the chosen artwork in this same milestone. The later screenshot explicitly resolves the title layout: integrated title-bar tabs replace that initial layout.

Task reviews: palettes `09e382e`, application themes `6bdd298`, title surface `7833692`, toolbar `116709b` approved. Final whole-branch review covered `942f5f7..0b8461b`. Its only minor finding was closed by `f487631`: the rendered icon test now distinguishes soft fields from enclosed face fills. A mutation removing the four face fills failed the new test while the earlier aggregate test still passed; all resources were restored. The scoped fix review approved with no residual findings. The alpha-specific test runs in dark mode against shared SVG opacity layers; existing color tests cover both themes.

Root final verification on `f487631`: `./gradlew check --rerun-tasks` ran all eight tasks in 11 seconds, with 299 tests, zero failures/errors and one known font skip. Source hygiene and `git diff --check` passed; no compiler warnings. Production code/resources are unchanged since `116709b`. The plan-specific scratch workspace is removed after final review; this handoff, plan checkboxes, committed previews and git history preserve the result.

The user selected local integration. The completed feature worktree and branch are cleaned up after merged verification succeeds. No GUI or benchmark was launched and no remote is configured. Native title-bar/appearance checks and the terminal daily-use gate remain open.

## 11. Screenshot revision execution rulings

- Treat the supplied mock and explicit exact-match instruction as the approved visual design, superseding earlier colored-toolbar and separate-title decisions — avoids asking approval for the mock the user just selected — costs rework for any mistaken measurements.
- Interpret the144dpi screenshot as2x and convert its monitor ICC colors to sRGB for Swing values — aligns logical sizes and displayed colors — costs refinement if the mock's intended logical scale differs.
- Add public jbr-api1.9.0 native title-height integration with fallback — exact54px native-control placement needs more than oldfullcontentproperties — costs native-platform validation and a small app dependency.
- Use sampled visible green/blue/cursor colors and refine toolbar text to11.5px after actual-render comparison — user’s exact mock takes precedence over the initial retain-ANSI/12px approximation — costs changing those built-in dark ANSI accents and potential font refinement on other systems.

Both task reviews and the final whole-branch review (`182b9fb..be39bbf`) approved without actionable findings. The screenshot-only production/test commit was `63a3325`; the follow-up below changes it. Fresh root verification and independent reviewer XML checks confirm 308 total tests, 307 passed, one known skip. This plan’s scratch workspace is removed after review; the committed plan, comparison report, previews and this handoff retain the evidence and rulings. The feature worktree remains available for user inspection and integration approval.

## 12. Compact tab follow-up

Task 1 (`0c4ab43`) approved without findings: shared live native/Swing height, current-window numeric control, real platform shortcut/override dispatch, 80 app tests. Task 2 (`e46eaae`) implements 180ms entry/underline motion; nine deterministic real-strip tests cover intermediate/final rendered positions, retargeting, metadata continuity, overflow/resize/removal and idle/lifecycle cleanup. Fresh root full verification is recorded at the top. The updated previews show settled 38px geometry; the original 54px mock remains a historical reference. Persistence remains Plan 4.

- Treat the user's concrete follow-up as authorization to implement these reversible refinements — avoids repeating approval already given — costs visual rework if chosen defaults differ from intent.
- Default to 38px height and 180ms motion with 3.5% overshoot — makes the row materially shorter and movement quick with a small settle — costs tuning after native viewing.
- Provide a live current-window height control; persistence stays Plan 4 — keeps existing session-only appearance behavior consistent — costs reapplying height after restart.

Final whole-branch review (`182b9fb..7449ac4`) found one integration issue: the active title’s preferred width changed tab-strip allocation and prematurely settled animation. The single fix wave `20764b3` preserves motion when allocation changes leave tab coordinates valid, and adds three real MacTitleBar/JRootPane regressions. Scoped review (`7449ac4..20764b3`) marked it addressed with no new findings. Twelve motion tests now pass; root fresh final verification is 328 total, 327 passed, one known skip. Native smoothness, controls and physical keyboard-layout acceptance remain user-run. The plan scratch workspace is removed after review; committed reports, plan, source and this handoff preserve the result. At that milestone the feature remained in `.worktrees/mock-ui`; section 14 records its subsequent integration and removal.

## 13. Reverse close animation

User-requested bounded follow-up `0f4cd74` reverses the existing motion for visible tab closure. The tab and shell leave the live model immediately; a disabled departing entry contracts while following tabs fill its space and the underline targets the new selection. Closing during entry uses its current width; consecutive closes have independent deadlines. Completed, hidden, reordered, cramped/overflow and disposed departing visuals are removed. Final-tab closure is not delayed.

Four new real MacTitleBar/JRootPane regressions cover active/inactive closes, closing during entry, consecutive deadlines and cleanup. Initial three regressions failed against the prior behavior; the empty-owner timer regression also failed before its fix. Final covering tests:27 passed. Fresh full `./gradlew check --rerun-tasks`:332 total,331 passed, one known font skip; all eight tasks executed, no warnings. Source hygiene and diff checks pass. Independent review of `600c378..0f4cd74` approved without actionable findings; native reverse-motion acceptance remains user-run. This bounded extension updates the existing spec without a new subsystem or plan.

## 14. Approved integration and Plan 4 start

The user explicitly approved merging all current UI work into main and beginning Plan 4 with saved settings and live reload. Main received the complete UI branch at `1bd8b49`. Fresh merged verification passed 332 tests (331 passed, one known font skip), and the completed mock-ui worktree/branch were removed. Plan 4a then started in the isolated `.worktrees/plan-4` worktree. Native UI/benchmark/daily-use acceptance remains open and is not implied by merge approval.

Plan 4a covers existing live controls only: tab height, toolbar/status, font size, built-in theme and shortcut overrides. Runtime View choices remain temporary; saved defaults are in the user-edited file. Additional terminal options, custom themes, system appearance and packaging follow in separate runnable slices. TomlJ 1.1.1 is selected for precise TOML source positions.

## 15. Plan 4a implementation and execution rulings

Parser/paths/CLI: `8c6fa0e`. File service/template: `e25b4c8`. Live application integration: `a18de61`. Review fixes: `9c0ae64` (all modifiers in rendered toolbar hints and removal of obsolete configuration tooltip). Task reviews are complete with no open findings. Full check after fixes executed all eight Gradle tasks: app 158 passed; terminal 235 passed and one known skip. Source hygiene and diff checks passed. No terminal-module changes were needed.

The configuration service retains its parse platform for application of shortcuts. Existing Action-backed toolbar hints and pending-pane hooks were reused; the toolbar formatter was corrected after review exposed its omission of extra macOS modifiers. Native editor, real window and daily-use acceptance remain user-run. The user selected local integration on 2026-09-12; the implementation is now on main.

- Ruling: The user approved local UI merge and starting the proposed first Plan4 deliverable; execute without repeating design approval — follows explicit go-ahead — costs rework if detailed choices differ from intent.
- Ruling: Split Plan4 into runnable stages, first covering existing live controls and config lifecycle — follows the accepted saved-settings/live-reload first step and repository one-deliverable-per-plan rule — costs waiting for additional terminal options, custom themes, automatic appearance and packaging.
- Ruling: Saved defaults are file contents; runtime View choices remain temporary and unrelated reloads preserve them — respects the parent prohibition on unasked config writes — costs editing the file to persist a menu adjustment.
- Ruling: Select TomlJ1.1.1 for positioned diagnostics instead of Jackson binding — upstream API provides parser and key positions directly, an alternative allowed by the parent — costs one parser dependency and validation code.
- Ruling: Invalid or colliding shortcut values fall back to the entire default binding map, while unknown action names warn and are ignored — bindings are mutually constrained and per-action fallbacks can introduce collisions — costs resetting valid custom bindings in the same invalid map until corrected.
- Ruling: Expose the service’s immutable parse platform to the controller and use it to materialize snapshot bindings — resolves the task review’s cross-platform collision concern without changing immutable snapshot data — costs a small package-private accessor.

Final whole-branch review (`1bd8b49..b23ee71`) found one P2 test portability issue: Windows-invalid filename characters and Unix-only expected path separators. The single combined fix wave `acd5ea3` uses portable fixtures and native path formatting while retaining plain-text safety assertions. Scoped re-review approved with no new findings. Fresh final `./gradlew check --rerun-tasks` ran all eight tasks in 10 seconds: 394 total, 393 passed, one known skip, zero failures/errors. Source hygiene and diff checks passed. Production code remains `9c0ae64`; the final fix changed tests only. Native and Windows execution remain unverified. This plan’s scratch workspace is removed after preserving these decisions and evidence; the user subsequently approved local integration, completed on 2026-09-12.

Merged-result verification on `05a94d4`: `./gradlew check --rerun-tasks` passed in 11 seconds, all eight tasks executed; XML confirms 394 tests, 393 passed and one known skip, zero failures/errors. The completed feature worktree is no longer the development entry point; use the main checkout.

## Plan 4b recorded decisions

- Ruling: Treat the user's go-ahead as approval of the full Plan4b scope just presented, continuing the established spec-driven workflow without repeating approval — explicit scope and live/new-pane semantics were accepted — costs rework if detailed choices differ from intent.
- Ruling: Extend the existing terminal options API with backward-compatible constructors and applyOptions rather than replacing views — preserves sessions and existing callers — costs maintaining compatibility overloads.
- Ruling: Keep app font16 and use the parent spec's150x45 new-window grid; bound columns5–500, lines2–200, lineHeight1–3 and scrollback0–1000000 — preserves approved font while bounding new values and avoiding compressed glyph overlap — costs limiting unusual extreme configurations and changing initial window dimensions.
- Ruling: Capture session settings at request time and window grid once per new window; append configured args to the resolved default shell command when program is empty — prevents queued launches from changing under reload and preserves login-shell defaults — costs requiring an explicit executable to replace default login arguments.
- Ruling: Coalesce attached-view bells; visual defaults to a150ms15%-foreground flash, sound is explicit, and stale attachment callbacks are rejected — implements the spec bell choice without late sound/flashes or unbounded queued events — costs tuning visual strength/duration after native use and ignoring bells while detached.
- Ruling: Anchor array validation diagnostics to the containing key’s exact position instead of TomlJ’s misleading element start — TomlJ includes preceding whitespace/comments in array-element locations, and existing diagnostics identify fields — costs locating the invalid element within the named list rather than pointing directly to its token.

## Plan 4c execution rulings

Ruling: Exclude the detector dependency’s transitive net.java.dev.jna group while retaining existing pty4j-provided JNA and JNA-platform 5.14.0 — detector metadata requested an unpublished jpms variant during Gradle test resolution; this avoids replacing the established runtime — costs revisiting the exclusion if native dependency requirements change. Implementer must include runtime dependency evidence.

Ruling: Keep the latest OS appearance reading separately from the committed effective theme and use it when retrying selection/configuration — the plan’s transactional pseudocode otherwise forgets an OS change when LAF installation fails — costs one extra state field, but preserves visual rollback and permits Follow System retry without another OS event. Task 4 implements and tests it.

Ruling: Explicit Reload publishes one accepted state through the existing revision/close guard even when its value is unchanged, while polling and Settings still suppress unchanged values — equal-state suppression prevented the promised failed-theme retry — costs one deliberate UI reapplication per user reload and avoids a second unguarded completion path.


## Packaging verification notes

Task 1 was independently approved with no required fixes. Task 2 added RED/GREEN environment regressions; full `./gradlew check` passed with 508 tests, 507 passed and one known font skip. Final forced `./gradlew check :jasper-app:packageDist --rerun-tasks` passed all 13 tasks in 44 seconds: 508 tests, 507 passed, one known font skip, zero failures/errors; package and DMG checks passed. Whole-branch review fixes in `de4ec8c` were approved by scoped re-review with no open findings. [Verification, artifact checksum and execution decisions](superpowers/plans/2026-09-12-jasper-plan-4d-verification.md). One intermediate forced run observed a concurrent RIS snapshot NPE in `TerminalLiveOptionsTest` in unchanged terminal source/tests; targeted and subsequent full runs passed. The readiness branch reproduced and fixed this reset-lock race with a deterministic concurrent-reset regression, distinct from the known font skip. No claim of Windows or desktop runtime acceptance is made.

## Authorized publication and Windows CI test portability

The readiness branch was pushed after explicit user approval. Initial CI passed macOS/Linux but failed three Windows app tests. Test-only `a5f4dc1` uses the platform line separator for the exact logging fallback assertion and adds method-level Windows exclusions for two existing `/bin/sh` fixtures. Production code, package and measured runtime remain unchanged. Focused 15/15 and local full 575 passed/one known skip; `c793925` additionally fixes an off-EDT test resize race without weakening its grid/connector assertions; focused8/full575pass1skip and the confirming three-platform run passed. Details and the controller-review substitution caused by the agent service's thread limit are recorded in the [readiness handoff](terminal-readiness.md#publication-and-ci-follow-up).

App refactor execution ruling: coordinator shutdown preserves pane-owned session close; late arrivals close immediately. The controlled lifecycle tests use a child JVM through supported APIs because app tests cannot access terminal fake connectors.

### Retro toolbar Settings and Exit — 2026-09-23

Added retro-only Settings and Exit buttons after the toolbar's flexible space. Plugin
controls stay to their left. Both use the existing application actions, preserving
configuration availability and normal quit handling. They retain the compact 28-pixel,
flat, labeled presentation and existing icon-only/hidden modes. Exit uses original
16/24-pixel OldGNOME2 `actions/exit.png` artwork (updated per user feedback) with
source hashes in the GPL2+ asset manifest.
Modern toolbar contents are unchanged.

Targeted toolbar, action, contribution and icon tests passed, including red/green checks
for the new placement and callbacks; installDist rebuilt successfully. Inspected the
headless 2x toolbar preview. No native GUI launch, merge or push.

The full application check and architecture gate also passed: 815 tests, 0 failures, 0 errors, 1 skips. Source hygiene and diff checks passed.

User-selected Exit artwork follow-up: copied the collection’s exact `actions/exit.png`
variants, removed the shutdown image and special scaling path. Icon/chrome tests and
installDist passed; inspected the updated headless toolbar preview.

### Flat retro toolbar background — 2026-09-23

The retro toolbar now uses an explicit solid Metal light-gray background, suppressing
Ocean's toolbar gradient while retaining the Metal delegate, border and layout.
Targeted retro chrome/contribution tests and installDist passed; inspected the headless
2x toolbar preview. No native GUI launch, merge or push.

The retro menu bar now uses the same solid background as the toolbar, per the next
screenshot refinement. Metal menu behavior and selection painting are retained.
Targeted chrome/contribution tests and installDist passed; inspected a headless 2x
combined menu/toolbar preview. No native GUI launch, merge or push.

The retro menu bar now has an explicit one-pixel soft-gray bottom separator. This
remains visible next to a toolbar, where Metal suppresses its own default menu border.
Targeted chrome/contribution tests and installDist passed; visually checked a headless
root-pane render with the menu bar directly above the toolbar.

### Regular system typography in retro mode — 2026-09-23

Retro UI defaults now use Helvetica Neue Regular when available, followed by Segoe UI,
Noto Sans, DejaVu Sans and logical SansSerif. Existing Metal font sizes are retained;
toolbar captions keep their one-point reduction. Menus, tab titles, standard buttons
and plugin forms inherit regular-weight defaults. Terminal font configuration remains
independent, and macOS still controls the native window-title font. No font is bundled.

Verified that this Mac resolves the UI defaults to Helvetica Neue Regular, and inspected
headless menu/toolbar and workspace/form renders. Full application check, architecture
verification and installDist passed (815 tests: 814 passed, one expected skip). No GUI
launch, merge or push.

### Retro font portability — 2026-09-23

Font preferences now follow the host: Helvetica Neue/Helvetica on macOS, Segoe UI/Tahoma
on Windows, Noto Sans/Liberation Sans on Linux. Noto Sans and DejaVu Sans provide
additional installed-family fallbacks; Java SansSerif is the final fallback everywhere.
Font discovery requests locale-neutral family names. No platform font files are copied
or required, and all UI weights remain regular.

Added checks for each OS preference when other platform fonts are also installed,
missing preferred families, empty font inventories, and restoration of modern fonts.
The targeted theme/chrome suite, architecture check and installDist passed locally on
macOS. Existing CI runs check on macOS, Ubuntu and Windows; it has not been dispatched
for this local branch, so native Linux/Windows rendering is not claimed as verified.

### Custom retro macOS title bar — 2026-09-23

Implemented the user-approved compact title treatment as a bounded extension of the
existing MacTitleBar. Supported macOS/JBR windows keep OS traffic lights and native
window gestures, with a 32-logical-pixel flat gray title strip, regular retro font,
centered title and subtle divider. Title color is slightly darker than menu/toolbar.
The menu sits below the native control region; Metal tabs stay below the toolbar with
one or many sessions. Terminal and auxiliary frames/dialogs share the treatment.
Windows, Linux and runtimes without the required custom-decoration support retain
native title bars. Updated the original retro design with the approved amendment.

A new layout/metadata/menu-action regression failed before enabling the custom retro
header and passed afterward. Full application check, architecture gates and installDist
passed: 824 tests, 823 passed, one expected skip, zero failures/errors. Inspected
headless 1x/2x previews with one and two tabs; source hygiene and diff checks passed.
No native GUI launched: traffic-light hover/actions, dragging, double-click and
fullscreen on macOS remain user-run acceptance. No merge or push.

Independent review found no actionable correctness issues in runtime fallback, native
integration, menu placement/keyboard handling, tab ownership, auxiliary windows or disposal.

### Main integration and Remote semantic icons — 2026-09-23

Fetched origin; local main (6aac2ff9) was 27 commits ahead and held the requested Remote
SSH merge. Merged it into codex/retro-metal in 55ededf, retaining retro title/menu/toolbar
styling, portable fonts and semantic SDK APIs alongside the new SSH UI and [ui.font]
configuration. Retro now honors explicit UI font reloads while retaining its own defaults.
Resolved a stale status-render assertion to match main's title-colored status background.

Independent review caught a failed retro font reload reapplying size offsets and, after
a LAF replacement, losing app aliases. The fix restores exact captured Metal defaults.
Both before/after-replacement regressions failed before the fix and passed after it;
scoped re-review found no remaining issues.

Remote now requests IconName.NETWORK once and shares the host-selected artwork across
its Sessions dropdown, dynamic session/manage/connect/hosts actions, hosts panel and
SSH status indicator. Removed the plugin-owned server SVG. Main already uses SDK 0.7.2
for panel toggling and 0.7.3 for overlays, so the combined icon API is now SDK 0.7.4;
Remote, Vault and Sample declare that minimum, preventing old hosts from loading them
without the icon API. Existing toggle/overlay version annotations remain intact.

Fake-host checks cover both icon families. Real staged-plugin tests cover Remote without
Vault and verify all icon placements plus separate 28px retro toolbar sizing; fixture
copying avoids Windows symlink privileges. Inspected a headless retro Sessions toolbar
preview. Full check and installDist passed: 1,711 tests, 1,708 passed, three expected skips,
zero failures/errors. Installed Remote jar has the >=0.7.4 manifest and no old server.svg.
Independent review and rollback re-review are complete. No GUI launch or push.
