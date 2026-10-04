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
