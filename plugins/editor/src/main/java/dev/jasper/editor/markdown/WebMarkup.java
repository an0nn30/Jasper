package dev.jasper.editor.markdown;

import java.util.Locale;
import org.commonmark.ext.footnotes.FootnoteDefinition;
import org.commonmark.ext.footnotes.FootnoteReference;
import org.commonmark.ext.gfm.alerts.Alert;
import org.commonmark.ext.gfm.strikethrough.Strikethrough;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TableRow;
import org.commonmark.ext.task.list.items.TaskListItemMarker;
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
import org.commonmark.node.StrongEmphasis;
import org.commonmark.node.Text;
import org.commonmark.node.ThematicBreak;

/**
 * The WEB dialect: a body fragment in GitHub's markup, in the vocabulary the Chromium preview's script and stylesheet
 * select on. A block's anchor is {@code data-b="<n>"} on its element, {@code <n>} being the index of anchor
 * {@code b<n>}; a link is {@code jasper-link:<id>} with {@code data-link}, and an image carries only its
 * {@code data-image} id and its alternative text. Raw HTML stays text. No attribute is written but {@code id},
 * {@code class}, {@code data-b}, {@code data-link}, {@code data-image}, {@code href}, {@code alt}, {@code start},
 * {@code type}, {@code disabled}, {@code checked} and {@code aria-label}.
 */
final class WebMarkup {
    private final MarkdownRenderer.Output out;
    private final MarkdownLimits.Html html;

    WebMarkup(MarkdownRenderer.Output out) { this.out = out; html = out.html; }

    void document(Node root) {
        children(root);
        footnotes();
    }

    private void children(Node parent) {
        for (Node node = parent.getFirstChild(); node != null; node = node.getNext()) node(node);
    }

    // This exhaustive vocabulary is the tag allowlist, and it anchors the nodes the SWING dialect anchors, in its order,
    // but for a mermaid fence, which it anchors once, where SWING anchors the fence and each of its lines.
    private void node(Node node) {
        if (node instanceof Block || node instanceof TableRow) MarkdownRenderer.checkInterrupted();
        switch (node) {
            case Heading h -> {
                html.append("<h" + h.getLevel() + " id=\""); out.attribute(out.headingId(h));
                html.append("\"" + anchor(h) + ">"); children(h); html.append("</h" + h.getLevel() + ">\n");
            }
            case Paragraph p -> paragraph(p, null);
            case Alert alert -> {
                html.append("<div class=\"markdown-alert markdown-alert-"); out.attribute(alert.getType().toLowerCase(Locale.ROOT));
                html.append("\"" + anchor(alert) + "><p class=\"markdown-alert-title\">");
                out.text(MarkdownRenderer.Output.title(alert)); html.append("</p>");
                children(alert); html.append("</div>\n");
            }
            case BlockQuote quote -> { html.append("<blockquote" + anchor(quote) + ">"); children(quote); html.append("</blockquote>\n"); }
            case BulletList list -> { html.append("<ul>"); children(list); html.append("</ul>\n"); }
            case OrderedList list -> {
                html.append("<ol start=\"" + list.getMarkerStartNumber() + "\">"); children(list); html.append("</ol>\n");
            }
            case ListItem item -> {
                html.append(item.getFirstChild() instanceof TaskListItemMarker ? "<li class=\"task-list-item\"" : "<li");
                html.append(anchor(item) + ">"); children(item); html.append("</li>\n");
            }
            case FencedCodeBlock code when CodeHighlighter.mermaid(code.getInfo()) -> mermaid(code);
            case FencedCodeBlock code -> code(code, code.getLiteral(), code.getInfo(), true);
            case IndentedCodeBlock code -> code(code, code.getLiteral(), "", false);
            case ThematicBreak rule -> html.append("<hr" + anchor(rule) + ">\n");
            case HtmlBlock raw -> {
                if (MarkdownRenderer.Output.hasNonCommentText(raw.getLiteral())) {
                    html.append("<p" + anchor(raw) + ">"); out.raw(raw.getLiteral()); html.append("</p>\n");
                }
            }
            case HtmlInline raw -> out.raw(raw.getLiteral());
            case Text text -> out.text(text.getLiteral());
            case Code code -> { html.append("<code>"); out.text(code.getLiteral()); html.append("</code>"); }
            case Emphasis em -> inline("em", em);
            case StrongEmphasis strong -> inline("strong", strong);
            case Strikethrough strike -> inline("del", strike);
            case HardLineBreak ignored -> html.append("<br>\n");
            case SoftLineBreak ignored -> html.append("\n");
            case Link link -> { link(out.link(link.getDestination())); html.append(">"); children(link); html.append("</a>"); }
            case Image image -> {
                String alt = out.visible(image);
                html.append("<img data-image=\"" + out.image(image.getDestination(), alt) + "\" alt=\"");
                out.attribute(alt); html.append("\">");
            }
            case FootnoteReference reference -> {
                var note = out.footnotes.note(reference);
                if (note == null) out.text(MarkdownRenderer.Output.literal(reference));
                else footnoteReference(reference, note);
            }
            case TableBlock table -> { html.append("<table>\n"); children(table); html.append("</table>\n"); }
            case TableHead head -> children(head);
            case TableBody body -> children(body);
            case TableRow row -> { html.append("<tr" + anchor(row) + ">"); children(row); html.append("</tr>\n"); }
            case TableCell cell -> {
                String tag = cell.isHeader() ? "th" : "td";
                html.append("<" + tag);
                if (cell.getAlignment() != null) html.append(" class=\"align-" + cell.getAlignment().name().toLowerCase(Locale.ROOT) + "\"");
                html.append(">"); children(cell); html.append("</" + tag + ">");
            }
            case TaskListItemMarker ignored -> { }
            case LinkReferenceDefinition ignored -> { }
            case FootnoteDefinition ignored -> { }
            default -> children(node);
        }
    }

