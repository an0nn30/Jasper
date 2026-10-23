# Floating tool windows 1: Float and Window view modes Implementation Plan

**Status: executed on 2026-10-23** (`6cef314c3..2a5f0d62e`, the spec's amendments `df1625825` and `acaac9e7c`, then the
documentation commit); see `docs/STATUS.md`. Deviations from the plan's text: (1) a stripe click toggles a detached tool
window as it does a docked one (shown hides, hidden shows and raises), where Task 6 had a click raise a shown window that
is not the active one (`af1b90e6e`, the spec amended); only the View menu and `<id>.toggle` raise it; (2) Move to on a detached
tool window's own slot docks it there too (Task 6); (3) Task 8's `DetachedFrame` has a public static factory, `shells`,
and `Shell` a sixth component, `content`, with `ToolWindowArea.onRemounted`, so that a kit view's remounted frame reaches
an open window; (4) a detached window's `show` never takes the focus, and View Mode > Float or Window from a menu
activates the window (`d1d8db2f3`, `2a5f0d62e`, the spec amended); (5) Task 3 draws `more.vertical` in the strip ink
(`IconRegistry`); (6) Task 9 also edits `docs/configuration.md` and `docs/plugin-authoring.md`.

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** A tool window can leave the main window's dock for a window of its own, in IntelliJ's Float or Window view mode, from its ⋮ Options menu (View Mode ▸ Docked / Float / Window), and come back; mode, bounds and visibility persist.

**Architecture:** The pure dock model (`DockState`) gains a mode and remembered bounds per tool window; a detached tool window keeps its slot (its stripe button) but is never its slot's active window. `ToolWindowArea` mounts a detached tool window's frame (header + content) outside the area; a new headless `DetachedToolWindows` keeps one native window per shown detached tool window through a shell record, as `WindowShell` does for workspace windows; the native `DetachedFrame` (a `JDialog` for Float, a `JFrame` for Window) is built only off-headless. `WindowContributions` stays the one place a dock change takes effect.

**Tech Stack:** Java 25 (JBR), Swing, FlatLaf, JUnit 5, AssertJ; headless tests.

**Spec:** `docs/superpowers/specs/2026-10-23-jasper-floating-tool-windows-design.md`

## Global Constraints

- `dev.jasper.app.dock` stays JDK-only: no Swing; from `java.awt` only `Rectangle` and `Point` (`verifyApplicationArchitecture`).
- Never launch the GUI; tests are headless (`java.awt.headless=true`). Native frames and dialogs are never built headless.
- Every effective `DockState` mutation fires exactly one `Change`; a no-op fires nothing.
- Menu labels exactly: `View Mode`, `Docked`, `Float`, `Window`, `Move to`, `Hide`.
- The Options button glyph is `more.vertical` (⋮) in every header.
- No animations. No glass in detached windows (opaque).
- New classes get rows in `docs/design/theme-engine/migration.csv` (`ThemeMigrationCoverageTest`).
- Source hygiene per AGENTS.md (no raw control/private-use characters).
- Commit messages end with the two attribution lines:
  `🤖 Generated with Claude Code at The Home Depot` and `Co-Authored-By: Claude <noreply@anthropic.com>`.

## Review Focus

1. A detached tool window saved on a display that is no longer connected reopens on a connected one
   (`WindowPlacement.restore`), not off-screen — Task 5's restore test with a two-display then one-display list.
2. A plugin stopped while its tool window is detached and shown: the window closes, nothing throws, and the saved mode and
   bounds survive for its next start — Task 5.
3. The main window closing with detached windows shown: they close, are saved as shown, and reopen with the main window —
   Task 5.
4. Closing a detached window (OS close, Hide, Close Pane/Tab/Window keys) never pulls focus or the main window forward —
   Task 6's focus test.
5. A Float window requested before the main window shows (restore at start-up) waits and opens once it shows, owned by
   it — Task 5's pending test.

---

### Task 1: `ToolWindowMode` and detached tool windows in `DockState`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/ToolWindowMode.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/DockState.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/dock/DockStateDetachTest.java` (new); existing `DockStateTest`, `DockDropModelTest` keep passing unchanged (the 4-argument `Change` constructor stays).

