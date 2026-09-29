package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;

/**
 * The exchange extension (spec 8), ported from Tom McDonald's exchange.vim: {@code cx{motion}} marks some text (shown
 * highlighted) and a second {@code cx{motion}} swaps it with that; {@code cxx} takes lines, Visual {@code X} the
 * area, and {@code cxc} forgets the first. As the plugin does, it puts each text over the other with
 * {@code `[v`]"zp}, re-indents whole lines to each other's indent, and leaves the cursor where the plugin does.
 */
final class Exchange {
    private Exchange() { }

    private static final String OPTION = "exchange";

    /**
     * Some text an exchange takes: as the plugin's dictionary, its text, type ({@code v}, {@code V} or CTRL-V) and
     * ends, 0-based; a linewise one ends past its last line's last character.
     */
    record Region(String text, String type, Pos start, Pos end) { }

    static void register(VimCommands commands) {
        commands.operator("<Plug>(Exchange)", "mark {motion}'s text for an exchange, or swap it with the text marked (`cx`, with `set exchange`)",
            (c, span) -> {
                Extensions.require(c, OPTION);
                set(c, Script.operatorMarks(c, span), false);
            });
        commands.action("<Plug>(ExchangeLine)", "exchange [count] lines (`cxx`)", true, c -> {
            Extensions.require(c, OPTION);
            if (!Script.normal(c.editor, (c.hasCount ? String.valueOf(c.count) : "") + "<Plug>(Exchange)_")) throw Context.fail();
        });
        commands.action("<Plug>(ExchangeClear)", "forget the text marked for an exchange (`cxc`)", false, c -> {
            Extensions.require(c, OPTION);
            clear(c.editor);
        });
        commands.visual("<Plug>(Exchange)", "mark the Visual area for an exchange, or swap it (`X`)", false, true, c -> {
            Extensions.require(c, OPTION);
            Mode mode = c.editor.mode();
            Visual.end(c.editor);
            set(c, mode == Mode.VISUAL_LINE ? "V" : mode == Mode.VISUAL_BLOCK ? "\u0016" : "v", true);
        });
    }

    /** s:exchange_set: the first text is kept (and highlighted); the second is swapped with it. */
    private static void set(Context c, String type, boolean visual) {
        VimEditor editor = c.editor;
        Region first = editor.exchange();
        if (first == null) {
            editor.exchange(get(editor, type, visual));
            return;
        }
        Region x = first, y = get(editor, type, visual);
        boolean reverse = false, expand = false;
        switch (compare(x, y)) {
            case "overlap" -> {
                editor.say("Exchange aborted: overlapping text");
                clear(editor);
                return;
            }
            case "outer" -> {
                expand = true;
                reverse = true;
                Region swap = x;
                x = y;
                y = swap;
            }
            case "inner" -> expand = true;
            case "gt" -> {
                reverse = true;
                Region swap = x;
                x = y;
                y = swap;
            }
            default -> { }
        }
        exchange(editor, x, y, reverse, expand);
        clear(editor);
    }

    /** s:exchange_clear. */
    private static void clear(VimEditor editor) { editor.exchange(null); }

    /** s:exchange_get: the text yanked, with the unnamed and clipboard registers put back. */
    private static Region get(VimEditor editor, String type, boolean visual) {
        Saved saved = Saved.of(editor, '"', '*', '+');
        try {
            Pos start, end;
            String kind;
            if (visual) {
                kind = type;
                start = MarkCommands.get(editor, '<');
                end = MarkCommands.get(editor, '>');
                Script.normal(editor, "gvy");
            } else {
                start = editor.marks().get('[');
                end = editor.marks().get(']');
                switch (type) {
                    case "line" -> {
                        kind = "V";
                        Script.normal(editor, "'[V']y");
                    }
                    case "block" -> {
                        kind = "\u0016";
                        Script.normal(editor, "`[<C-v>`]y");
                    }
                    default -> {
                        kind = "v";
                        Script.normal(editor, "`[v`]y");
                    }
                }
            }
            String text = Script.getreg(editor, '"');
            // s:apply_type: a linewise region ends past its last line (col('$')).
            if (kind.equals("V")) end = new Pos(end.line, editor.buffer().line(end.line).length());
            if (kind.equals("V")) start = new Pos(start.line, Math.min(start.col, Math.max(0, editor.buffer().line(start.line).length())));
            return new Region(text, kind, start.copy(), end.copy());
        } finally {
            saved.restore(editor);
        }
    }

