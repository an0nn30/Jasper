package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import java.util.ArrayList;
import java.util.List;

/** Normal-mode commands that are neither motions nor operators (spec 4.1), ported from Vim's normal.c and ops.c. */
final class NormalCommands {
    private NormalCommands() { }

    static void register(VimCommands commands) {
        commands.action("x", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("<Del>", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("X", "delete {count} characters before the cursor (dh)", true, c -> c.editor.machine().alias(c, "d", "h"));
        commands.action("D", "delete to the end of the line (d$)", true, c -> c.editor.machine().alias(c, "d", "$"));
        commands.action("C", "change to the end of the line (c$)", true, c -> c.editor.machine().alias(c, "c", "$"));
        commands.action("s", "substitute {count} characters (cl)", true, c -> c.editor.machine().alias(c, "c", "l"));
        commands.action("S", "substitute {count} lines (cc)", true, c -> c.editor.machine().alias(c, "c", "c"));
        commands.action("Y", "yank {count} lines (yy)", false, c -> c.editor.machine().alias(c, "y", "y"));
        commands.actionWithChar("r", "replace {count} characters with {char}", true, NormalCommands::replace);
        commands.action("~", "toggle the case of {count} characters", true, NormalCommands::tilde);
        commands.action("J", "join {count} lines, with a space", true, c -> join(c, true));
        commands.action("<C-g>", "show the file name, line count and cursor position", false, NormalCommands::fileInfo);
        commands.action("ga", "show the code of the character under the cursor", false, NormalCommands::ascii);
        commands.action("gJ", "join {count} lines, without a space", true, c -> join(c, false));
        commands.action("p", "put after the cursor", true, c -> put(c, true, false, false));
        commands.action("P", "put before the cursor", true, c -> put(c, false, false, false));
        commands.action("gp", "put after, cursor after the text", true, c -> put(c, true, true, false));
        commands.action("gP", "put before, cursor after the text", true, c -> put(c, false, true, false));
        commands.action("]p", "put after, with the current line's indent", true, c -> put(c, true, false, true));
        commands.action("[p", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("]P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("[P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("i", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("<Insert>", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("a", "append after the cursor", true, NormalCommands::append);
        commands.action("I", "insert before the first non-blank", true, c -> insertAt(c, Text.firstNonBlank(c.buffer.line(c.cursor().line)), "I"));
        commands.action("gI", "insert in column 1", true, c -> insertAt(c, 0, "gI"));
        commands.action("A", "append at the end of the line", true, c -> insertAt(c, c.buffer.line(c.cursor().line).length(), "A"));
        commands.action("o", "open a line below", true, c -> open(c, true));
        commands.action("O", "open a line above", true, c -> open(c, false));
        commands.action("R", "Replace mode", true, c -> c.editor.insert().begin("R", c.count, true, -1));
        commands.action("u", "undo {count} changes", false, c -> c.editor.undo(c.count));
        commands.action("<C-r>", "redo {count} changes", false, c -> c.editor.redo(c.count));
        commands.action(".", "repeat the last change, with {count} replacing its count", false, c -> c.editor.machine().repeat(c));
        commands.action("<C-l>", "redraw (does nothing)", false, c -> { });
    }

    /** nv_replace. */
    static void replace(Context c) {
        KeyInput key = c.argument;
        if (key.is("<Esc>")) return;
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int end = p.col;
        for (int n = 0; n < c.count; n++) {
            if (end >= line.length()) throw Context.fail();
            end = Text.next(line, end);
        }
        if (key.is("<CR>") || key.is("<C-j>")) {
            c.buffer.remove(c.buffer.offset(p), c.buffer.offset(p.line, end));
            c.editor.setCursor(p);
            InsertMode insert = c.editor.insert();
            insert.beginNested("r");
            insert.key(KeyInput.named("CR"));
            insert.key(KeyInput.named("Esc"));
            return;
        }
        String with;
        if (key.is("<Tab>")) with = "\t";
        else if (key.plainChar() >= 0) with = key.key();
        else throw Context.fail();
        c.buffer.replace(c.buffer.offset(p), c.buffer.offset(p.line, end), with.repeat(c.count));
        c.editor.setCursor(new Pos(p.line, p.col + (c.count - 1) * with.length()));
    }

    /** n_swapchar, with 'notildeop'. */
    static void tilde(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (line.isEmpty()) throw Context.fail();
        int col = p.col;
        StringBuilder out = new StringBuilder(line.substring(0, col));
        for (int n = c.count; n > 0 && col < line.length(); n--) {
            int next = Text.next(line, col);
            line.substring(col, next).codePoints().forEach(cp -> out.appendCodePoint(Operators.toggle(cp)));
            col = next;
        }
        out.append(line.substring(col));
        c.buffer.setLine(p.line, out.toString());
        c.editor.setCursor(new Pos(p.line, col));
    }

    static void append(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (!line.isEmpty()) p.col = Text.next(line, p.col);
        c.editor.setCursor(p, true);
        c.editor.insert().begin("a", c.count, false, -1);
    }

    static void insertAt(Context c, int col, String command) {
        c.editor.setCursor(new Pos(c.cursor().line, col), true);
        c.editor.insert().begin(command, c.count, false, -1);
    }

    static void open(Context c, boolean below) {
        String command = below ? "o" : "O";
        c.editor.insert().openLine(below);
        c.editor.insert().begin(command, c.count, false, c.options.flag("autoindent") ? c.cursor().line : -1);
    }

    /** nv_join, do_join. */
    static void join(Context c, boolean spaces) {
        int count = Math.max(c.count, 2), first = c.cursor().line, last = c.buffer.count() - 1;
        if (first + count - 1 > last) {
            if (count <= 2) throw Context.fail();
            count = last - first + 1;
        }
        joinLines(c, first, count, spaces);
    }

    /**
     * do_join. Each line joins the first in turn: the separator goes after the first line's text, then the line break
     * and the next line's indent go, so marks on the joined line move as Vim's mark_col_adjust moves them.
     */
    static void joinLines(Context c, int first, int count, boolean insertSpace) {
        Buffer b = c.buffer;
        boolean js = c.options.flag("joinspaces");
        int col = 0, endcurr1 = 0, endcurr2 = 0;
        String head = b.line(first);
        if (insertSpace && !head.isEmpty()) {
            int lastStart = head.offsetByCodePoints(head.length(), -1);
            endcurr1 = head.codePointAt(lastStart);
            if (lastStart > 0) endcurr2 = head.codePointBefore(lastStart);
        }
        for (int t = 1; t < count; t++) {
            String curr = b.line(first + 1);
            int removed = 0, spaces = 0;
            if (insertSpace) {
                removed = Text.firstNonBlank(curr);
                curr = curr.substring(removed);
                if (!curr.isEmpty() && curr.charAt(0) != ')' && b.line(first).length() > 0 && endcurr1 != '\t') {
                    if (endcurr1 == ' ') endcurr1 = endcurr2;
                    else spaces++;
                    if (js && (endcurr1 == '.' || endcurr1 == '?' || endcurr1 == '!')) spaces++;
                }
            }
            int end = b.end(first);
            if (t == count - 1) col = b.line(first).length();
            if (spaces > 0) b.insert(end, " ".repeat(spaces));
            int from = b.end(first), to = b.start(first + 1) + removed;
            c.editor.marks().joining(() -> b.remove(from, to));
            endcurr1 = endcurr2 = 0;
            if (insertSpace && !curr.isEmpty()) {
                int lastStart = curr.offsetByCodePoints(curr.length(), -1);
                endcurr1 = curr.codePointAt(lastStart);
                if (lastStart > 0) endcurr2 = curr.codePointBefore(lastStart);
            }
        }
        c.editor.setCursor(new Pos(first, col));
    }

    /**
     * fileinfo (buffer.c) with 'noruler': {@code "name" [Modified] line 2 of 10 --20%-- col 3}, the column shown as
     * {@code 3-10} when the display column differs.
     */
    static void fileInfo(Context c) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        StringBuilder out = new StringBuilder("\"").append(c.editor.documentName()).append('"');
        boolean modified = c.editor.documentModified();
        out.append(modified ? " [Modified] " : " ");
        if (b.length() == 0) out.append("--No lines in buffer--");
        else {
            int lines = b.count();
            int percent = (int) ((long) (p.line + 1) * 100 / lines);
            String line = b.line(p.line);
            int col = p.col + 1, vcol = Text.virtcol(line, p.col, c.tabstop()) + 1;
            out.append("line ").append(p.line + 1).append(" of ").append(lines).append(" --").append(percent).append("%-- col ");
            out.append(col == vcol ? String.valueOf(col) : col + "-" + vcol);
        }
        c.editor.message(out.toString(), false);
    }

    /**
     * do_ascii: the character under the cursor as {@code <a>  97,  Hex 61,  Octal 141}, or for a character above
     * U+007F {@code <é> 233, Hex 00e9, Oct 351}, followed by any combining characters. Vim adds the character's
     * digraph; Jasper has no digraph table (docs/vim-divergences.md).
     */
    static void ascii(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (p.col >= line.length()) {
            c.editor.message("NUL", false);
            return;
        }
        StringBuilder out = new StringBuilder();
        int i = p.col;
        int cp = line.codePointAt(i);
        if (cp < 0x80) {
            String shown = cp < 0x20 ? "^" + (char) (cp + '@') : cp == 0x7f ? "^?" : String.valueOf((char) cp);
            out.append('<').append(shown).append(">  ").append(cp).append(",  Hex ").append(String.format("%02x", cp))
                .append(",  Octal ").append(String.format("%03o", cp));
            i += 1;
        } else {
            out.append('<').append(Character.toString(cp)).append("> ").append(cp).append(", Hex ").append(String.format("%04x", cp))
                .append(", Oct ").append(Integer.toOctalString(cp));
            i += Character.charCount(cp);
        }
        while (i < line.length() && Character.getType(line.codePointAt(i)) == Character.NON_SPACING_MARK) {
            int mark = line.codePointAt(i);
            out.append(" < ").append(Character.toString(mark)).append("> ").append(mark).append(", Hex ").append(String.format("%04x", mark))
                .append(", Oct ").append(Integer.toOctalString(mark));
            i += Character.charCount(mark);
        }
        c.editor.message(out.toString(), false);
    }

    /** do_put: see {@link Put}. */
    static void put(Context c, boolean after, boolean cursorAfter, boolean fixIndent) {
        Put.put(c, after, c.count, (cursorAfter ? Put.CURSEND : 0) | (fixIndent ? Put.FIXINDENT : 0), -1);
    }
}
