package dev.jasper.app.dock;

import dev.jasper.app.dock.WorkspaceDropResolver.Area;
import dev.jasper.app.dock.WorkspaceDropResolver.Drop;
import dev.jasper.app.layout.GroupId;
import dev.jasper.app.layout.Side;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Workspace tabs spec 12 and 17: where a dragged tab drops, from the cursor alone, for every zone and overlapping windows. */
class WorkspaceDropResolverTest {
    private static final GroupId LEFT = GroupId.random(), RIGHT = GroupId.random();
    /**
     * Two groups side by side, 6 px apart, under 28 px strips: the left strip shows two tabs besides the dragged one, the
     * right strip one tab 120 px wide.
     */
    private static final List<Area> TWO = List.of(
        new Area(LEFT, new Rectangle(0, 0, 400, 28), List.of(new Rectangle(0, 0, 200, 28), new Rectangle(200, 0, 200, 28)),
            new Rectangle(0, 28, 400, 572), true),
        new Area(RIGHT, new Rectangle(406, 0, 394, 28), List.of(new Rectangle(406, 0, 120, 28)),
            new Rectangle(406, 28, 394, 572), true));
    /** One group whose card area is 100 x 80 under a 10 px strip that shows no other tab, for pixel-exact boundaries. */
    private static final List<Area> SMALL = List.of(new Area(LEFT, new Rectangle(0, 0, 100, 10), List.of(), new Rectangle(0, 10, 100, 80), true));

    private static Drop at(List<Area> areas, int x, int y) {
        return WorkspaceDropResolver.resolve(new Point(x, y), areas).orElseThrow();
    }

    @Test void overAStripTheTabGoesBeforeTheFirstTabWhoseMiddleIsNotLeftOfTheCursor() {
        assertThat(at(TWO, 10, 10)).isEqualTo(new Drop.Strip(LEFT, 0));
        assertThat(at(TWO, 100, 10)).as("on a middle: still before that tab").isEqualTo(new Drop.Strip(LEFT, 0));
        assertThat(at(TWO, 101, 10)).isEqualTo(new Drop.Strip(LEFT, 1));
        assertThat(at(TWO, 399, 27)).as("the strip's last pixel").isEqualTo(new Drop.Strip(LEFT, 2));
        assertThat(at(TWO, 700, 0)).as("the empty end of a strip").isEqualTo(new Drop.Strip(RIGHT, 1));
        assertThat(at(SMALL, 50, 5)).as("a strip whose only tab is the dragged one").isEqualTo(new Drop.Strip(LEFT, 0));
    }

    @Test void theMiddleOfACardAreaAddsToItsGroup() {
        assertThat(at(TWO, 200, 300)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(TWO, 603, 314)).isEqualTo(new Drop.Centre(RIGHT));
    }

