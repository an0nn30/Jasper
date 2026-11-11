package dev.jasper.browser.page;

import dev.jasper.browser.store.CapturedLog;
import dev.jasper.sdk.Subscription;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Plan 8 Task 5a (F18): a page's part in HTTP sign-ins. Its top address is that of its main frame's latest navigation
 * request, after its redirects, and its committed address before the first; each sign-in Chromium asks for goes to who
 * listens, and is cancelled at once when no one does, when the page has closed, or when its listener throws, so no
 * request waits on nothing; and each final main-frame response is told until the close. The page has no browser, and
 * the sign-ins' callbacks are recording stand-ins for JCEF's.
 */
class PageSignInsTest {
    private final List<String> told = new ArrayList<>();

    @Test void theTopAddressIsTheLatestMainFrameRequestAfterItsRedirectsElseTheCommittedOne() throws Exception {
        onUiThread(() -> {
            Page page = page();
            assertThat(page.topAddress()).as("before any navigation commits").isEmpty();
            PageTestAccess.addressChanged(page, "https://start.example/");
            assertThat(page.topAddress()).as("before any request").isEqualTo("https://start.example/");
            page.mainFrameRequested("http://intranet.example/");
            assertThat(page.topAddress()).as("a request").isEqualTo("http://intranet.example/");
            page.mainFrameRequested("https://intranet.example/");
            assertThat(page.topAddress()).as("its redirect").isEqualTo("https://intranet.example/");
            PageTestAccess.addressChanged(page, "https://intranet.example/home");
            assertThat(page.topAddress()).as("a commit is no request").isEqualTo("https://intranet.example/");
            page.mainFrameRequested(null);
            assertThat(page.topAddress()).as("no address ignored").isEqualTo("https://intranet.example/");
        });
    }

    /** F12: the top address is noted on Chromium's IO thread, and read there. */
    @Test void theTopAddressIsNotedAndReadOnAnyThread() throws Exception {
        var made = new AtomicReference<Page>();
        onUiThread(() -> made.set(page()));
        Thread chromium = new Thread(() -> made.get().mainFrameRequested("https://intranet.example/"));
        chromium.start();
        chromium.join();
        assertThat(made.get().topAddress()).isEqualTo("https://intranet.example/");
    }

    @Test void aSignInGoesToWhoListensUnanswered() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var heard = new ArrayList<SignIn>();
            Subscription listening = page.onSignIn(heard::add);
            SignIn signIn = signIn();
            page.signIn(signIn);
            assertThat(heard).containsExactly(signIn);
            assertThat(signIn.answered()).isFalse();
            assertThat(told).isEmpty();
            listening.close();
            SignIn next = signIn();
            page.signIn(next);
            assertThat(heard).as("no longer listening").hasSize(1);
            assertThat(next.answered()).isTrue();
            assertThat(told).containsExactly("cancel");
        });
    }

    @Test void aSignInNoOneListensForIsCancelledAtOnce() throws Exception {
        onUiThread(() -> {
            Page page = page();
            SignIn signIn = signIn();
            page.signIn(signIn);
            assertThat(signIn.answered()).isTrue();
            assertThat(told).containsExactly("cancel");
        });
    }

    @Test void aSignInOfAClosedPageIsCancelled() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var heard = new ArrayList<SignIn>();
            page.onSignIn(heard::add);
            page.close();
            page.onSignIn(heard::add);
            SignIn signIn = signIn();
            page.signIn(signIn);
            assertThat(heard).isEmpty();
            assertThat(told).containsExactly("cancel");
        });
    }

    /**
     * A listener that throws is logged, with the exception alone, and its sign-in cancelled, so it waits on nothing.
     */
    @Test void aThrowingListenerCancelsTheSignIn() throws Exception {
        onUiThread(() -> {
            try (var log = new CapturedLog(PageLoginHooks.class.getName())) {
                Page page = page();
                page.onSignIn(signIn -> { throw new IllegalStateException("The dialog could not show"); });
                SignIn signIn = signIn();
                page.signIn(signIn);
                assertThat(told).containsExactly("cancel");
                assertThat(log.records()).singleElement().satisfies(record -> {
                    assertThat(record.getThrown()).isInstanceOf(IllegalStateException.class);
                    assertThat(record.getMessage()).doesNotContain("Corp", "intranet.example");
                });
            }
        });
    }

    @Test void eachFinalMainFrameResponseIsToldUntilTheClose() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var responses = new ArrayList<String>();
            page.onMainFrameResponse((url, status) -> responses.add(status + " " + url));
            page.mainFrameResponded("https://intranet.example/", 401);
            page.mainFrameResponded("https://intranet.example/home", 200);
            assertThat(responses).containsExactly("401 https://intranet.example/", "200 https://intranet.example/home");
            page.close();
            page.mainFrameResponded("https://intranet.example/", 200);
            page.onMainFrameResponse((url, status) -> responses.add("after the close"));
            page.mainFrameResponded("https://intranet.example/", 200);
            assertThat(responses).as("nobody is told after the close").hasSize(2);
        });
    }

    private SignIn signIn() {
        return new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "basic", "https://intranet.example/",
            (user, password) -> told.add("continue " + user), () -> told.add("cancel"));
    }

    private static Page page() {
        return new Page(URI.create("https://start.example/"), () -> { }, PageTestAccess.noFavicons(), Runnable::run,
            PageContext.persistent());
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new AtomicReference<Throwable>();
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure.set(thrown);
            }
        });
        if (failure.get() instanceof Error error) throw error;
        if (failure.get() instanceof Exception exception) throw exception;
    }
}
