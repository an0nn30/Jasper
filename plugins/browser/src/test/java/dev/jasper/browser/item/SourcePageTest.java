package dev.jasper.browser.item;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.awt.EventQueue;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Spec section 10's View Source page, {@code jasper-about:source}, without Chromium: the source escaped, its tags,
 * attributes, strings and comments in the theme's syntax colours and its http(s) URLs links, within a bound; the
 * heading's URL without user info (R46); an http(s) page's source fetched again from a local server, and what the page
 * shows when that fails or for a page that is no web page (P42).
 */
class SourcePageTest {
    private static final WorkspacePalette PALETTE = new WorkspacePalette(new Color(0x10, 0x20, 0x30), new Color(0x11, 0x21, 0x31),
        Color.BLACK, new Color(0xee, 0xee, 0xee), new Color(0x99, 0x99, 0x99), new Color(0x44, 0x44, 0x44, 0x80), Color.BLUE,
        Color.WHITE, Color.GRAY, new Color(0x33, 0x66, 0xff), Color.GREEN, new Color(0xff, 0xaa, 0x00), new Color(0xdd, 0x22, 0x11));
    private static final SyntaxPalette SYNTAX = syntax();
    private static final String PAGE = "https://example.com/";
    private static final String TOKEN = "0123456789abcdef0123456789abcdef";
    private static final Executor STARTS = task -> Thread.ofVirtual().start(task);
    private final CountDownLatch release = new CountDownLatch(1);
    private final List<String> asked = new CopyOnWriteArrayList<>();
    private final List<String> headers = new CopyOnWriteArrayList<>();
    private HttpServer server;
    private FakePluginHost host;
    private SourcePage sources;

