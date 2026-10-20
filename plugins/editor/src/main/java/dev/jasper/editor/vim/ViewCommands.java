package dev.jasper.editor.vim;

import java.util.List;

/**
 * Scrolling, the screen-line motions and the fold commands (spec 4.1), ported from Vim's normal.c (nv_scroll_line,
 * nv_halfpage, nv_page, nv_zet, nv_scroll, nv_screengo and the "g" screen-line commands, nv_brackets for [z and
 * ]z). They work on {@link View} and {@link Folds}.
 */
final class ViewCommands {
    private ViewCommands() { }

    static void register(VimCommands commands) {
        scrolling(commands, false);
        scrolling(commands, true);
        for (String keys : List.of("zt", "z<CR>", "zz", "z.", "zb", "z-", "z+", "z^")) {
            commands.action(keys, zDescription(keys), false, c -> zet(c, keys.substring(1)));
        }
        commands.motion("H", "to line {count} from the top of the window", c -> hml(c, 'H'));
        commands.motion("M", "to the middle line of the window", c -> hml(c, 'M'));
        commands.motion("L", "to line {count} from the bottom of the window", c -> hml(c, 'L'));
        commands.motion("gj", "down {count} screen lines", c -> screenLines(c, true));
        commands.motion("gk", "up {count} screen lines", c -> screenLines(c, false));
        commands.motion("g<Down>", "down {count} screen lines", c -> screenLines(c, true));
        commands.motion("g<Up>", "up {count} screen lines", c -> screenLines(c, false));
        commands.motion("g0", "to the first character of the screen line", c -> homeOfScreenLine(c, '0'));
        commands.motion("g<Home>", "to the first character of the screen line", c -> homeOfScreenLine(c, '0'));
        commands.motion("g^", "to the first non-blank of the screen line", c -> homeOfScreenLine(c, '^'));
        commands.motion("gm", "to the middle of the screen line", c -> homeOfScreenLine(c, 'm'));
        commands.motion("g$", "to the last character of the screen line", c -> endOfScreenLine(c, false));
        commands.motion("g<End>", "to the last non-blank of the screen line", c -> endOfScreenLine(c, true));
        commands.action("zo", "open {count} levels of folds at the cursor", false, c -> c.editor.folds().repeat(line(c), c.count, true));
        commands.action("zO", "open every fold at the cursor", false, c -> c.editor.folds().recurse(line(c), true));
        commands.action("zc", "close {count} levels of folds at the cursor", false, c -> c.editor.folds().repeat(line(c), c.count, false));
        commands.action("zC", "close every fold at the cursor", false, c -> c.editor.folds().recurse(line(c), false));
        commands.action("za", "open a closed fold, or close an open one", false, c -> toggle(c, false));
        commands.action("zA", "open or close every fold at the cursor", false, c -> toggle(c, true));
        commands.action("zv", "open the folds that hide the cursor line", false, c -> c.editor.folds().openCursor(line(c)));
        commands.action("zR", "open every fold", false, c -> c.editor.folds().all(true));
        commands.action("zM", "close every fold", false, c -> c.editor.folds().all(false));
        commands.motion("zj", "to the start of the next fold", c -> foldMove(c, true, true));
        commands.motion("zk", "to the end of the previous fold", c -> foldMove(c, true, false));
        commands.motion("[z", "to the start of the open fold", c -> foldMove(c, false, false));
        commands.motion("]z", "to the end of the open fold", c -> foldMove(c, false, true));
    }

    private static String zDescription(String keys) {
        return switch (keys) {
            case "zt" -> "line {count} (default the cursor line) to the top of the window";
            case "z<CR>" -> "as zt, the cursor on the first non-blank";
            case "zz" -> "line {count} to the middle of the window";
            case "z." -> "as zz, the cursor on the first non-blank";
            case "zb" -> "line {count} to the bottom of the window";
            case "z-" -> "as zb, the cursor on the first non-blank";
            case "z+" -> "the line below the window to the top (line {count} with a count)";
            default -> "the line above the window to the bottom (line {count} with a count)";
        };
    }

