package dev.jasper.app.layout;

import static dev.jasper.app.layout.LayoutFixtures.child;
import static dev.jasper.app.layout.LayoutFixtures.group;
import static dev.jasper.app.layout.LayoutFixtures.groupId;
import static dev.jasper.app.layout.LayoutFixtures.h;
import static dev.jasper.app.layout.LayoutFixtures.layout;
import static dev.jasper.app.layout.LayoutFixtures.tab;
import static dev.jasper.app.layout.LayoutFixtures.v;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.entry;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 7.3: splits, new groups, sizes, maximize, regions and neighbours; SplitTreeTest cases, ported. */
class LayoutGeometryTest {
    /** The group {@code split} made for tab {@code n}. */
    static Group fresh(Layout split, int n) {
        return new Group(split.groupOf(tab(n)).orElseThrow(), List.of(tab(n)), Optional.of(tab(n)));
    }

    @Test void splitPutsTheTabInANewGroupOnTheSideTakingHalfTheSpace() {
        Layout one = layout(group(1), 1);
        for (Side side : Side.values()) {
            Layout split = one.split(tab(2), groupId(1), side);
            List<Child> children = side.leading()
                ? List.of(child(fresh(split, 2), 0.5), child(group(1), 0.5))
                : List.of(child(group(1), 0.5), child(fresh(split, 2), 0.5));
            assertThat(split.root()).as(side.name()).isEqualTo(new Split(side.orientation(), children));
            assertThat(split.activeGroup()).isEqualTo(split.groupOf(tab(2)).orElseThrow()).isNotEqualTo(groupId(1));
            assertThat(split.activeTab()).contains(tab(2));
        }
    }