    /** s:exchange: y becomes x's text and (unless x holds y) x becomes y's; whole lines keep each other's indent. */
    private static void exchange(VimEditor editor, Region x, Region y, boolean reverse, boolean expand) {
        Saved saved = Saved.of(editor, 'z', '"', '*', '+');
        Buffer b = editor.buffer();
        try {
            Object setting = editor.vim().variables().getOrDefault("exchange_indent", 1L);
            boolean indent = !setting.toString().equals("0") && x.type().equals("V") && y.type().equals("V");
            String xindent = "", yindent = "";
            if (indent) {
                xindent = leadingWhite(b.line(nextnonblank(b, y.start().line)));
                yindent = leadingWhite(b.line(nextnonblank(b, x.start().line)));
            }
            Pos view = editor.cursor();
            putOver(editor, y, x);
            if (!expand) putOver(editor, x, y);
            if (indent) {
                int xlines = 1 + x.end().line - x.start().line;
                int ylines = expand ? xlines : 1 + y.end().line - y.start().line;
                if (!expand) reindent(editor, x.start().line, ylines, yindent);
                reindent(editor, y.start().line - xlines + ylines, xlines, xindent);
            }
            // winrestview()
            editor.setCursor(clamp(b, view));
            if (!expand) fixCursor(editor, x, y, reverse);
        } finally {
            saved.restore(editor);
        }
    }

    /** {@code `[{type}`]"zp}: the region's text replaced by {@code with}'s. */
    private static void putOver(VimEditor editor, Region region, Region with) {
        editor.marks().set('[', region.start());
        editor.marks().set(']', region.end());
        Script.setreg(editor, 'z', with.text(), with.type());
        String type = region.type().equals("\u0016") ? "<C-v>" : region.type();
        Script.normal(editor, "`[" + type + "`]\"zp");
    }

    /** s:fix_cursor: the cursor at the start of the text that moved, allowing for how its size changed. */
    private static void fixCursor(VimEditor editor, Region x, Region y, boolean reverse) {
        Buffer b = editor.buffer();
        if (reverse) editor.setCursor(clamp(b, x.start()));
        else if (x.start().line == y.start().line) {
            int offset = x.end().col - y.end().col;
            editor.setCursor(clamp(b, new Pos(x.start().line, x.start().col - offset)));
        } else if (x.end().line - x.start().line != y.end().line - y.start().line) {
            int offset = x.end().line - y.end().line;
            editor.setCursor(clamp(b, new Pos(x.start().line - offset, x.start().col)));
        }
    }

    /** s:reindent: the lines get {@code newIndent} in place of the indent of the first that is not blank. */
    private static void reindent(VimEditor editor, int start, int lines, String newIndent) {
        Buffer b = editor.buffer();
        int ts = editor.options().number("tabstop");
        String indent = leadingWhite(b.line(nextnonblank(b, start)));
        int want = Text.virtcol(newIndent, newIndent.length(), ts), have = Text.virtcol(indent, indent.length(), ts);
        int last = Math.min(b.count() - 1, start + lines - 1);
        if (want > have) {
            for (int n = start; n <= last; n++) b.setLine(n, newIndent + b.line(n).substring(Math.min(indent.length(), b.line(n).length())));
        } else if (want < have) {
            boolean canDedent = true;
            for (int n = start; n <= last; n++) if (!b.line(n).startsWith(newIndent) && nextnonblank(b, n) == n) canDedent = false;
            if (canDedent)
                for (int n = start; n <= last; n++)
                    if (b.line(n).startsWith(newIndent)) b.setLine(n, newIndent + b.line(n).substring(Math.min(indent.length(), b.line(n).length())));
        }
    }

