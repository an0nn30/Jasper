package dev.jasper.editor.vim;

/**
 * The window onto the text as Vim models it (spec 3.1 and 4.1): the first line shown ({@code topline}), how many
 * screen rows and columns there are, and 'wrap'. A port of Vim's move.c (update_topline, the scroll_cursor_*
 * functions, scrollup and scrolldown, cursor_correct, pagescroll) and normal.c's scrolling commands, so that
 * {@code <C-e>}, {@code <C-d>}, {@code <C-f>}, {@code zz}, {@code H} and the rest land where Vim's do. The Editor keeps
 * the text area's viewport in step with it; tests and the corpus set its size directly.
 *
 * <p>Line numbers here are Vim's: 1 is the first line. A closed fold counts as one screen line. The ports keep
 * Vim's names; 'smoothscroll' is never on, but pagescroll turns it on for a moment as Vim does, so its paths are
 * ported too ({@link #skipcol} is the number of display columns of the top line scrolled out of view).
 */
final class View {
    private static final int MAXCOL = Integer.MAX_VALUE / 4;

    private final VimEditor editor;
    /** Screen rows and columns for text (Vim's w_height and w_width; no number column is counted). */
    private int height = 24, width = 80;
    int topline = 1;
    /** Without 'wrap', the first display column shown (w_leftcol). */
    int leftcol;
    int botline = 2;
    int emptyRows;
    int skipcol;
    /** 'smoothscroll', only on while pagescroll runs. */
    private boolean sms;
    /** 'scroll': lines for CTRL-D and CTRL-U, half the window unless a count set it. */
    private int scroll = 12;
    private int clineRow, clineHeight;
    private boolean physical;
    private int physicalColumnOffset = -1;
    private float physicalColumn;

    /** A mounted editor uses its shaped rows; standalone corpus windows retain explicit cell geometry. */
    void physical(boolean value) { physical = value; }
    boolean physical() { return physical; }
    int rowPosition(int offset,int delta,char which) { return geometry().rowPosition(offset,delta,which); }
    private dev.jasper.editor.textview.EditorTextView geometry() { return editor.area().textView(); }
    private int cursorOffset() { return editor.buffer().offset(editor.cursor()); }
    private int cellWidth() { return Math.max(1, editor.area().getFontMetrics(editor.area().getFont()).charWidth('m')); }


    View(VimEditor editor) { this.editor = editor; }

    // --- size and state ---

    /** Sets the window's size in screen rows and columns (Vim's w_height and w_width); 'scroll' becomes half the rows. */
    void resize(int rows, int columns) {
        int h = Math.max(1, rows), w = Math.max(1, columns);
        if (h != height) scroll = Math.max(1, h / 2);
        height = h;
        width = w;
    }

    int height() { return height; }

    int width() { return width; }

    /** Makes a line the first one shown, as when the user scrolls the text area. */
    void topline(int lnum) {
        topline = Math.max(1, Math.min(lnum, count()));
        skipcol = 0;
        int[] fold = folded(topline);
        if (fold != null) topline = fold[0];
    }

    /** save_viewstate: the window's position, for 'incsearch' to put back. */
    int[] viewstate() { return new int[] { topline, botline, emptyRows, leftcol, skipcol }; }

    /** restore_viewstate. */
    void viewstate(int[] saved) {
        topline = saved[0];
        botline = saved[1];
        emptyRows = saved[2];
        leftcol = saved[3];
        skipcol = saved[4];
    }

    private boolean wrap() { return editor.options().flag("wrap"); }

    private int so() { return editor.options().number("scrolloff"); }

    private int count() { return editor.buffer().count(); }

    private String line(int lnum) { return editor.buffer().line(lnum - 1); }

    private int ts() { return editor.options().number("tabstop"); }

    private int lnum() { return editor.cursor().line + 1; }

    private void lnum(int lnum) {
        Pos p = editor.cursor();
        int line = Math.max(1, Math.min(lnum, count())) - 1;
        editor.setCursor(new Pos(line, Math.min(p.col, editor.buffer().line(line).length())), true);
    }

    /** @return the closed fold holding the line as {first, last}, or null (hasFolding) */
    private int[] folded(int lnum) { return editor.folds().closed(lnum); }

    private boolean anyFolding() { return editor.folds().any(); }

    /** linetabsize: the line's width in display columns. */
    int linetabsize(int lnum) {
        String text = line(lnum);
        return Text.virtcol(text, text.length(), ts());
    }

    /** plines_win_nofold: screen rows the line takes. */
    int plinesNofold(int lnum) {
        if (physical) return geometry().rowsForLine(lnum - 1);
        if (line(lnum).isEmpty()) return 1;
        int col = linetabsize(lnum);
        if (col <= width) return 1;
        col -= width;
        return (col + (width - 1)) / width + 1;
    }

