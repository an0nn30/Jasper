package dev.jasper.browser.engine;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The {@code jasper-preview:} origins and what each answers, asked as Chromium's IO thread asks, without Chromium. */
class PreviewSchemeTest {
    private static final String POLICY = "default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self'; "
        + "connect-src 'self'; base-uri 'none'; form-action 'none'; frame-ancestors 'none'";

    @Test void thePolicyIsTheOneEveryAnswerCarries() {
        assertThat(PreviewScheme.NAME).isEqualTo("jasper-preview");
        assertThat(PreviewScheme.POLICY).isEqualTo(POLICY);
    }

    /** N-F4: the shell carries the policy in a meta tag too, first in its head, without frame-ancestors, which meta cannot carry. */
    @Test void theShellIs200HtmlWithThePolicyFirstInItsHead() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        var shell = scheme.answer("GET", origin.shell().toString());
        assertThat(shell.status()).isEqualTo(200);
        assertThat(shell.mimeType()).isEqualTo("text/html");
        assertThat(text(shell)).isEqualTo("<!doctype html><html><head>"
            + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'none'\">"
            + "<meta charset=\"utf-8\"><meta name=\"color-scheme\" content=\"light dark\"><link rel=\"stylesheet\" href=\"/preview.css\">"
            + "<script src=\"/preview.js\"></script></head><body><main id=\"Jasper-content\" class=\"markdown-body\"></main></body></html>");
        assertThat(PreviewScheme.POLICY).as("the meta's policy is the header's but frame-ancestors")
            .isEqualTo(text(shell).replaceFirst("(?s)^.*Content-Security-Policy\" content=\"([^\"]*)\".*$", "$1") + "; frame-ancestors 'none'");
    }

    @Test void theOriginIsItsTokensRoot() {
        var origin = new PreviewScheme().open();
        assertThat(origin.token()).matches("[0-9a-f]{32}");
        assertThat(origin.shell()).hasToString("jasper-preview://" + origin.token() + "/");
    }

    /** The stylesheet and script come from the plugin's resources (N-F2: Task 5 fills them). */
    @Test void theStylesheetAndScriptAreServedWithTheirTypes() throws IOException {
        var scheme = new PreviewScheme();
        String root = scheme.open().shell().toString();
        var css = scheme.answer("GET", root + "preview.css");
        assertThat(css.status()).isEqualTo(200);
        assertThat(css.mimeType()).isEqualTo("text/css");
        assertThat(css.body()).isEqualTo(resource("/dev/jasper/browser/preview/preview.css"));
        var js = scheme.answer("GET", root + "preview.js");
        assertThat(js.status()).isEqualTo(200);
        assertThat(js.mimeType()).isEqualTo("text/javascript");
        assertThat(js.body()).isEqualTo(resource("/dev/jasper/browser/preview/preview.js"));
    }

    /** N-F29: nothing is read until a request asks for it, and Mermaid is read once, then held. */
    @Test void mermaidIsReadOnceOnItsFirstRequestAndThenHeld() {
        var reads = new ConcurrentHashMap<String, AtomicInteger>();
        Map<String, byte[]> files = Map.of("third_party/mermaid.min.js", "mermaid();".getBytes(StandardCharsets.UTF_8));
        var scheme = new PreviewScheme(name -> {
            reads.computeIfAbsent(name, key -> new AtomicInteger()).incrementAndGet();
            return files.get(name);
        });
        var origin = scheme.open();
        assertThat(reads).as("opening reads nothing").isEmpty();
        for (int request = 0; request < 3; request++) {
            var mermaid = scheme.answer("GET", origin.shell() + "mermaid.min.js");
            assertThat(mermaid.status()).isEqualTo(200);
            assertThat(mermaid.mimeType()).isEqualTo("text/javascript");
            assertThat(text(mermaid)).isEqualTo("mermaid();");
        }
        assertThat(scheme.answer("GET", scheme.open().shell() + "mermaid.min.js").status()).as("another origin").isEqualTo(200);
        assertThat(reads).containsOnlyKeys("third_party/mermaid.min.js");
        assertThat(reads.get("third_party/mermaid.min.js")).hasValue(1);
        assertThat(scheme.answer("GET", origin.shell() + "preview.css")).as("a file the plugin lacks")
            .satisfies(PreviewSchemeTest::isNotFound);
        assertThat(scheme.answer("GET", origin.shell() + "preview.css")).satisfies(PreviewSchemeTest::isNotFound);
        assertThat(reads.get("preview/preview.css")).as("its absence is held too").hasValue(1);
    }

    @Test void theLatestDocumentIsServedAsHtmlAndAnOlderOneIsNot() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        String root = origin.shell().toString();
        assertThat(scheme.answer("GET", root + "document/1")).as("before any document").satisfies(PreviewSchemeTest::isNotFound);
        origin.document(1, "<p>café</p>");
        var first = scheme.answer("GET", root + "document/1");
        assertThat(first.status()).isEqualTo(200);
        assertThat(first.mimeType()).isEqualTo("text/html");
        assertThat(text(first)).isEqualTo("<p>café</p>");
        origin.document(2, "<p>two</p>");
        assertThat(scheme.answer("GET", root + "document/1")).as("an older revision").satisfies(PreviewSchemeTest::isNotFound);
        assertThat(text(scheme.answer("GET", root + "document/2"))).isEqualTo("<p>two</p>");
        assertThat(scheme.answer("GET", root + "document/3")).as("a newer one than set").satisfies(PreviewSchemeTest::isNotFound);
        for (String odd : new String[] {"document/02", "document/+2", "document/-2", "document/2/", "document/2?x=1", "document/", "document",
                "document/99999999999999999999"})
            assertThat(scheme.answer("GET", root + odd)).as(odd).satisfies(PreviewSchemeTest::isNotFound);
        assertThatThrownBy(() -> origin.document(-1, "<p></p>")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void currentImagesAreServedAsPngAndClearedOnesAreNot() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        String root = origin.shell().toString();
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        origin.image(7, png);
        var image = scheme.answer("GET", root + "image/7.png");
        assertThat(image.status()).isEqualTo(200);
        assertThat(image.mimeType()).isEqualTo("image/png");
        assertThat(image.body()).isEqualTo(png);
        for (String odd : new String[] {"image/8.png", "image/7.gif", "image/7", "image/07.png", "image/-7.png", "image/7.png?x"})
            assertThat(scheme.answer("GET", root + odd)).as(odd).satisfies(PreviewSchemeTest::isNotFound);
        origin.clearImages();
        assertThat(scheme.answer("GET", root + "image/7.png")).as("cleared").satisfies(PreviewSchemeTest::isNotFound);
        assertThatThrownBy(() -> origin.image(-1, png)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void anUnknownPathAnotherTokenAClosedOriginAndAnyMethodButGetAre404() {
        var scheme = new PreviewScheme();
        var origin = scheme.open();
        origin.document(1, "<p>one</p>");
        origin.image(1, new byte[] {1});
        String root = origin.shell().toString();
        for (String path : new String[] {"missing", "preview.css/", "index.html", "Preview.css", "document/1#top"})
            assertThat(scheme.answer("GET", root + path)).as(path).satisfies(PreviewSchemeTest::isNotFound);
        assertThat(scheme.answer("GET", "jasper-preview://" + "0".repeat(32) + "/")).as("a token never opened").satisfies(PreviewSchemeTest::isNotFound);
        assertThat(scheme.answer("GET", "jasper-preview://" + origin.token().toUpperCase(java.util.Locale.ROOT) + "/"))
            .as("its token in upper case").satisfies(PreviewSchemeTest::isNotFound);
        for (String method : new String[] {"POST", "HEAD", "PUT", "OPTIONS", "get", "", null})
            assertThat(scheme.answer(method, root)).as("method " + method).satisfies(PreviewSchemeTest::isNotFound);
        for (String url : new String[] {null, "", "https://" + origin.token() + "/", "jasper-preview:" + origin.token() + "/",
                "jasper-preview://" + origin.token(), "jasper-preview://x@" + origin.token() + "/"})
            assertThat(scheme.answer("GET", url)).as(String.valueOf(url)).satisfies(PreviewSchemeTest::isNotFound);
        assertThat(scheme.answer("GET", "JASPER-PREVIEW://" + origin.token() + "/").status()).as("any case of the scheme").isEqualTo(200);
        var other = scheme.open();
        origin.close();
        for (String path : new String[] {"", "preview.css", "document/1", "image/1.png"})
            assertThat(scheme.answer("GET", root + path)).as("closed: /" + path).satisfies(PreviewSchemeTest::isNotFound);
        origin.document(2, "<p>after</p>");
        origin.image(2, new byte[] {2});
        origin.close();
        assertThat(scheme.answer("GET", root + "document/2")).as("a closed origin takes nothing").satisfies(PreviewSchemeTest::isNotFound);
        assertThat(scheme.answer("GET", other.shell().toString()).status()).as("the other origin stays").isEqualTo(200);
    }

    /** Review Focus 1: a preview's page reaches its own origin only. */
    @Test void allowsOnlyItsOwnOrigin() {
        var scheme = new PreviewScheme();
        String token = scheme.open().token(), other = scheme.open().token();
        assertThat(PreviewScheme.allows("jasper-preview://" + token + "/", token)).isTrue();
        assertThat(PreviewScheme.allows("jasper-preview://" + token + "/document/3", token)).isTrue();
        assertThat(PreviewScheme.allows("JASPER-PREVIEW://" + token + "/a", token)).isTrue();
        for (String url : new String[] {"http://" + token + "/", "https://" + token + "/", "file:///" + token + "/", "data:text/html,<p>",
                "blob:jasper-preview://" + token + "/1", "jasper-about://welcome/", "jasper-about://" + token + "/",
                "jasper-preview://" + other + "/", "jasper-preview://" + token + "@x/", "jasper-preview://x@" + token + "/",
                "jasper-preview://" + token + ":1/", "jasper-preview://" + percentEncoded(token) + "/", "jasper-preview://" + token,
                "jasper-preview:" + token + "/", "jasper-preview://" + token + "x/", "jasper-preview://" + token.toUpperCase(java.util.Locale.ROOT) + "/",
                "", null})
            assertThat(PreviewScheme.allows(url, token)).as(String.valueOf(url)).isFalse();
        assertThat(PreviewScheme.allows("jasper-preview://" + token + "/", null)).isFalse();
        assertThat(PreviewScheme.allows("jasper-preview://abc/", "abc")).as("no token of the scheme's").isFalse();
    }

    @Test void aThousandOpensGiveAThousandDistinctTokens() {
        var scheme = new PreviewScheme();
        var tokens = new HashSet<String>();
        for (int open = 0; open < 1000; open++) {
            String token = scheme.open().token();
            assertThat(token).matches("[0-9a-f]{32}");
            tokens.add(token);
        }
        assertThat(tokens).hasSize(1000);
    }

    /** The token with every character percent-encoded, as {@code %61} for {@code a}. */
    private static String percentEncoded(String token) {
        var encoded = new StringBuilder();
        for (char c : token.toCharArray()) encoded.append('%').append(java.util.HexFormat.of().toHexDigits((byte) c));
        return encoded.toString();
    }

    private static void isNotFound(PreviewScheme.Answer answer) {
        assertThat(answer.status()).isEqualTo(404);
        assertThat(answer.body()).isEmpty();
    }

    private static String text(PreviewScheme.Answer answer) {
        return new String(answer.body(), StandardCharsets.UTF_8);
    }

    private static byte[] resource(String name) throws IOException {
        try (InputStream in = PreviewScheme.class.getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return in.readAllBytes();
        }
    }
}
