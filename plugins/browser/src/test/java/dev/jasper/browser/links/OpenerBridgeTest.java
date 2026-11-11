package dev.jasper.browser.links;

import dev.jasper.browser.json.StrictJson;
import dev.jasper.browser.links.Isolation.OpenerPolicy;
import dev.jasper.browser.links.Isolation.Sandbox;
import dev.jasper.browser.links.OpenerBridge.Answer;
import dev.jasper.browser.links.OpenerBridge.Party;
import dev.jasper.browser.links.OpenerBridge.QueryId;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.Favicons;
import dev.jasper.browser.page.LastPress;
import dev.jasper.browser.page.Page;
import dev.jasper.browser.page.PageContext;
import dev.jasper.browser.page.PageFind;
import dev.jasper.browser.page.PageQuery;
import dev.jasper.browser.page.PageTestAccess;
import dev.jasper.browser.store.CapturedLog;
import java.awt.EventQueue;
import java.io.InputStream;
import java.lang.ref.WeakReference;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The opener bridge's Java side (spec section 8; plan 4a Task 8). Each page's document is a recording stand-in that
 * takes the scripts Jasper would run in it, so no Chromium is needed: what each request opens, delivers, closes or
 * refuses, and what each page is given as it starts loading. Every request is untrusted, and the bridge keeps
 * Chromium's limits: only a page's top frame may ask, and only so often; an {@code open} opens only what the page could
 * open itself and needs and consumes Jasper's own record of the user's activation; the other requests need the asking
 * page to be one end of the link, and {@code focus} its activation while it shows; message data is bounded and reaches
 * the other page only as an escaped string literal; a sandboxed document's origin is opaque; and a link ends as
 * Chromium's window groups part, on the popup's close, the user's navigation or an opener policy. Pages run on the UI
 * thread, as Jasper's do.
 */
class OpenerBridgeTest {
    private static final long NOW = 1_000_000;
    // The pages here fetch no favicon: the bridge's part ends where a page is told its document's icon.
    private static final Favicons NO_FAVICONS = PageTestAccess.noFavicons();
    private static final String APP = "https://app.example/start", LOGIN = "https://login.example/sign-in";
    /** What {@code login-fields.js} sends as the user clicks a password field's button (plan 8). */
    private static final String PICK = "{\"op\":\"login.pick\",\"field\":\"password\",\"x\":8,\"y\":16,\"width\":200,\"height\":24}";
    private final OpenerBridge bridge = new OpenerBridge();
    // The ids Chromium gives the queries the tests answer, each its own.
    private long queries;

    @Test void anOpenWithTheUsersActivationMakesALinkAndOpensANextTabItem() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            Answer answer = ask(opener, open(LOGIN));
            assertThat(answer.ok()).as(answer.toString()).isTrue();
            assertThat(answer.text()).as("the link's id").matches("[1-9][0-9]*");
            assertThat(opener.opened).containsExactly("NEXT_TAB " + LOGIN);
            assertThat(opener.activation.active(NOW)).as("the open consumed the activation").isFalse();
            assertThat(bridge.links()).isEqualTo(1);

            var popup = opener.popups.getFirst();
            assertThat(bridge.loadStarted(popup.party(), LOGIN)).as("the popup's window.opener")
                .isEqualTo(OpenerBridge.SOURCE + "\n({\"opener\":\"" + answer.text() + "\",\"closed\":false});");
            assertThat(bridge.loadStarted(opener.party(), APP)).as("every other page gets the window.open stand-in")
                .isEqualTo(OpenerBridge.SOURCE + "\n({});");

