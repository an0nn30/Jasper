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

    private static Target at(int x, int y) { return at(WORKSPACE, x, y); }

    private static Target at(Rectangle workspace, int x, int y) {
        return DockDropResolver.resolve(new Point(x, y), List.of(), workspace).orElseThrow();
    }

    /** A point at a fraction of the workspace's width and height, from its top left. */
    private static Point inside(Rectangle workspace, double across, double down) {
        return new Point(workspace.x + (int) (workspace.width * across), workspace.y + (int) (workspace.height * down));
    }

    /**
     * One point well inside each of the six zones, as fractions of any workspace: close to one edge and clearly farther
     * from the others, so the zones are the same for every aspect ratio.
     */
    private static void assertEverySlotIsReachable(Rectangle workspace) {
        assertThat(DockDropResolver.resolve(inside(workspace, 0.10, 0.20), List.of(), workspace)).as("left, upper half")
            .hasValue(Target.workspace(Slot.LEFT_TOP));
        assertThat(DockDropResolver.resolve(inside(workspace, 0.10, 0.80), List.of(), workspace)).as("left, lower half")
            .hasValue(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(DockDropResolver.resolve(inside(workspace, 0.90, 0.20), List.of(), workspace)).as("right, upper half")
            .hasValue(Target.workspace(Slot.RIGHT_TOP));
        assertThat(DockDropResolver.resolve(inside(workspace, 0.90, 0.80), List.of(), workspace)).as("right, lower half")
            .hasValue(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(DockDropResolver.resolve(inside(workspace, 0.25, 0.95), List.of(), workspace)).as("bottom, left half")
            .hasValue(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(DockDropResolver.resolve(inside(workspace, 0.75, 0.95), List.of(), workspace)).as("bottom, right half")
            .hasValue(Target.workspace(Slot.BOTTOM_RIGHT));
    }

    @Test void theWorkspaceHasSixZonesOneForEachSlot() {
        assertThat(at(100, 100)).isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(100, 500)).isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(900, 100)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(at(900, 500)).isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(280, 600)).isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(at(780, 600)).isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(at(100, 100).onStripe()).isFalse();
        assertEverySlotIsReachable(WORKSPACE);
    }

    @Test void theLowerHalfOfEachSideIsItsBottomSlotNotTheBottomArea() {
        // The probe's window: the lower part of each side's band used to fall in the foot row of the old 3 x 2 grid.
        Rectangle probe = new Rectangle(28, 30, 1144, 746);
        assertThat(at(probe, probe.x + 130, probe.y + 560)).as("lower half of the left band")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(probe, probe.x + probe.width - 130, probe.y + 560)).as("lower half of the right band")
            .isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(probe, probe.x + 130, probe.y + 150)).isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(probe, probe.x + probe.width - 130, probe.y + 150)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
    }

    @Test void theNearestEdgeDecidesAndTheDiagonalSplitsASideFromTheBottom() {
        // From each lower corner a diagonal runs to the middle: a side's distance counts as a share of the width, the
        // bottom's as a share of the height. In 1000 x 600 the left edge and the bottom are level where
        // dx / 1000 = (600 - dy) / 600, the right edge and the bottom where (1000 - dx) / 1000 = (600 - dy) / 600.
        assertThat(at(28 + 16, 30 + 590)).as("just above the left diagonal").isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(28 + 17, 30 + 590)).as("just below it").isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(at(28 + 1000 - 16, 30 + 590)).as("just above the right diagonal").isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(28 + 1000 - 17, 30 + 590)).as("just below it").isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(at(28 + 100, 30 + 540)).as("100px in, the diagonal is at 540px down, level: the side wins")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(28 + 100, 30 + 541)).as("one pixel lower the bottom is nearer").isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
    }

    @Test void aSideSplitsAtHalfTheHeightAndTheBottomAtHalfTheWidth() {
        assertThat(at(28 + 10, 30 + 299)).as("left, just above the half").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(28 + 10, 30 + 300)).as("left, the half belongs to the lower slot").isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(28 + 989, 30 + 299)).as("right, just above the half").isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(at(28 + 989, 30 + 300)).as("right, the half belongs to the lower slot").isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(28 + 499, 30 + 595)).as("bottom, just left of the half").isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(at(28 + 500, 30 + 595)).as("bottom, the half belongs to the right slot").isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
    }

    @Test void aSideWinsATieWithTheBottomAndLeftWinsATieWithRight() {
        // 1000 x 500: the left edge and the bottom are both 2% away at dx 20, dy 490; the right edge at dx 980.
        Rectangle w = new Rectangle(28, 30, 1000, 500);
        assertThat(at(w, 28 + 20, 30 + 490)).as("level with the bottom, the left edge wins").isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(w, 28 + 21, 30 + 490)).as("one pixel farther, the bottom is nearer").isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(at(w, 28 + 980, 30 + 490)).as("level with the bottom, the right edge wins").isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(w, 28 + 979, 30 + 490)).as("one pixel farther, the bottom is nearer").isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(at(w, 28 + 500, 30 + 100)).as("level between left and right, left wins").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(w, 28 + 501, 30 + 100)).as("one pixel right of the middle").isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(at(w, 28 + 500, 30 + 250)).as("all three edges level at the exact middle: left, and the lower half")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
    }

    @Test void aWideWorkspaceKeepsAllSixZonesAndItsUpperMiddleIsNotTheBottom() {
        Rectangle wide = new Rectangle(28, 30, 2000, 600);
        assertEverySlotIsReachable(wide);
        // Measured in pixels the bottom (500 away) would beat both sides (1000 away); as shares of each side's own
        // length the sides are at one half and the bottom at five sixths.
        assertThat(at(wide, wide.x + 1000, wide.y + 100)).isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(wide, wide.x + 1100, wide.y + 100)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(at(wide, wide.x + 900, wide.y + 100)).isEqualTo(Target.workspace(Slot.LEFT_TOP));
    }

    @Test void aTallWorkspaceKeepsAllSixZones() {
        Rectangle tall = new Rectangle(28, 30, 600, 1400);
        assertEverySlotIsReachable(tall);
        assertThat(at(tall, tall.x + 300, tall.y + 1300)).as("the middle of the bottom, near its edge").isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(at(tall, tall.x + 299, tall.y + 1300)).isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
    }

    @Test void theWorkspaceCountsItsLeftAndTopEdgesInAndItsRightAndBottomEdgesOut() {
        assertThat(at(28, 30)).as("the top left pixel").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(28 + 999, 30 + 599)).as("the last pixel").isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(DockDropResolver.resolve(new Point(28 + 1000, 100), List.of(), WORKSPACE)).as("past the right edge").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(100, 30 + 600), List.of(), WORKSPACE)).as("past the bottom edge").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(27, 100), List.of(), WORKSPACE)).as("left of the left edge").isEmpty();
        assertThat(DockDropResolver.resolve(new Point(100, 29), List.of(), WORKSPACE)).as("above the top edge").isEmpty();
    }

    private static Target split(Rectangle workspace, int splitY, int x, int y) {
        return DockDropResolver.resolve(new Point(x, y), List.of(), workspace, splitY).orElseThrow();
    }

    @Test void aSplitLineAboveTheMiddleMovesWhereASideTurnsFromTopToBottom() {
        // A bottom area 200px tall under the sides leaves them 400px of the 600: their visible half is at 200, not 300.
        int splitY = WORKSPACE.y + 200;
        int between = WORKSPACE.y + 250;
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 100, between)).as("left, between the split line and the middle")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(at(WORKSPACE.x + 100, between)).as("the three-argument form splits at the middle").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 900, between)).as("right, between the split line and the middle")
            .isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(at(WORKSPACE.x + 900, between)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 100, splitY - 1)).as("just above the line").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 100, splitY)).as("the line belongs to the bottom slot")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 900, splitY - 1)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(split(WORKSPACE, splitY, WORKSPACE.x + 900, splitY)).isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
    }

    @Test void theSplitLineChangesNeitherTheBottomZonesNorTheTies() {
        for (int splitY : new int[] {WORKSPACE.y, WORKSPACE.y + 200, WORKSPACE.y + 300, WORKSPACE.y + 599}) {
            assertThat(split(WORKSPACE, splitY, 280, 600)).as("bottom, left half, split %s", splitY).isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
            assertThat(split(WORKSPACE, splitY, 780, 600)).as("bottom, right half, split %s", splitY).isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
            assertThat(split(WORKSPACE, splitY, 28 + 499, 30 + 595)).isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
            assertThat(split(WORKSPACE, splitY, 28 + 500, 30 + 595)).as("the bottom's half width").isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        }
        Rectangle w = new Rectangle(28, 30, 1000, 500);
        int splitY = w.y + 150;
        assertThat(split(w, splitY, 28 + 20, 30 + 490)).as("level with the bottom, the left edge wins")
            .isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(split(w, splitY, 28 + 21, 30 + 490)).as("one pixel farther, the bottom is nearer").isEqualTo(Target.workspace(Slot.BOTTOM_LEFT));
        assertThat(split(w, splitY, 28 + 980, 30 + 490)).as("level with the bottom, the right edge wins")
            .isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
        assertThat(split(w, splitY, 28 + 979, 30 + 490)).isEqualTo(Target.workspace(Slot.BOTTOM_RIGHT));
        assertThat(split(w, splitY, 28 + 500, 30 + 100)).as("level between left and right, left wins").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(split(w, splitY, 28 + 501, 30 + 100)).isEqualTo(Target.workspace(Slot.RIGHT_TOP));
        assertThat(split(w, splitY, 28 + 500, 30 + 200)).as("the same lines, below the split").isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
        assertThat(split(w, splitY, 28 + 501, 30 + 200)).isEqualTo(Target.workspace(Slot.RIGHT_BOTTOM));
    }

    @Test void theThreeArgumentFormSplitsAtTheWorkspacesHalfLikeTheFourArgumentFormEvenForAnOddHeight() {
        for (Rectangle w : new Rectangle[] {new Rectangle(28, 30, 1000, 600), new Rectangle(28, 30, 1000, 601)}) {
            int half = w.y + (w.height + 1) / 2;
            for (int x : new int[] {w.x + 10, w.x + w.width - 10}) {
                for (int y = w.y + 295; y < w.y + 306; y++) {
                    assertThat(DockDropResolver.resolve(new Point(x, y), List.of(), w))
                        .as("%s at %s,%s", w, x, y).isEqualTo(DockDropResolver.resolve(new Point(x, y), List.of(), w, half));
                }
            }
        }
        Rectangle odd = new Rectangle(28, 30, 1000, 601);
        assertThat(at(odd, odd.x + 10, odd.y + 300)).as("2 * 300 < 601").isEqualTo(Target.workspace(Slot.LEFT_TOP));
        assertThat(at(odd, odd.x + 10, odd.y + 301)).as("2 * 301 is not").isEqualTo(Target.workspace(Slot.LEFT_BOTTOM));
    }

    @Test void aStripeGroupWinsOverTheWorkspaceWhereTheyOverlap() {
        var group = new Group(Slot.BOTTOM_RIGHT, new Rectangle(900, 500, 100, 100), List.of());
        assertThat(DockDropResolver.resolve(new Point(950, 550), List.of(group), WORKSPACE)).hasValue(Target.stripe(Slot.BOTTOM_RIGHT, 0));
        assertThat(DockDropResolver.resolve(new Point(950, 450), List.of(group), WORKSPACE)).as("beside the group, the workspace decides")
            .hasValue(Target.workspace(Slot.RIGHT_BOTTOM));
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
