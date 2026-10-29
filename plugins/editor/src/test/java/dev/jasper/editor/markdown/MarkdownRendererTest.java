package dev.jasper.editor.markdown;

import dev.jasper.sdk.ui.SyntaxPalette;
import dev.jasper.sdk.ui.SyntaxRole;
import dev.jasper.sdk.ui.SyntaxStyle;
import java.awt.Color;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MarkdownRendererTest {
    static SyntaxPalette palette() {
        var styles = new EnumMap<SyntaxRole, SyntaxStyle>(SyntaxRole.class);
        for (var role : SyntaxRole.values()) styles.put(role, new SyntaxStyle(new Color(20 + role.ordinal(), 40, 60), false, false));
        return new SyntaxPalette(styles);
    }
    static MarkdownResult render(String source) { return new MarkdownRenderer().render(source, palette()); }

    @Test void headingNamesCannotCollideWithEarlierSuffixes() {
        var ids = new HeadingIds();
        assertThat(ids.next("A")).isEqualTo("a");
        assertThat(ids.next("A-1")).isEqualTo("a-1");
        assertThat(ids.next("A")).isEqualTo("a-2");
        assertThat(ids.next("!!!")).isEqualTo("section");
        assertThat(ids.next("  É e\u0301 _ B  ")).isEqualTo("-é-e\u0301-_-b-");
        assertThat(ids.next("A-2")).isEqualTo("a-2-1");
        assertThat(ids.next("A")).isEqualTo("a-3");
    }

    @Test void everydayMarkdownUsesControlledTagsAndPrivateResourceIdentifiers() throws Exception {
        String source = new String(getClass().getResourceAsStream("/markdown/everyday.md").readAllBytes(), StandardCharsets.UTF_8);
        var result = render(source);
        assertThat(result.headings()).containsExactlyInAnyOrderEntriesOf(Map.of("a", 0, "a-1", 4, "a-2", 10, "section", 14));
        assertThat(result.html()).contains("<h1>", "<em>small</em>", "<strong>document</strong>", "<strike>old</strike>",
            "<blockquote>", "<ul>", "<li>", "<table>", "align=\"left\"", "align=\"right\"", "align=\"center\"",
            "\u2611 finished", "\u2610 pending", "&lt;script&gt;", "&lt;b title=\"x\"&gt;", "&lt;escaped&gt;", "<pre>", "&lt;x&gt;&amp;");
        assertThat(result.html()).doesNotContain("<!--", "hidden comment", "<script", "<input", "href=\"https://", "src=\"https://", "images/picture", "title=\"ignored");
        assertThat(result.links()).extracting(MarkdownResult.Link::destination).containsExactly("https://example.invalid/a?x=1&y=2", "https://example.invalid/auto");
        assertThat(result.images()).containsExactly(new MarkdownResult.Image(0, "images/picture.png", "a small image"));
        assertThat(result.html()).contains("href=\"jasper-link:0\"", "src=\"jasper-image:0\"");
        assertThat(result.blocks()).allSatisfy(block -> {
            assertThat(block.sourceStart()).isBetween(0, source.length());
            assertThat(block.sourceEnd()).isBetween(block.sourceStart(), source.length());
            assertThat(result.html()).contains("<a name=\"" + block.id() + "\"></a>");
        });
        assertThat(result.blocks().size()).isGreaterThan(24);
    }

    @Test void visibleHeadingTextAndSourceRangesUseUtf16() {
        String source = "# \uD83D\uDE00 *Café* `code` ![alt](x)\n\n\uD83D\uDE00 tail\n";
        var result = render(source);
        assertThat(result.headings()).containsEntry("-café-code-alt", 0);
        assertThat(result.blocks()).anySatisfy(block -> {
            assertThat(block.sourceStart()).isEqualTo(30);
            assertThat(block.sourceEnd()).isEqualTo(37);
        });
    }

    @Test void mapIncludesNestedInteriorsTableRowsAndEveryCodeLine() {
        String source = "> - outer\n>   - inner\n\n| a | b |\n| - | - |\n| c | d |\n\n```\none\ntwo\n```\n";
        var result = render(source);
        assertThat(result.blocks()).anySatisfy(b -> assertThat(source.substring(b.sourceStart(), b.sourceEnd())).isEqualTo("inner"));
        assertThat(result.blocks()).anySatisfy(b -> assertThat(source.substring(b.sourceStart(), b.sourceEnd())).isEqualTo("| c | d |"));
        assertThat(result.blocks()).anySatisfy(b -> assertThat(source.substring(b.sourceStart(), b.sourceEnd())).isEqualTo("one"));
        assertThat(result.blocks()).anySatisfy(b -> assertThat(source.substring(b.sourceStart(), b.sourceEnd())).isEqualTo("two"));
    }

    @Test void commentsAreOmittedButOtherHtmlIsEscaped() {
        assertThat(render("before <!-- hide\nmore --> after\n\n<svg>bad</svg>\n").html())
            .contains("before ", " after", "&lt;svg&gt;bad&lt;/svg&gt;").doesNotContain("hide", "more", "<!--", "<svg>");
    }

    @Test void aCommentOnlyBlockHasNoVisibleLayoutOrMapping() {
        assertThat(render("<!-- hidden -->\n").html()).isEqualTo("<html><body></body></html>");
        assertThat(render("<!-- hidden -->\n").blocks()).isEmpty();
    }

    @Test void attributeTextCannotInjectMarkupAndDestinationsRemainInMetadata() {
        var result = render("![&quot; &lt; &amp;](javascript:evil) [click](file:/secret)\n");
        assertThat(result.html()).contains("alt=\"&quot; &lt; &amp;\"")
            .doesNotContain("javascript:", "file:", "src=\"javascript", "href=\"file");
        assertThat(result.images()).containsExactly(new MarkdownResult.Image(0, "javascript:evil", "\" < &"));
        assertThat(result.links()).containsExactly(new MarkdownResult.Link(0, "file:/secret"));
    }

    @Test void independentTokenizersCarryMultilineStateAndUnknownLanguageStaysPlain() {
        String java = render("```java\n/* start\nend */ int x = 1;\n```\n").html();
        assertThat(java).contains("<font color=\"#");
        String commentColor = String.format("#%02x283c", 20 + SyntaxRole.COMMENT.ordinal());
        assertThat(java).contains("color=\"" + commentColor + "\">/* start", "color=\"" + commentColor + "\">end */");
        assertThat(render("```not-a-language\nint x = 1;\n```\n").html()).doesNotContain("<font");
        assertThat(render("```java\nint x = 1;\n```\n").html()).doesNotContain("color=\"" + commentColor + "\">int");
    }

    @Test void resultCopiesEveryCollection() {
        var blocks = new java.util.ArrayList<MarkdownResult.BlockAnchor>();
        var headings = new java.util.HashMap<String, Integer>();
        var links = new java.util.ArrayList<MarkdownResult.Link>();
        var images = new java.util.ArrayList<MarkdownResult.Image>();
        var result = new MarkdownResult("", blocks, headings, links, images);
        blocks.add(new MarkdownResult.BlockAnchor("b0", 0, 1)); headings.put("h", 0);
        links.add(new MarkdownResult.Link(0, "x")); images.add(new MarkdownResult.Image(0, "x", ""));
        assertThat(result.blocks()).isEmpty(); assertThat(result.headings()).isEmpty();
        assertThat(result.links()).isEmpty(); assertThat(result.images()).isEmpty();
        assertThatThrownBy(() -> result.blocks().add(blocks.getFirst())).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void modesAreStrictSavedKeys() {
        assertThat(MarkdownMode.parse("split")).isEqualTo(MarkdownMode.SPLIT);
        assertThat(MarkdownMode.PREVIEW.key()).isEqualTo("preview");
        for (String bad : List.of("SOURCE", " source", "preview ", "other"))
            assertThatThrownBy(() -> MarkdownMode.parse(bad)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test void rejectsOversizedSourceBeforeParsing() {
        assertThatThrownBy(() -> render("x".repeat(2_097_153))).isInstanceOf(PreviewLimitException.class);
        MarkdownLimits.requireSource("x".repeat(2_097_152));
    }

    @Test void boundsOutputDuringEmissionIncludingUtf8AndAnchorCount() {
        var ascii = new MarkdownLimits.Html();
        ascii.append("a".repeat(16 * 1024 * 1024));
        assertThatThrownBy(() -> ascii.append("a")).isInstanceOf(PreviewLimitException.class);
        var utf8 = new MarkdownLimits.Html();
        utf8.append("\uD83D\uDE00".repeat(4 * 1024 * 1024));
        assertThatThrownBy(() -> utf8.append("é")).isInstanceOf(PreviewLimitException.class);
        assertThat(render("x\n\n".repeat(50_000)).blocks()).hasSize(50_000);
        assertThatThrownBy(() -> render("x\n\n".repeat(50_001))).isInstanceOf(PreviewLimitException.class);
    }

    @Test void utf8BudgetIncludesSurrogatePairsSplitAcrossAppends() {
        var out = new MarkdownLimits.Html();
        out.append("a".repeat(16 * 1024 * 1024 - 3));
        out.append("\uD83D");
        assertThatThrownBy(() -> out.append("\uDE00")).isInstanceOf(PreviewLimitException.class);
    }

    @Test void deeplyNestedBlocksDoNotExhaustTheJavaStack() {
        var result = render("> ".repeat(4_000) + "end\n");
        assertThat(result.html()).contains("end");
    }

    @Test void tokenTagsCannotGrowPastTheHtmlBudget() {
        // Keep each lexer call small while the whole block still exceeds the HTML budget.
        assertThatThrownBy(() -> render("```java\n" + ("int ".repeat(100) + "\n").repeat(5_200) + "```\n"))
            .isInstanceOf(PreviewLimitException.class);
    }

    @Test void escapedMarkupCannotGrowPastTheHtmlBudget() {
        var out = new MarkdownLimits.Html();
        out.append("a".repeat(16 * 1024 * 1024 - 4));
        assertThatThrownBy(() -> MarkdownRenderer.text(out, "&"))
            .isInstanceOf(PreviewLimitException.class);
        assertThat(out.toString()).hasSize(16 * 1024 * 1024 - 4);
    }

    @Test void mapsBlankAndIndentedCodeLinesWithCrLfSource() {
        String source = "```\r\none\r\n\r\ntwo\r\n```\r\n\r\n    indented\r\n";
        var result = render(source);
        assertThat(result.blocks()).anySatisfy(b -> {
            assertThat(b.sourceStart()).isEqualTo(10);
            assertThat(b.sourceEnd()).isEqualTo(10);
        });
        assertThat(result.blocks()).anySatisfy(b -> {
            assertThat(b.sourceStart()).isEqualTo(28);
            assertThat(b.sourceEnd()).isEqualTo(36);
        });
    }

    @Test void interruptCancelsAtABlockBoundary() {
        Thread.currentThread().interrupt();
        try { assertThatThrownBy(() -> render("# heading\n")).isInstanceOf(java.util.concurrent.CancellationException.class); }
        finally { Thread.interrupted(); }
    }
}
