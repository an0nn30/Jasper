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
        assertThat(changes).containsExactly(new DockState.Change(true, true, List.of("b"), List.of(), List.of("a")));

        changes.clear();
        state.move("c", Slot.BOTTOM_LEFT, 0);
        assertThat(state.visible("c")).as("a hidden window stays hidden").isFalse();
        assertThat(state.slot("c")).hasValue(Slot.BOTTOM_LEFT);
        assertThat(changes).containsExactly(new DockState.Change(false, true, List.of(), List.of(), List.of("c")));
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

    @Test void dropIndexReturnsTheWindowsPlaceIfItAlreadyIsInTheTargetSlot() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP); state.add("c", Slot.LEFT_TOP);
        assertThat(state.dropIndex("b", Slot.LEFT_TOP)).isEqualTo(1);
    }

    @Test void dropIndexReturnsTheTargetGroupsSizeForAWindowInAnotherSlot() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_BOTTOM); state.add("c", Slot.LEFT_BOTTOM);
        assertThat(state.dropIndex("a", Slot.LEFT_BOTTOM)).isEqualTo(2);
    }

    @Test void dropIndexReturnsZeroForAnEmptyTargetSlot() {
        state.add("a", Slot.LEFT_TOP);
        assertThat(state.dropIndex("a", Slot.LEFT_BOTTOM)).isEqualTo(0);
    }

    @Test void dropIndexReturnsTheTargetGroupsSizeForAnUnknownId() {
        state.add("a", Slot.LEFT_TOP); state.add("b", Slot.LEFT_TOP);
        assertThat(state.dropIndex("unknown", Slot.LEFT_TOP)).isEqualTo(2);
    }
}