    /** plines_win_nofill: one row for a closed fold or without 'wrap'. */
    int plinesWin(int lnum, boolean limit) {
        if (!wrap()) return 1;
        if (folded(lnum) != null) return 1;
        int lines = plinesNofold(lnum);
        return limit && lines > height ? height : lines;
    }

    int plines(int lnum) { return plinesWin(lnum, true); }

    private int adjustPlinesForSkipcol() {
        if (skipcol == 0) return 0;
        if (skipcol >= width && width > 0) return (skipcol - width) / width + 1;
        return 0;
    }

    private int plinesCorrectTopline(int lnum, boolean limit) {
        int n = plinesWin(lnum, false);
        if (lnum == topline) n -= adjustPlinesForSkipcol();
        if (limit && n > height) n = height;
        return n;
    }

    private int skipcolFromPlines(int plinesOff) {
        int skip = 0;
        if (plinesOff > 0) skip += width;
        if (plinesOff > 1) skip += width * (plinesOff - 1);
        return skip;
    }

    /** w_virtcol: the cursor's display column; outside Insert mode a tab's cursor is on its last column. */
    private int virtcol() {
        if (physical) return (wrap() ? geometry().rowInLine(cursorOffset()) * width : 0)
            + Math.round(geometry().xForOffset(cursorOffset()) / cellWidth());
        Pos p = editor.cursor();
        String text = editor.buffer().line(p.line);
        int col = Math.min(p.col, text.length());
        int start = Text.virtcol(text, col, ts());
        if (col < text.length() && text.charAt(col) == '\t' && !editor.mode().inserting())
            return start + Text.width(text, col, start, ts()) - 1;
        return start;
    }

    /**
     * curs_columns: without 'wrap', the window scrolls sideways to keep the cursor shown ('sidescroll' 0 puts it in
     * the middle).
     */
    void cursColumns() {
        if (wrap()) {
            leftcol = 0;
            return;
        }
        if (folded(lnum()) != null) return;
        Pos p = editor.cursor();
        String text = editor.buffer().line(p.line);
        int col = Math.min(p.col, text.length());
        int startcol = physical ? Math.round(geometry().xForOffset(cursorOffset()) / cellWidth()) : Text.virtcol(text, col, ts());
        int endcol = physical ? startcol : col < text.length() ? startcol + Text.width(text, col, startcol, ts()) - 1 : startcol;
        int siso = editor.options().number("sidescrolloff"), ss = editor.options().number("sidescroll");
        int offLeft = startcol - leftcol - siso;
        int offRight = endcol - (leftcol + width - siso) + 1;
        if (offLeft < 0 || offRight > 0) {
            int diff = offLeft < 0 ? -offLeft : offRight;
            int newLeftcol;
            if (ss == 0 || diff >= width / 2 || offRight >= offLeft) newLeftcol = virtcol() - width / 2;
            else {
                if (diff < ss) diff = ss;
                newLeftcol = offLeft < 0 ? leftcol - diff : leftcol + diff;
            }
            leftcol = Math.max(0, newLeftcol);
        }
    }

    // --- botline (comp_botline) ---

    /** comp_botline: the first line below the window, and the cursor line's row and height. */
    void compBotline() {
        int lnum = topline, done = 0, cursor = lnum(), n;
        for (; lnum <= count(); lnum++) {
            int last = lnum;
            int[] fold = folded(lnum);
            if (fold != null) {
                last = fold[1];
                n = 1;
            } else n = plinesCorrectTopline(lnum, true);
            if (lnum <= cursor && last >= cursor) {
                clineRow = done;
                clineHeight = n;
            }
            if (done + n > height) break;
            done += n;
            lnum = last;
        }
        botline = lnum;
        emptyRows = done == 0 ? 0 : height - done;
    }

    private void validateCheight() {
        int[] fold = folded(lnum());
        clineHeight = fold != null ? 1 : plines(lnum());
    }

    /** The cursor's row in the window (w_wrow). */
    private int wrow() {
        int row = 0;
        for (int lnum = topline; lnum < lnum() && lnum <= count(); lnum++) {
            int[] fold = folded(lnum);
            if (fold != null) {
                if (fold[1] >= lnum()) break;
                row += 1;
                lnum = fold[1];
            } else row += plinesCorrectTopline(lnum, true);
        }
        if (wrap() && folded(lnum()) == null) {
            int vcol = virtcol();
            if (lnum() == topline) vcol -= skipcol;
            if (vcol >= width) row += vcol / width;
        }
        return row;
    }

    // --- update_topline ---

