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

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

/** Spec 7.3: opening, selecting, activating, moving and removing tabs, the order of use and the maximize. */
class LayoutOperationsTest {
    /** Group 1 holds tabs 1 and 2 (1 shown), group 2 holds tab 3; side by side, group 2 active. */
    static Layout pair() {
        return layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 2);
    }

    @Test void openInsertsSelectsAndActivates() {
        Layout opened = pair().open(tab(9), groupId(1), 1);
        assertThat(opened.group(groupId(1))).contains(group(1, 9, 1, 9, 2));
        assertThat(opened.activeGroup()).isEqualTo(groupId(1));
        assertThat(opened.recent()).containsExactly(groupId(1), groupId(2));
        assertThat(pair().open(tab(9), groupId(2), 1).group(groupId(2))).contains(group(2, 9, 3, 9));
        assertThat(pair().open(tab(9), groupId(1), 0).tabs()).containsExactly(tab(9), tab(1), tab(2), tab(3));
    }

    @Test void openRejectsAKnownTabAnUnknownGroupAndAnIndexOutsideTheGroup() {
        Layout pair = pair();
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(3), groupId(1), 0)).withMessageContaining(tab(3).toString());
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(7), 0)).withMessageContaining(groupId(7).toString());
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(1), -1));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.open(tab(9), groupId(1), 3));
        assertThatNullPointerException().isThrownBy(() -> pair.open(null, groupId(1), 0));
    }

    @Test void openFillsTheEmptyWorkspacesGroup() {
        Layout empty = Layout.empty();
        Layout one = empty.open(tab(1), empty.activeGroup(), 0);
        assertThat(one.root()).isEqualTo(new Group(empty.activeGroup(), List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
    }

    @Test void selectShowsTheTabAndActivatesItsGroup() {
        Layout selected = pair().select(tab(2));
        assertThat(selected.group(groupId(1))).contains(group(1, 2, 1, 2));
        assertThat(selected.activeGroup()).isEqualTo(groupId(1));
        assertThat(selected.activeTab()).contains(tab(2));
        assertThatIllegalArgumentException().isThrownBy(() -> pair().select(tab(9))).withMessageContaining(tab(9).toString());
    }

    @Test void activateMovesTheGroupToTheFrontOfTheOrderOfUse() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1);
        assertThat(three.recent()).containsExactly(groupId(1), groupId(2), groupId(3));
        Layout used = three.activate(groupId(3)).activate(groupId(2));
        assertThat(used.recent()).containsExactly(groupId(2), groupId(3), groupId(1));
        assertThat(used.activeGroup()).isEqualTo(groupId(2));
        assertThat(used.root()).isEqualTo(three.root());
        assertThatIllegalArgumentException().isThrownBy(() -> three.activate(groupId(9)));
    }

    @Test void equalityIgnoresTheOrderOfUse() {
        Layout direct = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 3);
        Layout used = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(2)).activate(groupId(3));
        assertThat(used).isEqualTo(direct).hasSameHashCodeAs(direct);
        assertThat(used.recent()).containsExactly(groupId(3), groupId(2), groupId(1));
        assertThat(direct.recent()).containsExactly(groupId(3), groupId(1), groupId(2));
    }

    @Test void removingTheSelectedTabSelectsTheGroupsMostRecentlyShownTab() {
        Layout row = layout(h(child(group(1, 1, 1, 2, 3), 0.5), child(group(2, 4, 4), 0.5)), 1);
        Layout used = row.select(tab(3)).select(tab(1));
        assertThat(used.remove(tab(1)).group(groupId(1))).as("tab 3, not the neighbour").contains(group(1, 3, 2, 3));
        assertThat(used).as("the tabs' order of use is not part of equality").isEqualTo(row).hasSameHashCodeAs(row);
        assertThat(row.remove(tab(2)).group(groupId(1))).as("another tab: the selection stays").contains(group(1, 1, 1, 3));
        assertThat(row.remove(tab(2)).activeGroup()).isEqualTo(groupId(1));
        Layout moved = row.move(tab(4), groupId(1), 3).select(tab(2));
        assertThat(moved.remove(tab(2)).activeTab()).as("a tab moved in counts as shown").contains(tab(4));
    }

    @Test void tabsNeverShownAreReachedAsJTabbedPaneReachesThem() {
        Layout row = layout(h(child(group(1, 2, 1, 2, 3, 4), 0.5), child(group(2, 5, 5), 0.5)), 1);
        Layout closed = row.remove(tab(2));
        assertThat(closed.activeTab()).as("the tab that takes its place").contains(tab(3));
        closed = closed.remove(tab(3));
        assertThat(closed.activeTab()).contains(tab(4));
        closed = closed.remove(tab(4));
        assertThat(closed.activeTab()).as("the last: the one before it").contains(tab(1));
    }

    @Test void anEmptiedGroupGivesItsSpaceToTheSiblingBeforeItElseAfter() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.5), child(group(3), 0.25)), 1);
        assertThat(three.remove(tab(2)).root()).as("the sibling before").isEqualTo(h(child(group(1), 0.75), child(group(3), 0.25)));
        assertThat(three.remove(tab(1)).root()).as("the first: the sibling after").isEqualTo(h(child(group(2), 0.75), child(group(3), 0.25)));
    }

    @Test void aSplitLeftWithOneChildCollapsesAndNestingFlattensAgain() {
        Layout nested = layout(h(child(group(1), 0.5), child(v(child(group(2), 0.5), child(h(child(group(3), 0.5), child(group(4), 0.5)), 0.5)), 0.5)), 1);
        assertThat(nested.remove(tab(2)).root()).isEqualTo(h(child(group(1), 0.5), child(group(3), 0.25), child(group(4), 0.25)));
    }

    @Test void theActiveGroupAfterARemovalIsTheMostRecentlyUsedRemainingGroup() {
        Layout three = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 1)
            .activate(groupId(3)).activate(groupId(2));
        Layout afterTwo = three.remove(tab(2));
        assertThat(afterTwo.activeGroup()).as("not the first in the tree nor the sibling before").isEqualTo(groupId(3));
        assertThat(afterTwo.recent()).containsExactly(groupId(3), groupId(1));
        assertThat(afterTwo.remove(tab(3)).activeGroup()).isEqualTo(groupId(1));
        assertThat(three.activate(groupId(1)).remove(tab(3)).activeGroup()).as("the active group stays when another goes").isEqualTo(groupId(1));
    }

    @Test void removalDownToTheEmptyWorkspaceKeepsItsGroupForTheNextTab() {
        Layout pair = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1);
        Layout one = pair.remove(tab(1));
        assertThat(one.root()).as("the split collapses into the remaining group").isEqualTo(group(2));
        assertThat(one.activeGroup()).isEqualTo(groupId(2));
        Layout empty = one.remove(tab(2));
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.root()).isEqualTo(new Group(groupId(2), List.of(), Optional.empty()));
        assertThat(empty.activeGroup()).isEqualTo(groupId(2));
        assertThat(empty.tabs()).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> empty.remove(tab(2)));
        assertThat(empty.open(tab(5), groupId(2), 0).root()).as("the next tab goes into the same group").isEqualTo(group(2, 5, 5));
    }

    @Test void moveReordersWithinAGroupCountingWithoutTheMovedTab() {
        Layout row = layout(h(child(group(1, 1, 1, 2, 3), 0.5), child(group(2, 4, 4), 0.5)), 2);
        Layout moved = row.move(tab(1), groupId(1), 2);
        assertThat(moved.group(groupId(1))).contains(group(1, 1, 2, 3, 1));
        assertThat(moved.activeGroup()).isEqualTo(groupId(1));
        assertThat(row.move(tab(3), groupId(1), 0).group(groupId(1))).contains(group(1, 3, 3, 1, 2));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(1), groupId(1), 3));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(9), groupId(1), 0));
        assertThatIllegalArgumentException().isThrownBy(() -> row.move(tab(1), groupId(9), 0));
    }

    @Test void moveBetweenGroupsSelectsActivatesAndRemovesAnEmptiedSource() {
        Layout pair = layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3), 0.5)), 1);
        Layout across = pair.move(tab(1), groupId(2), 0);
        assertThat(across.root()).isEqualTo(h(child(group(1, 2, 2), 0.5), child(group(2, 1, 1, 3), 0.5)));
        assertThat(across.activeGroup()).isEqualTo(groupId(2));
        Layout emptied = pair.move(tab(3), groupId(1), 1);
        assertThat(emptied.root()).isEqualTo(group(1, 3, 1, 3, 2));
        assertThat(emptied.activeGroup()).isEqualTo(groupId(1));
        assertThatIllegalArgumentException().isThrownBy(() -> pair.move(tab(3), groupId(1), 3));
    }

    @Test void theMaximizeFollowsTheActiveGroupClearsOnMovesAndSurvivesClosedTabs() {
        Layout max = new Layout(h(child(group(1, 1, 1, 2), 0.5), child(group(2, 3, 3, 4), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(max.open(tab(9), groupId(2), 0).maximized()).as("a tab opens in it").contains(groupId(2));
        assertThat(max.open(tab(9), groupId(1), 0).maximized()).as("a tab opens in another group").contains(groupId(1));
        assertThat(max.select(tab(2)).maximized()).as("a tab of another group is selected").contains(groupId(1));
        assertThat(max.activate(groupId(1)).maximized()).as("another group is activated").contains(groupId(1));
        assertThat(max.move(tab(4), groupId(2), 0).maximized()).as("a reorder").contains(groupId(2));
        assertThat(max.move(tab(1), groupId(2), 0).maximized()).as("it gains a tab by a move").isEmpty();
        assertThat(max.move(tab(3), groupId(1), 0).maximized()).as("it loses a tab by a move").isEmpty();
        assertThat(max.remove(tab(3)).maximized()).as("one of its tabs closes").contains(groupId(2));
        assertThat(max.remove(tab(1)).maximized()).as("a tab of another group closes").contains(groupId(2));
        assertThat(max.remove(tab(3)).remove(tab(4)).maximized()).as("its last tab closes and it goes").isEmpty();
        assertThat(max.remove(tab(1)).remove(tab(2)).maximized()).as("the last other group goes: one group is not maximized").isEmpty();
        Layout three = new Layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(three.remove(tab(3)).maximized()).as("another group goes and two remain").contains(groupId(2));
        assertThat(three.remove(tab(3)).activeGroup()).isEqualTo(groupId(2));
    }
}
