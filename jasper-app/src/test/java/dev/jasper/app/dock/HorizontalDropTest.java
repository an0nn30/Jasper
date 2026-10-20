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
