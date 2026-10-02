# Jasper Dock Layout Plan 2: Drag and Drop Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Drag tool windows by their stripe buttons and headers to any slot or stripe position, with IntelliJ's
guides: a translucent highlight where the window will land, a gap and placeholder in the target stripe, and the
window's icon at the cursor.

**Architecture:** Two pure additions to `dev.jasper.app.dock`: `DockDropResolver` turns the cursor into a target
(a stripe group and position, or a cell of the workspace's 3 × 2 grid), and `DockGeometry.landing` lays out a
"what if" copy of the state for the preview, with the same `compute` the real layout uses. In
`dev.jasper.app.workspace`, `ToolStripe` gains a drag view (all three groups, the dragged button lifted out, a gap
at the target), `DockDropOverlay` paints the guides on the layered pane's `DRAG_LAYER`, and `DockDrag` owns the
gesture with plain mouse events. `WindowContributions` applies drops through `DockState.move` and the new
`DockState.moveAndShow`.

**Tech Stack:** Java 25 on JBR 25, Gradle wrapper, Swing with FlatLaf 3.7, JUnit 6.1.3, AssertJ 3.27.7. No new
dependencies. No SDK change.

**Spec:** [Dock layout design](../specs/2026-10-01-jasper-dock-layout-design.md), §3 "Drag and drop (plan 2)" and §5.

**Prerequisite:** [Plan 1, six slots](2026-10-02-jasper-dock-layout-plan-1-six-slots.md), executed on this branch.
Every task here edits plan 1's code.

**Status:** Written 2026-10-02 on `claude/dock-layout` and approved by the user the same day. Before execution its
text was aligned with plan 1 as executed (commits `400dcba5` through `5cd22948`): the dock test counts in Tasks 1 and 2
(plan 1 ends with 29 dock tests, not 25), `ToolWindowAreaTest`'s count in Task 3 (9, not 8) and Task 6's `close()`
anchor (plan 1's final review removed the save on close and reworded that comment). Executing.

**Execution:** As plan 1: superpowers:subagent-driven-development, one implementer and one reviewer per task, then
one whole-branch review. Record deviations in this banner and in `docs/STATUS.md`.

## Global Constraints

Every task's requirements include this section, and plan 1's Global Constraints still hold.

- Gesture: press on a stripe window button or on a tool window header (its title or empty space; not its ⋮ or —
  buttons) and move more than 5px (scaled). Plain mouse events, never AWT drag and drop. `DockDrag` owns the gesture.
  **Esc**, or releasing with no target, cancels. Double-click does nothing.
- Targets are `(slot, index)` from the pure `DockDropResolver`: over a stripe, the group under the cursor (top,
  second, foot) and the insertion index from the button midpoints; over the workspace (between the stripes, below
  the header, above the status bar: the `ToolWindowArea`'s bounds), a 3 × 2 grid mirroring the stripes, rows thirds
  of the height and columns halves of the width; anywhere else, nothing.
- During a drag both stripes show all three groups with their separators; an empty group gets one button's height of
  drop space; the target group's buttons part to open a gap at the insertion index; the source button is lifted out.
- Guides on the layered pane's `DRAG_LAYER`: the area highlight (translucent fill, 1px border) exactly where the
  window would land, from `DockGeometry` on a what-if copy with the dragged window active in the target slot; the
  stripe placeholder (a highlighted rounded square in the gap); the ghost (the window's icon at the cursor). The real
  layout does not reflow during the drag.
- Colours: `DragAndDrop.areaBackground` and `DragAndDrop.borderColor` when the theme defines them, otherwise
  `Component.accentColor` at about 25 % alpha for the fill and 60 % for the border.
- Drops: a stripe drop moves the window and keeps its visibility; a workspace drop moves and shows it, and the slot's
  previously active window hides but stays on the stripe; dropping where it already is changes nothing. A move does
  not fire `onVisibility`; a workspace drop that shows a hidden window does.
- Failures: a plugin unloading or the window closing mid-drag cancels the drag; resizing the window mid-drag
  recomputes the targets.
- Rail actions are plain buttons and cannot be dragged. With the stripes hidden, drags start from headers only.
- `dev.jasper.app.dock` stays JDK-only (`java.awt.Point` and `Rectangle` only). Maximize and floating windows stay
  out of scope.
- Tests are headless and drive the gesture with synthetic `MouseEvent`s and `DockDrag.dispatchKeyEvent`. Never launch
  the GUI. Work only in `.worktrees/dock-layout` on `claude/dock-layout`; commit messages end with the trailer block
  shown in each task; do not merge to `main`.

## Review Focus

1. **A click on a stripe button must still toggle, and a drag must not also toggle** when the mouse is released on the
   button's old place. Tests: Task 5 `aPressBecomesADragOnlyPastFivePixels…`, Task 6 `aClickStillTogglesAndADragDoesNotAlsoToggle`.
2. **Something else changing the dock mid-drag** (a plugin unloading, a panel showing itself, rail actions changing):
   the drag ends cleanly, no overlay or gap is left behind. Tests: Task 5 `aDockChangeOrCancelEndsTheDragAndAPendingPress`,
   Task 6 `aPluginUnloadingOrTheWindowClosingMidDragCancelsIt`.
3. **The window resizing while the mouse is held still**: the target under the cursor is resolved again. Test: Task 5
   `resizingTheWindowMidDragResolvesTheTargetAgain`.
4. **Esc while the terminal has focus**: the drag takes Esc, the terminal does not see it; with no drag running, Esc
   is not claimed. Test: Task 5 `escapeOrAReleaseWithoutATargetCancels`.
5. **Dropping a window where it already is**, on its stripe or on its own workspace cell: nothing moves, and a hidden
   window dropped on its own cell shows in place. Tests: Task 1 `moveAndShowWhereTheWindowAlreadyShowsChangesNothing`,
   Task 5 `aPressBecomesADragOnlyPastFivePixels…`.

## Decisions made while planning (for the user's review)

1. Any dock change or stripe render cancels a running drag. The spec names a plugin unloading and the window closing;
   this rule covers both and also a panel that shows itself mid-drag.
2. The dragged stripe button is lifted out by giving it empty bounds, not by hiding it: Swing keeps sending the drag's
   mouse events to the component that was pressed, and a hidden component may stop receiving them.
3. A workspace drop on the window's own slot keeps its place in the group (and shows it); only a drop on another slot
   appends.
4. A stripe's three groups split its whole height, so anywhere on a stripe is a target; the rail actions' stretch
   belongs to the second group.
5. Targets are resolved against the drag layout without the gap, so opening the gap never moves the target under the
   cursor.
6. The highlight is the target slot's whole frame (gutters included) in the what-if layout. Its fill sits inside the
   1px border so the two translucent colours do not stack.
7. Esc is caught by a `KeyEventDispatcher` only while a drag runs.
8. Without a theme accent colour the guides use IntelliJ's blue, `#3574F0`.

## Baseline facts (verified 2026-10-02)

- After plan 1, `WindowContent` owns `dock()`, `area()`, `leftStripe()` and `rightStripe()`; `WindowContributions`
  has one `DockState` listener (`docked`) and `renderStripes()`; `ToolStripe.render` creates the buttons;
  `ToolWindowArea.sync` creates the headers.
- `JToggleButton` fires its action on release when its model is armed; disarming the model when a drag starts stops
  the release from also toggling.
- The Task 1–5 sources and tests below were compiled and run headlessly on top of plan 1's verified sources (GraalVM
  21, JUnit 5.10, AssertJ 3.27, FlatLaf 3.7, stubs for `AppIcons`, `ConfigSnapshot` and `Command`): 77 tests pass,
  plan 1's `ToolStripeTest` among them, unchanged. The Task 1 and 3 edit scripts reproduce those sources byte for byte
  from plan 1's. Task 6's scripts and Task 7's were dry-run against plan 1's resulting files.

## File map

| Task | Creates | Modifies |
|---|---|---|
| 1 | `DockDropModelTest` | `dock/{Slot,DockState,DockGeometry}.java` |
| 2 | `dock/DockDropResolver.java`, `DockDropResolverTest` | |
| 3 | `ToolStripeDragTest` | `workspace/ToolStripe.java` (whole file), `workspace/ToolWindowArea.java` (three hunks) |
| 4 | `workspace/DockDropOverlay.java`, `DockDropOverlayTest` | |
| 5 | `workspace/DockDrag.java`, `DockDragTest` | |
| 6 | `WindowDragTest` | `workspace/{WindowContent,WindowContributions}.java` |
| 7 | | `docs/{plugin-authoring,sdk-architecture,app-architecture,app-maintenance,STATUS}.md`, both plan banners |

Tests live beside their code: `jasper-app/src/test/java/dev/jasper/app/dock/` and `…/workspace/`.

---

## Task 1: `Slot.on`, `DockState.moveAndShow` and the landing preview

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/dock/Slot.java`, `DockState.java`, `DockGeometry.java` (one method each)
- Create: `jasper-app/src/test/java/dev/jasper/app/dock/DockDropModelTest.java`

**Interfaces:**
- Consumes: plan 1's `Slot`, `Side`, `DockState`, `DockGeometry.compute`.
- Produces: `static Slot Slot.on(Side stripe, Slot.Group group)`;
  `void DockState.moveAndShow(String id, Slot target, int index)` (one change: `layout` and `stripes` true, `hidden`
  the target's previously active window, `shown` the window only if it was hidden);
  `static Optional<Rectangle> DockGeometry.landing(Rectangle bounds, DockState state, String id, Slot target, Metrics metrics)`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/DockDropModelTest.java`:

```java
package dev.jasper.app.dock;

import dev.jasper.app.dock.DockGeometry.Metrics;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** The model half of a drop: the stripe groups' slots, moving and showing in one change, and the landing preview. */
class DockDropModelTest {
    private static final Rectangle BOUNDS = new Rectangle(0, 0, 1000, 600);
    private final DockState state = new DockState();
    private final List<DockState.Change> changes = new ArrayList<>();

    @Test void eachStripeGroupShowsOneSlot() {
        assertThat(Slot.on(Side.LEFT, Slot.Group.TOP)).isEqualTo(Slot.LEFT_TOP);
        assertThat(Slot.on(Side.LEFT, Slot.Group.SECOND)).isEqualTo(Slot.LEFT_BOTTOM);
        assertThat(Slot.on(Side.LEFT, Slot.Group.FOOT)).isEqualTo(Slot.BOTTOM_LEFT);
        assertThat(Slot.on(Side.RIGHT, Slot.Group.TOP)).isEqualTo(Slot.RIGHT_TOP);
        assertThat(Slot.on(Side.RIGHT, Slot.Group.SECOND)).isEqualTo(Slot.RIGHT_BOTTOM);
        assertThat(Slot.on(Side.RIGHT, Slot.Group.FOOT)).isEqualTo(Slot.BOTTOM_RIGHT);
        for (Slot slot : Slot.values()) assertThat(Slot.on(slot.stripe(), slot.group())).isSameAs(slot);
        assertThatIllegalArgumentException().isThrownBy(() -> Slot.on(Side.BOTTOM, Slot.Group.TOP));
    }

    @Test void moveAndShowShowsAHiddenWindowAndHidesTheTargetsActiveOne() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.BOTTOM_RIGHT); state.add("c", Slot.BOTTOM_RIGHT);
        state.show("b");
        state.addListener(changes::add);
        state.moveAndShow("a", Slot.BOTTOM_RIGHT, 2);
        assertThat(state.ids(Slot.BOTTOM_RIGHT)).containsExactly("b", "c", "a");
        assertThat(state.active(Slot.BOTTOM_RIGHT)).hasValue("a");
        assertThat(state.ids(Slot.BOTTOM_RIGHT)).as("the hidden window stays on the stripe").contains("b");
        assertThat(changes).containsExactly(new DockState.Change(true, true, List.of("b"), List.of("a")));
    }

    @Test void moveAndShowOfAShowingWindowIsAMoveForThePlugins() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.RIGHT_TOP);
        state.show("a"); state.show("b");
        state.addListener(changes::add);
        state.moveAndShow("a", Slot.RIGHT_TOP, 0);
        assertThat(state.ids(Slot.RIGHT_TOP)).containsExactly("a", "b");
        assertThat(state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(state.active(Slot.LEFT_TOP)).isEmpty();
        assertThat(changes).as("a stays visible: no shown entry").containsExactly(new DockState.Change(true, true, List.of("b"), List.of()));
    }

    @Test void moveAndShowWhereTheWindowAlreadyShowsChangesNothing() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP);
        state.show("a");
        state.addListener(changes::add);
        state.moveAndShow("a", Slot.LEFT_TOP, 0);
        state.moveAndShow("absent", Slot.LEFT_TOP, 0);
        assertThat(changes).isEmpty();
        state.moveAndShow("b", Slot.LEFT_TOP, 1);
        assertThat(state.ids(Slot.LEFT_TOP)).as("a hidden window shows in place").containsExactly("a", "b");
        assertThat(changes).containsExactly(new DockState.Change(true, true, List.of("a"), List.of("b")));
    }

    @Test void theLandingPreviewLaysOutACopyWithTheWindowShowingInTheTarget() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP);
        state.show("a");
        state.addListener(changes::add);
        assertThat(DockGeometry.landing(BOUNDS, state, "b", Slot.BOTTOM_RIGHT, Metrics.DEFAULT))
            .as("alone in the bottom area: its full width").hasValue(new Rectangle(0, 400, 1000, 200));
        assertThat(DockGeometry.landing(BOUNDS, state, "a", Slot.RIGHT_TOP, Metrics.DEFAULT)).hasValue(new Rectangle(740, 0, 260, 600));
        assertThat(DockGeometry.landing(BOUNDS, state, "b", Slot.LEFT_BOTTOM, Metrics.DEFAULT))
            .as("under the window the upper half shows").hasValue(new Rectangle(0, 303, 260, 297));
        assertThat(DockGeometry.landing(BOUNDS, state, "b", Slot.LEFT_TOP, Metrics.DEFAULT))
            .as("taking over its own slot").hasValue(new Rectangle(0, 0, 260, 600));
        assertThat(DockGeometry.landing(BOUNDS, state, "absent", Slot.LEFT_TOP, Metrics.DEFAULT)).isEmpty();
        assertThat(changes).as("the real state never changes").isEmpty();
        assertThat(state.slot("a")).hasValue(Slot.LEFT_TOP);
        assertThat(state.visible("b")).isFalse();
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.DockDropModelTest'`
Expected: FAIL — `cannot find symbol: method on(Side,Group)`, `moveAndShow`, `landing`.

- [ ] **Step 3: Add the three methods**

Save this script as `/tmp/dock-model.py` and run it from the worktree root with `python3 /tmp/dock-model.py`. It
inserts each method before an existing one; each insertion point must match exactly once:

```python
import pathlib

# Run from the worktree root after plan 1. Each insertion point must match exactly once, or nothing is written.
dock = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/dock")
edits = {
    "Slot.java": ("""    /** The slot for a side and split flag. */""", """    /** The slot a stripe group shows: the left stripe's top, second and foot groups, or the right stripe's. */
    public static Slot on(Side stripe, Group group) {
        if (stripe == Side.BOTTOM) throw new IllegalArgumentException("A stripe stands on the left or the right");
        return switch (group) {
            case TOP -> of(stripe, false);
            case SECOND -> of(stripe, true);
            case FOOT -> of(Side.BOTTOM, stripe == Side.RIGHT);
        };
    }

    /** The slot for a side and split flag. */"""),
    "DockState.java": ("""    /** Sets a side's size, clamped to {@link #MIN_SIZE}..{@link #MAX_SIZE} unscaled pixels. */""", """    /**
     * Moves the window and shows it, in one change: a drop on the workspace. The window the target slot was showing
     * hides but stays on its stripe. A window that already shows in that place changes nothing.
     *
     * @param index position among the target slot's other windows, clamped; within one slot, its new position
     */
    public void moveAndShow(String id, Slot target, int index) {
        Objects.requireNonNull(target, "target");
        Optional<Slot> from = slot(id);
        if (from.isEmpty()) return;
        boolean wasVisible = visible(id);
        List<String> source = order.get(from.get());
        List<String> ids = order.get(target);
        boolean sameSlot = from.get() == target;
        int clamped = Math.clamp(index, 0, sameSlot ? ids.size() - 1 : ids.size());
        boolean moved = !(sameSlot && clamped == source.indexOf(id));
        if (!moved && wasVisible) return;
        if (moved) {
            source.remove(id);
            ids.add(clamped, id);
        }
        if (wasVisible) active.remove(from.get(), id);
        String previous = active.put(target, id);
        fire(new Change(true, true, previous == null || previous.equals(id) ? List.of() : List.of(previous),
            wasVisible ? List.of() : List.of(id)));
    }

    /** Sets a side's size, clamped to {@link #MIN_SIZE}..{@link #MAX_SIZE} unscaled pixels. */"""),
    "DockGeometry.java": ("""    /**
     * Applies dragging a divider:""", """    /**
     * Where a window would land if it moved to the target slot and showed there: the drop preview. It lays out a copy
     * of the state, so the real layout does not change, and it uses {@link #compute}, so preview and layout agree. A
     * window already in the target slot keeps its place there. Empty when the window is not docked.
     */
    public static Optional<Rectangle> landing(Rectangle bounds, DockState state, String id, Slot target, Metrics metrics) {
        DockState whatIf = state.copy();
        if (whatIf.slot(id).isEmpty()) return Optional.empty();
        List<String> ids = whatIf.ids(target);
        whatIf.moveAndShow(id, target, ids.contains(id) ? ids.indexOf(id) : ids.size());
        return compute(bounds, whatIf, metrics).slot(target);
    }

    /**
     * Applies dragging a divider:"""),
}
changed = {}
for name, (anchor, replacement) in edits.items():
    path = dock / name
    text = path.read_text(encoding="utf-8")
    if text.count(anchor) != 1: raise SystemExit(f"{name}: expected one insertion point, found {text.count(anchor)}")
    changed[path] = text.replace(anchor, replacement)
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path)
```

- [ ] **Step 4: Run the tests to make sure they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*'`
Expected: PASS, 34 tests (plan 1's 29 and these 5).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/dock jasper-app/src/test/java/dev/jasper/app/dock/DockDropModelTest.java
git commit -F - <<'EOF'
feat(dock): move-and-show and the landing preview for drops

DockState.moveAndShow moves a window and shows it in one change; the slot's
previous window hides but stays on its stripe. DockGeometry.landing lays
out a copy with the window showing in the target, so the drop preview and
the real layout come from the same function. Slot.on names the slot a
stripe group shows.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 2: `DockDropResolver`

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/dock/DockDropResolver.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/dock/DockDropResolverTest.java`

**Interfaces:**
- Consumes: plan 1's `Slot`.
- Produces (`public`, package `dev.jasper.app.dock`): `final class DockDropResolver` with
  `static Optional<Target> resolve(Point cursor, List<Group> groups, Rectangle workspace)`;
  `record Group(Slot slot, Rectangle bounds, List<Rectangle> buttons)` (buttons without the dragged one; accessors
  return copies); `record Target(Slot slot, OptionalInt index)` with `static Target stripe(Slot, int)`,
  `static Target workspace(Slot)` and `boolean onStripe()`.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/dock/DockDropResolverTest.java`:

```java
package dev.jasper.app.dock;

import dev.jasper.app.dock.DockDropResolver.Group;
import dev.jasper.app.dock.DockDropResolver.Target;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class DockDropResolverTest {
    /** A workspace between two 28px stripes, under a 30px header. */
    private static final Rectangle WORKSPACE = new Rectangle(28, 30, 1000, 600);

    private static Target at(int x, int y) {
        return DockDropResolver.resolve(new Point(x, y), List.of(), WORKSPACE).orElseThrow();
    }

    @Test void theWorkspaceIsAThreeByTwoGridThatMirrorsTheStripes() {
        assertThat(at(100, 60)).isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(900, 60)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(at(100, 330)).isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(900, 330)).isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(100, 600)).isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(at(900, 600)).isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(at(100, 60).onStripe()).isFalse();
    }

    @Test void rowsAreThirdsAndColumnsHalvesOfTheWorkspace() {
        assertThat(at(527, 100).slot()).as("just left of the middle").isEqualTo(Slot.LEFT_TOP);
        assertThat(at(528, 100).slot()).as("the middle belongs to the right").isEqualTo(Slot.RIGHT_TOP);
        assertThat(at(100, 229).slot()).as("just above a third").isEqualTo(Slot.LEFT_TOP);
        assertThat(at(100, 230).slot()).as("a third down starts the second row").isEqualTo(Slot.LEFT_BOTTOM);
        assertThat(at(100, 430).slot()).as("two thirds down starts the foot row").isEqualTo(Slot.BOTTOM_LEFT);
        assertThat(at(1027, 629).slot()).as("the last pixel").isEqualTo(Slot.BOTTOM_RIGHT);
    }

    @Test void outsideTheWorkspaceAndTheStripesThereIsNoTarget() {
        assertThat(DockDropResolver.resolve(new Point(100, 10), List.of(), WORKSPACE)).as("the title bar").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(100, 700), List.of(), WORKSPACE)).as("the status bar").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(-5, 100), List.of(), WORKSPACE)).as("outside the window").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(0, 0), List.of(), new Rectangle())).isEmpty();
    }

    @Test void overAStripeTheIndexComesFromTheButtonMidpoints() {
        var top = new Group(Slot.LEFT_TOP, new Rectangle(0, 30, 28, 100),
            List.of(new Rectangle(3, 34, 22, 22), new Rectangle(3, 60, 22, 22)));
        var foot = new Group(Slot.BOTTOM_LEFT, new Rectangle(0, 400, 28, 230), List.of());
        List<Group> groups = List.of(top, foot);
        assertThat(DockDropResolver.resolve(new Point(10, 40), groups, WORKSPACE)).hasValue(Target.stripe(Slot.LEFT_TOP, 0));
        assertThat(DockDropResolver.resolve(new Point(10, 46), groups, WORKSPACE)).as("past the first button's middle")
            .hasValue(Target.stripe(Slot.LEFT_TOP, 1));
        assertThat(DockDropResolver.resolve(new Point(10, 120), groups, WORKSPACE)).hasValue(Target.stripe(Slot.LEFT_TOP, 2));
        assertThat(DockDropResolver.resolve(new Point(10, 500), groups, WORKSPACE)).as("an empty group takes index 0")
            .hasValue(Target.stripe(Slot.BOTTOM_LEFT, 0));
        assertThat(DockDropResolver.resolve(new Point(10, 200), groups, WORKSPACE)).as("a stripe gap between groups").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(10, 40), groups, WORKSPACE).orElseThrow().onStripe()).isTrue();
    }

    @Test void groupsHandOutCopies() {
        var group = new Group(Slot.RIGHT_TOP, new Rectangle(0, 0, 28, 100), List.of(new Rectangle(3, 4, 22, 22)));
        group.bounds().width = 1;
        group.buttons().getFirst().y = 99;
        assertThat(group.bounds().width).isEqualTo(28);
        assertThat(group.buttons().getFirst().y).isEqualTo(4);
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.DockDropResolverTest'`
Expected: FAIL — `cannot find symbol: class DockDropResolver`.

- [ ] **Step 3: Write the resolver**

Create `jasper-app/src/main/java/dev/jasper/app/dock/DockDropResolver.java`:

```java
package dev.jasper.app.dock;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * Where a dragged tool window would drop, from the cursor alone: a stripe group and a position in it, a cell of the
 * workspace's 3 x 2 grid, or nothing. Pure; every rectangle and the cursor are in the caller's one coordinate space.
 */
public final class DockDropResolver {
    private DockDropResolver() { }

    /**
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

        @Override public Rectangle bounds() { return new Rectangle(bounds); }

        @Override public List<Rectangle> buttons() { return buttons.stream().map(Rectangle::new).toList(); }
    }

    /**
     * A drop.
     *
     * @param slot where the window goes
     * @param index over a stripe, its position among the group's other windows; empty for a workspace drop, which
     *              appends the window to the slot's group
     */
    public record Target(Slot slot, OptionalInt index) {
        public Target {
            Objects.requireNonNull(slot, "slot");
            Objects.requireNonNull(index, "index");
        }

        public static Target stripe(Slot slot, int index) { return new Target(slot, OptionalInt.of(index)); }

        public static Target workspace(Slot slot) { return new Target(slot, OptionalInt.empty()); }

        public boolean onStripe() { return index.isPresent(); }
    }

    /** The workspace grid mirrors the stripes: rows are the top, second and foot groups; columns the two stripes. */
    private static final Slot[][] GRID = {
        {Slot.LEFT_TOP, Slot.RIGHT_TOP}, {Slot.LEFT_BOTTOM, Slot.RIGHT_BOTTOM}, {Slot.BOTTOM_LEFT, Slot.BOTTOM_RIGHT}};

    /**
     * Over a stripe group: that group, at the number of its buttons whose middle is above the cursor. Over the
     * workspace: the grid cell, rows a third of its height and columns half its width. Anywhere else: nothing.
     */
    public static Optional<Target> resolve(Point cursor, List<Group> groups, Rectangle workspace) {
        for (Group group : groups) {
            if (!group.bounds.contains(cursor)) continue;
            int index = 0;
            for (Rectangle button : group.buttons) if (button.y + button.height / 2.0 < cursor.y) index++;
            return Optional.of(Target.stripe(group.slot, index));
        }
        if (workspace.isEmpty() || !workspace.contains(cursor)) return Optional.empty();
        int row = (int) Math.min(2, (long) (cursor.y - workspace.y) * 3 / workspace.height);
        int column = (cursor.x - workspace.x) * 2 < workspace.width ? 0 : 1;
        return Optional.of(Target.workspace(GRID[row][column]));
    }
}
```

- [ ] **Step 4: Run the tests and the gate**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.dock.*'` and `./gradlew verifyApplicationArchitecture`
Expected: PASS, 39 tests; the gate accepts `java.awt.Point` and `java.awt.Rectangle`.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/dock/DockDropResolver.java jasper-app/src/test/java/dev/jasper/app/dock/DockDropResolverTest.java
git commit -F - <<'EOF'
feat(dock): resolve a drag's drop target from the cursor

Over a stripe group: that slot, at the index the button midpoints give.
Over the workspace: the 3 x 2 grid that mirrors the stripes, rows thirds
and columns halves. Anywhere else: no target.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 3: The stripe's drag view, and drag sources on buttons and headers

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java` (whole file)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java` (three hunks, by script)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeDragTest.java`

**Interfaces:**
- Consumes: Task 1's `Slot.on`; Task 2's `DockDropResolver.Group`; plan 1's `ToolStripe`, `ToolWindowArea`,
  `ToolWindowHeader.titleLabel()`.
- Produces (package-private, EDT only):
  - On `ToolStripe`: field `BiConsumer<JComponent, String> dragSource` (called for each window button a render
    creates, not for actions); `void beginDrag(String id)`, `void showGap(Slot slot, int index)` (no gap when the slot
    is on the other stripe or null), `void endDrag()`, `boolean dragging()`, `Optional<Rectangle> gapBounds()` and
    `List<DockDropResolver.Group> dropGroups()` (stripe coordinates, drag layout without the gap). A render ends any
    drag view. Normal layout is unchanged: plan 1's `ToolStripeTest` passes as it is.
  - On `ToolWindowArea`: field `BiConsumer<JComponent, String> dragSource` (called with each new header and its
    title label) and `static DockGeometry.Metrics metrics()` (was private).

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeDragTest.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockState;
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
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** The stripe during a dock drag: all three groups, the lifted button, the gap, and the groups a drag resolves. */
@ExtendWith(EdtTestExtension.class)
class ToolStripeDragTest {
    private static int s(int value) { return UIScale.scale(value); }

    /** Left stripe, 400px tall: a and b on top, c at the foot, one rail action. */
    private static ToolStripe stripe() {
        var stripe = new ToolStripe(Side.LEFT);
        stripe.render(List.of(new ToolStripe.Button("a", "A", null, Slot.LEFT_TOP, true), new ToolStripe.Button("b", "B", null, Slot.LEFT_TOP, false),
            new ToolStripe.Button("c", "C", null, Slot.BOTTOM_LEFT, false)), List.of(new AbstractAction("Open") {
                @Override public void actionPerformed(ActionEvent event) { }
            }));
        stripe.setSize(stripe.getPreferredSize().width, s(400));
        stripe.doLayout();
        return stripe;
    }

    private static AbstractButton button(ToolStripe stripe, String tip) {
        return stripe.buttons().stream().filter(button -> tip.equals(button.getToolTipText())).findFirst().orElseThrow();
    }

    private static List<Rectangle> separators(ToolStripe stripe) {
        return Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance).map(Component::getBounds)
            .filter(bounds -> !bounds.isEmpty()).toList();
    }

    @Test void aDragShowsAllThreeGroupsAndLiftsTheDraggedButton() {
        var stripe = stripe();
        int step = s(22) + s(4);
        assertThat(separators(stripe)).as("normally only the actions' separator").hasSize(1);
        stripe.beginDrag("a");
        assertThat(stripe.dragging()).isTrue();
        assertThat(button(stripe, "A").getBounds().isEmpty()).as("the dragged button is lifted out").isTrue();
        assertThat(button(stripe, "A").isVisible()).as("but stays a visible child, so its drag events keep coming").isTrue();
        assertThat(button(stripe, "B").getY()).isEqualTo(s(4));
        assertThat(separators(stripe)).as("the second group's separator joins").hasSize(2);
        assertThat(button(stripe, "Open").getY()).as("after the empty second group's drop space")
            .isEqualTo(s(4) + step + s(8) + step + s(8));
        assertThat(button(stripe, "C").getY()).isEqualTo(s(400) - s(4) - s(22));
        assertThat(stripe.gapBounds()).isEmpty();
    }

    @Test void theDropGroupsSplitTheStripeAndLeaveTheLiftedButtonOut() {
        var stripe = stripe();
        stripe.beginDrag("a");
        List<DockDropResolver.Group> groups = stripe.dropGroups();
        assertThat(groups).extracting(DockDropResolver.Group::slot).containsExactly(Slot.LEFT_TOP, Slot.LEFT_BOTTOM, Slot.BOTTOM_LEFT);
        int width = stripe.getWidth(), step = s(22) + s(4);
        int split = s(4) + step + s(4), end = s(4) + step + s(8) + step + s(8) + step, footTop = s(400) - s(4) - s(22);
        int boundary = (end + footTop) / 2;
        assertThat(groups.get(0).bounds()).isEqualTo(new Rectangle(0, 0, width, split));
        assertThat(groups.get(0).buttons()).containsExactly(new Rectangle(s(3), s(4), s(22), s(22)));
        assertThat(groups.get(1).bounds()).isEqualTo(new Rectangle(0, split, width, boundary - split));
        assertThat(groups.get(1).buttons()).isEmpty();
        assertThat(groups.get(2).bounds()).isEqualTo(new Rectangle(0, boundary, width, s(400) - boundary));
        assertThat(groups.get(2).buttons()).containsExactly(new Rectangle(s(3), footTop, s(22), s(22)));
    }

    @Test void theGapOpensAtTheIndexInTheTargetGroup() {
        var stripe = stripe();
        int step = s(22) + s(4);
        stripe.beginDrag("a");
        stripe.showGap(Slot.LEFT_TOP, 0);
        assertThat(stripe.gapBounds()).hasValue(new Rectangle(s(3), s(4), s(22), s(22)));
        assertThat(button(stripe, "B").getY()).as("the buttons part").isEqualTo(s(4) + step);
        stripe.showGap(Slot.LEFT_BOTTOM, 0);
        assertThat(stripe.gapBounds()).as("in the empty second group's drop space").hasValue(new Rectangle(s(3), s(4) + step + s(8), s(22), s(22)));
        assertThat(button(stripe, "B").getY()).isEqualTo(s(4));
        stripe.showGap(Slot.BOTTOM_LEFT, 0);
        assertThat(stripe.gapBounds()).as("the foot grows upward").hasValue(new Rectangle(s(3), s(400) - s(4) - s(22) - step, s(22), s(22)));
        assertThat(button(stripe, "C").getY()).isEqualTo(s(400) - s(4) - s(22));
        stripe.showGap(Slot.BOTTOM_LEFT, 7);
        assertThat(stripe.gapBounds()).as("an index past the end clamps").hasValue(new Rectangle(s(3), s(400) - s(4) - s(22), s(22), s(22)));
        stripe.showGap(Slot.RIGHT_TOP, 0);
        assertThat(stripe.gapBounds()).as("a target on the other stripe").isEmpty();
        stripe.showGap(null, 0);
        assertThat(stripe.gapBounds()).isEmpty();
    }

    @Test void endingTheDragRestoresTheNormalStripe() {
        var stripe = stripe();
        stripe.beginDrag("a");
        stripe.showGap(Slot.LEFT_BOTTOM, 0);
        stripe.endDrag();
        assertThat(stripe.dragging()).isFalse();
        assertThat(stripe.gapBounds()).isEmpty();
        assertThat(separators(stripe)).hasSize(1);
        assertThat(Arrays.stream(stripe.getComponents()).filter(JSeparator.class::isInstance)).as("the drag's separator is gone").hasSize(1);
        assertThat(button(stripe, "A").getBounds()).isEqualTo(new Rectangle(s(3), s(4), s(22), s(22)));
        stripe.showGap(Slot.LEFT_TOP, 0);
        assertThat(stripe.gapBounds()).as("no gap outside a drag").isEmpty();
    }

    @Test void aDragFromTheOtherStripeLiftsNothingHereAndARenderEndsTheDragView() {
        var stripe = stripe();
        stripe.beginDrag("z");
        assertThat(button(stripe, "A").getBounds().isEmpty()).isFalse();
        stripe.render(List.of(), List.of());
        assertThat(stripe.dragging()).isFalse();
    }

    @Test void dragSourcesHearOfEveryWindowButtonButNoAction() {
        List<String> seen = new ArrayList<>();
        var stripe = new ToolStripe(Side.RIGHT);
        stripe.dragSource = (component, id) -> seen.add(((AbstractButton) component).getToolTipText() + ":" + id);
        stripe.render(List.of(new ToolStripe.Button("x", "X", null, Slot.RIGHT_TOP, false),
            new ToolStripe.Button("y", "Y", null, Slot.BOTTOM_RIGHT, false)), List.of(new AbstractAction("Open") {
                @Override public void actionPerformed(ActionEvent event) { }
            }));
        assertThat(seen).containsExactly("X:x", "Y:y");
    }

    @Test void theAreaOffersEachHeaderAndItsTitleAsADragSource() {
        List<String> seen = new ArrayList<>();
        var area = new ToolWindowArea(new JPanel(), new DockState());
        area.dragSource = (component, id) -> seen.add((component instanceof ToolWindowHeader ? "header" : component instanceof JLabel ? "title" : "other") + ":" + id);
        DockTestSupport.show(area, Slot.LEFT_TOP, new JLabel("hosts"), 260);
        assertThat(seen).containsExactly("header:test.left-top", "title:test.left-top");
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolStripeDragTest'`
Expected: FAIL — `cannot find symbol: method beginDrag(String)`, `dragSource`.

- [ ] **Step 3: Give the stripe its drag view**

Replace `jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java` with this version. Its normal layout is
plan 1's; `place` now computes it, and during a drag also the drop groups and the gap:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.commands.Command;
import dev.jasper.app.config.ConfigSnapshot;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.platform.AppIcons;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Insets;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.swing.AbstractButton;
import javax.swing.Action;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JToggleButton;
import javax.swing.UIManager;

/**
 * One of the window's two tool window stripes (IntelliJ's tool window bars). Top to bottom: the top group; a separator
 * when the second group has icons; the second group; on the left stripe, the plugins' rail actions behind their own
 * separator; flexible space; and the foot group, pinned to the bottom. A button is selected while its window shows;
 * a click toggles the window and a right-click opens its {@link ToolWindowMenu}. During a dock drag the stripe shows
 * all three groups with their separators (an empty group as one button's drop space), lifts the dragged window's
 * button out, and opens a gap where the window would drop. The stripe owns no state: the window renders it again
 * after every change. EDT only.
 */
final class ToolStripe extends JPanel {
    /** One tool window's button. */
    record Button(String id, String title, Icon icon, Slot slot, boolean selected) { }

    /** Where the last layout put everything; during a drag, also each group's drop bounds and the gap. */
    private static final class Placement {
        final Map<Component, Rectangle> bounds = new IdentityHashMap<>();
        final EnumMap<Slot.Group, List<Rectangle>> buttons = new EnumMap<>(Slot.Group.class);
        final EnumMap<Slot.Group, Rectangle> groups = new EnumMap<>(Slot.Group.class);
        Rectangle gap;
    }

    Consumer<String> onToggle = id -> { };
    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    /** Hears of each window button a render creates, so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };
    private final Side side;
    private final List<AbstractButton> buttons = new ArrayList<>();
    private final EnumMap<Slot.Group, List<AbstractButton>> groups = new EnumMap<>(Slot.Group.class);
    private final Map<AbstractButton, String> ids = new IdentityHashMap<>();
    private final List<AbstractButton> actions = new ArrayList<>();
    /** The separators the normal layout has, or null; a drag adds the missing ones for its duration. */
    private JSeparator secondSeparator, actionsSeparator;
    private final List<JSeparator> dragSeparators = new ArrayList<>();
    private boolean dragging;
    private String lifted;
    private Slot.Group gapGroup;
    private int gapIndex;
    private Rectangle gap;
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
     * slot belongs to the other stripe are ignored. A render ends any drag view.
     */
    void render(List<Button> windows, List<Action> contributed) {
        dragging = false; lifted = null; gapGroup = null; gap = null;
        removeAll();
        buttons.clear(); ids.clear(); actions.clear(); dragSeparators.clear();
        for (Slot.Group group : Slot.Group.values()) groups.put(group, new ArrayList<>());
        secondSeparator = null; actionsSeparator = null;
        List<Button> mine = windows.stream().filter(window -> window.slot().stripe() == side).toList();
        for (Button window : group(mine, Slot.Group.TOP)) groups.get(Slot.Group.TOP).add(toggle(window));
        List<Button> second = group(mine, Slot.Group.SECOND);
        if (!second.isEmpty()) secondSeparator = new JSeparator();
        for (Button window : second) groups.get(Slot.Group.SECOND).add(toggle(window));
        boolean windowsAbove = !groups.get(Slot.Group.TOP).isEmpty() || !groups.get(Slot.Group.SECOND).isEmpty();
        if (!contributed.isEmpty() && windowsAbove) actionsSeparator = new JSeparator();
        for (Action action : contributed) actions.add(action(action));
        for (Button window : group(mine, Slot.Group.FOOT)) groups.get(Slot.Group.FOOT).add(toggle(window));
        groups.get(Slot.Group.TOP).forEach(this::add);
        if (secondSeparator != null) add(secondSeparator);
        groups.get(Slot.Group.SECOND).forEach(this::add);
        if (actionsSeparator != null) add(actionsSeparator);
        actions.forEach(this::add);
        groups.get(Slot.Group.FOOT).forEach(this::add);
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
        ids.put(button, window.id());
        dragSource.accept(button, window.id());
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

    /**
     * Shows all three groups for a drag and lifts the dragged window's button out of its place.
     *
     * @param id the dragged window; it may be on the other stripe
     */
    void beginDrag(String id) {
        dragging = true;
        lifted = id;
        gapGroup = null;
        if (secondSeparator == null) secondSeparator = dragSeparator();
        if (actionsSeparator == null && !actions.isEmpty()) actionsSeparator = dragSeparator();
        doLayout(); repaint();
    }

    private JSeparator dragSeparator() {
        var separator = new JSeparator();
        dragSeparators.add(separator);
        add(separator);
        return separator;
    }

    /** Opens the gap at index in the slot's group when the slot is on this stripe, and closes it otherwise. */
    void showGap(Slot slot, int index) {
        if (!dragging) return;
        Slot.Group group = slot != null && slot.stripe() == side ? slot.group() : null;
        if (group == gapGroup && (group == null || index == gapIndex)) return;
        gapGroup = group;
        gapIndex = index;
        doLayout(); repaint();
    }

    /** Back to the normal stripe after a drag, whether it dropped or not. */
    void endDrag() {
        if (!dragging) return;
        dragging = false; lifted = null; gapGroup = null; gap = null;
        for (JSeparator separator : dragSeparators) {
            remove(separator);
            if (separator == secondSeparator) secondSeparator = null;
            if (separator == actionsSeparator) actionsSeparator = null;
        }
        dragSeparators.clear();
        revalidate(); doLayout(); repaint();
    }

    boolean dragging() { return dragging; }

    /** The gap the last layout opened, in this stripe's coordinates. */
    Optional<Rectangle> gapBounds() { return Optional.ofNullable(gap).map(Rectangle::new); }

    /**
     * The three groups as a drag resolves them, in this stripe's coordinates: they split the stripe from top to bottom,
     * and their buttons are where the drag layout puts them before any gap opens, without the lifted one.
     */
    List<DockDropResolver.Group> dropGroups() {
        Placement placement = place(false);
        List<DockDropResolver.Group> result = new ArrayList<>();
        for (Slot.Group group : Slot.Group.values())
            result.add(new DockDropResolver.Group(Slot.on(side, group), placement.groups.get(group), placement.buttons.get(group)));
        return result;
    }

    @Override public Dimension getMinimumSize() { return new Dimension(UIScale.scale(railWidth), UIScale.scale(80)); }
    @Override public Dimension getPreferredSize() { return getMinimumSize(); }

    @Override public void doLayout() {
        Placement placement = place(true);
        for (Component child : getComponents()) child.setBounds(placement.bounds.getOrDefault(child, new Rectangle()));
        gap = placement.gap;
    }

    /**
     * Square buttons 3 px in from each side and 4 px apart; separators inset 7 px; the foot ends 4 px from the bottom.
     * During a drag every group shows, the lifted button gets no space and, with the gap, the gap takes one button's.
     */
    private Placement place(boolean withGap) {
        var placement = new Placement();
        int x = UIScale.scale(3), square = UIScale.scale(railWidth - 6), step = square + UIScale.scale(4);
        int y = column(Slot.Group.TOP, UIScale.scale(4), x, square, step, withGap, placement);
        int split = y;
        if (secondSeparator != null && (dragging || !groups.get(Slot.Group.SECOND).isEmpty())) {
            placement.bounds.put(secondSeparator, separator(y));
            split = y + UIScale.scale(4);
            y += UIScale.scale(8);
        }
        y = column(Slot.Group.SECOND, y, x, square, step, withGap, placement);
        if (actionsSeparator != null) {
            placement.bounds.put(actionsSeparator, separator(y));
            y += UIScale.scale(8);
        }
        for (AbstractButton action : actions) {
            placement.bounds.put(action, new Rectangle(x, y, square, square));
            y += step;
        }
        int count = footCount(withGap);
        int footHeight = count == 0 ? 0 : count * step - UIScale.scale(4);
        int footY = Math.max(y, getHeight() - UIScale.scale(4) - footHeight);
        column(Slot.Group.FOOT, footY, x, square, step, withGap, placement);
        int boundary = (y + footY) / 2;
        placement.groups.put(Slot.Group.TOP, new Rectangle(0, 0, getWidth(), split));
        placement.groups.put(Slot.Group.SECOND, new Rectangle(0, split, getWidth(), Math.max(0, boundary - split)));
        placement.groups.put(Slot.Group.FOOT, new Rectangle(0, boundary, getWidth(), Math.max(0, getHeight() - boundary)));
        return placement;
    }

    private Rectangle separator(int y) {
        return new Rectangle(UIScale.scale(7), y + UIScale.scale(3), UIScale.scale(railWidth - 14), UIScale.scale(1));
    }

    /** The group's shown buttons, the lifted one left out. */
    private List<AbstractButton> shown(Slot.Group group) {
        return groups.get(group).stream().filter(button -> !dragging || !ids.get(button).equals(lifted)).toList();
    }

    private int footCount(boolean withGap) {
        int shown = shown(Slot.Group.FOOT).size();
        boolean gapHere = withGap && dragging && gapGroup == Slot.Group.FOOT;
        return shown + (gapHere || dragging && shown == 0 ? 1 : 0);
    }

    /** Lays a group out downward from y and returns where the next item starts. */
    private int column(Slot.Group group, int y, int x, int square, int step, boolean withGap, Placement placement) {
        List<AbstractButton> shown = shown(group);
        boolean gapHere = withGap && dragging && gapGroup == group;
        int gapAt = Math.clamp(gapIndex, 0, shown.size());
        List<Rectangle> rects = new ArrayList<>();
        for (int i = 0; i <= shown.size(); i++) {
            if (gapHere && i == gapAt) {
                placement.gap = new Rectangle(x, y, square, square);
                y += step;
            }
            if (i == shown.size()) break;
            var at = new Rectangle(x, y, square, square);
            placement.bounds.put(shown.get(i), at);
            rects.add(at);
            y += step;
        }
        // An empty group shows one button's drop space during a drag.
        if (dragging && shown.isEmpty() && !gapHere) y += step;
        placement.buttons.put(group, rects);
        return y;
    }

    void refreshTheme() {
        setBackground(UIManager.getColor("Jasper.titleBackground"));
        repaint();
    }
}
```

Save this script as `/tmp/dock-area.py` and run it from the worktree root with `python3 /tmp/dock-area.py`:

```python
import pathlib

# Run from the worktree root after plan 1. Every replacement must match exactly once, or nothing is written.
path = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java")
pairs = [
    ("""    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };

    ToolWindowArea(""", """    BiConsumer<String, Slot> onMove = (id, slot) -> { };
    Consumer<String> onHide = id -> { };
    /** Hears of each header a sync creates (and its title label), so that a drag can start from it. */
    BiConsumer<JComponent, String> dragSource = (component, id) -> { };

    ToolWindowArea("""),
    ("""                host = new Host(true);
                host.content = wanted.component();
                host.add(header(id, wanted.title()), BorderLayout.NORTH);""", """                host = new Host(true);
                host.content = wanted.component();
                ToolWindowHeader header = header(id, wanted.title());
                host.add(header, BorderLayout.NORTH);
                dragSource.accept(header, id);
                dragSource.accept(header.titleLabel(), id);"""),
    ("""    private static DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }""",
     """    /** The dock metrics at the current UI scale; the drag's preview uses the same. */
    static DockGeometry.Metrics metrics() { return DockGeometry.Metrics.at(UIScale.getUserScaleFactor()); }"""),
]
text = path.read_text(encoding="utf-8")
for old, new in pairs:
    if text.count(old) != 1: raise SystemExit(f"expected one match, found {text.count(old)}: {old.strip()[:70]}")
    text = text.replace(old, new)
path.write_text(text, encoding="utf-8")
print("updated", path)
```

- [ ] **Step 4: Run the stripe and area tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.ToolStripe*' --tests 'dev.jasper.app.workspace.ToolWindowAreaTest'`
Expected: PASS (`ToolStripeDragTest` 7, `ToolStripeTest` 5, `ToolWindowAreaTest` 9).

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/ToolStripe.java jasper-app/src/main/java/dev/jasper/app/workspace/ToolWindowArea.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/ToolStripeDragTest.java
git commit -F - <<'EOF'
feat(workspace): give the stripes a drag view and offer drag sources

During a drag a stripe shows all three groups with their separators, an
empty group as one button's drop space, lifts the dragged button out and
opens a gap at the target index. Stripe window buttons and tool window
headers announce themselves as drag sources; rail actions do not.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 4: `DockDropOverlay`, the guides

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/DockDropOverlay.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java`

**Interfaces:**
- Consumes: nothing new.
- Produces (package-private, EDT only): `final class DockDropOverlay extends JComponent` with
  `void show(Rectangle highlight, Rectangle placeholder, Icon ghost, Point cursor)` (each may be null),
  `void clear()`, `Rectangle highlight()`, `Rectangle placeholder()`, `static Color fill()`, `static Color border()`;
  `contains` is always false, so mouse events pass through.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.testsupport.EdtTestExtension;
import java.awt.Color;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.util.Map;
import javax.swing.ImageIcon;
import javax.swing.UIManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(EdtTestExtension.class)
class DockDropOverlayTest {
    private static final String[] KEYS = {"DragAndDrop.areaBackground", "DragAndDrop.borderColor", "Component.accentColor"};
    private final Map<String, Object> saved = new java.util.HashMap<>();

    DockDropOverlayTest() { for (String key : KEYS) saved.put(key, UIManager.get(key)); }

    @AfterEach void restore() { saved.forEach(UIManager::put); }

    private static BufferedImage paint(DockDropOverlay overlay) {
        overlay.setSize(400, 300);
        var image = new BufferedImage(400, 300, BufferedImage.TYPE_INT_ARGB);
        var g = image.createGraphics();
        try { overlay.paint(g); } finally { g.dispose(); }
        return image;
    }

    /** Within three steps per channel: compositing a translucent colour onto a clear image rounds. */
    private static void assertColour(BufferedImage image, int x, int y, Color expected) {
        var actual = new Color(image.getRGB(x, y), true);
        assertThat(actual.getAlpha()).as("alpha at %s,%s", x, y).isEqualTo(expected.getAlpha());
        for (int[] channel : new int[][]{{actual.getRed(), expected.getRed()}, {actual.getGreen(), expected.getGreen()}, {actual.getBlue(), expected.getBlue()}})
            assertThat(Math.abs(channel[0] - channel[1])).as("colour at %s,%s: %s", x, y, actual).isLessThanOrEqualTo(3);
    }

    @Test void theHighlightAndThePlaceholderLandExactlyInTheirRectangles() {
        for (String key : KEYS) UIManager.put(key, null);
        UIManager.put("Component.accentColor", new Color(0x2f, 0x80, 0xed));
        var overlay = new DockDropOverlay();
        overlay.show(new Rectangle(50, 40, 200, 100), new Rectangle(10, 160, 22, 22), null, null);
        BufferedImage image = paint(overlay);
        Color fill = new Color(0x2f, 0x80, 0xed, 64), border = new Color(0x2f, 0x80, 0xed, 153);
        assertColour(image, 150, 90, fill);
        assertColour(image, 50, 40, border);
        assertColour(image, 249, 139, border);
        assertThat(new Color(image.getRGB(49, 90), true).getAlpha()).as("left of the highlight").isZero();
        assertThat(new Color(image.getRGB(250, 90), true).getAlpha()).as("right of the highlight").isZero();
        assertThat(new Color(image.getRGB(150, 141), true).getAlpha()).as("under the highlight").isZero();
        assertColour(image, 21, 171, fill);
        assertThat(new Color(image.getRGB(10, 160), true).getAlpha()).as("the placeholder's corner is rounded off").isLessThan(64);
        assertThat(overlay.contains(150, 90)).as("mouse events pass through").isFalse();
    }

    @Test void theThemesOwnDragColoursWin() {
        UIManager.put("DragAndDrop.areaBackground", new Color(10, 20, 30, 40));
        UIManager.put("DragAndDrop.borderColor", new Color(200, 100, 50, 255));
        assertThat(DockDropOverlay.fill()).isEqualTo(new Color(10, 20, 30, 40));
        assertThat(DockDropOverlay.border()).isEqualTo(new Color(200, 100, 50, 255));
        for (String key : KEYS) UIManager.put(key, null);
        assertThat(DockDropOverlay.fill()).as("no accent either: IntelliJ's blue").isEqualTo(new Color(0x35, 0x74, 0xf0, 64));
    }

    @Test void theGhostFollowsTheCursorAndClearRemovesEverything() {
        var icon = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        var g = icon.createGraphics(); g.setColor(Color.RED); g.fillRect(0, 0, 16, 16); g.dispose();
        var overlay = new DockDropOverlay();
        overlay.show(null, null, new ImageIcon(icon), new Point(200, 100));
        BufferedImage image = paint(overlay);
        assertThat(image.getRGB(192, 92)).isEqualTo(Color.RED.getRGB());
        assertThat(image.getRGB(207, 107)).isEqualTo(Color.RED.getRGB());
        assertThat(new Color(image.getRGB(208, 100), true).getAlpha()).isZero();
        overlay.clear();
        assertThat(overlay.highlight()).isNull();
        assertThat(new Color(paint(overlay).getRGB(200, 100), true).getAlpha()).isZero();
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DockDropOverlayTest'`
Expected: FAIL — `cannot find symbol: class DockDropOverlay`.

- [ ] **Step 3: Write the overlay**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/DockDropOverlay.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.UIManager;

/**
 * The drag guides, on the layered pane's DRAG_LAYER: a translucent highlight with a 1px border exactly where the
 * dragged tool window would land, a highlighted rounded square in the stripe's gap, and the window's icon at the
 * cursor. It takes no mouse input. Colours: {@code DragAndDrop.areaBackground} and {@code DragAndDrop.borderColor}
 * when the theme defines them, otherwise {@code Component.accentColor} at 25 % for the fill and 60 % for the border.
 * EDT only.
 */
final class DockDropOverlay extends JComponent {
    private static final int FILL_ALPHA = 64, BORDER_ALPHA = 153, ARC = 10;
    /** IntelliJ's accent, for a theme without an accent colour. */
    private static final Color FALLBACK_ACCENT = new Color(0x3574f0);

    private Rectangle highlight;
    private Rectangle placeholder;
    private Icon ghost;
    private Point cursor;

    DockDropOverlay() { setOpaque(false); }

    /**
     * Shows the guides; each may be null.
     *
     * @param highlight where the window would land
     * @param placeholder the stripe's gap
     * @param ghost the dragged window's icon
     * @param cursor where to draw the ghost, centred
     */
    void show(Rectangle highlight, Rectangle placeholder, Icon ghost, Point cursor) {
        this.highlight = highlight == null ? null : new Rectangle(highlight);
        this.placeholder = placeholder == null ? null : new Rectangle(placeholder);
        this.ghost = ghost;
        this.cursor = cursor == null ? null : new Point(cursor);
        repaint();
    }

    void clear() { show(null, null, null, null); }

    Rectangle highlight() { return highlight == null ? null : new Rectangle(highlight); }

    Rectangle placeholder() { return placeholder == null ? null : new Rectangle(placeholder); }

    /** Events pass through to whatever is underneath. */
    @Override public boolean contains(int x, int y) { return false; }

    static Color fill() {
        Color theme = UIManager.getColor("DragAndDrop.areaBackground");
        return theme != null ? theme : withAlpha(accent(), FILL_ALPHA);
    }

    static Color border() {
        Color theme = UIManager.getColor("DragAndDrop.borderColor");
        return theme != null ? theme : withAlpha(accent(), BORDER_ALPHA);
    }

    private static Color accent() {
        Color accent = UIManager.getColor("Component.accentColor");
        return accent != null ? accent : FALLBACK_ACCENT;
    }

    private static Color withAlpha(Color color, int alpha) { return new Color(color.getRed(), color.getGreen(), color.getBlue(), alpha); }

    @Override protected void paintComponent(Graphics graphics) {
        var g = (Graphics2D) graphics.create();
        try {
            if (highlight != null) {
                // The fill stays inside the border, so the two translucent colours never stack.
                g.setColor(fill());
                g.fillRect(highlight.x + 1, highlight.y + 1, highlight.width - 2, highlight.height - 2);
                g.setColor(border());
                g.drawRect(highlight.x, highlight.y, highlight.width - 1, highlight.height - 1);
            }
            if (placeholder != null) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                double arc = UIScale.scale(ARC);
                g.setColor(fill());
                g.fill(new RoundRectangle2D.Double(placeholder.x, placeholder.y, placeholder.width, placeholder.height, arc, arc));
                g.setColor(border());
                g.draw(new RoundRectangle2D.Double(placeholder.x + 0.5, placeholder.y + 0.5, placeholder.width - 1, placeholder.height - 1, arc, arc));
            }
            if (ghost != null && cursor != null)
                ghost.paintIcon(this, g, cursor.x - ghost.getIconWidth() / 2, cursor.y - ghost.getIconHeight() / 2);
        } finally { g.dispose(); }
    }
}
```

- [ ] **Step 4: Run the test to make sure it passes**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DockDropOverlayTest'`
Expected: PASS, 3 tests.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/DockDropOverlay.java jasper-app/src/test/java/dev/jasper/app/workspace/DockDropOverlayTest.java
git commit -F - <<'EOF'
feat(workspace): paint the dock drag guides

DockDropOverlay draws a translucent highlight with a 1px border where the
window would land, a rounded placeholder in the stripe's gap and the
window's icon at the cursor, in the theme's drag colours or its accent at
25 and 60 percent. It lets mouse events through.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 5: `DockDrag`, the gesture

**Files:**
- Create: `jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java`
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragTest.java`

**Interfaces:**
- Consumes: Task 1's `DockGeometry.landing` and `DockState.moveAndShow` (in the test's drop); Task 2's resolver;
  Task 3's stripe drag view, `dragSource` hooks and `ToolWindowArea.metrics()`; Task 4's overlay.
- Produces (package-private, EDT only): `final class DockDrag` with `static final int THRESHOLD = 5`, constructor
  `DockDrag(JComponent root, DockState state, ToolWindowArea area, ToolStripe left, ToolStripe right)`, fields
  `BiConsumer<String, DockDropResolver.Target> onDrop` and `Function<String, Icon> icons`, and
  `void install(JComponent component, String id)`, `boolean active()`, `String dragged()`,
  `Optional<DockDropResolver.Target> target()`, `DockDropOverlay overlay()`, `void cancel()`,
  `boolean dispatchKeyEvent(KeyEvent)`. The test builds a window like `WindowContent`'s (header, stripes beside the
  area, status bar) without `WindowContent`; Task 6 wires the real one.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/DockDragTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import dev.jasper.app.dock.Side;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.testsupport.EdtTestExtension;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JLayeredPane;
import javax.swing.JPanel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.assertj.core.api.Assertions.assertThat;

/** The dock drag gesture against a window built like WindowContent's: header, stripes beside the area, status bar. */
@ExtendWith(EdtTestExtension.class)
class DockDragTest {
    /** A window with a, b, c in the left top group and d in the right top group. Built on the EDT, in each test. */
    private static final class Window {
        final DockState state = new DockState();
        final ToolStripe left = new ToolStripe(Side.LEFT), right = new ToolStripe(Side.RIGHT);
        final ToolWindowArea area = new ToolWindowArea(new JPanel(), state);
        final JPanel content = new JPanel(new BorderLayout());
        final JRootPane rootPane = new JRootPane();
        final DockDrag drag = new DockDrag(content, state, area, left, right);
        final List<String> shown = new ArrayList<>();

        Window() {
            var header = new JPanel(); header.setPreferredSize(new Dimension(10, 30));
            var status = new JPanel(); status.setPreferredSize(new Dimension(10, 24));
            var body = new JPanel(new BorderLayout());
            body.add(left, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(right, BorderLayout.EAST);
            content.add(header, BorderLayout.NORTH); content.add(body, BorderLayout.CENTER); content.add(status, BorderLayout.SOUTH);
            rootPane.setContentPane(content);
            left.dragSource = drag::install;
            right.dragSource = drag::install;
            area.dragSource = drag::install;
            drag.icons = id -> new ImageIcon(new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB));
            // The drop, as WindowContributions applies it.
            drag.onDrop = (id, target) -> {
                if (target.onStripe()) state.move(id, target.slot(), target.index().getAsInt());
                else {
                    List<String> ids = state.ids(target.slot());
                    state.moveAndShow(id, target.slot(), ids.contains(id) ? ids.indexOf(id) : ids.size());
                }
            };
            for (String id : List.of("a", "b", "c")) { state.add(id, Slot.LEFT_TOP); area.setContent(id, id.toUpperCase(), new JLabel(id)); }
            state.add("d", Slot.RIGHT_TOP); area.setContent("d", "D", new JLabel("d"));
            // The window's one dock listener, cut down: lay out, render the stripes.
            state.addListener(change -> { drag.cancel(); shown.addAll(change.shown()); area.sync(); render(); layout(); });
            render();
            rootPane.setSize(1200, 800);
            layout();
        }

        void render() {
            List<ToolStripe.Button> buttons = new ArrayList<>();
            for (Slot slot : Slot.values())
                for (String id : state.ids(slot)) buttons.add(new ToolStripe.Button(id, id.toUpperCase(), null, slot, state.visible(id)));
            left.render(buttons, List.of());
            right.render(buttons, List.of());
        }

        void layout() { LayoutTestSupport.layoutTree(rootPane); }

        AbstractButton button(ToolStripe stripe, String id) {
            return stripe.buttons().stream().filter(button -> id.toUpperCase().equals(button.getToolTipText())).findFirst().orElseThrow();
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), content);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        Rectangle workspace() { return SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), content); }

        /** The middle of one cell of the workspace's 3 x 2 grid. */
        Point cell(int row, int column) {
            Rectangle w = workspace();
            return new Point(w.x + w.width * (2 * column + 1) / 4, w.y + w.height * (2 * row + 1) / 6);
        }

        void mouse(JComponent source, int id, Point inContent) {
            Point at = SwingUtilities.convertPoint(content, inContent, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** Press on the source, move past the threshold, then to each point; returns the source for the release. */
        JComponent dragTo(JComponent source, Point... points) {
            Point start = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, start);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 10, start.y));
            for (Point point : points) mouse(source, MouseEvent.MOUSE_DRAGGED, point);
            return source;
        }
    }

    @Test void aPressBecomesADragOnlyPastFivePixelsAndTheOverlayComesAndGoes() {
        var w = new Window();
        var source = w.button(w.left, "b");
        Point start = w.center(source);
        w.mouse(source, MouseEvent.MOUSE_PRESSED, start);
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 3, start.y + 2));
        assertThat(w.drag.active()).as("3px is still a click").isFalse();
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 6, start.y));
        assertThat(w.drag.active()).isTrue();
        assertThat(w.drag.dragged()).isEqualTo("b");
        assertThat(w.drag.overlay().getParent()).isSameAs(w.rootPane.getLayeredPane());
        assertThat(w.rootPane.getLayeredPane().getLayer(w.drag.overlay())).isEqualTo(JLayeredPane.DRAG_LAYER);
        assertThat(w.left.dragging()).isTrue();
        assertThat(w.right.dragging()).as("both stripes show all their groups").isTrue();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, new Point(start.x + 6, start.y));
        assertThat(w.drag.active()).isFalse();
        assertThat(w.drag.overlay().getParent()).isNull();
        assertThat(w.left.dragging()).isFalse();
        assertThat(w.state.ids(Slot.LEFT_TOP)).as("dropping where it was changes nothing").containsExactly("a", "b", "c");
    }

    @Test void aStripeDropMovesTheWindowAndKeepsItsVisibility() {
        var w = new Window();
        w.state.show("a");
        w.shown.clear();
        Point belowD = w.center(w.button(w.right, "d"));
        belowD.translate(0, 12);
        var source = w.dragTo(w.button(w.left, "a"), belowD);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 1));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, belowD);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("d", "a");
        assertThat(w.state.visible("a")).as("a stripe drop keeps visibility").isTrue();
        assertThat(w.shown).as("a move tells no plugin it was shown").isEmpty();
        assertThat(w.area.content(Slot.RIGHT_TOP)).isNotNull();
    }

    @Test void aWorkspaceDropMovesTheWindowAndShowsIt() {
        var w = new Window();
        Point bottomRight = w.cell(2, 1);
        var source = w.dragTo(w.button(w.left, "b"), bottomRight);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.BOTTOM_RIGHT));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, bottomRight);
        assertThat(w.state.slot("b")).hasValue(Slot.BOTTOM_RIGHT);
        assertThat(w.state.visible("b")).isTrue();
        assertThat(w.shown).as("showing a hidden window tells its plugin").containsExactly("b");
        assertThat(w.area.content(Slot.BOTTOM_RIGHT)).isNotNull();
    }

    @Test void aWindowReordersWithinItsStripe() {
        var w = new Window();
        Point aboveA = w.center(w.button(w.left, "a"));
        aboveA.translate(0, -8);
        var source = w.dragTo(w.button(w.left, "c"), aboveA);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.stripe(Slot.LEFT_TOP, 0));
        w.mouse(source, MouseEvent.MOUSE_RELEASED, aboveA);
        assertThat(w.state.ids(Slot.LEFT_TOP)).containsExactly("c", "a", "b");
    }

    @Test void theHeaderStartsTheSameDrag() {
        var w = new Window();
        w.state.show("a");
        w.shown.clear();
        Point rightTop = w.cell(0, 1);
        var source = w.dragTo(w.area.headerOf(Slot.LEFT_TOP).titleLabel(), rightTop);
        assertThat(w.drag.dragged()).isEqualTo("a");
        w.mouse(source, MouseEvent.MOUSE_RELEASED, rightTop);
        assertThat(w.state.ids(Slot.RIGHT_TOP)).containsExactly("d", "a");
        assertThat(w.state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(w.shown).as("it was showing already").isEmpty();
    }

    @Test void theGuidesShowWhereTheWindowWouldLandWithoutMovingTheRealLayout() {
        var w = new Window();
        w.dragTo(w.button(w.left, "b"), w.cell(2, 0));
        Rectangle area = new Rectangle(0, 0, w.area.getWidth(), w.area.getHeight());
        Rectangle expected = SwingUtilities.convertRectangle(w.area,
            DockGeometry.landing(area, w.state, "b", Slot.BOTTOM_LEFT, ToolWindowArea.metrics()).orElseThrow(), w.rootPane.getLayeredPane());
        assertThat(w.drag.overlay().highlight()).isEqualTo(expected);
        assertThat(expected.width).as("the bottom area spans stripe to stripe").isEqualTo(w.area.getWidth());
        assertThat(w.area.content(Slot.BOTTOM_LEFT)).as("the real layout does not reflow").isNull();
        assertThat(w.drag.overlay().placeholder()).as("no stripe gap over the workspace").isNull();

        Point onStripe = w.center(w.button(w.right, "d"));
        onStripe.translate(0, -8);
        w.mouse(w.button(w.left, "b"), MouseEvent.MOUSE_DRAGGED, onStripe);
        Rectangle gap = w.right.gapBounds().orElseThrow();
        assertThat(w.drag.overlay().placeholder()).isEqualTo(SwingUtilities.convertRectangle(w.right, gap, w.rootPane.getLayeredPane()));
        assertThat(w.drag.overlay().highlight()).as("where it lands if shown there").isNotNull();
    }

    @Test void escapeOrAReleaseWithoutATargetCancels() {
        var w = new Window();
        var source = w.dragTo(w.button(w.left, "b"), w.cell(1, 1));
        assertThat(w.drag.dispatchKeyEvent(new KeyEvent(w.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("the drag owns Esc").isTrue();
        assertThat(w.drag.active()).isFalse();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, w.cell(1, 1));
        assertThat(w.state.slot("b")).as("cancelled: nothing moves").hasValue(Slot.LEFT_TOP);
        assertThat(w.drag.dispatchKeyEvent(new KeyEvent(w.content, KeyEvent.KEY_PRESSED, System.currentTimeMillis(), 0,
            KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED))).as("no drag, no claim on Esc").isFalse();

        Point titleBar = new Point(600, 10);
        source = w.dragTo(w.button(w.left, "b"), titleBar);
        assertThat(w.drag.target()).isEmpty();
        w.mouse(source, MouseEvent.MOUSE_RELEASED, titleBar);
        assertThat(w.state.slot("b")).hasValue(Slot.LEFT_TOP);
        assertThat(w.drag.overlay().getParent()).isNull();
    }

    @Test void resizingTheWindowMidDragResolvesTheTargetAgain() {
        var w = new Window();
        Point point = new Point(1100, 50);
        w.dragTo(w.button(w.left, "b"), point);
        assertThat(w.drag.target()).hasValue(DockDropResolver.Target.workspace(Slot.RIGHT_TOP));
        w.rootPane.setSize(1120, 800);
        w.layout();
        w.content.dispatchEvent(new ComponentEvent(w.content, ComponentEvent.COMPONENT_RESIZED));
        assertThat(w.drag.target()).as("the right stripe moved under the cursor, below d").hasValue(DockDropResolver.Target.stripe(Slot.RIGHT_TOP, 1));
    }

    @Test void aDockChangeOrCancelEndsTheDragAndAPendingPress() {
        var w = new Window();
        w.dragTo(w.button(w.left, "b"), w.cell(0, 1));
        w.state.show("c");
        assertThat(w.drag.active()).as("any dock change ends the drag").isFalse();
        var source = w.button(w.left, "a");
        Point start = w.center(source);
        w.mouse(source, MouseEvent.MOUSE_PRESSED, start);
        w.drag.cancel();
        w.mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 40, start.y));
        assertThat(w.drag.active()).as("a cancelled press never becomes a drag").isFalse();
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.DockDragTest'`
Expected: FAIL — `cannot find symbol: class DockDrag`.

- [ ] **Step 3: Write the gesture**

Create `jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java`:

```java
package dev.jasper.app.workspace;

import com.formdev.flatlaf.util.UIScale;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockGeometry;
import dev.jasper.app.dock.DockState;
import java.awt.Component;
import java.awt.Container;
import java.awt.KeyEventDispatcher;
import java.awt.KeyboardFocusManager;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;
import java.util.function.Function;
import javax.swing.AbstractButton;
import javax.swing.Icon;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;

/**
 * One window's dock drag: press on a stripe button or a tool window header and move more than 5px. It uses plain
 * mouse events, not AWT drag and drop, so it stays inside the window, needs no OS drag image and can be driven with
 * synthetic events. While it runs, both stripes show all three groups with the dragged button lifted out, the target
 * group opens a gap, and the overlay shows where the window would land, from {@link DockGeometry#landing}; the real
 * layout does not change. Releasing over a target hands the drop to {@link #onDrop}; releasing anywhere else, Esc or
 * {@link #cancel} ends the drag without one. A window resize while dragging resolves the target again. EDT only.
 */
final class DockDrag {
    /** How far, in logical pixels, a press must move before it becomes a drag. */
    static final int THRESHOLD = 5;

    private final JComponent root;
    private final DockState state;
    private final ToolWindowArea area;
    private final List<ToolStripe> stripes;
    private final DockDropOverlay overlay = new DockDropOverlay();
    private final KeyEventDispatcher escape = this::dispatchKeyEvent;

    /** Applies a drop: the dragged window and where it goes. */
    BiConsumer<String, DockDropResolver.Target> onDrop = (id, target) -> { };
    /** The dragged window's icon, drawn at the cursor. */
    Function<String, Icon> icons = id -> null;

    private String pressedId;
    private JComponent source;
    private Point pressedAt;
    private String dragging;
    private Point last;
    private Optional<DockDropResolver.Target> target = Optional.empty();

    /**
     * @param root the window content that holds the stripes and the area; drag coordinates are in its space
     */
    DockDrag(JComponent root, DockState state, ToolWindowArea area, ToolStripe left, ToolStripe right) {
        this.root = root;
        this.state = state;
        this.area = area;
        this.stripes = List.of(left, right);
        root.addComponentListener(new ComponentAdapter() {
            @Override public void componentResized(ComponentEvent event) {
                if (active()) update(last);
            }
        });
    }

    /** Lets a drag start from the component for the window with that id: a stripe button, a header or its title. */
    void install(JComponent component, String id) {
        var mouse = new MouseAdapter() {
            @Override public void mousePressed(MouseEvent event) { pressed(component, id, event); }
            @Override public void mouseDragged(MouseEvent event) { dragged(component, event); }
            @Override public void mouseReleased(MouseEvent event) { released(component, event); }
        };
        component.addMouseListener(mouse);
        component.addMouseMotionListener(mouse);
    }

    boolean active() { return dragging != null; }

    /** The dragged window, or null. */
    String dragged() { return dragging; }

    /** Where a release would drop now. */
    Optional<DockDropResolver.Target> target() { return target; }

    DockDropOverlay overlay() { return overlay; }

    /** Ends a drag or a pending press without dropping: Esc, the window closing, a plugin unloading, a dock change. */
    void cancel() { finish(); }

    /** While a drag runs it owns Esc, which cancels it. */
    boolean dispatchKeyEvent(KeyEvent event) {
        if (!active() || event.getKeyCode() != KeyEvent.VK_ESCAPE) return false;
        if (event.getID() == KeyEvent.KEY_PRESSED) cancel();
        return true;
    }

    private void pressed(JComponent component, String id, MouseEvent event) {
        if (!SwingUtilities.isLeftMouseButton(event) || active()) return;
        pressedId = id;
        source = component;
        pressedAt = toRoot(component, event.getPoint());
    }

    private void dragged(JComponent component, MouseEvent event) {
        if (pressedId == null || component != source) return;
        Point at = toRoot(component, event.getPoint());
        if (!active()) {
            if (at.distance(pressedAt) <= UIScale.scale(THRESHOLD)) return;
            start();
        }
        update(at);
    }

    private void released(JComponent component, MouseEvent event) {
        if (component != source) return;
        if (!active()) { finish(); return; }
        update(toRoot(component, event.getPoint()));
        String id = dragging;
        Optional<DockDropResolver.Target> drop = target;
        finish();
        drop.ifPresent(where -> onDrop.accept(id, where));
    }

    private void start() {
        dragging = pressedId;
        // The button saw a press; releasing it after a drag must not also toggle the window.
        if (source instanceof AbstractButton button) {
            button.getModel().setArmed(false);
            button.getModel().setPressed(false);
        }
        for (ToolStripe stripe : stripes) if (stripe.isVisible()) stripe.beginDrag(dragging);
        JLayeredPane layers = layers();
        if (layers != null) layers.add(overlay, JLayeredPane.DRAG_LAYER);
        KeyboardFocusManager.getCurrentKeyboardFocusManager().addKeyEventDispatcher(escape);
    }

    /** Resolves the target under the cursor, opens the stripe gap and moves the guides. */
    private void update(Point at) {
        last = at;
        List<DockDropResolver.Group> groups = new ArrayList<>();
        for (ToolStripe stripe : stripes)
            if (stripe.isVisible()) for (DockDropResolver.Group group : stripe.dropGroups()) groups.add(inRoot(stripe, group));
        Rectangle workspace = SwingUtilities.convertRectangle(area.getParent(), area.getBounds(), root);
        target = DockDropResolver.resolve(at, groups, workspace);
        for (ToolStripe stripe : stripes)
            stripe.showGap(target.filter(DockDropResolver.Target::onStripe).map(DockDropResolver.Target::slot).orElse(null),
                target.map(where -> where.index().orElse(0)).orElse(0));
        JLayeredPane layers = layers();
        if (layers == null) return;
        Rectangle highlight = target.flatMap(where -> DockGeometry.landing(new Rectangle(0, 0, area.getWidth(), area.getHeight()),
                state, dragging, where.slot(), ToolWindowArea.metrics()))
            .map(landing -> SwingUtilities.convertRectangle(area, landing, layers)).orElse(null);
        Rectangle placeholder = null;
        for (ToolStripe stripe : stripes) {
            Optional<Rectangle> gap = stripe.gapBounds();
            if (gap.isPresent()) placeholder = SwingUtilities.convertRectangle(stripe, gap.get(), layers);
        }
        overlay.setBounds(0, 0, layers.getWidth(), layers.getHeight());
        overlay.show(highlight, placeholder, icons.apply(dragging), SwingUtilities.convertPoint(root, at, layers));
    }

    private void finish() {
        boolean wasActive = active();
        dragging = null;
        pressedId = null;
        source = null;
        target = Optional.empty();
        if (!wasActive) return;
        KeyboardFocusManager.getCurrentKeyboardFocusManager().removeKeyEventDispatcher(escape);
        for (ToolStripe stripe : stripes) stripe.endDrag();
        overlay.clear();
        Container parent = overlay.getParent();
        if (parent != null) {
            parent.remove(overlay);
            parent.repaint();
        }
    }

    private JLayeredPane layers() {
        JRootPane rootPane = SwingUtilities.getRootPane(root);
        return rootPane == null ? null : rootPane.getLayeredPane();
    }

    private Point toRoot(Component component, Point point) { return SwingUtilities.convertPoint(component, point, root); }

    private DockDropResolver.Group inRoot(ToolStripe stripe, DockDropResolver.Group group) {
        List<Rectangle> buttons = group.buttons().stream().map(button -> SwingUtilities.convertRectangle(stripe, button, root)).toList();
        return new DockDropResolver.Group(group.slot(), SwingUtilities.convertRectangle(stripe, group.bounds(), root), buttons);
    }
}
```

- [ ] **Step 4: Run the tests to make sure they pass**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*' --tests 'dev.jasper.app.dock.*'`
Expected: PASS; `DockDragTest` runs 9 cases.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/DockDrag.java jasper-app/src/test/java/dev/jasper/app/workspace/DockDragTest.java
git commit -F - <<'EOF'
feat(workspace): drag tool windows with plain mouse events

DockDrag starts after 5px from a stripe button or a header, shows the
stripes' drag view and the guides, resolves the target on every move and on
a window resize, and hands the drop over on release. Esc, a release with no
target or cancel() ends it without one.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 6: Wire the drag into the window

**Files:**
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java` (four hunks, by script)
- Modify: `jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java` (six hunks, by script)
- Create: `jasper-app/src/test/java/dev/jasper/app/workspace/WindowDragTest.java`

**Interfaces:**
- Consumes: Tasks 1–5; plan 1's `WindowContent` and `WindowContributions`.
- Produces: `WindowContent.dockDrag()`; the window installs the drag on both stripes and the area; drops reach
  `WindowContributions.dropped`, which calls `dock.move` (stripe) or `dock.moveAndShow` (workspace). A dock change,
  a stripe render or the window closing cancels a running drag.

- [ ] **Step 1: Write the failing test**

Create `jasper-app/src/test/java/dev/jasper/app/workspace/WindowDragTest.java`:

```java
package dev.jasper.app.workspace;

import dev.jasper.app.contributions.Contributions;
import dev.jasper.app.contributions.PanelEntry;
import dev.jasper.app.dock.Slot;
import dev.jasper.app.persistence.UiState;
import dev.jasper.app.testsupport.LayoutTestSupport;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.InputEvent;
import java.awt.event.MouseEvent;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import javax.swing.AbstractButton;
import javax.swing.ImageIcon;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JRootPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.workspace.DesktopTestSupport.edt;
import static org.assertj.core.api.Assertions.assertThat;

/** Dock drags in a real window: what a drop does to the panels, the saved layout and the plugins. */
class WindowDragTest {
    @AfterEach void closeWindows() throws Exception { DesktopTestSupport.closeOwners(); }

    /** A connected window in a root pane, with Sessions and Files on the left; events record what the plugins hear. */
    private static final class Fixture {
        final Contributions model = new Contributions();
        final UiState state = UiState.inMemory();
        final WindowContent owner = DesktopTestSupport.content(DesktopTestSupport.launcher(new ArrayDeque<>()));
        final JRootPane root = new JRootPane();
        final List<String> events = new ArrayList<>();
        final PanelEntry sessions, files;

        Fixture() {
            owner.connectContributions(model, state);
            sessions = model.addPanel("dev.x.sessions", "Sessions", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onVisibility(visible -> events.add("sessions:" + visible));
                return new JLabel("sessions");
            });
            files = model.addPanel("dev.x.files", "Files", new ImageIcon(), Slot.LEFT_TOP, site -> {
                site.onVisibility(visible -> events.add("files:" + visible));
                return new JLabel("files");
            });
            root.setContentPane(owner);
            root.setSize(1200, 800);
            layout();
        }

        void layout() { LayoutTestSupport.layoutTree(root); LayoutTestSupport.layoutTree(root); }

        AbstractButton button(String title) {
            for (ToolStripe stripe : List.of(owner.leftStripe(), owner.rightStripe()))
                for (AbstractButton button : stripe.buttons()) if (title.equals(button.getToolTipText())) return button;
            throw new AssertionError("No stripe button " + title);
        }

        Point center(Component component) {
            Rectangle bounds = SwingUtilities.convertRectangle(component.getParent(), component.getBounds(), owner);
            return new Point((int) bounds.getCenterX(), (int) bounds.getCenterY());
        }

        Point cell(int row, int column) {
            Rectangle w = SwingUtilities.convertRectangle(owner.area().getParent(), owner.area().getBounds(), owner);
            return new Point(w.x + w.width * (2 * column + 1) / 4, w.y + w.height * (2 * row + 1) / 6);
        }

        void mouse(JComponent source, int id, Point inOwner) {
            Point at = SwingUtilities.convertPoint(owner, inOwner, source);
            int modifiers = id == MouseEvent.MOUSE_RELEASED ? 0 : InputEvent.BUTTON1_DOWN_MASK;
            int button = id == MouseEvent.MOUSE_DRAGGED ? MouseEvent.NOBUTTON : MouseEvent.BUTTON1;
            source.dispatchEvent(new MouseEvent(source, id, System.currentTimeMillis(), modifiers, at.x, at.y, 1, false, button));
        }

        /** Press, pass the threshold, move to the point; the caller releases or not. */
        JComponent drag(JComponent source, Point to) {
            Point start = center(source);
            mouse(source, MouseEvent.MOUSE_PRESSED, start);
            mouse(source, MouseEvent.MOUSE_DRAGGED, new Point(start.x + 10, start.y));
            mouse(source, MouseEvent.MOUSE_DRAGGED, to);
            return source;
        }

        void drop(JComponent source, Point to) {
            drag(source, to);
            mouse(source, MouseEvent.MOUSE_RELEASED, to);
            layout();
        }
    }

    @Test void aWorkspaceDropShowsAHiddenPanelTellsItsPluginAndIsSaved() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drop(f.button("Files"), f.cell(2, 1));
            assertThat(f.owner.dock().slot("dev.x.files")).hasValue(Slot.BOTTOM_RIGHT);
            assertThat(f.owner.area().content(Slot.BOTTOM_RIGHT)).isNotNull();
            assertThat(f.events).containsExactly("files:true");
            assertThat(f.state.panel("dev.x.files")).hasValue(new UiState.Panel(Slot.BOTTOM_RIGHT, 0, true));
            assertThat(f.owner.rightStripe().buttons()).extracting(AbstractButton::getToolTipText).containsExactly("Files");
        });
    }

    @Test void aClickStillTogglesAndADragDoesNotAlsoToggle() throws Exception {
        edt(() -> {
            var f = new Fixture();
            var files = f.button("Files");
            Point at = f.center(files);
            f.mouse(files, MouseEvent.MOUSE_PRESSED, at);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, at);
            assertThat(f.owner.dock().visible("dev.x.files")).as("a click toggles").isTrue();
            f.layout();
            files = f.button("Files");
            Point home = f.center(files);
            f.drag(files, new Point(home.x + 300, home.y + 300));
            f.mouse(files, MouseEvent.MOUSE_DRAGGED, home);
            f.mouse(files, MouseEvent.MOUSE_RELEASED, home);
            assertThat(f.owner.dockDrag().active()).isFalse();
            assertThat(f.owner.dock().visible("dev.x.files")).as("releasing a drag where the button was does not toggle").isTrue();
            assertThat(f.owner.dock().ids(Slot.LEFT_TOP)).containsExactly("dev.x.sessions", "dev.x.files");
            assertThat(f.events).containsExactly("files:true");
        });
    }

    @Test void aStripeDropMovesAShowingPanelWithoutTellingItsPlugin() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.layout();
            Rectangle stripe = SwingUtilities.convertRectangle(f.owner.rightStripe().getParent(), f.owner.rightStripe().getBounds(), f.owner);
            Point emptyRightTop = new Point(stripe.x + stripe.width / 2, stripe.y + 10);
            f.drop(f.button("Sessions"), emptyRightTop);
            assertThat(f.owner.dock().slot("dev.x.sessions")).as("into the empty right stripe's drop space").hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.dock().visible("dev.x.sessions")).isTrue();
            assertThat(f.events).as("a move is not a change of visibility").containsExactly("sessions:true");
        });
    }

    @Test void theHeaderDragsEvenWithTheStripesHidden() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.requestPanel(new Contributions.PanelRequest(f.owner.id(), "dev.x.sessions", Contributions.PanelRequest.Op.SHOW));
            f.owner.setRailVisible(false);
            f.layout();
            f.drop(f.owner.area().headerOf(Slot.LEFT_TOP).titleLabel(), f.cell(0, 1));
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.RIGHT_TOP);
            assertThat(f.owner.area().headerOf(Slot.RIGHT_TOP).titleLabel().getText()).isEqualTo("Sessions");
        });
    }

    @Test void aPluginUnloadingOrTheWindowClosingMidDragCancelsIt() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.drag(f.button("Sessions"), f.cell(1, 1));
            assertThat(f.owner.dockDrag().active()).isTrue();
            f.files.close();
            assertThat(f.owner.dockDrag().active()).as("a plugin unloading").isFalse();
            assertThat(f.owner.dockDrag().overlay().getParent()).isNull();
            f.layout();
            f.drag(f.button("Sessions"), f.cell(1, 1));
            assertThat(f.owner.dockDrag().active()).isTrue();
            f.owner.close();
            assertThat(f.owner.dockDrag().active()).as("the window closing").isFalse();
            assertThat(f.owner.dock().slot("dev.x.sessions")).hasValue(Slot.LEFT_TOP);
        });
    }

    @Test void aRailActionDoesNotDrag() throws Exception {
        edt(() -> {
            var f = new Fixture();
            f.model.addAction("dev.x.open", "Open Manager", new ImageIcon(), List.of(), java.util.Optional.empty(), invocation -> { });
            f.model.addRailAction("dev.x.open");
            f.layout();
            f.drag(f.button("Open Manager"), f.cell(0, 1));
            assertThat(f.owner.dockDrag().active()).isFalse();
        });
    }
}
```

- [ ] **Step 2: Run it to make sure it fails**

Run: `./gradlew :jasper-app:compileTestJava`
Expected: FAIL — `cannot find symbol: method dockDrag()`.

- [ ] **Step 3: Wire it**

Save this script as `/tmp/dock-wiring.py` and run it from the worktree root with `python3 /tmp/dock-wiring.py`;
every hunk must match exactly once:

```python
import pathlib

