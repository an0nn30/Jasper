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
            .as("alone in the bottom area it would fill it, but the guide shows the slot's half").hasValue(new Rectangle(503, 400, 497, 200));
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

    /** A hidden window "m" docked on another side than the target's, so the target and its partner both start empty. */
    private DockState emptyPair(Slot target) {
        var fresh = new DockState();
        fresh.add("m", target.side() == Side.LEFT ? Slot.RIGHT_TOP : Slot.LEFT_TOP);
        return fresh;
    }

    /** The target's rectangle in the layout that really shows the window there with a window in its partner. */
    private Rectangle splitHalf(Slot target) {
        var split = new DockState();
        split.add("m", target); split.add("p", target.partner());
        split.show("m"); split.show("p");
        return DockGeometry.compute(BOUNDS, split, Metrics.DEFAULT).slot(target).orElseThrow();
    }

    private static final List<Slot> SECOND_SLOTS = List.of(Slot.LEFT_BOTTOM, Slot.RIGHT_BOTTOM, Slot.BOTTOM_RIGHT);

    @Test void aSecondSlotsGuideShowsItsHalfEvenWhenItsPartnerIsEmpty() {
        for (Slot target : SECOND_SLOTS) {
            Rectangle guide = DockGeometry.landing(BOUNDS, emptyPair(target), "m", target, Metrics.DEFAULT).orElseThrow();
            assertThat(guide).as(target + " previews its half, as with a window in its partner").isEqualTo(splitHalf(target));
            Rectangle full = DockGeometry.compute(BOUNDS, onlyShowing(target), Metrics.DEFAULT).slot(target).orElseThrow();
            assertThat(full).as("where the window really lands: the whole side or bottom area").isNotEqualTo(guide);
            if (target.side() == Side.BOTTOM) {
                assertThat(guide.width).as(target + " is the right half of the bottom area").isLessThan(full.width);
                assertThat(guide.x).isGreaterThan(full.x);
                assertThat(guide.height).isEqualTo(full.height);
            } else {
                assertThat(guide.height).as(target + " is the lower half of its side").isLessThan(full.height);
                assertThat(guide.y).isGreaterThan(full.y);
                assertThat(guide.width).isEqualTo(full.width);
            }
        }
        assertThat(DockGeometry.landing(BOUNDS, emptyPair(Slot.LEFT_BOTTOM), "m", Slot.LEFT_BOTTOM, Metrics.DEFAULT))
            .hasValue(new Rectangle(0, 303, 260, 297));
        assertThat(DockGeometry.landing(BOUNDS, emptyPair(Slot.RIGHT_BOTTOM), "m", Slot.RIGHT_BOTTOM, Metrics.DEFAULT))
            .hasValue(new Rectangle(740, 303, 260, 297));
        assertThat(DockGeometry.landing(BOUNDS, emptyPair(Slot.BOTTOM_RIGHT), "m", Slot.BOTTOM_RIGHT, Metrics.DEFAULT))
            .hasValue(new Rectangle(503, 400, 497, 200));
    }

    /** The layout where the window shows alone in the slot: what a drop there really gives. */
    private static DockState onlyShowing(Slot slot) {
        var alone = new DockState();
        alone.add("m", slot);
        alone.show("m");
        return alone;
    }

    @Test void aSecondSlotsGuideShowsItsHalfWhenItsPartnerOnlyHoldsAHiddenWindow() {
        for (Slot target : SECOND_SLOTS) {
            var hiddenPartner = emptyPair(target);
            hiddenPartner.add("h", target.partner());
            assertThat(DockGeometry.landing(BOUNDS, hiddenPartner, "m", target, Metrics.DEFAULT))
                .as(target + " with a hidden window in its partner").hasValue(splitHalf(target));
            assertThat(hiddenPartner.visible("h")).isFalse();
        }
    }

    @Test void movingThePartnersOnlyWindowIntoASecondSlotStillGuidesItsHalf() {
        for (Slot target : SECOND_SLOTS) {
            var alone = new DockState();
            alone.add("s", target.partner());
            alone.show("s");
            assertThat(DockGeometry.compute(BOUNDS, alone, Metrics.DEFAULT).slot(target.partner()).orElseThrow())
                .as("alone in " + target.partner() + " it fills the side or the bottom area").isNotEqualTo(splitHalf(target.partner()));
            assertThat(DockGeometry.landing(BOUNDS, alone, "s", target, Metrics.DEFAULT))
                .as("moved to " + target + " the partner is left empty, and the guide still shows the half").hasValue(splitHalf(target));
        }
    }

    @Test void aFirstSlotsGuideStaysTheWholeSideOrBottomAreaWhenItsPartnerIsEmpty() {
        for (Slot target : List.of(Slot.LEFT_TOP, Slot.RIGHT_TOP, Slot.BOTTOM_LEFT)) {
            assertThat(DockGeometry.landing(BOUNDS, emptyPair(target), "m", target, Metrics.DEFAULT))
                .as(target + " alone is the whole side or bottom area")
                .isEqualTo(DockGeometry.compute(BOUNDS, onlyShowing(target), Metrics.DEFAULT).slot(target));
        }
        assertThat(DockGeometry.landing(BOUNDS, emptyPair(Slot.LEFT_TOP), "m", Slot.LEFT_TOP, Metrics.DEFAULT))
            .hasValue(new Rectangle(0, 0, 260, 600));
        assertThat(DockGeometry.landing(BOUNDS, emptyPair(Slot.BOTTOM_LEFT), "m", Slot.BOTTOM_LEFT, Metrics.DEFAULT))
            .hasValue(new Rectangle(0, 400, 1000, 200));
    }

    @Test void theGuideForASecondSlotLeavesTheStateItWasGivenAsItWas() {
        for (Slot target : SECOND_SLOTS) {
            var before = emptyPair(target);
            before.add("h", target.partner());
            before.add("k", target);
            before.show("k");
            before.resize(Side.LEFT, 300);
            before.setRatio(Side.BOTTOM, 0.3);
            before.addListener(changes::add);
            var untouched = before.copy();
            DockGeometry.landing(BOUNDS, before, "m", target, Metrics.DEFAULT);
            DockGeometry.landing(BOUNDS, before, "k", target.partner(), Metrics.DEFAULT);
            assertThat(changes).as("no listener hears the what-if").isEmpty();
            for (Slot slot : Slot.values()) {
                assertThat(before.ids(slot)).as(target + " " + slot).isEqualTo(untouched.ids(slot));
                assertThat(before.active(slot)).isEqualTo(untouched.active(slot));
            }
            assertThat(before.ids(target.partner())).as("the guide's stand-in window is not docked").containsExactly("h");
            assertThat(before.active(target.partner())).isEmpty();
            assertThat(before.ids(target)).containsExactly("k");
            assertThat(before.active(target)).hasValue("k");
            assertThat(before.slot("m")).isPresent();
            assertThat(before.visible("m")).isFalse();
            assertThat(before.size(Side.LEFT)).isEqualTo(300);
            assertThat(before.ratio(Side.BOTTOM)).isEqualTo(0.3);
        }
    }
}
