package dev.jasper.app.layout;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.within;

/** Parent tabs spec 4, 5 and 11: where a pane sits, the origin a pane window keeps, and Bring Back's layout. */
class OriginTest {
    private static final ParentTabId PARENT = ParentTabId.random();
    private static final TabId A = TabId.random(), B = TabId.random(), C = TabId.random(), X = TabId.random(), Y = TabId.random();

    /** A | B | C, taking a half, a quarter and a quarter. */
    private static Layout row() {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), Side.RIGHT);
        return layout.split(C, layout.activeGroup(), Side.RIGHT);
    }

    private static GroupId of(Layout layout, TabId tab) { return layout.groupOf(tab).orElseThrow(); }

    /** A, B and C in a line along {@code side}, with the given weights. */
    private static Layout line(Side side, double... weights) {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), side);
        layout = layout.split(C, layout.activeGroup(), side);
        return layout.resize(List.of(), Arrays.stream(weights).boxed().toList());
    }

    /** The same groups, tabs and orientations, and weights within {@code 1e-9}. */
    private static void assertSameTree(Node expected, Node actual) {
        if (expected instanceof Split split) {
            assertThat(actual).isInstanceOf(Split.class);
            Split other = (Split) actual;
            assertThat(other.orientation()).isEqualTo(split.orientation());
            assertThat(other.children()).hasSameSizeAs(split.children());
            for (int i = 0; i < split.children().size(); i++) {
                assertThat(other.children().get(i).weight()).isCloseTo(split.children().get(i).weight(), within(1e-9));
                assertSameTree(split.children().get(i).node(), other.children().get(i).node());
            }
        } else {
            assertThat(actual).isEqualTo(expected);
        }
    }

    static Stream<Arguments> everyPaneOfALine() {
        Stream.Builder<Arguments> cases = Stream.builder();
        for (Side side : new Side[] {Side.RIGHT, Side.DOWN})
            for (double[] weights : new double[][] {{0.5, 0.25, 0.25}, {0.2, 0.3, 0.5}, {0.6, 0.1, 0.3}})
                for (int index = 0; index < 3; index++) cases.add(Arguments.of(side, weights, index));
        return cases.build();
    }

    @Test void aPaneSitsBesideTheSiblingItsSpaceGoesToWithItsShareOfTheTwo() {
        Layout layout = row();
        assertThat(((Split) layout.root()).children()).extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
        assertThat(layout.origin(PARENT, of(layout, C))).isEqualTo(new Origin(PARENT, of(layout, B), Side.RIGHT, 0.5));
        Origin middle = layout.origin(PARENT, of(layout, B));
        assertThat(middle.pane()).isEqualTo(of(layout, A));
        assertThat(middle.side()).isEqualTo(Side.RIGHT);
        assertThat(middle.share()).isCloseTo(1.0 / 3, within(1e-12));
        Origin first = layout.origin(PARENT, of(layout, A));
        assertThat(first.pane()).as("the first gives its space to the one after it").isEqualTo(of(layout, B));
        assertThat(first.side()).isEqualTo(Side.LEFT);
        assertThat(first.share()).isCloseTo(2.0 / 3, within(1e-12));
        assertThat(first.parentTab()).isEqualTo(PARENT);
    }

    @Test void aPaneInAColumnSitsAboveOrBelowAndTheOneBesideIsNearestItsMiddle() {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), Side.RIGHT);
        layout = layout.split(C, layout.activeGroup(), Side.DOWN);
        // A | (B over C)
        assertThat(layout.origin(PARENT, of(layout, C))).isEqualTo(new Origin(PARENT, of(layout, B), Side.DOWN, 0.5));
        assertThat(layout.origin(PARENT, of(layout, B))).isEqualTo(new Origin(PARENT, of(layout, C), Side.UP, 0.5));
        assertThat(layout.origin(PARENT, of(layout, A))).as("both touch its edge: the first in tree order")
            .isEqualTo(new Origin(PARENT, of(layout, B), Side.LEFT, 0.5));
    }

    @Test void aLayoutsOnlyPaneSitsOnTheRightOfItself() {
        Layout layout = Layout.of(A);
        assertThat(layout.origin(PARENT, layout.activeGroup())).isEqualTo(new Origin(PARENT, layout.activeGroup(), Side.RIGHT, 0.5));
        assertThatIllegalArgumentException().isThrownBy(() -> layout.origin(PARENT, GroupId.random())).withMessageContaining("not in the layout");
    }

    @ParameterizedTest(name = "{0} {1} pane {2}") @MethodSource("everyPaneOfALine")
    void leavingAndBringingBackRestoresALineWhateverItsWeights(Side side, double[] weights, int index) {
        Layout layout = line(side, weights);
        TabId tab = layout.tabs().get(index);
        GroupId group = of(layout, tab);
        Origin origin = layout.origin(PARENT, group);
        Layout panes = new Layout(layout.group(group).orElseThrow(), group, Optional.empty());
        Layout back = layout.remove(tab).graft(panes, origin.pane(), origin.side(), origin.share());
        assertSameTree(layout.root(), back.root());
        assertThat(back.activeGroup()).isEqualTo(group);
        assertThat(back.tabs()).containsExactlyInAnyOrder(A, B, C);
    }

    @Test void aPaneBesideASubtreeComesBackBesideTheGroupOfItNearestItsEdge() {
        Layout layout = Layout.of(A);
        GroupId a = layout.activeGroup();
        layout = layout.split(B, a, Side.RIGHT).split(C, a, Side.DOWN);
        // (A over C) | B
        Split before = (Split) layout.root();
        assertThat(before.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(before.children().getFirst().node()).isInstanceOf(Split.class);
        Origin origin = layout.origin(PARENT, of(layout, B));
        assertThat(origin).as("the first group of the sibling's subtree across its edge, tied for nearest")
            .isEqualTo(new Origin(PARENT, a, Side.RIGHT, 0.5));
        Layout panes = Layout.of(B);
        Layout back = layout.remove(B).graft(panes, origin.pane(), origin.side(), origin.share());
        Split column = (Split) back.root();
        assertThat(column.orientation()).as("the column is not restored: B sits beside A alone").isEqualTo(Orientation.VERTICAL);
        assertThat(column.children()).extracting(Child::weight).containsExactly(0.5, 0.5);
        Split top = (Split) column.children().getFirst().node();
        assertThat(top.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(top.children()).extracting(Child::node).containsExactly(layout.group(a).orElseThrow(), panes.root());
        assertThat(top.children()).extracting(Child::weight).containsExactly(0.5, 0.5);
        assertThat(column.children().getLast().node()).isEqualTo(layout.group(of(layout, C)).orElseThrow());
    }

    @Test void graftingAtTheOriginOfAPaneBesideAPaneRestoresTheSplitThePaneLeft() {
        Layout layout = row();
        Origin origin = layout.origin(PARENT, of(layout, C));
        Layout rest = layout.remove(C);
        assertThat(((Split) rest.root()).children()).extracting(Child::weight).containsExactly(0.5, 0.5);
        Layout panes = Layout.of(C);
        Layout back = rest.graft(panes, origin.pane(), origin.side(), origin.share());
        var children = ((Split) back.root()).children();
        assertThat(children).extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
        assertThat(children).extracting(Child::node).containsExactly(rest.group(of(rest, A)).orElseThrow(),
            rest.group(of(rest, B)).orElseThrow(), panes.root());
        assertThat(back.activeGroup()).as("the panes come back active").isEqualTo(panes.activeGroup());
        assertThat(back.activeTab()).contains(C);
    }

    @Test void graftingNestsThePanesOnTheGivenSide() {
        Layout panes = Layout.of(X);
        panes = panes.split(Y, panes.activeGroup(), Side.DOWN);
        Layout layout = Layout.of(A);
        GroupId a = layout.activeGroup();
        Layout right = layout.graft(panes, a, Side.RIGHT, 0.25);
        Split split = (Split) right.root();
        assertThat(split.orientation()).isEqualTo(Orientation.HORIZONTAL);
        assertThat(split.children()).extracting(Child::node).containsExactly(layout.root(), panes.root());
        assertThat(split.children()).extracting(Child::weight).containsExactly(0.75, 0.25);
        assertThat(right.activeGroup()).isEqualTo(panes.activeGroup());
        Layout left = layout.graft(panes, a, Side.LEFT, 0.5);
        assertThat(((Split) left.root()).children()).extracting(Child::node).as("leading: the panes first")
            .containsExactly(panes.root(), layout.root());
        Layout below = layout.graft(panes, a, Side.DOWN, 0.5);
        assertThat(((Split) below.root()).children()).as("panes of the split's orientation join it (spec 7.2)")
            .extracting(Child::weight).containsExactly(0.5, 0.25, 0.25);
    }

    @Test void graftingEndsAMaximizeAndTheEmptyWorkspaceTakesThePanesWhole() {
        Layout maximized = row();
        GroupId a = of(maximized, A);
        maximized = maximized.open(Y, a, 1);
        maximized = maximized.toggleMaximize(of(maximized, C));
        assertThat(maximized.maximized()).isPresent();
        Layout panes = Layout.of(X);
        panes = panes.open(TabId.random(), panes.activeGroup(), 1).select(X);
        Layout back = maximized.graft(panes, a, Side.RIGHT, 0.5);
        assertThat(back.maximized()).as("spec 11: Bring Back ends a maximize").isEmpty();
        assertThat(back.activeGroup()).as("the panes' group is the active one").isEqualTo(panes.activeGroup());
        assertThat(back.activeTab()).as("showing the panes' active tab").isEqualTo(panes.activeTab()).contains(X);
        assertThat(back.group(a).orElseThrow().selected()).as("another group keeps its selected tab").contains(Y);
        Layout empty = Layout.empty();
        assertThat(empty.graft(panes, empty.activeGroup(), Side.RIGHT, 0.5)).isEqualTo(panes);
    }

    @Test void graftingDropsThePanesOwnMaximize() {
        Layout panes = Layout.of(X);
        panes = panes.split(Y, panes.activeGroup(), Side.DOWN);
        panes = panes.toggleMaximize(panes.activeGroup());
        assertThat(panes.maximized()).isPresent();
        Layout layout = Layout.of(A);
        assertThat(layout.graft(panes, layout.activeGroup(), Side.RIGHT, 0.5).maximized()).isEmpty();
        Layout empty = Layout.empty();
        assertThat(empty.graft(panes, empty.activeGroup(), Side.RIGHT, 0.5).maximized()).isEmpty();
    }

    @Test void anExtremeWeightLeavesTheSharesInsideTheSplit() {
        Layout layout = Layout.of(A);
        layout = layout.split(B, layout.activeGroup(), Side.RIGHT).resize(List.of(), List.of(1.0, 1e-17));
        Origin big = layout.origin(PARENT, of(layout, A));
        assertThat(big.pane()).isEqualTo(of(layout, B));
        assertThat(big.share()).as("a weight of nearly 1 is held to 0.99").isEqualTo(0.99);
        Origin small = layout.origin(PARENT, of(layout, B));
        assertThat(small.pane()).isEqualTo(of(layout, A));
        assertThat(small.share()).as("a weight of nearly 0 is held to 0.01").isEqualTo(0.01);
    }

    @Test void graftingRefusesWhatWouldBreakTheLayout() {
        Layout layout = Layout.of(A);
        GroupId a = layout.activeGroup();
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(A), a, Side.RIGHT, 0.5)).withMessageContaining("twice");
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.empty(), a, Side.RIGHT, 0.5)).withMessageContaining("no panes");
        assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(X), GroupId.random(), Side.RIGHT, 0.5))
            .withMessageContaining("not in the layout");
        for (double share : new double[] {0, 1, -0.5, Double.NaN})
            assertThatIllegalArgumentException().isThrownBy(() -> layout.graft(Layout.of(X), a, Side.RIGHT, share)).withMessageContaining("share");
    }

    @Test void anOriginNeedsEveryValueAndAShareBetweenZeroAndOne() {
        GroupId pane = GroupId.random();
        assertThatIllegalArgumentException().isThrownBy(() -> new Origin(PARENT, pane, Side.RIGHT, 1)).withMessageContaining("share");
        assertThatIllegalArgumentException().isThrownBy(() -> new Origin(PARENT, pane, Side.RIGHT, 0)).withMessageContaining("share");
        assertThatNullPointerException().isThrownBy(() -> new Origin(null, pane, Side.RIGHT, 0.5));
        Origin origin = new Origin(PARENT, pane, Side.UP, 0.25);
        var window = new PaneLayout(Layout.of(A), origin);
        assertThat(window.origin()).isEqualTo(origin);
        assertThat(window.tabs()).containsExactly(A);
        assertThatNullPointerException().isThrownBy(() -> new PaneLayout(Layout.of(A), null));
    }
}
