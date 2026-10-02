# Jasper Dock Layout Plan 1: Six-Slot Layout Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single left rail and the three nested-split regions with IntelliJ's tool window model, apart
from dragging: two stripes, six slots, a header on every tool window, six-slot Move to menus, `ui-state.toml`
version 2 with migration, and `PanelSpec.withSplit()` in SDK 0.13.0.

**Architecture:** A new JDK-only package `dev.jasper.app.dock` holds the model (`Slot`, `Side`, `DockState`) and a
pure layout function (`DockGeometry`). In `dev.jasper.app.workspace`, `ToolWindowArea` lays out the terminal column
and the tool window frames from that geometry and owns the dividers; two `ToolStripe`s replace `WindowRail`; each
frame carries a `ToolWindowHeader`. `WindowContributions` keeps its lazy per-window panel instances but moves slot,
order and visibility into the window's `DockState`, and one listener on that state renders the stripes, lays out
the area, tells the plugins and saves.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing with FlatLaf 3.7, TomlJ 1.1.1, JUnit 6.1.3, AssertJ
3.27.7. No new dependencies.

**Spec:** [Dock layout design](../specs/2026-10-01-jasper-dock-layout-design.md) (binding; its Decisions list was
approved by the user section by section). Plan 2 (drag and drop) builds on this plan:
[plan 2](2026-10-02-jasper-dock-layout-plan-2-drag-and-drop.md).

**Status:** Written 2026-10-02 on `claude/dock-layout` (base `7749e13b`, the spec commit, on top of `cc0429f5`
"Merge theme system Plan 4"). Not executed. Waiting for the user's review.

**Execution:** The repository's usual shape: superpowers:subagent-driven-development, one implementer and one
reviewer per task, fix rounds, then one whole-branch review. Record any deviation from the plan text in this banner
and in `docs/STATUS.md`.

## Global Constraints

Every task's requirements include this section.

- `dev.jasper.app.dock` is JDK-only and holds no Swing components; it may use `java.awt.Rectangle` and
  `java.awt.Point` and nothing else from `java.awt`. Its `package-info` says so and `verifyApplicationArchitecture`
  enforces it (Task 1).
- Slots, in this order: `LEFT_TOP`, `LEFT_BOTTOM`, `BOTTOM_LEFT`, `RIGHT_TOP`, `RIGHT_BOTTOM`, `BOTTOM_RIGHT`. Display
  names: Left Top, Left Bottom, Bottom Left, Right Top, Right Bottom, Bottom Right. SDK defaults: `Anchor.LEFT` →
  `LEFT_TOP`, `Anchor.LEFT` + `withSplit()` → `LEFT_BOTTOM`, `Anchor.BOTTOM` → `BOTTOM_LEFT`, `Anchor.RIGHT` →
  `RIGHT_TOP`, `Anchor.RIGHT` + `withSplit()` → `RIGHT_BOTTOM`, `Anchor.BOTTOM` + `withSplit()` → `BOTTOM_RIGHT`.
- `DockState`: per slot an ordered list of ids and at most one active id; sizes per side (left width, right width,
  bottom height) in unscaled pixels, defaults 260 / 260 / 200; split ratios per side, 0.1–0.9, default 0.5. Showing a
  window hides the slot's other active window. `move` keeps visibility. Every mutation that changes something fires
  one change event saying what changed; a mutation that changes nothing fires none.
- Geometry: the bottom area spans the full width, stripe to stripe; side panels sit above it. No setting for the
  other geometry. Dividers are 6px. Tool window minimum 120px; terminal column minimum 200 × 120 px; all scaled. A
  too-small window squeezes the bottom area, then the right side, then the left side, before the terminal column goes
  under its minimum. The squeeze never changes the saved sizes.
- Gutters and frames keep today's rules: tool windows in rounded frames; the center frame rounded only while the
  stripes are visible; with the stripes hidden the terminal fills its area edge to edge. Tool window frames keep
  `Jasper.zone = chrome`. The center frame paints the terminal palette background.
- Stripes: both use `window.rail_width` (28–64, default 28). Left stripe: `LEFT_TOP` group, separator only when the
  `LEFT_BOTTOM` group has icons, `LEFT_BOTTOM` group, plugin rail actions behind their own separator, flexible space,
  `BOTTOM_LEFT` group pinned to the foot. The right stripe mirrors it with `RIGHT_TOP`, `RIGHT_BOTTOM`,
  `BOTTOM_RIGHT` and no rail actions. Both stripes show together whenever there is at least one tool window or rail
  action, an empty right stripe included. View › Rail (`view.rail`) hides and shows both.
- Stripe button: selected means active in its slot (the rounded filled toolbar selection); click toggles;
  right-click opens **Move to ›** (six slots by display name, current one checked) and **Hide**.
- Header: title on the left; **⋮** (options: Move to › six slots, Hide) and **—** (hide) on the right; 32px tall,
  scaled; no bar colour of its own. Double-click does nothing. Hiding a window by any route returns focus to the
  terminal.
- `ui-state.toml` version 2 is exactly the spec's format (`version`, `rail_visible`, `[dock]` with six keys,
  `[panels."<id>"]` with `slot`, `order`, `visible`). A saved slot wins over the plugin's default; a panel with no
  saved entry is appended to its default slot's group. A bad entry or key falls back alone. Version 1 migrates as the
  spec says. `[windows]` and `[chooser]` are untouched.