# Run from the worktree root after plan 1. Every replacement must match exactly once, or the script stops before writing.
base = pathlib.Path("jasper-app/src/main/java/dev/jasper/app/workspace")
edits = {
    "WindowContent.java": [
        ("""    private final ToolStripe leftStripe = new ToolStripe(Side.LEFT), rightStripe = new ToolStripe(Side.RIGHT);
""", """    private final ToolStripe leftStripe = new ToolStripe(Side.LEFT), rightStripe = new ToolStripe(Side.RIGHT);
    /** Drags a tool window from its stripe button or its header to another slot. */
    private final DockDrag dockDrag;
"""),
        ("""        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
""", """        body.add(leftStripe, BorderLayout.WEST); body.add(area, BorderLayout.CENTER); body.add(rightStripe, BorderLayout.EAST);
        dockDrag = new DockDrag(this, dock, area, leftStripe, rightStripe);
        leftStripe.dragSource = dockDrag::install;
        rightStripe.dragSource = dockDrag::install;
        area.dragSource = dockDrag::install;
"""),
        ("""    ToolStripe rightStripe() { return rightStripe; }
""", """    ToolStripe rightStripe() { return rightStripe; }
    DockDrag dockDrag() { return dockDrag; }
"""),
        ("""    @Override public void close() {
        if (closed) return;
        paletteKeys.close();
""", """    @Override public void close() {
        if (closed) return;
        dockDrag.cancel();
        paletteKeys.close();
"""),
    ],
    "WindowContributions.java": [
        ("""import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockState;
""", """import dev.jasper.app.contributions.PanelSite;
import dev.jasper.app.dock.DockDropResolver;
import dev.jasper.app.dock.DockState;
"""),
        ("""        owner.area().onMove = this::movePanel;
        owner.area().onHide = this::hidePanel;
        dock.addListener(docked);
""", """        owner.area().onMove = this::movePanel;
        owner.area().onHide = this::hidePanel;
        owner.dockDrag().onDrop = this::dropped;
        owner.dockDrag().icons = id -> panels.containsKey(id) ? panels.get(id).entry.icon() : null;
        dock.addListener(docked);
"""),
        ("""    /** From a Move to menu: the end of the target slot's group, keeping visibility. */""",
         """    /**
     * A drop from the dock drag. Over a stripe the window moves and keeps its visibility; over the workspace it moves
     * and shows, and the slot's previously showing window hides but stays on its stripe. A workspace drop on the
     * window's own slot keeps its place in the group.
     */
    private void dropped(String id, DockDropResolver.Target target) {
        if (!panels.containsKey(id)) return;
        Slot slot = target.slot();
        if (target.onStripe()) {
            dock.move(id, slot, target.index().getAsInt());
            return;
        }
        List<String> ids = dock.ids(slot);
        dock.moveAndShow(id, slot, ids.contains(id) ? ids.indexOf(id) : ids.size());
    }

    /** From a Move to menu: the end of the target slot's group, keeping visibility. */"""),
        ("""    private void docked(DockState.Change change) {
        for (String id : change.shown()) build(id);""", """    private void docked(DockState.Change change) {
        // A dock change mid-drag (a plugin unloading, a panel showing itself) ends the drag: what it drew renders again.
        owner.dockDrag().cancel();
        for (String id : change.shown()) build(id);"""),
        ("""    private void renderStripes() {
        List<ToolStripe.Button> left""", """    private void renderStripes() {
        owner.dockDrag().cancel();
        List<ToolStripe.Button> left"""),
        ("""        // Closing the window is not hiding its panels, and every change was saved as it happened: just let go of the dock.
        dock.removeListener(docked);""", """        // Closing the window is not hiding its panels, and every change was saved as it happened: just let go of the dock.
        owner.dockDrag().cancel();
        owner.dockDrag().onDrop = (id, target) -> { };
        owner.dockDrag().icons = id -> null;
        dock.removeListener(docked);"""),
    ],
}
changed = {}
for name, pairs in edits.items():
    path = base / name
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count != 1: raise SystemExit(f"{name}: expected one match, found {count}: {old.strip()[:70]}")
        text = text.replace(old, new)
    changed[path] = text
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path)
```

- [ ] **Step 4: Run the tests**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.workspace.*'`
Expected: PASS; `WindowDragTest` runs 6 cases, and plan 1's `WindowPanelsTest` still passes.

