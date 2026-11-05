package dev.jasper.browser.preview;

import dev.jasper.browser.api.PreviewFind;
import dev.jasper.browser.engine.PreviewScheme;
import dev.jasper.sdk.Variant;
import java.awt.EventQueue;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.JComponent;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code preview.js} read as text, and {@code find.js}, which the preview runs for Find (N-F13): what they may not do,
 * what they tell Jasper, and that they define what Jasper calls.
 */
class PreviewScriptTest {
    private static final Pattern OP = Pattern.compile("\\bop:\\s*['\"]([A-Za-z]+)['\"]");

    /** Review Focus 1: a diagram's directives cannot loosen Mermaid, and Mermaid draws no error of its own (N-F19). */
    @Test void mermaidIsStrict() throws IOException {
        String script = script("/dev/jasper/browser/preview/preview.js");
        Matcher initialize = Pattern.compile("(?s)mermaid\\.initialize\\(\\{(.*?)}\\)").matcher(script);
        assertThat(initialize.find()).as("one initialise call").isTrue();
        String options = initialize.group(1);
        assertThat(options).contains("securityLevel: \"strict\"").contains("startOnLoad: false").contains("theme: \"base\"")
            .contains("deterministicIds: true").contains("suppressErrorRendering: true").contains("themeVariables")
            .contains("fontFamily");
        assertThat(initialize.find()).as("initialised in one place").isFalse();
        assertThat(script).as("Mermaid's secure list stays its default").doesNotContainPattern("\\bsecure\\s*[:=]")
            .doesNotContainPattern("['\"]secure['\"]").doesNotContainPattern("\\.secure\\b");
        assertThat(script).contains("Mermaid could not draw this diagram: ").contains("'/mermaid.min.js'");
    }

    @Test void neitherScriptUsesAForbiddenCall() throws IOException {
        for (String name : List.of("/dev/jasper/browser/preview/preview.js", "/dev/jasper/browser/page/find.js")) {
            String script = script(name);
            assertThat(script).as(name).doesNotContainPattern("\\beval\\s*\\(").doesNotContainPattern("new\\s+Function\\b")
                .doesNotContainPattern("setTimeout\\(\\s*['\"`]").doesNotContainPattern("setInterval\\s*\\(")
                .doesNotContain("document.write").doesNotContain("insertAdjacentHTML").doesNotContain("outerHTML");
        }
        String preview = script("/dev/jasper/browser/preview/preview.js");
        Matcher inner = Pattern.compile("(\\w+)\\.innerHTML\\s*=").matcher(preview);
        var assigned = new ArrayList<String>();
        while (inner.find()) assigned.add(inner.group(1));
        assertThat(assigned).as("innerHTML only on the template and for Mermaid's SVG").containsExactlyInAnyOrder("template", "picture");
        assertThat(script("/dev/jasper/browser/page/find.js")).doesNotContain("innerHTML");
    }

    @Test void everyOpTheScriptsPostIsOneJasperReads() throws IOException {
        var posted = new LinkedHashSet<String>();
        for (String name : List.of("/dev/jasper/browser/preview/preview.js", "/dev/jasper/browser/page/find.js")) {
            Matcher op = OP.matcher(script(name));
            while (op.find()) posted.add(op.group(1));
        }
        assertThat(posted).containsExactlyInAnyOrder("ready", "shown", "geometry", "scrolled", "link", "find");
        assertThat(PreviewMessages.OPS).containsAll(posted);
    }