    /** update_topline: after a command, scroll so the cursor is shown with 'scrolloff' lines around it. */
    void updateTopline() {
        int so = so();
        if (topline > count()) topline = Math.max(1, count());
        if (editor.buffer().length() == 0) {
            topline = 1;
            botline = 2;
            skipcol = 0;
            return;
        }
        int oldTopline = topline;
        boolean checkTopline = false, checkBotline = false;
        if (topline > 1 || skipcol > 0) {
            if (lnum() < topline) checkTopline = true;
            else if (checkTopOffset()) checkTopline = true;
            else if (skipcol > 0 && lnum() == topline) {
                if (skipcol + 3 > virtcol()) checkTopline = true;
            }
        }
        if (checkTopline) {
            int halfheight = Math.max(2, height / 2 - 1);
            int n;
            if (anyFolding()) {
                n = 0;
                for (int lnum = lnum(); lnum < topline + so; lnum++) {
                    n++;
                    if (lnum >= count() || n >= halfheight) break;
                    int[] fold = folded(lnum);
                    if (fold != null) lnum = fold[1];
                }
            } else n = topline + so - lnum();
            if (n >= halfheight) scrollCursorHalfway(false, false);
            else {
                scrollCursorTop(scrolljump(), false);
                checkBotline = true;
            }
        } else {
            int[] fold = folded(topline);
            if (fold != null) topline = fold[0];
            checkBotline = true;
        }
        if (checkBotline) {
            compBotline();
            if (botline <= count()) {
                if (lnum() < botline) {
                    if (lnum() >= botline - so || anyFolding()) {
                        int n = emptyRows;
                        int[] fold = folded(lnum());
                        int loff = fold != null ? fold[1] : lnum();
                        int height0 = 0;
                        while (loff < botline) {
                            n += height0;
                            if (n >= so) break;
                            int[] f = botlineForw(loff);
                            loff = f[0];
                            height0 = f[1];
                        }
                        if (n >= so) checkBotline = false;
                    } else checkBotline = false;
                }
                if (checkBotline) {
                    int lineCount;
                    if (anyFolding()) {
                        lineCount = 0;
                        for (int lnum = lnum(); lnum >= botline - so; lnum--) {
                            lineCount++;
                            if (lnum <= 0 || lineCount > height + 1) break;
                            int[] fold = folded(lnum);
                            if (fold != null) lnum = fold[0];
                        }
                    } else lineCount = lnum() - botline + 1 + so;
                    if (lineCount <= height + 1) scrollCursorBot(scrolljump(), false);
                    else scrollCursorHalfway(false, false);
                }
            }
        }
        if (topline != oldTopline && !sms) skipcol = 0;
        compBotline();
    }

    private int scrolljump() {
        int sj = editor.options().number("scrolljump");
        return sj >= 0 ? sj : height * -sj / 100;
    }

    /** check_top_offset: fewer than 'scrolloff' lines above the cursor. */
    private boolean checkTopOffset() {
        int so = so();
        if (lnum() < topline + so || anyFolding()) {
            int lnum = lnum(), n = 0;
            while (n < so) {
                int[] b = toplineBack(lnum, true);
                lnum = b[0];
                if (lnum < topline) break;
                n += b[1];
            }
            return n < so;
        }
        return false;
    }

    /** topline_back: the line above, its height (a closed fold is one row; above the first line, MAXCOL). */
    private int[] toplineBack(int lnum, boolean winheight) {
        lnum--;
        if (lnum < 1) return new int[] { lnum, MAXCOL };
        int[] fold = folded(lnum);
        if (fold != null) return new int[] { fold[0], 1 };
        return new int[] { lnum, plinesWin(lnum, winheight) };
    }

    /** botline_forw: the line below, its height (MAXCOL below the last line). */
    private int[] botlineForw(int lnum) {
        lnum++;
        if (lnum > count()) return new int[] { lnum, MAXCOL };
        int[] fold = folded(lnum);
        if (fold != null) return new int[] { fold[1], 1 };
        return new int[] { lnum, plines(lnum) };
    }

    // --- scroll_cursor_top, scroll_cursor_bot, scroll_cursor_halfway ---

    /** scroll_cursor_top: the cursor line at the top, with 'scrolloff', scrolling at least minScroll lines. */
    void scrollCursorTop(int minScroll, boolean always) {
        int scrolled = 0, extra = 0, used, i;
        int oldTopline = topline;
        int off = so();
        validateCheight();
        used = clineHeight;
        if (lnum() < topline) scrolled = used;
        int top, bot;
        int[] fold = folded(lnum());
        if (fold != null) {
            top = fold[0] - 1;
            bot = fold[1] + 1;
        } else {
            top = lnum() - 1;
            bot = lnum() + 1;
        }
        int newTopline = top + 1;
        while (top > 0) {
            int[] f = folded(top);
            if (f != null) {
                top = f[0];
                i = 1;
            } else i = plines(top);
            if (top < topline) scrolled += i;
            if ((newTopline >= topline || scrolled > minScroll) && extra >= off) break;
            used += i;
            if (extra + i <= off && bot < count()) {
                int[] fb = folded(bot);
                if (fb != null) {
                    bot = fb[1];
                    used++;
                } else used += plines(bot);
            }
            if (used > height) break;
            extra += i;
            newTopline = top;
            top--;
            bot++;
        }
        if (used > height) scrollCursorHalfway(false, false);
        else {
            if (newTopline < topline || always) topline = newTopline;
            if (topline > lnum()) topline = lnum();
            if (topline != oldTopline) skipcol = 0;
            else if (topline == lnum() && skipcol >= virtcol()) skipcol = 0;
        }
        compBotline();
    }