- SDK 0.13.0 (0.12.0 is taken by the theme system's workspace appearance). `PanelSpec` gains `boolean split`,
  `split()` and `withSplit()`; the four- and five-argument constructors stay as explicit constructors so plugins
  built against earlier SDKs still link; `withToggleActionId` keeps the flag. `Anchor`'s constants are unchanged.
- `PanelRegion` is removed; `dock.Slot` replaces it. Unchanged: `PanelRequest`, View › Tools, toggle actions and the
  `PANEL` shortcut context.
- SFTP registers with `withSplit()`; Remote takes a minor bump (0.10.0 → 0.11.0). Sessions, Tunnels, File Servers
  and Sample stay `Anchor.LEFT`.
- Jasper is an MVP: no compatibility shims or deprecations beyond the two `PanelSpec` constructors the spec keeps.
  Interim bridges inside this branch (Tasks 3 and 4) are deleted by Task 8.
- Tests are headless. Swing lays out hidden children at 0 × 0, so layout tests call
  `LayoutTestSupport.layoutTree`, which recurses into hidden containers too. Never launch the GUI
  (`./gradlew :jasper-app:run`) or the benchmark.
- Work only in `.worktrees/dock-layout` on `claude/dock-layout`; check `git branch --show-current` before every
  commit. Never commit on `main` or in the main checkout. Commit messages end with the trailer block shown in each
  task. Do not merge to `main`.
- The unmerged branch `claude/window-size` also edits `UiState`: whichever of the two merges second rebases onto the
  other and reconciles `UiState`, `UiStateTest` and the v2 save format.
- Source hygiene (AGENTS.md): no raw control, private-use or unpaired surrogate characters in Java source. The ⋮
  and — glyphs never appear in Java string literals; the header uses IntelliJ icons.

## Review Focus

The five inputs the spec implies that are most likely to bite a real user, most likely first. Each has a test in the
task that owns the code.

1. **An existing `ui-state.toml` version 1** (Sessions visible on the left at a custom width, other panels hidden,
   a `[windows]` and a `[chooser]` section): the first launch shows Sessions where it was, at that width, the other
   sections survive, and the next save writes version 2. Tests: Task 4 `aVersionOneFileMigrates…`, Task 8
   `aVersionOneLayoutOpensWhereItWas`.
2. **Plugins registering in a different order from the saved order, and a plugin turned off and on while its panel
   shows**: the stripe order comes back as saved, the frame disappears and returns to the same slot and position, and
   focus goes back to the terminal. Tests: Task 8 `theSavedOrderSurvivesAnyRegistrationOrder`,
   `aReloadedPluginGoesBackWhereItWas`.
3. **A window much smaller than its panels** (all six slots showing, the window dragged small): nothing gets a
   negative size, the terminal column is the last to give way, and the saved sizes come back when the window grows.
   Tests: Task 2 `tinyBoundsNeverProduceNegativeSizes`, `aNarrowWindowSqueezes…`; Task 7 `aTinyAreaLaysOutWithoutNegativeSizes`.
4. **Several windows**: moving a panel in one window does not move it in another open window; a new window opens with
   the last saved layout. Test: Task 8 `eachWindowKeepsItsOwnLayoutAndANewWindowTakesTheLastSaved`.
5. **A theme change while frames are open and while a panel is hidden**: headers, frames and stripes take the new
   theme; a hidden panel keeps its component and handlers. Tests: Task 7 `SidePanelRenderTest` (light then dark,
   header included); Task 8 `hiddenPanelKeepsItsComponentAndHandlersAcrossADelegateThemeUpdate`.

## Decisions made while planning (for the user's review)

These fill gaps the spec leaves or adjust its sketches. Each is small; say if one should change.

1. `DockGeometry.compute(Rectangle, DockState, Metrics)`: the spec's third parameter is called `Minimums`; it is
   `Metrics` here because it also carries the 6px divider and the UI scale, which turns the state's unscaled sizes
   into pixels. `Metrics.at(scale)` gives Jasper's values.
2. Each divider carries the range its leading edge may move in, so a drag cannot push a tool window under 120px or
   the terminal column under its minimum (JSplitPane's behaviour today).
3. A divider drag lays out a scratch copy of the state while the mouse moves and changes the real state once, on
   release, so a drag saves `ui-state.toml` once.
4. `dev.jasper.app.persistence` may depend on `dev.jasper.app.dock` (for `Slot` and the size limits); its
   `package-info` says so.
5. Saved order: a slot's saved `order` values also keep a place for panels that are not loaded right now, so a plugin
   that registers late, or is turned off and on, goes back to its position. Migrated version 1 entries have no order
   (`Panel.LAST`) and follow registration order, as the spec says.
6. The testkit and contract harness print a split panel's default as `LEFT+split` (`id|title|LEFT+split|toggle`).
7. The duplicate in-content titles of Sessions, Tunnels and File Servers are removed and Sample's "Sample panel"
   label becomes a sentence. Only Remote's version changes (0.11.0); File Server and Sample keep theirs because the
   change is cosmetic and they ship with the app.
8. The header title is bold, like the panel headings it replaces.
9. Saving: every dock change saves `ui-state.toml` (today only show, hide and move do), which includes restoring
   panels while a window opens.

## Baseline facts (verified 2026-10-02)

- `WorkspaceRegions` nests `JSplitPane`s (bottom wraps the center, right wraps that, left wraps that) around a center
  `Host`; `refreshGutters` applies: top gutter 6 unless the header shows; leading gutter 6 unless the rail shows;
  bottom gutter 6 unless the status bar shows; the center is rounded and has the 8px lower inset only while the rail
  shows; with the rail hidden the center has no gutters. Tool window hosts set `Jasper.zone = chrome`.
- `WindowRail` renders one toggle per panel grouped by region, then plain action buttons; buttons are square at
  `rail_width − 6`, 3px in, 4px apart; separators are `rail_width − 14` wide at 8px steps.
- `WindowContributions` owns `PanelInstance { entry, region, visible, size, site, component }` and calls
  `owner.regions()` and `owner.rail()` directly; it saves one panel at a time with `UiState.Panel(region, visible,
  size)`.
- `UiState` reads `version = 1` strictly (any bad panel entry makes the whole file fall back); one shared instance
  is loaded by `JasperApplication` and passed to every window.
- All seven bundled manifests declare `sdk = ">=0.12.0, <0.13"`; 0.13.0 needs a new range in each (the 0.12.0 bump
  moved all seven to the new floor).
- IntelliJ icons `general/moreVertical` and `general/hide` are in the bundled catalog (`AppIcons.bundled`).
- The planning machine has no JBR 25 (only GraalVM 21 and JDK 17), so `./gradlew` could not run there. The code of
  Tasks 1, 2 and 4 and the Swing components and tests of Tasks 5–7 were compiled and run headlessly in a scratch copy
  instead (GraalVM 21, JUnit 5.10, AssertJ 3.27, TomlJ 1.1.1, FlatLaf 3.7): 48 tests pass. "Plan self-review" at the
  end lists what was not compiled.

## File map and dependency order

| Task | Creates | Modifies | Deletes |
|---|---|---|---|
| 1 | `dock/{package-info,Side,Slot,DockState}.java`, `DockStateTest` | `gradle/application-architecture.gradle.kts` | |
| 2 | `dock/DockGeometry.java`, `DockGeometryTest` | | |
| 3 | | SDK `PanelSpec`, `Anchor`, `PanelHost`, `Panels`, `Rail`, `JasperSdk` and SDK tests; testkit `FakePluginHost`, `ContractHarness`, `PluginContractTest`; `contributions/{Contributions,PanelEntry,package-info}`; `plugins/{HostedUi,package-info}`; `WindowContributions` (bridge); app tests naming `PanelRegion` in `addPanel`; seven `plugin.toml`; `VersionRangeTest`; two doc lines | |
| 4 | | `persistence/{UiState,package-info}`, `UiStateTest`, `WindowContributions` (bridge) | |
| 5 | `workspace/{ToolWindowMenu,ToolWindowHeader}.java`, `ToolWindowHeaderTest` | | |
| 6 | `workspace/ToolStripe.java`, `ToolStripeTest` | | |
| 7 | `workspace/ToolWindowArea.java`, `ToolWindowAreaTest`, test `DockTestSupport` | `SidePanelRenderTest`, `ToolWindowSurfaceTest` | |
| 8 | | `WindowContent`, `WindowContributions` (rewrite), `workspace/package-info`, `WindowPanelsTest` (rewrite), `RailWidthTest`, `GraphiteChromeTest`, `BarHeightTest`, `MacTitleBarTest`, `WindowCommandPaletteTest`, `ThemeControllerTest`, `WorkspaceThemeTest`, `PluginPaneAppearanceTest`, `DockTestSupport` | `WorkspaceRegions`, `WorkspaceRegionsTest`, `WindowRail`, `contributions/PanelRegion` |
| 9 | | `SftpUi`, Remote `plugin.toml`, `HostsPanel`, `TunnelsPanel`, `FileServersPanel`, `SamplePlugin`, plugin tests | |
| 10 | | `docs/{sdk-architecture,plugin-authoring,app-architecture,app-maintenance,configuration,STATUS}.md`, `jasper-sdk/README.md`, this banner | |

Paths: app sources are under `jasper-app/src/main/java/dev/jasper/app/`, app tests under
`jasper-app/src/test/java/dev/jasper/app/`. Tasks run in order; each leaves `./gradlew check` green.

Common commands (from the worktree root):

```bash
./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*'
./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'
./gradlew verifyApplicationArchitecture
./gradlew check
```

---

## Task 1: The dock model: `Side`, `Slot`, `DockState`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/package-info.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/Side.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/Slot.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/DockState.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/dock/DockStateTest.java`
- Modify: `gradle/application-architecture.gradle.kts` (the edge loop, after the SDK confinement check)

**Interfaces:**
- Consumes: nothing.
- Produces (all `public`, package `dev.jasper.app.dock`):
  - `enum Side { LEFT, RIGHT, BOTTOM }`
  - `enum Slot { LEFT_TOP, LEFT_BOTTOM, BOTTOM_LEFT, RIGHT_TOP, RIGHT_BOTTOM, BOTTOM_RIGHT }` with `Side side()`,
    `boolean split()`, `Side stripe()` (LEFT or RIGHT), `Group group()`, `Slot partner()`, `String displayName()`,
    `static Slot of(Side side, boolean split)`, and nested `enum Group { TOP, SECOND, FOOT }`.
  - `final class DockState` with constants `DEFAULT_SIDE = 260`, `DEFAULT_BOTTOM = 200`, `MIN_SIZE = 120`,
    `MAX_SIZE = 4000`, `DEFAULT_RATIO = 0.5`, `MIN_RATIO = 0.1`, `MAX_RATIO = 0.9`; nested
    `record Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown)`; queries
    `Optional<Slot> slot(String)`, `List<String> ids(Slot)`, `Optional<String> active(Slot)`, `boolean visible(String)`,
    `boolean showing(Side)`, `int size(Side)`, `double ratio(Side)`; mutations `add(String, Slot)`,
    `add(String, Slot, int)`, `remove(String)`, `show(String)`, `hide(String)`, `toggle(String)`,
    `move(String, Slot, int)`, `resize(Side, int)`, `setRatio(Side, double)`; `DockState copy()`;
    `addListener(Consumer<Change>)`, `removeListener(Consumer<Change>)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/DockStateTest.java`:

```java
package dev.jasper.app.dock;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class DockStateTest {
    private final DockState state = new DockState();
    private final List<DockState.Change> changes = new ArrayList<>();

    private DockState listening() { state.addListener(changes::add); return state; }

    private static DockState.Change order() { return new DockState.Change(false, true, List.of(), List.of()); }

    @Test void slotsKnowTheirSideHalfStripeGroupPartnerAndName() {
        assertThat(Slot.values()).extracting(Slot::displayName).containsExactly(
            "Left Top", "Left Bottom", "Bottom Left", "Right Top", "Right Bottom", "Bottom Right");
        assertThat(Slot.LEFT_BOTTOM.side()).isEqualTo(Side.LEFT);
        assertThat(Slot.LEFT_BOTTOM.split()).isTrue();
        assertThat(Slot.BOTTOM_LEFT.side()).isEqualTo(Side.BOTTOM);
        assertThat(Slot.BOTTOM_LEFT.stripe()).isEqualTo(Side.LEFT);
        assertThat(Slot.BOTTOM_RIGHT.stripe()).isEqualTo(Side.RIGHT);
        assertThat(Slot.BOTTOM_RIGHT.split()).isTrue();
        assertThat(Slot.values()).extracting(Slot::group).containsExactly(
            Slot.Group.TOP, Slot.Group.SECOND, Slot.Group.FOOT, Slot.Group.TOP, Slot.Group.SECOND, Slot.Group.FOOT);
        assertThat(Slot.values()).extracting(Slot::partner).containsExactly(
            Slot.LEFT_BOTTOM, Slot.LEFT_TOP, Slot.BOTTOM_RIGHT, Slot.RIGHT_BOTTOM, Slot.RIGHT_TOP, Slot.BOTTOM_LEFT);
        for (Slot slot : Slot.values()) assertThat(Slot.of(slot.side(), slot.split())).isSameAs(slot);
    }

    @Test void aNewStateHasEmptySlotsAndTheDefaultSizesAndRatios() {
        for (Slot slot : Slot.values()) {
            assertThat(state.ids(slot)).isEmpty();
            assertThat(state.active(slot)).isEmpty();
        }
        assertThat(state.size(Side.LEFT)).isEqualTo(260);
        assertThat(state.size(Side.RIGHT)).isEqualTo(260);
        assertThat(state.size(Side.BOTTOM)).isEqualTo(200);
        for (Side side : Side.values()) {
            assertThat(state.ratio(side)).isEqualTo(0.5);
            assertThat(state.showing(side)).isFalse();
        }
    }

    @Test void addAppendsOrInsertsAHiddenWindowAndRejectsADuplicate() {
        listening().add("a", Slot.LEFT_TOP);
        state.add("b", Slot.LEFT_TOP);
        state.add("c", Slot.LEFT_TOP, 0);
        state.add("d", Slot.LEFT_TOP, 99);
        assertThat(state.ids(Slot.LEFT_TOP)).containsExactly("c", "a", "b", "d");
        assertThat(state.slot("a")).hasValue(Slot.LEFT_TOP);
        assertThat(state.slot("absent")).isEmpty();
        assertThat(state.visible("a")).isFalse();
        assertThat(changes).hasSize(4).allSatisfy(change -> assertThat(change).isEqualTo(order()));
        assertThatIllegalArgumentException().isThrownBy(() -> state.add("a", Slot.RIGHT_TOP));
        assertThat(changes).hasSize(4);
    }

    @Test void showingAWindowHidesTheOtherWindowOfItsSlotOnly() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP); state.add("c", Slot.LEFT_BOTTOM);
        listening().show("a");
        state.show("c");
        state.show("b");
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
        assertThat(state.active(Slot.LEFT_BOTTOM)).hasValue("c");
        assertThat(state.visible("a")).isFalse();
        assertThat(state.showing(Side.LEFT)).isTrue();
        assertThat(state.showing(Side.RIGHT)).isFalse();
        assertThat(changes).containsExactly(
            new DockState.Change(true, true, List.of(), List.of("a")),
            new DockState.Change(true, true, List.of(), List.of("c")),
            new DockState.Change(true, true, List.of("a"), List.of("b")));
        state.show("b");
        state.show("absent");
        state.hide("a");
        state.hide("absent");
        assertThat(changes).as("changing nothing is silent").hasSize(3);
    }

    @Test void hideAndToggle() {
        state.add("a", Slot.BOTTOM_RIGHT);
        listening().toggle("a");
        assertThat(state.visible("a")).isTrue();
        state.toggle("a");
        assertThat(state.visible("a")).isFalse();
        state.show("a");
        state.hide("a");
        assertThat(changes).containsExactly(
            new DockState.Change(true, true, List.of(), List.of("a")),
            new DockState.Change(true, true, List.of("a"), List.of()),
            new DockState.Change(true, true, List.of(), List.of("a")),
            new DockState.Change(true, true, List.of("a"), List.of()));
    }

    @Test void moveKeepsVisibilityAndAShowingWindowHidesTheTargetsActiveWindow() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.RIGHT_TOP); state.add("c", Slot.RIGHT_TOP);
        state.show("a"); state.show("b");
        listening().move("a", Slot.RIGHT_TOP, 1);
        assertThat(state.ids(Slot.LEFT_TOP)).isEmpty();
        assertThat(state.ids(Slot.RIGHT_TOP)).containsExactly("b", "a", "c");
        assertThat(state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(state.active(Slot.LEFT_TOP)).isEmpty();
        assertThat(changes).containsExactly(new DockState.Change(true, true, List.of("b"), List.of()));

        changes.clear();
        state.move("c", Slot.BOTTOM_LEFT, 0);
        assertThat(state.visible("c")).as("a hidden window stays hidden").isFalse();
        assertThat(state.slot("c")).hasValue(Slot.BOTTOM_LEFT);
        assertThat(changes).containsExactly(order());
    }

    @Test void moveWithinASlotReordersAndClampsTheIndex() {
        for (String id : List.of("a", "b", "c")) state.add(id, Slot.LEFT_TOP);
        state.show("b");
        listening().move("a", Slot.LEFT_TOP, 2);
        assertThat(state.ids(Slot.LEFT_TOP)).containsExactly("b", "c", "a");
        state.move("a", Slot.LEFT_TOP, -5);
        assertThat(state.ids(Slot.LEFT_TOP)).containsExactly("a", "b", "c");
        state.move("c", Slot.LEFT_TOP, 99);
        state.move("a", Slot.LEFT_TOP, 0);
        state.move("absent", Slot.LEFT_TOP, 0);
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
        assertThat(changes).as("only the two real reorders").containsExactly(order(), order());
    }

    @Test void removingAShowingWindowReportsItHidden() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP);
        state.show("a");
        listening().remove("a");
        state.remove("b");
        state.remove("absent");
        assertThat(state.ids(Slot.LEFT_TOP)).isEmpty();
        assertThat(state.showing(Side.LEFT)).isFalse();
        assertThat(changes).containsExactly(new DockState.Change(true, true, List.of("a"), List.of()), order());
    }

    @Test void sizesAndRatiosAreClampedAndOnlyAChangeFires() {
        listening().resize(Side.LEFT, 300);
        state.resize(Side.LEFT, 300);
        state.resize(Side.RIGHT, 10);
        state.resize(Side.BOTTOM, 99_999);
        assertThat(state.size(Side.LEFT)).isEqualTo(300);
        assertThat(state.size(Side.RIGHT)).isEqualTo(DockState.MIN_SIZE);
        assertThat(state.size(Side.BOTTOM)).isEqualTo(DockState.MAX_SIZE);
        state.setRatio(Side.LEFT, 0.25);
        state.setRatio(Side.LEFT, 0.25);
        state.setRatio(Side.RIGHT, 0);
        state.setRatio(Side.BOTTOM, 2);
        assertThat(state.ratio(Side.LEFT)).isEqualTo(0.25);
        assertThat(state.ratio(Side.RIGHT)).isEqualTo(DockState.MIN_RATIO);
        assertThat(state.ratio(Side.BOTTOM)).isEqualTo(DockState.MAX_RATIO);
        assertThatIllegalArgumentException().isThrownBy(() -> state.setRatio(Side.LEFT, Double.NaN));
        assertThat(changes).hasSize(6).allSatisfy(change ->
            assertThat(change).isEqualTo(new DockState.Change(true, false, List.of(), List.of())));
    }

    @Test void aCopyIsIndependentAndHasNoListeners() {
        state.add("a", Slot.LEFT_TOP); state.show("a"); state.resize(Side.LEFT, 333); state.setRatio(Side.BOTTOM, 0.7);
        listening();
        DockState copy = state.copy();
        assertThat(copy.ids(Slot.LEFT_TOP)).containsExactly("a");
        assertThat(copy.active(Slot.LEFT_TOP)).hasValue("a");
        assertThat(copy.size(Side.LEFT)).isEqualTo(333);
        assertThat(copy.ratio(Side.BOTTOM)).isEqualTo(0.7);
        copy.move("a", Slot.RIGHT_BOTTOM, 0);
        copy.resize(Side.LEFT, 400);
        assertThat(state.slot("a")).hasValue(Slot.LEFT_TOP);
        assertThat(state.size(Side.LEFT)).isEqualTo(333);
        assertThat(changes).isEmpty();
    }

    @Test void aRemovedListenerHearsNothing() {
        state.addListener(changes::add);
        java.util.function.Consumer<DockState.Change> other = change -> { throw new AssertionError("removed"); };
        state.addListener(other);
        state.removeListener(other);
        state.add("a", Slot.LEFT_TOP);
        assertThat(changes).hasSize(1);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.DockStateTest'`
Expected: FAIL — compilation errors, `package dev.jasper.app.dock does not exist` / `cannot find symbol: class DockState`.

- [ ] **Step 3: Write the package**

Create `jasper-app/src/main/java/dev/jasper/app/dock/package-info.java`:

```java
/**
 * The tool window dock model: the six slots, one window's {@code DockState} and the {@code DockGeometry} that lays it
 * out. JDK-only: no Swing or AWT components, and from {@code java.awt} only the {@code Rectangle} and {@code Point}
 * values; {@code verifyApplicationArchitecture} enforces it. Each window owns its state and confines it to the EDT.
 * <p>Allowed outgoing Jasper dependencies: no other Jasper package.
 * The module architecture check forbids package cycles; app types are not an external plugin API.
 */
package dev.jasper.app.dock;
```

Create `jasper-app/src/main/java/dev/jasper/app/dock/Side.java`:

```java
package dev.jasper.app.dock;

/** Where a slot docks: beside the terminal column on the left or right, or in the full-width bottom area. */
public enum Side { LEFT, RIGHT, BOTTOM }
```

Create `jasper-app/src/main/java/dev/jasper/app/dock/Slot.java`:

```java
package dev.jasper.app.dock;

/**
 * The six places a tool window docks: IntelliJ's anchor plus split. On a side, the first slot takes the upper half and
 * the split slot the lower half; in the bottom area, the first slot takes the left half and the split slot the right.
 * A slot alone on its side, or alone in the bottom area, takes all of it.
 */
public enum Slot {
    LEFT_TOP(Side.LEFT, false, Side.LEFT, "Left Top"),
    LEFT_BOTTOM(Side.LEFT, true, Side.LEFT, "Left Bottom"),
    BOTTOM_LEFT(Side.BOTTOM, false, Side.LEFT, "Bottom Left"),
    RIGHT_TOP(Side.RIGHT, false, Side.RIGHT, "Right Top"),
    RIGHT_BOTTOM(Side.RIGHT, true, Side.RIGHT, "Right Bottom"),
    BOTTOM_RIGHT(Side.BOTTOM, true, Side.RIGHT, "Bottom Right");

    /** A slot's group on its stripe, top to bottom: the top group, the group under the separator, the foot. */
    public enum Group { TOP, SECOND, FOOT }

    private final Side side;
    private final boolean split;
    private final Side stripe;
    private final String displayName;

    Slot(Side side, boolean split, Side stripe, String displayName) {
        this.side = side; this.split = split; this.stripe = stripe; this.displayName = displayName;
    }

    /** Where the slot docks. */
    public Side side() { return side; }

    /** Whether this is its side's secondary half (the lower half of a side, the right half of the bottom area). */
    public boolean split() { return split; }

    /** The stripe that shows the slot's icons: {@link Side#LEFT} or {@link Side#RIGHT}, never {@link Side#BOTTOM}. */
    public Side stripe() { return stripe; }

    /** The slot's group on its stripe. */
    public Group group() { return side == Side.BOTTOM ? Group.FOOT : split ? Group.SECOND : Group.TOP; }

    /** The other slot on the same side. */
    public Slot partner() { return of(side, !split); }

    /** The name menus show, such as "Left Top". */
    public String displayName() { return displayName; }

    /** The slot for a side and split flag. */
    public static Slot of(Side side, boolean split) {
        return switch (side) {
            case LEFT -> split ? LEFT_BOTTOM : LEFT_TOP;
            case RIGHT -> split ? RIGHT_BOTTOM : RIGHT_TOP;
            case BOTTOM -> split ? BOTTOM_RIGHT : BOTTOM_LEFT;
        };
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/dock/DockState.java`:

```java
package dev.jasper.app.dock;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * One window's tool window layout: each slot's windows in stripe order with at most one of them active, a size per
 * side in unscaled pixels and a split ratio per side. Mutable and confined to its owner's thread (the EDT). Every
 * mutation that changes something tells each listener once, with what changed; one that changes nothing is silent.
 */
public final class DockState {
    /** Default and limits of a side's size, in unscaled pixels. */
    public static final int DEFAULT_SIDE = 260, DEFAULT_BOTTOM = 200, MIN_SIZE = 120, MAX_SIZE = 4000;
    /** Default and limits of a side's split ratio: the share of its first slot. */
    public static final double DEFAULT_RATIO = 0.5, MIN_RATIO = 0.1, MAX_RATIO = 0.9;

    /**
     * What one mutation changed.
     *
     * @param layout the showing windows, a size or a ratio changed, so the area lays out again
     * @param stripes a window was added, removed or moved, or a slot's active window changed, so the stripes render again
     * @param hidden windows that stopped showing, in order
     * @param shown windows that started showing, in order
     */
    public record Change(boolean layout, boolean stripes, List<String> hidden, List<String> shown) {
        public Change {
            hidden = List.copyOf(hidden);
            shown = List.copyOf(shown);
        }
    }

    private static final Change ORDER = new Change(false, true, List.of(), List.of());
    private static final Change LAYOUT = new Change(true, false, List.of(), List.of());

    private final EnumMap<Slot, List<String>> order = new EnumMap<>(Slot.class);
    private final EnumMap<Slot, String> active = new EnumMap<>(Slot.class);
    private final EnumMap<Side, Integer> sizes = new EnumMap<>(Side.class);
    private final EnumMap<Side, Double> ratios = new EnumMap<>(Side.class);
    private final List<Consumer<Change>> listeners = new ArrayList<>();

    public DockState() {
        for (Slot slot : Slot.values()) order.put(slot, new ArrayList<>());
        sizes.put(Side.LEFT, DEFAULT_SIDE);
        sizes.put(Side.RIGHT, DEFAULT_SIDE);
        sizes.put(Side.BOTTOM, DEFAULT_BOTTOM);
        for (Side side : Side.values()) ratios.put(side, DEFAULT_RATIO);
    }

    /** An independent copy without listeners, for laying out a change before making it. */
    public DockState copy() {
        var copy = new DockState();
        for (Slot slot : Slot.values()) copy.order.get(slot).addAll(order.get(slot));
        copy.active.putAll(active);
        copy.sizes.putAll(sizes);
        copy.ratios.putAll(ratios);
        return copy;
    }

    public void addListener(Consumer<Change> listener) { listeners.add(Objects.requireNonNull(listener, "listener")); }

    public void removeListener(Consumer<Change> listener) { listeners.remove(listener); }

    /** The slot holding the window, if it is docked. */
    public Optional<Slot> slot(String id) {
        for (Slot slot : Slot.values()) if (order.get(slot).contains(id)) return Optional.of(slot);
        return Optional.empty();
    }

    /** The slot's windows in stripe order. */
    public List<String> ids(Slot slot) { return List.copyOf(order.get(slot)); }

    /** The window the slot shows, if any. */
    public Optional<String> active(Slot slot) { return Optional.ofNullable(active.get(slot)); }

    /** Whether the window is showing. */
    public boolean visible(String id) { return active.containsValue(id); }

    /** Whether any window shows on that side. */
    public boolean showing(Side side) {
        for (Slot slot : active.keySet()) if (slot.side() == side) return true;
        return false;
    }

    /** A side's size in unscaled pixels: the width of LEFT or RIGHT, the height of BOTTOM. */
    public int size(Side side) { return sizes.get(side); }

    /** The share of the side's first slot when both of its slots show. */
    public double ratio(Side side) { return ratios.get(side); }

    /** Appends a hidden window to the slot's group. */
    public void add(String id, Slot slot) { add(id, slot, Integer.MAX_VALUE); }

    /**
     * Inserts a hidden window into the slot's group.
     *
     * @param index position among the slot's windows, clamped to the group
     * @throws IllegalArgumentException when the window is already docked
     */
    public void add(String id, Slot slot, int index) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        if (slot(id).isPresent()) throw new IllegalArgumentException("Already docked: " + id);
        List<String> ids = order.get(slot);
        ids.add(Math.clamp(index, 0, ids.size()), id);
        fire(ORDER);
    }

    /** Removes the window; an unknown id changes nothing. */
    public void remove(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty()) return;
        order.get(slot.get()).remove(id);
        boolean wasActive = active.remove(slot.get(), id);
        fire(new Change(wasActive, true, wasActive ? List.of(id) : List.of(), List.of()));
    }

    /** Shows the window, hiding whichever other window its slot was showing. */
    public void show(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || id.equals(active.get(slot.get()))) return;
        String previous = active.put(slot.get(), id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of(id)));
    }

    /** Hides the window if it shows. */
    public void hide(String id) {
        Optional<Slot> slot = slot(id);
        if (slot.isEmpty() || !active.remove(slot.get(), id)) return;
        fire(new Change(true, true, List.of(id), List.of()));
    }

    public void toggle(String id) {
        if (visible(id)) hide(id); else show(id);
    }

    /**
     * Moves the window and keeps its visibility: a showing window shows in the new slot and hides the window that
     * slot was showing.
     *
     * @param index position among the target slot's other windows, clamped; within one slot, its new position
     */
    public void move(String id, Slot target, int index) {
        Objects.requireNonNull(target, "target");
        Optional<Slot> from = slot(id);
        if (from.isEmpty()) return;
        List<String> source = order.get(from.get());
        List<String> ids = order.get(target);
        boolean sameSlot = from.get() == target;
        int clamped = Math.clamp(index, 0, sameSlot ? ids.size() - 1 : ids.size());
        if (sameSlot && clamped == source.indexOf(id)) return;
        source.remove(id);
        ids.add(clamped, id);
        if (sameSlot || !active.remove(from.get(), id)) { fire(ORDER); return; }
        String previous = active.put(target, id);
        fire(new Change(true, true, previous == null ? List.of() : List.of(previous), List.of()));
    }

    /** Sets a side's size, clamped to {@link #MIN_SIZE}..{@link #MAX_SIZE} unscaled pixels. */
    public void resize(Side side, int pixels) {
        int value = Math.clamp(pixels, MIN_SIZE, MAX_SIZE);
        int previous = sizes.put(Objects.requireNonNull(side, "side"), value);
        if (previous != value) fire(LAYOUT);
    }

    /**
     * Sets the share of a side's first slot, clamped to {@link #MIN_RATIO}..{@link #MAX_RATIO}.
     *
     * @throws IllegalArgumentException when the ratio is not a number
     */
    public void setRatio(Side side, double ratio) {
        if (Double.isNaN(ratio)) throw new IllegalArgumentException("Ratio is not a number");
        double value = Math.clamp(ratio, MIN_RATIO, MAX_RATIO);
        double previous = ratios.put(Objects.requireNonNull(side, "side"), value);
        if (previous != value) fire(LAYOUT);
    }

    private void fire(Change change) {
        for (Consumer<Change> listener : List.copyOf(listeners)) listener.accept(change);
    }
}
```

- [ ] **Step 4: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.DockStateTest'`
Expected: PASS, 11 tests.

- [ ] **Step 5: Enforce JDK-only in the architecture gate**

In `gradle/application-architecture.gradle.kts`, after the line that defines `supported` (the Buddy set), add:

```kotlin
        // The dock model is pure: JDK types only, and from java.awt only two value classes.
        val dockAwt = setOf("java.awt.Rectangle", "java.awt.Point")
```

and inside the `output.lineSequence().forEach { line -> … }` loop, right after the
`"SDK types are confined to dev.jasper.app.plugins"` check, add:

```kotlin
                if (module == app && packageOf(from) == "dev.jasper.app.dock")
                    check(to.startsWith("dev.jasper.app.dock.") || (to.startsWith("java.") && !to.startsWith("java.awt.")) || to in dockAwt) {
                        "dev.jasper.app.dock is JDK-only and holds no Swing or AWT components: $line"
                    }
```

- [ ] **Step 6: Prove the gate catches a Swing type, then remove the probe**

Temporarily add `private final javax.swing.JPanel probe = null;` as the first field of `DockState`.
Run: `./gradlew verifyApplicationArchitecture`
Expected: FAIL with `dev.jasper.app.dock is JDK-only and holds no Swing or AWT components:` followed by the
`DockState -> javax.swing.JPanel` edge.
Remove the probe line and run `./gradlew verifyApplicationArchitecture` again. Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. `AppDocumentationTest.everyProductionPackageHasAnOwnershipContract` passes because the
package has its `package-info`.

- [ ] **Step 8: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/dock jasper-app/src/test/java/dev/jasper/app/dock gradle/application-architecture.gradle.kts
git commit -F - <<'EOF'
feat(dock): add the slot model and per-window dock state

Six slots with sides, halves and stripe groups; DockState keeps each slot's
order and active window, per-side sizes and ratios, and reports every change
once. The architecture gate keeps dev.jasper.app.dock JDK-only.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 2: `DockGeometry`, the pure layout function

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/DockGeometry.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/dock/DockGeometryTest.java`

**Interfaces:**
- Consumes: Task 1's `DockState`, `Slot`, `Side`.
- Produces (package `dev.jasper.app.dock`, all `public`):
  - `final class DockGeometry` with `static Layout compute(Rectangle bounds, DockState state, Metrics metrics)` and
    `static void drag(DockState state, Divider divider, int position, Metrics metrics)`.
  - `record Metrics(double scale, int divider, int toolWindowMinimum, int terminalMinimumWidth, int terminalMinimumHeight)`
    with `static final Metrics DEFAULT`, `static Metrics at(double scale)` (6, 120, 200, 120), `int scaled(int)`,
    `int unscaled(int)`.
  - `enum Handle { LEFT_WIDTH, RIGHT_WIDTH, BOTTOM_HEIGHT, LEFT_RATIO, RIGHT_RATIO, BOTTOM_RATIO }` with
    `boolean vertical()` (true for a vertical bar dragged left and right: `LEFT_WIDTH`, `RIGHT_WIDTH`, `BOTTOM_RATIO`).
  - `record Divider(Handle handle, Rectangle bounds, Rectangle span, int minimum, int maximum)` with `int position()`.
  - `record Layout(Rectangle terminal, Map<Slot, Rectangle> slots, List<Divider> dividers)` with
    `Optional<Rectangle> slot(Slot)` and `Optional<Divider> divider(Handle)`. Every accessor returns copies.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/DockGeometryTest.java`:

```java
package dev.jasper.app.dock;

import dev.jasper.app.dock.DockGeometry.Handle;
import dev.jasper.app.dock.DockGeometry.Layout;
import dev.jasper.app.dock.DockGeometry.Metrics;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DockGeometryTest {
    private static final Rectangle BOUNDS = new Rectangle(0, 0, 1000, 600);

    /** A state showing one window in each of the given slots. */
    private static DockState showing(Slot... slots) {
        var state = new DockState();
        for (Slot slot : slots) {
            String id = slot.name().toLowerCase(java.util.Locale.ROOT);
            state.add(id, slot);
            state.show(id);
        }
        return state;
    }

    private static Layout layout(DockState state) { return DockGeometry.compute(BOUNDS, state, Metrics.DEFAULT); }

    private static Layout layout(int width, int height, DockState state) {
        return DockGeometry.compute(new Rectangle(0, 0, width, height), state, Metrics.DEFAULT);
    }

    private static Rectangle rect(int x, int y, int width, int height) { return new Rectangle(x, y, width, height); }

    @Test void withNothingShowingTheTerminalTakesEverything() {
        Layout layout = layout(new DockState());
        assertThat(layout.terminal()).isEqualTo(BOUNDS);
        assertThat(layout.slots()).isEmpty();
        assertThat(layout.dividers()).isEmpty();
    }

    @Test void aWindowAloneOnItsSideTakesAllOfIt() {
        Layout left = layout(showing(Slot.LEFT_TOP));
        assertThat(left.slots()).containsOnlyKeys(Slot.LEFT_TOP);
        assertThat(left.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 600));
        assertThat(left.divider(Handle.LEFT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(260, 0, 6, 600));
        assertThat(left.terminal()).isEqualTo(rect(266, 0, 734, 600));
        assertThat(layout(showing(Slot.LEFT_BOTTOM)).slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 0, 260, 600));

        Layout right = layout(showing(Slot.RIGHT_TOP));
        assertThat(right.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 600));
        assertThat(right.divider(Handle.RIGHT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(734, 0, 6, 600));
        assertThat(right.terminal()).isEqualTo(rect(0, 0, 734, 600));
        assertThat(layout(showing(Slot.RIGHT_BOTTOM)).slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 0, 260, 600));

        Layout bottom = layout(showing(Slot.BOTTOM_LEFT));
        assertThat(bottom.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 1000, 200));
        assertThat(bottom.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(0, 394, 1000, 6));
        assertThat(bottom.terminal()).isEqualTo(rect(0, 0, 1000, 394));
        assertThat(layout(showing(Slot.BOTTOM_RIGHT)).slot(Slot.BOTTOM_RIGHT)).hasValue(rect(0, 400, 1000, 200));
    }

    @Test void bothSlotsOfASideSplitItAtItsRatio() {
        Layout left = layout(showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM));
        assertThat(left.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 297));
        assertThat(left.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 303, 260, 297));
        assertThat(left.divider(Handle.LEFT_RATIO).orElseThrow().bounds()).isEqualTo(rect(0, 297, 260, 6));

        Layout right = layout(showing(Slot.RIGHT_TOP, Slot.RIGHT_BOTTOM));
        assertThat(right.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 297));
        assertThat(right.slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 303, 260, 297));
        assertThat(right.divider(Handle.RIGHT_RATIO).orElseThrow().bounds()).isEqualTo(rect(740, 297, 260, 6));

        Layout bottom = layout(showing(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT));
        assertThat(bottom.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 497, 200));
        assertThat(bottom.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(503, 400, 497, 200));
        assertThat(bottom.divider(Handle.BOTTOM_RATIO).orElseThrow().bounds()).isEqualTo(rect(497, 400, 6, 200));
    }

    @Test void theBottomAreaSpansTheFullWidthUnderTheSidesAndTheTerminal() {
        Layout layout = layout(showing(Slot.values()));
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 194));
        assertThat(layout.slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 200, 260, 194));
        assertThat(layout.slot(Slot.RIGHT_TOP)).hasValue(rect(740, 0, 260, 194));
        assertThat(layout.slot(Slot.RIGHT_BOTTOM)).hasValue(rect(740, 200, 260, 194));
        assertThat(layout.terminal()).isEqualTo(rect(266, 0, 468, 394));
        assertThat(layout.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(0, 394, 1000, 6));
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 497, 200));
        assertThat(layout.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(503, 400, 497, 200));
        assertThat(layout.dividers()).extracting(DockGeometry.Divider::handle).containsExactlyInAnyOrder(Handle.values());
    }

    @Test void ratiosMoveTheSplitButKeepBothSlotsAtTheirMinimum() {
        DockState state = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
        state.setRatio(Side.BOTTOM, 0.7);
        Layout layout = layout(state);
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 400, 696, 200));
        assertThat(layout.slot(Slot.BOTTOM_RIGHT)).hasValue(rect(702, 400, 298, 200));

        DockState side = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
        side.setRatio(Side.LEFT, 0.25);
        assertThat(layout(side).slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 149));
        assertThat(layout(side).slot(Slot.LEFT_BOTTOM)).hasValue(rect(0, 155, 260, 445));
        side.setRatio(Side.LEFT, 0.1);
        assertThat(layout(side).slot(Slot.LEFT_TOP)).as("the upper slot keeps its minimum").hasValue(rect(0, 0, 260, 120));
        side.setRatio(Side.LEFT, 0.9);
        assertThat(layout(side).slot(Slot.LEFT_BOTTOM)).as("the lower slot keeps its minimum").hasValue(rect(0, 480, 260, 120));
    }

    @Test void aNarrowWindowSqueezesTheRightSideThenTheLeftBeforeTheTerminal() {
        DockState state = showing(Slot.LEFT_TOP, Slot.RIGHT_TOP);
        Layout seven = layout(700, 600, state);
        assertThat(seven.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 260, 600));
        assertThat(seven.slot(Slot.RIGHT_TOP)).hasValue(rect(472, 0, 228, 600));
        assertThat(seven.terminal()).isEqualTo(rect(266, 0, 200, 600));

        Layout five = layout(500, 600, state);
        assertThat(five.slot(Slot.RIGHT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(five.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(168);
        assertThat(five.terminal().width).isEqualTo(200);

        Layout four = layout(400, 600, state);
        assertThat(four.slot(Slot.RIGHT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(four.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(120);
        assertThat(four.terminal().width).as("only now does the terminal go under its minimum").isEqualTo(148);

        Layout two = layout(200, 600, state);
        assertThat(two.terminal().width).isZero();
        assertThat(two.slot(Slot.RIGHT_TOP)).hasValue(rect(132, 0, 68, 600));
        assertThat(two.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 120, 600));

        assertThat(state.size(Side.LEFT)).as("the squeeze never changes the saved sizes").isEqualTo(260);
        assertThat(state.size(Side.RIGHT)).isEqualTo(260);
        assertThat(layout(state).slot(Slot.RIGHT_TOP).orElseThrow().width).as("a larger window restores them").isEqualTo(260);
    }

    @Test void aShortWindowSqueezesTheBottomAreaFirst() {
        DockState state = showing(Slot.BOTTOM_LEFT);
        Layout three = layout(1000, 300, state);
        assertThat(three.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 126, 1000, 174));
        assertThat(three.terminal()).isEqualTo(rect(0, 0, 1000, 120));
        Layout two = layout(1000, 200, state);
        assertThat(two.slot(Slot.BOTTOM_LEFT)).hasValue(rect(0, 80, 1000, 120));
        assertThat(two.terminal().height).isEqualTo(74);
        assertThat(state.size(Side.BOTTOM)).isEqualTo(200);
    }

    @Test void tinyBoundsNeverProduceNegativeSizes() {
        for (int[] size : new int[][]{{0, 0}, {5, 5}, {130, 90}}) {
            Layout layout = layout(size[0], size[1], showing(Slot.values()));
            assertThat(layout.terminal().width).isNotNegative();
            assertThat(layout.terminal().height).isNotNegative();
            layout.slots().values().forEach(bounds -> {
                assertThat(bounds.width).isNotNegative();
                assertThat(bounds.height).isNotNegative();
            });
            layout.dividers().forEach(divider -> assertThat(divider.minimum()).isLessThanOrEqualTo(divider.maximum()));
        }
    }

    @Test void boundsWithAnOriginOffsetEveryRectangle() {
        Layout layout = DockGeometry.compute(new Rectangle(10, 20, 1000, 600), showing(Slot.LEFT_TOP, Slot.BOTTOM_LEFT), Metrics.DEFAULT);
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(10, 20, 260, 394));
        assertThat(layout.terminal()).isEqualTo(rect(276, 20, 734, 394));
        assertThat(layout.divider(Handle.BOTTOM_HEIGHT).orElseThrow().bounds()).isEqualTo(rect(10, 414, 1000, 6));
        assertThat(layout.slot(Slot.BOTTOM_LEFT)).hasValue(rect(10, 420, 1000, 200));
    }

    @Test void theUiScaleScalesSizesGapsAndMinimums() {
        Metrics doubled = Metrics.at(2);
        Layout layout = DockGeometry.compute(new Rectangle(0, 0, 2000, 1200), showing(Slot.LEFT_TOP), doubled);
        assertThat(layout.slot(Slot.LEFT_TOP)).hasValue(rect(0, 0, 520, 1200));
        assertThat(layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds()).isEqualTo(rect(520, 0, 12, 1200));
        assertThat(layout.terminal()).isEqualTo(rect(532, 0, 1468, 1200));
        assertThat(doubled.scaled(120)).isEqualTo(240);
        assertThat(doubled.unscaled(701)).isEqualTo(351);
    }

    @Test void draggingASideDividerResizesItsSideWithinItsRange() {
        DockState left = showing(Slot.LEFT_TOP);
        var bar = layout(left).divider(Handle.LEFT_WIDTH).orElseThrow();
        assertThat(List.of(bar.position(), bar.minimum(), bar.maximum())).containsExactly(260, 120, 794);
        DockGeometry.drag(left, bar, 300, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).isEqualTo(300);
        DockGeometry.drag(left, bar, 50, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).as("not below a tool window's minimum").isEqualTo(120);
        DockGeometry.drag(left, bar, 900, Metrics.DEFAULT);
        assertThat(left.size(Side.LEFT)).as("not into the terminal's minimum").isEqualTo(794);

        DockState right = showing(Slot.RIGHT_TOP);
        var rightBar = layout(right).divider(Handle.RIGHT_WIDTH).orElseThrow();
        assertThat(List.of(rightBar.minimum(), rightBar.maximum())).containsExactly(200, 874);
        DockGeometry.drag(right, rightBar, 700, Metrics.DEFAULT);
        assertThat(right.size(Side.RIGHT)).isEqualTo(294);
        DockGeometry.drag(right, rightBar, 950, Metrics.DEFAULT);
        assertThat(right.size(Side.RIGHT)).isEqualTo(120);

        DockState bottom = showing(Slot.BOTTOM_RIGHT);
        var bottomBar = layout(bottom).divider(Handle.BOTTOM_HEIGHT).orElseThrow();
        assertThat(List.of(bottomBar.minimum(), bottomBar.maximum())).containsExactly(120, 474);
        DockGeometry.drag(bottom, bottomBar, 350, Metrics.DEFAULT);
        assertThat(bottom.size(Side.BOTTOM)).isEqualTo(244);
        DockGeometry.drag(bottom, bottomBar, 0, Metrics.DEFAULT);
        assertThat(bottom.size(Side.BOTTOM)).as("the terminal keeps its minimum height").isEqualTo(474);
    }

    @Test void draggingASplitDividerSetsTheRatio() {
        DockState side = showing(Slot.LEFT_TOP, Slot.LEFT_BOTTOM);
        var split = layout(side).divider(Handle.LEFT_RATIO).orElseThrow();
        assertThat(List.of(split.minimum(), split.maximum())).containsExactly(120, 474);
        DockGeometry.drag(side, split, 198, Metrics.DEFAULT);
        assertThat(side.ratio(Side.LEFT)).isCloseTo(198 / 594.0, within(1e-9));
        DockGeometry.drag(side, split, 10, Metrics.DEFAULT);
        assertThat(side.ratio(Side.LEFT)).isCloseTo(120 / 594.0, within(1e-9));

        DockState row = showing(Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT);
        DockGeometry.drag(row, layout(row).divider(Handle.BOTTOM_RATIO).orElseThrow(), 300, Metrics.DEFAULT);
        assertThat(row.ratio(Side.BOTTOM)).isCloseTo(300 / 994.0, within(1e-9));
    }

    @Test void draggingStoresUnscaledPixels() {
        DockState state = showing(Slot.LEFT_TOP);
        Metrics doubled = Metrics.at(2);
        var bar = DockGeometry.compute(new Rectangle(0, 0, 2000, 1200), state, doubled).divider(Handle.LEFT_WIDTH).orElseThrow();
        DockGeometry.drag(state, bar, 700, doubled);
        assertThat(state.size(Side.LEFT)).isEqualTo(350);
    }

    @Test void aLayoutHandsOutCopies() {
        Layout layout = layout(showing(Slot.LEFT_TOP));
        layout.terminal().width = 1;
        layout.slot(Slot.LEFT_TOP).orElseThrow().width = 1;
        layout.slots().get(Slot.LEFT_TOP).width = 1;
        layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds().width = 1;
        assertThat(layout.terminal().width).isEqualTo(734);
        assertThat(layout.slot(Slot.LEFT_TOP).orElseThrow().width).isEqualTo(260);
        assertThat(layout.divider(Handle.LEFT_WIDTH).orElseThrow().bounds().width).isEqualTo(6);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.DockGeometryTest'`
Expected: FAIL — `cannot find symbol: class DockGeometry`.

- [ ] **Step 3: Write `DockGeometry`**

Create `jasper-app/src/main/java/dev/jasper/app/dock/DockGeometry.java`:

```java
package dev.jasper.app.dock;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Where everything goes: a pure function from bounds and a {@link DockState} to the rectangle of each showing slot,
 * the terminal column and each divider. The bottom area spans the full width under the sides and the terminal
 * column. When the bounds are too small, the bottom area gives way first, then the right side, then the left, before
 * the terminal column goes under its minimum; the sizes in the state are never changed by that squeeze.
 */
public final class DockGeometry {
    private DockGeometry() { }

    /**
     * Unscaled metrics, and the UI scale that turns them and the state's sizes into pixels.
     *
     * @param scale the UI scale factor, 1 for unscaled
     * @param divider the gap between two areas, which is also the divider's width
     * @param toolWindowMinimum the smallest a tool window gets before the terminal column gives way
     * @param terminalMinimumWidth the terminal column's width that the side panels give way to
     * @param terminalMinimumHeight the terminal column's height that the bottom area gives way to
     */
    public record Metrics(double scale, int divider, int toolWindowMinimum, int terminalMinimumWidth, int terminalMinimumHeight) {
        public static final Metrics DEFAULT = at(1);

        public Metrics {
            if (!(scale > 0)) throw new IllegalArgumentException("The scale must be positive: " + scale);
        }

        /** Jasper's metrics at a UI scale: 6px dividers, 120px tool windows, a 200 x 120 px terminal column. */
        public static Metrics at(double scale) { return new Metrics(scale, 6, 120, 200, 120); }

        public int scaled(int unscaled) { return scale == 1 ? unscaled : (int) Math.round(unscaled * scale); }

        public int unscaled(int pixels) { return scale == 1 ? pixels : (int) Math.round(pixels / scale); }
    }

    /** What dragging a divider changes. */
    public enum Handle {
        LEFT_WIDTH, RIGHT_WIDTH, BOTTOM_HEIGHT, LEFT_RATIO, RIGHT_RATIO, BOTTOM_RATIO;

        /** Whether the divider is a vertical bar, dragged left and right. */
        public boolean vertical() { return this == LEFT_WIDTH || this == RIGHT_WIDTH || this == BOTTOM_RATIO; }
    }

    /**
     * One divider.
     *
     * @param bounds where the divider is
     * @param span what it resizes: the area beside or under it, or the column or row it splits
     * @param minimum the smallest position of its leading edge (x for a vertical bar, y otherwise)
     * @param maximum the largest position of its leading edge
     */
    public record Divider(Handle handle, Rectangle bounds, Rectangle span, int minimum, int maximum) {
        public Divider {
            bounds = new Rectangle(bounds);
            span = new Rectangle(span);
            if (minimum > maximum) throw new IllegalArgumentException("Divider range is empty");
        }

        @Override public Rectangle bounds() { return new Rectangle(bounds); }

        @Override public Rectangle span() { return new Rectangle(span); }

        /** The leading edge's position now. */
        public int position() { return handle.vertical() ? bounds.x : bounds.y; }
    }

    /** One computed layout. Rectangles are copies; changing them changes nothing. */
    public record Layout(Rectangle terminal, Map<Slot, Rectangle> slots, List<Divider> dividers) {
        public Layout {
            terminal = new Rectangle(terminal);
            var copies = new EnumMap<Slot, Rectangle>(Slot.class);
            slots.forEach((slot, bounds) -> copies.put(slot, new Rectangle(bounds)));
            slots = Collections.unmodifiableMap(copies);
            dividers = List.copyOf(dividers);
        }

        @Override public Rectangle terminal() { return new Rectangle(terminal); }

        @Override public Map<Slot, Rectangle> slots() {
            var copies = new EnumMap<Slot, Rectangle>(Slot.class);
            slots.forEach((slot, bounds) -> copies.put(slot, new Rectangle(bounds)));
            return Collections.unmodifiableMap(copies);
        }

        /** Where a showing slot is. */
        public Optional<Rectangle> slot(Slot slot) { return Optional.ofNullable(slots.get(slot)).map(Rectangle::new); }

        public Optional<Divider> divider(Handle handle) {
            return dividers.stream().filter(divider -> divider.handle() == handle).findFirst();
        }
    }

    public static Layout compute(Rectangle bounds, DockState state, Metrics metrics) {
        int x = bounds.x, y = bounds.y, width = Math.max(0, bounds.width), height = Math.max(0, bounds.height);
        int gap = metrics.scaled(metrics.divider()), floor = metrics.scaled(metrics.toolWindowMinimum());
        boolean left = state.showing(Side.LEFT), right = state.showing(Side.RIGHT), bottom = state.showing(Side.BOTTOM);
        var slots = new EnumMap<Slot, Rectangle>(Slot.class);
        var dividers = new ArrayList<Divider>();

        int[] bottomHeight = {bottom ? Math.max(floor, metrics.scaled(state.size(Side.BOTTOM))) : 0};
        int upper = squeeze(height - (bottom ? gap : 0), metrics.scaled(metrics.terminalMinimumHeight()), floor, bottomHeight);
        int[] sides = {right ? Math.max(floor, metrics.scaled(state.size(Side.RIGHT))) : 0,
                       left ? Math.max(floor, metrics.scaled(state.size(Side.LEFT))) : 0};
        int terminalWidth = squeeze(width - (left ? gap : 0) - (right ? gap : 0),
            metrics.scaled(metrics.terminalMinimumWidth()), floor, sides);
        int rightWidth = sides[0], leftWidth = sides[1];
        int surplusWidth = Math.max(0, terminalWidth - metrics.scaled(metrics.terminalMinimumWidth()));
        var upperArea = new Rectangle(x, y, width, upper);

        if (left) {
            pair(new Rectangle(x, y, leftWidth, upper), state, Slot.LEFT_TOP, Handle.LEFT_RATIO, gap, floor, slots, dividers);
            int bar = x + leftWidth;
            dividers.add(divider(Handle.LEFT_WIDTH, new Rectangle(bar, y, gap, upper), upperArea, x + floor, bar + surplusWidth));
        }
        if (right) {
            int column = x + width - rightWidth, bar = column - gap;
            pair(new Rectangle(column, y, rightWidth, upper), state, Slot.RIGHT_TOP, Handle.RIGHT_RATIO, gap, floor, slots, dividers);
            dividers.add(divider(Handle.RIGHT_WIDTH, new Rectangle(bar, y, gap, upper), upperArea,
                bar - surplusWidth, x + width - floor - gap));
        }
        if (bottom) {
            int bar = y + upper;
            int surplusHeight = Math.max(0, upper - metrics.scaled(metrics.terminalMinimumHeight()));
            dividers.add(divider(Handle.BOTTOM_HEIGHT, new Rectangle(x, bar, width, gap), new Rectangle(x, y, width, height),
                bar - surplusHeight, y + height - floor - gap));
            pair(new Rectangle(x, bar + gap, width, bottomHeight[0]), state, Slot.BOTTOM_LEFT, Handle.BOTTOM_RATIO,
                gap, floor, slots, dividers);
        }
        int terminalX = x + leftWidth + (left ? gap : 0);
        return new Layout(new Rectangle(terminalX, y, terminalWidth, upper), slots, dividers);
    }

    /**
     * Applies dragging a divider: its leading edge moves to position, kept within the divider's range. A width or
     * height is stored unscaled; a ratio is the first slot's share of the split.
     */
    public static void drag(DockState state, Divider divider, int position, Metrics metrics) {
        int at = Math.clamp(position, divider.minimum(), divider.maximum());
        Rectangle span = divider.span();
        int gap = metrics.scaled(metrics.divider());
        switch (divider.handle()) {
            case LEFT_WIDTH -> state.resize(Side.LEFT, metrics.unscaled(at - span.x));
            case RIGHT_WIDTH -> state.resize(Side.RIGHT, metrics.unscaled(span.x + span.width - at - gap));
            case BOTTOM_HEIGHT -> state.resize(Side.BOTTOM, metrics.unscaled(span.y + span.height - at - gap));
            case LEFT_RATIO -> ratio(state, Side.LEFT, at - span.y, span.height - gap);
            case RIGHT_RATIO -> ratio(state, Side.RIGHT, at - span.y, span.height - gap);
            case BOTTOM_RATIO -> ratio(state, Side.BOTTOM, at - span.x, span.width - gap);
        }
    }

    private static void ratio(DockState state, Side side, int first, int total) {
        if (total > 0) state.setRatio(side, (double) first / total);
    }

    /**
     * Lays out a side's column or the bottom row: both slots split at the side's ratio around a divider, or the one
     * showing slot alone. The first slot is the upper one of a column and the left one of the row.
     */
    private static void pair(Rectangle area, DockState state, Slot first, Handle handle, int gap, int floor,
                             Map<Slot, Rectangle> slots, List<Divider> dividers) {
        Slot second = first.partner();
        boolean one = state.active(first).isPresent(), two = state.active(second).isPresent();
        if (!(one && two)) {
            slots.put(one ? first : second, new Rectangle(area));
            return;
        }
        boolean row = handle.vertical();
        int total = Math.max(0, (row ? area.width : area.height) - gap);
        int part = split(total, state.ratio(first.side()), floor);
        int start = row ? area.x : area.y, bar = start + part;
        int minimum = total >= 2 * floor ? start + floor : bar, maximum = total >= 2 * floor ? start + total - floor : bar;
        if (row) {
            slots.put(first, new Rectangle(area.x, area.y, part, area.height));
            slots.put(second, new Rectangle(bar + gap, area.y, total - part, area.height));
            dividers.add(divider(handle, new Rectangle(bar, area.y, gap, area.height), area, minimum, maximum));
        } else {
            slots.put(first, new Rectangle(area.x, area.y, area.width, part));
            slots.put(second, new Rectangle(area.x, bar + gap, area.width, total - part));
            dividers.add(divider(handle, new Rectangle(area.x, bar, area.width, gap), area, minimum, maximum));
        }
    }

    /** A divider whose range always contains where it is now, however squeezed the layout. */
    private static Divider divider(Handle handle, Rectangle bounds, Rectangle span, int minimum, int maximum) {
        int now = handle.vertical() ? bounds.x : bounds.y;
        return new Divider(handle, bounds, span, Math.min(minimum, now), Math.max(maximum, now));
    }

    /** The first part of total at ratio, at least floor on both sides of the split when there is room for that. */
    private static int split(int total, double ratio, int floor) {
        int first = (int) Math.round(total * ratio);
        return total >= 2 * floor ? Math.clamp(first, floor, total - floor) : first;
    }

    /**
     * Fits panels and a flexible part into the available length and returns the flexible part's length. The panels
     * shrink, in order, toward floor until the flexible part has its minimum; then the flexible part gives way; then
     * the panels shrink below floor, in the same order. The array holds the panels' lengths and is updated.
     */
    private static int squeeze(int available, int flexibleMinimum, int floor, int[] panels) {
        int room = Math.max(0, available);
        int excess = sum(panels) + flexibleMinimum - room;
        for (int i = 0; i < panels.length && excess > 0; i++) {
            int cut = Math.min(excess, Math.max(0, panels[i] - floor));
            panels[i] -= cut;
            excess -= cut;
        }
        excess = sum(panels) - room;
        for (int i = 0; i < panels.length && excess > 0; i++) {
            int cut = Math.min(excess, panels[i]);
            panels[i] -= cut;
            excess -= cut;
        }
        return room - sum(panels);
    }

    private static int sum(int[] values) {
        int total = 0;
        for (int value : values) total += value;
        return total;
    }
}
```

- [ ] **Step 4: Run the tests to make sure they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*'`
Expected: PASS, 25 tests (11 `DockStateTest`, 14 `DockGeometryTest`).

- [ ] **Step 5: Run the architecture gate**

Run: `./gradlew verifyApplicationArchitecture`
Expected: BUILD SUCCESSFUL (`DockGeometry` uses only `java.awt.Rectangle` from AWT).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/dock/DockGeometry.java jasper-app/src/test/java/dev/jasper/app/dock/DockGeometryTest.java
git commit -F - <<'EOF'
feat(dock): lay out the six slots with a pure geometry function

DockGeometry turns bounds and a DockState into slot, terminal and divider
rectangles: a full-width bottom area, per-side splits at their ratios, and a
squeeze order of bottom, right, left before the terminal. Dividers carry their
drag range, and drag() stores sizes unscaled.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 3: SDK 0.13.0 `PanelSpec.withSplit()`, the testkit, and slots in the contributions model

**Files:**
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelSpec.java` (whole file)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Anchor.java` (whole file)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java` (the `show()` Javadoc)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Panels.java` (type Javadoc)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Rail.java` (type Javadoc)
- Modify: `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java` (`VERSION`)
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelAndWindowValuesTest.java`
- Modify: `jasper-sdk/src/test/java/dev/jasper/sdk/{CapabilitiesTest,files/FilesValuesTest,palette/PaletteValuesTest,ui/AppearanceContractTest}.java` (version line)
- Modify: `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` (`panels()`)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/ContractHarness.java` (`panels()` Javadoc)
- Modify: `jasper-sdk-testkit/src/testFixtures/java/dev/jasper/sdk/testing/contract/PluginContractTest.java` (one new case)
- Modify: `jasper-app/src/main/java/dev/jasper/app/contributions/{Contributions,PanelEntry,package-info}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/plugins/{HostedUi,package-info}.java`
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (one line, a bridge)
- Modify: `jasper-app/src/test/java/dev/jasper/app/plugins/{AppContractTest,HostedUiTest,VersionRangeTest}.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/contributions/PanelContributionsTest.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/{WindowPanelsTest,ShortcutContextDispatchTest,StartupFocusTest}.java` (`addPanel` arguments)
- Modify: `plugins/*/src/main/resources/plugin.toml` (all seven: the `sdk` line)
- Modify: `jasper-sdk/README.md`, `docs/plugin-authoring.md` (the `sdk = …` lines)

**Interfaces:**
- Consumes: Task 1's `Slot`, `Side`, `Slot.of(Side, boolean)`.
- Produces:
  - SDK: `record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId, boolean split)`,
    explicit constructors `(id, title, icon, defaultAnchor)` and `(id, title, icon, defaultAnchor, toggleActionId)`
    (both `split = false`), `PanelSpec withSplit()`, `withToggleActionId` keeps `split`. `JasperSdk.VERSION = "0.13.0"`.
  - Testkit: `FakePluginHost.panels()` and `ContractHarness.panels()` lines `id|title|ANCHOR[+split][|actionId]`.
  - App: `Contributions.addPanel(String id, String title, Icon icon, Slot defaultSlot, Function<PanelSite, JComponent> factory)`
    and `addPanel(String, String, Icon, Slot, Optional<String> toggleActionId, Function<PanelSite, JComponent>)`;
    `PanelEntry.defaultSlot()` returns `Slot` (replaces `defaultRegion()`).
  - Bridge (deleted in Task 8): `WindowContributions` maps `entry.defaultSlot().side()` to the old `PanelRegion`.

- [ ] **Step 1: Write the failing SDK tests**

In `jasper-sdk/src/test/java/dev/jasper/sdk/ui/PanelAndWindowValuesTest.java`, add this test after `panelSpecsValidate`:

```java
    @Test void aSplitSurvivesTheToggleActionAndOlderConstructorsStillLink() throws Exception {
        var spec = new PanelSpec("dev.x.tool.files", "Files", new ImageIcon(), Anchor.LEFT);
        assertThat(spec.split()).as("the first half unless asked").isFalse();
        assertThat(new PanelSpec("dev.x.tool.files", "Files", new ImageIcon(), Anchor.LEFT,
            java.util.Optional.of("dev.x.tool.show")).split()).isFalse();
        var split = spec.withSplit();
        assertThat(split.split()).isTrue();
        assertThat(split.defaultAnchor()).isEqualTo(Anchor.LEFT);
        assertThat(split.withToggleActionId("dev.x.tool.show")).satisfies(value -> {
            assertThat(value.split()).as("withToggleActionId keeps the flag").isTrue();
            assertThat(value.toggleActionId()).hasValue("dev.x.tool.show");
        });
        assertThat(spec.withToggleActionId("dev.x.tool.show").withSplit().toggleActionId()).hasValue("dev.x.tool.show");
        // Plugins built against 0.12 and earlier call these two constructors.
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, javax.swing.Icon.class, Anchor.class)).isNotNull();
        assertThat(PanelSpec.class.getConstructor(String.class, String.class, javax.swing.Icon.class, Anchor.class,
            java.util.Optional.class)).isNotNull();
    }
```

In each of these four files change the expected version from `"0.12.0"` to `"0.13.0"` (one line each):
`jasper-sdk/src/test/java/dev/jasper/sdk/CapabilitiesTest.java:8`,
`jasper-sdk/src/test/java/dev/jasper/sdk/ui/AppearanceContractTest.java:21`,
`jasper-sdk/src/test/java/dev/jasper/sdk/files/FilesValuesTest.java:54`,
`jasper-sdk/src/test/java/dev/jasper/sdk/palette/PaletteValuesTest.java:70`:

```java
        assertThat(JasperSdk.VERSION).isEqualTo("0.13.0");
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :jasper-sdk:test`
Expected: FAIL — `cannot find symbol: method split()` / `withSplit()`.

- [ ] **Step 3: Implement the SDK change**

Replace `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelSpec.java` with:

```java
package dev.jasper.sdk.ui;

import java.util.Objects;
import java.util.Optional;
import java.util.regex.Pattern;
import javax.swing.Icon;

/**
 * A panel as the stripes show it: an icon on a tool window stripe and, while it shows, a tool window with a header.
 *
 * @param id namespaced id that starts with the plugin's id and a dot; unless {@code toggleActionId}
 *           names one, the application also registers the action {@code <id>.toggle}, which users
 *           may bind under {@code [keybindings]}
 * @param title non-blank title, used for the stripe tooltip, the tool window's header and the application's toggle action
 * @param icon stripe icon, normally from {@link Appearance#icon}
 * @param defaultAnchor the side used until the user moves the panel
 * @param toggleActionId one of the plugin's own actions, already registered, that toggles this panel:
 *                       View &gt; Tools lists it, with its shortcut, instead of an application-made
 *                       {@code <id>.toggle} (since 0.8.1)
 * @param split whether the panel starts in its side's secondary half: the lower half of the left or right side, or
 *              the right half of the bottom area (since 0.13.0)
 */
public record PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId, boolean split) {
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_-]*(\\.[a-z0-9_-]+)+");

    /** Validates every component. */
    public PanelSpec {
        if (id == null || id.length() > 120 || !ID.matcher(id).matches())
            throw new IllegalArgumentException("Not a namespaced panel id: " + id);
        if (title == null || title.isBlank()) throw new IllegalArgumentException("A panel needs a title");
        Objects.requireNonNull(icon, "icon");
        Objects.requireNonNull(defaultAnchor, "defaultAnchor");
        Objects.requireNonNull(toggleActionId, "toggleActionId");
    }

    /**
     * A panel toggled by the application's own {@code <id>.toggle} action, in its side's first half.
     *
     * @param id the panel id
     * @param title the title
     * @param icon the stripe icon
     * @param defaultAnchor the starting side
     */
    public PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor) {
        this(id, title, icon, defaultAnchor, Optional.empty(), false);
    }

    /**
     * A panel in its side's first half. Before 0.13.0 this was the canonical constructor; it stays so that plugins
     * built against earlier SDKs still link.
     *
     * @param id the panel id
     * @param title the title
     * @param icon the stripe icon
     * @param defaultAnchor the starting side
     * @param toggleActionId the plugin's own toggle action, or empty for the application's {@code <id>.toggle}
     */
    public PanelSpec(String id, String title, Icon icon, Anchor defaultAnchor, Optional<String> toggleActionId) {
        this(id, title, icon, defaultAnchor, toggleActionId, false);
    }

    /**
     * Derives a value. Name an action when the plugin already has one that toggles this panel, so the
     * View menu and the palette offer it once.
     *
     * @param value the plugin's toggle action, or null for the application's {@code <id>.toggle}
     * @return a copy naming it, with the same {@code split}
     * @since 0.8.1
     */
    public PanelSpec withToggleActionId(String value) {
        return new PanelSpec(id, title, icon, defaultAnchor, Optional.ofNullable(value), split);
    }

    /**
     * Derives a value that starts in its side's secondary half: under the first half's tool window on the left or
     * right side, or to its right in the bottom area. A place the user chose still wins.
     *
     * @return a copy with {@code split} set
     * @since 0.13.0
     */
    public PanelSpec withSplit() {
        return new PanelSpec(id, title, icon, defaultAnchor, toggleActionId, true);
    }
}
```

Replace `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Anchor.java` with (constants unchanged, Javadoc only):

```java
package dev.jasper.sdk.ui;