**Interfaces:**
- Produces:
  - `public enum ToolWindowMode { DOCKED, FLOAT, WINDOW; public boolean detached() }` (`detached()` is `this != DOCKED`).
  - `DockState.Change` becomes `record Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown, List<String> placed)`
    with `public Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown)` (placed empty).
    `placed`: ids whose slot or mode changed, in order.
  - `ToolWindowMode mode(String id)` (DOCKED for a docked or unknown id);
    `Optional<Rectangle> bounds(String id)` (a copy); `List<String> detachedVisible()` (shown detached ids, slots in
    `Slot.values()` order, each slot's stripe order);
    `void setMode(String id, ToolWindowMode mode)`; `void detach(String id, ToolWindowMode mode, Rectangle bounds)`;
    `void setBounds(String id, Rectangle bounds)`.

- [ ] **Step 1: Write the failing tests** in `DockStateDetachTest` (one `DockState` with `a`, `b` in `LEFT_TOP`, `c` in
  `RIGHT_TOP`, a recording listener). Each asserts the exact `Change` list:
  - `floatingAShowingWindowKeepsItShownOutsideItsSlot`: `show("a")`, clear; `setMode("a", FLOAT)` →
    `[Change(true, true, [], [], ["a"])]`; `visible("a")` true; `active(LEFT_TOP)` empty; `slot("a")` LEFT_TOP;
    `detachedVisible()` `["a"]`; `mode("a")` FLOAT.
  - `floatingAHiddenWindowChangesOnlyItsMode`: `setMode("a", WINDOW)` → `[Change(false, true, [], [], ["a"])]`; not visible.
  - `dockingAShownDetachedWindowShowsItInItsSlotAndHidesTheOther`: `show("b")`; `setMode("a", FLOAT)`; `show("a")`; clear;
    `setMode("a", DOCKED)` → `[Change(true, true, ["b"], [], ["a"])]`; `active(LEFT_TOP)` `a`.
  - `floatToWindowIsAPlacementChange`: shown FLOAT → `setMode WINDOW` → `[Change(true, true, [], [], ["a"])]`.
  - `theSameModeIsSilent`: `setMode("a", DOCKED)` on docked `a` → no change; unknown id → no change.
  - `showAndHideOfADetachedWindowLeaveTheSlotAlone`: `show("b")`; `setMode("a", FLOAT)`; clear; `show("a")` →
    `[Change(true, true, [], ["a"], [])]`, `active(LEFT_TOP)` still `b`; `hide("a")` → `[Change(true, true, ["a"], [], [])]`;
    `toggle("a")` shows it again.
  - `detachShowsAtTheBoundsInOneChange`: `show("a")`; clear; `detach("a", FLOAT, new Rectangle(10, 20, 300, 200))` →
    `[Change(true, true, [], [], ["a"])]`, `bounds("a")` that rectangle; `detach` of hidden `b` →
    `[Change(true, true, [], ["b"], ["b"])]`; detaching shown `a` again at new bounds (same mode) →
    `[Change(true, true, [], [], [])]`; the same call again → silent; `detach("a", DOCKED, …)` throws
    `IllegalArgumentException`.
  - `setBoundsIsSilentAndKeptAfterDocking`: `setBounds("a", r)` fires nothing; after `setMode("a", FLOAT)` then
    `setMode("a", DOCKED)`, `bounds("a")` still `r`; `setBounds` with a non-positive size throws
    `IllegalArgumentException`; an unknown id is ignored.
  - `aStripeMoveKeepsADetachedWindowDetached`: `setMode("a", FLOAT)`; `show("a")`; clear; `move("a", RIGHT_TOP, 0)` →
    `[Change(false, true, [], [], ["a"])]`; still FLOAT and visible; `active(RIGHT_TOP)` unchanged.
  - `aWorkspaceDropDocksADetachedWindow`: `show("c")`; `setMode("a", FLOAT)`; `show("a")`; clear;
    `moveAndShow("a", RIGHT_TOP, 0)` → `[Change(true, true, ["c"], [], ["a"])]`; mode DOCKED; `active(RIGHT_TOP)` `a`.
    Hidden detached `a` → `moveAndShow` reports `shown ["a"]`.
  - `dockedMovesBetweenSlotsArePlacements`: `move("a", RIGHT_TOP, 0)` (hidden) → `[Change(false, true, [], [], ["a"])]`;
    a reorder within `LEFT_TOP` → `placed` empty.
  - `removeForgetsModeAndBounds`: detached shown `a`, `remove("a")` → `[Change(true, true, ["a"], [], [])]`;
    `mode("a")` DOCKED, `bounds("a")` empty, `detachedVisible()` empty.
  - `copyCarriesModesBoundsAndShownDetached`.
  - `showingASideCountsDockedWindowsOnly`: only a detached shown window on LEFT → `showing(LEFT)` false.

- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*DockStateDetachTest'` — expect compilation failure.

- [ ] **Step 3: Implement.** In `DockState` keep `EnumMap<Slot,String> active` for docked windows only; add
  `Map<String, ToolWindowMode> modes` (detached only), `Map<String, Rectangle> bounds`, `LinkedHashSet<String> shownDetached`.
  `visible(id)` = `active.containsValue(id) || shownDetached.contains(id)`. Existing `move` and `moveAndShow` add `id` to
  `placed` when the slot changes; `moveAndShow` of a detached id first clears its mode and `shownDetached` entry
  (it docks). Javadoc each new method with the semantics in Step 1; update the class Javadoc ("one window's tool window
  layout … a tool window is docked in a slot or detached into a window of its own, keeping its slot for its stripe
  button").

- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*'` — all pass.

- [ ] **Step 5: Commit** — `Dock: a tool window can be detached (Float or Window), keeping its slot`.

---

### Task 2: Saving the mode and bounds (`UiState`)

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java`

**Interfaces:**
- Consumes: `ToolWindowMode` (Task 1).
- Produces: `public record Panel(Slot slot, int order, boolean visible, ToolWindowMode mode, Optional<Bounds> window)`
  with `public Panel(Slot slot, int order, boolean visible)` (DOCKED, empty). TOML per panel table: `mode = "float"` or
  `"window"` (written only when detached), `window = { x = …, y = …, width = …, height = … }` (an inline table, written
  when present, whatever the mode).

- [ ] **Step 1: Write the failing tests:**
  - `aDetachedPanelsModeAndWindowRoundTrip`: put `Panel(LEFT_TOP, 0, true, FLOAT, Optional.of(new Bounds(-1200, 40, 420, 610)))`,
    save, load: equal; the file text contains `mode = "float"` and `window = { x = -1200, y = 40, width = 420, height = 610 }`.
  - `aDockedPanelKeepsItsWindowBounds`: DOCKED with bounds round-trips; the text has no `mode` key.
  - `anUnknownModeReadsAsDocked`, `aBadWindowTableIsDropped` (non-positive size, missing key, wrong type): the panel
    still loads with its slot, order and visibility.
  - `anotherLooksPanelsKeepTheirModes`: the `[look."<key>".panels."<id>"]` form round-trips mode and window.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*UiStateTest'` — failures.
- [ ] **Step 3: Implement** reading in `readDock` (reuse the existing `bounds(TomlTable)` helper for the `window`
  table) and writing in `appendDock`; Javadoc the two new components.
- [ ] **Step 4: Run** the test class — pass.
- [ ] **Step 5: Commit** — `UiState: a panel's view mode and its own window's bounds`.

---

### Task 3: View Mode in the ⋮ Options menu, and the ⋮ glyph

**Files:**
- Modify: `workspace/ToolWindowMenu.java`, `workspace/ToolWindowHeader.java`, `workspace/ToolWindowArea.java`
  (`header`, `mountHeader`), `workspace/DockStripe.java`, `workspace/ToolStripe.java` (`Button`, `toggle`),
  `workspace/LabelledStripe.java`, `workspace/BottomStrip.java`, `workspace/WindowContributions.java` (`renderStripes`
  passes the mode; wire `onMode` to a no-op `setMode` placeholder that Task 5 fills — a `dock.setMode` call is enough
  here) — all under `jasper-app/src/main/java/dev/jasper/app/`.
- Possibly: `jasper-ui/src/main/java/dev/jasper/ui/icons/IconRegistry.java` (only if the kit cannot draw
  `more.vertical`; then add `Map.entry("more.vertical", "M 8 3 V 3.1 M 8 8 V 8.1 M 8 13 V 13.1")` beside `more`, and
  to `DOT_PANEL_BUILT_INS` if `more` needs it there).
- Test: `ToolWindowHeaderTest`, `ToolStripeTest`, `LabelledStripeTest`, `BottomStripTest`, `ToolWindowKitPanelTest`
  (all `jasper-app/src/test/java/dev/jasper/app/workspace/`).

**Interfaces:**
- Produces:
  - `static JPopupMenu ToolWindowMenu.create(Slot current, ToolWindowMode mode, boolean visible, Consumer<Slot> move,
    Consumer<ToolWindowMode> setMode, Runnable hide)` — items in order: `View Mode` ▸ (`Docked`, `Float`, `Window`,
    `JCheckBoxMenuItem`s in one `ButtonGroup`, the current checked and inert), `Move to` ▸ (unchanged), `Hide`.
  - `ToolStripe.Button` gains a last component `ToolWindowMode mode`; every existing constructor defaults it to DOCKED.
  - `DockStripe.onMode` and `ToolWindowArea.onMode`: `BiConsumer<String, ToolWindowMode>`, default no-op.

- [ ] **Step 1: Write the failing tests:**
  - `ToolWindowHeaderTest.theMenuOffersViewModeWithTheCurrentModeChecked`: the menu's first item is `View Mode` with
    `Docked`, `Float`, `Window`; `Docked` selected; clicking `Float` calls `setMode` with FLOAT once; clicking `Docked`
    calls nothing. Keep `theMenuMovesToAnyOfTheSixSlotsAndHides` (update the call).
  - `ToolWindowHeaderTest.theOptionsButtonDrawsTheVerticalEllipsis`: the Options button's icon is
    `AppIcons.semantic("more.vertical")` (standard and flush headers).
  - `ToolWindowKitPanelTest.theBorrowedHeadersOptionsActionIsTheVerticalEllipsis`: `host.options`' icon name is
    `more.vertical`; its menu has `View Mode`.
  - In each stripe test: a right-click menu on a FLOAT button has `Float` checked; choosing `Docked` calls `onMode` with
    the id and DOCKED.
- [ ] **Step 2: Run** the five test classes — failures.
- [ ] **Step 3: Implement.** Update all five `ToolWindowMenu.create` call sites (`ToolStripe:147`, `LabelledStripe:95`,
  `BottomStrip:86`, `ToolWindowArea.mountHeader`, `ToolWindowArea.header`). Fix `ToolWindowHeader`'s glyph comment.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`. If a pixel or mock test
  (`DockMockTest`, `DefaultDockPixelsTest`, `EnterpriseDockWindowTest`, `FlushFramesTest`) pins the horizontal glyph,
  update its expectation to the vertical one and note it in the commit message (spec: ⋮ everywhere).
- [ ] **Step 5: Commit** — `Tool windows: View Mode in the Options menu, and the vertical ellipsis`.

---

### Task 4: `ToolWindowArea` mounts detached frames outside the area

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java`
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowAreaDetachedTest.java` (new)

**Interfaces:**
- Consumes: `DockState.detachedVisible()`, `mode(id)` (Task 1); `ToolWindowMenu.create` (Task 3).
- Produces (package-private):
  - `JComponent detachedFrame(String id)` — the frame (a `Host`: header above content) for a shown detached tool window
    with content, else null. `List<String> detachedFrames()` — ids with a frame, in `detachedVisible()` order.
  - `Optional<Rectangle> frameOnScreen(String id)` — a shown docked tool window's frame bounds on screen
    (`SwingUtilities.convertPointToScreen`), empty otherwise.
  - `Dimension landingSize(String id)` — the size `DockGeometry.landing(new Rectangle(getSize()), state, id, slot,
    metrics())` gives in its slot, else `(state.size(LEFT|RIGHT), getHeight() / 2)` for a side slot and
    `(getWidth() / 2, state.size(BOTTOM))` for a bottom slot.
  - `void activate(String id)` now also activates a detached frame: focus moves to its `focusTarget` (any frame style;
    a flush frame also shows its header focused).

- [ ] **Step 1: Write the failing tests** (`DockTestSupport.show` style; a `KitView` panel and a plain component panel):
  - `aDetachedWindowsFrameHoldsItsHeaderAndContentOutsideTheArea`: `setContent("a", "Alpha", label)`; `state.show("a")`;
    `state.setMode("a", FLOAT)`; `sync()`: `content(LEFT_TOP)` null; `detachedFrame("a")` non-null, parentless, its
    NORTH a `ToolWindowHeader` titled `Alpha`, its CENTER the label; gutters 0; `detachedFrames()` `["a"]`.
  - `hidingOrDockingReleasesTheDetachedFrame`: after `hide("a")`, `detachedFrame("a")` null; after `show` +
    `setMode(DOCKED)` the label is back in `content(LEFT_TOP)`.
  - `aDetachedHeaderIsNoDragSource`: `dragSource` hears nothing for a detached frame's header.
  - `aKitPanelLendsItsHeaderToTheDetachedFrame`: its `host.options` and `host.hide` actions are present; hide calls
    `onHide`; releasing (hide) clears them.
  - `theDetachedHeadersMenuOffersDocked`: the header's menu has `Float` checked; `Docked` calls `onMode(id, DOCKED)`.
  - `frameOnScreenAndLandingSize`: a shown docked frame reports its bounds on a stand-in frame's pretend screen
    (`TabDragTestSupport.frame`); a hidden one is empty; `landingSize` equals the slot's landing size.
  - `underFlushFramesTheFocusedDetachedFrameShowsFocus`: `focusMoved(component inside the detached frame)` marks it.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*ToolWindowAreaDetachedTest'` — failures.
- [ ] **Step 3: Implement.** Extract the frame-building part of `sync()` into
  `private Host mount(String id, Content wanted, boolean dragSources)`; `sync()` reconciles `detached`
  (`LinkedHashMap<String, Host>`) after the slots with the same stale-first release rule. Detached hosts:
  `gutters(0,0,0,0)`, `rounded(false,false)`, never `add(...)`ed to the area, no drag sources.
  `removeContent` and `close` release them too. `focusMoved` marks detached hosts by id.
- [ ] **Step 4: Run** the workspace tests — pass.
- [ ] **Step 5: Commit** — `Tool windows: the area mounts a detached tool window's frame outside it`.

---

### Task 5: Detached windows follow the dock (`DetachedToolWindows`, `WindowContributions`)

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/DetachedToolWindows.java`
- Modify: `workspace/WindowContributions.java`, `workspace/WindowContent.java` (forwarders below)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/DetachedToolWindowsTest.java` (new; a real
  `WindowContent` via `DesktopTestSupport.content(...)` + `connectContributions(new Contributions(), state)`, panels via
  `model.addPanel`, recording shells)

**Interfaces:**
- Consumes: Tasks 1–4.
- Produces:
  ```java
  final class DetachedToolWindows implements AutoCloseable {
      /** What a detached tool window's native window opens with; {@code owner} is the main window's (Float's owner). */
      record Opening(String id, String title, ToolWindowMode mode, Rectangle bounds, JComponent frame,
                     java.awt.Window owner, Function<JRootPane, AutoCloseable> keys) {}
      /** What the native window reports: the user closed it, or it moved or resized (screen bounds). */
      record Events(Runnable closing, Consumer<Rectangle> moved) {}
      /** The native window's functions. */
      record Shell(Runnable show, Runnable toFront, Runnable dispose, BooleanSupplier active, Consumer<Rectangle> place) {}
      BiFunction<Opening, Events, Shell> shells;       // null: windows wait (headless default)
      BooleanSupplier ready;                            // default: the owner content is showing
      Supplier<List<Rectangle>> displays;              // usable display bounds; default from GraphicsEnvironment (empty headless)
      void sync();                                      // open, move, reopen (mode change) or dispose to match the area
      boolean showing(String id); boolean active(String id); void activate(String id); // toFront + area.activate
      @Override public void close();                    // disposes every window
  }
  ```
  `WindowContent`: `void detachedShells(BiFunction<DetachedToolWindows.Opening, DetachedToolWindows.Events, DetachedToolWindows.Shell> shells)`
  (forwards to its contributions; syncs); `boolean panelDetached(String panelId)`.

- [ ] **Step 1: Write the failing tests** (`DetachedToolWindowsTest`; shells record every call as text, e.g.
  `"open a FLOAT 10,20 300x200"`, `"place a …"`, `"dispose a"`; `ready` true; displays one 0,0 1920×1080):
  - `viewModeFloatOpensItsWindowWhereTheFrameWas`: show panel `a` docked on a stand-in frame; the header menu's
    `Float`: one `open a FLOAT` at `frameOnScreen("a")`'s bounds (fitted); the slot shows nothing; `a` visible.
  - `viewModeWindowReopensItAsAWindow`: FLOAT → `Window`: `dispose a`, then `open a WINDOW` at the same bounds.
  - `viewModeDockedPutsItBack`: `dispose a`; `area().content(LEFT_TOP)` is `a`'s component.
  - `aHiddenToolWindowFloatsHiddenAndOpensCentredOnTheMainWindowWhenShown`: no window until shown; then centred on the
    stand-in frame, size `landingSize("a")`, at least 240×160 scaled.
  - `closingItsWindowHidesItAndKeepsItDetached`: `Events.closing` → `dispose a`, `a` hidden, mode FLOAT, the stripe
    button unselected; showing again opens at the last bounds `Events.moved` reported.
  - `moveToOnADetachedToolWindowDocksItThere`: Move to ▸ Right Top → `dispose a`, docked shown in RIGHT_TOP.
  - `modeAndBoundsAreSavedAndRestored`: after detaching, `UiState.panel("a")` has FLOAT, visible, the bounds; a second
    `WindowContent` connected to the same state with `ready` false opens nothing; when `ready` turns true and
    `sync()` runs, `open a FLOAT` at the saved bounds.
  - `restoredBoundsOnAGoneDisplayLandOnAConnectedOne`: saved at x=-1500 with displays `[0,0,1920,1080]` → opened inside it.
  - `aRemovedPanelsWindowCloses`: `entry.close()` → `dispose a`, no exception; the saved panel keeps FLOAT and bounds.
  - `closingTheMainWindowClosesDetachedWindowsAndSavesThemShown`: `content.close()` → `dispose a`; saved visible true.
  - `withoutShellsNothingOpensUntilTheyAreGiven`: `shells` null → nothing; `detachedShells(recorder)` → opens.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*DetachedToolWindowsTest'` — failures.
- [ ] **Step 3: Implement.**
  - `WindowContributions` owns one `DetachedToolWindows` (main window only). Its `setMode(id, mode)`: a detached mode with
    no remembered bounds first sets `initialBounds(id)` — size: `area.frameOnScreen(id)`'s, else `area.landingSize(id)`,
    at least `UIScale.scale(240)` × `UIScale.scale(160)`; location: the docked frame's, else centred on the main window's
    frame on screen (`SwingUtilities.getRoot(owner)`'s bounds, converted for a stand-in); then
    `WindowPlacement.fitted(bounds, display)` with the display from `WindowPlacement.displayFor`. Then `dock.setMode`.
  - `movePanel` of a detached id → `dock.moveAndShow(id, slot, dock.ids(slot).size())`.
  - `docked(change)`: after `area.sync()`, `detached.sync()`; ids in `change.hidden()` that are detached do not trigger
    `owner.focusSelected()`.
  - `persist()` writes `Panel(slot, order, visible, dock.mode(id), dock.bounds(id).map(r -> new Bounds(...)))`; absent
    panels keep their saved record whole. `syncPanels()` restores bounds then mode before `show`.
  - `Opening.keys` is `root -> owner.bindDetachedRoot(root, () -> hidePanel(id))`, which Task 7 adds; until then pass
    `root -> () -> { }` and leave a `// Task 7` marker removed by Task 7.
  - `Events.closing` → `hidePanel(id)`; `Events.moved` → `dock.setBounds(id, r)`.
  - Restoring: `WindowContributions` listens for the owner content's `HierarchyEvent.SHOWING_CHANGED` and syncs.
  - `close()` closes `detached` before notifying sites.
- [ ] **Step 4: Run** `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.persistence.*'` — pass.
- [ ] **Step 5: Commit** — `Tool windows: Float and Window open their own windows, follow the dock and persist`.

---

### Task 6: Toggles, child-window requests and Zen for detached tool windows

**Files:**
- Modify: `workspace/WindowContributions.java` (`activatingToggle`, `changeAndActivate`, `zenChanged`),
  `workspace/WindowManager.java` (`routePanel`)
- Test: `DetachedToolWindowsTest` (more cases), `WindowManagerTest`

**Interfaces:**
- Consumes: `DetachedToolWindows.active/activate`, `WindowContent.panelDetached` (Task 5).

- [ ] **Step 1: Write the failing tests:**
  - `aStripeClickShowsRaisesThenHides`: hidden detached `a`: click → `open a`, `activate` (shell `toFront`, focus in
    its frame); shown, shell `active` false: click → `toFront` only, still shown; shown and active: click → hidden.
  - `theViewMenusToggleFollowsTheSameRule` (`ACTIVATING_TOGGLE` request).
  - `hidingADetachedWindowLeavesFocusAlone`: the main window's `focusSelected` is not called (count via a test hook) when
    a detached id hides; a docked hide still calls it.
  - `WindowManagerTest.aChildWindowsRequestForADetachedPanelRaisesItsWindowNotTheMainWindow`: `main.toFront()` not called;
    the request reaches the main window as `ACTIVATING_SHOW`.
  - `zenHidesDetachedWindowsAndBringsThemBack`: Zen on → `dispose a`, saved visible stays true; Zen off → `open a`.
- [ ] **Step 2: Run** them — failures.
- [ ] **Step 3: Implement.** `activatingToggle`/`toggleAndActivate` for a detached id: hidden → show then
  `detached.activate(id)`; shown and not `detached.active(id)` → `detached.activate(id)`; else hide. `routePanel` skips
  `main.toFront()` when `main.content().panelDetached(panelId)`. Zen collects `dock.detachedVisible()` with the slots'
  active windows and restores both.
- [ ] **Step 4: Run** the workspace tests — pass.
- [ ] **Step 5: Commit** — `Tool windows: a detached window's stripe button raises it; Zen and child windows include it`.

---

### Task 7: Shortcuts in a detached window

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`, `workspace/WindowContributions.java`
  (remove the Task 5 `keys` marker)
- Test: `jasper-app/src/test/java/dev/jasper/app/workspace/DetachedShortcutsTest.java` (new)

**Interfaces:**
- Produces: `AutoCloseable WindowContent.bindDetachedRoot(JRootPane root, Runnable hide)` — binds every keymap stroke on
  `root`'s `WHEN_IN_FOCUSED_WINDOW` map: `close_pane`, `close_tab` and `close_window` strokes run `hide`; every other
  stroke takes the key when `claimsShortcut(stroke, focusOwner)` would and runs `dispatchShortcut(stroke, focusOwner)`,
  first raising the main window (`bringToFront`) unless the stroke resolves to a `PANEL`-context action whose panel holds
  the focus owner. Closing the returned handle removes exactly those bindings. Updated on rebind like the main root.

- [ ] **Step 1: Write the failing tests** (a `WindowContent` with a panel `a` detached; its frame in a bare `JRootPane`;
  run bindings through `root.getActionMap().get(root.getInputMap(WHEN_IN_FOCUSED_WINDOW).get(stroke))`):
  - `closeKeysHideTheToolWindow`: the strokes of Close Pane, Close Tab and Close Window each run `hide`; the main
    window's tab count is unchanged.
  - `aGlobalShortcutRaisesTheMainWindowAndRuns`: New Tab's stroke → `bringToFront` called once; a tab opened.
  - `thePanelsOwnShortcutRunsInPlace`: a contributed action with `KeyContext` PANEL for `a`, focus owner inside the
    frame → runs; `bringToFront` not called.
  - `closingTheBindingRemovesIt`.
- [ ] **Step 2: Run** — failures. **Step 3: Implement.** **Step 4: Run** the workspace tests — pass.
- [ ] **Step 5: Commit** — `Tool windows: Jasper's shortcuts work in a detached window`.

---

### Task 8: The native detached window (`DetachedFrame`)

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/DetachedFrame.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/application/JasperApplication.java` (`composeContent`)
- Test: `jasper-app/src/test/java/dev/jasper/app/application/JasperApplicationWindowsTest.java` (the main window's
  content gets shells only off-headless: assert headless leaves `shells` null)

**Interfaces:**
- Consumes: `DetachedToolWindows.Opening/Events/Shell` (Task 5).
- Produces: `static DetachedToolWindows.Shell DetachedFrame.open(DetachedToolWindows.Opening opening,
  DetachedToolWindows.Events events, ThemeController themes, WindowLook look)` — never called headless.

- [ ] **Step 1: Implement** after reading `NativeShells.frame` / `dialog` / `menuBar` (`dev.jasper.app.windows`) and
  `WindowContent.mountChild` as the models:
  - FLOAT: `new JDialog(opening.owner(), title, Dialog.ModalityType.MODELESS)`, resizable; WINDOW: `new JFrame(title)`.
  - `setIconImages(ApplicationIcon.images())`, `PlainTitlePane.install(root)`, accessible name = title.
  - Title bar: macOS — `MacTitleBar` (style `DIALOG` for Float, `WINDOW` for Window) with the title, attached after
    `install`; under the look's classic title the band or the system bar with `MacAppearance.follow`, as `mountChild`
    chooses; Windows/Linux — FlatLaf's title pane with `TITLE_BAR_BACKGROUND`/`TITLE_BAR_FOREGROUND` from the chrome
    theme, as `mountChild` sets them.
  - Content pane: a `BorderLayout` panel holding `opening.frame()` at CENTER; minimum size 240×160 scaled;
    `setBounds(opening.bounds())`.
  - macOS with the screen menu bar and WINDOW: a `JMenuBar` with File ▸ Close Window (the menu shortcut + W) running
    `events.closing()` (`NativeShells.menuBar`'s shape; no Quit on macOS).
  - `DO_NOTHING_ON_CLOSE`; `windowClosing` → `events.closing()`; `componentMoved`/`componentResized` →
    `events.moved(getBounds())`; `MacTitleBar.setActive` on (de)activation.
  - `opening.keys().apply(root)` once; closed on dispose.
  - Theme: `themes.subscribe` → `updateComponentTreeUI(window)` when the chrome changed, `bar.setLight`, title colours.
  - Shell: `show` → `setVisible(true)`; `toFront` → de-iconify (Window), `toFront()`, `requestFocus()`; `dispose` →
    close subscriptions, keys, bar, then `dispose()`; `active` → `isActive()`; `place` → `setBounds`.
  - `JasperApplication.composeContent`: for the main window and `!GraphicsEnvironment.isHeadless()`,
    `content.detachedShells((opening, events) -> DetachedFrame.open(opening, events, themes, windowLook()))`.
- [ ] **Step 2: Run** `./gradlew :jasper-app:test --tests '*JasperApplicationWindowsTest' --tests 'dev.jasper.app.workspace.*'` — pass.
- [ ] **Step 3: Commit** — `Tool windows: the native Float dialog and Window frame`.

---

### Task 9: Documentation and records

**Files:**
- Modify: `docs/workspace.md` ("Tool windows and the dock": View Mode ▸ Docked/Float/Window, closing hides, the stripe
  button raises, Move to on a detached tool window docks it, shortcuts in its window, saved per look),
  `docs/app-architecture.md` (the `dock` row: modes and bounds; the `workspace` row: `DetachedToolWindows`,
  `DetachedFrame`), `docs/STATUS.md` (a new top entry: what landed, the check's counts, GUI checks per platform from the
  spec's Testing section), `docs/design/theme-engine/migration.csv` (rows for `ToolWindowMode`, `DetachedToolWindows`,
  `DetachedFrame`, and the new test classes, following neighbouring rows).
- [ ] **Step 1:** Edit the docs (smallest accurate edits; AGENTS.md "Documentation edits").
- [ ] **Step 2: Run** `./gradlew check --continue` — record tests/failures/errors/skipped from
  `*/build/test-results/test/*.xml` in the STATUS entry.
- [ ] **Step 3: Commit** — `Docs: Float and Window tool windows`.
