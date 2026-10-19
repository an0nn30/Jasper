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

/** Spec 7.1 and 7.2: a layout's construction, invariants and queries. */
class LayoutTest {
    @Test void theEmptyWorkspaceIsOneEmptyActiveGroup() {
        Layout empty = Layout.empty();
        Group group = (Group) empty.root();
        assertThat(group.tabs()).isEmpty();
        assertThat(group.selected()).isEmpty();
        assertThat(empty.activeGroup()).isEqualTo(group.id());
        assertThat(empty.maximized()).isEmpty();
        assertThat(empty.isEmpty()).isTrue();
        assertThat(empty.tabs()).isEmpty();
        assertThat(empty.groups()).containsExactly(group);
        assertThat(empty.recent()).containsExactly(group.id());
        assertThat(empty.activeTab()).isEmpty();
        assertThat(Layout.empty().activeGroup()).as("a fresh group each time").isNotEqualTo(empty.activeGroup());
    }

    @Test void ofHoldsOneSelectedTabInOneActiveGroup() {
        Layout one = Layout.of(tab(1));
        GroupId id = one.activeGroup();
        assertThat(one.root()).isEqualTo(new Group(id, List.of(tab(1)), Optional.of(tab(1))));
        assertThat(one.isEmpty()).isFalse();
        assertThat(one.contains(tab(1))).isTrue();
        assertThat(one.groupOf(tab(1))).contains(id);
        assertThat(one.activeTab()).contains(tab(1));
        assertThatNullPointerException().isThrownBy(() -> Layout.of(null));
    }

    @Test void theConstructorChecksTheInvariants() {
        Group a = group(1), b = group(2);
        assertThatIllegalArgumentException().as("a group twice")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(a, 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("a tab in two groups")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(group(2, 1, 1), 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("an empty group beside another")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(new Group(groupId(2), List.of(), Optional.empty()), 0.5)), groupId(1), Optional.empty()));
        assertThatIllegalArgumentException().as("an active group outside the tree")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(3), Optional.empty()));
        assertThatIllegalArgumentException().as("a maximized group that is not active")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(1), Optional.of(groupId(2))));
        assertThatIllegalArgumentException().as("a maximized group outside the tree")
            .isThrownBy(() -> new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(1), Optional.of(groupId(3))));
        assertThatIllegalArgumentException().as("one group cannot be maximized")
            .isThrownBy(() -> new Layout(a, groupId(1), Optional.of(groupId(1))));
        assertThatNullPointerException().isThrownBy(() -> new Layout(null, groupId(1), Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new Layout(a, null, Optional.empty()));
        assertThatNullPointerException().isThrownBy(() -> new Layout(a, groupId(1), null));
        assertThat(new Layout(new Group(groupId(1), List.of(), Optional.empty()), groupId(1), Optional.empty()).isEmpty())
            .as("the empty workspace's group may be empty").isTrue();
        assertThat(new Layout(h(child(a, 0.5), child(b, 0.5)), groupId(2), Optional.of(groupId(2))).maximized()).contains(groupId(2));
    }

    @Test void queriesListGroupsAndTabsInWorkspaceOrder() {
        Group a = group(1, 2, 1, 2), b = group(2, 3, 3), c = group(3, 4, 4);
        Layout grid = layout(h(child(a, 0.5), child(v(child(b, 0.5), child(c, 0.5)), 0.5)), 3);
        assertThat(grid.groups()).containsExactly(a, b, c);
        assertThat(grid.tabs()).containsExactly(tab(1), tab(2), tab(3), tab(4));
        assertThat(grid.group(groupId(2))).contains(b);
        assertThat(grid.group(groupId(9))).isEmpty();
        assertThat(grid.groupOf(tab(4))).contains(groupId(3));
        assertThat(grid.groupOf(tab(9))).isEmpty();
        assertThat(grid.contains(tab(2))).isTrue();
        assertThat(grid.contains(tab(9))).isFalse();
        assertThat(grid.activeTab()).contains(tab(4));
        assertThat(grid.isEmpty()).isFalse();
    }

    @Test void theOrderOfUseStartsWithTheActiveGroupThenFollowsTheTree() {
        Layout row = layout(h(child(group(1), 0.25), child(group(2), 0.25), child(group(3), 0.5)), 2);
        assertThat(row.recent()).containsExactly(groupId(2), groupId(1), groupId(3));
    }

    @Test void equalLayoutsHaveEqualHashCodes() {
        Layout first = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 2);
        Layout second = layout(h(child(group(1), 0.5), child(group(2), 0.5)), 2);
        assertThat(first).isEqualTo(second).hasSameHashCodeAs(second);
        assertThat(first).isNotEqualTo(layout(h(child(group(1), 0.5), child(group(2), 0.5)), 1));
    }
}