/**
 * Where a panel appears by default; the user may move it. A side has two halves: a panel starts in the first unless
 * its spec says {@link PanelSpec#withSplit()}.
 */
public enum Anchor {
    /** The left side, beside the left stripe: its upper half, or with a split its lower half. */
    LEFT,
    /** The right side, beside the right stripe: its upper half, or with a split its lower half. */
    RIGHT,
    /** The bottom area, the window's full width under the sides and the terminal: its left half, or with a split its right half. */
    BOTTOM
}
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/PanelHost.java` replace the `show()` Javadoc line:

```java
    /** Shows the panel in this window, hiding whatever else occupied its slot. */
    void show();
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Panels.java` replace the type Javadoc line:

```java
/** Tool window panels. Each registered panel gets a stripe icon in every window. */
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/ui/Rail.java` replace the type Javadoc line:

```java
/** Plain action buttons on the left stripe, after the panel icons: for example a button that opens a plugin window. */
```

In `jasper-sdk/src/main/java/dev/jasper/sdk/JasperSdk.java`:

```java
    public static final String VERSION = "0.13.0";
```

- [ ] **Step 4: Run the SDK tests and its JavaDoc**

Run: `./gradlew :jasper-sdk:test :jasper-sdk:javadoc`
Expected: PASS; doclint reports nothing.

- [ ] **Step 5: Teach the testkit the flag and add the contract case**

In `jasper-sdk-testkit/src/main/java/dev/jasper/sdk/testing/FakePluginHost.java` replace `panels()` and its Javadoc:

```java
    /**
     * Registered panels, registration order.
     *
     * @return lines of the form {@code id|title|LEFT|RIGHT|BOTTOM} (the default anchor, with {@code +split} when the
     *         panel starts in its side's secondary half), followed by {@code |actionId} when the panel names its own
     *         toggle action
     */
    public List<String> panels() {
        List<String> lines = new ArrayList<>();
        for (FakePluginContext context : contexts.values())
            for (FakeUi.Panel panel : context.ui.panels) lines.add(panel.spec.id() + "|" + panel.spec.title() + "|" + panel.spec.defaultAnchor()
                + (panel.spec.split() ? "+split" : "") + panel.spec.toggleActionId().map(toggle -> "|" + toggle).orElse(""));
        return lines;
    }
```

In `ContractHarness.java` replace the `panels()` Javadoc:

```java
    /**
     * Registered panels as {@code id|title|LEFT|RIGHT|BOTTOM} (the default anchor, with {@code +split} when the panel
     * starts in its side's secondary half), followed by {@code |actionId} when the panel names its own toggle action;
     * registration order.
     */
    List<String> panels();
```

In `PluginContractTest.java`, add after `aPanelMayNameOneOfThePluginsOwnActionsAsItsToggle`:

```java
    @Test void aSplitPanelKeepsItsSplitBesideItsToggleAndStartsInTheSecondaryHalf() {
        h.start(info("test.alpha"), Set.of(), Set.of(), context -> {
            context.actions().register(ActionSpec.of("test.alpha.show", "Toggle Files"), invoked -> { });
            context.panels().register(new PanelSpec("test.alpha.files", "Files", new ImageIcon(), Anchor.LEFT).withSplit()
                .withToggleActionId("test.alpha.show"), host -> new JLabel());
            context.panels().register(new PanelSpec("test.alpha.log", "Log", new ImageIcon(), Anchor.BOTTOM).withSplit(),
                host -> new JLabel());
            context.panels().register(new PanelSpec("test.alpha.hosts", "Hosts", new ImageIcon(), Anchor.RIGHT), host -> new JLabel());
        });
        assertThat(h.panels()).containsExactly("test.alpha.files|Files|LEFT+split|test.alpha.show",
            "test.alpha.log|Log|BOTTOM+split", "test.alpha.hosts|Hosts|RIGHT");
    }
```

Run: `./gradlew :jasper-sdk-testkit:test`
Expected: PASS (the fake runs the new case through `FakeContractTest`).

- [ ] **Step 6: Write the failing app tests**

In `jasper-app/src/test/java/dev/jasper/app/plugins/HostedUiTest.java`, replace the body of
`panelsAndRailReachTheModelWithContainedFactories` from `var entry = model.panels().get(0);` through
`assertThat(model.panels()).hasSize(1);` with:

```java
        ui.panels().register(new dev.jasper.sdk.ui.PanelSpec("dev.x.tool.files", "Files", new javax.swing.ImageIcon(),
            dev.jasper.sdk.ui.Anchor.BOTTOM).withSplit(), host -> new javax.swing.JLabel("files"));

        var entry = model.panels().get(0);
        assertThat(entry.defaultSlot()).isEqualTo(dev.jasper.app.dock.Slot.RIGHT_TOP);
        assertThat(model.panels().get(1).defaultSlot()).isEqualTo(dev.jasper.app.dock.Slot.LEFT_TOP);
        assertThat(model.panels().get(2).defaultSlot()).as("a split panel starts in its side's secondary half")
            .isEqualTo(dev.jasper.app.dock.Slot.BOTTOM_RIGHT);
        UUID window = UUID.randomUUID();
        boolean[] visible = {true};
        var site = new dev.jasper.app.contributions.PanelSite(window, () -> visible[0] = true, () -> visible[0] = false, () -> visible[0]);
        assertThat(entry.factory().apply(site)).isInstanceOf(javax.swing.JLabel.class);
        assertThat(hosts).singleElement().satisfies(host -> {
            assertThat(host.window().id()).isEqualTo(window);
            host.hide();
            assertThat(host.visible()).isFalse();
        });
        assertThat(model.panels().get(1).factory().apply(site)).as("a failed factory yields no component").isNull();
        assertThat(containment.failures("dev.x.tool")).isEqualTo(1);

        ui.rail().add("dev.x.tool.open");
        assertThatIllegalArgumentException().isThrownBy(() -> ui.rail().add("new_tab"));
        assertThat(model.railActions()).containsExactly("dev.x.tool.open");
        var requests = new ArrayList<dev.jasper.app.contributions.Contributions.PanelRequest>();
        model.onPanelRequest(requests::add);
        ui.panels().toggle("dev.x.tool.hosts", hosts.getFirst().window());
        assertThat(requests).containsExactly(new dev.jasper.app.contributions.Contributions.PanelRequest(window, "dev.x.tool.hosts", dev.jasper.app.contributions.Contributions.PanelRequest.Op.TOGGLE));
        panel.close();
        assertThat(model.panels()).hasSize(2);
```

(The lines after it, `ui.closeAll();` and the two `isEmpty()` assertions, stay.)

In `jasper-app/src/test/java/dev/jasper/app/plugins/AppContractTest.java` replace the `panels()` override:

```java
            @Override public List<String> panels() {
                return onEdtValue(() -> contributions.panels().stream()
                    .map(panel -> panel.id() + "|" + panel.title() + "|" + panel.defaultSlot().side()
                        + (panel.defaultSlot().split() ? "+split" : "")
                        + (panel.namedToggle() ? "|" + panel.toggleActionId() : "")).toList());
            }
```

The app harness now prints the slot it chose: `LEFT+split` is `Slot.LEFT_BOTTOM`, so the new contract case checks
that the app places a split panel in the secondary slot.

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL — `cannot find symbol: method defaultSlot()`.

- [ ] **Step 7: Put slots into the contributions model**

In `jasper-app/src/main/java/dev/jasper/app/contributions/PanelEntry.java`: add `import dev.jasper.app.dock.Slot;`
after the `package` line and replace the `defaultRegion` field, constructor parameter and accessor. The changed
members read:

```java
    private final Slot defaultSlot;
```

```java
    PanelEntry(Contributions owner, String id, String title, Icon icon, Slot defaultSlot,
               String toggleActionId, boolean namedToggle, Function<PanelSite, JComponent> factory) {
        this.owner = owner; this.id = id; this.title = title; this.icon = icon;
        this.defaultSlot = defaultSlot; this.factory = factory;
        this.toggleActionId = toggleActionId; this.namedToggle = namedToggle;
    }
```

```java
    /** Where the panel docks until the user moves it. */
    public Slot defaultSlot() { return defaultSlot; }
```

In `Contributions.java`, add `import dev.jasper.app.dock.Slot;` and replace the two `addPanel` methods:

```java
    public PanelEntry addPanel(String id, String title, Icon icon, Slot defaultSlot,
                               Function<PanelSite, JComponent> factory) {
        return addPanel(id, title, icon, defaultSlot, Optional.empty(), factory);
    }

    /** A panel toggled by {@code toggleActionId} when present; otherwise the model registers {@code <id>.toggle}. */
    public PanelEntry addPanel(String id, String title, Icon icon, Slot defaultSlot, Optional<String> toggleActionId,
                               Function<PanelSite, JComponent> factory) {
        requireEdt();
        Objects.requireNonNull(factory, "factory");
        if (panels.containsKey(id)) throw new IllegalArgumentException("Panel already registered: " + id);
        String toggle = toggleActionId.orElse(id + ".toggle");
        var entry = new PanelEntry(this, id, title, icon, Objects.requireNonNull(defaultSlot, "defaultSlot"),
            toggle, toggleActionId.isPresent(), factory);
        panels.put(id, entry);
        if (toggleActionId.isEmpty())
            panelToggles.put(id, addAction(toggle, "Toggle " + title, icon, List.of("panel", "show", "hide"), Optional.empty(),
                invocation -> requestPanel(new PanelRequest(invocation.windowId(), id, PanelRequest.Op.TOGGLE))));
        rebuildPanelsMenu();
        changed(Kind.PANELS);
        return entry;
    }
```

(`MenuTarget.Slot.VIEW` in `rebuildPanelsMenu` stays fully qualified, so the imported `Slot` does not clash with it.)

In `contributions/package-info.java` replace the allowed-dependencies line:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.dock, dev.jasper.app.files, dev.jasper.app.lifecycle, dev.jasper.app.palette.
```

In `plugins/HostedUi.java`, replace `import dev.jasper.app.contributions.PanelRegion;` with
`import dev.jasper.app.dock.Slot;` and the `model.addPanel(…)` call in `panels().register` with:

```java
            // Anchor plus split is IntelliJ's anchor plus split: one of the six slots. The SDK's own Side is imported here.
            var entry = model.addPanel(spec.id(), spec.title(), spec.icon(),
                Slot.of(dev.jasper.app.dock.Side.valueOf(spec.defaultAnchor().name()), spec.split()),
                spec.toggleActionId(), site -> {
                JComponent[] built = {null};
                containment.run(pluginId, "panel " + spec.id(), () -> built[0] = factory.create(host(site)));
                return built[0];
            });
```

In `plugins/package-info.java`, add `dev.jasper.app.dock` to the allowed list right after `dev.jasper.app.contributions`:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commands, dev.jasper.app.contributions, dev.jasper.app.dock, dev.jasper.app.files, dev.jasper.app.launch, dev.jasper.app.notifications, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.config, dev.jasper.app.settings, dev.jasper.app.lifecycle, dev.jasper.app.settingsui, dev.jasper.app.terminals, dev.jasper.app.windows, dev.jasper.sdk, dev.jasper.sdk.activity, dev.jasper.sdk.events, dev.jasper.sdk.files, dev.jasper.sdk.plugin, dev.jasper.sdk.services, dev.jasper.sdk.settings, dev.jasper.sdk.terminal, dev.jasper.sdk.ui, dev.jasper.terminal.session.
```

In `workspace/WindowContributions.java`, `syncPanels` restores the region from the slot's side. Replace the
`panel.region = …` line with this bridge (Task 8 deletes it with `PanelRegion`):

```java
            panel.region = saved.map(value -> PanelRegion.valueOf(value.region()))
                .orElse(PanelRegion.valueOf(entry.defaultSlot().side().name()));
```

- [ ] **Step 8: Move the app tests' `addPanel` calls to slots**

Run this from the worktree root. It rewrites only `addPanel` arguments (`PanelRegion.X, site` and
`PanelRegion.X, Optional`), which no other call uses, and swaps the import where `PanelRegion` is no longer used:

```bash
python3 - <<'PY'
import pathlib, re
root = pathlib.Path("jasper-app/src/test/java/dev/jasper/app")
names = ["contributions/PanelContributionsTest.java", "workspace/WindowPanelsTest.java",
         "workspace/ShortcutContextDispatchTest.java", "workspace/StartupFocusTest.java"]
slots = {"LEFT": "Slot.LEFT_TOP", "RIGHT": "Slot.RIGHT_TOP", "BOTTOM": "Slot.BOTTOM_LEFT"}
for name in names:
    path = root / name
    text = path.read_text(encoding="utf-8")
    text = re.sub(r"PanelRegion\.(LEFT|RIGHT|BOTTOM), (site|Optional)", lambda m: slots[m.group(1)] + ", " + m.group(2), text)
    if "PanelRegion" in text.replace("import dev.jasper.app.contributions.PanelRegion;", ""):
        text = text.replace("import dev.jasper.app.contributions.PanelRegion;",
                            "import dev.jasper.app.contributions.PanelRegion;\nimport dev.jasper.app.dock.Slot;")
    else:
        text = text.replace("import dev.jasper.app.contributions.PanelRegion;", "import dev.jasper.app.dock.Slot;")
    if "import dev.jasper.app.dock.Slot;" not in text:
        text = text.replace("\nimport ", "\nimport dev.jasper.app.dock.Slot;\nimport ", 1)
    path.write_text(text, encoding="utf-8")
    print(name, len(re.findall(r"\bSlot\.(LEFT_TOP|RIGHT_TOP|BOTTOM_LEFT)", text)))
PY
```

Expected output: four lines with counts 4, 9, 2 and 1. `WindowPanelsTest` keeps `PanelRegion` for its
`owner.regions()` calls until Task 8.

- [ ] **Step 9: Move every bundled manifest to SDK 0.13**

```bash
for f in plugins/*/src/main/resources/plugin.toml; do
  sed -i '' 's/^sdk = ">=0.12.0, <0.13"$/sdk = ">=0.13.0, <0.14"/' "$f"
done
grep -h '^sdk' plugins/*/src/main/resources/plugin.toml | sort | uniq -c
```

Expected: `7 sdk = ">=0.13.0, <0.14"`. (On Linux use `sed -i` without `''`.)

In `jasper-app/src/test/java/dev/jasper/app/plugins/VersionRangeTest.java` replace the last test:

```java
    @Test void pluginsBuiltForOlderMinorsAreIncompatibleWithZeroThirteen() {
        var host = Version.parse(dev.jasper.sdk.JasperSdk.VERSION);
        assertThat(VersionRange.parse(">=0.10.1, <0.11").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.11.0, <0.12").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.12.0, <0.13").contains(host)).isFalse();
        assertThat(VersionRange.parse(">=0.13.0, <0.14").contains(host)).isTrue();
    }
```

In `jasper-sdk/README.md` change `Plugins declare \`sdk = ">=0.12.0, <0.13"\`.` to
`Plugins declare \`sdk = ">=0.13.0, <0.14"\`.`, and in `docs/plugin-authoring.md` change the manifest example line
`sdk = ">=0.12.0, <0.13"` to `sdk = ">=0.13.0, <0.14"`.

- [ ] **Step 10: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL. The contract suite passes for the fake and for the app, including the new split case.
Plugin tests that list panels are unchanged (no bundled panel is split yet).

- [ ] **Step 11: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-sdk jasper-sdk-testkit jasper-app plugins docs/plugin-authoring.md
git commit -F - <<'EOF'
feat(sdk)!: SDK 0.13.0 adds PanelSpec.withSplit and the app docks by slot

PanelSpec gains a split component, split() and withSplit(); the four- and
five-argument constructors stay so older plugins still link, and
withToggleActionId keeps the flag. The contributions model carries a
dock.Slot (Anchor plus split) instead of a region; the testkit and the
contract harness print a split default as LEFT+split. Every bundled
manifest moves to sdk >=0.13.0, <0.14.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 4: `ui-state.toml` version 2 with migration from version 1

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/persistence/package-info.java` (allowed dependencies)
- Modify: `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (bridge; deleted in Task 8)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java` (one assertion)

**Interfaces:**
- Consumes: Task 1's `Slot`, `Side`, `DockState` constants.
- Produces (package `dev.jasper.app.persistence`):
  - `record UiState.Panel(Slot slot, int order, boolean visible)` with `static final int LAST = 9999` (no remembered
    position); `order` must be 0..`LAST`.
  - `record UiState.Dock(int leftWidth, int rightWidth, int bottomHeight, double leftRatio, double rightRatio, double bottomRatio)`
    with `static final Dock DEFAULT` (260, 260, 200, 0.5, 0.5, 0.5); sizes 120..4000, ratios 0.1..0.9.
  - `UiState.panels()` (every remembered panel, sorted by id, unmodifiable), `UiState.dock()`, `UiState.setDock(Dock)`;
    `panel(id)`, `putPanel(id, Panel)`, `window`, `putWindow`, `railVisible`, `setRailVisible`, `chooser`,
    `setChooser`, `save()`, `load(Path)`, `inMemory()` as before.

- [ ] **Step 1: Write the failing test**

Replace `jasper-app/src/test/java/dev/jasper/app/persistence/UiStateTest.java` with:

```java
package dev.jasper.app.persistence;

import dev.jasper.app.dock.Slot;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.*;

class UiStateTest {
    @TempDir Path dir;

    @Test void roundTripsTheDockPanelsWindowsAndTheRail() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isTrue();
        assertThat(state.dock()).isEqualTo(UiState.Dock.DEFAULT);
        assertThat(state.panel("dev.x.hosts")).isEmpty();
        state.putPanel("dev.x.hosts", new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        state.putPanel("dev.x.files", new UiState.Panel(Slot.LEFT_BOTTOM, 1, false));
        state.setDock(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        state.putWindow("dev.x.manager", new UiState.Bounds(-20, 40, 800, 600));
        state.setRailVisible(false);
        state.save();
        assertThat(Files.readString(file)).isEqualTo("""
            version = 2
            rail_visible = false

            [dock]
            left_width = 300
            right_width = 280
            bottom_height = 180
            left_ratio = 0.25
            right_ratio = 0.5
            bottom_ratio = 0.75

            [panels."dev.x.files"]
            slot = "LEFT_BOTTOM"
            order = 1
            visible = false

            [panels."dev.x.hosts"]
            slot = "RIGHT_TOP"
            order = 0
            visible = true

            [windows."dev.x.manager"]
            x = -20
            y = 40
            width = 800
            height = 600
            """);
        UiState again = UiState.load(file);
        assertThat(again.railVisible()).isFalse();
        assertThat(again.dock()).isEqualTo(new UiState.Dock(300, 280, 180, 0.25, 0.5, 0.75));
        assertThat(again.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, 0, true));
        assertThat(again.panels()).containsOnlyKeys("dev.x.files", "dev.x.hosts");
        assertThat(again.window("dev.x.manager")).hasValue(new UiState.Bounds(-20, 40, 800, 600));
    }

    @Test void aVersionOneFileMigratesRegionsSizesAndVisibilityAndKeepsTheOtherSections() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            rail_visible = true

            [panels."dev.a.sessions"]
            region = "LEFT"
            visible = true
            size = 300

            [panels."dev.b.tunnels"]
            region = "LEFT"
            visible = false
            size = 250

            [panels."dev.c.files"]
            region = "RIGHT"
            visible = false
            size = 50

            [panels."dev.d.log"]
            region = "BOTTOM"
            visible = true
            size = 150

            [panels."dev.e.other"]
            region = "BOTTOM"
            visible = true
            size = 400

            [panels."dev.f.odd"]
            region = "TOP"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = 10
            y = 20
            width = 640
            height = 480

            [chooser]
            recent = ["/work"]
            pinned = []
            preview = true
            show_hidden = false
            """);
        UiState state = UiState.load(file);
        assertThat(state.panel("dev.a.sessions")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, true));
        assertThat(state.panel("dev.b.tunnels")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.c.files")).hasValue(new UiState.Panel(Slot.RIGHT_TOP, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.d.log")).hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, UiState.Panel.LAST, true));
        assertThat(state.panel("dev.e.other")).as("the first visible panel of a region wins")
            .hasValue(new UiState.Panel(Slot.BOTTOM_LEFT, UiState.Panel.LAST, false));
        assertThat(state.panel("dev.f.odd")).as("an unknown region drops that entry only").isEmpty();
        assertThat(state.dock()).as("sizes come from the visible panel, else the first, clamped to the limits")
            .isEqualTo(new UiState.Dock(300, 120, 150, 0.5, 0.5, 0.5));
        assertThat(state.window("dev.x.manager")).hasValue(new UiState.Bounds(10, 20, 640, 480));
        assertThat(state.chooser()).isEqualTo(new UiState.Chooser(List.of("/work"), List.of(), true, false));

        state.save();
        assertThat(Files.readString(file)).startsWith("version = 2\n").contains("[windows.\"dev.x.manager\"]", "[chooser]")
            .doesNotContain("region", "size =");
        assertThat(UiState.load(file).panel("dev.a.sessions")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, true));
    }

    @Test void aBadVersionTwoEntryOrKeyFallsBackAlone() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 2
            rail_visible = false

            [dock]
            left_width = 5
            right_width = 300
            bottom_height = "tall"
            left_ratio = 0.95
            right_ratio = 0.3
            bottom_ratio = 1

            [panels."dev.x.good"]
            slot = "BOTTOM_RIGHT"
            order = 2
            visible = true

            [panels."dev.x.unknown"]
            slot = "MIDDLE"
            order = 0
            visible = true

            [panels."dev.x.disordered"]
            slot = "LEFT_TOP"
            order = -3
            visible = "yes"
            """);
        UiState state = UiState.load(file);
        assertThat(state.railVisible()).isFalse();
        assertThat(state.dock()).isEqualTo(new UiState.Dock(260, 300, 200, 0.5, 0.3, 0.5));
        assertThat(state.panel("dev.x.good")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 2, true));
        assertThat(state.panel("dev.x.unknown")).isEmpty();
        assertThat(state.panel("dev.x.disordered")).hasValue(new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST, false));
    }

    @Test void invalidValuesAreRejectedAndInvalidFilesFallBackToDefaults() throws Exception {
        assertThatNullPointerException().isThrownBy(() -> new UiState.Panel(null, 0, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, -1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Panel(Slot.LEFT_TOP, UiState.Panel.LAST + 1, true));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(100, 260, 200, 0.5, 0.5, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Dock(260, 260, 200, 0.5, Double.NaN, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> new UiState.Bounds(0, 0, 0, 10));
        Path file = dir.resolve("ui-state.toml");
        for (String bad : new String[]{"not toml = = =", "version = 3\n", "rail_visible = false\n",
                "version = 2\nrail_visible = false\n[windows.\"a.b\"]\nx = \"left\"\n"}) {
            Files.writeString(file, bad);
            UiState state = UiState.load(file);
            assertThat(state.railVisible()).as(bad).isTrue();
            assertThat(state.dock()).as(bad).isEqualTo(UiState.Dock.DEFAULT);
            assertThat(state.panels()).as(bad).isEmpty();
        }
    }

    @Test void inMemoryStateNeverTouchesDisk() {
        UiState state = UiState.inMemory();
        state.putPanel("dev.x.hosts", new UiState.Panel(Slot.LEFT_TOP, 0, false));
        assertThatCode(state::save).doesNotThrowAnyException();
        assertThat(state.panel("dev.x.hosts")).isPresent();
    }

    @Test void remembersTheFileChoosersFoldersAndToggles() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        UiState state = UiState.load(file);
        assertThat(state.chooser()).isEqualTo(UiState.Chooser.DEFAULT);
        state.setChooser(new UiState.Chooser(List.of("/work/a \"quoted\"", "C:\\Users\\me"), List.of("/pinned\ttab"), true, false));
        state.save();
        assertThat(Files.readString(file)).contains("[chooser]",
            "recent = [\"/work/a \\\"quoted\\\"\", \"C:\\\\Users\\\\me\"]", "pinned = [\"/pinned\\u0009tab\"]",
            "preview = true", "show_hidden = false");
        assertThat(UiState.load(file).chooser()).isEqualTo(state.chooser());
    }

    @Test void unreadableChooserValuesFallBackOneKeyAtATime() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, "version = 2\n[chooser]\nrecent = [1, \"/ok\"]\npinned = \"not a list\"\npreview = \"yes\"\n");
        assertThat(UiState.load(file).chooser()).isEqualTo(new UiState.Chooser(List.of("/ok"), List.of(), false, true));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.UiStateTest'`
Expected: FAIL — compilation errors (`UiState.Dock` does not exist; `Panel(Slot, int, boolean)` has no such constructor).

- [ ] **Step 3: Write version 2**

Replace `jasper-app/src/main/java/dev/jasper/app/persistence/UiState.java` with:

