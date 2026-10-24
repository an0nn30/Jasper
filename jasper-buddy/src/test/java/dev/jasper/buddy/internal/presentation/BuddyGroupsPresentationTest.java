package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.internal.model.BuddyGroups;
import dev.jasper.buddy.notice.*;
import java.awt.Point;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyGroupsPresentationTest {
    BuddyNotice notice(String key, String title, String group) {
        return new BuddyNotice(new BuddyNoticeId("test", key), BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.RUNNING, () -> "Running · 2m", () -> {},
            group == null ? null : new BuddyNotice.Group(new BuddyNoticeId("test", group), "Tab"));
    }
    @Test void groupsUseIdentityAndNewestMemberOrderWithSeparateNoticeNamespace() {
        var entries = BuddyGroups.project(List.of(notice("1", "one", "a"), notice("a", "plugin", null),
            notice("2", "two", "b"), notice("3", "three", "a")));
        assertThat(entries).hasSize(3);
        assertThat(entries.getFirst().members()).extracting(BuddyNotice::title).containsExactly("one", "three");
        assertThat(entries.getFirst().collapsed().title()).isEqualTo("Tab");
        assertThat(entries.getFirst().collapsed().detail().get()).isEqualTo("2 running");
        assertThat(entries.get(0).identity()).isNotEqualTo(entries.get(1).identity());
    }
    @Test void stackClickExpandsWithoutActivationAndSingletonActivates() {
        var deck = new BuddyDeck();
        var requests = new ArrayList<BuddyNoticeId>();
        int[] activated = {0};
        var group = new BuddyNotice.Group(new BuddyNoticeId("test", "a"), "Tab");
        for (int i = 0; i < 4; i++) deck.post(new BuddyNotice(new BuddyNoticeId("test", "pane" + i),
            BuddyNotice.Kind.TASK, "Pane " + i, BuddyNotice.State.RUNNING, () -> "detail", () -> activated[0]++, group));
        var panel = new BuddyColumnPanel(BuddyTestAppearance.options(), deck, () -> {}, requests::add);
        panel.setClock(() -> 0); panel.refresh(); panel.setClock(() -> 1_000_000_000L);
        panel.setSize(panel.getPreferredSize());
        var bounds = panel.cardBounds(0);
        panel.handleClick(new Point(bounds.x + 1, bounds.y + 1));
        assertThat(requests).containsExactly(group.id());
        assertThat(activated[0]).isZero();
        panel.handleClick(new Point(bounds.x + bounds.width + 4, bounds.y + bounds.height / 2));
        assertThat(requests).containsExactly(group.id(), group.id());
        assertThat(BuddyGroups.project(deck.notices()).getFirst().backingLayers()).isEqualTo(2);
        assertThat(panel.getPreferredSize().height).isEqualTo(BuddyCard.HEIGHT + 8 + 2 * BuddyColumnPanel.MARGIN + BuddyColumnPanel.ARRIVAL_ROOM);
        for (int i = 1; i < 4; i++) deck.dismiss(new BuddyNoticeId("test", "pane" + i));
        panel.refresh(); bounds = panel.cardBounds(0);
        panel.handleClick(new Point(bounds.x + 20, bounds.y + 20));
        assertThat(activated[0]).isEqualTo(1);
        assertThat(BuddyGroups.project(deck.notices()).getFirst().collapsed().title()).isEqualTo("Tab");
    }
    @Test void suppressionRestorationKeepsTheStackMotionIdentity() {
        var deck = new BuddyDeck(); var n = notice("one", "pane", "a"); deck.post(n);
        long[] now = {0};
        var panel = new BuddyColumnPanel(BuddyTestAppearance.options(), deck, () -> {}, () -> {});
        panel.setClock(() -> now[0]); panel.refresh(); now[0] = 1_000_000_000L;
        var settled = panel.cardBounds(0);
        deck.updateContext(n.id(), n.group(), false); panel.refresh();
        assertThat(panel.getPreferredSize().height).isZero();
        deck.updateContext(n.id(), n.group(), true); panel.refresh();
        assertThat(panel.cardBounds(0)).isEqualTo(settled);
        assertThat(panel.animating()).isFalse();
    }
    @Test void filteredDrawerTracksSuppressionAndRetainsStableDuplicateLabels() {
        var deck = new BuddyDeck(); var first = notice("1", "same", "a"); var second = notice("2", "same", "a");
        deck.post(first); deck.post(second); deck.post(notice("other", "other", "b"));
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        panel.setGroupFilter(first.group().id());
        String label = panel.displayed().getFirst().title();
        deck.updateContext(first.id(), first.group(), false);
        assertThat(panel.displayed()).hasSize(1);
        assertThat(panel.displayed().getFirst().title()).isEqualTo(label);
        deck.updateContext(second.id(), second.group(), false);
        assertThat(panel.getPreferredSize()).isEqualTo(new java.awt.Dimension(0, 0));
        panel.setGroupFilter(null);
        assertThat(panel.displayed()).hasSize(1);
    }
    @Test void filteredDrawerClearDismissesOnlyVisibleMembersAndKeepsHiddenWork() {
        var deck = new BuddyDeck();
        deck.post(notice("1", "same", "a")); deck.post(notice("2", "same", "a"));
        deck.post(notice("3", "other", "b"));
        var hidden = notice("hidden", "hidden", "a"); deck.post(hidden, false);
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        panel.setGroupFilter(new BuddyNoticeId("test", "a"));
        assertThat(panel.header()).isEqualTo("Tab");
        assertThat(panel.displayed()).hasSize(2);
        assertThat(panel.displayed()).extracting(BuddyNotice::title).doesNotHaveDuplicates();
        panel.setSize(panel.getPreferredSize());
        var clear = BuddyDeckLayout.clearRow(2, BuddyCard.WIDTH, panel.cardHeight(), 0);
        assertThat(panel.handleClick(new Point(clear.x + 30 + BuddyDeckPanel.MOTION_MARGIN,
            clear.y + 5 + BuddyDeckPanel.MOTION_MARGIN + panel.headerHeight()))).isFalse();
        assertThat(deck.notices()).extracting(n -> n.id().key()).containsExactly("3");
        assertThat(panel.displayed()).isEmpty();
        deck.updateContext(hidden.id(), hidden.group(), true);
        assertThat(deck.notices()).extracting(n -> n.id().key()).contains("hidden");
    }
}