    /** scroll_cursor_bot: the cursor line at the bottom, with 'scrolloff'. */
    void scrollCursorBot(int minScroll, boolean setTopbot) {
        int used, scrolled = 0, extra = 0, i, lineCount;
        int oldTopline = topline, oldSkipcol = skipcol, oldBotline = botline, oldEmptyRows = emptyRows;
        int cln = lnum();
        int so = so();
        boolean doSms = wrap() && sms;
        if (setTopbot) {
            used = 0;
            botline = cln + 1;
            int loff = cln + 1;
            while (true) {
                int[] b = toplineBack(loff, false);
                loff = b[0];
                if (b[1] == MAXCOL) break;
                if (used + b[1] > height) {
                    if (doSms && used < height) {
                        int plinesOffset = used + b[1] - height;
                        used = height;
                        topline = loff;
                        skipcol = skipcolFromPlines(plinesOffset);
                    }
                    break;
                }
                topline = loff;
                used += b[1];
            }
            emptyRows = used == 0 ? 0 : height - used;
            if (topline != oldTopline || skipcol != oldSkipcol || skipcol != 0) {
                if (skipcol == oldSkipcol) skipcol = 0;
            }
        } else compBotline();
        validateCheight();
        used = clineHeight;
        if (cln >= botline) {
            scrolled = used;
            if (cln == botline) scrolled -= emptyRows;
            if (doSms) {
                int topPlines = plinesWin(topline, false);
                int skipLines = 0;
                if (skipcol > width) skipLines += (skipcol - width) / width + 1;
                else if (skipcol > 0) skipLines = 1;
                topPlines -= skipLines;
                if (topPlines > height) scrolled += topPlines - height;
            }
        }
        int loff, boff;
        int[] fold = folded(cln);
        if (fold != null) {
            loff = fold[0];
            boff = fold[1];
        } else {
            loff = cln;
            boff = cln;
        }
        while (loff > 1) {
            if (((scrolled <= 0 || scrolled >= minScroll) && extra >= so || boff + 1 > count()) && loff <= botline) break;
            int[] b = toplineBack(loff, true);
            loff = b[0];
            if (b[1] == MAXCOL) used = MAXCOL;
            else used += b[1];
            if (used > height) break;
            if (loff >= botline) {
                scrolled += b[1];
                if (loff == botline) scrolled -= emptyRows;
            }
            if (boff < count()) {
                int[] f = botlineForw(boff);
                boff = f[0];
                used += f[1];
                if (used > height) break;
                if (extra < so || scrolled < minScroll) {
                    extra += f[1];
                    if (boff >= botline) {
                        scrolled += f[1];
                        if (boff == botline) scrolled -= emptyRows;
                    }
                }
            }
        }
        if (scrolled <= 0) lineCount = 0;
        else if (used > height) lineCount = used;
        else {
            lineCount = 0;
            int bl = topline - 1;
            for (i = 0; i < scrolled && bl < botline; ) {
                int[] f = botlineForw(bl);
                bl = f[0];
                i += f[1];
                lineCount++;
            }
            if (i < scrolled) lineCount = 9999;
        }
        if (lineCount >= height && lineCount > minScroll) scrollCursorHalfway(false, true);
        else if (lineCount > 0) {
            if (doSms) scrollup(scrolled, true);
            else scrollup(lineCount, true);
        }
        if (topline == oldTopline && skipcol == oldSkipcol && setTopbot) {
            botline = oldBotline;
            emptyRows = oldEmptyRows;
        }
        if (setTopbot) cursorCorrectSms();
        compBotline();
    }

