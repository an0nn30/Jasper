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
