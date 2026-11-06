package dev.jasper.browser.pages;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.store.HistoryStore;
import dev.jasper.browser.store.MemoryStores;
import dev.jasper.browser.store.Reached;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** {@code jasper-about:welcome}: the redirect to the onboarding until it is finished, Top Sites and the Quick Actions. */
class WelcomePageTest {
    @TempDir Path folder;
    private final MemoryStores memory = new MemoryStores();
    private final HistoryStore history = memory.make().history();
    private PagesFixture fixture;
    private Onboarding onboarding;

    @BeforeEach void register() throws Exception {
        fixture = new PagesFixture();
        onboarding = new Onboarding(folder.resolve("onboarding-finished"), Runnable::run);
        WelcomePage.register(fixture.about, fixture.pages, browser -> browser == 1 ? Optional.of(history) : Optional.empty(), onboarding);
    }

    @AfterEach void close() throws Exception {
        fixture.close();
        memory.close();
    }

    @Test void theWelcomePageRedirectsToTheIntroUntilTheOnboardingIsFinishedThenServesWelcomeBack() {
        assertThat(WelcomePage.NAME).isEqualTo("welcome");
        assertThat(fixture.about.answer("jasper-about://welcome/"))
            .isEqualTo(new dev.jasper.browser.engine.AboutScheme.Answer(302, "", Optional.of("jasper-about:onboarding-intro")));
        onboarding.finish();
        String html = fixture.html("welcome");
        assertThat(html).contains("<title>Welcome</title>", "<h1>Welcome Back</h1>", "Top Sites", "Refresh", "Quick Actions")
            .doesNotContain("{{");
    }

    @Test void theQuickActionsAreBookmarksHistoryAndSettings() {
        onboarding.finish();
        assertThat(fixture.html("welcome")).contains("<a class=\"tile\" href=\"jasper-about:bookmarks\">Bookmarks</a>",
            "<a class=\"tile\" href=\"jasper-about:history\">History</a>", "<a class=\"tile\" href=\"jasper-about:settings\">Settings</a>");
    }

    @Test void thePagesScriptComesAfterAboutJs() {
        onboarding.finish();
        assertThat(fixture.html("welcome")).contains(AboutPages.resource("welcome.js").strip()).contains("'top-sites'");
    }

    /** Plan 6: Top Sites come from the asking browser's history, and a browser with none is refused its answer. */
    @Test void topSitesComeFromTheAskingBrowsersHistoryAndABrowserWithNoneFails() throws Exception {
        history.record("https://site.example/", Reached.LINK, 1_000).get(10, TimeUnit.SECONDS);
        assertThat(sites(fixture.object("welcome", "{\"op\":\"top-sites\"}"))).extracting(site -> site.get("url"))
            .containsExactly("https://site.example/");
        String secret = fixture.about.secret("welcome").orElseThrow();
        CompletableFuture<String> answer = fixture.about.query("jasper-about:welcome", true, 2, secret + "{\"op\":\"top-sites\"}").orElseThrow();
        assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).isInstanceOf(ExecutionException.class)
            .hasCauseInstanceOf(IllegalStateException.class);
    }

    @Test void topSitesGiveAtMostEightInTheOrderOfTheHistorysTopSites() throws Exception {
        long last = 0;
        for (int site = 1; site <= 10; site++)
            for (int visit = 0; visit < site; visit++)
                last = history.record("https://site" + site + ".example/", Reached.LINK, 1_000L * site + visit).get(10, TimeUnit.SECONDS).orElseThrow().entry();
        history.retitle(last, "Ten").get(10, TimeUnit.SECONDS);
        List<HistoryStore.Visit> top = history.topSites(8).get(10, TimeUnit.SECONDS);
        Map<String, Object> answer = fixture.object("welcome", "{\"op\":\"top-sites\"}");
        assertThat(answer).containsOnlyKeys("sites");
        List<Map<String, Object>> sites = sites(answer);
        assertThat(sites).extracting(site -> site.get("url")).containsExactlyElementsOf(top.stream().map(HistoryStore.Visit::url).toList())
            .hasSize(8);
        assertThat(sites.getFirst()).isEqualTo(Map.of("url", "https://site10.example/", "title", "Ten", "host", "site10.example"));
    }

    @Test void aSiteWithoutATitleGivesItsHostAndAnAddressWithoutAHostItself() throws Exception {
        history.record("https://user:secret@example.com:8443/path", Reached.LINK, 1_000).get(10, TimeUnit.SECONDS);
        history.record("file:///Users/ada/notes.html", Reached.LINK, 500).get(10, TimeUnit.SECONDS);
        List<Map<String, Object>> sites = sites(fixture.object("welcome", "{\"op\":\"top-sites\"}"));
        assertThat(sites).extracting(site -> site.toString()).noneMatch(site -> site.contains("secret"));
        assertThat(sites).extracting(site -> site.get("host")).contains("example.com", "file:///Users/ada/notes.html");
    }

    @Test void aHostileTitleReadsBackExactly() throws Exception {
        String title = "</script><img src=x onerror=alert(1)>" + (char) 0x2028 + "\"'\\" + (char) 0x2029;
        long entry = history.record("https://a.example/", Reached.LINK, 1_000).get(10, TimeUnit.SECONDS).orElseThrow().entry();
        history.retitle(entry, title).get(10, TimeUnit.SECONDS);
        assertThat(sites(fixture.object("welcome", "{\"op\":\"top-sites\"}"))).singleElement()
            .satisfies(site -> assertThat(site).isEqualTo(Map.of("url", "https://a.example/", "title", title, "host", "a.example")));
    }

    @Test void noHistoryGivesNoSites() throws Exception {
        assertThat(sites(fixture.object("welcome", "{\"op\":\"top-sites\"}"))).isEmpty();
    }

    @Test void anythingElseIsRefusedWithOneReason() {
        for (String request : new String[] {null, "", "not json", "[]", "{}", "{\"op\":\"list\"}", "{\"op\":7}", "{\"op\":\"clear\"}",
            "{\"op\":\"top-sites\",\"extra\":[]}", "{\"op\":\"top-sites\",\"op\":\"top-sites\"}"}) {
            CompletableFuture<String> answer = WelcomePage.answer(history, request);
            assertThatThrownBy(() -> answer.get(10, TimeUnit.SECONDS)).as(String.valueOf(request)).isInstanceOf(ExecutionException.class)
                .cause().isInstanceOf(IllegalArgumentException.class).hasMessage("Jasper does not understand this request.");
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> sites(Map<String, Object> answer) {
        return (List<Map<String, Object>>) answer.get("sites");
    }
}
