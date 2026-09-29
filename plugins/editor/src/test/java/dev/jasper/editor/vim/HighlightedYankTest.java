package dev.jasper.editor.vim;

import java.awt.Color;
import java.util.List;
import java.util.function.Consumer;
import javax.swing.SwingUtilities;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

/** The highlightedyank extension (spec 8), which only draws, so the corpus cannot see it. */
class HighlightedYankTest {
    private static void type(VimEditor editor, String keys) { for (KeyInput key : KeyInput.parse(keys)) editor.feed(key); }

    /** Runs the keys, then lets the highlight be drawn as it is once the command has finished, then checks. */
    private static void after(VimEditor editor, String keys, Consumer<List<String>> check) throws Exception {
        SwingUtilities.invokeAndWait(() -> type(editor, keys));
        SwingUtilities.invokeAndWait(() -> check.accept(yank(editor)));
    }

    private static VimEditor editor(String text, String... settings) throws Exception {
        VimEditor[] made = new VimEditor[1];
        SwingUtilities.invokeAndWait(() -> {
            var area = new VimTextArea();
            area.setText(text);
            area.discardAllEdits();
            area.setCaretPosition(0);
            made[0] = new VimEditor(new Vim(VimHost.detached()), area, EditorDefaults.VIM);
            for (String setting : settings) type(made[0], ":" + setting + "<CR>");
            type(made[0], ":set highlightedyank<CR>");
        });
        return made[0];
    }

    /** @return the yank's highlight as start-end offsets */
    private static List<String> yank(VimEditor editor) {
        return editor.highlights().stream().filter(r -> r[0] == 2).map(r -> r[1] + "-" + r[2]).toList();
    }

    @Test void aYankIsHighlightedOnceItHasFinished() throws Exception {
        var editor = editor("one two\nthree\n");
        SwingUtilities.invokeAndWait(() -> {
            type(editor, "wyiw");
            assertThat(yank(editor)).as("drawn after the command, as the plugin's timer does").isEmpty();
        });
        after(editor, "", ranges -> assertThat(ranges).containsExactly("4-7"));
        after(editor, "yy", ranges -> assertThat(ranges).as("a line with its break").containsExactly("0-8"));
        after(editor, "y$", ranges -> assertThat(ranges).containsExactly("4-7"));
        after(editor, "2yy", ranges -> assertThat(ranges).containsExactly("0-14"));
        after(editor, "0vjy", ranges -> assertThat(ranges).as("from Visual mode, the area").containsExactly("0-9"));
        after(editor, "gg0<C-v>jly", ranges -> assertThat(ranges).as("a block").containsExactly("0-2", "8-10"));
        after(editor, ":2y<CR>", ranges -> assertThat(ranges).containsExactly("8-14"));
        after(editor, "gg0yj", ranges -> assertThat(ranges).containsExactly("0-14"));
        after(editor, "gg0yvj", ranges -> assertThat(ranges).as("charwise across lines, to the line break").containsExactly("0-8"));
    }

    @Test void onlyYanksAndOnlyWhenSwitchedOn() throws Exception {
        var editor = editor("one two\n");
        after(editor, "dw", ranges -> assertThat(ranges).as("a delete is not a yank").isEmpty());
        after(editor, "\"_yiw", ranges -> assertThat(ranges).as("the black hole takes nothing").isEmpty());
        after(editor, ":set nohighlightedyank<CR>yiw", ranges -> assertThat(ranges).isEmpty());
    }

    @Test void theLastYankOfACommandIsDrawnAndAChangeOrInsertModeClearsIt() throws Exception {
        var editor = editor("one two three\n");
        after(editor, "yiwwyiw", ranges -> assertThat(ranges).as("only the last yank").containsExactly("4-7"));
        after(editor, "x", ranges -> assertThat(ranges).as("a change clears it").isEmpty());
        after(editor, "0yiw", ranges -> assertThat(ranges).containsExactly("0-3"));
        after(editor, "i", ranges -> assertThat(ranges).as("Insert mode clears it").isEmpty());
        after(editor, "<Esc>yiwx", ranges -> assertThat(ranges).as("a change before it is drawn: nothing").isEmpty());
    }

    @Test void theDurationAndTheSettings() throws Exception {
        var editor = editor("one\ntwo\nthree\n");
        after(editor, "yy", ranges -> assertThat(ranges).containsExactly("0-4"));
        SwingUtilities.invokeAndWait(() -> {
            editor.highlightedYank().expireNow();
            assertThat(yank(editor)).as("after g:highlightedyank_highlight_duration").isEmpty();
        });
        after(editor, ":let g:highlightedyank_max_lines = 2<CR>3yy", ranges -> assertThat(ranges).as("too many lines").isEmpty());
        after(editor, "2yy", ranges -> assertThat(ranges).containsExactly("0-8"));
        SwingUtilities.invokeAndWait(() -> editor.highlightedYank().expireNow());
        after(editor, ":let g:highlightedyank_highlight_in_visual = 0<CR>Vy", ranges -> assertThat(ranges).as("not from Visual mode").isEmpty());
        after(editor, ":let g:highlightedyank_highlight_duration = 0<CR>yy", ranges -> assertThat(ranges).as("0: never").isEmpty());
        after(editor, ":let g:highlightedyank_highlight_duration = -1<CR>yy", ranges -> assertThat(ranges).containsExactly("0-4"));
        SwingUtilities.invokeAndWait(() -> {
            editor.highlightedYank().expireNow();
            assertThat(yank(editor)).as("below 0: no timer").containsExactly("0-4");
        });
        after(editor, "Vy", ranges -> assertThat(ranges).as("a yank the plugin leaves out keeps what is drawn").containsExactly("0-4"));
        after(editor, ":let g:highlightedyank_highlight_color = 'rgba(10, 20, 30, 40)'<CR>", ranges ->
            assertThat(editor.highlightedYank().color()).isEqualTo(new Color(10, 20, 30, 40)));
    }

    @Test void theEmptyTextAndTheLastLine() throws Exception {
        var empty = editor("");
        after(empty, "yy", ranges -> assertThat(ranges).as("nothing to draw in an empty text").isEmpty());
        var last = editor("a\nb");
        after(last, "Gyy", ranges -> assertThat(ranges).as("the last line, with no line break").containsExactly("2-3"));
        after(last, "yl", ranges -> assertThat(ranges).containsExactly("2-3"));
    }
}
