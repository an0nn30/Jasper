package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;

/**
 * One editor's marks (spec 3.6): {@code a}–{@code z}, the special marks, the Visual area for {@code gv}, the
 * jumplist and the change list. They follow the text as Vim's mark_adjust and mark_col_adjust move them (mark.c):
 * by line, not by character. Adding or removing lines moves the marks below; deleting a line deletes the
 * lowercase, {@code '.} and {@code '^} marks on it, while the others (Vim's one_adjust_nodel) move to the first
 * line after the deletion. Joining and splitting lines move the marks on the joined or split part. Edits inside a
 * line leave columns alone, as in Vim.
 *
 * <p>The table keeps the positions of the text's line breaks so that a removal, which a document event reports only
 * after the text is gone, can still be read as lines and columns.
 */
final class Marks {
    /** Marks a line deletion removes (one_adjust) rather than moves (one_adjust_nodel). */
    private static final String DELETABLE = "abcdefghijklmnopqrstuvwxyz.^\"";

    private final Document document;
    private final Map<Character, Pos> marks = new HashMap<>();
    private final List<Pos> jumps = new ArrayList<>();
    private final List<Pos> changes = new ArrayList<>();
    private final List<Pos> extra = new ArrayList<>();
    private final DocumentListener listener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { inserted(event.getOffset(), event.getLength()); }
        @Override public void removeUpdate(DocumentEvent event) { removed(event.getOffset(), event.getLength()); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };
    /** Offsets of the line breaks, ascending. */
    private int[] breaks = new int[0];
    private int breakCount;
    /** The jumplist index: jumps.size() when not moving through the list (Vim's w_jumplistidx). */
    int jumpIndex;
    /** The change-list index (Vim's w_changelistidx). */
    int changeIndex;
    /** The mode of the last Visual area, for {@code gv}. */
    Mode visualMode;
    /** The remembered column of the last Visual area. */
    int visualCurswant = -1;
    /** The previous-context mark ({@code ''}) and the one before it (Vim's w_pcmark, w_prev_pcmark); null when unset. */
    private Pos pcmark, prevPcmark;
    /** Whether the next change starts a new change-list entry (Vim's b_new_change, set when an undo step begins). */
    private boolean newChange = true;
    /** Set while a join removes a line break, so a blank joined line's marks move rather than go (do_join). */
    private boolean joining;
    /** The jumplist's and change list's size limit (Vim's JUMPLISTSIZE). */
    static final int LIST_SIZE = 100;

    Marks(Document document) {
        this.document = document;
        pcmark = new Pos(0, 0);
        rebuild();
        document.addDocumentListener(listener);
    }

    void close() { document.removeDocumentListener(listener); }

    /**
     * @param name a mark name: {@code a}–{@code z}, or one of {@code [ ] < > ^ . " '}
     * @return the mark, or null when it is not set
     */
    Pos get(char name) {
        Pos pos = marks.get(name);
        return pos == null ? null : pos.copy();
    }

    /** Sets a mark to a copy of the position; null clears it. */
    void set(char name, Pos pos) {
        if (pos == null) marks.remove(name);
        else marks.put(name, pos.copy());
    }

    /** Clears every mark and list, after the text was replaced. */
    void clear() {
        marks.clear();
        jumps.clear();
        changes.clear();
        jumpIndex = changeIndex = 0;
        visualMode = null;
        pcmark = new Pos(0, 0);
        prevPcmark = null;
        newChange = true;
        rebuild();
    }

    /** @return the previous-context mark, or null */
    Pos pcmark() { return pcmark == null ? null : pcmark.copy(); }

    /** setpcmark: the cursor becomes the previous context and a new jumplist entry. */
    void setpcmark(Pos cursor) {
        prevPcmark = pcmark;
        pcmark = cursor.copy();
        jumps.add(cursor.copy());
        if (jumps.size() > LIST_SIZE) jumps.removeFirst();
        jumpIndex = jumps.size();
    }

    /** m' and m`: like setpcmark, but the mark stays even when the cursor does not move. */
    void setpcmarkKeep(Pos cursor) {
        setpcmark(cursor);
        prevPcmark = pcmark.copy();
    }

    /** checkpcmark: after a jump that stayed on the same position, the previous context is kept. */
    void checkpcmark(Pos cursor) {
        if (prevPcmark != null && (pcmark == null || pcmark.equals(cursor))) pcmark = prevPcmark;
        prevPcmark = null;
    }

