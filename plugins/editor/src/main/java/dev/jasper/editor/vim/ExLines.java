package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Ex commands that change lines: {@code :d}, {@code :y}, {@code :>} and {@code :<} (ex_operators), {@code :put},
 * {@code :join}, {@code :move} and {@code :copy} (ex_copymove, do_move, ex_copy), {@code :normal}, {@code :sort},
 * {@code :left}, {@code :right} and {@code :center} (ex_align), {@code :undo} and {@code :redo}. Ported from
 * ex_docmd.c and ex_cmds.c; the operators and put are the Normal-mode ones.
 */
final class ExLines {
    private ExLines() { }

    /** ex_operators: {@code :d}, {@code :y}, {@code :>} and {@code :<} on whole lines. */
    static void operators(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Context c = cmd.context();
        Span span = new Span(new Pos(cmd.line1 - 1, 0), new Pos(cmd.line2 - 1, 0), Target.Kind.LINEWISE);
        if (!cmd.name().equals("yank")) {
            editor.ex().setpcmark();
            editor.ex().beginline(cmd.line1, true);
            Pos p = editor.cursor();
            editor.area().vimUndo().stepCursor(p.line, p.col);
        }
        switch (cmd.name()) {
            case "delete" -> Operators.delete(c, span, false);
            case "yank" -> Operators.yank(c, span);
            default -> Operators.shift(c, span, cmd.name().equals("<"), cmd.amount, false);
        }
        setOperatorMarks(editor, cmd);
        ExInfo.mayPrint(cmd);
    }