```java
package dev.jasper.app.persistence;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

/**
 * Application-owned layout state: where each panel docks, the dock's sizes, whether the stripes show, and where
 * auxiliary windows were. It is not user configuration. Caller-thread-confined. A bad panel entry or dock key falls
 * back alone; an unreadable file or an unknown version falls back to defaults rather than blocking startup, and
 * failed writes are only logged. Version 1 files (a region and a size per panel) are migrated on read; saving writes
 * version 2.
 */
public final class UiState {
    /**
     * One panel's remembered place.
     *
     * @param slot where it docks
     * @param order its position in the slot's stripe group; {@link #LAST} when it has none yet
     * @param visible whether it showed
     */
    public record Panel(Slot slot, int order, boolean visible) {
        /** The order of a panel without a remembered position: it goes after the others, in registration order. */
        public static final int LAST = 9999;

        public Panel {
            Objects.requireNonNull(slot, "slot");
            if (order < 0 || order > LAST) throw new IllegalArgumentException("Panel order must be 0 to " + LAST + ": " + order);
        }
    }

    /** The dock's remembered sizes, in unscaled pixels, and split ratios. */
    public record Dock(int leftWidth, int rightWidth, int bottomHeight, double leftRatio, double rightRatio, double bottomRatio) {
        public static final Dock DEFAULT = new Dock(DockState.DEFAULT_SIDE, DockState.DEFAULT_SIDE, DockState.DEFAULT_BOTTOM,
            DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO);

        public Dock {
            for (int size : new int[]{leftWidth, rightWidth, bottomHeight})
                if (!size(size)) throw new IllegalArgumentException("Dock size must be " + DockState.MIN_SIZE + " to " + DockState.MAX_SIZE + ": " + size);
            for (double ratio : new double[]{leftRatio, rightRatio, bottomRatio})
                if (!ratio(ratio)) throw new IllegalArgumentException("Dock ratio must be " + DockState.MIN_RATIO + " to " + DockState.MAX_RATIO + ": " + ratio);
        }

        static boolean size(long value) { return value >= DockState.MIN_SIZE && value <= DockState.MAX_SIZE; }

        static boolean ratio(double value) { return value >= DockState.MIN_RATIO && value <= DockState.MAX_RATIO; }
    }

    /** One auxiliary window's remembered bounds; the position may be negative on multi-display setups. */
    public record Bounds(int x, int y, int width, int height) {
        public Bounds {
            if (width <= 0 || height <= 0) throw new IllegalArgumentException("Window bounds need a positive size");
        }
    }

    /** The file chooser's remembered folders (absolute paths; recent is newest first) and its two toggles. */
    public record Chooser(List<String> recent, List<String> pinned, boolean preview, boolean showHidden) {
        public static final Chooser DEFAULT = new Chooser(List.of(), List.of(), false, true);
        public Chooser { recent = List.copyOf(recent); pinned = List.copyOf(pinned); }
    }

    private static final System.Logger LOG = System.getLogger(UiState.class.getName());
    private static final Pattern ID = Pattern.compile("[a-z][a-z0-9_.-]{0,127}");
    private static final int MAX_BYTES = 256 * 1024;
    private final Path file;
    private final Map<String, Panel> panels = new TreeMap<>();
    private final Map<String, Bounds> windows = new TreeMap<>();
    private boolean railVisible = true;
    private Dock dock = Dock.DEFAULT;
    private static final int MAX_CHOOSER_PATHS = 50;
    private Chooser chooser = Chooser.DEFAULT;

    private UiState(Path file) { this.file = file; }

    /** State that lives only for this process: tests, and windows that were never connected to a file. */
    public static UiState inMemory() { return new UiState(null); }

    public static UiState load(Path file) {
        UiState state = new UiState(file);
        try {
            Optional<String> text = TomlStateFile.readBounded(file, MAX_BYTES, "UI state");
            if (text.isPresent()) state.parse(text.get());
        } catch (IOException | RuntimeException invalid) {
            LOG.log(System.Logger.Level.WARNING, "Ignoring unreadable UI state " + file, invalid);
            state.panels.clear(); state.windows.clear(); state.railVisible = true; state.dock = Dock.DEFAULT;
            state.chooser = Chooser.DEFAULT;
        }
        return state;
    }

    private void parse(String text) throws IOException {
        TomlParseResult toml = Toml.parse(text);
        if (toml.hasErrors()) throw new IOException("Invalid UI state format");
        Object version = toml.get(List.of("version"));
        if (Long.valueOf(1).equals(version)) migrate(toml);
        else if (Long.valueOf(2).equals(version)) readDock(toml);
        else throw new IOException("Unknown UI state version: " + version);
        if (toml.get(List.of("rail_visible")) instanceof Boolean visible) railVisible = visible;
        if (toml.get(List.of("windows")) instanceof TomlTable table)
            for (String id : table.keySet())
                if (ID.matcher(id).matches() && table.get(List.of(id)) instanceof TomlTable entry)
                    windows.put(id, new Bounds(number(entry, "x"), number(entry, "y"), number(entry, "width"), number(entry, "height")));
        if (toml.get(List.of("chooser")) instanceof TomlTable table)
            chooser = new Chooser(strings(table, "recent"), strings(table, "pinned"),
                Boolean.TRUE.equals(table.get(List.of("preview"))), !Boolean.FALSE.equals(table.get(List.of("show_hidden"))));
    }

    /** Version 2: a {@code [dock]} table and one entry per panel; a bad key or entry falls back alone. */
    private void readDock(TomlParseResult toml) {
        if (toml.get(List.of("dock")) instanceof TomlTable table)
            dock = new Dock(size(table, "left_width", Dock.DEFAULT.leftWidth()), size(table, "right_width", Dock.DEFAULT.rightWidth()),
                size(table, "bottom_height", Dock.DEFAULT.bottomHeight()), ratio(table, "left_ratio", Dock.DEFAULT.leftRatio()),
                ratio(table, "right_ratio", Dock.DEFAULT.rightRatio()), ratio(table, "bottom_ratio", Dock.DEFAULT.bottomRatio()));
        if (!(toml.get(List.of("panels")) instanceof TomlTable table)) return;
        for (String id : table.keySet()) {
            if (!ID.matcher(id).matches() || !(table.get(List.of(id)) instanceof TomlTable entry)) continue;
            Optional<Slot> slot = slot(entry.get(List.of("slot")));
            if (slot.isEmpty()) continue;
            int order = entry.get(List.of("order")) instanceof Long value && value >= 0 && value <= Panel.LAST
                ? value.intValue() : Panel.LAST;
            panels.put(id, new Panel(slot.get(), order, Boolean.TRUE.equals(entry.get(List.of("visible")))));
        }
    }

    /**
     * Version 1 kept a region, a visibility and a size per panel. A region becomes its side's first slot, visibility
     * carries over (the first visible panel of a region wins), and each side's size comes from the panel that showed
     * there, else from the first panel there. Order follows registration.
     */
    private void migrate(TomlParseResult toml) {
        if (!(toml.get(List.of("panels")) instanceof TomlTable table)) return;
        var first = new EnumMap<Side, Integer>(Side.class);
        var shown = new EnumMap<Side, Integer>(Side.class);
        var taken = EnumSet.noneOf(Side.class);
        for (String id : new TreeSet<>(table.keySet())) {
            if (!ID.matcher(id).matches() || !(table.get(List.of(id)) instanceof TomlTable entry)) continue;
            Optional<Side> side = side(entry.get(List.of("region")));
            if (side.isEmpty()) continue;
            boolean visible = Boolean.TRUE.equals(entry.get(List.of("visible"))) && taken.add(side.get());
            if (entry.get(List.of("size")) instanceof Long size) {
                int clamped = Math.clamp(size, DockState.MIN_SIZE, DockState.MAX_SIZE);
                first.putIfAbsent(side.get(), clamped);
                if (visible) shown.put(side.get(), clamped);
            }
            panels.put(id, new Panel(Slot.of(side.get(), false), Panel.LAST, visible));
        }
        dock = new Dock(migrated(Side.LEFT, shown, first, DockState.DEFAULT_SIDE), migrated(Side.RIGHT, shown, first, DockState.DEFAULT_SIDE),
            migrated(Side.BOTTOM, shown, first, DockState.DEFAULT_BOTTOM), DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO, DockState.DEFAULT_RATIO);
    }

    private static int migrated(Side side, Map<Side, Integer> shown, Map<Side, Integer> first, int fallback) {
        return shown.getOrDefault(side, first.getOrDefault(side, fallback));
    }

    private static Optional<Slot> slot(Object value) {
        for (Slot slot : Slot.values()) if (slot.name().equals(value)) return Optional.of(slot);
        return Optional.empty();
    }

    private static Optional<Side> side(Object value) {
        for (Side side : Side.values()) if (side.name().equals(value)) return Optional.of(side);
        return Optional.empty();
    }

    private static int size(TomlTable table, String key, int fallback) {
        return table.get(List.of(key)) instanceof Long value && Dock.size(value) ? value.intValue() : fallback;
    }

    private static double ratio(TomlTable table, String key, double fallback) {
        return table.get(List.of(key)) instanceof Double value && Dock.ratio(value) ? value : fallback;
    }

    private static int number(TomlTable table, String key) throws IOException {
        if (table.get(List.of(key)) instanceof Long value && value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) return value.intValue();
        throw new IOException("UI state " + key + " must be an integer");
    }

    private static List<String> strings(TomlTable table, String key) {
        if (!(table.get(List.of(key)) instanceof TomlArray array)) return List.of();
        return array.toList().stream().filter(String.class::isInstance).map(String.class::cast).limit(MAX_CHOOSER_PATHS).toList();
    }

    public Optional<Panel> panel(String id) { return Optional.ofNullable(panels.get(id)); }
    /** Every remembered panel by id, sorted by id. */
    public Map<String, Panel> panels() { return Collections.unmodifiableMap(new TreeMap<>(panels)); }
    public void putPanel(String id, Panel value) { if (ID.matcher(id).matches()) panels.put(id, Objects.requireNonNull(value)); }
    public Dock dock() { return dock; }
    public void setDock(Dock value) { dock = Objects.requireNonNull(value); }
    public Optional<Bounds> window(String id) { return Optional.ofNullable(windows.get(id)); }
    public void putWindow(String id, Bounds value) { if (ID.matcher(id).matches()) windows.put(id, value); }
    public boolean railVisible() { return railVisible; }
    public void setRailVisible(boolean value) { railVisible = value; }
    public Chooser chooser() { return chooser; }
    public void setChooser(Chooser value) { chooser = Objects.requireNonNull(value); }

    /** Writes version 2. Ids are restricted to characters that need no TOML escaping. */
    public void save() {
        if (file == null) return;
        var text = new StringBuilder("version = 2\nrail_visible = ").append(railVisible).append('\n');
        text.append("\n[dock]\nleft_width = ").append(dock.leftWidth()).append("\nright_width = ").append(dock.rightWidth())
            .append("\nbottom_height = ").append(dock.bottomHeight()).append("\nleft_ratio = ").append(dock.leftRatio())
            .append("\nright_ratio = ").append(dock.rightRatio()).append("\nbottom_ratio = ").append(dock.bottomRatio()).append('\n');
        panels.forEach((id, panel) -> text.append("\n[panels.\"").append(id).append("\"]\nslot = \"").append(panel.slot().name())
            .append("\"\norder = ").append(panel.order()).append("\nvisible = ").append(panel.visible()).append('\n'));
        windows.forEach((id, bounds) -> text.append("\n[windows.\"").append(id).append("\"]\nx = ").append(bounds.x())
            .append("\ny = ").append(bounds.y()).append("\nwidth = ").append(bounds.width()).append("\nheight = ").append(bounds.height()).append('\n'));
        if (!chooser.equals(Chooser.DEFAULT))
            text.append("\n[chooser]\nrecent = ").append(array(chooser.recent())).append("\npinned = ").append(array(chooser.pinned()))
                .append("\npreview = ").append(chooser.preview()).append("\nshow_hidden = ").append(chooser.showHidden()).append('\n');
        try { TomlStateFile.writeAtomically(file, ".ui-state-", text.toString()); }
        catch (IOException failure) { LOG.log(System.Logger.Level.WARNING, "Could not save UI state to " + file, failure); }
    }

    private static String array(List<String> values) {
        return values.stream().map(UiState::quote).collect(Collectors.joining(", ", "[", "]"));
    }

    /** A TOML basic string: quotes and backslashes escaped, control characters as \\uXXXX escapes, a lone surrogate as U+FFFD. */
    static String quote(String value) {
        var out = new StringBuilder("\"");
        value.codePoints().forEach(c -> {
            if (c == '"' || c == '\\') out.append('\\').appendCodePoint(c);
            else if (c < 0x20 || c == 0x7f) out.append(String.format("\\u%04X", c));
            else if (c >= 0xD800 && c <= 0xDFFF) out.append('\uFFFD');
            else out.appendCodePoint(c);
        });
        return out.append('"').toString();
    }
}
```

In `persistence/package-info.java` replace the allowed-dependencies line:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.dock (slots and the dock's size limits).
```

- [ ] **Step 4: Bridge `WindowContributions` to the new records**

`WindowContributions` still lays panels out in the old regions until Task 8, which rewrites it. Until then it reads
and writes version 2 like this. Add the imports:

```java
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
```

In `syncPanels`, replace the two lines that set `panel.region` and `panel.size` with:

```java
            panel.region = saved.map(value -> PanelRegion.valueOf(value.slot().side().name()))
                .orElse(PanelRegion.valueOf(entry.defaultSlot().side().name()));
            panel.size = savedSize(panel.region);
```

Replace `persist(PanelInstance panel)` with these three methods:

```java
    private void persist(PanelInstance panel) {
        remember(panel);
        state.save();
    }

    /** Bridge until Task 8: a region's remembered size is its side's. */
    private int savedSize(PanelRegion region) {
        var dock = state.dock();
        return switch (region) {
            case LEFT -> dock.leftWidth();
            case RIGHT -> dock.rightWidth();
            case BOTTOM -> dock.bottomHeight();
        };
    }

    /** Bridge until Task 8: the panel's region becomes its side's first slot and its size the side's size. */
    private void remember(PanelInstance panel) {
        var dock = state.dock();
        int size = Math.clamp(panel.size, DockState.MIN_SIZE, DockState.MAX_SIZE);
        state.putPanel(panel.entry.id(), new UiState.Panel(Slot.of(Side.valueOf(panel.region.name()), false), UiState.Panel.LAST, panel.visible));
        state.setDock(switch (panel.region) {
            case LEFT -> new UiState.Dock(size, dock.rightWidth(), dock.bottomHeight(), dock.leftRatio(), dock.rightRatio(), dock.bottomRatio());
            case RIGHT -> new UiState.Dock(dock.leftWidth(), size, dock.bottomHeight(), dock.leftRatio(), dock.rightRatio(), dock.bottomRatio());
            case BOTTOM -> new UiState.Dock(dock.leftWidth(), dock.rightWidth(), size, dock.leftRatio(), dock.rightRatio(), dock.bottomRatio());
        });
    }
```

In `close()`, replace the line that saves a visible panel with:

```java
            if (panel.visible) { panel.size = owner.regions().size(panel.region); remember(panel); }
```

In `WindowPanelsTest.movesPersistAndANewWindowRestoresThem`, replace the `UiState.load(file).panel(…)` assertion with:

```java
            assertThat(UiState.load(file).panel("dev.x.hosts")).get().satisfies(panel -> {
                assertThat(panel.slot()).isEqualTo(Slot.RIGHT_TOP);
                assertThat(panel.visible()).isTrue();
            });
```

- [ ] **Step 5: Run the tests to make sure they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.persistence.*' --tests 'dev.jasper.app.workspace.WindowPanelsTest'`
Expected: PASS (7 `UiStateTest` cases).

- [ ] **Step 6: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL; `verifyApplicationArchitecture` accepts `persistence → dock` (no cycle: `dock` depends
on nothing).

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/persistence jasper-app/src/test/java/dev/jasper/app/persistence \
        jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java
git commit -F - <<'EOF'
feat(persistence): ui-state.toml version 2 with a dock table and slots

Panels remember a slot, an order and visibility; the dock remembers per-side
sizes and ratios. A bad entry or key falls back alone. Version 1 files
migrate on read (regions to first slots, sizes per side, the first visible
panel of a region wins), and the next save writes version 2. Windows and the
chooser are untouched. WindowContributions bridges to the new records until
the dock replaces the regions.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 5: `ToolWindowHeader` and the six-slot `ToolWindowMenu`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowMenu.java`
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java`

**Interfaces:**
- Consumes: Task 1's `Slot` (`values()`, `displayName()`); `AppIcons.bundled(String)` with the catalog ids
  `general/moreVertical` and `general/hide`.
- Produces (package-private, `dev.jasper.app.workspace`, EDT only):
  - `final class ToolWindowMenu` with `static JPopupMenu create(Slot current, boolean visible, Consumer<Slot> move, Runnable hide)`:
    item 0 is the `JMenu` "Move to" holding six `JCheckBoxMenuItem`s by display name (current checked; choosing it
    does nothing), item 1 is "Hide" (enabled only while visible).
  - `final class ToolWindowHeader extends JPanel` with `static final int HEIGHT = 32`,
    constructor `ToolWindowHeader(String title, Supplier<JPopupMenu> menu, Runnable onHide)`, and
    `JLabel titleLabel()`, `JButton optionsButton()`, `JButton hideButton()`, `JPopupMenu menu()`.
  - Test helper `ToolWindowHeaderTest.item(JPopupMenu menu, String text)` (package-private, static) finds a menu item,
    in the menu or its submenu; later tasks use it.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Font;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowHeaderTest {
    private final List<String> events = new ArrayList<>();

    private JPopupMenu menu(Slot current, boolean visible) {
        return ToolWindowMenu.create(current, visible, slot -> events.add("move:" + slot), () -> events.add("hide"));
    }

    static JMenuItem item(JPopupMenu menu, String text) {
        for (var child : menu.getComponents()) {
            if (child instanceof JMenu submenu) for (var nested : submenu.getMenuComponents())
                if (nested instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
            if (child instanceof JMenuItem entry && entry.getText().equals(text)) return entry;
        }
        throw new AssertionError("No menu item " + text);
    }

    @Test void theMenuMovesToAnyOfTheSixSlotsAndHides() {
        JPopupMenu menu = menu(Slot.LEFT_BOTTOM, true);
        var moveTo = (JMenu) menu.getComponent(0);
        assertThat(moveTo.getText()).isEqualTo("Move to");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).map(item -> ((JMenuItem) item).getText()))
            .containsExactly("Left Top", "Left Bottom", "Bottom Left", "Right Top", "Right Bottom", "Bottom Right");
        assertThat(Arrays.stream(moveTo.getMenuComponents()).filter(item -> ((JCheckBoxMenuItem) item).isSelected())
            .map(item -> ((JMenuItem) item).getText())).as("the current slot is checked").containsExactly("Left Bottom");
        item(menu, "Bottom Right").doClick();
        item(menu, "Left Bottom").doClick();
        item(menu, "Hide").doClick();
        assertThat(events).as("choosing the current slot does nothing").containsExactly("move:BOTTOM_RIGHT", "hide");
        assertThat(item(menu(Slot.RIGHT_TOP, false), "Hide").isEnabled()).as("nothing to hide").isFalse();
    }

    @Test void theHeaderShowsThePlainTitleAndHidesOrOpensItsMenu() {
        var header = new ToolWindowHeader("<html><b>Sessions</b>", () -> menu(Slot.LEFT_TOP, true), () -> events.add("hidden"));
        assertThat(header.titleLabel().getText()).isEqualTo("<html><b>Sessions</b>");
        assertThat(header.titleLabel().getClientProperty("html.disable")).as("a plugin title is never HTML").isEqualTo(Boolean.TRUE);
        assertThat(header.titleLabel().getFont().getStyle() & Font.BOLD).isEqualTo(Font.BOLD);
        header.updateUI();
        SwingUtilities.updateComponentTreeUI(header);
        assertThat(header.titleLabel().getFont().isBold()).as("bold after a look-and-feel change").isTrue();
        assertThat(header.optionsButton().getAccessibleContext().getAccessibleName()).isEqualTo("Options");
        assertThat(header.optionsButton().getToolTipText()).isEqualTo("Options");
        assertThat(header.hideButton().getAccessibleContext().getAccessibleName()).isEqualTo("Hide");
        assertThat(header.optionsButton().isFocusable()).isFalse();
        assertThat(header.getPreferredSize().height).isEqualTo(UIScale.scale(ToolWindowHeader.HEIGHT));

        header.hideButton().doClick();
        header.optionsButton().doClick();
        assertThat(events).as("a header that is not showing opens no popup").containsExactly("hidden");
        assertThat(header.menu().getComponentCount()).isEqualTo(2);
    }

    @Test void theButtonsSitAtTheRightAndAreCenteredVertically() {
        var header = new ToolWindowHeader("Sessions", () -> menu(Slot.LEFT_TOP, true), () -> { });
        header.setSize(260, header.getPreferredSize().height);
        LayoutTestSupport.layoutTree(header);
        var hide = SwingUtilities.convertRectangle(header.hideButton().getParent(), header.hideButton().getBounds(), header);
        var options = SwingUtilities.convertRectangle(header.optionsButton().getParent(), header.optionsButton().getBounds(), header);
        assertThat(hide.x + hide.width).isEqualTo(260 - UIScale.scale(6));
        assertThat(options.x + options.width).isLessThan(hide.x);
        assertThat(Math.abs(hide.y - (header.getHeight() - hide.y - hide.height))).isLessThanOrEqualTo(1);
        var title = SwingUtilities.convertRectangle(header.titleLabel().getParent(), header.titleLabel().getBounds(), header);
        assertThat(title.x).isEqualTo(UIScale.scale(10));
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolWindowHeaderTest'`
Expected: FAIL — `cannot find symbol: class ToolWindowMenu` and `class ToolWindowHeader`.

- [ ] **Step 3: Write the menu and the header**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowMenu.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.dock.Slot;
import java.util.function.Consumer;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JMenu;
import javax.swing.JMenuItem;
import javax.swing.JPopupMenu;

/**
 * A tool window's menu, the same from its stripe button (right-click) and its header's options button: Move to ›
 * the six slots by name with the current one checked, then Hide. EDT only.
 */
final class ToolWindowMenu {
    private ToolWindowMenu() { }

    /**
     * @param current the window's slot; choosing it again does nothing
     * @param visible whether the window shows, which enables Hide
     * @param move moves the window to another slot
     * @param hide hides the window
     */
    static JPopupMenu create(Slot current, boolean visible, Consumer<Slot> move, Runnable hide) {
        var menu = new JPopupMenu();
        var moveTo = new JMenu("Move to");
        for (Slot slot : Slot.values()) {
            var item = new JCheckBoxMenuItem(slot.displayName(), slot == current);
            if (slot != current) item.addActionListener(event -> move.accept(slot));
            moveTo.add(item);
        }
        menu.add(moveTo);
        var hideItem = new JMenuItem("Hide");
        hideItem.setEnabled(visible);
        hideItem.addActionListener(event -> hide.run());
        menu.add(hideItem);
        return menu;
    }
}
```

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.platform.AppIcons;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.Font;
import java.util.function.Supplier;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.UIManager;

/**
 * The header at the top of a tool window's frame, as in IntelliJ's new UI: the title on the left and, on the right,
 * an options button (the ⋮ menu: Move to, Hide) and a hide button (—). It has no bar colour of its own: a plain
 * panel inside the frame, it takes the tool window background. Plan 2 makes it a drag handle. EDT only.
 */
final class ToolWindowHeader extends JPanel {
    /** Height in logical pixels. */
    static final int HEIGHT = 32;

    private final JLabel title = new JLabel() {
        @Override public void updateUI() {
            super.updateUI();
            Font font = UIManager.getFont("Label.font");
            if (font != null) setFont(font.deriveFont(Font.BOLD));
        }
    };
    private final JButton options = button("general/moreVertical", "Options");
    private final JButton hide = button("general/hide", "Hide");
    private final Supplier<JPopupMenu> menu;

    /**
     * @param text the window's title, shown as plain text
     * @param menu builds the options menu each time it opens, so it names the window's current slot
     * @param onHide hides the window
     */
    ToolWindowHeader(String text, Supplier<JPopupMenu> menu, Runnable onHide) {
        super(new BorderLayout());
        this.menu = menu;
        title.putClientProperty("html.disable", Boolean.TRUE);
        title.setText(text);
        options.addActionListener(event -> {
            if (options.isShowing()) menu().show(options, 0, options.getHeight());
        });
        hide.addActionListener(event -> onHide.run());
        var buttons = new JPanel();
        buttons.setOpaque(false);
        buttons.setLayout(new BoxLayout(buttons, BoxLayout.X_AXIS));
        buttons.add(options);
        buttons.add(Box.createHorizontalStrut(2));
        buttons.add(hide);
        // A scaling border: 10px before the title, 6px after the buttons.
        setBorder(new FlatEmptyBorder(0, 10, 0, 6));
        add(title, BorderLayout.CENTER);
        add(buttons, BorderLayout.EAST);
    }

    private static JButton button(String icon, String name) {
        var button = new JButton(AppIcons.bundled(icon));
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
        return button;
    }

    JLabel titleLabel() { return title; }

    JButton optionsButton() { return options; }

    JButton hideButton() { return hide; }

    /** The options menu as it would open now. */
    JPopupMenu menu() { return menu.get(); }

    @Override public Dimension getPreferredSize() { return new Dimension(super.getPreferredSize().width, UIScale.scale(HEIGHT)); }

    @Override public Dimension getMinimumSize() { return new Dimension(super.getMinimumSize().width, UIScale.scale(HEIGHT)); }
}
```

- [ ] **Step 4: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolWindowHeaderTest'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowMenu.java \
        jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowHeader.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowHeaderTest.java
git commit -F - <<'EOF'
feat(workspace): add the tool window header and its six-slot menu

ToolWindowHeader shows the window's title as plain bold text with an options
button and a hide button on the tool window background. ToolWindowMenu,
shared with the stripe buttons, offers Move to with the six slots (the
current one checked) and Hide.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 6: `ToolStripe`, one of the two tool window stripes

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeTest.java`

**Interfaces:**
- Consumes: Task 1's `Slot` (`stripe()`, `group()`), `Side`; Task 5's `ToolWindowMenu.create` and the test helper
  `ToolWindowHeaderTest.item`; `ConfigSnapshot.DEFAULT_RAIL_WIDTH`, `Command.ICON`, `AppIcons.icon("command")`.
- Produces (package-private, EDT only): `final class ToolStripe extends JPanel` with
  `record Button(String id, String title, Icon icon, Slot slot, boolean selected)`, constructor `ToolStripe(Side side)`
  (LEFT or RIGHT; BOTTOM throws), fields `Consumer<String> onToggle`, `BiConsumer<String, Slot> onMove`,
  `Consumer<String> onHide`, and `Side side()`, `void render(List<Button> windows, List<Action> actions)`,
  `boolean empty()`, `List<AbstractButton> buttons()` (top group, second group, actions, foot, in that order),
  `void setRailWidth(int)`, `void refreshTheme()`. It replaces `WindowRail` in Task 8; until then both exist.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.swing.AbstractAction;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.JSeparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

@ExtendWith(EdtTestExtension.class)
class ToolStripeTest {
    private static int s(int value) { return UIScale.scale(value); }

    private static ToolStripe.Button window(String id, Slot slot) { return new ToolStripe.Button(id, id.toUpperCase(), null, slot, false); }

    private static List<Rectangle> separators(ToolStripe stripe) {
        return Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance).map(Component::getBounds).toList();
    }

    private static Action action(String name, List<String> events) {
        return new AbstractAction(name) {
            @Override public void actionPerformed(ActionEvent event) { events.add(name); }
        };
    }

    private static void layout(ToolStripe stripe, int height) {
        stripe.setSize(stripe.getPreferredSize().width, s(height));
        stripe.doLayout();
    }

    @Test void theLeftStripeStacksItsGroupsPutsActionsAfterTheSecondAndPinsTheFoot() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(window("a", Slot.LEFT_TOP), window("c", Slot.LEFT_BOTTOM), window("d", Slot.BOTTOM_LEFT),
            window("b", Slot.LEFT_TOP), window("x", Slot.RIGHT_TOP)), List.of(action("Open", new ArrayList<>())));
        layout(stripe, 400);
        List<AbstractButton> buttons = stripe.buttons();
        assertThat(buttons).extracting(AbstractButton::getToolTipText).as("the right stripe's window is not here")
            .containsExactly("A", "B", "C", "Open", "D");
        int square = s(22), step = square + s(4);
        assertThat(buttons.get(0).getBounds()).isEqualTo(new Rectangle(s(3), s(4), square, square));
        assertThat(buttons.get(1).getY()).isEqualTo(s(4) + step);
        assertThat(separators(stripe)).hasSize(2);
        assertThat(separators(stripe).get(0)).isEqualTo(new Rectangle(s(7), s(4) + 2 * step + s(3), s(14), s(1)));
        assertThat(buttons.get(2).getY()).as("the second group under its separator").isEqualTo(s(4) + 2 * step + s(8));
        assertThat(buttons.get(3).getY()).as("actions behind their own separator").isEqualTo(s(4) + 3 * step + 2 * s(8));
        assertThat(buttons.get(4).getBounds()).as("the foot is pinned to the bottom")
            .isEqualTo(new Rectangle(s(3), s(400) - s(4) - square, square, square));
        layout(stripe, 600);
        assertThat(buttons.get(4).getY()).isEqualTo(s(600) - s(4) - square);
    }

    @Test void separatorsAppearOnlyForASecondGroupOrForActionsBelowWindows() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(window("a", Slot.LEFT_TOP), window("d", Slot.BOTTOM_LEFT)), List.of());
        layout(stripe, 400);
        assertThat(separators(stripe)).isEmpty();
        stripe.render(List.of(), List.of(action("Open", new ArrayList<>())));
        layout(stripe, 400);
        assertThat(separators(stripe)).as("actions alone need no separator").isEmpty();
        stripe.render(List.of(window("c", Slot.LEFT_BOTTOM)), List.of());
        layout(stripe, 400);
        assertThat(separators(stripe)).as("the second group keeps its separator").singleElement()
            .isEqualTo(new Rectangle(s(7), s(4) + s(3), s(14), s(1)));
        assertThat(stripe.buttons().getFirst().getY()).isEqualTo(s(4) + s(8));
    }

    @Test void theRightStripeShowsTheRightSlotsAndAnEmptyStripeIsEmpty() {
        var stripe = new ToolStripe(Side.RIGHT);
        assertThat(stripe.empty()).isTrue();
        assertThat(stripe.getAccessibleContext().getAccessibleName()).isEqualTo("Right tool windows");
        stripe.render(List.of(window("a", Slot.RIGHT_TOP), window("b", Slot.RIGHT_BOTTOM), window("c", Slot.BOTTOM_RIGHT),
            window("l", Slot.LEFT_TOP), window("f", Slot.BOTTOM_LEFT)), List.of());
        layout(stripe, 300);
        assertThat(stripe.buttons()).extracting(AbstractButton::getToolTipText).containsExactly("A", "B", "C");
        assertThat(stripe.buttons().get(2).getY()).isEqualTo(s(300) - s(4) - s(22));
        assertThat(stripe.empty()).isFalse();
        assertThatIllegalArgumentException().isThrownBy(() -> new ToolStripe(Side.BOTTOM));
    }

    @Test void aClickTogglesAndTheRightClickMenuMovesOrHides() {
        List<String> events = new ArrayList<>();
        var stripe = new ToolStripe(Side.LEFT);
        stripe.onToggle = id -> events.add("toggle:" + id);
        stripe.onMove = (id, slot) -> events.add("move:" + id + ":" + slot);
        stripe.onHide = id -> events.add("hide:" + id);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, true),
            new ToolStripe.Button("b", "B", null, Slot.BOTTOM_LEFT, false)), List.of(action("Open", events)));
        var buttons = stripe.buttons();
        assertThat(buttons.get(0).isSelected()).isTrue();
        assertThat(buttons.get(2).isSelected()).isFalse();
        buttons.get(0).doClick();
        buttons.get(1).doClick();
        var menu = buttons.get(0).getComponentPopupMenu();
        ToolWindowHeaderTest.item(menu, "Right Bottom").doClick();
        ToolWindowHeaderTest.item(menu, "Hide").doClick();
        assertThat(ToolWindowHeaderTest.item(buttons.get(2).getComponentPopupMenu(), "Hide").isEnabled()).isFalse();
        assertThat(buttons.get(1).getComponentPopupMenu()).as("an action has no window menu").isNull();
        assertThat(events).containsExactly("toggle:a", "Open", "move:a:RIGHT_BOTTOM", "hide:a");
    }

    @Test void theStripeLaysOutItsButtonsFromItsWidth() {
        var stripe = new ToolStripe(Side.RIGHT);
        stripe.render(List.of(window("a", Slot.RIGHT_TOP), window("b", Slot.RIGHT_BOTTOM)), List.of());
        for (int width : new int[]{28, 36, 64}) {
            stripe.setRailWidth(width);
            assertThat(stripe.getPreferredSize().width).as("width %d", width).isEqualTo(s(width));
            assertThat(stripe.getMinimumSize().width).isEqualTo(s(width));
            layout(stripe, 400);
            int side = s(width - 6);
            assertThat(stripe.buttons().get(0).getBounds()).isEqualTo(new Rectangle(s(3), s(4), side, side));
            assertThat(separators(stripe).getFirst().width).isEqualTo(s(width - 14));
            assertThat(stripe.buttons().get(1).getY()).as("a button, then a separator").isEqualTo(s(4) + side + s(4) + s(8));
            assertThat(stripe.buttons().get(1).getWidth()).isEqualTo(side);
        }
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolStripeTest'`
Expected: FAIL — `cannot find symbol: class ToolStripe`.

- [ ] **Step 3: Write the stripe**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * One of the window's two tool window stripes (IntelliJ's tool window bars). Top to bottom: the top group; a separator
 * when the second group has icons; the second group; on the left stripe, the plugins' rail actions behind their own
 * separator; flexible space; and the foot group, pinned to the bottom. A button is selected while its window shows;
 * a click toggles the window and a right-click opens its {@link ToolWindowMenu}. The stripe owns no state: the window
 * renders it again after every change. EDT only.
 */
final class ToolStripe extends JPanel {
    /** One tool window's button. */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected) { }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    private final Side side;
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final List<Component> upper = new ArrayList<>();
    private final List<Component> foot = new ArrayList<>();
    /** The stripe's width in logical pixels; buttons, separators and spacing follow it. */
    private int railWidth = ConfigSnapshot.DEFAULT_RAIL_WIDTH;

    ToolStripe(Side side) {
        super(null);
        if (side == Side.BOTTOM) throw new IllegalArgumentException("A stripe stands on the left or the right");
        this.side = side;
        getAccessibleContext().setAccessibleName(side == Side.LEFT ? "Left tool windows" : "Right tool windows");
        render(List.of(), List.of());
        refreshTheme();
    }

    Side side() { return side; }

    private static void style(AbstractButton button, Icon icon, String name) {
        button.setHideActionText(true);
        button.setText(null);
        button.setIcon(icon);
        button.putClientProperty("JButton.buttonType", "toolBarButton");
        button.putClientProperty("FlatLaf.style", "arc: 10");
        button.setMargin(new Insets(0, 0, 0, 0));
        button.setFocusable(false);
        button.setToolTipText(name);
        button.getAccessibleContext().setAccessibleName(name);
    }

    /**
     * Shows this stripe's windows by group, in the order given, and the actions after the second group. Windows whose
     * slot belongs to the other stripe are ignored.
     */
    void render(List<Button> windows, List<Action> actions) {
        removeAll();
        buttons.clear(); upper.clear(); foot.clear();
        List<Button> mine = windows.stream().filter(window -> window.slot().stripe() == side).toList();
        for (Button window : group(mine, Slot.Group.TOP)) upper.add(toggle(window));
        List<Button> second = group(mine, Slot.Group.SECOND);
        if (!second.isEmpty()) upper.add(new JSeparator());
        for (Button window : second) upper.add(toggle(window));
        if (!actions.isEmpty() && !upper.isEmpty()) upper.add(new JSeparator());
        for (Action action : actions) upper.add(action(action));
        for (Button window : group(mine, Slot.Group.FOOT)) foot.add(toggle(window));
        upper.forEach(this::add);
        foot.forEach(this::add);
        revalidate(); repaint();
    }

    private static List<Button> group(List<Button> windows, Slot.Group group) {
        return windows.stream().filter(window -> window.slot().group() == group).toList();
    }

    private AbstractButton toggle(Button window) {
        var button = new JToggleButton();
        style(button, window.icon(), window.title());
        button.setSelected(window.selected());
        button.addActionListener(event -> onToggle.accept(window.id()));
        button.setComponentPopupMenu(ToolWindowMenu.create(window.slot(), window.selected(),
            slot -> onMove.accept(window.id(), slot), () -> onHide.accept(window.id())));
        buttons.add(button);
        return button;
    }

    private AbstractButton action(Action action) {
        var button = new JButton(action);
        Icon icon = action.getValue(Command.ICON) instanceof Icon contributed ? contributed : AppIcons.icon("command");
        style(button, icon, (String) action.getValue(Action.NAME));
        buttons.add(button);
        return button;
    }

    /** True when there are neither tool windows nor actions on this stripe. */
    boolean empty() { return buttons.isEmpty(); }

    /** Every button, top to bottom: the groups' toggles and the actions. */
    List<AbstractButton> buttons() { return List.copyOf(buttons); }

    /** Sets the width in logical pixels; the caller validates the range. */
    void setRailWidth(int width) {
        if (railWidth == width) return;
        railWidth = width;
        revalidate(); repaint();
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(railWidth), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        // Square buttons 3 px in from each side and 4 px apart; separators inset 7 px; the foot ends 4 px from the bottom.
        int x = UIScale.scale(3), square = UIScale.scale(railWidth - 6), gap = UIScale.scale(4), y = UIScale.scale(4);
        for (Component child : upper) y = place(child, x, y, square, gap);
        int footHeight = foot.isEmpty() ? 0 : foot.size() * square + (foot.size() - 1) * gap;
        int footY = Math.max(y, getHeight() - UIScale.scale(4) - footHeight);
        for (Component child : foot) footY = place(child, x, footY, square, gap);
    }

    /** Places one child at y and returns where the next one starts. */
    private int place(Component child, int x, int y, int square, int gap) {
        if (child instanceof JSeparator) {
            child.setBounds(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(railWidth - 14), UIScale.scale(1));
            return y + UIScale.scale(8);
        }
        child.setBounds(x, y, square, square);
        return y + square + gap;
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        repaint();
    }
}
```

- [ ] **Step 4: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolStripeTest' --tests 'dev.jasper.app.workspace.ToolWindowHeaderTest'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeTest.java
git commit -F - <<'EOF'
feat(workspace): add the tool window stripe

A stripe lays out its top group, a separator and the second group when that
has icons, the left stripe's rail actions behind their own separator, and
the foot group pinned to the bottom. Buttons toggle their window; the
right-click menu moves it to any slot or hides it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 7: `ToolWindowArea`, the dock layout over `DockGeometry`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowAreaTest.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/DockTestSupport.java`
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/SidePanelRenderTest.java` (whole file)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowSurfaceTest.java` (whole file)

