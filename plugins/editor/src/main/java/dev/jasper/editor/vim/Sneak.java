package dev.jasper.editor.vim;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;

/**
 * The sneak extension (spec 8), ported from Justin M. Keyes's vim-sneak: {@code s{c}{c}} goes to the next place the
 * two characters stand together, {@code S} to the one before, {@code z} and {@code Z} after an operator; {@code ;}
 * and {@code ,} repeat it (Vim's own {@code ;} and {@code ,} again after an {@code f} or {@code t}), and
 * {@code <Plug>Sneak_f} and the rest are one-character forms for mapping to {@code f}, {@code t} and the like. A
 * count before {@code s} keeps it to the columns that near the cursor ("vertical scope"). The matches ahead are drawn
 * until the cursor moves on. Label mode is not there.
 */
final class Sneak {
    private static final String OPTION = "sneak";

    /** The plugin's s:st: the last sneak, for {@code ;} and {@code ,}; {@code rst} when f or t took them back. */
    private boolean rst = true, reverse, hooked;
    private String input = "";
    private int inputlen, inclusive;
    private int[] bounds = { 0, 0 };
    /** Whether a sneak's highlight and state last until the cursor moves (the plugin's sneak autocommands). */
    private VimEditor sneakingIn;
    private Pos sneakingAt;

    static void register(VimCommands commands) {
        sneak(commands, "<Plug>Sneak_s", "to the next place {char}{char} stand together (`s`, `z` after an operator, with `set sneak`)", 2, false, 2);
        sneak(commands, "<Plug>Sneak_S", "back to the place {char}{char} stand together (`S`, `Z` after an operator)", 2, true, 2);
        sneak(commands, "<Plug>Sneak_f", "to the next {char}, as sneak repeats it", 1, false, 1);
        sneak(commands, "<Plug>Sneak_F", "back to the {char}, as sneak repeats it", 1, true, 1);
        sneak(commands, "<Plug>Sneak_t", "till the next {char}, as sneak repeats it", 1, false, 0);
        sneak(commands, "<Plug>Sneak_T", "back till the {char}, as sneak repeats it", 1, true, 0);
        commands.motion("<Plug>Sneak_;", "repeat the last sneak (`;`); after `f` or `t`, their `;`", c -> repeat(c, false));
        commands.motion("<Plug>Sneak_,", "repeat the last sneak the other way (`,`)", c -> repeat(c, true));
    }

    private static void sneak(VimCommands commands, String name, String description, int inputlen, boolean reverse, int inclusive) {
        commands.motionWithKeys(name, description, (editor, keys) -> enough(editor, keys, inputlen, inclusive), c -> wrap(c, inputlen, reverse, inclusive));
    }

    /**
     * s:getnchars: {@code n} characters, fewer with {@code <CR>}; a key that cancels ends it at once. With
     * {@code g:sneak#s_next} a sneak like the last, while that one is going on, reads none: it repeats it.
     */
    private static boolean enough(VimEditor editor, List<KeyInput> keys, int n, int inclusive) {
        if (keys.isEmpty()) {
            Vim vim = editor.vim();
            if (!vim.extensions().on(OPTION) || number(vim, "sneak#s_next", 0) == 0 || editor.machine().operatorPending()) return false;
            Sneak st = vim.extensions().script(Sneak.class, Sneak::new);
            return n == st.inputlen && inclusive == st.inclusive && st.sneaking(editor);
        }
        KeyInput last = keys.getLast();
        return keys.size() >= n || cancels(last) || last.is("<CR>");
    }

    private static boolean cancels(KeyInput key) {
        return key.is("<Esc>") || key.is("<C-c>") || key.is("<C-g>") || key.is("<BS>") || key.is("<Del>");
    }

    private static Sneak state(Context c) { return c.vim.extensions().script(Sneak.class, Sneak::new); }

    /** f, F, t and T in Normal or Visual mode give {@code ;} and {@code ,} back to them (the plugin's sneak#reset). */
    static void findTyped(Context c) {
        if (c.operator != null || !c.vim.extensions().on(OPTION)) return;
        Sneak st = state(c);
        if (!st.hooked) return;
        st.rst = true;
        st.reverse = false;
    }

    /** sneak#wrap: the keys read become the input; {@code <CR>} first repeats the last one. */
    private static Target wrap(Context c, int n, boolean reverse, int inclusive) {
        Extensions.require(c, OPTION);
        Sneak st = state(c);
        StringBuilder s = new StringBuilder();
        for (KeyInput key : c.keys) {
            if (cancels(key)) return cancelled(c);
            if (key.is("<CR>")) {
                if (s.isEmpty()) s.append(st.input);
                break;
            }
            s.append(KeyCodes.text(List.of(key)));
        }
        if (c.keys.isEmpty()) return st.rpt(c, reverse);
        return st.to(c, s.toString(), n, c.count, false, reverse, inclusive);
    }

