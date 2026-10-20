package dev.jasper.app.layout;

import org.junit.jupiter.api.Test;
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

    @Test void graftingAtTheOriginRestoresTheSplitThePaneLeft() {
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
        maximized = maximized.toggleMaximize(maximized.activeGroup());
        assertThat(maximized.maximized()).isPresent();
        Layout back = maximized.graft(Layout.of(X), of(maximized, A), Side.RIGHT, 0.5);
        assertThat(back.maximized()).as("spec 11: Bring Back ends a maximize").isEmpty();
        Layout empty = Layout.empty();
        Layout panes = Layout.of(X);
        assertThat(empty.graft(panes, empty.activeGroup(), Side.RIGHT, 0.5)).isEqualTo(panes);
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
