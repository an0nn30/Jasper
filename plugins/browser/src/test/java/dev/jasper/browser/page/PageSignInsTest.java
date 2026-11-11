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
 * request, after its redirects, or of its latest commit, whichever came last (L23), as Chromium's threads note them; a
 * request that fails or becomes a download falls back to the commit before it; each sign-in Chromium asks for goes to
 * who listens, and is cancelled at once when no one does, when the page has closed, or when its listener throws, so
 * no request waits on nothing; and each final main-frame response is told until the close. The page has no browser,
 * and the sign-ins' callbacks are recording stand-ins for JCEF's.
 */
class PageSignInsTest {
    private final List<String> told = new ArrayList<>();

    @Test void theTopAddressIsTheLatestMainFrameRequestAfterItsRedirectsOrTheLatestCommit() throws Exception {
        onUiThread(() -> {
            Page page = page();
            assertThat(page.topAddress()).as("before any navigation commits").isEmpty();
            PageTestAccess.addressChanged(page, "https://start.example/");
            assertThat(page.topAddress()).as("before any request").isEqualTo("https://start.example/");
            page.mainFrameRequested("http://intranet.example/");
            assertThat(page.topAddress()).as("a request").isEqualTo("http://intranet.example/");
            page.mainFrameRedirected("https://intranet.example/");
            assertThat(page.topAddress()).as("its redirect").isEqualTo("https://intranet.example/");
            committed(page, "https://intranet.example/home");
            assertThat(page.topAddress()).as("a commit").isEqualTo("https://intranet.example/home");
            page.mainFrameRequested(null);
            committed(page, null);
            assertThat(page.topAddress()).as("no address ignored").isEqualTo("https://intranet.example/home");
        });
    }

