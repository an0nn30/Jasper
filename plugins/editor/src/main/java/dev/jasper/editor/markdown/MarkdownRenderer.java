package dev.jasper.editor.markdown;

import dev.jasper.sdk.ui.SyntaxPalette;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CancellationException;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.footnotes.FootnotesExtension;
import org.commonmark.ext.gfm.alerts.Alert;
import org.commonmark.ext.gfm.alerts.AlertsExtension;
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

/**
 * Parses a snapshot into one of two deliberately small vocabularies and inert destination metadata. Both dialects
 * share what is kept here: the block anchors, heading and footnote names, links, images, limits and interruption.
 */
public final class MarkdownRenderer {
    /** The vocabulary a result is written in. */
    public enum Dialect {
        /** A document in the Swing HTML subset {@code PreviewPane.prepare} admits, with an empty named anchor per block. */
        SWING,
        /** A body fragment in GitHub's markup for the Chromium preview, with each block's anchor on its element. */
        WEB
    }

    /** Creates a complete bounded result without allocating or touching a Swing document. */
    public MarkdownResult render(String source, SyntaxPalette palette, Dialect dialect) {
        MarkdownLimits.requireSource(source);
        Objects.requireNonNull(palette);
        Objects.requireNonNull(dialect);
        checkInterrupted();
        var parser = Parser.builder().includeSourceSpans(IncludeSourceSpans.BLOCKS_AND_INLINES)
            .extensions(List.of(TablesExtension.create(), StrikethroughExtension.create(), TaskListItemsExtension.create(),
                AlertsExtension.create(), FootnotesExtension.create(), AutolinkExtension.create())).build();
        var root = parser.parse(source);
        var output = new Output(source, palette, root);
        switch (dialect) {
            case SWING -> new Swing(output).document(root);
            case WEB -> new WebMarkup(output).document(root);
        }
        return output.result();
    }

    static void checkInterrupted() {
        if (Thread.currentThread().isInterrupted()) throw new CancellationException("Markdown render interrupted");
    }

    /** Escapes visible text incrementally; no escaped copy can grow past the output budget. */
    static void text(MarkdownLimits.Html out, String value) { escape(out, value, false); }

    static void attribute(MarkdownLimits.Html out, String value) { escape(out, value, true); }

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

    /** What both dialects share: the bounded output, the block anchors, heading and footnote names, links and images. */
    static final class Output {
        final MarkdownLimits.Html html = new MarkdownLimits.Html();
        final SyntaxPalette palette;
        final Footnotes footnotes;
        private final String source;
        private final int[] lineStarts;
        private final List<MarkdownResult.BlockAnchor> blocks = new ArrayList<>();
        private final Map<String, Integer> headings = new LinkedHashMap<>();
        private final List<MarkdownResult.Link> links = new ArrayList<>();
        private final List<MarkdownResult.Image> images = new ArrayList<>();
        private final Map<Heading, String> headingIds = new IdentityHashMap<>();
        private boolean anchoring = true;

        Output(String source, SyntaxPalette palette, Node root) {
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
            // Every heading is named before any footnote, so a footnote never renames a heading.
            var ids = new HeadingIds();
            footnotes = new Footnotes(root, heading -> {
                String id = ids.next(visible(heading));
                headingIds.put(heading, id);
                headings.put(id, start(heading));
            });
            footnotes.name(ids);
            for (var note : footnotes.notes()) {
                headings.put(note.id(), start(note.definition()));
                headings.put(note.referenceId(), start(holder(note.first())));
            }
        }

        MarkdownResult result() { return new MarkdownResult(html.toString(), blocks, headings, links, images); }

        String headingId(Heading heading) { return headingIds.get(heading); }

        int link(String destination) {
            int id = links.size();
            links.add(new MarkdownResult.Link(id, destination));
            return id;
        }

        int image(String destination, String alt) {
            int id = images.size();
            images.add(new MarkdownResult.Image(id, destination, alt));
            return id;
        }

        /** Anchors a node's source range; the anchor's index, or -1 when the node is not anchored. */
        int anchor(Node node) {
            var spans = node.getSourceSpans();
            if (spans.isEmpty()) return -1;
            var last = spans.getLast();
            return anchor(spans.getFirst().getInputIndex(), last.getInputIndex() + last.getLength());
        }

        private int anchor(int start, int end) {
            if (!anchoring) return -1;
            if (blocks.size() >= MarkdownLimits.BLOCKS) throw new PreviewLimitException();
            blocks.add(new MarkdownResult.BlockAnchor("b" + blocks.size(), start, end));
            return blocks.size() - 1;
        }

        /**
         * Anchors nothing more. The footnote definitions follow, written at the end but taken from anywhere in the
         * source, and scroll sync needs its anchors in source order.
         */
        void stopAnchoring() { anchoring = false; }