    @Test void eachOuterQuarterOfACardAreaSplitsItsSide() {
        assertThat(at(TWO, 40, 300)).isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(TWO, 360, 300)).isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(at(TWO, 200, 60)).as("just under the strip").isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(TWO, 200, 560)).isEqualTo(new Drop.Edge(LEFT, Side.DOWN));
        assertThat(at(TWO, 420, 300)).as("the right group's own left quarter").isEqualTo(new Drop.Edge(RIGHT, Side.LEFT));
    }

    @Test void theQuartersArePixelExact() {
        assertThat(at(SMALL, 0, 50)).as("the left edge is inside").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 24, 50)).isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 25, 50)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 74, 50)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 75, 50)).isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 29)).isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(SMALL, 50, 30)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 50, 69)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(SMALL, 50, 70)).isEqualTo(new Drop.Edge(LEFT, Side.DOWN));
        assertThat(at(SMALL, 99, 89)).as("the last pixel").isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
        assertThat(WorkspaceDropResolver.resolve(new Point(100, 50), SMALL)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 90), SMALL)).as("nor the bottom edge").isEmpty();
    }

    @Test void aCornerGoesToTheNearerEdgeAndATieToLeftThenRightThenUpThenDown() {
        assertThat(at(SMALL, 10, 15)).as("nearer the top").isEqualTo(new Drop.Edge(LEFT, Side.UP));
        assertThat(at(SMALL, 5, 20)).as("nearer the left").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 5, 14)).as("as near the left as the top").isEqualTo(new Drop.Edge(LEFT, Side.LEFT));
        assertThat(at(SMALL, 95, 86)).as("as near the right as the bottom").isEqualTo(new Drop.Edge(LEFT, Side.RIGHT));
    }

    @Test void theEmptyWorkspacesGroupIsItsCentreEverywhere() {
        var empty = List.of(new Area(LEFT, new Rectangle(), List.of(), new Rectangle(0, 0, 800, 600), false));
        assertThat(at(empty, 0, 0)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(empty, 400, 300)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(at(empty, 799, 599)).isEqualTo(new Drop.Centre(LEFT));
        assertThat(empty.getFirst().bounds()).as("with no strip, its whole is its card area").isEqualTo(new Rectangle(0, 0, 800, 600));
    }

    @Test void nothingBetweenOrOutsideTheGroups() {
        assertThat(WorkspaceDropResolver.resolve(new Point(403, 300), TWO)).as("the divider").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(900, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(-1, 10), TWO)).isEmpty();
    }

    @Test void theLandingIsTheWholeGroupItsHalfOnASideOrABarInTheStrip() {
        Rectangle whole = new Rectangle(0, 0, 400, 600);
        assertThat(TWO.getFirst().bounds()).as("the strip and the card area").isEqualTo(whole);
        assertThat(WorkspaceDropResolver.landing(new Drop.Centre(LEFT), TWO, 2)).isEqualTo(whole);
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.LEFT), TWO, 2)).isEqualTo(new Rectangle(0, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.RIGHT), TWO, 2)).isEqualTo(new Rectangle(200, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.UP), TWO, 2)).isEqualTo(new Rectangle(0, 0, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(LEFT, Side.DOWN), TWO, 2)).isEqualTo(new Rectangle(0, 300, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop.Edge(RIGHT, Side.RIGHT), TWO, 2)).as("an odd width: the new group's half")
            .isEqualTo(new Rectangle(603, 0, 197, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 1), TWO, 2)).as("between the two tabs")
            .isEqualTo(new Rectangle(199, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 0), TWO, 2)).as("before the first, kept inside the strip")
            .isEqualTo(new Rectangle(0, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 2), TWO, 2)).as("after the last, kept inside the strip")
            .isEqualTo(new Rectangle(398, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(RIGHT, 1), TWO, 2)).as("after the last, mid-strip")
            .isEqualTo(new Rectangle(525, 0, 2, 28));
        assertThat(WorkspaceDropResolver.landing(new Drop.Strip(LEFT, 0), SMALL, 2)).as("a strip with no other tab: its start")
            .isEqualTo(new Rectangle(0, 0, 2, 10));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.landing(new Drop.Centre(GroupId.random()), TWO, 2));
    }

    /** Parent tabs spec 5 and workspace plan 7c Rulings 2: a parent tab's place in a bar follows the strip rule. */
    @Test void aParentTabGoesBeforeTheFirstOtherEntryOfABarWhoseMiddleIsNotLeftOfTheCursor() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThat(WorkspaceDropResolver.place(new Point(10, 10), bar, entries)).hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(0, 0), bar, entries)).as("the bar's left and top edges are inside")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(100, 10), bar, entries)).as("on a middle: still before that entry")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(101, 10), bar, entries)).hasValue(1);
        assertThat(WorkspaceDropResolver.place(new Point(599, 27), bar, entries)).as("the bar's last pixel").hasValue(2);
        assertThat(WorkspaceDropResolver.place(new Point(300, 5), bar, List.of())).as("a bar with no other entry").hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(300, 28), bar, entries)).as("just under the bar").isEmpty();
        assertThat(WorkspaceDropResolver.place(new Point(-1, 10), bar, entries)).isEmpty();
        assertThat(WorkspaceDropResolver.marker(bar, entries, 1, 2)).as("between the two entries")
            .isEqualTo(new Rectangle(299, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, entries, 2, 2)).as("after the last, kept inside the bar")
            .isEqualTo(new Rectangle(598, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, List.of(), 0, 2)).as("no other entry: the bar's start")
            .isEqualTo(new Rectangle(0, 0, 2, 28));
    }

    /** A marker's place is 0 to the number of entries; any other is refused. */
    @Test void aMarkerOutsideItsEntriesIsRefused() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, entries, -1, 2))
            .withMessage("Index -1 is outside 0 to 2");
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, entries, 3, 2))
            .withMessage("Index 3 is outside 0 to 2");
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.marker(bar, List.of(), 1, 2))
            .withMessage("Index 1 is outside 0 to 0");
    }

    @Test void whereWindowsOverlapTheFirstListedTakesTheDrop() {
        Rectangle older = new Rectangle(0, 0, 800, 600), newer = new Rectangle(400, 300, 800, 600);
        Point both = new Point(500, 400);
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(newer, older))).as("the most recently active, listed first")
            .isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(older, newer))).isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(new Point(100, 100), List.of(newer, older))).isEqualTo(OptionalInt.of(1));
        assertThat(WorkspaceDropResolver.windowAt(new Point(1100, 800), List.of(newer, older))).isEqualTo(OptionalInt.of(0));
        assertThat(WorkspaceDropResolver.windowAt(new Point(2000, 2000), List.of(newer, older))).as("outside every window").isEmpty();
        assertThat(WorkspaceDropResolver.windowAt(both, List.of(new Rectangle(), older))).as("a window that does not show")
            .isEqualTo(OptionalInt.of(1));
    }

    @Test void anAreaKeepsItsOwnRectangles() {
        Rectangle strip = new Rectangle(0, 0, 100, 10);
        var area = new Area(LEFT, strip, List.of(new Rectangle(0, 0, 50, 10)), new Rectangle(0, 10, 100, 80), true);
        strip.width = 1;
        area.strip().width = 2;
        area.tabs().getFirst().width = 3;
        assertThat(area.strip()).isEqualTo(new Rectangle(0, 0, 100, 10));
        assertThat(area.tabs()).containsExactly(new Rectangle(0, 0, 50, 10));
    }
}