Then: `./gradlew check`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add jasper-app/src/main/java/dev/jasper/app/workspace/WindowContent.java jasper-app/src/main/java/dev/jasper/app/workspace/WindowContributions.java \
        jasper-app/src/test/java/dev/jasper/app/workspace/WindowDragTest.java
git commit -F - <<'EOF'
feat(workspace): drop dragged tool windows into the window's dock

Each window installs the drag on its stripe buttons and headers. A stripe
drop moves the panel and keeps its visibility; a workspace drop moves and
shows it, telling its plugin only then. Any dock change or the window
closing ends a drag in progress.

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

---

## Task 7: Documentation, `STATUS.md` and the banners

**Files:**
- Modify: `docs/plugin-authoring.md`, `docs/sdk-architecture.md`, `docs/app-architecture.md`, `docs/app-maintenance.md`
  (script in Step 1)
- Modify: `docs/STATUS.md`; this plan's and plan 1's **Status** banners

**Interfaces:** none (documentation). `AppDocumentationTest` checks every link and anchor.

- [ ] **Step 1: Update the guides**

Save this script as `/tmp/dock-docs-2.py` and run it from the worktree root with `python3 /tmp/dock-docs-2.py`;
every replacement must match exactly once:

```python
import pathlib

# Run from the worktree root after plan 1. Every replacement must match exactly once, or the script stops before writing.
edits = {
    "docs/plugin-authoring.md": [
        ("""One panel shows per slot; the user
can move a panel to any of the six slots from its stripe icon's menu or its header's options menu,
and Jasper remembers its slot, its place on the stripe and the side's size.""",
         """One panel shows per slot; the user
can drag a panel by its stripe icon or its header to another slot or another place on a stripe, or
use the Move to menu on either, and Jasper remembers its slot, its place on the stripe and the side's
size. A drop on a stripe keeps the panel shown or hidden as it was; a drop on the workspace shows it,
and your `PanelHost.onVisibility` hears only that second kind."""),
    ],
    "docs/sdk-architecture.md": [
        ("""renders the two `ToolStripe`s, tells the panels and
saves.""", """renders the two `ToolStripe`s, tells the panels and
saves. `DockDrag` drags a window from its stripe button or its header with plain mouse events:
`DockDropResolver` finds the target under the cursor (a stripe group and a position, or a cell of
the workspace's 3 × 2 grid), the stripes open a gap, and `DockDropOverlay` on the layered pane's
`DRAG_LAYER` previews the landing with `DockGeometry.landing`, the same function as the layout. A
stripe drop moves the window and keeps its visibility; a workspace drop moves and shows it."""),
    ],
    "docs/app-architecture.md": [
        ("""| `workspace` | EDT windows, tabs, splits, panes, the tool window area, stripes and headers,""",
         """| `workspace` | EDT windows, tabs, splits, panes, the tool window area, stripes, headers and dock drag,"""),
    ],
    "docs/app-maintenance.md": [
        ("""Where frames go is `DockGeometry`'s job, never the area's: change the geometry and its
tests first, and keep the drop preview (plan 2) on the same function.""",
         """Where frames go is `DockGeometry`'s job, never the area's: change the geometry and its
tests first; the drop preview (`DockGeometry.landing`) uses the same function, so the two agree.
Dock drags use plain mouse events (`DockDrag`), never AWT drag and drop. Drag sources are the
components `DockDrag.install` was given: stripe window buttons (`ToolStripe.dragSource`) and
headers with their titles (`ToolWindowArea.dragSource`); rail actions are not sources. The dragged
button is lifted out by giving it empty bounds, not by hiding it, so the mouse events Swing sends
to the pressed component keep arriving. Any dock change or stripe render cancels a running drag."""),
    ],
}
changed = {}
for name, pairs in edits.items():
    path = pathlib.Path(name)
    text = path.read_text(encoding="utf-8")
    for old, new in pairs:
        count = text.count(old)
        if count != 1: raise SystemExit(f"{name}: expected one match, found {count}: {old.strip()[:70]}")
        text = text.replace(old, new)
    changed[path] = text
for path, text in changed.items():
    path.write_text(text, encoding="utf-8")
    print("updated", path)
```

