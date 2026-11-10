package dev.jasper.browser.page;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Whether a page's next download asks where it goes (spec section 10, ruling F32): Save Link As… and Save Image As…
 * arm it with the address they download, and the ask is taken once, by the page's first start of that address within
 * 10 s of arming, else by its first start within 1 s of arming, as a redirect changes the address Chromium gives. Pure:
 * no Chromium, no clock but the times given.
 */
class DownloadAskTest {
    private static final String LINK = "https://example.com/report.pdf", OTHER = "https://example.com/tracker.bin";

    @Test void itAsksOnceWithinTenSeconds() {
        var ask = new DownloadAsk();
        assertThat(ask.take(LINK, 0)).as("not armed").isFalse();
        ask.arm(LINK, 1_000);
        assertThat(ask.take(LINK, 1_005)).isTrue();
        assertThat(ask.take(LINK, 1_006)).as("once").isFalse();
        ask.arm(LINK, 1_000);
        assertThat(ask.take(LINK, 11_000)).as("10 s after arming").isTrue();
        ask.arm(LINK, 1_000);
        assertThat(ask.take(LINK, 11_001)).as("10,001 ms after arming").isFalse();
        assertThat(ask.take(LINK, 1_001)).as("an ask that lapsed is gone").isFalse();
    }

    /** Ruling F32: a start of another address past the first second takes nothing, and the armed address's start asks. */
    @Test void theArmedAddressBeatsANearerStartOfAnotherAddress() {
        var ask = new DownloadAsk();
        ask.arm(LINK, 0);
        assertThat(ask.take(OTHER, 2_000)).as("a page's own download meanwhile goes without asking").isFalse();
        assertThat(ask.take(LINK, 5_000)).isTrue();
        assertThat(ask.take(LINK, 5_001)).isFalse();
    }

    /** Ruling F32: within a second of arming, any start takes the ask, as a redirect changes the address. */
    @Test void anyStartWithinASecondOfArmingTakesIt() {
        var ask = new DownloadAsk();
        ask.arm(LINK, 0);
        assertThat(ask.take(OTHER, 999)).isTrue();
        assertThat(ask.take(LINK, 1_000)).as("taken already").isFalse();
        ask.arm(LINK, 0);
        assertThat(ask.take(OTHER, 1_000)).as("a second after arming").isTrue();
        ask.arm(LINK, 0);
        assertThat(ask.take(OTHER, 1_001)).isFalse();
        assertThat(ask.take(LINK, 1_002)).as("still armed for its own address").isTrue();
    }

    /** The address matches but for its fragment, which no request carries; a newer arming replaces the one before. */
    @Test void theAddressMatchesButForItsFragmentAndANewerArmingReplacesTheOneBefore() {
        var ask = new DownloadAsk();
        ask.arm(LINK + "#page=2", 0);
        assertThat(ask.take(LINK, 5_000)).isTrue();
        ask.arm(LINK, 0);
        ask.arm(OTHER, 3_000);
        assertThat(ask.take(LINK, 5_000)).as("replaced").isFalse();
        assertThat(ask.take(OTHER, 5_000)).isTrue();
        ask.arm(LINK, 10_000);
        assertThat(ask.take(LINK, 9_999)).as("a time before the arming").isFalse();
        assertThat(ask.take(null, 10_500)).as("a start without an address, within the second").isTrue();
    }
}