**Interfaces:**
- Consumes: Task 1's `DockState`, `Slot`, `Side`; Task 2's `DockGeometry.compute`, `DockGeometry.drag`,
  `DockGeometry.Metrics.at`, `DockGeometry.Handle`, `DockGeometry.Divider`; Task 5's `ToolWindowHeader`,
  `ToolWindowMenu`; Task 6's `ToolStripe` (in `ToolWindowSurfaceTest`); `ToolWindowSurface.apply`.
- Produces (package-private, EDT only):
  - `final class ToolWindowArea extends JPanel` with constructor `ToolWindowArea(JComponent terminalColumn, DockState state)`;
    fields `BiConsumer<String, Slot> onMove` and `Consumer<String> onHide` (the header's buttons and menu);
    `DockState state()`, `void setContent(String id, String title, JComponent component)`,
    `void removeContent(String id)`, `JComponent content(Slot)`, `ToolWindowHeader headerOf(Slot)`,
    `JComponent divider(DockGeometry.Handle)`, `void sync()`, `void setTerminalBackground(Color)`, `void refreshTheme()`,
    `void setStripesVisible(boolean)`, `void setStatusVisible(boolean)`, `void setHeaderVisible(boolean)`.
    Whoever changes the state calls `sync()`; a divider drag changes the state itself, once, on release.
  - Test helper `DockTestSupport` with `static void show(ToolWindowArea, Slot, JComponent, int size)`,
    `static void hide(ToolWindowArea, Slot)` and `static String id(Slot)` (`test.left-top` and so on). Task 8 adds
    `WindowContent` overloads.
- `WorkspaceRegions` keeps running the window until Task 8 swaps it out.

- [ ] **Step 1: Write the test helper and the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockTestSupport.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import java.util.Locale;
import javax.swing.JComponent;

/** Puts plain content into a dock without a plugin, as the old {@code regions().show(...)} did. Test only. */
final class DockTestSupport {
    private DockTestSupport() { }

    /** Shows content in a slot, with its side at size; the same slot again replaces the content. */
    static void show(ToolWindowArea area, Slot slot, JComponent content, int size) {
        String id = id(slot);
        DockState state = area.state();
        area.setContent(id, slot.displayName(), content);
        if (state.slot(id).isEmpty()) state.add(id, slot);
        state.resize(slot.side(), size);
        state.show(id);
        area.sync();
    }

    static void hide(ToolWindowArea area, Slot slot) {
        area.state().hide(id(slot));
        area.sync();
    }

    /** The id the helper gives a slot's content, such as {@code test.left-top}. */
    static String id(Slot slot) { return "test." + slot.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
```

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowAreaTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowAreaTest {
    private static Rectangle in(ToolWindowArea area, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), area);
    }

    private static void layout(ToolWindowArea area) {
        area.setSize(1000, 600);
        LayoutTestSupport.layoutTree(area);
    }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void theCenterKeepsItsRoundedFrameWhenPanelsOpenAndClose() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        area.setStripesVisible(true);
        deck.setMinimumSize(new Dimension(420, 320));
        assertThat(area.getMinimumSize()).as("terminal minimum plus the top and bottom gutters and the lower inset")
            .isEqualTo(new Dimension(420, 340));
        deck.setMinimumSize(new Dimension(500, 360));
        assertThat(area.getMinimumSize()).as("terminal font and split minimum changes propagate").isEqualTo(new Dimension(500, 380));
        layout(area);
        assertThat(in(area, deck)).isEqualTo(new Rectangle(0, 6, 1000, 580));
        assertThat(area.content(Slot.LEFT_TOP)).isNull();

        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 250);
        layout(area);
        assertThat(area.content(Slot.LEFT_TOP)).isSameAs(left);
        assertThat(in(area, left.getParent())).as("the frame").isEqualTo(new Rectangle(0, 0, 250, 600));
        int header = UIScale.scale(ToolWindowHeader.HEIGHT);
        assertThat(in(area, area.headerOf(Slot.LEFT_TOP))).isEqualTo(new Rectangle(0, 6, 250, header));
        assertThat(in(area, left)).as("content under the header, inside the gutters").isEqualTo(new Rectangle(0, 6 + header, 250, 588 - header));
        assertThat(in(area, deck)).isEqualTo(new Rectangle(256, 6, 744, 580));
        assertThat(area.getMinimumSize()).as("a showing frame keeps its minimum").isEqualTo(new Dimension(500 + 6 + 120, 380));

        DockTestSupport.hide(area, Slot.LEFT_TOP);
        layout(area);
        assertThat(left.getParent()).as("hidden content leaves the tree").isNull();
        assertThat(in(area, deck)).isEqualTo(new Rectangle(0, 6, 1000, 580));
        assertThat(area.state().size(Side.LEFT)).as("the size is remembered").isEqualTo(250);
    }

    @Test void theBottomAreaSpansTheFullWidthUnderTheSides() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        area.setStripesVisible(true);
        var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 200);
        DockTestSupport.show(area, Slot.RIGHT_TOP, right, 300);
        DockTestSupport.show(area, Slot.BOTTOM_LEFT, bottom, 150);
        layout(area);
        assertThat(in(area, left.getParent())).isEqualTo(new Rectangle(0, 0, 200, 444));
        assertThat(in(area, right.getParent())).isEqualTo(new Rectangle(700, 0, 300, 444));
        assertThat(in(area, bottom.getParent())).as("stripe to stripe").isEqualTo(new Rectangle(0, 450, 1000, 150));
        assertThat(in(area, deck.getParent())).isEqualTo(new Rectangle(206, 0, 488, 444));
        assertThat(in(area, deck).y).isEqualTo(6);

        var replacement = new JLabel("other");
        DockTestSupport.show(area, Slot.RIGHT_TOP, replacement, 320);
        layout(area);
        assertThat(area.content(Slot.RIGHT_TOP)).isSameAs(replacement);
        assertThat(right.getParent()).isNull();
        assertThat(in(area, replacement.getParent()).width).isEqualTo(320);
        assertThat(area.state().size(Side.BOTTOM)).isEqualTo(150);
        area.removeContent(DockTestSupport.id(Slot.BOTTOM_LEFT));
        area.sync();
        assertThat(area.content(Slot.BOTTOM_LEFT)).as("an active window without content shows no frame").isNull();
    }

    @Test void bothSlotsOfASideStackWithTheirOwnHeadersAndGutters() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        area.setStripesVisible(true);
        var upper = new JLabel("sessions"); var lower = new JLabel("sftp");
        DockTestSupport.show(area, Slot.LEFT_TOP, upper, 260);
        DockTestSupport.show(area, Slot.LEFT_BOTTOM, lower, 260);
        layout(area);
        assertThat(in(area, upper.getParent())).isEqualTo(new Rectangle(0, 0, 260, 297));
        assertThat(in(area, lower.getParent())).isEqualTo(new Rectangle(0, 303, 260, 297));
        assertThat(area.headerOf(Slot.LEFT_TOP).titleLabel().getText()).isEqualTo("Left Top");
        assertThat(area.headerOf(Slot.LEFT_BOTTOM).titleLabel().getText()).isEqualTo("Left Bottom");
        assertThat(upper.getParent().getInsets()).as("the upper frame keeps the top gutter, the lower one the bottom gutter")
            .isEqualTo(new Insets(6, 0, 0, 0));
        assertThat(lower.getParent().getInsets()).isEqualTo(new Insets(0, 0, 6, 0));
        assertThat(area.divider(DockGeometry.Handle.LEFT_RATIO).getBounds()).isEqualTo(new Rectangle(0, 297, 260, 6));
        assertThat(area.divider(DockGeometry.Handle.LEFT_RATIO).getCursor().getType()).isEqualTo(Cursor.N_RESIZE_CURSOR);
    }

    @Test void hiddenStripesGiveFramesTheirOuterGutterAndTheTerminalNone() {
        var deck = new JPanel();
        var area = new ToolWindowArea(deck, new DockState());
        var left = new JLabel("left"); var bottom = new JLabel("bottom");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 250);
        DockTestSupport.show(area, Slot.BOTTOM_RIGHT, bottom, 150);
        layout(area);
        assertThat(left.getParent().getInsets()).isEqualTo(new Insets(6, 6, 0, 0));
        assertThat(bottom.getParent().getInsets()).isEqualTo(new Insets(0, 6, 6, 6));
        assertThat(deck.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(in(area, deck)).as("no lower inset either").isEqualTo(in(area, deck.getParent()));
        area.setStripesVisible(true); area.setHeaderVisible(true); area.setStatusVisible(true);
        assertThat(left.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(bottom.getParent().getInsets()).isEqualTo(new Insets(0, 0, 0, 0));
        assertThat(((javax.swing.JComponent) left.getParent()).getClientProperty("Jasper.zone")).isEqualTo("chrome");
        assertThat(((javax.swing.JComponent) deck.getParent()).getClientProperty("Jasper.zone")).isNull();
    }

    @Test void aDividerIsAQuietGapThatPreviewsADragAndChangesTheStateOnceOnRelease() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            var state = new DockState();
            var area = new ToolWindowArea(new JPanel(), state);
            var panel = new JPanel();
            DockTestSupport.show(area, Slot.LEFT_TOP, panel, 250);
            List<DockState.Change> changes = new ArrayList<>();
            state.addListener(changes::add);
            for (var theme : List.of(Theme.DARK, Theme.INTELLIJ_LIGHT)) {
                ThemeTestSupport.install(theme);
                SwingUtilities.updateComponentTreeUI(area);
                area.refreshTheme();
                layout(area);
                var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
                var image = new BufferedImage(area.getWidth(), area.getHeight(), BufferedImage.TYPE_INT_ARGB);
                var g = image.createGraphics();
                try { area.paint(g); } finally { g.dispose(); }
                var gap = divider.getBounds();
                assertThat(gap).isEqualTo(new Rectangle(250, 0, 6, 600));
                int chrome = UIManager.getColor("Jasper.titleBackground").getRGB();
                for (int x = gap.x; x < gap.x + gap.width; x++) for (int y = gap.y; y < gap.y + gap.height; y += 7)
                    assertThat(image.getRGB(x, y)).as("the gap has no border or grip, %s", theme).isEqualTo(chrome);
                assertThat(divider.getCursor().getType()).isEqualTo(Cursor.E_RESIZE_CURSOR);
            }

            var divider = area.divider(DockGeometry.Handle.LEFT_WIDTH);
            mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 100);
            mouse(divider, MouseEvent.MOUSE_DRAGGED, 72, 100);
            layout(area);
            assertThat(panel.getParent().getWidth()).as("the drag previews").isEqualTo(320);
            assertThat(state.size(Side.LEFT)).as("but changes nothing yet").isEqualTo(250);
            // The divider moved with the preview, so the same mouse position is 2px into it.
            mouse(divider, MouseEvent.MOUSE_RELEASED, 2, 100);
            assertThat(state.size(Side.LEFT)).isEqualTo(320);
            assertThat(changes).as("one change for the whole drag").hasSize(1);
            layout(area);
            assertThat(panel.getParent().getWidth()).isEqualTo(320);
            assertThat(area.divider(DockGeometry.Handle.BOTTOM_HEIGHT).getBounds()).as("no bottom area, no divider").isEqualTo(new Rectangle());
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void sizesAreClampedToAUsableMinimum() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        var left = new JLabel("left");
        DockTestSupport.show(area, Slot.LEFT_TOP, left, 5);
        layout(area);
        assertThat(area.state().size(Side.LEFT)).isEqualTo(DockState.MIN_SIZE);
        assertThat(left.getParent().getWidth()).isEqualTo(UIScale.scale(DockState.MIN_SIZE));
    }

    @Test void theHeaderHidesAndMovesThroughTheArea() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        List<String> events = new ArrayList<>();
        area.onHide = id -> events.add("hide:" + id);
        area.onMove = (id, slot) -> events.add("move:" + id + ":" + slot);
        DockTestSupport.show(area, Slot.RIGHT_BOTTOM, new JLabel("files"), 260);
        var header = area.headerOf(Slot.RIGHT_BOTTOM);
        header.hideButton().doClick();
        ToolWindowHeaderTest.item(header.menu(), "Left Top").doClick();
        assertThat(((JCheckBoxMenuItem) ToolWindowHeaderTest.item(header.menu(), "Right Bottom")).isSelected()).isTrue();
        assertThat(events).containsExactly("hide:test.right-bottom", "move:test.right-bottom:LEFT_TOP");
        assertThat(area.headerOf(Slot.LEFT_TOP)).isNull();
    }

    @Test void aTinyAreaLaysOutWithoutNegativeSizes() {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        for (Slot slot : Slot.values()) DockTestSupport.show(area, slot, new JLabel(slot.name()), 300);
        for (int[] size : new int[][]{{0, 0}, {40, 30}, {300, 200}}) {
            area.setSize(size[0], size[1]);
            LayoutTestSupport.layoutTree(area);
            for (Component child : area.getComponents()) {
                assertThat(child.getWidth()).as("%s at %sx%s", child, size[0], size[1]).isNotNegative();
                assertThat(child.getHeight()).isNotNegative();
            }
        }
        area.setSize(2000, 1000);
        LayoutTestSupport.layoutTree(area);
        assertThat(area.content(Slot.LEFT_TOP).getParent().getWidth()).as("a larger area restores the sizes").isEqualTo(300);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolWindowAreaTest'`
Expected: FAIL — `cannot find symbol: class ToolWindowArea`.

- [ ] **Step 3: Write the area**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java`. `Host` and its painting are
`WorkspaceRegions.Host` moved here, plus a guard so an unchanged gutter does not replace the border:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.ui.FlatEmptyBorder;
import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Container;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.LayoutManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.RoundRectangle2D;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.BorderFactory;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * The terminal column with the tool windows docked around it, laid out by {@link DockGeometry} from the window's
 * {@link DockState}: a full-width bottom area under the left side, the terminal column and the right side. Each
 * showing tool window sits in a rounded frame under its {@link ToolWindowHeader}; the gaps between frames are this
 * area's dividers. The terminal column has a rounded frame and exterior gutter only while the stripes are visible;
 * otherwise it fills its area edge to edge. The center frame is painted in the terminal palette's background, never
 * a theme's tool window colour. The area changes the state only when a divider drag ends; whoever changes the state
 * calls {@link #sync} afterwards. EDT only.
 */
final class ToolWindowArea extends JPanel {
    private static final int GUTTER = 6, ARC = 16;

    /** A shared frame around plugin-owned content; gutters belong to the workspace chrome. */
    private static final class Host extends JPanel {
        private final int[] gutters = {-1, -1, -1, -1};
        private boolean rounded = true;
        private JComponent content;

        Host(boolean toolWindow) {
            super(new BorderLayout());
            if (toolWindow) putClientProperty("Jasper.zone", "chrome");
            gutters(GUTTER, GUTTER, GUTTER, GUTTER);
        }

        void gutters(int top, int left, int bottom, int right) {
            if (gutters[0] == top && gutters[1] == left && gutters[2] == bottom && gutters[3] == right) return;
            gutters[0] = top; gutters[1] = left; gutters[2] = bottom; gutters[3] = right;
            // An app-owned border keeps the gutter through look-and-feel switches.
            setBorder(BorderFactory.createCompoundBorder(new FlatEmptyBorder(top, left, bottom, right), null));
        }

        private java.awt.Shape surface() {
            var insets = getInsets();
            int width = Math.max(0, getWidth() - insets.left - insets.right);
            int height = Math.max(0, getHeight() - insets.top - insets.bottom), arc = rounded ? UIScale.scale(ARC) : 0;
            return new RoundRectangle2D.Double(insets.left, insets.top, width, height, arc, arc);
        }

        @Override protected boolean isPaintingOrigin() { return true; }
        @Override public boolean contains(int x, int y) { return surface().contains(x, y); }

        @Override protected void paintComponent(Graphics graphics) {
            var g = (Graphics2D) graphics.create();
            try {
                g.setColor(UIManager.getColor("Jasper.titleBackground"));
                g.fillRect(0, 0, getWidth(), getHeight());
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                // The terminal column (tab strip included) is one surface in the terminal palette, so every
                // theme follows the same frame, including its optional rounded corners.
                g.setColor(getBackground()); g.fill(surface());
            } finally { g.dispose(); }
        }

        @Override protected void paintChildren(Graphics graphics) {
            var clipped = (Graphics2D) graphics.create();
            try { clipped.clip(surface()); super.paintChildren(clipped); }
            finally { clipped.dispose(); }
        }
    }

    /** A gap between two frames that resizes what it divides. It paints nothing: the area's chrome shows through. */
    private final class DividerHandle extends JComponent {
        private final DockGeometry.Handle handle;
        /** Where the last layout put it, or null while it is not part of the layout. */
        private DockGeometry.Divider placed;
        private DockGeometry.Divider dragging;
        private int grab;

        DividerHandle(DockGeometry.Handle handle) {
            this.handle = handle;
            setCursor(Cursor.getPredefinedCursor(handle.vertical() ? Cursor.E_RESIZE_CURSOR : Cursor.N_RESIZE_CURSOR));
            var mouse = new MouseAdapter() {
                @Override public void mousePressed(MouseEvent event) {
                    if (!SwingUtilities.isLeftMouseButton(event) || placed == null) return;
                    dragging = placed;
                    grab = handle.vertical() ? event.getX() : event.getY();
                }

                @Override public void mouseDragged(MouseEvent event) {
                    if (dragging == null) return;
                    // The drag lays out a scratch copy; the real state changes once, on release.
                    preview = state.copy();
                    DockGeometry.drag(preview, dragging, position(event), metrics());
                    ToolWindowArea.this.revalidate(); ToolWindowArea.this.repaint();
                }

                @Override public void mouseReleased(MouseEvent event) {
                    if (dragging == null) return;
                    var divider = dragging;
                    dragging = null;
                    preview = null;
                    DockGeometry.drag(state, divider, position(event), metrics());
                    ToolWindowArea.this.revalidate(); ToolWindowArea.this.repaint();
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
        }

        /** Where the dragged leading edge goes, in the area's coordinates. */
        private int position(MouseEvent event) {
            Point point = SwingUtilities.convertPoint(this, event.getPoint(), ToolWindowArea.this);
            return (handle.vertical() ? point.x : point.y) - grab;
        }
    }

    /** What a tool window shows while it is active: its title and its content. */
    private record Content(String title, JComponent component) { }

    /** Lays the frames and dividers out from the geometry; the minimum keeps showing frames at their minimum. */
    private final class DockLayout implements LayoutManager {
        @Override public void addLayoutComponent(String name, Component component) { }
        @Override public void removeLayoutComponent(Component component) { }
        @Override public Dimension preferredLayoutSize(Container parent) { return size(center.getPreferredSize(), true); }
        @Override public Dimension minimumLayoutSize(Container parent) { return size(center.getMinimumSize(), false); }

        @Override public void layoutContainer(Container parent) {
            var layout = DockGeometry.compute(new Rectangle(0, 0, getWidth(), getHeight()), preview != null ? preview : state, metrics());
            center.setBounds(layout.terminal());
            hosts.forEach((slot, host) -> host.setBounds(layout.slot(slot).orElseGet(Rectangle::new)));
            dividers.values().forEach(divider -> {
                divider.placed = layout.divider(divider.handle).orElse(null);
                divider.setBounds(divider.placed == null ? new Rectangle() : divider.placed.bounds());
            });
        }
    }

    private final DockState state;
    private final Host center = new Host(false);
    private final JPanel lowerInset = new JPanel() {
        @Override public Dimension getPreferredSize() { return new Dimension(0, UIScale.scale(ARC / 2)); }
        @Override public Dimension getMinimumSize() { return getPreferredSize(); }
    };
    private final EnumMap<Slot, Host> hosts = new EnumMap<>(Slot.class);
    private final Map<String, Content> contents = new HashMap<>();
    private final EnumMap<DockGeometry.Handle, DividerHandle> dividers = new EnumMap<>(DockGeometry.Handle.class);
    /** A divider drag in progress lays out this copy of the state. */
    private DockState preview;
    private boolean stripesVisible, statusVisible, headerVisible;
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };

    ToolWindowArea(JComponent terminalColumn, DockState state) {
        super(null);
        this.state = Objects.requireNonNull(state, "state");
        setLayout(new DockLayout());
        center.add(terminalColumn, BorderLayout.CENTER);
        lowerInset.setOpaque(false);
        center.add(lowerInset, BorderLayout.SOUTH);
        add(center);
        for (DockGeometry.Handle handle : DockGeometry.Handle.values()) {
            var divider = new DividerHandle(handle);
            dividers.put(handle, divider);
            add(divider);
        }
        refreshTheme();
        sync();
    }

    @Override public void updateUI() {
        super.updateUI();
        // The gaps between frames show the workspace chrome.
        setBackground(UIManager.getColor("Jasper.titleBackground"));
    }

    DockState state() { return state; }

    /** What the window with this id shows when it is active: its header title and its content. */
    void setContent(String id, String title, JComponent component) {
        contents.put(Objects.requireNonNull(id), new Content(Objects.requireNonNull(title), Objects.requireNonNull(component)));
    }

    void removeContent(String id) { contents.remove(id); }

    /** The content showing in the slot, or null. */
    JComponent content(Slot slot) {
        Host host = hosts.get(slot);
        return host == null ? null : host.content;
    }

    /** The header of the slot's frame, or null when the slot shows nothing. */
    ToolWindowHeader headerOf(Slot slot) {
        Host host = hosts.get(slot);
        return host == null ? null : (ToolWindowHeader) ((BorderLayout) host.getLayout()).getLayoutComponent(BorderLayout.NORTH);
    }

    /** The divider for a handle; it has empty bounds while the layout has no such divider. */
    JComponent divider(DockGeometry.Handle handle) { return dividers.get(handle); }

    /**
     * Brings the frames in line with the state: a frame for each slot that shows a window with content, none for the
     * others. Content leaves the component tree while its window is hidden.
     */
    void sync() {
        for (Slot slot : Slot.values()) {
            String id = state.active(slot).orElse(null);
            Content wanted = id == null ? null : contents.get(id);
            Host host = hosts.get(slot);
            if (host != null && (wanted == null || host.content != wanted.component())) {
                remove(host);
                host.removeAll();
                hosts.remove(slot);
                host = null;
            }
            if (wanted != null && host == null) {
                host = new Host(true);
                host.content = wanted.component();
                host.add(header(id, wanted.title()), BorderLayout.NORTH);
                host.add(wanted.component(), BorderLayout.CENTER);
                ToolWindowSurface.apply(host);
                hosts.put(slot, host);
                add(host);
            }
        }
        refreshGutters();
        revalidate(); repaint();
    }

    private ToolWindowHeader header(String id, String title) {
        return new ToolWindowHeader(title,
            () -> ToolWindowMenu.create(state.slot(id).orElse(Slot.LEFT_TOP), state.visible(id),
                slot -> onMove.accept(id, slot), () -> onHide.accept(id)),
            () -> onHide.accept(id));
    }

    /** The terminal palette background of the whole center, including the tab strip and any lower arc. */
    void setTerminalBackground(Color background) { center.setBackground(background); }

    /** Reapplies the tool window colour after a theme change reset the frame backgrounds. */
    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        hosts.values().forEach(ToolWindowSurface::apply);
    }

    /** Visible stripes frame the terminal; hiding them lets the terminal fill its available area. */
    void setStripesVisible(boolean visible) {
        if (stripesVisible == visible) return;
        stripesVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /** The visible status bar supplies the spacing below the workspace surfaces. */
    void setStatusVisible(boolean visible) {
        if (statusVisible == visible) return;
        statusVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /** The visible title row or toolbar already supplies the spacing above the workspace. */
    void setHeaderVisible(boolean visible) {
        if (headerVisible == visible) return;
        headerVisible = visible;
        refreshGutters();
        revalidate(); repaint();
    }

    /**
     * Exterior gutters are 6px on each edge of the area a frame touches, except where the header, a stripe or the
     * status bar already supplies the spacing; facing edges have none, because the 6px divider is the gap.
     */
    private void refreshGutters() {
        int top = headerVisible ? 0 : GUTTER, edge = stripesVisible ? 0 : GUTTER, bottom = statusVisible ? 0 : GUTTER;
        boolean bottomShown = state.showing(Side.BOTTOM);
        center.rounded = stripesVisible;
        lowerInset.setVisible(stripesVisible);
        if (stripesVisible) center.gutters(top, 0, bottomShown ? 0 : bottom, 0);
        else center.gutters(0, 0, 0, 0);
        hosts.forEach((slot, host) -> {
            boolean alone = state.active(slot.partner()).isEmpty(), first = !slot.split();
            if (slot.side() == Side.BOTTOM)
                host.gutters(0, first || alone ? edge : 0, bottom, !first || alone ? edge : 0);
            else
                host.gutters(first || alone ? top : 0, slot.side() == Side.LEFT ? edge : 0,
                    (!first || alone) && !bottomShown ? bottom : 0, slot.side() == Side.RIGHT ? edge : 0);
        });
    }

    private Dimension size(Dimension terminal, boolean preferred) {
        var metrics = metrics();
        int gap = metrics.scaled(metrics.divider()), floor = metrics.scaled(metrics.toolWindowMinimum());
        int width = terminal.width, height = terminal.height;
        for (Side side : List.of(Side.LEFT, Side.RIGHT))
            if (state.showing(side)) width += gap + (preferred ? Math.max(floor, metrics.scaled(state.size(side))) : floor);
        if (state.showing(Side.BOTTOM))
            height += gap + (preferred ? Math.max(floor, metrics.scaled(state.size(Side.BOTTOM))) : floor);
        return new Dimension(width, height);
    }

    private static DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }
}
```

- [ ] **Step 4: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolWindowAreaTest'`
Expected: PASS, 8 tests.

- [ ] **Step 5: Move the render and surface tests onto the area**

These two tests drove `WorkspaceRegions` directly; Task 8 deletes it. Replace
`jasper-app/src/test/java/dev/jasper/app/workspace/SidePanelRenderTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.function.Predicate;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Screenshot parity for a side panel: a headless render of a tool window shaped like the Sessions panel
 * (header, search field, buttons, a list with a selected row) shows the theme's own colours.
 */
@ExtendWith(EdtTestExtension.class)
class SidePanelRenderTest {
    /** A side panel with a search field, two buttons and a host list whose rows are panels, like the Remote plugin's. */
    private static final class HostsLike {
        final JTextField search = new JTextField("prod", 16);
        final JButton connect = new JButton("Connect");
        final JButton add = new JButton("New Host");
        final JList<String> list = new JList<>(new String[]{"prod", "stage", "dev"});
        final JPanel header = new JPanel(new BorderLayout(0, 6));
        final JPanel root = new JPanel(new BorderLayout());

        HostsLike() {
            var actions = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
            actions.add(connect); actions.add(add);
            header.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
            header.add(search, BorderLayout.CENTER); header.add(actions, BorderLayout.SOUTH);
            list.setCellRenderer((owner, value, index, selected, focused) -> {
                var row = new JPanel(new BorderLayout());
                row.setOpaque(true);
                row.setBackground(selected ? owner.getSelectionBackground() : owner.getBackground());
                row.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 8));
                var name = new JLabel(value);
                name.setForeground(selected ? owner.getSelectionForeground() : owner.getForeground());
                row.add(name, BorderLayout.CENTER);
                return row;
            });
            // FlatLaf's focus-owner hook: the headless list paints its focused selection colours.
            list.putClientProperty("JComponent.focusOwner", (Predicate<JComponent>) component -> true);
            list.updateUI();
            list.setSelectedIndex(1);
            root.add(header, BorderLayout.NORTH);
            root.add(new JScrollPane(list), BorderLayout.CENTER);
        }
    }

    @Test void aSidePanelShowsTheThemesToolWindowFieldButtonAndListColours() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.INTELLIJ_LIGHT);
            var panel = new HostsLike();
            var area = new ToolWindowArea(new JPanel(), new DockState());
            DockTestSupport.show(area, Slot.LEFT_TOP, panel.root, 280);
            assertThemeColours(area, panel, Theme.INTELLIJ_LIGHT);

            ThemeTestSupport.install(Theme.DARK);
            SwingUtilities.updateComponentTreeUI(area);
            area.refreshTheme();
            assertThemeColours(area, panel, Theme.DARK);
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void assertThemeColours(ToolWindowArea area, HostsLike panel, Theme theme) {
        area.setSize(900, 400);
        LayoutTestSupport.layoutTree(area);
        var image = new BufferedImage(area.getWidth(), area.getHeight(), BufferedImage.TYPE_INT_ARGB);
        var graphics = image.createGraphics();
        try { area.paint(graphics); } finally { graphics.dispose(); }
        String name = theme.name() + " ";
        int gutter = com.formdev.flatlaf.util.UIScale.scale(6);
        var header = area.headerOf(Slot.LEFT_TOP);
        assertThat(panel.root.getX()).as(name + "retains its gutter after theme changes").isEqualTo(gutter);
        assertThat(header.getY()).isEqualTo(gutter);
        assertThat(panel.root.getY()).as(name + "content sits under the header").isEqualTo(gutter + header.getHeight());
        assertThat(pixel(image, area, header, header.getWidth() / 2, header.getHeight() / 2)).as(name + "header has no bar colour")
            .isEqualTo(UIManager.getColor("ToolWindow.background"));

        assertThat(pixel(image, area, panel.header, 12, 2)).as(name + "panel").isEqualTo(UIManager.getColor("ToolWindow.background"));
        var field = panel.search;
        assertThat(pixel(image, area, field, field.getWidth() - 10, field.getHeight() / 2)).as(name + "search field")
            .isEqualTo(UIManager.getColor("TextField.background"));
        for (var button : new JButton[]{panel.connect, panel.add})
            assertThat(pixel(image, area, button, button.getWidth() / 2, 4)).as(name + button.getText())
                .isEqualTo(UIManager.getColor("Button.background"));
        assertThat(pixel(image, area, panel.list, row(panel.list, 1))).as(name + "selected row")
            .isEqualTo(UIManager.getColor("List.selectionBackground"));
        var selected = panel.list.getCellBounds(1, 1);
        assertThat(pixel(image, area, panel.list, selected.width - 5, selected.y))
            .as(name + "rounded composite renderer corner").isEqualTo(panel.list.getBackground());
        assertThat(pixel(image, area, panel.list, row(panel.list, 0))).as(name + "unselected row")
            .isEqualTo(UIManager.getColor("List.background"));
    }

    /** A point near the right end of a row, clear of its text. */
    private static Point row(JList<?> list, int index) {
        Rectangle cell = list.getCellBounds(index, index);
        return new Point(cell.x + cell.width - 12, cell.y + cell.height / 2);
    }

    private static Color pixel(BufferedImage image, JComponent root, Component component, Point at) {
        return pixel(image, root, component, at.x, at.y);
    }

    private static Color pixel(BufferedImage image, JComponent root, Component component, int x, int y) {
        Point point = SwingUtilities.convertPoint(component, x, y, root);
        return new Color(image.getRGB(point.x, point.y), true);
    }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/ToolWindowSurfaceTest.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.appearance.Theme;
import dev.jasper.app.appearance.ThemeTestSupport;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Arrays;
import java.util.List;
import javax.swing.*;
import javax.swing.plaf.ColorUIResource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class ToolWindowSurfaceTest {
    private static ToolWindowArea showing(Slot slot, JComponent content, int size) {
        var area = new ToolWindowArea(new JPanel(), new DockState());
        DockTestSupport.show(area, slot, content, size);
        return area;
    }

    @Test void toolWindowsPaintTheToolWindowColourWhileControlsKeepTheirOwn() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.INTELLIJ_LIGHT);
            var content = new JPanel(new BorderLayout());
            var inner = new JPanel();
            var field = new JTextField("hosts");
            var scroll = new JScrollPane(new JList<>(new String[]{"prod", "stage"}));
            var fixed = new JPanel(); fixed.setBackground(Color.MAGENTA);
            inner.add(field);
            content.add(inner, BorderLayout.NORTH); content.add(scroll); content.add(fixed, BorderLayout.SOUTH);
            var area = showing(Slot.LEFT_TOP, content, 250);

            Color surface = UIManager.getColor("ToolWindow.background");
            assertThat(surface).isEqualTo(Color.WHITE);
            assertThat(UIManager.getColor("Panel.background")).isNotEqualTo(surface);
            assertThat(content.getParent().getBackground()).as("tool window frame").isEqualTo(surface);
            assertThat(area.headerOf(Slot.LEFT_TOP).getBackground()).as("the header has no bar colour").isEqualTo(surface);
            assertThat(content.getBackground()).isEqualTo(surface);
            assertThat(inner.getBackground()).isEqualTo(surface);
            assertThat(scroll.getViewport().getBackground()).isEqualTo(surface);
            assertThat(field.getBackground()).isEqualTo(UIManager.getColor("TextField.background"));
            assertThat(fixed.getBackground()).as("a plugin's own colour").isEqualTo(Color.MAGENTA);
            var later = new JPanel();
            inner.add(later);
            assertThat(later.getBackground()).as("added after the panel opened").isEqualTo(surface);

            ThemeTestSupport.install(Theme.DARK);
            SwingUtilities.updateComponentTreeUI(area);
            area.refreshTheme();
            Color dark = UIManager.getColor("ToolWindow.background");
            assertThat(dark).isEqualTo(new Color(0x191c20));
            for (var component : List.<JComponent>of(content, inner, later, area.headerOf(Slot.LEFT_TOP)))
                assertThat(component.getBackground()).isEqualTo(dark);
            assertThat(fixed.getBackground()).isEqualTo(Color.MAGENTA);
        } finally { UIManager.setLookAndFeel(original); }
    }

    @Test void roundedFramesClipOpaquePluginContentInEverySlotAndTheme() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            for (var theme : List.of(Theme.DARK, Theme.INTELLIJ_LIGHT)) {
                ThemeTestSupport.install(theme);
                for (var slot : Slot.values()) {
                    var plugin = new JPanel(); plugin.setBackground(Color.MAGENTA);
                    var area = showing(slot, plugin, 240);
                    area.setSize(700, 500);
                    dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(area);
                    var host = (JComponent) plugin.getParent();
                    var image = new java.awt.image.BufferedImage(host.getWidth(), host.getHeight(), 2);
                    var graphics = image.createGraphics();
                    try { host.paint(graphics); } finally { graphics.dispose(); }
                    assertThat(new Color(image.getRGB(0, 0))).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                    var insets = host.getInsets();
                    assertThat(new Color(image.getRGB(insets.left, insets.top))).as("opaque plugin cannot square the rounded corner")
                        .isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                    assertThat(new Color(image.getRGB(host.getWidth() / 2, host.getHeight() / 2))).isEqualTo(Color.MAGENTA);
                    assertThat(host.contains(insets.left, insets.top)).isFalse();
                    assertThat(host.contains(host.getWidth() / 2, host.getHeight() / 2)).isTrue();
                }
                renderToolPanels(theme);
            }
        } finally { UIManager.setLookAndFeel(original); }
    }

    private static void renderToolPanels(Theme theme) throws Exception {
        var terminal = new JTextArea("\n  ~ / projects / jasper\n\n  $ ");
        terminal.setBackground(theme.palette().background()); terminal.setForeground(theme.palette().foreground());
        terminal.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 14));
        var area = new ToolWindowArea(terminal, new DockState());
        area.setStripesVisible(true);
        var plugin = new JPanel(new BorderLayout(0, 8));
        plugin.setBorder(BorderFactory.createEmptyBorder(0, 12, 12, 12));
        // The header carries the title now, so the panel starts with its search field.
        var search = new JTextField(); search.putClientProperty("JTextField.placeholderText", "Search hosts…");
        plugin.add(search, BorderLayout.NORTH);
        var list = new JList<>(new String[]{"Local terminal", "staging-01", "production"}); list.setSelectedIndex(1);
        list.setFixedCellHeight(30); var scroll = new JScrollPane(list); scroll.setBorder(BorderFactory.createEmptyBorder());
        plugin.add(scroll); DockTestSupport.show(area, Slot.LEFT_TOP, plugin, 270);
        var bottom = new JPanel(new BorderLayout()); bottom.setBorder(BorderFactory.createEmptyBorder(12, 12, 12, 12));
        bottom.add(new JLabel("No active transfers", SwingConstants.CENTER));
        DockTestSupport.show(area, Slot.BOTTOM_LEFT, bottom, 140);
        var stripe = new ToolStripe(Side.LEFT); stripe.setRailWidth(36);
        stripe.render(List.of(new ToolStripe.Button("sessions", "Sessions", dev.jasper.app.platform.AppIcons.named("SERVER"), Slot.LEFT_TOP, true),
            new ToolStripe.Button("vault", "Vault", dev.jasper.app.platform.AppIcons.named("LOCK"), Slot.LEFT_TOP, false),
            new ToolStripe.Button("transfers", "Transfers", dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.BOTTOM_LEFT, true)), List.of());
        stripe.refreshTheme();
        var right = new ToolStripe(Side.RIGHT); right.setRailWidth(36); right.refreshTheme();
        assertThat(stripe.buttons().getFirst().getSelectedIcon()).as("the stripe retains the original theme-aware icon").isNull();
        var root = new JPanel(new BorderLayout()); root.add(stripe, BorderLayout.WEST); root.add(area); root.add(right, BorderLayout.EAST);
        root.setSize(820, 480);
        dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(root);
        var selectedButton = stripe.buttons().getFirst();
        var buttonImage = new java.awt.image.BufferedImage(selectedButton.getWidth(), selectedButton.getHeight(), 2);
        var bg = buttonImage.createGraphics(); selectedButton.paint(bg); bg.dispose();
        assertThat(new Color(buttonImage.getRGB(4, selectedButton.getHeight() / 2)))
            .as("the stripe uses the active theme's neutral toolbar selection")
            .isEqualTo(UIManager.getColor("ToggleButton.toolbar.selectedBackground"));
        var image = new java.awt.image.BufferedImage(1640, 960, 2); var g = image.createGraphics();
        try { g.scale(2, 2); root.printAll(g); } finally { g.dispose(); }
        var output = java.nio.file.Path.of("build/render-preview"); java.nio.file.Files.createDirectories(output);
        javax.imageio.ImageIO.write(image, "png", output.resolve("tool-panels-" + (theme.dark() ? "dark" : "light") + ".png").toFile());
    }

    @Test void renderersAndEditorsOfListsTablesTreesAndCombosKeepTheColoursTheirOwnerGaveThem() throws Exception {
        var original = UIManager.getLookAndFeel();
        try {
            ThemeTestSupport.install(Theme.INTELLIJ_LIGHT);
            var list = new JList<>(new String[]{"prod", "stage"});
            var table = new JTable(2, 2);
            var tree = new JTree();
            var combo = new JComboBox<>(new String[]{"Password", "Key"});
            var content = new JPanel();
            content.add(new JScrollPane(list)); content.add(new JScrollPane(table)); content.add(new JScrollPane(tree)); content.add(combo);
            showing(Slot.LEFT_TOP, content, 250);
            assertThat(content.getBackground()).isEqualTo(UIManager.getColor("ToolWindow.background"));

            // A renderer painted with the owner's selection colour, as the Remote plugin's host rows are.
            var selection = new ColorUIResource(UIManager.getColor("List.selectionBackground"));
            for (JComponent owner : List.<JComponent>of(list, table, tree, combo)) {
                var panes = Arrays.stream(owner.getComponents()).filter(CellRendererPane.class::isInstance).toList();
                assertThat(panes).as(owner.getClass().getSimpleName() + " renderer pane").isNotEmpty();
                for (var pane : panes) {
                    var row = new JPanel(); row.setBackground(selection);
                    ((CellRendererPane) pane).add(row);
                    assertThat(row.getBackground()).as(owner.getClass().getSimpleName() + " renderer").isEqualTo(selection);
                }
                var editor = new JPanel(); editor.setBackground(selection);
                owner.add(editor);
                assertThat(editor.getBackground()).as(owner.getClass().getSimpleName() + " editor").isEqualTo(selection);
            }
        } finally { UIManager.setLookAndFeel(original); }
    }
}
```

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.SidePanelRenderTest' --tests 'dev.jasper.app.workspace.ToolWindowSurfaceTest'`
Expected: PASS. `build/render-preview/tool-panels-{dark,light}.png` now show both stripes, headers and a full-width
bottom area (look at them; they are not GUI launches).

- [ ] **Step 6: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/{ToolWindowAreaTest,DockTestSupport,SidePanelRenderTest,ToolWindowSurfaceTest}.java
git commit -F - <<'EOF'
feat(workspace): add the tool window area over the dock geometry

ToolWindowArea lays out the terminal column and one rounded frame per showing
slot from DockGeometry, each frame under its header, with today's gutter and
rounded-center rules. It owns the dividers: a drag previews on a copy of the
state and changes the real state once, on release. The render and surface
tests move onto it.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 8: Wire the window: two stripes, the area, and `WindowContributions` over `DockState`

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (eight hunks, applied by the script in Step 3)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (whole file; drops the Task 3 and 4 bridges)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/package-info.java` (allowed dependencies)
- Delete: `jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java`,
  `jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java`,
  `jasper-app/src/main/java/dev/jasper/app/contributions/PanelRegion.java`,
  `jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRegionsTest.java` (its cases live in `ToolWindowAreaTest`)
- Modify: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java` (whole file),
  `RailWidthTest.java` (whole file), `DockTestSupport.java` (whole file)
- Modify (script in Step 6): `GraphiteChromeTest`, `BarHeightTest`, `MacTitleBarTest`, `WindowCommandPaletteTest`,
  `ThemeControllerTest`, `WorkspaceThemeTest`, `PluginPaneAppearanceTest`

**Interfaces:**
- Consumes: everything from Tasks 1–7; `UiState.Panel`, `UiState.Dock`, `UiState.panels()` (Task 4);
  `PanelEntry.defaultSlot()` (Task 3).
- Produces (package-private on `WindowContent`): `ToolWindowArea area()`, `DockState dock()`,
  `ToolStripe leftStripe()`, `ToolStripe rightStripe()`; `syncRailVisibility()` shows both stripes while either has a
  button; `rail()` and `regions()` are gone. `WindowContributions` registers one `DockState` listener (`docked`) that
  builds instances, calls `area().sync()`, renders both stripes, notifies `PanelSite`s (hidden first, then shown),
  returns focus to the terminal when something hid, and saves. Plan 2 reuses `dock()`, `area()`, the stripes and the
  listener.

- [ ] **Step 1: Write the failing tests**

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/DockTestSupport.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Slot;
import java.util.Locale;
import javax.swing.JComponent;

/** Puts plain content into a dock without a plugin, as the old {@code regions().show(...)} did. Test only. */
final class DockTestSupport {
    private DockTestSupport() { }

    /** Shows content in a slot, with its side at size; the same slot again replaces the content. */
    static void show(ToolWindowArea area, Slot slot, JComponent content, int size) {
        String id = id(slot);
        DockState state = area.state();
        area.setContent(id, slot.displayName(), content);
        if (state.slot(id).isEmpty()) state.add(id, slot);
        state.resize(slot.side(), size);
        state.show(id);
        area.sync();
    }

    static void hide(ToolWindowArea area, Slot slot) {
        area.state().hide(id(slot));
        area.sync();
    }

    /** The same in a window's own area; a connected window's contributions ignore these ids. */
    static void show(WindowContent owner, Slot slot, JComponent content, int size) { show(owner.area(), slot, content, size); }

    static void hide(WindowContent owner, Slot slot) { hide(owner.area(), slot); }

    /** The id the helper gives a slot's content, such as {@code test.left-top}. */
    static String id(Slot slot) { return "test." + slot.name().toLowerCase(Locale.ROOT).replace('_', '-'); }
}
```

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/WindowPanelsTest.java` with:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JSeparator;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

class WindowPanelsTest {
    @TempDir Path dir;

    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); edt(() -> new dev.jasper.app.appearance.ThemeController()); }

    private static WindowContent window() { return DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>())); }

    private static void toggle(Contributions model, WindowContent owner, String id) {
        model.requestPanel(new Contributions.PanelRequest(owner.id(), id, Contributions.PanelRequest.Op.TOGGLE));
    }

    private static List<String> tips(ToolStripe stripe) { return stripe.buttons().stream().map(AbstractButton::getToolTipText).toList(); }

    private static Rectangle in(JComponent root, Component component) {
        return SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), root);
    }

    private static void mouse(Component target, int id, int x, int y) {
        int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
        int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
        target.dispatchEvent(new MouseEvent(target, id, System.currentTimeMillis(), modifiers, x, y, 1, false, button));
    }

    @Test void anUnconnectedOrEmptyWindowHasNoStripesAndTheOldLayout() throws Exception {
        edt(() -> {
            WindowContent owner = window();
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            owner.connectContributions(new Contributions());
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
        });
    }

    @Test void panelsAreLazyPerWindowToggleFromTheStripeAndShareASlotOneAtATime() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<PanelSite> sites = new ArrayList<>();
            List<String> events = new ArrayList<>();
            var hostsView = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> {
                sites.add(site);
                site.onVisibility(visible -> events.add("hosts:" + visible));
                site.onClosed(() -> events.add("hosts:closed"));
                return hostsView;
            });
            PanelEntry files = model.addPanel("dev.x.files", "Files", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("files"));
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).as("an empty right stripe still shows").isTrue();
            assertThat(tips(owner.leftStripe())).containsExactly("Hosts", "Files");
            assertThat(owner.rightStripe().buttons()).isEmpty();
            assertThat(sites).as("no instance until first shown").isEmpty();

            owner.leftStripe().buttons().get(0).doClick();
            assertThat(sites).singleElement().satisfies(site -> {
                assertThat(site.windowId()).isEqualTo(owner.id());
                assertThat(site.visible()).isTrue();
            });
            assertThat(SwingUtilities.isDescendingFrom(hostsView, owner)).isTrue();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(hostsView);
            assertThat(owner.area().headerOf(Slot.LEFT_TOP).titleLabel().getText()).isEqualTo("Hosts");
            assertThat(owner.leftStripe().buttons().get(0).isSelected()).isTrue();

            toggle(model, owner, "dev.x.files");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNotSameAs(hostsView);
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(false, true);
            toggle(model, owner, "dev.x.hosts");
            assertThat(sites).as("the instance is reused").hasSize(1);
            sites.get(0).hide();
            assertThat(owner.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(events).containsExactly("hosts:true", "hosts:false", "hosts:true", "hosts:false");

            model.requestPanel(new Contributions.PanelRequest(java.util.UUID.randomUUID(), "dev.x.hosts", Contributions.PanelRequest.Op.SHOW));
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("another window's request").isNull();

            files.close();
            assertThat(owner.leftStripe().buttons()).hasSize(1);
        });
    }

    @Test void aSplitPanelOpensUnderTheFirstHalfAndBothShowAtOnce() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var sessions = new JLabel("sessions"); var sftp = new JLabel("sftp");
            model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> sessions);
            model.addPanel("dev.x.sftp", "SFTP", new ImageIcon(), Slot.LEFT_BOTTOM, site -> sftp);
            toggle(model, owner, "dev.x.sessions");
            toggle(model, owner, "dev.x.sftp");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(sessions);
            assertThat(owner.area().content(Slot.LEFT_BOTTOM)).isSameAs(sftp);
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            Rectangle upper = in(owner, sessions.getParent()), lower = in(owner, sftp.getParent());
            assertThat(lower.x).isEqualTo(upper.x);
            assertThat(lower.width).isEqualTo(upper.width);
            assertThat(lower.y).as("the second half sits under the first, past the divider").isEqualTo(upper.y + upper.height + UIScale.scale(6));
            assertThat(owner.leftStripe().buttons()).extracting(AbstractButton::isSelected).containsExactly(true, true);
            assertThat(Arrays.stream(owner.leftStripe().getComponents()).filter(JSeparator.class::isInstance))
                .as("the second group sits under a separator").hasSize(1);
        });
    }

    @Test void theStripeMenuMovesAWindowToAnyOfTheSixSlotsAndANewWindowRestoresIt() throws Exception {
        edt(() -> {
            var model = new Contributions();
            Path file = dir.resolve("ui-state.toml");
            WindowContent first = window();
            first.connectContributions(model, UiState.load(file));
            List<String> created = new ArrayList<>();
            List<String> visibility = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> {
                created.add("hosts");
                site.onVisibility(visible -> visibility.add("hosts:" + visible));
                return new JLabel("hosts");
            });
            toggle(model, first, "dev.x.hosts");
            var menu = first.leftStripe().buttons().getFirst().getComponentPopupMenu();
            ToolWindowHeaderTest.item(menu, "Right Bottom").doClick();
            assertThat(first.area().content(Slot.LEFT_TOP)).isNull();
            assertThat(first.area().content(Slot.RIGHT_BOTTOM)).as("a move keeps the window showing").isNotNull();
            assertThat(first.leftStripe().buttons()).isEmpty();
            assertThat(tips(first.rightStripe())).containsExactly("Hosts");
            assertThat(visibility).as("a move is not a change of visibility").containsExactly("hosts:true");
            assertThat(UiState.load(file).panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.RIGHT_BOTTOM, 0, true));

            WindowContent second = window();
            second.connectContributions(model, UiState.load(file));
            assertThat(second.area().content(Slot.RIGHT_BOTTOM)).as("restored visible in the right side's lower half").isNotNull();
            assertThat(created).hasSize(2);
        });
    }

    @Test void theHeaderMovesAndHidesItsWindow() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var view = new JTextField("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> view);
            toggle(model, owner, "dev.x.hosts");
            ToolWindowHeaderTest.item(owner.area().headerOf(Slot.LEFT_TOP).menu(), "Bottom Left").doClick();
            assertThat(owner.area().content(Slot.BOTTOM_LEFT)).isSameAs(view);
            assertThat(owner.area().headerOf(Slot.LEFT_TOP)).isNull();
            owner.area().headerOf(Slot.BOTTOM_LEFT).hideButton().doClick();
            assertThat(owner.area().content(Slot.BOTTOM_LEFT)).isNull();
            assertThat(owner.dock().visible("dev.x.hosts")).isFalse();
            assertThat(owner.leftStripe().buttons().getFirst().isSelected()).isFalse();
            assertThat(view.getParent()).as("hidden content leaves the tree").isNull();
        });
    }

    @Test void aFailedFactoryShowsAPlaceholderAndRemovalOrCloseNotifiesTheInstance() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.broken", "Broken", new ImageIcon(), Slot.BOTTOM_LEFT, site -> null);
            PanelEntry live = model.addPanel("dev.x.live", "Live", new ImageIcon(), Slot.RIGHT_TOP, site -> {
                site.onClosed(() -> events.add("closed"));
                return new JLabel("live");
            });
            toggle(model, owner, "dev.x.broken");
            JComponent placeholder = owner.area().content(Slot.BOTTOM_LEFT);
            assertThat(placeholder).isInstanceOf(JLabel.class);
            assertThat(((JLabel) placeholder).getText()).contains("could not be loaded");

            toggle(model, owner, "dev.x.live");
            live.close();
            assertThat(owner.area().content(Slot.RIGHT_TOP)).isNull();
            assertThat(owner.rightStripe().buttons()).isEmpty();
            assertThat(events).containsExactly("closed");
        });
    }

    @Test void railActionsAndTheRailToggleCommandHideAndShowBothStripes() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            owner.setActive(true);
            List<Contributions.Invocation> seen = new ArrayList<>();
            model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), Optional.empty(), seen::add);
            model.addRailAction("dev.x.open");
            model.addRailAction("dev.x.never-registered");
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).isTrue();
            assertThat(owner.leftStripe().buttons()).singleElement().satisfies(button -> {
                assertThat(button.getToolTipText()).isEqualTo("Open Manager");
                button.doClick();
            });
            assertThat(owner.rightStripe().buttons()).as("rail actions live on the left stripe").isEmpty();
            assertThat(seen).hasSize(1);

            owner.dispatchCommand(owner.commands().find("view.rail").orElseThrow());
            assertThat(owner.leftStripe().isVisible()).isFalse();
            assertThat(owner.rightStripe().isVisible()).isFalse();
            assertThat(state.railVisible()).isFalse();
            owner.setRailVisible(true);
            assertThat(owner.leftStripe().isVisible()).isTrue();
            assertThat(owner.rightStripe().isVisible()).isTrue();
        });
    }

    @Test void hiddenPanelKeepsItsComponentAndHandlersAcrossADelegateThemeUpdate() throws Exception {
        edt(() -> {
            var themes = new dev.jasper.app.appearance.ThemeController(dev.jasper.app.appearance.Theme.DARK);
            var owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()), themes);
            var model = new Contributions(); owner.connectContributions(model, UiState.inMemory());
            var clicks = new java.util.concurrent.atomic.AtomicInteger();
            var button = new javax.swing.JButton("Plugin action");
            button.addActionListener(event -> clicks.incrementAndGet());
            var instances = new java.util.concurrent.atomic.AtomicInteger();
            model.addPanel("dev.x.hidden", "Hidden panel", new ImageIcon(), Slot.LEFT_TOP, site -> {
                instances.incrementAndGet(); return button;
            });
            toggle(model, owner, "dev.x.hidden");
            toggle(model, owner, "dev.x.hidden");
            owner.applyTheme(owner.theme(), true);
            toggle(model, owner, "dev.x.hidden");
            assertThat(owner.area().content(Slot.LEFT_TOP)).isSameAs(button);
            assertThat(instances.get()).isEqualTo(1);
            button.doClick(); assertThat(clicks.get()).isEqualTo(1);
        });
    }

    @Test void theStripesAndSidePanelsReachTheToolbarAndTheBottomSpansStripeToStripe() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            var left = new JLabel("left"); var right = new JLabel("right"); var bottom = new JLabel("bottom");
            model.addPanel("dev.x.left", "Left", new ImageIcon(), Slot.LEFT_TOP, site -> left);
            model.addPanel("dev.x.right", "Right", new ImageIcon(), Slot.RIGHT_TOP, site -> right);
            model.addPanel("dev.x.bottom", "Bottom", new ImageIcon(), Slot.BOTTOM_LEFT, site -> bottom);
            toggle(model, owner, "dev.x.left"); toggle(model, owner, "dev.x.right"); toggle(model, owner, "dev.x.bottom");
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);

            Rectangle toolbar = in(owner, owner.toolbar()), leftStripe = in(owner, owner.leftStripe()), rightStripe = in(owner, owner.rightStripe());
            Rectangle strip = in(owner, owner.windowTabs()), deck = in(owner, owner.tabStrip());
            Rectangle leftBounds = in(owner, left.getParent()), rightBounds = in(owner, right.getParent()), bottomBounds = in(owner, bottom.getParent());
            int underToolbar = toolbar.y + toolbar.height;
            assertThat(leftStripe.y).as("the left stripe starts under the toolbar").isEqualTo(underToolbar);
            assertThat(rightStripe.y).as("so does the right stripe").isEqualTo(underToolbar);
            assertThat(leftBounds.y).as("the left panel starts under the toolbar").isEqualTo(underToolbar);
            assertThat(rightBounds.y).as("the right panel starts under the toolbar").isEqualTo(underToolbar);
            assertThat(strip.y).isEqualTo(underToolbar);
            assertThat(strip.x).as("the tabs start right of the left panel").isGreaterThanOrEqualTo(leftBounds.x + leftBounds.width);
            assertThat(strip.x + strip.width).as("the tabs end left of the right panel").isLessThanOrEqualTo(rightBounds.x);
            assertThat(strip.x).isEqualTo(deck.x);
            assertThat(strip.width).isEqualTo(deck.width);
            assertThat(deck.y).as("the terminal sits right under its tabs").isEqualTo(strip.y + strip.height);
            assertThat(bottomBounds.y).as("the bottom area sits under the terminal").isGreaterThan(deck.y + deck.height);
            assertThat(leftBounds.y + leftBounds.height).as("and under the side panels").isLessThan(bottomBounds.y);
            assertThat(bottomBounds.x).as("it spans stripe to stripe").isEqualTo(leftStripe.x + leftStripe.width);
            assertThat(bottomBounds.x + bottomBounds.width).isEqualTo(rightStripe.x);
        });
    }

    @Test void draggingADividerSavesTheNewSideSize() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            toggle(model, owner, "dev.x.hosts");
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            var divider = owner.area().divider(DockGeometry.Handle.LEFT_WIDTH);
            assertThat(divider.getX()).isEqualTo(260);
            mouse(divider, MouseEvent.MOUSE_PRESSED, 2, 50);
            mouse(divider, MouseEvent.MOUSE_DRAGGED, 42, 50);
            mouse(divider, MouseEvent.MOUSE_RELEASED, 42, 50);
            assertThat(owner.dock().size(Side.LEFT)).isEqualTo(300);
        });
        assertThat(UiState.load(file).dock().leftWidth()).isEqualTo(300);
    }

    @Test void aVersionOneLayoutOpensWhereItWas() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            rail_visible = true

            [panels."dev.x.hosts"]
            region = "LEFT"
            visible = true
            size = 300

            [windows."dev.x.manager"]
            x = 10
            y = 20
            width = 640
            height = 480
            """);
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            var hosts = new JLabel("hosts");
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.RIGHT_TOP, site -> hosts);
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("the saved place wins over the default").isSameAs(hosts);
            assertThat(owner.dock().size(Side.LEFT)).isEqualTo(300);
            owner.setSize(1200, 800);
            LayoutTestSupport.layoutTree(owner);
            assertThat(hosts.getParent().getWidth()).isEqualTo(UIScale.scale(300));
        });
        assertThat(Files.readString(file)).startsWith("version = 2\n")
            .contains("left_width = 300", "slot = \"LEFT_TOP\"", "[windows.\"dev.x.manager\"]");
    }

    @Test void theSavedOrderSurvivesAnyRegistrationOrder() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        var saved = UiState.load(file);
        saved.putPanel("dev.x.a", new UiState.Panel(Slot.LEFT_TOP, 0, false));
        saved.putPanel("dev.x.b", new UiState.Panel(Slot.LEFT_TOP, 1, false));
        saved.putPanel("dev.x.c", new UiState.Panel(Slot.LEFT_TOP, 2, false));
        saved.save();
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            for (String id : List.of("dev.x.c", "dev.x.b", "dev.x.a"))
                model.addPanel(id, id.substring(6).toUpperCase(java.util.Locale.ROOT), new ImageIcon(), Slot.RIGHT_TOP, site -> new JLabel(id));
            model.addPanel("dev.x.new", "NEW", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("new"));
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.a", "dev.x.b", "dev.x.c", "dev.x.new");
            assertThat(tips(owner.leftStripe())).containsExactly("A", "B", "C", "NEW");
        });
        assertThat(UiState.load(file).panel("dev.x.new")).hasValue(new UiState.Panel(Slot.LEFT_TOP, 3, false));
    }

    @Test void aReloadedPluginGoesBackWhereItWas() throws Exception {
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.inMemory());
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.a", "A", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("a"));
            PanelEntry b = model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onClosed(() -> events.add("b:closed"));
                return new JLabel("b");
            });
            model.addPanel("dev.x.c", "C", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("c"));
            toggle(model, owner, "dev.x.b");
            b.close();
            assertThat(events).containsExactly("b:closed");
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("its frame goes").isNull();
            assertThat(tips(owner.leftStripe())).containsExactly("A", "C");

            var again = new JLabel("b again");
            model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.RIGHT_TOP, site -> again);
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.a", "dev.x.b", "dev.x.c");
            assertThat(owner.area().content(Slot.LEFT_TOP)).as("and it shows again, as it did").isSameAs(again);
        });
    }

    @Test void aMigratedLayoutFollowsRegistrationOrder() throws Exception {
        Path file = dir.resolve("ui-state.toml");
        Files.writeString(file, """
            version = 1
            [panels."dev.x.a"]
            region = "LEFT"
            visible = false
            size = 260
            [panels."dev.x.b"]
            region = "LEFT"
            visible = false
            size = 260
            """);
        edt(() -> {
            var model = new Contributions();
            WindowContent owner = window();
            owner.connectContributions(model, UiState.load(file));
            model.addPanel("dev.x.b", "B", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("b"));
            model.addPanel("dev.x.c", "C", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("c"));
            model.addPanel("dev.x.a", "A", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("a"));
            assertThat(owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.b", "dev.x.c", "dev.x.a");
        });
    }

    @Test void eachWindowKeepsItsOwnLayoutAndANewWindowTakesTheLastSaved() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent first = window(), second = window();
            first.connectContributions(model, state);
            second.connectContributions(model, state);
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.LEFT_TOP, site -> new JLabel("hosts"));
            toggle(model, first, "dev.x.hosts");
            ToolWindowHeaderTest.item(first.area().headerOf(Slot.LEFT_TOP).menu(), "Right Top").doClick();
            assertThat(first.dock().slot("dev.x.hosts")).hasValue(Slot.RIGHT_TOP);
            assertThat(second.dock().slot("dev.x.hosts")).as("the other window keeps its own layout").hasValue(Slot.LEFT_TOP);
            assertThat(second.dock().visible("dev.x.hosts")).isFalse();

            WindowContent third = window();
            third.connectContributions(model, state);
            assertThat(third.dock().slot("dev.x.hosts")).as("a new window opens with the last saved layout").hasValue(Slot.RIGHT_TOP);
            assertThat(third.dock().visible("dev.x.hosts")).isTrue();
        });
    }

    @Test void closingTheWindowSavesShowingPanelsAsShowing() throws Exception {
        edt(() -> {
            var model = new Contributions();
            var state = UiState.inMemory();
            WindowContent owner = window();
            owner.connectContributions(model, state);
            List<String> events = new ArrayList<>();
            model.addPanel("dev.x.hosts", "Hosts", new ImageIcon(), Slot.BOTTOM_RIGHT, site -> {
                site.onVisibility(visible -> events.add("visible:" + visible));
                site.onClosed(() -> events.add("closed"));
                return new JLabel("hosts");
            });
            toggle(model, owner, "dev.x.hosts");
            owner.close();
            assertThat(state.panel("dev.x.hosts")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 0, true));
            assertThat(events).as("closing is not hiding").containsExactly("visible:true", "closed");
            assertThat(owner.leftStripe().buttons()).isEmpty();
        });
    }
}
```

- [ ] **Step 2: Run them to make sure they fail**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL — `cannot find symbol: method leftStripe()`, `area()`, `dock()` on `WindowContent`.

- [ ] **Step 3: Wire `WindowContent`**

Save this script as `/tmp/dock-window-content.py` and run it from the worktree root with
`python3 /tmp/dock-window-content.py`. Each hunk must match exactly once or nothing is written:

```python
import pathlib

# Run from the worktree root. Every replacement must match exactly once, or the script stops before writing.
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java")
pairs = [
    ("""import dev.jasper.app.contributions.Contributions;