    /**
     * movemark: {@code count} entries along the jumplist (negative for {@code <C-o>}); the first move from the end
     * adds the cursor. Adjacent entries on the same line are merged first (cleanup_jumplist).
     *
     * @return the entry, or null when the list does not reach that far
     */
    Pos movemark(int count, Pos cursor) {
        cleanupJumps();
        if (jumps.isEmpty()) return null;
        if (jumpIndex + count < 0 || jumpIndex + count >= jumps.size()) return null;
        if (jumpIndex == jumps.size()) {
            setpcmark(cursor);
            jumpIndex--;
            if (jumpIndex + count < 0) return null;
        }
        jumpIndex += count;
        return jumps.get(jumpIndex).copy();
    }

    /** cleanup_jumplist: an entry with a later entry on the same line goes. */
    private void cleanupJumps() {
        int to = 0;
        List<Pos> kept = new ArrayList<>();
        for (int from = 0; from < jumps.size(); from++) {
            if (jumpIndex == from) jumpIndex = to;
            boolean duplicate = false;
            for (int i = from + 1; i < jumps.size(); i++) {
                if (jumps.get(i).line == jumps.get(from).line) {
                    duplicate = true;
                    break;
                }
            }
            if (!duplicate) {
                kept.add(jumps.get(from));
                to++;
            }
        }
        if (jumpIndex == jumps.size()) jumpIndex = to;
        jumps.clear();
        jumps.addAll(kept);
    }

    /**
     * movechangelist: {@code count} entries along the change list (negative for {@code g;}), stopping at its ends.
     *
     * @return the entry, or null when already at that end
     */
    Pos movechangelist(int count) {
        if (changes.isEmpty()) return null;
        int n = changeIndex;
        if (n + count < 0) {
            if (n == 0) return null;
            n = 0;
        } else if (n + count >= changes.size()) {
            if (n == changes.size() - 1) return null;
            n = changes.size() - 1;
        } else n += count;
        changeIndex = n;
        return changes.get(n).copy();
    }

    /** Runs a removal of line breaks that joins lines: marks on a joined line move onto the line above. */
    void joining(Runnable removal) {
        joining = true;
        try { removal.run(); }
        finally { joining = false; }
    }

    /** A new undo step began: its first change may start a new change-list entry (u_savecommon's b_new_change). */
    void newChange() { newChange = true; }

    /**
     * changed_common: the {@code '.} mark moves to the change, and the change list gains an entry for the first change
     * of an undo step unless it is on the same line as the last entry and within 79 columns of it.
     */
    private void changed(int line, int col) {
        Pos at = new Pos(line, col);
        marks.put('.', at.copy());
        if (!newChange && !changes.isEmpty()) return;
        boolean add;
        if (changes.isEmpty()) add = true;
        else {
            Pos last = changes.getLast();
            add = last.line != line || last.col + 79 < col || col + 79 < last.col;
        }
        if (add) {
            newChange = false;
            if (changes.size() == LIST_SIZE) changes.removeFirst();
            changes.add(at.copy());
        } else changes.set(changes.size() - 1, at.copy());
        changeIndex = changes.size();
    }

    List<Pos> jumps() { return jumps; }

    List<Pos> changes() { return changes; }

    /** Positions owned elsewhere (uppercase marks in this document) that move with the text too. */
    void track(Pos pos) { extra.add(pos); }

    void untrack(Pos pos) { extra.removeIf(p -> p == pos); }

    private void inserted(int offset, int length) {
        String text;
        try { text = document.getText(offset, length); }
        catch (BadLocationException impossible) { rebuild(); return; }
        int line = lineBefore(offset), col = offset - lineStart(line);
        int newlines = 0, last = -1;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') { newlines++; last = i; }
        int lineLength = lineLength(line);
        shiftBreaks(offset, length, text);
        if (newlines > 0) {
            int tail = text.length() - last - 1;
            if (col == 0 && text.endsWith("\n")) adjust(line, Integer.MAX_VALUE, newlines, 0);
            else {
                adjust(line + 1, Integer.MAX_VALUE, newlines, 0);
                for (Pos pos : all()) if (pos.line == line && pos.col >= col) {
                    pos.line += newlines;
                    pos.col = pos.col - col + tail;
                }
            }
        }
        // Lines added after a line's end are a change at the first new line (appended_lines_mark).
        if (newlines > 0 && col == lineLength && col > 0 && text.startsWith("\n")) changed(line + 1, 0);
        else changed(line, col);
    }

