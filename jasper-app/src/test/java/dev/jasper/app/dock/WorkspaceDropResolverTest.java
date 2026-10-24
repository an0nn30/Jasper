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
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/**
 * Tabs and panes spec 6 and 7, workspace tabs spec 12 and 17: where a dragged pane or tab drops, from the cursor alone,
 * and which of overlapping windows takes it.
 */
class WorkspaceDropResolverTest {
    private static final GroupId LEFT = GroupId.random(), RIGHT = GroupId.random();
    /** Two panes side by side, 6 px apart. */
    private static final List<Area> TWO = List.of(new Area(LEFT, new Rectangle(0, 0, 400, 600)),
        new Area(RIGHT, new Rectangle(406, 0, 394, 600)));
    /** One pane 100 x 80 from 0, 10, for pixel-exact boundaries: the shares are dx / 100 and dy / 80. */
    private static final List<Area> SMALL = List.of(new Area(LEFT, new Rectangle(0, 10, 100, 80)));

    private static Drop at(List<Area> areas, int x, int y) {
        return WorkspaceDropResolver.resolve(new Point(x, y), areas).orElseThrow();
    }

    /** Workspace plan 8c Rulings 6: beside the pane under the cursor, on the nearer side; a pane has no centre. */
    @Test void aPaneGoesBesideThePaneUnderTheCursorOnTheNearestSide() {
        assertThat(at(TWO, 40, 300)).isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(TWO, 360, 300)).isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(TWO, 200, 60)).isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(TWO, 200, 560)).isEqualTo(new Drop(LEFT, Side.DOWN));
        assertThat(at(TWO, 420, 300)).as("the right pane's own left").isEqualTo(new Drop(RIGHT, Side.LEFT));
        assertThat(at(TWO, 190, 300)).as("near the middle: still the nearer side").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(TWO, 210, 300)).isEqualTo(new Drop(LEFT, Side.RIGHT));
    }

    @Test void theSidesAreTheTrianglesBetweenThePanesDiagonalsPixelExact() {
        assertThat(at(SMALL, 0, 50)).as("the left edge is inside").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 50, 10)).as("the top edge is inside").isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 99, 50)).as("the last column").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 89)).as("the last row").isEqualTo(new Drop(LEFT, Side.DOWN));
        assertThat(at(SMALL, 20, 30)).as("a fifth of the width against a quarter of the height").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 30, 30)).as("three tenths against a quarter").isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 50, 20)).isEqualTo(new Drop(LEFT, Side.UP));
        assertThat(at(SMALL, 80, 60)).isEqualTo(new Drop(LEFT, Side.RIGHT));
    }

    @Test void aTieGoesLeftThenRightThenUpThenDown() {
        assertThat(at(SMALL, 25, 30)).as("as near the left as the top").isEqualTo(new Drop(LEFT, Side.LEFT));
        assertThat(at(SMALL, 75, 30)).as("as near the right as the top").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 75, 70)).as("as near the right as the bottom").isEqualTo(new Drop(LEFT, Side.RIGHT));
        assertThat(at(SMALL, 50, 50)).as("the middle, as near every side").isEqualTo(new Drop(LEFT, Side.LEFT));
    }

    @Test void nothingBetweenOrOutsideThePanes() {
        assertThat(WorkspaceDropResolver.resolve(new Point(403, 300), TWO)).as("the divider").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(900, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(-1, 10), TWO)).isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(100, 50), SMALL)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 90), SMALL)).as("nor the bottom edge").isEmpty();
        assertThat(WorkspaceDropResolver.resolve(new Point(50, 50), List.of())).as("no pane").isEmpty();
    }

    @Test void theLandingIsThePanesHalfOnItsSide() {
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.LEFT), TWO)).isEqualTo(new Rectangle(0, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.RIGHT), TWO)).isEqualTo(new Rectangle(200, 0, 200, 600));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.UP), TWO)).isEqualTo(new Rectangle(0, 0, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop(LEFT, Side.DOWN), TWO)).isEqualTo(new Rectangle(0, 300, 400, 300));
        assertThat(WorkspaceDropResolver.landing(new Drop(RIGHT, Side.RIGHT), TWO)).as("an odd width: the new pane's half")
            .isEqualTo(new Rectangle(603, 0, 197, 600));
        assertThatIllegalArgumentException().isThrownBy(() -> WorkspaceDropResolver.landing(new Drop(GroupId.random(), Side.LEFT), TWO));
    }

    /** Tabs and panes spec 6 and workspace plan 7c Rulings 2: a tab's place in a strip follows the strip rule. */
    @Test void aTabGoesBeforeTheFirstOtherEntryOfAStripWhoseMiddleIsNotLeftOfTheCursor() {
        Rectangle bar = new Rectangle(0, 0, 600, 28);
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 200, 28), new Rectangle(400, 0, 200, 28));
        assertThat(WorkspaceDropResolver.place(new Point(10, 10), bar, entries)).hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(0, 0), bar, entries)).as("the strip's left and top edges are inside")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(100, 10), bar, entries)).as("on a middle: still before that entry")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(101, 10), bar, entries)).hasValue(1);
        assertThat(WorkspaceDropResolver.place(new Point(599, 27), bar, entries)).as("the strip's last pixel").hasValue(2);
        assertThat(WorkspaceDropResolver.place(new Point(300, 5), bar, List.of())).as("a strip with no other entry").hasValue(0);
        assertThat(WorkspaceDropResolver.place(new Point(300, 28), bar, entries)).as("just under the strip").isEmpty();
        assertThat(WorkspaceDropResolver.place(new Point(-1, 10), bar, entries)).isEmpty();
        assertThat(WorkspaceDropResolver.marker(bar, entries, 1, 2)).as("between the two entries")
            .isEqualTo(new Rectangle(299, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, entries, 2, 2)).as("after the last, kept inside the strip")
            .isEqualTo(new Rectangle(598, 0, 2, 28));
        assertThat(WorkspaceDropResolver.marker(bar, List.of(), 0, 2)).as("no other entry: the strip's start")
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

    @Test void anAreaKeepsItsOwnRectangleAndADropNeedsBoth() {
        Rectangle bounds = new Rectangle(0, 0, 100, 80);
        var area = new Area(LEFT, bounds);
        bounds.width = 1;
        area.bounds().width = 2;
        assertThat(area.bounds()).isEqualTo(new Rectangle(0, 0, 100, 80));
        assertThatNullPointerException().isThrownBy(() -> new Drop(LEFT, null));
        assertThatNullPointerException().isThrownBy(() -> new Area(null, bounds));
    }

    /** Workspace plan 8c Rulings 3: an entry's outer quarters are new-tab places, before and after it; its middle half is none. */
    @Test void anEntrysOuterQuartersAreNewTabPlaces() {
        List<Rectangle> entries = List.of(new Rectangle(0, 0, 300, 30), new Rectangle(300, 0, 300, 30),
            new Rectangle(600, 0, 300, 30));
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(0, 15), entries)).as("the left edge is inside").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(74, 15), entries)).as("the leading quarter's last pixel").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(75, 15), entries)).as("the middle half").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(224, 15), entries)).isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(225, 15), entries)).as("the trailing quarter").hasValue(1);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(300, 15), entries)).as("the next one's leading quarter").hasValue(1);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(899, 15), entries)).as("after the last").hasValue(3);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(900, 15), entries)).as("the right edge is not inside").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(450, 30), entries)).as("nor the bottom edge").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(10, 10), List.of())).as("no entry").isEmpty();
    }

    /**
     * Workspace plan 8c Rulings 3 (its final review, T3 M2): a quarter is a whole number of pixels, the width over four
     * rounded down, so an odd width's middle takes the rest; an entry under 4 px wide has no quarter, only a middle.
     */
    @Test void oddWidthsRoundTheQuartersDownAndANarrowEntryIsAllMiddle() {
        List<Rectangle> odd = List.of(new Rectangle(10, 0, 7, 30));
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(10, 15), odd)).as("7 px: the one leading pixel").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(11, 15), odd)).as("then the middle's five").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(15, 15), odd)).isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(16, 15), odd)).as("the one trailing pixel").hasValue(1);
        List<Rectangle> wider = List.of(new Rectangle(0, 0, 301, 30));
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(74, 15), wider)).as("301 px: quarters of 75").hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(75, 15), wider)).isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(225, 15), wider)).as("the middle takes 151").isEmpty();
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(226, 15), wider)).hasValue(1);
        for (int width = 1; width < 4; width++) {
            List<Rectangle> narrow = List.of(new Rectangle(0, 0, width, 30));
            for (int x = 0; x < width; x++)
                assertThat(WorkspaceDropResolver.newTabPlace(new Point(x, 15), narrow)).as("%d px wide, at %d", width, x).isEmpty();
        }
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(0, 15), List.of(new Rectangle(0, 0, 4, 30)))).as("4 px: a quarter")
            .hasValue(0);
        assertThat(WorkspaceDropResolver.newTabPlace(new Point(0, 15), List.of(new Rectangle(0, 0, 0, 30)))).as("no width").isEmpty();
    }
}
