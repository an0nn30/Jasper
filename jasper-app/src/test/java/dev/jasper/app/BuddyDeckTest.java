package dev.jasper.app;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

class BuddyDeckTest {
    private final BuddyDeck deck = new BuddyDeck();

    private static BuddyNotice running(String source, Object key, String title) {
        return new BuddyNotice(source, key, BuddyNotice.Kind.TASK, title,
            BuddyNotice.State.RUNNING, () -> "running", () -> { });
    }

    private static BuddyNotice notice(String source, Object key, String title) {
        return new BuddyNotice(source, key, BuddyNotice.Kind.TASK, title, BuddyNotice.State.DONE, () -> "done", () -> { });
    }

    @Test void noticesComeBackNewestFirst() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("second", "first");
    }

    @Test void postingOverTheSameKeyReplacesInPlaceAndPromotesToTheTop() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));
        deck.post(notice("terminal", "a", "first again"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("first again", "second");
    }

    /** The whole point of the source being part of the identity: producers cannot collide. */
    @Test void twoProducersWithTheSameKeyAreTwoNotices() {
        deck.post(notice("terminal", "1", "a build"));
        deck.post(notice("sftp", "1", "a transfer"));

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a transfer", "a build");
    }

    @Test void dismissingRemovesOneAndSaysWhetherThereWasOne() {
        deck.post(notice("terminal", "a", "first"));

        assertThat(deck.dismiss("terminal", "a")).isTrue();
        assertThat(deck.dismiss("terminal", "a")).isFalse();
        assertThat(deck.isEmpty()).isTrue();
    }

    @Test void clearingEmptiesTheDrawer() {
        deck.post(notice("terminal", "a", "first"));
        deck.post(notice("terminal", "b", "second"));

        deck.clear();

        assertThat(deck.notices()).isEmpty();
    }

    /**
     * The regression this design is shaped to avoid: a pane closed after a successful build must
     * still say the build succeeded. Orphaning is a lost action, not a lost outcome.
     */
    @Test void orphaningKeepsTheOutcomeAndOnlyTakesAwayTheAction() {
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "./gradlew build", BuddyNotice.State.DONE,
            () -> "Finished in 1m 12s", () -> { }));

        deck.orphan("terminal", "a", "Finished in 1m 12s");

        BuddyNotice orphan = deck.notices().getFirst();
        assertThat(orphan.state()).isEqualTo(BuddyNotice.State.DONE);
        assertThat(orphan.title()).isEqualTo("./gradlew build");
        assertThat(orphan.detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(orphan.orphaned()).isTrue();
        assertThat(orphan.activate()).isNull();
    }

    @Test void orphaningFreezesARunningNoticeSoItStopsTicking() {
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "sleep 600", BuddyNotice.State.RUNNING,
            () -> "Running · " + System.nanoTime(), () -> { }));

        deck.orphan("terminal", "a", "Stopped after 1m 12s");

        assertThat(deck.notices().getFirst().detail().get()).isEqualTo("Stopped after 1m 12s");
    }

    @Test void orphaningKeepsThePlaceRatherThanPromotingToTheTop() {
        deck.post(notice("terminal", "a", "older"));
        deck.post(notice("terminal", "b", "newer"));

        deck.orphan("terminal", "a", "gone");

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("newer", "older");
    }

    @Test void orphaningSomethingThatIsNotThereDoesNothing() {
        deck.post(notice("terminal", "a", "only"));

        deck.orphan("terminal", "missing", "gone");

        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("only");
    }

    @Test void theOldestFallOffOnceTheDrawerIsFull() {
        for (int i = 0; i < BuddyDeck.MAX_NOTICES + 5; i++) deck.post(notice("terminal", i, "n" + i));

        assertThat(deck.size()).isEqualTo(BuddyDeck.MAX_NOTICES);
        assertThat(deck.notices().getFirst().title()).isEqualTo("n" + (BuddyDeck.MAX_NOTICES + 4));
        assertThat(deck.notices().getLast().title()).isEqualTo("n5");
    }

    @Test void aNoticeNeedsAnIdentityAndSomethingToSay() {
        assertThatIllegalArgumentException().isThrownBy(() ->
            new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "  ", BuddyNotice.State.DONE, () -> "d", () -> { }));
    }

    /** The two-argument form is for a running notice only; this one must not rewrite what it said. */
    @Test void orphaningWithoutAReplacementKeepsWhatTheNoticeSaid() {
        deck.post(new BuddyNotice("terminal", "a", BuddyNotice.Kind.TASK, "./gradlew build", BuddyNotice.State.DONE,
            () -> "Finished in 1m 12s", () -> { }));

        deck.orphan("terminal", "a");

        BuddyNotice orphan = deck.notices().getFirst();
        assertThat(orphan.detail().get()).isEqualTo("Finished in 1m 12s");
        assertThat(orphan.state()).isEqualTo(BuddyNotice.State.DONE);
        assertThat(orphan.orphaned()).isTrue();
    }

    /** The column's whole rule: still happening, or you have not looked at it yet. */
    @Test void theColumnHoldsWhatIsLiveOrUnseen() {
        deck.post(running("terminal", "a", "a build"));
        deck.post(notice("terminal", "b", "a finished thing"));

        assertThat(deck.column()).extracting(BuddyNotice::title)
            .containsExactly("a finished thing", "a build");
    }

    @Test void lookingAtAFinishedNoticeTakesItOutOfTheColumnButNotTheDrawer() {
        deck.post(notice("terminal", "b", "a finished thing"));

        deck.acknowledge("terminal", "b");

        assertThat(deck.column()).isEmpty();
        assertThat(deck.notices()).extracting(BuddyNotice::title).containsExactly("a finished thing");
        assertThat(deck.acknowledged("terminal", "b")).isTrue();
    }

    /** It is live, so looking at it changes nothing until it stops being live. */
    @Test void lookingAtSomethingStillRunningLeavesItInTheColumn() {
        deck.post(running("terminal", "a", "a build"));

        deck.acknowledge("terminal", "a");

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("a build");
    }

    /** Or a flapping tunnel would go quiet after the first drop. */
    @Test void aNewStateOverAnAcknowledgedKeyAsksForAttentionAgain() {
        deck.post(running("terminal", "a", "a build"));
        deck.acknowledge("terminal", "a");

        deck.post(notice("terminal", "a", "a build"));

        assertThat(deck.acknowledged("terminal", "a")).isFalse();
        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("a build");
    }

    @Test void acknowledgingSomethingThatIsNotThereIsHarmless() {
        deck.acknowledge("terminal", "missing");

        assertThat(deck.acknowledged("terminal", "missing")).isFalse();
    }

    @Test void dismissingForgetsTheAcknowledgementToo() {
        deck.post(notice("terminal", "a", "one"));
        deck.acknowledge("terminal", "a");

        deck.dismiss("terminal", "a");
        deck.post(notice("terminal", "a", "one again"));

        assertThat(deck.column()).extracting(BuddyNotice::title).containsExactly("one again");
    }

    @Test void clearingForgetsEveryAcknowledgement() {
        deck.post(notice("terminal", "a", "one"));
        deck.acknowledge("terminal", "a");

        deck.clear();
        deck.post(notice("terminal", "a", "one again"));

        assertThat(deck.column()).hasSize(1);
    }

    /** The oldest falling off must not leave its acknowledgement behind to leak forever. */
    @Test void aNoticePushedOutByTheBoundTakesItsAcknowledgementWithIt() {
        deck.post(notice("terminal", "first", "first"));
        deck.acknowledge("terminal", "first");
        for (int i = 0; i < BuddyDeck.MAX_NOTICES; i++) deck.post(notice("terminal", "k" + i, "n" + i));

        assertThat(deck.acknowledged("terminal", "first")).isFalse();
    }
}