    @BeforeEach void serve() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        handle("/page", exchange -> answer(exchange, 200, "text/html; charset=utf-8", "<p class=\"fresh\">fresh</p>", StandardCharsets.UTF_8));
        handle("/latin", exchange -> answer(exchange, 200, "text/html; charset=ISO-8859-1", "<p>café</p>", StandardCharsets.ISO_8859_1));
        handle("/declared", exchange -> answer(exchange, 200, "text/html",
            "<meta charset=\"windows-1252\"><p>naïve</p>", Charset.forName("windows-1252")));
        handle("/missing", exchange -> answer(exchange, 404, "text/html", "<p>not found</p>", StandardCharsets.UTF_8));
        handle("/broken", exchange -> answer(exchange, 500, "text/html", "<p>broken</p>", StandardCharsets.UTF_8));
        handle("/huge", exchange -> answer(exchange, 200, "text/html", "a".repeat(SourcePage.LIMIT + 1), StandardCharsets.UTF_8));
        handle("/silent", exchange -> {
            hold();
            answer(exchange, 200, "text/html", "<p>late</p>", StandardCharsets.UTF_8);
        });
        server.start();
    }

    @AfterEach void stop() throws Exception {
        release.countDown();
        if (sources != null) sources.close();
        server.stop(0);
        ((ExecutorService) server.getExecutor()).shutdownNow();
        SwingUtilities.invokeAndWait(() -> { if (host != null) host.close(); });
    }

    @Test void theSourceIsEscapedSoAScriptShowsAsText() {
        String html = SourcePage.html(PAGE, "<script>alert('hi') && 1 < 2</script><img src=x onerror=alert(1)>", PALETTE, SYNTAX, true);
        assertThat(html).as("the page runs no script of its own or the source's").doesNotContainIgnoringCase("<script")
            .doesNotContain("<img");
        assertThat(source(html)).contains("&lt;script", "alert('hi') &amp;&amp; 1 &lt; 2", "&lt;/script", "&lt;img");
        assertThat(html).contains("<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; style-src 'unsafe-inline'\">",
            "<meta name=\"referrer\" content=\"no-referrer\">", "<meta name=\"color-scheme\" content=\"dark\">");
        assertThat(html).doesNotContain("transition", "animation");
        assertThat(SourcePage.html(PAGE, "", PALETTE, SYNTAX, false)).contains("content=\"light\"");
    }

    @Test void tagsAttributesStringsAndCommentsAreHighlightedInTheThemesSyntaxColours() {
        String html = SourcePage.html(PAGE, "<!doctype html>\n<!-- a <b>comment</b> -->\n<a href=\"/x\" class='y' data-z=w hidden>text</a><br/>",
            PALETTE, SYNTAX, true);
        assertThat(source(html)).isEqualTo("<span class=\"tag\">&lt;!doctype html&gt;</span>\n"
            + "<span class=\"comment\">&lt;!-- a &lt;b&gt;comment&lt;/b&gt; --&gt;</span>\n"
            + "<span class=\"tag\">&lt;a</span> <span class=\"attribute\">href</span>=<span class=\"string\">\"/x\"</span>"
            + " <span class=\"attribute\">class</span>=<span class=\"string\">'y'</span>"
            + " <span class=\"attribute\">data-z</span>=<span class=\"string\">w</span>"
            + " <span class=\"attribute\">hidden</span><span class=\"tag\">&gt;</span>text"
            + "<span class=\"tag\">&lt;/a</span><span class=\"tag\">&gt;</span><span class=\"tag\">&lt;br</span><span class=\"tag\">/&gt;</span>");
        assertThat(html).contains(".tag { color: #e06c75; font-weight: bold; }", ".attribute { color: #d19a66; }",
            ".string { color: #98c379; }", ".comment { color: #7f848e; font-style: italic; }", "--background: #102030;",
            "--text: #eeeeee;");
    }

    /** As HTML parses them: a script's, a style's and a title's text is no markup, up to the element's end tag. */
    @Test void theTextOfScriptsStylesAndTitlesIsNoMarkup() {
        String html = SourcePage.html(PAGE, "<script type=\"module\">if (a<b && c>d) x = \"<p>\";</SCRIPT><style>a<b{}</style>"
            + "<title><i>t</i></title><p>", PALETTE, SYNTAX, true);
        assertThat(source(html)).isEqualTo("<span class=\"tag\">&lt;script</span> <span class=\"attribute\">type</span>="
            + "<span class=\"string\">\"module\"</span><span class=\"tag\">&gt;</span>if (a&lt;b &amp;&amp; c&gt;d) x = \"&lt;p&gt;\";"
            + "<span class=\"tag\">&lt;/SCRIPT</span><span class=\"tag\">&gt;</span>"
            + "<span class=\"tag\">&lt;style</span><span class=\"tag\">&gt;</span>a&lt;b{}<span class=\"tag\">&lt;/style</span><span class=\"tag\">&gt;</span>"
            + "<span class=\"tag\">&lt;title</span><span class=\"tag\">&gt;</span>&lt;i&gt;t&lt;/i&gt;"
            + "<span class=\"tag\">&lt;/title</span><span class=\"tag\">&gt;</span><span class=\"tag\">&lt;p</span><span class=\"tag\">&gt;</span>");
    }

    /** What is no tag stays text, and markup the source leaves open runs to its end. */
    @Test void aLessThanSignThatStartsNoTagIsTextAndUnclosedMarkupRunsToTheEnd() {
        assertThat(source(SourcePage.html(PAGE, "1 < 2 <3 <= a", PALETTE, SYNTAX, true))).isEqualTo("1 &lt; 2 &lt;3 &lt;= a");
        assertThat(source(SourcePage.html(PAGE, "<!-- open", PALETTE, SYNTAX, true))).isEqualTo("<span class=\"comment\">&lt;!-- open</span>");
        assertThat(source(SourcePage.html(PAGE, "<a href=\"open", PALETTE, SYNTAX, true)))
            .isEqualTo("<span class=\"tag\">&lt;a</span> <span class=\"attribute\">href</span>=<span class=\"string\">\"open</span>");
        assertThat(source(SourcePage.html(PAGE, "<script>never closed <p>", PALETTE, SYNTAX, true)))
            .isEqualTo("<span class=\"tag\">&lt;script</span><span class=\"tag\">&gt;</span>never closed &lt;p&gt;");
    }

    @Test void httpAndHttpsUrlsInAttributeValuesAreLinksAndNothingElseIs() {
        String html = source(SourcePage.html(PAGE, "<a href=\"https://example.com/a?b=1&amp;c=2\">https://example.org/</a>"
            + "<img src=HTTP://Example.com/i.png srcset=\"https://a.test/1.png 1x, https://a.test/2.png 2x\">"
            + "<a href=\"javascript:alert(1)\" data-a=\"/relative\" data-b=\"data:text/html,x\" data-c=\"&#104;ttps://x.test/\""
            + " data-d=\" https://lead.test/\" data-e=\"ftp://x.test/\">", PALETTE, SYNTAX, true));
        assertThat(html).contains("<span class=\"string\">\"<a href=\"https://example.com/a?b=1&amp;c=2\">https://example.com/a?b=1&amp;amp;c=2</a>\"</span>",
            "<span class=\"string\"><a href=\"HTTP://Example.com/i.png\">HTTP://Example.com/i.png</a></span>",
            "\"<a href=\"https://a.test/1.png\">https://a.test/1.png</a> 1x, <a href=\"https://a.test/2.png\">https://a.test/2.png</a> 2x\"",
            "\" <a href=\"https://lead.test/\">https://lead.test/</a>\"");
        assertThat(Pattern.compile("<a href=").matcher(html).results().count()).as("the links in attribute values only, not in text").isEqualTo(5);
        assertThat(html).contains("<span class=\"string\">\"javascript:alert(1)\"</span>", "\"&amp;#104;ttps://x.test/\"", "\"ftp://x.test/\"",
            "\"/relative\"", "\"data:text/html,x\"");
    }

    /** A link's address is the value as the source wrote it: what would end its attribute is written as a reference. */
    @Test void aLinksValueCannotEndItsAttribute() {
        String html = source(SourcePage.html(PAGE, "<a title='https://x.test/\"onmouseover=\"alert(1)<b>'>", PALETTE, SYNTAX, true));
        assertThat(html).contains("<a href=\"https://x.test/&quot;onmouseover=&quot;alert(1)&lt;b&gt;\">"
            + "https://x.test/\"onmouseover=\"alert(1)&lt;b&gt;</a>");
        assertThat(html).doesNotContain(" onmouseover=\"");
    }

    @Test void theHeadingIsPageSourceAndTheUrlWithoutUserInfo() {
        String html = SourcePage.html("https://ada:secret@example.com/path?q=1", "<p>", PALETTE, SYNTAX, true);
        assertThat(html).contains("<h1>Page Source</h1>", "<p class=\"address\">https://example.com/path?q=1</p>",
            "<title>Page Source – example.com</title>");
        assertThat(html).doesNotContain("secret", "ada");
        assertThat(SourcePage.html("file:///tmp/a.html", "", PALETTE, SYNTAX, true)).contains("<title>Page Source – file:///tmp/a.html</title>");
        assertThat(SourcePage.html("https://example.com/<b>\"", "", PALETTE, SYNTAX, true)).contains("https://example.com/&lt;b&gt;&quot;");
    }

    /**
     * The bound: the first {@value SourcePage#HIGHLIGHTED} characters are highlighted and linked and the rest is plain
     * escaped text; a source longer than {@value SourcePage#LIMIT} characters shows that many. Each part says so.
     */
    @Test void aLongSourceIsHighlightedOnlyAtFirstAndCutAtTheLimit() {
        String tags = "<i>".repeat(SourcePage.HIGHLIGHTED / 3 + 10);
        String html = SourcePage.html(PAGE, tags, PALETTE, SYNTAX, true);
        long spans = Pattern.compile("<span ").matcher(html).results().count();
        assertThat(spans).isBetween(2L * (SourcePage.HIGHLIGHTED / 3), 2L * (SourcePage.HIGHLIGHTED / 3) + 2);
        assertThat(html).endsWith("&lt;i&gt;&lt;i&gt;</pre>\n</body>\n</html>\n")
            .contains("<p class=\"note\">Only the first 1,000,000 characters are highlighted.</p>").doesNotContain("Jasper shows the first");
        String attributes = "<a" + " b".repeat(SourcePage.LIMIT);
        String longest = SourcePage.html(PAGE, attributes, PALETTE, SYNTAX, true);
        assertThat(source(longest)).as("the limit's characters, the rest left out").endsWith(" b".repeat(10))
            .hasSizeLessThan(4 * SourcePage.LIMIT);
        assertThat(Pattern.compile("<span ").matcher(longest).results().count()).as("attributes highlighted up to the bound only")
            .isLessThanOrEqualTo(SourcePage.HIGHLIGHTED / 2 + 2);
        assertThat(longest).contains("<p class=\"note\">Jasper shows the first 5,242,880 characters of this page’s source.</p>",
            "<p class=\"note\">Only the first 1,000,000 characters are highlighted.</p>");
        assertThat(SourcePage.html(PAGE, "<p>", PALETTE, SYNTAX, true)).doesNotContain("class=\"note\"");
    }

    @Test void theSourceAddressCarriesThePageWithoutUserInfoAndTheToken() {
        URI address = SourcePage.address("https://ada:pw@example.com/a?b=1#c", Optional.of(TOKEN));
        assertThat(address).hasToString("jasper-about:source?url=https%3A%2F%2Fexample.com%2Fa%3Fb%3D1%23c&token=" + TOKEN);
        assertThat(SourcePage.page(address.toString())).contains("https://example.com/a?b=1#c");
        assertThat(SourcePage.token(address.toString())).contains(TOKEN);
        String chromiums = "jasper-about://source/?url=file%3A%2F%2F%2Ftmp%2Fa.html&token=" + TOKEN;
        assertThat(SourcePage.page(chromiums)).contains("file:///tmp/a.html");
        assertThat(SourcePage.token(chromiums)).contains(TOKEN);
        assertThat(SourcePage.address("file:///tmp/a.html", Optional.empty())).hasToString("jasper-about:source?url=file%3A%2F%2F%2Ftmp%2Fa.html");
        assertThat(SourcePage.page("jasper-about:source?url=https%3A%2F%2Fada%3Apw%40example.com%2F")).as("a written address's user info")
            .contains("https://example.com/");
        assertThat(SourcePage.token("jasper-about:source?url=x&token=ABCDEF0123456789abcdef0123456789")).as("not one Jasper makes").isEmpty();
        assertThat(SourcePage.token("jasper-about:error?token=" + TOKEN)).as("another page's").isEmpty();
        assertThat(SourcePage.page("jasper-about:source")).isEmpty();
        assertThat(SourcePage.saved(URI.create(chromiums))).as("restored without its token")
            .isEqualTo(URI.create("jasper-about:source?url=file%3A%2F%2F%2Ftmp%2Fa.html"));
        assertThat(SourcePage.saved(URI.create(PAGE))).isEqualTo(URI.create(PAGE));
        assertThat(SourcePage.saved(URI.create("jasper-about:error?token=" + TOKEN))).isEqualTo(URI.create("jasper-about:error?token=" + TOKEN));
    }

    /**
     * Plan 4b's W7: the source page of an error page is saved as that of the address that failed, as Chrome's View Source
     * of an error page shows the failed address's source; the error page's tokens end with the item.
     */
    @Test void anErrorPagesSourcePageIsSavedAsTheFailedAddresssWithoutItsTokens() {
        String error = "jasper-about:error?token=" + TOKEN + "&code=ERR_CONNECTION_REFUSED&url=https%3A%2F%2Fexample.com%2Fa&proceed=" + TOKEN;
        for (String inner : List.of(error, error.replace("jasper-about:error?", "jasper-about://error/?"))) {
            URI saved = SourcePage.saved(SourcePage.address(inner, Optional.of(TOKEN)));
            assertThat(saved).as(inner).isEqualTo(URI.create("jasper-about:source?url=https%3A%2F%2Fexample.com%2Fa"));
            assertThat(saved.toString()).doesNotContain(TOKEN);
        }
        assertThat(SourcePage.saved(SourcePage.address("jasper-about:error?token=" + TOKEN, Optional.empty()))).as("no failed address")
            .isEqualTo(URI.create("jasper-about:source"));
        assertThat(SourcePage.saved(SourcePage.address("jasper-about:welcome", Optional.of(TOKEN)))).as("another Jasper page")
            .isEqualTo(URI.create("jasper-about:source?url=jasper-about%3Awelcome"));
    }

    /** An http(s) page's source is fetched again, without cookies, and the fresh source wins over the item's capture. */
    @Test void aWebPagesSourceIsFetchedAgainAndUsedOnA200() throws Exception {
        open((token, page) -> Optional.of("<p>captured</p>"), SourcePage.TIMEOUT);
        String html = sources.html(about(at("/page"), Optional.of(TOKEN))).get(10, TimeUnit.SECONDS);
        assertThat(html).contains("<p class=\"address\">" + at("/page") + "</p>", "<span class=\"string\">\"fresh\"</span>")
            .doesNotContain("captured");
        assertThat(asked).containsExactly("/page");
        assertThat(headers).as("no cookie or credentials").noneMatch(name -> name.equalsIgnoreCase("Cookie") || name.equalsIgnoreCase("Authorization"));
        assertThat(sources.html(about(at("/latin"), Optional.empty())).get(10, TimeUnit.SECONDS)).as("in the charset the answer names")
            .contains("café");
        assertThat(sources.html(about(at("/declared"), Optional.empty())).get(10, TimeUnit.SECONDS)).as("or the document declares")
            .contains("naïve");
    }

    /** A written address's user info is never sent: the page is fetched without it, and shown without it. */
    @Test void aWebPageIsFetchedWithoutTheUserInfoAnAddressWrites() throws Exception {
        open((token, page) -> Optional.empty(), SourcePage.TIMEOUT);
        String withUser = at("/page").replace("http://", "http://ada:secret@");
        String html = sources.html(URI.create("jasper-about:source?url=" + java.net.URLEncoder.encode(withUser, StandardCharsets.UTF_8)))
            .get(10, TimeUnit.SECONDS);
        assertThat(html).contains("fresh").doesNotContain("secret");
        assertThat(headers).noneMatch(name -> name.equalsIgnoreCase("Authorization"));
    }

    /** Anything but a 200 within the limits falls back to the item's capture, or says the source could not be loaded. */
    @Test void aFetchThatFailsUsesTheCaptureTheItemHoldsOrSaysSo() throws Exception {
        var looked = new CopyOnWriteArrayList<String>();
        open((token, page) -> {
            looked.add(token + " " + page + " " + EventQueue.isDispatchThread());
            return token.equals(TOKEN) ? Optional.of("<p>captured " + page + "</p>") : Optional.empty();
        }, Duration.ofMillis(500));
        for (String path : List.of("/missing", "/broken", "/huge", "/silent")) {
            assertThat(sources.html(about(at(path), Optional.of(TOKEN))).get(10, TimeUnit.SECONDS)).as(path)
                .contains("captured " + at(path)).doesNotContain(SourcePage.NOT_LOADED);
            assertThat(sources.html(about(at(path), Optional.empty())).get(10, TimeUnit.SECONDS)).as(path + " with nothing captured")
                .contains("<p class=\"message\">" + SourcePage.NOT_LOADED + "</p>").doesNotContain("<pre>");
        }
        assertThat(looked).as("asked on the UI thread, with the page's address").allMatch(line -> line.endsWith(" true"))
            .contains(TOKEN + " " + at("/missing") + " true");
        assertThat(sources.html(about(at("/broken"), Optional.of("f".repeat(32)))).get(10, TimeUnit.SECONDS)).as("a token the item does not hold")
            .contains(SourcePage.NOT_LOADED);
    }

    /**
     * A page that is no web page shows only what the item captured under the address's token; for a forged address, with
     * no token or one the item does not hold, nothing is read: the page says its source is no longer available.
     */
    @Test void aLocalPagesSourceIsOnlyTheItemsCapture(@TempDir Path folder) throws Exception {
        Path secret = Files.writeString(folder.resolve("secret.html"), "<p>SECRET</p>");
        String local = secret.toUri().toString();
        open((token, page) -> token.equals(TOKEN) && page.equals(local) ? Optional.of("<p>captured</p>") : Optional.empty(), SourcePage.TIMEOUT);
        assertThat(sources.html(about(local, Optional.of(TOKEN))).get(10, TimeUnit.SECONDS)).contains("<p class=\"address\">" + local + "</p>",
            "captured");
        for (URI forged : List.of(about(local, Optional.empty()), about(local, Optional.of("e".repeat(32))),
                URI.create("jasper-about://source/?url=" + java.net.URLEncoder.encode(local, StandardCharsets.UTF_8) + "&token=" + TOKEN + "x"))) {
            String html = sources.html(forged).get(10, TimeUnit.SECONDS);
            assertThat(html).as(forged.toString()).contains("<p class=\"message\">" + SourcePage.UNAVAILABLE + "</p>").doesNotContain("SECRET", "captured");
        }
        assertThat(sources.html(URI.create("jasper-about:source")).get(10, TimeUnit.SECONDS)).as("no page at all").contains(SourcePage.UNAVAILABLE);
        assertThat(asked).as("nothing fetched").isEmpty();
    }

    /** Reads the source page's addresses with a look-up of the item's captures, in the fake host's theme. */
    private void open(BiFunction<String, String, Optional<String>> captured, Duration timeout) throws Exception {
        var made = new AtomicReference<SourcePage>();
        SwingUtilities.invokeAndWait(() -> {
            host = new FakePluginHost();
            var context = new AtomicReference<PluginContext>();
            host.start(new PluginInfo("dev.jasper.browser", "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), context::set);
            made.set(new SourcePage(context.get().appearance(), STARTS, captured, timeout));
        });
        sources = made.get();
    }

    /** What the page shows of the source: the text of its {@code pre}. */
    private static String source(String html) {
        Matcher pre = Pattern.compile("<pre>\n(.*)</pre>", Pattern.DOTALL).matcher(html);
        assertThat(pre.find()).as("the page shows the source").isTrue();
        return pre.group(1);
    }

    private static URI about(String page, Optional<String> token) {
        return SourcePage.address(page, token);
    }

    private String at(String path) {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
    }

    private void handle(String path, HttpHandler handler) {
        server.createContext(path, exchange -> {
            asked.add(exchange.getRequestURI().getPath());
            headers.addAll(exchange.getRequestHeaders().keySet());
            handler.handle(exchange);
        });
    }

    private void hold() {
        try {
            release.await(10, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static void answer(HttpExchange exchange, int status, String type, String body, Charset charset) throws IOException {
        byte[] bytes = body.getBytes(charset);
        if (!type.isEmpty()) exchange.getResponseHeaders().add("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    /** Every role a style of its own; a tag bold and a comment italic, as a theme may make them. */
    private static SyntaxPalette syntax() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (SyntaxRole role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(Color.MAGENTA, false, false));
        styles.put(SyntaxRole.TAG, new SyntaxStyle(new Color(0xe0, 0x6c, 0x75), true, false));
        styles.put(SyntaxRole.ATTRIBUTE, new SyntaxStyle(new Color(0xd1, 0x9a, 0x66), false, false));
        styles.put(SyntaxRole.STRING, new SyntaxStyle(new Color(0x98, 0xc3, 0x79), false, false));
        styles.put(SyntaxRole.COMMENT, new SyntaxStyle(new Color(0x7f, 0x84, 0x8e), false, true));
        return new SyntaxPalette(styles);
    }
}
