package dev.jasper.browser.item;

import dev.jasper.browser.item.AudioGate.AudioAnswer;
import dev.jasper.browser.item.AudioGate.Effect;
import dev.jasper.browser.permissions.AudioMode;
import dev.jasper.browser.permissions.Decision;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 12's audio decisions for one item, document by document (plan 6 Task 7): the first time a page with no
 * stored choice plays sound it is muted and the bar asks; Allow Once and Deny Once last until the page's next main-frame
 * load, and the Always answers give the decision to store. A stored Block mutes without the bar, a stored Allow plays,
 * and Unmute Current Page acts as Allow Once. Pure; no host.
 */
class AudioGateTest {
    private static final String SITE = "https://a.test";

    @Test void theFirstPlayInAskShowsTheBarAndMutesAndASecondShowsNothingNew() {
        var gate = asking();
        assertThat(gate.muted()).as("before any sound").isFalse();
        assertThat(gate.barShown()).isFalse();
        assertThat(gate.playing()).isEqualTo(Effect.SHOW_BAR);
        assertState(gate, true, true, AudioMode.ASK);
        assertThat(gate.origin()).isEqualTo(SITE);
        assertThat(gate.playing()).as("a second play").isEqualTo(Effect.NONE);
        assertState(gate, true, true, AudioMode.ASK);
    }

    @Test void allowOnceUnmutesThisDocumentAndStoresNothing() {
        var gate = asked();
        assertThat(gate.answer(AudioAnswer.ALLOW_ONCE)).isEmpty();
        assertState(gate, false, false, AudioMode.ALLOW);
        assertThat(gate.playing()).isEqualTo(Effect.NONE);
    }

    @Test void allowAlwaysUnmutesAndGivesAllowedToStore() {
        var gate = asked();
        assertThat(gate.answer(AudioAnswer.ALLOW_ALWAYS)).contains(Decision.ALLOWED);
        assertState(gate, false, false, AudioMode.ALLOW);
    }

    @Test void denyOnceKeepsThePageMutedAndStoresNothing() {
        var gate = asked();
        assertThat(gate.answer(AudioAnswer.DENY_ONCE)).isEmpty();
        assertState(gate, true, false, AudioMode.BLOCK);
        assertThat(gate.playing()).isEqualTo(Effect.NONE);
    }

    @Test void denyAlwaysKeepsThePageMutedAndGivesBlockedToStore() {
        var gate = asked();
        assertThat(gate.answer(AudioAnswer.DENY_ALWAYS)).contains(Decision.BLOCKED);
        assertState(gate, true, false, AudioMode.BLOCK);
    }

    @Test void aStoredBlockMutesWithNoBar() {
        var gate = new AudioGate();
        gate.document(SITE, AudioMode.BLOCK);
        assertState(gate, false, false, AudioMode.BLOCK);
        assertThat(gate.playing()).isEqualTo(Effect.MUTED);
        assertState(gate, true, false, AudioMode.BLOCK);
        assertThat(gate.playing()).isEqualTo(Effect.NONE);
        for (AudioAnswer answer : AudioAnswer.values())
            assertThat(gate.answer(answer)).as("no bar to answer: " + answer).isEmpty();
        assertState(gate, true, false, AudioMode.BLOCK);
    }

    @Test void aStoredAllowDoesNothing() {
        var gate = new AudioGate();
        gate.document(SITE, AudioMode.ALLOW);
        assertThat(gate.playing()).isEqualTo(Effect.NONE);
        assertThat(gate.playing()).isEqualTo(Effect.NONE);
        assertState(gate, false, false, AudioMode.ALLOW);
    }

    /** The plan's call: Allow Once and Deny Once last until the next main-frame load, which hides the bar too. */
    @Test void aNewDocumentDropsTheOnceAnswersAndHidesTheBar() {
        var gate = asked();
        gate.answer(AudioAnswer.ALLOW_ONCE);
        gate.document(SITE, AudioMode.ASK);
        assertState(gate, false, false, AudioMode.ASK);
        assertThat(gate.playing()).as("asked again").isEqualTo(Effect.SHOW_BAR);

        gate.answer(AudioAnswer.DENY_ONCE);
        gate.document("https://b.test", AudioMode.ASK);
        assertState(gate, false, false, AudioMode.ASK);
        assertThat(gate.origin()).isEqualTo("https://b.test");

        var shown = asked();
        shown.document(SITE, AudioMode.ALLOW);
        assertState(shown, false, false, AudioMode.ALLOW);
        assertThat(shown.answer(AudioAnswer.ALLOW_ALWAYS)).as("the bar the new document hid").isEmpty();
        assertThat(shown.answer(AudioAnswer.DENY_ALWAYS)).isEmpty();
        assertState(shown, false, false, AudioMode.ALLOW);
    }

    @Test void unmuteMakesTheModeAllow() {
        var gate = asked();
        gate.unmute();
        assertState(gate, false, false, AudioMode.ALLOW);
        assertThat(gate.answer(AudioAnswer.DENY_ALWAYS)).as("the bar went with it").isEmpty();

        var denied = asked();
        denied.answer(AudioAnswer.DENY_ONCE);
        denied.unmute();
        assertState(denied, false, false, AudioMode.ALLOW);

        var blocked = new AudioGate();
        blocked.document(SITE, AudioMode.BLOCK);
        blocked.playing();
        blocked.unmute();
        assertState(blocked, false, false, AudioMode.ALLOW);
        assertThat(blocked.playing()).isEqualTo(Effect.NONE);
    }

    /** Before its first document, and for a document without a site, a page is muted without the bar. */
    @Test void beforeItsFirstDocumentAPageIsMutedWithoutTheBar() {
        var gate = new AudioGate();
        assertThat(gate.origin()).isEmpty();
        assertThat(gate.mode()).isEqualTo(AudioMode.BLOCK);
        assertThat(gate.playing()).isEqualTo(Effect.MUTED);
        assertThat(gate.barShown()).isFalse();
    }

    private static AudioGate asking() {
        var gate = new AudioGate();
        gate.document(SITE, AudioMode.ASK);
        return gate;
    }

    /** A gate whose page played in ASK: muted, the bar shown. */
    private static AudioGate asked() {
        var gate = asking();
        assertThat(gate.playing()).isEqualTo(Effect.SHOW_BAR);
        return gate;
    }

    private static void assertState(AudioGate gate, boolean muted, boolean barShown, AudioMode mode) {
        assertThat(gate.muted()).as("muted").isEqualTo(muted);
        assertThat(gate.barShown()).as("bar shown").isEqualTo(barShown);
        assertThat(gate.mode()).as("mode").isEqualTo(mode);
    }
}
