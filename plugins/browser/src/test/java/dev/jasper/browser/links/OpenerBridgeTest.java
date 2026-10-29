package dev.jasper.browser.links;

import dev.jasper.browser.links.OpenerBridge.Answer;
import dev.jasper.browser.links.OpenerBridge.Party;
import dev.jasper.browser.page.Activation;
import dev.jasper.browser.page.Page;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * The opener bridge's Java side (spec section 8; plan 4a Task 8). Each page's document is a recording stand-in that
 * takes the scripts Jasper would run in it, so no Chromium is needed: what each request opens, delivers, closes or
 * refuses, and what each page is given as it starts loading. Every request is untrusted: only a page's top frame may
 * ask, an {@code open} needs and consumes Jasper's own record of the user's activation, the other requests need the
 * asking page to be one end of the link, and message data is bounded and reaches the other page only as an escaped
 * string literal. Pages run on the UI thread, as Jasper's do.
 */
class OpenerBridgeTest {
    private static final long NOW = 1_000_000;
    private static final String APP = "https://app.example/start", LOGIN = "https://login.example/sign-in";
    private final OpenerBridge bridge = new OpenerBridge();

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
            assertThat(bridge.loadStarted(popup.party())).as("the popup's window.opener")
                .isEqualTo(OpenerBridge.SOURCE + "\n({\"opener\":\"" + answer.text() + "\",\"closed\":false});");
            assertThat(bridge.loadStarted(opener.party())).as("every other page gets the window.open stand-in")
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

    @Test void anOpenAtAnotherSchemeOpensNothingAndKeepsTheActivation() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            for (String url : List.of("javascript:alert(1)", "mailto:someone@example.com", "data:text/html,hi", "chrome://settings"))
                assertThat(ask(opener, open(url)).code()).as(url).isEqualTo(OpenerBridge.REFUSED);
            assertThat(opener.opened).isEmpty();
            assertThat(opener.activation.active(NOW)).isTrue();
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
            assertThat(bridge.loadStarted(opener.popups.getFirst().party())).endsWith("\n({});");
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

    /** Messages to a popup that has not started loading are lost, as messages to a popup's first empty page are. */
    @Test void aMessageToAPopupThatHasNotLoadedIsLost() throws Exception {
        onUiThread(() -> {
            var opener = new Document(APP);
            opener.activation.activate(NOW - 100);
            String id = ask(opener, open(LOGIN)).text();
            assertThat(ask(opener, post(id, "*", "1")).ok()).isTrue();
            var popup = opener.popups.getFirst();
            assertThat(popup.scripts).isEmpty();
            bridge.loadStarted(popup.party());
            assertThat(ask(opener, post(id, "*", "2")).ok()).isTrue();
            assertThat(popup.scripts).singleElement().asString().contains(",\"2\",");
        });
    }

