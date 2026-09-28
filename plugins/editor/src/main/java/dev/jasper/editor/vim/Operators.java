package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayList;
import java.util.List;
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
        int before = c.buffer.count();
        deleteSpan(c, span, change);
        if (!change) c.editor.msgmore(c.buffer.count() - before);
    }

    private static void deleteSpan(Context c, Span span, boolean change) {
        Buffer b = c.buffer;
        Span s = span;
        if (s.blockwise()) {
            deleteBlock(c, s);
            return;
        }
        if (!change && !s.visual() && !s.linewise() && s.lineCount() > 1 && c.forced == 0) {
            // A charwise delete over lines that starts in the indent and ends at the end of a line becomes linewise.
            String endLine = b.line(s.end().line);
            int after = s.end().col;
            if (after < endLine.length() && s.kind() == Kind.INCLUSIVE) after = Text.next(endLine, after);
            if (Text.blank(endLine.substring(Math.min(after, endLine.length()))) && c.walk(false).inIndent(s.start(), 0))
                s = new Span(s.start(), s.end(), Kind.LINEWISE, s.regOne());
        }
        if (s.linewise()) {
            c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
            b.deleteLines(s.start().line, s.end().line);
            int line = Math.min(s.start().line, b.count() - 1);
            c.editor.setCursor(new Pos(line, Text.firstNonBlank(b.line(line))));
            return;
        }
        int from = b.offset(s.start()), to = end(b, s);
        if (to <= from) return;
        c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        b.remove(from, to);
        c.editor.setCursor(b.pos(from));
    }

    /** op_delete's block mode: each line loses its part of the block; a tab cut by an edge leaves spaces. */
    private static void deleteBlock(Context c, Span s) {
        Buffer b = c.buffer;
        c.registers.delete(c.register, content(c, s), s.lineCount(), s.regOne(), c.options.string("clipboard"));
        Pos cursor = c.cursor();
        for (int line = s.start().line; line <= s.end().line; line++) {
            String old = b.line(line);
            Block bd = prep(c, s, old, true, Block.Op.OTHER);
            if (bd.textlen == 0) continue;
            if (line == cursor.line) cursor.col = bd.textcol + bd.startspaces;
            b.setLine(line, old.substring(0, bd.textcol) + " ".repeat(bd.startspaces + bd.endspaces)
                + old.substring(Math.min(old.length(), bd.textcol + bd.textlen)));
        }
        c.editor.setCursor(cursor);
    }

    /** block_prep for one line of a block span. */
    static Block prep(Context c, Span s, String line, boolean isDel, Block.Op op) {
        Span.Columns k = s.block();
        return Block.prep(line, k.startVcol(), k.endVcol(), c.tabstop(), isDel, op, s.kind() == Kind.INCLUSIVE);
    }

    /** op_change. */
    static void change(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.blockwise()) {
            changeBlock(c, span);
            return;
        }
        if (span.linewise()) {
            c.registers.delete(c.register, content(c, span), span.lineCount(), span.regOne(), c.options.string("clipboard"));
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

    /**
     * op_change's block mode: the block is deleted, Insert mode starts on the first line, and when it ends (not with
     * {@code <C-c>}) the text typed there is inserted on every other line that reaches the block.
     */
    private static void changeBlock(Context c, Span span) {
        Buffer b = c.buffer;
        int startCol = span.start().col;
        delete(c, span, true);
        Pos p = c.cursor();
        if (startCol > p.col && !b.line(p.line).isEmpty()) {
            p.col = Text.next(b.line(p.line), p.col);
            c.editor.setCursor(p, true);
        }
        int first = span.start().line;
        int textcol = p.col;
        int preTextlen = b.line(first).length();
        c.editor.insert().begin("c", 1, false, -1);
        if (span.lineCount() == 1) return;
        c.editor.insert().onEnd(() -> {
            int insLen = b.line(first).length() - preTextlen;
            if (insLen <= 0) return;
            String text = b.line(first).substring(textcol, textcol + insLen);
            for (int line = first + 1; line <= span.end().line; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, true, Block.Op.OTHER);
                if (bd.isShort) continue;
                b.setLine(line, old.substring(0, bd.textcol) + text + old.substring(bd.textcol));
            }
            c.editor.setCursor(c.cursor());
        });
    }

    /** op_yank, with its message when more than 'report' lines were yanked. */
    static void yank(Context c, Span span) {
        c.registers.yank(c.register, content(c, span), c.options.string("clipboard"));
        if (c.register == '_') return;
        int lines = span.lineCount();
        if (!span.linewise() && !span.blockwise() && lines == 1) lines = 0;
        if (lines <= c.options.number("report")) return;
        String into = c.register == 0 ? "" : " into \"" + c.register;
        c.editor.say((span.blockwise() ? "block of " : "") + lines + (lines == 1 ? " line" : " lines") + " yanked" + into);
    }

    /** op_shift, with shift_line's 'shiftround'; a Visual area shifts {count} times. */
    static void shift(Context c, Span span, boolean left) {
        shift(c, span, left, span.visual() && c.hasCount ? c.count : 1, true);
    }

    /**
     * op_shift: each line {@code times} 'shiftwidth's; the cursor goes to the first line ({@code >>}), or for
     * {@code :>} to the last line's indent. More than 'report' lines give a message.
     */
    static void shift(Context c, Span span, boolean left, int times, boolean cursorTop) {
        shiftLines(c, span, left, times, cursorTop);
        int lines = span.lineCount();
        if (lines > c.options.number("report"))
            c.editor.say(lines + (lines == 1 ? " line " : " lines ") + (left ? "<" : ">") + "ed " + times + (times == 1 ? " time" : " times"));
    }

    private static void shiftLines(Context c, Span span, boolean left, int times, boolean cursorTop) {
        Buffer b = c.buffer;
        int sw = c.options.shiftwidth(), ts = c.tabstop();
        boolean et = c.options.flag("expandtab"), round = c.options.flag("shiftround");
        if (span.blockwise()) {
            int col = c.cursor().col;
            for (int line = span.start().line; line <= span.end().line; line++)
                if (!b.line(line).isEmpty()) shiftBlock(c, span, line, left, times);
            c.editor.setCursor(new Pos(span.start().line, col));
            return;
        }
        for (int line = span.start().line; line <= span.end().line; line++) {
            String text = b.line(line);
            if (text.isEmpty()) continue;
            int indent = Text.indent(text, ts), amount = times, width;
            if (round) {
                int i = indent / sw, j = indent % sw;
                if (j != 0 && left) amount--;
                i = left ? Math.max(0, i - amount) : i + amount;
                width = i * sw;
            } else width = left ? Math.max(0, indent - sw * amount) : indent + sw * amount;
            b.setLine(line, Text.makeIndent(width, ts, et) + text.substring(Text.firstNonBlank(text)));
        }
        if (!cursorTop) {
            // ":>": set_indent leaves the cursor after the last line's new indent.
            int last = span.end().line;
            String text = b.line(last);
            c.editor.setCursor(new Pos(last, text.isEmpty() ? 0 : Text.firstNonBlank(text)));
            return;
        }
        int first = span.start().line;
        c.editor.setCursor(new Pos(first, c.options.flag("startofline") ? Text.firstNonBlank(b.line(first)) : c.editor.wantedColumn(first)));
    }

    /** shift_block: the text from the block's start moves right, or its white space shrinks to move it left. */
    private static void shiftBlock(Context c, Span span, int line, boolean left, int amount) {
        Buffer b = c.buffer;
        int ts = c.tabstop(), total = amount * c.options.shiftwidth();
        String old = b.line(line);
        Block bd = prep(c, span, old, true, Block.Op.OTHER);
        if (bd.isShort) return;
        if (!left) {
            total += bd.preWhitesp;
            int wsVcol = bd.startVcol - bd.preWhitesp;
            int textstart = bd.textcol;
            if (bd.startspaces != 0) textstart = Text.next(old, textstart);
            int vcol = bd.startVcol;
            while (textstart < old.length() && Block.white(old.charAt(textstart))) {
                int incr = Text.width(old, textstart, vcol, ts);
                total += incr;
                vcol += incr;
                textstart++;
            }
            int tabs = 0, spaces;
            if (!c.options.flag("expandtab")) tabs = (wsVcol % ts + total) / ts;
            spaces = tabs > 0 ? (wsVcol % ts + total) % ts : total;
            int textcol = bd.textcol - (bd.preWhitespC - (bd.startspaces != 0 ? 1 : 0));
            b.setLine(line, old.substring(0, textcol) + "\t".repeat(tabs) + " ".repeat(spaces) + old.substring(textstart));
            return;
        }
        int nonWhite = bd.textcol;
        if (bd.startspaces != 0) nonWhite = Text.next(old, nonWhite);
        int nonWhiteCol = bd.startVcol;
        while (nonWhite < old.length() && Block.white(old.charAt(nonWhite))) {
            nonWhiteCol += Text.width(old, nonWhite, nonWhiteCol, ts);
            nonWhite++;
        }
        int blockSpaceWidth = nonWhiteCol - span.block().startVcol();
        int shift = Math.min(blockSpaceWidth, total);
        int destination = nonWhiteCol - shift;
        int copyEnd = bd.textcol, copyWidth = bd.startVcol;
        if (bd.startspaces != 0) copyWidth -= bd.startCharVcols;
        while (copyWidth < destination) {
            int incr = Text.width(old, copyEnd, copyWidth, ts);
            if (copyWidth + incr > destination) break;
            copyWidth += incr;
            copyEnd = Text.next(old, copyEnd);
        }
        int fill = destination - copyWidth;
        b.setLine(line, old.substring(0, copyEnd) + " ".repeat(fill) + old.substring(nonWhite));
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
        if (span.blockwise()) {
            for (int line = span.start().line; line <= span.end().line; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, false, Block.Op.OTHER);
                int end = Math.min(old.length(), bd.textcol + bd.textlen);
                if (end > bd.textcol && end < old.length() && Character.isHighSurrogate(old.charAt(end - 1))) end++;
                String part = old.substring(bd.textcol, end);
                StringBuilder out = new StringBuilder();
                part.codePoints().forEach(cp -> out.appendCodePoint(map.applyAsInt(cp)));
                if (!out.toString().equals(part)) b.setLine(line, old.substring(0, bd.textcol) + out + old.substring(end));
            }
            c.editor.setCursor(span.start());
            return;
        }
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

    /**
     * op_replace ({@code r} on a Visual area): every character becomes {@code ch}; in a block, a tab cut by an edge
     * leaves spaces, and a line break splits each line. The cursor goes to the area's start.
     */
    static void replace(Context c, Span span) {
        Buffer b = c.buffer;
        String ch;
        if (c.argument != null && (c.argument.is("<CR>") || c.argument.is("<C-j>"))) ch = c.argument.is("<CR>") ? "\r" : "\n";
        else if (c.argumentChar() >= 0) ch = Character.toString(c.argumentChar());
        else throw Context.fail();
        if (span.blockwise()) {
            Span.Columns k = span.block();
            int end = span.end().line;
            for (int line = span.start().line; line <= end; line++) {
                String old = b.line(line);
                Block bd = prep(c, span, old, true, Block.Op.REPLACE);
                if (bd.textlen == 0) continue;
                int numc = k.endVcol() - k.startVcol() + 1;
                if (bd.isShort) numc -= (k.endVcol() - bd.endVcol) + 1;
                String head = old.substring(0, bd.textcol) + " ".repeat(bd.startspaces);
                String tail = old.substring(Math.min(old.length(), bd.textcol + bd.textlen));
                if (ch.equals("\r") || ch.equals("\n")) {
                    b.setLine(line, head + "\n" + tail);
                    line++;
                    end++;
                } else b.setLine(line, head + ch.repeat(Math.max(0, numc)) + (bd.isShort ? "" : " ".repeat(bd.endspaces) + tail));
            }
            c.editor.setCursor(span.start());
            return;
        }
        Pos start = span.start(), last = span.end().copy();
        if (span.linewise()) {
            start = new Pos(start.line, 0);
            last.col = Math.max(0, b.line(last.line).length() - 1);
        } else if (span.kind() != Kind.INCLUSIVE) {
            if (last.col > 0) last.col = Text.previous(b.line(last.line), last.col);
            else if (last.line > 0) last = new Pos(last.line - 1, b.line(last.line - 1).length());
        }
        for (int line = start.line; line <= last.line; line++) {
            String old = b.line(line);
            int from = line == start.line ? start.col : 0;
            int to = line == last.line ? Math.min(old.length(), last.col < old.length() ? Text.next(old, last.col) : old.length()) : old.length();
            if (to <= from) continue;
            int chars = old.codePointCount(from, to);
            String replaced = old.substring(0, from) + ch.repeat(chars) + old.substring(to);
            if (!replaced.equals(old)) b.setLine(line, replaced);
        }
        c.editor.setCursor(start);
    }

    /** Visual J and gJ: join the area's lines (at least two). */
    static void joinVisual(Context c, Span span, boolean spaces) {
        int count = Math.max(2, span.lineCount());
        if (span.start().line + count - 1 > c.buffer.count() - 1) throw Context.fail();
        NormalCommands.joinLines(c, span.start().line, count, spaces);
    }

    /**
     * op_insert (Visual I and A): Insert mode before the area's start, or after its end; in a block, when the insert
     * ends on the first line (and not with {@code <C-c>}), the typed text goes into every other line of the block.
     */
    static void insert(Context c, Span span, boolean append) {
        Buffer b = c.buffer;
        int first = span.start().line;
        c.editor.setCursor(span.start(), true);
        if (!span.blockwise()) {
            if (append) {
                // Like an 'i' on the character after the area's end, unless the area is one display column; a
                // linewise area's end keeps the cursor's column. Vim compares op_start's column only for one line.
                Pos end = span.end();
                String line = b.line(end.line);
                int col = Math.min(end.col, Math.max(0, line.length() - 1));
                int ts = c.tabstop();
                int startVcol = span.lineCount() <= 1 ? Visual.startVcol(b, span.start(), ts) : 0;
                if (!line.isEmpty() && startVcol != Visual.endVcol(b, new Pos(end.line, col), ts)) col = Text.next(line, col);
                c.editor.setCursor(new Pos(end.line, col), true);
            }
            c.editor.insert().begin(append ? "A" : "I", 1, false, -1);
            return;
        }
        Span.Columns k = span.block();
        boolean max = k.max();
        Block bd = prep(c, span, b.line(first), true, append ? Block.Op.APPEND : Block.Op.INSERT);
        int preTextlen = b.line(first).length() - bd.textcol;
        if (append) {
            preTextlen -= bd.textlen;
            Pos p = c.cursor();
            String line = b.line(first);
            while (p.col < line.length() && p.col < bd.textcol + bd.textlen) p.col++;
            if (bd.isShort && !max) {
                b.insert(b.offset(first, line.length()), " ".repeat(bd.endspaces));
                bd.textlen += bd.endspaces;
                p.col = b.line(first).length();
            }
            c.editor.setCursor(p, true);
        }
        int textlenBefore = bd.textlen, textcolBefore = bd.textcol;
        int preLen = preTextlen;
        c.editor.insert().begin(append ? "A" : "I", 1, false, -1);
        c.editor.insert().onEnd(() -> {
            if (c.cursor().line != first) return;
            String firstLine = b.line(first);
            Block bd2 = prep(c, span, firstLine, true, append ? Block.Op.APPEND : Block.Op.INSERT);
            int textcol = textcolBefore, textlen = textlenBefore, pre = preLen;
            if (!max || bd2.textlen < textlen) {
                if (append) {
                    pre += bd2.textlen - textlen;
                    if (bd2.endspaces != 0) bd2.textlen--;
                }
                textcol = bd2.textcol;
                textlen = bd2.textlen;
            }
            int add = textcol + (append ? textlen : 0);
            if (add > firstLine.length()) add = firstLine.length();
            int insLen = firstLine.length() - add - pre;
            if (pre < 0 || insLen <= 0) return;
            blockInsert(c, span, firstLine.substring(add, add + insLen), !append);
            c.editor.setCursor(new Pos(first, span.start().col));
        });
    }

    /** block_insert: the text goes before (I) or after (A) the block on every line after the first. */
    private static void blockInsert(Context c, Span span, String text, boolean before) {
        Buffer b = c.buffer;
        Span.Columns k = span.block();
        for (int line = span.start().line + 1; line <= span.end().line; line++) {
            String old = b.line(line);
            Block bd = prep(c, span, old, true, before ? Block.Op.INSERT : Block.Op.APPEND);
            if (bd.isShort && before) continue;
            int tsVal, spaces = 0, count = 0, offset;
            if (before) {
                tsVal = bd.startCharVcols;
                spaces = bd.startspaces;
                if (spaces != 0) count = tsVal - 1;
                offset = bd.textcol;
            } else {
                tsVal = bd.endCharVcols;
                if (!bd.isShort) {
                    spaces = bd.endspaces != 0 ? tsVal - bd.endspaces : 0;
                    if (spaces != 0) count = tsVal - 1;
                    offset = bd.textcol + bd.textlen - (spaces != 0 ? 1 : 0);
                } else {
                    if (!k.max()) spaces = k.endVcol() - bd.endVcol + 1;
                    count = spaces;
                    offset = bd.textcol + bd.textlen;
                }
            }
            if (spaces < 0) spaces = 0;
            offset = Math.min(offset, old.length());
            StringBuilder out = new StringBuilder(old.substring(0, offset));
            out.append(" ".repeat(spaces)).append(text);
            int rest = offset;
            if (spaces > 0 && !bd.isShort) {
                if (rest < old.length() && old.charAt(rest) == '\t') {
                    out.append(" ".repeat(tsVal - spaces));
                    rest++;
                }
            }
            out.append(old.substring(rest));
            b.setLine(line, out.toString());
        }
    }

    /** @return the span's text as a register holds it (op_yank) */
    static Content content(Context c, Span span) {
        Buffer b = c.buffer;
        if (span.blockwise()) {
            Span.Columns k = span.block();
            List<String> lines = new ArrayList<>();
            for (int line = span.start().line; line <= span.end().line; line++) {
                String text = b.line(line);
                lines.add(prep(c, span, text, false, Block.Op.OTHER).copy(text));
            }
            int width = k.endVcol() - k.startVcol();
            if (k.max() && width > 0) width--;
            return new Content(lines, Registers.Type.BLOCK, width);
        }
        if (span.linewise()) return new Content(b.lines(span.start().line, span.end().line), Registers.Type.LINE);
        // An exclusive span from column 0 to column 0 of a later line yanks as lines (not for Visual areas).
        if (!span.visual() && span.kind() == Kind.EXCLUSIVE && span.start().col == 0 && span.end().col == 0 && span.lineCount() > 1)
            return new Content(b.lines(span.start().line, span.end().line - 1), Registers.Type.LINE);
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
