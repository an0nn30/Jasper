package dev.jasper.browser.tools;

import dev.jasper.sdk.ui.MenuItem;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * One page's tools (spec sections 6 and 10, plan 7's Task 5): reader mode's offer and state as its page's loads and its
 * scripts' reports drive them, over a recording stand-in for the page. Review Focus 5: a load start resets the modes and
 * the offer; a page with 10 characters of reader text, a {@code jasper-about:} page or a malformed report offers
 * nothing. F24: no check on a challenge address or a hidden page, and a same-document address change checks again
 * once it has settled for a second.
 */
class PageToolsTest {
    private static final String ARTICLE = "https://news.example/2026/article", CSS = ".reader { --text: #222; }";
    private final List<String> scripts = new ArrayList<>(), urls = new ArrayList<>();
    private final List<Delayed> waiting = new ArrayList<>();
    private boolean showing = true;
    private int changes;
    private final PageTools tools = new PageTools((script, url) -> {
        scripts.add(script);
        urls.add(url);
    }, () -> CSS, () -> 150, () -> showing, this::later, () -> changes++);

    private record Delayed(Duration delay, Runnable task) { }

    @Test void elevenCharactersOfferTheItemsAndTenDoNot() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        assertThat(scripts).containsExactly(ToolScripts.readerCheck(1));
        assertThat(urls).containsExactly(ToolScripts.READER_URL);
        length(1, 10);
        assertThat(tools.offered()).isFalse();
        assertThat(tools.menuItems()).isEmpty();
        assertThat(changes).isZero();
        length(1, 11);
        assertThat(tools.offered()).isTrue();
        assertThat(tools.menuItems()).isNotEmpty();
        assertThat(changes).isEqualTo(1);
        length(1, 0);
        assertThat(tools.offered()).as("a later answer withdraws it").isFalse();
        assertThat(changes).isEqualTo(2);
        assertThat(scripts).as("answers run nothing").hasSize(1);
    }

    /** Jasper's own pages, in any case, get no check, and their reports offer nothing. */
    @Test void aJasperAboutPageRunsNoCheck() {
        for (String page : List.of("jasper-about:welcome", "JASPER-ABOUT:history", "Jasper-About:source?token=1")) {
            tools.loadStarted(page);
            tools.loaded();
            tools.shown();
            length(1, 500);
            tools.toggleReader();
        }
        assertThat(scripts).isEmpty();
        assertThat(tools.offered()).isFalse();
        assertThat(tools.reading()).isFalse();
        tools.loadStarted(ARTICLE);
        tools.loaded();
        assertThat(scripts).as("the next page's").containsExactly(ToolScripts.readerCheck(1));
    }

    /** F24: Jasper's page scripts stay out of challenge frames, a main frame's too. */
    @Test void aChallengeAddressRunsNoCheck() {
        tools.loadStarted("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile");
        tools.loaded();
        tools.addressChanged("https://challenges.cloudflare.com/cdn-cgi/challenge-platform/h/b/turnstile#next");
        end();
        assertThat(scripts).isEmpty();
        assertThat(waiting).isEmpty();
    }

    /**
     * Review Focus 5: a load start ends reader mode and distraction removal and withdraws the offer, without a script, as
     * the overlay went with the document; the old document's late answer and its Escape then change nothing.
     */
    @Test void aLoadStartResetsEverything() {
        offer();
        tools.toggleReader();
        assertThat(tools.reading()).isTrue();
        int ran = scripts.size(), changed = changes;
        tools.loadStarted("https://news.example/next");
        assertThat(tools.offered()).isFalse();
        assertThat(tools.reading()).isFalse();
        assertThat(tools.removing()).isFalse();
        assertThat(tools.menuItems()).isEmpty();
        assertThat(changes).isEqualTo(changed + 1);
        assertThat(scripts).as("no script for a document that has gone").hasSize(ran);
        length(1, 400);
        reported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
        assertThat(tools.offered()).isFalse();
        assertThat(changes).isEqualTo(changed + 1);
        tools.loaded();
        assertThat(scripts.getLast()).as("the new document's check has a token of its own").isEqualTo(ToolScripts.readerCheck(2));
    }

    @Test void theToggleRunsShowThenHide() {
        offer();
        tools.toggleReader();
        assertThat(scripts.getLast()).isEqualTo(ToolScripts.readerShow(CSS, 1));
        assertThat(urls.getLast()).isEqualTo(ToolScripts.READER_URL);
        assertThat(tools.reading()).isTrue();
        assertThat(labels()).first().isEqualTo("Exit Reader Mode");
        tools.toggleReader();
        assertThat(scripts.getLast()).isEqualTo(ToolScripts.readerHide());
        assertThat(tools.reading()).isFalse();
        assertThat(labels()).first().isEqualTo("Reader Mode");
        assertThat(scripts).hasSize(3);
        assertThat(changes).isEqualTo(3);
    }

    /** Without the offer, or before any check, the toggle shows nothing. */
    @Test void theToggleDoesNothingWhileNothingIsOffered() {
        tools.toggleReader();
        tools.loadStarted(ARTICLE);
        tools.loaded();
        length(1, 3);
        tools.toggleReader();
        assertThat(scripts).containsExactly(ToolScripts.readerCheck(1));
        assertThat(tools.reading()).isFalse();
        assertThat(changes).isZero();
    }

    /** Escape in the page, as {@code reader.js} reports it, ends reader mode, and the menu's label follows. */
    @Test void aReportedExitEndsReaderMode() {
        offer();
        tools.toggleReader();
        int ran = scripts.size(), changed = changes;
        reported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
        assertThat(tools.reading()).isFalse();
        assertThat(tools.offered()).isTrue();
        assertThat(labels()).first().isEqualTo("Reader Mode");
        assertThat(changes).isEqualTo(changed + 1);
        assertThat(scripts).as("the overlay closed itself").hasSize(ran);
        reported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
        assertThat(changes).as("once").isEqualTo(changed + 1);
    }

    /** Spec section 6: Reader Mode, Remove Distractions and a separator, while the offer stands or a mode is on. */
    @Test void theLabelsAndTheirOrderAndNoneWhileNothingIsOffered() {
        assertThat(tools.menuItems()).isEmpty();
        offer();
        assertThat(tools.menuItems()).extracting(MenuItem::id, MenuItem::label, MenuItem::action, MenuItem::enabled, MenuItem::separator)
            .containsExactly(tuple(PageTools.READER, "Reader Mode", PageTools.READER, true, false),
                tuple(PageTools.DISTRACTIONS, "Remove Distractions", PageTools.DISTRACTIONS, true, false),
                tuple("browser.tools.separator", "", "", true, true));
        assertThat(PageTools.READER).isEqualTo("browser.reader");
        assertThat(PageTools.DISTRACTIONS).isEqualTo("browser.distractions");
        tools.toggleReader();
        assertThat(labels()).containsExactly("Exit Reader Mode", "Remove Distractions", "");
    }

    /** R38: what a page sends is untrusted, so any report but the three shapes, for the op it came as, is ignored. */
    @Test void aMalformedReportIsIgnored() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        String padded = "{\"op\":\"reader\",\"check\":1,\"length\":99" + " ".repeat(PageTools.REPORT_LIMIT) + "}";
        for (String json : List.of("", "{", "[]", "null", "{}", "{\"op\":\"reader\"}", "{\"op\":\"reader\",\"length\":99}",
                "{\"op\":\"reader\",\"check\":\"1\",\"length\":99}", "{\"op\":\"reader\",\"check\":1,\"length\":\"99\"}",
                "{\"op\":\"reader\",\"check\":1,\"length\":99.5}", "{\"op\":\"reader\",\"check\":1,\"length\":-99}",
                "{\"op\":\"reader\",\"check\":1,\"length\":99,\"title\":\"x\"}", "{\"op\":\"reader\",\"check\":1,\"length\":{\"n\":99}}",
                "{\"op\":\"reader\",\"check\":1,\"length\":99,\"state\":\"off\"}", "{\"op\":\"distractions\",\"check\":1,\"length\":99}",
                "{\"check\":1,\"length\":99}", "{\"op\":\"reader\",\"check\":1,\"length\":99", padded,
                "{\"op\":\"reader\",\"op\":\"reader\",\"check\":1,\"length\":99}"))
            reported("reader", json);
        reported("distractions", "{\"op\":\"reader\",\"check\":1,\"length\":99}");
        reported("savePage", "{\"op\":\"savePage\",\"check\":1,\"length\":99}");
        reported(null, "{\"op\":\"reader\",\"check\":1,\"length\":99}");
        reported("reader", null);
        assertThat(tools.offered()).isFalse();
        assertThat(changes).isZero();
        length(1, 400);
        assertThat(tools.offered()).as("the check's own answer").isTrue();
        tools.toggleReader();
        int changed = changes;
        for (String json : List.of("{\"op\":\"reader\",\"check\":1,\"state\":\"on\"}", "{\"op\":\"reader\",\"check\":1,\"state\":\"OFF\"}",
                "{\"op\":\"reader\",\"state\":\"off\"}", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\",\"x\":true}",
                "{\"op\":\"reader\",\"check\":2,\"state\":\"off\"}", "{\"op\":\"reader\",\"check\":0,\"state\":\"off\"}"))
            reported("reader", json);
        assertThat(tools.reading()).as("still reading").isTrue();
        assertThat(changes).isEqualTo(changed);
    }

    /** Review Focus 5: each check has a token, and only the latest check's answer counts, so a late one offers nothing. */
    @Test void anOlderChecksAnswerIsIgnored() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        tools.addressChanged(ARTICLE + "?page=2");
        end();
        assertThat(scripts).containsExactly(ToolScripts.readerCheck(1), ToolScripts.readerCheck(2));
        length(1, 400);
        assertThat(tools.offered()).isFalse();
        length(2, 400);
        assertThat(tools.offered()).isTrue();
    }

    /**
     * F24: a same-document address change, as a single-page app's route change, withdraws the offer and checks again a
     * second after the last change; one before the load ends checks nothing, as the load end will.
     */
    @Test void anAddressChangeChecksAgainOnceItSettles() {
        tools.loadStarted(ARTICLE);
        tools.addressChanged(ARTICLE);
        assertThat(waiting).as("the document's own commit").isEmpty();
        tools.loaded();
        length(1, 400);
        tools.addressChanged(ARTICLE + "#comments");
        assertThat(tools.offered()).isFalse();
        assertThat(tools.menuItems()).isEmpty();
        assertThat(waiting).extracting(Delayed::delay).containsExactly(Duration.ofSeconds(1));
        tools.addressChanged("https://news.example/2026/other");
        assertThat(waiting).as("the wait starts again").extracting(Delayed::delay).containsExactly(Duration.ofSeconds(1));
        length(1, 400);
        assertThat(tools.offered()).as("the check before the change answers late").isFalse();
        assertThat(scripts).hasSize(1);
        end();
        assertThat(scripts).containsExactly(ToolScripts.readerCheck(1), ToolScripts.readerCheck(2));
        length(2, 400);
        assertThat(tools.offered()).isTrue();
    }

    @Test void aLoadStartEndsTheWait() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        tools.addressChanged(ARTICLE + "#top");
        assertThat(waiting).hasSize(1);
        tools.loadStarted("https://news.example/next");
        assertThat(waiting).isEmpty();
        assertThat(scripts).hasSize(1);
    }

    /** F24: a page that is not showing, as a background tab's, is checked once it shows, and once. */
    @Test void aHiddenPageIsCheckedWhenItNextShows() {
        showing = false;
        tools.loadStarted(ARTICLE);
        tools.loaded();
        tools.addressChanged(ARTICLE + "#a");
        end();
        assertThat(scripts).isEmpty();
        showing = true;
        tools.shown();
        assertThat(scripts).containsExactly(ToolScripts.readerCheck(1));
        tools.shown();
        assertThat(scripts).as("once").hasSize(1);
        showing = false;
        tools.loadStarted("https://news.example/next");
        tools.loaded();
        tools.loadStarted("https://news.example/third");
        showing = true;
        tools.shown();
        assertThat(scripts).as("not for a document that has gone before its load ended").hasSize(1);
    }

    /** Reader mode outlives an address change in its document: Exit Reader Mode stays, and Escape still ends it. */
    @Test void readerModeOutlivesAnAddressChangeInItsDocument() {
        offer();
        tools.toggleReader();
        tools.addressChanged(ARTICLE + "#section-2");
        assertThat(tools.offered()).isFalse();
        assertThat(labels()).containsExactly("Exit Reader Mode", "Remove Distractions", "");
        end();
        reported("reader", "{\"op\":\"reader\",\"check\":1,\"state\":\"off\"}");
        assertThat(tools.reading()).isFalse();
        assertThat(tools.menuItems()).isEmpty();
    }

    @Test void aDistractionsExitWhileNoneRunsChangesNothing() {
        offer();
        int changed = changes;
        reported("distractions", "{\"op\":\"distractions\",\"check\":1,\"state\":\"off\"}");
        assertThat(tools.removing()).isFalse();
        assertThat(changes).isEqualTo(changed);
    }

    /** The item closes: the wait ends, and nothing runs or changes after. */
    @Test void closingEndsTheWaitAndEverythingAfter() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        length(1, 400);
        tools.addressChanged(ARTICLE + "#b");
        int changed = changes;
        tools.close();
        assertThat(waiting).isEmpty();
        tools.loadStarted(ARTICLE);
        tools.loaded();
        tools.shown();
        length(1, 400);
        tools.toggleReader();
        assertThat(scripts).hasSize(1);
        assertThat(changes).isEqualTo(changed);
    }

    /** An article page whose first check answered 400 characters. */
    private void offer() {
        tools.loadStarted(ARTICLE);
        tools.loaded();
        length(1, 400);
        assertThat(tools.offered()).isTrue();
    }

    private void length(long check, long length) {
        reported("reader", "{\"op\":\"reader\",\"check\":" + check + ",\"length\":" + length + "}");
    }

    private void reported(String op, String json) {
        tools.reported(op, json);
    }

    private List<String> labels() {
        return tools.menuItems().stream().map(MenuItem::label).toList();
    }

    private Runnable later(Duration delay, Runnable task) {
        var delayed = new Delayed(delay, task);
        waiting.add(delayed);
        return () -> waiting.remove(delayed);
    }

    /** Each delay waiting ends, and its task runs. */
    private void end() {
        var due = List.copyOf(waiting);
        waiting.clear();
        due.forEach(delayed -> delayed.task().run());
    }
}
