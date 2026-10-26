package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.*;
import dev.jasper.buddy.notice.*;
import java.awt.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyGroupsPresentationTest {
    BuddyNotice notice(String key, String title, String group) {
        return new BuddyNotice(new BuddyNoticeId("test", key), BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.RUNNING, () -> "Running · 2m", () -> {},
            group == null ? null : new BuddyNotice.Group(new BuddyNoticeId("test", group), "Group " + group));
    }
    @Test void groupsAndMembersRetainOrderAcrossUpdatesReparentingAndSuppression() {
        var deck = new BuddyDeck(); var groups = new BuddyGroups();
        var one = notice("1", "one", "a"); var two = notice("2", "two", "b"); var three = notice("3", "three", "a");
        for (var n : List.of(one, two, three)) { deck.post(n); groups.reconcile(deck); }
        var entries = groups.reconcile(deck);
        assertThat(entries).extracting(e -> e.group().title()).containsExactly("Group a", "Group b");
        groups.toggle(entries.getFirst().identity());
        deck.post(one); deck.updateTitle(three.id(), "new title");
        assertThat(groups.reconcile(deck).getFirst().members()).extracting(BuddyNotice::title).containsExactly("one", "new title");
        deck.updateContext(one.id(), two.group(), true);
        assertThat(groups.reconcile(deck).get(1).members()).extracting(BuddyNotice::title).containsExactly("one", "two");
        deck.updateContext(three.id(), three.group(), false);
        assertThat(groups.reconcile(deck)).hasSize(1);
        deck.updateContext(three.id(), new BuddyNotice.Group(three.group().id(), "Renamed"), true);
        assertThat(groups.reconcile(deck).getFirst().group().title()).isEqualTo("Renamed");
        assertThat(groups.expanded(entries.getFirst().identity())).isFalse();
        deck.dismiss(three.id()); groups.reconcile(deck); deck.post(three);
        assertThat(groups.reconcile(deck).getLast().group()).isEqualTo(three.group());
        assertThat(groups.expanded(entries.getFirst().identity())).isTrue();
    }
    @Test void duplicateTitlesRemainDistinctAcrossVisibilityAndRename() {
        var deck = new BuddyDeck(); var first = notice("1", "same", "a"); var second = notice("2", "same", "a");
        deck.post(first); deck.post(second);
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        var before = panel.displayed();
        assertThat(before).extracting(BuddyNotice::title).doesNotHaveDuplicates();
        String secondTitle = before.stream().filter(n -> n.id().equals(second.id())).findFirst().orElseThrow().title();
        deck.updateContext(first.id(), first.group(), false);
        assertThat(panel.displayed().getFirst().title()).isEqualTo(secondTitle);
        deck.updateTitle(second.id(), "changed");
        assertThat(panel.displayed().getFirst().title()).startsWith("changed · ");
    }
    @Test void collapsedRowsStayOutOfHitGeometryAndExternalShrinkClampsScroll() {
        var deck = new BuddyDeck();
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        for (int i = 0; i < 8; i++) { deck.post(notice("p" + i, "pane " + i, "a")); panel.refresh(); }
        panel.setMaxListHeight(() -> 160); panel.setSize(panel.getPreferredSize());
        panel.handleWheel(100); assertThat(panel.scroll()).isPositive();
        for (int i = 0; i < 7; i++) deck.dismiss(new BuddyNoticeId("test", "p" + i));
        panel.setSize(BuddyDeckWindow.reconcilePanel(panel));
        assertThat(panel.scroll()).isZero();
        assertThat(panel.rows()).hasSize(2);
        panel.handleClick(new Point(60, 45)); panel.setSize(panel.getPreferredSize());
        assertThat(panel.rows()).hasSize(1);
        panel.handleClick(new Point(60, 45)); panel.setSize(panel.getPreferredSize());
        assertThat(panel.rows()).hasSize(2);
    }
    @Test void viewportClipPreventsOffscreenRowAndDismissAllHits() {
        var deck = new BuddyDeck(); int[] activations = {0};
        for (int i = 0; i < 4; i++) deck.post(new BuddyNotice(new BuddyNoticeId("test", "p" + i),
            BuddyNotice.Kind.TASK, "Task", BuddyNotice.State.RUNNING, () -> "detail", () -> activations[0]++));
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        panel.setMaxListHeight(() -> 80); panel.setSize(panel.getPreferredSize());
        var clear = panel.rows().getLast().bounds();
        panel.handleClick(new Point(60, clear.y + 40));
        panel.handleClick(new Point(60, panel.getHeight() - 5));
        assertThat(deck.size()).isEqualTo(4); assertThat(activations[0]).isZero();
        panel.handleWheel(100); panel.handleClick(new Point(60, 30));
        assertThat(deck.size()).isEqualTo(4);
    }
}
