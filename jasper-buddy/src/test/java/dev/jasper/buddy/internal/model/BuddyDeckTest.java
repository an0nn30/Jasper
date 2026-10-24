package dev.jasper.buddy.internal.model;

import dev.jasper.buddy.notice.BuddyNotice;
import dev.jasper.buddy.notice.BuddyNoticeId;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyDeckTest {
    private final BuddyDeck deck = new BuddyDeck();

    private static BuddyNotice running(String source, Object key, String title) {
        return new BuddyNotice(new BuddyNoticeId(source, key), BuddyNotice.Kind.TASK, title, BuddyNotice.State.RUNNING,
            () -> "running", () -> { });
    }

    @Test void noticesComeBackNewestFirst() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second", "first");
    }

    @Test void postingOverTheSameKeyReplacesInPlaceAndPromotesToTheTop() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        deck.post(running("terminal", "a", "first again"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("first again", "second");
    }

    /** The whole point of the source being part of the identity: producers cannot collide. */
    @Test void twoProducersWithTheSameKeyAreTwoNotices() {
        deck.post(running("terminal", "1", "a build"));
        deck.post(running("sftp", "1", "a transfer"));
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a transfer", "a build");
    }

    /** What a producer does when its work ends. */
    @Test void dismissingRemovesOneAndSaysWhetherThereWasOne() {
        deck.post(running("terminal", "a", "first"));
        assertThat(deck.dismiss(new BuddyNoticeId("terminal", "a"))).isTrue();
        assertThat(deck.dismiss(new BuddyNoticeId("terminal", "a"))).isFalse();
        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void clearingEmptiesTheDrawer() {
        deck.post(running("terminal", "a", "first"));
        deck.post(running("terminal", "b", "second"));
        deck.clear();
        assertThat(deck.notices()).isEmpty();
    }

    @Test void aNewStateOverTheSameKeyIsANewPost() {
        deck.post(running("terminal", "a", "a build"));
        int generation = deck.generation();
        deck.post(new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "a build",
            BuddyNotice.State.NEEDS_INPUT, () -> "waiting", () -> { }));
        assertThat(deck.generation()).isEqualTo(generation + 1);
        assertThat(deck.notices()).singleElement().extracting(BuddyNotice::state).isEqualTo(BuddyNotice.State.NEEDS_INPUT);
    }

    @Test void aTitleChangeIsNotAPost() {
        deck.post(running("terminal", "a", "worker"));
        deck.post(running("terminal", "b", "other"));
        int generation = deck.generation();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "a"), "Reviewing")).isTrue();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "a"), "Reviewing")).isFalse();
        assertThat(deck.updateTitle(new BuddyNoticeId("terminal", "missing"), "Reviewing")).isFalse();
        assertThat(deck.generation()).isEqualTo(generation);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("other", "Reviewing");
    }

    @Test void aSnapshotKeepsWhatItSawWhenTheDeckChanges() {
        deck.post(running("terminal", "a", "first"));
        List<BuddyNotice> before = deck.notices();
        assertThat(deck.notices()).isSameAs(before);
        deck.post(running("terminal", "b", "second"));
        assertThat(before).extracting(BuddyNotice::title).containsExactly("first");
        assertThat(deck.notices()).hasSize(2);
    }

    @Test void theOldestFallOffOnceTheDrawerIsFull() {
        for (int i = 0; i < BuddyDeck.MAX_NOTICES + 5; i++) deck.post(running("terminal", i, "n" + i));
        assertThat(deck.size()).isEqualTo(BuddyDeck.MAX_NOTICES);
        assertThat(deck.notices().getFirst().title()).isEqualTo("n" + (BuddyDeck.MAX_NOTICES + 4));
        assertThat(deck.notices().getLast().title()).isEqualTo("n5");
    }

    @Test void aNoticeNeedsAnIdentityAndSomethingToSay() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new BuddyNotice(new BuddyNoticeId("terminal", "a"), BuddyNotice.Kind.TASK, "  ", BuddyNotice.State.RUNNING, () -> "d", () -> { }));
    }
    private static BuddyNotice.Group group(String title) {
        return new BuddyNotice.Group(new BuddyNoticeId("terminal-tab", "tab"), title);
    }

    @Test void hiddenNoticeRestoresWithoutPromotionAndDismissalCannotResurrectIt() {
        var id = new BuddyNoticeId("terminal", "pane");
        var group = group("Claude");
        var notice = new BuddyNotice(id, BuddyNotice.Kind.TASK, "Claude", BuddyNotice.State.RUNNING,
            () -> "Running · 12s", () -> {}, group);
        deck.post(notice, false);
        assertThat(deck.notices()).isEmpty();
        assertThat(deck.size()).isZero();
        assertThat(deck.isEmpty()).isTrue();
        int generation = deck.generation();
        deck.updateContext(id, group, true);
        assertThat(deck.notices()).containsExactly(notice);
        assertThat(deck.generation()).isEqualTo(generation);
        var snapshot = deck.notices();
        deck.updateContext(id, group, true);
        assertThat(deck.notices()).isSameAs(snapshot);
        deck.dismiss(id);
        deck.updateContext(id, group, true);
        assertThat(deck.notices()).isEmpty();
    }

    @Test void titleAndContextChangesPreserveGroupSuppressionAndArrivalOrder() {
        var first = running("terminal", "a", "first");
        var second = running("terminal", "b", "second");
        deck.post(first, false);
        deck.post(second);
        deck.updateContext(first.id(), group("Claude"), false);
        deck.updateTitle(first.id(), "Reviewing");
        assertThat(deck.notices()).containsExactly(second);
        deck.updateContext(first.id(), group("Renamed"), true);
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second", "Reviewing");
        assertThat(deck.notices().getLast().group()).isEqualTo(group("Renamed"));
        deck.updateTitle(first.id(), "Changed");
        assertThat(deck.notices().getLast().group()).isEqualTo(group("Renamed"));
        deck.updateContext(second.id(), null, false);
        deck.updateContext(second.id(), null, true);
        assertThat(deck.notices().getFirst()).isEqualTo(second);
        assertThat(deck.generation()).isEqualTo(2);
    }

    @Test void overflowCountsHiddenEntriesAndClearDropsTheirCallbacks() {
        var oldest = running("terminal", "old", "old");
        deck.post(oldest, false);
        for (int i = 0; i < BuddyDeck.MAX_NOTICES; i++) deck.post(running("terminal", i, "n" + i), false);
        deck.updateContext(oldest.id(), null, true);
        assertThat(deck.notices()).isEmpty();
        deck.updateContext(new BuddyNoticeId("terminal", 0), null, true);
        assertThat(deck.size()).isEqualTo(1);
        deck.clear();
        for (int i = 0; i < BuddyDeck.MAX_NOTICES; i++) deck.updateContext(new BuddyNoticeId("terminal", i), null, true);
        assertThat(deck.notices()).isEmpty();
        deck.post(oldest);
        assertThat(deck.notices()).containsExactly(oldest);
    }

    @Test void repostSetsVisibilityAndPromotesExactlyOnce() {
        var first = running("terminal", "a", "first");
        var second = running("terminal", "b", "second");
        deck.post(first); deck.post(second);
        deck.post(first, false);
        assertThat(deck.notices()).containsExactly(second);
        deck.post(first);
        assertThat(deck.notices()).containsExactly(first, second);
        assertThat(deck.generation()).isEqualTo(4);
    }
}
