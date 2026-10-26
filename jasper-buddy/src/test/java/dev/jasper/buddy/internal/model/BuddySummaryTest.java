package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class BuddySummaryTest {
    private final BuddyDeck deck = new BuddyDeck();
    private final BuddyGroups groups = new BuddyGroups();
    private BuddyNotice post(String id, BuddyNotice.State state) {
        var kind = state == BuddyNotice.State.RUNNING || state == BuddyNotice.State.NEEDS_INPUT ? BuddyNotice.Kind.TASK : BuddyNotice.Kind.CONNECTION;
        var n = new BuddyNotice(new BuddyNoticeId("test", id), kind, id, state, () -> "never use elapsed", () -> {});
        deck.post(n); return n;
    }
    @Test void countsVisibleTasksAndConnectionsWithoutGuessingTabsOrRunningConnections() {
        assertThat(groups.summary(deck)).isNull();
        var first = post("a", BuddyNotice.State.RUNNING);
        assertThat(groups.summary(deck).title()).isEqualTo("a");
        assertThat(groups.summary(deck).detail().get()).isEqualTo("Running");
        post("b", BuddyNotice.State.RUNNING); post("c", BuddyNotice.State.RUNNING);
        assertThat(groups.summary(deck).title()).isEqualTo("3 tasks");
        post("ssh", BuddyNotice.State.UP);
        assertThat(groups.summary(deck).title()).isEqualTo("3 tasks · 1 connection");
        assertThat(groups.summary(deck).detail().get()).isEqualTo("View activity");
        deck.updateContext(first.id(), null, false);
        assertThat(groups.summary(deck).title()).isEqualTo("2 tasks · 1 connection");
        deck.dismiss(new BuddyNoticeId("test", "b")); deck.dismiss(new BuddyNoticeId("test", "c"));
        post("tunnel", BuddyNotice.State.UP);
        assertThat(groups.summary(deck).title()).isEqualTo("2 connections");
    }
    @Test void attentionPriorityAndTiesAreStableAndIdentifyTheActualNotice() {
        post("run", BuddyNotice.State.RUNNING); post("degraded", BuddyNotice.State.DEGRADED);
        assertThat(groups.summary(deck).title()).isEqualTo("degraded");
        post("down", BuddyNotice.State.DOWN);
        assertThat(groups.summary(deck).title()).isEqualTo("down");
        var first = post("input", BuddyNotice.State.NEEDS_INPUT);
        assertThat(groups.summary(deck).title()).isEqualTo("input");
        post("later input", BuddyNotice.State.NEEDS_INPUT);
        assertThat(groups.summary(deck).title()).isEqualTo("input");
        deck.updateContext(first.id(), new BuddyNotice.Group(new BuddyNoticeId("test", "group"), "Workspace"), true);
        assertThat(groups.summary(deck).title()).isEqualTo("input");
        assertThat(groups.summary(deck).detail().get()).isEqualTo("Needs input · 4 other activities");
        deck.updateContext(first.id(), null, false);
        assertThat(groups.summary(deck).title()).isEqualTo("later input");
        deck.updateContext(first.id(), null, true);
        assertThat(groups.summary(deck).title()).isEqualTo("later input");
    }
}
