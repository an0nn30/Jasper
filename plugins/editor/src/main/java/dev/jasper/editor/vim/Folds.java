package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import org.fife.ui.rsyntaxtextarea.RSyntaxTextArea;
import org.fife.ui.rsyntaxtextarea.folding.Fold;
import org.fife.ui.rsyntaxtextarea.folding.FoldManager;

/**
 * The tab's code folds as Vim's fold commands see them (spec 4.1: fold commands act on RSyntaxTextArea's code folds).
 * RSyntaxTextArea finds the folds; a Vim fold covers a fold's first to last line, and a collapsed fold is a closed
 * one, shown and moved over as one line. The commands are ports of Vim's fold.c (setManualFoldWin, foldOpenCursor,
 * foldMoveTo) on that tree. Line numbers are Vim's (1 is the first line).
 */
final class Folds {
    private final RSyntaxTextArea area;

    Folds(RSyntaxTextArea area) { this.area = area; }

    private FoldManager manager() { return area.getFoldManager(); }

    /** hasAnyFolding: folding is on and there is a fold. */
    boolean any() {
        FoldManager m = manager();
        return m != null && m.isCodeFoldingEnabled() && m.getFoldCount() > 0;
    }

    private List<Fold> top() {
        List<Fold> out = new ArrayList<>();
        FoldManager m = manager();
        if (m == null || !m.isCodeFoldingEnabled()) return out;
        for (int i = 0; i < m.getFoldCount(); i++) out.add(m.getFold(i));
        return out;
    }

    private static List<Fold> children(Fold fold) {
        List<Fold> out = new ArrayList<>();
        for (int i = 0; i < fold.getChildCount(); i++) out.add(fold.getChild(i));
        return out;
    }

    private static int first(Fold fold) { return fold.getStartLine() + 1; }

    private static int last(Fold fold) { return fold.getEndLine() + 1; }

    /** foldFind: the fold in the list holding the line, or null. */
    private static Fold find(List<Fold> folds, int lnum) {
        for (Fold f : folds) if (first(f) <= lnum && lnum <= last(f)) return f;
        return null;
    }

    /** @return index of the first fold starting after the line (Vim's fp when foldFind fails) */
    private static int after(List<Fold> folds, int lnum) {
        int i = 0;
        while (i < folds.size() && last(folds.get(i)) < lnum) i++;
        return i;
    }

    /** hasFolding: the outermost closed fold holding the line, as {first, last}, or null. */
    int[] closed(int lnum) {
        if (!any()) return null;
        List<Fold> folds = top();
        while (true) {
            Fold f = find(folds, lnum);
            if (f == null) return null;
            if (f.isCollapsed() && last(f) > first(f)) return new int[] { first(f), last(f) };
            folds = children(f);
        }
    }

    /** DONE_FOLD: a fold holds the line; DONE_ACTION: one opened or closed. */
    private static final int FOLD = 1, ACTION = 2;

    /** setManualFoldWin: open the outermost closed fold, or close the innermost open one (all of them, recursive). */
    private int setManual(int lnum, boolean opening, boolean recurse) {
        Fold found = null;
        boolean foundFold = false;
        int done = 0;
        List<Fold> folds = top();
        while (true) {
            Fold f = find(folds, lnum);
            if (f == null) break;
            foundFold = true;
            if (!opening && recurse) {
                if (!f.isCollapsed()) {
                    collapse(f, true);
                    done |= ACTION;
                }
            } else if (f.isCollapsed()) {
                if (opening) {
                    collapse(f, false);
                    done |= ACTION;
                    if (recurse) openNested(f);
                }
                break;
            }
            found = f;
            folds = children(f);
        }
        if (foundFold) {
            if (!opening && found != null) {
                collapse(found, true);
                done |= ACTION;
            }
            done |= FOLD;
        }
        return done;
    }

    /**
     * Opens or closes a fold, keeping the caret where it was: the text area moves it out of a fold it closes, but
     * Vim's cursor stays inside.
     */
    private void collapse(Fold fold, boolean collapsed) {
        if (fold.isCollapsed() == collapsed) return;
        int caret = area.getCaretPosition();
        fold.setCollapsed(collapsed);
        if (area.getCaretPosition() != caret) area.setCaretPosition(Math.min(caret, area.getDocument().getLength()));
    }

    private void openNested(Fold fold) {
        for (Fold child : children(fold)) {
            collapse(child, false);
            openNested(child);
        }
    }

    /** setFoldRepeat: zo and zc, {@code count} levels; fails with E490 when no fold holds the line. */
    void repeat(int lnum, int count, boolean open) {
        for (int n = 0; n < count; n++) {
            int done = setManual(lnum, open, false);
            if ((done & ACTION) == 0) {
                if (n == 0 && (done & FOLD) == 0) throw new VimFailure("E490: No fold found");
                break;
            }
        }
    }

    /** zO and zC: every level. */
    void recurse(int lnum, boolean open) {
        int done = setManual(lnum, open, true);
        if ((done & FOLD) == 0) throw new VimFailure("E490: No fold found");
    }

    /** foldOpenCursor (zv, and 'foldopen'): open every fold holding the line. */
    void openCursor(int lnum) {
        if (!any()) return;
        while ((setManual(lnum, true, false) & ACTION) != 0) { }
    }

    /** zR opens and zM closes every fold. */
    void all(boolean open) {
        for (Fold f : top()) setAll(f, open);
    }

    private void setAll(Fold fold, boolean open) {
        collapse(fold, !open);
        for (Fold child : children(fold)) setAll(child, open);
    }

    /**
     * foldMoveTo: zj and zk ({@code updown}) to the start of the next or the end of the previous fold; [z and ]z to
     * the start or end of the open fold holding the cursor.
     *
     * @return the line to go to, or -1 when there is none
     */
    int moveTo(int cursor, boolean updown, boolean forward, int count) {
        int result = -1;
        for (int n = 0; n < count; n++) {
            List<Fold> folds = top();
            if (folds.isEmpty()) break;
            int lnumFound = cursor;
            boolean last = false;
            while (true) {
                Fold fp = find(folds, cursor);
                int index;
                if (fp == null) {
                    if (!updown || folds.isEmpty()) break;
                    int at = after(folds, cursor);
                    if (forward) {
                        if (at >= folds.size()) break;
                        index = at - 1;
                    } else {
                        if (at == 0) break;
                        index = at;
                    }
                    last = true;
                } else index = folds.indexOf(fp);
                if (!last && fp != null) {
                    if (fp.isCollapsed() && last(fp) > first(fp)) last = true;
                    if (last && !updown) break;
                }
                if (updown) {
                    if (forward) {
                        if (index + 1 < folds.size()) {
                            int lnum = first(folds.get(index + 1));
                            if (lnum > cursor) lnumFound = lnum;
                        }
                    } else if (index > 0) {
                        int lnum = last(folds.get(index - 1));
                        if (lnum < cursor) lnumFound = lnum;
                    }
                } else {
                    int lnum = forward ? last(fp) : first(fp);
                    if (forward ? lnum > cursor : lnum < cursor) lnumFound = lnum;
                }
                if (last) break;
                folds = children(fp);
            }
            if (lnumFound != cursor) {
                cursor = lnumFound;
                result = lnumFound;
            } else break;
        }
        return result;
    }
}