""", """import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
"""),
    ("""    private WindowContributions contributed;
    private final WorkspaceRegions regions;
    private final WindowRail rail;
""", """    private WindowContributions contributed;
    /** Where this window's tool windows dock: its contributions change it, its area lays it out. */
    private final DockState dock = new DockState();
    private final ToolWindowArea area;
    private final ToolStripe leftStripe = new ToolStripe(Side.LEFT), rightStripe = new ToolStripe(Side.RIGHT);
"""),
    ("""        // The tabs belong to the terminal column, so the rail and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(windowTabs, BorderLayout.NORTH); terminalColumn.add(tabs, BorderLayout.CENTER);
        regions = new WorkspaceRegions(terminalColumn);
        regions.setStatusVisible(chrome.status().isVisible());
        regions.setHeaderVisible(true);
        rail = new WindowRail();
        rail.setVisible(false);
        var body = new JPanel(new BorderLayout());
        body.add(rail, BorderLayout.WEST); body.add(regions, BorderLayout.CENTER);
""", """        // The tabs belong to the terminal column, so the stripes and the side panels reach the toolbar.
        var terminalColumn = new JPanel(new BorderLayout());
        // The rounded host paints behind this layout-only container, including partial tab repaints.
        terminalColumn.setOpaque(false);
        terminalColumn.add(windowTabs, BorderLayout.NORTH); terminalColumn.add(tabs, BorderLayout.CENTER);
        area = new ToolWindowArea(terminalColumn, dock);
        area.setStatusVisible(chrome.status().isVisible());
        area.setHeaderVisible(true);
        leftStripe.setVisible(false);
        rightStripe.setVisible(false);
        var body = new JPanel(new BorderLayout());
        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