    private void removed(int offset, int length) {
        int line1 = lineBefore(offset), col1 = offset - lineStart(line1);
        int line2 = lineBefore(offset + length), col2 = offset + length - lineStart(line2);
        int len1 = lineLength(line1), len2 = lineLength(line2);
        dropBreaks(offset, length);
        int lines = line2 - line1;
        if (lines > 0) {
            if (col1 == 0 && col2 == 0 && !joining) {
                delete(line1, line2 - 1);
                adjust(line2, Integer.MAX_VALUE, -lines, 0);
            } else if (col1 == len1 && col2 == len2 && !joining) {
                delete(line1 + 1, line2);
                adjust(line2 + 1, Integer.MAX_VALUE, -lines, 0);
            } else {
                delete(line1 + 1, line2 - 1);
                for (Pos pos : all()) {
                    if (pos.line == line2) {
                        pos.line = line1;
                        pos.col = Math.max(0, pos.col - col2 + col1);
                    } else if (pos.line > line2) pos.line -= lines;
                }
            }
        }
        // Whole lines deleted after a line's end (the last lines) are a change at the first deleted line (del_lines).
        if (lines > 0 && col1 == len1 && col2 == len2 && col1 > 0 && !joining) changed(line1 + 1, 0);
        else changed(line1, col1);
    }

    /** mark_adjust with an amount: lines {@code from}..{@code to} move by {@code amount}. */
    private void adjust(int from, int to, int amount, int amountAfter) {
        for (Pos pos : all()) {
            if (pos.line >= from && pos.line <= to) pos.line += amount;
            else if (amountAfter != 0 && pos.line > to) pos.line += amountAfter;
        }
    }

    /** mark_adjust with MAXLNUM: marks on deleted lines {@code from}..{@code to} are deleted or moved to {@code from}. */
    private void delete(int from, int to) {
        if (to < from) return;
        marks.entrySet().removeIf(entry -> DELETABLE.indexOf(entry.getKey()) >= 0 && entry.getValue().line >= from && entry.getValue().line <= to);
        for (Pos pos : all()) if (pos.line >= from && pos.line <= to) {
            pos.line = from;
            pos.col = 0;
        }
    }

    private List<Pos> all() {
        List<Pos> all = new ArrayList<>(marks.values());
        if (pcmark != null) all.add(pcmark);
        if (prevPcmark != null) all.add(prevPcmark);
        all.addAll(jumps);
        all.addAll(changes);
        all.addAll(extra);
        return all;
    }

    /** @return the line holding {@code offset}, by the break table as it was before the current change */
    private int lineBefore(int offset) {
        int found = Arrays.binarySearch(breaks, 0, breakCount, offset);
        return found >= 0 ? found : -found - 1;
    }

    private int lineStart(int line) { return line == 0 ? 0 : breaks[line - 1] + 1; }

    private int lineLength(int line) {
        int end = line < breakCount ? breaks[line] : documentLengthBefore;
        return end - lineStart(line);
    }

    private int documentLengthBefore;

    private void shiftBreaks(int offset, int length, String text) {
        int at = lineBefore(offset);
        int added = 0;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') added++;
        int[] next = new int[Math.max(16, breakCount + added)];
        System.arraycopy(breaks, 0, next, 0, at);
        int n = at;
        for (int i = 0; i < text.length(); i++) if (text.charAt(i) == '\n') next[n++] = offset + i;
        for (int i = at; i < breakCount; i++) next[n++] = breaks[i] + length;
        breaks = next;
        breakCount = n;
        documentLengthBefore = document.getLength();
    }

    private void dropBreaks(int offset, int length) {
        int n = 0;
        for (int i = 0; i < breakCount; i++) {
            int b = breaks[i];
            if (b < offset) breaks[n++] = b;
            else if (b >= offset + length) breaks[n++] = b - length;
        }
        breakCount = n;
        documentLengthBefore = document.getLength();
    }

    private void rebuild() {
        String text;
        try { text = document.getText(0, document.getLength()); }
        catch (BadLocationException impossible) { text = ""; }
        breaks = new int[Math.max(16, text.length() / 20)];
        breakCount = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) != '\n') continue;
            if (breakCount == breaks.length) breaks = Arrays.copyOf(breaks, breaks.length * 2);
            breaks[breakCount++] = i;
        }
        documentLengthBefore = text.length();
    }
}