Expected output: four `updated …` lines.

- [ ] **Step 2: Add the `STATUS.md` entry**

Count the tests with the script in plan 1's Task 10, Step 2 (after a fresh `./gradlew check`). Insert this entry as
the first bullet under `## Current state — …` in `docs/STATUS.md`, replacing the four `<…>` markers with the counted
numbers:

```markdown
- **Dock layout plan 2: drag and drop** (branch `claude/dock-layout`,
  [plan](superpowers/plans/2026-10-02-jasper-dock-layout-plan-2-drag-and-drop.md)): tool windows drag by their
  stripe buttons and headers. `DockDropResolver` (pure) resolves a stripe group and position or a cell of the
  workspace's 3 × 2 grid; `DockGeometry.landing` previews the landing on a copy of the state; during a drag both
  stripes show all three groups, lift the dragged button and open a gap; `DockDropOverlay` on `DRAG_LAYER` paints
  the highlight, the placeholder and the ghost; `DockDrag` owns the gesture (5px threshold, Esc cancels, a resize
  resolves again). A stripe drop moves and keeps visibility; a workspace drop moves and shows
  (`DockState.moveAndShow`). Any dock change or the window closing cancels a drag. Planning decisions are listed
  in the plan. `./gradlew check`: <tests> tests, <failures> failures, <errors> errors, <skipped> skipped. No GUI
  launched; GUI checks for the user are the plan's last section.
```

