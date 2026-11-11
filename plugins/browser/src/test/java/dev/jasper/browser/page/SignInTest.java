package dev.jasper.browser.page;

import dev.jasper.browser.store.CapturedLog;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.LogRecord;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 8 Task 5a: an HTTP sign-in Chromium asks for is answered exactly once, as Chromium's request waits until it is:
 * a proceed or a cancel, whichever comes first, even from two threads at once, and a callback that throws is contained
 * and logged with the exception alone. Its values are Chromium's, the scheme in lower case. The callbacks are recording
 * stand-ins for JCEF's; no Chromium.
 */
class SignInTest {
    private final List<String> told = new CopyOnWriteArrayList<>();

    @Test void aProceedThenACancelReachChromiumOnce() {
        SignIn signIn = signIn();
        assertThat(signIn.answered()).isFalse();
        assertThat(signIn.proceed("u", "pw")).isTrue();
        assertThat(signIn.answered()).isTrue();
        assertThat(signIn.cancel()).isFalse();
        assertThat(signIn.proceed("u", "other")).isFalse();
        assertThat(told).containsExactly("continue u pw");
    }

    @Test void aCancelThenAProceedReachChromiumOnce() {
        SignIn signIn = signIn();
        assertThat(signIn.cancel()).isTrue();
        assertThat(signIn.proceed("u", "pw")).isFalse();
        assertThat(signIn.cancel()).isFalse();
        assertThat(told).containsExactly("cancel");
    }

    /** Two threads answering at once, a proceed and a cancel, reach Chromium once between them, round after round. */
    @Test void twoThreadsAnsweringAtOnceReachChromiumOnce() throws Exception {
        for (int round = 0; round < 200; round++) {
            var answers = new AtomicInteger();
            SignIn signIn = new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "Basic",
                "https://intranet.example/", (user, password) -> answers.incrementAndGet(), answers::incrementAndGet);
            var start = new CountDownLatch(1);
            var won = new AtomicInteger();
            Thread proceeding = new Thread(() -> {
                await(start);
                if (signIn.proceed("u", "pw")) won.incrementAndGet();
            }), cancelling = new Thread(() -> {
                await(start);
                if (signIn.cancel()) won.incrementAndGet();
            });
            proceeding.start();
            cancelling.start();
            start.countDown();
            proceeding.join();
            cancelling.join();
            assertThat(answers).as("round %d", round).hasValue(1);
            assertThat(won).as("round %d", round).hasValue(1);
        }
    }

    /**
     * F29: a callback that throws is contained: the sign-in counts as answered, says Chromium was not told, and is
     * logged with the exception alone, never the user name or password.
     */
    @Test void aThrowingCallbackIsContainedAndLoggedWithoutTheSecret() {
        try (var log = new CapturedLog(SignIn.class.getName())) {
            SignIn proceeding = new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "basic", "",
                (user, password) -> { throw new IllegalStateException("No native browser"); }, () -> { });
            assertThat(proceeding.proceed("u", "pw")).isFalse();
            assertThat(proceeding.answered()).isTrue();
            assertThat(proceeding.cancel()).isFalse();
            SignIn cancelling = new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "basic", "",
                (user, password) -> { }, () -> { throw new IllegalStateException("No native browser"); });
            assertThat(cancelling.cancel()).isFalse();
            assertThat(cancelling.answered()).isTrue();
            assertThat(log.records()).hasSize(2).allSatisfy(record -> {
                assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
                assertThat(text(record)).doesNotContain("pw", "Corp", "intranet.example");
            });
        }
    }

    @Test void itKeepsChromiumsValuesTheSchemeInLowerCase() {
        SignIn signIn = new SignIn("https://intranet.example/", true, "proxy.example", 8118, "Corp", "NTLM",
            "https://intranet.example/start", (user, password) -> { }, () -> { });
        assertThat(signIn.originUrl()).isEqualTo("https://intranet.example/");
        assertThat(signIn.proxy()).isTrue();
        assertThat(signIn.host()).isEqualTo("proxy.example");
        assertThat(signIn.port()).isEqualTo(8118);
        assertThat(signIn.realm()).isEqualTo("Corp");
        assertThat(signIn.scheme()).isEqualTo("ntlm");
        assertThat(signIn.topAddress()).isEqualTo("https://intranet.example/start");
    }

    @Test void aNullTextReadsAsEmpty() {
        SignIn signIn = new SignIn(null, false, null, 80, null, null, null, (user, password) -> { }, () -> { });
        assertThat(List.of(signIn.originUrl(), signIn.host(), signIn.realm(), signIn.scheme(), signIn.topAddress()))
            .containsOnly("");
    }

    /** F5 and F12: whether Chromium named an origin alone or the request's full address (Task 8 records which). */
    @Test void anOriginUrlIsAnOriginAloneWithNoPathButASlashAndNoQuery() {
        for (String origin : List.of("https://intranet.example", "https://intranet.example/", "http://intranet.example:8080/",
                "https://intranet.example/#top", "https://[::1]:8443"))
            assertThat(signInFrom(origin).originOnly()).as(origin).isTrue();
        for (String address : List.of("https://intranet.example/x", "https://intranet.example/?q=1", "https://intranet.example?q",
                "https://intranet.example//", "intranet.example", ""))
            assertThat(signInFrom(address).originOnly()).as(address).isFalse();
    }

    private SignIn signIn() {
        return new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "Basic", "https://intranet.example/",
            (user, password) -> told.add("continue " + user + " " + password), () -> told.add("cancel"));
    }

    private static SignIn signInFrom(String originUrl) {
        return new SignIn(originUrl, false, "intranet.example", 443, "", "basic", "", (user, password) -> { }, () -> { });
    }

    /** The record's text, its parameters and its exception's message included. */
    private static String text(LogRecord record) {
        return record.getMessage() + " " + java.util.Arrays.toString(record.getParameters()) + " "
            + (record.getThrown() == null ? "" : record.getThrown().getMessage());
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
