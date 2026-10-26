package dev.jasper.buddy.internal.presentation;

import dev.jasper.buddy.internal.model.BuddyDeck;
import dev.jasper.buddy.notice.*;
import java.awt.Point;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddyCompactActivityTest {
    @Test void oneSummaryAlwaysOpensDrawerAndRunningWorkNeedsNoFrames() {
        var deck = new BuddyDeck();
        int[] actions = {0, 0};
        var panel = new BuddyColumnPanel(BuddyTestAppearance.options(), deck, () -> {}, () -> actions[0]++);
        for (int i = 0; i < 5; i++) {
            deck.post(new BuddyNotice(new BuddyNoticeId("test", "n" + i), BuddyNotice.Kind.TASK,
                "Task", BuddyNotice.State.RUNNING, () -> "12m", () -> actions[1]++));
            panel.refresh();
            panel.handleClick(new Point(BuddyColumnPanel.MARGIN + 30, BuddyColumnPanel.MARGIN + 25));
            assertThat(panel.getPreferredSize().height).isEqualTo(BuddyCard.HEIGHT + 2 * BuddyColumnPanel.MARGIN);
            assertThat(panel.needsAnimationFrames()).isFalse();
            assertThat(panel.needsDetailUpdates()).isFalse();
        }
        assertThat(actions).containsExactly(5, 0);
    }
    @Test void drawerUpdatesKeepFirstObservedRowsAndDoNotShimmer() {
        var deck = new BuddyDeck();
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        for (int i = 0; i < 3; i++) {
            deck.post(new BuddyNotice(new BuddyNoticeId("test", "n" + i), BuddyNotice.Kind.TASK,
                "Task " + i, BuddyNotice.State.RUNNING, () -> "12m", () -> {}));
            panel.refresh();
        }
        assertThat(panel.displayed()).extracting(BuddyNotice::title).containsExactly("Task 0", "Task 1", "Task 2");
        assertThat(panel.needsAnimationFrames()).isFalse();
    }
    @Test void groupedHeaderCollapsesInlineAndDismissAllIncludesCollapsedButNotSuppressed() {
        var deck = new BuddyDeck();
        var group = new BuddyNotice.Group(new BuddyNoticeId("test", "group"), "Workspace");
        for (int i = 0; i < 3; i++) deck.post(new BuddyNotice(new BuddyNoticeId("test", "n" + i),
            BuddyNotice.Kind.TASK, "Task", BuddyNotice.State.RUNNING, () -> "Running", () -> {}, group), i != 2);
        var panel = new BuddyDeckPanel(BuddyTestAppearance.options(), deck, () -> {});
        panel.refresh(); panel.setSize(panel.getPreferredSize());
        int expanded = panel.getPreferredSize().height;
        panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 40, BuddyDeckPanel.MOTION_MARGIN + 14));
        panel.setSize(panel.getPreferredSize());
        assertThat(panel.getPreferredSize().height).isLessThan(expanded);
        assertThat(panel.displayed()).hasSize(2);
        assertThat(panel.handleClick(new Point(BuddyDeckPanel.MOTION_MARGIN + 40,
            panel.getHeight() - BuddyDeckPanel.MOTION_MARGIN - 10))).isFalse();
        assertThat(deck.retainedNotices()).extracting(n -> n.id().key()).containsExactly("n2");
    }
}