            assertThat(ask(opener, open(LOGIN)).code()).as("one popup per gesture").isEqualTo(OpenerBridge.REFUSED);
            assertThat(opener.opened).hasSize(1);
        });
    }

    @Test void anOpenWithoutJaspersActivationOpensNothing() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            assertThat(ask(opener, open(LOGIN)).code()).as("never activated").isEqualTo(OpenerBridge.REFUSED);
            opener.activation.activate(NOW - 5_001);
            assertThat(ask(opener, open(LOGIN)).code()).as("activated 5,001 ms ago").isEqualTo(OpenerBridge.REFUSED);
            assertThat(opener.opened).isEmpty();
            assertThat(bridge.links()).isZero();
        });
    }

    /**
     * The bridge opens with Jasper's rights, so it opens only what the page could open itself, as Chromium's own check
     * does: web addresses and about:blank from any page, local files only from a local file, and Jasper's own pages
     * (whose actions act in Jasper) only from Jasper's own pages. A refusal keeps the activation.
     */
    @Test void anOpenReachesOnlyWhatThePageCouldOpenItselfAndARefusalKeepsTheActivation() throws Exception {
        onUiThread(() -> {
            var web = new Document(APP);
            web.activation.activate(NOW - 100);
            for (String url : List.of("file:///etc/passwd", "FILE:///tmp/", "jasper-about:settings", "jasper-about:welcome",
                    "about:settings", "about:version", "about:srcdoc", "chrome://settings", "javascript:alert(1)",
                    "mailto:someone@example.com", "data:text/html,hi", "blob:https://app.example/0f", "ftp://example.com/"))
                assertThat(ask(web, open(url)).code()).as(url).isEqualTo(OpenerBridge.REFUSED);
            assertThat(web.opened).isEmpty();
            assertThat(web.activation.active(NOW)).as("refusals consume nothing").isTrue();
            assertThat(ask(web, open("about:blank")).ok()).isTrue();
            web.activation.activate(NOW - 100);
            assertThat(ask(web, open("http://plain.example/")).ok()).isTrue();

            var file = new Document("file:///tmp/app.html");
            file.activation.activate(NOW - 100);
            assertThat(ask(file, open("jasper-about:welcome")).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(ask(file, open("file:///tmp/next.html")).ok()).isTrue();

            var jasper = new Document("jasper-about:welcome");
            jasper.activation.activate(NOW - 100);
            assertThat(ask(jasper, open("file:///tmp/next.html")).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(ask(jasper, open("jasper-about:welcome")).ok()).isTrue();
            assertThat(web.opened).containsExactly("NEXT_TAB about:blank", "NEXT_TAB http://plain.example/");
            assertThat(file.opened).containsExactly("NEXT_TAB file:///tmp/next.html");
            assertThat(jasper.opened).containsExactly("NEXT_TAB jasper-about:welcome");
        });
    }

    /** {@code window.open(url, name, "noopener")}: a next tab with no link, for the same activation. */
    @Test void aNoopenerOpenOpensANextTabItemWithoutALink() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            Answer answer = ask(opener, "{\"op\":\"open\",\"url\":\"" + LOGIN + "\",\"noopener\":true}");
            assertThat(answer.ok()).isTrue();
            assertThat(answer.text()).isEmpty();
            assertThat(opener.opened).containsExactly("NEXT_TAB " + LOGIN);
            assertThat(bridge.links()).isZero();
            assertThat(bridge.loadStarted(opener.popups.getFirst().party(), LOGIN)).endsWith("\n({});");
            assertThat(ask(opener, "{\"op\":\"open\",\"url\":\"" + LOGIN + "\",\"noopener\":true}").code()).as("consumed")
                .isEqualTo(OpenerBridge.REFUSED);
        });
    }

    @Test void aSubFramesRequestsAreRefused() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.opener.activation.activate(NOW - 100);
            Answer open = bridge.request(link.opener.party(), false, APP, open(LOGIN), NOW);
            assertThat(open.code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.opener.activation.active(NOW)).as("a refused frame consumes nothing").isTrue();
            assertThat(bridge.request(link.popup.party(), false, LOGIN, post(link.id, "*", "1"), NOW).code())
                .isEqualTo(OpenerBridge.REFUSED);
            assertThat(bridge.request(link.opener.party(), false, APP, "{\"op\":\"close\",\"id\":\"" + link.id + "\"}", NOW).code())
                .isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.opener.scripts).isEmpty();
            assertThat(link.popup.scripts).isEmpty();
            assertThat(link.popup.closes).isZero();
            assertThat(link.opener.opened).hasSize(1);
        });
    }

    /**
     * On Chromium's thread, before anything reaches the UI thread: a sub-frame's query, one too long to read and one
     * beyond the eight a page may have waiting are answered at once.
     */
    @Test void aQueryIsRefusedOnChromiumsThreadFromASubFrameWhenTooLongOrWhenTooManyWait() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP).page;
            var other = new Document(LOGIN).page;
            assertThat(bridge.admit(page, false, open(LOGIN))).hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.admit(page, true, null)).hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.MALFORMED));
            assertThat(bridge.admit(page, true, "x".repeat(2 * OpenerBridge.MESSAGE_LIMIT + 4_097)))
                .hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.TOO_LARGE));
            assertThat(bridge.waiting()).as("nothing refused waits").isZero();
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) assertThat(bridge.admit(page, true, "{}")).isEmpty();
            assertThat(bridge.admit(page, true, "{}")).hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.BUSY));
            assertThat(bridge.admit(other, true, "{}")).as("another page's queries wait apart").isEmpty();
            bridge.settled(page, true);
            assertThat(bridge.admit(page, true, "{}")).as("one answered, one more may wait").isEmpty();
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) bridge.settled(page, true);
            bridge.settled(other, true);
            assertThat(bridge.waiting()).isZero();
        });
    }

    /**
     * A page's sub-frames, which may send only {@code find}, wait apart from its main frame: a cross-origin frame that
     * floods the bridge keeps no {@code window.open} or {@code postMessage} of the page waiting.
     */
    @Test void aPagesSubFramesWaitApartFromItsMainFrame() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP).page;
            String find = findAnswer(1, 0);
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) assertThat(bridge.admit(page, false, find)).isEmpty();
            assertThat(bridge.admit(page, false, find)).hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.BUSY));
            assertThat(bridge.admit(page, true, open(LOGIN))).as("the main frame's own").isEmpty();
            bridge.settled(page, false);
            assertThat(bridge.admit(page, false, find)).as("a sub-frame's answered, one more may wait").isEmpty();
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) bridge.settled(page, false);
            bridge.settled(page, true);
            assertThat(bridge.waiting()).isZero();
        });
    }

    /**
     * A message reaches the other page as a {@code message} event when its target origin is {@code "*"} or the
     * receiver's origin, with the sender's origin as Jasper knows it, and is dropped, silently, otherwise.
     */
    @Test void aMessageIsDeliveredOnlyToAReceiverOfItsTargetOrigin() throws Exception {
        onUiThread(() -> {
            var link = link();
            assertThat(ask(link.popup, post(link.id, "https://app.example", "{\"token\":\"t\"}")).ok()).isTrue();
            assertThat(link.opener.scripts).singleElement().asString()
                .contains(".deliver(\"" + link.id + "\",\"popup\",\"{\\\"token\\\":\\\"t\\\"}\",\"https://login.example\",\"https://app.example\")");

            assertThat(ask(link.popup, post(link.id, "https://evil.example", "2")).ok()).as("no answer tells the origin apart").isTrue();
            assertThat(ask(link.popup, post(link.id, "https://app.example:8443", "3")).ok()).isTrue();
            assertThat(link.opener.scripts).hasSize(1);

            assertThat(ask(link.popup, post(link.id, "*", "4")).ok()).isTrue();
            assertThat(link.opener.scripts).hasSize(2).last().asString().contains(",\"4\",\"https://login.example\",\"*\")");

            assertThat(ask(link.opener, post(link.id, "https://login.example", "5")).ok()).isTrue();
            assertThat(link.popup.scripts).singleElement().asString()
                .contains(".deliver(\"" + link.id + "\",\"opener\",\"5\",\"https://app.example\",\"https://login.example\")");
        });
    }

    @Test void theTargetOriginIsCheckedAgainstWhereTheReceiverIsWhenTheMessageArrives() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.popup.address = "https://elsewhere.example/next";
            assertThat(ask(link.opener, post(link.id, "https://login.example", "1")).ok()).isTrue();
            assertThat(link.popup.scripts).isEmpty();
            assertThat(ask(link.opener, post(link.id, "https://elsewhere.example", "2")).ok()).isTrue();
            assertThat(link.popup.scripts).singleElement().asString().contains(",\"2\",");
        });
    }

    /** A page at no web origin (a file, about:blank) takes only {@code "*"}, and its messages come from "null". */
    @Test void aReceiverWithoutAWebOriginTakesOnlyTheWildcard() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.opener.address = "file:///tmp/app.html";
            link.popup.address = "about:blank";
            assertThat(ask(link.popup, post(link.id, "null", "1")).ok()).isTrue();
            assertThat(ask(link.popup, post(link.id, "file://", "1")).ok()).isTrue();
            assertThat(link.opener.scripts).isEmpty();
            assertThat(ask(link.popup, post(link.id, "*", "2")).ok()).isTrue();
            assertThat(link.opener.scripts).singleElement().asString().contains(",\"2\",\"null\",\"*\")");
        });
    }

    /**
     * A document whose {@code Content-Security-Policy} sandboxes it without {@code allow-same-origin} has an opaque
     * origin, as in Chromium: its messages come from "null", and only messages for {@code "*"} reach it.
     */
    @Test void aSandboxedDocumentSendsFromNullAndTakesOnlyTheWildcard() throws Exception {
        onUiThread(() -> {
            var link = link();
            bridge.responded(link.popup.page, LOGIN, null, List.of("default-src 'self'; sandbox allow-scripts allow-popups"));
            assertThat(bridge.loadStarted(link.popup.party(), LOGIN)).as("the same window group").contains("\"opener\"");
            assertThat(ask(link.popup, post(link.id, "*", "1")).ok()).isTrue();
            assertThat(link.opener.scripts).singleElement().asString().contains(",\"1\",\"null\",\"*\")");
            assertThat(ask(link.opener, post(link.id, "https://login.example", "2")).ok()).isTrue();
            assertThat(link.popup.scripts).as("its address's origin is not its origin").isEmpty();
            assertThat(ask(link.opener, post(link.id, "*", "3")).ok()).isTrue();
            assertThat(link.popup.scripts).singleElement().asString().contains(",\"3\",\"https://app.example\",\"*\")");

            bridge.responded(link.popup.page, LOGIN, null, List.of("sandbox allow-scripts allow-same-origin"));
            bridge.loadStarted(link.popup.party(), LOGIN);
            assertThat(ask(link.opener, post(link.id, "https://login.example", "4")).ok()).isTrue();
            assertThat(link.popup.scripts).as("allow-same-origin keeps the origin").hasSize(2);
        });
    }

    @Test void aDocumentWhoseSandboxBlocksPopupsOpensNothingAndKeepsTheActivation() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            bridge.responded(opener.page, APP, null, List.of("sandbox allow-scripts allow-same-origin"));
            bridge.loadStarted(opener.party(), APP);
            opener.activation.activate(NOW - 100);
            assertThat(ask(opener, open(LOGIN)).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(opener.activation.active(NOW)).isTrue();

            bridge.responded(opener.page, APP, null, List.of("sandbox allow-scripts allow-popups"));
            bridge.loadStarted(opener.party(), APP + "#top");
            assertThat(ask(opener, open(LOGIN)).ok()).as("allow-popups; the fragment is the response's document").isTrue();

            opener.activation.activate(NOW - 100);
            bridge.responded(opener.page, "https://app.example/other", null, List.of("sandbox"));
            bridge.loadStarted(opener.party(), APP);
            assertThat(ask(opener, open(LOGIN)).ok()).as("a response for another address is not this document's").isTrue();
            assertThat(opener.opened).hasSize(2);
        });
    }

    /**
     * A request is judged by the document that sent it as Chromium's thread saw it when it took the request: a sandboxed
     * document whose page has since committed an unsandboxed one sends from "null" still, and still opens nothing.
     */
    @Test void aRequestKeepsTheSandboxItsDocumentHadWhenItWasTaken() throws Exception {
        onUiThread(() -> {
            var link = link();
            bridge.responded(link.popup.page, LOGIN, null, List.of("sandbox allow-scripts allow-popups"));
            bridge.loadStarted(link.popup.party(), LOGIN);
            var sandboxed = bridge.sender(link.popup.page, "main", () -> LOGIN);
            bridge.loadStarted(link.popup.party(), LOGIN);
            assertThat(bridge.request(link.popup.party(), true, sandboxed, post(link.id, "*", "1"), NOW).ok()).isTrue();
            assertThat(link.opener.scripts).singleElement().asString().contains(",\"1\",\"null\",\"*\")");
            assertThat(ask(link.popup, post(link.id, "*", "2")).ok()).isTrue();
            assertThat(link.opener.scripts).as("the unsandboxed document that followed").hasSize(2).last().asString()
                .contains(",\"2\",\"https://login.example\",\"*\")");

            var opener = new Document(APP);
            bridge.responded(opener.page, APP, null, List.of("sandbox allow-scripts allow-same-origin"));
            bridge.loadStarted(opener.party(), APP);
            var blocked = bridge.sender(opener.page, "main", () -> APP);
            bridge.loadStarted(opener.party(), APP);
            opener.activation.activate(NOW - 100);
            assertThat(bridge.request(opener.party(), true, blocked, open(LOGIN), NOW).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(opener.activation.active(NOW)).isTrue();
            assertThat(opener.opened).isEmpty();
            assertThat(ask(opener, open(LOGIN)).ok()).as("the unsandboxed document that followed").isTrue();
        });
    }

    /**
     * A stray Back or Forward over a page with no history that way, such as a sign-in popup, does nothing and keeps its
     * link; one the page's history allows sends the page elsewhere and ends it.
     */
    @Test void backOrForwardEndsALinkOnlyWhenThePagesHistoryAllowsIt() throws Exception {
        onUiThread(() -> {
            var link = link();
            history(link.popup.page, false, false);
            link.popup.page.back();
            link.popup.page.forward();
            assertThat(bridge.links()).isEqualTo(1);
            assertThat(link.opener.scripts).isEmpty();
            history(link.popup.page, false, true);
            // The page here has no browser, so going forward fails once the link has ended.
            assertThatThrownBy(link.popup.page::forward).isInstanceOf(NullPointerException.class);
            assertThat(bridge.links()).isZero();
            assertThat(link.opener.scripts).singleElement().asString().contains(".closed(\"" + link.id + "\")");
        });
    }

    /** An admitted query is answered and settled whatever happens on the UI thread; an Error is answered, then thrown on. */
    @Test void anAdmittedQueryIsAnsweredAndSettledEvenWhenAnErrorEscapes() throws Exception {
        onUiThread(() -> {
            var link = link();
            var answers = new java.util.ArrayList<Answer>();
            var from = bridge.sender(link.popup.page, "main", () -> LOGIN);
            assertThat(bridge.admit(link.popup.page, true, post(link.id, "*", "1"))).isEmpty();
            bridge.answer(link.popup.party(), true, from, asked(), post(link.id, "*", "1"), NOW, answers::add);
            assertThat(answers).singleElement().satisfies(answer -> assertThat(answer.ok()).isTrue());
            assertThat(bridge.waiting()).isZero();

            var broken = new Error("the page's browser broke");
            link.opener.breaking = broken;
            assertThat(bridge.admit(link.popup.page, true, post(link.id, "*", "2"))).isEmpty();
            assertThatThrownBy(() -> bridge.answer(link.popup.party(), true, from, asked(), post(link.id, "*", "2"), NOW, answers::add)).isSameAs(broken);
            assertThat(answers).hasSize(2).last().satisfies(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.waiting()).isZero();
        });
    }

    /** A {@code Content-Security-Policy} header can hold several policies, and any sandbox lacking a token takes it away. */
    @Test void aSandboxIsReadConservativelyFromEveryPolicy() {
        assertThat(Sandbox.of(List.of())).isEqualTo(Sandbox.NONE);
        assertThat(Sandbox.of(List.of("default-src 'self'; script-src 'none'"))).isEqualTo(Sandbox.NONE);
        assertThat(Sandbox.of(List.of("sandbox"))).satisfies(sandbox -> {
            assertThat(sandbox.opaque()).isTrue();
            assertThat(sandbox.blocksPopups()).isTrue();
        });
        assertThat(Sandbox.of(List.of("SANDBOX Allow-Same-Origin\tALLOW-POPUPS"))).satisfies(sandbox -> {
            assertThat(sandbox.sandboxed()).isTrue();
            assertThat(sandbox.opaque()).isFalse();
            assertThat(sandbox.blocksPopups()).isFalse();
        });
        assertThat(Sandbox.of(List.of("default-src 'self', sandbox allow-same-origin allow-popups", "script-src 'none'; sandbox allow-scripts")))
            .as("the second policy's sandbox lacks both").isEqualTo(new Sandbox(true, false, false));
        assertThat(Sandbox.of(List.of("sandbox allow-same-origin allow-popups; sandbox allow-popups")))
            .as("a repeated directive counts too").isEqualTo(new Sandbox(true, false, true));
    }

    /** Messages to a popup that has not started loading are lost, as messages to a popup's first empty page are. */
    @Test void aMessageToAPopupThatHasNotLoadedIsLost() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            String id = ask(opener, open(LOGIN)).text();
            assertThat(ask(opener, post(id, "*", "1")).ok()).isTrue();
            var popup = opener.popups.getFirst();
            assertThat(popup.scripts).isEmpty();
            bridge.loadStarted(popup.party(), LOGIN);
            assertThat(ask(opener, post(id, "*", "2")).ok()).isTrue();
            assertThat(popup.scripts).singleElement().asString().contains(",\"2\",");
        });
    }

    @Test void closingThePopupMarksTheOpenersStandInClosedAndEndsTheLink() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.popup.page.close();
            assertThat(link.opener.scripts).singleElement().asString().contains(".closed(\"" + link.id + "\")");
            assertThat(bridge.links()).as("the link ends with its popup").isZero();
            link.opener.activation.activate(NOW - 100);
            assertThat(ask(link.opener, post(link.id, "*", "1")).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(ask(link.opener, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.popup.scripts).isEmpty();
            assertThat(link.popup.shows).isZero();
            link.opener.page.close();
            assertThat(link.opener.scripts).hasSize(1);
        });
    }

    @Test void closingTheOpenerMarksThePopupsWindowOpenerClosedForEveryDocumentItLoads() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.opener.page.close();
            assertThat(link.popup.scripts).singleElement().asString().contains(".closed(\"" + link.id + "\")");
            assertThat(ask(link.popup, post(link.id, "*", "1")).code()).isEqualTo(OpenerBridge.GONE);
            assertThat(link.opener.scripts).isEmpty();
            assertThat(bridge.loadStarted(link.popup.party(), LOGIN)).as("the popup's next document")
                .endsWith("\n({\"opener\":\"" + link.id + "\",\"closed\":true});");
            assertThat(bridge.links()).isEqualTo(1);
            link.popup.page.close();
            assertThat(bridge.links()).isZero();
            assertThat(bridge.loadStarted(link.popup.party(), LOGIN)).endsWith("\n({});");
        });
    }

    /**
     * A closed page is let go: a link ends with its popup, and an opener that closes first leaves only the link's id and
     * that it closed, so neither page, its canvas's images nor its closed browser stay reachable through the bridge.
     */
    @Test void aClosedPageIsNoLongerHeld() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            WeakReference<Page> popup = closedPopup(opener);
            assertThat(bridge.links()).isZero();
            assertThat(bridge.holds(opener.page)).isFalse();
            awaitCollected(popup, "the closed popup's page");

            var orphan = new java.util.ArrayList<Document>();
            WeakReference<Page> gone = closedOpener(orphan);
            assertThat(bridge.links()).isEqualTo(1);
            awaitCollected(gone, "the closed opener's page");
            assertThat(bridge.loadStarted(orphan.getFirst().party(), LOGIN)).contains("\"closed\":true");
        });
    }

    /** {@code popup.close()} from the opener and {@code window.close()} in the popup both close the popup's item. */
    @Test void closeClosesThePopupsItemFromEitherEnd() throws Exception {
        onUiThread(() -> {
            var fromOpener = link();
            assertThat(ask(fromOpener.opener, "{\"op\":\"close\",\"id\":\"" + fromOpener.id + "\"}").ok()).isTrue();
            assertThat(fromOpener.popup.closes).isEqualTo(1);
            assertThat(fromOpener.opener.scripts).singleElement().asString().contains(".closed(\"" + fromOpener.id + "\")");
            assertThat(ask(fromOpener.opener, "{\"op\":\"close\",\"id\":\"" + fromOpener.id + "\"}").code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(fromOpener.popup.closes).isEqualTo(1);

            var fromPopup = link();
            assertThat(ask(fromPopup.popup, "{\"op\":\"close\",\"id\":\"" + fromPopup.id + "\"}").ok()).isTrue();
            assertThat(fromPopup.popup.closes).isEqualTo(1);
            assertThat(fromPopup.opener.closes).isZero();

            var orphan = link();
            orphan.opener.page.close();
            assertThat(ask(orphan.popup, "{\"op\":\"close\",\"id\":\"" + orphan.id + "\"}").ok()).as("after its opener closed").isTrue();
            assertThat(orphan.popup.closes).isEqualTo(1);
            assertThat(bridge.links()).isZero();
        });
    }

    /**
     * {@code focus()} shows the other page's item, which takes the keyboard, so either end needs its own activation,
     * unconsumed, and must show: a background page cannot pull the keyboard away from where the user types.
     */
    @Test void focusShowsTheOtherItemOnlyFromAShownPageTheUserJustActivated() throws Exception {
        onUiThread(() -> {
            var link = link();
            String focus = "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}";
            assertThat(ask(link.opener, focus).code()).as("no activation").isEqualTo(OpenerBridge.REFUSED);
            link.opener.activation.activate(NOW - 100);
            link.opener.showing = false;
            assertThat(ask(link.opener, focus).code()).as("hidden").isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.popup.shows).isZero();
            link.opener.showing = true;
            assertThat(ask(link.opener, focus).ok()).isTrue();
            assertThat(link.popup.shows).isEqualTo(1);
            assertThat(link.opener.activation.active(NOW)).as("focus consumes nothing").isTrue();

            assertThat(ask(link.popup, focus).code()).isEqualTo(OpenerBridge.REFUSED);
            link.popup.activation.activate(NOW - 100);
            assertThat(ask(link.popup, focus).ok()).isTrue();
            assertThat(link.opener.shows).isEqualTo(1);
        });
    }

    @Test void aPageThatIsNeitherEndOfALinkCannotUseIt() throws Exception {
        onUiThread(() -> {
            var link = link();
            var stranger = new Document("https://app.example/other");
            stranger.activation.activate(NOW - 100);
            bridge.loadStarted(stranger.party(), stranger.address);
            assertThat(ask(stranger, post(link.id, "*", "1")).code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(ask(stranger, "{\"op\":\"close\",\"id\":\"" + link.id + "\"}").code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(ask(stranger, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.opener.scripts).isEmpty();
            assertThat(link.popup.scripts).isEmpty();
            assertThat(link.popup.closes).isZero();
            assertThat(link.popup.shows).isZero();
            assertThat(link.opener.shows).isZero();
        });
    }

    /** Chromium puts a page the user sends elsewhere in a window group of its own, so its link ends. */
    @Test void theUserSendingEitherPageElsewhereEndsTheLink() throws Exception {
        onUiThread(() -> {
            var fromOpener = link();
            fromOpener.opener.page.load(URI.create("https://app.example/elsewhere"));
            assertThat(bridge.links()).isZero();
            assertThat(fromOpener.popup.scripts).singleElement().asString().contains(".closed(\"" + fromOpener.id + "\")");
            assertThat(fromOpener.opener.scripts).singleElement().asString().contains(".closed(\"" + fromOpener.id + "\")");
            assertThat(bridge.loadStarted(fromOpener.popup.party(), LOGIN)).endsWith("\n({});");

            var fromPopup = link();
            fromPopup.popup.page.load(URI.create("https://login.example/other"));
            assertThat(bridge.links()).isZero();
            assertThat(fromPopup.opener.scripts).singleElement().asString().contains(".closed(\"" + fromPopup.id + "\")");
            assertThat(ask(fromPopup.popup, post(fromPopup.id, "*", "1")).code()).isEqualTo(OpenerBridge.REFUSED);
        });
    }

    /**
     * The HTML standard's "check if COOP values require a browsing context group switch", which Chromium follows: the
     * opener policies of the document a page shows and the one that replaces it, their origins, and whether the page
     * shows its first, empty document.
     */
    @Test void anOpenerPolicyMovesAPageToAWindowGroupOfItsOwnAsTheStandardSays() {
        var app = Optional.of("https://app.example");
        var login = Optional.of("https://login.example");
        Optional<String> opaque = Optional.empty();
        OpenerPolicy none = OpenerPolicy.UNSAFE_NONE, same = OpenerPolicy.SAME_ORIGIN, popups = OpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS,
            noopener = OpenerPolicy.NOOPENER_ALLOW_POPUPS;
        // active, active origin, response, response origin, initial about:blank -> severs
        record Row(OpenerPolicy active, Optional<String> activeOrigin, OpenerPolicy response, Optional<String> responseOrigin, boolean initial,
                   boolean severs) { }
        for (Row row : List.of(new Row(none, app, none, login, false, false), new Row(none, app, none, login, true, false),
                new Row(same, app, same, app, false, false), new Row(same, app, same, login, false, true),
                new Row(same, app, none, app, false, true), new Row(none, app, same, app, false, true),
                new Row(popups, app, popups, app, false, false), new Row(popups, app, popups, login, false, true),
                new Row(popups, app, same, app, false, true), new Row(same, app, popups, app, false, true),
                new Row(popups, app, none, login, true, false), new Row(popups, app, none, login, false, true),
                new Row(same, app, none, login, true, true), new Row(noopener, app, none, login, true, false),
                new Row(noopener, app, noopener, app, false, false), new Row(none, app, noopener, app, true, true),
                new Row(noopener, app, noopener, login, false, true), new Row(same, opaque, same, opaque, false, true),
                new Row(none, opaque, none, opaque, false, false)))
            assertThat(Isolation.severs(row.initial(), row.active(), row.activeOrigin(), row.response(), row.responseOrigin()))
                .as(row.toString()).isEqualTo(row.severs());
    }

    @Test void anOpenerPolicyIsReadOnlyInASecureContextAndOnlyForAKnownValue() {
        assertThat(OpenerPolicy.of("same-origin", APP)).isEqualTo(OpenerPolicy.SAME_ORIGIN);
        assertThat(OpenerPolicy.of(" same-origin ; report-to=\"coop\"", APP)).isEqualTo(OpenerPolicy.SAME_ORIGIN);
        assertThat(OpenerPolicy.of("same-origin-allow-popups", APP)).isEqualTo(OpenerPolicy.SAME_ORIGIN_ALLOW_POPUPS);
        assertThat(OpenerPolicy.of("noopener-allow-popups", APP)).isEqualTo(OpenerPolicy.NOOPENER_ALLOW_POPUPS);
        for (String header : new String[] {null, "", "unsafe-none", "Same-Origin", "same-origin, unsafe-none", "same-origin-plus-coep?"})
            assertThat(OpenerPolicy.of(header, APP)).as(String.valueOf(header)).isEqualTo(OpenerPolicy.UNSAFE_NONE);
        assertThat(OpenerPolicy.of("same-origin", "http://app.example/")).as("not a secure context").isEqualTo(OpenerPolicy.UNSAFE_NONE);
        for (String local : List.of("http://localhost:8000/", "http://127.0.0.1/", "http://[::1]:8080/a", "http://app.localhost/"))
            assertThat(OpenerPolicy.of("same-origin", local)).as(local).isEqualTo(OpenerPolicy.SAME_ORIGIN);
    }

    /** A popup's first document whose opener policy cuts it off from its opener ends the link: it gets no window.opener. */
    @Test void aPopupWhoseOpenerPolicyCutsItOffEndsTheLink() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            String id = ask(opener, open(LOGIN)).text();
            var popup = opener.popups.getFirst();
            bridge.responded(popup.page, LOGIN, "same-origin", List.of());
            assertThat(bridge.loadStarted(popup.party(), LOGIN)).endsWith("\n({});");
            assertThat(bridge.links()).isZero();
            assertThat(opener.scripts).singleElement().asString().contains(".closed(\"" + id + "\")");
        });
    }

    /**
     * An opener whose own policy is same-origin loses a cross-origin popup at once; one whose policy allows popups keeps
     * it; a same-origin popup of the same policy stays too.
     */
    @Test void anOpenersPolicyDecidesWhetherItsPopupsStay() throws Exception {
        onUiThread(() -> {
            assertThat(popupStays("same-origin", LOGIN, null)).isFalse();
            assertThat(popupStays("same-origin-allow-popups", LOGIN, null)).isTrue();
            assertThat(popupStays("same-origin", "https://app.example/popup", "same-origin")).isTrue();
            assertThat(popupStays("same-origin", "https://app.example/popup", null)).isFalse();
            assertThat(popupStays(null, LOGIN, null)).isTrue();
        });
    }

    @Test void anOpenerThatLoadsAnIsolatedDocumentEndsTheLink() throws Exception {
        onUiThread(() -> {
            var link = link();
            bridge.responded(link.opener.page, "https://app.example/next", "same-origin", List.of());
            bridge.loadStarted(link.opener.party(), "https://app.example/next");
            assertThat(bridge.links()).isZero();
            assertThat(link.popup.scripts).singleElement().asString().contains(".closed(\"" + link.id + "\")");
        });
    }

    @Test void aMessageOverOneMebibyteIsRefusedAndOneOfExactlyThatIsDelivered() throws Exception {
        onUiThread(() -> {
            var link = link();
            String largest = "\"" + "a".repeat(OpenerBridge.MESSAGE_LIMIT - 2) + "\"";
            assertThat(largest).hasSize(OpenerBridge.MESSAGE_LIMIT);
            assertThat(ask(link.popup, post(link.id, "*", largest + " ")).code()).isEqualTo(OpenerBridge.TOO_LARGE);
            assertThat(link.opener.scripts).isEmpty();
            assertThat(ask(link.popup, post(link.id, "*", largest)).ok()).isTrue();
            assertThat(link.opener.scripts).hasSize(1);
            String huge = "{\"op\":\"post\",\"id\":\"" + link.id + "\",\"targetOrigin\":\"*\",\"data\":\""
                + "\\u0001".repeat(OpenerBridge.MESSAGE_LIMIT + 1) + "\"}";
            assertThat(ask(link.popup, huge).code()).as("a request too long to read").isEqualTo(OpenerBridge.TOO_LARGE);
        });
    }

    /**
     * Data reaches the receiver's script only as a string literal: quotes, backslashes, control characters, the line and
     * paragraph separators and lone surrogates are escaped, so no data ends the literal or the line.
     */
    @Test void aDeliveredMessageIsAnEscapedStringLiteral() throws Exception {
        onUiThread(() -> {
            var link = link();
            // The envelope carries, as JSON escapes: "');alert(1);//\ U+2028 U+2029 NUL LF </script> and a lone U+D83D.
            String request = "{\"op\":\"post\",\"id\":\"" + link.id + "\",\"targetOrigin\":\"*\",\"data\":"
                + "\"\\\"');alert(1);//\\\\\\u2028\\u2029\\u0000\\n</script>\\ud83d\"}";
            assertThat(ask(link.popup, request).ok()).isTrue();
            String data = "\"');alert(1);//\\" + (char) 0x2028 + (char) 0x2029 + (char) 0 + "\n</script>" + (char) 0xD83D;
            String literal = "\"\\\"');alert(1);//\\\\\\u2028\\u2029\\u0000\\n</script>\\ud83d\"";
            assertThat(StrictJson.quote(data)).isEqualTo(literal);
            String script = link.opener.scripts.getFirst();
            assertThat(script).contains("\"popup\"," + literal + ",");
            assertThat(script.chars().filter(c -> c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029 || Character.isSurrogate((char) c)))
                .as("raw characters that would end the literal or the line").isEmpty();
            assertThat(BridgeJson.object("{\"data\":" + literal + "}")).as("the literal reads back as JSON")
                .hasValueSatisfying(members -> assertThat(members.get("data")).isEqualTo(data));
        });
    }

    /** A page the bridge cannot reach as another closes does not keep that page's other close listeners from hearing it. */
    @Test void aPageTheBridgeCannotReachDoesNotStopTheOtherCloseListeners() throws Exception {
        onUiThread(() -> {
            var link = link();
            var told = new AtomicInteger();
            link.popup.page.onClose(told::incrementAndGet);
            link.opener.failing = true;
            link.popup.page.close();
            assertThat(told).hasValue(1);
            assertThat(bridge.links()).isZero();
        });
    }

    @Test void aMalformedRequestFailsWithoutThrowing() throws Exception {
        onUiThread(() -> {
            var link = link();
            var bad = new java.util.ArrayList<String>(List.of("", "hello", "{", "[]", "{}", "null", "\"open\"",
                "{\"op\":\"launch\"}", "{\"op\":\"open\"}", "{\"op\":\"open\",\"url\":7}", "{\"op\":{\"url\":\"x\"}}",
                "{\"op\":\"open\",\"url\":\"" + LOGIN + "\",\"noopener\":\"yes\"}",
                "{\"op\":\"open\",\"url\":\"" + LOGIN + "\",\"url\":\"" + LOGIN + "\"}",
                "{\"op\":\"open\",\"url\":\"" + LOGIN + "\",\"name\":\"x\"}",
                "{\"op\":\"close\"}", "{\"op\":\"close\",\"id\":" + link.id + "}", "{\"op\":\"close\",\"id\":\"0" + link.id + "\"}",
                "{\"op\":\"close\",\"id\":\"-1\"}", "{\"op\":\"close\",\"id\":\"99999999999999999999\"}",
                "{\"op\":\"close\",\"id\":\"" + link.id + "\"} x", "{\"op\":\"close\",\"id\":\"" + link.id + "\",}",
                "{\"op\":\"post\",\"targetOrigin\":\"*\",\"data\":\"1\"}", "{\"op\":\"post\",\"id\":\"" + link.id + "\",\"data\":\"1\"}",
                "{\"op\":\"post\",\"id\":\"" + link.id + "\",\"targetOrigin\":\"*\"}", "{\"op\":\"\\x\"}", "{\"op\":\"\\u12\"}",
                "{\"op\":\"close" + (char) 1 + "\"}", "{\"op\":\"close"));
            bad.add(null);
            for (String request : bad) {
                Answer answer = ask(link.popup, request);
                assertThat(answer.ok()).as(String.valueOf(request)).isFalse();
                assertThat(answer.code()).as(String.valueOf(request)).isEqualTo(OpenerBridge.MALFORMED);
            }
            assertThat(ask(link.popup, "{\"op\":\"close\",\"id\":\"" + (Long.parseLong(link.id) ^ 1) + "\"}").code())
                .as("an id Jasper never issued").isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.popup.closes).isZero();
            assertThat(link.opener.scripts).isEmpty();
            assertThat(bridge.links()).isEqualTo(1);
        });
    }

    /**
     * Plan 4b's rulings P3 and P10: {@code favicon} tells the page the icon its document names, which the page then
     * fetches; nothing else changes.
     */
    @Test void aFaviconRequestFromTheTopFrameTellsThePageItsDocumentsIcon() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            Answer answer = ask(page, favicon("https://app.example/static/icon.png"));
            assertThat(answer.ok()).as(answer.toString()).isTrue();
            assertThat(answer.text()).isEmpty();
            assertThat(PageTestAccess.declaredFavicon(page.page)).contains(URI.create("https://app.example/static/icon.png"));
            assertThat(page.scripts).isEmpty();
            assertThat(page.opened).isEmpty();
            assertThat(bridge.links()).isZero();
            assertThat(ask(new Document(APP), favicon("HTTP://app.example/icon.png")).ok()).as("a scheme in capitals").isTrue();
        });
    }

    /**
     * Any script in the page can send {@code favicon}, so it is taken only from the top frame, and its address is only a
     * request: an absolute http or https address of at most {@value OpenerBridge#ICON_LIMIT} characters, without a user
     * name or password.
     */
    @Test void aFaviconRequestIsUntrusted() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            String icon = favicon("https://app.example/icon.png");
            assertThat(bridge.admit(page.page, false, icon)).as("a sub-frame's, on Chromium's thread")
                .hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.request(page.party(), false, APP, icon, NOW).code()).as("a sub-frame's").isEqualTo(OpenerBridge.REFUSED);
            for (String request : List.of("{\"op\":\"favicon\"}", "{\"op\":\"favicon\",\"url\":true}",
                    "{\"op\":\"favicon\",\"url\":\"https://app.example/icon.png\",\"size\":\"32\"}"))
                assertThat(ask(page, request).code()).as(request).isEqualTo(OpenerBridge.MALFORMED);
            for (String url : List.of("/favicon.ico", "icon.png", "data:image/png;base64,AAAA", "file:///tmp/icon.png",
                    "jasper-about:icon.png", "javascript:alert(1)", "ftp://app.example/icon.png", "https://user:secret@app.example/icon.png",
                    "https:///icon.png", "https://app.example/a b", "https://app.example/" + "a".repeat(OpenerBridge.ICON_LIMIT)))
                assertThat(ask(page, favicon(url)).code()).as(url).isEqualTo(OpenerBridge.REFUSED);
            assertThat(PageTestAccess.declaredFavicon(page.page)).isEmpty();
        });
    }

    /** The page takes an icon only from the document it shows: one from the document before tells it nothing. */
    @Test void aFaviconRequestFromADocumentThePageNoLongerShowsTellsItNothing() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            assertThat(bridge.request(page.party(), true, LOGIN, favicon("https://login.example/icon.png"), NOW).ok()).isTrue();
            assertThat(PageTestAccess.declaredFavicon(page.page)).isEmpty();
        });
    }

    /**
     * Plan 7's Task 5: {@code reader} and {@code distractions}, from {@code reader.js} and {@code distractions.js}, reach
     * the page's tools as the page script sent them, op and text, for its item to judge; a sub-frame's are refused, on
     * Chromium's thread and the UI thread, as those scripts live in the top frame alone; and none reaches a closed page.
     */
    @Test void aToolReportFromTheTopFrameReachesThePagesTools() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var told = new java.util.ArrayList<String>();
            page.page.onToolReport((op, json) -> told.add(op + " " + json));
            String reader = "{\"op\":\"reader\",\"check\":1,\"length\":42}", off = "{\"op\":\"distractions\",\"check\":1,\"state\":\"off\"}";
            Answer answer = ask(page, reader);
            assertThat(answer.ok()).as(answer.toString()).isTrue();
            assertThat(answer.text()).isEmpty();
            assertThat(ask(page, off).ok()).isTrue();
            assertThat(told).containsExactly("reader " + reader, "distractions " + off);
            assertThat(bridge.admit(page.page, false, reader)).as("a sub-frame's, on Chromium's thread")
                .hasValueSatisfying(refused -> assertThat(refused.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.request(page.party(), false, APP, off, NOW).code()).as("a sub-frame's").isEqualTo(OpenerBridge.REFUSED);
            assertThat(told).hasSize(2);
            assertThat(page.scripts).isEmpty();
            page.page.close();
            ask(page, reader);
            assertThat(told).as("a closed page's tools hear nothing").hasSize(2);
        });
    }

    /**
     * Plan 7's Task 7: {@code savePage}, a part of Save Page As's answer from {@code save-page.js}, reaches the page's tools
     * as the page sent it, from the top frame alone; a sub-frame's is refused on Chromium's thread and the UI thread. A part
     * may be as long as a message of the bridge's limit, 1 MiB of JSON.
     */
    @Test void aSavePagePartFromTheTopFrameReachesThePagesTools() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var told = new java.util.ArrayList<String>();
            page.page.onToolReport((op, json) -> told.add(op + " " + json.length()));
            String part = "{\"op\":\"savePage\",\"save\":\"jasper-save-0123456789abcdef-\",\"part\":0,\"parts\":1,\"text\":\""
                + "x".repeat(OpenerBridge.MESSAGE_LIMIT - 2) + "\"}";
            assertThat(bridge.admit(page.page, true, part)).as("admitted on Chromium's thread").isEmpty();
            bridge.settled(page.page, true);
            Answer answer = ask(page, part);
            assertThat(answer.ok()).as(answer.toString()).isTrue();
            assertThat(told).containsExactly("savePage " + part.length());
            assertThat(bridge.admit(page.page, false, part)).as("a sub-frame's, on Chromium's thread")
                .hasValueSatisfying(refused -> assertThat(refused.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.request(page.party(), false, APP, part, NOW).code()).as("a sub-frame's").isEqualTo(OpenerBridge.REFUSED);
            assertThat(told).hasSize(1);
        });
    }

    @Test void aRequestIsReadAsStrictJson() {
        assertThat(BridgeJson.object(" { \"op\" : \"post\" , \"data\" : \"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\\uD83D\\uDE00\" , \"x\" : true , \"y\" : false } "))
            .hasValueSatisfying(members -> assertThat(members).containsExactly(java.util.Map.entry("op", "post"),
                java.util.Map.entry("data", "a\"b\\c/d\b\f\n\r\t\u00e9" + (char) 0xD83D + (char) 0xDE00), java.util.Map.entry("x", true),
                java.util.Map.entry("y", false)));
        assertThat(BridgeJson.object("{}")).hasValue(java.util.Map.of());
        assertThat(BridgeJson.object("{\"a\":1,\"b\":-0,\"c\":123456789012345678}")).as("integers, as find's answers carry")
            .hasValue(java.util.Map.of("a", 1L, "b", 0L, "c", 123_456_789_012_345_678L));
        for (String bad : List.of("{\"a\":null}", "{\"a\":1.5}", "{\"a\":1e3}", "{\"a\":1234567890123456789}", "{\"a\":[]}", "{\"a\":{}}",
                "{\"a\":tru}", "{\"a\":\"b\"", "{'a':'b'}", "{\"a\" \"b\"}", "{,}"))
            assertThat(BridgeJson.object(bad)).as(bad).isEmpty();
    }

    /**
     * Plan 4b's P21: whether the main frame asked, as Chromium's thread read it, reaches the request the UI thread
     * answers, so a sub-frame's opener request or favicon is refused there too.
     */
    @Test void anAnsweredQueryKeepsWhetherASubFrameAsked() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            page.activation.activate(NOW - 100);
            var answers = new java.util.ArrayList<Answer>();
            var child = bridge.sender(page.page, "child", () -> APP);
            bridge.answer(page.party(), false, child, asked(), open(LOGIN), NOW, answers::add);
            bridge.answer(page.party(), false, child, asked(), favicon("https://app.example/icon.png"), NOW, answers::add);
            assertThat(answers).extracting(Answer::code).containsExactly(OpenerBridge.REFUSED, OpenerBridge.REFUSED);
            assertThat(page.opened).isEmpty();
            assertThat(page.activation.active(NOW)).as("a refused frame consumes nothing").isTrue();
            assertThat(PageTestAccess.declaredFavicon(page.page)).isEmpty();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), open(LOGIN), NOW, answers::add);
            assertThat(answers.getLast().ok()).as("the main frame's").isTrue();
            assertThat(page.opened).containsExactly("NEXT_TAB " + LOGIN);
        });
    }

    /**
     * {@code find.js} runs in every frame of a page, cross-origin ones too, so {@code find} is taken from any of them,
     * and each answer reaches the page's find for the frame Chromium names, whatever the frame's document says.
     */
    @Test void aFindAnswerIsTakenFromAnyFrameOfThePageAndReachesItsFind() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var ran = new java.util.ArrayList<String>();
            PageFind find = PageTestAccess.recordingFind(List.of("main", "child"), Optional.of("child"), ran, new java.util.ArrayList<>());
            PageTestAccess.useFind(page.page, find);
            var result = find.find("needle", false, false);
            long search = (Long) StrictJson.object(ran.getFirst().substring(ran.getFirst().indexOf(' ') + 1), 1).orElseThrow().get("search");
            String fromChild = findAnswer(search, 2);
            assertThat(bridge.admit(page.page, false, fromChild)).as("a sub-frame's, on Chromium's thread").isEmpty();
            bridge.settled(page.page, false);
            var child = bridge.sender(page.page, "child", () -> "https://ads.example/frame");
            assertThat(bridge.request(page.party(), false, child, fromChild, NOW).ok()).isTrue();
            assertThat(result).isNotDone();
            assertThat(bridge.request(page.party(), true, bridge.sender(page.page, "main", () -> APP), findAnswer(search, 1), NOW).ok()).isTrue();
            assertThat(result).isCompletedWithValue(new PageFind.Result(3, 1, false));

            var selection = find.selection();
            long asked = (Long) StrictJson.object(ran.getLast().substring(ran.getLast().indexOf(' ') + 1), 1).orElseThrow().get("search");
            assertThat(bridge.request(page.party(), false, child, "{\"op\":\"find\",\"search\":" + asked + ",\"selection\":\"word\"}", NOW).ok()).isTrue();
            assertThat(selection).isCompletedWithValue("word");
            assertThat(page.scripts).isEmpty();
            assertThat(page.opened).isEmpty();
        });
    }

    /** A find answer is only shown, so it is held to its two shapes and their bounds; anything else is malformed. */
    @Test void aFindAnswerIsUntrusted() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var main = bridge.sender(page.page, "main", () -> APP);
            var child = bridge.sender(page.page, "child", () -> APP);
            for (String request : List.of("{\"op\":\"find\"}", "{\"op\":\"find\",\"search\":1}",
                    "{\"op\":\"find\",\"search\":\"1\",\"count\":1,\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":0,\"count\":1,\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":-1,\"count\":1,\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":1,\"count\":-1,\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":1,\"count\":" + (PageFind.MAX_MATCHES + 1) + ",\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":1,\"count\":1.5,\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":1,\"count\":\"1\",\"invalid\":false}",
                    "{\"op\":\"find\",\"search\":1,\"count\":1,\"invalid\":\"no\"}",
                    "{\"op\":\"find\",\"search\":1,\"count\":1}",
                    "{\"op\":\"find\",\"search\":1,\"count\":1,\"invalid\":false,\"current\":0}",
                    "{\"op\":\"find\",\"search\":1,\"count\":1,\"invalid\":false,\"selection\":\"x\"}",
                    "{\"op\":\"find\",\"search\":1,\"selection\":true}",
                    "{\"op\":\"find\",\"search\":1,\"selection\":\"" + "x".repeat(PageFind.MAX_QUERY + 1) + "\"}"))
                assertThat(bridge.request(page.party(), true, main, request, NOW).code()).as(request).isEqualTo(OpenerBridge.MALFORMED);
            assertThat(bridge.request(page.party(), false, child, "{\"op\":\"find\",\"search\":1,\"count\":-1,\"invalid\":false}", NOW).code())
                .as("a sub-frame's, read as find").isEqualTo(OpenerBridge.MALFORMED);
            assertThat(bridge.request(page.party(), false, child, "{\"op\":\"find\",\"search\":1,\"count\":1.5,\"invalid\":false}", NOW).code())
                .as("a sub-frame's that is no request: its op is unread").isEqualTo(OpenerBridge.REFUSED);
            String long_ = "{\"op\":\"find\",\"search\":1,\"selection\":\"" + "\\\"".repeat(2_100) + "\"}";
            assertThat(bridge.admit(page.page, false, long_)).as("a sub-frame's request is short")
                .hasValueSatisfying(answer -> assertThat(answer.code()).isEqualTo(OpenerBridge.REFUSED));
            assertThat(bridge.admit(page.page, true, long_)).as("the main frame's may be longer").isEmpty();
            bridge.settled(page.page, true);
            assertThat(bridge.request(page.party(), false, child, findAnswer(1, 1), NOW).ok()).as("no search under way: shown nowhere").isTrue();
        });
    }

    @Test void anOriginIsTheSchemeHostAndPortOfAWebAddress() {
        assertThat(Isolation.origin("https://Example.COM:443/a?b#c")).contains("https://example.com");
        assertThat(Isolation.origin("http://a.example:80/")).contains("http://a.example");
        assertThat(Isolation.origin("http://a.example:8080")).contains("http://a.example:8080");
        assertThat(Isolation.origin("https://user:p@ss@a.example/x")).contains("https://a.example");
        assertThat(Isolation.origin("http://[::1]:8000/x")).contains("http://[::1]:8000");
        for (String url : new String[] {"file:///tmp/a.html", "about:blank", "jasper-about:welcome", "data:text/html,hi",
                "blob:https://a.example/0f", "https://", "https:///x", "", "a.example"})
            assertThat(Isolation.origin(url)).as(url).isEmpty();
        assertThat(Isolation.origin(null)).isEmpty();
    }

    /** The page script is the plugin's own file: a function the configuration is passed to, with no raw controls. */
    @Test void theScriptIsAFunctionOfItsConfigurationThatTheRouterReaches() throws Exception {
        assertThat(OpenerBridge.QUERY).isEqualTo("jasperBridgeQuery");
        assertThat(OpenerBridge.CANCEL).isEqualTo("jasperBridgeCancel");
        String file;
        try (InputStream in = OpenerBridge.class.getResourceAsStream("opener-bridge.js")) {
            assertThat(in).as("opener-bridge.js").isNotNull();
            file = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertThat(OpenerBridge.SOURCE).isEqualTo(file.strip());
        assertThat(file.strip()).contains("jasperBridgeQuery", "Symbol.for('dev.jasper.browser.opener-bridge')", "window.origin")
            .doesNotContain("location.origin").endsWith("})");
        assertThat(file.chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F))
            .as("ASCII only, with no control character but the newline").isEmpty();
    }

    /** Plan 8's F19: the codes a page's late answers fail with are the bridge's own, which the page scripts read. */
    @Test void aPageQuerysCodesAreTheBridges() {
        assertThat(List.of(PageQuery.MALFORMED, PageQuery.REFUSED, PageQuery.GONE, PageQuery.BUSY))
            .containsExactly(OpenerBridge.MALFORMED, OpenerBridge.REFUSED, OpenerBridge.GONE, OpenerBridge.BUSY);
    }

    /**
     * Plan 8's F14: a top frame's {@code login.pick} becomes a query its page's password manager answers later, once,
     * with the document's origin as Chromium's thread saw it and the request as the page sent it; it waits in no place
     * of the page's, and leaves the bridge once answered.
     */
    @Test void aTopFrameLoginPickReachesThePagesPasswordManagerAndIsAnsweredOnceLater() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            bridge.loadStarted(page.party(), APP);
            var answers = new ArrayList<Answer>();
            assertThat(bridge.admit(page.page, true, PICK)).isEmpty();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(answers).as("answered later").isEmpty();
            assertThat(bridge.waiting()).as("it holds no waiting place").isZero();
            assertThat(bridge.loginQueries()).isOne();
            PageQuery query = held.getFirst();
            assertThat(held).hasSize(1);
            assertThat(query.page()).isSameAs(page.page);
            assertThat(query.origin()).hasValue("https://app.example");
            assertThat(query.request()).containsExactly(Map.entry("op", "login.pick"), Map.entry("field", "password"), Map.entry("x", 8L),
                Map.entry("y", 16L), Map.entry("width", 200L), Map.entry("height", 24L));
            assertThat(query.answer("{\"password\":\"pw\"}")).isTrue();
            assertThat(answers).containsExactly(new Answer(0, "{\"password\":\"pw\"}"));
            assertThat(query.answer("{}")).isFalse();
            assertThat(query.fail(PageQuery.BUSY, "Busy.")).isFalse();
            assertThat(answers).as("once").hasSize(1);
            assertThat(bridge.loginQueries()).as("an answered query leaves").isZero();
        });
    }

    /** Plan 8's F14: the picker may stay open for minutes, so its query keeps none of the page's eight places. */
    @Test void aLoginPickHoldsNoneOfThePagesWaitingPlaces() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            bridge.loadStarted(page.party(), APP);
            var answers = new ArrayList<Answer>();
            for (int each = 0; each < 2 * OpenerBridge.WAITING_LIMIT; each++) {
                assertThat(bridge.admit(page.page, true, PICK)).as("pick %d", each).isEmpty();
                bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            }
            assertThat(held).hasSize(2 * OpenerBridge.WAITING_LIMIT);
            assertThat(bridge.waiting()).as("back to none while each picker would still be open").isZero();
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) assertThat(bridge.admit(page.page, true, "{}")).isEmpty();
            for (int each = 0; each < OpenerBridge.WAITING_LIMIT; each++) bridge.settled(page.page, true);
            assertThat(answers).isEmpty();
            held.forEach(query -> query.answer("{}"));
            assertThat(answers).hasSize(2 * OpenerBridge.WAITING_LIMIT);
            assertThat(bridge.loginQueries()).isZero();
        });
    }

    /** The user's call: the field buttons fill the top frame only, so a sub-frame's pick is refused on both threads. */
    @Test void aSubFramesLoginPickIsRefused() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            assertThat(bridge.admit(page.page, false, PICK)).as("on Chromium's thread")
                .hasValue(new Answer(OpenerBridge.REFUSED, "Only a page's top frame can use Jasper's opener bridge."));
            var answers = new ArrayList<Answer>();
            bridge.answer(page.party(), false, bridge.sender(page.page, "child", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(answers).extracting(Answer::code).containsExactly(OpenerBridge.REFUSED);
            assertThat(held).isEmpty();
            assertThat(bridge.loginQueries()).isZero();
        });
    }

    @Test void aLoginPickToAPageWithoutAPasswordManagerIsRefused() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var answers = new ArrayList<Answer>();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(answers).containsExactly(new Answer(OpenerBridge.REFUSED, "This page has no password manager."));
            assertThat(bridge.waiting()).isZero();
            assertThat(bridge.loginQueries()).isZero();
        });
    }

    /**
     * Plan 8's F14: a handler that throws leaves its query answered {@code REFUSED}, once, and settled; one that
     * answered first keeps its answer; an {@link Error} is answered, once, and thrown on.
     */
    @Test void aLoginPickWhoseHandlerThrowsIsRefusedOnce() throws Exception {
        onUiThread(() -> {
            var answers = new ArrayList<Answer>();
            var broken = new Document(APP);
            broken.page.handleLogins("login();", query -> { throw new IllegalStateException("the picker broke"); });
            try (var log = new CapturedLog(OpenerBridge.class.getName())) {
                bridge.answer(broken.party(), true, bridge.sender(broken.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
                assertThat(log.records()).singleElement().satisfies(record -> assertThat(record.getThrown()).hasMessage("the picker broke"));
            }
            assertThat(answers).containsExactly(new Answer(OpenerBridge.REFUSED, "Jasper could not answer this request."));

            var answering = new Document(APP);
            answering.page.handleLogins("login();", query -> {
                query.fail(PageQuery.BUSY, "Busy.");
                throw new IllegalStateException("after its answer");
            });
            try (var log = new CapturedLog(OpenerBridge.class.getName())) {
                bridge.answer(answering.party(), true, bridge.sender(answering.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
                assertThat(log.records()).hasSize(1);
            }
            assertThat(answers).hasSize(2).last().isEqualTo(new Answer(OpenerBridge.BUSY, "Busy."));

            var failing = new Error("the page's item broke");
            var erring = new Document(APP);
            erring.page.handleLogins("login();", query -> { throw failing; });
            assertThatThrownBy(() -> bridge.answer(erring.party(), true, bridge.sender(erring.page, "main", () -> APP), asked(), PICK, NOW,
                answers::add)).isSameAs(failing);
            assertThat(answers).hasSize(3).last().isEqualTo(new Answer(OpenerBridge.REFUSED, "Jasper could not answer this request."));
            assertThat(bridge.waiting()).isZero();
            assertThat(bridge.loginQueries()).isZero();
        });
    }

    /**
     * Plan 8's F3: a pick's activation needs Jasper's record of a recent mouse press as well as the page's activation,
     * which it then consumes; a key alone activates the page but opens no picker, and spends nothing.
     */
    @Test void aLoginPicksActivationNeedsAMousePress() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            var press = new AtomicReference<LastPress>(LastPress.NONE);
            Party party = page.pressed(press::get);
            var answers = new ArrayList<Answer>();
            page.activation.activate(NOW - 100); // a key the page got
            bridge.answer(party, true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(held.getLast().consumeActivation()).as("a key without a press").isFalse();
            assertThat(page.activation.active(NOW)).as("nothing spent").isTrue();

            press.set(new LastPress(1, 0, NOW - 6_000));
            bridge.answer(party, true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(held.getLast().consumeActivation()).as("a press 6 s before the pick").isFalse();

            press.set(new LastPress(1, 0, NOW - 100));
            page.activation.activate(NOW - 100);
            bridge.answer(party, true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            PageQuery clicked = held.getLast();
            assertThat(clicked.consumeActivation()).as("a click").isTrue();
            assertThat(page.activation.active(NOW)).as("consumed").isFalse();
            assertThat(clicked.consumeActivation()).isFalse();
            assertThat(answers).isEmpty();
        });
    }

    /** Plan 8's F14: the answer reaches the page only while its main frame shows the origin that asked. */
    @Test void aLoginQueryIsToldGoneOnceThePageShowsAnotherOrigin() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            bridge.loadStarted(page.party(), APP);
            var answers = new ArrayList<Answer>();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            bridge.loadStarted(page.party(), LOGIN);
            assertThat(held.getFirst().answer("{\"password\":\"pw\"}")).isFalse();
            assertThat(answers).singleElement().satisfies(answer -> {
                assertThat(answer.code()).isEqualTo(OpenerBridge.GONE);
                assertThat(answer.text()).doesNotContain("pw");
            });
            assertThat(bridge.loginQueries()).isZero();
        });
    }

    /**
     * Plan 8's F14: Chromium cancels a query as its frame navigates or closes, or as the page cancels it: the bridge
     * finds the login query by its browser's identifier and its id, which then sends nothing.
     */
    @Test void chromiumsCancelReachesTheLoginQuery() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var cancels = new AtomicInteger();
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", query -> {
                held.add(query);
                query.onCancelled(cancels::incrementAndGet);
            });
            bridge.loadStarted(page.party(), APP);
            var answers = new ArrayList<Answer>();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), new QueryId(7, 41), PICK, NOW, answers::add);
            bridge.cancelled(8, 41);
            bridge.cancelled(7, 40);
            assertThat(cancels).as("another browser's or another query's cancel").hasValue(0);
            bridge.cancelled(7, 41);
            assertThat(cancels).hasValue(1);
            assertThat(bridge.loginQueries()).isZero();
            assertThat(held.getFirst().answer("{\"password\":\"pw\"}")).isFalse();
            assertThat(answers).isEmpty();
        });
        onUiThread(() -> { }); // a cancel that found nothing looks again on the UI thread
    }

    /**
     * Plan 8's F14: a cancel that comes on Chromium's thread before the UI thread took the query is not lost: the task
     * that hands the query over was posted first, and the cancel follows it.
     */
    @Test void aCancelBeforeTheUiThreadTakesTheQueryCancelsItOnceTaken() throws Exception {
        var made = new AtomicReference<Document>();
        var cancels = new AtomicInteger();
        var held = new CopyOnWriteArrayList<PageQuery>();
        onUiThread(() -> {
            var page = new Document(APP);
            page.page.handleLogins("login();", query -> {
                held.add(query);
                query.onCancelled(cancels::incrementAndGet);
            });
            bridge.loadStarted(page.party(), APP);
            made.set(page);
        });
        Document page = made.get();
        var answers = new CopyOnWriteArrayList<Answer>();
        OpenerBridge.Sender from = bridge.sender(page.page, "main", () -> APP);
        // As Queries does on Chromium's thread: the query goes to the UI thread, then Chromium cancels it.
        EventQueue.invokeLater(() -> bridge.answer(page.party(), true, from, new QueryId(3, 9), PICK, NOW, answers::add));
        bridge.cancelled(3, 9);
        onUiThread(() -> { });
        assertThat(held).hasSize(1);
        assertThat(cancels).hasValue(1);
        assertThat(held.getFirst().answer("{\"password\":\"pw\"}")).isFalse();
        assertThat(answers).isEmpty();
        assertThat(bridge.loginQueries()).isZero();
    }

    /** Plan 8's F14: no login query outlives its page: the page's close cancels it, and it leaves the bridge. */
    @Test void aLoginQueryLeavesTheBridgeAsItsPageCloses() throws Exception {
        onUiThread(() -> {
            var page = new Document(APP);
            var held = new ArrayList<PageQuery>();
            page.page.handleLogins("login();", held::add);
            bridge.loadStarted(page.party(), APP);
            var answers = new ArrayList<Answer>();
            bridge.answer(page.party(), true, bridge.sender(page.page, "main", () -> APP), asked(), PICK, NOW, answers::add);
            assertThat(bridge.loginQueries()).isOne();
            page.page.close();
            assertThat(bridge.loginQueries()).isZero();
            assertThat(held.getFirst().answer("{\"password\":\"pw\"}")).isFalse();
            assertThat(answers).isEmpty();
        });
    }

    /** A page's document as a recording stand-in: the scripts run in it, where it is, and what it asked of its item. */
    private static final class Document {
        final Page page;
        final Activation activation = new Activation();
        final List<String> scripts = new java.util.ArrayList<>(), opened = new java.util.ArrayList<>();
        final List<Document> popups = new java.util.ArrayList<>();
        String address;
        boolean showing = true, failing;
        Error breaking;
        int closes, shows;

        Document(String address) {
            this.address = address;
            page = new Page(URI.create(address), () -> { }, NO_FAVICONS, Runnable::run, PageContext.persistent());
            page.onOpenItem((url, placement) -> {
                opened.add(placement + " " + url);
                var popup = new Document(url);
                popups.add(popup);
                return Optional.of(popup.page);
            });
            page.onCloseItem(() -> {
                closes++;
                page.close();
            });
            page.onShowItem(() -> shows++);
        }

        Party party() {
            return new Party(page, activation, () -> showing, this::run, () -> address);
        }

        /** The page as the bridge reaches it, with Jasper's record of its last mouse press. */
        Party pressed(java.util.function.Supplier<LastPress> press) {
            return new Party(page, activation, press, () -> showing, this::run, () -> address);
        }

        private void run(String script) {
            if (breaking != null) throw breaking;
            if (failing) throw new IllegalStateException("the page's browser has gone");
            scripts.add(script);
        }
    }

    private record Linked(Document opener, Document popup, String id) { }

    /** An opener at {@link #APP} whose activated open made a popup at {@link #LOGIN} that has started loading. */
    private Linked link() {
        var opener = new Document(APP);
        opener.activation.activate(NOW - 100);
        Answer answer = ask(opener, open(LOGIN));
        assertThat(answer.ok()).as(answer.toString()).isTrue();
        var popup = opener.popups.getLast();
        assertThat(bridge.loadStarted(popup.party(), LOGIN)).contains("\"opener\"");
        assertThat(popup.scripts).isEmpty();
        return new Linked(opener, popup, answer.text());
    }

    /**
     * Whether an opener at {@link #APP} with {@code openerPolicy} keeps a popup at {@code url} whose first document has
     * {@code popupPolicy}.
     */
    private boolean popupStays(String openerPolicy, String url, String popupPolicy) {
        var opener = new Document(APP);
        bridge.responded(opener.page, APP, openerPolicy, List.of());
        bridge.loadStarted(opener.party(), APP);
        opener.activation.activate(NOW - 100);
        String id = ask(opener, open(url)).text();
        var popup = opener.popups.getFirst();
        bridge.responded(popup.page, url, popupPolicy, List.of());
        boolean stays = bridge.loadStarted(popup.party(), url).contains("\"opener\":\"" + id + "\"");
        assertThat(opener.scripts.isEmpty()).as("the opener is told only when the popup leaves").isEqualTo(stays);
        return stays;
    }

    /** A popup of {@code opener} that loaded, took a message and closed: a weak reference to its page. */
    private WeakReference<Page> closedPopup(Document opener) {
        opener.activation.activate(NOW - 100);
        String id = ask(opener, open(LOGIN)).text();
        var popup = opener.popups.removeLast();
        bridge.loadStarted(popup.party(), LOGIN);
        assertThat(ask(popup, post(id, "*", "1")).ok()).isTrue();
        popup.page.close();
        assertThat(bridge.holds(popup.page)).isFalse();
        return new WeakReference<>(popup.page);
    }

    /** An opener that closed while its popup, added to {@code popups}, lives on: a weak reference to its page. */
    private WeakReference<Page> closedOpener(List<Document> popups) {
        var opener = new Document(APP);
        opener.activation.activate(NOW - 100);
        assertThat(ask(opener, open(LOGIN)).ok()).isTrue();
        var popup = opener.popups.getFirst();
        popups.add(popup);
        bridge.loadStarted(popup.party(), LOGIN);
        opener.page.close();
        assertThat(bridge.holds(opener.page)).isFalse();
        assertThat(bridge.holds(popup.page)).isTrue();
        return new WeakReference<>(opener.page);
    }

    /**
     * Gives {@code page} the history Chromium would report, and has it ready, as a page whose browser exists is; its
     * browser stays absent, so going where its history allows fails once the page has told its listeners.
     */
    private static void history(Page page, boolean back, boolean forward) {
        try {
            for (var field : List.of("ready", "canGoBack", "canGoForward")) {
                var each = Page.class.getDeclaredField(field);
                each.setAccessible(true);
                each.setBoolean(page, switch (field) {
                    case "canGoBack" -> back;
                    case "canGoForward" -> forward;
                    default -> true;
                });
            }
        } catch (ReflectiveOperationException unexpected) {
            throw new AssertionError(unexpected);
        }
    }

    private static void awaitCollected(WeakReference<?> reference, String what) {
        for (int attempt = 0; attempt < 100 && reference.get() != null; attempt++) {
            System.gc();
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        assertThat(reference.get()).as(what + " is collected").isNull();
    }

    /** A query as Chromium names it, its own: browser 1's next. */
    private QueryId asked() {
        return new QueryId(1, ++queries);
    }

    private Answer ask(Document from, String request) {
        return bridge.request(from.party(), true, from.address, request, NOW);
    }

    private static String open(String url) {
        return "{\"op\":\"open\",\"url\":\"" + url + "\"}";
    }

    private static String favicon(String url) {
        return "{\"op\":\"favicon\",\"url\":" + StrictJson.quote(url) + "}";
    }

    /** What a frame's {@code find.js} answers a search: its count, the pattern valid. */
    private static String findAnswer(long search, int count) {
        return "{\"op\":\"find\",\"search\":" + search + ",\"count\":" + count + ",\"invalid\":false}";
    }

    private static String post(String id, String targetOrigin, String data) {
        return "{\"op\":\"post\",\"id\":\"" + id + "\",\"targetOrigin\":\"" + targetOrigin + "\",\"data\":" + StrictJson.quote(data) + "}";
    }

    private static void onUiThread(Runnable test) throws Exception {
        var failure = new Throwable[1];
        SwingUtilities.invokeAndWait(() -> {
            try {
                test.run();
            } catch (Throwable thrown) {
                failure[0] = thrown;
            }
        });
        if (failure[0] instanceof Error error) throw error;
        if (failure[0] instanceof Exception exception) throw exception;
    }
}