    /** The scroll keys, in Normal mode or (with {@code visual}) extending a Visual area. */
    private static void scrolling(VimCommands commands, boolean visual) {
        add(commands, visual, "<C-e>", "scroll the text up {count} lines", c -> c.editor.view().scrollRedraw(true, c.count));
        add(commands, visual, "<C-y>", "scroll the text down {count} lines", c -> c.editor.view().scrollRedraw(false, c.count));
        add(commands, visual, "<C-d>", "scroll down half a window ({count} sets 'scroll'), the cursor too", c -> page(c, true, true));
        add(commands, visual, "<C-u>", "scroll up half a window ({count} sets 'scroll'), the cursor too", c -> page(c, false, true));
        for (String keys : List.of("<C-f>", "<PageDown>", "<S-Down>"))
            add(commands, visual, keys, "scroll down {count} pages", c -> page(c, true, false));
        for (String keys : List.of("<C-b>", "<PageUp>", "<S-Up>"))
            add(commands, visual, keys, "scroll up {count} pages", c -> page(c, false, false));
    }

    private static void add(VimCommands commands, boolean visual, String keys, String description, VimCommands.Action action) {
        if (visual) commands.visual(keys, description, false, false, action);
        else commands.action(keys, description, false, action);
    }

    /** The keys that scroll without forgetting the remembered column. */
    static final List<String> KEEP_COLUMN = List.of("<C-e>", "<C-y>", "<C-d>", "<C-u>", "<C-f>", "<PageDown>", "<S-Down>",
        "<C-b>", "<PageUp>", "<S-Up>", "zt", "zz", "zb", "zo", "zO", "zc", "zC", "za", "zA", "zv", "zR", "zM");

    private static int line(Context c) { return c.cursor().line + 1; }

    /** nv_halfpage and nv_page. */
    private static void page(Context c, boolean forward, boolean half) {
        if (!c.editor.view().pagescroll(forward, half ? (c.hasCount ? c.count : 0) : c.count, half)) throw Context.fail();
    }

    /** nv_zet for zt z<CR> zz z. zb z- z+ z^: a count is a line number to put there first. */
    private static void zet(Context c, String nchar) {
        View v = c.editor.view();
        Buffer b = c.buffer;
        int count0 = c.hasCount ? c.count : 0;
        if (count0 != 0 && count0 != line(c)) {
            c.editor.marks().setpcmark(c.cursor());
            int lnum = Math.min(count0, b.count());
            c.editor.setCursor(new Pos(lnum - 1, Math.min(c.cursor().col, b.line(lnum - 1).length())));
        }
        switch (nchar) {
            case "+" -> {
                if (count0 == 0) {
                    v.compBotline();
                    int lnum = v.botline > b.count() ? b.count() : v.botline;
                    c.editor.setCursor(new Pos(lnum - 1, 0));
                }
                v.beginline(false);
                v.scrollCursorTop(0, true);
            }
            case "<CR>" -> {
                v.beginline(false);
                v.scrollCursorTop(0, true);
            }
            case "t" -> v.scrollCursorTop(0, true);
            case "." -> {
                v.beginline(false);
                v.scrollCursorHalfway(true, false);
            }
            case "z" -> v.scrollCursorHalfway(true, false);
            case "^" -> {
                if (count0 != 0) {
                    v.scrollCursorBot(0, true);
                    c.editor.setCursor(new Pos(v.topline - 1, 0));
                } else c.editor.setCursor(new Pos(v.topline == 1 ? 0 : v.topline - 2, 0));
                v.beginline(false);
                v.scrollCursorBot(0, true);
            }
            case "-" -> {
                v.beginline(false);
                v.scrollCursorBot(0, true);
            }
            default -> v.scrollCursorBot(0, true);
        }
    }