    /** scroll_cursor_halfway: the cursor line in the middle of the window. */
    void scrollCursorHalfway(boolean atend, boolean preferAbove) {
        int above = 0, below = 0, used;
        int newSkipcol = 0;
        int loff = lnum(), boff = lnum();
        int[] fold = folded(loff);
        if (fold != null) {
            loff = fold[0];
            boff = fold[1];
        }
        used = plines(loff);
        int top = loff;
        boolean doSms = wrap() && sms;
        int wantHeight = height;
        if (doSms) {
            if (atend) {
                wantHeight = (height - used) / 2;
                used = 0;
            }
        }
        while (top > 1) {
            if (doSms) {
                int[] b = toplineBack(loff, false);
                loff = b[0];
                if (b[1] == MAXCOL) break;
                used += b[1];
                if (!atend && boff < count()) {
                    int[] f = botlineForw(boff);
                    boff = f[0];
                    used += f[1];
                }
                if (used > wantHeight) {
                    if (used - b[1] < wantHeight) {
                        top = loff;
                        newSkipcol = skipcolFromPlines(used - wantHeight);
                    }
                    break;
                }
                top = loff;
                continue;
            }
            boolean done = false;
            for (int round = 1; round <= 2; round++) {
                if (preferAbove ? round == 2 && below < above : round == 1 && below <= above) {
                    if (boff < count()) {
                        int[] f = botlineForw(boff);
                        boff = f[0];
                        used += f[1];
                        if (used > height) {
                            done = true;
                            break;
                        }
                        below += f[1];
                    } else {
                        below++;
                        if (atend) used++;
                    }
                }
                if (preferAbove ? round == 1 && below >= above : round == 1 && below > above) {
                    int[] b = toplineBack(loff, true);
                    loff = b[0];
                    if (b[1] == MAXCOL) used = MAXCOL;
                    else used += b[1];
                    if (used > height) {
                        done = true;
                        break;
                    }
                    above += b[1];
                    top = loff;
                }
            }
            if (done) break;
        }
        int[] f = folded(top);
        if (f != null) topline = f[0];
        else if (topline != top || newSkipcol != 0 || skipcol != 0) {
            topline = top;
            if (newSkipcol != 0) skipcol = newSkipcol;
            else if (doSms) skipcol = 0;
        }
        compBotline();
    }

    // --- scrolling: scrollup, scrolldown, scroll_redraw ---

    /** scrolldown: CTRL-Y, the text moves down {@code count} lines. */
    void scrolldown(long lineCount, boolean byfold) {
        long done = 0;
        boolean moved = false;
        boolean doSms = wrap() && sms;
        int width1 = width, width2 = width;
        int[] fold = folded(topline);
        if (fold != null) topline = fold[0];
        int wrowBefore = wrow();
        for (long todo = lineCount; todo > 0; todo--) {
            if (topline == 1 && (!doSms || skipcol < width1)) break;
            if (doSms && skipcol >= width1) {
                if (skipcol >= width1 + width2) skipcol -= width2;
                else skipcol -= width1;
                done++;
            } else {
                topline--;
                skipcol = 0;
                int[] f = folded(topline);
                if (f != null) {
                    done++;
                    if (!byfold) todo -= topline - f[0] - 1;
                    botline -= topline - f[0];
                    topline = f[0];
                } else if (doSms) {
                    int size = linetabsize(topline);
                    if (size > width1) {
                        skipcol = width1;
                        size -= width1;
                    }
                    while (size > width2) {
                        skipcol += width2;
                        size -= width2;
                    }
                    done++;
                } else done += plines(topline);
            }
            botline--;
        }
        long wrow = wrowBefore + done;
        if (wrap() && width != 0 && folded(lnum()) == null) {
            validateCheight();
            wrow += clineHeight - 1 - virtcol() / width;
        }
        while (wrow >= height && lnum() > 1) {
            int[] f = folded(lnum());
            if (f != null) {
                wrow--;
                lnum(f[0] == 1 ? 1 : f[0] - 1);
            } else {
                wrow -= plines(lnum());
                lnum(lnum() - 1);
            }
            moved = true;
        }
        if (moved) {
            foldAdjustCursor();
            coladvance();
        }
        if (lnum() < topline) lnum(topline);
        compBotline();
    }

    /** scrollup: CTRL-E, the text moves up {@code count} lines. */
    void scrollup(long lineCount, boolean byfold) {
        boolean doSms = wrap() && sms;
        if (doSms || byfold && anyFolding()) {
            int width1 = width, width2 = width;
            int size = 0;
            if (doSms) size = linetabsize(topline);
            for (long todo = lineCount; todo > 0; todo--) {
                int lnum = topline;
                if (byfold) {
                    int[] f = folded(lnum);
                    if (f != null) lnum = f[1];
                }
                if (lnum == topline && doSms) {
                    int add = skipcol > 0 ? width2 : width1;
                    skipcol += add;
                    if (skipcol >= size) {
                        if (lnum == count()) {
                            skipcol -= add;
                            break;
                        }
                        lnum++;
                    }
                } else {
                    if (lnum >= count()) break;
                    lnum++;
                }
                if (lnum > topline) {
                    botline += lnum - topline;
                    topline = lnum;
                    skipcol = 0;
                    if (todo > 1 && doSms) size = linetabsize(topline);
                }
            }
        } else {
            topline += (int) lineCount;
            botline += (int) lineCount;
        }
        if (topline > count()) topline = count();
        if (botline > count() + 1) botline = count() + 1;
        if (anyFolding()) {
            int[] f = folded(topline);
            if (f != null) topline = f[0];
        }
        if (lnum() < topline) {
            lnum(topline);
            coladvance();
        }
        compBotline();
    }

