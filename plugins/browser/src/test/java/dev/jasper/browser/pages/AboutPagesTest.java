package dev.jasper.browser.pages;

import dev.jasper.browser.engine.AboutScheme;
import dev.jasper.browser.json.StrictJson;
import dev.jasper.sdk.PluginInfo;
import dev.jasper.sdk.Variant;
import dev.jasper.sdk.plugin.PluginContext;
import dev.jasper.sdk.testing.FakePluginHost;
import dev.jasper.sdk.ui.WorkspacePalette;
import java.awt.Color;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Jasper's own pages built from their templates (spec section 14): the theme's colours and {@code about.css}, the
 * shared script and the page's own, every slot escaped, and a content security policy that loads nothing and runs only
 * the script that render wrote, by its nonce. The pages render off the UI thread, as Chromium's IO thread asks for them;
 * the templates here are the test's, and {@code about.css} and {@code about.js} the plugin's.
 */
class AboutPagesTest {
    private static final String ID = "dev.jasper.browser";
    private static final String TEMPLATE = """
        <!doctype html>
        <html lang="en">
        <head>
        <title>{{title}}</title>
        {{style}}
        </head>
        <body>
        <h1>{{title}}</h1>
        <p>{{note}}</p>
        {{script}}
        </body>
        </html>
        """;
    private static final String SAMPLE_SCRIPT = "jasper.ask({op: 'list'}).then(function (answer) { document.title = answer.title; });";
    private static final Pattern NONCE = Pattern.compile("script-src 'nonce-([0-9a-f]{32})'");
    private static final Pattern VARIABLES = Pattern.compile("(?s):root \\{.*?}");
    private FakePluginHost host;
    private PluginContext context;

    @BeforeEach void start() throws Exception {
        SwingUtilities.invokeAndWait(() -> {
            host = new FakePluginHost();
            var started = new AtomicReference<PluginContext>();
            host.start(new PluginInfo(ID, "Browser", "0.1.0", Set.of()), Set.of(), Set.of(), started::set);
            context = started.get();
        });
    }

    @AfterEach void close() throws Exception {
        SwingUtilities.invokeAndWait(host::close);
    }

    /** Review Focus 1: whatever a slot holds shows as text, and a slot's value is never read as a placeholder. */
    @Test void aSlotComesOutEscapedAndIsNeverReadAsAPlaceholder() throws Exception {
        AboutPages pages = pages(new AboutScheme());
        String html = pages.render("sample", Map.of("title", "<b>&\"'", "note", "{{script}} {{style}}"));
        assertThat(html).contains("<title>&lt;b&gt;&amp;&quot;&#39;</title>", "<h1>&lt;b&gt;&amp;&quot;&#39;</h1>",
            "<p>{{script}} {{style}}</p>");
        assertThat(html.split("<script", -1)).as("one script, the page's").hasSize(2);
        assertThat(html.split("<style", -1)).as("one style sheet").hasSize(2);
    }

    /**
     * Spec section 14: the style holds the theme's colours, then {@code about.css}, and the head opens with the policy:
     * nothing loads, only render's script runs, images only from data, and no base or form goes anywhere.
     */
    @Test void theStyleHoldsTheThemesColoursAndAboutCssAndTheHeadOpensWithThePolicy() throws Exception {
        var palette = new AtomicReference<WorkspacePalette>();
        var dark = new AtomicReference<Boolean>();
        SwingUtilities.invokeAndWait(() -> {
            palette.set(context.appearance().workspacePalette());
            dark.set(context.appearance().workspaceVariant() == Variant.DARK);
        });
        String html = pages(new AboutScheme()).render("sample", Map.of("title", "History", "note", ""));
        assertThat(html).contains("--background: " + PageHtml.css(palette.get().background()) + ";",
            "--text: " + PageHtml.css(palette.get().text()) + ";", "color-scheme: " + (dark.get() ? "dark" : "light") + ";",
            AboutPages.resource("about.css").strip());
        assertThat(html.indexOf("--background")).as("the colours come before the sheet that uses them")
            .isLessThan(html.indexOf(AboutPages.resource("about.css").strip()));
        Matcher nonce = NONCE.matcher(html);
        assertThat(nonce.find()).isTrue();
        assertThat(html).contains("<head>\n<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
            + "style-src 'unsafe-inline'; script-src 'nonce-" + nonce.group(1) + "'; img-src data:; base-uri 'none'; form-action 'none'\">\n");
        assertThat(html).doesNotContain("'unsafe-eval'").doesNotContainPattern("script-src[^;\"]*'unsafe-inline'");
        assertThat(html).doesNotContain("transition", "animation");
    }

