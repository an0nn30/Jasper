package dev.jasper.app.workspace;

import dev.jasper.app.persistence.UiState;
import java.awt.Dimension;
import java.awt.Frame;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * A window's place (workspace tabs spec 13 and 15): its normal bounds, kept apart from maximizing, fitted to today's
 * displays, and kept on a display as a restored window and a torn-out one are.
 */
class WindowPlacementTest {
    private static final Dimension MINIMUM = new Dimension(300, 200);

    private static WindowPlacement saved(int x, int y, int width, int height, int extendedState) {
        return new WindowPlacement(Optional.of(new UiState.Bounds(x, y, width, height)), extendedState);
    }

    @Test void savedBoundsComeBackExactlyOnADisplayTheyFit() {
        var placement = saved(-1200, 75, 1103, 807, Frame.NORMAL);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(-1600, 30, 1600, 1000)))).hasValue(new Rectangle(-1200, 75, 1103, 807));
        assertThat(placement.extendedState()).isEqualTo(Frame.NORMAL);
    }

    @Test void maximizingAndMinimizingKeepTheNormalBoundsAndOnlyTheMaximizedBitsAreKept() {
        var placement = new WindowPlacement(Optional.empty(), 0);
        assertThat(placement.placement(Frame.NORMAL)).as("nothing seen yet").isEqualTo(JasperWindow.Placement.NONE);
        placement.observe(new Rectangle(100, 90, 1103, 807), Frame.NORMAL);
        placement.observe(new Rectangle(0, 0, 1600, 1000), Frame.MAXIMIZED_BOTH);
        placement.observe(new Rectangle(0, 0, 1, 1), Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        JasperWindow.Placement kept = placement.placement(Frame.MAXIMIZED_BOTH | Frame.ICONIFIED);
        assertThat(kept).isEqualTo(new JasperWindow.Placement(Optional.of(new UiState.Bounds(100, 90, 1103, 807)), Frame.MAXIMIZED_BOTH));
        var restored = new WindowPlacement(kept.bounds(), kept.extendedState());
        assertThat(restored.restore(MINIMUM, List.of(new Rectangle(0, 0, 1600, 1000)))).hasValue(new Rectangle(100, 90, 1103, 807));
        assertThat(restored.extendedState()).as("never restored minimized").isEqualTo(Frame.MAXIMIZED_BOTH);
    }

    @Test void aWindowSpanningUnchangedDisplaysKeepsItsExactBounds() {
        assertThat(saved(1200, 100, 1000, 700, Frame.NORMAL).restore(MINIMUM,
            List.of(new Rectangle(0, 0, 1600, 1000), new Rectangle(1600, 0, 1600, 1000)))).hasValue(new Rectangle(1200, 100, 1000, 700));
    }

    @Test void aRemovedMonitorFitsTheWindowOntoTheRemainingScreen() {
        var placement = saved(-1700, 80, 1500, 1100, Frame.NORMAL);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(0, 25, 1280, 775)))).hasValue(new Rectangle(0, 25, 1280, 775));
        assertThat(placement.placement(Frame.NORMAL).bounds()).as("what is saved next is where it went")
            .hasValue(new UiState.Bounds(0, 25, 1280, 775));
    }

    @Test void aWindowWithoutSavedBoundsGoesWhereTheFramePutsANewWindow() {
        var placement = new WindowPlacement(Optional.empty(), Frame.MAXIMIZED_BOTH);
        assertThat(placement.restore(MINIMUM, List.of(new Rectangle(0, 0, 1600, 1000)))).isEmpty();
        assertThat(placement.extendedState()).isEqualTo(Frame.MAXIMIZED_BOTH);
    }

    /** Plan 5's tear-out case, moved here with its helper (workspace plan 6a Rulings 24). */
    @Test void aTearOutWindowIsKeptOnTheDisplayUnderTheCursor() {
        Rectangle usable = new Rectangle(0, 25, 1440, 875);
        assertThat(WindowPlacement.fitted(new Rectangle(100, 100, 800, 600), usable)).as("on it: as it is").isEqualTo(new Rectangle(100, 100, 800, 600));
        assertThat(WindowPlacement.fitted(new Rectangle(1000, 600, 800, 600), usable)).as("past its right and bottom edges: moved onto it")
            .isEqualTo(new Rectangle(640, 300, 800, 600));
        assertThat(WindowPlacement.fitted(new Rectangle(-50, 0, 2000, 1200), usable)).as("larger: as large as it").isEqualTo(usable);
        assertThat(WindowPlacement.fitted(new Rectangle(5, 5, 800, 600), new Rectangle())).as("no display known: as it is")
            .isEqualTo(new Rectangle(5, 5, 800, 600));
    }

    /**
     * A tear-out's workspace reaches right of and below the cursor by most of a group, so near the edge of a display left
     * of or above another it mostly covers that other one: the display under the cursor takes the window all the same.
     * Plan 5's case, moved here with its helper.
     */
    @Test void aTearOutWindowOpensOnTheDisplayUnderTheCursor() {
        List<Rectangle> displays = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(1440, 0, 1920, 1080));
        assertThat(WindowPlacement.displayFor(new Point(1500, 220), displays)).as("its corner on the left display, the cursor on the right")
            .hasValue(1);
        assertThat(WindowPlacement.displayFor(new Point(130, 120), displays)).as("on the left").hasValue(0);
        assertThat(WindowPlacement.displayFor(new Point(1440, 0), displays)).as("on the edge: the display it begins").hasValue(1);
        assertThat(WindowPlacement.displayFor(new Point(5000, 5000), displays)).as("on none: the default display").isEmpty();
        List<Rectangle> left = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(-1440, 0, 1440, 900));
        Rectangle workspace = new Rectangle(-80, 280, 800, 600);
        assertThat(workspace.intersection(left.get(0)).width).as("the workspace is mostly on the primary").isGreaterThan(700);
        assertThat(WindowPlacement.displayFor(new Point(-50, 300), left)).as("the cursor on the display left of the primary").hasValue(1);
        List<Rectangle> above = List.of(new Rectangle(0, 0, 1920, 1080), new Rectangle(0, -900, 1440, 900));
        assertThat(WindowPlacement.displayFor(new Point(400, -40), above)).as("the cursor on the display above the primary").hasValue(1);
        List<Rectangle> mirrored = List.of(new Rectangle(0, 0, 1440, 900), new Rectangle(0, 0, 1440, 900));
        assertThat(WindowPlacement.displayFor(new Point(100, 100), mirrored)).as("displays that overlap: the first").hasValue(0);
        assertThat(WindowPlacement.displayFor(new Point(100, 100), List.of())).as("no display known: the default").isEmpty();
    }
}