    /** scroll_redraw: CTRL-E and CTRL-Y, then the cursor stays inside the window with 'scrolloff'. */
    void scrollRedraw(boolean up, long count) {
        int prevTopline = topline, prevSkipcol = skipcol, prevLnum = lnum();
        if (up) scrollup(count, true);
        else scrolldown(count, true);
        if (so() > 0) {
            cursorCorrect();
            while (topline == prevTopline && skipcol == prevSkipcol) {
                if (up) {
                    if (lnum() > prevLnum || !cursorDown(1)) break;
                } else if (lnum() < prevLnum || prevTopline == 1 || !cursorUp(1)) break;
            }
        }
        cursorCorrectSms();
        if (lnum() != prevLnum) coladvance();
    }

    /** cursor_down_inner: {@code n} lines down, a closed fold counting as one; false on the last line. */
    boolean cursorDown(int n) {
        int lnum = lnum(), lineCount = count();
        if (n > 0 && lnum >= lineCount) return false;
        lnum = downFrom(lnum, n);
        lnum(lnum);
        return true;
    }

    /** cursor_down_inner's counting: {@code n} lines down from {@code lnum}, a closed fold counting as one. */
    int downFrom(int lnum, int n) {
        int lineCount = count();
        if (anyFolding()) {
            while (n-- > 0) {
                int[] f = folded(lnum);
                if (f != null) lnum = f[1] + 1;
                else lnum++;
                if (lnum >= lineCount) break;
            }
            if (lnum > lineCount) lnum = lineCount;
        } else if (lnum + n >= lineCount) lnum = lineCount;
        else lnum += n;
        return lnum;
    }

    /** cursor_up_inner: {@code n} lines up, a closed fold counting as one; false on the first line. */
    boolean cursorUp(int n) {
        int lnum = lnum();
        if (n > 0 && lnum <= 1) return false;
        lnum(upFrom(lnum, n, editor.mode().inserting()));
        return true;
    }

    /** cursor_up_inner's counting. */
    int upFrom(int lnum, int n, boolean inserting) {
        if (anyFolding()) {
            int[] f = folded(lnum);
            if (f != null) lnum = f[0];
            while (n-- > 0) {
                lnum--;
                if (lnum <= 1) break;
                if (n > 0 || !inserting) {
                    int[] g = folded(lnum);
                    if (g != null) lnum = g[0];
                }
            }
            if (lnum < 1) lnum = 1;
        } else if (lnum <= n) lnum = 1;
        else lnum -= n;
        return lnum;
    }

    // --- cursor_correct and the smoothscroll helpers ---

    /** cursor_correct: the cursor at least 'scrolloff' lines from the window's edges. */
    void cursorCorrect() {
        int above = 0, below = 0;
        int so = so();
        int aboveWanted = so, belowWanted = so;
        if (topline == 1) {
            aboveWanted = 0;
            int maxOff = height / 2;
            if (belowWanted > maxOff) belowWanted = maxOff;
        }
        compBotline();
        if (botline == count() + 1) {
            belowWanted = 0;
            int maxOff = (height - 1) / 2;
            if (aboveWanted > maxOff) aboveWanted = maxOff;
        }
        int cln = lnum();
        if (cln >= topline + aboveWanted && cln < botline - belowWanted && !anyFolding()) return;
        if (sms && !wrap()) {
            if (clineHeight == height) {
                skipcol = 0;
                return;
            }
        }
        int top = topline, bot = botline - 1;
        while ((above < aboveWanted || below < belowWanted) && top < bot) {
            if (below < belowWanted && (below <= above || above >= aboveWanted)) {
                int[] f = folded(bot);
                if (f != null) {
                    bot = f[0];
                    below++;
                } else below += plines(bot);
                bot--;
            }
            if (above < aboveWanted && (above < below || below >= belowWanted)) {
                int[] f = folded(top);
                if (f != null) {
                    top = f[1];
                    above++;
                } else above += plines(top);
                top++;
            }
        }
        if (top == bot || bot == 0) lnum(top);
        else if (top > bot) lnum(bot);
        else {
            if (cln < top && topline > 1) lnum(top);
            if (cln > bot && botline <= count()) lnum(bot);
        }
    }