"""),
    ("""    WorkspaceRegions regions() { return regions; }
    WindowRail rail() { return rail; }
""", """    ToolWindowArea area() { return area; }
    DockState dock() { return dock; }
    ToolStripe leftStripe() { return leftStripe; }
    ToolStripe rightStripe() { return rightStripe; }
"""),
    ("""    /** The rail shows only when it has contributed items and the user has not hidden it. */
    void syncRailVisibility() {
        boolean wanted = !rail.empty() && uiState.railVisible();
        regions.setRailVisible(wanted);
        if (rail.isVisible() != wanted) { rail.setVisible(wanted); revalidate(); repaint(); }
    }
""", """    /**
     * Both stripes show together while either has a tool window or an action (an empty right stripe included: it is a
     * place to move windows to), unless the user hid them with View > Rail.
     */
    void syncRailVisibility() {
        boolean wanted = !(leftStripe.empty() && rightStripe.empty()) && uiState.railVisible();
        area.setStripesVisible(wanted);
        if (leftStripe.isVisible() != wanted || rightStripe.isVisible() != wanted) {
            leftStripe.setVisible(wanted);
            rightStripe.setVisible(wanted);
            revalidate(); repaint();
        }
    }
"""),
    ("""            rail.refreshTheme();
            regions.refreshTheme();
            regions.setTerminalBackground(theme.palette().background());
""", """            leftStripe.refreshTheme();
            rightStripe.refreshTheme();
            area.refreshTheme();
            area.setTerminalBackground(theme.palette().background());
"""),
    ("""        rail.setRailWidth(width);
""", """        leftStripe.setRailWidth(width);
        rightStripe.setRailWidth(width);
"""),
    ("""        chrome.setStatusVisible(visible); regions.setStatusVisible(visible);
""", """        chrome.setStatusVisible(visible); area.setStatusVisible(visible);
"""),
]
text = path.read_text(encoding="utf-8")
for old, new in pairs:
    count = text.count(old)
    if count != 1: raise SystemExit(f"expected one match, found {count}: {old.strip()[:70]}")
    text = text.replace(old, new)
for gone in ("regions", "WindowRail", "WorkspaceRegions", "rail."):
    if gone in text: raise SystemExit(f"still names {gone}")
path.write_text(text, encoding="utf-8")
print("updated", path)
```

Expected output: `updated jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java`.

- [ ] **Step 4: Rewrite `WindowContributions` over the dock state**

Replace `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` with:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.commands.Command;
import dev.jasper.app.config.KeyBindings;
import dev.jasper.app.contributions.ActionEntry;
import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.lifecycle.Subscription;
import dev.jasper.app.palette.PaletteScope;
import dev.jasper.app.persistence.UiState;
import java.awt.event.ActionEvent;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

/**
 * This window's Swing side of the application-wide contributions model: one {@link Action} per
 * contributed action, registered as a palette command and bound to its shortcut, kept in step with the
 * model. Chrome and status rendering hang off the same change notifications. Panels dock in the window's
 * {@link DockState}; the one listener on it builds what starts showing, lays the area out, renders the
 * stripes, tells the panels and saves. EDT only.
 */
final class WindowContributions implements AutoCloseable {
    private static final System.Logger LOG = System.getLogger(WindowContributions.class.getName());
    private final WindowContent owner;
    private final Contributions model;
    private final Map<String, Action> actions = new LinkedHashMap<>();
    private final Map<String, Subscription> commands = new LinkedHashMap<>();
    private final Map<PaletteScope, Subscription> scopes = new LinkedHashMap<>();
    private Subscription listening;
    private Subscription paletteRequests;

    /** One panel's instance in this window, built the first time it shows; where it docks lives in the dock state. */
    private static final class PanelInstance {
        final PanelEntry entry;
        PanelSite site;
        JComponent component;
        PanelInstance(PanelEntry entry) { this.entry = entry; }
    }

    private final UiState state;
    private final DockState dock;
    private final Consumer<DockState.Change> docked = this::docked;
    private final Map<String, PanelInstance> panels = new LinkedHashMap<>();
    private Subscription panelRequests;

    WindowContributions(WindowContent owner, Contributions model, UiState state) {
        this.owner = owner;
        this.model = model;
        this.state = state;
        this.dock = owner.dock();
        syncActions();
        owner.chrome().connect(this);
        renderStatus();
        restoreSizes();
        for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe())) {
            stripe.onToggle = this::togglePanel;
            stripe.onMove = this::movePanel;
            stripe.onHide = this::hidePanel;
        }
        owner.area().onMove = this::movePanel;
        owner.area().onHide = this::hidePanel;
        dock.addListener(docked);
        panelRequests = model.onPanelRequest(this::requested);
        paletteRequests = model.onPaletteRequest(this::requestedPalette);
        syncScopes();
        syncPanels();
        renderStripes();
        listening = model.onChanged(this::changed);
    }

    Contributions model() { return model; }

    /** The window's action for a contributed id, or null when the contributor removed it. */
    Action action(String id) { return actions.get(id); }

    /** Contributed actions in registration order, which is plugin load order: the keybinding precedence. */
    List<KeyBindings.Extension> extensions() {
        List<KeyBindings.Extension> result = new ArrayList<>();
        for (ActionEntry entry : model.actions())
            if (KeyBindings.extensionId(entry.id())) result.add(entry.extension());
        return result;
    }

    void applyAccelerators() {
        actions.forEach((id, action) -> action.putValue(Action.ACCELERATOR_KEY, owner.bindings().strokeFor(id).orElse(null)));
    }

    /** From a shortcut, the palette, a menu, the toolbar or a status item. */
    void invoke(String id) {
        if (dev.jasper.app.platform.WindowInput.blocked(owner)) return;
        if (owner.closed() || owner.commandPalette() != null && owner.commandPalette().isOpen()) return;
        Optional<java.util.UUID> pane = Optional.ofNullable(owner.currentPane()).map(TerminalPane::id);
        model.action(id).ifPresent(entry -> entry.invoke(new Contributions.Invocation(owner.id(), pane)));
    }

    /** Whether the contributed action exists and is enabled, so its shortcut takes the key. */
    boolean enabled(String id) { return model.action(id).map(ActionEntry::enabled).orElse(false); }

    /** Whether focus is inside this window's instance of the panel. */
    boolean panelContains(String panelId, java.awt.Component focus) {
        PanelInstance panel = panels.get(panelId);
        return focus != null && panel != null && panel.component != null && SwingUtilities.isDescendingFrom(focus, panel.component);
    }

    private void renderStatus() { owner.status().setContributed(model.status(), this::action); }

    private void requestedPalette(Contributions.PaletteRequest request) {
        if (!request.windowId().equals(owner.id()) || owner.commandPalette() == null) return;
        owner.commandPalette().open(request.scopeId(), request.query().orElse(null), request.rowId().orElse(null));
    }

    /** Registers contributed scopes this window does not have yet and drops the ones that left the model. */
    private void syncScopes() {
        List<PaletteScope> current = model.scopes();
        for (PaletteScope scope : List.copyOf(scopes.keySet()))
            if (!current.contains(scope)) scopes.remove(scope).close();
        for (PaletteScope scope : current) {
            if (scopes.containsKey(scope)) continue;
            try { scopes.put(scope, owner.scopes().register(scope)); }
            catch (IllegalArgumentException clash) {
                LOG.log(System.Logger.Level.WARNING, "A contributed scope is not shown in this window: " + clash.getMessage());
            }
        }
    }

    private void requested(Contributions.PanelRequest request) {
        if (!request.windowId().equals(owner.id())) return;
        switch (request.op()) {
            case SHOW -> showPanel(request.panelId());
            case HIDE -> hidePanel(request.panelId());
            case TOGGLE -> togglePanel(request.panelId());
        }
    }

    private void togglePanel(String id) { if (panels.containsKey(id)) dock.toggle(id); }

    private void showPanel(String id) { if (panels.containsKey(id)) dock.show(id); }

    private void hidePanel(String id) { if (panels.containsKey(id)) dock.hide(id); }

    /** From a Move to menu: the end of the target slot's group, keeping visibility. */
    private void movePanel(String id, Slot slot) {
        if (!panels.containsKey(id) || dock.slot(id).filter(slot::equals).isPresent()) return;
        dock.move(id, slot, dock.ids(slot).size());
    }

    /** The window opens at the saved sizes and ratios; nothing listens yet, so this saves nothing. */
    private void restoreSizes() {
        UiState.Dock saved = state.dock();
        dock.resize(Side.LEFT, saved.leftWidth());
        dock.resize(Side.RIGHT, saved.rightWidth());
        dock.resize(Side.BOTTOM, saved.bottomHeight());
        dock.setRatio(Side.LEFT, saved.leftRatio());
        dock.setRatio(Side.RIGHT, saved.rightRatio());
        dock.setRatio(Side.BOTTOM, saved.bottomRatio());
    }

    /**
     * Docks new panels where they were saved (a saved slot wins over the default) or at the end of their default
     * slot's group, showing those that showed; undocks the panels that left the model and discards their instances.
     */
    private void syncPanels() {
        Map<String, PanelEntry> current = new LinkedHashMap<>();
        for (PanelEntry entry : model.panels()) current.put(entry.id(), entry);
        for (String id : List.copyOf(panels.keySet())) {
            if (current.containsKey(id)) continue;
            PanelInstance gone = panels.remove(id);
            dock.remove(id);
            owner.area().removeContent(id);
            if (gone.site != null) gone.site.notifyClosed();
        }
        for (PanelEntry entry : current.values()) {
            if (panels.containsKey(entry.id())) continue;
            panels.put(entry.id(), new PanelInstance(entry));
            Optional<UiState.Panel> saved = state.panel(entry.id());
            Slot slot = saved.map(UiState.Panel::slot).orElse(entry.defaultSlot());
            dock.add(entry.id(), slot, insertionIndex(slot, saved.map(UiState.Panel::order).orElse(UiState.Panel.LAST)));
            if (saved.map(UiState.Panel::visible).orElse(false)) dock.show(entry.id());
        }
    }

    /** Where a panel goes back into its slot: before the panels saved after it. One never placed goes last. */
    private int insertionIndex(Slot slot, int order) {
        List<String> ids = dock.ids(slot);
        if (order >= UiState.Panel.LAST) return ids.size();
        int index = 0;
        for (String other : ids)
            if (state.panel(other).map(UiState.Panel::order).orElse(UiState.Panel.LAST) < order) index++;
        return index;
    }

    /** The one place a dock change takes effect: build what now shows, lay out, render the stripes, tell the panels, save. */
    private void docked(DockState.Change change) {
        for (String id : change.shown()) build(id);
        if (change.layout()) owner.area().sync();
        if (change.stripes()) renderStripes();
        for (String id : change.hidden()) notifyVisibility(id, false);
        for (String id : change.shown()) notifyVisibility(id, true);
        // Hiding a window by any route returns focus to the terminal.
        if (!change.hidden().isEmpty()) owner.focusSelected();
        persist();
    }

    /** Builds the panel's instance the first time it shows in this window; a failing factory yields a placeholder. */
    private void build(String id) {
        PanelInstance panel = panels.get(id);
        if (panel == null || panel.component != null) return;
        panel.site = new PanelSite(owner.id(), () -> showPanel(id), () -> hidePanel(id), () -> dock.visible(id));
        JComponent built = null;
        try { built = panel.entry.factory().apply(panel.site); }
        catch (RuntimeException failure) { /* The contributor's adapter has logged it; the window only needs a placeholder. */ }
        panel.component = built != null ? built : new JLabel("This panel could not be loaded.", SwingConstants.CENTER);
        owner.area().setContent(id, panel.entry.title(), panel.component);
    }

    private void notifyVisibility(String id, boolean visible) {
        PanelInstance panel = panels.get(id);
        if (panel != null && panel.site != null) panel.site.notifyVisibility(visible);
    }

    /**
     * Saves every panel's place and the dock's sizes. A slot's saved order also keeps a place for its panels that are
     * not in this window right now (a plugin still loading, or turned off), so each goes back where it was; a panel
     * that never had a place keeps none.
     */
    private void persist() {
        for (Slot slot : Slot.values()) {
            List<String> merged = new ArrayList<>();
            for (String id : dock.ids(slot)) if (panels.containsKey(id)) merged.add(id);
            state.panels().entrySet().stream()
                .filter(entry -> entry.getValue().slot() == slot && entry.getValue().order() < UiState.Panel.LAST)
                .filter(entry -> !panels.containsKey(entry.getKey()))
                .sorted(Comparator.comparingInt(entry -> entry.getValue().order()))
                .forEach(entry -> merged.add(Math.min(entry.getValue().order(), merged.size()), entry.getKey()));
            for (int i = 0; i < merged.size(); i++) {
                String id = merged.get(i);
                boolean visible = panels.containsKey(id) ? dock.visible(id) : state.panel(id).map(UiState.Panel::visible).orElse(false);
                state.putPanel(id, new UiState.Panel(slot, Math.min(i, UiState.Panel.LAST), visible));
            }
        }
        state.setDock(new UiState.Dock(dock.size(Side.LEFT), dock.size(Side.RIGHT), dock.size(Side.BOTTOM),
            dock.ratio(Side.LEFT), dock.ratio(Side.RIGHT), dock.ratio(Side.BOTTOM)));
        state.save();
    }

    private void renderStripes() {
        List<ToolStripe.Button> left = new ArrayList<>(), right = new ArrayList<>();
        for (Slot slot : Slot.values())
            for (String id : dock.ids(slot)) {
                PanelInstance panel = panels.get(id);
                if (panel == null) continue;
                var button = new ToolStripe.Button(id, panel.entry.title(), panel.entry.icon(), slot, dock.visible(id));
                (slot.stripe() == Side.LEFT ? left : right).add(button);
            }
        List<Action> railActions = new ArrayList<>();
        for (String id : model.railActions()) if (actions.get(id) != null) railActions.add(actions.get(id));
        owner.leftStripe().render(left, railActions);
        owner.rightStripe().render(right, List.of());
        owner.syncRailVisibility();
    }

    /** Panel content that is not showing is outside the window's component tree and must be updated by hand. */
    void refreshTheme() {
        for (PanelInstance panel : panels.values())
            if (panel.component != null && !dock.visible(panel.entry.id())) SwingUtilities.updateComponentTreeUI(panel.component);
    }

    private void changed(Contributions.Kind kind) {
        switch (kind) {
            case ACTIONS -> {
                if (syncActions()) owner.rebind();
                // Titles label toolbar buttons, and a vanished or disabled action changes every placement of it.
                owner.chrome().renderContributedToolbar();
                owner.chrome().renderContributedMenus();
                renderStatus();
                renderStripes();
            }
            case PANELS -> { syncPanels(); renderStripes(); }
            case RAIL -> renderStripes();
            case SCOPES -> syncScopes();
            case TOOLBAR -> owner.chrome().renderContributedToolbar();
            case MENUS -> owner.chrome().renderContributedMenus();
            case STATUS -> renderStatus();
        }
    }

    /** Returns whether the set of actions changed, which is when shortcuts must be recomputed. */
    private boolean syncActions() {
        boolean structural = false;
        Map<String, ActionEntry> current = new LinkedHashMap<>();
        for (ActionEntry entry : model.actions()) current.put(entry.id(), entry);
        for (String id : List.copyOf(actions.keySet())) {
            if (current.containsKey(id)) continue;
            commands.remove(id).close();
            actions.remove(id).setEnabled(false);
            structural = true;
        }
        for (ActionEntry entry : current.values()) {
            Action action = actions.get(entry.id());
            if (action == null) {
                String id = entry.id();
                action = new AbstractAction(entry.title()) {
                    @Override public void actionPerformed(ActionEvent event) { invoke(id); }
                };
                if (entry.icon() != null) {
                    action.putValue(Command.ICON, entry.icon());
                    action.putValue(Action.SMALL_ICON, entry.icon());
                }
                actions.put(id, action);
                List<String> keywords = new ArrayList<>(List.of(id.replace('.', ' ').replace('_', ' ')));
                keywords.addAll(entry.keywords());
                // The name is set before registration: a command needs a non-blank Action.NAME.
                commands.put(id, owner.commands().register(new Command(id, action, keywords)));
                structural = true;
            }
            action.putValue(Action.NAME, entry.title());
            action.putValue(Command.TITLE, entry.title());
            action.setEnabled(entry.enabled());
        }
        return structural;
    }

    @Override public void close() {
        if (listening == null) return;
        listening.close();
        listening = null;
        // Closing the window is not hiding its panels: save them as they are, then let go of the dock.
        dock.removeListener(docked);
        persist();
        commands.values().forEach(Subscription::close);
        commands.clear();
        scopes.values().forEach(Subscription::close);
        scopes.clear();
        if (paletteRequests != null) { paletteRequests.close(); paletteRequests = null; }
        actions.values().forEach(action -> action.setEnabled(false));
        if (panelRequests != null) { panelRequests.close(); panelRequests = null; }
        for (PanelInstance panel : panels.values()) if (panel.site != null) panel.site.notifyClosed();
        panels.clear();
        for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe())) {
            stripe.onToggle = id -> { };
            stripe.onMove = (id, slot) -> { };
            stripe.onHide = id -> { };
            stripe.render(List.of(), List.of());
        }
        owner.area().onMove = (id, slot) -> { };
        owner.area().onHide = id -> { };
        owner.syncRailVisibility();
        actions.clear();
        owner.chrome().connect(null);
        owner.status().setContributed(List.of(), id -> null);
    }
}
```

In `workspace/package-info.java` add `dev.jasper.app.dock` after `dev.jasper.app.contributions`:

```java
 * <p>Allowed outgoing Jasper dependencies: dev.jasper.app.appearance, dev.jasper.app.commands, dev.jasper.app.config, dev.jasper.app.contributions, dev.jasper.app.dock, dev.jasper.app.launch, dev.jasper.app.lifecycle, dev.jasper.app.palette, dev.jasper.app.persistence, dev.jasper.app.platform, dev.jasper.app.settings, dev.jasper.app.terminals, dev.jasper.terminal.config, dev.jasper.terminal.rendering, dev.jasper.terminal.search, dev.jasper.terminal.session, dev.jasper.terminal.view.
```

- [ ] **Step 5: Delete what the dock replaced**

```bash
git rm jasper-app/src/main/java/dev/jasper/app/workspace/WorkspaceRegions.java \
       jasper-app/src/main/java/dev/jasper/app/workspace/WindowRail.java \
       jasper-app/src/main/java/dev/jasper/app/contributions/PanelRegion.java \
       jasper-app/src/test/java/dev/jasper/app/workspace/WorkspaceRegionsTest.java
```

- [ ] **Step 6: Move the remaining tests onto the stripes and the area**

Replace `jasper-app/src/test/java/dev/jasper/app/workspace/RailWidthTest.java` with (its first test, the button
layout, moved to `ToolStripeTest` in Task 6):

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Rectangle;
import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.*;
import static org.assertj.core.api.Assertions.*;

/** Both stripes follow {@code window.rail_width}; the stripe layout itself is pinned by {@code ToolStripeTest}. */
class RailWidthTest {
    @AfterEach void cleanup() throws Exception { closeOwners(); }

    private static int x(JComponent component, int at, WindowContent owner) { return SwingUtilities.convertPoint(component, at, 0, owner).x; }

    @Test void visibleStripesHaveBalancedSpacingToTheAdjacentSurfaces() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.leftStripe().render(List.of(new ToolStripe.Button("files", "Files",
                dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.LEFT_TOP, true)), List.of());
            owner.rightStripe().render(List.of(new ToolStripe.Button("log", "Log",
                dev.jasper.app.platform.AppIcons.named("FOLDER"), Slot.RIGHT_TOP, true)), List.of());
            owner.syncRailVisibility();
            var panel = new JPanel(); DockTestSupport.show(owner, Slot.LEFT_TOP, panel, 250);
            var right = new JPanel(); DockTestSupport.show(owner, Slot.RIGHT_TOP, right, 250);
            for (int width : new int[]{28, 36, 64}) {
                owner.setRailWidth(width); owner.setSize(1000, 600);
                LayoutTestSupport.layoutTree(owner);
                LayoutTestSupport.layoutTree(owner);
                int left = x(owner.leftStripe(), 0, owner);
                int surface = x(panel, 0, owner);
                assertThat(surface - left).as("no extra gutter on the icons' right").isEqualTo(owner.leftStripe().getWidth());
                assertThat(x(owner.rightStripe(), 0, owner)).as("the right stripe mirrors it").isEqualTo(x(right, right.getWidth(), owner));
                assertThat(owner.rightStripe().getWidth()).isEqualTo(owner.leftStripe().getWidth());
                var button = owner.leftStripe().buttons().getFirst();
                var insets = button.getInsets();
                var view = new Rectangle(insets.left, insets.top, button.getWidth() - insets.left - insets.right,
                    button.getHeight() - insets.top - insets.bottom);
                var icon = new Rectangle(); var text = new Rectangle();
                SwingUtilities.layoutCompoundLabel(button, button.getFontMetrics(button.getFont()), button.getText(), button.getIcon(),
                    button.getVerticalAlignment(), button.getHorizontalAlignment(), button.getVerticalTextPosition(),
                    button.getHorizontalTextPosition(), view, icon, text, button.getIconTextGap());
                int iconLeft = SwingUtilities.convertPoint(button, icon.x, 0, owner).x;
                assertThat(Math.abs((iconLeft - left) - (surface - iconLeft - icon.width))).as("icon centered between window edge and panel").isLessThanOrEqualTo(1);
            }
            var originalParent = panel.getParent();
            owner.setRailVisible(false); LayoutTestSupport.layoutTree(owner);
            assertThat(panel.getParent()).as("visibility does not reparent plugin content").isSameAs(originalParent);
            assertThat(x(panel, 0, owner)).isEqualTo(UIScale.scale(6));
            assertThat(x(right, right.getWidth(), owner)).isEqualTo(owner.getWidth() - UIScale.scale(6));
            owner.setRailVisible(true); LayoutTestSupport.layoutTree(owner);
            assertThat(x(panel, 0, owner)).isEqualTo(owner.leftStripe().getWidth());
            DockTestSupport.hide(owner, Slot.LEFT_TOP); DockTestSupport.hide(owner, Slot.RIGHT_TOP); LayoutTestSupport.layoutTree(owner);
            assertThat(x(owner.tabStrip(), 0, owner)).as("terminal follows the same stripe edge").isEqualTo(owner.leftStripe().getWidth());
            var bottom = new JPanel(); DockTestSupport.show(owner, Slot.BOTTOM_LEFT, bottom, 150);
            LayoutTestSupport.layoutTree(owner); LayoutTestSupport.layoutTree(owner);
            assertThat(x(bottom, 0, owner)).as("bottom panels use the same edge").isEqualTo(owner.leftStripe().getWidth());
            assertThat(x(bottom, bottom.getWidth(), owner)).as("and reach the right stripe").isEqualTo(x(owner.rightStripe(), 0, owner));
        });
    }

    @Test void theWindowAppliesALiveWidthToBothStripesAndRejectsValuesOutsideTheRange() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var changes = new AtomicInteger(); owner.onMinimumSizeChanged = changes::incrementAndGet;
            assertThat(owner.railWidth()).isEqualTo(28);
            owner.setRailWidth(48);
            assertThat(owner.railWidth()).isEqualTo(48);
            assertThat(owner.leftStripe().getPreferredSize().width).isEqualTo(UIScale.scale(48));
            assertThat(owner.rightStripe().getPreferredSize().width).isEqualTo(UIScale.scale(48));
            assertThat(changes.get()).isPositive();
            for (int width : new int[]{27, 65, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
                assertThatIllegalArgumentException().isThrownBy(() -> owner.setRailWidth(width));
                assertThat(owner.railWidth()).isEqualTo(48);
            }
            assertThat(content(launcher(new ArrayDeque<>())).railWidth()).isEqualTo(28);
        });
    }

    @Test void theSavedWidthAppliesOnEveryReload() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            var saved = ConfigSnapshot.defaults().toBuilder().railWidth(44).build();
            owner.applyConfiguration(saved, true);
            assertThat(owner.railWidth()).isEqualTo(44);
            owner.setRailWidth(52);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).build(), true);
            assertThat(owner.railWidth()).as("the saved width is the only width").isEqualTo(44);
            owner.applyConfiguration(saved.toBuilder().tabHeight(40).railWidth(30).build(), true);
            assertThat(owner.railWidth()).isEqualTo(30);
        });
    }

    @Test void closingTheWindowIgnoresLaterWidthChanges() throws Exception {
        edt(() -> {
            var owner = content(launcher(new ArrayDeque<>()));
            owner.setRailWidth(44);
            owner.close();
            owner.setRailWidth(60);
            assertThat(owner.railWidth()).isEqualTo(44);
        });
    }
}
```

Save this script as `/tmp/dock-window-tests.py` and run it from the worktree root with
`python3 /tmp/dock-window-tests.py`. It edits seven tests; each replacement must match exactly once, and no file may
still name the rail or the regions afterwards:

```python
import pathlib

# Run from the worktree root. Every replacement must match exactly once, or the script stops before writing.
root = pathlib.Path("jasper-app/src/test/java/dev/jasper/app/workspace")
SHOW_RAIL_OLD = """        owner.rail().render(java.util.List.of(new WindowRail.PanelButton("hosts", "SSH hosts",
            dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.contributions.PanelRegion.LEFT, false)), java.util.List.of());"""
SHOW_RAIL_NEW = """        owner.leftStripe().render(java.util.List.of(new ToolStripe.Button("hosts", "SSH hosts",
            dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.LEFT_TOP, false)), java.util.List.of());"""
edits = {
    "GraphiteChromeTest.java": [
        ("""                        var regions = owner.regions();
                        var column = owner.windowTabs().getParent();
                        var bounds = javax.swing.SwingUtilities.convertRectangle(column.getParent(), column.getBounds(), regions);
                        if (visible) {
                            assertThat(bounds.width).isLessThan(regions.getWidth());
                            assertThat(bounds.height).isLessThan(regions.getHeight());
                            continue;
                        }
                        assertThat(bounds).as("no gutter or bottom spacer when the rail is hidden")
                            .isEqualTo(new java.awt.Rectangle(0, 0, regions.getWidth(), regions.getHeight()));
                        var image = new java.awt.image.BufferedImage(regions.getWidth(), regions.getHeight(), 2);
                        var g = image.createGraphics();
                        try { regions.paint(g); } finally { g.dispose(); }""",
         """                        var area = owner.area();
                        var column = owner.windowTabs().getParent();
                        var bounds = javax.swing.SwingUtilities.convertRectangle(column.getParent(), column.getBounds(), area);
                        if (visible) {
                            assertThat(bounds.width).as("the stripes supply the side spacing").isEqualTo(area.getWidth());
                            assertThat(bounds.height).as("the lower corner spacer").isLessThan(area.getHeight());
                            continue;
                        }
                        assertThat(bounds).as("no gutter or bottom spacer when the stripes are hidden")
                            .isEqualTo(new java.awt.Rectangle(0, 0, area.getWidth(), area.getHeight()));
                        var image = new java.awt.image.BufferedImage(area.getWidth(), area.getHeight(), 2);
                        var g = image.createGraphics();
                        try { area.paint(g); } finally { g.dispose(); }"""),
        (SHOW_RAIL_OLD, SHOW_RAIL_NEW),
        ("""            assertThat(java.util.Arrays.stream(owner.rail().getComponents())""",
         """            assertThat(java.util.Arrays.stream(owner.leftStripe().getComponents())"""),
        ("""                    owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, panel, 260);
                    owner.rail().render(java.util.List.of(new WindowRail.PanelButton("hosts", "SSH hosts",
                        dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.contributions.PanelRegion.LEFT, true)), java.util.List.of());""",
         """                    DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, panel, 260);
                    owner.leftStripe().render(java.util.List.of(new ToolStripe.Button("hosts", "SSH hosts",
                        dev.jasper.app.platform.AppIcons.named("SERVER"), dev.jasper.app.dock.Slot.LEFT_TOP, true)), java.util.List.of());"""),
        ("""                    var railEdge = javax.swing.SwingUtilities.convertPoint(owner.rail(), owner.rail().getWidth() - 1, 120, root);
                    assertThat(image.getRGB(railEdge.x, railEdge.y)).as("no rail border").isEqualTo(chrome);""",
         """                    var railEdge = javax.swing.SwingUtilities.convertPoint(owner.leftStripe(), owner.leftStripe().getWidth() - 1, 120, root);
                    assertThat(image.getRGB(railEdge.x, railEdge.y)).as("no stripe border").isEqualTo(chrome);
                    var rightEdge = javax.swing.SwingUtilities.convertPoint(owner.rightStripe(), 0, 120, root);
                    assertThat(image.getRGB(rightEdge.x, rightEdge.y)).as("no right stripe border").isEqualTo(chrome);"""),
    ],
    "BarHeightTest.java": [
        ("""            owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, panel, 200);
            var parent = panel.getParent();""",
         """            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, panel, 200);
            var parent = panel.getParent();"""),
        ("""                assertThat(SwingUtilities.convertPoint(panel, 0, 0, owner).y).isEqualTo(expected);""",
         """                assertThat(SwingUtilities.convertPoint(panel.getParent(), 0, 0, owner).y).as("the frame starts under the header row").isEqualTo(expected);"""),
        ("""            var left = new JPanel(); var bottom = new JPanel();
            owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, left, 250);
            owner.regions().show(dev.jasper.app.contributions.PanelRegion.BOTTOM, bottom, 150);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            int top = SwingUtilities.convertPoint(owner.status(), 0, 0, owner).y;
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).isEqualTo(top);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(top);
            var parent = left.getParent();
            owner.setStatusVisible(false); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(left.getParent()).isSameAs(parent);
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).isEqualTo(owner.getHeight() - UIScale.scale(6));
            owner.setStatusVisible(true); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).isEqualTo(top);""",
         """            var left = new JPanel(); var bottom = new JPanel();
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, left, 250);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            int top = SwingUtilities.convertPoint(owner.status(), 0, 0, owner).y;
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).as("alone, a side reaches the status bar").isEqualTo(top);
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.BOTTOM_LEFT, bottom, 150);
            dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(top);
            assertThat(SwingUtilities.convertPoint(left, 0, left.getHeight(), owner).y).as("above the bottom area, a side ends at its divider")
                .isEqualTo(SwingUtilities.convertPoint(bottom.getParent(), 0, 0, owner).y - UIScale.scale(6));
            var parent = bottom.getParent();
            owner.setStatusVisible(false); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(bottom.getParent()).isSameAs(parent);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(owner.getHeight() - UIScale.scale(6));
            owner.setStatusVisible(true); dev.jasper.app.testsupport.LayoutTestSupport.layoutTree(owner);
            assertThat(SwingUtilities.convertPoint(bottom, 0, bottom.getHeight(), owner).y).isEqualTo(top);"""),
    ],
    "MacTitleBarTest.java": [
        ("""            owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, left, 200);
            owner.regions().show(dev.jasper.app.contributions.PanelRegion.RIGHT, right, 200);""",
         """            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, left, 200);
            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.RIGHT_TOP, right, 200);"""),
        ("""                            assertThat(SwingUtilities.convertPoint(panel, 0, 0, root).y).isEqualTo(surfaceTop);""",
         """                            assertThat(SwingUtilities.convertPoint(panel.getParent(), 0, 0, root).y).isEqualTo(surfaceTop);"""),
        ("""                assertThat(SwingUtilities.convertPoint(left, 0, 0, root).y)""",
         """                assertThat(SwingUtilities.convertPoint(left.getParent(), 0, 0, root).y)"""),
    ],
    "WindowCommandPaletteTest.java": [
        ("""                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.LEFT, new JPanel(), sideWidth);
                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.RIGHT, new JPanel(), 220);
                            owner.regions().show(dev.jasper.app.contributions.PanelRegion.BOTTOM, new JPanel(), 180);""",
         """                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.LEFT_TOP, new JPanel(), sideWidth);
                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.RIGHT_TOP, new JPanel(), 220);
                            DockTestSupport.show(owner, dev.jasper.app.dock.Slot.BOTTOM_LEFT, new JPanel(), 180);"""),
    ],
    "ThemeControllerTest.java": [
        ("""                assertThat(owner.rail().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));""",
         """                assertThat(owner.leftStripe().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));
                assertThat(owner.rightStripe().getBackground()).isEqualTo(UIManager.getColor("Jasper.titleBackground"));"""),
    ],
    "WorkspaceThemeTest.java": [
        ("""                assertThat(owner.rail().getBackground()).as("sidebar rail").isEqualTo(titleBackground);""",
         """                assertThat(owner.leftStripe().getBackground()).as("left stripe").isEqualTo(titleBackground);
                assertThat(owner.rightStripe().getBackground()).as("right stripe").isEqualTo(titleBackground);"""),
    ],
    "PluginPaneAppearanceTest.java": [
        ("""            var railBackground = owner.rail().getBackground();
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            assertThat(owner.rail().getClientProperty("Jasper.workspaceStyle")).isNull();
            assertThat(owner.rail().getBackground()).isEqualTo(railBackground);""",
         """            var railBackground = owner.leftStripe().getBackground();
            themes.configure(Theme.JASPER_LIGHT, new WorkspaceThemeChoice("Jasper Dark"), Theme.DARK, UiFontConfig.defaults());
            for (var stripe : java.util.List.of(owner.leftStripe(), owner.rightStripe())) {
                assertThat(stripe.getClientProperty("Jasper.workspaceStyle")).isNull();
                assertThat(stripe.getBackground()).isEqualTo(railBackground);
            }"""),
    ],
}
changed = {}
for name, pairs in edits.items():
    path = root / name
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count != 1: raise SystemExit(f"{name}: expected one match, found {count}: {old.strip()[:70]}")
        text = text.replace(old, new)
    for gone in ("owner.rail()", "owner.regions()", "WindowRail", "PanelRegion"):
        if gone in text: raise SystemExit(f"{name}: still names {gone}")
    changed[path] = text
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path.name)
```

Expected output: seven `updated …` lines. What changes, beyond the names: with a right stripe the terminal column
has no right gutter any more (`GraphiteChromeTest`); frames start under the header row while their content starts
under the tool window header, so the header-row tests measure the frame (`BarHeightTest`, `MacTitleBarTest`); and a
side above the bottom area ends at its divider instead of at the status bar (`BarHeightTest`).

- [ ] **Step 7: Make sure nothing names the old layout**

```bash
grep -rn "PanelRegion\|WorkspaceRegions\|WindowRail\|owner\.rail()\|\.regions()\|defaultRegion" --include='*.java' jasper-app/src plugins jasper-sdk jasper-sdk-testkit | grep -v /build/
```

Expected: no output.

- [ ] **Step 8: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.dock.*' --tests 'dev.jasper.app.persistence.*'`
Expected: PASS. `WindowPanelsTest` runs 16 cases.

Then: `./gradlew check`
Expected: BUILD SUCCESSFUL, all four architecture gates included. `GraphiteChromeTest` writes
`build/render-preview/fluid-workspace-{dark,light}.png` and `hidden-rail-{dark,light}.png`; look at them.

- [ ] **Step 9: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add -A jasper-app/src
git commit -F - <<'EOF'
feat(workspace)!: dock tool windows in six slots between two stripes

The window body is now the left stripe, the tool window area and the right
stripe. WindowContributions keeps its lazy panel instances and docks them in
the window's DockState; one listener lays out the area, renders the stripes,
tells the panels and saves. Saved order survives any registration order and
a plugin turned off and on. WorkspaceRegions, WindowRail and PanelRegion are
gone.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 9: Bundled panels: SFTP opens under Sessions, and no duplicate titles

**Files:**
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java` (`withSplit()`)
- Modify: `plugins/remote/src/main/resources/plugin.toml` (`version = "0.11.0"`)
- Modify: `plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java`, `…/ui/tunnels/TunnelsPanel.java`,
  `plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java` (drop the title label)
- Modify: `plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java` and the matching compiled example in
  `docs/plugin-authoring.md` (the "Sample panel" label becomes a sentence)
- Modify: `plugins/remote/src/test/java/dev/jasper/remote/{RemotePluginTest,SftpPluginTest}.java`,
  `…/ui/HostsPanelTest.java`, `…/ui/tunnels/TunnelsPanelTest.java`,
  `plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersPanelTest.java`

**Interfaces:**
- Consumes: Task 3's `PanelSpec.withSplit()` and the `LEFT+split` testkit line; Task 8's headers, which now carry
  every panel's title.
- Produces: SFTP's default slot is `LEFT_BOTTOM`; Remote 0.11.0. Sessions, Tunnels, File Servers and Sample stay
  `Anchor.LEFT` (`LEFT_TOP`).

- [ ] **Step 1: Write the failing tests, and make the change**

The tests and the code change together, so one script does both. Save it as `/tmp/dock-plugins.py` and run it from
the worktree root with `python3 /tmp/dock-plugins.py`; each replacement must match the stated number of times or
nothing is written:

```python
import pathlib

# Run from the worktree root. Every replacement must match the stated number of times, or the script stops before writing.
TEXTS = """
    private static List<String> texts(java.awt.Container root) {
        List<String> found = new ArrayList<>();
        for (java.awt.Component child : root.getComponents()) {
            if (child instanceof javax.swing.JLabel label) found.add(label.getText());
            if (child instanceof java.awt.Container nested) found.addAll(texts(nested));
        }
        return found;
    }
}
"""
SFTP_OLD = '"dev.jasper.remote.sftp.panel|SFTP|LEFT|dev.jasper.remote.sftp.toggle"'
SFTP_NEW = '"dev.jasper.remote.sftp.panel|SFTP|LEFT+split|dev.jasper.remote.sftp.toggle"'
edits = {
    "plugins/remote/src/main/java/dev/jasper/remote/ui/sftp/SftpUi.java": [
        ("Anchor.LEFT).withToggleActionId(TOGGLE),this::create);", "Anchor.LEFT).withToggleActionId(TOGGLE).withSplit(),this::create);", 1),
    ],
    "plugins/remote/src/main/resources/plugin.toml": [
        ('version = "0.10.0"', 'version = "0.11.0"', 1),
    ],
    "plugins/remote/src/main/java/dev/jasper/remote/ui/HostsPanel.java": [
        ('    final JLabel title = heading("Sessions");\n', "", 1),
        ("        header.add(title); header.add(Box.createHorizontalGlue());",
         "        // The tool window header carries the title.\n        header.add(Box.createHorizontalGlue());", 1),
    ],
    "plugins/remote/src/main/java/dev/jasper/remote/ui/tunnels/TunnelsPanel.java": [
        ("""        var title = new JLabel("Tunnels");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title); header.add(Box.createHorizontalGlue()); header.add(add);""",
         """        // The tool window header carries the title.
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalGlue()); header.add(add);""", 1),
    ],
    "plugins/fileserver/src/main/java/dev/jasper/fileserver/ui/FileServersPanel.java": [
        ("""        var title = label("File Servers");
        title.setFont(title.getFont().deriveFont(Font.BOLD));
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(title);
        header.add(Box.createHorizontalGlue());""",
         """        // The tool window header carries the title.
        var header = new JPanel();
        header.setLayout(new BoxLayout(header, BoxLayout.X_AXIS));
        header.add(Box.createHorizontalGlue());""", 1),
    ],
    "plugins/sample/src/main/java/dev/jasper/sample/SamplePlugin.java": [
        ('panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);',
         'panel.add(new JLabel("Runs a short demo activity in the background."), BorderLayout.NORTH);', 1),
    ],
    "docs/plugin-authoring.md": [
        ('panel.add(new JLabel("Sample panel"), BorderLayout.NORTH);',
         'panel.add(new JLabel("Runs a short demo activity in the background."), BorderLayout.NORTH);', 1),
    ],
    "plugins/remote/src/test/java/dev/jasper/remote/RemotePluginTest.java": [(SFTP_OLD, SFTP_NEW, 2)],
    "plugins/remote/src/test/java/dev/jasper/remote/SftpPluginTest.java": [(SFTP_OLD, SFTP_NEW, 1)],
    "plugins/remote/src/test/java/dev/jasper/remote/ui/HostsPanelTest.java": [
        ("    @Test void theHeadingReadsSessionsAndThereIsNoWslGroupWithoutDistributions() {",
         "    @Test void theToolWindowHeaderCarriesTheTitleAndThereIsNoWslGroupWithoutDistributions() {", 1),
        ('        assertThat(panel.title.getText()).isEqualTo("Sessions");',
         '        assertThat(texts(panel)).as("Jasper\'s header shows Sessions").doesNotContain("Sessions");', 1),
    ],
    "plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsPanelTest.java": [],
    "plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersPanelTest.java": [],
}
appended = {
    "plugins/remote/src/test/java/dev/jasper/remote/ui/HostsPanelTest.java": TEXTS,
    "plugins/remote/src/test/java/dev/jasper/remote/ui/tunnels/TunnelsPanelTest.java": """
    @Test void theToolWindowHeaderCarriesTheTitle() {
        assertThat(texts(panel)).as("Jasper's header shows Tunnels").doesNotContain("Tunnels");
    }