        /** Writes one line of a code block, given its anchor (or -1) and whether a newline follows it. */
        @FunctionalInterface interface CodeLine { void line(String text, int anchor, boolean newline); }

        /** Walks a code block's lines, anchoring each on its content, or on its source line when it is blank. */
        void codeLines(Block node, String literal, boolean fenced, CodeLine write) {
            var spans = node.getSourceSpans();
            int line = 0;
            int spanCursor = 0;
            for (int from = 0; from < literal.length(); line++) {
                checkInterrupted();
                int end = literal.indexOf('\n', from);
                if (end < 0) end = literal.length();
                int sourceLine = spans.getFirst().getLineIndex() + line + (fenced ? 1 : 0);
                while (spanCursor < spans.size() && spans.get(spanCursor).getLineIndex() < sourceLine) spanCursor++;
                SourceSpan span = spanCursor < spans.size() && spans.get(spanCursor).getLineIndex() == sourceLine
                    ? spans.get(spanCursor) : null;
                int anchor = -1;
                if (span != null) {
                    int start = span.getInputIndex();
                    // Source spans include code indentation; map the content rather than the syntactic prefix.
                    int extra = fenced ? ((FencedCodeBlock) node).getFenceIndent() : 4;
                    while (extra > 0 && start < span.getInputIndex() + span.getLength() && source.charAt(start) == ' ') { start++; extra--; }
                    anchor = anchor(start, span.getInputIndex() + span.getLength());
                } else if (sourceLine < lineStarts.length) {
                    // CommonMark omits zero-length source spans. Keep the blank line's source position explicitly.
                    int start = lineStarts[sourceLine];
                    int sourceEnd = sourceLine + 1 < lineStarts.length ? lineStarts[sourceLine + 1] : source.length();
                    while (sourceEnd > start && (source.charAt(sourceEnd - 1) == '\r' || source.charAt(sourceEnd - 1) == '\n')) sourceEnd--;
                    anchor = anchor(start, sourceEnd);
                }
                write.line(literal.substring(from, end), anchor, end < literal.length());
                from = end + 1;
            }
        }

        void text(String value) { MarkdownRenderer.text(html, value); }

        void attribute(String value) { MarkdownRenderer.attribute(html, value); }

        void raw(String literal) {
            // CommonMark keeps a complete inline/block comment as a literal. Drop comments embedded in HTML blocks too.
            int from = 0;
            while (from < literal.length()) {
                int comment = literal.indexOf("<!--", from);
                if (comment < 0) { text(literal.substring(from)); return; }
                text(literal.substring(from, comment));
                int end = literal.indexOf("-->", comment + 4);
                if (end < 0) return;
                from = end + 3;
            }
        }

        static boolean hasNonCommentText(String literal) {
            for (int from = 0; from < literal.length();) {
                if (literal.startsWith("<!--", from)) {
                    int end = literal.indexOf("-->", from + 4);
                    if (end < 0) return false;
                    from = end + 3;
                } else if (!Character.isWhitespace(literal.charAt(from++))) return true;
            }
            return false;
        }

        /** An alert's title as GitHub shows it: Note, Tip, Important, Warning or Caution. */
        static String title(Alert alert) {
            return AlertsExtension.STANDARD_TYPES.getOrDefault(alert.getType(), alert.getType());
        }

        static int start(Node node) {
            var spans = node.getSourceSpans();
            return spans.isEmpty() ? 0 : spans.getFirst().getInputIndex();
        }

        /** The anchored node that holds an inline node: its paragraph or heading, or its table row. */
        static Node holder(Node inline) {
            Node node = inline.getParent();
            while (!(node instanceof Block) && !(node instanceof TableRow)) node = node.getParent();
            return node;
        }

