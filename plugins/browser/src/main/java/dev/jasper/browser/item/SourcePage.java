package dev.jasper.browser.item;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.http.WebFetch;
import dev.jasper.browser.page.NavigationHistory;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.ui.Appearance;
import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.EventQueue;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.function.BiFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * {@code jasper-about:source}, View Source's page (spec section 10; P42): the heading "Page Source", the page's address
 * without its user name and password (R46), and the page's source, escaped, its tags, attribute names, attribute values
 * and comments in the workspace theme's syntax colours, and the http and https URLs in its attribute values links. The
 * page runs no script, its content security policy loads nothing, and it sends no referrer.
 *
 * <p>Its address is {@code jasper-about:source?url=<page>}, with {@code &token=<token>} when the item captured the
 * page's HTML as View Source began ({@code SourcePages}). Whenever Chromium asks for the page, on a reload and after a
 * restore too, and for an address anyone wrote:
 * <ul>
 *   <li>an http or https page's source is fetched again as {@link WebFetch} fetches, without cookies or credentials,
 *       for at most {@value #LIMIT} bytes and {@link #TIMEOUT}, and only a 200's body is used;</li>
 *   <li>else, and for any other page, the source is what the item captured under the address's token for that page,
 *       while it holds it; without one the page says "{@value #UNAVAILABLE}", or for an http or https page
 *       "{@value #NOT_LOADED}". Nothing local is read for an address: only what an item captured itself.</li>
 * </ul>
 *
 * <p><b>The bound.</b> The page shows at most {@value #LIMIT} characters of a source, and highlights and links its first
 * {@value #HIGHLIGHTED}; past them the source is plain escaped text. Each says so on the page. A usual source makes a
 * page two or three times its size; the worst, the shortest tags up to the highlighting's end and ampersands after it,
 * some 20 characters for each highlighted one and 5 for each other, about 41 million in all: large, but bounded.
 *
 * <p>The page is built on the plugin's background executor, at most {@value #BUILDS} at once (plan 4b's P47), never on
 * Chromium's IO thread, which only starts the fetch or the look-up, nor on the UI thread, in the theme as it was last
 * read; a page already open shows a theme change when it loads again. Only the short page for an address without a
 * {@code url} is made at once, on the IO thread.
 */
public final class SourcePage implements AutoCloseable {
    /** The page's name. */
    public static final String NAME = "source";
    /** The longest Chromium waits for the page: the fetch's time, and the look-up and the building after it. */
    public static final Duration WAIT = Duration.ofSeconds(20);
    /** How long a fetch of a web page's source may take in all. */
    static final Duration TIMEOUT = Duration.ofSeconds(10);
    /** The most bytes of a fetched source, and the most characters of a source the page shows: 5 MB. */
    static final int LIMIT = 5 * 1024 * 1024;
    /** How many of a source's first characters are highlighted and linked. */
    static final int HIGHLIGHTED = 1_000_000;
    /** How many pages are built at once; the others wait their turn. */
    static final int BUILDS = 2;
    /** What the page says for a page that is no web page when the item holds no capture of it. */
    static final String UNAVAILABLE = "The source of this page is no longer available.";
    /** What the page says for a web page whose fetch failed when the item holds no capture of it. */
    static final String NOT_LOADED = "Jasper can’t load the source of this page.";
    private static final String URL = "url", TOKEN = "token";
    private static final String ACCEPT = "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8";
    private static final int MAX_TITLE = 100;
    // Elements whose text is no markup up to their end tag, as HTML parses them.
    private static final Set<String> RAW = Set.of("script", "style", "textarea", "title", "xmp", "iframe", "noembed", "noframes");
    // A charset a document declares in its first bytes, as a browser looks for one when its answer names none.
    private static final Pattern DECLARED = Pattern.compile("(?i)<meta[^>]*?charset\\s*=\\s*[\"']?\\s*([A-Za-z0-9_:.-]+)");
    private static final int DECLARED_WITHIN = 1024;
    private final Appearance appearance;
    private final Executor starts;
    private final BiFunction<String, String, Optional<String>> captured;
    private final WebFetch web;
    private final SourceBuilds builds;
    private volatile Look look;

    /**
     * The page in the workspace theme, now and after each theme change. UI thread, while the plugin starts.
     *
     * @param appearance the plugin's appearance
     * @param starts where fetches start and pages are built: the plugin's background executor
     * @param captured given a token and a page's address, the HTML an item captured under that token for that page, on
     *     the UI thread; empty when no item holds it
     */
    public SourcePage(Appearance appearance, Executor starts, BiFunction<String, String, Optional<String>> captured) {
        this(appearance, starts, captured, TIMEOUT);
    }

    /** As {@link #SourcePage(Appearance, Executor, BiFunction)}, giving up on a fetch after {@code timeout}. */
    SourcePage(Appearance appearance, Executor starts, BiFunction<String, String, Optional<String>> captured, Duration timeout) {
        this.appearance = Objects.requireNonNull(appearance, "appearance");
        this.starts = Objects.requireNonNull(starts, "starts");
        this.captured = Objects.requireNonNull(captured, "captured");
        web = new WebFetch(starts, timeout, LIMIT);
        builds = new SourceBuilds(BUILDS, starts);
        rebuild();
        appearance.onChanged(variant -> rebuild());
    }

    /**
     * The page for {@code address}, as the class says. Chromium's IO thread, held only while the fetch or the look-up
     * starts; cancelling the future ends the fetch, and a build still waiting its turn.
     *
     * @param address the address Chromium asked for, its query with it
     * @return the page's HTML, to come; it fails only when the plugin's executor no longer takes work
     */
    public CompletableFuture<String> html(URI address) {
        String written = address.toString();
        Look now = look;
        Optional<String> asked = page(written);
        if (asked.isEmpty()) return CompletableFuture.completedFuture(message("", UNAVAILABLE, now.palette(), now.syntax(), now.dark()));
        String page = asked.get();
        Optional<URI> fetched = webAddress(page);
        CompletableFuture<Optional<String>> fresh = fetched.map(this::fetch)
            .orElseGet(() -> CompletableFuture.completedFuture(Optional.empty()));
        Optional<String> token = token(written);
        String missing = fetched.isPresent() ? NOT_LOADED : UNAVAILABLE;
        var result = new CompletableFuture<String>();
        fresh.thenCompose(source -> source.isPresent() ? CompletableFuture.completedFuture(source) : held(token, page))
            .whenComplete((source, failure) -> {
                if (failure != null) {
                    result.completeExceptionally(failure);
                    return;
                }
                CompletableFuture<String> built = builds.build(() -> source.map(text -> html(page, text, now.palette(), now.syntax(),
                    now.dark())).orElseGet(() -> message(page, missing, now.palette(), now.syntax(), now.dark())));
                built.whenComplete((html, buildFailed) -> {
                    if (buildFailed == null) result.complete(html);
                    else result.completeExceptionally(buildFailed);
                });
                result.whenComplete((html, ended) -> built.cancel(true));
            });
        result.whenComplete((html, failure) -> fresh.cancel(true));
        return result;
    }

    /**
     * Fetches the source of the web page at {@code page} again: the body of a 200 within the limits, read in the charset
     * a byte order mark, the answer or the document's first {@value #DECLARED_WITHIN} bytes name, else UTF-8. The future
     * completes on any thread, never with an exception; cancelling it ends the fetch.
     *
     * @param page an http or https address without user information
     * @return the source; empty for anything else {@link WebFetch} gives nothing for, and after {@link #close()}
     */
    CompletableFuture<Optional<String>> fetch(URI page) {
        var result = new CompletableFuture<Optional<String>>();
        CompletableFuture<Optional<HttpResponse<byte[]>>> answer = web.get(page, ACCEPT);
        answer.whenComplete((response, failure) -> result.complete(failure == null ? response.map(SourcePage::decoded) : Optional.empty()));
        result.whenComplete((source, failure) -> answer.cancel(true));
        return result;
    }

    /** The fetches under way end, and give nothing; later fetches give nothing at once. */
    @Override public void close() {
        web.close();
    }

    /**
     * View Source's address for the page at {@code page}: {@code jasper-about:source?url=…}, the page's address without
     * its user information, and {@code &token=…} for a capture.
     *
     * @param page the page's address
     * @param token the item's token for its capture of the page's HTML, or empty for none
     * @return the address
     */
    static URI address(String page, Optional<String> token) {
        String query = URL + "=" + URLEncoder.encode(NavigationHistory.withoutUserInfo(page), StandardCharsets.UTF_8)
            + token.map(value -> "&" + TOKEN + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8)).orElse("");
        return URI.create(AboutScheme.NAME + ":" + NAME + "?" + query);
    }

    /**
     * The page whose source a source page's address shows: its {@code url}, without user information, as any page may
     * have written it.
     *
     * @param address an address, as written or as Chromium writes it
     * @return the page's address; empty for an address that is no source page's, or has no {@code url}
     */
    static Optional<String> page(String address) {
        return source(address) ? AboutScheme.parameter(address, URL).map(NavigationHistory::withoutUserInfo) : Optional.empty();
    }

    /**
     * The token a source page's address carries.
     *
     * @param address an address, as written or as Chromium writes it
     * @return the token; empty for an address that is no source page's, or whose token Jasper would not have made
     */
    static Optional<String> token(String address) {
        return source(address) ? AboutScheme.parameter(address, TOKEN).filter(PageHtml::isToken) : Optional.empty();
    }

    /**
     * What an item saves of {@code address}: a source page's without its token, which ends with the item, and an error
     * page's source page as that of the address that failed, without the error page's tokens (plan 4b's W7); any other
     * as it is.
     *
     * @param address where the item is
     * @return the address to save
     */
    static URI saved(URI address) {
        String written = address.toString();
        if (!source(written)) return address;
        return page(written).map(SourcePage::savedPage).filter(page -> !page.isEmpty()).map(page -> address(page, Optional.empty()))
            .orElseGet(() -> URI.create(AboutScheme.NAME + ":" + NAME));
    }

    /** The page a source page saves: an error page as the address that failed, empty for none; any other as it is. */
    private static String savedPage(String page) {
        return AboutScheme.page(page).filter(ErrorPage.NAME::equals).isPresent() ? ErrorPage.Shown.of(page).url() : page;
    }

    /**
     * The page showing {@code source} as the source of the page at {@code page}, in the theme's colours: at most
     * {@value #LIMIT} characters, the first {@value #HIGHLIGHTED} highlighted and linked.
     *
     * @param page the page's address, whose user information is not shown
     * @param source its source
     * @param palette the workspace palette
     * @param syntax the workspace's syntax palette
     * @param dark whether the workspace theme is dark
     * @return the page's HTML; no transitions or animations
     */
    static String html(String page, String source, WorkspacePalette palette, SyntaxPalette syntax, boolean dark) {
        String shown = source.length() <= LIMIT ? source
            : source.substring(0, Character.isHighSurrogate(source.charAt(LIMIT - 1)) ? LIMIT - 1 : LIMIT);
        var out = new StringBuilder(4096 + shown.length() + shown.length() / 2);
        head(out, page, palette, syntax, dark);
        if (shown.length() < source.length())
            note(out, String.format(Locale.ROOT, "Jasper shows the first %,d characters of this page’s source.", LIMIT));
        if (shown.length() > HIGHLIGHTED)
            note(out, String.format(Locale.ROOT, "Only the first %,d characters are highlighted.", HIGHLIGHTED));
        out.append("</header>\n<pre>\n");
        new Highlighter(shown, out).run();
        return out.append("</pre>\n</body>\n</html>\n").toString();
    }

    /** The page saying {@code message} in place of the source of the page at {@code page}. */
    static String message(String page, String message, WorkspacePalette palette, SyntaxPalette syntax, boolean dark) {
        var out = new StringBuilder(4096);
        head(out, page, palette, syntax, dark);
        out.append("</header>\n<p class=\"message\">").append(PageHtml.escape(message)).append("</p>\n");
        return out.append("</body>\n</html>\n").toString();
    }

    private void rebuild() {
        look = new Look(appearance.workspacePalette(), appearance.syntaxPalette(), appearance.workspaceVariant() == Variant.DARK);
    }

    /** What the item captured under {@code token} for {@code page}, looked up on the UI thread; none without a token. */
    private CompletableFuture<Optional<String>> held(Optional<String> token, String page) {
        if (token.isEmpty()) return CompletableFuture.completedFuture(Optional.empty());
        var found = new CompletableFuture<Optional<String>>();
        EventQueue.invokeLater(() -> {
            try {
                found.complete(Objects.requireNonNull(captured.apply(token.get(), page), "captured"));
            } catch (RuntimeException failure) {
                found.completeExceptionally(failure);
            }
        });
        return found;
    }

    /** {@code page} as an address {@link WebFetch} fetches: http or https, with a host; else empty. */
    private static Optional<URI> webAddress(String page) {
        try {
            URI address = new URI(page);
            return WebFetch.fetches(address) ? Optional.of(address) : Optional.empty();
        } catch (URISyntaxException notAUri) {
            return Optional.empty();
        }
    }

    /** The text of a fetched answer, in the charset its byte order mark, its header or its first bytes name, else UTF-8. */
    private static String decoded(HttpResponse<byte[]> response) {
        byte[] body = response.body();
        if (starts(body, 0xEF, 0xBB, 0xBF)) return new String(body, 3, body.length - 3, StandardCharsets.UTF_8);
        if (starts(body, 0xFE, 0xFF)) return new String(body, 2, body.length - 2, StandardCharsets.UTF_16BE);
        if (starts(body, 0xFF, 0xFE)) return new String(body, 2, body.length - 2, StandardCharsets.UTF_16LE);
        Charset charset = WebFetch.charset(response).or(() -> declared(body)).orElse(StandardCharsets.UTF_8);
        return new String(body, charset);
    }

    private static boolean starts(byte[] body, int... mark) {
        if (body.length < mark.length) return false;
        for (int at = 0; at < mark.length; at++) if ((body[at] & 0xFF) != mark[at]) return false;
        return true;
    }

    /** The charset a document's {@code meta} declares within its first bytes. */
    private static Optional<Charset> declared(byte[] body) {
        Matcher meta = DECLARED.matcher(new String(body, 0, Math.min(body.length, DECLARED_WITHIN), StandardCharsets.ISO_8859_1));
        if (!meta.find()) return Optional.empty();
        try {
            return Optional.of(Charset.forName(meta.group(1)));
        } catch (IllegalArgumentException unknown) {
            return Optional.empty();
        }
    }

    /** Whether {@code address} is a source page's. */
    private static boolean source(String address) {
        return AboutScheme.page(address).filter(NAME::equals).isPresent();
    }

    /** The page's head and its header up to the notes: the heading and the address shown. */
    private static void head(StringBuilder out, String page, WorkspacePalette palette, SyntaxPalette syntax, boolean dark) {
        String shown = PageHtml.cut(NavigationHistory.withoutUserInfo(page), ErrorPage.MAX_URL);
        out.append("""
            <!doctype html>
            <html lang="en">
            <head>
            <meta charset="utf-8">
            <meta http-equiv="Content-Security-Policy" content="default-src 'none'; style-src 'unsafe-inline'">
            <meta name="referrer" content="no-referrer">
            <meta name="color-scheme" content="%s">
            <title>%s</title>
            <style>
            %s
            %s
            html, body { margin: 0; background: var(--background); color: var(--text); }
            body { font: 15px/1.5 system-ui, -apple-system, "Segoe UI", Roboto, "Helvetica Neue", sans-serif; }
            header { padding: 24px 24px 12px; border-bottom: 1px solid var(--border); }
            h1 { margin: 0 0 4px; font-size: 20px; font-weight: 600; }
            p { margin: 0 0 8px; }
            .address, pre { font: 13px/1.5 ui-monospace, Menlo, Consolas, monospace; overflow-wrap: anywhere; }
            .address, .note { color: var(--muted); }
            .message { padding: 24px; }
            pre { margin: 0; padding: 16px 24px; white-space: pre-wrap; tab-size: 4; }
            a { color: inherit; }
            a:focus-visible { outline: 2px solid var(--accent); outline-offset: 2px; }
            </style>
            </head>
            <body>
            <header>
            <h1>Page Source</h1>
            <p class="address">%s</p>
            """.formatted(dark ? "dark" : "light", PageHtml.escape(title(shown)), PageHtml.colours(palette), syntaxRules(syntax),
            PageHtml.escape(shown)));
    }

    private static void note(StringBuilder out, String text) {
        out.append("<p class=\"note\">").append(PageHtml.escape(text)).append("</p>\n");
    }

    /** The tab's title: "Page Source" and the page's host, or its address when it has none. */
    private static String title(String shown) {
        String host;
        try {
            host = new URI(shown).getHost();
        } catch (URISyntaxException notAUri) {
            host = null;
        }
        if (host != null && !host.isBlank()) return "Page Source – " + host;
        return shown.isEmpty() ? "Page Source" : "Page Source – " + PageHtml.cut(shown, MAX_TITLE);
    }

    /** The highlighted parts' rules, in the syntax palette's colours, bold and italic. */
    private static String syntaxRules(SyntaxPalette syntax) {
        return String.join("\n", rule("tag", syntax.style(SyntaxRole.TAG)), rule("attribute", syntax.style(SyntaxRole.ATTRIBUTE)),
            rule("string", syntax.style(SyntaxRole.STRING)), rule("comment", syntax.style(SyntaxRole.COMMENT)));
    }

    private static String rule(String name, SyntaxStyle style) {
        return "." + name + " { color: " + PageHtml.css(style.color()) + ";" + (style.bold() ? " font-weight: bold;" : "")
            + (style.italic() ? " font-style: italic;" : "") + " }";
    }

    /** The theme the page is built in. */
    private record Look(WorkspacePalette palette, SyntaxPalette syntax, boolean dark) { }

    /**
     * Writes a source as the page shows it: escaped, its markup highlighted as HTML's tokenizer reads it, roughly, up to
     * {@link #HIGHLIGHTED} characters, and plain escaped text after them. Comments, doctypes and other declarations, start
     * and end tags with their attribute names and values, and the text of raw text elements such as scripts, which holds
     * no markup, are told apart.
     */
    private static final class Highlighter {
        private final String source;
        private final StringBuilder out;
        private final int end, bound;
        private int at;
        // The raw text element whose end tag the text runs to, or null.
        private String raw;

        Highlighter(String source, StringBuilder out) {
            this.source = source;
            this.out = out;
            end = source.length();
            bound = Math.min(end, HIGHLIGHTED);
        }

        void run() {
            while (at < bound) {
                if (raw != null) rawText();
                else if (source.charAt(at) == '<') markup();
                else {
                    int next = source.indexOf('<', at);
                    int stop = next < 0 ? end : next;
                    text(at, stop);
                    at = stop;
                }
            }
            text(at, end);
        }

        /** At a {@code <}: a comment, a declaration, a tag, or a less-than sign that starts none. */
        private void markup() {
            if (source.startsWith("<!--", at)) {
                int close = source.indexOf("-->", at + 2);
                spanTo("comment", close < 0 ? end : close + 3);
                return;
            }
            char next = at + 1 < end ? source.charAt(at + 1) : 0;
            if (next == '!' || next == '?') {
                int close = source.indexOf('>', at + 2);
                spanTo("tag", close < 0 ? end : close + 1);
                return;
            }
            boolean closing = next == '/';
            int name = at + (closing ? 2 : 1);
            if (name >= end || !letter(source.charAt(name))) {
                text(at, at + 1);
                at++;
                return;
            }
            int nameEnd = name;
            while (nameEnd < end && !tagNameEnds(source.charAt(nameEnd))) nameEnd++;
            String element = source.substring(name, nameEnd).toLowerCase(Locale.ROOT);
            spanTo("tag", nameEnd);
            if (attributes() && !closing && RAW.contains(element)) raw = element;
        }

        /** A tag's attributes and its end; whether it ended, with {@code >} or {@code />}, before the end or the bound. */
        private boolean attributes() {
            while (at < end && at < bound) {
                char c = source.charAt(at);
                if (c == '>') {
                    spanTo("tag", at + 1);
                    return true;
                }
                if (c == '/' && at + 1 < end && source.charAt(at + 1) == '>') {
                    spanTo("tag", at + 2);
                    return true;
                }
                if (space(c) || c == '/') {
                    text(at, at + 1);
                    at++;
                    continue;
                }
                int nameEnd = at + 1;
                while (nameEnd < end && !attributeNameEnds(source.charAt(nameEnd))) nameEnd++;
                spanTo("attribute", nameEnd);
                int equals = skipSpace(at);
                if (equals < end && source.charAt(equals) == '=') {
                    int value = skipSpace(equals + 1);
                    text(at, value);
                    at = value;
                    if (at < end) value();
                }
            }
            return false;
        }

        /** An attribute's value: quoted, to its closing quote, or unquoted, to white space or {@code >}. */
        private void value() {
            char quote = source.charAt(at);
            if (quote == '"' || quote == '\'') {
                int close = source.indexOf(quote, at + 1);
                int content = close < 0 ? end : close;
                open("string");
                text(at, at + 1);
                links(at + 1, content);
                if (close >= 0) text(close, close + 1);
                out.append("</span>");
                at = close < 0 ? end : close + 1;
                return;
            }
            int stop = at;
            while (stop < end && !space(source.charAt(stop)) && source.charAt(stop) != '>') stop++;
            if (stop == at) return;
            open("string");
            links(at, stop);
            out.append("</span>");
            at = stop;
        }

        /**
         * Writes {@code [from, to)} of an attribute's value with each run without white space that starts with
         * {@code http://} or {@code https://}, before the bound, as a link to the run as the source wrote it.
         */
        private void links(int from, int to) {
            int run = from;
            while (run < to) {
                int start = run;
                while (start < to && space(source.charAt(start))) start++;
                int stop = start;
                while (stop < to && !space(source.charAt(stop))) stop++;
                text(run, start);
                if (start < bound && web(start, stop)) link(start, stop);
                else text(start, stop);
                run = stop;
            }
        }

        /** Whether {@code [start, stop)} is an http or https URL with something after its {@code //}. */
        private boolean web(int start, int stop) {
            return source.regionMatches(true, start, "http://", 0, 7) && stop > start + 7
                || source.regionMatches(true, start, "https://", 0, 8) && stop > start + 8;
        }

        /**
         * A link to {@code [start, stop)}: its address is the run as the source wrote it, which HTML decodes as it decoded
         * the source's own attribute, with what would end its attribute or a tag written as a character reference.
         */
        private void link(int start, int stop) {
            out.append("<a href=\"");
            for (int index = start; index < stop; index++) {
                char c = source.charAt(index);
                switch (c) {
                    case '"' -> out.append("&quot;");
                    case '\'' -> out.append("&#39;");
                    case '<' -> out.append("&lt;");
                    case '>' -> out.append("&gt;");
                    default -> out.append(c);
                }
            }
            out.append("\">");
            text(start, stop);
            out.append("</a>");
        }

        /** The text of a raw text element, up to its end tag, which is markup again; to the end when it has none. */
        private void rawText() {
            int close = rawEnd();
            text(at, close);
            at = close;
            raw = null;
        }

        /** Where {@code </name} of the raw text element begins, followed by white space, a slash or {@code >}. */
        private int rawEnd() {
            int look = at;
            while ((look = source.indexOf("</", look)) >= 0) {
                int after = look + 2 + raw.length();
                if (source.regionMatches(true, look + 2, raw, 0, raw.length()) && (after >= end || tagNameEnds(source.charAt(after))))
                    return look;
                look += 2;
            }
            return end;
        }

        /** {@code [at, stop)} in a span of {@code kind}; the source goes on at {@code stop}. */
        private void spanTo(String kind, int stop) {
            open(kind);
            text(at, stop);
            out.append("</span>");
            at = stop;
        }

        private void open(String kind) {
            out.append("<span class=\"").append(kind).append("\">");
        }

        /** {@code [from, to)} of the source as text: {@code &}, {@code <} and {@code >} written as references. */
        private void text(int from, int to) {
            for (int index = from; index < to; index++) {
                char c = source.charAt(index);
                switch (c) {
                    case '&' -> out.append("&amp;");
                    case '<' -> out.append("&lt;");
                    case '>' -> out.append("&gt;");
                    default -> out.append(c);
                }
            }
        }

        private int skipSpace(int from) {
            int index = from;
            while (index < end && space(source.charAt(index))) index++;
            return index;
        }

        private static boolean letter(char c) {
            return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z';
        }

        /** HTML's white space: tab, line feed, form feed, carriage return and space. */
        private static boolean space(char c) {
            return c == ' ' || c == '\t' || c == '\n' || c == '\f' || c == '\r';
        }

        private static boolean tagNameEnds(char c) {
            return space(c) || c == '/' || c == '>';
        }

        private static boolean attributeNameEnds(char c) {
            return space(c) || c == '/' || c == '>' || c == '=';
        }
    }
}