    /** cursor_correct_sms: with 'smoothscroll', the cursor moves to a part of the top line that is shown. */
    private void cursorCorrectSms() {
        if (!sms || !wrap() || lnum() != topline) return;
        int so = so();
        int width1 = width, width2 = width;
        int soCols = so == 0 ? 0 : width1 + (so - 1) * width2;
        int spaceCols = (height - 1) * width2;
        int size = so == 0 ? 0 : linetabsize(topline);
        if (topline == 1 && skipcol == 0) soCols = 0;
        else if (soCols > spaceCols / 2) soCols = spaceCols / 2;
        while (soCols > size && soCols - width2 >= width1 && width1 > 0) soCols -= width2;
        if (soCols >= width1 && soCols > size) soCols -= width1;
        int overlap = skipcol == 0 ? 0 : 3;
        int top = skipcol + (soCols != 0 ? soCols : overlap);
        int bot = skipcol + width1 + (height - 1) * width2 - soCols;
        int col = virtcol();
        int original = col;
        if (col < top) {
            if (col < width1) col += width1;
            while (width2 > 0 && col < top) col += width2;
        } else while (width2 > 0 && col >= bot) col -= width2;
        if (col != original) {
            editor.curswant(col);
            boolean reached = coladvance();
            if (!reached && skipcol > 0 && lnum() < count()) {
                if (virtcol() < skipcol + overlap) {
                    editor.setCursor(new Pos(lnum(), 0), true);
                    editor.curswant(0);
                }
            }
        }
    }

    /**
     * coladvance(w_curswant): the cursor to the remembered column on its line.
     *
     * @return whether the column was reached (not past the line's end)
     */
    private boolean coladvance() {
        Pos p = editor.cursor();
        int want = editor.columnWanted();
        editor.setCursor(new Pos(p.line, editor.wantedColumn(p.line)), true);
        String text = editor.buffer().line(p.line);
        return want == Visual.MAX || Text.virtcol(text, text.length(), ts()) > want;
    }

    /** foldAdjustCursor: the cursor to the first line of a closed fold. */
    void foldAdjustCursor() {
        int[] f = folded(lnum());
        if (f != null) lnum(f[0]);
    }

    // --- pagescroll ---

    /** get_scroll_overlap: how many lines CTRL-F and CTRL-B keep (two, one or none). */
    private int scrollOverlap(boolean forward) {
        int minHeight = height - 2;
        compBotline();
        if (!forward && topline == 1 || forward && botline > count()) return minHeight + 2;
        int lnum = forward ? botline : topline - 1;
        int h1 = plines(lnum);
        if (h1 > minHeight) return minHeight + 2;
        int[] l = forward ? toplineBack(lnum, true) : botlineForw(lnum);
        int h2 = l[1];
        if (h2 == MAXCOL || h2 + h1 > minHeight) return minHeight + 2;
        l = forward ? toplineBack(l[0], true) : botlineForw(l[0]);
        int h3 = l[1];
        if (h3 == MAXCOL || h3 + h2 > minHeight) return minHeight + 2;
        l = forward ? toplineBack(l[0], true) : botlineForw(l[0]);
        int h4 = l[1];
        if (h4 == MAXCOL || h4 + h3 + h2 > minHeight || h3 + h2 + h1 > minHeight) return minHeight + 1;
        return minHeight;
    }

    /**
     * scroll_with_sms: scroll with 'smoothscroll' on, then until no partial line is at the top.
     *
     * @return whether the window did not move (Vim's return value)
     */
    private boolean scrollWithSms(boolean forward, long count, long[] curscount) {
        boolean prevSms = sms;
        int prevSkipcol = skipcol, prevTopline = topline;
        sms = true;
        scrollRedraw(forward, count);
        if (!prevSms && skipcol > 0) {
            boolean fixForward = forward;
            if (Math.abs(topline - prevTopline) > (forward ? 0 : 1)) fixForward = !forward;
            int width1 = width, width2 = width;
            count = 1 + (skipcol - width1 - 1) / width2;
            if (fixForward) count = 1 + (linetabsize(topline) - skipcol - width1 + width2 - 1) / width2;
            scrollRedraw(fixForward, count);
            curscount[0] += count * (fixForward == forward ? 1 : -1);
        }
        sms = prevSms;
        return topline == prevTopline && skipcol == prevSkipcol;
    }

