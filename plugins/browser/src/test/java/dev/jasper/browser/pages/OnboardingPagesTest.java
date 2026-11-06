package dev.jasper.browser.pages;

import dev.jasper.browser.pages.OnboardingPages.SetupSwitch;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The three onboarding pages: the intro and the features' copy, the features per platform, the setup's switches and
 * the one request, {@code finish}, that only the setup page may make.
 */
class OnboardingPagesTest {
    @TempDir Path folder;
    private PagesFixture fixture;
    private Onboarding onboarding;

    @BeforeEach void open() throws Exception {
        fixture = new PagesFixture();
        onboarding = new Onboarding(folder.resolve("onboarding-finished"), Runnable::run);
    }

    @AfterEach void close() throws Exception {
        fixture.close();
    }

    private OnboardingPages register(boolean macOs, List<SetupSwitch> switches) {
        var pages = new OnboardingPages(fixture.pages, fixture.about, onboarding, macOs, switches, Runnable::run);
        pages.register();
        return pages;
    }

    @Test void theIntroHoldsItsCopyAndGetStartedLeadsToTheFeatures() {
        register(true, List.of());
        assertThat(fixture.html("onboarding-intro")).contains("<h1>Meet the Jasper Browser</h1>",
            "While not intended as a primary browser, Jasper's web capabilities provide a useful tool for integrated terminal and web workflows.",
            "<a class=\"button primary\" href=\"jasper-about:onboarding-features\">Get Started</a>").doesNotContain("{{");
    }

    @Test void theFeaturesPageHasItsHeadingAndBackAndContinue() {
        register(true, List.of());
        assertThat(fixture.html("onboarding-features")).contains("<h1>What Can It Do?</h1>",
            "<a class=\"button\" href=\"jasper-about:onboarding-intro\">Back</a>",
            "<a class=\"button primary\" href=\"jasper-about:onboarding-setup\">Continue</a>").doesNotContain("{{");
    }

    @Test void everyFeatureRowShowsWithCmdOnMacOsAndCtrlElsewhere() throws Exception {
        assertThat(OnboardingPages.FEATURES).extracting(OnboardingPages.Feature::title).containsExactly("Pages beside your terminals",
            "Links from the terminal", "An address field that searches", "Find in page", "Bookmarks and history",
            "Snippets in page fields", "Copy on select");
        for (boolean macOs : new boolean[] {true, false}) {
            try (var other = new PagesFixture()) {
                var pages = new OnboardingPages(other.pages, other.about, onboarding, macOs, List.of(), Runnable::run);
                pages.register();
                List<Object> rows = rows(other.object("onboarding-features", "{\"op\":\"features\"}"), "features");
                assertThat(rows).hasSize(OnboardingPages.FEATURES.size());
                for (int at = 0; at < rows.size(); at++) {
                    var feature = OnboardingPages.FEATURES.get(at);
                    assertThat(rows.get(at)).isEqualTo(Map.of("title", feature.title(), "text", macOs ? feature.macOs() : feature.other()));
                }
                String key = macOs ? "Cmd" : "Ctrl";
                assertThat(((Map<?, ?>) rows.get(1)).get("text")).isEqualTo(key + "-click a link in a terminal to open it in a browser tab, or "
                    + key + "+Shift-click to open it in a split. Open Links From Terminal, in Settings › Tools › Browser, chooses whether "
                    + "Jasper asks first or sends links to your default browser.");
                assertThat(rows.get(5)).isEqualTo(Map.of("title", "Snippets in page fields",
                    "text", "Insert a snippet into the page field you are typing in."));
                assertThat(rows.get(6)).isEqualTo(Map.of("title", "Copy on select",
                    "text", "With Copy on select on, selecting text in a page copies it."));
            }
        }
    }

    @Test void theSetupPageHoldsItsCopyAndStartBrowsingFinishes() {
        register(true, List.of());
        String html = fixture.html("onboarding-setup");
        assertThat(html).contains("<h1>Let’s Get You Set Up</h1>", "You can change these at any time in Settings › Tools › Browser.",
            "<a class=\"button\" href=\"jasper-about:onboarding-features\">Back</a>", "Start Browsing", "'finish'", "location.replace(")
            .doesNotContain("{{");
        assertThat(AboutPages.resource("onboarding-setup.js")).contains("jasper-about:welcome");
    }

