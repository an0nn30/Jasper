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

    @Test void onlyAWindowsOnlyParentTabMayBeEmpty() {
        ParentTab empty = new ParentTab(ParentTabId.random(), Layout.empty());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabs.of(empty).open(parent(A), 1))
            .as("New Tab fills the empty workspace instead").withMessageContaining("only parent tab may be empty");
        assertThatIllegalArgumentException().isThrownBy(() -> new ParentTabs(List.of(parent(A), empty), empty.id()));
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

    @Test void equalityIgnoresTheOrderOfUse() {
        ParentTab a = parent(A), b = parent(B);
        ParentTabs plain = new ParentTabs(List.of(a, b), a.id());
        ParentTabs used = plain.select(b.id()).select(a.id());
        assertThat(used).isEqualTo(plain).hasSameHashCodeAs(plain);
        assertThat(used.recent()).containsExactly(a.id(), b.id());
        assertThat(plain.select(b.id())).isNotEqualTo(plain);
    }

    @Test void aWindowHoldsParentTabsOrOnePaneLayout() {
        WindowLayout full = ParentTabs.of(parent(A, B));
        WindowLayout pane = new PaneLayout(Layout.of(C));
        for (WindowLayout layout : List.of(full, pane)) {
            List<TabId> tabs = switch (layout) {
                case ParentTabs parents -> parents.parentTabs().getFirst().layout().tabs();
                case PaneLayout single -> single.layout().tabs();
            };
            assertThat(layout.tabs()).isEqualTo(tabs);
        }
        assertThat(full.tabs()).containsExactly(A, B);
        assertThatIllegalArgumentException().isThrownBy(() -> new PaneLayout(Layout.empty())).withMessageContaining("pane window");
    }

    @Test void aParentTabIdIsAUuidThatParsesBack() {
        ParentTabId id = ParentTabId.random();
        assertThat(ParentTabId.parse(id.toString())).isEqualTo(id);
        assertThat(id).isNotEqualTo(ParentTabId.random());
        assertThatIllegalArgumentException().isThrownBy(() -> ParentTabId.parse("not an id"));
    }
}
