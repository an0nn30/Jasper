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
}