    /** With no switch the page has none to explain: the note is hidden until the page's script finds one. */
    @Test void theSettingsNoteIsHiddenUntilThereIsASwitch() throws Exception {
        register(true, List.of());
        assertThat(fixture.html("onboarding-setup")).containsPattern("<p[^>]*id=\"note\"[^>]*hidden");
        assertThat(fixture.object("onboarding-setup", "{\"op\":\"switches\"}")).containsEntry("switches", List.of());
    }

    @Test void aSwitchShowsItsStateAndSwitchCallsSet() throws Exception {
        var on = new AtomicBoolean(true);
        var calls = new ArrayList<Boolean>();
        register(false, List.of(new SetupSwitch("ad-blocker", "Ad Blocker", "Blocks ads.", on::get, value -> { calls.add(value); on.set(value); })));
        List<Object> switches = rows(fixture.object("onboarding-setup", "{\"op\":\"switches\"}"), "switches");
        assertThat(switches).containsExactly(Map.of("id", "ad-blocker", "title", "Ad Blocker", "description", "Blocks ads.", "on", true));
        assertThat(fixture.object("onboarding-setup", "{\"op\":\"switch\",\"id\":\"ad-blocker\",\"on\":\"false\"}")).containsEntry("on", false);
        assertThat(calls).containsExactly(false);
        assertThat(on).isFalse();
        assertThat(fixture.object("onboarding-setup", "{\"op\":\"switch\",\"id\":\"ad-blocker\",\"on\":\"true\"}")).containsEntry("on", true);
        assertThat(calls).containsExactly(false, true);
        for (String request : new String[] {"{\"op\":\"switch\",\"id\":\"missing\",\"on\":\"true\"}", "{\"op\":\"switch\",\"id\":\"ad-blocker\",\"on\":\"yes\"}",
            "{\"op\":\"switch\",\"id\":\"ad-blocker\",\"on\":true}", "{\"op\":\"switch\",\"on\":\"true\"}", "{\"op\":\"switch\",\"id\":\"ad-blocker\"}"}) {
            var answer = fixture.about.query("jasper-about:onboarding-setup", true, 1, fixture.about.secret("onboarding-setup").orElseThrow() + request).orElseThrow();
            assertThat(answer).as(request).failsWithin(java.time.Duration.ofSeconds(10)).withThrowableThat()
                .withCauseInstanceOf(IllegalArgumentException.class).withMessageContaining("Jasper does not understand this request.");
        }
        assertThat(calls).as("a refused request changes nothing").containsExactly(false, true);
    }

    /** The switch runs on the executor given for the UI thread, not on the thread that asked. */
    @Test void switchesAreReadAndSetOnTheGivenUiExecutor() throws Exception {
        var ran = new AtomicReference<Thread>();
        var pages = new OnboardingPages(fixture.pages, fixture.about, onboarding, true,
            List.of(new SetupSwitch("a", "A", "B", () -> true, value -> { })), task -> { ran.set(Thread.currentThread()); task.run(); });
        pages.register();
        fixture.ask("onboarding-setup", "{\"op\":\"switches\"}");
        assertThat(ran.get()).isNotNull();
    }

    @Test void finishWorksFromTheSetupPageAndIsRefusedFromTheIntroAndTheFeatures() throws Exception {
        register(true, List.of());
        assertThat(fixture.ask("onboarding-intro", "{\"op\":\"finish\"}")).as("the intro has no queries").isEmpty();
        assertThat(onboarding.finished()).isFalse();
        var features = fixture.about.query("jasper-about:onboarding-features", true, 1,
            fixture.about.secret("onboarding-features").orElseThrow() + "{\"op\":\"finish\"}").orElseThrow();
        assertThat(features).failsWithin(java.time.Duration.ofSeconds(10));
        assertThat(onboarding.finished()).isFalse();
        assertThat(fixture.ask("onboarding-setup", "{\"op\":\"finish\"}")).contains("{}");
        assertThat(onboarding.finished()).isTrue();
        assertThat(folder.resolve("onboarding-finished")).exists();
    }

    @SuppressWarnings("unchecked")
    private static List<Object> rows(Map<String, Object> answer, String key) {
        return (List<Object>) answer.get(key);
    }
}