    /** Every {@code jasperPreview.<function>} the preview calls, through each of its calls, is one the script defines. */
    @Test void everyFunctionThePreviewCallsIsDefined() throws Exception {
        var scheme = new PreviewScheme();
        var ran = new ArrayList<String>();
        var preview = new PagePreview(scheme.open(), new PagePreview.Hooks(new JComponent() { }, ran::add, color -> { }, () -> { },
            () -> { }, () -> { }, () -> { }), Runnable::run, event -> { });
        EventQueue.invokeAndWait(() -> {
            preview.received("{\"op\":\"ready\"}");
            preview.theme(PreviewCssTest.theme(Variant.DARK));
            preview.show(1, "<p>One</p>");
            preview.image(1, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB));
            preview.imageReason(2, "Too large");
            preview.clearImages();
            preview.scrollTo(10);
            preview.reveal("intro");
            preview.markAll(new PreviewFind("a", false, false, false));
            preview.select(new PreviewFind("a", false, false, false), true);
            preview.selectedText();
            preview.clearFind();
        });
        EventQueue.invokeAndWait(() -> preview.image(1, new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB)));
        EventQueue.invokeAndWait(() -> { });
        Set<String> called = new LinkedHashSet<>();
        Matcher call = Pattern.compile("^jasperPreview\\.(\\w+)\\(").matcher("");
        for (String script : ran) if (call.reset(script).find()) called.add(call.group(1));
        assertThat(called).containsExactlyInAnyOrder("theme", "show", "image", "imageReason", "clearImages", "scrollTo", "reveal");
        String source = script("/dev/jasper/browser/preview/preview.js");
        Matcher exported = Pattern.compile("(?s)'jasperPreview',\\s*\\{value: Object\\.freeze\\(\\{(.*?)}\\)").matcher(source);
        assertThat(exported.find()).as("the script defines jasperPreview").isTrue();
        Set<String> defined = new LinkedHashSet<>();
        Matcher name = Pattern.compile("(\\w+):\\s*\\w+").matcher(exported.group(1));
        while (name.find()) defined.add(name.group(1));
        assertThat(defined).containsAll(called);
        for (String script : ran)
            if (!script.startsWith("jasperPreview.")) assertThat(PagePreviewTest.findCommand(script)).containsKey("action");
        EventQueue.invokeAndWait(preview::close);
    }

    /**
     * A scroll Jasper asks for before the first document is in the page, as on {@code ready}, is kept for that document
     * rather than run on the empty shell, and the document's install goes there as Jasper's own move.
     */
    @Test void aScrollBeforeTheFirstDocumentWaitsForIt() throws IOException {
        String script = script("/dev/jasper/browser/preview/preview.js");
        String scroll = body(script, "scrollPage(y)"), install = body(script, "install(rev, html)");
        assertThat(scroll).as("no document yet: kept").containsPattern(
            "if \\(shownRevision === null\\) \\{\\s*pending = top;\\s*return;\\s*}");
        assertThat(scroll.indexOf("pending = top")).as("kept before any scroll").isLessThan(scroll.indexOf("window.scrollTo"));
        assertThat(install).contains("const asked = pending").contains("pending = null")
            .contains("top: asked === null ? kept : asked").contains("if (asked !== null || window.scrollY !== kept) target = window.scrollY;");
        assertThat(install.indexOf("pending = null")).as("taken before the document replaces the shell's")
            .isLessThan(install.indexOf("content.replaceChildren"));
    }

    /** A block with no box is written as NaN, so one such block is not read as none (an empty list). */
    @Test void aBlockWithNoBoxIsWrittenAsNaN() throws IOException {
        String geometry = body(script("/dev/jasper/browser/preview/preview.js"), "geometry()");
        assertThat(geometry).contains("tops.push('NaN')").doesNotContain("tops.push('')");
    }

    /** Task 2's note: a heading's id is a window property, so the script reads no global it does not define first. */
    @Test void theScriptsOwnIdsAreOnesNoHeadingHas() throws IOException {
        String script = script("/dev/jasper/browser/preview/preview.js");
        Matcher id = Pattern.compile("getElementById\\('([^']+)'\\)").matcher(script);
        var ids = new ArrayList<String>();
        while (id.find()) ids.add(id.group(1));
        assertThat(ids).containsExactly("Jasper-content");
        assertThat(ids).allSatisfy(each -> assertThat(each).as("an upper-case letter, which heading ids never hold").matches(".*[A-Z].*"));
        assertThat(script).contains("'Jasper-diagram-'").doesNotContainPattern("window\\.mermaid\\s*(?:===|!==|\\)|&&|\\|\\|)")
            .doesNotContainPattern("typeof\\s+mermaid\\b").doesNotContainPattern("typeof\\s+window\\.mermaid\\b");
    }

    /** The body of the script's function {@code signature}, from its brace to the brace that closes it at its indentation. */
    private static String body(String script, String signature) {
        Matcher function = Pattern.compile("(?s)\n( *)function " + Pattern.quote(signature) + " \\{\n(.*?)\n\\1}\n").matcher(script);
        assertThat(function.find()).as("function " + signature).isTrue();
        return function.group(2);
    }

    private static String script(String name) throws IOException {
        try (InputStream in = PreviewScriptTest.class.getResourceAsStream(name)) {
            assertThat(in).as(name).isNotNull();
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