    /** nv_scroll: H, M and L; with an operator the lines are counted without 'scrolloff'. */
    private static Target hml(Context c, char which) {
        View v = c.editor.view();
        Buffer b = c.buffer;
        Pos original = c.cursor();
        int topline = v.topline;
        v.compBotline();
        int lnum;
        Folds folds = c.editor.folds();
        if (which == 'L') {
            lnum = v.botline - 1;
            if (c.count - 1 >= lnum) lnum = 1;
            else if (folds.any()) {
                for (int n = c.count - 1; n > 0 && lnum > topline; n--) {
                    int[] f = folds.closed(lnum);
                    if (f != null) lnum = f[0];
                    if (lnum > topline) lnum--;
                }
            } else lnum -= c.count - 1;
        } else {
            int n;
            if (which == 'M') {
                int half = (v.height() - v.emptyRows + 1) / 2, used = 0;
                for (n = 0; topline + n < b.count(); n++) {
                    used += v.plines(topline + n);
                    if (used >= half) break;
                    int[] f = folds.closed(topline + n);
                    if (f != null) n = f[1] - topline;
                }
                if (n > 0 && used > v.height()) n--;
            } else {
                n = c.count - 1;
                if (folds.any()) {
                    int l = topline;
                    while (n-- > 0 && l < v.botline - 1) {
                        int[] f = folds.closed(l);
                        if (f != null) l = f[1];
                        l++;
                    }
                    n = l - topline;
                }
            }
            lnum = Math.min(topline + n, b.count());
        }
        c.editor.setCursor(new Pos(lnum - 1, Math.min(original.col, b.line(lnum - 1).length())));
        if (c.operator == null) v.cursorCorrect();
        v.beginline(true);
        Pos to = c.cursor();
        c.editor.setCursor(original, true);
        boolean sol = c.options.flag("startofline");
        return Target.linewise(to, sol ? Target.Column.RESET : Target.Column.KEEP).asJump();
    }

    /** gj and gk: screen lines with 'wrap', buffer lines without (a closed fold counting as one). */
    private static Target screenLines(Context c, boolean down) {
        View v = c.editor.view();
        if (!c.options.flag("wrap")) return down ? Motions.down(c, c.count) : Motions.up(c, c.count);
        Pos original = c.cursor();
        boolean ok = v.screengo(down, c.count);
        Pos to = c.cursor();
        if (!ok) throw Context.fail();
        c.editor.setCursor(original, true);
        boolean inclusive = c.editor.curswant() == Visual.MAX;
        Target target = inclusive ? Target.inclusive(to) : Target.exclusive(to);
        return target.withColumn(Target.Column.KEEP);
    }

    /** nv_g_home_m_cmd: g0, g^ and gm. */
    private static Target homeOfScreenLine(Context c, char which) {
        View v = c.editor.view();
        Pos p = c.cursor();
        String text = c.buffer.line(p.line);
        int ts = c.tabstop();
        if(v.physical() && c.options.flag("wrap")) {
            Pos at=c.buffer.pos(v.rowPosition(c.buffer.offset(p),0,which));
            int col=at.col;
            if(which=='^') while(col<text.length() && Block.white(text.charAt(col)) && col+1<text.length()) col=Text.next(text,col);
            return Target.exclusive(new Pos(at.line,col));
        }
        int i;
        if (c.options.flag("wrap")) {
            int width1 = v.width(), width2 = v.width();
            int virtcol = Text.virtcol(text, Math.min(p.col, text.length()), ts);
            if (p.col < text.length() && text.charAt(p.col) == '\t' && !c.editor.mode().inserting())
                virtcol += Text.width(text, p.col, virtcol, ts) - 1;
            i = 0;
            if (virtcol >= width1 && width2 > 0) i = (virtcol - width1) / width2 * width2 + width1;
        } else i = v.leftcol;
        if (which == 'm') i += v.width() / 2;
        int col = columnAdvance(c, p.line, i);
        if (which == '^') {
            while (col < text.length() && Block.white(text.charAt(col)) && col + 1 < text.length()) col = Text.next(text, col);
        }
        return Target.exclusive(new Pos(p.line, col));
    }