- [ ] **Step 3: Update the banners**

Replace this plan's `**Status:**` paragraph with one that says it was executed, on which commits, with which
deviations from the plan text (or "none"), and the test counts. In plan 1's banner add one sentence: "Plan 2
(drag and drop) executed on top of it; see its banner."

- [ ] **Step 4: Run the documentation test, the whole check and the hygiene scan**

Run: `./gradlew :jasper-app:test --tests 'dev.jasper.app.documentation.*'` then `./gradlew check`, then the source
hygiene scan and `git diff --check` from plan 1's Task 10, Step 4.
Expected: PASS and no output.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/dock-layout
git add docs
git commit -F - <<'EOF'
docs: describe dock drag and drop and record plan 2

🤖 Generated with Claude Code at The Home Depot

Co-Authored-By: Claude <noreply@anthropic.com>
EOF
```

Do not merge to `main` and do not push without the user. Hand the GUI checks below to the user.

---

## GUI checks for the user (agents do not launch the GUI)

Run `./gradlew :jasper-app:run` yourself.

1. **Click versus drag:** a click on a stripe icon still toggles its window; press and move a few pixels only, then
   release: still a click. Drag further and release over the same place: nothing moves and nothing toggles.
2. **Stripe guides:** drag a stripe icon over the other stripe: both stripes show their three groups and separators,
   the target group parts to open a gap with a highlighted rounded square, the dragged icon follows the cursor, and
   the area where the window would land is highlighted. The real layout does not move.
3. **Reorder:** drag an icon up and down its own stripe; release; the order sticks after a restart.
4. **Workspace grid:** drag over each sixth of the workspace (left and right, upper, middle and lower thirds): the
   highlight jumps to that slot's area, the bottom area spanning stripe to stripe. Release: the window moves there
   and shows; the slot's previous window hides but keeps its icon.
5. **Headers:** drag a header by its title to another slot. Its ⋮ and — buttons still click. With View › Rail off,
   headers still drag.
6. **Cancel:** Esc mid-drag; release over the title bar or the status bar; drag outside the window and release. All
   cancel with no trace.
7. **Mid-drag changes:** resize the window with the keyboard or another monitor arrangement while holding a drag;
   turn a plugin off from another window while dragging one of its panels.
8. **Look against IntelliJ:** the highlight's fill and border colours in Jasper Dark and Light and an IntelliJ theme
   that defines `DragAndDrop.areaBackground`; the ghost icon; drop space in empty groups.

---

## Plan self-review

**Spec coverage.**

| Spec §3 / §5 requirement | Task |
|---|---|
| Gesture: press on a stripe icon or header, more than 5px, plain mouse events, `DockDrag`, Esc and release without target cancel | 5, 6 |
| Targets: `(slot, index)` by the pure `DockDropResolver`; stripe groups and midpoints; 3 × 2 workspace grid; nothing elsewhere | 2, 5 |
| Both stripes show all three groups and separators; empty group drop space | 3 |
| Area highlight from `DockGeometry.compute` on a what-if copy; the real layout does not reflow | 1, 5 |
| Stripe placeholder: buttons part at the index, a highlighted rounded square; the source button lifted out | 3, 4, 5 |
| Ghost icon follows the cursor | 4, 5 |
| Colours: `DragAndDrop.*` else accent at 25 % / 60 % | 4 |
| Stripe drop keeps visibility; workspace drop moves and shows, previous active hides but stays; same place changes nothing; `onVisibility` only when shown | 1, 5, 6 |
| Reorder within a stripe | 5 |
| Rail actions are not draggable; with stripes hidden, headers only | 3, 6 |
| Failures: plugin unloading or window closing cancels; a resize recomputes | 5, 6 |
| Tests named in §5: `DockDropResolverTest`, `DockDrag` with synthetic events, the overlay paint test | 2, 4, 5, 6 |

**Placeholder scan.** No "TBD" or "similar to". The only blanks are the four test counts in the `STATUS.md` entry,
known only after the run.

**Type consistency.** `DockDropResolver.Group(slot, bounds, buttons)` and `Target.stripe/workspace/onStripe/index`
(Task 2) are what `ToolStripe.dropGroups` (Task 3), `DockDrag` (Task 5) and `WindowContributions.dropped` (Task 6)
use; `ToolStripe.beginDrag/showGap/endDrag/gapBounds/dragging` and the two `dragSource` fields (Task 3) are what
`DockDrag` and `WindowContent` call; `ToolWindowArea.metrics()` (Task 3) is what `DockDrag` previews with;
`DockState.moveAndShow` (Task 1) is used by Task 5's test drop and Task 6.

**Not compiled before handing over:** Task 6 (`WindowDragTest` and the two wiring scripts' result) needs the whole
app; review it first. Everything else ran as described under "Baseline facts".
