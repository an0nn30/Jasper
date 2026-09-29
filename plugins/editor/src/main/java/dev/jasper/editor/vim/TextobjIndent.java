package dev.jasper.editor.vim;

/**
 * The textobj-indent extension (spec 8), ported from Michael Smith's vim-indent-object: {@code ii} is the block of
 * lines at the cursor's indent or deeper, {@code ai} also the line above it, {@code aI} the lines above and below,
 * and {@code iI} the same as {@code ii}. The selection is linewise; in Visual mode the object again takes the block
 * around the area, so {@code ii} repeated grows it. As in the plugin, lines are 1-based and columns are counted
 * from 1, and what was selected last is kept between uses ({@code s:l0} to {@code s:c1}).
 */
final class TextobjIndent {
    private static final String OPTION = "textobj-indent";
    /** col() of the {@code '>} mark of a linewise area. */
    private static final int MAXCOL = Integer.MAX_VALUE;

    /** The plugin's script variables, one set for each Vim. */
    private int l0 = -1, l1 = -1, c0 = -1, c1 = -1;

    static void register(VimCommands commands) {
        commands.object("<Plug>textobj-indent-ai", "the lines at the cursor's indent and deeper, and the line above (`ai`, with `set textobj-indent`)",
            c -> select(c, false, false));
        commands.object("<Plug>textobj-indent-aI", "the lines at the cursor's indent and deeper, and the lines above and below (`aI`)",
            c -> select(c, false, true));
        commands.object("<Plug>textobj-indent-ii", "the lines at the cursor's indent and deeper (`ii`)", c -> select(c, true, false));
        commands.object("<Plug>textobj-indent-iI", "the lines at the cursor's indent and deeper (`iI`)", c -> select(c, true, true));
    }

    /** s:HandleTextObjectMapping: the range the mapping passes, the cursor or the Visual area's marks. */
    private static Span select(Context c, boolean inner, boolean incbelow) {
        Extensions.require(c, OPTION);
        VimEditor editor = c.editor;
        int[] range;
        if (c.visual != null) {
            Pos start = Pos.min(c.visual, editor.cursor()), end = Pos.max(c.visual, editor.cursor());
            boolean lines = editor.mode() == Mode.VISUAL_LINE;
            range = new int[] { start.line + 1, end.line + 1, lines ? 1 : start.col + 1, lines ? MAXCOL : end.col + 1 };
        } else {
            Pos p = editor.cursor();
            range = new int[] { p.line + 1, p.line + 1, p.col + 1, p.col + 1 };
        }
        TextobjIndent script = editor.vim().extensions().script(TextobjIndent.class, TextobjIndent::new);
        // :<C-u> ends Visual mode, setting '< and '>.
        if (c.visual != null) Visual.end(editor);
        if (!script.textObject(c, inner, incbelow, range, c.count)) {
            // The plugin returns without selecting: Visual mode gets its area back (the mapping's <Esc>gv), and an
            // operator gets the : motion, which moved nowhere.
            if (c.visual != null) {
                Script.normal(editor, "gv");
                return TextObjects.VISUAL_DONE;
            }
            return new Span(editor.cursor(), editor.cursor(), Target.Kind.EXCLUSIVE);
        }
        return Script.select(c, "V", new Pos(script.l0 - 1, 0), new Pos(script.l1 - 1, 0));
    }