    /** An empty input: nothing moves. */
    private static Target cancelled(Context c) { throw VimFailure.quiet(); }

    /** sneak#rpt: {@code ;} and {@code ,}. */
    private static Target repeat(Context c, boolean reverse) {
        Extensions.require(c, OPTION);
        return state(c).rpt(c, reverse);
    }

    private Target rpt(Context c, boolean reverseKey) {
        VimEditor editor = c.editor;
        if (rst) {
            // exec "norm! " . v:count1 . (a:reverse ? "," : ";"): Vim's own, as a : motion.
            Pos start = editor.cursor();
            Script.normal(editor, c.count + (reverseKey ? "," : ";"));
            Pos to = editor.cursor();
            editor.setCursor(start);
            return Target.exclusive(to);
        }
        boolean relativeReverse = reverseKey != reverse;
        boolean absolute = number(c.vim, "sneak#absolute_dir", 0) != 0;
        return to(c, input, inputlen, c.count, true, absolute ? reverseKey : relativeReverse, inclusive);
    }

    /** sneak#to: the search, from where the cursor is, as the plugin moves it. */
    private Target to(Context c, String in, int len, int count, boolean repeatmotion, boolean rev, int incl) {
        VimEditor editor = c.editor;
        Buffer b = editor.buffer();
        if (in.isEmpty()) return cancelled(c);
        boolean isOp = c.operator != null;
        Pos cur = editor.cursor();
        int ts = c.tabstop();
        int vcol = vcol(b, cur, ts);
        boolean ic = number(c.vim, "sneak#use_ic_scs", 0) != 0 && c.options.flag("ignorecase")
            && !(c.options.flag("smartcase") && in.chars().anyMatch(Character::isUpperCase));
        int skip = isOp || repeatmotion || len < 2 ? Math.min(999, count) : 0;
        int[] bnds = repeatmotion ? bounds.clone() : new int[] { 0, 0 };
        if ((skip == 0 && count > 1 || Math.max(bnds[0], bnds[1]) > 0) && !isOp && Math.max(bnds[0], bnds[1]) == 0) {
            bnds[0] = Math.max(0, vcol - count - 1);
            bnds[1] = count + vcol + 1;
        }
        int leftbound = Math.max(bnds[0], bnds[1]) > 0 ? Math.max(0, bnds[0] - len + 1) : -1;
        Search search = new Search(b, in, ic, leftbound, bnds[1], ts, rev);
        boolean setMark = !repeatmotion && !sneaking(editor);
        if (!repeatmotion) {
            rst = false;
            input = in;
            inputlen = len;
            reverse = rev;
            bounds = bnds;
            inclusive = incl;
            hooked = true;
        }
        Pos nextchar = nextChar(b, cur, !rev);
        boolean nudge = incl == 0 && repeatmotion && nextchar != null && nextchar.equals(search.next(cur));
        if (nudge) cur = nextchar;
        Pos matchpos = null;
        for (int i = 0; i < Math.max(1, skip); i++) {
            Pos found = search.next(cur);
            if (found == null) {
                matchpos = null;
                break;
            }
            matchpos = found;
            cur = found;
            nudge = incl == 0;
        }
        sneakingIn = editor;
        if (matchpos == null) {
            // Not found: the cursor stays where the last match found put it (for t, a step back), and nothing fails.
            if (nudge) {
                Pos back = nextChar(b, cur, rev);
                if (back != null) cur = back;
            }
            sneakingAt = cur;
            editor.sneakHighlight(List.of(), cur);
            return target(cur, setMark && !cur.equals(editor.cursor()));
        }
        if (nudge) {
            Pos back = nextChar(b, cur, rev);
            if (back != null) cur = back;
        }
        if (isOp && incl != 2 && !rev) {
            if (c.forced == 'v') {
                Pos back = nextChar(b, cur, false);
                if (back != null) cur = back;
            } else {
                // norm! l; on the last character only an f-like sneak goes past it ('virtualedit' onemore).
                String line = b.line(cur.line);
                int next = cur.col < line.length() ? Text.next(line, cur.col) : line.length();
                if (next < line.length() || next == line.length() && cur.col < line.length() && incl == 1) cur = new Pos(cur.line, next);
            }
        }
        sneakingAt = cur;
        editor.sneakHighlight(isOp ? List.of() : search.highlight(cur, editor, in.codePointCount(0, in.length())), cur);
        return target(cur, setMark);
    }

    private static Target target(Pos pos, boolean jump) {
        Target t = Target.exclusive(pos);
        return jump ? t.asJump() : t;
    }

