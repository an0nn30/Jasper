package dev.jasper.browser.engine;

import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The pages behind {@code jasper-about:}, looked up as Chromium asks for them, without Chromium. */
class AboutSchemeTest {
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
}