    /** s:TextObject; returns false where the plugin returns without selecting. */
    private boolean textObject(Context c, boolean inner, boolean incbelow, int[] range, int count) {
        Buffer b = c.editor.buffer();
        int ts = c.tabstop();
        boolean exceptFirstLevel = !String.valueOf(c.vim.variables().getOrDefault("indent_object_except_first_level", 1L)).equals("0");
        boolean newVis = l0 != range[0] || l1 != range[1] || c0 != range[2] || c1 != range[3];
        l0 = range[0];
        l1 = range[1];
        c0 = range[2];
        c1 = range[3];
        int itrCnt = 0, cnt = count;
        while (cnt > 0) {
            // The least indent of the lines selected, blank ones aside.
            int idntInvalid = 1000, idnt = idntInvalid;
            for (int l = l0; l <= l1; l++) if (!blank(getline(b, l))) idnt = Math.min(idnt, indent(b, l, ts));
            int lm1 = l0, lm1o = lm1, l2 = l1, l2o = l2;
            // Only blank lines: the indent of the nearest lines with text, the deeper one.
            if (idnt == idntInvalid) {
                idnt = 0;
                int pnb = prevnonblank(b, l0);
                if (pnb != 0) {
                    idnt = Math.max(idnt, indent(b, pnb, ts));
                    lm1 = pnb;
                }
                int nnb = nextnonblank(b, l0);
                if (nnb != 0) idnt = Math.max(idnt, indent(b, nnb, ts));
                if (idnt > indent(b, pnb, ts)) lm1 = nnb;
                if (idnt > indent(b, nnb, ts)) l2 = pnb;
            }
            // Up to the first line with less indent, blank lines aside.
            boolean blnk = blank(getline(b, lm1));
            while (lm1 > 0 && (blnk || indent(b, lm1, ts) >= idnt)) {
                if (exceptFirstLevel && idnt == 0 && blnk) break;
                if (!blnk || !inner) lm1o = lm1;
                lm1--;
                blnk = blank(getline(b, lm1));
            }
            // Down to the first line with less indent, blank lines aside.
            int lineCnt = b.count();
            blnk = blank(getline(b, l2));
            while (l2 <= lineCnt && (blnk || indent(b, l2, ts) >= idnt)) {
                if (exceptFirstLevel && idnt == 0 && blnk) break;
                if (!blnk || !inner) l2o = l2;
                l2++;
                blnk = blank(getline(b, l2));
            }
            // The lines around the block: neither for inner, the one below only for aI.
            int idnt2 = Math.max(indent(b, lm1, ts), indent(b, l2, ts));
            if (indent(b, lm1, ts) < idnt2 || inner) lm1 = lm1o;
            if (indent(b, l2, ts) < idnt2 || inner || !incbelow) l2 = l2o;
            lm1 = Math.max(lm1, 1);
            l2 = Math.min(l2, b.count());
            int cm1 = 1;
            if (inner) cm1 = firstNonWhite(getline(b, lm1)) + 1;
            int cc2 = getline(b, l2).length();
            if (!inner) cc2++;
            // No real change in linewise mode keeps the columns, so that the selection can grow.
            if (itrCnt == 0 && l0 == lm1 && l1 == l2) {
                cm1 = c0;
                cc2 = c1;
            }
            boolean chg = l0 != lm1 || l1 != l2 || c0 != cm1 || c1 != cc2 || newVis;
            l0 = lm1;
            l1 = l2;
            c0 = cm1;
            c1 = cc2;
            if (chg) cnt--;
            else {
                // Nothing changed: from the line above, to get the enclosing block.
                if (l0 == 0) return false;
                l0--;
                c0 = getline(b, l0).length();
            }
            itrCnt++;
        }
        // The area is selected linewise and left with '< and '> set: col("'<") is 1 and col("'>") MAXCOL.
        c0 = 1;
        c1 = MAXCOL;
        return true;
    }

    /** getline(): empty outside the text. */
    private static String getline(Buffer b, int lnum) { return lnum < 1 || lnum > b.count() ? "" : b.line(lnum - 1); }

    /** indent(): -1 outside the text. */
    private static int indent(Buffer b, int lnum, int ts) { return lnum < 1 || lnum > b.count() ? -1 : Text.indent(b.line(lnum - 1), ts); }

    /** {@code =~ "^\\s*$"}. */
    private static boolean blank(String line) { return Text.firstNonBlank(line) == line.length(); }

    /** match(line, '\S'), -1 for none. */
    private static int firstNonWhite(String line) {
        int col = Text.firstNonBlank(line);
        return col == line.length() ? -1 : col;
    }

    /** prevnonblank(): the line at or above with more than white space, or 0. */
    private static int prevnonblank(Buffer b, int lnum) {
        for (int l = Math.min(lnum, b.count()); l >= 1; l--) if (!blank(b.line(l - 1))) return l;
        return 0;
    }

    /** nextnonblank(): the line at or below with more than white space, or 0. */
    private static int nextnonblank(Buffer b, int lnum) {
        for (int l = Math.max(lnum, 1); l <= b.count(); l++) if (!blank(b.line(l - 1))) return l;
        return 0;
    }
}
