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

    static void joinLines(Context c, int first, int count, boolean insertSpace) {
        Buffer b = c.buffer;
        boolean js = c.options.flag("joinspaces");
        StringBuilder out = new StringBuilder();
        int col = 0, endcurr1 = 0, endcurr2 = 0;
        for (int t = 0; t < count; t++) {
            String curr = b.line(first + t);
            int spaces = 0;
            if (insertSpace && t > 0) {
                curr = curr.substring(Text.firstNonBlank(curr));
                if (!curr.isEmpty() && curr.charAt(0) != ')' && !out.isEmpty() && endcurr1 != '\t') {
                    if (endcurr1 == ' ') endcurr1 = endcurr2;
                    else spaces++;
                    if (js && (endcurr1 == '.' || endcurr1 == '?' || endcurr1 == '!')) spaces++;
                }
            }
            if (t == count - 1) col = out.length();
            out.append(" ".repeat(spaces)).append(curr);
            endcurr1 = endcurr2 = 0;
            if (insertSpace && !curr.isEmpty()) {
                int lastStart = curr.offsetByCodePoints(curr.length(), -1);
                endcurr1 = curr.codePointAt(lastStart);
                if (lastStart > 0) endcurr2 = curr.codePointBefore(lastStart);
            }
        }
        b.replace(b.start(first), b.end(first + count - 1), out.toString());
        c.editor.setCursor(new Pos(first, col));
    }

    /** do_put. */
    static void put(Context c, boolean after, boolean cursorAfter, boolean fixIndent) {
        char requested = Registers.effective(c.register, c.options.string("clipboard"));
        char name = requested == 0 ? '"' : requested;
        Content content = c.registers.get(name).orElseThrow(() -> new VimFailure("E353: Nothing in register " + name));
        Buffer b = c.buffer;
        Pos p = c.cursor();
        if (content.type() == Registers.Type.LINE) {
            List<String> lines = new ArrayList<>();
            for (int n = 0; n < c.count; n++) lines.addAll(content.lines());
            if (fixIndent) lines = reindented(c, lines, Text.indent(b.line(p.line), c.tabstop()));
            int at = after ? p.line + 1 : p.line;
            b.insertLines(at, lines);
            if (cursorAfter) {
                int line = at + lines.size();
                c.editor.setCursor(new Pos(Math.min(line, b.count() - 1), 0));
            } else c.editor.setCursor(new Pos(at, Text.firstNonBlank(b.line(at))));
            return;
        }
        String line = b.line(p.line);
        int col = after && !line.isEmpty() ? Text.next(line, p.col) : p.col;
        String text = content.text().repeat(c.count);
        int offset = b.offset(p.line, col);
        b.insert(offset, text);
        if (content.lines().size() == 1) {
            if (cursorAfter) c.editor.setCursor(new Pos(p.line, col + text.length()));
            else c.editor.setCursor(new Pos(p.line, col + Text.last(text)));
        } else c.editor.setCursor(cursorAfter ? b.pos(offset + text.length()) : new Pos(p.line, col));
    }

    /** ]p: the first line gets {@code target} indent and the others keep their indent relative to it. */
    private static List<String> reindented(Context c, List<String> lines, int target) {
        int ts = c.tabstop();
        boolean et = c.options.flag("expandtab");
        int delta = target - Text.indent(lines.getFirst(), ts);
        List<String> out = new ArrayList<>();
        for (String line : lines) {
            if (Text.blank(line)) out.add(line);
            else out.add(Text.makeIndent(Math.max(0, Text.indent(line, ts) + delta), ts, et) + line.substring(Text.firstNonBlank(line)));
        }
        return out;
    }
}
