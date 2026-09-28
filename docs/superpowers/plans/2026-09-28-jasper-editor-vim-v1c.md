# Editor Vim mode, plan V1c: the view — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Scrolling, `H M L`, the screen-line motions and the fold commands, with the window modelled as Vim models it, so they land where Vim's do; the text area's viewport follows the model and the model follows the user's own scrolling.

**Architecture:** `View` is Vim's window (`topline`, rows, columns, 'wrap', `leftcol`), a port of move.c: `updateTopline` runs after every key, and the scroll commands are ports of scrollup/scrolldown, scroll_cursor_top/bot/halfway, cursor_correct and pagescroll (whose temporary 'smoothscroll' paths are ported too). `Folds` presents RSyntaxTextArea's code folds as Vim's folds (a closed fold counts as one line) and ports fold.c's open/close rules and foldMoveTo. `ViewCommands` registers the commands (ports of normal.c's nv_scroll, nv_screengo, nv_zet and the screen-line "g" commands). `ViewportSync` sizes the view from the text area's scroll pane and scrolls the pane to the view. The corpus gains a window size, code folds and the recorded view.

**Tech Stack:** Java 25 (JBR), Swing, RSyntaxTextArea 3.6.0 (its fold manager and Java fold parser), JUnit 6 and AssertJ, Vim 9 (only for `recordVimCorpus`).

**Spec:** `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md` (plan V1c of section 13, sections 3.1, 4.1, 5.1 and 11). Plans V1a and V1b are merged; their headers describe the engine.

**Status:** Written 2026-09-28 on branch `claude/vim-v1c` (worktree `.worktrees/vim-v1c`, from `main` at 7ef531b8). Every source file, test and corpus case below was compiled and run before the plan was written: against Vim 9.1 all 1125 corpus cases (179 new: view 113, folds 66) passed, three of them divergent, and every command in the table has a case. Applied to a fresh checkout of `7ef531b8` exactly as written, `./gradlew check` passed (3,593 tests, 0 failed, 5 skipped).

## Deviations from the spec (approved scope, adjusted in this plan)

Task 3 writes these into the spec as **Amended (V1c)**.

- **More keys than spec 4.1 lists:** `z+`, `z^`, `<PageUp>`, `<PageDown>`, `<S-Up>`, `<S-Down>`, `g<Down>`, `g<Up>`, `g<Home>`, `g<End>`, and the scroll keys in Visual mode, all as in Vim. 'scrolljump', 'sidescroll' and 'sidescrolloff' are respected (spec 6.3 lists them as options).
- **Folds as Vim sees them:** a code fold covers its first to last line; a closed fold counts as one line for `j`, `k`, `H`, `M`, `L` and scrolling; an operator takes a closed fold whole; word motions, `{`, `}` and searches start past a closed fold; and the motions Vim's default 'foldopen' names (`block,hor,mark,percent,search,undo`) open the fold they land in. The cursor may stay inside a closed fold, as in Vim, so the text area's fold-aware caret navigation is off while Vim is on.
- **Wrapping at any character:** while Vim is on, the text area wraps long lines at any character (Vim's 'nolinebreak'), not at words, so screen lines match Vim's. The tab's Wrap state becomes 'wrap' (new fourth component of `EditorDefaults`).
- **Corpus format:** `window:` sets the window's size (`20x5`, columns by rows); `folds: java 2 7` makes the text's code folds with RSyntaxTextArea's Java fold parser and closes the folds starting on the listed lines (the recorder makes the same folds in Vim as manual folds, innermost first); `expect-view:` (`topline,leftcol`) and `expect-folds:` (the closed folds) are compared for such cases. Cases without a window use Vim's default of 80 columns by 23 rows.
- **Undo cursor:** an operator's undo step records the cursor at the start of the text it changes (Vim's u_save after do_pending_operator moved the cursor), which matters when a closed fold widened the text.

## Global Constraints

- Java 25 on the JetBrains Runtime; always `./gradlew`, never a system Gradle. Work in `.worktrees/vim-v1c` on `claude/vim-v1c`; check `git branch --show-current` before every commit (other sessions switch branches in the main checkout). Never commit on `main`.
- Commit messages end with `Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>`. One commit per task.
- The Editor plugin compiles against the SDK only (`verifyPluginArchitecture`, a root task). `dev.jasper.editor.vim` may use `vim.regex`, RSyntaxTextArea (including its folding package) and the JDK; `dev.jasper.editor.vim.ui` adds `dev.jasper.editor.vim`. No SDK change.
- No interface without two real implementations; this plan adds none.
- Source hygiene: never put raw control, private-use or unpaired surrogate characters in `.java` files; use escapes. Corpus `.case` files are UTF-8 resources and may hold any text. No run of three backticks may appear in any file this plan embeds (the docs link check strips fenced blocks lazily).
- **Do not launch the GUI.** GUI checks go in `docs/editor-verification.md` for the user.
- Engine code runs on the event thread in the app. Tests that type through a `VimEditor` run inside `SwingUtilities.invokeAndWait`.
- The corpus is the authority on Vim behaviour; mark a case `divergent:` only for a documented, deliberate difference.
- `vim` must be on `PATH` for `recordVimCorpus` (the dev Mac has Vim 9.1 at `/usr/bin/vim`). `check` never runs Vim.
- The ports keep Vim's control flow and names (`update_topline`, `scroll_cursor_bot`, `pagescroll`, `setManualFoldWin`); a reviewer's "simplification" is not a reason to change them — the corpus proves them.

## File structure

New, `plugins/editor/src/main/java/dev/jasper/editor/vim/`:

| File | Responsibility |
|---|---|
| `View.java` | Vim's window: topline, size, 'wrap', leftcol; update_topline, the scroll_cursor_* functions, scrollup/scrolldown, cursor_correct, pagescroll, nv_screengo, curs_columns |
| `Folds.java` | the text area's code folds as Vim's folds: hasFolding, zo/zc/zO/zC (setManualFoldWin), zv, zR/zM, foldMoveTo |
| `ViewCommands.java` | the scroll keys, the `z` scroll commands, `H M L`, `gj gk g0 g^ gm g$`, the fold commands |
| `ViewportSync.java` | the view's size from the scroll pane; the scroll pane shows the view; the user's scrolling moves the view |

Changed there: `Options` ('wrap', 'scrolloff', 'scrolljump', 'sidescroll', 'sidescrolloff', 'foldopen'), `EditorDefaults` (`wrap`), `Context` and `Walk` (word motions start past a closed fold), `Motions` (`j`/`k` over folds, `{`/`}` past a fold), `Search` (a search starts past a closed fold), `MarkCommands` and `NormalCommands` ('foldopen' for jumps and undo), `VimUndoManager` (`stepCursor`), `VimCommands` (registration), `Machine` (fold-aware operator spans, 'foldopen', commands that keep the column), `VimEditor` (the view, folds and viewport; character wrapping; no fold-aware caret navigation).
Changed: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java` (the Wrap state into `EditorDefaults`).
Tests: `vim/ViewportSyncTest.java` (new); the corpus harness (`CorpusCase`, `CorpusFile`, `CorpusRunner`, `RecordCorpus`). Corpus: `view.case` and `folds.case`.
Docs: `docs/vim-divergences.md`, `docs/editor-vim-reference.md` (generated), `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, the spec.

---

### Task 1: The engine: the view, folds and the viewport

**Files:**
- Create in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `View.java`, `Folds.java`, `ViewCommands.java`, `ViewportSync.java`
- Replace in `plugins/editor/src/main/java/dev/jasper/editor/vim/`: `Options.java`, `EditorDefaults.java`, `Context.java`, `Walk.java`, `Motions.java`, `Search.java`, `MarkCommands.java`, `NormalCommands.java`, `VimUndoManager.java`, `VimCommands.java`, `Machine.java`, `VimEditor.java`
- Modify: `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`
- Test: `plugins/editor/src/test/java/dev/jasper/editor/vim/ViewportSyncTest.java`

**Interfaces:**
- Consumes: plans V1a and V1b's engine.
- Produces (used by Task 2): package-private `VimEditor.view()` (`View`: `resize(int rows, int columns)`, `updateTopline()`, fields `topline`, `leftcol`, `botline`), `VimEditor.folds()` (`Folds`: `closed(int lnum)` returns `{first, last}` of the closed fold holding a 1-based line, or null; `any()`); public `EditorDefaults(int tabstop, boolean expandtab, boolean autoindent, boolean wrap)` (the three-argument constructor stays, with `wrap` true).

How the pieces fit:
- **The view.** `VimEditor.feed` calls `View.updateTopline` and `cursColumns` after every key, then `ViewportSync.show` scrolls the text area's viewport so `topline` is at the top (with `leftcol` when 'nowrap'). `View` uses Vim's 1-based line numbers internally; `plines` counts screen rows (a line wider than the window takes more with 'wrap'; a closed fold takes one). `pagescroll` scrolls with 'smoothscroll' on for a moment and then finishes the partial line, as Vim 9.1 does, so `<C-d>` and `<C-f>` match Vim on wrapped lines too.
- **Folds.** `Folds` reads RSyntaxTextArea's `FoldManager`: a fold covers its first to last line and a collapsed fold is closed. `View`, the motions (`Motions.down`/`up`, `Walk`, `findParagraph`, `Search`) and `Machine.span`/`lineSpan` ask it which fold hides a line. Opening or closing a fold keeps the caret where it was, because the text area would move it out of a fold it closes and Vim keeps the cursor inside; for the same reason `VimEditor` turns off the text area's fold-aware navigation filter while Vim is on.
- **'foldopen'.** `Machine.foldOpen` opens the folds hiding the cursor after a typed motion whose 'foldopen' item is set (`hor`, `block`, `percent`, `mark`, `search`, `jump`), with Vim's conditions (marks and `%` only when the line changed; `h`/`l` only when they moved); undo, redo and the jumplist call `foldOpenAfter`.
- **The viewport.** `ViewportSync` finds the text area's `JViewport` (also later, through a hierarchy listener), takes the view's rows and columns from the viewport's size in lines and characters of the font, and on the user's own scrolling sets `topline` and moves the cursor into the window (`VimEditor.viewScrolled`).

- [ ] **Step 1: Write the failing test**

Create `plugins/editor/src/test/java/dev/jasper/editor/vim/ViewportSyncTest.java`:

```java
package dev.jasper.editor.vim;

import java.awt.Font;
import java.awt.Point;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The view and the text area's scroll pane move together (spec 5.1); the corpus checks the view itself. */
class ViewportSyncTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private record Setup(VimTextArea area, JScrollPane scroll, VimEditor editor) { }

    /** A text area of 60 lines in a scroll pane about 10 lines high and 30 characters wide. */
    private static Setup setup() {
        var area = new VimTextArea();
        area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
        StringBuilder text = new StringBuilder();
        for (int i = 1; i <= 60; i++) text.append("line ").append(i).append('\n');
        area.setText(text.toString());
        area.setCaretPosition(0);
        var scroll = new JScrollPane(area);
        int charWidth = area.getFontMetrics(area.getFont()).charWidth('m');
        scroll.setSize(30 * charWidth + 40, 10 * area.getLineHeight() + 4);
        scroll.doLayout();
        scroll.getViewport().doLayout();
        var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
        return new Setup(area, scroll, editor);
    }

    @Test void theViewTakesItsSizeFromTheScrollPane() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "j");
            var visible = s.scroll().getViewport().getViewRect();
            int charWidth = s.area().getFontMetrics(s.area().getFont()).charWidth('m');
            assertThat(s.editor().view().height()).isEqualTo(visible.height / s.area().getLineHeight());
            assertThat(s.editor().view().width()).isEqualTo(visible.width / charWidth);
            assertThat(s.editor().view().height()).isGreaterThan(5);
        });
    }

    @Test void theViewportShowsTheViewsFirstLine() throws Exception {
        onEdt(() -> {
            var s = setup();
            type(s.editor(), "40Gzt");
            assertThat(s.editor().view().topline).isEqualTo(40);
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 39));
            type(s.editor(), "<C-y>");
            assertThat(s.scroll().getViewport().getViewPosition().y).isEqualTo(yFor(s, 38));
        });
    }

    @Test void scrollingTheViewportMovesTheViewAndKeepsTheCursorInside() throws Exception {
        onEdt(() -> {
            var s = setup();
            s.scroll().getViewport().setViewPosition(new Point(0, yFor(s, 20)));
            assertThat(s.editor().view().topline).isEqualTo(21);
            assertThat(s.editor().cursor().line).as("the cursor comes into the window").isEqualTo(20);
            type(s.editor(), "j");
            assertThat(s.editor().cursor().line).isEqualTo(21);
            assertThat(s.editor().view().topline).as("a move inside the window does not scroll").isEqualTo(21);
        });
    }

    @Test void turningVimOffGivesTheTextAreaBackItsWrappingAndFoldNavigation() throws Exception {
        onEdt(() -> {
            var area = new VimTextArea();
            area.setWrapStyleWord(true);
            var filter = area.getNavigationFilter();
            var editor = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            assertThat(area.getWrapStyleWord()).as("Vim wraps at any character").isFalse();
            assertThat(area.getNavigationFilter()).as("the cursor may be in a closed fold").isNull();
            editor.close();
            assertThat(area.getWrapStyleWord()).isTrue();
            assertThat(area.getNavigationFilter()).isSameAs(filter);
        });
    }

    private static int yFor(Setup s, int line) {
        try { return s.area().yForLine(line); }
        catch (javax.swing.text.BadLocationException bad) { throw new AssertionError(bad); }
    }
}
```

- [ ] **Step 2: Run it to see it fail**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ViewportSyncTest'
```

Expected: compilation fails: `VimEditor.view` does not exist.

- [ ] **Step 3: Write the new classes**

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/View.java`:

```java
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
        int startcol = Text.virtcol(text, col, ts());
        int endcol = col < text.length() ? startcol + Text.width(text, col, startcol, ts()) - 1 : startcol;
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
        int oldTopline = topline, oldSkipcol = skipcol;
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
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/Folds.java`:

```java
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
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ViewCommands.java`:

```java
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
        if (c.options.flag("wrap")) {
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
```

Create `plugins/editor/src/main/java/dev/jasper/editor/vim/ViewportSync.java`:

```java
package dev.jasper.editor.vim;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.ComponentListener;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.event.ChangeListener;
import javax.swing.text.BadLocationException;

/**
 * Keeps the text area's scroll pane and {@link View} in step (spec 5.1): the view's rows and columns follow the
 * viewport's size in lines and characters, after each command the viewport shows the view's first line (and, without
 * 'wrap', its first column), and when the user scrolls the viewport (the wheel, the scroll bar) the view follows and
 * the cursor stays inside it, as Vim keeps it inside the window.
 */
final class ViewportSync {
    private final VimEditor editor;
    private final VimTextArea area;
    private JViewport viewport;
    private boolean moving;
    private final ChangeListener scrolled = event -> userScrolled();
    private final ComponentListener resized = new ComponentAdapter() {
        @Override public void componentResized(ComponentEvent event) { resize(); }
    };
    /** The text area may be put into a scroll pane after Vim turns on. */
    private final java.awt.event.HierarchyListener moved = event -> viewport();

    ViewportSync(VimEditor editor, VimTextArea area) {
        this.editor = editor;
        this.area = area;
        area.addHierarchyListener(moved);
        viewport();
    }

    /** @return the viewport the text area is in, listening to it once found; null when it is not in one */
    private JViewport viewport() {
        JViewport found = (JViewport) SwingUtilities.getAncestorOfClass(JViewport.class, area);
        if (found != viewport) {
            detach();
            viewport = found;
            if (viewport != null) {
                viewport.addChangeListener(scrolled);
                viewport.addComponentListener(resized);
                resize();
            }
        }
        return viewport;
    }

    /** Vim turned off: nothing is listened to any more. */
    void close() {
        area.removeHierarchyListener(moved);
        detach();
    }

    private void detach() {
        if (viewport == null) return;
        viewport.removeChangeListener(scrolled);
        viewport.removeComponentListener(resized);
        viewport = null;
    }

    /** The view's size from the viewport: whole lines and whole characters of the Editor's font. */
    void resize() {
        JViewport port = viewport;
        if (port == null) return;
        Rectangle visible = port.getViewRect();
        int lineHeight = Math.max(1, area.getLineHeight());
        int charWidth = Math.max(1, area.getFontMetrics(area.getFont()).charWidth('m'));
        if (visible.height <= 0 || visible.width <= 0) return;
        editor.view().resize(Math.max(1, visible.height / lineHeight), Math.max(1, visible.width / charWidth));
    }

    /** After a command: the viewport shows the view's first line and column. */
    void show() {
        JViewport port = viewport();
        if (port == null) return;
        View view = editor.view();
        try {
            int y = area.yForLine(view.topline - 1);
            if (y < 0) return;
            int charWidth = Math.max(1, area.getFontMetrics(area.getFont()).charWidth('m'));
            int x = editor.options().flag("wrap") ? 0 : view.leftcol * charWidth;
            Point target = new Point(x, y);
            if (!target.equals(port.getViewPosition())) {
                moving = true;
                try { port.setViewPosition(target); }
                finally { moving = false; }
            }
        } catch (BadLocationException ignored) { }
    }

    /** The user scrolled: the view's first line follows, and the cursor moves inside the window. */
    private void userScrolled() {
        if (moving || viewport == null) return;
        Point at = viewport.getViewPosition();
        int offset = area.viewToModel2D(new Point(0, at.y));
        int line;
        try { line = area.getLineOfOffset(Math.max(0, offset)); }
        catch (BadLocationException bad) { return; }
        View view = editor.view();
        int top = line + 1;
        // The first line only partly shown does not count, as Vim shows whole lines.
        try {
            if (area.yForLine(line) < at.y && top < editor.buffer().count()) top++;
        } catch (BadLocationException ignored) { }
        if (top == view.topline) return;
        editor.viewScrolled(top);
    }
}
```

- [ ] **Step 4: Replace the changed classes**

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Options.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Vim options (spec 3.8). The global instance holds global values; each editor has a local instance whose
 * buffer- and window-local options keep their own values. Setting an option on a local instance sets it there and
 * globally, as {@code :set} does. Defaults are Vim's with {@code -u NONE -N}, except where the Editor supplies them
 * ({@link EditorDefaults}) and 'showcmd' and 'incsearch', which are on (spec 3.8; 'hlsearch' stays off until plan V2
 * brings {@code :noh}).
 */
public final class Options {
    enum Type { BOOLEAN, NUMBER, STRING }

    /**
     * @param name the full name
     * @param abbreviation the short name, or the full name when there is none
     * @param type the value type
     * @param fallback the default
     * @param local whether each editor keeps its own value
     */
    record Def(String name, String abbreviation, Type type, Object fallback, boolean local) { }

    /** Every option this version knows. */
    static final List<Def> DEFS = List.of(
        new Def("autoindent", "ai", Type.BOOLEAN, false, true),
        new Def("backspace", "bs", Type.STRING, "indent,eol,start", false),
        new Def("clipboard", "cb", Type.STRING, "", false),
        new Def("comments", "com", Type.STRING, "s1:/*,mb:*,ex:*/,://,b:#,:%,:XCOMM,n:>,fb:-", true),
        new Def("expandtab", "et", Type.BOOLEAN, false, true),
        new Def("foldopen", "fdo", Type.STRING, "block,hor,mark,percent,quickfix,search,tag,undo", false),
        new Def("formatoptions", "fo", Type.STRING, "tcq", true),
        new Def("hlsearch", "hls", Type.BOOLEAN, false, false),
        new Def("ignorecase", "ic", Type.BOOLEAN, false, false),
        new Def("incsearch", "is", Type.BOOLEAN, true, false),
        new Def("iskeyword", "isk", Type.STRING, "@,48-57,_,192-255", true),
        new Def("joinspaces", "js", Type.BOOLEAN, true, false),
        new Def("matchpairs", "mps", Type.STRING, "(:),{:},[:]", true),
        new Def("nrformats", "nf", Type.STRING, "bin,octal,hex", true),
        new Def("report", "report", Type.NUMBER, 2, false),
        new Def("scrolljump", "sj", Type.NUMBER, 1, false),
        new Def("scrolloff", "so", Type.NUMBER, 0, false),
        new Def("shiftround", "sr", Type.BOOLEAN, false, false),
        new Def("shiftwidth", "sw", Type.NUMBER, 8, true),
        new Def("showcmd", "sc", Type.BOOLEAN, true, false),
        new Def("showmode", "smd", Type.BOOLEAN, true, false),
        new Def("sidescroll", "ss", Type.NUMBER, 0, false),
        new Def("sidescrolloff", "siso", Type.NUMBER, 0, false),
        new Def("smartcase", "scs", Type.BOOLEAN, false, false),
        new Def("softtabstop", "sts", Type.NUMBER, 0, true),
        new Def("startofline", "sol", Type.BOOLEAN, true, false),
        new Def("tabstop", "ts", Type.NUMBER, 8, true),
        new Def("textwidth", "tw", Type.NUMBER, 0, true),
        new Def("tildeop", "top", Type.BOOLEAN, false, false),
        new Def("whichwrap", "ww", Type.STRING, "b,s", false),
        new Def("wrap", "wrap", Type.BOOLEAN, true, true),
        new Def("wrapscan", "ws", Type.BOOLEAN, true, false));

    private final Options global;
    private final Map<String, Object> values = new HashMap<>();
    private final List<Runnable> listeners = new ArrayList<>();
    private String keywordSpec;
    private Keywords keywords;

    /** A global instance with Vim's defaults. */
    public Options() { this(null); }

    private Options(Options global) { this.global = global; }

    /** @return a local instance for one editor, backed by this global one */
    Options local() {
        if (global != null) throw new IllegalStateException("Only the global options have local copies");
        return new Options(this);
    }

    /** @param listener runs after any value of this instance changes */
    void onChanged(Runnable listener) { listeners.add(listener); }

    boolean flag(String name) { return (Boolean) get(name); }

    int number(String name) { return (Integer) get(name); }

    String string(String name) { return (String) get(name); }

    /** @return 'shiftwidth', or 'tabstop' when it is 0, as Vim's get_sw_value */
    int shiftwidth() {
        int sw = number("shiftwidth");
        return sw == 0 ? number("tabstop") : sw;
    }

    /** @return whether a comma-separated option lists {@code item}, such as 'clipboard' holding {@code unnamed} */
    boolean has(String name, String item) { return List.of(string(name).split(",")).contains(item); }

    Keywords keywords() {
        String spec = string("iskeyword");
        if (!spec.equals(keywordSpec)) {
            keywords = Keywords.parse(spec);
            keywordSpec = spec;
        }
        return keywords;
    }

    Object get(String name) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        if (global != null && def.local() && values.containsKey(def.name())) return values.get(def.name());
        Options root = global != null ? global : this;
        return root.values.getOrDefault(def.name(), def.fallback());
    }

    /** Sets a value here and, for a local instance, globally too (as {@code :set}). */
    void set(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null) {
            global.put(def, checked);
            if (def.local()) put(def, checked);
        } else put(def, checked);
    }

    /** Sets a value in this local instance only (as {@code :setlocal}); a global option is set globally. */
    void setLocal(String name, Object value) {
        Def def = def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
        Object checked = check(def, value);
        if (global != null && !def.local()) global.put(def, checked);
        else put(def, checked);
    }

    /**
     * Applies {@code :set} arguments: {@code name}, {@code noname}, {@code invname}, {@code name!}, {@code name&},
     * {@code name=value} and {@code name:value}, separated by spaces.
     *
     * @throws IllegalArgumentException with Vim's message for an unknown option or a bad value
     */
    void apply(String arguments) {
        for (String argument : arguments.trim().split("\\s+")) {
            if (argument.isEmpty()) continue;
            int equals = indexOfAny(argument, "=:");
            if (equals > 0) {
                char how = "+-^".indexOf(argument.charAt(equals - 1)) >= 0 && argument.charAt(equals) == '=' ? argument.charAt(equals - 1) : '=';
                String name = argument.substring(0, how == '=' ? equals : equals - 1);
                String text = argument.substring(equals + 1);
                Def def = required(name);
                switch (def.type()) {
                    case NUMBER -> {
                        int number;
                        try { number = Integer.parseInt(text); }
                        catch (NumberFormatException bad) { throw new IllegalArgumentException("E521: Number required after =: " + argument); }
                        int old = number(def.name());
                        set(def.name(), switch (how) { case '+' -> old + number; case '-' -> old - number; case '^' -> old * number; default -> number; });
                    }
                    case STRING -> set(def.name(), combine(string(def.name()), text, how));
                    case BOOLEAN -> throw new IllegalArgumentException("E474: Invalid argument: " + argument);
                }
            } else if (argument.endsWith("&")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                set(def.name(), def.fallback());
            } else if (argument.endsWith("!")) {
                Def def = required(argument.substring(0, argument.length() - 1));
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else if (def(argument).isPresent()) {
                Def def = def(argument).get();
                booleanOnly(def, argument);
                set(def.name(), true);
            } else if (argument.startsWith("no") && def(argument.substring(2)).isPresent()) {
                Def def = def(argument.substring(2)).get();
                booleanOnly(def, argument);
                set(def.name(), false);
            } else if (argument.startsWith("inv") && def(argument.substring(3)).isPresent()) {
                Def def = def(argument.substring(3)).get();
                booleanOnly(def, argument);
                set(def.name(), !flag(def.name()));
            } else throw new IllegalArgumentException("E518: Unknown option: " + argument);
        }
    }

    /** {@code :set opt+=}, {@code -=} and {@code ^=} on a comma-separated option (option.c's add, remove and prepend). */
    private static String combine(String old, String item, char how) {
        List<String> items = new ArrayList<>(old.isEmpty() ? List.of() : List.of(old.split(",", -1)));
        switch (how) {
            case '+' -> {
                if (items.contains(item)) return old;
                items.add(item);
            }
            case '^' -> {
                if (items.contains(item)) return old;
                items.addFirst(item);
            }
            case '-' -> items.remove(item);
            default -> { return item; }
        }
        return String.join(",", items);
    }

    static Optional<Def> def(String name) {
        String lower = name.toLowerCase(Locale.ROOT);
        return DEFS.stream().filter(def -> def.name().equals(lower) || def.abbreviation().equals(lower)).findFirst();
    }

    private void put(Def def, Object value) {
        Object old = values.put(def.name(), value);
        if (!value.equals(old)) for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private static Def required(String name) {
        return def(name).orElseThrow(() -> new IllegalArgumentException("E518: Unknown option: " + name));
    }

    private static void booleanOnly(Def def, String argument) {
        if (def.type() != Type.BOOLEAN) throw new IllegalArgumentException("E474: Invalid argument: " + argument);
    }

    private static Object check(Def def, Object value) {
        return switch (def.type()) {
            case BOOLEAN -> (Boolean) value;
            case NUMBER -> {
                int number = (Integer) value;
                if (number < 0 || def.name().equals("tabstop") && number == 0) throw new IllegalArgumentException("E487: Argument must be positive: " + def.name() + "=" + number);
                yield number;
            }
            case STRING -> {
                String text = (String) value;
                if (def.name().equals("iskeyword")) Keywords.parse(text);
                yield text;
            }
        };
    }

    private static int indexOfAny(String text, String characters) {
        for (int i = 0; i < text.length(); i++) if (characters.indexOf(text.charAt(i)) >= 0) return i;
        return -1;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/EditorDefaults.java` with:

```java
package dev.jasper.editor.vim;

/**
 * Option defaults the Editor supplies for a tab (spec 3.8), used until the user sets the option.
 *
 * @param tabstop the Editor's tab size, used for 'tabstop' and 'shiftwidth'
 * @param expandtab whether the tab indents with spaces
 * @param autoindent whether new lines copy the indent (the Editor auto-indents, so Jasper passes true)
 * @param wrap whether long lines wrap (the tab's Wrap state), for 'wrap'
 */
public record EditorDefaults(int tabstop, boolean expandtab, boolean autoindent, boolean wrap) {
    /** Vim's own defaults ({@code -u NONE}), which the corpus uses. */
    public static final EditorDefaults VIM = new EditorDefaults(8, false, false, true);

    /**
     * Defaults with lines wrapping.
     *
     * @param tabstop the Editor's tab size
     * @param expandtab whether the tab indents with spaces
     * @param autoindent whether new lines copy the indent
     */
    public EditorDefaults(int tabstop, boolean expandtab, boolean autoindent) { this(tabstop, expandtab, autoindent, true); }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Context.java` with:

```java
package dev.jasper.editor.vim;

/** What one command runs with: the editor, its count, register and argument, and the operator waiting for it. */
final class Context {
    final VimEditor editor;
    final Buffer buffer;
    final Options options;
    final Registers registers;
    final Vim vim;
    /** The count, 1 when none was typed; after an operator, the product of both counts. */
    int count = 1;
    boolean hasCount;
    /** The register typed with {@code "x}, or 0. */
    char register;
    /** The key read as the command's argument ({@code f}'s character), or null. */
    KeyInput argument;
    /** The pending operator's keys ({@code d}, {@code gU}), or null in Normal mode. */
    String operator;
    /** The forced motion type after the operator: {@code v}, {@code V}, or 0. */
    char forced;
    /** In Visual mode, the other end of the area (Vim's VIsual); null otherwise. */
    Pos visual;
    /** The command line typed for {@code /} and {@code ?}, or null. */
    String line;
    /** For an operator, where the cursor was when it started (Vim's oap->cursor_start, for {@code gw}). */
    Pos cursorStart;

    Context(VimEditor editor) {
        this.editor = editor;
        this.buffer = editor.buffer();
        this.options = editor.options();
        this.registers = editor.vim().registers();
        this.vim = editor.vim();
    }

    /** @return a copy of the cursor position */
    Pos cursor() { return editor.cursor(); }

    Walk walk(boolean bigword) {
        var walk = new Walk(buffer, options.keywords());
        walk.bigword = bigword;
        Folds folds = editor.folds();
        if (folds.any()) walk.folds = folds::closed;
        return walk;
    }

    /** @return the argument's character, or -1 when the argument is a named key or has modifiers */
    int argumentChar() { return argument == null ? -1 : argument.plainChar(); }

    int tabstop() { return options.number("tabstop"); }

    /** @return whether Visual mode is active, where the cursor may sit on the end of a line (Vim's past_line) */
    boolean inVisual() { return visual != null; }

    static VimFailure fail() { return VimFailure.bell(); }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Walk.java` with:

```java
package dev.jasper.editor.vim;

/**
 * Vim's character walking, ported from misc2.c, search.c and textobject.c so motions and text objects match Vim:
 * {@code inc}, {@code dec}, {@code incl}, {@code decl}, {@code gchar}, {@code cls} and the word movers. Positions
 * are mutated in place, as Vim's are. Return codes are Vim's: 0 moved within the line, 1 moved to another line,
 * 2 moved onto the end of the line, -1 could not move.
 */
final class Walk {
    static final int FAIL = -1;

    private final Buffer buffer;
    private final Keywords keywords;
    /** Vim's cls_bigword: every non-blank is one class, for W, B, E and gE. */
    boolean bigword;

    /** The closed fold holding a line (1-based) as {first, last}, or null; null when there are no folds. */
    java.util.function.IntFunction<int[]> folds;

    /** fwd_word and end_word: from inside a closed fold, go to the last character of its last line first. */
    private void toFoldEnd(Pos pos) {
        int[] fold = folds == null ? null : folds.apply(pos.line + 1);
        if (fold == null) return;
        pos.line = fold[1] - 1;
        String text = buffer.line(pos.line);
        pos.col = text.isEmpty() ? 0 : Text.last(text);
    }

    /** bck_word: from inside a closed fold, go to the start of its first line first. */
    private void toFoldStart(Pos pos) {
        int[] fold = folds == null ? null : folds.apply(pos.line + 1);
        if (fold == null) return;
        pos.line = fold[0] - 1;
        pos.col = 0;
    }

    Walk(Buffer buffer, Keywords keywords) {
        this.buffer = buffer;
        this.keywords = keywords;
    }

    Buffer buffer() { return buffer; }

    Keywords keywords() { return keywords; }

    /** @return the character at the position, or 0 on the end of the line (Vim's NUL) */
    int gchar(Pos pos) {
        String line = buffer.line(pos.line);
        return pos.col < line.length() ? line.codePointAt(pos.col) : 0;
    }

    int inc(Pos pos) {
        String line = buffer.line(pos.line);
        if (pos.col < line.length()) {
            pos.col = Text.next(line, pos.col);
            return pos.col < line.length() ? 0 : 2;
        }
        if (pos.line < buffer.count() - 1) {
            pos.line++;
            pos.col = 0;
            return 1;
        }
        return FAIL;
    }

    int dec(Pos pos) {
        if (pos.col > 0) {
            pos.col = Text.previous(buffer.line(pos.line), pos.col);
            return 0;
        }
        if (pos.line > 0) {
            pos.line--;
            pos.col = buffer.line(pos.line).length();
            return 1;
        }
        return FAIL;
    }

    /** inc, skipping the end of a non-empty line. */
    int incl(Pos pos) {
        int result = inc(pos);
        if (result >= 1 && pos.col > 0) result = inc(pos);
        return result;
    }

    /** dec, skipping the end of a non-empty line. */
    int decl(Pos pos) {
        int result = dec(pos);
        if (result == 1 && pos.col > 0) result = dec(pos);
        return result;
    }

    /** @return the character class at the position: 0 blank (or end of line), 1 punctuation, 2 and up words */
    int cls(Pos pos) {
        int c = gchar(pos);
        if (c == ' ' || c == '\t' || c == 0) return 0;
        int cls = keywords.charClass(c);
        return cls != 0 && bigword ? 1 : cls;
    }

    boolean lineEmpty(int line) { return buffer.line(line).isEmpty(); }

    /** @return whether only spaces and tabs precede column {@code pos.col + extra} (Vim's inindent) */
    boolean inIndent(Pos pos, int extra) { return Text.firstNonBlank(buffer.line(pos.line)) >= pos.col + extra; }

    /** Vim's fwd_word: moves to the start of the {@code count}-th next word. */
    boolean fwdWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            toFoldEnd(pos);
            int sclass = cls(pos);
            boolean lastLine = pos.line == buffer.count() - 1;
            int i = inc(pos);
            if (i == FAIL || i >= 1 && lastLine) return false;
            if (i >= 1 && eol && count == 0) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = inc(pos);
                    if (i == FAIL || i >= 1 && eol && count == 0) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = inc(pos);
                if (i == FAIL || i >= 1 && eol && count == 0) return true;
            }
        }
        return true;
    }

    /** Vim's bck_word: moves to the start of the {@code count}-th previous word. */
    boolean bckWord(Pos pos, int count, boolean stop) {
        while (--count >= 0) {
            toFoldStart(pos);
            int sclass = cls(pos);
            if (dec(pos) == FAIL) return false;
            boolean finished = false;
            if (!stop || sclass == cls(pos) || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line)) {
                        finished = true;
                        break;
                    }
                    if (dec(pos) == FAIL) return true;
                }
                if (!finished && skipChars(pos, cls(pos), false)) return true;
            }
            if (!finished) inc(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's end_word: moves to the end of the {@code count}-th word. */
    boolean endWord(Pos pos, int count, boolean stop, boolean empty) {
        while (--count >= 0) {
            toFoldEnd(pos);
            int sclass = cls(pos);
            if (inc(pos) == FAIL) return false;
            boolean finished = false;
            if (cls(pos) == sclass && sclass != 0) {
                if (skipChars(pos, sclass, true)) return false;
            } else if (!stop || sclass == 0) {
                while (cls(pos) == 0) {
                    if (pos.col == 0 && lineEmpty(pos.line) && empty) {
                        finished = true;
                        break;
                    }
                    if (inc(pos) == FAIL) return false;
                }
                if (!finished && skipChars(pos, cls(pos), true)) return false;
            }
            if (!finished) dec(pos);
            stop = false;
        }
        return true;
    }

    /** Vim's bckend_word: moves to the end of the {@code count}-th previous word. */
    boolean bckendWord(Pos pos, int count, boolean eol) {
        while (--count >= 0) {
            int sclass = cls(pos);
            int i = dec(pos);
            if (i == FAIL) return false;
            if (eol && i == 1) return true;
            if (sclass != 0) {
                while (cls(pos) == sclass) {
                    i = dec(pos);
                    if (i == FAIL || eol && i == 1) return true;
                }
            }
            while (cls(pos) == 0) {
                if (pos.col == 0 && lineEmpty(pos.line)) break;
                i = dec(pos);
                if (i == FAIL || eol && i == 1) return true;
            }
        }
        return true;
    }

    /** @return true when the start or end of the buffer was hit */
    boolean skipChars(Pos pos, int cclass, boolean forward) {
        while (cls(pos) == cclass) if ((forward ? inc(pos) : dec(pos)) == FAIL) return true;
        return false;
    }

    /** Vim's back_in_line: back to the start of the run of characters of the cursor's class, within the line. */
    void backInLine(Pos pos) {
        int sclass = cls(pos);
        while (pos.col != 0) {
            dec(pos);
            if (cls(pos) != sclass) {
                inc(pos);
                break;
            }
        }
    }

    /** Vim's oneleft: false at the start of the line. */
    boolean oneLeft(Pos pos) {
        if (pos.col == 0) return false;
        pos.col = Text.previous(buffer.line(pos.line), pos.col);
        return true;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Motions.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Column;

/**
 * Normal-mode and operator-pending motions (spec 4.1), ported from Vim's normal.c and search.c so that where they
 * land, whether they fail, and how an operator treats them match Vim. A motion returns null to fail.
 */
final class Motions {
    private static final String PARAGRAPHS = "IPLPPPQPP TPHPLIPpLpItpplpipbp", SECTIONS = "SHNHH HUnhsh";

    private Motions() { }

    static void register(VimCommands commands) {
        commands.motion("h", "left", c -> left(c, false));
        commands.motion("<Left>", "left", c -> left(c, false));
        commands.motion("<BS>", "left, wrapping to the previous line ('whichwrap' b)", c -> left(c, true));
        commands.motion("<C-h>", "left, wrapping like BS", c -> left(c, true));
        commands.motion("l", "right", c -> right(c, 'l'));
        commands.motion("<Right>", "right", c -> right(c, '>'));
        commands.motion(" ", "right, wrapping to the next line ('whichwrap' s)", c -> right(c, 's'));
        commands.motion("j", "down", c -> down(c, c.count));
        commands.motion("<Down>", "down", c -> down(c, c.count));
        commands.motion("<C-j>", "down", c -> down(c, c.count));
        commands.motion("<C-n>", "down", c -> down(c, c.count));
        commands.motion("k", "up", c -> up(c, c.count));
        commands.motion("<Up>", "up", c -> up(c, c.count));
        commands.motion("<C-p>", "up", c -> up(c, c.count));
        commands.motion("+", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("<CR>", "down to the first non-blank", c -> firstNonBlank(c, down(c, c.count)));
        commands.motion("-", "up to the first non-blank", c -> firstNonBlank(c, up(c, c.count)));
        commands.motion("_", "count - 1 lines down, first non-blank", c -> firstNonBlank(c, c.count == 1 ? Target.linewise(c.cursor(), Column.KEEP) : down(c, c.count - 1)));
        commands.motion("G", "to the last line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : c.buffer.count()));
        commands.motion("gg", "to the first line, or line {count}", c -> goToLine(c, c.hasCount ? c.count : 1));
        commands.motion("0", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("<Home>", "to the first column", c -> Target.exclusive(new Pos(c.cursor().line, 0)));
        commands.motion("^", "to the first non-blank", c -> Target.exclusive(new Pos(c.cursor().line, Text.firstNonBlank(c.buffer.line(c.cursor().line)))));
        commands.motion("$", "to the end of the line, {count} - 1 lines down", Motions::endOfLine);
        commands.motion("<End>", "to the end of the line", Motions::endOfLine);
        commands.motion("g_", "to the last non-blank, {count} - 1 lines down", Motions::lastNonBlank);
        commands.motion("|", "to display column {count}", c -> Target.exclusive(new Pos(c.cursor().line, Text.columnAt(c.buffer.line(c.cursor().line), c.count - 1, c.tabstop()))));
        commands.motion("go", "to character {count} of the text", Motions::goToOffset);
        commands.motion("w", "to the start of the next word", c -> word(c, false, false));
        commands.motion("W", "to the start of the next WORD", c -> word(c, true, false));
        commands.motion("e", "to the end of the word", c -> word(c, false, true));
        commands.motion("E", "to the end of the WORD", c -> word(c, true, true));
        commands.motion("b", "back to the start of the word", c -> backWord(c, false));
        commands.motion("B", "back to the start of the WORD", c -> backWord(c, true));
        commands.motion("ge", "back to the end of the previous word", c -> backEnd(c, false));
        commands.motion("gE", "back to the end of the previous WORD", c -> backEnd(c, true));
        commands.motionWithChar("f", "to the {count}-th {char} to the right", c -> find(c, true, false, false, false));
        commands.motionWithChar("F", "to the {count}-th {char} to the left", c -> find(c, false, false, false, false));
        commands.motionWithChar("t", "till before the {count}-th {char} to the right", c -> find(c, true, true, false, false));
        commands.motionWithChar("T", "till after the {count}-th {char} to the left", c -> find(c, false, true, false, false));
        commands.motion(";", "repeat the last f, F, t or T", c -> find(c, true, false, true, false));
        commands.motion(",", "repeat the last f, F, t or T the other way", c -> find(c, true, false, true, true));
        commands.motion("(", "back {count} sentences", c -> sentence(c, false));
        commands.motion(")", "forward {count} sentences", c -> sentence(c, true));
        commands.motion("{", "back {count} paragraphs", c -> paragraph(c, false, 0, false));
        commands.motion("}", "forward {count} paragraphs", c -> paragraph(c, true, 0, false));
        commands.motion("[[", "back to a { in column 1", c -> section(c, false, '{'));
        commands.motion("]]", "forward to a { in column 1", c -> section(c, true, '{'));
        commands.motion("[]", "back to a } in column 1", c -> section(c, false, '}'));
        commands.motion("][", "forward to a } in column 1", c -> section(c, true, '}'));
        commands.motion("%", "to the matching bracket, or to {count} percent of the text", Motions::percent);
        commands.motion("[(", "back to the unmatched (", c -> unmatched(c, '(', ')', true));
        commands.motion("[{", "back to the unmatched {", c -> unmatched(c, '{', '}', true));
        commands.motion("])", "forward to the unmatched )", c -> unmatched(c, '(', ')', false));
        commands.motion("]}", "forward to the unmatched }", c -> unmatched(c, '{', '}', false));
    }

    /** nv_left: fails only when it could not move at all and no operator waits. */
    static Target left(Context c, boolean backspace) {
        Pos p = c.cursor();
        boolean wrap = backspace && c.options.has("whichwrap", "b");
        boolean moved = false, noAdjust = false;
        for (int n = c.count; n > 0; n--) {
            if (p.col > 0) {
                p.col = Text.previous(c.buffer.line(p.line), p.col);
                moved = true;
            } else if (wrap && p.line > 0) {
                p.line--;
                String line = c.buffer.line(p.line);
                p.col = Text.last(line);
                // Deleting the line break before the first character: Vim puts the cursor past the end of the line above.
                if (("d".equals(c.operator) || "c".equals(c.operator)) && !line.isEmpty()) {
                    p.col = line.length();
                    noAdjust = true;
                }
                moved = true;
            } else break;
        }
        if (!moved && c.operator == null) return null;
        Target target = Target.exclusive(p);
        return noAdjust ? target.withoutAdjust() : target;
    }

    /** nv_right: with an operator, stopping at the last character makes the motion inclusive. */
    static Target right(Context c, char which) {
        Pos p = c.cursor();
        boolean wraps = c.options.has("whichwrap", String.valueOf(which));
        boolean inclusive = false;
        // In Visual mode l may go onto the end of the line (nv_right's past_line).
        boolean pastLine = c.inVisual();
        for (int n = c.count; n > 0; n--) {
            String line = c.buffer.line(p.line);
            int next = Text.next(line, p.col);
            if (p.col < line.length() && (next < line.length() || pastLine)) {
                p.col = next;
                continue;
            }
            if (wraps && p.line < c.buffer.count() - 1) {
                if (c.operator != null && !inclusive && !line.isEmpty()) inclusive = true;
                else {
                    p.line++;
                    p.col = 0;
                    inclusive = false;
                }
                continue;
            }
            if (c.operator == null) {
                if (n == c.count) return null;
            } else if (!line.isEmpty()) inclusive = true;
            break;
        }
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    /**
     * cursor_down: fails only on the last line (or in a closed fold that ends there); a count past the end stops on
     * the last line; a closed fold counts as one line.
     */
    static Target down(Context c, int n) {
        int lnum = c.cursor().line + 1;
        if (n > 0) {
            int[] fold = c.editor.folds().closed(lnum);
            if ((fold != null ? fold[1] : lnum) >= c.buffer.count()) return null;
        }
        int line = c.editor.view().downFrom(lnum, n) - 1;
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    /** cursor_up: fails on the first line; a closed fold counts as one line. */
    static Target up(Context c, int n) {
        int lnum = c.cursor().line + 1;
        if (n > 0 && lnum <= 1) return null;
        int line = c.editor.view().upFrom(lnum, n, c.editor.mode().inserting()) - 1;
        return Target.linewise(new Pos(line, c.editor.wantedColumn(line)), Column.KEEP);
    }

    private static Target firstNonBlank(Context c, Target target) {
        if (target == null) return null;
        int line = target.pos().line;
        return Target.linewise(new Pos(line, Text.firstNonBlank(c.buffer.line(line))), Column.RESET);
    }

    /** G, gg and N%: linewise to a line, the first non-blank with 'startofline'. */
    static Target goToLine(Context c, int number) {
        int line = Math.max(0, Math.min(number, c.buffer.count()) - 1);
        boolean sol = c.options.flag("startofline");
        int col = sol ? Text.firstNonBlank(c.buffer.line(line)) : c.editor.wantedColumn(line);
        return Target.linewise(new Pos(line, col), sol ? Column.RESET : Column.KEEP).asJump();
    }

    static Target endOfLine(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        // In Visual mode the cursor goes onto the end of the line, so the area takes the line break (coladvance).
        String text = c.buffer.line(line);
        return new Target(new Pos(line, c.inVisual() ? text.length() : Text.last(text)), Target.Kind.INCLUSIVE, Column.END, false, false, true);
    }

    static Target lastNonBlank(Context c) {
        Pos p = c.cursor();
        int down = c.count - 1, last = c.buffer.count() - 1;
        if (down > 0 && p.line >= last) return null;
        int line = Math.min(last, p.line + down);
        String text = c.buffer.line(line);
        int col = Text.last(text);
        while (col > 0 && (text.charAt(col) == ' ' || text.charAt(col) == '\t')) col = Text.previous(text, col);
        return Target.inclusive(new Pos(line, col));
    }

    static Target goToOffset(Context c) {
        int offset = Math.max(0, Math.min(c.count - 1, c.buffer.length() - 1));
        return Target.exclusive(c.buffer.pos(Math.max(0, offset))).asJump();
    }

    /** nv_wordcmd: w, W, e and E, with the cw special case and the stop at the end of a line for operators. */
    static Target word(Context c, boolean bigword, boolean end) {
        Pos start = c.cursor(), p = start.copy();
        Walk walk = c.walk(bigword);
        boolean wordEnd = end, stop = false;
        if (!end && "c".equals(c.operator)) {
            int ch = walk.gchar(p);
            if (ch != 0) {
                if (ch != ' ' && ch != '\t') wordEnd = true;
                stop = true;
            }
        }
        boolean ok = wordEnd ? walk.endWord(p, c.count, stop, false) : walk.fwdWord(p, c.count, c.operator != null);
        boolean inclusive = wordEnd;
        // adjust_cursor: never leave the cursor on the end of the line after moving forward.
        if (p.compareTo(start) > 0 && p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        if (!ok && c.operator == null) return null;
        return inclusive ? Target.inclusive(p) : Target.exclusive(p);
    }

    static Target backWord(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckWord(p, c.count, false) ? Target.exclusive(p) : null;
    }

    static Target backEnd(Context c, boolean bigword) {
        Pos p = c.cursor();
        return c.walk(bigword).bckendWord(p, c.count, false) ? Target.inclusive(p) : null;
    }

    /** searchc: f, F, t, T, ; and ,. */
    static Target find(Context c, boolean forward, boolean till, boolean repeat, boolean reverse) {
        int ch;
        boolean stop = true;
        if (repeat) {
            Vim.Find last = c.vim.lastFind();
            if (last == null) return null;
            forward = reverse != last.forward();
            till = last.till();
            ch = last.character();
            // Without ';' in 'cpoptions', ";" after t moves at least one character.
            if (c.count == 1 && till) stop = false;
        } else {
            ch = c.argumentChar();
            if (c.argument != null && c.argument.is("<Tab>")) ch = '\t';
            if (ch < 0) return null;
            c.vim.lastFind(new Vim.Find(forward, till, ch));
        }
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int col = p.col;
        for (int count = c.count; count > 0; count--) {
            while (true) {
                if (forward) {
                    col = Text.next(line, col);
                    if (col >= line.length()) return null;
                } else {
                    if (col == 0) return null;
                    col = Text.previous(line, col);
                }
                if (line.codePointAt(col) == ch && stop) break;
                stop = true;
            }
        }
        if (till) col = forward ? Text.previous(line, col) : Text.next(line, col);
        Pos result = new Pos(p.line, col);
        return forward ? Target.inclusive(result) : Target.exclusive(result);
    }

    /** nv_brace: ( and ). */
    static Target sentence(Context c, boolean forward) {
        Pos p = c.cursor();
        Walk walk = c.walk(false);
        if (!findSentence(walk, p, forward, c.count)) return null;
        boolean inclusive = false;
        if (p.col > 0 && walk.gchar(p) == 0) {
            p.col = Text.previous(c.buffer.line(p.line), p.col);
            inclusive = true;
        }
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_findpar: { and }. */
    static Target paragraph(Context c, boolean forward, int what, boolean both) {
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both, c.editor.folds().any() ? c.editor.folds()::closed : null);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).usingRegisterOne().asJump();
    }

    /** nv_brackets: [[, ]], [] and ][. */
    static Target section(Context c, boolean forward, char what) {
        boolean both = c.operator != null && forward && what == '{';
        Pos p = findParagraph(c.buffer, c.cursor().line, forward, c.count, what, both, c.editor.folds().any() ? c.editor.folds()::closed : null);
        if (p == null) return null;
        boolean inclusive = p.line == c.buffer.count() - 1 && what != '}' && forward && !c.buffer.line(p.line).isEmpty();
        if (!inclusive && c.operator == null) p.col = Text.firstNonBlank(c.buffer.line(p.line));
        return (inclusive ? Target.inclusive(p) : Target.exclusive(p)).asJump();
    }

    static Target percent(Context c) {
        if (c.hasCount) {
            if (c.count > 100) return null;
            return goToLine(c, (int) ((c.count * (long) c.buffer.count() + 99) / 100)).withColumn(Column.RESET);
        }
        Pos match = Matching.percent(c.buffer, c.cursor(), c.options.string("matchpairs"));
        return match == null ? null : Target.inclusive(match).asJump().usingRegisterOne();
    }

    static Target unmatched(Context c, char open, char close, boolean backward) {
        Pos p = c.cursor();
        for (int n = c.count; n > 0; n--) {
            Pos found = Matching.unmatched(c.buffer, p, open, close, backward, true);
            if (found == null) {
                if (n == c.count) return null;
                break;
            }
            p = found;
        }
        return Target.exclusive(p).asJump();
    }

    /** findpar (textobject.c): the {@code count}-th paragraph or section boundary; null when there are too few. */
    static Pos findParagraph(Buffer buffer, int start, boolean forward, int count, int what, boolean both,
                             java.util.function.IntFunction<int[]> folds) {
        int curr = start, dir = forward ? 1 : -1, last = buffer.count() - 1;
        while (count-- > 0) {
            boolean didSkip = false;
            for (boolean first = true; ; first = false) {
                if (!buffer.line(curr).isEmpty()) didSkip = true;
                // A closed fold the search starts in is skipped whole.
                boolean foldSkipped = false;
                int[] fold = first && folds != null ? folds.apply(curr + 1) : null;
                if (fold != null) {
                    curr = (dir > 0 ? fold[1] : fold[0]) - 1 + dir;
                    foldSkipped = true;
                }
                if (!first && didSkip && startPS(buffer, curr, what, both)) break;
                if (foldSkipped) curr -= dir;
                curr += dir;
                if (curr < 0 || curr > last) {
                    if (count > 0) return null;
                    curr -= dir;
                    break;
                }
            }
        }
        if (both && buffer.line(curr).startsWith("}")) curr = Math.min(curr + 1, last);
        Pos p = new Pos(curr, 0);
        if (curr == last && what != '}' && forward) p.col = Text.last(buffer.line(curr));
        return p;
    }

    /** startPS: a paragraph or section starts at the line (an empty line for {@code para} 0). */
    static boolean startPS(Buffer buffer, int line, int para, boolean both) {
        String s = buffer.line(line);
        int c = s.isEmpty() ? 0 : s.charAt(0);
        if (c == para || c == '\f' || both && c == '}') return true;
        return c == '.' && (inMacro(SECTIONS, s) || para == 0 && inMacro(PARAGRAPHS, s));
    }

    /** inmacro: the nroff macro after the line's leading dot is listed in {@code macros} (two characters each). */
    private static boolean inMacro(String macros, String line) {
        char s0 = line.length() > 1 ? line.charAt(1) : 0, s1 = line.length() > 2 ? line.charAt(2) : 0;
        for (int i = 0; i < macros.length(); i += 2) {
            char m0 = macros.charAt(i), m1 = i + 1 < macros.length() ? macros.charAt(i + 1) : 0;
            boolean first = m0 == s0 || m0 == ' ' && (s0 == 0 || s0 == ' ');
            boolean second = m1 == s1 || (m1 == 0 || m1 == ' ') && (s0 == 0 || s1 == 0 || s1 == ' ');
            if (first && second) return true;
        }
        return false;
    }

    /** findsent (textobject.c). */
    static boolean findSentence(Walk walk, Pos pos, boolean forward, int count) {
        Buffer buffer = walk.buffer();
        boolean noskip = false;
        while (count-- > 0) {
            boolean found = false;
            if (walk.gchar(pos) == 0) {
                do {
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) break;
                } while (walk.gchar(pos) == 0);
                if (forward) found = true;
            } else if (forward && pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                if (pos.line == buffer.count() - 1) return false;
                pos.line++;
                found = true;
            } else if (!forward) walk.decl(pos);

            if (!found) {
                boolean foundDot = false;
                int c;
                while ((c = walk.gchar(pos)) == ' ' || c == '\t' || c != 0 && ".!?)]\"'".indexOf(c) >= 0) {
                    Pos t = pos.copy();
                    if (walk.decl(t) == Walk.FAIL || buffer.line(t.line).isEmpty() && forward) break;
                    if (foundDot) break;
                    if (".!?".indexOf(c) >= 0) foundDot = true;
                    int before = walk.gchar(t);
                    if (")]\"'".indexOf(c) >= 0 && (before == 0 || ".!?)]\"'".indexOf(before) < 0)) break;
                    walk.decl(pos);
                }
                int startLine = pos.line;
                while (true) {
                    c = walk.gchar(pos);
                    if (c == 0 || pos.col == 0 && startPS(buffer, pos.line, 0, false)) {
                        if (!forward && pos.line != startLine) {
                            pos.line++;
                            pos.col = Math.min(pos.col, buffer.line(pos.line).length());
                        }
                        break;
                    }
                    if (c == '.' || c == '!' || c == '?') {
                        Pos t = pos.copy();
                        int after;
                        while (true) {
                            if (walk.inc(t) == Walk.FAIL) {
                                after = -1;
                                break;
                            }
                            after = walk.gchar(t);
                            if (after == 0 || ")]\"'".indexOf(after) < 0) break;
                        }
                        if (after == -1 || after == ' ' || after == '\t' || after == 0) {
                            pos.set(t);
                            if (walk.gchar(pos) == 0) walk.inc(pos);
                            break;
                        }
                    }
                    if ((forward ? walk.incl(pos) : walk.decl(pos)) == Walk.FAIL) {
                        if (count > 0) return false;
                        noskip = true;
                        break;
                    }
                }
            }
            while (!noskip && (walk.gchar(pos) == ' ' || walk.gchar(pos) == '\t')) if (walk.incl(pos) == Walk.FAIL) break;
        }
        return true;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Search.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.regex.VimRegex;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;

/**
 * Pattern search (spec 3.7), ported from Vim's search.c: searchit (the count loop, where a forward search may start
 * and 'wrapscan') and do_search (offsets and {@code ;}), with normal.c's normal_search, nv_next and nv_ident for
 * {@code n}, {@code N}, {@code *} and {@code #}. The last pattern, its direction and offset are global, as in Vim.
 */
final class Search {
    /** The last search's offset (Vim's soffset): a line offset, or a character offset from the match's start or end. */
    record Offset(boolean line, boolean end, long off) {
        static final Offset NONE = new Offset(false, false, 0);
    }

    /** A match: its start, and the position after its end. */
    record Match(Pos start, Pos end) { }

    private String pattern;
    private char direction = '/';
    private Offset offset = Offset.NONE;
    private boolean noHighlight;
    private final List<String> history = new ArrayList<>();

    /** @return the last search pattern, or null */
    String pattern() { return pattern; }

    /** @return {@code /} or {@code ?} */
    char direction() { return direction; }

    /** @return whether {@code :noh} hid the highlight until the next search */
    boolean highlightHidden() { return noHighlight; }

    void hideHighlight() { noHighlight = true; }

    /** @return the search history, oldest first */
    List<String> history() { return history; }

    void remember(String line) {
        if (line.isEmpty()) return;
        history.remove(line);
        history.add(line);
        if (history.size() > 50) history.removeFirst();
    }

    static void register(VimCommands commands) {
        commands.motionWithLine("/", "search forward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '/', c.line));
        commands.motionWithLine("?", "search backward for a pattern, with an offset", c -> c.editor.vim().search().command(c, '?', c.line));
        commands.motion("n", "repeat the last search", c -> c.editor.vim().search().next(c, false));
        commands.motion("N", "repeat the last search in the other direction", c -> c.editor.vim().search().next(c, true));
        commands.motion("*", "search forward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '*', false));
        commands.motion("#", "search backward for the keyword under the cursor", c -> c.editor.vim().search().word(c, '#', false));
        commands.motion("g*", "search forward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '*', true));
        commands.motion("g#", "search backward for the keyword under the cursor, also inside words", c -> c.editor.vim().search().word(c, '#', true));
    }

    /** nv_search: the typed line is a pattern, perhaps with an offset and {@code ;} searches after it. */
    Target command(Context c, char dir, String line) {
        remember(line);
        return normal(c, dir, line, true, false, false);
    }

    /** nv_next: n and N; if an offset leaves the cursor where it was, the search is repeated once more. */
    Target next(Context c, boolean reverse) {
        Pos old = c.cursor();
        Target target = normal(c, (char) 0, null, true, reverse, false);
        if (target != null && target.pos().equals(old) && !lastWrapped) {
            Context again = copy(c);
            again.count = c.count + 1;
            Target retry = normal(again, (char) 0, null, true, reverse, false);
            if (retry != null) return retry;
        }
        return target;
    }

    private static Context copy(Context c) {
        Context copy = new Context(c.editor);
        copy.count = c.count;
        copy.hasCount = c.hasCount;
        copy.operator = c.operator;
        copy.visual = c.visual;
        return copy;
    }

    /** nv_ident for * # g* g#: the keyword (or other text) under or after the cursor, as a whole word unless g. */
    Target word(Context c, char command, boolean g) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        String line = b.line(p.line);
        Keywords keywords = c.options.keywords();
        int[] found = identifier(line, p.col, keywords);
        if (found == null) throw new VimFailure("E348: No string under cursor");
        int start = found[0], end = found[1];
        String text = line.substring(start, end);
        StringBuilder pat = new StringBuilder();
        if (!g && keywords.isWord(text.codePointAt(0))) pat.append("\\<");
        String special = command == '#' ? "/?.*~[^$\\" : "/.*~[^$\\";
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (special.indexOf(ch) >= 0) pat.append('\\');
            pat.append(ch);
        }
        if (!g && keywords.isWord(text.codePointBefore(text.length()))) pat.append("\\>");
        remember(pat.toString());
        // The previous context is set first, so `` returns here; then the cursor goes to the word's start, so the
        // search skips the word.
        Context from = copy(c);
        c.editor.marks().setpcmark(p);
        c.editor.setCursor(new Pos(p.line, start), true);
        try {
            Target target = normal(from, command == '*' ? '/' : '?', pat.toString(), false, false, true);
            if (target == null) c.editor.setCursor(p, true);
            return target;
        } catch (RuntimeException failure) {
            c.editor.setCursor(p, true);
            throw failure;
        }
    }

    /**
     * find_ident_at_pos with FIND_IDENT|FIND_STRING.
     *
     * @return the start and end columns of the keyword, or other non-blank text, under or after {@code col}; or null
     */
    static int[] identifier(String line, int startcol, Keywords k) {
        int col = 0, thisClass = 0, i;
        for (i = 0; i < 2; i++) {
            col = startcol;
            while (col < line.length()) {
                thisClass = cls(k, line.codePointAt(col));
                if (thisClass != 0 && (i == 1 || thisClass != 1)) break;
                col += Character.charCount(line.codePointAt(col));
            }
            thisClass = col < line.length() ? cls(k, line.codePointAt(col)) : 0;
            while (col > 0 && thisClass != 0) {
                int prevcol = line.offsetByCodePoints(col, -1);
                int prevClass = cls(k, line.codePointAt(prevcol));
                // FIND_IDENT is set, so any change of class ends the text.
                if (thisClass != prevClass) break;
                col = prevcol;
            }
            if (thisClass > 2) thisClass = 2;
            if (thisClass == 2) break;
        }
        if (col >= line.length() || i == 0 && thisClass != 2) return null;
        int cls = cls(k, line.codePointAt(col));
        int end = col;
        while (end < line.length() && (i == 0 ? cls(k, line.codePointAt(end)) == cls : cls(k, line.codePointAt(end)) != 0))
            end += Character.charCount(line.codePointAt(end));
        return new int[] { col, end };
    }

    private static int cls(Keywords k, int c) { return k.charClass(c); }

    private boolean lastWrapped;

    /**
     * normal_search and do_search.
     *
     * @param dir {@code /} or {@code ?}, or 0 to use the last direction
     * @param pat the typed line (pattern, offset, {@code ;}…), or null to use the last pattern and offset
     * @param mark whether the search sets the previous-context mark (the jumplist)
     * @param reverse N: the other direction
     * @param noSmartcase for * and #, which ignore 'smartcase'
     * @return where the cursor goes, or null
     */
    Target normal(Context c, char dir, String pat, boolean mark, boolean reverse, boolean noSmartcase) {
        Buffer b = c.buffer;
        Pos pos = c.cursor();
        lastWrapped = false;
        int[] fold = c.editor.folds().closed(pos.line + 1);
        if (dir == 0) dir = direction;
        else direction = dir;
        if (reverse) dir = dir == '/' ? '?' : '/';
        // do_search: from inside a closed fold, a search starts after its end (forward) or at its start (backward).
        if (fold != null) pos = dir == '/' ? new Pos(fold[1] - 1, Integer.MAX_VALUE / 2) : new Pos(fold[0] - 1, 0);
        noHighlight = false;
        boolean end = false;
        int retval;
        String rest = pat;
        while (true) {
            String searchstr = rest;
            if (rest == null || rest.isEmpty() || rest.charAt(0) == dir) {
                if (pattern == null) throw new VimFailure("E35: No previous regular expression");
                searchstr = "";
            }
            String after = null;
            if (rest != null && !rest.isEmpty()) {
                int stop = skipPattern(rest, dir);
                searchstr = rest.substring(0, stop);
                String p = stop < rest.length() ? rest.substring(stop + 1) : "";
                boolean line = false, endOff = false;
                long off = 0;
                int i = 0;
                if (i < p.length() && (p.charAt(i) == '+' || p.charAt(i) == '-' || Character.isDigit(p.charAt(i)))) line = true;
                else if (i < p.length() && "esb".indexOf(p.charAt(i)) >= 0) {
                    endOff = p.charAt(i) == 'e';
                    i++;
                }
                if (i < p.length() && (Character.isDigit(p.charAt(i)) || p.charAt(i) == '+' || p.charAt(i) == '-')) {
                    int j = i + 1;
                    while (j < p.length() && Character.isDigit(p.charAt(j))) j++;
                    String number = p.substring(i, j);
                    if (number.equals("+")) off = 1;
                    else if (number.equals("-")) off = -1;
                    else off = Long.parseLong(number.startsWith("+") ? number.substring(1) : number);
                    i = j;
                }
                offset = new Offset(line, endOff, off);
                after = p.substring(i);
            }
            if (!searchstr.isEmpty()) pattern = searchstr;
            end = offset.end();
            // A character offset is taken off the start position, so ?pat?e+2 and /pat/s-2 do not get stuck.
            if (!offset.line() && offset.off() != 0) {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!decl(b, pos)) break;
                    if (n > 0) pos = new Pos(-1, Integer.MAX_VALUE);
                } else {
                    for (; n < 0; n++) if (!incl(b, pos)) break;
                    if (n < 0) pos = new Pos(b.count(), 0);
                }
            }
            VimRegex re = compile(c, pattern, noSmartcase);
            Pos found = searchit(c, pos, dir == '/', re, c.count, end, pattern);
            if (found == null) return null;
            pos = found;
            retval = 1;
            if (offset.line()) {
                long line = pos.line + offset.off();
                pos = new Pos((int) Math.max(0, Math.min(b.count() - 1, line)), 0);
                retval = 2;
            } else {
                long n = offset.off();
                if (n > 0) {
                    for (; n > 0; n--) if (!incl(b, pos)) break;
                } else for (; n < 0; n++) if (!decl(b, pos)) break;
            }
            if (after == null || !after.startsWith(";")) break;
            if (after.length() < 2 || after.charAt(1) != '/' && after.charAt(1) != '?')
                throw new VimFailure("E386: Expected '?' or '/' after ';'");
            dir = after.charAt(1);
            rest = after.substring(2);
        }
        // do_search echoes the search ("/pattern/e"), unless wrapping gave its warning.
        if (!lastWrapped) c.editor.message(echo(dir), false);
        // check_cursor: in Normal mode and with an operator the cursor stays on a character.
        String text = b.line(pos.line);
        if (!c.inVisual() && pos.col >= text.length() && !text.isEmpty()) pos = new Pos(pos.line, Text.last(text));
        Target.Kind kind = retval == 2 ? Target.Kind.LINEWISE : end ? Target.Kind.INCLUSIVE : Target.Kind.EXCLUSIVE;
        Target target = new Target(pos, kind, Target.Column.RESET, mark, true, true);
        return target;
    }

    /** @return the search as do_search echoes it: the pattern and any offset */
    private String echo(char dir) {
        StringBuilder out = new StringBuilder().append(dir).append(pattern);
        if (offset.line() || offset.end() || offset.off() != 0) {
            out.append(dir);
            if (offset.end()) out.append('e');
            else if (!offset.line()) out.append('s');
            if (offset.off() != 0 || offset.line()) out.append(offset.off() >= 0 ? "+" : "").append(offset.off());
        }
        return out.toString();
    }

    /** skip_regexp: the end of a pattern typed after {@code /} or {@code ?}, skipping brackets and backslashes. */
    static int skipPattern(String s, char delim) {
        int i = 0;
        while (i < s.length()) {
            char ch = s.charAt(i);
            if (ch == delim) return i;
            if (ch == '[') {
                int close = s.indexOf(']', i + 2);
                if (close > 0) {
                    i = close + 1;
                    continue;
                }
            }
            if (ch == '\\' && i + 1 < s.length()) i++;
            i++;
        }
        return s.length();
    }

    VimRegex compile(Context c, String pat, boolean noSmartcase) {
        Options o = c.options;
        try {
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase") && !noSmartcase,
                o.keywords().javaClass(), null));
        } catch (IllegalArgumentException bad) {
            throw new VimFailure(bad.getMessage());
        }
    }

    /**
     * searchit: the {@code count}-th match from {@code start}, forward or backward, wrapping with 'wrapscan'.
     *
     * @param atEnd the 'e' offset: the match's last character counts, and the result is it
     * @return the match position (its start, or its last character with {@code atEnd}), or null with a message
     */
    Pos searchit(Context c, Pos start, boolean forward, VimRegex re, int count, boolean atEnd, String shown) {
        Buffer b = c.buffer;
        String text = b.text(0, b.length());
        Matcher m = re.pattern().matcher(text);
        Pos pos = start.copy();
        boolean wrapscan = c.options.flag("wrapscan");
        int lastLine = b.count() - 1;
        boolean found = false;
        int lnum = 0;
        do {
            int startCharLen;
            if (pos.col == Integer.MAX_VALUE) startCharLen = 0;
            else if (pos.line >= 0 && pos.line <= lastLine) {
                String l = b.line(pos.line);
                startCharLen = pos.col >= l.length() ? 1 : Character.charCount(l.codePointAt(pos.col));
            } else startCharLen = 1;
            int extraCol = forward ? startCharLen : 0;
            Pos startPos = pos.copy();
            found = false;
            boolean atFirstLine = true;
            if (pos.line < 0) {
                pos = new Pos(0, 0);
                atFirstLine = false;
            }
            if (!forward && startPos.col == 0) {
                lnum = pos.line - 1;
                atFirstLine = false;
            } else lnum = pos.line;
            if (pos.line > lastLine) atFirstLine = false;
            for (int loop = 0; loop <= 1; loop++) {
                for (; lnum >= 0 && lnum <= lastLine; lnum += forward ? 1 : -1, atFirstLine = false) {
                    Match match = forward ? forwardIn(b, m, re, lnum, atFirstLine, startPos, extraCol, atEnd)
                        : backwardIn(b, m, re, lnum, loop > 0, startPos, extraCol, atEnd);
                    if (match != null) {
                        if (atEnd && !match.start().equals(match.end())) {
                            Pos e = match.end().copy();
                            if (e.col == 0) {
                                if (e.line > 0) {
                                    e.line--;
                                    e.col = b.line(e.line).length();
                                }
                            } else e.col = Text.previous(b.line(e.line), e.col);
                            pos = e;
                        } else pos = match.start().copy();
                        found = true;
                        break;
                    }
                    if (loop > 0 && lnum == startPos.line) break;
                }
                if (!wrapscan || found || loop > 0) break;
                lnum = forward ? 0 : lastLine;
                lastWrapped = true;
                c.editor.message(forward ? "search hit BOTTOM, continuing at TOP" : "search hit TOP, continuing at BOTTOM", true);
            }
        } while (--count > 0 && found);
        if (!found) {
            if (wrapscan) throw new VimFailure("E486: Pattern not found: " + shown);
            if (lnum < 0) throw new VimFailure("E384: Search hit TOP without match for: " + shown);
            throw new VimFailure("E385: Search hit BOTTOM without match for: " + shown);
        }
        if (pos.line > lastLine) {
            String l = b.line(lastLine);
            pos = new Pos(lastLine, l.isEmpty() ? 0 : Text.last(l));
        }
        return pos;
    }

    /** The first match attempted at or after {@code col} of line {@code lnum}; null when no attempt there matches. */
    private static Matcher attempt(Buffer b, Matcher m, int lnum, int col) {
        int from = b.offset(lnum, col), lineEnd = b.end(lnum);
        if (from > b.length()) return null;
        if (!m.find(from) || m.start() > lineEnd) return null;
        // The text's final line break ends the last line; nothing starts after it.
        if (m.start() >= b.length() && b.length() > 0) return null;
        return m;
    }

    private static Match match(Buffer b, VimRegex re, Matcher m) {
        int s = Math.min(re.start(m), b.length()), e = Math.min(re.end(m), b.length());
        return new Match(position(b, s), position(b, Math.max(s, e)));
    }

    /** An offset as a position; the offset after the final line break is the end of the last line. */
    private static Pos position(Buffer b, int offset) {
        if (offset >= b.length() && b.length() > 0) {
            int last = b.count() - 1;
            return new Pos(last, b.line(last).length());
        }
        return b.pos(offset);
    }

    private static Match forwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean atFirstLine, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match match = match(b, re, found);
        if (!atFirstLine) return match;
        boolean firstMatch = true;
        while (match.start().line == lnum && (atEnd && firstMatch
            ? endCol(b, match) - 1 < startPos.col + extraCol
            : match.start().col - (match.start().col >= b.line(lnum).length() ? 1 : 0) < startPos.col + extraCol)) {
            // Try again one character after where this attempt started.
            int matchcol = found.start() - b.start(lnum);
            String line = b.line(lnum);
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else return null;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) return null;
            match = match(b, re, found);
        }
        return match;
    }

    /** The end column of a match on its start line, or a large value when it ends on a later line. */
    private static int endCol(Buffer b, Match match) {
        return match.end().line == match.start().line ? match.end().col : Integer.MAX_VALUE / 2;
    }

    private static Match backwardIn(Buffer b, Matcher m, VimRegex re, int lnum, boolean loop, Pos startPos, int extraCol, boolean atEnd) {
        Matcher found = attempt(b, m, lnum, 0);
        if (found == null) return null;
        Match best = null;
        Match match = match(b, re, found);
        String line = b.line(lnum);
        while (true) {
            boolean ok;
            if (loop) ok = true;
            else if (atEnd) ok = match.end().line < startPos.line || match.end().line == startPos.line && match.end().col - 1 < startPos.col + extraCol;
            else ok = match.start().line < startPos.line || match.start().line == startPos.line && match.start().col < startPos.col + extraCol;
            if (!ok) break;
            best = match;
            if (match.start().line != lnum) break;
            int matchcol = match.start().col;
            if (matchcol < line.length()) matchcol += Character.charCount(line.codePointAt(matchcol));
            else break;
            found = attempt(b, m, lnum, matchcol);
            if (found == null) break;
            match = match(b, re, found);
        }
        return best;
    }

    /** incl: one character forward, onto the next line's start after the last; false at the end of the text. */
    static boolean incl(Buffer b, Pos p) {
        String line = b.line(p.line);
        if (p.col < line.length()) {
            p.col += Character.charCount(line.codePointAt(p.col));
            if (p.col < line.length()) return true;
        }
        if (p.line < b.count() - 1) {
            p.line++;
            p.col = 0;
            return true;
        }
        return false;
    }

    /** decl: one character back, onto the previous line's end; false at the start of the text. */
    static boolean decl(Buffer b, Pos p) {
        if (p.col == Integer.MAX_VALUE) p.col = b.line(p.line).length();
        if (p.col > 0) {
            String line = b.line(p.line);
            p.col = Math.min(p.col, line.length());
            if (p.col > 0) {
                p.col = Text.previous(line, p.col);
                return true;
            }
        }
        if (p.line > 0) {
            p.line--;
            p.col = b.line(p.line).length();
            return true;
        }
        return false;
    }

    /** @return every match in the text, for 'hlsearch'; empty when there is no pattern or it is invalid */
    List<Match> all(VimEditor editor) {
        if (pattern == null || noHighlight) return List.of();
        VimRegex re = quietly(editor, pattern);
        return re == null ? List.of() : matches(editor.buffer(), re);
    }

    /**
     * 'incsearch': the match a search for the typed line would find from the cursor, without moving it, changing
     * the last pattern or giving messages.
     *
     * @return the match, or null
     */
    Match preview(VimEditor editor, char type, String line) {
        String pat = line.substring(0, skipPattern(line, type));
        if (pat.isEmpty()) pat = pattern;
        if (pat == null) return null;
        VimRegex re = quietly(editor, pat);
        if (re == null) return null;
        List<Match> found = matches(editor.buffer(), re);
        if (found.isEmpty()) return null;
        Pos cursor = editor.cursor();
        boolean wrap = editor.options().flag("wrapscan");
        if (type == '/') {
            for (Match m : found) if (m.start().compareTo(cursor) > 0) return m;
            return wrap ? found.getFirst() : null;
        }
        for (int i = found.size() - 1; i >= 0; i--) if (found.get(i).start().compareTo(cursor) < 0) return found.get(i);
        return wrap ? found.getLast() : null;
    }

    private static VimRegex quietly(VimEditor editor, String pat) {
        try {
            Options o = editor.options();
            return VimRegex.compile(pat, new VimRegex.Settings(o.flag("ignorecase"), o.flag("smartcase"), o.keywords().javaClass(), null));
        } catch (IllegalArgumentException bad) {
            return null;
        }
    }

    private static List<Match> matches(Buffer b, VimRegex re) {
        List<Match> out = new ArrayList<>();
        Matcher m = re.pattern().matcher(b.text(0, b.length()));
        int from = 0;
        while (from <= b.length() && m.find(from) && out.size() < 10_000) {
            if (m.start() >= b.length() && b.length() > 0) break;
            out.add(match(b, re, m));
            from = m.end() > m.start() ? m.end() : m.start() + 1;
        }
        return out;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/MarkCommands.java` with:

```java
package dev.jasper.editor.vim;

/**
 * Marks, the jumplist and the change list (spec 3.6), ported from Vim's normal.c (nv_mark, nv_gomark,
 * nv_cursormark, nv_pcmark, {@code gi}) and mark.c (setmark_pos, getmark_buf_fnum, check_mark).
 */
final class MarkCommands {
    private MarkCommands() { }

    static void register(VimCommands commands) {
        commands.actionWithChar("m", "set mark {char} at the cursor", false, MarkCommands::set);
        commands.motionWithChar("'", "to the first non-blank of mark {char}'s line", c -> go(c, true));
        commands.motionWithChar("`", "to mark {char}", c -> go(c, false));
        commands.action("<C-o>", "to an older position in the jumplist", false, c -> jump(c, -c.count));
        // CTRL-I arrives as <Tab>, as in Vim (KeyInput.normalized).
        commands.action("<Tab>", "to a newer position in the jumplist (CTRL-I)", false, c -> jump(c, c.count));
        commands.action("g;", "to an older position in the change list", false, c -> change(c, -c.count));
        commands.action("g,", "to a newer position in the change list", false, c -> change(c, c.count));
        commands.action("gi", "Insert mode where it was last left (the '^ mark)", true, MarkCommands::insertAgain);
        commands.visual("m", "set mark {char} at the cursor", true, false, MarkCommands::set);
    }

    /** nv_mark and setmark_pos. */
    static void set(Context c) {
        int name = c.argumentChar();
        Marks marks = c.editor.marks();
        Pos cursor = c.cursor();
        if (name == '\'' || name == '`') marks.setpcmarkKeep(cursor);
        else if (name >= 'a' && name <= 'z' || name == '[' || name == ']' || name == '"') marks.set((char) name, cursor);
        else if (name == '<') setVisual(c, Visual.START, cursor);
        else if (name == '>') setVisual(c, Visual.END, cursor);
        else if (name >= 'A' && name <= 'Z') c.vim.globalMark((char) name, c.editor, cursor);
        else throw Context.fail();
    }

    private static void setVisual(Context c, char which, Pos cursor) {
        Marks marks = c.editor.marks();
        marks.set(which, cursor);
        if (marks.visualMode == null) marks.visualMode = Mode.VISUAL;
    }

    /**
     * getmark_buf_fnum.
     *
     * @return the mark's position, which may be past the end of its line or the text; null when it is not set
     */
    static Pos get(VimEditor editor, char name) {
        Marks marks = editor.marks();
        return switch (name) {
            case '\'', '`' -> marks.pcmark();
            case '<', '>' -> {
                Pos start = marks.get(Visual.START), end = marks.get(Visual.END);
                Pos pos;
                if (start != null && ((name == '<') == (end == null || start.compareTo(end) < 0))) pos = start;
                else pos = end;
                if (pos != null && marks.visualMode == Mode.VISUAL_LINE) pos = new Pos(pos.line, name == '<' ? 0 : Visual.MAX);
                yield pos;
            }
            default -> {
                if (name >= 'A' && name <= 'Z') yield editor.vim().globalMark(name, editor);
                yield marks.get(name);
            }
        };
    }

    /** nv_gomark: ' is linewise to the first non-blank, ` exclusive to the mark itself; both are jumps. */
    static Target go(Context c, boolean linewise) {
        int name = c.argumentChar();
        if (name < 0) throw Context.fail();
        Pos pos = get(c.editor, (char) name);
        pos = check(c.buffer, pos);
        if (linewise) {
            String line = c.buffer.line(pos.line);
            return Target.linewise(new Pos(pos.line, Text.firstNonBlank(line)), Target.Column.RESET).asJump();
        }
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (!c.inVisual() && col >= line.length() && !line.isEmpty()) col = Text.last(line);
        return Target.exclusive(new Pos(pos.line, col)).asJump().usingRegisterOne().withoutAdjust();
    }

    /** check_mark: E20 for a mark that is not set, E19 for one past the last line. */
    static Pos check(Buffer b, Pos pos) {
        if (pos == null || pos.line < 0) throw new VimFailure("E20: Mark not set");
        if (pos.line >= b.count()) throw new VimFailure("E19: Mark has invalid line number");
        return pos;
    }

    /** nv_pcmark for CTRL-O and CTRL-I; not with an operator or in Visual mode. */
    static void jump(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Pos pos = c.editor.marks().movemark(count, c.cursor());
        if (pos == null) throw Context.fail();
        moveTo(c, check(c.buffer, pos));
    }

    /** nv_pcmark for g; and g,. */
    static void change(Context c, int count) {
        if (c.operator != null || c.inVisual()) throw Context.fail();
        Marks marks = c.editor.marks();
        Pos pos = marks.movechangelist(count);
        if (pos == null) {
            if (marks.changes().isEmpty()) throw new VimFailure("E664: Changelist is empty");
            throw new VimFailure(count < 0 ? "E662: At start of changelist" : "E663: At end of changelist");
        }
        moveTo(c, check(c.buffer, pos));
    }

    private static void moveTo(Context c, Pos pos) {
        Pos before = c.cursor();
        String line = c.buffer.line(pos.line);
        int col = Math.min(pos.col, line.length());
        if (col >= line.length() && !line.isEmpty()) col = Text.last(line);
        c.editor.setCursor(new Pos(pos.line, col));
        c.editor.forgetColumn();
        c.editor.machine().foldOpenAfter("mark", before);
    }

    /** gi: Insert mode at the {@code '^} mark, or at the cursor when it is not set. */
    static void insertAgain(Context c) {
        Pos last = c.editor.marks().get('^');
        if (last != null) {
            int line = Math.min(last.line, c.buffer.count() - 1);
            int col = Math.min(last.col, c.buffer.line(line).length());
            c.editor.setCursor(new Pos(line, col), true);
        }
        c.editor.insert().begin("i", c.count, false, -1);
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/NormalCommands.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Registers.Content;
import java.util.ArrayList;
import java.util.List;

/** Normal-mode commands that are neither motions nor operators (spec 4.1), ported from Vim's normal.c and ops.c. */
final class NormalCommands {
    private NormalCommands() { }

    static void register(VimCommands commands) {
        commands.action("x", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("<Del>", "delete {count} characters (dl)", true, c -> c.editor.machine().alias(c, "d", "l"));
        commands.action("X", "delete {count} characters before the cursor (dh)", true, c -> c.editor.machine().alias(c, "d", "h"));
        commands.action("D", "delete to the end of the line (d$)", true, c -> c.editor.machine().alias(c, "d", "$"));
        commands.action("C", "change to the end of the line (c$)", true, c -> c.editor.machine().alias(c, "c", "$"));
        commands.action("s", "substitute {count} characters (cl)", true, c -> c.editor.machine().alias(c, "c", "l"));
        commands.action("S", "substitute {count} lines (cc)", true, c -> c.editor.machine().alias(c, "c", "c"));
        commands.action("Y", "yank {count} lines (yy)", false, c -> c.editor.machine().alias(c, "y", "y"));
        commands.actionWithChar("r", "replace {count} characters with {char}", true, NormalCommands::replace);
        commands.action("~", "toggle the case of {count} characters", true, NormalCommands::tilde);
        commands.action("J", "join {count} lines, with a space", true, c -> join(c, true));
        commands.action("<C-g>", "show the file name, line count and cursor position", false, NormalCommands::fileInfo);
        commands.action("ga", "show the code of the character under the cursor", false, NormalCommands::ascii);
        commands.action("gJ", "join {count} lines, without a space", true, c -> join(c, false));
        commands.action("p", "put after the cursor", true, c -> put(c, true, false, false));
        commands.action("P", "put before the cursor", true, c -> put(c, false, false, false));
        commands.action("gp", "put after, cursor after the text", true, c -> put(c, true, true, false));
        commands.action("gP", "put before, cursor after the text", true, c -> put(c, false, true, false));
        commands.action("]p", "put after, with the current line's indent", true, c -> put(c, true, false, true));
        commands.action("[p", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("]P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("[P", "put before, with the current line's indent", true, c -> put(c, false, false, true));
        commands.action("i", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("<Insert>", "insert before the cursor", true, c -> c.editor.insert().begin("i", c.count, false, -1));
        commands.action("a", "append after the cursor", true, NormalCommands::append);
        commands.action("I", "insert before the first non-blank", true, c -> insertAt(c, Text.firstNonBlank(c.buffer.line(c.cursor().line)), "I"));
        commands.action("gI", "insert in column 1", true, c -> insertAt(c, 0, "gI"));
        commands.action("A", "append at the end of the line", true, c -> insertAt(c, c.buffer.line(c.cursor().line).length(), "A"));
        commands.action("o", "open a line below", true, c -> open(c, true));
        commands.action("O", "open a line above", true, c -> open(c, false));
        commands.action("R", "Replace mode", true, c -> c.editor.insert().begin("R", c.count, true, -1));
        commands.action("u", "undo {count} changes", false, c -> {
            Pos before = c.cursor();
            c.editor.undo(c.count);
            c.editor.machine().foldOpenAfter("undo", before);
        });
        commands.action("<C-r>", "redo {count} changes", false, c -> {
            Pos before = c.cursor();
            c.editor.redo(c.count);
            c.editor.machine().foldOpenAfter("undo", before);
        });
        commands.action(".", "repeat the last change, with {count} replacing its count", false, c -> c.editor.machine().repeat(c));
        commands.action("<C-l>", "redraw (does nothing)", false, c -> { });
    }

    /** nv_replace. */
    static void replace(Context c) {
        KeyInput key = c.argument;
        if (key.is("<Esc>")) return;
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        int end = p.col;
        for (int n = 0; n < c.count; n++) {
            if (end >= line.length()) throw Context.fail();
            end = Text.next(line, end);
        }
        if (key.is("<CR>") || key.is("<C-j>")) {
            c.buffer.remove(c.buffer.offset(p), c.buffer.offset(p.line, end));
            c.editor.setCursor(p);
            InsertMode insert = c.editor.insert();
            insert.beginNested("r");
            insert.key(KeyInput.named("CR"));
            insert.key(KeyInput.named("Esc"));
            return;
        }
        String with;
        if (key.is("<Tab>")) with = "\t";
        else if (key.plainChar() >= 0) with = key.key();
        else throw Context.fail();
        c.buffer.replace(c.buffer.offset(p), c.buffer.offset(p.line, end), with.repeat(c.count));
        c.editor.setCursor(new Pos(p.line, p.col + (c.count - 1) * with.length()));
    }

    /** n_swapchar, with 'notildeop'. */
    static void tilde(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (line.isEmpty()) throw Context.fail();
        int col = p.col;
        StringBuilder out = new StringBuilder(line.substring(0, col));
        for (int n = c.count; n > 0 && col < line.length(); n--) {
            int next = Text.next(line, col);
            line.substring(col, next).codePoints().forEach(cp -> out.appendCodePoint(Operators.toggle(cp)));
            col = next;
        }
        out.append(line.substring(col));
        c.buffer.setLine(p.line, out.toString());
        c.editor.setCursor(new Pos(p.line, col));
    }

    static void append(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (!line.isEmpty()) p.col = Text.next(line, p.col);
        c.editor.setCursor(p, true);
        c.editor.insert().begin("a", c.count, false, -1);
    }

    static void insertAt(Context c, int col, String command) {
        c.editor.setCursor(new Pos(c.cursor().line, col), true);
        c.editor.insert().begin(command, c.count, false, -1);
    }

    static void open(Context c, boolean below) {
        String command = below ? "o" : "O";
        c.editor.insert().openLine(below);
        c.editor.insert().begin(command, c.count, false, c.options.flag("autoindent") ? c.cursor().line : -1);
    }

    /** nv_join, do_join. */
    static void join(Context c, boolean spaces) {
        int count = Math.max(c.count, 2), first = c.cursor().line, last = c.buffer.count() - 1;
        if (first + count - 1 > last) {
            if (count <= 2) throw Context.fail();
            count = last - first + 1;
        }
        joinLines(c, first, count, spaces);
    }

    /**
     * do_join. Each line joins the first in turn: the separator goes after the first line's text, then the line break
     * and the next line's indent go, so marks on the joined line move as Vim's mark_col_adjust moves them.
     */
    static void joinLines(Context c, int first, int count, boolean insertSpace) {
        Buffer b = c.buffer;
        boolean js = c.options.flag("joinspaces");
        int col = 0, endcurr1 = 0, endcurr2 = 0;
        String head = b.line(first);
        if (insertSpace && !head.isEmpty()) {
            int lastStart = head.offsetByCodePoints(head.length(), -1);
            endcurr1 = head.codePointAt(lastStart);
            if (lastStart > 0) endcurr2 = head.codePointBefore(lastStart);
        }
        for (int t = 1; t < count; t++) {
            String curr = b.line(first + 1);
            int removed = 0, spaces = 0;
            if (insertSpace) {
                removed = Text.firstNonBlank(curr);
                curr = curr.substring(removed);
                if (!curr.isEmpty() && curr.charAt(0) != ')' && b.line(first).length() > 0 && endcurr1 != '\t') {
                    if (endcurr1 == ' ') endcurr1 = endcurr2;
                    else spaces++;
                    if (js && (endcurr1 == '.' || endcurr1 == '?' || endcurr1 == '!')) spaces++;
                }
            }
            int end = b.end(first);
            if (t == count - 1) col = b.line(first).length();
            if (spaces > 0) b.insert(end, " ".repeat(spaces));
            int from = b.end(first), to = b.start(first + 1) + removed;
            c.editor.marks().joining(() -> b.remove(from, to));
            endcurr1 = endcurr2 = 0;
            if (insertSpace && !curr.isEmpty()) {
                int lastStart = curr.offsetByCodePoints(curr.length(), -1);
                endcurr1 = curr.codePointAt(lastStart);
                if (lastStart > 0) endcurr2 = curr.codePointBefore(lastStart);
            }
        }
        c.editor.setCursor(new Pos(first, col));
    }

    /**
     * fileinfo (buffer.c) with 'noruler': {@code "name" [Modified] line 2 of 10 --20%-- col 3}, the column shown as
     * {@code 3-10} when the display column differs.
     */
    static void fileInfo(Context c) {
        Buffer b = c.buffer;
        Pos p = c.cursor();
        StringBuilder out = new StringBuilder("\"").append(c.editor.documentName()).append('"');
        boolean modified = c.editor.documentModified();
        out.append(modified ? " [Modified] " : " ");
        if (b.length() == 0) out.append("--No lines in buffer--");
        else {
            int lines = b.count();
            int percent = (int) ((long) (p.line + 1) * 100 / lines);
            String line = b.line(p.line);
            int col = p.col + 1, vcol = Text.virtcol(line, p.col, c.tabstop()) + 1;
            out.append("line ").append(p.line + 1).append(" of ").append(lines).append(" --").append(percent).append("%-- col ");
            out.append(col == vcol ? String.valueOf(col) : col + "-" + vcol);
        }
        c.editor.message(out.toString(), false);
    }

    /**
     * do_ascii: the character under the cursor as {@code <a>  97,  Hex 61,  Octal 141}, or for a character above
     * U+007F {@code <é> 233, Hex 00e9, Oct 351}, followed by any combining characters. Vim adds the character's
     * digraph; Jasper has no digraph table (docs/vim-divergences.md).
     */
    static void ascii(Context c) {
        Pos p = c.cursor();
        String line = c.buffer.line(p.line);
        if (p.col >= line.length()) {
            c.editor.message("NUL", false);
            return;
        }
        StringBuilder out = new StringBuilder();
        int i = p.col;
        int cp = line.codePointAt(i);
        if (cp < 0x80) {
            String shown = cp < 0x20 ? "^" + (char) (cp + '@') : cp == 0x7f ? "^?" : String.valueOf((char) cp);
            out.append('<').append(shown).append(">  ").append(cp).append(",  Hex ").append(String.format("%02x", cp))
                .append(",  Octal ").append(String.format("%03o", cp));
            i += 1;
        } else {
            out.append('<').append(Character.toString(cp)).append("> ").append(cp).append(", Hex ").append(String.format("%04x", cp))
                .append(", Oct ").append(Integer.toOctalString(cp));
            i += Character.charCount(cp);
        }
        while (i < line.length() && Character.getType(line.codePointAt(i)) == Character.NON_SPACING_MARK) {
            int mark = line.codePointAt(i);
            out.append(" < ").append(Character.toString(mark)).append("> ").append(mark).append(", Hex ").append(String.format("%04x", mark))
                .append(", Oct ").append(Integer.toOctalString(mark));
            i += Character.charCount(mark);
        }
        c.editor.message(out.toString(), false);
    }

    /** do_put: see {@link Put}. */
    static void put(Context c, boolean after, boolean cursorAfter, boolean fixIndent) {
        Put.put(c, after, c.count, (cursorAfter ? Put.CURSEND : 0) | (fixIndent ? Put.FIXINDENT : 0), -1);
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimUndoManager.java` with:

```java
package dev.jasper.editor.vim;

import javax.swing.UIManager;
import javax.swing.event.UndoableEditEvent;
import javax.swing.text.BadLocationException;
import javax.swing.undo.CompoundEdit;
import javax.swing.undo.UndoableEdit;
import org.fife.ui.rtextarea.RTextArea;
import org.fife.ui.rtextarea.RUndoManager;

/**
 * The text area's undo manager (spec 3.3). With Vim off it groups edits as RSyntaxTextArea's own manager does
 * (neighbouring typed characters form one step). With Vim on it never guesses: each Vim change the engine brackets
 * with {@link #beginStep} and {@link #endStep} is one step, and any other edit (the Editor's Replace, a paste
 * through ⌘V) is a step of its own. RSyntaxTextArea's atomic edits inside a Vim step join that step.
 */
final class VimUndoManager extends RUndoManager {
    /** One undo step, remembering where the cursor was before it, for Vim's cursor placement after undo. */
    static final class Step extends CompoundEdit {
        int cursorLine, cursorCol;
        int edits;

        Step(int cursorLine, int cursorCol) {
            this.cursorLine = cursorLine;
            this.cursorCol = cursorCol;
        }

        void take(UndoableEdit edit) {
            addEdit(edit);
            edits++;
        }

        // The manager treats a step as finished; addEdit above still works until end() because it reads the field.
        @Override public boolean isInProgress() { return false; }
        @Override public String getUndoPresentationName() { return UIManager.getString("AbstractUndoableEdit.undoText"); }
        @Override public String getRedoPresentationName() { return UIManager.getString("AbstractUndoableEdit.redoText"); }
    }

    private final RTextArea area;
    private boolean vim;
    private Step vimStep, internal, typing;
    private int stepDepth, internalDepth, lastOffset;

    VimUndoManager(RTextArea area) {
        super(area);
        this.area = area;
        setLimit(1000);
    }

    /** @param on whether Vim mode groups the edits */
    void vim(boolean on) {
        vim = on;
        typing = seal(typing);
    }

    /**
     * The cursor moved before the open step's first change (an operator goes to its span's start, as Vim's
     * do_pending_operator does before u_save): the step records the cursor there.
     */
    void stepCursor(int cursorLine, int cursorCol) {
        if (vimStep != null && vimStep.edits == 0) {
            vimStep.cursorLine = cursorLine;
            vimStep.cursorCol = cursorCol;
        }
    }

    /** Opens a Vim step, or nests inside the open one. */
    void beginStep(int cursorLine, int cursorCol) {
        if (stepDepth++ == 0) {
            typing = seal(typing);
            vimStep = new Step(cursorLine, cursorCol);
        }
    }

    /** Closes one level; the outermost close puts the step on the undo stack if it changed anything. */
    void endStep() {
        if (stepDepth == 0 || --stepDepth > 0) return;
        Step step = vimStep;
        vimStep = null;
        step.end();
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    boolean stepOpen() { return stepDepth > 0; }

    /** @return the step {@link #undo} would undo, or null when there is none or it is not a Vim step */
    Step nextUndo() { return editToBeUndone() instanceof Step step ? step : null; }

    /** @return the step {@link #redo} would redo, or null */
    Step nextRedo() { return editToBeRedone() instanceof Step step ? step : null; }

    @Override public void undoableEditHappened(UndoableEditEvent event) {
        UndoableEdit edit = event.getEdit();
        if (vimStep != null) {
            vimStep.take(edit);
            return;
        }
        if (internalDepth > 0) {
            internal.take(edit);
            return;
        }
        if (vim) {
            Step step = new Step(-1, -1);
            step.take(edit);
            step.end();
            addEdit(step);
            updateActions();
            return;
        }
        // RSyntaxTextArea's grouping: an edit next to the previous one continues its step.
        int caret = area.getCaretPosition();
        if (typing != null && Math.abs(caret - lastOffset) <= 1) {
            typing.take(edit);
            lastOffset = caret;
            return;
        }
        typing = seal(typing);
        typing = new Step(-1, -1);
        typing.take(edit);
        addEdit(typing);
        lastOffset = caret;
        updateActions();
    }

    @Override public void beginInternalAtomicEdit() {
        if (internalDepth++ == 0 && vimStep == null) {
            typing = seal(typing);
            internal = new Step(-1, -1);
        }
    }

    @Override public void endInternalAtomicEdit() {
        if (internalDepth == 0 || --internalDepth > 0 || internal == null) return;
        Step step = internal;
        internal = null;
        step.end();
        if (step.edits > 0) addEdit(step);
        updateActions();
    }

    /** Counts an open step's edits, since RTextArea asks before it undoes (⌘Z in the middle of an insert). */
    @Override public synchronized boolean canUndo() {
        return vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0 || super.canUndo();
    }

    /**
     * Nothing is redoable while an open step has edits: closing it for the redo would put it on the undo stack and
     * trim the redo stack, so the redo would fail (an outside ⌘⇧Z in the middle of an insert).
     */
    @Override public synchronized boolean canRedo() {
        if (vimStep != null && vimStep.edits > 0 || internal != null && internal.edits > 0) return false;
        return super.canRedo();
    }

    /**
     * An outside undo (⌘Z) while the engine has a step open: closes it so it is undoable, then reopens the same
     * nesting once the undo runs, so the engine's own {@link #endStep} later balances (spec: the rest of an insert
     * in progress stays one step).
     */
    @Override public void undo() {
        int depth = stepDepth;
        closeAll();
        try { super.undo(); }
        finally { reopen(depth); }
    }

    /** The redo counterpart of {@link #undo}. */
    @Override public void redo() {
        int depth = stepDepth;
        closeAll();
        try { super.redo(); }
        finally { reopen(depth); }
    }

    /** Reopens {@code depth} levels of Vim step after an outside undo/redo, cursor taken from the area's caret now. */
    private void reopen(int depth) {
        if (depth == 0) return;
        int line = 0, col = 0;
        try {
            int offset = area.getCaretPosition();
            line = area.getLineOfOffset(offset);
            col = offset - area.getLineStartOffset(line);
        } catch (BadLocationException ignored) {
            // Falls back to (0, 0); the reopened step's remembered cursor is only advisory.
        }
        for (int n = 0; n < depth; n++) beginStep(line, col);
    }

    @Override public void discardAllEdits() {
        stepDepth = internalDepth = 0;
        vimStep = internal = typing = null;
        super.discardAllEdits();
    }

    /** Ends every open step, as an undo from outside Vim (⌘Z during an insert) must. */
    private void closeAll() {
        while (stepDepth > 0) endStep();
        if (internal != null) {
            internalDepth = 1;
            endInternalAtomicEdit();
        }
        typing = seal(typing);
    }

    private static Step seal(Step step) {
        if (step != null) step.end();
        return null;
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimCommands.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Every key Vim mode knows (spec 11.1): the parser looks keys up here, the coverage guard checks each entry has a
 * corpus case, and the reference document is generated from it. Keys are Vim notation, as {@link KeyInput#format}
 * writes them.
 */
public final class VimCommands {
    /** What an entry does. */
    public enum Kind { MOTION, OPERATOR, ACTION, OBJECT, INSERT, VISUAL }

    @FunctionalInterface interface Motion { Target move(Context context); }

    @FunctionalInterface interface TextObject { Span select(Context context); }

    @FunctionalInterface interface Operator { void apply(Context context, Span span); }

    @FunctionalInterface interface Action { void run(Context context); }

    @FunctionalInterface interface InsertKey { void run(InsertMode insert, KeyInput key); }

    /**
     * One command.
     *
     * @param kind what it is
     * @param keys its keys in Vim notation
     * @param description what it does, for the reference
     * @param needsChar whether it reads one more key as its argument ({@code f}, {@code r})
     * @param change whether it changes text, so {@code .} repeats it and it is one undo step
     * @param handler a {@link Motion}, {@link TextObject}, {@link Operator}, {@link Action} or {@link InsertKey}
     */
    public record Entry(Kind kind, String keys, String description, boolean needsChar, boolean change, Object handler) {
        /** @return the coverage id, such as {@code motion w} */
        public String id() { return kind.name().toLowerCase(java.util.Locale.ROOT) + " " + keys; }
    }

    private final Map<String, Entry> normal = new LinkedHashMap<>();
    private final Map<String, Entry> objects = new LinkedHashMap<>();
    private final Map<String, Entry> insert = new LinkedHashMap<>();
    private final Map<String, Entry> visual = new LinkedHashMap<>();
    private final Set<String> normalPrefixes = new HashSet<>(), pendingPrefixes = new HashSet<>(), visualPrefixes = new HashSet<>();

    private VimCommands() { }

    /** @return the standard commands */
    public static VimCommands standard() {
        var commands = new VimCommands();
        Motions.register(commands);
        TextObjects.register(commands);
        Operators.register(commands);
        NormalCommands.register(commands);
        InsertMode.register(commands);
        Visual.register(commands);
        Search.register(commands);
        MarkCommands.register(commands);
        Numbers.register(commands);
        Format.register(commands);
        ViewCommands.register(commands);
        return commands;
    }

    /** @return every entry, in registration order */
    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>(normal.values());
        all.addAll(objects.values());
        all.addAll(visual.values());
        all.addAll(insert.values());
        return Collections.unmodifiableList(all);
    }

    void motion(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, false, false, motion)); }

    /** A motion that reads a command line first ({@code /} and {@code ?}); the line is in {@link Context#line}. */
    void motionWithLine(String keys, String description, Motion motion) {
        motion(keys, description, motion);
        lineCommands.add(keys);
    }

    /** @return whether the entry reads a command line before it runs */
    boolean needsLine(Entry entry) { return entry.kind() == Kind.MOTION && lineCommands.contains(entry.keys()); }

    private final java.util.Set<String> lineCommands = new java.util.HashSet<>();

    void motionWithChar(String keys, String description, Motion motion) { add(normal, new Entry(Kind.MOTION, keys, description, true, false, motion)); }

    void operator(String keys, String description, Operator operator) { add(normal, new Entry(Kind.OPERATOR, keys, description, false, !keys.equals("y"), operator)); }

    void action(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, false, change, action)); }

    void actionWithChar(String keys, String description, boolean change, Action action) { add(normal, new Entry(Kind.ACTION, keys, description, true, change, action)); }

    void object(String keys, String description, TextObject object) { add(objects, new Entry(Kind.OBJECT, keys, description, false, false, object)); }

    void insert(String keys, String description, InsertKey key) { add(insert, new Entry(Kind.INSERT, keys, description, false, false, key)); }

    /** A Visual-mode command; {@code change} commands are repeated by {@code .} on an area of the same size. */
    void visual(String keys, String description, boolean needsChar, boolean change, Action action) {
        add(visual, new Entry(Kind.VISUAL, keys, description, needsChar, change, action));
    }

    /** @return the Normal-mode command with exactly these keys, or null */
    Entry normal(String keys) { return normal.get(keys); }

    /** @return the motion or text object with exactly these keys after an operator, or null */
    Entry pending(String keys) {
        Entry entry = normal.get(keys);
        if (entry != null && entry.kind() == Kind.MOTION) return entry;
        return objects.get(keys);
    }

    /** @return the Insert-mode key, or null */
    Entry insertKey(String keys) { return insert.get(keys); }

    /** @return in Visual mode: a Visual command, else a motion or operator, else a text object; or null */
    Entry visualKey(String keys) {
        Entry entry = visual.get(keys);
        if (entry != null) return entry;
        entry = normal.get(keys);
        if (entry != null && (entry.kind() == Kind.MOTION || entry.kind() == Kind.OPERATOR)) return entry;
        return objects.get(keys);
    }

    /** @return whether more keys could complete a Visual-mode command */
    boolean visualPrefix(String keys) { return visualPrefixes.contains(keys); }

    /** @return whether more keys could complete a command */
    boolean prefix(String keys, boolean pending) { return (pending ? pendingPrefixes : normalPrefixes).contains(keys); }

    private void add(Map<String, Entry> map, Entry entry) {
        if (map.putIfAbsent(entry.keys(), entry) != null) throw new IllegalStateException("Duplicate command " + entry.keys());
        if (entry.kind() == Kind.INSERT) return;
        List<KeyInput> keys = KeyInput.parse(entry.keys());
        for (int n = 1; n < keys.size(); n++) {
            String prefix = KeyInput.format(keys.subList(0, n));
            if (entry.kind() != Kind.OBJECT && entry.kind() != Kind.VISUAL) normalPrefixes.add(prefix);
            if (entry.kind() == Kind.MOTION || entry.kind() == Kind.OBJECT) pendingPrefixes.add(prefix);
            if (entry.kind() != Kind.ACTION) visualPrefixes.add(prefix);
        }
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/Machine.java` with:

```java
package dev.jasper.editor.vim;

import dev.jasper.editor.vim.Target.Kind;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The Normal-mode command parser (spec 1): {@code ["x][count]command}, where a command is an action, a motion, or
 * an operator followed by {@code [count][v|V]} and a motion or text object. It records each change for {@code .}
 * and brackets it in one undo step; a change that enters Insert mode stays open until the insert ends.
 */
final class Machine {
    private final VimEditor editor;
    private final Vim vim;
    private int count1, count2;
    private char register;
    private String operator;
    private char forced;
    /** The forced motion type for a block ({@code d<C-v>j}). */
    static final char CTRL_V = (char) 0x16;
    private String keys = "";
    private final List<KeyInput> shown = new ArrayList<>();
    private Consumer<KeyInput> argument;
    /** The keys of the change being typed, without counts or register, for {@code .}. */
    private final List<KeyInput> record = new ArrayList<>();
    private char recordRegister;
    private int recordCount;
    /** For a Visual-mode change being recorded, the size of its area. */
    private Visual.Redo recordVisual, visualRedo;
    /** The command line typed for the motion being run ({@code /pattern}), or null. */
    private String line;
    private boolean recordingInsert, replaying, resumeReplace;
    private int resumeEndLine = -1;
    private int changeDepth;

    Machine(VimEditor editor) {
        this.editor = editor;
        this.vim = editor.vim();
    }

    boolean operatorPending() { return operator != null; }

    /** @return whether a command is partly typed */
    boolean pending() { return operator != null || argument != null || !keys.isEmpty() || count1 > 0 || count2 > 0 || register != 0; }

    /** @return the partly typed command, for 'showcmd' */
    String showcmd() { return KeyInput.format(shown); }

    boolean replaying() { return replaying; }

    /** @return whether the key means something now, for the handler rule */
    boolean wants(KeyInput key) {
        if (argument != null || key.plainChar() >= 0 || key.is("<Esc>")) return true;
        if (key.is("<C-c>")) return pending() || editor.mode() == Mode.INSERT_NORMAL || editor.mode().visual();
        String candidate = keys + key.notation();
        VimCommands commands = vim.commands();
        if (editor.mode().visual()) return commands.visualKey(candidate) != null || commands.visualPrefix(candidate);
        VimCommands.Entry entry = operator == null ? commands.normal(candidate) : commands.pending(candidate);
        return entry != null || commands.prefix(candidate, operator != null);
    }

    void key(KeyInput key) {
        shown.add(key);
        if (argument != null) {
            Consumer<KeyInput> handler = argument;
            argument = null;
            if (key.is("<Esc>")) {
                reset();
                resumeIfInsertNormal();
                return;
            }
            handler.accept(key);
            return;
        }
        if (key.is("<Esc>") || key.is("<C-c>")) {
            reset();
            if (editor.mode().visual()) Visual.end(editor);
            resumeIfInsertNormal();
            return;
        }
        int c = key.plainChar();
        if (keys.isEmpty() && c >= '0' && c <= '9' && (c != '0' || (operator == null ? count1 : count2) > 0)) {
            if (operator == null) count1 = Math.min(count1 * 10 + (c - '0'), 99_999_999);
            else count2 = Math.min(count2 * 10 + (c - '0'), 99_999_999);
            return;
        }
        if (keys.isEmpty() && operator == null && c == '"') {
            argument = name -> {
                int n = name.plainChar();
                if (n < 0 || !Registers.valid((char) n, false)) throw Context.fail();
                register = (char) n;
            };
            return;
        }
        if (editor.mode().visual()) {
            keys += key.notation();
            record.add(key);
            visualKey();
            return;
        }
        if (operator != null && keys.isEmpty() && (c == 'v' || c == 'V' || key.is("<C-v>"))) {
            forced = c == 'v' || c == 'V' ? (char) c : CTRL_V;
            record.add(key);
            return;
        }
        keys += key.notation();
        record.add(key);
        if (operator != null && doubled()) {
            applyOperator(context(null), lineSpan(context(null)));
            return;
        }
        VimCommands.Entry entry;
        if (operator == null) entry = keys.equals("~") && editor.options().flag("tildeop") ? vim.commands().normal("g~") : vim.commands().normal(keys);
        else entry = vim.commands().pending(keys);
        if (entry == null) {
            if (vim.commands().prefix(keys, operator != null)) return;
            throw Context.fail();
        }
        if (entry.needsChar()) {
            VimCommands.Entry waiting = entry;
            argument = arg -> {
                record.add(arg);
                run(waiting, arg);
            };
            return;
        }
        if (vim.commands().needsLine(entry)) {
            readLine(entry, false);
            return;
        }
        run(entry, null);
    }

    /**
     * {@code /} and {@code ?}: the command line reads the pattern; {@code <CR>} runs the motion with it, and
     * cancelling drops the command (in Visual mode the area stays).
     */
    private void readLine(VimCommands.Entry entry, boolean visual) {
        char type = entry.keys().charAt(0);
        editor.commandLine().start(type, vim.search().history(), text -> editor.incsearch(type, text), text -> {
            editor.incsearchDone();
            record.addAll(editor.commandLine().typed());
            line = text;
            if (visual) runVisual(entry, null);
            else run(entry, null);
        }, () -> {
            editor.incsearchDone();
            reset();
            resumeIfInsertNormal();
        });
    }

    /** Motions and the 'foldopen' item that makes them open the folds at the cursor (normal.c's FDO_ checks). */
    private static final java.util.Map<String, String> FOLDOPEN = foldopenTable();

    private static java.util.Map<String, String> foldopenTable() {
        var table = new java.util.HashMap<String, String>();
        for (String k : List.of("h", "l", "<Left>", "<Right>", "<BS>", " ", "$", "<End>", "f", "F", "t", "T", ";", ",", "w", "W", "b", "B",
            "e", "E", "0", "^", "<S-Right>", "<C-Right>", "<S-Left>", "<C-Left>")) table.put(k, "hor");
        for (String k : List.of("(", ")", "{", "}", "[[", "]]", "[]", "][", "[(", "[{", "])", "]}")) table.put(k, "block");
        table.put("%", "percent");
        for (String k : List.of("'", "`")) table.put(k, "mark");
        for (String k : List.of("/", "?", "n", "N", "*", "#", "g*", "g#")) table.put(k, "search");
        for (String k : List.of("G", "gg")) table.put(k, "jump");
        return table;
    }

    /**
     * 'foldopen': a typed motion (not replayed by {@code .}, not after an operator) opens the folds that hide the
     * cursor. Marks, % and <C-o> only when they changed the line; h and l only when they moved.
     */
    private void foldOpen(String keys, Pos before) {
        String item = FOLDOPEN.get(keys);
        if (item == null || replaying || !editor.options().has("foldopen", item)) return;
        Pos after = editor.cursor();
        if ((item.equals("mark") || item.equals("percent")) && after.line == before.line) return;
        if (item.equals("hor") && after.equals(before) && (keys.equals("h") || keys.equals("l") || keys.equals("<Left>") || keys.equals("<Right>"))) return;
        editor.folds().openCursor(after.line + 1);
    }

    /** 'foldopen' for commands that are not motions: undo and redo ({@code undo}), the jumplist and change list ({@code mark}). */
    void foldOpenAfter(String item, Pos before) {
        if (replaying || !editor.options().has("foldopen", item)) return;
        Pos after = editor.cursor();
        if (item.equals("mark") && after.line == before.line) return;
        editor.folds().openCursor(after.line + 1);
    }

    /** A key in Visual mode: a Visual command, a motion that moves the cursor, an operator, or a text object. */
    private void visualKey() {
        VimCommands.Entry entry = vim.commands().visualKey(keys);
        if (entry == null) {
            if (vim.commands().visualPrefix(keys)) return;
            throw Context.fail();
        }
        if (entry.needsChar()) {
            argument = arg -> {
                record.add(arg);
                runVisual(entry, arg);
            };
            return;
        }
        if (vim.commands().needsLine(entry)) {
            readLine(entry, true);
            return;
        }
        runVisual(entry, null);
    }

    private void runVisual(VimCommands.Entry entry, KeyInput argument) {
        Context context = context(argument);
        context.visual = editor.visualAnchor();
        vim.covered(entry.id());
        switch (entry.kind()) {
            case MOTION -> {
                Target target = ((VimCommands.Motion) entry.handler()).move(context);
                if (target == null) throw Context.fail();
                if (target.jump()) editor.marks().setpcmark(editor.cursor());
                Pos before = editor.cursor();
                editor.moveTo(target);
                foldOpen(entry.keys(), before);
                complete(false);
            }
            case OBJECT -> {
                if (((VimCommands.TextObject) entry.handler()).select(context) == null) throw Context.fail();
                complete(false);
            }
            case OPERATOR -> Visual.operate(context, entry.keys(), false);
            case VISUAL -> {
                ((VimCommands.Action) entry.handler()).run(context);
                if (!keys.isEmpty()) complete(false);
            }
            default -> throw Context.fail();
        }
    }

    /**
     * An operator on a Visual area (do_pending_operator with VIsual_active): Visual mode ends, the area is
     * remembered for gv, and the operator runs on the span, recorded for {@code .} with the area's size.
     */
    void applyVisual(Context context, String operatorKeys, Visual.Range range, Mode original) {
        Visual.remember(editor, original);
        editor.visualAnchor(null);
        editor.mode(Mode.NORMAL);
        operator = operatorKeys;
        visualRedo = range.redo();
        Span span = range.span();
        switch (operatorKeys) {
            case "J", "gJ", "r", "I", "A", "<C-a>", "<C-x>", "g<C-a>", "g<C-x>" -> {
                boolean change = true;
                editor.forgetColumn();
                editor.setCursor(span.start(), true);
                beginChange();
                try {
                    switch (operatorKeys) {
                        case "J", "gJ" -> Operators.joinVisual(context, span, operatorKeys.equals("J"));
                        case "r" -> Operators.replace(context, span);
                        case "<C-a>", "<C-x>", "g<C-a>", "g<C-x>" -> Numbers.visual(context, span, operatorKeys.endsWith("<C-x>"), operatorKeys.startsWith("g"));
                        default -> Operators.insert(context, span, operatorKeys.equals("A"));
                    }
                } catch (RuntimeException failure) {
                    endChangeUnlessInserting();
                    throw failure;
                }
                complete(change);
            }
            default -> applyOperator(context, span);
        }
    }

    /** A Visual-mode change that is not an operator (put): records it for {@code .} with the area's size. */
    void visualChange(Visual.Redo redo) {
        visualRedo = redo;
    }

    /** {@code dd}, {@code gUU}, {@code gUgU}, and {@code ~~} with 'tildeop'. */
    private boolean doubled() {
        if (keys.equals(operator)) return true;
        if (operator.length() == 2 && operator.charAt(0) == 'g' && keys.equals(operator.substring(1))) return true;
        return operator.equals("g~") && keys.equals("~") && editor.options().flag("tildeop");
    }

    private Context context(KeyInput argument) {
        var context = new Context(editor);
        context.hasCount = count1 > 0 || count2 > 0;
        context.count = Math.max(count1, 1) * Math.max(count2, 1);
        context.register = register;
        context.argument = argument;
        context.operator = operator;
        context.forced = forced;
        context.line = line;
        return context;
    }

    private void run(VimCommands.Entry entry, KeyInput argument) {
        Context context = context(argument);
        vim.covered(entry.id());
        switch (entry.kind()) {
            case OPERATOR -> {
                operator = entry.keys();
                count1 = context.hasCount ? context.count : 0;
                count2 = 0;
                keys = "";
            }
            case MOTION -> {
                Target target = ((VimCommands.Motion) entry.handler()).move(context);
                if (target == null) throw Context.fail();
                if (target.jump()) editor.marks().setpcmark(editor.cursor());
                if (operator != null && forced == CTRL_V) {
                    forcedBlock(target);
                    return;
                }
                if (operator == null) {
                    Pos before = editor.cursor();
                    editor.moveTo(target);
                    foldOpen(entry.keys(), before);
                    complete(false);
                } else applyOperator(context, span(context, target));
            }
            case OBJECT -> {
                Span span = ((VimCommands.TextObject) entry.handler()).select(context);
                if (span == null) throw Context.fail();
                applyOperator(context, span);
            }
            case ACTION -> {
                if (entry.keys().equals(".")) {
                    reset();
                    repeat(context);
                    resumeIfInsertNormal();
                    return;
                }
                boolean change = entry.change();
                if (!entry.keys().equals("u") && !entry.keys().equals("<C-r>") && !ViewCommands.KEEP_COLUMN.contains(entry.keys()))
                    editor.forgetColumn();
                if (change) beginChange();
                try {
                    ((VimCommands.Action) entry.handler()).run(context);
                } catch (RuntimeException failure) {
                    if (change) endChangeUnlessInserting();
                    throw failure;
                }
                complete(change);
            }
            case INSERT -> throw new IllegalStateException("Insert-mode key in Normal mode: " + entry.keys());
        }
    }

    /**
     * The lines a doubled operator acts on (nv_lineop): the cursor line and {@code count - 1} more. Like Vim, the
     * "motion" lands on the last line's first non-blank (keeping the column for {@code yy}, and for {@code dd},
     * {@code <<} and {@code >>} with 'nostartofline'), so the span starts at whichever comes first.
     */
    private Span lineSpan(Context context) {
        Pos cursor = context.cursor();
        int last = editor.buffer().count() - 1;
        // cursor_down(count - 1): a closed fold counts as one line.
        int[] fold = editor.folds().closed(cursor.line + 1);
        if (context.count > 1 && (fold != null ? fold[1] - 1 : cursor.line) >= last) throw Context.fail();
        int end = editor.view().downFrom(cursor.line + 1, context.count - 1) - 1;
        if (forced == 'v') return new Span(cursor.copy(), new Pos(end, editor.wantedColumn(end)), Kind.EXCLUSIVE);
        String text = editor.buffer().line(end);
        boolean sol = operator.equals("d") || operator.equals(">") || operator.equals("<lt>");
        int col;
        if (operator.equals("y")) col = end == cursor.line ? cursor.col : editor.wantedColumn(end);
        else if (sol && !editor.options().flag("startofline")) col = editor.wantedColumn(end);
        else col = Text.firstNonBlank(text) >= text.length() && !text.isEmpty() ? Text.last(text) : Text.firstNonBlank(text);
        Pos target = new Pos(end, col);
        Pos start = Pos.min(cursor, target).copy(), stop = Pos.max(cursor, target).copy();
        // Closed folds are taken whole.
        int[] first = editor.folds().closed(start.line + 1), lastFold = editor.folds().closed(stop.line + 1);
        if (first != null) start = new Pos(first[0] - 1, 0);
        if (lastFold != null) stop = new Pos(lastFold[1] - 1, 0);
        return new Span(start, stop, Kind.LINEWISE);
    }

    /** Runs {@code x}, {@code D} and the other abbreviations as their operator and motion, inside their own step. */
    void alias(Context action, String operatorKeys, String motionKeys) {
        operator = operatorKeys;
        count1 = action.hasCount ? action.count : 0;
        count2 = 0;
        Context context = context(null);
        Span span;
        if (motionKeys.equals(operatorKeys)) span = lineSpan(context);
        else {
            VimCommands.Entry motion = vim.commands().pending(motionKeys);
            Target target = ((VimCommands.Motion) motion.handler()).move(context);
            if (target == null) throw Context.fail();
            span = span(context, target);
        }
        applyBody(context, span);
        operator = null;
    }

    /** The span an operator acts on for a motion: forced motion types and Vim's exclusive adjustment. */
    private Span span(Context context, Target target) {
        Pos cursor = context.cursor();
        Kind kind = target.kind();
        if (forced == 'v') kind = kind == Kind.LINEWISE ? Kind.EXCLUSIVE : kind == Kind.EXCLUSIVE ? Kind.INCLUSIVE : Kind.EXCLUSIVE;
        else if (forced == 'V') kind = Kind.LINEWISE;
        Pos orig = cursor.copy(), moved = target.pos().copy();
        // do_pending_operator: closed folds are taken whole.
        Folds folds = editor.folds();
        Buffer b = editor.buffer();
        if (orig.compareTo(moved) < 0) {
            int[] f = folds.closed(orig.line + 1);
            if (f != null) orig = new Pos(f[0] - 1, 0);
            int[] g = folds.closed(moved.line + 1);
            if ((moved.col > 0 || kind != Kind.EXCLUSIVE) && g != null) moved = new Pos(g[1] - 1, b.line(g[1] - 1).length());
        } else if (kind == Kind.LINEWISE) {
            int[] f = folds.closed(moved.line + 1);
            if (f != null) moved = new Pos(f[0] - 1, 0);
            int[] g = folds.closed(orig.line + 1);
            if (g != null) orig = new Pos(g[1] - 1, b.line(g[1] - 1).length());
        }
        Pos start = Pos.min(orig, moved).copy(), end = Pos.max(orig, moved).copy();
        boolean adjusted = false;
        if (kind == Kind.EXCLUSIVE && forced == 0 && target.adjust() && end.col == 0 && end.line > start.line) {
            adjusted = true;
            end.line--;
            if (context.walk(false).inIndent(start, 0)) kind = Kind.LINEWISE;
            else {
                String line = editor.buffer().line(end.line);
                end.col = line.length();
                if (end.col > 0) {
                    end.col = Text.previous(line, end.col);
                    kind = Kind.INCLUSIVE;
                }
            }
        }
        return new Span(start, end, kind, target.regOne(), null, false, adjusted);
    }

    /**
     * An operator with a motion forced blockwise ({@code d<C-v>2j}): as in Vim, the motion's two ends become a
     * Visual block and the operator runs on it; {@code .} repeats it on a block of the same size.
     */
    private void forcedBlock(Target target) {
        Pos anchor = editor.cursor();
        String keys = operator;
        editor.visualAnchor(anchor);
        editor.mode(Mode.VISUAL_BLOCK);
        editor.moveTo(target);
        Context context = context(null);
        context.visual = anchor;
        record.clear();
        record.addAll(KeyInput.parse(keys));
        applyVisual(context, keys, Visual.range(context, Mode.VISUAL_BLOCK, keys), Mode.VISUAL_BLOCK);
    }

    private void applyOperator(Context context, Span span) {
        boolean change = !operator.equals("y");
        context.cursorStart = editor.cursor();
        editor.forgetColumn();
        editor.setCursor(span.start(), true);
        if (change) beginChange();
        try {
            applyBody(context, span);
        } catch (RuntimeException failure) {
            if (change) endChangeUnlessInserting();
            throw failure;
        }
        complete(change);
    }

    private void applyBody(Context context, Span span) {
        VimCommands.Entry entry = vim.commands().normal(operator);
        vim.covered(entry.id());
        editor.setCursor(span.start(), true);
        editor.area().vimUndo().stepCursor(span.start().line, span.start().col);
        ((VimCommands.Operator) entry.handler()).apply(context, span);
        setChangeMarks(span);
    }

    /**
     * The {@code '[} and {@code ']} marks after an operator, as each of Vim's op_* functions sets them: a delete marks
     * where the text was ({@code ']} in a block on its last line); the others mark the span, whole lines for a linewise one.
     */
    private void setChangeMarks(Span span) {
        if (operator.equals("gq") || operator.equals("gw")) return;
        Marks marks = editor.marks();
        Pos start = span.start().copy(), end = span.end().copy();
        end.line = Math.min(end.line, editor.buffer().count() - 1);
        if (operator.equals("d") || operator.equals("c")) {
            marks.set('[', start);
            marks.set(']', span.blockwise() ? new Pos(end.line, start.col) : start);
            return;
        }
        if (span.linewise()) {
            start.col = 0;
            end.col = Visual.MAX;
            if (!operator.equals("y")) {
                // op_shift and op_tilde: the last line's last character.
                String last = editor.buffer().line(end.line);
                end.col = last.isEmpty() ? 0 : Text.last(last);
            }
        } else if (!span.blockwise() && span.kind() == Target.Kind.EXCLUSIVE) Search.decl(editor.buffer(), end);
        if (operator.equals(">") || operator.equals("<lt>")) {
            String last = editor.buffer().line(end.line);
            end.col = last.isEmpty() ? 0 : Text.last(last);
        }
        marks.set('[', start);
        marks.set(']', end);
    }

    /** A command completed: records it for {@code .} and closes its undo step, unless it entered Insert mode. */
    private void complete(boolean change) {
        if (change && !replaying) {
            recordRegister = register;
            recordCount = count1 > 0 || count2 > 0 ? Math.max(count1, 1) * Math.max(count2, 1) : 0;
            recordVisual = visualRedo;
            if (editor.mode().inserting()) recordingInsert = true;
            else vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record), recordVisual));
        }
        visualRedo = null;
        if (change) endChangeUnlessInserting();
        reset();
        resumeIfInsertNormal();
    }

    /** After {@code i_CTRL-O}'s command, or an {@code <Esc>} instead of one, Insert mode resumes as a new insert. */
    void resumeIfInsertNormal() {
        if (editor.mode() != Mode.INSERT_NORMAL) return;
        // edit.c: back after the end of the line when it was there before CTRL-O, or when the column wants it ($).
        Pos p = editor.cursor();
        String line = editor.buffer().line(p.line);
        if (!line.isEmpty() && p.col == Text.last(line) && (p.line == resumeEndLine || editor.wantsPastEnd())) editor.setCursor(new Pos(p.line, line.length()), true);
        resumeEndLine = -1;
        editor.insert().begin("i", 1, resumeReplace, -1);
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of(resumeReplace ? 'R' : 'i'));
        recordRegister = 0;
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /** Drops the partly typed command, keeping an insert being recorded. */
    void reset() {
        count1 = count2 = 0;
        register = 0;
        operator = null;
        forced = 0;
        visualRedo = null;
        line = null;
        keys = "";
        argument = null;
        shown.clear();
        if (!recordingInsert) record.clear();
    }

    /** After an internal error: forgets everything, including a recorded insert, and closes every undo step. */
    void abort() {
        recordingInsert = false;
        reset();
        while (changeDepth > 0) endChange();
    }

    void recordInsert(KeyInput key) {
        if (recordingInsert && !replaying) record.add(key);
    }

    /** The insert a recorded change entered has ended: stores the change and closes its undo step. */
    void insertEnded() {
        if (recordingInsert && !replaying) {
            record.add(KeyInput.named("Esc"));
            vim.lastChange(new Vim.Change(recordRegister, recordCount, List.copyOf(record), recordVisual));
        }
        recordingInsert = false;
        record.clear();
        endChange();
    }

    /**
     * {@code i_CTRL-O} was typed: Insert mode resumes after one command.
     *
     * @param replace whether it was Replace mode
     * @param endLine the line whose end the cursor was after, or -1
     */
    void pausedInsert(boolean replace, int endLine) {
        resumeReplace = replace;
        resumeEndLine = endLine;
    }

    /** An arrow key moved in Insert mode: a new undo step, and {@code .} repeats only what follows, as {@code i}. */
    void restartInsert() {
        endChange();
        beginChange();
        if (replaying) return;
        record.clear();
        record.add(KeyInput.of('i'));
        recordRegister = 0;
        recordCount = 0;
        recordVisual = null;
        recordingInsert = true;
    }

    /** {@code .}: the last change again, the new count replacing its count; {@code "1p.} puts register 2. */
    void repeat(Context context) {
        Vim.Change change = vim.lastChange();
        if (change == null) throw Context.fail();
        // A Visual change repeats with its own count on an area of the same size (redo_VIsual).
        int count = context.hasCount && change.visual() == null ? context.count : change.count();
        char reg = change.register();
        if (reg >= '1' && reg < '9') reg++;
        List<KeyInput> replay = new ArrayList<>();
        if (reg != 0) {
            replay.add(KeyInput.of('"'));
            replay.add(KeyInput.of(reg));
        }
        if (count > 0) String.valueOf(count).chars().forEach(d -> replay.add(KeyInput.of(d)));
        replay.addAll(change.keys());
        replaying = true;
        beginChange();
        try {
            if (change.visual() != null) Visual.reselectForRedo(editor, change.visual());
            for (KeyInput key : replay) editor.dispatch(key);
        } finally {
            replaying = false;
            endChange();
            recordingInsert = false;
            reset();
        }
        vim.lastChange(new Vim.Change(reg, count, change.keys(), change.visual()));
    }

    private void beginChange() {
        changeDepth++;
        editor.beginUndoStep();
    }

    private void endChange() {
        if (changeDepth == 0) return;
        changeDepth--;
        editor.endUndoStep();
    }

    private void endChangeUnlessInserting() {
        if (!editor.mode().inserting()) endChange();
    }
}
```

Replace the whole of `plugins/editor/src/main/java/dev/jasper/editor/vim/VimEditor.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.List;
import javax.swing.event.CaretListener;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import org.fife.ui.rtextarea.CaretStyle;
import org.fife.ui.rtextarea.ConfigurableCaret;
import org.fife.ui.rtextarea.RTextArea;

/**
 * Vim in one tab (spec 1.1): the mode, the parser, the insert session, the remembered column, the tab's local
 * options and its messages. Attaching it to a {@link VimTextArea} turns Vim on for that area; {@link #close} turns
 * it off and leaves the text as it is. UI thread only.
 */
public final class VimEditor {
    private static final System.Logger LOG = System.getLogger(VimEditor.class.getName());

    private final Vim vim;
    private final VimTextArea area;
    private final Buffer buffer;
    private final Options options;
    private final Machine machine;
    private final InsertMode insert;
    private final CommandLine commandLine = new CommandLine(this);
    private final Marks marks;
    private final List<Runnable> listeners = new ArrayList<>();
    private final CaretListener caretListener = event -> caretMoved();
    private final CaretStyle originalCaret;
    private Mode mode = Mode.NORMAL;
    /** The display column vertical motions aim for; {@link Integer#MAX_VALUE} for the end of the line, -1 to take it from the cursor. */
    private int curswant = -1;
    private String message = "";
    private boolean messageError;
    private int bells;
    private boolean busy;
    private EditorDefaults defaults;
    /** In Visual mode, the other end of the area (Vim's VIsual). */
    private Pos visualAnchor;
    private final Highlights highlights;
    private final View view = new View(this);
    private final ViewportSync viewport;
    private final Folds folds;
    /** The text area's word-wrapping, put back when Vim turns off (Vim wraps at any character). */
    private final boolean originalWrapStyleWord;
    /** The text area's fold-aware navigation, put back when Vim turns off (Vim's cursor may be inside a closed fold). */
    private final javax.swing.text.NavigationFilter originalNavigation;
    /** While a search pattern is typed with 'incsearch', the match it would find ({start, end} offsets), or null. */
    private int[] incsearch;
    /** Bumped by every text change, so the 'hlsearch' matches are found again only when needed. */
    private int textVersion;
    private List<Object> highlightedPattern;
    private int highlightedVersion = -1;
    private List<int[]> searchMatches = List.of();
    private boolean highlightPending;
    private java.util.function.Supplier<String> documentName = () -> "[No Name]";
    private java.util.function.BooleanSupplier documentModified = () -> false;
    /** Leaving the tab sets the {@code '"} mark (spec 3.6). */
    private final java.awt.event.FocusListener leaveListener = new java.awt.event.FocusAdapter() {
        @Override public void focusLost(java.awt.event.FocusEvent event) { marks.set('"', cursor()); }
    };
    private final DocumentListener highlightListener = new DocumentListener() {
        @Override public void insertUpdate(DocumentEvent event) { textChanged(); }
        @Override public void removeUpdate(DocumentEvent event) { textChanged(); }
        @Override public void changedUpdate(DocumentEvent event) { }
    };

    /**
     * Turns Vim on for the area.
     *
     * @param vim the global state
     * @param area the tab's text area
     * @param defaults the Editor's option defaults for the tab
     */
    public VimEditor(Vim vim, VimTextArea area, EditorDefaults defaults) {
        this.vim = vim;
        this.area = area;
        this.buffer = new Buffer(area.getDocument());
        this.marks = new Marks(area.getDocument());
        this.options = vim.options().local();
        this.machine = new Machine(this);
        this.insert = new InsertMode(this);
        this.highlights = new Highlights(area);
        this.folds = new Folds(area);
        this.viewport = new ViewportSync(this, area);
        this.originalWrapStyleWord = area.getWrapStyleWord();
        area.setWrapStyleWord(false);
        this.originalNavigation = area.getNavigationFilter();
        area.setNavigationFilter(null);
        area.getDocument().addDocumentListener(highlightListener);
        area.addFocusListener(leaveListener);
        originalCaret = area.getCaret() instanceof ConfigurableCaret caret ? caret.getStyle() : CaretStyle.VERTICAL_LINE_STYLE;
        editorDefaults(defaults);
        options.onChanged(this::syncArea);
        syncArea();
        area.attach(this);
        area.addCaretListener(caretListener);
        busy = true;
        try {
            area.select(area.getCaretPosition(), area.getCaretPosition());
            setCursor(cursor());
        } finally { busy = false; }
        look();
    }

    /** Turns Vim off: the caret and input methods return to the Editor's; the text is untouched. */
    public void close() {
        insert.abort();
        machine.abort();
        area.removeCaretListener(caretListener);
        area.attach(null);
        area.setCaretStyle(RTextArea.INSERT_MODE, originalCaret);
        area.enableInputMethods(true);
        marks.close();
        area.getDocument().removeDocumentListener(highlightListener);
        area.removeFocusListener(leaveListener);
        area.setWrapStyleWord(originalWrapStyleWord);
        area.setNavigationFilter(originalNavigation);
        viewport.close();
        highlights.clear();
        vim.forget(this);
        buffer.close();
    }

    /**
     * The Editor's defaults changed (its tab size, or Tabs/Spaces): the tab's options follow.
     *
     * @param changed the new defaults
     */
    public void editorDefaults(EditorDefaults changed) {
        if (changed.equals(defaults)) return;
        defaults = changed;
        options.setLocal("tabstop", changed.tabstop());
        options.setLocal("shiftwidth", changed.tabstop());
        options.setLocal("expandtab", changed.expandtab());
        options.setLocal("autoindent", changed.autoindent());
        options.setLocal("wrap", changed.wrap());
    }

    /** The text was replaced (a reload): any command or insert in progress is dropped. */
    public void textReplaced() {
        insert.abort();
        machine.abort();
        mode = Mode.NORMAL;
        visualAnchor = null;
        marks.clear();
        busy = true;
        try { setCursor(cursor()); }
        finally { busy = false; }
        look();
        fire();
    }

    /**
     * The handler rule (spec 2.2): gives the key to Vim when Vim takes it.
     *
     * @param key a key from the text area
     * @return whether Vim took it
     */
    public boolean offer(KeyInput key) {
        if (commandLine.active()) {
            // The command line takes every key but the ⌘ ones.
            if ((key.modifiers() & KeyInput.META) != 0) return false;
            feed(key);
            return true;
        }
        boolean mine = switch (vim.handlers().handler(key, handlerMode())) {
            case VIM -> true;
            case IDE -> false;
            case DEFAULT -> (key.modifiers() & KeyInput.META) == 0 && (mode.inserting() ? insert.wants(key) : machine.wants(key));
        };
        if (mine) feed(key);
        return mine;
    }

    private char handlerMode() {
        return switch (mode()) {
            case INSERT, REPLACE -> 'i';
            case OPERATOR_PENDING -> 'o';
            case VISUAL, VISUAL_LINE, VISUAL_BLOCK -> 'x';
            default -> 'n';
        };
    }

    /** @return whether the mode is Insert or Replace, where keys Vim declines go on to the text area */
    boolean editing() { return mode.inserting(); }

    /**
     * For a key {@link #offer} declined: whether it goes on to the text area anyway. In Insert and Replace mode every
     * declined key does; elsewhere only a ⌘ key or one a {@code sethandler} entry gives to the Editor. The text area
     * also passes on a key bound to one of the Editor's own commands (spec 2.2).
     */
    boolean passesOn(KeyInput key) {
        if (commandLine.active()) return (key.modifiers() & KeyInput.META) != 0;
        if (mode.inserting() || (key.modifiers() & KeyInput.META) != 0) return true;
        return vim.handlers().handler(key, handlerMode()) == KeyHandlers.Handler.IDE;
    }

    /** A key the text area held back outside Insert mode: Vim has no meaning for it, so the bell rings. */
    void declined() {
        bells++;
        fire();
    }

    /** @return the window onto the text (topline, size, 'wrap') */
    View view() { return view; }

    /**
     * The user scrolled the text area to show {@code topline} first: the view follows, and the cursor moves inside
     * the window when it was scrolled out, keeping its column, as with Vim's scroll wheel.
     */
    void viewScrolled(int topline) {
        if (mode.inserting() || commandLine.active()) {
            view.topline(topline);
            view.compBotline();
            return;
        }
        view.topline(topline);
        view.compBotline();
        int lnum = cursor().line + 1;
        int target = lnum < view.topline ? view.topline : lnum >= view.botline && view.botline <= buffer.count() ? view.botline - 1 : lnum;
        if (target != lnum) {
            busy = true;
            try { setCursor(new Pos(target - 1, wantedColumn(target - 1)), mode.visual()); }
            finally { busy = false; }
        }
        fire();
    }

    /** @return the tab's code folds */
    Folds folds() { return folds; }

    /** @return the remembered display column, computed from the cursor when it was forgotten (Vim's w_curswant) */
    int columnWanted() { return want(false); }

    /** Rings the bell without failing the command (a key the command line has no use for). */
    void bell() { bells++; }

    /** @return the command line, active while {@code /} or {@code ?} reads a pattern */
    CommandLine commandLine() { return commandLine; }

    /** A failing Insert-mode key: the bell rings and the insert goes on, as Vim's ins_del and ins_bs beep (spec 2.3). */
    void insertKeyFailed(VimFailure failure) {
        bells++;
        if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
        fire();
    }

    /**
     * Runs one key, as typed. A failing command rings the bell; an unexpected exception cancels the command, closes
     * its undo step and returns to Normal mode (spec 10).
     *
     * @param key the key
     */
    public void feed(KeyInput key) {
        busy = true;
        message = "";
        messageError = false;
        boolean vertical = key.is("<Up>") || key.is("<Down>");
        if (mode.inserting() && !vertical) curswant = -1;
        try {
            dispatch(key);
        } catch (VimFailure failure) {
            bells++;
            if (!failure.getMessage().isEmpty()) message(failure.getMessage(), true);
            if (!mode.inserting()) {
                machine.reset();
                machine.resumeIfInsertNormal();
            }
        } catch (RuntimeException bug) {
            LOG.log(System.Logger.Level.ERROR, "Vim command failed", bug);
            insert.abort();
            machine.abort();
            mode = Mode.NORMAL;
            message("Internal error; the command was cancelled", true);
        } finally {
            busy = false;
            if (!commandLine.active()) marks.checkpcmark(cursor());
            if (mode == Mode.NORMAL || mode == Mode.OPERATOR_PENDING) setCursor(cursor());
            view.updateTopline();
            view.cursColumns();
            viewport.show();
            look();
            fire();
        }
    }

    /** Runs one key without the failure handling, for {@code .} and a count's repeat. */
    void dispatch(KeyInput key) {
        if (commandLine.active()) commandLine.key(key);
        else if (mode.inserting()) insert.key(key);
        else machine.key(key);
    }

    /** Text an input method committed in Insert mode, recorded for {@code .}. */
    void typedByInputMethod(String text) { insert.typedByInputMethod(text); }

    /** @return the mode */
    public Mode mode() {
        if (commandLine.active()) return Mode.COMMAND_LINE;
        return mode == Mode.NORMAL && machine.operatorPending() ? Mode.OPERATOR_PENDING : mode;
    }

    /** @return the partly typed command ('showcmd') */
    public String showcmd() {
        if (!options.flag("showcmd")) return "";
        String pending = machine.showcmd();
        return pending.isEmpty() && mode.visual() && visualAnchor != null ? visualSize() : pending;
    }

    /**
     * The Visual area's size as Vim's showcmd shows it: {@code 3x5} (lines by display columns) for a block, the line
     * count for a linewise or multi-line area, else the character count, with the UTF-8 byte count after it when it
     * differs ({@code 3-4}).
     */
    private String visualSize() {
        Pos cursor = cursor();
        int lines = Math.abs(cursor.line - visualAnchor.line) + 1;
        if (mode == Mode.VISUAL_BLOCK) {
            int ts = options.number("tabstop");
            int left = Math.min(Visual.startVcol(buffer, visualAnchor, ts), Visual.startVcol(buffer, cursor, ts));
            int right = Math.max(Visual.endVcol(buffer, visualAnchor, ts), Visual.endVcol(buffer, cursor, ts));
            return lines + "x" + (right - left + 1);
        }
        if (mode == Mode.VISUAL_LINE || lines > 1) return String.valueOf(lines);
        String line = buffer.line(cursor.line);
        int from = Math.min(cursor.col, visualAnchor.col), to = Math.max(cursor.col, visualAnchor.col);
        to = to < line.length() ? Text.next(line, to) : line.length();
        String part = line.substring(Math.min(from, line.length()), to);
        int chars = part.codePointCount(0, part.length()), bytes = part.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        if (from >= line.length()) chars = bytes = 1;
        return bytes == chars ? String.valueOf(chars) : chars + "-" + bytes;
    }

    /** @return the last message, or empty */
    public String message() { return message; }

    /** @return whether the message is an error */
    public boolean messageIsError() { return messageError; }

    /** @return how many times the bell has rung; the Vim bar flashes when it changes */
    public int bells() { return bells; }

    /** @param listener runs after every key and every change of mode or message */
    public void addListener(Runnable listener) { listeners.add(listener); }

    /** @param listener no longer runs */
    public void removeListener(Runnable listener) { listeners.remove(listener); }

    /** @return whether the Vim bar shows the mode ('showmode') */
    public boolean showmode() { return options.flag("showmode"); }

    /** @return the tab's options */
    public Options options() { return options; }

    Vim vim() { return vim; }

    VimTextArea area() { return area; }

    Buffer buffer() { return buffer; }

    Machine machine() { return machine; }

    Marks marks() { return marks; }

    /** @return in Visual mode, the other end of the area; null otherwise */
    Pos visualAnchor() { return visualAnchor == null ? null : visualAnchor.copy(); }

    void visualAnchor(Pos anchor) { visualAnchor = anchor == null ? null : anchor.copy(); }

    /** @return the remembered display column; {@link Integer#MAX_VALUE} after {@code $}, -1 when forgotten */
    int curswant() { return curswant; }

    void curswant(int value) { curswant = value; }

    InsertMode insert() { return insert; }

    void mode(Mode value) { mode = value; }

    void message(String text, boolean error) {
        message = text;
        messageError = error;
    }

    /** @return the cursor as a position */
    Pos cursor() { return buffer.pos(area.getCaretPosition()); }

    /** Moves the cursor, clamping it to the text; in Normal mode never past the last character. */
    void setCursor(Pos pos) { setCursor(pos, false); }

    /** @param pastEnd whether the cursor may sit after the last character (operators and Insert mode) */
    void setCursor(Pos pos, boolean pastEnd) {
        int line = Math.max(0, Math.min(pos.line, buffer.count() - 1));
        String text = buffer.line(line);
        int col = Math.max(0, Math.min(pos.col, text.length()));
        boolean normal = mode == Mode.NORMAL && !pastEnd && !machine.operatorPending();
        if (normal && col >= text.length() && !text.isEmpty()) col = Text.last(text);
        setCursorOffset(buffer.offset(line, col));
    }

    /** Moves the cursor in Insert mode, where it may sit after the last character. */
    void setCursorInInsert(Pos pos) { setCursor(pos, true); }

    void setCursorOffset(int offset) {
        boolean was = busy;
        busy = true;
        try { area.setCaretPosition(Math.max(0, Math.min(offset, buffer.length()))); }
        finally { busy = was; }
    }

    /** A motion without an operator: moves, and updates the remembered column (curswant). */
    void moveTo(Target target) {
        setCursor(target.pos());
        switch (target.column()) {
            case RESET -> curswant = -1;
            case END -> curswant = Integer.MAX_VALUE;
            case KEEP -> { }
        }
    }

    /**
     * @return the column a vertical motion lands on in {@code line}, from the remembered column; in Visual mode it
     *     may be the end of the line (coladvance's one_more)
     */
    int wantedColumn(int line) {
        String text = buffer.line(line);
        boolean visual = mode.visual();
        if (curswant == Integer.MAX_VALUE) return visual ? text.length() : Text.last(text);
        int col = Text.columnAt(text, want(false), options.number("tabstop"));
        return col >= text.length() && !text.isEmpty() && !visual ? Text.last(text) : col;
    }

    /** @return the column Insert mode's Up and Down land on: the remembered column, or the end of a shorter line */
    int insertColumn(int line) {
        String text = buffer.line(line);
        if (curswant == Integer.MAX_VALUE) return text.length();
        return Text.columnAt(text, want(true), options.number("tabstop"));
    }

    /** @return whether the remembered column is beyond the cursor, as after {@code $} */
    boolean wantsPastEnd() {
        if (curswant == Integer.MAX_VALUE) return true;
        Pos p = cursor();
        return curswant > Text.virtcol(buffer.line(p.line), p.col, options.number("tabstop"));
    }

    /** The next vertical move takes its column from the cursor (Vim's w_set_curswant). */
    void forgetColumn() { curswant = -1; }

    /** @return the remembered display column, set from the cursor if forgotten; in Normal mode a tab counts at its end */
    private int want(boolean inserting) {
        if (curswant >= 0) return curswant;
        int ts = options.number("tabstop");
        Pos p = cursor();
        String text = buffer.line(p.line);
        int vcol = Text.virtcol(text, p.col, ts);
        if (!inserting && p.col < text.length() && text.charAt(p.col) == '\t') vcol += Text.width(text, p.col, vcol, ts) - 1;
        curswant = vcol;
        return vcol;
    }

    void beginUndoStep() {
        marks.newChange();
        Pos p = cursor();
        area.vimUndo().beginStep(p.line, p.col);
    }

    void endUndoStep() { area.vimUndo().endStep(); }

    /** {@code u}: undoes {@code count} steps, placing the cursor as Vim's u_undoredo does. */
    void undo(int count) { undoRedo(count, true); }

    /** {@code CTRL-R}. */
    void redo(int count) { undoRedo(count, false); }

    private void undoRedo(int count, boolean undo) {
        VimUndoManager manager = area.vimUndo();
        for (int n = 0; n < count; n++) {
            if (undo ? !manager.canUndo() : !manager.canRedo()) {
                if (n == 0) throw new VimFailure(undo ? "Already at oldest change" : "Already at newest change");
                break;
            }
            VimUndoManager.Step step = undo ? manager.nextUndo() : manager.nextRedo();
            int[] top = {Integer.MAX_VALUE};
            DocumentListener watch = new DocumentListener() {
                @Override public void insertUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, true)); }
                @Override public void removeUpdate(DocumentEvent event) { top[0] = Math.min(top[0], changedLine(event, false)); }
                @Override public void changedUpdate(DocumentEvent event) { }
            };
            buffer.document().addDocumentListener(watch);
            try {
                if (undo) manager.undo();
                else manager.redo();
            } finally { buffer.document().removeDocumentListener(watch); }
            int line = Math.min(top[0] == Integer.MAX_VALUE ? cursor().line : top[0], buffer.count() - 1);
            Pos cursor;
            if (step != null && step.cursorLine >= 0) {
                if (step.cursorLine + 1 == line && line > 0) line--;
                cursor = step.cursorLine == line ? new Pos(line, step.cursorCol) : new Pos(line, Text.firstNonBlank(buffer.line(line)));
            } else cursor = new Pos(line, Text.firstNonBlank(buffer.line(line)));
            setCursor(cursor);
            curswant = -1;
        }
    }

    /** @return the first line an undo event changed; text inserted at a line's end that starts with a break changes the next line */
    private int changedLine(DocumentEvent event, boolean inserted) {
        int line = buffer.lineOf(event.getOffset());
        if (inserted && event.getOffset() == buffer.end(line) && buffer.charAt(event.getOffset()) == '\n') return line + 1;
        return line;
    }

    /** The caret moved by the mouse or the Editor: in Normal mode it is kept off the end of the line. */
    private void caretMoved() {
        if (busy || mode != Mode.NORMAL || machine.pending()) return;
        // An outside selection (a mouse drag, the Find bar, Select All) becomes a Visual area (spec 3.1).
        if (area.getSelectionStart() != area.getSelectionEnd()) {
            javax.swing.SwingUtilities.invokeLater(this::selectionToVisual);
            return;
        }
        if (area.getSelectionStart() != area.getSelectionEnd()) return;
        Pos p = cursor();
        String text = buffer.line(p.line);
        if (!text.isEmpty() && p.col >= text.length()) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                if (area.vim().orElse(null) != this) return;
                if (!busy && mode == Mode.NORMAL && area.getSelectionStart() == area.getSelectionEnd()) {
                    busy = true;
                    try { setCursor(cursor()); }
                    finally { busy = false; }
                }
            });
        }
        curswant = -1;
    }

    /** The text area's own selection becomes a charwise Visual area, which Vim then draws. */
    private void selectionToVisual() {
        if (area.vim().orElse(null) != this || busy || mode != Mode.NORMAL) return;
        int start = area.getSelectionStart(), end = area.getSelectionEnd();
        if (start == end) return;
        int dot = area.getCaretPosition();
        int mark = dot == start ? end : start;
        busy = true;
        try {
            // Vim's area includes the character at each end; the text area's selection excludes the one at its end, so
            // that end moves back one character (onto the line's end when it was the line break).
            Pos anchor = selectionEnd(mark, dot);
            Pos head = selectionEnd(dot, mark);
            area.select(dot, dot);
            visualAnchor = anchor;
            mode = Mode.VISUAL;
            setCursor(head, true);
        } finally { busy = false; }
        look();
        fire();
    }

    /** @return one end of a selection as a Visual position: the end that is further in the text steps back a character */
    private Pos selectionEnd(int offset, int other) {
        if (offset <= other) return buffer.pos(offset);
        Pos p = buffer.pos(offset - 1);
        String line = buffer.line(p.line);
        if (p.col < line.length() && Character.isLowSurrogate(line.charAt(p.col)) && p.col > 0) p = new Pos(p.line, p.col - 1);
        return p;
    }

    /** The caret shape and input methods for the mode (spec 5.1 and 2.1). */
    private void look() {
        CaretStyle style = switch (mode()) {
            case INSERT -> CaretStyle.VERTICAL_LINE_STYLE;
            case REPLACE, OPERATOR_PENDING -> CaretStyle.UNDERLINE_STYLE;
            default -> CaretStyle.BLOCK_STYLE;
        };
        area.setCaretStyle(RTextArea.INSERT_MODE, style);
        area.enableInputMethods(mode.inserting());
    }

    private void syncArea() {
        area.setTabSize(options.number("tabstop"));
        area.setTabsEmulated(options.flag("expandtab"));
    }

    private void fire() {
        refreshHighlights();
        for (Runnable listener : List.copyOf(listeners)) listener.run();
    }

    private void textChanged() {
        textVersion++;
        if (highlightPending) return;
        highlightPending = true;
        javax.swing.SwingUtilities.invokeLater(() -> {
            highlightPending = false;
            if (area.vim().orElse(null) == this) refreshHighlights();
        });
    }

    /** Draws the Visual area, the 'hlsearch' matches and the incremental-search match. */
    void refreshHighlights() {
        List<int[]> ranges = new ArrayList<>();
        Search search = vim.search();
        if (options.flag("hlsearch") && search.pattern() != null && !search.highlightHidden()) {
            List<Object> key = List.of(search.pattern(), options.flag("ignorecase"), options.flag("smartcase"), options.string("iskeyword"));
            if (!key.equals(highlightedPattern) || highlightedVersion != textVersion) {
                List<int[]> found = new ArrayList<>();
                for (Search.Match match : search.all(this)) {
                    int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                    // An empty match is drawn one character wide, as Vim does.
                    found.add(new int[] { 1, start, end > start ? end : start + 1 });
                }
                searchMatches = found;
                highlightedPattern = key;
                highlightedVersion = textVersion;
            }
            ranges.addAll(searchMatches);
        }
        ranges.addAll(Highlights.visual(this));
        if (incsearch != null) ranges.add(new int[] { 0, incsearch[0], incsearch[1] });
        highlights.show(ranges);
    }

    /** @return the drawn highlights as {kind, start, end}, for tests */
    List<int[]> highlights() { return highlights.shown(); }

    /**
     * 'incsearch': while {@code /} or {@code ?} reads a pattern, the match it would find is drawn and scrolled to;
     * the cursor stays where it is.
     */
    void incsearch(char type, String line) {
        incsearch = null;
        if (options.flag("incsearch") && !line.isEmpty()) {
            Search.Match match = vim.search().preview(this, type, line);
            if (match != null) {
                int start = buffer.offset(match.start()), end = buffer.offset(match.end());
                incsearch = new int[] { start, Math.max(end, start + 1) };
                try {
                    var rect = area.modelToView2D(start);
                    if (rect != null) area.scrollRectToVisible(rect.getBounds());
                } catch (javax.swing.text.BadLocationException ignored) { }
            }
        }
        refreshHighlights();
    }

    /** The command line closed: the incremental-search match goes. */
    void incsearchDone() {
        incsearch = null;
        Pos p = cursor();
        try {
            var rect = area.modelToView2D(buffer.offset(p));
            if (rect != null) area.scrollRectToVisible(rect.getBounds());
        } catch (javax.swing.text.BadLocationException ignored) { }
    }

    /** @return while a command line is typed, its text with the {@code /} or {@code ?} in front; else empty */
    public java.util.Optional<String> commandLineText() {
        return commandLine.active() ? java.util.Optional.of(commandLine.type() + commandLine.text()) : java.util.Optional.empty();
    }

    /** @return the command line's cursor, as an index into {@link #commandLineText()} */
    public int commandLineCursor() { return commandLine.cursor() + 1; }

    /**
     * @param name the document's name for {@code <C-g>}
     * @param modified whether the document has unsaved changes
     */
    public void documentInfo(java.util.function.Supplier<String> name, java.util.function.BooleanSupplier modified) {
        documentName = name;
        documentModified = modified;
    }

    String documentName() { return documentName.get(); }

    boolean documentModified() { return documentModified.getAsBoolean(); }
}
```

In `plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java`, replace:

```java
    private EditorDefaults defaults() { return new EditorDefaults(tabSize, !session.tabs(), true); }
```

with:

```java
    private EditorDefaults defaults() { return new EditorDefaults(tabSize, !session.tabs(), true, session.wrap()); }
```

- [ ] **Step 5: Run the tests**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.ViewportSyncTest' --tests 'dev.jasper.editor.vim.VisualSearchUiTest' --tests 'dev.jasper.editor.vim.VimEditorTest' --tests 'dev.jasper.editor.vim.VimUndoManagerTest' --tests 'dev.jasper.editor.vim.BufferTest' --tests 'dev.jasper.editor.vim.OptionsTest' --tests 'dev.jasper.editor.vim.RegistersTest' --tests 'dev.jasper.editor.vim.KeywordsTest' --tests 'dev.jasper.editor.vim.TextTest' --tests 'dev.jasper.editor.vim.KeyInputTest' --tests 'dev.jasper.editor.vim.KeyTranslatorTest' --tests 'dev.jasper.editor.vim.regex.VimRegexTest' --tests 'dev.jasper.editor.vim.ui.VimBarTest' --tests 'dev.jasper.editor.ui.*'
```

Expected: 136 tests pass.

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: the 946 existing cases replay and pass; `everyCommandHasACase` fails, listing the new commands (`action <C-e>`, `motion H`, …). Task 2 adds their cases.

Run:

```bash
./gradlew :jasper-plugin-editor:compileJava --rerun 2>&1 | grep -i "warning\|error" || echo clean
```

Expected: `clean`: the Editor compiles without warnings (`-Xlint:all`).

Run:

```bash
./gradlew verifyPluginArchitecture -q
```

Expected: nothing is printed about the Editor (warnings from other plugins' sources may appear; they are not this plan's).

- [ ] **Step 6: Commit**

```bash
git branch --show-current   # must print claude/vim-v1c
git add plugins/editor/src/main/java/dev/jasper/editor/vim plugins/editor/src/main/java/dev/jasper/editor/ui/EditorTab.java plugins/editor/src/test/java/dev/jasper/editor/vim/ViewportSyncTest.java
git commit -m "Add the Vim view: scrolling, screen-line motions and fold commands

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 2: The corpus: windows and folds

**Files:**
- Replace: `plugins/editor/src/test/java/dev/jasper/editor/vim/{CorpusCase,CorpusFile,CorpusRunner,RecordCorpus}.java`
- Create: `plugins/editor/src/test/resources/vim/corpus/view.case`, `plugins/editor/src/test/resources/vim/corpus/folds.case`

**Interfaces:**
- Consumes: Task 1's `View` and `Folds`.
- Produces: `CorpusCase` gains `window` and `folds` (with `windowSize()` and `closedAtStart()`); `CorpusCase.Expected` gains `view` and `folds`. `CorpusRunner.javaFolds(VimTextArea, List<Integer>)` makes a text's code folds; `RecordCorpus.foldCommands` makes the same folds in Vim.

The replay sizes the view (Vim's default 80 by 23 when a case gives no window) and scrolls to the cursor before the first key, as Vim's main loop does. The expectations below were recorded from Vim 9.1; recording again must change nothing.

- [ ] **Step 1: Update the harness**

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusCase.java` with:

```java
package dev.jasper.editor.vim;

import java.util.List;
import java.util.Map;

/**
 * One case of the differential corpus (spec 11.1): a starting text, cursor and options, the keys typed, and what
 * Vim did.
 *
 * @param file the file it came from, for messages
 * @param name its title
 * @param divergent why Jasper deliberately differs, or null; a divergent case's expectation is written by hand
 * @param set {@code :set} arguments applied first, or empty
 * @param given registers set first
 * @param text the starting lines
 * @param line the cursor's line, 1-based
 * @param col the cursor's column in characters, 1-based
 * @param keys the keys, in Vim notation
 * @param registers the registers whose contents are recorded and compared
 * @param window the window's size as {@code columnsxrows} ({@code 20x5}), or null; with it the view is compared
 * @param folds with {@code java}, the text's code folds as RSyntaxTextArea's Java fold parser finds them, followed
 *     by the first lines of the folds that start closed ({@code java 2 7}); null for none. The fold state is compared
 * @param expected what Vim did, or null when the case has not been recorded
 */
record CorpusCase(String file, String name, String divergent, String set, Map<Character, Register> given, List<String> text,
                  int line, int col, String keys, List<Character> registers, String window, String folds, Expected expected) {
    /**
     * A register's content as the corpus writes it.
     *
     * @param type as getregtype() gives it, but in letters: {@code c} charwise, {@code l} linewise, or {@code b} and
     *     the width for a block ({@code b3})
     * @param text its text, as getreg() gives it
     */
    record Register(String type, String text) {
        /** @return the content a register holds for it */
        Registers.Content content() {
            if (type.startsWith("b")) {
                return new Registers.Content(java.util.List.of(text.split("\n", -1)), Registers.Type.BLOCK, Integer.parseInt(type.substring(1)) - 1);
            }
            return Registers.Content.of(text, type.equals("l") ? Registers.Type.LINE : Registers.Type.CHAR);
        }

        /** @return how the corpus writes a register's content */
        static Register of(Registers.Content content) {
            String type = switch (content.type()) {
                case CHAR -> "c";
                case LINE -> "l";
                case BLOCK -> "b" + (content.width() + 1);
            };
            return new Register(type, content.text());
        }
    }

    /**
     * The result.
     *
     * @param recordedBy the Vim that recorded it ({@code vim 9.1}), or {@code expected} for a divergent case
     * @param text the lines
     * @param line the cursor line, 1-based
     * @param col the cursor column in characters, 1-based
     * @param mode {@link Mode#id()}
     * @param visual in Visual mode, the other end of the area as {@code line,col} (1-based, in characters); else null
     * @param registers the compared registers; a missing value means empty
     * @param view for a case with a window, its first line and (without 'wrap') first column: {@code topline,leftcol}
     * @param folds for a case with folds, the closed folds as {@code first-last} line ranges, or {@code none}
     */
    record Expected(String recordedBy, List<String> text, int line, int col, String mode, String visual, Map<Character, Register> registers,
                    String view, String folds) { }

    CorpusCase withExpected(Expected value) {
        return new CorpusCase(file, name, divergent, set, given, text, line, col, keys, registers, window, folds, value);
    }

    /** @return the window's columns and rows, from {@link #window} */
    int[] windowSize() {
        String[] parts = window.split("x");
        return new int[] { Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()) };
    }

    /** @return the first lines of the folds that start closed, from {@link #folds} */
    List<Integer> closedAtStart() {
        List<Integer> out = new java.util.ArrayList<>();
        String[] parts = folds.trim().split("\\s+");
        for (int i = 1; i < parts.length; i++) out.add(Integer.parseInt(parts[i]));
        return out;
    }

    @Override public String toString() { return file + ": " + name; }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusFile.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads and writes corpus files (spec 11.1). Lines starting with {@code #} outside a text block are comments. Text
 * lines are indented two spaces; one trailing {@code $} is dropped, so a line ending in white space or {@code $} is
 * written with a {@code $} after it. Everything from a case's {@code ---} line on is its expectation; writing
 * replaces only that part.
 */
final class CorpusFile {
    private CorpusFile() { }

    static List<CorpusCase> read(Path file) throws IOException {
        return parse(file.getFileName().toString(), Files.readAllLines(file, StandardCharsets.UTF_8));
    }

    static List<CorpusCase> parse(String file, List<String> lines) {
        List<CorpusCase> cases = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String line = lines.get(i);
            if (line.isBlank() || line.startsWith("#")) {
                i++;
                continue;
            }
            if (!line.startsWith("=== ")) throw error(file, i, "expected \"=== name\"");
            String name = line.substring(4).trim();
            String divergent = null, set = "", keys = null, window = null, folds = null;
            Map<Character, CorpusCase.Register> given = new LinkedHashMap<>();
            List<String> text = null;
            int cursorLine = 1, cursorCol = 1;
            List<Character> registers = new ArrayList<>();
            CorpusCase.Expected expected = null;
            i++;
            while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
                String field = lines.get(i);
                if (field.isBlank() || field.startsWith("#")) {
                    i++;
                    continue;
                }
                if (field.startsWith("---")) {
                    String by = field.substring(3).trim();
                    Parsed parsed = expectation(file, lines, i + 1, by);
                    expected = parsed.expected;
                    i = parsed.next;
                    break;
                }
                if (field.equals("text:")) {
                    List<String> block = new ArrayList<>();
                    i = block(lines, i + 1, block);
                    text = block;
                    continue;
                }
                int colon = field.indexOf(':');
                if (colon < 0) throw error(file, i, "expected \"field: value\"");
                String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
                switch (key) {
                    case "divergent" -> divergent = value;
                    case "set" -> set = value;
                    case "window" -> window = value;
                    case "folds" -> folds = value;
                    case "cursor" -> {
                        String[] parts = value.split(",");
                        cursorLine = Integer.parseInt(parts[0].trim());
                        cursorCol = Integer.parseInt(parts[1].trim());
                    }
                    case "keys" -> keys = field.substring(colon + 2);
                    case "registers" -> {
                        for (String name1 : value.split("\\s+")) if (!name1.isEmpty()) registers.add(name1.charAt(0));
                    }
                    default -> {
                        if (key.startsWith("register ")) given.put(key.charAt(9), register(value));
                        else throw error(file, i, "unknown field " + key);
                    }
                }
                i++;
            }
            if (text == null || keys == null) throw error(file, i, "case \"" + name + "\" needs text: and keys:");
            cases.add(new CorpusCase(file, name, divergent, set, given, text, cursorLine, cursorCol, keys, registers, window, folds, expected));
        }
        return cases;
    }

    private record Parsed(CorpusCase.Expected expected, int next) { }

    private static Parsed expectation(String file, List<String> lines, int i, String by) {
        List<String> text = List.of();
        int line = 1, col = 1;
        String mode = "normal", visual = null, view = null, closedFolds = null;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        while (i < lines.size() && !lines.get(i).startsWith("=== ")) {
            String field = lines.get(i);
            if (field.isBlank() || field.startsWith("#")) {
                i++;
                continue;
            }
            if (field.equals("expect-text:")) {
                List<String> block = new ArrayList<>();
                i = block(lines, i + 1, block);
                text = block;
                continue;
            }
            int colon = field.indexOf(':', field.startsWith("expect-register ") ? 17 : 0);
            String key = field.substring(0, colon), value = field.substring(colon + 1).trim();
            switch (key) {
                case "expect-cursor" -> {
                    String[] parts = value.split(",");
                    line = Integer.parseInt(parts[0].trim());
                    col = Integer.parseInt(parts[1].trim());
                }
                case "expect-mode" -> mode = value;
                case "expect-visual" -> visual = value;
                case "expect-view" -> view = value;
                case "expect-folds" -> closedFolds = value;
                default -> {
                    if (!key.startsWith("expect-register ")) throw error(file, i, "unknown field " + key);
                    if (!value.equals("none")) registers.put(key.charAt(16), register(value));
                }
            }
            i++;
        }
        return new Parsed(new CorpusCase.Expected(by, text, line, col, mode, visual, registers, view, closedFolds), i);
    }

    private static CorpusCase.Register register(String value) {
        int space = value.indexOf(' ');
        return new CorpusCase.Register(value.substring(0, space), Json.unquote(value.substring(space + 1).trim()));
    }

    /** Reads indented text lines until a line that is not indented; returns the index after them. */
    private static int block(List<String> lines, int i, List<String> out) {
        while (i < lines.size() && lines.get(i).startsWith("  ")) {
            String content = lines.get(i).substring(2);
            if (content.endsWith("$")) content = content.substring(0, content.length() - 1);
            out.add(content);
            i++;
        }
        return i;
    }

    private static IllegalArgumentException error(String file, int line, String message) {
        return new IllegalArgumentException(file + ":" + (line + 1) + ": " + message);
    }

    /** Rewrites the file with each named case's expectation replaced by the lines given, keeping everything above it. */
    static void writeExpectations(Path file, Map<String, List<String>> byName) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        List<String> out = new ArrayList<>();
        String current = null;
        boolean skipping = false;
        for (int i = 0; i <= lines.size(); i++) {
            String line = i < lines.size() ? lines.get(i) : null;
            boolean boundary = line == null || line.startsWith("=== ");
            if (boundary && current != null && byName.containsKey(current)) {
                while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
                out.addAll(byName.get(current));
                out.add("");
            }
            if (line == null) break;
            if (boundary) {
                current = line.substring(4).trim();
                skipping = false;
                out.add(line);
                continue;
            }
            if (line.startsWith("---") && current != null && byName.containsKey(current)) skipping = true;
            if (!skipping) out.add(line);
        }
        while (!out.isEmpty() && out.getLast().isBlank()) out.removeLast();
        Files.write(file, out, StandardCharsets.UTF_8);
    }

    /** @return a case's expectation lines: its {@code ---} line, the text, cursor, mode and each compared register */
    static List<String> format(CorpusCase c, CorpusCase.Expected expected) {
        List<String> out = new ArrayList<>();
        out.add("--- " + expected.recordedBy());
        out.add("expect-text:");
        for (String line : expected.text()) out.add(textLine(line));
        out.add("expect-cursor: " + expected.line() + "," + expected.col());
        out.add("expect-mode: " + expected.mode());
        if (expected.visual() != null) out.add("expect-visual: " + expected.visual());
        if (expected.view() != null) out.add("expect-view: " + expected.view());
        if (expected.folds() != null) out.add("expect-folds: " + expected.folds());
        for (char name : c.registers()) {
            CorpusCase.Register register = expected.registers().get(name);
            out.add("expect-register " + name + ": " + (register == null ? "none" : register.type() + " " + Json.quote(register.text())));
        }
        return out;
    }

    static String textLine(String content) {
        boolean mark = content.isEmpty() || content.endsWith(" ") || content.endsWith("\t") || content.endsWith("$");
        return "  " + content + (mark ? "$" : "");
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/CorpusRunner.java` with:

```java
package dev.jasper.editor.vim;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Replays a corpus case on Jasper's engine (spec 11.1): a headless {@link VimTextArea} holding the text with a final
 * line break, Vim's own option defaults, and the keys fed as typed.
 */
final class CorpusRunner {
    private CorpusRunner() { }

    /** @return what the engine did, in the corpus's terms; runs on the event thread, as the Editor does */
    static CorpusCase.Expected run(CorpusCase c, Consumer<String> coverage) {
        var result = new java.util.concurrent.atomic.AtomicReference<CorpusCase.Expected>();
        try {
            javax.swing.SwingUtilities.invokeAndWait(() -> result.set(runHere(c, coverage)));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(interrupted);
        } catch (java.lang.reflect.InvocationTargetException failed) {
            if (failed.getCause() instanceof RuntimeException runtime) throw runtime;
            if (failed.getCause() instanceof Error error) throw error;
            throw new IllegalStateException(failed.getCause());
        }
        return result.get();
    }

    private static CorpusCase.Expected runHere(CorpusCase c, Consumer<String> coverage) {
        var vim = new Vim(VimHost.detached());
        vim.coverage(coverage);
        var area = new VimTextArea();
        area.setInsertPairedCharacters(false);
        area.setCloseCurlyBraces(false);
        area.setCloseMarkupTags(false);
        area.setText(String.join("\n", c.text()) + "\n");
        area.discardAllEdits();
        if (c.folds() != null) javaFolds(area, c.closedAtStart());
        var editor = new VimEditor(vim, area, EditorDefaults.VIM);
        // Vim's window when the recorder starts: 80 columns, 23 rows (24 lines, one for the command line).
        int[] size = c.window() != null ? c.windowSize() : new int[] { 80, 23 };
        editor.view().resize(size[1], size[0]);
        if (!c.set().isBlank()) editor.options().apply(c.set());
        for (var entry : c.given().entrySet()) {
            var register = entry.getValue();
            vim.registers().set(entry.getKey(), register.content());
        }
        String start = c.text().get(c.line() - 1);
        editor.setCursor(new Pos(c.line() - 1, Text.column(start, c.col() - 1)));
        // Vim scrolls to the cursor before it reads the first key.
        editor.view().updateTopline();
        for (KeyInput key : KeyInput.parse(c.keys())) editor.feed(key);
        Buffer buffer = editor.buffer();
        List<String> text = new ArrayList<>();
        for (int line = 0; line < buffer.count(); line++) text.add(buffer.line(line));
        if (buffer.length() == 0) text = List.of("");
        Pos cursor = editor.cursor();
        int col = Text.characters(buffer.line(cursor.line), cursor.col) + 1;
        Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
        for (char name : c.registers()) {
            vim.registers().get(name).ifPresent(content -> registers.put(name, CorpusCase.Register.of(content)));
        }
        String visual = null;
        Pos anchor = editor.visualAnchor();
        if (editor.mode().visual() && anchor != null)
            visual = (anchor.line + 1) + "," + (Text.characters(buffer.line(anchor.line), Math.min(anchor.col, buffer.line(anchor.line).length())) + 1);
        String view = c.window() == null ? null : editor.view().topline + "," + editor.view().leftcol;
        String folds = c.folds() == null ? null : closedFolds(editor);
        editor.close();
        return new CorpusCase.Expected("jasper", text, cursor.line + 1, col, editor.mode().id(), visual, registers, view, folds);
    }

    /** The text's code folds as RSyntaxTextArea's Java fold parser finds them, those starting on the given lines closed. */
    static void javaFolds(VimTextArea area, List<Integer> closed) {
        area.setSyntaxEditingStyle(org.fife.ui.rsyntaxtextarea.SyntaxConstants.SYNTAX_STYLE_JAVA);
        area.setCodeFoldingEnabled(true);
        area.getFoldManager().reparse();
        for (int line : closed) collapse(area.getFoldManager(), line - 1);
    }

    private static void collapse(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager, int startLine) {
        for (var fold : folds(manager)) if (fold.getStartLine() == startLine) fold.setCollapsed(true);
    }

    /** @return every fold, outer before inner */
    static List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds(org.fife.ui.rsyntaxtextarea.folding.FoldManager manager) {
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> out = new ArrayList<>();
        for (int i = 0; i < manager.getFoldCount(); i++) add(manager.getFold(i), out);
        return out;
    }

    private static void add(org.fife.ui.rsyntaxtextarea.folding.Fold fold, List<org.fife.ui.rsyntaxtextarea.folding.Fold> out) {
        out.add(fold);
        for (int i = 0; i < fold.getChildCount(); i++) add(fold.getChild(i), out);
    }

    /** @return the closed folds as Vim's foldclosed() reports them: {@code 2-5 8-9}, or {@code none} */
    static String closedFolds(VimEditor editor) {
        List<String> out = new ArrayList<>();
        int count = editor.buffer().count();
        for (int lnum = 1; lnum <= count; lnum++) {
            int[] fold = editor.folds().closed(lnum);
            if (fold != null && fold[0] == lnum) {
                out.add(fold[0] + "-" + fold[1]);
                lnum = fold[1];
            }
        }
        return out.isEmpty() ? "none" : String.join(" ", out);
    }

    /** @return the differences from the expectation, or empty */
    static String compare(CorpusCase.Expected expected, CorpusCase.Expected actual) {
        List<String> problems = new ArrayList<>();
        if (!expected.text().equals(actual.text())) problems.add("text " + actual.text() + " (Vim: " + expected.text() + ")");
        if (expected.line() != actual.line() || expected.col() != actual.col())
            problems.add("cursor " + actual.line() + "," + actual.col() + " (Vim: " + expected.line() + "," + expected.col() + ")");
        if (!expected.mode().equals(actual.mode())) problems.add("mode " + actual.mode() + " (Vim: " + expected.mode() + ")");
        if (!java.util.Objects.equals(expected.visual(), actual.visual())) problems.add("visual " + actual.visual() + " (Vim: " + expected.visual() + ")");
        if (!java.util.Objects.equals(expected.view(), actual.view())) problems.add("view " + actual.view() + " (Vim: " + expected.view() + ")");
        if (!java.util.Objects.equals(expected.folds(), actual.folds())) problems.add("folds " + actual.folds() + " (Vim: " + expected.folds() + ")");
        if (!expected.registers().equals(actual.registers())) problems.add("registers " + show(actual.registers()) + " (Vim: " + show(expected.registers()) + ")");
        return String.join("; ", problems);
    }

    private static String show(Map<Character, CorpusCase.Register> registers) {
        List<String> parts = new ArrayList<>();
        registers.forEach((name, register) -> parts.add(name + "=" + register.type() + Json.quote(register.text())));
        return "{" + String.join(", ", parts) + "}";
    }
}
```

Replace the whole of `plugins/editor/src/test/java/dev/jasper/editor/vim/RecordCorpus.java` with:

```java
package dev.jasper.editor.vim;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Records the corpus's expectations from real Vim (spec 11.1): {@code ./gradlew :jasper-plugin-editor:recordVimCorpus}.
 * Each case runs in its own {@code vim -u NONE -i NONE -N}, which types the keys with feedkeys() and writes the text,
 * cursor, mode and registers as JSON. Divergent cases are skipped. Not part of {@code check}.
 *
 * <p>Arguments: the corpus folder, the Vim binary, and an optional case-name filter (a substring).
 */
public final class RecordCorpus {
    private RecordCorpus() { }

    public static void main(String[] arguments) throws Exception {
        Path folder = Path.of(arguments[0]);
        String vim = arguments.length > 1 && !arguments[1].isBlank() ? arguments[1] : "vim";
        String filter = arguments.length > 2 ? arguments[2] : "";
        String version = version(vim);
        List<Path> files;
        try (Stream<Path> listing = Files.list(folder)) { files = listing.filter(p -> p.toString().endsWith(".case")).sorted().toList(); }
        ExecutorService pool = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors()));
        int recorded = 0;
        try {
            for (Path file : files) {
                List<CorpusCase> cases = CorpusFile.read(file);
                Map<String, Future<CorpusCase.Expected>> pending = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    if (c.divergent() != null || !c.name().contains(filter)) continue;
                    pending.put(c.name(), pool.submit(() -> record(vim, version, c)));
                }
                Map<String, List<String>> lines = new LinkedHashMap<>();
                for (CorpusCase c : cases) {
                    Future<CorpusCase.Expected> result = pending.get(c.name());
                    if (result == null) continue;
                    lines.put(c.name(), CorpusFile.format(c, result.get()));
                    recorded++;
                }
                if (!lines.isEmpty()) CorpusFile.writeExpectations(file, lines);
            }
        } finally { pool.shutdownNow(); }
        System.out.println("Recorded " + recorded + " cases with " + version + ".");
    }

    static String version(String vim) throws IOException, InterruptedException {
        Process process = new ProcessBuilder(vim, "--version").redirectErrorStream(true).start();
        String first = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).lines().findFirst().orElse("");
        process.waitFor();
        // "VIM - Vi IMproved 9.1 (2024 Jan 02, compiled ...)" -> "vim 9.1"
        String[] words = first.split("\\s+");
        return words.length > 4 ? "vim " + words[4] : "vim";
    }

    static CorpusCase.Expected record(String vim, String version, CorpusCase c) throws IOException, InterruptedException {
        Path folder = Files.createTempDirectory("jasper-vim-case");
        try {
            Path out = folder.resolve("out.json"), script = folder.resolve("case.vim");
            Files.writeString(script, script(c, out, folder.resolve("text.txt")), StandardCharsets.UTF_8);
            Process process = new ProcessBuilder(vim, "-u", "NONE", "-i", "NONE", "-N", "-n", "--not-a-term", "-S", script.toString())
                .redirectInput(ProcessBuilder.Redirect.from(Path.of(System.getProperty("os.name").startsWith("Windows") ? "NUL" : "/dev/null").toFile()))
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start();
            if (!process.waitFor(20, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                throw new IllegalStateException(c + ": Vim did not finish within 20 s (keys left it waiting for input?)");
            }
            if (!Files.exists(out)) throw new IllegalStateException(c + ": Vim wrote no result; check the keys");
            @SuppressWarnings("unchecked")
            Map<String, Object> result = (Map<String, Object>) Json.parse(Files.readString(out, StandardCharsets.UTF_8).trim());
            @SuppressWarnings("unchecked")
            List<String> text = (List<String>) result.get("text");
            Map<Character, CorpusCase.Register> registers = new LinkedHashMap<>();
            @SuppressWarnings("unchecked")
            Map<String, List<Object>> regs = (Map<String, List<Object>>) result.get("regs");
            for (var entry : regs.entrySet()) {
                String type = (String) entry.getValue().get(0);
                @SuppressWarnings("unchecked")
                List<String> lines = (List<String>) entry.getValue().get(1);
                if (type.isEmpty() || lines.isEmpty()) continue;
                String kind = type.equals("V") ? "l" : type.equals("v") ? "c" : "b" + type.substring(1);
                String joined = String.join("\n", lines) + (kind.equals("l") ? "\n" : "");
                registers.put(entry.getKey().charAt(0), new CorpusCase.Register(kind, joined));
            }
            @SuppressWarnings("unchecked")
            List<Long> visual = (List<Long>) result.get("visual");
            @SuppressWarnings("unchecked")
            List<Long> view = (List<Long>) result.get("view");
            @SuppressWarnings("unchecked")
            List<String> closed = (List<String>) result.get("folds");
            return new CorpusCase.Expected(version, text, ((Long) result.get("line")).intValue(), ((Long) result.get("col")).intValue(),
                mode((String) result.get("mode")), visual.isEmpty() ? null : visual.get(0) + "," + visual.get(1), registers,
                c.window() == null ? null : view.get(0) + "," + view.get(1),
                c.folds() == null ? null : closed.isEmpty() ? "none" : String.join(" ", closed));
        } finally {
            try (Stream<Path> files = Files.list(folder)) { for (Path p : files.toList()) Files.deleteIfExists(p); }
            Files.deleteIfExists(folder);
        }
    }

    static String mode(String vim) {
        if (vim.equals("n")) return "normal";
        if (vim.equals("i")) return "insert";
        if (vim.equals("R")) return "replace";
        if (vim.equals("niI") || vim.equals("niR")) return "insert-normal";
        if (vim.startsWith("no")) return "operator-pending";
        if (vim.equals("c")) return "command-line";
        if (vim.equals("v")) return "visual";
        if (vim.equals("V")) return "visual-line";
        if (vim.equals("\u0016")) return "visual-block";
        return vim;
    }

    /** @return a Vim script that sets the case up, types its keys and writes the result to {@code out} */
    static String script(CorpusCase c, Path out, Path file) {
        List<String> lines = new ArrayList<>();
        lines.add("set encoding=utf-8");
        lines.add("scriptencoding utf-8");
        lines.add("set nocompatible");
        lines.add("function! JasperRecord()");
        lines.add("  let g:jasper = {'text': getline(1, '$'), 'line': line('.'), 'col': charcol('.'), 'mode': mode(1), 'regs': {}, 'visual': []}");
        lines.add("  let g:jasper.view = [winsaveview().topline, winsaveview().leftcol]");
        lines.add("  let g:jasper.folds = []");
        lines.add("  let l = 1");
        lines.add("  while l <= line('$')");
        lines.add("    if foldclosed(l) == l");
        lines.add("      call add(g:jasper.folds, l . '-' . foldclosedend(l))");
        lines.add("      let l = foldclosedend(l)");
        lines.add("    endif");
        lines.add("    let l += 1");
        lines.add("  endwhile");
        lines.add("  if mode() =~# '^[vV\\x16]'");
        lines.add("    let g:jasper.visual = [line('v'), charcol('v')]");
        lines.add("  endif");
        lines.add("  for r in " + vimList(c.registers().stream().map(String::valueOf).toList()));
        lines.add("    let g:jasper.regs[r] = [getregtype(r), getreg(r, 1, 1)]");
        lines.add("  endfor");
        lines.add("endfunction");
        lines.add("for r in split('\"0123456789abcdefghijklmnopqrstuvwxyz-', '\\zs')");
        lines.add("  call setreg(r, [], 'c')");
        lines.add("endfor");
        if (c.window() != null) {
            int[] size = c.windowSize();
            lines.add("set columns=" + size[0] + " lines=" + (size[1] + 1));
        }
        if (!c.set().isBlank()) lines.add("set " + c.set());
        for (var entry : c.given().entrySet()) {
            CorpusCase.Register register = entry.getValue();
            String text = register.type().equals("l") && register.text().endsWith("\n") ? register.text().substring(0, register.text().length() - 1) : register.text();
            lines.add("call setreg(" + Json.quote(String.valueOf(entry.getKey())) + ", " + vimList(List.of(text.split("\n", -1))) + ", " + Json.quote(register.type()) + ")");
        }
        // Editing a file, rather than setline(), leaves no undo history for u to reach.
        lines.add("call writefile(" + vimList(c.text()) + ", " + Json.quote(file.toString()) + ")");
        lines.add("silent execute 'edit!' fnameescape(" + Json.quote(file.toString()) + ")");
        if (c.folds() != null) lines.addAll(foldCommands(c));
        lines.add("call setcursorcharpos(" + c.line() + ", " + c.col() + ")");
        lines.add("let g:jasper = {}");
        lines.add("call feedkeys(\"" + vimKeys(c.keys()) + "\\<Cmd>call JasperRecord()\\<CR>\", 'xt')");
        lines.add("call writefile([json_encode(g:jasper)], " + Json.quote(out.toString()) + ")");
        lines.add("qa!");
        return String.join("\n", lines) + "\n";
    }

    /**
     * The case's code folds as Vim manual folds: RSyntaxTextArea's Java fold parser finds them in the text, inner
     * folds are made first (a fold made over a closed one would take it whole), all are opened, and then the ones the
     * case lists are closed, innermost (last-starting) first.
     */
    static List<String> foldCommands(CorpusCase c) {
        List<String> out = new ArrayList<>();
        var area = new VimTextArea();
        area.setText(String.join("\n", c.text()) + "\n");
        CorpusRunner.javaFolds(area, List.of());
        List<org.fife.ui.rsyntaxtextarea.folding.Fold> folds = new ArrayList<>(CorpusRunner.folds(area.getFoldManager()));
        java.util.Collections.reverse(folds);
        for (var fold : folds) out.add((fold.getStartLine() + 1) + "," + (fold.getEndLine() + 1) + "fold");
        out.add("normal! zR");
        // Innermost first: closing a line's fold when an outer one is already closed would close nothing.
        List<Integer> closed = new ArrayList<>(c.closedAtStart());
        closed.sort(java.util.Comparator.reverseOrder());
        for (int line : closed) out.add(line + "foldclose");
        return out;
    }

    private static String vimList(List<String> items) {
        List<String> quoted = items.stream().map(Json::quote).toList();
        return "[" + String.join(", ", quoted) + "]";
    }

    /** @return the keys as the inside of a Vim double-quoted string: {@code <Esc>} becomes {@code \<Esc>} */
    static String vimKeys(String notation) {
        StringBuilder out = new StringBuilder();
        for (KeyInput key : KeyInput.parse(notation)) {
            if (key.isChar() && key.modifiers() == 0) {
                char c = key.key().charAt(0);
                if (c == '\\') out.append("\\\\");
                else if (c == '"') out.append("\\\"");
                else if (key.key().length() == 1 && c < 0x20) out.append(String.format("\\x%02x", (int) c));
                else out.append(key.key());
            } else out.append('\\').append(key.notation());
        }
        return out.toString();
    }
}
```

- [ ] **Step 2: Add the cases**

Create `plugins/editor/src/test/resources/vim/corpus/view.case`:

```text
# Scrolling, H M L and the screen-line motions, with the window's first line compared.

=== the window scrolls to the cursor
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: 12G
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 12,1
expect-mode: normal
expect-view: 10,0

=== the window scrolls to the cursor going up
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: 20G
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0

=== far jumps center the cursor
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: 30G
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 30,1
expect-mode: normal
expect-view: 28,0

=== G shows the end
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: G
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 36,0

=== gg shows the start
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 40,1
window: 20x5
keys: gg
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== j past the bottom scrolls one line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 5,1
window: 20x5
keys: j
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 6,1
expect-mode: normal
expect-view: 2,0

=== k past the top scrolls one line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: 16Gk
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 15,1
expect-mode: normal
expect-view: 14,0

=== C-e scrolls up a line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-e>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 2,1
expect-mode: normal
expect-view: 2,0

=== C-e moves the cursor off the top
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: 3<C-e>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: normal
expect-view: 4,0

=== C-e keeps the column
text:
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
cursor: 1,4
window: 20x5
keys: 3<C-e>
--- vim 9.1
expect-text:
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
  abcdef
expect-cursor: 4,4
expect-mode: normal
expect-view: 4,0

=== C-e at the end stops
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 40,1
window: 20x5
keys: <C-e>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 37,0

=== C-y scrolls down a line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: <C-y>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 17,0

=== C-y moves the cursor off the bottom
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: 3<C-y>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 19,1
expect-mode: normal
expect-view: 15,0

=== C-y at the top fails
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-y>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== C-d scrolls half a window
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 3,1
expect-mode: normal
expect-view: 3,0

=== C-d twice
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-d><C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 5,1
expect-mode: normal
expect-view: 5,0

=== C-d with a count sets scroll
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: 4<C-d><C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 9,1
expect-mode: normal
expect-view: 9,0

=== C-d at the end moves the cursor
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 38,1
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 36,0

=== C-d on the last line fails
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 40,1
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 36,0

=== C-u scrolls up half a window
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: <C-u>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 28,1
expect-mode: normal
expect-view: 26,0

=== C-u at the top moves the cursor
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 3,1
window: 20x5
keys: <C-u>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== C-d then C-u
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-d><C-u>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== C-d keeps the column with nostartofline
text:
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
cursor: 1,5
set: nostartofline
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
  abcdefgh
expect-cursor: 3,5
expect-mode: normal
expect-view: 3,0

=== C-d goes to the first non-blank
text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
cursor: 1,5
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
expect-cursor: 3,3
expect-mode: normal
expect-view: 3,0

=== C-f scrolls a page
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: normal
expect-view: 4,0

=== C-f twice
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-f><C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 7,1
expect-mode: normal
expect-view: 7,0

=== C-f with a count
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: 3<C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 10,1
expect-mode: normal
expect-view: 10,0

=== C-f at the end
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 35,1
window: 20x5
keys: <C-f><C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 40,0

=== C-b scrolls back a page
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: <C-b>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: normal
expect-view: 25,0

=== C-b at the top fails
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <C-b>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== PageDown
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <PageDown>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: normal
expect-view: 4,0

=== PageUp
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: <PageUp>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: normal
expect-view: 25,0

=== S-Down
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: <S-Down>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: normal
expect-view: 4,0

=== S-Up
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: <S-Up>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: normal
expect-view: 25,0

=== zt puts the line at the top
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: zt
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 20,0

=== zt with a count
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: 10zt
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 10,1
expect-mode: normal
expect-view: 10,0

=== z CR goes to the first non-blank
text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
cursor: 20,1
window: 20x5
keys: z<CR>
--- vim 9.1
expect-text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
expect-cursor: 20,3
expect-mode: normal
expect-view: 20,0

=== zz centers the line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: zz
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0

=== zz near the start
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 2,1
window: 20x5
keys: zz
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0

=== zz near the end
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 39,1
window: 20x5
keys: zz
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 39,1
expect-mode: normal
expect-view: 37,0

=== z dot
text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
cursor: 20,1
window: 20x5
keys: z.
--- vim 9.1
expect-text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
expect-cursor: 20,3
expect-mode: normal
expect-view: 18,0

=== zb puts the line at the bottom
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: zb
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 16,0

=== zb near the start
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 2,1
window: 20x5
keys: zb
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0

=== z minus
text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
cursor: 20,1
window: 20x5
keys: z-
--- vim 9.1
expect-text:
    line 1
    line 2
    line 3
    line 4
    line 5
    line 6
    line 7
    line 8
    line 9
    line 10
    line 11
    line 12
    line 13
    line 14
    line 15
    line 16
    line 17
    line 18
    line 19
    line 20
    line 21
    line 22
    line 23
    line 24
    line 25
    line 26
    line 27
    line 28
    line 29
    line 30
    line 31
    line 32
    line 33
    line 34
    line 35
    line 36
    line 37
    line 38
    line 39
    line 40
expect-cursor: 20,3
expect-mode: normal
expect-view: 16,0

=== z plus
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 10,1
window: 20x5
keys: z+
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 13,1
expect-mode: normal
expect-view: 13,0

=== z caret
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: z^
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 17,1
expect-mode: normal
expect-view: 13,0

=== H goes to the top line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,3
window: 20x5
keys: H
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 18,1
expect-mode: normal
expect-view: 18,0

=== H with a count
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: 3H
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0

=== L goes to the bottom line
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: L
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 22,1
expect-mode: normal
expect-view: 18,0

=== L with a count
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: 2L
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 21,1
expect-mode: normal
expect-view: 18,0

=== M goes to the middle
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: M
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0

=== M on a short text
text:
  a
  b
  c
cursor: 1,1
window: 20x5
keys: M
--- vim 9.1
expect-text:
  a
  b
  c
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0

=== H is a jump
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: H``
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0

=== d L deletes to the bottom
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: dL
registers: "
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 18,0
expect-register ": l "line 20\nline 21\nline 22\n"

=== d H deletes to the top
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: dH
registers: "
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 18,1
expect-mode: normal
expect-view: 18,0
expect-register ": l "line 18\nline 19\nline 20\n"

=== scrolloff keeps lines around the cursor
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
set: scrolloff=1
window: 20x5
keys: 12G
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 12,1
expect-mode: normal
expect-view: 10,0

=== scrolloff with j
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 3,1
set: scrolloff=1
window: 20x5
keys: jj
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 5,1
expect-mode: normal
expect-view: 2,0

=== scrolloff with H
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
set: scrolloff=1
window: 20x5
keys: H
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 19,1
expect-mode: normal
expect-view: 18,0

=== scrolloff with C-e
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
set: scrolloff=1
window: 20x5
keys: <C-e><C-e>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 21,1
expect-mode: normal
expect-view: 20,0

=== scrolloff with C-y
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
set: scrolloff=1
window: 20x5
keys: <C-y>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: normal
expect-view: 17,0

=== scrolloff with C-d
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
set: scrolloff=2
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 5,1
expect-mode: normal
expect-view: 3,0

=== scrolloff with C-f
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
set: scrolloff=1
window: 20x5
keys: <C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 5,1
expect-mode: normal
expect-view: 4,0

=== scrolloff larger than half
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
set: scrolloff=10
window: 20x5
keys: j
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 21,1
expect-mode: normal
expect-view: 19,0

=== wrapped lines take more rows
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 1,1
window: 20x5
keys: 5G
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 5,1
expect-mode: normal
expect-view: 4,0

=== wrapped lines with C-e
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 1,1
window: 20x5
keys: <C-e><C-e>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,1
expect-mode: normal
expect-view: 3,0

=== wrapped lines with C-d
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 1,1
window: 20x5
keys: <C-d>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,1
expect-mode: normal
expect-view: 2,0

=== wrapped lines with C-f
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 1,1
window: 20x5
keys: <C-f>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,1
expect-mode: normal
expect-view: 3,0

=== wrapped lines with zt
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 5,1
window: 20x5
keys: zt
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 5,1
expect-mode: normal
expect-view: 5,0

=== wrapped lines with zb
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 5,1
window: 20x5
keys: zb
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 5,1
expect-mode: normal
expect-view: 4,0

=== wrapped lines with L
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 1,1
window: 20x5
keys: L
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0

=== a line taller than the window
text:
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  b
cursor: 1,1
window: 20x5
keys: j
--- vim 9.1
expect-text:
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  b
expect-cursor: 2,1
expect-mode: normal
expect-view: 2,0

=== gj moves down a screen line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: gj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,23
expect-mode: normal
expect-view: 1,0

=== gj twice
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: gjgj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,43
expect-mode: normal
expect-view: 1,0

=== gj to the next line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,43
window: 20x5
keys: gj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,3
expect-mode: normal
expect-view: 2,0

=== gk moves up a screen line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,43
window: 20x5
keys: gk
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,23
expect-mode: normal
expect-view: 1,0

=== gk to the previous line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 3,2
window: 20x5
keys: gk
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,42
expect-mode: normal
expect-view: 2,0

=== gj with a count
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,1
window: 20x5
keys: 4gj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,21
expect-mode: normal
expect-view: 2,0

=== gj on the last line fails
text:
  x
  y
cursor: 2,1
window: 20x5
keys: gj
--- vim 9.1
expect-text:
  x
  y
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0

=== gj without wrap is j
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
set: nowrap
window: 20x5
keys: gj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,3
expect-mode: normal
expect-view: 1,0

=== gj after dollar sticks to the end
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,1
window: 20x5
keys: $gj
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 3,20
expect-mode: normal
expect-view: 2,0

=== d gj
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: dgj
registers: "
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,3
expect-mode: normal
expect-view: 1,0
expect-register ": c "aaaaaaaaaaaaaaaaaaaa"

=== g0 goes to the screen line start
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,30
window: 20x5
keys: g0
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,21
expect-mode: normal
expect-view: 1,0

=== g Down moves down a screen line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: g<Down>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,23
expect-mode: normal
expect-view: 1,0

=== g Up moves up a screen line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,43
window: 20x5
keys: g<Up>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,23
expect-mode: normal
expect-view: 1,0

=== g Home goes to the screen line start
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,30
window: 20x5
keys: g<Home>
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,21
expect-mode: normal
expect-view: 1,0

=== g End goes to the last non-blank of the screen line
text:
    aaaaaaaaaaaaaaaa  $
  b
cursor: 1,1
window: 20x5
keys: g<End>
--- vim 9.1
expect-text:
    aaaaaaaaaaaaaaaa  $
  b
expect-cursor: 1,18
expect-mode: normal
expect-view: 1,0

=== g caret
text:
     aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
cursor: 1,30
window: 20x5
keys: g^
--- vim 9.1
expect-text:
     aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
expect-cursor: 1,21
expect-mode: normal
expect-view: 1,0

=== gm goes to the middle
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,30
window: 20x5
keys: gm
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,31
expect-mode: normal
expect-view: 1,0

=== g dollar goes to the screen line end
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: g$
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,20
expect-mode: normal
expect-view: 1,0

=== g dollar on the second screen line
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,25
window: 20x5
keys: g$
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,40
expect-mode: normal
expect-view: 1,0

=== g dollar with a count
text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
cursor: 2,3
window: 20x5
keys: 2g$
--- vim 9.1
expect-text:
  short
  aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa
  bbbbbbbbbbbbbbbbbbbbbbbbb
  x
  cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc
  end
  l7
  l8
  l9
  l10
  l11
  l12
  l13
  l14
  l15
  l16
  l17
  l18
  l19
  l20
  l21
  l22
  l23
  l24
  l25
  l26
  l27
  l28
  l29
  l30
expect-cursor: 2,40
expect-mode: normal
expect-view: 1,0

=== g0 without wrap
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,50
set: nowrap
window: 20x5
keys: g0
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,40
expect-mode: normal
expect-view: 1,39

=== g dollar without wrap
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,5
set: nowrap
window: 20x5
keys: g$
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,20
expect-mode: normal
expect-view: 1,0

=== nowrap scrolls sideways
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,1
set: nowrap
window: 20x5
keys: 50|
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,50
expect-mode: normal
expect-view: 1,39

=== nowrap scrolls back
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,1
set: nowrap
window: 20x5
keys: 50|0
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== nowrap with sidescroll
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,1
set: nowrap sidescroll=1
window: 20x5
keys: 25|
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,25
expect-mode: normal
expect-view: 1,5

=== nowrap with sidescrolloff
text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
cursor: 1,1
set: nowrap sidescrolloff=3
window: 20x5
keys: 18|
--- vim 9.1
expect-text:
  xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
expect-cursor: 1,18
expect-mode: normal
expect-view: 1,7

=== Visual C-d extends
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: v<C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 3,1
expect-mode: visual
expect-visual: 1,1
expect-view: 3,0

=== Visual C-f extends
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: V<C-f>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: visual-line
expect-visual: 1,1
expect-view: 4,0

=== Visual C-e
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: v3<C-e>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: visual
expect-visual: 1,1
expect-view: 4,0

=== Visual C-y
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: v2<C-y>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 20,1
expect-mode: visual
expect-visual: 20,1
expect-view: 16,0

=== Visual C-u
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: v<C-u>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 28,1
expect-mode: visual
expect-visual: 30,1
expect-view: 26,0

=== Visual C-b
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: v<C-b>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: visual
expect-visual: 30,1
expect-view: 25,0

=== Visual PageDown
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: v<PageDown>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: visual
expect-visual: 1,1
expect-view: 4,0

=== Visual PageUp
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: v<PageUp>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: visual
expect-visual: 30,1
expect-view: 25,0

=== Visual S-Down
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: v<S-Down>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 4,1
expect-mode: visual
expect-visual: 1,1
expect-view: 4,0

=== Visual S-Up
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 30,1
window: 20x5
keys: v<S-Up>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 29,1
expect-mode: visual
expect-visual: 30,1
expect-view: 25,0

=== Visual H
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 20,1
window: 20x5
keys: vH
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 18,1
expect-mode: visual
expect-visual: 20,1
expect-view: 18,0

=== C-d after an operator fails
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: d<C-d>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 1,1
expect-mode: normal
expect-view: 1,0

=== insert keeps the cursor shown
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 5,1
window: 20x5
keys: ox<Esc>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  x
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 6,1
expect-mode: normal
expect-view: 2,0

=== dd at the bottom
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 40,1
window: 20x5
keys: dd
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
expect-cursor: 39,1
expect-mode: normal
expect-view: 36,0

=== u scrolls to the change
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: Gddgg<C-e>u
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 40,1
expect-mode: normal
expect-view: 36,0

=== search scrolls
text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
cursor: 1,1
window: 20x5
keys: /line 25<CR>
--- vim 9.1
expect-text:
  line 1
  line 2
  line 3
  line 4
  line 5
  line 6
  line 7
  line 8
  line 9
  line 10
  line 11
  line 12
  line 13
  line 14
  line 15
  line 16
  line 17
  line 18
  line 19
  line 20
  line 21
  line 22
  line 23
  line 24
  line 25
  line 26
  line 27
  line 28
  line 29
  line 30
  line 31
  line 32
  line 33
  line 34
  line 35
  line 36
  line 37
  line 38
  line 39
  line 40
expect-cursor: 25,1
expect-mode: normal
expect-view: 23,0
```

Create `plugins/editor/src/test/resources/vim/corpus/folds.case`:

```text
# The fold commands on the text's code folds, and closed folds counted as one line.

=== folds start open
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java
keys: 
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,1
expect-mode: normal
expect-folds: none

=== zc closes the fold at the cursor
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zc
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 2-5

=== zc twice closes the outer fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zczc
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 1-9

=== zc with a count
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: 2zc
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 1-9

=== zo opens a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: zo
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zo on a nested closed fold opens one level
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 1 2
keys: zo
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 2-5

=== zo with a count
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 1 2
keys: 2zo
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zO opens every level
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 1 2
keys: zO
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zC closes every level
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zC
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 1-9

=== za closes an open fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: za
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 2-5

=== za opens a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: za
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zA on an open fold closes every level
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zA
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: 1-9

=== zA on a closed fold opens every level
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 1 2
keys: zA
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zv opens the folds at the cursor
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 1 2
keys: zv
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,1
expect-mode: normal
expect-folds: none

=== zR opens every fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 1 2 6
keys: zR
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,1
expect-mode: normal
expect-folds: none

=== zM closes every fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
folds: java
keys: zM
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 10,1
expect-mode: normal
expect-folds: 1-9

=== zo without a fold fails
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
folds: java
keys: zo
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 10,1
expect-mode: normal
expect-folds: none

=== zc without a fold fails
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
folds: java
keys: zc
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 10,1
expect-mode: normal
expect-folds: none

=== j skips a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: jj
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,1
expect-mode: normal
expect-folds: 2-5

=== j onto a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: j
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: 2-5

=== k skips a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 6,1
folds: java 2
keys: k
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: 2-5

=== k from a line inside a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java 2
keys: k
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,1
expect-mode: normal
expect-folds: 2-5

=== j from inside a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java 2
keys: j
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,1
expect-mode: normal
expect-folds: 2-5

=== j with a count over folds
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2 6
keys: 3j
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 9,1
expect-mode: normal
expect-folds: 2-5 6-8

=== G into a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: 4G
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 4,5
expect-mode: normal
expect-folds: 2-5

=== dd on a closed fold deletes it whole
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: dd
registers: " 0 1 -
--- vim 9.1
expect-text:
  class A {
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,3
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    one();\n    two();\n  }\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    one();\n    two();\n  }\n"
expect-register -: none

=== dj over a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: jdj
registers: " 0 1 -
--- vim 9.1
expect-text:
  class A {
      three();
    }
  }
  after
expect-cursor: 2,5
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register -: none

=== yy on a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: yy
registers: " 0 1 -
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: 2-5
expect-register ": l "  void f() {\n    one();\n    two();\n  }\n"
expect-register 0: l "  void f() {\n    one();\n    two();\n  }\n"
expect-register 1: none
expect-register -: none

=== 2dd with a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 2,1
folds: java 2
keys: 2dd
registers: " 0 1 -
--- vim 9.1
expect-text:
  class A {
      three();
    }
  }
  after
expect-cursor: 2,5
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register -: none

=== dk onto a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 6,1
folds: java 2
keys: dk
registers: " 0 1 -
--- vim 9.1
expect-text:
  class A {
      three();
    }
  }
  after
expect-cursor: 2,5
expect-mode: normal
expect-folds: none
expect-register ": l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register 0: none
expect-register 1: l "  void f() {\n    one();\n    two();\n  }\n  void g() {\n"
expect-register -: none

=== x in a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: x
--- vim 9.1
expect-text:
  class A {
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,3
expect-mode: normal
expect-folds: none

=== l opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: l
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,2
expect-mode: normal
expect-folds: none

=== w opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: w
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,3
expect-mode: normal
expect-folds: 2-5

=== dollar opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java 2
keys: $
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 3,10
expect-mode: normal
expect-folds: none

=== search opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: /two<CR>
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 4,5
expect-mode: normal
expect-folds: none

=== n opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: /two<CR>ggn
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 4,5
expect-mode: normal
expect-folds: none

=== percent into a fold opens it
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 9,1
folds: java 2
keys: %
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,9
expect-mode: normal
expect-folds: 2-5

=== mark jump opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java 2
keys: magg'a
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 4,5
expect-mode: normal
expect-folds: none

=== G does not open a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: 4G
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 4,5
expect-mode: normal
expect-folds: 2-5

=== brace motion opens a fold
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
folds: java 2
keys: }
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 13,1
expect-mode: normal
expect-folds: 2-8

=== u opens a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java 2
keys: xggu
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: none

=== zj goes to the next fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java
keys: zj
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: none

=== zj from inside a fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zj
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,1
expect-mode: normal
expect-folds: none

=== zj with a count
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
folds: java
keys: 2zj
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 4,1
expect-mode: normal
expect-folds: none

=== zj past the last fold fails
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
folds: java
keys: zj
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 10,1
expect-mode: normal
expect-folds: none

=== zk goes to the previous fold end
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 7,1
folds: java
keys: zk
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 5,1
expect-mode: normal
expect-folds: none

=== zk before the first fold fails
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
folds: java
keys: zk
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 1,1
expect-mode: normal
expect-folds: none

=== bracket z goes to the fold start
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java
keys: [z
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: none

=== close bracket z goes to the fold end
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 4,1
folds: java
keys: ]z
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 5,1
expect-mode: normal
expect-folds: none

=== bracket z at the start goes to the outer fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 2,1
folds: java
keys: [z
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,1
expect-mode: normal
expect-folds: none

=== bracket z outside a fold fails
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
folds: java
keys: [z
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 10,1
expect-mode: normal
expect-folds: none

=== d zj
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java
keys: dzj
registers: " 0 1 -
--- vim 9.1
expect-text:
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,3
expect-mode: normal
expect-folds: none
expect-register ": l "class A {\n"
expect-register 0: none
expect-register 1: l "class A {\n"
expect-register -: none

=== C-e over a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
window: 20x4
folds: java 2
keys: <C-e><C-e>
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,1
expect-mode: normal
expect-view: 6,0
expect-folds: 2-5

=== C-y over a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 10,1
window: 20x4
folds: java 2
keys: <C-y><C-y>
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 8,1
expect-mode: normal
expect-view: 2,0
expect-folds: 2-5

=== C-d with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
window: 20x4
folds: java 2 10
keys: <C-d>
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 9,1
expect-mode: normal
expect-view: 2,0
expect-folds: 2-8 10-12

=== C-f with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
window: 20x4
folds: java 2 10
keys: <C-f>
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 10,1
expect-mode: normal
expect-view: 10,0
expect-folds: 2-8 10-12

=== H with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 13,1
window: 20x4
folds: java 2
keys: 2H
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 11,3
expect-mode: normal
expect-view: 10,0
expect-folds: 2-8

=== L with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
window: 20x4
folds: java 2
keys: L
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 10,1
expect-mode: normal
expect-view: 1,0
expect-folds: 2-8

=== M with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
window: 20x4
folds: java 2
keys: M
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 2,1
expect-mode: normal
expect-view: 1,0
expect-folds: 2-8

=== zt with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 10,1
window: 20x4
folds: java 2
keys: zt
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 10,1
expect-mode: normal
expect-view: 10,0
expect-folds: 2-8

=== zb with folds
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 12,1
window: 20x4
folds: java 2
keys: zb
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 12,1
expect-mode: normal
expect-view: 9,0
expect-folds: 2-8

=== the window scrolls past a closed fold
text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
cursor: 1,1
window: 20x3
folds: java 2
keys: G
--- vim 9.1
expect-text:
  x
  a {
    b
    c {
      d
    }
    e
  }
  y
  f {
    g
  }
  z
expect-cursor: 13,1
expect-mode: normal
expect-view: 11,0
expect-folds: 2-8

=== gj over a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: gjgj
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 6,1
expect-mode: normal
expect-folds: 2-5

=== gk over a closed fold
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 6,1
folds: java 2
keys: gk
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: 2-5

=== V over a closed fold then d
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 1,1
folds: java 2
keys: Vjd
registers: " 0 1 -
--- vim 9.1
expect-text:
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 1,5
expect-mode: normal
expect-folds: 1-3
expect-register ": l "class A {\n  void f() {\n"
expect-register 0: none
expect-register 1: l "class A {\n  void f() {\n"
expect-register -: none

=== zc then undo
text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
cursor: 3,1
folds: java
keys: zcxu
--- vim 9.1
expect-text:
  class A {
    void f() {
      one();
      two();
    }
    void g() {
      three();
    }
  }
  after
expect-cursor: 2,1
expect-mode: normal
expect-folds: none
```

- [ ] **Step 3: Run the corpus**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests 'dev.jasper.editor.vim.VimCorpusTest'
```

Expected: 1127 tests pass: every case replays as Vim recorded it, and every command has a case. (`VimDocsTest` fails until Task 3 regenerates the reference.)

- [ ] **Step 4: Check the recording (needs `vim` on `PATH`)**

Run:

```bash
./gradlew :jasper-plugin-editor:recordVimCorpus && git status --short plugins/editor/src/test/resources/vim/corpus
```

Expected: the task prints `Recorded 1122 cases with vim 9.1.` and `git status` lists only the two new files (`??`), unchanged from what you created: Vim agrees with every expectation.

- [ ] **Step 5: Commit**

```bash
git branch --show-current   # must print claude/vim-v1c
git add plugins/editor/src/test/java/dev/jasper/editor/vim plugins/editor/src/test/resources/vim/corpus
git commit -m "Record scrolling, screen-line and fold cases from Vim

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```

### Task 3: Documentation, the spec amendments and the final check

**Files:**
- Modify: `docs/vim-divergences.md`, `docs/editor.md`, `docs/editor-verification.md`, `docs/STATUS.md`, `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`
- Regenerate: `docs/editor-vim-reference.md`

- [ ] **Step 1: List the differences**

In `docs/vim-divergences.md`, replace:

```markdown
- **`go` counts characters.** Vim counts bytes.
```

with:

```markdown
- **`go` counts characters.** Vim counts bytes.
- **Folds are the Editor's code folds.** There are no manual folds (`zf`, `zd`, `zE`); `zo`, `zc` and the other fold
  commands act on the folds the Editor finds in the code. A closed fold shows its first line with the Editor's fold
  marker, where Vim shows a one-line summary such as `+--  4 lines: void f() {`.
- **The window is the text area's.** Its height and width are the lines and characters of the Editor's font that the
  text area shows. Vim mode wraps long lines at any character, as Vim does ('linebreak' is not supported); with a
  font whose characters differ in width, a screen line can hold a different number of characters than Vim's would.
```

- [ ] **Step 2: Describe the view in the Editor guide**

In `docs/editor.md`, replace:

```markdown
- Not yet: scrolling commands (`<C-d>`, `zz`), `H`, `M`, `L` and the other screen-line motions, folds, the `:`
  command line, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in later releases.
  An uppercase mark (`mA`) works within its own tab.
```

with:

```markdown
- Scrolling (`<C-e>`, `<C-d>`, `<C-f>`, `zt`, `zz`), `H`, `M`, `L` and the screen-line motions (`gj`, `g0`, `g$`)
  work on a window as many lines high and characters wide as the text area shows. Scrolling with the mouse or the
  scroll bar keeps the cursor inside it, as in Vim. With Vim on, long lines wrap at any character, as Vim wraps them.
- The fold commands (`zo`, `zc`, `za`, `zR`, `zM`, `zj`, `[z`) work on the Editor's code folds. A closed fold counts as
  one line for `j`, `k` and scrolling, an operator takes it whole, and the motions Vim's 'foldopen' names (`l`, `w`,
  searches, marks, `%`, `{`, `u`) open the fold they land in.
- Not yet: the `:` command line, macros, a vimrc, and emulated plugins such as surround and commentary. They follow in
  later releases. An uppercase mark (`mA`) works within its own tab.
```

- [ ] **Step 3: Add the GUI checks for the user**

Append to `docs/editor-verification.md`:

```markdown

## Vim mode (plan V1c)

- [ ] 41. `<C-d>`, `<C-u>`, `<C-f>` and `<C-b>` scroll by half or whole windows and the cursor follows; `<C-e>` and `<C-y>` scroll one line, the cursor staying put until it would leave the window.
- [ ] 42. `zt`, `zz` and `zb` put the cursor line at the top, middle and bottom of the window; after resizing the window, `zz` still centres.
- [ ] 43. `H`, `M` and `L` go to the top, middle and bottom lines on screen; `3H` to the third.
- [ ] 44. With Wrap on, a long line wraps at any character; `gj`/`gk` move by screen line and `g0`/`g$` go to the start and end of the screen line.
- [ ] 45. Scrolling with the mouse wheel or the scroll bar moves the view; the caret moves into the window when it was scrolled out, and the next `j` does not jump back.
- [ ] 46. In a Java file, `zc` inside a method folds it (the fold marker in the gutter shows it), `j` steps over the closed fold, `zo` opens it; `zM` folds everything and `zR` opens everything; clicking the gutter's fold markers still works.
- [ ] 47. With a method folded, `dd` on it deletes the whole method, `u` brings it back, and `/` for text inside it opens the fold.
```

- [ ] **Step 4: Record the state**

In `docs/STATUS.md`, replace:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V1b** (branch `claude/vim-v1b`, plan
```

with:

```markdown
## Current state — 2026-09-28

- **Editor Vim mode, plan V1c** (branch `claude/vim-v1c`, plan
  `docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1c.md`, written 2026-09-28): the view — scrolling
  (`<C-e> <C-y> <C-d> <C-u> <C-f> <C-b>`, page keys, `zt z<CR> zz z. zb z- z+ z^`), `H M L`, the screen-line motions
  (`gj gk g0 g^ gm g$`), 'scrolloff', 'sidescroll' and 'sidescrolloff', and the fold commands on the Editor's code
  folds (`zo zO zc zC za zA zv zR zM zj zk [z ]z`) with closed folds counted as one line and 'foldopen'. A port of
  Vim's move.c (`View`) kept in step with the text area's viewport (`ViewportSync`). Every file in the plan was
  compiled and run against Vim 9.1 before the plan was written: 1,125 corpus cases (1,122 recorded, 3 divergent).
  - Not done: executing the plan; GUI checks 41–47 of the [checklist](editor-verification.md) (user-run); plans V2,
    V3 and V4.
- **Editor Vim mode, plan V1b** (branch `claude/vim-v1b`, plan
```

- [ ] **Step 5: Amend the spec**

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
amends sections 3.5, 3.6, 3.8, 5.2, 5.3, 11.1 and 13, as marked **Amended (V1b)**.
```

with:

```markdown
amends sections 3.5, 3.6, 3.8, 5.2, 5.3, 11.1 and 13, as marked **Amended (V1b)**. Plan V1c (`docs/superpowers/plans/2026-09-28-jasper-editor-vim-v1c.md`) amends sections 3.1, 4.1 and 11.1, as marked **Amended (V1c)**.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- `j`/`k` move by buffer lines; `gj`/`gk`, `g0`, `g^`, `g$`, `gm` use screen lines when wrap is on.
```

with:

```markdown
- `j`/`k` move by buffer lines; `gj`/`gk`, `g0`, `g^`, `g$`, `gm` use screen lines when wrap is on. **Amended (V1c):** the window is Vim's model of the text area (its height and width in lines and characters of the Editor font), kept in step with the scroll pane; while Vim is on the text area wraps at any character, as Vim does, and the cursor may be inside a closed fold.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Scrolling: `<C-e> <C-y> <C-d> <C-u> <C-f> <C-b> zt zz zb z<CR> z. z-`, `scrolloff` respected.
```

with:

```markdown
- Scrolling: `<C-e> <C-y> <C-d> <C-u> <C-f> <C-b> zt zz zb z<CR> z. z-`, `scrolloff` respected. **Amended (V1c):** also `z+ z^ <PageUp> <PageDown> <S-Up> <S-Down>`, the scroll keys in Visual mode, `g<Down> g<Up> g<Home> g<End>`; 'scrolljump', 'sidescroll' and 'sidescrolloff' are respected.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
- Folds: `zo zc za zO zC zA zR zM zv zj zk [z ]z` on the tab's code folds.
```

with:

```markdown
- Folds: `zo zc za zO zC zA zR zM zv zj zk [z ]z` on the tab's code folds. **Amended (V1c):** a code fold covers its first to last line; a closed fold counts as one line for vertical motions and scrolling, an operator takes it whole, word motions, `{`, `}` and searches start past it, and the motions Vim's default 'foldopen' names open it.
```

In `docs/superpowers/specs/2026-09-27-jasper-editor-vim-design.md`, replace:

```markdown
a blockwise register's type is `b` and its width (`b3`, `getregtype()` in letters); the mode `command-line` is Vim's `c`.
```

with:

```markdown
a blockwise register's type is `b` and its width (`b3`, `getregtype()` in letters); the mode `command-line` is Vim's `c`. **Amended (V1c):** `window:` sets the window's size (`20x5`, columns by rows); `folds: java 2 7` makes the text's code folds with RSyntaxTextArea's Java fold parser and closes those starting on the listed lines (the recorder makes the same folds in Vim as manual folds); `expect-view:` (`topline,leftcol`) and `expect-folds:` (the closed folds) are then compared.
```

- [ ] **Step 6: Regenerate the reference**

Run:

```bash
./gradlew :jasper-plugin-editor:test --tests '*VimDocsTest' -PupdateDocs=true && git diff --stat docs/editor-vim-reference.md
```

Expected: `docs/editor-vim-reference.md` changes: it gains the scroll, `z`, screen-line and fold commands.

- [ ] **Step 7: Run everything**

Run:

```bash
./gradlew check
```

Expected: BUILD SUCCESSFUL (3,593 tests, 0 failed, 5 skipped).

- [ ] **Step 8: Check source hygiene**

Run the snippet in `AGENTS.md` ("Source hygiene"). Expected: no output.

- [ ] **Step 9: Commit**

```bash
git branch --show-current   # must print claude/vim-v1c
git add docs
git commit -m "Document Vim mode V1c: scrolling, screen-line motions and folds

Co-Authored-By: Claude Opus 5.5 <noreply@anthropic.com>"
```
