package dev.jasper.app.dock;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static dev.jasper.app.dock.ToolWindowMode.DOCKED;
import static dev.jasper.app.dock.ToolWindowMode.FLOAT;
import static dev.jasper.app.dock.ToolWindowMode.WINDOW;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;

/** A tool window that leaves its slot for a window of its own: what it keeps, what shows and what each change reports. */
class DockStateDetachTest {
    private final DockState state = new DockState();
    private final List<DockState.Change> changes = new ArrayList<>();

    DockStateDetachTest() {
        state.add("a", Slot.LEFT_TOP);
        state.add("b", Slot.LEFT_TOP);
        state.add("c", Slot.RIGHT_TOP);
        state.addListener(changes::add);
    }

    private static DockState.Change change(boolean layout, boolean stripes, List<String> hidden, List<String> shown, List<String> placed) {
        return new DockState.Change(layout, stripes, hidden, shown, placed);
    }

    @Test void theFourArgumentChangeHasNothingPlaced() {
        assertThat(new DockState.Change(true, false, List.of("a"), List.of("b")))
            .isEqualTo(change(true, false, List.of("a"), List.of("b"), List.of()));
    }

    @Test void floatingAShowingWindowKeepsItShownOutsideItsSlot() {
        state.show("a");
        changes.clear();
        state.setMode("a", FLOAT);
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of(), List.of("a")));
        assertThat(state.visible("a")).isTrue();
        assertThat(state.active(Slot.LEFT_TOP)).isEmpty();
        assertThat(state.slot("a")).hasValue(Slot.LEFT_TOP);
        assertThat(state.ids(Slot.LEFT_TOP)).containsExactly("a", "b");
        assertThat(state.detachedVisible()).containsExactly("a");
        assertThat(state.mode("a")).isEqualTo(FLOAT);
    }

    @Test void floatingAHiddenWindowChangesOnlyItsMode() {
        state.setMode("a", WINDOW);
        assertThat(changes).containsExactly(change(false, true, List.of(), List.of(), List.of("a")));
        assertThat(state.visible("a")).isFalse();
        assertThat(state.mode("a")).isEqualTo(WINDOW);
        assertThat(state.detachedVisible()).isEmpty();
    }

    @Test void dockingAShownDetachedWindowShowsItInItsSlotAndHidesTheOther() {
        state.show("b");
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.setMode("a", DOCKED);
        assertThat(changes).containsExactly(change(true, true, List.of("b"), List.of(), List.of("a")));
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("a");
        assertThat(state.visible("a")).isTrue();
        assertThat(state.visible("b")).isFalse();
        assertThat(state.mode("a")).isEqualTo(DOCKED);
        assertThat(state.detachedVisible()).isEmpty();
    }

    @Test void dockingAShownDetachedWindowIntoAnEmptySlotHidesNothing() {
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.setMode("a", DOCKED);
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of(), List.of("a")));
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("a");
    }

    @Test void dockingAHiddenDetachedWindowLeavesItHidden() {
        state.show("b");
        state.setMode("a", WINDOW);
        changes.clear();
        state.setMode("a", DOCKED);
        assertThat(changes).containsExactly(change(false, true, List.of(), List.of(), List.of("a")));
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
        assertThat(state.visible("a")).isFalse();
    }

    @Test void floatToWindowIsAPlacementChange() {
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.setMode("a", WINDOW);
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of(), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(WINDOW);
        assertThat(state.visible("a")).isTrue();
    }

    @Test void theSameModeIsSilent() {
        state.setMode("a", DOCKED);
        state.setMode("zzz", FLOAT);
        assertThat(changes).isEmpty();
        state.setMode("a", WINDOW);
        changes.clear();
        state.setMode("a", WINDOW);
        assertThat(changes).isEmpty();
        assertThat(state.mode("zzz")).isEqualTo(DOCKED);
        assertThat(state.slot("zzz")).isEmpty();
        assertThatNullPointerException().isThrownBy(() -> state.setMode("a", null));
    }

    @Test void showAndHideOfADetachedWindowLeaveTheSlotAlone() {
        state.show("b");
        state.setMode("a", FLOAT);
        changes.clear();
        state.show("a");
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of("a"), List.of()));
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
        assertThat(state.visible("a")).isTrue();
        assertThat(state.visible("b")).isTrue();
        state.show("a");
        assertThat(changes).as("showing what shows is silent").hasSize(1);
        changes.clear();
        state.hide("a");
        assertThat(changes).containsExactly(change(true, true, List.of("a"), List.of(), List.of()));
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
        assertThat(state.visible("a")).isFalse();
        state.hide("a");
        assertThat(changes).as("hiding what is hidden is silent").hasSize(1);
        changes.clear();
        state.toggle("a");
        assertThat(state.visible("a")).isTrue();
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of("a"), List.of()));
        state.toggle("a");
        assertThat(state.visible("a")).isFalse();
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("b");
    }

    @Test void detachShowsAtTheBoundsInOneChange() {
        state.show("a");
        changes.clear();
        state.detach("a", FLOAT, new Rectangle(10, 20, 300, 200));
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of(), List.of("a")));
        assertThat(state.bounds("a")).hasValue(new Rectangle(10, 20, 300, 200));
        assertThat(state.mode("a")).isEqualTo(FLOAT);
        assertThat(state.active(Slot.LEFT_TOP)).isEmpty();
        assertThat(state.detachedVisible()).containsExactly("a");

        changes.clear();
        state.detach("b", WINDOW, new Rectangle(0, 0, 400, 300));
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of("b"), List.of("b")));
        assertThat(state.visible("b")).isTrue();
        assertThat(state.mode("b")).isEqualTo(WINDOW);
        assertThat(state.detachedVisible()).containsExactly("a", "b");

        changes.clear();
        state.detach("a", FLOAT, new Rectangle(50, 60, 300, 200));
        assertThat(changes).as("a window at new bounds, same mode").containsExactly(change(true, true, List.of(), List.of(), List.of()));
        assertThat(state.bounds("a")).hasValue(new Rectangle(50, 60, 300, 200));
        changes.clear();
        state.detach("a", FLOAT, new Rectangle(50, 60, 300, 200));
        assertThat(changes).as("the same call again").isEmpty();

        assertThatIllegalArgumentException().isThrownBy(() -> state.detach("a", DOCKED, new Rectangle(0, 0, 10, 10)));
        assertThatIllegalArgumentException().isThrownBy(() -> state.detach("a", FLOAT, new Rectangle(0, 0, 0, 10)));
        assertThatIllegalArgumentException().isThrownBy(() -> state.detach("a", FLOAT, new Rectangle(0, 0, 10, -1)));
        assertThat(changes).isEmpty();
    }

    @Test void detachOfAHiddenDetachedWindowInAnotherModeShowsItAndPlacesIt() {
        state.setMode("a", FLOAT);
        changes.clear();
        state.detach("a", WINDOW, new Rectangle(1, 2, 300, 200));
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of("a"), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(WINDOW);
        assertThat(state.visible("a")).isTrue();
    }

    @Test void detachOfAnUnknownIdIsIgnored() {
        state.detach("zzz", FLOAT, new Rectangle(0, 0, 10, 10));
        assertThat(changes).isEmpty();
        assertThat(state.bounds("zzz")).isEmpty();
        assertThat(state.mode("zzz")).isEqualTo(DOCKED);
    }

    @Test void boundsAreCopiedInAndOut() {
        var given = new Rectangle(1, 2, 30, 40);
        state.detach("a", FLOAT, given);
        given.x = 99;
        state.bounds("a").orElseThrow().width = 99;
        assertThat(state.bounds("a")).hasValue(new Rectangle(1, 2, 30, 40));
    }

    @Test void setBoundsIsSilentAndKeptAfterDocking() {
        var r = new Rectangle(5, 6, 700, 500);
        assertThat(state.bounds("a")).isEmpty();
        state.setBounds("a", r);
        assertThat(changes).isEmpty();
        assertThat(state.bounds("a")).hasValue(r);
        state.setMode("a", FLOAT);
        state.setMode("a", DOCKED);
        assertThat(state.bounds("a")).as("docking back keeps where the window was").hasValue(r);
        state.setBounds("a", new Rectangle(7, 8, 710, 510));
        assertThat(state.bounds("a")).hasValue(new Rectangle(7, 8, 710, 510));

        assertThatIllegalArgumentException().isThrownBy(() -> state.setBounds("a", new Rectangle(0, 0, 0, 10)));
        assertThatIllegalArgumentException().isThrownBy(() -> state.setBounds("a", new Rectangle(0, 0, 10, 0)));
        assertThatIllegalArgumentException().isThrownBy(() -> state.setBounds("zzz", new Rectangle(0, 0, -1, 10)));
        assertThatNullPointerException().isThrownBy(() -> state.setBounds("a", null));
        assertThat(state.bounds("a")).hasValue(new Rectangle(7, 8, 710, 510));

        changes.clear();
        state.setBounds("zzz", r);
        assertThat(state.bounds("zzz")).isEmpty();
        assertThat(changes).isEmpty();
    }

    @Test void aStripeMoveKeepsADetachedWindowDetached() {
        state.show("c");
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.move("a", Slot.RIGHT_TOP, 0);
        assertThat(changes).containsExactly(change(false, true, List.of(), List.of(), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(FLOAT);
        assertThat(state.visible("a")).isTrue();
        assertThat(state.slot("a")).hasValue(Slot.RIGHT_TOP);
        assertThat(state.ids(Slot.RIGHT_TOP)).containsExactly("a", "c");
        assertThat(state.active(Slot.RIGHT_TOP)).hasValue("c");
    }

    @Test void aWorkspaceDropDocksADetachedWindow() {
        state.show("c");
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.moveAndShow("a", Slot.RIGHT_TOP, 0);
        assertThat(changes).containsExactly(change(true, true, List.of("c"), List.of(), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(DOCKED);
        assertThat(state.active(Slot.RIGHT_TOP)).hasValue("a");
        assertThat(state.detachedVisible()).isEmpty();
        assertThat(state.ids(Slot.RIGHT_TOP)).containsExactly("a", "c");
    }

    @Test void aWorkspaceDropOfAHiddenDetachedWindowShowsIt() {
        state.setMode("a", WINDOW);
        changes.clear();
        state.moveAndShow("a", Slot.RIGHT_TOP, 1);
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of("a"), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(DOCKED);
        assertThat(state.active(Slot.RIGHT_TOP)).hasValue("a");
    }

    @Test void aWorkspaceDropInTheDetachedWindowsOwnSlotDocksItThere() {
        state.setMode("a", FLOAT);
        state.show("a");
        changes.clear();
        state.moveAndShow("a", Slot.LEFT_TOP, 0);
        assertThat(changes).containsExactly(change(true, true, List.of(), List.of(), List.of("a")));
        assertThat(state.mode("a")).isEqualTo(DOCKED);
        assertThat(state.active(Slot.LEFT_TOP)).hasValue("a");
    }

    @Test void dockedMovesBetweenSlotsArePlacements() {
        state.move("a", Slot.RIGHT_TOP, 0);
        assertThat(changes).containsExactly(change(false, true, List.of(), List.of(), List.of("a")));
        changes.clear();
        state.move("c", Slot.RIGHT_TOP, 0);
        assertThat(changes).as("a reorder within a slot places nothing")
            .containsExactly(change(false, true, List.of(), List.of(), List.of()));
    }

    @Test void aShownDockedMoveAndAShowingDropReportThePlacement() {
        state.show("a"); state.show("c");
        changes.clear();
        state.move("a", Slot.RIGHT_TOP, 0);
        state.moveAndShow("c", Slot.LEFT_TOP, 0);
        assertThat(changes).containsExactly(
            change(true, true, List.of("c"), List.of(), List.of("a")),
            change(true, true, List.of(), List.of("c"), List.of("c")));
    }

    @Test void removeForgetsModeAndBounds() {
        state.detach("a", FLOAT, new Rectangle(1, 2, 30, 40));
        changes.clear();
        state.remove("a");
        assertThat(changes).containsExactly(change(true, true, List.of("a"), List.of(), List.of()));
        assertThat(state.mode("a")).isEqualTo(DOCKED);
        assertThat(state.bounds("a")).isEmpty();
        assertThat(state.detachedVisible()).isEmpty();
        assertThat(state.visible("a")).isFalse();
        state.add("a", Slot.LEFT_TOP);
        assertThat(state.mode("a")).as("a window added again starts docked").isEqualTo(DOCKED);
        assertThat(state.visible("a")).isFalse();
    }

    @Test void removingAHiddenDetachedWindowReportsNoVisibilityChange() {
        state.setMode("a", WINDOW);
        changes.clear();
        state.remove("a");
        assertThat(changes).containsExactly(change(false, true, List.of(), List.of(), List.of()));
        assertThat(state.mode("a")).isEqualTo(DOCKED);
    }

    @Test void copyCarriesModesBoundsAndShownDetached() {
        state.show("c");
        state.detach("a", FLOAT, new Rectangle(1, 2, 30, 40));
        state.setMode("b", WINDOW);
        state.setBounds("b", new Rectangle(5, 6, 70, 80));
        DockState copy = state.copy();
        assertThat(copy.mode("a")).isEqualTo(FLOAT);
        assertThat(copy.mode("b")).isEqualTo(WINDOW);
        assertThat(copy.mode("c")).isEqualTo(DOCKED);
        assertThat(copy.bounds("a")).hasValue(new Rectangle(1, 2, 30, 40));
        assertThat(copy.bounds("b")).hasValue(new Rectangle(5, 6, 70, 80));
        assertThat(copy.visible("a")).isTrue();
        assertThat(copy.visible("b")).isFalse();
        assertThat(copy.detachedVisible()).containsExactly("a");
        assertThat(copy.active(Slot.RIGHT_TOP)).hasValue("c");
        changes.clear();
        copy.hide("a");
        copy.setMode("b", DOCKED);
        copy.setBounds("a", new Rectangle(9, 9, 9, 9));
        assertThat(state.visible("a")).isTrue();
        assertThat(state.mode("b")).isEqualTo(WINDOW);
        assertThat(state.bounds("a")).hasValue(new Rectangle(1, 2, 30, 40));
        assertThat(changes).as("a copy has no listeners").isEmpty();
    }

    @Test void showingASideCountsDockedWindowsOnly() {
        state.setMode("a", FLOAT);
        state.show("a");
        assertThat(state.showing(Side.LEFT)).isFalse();
        state.show("b");
        assertThat(state.showing(Side.LEFT)).isTrue();
        state.hide("b");
        assertThat(state.showing(Side.LEFT)).isFalse();
    }

    @Test void detachedVisibleFollowsTheSlotsThenTheirStripeOrder() {
        state.add("d", Slot.LEFT_BOTTOM);
        state.add("e", Slot.BOTTOM_RIGHT);
        for (String id : List.of("e", "c", "d", "b", "a")) state.detach(id, WINDOW, new Rectangle(0, 0, 100, 100));
        assertThat(state.detachedVisible()).containsExactly("a", "b", "d", "c", "e");
        state.move("a", Slot.LEFT_TOP, 1);
        assertThat(state.detachedVisible()).containsExactly("b", "a", "d", "c", "e");
        state.hide("d");
        assertThat(state.detachedVisible()).containsExactly("b", "a", "c", "e");
    }
}