    /** Whether the last sneak is still going on: the cursor has stayed where it left it (sneak#is_sneaking). */
    private boolean sneaking(VimEditor editor) {
        return sneakingIn == editor && sneakingAt != null && sneakingAt.equals(editor.cursor()) && !editor.mode().inserting();
    }

    /** searchpos('\_.', 'nW'): the next position, the end of a line and the next line's start counted. */
    private static Pos nextChar(Buffer b, Pos p, boolean forward) {
        String line = b.line(p.line);
        if (forward) {
            if (p.col < line.length()) return new Pos(p.line, p.col < line.length() - 1 ? Text.next(line, p.col) : line.length());
            return p.line + 1 < b.count() ? new Pos(p.line + 1, 0) : null;
        }
        if (p.col > 0) return new Pos(p.line, Text.previous(line, Math.min(p.col, line.length())));
        return p.line > 0 ? new Pos(p.line - 1, b.line(p.line - 1).length()) : null;
    }

    /** The display column, from 1, of a position (virtcol()). */
    private static int vcol(Buffer b, Pos p, int ts) {
        String line = b.line(p.line);
        return Text.virtcol(line, Math.min(p.col, line.length()), ts) + 1;
    }

    /** @return a {@code g:} number, or the default */
    private static long number(Vim vim, String name, long fallback) {
        Object value = vim.variables().get(name);
        if (value == null) return fallback;
        try { return Long.parseLong(value.toString().strip()); }
        catch (NumberFormatException notNumber) { return fallback; }
    }

    /**
     * The plugin's search: {@code \V} and the input with each letter an equivalence class ({@code [[=a=]]}), matched
     * with case unless {@code g:sneak#use_ic_scs} says otherwise, starting in the columns of a vertical scope, in the
     * one direction and without wrapping.
     */
    private record Search(Buffer b, String input, boolean ignoreCase, int leftbound, int right, int ts, boolean reverse) {
        /** @return the next match's start from {@code from}, not at it; null for none */
        Pos next(Pos from) {
            if (!reverse) {
                for (int line = from.line; line < b.count(); line++) {
                    String text = b.line(line);
                    for (int col = line == from.line ? next(text, from.col) : 0; col < text.length(); col = next(text, col))
                        if (at(line, col)) return new Pos(line, col);
                }
                return null;
            }
            for (int line = from.line; line >= 0; line--) {
                String text = b.line(line);
                int start = line == from.line ? Math.min(from.col, text.length()) : text.length();
                for (int col = start > 0 ? Text.previous(text, start) : -1; col >= 0; col = col > 0 ? Text.previous(text, col) : -1)
                    if (at(line, col) && (line != from.line || col < from.col)) return new Pos(line, col);
            }
            return null;
        }

        private static int next(String text, int col) { return col < text.length() ? Text.next(text, col) : col + 1; }

        /** Whether the input matches at the position. */
        boolean at(int line, int col) {
            String text = b.line(line);
            if (leftbound >= 0) {
                int v = Text.virtcol(text, col, ts) + 1;
                if (!(v > leftbound && v < right)) return false;
            }
            int i = col;
            for (int k = 0; k < input.length(); ) {
                if (i >= text.length()) return false;
                int want = input.codePointAt(k), got = text.codePointAt(i);
                if (!same(want, got)) return false;
                k += Character.charCount(want);
                i += Character.charCount(got);
            }
            return true;
        }

        private boolean same(int want, int got) {
            if (ignoreCase) {
                want = Character.toLowerCase(want);
                got = Character.toLowerCase(got);
            }
            if (want == got) return true;
            // \a: an ASCII letter matches its equivalence class, the letters with it as their base.
            return want < 128 && Character.isLetter(want) && base(got) == want;
        }

        private int base(int c) {
            String nfd = Normalizer.normalize(new String(Character.toChars(c)), Normalizer.Form.NFD);
            int base = nfd.codePointAt(0);
            return ignoreCase ? Character.toLowerCase(base) : base;
        }

        /** The matches ahead of the cursor, as the plugin draws them (within 99 lines of the view), and the current one. */
        List<int[]> highlight(Pos cursor, VimEditor editor, int matchlen) {
            List<int[]> out = new ArrayList<>();
            int top = Math.max(0, editor.view().topline - 1 - 99), bottom = Math.min(b.count() - 1, editor.view().botline - 1 + 99);
            Pos p = cursor;
            while (true) {
                Pos m = next(p);
                if (m == null || m.line < top || m.line > bottom) break;
                out.add(range(m, 3));
                p = m;
            }
            if (matchlen > 1) out.add(range(cursor, 3));
            return out;
        }

        private int[] range(Pos p, int kind) {
            String text = b.line(p.line);
            int end = p.col;
            for (int k = 0; k < input.codePointCount(0, input.length()) && end < text.length(); k++) end = Text.next(text, end);
            return new int[] { kind, b.offset(p), b.offset(p.line, end) };
        }
    }
}
