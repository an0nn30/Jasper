package dev.jasper.editor.vim;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

/**
 * The highlightedyank extension (spec 8), ported from Machakann's vim-highlightedyank (its TextYankPost path): the
 * text a yank took is drawn for {@code g:highlightedyank_highlight_duration} milliseconds (1000; 0 for never, below
 * 0 until something else clears it). As in the plugin, the highlight is drawn once the command has finished (only
 * the last yank of a macro, and only if the text has not changed since), a yank from Visual mode is drawn unless
 * {@code g:highlightedyank_highlight_in_visual} is 0, one of more than {@code g:highlightedyank_max_lines} lines
 * (10000) is not, and a change to the text, Insert mode or the next yank clears it. The colour is IdeaVim's
 * {@code g:highlightedyank_highlight_color} ({@code rgba(r, g, b, a)}), else the incremental-search match's.
 */
final class HighlightedYank {
    private static final String OPTION = "highlightedyank";
    private static final Pattern RGBA = Pattern.compile("rgba\\(\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*,\\s*(\\d+)\\s*\\)");

    /** A yank to draw: whether it was from Visual mode, what it took, and the text's version then (b:changedtick). */
    private record Yank(boolean visual, Registers.Content content, int version) { }

    private final VimEditor editor;
    private Yank pending;
    private boolean scheduled;
    private List<int[]> shown = List.of();
    private int shownVersion;
    private Timer timer;

    HighlightedYank(VimEditor editor) { this.editor = editor; }

    /** highlightedyank#debounce: a yank ended; it is drawn when the command has finished, the last one only. */
    static void yanked(Context c, boolean visual, Registers.Content content) {
        if (!c.vim.extensions().on(OPTION)) return;
        if (visual && number(c.vim, "highlightedyank_highlight_in_visual", 1) == 0) return;
        c.editor.highlightedYank().debounce(visual, content);
    }

    private void debounce(boolean visual, Registers.Content content) {
        pending = new Yank(visual, content, editor.textVersion());
        if (scheduled) return;
        scheduled = true;
        SwingUtilities.invokeLater(this::highlight);
    }

    /** s:highlight: the region from the marks and the register's text. */
    private void highlight() {
        scheduled = false;
        Yank yank = pending;
        pending = null;
        if (yank == null || yank.version() != editor.textVersion() || editor.area().vim().orElse(null) != editor) return;
        Marks marks = editor.marks();
        Pos start = yank.visual() ? MarkCommands.get(editor, '<') : marks.get('[');
        Pos end = yank.visual() ? MarkCommands.get(editor, '>') : marks.get(']');
        if (start == null || end == null) return;
        List<int[]> ranges = region(start, end, yank.content());
        int lines = yank.content().type() == Registers.Type.LINE ? end.line - start.line + 1 : yank.content().lines().size();
        if (ranges.isEmpty() || lines > number(editor.vim(), "highlightedyank_max_lines", 10000)) return;
        long duration = number(editor.vim(), "highlightedyank_highlight_duration", 1000);
        if (duration == 0) return;
        clear();
        shown = ranges;
        shownVersion = yank.version();
        editor.refreshHighlights();
        if (duration > 0) {
            timer = new Timer((int) Math.min(duration, Integer.MAX_VALUE), e -> clearAndDraw());
            timer.setRepeats(false);
            timer.start();
        }
    }

    /** @return the highlight as {kind 2, start, end} ranges; a change to the text or Insert mode clears it first */
    List<int[]> ranges() {
        if (!shown.isEmpty() && (shownVersion != editor.textVersion() || editor.mode().inserting())) clear();
        return shown;
    }

    /** @return the highlight's colour, or null for the incremental-search match's */
    Color color() {
        Object value = editor.vim().variables().get("highlightedyank_highlight_color");
        Matcher m = value == null ? null : RGBA.matcher(value.toString().strip());
        if (m == null || !m.matches()) return null;
        int[] v = new int[4];
        for (int i = 0; i < 4; i++) v[i] = Math.min(255, Integer.parseInt(m.group(i + 1)));
        return new Color(v[0], v[1], v[2], v[3]);
    }

    private void clear() {
        if (timer != null) timer.stop();
        timer = null;
        shown = List.of();
    }

    private void clearAndDraw() {
        clear();
        if (editor.area().vim().orElse(null) == editor) editor.refreshHighlights();
    }

    /** Vim is off: the highlight and its timer go. */
    void close() { clear(); }

    /** The timer runs out now, for tests. */
    void expireNow() { if (timer != null) clearAndDraw(); }

    /** s:get_region: charwise from the start as far as the register's text reaches, whole lines, or a block. */
    private List<int[]> region(Pos start, Pos end, Registers.Content content) {
        Buffer b = editor.buffer();
        List<int[]> out = new ArrayList<>();
        List<String> lines = content.lines();
        if (lines.isEmpty() || start.line >= b.count()) return out;
        switch (content.type()) {
            case LINE -> out.add(new int[] { 2, b.start(start.line), Math.min(b.length(), b.end(Math.min(end.line, b.count() - 1)) + 1) });
            case CHAR -> {
                int len = lines.size(), endLine = start.line, endCol;
                if (len == 1) endCol = start.col + lines.getFirst().length() - 1;
                else if (len == 2 && lines.get(1).isEmpty()) endCol = start.col + lines.getFirst().length();
                else if (lines.getLast().isEmpty()) {
                    endLine += len - 2;
                    endCol = lines.get(len - 2).length() - 1;
                } else {
                    endLine += len - 1;
                    endCol = lines.getLast().length() - 1;
                }
                if (endLine >= b.count()) return out;
                String last = b.line(endLine);
                int from = b.offset(start.line, Math.min(start.col, b.line(start.line).length()));
                if (endLine == start.line && endCol < start.col) return out;
                int to = endCol < last.length() ? b.offset(endLine, Text.next(last, Math.max(0, endCol))) : Math.min(b.length(), b.end(endLine) + 1);
                out.add(new int[] { 2, from, to });
            }
            case BLOCK -> {
                int ts = editor.options().number("tabstop");
                int width = 0;
                for (String line : lines) width = Math.max(width, Text.virtcol(line, line.length(), ts));
                int left = Visual.startVcol(b, start, ts), right = left + width - 1;
                for (int line = start.line; line < Math.min(b.count(), start.line + lines.size()); line++) {
                    String text = b.line(line);
                    Block bd = Block.prep(text, left, right, ts, true, Block.Op.OTHER, true);
                    if (bd.isShort && bd.textlen == 0) continue;
                    int from = b.offset(line, Math.min(bd.textcol, text.length()));
                    int until = b.offset(line, Math.min(text.length(), bd.textcol + bd.textlen));
                    if (until > from) out.add(new int[] { 2, from, until });
                }
            }
        }
        out.removeIf(range -> range[2] <= range[1]);
        return out;
    }

    /** @return a {@code g:} number, or the default when it is not set or not a number */
    private static long number(Vim vim, String name, long fallback) {
        Object value = vim.variables().get(name);
        if (value == null) return fallback;
        try { return Long.parseLong(value.toString().strip()); }
        catch (NumberFormatException notNumber) { return fallback; }
    }
}
