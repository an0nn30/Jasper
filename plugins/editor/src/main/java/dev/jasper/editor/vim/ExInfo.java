package dev.jasper.editor.vim;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Ex commands that show things or set marks and options: {@code :print} and its kin, {@code :=}, {@code :marks},
 * {@code :jumps}, {@code :changes}, {@code :registers}, {@code :k}/{@code :mark}, {@code :delmarks},
 * {@code :nohlsearch} and {@code :set}. Listings are ports of mark.c's ex_marks, ex_jumps and ex_changes, register.c's
 * ex_display and ex_cmds.c's print_line; each line goes to the output panel. Columns are byte columns, as Vim shows.
 */
final class ExInfo {
    private ExInfo() { }

    /** ex_print: {@code :p}, {@code :l} (list), {@code :nu} and {@code :#} (numbered), with any flags. */
    static void print(ExCommand cmd) {
        boolean number = cmd.name().equals("number") || cmd.name().equals("#") || (cmd.flags & Ex.NUMBER) != 0;
        boolean list = cmd.name().equals("list") || (cmd.flags & Ex.LIST) != 0;
        printLines(cmd, number, list);
    }

    /** The lines of the range, then the cursor on the last one (ex_print). */
    static void printLines(ExCommand cmd, boolean number, boolean list) {
        VimEditor editor = cmd.editor;
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) editor.say(line(editor, lnum, number, list));
        editor.ex().setpcmark();
        editor.ex().beginline(cmd.line2, true);
    }

    /** ex_may_print: after a command with {@code p}, {@code l} or {@code #}, the cursor line is printed. */
    static void mayPrint(ExCommand cmd) {
        if (cmd.flags == 0) return;
        VimEditor editor = cmd.editor;
        editor.say(line(editor, editor.cursor().line + 1, (cmd.flags & Ex.NUMBER) != 0, (cmd.flags & Ex.LIST) != 0));
    }

    /**
     * print_line: a line as {@code :print} shows it: a tab as spaces (or {@code ^I} when listing, with {@code $} at
     * the end), control characters as {@code ^X}, and with a line number in a field of at least three columns.
     */
    static String line(VimEditor editor, int lnum, boolean number, boolean list) {
        StringBuilder out = new StringBuilder();
        if (number) {
            int width = Math.max(3, String.valueOf(editor.buffer().count()).length());
            out.append(String.format("%" + width + "d ", lnum));
        }
        String text = editor.buffer().line(lnum - 1);
        int ts = editor.options().number("tabstop"), col = 0;
        for (int i = 0; i < text.length(); ) {
            int cp = text.codePointAt(i);
            i += Character.charCount(cp);
            if (cp == '\t' && !list) {
                int n = ts - col % ts;
                out.append(" ".repeat(n));
                col += n;
            } else if (cp < 0x20 || cp == 0x7f) {
                out.append('^').append(cp == 0x7f ? '?' : (char) (cp + '@'));
                col += 2;
            } else {
                out.appendCodePoint(cp);
                col += Text.width(cp);
            }
        }
        if (list) out.append('$');
        return out.toString();
    }

    /** ex_equal: the range's last line number (the last line when no range is given). */
    static void equal(ExCommand cmd) {
        cmd.editor.say(String.valueOf(cmd.line2));
        mayPrint(cmd);
    }

    /** ex_mark: {@code :k} and {@code :mark} set a mark at the first non-blank of the range's last line. */
    static void mark(ExCommand cmd) {
        if (cmd.arg.isEmpty()) throw new VimFailure("E471: Argument required");
        if (cmd.arg.codePointCount(0, cmd.arg.length()) > 1) throw new VimFailure("E488: Trailing characters: " + cmd.arg);
        VimEditor editor = cmd.editor;
        int line = cmd.line2 - 1;
        Pos at = new Pos(line, Text.firstNonBlank(editor.buffer().line(line)));
        if (!MarkCommands.setmark(editor, cmd.arg.charAt(0), at)) throw new VimFailure("E191: Argument must be a letter or forward/backward quote");
    }

    /** ex_delmarks: marks, ranges of them ({@code a-d}), or with {@code !} all lowercase marks. */
    static void delmarks(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        String arg = cmd.arg;
        if (arg.isEmpty() && cmd.forceit) {
            marks.clearAll();
            return;
        }
        if (cmd.forceit) throw new VimFailure("E474: Invalid argument");
        if (arg.isEmpty()) throw new VimFailure("E471: Argument required");
        for (int i = 0; i < arg.length(); i++) {
            char c = arg.charAt(i);
            boolean lower = c >= 'a' && c <= 'z', digit = c >= '0' && c <= '9', upper = c >= 'A' && c <= 'Z';
            if (lower || digit || upper) {
                char from = c, to = c;
                if (i + 1 < arg.length() && arg.charAt(i + 1) == '-') {
                    to = i + 2 < arg.length() ? arg.charAt(i + 2) : 0;
                    boolean same = lower ? to >= 'a' && to <= 'z' : digit ? to >= '0' && to <= '9' : to >= 'A' && to <= 'Z';
                    if (!same || to < from) throw new VimFailure("E475: Invalid argument: " + arg.substring(i));
                    i += 2;
                }
                for (char m = from; m <= to; m++) {
                    if (lower) marks.set(m, null);
                    else editor.vim().clearGlobalMark(m);
                }
            } else switch (c) {
                case '"', '^', '.', '[', ']' -> marks.set(c, null);
                case '<' -> marks.set(Visual.START, null);
                case '>' -> marks.set(Visual.END, null);
                case ' ' -> { }
                default -> throw new VimFailure("E475: Invalid argument: " + arg.substring(i));
            }
        }
    }

    /** ex_marks: every mark that is set, or those named in the argument. */
    static void marks(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String arg = cmd.arg.isEmpty() ? null : cmd.arg;
        Marks marks = editor.marks();
        List<String> lines = new ArrayList<>();
        mark(editor, lines, arg, '\'', marks.pcmark(), null);
        for (char c = 'a'; c <= 'z'; c++) mark(editor, lines, arg, c, marks.get(c), null);
        var global = editor.vim().globalMarks();
        for (char c = 'A'; c <= 'Z'; c++) globalMark(editor, lines, arg, c, global.get(c));
        for (char c = '0'; c <= '9'; c++) globalMark(editor, lines, arg, c, global.get(c));
        mark(editor, lines, arg, '"', marks.get('"'), null);
        mark(editor, lines, arg, '[', marks.get('['), null);
        mark(editor, lines, arg, ']', marks.get(']'), null);
        mark(editor, lines, arg, '^', marks.get('^'), null);
        mark(editor, lines, arg, '.', marks.get('.'), null);
        // The Visual marks as they will be jumped to: '< is the earlier end.
        Pos start = marks.get(Visual.START), end = marks.get(Visual.END);
        Pos first = start != null && (end == null || start.compareTo(end) < 0) ? start : end;
        mark(editor, lines, arg, '<', first, null);
        mark(editor, lines, arg, '>', first == start ? end : start, null);
        if (lines.isEmpty()) {
            if (arg == null) editor.say("No marks set");
            else throw new VimFailure("E283: No marks matching \"" + arg + "\"");
            return;
        }
        editor.say("mark line  col file/text");
        lines.forEach(editor::say);
    }

    private static void globalMark(VimEditor editor, List<String> lines, String arg, char name, Vim.GlobalMark mark) {
        if (mark == null) return;
        String other = mark.inFileOf(editor) ? null : mark.editor().documentName();
        mark(other == null ? editor : mark.editor(), lines, arg, name, mark.pos(), other);
    }

    private static void mark(VimEditor editor, List<String> lines, String arg, char name, Pos pos, String fileName) {
        if (pos == null || arg != null && arg.indexOf(name) < 0) return;
        Buffer b = editor.buffer();
        String text = fileName != null ? fileName : markLine(editor, pos, 15);
        lines.add(String.format(" %c %6d %4d ", name, pos.line + 1, byteColumn(b, pos)) + text);
    }

    /** ex_jumps: the jumplist, {@code >} at the current entry. */
    static void jumps(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        marks.cleanupJumps();
        editor.say(" jump line  col file/text");
        List<Pos> jumps = marks.jumps();
        int index = marks.jumpIndex;
        for (int i = 0; i < jumps.size(); i++) {
            Pos pos = jumps.get(i);
            editor.say(String.format("%c %2d %5d %4d ", i == index ? '>' : ' ', Math.abs(i - index), pos.line + 1, byteColumn(editor.buffer(), pos))
                + markLine(editor, pos, 16));
        }
        if (index == jumps.size()) editor.say(">");
    }

    /** ex_changes: the change list, {@code >} at the current entry. */
    static void changes(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Marks marks = editor.marks();
        editor.say("change line  col text");
        List<Pos> changes = marks.changes();
        int index = marks.changeIndex;
        for (int i = 0; i < changes.size(); i++) {
            Pos pos = changes.get(i);
            editor.say(String.format("%c %3d %5d %4d ", i == index ? '>' : ' ', Math.abs(i - index), pos.line + 1, byteColumn(editor.buffer(), pos))
                + markLine(editor, pos, 17));
        }
        if (index == changes.size()) editor.say(">");
    }

    /** @return a mark's column in bytes, as Vim counts it; a column past the line counts the line's bytes before it */
    private static int byteColumn(Buffer b, Pos pos) {
        if (pos.col == Visual.MAX) return Integer.MAX_VALUE;
        if (pos.line >= b.count()) return pos.col;
        String line = b.line(pos.line);
        int col = Math.min(pos.col, line.length());
        return line.substring(0, col).getBytes(StandardCharsets.UTF_8).length + Math.max(0, pos.col - line.length());
    }

    /** mark_line: the mark's line without its indent, cut to fit the window after {@code lead} columns. */
    private static String markLine(VimEditor editor, Pos pos, int lead) {
        Buffer b = editor.buffer();
        if (pos.line < 0 || pos.line >= b.count()) return "-invalid-";
        String line = b.line(pos.line);
        line = line.substring(Text.firstNonBlank(line));
        return fit(line, editor.view().width() - lead, true);
    }

    /**
     * The text as msg_outtrans shows it, cut before the character that reaches {@code room} cells ({@code strict})
     * or that goes past it.
     */
    private static String fit(String text, int room, boolean strict) {
        StringBuilder out = new StringBuilder();
        int len = 0;
        for (String shown : KeyCodes.shown(text)) {
            len += KeyCodes.cells(shown);
            if (strict ? len >= room : len > room) break;
            out.append(shown);
        }
        return out.toString();
    }

    /** ex_display: {@code :registers} and {@code :display}, all registers or those named in the argument. */
    static void registers(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        String arg = cmd.arg.isEmpty() ? null : cmd.arg;
        Registers registers = editor.vim().registers();
        int columns = editor.view().width();
        editor.say("Type Name Content");
        StringBuilder names = new StringBuilder("\"0123456789abcdefghijklmnopqrstuvwxyz-*+");
        for (int i = 0; i < names.length(); i++) {
            char name = names.charAt(i);
            if (arg != null && arg.indexOf(name) < 0 && !(name == '*' && arg.indexOf('+') >= 0)) continue;
            var content = registers.get(name);
            if (content.isEmpty()) continue;
            Registers.Content c = content.get();
            char type = switch (c.type()) { case LINE -> 'l'; case CHAR -> 'c'; case BLOCK -> 'b'; };
            StringBuilder line = new StringBuilder("  ").append(type).append("  \"").append(name).append("   ");
            int n = columns - 11;
            List<String> lines = c.lines();
            for (int j = 0; j < lines.size() && n > 1; j++) {
                if (j > 0) {
                    line.append("^J");
                    n -= 2;
                }
                for (String shown : KeyCodes.shown(lines.get(j))) {
                    n -= KeyCodes.cells(shown);
                    if (n < 0) break;
                    line.append(shown);
                }
            }
            if (n > 1 && c.type() == Registers.Type.LINE) line.append("^J");
            editor.say(line.toString());
        }
        String inserted = registers.lastInserted();
        if (!inserted.isEmpty() && (arg == null || arg.indexOf('.') >= 0)) {
            String text = inserted.endsWith("\033") ? inserted.substring(0, inserted.length() - 1) : inserted;
            editor.say("  c  \".   " + fit(text, columns - 6, false));
        }
        var command = registers.get(':');
        if (command.isPresent() && (arg == null || arg.indexOf(':') >= 0)) editor.say("  c  \":   " + fit(command.get().text(), columns - 6, false));
        String name = editor.documentName();
        if (!name.equals("[No Name]") && (arg == null || arg.indexOf('%') >= 0)) editor.say("  c  \"%   " + fit(name, columns - 6, false));
        String pattern = editor.vim().search().pattern();
        if (pattern != null && (arg == null || arg.indexOf('/') >= 0)) editor.say("  c  \"/   " + fit(pattern, columns - 6, false));
    }

    /** ex_nohlsearch: the 'hlsearch' matches are hidden until the next search. */
    static void nohlsearch(ExCommand cmd) { cmd.editor.vim().search().hideHighlight(); }

    /** {@code :set}, {@code :setlocal} and {@code :setglobal}. */
    static void set(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Options.Scope scope = switch (cmd.name()) {
            case "setlocal" -> Options.Scope.LOCAL;
            case "setglobal" -> Options.Scope.GLOBAL;
            default -> Options.Scope.BOTH;
        };
        List<String> shown = new ArrayList<>();
        try {
            editor.options().set(cmd.arg, scope, editor.view().width(), shown);
        } catch (IllegalArgumentException bad) {
            shown.forEach(editor::say);
            throw new VimFailure(bad.getMessage());
        }
        shown.forEach(editor::say);
    }
}
