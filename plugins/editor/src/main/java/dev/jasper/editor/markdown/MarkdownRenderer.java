package dev.jasper.editor.markdown;

import dev.jasper.sdk.ui.SyntaxPalette;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
import org.commonmark.ext.task.list.items.TaskListItemsExtension;
import org.commonmark.node.Block;
import org.commonmark.node.BlockQuote;
import org.commonmark.node.BulletList;
import org.commonmark.node.Code;
import org.commonmark.node.Emphasis;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.HardLineBreak;
import org.commonmark.node.Heading;
import org.commonmark.node.HtmlBlock;
import org.commonmark.node.HtmlInline;
import org.commonmark.node.Image;
import org.commonmark.node.IndentedCodeBlock;
import org.commonmark.node.Link;
import org.commonmark.node.LinkReferenceDefinition;
import org.commonmark.node.ListItem;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.node.Paragraph;
import org.commonmark.node.SoftLineBreak;
import org.commonmark.node.SourceSpan;
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;
import org.commonmark.parser.IncludeSourceSpans;
import org.commonmark.parser.Parser;

/** Parses a snapshot into a deliberately small Swing HTML vocabulary and inert destination metadata. */
public final class MarkdownRenderer {
    /** Creates a complete bounded result without allocating or touching a Swing document. */
    public MarkdownResult render(String source, SyntaxPalette palette) {
        MarkdownLimits.requireSource(source);
        Objects.requireNonNull(palette);
        checkInterrupted();
        var parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create())).build();
        var root = parser.parse(source);
        var output = new Output(source, palette);
        output.html.append("<html><body>");
        output.children(root);
        output.html.append("</body></html>");
        return new MarkdownResult(output.html.toString(), output.blocks, output.headings, output.links, output.images);
    }

    private static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Markdown render interrupted");
    }

    /** Escapes visible text incrementally; no escaped copy can grow past the output budget. */
    static void text(MarkdownLimits.Html out, String value) { escape(out, value, false); }

    private static void attribute(MarkdownLimits.Html out, String value) { escape(out, value, true); }

    private static void escape(MarkdownLimits.Html out, String value, boolean attribute) {
        int start = 0;
        for (int i = 0; i < value.length(); i++) {
            String replacement = switch (value.charAt(i)) {
                case '&' -> "&amp;";
                case '<' -> "&lt;";
                case '>' -> "&gt;";
                case '"' -> attribute ? "&quot;" : null;
                case '\'' -> attribute ? "&#39;" : null;
                default -> null;
            };
            if (replacement != null) {
                out.append(value.substring(start, i));
                out.append(replacement);
                start = i + 1;
            }
        }
        out.append(value.substring(start));
    }

    private static final class Output {
        private final String source;
        private final int[] lineStarts;
        private final SyntaxPalette palette;
        private final MarkdownLimits.Html html = new MarkdownLimits.Html();
        private final List<MarkdownResult.BlockAnchor> blocks = new ArrayList<>();
        private final Map<String, Integer> headings = new LinkedHashMap<>();
        private final List<MarkdownResult.Link> links = new ArrayList<>();
        private final List<MarkdownResult.Image> images = new ArrayList<>();
        private final HeadingIds headingIds = new HeadingIds();

        Output(String source, SyntaxPalette palette) {
            this.source = source;
            this.palette = palette;
            int count = 1;
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\r') { count++; if (i + 1 < source.length() && source.charAt(i + 1) == '\n') i++; }
                else if (source.charAt(i) == '\n') count++;
            }
            lineStarts = new int[count];
            int line = 1;
            for (int i = 0; i < source.length(); i++) {
                if (source.charAt(i) == '\r') {
                    if (i + 1 < source.length() && source.charAt(i + 1) == '\n') i++;
                    lineStarts[line++] = i + 1;
                } else if (source.charAt(i) == '\n') lineStarts[line++] = i + 1;
            }
        }

        void children(Node parent) {
            for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) node(node);
        }

        // This exhaustive vocabulary is the tag allowlist. No CommonMark HTML renderer or document attributes pass through.
        void node(Node node) {
            if (node instanceof Block || node instanceof TableRow) checkInterrupted();
            switch (node) {
                case Heading h -> {
                    html.append("<h" + h.getLevel() + ">");
                    anchor(h);
                    headings.put(headingIds.next(visible(h)), start(h));
                    children(h);
                    html.append("</h" + h.getLevel() + ">\n");
                }
                case Paragraph p -> container("p", p);
                case BlockQuote q -> container("blockquote", q);
                case BulletList list -> container("ul", list);
                case OrderedList list -> {
                    html.append("<ol start=\"" + list.getMarkerStartNumber() + "\">");
                    children(list); html.append("</ol>\n");
                }
                case ListItem item -> container("li", item);
                case FencedCodeBlock code -> code(code, code.getLiteral(), code.getInfo(), true);
                case IndentedCodeBlock code -> code(code, code.getLiteral(), "", false);
                case ThematicBreak rule -> { anchor(rule); html.append("<hr>\n"); }
                case HtmlBlock raw -> {
                    if (hasNonCommentText(raw.getLiteral())) {
                        html.append("<p>"); anchor(raw); raw(raw.getLiteral()); html.append("</p>\n");
                    }
                }
                case HtmlInline raw -> raw(raw.getLiteral());
                case Text text -> text(html, text.getLiteral());
                case Code code -> { html.append("<code>"); text(html, code.getLiteral()); html.append("</code>"); }
                case Emphasis em -> container("em", em);
                case StrongEmphasis strong -> container("strong", strong);
                case Strikethrough strike -> container("strike", strike);
                case HardLineBreak ignored -> html.append("<br>\n");
                case SoftLineBreak ignored -> html.append("\n");
                case Link link -> {
                    int id = links.size();
                    links.add(new MarkdownResult.Link(id, link.getDestination()));
                    html.append("<a href=\"jasper-link:" + id + "\">"); children(link); html.append("</a>");
                }
                case Image image -> {
                    int id = images.size(); String alt = visible(image);
                    images.add(new MarkdownResult.Image(id, image.getDestination(), alt));
                    html.append("<img src=\"jasper-image:" + id + "\" alt=\""); attribute(html, alt); html.append("\">");
                }
                case TableBlock table -> container("table", table);
                case TableHead head -> children(head);
                case TableBody body -> children(body);
                case TableRow row -> { html.append("<tr>"); children(row); html.append("</tr>\n"); }
                case TableCell cell -> {
                    String tag = cell.isHeader() ? "th" : "td";
                    html.append("<" + tag);
                    if (cell.getAlignment() != null) html.append(" align=\"" + cell.getAlignment().name().toLowerCase(Locale.ROOT) + "\"");
                    html.append(">");
                    if (cell.getPrevious() == null) anchor(cell.getParent());
                    children(cell); html.append("</" + tag + ">");
                }
                case TaskListItemMarker ignored -> { }
                case LinkReferenceDefinition ignored -> { }
                default -> children(node);
            }
        }

        void container(String tag, Node node) {
            html.append("<" + tag + ">");
            if (node instanceof Block && !(node instanceof BulletList) && !(node instanceof TableBlock)) anchor(node);
            if (node instanceof Paragraph && node.getPrevious() instanceof TaskListItemMarker task)
                html.append(task.isChecked() ? "\u2611 " : "\u2610 ");
            children(node);
            html.append("</" + tag + ">" + (node instanceof Block || node instanceof TableBlock ? "\n" : ""));
        }

        boolean hasNonCommentText(String literal) {
            for (int from = 0; from < literal.length();) {
                if (literal.startsWith("<!--", from)) {
                    int end = literal.indexOf("-->", from + 4);
                    if (end < 0) return false;
                    from = end + 3;
                } else if (!Character.isWhitespace(literal.charAt(from++))) return true;
            }
            return false;
        }

        void raw(String literal) {
            // CommonMark keeps a complete inline/block comment as a literal. Drop comments embedded in HTML blocks too.
            int from = 0;
            while (from < literal.length()) {
                int comment = literal.indexOf("<!--", from);
                if (comment < 0) { text(html, literal.substring(from)); return; }
                text(html, literal.substring(from, comment));
                int end = literal.indexOf("-->", comment + 4);
                if (end < 0) return;
                from = end + 3;
            }
        }

        int start(Node node) {
            var spans = node.getSourceSpans();
            return spans.isEmpty() ? 0 : spans.getFirst().getInputIndex();
        }

        void anchor(Node node) {
            var spans = node.getSourceSpans();
            if (spans.isEmpty()) return;
            var last = spans.getLast();
            anchor(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
        }

        void anchor(int start, int end) {
            if (blocks.size() >= MarkdownLimits.BLOCKS) throw new PreviewLimitException();
            String id = "b" + blocks.size();
            blocks.add(new MarkdownResult.BlockAnchor(id, start, end));
            html.append("<a name=\"" + id + "\"></a>");
        }

        void code(Block node, String literal, String language, boolean fenced) {
            html.append("<pre>"); anchor(node);
            var highlighter = new CodeHighlighter(language == null ? "" : language, palette);
            var spans = node.getSourceSpans();
            int line = 0;
            int spanCursor = 0;
            for (int from = 0; from < literal.length(); line++) {
                checkInterrupted();
                int end = literal.indexOf('\n', from);
                if (end < 0) end = literal.length();
                String value = literal.substring(from, end);
                int sourceLine = spans.getFirst().getLineIndex() + line + (fenced ? 1 : 0);
                while (spanCursor < spans.size() && spans.get(spanCursor).getLineIndex() < sourceLine) spanCursor++;
                SourceSpan span = spanCursor < spans.size() && spans.get(spanCursor).getLineIndex() == sourceLine
                    ? spans.get(spanCursor) : null;
                if (span != null) {
                    int start = span.getInputIndex();
                    // Source spans include code indentation; map the content rather than the syntactic prefix.
                    int extra = fenced ? ((FencedCodeBlock) node).getFenceIndent() : 4;
                    while (extra > 0 && start < span.getInputIndex() + span.getLength() && source.charAt(start) == ' ') { start++; extra--; }
                    anchor(start, span.getInputIndex() + span.getLength());
                } else if (sourceLine < lineStarts.length) {
                    // CommonMark omits zero-length source spans. Keep the blank line's source position explicitly.
                    int start = lineStarts[sourceLine];
                    int sourceEnd = sourceLine + 1 < lineStarts.length ? lineStarts[sourceLine + 1] : source.length();
                    while (sourceEnd > start && (source.charAt(sourceEnd - 1) == '\r' || source.charAt(sourceEnd - 1) == '\n')) sourceEnd--;
                    anchor(start, sourceEnd);
                }
                highlighter.line(value, html);
                if (end < literal.length()) html.append("\n");
                from = end + 1;
            }
            html.append("</pre>\n");
        }

        String visible(Node parent) {
            var text = new StringBuilder();
            // Use an explicit stack so adversarial inline nesting does not grow the Java call stack.
            var pending = new java.util.ArrayDeque<Node>();
            for (Node child = parent.getLastChild(); child != null; child = child.getPrevious()) pending.push(child);
            while (!pending.isEmpty()) {
                Node child = pending.pop();
                if (child instanceof Text value) text.append(value.getLiteral());
                else if (child instanceof Code value) text.append(value.getLiteral());
                else if (child instanceof HardLineBreak || child instanceof SoftLineBreak) text.append(' ');
                else if (child instanceof HtmlInline value) {
                    if (!value.getLiteral().startsWith("<!--")) text.append(value.getLiteral());
                } else for (Node nested = child.getLastChild(); nested != null; nested = nested.getPrevious()) pending.push(nested);
            }
            return text.toString();
        }
    }
}
