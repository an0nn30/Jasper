package dev.jasper.app.layout;

import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

/** Parent tabs spec 4 and 14: a window's parent tabs, their operations and invariants, and a window's two content shapes. */
class ParentTabsTest {
    private static final TabId A = TabId.random(), B = TabId.random(), C = TabId.random(), D = TabId.random();

    /** A parent tab of its own id holding {@code tabs} in one group, the first selected. */
    private static ParentTab parent(TabId... tabs) {
        Layout layout = Layout.of(tabs[0]);
        for (int i = 1; i < tabs.length; i++) layout = layout.open(tabs[i], layout.activeGroup(), i);
        return new ParentTab(ParentTabId.random(), layout.select(tabs[0]));
    }

    @Test void aWindowStartsWithOneParentTab() {
        ParentTab only = parent(A);
        ParentTabs tabs = ParentTabs.of(only);
        assertThat(tabs.parentTabs()).containsExactly(only);
        assertThat(tabs.active()).isEqualTo(only.id());
        assertThat(tabs.activeParentTab()).isEqualTo(only);
        assertThat(tabs.recent()).containsExactly(only.id());
        assertThat(tabs.tabs()).containsExactly(A);
        assertThat(tabs.isEmpty()).isFalse();
        assertThat(ParentTabs.of(new ParentTab(ParentTabId.random(), Layout.empty())).isEmpty())
            .as("the main window's empty workspace").isTrue();
    }