    /**
     * L23: a commit with no request of its own, as a document restored from the back/forward cache might make, is the
     * top address, so the restored document never inherits the request before it.
     */
    @Test void aCommitWithoutARequestIsTheTopAddress() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.mainFrameRequested("https://bank.example/account");
            committed(page, "https://bank.example/account");
            committed(page, "https://other.example/");
            assertThat(page.topAddress()).isEqualTo("https://other.example/");
        });
    }

    /** F6's chain, then its commit: the request, its redirect, and the address it committed. */
    @Test void aRedirectedRequestThenItsCommit() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.mainFrameRequested("http://intranet.example/");
            page.mainFrameRedirected("https://intranet.example/");
            assertThat(page.topAddress()).isEqualTo("https://intranet.example/");
            committed(page, "https://intranet.example/");
            assertThat(page.topAddress()).isEqualTo("https://intranet.example/");
            page.mainFrameRequested("https://intranet.example/next");
            assertThat(page.topAddress()).as("the next request").isEqualTo("https://intranet.example/next");
        });
    }

    /**
     * A sign-in keeps the top address it was asked with: a main frame's own challenge comes before its commit, so the
     * commit that follows changes nothing it carries; and two sign-ins with no commit between carry the same address.
     */
    @Test void aSignInKeepsTheTopAddressOfItsRequest() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.mainFrameRequested("https://intranet.example/private");
            SignIn first = signIn(page.topAddress()), second = signIn(page.topAddress());
            committed(page, "https://intranet.example/private");
            committed(page, "https://other.example/");
            assertThat(List.of(first.topAddress(), second.topAddress())).containsOnly("https://intranet.example/private");
        });
    }

    /**
     * L24: a main-frame request that never commits, as one that fails or becomes a download, is the top address no
     * more: it falls back to the commit before it. One that fails after the next request was noted leaves that one.
     */
    @Test void aRequestThatFailsOrBecomesADownloadFallsBackToTheCommitBeforeIt() throws Exception {
        onUiThread(() -> {
            Page page = page();
            page.mainFrameRequested("https://bank.example/");
            assertThat(page.topAddress()).isEqualTo("https://bank.example/");
            PageEvents.loadError(page, frame(true), null, "https://bank.example/", task -> { });
            assertThat(page.topAddress()).as("no commit yet").isEmpty();
            committed(page, "https://start.example/");
            page.mainFrameRequested("https://unknown.example/");
            PageEvents.loadError(page, frame(true), null, "https://unknown.example/", task -> { });
            assertThat(page.topAddress()).as("a failure").isEqualTo("https://start.example/");
            page.mainFrameRequested("https://files.example/report.pdf");
            PageTestAccess.downloadStart(page, 1, 7, "https://files.example/report.pdf", "report.pdf", "https://start.example/", 0);
            assertThat(page.topAddress()).as("a download").isEqualTo("https://start.example/");
            page.mainFrameRequested("https://slow.example/");
            page.mainFrameRequested("https://next.example/");
            PageEvents.loadError(page, frame(true), null, "https://slow.example/", task -> { });
            PageTestAccess.downloadStart(page, 1, 8, "https://files.example/other.pdf", "other.pdf", "https://start.example/", 0);
            PageEvents.loadError(page, frame(false), null, "https://next.example/", task -> { });
            assertThat(page.topAddress()).as("another address's end, or a sub-frame's").isEqualTo("https://next.example/");
        });
    }

    /**
     * L35: each main-frame navigation request notes whether the page had a user activation as it started, read and not
     * consumed: one within 5 s of the page's press or key has it, a later one has not, and a redirect keeps its
     * navigation's, as does the number, read together with it.
     */
    @Test void aNavigationNotesWhetherThePagesActivationBeganIt() throws Exception {
        onUiThread(() -> {
            Page page = page();
            assertThat(page.latestNavigation()).as("before any").isEqualTo(Page.Navigation.NONE);
            PageTestAccess.activate(page, 1_000);
            PageTestAccess.mainFrameRequested(page, "https://intranet.example/", 6_000);
            assertThat(page.latestNavigation()).as("within the activation")
                .isEqualTo(new Page.Navigation(1, true, false));
            assertThat(page.activation().active(6_000)).as("not consumed").isTrue();
            page.mainFrameRedirected("https://intranet.example/home");
            assertThat(page.latestNavigation()).as("its redirect").isEqualTo(new Page.Navigation(1, true, false));
            PageTestAccess.mainFrameRequested(page, "https://intranet.example/next", 6_001);
            assertThat(page.latestNavigation()).as("after it").isEqualTo(new Page.Navigation(2, false, false));
            page.mainFrameRedirected("https://intranet.example/last");
            assertThat(page.latestNavigation()).as("its redirect").isEqualTo(new Page.Navigation(2, false, false));
            assertThat(page.navigation()).isEqualTo(2);
        });
    }

    /**
     * L42: Jasper's navigation marks the page's next main-frame request, if it comes within 10 s: that navigation is
     * Jasper's, and its redirect keeps it, but the request after it, as the page's own, is not. A mark no request takes
     * within 10 s lapses, and a main frame's address change, load start or load failure before a request drops it.
     */
    @Test void jaspersNavigationMarksItsOwnRequestAlone() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var millis = new java.util.concurrent.atomic.AtomicLong(1_000_000);
            PageTestAccess.useClock(page, millis::get);
            page.load(URI.create("https://intranet.example/"));
            millis.addAndGet(10_000);
            page.mainFrameRequested("https://intranet.example/");
            assertThat(page.latestNavigation()).as("within 10 s").isEqualTo(new Page.Navigation(1, false, true));
            page.mainFrameRedirected("https://intranet.example/home");
            assertThat(page.latestNavigation()).as("its redirect").isEqualTo(new Page.Navigation(1, false, true));
            page.mainFrameRequested("https://intranet.example/other");
            assertThat(page.latestNavigation()).as("the page's own request after it")
                .isEqualTo(new Page.Navigation(2, false, false));
            page.load(URI.create("https://intranet.example/"));
            millis.addAndGet(10_001);
            page.mainFrameRequested("https://intranet.example/");
            assertThat(page.latestNavigation().byJasper()).as("lapsed").isFalse();
            var drops = new java.util.LinkedHashMap<String, Runnable>();
            drops.put("an address change", () -> committed(page, "https://intranet.example/#top"));
            drops.put("a load start", page::mainFrameStarting);
            drops.put("a load failure",
                () -> PageEvents.loadError(page, frame(true), null, "https://intranet.example/x", task -> { }));
            for (var drop : drops.entrySet()) {
                page.load(URI.create("https://intranet.example/#top"));
                drop.getValue().run();
                page.mainFrameRequested("https://intranet.example/");
                assertThat(page.latestNavigation().byJasper()).as(drop.getKey()).isFalse();
            }
        });
    }

    /**
     * L42: an activation begins a navigation only when it came after the main frame last started loading a document,
     * so the click that brought a document begins none of that document's own navigations; the page's activation is
     * left as it was, for its popups and downloads.
     */
    @Test void anActivationFromBeforeTheLastLoadStartBeginsNoNavigation() throws Exception {
        onUiThread(() -> {
            Page page = page();
            var millis = new java.util.concurrent.atomic.AtomicLong(1_000_000);
            PageTestAccess.useClock(page, millis::get);
            PageTestAccess.activate(page, 1_000_000); // the click on a link
            millis.set(1_000_100);
            page.mainFrameRequested("https://intranet.example/");
            assertThat(page.latestNavigation().activated()).as("the link's navigation").isTrue();
            millis.set(1_000_300);
            page.mainFrameStarting();
            millis.set(1_001_000);
            page.mainFrameRequested("https://intranet.example/refresh");
            assertThat(page.latestNavigation().activated()).as("the new document's own, within 5 s").isFalse();
            assertThat(page.activation().active(1_001_000)).as("the activation itself").isTrue();
            PageTestAccess.activate(page, 1_002_000); // a click in the new document
            millis.set(1_002_100);
            page.mainFrameRequested("https://intranet.example/next");
            assertThat(page.latestNavigation().activated()).isTrue();
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

    /** The main frame commits {@code url}, as Chromium's display handler tells it, its hand-off run at once. */
    private static void committed(Page page, String url) {
        PageEvents.addressChanged(page, frame(true), url, Runnable::run);
    }

    /** A frame that answers only whether it is the main one. */
    private static org.cef.browser.CefFrame frame(boolean main) {
        return (org.cef.browser.CefFrame) java.lang.reflect.Proxy.newProxyInstance(org.cef.browser.CefFrame.class.getClassLoader(),
            new Class<?>[] {org.cef.browser.CefFrame.class}, (proxy, method, args) -> {
                if (method.getName().equals("isMain")) return main;
                throw new UnsupportedOperationException(method.getName());
            });
    }

    private SignIn signIn(String topAddress) {
        return new SignIn("https://intranet.example/private", false, "intranet.example", 443, "Corp", "basic", topAddress,
            0, false, false, (user, password) -> told.add("continue " + user), () -> told.add("cancel"));
    }

    private SignIn signIn() {
        return new SignIn("https://intranet.example/", false, "intranet.example", 443, "Corp", "basic",
            "https://intranet.example/", 0, false, false, (user, password) -> told.add("continue " + user),
            () -> told.add("cancel"));
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
