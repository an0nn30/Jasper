package dev.jasper.browser.engine;

import dev.jasper.browser.store.CapturedLog;
import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.callback.CefQueryCallback;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The pages behind {@code jasper-about:}, looked up as Chromium asks for them, without Chromium; and Jasper's pages'
 * channel to Java (plan 5's Task 3): their queries, answered only for the main frame of the page that has them, and
 * the router that takes them from Chromium, played here by the test's browsers, frames and thread.
 */
class AboutSchemeTest {
    private static final String REFUSED = "failure 2 Jasper's pages answer only themselves.";

    @Test void aRegisteredPageAnswersItsHtmlAndAnUnknownOneAnswers404WithAnEmptyBody() {
        var about = new AboutScheme();
        var asked = new AtomicInteger();
        about.register("welcome", () -> "<h1>Welcome " + asked.incrementAndGet() + "</h1>");
        assertThat(about.answer("jasper-about://welcome/")).isEqualTo(new AboutScheme.Answer(200, "<h1>Welcome 1</h1>"));
        assertThat(about.answer("jasper-about:welcome")).as("each request asks the page again")
            .isEqualTo(new AboutScheme.Answer(200, "<h1>Welcome 2</h1>"));
        assertThat(about.answer("JASPER-ABOUT://Welcome/?from=tab#top")).extracting(AboutScheme.Answer::status).isEqualTo(200);
        assertThat(about.answer("jasper-about://missing/")).isEqualTo(new AboutScheme.Answer(404, ""));
        assertThat(about.answer("https://welcome/")).isEqualTo(new AboutScheme.Answer(404, ""));
        about.register("empty", () -> null);
        assertThat(about.answer("jasper-about://empty/")).as("a page with nothing to show").isEqualTo(new AboutScheme.Answer(404, ""));
    }

    @Test void anActionAnswers204WithAnEmptyBodyAndRunsOnTheUiThread() throws Exception {
        var about = new AboutScheme();
        var ran = new java.util.concurrent.CompletableFuture<Boolean>();
        about.registerAction("settings", () -> ran.complete(java.awt.EventQueue.isDispatchThread()));
        assertThat(about.answer("jasper-about://settings/")).isEqualTo(new AboutScheme.Answer(204, ""));
        assertThat(ran.get(10, java.util.concurrent.TimeUnit.SECONDS)).as("on the UI thread").isTrue();
        about.registerAction("fails", () -> { throw new IllegalStateException("closed"); });
        assertThat(about.answer("jasper-about:fails")).extracting(AboutScheme.Answer::status).isEqualTo(204);
        java.awt.EventQueue.invokeAndWait(() -> { }); // a failing action is logged, not thrown on the UI thread
    }

    @Test void aPageOrActionOfTheAddressIsHandedTheAddressWithItsQuery() throws Exception {
        var about = new AboutScheme();
        about.register("error", address -> "<p>" + address + "</p>");
        assertThat(about.answer("jasper-about://error/?url=https%3A%2F%2Fexample.com&code=ERR_FAILED#top"))
            .isEqualTo(new AboutScheme.Answer(200, "<p>jasper-about://error/?url=https%3A%2F%2Fexample.com&code=ERR_FAILED#top</p>"));
        var asked = new java.util.concurrent.CompletableFuture<java.net.URI>();
        about.registerAction("error-retry", asked::complete);
        assertThat(about.answer("jasper-about://error-retry/?token=abc")).isEqualTo(new AboutScheme.Answer(204, ""));
        assertThat(asked.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(java.net.URI.create("jasper-about://error-retry/?token=abc"));
        assertThat(about.answer("jasper-about://error/?url=a b")).as("an address that is no URI").isEqualTo(new AboutScheme.Answer(404, ""));
    }

    @Test void anAddressesParametersAreReadDecoded() {
        String address = "jasper-about://error/?token=abc&url=https%3A%2F%2Fexample.com%2F%3Fq%3D1&subject=CN%3Da&subject=CN%3Db+c&empty=#frag";
        assertThat(AboutScheme.parameter(address, "url")).contains("https://example.com/?q=1");
        assertThat(AboutScheme.parameter(address, "token")).contains("abc");
        assertThat(AboutScheme.parameters(address, "subject")).containsExactly("CN=a", "CN=b c");
        assertThat(AboutScheme.parameter(address, "empty")).contains("");
        assertThat(AboutScheme.parameter(address, "missing")).isEmpty();
        assertThat(AboutScheme.parameter("jasper-about:error?token=abc", "token")).as("written without //").contains("abc");
        assertThat(AboutScheme.parameter("jasper-about://error/#token=abc", "token")).as("a fragment is no query").isEmpty();
        assertThat(AboutScheme.parameter("jasper-about://error/?token=%zz", "token")).as("not decodable").isEmpty();
        assertThat(AboutScheme.page("JASPER-ABOUT://Error/?token=abc")).contains("error");
        assertThat(AboutScheme.page("jasper-about:error-retry?token=abc")).contains("error-retry");
        assertThat(AboutScheme.page("https://error/")).isEmpty();
    }

    @Test void pagesAndActionsShareTheirNames() {
        var about = new AboutScheme();
        about.register("welcome", () -> "");
        about.registerAction("settings", () -> { });
        assertThatThrownBy(() -> about.registerAction("welcome", () -> { })).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:welcome");
        assertThatThrownBy(() -> about.register("settings", () -> "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.registerAction("Settings", () -> { })).isInstanceOf(IllegalArgumentException.class);
    }

    /** P5: a page whose HTML comes later is answered once it arrives, and the thread that asked is not held for it. */
    @Test void aLaterPageIsAnsweredOnceItsHtmlArrivesWithoutHoldingTheThreadThatAsked() throws Exception {
        var about = new AboutScheme();
        var html = new java.util.concurrent.CompletableFuture<String>();
        var asked = new java.util.concurrent.atomic.AtomicReference<java.net.URI>();
        about.registerLater("source", java.time.Duration.ofSeconds(10), address -> {
            asked.set(address);
            return html;
        });
        var answer = about.answerLater("jasper-about://source/?url=https%3A%2F%2Fexample.com");
        assertThat(answer).as("answered later, not on this thread").isNotDone();
        assertThat(asked.get()).isEqualTo(java.net.URI.create("jasper-about://source/?url=https%3A%2F%2Fexample.com"));
        html.complete("<p>source</p>");
        assertThat(answer.get(10, java.util.concurrent.TimeUnit.SECONDS)).isEqualTo(new AboutScheme.Answer(200, "<p>source</p>"));
        assertThat(about.answer("jasper-about://source/")).as("a later page is answered only later").isEqualTo(new AboutScheme.Answer(404, ""));
    }

    @Test void aLaterPageThatFailsGivesNothingOrComesTooLateAnswers404() throws Exception {
        var about = new AboutScheme();
        var slow = new java.util.concurrent.CompletableFuture<String>();
        about.registerLater("failing", java.time.Duration.ofSeconds(10),
            address -> java.util.concurrent.CompletableFuture.failedFuture(new IllegalStateException("broken")));
        about.registerLater("empty", java.time.Duration.ofSeconds(10), address -> java.util.concurrent.CompletableFuture.completedFuture(null));
        about.registerLater("throwing", java.time.Duration.ofSeconds(10), address -> { throw new IllegalStateException("thrown"); });
        about.registerLater("slow", java.time.Duration.ofMillis(100), address -> slow);
        for (String page : new String[] { "failing", "empty", "throwing", "slow" })
            assertThat(about.answerLater("jasper-about://" + page + "/").get(10, java.util.concurrent.TimeUnit.SECONDS)).as(page)
                .isEqualTo(new AboutScheme.Answer(404, ""));
        assertThat(slow).as("given up on, so its work ends").isCancelled();
        assertThat(about.answerLater("jasper-about://slow/?url=a b").get(10, java.util.concurrent.TimeUnit.SECONDS)).as("no URI")
            .isEqualTo(new AboutScheme.Answer(404, ""));
    }

    /** A request that ends while it waits, as when the user goes elsewhere, ends its page's work. */
    @Test void endingTheAnswerCancelsTheLaterPage() {
        var about = new AboutScheme();
        var html = new java.util.concurrent.CompletableFuture<String>();
        about.registerLater("source", java.time.Duration.ofSeconds(10), address -> html);
        about.answerLater("jasper-about:source").cancel(true);
        assertThat(html).isCancelled();
    }

    @Test void pagesAndActionsAnswerLaterAsTheyAnswerNowAndShareTheirNamesWithLaterPages() throws Exception {
        var about = new AboutScheme();
        about.register("welcome", () -> "<h1>Welcome</h1>");
        about.registerAction("settings", () -> { });
        about.registerLater("source", java.time.Duration.ofSeconds(10), address -> java.util.concurrent.CompletableFuture.completedFuture("x"));
        assertThat(about.answerLater("jasper-about://welcome/")).isCompletedWithValue(new AboutScheme.Answer(200, "<h1>Welcome</h1>"));
        assertThat(about.answerLater("jasper-about://settings/")).isCompletedWithValue(new AboutScheme.Answer(204, ""));
        assertThat(about.answerLater("jasper-about://missing/")).isCompletedWithValue(new AboutScheme.Answer(404, ""));
        assertThatThrownBy(() -> about.registerLater("welcome", java.time.Duration.ofSeconds(1), address -> null))
            .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("jasper-about:welcome");
        assertThatThrownBy(() -> about.register("source", () -> "")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.registerAction("source", () -> { })).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.registerLater("Source", java.time.Duration.ofSeconds(1), address -> null))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.registerLater("later", java.time.Duration.ZERO, address -> null)).as("no wait")
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test void aPageNameIsLowercaseLettersDigitsAndHyphensAndIsTakenOnce() {
        var about = new AboutScheme();
        about.register("new-tab2", () -> "");
        for (String name : new String[] { "", "Welcome", "2nd", "a/b", "a.b", "-a" })
            assertThatThrownBy(() -> about.register(name, () -> "")).as(name).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> about.register("new-tab2", () -> "")).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:new-tab2");
    }

    /** A page that throws as it is built is answered 404, as one with nothing to show is, and the failure is logged. */
    @Test void aPageThatThrowsAnswers404() {
        var about = new AboutScheme();
        about.register("broken", () -> { throw new IllegalStateException("no template"); });
        try (var log = new CapturedLog(AboutScheme.class.getName())) {
            assertThat(about.answer("jasper-about://broken/")).isEqualTo(new AboutScheme.Answer(404, ""));
            assertThat(about.answerLater("jasper-about://broken/")).isCompletedWithValue(new AboutScheme.Answer(404, ""));
            assertThat(log.records()).hasSize(2).allSatisfy(record -> {
                assertThat(record.getMessage()).isEqualTo("A jasper-about page failed");
                assertThat(record.getThrown()).hasMessage("no template");
            });
        }
    }

    /**
     * History's queries answer the main frame of history's page, its address written either way, with the page's
     * secret before the request; the answer is handed the asking browser and the request without the secret.
     */
    @Test void aPagesQueriesAnswerItsMainFrameWrittenEitherWay() {
        var about = new AboutScheme();
        about.register("history", () -> "<p>history</p>");
        var asked = new CopyOnWriteArrayList<String>();
        about.registerQueries("history", (browser, request) -> {
            asked.add(browser + " " + request);
            return CompletableFuture.completedFuture("{\"entries\":[]}");
        });
        String secret = about.secret("history").orElseThrow();
        assertThat(secret).as("128 random bits").matches("[0-9a-f]{32}");
        assertThat(about.secret("history")).as("one secret for the page").contains(secret);
        assertThat(about.query("jasper-about:history", true, 7, secret + "{\"op\":\"list\"}"))
            .hasValueSatisfying(answer -> assertThat(answer).isCompletedWithValue("{\"entries\":[]}"));
        assertThat(about.query("jasper-about://history/?q=1", true, 8, secret + "{\"op\":\"clear\"}")).isPresent();
        assertThat(about.query("JASPER-ABOUT://History/", true, 9, secret + "{}")).isPresent();
        assertThat(asked).containsExactly("7 {\"op\":\"list\"}", "8 {\"op\":\"clear\"}", "9 {}");
    }

    /**
     * Review Focus 2: history's queries refuse a web page, a {@code file:} page, the bookmarks page (which has queries
     * of its own and its own secret), any other address, a sub-frame of history's page, a page with no queries, a
     * request without the page's secret and one of 65,537 characters; no answer runs for any of them.
     */
    @Test void aPagesQueriesRefuseEveryOtherAsker() {
        var about = new AboutScheme();
        var asked = new AtomicInteger();
        BiFunction<Integer, String, CompletableFuture<String>> answer = (browser, request) -> {
            asked.incrementAndGet();
            return CompletableFuture.completedFuture("{}");
        };
        for (String page : List.of("history", "bookmarks", "welcome")) about.register(page, () -> "<p>" + page + "</p>");
        about.registerQueries("history", answer);
        about.registerQueries("bookmarks", answer);
        String secret = about.secret("history").orElseThrow();
        String request = secret + "{\"op\":\"clear\"}";
        for (String address : new String[] {"https://evil.example/", "file:///tmp/x.html", "jasper-about:bookmarks", "jasper-about://bookmarks/",
                "jasper-about:welcome", "jasper-about://missing/", "about:blank", "", null, "view-source:jasper-about://history/",
                "blob:jasper-about://history/0b1c", "https://example.com/jasper-about:history"})
            assertThat(about.query(address, true, 1, request)).as(String.valueOf(address)).isEmpty();
        assertThat(about.query("jasper-about://history/", false, 1, request)).as("a sub-frame").isEmpty();
        assertThat(about.query("jasper-about://history/", true, 1, "{\"op\":\"clear\"}")).as("no secret").isEmpty();
        assertThat(about.query("jasper-about://history/", true, 1, about.secret("bookmarks").orElseThrow() + "{\"op\":\"clear\"}"))
            .as("bookmarks' secret").isEmpty();
        assertThat(about.query("jasper-about://history/", true, 1, secret.toUpperCase(java.util.Locale.ROOT) + "{}")).as("not its secret").isEmpty();
        assertThat(about.query("jasper-about://history/", true, 1, secret.substring(1))).as("shorter than a secret").isEmpty();
        assertThat(about.query("jasper-about://history/", true, 1, null)).isEmpty();
        assertThat(about.secret("welcome")).as("a page with no queries").isEmpty();
        String longest = secret + "x".repeat(AboutScheme.LONGEST_REQUEST - secret.length());
        assertThat(longest).hasSize(65_536);
        assertThat(about.query("jasper-about://history/", true, 1, longest + "x")).as("65,537 characters").isEmpty();
        assertThat(asked).as("no answer ran").hasValue(0);
        assertThat(about.query("jasper-about://history/", true, 1, longest)).as("65,536 characters").isPresent();
        assertThat(asked).hasValue(1);
    }

    @Test void anAnswerThatThrowsOrGivesNothingIsAFailedFutureNeverAThrow() {
        var about = new AboutScheme();
        about.register("history", () -> "");
        about.register("bookmarks", () -> "");
        about.registerQueries("history", (browser, request) -> { throw new IllegalStateException("broken"); });
        about.registerQueries("bookmarks", (browser, request) -> null);
        for (String page : List.of("history", "bookmarks"))
            assertThat(about.query("jasper-about:" + page, true, 1, about.secret(page).orElseThrow() + "{}"))
                .as(page).hasValueSatisfying(answer -> assertThat(answer).isCompletedExceptionally());
    }

    @Test void queriesAreRegisteredOnceForAPageAlreadyRegistered() {
        var about = new AboutScheme();
        BiFunction<Integer, String, CompletableFuture<String>> answer = (browser, request) -> CompletableFuture.completedFuture("{}");
        about.register("history", () -> "");
        about.registerAction("settings", () -> { });
        about.registerLater("source", Duration.ofSeconds(1), address -> CompletableFuture.completedFuture(""));
        assertThatThrownBy(() -> about.registerQueries("missing", answer)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:missing");
        assertThatThrownBy(() -> about.registerQueries("settings", answer)).as("an action").isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:settings");
        about.registerQueries("history", answer);
        about.registerQueries("source", answer);
        assertThatThrownBy(() -> about.registerQueries("history", answer)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:history");
        assertThat(about.secret("history")).isPresent().isNotEqualTo(about.secret("source"));
        assertThat(about.secret("settings")).isEmpty();
        assertThat(about.secret("missing")).isEmpty();
    }

    /** Before a page is answered its redirect is asked: an address answers 302 to it with no body, none the page. */
    @Test void aRedirectAnswers302WithNoBodyAndAnEmptyOneGivesThePage() {
        var about = new AboutScheme();
        var built = new AtomicInteger();
        about.register("welcome", () -> "<h1>Welcome " + built.incrementAndGet() + "</h1>");
        var to = new AtomicReference<>(Optional.of("jasper-about:onboarding-intro"));
        about.redirect("welcome", to::get);
        assertThat(about.answer("jasper-about://welcome/"))
            .isEqualTo(new AboutScheme.Answer(302, "", Optional.of("jasper-about:onboarding-intro")));
        assertThat(about.answerLater("jasper-about:welcome"))
            .isCompletedWithValue(new AboutScheme.Answer(302, "", Optional.of("jasper-about:onboarding-intro")));
        assertThat(built).as("the page is not built").hasValue(0);
        to.set(Optional.empty());
        assertThat(about.answer("jasper-about://welcome/")).isEqualTo(new AboutScheme.Answer(200, "<h1>Welcome 1</h1>"));

        var later = new AtomicInteger();
        about.registerLater("source", Duration.ofSeconds(10), address -> {
            later.incrementAndGet();
            return CompletableFuture.completedFuture("<p>source</p>");
        });
        about.redirect("source", () -> Optional.of("jasper-about:welcome"));
        assertThat(about.answerLater("jasper-about://source/")).isCompletedWithValue(new AboutScheme.Answer(302, "", Optional.of("jasper-about:welcome")));
        assertThat(later).hasValue(0);

        about.register("broken", () -> "<p>broken</p>");
        about.redirect("broken", () -> { throw new IllegalStateException("unreadable"); });
        try (var log = new CapturedLog(AboutScheme.class.getName())) {
            assertThat(about.answer("jasper-about:broken")).as("a redirect that fails gives the page")
                .isEqualTo(new AboutScheme.Answer(200, "<p>broken</p>"));
            assertThat(log.records()).singleElement().satisfies(record -> {
                assertThat(record.getMessage()).isEqualTo("A jasper-about page's redirect failed");
                assertThat(record.getThrown()).hasMessage("unreadable");
            });
        }
    }

    @Test void aRedirectIsGivenOnceForAPageAlreadyRegistered() {
        var about = new AboutScheme();
        about.register("welcome", () -> "");
        about.registerAction("settings", () -> { });
        assertThatThrownBy(() -> about.redirect("missing", Optional::empty)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:missing");
        assertThatThrownBy(() -> about.redirect("settings", Optional::empty)).as("an action").isInstanceOf(IllegalArgumentException.class);
        about.redirect("welcome", Optional::empty);
        assertThatThrownBy(() -> about.redirect("welcome", Optional::empty)).isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("jasper-about:welcome");
    }

    /**
     * Plan 5's F8: on Chromium's thread the router refuses, there and then, a persistent query, one too long, and one
     * from a browser that is no page's, as a page's DevTools are not, or from none; nothing of them reaches the
     * background, and no answer runs.
     */
    @Test void theRouterRefusesAtOnceAPersistentOrTooLongQueryAndADevToolsOrOtherBrowser() {
        var about = history();
        var asked = new AtomicInteger();
        about.registerQueries("history", (browser, request) -> {
            asked.incrementAndGet();
            return CompletableFuture.completedFuture("{}");
        });
        String request = about.secret("history").orElseThrow() + "{}";
        CefBrowser page = browser(7), devTools = browser(8), other = browser(9);
        var background = new ArrayDeque<Runnable>();
        var router = new AboutQueries.Router(about, browser -> browser == page, background::add);
        CefFrame main = frame("jasper-about://history/", true);
        var replies = new Replies();
        router.onQuery(page, main, 1, request, true, replies);
        router.onQuery(page, main, 2, request + "x".repeat(AboutScheme.LONGEST_REQUEST), false, replies);
        router.onQuery(page, main, 3, null, false, replies);
        router.onQuery(devTools, main, 4, request, false, replies);
        router.onQuery(other, main, 5, request, false, replies);
        router.onQuery(null, main, 6, request, false, replies);
        router.onQuery(page, null, 7, request, false, replies);
        assertThat(replies.calls).as("each refused there and then").hasSize(7).containsOnly(REFUSED);
        assertThat(background).as("nothing reaches the background").isEmpty();
        assertThat(asked).hasValue(0);
    }

    /**
     * Plan 5's final review: a query from a sub-frame, or from a frame that shows no {@code jasper-about:} address, as a
     * web page's, is refused on Chromium's thread too, before it reaches the background; CEF adds the query function to
     * every frame of every page.
     */
    @Test void theRouterRefusesAtOnceASubFramesQueryAndOneFromAFrameShowingNoJasperPage() {
        var about = history();
        var asked = new AtomicInteger();
        about.registerQueries("history", (browser, request) -> {
            asked.incrementAndGet();
            return CompletableFuture.completedFuture("{}");
        });
        String request = about.secret("history").orElseThrow() + "{}";
        CefBrowser page = browser(7);
        var background = new ArrayDeque<Runnable>();
        var router = new AboutQueries.Router(about, browser -> browser == page, background::add);
        var replies = new Replies();
        router.onQuery(page, frame("https://evil.example/", true), 1, request, false, replies);
        router.onQuery(page, frame("file:///Users/someone/page.html", true), 2, request, false, replies);
        router.onQuery(page, frame(null, true), 3, request, false, replies);
        router.onQuery(page, frame("jasper-about://history/", false), 4, request, false, replies);
        router.onQuery(page, frame("https://evil.example/jasper-about:history", true), 5, request, false, replies);
        assertThat(replies.calls).as("each refused there and then").hasSize(5).containsOnly(REFUSED);
        assertThat(background).as("nothing reaches the background").isEmpty();
        router.onQuery(page, frame("JASPER-ABOUT:history", true), 6, request, false, replies);
        assertThat(background).as("Jasper's page, its scheme in any case, goes on").hasSize(1);
        runOnAnotherThread(background, "jasper-test-background");
        assertThat(asked).hasValue(1);
    }

    /**
     * A page's query is handed to the background, where it is read and answered: never on Chromium's thread. The
     * answer goes back as success; a query its page's queries refuse fails with 2, and an answer that fails with 1 and
     * its reason.
     */
    @Test void aQueryIsAnsweredOnTheBackgroundAndARefusedOrFailedOneFails() {
        var about = history();
        var asked = new CopyOnWriteArrayList<String>();
        about.registerQueries("history", (browser, request) -> {
            asked.add(browser + " " + request + " " + Thread.currentThread().getName());
            return request.equals("{\"op\":\"fail\"}") ? CompletableFuture.failedFuture(new IllegalArgumentException("Jasper does not understand this request."))
                : CompletableFuture.completedFuture("{\"entries\":[]}");
        });
        String secret = about.secret("history").orElseThrow();
        CefBrowser page = browser(7);
        var background = new ArrayDeque<Runnable>();
        var router = new AboutQueries.Router(about, browser -> browser == page, background::add);

        var answered = new Replies();
        assertThat(router.onQuery(page, frame("jasper-about://history/", true), 1, secret + "{\"op\":\"list\"}", false, answered)).isTrue();
        assertThat(asked).as("not read on Chromium's thread").isEmpty();
        assertThat(answered.calls).isEmpty();
        runOnAnotherThread(background, "jasper-test-background");
        assertThat(asked).containsExactly("7 {\"op\":\"list\"} jasper-test-background");
        assertThat(answered.calls).containsExactly("success {\"entries\":[]}");

        var web = new Replies();
        router.onQuery(page, frame("https://evil.example/", true), 2, secret + "{\"op\":\"clear\"}", false, web);
        var subFrame = new Replies();
        router.onQuery(page, frame("jasper-about://history/", false), 3, secret + "{\"op\":\"clear\"}", false, subFrame);
        var failed = new Replies();
        router.onQuery(page, frame("jasper-about://history/", true), 4, secret + "{\"op\":\"fail\"}", false, failed);
        runOnAnotherThread(background, "jasper-test-background");
        assertThat(web.calls).containsExactly(REFUSED);
        assertThat(subFrame.calls).containsExactly(REFUSED);
        assertThat(failed.calls).containsExactly("failure 1 Jasper does not understand this request.");
        assertThat(asked).as("only history's own page asked").hasSize(2);
    }

    /** A query the page cancels, or that ends as the page goes, has its answer cancelled, and is never answered. */
    @Test void aCancelledQueryCancelsItsAnswerAndIsNeverAnswered() {
        var about = history();
        var answers = new ArrayDeque<CompletableFuture<String>>();
        var asked = new AtomicInteger();
        about.registerQueries("history", (browser, request) -> {
            asked.incrementAndGet();
            var answer = new CompletableFuture<String>();
            answers.add(answer);
            return answer;
        });
        String request = about.secret("history").orElseThrow() + "{}";
        CefBrowser page = browser(7);
        var background = new ArrayDeque<Runnable>();
        var router = new AboutQueries.Router(about, browser -> browser == page, background::add);
        CefFrame main = frame("jasper-about://history/", true);

        var waiting = new Replies();
        router.onQuery(page, main, 1, request, false, waiting);
        runOnAnotherThread(background, "jasper-test-background");
        CompletableFuture<String> answer = answers.remove();
        router.onQueryCanceled(page, main, 1);
        assertThat(answer).as("its work ends").isCancelled();
        answer.complete("{}");
        assertThat(waiting.calls).as("never answered").isEmpty();

        var early = new Replies();
        router.onQuery(page, main, 2, request, false, early);
        router.onQueryCanceled(page, null, 2);
        runOnAnotherThread(background, "jasper-test-background");
        assertThat(asked).as("cancelled before it was read: no answer runs").hasValue(1);
        assertThat(early.calls).isEmpty();
        router.onQueryCanceled(page, main, 99); // one never asked: nothing to cancel
    }

    /** A page {@code history}, with no queries yet. */
    private static AboutScheme history() {
        var about = new AboutScheme();
        about.register("history", () -> "<p>history</p>");
        return about;
    }

    /** Runs the tasks handed to the background, in order, on a thread of their own called {@code name}. */
    private static void runOnAnotherThread(Queue<Runnable> tasks, String name) {
        Thread thread = Thread.ofPlatform().name(name).start(() -> {
            for (Runnable task; (task = tasks.poll()) != null; ) task.run();
        });
        try {
            thread.join(10_000);
        } catch (InterruptedException interrupted) {
            throw new AssertionError(interrupted);
        }
        assertThat(thread.isAlive()).as("the background's tasks ended").isFalse();
    }

    /** A browser Chromium knows by {@code identifier}. */
    private static CefBrowser browser(int identifier) {
        return (CefBrowser) Proxy.newProxyInstance(CefBrowser.class.getClassLoader(), new Class<?>[] {CefBrowser.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getIdentifier" -> identifier;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** A frame showing {@code url}: its browser's main frame or one of its sub-frames. */
    private static CefFrame frame(String url, boolean main) {
        return (CefFrame) Proxy.newProxyInstance(CefFrame.class.getClassLoader(), new Class<?>[] {CefFrame.class},
            (proxy, method, args) -> switch (method.getName()) {
                case "getURL" -> url;
                case "isMain" -> main;
                case "equals" -> proxy == args[0];
                case "hashCode" -> System.identityHashCode(proxy);
                default -> throw new UnsupportedOperationException(method.getName());
            });
    }

    /** A query's callback, recording {@code success <answer>} or {@code failure <code> <reason>}. */
    private static final class Replies implements CefQueryCallback {
        final List<String> calls = new CopyOnWriteArrayList<>();

        @Override public void success(String response) {
            calls.add("success " + response);
        }

        @Override public void failure(int code, String message) {
            calls.add("failure " + code + " " + message);
        }
    }
}
