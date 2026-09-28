package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;
import java.util.function.IntUnaryOperator;

/**
 * Operators (spec 4.1), ported from Vim's ops.c. The machine has already put the cursor on the span's start, as
 * do_pending_operator does; each operator then places it as Vim does.
 */
final class Operators {
    private Operators() { }

    static void register(VimCommands commands) {
        commands.operator("d", "delete", (c, span) -> delete(c, span, false));
        commands.operator("c", "change: delete, then Insert mode", Operators::change);
        commands.operator("y", "yank", Operators::yank);
        commands.operator(">", "shift right by 'shiftwidth'", (c, span) -> shift(c, span, false));
        commands.operator("<lt>", "shift left by 'shiftwidth'", (c, span) -> shift(c, span, true));
        commands.operator("=", "re-indent (not Vim's C-indenting; see docs/vim-divergences.md)", Operators::reindent);
        commands.operator("g~", "toggle case", (c, span) -> changeCase(c, span, Operators::toggle));
        commands.operator("gu", "lower case", (c, span) -> changeCase(c, span, Character::toLowerCase));
        commands.operator("gU", "upper case", (c, span) -> changeCase(c, span, Character::toUpperCase));
        commands.operator("g?", "rot13", (c, span) -> changeCase(c, span, Operators::rot13));
    }

    /** op_delete. {@code change} is true for {@code c}, which keeps charwise spans charwise. */
    static void delete(Context c, Span span, boolean change) {
        Buffer b = c.buffer;
        Span s = span;
        if (!change && !s.linewise() && s.lineCount() > 1 && c.forced == 0) {
            // A charwise delete over lines that starts in the indent and ends at the end of a line becomes linewise.
            String endLine = b.line(s.end().line);
            int after = s.end().col;
            if (after < endLine.length() && s.kind() == Kind.INCLUSIVE) after = Text.next(endLine, after);
            if (Text.blank(endLine.substring(Math.min(after, endLine.length()))) && c.walk(false).inIndent(s.start(), 0))
                s = new Span(s.start(), s.end(), Kind.LINEWISE, s.regOne());
        }
        if (s.linewise()) {
            c.registers.delete(c.register, content(b, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
            b.deleteLines(s.start().line, s.end().line);
            int line = Math.min(s.start().line, b.count() - 1);
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))));
            return;
        }
        int from = b.offset(s.start()), to = end(b, s);
        if (to <= from) return;
        c.registers.delete(c.register, content(b, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        b.remove(from, to);
        c.editor.setCursor(b.pos(from));
    }

    /** op_change. */
    static void change(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.linewise()) {
            c.registers.delete(c.register, content(b, span), span.lineCount(), span.regOne(), c.options.string("clipboard"));
            int first = span.start().line;
            if (span.lineCount() > 1) b.deleteLines(first + 1, span.end().line);
            String line = b.line(first);
            boolean ai = c.options.flag("autoindent");
            String keep = ai ? line.substring(0, Text.firstNonBlank(line)) : "";
            b.setLine(first, keep);
            c.editor.setCursor(new Pos(first, keep.length()), true);
            c.editor.insert().begin("c", 1, false, ai ? first : -1);
            return;
        }
        int from = b.offset(span.start());
        delete(c, span, true);
        c.editor.setCursor(b.pos(from), true);
        c.editor.insert().begin("c", 1, false, -1);
    }

    /** op_yank. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c.buffer, span), c.options.string("clipboard"));
    }

    /** op_shift, with shift_line's 'shiftround'. */
    static void shift(Context c, Span span, boolean left) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab"), round = c.options.flag("shiftround");
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            if (text.isEmpty()) continue;
            int indent = Text.indent(text, ts), amount = 1, width;
            if (round) {
                int i = indent / sw, j = indent % sw;
                if (j != 0 && left) amount--;
                i = left ? Math.max(0, i - amount) : i + amount;
                width = i * sw;
            } else width = left ? Math.max(0, indent - sw) : indent + sw;
            b.setLine(line, Text.makeIndent(width, ts, et) + text.substring(Text.firstNonBlank(text)));
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, c.options.flag("startofline") ? Text.firstNonBlank(b.line(first)) : c.editor.wantedColumn(first)));
    }

    /**
     * {@code =}: each line gets the indent of the previous non-blank line, plus one 'shiftwidth' for each bracket
     * that line leaves open, minus one when the line starts with a closing bracket. Blank lines lose their indent.
     * A recorded divergence: Vim uses C-indenting.
     */
    static void reindent(Context c, Span span) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab");
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            String body = text.substring(Text.firstNonBlank(text));
            if (body.isEmpty()) {
                b.setLine(line, "");
                continue;
            }
            int previous = line - 1;
            while (previous >= 0 && Text.blank(b.line(previous))) previous--;
            int width = 0;
            if (previous >= 0) {
                String above = b.line(previous);
                width = Text.indent(above, ts) + sw * Math.max(0, openBrackets(above));
            }
            if (")]}".indexOf(body.charAt(0)) >= 0) width -= sw;
            b.setLine(line, Text.makeIndent(Math.max(0, width), ts, et) + body);
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, Text.firstNonBlank(b.line(first))));
    }

    private static int openBrackets(String line) {
        int depth = 0;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if ("([{".indexOf(ch) >= 0) depth++;
            else if (")]}".indexOf(ch) >= 0 && depth > 0) depth--;
        }
        // A line that starts with a closer has already been dedented; its closer does not count again.
        return depth;
    }

    /** op_tilde: maps every character of the span; the cursor stays on the span's start. */
    static void changeCase(Context c, Span span, IntUnaryOperator map) {
        Buffer b = c.buffer;
        int from, to;
        if (span.linewise()) {
            from = b.start(span.start().line);
            to = b.end(span.end().line);
        } else {
            from = b.offset(span.start());
            to = end(b, span);
        }
        if (to <= from) return;
        String text = b.text(from, to);
        StringBuilder out = new StringBuilder(text.length());
        text.codePoints().forEach(cp -> out.appendCodePoint(map.applyAsInt(cp)));
        if (!out.toString().equals(text)) b.replace(from, to, out.toString());
        c.editor.setCursor(span.start());
    }

    static int toggle(int cp) {
        if (Character.isUpperCase(cp)) return Character.toLowerCase(cp);
        if (Character.isLowerCase(cp)) return Character.toUpperCase(cp);
        return cp;
    }

    static int rot13(int cp) {
        if (cp >= 'a' && cp <= 'z') return 'a' + (cp - 'a' + 13) % 26;
        if (cp >= 'A' && cp <= 'Z') return 'A' + (cp - 'A' + 13) % 26;
        return cp;
    }

    /** @return the span's text as a register holds it */
    static Content content(Buffer b, Span span) {
        if (span.linewise()) return new Content(b.lines(span.start().line, span.end().line), Registers.Type.LINE);
        return Content.of(b.text(b.offset(span.start()), end(b, span)), Registers.Type.CHAR);
    }

    /** @return the offset just after a charwise span; an inclusive end on the end of a line stops before its break */
    static int end(Buffer b, Span span) {
        String line = b.line(span.end().line);
        int col = Math.min(span.end().col, line.length());
        if (span.kind() == Kind.INCLUSIVE && col < line.length()) col = Text.next(line, col);
        return b.offset(span.end().line, col);
    }
}