    @Test void aParentOfTheSameOrientationGainsASibling() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout right = pair.split(tab(3), groupId(1), Side.RIGHT);
        assertThat(right.root()).isEqualTo(h(child(group(1), 0.25), child(fresh(right, 3), 0.25), child(group(2), 0.5)));
        Layout left = pair.split(tab(3), groupId(2), Side.LEFT);
        assertThat(left.root()).isEqualTo(h(child(group(1), 0.5), child(fresh(left, 3), 0.25), child(group(2), 0.25)));
    }

    @Test void aParentOfTheOtherOrientationNestsANewSplit() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout down = pair.split(tab(3), groupId(2), Side.DOWN);
        assertThat(down.root()).isEqualTo(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(fresh(down, 3), 0.5)), 0.5)));
    }

    @Test void splittingRightOrDownThenClosingRestoresTheSizes() {
        Layout pair = layout(h(child(group(1), 0.25), child(group(2), 0.75)), 2);
        for (Side side : List.of(Side.RIGHT, Side.DOWN)) {
            Layout split = pair.split(tab(9), groupId(2), side);
            assertThat(split.remove(tab(9))).as(side.name()).isEqualTo(pair);
        }
    }

    @Test void aSplitKeepsTheOtherWeightsAndClearsTheMaximize() {
        // SplitTreeTest.splitRebuildsOnlyTheFocusedLeafAndRetainsBranchRatiosThroughZoom
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1)
            .resize(List.of(), List.of(0.73, 0.27)).toggleMaximize(groupId(2));
        Layout split = pair.split(tab(3), groupId(2), Side.DOWN);
        assertThat(split.root()).isEqualTo(h(child(group(1), 0.73), child(v(child(group(2), 0.5), child(fresh(split, 3), 0.5)), 0.27)));
        assertThat(split.maximized()).isEmpty();
        assertThat(split.activeTab()).contains(tab(3));
    }

    @Test void splitRefusesAKnownTabAnUnknownGroupAndTheEmptyWorkspace() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        assertThatIllegalArgumentException().isThrownBy(() -> pair.split(tab(2), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.split(tab(9), groupId(9), Side.RIGHT));
        Layout empty = Layout.empty();
        assertThatIllegalArgumentException().as("an empty group beside a new one")
            .isThrownBy(() -> empty.split(tab(1), empty.activeGroup(), Side.RIGHT));
        assertThatNullPointerException().isThrownBy(() -> pair.split(tab(9), groupId(1), null));
    }

    /** Plan 8c's pane moves: a tab's pane goes beside another pane, its own pane's space going first. */
    @Test void moveToNewGroupMovesATabsPaneBesideAnotherPane() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2, 3), 0.5)), 1);
        Layout below = pair.moveToNewGroup(tab(1), groupId(2), Side.DOWN);
        assertThat(below.root()).as("its own pane goes; the other splits").isEqualTo(v(child(group(2, 3), 0.5), child(fresh(below, 1), 0.5)));
        assertThat(below.activeGroup()).isEqualTo(below.groupOf(tab(1)).orElseThrow());
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        Layout left = three.moveToNewGroup(tab(3), groupId(1), Side.LEFT);
        assertThat(left.root()).as("its emptied pane's space goes first")
            .isEqualTo(h(child(fresh(left, 3), 0.125), child(group(1), 0.125), child(group(2), 0.75)));
        assertThatIllegalArgumentException().as("beside its own pane").isThrownBy(() -> pair.moveToNewGroup(tab(3), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(9), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.moveToNewGroup(tab(1), groupId(9), Side.RIGHT));
    }

    /**
     * Pane flexibility spec 5 and workspace plan 9a Rulings 10: another tab's whole layout goes beside a pane as one nested
     * split, which takes half the pane's space as a split does, keeping its arrangement and sizes; a split of the side's
     * orientation joins its parent's, its shares kept. Its active pane is active and first in the order of use, and a
     * maximize ends.
     */
    @Test void insertBesidePutsAnotherTabsPanesBesideAPaneAsOneNestedSplit() {
        Layout target = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1).toggleMaximize(groupId(1));
        Layout moved = layout(v(child(group(3), 0.75), child(group(4), 0.25)), 4);
        Layout joined = target.insertBeside(moved, groupId(2), Side.RIGHT);
        assertThat(joined.root()).isEqualTo(h(child(group(1), 0.5), child(group(2), 0.25),
            child(v(child(group(3), 0.75), child(group(4), 0.25)), 0.25)));
        assertThat(joined.activeGroup()).as("the moved tab's active pane").isEqualTo(groupId(4));
        assertThat(joined.maximized()).as("a maximize ends").isEmpty();
        assertThat(joined.recent()).startsWith(groupId(4), groupId(3));
        Layout flat = target.insertBeside(layout(h(child(group(3), 0.75), child(group(4), 0.25)), 3), groupId(2), Side.RIGHT);
        assertThat(flat.root()).as("a split of the side's orientation joins its parent's")
            .isEqualTo(h(child(group(1), 0.5), child(group(2), 0.25), child(group(3), 0.1875), child(group(4), 0.0625)));
        Layout left = layout(group(1), 1).insertBeside(moved, groupId(1), Side.LEFT);
        assertThat(left.root()).isEqualTo(h(child(v(child(group(3), 0.75), child(group(4), 0.25)), 0.5), child(group(1), 0.5)));
        Layout single = layout(group(1), 1).insertBeside(layout(group(5), 5), groupId(1), Side.DOWN);
        assertThat(single.root()).as("a tab of one pane, as a split puts it").isEqualTo(v(child(group(1), 0.5), child(group(5), 0.5)));
        assertThatIllegalArgumentException().as("an empty layout").isThrownBy(() -> target.insertBeside(Layout.empty(), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().as("a pane in both").isThrownBy(() -> target.insertBeside(layout(group(2, 9), 2), groupId(1), Side.RIGHT));
        assertThatIllegalArgumentException().as("a tab in both").isThrownBy(() -> target.insertBeside(layout(group(9, 1), 9), groupId(2), Side.RIGHT));
        assertThatIllegalArgumentException().as("an unknown pane").isThrownBy(() -> target.insertBeside(moved, groupId(9), Side.RIGHT));
        Layout empty = Layout.empty();
        assertThatIllegalArgumentException().as("the empty workspace").isThrownBy(() -> empty.insertBeside(moved, empty.activeGroup(), Side.RIGHT));
        assertThatNullPointerException().isThrownBy(() -> target.insertBeside(moved, groupId(2), null));
    }

    @Test void resizeAddressesASplitByItsPathAndNormalizes() {
        // SplitTreeTest.ratiosAreClampedAndNonFiniteRatiosAreRejected: no clamp here (plan Rulings 4), the rest rejected
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.5)), 2);
        assertThat(nested.resize(List.of(), List.of(1.0, 3.0)).root())
            .isEqualTo(h(child(group(1), 0.25), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.75)));
        assertThat(nested.resize(List.of(1), List.of(0.75, 0.25)).root())
            .isEqualTo(h(child(group(1), 0.5), child(v(child(group(2), 0.75), child(group(3), 0.25)), 0.5)));
        assertThat(nested.resize(List.of(1), List.of(3.0, 1.0)).activeGroup()).isEqualTo(groupId(2));
        for (List<Integer> path : List.of(List.of(0), List.of(2), List.of(-1), List.of(1, 0)))
            assertThatIllegalArgumentException().as("path %s", path).isThrownBy(() -> nested.resize(path, List.of(0.5, 0.5)));
        assertThatIllegalArgumentException().as("one weight per child").isThrownBy(() -> nested.resize(List.of(), List.of(1.0)));
        for (double bad : new double[] {0, -1, Double.NaN, Double.POSITIVE_INFINITY})
            assertThatIllegalArgumentException().as("weight %s", bad)
                .isThrownBy(() -> nested.resize(List.of(), List.of(bad, 1.0))).withMessageContaining("weight");
        assertThatIllegalArgumentException().as("one group has no split")
            .isThrownBy(() -> layout(group(1), 1).resize(List.of(), List.of(1.0, 1.0)));
    }

    @Test void toggleMaximizeMaximizesAndActivatesThenRestoresAndUnmaximizeClears() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout max = pair.toggleMaximize(groupId(2));
        assertThat(max.maximized()).contains(groupId(2));
        assertThat(max.activeGroup()).isEqualTo(groupId(2));
        assertThat(max.toggleMaximize(groupId(2)).maximized()).isEmpty();
        assertThat(max.toggleMaximize(groupId(2)).activeGroup()).isEqualTo(groupId(2));
        assertThat(max.toggleMaximize(groupId(1)).maximized()).as("another group takes it").contains(groupId(1));
        Layout one = layout(group(1), 1);
        assertThat(one.toggleMaximize(groupId(1))).as("one group: unchanged").isEqualTo(one);
        assertThat(one.toggleMaximize(groupId(1)).maximized()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> pair.toggleMaximize(groupId(9)));
        assertThat(max.unmaximize().maximized()).isEmpty();
        assertThat(max.unmaximize().activeGroup()).isEqualTo(groupId(2));
        assertThat(max.unmaximize().root()).isEqualTo(max.root());
        assertThat(pair.unmaximize()).as("nothing maximized: this layout").isSameAs(pair);
    }

    @Test void newGroupsClearTheMaximizeAndResizingKeepsIt() {
        Layout max = layout(h(child(group(1), 0.5), child(v(child(group(2, 3), 0.5), child(group(4, 2), 0.5)), 0.5)), 1).toggleMaximize(groupId(2));
        assertThat(max.split(tab(9), groupId(2), Side.RIGHT).maximized()).isEmpty();
        assertThat(max.moveToNewGroup(tab(2), groupId(1), Side.DOWN).maximized()).isEmpty();
        assertThat(max.resize(List.of(), List.of(1.0, 3.0)).maximized()).contains(groupId(2));
    }

    @Test void boundsTileTheUnitSquareInTreeOrder() {
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.75), child(group(3), 0.25)), 0.5)), 1);
        assertThat(nested.bounds()).containsExactly(
            entry(groupId(1), new Region(0, 0, 0.5, 1)),
            entry(groupId(2), new Region(0.5, 0, 0.5, 0.75)),
            entry(groupId(3), new Region(0.5, 0.75, 0.5, 0.25)));
        assertThat(layout(group(1), 1).bounds()).containsExactly(entry(groupId(1), new Region(0, 0, 1, 1)));
        assertThat(nested.toggleMaximize(groupId(2)).bounds()).as("a maximized group keeps its place").isEqualTo(nested.bounds());
    }

    @Test void directionalNavigationRequiresOverlapAndUsesGeometryInsteadOfTreeOrder() {
        // SplitTreeTest.directionalNavigationRequiresOverlapAndUsesGeometryInsteadOfTraversalOrder
        Layout grid = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(group(3), 0.5)), 0.5)), 1);
        assertThat(grid.neighbour(groupId(1), Side.RIGHT)).as("a tie across goes to the first in tree order").contains(groupId(2));
        assertThat(grid.neighbour(groupId(2), Side.DOWN)).contains(groupId(3));
        assertThat(grid.neighbour(groupId(3), Side.LEFT)).contains(groupId(1));
        assertThat(grid.neighbour(groupId(3), Side.UP)).contains(groupId(2));
        assertThat(grid.neighbour(groupId(1), Side.DOWN)).isEmpty();
    }

    @Test void asymmetricGeometryChoosesTheGroupThatOverlaps() {
        // SplitTreeTest.asymmetricGeometryChoosesTheNearestPerpendicularCenter
        Layout grid = layout(h(child(v(child(group(1), 0.8), child(group(2), 0.2)), 0.65), child(v(child(group(3), 0.3), child(group(4), 0.7)), 0.35)), 2);
        assertThat(grid.neighbour(groupId(2), Side.RIGHT)).contains(groupId(4));
    }

    @Test void theNearestGroupAheadWinsThenTheNearestCentreAcross() {
        Layout row = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        assertThat(row.neighbour(groupId(1), Side.RIGHT)).contains(groupId(2));
        Layout grid = layout(v(child(h(child(group(1), 0.5), child(group(2), 0.5)), 0.5), child(h(child(group(3), 0.625), child(group(4), 0.375)), 0.5)), 2);
        assertThat(grid.neighbour(groupId(2), Side.DOWN)).as("both overlap; group 4's centre is nearer").contains(groupId(4));
    }

    @Test void navigationDoesNotWrapAndMovesTheMaximize() {
        // SplitTreeTest.navigationDoesNotWrapAndStillChangesTheVisiblePaneWhileZoomed
        Layout max = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1).toggleMaximize(groupId(1));
        Layout moved = max.activate(max.neighbour(groupId(1), Side.RIGHT).orElseThrow());
        assertThat(moved.maximized()).contains(groupId(2));
        assertThat(moved.neighbour(groupId(2), Side.RIGHT)).isEmpty();
        assertThat(moved.neighbour(groupId(2), Side.LEFT)).contains(groupId(1));
        assertThatIllegalArgumentException().isThrownBy(() -> max.neighbour(groupId(9), Side.LEFT));
        assertThatNullPointerException().isThrownBy(() -> max.neighbour(groupId(1), null));
    }

    @Test void theSplitTreeScenarioKeepsAValidActiveGroup() {
        // SplitTreeTest.splitNavigateCloseAndZoomPreserveAValidFocus and closingTheLastPaneLeavesAnEmptyUnzoomedTree
        Layout tree = Layout.of(tab(1));
        tree = tree.split(tab(2), tree.activeGroup(), Side.RIGHT);
        tree = tree.split(tab(3), tree.activeGroup(), Side.DOWN);
        tree = tree.activate(tree.neighbour(tree.activeGroup(), Side.UP).orElseThrow());
        assertThat(tree.activeTab()).contains(tab(2));
        tree = tree.remove(tab(2));
        assertThat(tree.tabs()).containsExactly(tab(1), tab(3));
        assertThat(tree.activeTab()).contains(tab(3));
        tree = tree.toggleMaximize(tree.activeGroup());
        assertThat(tree.maximized()).contains(tree.activeGroup());
        tree = tree.remove(tab(3));
        assertThat(tree.tabs()).containsExactly(tab(1));
        assertThat(tree.activeTab()).contains(tab(1));
        assertThat(tree.maximized()).isEmpty();
        tree = tree.remove(tab(1));
        assertThat(tree.isEmpty()).isTrue();
        assertThat(tree.maximized()).isEmpty();
    }
}
