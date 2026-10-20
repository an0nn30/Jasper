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

/**
 * Spec 7.3 and tabs and panes spec 4: the empty workspace taking a tab, selecting, activating and removing tabs, the
 * order of use and the maximize; a pane holds one tab (workspace plan 8a Rulings 2).
 */
class LayoutOperationsTest {
    /** Pane 1 holds tab 1, pane 2 tab 3; side by side, pane 2 active. */
    static Layout pair() {
        return layout(h(child(group(1), 0.5), child(group(2, 3), 0.5)), 2);
    }

    @Test void openFillsTheEmptyWorkspacesPane() {
        Layout empty = Layout.empty();
        Layout one = empty.open(tab(1));
        assertThat(one.root()).isEqualTo(new Group(empty.activeGroup(), List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
        assertThat(one.activeTab()).contains(tab(1));
    }

    /** A pane holds one tab, so only the empty workspace's pane takes one; a split puts a tab beside a pane. */
    @Test void openRefusesALayoutHoldingATab() {
        assertThatIllegalArgumentException().isThrownBy(() -> pair().open(tab(9))).withMessageContaining("empty workspace");
        Layout one = Layout.empty().open(tab(1));
        assertThatIllegalArgumentException().isThrownBy(() -> one.open(tab(2)));
        assertThatNullPointerException().isThrownBy(() -> Layout.empty().open(null));
    }

    @Test void selectShowsTheTabAndActivatesItsPane() {
        Layout selected = pair().select(tab(1));
        assertThat(selected.group(groupId(1))).contains(group(1));
        assertThat(selected.activeGroup()).isEqualTo(groupId(1));
        assertThat(selected.activeTab()).contains(tab(1));
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
        assertThat(empty.open(tab(5)).root()).as("the next tab goes into the same pane").isEqualTo(group(2, 5));
    }

    @Test void theMaximizeFollowsTheActivePaneAndSurvivesClosedTabs() {
        Layout max = new Layout(h(child(group(1), 0.5), child(group(2), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(max.select(tab(1)).maximized()).as("another pane's tab is selected").contains(groupId(1));
        assertThat(max.activate(groupId(1)).maximized()).as("another pane is activated").contains(groupId(1));
        assertThat(max.remove(tab(2)).maximized()).as("its tab closes and it goes").isEmpty();
        assertThat(max.remove(tab(1)).maximized()).as("the last other pane goes: one pane is not maximized").isEmpty();
        Layout three = new Layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), groupId(2), Optional.of(groupId(2)));
        assertThat(three.remove(tab(3)).maximized()).as("another pane goes and two remain").contains(groupId(2));
        assertThat(three.remove(tab(3)).activeGroup()).isEqualTo(groupId(2));
        assertThat(three.remove(tab(2)).maximized()).as("its tab closes and two others remain").isEmpty();
        assertThat(three.remove(tab(2)).activeGroup()).as("the most recently used remaining pane").isEqualTo(groupId(1));
    }
}