        static String visible(Node parent) {
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

    /** The SWING dialect: the HTML subset {@code PreviewPane.prepare} admits, with an empty named anchor per block. */
    private static final class Swing {
        private final Output out;
        private final MarkdownLimits.Html html;

        Swing(Output out) { this.out = out; html = out.html; }

        void document(Node root) {
            html.append("<html><body>");
            children(root);
            footnotes();
            html.append("</body></html>");
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
                    mark(out.anchor(h));
                    children(h);
                    html.append("</h" + h.getLevel() + ">\n");
                }
                case Paragraph p -> paragraph(p, null);
                case Alert alert -> {
                    // A quote whose first paragraph is the alert's bold title.
                    html.append("<blockquote>"); mark(out.anchor(alert));
                    html.append("<p><b>"); out.text(Output.title(alert)); html.append("</b></p>\n");
                    children(alert); html.append("</blockquote>\n");
                }
                case BlockQuote q -> container("blockquote", q);
                case BulletList list -> container("ul", list);
                case OrderedList list -> {
                    html.append("<ol start=\"" + list.getMarkerStartNumber() + "\">");
                    children(list); html.append("</ol>\n");
                }
                case ListItem item -> container("li", item);
                case FencedCodeBlock code -> code(code, code.getLiteral(), code.getInfo(), true);
                case IndentedCodeBlock code -> code(code, code.getLiteral(), "", false);
                case ThematicBreak rule -> { mark(out.anchor(rule)); html.append("<hr>\n"); }
                case HtmlBlock raw -> {
                    if (Output.hasNonCommentText(raw.getLiteral())) {
                        html.append("<p>"); mark(out.anchor(raw)); out.raw(raw.getLiteral()); html.append("</p>\n");
                    }
                }
                case HtmlInline raw -> out.raw(raw.getLiteral());
                case Text text -> out.text(text.getLiteral());
                case Code code -> { html.append("<code>"); out.text(code.getLiteral()); html.append("</code>"); }
                case Emphasis em -> container("em", em);
                case StrongEmphasis strong -> container("strong", strong);
                case Strikethrough strike -> container("strike", strike);
                case HardLineBreak ignored -> html.append("<br>\n");
                case SoftLineBreak ignored -> html.append("\n");
                case Link link -> {
                    html.append("<a href=\"jasper-link:" + out.link(link.getDestination()) + "\">"); children(link); html.append("</a>");
                }
                case Image image -> {
                    String alt = Output.visible(image);
                    html.append("<img src=\"jasper-image:" + out.image(image.getDestination(), alt) + "\" alt=\"");
                    out.attribute(alt); html.append("\">");
                }
                case FootnoteReference reference -> {
                    var note = out.footnotes.note(reference);
                    if (reference == note.first()) name(note.referenceId());
                    html.append("<a href=\"jasper-link:" + out.link("#" + note.id()) + "\">[" + note.number() + "]</a>");
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
                    if (cell.getPrevious() == null) mark(out.anchor(cell.getParent()));
                    children(cell); html.append("</" + tag + ">");
                }
                case TaskListItemMarker ignored -> { }
                case LinkReferenceDefinition ignored -> { }
                case FootnoteDefinition ignored -> { }
                default -> children(node);
            }
        }

        void container(String tag, Node node) {
            html.append("<" + tag + ">");
            if (node instanceof Block && !(node instanceof BulletList) && !(node instanceof TableBlock)) mark(out.anchor(node));
            children(node);
            html.append("</" + tag + ">" + (node instanceof Block ? "\n" : ""));
        }

        /** A paragraph, ending with a footnote's back reference when it is the last of the definition being written. */
        void paragraph(Paragraph p, Footnotes.Note backReference) {
            html.append("<p>");
            mark(out.anchor(p));
            if (p.getPrevious() instanceof TaskListItemMarker task) html.append(task.isChecked() ? "\u2611 " : "\u2610 ");
            children(p);
            if (backReference != null) backReference(backReference);
            html.append("</p>\n");
        }

        void code(Block node, String literal, String info, boolean fenced) {
            html.append("<pre>"); mark(out.anchor(node));
            var highlighter = new CodeHighlighter(info == null ? "" : info, out.palette, Dialect.SWING);
            out.codeLines(node, literal, fenced, (line, anchor, newline) -> {
                mark(anchor);
                highlighter.line(line, html);
                if (newline) html.append("\n");
            });
            html.append("</pre>\n");
        }

        /** After a rule, the definitions as a numbered list, each named for its fragment. */
        void footnotes() {
            var notes = out.footnotes.notes();
            if (notes.isEmpty()) return;
            out.stopAnchoring();
            html.append("<hr>\n<ol>\n");
            for (var note : notes) {
                html.append("<li>");
                name(note.id());
                Node last = note.definition().getLastChild();
                for (Node node = note.definition().getFirstChild(); node != null; node = node.getNext()) {
                    if (node == last && node instanceof Paragraph p) { checkInterrupted(); paragraph(p, note); }
                    else node(node);
                }
                if (!(last instanceof Paragraph)) backReference(note);
                html.append("</li>\n");
            }
            html.append("</ol>\n");
        }

        void backReference(Footnotes.Note note) {
            html.append(" <a href=\"jasper-link:" + out.link("#" + note.referenceId()) + "\">\u21a9</a>");
        }

        /** The block anchor at an index the output gave, if it gave one. */
        void mark(int anchor) {
            if (anchor >= 0) html.append("<a name=\"b" + anchor + "\"></a>");
        }

        /** A fragment's named marker. It is not a block anchor, so scroll sync never sees it. */
        void name(String fragment) {
            html.append("<a name=\""); out.attribute(fragment); html.append("\"></a>");
        }
    }
}
