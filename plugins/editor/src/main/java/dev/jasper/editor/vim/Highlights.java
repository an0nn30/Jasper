package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.text.BadLocationException;
import javax.swing.text.Highlighter;
import org.fife.ui.rtextarea.SmartHighlightPainter;

/**
 * What Vim draws on the text (spec 5.1): the Visual area (charwise, linewise or blockwise) in the theme's selection
 * colour, 'hlsearch' matches in its mark-all colour, and the incremental-search match in the selection colour. The
 * text area's own selection stays empty in Vim mode, so these do not move the caret.
 */
final class Highlights {
    private final VimTextArea area;
    private final List<Object> tags = new ArrayList<>();
    private List<int[]> shown = List.of();
    private java.awt.Color yankColor;

    Highlights(VimTextArea area) { this.area = area; }

    /**
     * Draws the ranges ({@code [kind, start, end)} with kind 0 for Visual and incsearch, 1 for 'hlsearch' and 2 for
     * a yank's highlight, in {@code yank} or else the selection colour).
     */
    void show(List<int[]> ranges, java.awt.Color yank) {
        if (same(ranges) && java.util.Objects.equals(yank, yankColor)) return;
        yankColor = yank;
        clear();
        Highlighter highlighter = area.getHighlighter();
        if (highlighter == null) return;
        var selection = new SmartHighlightPainter(area.getSelectionColor());
        var search = new SmartHighlightPainter(area.getMarkAllHighlightColor());
        var yanked = yank == null ? selection : new SmartHighlightPainter(yank);
        int length = area.getDocument().getLength();
        for (int[] range : ranges) {
            int start = Math.min(range[1], length), end = Math.min(range[2], length);
            if (end <= start) continue;
            try { tags.add(highlighter.addHighlight(start, end, range[0] == 0 ? selection : range[0] == 1 ? search : yanked)); }
            catch (BadLocationException ignored) { }
        }
        shown = ranges;
    }

    private boolean same(List<int[]> ranges) {
        if (ranges.size() != shown.size()) return false;
        for (int i = 0; i < ranges.size(); i++) if (!java.util.Arrays.equals(ranges.get(i), shown.get(i))) return false;
        return true;
    }

    void clear() {
        Highlighter highlighter = area.getHighlighter();
        if (highlighter != null) for (Object tag : tags) highlighter.removeHighlight(tag);
        tags.clear();
        shown = List.of();
    }

    /** @return the drawn ranges, for tests */
    List<int[]> shown() { return shown; }

    /**
     * The Visual area as text ranges: a charwise area includes the character under its end (and the line break
     * when that end is past the last character); a linewise area whole lines; a block each line's part.
     */
    static List<int[]> visual(VimEditor editor) {
        List<int[]> out = new ArrayList<>();
        Pos anchor = editor.visualAnchor();
        Mode mode = editor.mode();
        if (anchor == null || !mode.visual()) return out;
        Buffer b = editor.buffer();
        Pos cursor = editor.cursor();
        Pos start = Pos.min(anchor, cursor), end = Pos.max(anchor, cursor);
        switch (mode) {
            case VISUAL -> {
                String last = b.line(end.line);
                int to = end.col < last.length() ? b.offset(end.line, Text.next(last, end.col)) : b.end(end.line) + 1;
                out.add(new int[] { 0, b.offset(start), to });
            }
            case VISUAL_LINE -> out.add(new int[] { 0, b.start(start.line), b.end(end.line) + 1 });
            default -> {
                int ts = editor.options().number("tabstop");
                int left = Math.min(Visual.startVcol(b, anchor, ts), Visual.startVcol(b, cursor, ts));
                int right = Math.max(Visual.endVcol(b, anchor, ts), Visual.endVcol(b, cursor, ts));
                boolean max = editor.curswant() == Visual.MAX;
                for (int line = start.line; line <= end.line; line++) {
                    String text = b.line(line);
                    int to = max ? Integer.MAX_VALUE / 2 : right;
                    Block bd = Block.prep(text, left, to, ts, true, Block.Op.OTHER, true);
                    if (bd.isShort && bd.textlen == 0) continue;
                    int from = b.offset(line, Math.min(bd.textcol, text.length()));
                    int until = b.offset(line, Math.min(text.length(), bd.textcol + bd.textlen));
                    if (until > from) out.add(new int[] { 0, from, until });
                }
            }
        }
        return out;
    }
}