    @Test void openPutsAParentTabAtItsPlaceAndShowsIt() {
        ParentTab first = parent(A), second = parent(B), third = parent(C);
        ParentTabs tabs = ParentTabs.of(first).open(second, 1).open(third, 0);
        assertThat(tabs.parentTabs()).containsExactly(third, first, second);
        assertThat(tabs.active()).isEqualTo(third.id());
        assertThat(tabs.recent()).containsExactly(third.id(), second.id(), first.id());
        assertThat(tabs.tabs()).as("every tab, parent tab by parent tab").containsExactly(C, A, B);
        assertThat(tabs.indexOf(first.id())).isEqualTo(1);
        assertThat(tabs.indexOf(ParentTabId.random())).isEqualTo(-1);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(parent(D), 4)).withMessageContaining("Index 4");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(first, 0)).withMessageContaining("twice");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(parent(A), 0)).as("a tab in two parent tabs")
            .withMessageContaining("Tab " + A);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.open(new ParentTab(ParentTabId.random(), first.layout()), 0))
            .as("a group in two parent tabs").withMessageContaining("Group");
    }

    @Test void openRefusesAnEmptyParentTabBesideAnotherAndAnIndexBelowTheBar() {
        ParentTabs one = ParentTabs.of(parent(A));
        assertThatIllegalArgumentException().isThrownBy(() -> one.open(new ParentTab(ParentTabId.random(), Layout.empty()), 1))
            .withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> one.open(parent(B), -1)).withMessageContaining("Index -1");
    }

    @Test void onlyAWindowsOnlyParentTabMayBeEmpty() {
        ParentTab empty = new ParentTab(ParentTabId.random(), Layout.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabs.of(empty).open(parent(A), 1))
            .as("New Tab fills the empty workspace instead").withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(parent(A), empty), empty.id()))
            .withMessageContaining("only parent tab may be empty");
        ParentTab first = parent(A);
        ParentTabs one = ParentTabs.of(first);
        assertThat(one.replace(new ParentTab(first.id(), first.layout().remove(A))).isEmpty()).as("its last tab closed").isTrue();
        ParentTabs two = one.open(parent(B), 1);
        assertThatIllegalArgumentException().isThrownBy(() -> two.replace(new ParentTab(first.id(), first.layout().remove(A))))
            .withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(), first.id())).withMessageContaining("at least one");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(first), ParentTabId.random()))
            .withMessageContaining("not in the window");
    }

    @Test void selectShowsAParentTabAndPutsItFirstInTheOrderOfUse() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), a.id());
        ParentTabs next = tabs.select(c.id());
        assertThat(next.active()).isEqualTo(c.id());
        assertThat(next.parentTabs()).as("the bar keeps its order").containsExactly(a, b, c);
        assertThat(next.recent()).containsExactly(c.id(), a.id(), b.id());
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.select(ParentTabId.random())).withMessageContaining("not in the window");
    }

    @Test void removingTheShownParentTabShowsTheOneUsedBeforeIt() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), a.id()).select(c.id()).select(b.id());
        ParentTabs without = tabs.remove(b.id());
        assertThat(without.parentTabs()).containsExactly(a, c);
        assertThat(without.active()).as("the one used before it").isEqualTo(c.id());
        assertThat(without.remove(a.id()).active()).as("removing a hidden one keeps the shown one").isEqualTo(c.id());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabs.of(a).remove(a.id())).withMessageContaining("only parent tab");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.remove(ParentTabId.random())).withMessageContaining("not in the window");
    }

    @Test void theOneUsedBeforeItShowsEvenWhenAnotherFollowsItInTheBar() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), a.id()).select(c.id()).select(a.id());
        assertThat(tabs.recent()).containsExactly(a.id(), c.id(), b.id());
        assertThat(tabs.remove(a.id()).active()).as("c was used before a; b merely follows it in the bar").isEqualTo(c.id());
    }

    @Test void parentTabsNeverShownFollowAfterTheShownOneThenBeforeIt() {
        ParentTab a = parent(A), b = parent(B), c = parent(C), d = parent(D);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c, d), b.id());
        assertThat(tabs.recent()).containsExactly(b.id(), c.id(), d.id(), a.id());
        assertThat(tabs.remove(b.id()).active()).as("the next one, as a tab strip does").isEqualTo(c.id());
        assertThat(new ParentTabs(List.of(a, b, c, d), d.id()).remove(d.id()).active()).as("the last: the one before it")
            .isEqualTo(c.id());
    }

    @Test void stepGoesAlongTheBarAndWraps() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), c.id());
        assertThat(tabs.step(1)).isEqualTo(a.id());
        assertThat(tabs.step(-1)).isEqualTo(b.id());
        assertThat(tabs.step(3)).isEqualTo(c.id());
        assertThat(ParentTabs.of(a).step(1)).isEqualTo(a.id());
    }

    /** Parent tabs spec 5 and workspace plan 7c Rulings 2: a parent tab dragged along its bar takes another place there. */
    @Test void moveTakesAParentTabToAnotherPlaceAndKeepsTheShownOne() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), b.id()).select(c.id());
        ParentTabs moved = tabs.move(a.id(), 2);
        assertThat(moved.parentTabs()).containsExactly(b, c, a);
        assertThat(moved.active()).as("the shown one stays").isEqualTo(c.id());
        assertThat(moved.recent()).as("and so does the order of use").isEqualTo(tabs.recent());
        assertThat(moved.move(a.id(), 0).parentTabs()).containsExactly(a, b, c);
        assertThat(tabs.move(b.id(), 1)).as("its own place").isEqualTo(tabs);
        assertThat(tabs.move(b.id(), 1).recent()).as("keeps the order of use").isEqualTo(tabs.recent());
        ParentTabs one = ParentTabs.of(a);
        assertThat(one.move(a.id(), 0)).as("a window's only parent tab, in its one place").isEqualTo(one);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(a.id(), 3)).withMessageContaining("Index 3");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(a.id(), -1)).withMessageContaining("Index -1");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.move(ParentTabId.random(), 0))
            .withMessageContaining("not in the window");
    }

    @Test void replaceGivesAParentTabItsNewLayoutAndLookupsFollowIt() {
        ParentTab a = parent(A), b = parent(B);
        ParentTabs tabs = new ParentTabs(List.of(a, b), b.id());
        Layout grown = a.layout().split(C, a.layout().activeGroup(), Side.RIGHT);
        ParentTabs next = tabs.replace(new ParentTab(a.id(), grown));
        assertThat(next.parentTab(a.id()).orElseThrow().layout()).isEqualTo(grown);
        assertThat(next.active()).as("the shown one stays").isEqualTo(b.id());
        assertThat(next.holding(C)).contains(a.id());
        assertThat(next.holding(grown.activeGroup())).contains(a.id());
        assertThat(next.holding(B)).contains(b.id());
        assertThat(next.holding(D)).isEmpty();
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.replace(new ParentTab(ParentTabId.random(), grown)))
            .withMessageContaining("not in the window");
    }

    /** Parent tabs spec 4 and 7b's final review: a parent tab that moved in part takes a new id in its new window. */
    @Test void reidGivesAParentTabANewIdInItsPlace() {
        ParentTab a = parent(A), b = parent(B), c = parent(C);
        ParentTabs tabs = new ParentTabs(List.of(a, b, c), c.id()).select(b.id());
        ParentTabId fresh = ParentTabId.random();
        ParentTabs next = tabs.reid(b.id(), fresh);
        assertThat(next.parentTabs()).containsExactly(a, new ParentTab(fresh, b.layout()), c);
        assertThat(next.active()).as("it still shows").isEqualTo(fresh);
        assertThat(next.recent()).as("and keeps its place in the order of use").containsExactly(fresh, c.id(), a.id());
        assertThat(next.holding(B)).contains(fresh);
        ParentTabs other = tabs.reid(a.id(), fresh);
        assertThat(other.active()).as("the shown one stays").isEqualTo(b.id());
        assertThat(other.recent()).containsExactly(b.id(), c.id(), fresh);
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.reid(a.id(), c.id())).withMessageContaining("twice");
        assertThatIllegalArgumentException().isThrownBy(() -> tabs.reid(ParentTabId.random(), fresh))
            .withMessageContaining("not in the window");
    }

    @Test void equalityIgnoresTheOrderOfUse() {
        ParentTab a = parent(A), b = parent(B);
        ParentTabs plain = new ParentTabs(List.of(a, b), a.id());
        ParentTabs used = plain.select(b.id()).select(a.id());
        assertThat(used).isEqualTo(plain).hasSameHashCodeAs(plain);
        assertThat(used.recent()).containsExactly(a.id(), b.id());
        assertThat(plain.select(b.id())).isNotEqualTo(plain);
    }

    @Test void aWindowsTabsAreEachParentTabsInBarOrder() {
        ParentTab first = parent(A, B), second = parent(C);
        assertThat(new ParentTabs(List.of(first, second), second.id()).tabs()).containsExactly(A, B, C);
    }

    @Test void aParentTabIdIsAUuidThatParsesBack() {
        ParentTabId id = ParentTabId.random();
        assertThat(ParentTabId.parse(id.toString())).isEqualTo(id);
        assertThat(id).isNotEqualTo(ParentTabId.random());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabId.parse("not an id"));
    }
}