    /**
     * pagescroll: CTRL-F, CTRL-B (pages) and CTRL-D, CTRL-U (half pages, moving the cursor as far).
     *
     * @param count the typed count, or 0
     * @return false when neither the window nor the cursor moved
     */
    boolean pagescroll(boolean forward, long count, boolean half) {
        boolean nochange = true;
        int buflen = count();
        Pos prev = editor.cursor();
        int prevCurswant = editor.columnWanted();
        int prevLnum = lnum();
        if (half) {
            if (count > 0) scroll = (int) Math.min(height, count);
            count = Math.min(height, scroll);
            long[] curscount = { count };
            if (forward && (topline + height + count > buflen || anyFolding())) {
                int n = plinesCorrectTopline(topline, false);
                if (n - count < height && topline < buflen) n += plinesM(topline + 1, buflen, height + (int) count);
                if (n < height + count) count = n - height;
            }
            if (count > 0) {
                nochange = scrollWithSms(forward, count, curscount);
                editor.setCursor(prev, true);
                editor.curswant(prevCurswant);
            }
            if (wrap()) screengo(forward, curscount[0]);
            else if (forward) cursorDown((int) curscount[0]);
            else cursorUp((int) curscount[0]);
        } else {
            count *= scrollOverlap(forward);
            long[] ignored = { count };
            nochange = scrollWithSms(forward, count, ignored);
            if (!nochange) {
                compBotline();
                int lnum = forward ? topline : botline - 1;
                lnum(Math.max(lnum, 1));
            }
        }
        if (so() > 0) cursorCorrect();
        foldAdjustCursor();
        Pos now = editor.cursor();
        nochange = nochange && prev.col == now.col && prevLnum == lnum();
        if (!nochange) beginline(true);
        compBotline();
        return !nochange;
    }

    /** plines_m_win: the screen rows of lines first..last, stopping past {@code max}. */
    private int plinesM(int first, int last, int max) {
        int count = 0;
        while (first <= last && count < max) {
            int[] f = folded(first);
            if (f != null) {
                count++;
                first = f[1] + 1;
            } else {
                count += plinesWin(first, false);
                first++;
            }
        }
        return count;
    }

    /**
     * beginline: with {@code sol} (BL_SOL|BL_FIX) the first non-blank when 'startofline' is set, else the remembered
     * column; without it (BL_WHITE|BL_FIX), always the first non-blank.
     */
    void beginline(boolean sol) {
        Pos p = editor.cursor();
        String text = editor.buffer().line(p.line);
        if (!sol || editor.options().flag("startofline")) {
            int col = Text.firstNonBlank(text);
            if (col >= text.length() && !text.isEmpty()) col = Text.last(text);
            editor.setCursor(new Pos(p.line, col), true);
            editor.forgetColumn();
        } else coladvance();
    }

    // --- nv_screengo: gj, gk and half-page cursor moves with 'wrap' ---

    /**
     * nv_screengo: {@code dist} screen lines up or down, keeping the column within the screen line.
     *
     * @return false when it could not move as far
     */
    boolean screengo(boolean forward, long dist) {
        if (physical && wrap()) {
            int offset = cursorOffset();
            if (offset != physicalColumnOffset) physicalColumn = geometry().xForOffset(offset);
            int delta = (int)Math.min(Integer.MAX_VALUE, dist) * (forward ? 1 : -1);
            int next = geometry().moveRows(offset, delta, physicalColumn);
            editor.setCursor(editor.buffer().pos(next), true);
            physicalColumnOffset = cursorOffset();
            editor.curswant(Text.virtcol(line(lnum()), editor.cursor().col, ts()));
            return next != offset;
        }
        int linelen = linetabsize(lnum());
        boolean ok = true, atend = false;
        int width1 = width, width2 = width;
        int curswant = editor.columnWanted();
        if (curswant == Visual.MAX) {
            atend = true;
            int vc = virtcol();
            curswant = width1 - 1;
            if (vc > curswant) curswant += ((vc - curswant - 1) / width2 + 1) * width2;
        } else {
            int n = linelen > width1 ? ((linelen - width1 - 1) / width2 + 1) * width2 + width1 : width1;
            if (curswant >= n) curswant = n - 1;
        }
        while (dist-- > 0) {
            if (!forward) {
                if (curswant >= width1 && folded(lnum()) == null) curswant -= width2;
                else {
                    if (lnum() <= 1) {
                        ok = false;
                        break;
                    }
                    lnum(upFrom(lnum(), 1, editor.mode().inserting()));
                    linelen = linetabsize(lnum());
                    if (linelen > width1) curswant += ((linelen - width1 - 1) / width2 + 1) * width2;
                }
            } else {
                int n = linelen > width1 ? ((linelen - width1 - 1) / width2 + 1) * width2 + width1 : width1;
                if (curswant + width2 < n && folded(lnum()) == null) curswant += width2;
                else {
                    if (lnum() >= count()) {
                        ok = false;
                        break;
                    }
                    lnum(downFrom(lnum(), 1));
                    curswant %= width2;
                    if (curswant >= width1) curswant -= width2;
                    linelen = linetabsize(lnum());
                }
            }
        }
        editor.curswant(Math.max(0, curswant));
        coladvance();
        Pos p = editor.cursor();
        if (p.col > 0 && wrap()) {
            int vc = virtcol();
            if (vc > curswant && (curswant < width1 ? curswant > width1 / 2 : (curswant - width1) % width2 > width2 / 2))
                editor.setCursor(new Pos(p.line, p.col - 1), true);
        }
        if (atend) editor.curswant(Visual.MAX);
        return ok;
    }
}