    /** A reference as GitHub's raised number, the first of its note's carrying the id its back reference goes to. */
    private void footnoteReference(FootnoteReference reference, Footnotes.Note note) {
        html.append("<sup class=\"footnote-ref\">");
        link(out.link("#" + note.id()));
        if (reference == note.first()) { html.append(" id=\""); out.attribute(note.referenceId()); html.append("\""); }
        html.append(">" + note.number() + "</a></sup>");
    }

    private void inline(String tag, Node node) {
        html.append("<" + tag + ">"); children(node); html.append("</" + tag + ">");
    }

    /** A paragraph, ending with a footnote's back reference when it is the last of the definition being written. */
    private void paragraph(Paragraph p, Footnotes.Note backReference) {
        html.append("<p" + anchor(p) + ">");
        if (p.getPrevious() instanceof TaskListItemMarker task)
            html.append(task.isChecked() ? "<input type=\"checkbox\" disabled checked> " : "<input type=\"checkbox\" disabled> ");
        children(p);
        if (backReference != null) backReference(backReference);
        html.append("</p>\n");
    }

    /** Each line is a span anchored as SWING anchors it, its tokens classes the stylesheet colours. */
    private void code(Block node, String literal, String info, boolean fenced) {
        html.append("<pre" + anchor(node) + "><code>");
        var highlighter = new CodeHighlighter(info == null ? "" : info, out.palette, MarkdownRenderer.Dialect.WEB);
        out.codeLines(node, literal, fenced, (line, anchor, newline) -> {
            html.append("<span class=\"line\"" + anchored(anchor) + ">");
            highlighter.line(line, html);
            html.append(newline ? "</span>\n" : "</span>");
        });
        html.append("</code></pre>\n");
    }

    /** The diagram's source as text for Mermaid to draw, under one anchor. */
    private void mermaid(FencedCodeBlock code) {
        String literal = code.getLiteral();
        html.append("<pre class=\"mermaid\"" + anchor(code) + ">");
        out.text(literal.endsWith("\n") ? literal.substring(0, literal.length() - 1) : literal);
        html.append("</pre>\n");
    }

    /** The definitions in number order, after the document, as GitHub's footnotes section. */
    private void footnotes() {
        var notes = out.footnotes.notes();
        if (notes.isEmpty()) return;
        out.stopAnchoring();
        html.append("<section class=\"footnotes\"><ol>\n");
        for (var note : notes) {
            html.append("<li id=\""); out.attribute(note.id()); html.append("\">");
            Node last = note.definition().getLastChild();
            for (Node node = note.definition().getFirstChild(); node != null; node = node.getNext()) {
                if (node == last && node instanceof Paragraph p) { MarkdownRenderer.checkInterrupted(); paragraph(p, note); }
                else node(node);
            }
            if (!(last instanceof Paragraph)) backReference(note);
            html.append("</li>\n");
        }
        html.append("</ol></section>\n");
    }

    private void backReference(Footnotes.Note note) {
        html.append(" ");
        link(out.link("#" + note.referenceId()));
        html.append(" class=\"footnote-backref\" aria-label=\"Back to reference " + note.number() + "\">\u21a9</a>");
    }

    /** Opens a link to a private id; the caller closes the tag. */
    private void link(int id) {
        html.append("<a href=\"jasper-link:" + id + "\" data-link=\"" + id + "\"");
    }

    private String anchor(Node node) { return anchored(out.anchor(node)); }

    /** The {@code data-b} attribute of an anchor the output gave, if it gave one. */
    private static String anchored(int anchor) { return anchor < 0 ? "" : " data-b=\"" + anchor + "\""; }
}