""" + TEXTS,
    "plugins/fileserver/src/test/java/dev/jasper/fileserver/ui/FileServersPanelTest.java": """
    @Test void theToolWindowHeaderCarriesTheTitle() {
        List<javax.swing.JLabel> found = new ArrayList<>();
        labels(panel(), found);
        assertThat(found).extracting(javax.swing.JLabel::getText).as("Jasper's header shows File Servers").doesNotContain("File Servers");
    }
}
""",
}
changed = {}
for name, pairs in edits.items():
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new, times in pairs:
        count = text.count(old)
        if count != times: raise SystemExit(f"{name}: expected {times} match(es), found {count}: {old.strip()[:70]}")
        text = text.replace(old, new)
    if name in appended:
        body = text.rstrip()
        if not body.endswith("}"): raise SystemExit(f"{name}: does not end with the class's closing brace")
        text = body[:-1].rstrip("\n") + "\n" + appended[name]
    changed[path] = text
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path)
```

Expected output: twelve `updated …` lines.

To watch the new tests fail first, set the code half aside as a patch (not the stash, which other sessions share):

```bash
git diff -- plugins/remote/src/main plugins/fileserver/src/main plugins/sample/src/main docs/plugin-authoring.md > /tmp/dock-plugins-code.patch
git checkout -- plugins/remote/src/main plugins/fileserver/src/main plugins/sample/src/main docs/plugin-authoring.md
```

Run the Step 2 command. Expected: FAIL, with `SFTP|LEFT|…` where `SFTP|LEFT+split|…` was expected, and the three
`theToolWindowHeaderCarriesTheTitle…` cases finding their titles. Then put the code back with
`git apply /tmp/dock-plugins-code.patch`.

- [ ] **Step 2: Run the plugin tests**

Run: `./gradlew :jasper-plugin-remote:test :jasper-plugin-fileserver:test :jasper-plugin-sample:test :jasper-app:test --tests 'dev.jasper.app.documentation.*'`
Expected: PASS. `AppDocumentationTest` confirms the authoring guide's compiled example matches `SamplePlugin`.

- [ ] **Step 3: Run the whole check**

Run: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add plugins docs/plugin-authoring.md
git commit -F - <<'EOF'
feat(remote): open SFTP under Sessions and leave titles to the header

SFTP registers with withSplit(), so it opens in the left side's lower half.
Remote 0.11.0. Sessions, Tunnels and File Servers no longer repeat their
title inside the panel, and the Sample panel's label is a sentence.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 10: Documentation, `STATUS.md` and the plan banner

**Files:**
- Modify: `AGENTS.md`, `docs/sdk-architecture.md`, `docs/plugin-authoring.md`, `docs/app-architecture.md`,
  `docs/app-maintenance.md`, `docs/configuration.md`, `jasper-sdk/README.md` (script in Step 1)
- Modify: `docs/STATUS.md` (new entry at the top of "Current state")
- Modify: this plan's **Status** banner

**Interfaces:** none (documentation). `AppDocumentationTest` checks every link and anchor in these files and every
plan, so keep links to files that exist.

- [ ] **Step 1: Update the guides**

Save this script as `/tmp/dock-docs.py` and run it from the worktree root with `python3 /tmp/dock-docs.py`; each
replacement must match exactly once or nothing is written:

```python
import pathlib

# Run from the worktree root. Every replacement must match exactly once, or the script stops before writing.
edits = {
    "AGENTS.md": [
        ("""- JediTerm (`org.jetbrains.jediterm:jediterm-core:3.76`""",
         """- `dev.jasper.app.dock` (the tool window dock model: slots, `DockState`, `DockGeometry`) is JDK-only: no Swing, and from `java.awt` only `Rectangle` and `Point`. `verifyApplicationArchitecture` enforces it.
- JediTerm (`org.jetbrains.jediterm:jediterm-core:3.76`"""),
    ],
    "docs/sdk-architecture.md": [
        ("""| `dev.jasper.app.contributions` | App-native EDT model of contributed actions, toolbar entries, menu sections, status entries, panels and rail actions | `lifecycle` |
""", """| `dev.jasper.app.contributions` | App-native EDT model of contributed actions, toolbar entries, menu sections, status entries, panels and rail actions | `dock`, `lifecycle` |
| `dev.jasper.app.dock` | The tool window dock model: the six slots, each window's `DockState` and the pure `DockGeometry`; JDK-only | none |
"""),
        ("""Panels and rail actions are part of the `Contributions` model. Each window's
`WindowContributions` keeps one lazily built instance per panel, shows at most one per region
through `WorkspaceRegions` (nested split panes rebuilt around the terminal column, the tab
strip over the deck: bottom wraps the column, right wraps that, left wraps that, so the rail and
the side panels run from the toolbar to the status bar), and renders `WindowRail`. A window with nothing
contributed has no rail and its old layout. Requests to show, hide or toggle a panel travel""",
         """Panels and rail actions are part of the `Contributions` model; each panel carries its default
`dock.Slot`, which `HostedUi` maps from the SDK's `Anchor` plus `PanelSpec.split` (SDK 0.13.0). Each
window's `WindowContributions` keeps one lazily built instance per panel and docks the panels in the
window's `DockState` (per slot an ordered list with at most one active window; per-side sizes and
ratios). One listener on that state builds what starts showing, lays out `ToolWindowArea` (the terminal
column, the tab strip over the deck, and a rounded frame with a `ToolWindowHeader` for each showing
slot, placed by the pure `DockGeometry`: the bottom area spans the full width, the sides and the
terminal column run from the toolbar to it), renders the two `ToolStripe`s, tells the panels and
saves. A window with nothing contributed has no stripes and its old layout. Requests to show, hide or toggle a panel travel"""),
        ("""`persistence.UiState` (`ui-state.toml`) holds panel region, visibility and size, rail
visibility and auxiliary window bounds. It is application state, not configuration: strict
read, atomic write, defaults when unreadable. The process stays alive while a plugin window
is open.""",
         """`persistence.UiState` (`ui-state.toml`, version 2) holds each panel's slot, order and visibility,
the dock's per-side sizes and ratios, stripe visibility and auxiliary window bounds. It is
application state, not configuration: a bad panel entry or dock key falls back alone, an
unreadable file falls back to defaults, writes are atomic, and a version 1 file migrates on read.
The process stays alive while a plugin window is open."""),
        ("""`WorkspaceRegions` remain the single renderer.""", """`ToolWindowArea` remain the single renderer."""),
    ],
    "docs/plugin-authoring.md": [
        ("""A panel is a Swing component in the left, right or bottom region of every terminal window.
Register it once; Jasper asks your factory for one instance per window, the first time the
panel is shown there, and gives the panel a rail icon, a `<panel id>.toggle` action and a
View → Tools entry, so don't add a View menu item of your own that toggles it. If you already have
an action that toggles the panel, for example to give it a shortcut, register it first and name it
with `PanelSpec.withToggleActionId(id)` (SDK 0.8.1): Tools and the palette then list your action,
with its shortcut, and Jasper registers no `<panel id>.toggle`. One panel shows per region; the user can move a panel to another region
from its rail icon, and Jasper remembers where it was and how big.

A rail button runs one of your actions.""",
         """A panel is a Swing component docked as a tool window in every terminal window, as in IntelliJ:
stripes of icons on the window's left and right edges, and six slots. The left and right sides each
have an upper and a lower half; the bottom area, the window's full width under the sides, has a left
and a right half. `Anchor` picks the side and `PanelSpec.withSplit()` (SDK 0.13.0) the second half;
SFTP, for example, opens under Sessions. Register a panel once; Jasper asks your factory for one
instance per window, the first time the panel is shown there, and gives the panel a stripe icon, a
header (its title, an options menu and a hide button), a `<panel id>.toggle` action and a
View → Tools entry, so don't add a View menu item of your own that toggles it, and don't repeat the
panel's title inside it. If you already have
an action that toggles the panel, for example to give it a shortcut, register it first and name it
with `PanelSpec.withToggleActionId(id)` (SDK 0.8.1): Tools and the palette then list your action,
with its shortcut, and Jasper registers no `<panel id>.toggle`. One panel shows per slot; the user
can move a panel to any of the six slots from its stripe icon's menu or its header's options menu,
and Jasper remembers its slot, its place on the stripe and the side's size.

A rail button, on the left stripe after the panel icons, runs one of your actions."""),
        ("""`<panel id>.toggle` action or View → Tools menu; `host.panels()` appends `|<action id>` for a panel
that names its own toggle.""",
         """`<panel id>.toggle` action or View → Tools menu; `host.panels()` lists `id|title|ANCHOR`, with
`+split` after the anchor for a split panel and `|<action id>` for a panel that names its own toggle."""),
    ],
    "docs/app-architecture.md": [
        ("""| `workspace` | EDT windows, tabs, splits, panes, panel regions and the rail,""",
         """| `workspace` | EDT windows, tabs, splits, panes, the tool window area, stripes and headers,"""),
        ("""| `commands` | EDT action registry,""",
         """| `dock` | Pure tool window dock model: the six slots, each window's `DockState` and `DockGeometry`. JDK-only (no Swing; `java.awt.Rectangle` and `Point` only, enforced by `verifyApplicationArchitecture`); each window owns its state on the EDT. |
| `commands` | EDT action registry,"""),
        ("""Side panels paint `ToolWindow.background` through `ToolWindowSurface`; the rail stays chrome.""",
         """Tool windows paint `ToolWindow.background` through `ToolWindowSurface`, their headers included; the stripes stay chrome."""),
    ],
    "docs/app-maintenance.md": [
        ("""Workspace rounding belongs to `WorkspaceRegions.Host`: 8px-radius panel surfaces and,
while the rail is visible, matching rounded corners for the terminal/tab column. With
the rail hidden or empty, the terminal/tab column has square corners, no exterior
gutters and no lower corner spacer: it fills the available workspace edge to edge.""",
         """Workspace rounding belongs to `ToolWindowArea.Host`: 8px-radius tool window frames and,
while the stripes are visible, matching rounded corners for the terminal/tab column. With
the stripes hidden or empty, the terminal/tab column has square corners, no exterior
gutters and no lower corner spacer: it fills the available workspace edge to edge.
Where frames go is `DockGeometry`'s job, never the area's: change the geometry and its
tests first, and keep the drop preview (plan 2) on the same function."""),
        ("""Exterior gutters are 6px, except
beside a visible rail: the rail supplies its own balanced spacing, so the adjacent
left/center/bottom surface has no additional leading gutter. Hiding or emptying the
rail restores that gutter on plugin panel hosts without reparenting their content.
The following gutter and lower-inset rules apply to the terminal column only while
the rail is visible.""",
         """Exterior gutters are 6px, except
beside the visible stripes: each stripe supplies its own balanced spacing, so the adjacent
frames and the terminal column have no additional side gutter. Hiding or emptying the
stripes restores that gutter on tool window frames without reparenting their content.
The following gutter and lower-inset rules apply to the terminal column only while
the stripes are visible."""),
        ("""Adjacent hosts omit
their facing gutters so the plain 6px draggable splitter supplies the gap.
Region widths/heights and persisted splitter sizes include exterior gutters.""",
         """Adjacent frames omit
their facing gutters so the area's plain 6px divider supplies the gap.
Slot sizes and the saved dock sizes include exterior gutters."""),
        ("""Rail selection uses the theme's toolbar-button background and the original theme-aware icon;""",
         """Stripe selection uses the theme's toolbar-button background and the original theme-aware icon;"""),
        ("""Workspace-region splitters remove their border/grip after every UI
update;""", """Dock dividers paint nothing, so no UI
update can give them a border or grip;"""),
    ],
    "docs/configuration.md": [
        ("""Integer 28–64 logical pixels; the panel rail's buttons scale with it;""",
         """Integer 28–64 logical pixels; both tool window stripes and their buttons scale with it;"""),
        ("""`ui-state.toml` beside `config.toml` remembers panel placement, rail visibility and plugin
window bounds;""",
         """`ui-state.toml` beside `config.toml` remembers each panel's slot and place on its stripe, the
dock's sizes, stripe visibility and plugin window bounds;"""),
    ],
}
readme = pathlib.Path("jasper-sdk/README.md")
section = """
## Tool windows (SDK 0.13.0)

A panel docks in one of six slots: the upper or lower half of the left or right side, or the left or right half of
the full-width bottom area. `Anchor` names the side; `PanelSpec.withSplit()` starts the panel in the second half
(the lower half of a side, the right half of the bottom area). `withToggleActionId` keeps the flag, and the four- and
five-argument constructors remain, so plugins built against 0.12 still link. Jasper gives every tool window a header
with its title, so a panel should not show its own title. The user's placement wins over the default. The testkit
lists a split panel as `id|title|LEFT+split`.
"""
changed = {}
for name, pairs in edits.items():
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count != 1: raise SystemExit(f"{name}: expected one match, found {count}: {old.strip()[:70]}")
        text = text.replace(old, new)
    changed[path] = text
text = readme.read_text(encoding="utf-8")
if "## Tool windows (SDK 0.13.0)" in text: raise SystemExit("README already has the section")
changed[readme] = text.rstrip("\n") + "\n" + section
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path)
```

Expected output: seven `updated …` lines.

- [ ] **Step 2: Add the `STATUS.md` entry**

Count the tests first (from the worktree root, after a fresh `./gradlew check`):

```bash
python3 - <<'COUNT'
import pathlib, re
roots = ["jasper-terminal", "jasper-app", "jasper-buddy", "jasper-sdk", "jasper-sdk-testkit"]
roots += [str(p) for p in sorted(pathlib.Path("plugins").iterdir()) if p.is_dir()]
keys = ("tests", "failures", "errors", "skipped")
total = dict.fromkeys(keys, 0)
for root in roots:
    for report in pathlib.Path(root, "build/test-results/test").glob("TEST-*.xml"):
        suite = re.search(r"<testsuite\b[^>]*>", report.read_text(encoding="utf-8")).group(0)
        for key in keys: total[key] += int(re.search(key + r'="(\d+)"', suite).group(1))
print(", ".join(f"{key} {total[key]}" for key in keys))
COUNT
```

Then insert this entry as the first bullet under `## Current state — …` in `docs/STATUS.md`. Replace the four
`<…>` markers with the counted numbers before saving:

```markdown
- **Dock layout plan 1: six slots** (branch `claude/dock-layout`,
  [plan](superpowers/plans/2026-10-02-jasper-dock-layout-plan-1-six-slots.md),
  [spec](superpowers/specs/2026-10-01-jasper-dock-layout-design.md)): IntelliJ-style tool windows apart from
  dragging. New JDK-only package `dev.jasper.app.dock` (`Slot`, `Side`, `DockState`, pure `DockGeometry`), enforced
  by `verifyApplicationArchitecture`. `ToolWindowArea` replaces `WorkspaceRegions` (one rounded frame with a
  `ToolWindowHeader` per showing slot, a full-width bottom area, its own 6px dividers that preview a drag and save
  on release); two `ToolStripe`s replace `WindowRail`; six-slot Move to menus on stripe buttons and headers.
  `ui-state.toml` version 2 (`[dock]` sizes and ratios, per-panel slot, order and visibility; per-entry fallback;
  version 1 migrates on read). SDK 0.13.0 adds `PanelSpec.withSplit()`; all seven manifests require
  `>=0.13.0, <0.14`; Remote 0.11.0 opens SFTP under Sessions. Sessions, Tunnels and File Servers no longer repeat
  their title. Planning decisions are listed in the plan. `./gradlew check`: <tests> tests, <failures> failures,
  <errors> errors, <skipped> skipped. No GUI launched. GUI checks for the user: the plan's last section. Next:
  [plan 2, drag and drop](superpowers/plans/2026-10-02-jasper-dock-layout-plan-2-drag-and-drop.md).
```

- [ ] **Step 3: Update this plan's banner**

Replace the `**Status:**` paragraph at the top of this plan with one that says the plan was executed, on which
commits, with which deviations from the plan text (or "none"), and the same test counts.

- [ ] **Step 4: Run the documentation test, the whole check and the hygiene scan**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'` then `./gradlew check`
Expected: PASS; every link resolves.

Run the source hygiene scan from `AGENTS.md` and the whitespace check:

```bash
python3 - <<'HYGIENE'
import pathlib
for root in ("jasper-terminal/src", "jasper-app/src", "jasper-buddy/src", "jasper-sdk/src", "jasper-sdk-testkit/src", "plugins"):
    for p in pathlib.Path(root).rglob("*.java"):
        text = p.read_text(encoding="utf-8")
        bad = sum(1 for c in text if 0xD800 <= ord(c) <= 0xDFFF or 0xE000 <= ord(c) <= 0xF8FF or (ord(c) < 0x20 and c not in "\n\t\r") or ord(c) == 0x7f)
        if bad: print(p, "bad chars:", bad)
HYGIENE
git diff --check 7749e13b
```

Expected: no output from either.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add AGENTS.md docs jasper-sdk/README.md
git commit -F - <<'EOF'
docs: describe the six-slot dock layout and record plan 1

The SDK, plugin authoring, app architecture, maintenance and configuration
guides describe stripes, slots, headers and ui-state.toml version 2.
STATUS records plan 1 and its checks.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Do not merge to `main` and do not push without the user. Hand the GUI checks below to the user.

---

## GUI checks for the user (agents do not launch the GUI)

Run `./gradlew :jasper-app:run` yourself. Back up `~/.config/jasper/ui-state.toml` first if you want to try the
version 1 migration more than once.

1. **First launch on your existing state:** the panels that were open are open in the same place and width
   (Sessions on the left). `ui-state.toml` now starts with `version = 2` and still has your `[windows]` and
   `[chooser]` sections.
2. **Look against IntelliJ:** a stripe on each side; the left one lists Sessions, Tunnels, File Servers and Sample
   on top (in the order the plugins register them), a short grey separator, SFTP, then the rail actions behind their
   own separator; the right stripe is empty but present. Selected buttons are rounded filled squares. Each open tool window has a
   header: bold title, then ⋮ and — at the right, on the tool window colour with no bar of its own; rounded frames;
   6px gaps.
3. **Two halves:** open Sessions and SFTP together; SFTP sits under Sessions. Drag the divider between them.
4. **Move to:** right-click a stripe icon and move a panel to each of the six slots in turn; do the same from the
   header's ⋮. The current slot is checked. Moving keeps the panel open; moving onto an open panel hides that one.
5. **Hide:** — on the header hides the window and typing goes straight to the terminal.
6. **Divider feel:** drag the left, right and bottom dividers and both splits. The cursor changes; a panel stops at
   120px; the terminal keeps at least 200 × 120. The bottom area spans from stripe to stripe.
7. **Squeeze:** with panels on both sides and at the bottom, make the window small, then large again: the bottom
   area gives way first, then the right, then the left; the sizes come back.
8. **View › Rail:** hides both stripes; the terminal goes edge to edge; panels stay reachable from View › Tools
   and their shortcuts (⇧⌘S for Sessions); show them again.
9. **Restart:** slots, stripe order, visibility, widths, heights and splits come back.
10. **Themes:** Jasper Dark and Light, Darcula and an imported theme: headers and frames take the tool window colour,
    stripes stay chrome; switch while a panel is hidden, then show it.
11. **Rail width** 28, 40 and 64 in Settings: both stripes follow.
12. **Two windows:** moving a panel in one leaves the other alone; a new window opens with the last saved layout.

---

## Plan self-review

**Spec coverage.**

| Spec section | Task |
|---|---|
| Decisions 1–2: two stripes, six slots, full-width bottom area | 1, 2, 6, 7, 8 |
| Decision 3 and §2 Headers: title, ⋮, —, 32px, no bar colour, focus back to the terminal | 5, 7, 8 |
| Decision 4 and §3: drag and drop, guides | Plan 2 |
| Decision 5: reorder within a stripe (plan 2); View › Rail hides both stripes | Plan 2; 8 |
| Decision 6 and §4 SDK: `withSplit()`, constructors kept, `withToggleActionId` keeps it, Javadoc, testkit, contract cases, docs | 3, 10 |
| Decision 7: own geometry function; preview and layout from the same function | 2, 7 (plan 2 reuses `compute`) |
| Decision 8: sizes per side | 1, 4, 7 |
| Decision 9 and "Defaults for existing panels": SFTP split, Remote minor bump | 9 |
| §1 Slots, State, Geometry (minimums, squeeze order, saved sizes untouched, gutters, rounded center) | 1, 2, 7 |
| §2 Stripes: groups, separator rule, rail actions, foot pinned, click, right-click menu, visibility, `view.rail` | 6, 8 |
| §2 in-content titles of the five panels | 9 |
| §4 App wiring: `PanelRegion` removed, `defaultSlot`, `HostedUi` mapping, `WindowContributions` over `DockState`, one listener | 3, 8 |
| §4 `ui-state.toml` v2: format, saved slot wins, append, per-entry fallback, v1 migration, several windows | 4, 8 |
| §5 Tests listed for plan 1 | 1, 2, 4, 5, 6, 7, 8 |
| §5 Failures: placeholder on a failing factory; a bad saved entry falls back alone | 8; 4 |
| Delivery and GUI checks | this plan; GUI section above |

**Placeholder scan.** No step says "TBD" or "similar to". The only blanks are the four test counts in the `STATUS.md`
entry, which exist only after the run; Step 2 says how to fill them.

**Type consistency.** Checked across tasks: `Slot.partner()`/`group()`/`stripe()`/`displayName()` (Task 1) in Tasks
5–8; `DockState.showing(Side)` in Task 7; `DockGeometry.Metrics.at`, `Handle`, `Divider.position()` in Task 7;
`UiState.Panel.LAST`, `UiState.panels()`, `UiState.Dock` (Task 4) in Task 8; `PanelEntry.defaultSlot()` (Task 3) in
Task 8; `ToolWindowHeaderTest.item` (Task 5) in Tasks 6–8; `DockTestSupport.show/hide/id` (Task 7, owner overloads
in Task 8); `WindowContent.area()/dock()/leftStripe()/rightStripe()` (Task 8) in Task 8's tests and plan 2.

**Verified before handing over.** Tasks 1, 2 and 4 (the pure model, the geometry and `UiState`) and the Swing
components of Tasks 5–7 were compiled and run headlessly in a scratch copy (GraalVM 21, JUnit 5.10, AssertJ 3.27,
FlatLaf 3.7, stubs for `AppIcons`, `ConfigSnapshot` and `Command`): 48 tests pass. The edit scripts in Tasks 3, 8,
9 and 10 were dry-run against copies of the current files and every replacement matched. Task 8's
`WindowContributions`, `WindowPanelsTest` and `RailWidthTest`, Task 7's `SidePanelRenderTest` and
`ToolWindowSurfaceTest`, and the theme loop in `ToolWindowAreaTest` need the full app and were not compiled; review
them first.

**Review Focus.** Each of the five lines at the top has its test in the owning task (named there).
