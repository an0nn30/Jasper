package dev.jasper.browser.engine;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.cef.callback.CefCallback;
import org.cef.misc.BoolRef;
import org.cef.misc.IntRef;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Chromium's handler of one {@code jasper-about:} request (P5), driven as Chromium's IO thread drives it, without
 * Chromium: an answer ready at once is handled at once, and one still to come lets {@code open} return and continues
 * the request once it arrives, never after the request was cancelled.
 */
class AboutSchemeFactoryTest {
    @Test void anAnswerReadyAtOnceIsHandledAtOnceAndReadAsUtf8() {
        var handler = new AboutSchemeFactory.Page(CompletableFuture.completedFuture(new AboutScheme.Answer(200, "<p>café</p>")));
        var handle = new BoolRef();
        var continued = new Calls();
        assertThat(handler.open(null, handle, continued)).isTrue();
        assertThat(handle.get()).as("handled at once").isTrue();
        assertThat(read(handler)).isEqualTo("<p>café</p>");
        assertThat(continued.count()).isZero();
    }

    @Test void anAnswerStillToComeLetsOpenReturnAndContinuesTheRequestOnceItArrives() throws Exception {
        var later = new CompletableFuture<AboutScheme.Answer>();
        var handler = new AboutSchemeFactory.Page(later);
        var handle = new BoolRef(true);
        var continued = new Calls();
        assertThat(handler.open(null, handle, continued)).isTrue();
        assertThat(handle.get()).as("decided later").isFalse();
        assertThat(continued.count()).isZero();
        later.complete(new AboutScheme.Answer(200, "<p>source</p>"));
        assertThat(continued.awaitContinue()).isTrue();
        assertThat(read(handler)).isEqualTo("<p>source</p>");
        assertThat(continued.count()).as("once").isEqualTo(1);
    }

    @Test void aRequestCancelledWhileItWaitsIsNeverContinuedAndItsAnswerIsCancelled() throws Exception {
        var later = new CompletableFuture<AboutScheme.Answer>();
        var handler = new AboutSchemeFactory.Page(later);
        var continued = new Calls();
        assertThat(handler.open(null, new BoolRef(), continued)).isTrue();
        handler.cancel();
        assertThat(later).isCancelled();
        assertThat(continued.awaitContinue()).as("never continued").isFalse();
    }

    /** The body, read as Chromium reads it, a few bytes at a time. */
    private static String read(AboutSchemeFactory.Page handler) {
        var body = new byte[0];
        var chunk = new byte[4];
        var count = new IntRef();
        while (handler.read(chunk, chunk.length, count, null)) {
            int at = body.length;
            body = Arrays.copyOf(body, at + count.get());
            System.arraycopy(chunk, 0, body, at, count.get());
        }
        return new String(body, StandardCharsets.UTF_8);
    }

    /** Chromium's callback for a request decided later, counting its Continues. */
    private static final class Calls implements CefCallback {
        private final AtomicInteger continues = new AtomicInteger();
        private final CountDownLatch continued = new CountDownLatch(1);

        @Override public void Continue() {
            continues.incrementAndGet();
            continued.countDown();
        }

        @Override public void cancel() {
            throw new AssertionError("Jasper cancels no request itself");
        }

        int count() {
            return continues.get();
        }

        /** Whether the request was continued within a second. */
        boolean awaitContinue() throws InterruptedException {
            return continued.await(1, TimeUnit.SECONDS);
        }
    }
}
