package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Registers.Type;
import java.util.ArrayList;
import java.util.List;

/**
 * Putting a register's text: a port of Vim's do_put (register.c), for charwise, linewise and blockwise
 * registers, with the flags Visual-mode put uses. It sets the {@code '[} and {@code ']} marks and places the
 * cursor as Vim does.
 */
final class Put {
    /** gp and gP: the cursor goes after the new text. */
    static final int CURSEND = 1;
    /** ]p and friends: the lines take the current line's indent. */
    static final int FIXINDENT = 2;
    /** Put as lines whatever the register holds (p over a linewise Visual area). */
    static final int LINE = 4;
    /** Split the line at the cursor to put lines in between (p over a charwise Visual area). */
    static final int LINE_SPLIT = 8;
    /** Put lines below the Visual block (p over a block). */
    static final int LINE_FORWARD = 16;

    private Put() { }

    /**
     * @param c the command's context; its register, if any, is the one put
     * @param forward p rather than P
     * @param count how many copies
     * @param flags the flags above
     * @param visualEnd for {@code p} in Visual mode: the last line of the deleted Visual area, or -1
     */
    static void put(Context c, boolean forward, int count, int flags, int visualEnd) {
        Buffer b = c.buffer;
        Marks marks = c.editor.marks();
        char requested = Registers.effective(c.register, c.options.string("clipboard"));
        char name = requested == 0 ? '"' : requested;
        Pos cursor = c.cursor();
        marks.set('[', cursor);
        marks.set(']', cursor);
        Content content = c.registers.get(name).orElseThrow(() -> new VimFailure("E353: Nothing in register " + name));
        Type type = content.type();
        List<String> y = content.lines();
        int width = content.width();
        if (type == Type.LINE) {
            if ((flags & LINE_SPLIT) != 0) {
                String line = b.line(cursor.line);
                int split = forward && cursor.col < line.length() ? Text.next(line, cursor.col) : cursor.col;
                b.replace(b.offset(cursor.line, split), b.end(cursor.line), "\n" + line.substring(split));
                forward = true;
            }
            if ((flags & LINE_FORWARD) != 0) {
                cursor = new Pos(visualEnd, 0);
                forward = true;
            }
            marks.set('[', cursor);
            marks.set(']', cursor);
        }
        if ((flags & LINE) != 0) type = Type.LINE;
        int ts = c.tabstop();
        if (type == Type.BLOCK) {
            block(c, cursor, forward, count, flags, y, width, ts);
            return;
        }
        int lnum = cursor.line, col = cursor.col;
        String first = y.getFirst();
        if (type == Type.CHAR) {
            String line = b.line(lnum);
            if (forward && col < line.length()) {
                col = Text.next(line, col);
                if (!first.isEmpty()) cursor.col = col;
            }
            marks.set('[', cursor);
        } else if (!forward) lnum--;
        if (type == Type.CHAR && y.size() == 1) {
            simple(c, cursor, lnum, col, count, first, flags, visualEnd, ts);
            return;
        }
        Pos newCursor = cursor.copy();
        int newLine = newCursor.line;
        int origIndent = (flags & FIXINDENT) != 0 ? Text.indent(b.line(cursor.line), ts) : 0;
        boolean firstIndent = true;
        int indentDiff = 0, lenDiff = 0;
        for (int n = 1; n <= count; n++) {
            int i = 0;
            if (type == Type.CHAR) {
                // Split the line at the insert position, then join the register's first and last lines on.
                lnum = newCursor.line;
                String line = b.line(lnum);
                String tail = y.getLast() + line.substring(col);
                b.replace(b.offset(lnum, col), b.end(lnum), first + "\n" + tail);
                newLine++;
                cursor.line = lnum;
                i = 1;
            }
            List<String> block = new ArrayList<>();
            int insertAfter = lnum;
            for (; i < y.size(); i++) {
                if (type != Type.CHAR || i < y.size() - 1) {
                    block.add(y.get(i));
                    newLine++;
                }
                lnum++;
            }
            if (!block.isEmpty()) b.insertLines(insertAfter + 1, block);
            if ((flags & FIXINDENT) != 0) {
                for (int line = insertAfter + 1; line <= lnum; line++) {
                    String text = b.line(line);
                    boolean last = n == count && line == lnum;
                    int indent;
                    if (text.isEmpty()) indent = 0;
                    else if (firstIndent) {
                        indentDiff = origIndent - Text.indent(text, ts);
                        indent = origIndent;
                        firstIndent = false;
                    } else indent = Math.max(0, Text.indent(text, ts) + indentDiff);
                    String changed = text.isEmpty() ? text : Text.makeIndent(indent, ts, c.options.flag("expandtab")) + text.substring(Text.firstNonBlank(text));
                    if (last) lenDiff = text.length() - changed.length();
                    b.setLine(line, changed);
                }
            }
            if (n == 1) newLine = lnum;
        }
        Pos opStart = marks.get('[');
        if (type == Type.LINE) {
            opStart.col = 0;
            if (forward) opStart.line++;
            marks.set('[', opStart);
        }
        int lastLen = Math.max(0, y.getLast().length() - lenDiff);
        marks.set(']', new Pos(newLine, lastLen > 1 ? Text.previous(y.getLast(), lastLen) : 0));
        if ((flags & CURSEND) != 0) {
            if (type == Type.LINE) c.editor.setCursor(new Pos(Math.min(lnum + 1, b.count() - 1), 0), true);
            else c.editor.setCursor(new Pos(newLine, lastLen), true);
        } else if (type == Type.LINE) {
            int line = forward ? cursor.line + 1 : cursor.line;
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))), true);
        } else c.editor.setCursor(newCursor, true);
        clampAfterPut(c);
    }

    /** The one-line charwise case, which in Visual block mode repeats on every line of the block. */
    private static void simple(Context c, Pos cursor, int lnum, int col, int count, String text, int flags, int visualEnd, int ts) {
        Buffer b = c.buffer;
        int start = lnum, end = visualEnd > start ? visualEnd : start;
        int vcol = Text.virtcol(b.line(lnum), col, ts);
        int total = count * text.length();
        int headOff = 0;
        if (count > 0 && !text.isEmpty()) {
            for (int line = start; line <= end; line++) {
                String old = b.line(line);
                int at = line == start ? col : Text.columnAt(old, vcol, ts);
                if (visualEnd >= 0 && at > old.length()) continue;
                at = Math.min(at, old.length());
                String inserted = text.repeat(count);
                b.insert(b.offset(line, at), inserted);
                headOff = inserted.length() - Text.last(inserted) - 1;
                if (line == cursor.line) cursor.col += total - 1;
            }
        }
        // '] is on the first unit of the last character put; the cursor there, or after it for gp.
        c.editor.marks().set(']', new Pos(cursor.line, Math.max(0, cursor.col - headOff)));
        if (total > 0 && (flags & CURSEND) != 0) cursor.col++;
        else cursor.col -= headOff;
        c.editor.setCursor(cursor, true);
        clampAfterPut(c);
    }

    /** The blockwise case: each register line goes into the next line at the cursor's display column. */
    private static void block(Context c, Pos cursor, boolean forward, int count, int flags, List<String> y, int width, int ts) {
        Buffer b = c.buffer;
        int lnum = cursor.line;
        String line0 = b.line(lnum);
        int col;
        if (forward && cursor.col < line0.length()) {
            int vcol = Text.virtcol(line0, cursor.col, ts);
            col = vcol + Text.width(line0, cursor.col, vcol, ts) - 1 + 1;
            cursor.col = Text.next(line0, cursor.col);
        } else col = Text.virtcol(line0, Math.min(cursor.col, line0.length()), ts);
        int totlen = 0, textcol = 0, firstStartspaces = 0;
        int row = lnum;
        for (int i = 0; i < y.size(); i++, row++) {
            if (row >= b.count()) b.insertLines(b.count(), List.of(""));
            String old = b.line(row);
            int vcol = 0, p = 0, incr = 0;
            while (vcol < col && p < old.length()) {
                incr = Text.width(old, p, vcol, ts);
                vcol += incr;
                p = Text.next(old, p);
            }
            textcol = p;
            boolean shortline = vcol < col || vcol == col && p >= old.length();
            int startspaces = 0, endspaces = 0, delcount = 0;
            if (vcol < col) startspaces = col - vcol;
            else if (vcol > col) {
                endspaces = vcol - col;
                startspaces = incr - endspaces;
                textcol = Text.previous(old, textcol);
                delcount = 1;
                if (old.charAt(textcol) != '\t') {
                    delcount = 0;
                    endspaces = 0;
                }
            }
            String text = y.get(i);
            int spaces = width + 1;
            int v = 0;
            for (int q = 0; q < text.length(); q = Text.next(text, q)) spaces -= Text.width(text, q, v, ts);
            if (spaces < 0) spaces = 0;
            StringBuilder out = new StringBuilder(old.substring(0, textcol));
            out.append(" ".repeat(startspaces));
            totlen = startspaces + endspaces;
            for (int j = 0; j < count; j++) {
                out.append(text);
                totlen += text.length();
                if ((j < count - 1 || !shortline) && spaces > 0) {
                    out.append(" ".repeat(spaces));
                    totlen += spaces;
                }
            }
            out.append(" ".repeat(endspaces));
            out.append(old.substring(Math.min(old.length(), textcol + delcount)));
            b.setLine(row, out.toString());
            if (i == 0) firstStartspaces = startspaces;
        }
        Marks marks = c.editor.marks();
        marks.set('[', new Pos(lnum, cursor.col + firstStartspaces));
        Pos opEnd = new Pos(row - 1, Math.max(0, textcol + totlen - 1));
        marks.set(']', opEnd);
        if ((flags & CURSEND) != 0) {
            Pos after = opEnd.copy();
            after.col = Math.min(after.col + 1, b.line(after.line).length());
            c.editor.setCursor(after, true);
        } else c.editor.setCursor(new Pos(lnum, cursor.col + firstStartspaces), true);
        clampAfterPut(c);
    }

    /** do_put's end: the cursor is never after the end of the line. */
    private static void clampAfterPut(Context c) {
        Pos p = c.cursor();
        int len = c.buffer.line(p.line).length();
        if (p.col > len) c.editor.setCursor(new Pos(p.line, len), true);
    }
}
