package dev.jasper.editor.markdown;

import dev.jasper.editor.markdown.MarkdownRenderer.Dialect;
import dev.jasper.editor.markdown.resources.Destination;
import dev.jasper.editor.markdown.resources.Destinations;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.IntStream;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.node.Document;
import org.commonmark.node.Heading;
import org.commonmark.node.Paragraph;
import org.commonmark.node.Text;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class WebMarkupTest {
    private static final Set<String> TAGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6", "p", "blockquote", "ul", "ol", "li",
        "hr", "br", "em", "strong", "del", "code", "input", "table", "tr", "th", "td", "pre", "span", "div", "sup", "a",
        "section", "img");
    private static final Set<String> ATTRIBUTES = Set.of("id", "class", "data-b", "data-link", "data-image", "href", "alt",
        "start", "type", "disabled", "checked", "aria-label");
    private static final Pattern TAG = Pattern.compile("<(/?)([a-z0-9]+)((?: [a-z-]+(?:=\"[^\"<>]*\")?)*)>");
    private static final Pattern ATTRIBUTE = Pattern.compile(" ([a-z-]+)(?:=\"([^\"]*)\")?");

    static MarkdownResult web(String source) {
        return new MarkdownRenderer().render(source, MarkdownRendererTest.palette(), Dialect.WEB);
    }

    static MarkdownResult swing(String source) {
        return new MarkdownRenderer().render(source, MarkdownRendererTest.palette(), Dialect.SWING);
    }

    static String fixture() throws Exception {
        try (var in = WebMarkupTest.class.getResourceAsStream("/markdown/github-features.md")) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Every {@code <} starts a tag of the vocabulary, with only the vocabulary's attributes and private link ids. */
    static void assertVocabulary(String html) {
        var matcher = TAG.matcher(html);
        for (int at = html.indexOf('<'); at >= 0; at = html.indexOf('<', at + 1)) {
            assertThat(matcher.find(at) && matcher.start() == at).as("a tag at %d: %s", at,
                html.substring(at, Math.min(html.length(), at + 60))).isTrue();
            assertThat(TAGS).as("tag").contains(matcher.group(2));
            var attributes = ATTRIBUTE.matcher(matcher.group(3));
            while (attributes.find()) {
                String name = attributes.group(1), value = attributes.group(2);
                assertThat(ATTRIBUTES).as("attribute of <%s>", matcher.group(2)).contains(name);
                if (name.equals("href")) assertThat(value).matches("jasper-link:\\d+");
                if (name.equals("data-link") || name.equals("data-image") || name.equals("data-b")) assertThat(value).matches("\\d+");
            }
        }
    }

    static List<Integer> dataBlocks(String html) {
        var found = new ArrayList<Integer>();
        var matcher = Pattern.compile(" data-b=\"(\\d+)\"").matcher(html);
        while (matcher.find()) found.add(Integer.parseInt(matcher.group(1)));
        return found;
    }

    @Test void headingsCarryTheirIds() {
        assertThat(web("# Getting started\n").html()).contains("<h1 id=\"getting-started\" data-b=\"0\">Getting started</h1>");
    }

    @Test void hostileHtmlStaysText() {
        String source = """
            <script>alert(1)</script>

            <img src="x" onerror="alert(2)">

            <iframe src="https://evil.example/frame"></iframe>

            <meta http-equiv="refresh" content="0;url=https://evil.example/">

            <base href="https://evil.example/">

            <form action="https://evil.example/post"><input name="q" autofocus onfocus="alert(3)"></form>

            <style>body { background: url(https://evil.example/style.png) }</style>

            Inline <script>alert(4)</script>, <img src=x onerror=alert(5)> and <a href="javascript:alert(6)">a raw link</a>.

            [a javascript: link](javascript:alert(7)) and [a data: link](data:text/html,<script>alert(8)</script>)

            ```mermaid
            %%{init: {"securityLevel": "loose"}}%%
            flowchart LR
                A["<img src=https://evil.example/node.png onerror=alert(9)>"] --> B
                click A callback "Tooltip"
                click B "javascript:alert(10)"
            ```

            ![a network image](https://evil.example/pixel.png "title")
            """;
        String html = web(source).html();
        assertVocabulary(html);
        // Quotes in escaped text are harmless: with every < escaped, no attribute can form.
        assertThat(html).doesNotContain("<script", "<img src", "<iframe", "<meta", "<base", "<form", "<input name", "<style",
            "<a href=\"javascript", "<a href=\"data");
        assertThat(html).contains("&lt;script&gt;alert(1)&lt;/script&gt;", "&lt;img src=x onerror=alert(5)&gt;",
            "&lt;meta http-equiv=", "&lt;base href=", "<pre class=\"mermaid\"", "--&gt; B",
            "<img data-image=\"0\" alt=\"a network image\">");
    }

    @Test void linksAreIds() {
        var result = web("[x](javascript:alert(1))\n");
        assertThat(result.html()).contains("<a href=\"jasper-link:0\" data-link=\"0\">x</a>").doesNotContain("javascript");
        assertThat(result.links().get(0).destination()).isEqualTo("javascript:alert(1)");
        assertThat(Destinations.parse(result.links().get(0).destination())).isInstanceOf(Destination.Unsupported.class);
    }

    @Test void imagesAreIdsWithNoSource() {
        var result = web("![a \"quoted\" <alt>](https://example.com/a.png)\n");
        assertThat(result.html()).contains("<img data-image=\"0\" alt=\"a &quot;quoted&quot; &lt;alt&gt;\">")
            .doesNotContain("src", "example.com");
        assertThat(result.images()).containsExactly(new MarkdownResult.Image(0, "https://example.com/a.png", "a \"quoted\" <alt>"));
    }

    @Test void alertsUseGithubsMarkup() {
        var result = web("> [!WARNING]\n> Careful\n");
        assertThat(result.html()).contains("<div class=\"markdown-alert markdown-alert-warning\" data-b=\"0\">",
            "<p class=\"markdown-alert-title\">Warning</p>", "<p data-b=\"1\">Careful</p>");
        assertThat(result.blocks()).hasSize(2);
    }

    @Test void footnotesAreNumberedByFirstReference() {
        String source = "b[^y] a[^x]\n\n[^x]: X\n[^y]: Y\n[^z]: Z";
        var result = web(source);
        String html = result.html();
        assertThat(html).contains(
            "b<sup class=\"footnote-ref\"><a href=\"jasper-link:0\" data-link=\"0\" id=\"fnref-1\">1</a></sup>",
            " a<sup class=\"footnote-ref\"><a href=\"jasper-link:1\" data-link=\"1\" id=\"fnref-2\">2</a></sup>",
            "<section class=\"footnotes\"><ol>",
            "<li id=\"fn-1\"><p>Y <a href=\"jasper-link:2\" data-link=\"2\" class=\"footnote-backref\" aria-label=\"Back to reference 1\">\u21a9</a></p>",
            "<li id=\"fn-2\"><p>X <a href=\"jasper-link:3\" data-link=\"3\" class=\"footnote-backref\" aria-label=\"Back to reference 2\">\u21a9</a></p>");
        assertThat(html.indexOf("<li id=\"fn-1\"><p>Y")).isLessThan(html.indexOf("<li id=\"fn-2\"><p>X"));
        assertThat(html).doesNotContain("Z");
        assertThat(result.links()).extracting(MarkdownResult.Link::destination).containsExactly("#fn-1", "#fn-2", "#fnref-1", "#fnref-2");
        assertThat(result.headings()).containsOnlyKeys("fn-1", "fn-2", "fnref-1", "fnref-2")
            .containsEntry("fn-1", source.indexOf("[^y]:")).containsEntry("fn-2", source.indexOf("[^x]:"))
            .containsEntry("fnref-1", 0).containsEntry("fnref-2", 0);
        assertThat(Destinations.parse(result.links().get(0).destination())).isEqualTo(new Destination.Fragment("fn-1"));
    }

    @Test void definitionsCarryNoBlockAnchorSoScrollSyncKeepsSourceOrder() {
        String source = "a[^1]\n\n[^1]: The note\n\n    and its second paragraph.\n\nb\n";
        for (var result : List.of(web(source), swing(source))) {
            assertThat(result.blocks()).extracting(MarkdownResult.BlockAnchor::sourceStart).containsExactly(0, source.indexOf("b\n"));
            assertThat(result.headings()).containsEntry("fn-1", source.indexOf("[^1]:"));
        }
        assertThat(web(source).html()).contains("<li id=\"fn-1\"><p>The note</p>", "<p>and its second paragraph. <a href=");
    }

    @Test void footnoteNamesComeAfterEveryHeadingAndOnlyTheFirstReferenceIsNamed() {
        String source = "x[^a] y[^a]\n\n# fn 1\n\n[^a]: A\n\n# fnref 1\n";
        var result = web(source);
        assertThat(result.headings()).containsEntry("fn-1", source.indexOf("# fn 1")).containsEntry("fnref-1", source.indexOf("# fnref 1"))
            .containsEntry("fn-1-1", source.indexOf("[^a]:")).containsEntry("fnref-1-1", 0);
        assertThat(result.html()).contains("<h1 id=\"fn-1\"", "<h1 id=\"fnref-1\"", "<li id=\"fn-1-1\">", " id=\"fnref-1-1\">1</a>");
        assertThat(result.html().split("id=\"fnref-1-1\"", -1)).hasSize(2);
        assertThat(result.links()).extracting(MarkdownResult.Link::destination).containsExactly("#fn-1-1", "#fn-1-1", "#fnref-1-1");
    }

    /**
     * W5: a heading is named from its text with each footnote reference read as its number, as GitHub names it and as
     * Jasper did before footnotes: {@code # Heading [^1]} is {@code heading-1}, in both dialects, whichever note it is.
     */
    @Test void aHeadingsNameReadsAFootnoteReferenceAsItsNumber() {
        String first = "# Heading [^1]\n\nSee [the heading](#heading-1).\n\n[^1]: The note\n";
        String later = "a[^x] b[^y]\n\n# Later [^y]\n\n# Again [^x]\n\n[^x]: X\n[^y]: Y\n";
        for (var result : List.of(web(first), swing(first))) {
            assertThat(result.headings()).containsEntry("heading-1", 0).doesNotContainKey("heading-");
            var link = result.links().stream().filter(each -> each.destination().equals("#heading-1")).findFirst().orElseThrow();
            assertThat(Destinations.parse(link.destination())).isEqualTo(new Destination.Fragment("heading-1"));
        }
        assertThat(web(first).html()).startsWith("<h1 id=\"heading-1\" data-b=\"0\">Heading <sup class=\"footnote-ref\">");
        for (var result : List.of(web(later), swing(later)))
            assertThat(result.headings()).containsEntry("later-2", later.indexOf("# Later")).containsEntry("again-1", later.indexOf("# Again"));
    }

    /** W6: footnote labels match whatever their case, as commonmark matches them. */
    @Test void aFootnoteLabelMatchesInAnyCase() {
        String source = "a[^Note] b[^NOTE]\n\n[^note]: The note\n";
        for (var result : List.of(web(source), swing(source))) {
            assertThat(result.headings()).containsOnlyKeys("fn-1", "fnref-1");
            assertThat(result.links()).extracting(MarkdownResult.Link::destination).containsExactly("#fn-1", "#fn-1", "#fnref-1");
            assertThat(result.html()).contains("The note");
        }
    }

    /** W6: a footnote reference with no note, which commonmark does not make but a tree can hold, is written as its text. */
    @Test void aFootnoteReferenceWithNoNoteIsItsText() {
        var root = new Document();
        var paragraph = new Paragraph();
        paragraph.appendChild(new Text("See "));
        paragraph.appendChild(new FootnoteReference("missing"));
        paragraph.appendChild(new Text("."));
        root.appendChild(paragraph);
        var heading = new Heading();
        heading.setLevel(2);
        heading.appendChild(new Text("Title "));
        heading.appendChild(new FootnoteReference("gone"));
        root.appendChild(heading);
        var web = MarkdownRenderer.write("See [^missing].", root, MarkdownRendererTest.palette(), Dialect.WEB);
        var swing = MarkdownRenderer.write("See [^missing].", root, MarkdownRendererTest.palette(), Dialect.SWING);
        assertThat(web.html()).contains("<p>See [^missing].</p>", "<h2 id=\"title-gone\">Title [^gone]</h2>");
        assertThat(swing.html()).contains("<p>See [^missing].</p>", "<h2>Title [^gone]</h2>");
        for (var result : List.of(web, swing)) {
            assertThat(result.links()).isEmpty();
            assertThat(result.headings()).containsOnlyKeys("title-gone");
        }
    }

    @Test void autolinksBecomeLinks() {
        var result = web("see https://example.com and www.example.org\n");
        assertThat(result.links()).extracting(MarkdownResult.Link::destination).containsExactly("https://example.com", "http://www.example.org");
        assertThat(result.html()).contains("<a href=\"jasper-link:0\" data-link=\"0\">https://example.com</a>",
            "<a href=\"jasper-link:1\" data-link=\"1\">www.example.org</a>");
    }

    @Test void mermaidBlocksStayText() {
        var result = web("```mermaid\ngraph TD; A-->B\n```\n");
        assertThat(result.html()).contains("<pre class=\"mermaid\" data-b=\"0\">graph TD; A--&gt;B</pre>");
        assertThat(result.blocks()).hasSize(1);
        assertThat(web("```Mermaid title\nx\n```\n").html()).contains("<pre class=\"mermaid\" data-b=\"0\">x</pre>");
        assertThat(web("    mermaid\n").html()).doesNotContain("class=\"mermaid\"");
    }

    @Test void codeIsHighlightedWithClasses() {
        var result = web("```java\nreturn 1;\n\nx\n```\n");
        assertThat(result.html()).contains("<pre data-b=\"0\"><code><span class=\"line\" data-b=\"1\">",
            "<span class=\"tok-keyword\">return</span>", "<span class=\"tok-number\">1</span>",
            "</span>\n<span class=\"line\" data-b=\"2\"></span>\n<span class=\"line\" data-b=\"3\">x</span>\n</code></pre>")
            .doesNotContain("<font", "color", "<b>", "<i>");
        assertThat(result.blocks()).hasSize(4);
        assertThat(web("```\n/* x */\n```\n").html()).contains("<span class=\"line\" data-b=\"1\">/* x */</span>");
    }

    @Test void tablesAlignWithClasses() {
        var result = web("| a | b | c | d |\n| :- | :-: | -: | - |\n| 1 | 2 | 3 | 4 |\n");
        assertThat(result.html()).contains("<table>", "<tr data-b=\"0\"><th class=\"align-left\">a</th><th class=\"align-center\">b</th>"
                + "<th class=\"align-right\">c</th><th>d</th></tr>",
            "<tr data-b=\"1\"><td class=\"align-left\">1</td><td class=\"align-center\">2</td><td class=\"align-right\">3</td><td>4</td></tr>")
            .doesNotContain("align=");
    }

    @Test void taskItemsAreDisabledBoxes() {
        var result = web("- [x] done\n- [ ] todo\n- plain\n");
        assertThat(result.html()).contains(
            "<li class=\"task-list-item\" data-b=\"0\"><p data-b=\"1\"><input type=\"checkbox\" disabled checked> done</p>",
            "<li class=\"task-list-item\" data-b=\"2\"><p data-b=\"3\"><input type=\"checkbox\" disabled> todo</p>",
            "<li data-b=\"4\"><p data-b=\"5\">plain</p>").doesNotContain("\u2611", "\u2610");
    }

    @Test void inlineMarkupAndRawHtml() {
        var result = web("*a* **b** ~~c~~ `<d>` e  \nf <b>g</b> <!-- h -->\n\n<div>i</div>\n\n<!-- j -->\n\n***\n\n3. k\n");
        assertThat(result.html()).contains("<p data-b=\"0\"><em>a</em> <strong>b</strong> <del>c</del> <code>&lt;d&gt;</code> e<br>\nf "
                + "&lt;b&gt;g&lt;/b&gt; </p>", "<p data-b=\"1\">&lt;div&gt;i&lt;/div&gt;</p>", "<hr data-b=\"2\">",
            "<ol start=\"3\"><li data-b=\"3\"><p data-b=\"4\">k</p>")
            .doesNotContain("h --", "j --", "<!--", "<html", "<body");
    }

    @Test void everyConstructStaysInTheVocabularyWithOneAnchorPerBlock() throws Exception {
        var result = web(fixture());
        assertVocabulary(result.html());
        assertThat(dataBlocks(result.html())).containsExactlyElementsOf(IntStream.range(0, result.blocks().size()).boxed().toList());
        assertThat(result.html()).contains("markdown-alert-note", "markdown-alert-tip", "markdown-alert-important",
            "markdown-alert-warning", "markdown-alert-caution", "class=\"footnotes\"", "class=\"mermaid\"", "tok-keyword",
            "task-list-item", "align-center").doesNotContain("A definition that nothing references");
    }

    @Test void bothDialectsAnchorTheSameBlocks() throws Exception {
        String source = fixture().replaceAll("(?ms)^```mermaid\\n.*?^```\\n", "");
        assertThat(source).doesNotContain("mermaid").contains("[^first]", "> [!NOTE]");
        var web = web(source);
        var swing = swing(source);
        assertThat(web.blocks()).isNotEmpty().isEqualTo(swing.blocks());
        assertThat(web.headings()).isEqualTo(swing.headings());
        assertThat(web.links()).isEqualTo(swing.links());
        assertThat(web.images()).isEqualTo(swing.images());
        var names = new ArrayList<Integer>();
        var matcher = Pattern.compile("<a name=\"b(\\d+)\"></a>").matcher(swing.html());
        while (matcher.find()) names.add(Integer.parseInt(matcher.group(1)));
        assertThat(names).isEqualTo(dataBlocks(web.html()));
    }

    @Test void theLimitsHold() {
        assertThat(web("x\n\n".repeat(50_000)).blocks()).hasSize(50_000);
        assertThatThrownBy(() -> web("x\n\n".repeat(50_001))).isInstanceOf(PreviewLimitException.class);
        assertThatThrownBy(() -> web("x".repeat(2_097_153))).isInstanceOf(PreviewLimitException.class);
        assertThatThrownBy(() -> web("```java\n" + ("int ".repeat(100) + "\n").repeat(5_200) + "```\n"))
            .isInstanceOf(PreviewLimitException.class);
    }

    @Test void interruptCancelsTheWebDialectToo() {
        Thread.currentThread().interrupt();
        try { assertThatThrownBy(() -> web("# heading\n")).isInstanceOf(java.util.concurrent.CancellationException.class); }
        finally { Thread.interrupted(); }
    }
}