    /** The {@code '[} and {@code ']} marks after a linewise operator, as op_delete, op_yank and op_shift set them. */
    private static void setOperatorMarks(VimEditor editor, ExCommand cmd) {
        Marks marks = editor.marks();
        Buffer b = editor.buffer();
        switch (cmd.name()) {
            case "delete" -> {
                Pos at = new Pos(Math.min(cmd.line1 - 1, b.count() - 1), 0);
                marks.set('[', at);
                marks.set(']', at);
            }
            case "yank" -> {
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(cmd.line2 - 1, Visual.MAX));
            }
            default -> {
                String last = b.line(cmd.line2 - 1);
                marks.set('[', new Pos(cmd.line1 - 1, 0));
                marks.set(']', new Pos(cmd.line2 - 1, last.isEmpty() ? 0 : Text.last(last)));
            }
        }
    }

    /** ex_put: the register's text as lines below the line ({@code !}: above); {@code :0put} puts above the first. */
    static void put(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        int line = cmd.line2;
        boolean above = cmd.forceit;
        if (line == 0) {
            line = 1;
            above = true;
        }
        editor.setCursor(new Pos(line - 1, editor.cursor().col));
        Put.put(cmd.context(), !above, 1, Put.LINE | Put.CURSLINE, -1);
    }

    /** ex_join: the range's lines, at least two; with {@code !} without inserting spaces. */
    static void join(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        editor.setCursor(new Pos(cmd.line1 - 1, 0));
        if (cmd.line1 == cmd.line2) {
            if (cmd.addrCount >= 2) return;
            if (cmd.line2 == b.count()) throw VimFailure.bell();
            cmd.line2++;
        }
        NormalCommands.joinLines(cmd.context(), cmd.line1 - 1, cmd.line2 - cmd.line1 + 1, !cmd.forceit);
        editor.ex().beginline(editor.cursor().line + 1, false);
        ExInfo.mayPrint(cmd);
    }

    /** ex_copymove: {@code :move}, {@code :copy} and {@code :t} to below the address in the argument. */
    static void copyMove(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int[] at = { 0 };
        int n = editor.ex().address(cmd.arg, at);
        String rest = cmd.arg.substring(at[0]);
        int f = 0;
        while (f < rest.length() && "lp#".indexOf(rest.charAt(f)) >= 0) {
            char flag = rest.charAt(f);
            cmd.flags |= flag == 'l' ? Ex.LIST : flag == 'p' ? Ex.PRINT : Ex.NUMBER;
            f = Ex.skipWhite(rest, f + 1);
        }
        if (n == Ex.NO_ADDRESS || n < 0 || n > b.count()) throw new VimFailure("E16: Invalid range");
        if (cmd.name().equals("move")) move(editor, cmd.line1, cmd.line2, n);
        else copy(editor, cmd.line1, cmd.line2, n);
        editor.ex().beginline(editor.cursor().line + 1, true);
        ExInfo.mayPrint(cmd);
    }

    /** do_move: lines {@code line1}..{@code line2} to below line {@code dest}, their marks with them. */
    private static void move(VimEditor editor, int line1, int line2, int dest) {
        Buffer b = editor.buffer();
        if (dest >= line1 && dest < line2) throw new VimFailure("E134: Cannot move a range of lines into itself");
        int num = line2 - line1 + 1;
        if (dest == line1 - 1 || dest == line2) {
            // Nothing moves, but the cursor goes where it would have.
            int to = dest >= line1 ? dest : dest + num;
            editor.setCursor(new Pos(Math.max(0, to - 1), 0));
            return;
        }
        List<String> lines = b.lines(line1 - 1, line2 - 1);
        Marks marks = editor.marks();
        editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
        if (dest >= line2) {
            marks.moving(line1 - 1, line2 - 1, dest - num, () -> {
                b.deleteLines(line1 - 1, line2 - 1);
                b.insertLines(dest - num, lines);
            });
            marks.set('[', new Pos(dest - num, 0));
            marks.set(']', new Pos(dest - 1, 0));
        } else {
            marks.moving(line1 - 1, line2 - 1, dest, () -> {
                b.insertLines(dest, lines);
                b.deleteLines(line1 - 1 + num, line2 - 1 + num);
            });
            marks.set('[', new Pos(dest, 0));
            marks.set(']', new Pos(dest + num - 1, 0));
        }
        marks.set('.', new Pos(Math.min(line1, dest + 1) - 1, 0));
        if (!editor.ex().globalBusy() && num > editor.options().number("report"))
            editor.say(num + (num == 1 ? " line moved" : " lines moved"));
        int to = dest >= line1 ? dest : dest + num;
        editor.setCursor(new Pos(to - 1, 0));
    }

    /** ex_copy: a copy of lines {@code line1}..{@code line2} below line {@code n}. */
    private static void copy(VimEditor editor, int line1, int line2, int n) {
        Buffer b = editor.buffer();
        int count = line2 - line1 + 1;
        List<String> lines = b.lines(line1 - 1, line2 - 1);
        editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
        b.insertLines(n, lines);
        Marks marks = editor.marks();
        marks.set('[', new Pos(n, 0));
        marks.set(']', new Pos(n + count - 1, 0));
        editor.setCursor(new Pos(n + count - 1, 0));
        editor.msgmore(count);
    }

    /**
     * ex_normal: the argument's characters run as Normal-mode keys, on each line of the range from its start, or
     * once. A command left incomplete is ended as with {@code <Esc>}, as Vim's :normal gets <Esc> when it runs out.
     * A failing command drops the rest of the keys.
     */
    static void normal(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Ex ex = editor.ex();
        if (ex.normalDepth >= 1000) throw new VimFailure("E192: Recursive use of :normal too deep");
        List<KeyInput> keys = KeyCodes.keys(cmd.arg);
        ex.normalDepth++;
        try {
            int line = cmd.line1;
            do {
                if (cmd.addrCount != 0) {
                    editor.setCursor(new Pos(line - 1, 0));
                    editor.forgetColumn();
                    line++;
                }
                run(editor, keys, !cmd.forceit);
            } while (cmd.addrCount > 0 && line <= cmd.line2);
        } finally {
            ex.normalDepth--;
        }
    }

    /**
     * The keys run with the typeahead saved (mapped unless {@code :normal!}); a failure drops the rest of them.
     * Out of keys, whatever still waits for one gets {@code <Esc>}.
     */
    private static void run(VimEditor editor, List<KeyInput> keys, boolean remap) {
        VimFailure failed = editor.typeahead().execute(keys, remap);
        if (failed != null) {
            if (!failed.quiet) editor.bell();
            if (!failed.getMessage().isEmpty()) editor.message(failed.getMessage(), true);
            if (!editor.mode().inserting()) {
                editor.machine().reset();
                editor.machine().resumeIfInsertNormal();
            }
        }
        endPending(editor);
        if (failed != null && !failed.getMessage().isEmpty()) throw VimFailure.quiet();
    }

    /** Out of keys ({@code :normal}'s, or a ported plugin's): whatever still waits for one gets {@code <Esc>}. */
    static void endPending(VimEditor editor) {
        for (int n = 0; n < 100 && pending(editor); n++) {
            try {
                editor.dispatch(KeyInput.named("Esc"));
            } catch (VimFailure ignored) {
                editor.machine().reset();
            }
        }
    }

    private static boolean pending(VimEditor editor) {
        return editor.prompting() || editor.commandLine().active() || editor.mode().inserting() || editor.machine().pending()
            || editor.mode() == Mode.INSERT_NORMAL;
    }

    /** ex_sort: the range's lines sorted by their text, a number in them ({@code n x o b f}), or after or at a pattern. */
    static void sort(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int count = cmd.line2 - cmd.line1 + 1;
        if (count <= 1) return;
        boolean ic = false, rx = false, unique = false, flt = false, nr = false;
        int radix = 0, formats = 0;
        VimRegex regex = null;
        String arg = cmd.arg;
        for (int p = 0; p < arg.length(); p++) {
            char ch = arg.charAt(p);
            if (Ex.isWhite(ch)) continue;
            switch (ch) {
                case 'i' -> ic = true;
                case 'l' -> { }
                case 'r' -> rx = true;
                case 'n' -> { nr = true; formats++; }
                case 'f' -> { flt = true; formats++; }
                case 'b' -> { radix = 2; formats++; }
                case 'o' -> { radix = 8; formats++; }
                case 'x' -> { radix = 16; formats++; }
                case 'u' -> unique = true;
                default -> {
                    if (ch == '"') {
                        p = arg.length();
                        continue;
                    }
                    int next = nextCommand(arg, p);
                    if (next >= 0 && cmd.nextcmd == null) {
                        cmd.nextcmd = arg.substring(next);
                        p = arg.length();
                        continue;
                    }
                    if (!(ch < 128 && Character.isLetter(ch)) && regex == null) {
                        int end = p + 1 + Search.skipPattern(arg.substring(p + 1), ch);
                        String pat = arg.substring(p + 1, Math.min(end, arg.length()));
                        if (end >= arg.length() || arg.charAt(end) != ch) throw new VimFailure("E654: Missing delimiter after search pattern: " + arg.substring(p));
                        Search search = editor.vim().search();
                        if (pat.isEmpty()) {
                            pat = search.pattern();
                            if (pat == null) throw new VimFailure("E35: No previous regular expression");
                        }
                        regex = compile(editor, pat);
                        p = end;
                        continue;
                    }
                    throw new VimFailure("E475: Invalid argument: " + arg.substring(p));
                }
            }
        }
        if (formats > 1) throw new VimFailure("E474: Invalid argument");
        boolean number = nr || radix != 0;
        record Item(int lnum, String key, boolean isNumber, long value, double real) { }
        List<Item> items = new ArrayList<>();
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) {
            String s = b.line(lnum - 1);
            int start = 0, end = s.length();
            if (regex != null) {
                Matcher m = regex.pattern().matcher(s + "\n");
                if (m.find() && m.start() <= s.length()) {
                    int ms = Math.min(regex.start(m), s.length()), me = Math.min(regex.end(m), s.length());
                    if (rx) {
                        start = ms;
                        end = me;
                    } else start = me;
                } else end = 0;
            }
            String part = s.substring(Math.min(start, end), end);
            if (number) {
                long[] parsed = number(part, radix);
                items.add(new Item(lnum, null, parsed != null, parsed == null ? 0 : parsed[0], 0));
            } else if (flt) items.add(new Item(lnum, null, false, 0, real(part)));
            else items.add(new Item(lnum, part, false, 0, 0));
        }
        boolean ignoreCase = ic;
        Comparator<Item> order;
        if (number) order = (x, y) -> x.isNumber() != y.isNumber() ? (x.isNumber() ? 1 : -1) : Long.compare(x.value(), y.value());
        else if (flt) order = (x, y) -> Double.compare(x.real(), y.real()) == 0 ? 0 : x.real() > y.real() ? 1 : -1;
        else order = (x, y) -> compare(x.key(), y.key(), ignoreCase);
        order = order.thenComparingInt(Item::lnum);
        List<Item> sorted = new ArrayList<>(items);
        sorted.sort(order);
        List<String> lines = new ArrayList<>();
        String previous = null;
        for (int i = 0; i < count; i++) {
            Item item = sorted.get(cmd.forceit ? count - i - 1 : i);
            String s = b.line(item.lnum() - 1);
            if (!unique || i == 0 || compare(s, previous, ignoreCase) != 0) {
                lines.add(s);
                previous = s;
            }
        }
        List<String> old = b.lines(cmd.line1 - 1, cmd.line2 - 1);
        if (!lines.equals(old)) {
            editor.area().vimUndo().stepCursor(editor.cursor().line, editor.cursor().col);
            replaceLines(b, cmd.line1 - 1, old, lines);
        }
        int deleted = count - lines.size();
        if (deleted > 0) editor.msgmore(-deleted);
        editor.ex().beginline(cmd.line1, false);
    }

    /** Replaces lines {@code first}.. (which were {@code old}) with {@code lines}, changing only the lines that differ. */
    private static void replaceLines(Buffer b, int first, List<String> old, List<String> lines) {
        int common = Math.min(old.size(), lines.size());
        for (int i = 0; i < common; i++) b.setLine(first + i, lines.get(i));
        if (old.size() > lines.size()) b.deleteLines(first + lines.size(), first + old.size() - 1);
        else if (lines.size() > old.size()) b.insertLines(first + old.size(), lines.subList(old.size(), lines.size()));
    }

    /** STRCMP or STRICMP: by code point (UTF-8 byte order), folding ASCII letters for {@code i}. */
    private static int compare(String a, String b, boolean ignoreCase) {
        int i = 0, j = 0;
        while (i < a.length() && j < b.length()) {
            int x = a.codePointAt(i), y = b.codePointAt(j);
            if (ignoreCase) {
                if (x < 128) x = Character.toLowerCase(x);
                if (y < 128) y = Character.toLowerCase(y);
            }
            if (x != y) return Integer.compare(x, y);
            i += Character.charCount(a.codePointAt(i));
            j += Character.charCount(b.codePointAt(j));
        }
        return Boolean.compare(i < a.length(), j < b.length());
    }

    /**
     * The number {@code :sort n} (decimal), {@code x}, {@code o} or {@code b} sorts on: the first digit and what
     * follows, with a {@code -} before it.
     *
     * @return {value}, or null when the text has no number
     */
    private static long[] number(String s, int radix) {
        String digits = radix == 16 ? "0123456789abcdefABCDEF" : radix == 2 ? "01" : "0123456789";
        int i = 0;
        while (i < s.length() && digits.indexOf(s.charAt(i)) < 0) i++;
        if (i >= s.length()) return null;
        boolean negative = i > 0 && s.charAt(i - 1) == '-';
        // A 0x or 0b prefix before hex or binary digits is skipped.
        char prefix = radix == 16 ? 'x' : radix == 2 ? 'b' : 0;
        if (prefix != 0 && s.charAt(i) == '0' && i + 2 < s.length() && Character.toLowerCase(s.charAt(i + 1)) == prefix
            && digits.indexOf(s.charAt(i + 2)) >= 0) i += 2;
        String allowed = radix == 8 ? "01234567" : digits;
        int base = radix == 0 ? 10 : radix;
        long value = 0;
        while (i < s.length() && allowed.indexOf(s.charAt(i)) >= 0) {
            int d = Character.digit(s.charAt(i++), base);
            value = value > (Long.MAX_VALUE - d) / base ? Long.MAX_VALUE : value * base + d;
        }
        return new long[] { negative ? -value : value };
    }

    /** strtod after white space and a {@code +}; an empty text sorts before every number. */
    private static double real(String s) {
        String t = s.stripLeading();
        if (t.startsWith("+")) t = t.substring(1).stripLeading();
        if (t.isEmpty()) return -Double.MAX_VALUE;
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("^[-]?(?:\\d+\\.?\\d*(?:[eE][-+]?\\d+)?|\\.\\d+(?:[eE][-+]?\\d+)?|inf(?:inity)?|nan)", java.util.regex.Pattern.CASE_INSENSITIVE).matcher(t);
        if (!m.find()) return 0;
        String n = m.group().toLowerCase(java.util.Locale.ROOT);
        if (n.contains("inf")) return n.startsWith("-") ? Double.NEGATIVE_INFINITY : Double.POSITIVE_INFINITY;
        if (n.contains("nan")) return Double.NaN;
        return Double.parseDouble(n);
    }

    private static VimRegex compile(VimEditor editor, String pat) {
        Options o = editor.options();
        try {
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), false, o.keywords().javaClass(), editor.vim().search().prevSub));
        } catch (IllegalArgumentException bad) {
            throw new VimFailure(bad.getMessage());
        }
    }

    private static int nextCommand(String s, int i) {
        int j = Ex.skipWhite(s, i);
        return j < s.length() && s.charAt(j) == '|' ? j + 1 : -1;
    }

    /**
     * ex_align: {@code :left [indent]}, {@code :right [width]} and {@code :center [width]}; the width defaults to
     * 'textwidth', or 80. Blank lines are left alone by {@code :right} and {@code :center}.
     */
    static void align(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        boolean et = editor.options().flag("expandtab");
        int width = atoi(cmd.arg), indent = 0;
        boolean left = cmd.name().equals("left"), right = cmd.name().equals("right");
        if (left) {
            if (width >= 0) indent = width;
        } else {
            if (width <= 0) width = editor.options().number("textwidth");
            if (width <= 0) width = 80;
        }
        Pos saved = editor.cursor();
        editor.area().vimUndo().stepCursor(saved.line, saved.col);
        for (int lnum = cmd.line1; lnum <= cmd.line2; lnum++) {
            String line = b.line(lnum - 1);
            int newIndent;
            if (left) newIndent = indent;
            else {
                int len = lineLength(line, ts) - Text.indent(line, ts);
                if (len <= 0) continue;
                if (!right) newIndent = (width - len) / 2;
                else {
                    newIndent = width - len;
                    if (hasTab(line)) {
                        while (newIndent > 0) {
                            if (lineLength(indented(line, newIndent, ts, et), ts) <= width) {
                                do newIndent++;
                                while (lineLength(indented(line, newIndent, ts, et), ts) <= width);
                                newIndent--;
                                break;
                            }
                            newIndent--;
                        }
                    }
                }
            }
            b.setLine(lnum - 1, indented(line, Math.max(0, newIndent), ts, et));
        }
        editor.setCursor(saved);
        editor.ex().beginline(saved.line + 1, false);
    }

    private static String indented(String line, int indent, int ts, boolean et) {
        return Text.makeIndent(indent, ts, et) + line.substring(Text.firstNonBlank(line));
    }

    /** linelen: the display width of the line without trailing white space. */
    private static int lineLength(String line, int ts) {
        int last = line.length();
        while (last > 0 && Ex.isWhite(line.charAt(last - 1))) last--;
        return Text.virtcol(line.substring(0, last), last, ts);
    }

    private static boolean hasTab(String line) {
        int last = line.length();
        while (last > 0 && Ex.isWhite(line.charAt(last - 1))) last--;
        return line.substring(Text.firstNonBlank(line), Math.max(Text.firstNonBlank(line), last)).indexOf('\t') >= 0;
    }

    /** atoi: leading white space, a sign and digits; 0 when there are none. */
    private static int atoi(String s) {
        int i = Ex.skipWhite(s, 0);
        boolean negative = false;
        if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) negative = s.charAt(i++) == '-';
        long v = 0;
        while (i < s.length() && Character.isDigit(s.charAt(i))) v = Math.min(Integer.MAX_VALUE, v * 10 + s.charAt(i++) - '0');
        return (int) (negative ? -v : v);
    }

    /** ex_undo: {@code :undo} undoes one change; {@code :undo N} goes to the text after change N (0: before all). */
    static void undo(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Pos before = editor.cursor();
        if (cmd.addrCount == 1) editor.undoTo(cmd.line2);
        else editor.undo(1);
        editor.machine().foldOpenAfter("undo", before);
    }

    /** ex_redo. */
    static void redo(ExCommand cmd) {
        VimEditor editor = cmd.editor;
        Pos before = editor.cursor();
        editor.redo(1);
        editor.machine().foldOpenAfter("undo", before);
    }
}