    /**
     * Plan 5's F8: each render has a nonce of its own, which only its one script carries, and the script holds the
     * page's secret, which {@code jasper.ask} sends; a page with no queries has none to send, and a page with no script
     * of its own runs {@code about.js} alone.
     */
    @Test void eachRenderHasItsOwnNonceAndItsScriptThePagesSecret() throws Exception {
        var about = new AboutScheme();
        about.register("sample", () -> "");
        about.registerQueries("sample", (browser, request) -> CompletableFuture.completedFuture("{}"));
        String secret = about.secret("sample").orElseThrow();
        AboutPages pages = pages(about);
        String first = pages.render("sample", Map.of("title", "", "note", "")), second = pages.render("sample", Map.of("title", "", "note", ""));
        String nonce = nonce(first);
        assertThat(nonce).isNotEqualTo(nonce(second));
        assertThat(first).contains("<script nonce=\"" + nonce + "\">\nconst jasper = ", "(" + StrictJson.quote(secret) + ");\n" + SAMPLE_SCRIPT,
            AboutPages.resource("about.js").strip());
        assertThat(first.split("nonce=\"", -1)).as("one element carries the nonce").hasSize(2);

        String plain = pages.render("plain", Map.of("title", "", "note", ""));
        assertThat(plain).contains("(\"\");\n</script>").doesNotContain(SAMPLE_SCRIPT, secret);
    }

    @Test void anUnknownPageAMissingSlotOrAReservedOneThrows() throws Exception {
        AboutPages pages = pages(new AboutScheme());
        assertThatThrownBy(() -> pages.render("missing", Map.of())).isInstanceOf(IllegalArgumentException.class).hasMessageContaining("missing");
        assertThatThrownBy(() -> pages.render("../about", Map.of())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pages.render("sample", Map.of("title", ""))).as("no note").isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("{{note}}");
        assertThatThrownBy(() -> pages.render("sample", Map.of("title", "", "note", "", "style", "x"))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> pages.render("headless", Map.of())).as("no head for the policy").isInstanceOf(IllegalStateException.class);
    }

    /** A theme change, its colours and its darkness, is in every page rendered after it. */
    @Test void aThemeChangeRebuildsTheVariables() throws Exception {
        AboutPages pages = pages(new AboutScheme());
        var dark = new AtomicReference<Boolean>();
        SwingUtilities.invokeAndWait(() -> dark.set(context.appearance().workspaceVariant() == Variant.DARK));
        String before = pages.render("sample", Map.of("title", "", "note", ""));
        assertThat(before).contains("color-scheme: " + (dark.get() ? "dark" : "light") + ";");
        var palette = new WorkspacePalette(new Color(0x10, 0x20, 0x30), new Color(0x11, 0x21, 0x31), Color.BLACK, new Color(0xee, 0xee, 0xee),
            new Color(0x99, 0x99, 0x99), new Color(0x44, 0x44, 0x44, 0x80), Color.BLUE, Color.WHITE, Color.GRAY, new Color(0x33, 0x66, 0xff),
            Color.GREEN, Color.ORANGE, Color.RED);
        SwingUtilities.invokeAndWait(() -> {
            var appearance = context.appearance();
            host.setWorkspace(dark.get() ? Variant.LIGHT : Variant.DARK, palette, appearance.editorPalette(), appearance.syntaxPalette());
            host.flush();
        });
        String after = pages.render("sample", Map.of("title", "", "note", ""));
        assertThat(variables(after)).isNotEqualTo(variables(before)).contains("--background: #102030;", "--card: #112131;",
            "--border: rgba(68, 68, 68, 0.502);", "--accent: #3366ff;");
        assertThat(after).contains("color-scheme: " + (dark.get() ? "light" : "dark") + ";");
    }

    /** The pages, made on the UI thread as the plugin's start makes them, serving the test's templates. */
    private AboutPages pages(AboutScheme about) throws Exception {
        var made = new AtomicReference<AboutPages>();
        SwingUtilities.invokeAndWait(() -> made.set(new AboutPages(context.appearance(), about, AboutPagesTest::file)));
        return made.get();
    }

    /** The test's page files, and the plugin's own {@code about.css} and {@code about.js}. */
    private static String file(String name) {
        return switch (name) {
            case "sample.html", "plain.html" -> TEMPLATE;
            case "sample.js" -> SAMPLE_SCRIPT;
            case "headless.html" -> "<p>{{style}}</p>";
            default -> AboutPages.resource(name);
        };
    }

    private static String nonce(String html) {
        Matcher nonce = NONCE.matcher(html);
        assertThat(nonce.find()).as("a nonce").isTrue();
        return nonce.group(1);
    }

    private static String variables(String html) {
        Matcher root = VARIABLES.matcher(html);
        assertThat(root.find()).as("the theme's variables").isTrue();
        return root.group();
    }
}