    /** s:compare: {@code lt}, {@code gt}, {@code outer}, {@code inner} or {@code overlap}. */
    private static String compare(Region x, Region y) {
        if (x.type().equals("\u0016") && y.type().equals("\u0016")) {
            if (intersects(x, y)) return "overlap";
            return x.start().col - y.start().col <= 0 ? "lt" : "gt";
        }
        if (x.start().compareTo(y.start()) <= 0 && x.end().compareTo(y.end()) >= 0) return "outer";
        if (y.start().compareTo(x.start()) <= 0 && y.end().compareTo(x.end()) >= 0) return "inner";
        if (x.start().compareTo(y.end()) <= 0 && y.start().compareTo(x.end()) <= 0) return "overlap";
        int cmp = x.start().compareTo(y.start());
        return cmp == 0 ? "overlap" : cmp < 0 ? "lt" : "gt";
    }

    private static boolean intersects(Region x, Region y) {
        return !(x.end().col < y.start().col || x.end().line < y.start().line || x.start().col > y.end().col || x.start().line > y.end().line);
    }

    /** nextnonblank(): the first line from {@code line} on with a non-blank character, or the line itself when none has. */
    private static int nextnonblank(Buffer b, int line) {
        for (int n = line; n < b.count(); n++) if (!b.line(n).isBlank()) return n;
        return Math.min(line, b.count() - 1);
    }

    private static String leadingWhite(String text) {
        int i = 0;
        while (i < text.length() && (text.charAt(i) == ' ' || text.charAt(i) == '\t')) i++;
        return text.substring(0, i);
    }

    private static Pos clamp(Buffer b, Pos p) {
        int line = Math.max(0, Math.min(p.line, b.count() - 1));
        String text = b.line(line);
        return new Pos(line, Math.max(0, Math.min(p.col, text.isEmpty() ? 0 : Text.last(text))));
    }

    /** The first text marked, highlighted as the plugin's matchadd() does: its lines and columns as offsets. */
    static List<int[]> highlight(VimEditor editor, Region region) {
        List<int[]> out = new ArrayList<>();
        if (region == null) return out;
        Buffer b = editor.buffer();
        int count = b.count();
        if (region.start().line >= count) return out;
        int last = Math.min(region.end().line, count - 1);
        switch (region.type()) {
            case "V" -> out.add(new int[] { 1, b.start(region.start().line), b.end(last) + 1 });
            case "v" -> {
                String end = b.line(last);
                int to = region.end().col < end.length() ? b.offset(last, Text.next(end, region.end().col)) : b.end(last);
                out.add(new int[] { 1, b.offset(region.start().line, Math.min(region.start().col, b.line(region.start().line).length())), to });
            }
            default -> {
                int left = Math.min(region.start().col, region.end().col), right = Math.max(region.start().col, region.end().col);
                for (int line = region.start().line; line <= last; line++) {
                    String text = b.line(line);
                    if (left >= text.length()) continue;
                    out.add(new int[] { 1, b.offset(line, left), b.offset(line, Math.min(text.length(), Text.next(text, Math.min(right, text.length() - 1)))) });
                }
            }
        }
        return out;
    }

    /** s:save_reg and s:restore_reg for a few registers; an empty register is left alone. */
    private record Saved(List<Character> names, List<String> texts, List<String> types) {
        static Saved of(VimEditor editor, char... names) {
            List<Character> n = new ArrayList<>();
            List<String> t = new ArrayList<>(), ty = new ArrayList<>();
            for (char name : names) {
                n.add(name);
                t.add(Script.getreg(editor, name));
                ty.add(Script.regtype(editor, name));
            }
            return new Saved(n, t, ty);
        }

        void restore(VimEditor editor) {
            for (int i = 0; i < names.size(); i++) if (!types.get(i).isEmpty()) Script.setreg(editor, names.get(i), texts.get(i), types.get(i));
        }
    }
}