    /** coladvance(vcol) in Normal mode: the character covering the display column, the last one past the end. */
    private static int columnAdvance(Context c, int line, int vcol) {
        String text = c.buffer.line(line);
        int col = Text.columnAt(text, vcol, c.tabstop());
        if (!c.inVisual() && col >= text.length() && !text.isEmpty()) col = Text.last(text);
        return col;
    }

    /** nv_g_dollar_cmd: g$ (and g<End>, which then backs up over white space). */
    private static Target endOfScreenLine(Context c, boolean end) {
        View v = c.editor.view();
        Pos original = c.cursor();
        String text;
        int ts = c.tabstop();
        Pos to;
        if (v.physical() && c.options.flag("wrap")) {
            to=c.buffer.pos(v.rowPosition(c.buffer.offset(original),c.count-1,'$'));
            text=c.buffer.line(to.line);
            c.editor.curswant(Text.virtcol(text,to.col,ts));
        } else if (c.options.flag("wrap")) {
            if (c.count == 1) {
                text = c.buffer.line(original.line);
                int width1 = v.width(), width2 = v.width();
                int virtcol = Text.virtcol(text, Math.min(original.col, text.length()), ts);
                if (original.col < text.length() && text.charAt(original.col) == '\t' && !c.editor.mode().inserting())
                    virtcol += Text.width(text, original.col, virtcol, ts) - 1;
                int i = width1 - 1;
                if (virtcol >= width1) i += ((virtcol - width1) / width2 + 1) * width2;
                int col = columnAdvance(c, original.line, i);
                int startV = Text.virtcol(text, Math.min(col, text.length()), ts);
                if (col > 0 && col < text.length() && startV + Text.width(text, col, startV, ts) - 1 > i) col = Text.previous(text, col);
                to = new Pos(original.line, col);
                c.editor.curswant(Text.virtcol(text, Math.min(col, text.length()), ts));
            } else {
                c.editor.curswant(Visual.MAX);
                boolean ok = v.screengo(true, c.count - 1);
                to = c.cursor();
                c.editor.setCursor(original, true);
                if (!ok) throw Context.fail();
            }
        } else {
            int line = original.line;
            if (c.count > 1) line = c.editor.view().downFrom(line + 1, c.count - 1) - 1;
            int i = v.leftcol + v.width() - 1;
            int col = columnAdvance(c, line, i);
            to = new Pos(line, col);
            text = c.buffer.line(line);
            c.editor.curswant(Text.virtcol(text, Math.min(col, text.length()), ts));
        }
        if (end) {
            text = c.buffer.line(to.line);
            int col = to.col;
            while (col > 0 && col < text.length() && Block.white(text.charAt(col))) col = Text.previous(text, col);
            to = new Pos(to.line, col);
        }
        return Target.inclusive(to).withColumn(Target.Column.KEEP);
    }

    /** za and zA. */
    private static void toggle(Context c, boolean all) {
        Folds folds = c.editor.folds();
        boolean closed = folds.closed(line(c)) != null;
        if (all) folds.recurse(line(c), closed);
        else folds.repeat(line(c), c.count, closed);
    }

    /** zj, zk (to the next or previous fold) and [z, ]z (the open fold's start or end): jumps to column 0. */
    private static Target foldMove(Context c, boolean updown, boolean forward) {
        int lnum = c.editor.folds().moveTo(line(c), updown, forward, c.count);
        if (lnum < 0) throw Context.fail();
        return Target.exclusive(new Pos(lnum - 1, 0)).asJump();
    }
}
