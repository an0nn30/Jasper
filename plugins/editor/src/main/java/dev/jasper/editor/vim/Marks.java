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
 * <p>As in Vim, the named marks, the Visual area and the change list belong to the buffer: every editor of one
 * document, in each of its views, shares them (pane flexibility spec 6), in one store the document holds, which follows
 * every edit to the document, whichever view made it. The jumplist, the previous-context mark and the place in the
 * change list are each editor's own, as they are a Vim window's; the editor of the view that edits now is Vim's
 * current window.
 *
 * <p>The table keeps the positions of the text's line breaks so that a removal, which a document event reports only
 * after the text is gone, can still be read as lines and columns.
 */
final class Marks {
    /** Marks a line deletion removes (one_adjust) rather than moves (one_adjust_nodel). */
    private static final String DELETABLE = "abcdefghijklmnopqrstuvwxyz.^\"";
    /** The document property under which a document holds its {@link Buffer}. */
    private static final Object BUFFER = new Object();

    /** A document's marks, shared by the editors of its views (Vim's buffer marks). */
    private static final class Buffer {
        final Document document;
        final List<Marks> editors = new ArrayList<>();
        final Map<Character, Pos> marks = new HashMap<>();
        final List<Pos> changes = new ArrayList<>();
        final List<Pos> extra = new ArrayList<>();
        /** The lines {@code :global} marked (Vim's ml_setmarked): they move with the text and go when deleted. */
        final List<Pos> marked = new ArrayList<>();
        final DocumentListener listener = new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent event) { inserted(event.getOffset(), event.getLength()); }
            @Override public void removeUpdate(DocumentEvent event) { removed(event.getOffset(), event.getLength()); }
            @Override public void changedUpdate(DocumentEvent event) { }
        };
        /** Offsets of the line breaks, ascending. */
        int[] breaks = new int[0];
        int breakCount;
        int documentLengthBefore;
        /** The mode of the last Visual area, for {@code gv}. */
        Mode visualMode;
        /** The remembered column of the last Visual area. */
        int visualCurswant = -1;
        /** Whether the next change starts a new change-list entry (Vim's b_new_change, set when an undo step begins). */
        boolean newChange = true;
        /** Set while a join removes a line break, so a blank joined line's marks move rather than go (do_join). */
        boolean joining;

        Buffer(Document document) {
            this.document = document;
            rebuild();
            document.addDocumentListener(listener);
        }

        /**
         * changed_common: the {@code '.} mark moves to the change, and the change list gains an entry for the first change
         * of an undo step unless it is on the same line as the last entry and within 79 columns of it. The editor of the
         * view that edits now goes to the list's end; another stays at its place, or at the end when it was there, and
         * when a full list drops its oldest entry, each place after the first moves back one with its entry.
         */
        void changed(int line, int col) {
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
                if (changes.size() == LIST_SIZE) {
                    changes.removeFirst();
                    for (Marks editor : editors) if (editor.changeIndex > 0) editor.changeIndex--;
                }
                for (Marks editor : editors) if (editor.changeIndex == changes.size()) editor.changeIndex++;
                changes.add(at.copy());
            } else changes.set(changes.size() - 1, at.copy());
            for (Marks editor : editors) if (editor.area.editsNow()) editor.changeIndex = changes.size();
        }

        void inserted(int offset, int length) {
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

        void removed(int offset, int length) {
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
                    // The joined line is gone, and with it its :global mark.
                    marked.removeIf(pos -> pos.line == line2);
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
        void adjust(int from, int to, int amount, int amountAfter) {
            for (Pos pos : all()) {
                if (pos.line >= from && pos.line <= to) pos.line += amount;
                else if (amountAfter != 0 && pos.line > to) pos.line += amountAfter;
            }
        }

        /** mark_adjust with MAXLNUM: marks on deleted lines {@code from}..{@code to} are deleted or moved to {@code from}. */
        void delete(int from, int to) {
            if (to < from) return;
            marks.entrySet().removeIf(entry -> DELETABLE.indexOf(entry.getKey()) >= 0 && entry.getValue().line >= from && entry.getValue().line <= to);
            marked.removeIf(pos -> pos.line >= from && pos.line <= to);
            for (Pos pos : all()) if (pos.line >= from && pos.line <= to) {
                pos.line = from;
                pos.col = 0;
            }
        }

        /** Every position that follows the text: the buffer's, and each editor's jumplist and previous contexts. */
        List<Pos> all() {
            List<Pos> all = new ArrayList<>(marks.values());
            for (Marks editor : editors) {
                if (editor.pcmark != null) all.add(editor.pcmark);
                if (editor.prevPcmark != null) all.add(editor.prevPcmark);
                all.addAll(editor.jumps);
            }
            all.addAll(changes);
            all.addAll(extra);
            all.addAll(marked);
            return all;
        }

        /** @return the line holding {@code offset}, by the break table as it was before the current change */
        int lineBefore(int offset) {
            int found = Arrays.binarySearch(breaks, 0, breakCount, offset);
            return found >= 0 ? found : -found - 1;
        }

        int lineStart(int line) { return line == 0 ? 0 : breaks[line - 1] + 1; }

        int lineLength(int line) {
            int end = line < breakCount ? breaks[line] : documentLengthBefore;
            return end - lineStart(line);
        }

        void shiftBreaks(int offset, int length, String text) {
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

        void dropBreaks(int offset, int length) {
            int n = 0;
            for (int i = 0; i < breakCount; i++) {
                int b = breaks[i];
                if (b < offset) breaks[n++] = b;
                else if (b >= offset + length) breaks[n++] = b - length;
            }
            breakCount = n;
            documentLengthBefore = document.getLength();
        }

        void rebuild() {
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

    private final Buffer buffer;
    /** The editor whose marks these are, and its text area. */
    private final VimEditor owner;
    private final VimTextArea area;
    private final List<Pos> jumps = new ArrayList<>();
    /** The jumplist index: jumps.size() when not moving through the list (Vim's w_jumplistidx). */
    int jumpIndex;
    /** The change-list index (Vim's w_changelistidx). */
    int changeIndex;
    /** The previous-context mark ({@code ''}) and the one before it (Vim's w_pcmark, w_prev_pcmark); null when unset. */
    private Pos pcmark, prevPcmark;
    /** The jumplist's and change list's size limit (Vim's JUMPLISTSIZE). */
    static final int LIST_SIZE = 100;

    /**
     * An editor's marks; the buffer's are the ones the document's other editors already have, else new. Its place in the
     * change list is after the last change, as a Vim window's is when it starts.
     *
     * @param owner the editor
     * @param area its text area, whose document it shows
     */
    Marks(VimEditor owner, VimTextArea area) {
        this.owner = owner;
        this.area = area;
        Document document = area.getDocument();
        Buffer shared = document.getProperty(BUFFER) instanceof Buffer held ? held : null;
        if (shared == null) {
            shared = new Buffer(document);
            document.putProperty(BUFFER, shared);
        }
        buffer = shared;
        buffer.editors.add(this);
        pcmark = new Pos(0, 0);
        changeIndex = buffer.changes.size();
    }

    /** @return another editor of the document, the first of them to open; null when there is none */
    VimEditor sibling() {
        for (Marks other : buffer.editors) if (other != this) return other.owner;
        return null;
    }

    /** @return whether {@code other} is an editor's of the same document: one Vim buffer */
    boolean sameBuffer(Marks other) { return buffer == other.buffer; }

    /**
     * win_init: the editor of a view split from {@code source}'s starts with its jumplist, its previous contexts and its
     * place in the change list.
     *
     * @param source the marks of an editor of the same document
     */
    void splitFrom(Marks source) {
        jumps.clear();
        for (Pos pos : source.jumps) jumps.add(pos.copy());
        jumpIndex = source.jumpIndex;
        pcmark = source.pcmark == null ? null : source.pcmark.copy();
        prevPcmark = source.prevPcmark == null ? null : source.prevPcmark.copy();
        changeIndex = source.changeIndex;
    }

    /** The editor goes; the buffer's marks go with the document's last editor. */
    void close() {
        buffer.editors.remove(this);
        if (!buffer.editors.isEmpty()) return;
        buffer.document.removeDocumentListener(buffer.listener);
        if (buffer.document.getProperty(BUFFER) == buffer) buffer.document.putProperty(BUFFER, null);
    }

    /**
     * @param name a mark name: {@code a}–{@code z}, or one of {@code [ ] < > ^ . " '}
     * @return the mark, or null when it is not set
     */
    Pos get(char name) {
        Pos pos = buffer.marks.get(name);
        return pos == null ? null : pos.copy();
    }

    /** Sets a mark to a copy of the position; null clears it. */
    void set(char name, Pos pos) {
        if (pos == null) buffer.marks.remove(name);
        else buffer.marks.put(name, pos.copy());
    }

    /** @return the mode of the last Visual area, for {@code gv}; null when there was none */
    Mode visualMode() { return buffer.visualMode; }

    /** @param mode the mode of the last Visual area */
    void visualMode(Mode mode) { buffer.visualMode = mode; }

    /** @return the remembered column of the last Visual area */
    int visualCurswant() { return buffer.visualCurswant; }

    /** @param curswant the remembered column of the last Visual area */
    void visualCurswant(int curswant) { buffer.visualCurswant = curswant; }

    /** clrallmarks ({@code :delmarks!}): the lowercase marks, {@code '[ '] '^ '.} and the change list go; {@code '"} goes to the start. */
    void clearAll() {
        buffer.marks.keySet().removeIf(name -> name >= 'a' && name <= 'z' || "[]^.".indexOf(name) >= 0);
        buffer.marks.put('"', new Pos(0, 0));
        buffer.changes.clear();
        changeIndex = 0;
    }

    /** Clears every mark and list, after the text was replaced. */
    void clear() {
        buffer.marks.clear();
        jumps.clear();
        buffer.changes.clear();
        jumpIndex = changeIndex = 0;
        buffer.visualMode = null;
        pcmark = new Pos(0, 0);
        prevPcmark = null;
        buffer.newChange = true;
        buffer.rebuild();
    }

    /** @return the previous-context mark, or null */
    Pos pcmark() { return pcmark == null ? null : pcmark.copy(); }

    /** While {@code :global} runs, the previous context stays where the command started (setpcmark does nothing). */
    boolean frozen;

    /** setpcmark: the cursor becomes the previous context and a new jumplist entry. */
    void setpcmark(Pos cursor) {
        if (frozen) return;
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
    void cleanupJumps() {
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
        List<Pos> changes = buffer.changes;
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
        buffer.joining = true;
        try { removal.run(); }
        finally { buffer.joining = false; }
    }

    /** A new undo step began: its first change may start a new change-list entry (u_savecommon's b_new_change). */
    void newChange() { buffer.newChange = true; }

    List<Pos> jumps() { return jumps; }

    List<Pos> changes() { return buffer.changes; }

    /**
     * do_move's mark_adjust: the marks on lines {@code first}..{@code last} (0-based) follow the lines to where
     * {@code edit} moves them, the first to {@code target}. The edit deletes and inserts lines, which alone would
     * delete those marks. {@code :global}'s marks stay behind, as Vim's line flags do.
     */
    void moving(int first, int last, int target, Runnable edit) {
        Map<Character, Pos> named = new HashMap<>();
        for (var entry : buffer.marks.entrySet())
            if (entry.getKey() != '.' && entry.getValue().line >= first && entry.getValue().line <= last) named.put(entry.getKey(), entry.getValue());
        Map<Pos, Pos> was = new java.util.IdentityHashMap<>();
        for (Pos pos : buffer.all()) if (pos.line >= first && pos.line <= last && buffer.marked.stream().noneMatch(m -> m == pos)) was.put(pos, pos.copy());
        edit.run();
        for (var entry : was.entrySet()) {
            entry.getKey().line = target + entry.getValue().line - first;
            entry.getKey().col = entry.getValue().col;
        }
        buffer.marks.putAll(named);
    }

    /** ml_setmarked: marks lines (0-based) for {@code :global}'s second pass. */
    void markLines(List<Integer> lines) {
        buffer.marked.clear();
        for (int line : lines) buffer.marked.add(new Pos(line, 0));
    }

    /** ml_firstmarked: the first marked line, which is unmarked; -1 when none is left. */
    int firstMarked() {
        Pos first = null;
        for (Pos pos : buffer.marked) if (first == null || pos.line < first.line) first = pos;
        if (first == null) return -1;
        buffer.marked.remove(first);
        return first.line;
    }

    /** ml_clearmarked. */
    void clearMarked() { buffer.marked.clear(); }

    /** Positions owned elsewhere (uppercase marks in this document) that move with the text too. */
    void track(Pos pos) { buffer.extra.add(pos); }

    void untrack(Pos pos) { buffer.extra.removeIf(p -> p == pos); }

    /** @return the positions owned elsewhere that move with the text, for tests */
    List<Pos> tracked() { return List.copyOf(buffer.extra); }
}