    @Test void closingThePopupMarksTheOpenersStandInClosedAndDropsItsMessages() throws Exception {
        onUiThread(() -> {
            var link = link();
            link.popup.page.close();
            assertThat(link.opener.scripts).singleElement().asString().contains(".closed(\"" + link.id + "\")");
            assertThat(ask(link.opener, post(link.id, "*", "1")).code()).isEqualTo(OpenerBridge.GONE);
            assertThat(ask(link.opener, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").code()).isEqualTo(OpenerBridge.GONE);
            assertThat(link.popup.scripts).isEmpty();
            assertThat(link.popup.shows).isZero();
            assertThat(bridge.links()).as("the opener still lives").isEqualTo(1);
            link.opener.page.close();
            assertThat(bridge.links()).as("forgotten once both are gone").isZero();
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
            assertThat(bridge.loadStarted(link.popup.party())).as("the popup's next document")
                .endsWith("\n({\"opener\":\"" + link.id + "\",\"closed\":true});");
            assertThat(bridge.links()).isEqualTo(1);
            link.popup.page.close();
            assertThat(bridge.links()).isZero();
            assertThat(bridge.loadStarted(link.popup.party())).endsWith("\n({});");
        });
    }

    /** {@code popup.close()} from the opener and {@code window.close()} in the popup both close the popup's item. */
    @Test void closeClosesThePopupsItemFromEitherEnd() throws Exception {
        onUiThread(() -> {
            var fromOpener = link();
            assertThat(ask(fromOpener.opener, "{\"op\":\"close\",\"id\":\"" + fromOpener.id + "\"}").ok()).isTrue();
            assertThat(fromOpener.popup.closes).isEqualTo(1);
            assertThat(fromOpener.opener.scripts).singleElement().asString().contains(".closed(\"" + fromOpener.id + "\")");
            assertThat(ask(fromOpener.opener, "{\"op\":\"close\",\"id\":\"" + fromOpener.id + "\"}").code()).isEqualTo(OpenerBridge.GONE);
            assertThat(fromOpener.popup.closes).isEqualTo(1);

            var fromPopup = link();
            assertThat(ask(fromPopup.popup, "{\"op\":\"close\",\"id\":\"" + fromPopup.id + "\"}").ok()).isTrue();
            assertThat(fromPopup.popup.closes).isEqualTo(1);
            assertThat(fromPopup.opener.closes).isZero();

            var orphan = link();
            orphan.opener.page.close();
            assertThat(ask(orphan.popup, "{\"op\":\"close\",\"id\":\"" + orphan.id + "\"}").ok()).as("after its opener closed").isTrue();
            assertThat(orphan.popup.closes).isEqualTo(1);
            assertThat(bridge.links()).as("the orphan's link is gone; the first two openers still live").isEqualTo(2);
        });
    }

    /**
     * {@code popup.focus()} shows the popup's item; {@code window.opener.focus()} shows the opener's only with the user's
     * activation, as Chromium lets only a window's opener focus it without one.
     */
    @Test void focusShowsTheOtherItemAndTheOpenersNeedsTheUsersActivation() throws Exception {
        onUiThread(() -> {
            var link = link();
            assertThat(ask(link.opener, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").ok()).isTrue();
            assertThat(link.popup.shows).isEqualTo(1);
            assertThat(ask(link.popup, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").code()).isEqualTo(OpenerBridge.REFUSED);
            assertThat(link.opener.shows).isZero();
            link.popup.activation.activate(NOW - 100);
            assertThat(ask(link.popup, "{\"op\":\"focus\",\"id\":\"" + link.id + "\"}").ok()).isTrue();
            assertThat(link.opener.shows).isEqualTo(1);
            assertThat(link.popup.activation.active(NOW)).as("focus consumes nothing").isTrue();
        });
    }

    @Test void aPageThatIsNeitherEndOfALinkCannotUseIt() throws Exception {
        onUiThread(() -> {
            var link = link();
            var stranger = new Document("https://app.example/other");
            stranger.activation.activate(NOW - 100);
            bridge.loadStarted(stranger.party());
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
            assertThat(BridgeJson.quote(data)).isEqualTo(literal);
            String script = link.opener.scripts.getFirst();
            assertThat(script).contains("\"popup\"," + literal + ",");
            assertThat(script.chars().filter(c -> c < 0x20 || c == 0x7F || c == 0x2028 || c == 0x2029 || Character.isSurrogate((char) c)))
                .as("raw characters that would end the literal or the line").isEmpty();
            assertThat(BridgeJson.object("{\"data\":" + literal + "}")).as("the literal reads back as JSON")
                .hasValueSatisfying(members -> assertThat(members.get("data")).isEqualTo(data));
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

    @Test void aRequestIsReadAsStrictJson() {
        assertThat(BridgeJson.object(" { \"op\" : \"post\" , \"data\" : \"a\\\"b\\\\c\\/d\\b\\f\\n\\r\\t\\u00e9\\uD83D\\uDE00\" , \"x\" : true , \"y\" : false } "))
            .hasValueSatisfying(members -> assertThat(members).containsExactly(java.util.Map.entry("op", "post"),
                java.util.Map.entry("data", "a\"b\\c/d\b\f\n\r\t\u00e9" + (char) 0xD83D + (char) 0xDE00), java.util.Map.entry("x", true),
                java.util.Map.entry("y", false)));
        assertThat(BridgeJson.object("{}")).hasValue(java.util.Map.of());
        for (String bad : List.of("{\"a\":null}", "{\"a\":1}", "{\"a\":tru}", "{\"a\":\"b\"", "{'a':'b'}", "{\"a\" \"b\"}", "{,}"))
            assertThat(BridgeJson.object(bad)).as(bad).isEmpty();
    }

    @Test void anOriginIsTheSchemeHostAndPortOfAWebAddress() {
        assertThat(OpenerBridge.origin("https://Example.COM:443/a?b#c")).contains("https://example.com");
        assertThat(OpenerBridge.origin("http://a.example:80/")).contains("http://a.example");
        assertThat(OpenerBridge.origin("http://a.example:8080")).contains("http://a.example:8080");
        assertThat(OpenerBridge.origin("https://user:p@ss@a.example/x")).contains("https://a.example");
        assertThat(OpenerBridge.origin("http://[::1]:8000/x")).contains("http://[::1]:8000");
        for (String url : new String[] {"file:///tmp/a.html", "about:blank", "jasper-about:welcome", "data:text/html,hi",
                "blob:https://a.example/0f", "https://", "https:///x", "", "a.example"})
            assertThat(OpenerBridge.origin(url)).as(url).isEmpty();
        assertThat(OpenerBridge.origin(null)).isEmpty();
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
        assertThat(file.strip()).contains("jasperBridgeQuery", "Symbol.for('dev.jasper.browser.opener-bridge')").endsWith("})");
        assertThat(file.chars().filter(c -> (c < 0x20 && c != '\n') || c >= 0x7F))
            .as("ASCII only, with no control character but the newline").isEmpty();
    }

    /** A page's document as a recording stand-in: the scripts run in it, where it is, and what it asked of its item. */
    private static final class Document {
        final Page page;
        final Activation activation = new Activation();
        final List<String> scripts = new java.util.ArrayList<>(), opened = new java.util.ArrayList<>();
        final List<Document> popups = new java.util.ArrayList<>();
        String address;
        int closes, shows;

        Document(String address) {
            this.address = address;
            page = new Page(URI.create(address), () -> { });
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
            return new Party(page, activation, scripts::add, () -> address);
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
        bridge.loadStarted(popup.party());
        assertThat(popup.scripts).isEmpty();
        return new Linked(opener, popup, answer.text());
    }

    private Answer ask(Document from, String request) {
        return bridge.request(from.party(), true, from.address, request, NOW);
    }

    private static String open(String url) {
        return "{\"op\":\"open\",\"url\":\"" + url + "\"}";
    }

    private static String post(String id, String targetOrigin, String data) {
        return "{\"op\":\"post\",\"id\":\"" + id + "\",\"targetOrigin\":\"" + targetOrigin + "\",\"data\":" + BridgeJson.quote(data) + "}";
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
