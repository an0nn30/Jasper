package dev.jasper.editor.vim;

import java.util.List;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** What the corpus cannot see: highlights, messages, the command line's history and the mouse (spec 5.1 and 5.3). */
class VisualSearchUiTest {
    private static void onEdt(Runnable test) throws Exception { SwingUtilities.invokeAndWait(test); }

    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    private static VimEditor editor(Vim vim, String text) {
        var area = new VimTextArea();
        area.setText(text);
        area.discardAllEdits();
        area.setCaretPosition(0);
        return new VimEditor(vim, area, EditorDefaults.VIM);
    }

    private static List<String> ranges(VimEditor editor) {
        return editor.highlights().stream().map(r -> r[0] + ":" + r[1] + "-" + r[2]).toList();
    }

    @Test void visualAreasAreDrawnAsVimSelectsThem() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\n\tdef\nghi\n");
            type(editor, "vl");
            assertThat(ranges(editor)).containsExactly("0:0-2");
            type(editor, "$");
            assertThat(ranges(editor)).as("past the last character the line break is in the area").containsExactly("0:0-4");
            type(editor, "V");
            assertThat(ranges(editor)).containsExactly("0:0-4");
            type(editor, "<C-v>j");
            assertThat(ranges(editor)).as("a block covers the tab it cuts").containsExactly("0:0-3", "0:4-8");
            type(editor, "<Esc>");
            assertThat(ranges(editor)).isEmpty();
            assertThat(editor.area().getSelectionStart()).isEqualTo(editor.area().getSelectionEnd());
        });
    }

    @Test void hlsearchDrawsEveryMatchAndFollowsEdits() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "ab ab\nab\n");
            editor.options().set("hlsearch", true);
            type(editor, "/ab<CR>");
            assertThat(ranges(editor)).containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "x");
            assertThat(ranges(editor)).containsExactly("1:0-2", "1:5-7");
            editor.options().set("hlsearch", false);
            type(editor, "l");
            assertThat(ranges(editor)).isEmpty();
        });
    }

    @Test void escapeInNormalModeHidesTheHlsearchMatchesUntilTheNextSearch() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "ab ab\nab\n");
            type(editor, "/ab<CR>");
            assertThat(ranges(editor)).containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "<Esc>");
            assertThat(ranges(editor)).as("<Esc> acts as :nohlsearch").isEmpty();
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 3));
            type(editor, "n");
            assertThat(ranges(editor)).as("the next search shows them again").containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "vl<Esc>");
            assertThat(ranges(editor)).as("leaving Visual mode keeps them").containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "d<Esc>");
            assertThat(ranges(editor)).as("cancelling an operator keeps them").containsExactly("1:0-2", "1:3-5", "1:6-8");
            type(editor, "0ix<Esc>");
            assertThat(ranges(editor)).as("leaving Insert mode keeps them").hasSize(3);
            type(editor, "<Esc>");
            assertThat(ranges(editor)).isEmpty();
        });
    }

    @Test void incsearchShowsTheMatchWithoutMovingTheCursor() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "one two\nthree two\n");
            type(editor, "/tw");
            assertThat(ranges(editor)).containsExactly("0:4-6");
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            type(editor, "<BS><BS>thr");
            assertThat(ranges(editor)).containsExactly("0:8-11");
            type(editor, "<Esc>");
            assertThat(ranges(editor)).isEmpty();
            assertThat(editor.cursor()).isEqualTo(new Pos(0, 0));
            assertThat(editor.mode()).isEqualTo(Mode.NORMAL);
        });
    }

    @Test void searchesReportLikeVim() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\n");
            type(editor, "/zz<CR>");
            assertThat(editor.message()).isEqualTo("E486: Pattern not found: zz");
            assertThat(editor.messageIsError()).isTrue();
            editor.options().set("wrapscan", false);
            type(editor, "/abc<CR>");
            assertThat(editor.message()).isEqualTo("E385: Search hit BOTTOM without match for: abc");
            type(editor, "?b<CR>");
            assertThat(editor.message()).isEqualTo("E384: Search hit TOP without match for: b");
            type(editor, "/a\\%Vb<CR>");
            assertThat(editor.message()).isEqualTo("E869: Unsupported in Jasper's Vim regex: \\%V");
            type(editor, "/\\(a<CR>");
            assertThat(editor.message()).isEqualTo("E54: Unmatched \\(");
        });
    }

    @Test void theCommandLineKeepsAHistoryFilteredByPrefix() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "alpha beta alpine\n");
            type(editor, "/alpha<CR>/beta<CR>/alpine<CR>gg");
            type(editor, "/al<Up>");
            assertThat(editor.commandLineText()).hasValue("/alpine");
            type(editor, "<Up>");
            assertThat(editor.commandLineText()).hasValue("/alpha");
            type(editor, "<Down><Down>");
            assertThat(editor.commandLineText()).as("past the newest entry the typed prefix returns").hasValue("/al");
            type(editor, "<C-u><Up>");
            assertThat(editor.commandLineText()).hasValue("/alpine");
            type(editor, "<Esc>");
        });
    }

    @Test void ctrlGAndGaShowTheirMessages() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "a\tb\nxé́\n");
            editor.documentInfo(() -> "notes.txt", () -> false);
            type(editor, "ll<C-g>");
            assertThat(editor.message()).isEqualTo("\"notes.txt\" line 1 of 2 --50%-- col 3-9");
            editor.documentInfo(() -> "notes.txt", () -> true);
            type(editor, "j0<C-g>");
            assertThat(editor.message()).isEqualTo("\"notes.txt\" [Modified] line 2 of 2 --100%-- col 1");
            type(editor, "ga");
            assertThat(editor.message()).isEqualTo("<x>  120,  Hex 78,  Octal 170");
            type(editor, "lga");
            assertThat(editor.message()).isEqualTo("<é> 233, Hex 00e9, Oct 351 < ́> 769, Hex 0301, Oct 1401");
        });
    }

    @Test void leavingTheTabSetsTheQuoteMark() throws Exception {
        onEdt(() -> {
            var editor = editor(new Vim(VimHost.detached()), "abc\ndef\n");
            type(editor, "`\"");
            assertThat(editor.message()).isEqualTo("E20: Mark not set");
            type(editor, "jl");
            for (var listener : editor.area().getFocusListeners())
                listener.focusLost(new java.awt.event.FocusEvent(editor.area(), java.awt.event.FocusEvent.FOCUS_LOST));
            type(editor, "gg`\"");
            assertThat(editor.cursor()).isEqualTo(new Pos(1, 1));
        });
    }

    @Test void aMouseSelectionBecomesAVisualAreaWithTheSameCharacters() throws Exception {
        var editor = new VimEditor[1];
        onEdt(() -> {
            editor[0] = editor(new Vim(VimHost.detached()), "abcd\nefgh\n");
            editor[0].area().select(1, 3);
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(editor[0].mode()).isEqualTo(Mode.VISUAL);
            assertThat(editor[0].visualAnchor()).isEqualTo(new Pos(0, 1));
            assertThat(editor[0].cursor()).isEqualTo(new Pos(0, 2));
            type(editor[0], "<Esc>");
            editor[0].area().setCaretPosition(7);
            editor[0].area().moveCaretPosition(2);
        });
        onEdt(() -> { });
        onEdt(() -> {
            assertThat(editor[0].mode()).isEqualTo(Mode.VISUAL);
            assertThat(editor[0].visualAnchor()).as("a backward drag: the anchor steps back").isEqualTo(new Pos(1, 1));
            assertThat(editor[0].cursor()).isEqualTo(new Pos(0, 2));
            type(editor[0], "<Esc>");
            editor[0].area().select(2, 5);
        });
        onEdt(() -> { });
        onEdt(() -> assertThat(editor[0].cursor()).as("ending on the line break: the line's end").isEqualTo(new Pos(0, 4)));
    }
}
